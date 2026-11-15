package com.notipass.droid.engine

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TemplateEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun render(
        template: String,
        payload: NotificationPayload,
        regexGroups: Map<String, String> = emptyMap()
    ): String {
        var result = template

        val safeTitle = escapeJson(payload.title)
        val safeContent = escapeJson(payload.content)
        val safeApp = escapeJson(payload.appName)
        val safePackage = escapeJson(payload.packageName)
        val dateStr = dateFormat.format(Date(payload.postTime))

        // Replace standard placeholders
        result = result.replace("{title}", safeTitle)
            .replace("{content}", safeContent)
            .replace("{text}", safeContent)
            .replace("{app}", safeApp)
            .replace("{appName}", safeApp)
            .replace("{package}", safePackage)
            .replace("{packageName}", safePackage)
            .replace("{time}", payload.postTime.toString())
            .replace("{date}", dateStr)

        // Replace regex groups
        for ((key, value) in regexGroups) {
            result = result.replace("{$key}", escapeJson(value))
        }

        return result
    }

    private fun escapeJson(input: String): String {
        return JSONObject.quote(input).let {
            // Remove surrounding double quotes added by JSONObject.quote
            if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) {
                it.substring(1, it.length - 1)
            } else {
                it
            }
        }
    }
}
