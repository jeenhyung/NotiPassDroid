package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long? = null,
    val ruleName: String? = null,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: LogStatus,
    val httpCode: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null
)
