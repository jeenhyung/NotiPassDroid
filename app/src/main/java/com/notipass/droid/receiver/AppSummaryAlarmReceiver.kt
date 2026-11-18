package com.notipass.droid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.engine.AppSummaryEngine
import com.notipass.droid.engine.AppSummaryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppSummaryAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppSummaryAlarm"
        const val ACTION_TRIGGER_SUMMARY = "com.notipass.droid.ACTION_TRIGGER_APP_SUMMARY"
        const val EXTRA_CONFIG_ID = "extra_config_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Alarm received: action=$action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TRIGGER_SUMMARY -> {
                        val configId = intent.getLongExtra(EXTRA_CONFIG_ID, -1L)
                        if (configId > 0) {
                            val db = AppDatabase.getDatabase(context)
                            val config = db.appCollectorDao().getConfigById(configId)
                            if (config != null && config.isEnabled) {
                                Log.i(TAG, "Triggering scheduled summary for app '${config.appName}'")
                                AppSummaryEngine.executeSummary(context, config, isManual = false)

                                // Reschedule for next day
                                AppSummaryScheduler.schedule(context, config)
                            } else {
                                Log.w(TAG, "Config not found or disabled for configId=$configId")
                            }
                        }
                    }

                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        Log.i(TAG, "Device rebooted or package replaced. Rescheduling all active app collector timers...")
                        AppSummaryScheduler.rescheduleAll(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling app alarm event", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
