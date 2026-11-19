package com.notipass.droid.engine

import android.util.Log
import com.notipass.droid.data.storage.KakaoLogStorage
import com.notipass.droid.model.SummaryStyle
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
        "models/gemini-1.5-flash",
        "models/gemini-2.0-flash",
        "models/gemini-1.5-flash-latest",
        "models/gemini-1.5-flash-002",
        "models/gemini-1.5-flash-8b",
        "models/gemini-1.5-pro",
        "models/gemini-pro"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private fun isApiKeyInvalid(text: String): Boolean {
        return text.contains("API_KEY_INVALID", ignoreCase = true) ||
               text.contains("API key not valid", ignoreCase = true)
    }

    fun sortModelsByPriority(models: List<String>): List<String> {
        return models.sortedWith(compareBy { name ->
            when {
                name.contains("1.5-flash", ignoreCase = true) && !name.contains("8b", ignoreCase = true) -> 0
                name.contains("2.0-flash", ignoreCase = true) -> 1
                name.contains("2.5-flash", ignoreCase = true) -> 2
                name.contains("flash", ignoreCase = true) -> 3
                name.contains("1.5-pro", ignoreCase = true) || name.contains("2.0-pro", ignoreCase = true) -> 4
                name.contains("pro", ignoreCase = true) -> 5
                else -> 6
            }
        })
    }

    /**
     * Query Google Generative Language API ModelService.ListModels to dynamically discover
     * which exact models the user's API key has permission to use for generateContent.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API 키가 비어있습니다."))
        }

        // Try v1 first for stable GA releases, then v1beta
        val versions = listOf("v1", "v1beta")
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
                        if (isApiKeyInvalid(body)) {
                            // Definitive key error across all endpoints
                            return@withContext Result.failure(Exception(parsed))
                        }
                        // Non-fatal for this version, try next version
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
        return sortModelsByPriority(models).firstOrNull() ?: "models/gemini-1.5-flash"
    }

    /**
     * Test the API key by querying available models and sending a tiny test prompt.
     * Even if dynamic ListModels discovery fails, directly tries candidate models (v1 / v1beta)
     * so that restricted or standard API keys work seamlessly.
     */
    suspend fun testApiKey(apiKey: String): GeminiTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiTestResult(false, errorMessage = "API 키를 먼저 입력해주세요.")
        }

        // 1. Try to discover available models dynamically (non-blocking fallback)
        val modelDiscoveryResult = fetchAvailableModels(cleanKey)
        val discoveredModels = modelDiscoveryResult.getOrNull()

        val modelsToTry = if (!discoveredModels.isNullOrEmpty()) {
            sortModelsByPriority(discoveredModels)
        } else {
            DEFAULT_CANDIDATE_MODELS
        }

        val testPrompt = "카카오톡 AI 요약 비서 연동 테스트입니다. '연결 성공'이라고 1단어로만 짧게 응답하세요."
        var lastError: String? = null

        // 2. Direct generation test across candidate models until one succeeds
        for (model in modelsToTry) {
            val genResult = executeGenerateContent(cleanKey, model, testPrompt, maxTokens = 50)
            if (genResult.isSuccess && !genResult.summary.isNullOrBlank()) {
                cachedWorkingModel = model
                val displayModel = model.removePrefix("models/")
                val displayList = if (!discoveredModels.isNullOrEmpty()) {
                    discoveredModels.map { it.removePrefix("models/") }
                } else {
                    listOf(displayModel)
                }
                return@withContext GeminiTestResult(
                    isSuccess = true,
                    selectedModel = displayModel,
                    availableModels = displayList,
                    sampleResponse = genResult.summary.trim()
                )
            }
            lastError = genResult.errorMessage ?: lastError
            if (isApiKeyInvalid(lastError ?: "")) {
                break
            }
        }

        GeminiTestResult(
            isSuccess = false,
            errorMessage = lastError ?: modelDiscoveryResult.exceptionOrNull()?.message ?: "테스트 텍스트 생성에 실패했습니다."
        )
    }

    /**
     * Dynamically construct prompt and token limit based on conversation volume and chosen style.
     */
    fun buildPrompt(
        roomName: String,
        sanitizedLogs: String,
        effectiveCount: Int,
        style: SummaryStyle
    ): Pair<String, Int> {
        return when (style) {
            SummaryStyle.REPORT -> {
                val prompt = """
당신은 대한민국 최고 수준의 비즈니스 커뮤니케이션 & 대화 분석 전문 AI 비서입니다.
아래는 '$roomName' 카카오톡 단톡방에서 수집된 실제 대화 내용(총 ${effectiveCount}개 메시지)입니다.

사용자의 요구사항:
"수집된 대화량이 많으므로, 단편적인 1~2줄 요약이 아니라, 대화에 참여하지 않은 사람도 모든 맥락과 핵심을 완벽히 파악할 수 있도록 **체계적인 고품질 [단톡방 대화 분석 종합 보고서]**를 작성해 주세요."

[작성 및 분석 원칙]:
1. **잡담/노이즈 완벽 제거**: 단순 인사("안녕하세요", "수고하세요"), 감정 표현/이모티콘, 단순 맞장구("네", "ㅋㅋ", "ㅎㅎ", "ㅇㅇ", "감사합니다")는 과감하게 제외하세요.
2. **풍부한 디테일과 맥락 보존**: 중요한 안건, 의견 교환의 배경, 논의된 대안, 구체적인 수치/금액/장소/날짜, 누가 어떤 의견을 냈는지를 축약하거나 생략하지 말고 **자세하고 충실하게** 서술하세요.
3. **가독성 높은 구조화**: 마크다운 제목, 불릿 포인트, 볼드체 등을 활용하여 한눈에 들어오게 정리하세요.

[보고서 출력 양식]:
━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 [단톡방 대화 분석 종합 보고서: $roomName]
• 분석 대화량: 총 ${effectiveCount}개 메시지
━━━━━━━━━━━━━━━━━━━━━━━━━━━

📌 1. 핵심 총괄 브리핑 (Executive Summary)
- 전체 대화의 핵심 주제와 가장 중요한 결과를 2~3개 문장으로 압축하여 종합 브리핑합니다.

💡 2. 주요 안건별 상세 논의 및 진행 경과
- 대화에서 다룬 핵심 주제들을 [주제명] 단위로 분류하여, 각 안건의 발생 배경, 주요 발언자들의 의견, 주고받은 논의 과정을 구체적으로 기술합니다.
  (예: [프로젝트 일정 조율], [결제 모듈 버그 수정], [주말 모임 장소 선정] 등)

🎯 3. 최종 결정 및 합의 사항
- 대화를 통해 최종 합의되거나 확정된 결정 사항들을 명확히 정리합니다.

⏰ 4. 일정 · 약속 및 액션 아이템 (Who, When, Where, What)
- 일시, 장소, 담당자, 수행해야 할 과제 등을 명확한 체크리스트 형태로 정리합니다.
  (해당 사항이 없는 경우 '특이 일정 없음' 표기)

❓ 5. 미해결 과제 및 추가 확인 필요 사항
- 대화 중 거론되었으나 결론이 나지 않은 질문, 추가 확인이나 의사결정이 필요한 사항들을 정리합니다.
  (해당 사항이 없는 경우 생략 가능)

(답변에 '보고서 내용입니다' 같은 불필요한 사족을 달지 말고 위의 보고서 본문만 바로 출력하세요.)

[대화 내용]:
$sanitizedLogs
                """.trimIndent()
                Pair(prompt, 3500)
            }

            SummaryStyle.AUTO -> {
                val prompt = """
당신은 카카오톡 단톡방 전문 AI 비서입니다.
아래는 '$roomName' 단톡방에서 오간 대화 내용(총 ${effectiveCount}개 메시지)입니다.
불필요한 잡담(ㅋㅋ, ㅎㅎ, 단순 맞장구 등)은 제외하되, 대화의 전반적인 맥락과 세부 정보가 충실히 전달될 수 있도록 아래의 양식에 맞추어 체계적으로 요약해 주세요:

📋 [$roomName 대화 핵심 요약] (총 ${effectiveCount}개 대화)

📌 1. 핵심 브리핑
- 대화의 전체 요지와 결론을 2~3문장으로 요약합니다.

💬 2. 주요 논의 및 전달 사항
- 나눈 대화의 주요 내용을 주제별로 분류하여 구체적인 내용과 함께 정리합니다.

🎯 3. 결정 사항 및 약속/일정
- 정해진 약속(일시/장소)이나 할 일, 확정된 사항을 정리합니다. (없을 시 '특이 일정 없음' 표기)

(사족 없이 본문만 바로 출력하세요.)

[대화 내용]:
$sanitizedLogs
                """.trimIndent()
                Pair(prompt, 1800)
            }

            SummaryStyle.BRIEF -> {
                val prompt = """
당신은 카카오톡 단톡방 AI 비서입니다.
아래는 '$roomName' 단톡방에서 오간 짧은 대화 내용(총 ${effectiveCount}개)입니다.
불필요한 인사나 리액션은 제외하고, 핵심 용건과 주요 내용을 2~4개 불릿 포인트 또는 간결한 문장으로 명확하게 요약해 주세요.
(사족 없이 요약 내용만 바로 출력하세요.)

[대화 내용]:
$sanitizedLogs
                """.trimIndent()
                Pair(prompt, 900)
            }
        }
    }

    suspend fun summarize(
        apiKey: String,
        roomName: String,
        chatLogs: String,
        messageCount: Int = 0,
        style: SummaryStyle = SummaryStyle.AUTO
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

        val effectiveCount = if (messageCount > 0) messageCount else sanitizedLogs.lines().count { it.isNotBlank() }

        // Determine effective style
        val effectiveStyle = when (style) {
            SummaryStyle.REPORT -> SummaryStyle.REPORT
            SummaryStyle.BRIEF -> SummaryStyle.BRIEF
            SummaryStyle.AUTO -> {
                when {
                    effectiveCount >= 25 || sanitizedLogs.length >= 800 -> SummaryStyle.REPORT
                    effectiveCount >= 10 || sanitizedLogs.length >= 250 -> SummaryStyle.AUTO
                    else -> SummaryStyle.BRIEF
                }
            }
        }

        val (prompt, maxTokens) = buildPrompt(roomName, sanitizedLogs, effectiveCount, effectiveStyle)

        // 1. Try cached working model first if available
        cachedWorkingModel?.let { model ->
            val result = executeGenerateContent(cleanKey, model, prompt, maxTokens)
            if (result.isSuccess) {
                return@withContext result
            }
            Log.w(TAG, "Cached model $model failed, falling back to candidate model search...")
        }

        // 2. Dynamically discover models supported by this API key, fallback to DEFAULT_CANDIDATE_MODELS on any error
        val discoveryResult = fetchAvailableModels(cleanKey)
        val discovered = discoveryResult.getOrNull()
        val modelsToTry = if (!discovered.isNullOrEmpty()) {
            sortModelsByPriority(discovered)
        } else {
            DEFAULT_CANDIDATE_MODELS
        }

        var lastError = "요약 처리에 실패했습니다."

        // 3. Try candidates in order
        for (model in modelsToTry) {
            val result = executeGenerateContent(cleanKey, model, prompt, maxTokens)
            if (result.isSuccess) {
                cachedWorkingModel = model
                return@withContext result
            }
            lastError = result.errorMessage ?: lastError
            if (isApiKeyInvalid(lastError)) {
                return@withContext GeminiResult(false, errorMessage = "API 키가 올바르지 않습니다. Google AI Studio(aistudio.google.com)에서 발급받은 키를 다시 확인해주세요.")
            }
        }

        GeminiResult(false, errorMessage = lastError)
    }

    suspend fun summarizeAppNotifications(
        apiKey: String,
        appName: String,
        notificationsText: String,
        notificationCount: Int = 0,
        style: SummaryStyle = SummaryStyle.AUTO
    ): GeminiResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "Gemini API 키가 등록되지 않았습니다.")
        }

        if (notificationsText.isBlank()) {
            return@withContext GeminiResult(false, errorMessage = "요약할 알림 내역이 없습니다.")
        }

        val effectiveCount = if (notificationCount > 0) notificationCount else notificationsText.lines().count { it.isNotBlank() }

        val (prompt, maxTokens) = when (style) {
            SummaryStyle.REPORT -> {
                val p = """
당신은 스마트폰 앱 알림 전문 AI 분석 비서입니다.
아래는 '$appName' 앱에서 수신된 알림 내역(총 ${effectiveCount}건)입니다.
중복되거나 일상적인 잡음은 거르고, 핵심 공지, 중요 정보, 거래/결제 내역, 일정, 주요 메시지 등을 체계적인 보고서 형식으로 작성해 주세요:

📋 [$appName 알림 상세 보고서] (총 ${effectiveCount}건 정리)

📌 1. 전체 핵심 요약
- 전체 알림의 가장 중요한 핵심 내용을 2~3문장으로 명확히 요약합니다.

🔔 2. 주요 알림 항목별 정리
- 알림 성격별(예: 결제/주문, 새 메시지/채팅, 공지사항, 키워드 알림 등)로 분류하여 중요한 세부 내용과 시간을 정리합니다.

🎯 3. 즉시 확인 또는 대응이 필요한 사항
- 사용자가 놓치지 말아야 할 긴급 확인 항목, 결제/배송 변동, 예약/마감 일정 등을 명시합니다. (없을 시 '특이 사항 없음' 표기)

(사족 없이 본문만 바로 출력하세요.)

[알림 내역]:
$notificationsText
                """.trimIndent()
                Pair(p, 3000)
            }
            SummaryStyle.BRIEF -> {
                val p = """
당신은 스마트폰 앱 알림 전문 AI 비서입니다.
아래는 '$appName' 앱에서 수신된 알림 내역(총 ${effectiveCount}건)입니다.
가장 중요한 핵심 정보와 확인이 필요한 사항을 2~4개 불릿 포인트로 간결하고 명확하게 요약해 주세요.
(사족 없이 요약 내용만 바로 출력하세요.)

[알림 내역]:
$notificationsText
                """.trimIndent()
                Pair(p, 900)
            }
            SummaryStyle.AUTO -> {
                val p = """
당신은 스마트폰 앱 알림 전문 AI 비서입니다.
아래는 '$appName' 앱에서 수신된 알림 내역(총 ${effectiveCount}건)입니다.
중복된 내용은 합치고, 주요 내용과 핵심 정보를 아래 양식에 맞추어 깔끔하게 요약해 주세요:

📋 [$appName 알림 브리핑] (총 ${effectiveCount}건 정리)

📌 1. 핵심 요약
- 전체 알림 중 가장 중요한 내용을 1~2문장으로 요약합니다.

🔔 2. 주요 알림 내용
- 중요한 알림 내용(결제, 배송, 메시지, 공지 등)을 2~4개 항목으로 정리합니다.

🎯 3. 확인 필요 사항
- 사용자가 확인해야 할 사항이나 일정. (없을 시 '확인 필요 사항 없음' 표기)

(사족 없이 본문만 바로 출력하세요.)

[알림 내역]:
$notificationsText
                """.trimIndent()
                Pair(p, 1800)
            }
        }

        // 1. Try cached model first
        cachedWorkingModel?.let { model ->
            val result = executeGenerateContent(cleanKey, model, prompt, maxTokens)
            if (result.isSuccess) return@withContext result
            Log.w(TAG, "Cached model $model failed, falling back to candidate model search...")
        }

        // 2. Dynamically discover models supported by this API key, fallback to DEFAULT_CANDIDATE_MODELS on any error
        val discoveryResult = fetchAvailableModels(cleanKey)
        val discovered = discoveryResult.getOrNull()
        val modelsToTry = if (!discovered.isNullOrEmpty()) {
            sortModelsByPriority(discovered)
        } else {
            DEFAULT_CANDIDATE_MODELS
        }

        var lastError = "앱 알림 요약 처리에 실패했습니다."
        for (model in modelsToTry) {
            val result = executeGenerateContent(cleanKey, model, prompt, maxTokens)
            if (result.isSuccess) {
                cachedWorkingModel = model
                return@withContext result
            }
            lastError = result.errorMessage ?: lastError
            if (isApiKeyInvalid(lastError)) {
                return@withContext GeminiResult(false, errorMessage = "API 키가 올바르지 않습니다. Google AI Studio(aistudio.google.com)에서 발급받은 키를 다시 확인해주세요.")
            }
        }

        GeminiResult(false, errorMessage = lastError)
    }

    private fun executeGenerateContent(
        cleanKey: String,
        modelName: String,
        prompt: String,
        maxTokens: Int = 2000
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

            // Safety settings to prevent false-positive content blocks on Korean chat messages & notifications
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

            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", maxTokens)
            }
            put("generationConfig", genConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        // For gemini-1.5 models, v1 is the stable GA endpoint (which fixed v1beta 404).
        // For gemini-2.0 models, v1beta is the primary endpoint.
        val is2xModel = safeModel.contains("2.0") || safeModel.contains("2.5")
        val candidateUrls = if (is2xModel) {
            listOf(
                "https://generativelanguage.googleapis.com/v1beta/$safeModel:generateContent?key=$cleanKey",
                "https://generativelanguage.googleapis.com/v1/$safeModel:generateContent?key=$cleanKey"
            )
        } else {
            listOf(
                "https://generativelanguage.googleapis.com/v1/$safeModel:generateContent?key=$cleanKey",
                "https://generativelanguage.googleapis.com/v1beta/$safeModel:generateContent?key=$cleanKey"
            )
        }

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
                        if (isApiKeyInvalid(responseBodyStr)) {
                            return GeminiResult(false, errorMessage = "API 키가 올바르지 않습니다. Google AI Studio(aistudio.google.com)에서 발급받은 키를 다시 확인해주세요.")
                        }
                        // Non-fatal for this URL, continue to next candidate URL
                        return@use
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
                                Log.i(TAG, "Successfully generated content using $safeModel via $url ($maxTokens tokens limit)")
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
            val details = error?.optJSONArray("details")
            var detailReason = ""
            if (details != null) {
                for (i in 0 until details.length()) {
                    val d = details.optJSONObject(i)
                    val r = d?.optString("reason", "")
                    if (!r.isNullOrBlank()) {
                        detailReason = r
                        break
                    }
                }
            }

            when {
                detailReason == "API_KEY_INVALID" || message.contains("API key not valid", ignoreCase = true) ->
                    "API 키가 올바르지 않습니다. Google AI Studio(aistudio.google.com)에서 발급받은 키를 다시 확인해주세요."
                httpCode == 403 || status == "PERMISSION_DENIED" || detailReason == "ACCESS_DENIED" || message.contains("permission", ignoreCase = true) ->
                    "접근 권한 오류 (HTTP 403): API 키에 'Generative Language API' 접근 권한이 없거나 제한된 키입니다. ($message)"
                httpCode == 429 || status == "RESOURCE_EXHAUSTED" || message.contains("quota", ignoreCase = true) ->
                    "할당량 초과 (HTTP 429): 무료 요청 한도에 도달했습니다. 잠시 후 다시 시도해주세요."
                httpCode == 404 ->
                    "모델 또는 엔드포인트를 찾을 수 없음 (HTTP 404): $message"
                httpCode == 400 ->
                    "요청 형식 오류 (HTTP 400): $message"
                else ->
                    "Gemini 오류 (HTTP $httpCode): $message"
            }
        } catch (_: Exception) {
            if (httpCode > 0) "Gemini 호출 실패 (HTTP $httpCode): ${responseStr.take(150)}"
            else responseStr.take(150)
        }
    }
}
