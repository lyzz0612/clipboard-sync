package com.clipboardsync.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clipboardsync.app.R
import com.clipboardsync.app.util.MiuiHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsGuideScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appName = context.getString(R.string.app_name)
    val isMiui = MiuiHelper.isMiui()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "为确保剪贴板自动同步正常工作，请完成以下设置：",
                style = MaterialTheme.typography.bodyLarge
            )

            if (isMiui) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "小米 / MIUI：上划最近任务会杀进程",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "在 MIUI 上，从多任务界面上划掉本应用，通常会直接结束进程（与「强行停止」类似），无障碍与后台同步会停，直到您再次打开本应用。这不是应用能绕过的系统策略。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "建议：\n" +
                                "• 需要长期后台时：打开多任务界面，长按 $appName 卡片，点「锁定」（锁形图标），再清理其它任务。\n" +
                                "• 日常可多用桌面键返回桌面，少用上划结束本应用。\n" +
                                "• 下方「省电策略」设为「无限制」，并开启自启动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { MiuiHelper.openApplicationDetailsSettings(context) }) {
                            Text("打开本应用信息（省电策略）")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. 无障碍服务",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "开启后，App 会在检测到输入法弹出时自动同步最新剪贴板内容。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "路径：设置 → 无障碍 → 已下载的应用 → $appName → 开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { MiuiHelper.openAccessibilitySettings(context) }) {
                        Text("前往无障碍设置")
                    }
                }
            }

            if (isMiui) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. 自启动权限（小米/MIUI）",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "允许 App 在开机后自动启动，保持后台同步。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "路径：设置 → 应用设置 → 应用管理 → $appName → 自启动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { MiuiHelper.openAutoStartSettings(context) }) {
                            Text("前往自启动设置")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isMiui) "3. 省电策略" else "2. 电池优化",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "将 $appName 排除在电池优化之外，防止后台同步被系统杀死。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { MiuiHelper.openBatteryOptimizationSettings(context) }) {
                        Text("前往电池设置")
                    }
                }
            }
        }
    }
}
