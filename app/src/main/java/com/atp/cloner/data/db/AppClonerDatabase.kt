package com.atp.cloner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, ProcessingLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppClonerDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun processingLogDao(): ProcessingLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppClonerDatabase? = null

        fun getDatabase(context: Context): AppClonerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppClonerDatabase::class.java,
                    "atp_app_cloner.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
