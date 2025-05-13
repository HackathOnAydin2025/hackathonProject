package com.example.hackathon.progress.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drop_table")
data class Drop(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val count: Int
)