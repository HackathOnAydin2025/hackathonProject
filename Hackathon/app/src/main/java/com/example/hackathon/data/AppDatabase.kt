package com.example.hackathon.data // Kendi paket adınızı kullanın

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.hackathon.progress.dao.DropDao
import com.example.hackathon.progress.dao.TreeDao
import com.example.hackathon.progress.entity.Drop
import com.example.hackathon.progress.entity.Tree

@Database(entities = [Task::class, Tree::class, Drop::class], version = 3, exportSchema = false) // Versiyon 2 doğru.
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun treeDao(): TreeDao
    abstract fun dropDao(): DropDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zaman_bahcesi_database" // Veritabanı dosya adı
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}