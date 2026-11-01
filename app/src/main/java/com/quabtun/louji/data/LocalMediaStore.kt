package com.quabtun.louji.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.MediaType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the app-private media directory. Every path that reaches the database lives here, so
 * deleting a record can never delete an arbitrary file chosen by a content URI.
 */
class LocalMediaStore(
    private val context: Context,
    mediaDirectoryOverride: File? = null,
    thumbsDirectoryOverride: File? = null,
) {

    private val resolver get() = context.contentResolver
    private val mediaRoot: File =
        (mediaDirectoryOverride ?: File(context.filesDir, "media")).apply { mkdirs() }
    private val thumbsRoot: File =
        (thumbsDirectoryOverride ?: File(context.filesDir, "thumbs")).apply { mkdirs() }

    fun newPhotoFile(): File = File(mediaRoot, "${UUID.randomUUID()}.jpg")

    fun newVideoFile(): File = File(mediaRoot, "${UUID.randomUUID()}.mp4")

    fun mediaDirectory(): File = mediaRoot

    suspend fun importFromUri(uri: Uri): MediaDraft = withContext(Dispatchers.IO) {
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase())
            ?: throw IOException("无法识别这个文件的类型")
        val type = mediaTypeOf(mimeType) ?: throw IOException("只支持图片和视频")
        val extension = extensionFor(mimeType)
        val target = File(mediaRoot, "${UUID.randomUUID()}.$extension")
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) {
                            throw IOException("文件超过 500MB，未导入")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("无法读取这个文件")
            if (target.length() == 0L) throw IOException("文件为空")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }

        val thumbnail = if (type == MediaType.VIDEO) createVideoThumbnail(target) else null
        MediaDraft(
            type = type,
            filePath = target.absolutePath,
            thumbPath = thumbnail,
            mimeType = mimeType,
            sizeBytes = target.length(),
            durationMs = if (type == MediaType.VIDEO) readDuration(target) else 0L,
        )
    }

    fun completePhoto(file: File): MediaDraft = MediaDraft(
        type = MediaType.IMAGE,
        filePath = file.absolutePath,
        thumbPath = null,
        mimeType = "image/jpeg",
        sizeBytes = file.length(),
        durationMs = 0L,
    )

    fun completeVideo(file: File): MediaDraft = MediaDraft(
        type = MediaType.VIDEO,
        filePath = file.absolutePath,
        thumbPath = createVideoThumbnail(file),
        mimeType = "video/mp4",
        sizeBytes = file.length(),
        durationMs = readDuration(file),
    )

    fun deleteDraft(draft: MediaDraft) {
        deletePath(draft.filePath)
        draft.thumbPath?.let(::deletePath)
    }

    fun deleteAttachment(path: String, thumbPath: String?) {
        deletePath(path)
        thumbPath?.let(::deletePath)
    }

    fun deletePath(path: String) {
        val file = File(path)
        if (isInsideMediaRoots(file)) {
            file.delete()
        }
    }

    fun exists(path: String): Boolean = File(path).isFile

    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        var mediaBytes = 0L
        var mediaCount = 0
        mediaRoot.listFiles()?.forEach { file ->
            if (file.isFile) {
                mediaBytes += file.length()
                mediaCount++
            }
        }
        var thumbBytes = 0L
        thumbsRoot.listFiles()?.forEach { file ->
            if (file.isFile) thumbBytes += file.length()
        }
        StorageUsage(mediaBytes = mediaBytes, thumbBytes = thumbBytes, itemCount = mediaCount)
    }

    /** Deletes media files that no record references. Called after destructive imports. */
    suspend fun sweepOrphans(keepPaths: Set<String>) = withContext(Dispatchers.IO) {
        mediaRoot.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in keepPaths) file.delete()
        }
        thumbsRoot.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in keepPaths) file.delete()
        }
    }

    private fun isInsideMediaRoots(file: File): Boolean {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val media = runCatching { mediaRoot.canonicalFile }.getOrNull() ?: return false
        val thumbs = runCatching { thumbsRoot.canonicalFile }.getOrNull() ?: return false
        return canonical.path.startsWith(media.path + File.separator) ||
            canonical.path.startsWith(thumbs.path + File.separator)
    }

    private fun createVideoThumbnail(video: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val rotated = if (rotation == 0) {
                frame
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                    .also { if (it !== frame) frame.recycle() }
            }
            val scale = minOf(1f, 512f / maxOf(rotated.width, rotated.height).toFloat())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * scale).toInt().coerceAtLeast(1),
                    (rotated.height * scale).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== rotated) rotated.recycle() }
            } else {
                rotated
            }
            val target = File(thumbsRoot, "${UUID.randomUUID()}.jpg")
            FileOutputStream(target).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 84, output)
            }
            scaled.recycle()
            target.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readDuration(video: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun mediaTypeOf(mime: String): MediaType? = when {
        mime.startsWith("image/") -> MediaType.IMAGE
        mime.startsWith("video/") -> MediaType.VIDEO
        else -> null
    }

    private fun extensionFor(mime: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: when {
            mime.startsWith("image/") -> "jpg"
            else -> "mp4"
        }

    /** Used by tests to verify a decoded image without loading it into the list. */
    fun decodeBounds(path: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return if (options.outWidth > 0) options.outWidth to options.outHeight else null
    }

    data class StorageUsage(
        val mediaBytes: Long,
        val thumbBytes: Long,
        val itemCount: Int,
    ) {
        val totalBytes: Long get() = mediaBytes + thumbBytes
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 500L * 1024 * 1024
    }
}
