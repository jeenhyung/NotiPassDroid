package com.notipass.droid.data.db

import androidx.room.*
import com.notipass.droid.model.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM notification_logs ORDER BY id DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<NotificationLog>>

    @Query("SELECT COUNT(*) FROM notification_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Insert
    suspend fun insertLog(log: NotificationLog): Long

    @Query("DELETE FROM notification_logs")
    suspend fun clearAll()
}
