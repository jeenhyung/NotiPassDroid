package com.notipass.droid.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.model.AppCollectorConfig
import com.notipass.droid.receiver.AppSummaryAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object AppSummaryScheduler {

    private const val TAG = "AppSummaryScheduler"

    fun schedule(context: Context, config: AppCollectorConfig) {
        if (!config.isEnabled || !config.isScheduledTimeEnabled) {
            cancel(context, config.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, config.scheduledHour)
            set(Calendar.MINUTE, config.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AppSummaryAlarmReceiver::class.java).apply {
            action = AppSummaryAlarmReceiver.ACTION_TRIGGER_SUMMARY
            putExtra(AppSummaryAlarmReceiver.EXTRA_CONFIG_ID, config.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("app_" + config.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            Log.i(TAG, "Scheduled daily app summary for '${config.appName}' at ${config.scheduledHour}:${config.scheduledMinute} (Target: ${calendar.time})")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied. Falling back to non-exact alarm.", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${config.appName}", e)
        }
    }

    fun cancel(context: Context, configId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AppSummaryAlarmReceiver::class.java).apply {
            action = AppSummaryAlarmReceiver.ACTION_TRIGGER_SUMMARY
            putExtra(AppSummaryAlarmReceiver.EXTRA_CONFIG_ID, configId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("app_" + configId).hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Canceled scheduled app summary for configId=$configId")
        }
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val configs = db.appCollectorDao().getEnabledConfigs()
                configs.filter { it.isScheduledTimeEnabled }.forEach { config ->
                    schedule(context, config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule all app configs", e)
            }
        }
    }
}
