package com.notipass.droid

import android.app.Application
import android.util.Log
import com.notipass.droid.data.db.AppDatabase

class NotiPassApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NotiPassApplication", "Uncaught exception in thread ${thread.name}", throwable)
            try {
                val errorMsg = "${throwable.javaClass.simpleName}: ${throwable.message}\n\n${throwable.stackTraceToString().take(1200)}"
                CrashActivity.start(this, errorMsg)
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Exception) {
                Log.e("NotiPassApplication", "Failed to launch CrashActivity", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
