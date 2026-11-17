package com.notipass.droid.engine

import android.util.Log
import com.notipass.droid.data.storage.KakaoLogStorage
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
    val errorMessage: String? = null,
    val usedModel: String? = null
)

data class GeminiTestResult(
    val isSuccess: Boolean,
    val selectedModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val sampleResponse: String? = null,
    val errorMessage: String? = null
)

object GeminiSummarizer {

    private const val TAG = "GeminiSummarizer"

    @Volatile
    private var cachedWorkingModel: String? = null

    private val DEFAULT_CANDIDATE_MODELS = listOf(
        "models/gemini-2.0-flash",
        "models/gemini-1.5-flash",
        "models/gemini-1.5-flash-002",
        "models/gemini-1.5-flash-8b",
        "models/gemini-1.5-pro",
        "models/gemini-2.5-flash"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Query Google Generative Language API ModelService.ListModels to dynamically discover
     * which exact models the user's API key has permission to use for generateContent.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API 키가 비어있습니다."))
        }

        val versions = listOf("v1beta", "v1")
        var lastErr = "모델 목록을 조회할 수 없습니다."

        for (version in versions) {
            val url = "https://generativelanguage.googleapis.com/$version/models?key=$cleanKey"
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", cleanKey)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w(TAG, "ListModels ($version) failed: HTTP ${response.code} - $body")
                        val parsed = parseError(body, response.code)
                        lastErr = parsed
                        if (response.code == 400 || response.code == 403) {
                            // Definitive key or permission error
                            return@withContext Result.failure(Exception(parsed))
                        }
                        return@use
                    }

                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val validModels = mutableListOf<String>()

                    for (i in 0 until modelsArray.length()) {
                        val modelObj = modelsArray.optJSONObject(i) ?: continue
                        val name = modelObj.optString("name", "")
                        val methods = modelObj.optJSONArray("supportedGenerationMethods")
                        var supportsGenerate = false
                        if (methods != null) {
                            for (j in 0 until methods.length()) {
                                if (methods.optString(j) == "generateContent") {
                                    supportsGenerate = true
                                    break
                                }
                            }
                        }
                        if (supportsGenerate && name.isNotBlank()) {
                            validModels.add(name)
                        }
                    }

                    if (validModels.isNotEmpty()) {
                        Log.i(TAG, "Successfully discovered ${validModels.size} models from $version: $validModels")
                        return@withContext Result.success(validModels)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during ListModels on $version", e)
                lastErr = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        Result.failure(Exception(lastErr))
    }

    /**
     * Pick the optimal model based on speed, cost and performance.
     */
    fun selectBestModel(models: List<String>): String {
        val priorityPredicates: List<(String) -> Boolean> = listOf(
            { it.contains("2.0-flash", ignoreCase = true) || it.contains("2.5-flash", ignoreCase = true) },
            { it.contains("1.5-flash", ignoreCase = true) && !it.contains("8b", ignoreCase = true) },
            { it.contains("1.5-flash-8b", ignoreCase = true) },
            { it.contains("flash", ignoreCase = true) },
            { it.contains("1.5-pro", ignoreCase = true) || it.contains("2.0-pro", ignoreCase = true) },
            { it.contains("pro", ignoreCase = true) }
        )

        for (predicate in priorityPredicates) {
            val match = models.firstOrNull { predicate(it) }
            if (match != null) return match
        }

        return models.firstOrNull() ?: "models/gemini-2.0-flash"
    }

    /**
     * Test the API key by querying available models and sending a tiny test prompt.
     */
    suspend fun testApiKey(apiKey: String): GeminiTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiTestResult(false, errorMessage = "API 키를 먼저 입력해주세요.")
        }

        // 1. Discover available models
        val modelDiscoveryResult = fetchAvailableModels(cleanKey)
        val availableModels = modelDiscoveryResult.getOrNull()

        if (modelDiscoveryResult.isFailure || availableModels.isNullOrEmpty()) {
            val err = modelDiscoveryResult.exceptionOrNull()?.message ?: "사용 가능한 Gemini 모델을 찾을 수 없습니다."
            return@withContext GeminiTestResult(false, errorMessage = err)
        }

        val bestModel = selectBestModel(availableModels)
        Log.i(TAG, "Testing API key with best model: $bestModel")

        // 2. Test generation with short prompt
        val testPrompt = "카카오톡 AI 요약 비서 연동 테스트입니다. '연결 성공'이라고 1단어로만 짧게 응답하세요."
        val genResult = executeGenerateContent(cleanKey, bestModel, testPrompt)

        if (genResult.isSuccess && !genResult.summary.isNullOrBlank()) {
            cachedWorkingModel = bestModel
            GeminiTestResult(
                isSuccess = true,
                selectedModel = bestModel.removePrefix("models/"),
                availableModels = availableModels.map { it.removePrefix("models/") },
                sampleResponse = genResult.summary.trim()
            )
        } else {
            GeminiTestResult(
                isSuccess = false,
                selectedModel = bestModel.removePrefix("models/"),
                availableModels = availableModels.map { it.removePrefix("models/") },
                errorMessage = genResult.errorMessage ?: "테스트 텍스트 생성에 실패했습니다."
            )
        }
    }

    suspend fun summarize(
        apiKey: String,
        roomName: String,
        chatLogs: String
    ): GeminiResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "Gemini API 키가 등록되지 않았습니다.")
        }

        // Deduplicate logs before summarizing
        val sanitizedLogs = KakaoLogStorage.deduplicateContent(chatLogs)
        if (sanitizedLogs.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "요약할 대화 내용이 없습니다.")
        }

        val prompt = """
당신은 카카오톡 단톡방 AI 비서입니다.
아래는 '$roomName' 단톡방에서 오간 대화 내용입니다.
불필요한 인사나 잡담(ㅋㅋ, 이모티콘, 단순 맞장구 등)은 모두 제외하고,
주요 공지사항, 결정된 약속(일시/장소), 중요한 질문과 답변, 핵심 전달 사항 위주로
한국어 1~3문장으로 간결하게 핵심만 요약해 주세요.
(답변에 '요약 결과입니다:' 같은 사족을 붙이지 말고 요약 내용만 바로 출력하세요.)

[대화 내용]:
$sanitizedLogs
        """.trimIndent()

        // 1. Try cached working model first if available
        cachedWorkingModel?.let { model ->
            val result = executeGenerateContent(cleanKey, model, prompt)
            if (result.isSuccess) {
                return@withContext result
            }
            Log.w(TAG, "Cached model $model failed, falling back to dynamic model search...")
        }

        // 2. Dynamically discover models supported by this API key
        val discoveryResult = fetchAvailableModels(cleanKey)
        val modelsToTry = if (discoveryResult.isSuccess && !discoveryResult.getOrNull().isNullOrEmpty()) {
            val list = discoveryResult.getOrNull()!!
            val sorted = list.sortedWith(compareBy { name ->
                when {
                    name.contains("2.0-flash", ignoreCase = true) -> 0
                    name.contains("2.5-flash", ignoreCase = true) -> 1
                    name.contains("1.5-flash", ignoreCase = true) -> 2
                    name.contains("flash", ignoreCase = true) -> 3
                    name.contains("pro", ignoreCase = true) -> 4
                    else -> 5
                }
            })
            sorted
        } else {
            // If discovery failed due to auth/permission, return error immediately
            val exc = discoveryResult.exceptionOrNull()
            if (exc != null && (exc.message?.contains("올바르지 않습니다") == true || exc.message?.contains("권한") == true)) {
                return@withContext GeminiResult(false, errorMessage = exc.message)
            }
            DEFAULT_CANDIDATE_MODELS
        }

        var lastError = "요약 처리에 실패했습니다."

        // 3. Try candidates in order
        for (model in modelsToTry) {
            val result = executeGenerateContent(cleanKey, model, prompt)
            if (result.isSuccess) {
                cachedWorkingModel = model
                return@withContext result
            }
            lastError = result.errorMessage ?: lastError
            // If error is 400 (Bad request/key) or 403 (Forbidden), stop retrying
            if (lastError.contains("API 키가 올바르지") || lastError.contains("권한 오류")) {
                return@withContext GeminiResult(false, errorMessage = lastError)
            }
        }

        GeminiResult(false, errorMessage = lastError)
    }

    private fun executeGenerateContent(
        cleanKey: String,
        modelName: String,
        prompt: String
    ): GeminiResult {
        val safeModel = if (modelName.startsWith("models/")) modelName else "models/$modelName"
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

            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 800)
            }
            put("generationConfig", genConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        // Try v1beta first, then v1
        val candidateUrls = listOf(
            "https://generativelanguage.googleapis.com/v1beta/$safeModel:generateContent?key=$cleanKey",
            "https://generativelanguage.googleapis.com/v1/$safeModel:generateContent?key=$cleanKey"
        )

        var lastError = "통신 오류"

        for (url in candidateUrls) {
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", cleanKey)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w(TAG, "generateContent on $url failed (HTTP ${response.code}): $responseBodyStr")
                        lastError = parseError(responseBodyStr, response.code)
                        if (response.code == 404) {
                            // 404 indicates model or api version mismatch, try next URL or model
                            return@use
                        } else {
                            // 400, 403, 429
                            return GeminiResult(false, errorMessage = lastError)
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
                                Log.i(TAG, "Successfully generated content using $safeModel")
                                return GeminiResult(true, summary = summaryText, usedModel = safeModel)
                            }
                        }
                        if (finishReason.isNotBlank() && finishReason != "STOP") {
                            return GeminiResult(false, errorMessage = "Gemini 생성 중단 (사유: $finishReason)")
                        }
                    }

                    val promptFeedback = jsonResponse.optJSONObject("promptFeedback")
                    val blockReason = promptFeedback?.optString("blockReason", "")
                    if (!blockReason.isNullOrBlank()) {
                        return GeminiResult(false, errorMessage = "Gemini 프롬프트 차단 (사유: $blockReason)")
                    }

                    lastError = "응답에서 요약 텍스트를 찾을 수 없습니다."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling $url", e)
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        return GeminiResult(false, errorMessage = lastError)
    }

    private fun parseError(responseStr: String, httpCode: Int = 0): String {
        return try {
            val json = JSONObject(responseStr)
            val error = json.optJSONObject("error")
            val status = error?.optString("status", "") ?: ""
            val message = error?.optString("message", "") ?: responseStr.take(180)

            when {
                httpCode == 400 || status == "INVALID_ARGUMENT" || message.contains("API key not valid", ignoreCase = true) ->
                    "API 키가 올바르지 않습니다 (HTTP 400). Google AI Studio(aistudio.google.com)에서 발급받은 키를 다시 확인해주세요."
                httpCode == 403 || status == "PERMISSION_DENIED" || message.contains("permission", ignoreCase = true) ->
                    "접근 권한 오류 (HTTP 403): API 키에 'Generative Language API' 접근 권한이 없거나 제한된 키입니다."
                httpCode == 429 || status == "RESOURCE_EXHAUSTED" || message.contains("quota", ignoreCase = true) ->
                    "할당량 초과 (HTTP 429): 무료 요청 한도에 도달했습니다. 잠시 후 다시 시도해주세요."
                httpCode == 404 ->
                    "모델을 찾을 수 없음 (HTTP 404): $message"
                else ->
                    "Gemini 오류 (HTTP $httpCode): $message"
            }
        } catch (_: Exception) {
            if (httpCode > 0) "Gemini 호출 실패 (HTTP $httpCode): ${responseStr.take(150)}"
            else responseStr.take(150)
        }
    }
}
