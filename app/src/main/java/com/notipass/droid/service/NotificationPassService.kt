package com.notipass.droid.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.data.storage.KakaoLogStorage
import com.notipass.droid.engine.AppSummaryEngine
import com.notipass.droid.engine.GoogleDriveDispatcher
import com.notipass.droid.engine.KakaoSummaryEngine
import com.notipass.droid.engine.NotificationPayload
import com.notipass.droid.engine.RuleMatcher
import com.notipass.droid.engine.SmsDispatcher
import com.notipass.droid.engine.TemplateEngine
import com.notipass.droid.engine.WebhookDispatcher
import com.notipass.droid.model.ActionType
import com.notipass.droid.model.CollectedAppNotification
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.TriggerType
import com.notipass.droid.util.ContactUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationPassService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appNameCache = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "NotiPassService"
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()

        // Cache to deduplicate rapid duplicate notifications (KakaoTalk group summary / micro-updates)
        private val recentNotificationCache = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                    return size > 250
                }
            }
        )

        // Ultra-fast in-memory cache of room names configured to dismiss notifications (0ms latency for Galaxy Watch / Wear OS)
        @Volatile
        private var cachedDismissRooms: Set<String> = emptySet()

        fun updateDismissRoomsCache(rooms: Set<String>) {
            cachedDismissRooms = rooms.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            Log.d(TAG, "Updated cachedDismissRooms: $cachedDismissRooms")
        }

        fun refreshDismissRoomsCache(context: Context) {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val dismissRooms = db.kakaoSummaryDao().getEnabledConfigs()
                        .filter { it.dismissNotification }
                        .map { it.roomName.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    updateDismissRoomsCache(dismissRooms)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh dismiss rooms cache", e)
            }
        }

        // App collector in-memory caches
        @Volatile
        private var cachedCollectorPackages: Set<String> = emptySet()

        @Volatile
        private var cachedDismissPackages: Set<String> = emptySet()

        fun updateCollectorCache(packages: Set<String>, dismissPackages: Set<String>) {
            cachedCollectorPackages = packages
            cachedDismissPackages = dismissPackages
            Log.d(TAG, "Updated collector cache: collectors=$cachedCollectorPackages, dismiss=$cachedDismissPackages")
        }

        fun refreshCollectorCache(context: Context) {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val configs = db.appCollectorDao().getEnabledConfigs()
                    val packages = configs.map { it.packageName }.toSet()
                    val dismiss = configs.filter { it.dismissNotification }.map { it.packageName }.toSet()
                    updateCollectorCache(packages, dismiss)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh collector cache", e)
            }
        }

        fun rebindService(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    requestRebind(ComponentName(context, NotificationPassService::class.java))
                    Log.i(TAG, "Requested NotificationListenerService rebind")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rebind NotificationListenerService", e)
            }
        }

        suspend fun processNotificationPayload(context: Context, payload: NotificationPayload) {
            val db = AppDatabase.getDatabase(context)
            val enabledRules = db.ruleDao().getEnabledRules()
                .filter { it.triggerType == TriggerType.NOTIFICATION }

            if (enabledRules.isEmpty()) {
                Log.d(TAG, "No enabled rules for NOTIFICATION trigger")
                return
            }

            // Try to resolve contact phone number if title matches a contact name in phonebook
            val senderPhone = ContactUtil.getPhoneNumberByName(context, payload.title)

            for (rule in enabledRules) {
                val matchResult = RuleMatcher.match(rule, payload)
                if (matchResult.isMatched) {
                    var actionSuccess = true
                    var actionError: String? = null
                    var responseBody: String? = null
                    var httpCode: Int? = null

                    // 1. Webhook Action
                    if (rule.actionType == ActionType.WEBHOOK || rule.actionType == ActionType.BOTH) {
                        val renderedBody = TemplateEngine.render(
                            template = rule.webhookBodyTemplate,
                            payload = payload,
                            regexGroups = matchResult.regexGroups,
                            isJson = true
                        )
                        val renderedUrl = TemplateEngine.render(
                            template = rule.webhookUrl,
                            payload = payload,
                            regexGroups = matchResult.regexGroups,
                            isJson = false
                        )

                        val webhookResult = WebhookDispatcher.send(
                            url = renderedUrl,
                            method = rule.webhookMethod,
                            headersJson = rule.webhookHeadersJson,
                            bodyContent = renderedBody
                        )
                        httpCode = webhookResult.statusCode
                        responseBody = webhookResult.responseBody
                        if (!webhookResult.isSuccess) {
                            actionSuccess = false
                            actionError = "웹훅 실패: ${webhookResult.errorMessage}"
                        }
                    }

                    // 2. SMS Action
                    if (rule.actionType == ActionType.SMS || rule.actionType == ActionType.BOTH) {
                        val targetNumber = if (rule.smsTargetNumber.contains("{caller}")) {
                            senderPhone ?: ""
                        } else {
                            rule.smsTargetNumber
                        }

                        if (targetNumber.isNotBlank()) {
                            val smsText = TemplateEngine.render(
                                template = rule.smsTemplate,
                                payload = payload,
                                regexGroups = matchResult.regexGroups,
                                isJson = false
                            )
                            val smsResult = SmsDispatcher.send(context, targetNumber, smsText)
                            if (!smsResult.isSuccess) {
                                actionSuccess = false
                                actionError = (actionError?.let { "$it | " } ?: "") + "SMS 실패: ${smsResult.errorMessage}"
                            } else {
                                responseBody = (responseBody?.let { "$it | " } ?: "") + "SMS 전송 완료 ($targetNumber)"
                            }
                        } else {
                            actionSuccess = false
                            actionError = "SMS 수신 대상 번호가 비어있습니다."
                        }
                    }

                    // Record Log
                    val logEntry = NotificationLog(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        packageName = payload.packageName,
                        appName = payload.appName,
                        title = "알림 감지: ${payload.senderDisplay}",
                        content = if (rule.actionType != ActionType.WEBHOOK) {
                            "문자 발송 -> ${rule.smsTargetNumber}: ${payload.content}"
                        } else {
                            "웹훅 전송: ${payload.content}"
                        },
                        timestamp = payload.postTime,
                        status = if (actionSuccess) LogStatus.SUCCESS else LogStatus.FAILED,
                        httpCode = httpCode,
                        responseBody = responseBody,
                        errorMessage = actionError
                    )
                    db.logDao().insertLog(logEntry)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        refreshDismissRoomsCache(applicationContext)
        refreshCollectorCache(applicationContext)
        Log.i(TAG, "NotificationPassService connected and listening.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
        Log.i(TAG, "NotificationPassService disconnected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        // Ignore self
        if (pkgName == packageName) return

        val notification = sbn.notification ?: return

        // 1. Filter out Android Group Summary notifications (KakaoTalk group bundle)
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "Ignored group summary notification from $pkgName (key=${sbn.key})")
            return
        }

        // 2. Filter out ongoing event notifications (e.g. calls, music playback)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            Log.d(TAG, "Ignored ongoing event notification from $pkgName")
            return
        }

        val extras = notification.extras ?: return

        // 1. Title Extraction
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ""

        // 2. Text / Message Body Extraction (Comprehensive for KakaoTalk & Messengers)
        var text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        // If text is empty, check textLines (InboxStyle)
        if (text.isBlank()) {
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) {
                text = textLines.lastOrNull()?.toString() ?: ""
            }
        }

        // If text is still empty, check MessagingStyle messages (Android 9+ KakaoTalk)
        if (text.isBlank()) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val lastMsg = messages.lastOrNull() as? Bundle
                text = lastMsg?.getCharSequence("text")?.toString() ?: ""
                if (title.isBlank() || title == "카카오톡") {
                    val sender = lastMsg?.getCharSequence("sender")?.toString()
                    if (!sender.isNullOrBlank()) {
                        title = sender
                    }
                }
            }
        }

        // 3. SubText (Chat room name in KakaoTalk group chats)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Ultra-Fast Synchronous Dismissal for KakaoTalk Monitored Rooms (Prevents Galaxy Watch / Wear OS vibration)
        if (pkgName == "com.kakao.talk") {
            val cleanSub = subText.trim()
            val cleanTitle = title.trim()
            val matchesDismissRoom = cachedDismissRooms.any { room ->
                room.isNotEmpty() && (
                    cleanSub.equals(room, ignoreCase = true) ||
                    cleanTitle.equals(room, ignoreCase = true) ||
                    cleanSub.contains(room, ignoreCase = true)
                )
            }
            if (matchesDismissRoom) {
                try {
                    // Synchronously cancel immediately before any thread switch or Bluetooth relay!
                    cancelNotification(sbn.key)
                    cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            snoozeNotification(sbn.key, 3600000L) // 1 hour snooze to suppress wearable alerts
                        } catch (_: Exception) {}
                    }
                    Log.i(TAG, "Ultra-fast synchronous cancel for KakaoTalk room (key=${sbn.key})")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed ultra-fast cancel", e)
                }
            }
        }

        // Ultra-Fast Synchronous Dismissal for App Collector configured packages with dismiss=true
        if (cachedDismissPackages.contains(pkgName)) {
            try {
                cancelNotification(sbn.key)
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        snoozeNotification(sbn.key, 3600000L)
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "Ultra-fast synchronous cancel for collector package: $pkgName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed ultra-fast cancel for package $pkgName", e)
            }
        }

        // Skip if both title and text are completely blank
        if (title.isBlank() && text.isBlank()) {
            Log.d(TAG, "Ignored empty notification from $pkgName")
            return
        }

        // 3. De-duplicate identical notifications arriving in rapid bursts (within 2.5 seconds)
        val dedupeKey = "$pkgName|$title|$text|$subText"
        val now = System.currentTimeMillis()
        val lastSeen = recentNotificationCache[dedupeKey] ?: 0L
        if (now - lastSeen < 2500L) {
            Log.d(TAG, "Ignored duplicate notification within 2.5s: $dedupeKey")
            return
        }
        recentNotificationCache[dedupeKey] = now

        // Try to resolve contact phone number if title matches a contact name in phonebook
        val senderPhone = ContactUtil.getPhoneNumberByName(applicationContext, title)
        val senderDisplay = if (!senderPhone.isNullOrBlank()) {
            "$title($senderPhone)"
        } else {
            title
        }

        val appName = getAppName(pkgName)
        val payload = NotificationPayload(
            packageName = pkgName,
            appName = appName,
            title = title,
            content = text,
            subText = subText,
            senderDisplay = senderDisplay,
            postTime = sbn.postTime
        )

        Log.i(TAG, "Notification received: pkg=$pkgName, title=$title, text=$text, subText=$subText")

        // Process in background coroutine
        serviceScope.launch {
            // Check App Collector
            if (cachedCollectorPackages.contains(pkgName)) {
                handleAppCollectorNotification(sbn, pkgName, title, text, subText)
            }

            if (pkgName == "com.kakao.talk") {
                val intercepted = handleKakaoMessage(sbn, title, text, subText)
                if (intercepted) {
                    return@launch
                }
            }
            processNotificationPayload(applicationContext, payload)
        }
    }

    private suspend fun handleAppCollectorNotification(
        sbn: StatusBarNotification,
        pkgName: String,
        title: String,
        text: String,
        subText: String
    ): Boolean {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val config = db.appCollectorDao().getConfigByPackage(pkgName) ?: return false
            if (!config.isEnabled) return false

            // Dismiss if configured
            if (config.dismissNotification) {
                try {
                    cancelNotification(sbn.key)
                    cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel notification for ${config.packageName}", e)
                }
            }

            // Save to DB
            val appNotification = CollectedAppNotification(
                packageName = config.packageName,
                appName = config.appName,
                title = title.trim(),
                content = text.trim(),
                subText = subText.trim(),
                postTime = sbn.postTime,
                createdAt = System.currentTimeMillis()
            )
            db.appCollectorDao().insertNotification(appNotification)
            Log.d(TAG, "Saved collected notification for ${config.appName}: $title - $text")

            // Check auto summary threshold
            if (config.summaryThresholdCount > 0) {
                val count = db.appCollectorDao().getNotificationCount(config.packageName)
                if (count >= config.summaryThresholdCount) {
                    Log.i(TAG, "Threshold reached ($count/${config.summaryThresholdCount}) for ${config.appName}. Triggering summary...")
                    AppSummaryEngine.executeSummary(applicationContext, config, isManual = false)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling app collector notification", e)
            false
        }
    }

    private suspend fun handleKakaoMessage(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        subText: String
    ): Boolean {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val enabledConfigs = db.kakaoSummaryDao().getEnabledConfigs()
            if (enabledConfigs.isEmpty()) return false

            val matchedConfig = enabledConfigs.firstOrNull { cfg ->
                val target = cfg.roomName.trim()
                target.isNotEmpty() && (
                    subText.trim().equals(target, ignoreCase = true) ||
                    title.trim().equals(target, ignoreCase = true) ||
                    subText.contains(target, ignoreCase = true)
                )
            } ?: return false

            Log.i(TAG, "Matched KakaoTalk room: ${matchedConfig.roomName} (dismiss=${matchedConfig.dismissNotification})")

            // 1. Dismiss notification if configured (suppress sound/vibration/status bar)
            if (matchedConfig.dismissNotification) {
                val rName = matchedConfig.roomName.trim()
                if (!cachedDismissRooms.contains(rName)) {
                    cachedDismissRooms = cachedDismissRooms + rName
                }
                try {
                    cancelNotification(sbn.key)
                    cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                    Log.d(TAG, "Canceled notification key: ${sbn.key}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel notification", e)
                }
            }

            // 2. Append message to local storage
            val sender = if (subText.isNotBlank()) title.ifBlank { "알 수 없음" } else "상대방"
            val line = KakaoLogStorage.appendMessage(applicationContext, matchedConfig.roomName, sender, text, sbn.postTime)

            // 3. Sync to Google Drive in real-time if enabled
            if (line.isNotBlank()) {
                GoogleDriveDispatcher.appendMessageAsync(
                    context = applicationContext,
                    roomName = matchedConfig.roomName,
                    sender = sender,
                    text = text,
                    timestamp = sbn.postTime,
                    formattedLine = line
                )
            }

            // 4. Check message count threshold for auto-summary
            val currentCount = KakaoLogStorage.getMessageCount(applicationContext, matchedConfig.roomName)
            Log.d(TAG, "Room ${matchedConfig.roomName} accumulated: $currentCount / ${matchedConfig.summaryThresholdCount}")

            if (matchedConfig.summaryThresholdCount > 0 && currentCount >= matchedConfig.summaryThresholdCount) {
                Log.i(TAG, "Threshold reached ($currentCount/${matchedConfig.summaryThresholdCount}). Triggering Gemini summary...")
                KakaoSummaryEngine.executeSummary(applicationContext, matchedConfig, isManual = false)
            }

            // Return true if notification was dismissed to suppress further general rule matching
            matchedConfig.dismissNotification
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleKakaoMessage", e)
            false
        }
    }

    private fun getAppName(packageName: String): String {
        return appNameCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }
}
