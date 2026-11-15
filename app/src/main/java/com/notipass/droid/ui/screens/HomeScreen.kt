package com.notipass.droid.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.model.ActionType
import com.notipass.droid.model.LogStatus
import com.notipass.droid.model.NotificationLog
import com.notipass.droid.model.Rule
import com.notipass.droid.model.TriggerType
import com.notipass.droid.service.NotificationPassService
import com.notipass.droid.ui.theme.StatusError
import com.notipass.droid.ui.theme.StatusSuccess
import com.notipass.droid.util.PermissionUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    db: AppDatabase,
    onNavigateToRules: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isListenerEnabled by remember {
        mutableStateOf(PermissionUtil.isNotificationListenerEnabled(context))
    }
    var isBatteryOptIgnored by remember {
        mutableStateOf(PermissionUtil.isIgnoringBatteryOptimizations(context))
    }
    var hasPhoneSmsPerms by remember {
        mutableStateOf(PermissionUtil.hasPhoneAndSmsPermissions(context))
    }
    val isServiceConnected by NotificationPassService.isServiceConnected.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPhoneSmsPerms = results.values.all { it }
        if (hasPhoneSmsPerms) {
            Toast.makeText(context, "전화 및 SMS 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "일부 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val totalCount by db.logDao().getTotalCount().collectAsState(initial = 0)
    val successCount by db.logDao().getSuccessCount().collectAsState(initial = 0)
    val failedCount by db.logDao().getFailedCount().collectAsState(initial = 0)
    val recentLogs by db.logDao().getRecentLogs(limit = 5).collectAsState(initial = emptyList())

    var showQuickCallSmsDialog by remember { mutableStateOf(false) }
    var showQuickKakaoSmsDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        isListenerEnabled = PermissionUtil.isNotificationListenerEnabled(context)
        isBatteryOptIgnored = PermissionUtil.isIgnoringBatteryOptimizations(context)
        hasPhoneSmsPerms = PermissionUtil.hasPhoneAndSmsPermissions(context)
        NotificationPassService.rebindService(context)
        onDispose { }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status Overview Card
        item {
            val statusColor = when {
                !isListenerEnabled -> MaterialTheme.colorScheme.errorContainer
                !isServiceConnected -> Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val statusDotColor = when {
                !isListenerEnabled -> StatusError
                !isServiceConnected -> Color(0xFFE65100)
                else -> StatusSuccess
            }
            val statusTitle = when {
                !isListenerEnabled -> "알림 접근 권한 필요"
                !isServiceConnected -> "알림 권한 허용됨 (서비스 연결 대기 중)"
                else -> "알림 수신 서비스 정상 동작 중 (준비 완료)"
            }
            val statusDesc = when {
                !isListenerEnabled -> "카카오톡 및 시스템 알림을 가로채려면 [알림 접근 권한]이 활성화되어 있어야 합니다."
                !isServiceConnected -> "앱 업데이트 후 안드로이드 시스템이 알림 수신 연결을 일시 중단했습니다. 아래 [서비스 재연결]을 누르거나 설정에서 알림 접근 허용을 껐다 켜주세요."
                else -> "기기의 카카오톡 알림 및 통화를 실시간 감지하여 등록된 번호로 문자를 자동 발송합니다."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = statusColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = statusDesc, style = MaterialTheme.typography.bodyMedium)

                    if (!isListenerEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { PermissionUtil.openNotificationListenerSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("알림 접근 권한 설정 열기")
                        }
                    } else if (!isServiceConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    NotificationPassService.rebindService(context)
                                    Toast.makeText(context, "서비스 재연결을 요청했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("서비스 재연결")
                            }
                            OutlinedButton(
                                onClick = { PermissionUtil.openNotificationListenerSettings(context) }
                            ) {
                                Text("설정에서 껐다 켜기")
                            }
                        }
                    }

                    if (!isBatteryOptIgnored) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { PermissionUtil.requestIgnoreBatteryOptimizations(context) }
                        ) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("백그라운드 배터리 최적화 해제")
                        }
                    }
                }
            }
        }

        // 2. Call & SMS Permission Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPhoneSmsPerms) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.PhoneCallback,
                            contentDescription = null,
                            tint = if (hasPhoneSmsPerms) MaterialTheme.colorScheme.primary else Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasPhoneSmsPerms) "전화 수신, 연락처 조회 및 문자 발송 활성화됨" else "전화/연락처/SMS 권한 필요",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "전화가 왔을 때 번호를 감지하고, 저장된 연락처 이름을 확인하여 등록된 번호로 안내 문자를 자동 발송하려면 권한이 필요합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    if (!hasPhoneSmsPerms) {
                        Button(
                            onClick = {
                                permLauncher.launch(PermissionUtil.REQUIRED_PHONE_PERMISSIONS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("전화/연락처/SMS 권한 허용하기")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showQuickCallSmsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("전화 문자 전달", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showQuickKakaoSmsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("카톡 문자 전달", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Statistics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "총 감지",
                    value = totalCount.toString(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "전송 성공",
                    value = successCount.toString(),
                    color = Color(0xFFE8F5E9),
                    valueColor = StatusSuccess
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "전송 실패",
                    value = failedCount.toString(),
                    color = Color(0xFFFFEBEE),
                    valueColor = StatusError
                )
            }
        }

        // 4. Quick Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "빠른 동작",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onNavigateToRules,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("규칙 관리")
                        }
                        OutlinedButton(
                            onClick = onNavigateToLogs,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("로그 전체보기")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val mockPayload = com.notipass.droid.engine.NotificationPayload(
                                    packageName = "com.kakao.talk",
                                    appName = "카카오톡",
                                    title = "홍길동",
                                    content = "안녕하세요! NotiPass 카카오톡 수신 테스트 메시지입니다.",
                                    subText = "테스트 단톡방"
                                )
                                NotificationPassService.processNotificationPayload(context, mockPayload)
                                Toast.makeText(context, "가상 카톡 알림을 발송했습니다. [로그 전체보기]를 확인하세요!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("카톡 알림 가상 시뮬레이션 테스트 (문자 전송 확인)")
                    }
                }
            }
        }

        // 5. Recent Logs Preview
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "최근 감지 및 전송 내역",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToLogs) {
                    Text("더보기")
                }
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "아직 감지된 알림이나 통화가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentLogs) { log ->
                LogPreviewItem(log)
            }
        }
    }

    // Quick Call SMS Setup Dialog
    if (showQuickCallSmsDialog) {
        var quickTargetNumber by remember { mutableStateOf("") }
        var quickSmsTemplate by remember {
            mutableStateOf("[NotiPass] {caller_display}님으로부터 전화가 수신되었습니다. ({date})")
        }
        var sendToCallerOnly by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showQuickCallSmsDialog = false },
            title = { Text("전화 수신 자동 문자 규칙 생성") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "전화가 올 때 자동으로 SMS를 전송할 대상 번호와 내용을 설정하세요. 연락처에 저장된 번호면 이름도 자동 표시됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sendToCallerOnly,
                            onCheckedChange = {
                                sendToCallerOnly = it
                                if (it) {
                                    quickSmsTemplate = "[자동응답] 현재 통화가 어렵습니다. 용건을 문자로 남겨주시면 확인 후 연락드리겠습니다."
                                } else {
                                    quickSmsTemplate = "[NotiPass] {caller_display}님으로부터 전화가 수신되었습니다. ({date})"
                                }
                            }
                        )
                        Text(
                            text = "전화 건 사람(발신자)에게 자동 회신 문자",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!sendToCallerOnly) {
                        OutlinedTextField(
                            value = quickTargetNumber,
                            onValueChange = { quickTargetNumber = it },
                            label = { Text("알림 받을 전화번호") },
                            placeholder = { Text("예: 01012345678 (내 다른 폰 번호)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = quickSmsTemplate,
                        onValueChange = { quickSmsTemplate = it },
                        label = { Text("전송할 문자 내용") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTarget = if (sendToCallerOnly) "{caller}" else quickTargetNumber.trim()
                        if (finalTarget.isBlank()) {
                            Toast.makeText(context, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val newRule = Rule(
                                name = if (sendToCallerOnly) "발신자 통화 부재 자동응답" else "전화 수신 알림 문자 전달",
                                isEnabled = true,
                                triggerType = TriggerType.INCOMING_CALL,
                                actionType = ActionType.SMS,
                                smsTargetNumber = finalTarget,
                                smsTemplate = quickSmsTemplate
                            )
                            db.ruleDao().insertRule(newRule)
                            showQuickCallSmsDialog = false
                            Toast.makeText(context, "통화 자동 문자 규칙이 등록되었습니다!", Toast.LENGTH_SHORT).show()
                            onNavigateToRules()
                        }
                    }
                ) {
                    Text("규칙 추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCallSmsDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // Quick KakaoTalk SMS Setup Dialog
    if (showQuickKakaoSmsDialog) {
        var kakaoTargetNumber by remember { mutableStateOf("") }
        var kakaoSmsTemplate by remember {
            mutableStateOf("[카톡] {sender_display}: {content} ({time})")
        }

        AlertDialog(
            onDismissRequest = { showQuickKakaoSmsDialog = false },
            title = { Text("카카오톡 수신 문자 전달 규칙 생성") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "카카오톡 메시지를 받았을 때 발신자 이름과 내용을 등록된 번호로 자동 전달합니다. 연락처에 등록된 사람이면 전화번호도 함께 표기됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = kakaoTargetNumber,
                        onValueChange = { kakaoTargetNumber = it },
                        label = { Text("알림 받을 전화번호") },
                        placeholder = { Text("예: 01012345678 (내 다른 폰 번호)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = kakaoSmsTemplate,
                        onValueChange = { kakaoSmsTemplate = it },
                        label = { Text("전송할 문자 내용") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Text(
                        text = "치환 토큰: {sender_display}(발신자+번호), {room}(단톡방), {content}(내용), {time}(시각)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (kakaoTargetNumber.isBlank()) {
                            Toast.makeText(context, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val newRule = Rule(
                                name = "카카오톡 수신 문자 전달",
                                isEnabled = true,
                                triggerType = TriggerType.NOTIFICATION,
                                targetPackage = "com.kakao.talk",
                                targetAppName = "카카오톡",
                                actionType = ActionType.SMS,
                                smsTargetNumber = kakaoTargetNumber.trim(),
                                smsTemplate = kakaoSmsTemplate
                            )
                            db.ruleDao().insertRule(newRule)
                            showQuickKakaoSmsDialog = false
                            Toast.makeText(context, "카톡 문자 전달 규칙이 등록되었습니다!", Toast.LENGTH_SHORT).show()
                            onNavigateToRules()
                        }
                    }
                ) {
                    Text("규칙 추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickKakaoSmsDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
fun LogPreviewItem(log: NotificationLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (log.status == LogStatus.SUCCESS) StatusSuccess else StatusError)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.appName.ifBlank { log.packageName },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (log.title.isNotBlank()) {
                    Text(
                        text = log.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Text(
                    text = log.content.ifBlank { "(내용 없음)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}
