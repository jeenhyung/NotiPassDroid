package com.notipass.droid.model

enum class SummaryDeliveryType {
    LOCAL_NOTIFICATION, // 내 폰 상단바 알림만 띄우기
    SELF_SMS,           // 내 번호(나 자신)에게 문자로 발송
    TARGET_SMS,         // 지정된 다른 번호로 문자 발송
    ALL                 // 상단바 알림 + 문자(SMS) 모두 발송
}
