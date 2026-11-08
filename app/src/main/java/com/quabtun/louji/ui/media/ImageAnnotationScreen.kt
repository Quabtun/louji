package com.quabtun.louji.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AnnotateTool { BRUSH, ARROW, CIRCLE, TEXT }

private sealed interface Annotation {
    data class Stroke(
        val points: List<Offset>,
        val color: Color,
        val width: Float,
    ) : Annotation

    data class Arrow(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val width: Float,
    ) : Annotation

    data class Circle(
        val center: Offset,
        val radius: Float,
        val color: Color,
        val width: Float,
    ) : Annotation

    data class Label(
        val position: Offset,
        val text: String,
        val color: Color,
        val width: Float,
    ) : Annotation
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnnotationScreen(
    viewModel: AnnotationViewModel,
    attachmentId: Long,
    onClose: () -> Unit,
) {
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val annotations = remember { mutableStateListOf<Annotation>() }
    var tool by remember { mutableStateOf(AnnotateTool.BRUSH) }
    var color by remember { mutableStateOf(Color(0xFFFFB800)) }
    var width by remember { mutableStateOf(10f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var arrowStart by remember { mutableStateOf<Offset?>(null) }
    var arrowEnd by remember { mutableStateOf<Offset?>(null) }
    var circleCenter by remember { mutableStateOf<Offset?>(null) }
    var circleRadius by remember { mutableStateOf(0f) }
    var showDoneConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(attachmentId) { viewModel.load(attachmentId) }
    LaunchedEffect(message) {
        if (message != null) {
            viewModel.clearMessage()
            onClose()
        }
    }

    val file = attachment?.takeIf { it.type == com.quabtun.louji.model.MediaType.IMAGE }?.let {
        File(it.filePath).takeIf { f -> f.isFile }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0E14))) {
        if (file == null) {
            if (attachment == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Text(
                    "这条附件不是图片，无法标注",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭标注", tint = Color.White)
                    }
                    Text(
                        text = "涂鸦标注",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { if (annotations.isNotEmpty()) annotations.removeAt(annotations.lastIndex) }) {
                        Text("撤销", color = Color.White)
                    }
                    TextButton(onClick = { annotations.clear() }) {
                        Text("清空", color = Color.White)
                    }
                    Button(
                        onClick = { showDoneConfirm = true },
                        enabled = !saving,
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("完成") }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(tool, color, width) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    when (tool) {
                                        AnnotateTool.BRUSH -> currentStroke = listOf(offset)
                                        AnnotateTool.ARROW -> {
                                            arrowStart = offset
                                            arrowEnd = offset
                                        }
                                        AnnotateTool.CIRCLE -> {
                                            circleCenter = offset
                                            circleRadius = 0f
                                        }
                                        AnnotateTool.TEXT -> Unit
                                    }
                                },
                                onDrag = { change, _ ->
                                    val point = change.position
                                    when (tool) {
                                        AnnotateTool.BRUSH -> {
                                            currentStroke = currentStroke + point
                                        }
                                        AnnotateTool.ARROW -> arrowEnd = point
                                        AnnotateTool.CIRCLE -> {
                                            val center = circleCenter
                                            if (center != null) {
                                                circleRadius = hypot(
                                                    point.x - center.x,
                                                    point.y - center.y,
                                                )
                                            }
                                        }
                                        AnnotateTool.TEXT -> Unit
                                    }
                                },
                                onDragEnd = {
                                    when (tool) {
                                        AnnotateTool.BRUSH -> {
                                            if (currentStroke.size > 1) {
                                                annotations += Annotation.Stroke(
                                                    points = currentStroke,
                                                    color = color,
                                                    width = width,
                                                )
                                            }
                                            currentStroke = emptyList()
                                        }
                                        AnnotateTool.ARROW -> {
                                            val start = arrowStart
                                            val end = arrowEnd
                                            if (start != null && end != null &&
                                                hypot(end.x - start.x, end.y - start.y) > 12f
                                            ) {
                                                annotations += Annotation.Arrow(
                                                    start = start,
                                                    end = end,
                                                    color = color,
                                                    width = width,
                                                )
                                            }
                                            arrowStart = null
                                            arrowEnd = null
                                        }
                                        AnnotateTool.CIRCLE -> {
                                            val center = circleCenter
                                            if (center != null && circleRadius > 8f) {
                                                annotations += Annotation.Circle(
                                                    center = center,
                                                    radius = circleRadius,
                                                    color = color,
                                                    width = width,
                                                )
                                            }
                                            circleCenter = null
                                            circleRadius = 0f
                                        }
                                        AnnotateTool.TEXT -> Unit
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = file,
                        contentDescription = "待标注图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("annotation_canvas"),
                    ) {
                        annotations.forEach { drawAnnotation(it) }
                        if (currentStroke.size > 1) {
                            drawAnnotation(
                                Annotation.Stroke(currentStroke, color, width),
                            )
                        }
                        arrowStart?.let { start ->
                            arrowEnd?.let { end ->
                                drawAnnotation(Annotation.Arrow(start, end, color, width))
                            }
                        }
                        circleCenter?.let { center ->
                            if (circleRadius > 0f) {
                                drawAnnotation(
                                    Annotation.Circle(center, circleRadius, color, width),
                                )
                            }
                        }
                    }
                }

                // Tool bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141923))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("画笔", AnnotateTool.BRUSH, tool) { tool = it }
                        ToolButton("箭头", AnnotateTool.ARROW, tool) { tool = it }
                        ToolButton("圈选", AnnotateTool.CIRCLE, tool) { tool = it }
                        ToolButton("文字", AnnotateTool.TEXT, tool) { tool = it }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(
                            Color(0xFFFFB800),
                            Color(0xFFEF4444),
                            Color(0xFF10B981),
                        ).forEach { candidate ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(if (color == candidate) 32.dp else 26.dp)
                                    .background(candidate, CircleShape),
                            ) {
                                if (color == candidate) {
                                    Text(
                                        "✓",
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Box(modifier = Modifier.size(0.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        listOf(6f, 10f, 16f).forEach { candidate ->
                            TextButton(onClick = { width = candidate }) {
                                Text(
                                    text = if (width == candidate) "●" else "○",
                                    color = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (tool == AnnotateTool.TEXT) {
                            Button(onClick = { showTextDialog = true }) { Text("加文字") }
                        }
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("添加文字标签") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it.take(12) },
                    label = { Text("如：按这个铃") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textDraft.isNotBlank()) {
                            annotations += Annotation.Label(
                                position = Offset(
                                    canvasSize.width / 2f,
                                    canvasSize.height / 2f,
                                ),
                                text = textDraft.trim(),
                                color = color,
                                width = width,
                            )
                        }
                        textDraft = ""
                        showTextDialog = false
                    },
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("取消") }
            },
        )
    }

    if (showDoneConfirm && file != null && attachment != null) {
        AlertDialog(
            onDismissRequest = { showDoneConfirm = false },
            title = { Text("保存标注图？") },
            text = { Text("会把标注后的图片作为新附件加到这条记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDoneConfirm = false
                        val target = attachment!!
                        scope.launch {
                            val output = withContext(Dispatchers.IO) {
                                runCatching {
                                    renderAnnotated(
                                        source = file,
                                        annotations = annotations.toList(),
                                        canvasSize = canvasSize,
                                        outDir = File(context.filesDir, "media"),
                                    )
                                }.getOrNull()
                            }
                            if (output == null) {
                                onClose()
                            } else {
                                viewModel.saveAnnotated(target.recordId, output) { onClose() }
                            }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDoneConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    value: AnnotateTool,
    selected: AnnotateTool,
    onSelect: (AnnotateTool) -> Unit,
) {
    val isSelected = value == selected
    TextButton(
        onClick = { onSelect(value) },
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFFFFB800) else Color(0xFF1D2432),
                RoundedCornerShape(10.dp),
            ),
    ) {
        Text(label, color = if (isSelected) Color(0xFF111827) else Color.White)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(annotation: Annotation) {
    when (annotation) {
        is Annotation.Stroke -> {
            if (annotation.points.size > 1) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(annotation.points.first().x, annotation.points.first().y)
                    annotation.points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = annotation.color,
                    style = Stroke(width = annotation.width, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        is Annotation.Arrow -> {
            drawLine(
                color = annotation.color,
                start = annotation.start,
                end = annotation.end,
                strokeWidth = annotation.width,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            val angle = kotlin.math.atan2(
                annotation.end.y - annotation.start.y,
                annotation.end.x - annotation.start.x,
            )
            val head = (annotation.width * 3.2f).coerceAtLeast(24f)
            listOf(angle + 2.6f, angle - 2.6f).forEach { direction ->
                drawLine(
                    color = annotation.color,
                    start = annotation.end,
                    end = Offset(
                        annotation.end.x + head * kotlin.math.cos(direction),
                        annotation.end.y + head * kotlin.math.sin(direction),
                    ),
                    strokeWidth = annotation.width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        is Annotation.Circle -> {
            drawCircle(
                color = annotation.color,
                radius = annotation.radius,
                center = annotation.center,
                style = Stroke(width = annotation.width),
            )
        }
        is Annotation.Label -> {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.7f),
                topLeft = Offset(
                    annotation.position.x - annotation.text.length * 14f - 10f,
                    annotation.position.y - 46f,
                ),
                size = Size(annotation.text.length * 28f + 20f, 62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                annotation.text,
                annotation.position.x - annotation.text.length * 14f,
                annotation.position.y,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 38f
                    color = AndroidColor.parseColor("#FFB800")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                },
            )
        }
    }
}

/**
 * Bakes the annotations onto the source bitmap. Coordinates are captured in the on-screen canvas
 * space and uniformly scaled to the (larger) decoded bitmap.
 */
private fun renderAnnotated(
    source: File,
    annotations: List<Annotation>,
    canvasSize: IntSize,
    outDir: File,
): File {
    val decoded = decodeRotated(source) ?: error("无法读取图片")
    val maxSide = 1800
    val scaleDown = minOf(1f, maxSide / maxOf(decoded.width, decoded.height).toFloat())
    val bitmap = if (scaleDown < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scaleDown).toInt(),
            (decoded.height * scaleDown).toInt(),
            true,
        ).also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }

    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    canvas.drawBitmap(bitmap, 0f, 0f, null)

    // Account for ContentScale.Fit letterboxing.
    val viewW = canvasSize.width.toFloat().coerceAtLeast(1f)
    val viewH = canvasSize.height.toFloat().coerceAtLeast(1f)
    val fit = minOf(viewW / bitmap.width, viewH / bitmap.height)
    val offsetX = (viewW - bitmap.width * fit) / 2f
    val offsetY = (viewH - bitmap.height * fit) / 2f
    fun map(point: Offset): Offset =
        Offset((point.x - offsetX) / fit, (point.y - offsetY) / fit)

    annotations.forEach { annotation ->
        when (annotation) {
            is Annotation.Stroke -> {
                if (annotation.points.size > 1) {
                    val path = Path().apply {
                        val first = map(annotation.points.first())
                        moveTo(first.x, first.y)
                        annotation.points.drop(1).forEach {
                            val p = map(it)
                            lineTo(p.x, p.y)
                        }
                    }
                    canvas.drawPath(
                        path,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = annotation.width / fit
                            color = annotation.color.toArgbCompat()
                        },
                    )
                }
            }
            is Annotation.Arrow -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = annotation.width / fit
                    color = annotation.color.toArgbCompat()
                }
                val start = map(annotation.start)
                val end = map(annotation.end)
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                val head = (annotation.width / fit) * 3.2f
                listOf(angle + 2.6f, angle - 2.6f).forEach { direction ->
                    canvas.drawLine(
                        end.x,
                        end.y,
                        end.x + head * kotlin.math.cos(direction),
                        end.y + head * kotlin.math.sin(direction),
                        paint,
                    )
                }
            }
            is Annotation.Circle -> {
                val center = map(annotation.center)
                canvas.drawCircle(
                    center.x,
                    center.y,
                    annotation.radius / fit,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = annotation.width / fit
                        color = annotation.color.toArgbCompat()
                    },
                )
            }
            is Annotation.Label -> {
                val position = map(annotation.position)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 38f / fit
                    color = AndroidColor.parseColor("#FFB800")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val textWidth = textPaint.measureText(annotation.text)
                canvas.drawRoundRect(
                    position.x - textWidth / 2f - 16f,
                    position.y - textPaint.textSize - 10f,
                    position.x + textWidth / 2f + 16f,
                    position.y + 14f,
                    12f,
                    12f,
                    Paint().apply { color = AndroidColor.argb(180, 0, 0, 0) },
                )
                canvas.drawText(annotation.text, position.x - textWidth / 2f, position.y, textPaint)
            }
        }
    }

    outDir.mkdirs()
    val target = File(outDir, "annotated-${System.currentTimeMillis()}.jpg")
    FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    bitmap.recycle()
    out.recycle()
    return target
}

private fun Color.toArgbCompat(): Int = AndroidColor.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun decodeRotated(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 2400) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val degrees = runCatching {
        when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}
