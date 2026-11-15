package com.notipass.droid.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.Rule

@Database(
    entities = [Rule::class, NotificationLog::class],
    version = 2, // Incremented from 1 to 2 to trigger destructive migration
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "notipass_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    buildDatabase(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize database. Wiping and recreating...", e)
                    try {
                        context.deleteDatabase(DB_NAME)
                    } catch (_: Exception) {}
                    buildDatabase(context)
                }.also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
