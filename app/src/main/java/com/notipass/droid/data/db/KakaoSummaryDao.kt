package com.notipass.droid.data.db

import androidx.room.*
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.model.KakaoSummaryRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface KakaoSummaryDao {
    // Configs
    @Query("SELECT * FROM kakao_summary_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<KakaoSummaryConfig>>

    @Query("SELECT * FROM kakao_summary_configs WHERE isEnabled = 1")
    suspend fun getEnabledConfigs(): List<KakaoSummaryConfig>

    @Query("SELECT * FROM kakao_summary_configs WHERE roomName = :roomName LIMIT 1")
    suspend fun getConfigByRoom(roomName: String): KakaoSummaryConfig?

    @Query("SELECT * FROM kakao_summary_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Long): KakaoSummaryConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: KakaoSummaryConfig): Long

    @Update
    suspend fun updateConfig(config: KakaoSummaryConfig)

    @Delete
    suspend fun deleteConfig(config: KakaoSummaryConfig)

    @Query("UPDATE kakao_summary_configs SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setConfigEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE kakao_summary_configs SET lastSummarizedTime = :time WHERE id = :id")
    suspend fun updateLastSummarizedTime(id: Long, time: Long)

    // Summary Records (History)
    @Query("SELECT * FROM kakao_summary_records WHERE roomName = :roomName ORDER BY createdAt DESC")
    fun getSummaryRecordsByRoom(roomName: String): Flow<List<KakaoSummaryRecord>>

    @Query("SELECT * FROM kakao_summary_records ORDER BY createdAt DESC LIMIT 100")
    fun getAllSummaryRecords(): Flow<List<KakaoSummaryRecord>>

    @Query("SELECT * FROM kakao_summary_records WHERE id = :id LIMIT 1")
    suspend fun getSummaryRecordById(id: Long): KakaoSummaryRecord?

    @Query("SELECT COUNT(*) FROM kakao_summary_records WHERE roomName = :roomName")
    fun getSummaryCountByRoom(roomName: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaryRecord(record: KakaoSummaryRecord): Long

    @Delete
    suspend fun deleteSummaryRecord(record: KakaoSummaryRecord)

    @Query("DELETE FROM kakao_summary_records WHERE roomName = :roomName")
    suspend fun clearSummaryRecordsByRoom(roomName: String)
}
