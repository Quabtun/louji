package com.quabtun.louji.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs on-device OCR over an order screenshot. Uses the bundled Latin model so it works fully
 * offline; digits and letters (order number, phone tail) are the reliable target, while CJK text
 * needs the larger `text-recognition-chinese` model as a follow-up.
 */
class OrderTextRecognizer(private val context: Context) {
    suspend fun recognize(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = try {
            InputImage.fromFilePath(context, uri)
        } catch (_: Throwable) {
            return@withContext ""
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
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
