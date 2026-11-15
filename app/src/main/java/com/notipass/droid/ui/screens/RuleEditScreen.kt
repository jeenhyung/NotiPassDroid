package com.notipass.droid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.engine.NotificationPayload
import com.notipass.droid.engine.TemplateEngine
import com.notipass.droid.engine.WebhookDispatcher
import com.notipass.droid.model.MatchMode
import com.notipass.droid.model.Rule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    db: AppDatabase,
    ruleId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var targetPackage by remember { mutableStateOf<String?>(null) }
    var targetAppName by remember { mutableStateOf<String?>(null) }
    var isEnabled by remember { mutableStateOf(true) }

    var titleMode by remember { mutableStateOf(MatchMode.IGNORE) }
    var titleKeyword by remember { mutableStateOf("") }

    var contentMode by remember { mutableStateOf(MatchMode.IGNORE) }
    var contentKeyword by remember { mutableStateOf("") }

    var webhookUrl by remember { mutableStateOf("") }
    var webhookMethod by remember { mutableStateOf("POST") }
    var headersJson by remember { mutableStateOf("{}") }
    var bodyTemplate by remember {
        mutableStateOf("""{"title":"{title}","content":"{content}","app":"{app}","time":"{time}"}""")
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var isTestingWebhook by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // Load existing rule if ruleId > 0
    LaunchedEffect(ruleId) {
        if (ruleId > 0) {
            val rule = db.ruleDao().getRuleById(ruleId)
            if (rule != null) {
                name = rule.name
                targetPackage = rule.targetPackage
                targetAppName = rule.targetAppName
                isEnabled = rule.isEnabled
                titleMode = rule.titleMatchMode
                titleKeyword = rule.titleKeyword
                contentMode = rule.contentMatchMode
                contentKeyword = rule.contentKeyword
                webhookUrl = rule.webhookUrl
                webhookMethod = rule.webhookMethod
                headersJson = rule.webhookHeadersJson
                bodyTemplate = rule.webhookBodyTemplate
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId > 0) "규칙 수정" else "새 규칙 추가") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isBlank()) {
                                Toast.makeText(context, "규칙 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            if (webhookUrl.isBlank()) {
                                Toast.makeText(context, "웹훅 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            coroutineScope.launch {
                                val rule = Rule(
                                    id = if (ruleId > 0) ruleId else 0,
                                    name = name.trim(),
                                    isEnabled = isEnabled,
                                    targetPackage = targetPackage,
                                    targetAppName = targetAppName,
                                    titleMatchMode = titleMode,
                                    titleKeyword = titleKeyword.trim(),
                                    contentMatchMode = contentMode,
                                    contentKeyword = contentKeyword.trim(),
                                    webhookUrl = webhookUrl.trim(),
                                    webhookMethod = webhookMethod,
                                    webhookHeadersJson = headersJson.trim(),
                                    webhookBodyTemplate = bodyTemplate
                                )
                                if (ruleId > 0) {
                                    db.ruleDao().updateRule(rule)
                                } else {
                                    db.ruleDao().insertRule(rule)
                                }
                                Toast.makeText(context, "규칙이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Basic Info
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("규칙 이름") },
                placeholder = { Text("예: 카카오톡 결제 알림 전달") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Target App Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppPicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "대상 앱 필터", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!targetAppName.isNullOrBlank()) targetAppName!! else (targetPackage ?: "모든 앱 (필터 없음)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showAppPicker = true }) {
                        Text("변경")
                    }
                }
            }

            // 2. Title Filter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "알림 제목 필터", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatchMode.values().forEach { mode ->
                            FilterChip(
                                selected = titleMode == mode,
                                onClick = { titleMode = mode },
                                label = {
                                    Text(
                                        when (mode) {
                                            MatchMode.IGNORE -> "무시 (전부)"
                                            MatchMode.CONTAINS -> "포함"
                                            MatchMode.EXACT -> "일치"
                                            MatchMode.REGEX -> "정규식"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (titleMode != MatchMode.IGNORE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = titleKeyword,
                            onValueChange = { titleKeyword = it },
                            label = { Text(if (titleMode == MatchMode.REGEX) "정규표현식" else "검색 키워드") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Content Filter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "알림 내용(본문) 필터", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatchMode.values().forEach { mode ->
                            FilterChip(
                                selected = contentMode == mode,
                                onClick = { contentMode = mode },
                                label = {
                                    Text(
                                        when (mode) {
                                            MatchMode.IGNORE -> "무시 (전부)"
                                            MatchMode.CONTAINS -> "포함"
                                            MatchMode.EXACT -> "일치"
                                            MatchMode.REGEX -> "정규식"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (contentMode != MatchMode.IGNORE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contentKeyword,
                            onValueChange = { contentKeyword = it },
                            label = { Text(if (contentMode == MatchMode.REGEX) "정규표현식" else "검색 키워드") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Webhook Action Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "웹훅 (Webhook) 전송 설정", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Method Chips (POST / GET)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("POST", "GET").forEach { method ->
                            FilterChip(
                                selected = webhookMethod == method,
                                onClick = { webhookMethod = method },
                                label = { Text(method) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Webhook URL") },
                        placeholder = { Text("https://discord.com/api/webhooks/... or https://your-server.com") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "동적 치환 토큰 (클릭 시 템플릿에 삽입):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("{title}", "{content}", "{app}", "{package}", "{time}", "{date}").forEach { token ->
                            AssistChip(
                                onClick = {
                                    bodyTemplate += token
                                },
                                label = { Text(token) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bodyTemplate,
                        onValueChange = { bodyTemplate = it },
                        label = { Text("HTTP Body (JSON Template)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (webhookUrl.isBlank()) {
                                Toast.makeText(context, "URL을 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTestingWebhook = true
                            testResult = null
                            coroutineScope.launch {
                                val mockPayload = NotificationPayload(
                                    packageName = targetPackage ?: "com.kakao.talk",
                                    appName = targetAppName ?: "카카오톡",
                                    title = "홍길동",
                                    content = "안녕하세요! NotiPass 테스트 메시지입니다."
                                )
                                val renderedBody = TemplateEngine.render(bodyTemplate, mockPayload)
                                val renderedUrl = TemplateEngine.render(webhookUrl, mockPayload)
                                val result = WebhookDispatcher.send(
                                    url = renderedUrl,
                                    method = webhookMethod,
                                    headersJson = headersJson,
                                    bodyContent = renderedBody
                                )
                                isTestingWebhook = false
                                testResult = if (result.isSuccess) {
                                    "전송 성공! (HTTP ${result.statusCode})\n${result.responseBody ?: ""}"
                                } else {
                                    "전송 실패: ${result.errorMessage}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingWebhook) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("웹훅 테스트 전송")
                        }
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testResult!!.startsWith("전송 성공")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismissRequest = { showAppPicker = false },
            onAppSelected = { app ->
                if (app != null) {
                    targetPackage = app.packageName
                    targetAppName = app.appName
                } else {
                    targetPackage = null
                    targetAppName = null
                }
                showAppPicker = false
            }
        )
    }
}
