package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val titleMatchMode: MatchMode = MatchMode.IGNORE,
    val titleKeyword: String = "",
    val contentMatchMode: MatchMode = MatchMode.IGNORE,
    val contentKeyword: String = "",
    val webhookUrl: String = "",
    val webhookMethod: String = "POST",
    val webhookHeadersJson: String = "{}",
    val webhookBodyTemplate: String = """{"title":"{title}","content":"{content}","app":"{app}","time":"{time}"}""",
    val createdAt: Long = System.currentTimeMillis()
)
