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
        val extra = mapOf(
            "title" to payload.title,
            "content" to payload.content,
            "text" to payload.content,
            "app" to payload.appName,
            "appName" to payload.appName,
            "package" to payload.packageName,
            "packageName" to payload.packageName,
            "time" to payload.postTime.toString(),
            "date" to dateFormat.format(Date(payload.postTime))
        )
        return renderWithVariables(template, extra + regexGroups, isJson = true)
    }

    fun renderText(
        template: String,
        variables: Map<String, String>,
        isJson: Boolean = false
    ): String {
        val dateStr = dateFormat.format(Date())
        val baseVars = mapOf(
            "date" to dateStr,
            "time" to System.currentTimeMillis().toString()
        )
        return renderWithVariables(template, baseVars + variables, isJson)
    }

    private fun renderWithVariables(
        template: String,
        variables: Map<String, String>,
        isJson: Boolean
    ): String {
        var result = template
        for ((key, value) in variables) {
            val safeVal = if (isJson) escapeJson(value) else value
            result = result.replace("{$key}", safeVal)
        }
        return result
    }

    private fun escapeJson(input: String): String {
        return JSONObject.quote(input).let {
            if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) {
                it.substring(1, it.length - 1)
            } else {
                it
            }
        }
    }
}
