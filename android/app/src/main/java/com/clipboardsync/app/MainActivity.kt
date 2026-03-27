package com.clipboardsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.ui.main.MainScreen
import com.clipboardsync.app.ui.setup.SetupScreen
import com.clipboardsync.app.ui.theme.ClipboardSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClipboardSyncTheme {
                val navController = rememberNavController()
                val prefs = remember { PrefsManager.getInstance(applicationContext) }
                val startDest = if (prefs.isLoggedIn()) "main" else "setup"

                NavHost(navController, startDestination = startDest) {
                    composable("setup") {
                        SetupScreen(
                            onLoginSuccess = {
                                navController.navigate("main") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("main") {
                        MainScreen(
                            onLogout = {
                                navController.navigate("setup") {
                                    popUpTo("main") { inclusive = true }
                                }
                            },
                            onNavigateToPermissions = {
                                navController.navigate("permissions")
                            }
                        )
                    }
                    composable("permissions") {
                        com.clipboardsync.app.ui.settings.PermissionsGuideScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
