package com.notipass.droid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.notipass.droid.data.db.AppDatabase
import com.notipass.droid.ui.screens.HomeScreen
import com.notipass.droid.ui.screens.KakaoSummaryScreen
import com.notipass.droid.ui.screens.LogsScreen
import com.notipass.droid.ui.screens.RuleEditScreen
import com.notipass.droid.ui.screens.RulesScreen
import com.notipass.droid.ui.theme.NotiPassDroidTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var initError: Throwable? = null
        val db: AppDatabase? = try {
            val app = application as NotiPassApplication
            app.database
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error obtaining database", t)
            initError = t
            null
        }

        setContent {
            NotiPassDroidTheme {
                if (initError != null || db == null) {
                    ErrorFallbackScreen(
                        error = initError ?: RuntimeException("데이터베이스를 초기화할 수 없습니다."),
                        onReset = {
                            try {
                                applicationContext.deleteDatabase("notipass_database")
                            } catch (_: Exception) {}
                            recreate()
                        }
                    )
                } else {
                    AppNavigation(db = db)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(db: AppDatabase) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isEditRoute = currentRoute?.startsWith("rule_edit") == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isEditRoute) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "홈") },
                        label = { Text("대시보드") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "rules",
                        onClick = {
                            navController.navigate("rules") {
                                popUpTo("home")
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = "규칙") },
                        label = { Text("규칙 관리") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "kakao_summary",
                        onClick = {
                            navController.navigate("kakao_summary") {
                                popUpTo("home")
                            }
                        },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI 요약") },
                        label = { Text("AI 요약/백업") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "logs",
                        onClick = {
                            navController.navigate("logs") {
                                popUpTo("home")
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "로그") },
                        label = { Text("실행 로그") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    db = db,
                    onNavigateToRules = { navController.navigate("rules") },
                    onNavigateToLogs = { navController.navigate("logs") },
                    onNavigateToKakaoSummary = { navController.navigate("kakao_summary") }
                )
            }
            composable("rules") {
                RulesScreen(
                    db = db,
                    onNavigateToEditRule = { ruleId ->
                        navController.navigate("rule_edit/$ruleId")
                    }
                )
            }
            composable("kakao_summary") {
                KakaoSummaryScreen(db = db)
            }
            composable(
                route = "rule_edit/{ruleId}",
                arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
            ) { backStackEntry ->
                val ruleId = backStackEntry.arguments?.getLong("ruleId") ?: 0L
                RuleEditScreen(
                    db = db,
                    ruleId = ruleId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("logs") {
                LogsScreen(db = db)
            }
        }
    }
}

@Composable
fun ErrorFallbackScreen(error: Throwable, onReset: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "앱 초기화 중 오류 발생",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "이전 버전의 데이터베이스 구조와 충돌했을 수 있습니다.\n아래 버튼을 눌러 로컬 DB를 초기화하고 다시 시작하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${error.javaClass.simpleName}: ${error.message}\n\n${error.stackTraceToString().take(600)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("데이터베이스 초기화 후 재시작")
            }
        }
    }
}
