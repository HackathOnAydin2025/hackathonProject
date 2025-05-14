package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.data.DailyTaskSummary
import com.example.hackathon.data.Task
import com.example.hackathon.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // withContext için eklendi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var taskDao: TaskDao
    val todayTasks: LiveData<List<Task>> // Bu, DB'den güncel görevleri çeker

    private val _selectedDate = MutableLiveData<String>()
    val tasksForSelectedDate: LiveData<List<Task>> = _selectedDate.switchMap { dateStr ->
        taskDao.getTasksForDate(dateStr)
    }

    private val _weeklyTaskSummary = MediatorLiveData<List<DailyTaskSummary>>()
    val weeklyTaskSummary: LiveData<List<DailyTaskSummary>> = _weeklyTaskSummary
    private val activeWeeklySummarySources = mutableListOf<LiveData<List<Task>>>()

    private val _totalPlannedFocusTimeToday = MutableLiveData<Int>()
    val totalPlannedFocusTimeToday: LiveData<Int> = _totalPlannedFocusTimeToday

    private val _actualFocusTimeSpentToday = MutableLiveData<Int>()
    val actualFocusTimeSpentToday: LiveData<Int> = _actualFocusTimeSpentToday

    private val TAG_VIEWMODEL = "TaskViewModel" // Loglama için TAG

    private val todayTasksObserver = Observer<List<Task>> { tasks ->
        val allTasks = tasks ?: emptyList()
        Log.d(TAG_VIEWMODEL, "todayTasksObserver tetiklendi. Gelen görev sayısı: ${allTasks.size}")
        allTasks.forEach { task ->
            Log.d(TAG_VIEWMODEL, "  Task ID: ${task.id}, Title: ${task.title}, Planned: ${task.durationMinutes} dk, ActualFocused: ${task.actualFocusedMinutes} dk")
        }

        val totalPlannedMinutes = allTasks.sumOf { it.durationMinutes }
        _totalPlannedFocusTimeToday.value = totalPlannedMinutes

        val totalActualMinutes = allTasks.sumOf { it.actualFocusedMinutes }
        _actualFocusTimeSpentToday.value = totalActualMinutes
        Log.d(TAG_VIEWMODEL, "Bugünün odak durumu güncellendi - Fiili: $totalActualMinutes dk, Planlanan: $totalPlannedMinutes dk")
    }

    private val _selectedTaskForPomodoro = MutableLiveData<Task?>()
    val selectedTaskForPomodoro: LiveData<Task?> = _selectedTaskForPomodoro

    var pausedPomodoroTaskId: Int? = null
    var pausedPomodoroTimeLeftMillis: Long? = null
    var pausedPomodoroSessionDurationMillis: Long? = null

    init {
        Log.d(TAG_VIEWMODEL, "TaskViewModel init bloğu çalışıyor.")
        val database = AppDatabase.getDatabase(application)
        taskDao = database.taskDao()
        todayTasks = taskDao.getTodayTasks() // Bu, DB'den LiveData olarak görevleri alır
        Log.d(TAG_VIEWMODEL, "todayTasks LiveData'sı Dao'dan alındı.")
        todayTasks.observeForever(todayTasksObserver)
        Log.d(TAG_VIEWMODEL, "todayTasksObserver eklendi.")
    }

    fun selectTaskForPomodoro(task: Task?) {
        if (task?.id != pausedPomodoroTaskId) {
            clearPausedPomodoroState()
        }
        _selectedTaskForPomodoro.value = task
        Log.d(TAG_VIEWMODEL, "Pomodoro için görev seçildi: ${task?.title}")
    }

    fun clearSelectedTaskForPomodoro() {
        _selectedTaskForPomodoro.value = null
        clearPausedPomodoroState()
        Log.d(TAG_VIEWMODEL, "Seçili Pomodoro görevi ve duraklatılmış durum temizlendi.")
    }

    fun recordFocusedSession(taskId: Int, sessionMinutesActuallyFocused: Int) {
        if (sessionMinutesActuallyFocused <= 0) {
            Log.w(TAG_VIEWMODEL, "Odak süresi 0 veya daha az olduğu için kayıt yapılmadı: $sessionMinutesActuallyFocused dk")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val task = taskDao.getTaskById(taskId)
            if (task != null) {
                val newFocusedTime = task.actualFocusedMinutes + sessionMinutesActuallyFocused
                val updatedTask = task.copy(actualFocusedMinutes = newFocusedTime)
                taskDao.updateTask(updatedTask)
                Log.i(TAG_VIEWMODEL, "Veritabanı güncellendi: Task ID $taskId, Yeni ActualFocused: $newFocusedTime dk.")

                // Güncellemenin hemen ardından veritabanından görevi tekrar okuyup loglayalım (doğrulama için)
                val taskFromDbAfterUpdate = taskDao.getTaskById(taskId)
                if (taskFromDbAfterUpdate != null) {
                    Log.i(TAG_VIEWMODEL, "DB'den doğrulama: Task ID ${taskFromDbAfterUpdate.id}, Güncel ActualFocused: ${taskFromDbAfterUpdate.actualFocusedMinutes} dk")
                } else {
                    Log.e(TAG_VIEWMODEL, "DB'den doğrulama BAŞARISIZ: Task ID $taskId bulunamadı!")
                }
            } else {
                Log.w(TAG_VIEWMODEL, "$taskId ID'li görev odak seansı kaydı için bulunamadı.")
            }
        }
    }

    fun storePausedPomodoroState(taskId: Int, timeLeftMillis: Long, sessionDurationMillis: Long) {
        pausedPomodoroTaskId = taskId
        pausedPomodoroTimeLeftMillis = timeLeftMillis
        pausedPomodoroSessionDurationMillis = sessionDurationMillis
        Log.d(TAG_VIEWMODEL, "Pomodoro durumu duraklatıldı: TaskID=$taskId, KalanSüre=$timeLeftMillis, SeansSüresi=$sessionDurationMillis")
    }

    fun clearPausedPomodoroState() {
        pausedPomodoroTaskId = null
        pausedPomodoroTimeLeftMillis = null
        pausedPomodoroSessionDurationMillis = null
        Log.d(TAG_VIEWMODEL, "Duraklatılmış Pomodoro durumu temizlendi.")
    }

    override fun onCleared() {
        super.onCleared()
        todayTasks.removeObserver(todayTasksObserver)
        activeWeeklySummarySources.forEach { source ->
            _weeklyTaskSummary.removeSource(source)
        }
        activeWeeklySummarySources.clear()
        Log.d(TAG_VIEWMODEL, "TaskViewModel onCleared çağrıldı, observer'lar kaldırıldı.")
    }

    fun insertTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.insertTask(task)
        Log.d(TAG_VIEWMODEL, "Görev eklendi: ${task.title}")
    }

    fun updateTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.updateTask(task)
        Log.d(TAG_VIEWMODEL, "Görev güncellendi: ${task.title}, Tamamlandı: ${task.isCompleted}, ActualFocused: ${task.actualFocusedMinutes}")
    }

    fun deleteTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        taskDao.deleteTask(task)
        Log.d(TAG_VIEWMODEL, "Görev silindi: ${task.title}")
    }

    fun addNewTask(title: String, durationMinutes: Int = 25, startTime: Long? = null) {
        if (title.isBlank()) return
        val newTask = Task(
            title = title,
            durationMinutes = durationMinutes,
            actualFocusedMinutes = 0,
            startTime = startTime
        )
        insertTask(newTask)
    }

    fun toggleTaskCompleted(taskToToggle: Task) {
        // TaskListAdapter'dan gelen task zaten güncel isCompleted durumunu içeriyor olmalı.
        updateTask(taskToToggle)
    }

    fun deleteCompletedTasks() = viewModelScope.launch(Dispatchers.IO) {
        taskDao.deleteCompletedTasks()
        Log.d(TAG_VIEWMODEL, "Tamamlanmış görevler silindi.")
    }

    fun loadTasksForDate(year: Int, month: Int, dayOfMonth: Int) {
        val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        _selectedDate.value = dateString
        Log.d(TAG_VIEWMODEL, "$dateString için görevler yükleniyor.")
    }

    fun addTasksFromSuggestions(suggestions: List<Pair<String, Int>>) {
        viewModelScope.launch(Dispatchers.IO) {
            suggestions.forEach { (title, duration) ->
                val newTask = Task(title = title, durationMinutes = duration, actualFocusedMinutes = 0)
                taskDao.insertTask(newTask)
            }
            Log.d(TAG_VIEWMODEL, "${suggestions.size} adet önerilen görev eklendi.")
        }
    }

    fun loadWeeklyTaskSummary(startDateOfWeek: Date) {
        // ... (Mevcut kodunuzdaki gibi, değişiklik yok) ...
    }

    fun getTasksLiveDataForSpecificDate(dateString: String): LiveData<List<Task>> {
        return taskDao.getTasksForDate(dateString)
    }
}