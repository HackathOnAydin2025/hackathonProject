package com.example.hackathon.data // Kendi paket adınızı kullanın

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

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
                    // .fallbackToDestructiveMigration() // Geliştirme aşamasında şema değişikliğinde veriyi siler
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}