package com.quabtun.louji.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.ImportMode
import com.quabtun.louji.model.ThemeMode
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.update.UpdateUiState
import com.quabtun.louji.ui.update.UpdateViewModel
import com.quabtun.louji.util.backupFileName
import com.quabtun.louji.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit,
    onOpenGitHub: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    LaunchedEffect(backup.message) {
        val message = backup.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBackupMessage()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings_screen").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                SectionHeader("外观")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "主题",
                    subtitle = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { showThemeDialog = true },
                )

                Spacer(Modifier.height(22.dp))
                SectionHeader("数据")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "导出备份",
                    subtitle = "打包记录和全部附件为 zip，包含客户信息，请妥善保管",
                    icon = R.drawable.ic_export,
                    onClick = {
                        exportLauncher.launch(backupFileName())
                    },
                )
                SettingsRow(
                    title = "导入备份",
                    subtitle = "从 zip 恢复；可选择合并或覆盖",
                    icon = R.drawable.ic_backup,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                )
                SettingsRow(
                    title = "媒体占用",
                    subtitle = storage?.let {
                        "${formatBytes(it.totalBytes)} · ${it.itemCount} 个文件"
                    } ?: "正在计算…",
                    icon = R.drawable.ic_gallery,
                    onClick = { viewModel.refreshStorage() },
                )
                SettingsRow(
                    title = "清除所有记录",
                    subtitle = "删除本机全部记录和附件，无法恢复",
                    icon = R.drawable.ic_delete,
                    danger = true,
                    onClick = { showClearDialog = true },
                )

                Spacer(Modifier.height(22.dp))
                SectionHeader("更新")
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("当前版本") },
                    supportingContent = {
                        Text(
                            updateViewModel.currentVersion,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("自动检查更新") },
                    supportingContent = { Text("启动时最多每 24 小时检查一次") },
                    trailingContent = {
                        Switch(
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = viewModel::setAutoCheckUpdates,
                            modifier = Modifier.testTag("auto_update_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("检查更新") },
                    supportingContent = {
                        Text(
                            when (updateState) {
                                is UpdateUiState.Checking -> "正在检查…"
                                is UpdateUiState.Downloading -> "正在下载新版本…"
                                else -> "从 GitHub Releases 获取最新版本"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        if (updateState is UpdateUiState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { updateViewModel.checkNow() },
                                modifier = Modifier.testTag("check_update"),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "检查更新")
                            }
                        }
                    },
                )

                Spacer(Modifier.height(22.dp))
                SectionHeader("关于")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "GitHub 仓库",
                    subtitle = "Quabtun/louji · 源码与版本发布",
                    icon = R.drawable.ic_link,
                    onClick = onOpenGitHub,
                )
                SettingsRow(
                    title = "隐私说明",
                    subtitle = "记录只存本机；联网用于更新和可选定位",
                    icon = R.drawable.ic_info,
                    onClick = { showPrivacyDialog = true },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "楼记 v${updateViewModel.currentVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "为骑手做的私人爬楼台账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.themeMode == mode,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = null,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("完成") }
            },
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("导入备份") },
            text = {
                Text(
                    "合并：保留现有记录，跳过相同记录。\n覆盖：先删除本机现有记录和附件，再导入备份。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        pendingImportUri = null
                        uri?.let { viewModel.import(it, ImportMode.MERGE) }
                    },
                ) { Text("合并导入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        pendingImportUri = null
                        uri?.let { viewModel.import(it, ImportMode.OVERWRITE) }
                    },
                ) {
                    Text("覆盖导入", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (backup.running) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在处理备份") },
            text = {
                Column {
                    if (backup.progress != null) {
                        LinearProgressIndicator(
                            progress = { backup.progress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "请不要离开应用，完成后会提示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "清除所有记录？",
            text = "本机全部记录、照片和视频都会被删除，且无法恢复。建议先导出备份。",
            confirmLabel = "全部清除",
            destructive = true,
            onConfirm = {
                showClearDialog = false
                viewModel.clearAllData()
            },
            onDismiss = { showClearDialog = false },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "楼记不要求登录，记录、截图、照片和视频都保存在本机应用私有目录。" +
                        "联网只用于访问 GitHub 检查新版本；点「使用当前位置」时，系统定位或地理编码服务可能会把坐标用于解析地址。" +
                        "除此之外不上传任何记录。导出的备份文件包含客户信息，请自行妥善保管。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: Int? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = icon?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
