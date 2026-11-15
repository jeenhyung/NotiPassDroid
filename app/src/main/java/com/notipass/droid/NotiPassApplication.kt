package com.notipass.droid

import android.app.Application
import com.notipass.droid.data.db.AppDatabase

class NotiPassApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
