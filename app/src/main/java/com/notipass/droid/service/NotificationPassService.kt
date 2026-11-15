package com.notipass.droid.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.engine.NotificationPayload
import com.notipass.droid.engine.RuleMatcher
import com.notipass.droid.engine.TemplateEngine
import com.notipass.droid.engine.WebhookDispatcher
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
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
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
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
        val extras = notification.extras ?: return

        // Extract Title
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ""

        // Extract Text (Body)
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        // Skip completely empty noise notifications
        if (title.isBlank() && text.isBlank()) return

        val appName = getAppName(pkgName)
        val payload = NotificationPayload(
            packageName = pkgName,
            appName = appName,
            title = title,
            content = text,
            postTime = sbn.postTime
        )

        // Process matching rules in background coroutine
        serviceScope.launch {
            processNotification(payload)
        }
    }

    private suspend fun processNotification(payload: NotificationPayload) {
        val db = AppDatabase.getDatabase(applicationContext)
        val enabledRules = db.ruleDao().getEnabledRules()

        if (enabledRules.isEmpty()) return

        for (rule in enabledRules) {
            val matchResult = RuleMatcher.match(rule, payload)
            if (matchResult.isMatched) {
                // Render templates
                val renderedBody = TemplateEngine.render(
                    template = rule.webhookBodyTemplate,
                    payload = payload,
                    regexGroups = matchResult.regexGroups
                )
                val renderedUrl = TemplateEngine.render(
                    template = rule.webhookUrl,
                    payload = payload,
                    regexGroups = matchResult.regexGroups
                )

                // Dispatch Webhook
                val webhookResult = WebhookDispatcher.send(
                    url = renderedUrl,
                    method = rule.webhookMethod,
                    headersJson = rule.webhookHeadersJson,
                    bodyContent = renderedBody
                )

                // Record Log
                val logEntry = NotificationLog(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    packageName = payload.packageName,
                    appName = payload.appName,
                    title = payload.title,
                    content = payload.content,
                    timestamp = payload.postTime,
                    status = if (webhookResult.isSuccess) LogStatus.SUCCESS else LogStatus.FAILED,
                    httpCode = webhookResult.statusCode,
                    responseBody = webhookResult.responseBody,
                    errorMessage = webhookResult.errorMessage
                )
                db.logDao().insertLog(logEntry)
            }
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
