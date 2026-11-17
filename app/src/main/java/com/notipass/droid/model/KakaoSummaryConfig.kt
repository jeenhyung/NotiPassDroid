package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SummaryStyle(val displayName: String, val description: String) {
    AUTO("자동 맞춤형 (추천)", "대화량에 따라 간결 요약 또는 심층 보고서로 자동 조절"),
    REPORT("상세 보고서형", "안건/결정사항/일정/세부내용을 포함한 체계적인 심층 보고서"),
    BRIEF("간결형", "바쁜 일상을 위한 2~3문장 퀵 브리핑")
}

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
    val summaryStyle: SummaryStyle = SummaryStyle.AUTO, // 대화 요약 스타일 (자동/보고서/간결)
    val lastSummarizedTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
