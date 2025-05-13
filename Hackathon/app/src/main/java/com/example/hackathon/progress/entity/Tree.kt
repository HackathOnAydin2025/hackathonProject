package com.example.hackathon.progress.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tree_table")
data class Tree(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val count: Int
)
