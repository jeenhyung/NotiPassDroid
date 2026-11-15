package com.notipass.droid.engine

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object SmsDispatcher {

    private const val TAG = "SmsDispatcher"

    suspend fun send(
        context: Context,
        destinationAddress: String,
        message: String
    ): SmsResult = withContext(Dispatchers.IO) {
        val cleanNumber = destinationAddress.trim().replace("-", "")
        if (cleanNumber.isBlank()) {
            return@withContext SmsResult(false, "수신 전화번호가 비어있습니다.")
        }
        if (message.isBlank()) {
            return@withContext SmsResult(false, "발송할 메시지 내용이 비어있습니다.")
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
            }

            Log.i(TAG, "SMS successfully dispatched to $cleanNumber: $message")
            SmsResult(isSuccess = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $cleanNumber", e)
            SmsResult(isSuccess = false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
