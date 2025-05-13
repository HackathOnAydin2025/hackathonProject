package com.example.hackathon.progress.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.hackathon.progress.entity.Drop

@Dao
interface DropDao {
    @Insert
    suspend fun insert(drop: Drop)

    @Query("SELECT * FROM drop_table")
    suspend fun getAllDrops(): List<Drop>
}