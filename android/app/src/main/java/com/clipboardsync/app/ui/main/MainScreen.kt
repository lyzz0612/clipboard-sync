package com.clipboardsync.app.ui.main

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipboardsync.app.R
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.service.ClipboardAccessibilityService
import com.clipboardsync.app.util.MiuiHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val app = context.applicationContext as Application
    val prefs = remember { PrefsManager.getInstance(context) }
    val username = remember { prefs.getUsername() }
    val repository = remember { ClipboardRepository(prefs) }
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(app, prefs, repository)
    )

    val clips by viewModel.clips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var newClipText by rememberSaveable { mutableStateOf("") }
    var showPermissionGuide by rememberSaveable {
        mutableStateOf(
            !prefs.hasSeenPermissionGuide() &&
                !MiuiHelper.isAccessibilityServiceEnabled(context, ClipboardAccessibilityService::class.java)
        )
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(infoMessage) {
        infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfoMessage()
        }
    }

    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = {
                prefs.setHasSeenPermissionGuide(true)
                showPermissionGuide = false
            },
            title = { Text("先完成权限设置") },
            text = {
                Text(
                    "为保证剪贴板能自动同步，应用需要开启无障碍服务，" +
                        "这样才能在您准备粘贴时触发同步最新内容。我们只用它感知输入法弹出，" +
                        "不会读取屏幕文字；如果不打开，应用只能手动刷新。" +
                        "另外也建议按页面说明关闭电池优化，避免后台被系统回收。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.setHasSeenPermissionGuide(true)
                        showPermissionGuide = false
                        onNavigateToPermissions()
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        prefs.setHasSeenPermissionGuide(true)
                        showPermissionGuide = false
                    }
                ) {
                    Text("稍后再说")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appName)
                        if (username.isNotBlank()) {
                            Text(
                                text = "当前用户：$username",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToPermissions) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "权限设置"
                        )
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "退出登录"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!isLoading) viewModel.refresh() },
                modifier = Modifier.alpha(if (isLoading) 0.38f else 1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新并同步到剪贴板")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newClipText,
                    onValueChange = { newClipText = it },
                    label = { Text("新剪贴内容") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        viewModel.postClip(newClipText)
                        newClipText = ""
                    },
                    enabled = newClipText.isNotBlank() && !isLoading
                ) {
                    Text("提交")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && clips.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clips, key = { it.id }) { clip ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.copyTextToClipboard(clip.text) }
                            ) {
                                Text(
                                    text = clip.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = clip.createdAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { viewModel.deleteClip(clip.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
