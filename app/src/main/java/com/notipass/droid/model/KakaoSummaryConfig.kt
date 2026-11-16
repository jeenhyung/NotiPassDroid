package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kakao_summary_configs")
data class KakaoSummaryConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val roomName: String,
    val isEnabled: Boolean = true,
    val dismissNotification: Boolean = true, // 단톡방 알림 상단바에서 즉시 지우기 (소리/진동 차단)
    val summaryThresholdCount: Int = 30,     // 대화 N개 누적 시 자동 요약 (0이면 수동 전용)
    val deliveryType: SummaryDeliveryType = SummaryDeliveryType.SELF_SMS,
    val targetSmsNumber: String = "",        // 빈값이면 AppPreferences의 내 번호 사용
    val isScheduledTimeEnabled: Boolean = false, // 매일 지정된 시간에 자동 요약
    val scheduledHour: Int = 21,                 // 0~23 (기본 오후 9시)
    val scheduledMinute: Int = 0,                // 0~59 (기본 0분)
    val lastSummarizedTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
