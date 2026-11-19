package com.notipass.droid.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.data.preference.AppPreferences
import com.notipass.droid.engine.AppSummaryEngine
import com.notipass.droid.engine.AppSummaryScheduler
import com.notipass.droid.engine.GeminiSummarizer
import com.notipass.droid.engine.GeminiTestResult
import com.notipass.droid.model.AppCollectorConfig
import com.notipass.droid.model.AppSummaryRecord
import com.notipass.droid.model.CollectedAppNotification
import com.notipass.droid.model.SummaryDeliveryType
import com.notipass.droid.model.SummaryStyle
import com.notipass.droid.service.NotificationPassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppCollectorTabContent(
    db: AppDatabase,
    initialTargetAppPackage: String? = null,
    showAddAppTrigger: Boolean = false,
    onResetAddAppTrigger: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val configs by db.appCollectorDao().getAllConfigs().collectAsState(initial = emptyList())

    // Update NotificationPassService cache in sync with enabled configs
    LaunchedEffect(configs) {
        val packages = configs.filter { it.isEnabled }.map { it.packageName }.toSet()
        val dismiss = configs.filter { it.isEnabled && it.dismissNotification }.map { it.packageName }.toSet()
        NotificationPassService.updateCollectorCache(packages, dismiss)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<AppCollectorConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    var showNotificationsForApp by remember { mutableStateOf<AppCollectorConfig?>(null) }
    var showSummaryHistoryForApp by remember { mutableStateOf<AppCollectorConfig?>(null) }
    var isRunningSummaryPackage by remember { mutableStateOf<String?>(null) }
    var summaryResultDialog by remember { mutableStateOf<String?>(null) }
    var geminiApiKey by remember { mutableStateOf(AppPreferences.getGeminiApiKey(context)) }
    var isTestingGemini by remember { mutableStateOf(false) }
    var geminiTestResult by remember { mutableStateOf<GeminiTestResult?>(null) }

    // External trigger to open app picker (e.g. from FAB or TopBar action)
    LaunchedEffect(showAddAppTrigger) {
        if (showAddAppTrigger) {
            showAppPicker = true
            onResetAddAppTrigger()
        }
    }

    // Auto open history if navigated from a local notification
    LaunchedEffect(initialTargetAppPackage, configs) {
        if (!initialTargetAppPackage.isNullOrBlank()) {
            val matched = configs.firstOrNull { it.packageName == initialTargetAppPackage }
            if (matched != null) {
                showSummaryHistoryForApp = matched
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "앱 알림 자동 수집 & 스마트 AI 브리핑",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "원하는 앱(당근, 토스, 밴드, 배민, SNS 등)을 등록하면 도착하는 알림을 실시간 타임라인으로 보관하고, 알림이 누적되면 Gemini AI가 중요한 거래/공지/일정만 깔끔하게 요약해 드립니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 1.5 Gemini API Key & Connection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI API 설정", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        if (geminiApiKey.isNotBlank()) {
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "등록됨",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = {
                            geminiApiKey = it
                            AppPreferences.setGeminiApiKey(context, it)
                        },
                        label = { Text("Google Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (geminiApiKey.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (geminiApiKey.isBlank()) {
                                    Toast.makeText(context, "API 키를 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
                                    return@FilledTonalButton
                                }
                                isTestingGemini = true
                                coroutineScope.launch {
                                    val res = GeminiSummarizer.testApiKey(geminiApiKey)
                                    isTestingGemini = false
                                    geminiTestResult = res
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingGemini
                        ) {
                            if (isTestingGemini) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("연결 확인 중...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("연결 & 모델 테스트", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("무료 키 발급", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Apps Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "수집 대상 앱 (${configs.size}개)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAppPicker = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("앱 추가")
                }
            }
        }

        // 3. Configs List
        if (configs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("등록된 수집 대상 앱이 없습니다.", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "우측 상단 '+ 앱 추가'를 눌러 알림을 모아보고 요약받을 앱을 선택하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(configs, key = { it.id }) { config ->
                AppCollectorConfigCard(
                    config = config,
                    db = db,
                    isRunning = isRunningSummaryPackage == config.packageName,
                    onToggleEnabled = { enabled ->
                        coroutineScope.launch {
                            db.appCollectorDao().setConfigEnabled(config.id, enabled)
                            AppSummaryScheduler.schedule(context, config.copy(isEnabled = enabled))
                            NotificationPassService.refreshCollectorCache(context)
                        }
                    },
                    onEdit = {
                        editingConfig = config
                        showEditDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch {
                            AppSummaryScheduler.cancel(context, config.id)
                            db.appCollectorDao().deleteConfig(config)
                            db.appCollectorDao().clearNotificationsByPackage(config.packageName)
                            NotificationPassService.refreshCollectorCache(context)
                            Toast.makeText(context, "${config.appName} 설정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onViewNotifications = {
                        showNotificationsForApp = config
                    },
                    onViewHistory = {
                        showSummaryHistoryForApp = config
                    },
                    onRunSummaryNow = {
                        val key = AppPreferences.getGeminiApiKey(context)
                        if (key.isBlank()) {
                            Toast.makeText(context, "Gemini API 키를 먼저 등록해주세요. (상단 'Gemini AI API 설정'에서 등록)", Toast.LENGTH_SHORT).show()
                            return@AppCollectorConfigCard
                        }
                        isRunningSummaryPackage = config.packageName
                        coroutineScope.launch {
                            val result = AppSummaryEngine.executeSummary(context, config, isManual = true)
                            isRunningSummaryPackage = null
                            if (result.isSuccess) {
                                summaryResultDialog = "🎉 [${config.appName} 알림 요약 완료]\n\n${result.summary}"
                            } else {
                                summaryResultDialog = "⚠️ [${config.appName} 요약 실패]\n\n${result.errorMessage}"
                            }
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        AppPickerDialog(
            onDismissRequest = { showAppPicker = false },
            showAllAppsOption = false,
            onAppSelected = { app ->
                showAppPicker = false
                if (app != null) {
                    val existing = configs.firstOrNull { it.packageName == app.packageName }
                    if (existing != null) {
                        editingConfig = existing
                    } else {
                        editingConfig = AppCollectorConfig(
                            packageName = app.packageName,
                            appName = app.appName
                        )
                    }
                    showEditDialog = true
                }
            }
        )
    }

    // Edit Config Dialog
    if (showEditDialog && editingConfig != null) {
        AppCollectorConfigEditDialog(
            initialConfig = editingConfig!!,
            onDismiss = { showEditDialog = false },
            onSave = { newConfig ->
                coroutineScope.launch {
                    val savedConfig = if (newConfig.id == 0L) {
                        val newId = db.appCollectorDao().insertConfig(newConfig)
                        newConfig.copy(id = newId)
                    } else {
                        db.appCollectorDao().updateConfig(newConfig)
                        newConfig
                    }
                    AppSummaryScheduler.schedule(context, savedConfig)
                    NotificationPassService.refreshCollectorCache(context)
                    Toast.makeText(context, "'${savedConfig.appName}' 알림 수집 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    showEditDialog = false
                }
            }
        )
    }

    // Collected Notifications Viewer Dialog
    showNotificationsForApp?.let { config ->
        CollectedNotificationsViewerDialog(
            config = config,
            db = db,
            onDismiss = { showNotificationsForApp = null }
        )
    }

    // Summary History Dialog
    showSummaryHistoryForApp?.let { config ->
        AppSummaryHistoryDialog(
            config = config,
            db = db,
            onDismiss = { showSummaryHistoryForApp = null }
        )
    }

    // Gemini API Connection Test Dialog
    geminiTestResult?.let { res ->
        AlertDialog(
            onDismissRequest = { geminiTestResult = null },
            icon = {
                Icon(
                    if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (res.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (res.isSuccess) "Gemini 연결 성공!" else "Gemini 연결 실패",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (res.isSuccess) {
                        Text("✅ 현재 API 키로 앱 알림 AI 요약이 정상 작동합니다.", fontWeight = FontWeight.Bold)
                        Text(
                            "• 선택된 최적 모델: ${res.selectedModel}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("• 테스트 응답: \"${res.sampleResponse}\"", fontSize = 13.sp)
                        if (res.availableModels.isNotEmpty()) {
                            Text(
                                "• 지원 모델 목록 (${res.availableModels.size}개):",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            res.availableModels.take(6).forEach { m ->
                                Text("  - $m", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        Text(
                            "구글 AI API 호출 중 오류가 발생했습니다:",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(res.errorMessage ?: "알 수 없는 오류", fontSize = 13.sp)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("💡 해결 가이드:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("1. aistudio.google.com 에 접속하여 API 키를 다시 생성하거나 복사해보세요.", fontSize = 11.sp)
                        Text("2. API 키에 앞뒤 공백이나 특수문자가 섞이지 않았는지 확인해주세요.", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { geminiTestResult = null }) {
                    Text("확인")
                }
            }
        )
    }

    // Manual Summary Result Dialog
    summaryResultDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { summaryResultDialog = null },
            title = { Text(if (msg.contains("완료")) "앱 알림 AI 요약 결과" else "요약 안내", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(msg, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(msg))
                        Toast.makeText(context, "요약 내용이 복사되었습니다.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("내용 복사")
                    }
                    Button(onClick = { summaryResultDialog = null }) {
                        Text("확인")
                    }
                }
            }
        )
    }
}

@Composable
fun AppCollectorConfigCard(
    config: AppCollectorConfig,
    db: AppDatabase,
    isRunning: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewNotifications: () -> Unit,
    onViewHistory: () -> Unit,
    onRunSummaryNow: () -> Unit
) {
    val count by db.appCollectorDao().getNotificationCountFlow(config.packageName).collectAsState(initial = 0)
    val summaryCount by db.appCollectorDao().getSummaryCountFlow(config.packageName).collectAsState(initial = 0)
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconImage(packageName = config.packageName, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = config.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = config.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (config.dismissNotification) {
                    BadgeChip(text = "🔕 상단바 알림 자동 숨김", color = Color(0xFFFFEBEE), textColor = Color(0xFFC62828))
                } else {
                    BadgeChip(text = "🔔 상단바 알림 유지", color = Color(0xFFF5F5F5), textColor = Color(0xFF616161))
                }

                if (config.isScheduledTimeEnabled) {
                    val amPm = if (config.scheduledHour < 12) "오전" else "오후"
                    val displayHour = when {
                        config.scheduledHour == 0 -> 12
                        config.scheduledHour > 12 -> config.scheduledHour - 12
                        else -> config.scheduledHour
                    }
                    val timeStr = String.format(Locale.KOREAN, "%s %d:%02d", amPm, displayHour, config.scheduledMinute)
                    BadgeChip(text = "⏰ 매일 $timeStr", color = Color(0xFFE8F5E9), textColor = Color(0xFF2E7D32))
                }

                val deliveryText = when (config.deliveryType) {
                    SummaryDeliveryType.SELF_SMS -> "나에게 SMS"
                    SummaryDeliveryType.LOCAL_NOTIFICATION -> "상단바 알림"
                    SummaryDeliveryType.TARGET_SMS -> "지정 번호 SMS"
                    SummaryDeliveryType.ALL -> "알림+문자 모두"
                    SummaryDeliveryType.NONE -> "발송 안 함(앱내보관)"
                }
                BadgeChip(text = "수신: $deliveryText", color = Color(0xFFE3F2FD), textColor = Color(0xFF1565C0))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "현재 수집된 알림: ${count}개" + (if (config.summaryThresholdCount > 0) " / ${config.summaryThresholdCount}개 시 요약" else " (수동 요약)"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )

                if (config.lastSummarizedTime > 0) {
                    Text(
                        text = "최근 요약: ${timeFormat.format(Date(config.lastSummarizedTime))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRunSummaryNow,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("요약 중...")
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("지금 요약")
                    }
                }

                FilledTonalButton(
                    onClick = onViewHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (summaryCount > 0) "요약 기록 ($summaryCount)" else "요약 기록")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Actions Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewNotifications
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("수집된 알림 ($count)")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "수정", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun AppCollectorConfigEditDialog(
    initialConfig: AppCollectorConfig,
    onDismiss: () -> Unit,
    onSave: (AppCollectorConfig) -> Unit
) {
    val context = LocalContext.current
    var dismissNotification by remember { mutableStateOf(initialConfig.dismissNotification) }
    var thresholdCountText by remember { mutableStateOf(initialConfig.summaryThresholdCount.toString()) }
    var isScheduledTimeEnabled by remember { mutableStateOf(initialConfig.isScheduledTimeEnabled) }
    var scheduledHour by remember { mutableStateOf(initialConfig.scheduledHour) }
    var scheduledMinute by remember { mutableStateOf(initialConfig.scheduledMinute) }
    var deliveryType by remember { mutableStateOf(initialConfig.deliveryType) }
    var targetSmsNumber by remember { mutableStateOf(initialConfig.targetSmsNumber) }
    var summaryStyle by remember { mutableStateOf(initialConfig.summaryStyle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(packageName = initialConfig.packageName, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${initialConfig.appName} 수집 설정",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "패키지: ${initialConfig.packageName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Status bar notification behavior section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "수집 후 스마트폰 상단바 알림 처리",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: Keep notification (알림 유지)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dismissNotification = false },
                            colors = CardDefaults.cardColors(
                                containerColor = if (!dismissNotification) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (!dismissNotification) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔔 알림 유지 (추천)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (!dismissNotification) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("상단바에 알림 남김\n(소리/진동 유지)", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Option 2: Dismiss notification (알림 삭제/숨김)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dismissNotification = true },
                            colors = CardDefaults.cardColors(
                                containerColor = if (dismissNotification) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (dismissNotification) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔕 알림 즉시 삭제", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (dismissNotification) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("상단바에서 즉시 지움\n(소음/진동 차단)", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                OutlinedTextField(
                    value = thresholdCountText,
                    onValueChange = { thresholdCountText = it.filter { char -> char.isDigit() } },
                    label = { Text("알림 N개 누적 시 자동 요약") },
                    placeholder = { Text("30 (0이면 누적 요약 없이 수동/정기 스케줄만)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Scheduled Daily Time Option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isScheduledTimeEnabled = !isScheduledTimeEnabled }
                ) {
                    Checkbox(
                        checked = isScheduledTimeEnabled,
                        onCheckedChange = { isScheduledTimeEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("⏰ 매일 지정된 시간에 정기 요약", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("하루 동안 모인 알림을 설정한 시각에 자동으로 Gemini가 요약합니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                if (isScheduledTimeEnabled) {
                    val amPm = if (scheduledHour < 12) "오전" else "오후"
                    val displayHour = when {
                        scheduledHour == 0 -> 12
                        scheduledHour > 12 -> scheduledHour - 12
                        else -> scheduledHour
                    }
                    val timeString = String.format(Locale.KOREAN, "%s %d시 %02d분", amPm, displayHour, scheduledMinute)

                    OutlinedButton(
                        onClick = {
                            android.app.TimePickerDialog(
                                context,
                                { _, h, m ->
                                    scheduledHour = h
                                    scheduledMinute = m
                                },
                                scheduledHour,
                                scheduledMinute,
                                false
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("발송 시간: $timeString (터치하여 변경)")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text("요약 스타일", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleOptionRow(
                        title = "🔄 자동 맞춤형 (추천)",
                        subtitle = "알림 양에 맞추어 알림 브리핑 자동 조절",
                        selected = summaryStyle == SummaryStyle.AUTO,
                        onSelect = { summaryStyle = SummaryStyle.AUTO }
                    )
                    StyleOptionRow(
                        title = "📑 상세 보고서형",
                        subtitle = "전체 핵심/항목별 분류/즉시 확인 사항까지 충실히 작성",
                        selected = summaryStyle == SummaryStyle.REPORT,
                        onSelect = { summaryStyle = SummaryStyle.REPORT }
                    )
                    StyleOptionRow(
                        title = "⚡ 간결형",
                        subtitle = "핵심 정보 2~4줄 불릿 포인트 요약",
                        selected = summaryStyle == SummaryStyle.BRIEF,
                        onSelect = { summaryStyle = SummaryStyle.BRIEF }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text("요약 결과 받을 곳", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeliveryOptionRow(
                        title = "내 폰 상단바 알림만 띄우기 (추천: 클릭 시 기록 확인)",
                        selected = deliveryType == SummaryDeliveryType.LOCAL_NOTIFICATION,
                        onSelect = { deliveryType = SummaryDeliveryType.LOCAL_NOTIFICATION }
                    )
                    DeliveryOptionRow(
                        title = "나에게 문자(SMS) 발송 (내 문자함 보관)",
                        selected = deliveryType == SummaryDeliveryType.SELF_SMS,
                        onSelect = { deliveryType = SummaryDeliveryType.SELF_SMS }
                    )
                    DeliveryOptionRow(
                        title = "지정된 다른 번호로 문자 발송",
                        selected = deliveryType == SummaryDeliveryType.TARGET_SMS,
                        onSelect = { deliveryType = SummaryDeliveryType.TARGET_SMS }
                    )
                    DeliveryOptionRow(
                        title = "상단바 알림 + 문자(SMS) 모두",
                        selected = deliveryType == SummaryDeliveryType.ALL,
                        onSelect = { deliveryType = SummaryDeliveryType.ALL }
                    )
                    DeliveryOptionRow(
                        title = "없음 (발송 안 함 - 앱 내 요약 기록 전용)",
                        selected = deliveryType == SummaryDeliveryType.NONE,
                        onSelect = { deliveryType = SummaryDeliveryType.NONE }
                    )
                }

                if (deliveryType == SummaryDeliveryType.TARGET_SMS) {
                    OutlinedTextField(
                        value = targetSmsNumber,
                        onValueChange = { targetSmsNumber = it },
                        label = { Text("수신 전화번호") },
                        placeholder = { Text("01012345678") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val threshold = thresholdCountText.toIntOrNull() ?: 30
                    val toSave = initialConfig.copy(
                        dismissNotification = dismissNotification,
                        summaryThresholdCount = threshold,
                        deliveryType = deliveryType,
                        targetSmsNumber = targetSmsNumber.trim(),
                        isScheduledTimeEnabled = isScheduledTimeEnabled,
                        scheduledHour = scheduledHour,
                        scheduledMinute = scheduledMinute,
                        summaryStyle = summaryStyle
                    )
                    onSave(toSave)
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun CollectedNotificationsViewerDialog(
    config: AppCollectorConfig,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val notifications by db.appCollectorDao().getNotificationsByPackage(config.packageName).collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showConfirmClearAll by remember { mutableStateOf(false) }

    val filtered = remember(notifications, searchQuery) {
        if (searchQuery.isBlank()) notifications
        else notifications.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true) ||
            it.subText.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(packageName = config.packageName, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[${config.appName}] 수집된 알림 (${notifications.size}건)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("알림 내용 검색") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (searchQuery.isBlank()) "아직 수집된 알림이 없습니다." else "검색 결과가 없습니다.",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { notif ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = timeFormat.format(Date(notif.postTime)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    db.appCollectorDao().deleteNotificationById(notif.id)
                                                }
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "삭제",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (notif.title.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = notif.content,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )

                                    if (notif.subText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "• ${notif.subText}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val copyText = if (notif.title.isNotBlank()) "[${notif.title}]\n${notif.content}" else notif.content
                                                clipboardManager.setText(AnnotatedString(copyText))
                                                Toast.makeText(context, "알림 내용이 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("복사", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = {
            if (notifications.isNotEmpty()) {
                TextButton(onClick = { showConfirmClearAll = true }) {
                    Text("전체 비우기", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )

    if (showConfirmClearAll) {
        AlertDialog(
            onDismissRequest = { showConfirmClearAll = false },
            title = { Text("알림 전체 삭제") },
            text = { Text("[${config.appName}]의 모든 수집된 알림을 삭제하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.appCollectorDao().clearNotificationsByPackage(config.packageName)
                            showConfirmClearAll = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("전체 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearAll = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun AppSummaryHistoryDialog(
    config: AppCollectorConfig,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val records by db.appCollectorDao().getSummaryRecordsByPackage(config.packageName).collectAsState(initial = emptyList())
    var showConfirmClearAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[${config.appName}] AI 요약 기록 (${records.size}건)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                if (records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("아직 생성된 요약 기록이 없습니다.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("알림이 누적되어 요약되거나 '지금 요약'을 누르면 이곳에 보관됩니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = timeFormat.format(Date(record.createdAt)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            BadgeChip(
                                                text = "${record.notificationCount}건 알림",
                                                color = Color(0xFFE8F5E9),
                                                textColor = Color(0xFF2E7D32)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    db.appCollectorDao().deleteSummaryRecord(record)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "기록 삭제",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = record.summaryText,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("[${config.appName} 알림 요약]\n${record.summaryText}"))
                                                Toast.makeText(context, "요약 내용이 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("복사", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = {
            if (records.isNotEmpty()) {
                TextButton(onClick = { showConfirmClearAll = true }) {
                    Text("전체 비우기", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )

    if (showConfirmClearAll) {
        AlertDialog(
            onDismissRequest = { showConfirmClearAll = false },
            title = { Text("요약 기록 전체 삭제") },
            text = { Text("[${config.appName}]의 모든 요약 기록을 삭제하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.appCollectorDao().clearSummaryRecordsByPackage(config.packageName)
                            showConfirmClearAll = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("전체 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearAll = false }) {
                    Text("취소")
                }
            }
        )
    }
}

private val appIconCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(150)

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var imageBitmap by remember(packageName) { mutableStateOf(appIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (imageBitmap == null) {
            withContext(Dispatchers.IO) {
                val cached = appIconCache.get(packageName)
                if (cached != null) {
                    imageBitmap = cached
                } else {
                    try {
                        val d = context.packageManager.getApplicationIcon(packageName)
                        val w = d.intrinsicWidth.coerceAtLeast(1)
                        val h = d.intrinsicHeight.coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        d.setBounds(0, 0, canvas.width, canvas.height)
                        d.draw(canvas)
                        val converted = bmp.asImageBitmap()
                        appIconCache.put(packageName, converted)
                        imageBitmap = converted
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}
