package com.notipass.droid.model

enum class TriggerType {
    NOTIFICATION,   // 앱 푸시 알림 수신
    INCOMING_CALL,  // 전화 수신 (벨 울림)
    MISSED_CALL     // 부재중 전화
}
