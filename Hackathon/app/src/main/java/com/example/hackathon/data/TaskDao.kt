package com.example.hackathon.data // Kendi paket adınızı kullanın

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Sadece bugünün görevlerini, oluşturulma zamanına göre en yeniden eskiye sıralı getirir
    @Query("SELECT * FROM tasks_table WHERE date(creationTimestamp / 1000, 'unixepoch', 'localtime') = date('now', 'localtime') ORDER BY creationTimestamp DESC")
    fun getTodayTasks(): LiveData<List<Task>>

    // Tüm görevleri, oluşturulma zamanına göre en yeniden eskiye sıralı getirir
    @Query("SELECT * FROM tasks_table ORDER BY creationTimestamp DESC")
    fun getAllTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks_table WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?

    // Tamamlanmış tüm görevleri siler
    @Query("DELETE FROM tasks_table WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    // Belirli bir tarihteki görevleri getirir (YYYY-MM-DD formatında)
    @Query("SELECT * FROM tasks_table WHERE date(creationTimestamp / 1000, 'unixepoch', 'localtime') = :dateString ORDER BY creationTimestamp ASC")
    fun getTasksForDate(dateString: String): LiveData<List<Task>>
}