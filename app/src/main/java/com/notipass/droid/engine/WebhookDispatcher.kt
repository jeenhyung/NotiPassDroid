package com.notipass.droid.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookResult(
    val isSuccess: Boolean,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null
)

object WebhookDispatcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun send(
        url: String,
        method: String = "POST",
        headersJson: String = "{}",
        bodyContent: String = ""
    ): WebhookResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext WebhookResult(false, errorMessage = "Webhook URL is empty")
        }

        try {
            val requestBuilder = Request.Builder().url(url.trim())

            // Add custom headers
            if (headersJson.isNotBlank() && headersJson != "{}") {
                try {
                    val jsonObj = JSONObject(headersJson)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        requestBuilder.addHeader(key, jsonObj.optString(key, ""))
                    }
                } catch (e: Exception) {
                    // Ignore header parse errors or continue
                }
            }

            if (method.equals("POST", ignoreCase = true)) {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = bodyContent.toRequestBody(mediaType)
                requestBuilder.post(requestBody)
            } else {
                requestBuilder.get()
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val responseStr = response.body?.string()?.take(500)
                if (response.isSuccessful) {
                    WebhookResult(
                        isSuccess = true,
                        statusCode = code,
                        responseBody = responseStr
                    )
                } else {
                    WebhookResult(
                        isSuccess = false,
                        statusCode = code,
                        responseBody = responseStr,
                        errorMessage = "HTTP Error $code: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            WebhookResult(
                isSuccess = false,
                errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
