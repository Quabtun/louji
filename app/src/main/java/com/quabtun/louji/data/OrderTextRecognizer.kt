package com.quabtun.louji.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs on-device OCR over an order screenshot. Uses the bundled Chinese model (offline, no
 * download) which also recognizes Latin and digits; if it fails to initialize it falls back to
 * the smaller bundled Latin model.
 */
class OrderTextRecognizer(private val context: Context) {
    suspend fun recognize(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = try {
            InputImage.fromFilePath(context, uri)
        } catch (_: Throwable) {
            return@withContext ""
        }
        return@withContext try {
            recognize(TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()), input)
        } catch (_: Throwable) {
            recognize(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS), input)
        }
    }

    private fun recognize(recognizer: TextRecognizer, input: InputImage): String {
        return try {
            val result = Tasks.await(recognizer.process(input), RECOGNITION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result.text
        } catch (_: Throwable) {
            ""
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val RECOGNITION_TIMEOUT_SECONDS = 15L
    }
}
