package com.notipass.droid.data.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KakaoLogStorage {

    private const val TAG = "KakaoLogStorage"
    private const val LOGS_DIR = "kakao_chat_logs"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun getLogsDir(context: Context): File {
        val dir = File(context.filesDir, LOGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_").take(50).ifBlank { "default_room" }
    }

    fun getLogFile(context: Context, roomName: String): File {
        val safeName = sanitizeFileName(roomName)
        return File(getLogsDir(context), "$safeName.txt")
    }

    @Synchronized
    fun appendMessage(context: Context, roomName: String, sender: String, text: String, timestamp: Long = System.currentTimeMillis()): String {
        return try {
            val file = getLogFile(context, roomName)
            val timeStr = timeFormat.format(Date(timestamp))
            val line = "[$timeStr] $sender: $text\n"
            file.appendText(line, Charsets.UTF_8)
            Log.d(TAG, "Appended to ${file.name}: $line")
            line
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append message to log for $roomName", e)
            ""
        }
    }

    fun readLogs(context: Context, roomName: String): String {
        val file = getLogFile(context, roomName)
        return if (file.exists()) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    fun getMessageCount(context: Context, roomName: String): Int {
        val file = getLogFile(context, roomName)
        if (!file.exists()) return 0
        return try {
            file.useLines { lines -> lines.count { it.isNotBlank() } }
        } catch (e: Exception) {
            0
        }
    }

    @Synchronized
    fun clearLogs(context: Context, roomName: String) {
        val file = getLogFile(context, roomName)
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete log file for $roomName", e)
            }
        }
    }
}
