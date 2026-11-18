package com.notipass.droid.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.model.KakaoSummaryRecord
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.Rule

@Database(
    entities = [Rule::class, NotificationLog::class, KakaoSummaryConfig::class, KakaoSummaryRecord::class],
    version = 6, // Incremented to 6 for kakao_summary_records history table
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `triggerType` TEXT NOT NULL DEFAULT 'NOTIFICATION'")
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `targetPackage` TEXT")
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `targetAppName` TEXT")
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `smsTargetNumber` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `smsTemplate` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `kakao_summary_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `roomName` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL DEFAULT 1, `dismissNotification` INTEGER NOT NULL DEFAULT 1, `summaryThresholdCount` INTEGER NOT NULL DEFAULT 30, `deliveryType` TEXT NOT NULL DEFAULT 'SELF_SMS', `targetSmsNumber` TEXT NOT NULL DEFAULT '', `lastSummarizedTime` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `kakao_summary_configs` ADD COLUMN `isScheduledTimeEnabled` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `kakao_summary_configs` ADD COLUMN `scheduledHour` INTEGER NOT NULL DEFAULT 21")
                    db.execSQL("ALTER TABLE `kakao_summary_configs` ADD COLUMN `scheduledMinute` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `kakao_summary_configs` ADD COLUMN `summaryStyle` TEXT NOT NULL DEFAULT 'AUTO'")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `kakao_summary_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `configId` INTEGER NOT NULL DEFAULT 0,
                            `roomName` TEXT NOT NULL,
                            `messageCount` INTEGER NOT NULL,
                            `summaryText` TEXT NOT NULL,
                            `style` TEXT NOT NULL DEFAULT 'AUTO',
                            `deliveryType` TEXT NOT NULL DEFAULT 'LOCAL_NOTIFICATION',
                            `createdAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_kakao_summary_records_roomName` ON `kakao_summary_records` (`roomName`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_kakao_summary_records_createdAt` ON `kakao_summary_records` (`createdAt`)")
                } catch (_: Exception) {}
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
