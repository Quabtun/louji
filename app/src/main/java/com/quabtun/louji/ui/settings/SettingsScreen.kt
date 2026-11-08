package com.quabtun.louji.ui.settings

import android.app.Activity
import android.app.KeyguardManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.ImportMode
import com.quabtun.louji.model.ThemeMode
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.update.UpdateUiState
import com.quabtun.louji.ui.update.UpdateViewModel
import com.quabtun.louji.util.backupFileName
import com.quabtun.louji.util.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel,
    onBack: (() -> Unit)?,
    onOpenGitHub: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lockDesired by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showCsvDialog by remember { mutableStateOf(false) }
    var csvDesensitised by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.export(it, exportPassword.ifBlank { null }) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importPassword = ""
            pendingImportUri = uri
        }
    }
    val diagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let(viewModel::exportDiagnostics)
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { viewModel.exportCsv(it, csvDesensitised) }
    }
    val lockConfirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.setAppLockEnabled(lockDesired)
    }

    @Suppress("DEPRECATION")
    fun toggleAppLock() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardSecure) {
            scope.launch { snackbarHostState.showSnackbar("请先在系统设置中开启锁屏密码或指纹") }
            return
        }
        lockDesired = !settings.appLockEnabled
        val title = if (lockDesired) "开启应用锁" else "关闭应用锁"
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            keyguard.createConfirmDeviceCredentialIntent(title, "请验证系统锁屏以继续")
        } else {
            @Suppress("DEPRECATION")
            keyguard.createConfirmDeviceCredentialIntent(title, null)
        }
        if (intent != null) {
            runCatching { lockConfirmLauncher.launch(intent) }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法启动系统验证，请重试") }
            }
        }
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
                title = { Text("我的", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
            ) {
                SectionHeader("外观主题")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    SettingsRow(
                        title = "主题",
                        subtitle = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        onClick = { showThemeDialog = true },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("隐私安全")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("应用锁") },
                        supportingContent = { Text("离开应用后自动锁定，需系统锁屏密码或指纹") },
                        trailingContent = {
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { toggleAppLock() },
                                modifier = Modifier.testTag("app_lock_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("数据备份与恢复")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("每周备份提醒") },
                        supportingContent = { Text("超过一周未导出备份时，启动后提醒一次") },
                        trailingContent = {
                            Switch(
                                checked = settings.backupReminderEnabled,
                                onCheckedChange = viewModel::setBackupReminderEnabled,
                                modifier = Modifier.testTag("backup_reminder_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "导出备份",
                        subtitle = "打包记录和全部附件为 zip；可设密码 AES-256 加密",
                        icon = R.drawable.ic_export,
                        onClick = { showExportPasswordDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "导入备份",
                        subtitle = "从 zip 恢复；可选择合并或覆盖，兼容旧版备份",
                        icon = R.drawable.ic_backup,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream"),
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "合并重复记录",
                        subtitle = "把同一位置、同一订单的重复记录合成一条",
                        icon = R.drawable.ic_merge,
                        modifier = Modifier.testTag("settings_merge"),
                        onClick = { viewModel.checkDuplicates() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "导出 CSV 台账",
                        subtitle = "Excel 可直接打开；可选隐私脱敏导出",
                        icon = R.drawable.ic_chart,
                        onClick = { showCsvDialog = true },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("效率工具")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    SettingsRow(
                        title = "媒体占用",
                        subtitle = storage?.let {
                            "${formatBytes(it.totalBytes)} · ${it.itemCount} 个文件"
                        } ?: "正在计算…",
                        icon = R.drawable.ic_gallery,
                        onClick = { viewModel.refreshStorage() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "导出诊断日志",
                        subtitle = "本地脱敏运行日志，不含客户信息",
                        icon = R.drawable.ic_info,
                        onClick = {
                            diagnosticsLauncher.launch(
                                "louji-diagnostics-${System.currentTimeMillis()}.txt",
                            )
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("版本更新与关于")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("当前版本") },
                        supportingContent = {
                            Text(
                                updateViewModel.currentVersion,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "GitHub 仓库",
                        subtitle = "Quabtun/louji · 源码与版本发布",
                        icon = R.drawable.ic_link,
                        onClick = onOpenGitHub,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "隐私说明",
                        subtitle = "记录只存本机；联网用于更新和可选定位",
                        icon = R.drawable.ic_info,
                        onClick = { showPrivacyDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "清除所有记录",
                        subtitle = "删除本机全部记录和附件，无法恢复",
                        icon = R.drawable.ic_delete,
                        danger = true,
                        onClick = { showClearDialog = true },
                    )
                }

                Spacer(Modifier.height(16.dp))
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
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
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

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("导出 CSV 台账") },
            text = {
                Column {
                    Text("用 Excel / WPS 可直接打开。")
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            showCsvDialog = false
                            csvDesensitised = false
                            csvLauncher.launch("louji-${System.currentTimeMillis()}.csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("完整导出") }
                    TextButton(
                        onClick = {
                            showCsvDialog = false
                            csvDesensitised = true
                            csvLauncher.launch("louji-safe-${System.currentTimeMillis()}.csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("隐私脱敏导出（隐藏订单号与尾号前两位）") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCsvDialog = false }) { Text("取消") } },
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("导入备份") },
            text = {
                Column {
                    Text("合并：保留现有记录，跳过相同记录。\n覆盖：先删除本机现有记录和附件，再导入备份。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        modifier = Modifier.fillMaxWidth().testTag("import_password"),
                        label = { Text("备份密码（加密备份才需要）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        pendingImportUri = null
                        uri?.let {
                            viewModel.import(it, ImportMode.MERGE, importPassword.ifBlank { null })
                        }
                    },
                ) { Text("合并导入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        pendingImportUri = null
                        uri?.let {
                            viewModel.import(it, ImportMode.OVERWRITE, importPassword.ifBlank { null })
                        }
                    },
                ) { Text("覆盖导入", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = false
                exportPassword = ""
            },
            title = { Text("导出备份") },
            text = {
                Column {
                    Text("留空则导出明文 zip；填写密码则用 AES-256 加密，导入时需输入相同密码。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        modifier = Modifier.fillMaxWidth().testTag("export_password"),
                        label = { Text("备份密码（可选）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportPasswordDialog = false
                        exportLauncher.launch(backupFileName())
                    },
                ) { Text("导出") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportPasswordDialog = false
                        exportPassword = ""
                    },
                ) { Text("取消") }
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

    duplicates?.let { groups ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicates() },
            title = { Text("重复记录") },
            text = {
                Column {
                    if (groups.isEmpty()) {
                        Text("没有发现重复记录。")
                    } else {
                        Text("发现 ${groups.size} 组重复，合并后保留信息最全的一条，其余移入回收站：")
                        Spacer(Modifier.height(8.dp))
                        groups.take(6).forEach { group ->
                            val item = group.first().record
                            Text(
                                "· ${item.location}（${group.size} 条）",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (groups.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.mergeDuplicates() },
                        modifier = Modifier.testTag("merge_duplicates"),
                    ) { Text("合并") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicates() }) { Text("关闭") }
            },
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
                        "除此之外不上传任何记录。导出的备份和 CSV 包含客户信息，请自行妥善保管。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .semantics(mergeDescendants = false) { },
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: Int? = null,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
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
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
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
        modifier = modifier.clickable(onClick = onClick),
    )
}
