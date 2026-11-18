package com.notipass.droid.data.db

import androidx.room.*
import com.notipass.droid.model.AppCollectorConfig
import com.notipass.droid.model.AppSummaryRecord
import com.notipass.droid.model.CollectedAppNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCollectorDao {

    // Configs
    @Query("SELECT * FROM app_collector_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<AppCollectorConfig>>

    @Query("SELECT * FROM app_collector_configs WHERE isEnabled = 1")
    suspend fun getEnabledConfigs(): List<AppCollectorConfig>

    @Query("SELECT * FROM app_collector_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getConfigByPackage(packageName: String): AppCollectorConfig?

    @Query("SELECT * FROM app_collector_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Long): AppCollectorConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppCollectorConfig): Long

    @Update
    suspend fun updateConfig(config: AppCollectorConfig)

    @Delete
    suspend fun deleteConfig(config: AppCollectorConfig)

    @Query("UPDATE app_collector_configs SET isEnabled = :enabled WHERE id = :id")
    suspend fun setConfigEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE app_collector_configs SET lastSummarizedTime = :time WHERE id = :id")
    suspend fun updateLastSummarizedTime(id: Long, time: Long)

    // Collected Notifications
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: CollectedAppNotification): Long

    @Query("SELECT * FROM collected_app_notifications WHERE packageName = :packageName ORDER BY postTime DESC")
    fun getNotificationsByPackage(packageName: String): Flow<List<CollectedAppNotification>>

    @Query("SELECT * FROM collected_app_notifications WHERE packageName = :packageName ORDER BY postTime ASC")
    suspend fun getNotificationsListForPackage(packageName: String): List<CollectedAppNotification>

    @Query("SELECT COUNT(*) FROM collected_app_notifications WHERE packageName = :packageName")
    fun getNotificationCountFlow(packageName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM collected_app_notifications WHERE packageName = :packageName")
    suspend fun getNotificationCount(packageName: String): Int

    @Query("DELETE FROM collected_app_notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Long)

    @Query("DELETE FROM collected_app_notifications WHERE packageName = :packageName")
    suspend fun clearNotificationsByPackage(packageName: String)

    // Summary Records
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaryRecord(record: AppSummaryRecord): Long

    @Query("SELECT * FROM app_summary_records WHERE packageName = :packageName ORDER BY createdAt DESC")
    fun getSummaryRecordsByPackage(packageName: String): Flow<List<AppSummaryRecord>>

    @Query("SELECT COUNT(*) FROM app_summary_records WHERE packageName = :packageName")
    fun getSummaryCountFlow(packageName: String): Flow<Int>

    @Delete
    suspend fun deleteSummaryRecord(record: AppSummaryRecord)

    @Query("DELETE FROM app_summary_records WHERE packageName = :packageName")
    suspend fun clearSummaryRecordsByPackage(packageName: String)
}
