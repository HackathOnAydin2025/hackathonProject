package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.app.Application
import androidx.lifecycle.*
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.data.Task
import com.example.hackathon.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var taskDao: TaskDao
    val todayTasks: LiveData<List<Task>>

    // Takvim için seçilen tarihi ve o tarihe ait görevleri tutar
    private val _selectedDate = MutableLiveData<String>()
    val tasksForSelectedDate: LiveData<List<Task>> = _selectedDate.switchMap { dateStr ->
        taskDao.getTasksForDate(dateStr)
    }

    init {
        val database = AppDatabase.getDatabase(application)
        taskDao = database.taskDao()
        todayTasks = taskDao.getTodayTasks() // Başlangıçta bugünün görevlerini yükle
        // Başlangıçta bugünün tarihini selectedDate'e ata (opsiyonel, eğer direkt belirli bir tarih göstermek istenirse)
        // _selectedDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
        if (title.isBlank()) return // Boş başlık eklemeyi engelle
        val newTask = Task(
            title = title,
            durationMinutes = durationMinutes,
            startTime = startTime
        )
        insertTask(newTask)
    }

    fun toggleTaskCompleted(task: Task) {
        val updatedTask = task.copy(isCompleted = !task.isCompleted)
        updateTask(updatedTask)
    }

    fun deleteCompletedTasks() = viewModelScope.launch(Dispatchers.IO) {
        taskDao.deleteCompletedTasks()
    }

    fun loadTasksForDate(year: Int, month: Int, dayOfMonth: Int) {
        // Ay 0'dan başladığı için month + 1
        _selectedDate.value = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
    }

    // Gemini'den gelen görev önerilerini eklemek için bir fonksiyon
    fun addTasksFromSuggestions(suggestions: List<Pair<String, Int>>) { // (Başlık, Süre)
        viewModelScope.launch(Dispatchers.IO) {
            suggestions.forEach { (title, duration) ->
                val newTask = Task(title = title, durationMinutes = duration)
                taskDao.insertTask(newTask)
            }
        }
    }
}