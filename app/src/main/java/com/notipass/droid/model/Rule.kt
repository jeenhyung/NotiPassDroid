package com.notipass.droid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,

    // Trigger Configuration
    val triggerType: TriggerType = TriggerType.NOTIFICATION,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val titleMatchMode: MatchMode = MatchMode.IGNORE,
    val titleKeyword: String = "",
    val contentMatchMode: MatchMode = MatchMode.IGNORE,
    val contentKeyword: String = "",

    // Action Configuration
    val actionType: ActionType = ActionType.WEBHOOK,

    // Webhook Settings
    val webhookUrl: String = "",
    val webhookMethod: String = "POST",
    val webhookHeadersJson: String = "{}",
    val webhookBodyTemplate: String = """{"title":"{title}","content":"{content}","app":"{app}","time":"{time}"}""",

    // SMS Settings
    val smsTargetNumber: String = "", // e.g. "010-1234-5678" or "{caller}"
    val smsTemplate: String = """[NotiPass] {caller} 번호로 전화가 수신되었습니다. ({date})""",

    val createdAt: Long = System.currentTimeMillis()
)
