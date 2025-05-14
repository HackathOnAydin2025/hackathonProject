package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.data.DailyTaskSummary // Bu sınıfın tanımını kontrol edin
import com.example.hackathon.data.Task
import com.example.hackathon.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext // Eğer DAO'da suspend olmayan fonksiyonlar için gerekirse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var taskDao: TaskDao // lateinit kaldırıldı, init içinde atanacak
    val todayTasks: LiveData<List<Task>>

    // Takvim veya belirli bir tarih için görevleri tutar
    private val _selectedDate = MutableLiveData<String>()
    val tasksForSelectedDate: LiveData<List<Task>> = _selectedDate.switchMap { dateStr ->
        taskDao.getTasksForDate(dateStr)
    }

    // Haftalık görev özeti için (PieChart)
    private val _weeklyTaskSummary = MediatorLiveData<List<DailyTaskSummary>>()
    val weeklyTaskSummary: LiveData<List<DailyTaskSummary>> = _weeklyTaskSummary
    private val activeWeeklySummarySources = mutableListOf<LiveData<List<Task>>>()


    // Bugünün TÜM görevlerinin toplam PLANLANAN odak süresi (ProgressFragment ProgressBar MAX için)
    private val _totalPlannedFocusTimeToday = MutableLiveData<Int>()
    val totalPlannedFocusTimeToday: LiveData<Int> = _totalPlannedFocusTimeToday

    // Bugünün görevleri için harcanan FİİLİ toplam odak süresi (ProgressFragment ProgressBar PROGRESS için)
    // İsim değişikliği: _completedFocusTimeToday -> _actualFocusTimeSpentToday
    private val _actualFocusTimeSpentToday = MutableLiveData<Int>()
    val actualFocusTimeSpentToday: LiveData<Int> = _actualFocusTimeSpentToday


    // todayTasks LiveData'sını gözlemleyerek yukarıdaki LiveData'ları günceller
    private val todayTasksObserver = Observer<List<Task>> { tasks ->
        val allTasks = tasks ?: emptyList() // Null ise boş liste kullan

        // Tüm planlanan görevlerin toplam süresini hesapla
        val totalPlannedMinutes = allTasks.sumOf { it.durationMinutes }
        _totalPlannedFocusTimeToday.value = totalPlannedMinutes

        // Fiilen harcanan toplam odak süresini hesapla (actualFocusedMinutes kullanarak)
        val totalActualMinutes = allTasks.sumOf { it.actualFocusedMinutes }
        _actualFocusTimeSpentToday.value = totalActualMinutes
        Log.d("TaskViewModel", "Today's focus updated - Actual: $totalActualMinutes min, Planned: $totalPlannedMinutes min")
    }

    // Pomodoro için seçilen görevi tutacak LiveData
    private val _selectedTaskForPomodoro = MutableLiveData<Task?>()
    val selectedTaskForPomodoro: LiveData<Task?> = _selectedTaskForPomodoro

    init {
        val database = AppDatabase.getDatabase(application)
        taskDao = database.taskDao()
        todayTasks = taskDao.getTodayTasks() // Bugünün görevlerini yükle

        // todayTasks LiveData'sını gözlemlemeye başla
        todayTasks.observeForever(todayTasksObserver)
    }

    // Pomodoro için görevi ayarlayan fonksiyon (Senin kullandığın isim)
    fun selectTaskForPomodoro(task: Task?) { // PomodoroFragment'ın kullanabilmesi için ismi selectTaskForPomodoro olarak değiştirdim.
        _selectedTaskForPomodoro.value = task
        Log.d("TaskViewModel", "Task selected for Pomodoro: ${task?.title}")
    }

    // Pomodoro bittiğinde veya iptal edildiğinde seçili görevi temizle
    fun clearSelectedTaskForPomodoro() {
        _selectedTaskForPomodoro.value = null
        Log.d("TaskViewModel", "Selected Pomodoro task cleared.")
    }

    // YENİ FONKSİYON: Bir Pomodoro seansı sonrası fiili odak süresini kaydeder
    fun recordFocusedSession(taskId: Int, sessionMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = taskDao.getTaskById(taskId) // Bu fonksiyon DAO'da suspend olmalı
            if (task != null) {
                val newFocusedTime = task.actualFocusedMinutes + sessionMinutes
                val updatedTask = task.copy(actualFocusedMinutes = newFocusedTime)
                taskDao.updateTask(updatedTask)
                Log.d("TaskViewModel", "Recorded focus session for task ID $taskId: $sessionMinutes min. New actual total: $newFocusedTime min.")
            } else {
                Log.w("TaskViewModel", "Task with ID $taskId not found for recording focus session.")
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        todayTasks.removeObserver(todayTasksObserver) // Observer'ı kaldır
        activeWeeklySummarySources.forEach { source ->
            _weeklyTaskSummary.removeSource(source)
        }
        activeWeeklySummarySources.clear()
    }

    // --- Diğer Standart Task İşlemleri ---
    fun insertTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    fun updateTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.updateTask(task)
    }

    fun deleteTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.deleteTask(task)
    }

    fun addNewTask(title: String, durationMinutes: Int = 25, startTime: Long? = null) {
        if (title.isBlank()) return
        val newTask = Task(
            title = title,
            durationMinutes = durationMinutes,
            actualFocusedMinutes = 0, // Yeni görev için fiili odak 0'dır
            startTime = startTime
        )
        insertTask(newTask)
    }

    // TaskListFragment'tan gelen çağrıya göre güncellendi:
    // TaskListAdapter'dan gelen task zaten güncel isCompleted durumunu içeriyor.
    // Bu fonksiyon, görevin tamamlanma durumunu değiştirir.
    // Fiili odak süresini doğrudan etkilemez, o Pomodoro seansları ile yönetilir.
    fun toggleTaskCompleted(taskToToggle: Task) {
        // isCompleted durumu zaten tersine çevrilmiş olarak geliyor olabilir (adapter'dan)
        // veya burada tersine çevirebiliriz. Senin kodunda adapter'dan geldiği varsayılmış.
        // Eğer adapter'dan gelmiyorsa: val updatedTask = taskToToggle.copy(isCompleted = !taskToToggle.isCompleted)
        // Şimdilik, TaskListAdapter'dan gelen task'ın isCompleted durumunun doğru olduğunu varsayıyoruz.
        updateTask(taskToToggle)
    }


    fun deleteCompletedTasks() = viewModelScope.launch(Dispatchers.IO) {
        taskDao.deleteCompletedTasks()
    }

    fun loadTasksForDate(year: Int, month: Int, dayOfMonth: Int) {
        _selectedDate.value = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
    }

    fun addTasksFromSuggestions(suggestions: List<Pair<String, Int>>) {
        viewModelScope.launch(Dispatchers.IO) {
            suggestions.forEach { (title, duration) ->
                val newTask = Task(title = title, durationMinutes = duration, actualFocusedMinutes = 0)
                taskDao.insertTask(newTask)
            }
        }
    }

    // loadWeeklyTaskSummary metodun senin sağladığın şekilde bırakıldı.
    // DailyTaskSummary'nin completedTaskCount alanı PieChart için kullanılabilir.
    fun loadWeeklyTaskSummary(startDateOfWeek: Date) {
        activeWeeklySummarySources.forEach { source ->
            _weeklyTaskSummary.removeSource(source)
        }
        activeWeeklySummarySources.clear()

        val calendar = Calendar.getInstance()
        calendar.time = startDateOfWeek

        val dateFormatForQuery = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateFormatForLabel = SimpleDateFormat("dd MMM (E)", Locale("tr"))

        val datesInWeek = (0..6).map {
            val date = calendar.time.clone() as Date
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            date
        }

        val dailyResults = mutableMapOf<String, Int>()
        val dailyCompletedResults = mutableMapOf<String, Int>() // Bu senin kodunda vardı.
        val expectedResults = datesInWeek.size

        datesInWeek.forEach { dayDate ->
            val dateStrQuery = dateFormatForQuery.format(dayDate)
            val dayTasksLiveData = taskDao.getTasksForDate(dateStrQuery)
            activeWeeklySummarySources.add(dayTasksLiveData)

            _weeklyTaskSummary.addSource(dayTasksLiveData) { tasks ->
                dailyResults[dateStrQuery] = tasks?.size ?: 0
                dailyCompletedResults[dateStrQuery] = tasks?.count { it.isCompleted } ?: 0

                if (dailyResults.size == expectedResults && dailyCompletedResults.size == expectedResults) {
                    val summaryList = datesInWeek.map { d ->
                        val queryStr = dateFormatForQuery.format(d)
                        DailyTaskSummary(
                            date = d,
                            taskCount = dailyResults[queryStr] ?: 0,
                            completedTaskCount = dailyCompletedResults[queryStr] ?: 0, // Senin DailyTaskSummary'ne uygun
                            label = dateFormatForLabel.format(d)
                        )
                    }
                    _weeklyTaskSummary.value = summaryList
                }
            }
        }
    }

    // ProgressFragment'ın belirli bir tarihin görevlerini alması için
    fun getTasksLiveDataForSpecificDate(dateString: String): LiveData<List<Task>> {
        return taskDao.getTasksForDate(dateString)
    }
}
