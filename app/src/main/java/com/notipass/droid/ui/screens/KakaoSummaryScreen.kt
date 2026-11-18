package com.notipass.droid.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.data.preference.AppPreferences
import com.notipass.droid.data.storage.KakaoLogStorage
import com.notipass.droid.engine.GeminiSummarizer
import com.notipass.droid.engine.GeminiTestResult
import com.notipass.droid.engine.GoogleDriveDispatcher
import com.notipass.droid.engine.KakaoSummaryEngine
import com.notipass.droid.engine.KakaoSummaryScheduler
import com.notipass.droid.model.KakaoSummaryConfig
import com.notipass.droid.model.KakaoSummaryRecord
import com.notipass.droid.model.SummaryDeliveryType
import com.notipass.droid.model.SummaryStyle
import com.notipass.droid.service.NotificationPassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KakaoSummaryScreen(
    db: AppDatabase,
    onNavigateBack: (() -> Unit)? = null,
    initialTargetRoom: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Preferences states
    var geminiApiKey by remember { mutableStateOf(AppPreferences.getGeminiApiKey(context)) }
    var myPhoneNumber by remember { mutableStateOf(AppPreferences.getMyPhoneNumber(context)) }
    var googleDriveUrl by remember { mutableStateOf(AppPreferences.getGoogleDriveUrl(context)) }
    var isDriveEnabled by remember { mutableStateOf(AppPreferences.isGoogleDriveEnabled(context)) }

    // Dialog & UI states
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<KakaoSummaryConfig?>(null) }
    var showLogsDialogForRoom by remember { mutableStateOf<String?>(null) }
    var showSummaryHistoryForRoom by remember { mutableStateOf(initialTargetRoom) }
    var showWatchGuideDialog by remember { mutableStateOf(false) }
    var showDriveGuide by remember { mutableStateOf(false) }
    var isTestingDrive by remember { mutableStateOf(false) }
    var isRunningSummaryId by remember { mutableStateOf<Long?>(null) }
    var isTestingGemini by remember { mutableStateOf(false) }
    var geminiTestResult by remember { mutableStateOf<GeminiTestResult?>(null) }
    var summaryResultDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTargetRoom) {
        if (!initialTargetRoom.isNullOrBlank()) {
            showSummaryHistoryForRoom = initialTargetRoom
        }
    }

    val configs by db.kakaoSummaryDao().getAllConfigs().collectAsState(initial = emptyList())

    // Keep NotificationPassService dismiss cache in sync with enabled configs
    LaunchedEffect(configs) {
        val dismissRooms = configs
            .filter { it.isEnabled && it.dismissNotification }
            .map { it.roomName.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        NotificationPassService.updateDismissRoomsCache(dismissRooms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단톡방 AI 요약 & 드라이브 백업", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingConfig = null
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "단톡방 추가")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingConfig = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "단톡방 추가")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Feature Intro Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "카톡 소음 완벽 차단 & 스마트 AI 요약",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "수백 개의 잡담 메시지 알림(소리/진동)을 상단바에서 즉시 숨기고, 텍스트로 보관하다가 핵심만 AI가 체계적인 보고서와 요약으로 정리해 드립니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showWatchGuideDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⌚ 갤럭시 워치 카톡 알림 완벽 차단 가이드", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 2. Gemini AI Key & Phone Setup Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI & 문자 발신 설정", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "무료 API 키 발급 (신용카드 불필요)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Google AI Studio 열기")
                            }
                        }

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
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTestingGemini
                        ) {
                            if (isTestingGemini) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("연결 확인 및 지원 모델 탐색 중...")
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini API 연결 & 모델 자동 탐색 테스트", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        OutlinedTextField(
                            value = myPhoneNumber,
                            onValueChange = {
                                myPhoneNumber = it
                                AppPreferences.setMyPhoneNumber(context, it)
                            },
                            label = { Text("내 전화번호 (나에게 문자 발송용)") },
                            placeholder = { Text("01012345678") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = "💡 요약 결과를 '나에게 문자(Self-SMS)'로 발송하면 기본 문자앱에 영구 보관됩니다.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 3. Google Drive Sync Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDriveEnabled) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant
                    )
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
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("구글 드라이브(Google Drive) 백업", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                            Switch(
                                checked = isDriveEnabled,
                                onCheckedChange = {
                                    isDriveEnabled = it
                                    AppPreferences.setGoogleDriveEnabled(context, it)
                                    if (it && googleDriveUrl.isBlank()) {
                                        Toast.makeText(context, "구글 드라이브 웹 앱 URL을 등록해주세요.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        Text(
                            text = "감시 대상 단톡방 메시지가 수신될 때마다 내 구글 드라이브의 '카카오톡_대화_백업/[방이름]' Google Docs(구글 문서)에 실시간 누적 저장됩니다. 제미나이 앱에서 '@Google Docs [방이름]'으로 바로 요약할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (isDriveEnabled) {
                            OutlinedTextField(
                                value = googleDriveUrl,
                                onValueChange = {
                                    googleDriveUrl = it
                                    AppPreferences.setGoogleDriveUrl(context, it)
                                },
                                label = { Text("Google Apps Script 웹 앱 URL") },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(GoogleDriveDispatcher.SAMPLE_GAS_SCRIPT))
                                        Toast.makeText(context, "구글 드라이브 연동 스크립트가 복사되었습니다!", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("스크립트 복사", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (googleDriveUrl.isBlank()) {
                                            Toast.makeText(context, "URL을 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
                                            return@OutlinedButton
                                        }
                                        isTestingDrive = true
                                        coroutineScope.launch {
                                            val testResult = GoogleDriveDispatcher.testConnection(googleDriveUrl, geminiApiKey)
                                            isTestingDrive = false
                                            if (testResult.isSuccess) {
                                                Toast.makeText(context, "🎉 연동 성공! 구글 드라이브에 Google Docs 문서가 생성되었습니다.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "연동 실패: ${testResult.errorMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isTestingDrive
                                ) {
                                    if (isTestingDrive) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("연동 테스트", fontSize = 12.sp)
                                    }
                                }
                            }

                            TextButton(
                                onClick = { showDriveGuide = !showDriveGuide }
                            ) {
                                Icon(
                                    if (showDriveGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("구글 문서 연동 & 매일 자동 요약 스케줄 가이드")
                            }

                            if (showDriveGuide) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("1. 위 [스크립트 복사] 버튼을 누릅니다.", fontSize = 12.sp)
                                        Text("2. script.google.com 에 접속 후 [새 프로젝트]를 만듭니다.", fontSize = 12.sp)
                                        Text("3. 기존 코드를 지우고 복사한 스크립트를 붙여넣은 뒤 저장(Ctrl+S)합니다.", fontSize = 12.sp)
                                        Text("4. 우측 상단 [배포] -> [새 배포] 클릭", fontSize = 12.sp)
                                        Text("5. 톱니바퀴 -> [웹 앱] 선택, 액세스 권한: '모든 사용자(Anyone)'로 지정하여 배포합니다.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("6. 배포된 '웹 앱 URL'을 복사해 위 입력창에 넣고 [연동 테스트]를 누르면 완료!", fontSize = 12.sp)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text("⏰ [매일 밤 자동 요약 스케줄 (휴대폰 배터리 소모 0%)]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        Text("• script.google.com 좌측 '트리거(⏰ 시계 아이콘)' 클릭 -> [트리거 추가]", fontSize = 11.sp)
                                        Text("• 실행할 함수: 'dailyScheduleSummary' 선택", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("• 이벤트 소스: '시간 기반' -> '일 단위 타이머(예: 오후 10시~11시)' 저장", fontSize = 11.sp)
                                        Text("• 이제 폰이 꺼져 있어도 구글 서버가 매일 밤 대화록을 읽고 Gemini로 자동 요약하여 구글 문서에 기록합니다!", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Group Chats Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "감시 및 요약 대상 단톡방 (${configs.size}개)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        editingConfig = null
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("단톡방 추가")
                    }
                }
            }

            // 5. Configs List
            if (configs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("등록된 감시 단톡방이 없습니다.", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "우측 상단 '+'를 눌러 알림을 숨기고 요약을 받을 단톡방 이름을 추가하세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(configs, key = { it.id }) { config ->
                    RoomConfigCard(
                        config = config,
                        db = db,
                        isRunning = isRunningSummaryId == config.id,
                        onToggleEnabled = { enabled ->
                            coroutineScope.launch {
                                db.kakaoSummaryDao().setConfigEnabled(config.id, enabled)
                                KakaoSummaryScheduler.schedule(context, config.copy(isEnabled = enabled))
                            }
                        },
                        onEdit = {
                            editingConfig = config
                            showAddEditDialog = true
                        },
                        onDelete = {
                            coroutineScope.launch {
                                KakaoSummaryScheduler.cancel(context, config.id)
                                db.kakaoSummaryDao().deleteConfig(config)
                                KakaoLogStorage.clearLogs(context, config.roomName)
                                Toast.makeText(context, "${config.roomName} 설정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onViewLogs = {
                            showLogsDialogForRoom = config.roomName
                        },
                        onViewHistory = {
                            showSummaryHistoryForRoom = config.roomName
                        },
                        onRunSummaryNow = {
                            if (geminiApiKey.isBlank()) {
                                Toast.makeText(context, "Gemini API 키를 먼저 등록해주세요.", Toast.LENGTH_SHORT).show()
                                return@RoomConfigCard
                            }
                            isRunningSummaryId = config.id
                            coroutineScope.launch {
                                val result = KakaoSummaryEngine.executeSummary(context, config, isManual = true)
                                isRunningSummaryId = null
                                if (result.isSuccess) {
                                    summaryResultDialog = "🎉 [${config.roomName} 요약 완료]\n\n${result.summary}"
                                } else {
                                    summaryResultDialog = "⚠️ [${config.roomName} 요약 실패]\n\n${result.errorMessage}\n\n💡 상단의 'Gemini API 연결 & 모델 자동 탐색 테스트' 버튼을 눌러 정확한 원인을 확인해보세요."
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
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        RoomConfigEditDialog(
            initialConfig = editingConfig,
            onDismiss = { showAddEditDialog = false },
            onSave = { newConfig ->
                coroutineScope.launch {
                    val savedConfig = if (editingConfig == null) {
                        val newId = db.kakaoSummaryDao().insertConfig(newConfig)
                        newConfig.copy(id = newId)
                    } else {
                        db.kakaoSummaryDao().updateConfig(newConfig)
                        newConfig
                    }
                    KakaoSummaryScheduler.schedule(context, savedConfig)
                    Toast.makeText(context, "'${savedConfig.roomName}' 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    showAddEditDialog = false
                }
            }
        )
    }

    // Raw Logs Viewer Dialog
    showLogsDialogForRoom?.let { roomName ->
        RoomLogsDialog(
            roomName = roomName,
            onDismiss = { showLogsDialogForRoom = null }
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
                        Text("✅ 현재 API 키로 AI 요약이 정상 작동합니다.", fontWeight = FontWeight.Bold)
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
            title = {
                Text(
                    text = if (msg.contains("완료")) "단톡방 AI 요약 결과" else "요약 안내",
                    fontWeight = FontWeight.Bold
                )
            },
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
                        Toast.makeText(context, "요약 내용이 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
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

    // Summary History Dialog
    showSummaryHistoryForRoom?.let { roomName ->
        RoomSummaryHistoryDialog(
            roomName = roomName,
            db = db,
            onDismiss = { showSummaryHistoryForRoom = null }
        )
    }

    // Galaxy Watch Notification Suppression Guide Dialog
    if (showWatchGuideDialog) {
        GalaxyWatchGuideDialog(onDismiss = { showWatchGuideDialog = false })
    }
}

@Composable
fun RoomConfigCard(
    config: KakaoSummaryConfig,
    db: AppDatabase,
    isRunning: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewLogs: () -> Unit,
    onViewHistory: () -> Unit,
    onRunSummaryNow: () -> Unit
) {
    val context = LocalContext.current
    var currentCount by remember { mutableStateOf(0) }
    val historyCount by db.kakaoSummaryDao().getSummaryCountByRoom(config.roomName).collectAsState(initial = 0)
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(config.roomName) {
        withContext(Dispatchers.IO) {
            currentCount = KakaoLogStorage.getMessageCount(context, config.roomName)
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = config.roomName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (config.dismissNotification) {
                    BadgeChip(text = "🔕 상단바 알림 자동 숨김", color = Color(0xFFFFEBEE), textColor = Color(0xFFC62828))
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

                when (config.summaryStyle) {
                    SummaryStyle.REPORT -> {
                        BadgeChip(text = "📑 상세 보고서", color = Color(0xFFE0F7FA), textColor = Color(0xFF006064))
                    }
                    SummaryStyle.BRIEF -> {
                        BadgeChip(text = "⚡ 간결형", color = Color(0xFFFFF3E0), textColor = Color(0xFFE65100))
                    }
                    SummaryStyle.AUTO -> {
                        BadgeChip(text = "🔄 자동 맞춤형", color = Color(0xFFEDE7F6), textColor = Color(0xFF4A148C))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "현재 누적 대화: ${currentCount}개" + (if (config.summaryThresholdCount > 0) " / ${config.summaryThresholdCount}개 시 요약" else " (수동 요약)"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (currentCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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

            // Action Buttons
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
                    Text(if (historyCount > 0) "요약 기록 ($historyCount)" else "요약 기록")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewLogs
                ) {
                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("대화 원본")
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
fun BadgeChip(text: String, color: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RoomConfigEditDialog(
    initialConfig: KakaoSummaryConfig?,
    onDismiss: () -> Unit,
    onSave: (KakaoSummaryConfig) -> Unit
) {
    val context = LocalContext.current
    var roomName by remember { mutableStateOf(initialConfig?.roomName ?: "") }
    var dismissNotification by remember { mutableStateOf(initialConfig?.dismissNotification ?: true) }
    var thresholdCountText by remember { mutableStateOf((initialConfig?.summaryThresholdCount ?: 30).toString()) }
    var isScheduledTimeEnabled by remember { mutableStateOf(initialConfig?.isScheduledTimeEnabled ?: false) }
    var scheduledHour by remember { mutableStateOf(initialConfig?.scheduledHour ?: 21) }
    var scheduledMinute by remember { mutableStateOf(initialConfig?.scheduledMinute ?: 0) }
    var deliveryType by remember { mutableStateOf(initialConfig?.deliveryType ?: SummaryDeliveryType.SELF_SMS) }
    var targetSmsNumber by remember { mutableStateOf(initialConfig?.targetSmsNumber ?: "") }
    var summaryStyle by remember { mutableStateOf(initialConfig?.summaryStyle ?: SummaryStyle.AUTO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) "요약 대상 단톡방 추가" else "단톡방 설정 수정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("카카오톡 단톡방 이름 (정확히 입력)") },
                    placeholder = { Text("예: 가족방, 팀프로젝트 단톡") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dismissNotification = !dismissNotification }
                ) {
                    Checkbox(
                        checked = dismissNotification,
                        onCheckedChange = { dismissNotification = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("상단바 알림 자동 숨김 (추천)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("메시지가 와도 소리/진동을 울리지 않고 상단바에서 즉시 지웁니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                OutlinedTextField(
                    value = thresholdCountText,
                    onValueChange = { thresholdCountText = it.filter { char -> char.isDigit() } },
                    label = { Text("대화 N개 누적 시 자동 요약") },
                    placeholder = { Text("30 (0이면 누적요약 없이 매일 지정 시간에만)") },
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
                        Text("설정한 시각에 자동으로 Gemini 요약이 발송됩니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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

                Text("요약 스타일 (보고서 / 간결형)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleOptionRow(
                        title = "🔄 자동 맞춤형 (기본 추천)",
                        subtitle = "대화량이 많으면 심층 보고서, 적으면 핵심 요약으로 자동 조절",
                        selected = summaryStyle == SummaryStyle.AUTO,
                        onSelect = { summaryStyle = SummaryStyle.AUTO }
                    )
                    StyleOptionRow(
                        title = "📑 상세 보고서형 (Executive Report)",
                        subtitle = "안건/논의과정/결정사항/일정/미해결 과제까지 충실하고 풍부하게 작성",
                        selected = summaryStyle == SummaryStyle.REPORT,
                        onSelect = { summaryStyle = SummaryStyle.REPORT }
                    )
                    StyleOptionRow(
                        title = "⚡ 간결형 (Quick Brief)",
                        subtitle = "바쁜 일상을 위해 2~3줄 핵심 위주로 빠르게 요약",
                        selected = summaryStyle == SummaryStyle.BRIEF,
                        onSelect = { summaryStyle = SummaryStyle.BRIEF }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text("요약 결과 받을 곳", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeliveryOptionRow(
                        title = "나에게 문자(SMS) 발송 (추천: 내 문자함 보관)",
                        selected = deliveryType == SummaryDeliveryType.SELF_SMS,
                        onSelect = { deliveryType = SummaryDeliveryType.SELF_SMS }
                    )
                    DeliveryOptionRow(
                        title = "내 폰 상단바 알림만 띄우기 (클릭 시 앱에서 기록 확인)",
                        selected = deliveryType == SummaryDeliveryType.LOCAL_NOTIFICATION,
                        onSelect = { deliveryType = SummaryDeliveryType.LOCAL_NOTIFICATION }
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
                        title = "없음 (발송 안 함 - 앱 내 요약 기록 및 드라이브 전용)",
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
                    if (roomName.isBlank()) {
                        Toast.makeText(context, "단톡방 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val threshold = thresholdCountText.toIntOrNull() ?: 30
                    val configToSave = initialConfig?.copy(
                        roomName = roomName.trim(),
                        dismissNotification = dismissNotification,
                        summaryThresholdCount = threshold,
                        deliveryType = deliveryType,
                        targetSmsNumber = targetSmsNumber.trim(),
                        isScheduledTimeEnabled = isScheduledTimeEnabled,
                        scheduledHour = scheduledHour,
                        scheduledMinute = scheduledMinute,
                        summaryStyle = summaryStyle
                    ) ?: KakaoSummaryConfig(
                        roomName = roomName.trim(),
                        dismissNotification = dismissNotification,
                        summaryThresholdCount = threshold,
                        deliveryType = deliveryType,
                        targetSmsNumber = targetSmsNumber.trim(),
                        isScheduledTimeEnabled = isScheduledTimeEnabled,
                        scheduledHour = scheduledHour,
                        scheduledMinute = scheduledMinute,
                        summaryStyle = summaryStyle
                    )
                    onSave(configToSave)
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
fun DeliveryOptionRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, fontSize = 13.sp)
    }
}

@Composable
fun StyleOptionRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun RoomLogsDialog(roomName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf("로딩 중...") }

    LaunchedEffect(roomName) {
        withContext(Dispatchers.IO) {
            logs = KakaoLogStorage.readLogs(context, roomName).ifBlank { "(아직 누적된 대화 내용이 없습니다.)" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("[$roomName] 누적 대화 원본") },
        text = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = logs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        KakaoLogStorage.clearLogs(context, roomName)
                        logs = "(대화록이 비워졌습니다.)"
                    }
                }
            ) {
                Text("대화록 비우기", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun RoomSummaryHistoryDialog(
    roomName: String,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val records by db.kakaoSummaryDao().getSummaryRecordsByRoom(roomName).collectAsState(initial = emptyList())
    var showConfirmClearAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[$roomName] 요약 기록 (${records.size}건)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
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
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "아직 생성된 요약 기록이 없습니다.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "누적 대화 요약 또는 매일 정기 요약이 실행되면\n이곳에 차곡차곡 보관됩니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
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
                                                text = "${record.messageCount}개 대화",
                                                color = Color(0xFFE8F5E9),
                                                textColor = Color(0xFF2E7D32)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    db.kakaoSummaryDao().deleteSummaryRecord(record)
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
                                                clipboardManager.setText(AnnotatedString("[AI 요약: $roomName]\n${record.summaryText}"))
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
            text = { Text("[$roomName]의 모든 요약 기록을 삭제하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.kakaoSummaryDao().clearSummaryRecordsByRoom(roomName)
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
fun GalaxyWatchGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("⌚ 갤럭시 워치 카톡 알림 차단 가이드", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "휴대폰에서는 상단바 알림이 즉시 숨겨지는데 갤럭시 워치에서 계속 진동이 울리는 경우, 삼성 웨어러블 앱이 블루투스로 알림을 즉시 복사하기 때문입니다.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🚀 NotiPassDroid v1.6.0 엔진 개선 완료", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text("• 수신 즉시 0.1ms 이내에 초고속 알림 취소(Cancel & Snooze)를 적용하여 워치 전달을 선제 차단합니다.", fontSize = 11.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("✨ 100% 완벽 차단을 위한 2가지 필수 설정", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1️⃣ 카카오톡 설정 (가장 중요!)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
                        Text("• 카톡 앱 실행 > ⚙️ 설정 > [전체 설정] > [알림]", fontSize = 11.sp)
                        Text("• [알림센터에 메시지 표시] 항목 클릭", fontSize = 11.sp)
                        Text("• [알림 켠 채팅방만]으로 변경!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("💡 단톡방의 카톡 알림(종 아이콘)을 꺼두시면 워치로 소리/진동이 아예 가지 않으며, NotiPassDroid는 정상적으로 대화록을 수집하고 요약합니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("2️⃣ 갤럭시 웨어러블 앱 설정", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1565C0))
                        Text("• Galaxy Wearable 앱 실행 > [워치 설정] > [알림]", fontSize = 11.sp)
                        Text("• [휴대전화에서 알림 삭제 시 워치에서도 삭제] 켜기", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("• [휴대전화를 사용하는 중에는 워치에 알림 표시 안 함] 켜기", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인했습니다")
            }
        }
    )
}
