package com.notipass.droid.data.preference

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {

    private const val PREF_NAME = "notipass_preferences"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_MY_PHONE_NUMBER = "my_phone_number"
    private const val KEY_GOOGLE_DRIVE_URL = "google_drive_url"
    private const val KEY_GOOGLE_DRIVE_ENABLED = "google_drive_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getGeminiApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun setGeminiApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun getMyPhoneNumber(context: Context): String {
        return getPrefs(context).getString(KEY_MY_PHONE_NUMBER, "") ?: ""
    }

    fun setMyPhoneNumber(context: Context, number: String) {
        getPrefs(context).edit().putString(KEY_MY_PHONE_NUMBER, number.trim()).apply()
    }

    fun getGoogleDriveUrl(context: Context): String {
        return getPrefs(context).getString(KEY_GOOGLE_DRIVE_URL, "") ?: ""
    }

    fun setGoogleDriveUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_GOOGLE_DRIVE_URL, url.trim()).apply()
    }

    fun isGoogleDriveEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GOOGLE_DRIVE_ENABLED, false)
    }

    fun setGoogleDriveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GOOGLE_DRIVE_ENABLED, enabled).apply()
    }
}
