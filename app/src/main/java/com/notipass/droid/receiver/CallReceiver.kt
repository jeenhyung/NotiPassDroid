package com.notipass.droid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.engine.SmsDispatcher
import com.notipass.droid.engine.TemplateEngine
import com.notipass.droid.engine.WebhookDispatcher
import com.notipass.droid.model.ActionType
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.TriggerType
import com.notipass.droid.util.ContactUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Session & De-duplication tracking
        private var hasSentIncomingCallAlert = false
        private var fallbackJob: Job? = null
        private var ringingNumber: String? = null
        private var wasRinging = false
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        Log.d(TAG, "Phone State Changed: state=$stateStr, incomingNumber=$rawNumber, wasRinging=$wasRinging")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true

                if (rawNumber.isNotBlank()) {
                    // Valid phone number received
                    ringingNumber = rawNumber
                    fallbackJob?.cancel()
                    fallbackJob = null

                    if (!hasSentIncomingCallAlert) {
                        hasSentIncomingCallAlert = true
                        receiverScope.launch {
                            handleCallTrigger(context, TriggerType.INCOMING_CALL, rawNumber)
                        }
                    }
                } else {
                    // Android OS sends an initial RINGING broadcast before caller ID arrives.
                    // Wait 1.2 seconds for the second broadcast with the actual number.
                    if (!hasSentIncomingCallAlert && fallbackJob == null) {
                        fallbackJob = receiverScope.launch {
                            delay(1200)
                            if (!hasSentIncomingCallAlert) {
                                hasSentIncomingCallAlert = true
                                val finalNumber = ringingNumber ?: "발신번호 미표시"
                                handleCallTrigger(context, TriggerType.INCOMING_CALL, finalNumber)
                            }
                        }
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered: cancel pending fallback
                fallbackJob?.cancel()
                fallbackJob = null
                wasRinging = false
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended or missed
                fallbackJob?.cancel()
                fallbackJob = null

                if (wasRinging) {
                    // Missed call without answering
                    val missedNum = ringingNumber ?: "발신번호 미표시"
                    receiverScope.launch {
                        handleCallTrigger(context, TriggerType.MISSED_CALL, missedNum)
                    }
                }

                // Reset session state for the next call
                wasRinging = false
                hasSentIncomingCallAlert = false
                ringingNumber = null
            }
        }
    }

    private suspend fun handleCallTrigger(context: Context, triggerType: TriggerType, callerNumber: String) {
        val db = AppDatabase.getDatabase(context)
        val enabledRules = db.ruleDao().getEnabledRules()
        val matchingRules = enabledRules.filter { it.triggerType == triggerType }

        if (matchingRules.isEmpty()) {
            Log.d(TAG, "No matching active rules for trigger $triggerType")
            return
        }

        // Resolve Contact Name if registered in user's phone contacts
        val contactName = ContactUtil.getContactName(context, callerNumber)
        val callerDisplay = if (!contactName.isNullOrBlank()) {
            "$contactName($callerNumber)"
        } else {
            callerNumber
        }

        val baseVariables = mapOf(
            "caller" to callerNumber,
            "number" to callerNumber,
            "phone" to callerNumber,
            "name" to (contactName ?: "알 수 없음"),
            "contact" to (contactName ?: "알 수 없음"),
            "caller_name" to (contactName ?: "알 수 없음"),
            "caller_display" to callerDisplay,
            "title" to if (triggerType == TriggerType.INCOMING_CALL) "수신 전화: $callerDisplay" else "부재중 전화: $callerDisplay",
            "app" to "전화 (Phone)",
            "package" to "com.android.phone",
            "content" to "$callerDisplay 님으로부터 전화가 수신되었습니다."
        )

        for (rule in matchingRules) {
            // Optional caller number or contact name filter
            if (rule.titleKeyword.isNotBlank()) {
                val keyword = rule.titleKeyword.trim()
                val matchesNumber = callerNumber.contains(keyword, ignoreCase = true)
                val matchesName = contactName?.contains(keyword, ignoreCase = true) == true
                if (!matchesNumber && !matchesName) continue
            }

            var actionSuccess = true
            var actionError: String? = null
            var responseBody: String? = null
            var httpCode: Int? = null

            // 1. Send SMS Action
            if (rule.actionType == ActionType.SMS || rule.actionType == ActionType.BOTH) {
                val targetNumber = if (rule.smsTargetNumber.isBlank() || rule.smsTargetNumber.contains("{caller}")) {
                    callerNumber
                } else {
                    TemplateEngine.renderText(rule.smsTargetNumber, baseVariables, isJson = false)
                }

                val targetContact = ContactUtil.getContactName(context, targetNumber)
                val targetDisplay = if (!targetContact.isNullOrBlank()) "$targetContact($targetNumber)" else targetNumber

                val smsVariables = baseVariables + mapOf(
                    "target" to targetNumber,
                    "target_number" to targetNumber,
                    "target_display" to targetDisplay
                )

                val smsBody = TemplateEngine.renderText(rule.smsTemplate, smsVariables, isJson = false)
                val smsResult = SmsDispatcher.send(context, targetNumber, smsBody)

                if (!smsResult.isSuccess) {
                    actionSuccess = false
                    actionError = "SMS 발송 실패: ${smsResult.errorMessage}"
                } else {
                    responseBody = "SMS 전송 완료 ($targetDisplay)"
                }
            }

            // 2. Webhook Action
            if (rule.actionType == ActionType.WEBHOOK || rule.actionType == ActionType.BOTH) {
                val renderedUrl = TemplateEngine.renderText(rule.webhookUrl, baseVariables, isJson = false)
                val renderedBody = TemplateEngine.renderText(rule.webhookBodyTemplate, baseVariables, isJson = true)

                val webhookResult = WebhookDispatcher.send(
                    url = renderedUrl,
                    method = rule.webhookMethod,
                    headersJson = rule.webhookHeadersJson,
                    bodyContent = renderedBody
                )
                httpCode = webhookResult.statusCode
                if (!webhookResult.isSuccess) {
                    actionSuccess = false
                    actionError = (actionError?.let { "$it | " } ?: "") + "웹훅 실패: ${webhookResult.errorMessage}"
                }
            }

            // Record Log
            val logEntry = NotificationLog(
                ruleId = rule.id,
                ruleName = rule.name,
                packageName = "com.android.phone",
                appName = if (triggerType == TriggerType.INCOMING_CALL) "수신 전화" else "부재중 전화",
                title = "전화 감지: $callerDisplay",
                content = if (rule.actionType != ActionType.WEBHOOK) {
                    "문자 발송 -> ${rule.smsTargetNumber.ifBlank { callerNumber }}: ${rule.smsTemplate}"
                } else {
                    "웹훅 전송: ${rule.webhookUrl}"
                },
                timestamp = System.currentTimeMillis(),
                status = if (actionSuccess) LogStatus.SUCCESS else LogStatus.FAILED,
                httpCode = httpCode,
                responseBody = responseBody,
                errorMessage = actionError
            )
            db.logDao().insertLog(logEntry)
        }
    }
}
