package com.notipass.droid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.notipass.droid.engine.SmsDispatcher
import com.notipass.droid.engine.TemplateEngine
import com.notipass.droid.engine.WebhookDispatcher
import com.notipass.droid.model.ActionType
import com.notipass.droid.model.MatchMode
import com.notipass.droid.model.Rule
import com.notipass.droid.model.TriggerType
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
    var isEnabled by remember { mutableStateOf(true) }

    // Trigger States
    var triggerType by remember { mutableStateOf(TriggerType.NOTIFICATION) }
    var targetPackage by remember { mutableStateOf<String?>(null) }
    var targetAppName by remember { mutableStateOf<String?>(null) }
    var titleMode by remember { mutableStateOf(MatchMode.IGNORE) }
    var titleKeyword by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(MatchMode.IGNORE) }
    var contentKeyword by remember { mutableStateOf("") }

    // Action States
    var actionType by remember { mutableStateOf(ActionType.WEBHOOK) }

    // Webhook Settings
    var webhookUrl by remember { mutableStateOf("") }
    var webhookMethod by remember { mutableStateOf("POST") }
    var headersJson by remember { mutableStateOf("{}") }
    var bodyTemplate by remember {
        mutableStateOf("""{"title":"{title}","content":"{content}","app":"{app}","time":"{time}"}""")
    }

    // SMS Settings
    var smsTargetNumber by remember { mutableStateOf("") }
    var smsTemplate by remember {
        mutableStateOf("[NotiPass] {caller_display}님으로부터 전화가 수신되었습니다. ({date})")
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var isTestingAction by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // Load existing rule if ruleId > 0
    LaunchedEffect(ruleId) {
        if (ruleId > 0) {
            val rule = db.ruleDao().getRuleById(ruleId)
            if (rule != null) {
                name = rule.name
                isEnabled = rule.isEnabled
                triggerType = rule.triggerType
                targetPackage = rule.targetPackage
                targetAppName = rule.targetAppName
                titleMode = rule.titleMatchMode
                titleKeyword = rule.titleKeyword
                contentMode = rule.contentMatchMode
                contentKeyword = rule.contentKeyword
                actionType = rule.actionType
                webhookUrl = rule.webhookUrl
                webhookMethod = rule.webhookMethod
                headersJson = rule.webhookHeadersJson
                bodyTemplate = rule.webhookBodyTemplate
                smsTargetNumber = rule.smsTargetNumber
                smsTemplate = rule.smsTemplate
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId > 0) "규칙 수정" else "새 규칙 추가") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isBlank()) {
                                Toast.makeText(context, "규칙 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            if (actionType != ActionType.SMS && webhookUrl.isBlank()) {
                                Toast.makeText(context, "웹훅 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            if (actionType != ActionType.WEBHOOK && smsTargetNumber.isBlank()) {
                                Toast.makeText(context, "문자 수신 번호를 입력해주세요 (회신은 {caller}).", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            coroutineScope.launch {
                                val rule = Rule(
                                    id = if (ruleId > 0) ruleId else 0,
                                    name = name.trim(),
                                    isEnabled = isEnabled,
                                    triggerType = triggerType,
                                    targetPackage = targetPackage,
                                    targetAppName = targetAppName,
                                    titleMatchMode = titleMode,
                                    titleKeyword = titleKeyword.trim(),
                                    contentMatchMode = contentMode,
                                    contentKeyword = contentKeyword.trim(),
                                    actionType = actionType,
                                    webhookUrl = webhookUrl.trim(),
                                    webhookMethod = webhookMethod,
                                    webhookHeadersJson = headersJson.trim(),
                                    webhookBodyTemplate = bodyTemplate,
                                    smsTargetNumber = smsTargetNumber.trim(),
                                    smsTemplate = smsTemplate
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
            // 1. Rule Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("규칙 이름") },
                placeholder = { Text("예: 전화 수신 시 등록 번호로 문자 전송") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. Trigger Type Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "1. 언제 실행할까요? (발동 조건)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = triggerType == TriggerType.NOTIFICATION,
                            onClick = { triggerType = TriggerType.NOTIFICATION },
                            label = { Text("🔔 앱 알림 수신") }
                        )
                        FilterChip(
                            selected = triggerType == TriggerType.INCOMING_CALL,
                            onClick = { triggerType = TriggerType.INCOMING_CALL },
                            label = { Text("📞 전화 올 때 (벨 울림)") }
                        )
                        FilterChip(
                            selected = triggerType == TriggerType.MISSED_CALL,
                            onClick = { triggerType = TriggerType.MISSED_CALL },
                            label = { Text("📵 부재중 전화") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (triggerType == TriggerType.NOTIFICATION) {
                        // Target App Selector
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAppPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "대상 앱", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = if (!targetAppName.isNullOrBlank()) targetAppName!! else (targetPackage ?: "모든 앱 (필터 없음)"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(onClick = { showAppPicker = true }) {
                                    Text("앱 선택")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = titleKeyword,
                            onValueChange = { titleKeyword = it },
                            label = { Text("알림 제목 포함 키워드 (선택)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contentKeyword,
                            onValueChange = { contentKeyword = it },
                            label = { Text("알림 본문 포함 키워드 (선택)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Phone Call trigger settings
                        OutlinedTextField(
                            value = titleKeyword,
                            onValueChange = { titleKeyword = it },
                            label = { Text("발신자 번호 필터 (비워두면 모든 전화)") },
                            placeholder = { Text("예: 010 (특정 국번만) 또는 비워두기") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Action Type Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "2. 어떤 작업을 수행할까요? (동작)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = actionType == ActionType.SMS,
                            onClick = { actionType = ActionType.SMS },
                            label = { Text("💬 문자(SMS) 발송") }
                        )
                        FilterChip(
                            selected = actionType == ActionType.WEBHOOK,
                            onClick = { actionType = ActionType.WEBHOOK },
                            label = { Text("🌐 웹훅 전송") }
                        )
                        FilterChip(
                            selected = actionType == ActionType.BOTH,
                            onClick = { actionType = ActionType.BOTH },
                            label = { Text("⚡ 둘 다") }
                        )
                    }
                }
            }

            // 4. SMS Configuration Section
            if (actionType == ActionType.SMS || actionType == ActionType.BOTH) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "💬 문자(SMS) 자동 발송 설정", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = smsTargetNumber,
                            onValueChange = { smsTargetNumber = it },
                            label = { Text("문자 받을 대상 번호") },
                            placeholder = { Text("예: 01012345678 (또는 {caller} 입력 시 발신자에게 회신)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { smsTargetNumber = "{caller}" },
                                label = { Text("전화 건 사람({caller})에게 회신") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "메시지 템플릿 치환 토큰 (탭하여 삽입):", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "{caller_display}" to "이름+번호",
                                "{name}" to "연락처 이름",
                                "{caller}" to "발신 번호",
                                "{target}" to "설정된 번호",
                                "{date}" to "일시",
                                "{time}" to "시각"
                            ).forEach { (token, desc) ->
                                AssistChip(
                                    onClick = { smsTemplate += token },
                                    label = { Text("$token ($desc)") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = smsTemplate,
                            onValueChange = { smsTemplate = it },
                            label = { Text("전송할 문자 내용") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (smsTargetNumber.isBlank()) {
                                    Toast.makeText(context, "수신 번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isTestingAction = true
                                testResult = null
                                coroutineScope.launch {
                                    val testVars = mapOf(
                                        "caller" to "010-1234-5678",
                                        "number" to "010-1234-5678",
                                        "phone" to "010-1234-5678",
                                        "name" to "홍길동",
                                        "contact" to "홍길동",
                                        "caller_display" to "홍길동(010-1234-5678)",
                                        "target" to smsTargetNumber,
                                        "content" to "테스트 수신 내용"
                                    )
                                    val target = if (smsTargetNumber.contains("{caller}")) "01012345678" else smsTargetNumber
                                    val rendered = TemplateEngine.renderText(smsTemplate, testVars, isJson = false)
                                    val res = SmsDispatcher.send(context, target, rendered)
                                    isTestingAction = false
                                    testResult = if (res.isSuccess) {
                                        "SMS 발송 성공! ($target -> $rendered)"
                                    } else {
                                        "SMS 발송 실패: ${res.errorMessage}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SMS 발송 테스트")
                        }
                    }
                }
            }

            // 5. Webhook Configuration Section
            if (actionType == ActionType.WEBHOOK || actionType == ActionType.BOTH) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "🌐 웹훅 (Webhook) 설정", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

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

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = bodyTemplate,
                            onValueChange = { bodyTemplate = it },
                            label = { Text("HTTP Body (JSON Template)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                }
            }

            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.startsWith("SMS 발송 성공")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
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
