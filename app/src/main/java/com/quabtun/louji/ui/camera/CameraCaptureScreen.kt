package com.quabtun.louji.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.quabtun.louji.R
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.util.formatDuration
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_VIDEO_MS = 2 * 60 * 1000L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraCaptureScreen(
    mediaStore: LocalMediaStore,
    initialVideo: Boolean,
    onClose: () -> Unit,
    onCaptured: (com.quabtun.louji.model.MediaDraft) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var videoMode by remember { mutableStateOf(initialVideo) }
    var useFront by remember { mutableStateOf(false) }
    var frontAvailable by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0L) }
    var captureBusy by remember { mutableStateOf(false) }
    var flashOverlay by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discardVideo by remember { mutableStateOf(false) }
    val alive = remember { mutableStateOf(true) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) error = "没有麦克风权限，将录制无声视频"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            alive.value = false
            runCatching { recording?.close() }
            recording = null
            runCatching { cameraProvider?.unbindAll() }
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(hasCameraPermission, useFront) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            Log.d("LouJiCamera", "binding camera, permission=$hasCameraPermission")
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get(20, TimeUnit.SECONDS)
            }
            Log.d("LouJiCamera", "provider ready")
            withContext(Dispatchers.Main.immediate) {
                cameraProvider = provider
                frontAvailable = runCatching {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                }.getOrDefault(false)
                if (useFront && !frontAvailable) {
                    useFront = false
                    return@withContext
                }
                val selector = if (useFront) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                        ),
                    )
                    .build()
                val video = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture,
                    video,
                )
                imageCapture = capture
                videoCapture = video
                camera = boundCamera
                error = null
                Log.d("LouJiCamera", "camera bound")
            }
        } catch (throwable: Throwable) {
            Log.e("LouJiCamera", "camera binding failed", throwable)
            error = "相机不可用：${throwable.message ?: "初始化失败"}"
        }
    }

    LaunchedEffect(flashOn, imageCapture, camera) {
        imageCapture?.flashMode = if (flashOn) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        runCatching { camera?.cameraControl?.enableTorch(flashOn && videoMode) }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingSeconds = 0
            return@LaunchedEffect
        }
        while (isRecording) {
            delay(200)
            recordingSeconds += 200
            if (recordingSeconds >= MAX_VIDEO_MS) {
                runCatching { recording?.stop() }
                break
            }
        }
    }

    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        delay(4_000)
        error = null
    }

    val requestClose: () -> Unit = {
        val activeRecording = recording
        if (activeRecording != null) {
            discardVideo = true
            runCatching { activeRecording.stop() }
        }
        onClose()
    }

    BackHandler { requestClose() }

    if (!hasCameraPermission) {
        CameraPermissionGate(
            onRequest = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                )
            },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onClose = requestClose,
        )
        return
    }

    val capturePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            error = "相机还没准备好，请稍等"
        } else if (!captureBusy) {
            captureBusy = true
            val file = mediaStore.newPhotoFile()
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(
                options,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!alive.value) {
                            file.delete()
                            return
                        }
                        flashOverlay = true
                        scope.launch {
                            delay(120)
                            flashOverlay = false
                        }
                        onCaptured(mediaStore.completePhoto(file))
                        captureBusy = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        captureBusy = false
                        error = "拍照失败：${exception.message ?: "请重试"}"
                    }
                },
            )
        }
    }

    val toggleRecording: () -> Unit = {
        if (isRecording) {
            runCatching { recording?.stop() }
        } else if (recording == null) {
            val video = videoCapture
            val recorder = video?.output
            if (video == null || recorder == null) {
                error = "相机还没准备好，请稍等"
            } else {
                val file = mediaStore.newVideoFile()
                discardVideo = false
                val pending = recorder.prepareRecording(
                    context,
                    FileOutputOptions.Builder(file).build(),
                )
                val prepared = if (hasAudioPermission) pending.withAudioEnabled() else pending
                val newRecording = prepared.start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            recordingSeconds = 0
                        }

                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            recording = null
                            val discard = discardVideo
                            discardVideo = false
                            if (event.hasError() || discard) {
                                file.delete()
                                if (!discard) {
                                    error = "录像失败：${event.error}"
                                }
                            } else if (!alive.value) {
                                file.delete()
                            } else {
                                scope.launch {
                                    try {
                                        val draft = withContext(Dispatchers.IO) {
                                            mediaStore.completeVideo(file)
                                        }
                                        onCaptured(draft)
                                    } catch (throwable: Throwable) {
                                        file.delete()
                                        error = "视频处理失败：${throwable.message ?: "请重试"}"
                                    }
                                }
                            }
                        }
                    }
                }
                recording = newRecording
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).semantics { testTagsAsResourceId = true }) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (flashOverlay) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        }
        if (imageCapture == null) {
            Text(
                text = "正在准备相机…",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("camera_ready"),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCircleButton(
                onClick = requestClose,
                description = "关闭相机",
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            CameraCircleButton(
                onClick = { flashOn = !flashOn },
                description = if (flashOn) "关闭闪光灯" else "打开闪光灯",
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flash),
                    contentDescription = null,
                    tint = if (flashOn) Color(0xFFFFC83D) else Color.White,
                )
            }
            if (frontAvailable) {
                Spacer(Modifier.width(10.dp))
                CameraCircleButton(
                    onClick = { if (!isRecording) useFront = !useFront },
                    description = "切换前后摄像头",
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_switch_camera),
                        contentDescription = null,
                        tint = if (isRecording) Color.White.copy(alpha = 0.4f) else Color.White,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error != null) {
                Text(
                    text = error!!,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = when {
                    isRecording -> "录像中  ${formatDuration(recordingSeconds)}"
                    videoMode && !hasAudioPermission -> "录像最长 2 分钟 · 无声"
                    videoMode -> "录像最长 2 分钟"
                    else -> "拍照"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
            ) {
                CameraModePill(
                    label = "拍照",
                    selected = !videoMode,
                    enabled = !isRecording && recording == null,
                    onClick = { videoMode = false },
                )
                CameraModePill(
                    label = "录像",
                    selected = videoMode,
                    enabled = !isRecording && recording == null,
                    onClick = {
                        videoMode = true
                        if (!hasAudioPermission) {
                            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(
                        enabled = !captureBusy && imageCapture != null,
                        role = Role.Button,
                        onClick = { if (videoMode) toggleRecording() else capturePhoto() },
                    )
                    .semantics {
                        contentDescription = when {
                            isRecording -> "停止录像"
                            videoMode -> "开始录像"
                            else -> "拍照"
                        }
                    }
                    .testTag("camera_shutter"),
                contentAlignment = Alignment.Center,
            ) {
                if (captureBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 30.dp else if (videoMode) 56.dp else 58.dp)
                            .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                            .background(if (videoMode) Color(0xFFE45757) else Color.White),
                    )
                }
            }
            if (isRecording) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "点按停止",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun CameraCircleButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.38f), CircleShape)
            .semantics { contentDescription = description },
    ) {
        content()
    }
}

@Composable
private fun CameraModePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
private fun CameraPermissionGate(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = Color.White,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "需要相机权限",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "拍照和录像只保存在本机，不会上传。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(22.dp))
            TextButton(onClick = onRequest) {
                Text("允许使用相机", color = Color(0xFFFFC83D))
            }
            TextButton(onClick = onOpenSettings) {
                Text("去系统设置", color = Color.White.copy(alpha = 0.8f))
            }
            TextButton(onClick = onClose) {
                Text("返回", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
