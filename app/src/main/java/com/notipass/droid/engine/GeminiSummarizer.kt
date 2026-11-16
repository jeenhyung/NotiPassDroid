package com.notipass.droid.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiResult(
    val isSuccess: Boolean,
    val summary: String? = null,
    val errorMessage: String? = null
)

object GeminiSummarizer {

    private const val TAG = "GeminiSummarizer"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(
        apiKey: String,
        roomName: String,
        chatLogs: String
    ): GeminiResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "Gemini API 키가 등록되지 않았습니다.")
        }
        if (chatLogs.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "요약할 대화 내용이 없습니다.")
        }

        try {
            val url = "$BASE_URL?key=$cleanKey"

            val prompt = """
당신은 카카오톡 단톡방 AI 비서입니다.
아래는 '$roomName' 단톡방에서 오간 대화 내용입니다.
불필요한 인사나 잡담(ㅋㅋ, 이모티콘, 단순 맞장구 등)은 모두 제외하고,
주요 공지사항, 결정된 약속(일시/장소), 중요한 질문과 답변, 핵심 전달 사항 위주로
한국어 1~3문장으로 간결하게 핵심만 요약해 주세요.
(답변에 '요약 결과입니다:' 같은 사족을 붙이지 말고 요약 내용만 바로 출력하세요.)

[대화 내용]:
$chatLogs
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", prompt)
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error: ${response.code} $responseBodyStr")
                    return@withContext GeminiResult(
                        false,
                        errorMessage = "Gemini 호출 실패 (HTTP ${response.code}): ${parseError(responseBodyStr)}"
                    )
                }

                val jsonResponse = JSONObject(responseBodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val summaryText = parts.getJSONObject(0).optString("text", "").trim()
                        return@withContext GeminiResult(true, summary = summaryText)
                    }
                }

                GeminiResult(false, errorMessage = "응답에서 요약 텍스트를 찾을 수 없습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini summary", e)
            GeminiResult(false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseError(responseStr: String): String {
        return try {
            val json = JSONObject(responseStr)
            val error = json.optJSONObject("error")
            error?.optString("message", responseStr) ?: responseStr.take(150)
        } catch (_: Exception) {
            responseStr.take(150)
        }
    }
}
