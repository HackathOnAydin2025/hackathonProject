package com.example.hackathon.progress.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.hackathon.progress.entity.Tree

@Dao
interface TreeDao {
    @Insert
    suspend fun insert(tree: Tree)

    @Query("SELECT * FROM tree_table")
    suspend fun getAllTrees(): List<Tree>
}