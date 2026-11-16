package com.notipass.droid.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.Rule

@Database(
    entities = [Rule::class, NotificationLog::class, KakaoSummaryConfig::class],
    version = 3, // Incremented to 3 for KakaoSummaryConfig
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao
    abstract fun kakaoSummaryDao(): KakaoSummaryDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "notipass_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = try {
                    val built = buildDatabase(context)
                    // Actively touch SQLite database to trigger migration/creation immediately
                    built.openHelper.writableDatabase
                    built
                } catch (t: Throwable) {
                    Log.e(TAG, "Fatal DB opening error. Deleting database file and recreating...", t)
                    try {
                        context.deleteDatabase(DB_NAME)
                    } catch (_: Exception) {}
                    val fresh = buildDatabase(context)
                    try {
                        fresh.openHelper.writableDatabase
                    } catch (t2: Throwable) {
                        Log.e(TAG, "Fresh DB also failed", t2)
                    }
                    fresh
                }
                INSTANCE = db
                db
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
