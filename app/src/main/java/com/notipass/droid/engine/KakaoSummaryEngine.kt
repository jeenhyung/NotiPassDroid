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
import com.notipass.droid.R
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.data.preference.AppPreferences
import com.notipass.droid.data.storage.KakaoLogStorage
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.SummaryDeliveryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KakaoSummaryEngine {

    private const val TAG = "KakaoSummaryEngine"
    private const val CHANNEL_ID = "kakao_summary_channel"
    private const val CHANNEL_NAME = "카카오톡 AI 단톡방 요약"

    suspend fun executeSummary(
        context: Context,
        config: KakaoSummaryConfig,
        isManual: Boolean = false
    ): GeminiResult = withContext(Dispatchers.IO) {
        val roomName = config.roomName
        val rawLogs = KakaoLogStorage.readLogs(context, roomName)
        val messageCount = KakaoLogStorage.getMessageCount(context, roomName)

        if (rawLogs.isBlank() || messageCount == 0) {
            return@withContext GeminiResult(false, errorMessage = "요약할 대화 내용이 아직 없습니다.")
        }

        val apiKey = AppPreferences.getGeminiApiKey(context)
        if (apiKey.isBlank()) {
            val err = "Gemini API 키가 설정되지 않았습니다. [단톡방 AI 요약] 화면에서 등록해 주세요."
            postLocalNotification(context, roomName, err, isError = true)
            return@withContext GeminiResult(false, errorMessage = err)
        }

        // Call Gemini
        val result = GeminiSummarizer.summarize(apiKey, roomName, rawLogs)
        val db = AppDatabase.getDatabase(context)

        if (result.isSuccess && !result.summary.isNullOrBlank()) {
            val summaryText = result.summary.trim()
            val fullMessage = "[AI 요약: $roomName]\n$summaryText\n(총 ${messageCount}개 대화 정리)"

            // 1. Deliver to Local Notification
            if (config.deliveryType == SummaryDeliveryType.LOCAL_NOTIFICATION || config.deliveryType == SummaryDeliveryType.ALL) {
                postLocalNotification(context, roomName, "$summaryText (총 ${messageCount}개 대화)")
            }

            // 2. Deliver via SMS (Self or Target)
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

            // 3. Deliver to Google Drive if enabled
            if (AppPreferences.isGoogleDriveEnabled(context)) {
                GoogleDriveDispatcher.appendSummary(context, roomName, summaryText, messageCount)
            }

            // Record to Database Logs
            val logEntry = NotificationLog(
                ruleName = "단톡방 AI 요약: $roomName",
                packageName = "com.kakao.talk",
                appName = "카카오톡 AI 요약",
                title = "[$roomName] 대화 ${messageCount}개 요약 완료",
                content = summaryText,
                status = LogStatus.SUCCESS,
                responseBody = smsStatusDesc ?: "요약 완료",
                timestamp = System.currentTimeMillis()
            )
            db.logDao().insertLog(logEntry)

            // Update last summarized timestamp
            db.kakaoSummaryDao().updateLastSummarizedTime(config.id, System.currentTimeMillis())

            // Clear log file for the next summary cycle
            KakaoLogStorage.clearLogs(context, roomName)
            Log.i(TAG, "Successfully summarized $roomName and cleared logs.")

            result
        } else {
            val errorMsg = result.errorMessage ?: "알 수 없는 요약 오류"
            if (isManual) {
                postLocalNotification(context, roomName, errorMsg, isError = true)
            }

            val logEntry = NotificationLog(
                ruleName = "단톡방 AI 요약: $roomName",
                packageName = "com.kakao.talk",
                appName = "카카오톡 AI 요약",
                title = "[$roomName] 요약 실패",
                content = errorMsg,
                status = LogStatus.FAILED,
                errorMessage = errorMsg,
                timestamp = System.currentTimeMillis()
            )
            db.logDao().insertLog(logEntry)

            result
        }
    }

    private fun postLocalNotification(
        context: Context,
        roomName: String,
        content: String,
        isError: Boolean = false
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "단톡방 AI 1줄 요약 알림을 제공합니다."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            roomName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isError) "⚠️ [AI 요약 오류] $roomName" else "🔔 [AI 단톡방 요약] $roomName"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(roomName.hashCode(), notification)
    }
}
