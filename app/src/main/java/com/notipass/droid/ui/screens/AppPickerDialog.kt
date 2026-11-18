package com.notipass.droid.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    onDismissRequest: () -> Unit,
    showAllAppsOption: Boolean = true,
    onAppSelected: (InstalledApp?) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var includeSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager

            // 1. Get packages with launcher activities (Launcher apps like Gmail, YouTube, Messages, etc.)
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchablePackages = try {
                pm.queryIntentActivities(launcherIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }

            // 2. Query all installed applications
            val installedApps = try {
                pm.getInstalledApplications(0)
            } catch (e: Exception) {
                try {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                } catch (e2: Exception) {
                    emptyList()
                }
            }

            // 3. Transform to InstalledApp
            val processed = installedApps.mapNotNull { appInfo ->
                try {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val isLaunchable = launchablePackages.contains(appInfo.packageName)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    // Pure background system component if it's system, not updated, and not launchable
                    val isPureSystem = isSystem && !isUpdatedSystem && !isLaunchable

                    InstalledApp(
                        appName = label.ifBlank { appInfo.packageName },
                        packageName = appInfo.packageName,
                        isSystemApp = isPureSystem
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedWith(
                compareBy<InstalledApp> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )

            apps = processed
            isLoading = false
        }
    }

    val filteredApps = remember(apps, searchQuery, includeSystemApps) {
        val q = searchQuery.trim()
        val baseList = if (includeSystemApps || q.isNotBlank()) {
            apps
        } else {
            apps.filter { !it.isSystemApp }
        }

        if (q.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.appName.contains(q, ignoreCase = true) ||
                        it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "대상 앱 선택",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("앱 이름 또는 패키지명 검색") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "지우기")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilterChip(
                        selected = includeSystemApps,
                        onClick = { includeSystemApps = !includeSystemApps },
                        label = {
                            Text(
                                text = if (includeSystemApps) "시스템 앱 포함됨" else "시스템 앱 포함",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                    Text(
                        text = "총 ${filteredApps.size}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showAllAppsOption) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(null) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌐 모든 앱 (필터 없음)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // If user typed a search query and it's not exact match to an existing package, offer direct manual entry
                val trimmedQuery = searchQuery.trim()
                if (trimmedQuery.isNotBlank() && !filteredApps.any { it.packageName.equals(trimmedQuery, ignoreCase = true) }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAppSelected(
                                    InstalledApp(
                                        appName = trimmedQuery,
                                        packageName = trimmedQuery,
                                        isSystemApp = false
                                    )
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "직접 입력한 패키지명 추가",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = trimmedQuery,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "일치하는 앱이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(app) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(
                                    packageName = app.packageName,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (app.isSystemApp) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "시스템",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}
