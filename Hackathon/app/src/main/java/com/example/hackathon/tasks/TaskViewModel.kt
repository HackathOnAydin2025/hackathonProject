package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.app.Application
import androidx.lifecycle.*
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.data.DailyTaskSummary // Bu sınıfın tanımını kontrol edin
import com.example.hackathon.data.Task
import com.example.hackathon.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var taskDao: TaskDao
    val todayTasks: LiveData<List<Task>>

    private val _selectedDate = MutableLiveData<String>()
    val tasksForSelectedDate: LiveData<List<Task>> = _selectedDate.switchMap { dateStr ->
        taskDao.getTasksForDate(dateStr)
    }

    private val _weeklyTaskSummary = MediatorLiveData<List<DailyTaskSummary>>()
    val weeklyTaskSummary: LiveData<List<DailyTaskSummary>> = _weeklyTaskSummary

    private val activeWeeklySummarySources = mutableListOf<LiveData<List<Task>>>()

    // Pomodoro için seçilen görevi tutacak LiveData
    private val _selectedTaskForPomodoro = MutableLiveData<Task?>()
    val selectedTaskForPomodoro: LiveData<Task?> = _selectedTaskForPomodoro

    init {
        val database = AppDatabase.getDatabase(application)
        taskDao = database.taskDao()
        todayTasks = taskDao.getTodayTasks()
    }

    // Pomodoro için görevi ayarlayan fonksiyon
    fun startPomodoroForTask(task: Task) {
        _selectedTaskForPomodoro.value = task
    }

    // Pomodoro bittiğinde veya iptal edildiğinde seçili görevi temizle
    // !!! KULLANILACAK DOĞRU FONKSİYON ADI BUDUR: clearSelectedTaskForPomodoro !!!
    fun clearSelectedTaskForPomodoro() {
        _selectedTaskForPomodoro.value = null
    }


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
            startTime = startTime
        )
        insertTask(newTask)
    }

    // TaskListFragment'tan gelen çağrıya göre güncellendi:
    // TaskListAdapter'dan gelen task zaten güncel isCompleted durumunu içeriyor.
    fun toggleTaskCompleted(updatedTaskWithCheckedState: Task) {
        updateTask(updatedTaskWithCheckedState)
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
                val newTask = Task(title = title, durationMinutes = duration)
                taskDao.insertTask(newTask)
            }
        }
    }

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
        val dailyCompletedResults = mutableMapOf<String, Int>()
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
                            completedTaskCount = dailyCompletedResults[queryStr] ?: 0,
                            label = dateFormatForLabel.format(d)
                        )
                    }
                    _weeklyTaskSummary.value = summaryList
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeWeeklySummarySources.forEach { source ->
            _weeklyTaskSummary.removeSource(source)
        }
        activeWeeklySummarySources.clear()
    }
}
