package com.notipass.droid.data.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

object KakaoLogStorage {

    private const val TAG = "KakaoLogStorage"
    private const val LOGS_DIR = "kakao_chat_logs"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Tracks last appended message per room to prevent duplicate writes: roomName -> Pair(senderKey, timestamp)
    private val lastAppendedMap = Collections.synchronizedMap(mutableMapOf<String, Pair<String, Long>>())

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
            val trimmedText = text.trim()
            if (trimmedText.isBlank()) return ""

            val senderKey = "$sender:$trimmedText"
            val now = System.currentTimeMillis()
            val last = lastAppendedMap[roomName]
            if (last != null && last.first == senderKey && (now - last.second < 2500L)) {
                Log.d(TAG, "Skipped duplicate message append for $roomName: $senderKey")
                return ""
            }
            lastAppendedMap[roomName] = Pair(senderKey, now)

            val file = getLogFile(context, roomName)
            val timeStr = timeFormat.format(Date(timestamp))
            val line = "[$timeStr] $sender: $trimmedText\n"
            file.appendText(line, Charsets.UTF_8)
            Log.d(TAG, "Appended to ${file.name}: $line")
            line
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append message to log for $roomName", e)
            ""
        }
    }

    fun deduplicateContent(content: String): String {
        if (content.isBlank()) return ""
        val lines = content.lines()
        val result = mutableListOf<String>()
        var lastBody: String? = null
        val regex = Regex("^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s*(.*)$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val currentBody = regex.find(trimmed)?.groupValues?.getOrNull(1) ?: trimmed
            if (currentBody == lastBody) {
                // Skip consecutive duplicate
                continue
            }
            result.add(trimmed)
            lastBody = currentBody
        }
        return result.joinToString("\n")
    }

    @Synchronized
    fun cleanExistingDuplicates(context: Context, roomName: String) {
        val file = getLogFile(context, roomName)
        if (!file.exists()) return
        try {
            val text = file.readText(Charsets.UTF_8)
            val deduped = deduplicateContent(text)
            file.writeText(if (deduped.isNotBlank()) "$deduped\n" else "", Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean duplicates for $roomName", e)
        }
    }

    fun readLogs(context: Context, roomName: String): String {
        val file = getLogFile(context, roomName)
        return if (file.exists()) {
            try {
                val raw = file.readText(Charsets.UTF_8)
                deduplicateContent(raw)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    fun getMessageCount(context: Context, roomName: String): Int {
        val content = readLogs(context, roomName)
        if (content.isBlank()) return 0
        return content.lines().count { it.isNotBlank() }
    }

    @Synchronized
    fun clearLogs(context: Context, roomName: String) {
        val file = getLogFile(context, roomName)
        if (file.exists()) {
            try {
                file.delete()
                lastAppendedMap.remove(roomName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete log file for $roomName", e)
            }
        }
    }
}
