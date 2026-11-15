package com.notipass.droid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.notipass.droid.ui.screens.HomeScreen
import com.notipass.droid.ui.screens.LogsScreen
import com.notipass.droid.ui.screens.RuleEditScreen
import com.notipass.droid.ui.screens.RulesScreen
import com.notipass.droid.ui.theme.NotiPassDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NotiPassApplication
        val db = app.database

        setContent {
            NotiPassDroidTheme {
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
                                    icon = { Icon(Icons.Default.Rule, contentDescription = "규칙") },
                                    label = { Text("규칙 관리") }
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
                                onNavigateToLogs = { navController.navigate("logs") }
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
        }
    }
}
