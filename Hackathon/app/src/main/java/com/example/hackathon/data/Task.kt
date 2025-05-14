package com.example.hackathon.data // Kendi paket adınızı kullanın

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks_table")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    var isCompleted: Boolean = false, // Değiştirilebilir olmalı
    val durationMinutes: Int = 25,   // Varsayılan Pomodoro süresi
    var actualFocusedMinutes: Int = 0, // Bu görev için fiilen odaklanılan toplam süre
    val startTime: Long? = null,     // Başlangıç zamanı (opsiyonel, milisaniye cinsinden)
    val creationTimestamp: Long = System.currentTimeMillis() // Görevin oluşturulma zamanı
)