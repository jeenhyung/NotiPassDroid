package com.notipass.droid.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.receiver.KakaoSummaryAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object KakaoSummaryScheduler {

    private const val TAG = "KakaoSummaryScheduler"

    fun schedule(context: Context, config: KakaoSummaryConfig) {
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

        val intent = Intent(context, KakaoSummaryAlarmReceiver::class.java).apply {
            action = KakaoSummaryAlarmReceiver.ACTION_TRIGGER_SUMMARY
            putExtra(KakaoSummaryAlarmReceiver.EXTRA_CONFIG_ID, config.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            config.id.toInt(),
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
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            Log.i(TAG, "Scheduled alarm for '${config.roomName}' at ${calendar.time} (epoch: ${calendar.timeInMillis})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for '${config.roomName}'", e)
        }
    }

    fun cancel(context: Context, configId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, KakaoSummaryAlarmReceiver::class.java).apply {
            action = KakaoSummaryAlarmReceiver.ACTION_TRIGGER_SUMMARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            configId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Canceled scheduled alarm for configId=$configId")
        }
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val configs = db.kakaoSummaryDao().getEnabledConfigs()
                configs.filter { it.isScheduledTimeEnabled }.forEach { config ->
                    schedule(context, config)
                }
                Log.i(TAG, "Rescheduled all active summary timers (${configs.size} total)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms", e)
            }
        }
    }
}
