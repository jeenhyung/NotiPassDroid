package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kakao_summary_records",
    indices = [
        Index(value = ["roomName"]),
        Index(value = ["createdAt"])
    ]
)
data class KakaoSummaryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val configId: Long = 0,
    val roomName: String,
    val messageCount: Int,
    val summaryText: String,
    val style: SummaryStyle = SummaryStyle.AUTO,
    val deliveryType: SummaryDeliveryType = SummaryDeliveryType.LOCAL_NOTIFICATION,
    val createdAt: Long = System.currentTimeMillis()
)
