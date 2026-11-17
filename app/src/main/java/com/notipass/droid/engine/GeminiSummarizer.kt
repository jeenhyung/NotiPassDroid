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

    // Multi-model candidate list to seamlessly handle 404 / deprecation across v1 and v1beta
    private val CANDIDATE_ENDPOINTS = listOf(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent",
        "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
        "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent"
    )

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

                // Safety Settings: Prevent everyday Korean chat slang/banter from false-positive safety blocking
                val safetyArray = JSONArray().apply {
                    val categories = listOf(
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                    )
                    for (cat in categories) {
                        put(JSONObject().apply {
                            put("category", cat)
                            put("threshold", "BLOCK_NONE")
                        })
                    }
                }
                put("safetySettings", safetyArray)

                // Generation Config
                val genConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 800)
                }
                put("generationConfig", genConfig)
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

            var lastError = "알 수 없는 오류"

            // Fallback loop through candidate endpoints
            for (baseUrl in CANDIDATE_ENDPOINTS) {
                val url = "$baseUrl?key=$cleanKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseBodyStr = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Gemini endpoint $baseUrl failed with HTTP ${response.code}: $responseBodyStr")
                            lastError = "Gemini 호출 실패 (HTTP ${response.code}): ${parseError(responseBodyStr)}"
                            if (response.code == 404) {
                                // 404 indicates this specific model or API version is not routed. Try next candidate!
                                return@use
                            } else {
                                // 400 (Bad Request/Invalid Key) or 403 (Permission) means key problem, no need to retry
                                return@withContext GeminiResult(false, errorMessage = lastError)
                            }
                        }

                        val jsonResponse = JSONObject(responseBodyStr)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val finishReason = firstCandidate.optString("finishReason", "")
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val summaryText = parts.getJSONObject(0).optString("text", "").trim()
                                if (summaryText.isNotBlank()) {
                                    Log.i(TAG, "Successfully generated summary using endpoint: $baseUrl")
                                    return@withContext GeminiResult(true, summary = summaryText)
                                }
                            }
                            if (finishReason.isNotBlank() && finishReason != "STOP") {
                                return@withContext GeminiResult(false, errorMessage = "Gemini 생성 중단 (사유: $finishReason).")
                            }
                        }

                        val promptFeedback = jsonResponse.optJSONObject("promptFeedback")
                        val blockReason = promptFeedback?.optString("blockReason", "")
                        if (!blockReason.isNullOrBlank()) {
                            return@withContext GeminiResult(false, errorMessage = "Gemini 프롬프트 차단 (사유: $blockReason)")
                        }

                        lastError = "응답에서 요약 텍스트를 찾을 수 없습니다."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception calling $baseUrl", e)
                    lastError = "${e.javaClass.simpleName}: ${e.message}"
                }
            }

            GeminiResult(false, errorMessage = lastError)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini summary", e)
            GeminiResult(false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseError(responseStr: String): String {
        return try {
            val json = JSONObject(responseStr)
            val error = json.optJSONObject("error")
            val status = error?.optString("status", "") ?: ""
            val message = error?.optString("message", "") ?: responseStr.take(150)

            when {
                status == "INVALID_ARGUMENT" || message.contains("API key not valid", ignoreCase = true) ->
                    "API 키가 올바르지 않습니다. Google AI Studio(aistudio.google.com)에서 발급받은 키인지 확인해주세요."
                status == "PERMISSION_DENIED" || message.contains("permission", ignoreCase = true) ->
                    "권한 오류(403): API 키에 'Generative Language API' 접근 권한이 없거나 제한된 키입니다."
                status == "RESOURCE_EXHAUSTED" || message.contains("quota", ignoreCase = true) ->
                    "할당량 초과(429): 무료 요청 한도에 도달했습니다. 잠시 후 다시 시도해주세요."
                else -> message
            }
        } catch (_: Exception) {
            responseStr.take(150)
        }
    }
}
