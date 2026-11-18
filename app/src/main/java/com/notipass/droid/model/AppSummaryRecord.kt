package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_summary_records",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["configId"])
    ]
)
data class AppSummaryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val configId: Long = 0,
    val packageName: String,
    val appName: String,
    val notificationCount: Int,
    val summaryText: String,
    val style: SummaryStyle = SummaryStyle.AUTO,
    val deliveryType: SummaryDeliveryType = SummaryDeliveryType.LOCAL_NOTIFICATION,
    val createdAt: Long = System.currentTimeMillis()
)
