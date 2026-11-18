package com.quabtun.louji.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of one OCR pass. [errorReason] is a short, sanitised technical reason ("识别超时",
 * "无法读取图片文件") and never contains image bytes or recognised text.
 */
data class OcrResult(
    val rawText: String = "",
    val visualLines: List<String> = emptyList(),
    val engineUsed: String = ENGINE_NONE,
    val errorReason: String? = null,
) {
    val isEmpty: Boolean get() = rawText.isBlank()

    /** True when OCR produced nothing usable — callers should fall back to manual entry. */
    val failed: Boolean get() = errorReason != null || rawText.isBlank()

    companion object {
        const val ENGINE_NONE = "none"
        const val ENGINE_CHINESE = "mlkit-chinese"
        const val ENGINE_LATIN = "mlkit-latin"
    }
}

/**
 * Plans OCR tiles for a tall screenshot: overlapping vertical slices so glyphs cut by a tile
 * boundary still appear whole in at least one slice. Pure so the tiling rule is unit-testable.
 */
fun planOcrTiles(
    totalHeight: Int,
    tileHeight: Int = 1_600,
    overlap: Int = 140,
    maxTiles: Int = 8,
    singlePageLimit: Int = 2_400,
    minTileHeight: Int = 240,
): List<Pair<Int, Int>> {
    if (totalHeight <= 0) return emptyList()
    if (totalHeight <= singlePageLimit) return listOf(0 to totalHeight)
    val tiles = mutableListOf<Pair<Int, Int>>()
    var top = 0
    var capped = false
    while (top < totalHeight) {
        if (tiles.size >= maxTiles) {
            capped = true
            break
        }
        val height = minOf(tileHeight, totalHeight - top)
        if (height < minTileHeight && tiles.isNotEmpty()) break
        tiles += top to height
        if (top + height >= totalHeight) break
        top += tileHeight - overlap
    }
    // A short tail is folded into the previous tile instead of being dropped.
    if (!capped && tiles.isNotEmpty()) {
        val last = tiles.last()
        if (last.first + last.second < totalHeight) {
            tiles[tiles.lastIndex] = last.first to (totalHeight - last.first)
        }
    }
    return tiles
}

/**
 * One OCR line with its box, in image coordinates. Kept public for the pure line-composer tests.
 */
data class OcrLineBox(
    val text: String,
    val left: Int,
    val centerY: Int,
    val height: Int,
)

/**
 * Rebuilds reading order from OCR line boxes. Boxes sharing a baseline (e.g. "12栋" and "1804室"
 * detected as separate blocks) are joined left-to-right with a single space; duplicate rows from
 * overlapping tiles are dropped.
 */
object OcrLineComposer {
    fun compose(boxes: List<OcrLineBox>): List<String> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedWith(compareBy({ it.centerY }, { it.left }))
        val rows = mutableListOf<MutableList<OcrLineBox>>()
        for (box in sorted) {
            val row = rows.lastOrNull()
            val tolerance = if (row == null) {
                0
            } else {
                (row.map { it.height }.average() * 0.55).toInt().coerceAtLeast(4)
            }
            val sameRow = row != null && kotlin.math.abs(
                row.map { it.centerY }.average().toInt() - box.centerY,
            ) <= tolerance
            if (sameRow) row!!.add(box) else rows += mutableListOf(box)
        }

        val output = mutableListOf<String>()
        rows.forEach { row ->
            val seen = HashSet<String>()
            val text = row.sortedBy { it.left }
                .map { it.text }
                .filter { seen.add(it) }
                .joinToString(" ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            if (text.isEmpty()) return@forEach
            if (output.lastOrNull() == text) return@forEach
            output += text
        }
        return output
    }
}

/**
 * On-device OCR for order screenshots. The bundled Chinese model (offline, no download) also
 * reads Latin and digits; if it fails to initialise or times out, the smaller bundled Latin model
 * is actually retried instead of silently returning an empty string.
 *
 * Screenshots are decoded through [android.content.ContentResolver] (not
 * [InputImage.fromFilePath]) so EXIF rotation and huge scrolling captures can be handled safely:
 * the bitmap is bounded, rotated upright, and very tall captures are recognised in overlapping
 * tiles. Recognised lines are then re-grouped by their `boundingBox` so a row that OCR splits into
 * separate blocks is emitted in left-to-right reading order.
 */
class OrderTextRecognizer(
    private val context: Context,
    private val diagnostics: DiagnosticLog? = null,
) {
    private val chineseClient: TextRecognizer? by lazy {
        runCatching {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }.onFailure { record("chinese_init", it) }.getOrNull()
    }
    private val latinClient: TextRecognizer? by lazy {
        runCatching {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }.onFailure { record("latin_init", it) }.getOrNull()
    }
    private val chineseBroken = AtomicBoolean(false)
    private val firstRun = AtomicBoolean(true)

    /** Legacy entry point: raw text only. */
    suspend fun recognize(uri: Uri): String = analyze(uri).rawText

    suspend fun analyze(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val pages = try {
            loadPages(uri)
        } catch (error: Throwable) {
            record("decode", error)
            return@withContext OcrResult(errorReason = reasonFor(error))
        }
        if (pages.isEmpty()) {
            return@withContext OcrResult(errorReason = "无法读取图片文件")
        }

        val lineBoxes = mutableListOf<OcrLineBox>()
        var engineUsed = OcrResult.ENGINE_NONE
        var lastError: String? = null
        for (page in pages) {
            val pass = recognizePage(page)
            if (pass.engine != OcrResult.ENGINE_NONE) engineUsed = pass.engine
            if (pass.error != null) lastError = pass.error
            lineBoxes += pass.lines
        }
        pages.forEach { it.bitmap.recycle() }

        val visualLines = rebuildVisualLines(lineBoxes)
        if (visualLines.isEmpty() && lastError == null) lastError = "没识别到文字"
        OcrResult(
            rawText = visualLines.joinToString("\n"),
            visualLines = visualLines,
            engineUsed = engineUsed,
            errorReason = if (visualLines.isEmpty()) lastError else null,
        )
    }

    // ---------------------------------------------------------------- decoding

    private data class Page(val bitmap: Bitmap, val yOffset: Int)

    private fun loadPages(uri: Uri): List<Page> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("open failed")
        boundsStream.use {
            // With inJustDecodeBounds the decode intentionally returns null; only the bounds
            // out-params matter, so readability is proven by the stream above.
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("empty image")
        }

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decoded)
        } ?: throw IOException("decode failed")

        bitmap = rotateForExif(uri, bitmap)

        // Very tall scrolling screenshots: keep small text legible by tiling instead of shrinking.
        val plan = planOcrTiles(bitmap.height)
        if (plan.size <= 1) {
            return listOf(Page(bitmap, 0))
        }
        val tiles = plan.map { (top, height) ->
            Page(Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height), top)
        }
        bitmap.recycle()
        return tiles
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_WIDTH) sample *= 2
        // Guard against absurd pixel counts from very long captures.
        while ((width / sample).toLong() * (height / sample) > MAX_DECODE_PIXELS) {
            sample *= 2
        }
        return sample
    }

    private fun rotateForExif(uri: Uri, source: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                .also { if (it !== source) source.recycle() }
        }.getOrDefault(source)
    }

    // ------------------------------------------------------------ recognition

    private class PassResult(
        val lines: List<OcrLineBox>,
        val engine: String,
        val error: String?,
    )

    private fun recognizePage(page: Page): PassResult {
        val input = InputImage.fromBitmap(page.bitmap, 0)
        var error: String? = null
        var engine = OcrResult.ENGINE_NONE
        var lines: List<OcrLineBox> = emptyList()

        if (!chineseBroken.get()) {
            val client = chineseClient
            if (client != null) {
                val outcome = runRecognizer(client, input, page.yOffset)
                if (outcome.error == null) {
                    engine = OcrResult.ENGINE_CHINESE
                    lines = outcome.lines
                } else {
                    error = outcome.error
                    record("chinese_pass", outcome.throwable)
                    // A recogniser that keeps failing would cost a timeout on every screenshot.
                    if (outcome.fatal) {
                        chineseBroken.set(true)
                        runCatching { client.close() }
                    }
                }
            } else {
                chineseBroken.set(true)
            }
        }
        if (lines.isEmpty()) {
            val latin = latinClient
            if (latin != null) {
                val outcome = runRecognizer(latin, input, page.yOffset)
                if (outcome.error == null) {
                    engine = OcrResult.ENGINE_LATIN
                    lines = outcome.lines
                    error = null
                } else if (error == null) {
                    error = outcome.error
                }
            }
        }
        return PassResult(lines = lines, engine = engine, error = error)
    }

    private class Outcome(
        val lines: List<OcrLineBox>,
        val error: String? = null,
        val throwable: Throwable? = null,
        val fatal: Boolean = false,
    )

    private fun runRecognizer(
        client: TextRecognizer,
        input: InputImage,
        yOffset: Int,
    ): Outcome = when (val error = runCatchingOnce(client, input, yOffset)) {
        is Recognized -> Outcome(lines = collectLines(error.result, yOffset))
        is Cancelled -> throw error.original
        is Failed -> Outcome(
            lines = emptyList(),
            error = reasonFor(error.original),
            throwable = error.original,
            // Only a genuinely unavailable engine (init/native failure) trips the breaker.
            // A timeout may just be a cold model, and cancellations must never do so.
            fatal = error.original !is TimeoutException && !error.original.isCancellation(),
        )
    }

    private sealed class RecognizeOutcome
    private data class Recognized(val result: Text) : RecognizeOutcome()
    private data class Cancelled(val original: CancellationException) : RecognizeOutcome()
    private data class Failed(val original: Throwable) : RecognizeOutcome()

    private fun runCatchingOnce(
        client: TextRecognizer,
        input: InputImage,
        yOffset: Int,
    ): RecognizeOutcome {
        val timeout = if (firstRun.getAndSet(false)) {
            FIRST_RUN_TIMEOUT_SECONDS
        } else {
            TIMEOUT_SECONDS
        }
        return try {
            Recognized(Tasks.await(client.process(input), timeout, TimeUnit.SECONDS))
        } catch (error: CancellationException) {
            // Coroutine cancellation or a cancelled task: propagate, never disable the engine.
            Cancelled(error)
        } catch (error: InterruptedException) {
            val cancelled = java.util.concurrent.CancellationException(error.message)
            cancelled.initCause(error)
            Cancelled(cancelled)
        } catch (error: Throwable) {
            Failed(error)
        }
    }

    private fun Throwable.isCancellation(): Boolean =
        this is CancellationException || this is InterruptedException

    private fun collectLines(result: Text, yOffset: Int): List<OcrLineBox> {
        val lines = mutableListOf<OcrLineBox>()
        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val text = line.text.trim()
                if (text.isEmpty()) return@forEach
                val box = line.boundingBox ?: return@forEach
                lines += OcrLineBox(
                    text = text,
                    left = box.left,
                    centerY = box.centerY() + yOffset,
                    height = maxOf(box.height(), 1),
                )
            }
        }
        return lines
    }

    // ---------------------------------------------------------- line rebuild

    private fun rebuildVisualLines(boxes: List<OcrLineBox>): List<String> =
        OcrLineComposer.compose(boxes)

    // --------------------------------------------------------------- helpers

    private fun reasonFor(error: Throwable): String = when (error) {
        is TimeoutException -> "识别超时"
        is java.io.FileNotFoundException -> "无法读取图片文件"
        is IOException -> "无法读取图片文件"
        is OutOfMemoryError -> "图片过大"
        else -> "识别失败：${error.javaClass.simpleName}"
    }

    private fun record(tag: String, error: Throwable?) {
        if (error == null) return
        diagnostics?.log(
            "ocr_$tag",
            "${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}",
        )
    }

    fun close() {
        runCatching { chineseClient?.close() }
        runCatching { latinClient?.close() }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val FIRST_RUN_TIMEOUT_SECONDS = 30L
        const val MAX_WIDTH = 1_400
        const val MAX_DECODE_PIXELS = 14_000_000L
    }
}
