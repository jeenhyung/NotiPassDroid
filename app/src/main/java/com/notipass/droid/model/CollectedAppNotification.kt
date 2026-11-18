package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collected_app_notifications",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["postTime"])
    ]
)
data class CollectedAppNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val subText: String = "",
    val postTime: Long,
    val createdAt: Long = System.currentTimeMillis()
)
