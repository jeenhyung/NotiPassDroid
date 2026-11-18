package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_collector_configs",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class AppCollectorConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val dismissNotification: Boolean = false, // 기본값: false (상단바 알림 유지)
    val summaryThresholdCount: Int = 30, // 누적 N건 도달 시 자동 요약 (0이면 수동 또는 스케줄 전용)
    val isScheduledTimeEnabled: Boolean = false,
    val scheduledHour: Int = 21,
    val scheduledMinute: Int = 0,
    val deliveryType: SummaryDeliveryType = SummaryDeliveryType.LOCAL_NOTIFICATION,
    val targetSmsNumber: String = "",
    val summaryStyle: SummaryStyle = SummaryStyle.AUTO,
    val lastSummarizedTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
