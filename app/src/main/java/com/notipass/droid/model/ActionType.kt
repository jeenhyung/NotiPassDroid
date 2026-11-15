package com.notipass.droid.model

enum class ActionType {
    WEBHOOK,    // HTTP Webhook 전송
    SMS,        // SMS 문자 발송
    BOTH        // 웹훅 및 SMS 동시 실행
}
