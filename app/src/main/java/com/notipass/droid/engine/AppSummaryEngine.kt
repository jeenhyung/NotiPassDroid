package com.notipass.droid.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notipass.droid.MainActivity
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.data.preference.AppPreferences
import com.notipass.droid.model.AppCollectorConfig
import com.notipass.droid.model.AppSummaryRecord
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.SummaryDeliveryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppSummaryEngine {

    private const val TAG = "AppSummaryEngine"
    private const val CHANNEL_ID = "channel_app_summary"
    private const val CHANNEL_NAME = "앱 알림 AI 요약"

    suspend fun executeSummary(
        context: Context,
        config: AppCollectorConfig,
        isManual: Boolean = false
    ): GeminiResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val notifications = db.appCollectorDao().getNotificationsListForPackage(config.packageName)
        val count = notifications.size

        if (count == 0) {
            Log.d(TAG, "No notifications to summarize for ${config.appName}")
            return@withContext GeminiResult(false, errorMessage = "요약할 알림 내역이 아직 없습니다.")
        }

        val apiKey = AppPreferences.getGeminiApiKey(context)
        if (apiKey.isBlank()) {
            val err = "Gemini API 키가 설정되지 않았습니다. [AI 요약] 화면에서 API 키를 먼저 등록해 주세요."
            postLocalNotification(context, config.appName, err, isError = true, packageName = config.packageName)
            return@withContext GeminiResult(false, errorMessage = err)
        }

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val rawText = notifications.joinToString("\n") { n ->
            val titleStr = if (n.title.isNotBlank()) "[${n.title}] " else ""
            "- (${timeFormat.format(Date(n.postTime))}) $titleStr${n.content}"
        }

        val result = GeminiSummarizer.summarizeAppNotifications(
            apiKey = apiKey,
            appName = config.appName,
            notificationsText = rawText,
            notificationCount = count,
            style = config.summaryStyle
        )

        if (result.isSuccess && !result.summary.isNullOrBlank()) {
            val summaryText = result.summary.trim()
            val fullMessage = "[${config.appName} 알림 요약]\n$summaryText\n(총 ${count}건 알림 정리)"

            // 1. Save to AppSummaryRecord history
            val record = AppSummaryRecord(
                configId = config.id,
                packageName = config.packageName,
                appName = config.appName,
                notificationCount = count,
                summaryText = summaryText,
                style = config.summaryStyle,
                deliveryType = config.deliveryType,
                createdAt = System.currentTimeMillis()
            )
            val recordId = db.appCollectorDao().insertSummaryRecord(record)
            Log.i(TAG, "Saved app summary record #$recordId for ${config.appName}")

            // 2. Deliver to Local Notification
            if (config.deliveryType == SummaryDeliveryType.LOCAL_NOTIFICATION || config.deliveryType == SummaryDeliveryType.ALL) {
                postLocalNotification(context, config.appName, summaryText, recordId = recordId, packageName = config.packageName)
            }

            // 3. Deliver via SMS
            var smsStatusDesc: String? = null
            if (config.deliveryType == SummaryDeliveryType.SELF_SMS ||
                config.deliveryType == SummaryDeliveryType.TARGET_SMS ||
                config.deliveryType == SummaryDeliveryType.ALL) {

                val targetNumber = if (config.deliveryType == SummaryDeliveryType.SELF_SMS) {
                    AppPreferences.getMyPhoneNumber(context).ifBlank { config.targetSmsNumber }
                } else {
                    config.targetSmsNumber.ifBlank { AppPreferences.getMyPhoneNumber(context) }
                }

                if (targetNumber.isNotBlank()) {
                    val smsResult = SmsDispatcher.send(context, targetNumber, fullMessage)
                    smsStatusDesc = if (smsResult.isSuccess) {
                        "SMS 발송 완료 ($targetNumber)"
                    } else {
                        "SMS 실패: ${smsResult.errorMessage}"
                    }
                } else {
                    smsStatusDesc = "SMS 발송 실패 (수신 전화번호 미설정)"
                }
            }

            // 4. Record to Database Logs
            val logEntry = NotificationLog(
                ruleName = "앱 알림 AI 요약: ${config.appName}",
                packageName = config.packageName,
                appName = config.appName,
                title = "[${config.appName}] 알림 ${count}건 요약 완료",
                content = summaryText,
                status = LogStatus.SUCCESS,
                responseBody = smsStatusDesc ?: "요약 완료",
                timestamp = System.currentTimeMillis()
            )
            db.logDao().insertLog(logEntry)

            // 5. Update last summarized time
            db.appCollectorDao().updateLastSummarizedTime(config.id, System.currentTimeMillis())

            // 6. Clear accumulated notifications for the next summary batch
            db.appCollectorDao().clearNotificationsByPackage(config.packageName)
            Log.i(TAG, "Successfully summarized ${config.appName} ($count items) and cleared queue.")

            result
        } else {
            val errorMsg = result.errorMessage ?: "알 수 없는 요약 오류"
            if (isManual) {
                postLocalNotification(context, config.appName, errorMsg, isError = true, packageName = config.packageName)
            }

            val logEntry = NotificationLog(
                ruleName = "앱 알림 AI 요약: ${config.appName}",
                packageName = config.packageName,
                appName = config.appName,
                title = "[${config.appName}] 요약 실패",
                content = errorMsg,
                status = LogStatus.FAILED,
                errorMessage = errorMsg,
                timestamp = System.currentTimeMillis()
            )
            db.logDao().insertLog(logEntry)

            result
        }
    }

    fun postLocalNotification(
        context: Context,
        appName: String,
        content: String,
        recordId: Long = 0,
        packageName: String = "",
        isError: Boolean = false
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "수집된 앱 알림의 AI 요약 결과를 알려드립니다."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_APP_SUMMARY_HISTORY", true)
            putExtra("EXTRA_PACKAGE_NAME", packageName)
            putExtra("EXTRA_APP_NAME", appName)
            putExtra("EXTRA_RECORD_ID", recordId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (packageName + recordId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isError) "⚠️ [$appName] AI 요약 실패" else "📋 [$appName] AI 알림 요약"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((packageName + "summary").hashCode(), notification)
    }
}
