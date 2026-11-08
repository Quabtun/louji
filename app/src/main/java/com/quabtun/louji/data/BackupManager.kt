package com.quabtun.louji.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.quabtun.louji.BuildConfig
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod

class BackupManager(
    private val context: Context,
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportBackup(
        uri: Uri,
        password: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): BackupManifest = withContext(Dispatchers.IO) {
        cleanupStaleCache()
        val prepared = repository.allRecords().mapIndexed { recordIndex, item ->
            val files = item.attachments.mapIndexedNotNull { attachmentIndex, attachment ->
                val source = File(attachment.filePath)
                if (!source.isFile) return@mapIndexedNotNull null
                val entryName = "media/${recordIndex}_${attachmentIndex}.${extensionOf(source)}"
                PreparedFile(attachment, source, entryName)
            }
            PreparedRecord(item.record, files)
        }
        val mediaCount = prepared.sumOf { it.files.size }
        val buildings = repository.allBuildings()
        val manifest = BackupManifest(
            exportedAt = clock(),
            appVersion = BuildConfig.VERSION_NAME,
            recordCount = prepared.size,
            mediaCount = mediaCount,
            buildingCount = buildings.size,
        )

        if (password.isNullOrBlank()) {
            exportPlain(uri, prepared, buildings, manifest, mediaCount, onProgress)
        } else {
            exportEncrypted(uri, prepared, buildings, manifest, mediaCount, password, onProgress)
        }
        manifest
    }

    private fun exportPlain(
        uri: Uri,
        prepared: List<PreparedRecord>,
        buildings: List<com.quabtun.louji.data.db.BuildingProfileEntity>,
        manifest: BackupManifest,
        mediaCount: Int,
        onProgress: (Int, Int) -> Unit,
    ) {
        val output = openOutput(uri) ?: throw BackupException("无法写入所选位置")
        try {
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putJsonEntry("manifest.json", json.encodeToString(manifest))
                zip.putJsonEntry(
                    "records.json",
                    json.encodeToString(prepared.map { it.toBackupRecord() }),
                )
                zip.putJsonEntry(
                    "buildings.json",
                    json.encodeToString(buildings.map { it.toBackupBuilding() }),
                )
                var done = 0
                for (record in prepared) {
                    for (file in record.files) {
                        zip.putNextEntry(ZipEntry(file.entryName))
                        file.source.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                        done++
                        onProgress(done, mediaCount)
                    }
                }
                if (mediaCount == 0) onProgress(1, 1)
            }
        } catch (error: IOException) {
            throw BackupException("导出失败，请检查存储空间后重试", error)
        }
    }

    private fun exportEncrypted(
        uri: Uri,
        prepared: List<PreparedRecord>,
        buildings: List<com.quabtun.louji.data.db.BuildingProfileEntity>,
        manifest: BackupManifest,
        mediaCount: Int,
        password: String,
        onProgress: (Int, Int) -> Unit,
    ) {
        val tempZip = File(context.cacheDir, "louji-export-${UUID.randomUUID()}.zip")
        try {
            ZipFile(tempZip).use { zip ->
                zip.setPassword(password.toCharArray())
                zip.addStream(
                    ByteArrayInputStream(json.encodeToString(manifest).toByteArray()),
                    encryptedParams("manifest.json"),
                )
                zip.addStream(
                    ByteArrayInputStream(
                        json.encodeToString(prepared.map { it.toBackupRecord() }).toByteArray(),
                    ),
                    encryptedParams("records.json"),
                )
                zip.addStream(
                    ByteArrayInputStream(
                        json.encodeToString(buildings.map { it.toBackupBuilding() }).toByteArray(),
                    ),
                    encryptedParams("buildings.json"),
                )
                var done = 0
                for (record in prepared) {
                    for (file in record.files) {
                        file.source.inputStream().use {
                            zip.addStream(it, encryptedParams(file.entryName))
                        }
                        done++
                        onProgress(done, mediaCount)
                    }
                }
                if (mediaCount == 0) onProgress(1, 1)
            }
            val output = openOutput(uri) ?: throw BackupException("无法写入所选位置")
            try {
                tempZip.inputStream().use { input ->
                    output.use { target -> input.copyTo(target) }
                }
            } catch (error: IOException) {
                throw BackupException("导出失败，请检查存储空间后重试", error)
            }
        } finally {
            tempZip.delete()
        }
    }

    private fun encryptedParams(name: String): ZipParameters = ZipParameters().apply {
        fileNameInZip = name
        isEncryptFiles = true
        encryptionMethod = EncryptionMethod.AES
        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
    }

    /** Writes the ledger as CSV (UTF-8 + BOM). Returns the exported record count. */
    suspend fun exportCsv(uri: Uri, desensitised: Boolean): Int = withContext(Dispatchers.IO) {
        val records = repository.allRecords()
        val csv = CsvExporter.build(records, desensitised)
        val output = openOutput(uri) ?: throw BackupException("无法写入所选位置")
        try {
            output.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        } catch (error: IOException) {
            throw BackupException("导出 CSV 失败，请检查存储空间后重试", error)
        }
        records.size
    }

    suspend fun importBackup(
        uri: Uri,
        mode: ImportMode,
        password: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ImportSummary = withContext(Dispatchers.IO) {
        cleanupStaleCache()
        val encrypted = isEncryptedZip(uri)
        if (encrypted && password.isNullOrBlank()) {
            throw BackupException("这是加密备份，请输入密码")
        }
        if (!encrypted && !password.isNullOrBlank()) {
            throw BackupException("这不是加密备份，请留空密码重新导入")
        }

        val temp = File(context.cacheDir, "louji-import-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            if (encrypted) {
                extractEncrypted(uri, temp, password!!)
            } else {
                extractZip(uri, temp)
            }
            importFromExtracted(temp, mode, onProgress)
        } finally {
            temp.deleteRecursively()
        }
    }

    private suspend fun importFromExtracted(
        temp: File,
        mode: ImportMode,
        onProgress: (Int, Int) -> Unit,
    ): ImportSummary {
        val manifest = readJson<BackupManifest>(File(temp, "manifest.json"))
        if (manifest.app != "louji") throw BackupException("这不是楼记的备份文件")
        if (manifest.schema > SUPPORTED_SCHEMA) {
            throw BackupException("备份来自更新版本的楼记，请先升级应用")
        }
        val records = readJson<List<BackupRecord>>(File(temp, "records.json"))
        if (records.size > MAX_RECORDS) throw BackupException("备份中的记录过多")

        val prepared = mutableListOf<PreparedImport>()
        val seenKeys = HashSet<Triple<String, String, Long>>()
        var skipped = 0
        records.forEach { backup ->
            val location = backup.location.trim().take(120)
            if (location.isBlank()) {
                skipped++
                return@forEach
            }
            val createdAt = backup.createdAt.takeIf { it > 0 } ?: clock()
            val orderNo = backup.orderNo.trim().take(60)
            if (mode == ImportMode.MERGE) {
                val duplicateInDatabase = repository.countBySignature(location, orderNo, createdAt) > 0
                val key = Triple(location, orderNo, createdAt)
                if (duplicateInDatabase || !seenKeys.add(key)) {
                    skipped++
                    return@forEach
                }
            }
            val attachments = backup.attachments.take(MAX_ATTACHMENTS).mapNotNull { attachment ->
                val entry = attachment.file.replace('\\', '/')
                if (!entry.startsWith("media/")) return@mapNotNull null
                val source = File(temp, entry).canonicalFile
                if (!source.isFile || !source.path.startsWith(temp.canonicalPath + File.separator)) {
                    throw BackupException("备份中的媒体文件不完整")
                }
                attachment to source
            }
            prepared += PreparedImport(
                backup = backup.copy(
                    location = location,
                    orderNo = orderNo,
                    personalityNote = backup.personalityNote.take(200),
                    remark = backup.remark.take(300),
                    climbNote = backup.climbNote.take(300),
                    personalityTags = backup.personalityTags.take(12).map { it.take(30) },
                ),
                createdAt = createdAt,
                sourceAttachments = attachments,
            )
        }

        val copiedMedia = mutableListOf<MediaDraft>()
        val withMedia = try {
            prepared.map { item ->
                item.copy(
                    media = item.sourceAttachments.mapNotNull { (attachment, source) ->
                        val type = runCatching { MediaType.valueOf(attachment.type) }
                            .getOrDefault(MediaType.IMAGE)
                        val mime = attachment.mimeType.takeIf { it.contains('/') }
                            ?: mimeFor(source, type)
                        val extension = extensionForMime(mime, source)
                        val target = File(
                            mediaStore.mediaDirectory(),
                            "${UUID.randomUUID()}.$extension",
                        )
                        source.inputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (target.length() == 0L) {
                            target.delete()
                            return@mapNotNull null
                        }
                            val base = when (type) {
                                MediaType.VIDEO -> mediaStore.completeVideo(target)
                                MediaType.IMAGE -> mediaStore.completeImage(target, mime)
                            }
                            val draft = base.copy(
                                category = runCatching {
                                    AttachmentCategory.valueOf(attachment.category)
                                }.getOrDefault(AttachmentCategory.OTHER),
                            )
                        copiedMedia += draft
                        draft to item.createdAt
                    },
                )
            }
        } catch (error: Throwable) {
            copiedMedia.forEach { mediaStore.deleteDraft(it) }
            throw error
        }

        val importedCount = try {
            repository.insertImportedBatch(
                items = withMedia.map { item -> item.toEntity(clock()) to item.media },
                replaceAll = mode == ImportMode.OVERWRITE,
            )
        } catch (error: Throwable) {
            copiedMedia.forEach { mediaStore.deleteDraft(it) }
            throw error
        }
        val mediaCount = withMedia.sumOf { it.media.size }
        // The import is committed at this point. Cleanup or progress callbacks must never
        // delete files that the database now references.
        runCatching { mediaStore.sweepOrphans(repository.referencedMediaPaths()) }
        runCatching { onProgress(maxOf(importedCount, 1), maxOf(withMedia.size, 1)) }
        return ImportSummary(imported = importedCount, skipped = skipped, media = mediaCount)
    }

    private fun isEncryptedZip(uri: Uri): Boolean {
        val input = openInput(uri) ?: return false
        return try {
            input.use {
                val header = ByteArray(30)
                val read = it.read(header)
                if (read < 8) {
                    false
                } else {
                    var encrypted = false
                    for (i in 0..read - 8) {
                        if (header[i] == 0x50.toByte() && header[i + 1] == 0x4B.toByte() &&
                            header[i + 2] == 0x03.toByte() && header[i + 3] == 0x04.toByte()
                        ) {
                            val flag =
                                (header[i + 6].toInt() and 0xFF) or ((header[i + 7].toInt() and 0xFF) shl 8)
                            encrypted = (flag and 0x1) != 0
                            break
                        }
                    }
                    encrypted
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Deletes leftover backup temp files (`louji-export-*` / `louji-import-*`) from a previous
     * session that was killed mid-operation. Files younger than [olderThanMs] are kept so a
     * concurrent backup is never disturbed. Called before each export/import.
     */
    fun cleanupStaleCache(olderThanMs: Long = STALE_CACHE_MS) {
        val cutoff = clock() - olderThanMs
        runCatching {
            context.cacheDir.listFiles()?.forEach { file ->
                val isBackupTemp = file.name.startsWith("louji-export-") ||
                    file.name.startsWith("louji-import-")
                if (isBackupTemp && file.lastModified() <= cutoff) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
        }
    }

    private fun extractEncrypted(uri: Uri, targetDir: File, password: String) {
        val tempZip = File(context.cacheDir, "louji-import-enc-${UUID.randomUUID()}.zip")
        try {
            val input = openInput(uri) ?: throw BackupException("无法读取所选文件")
            input.use { source -> tempZip.outputStream().use { target -> source.copyTo(target) } }
            val root = targetDir.canonicalFile
            ZipFile(tempZip).use { zip ->
                if (!zip.isEncrypted) {
                    throw BackupException("这不是加密备份，请留空密码重新导入")
                }
                zip.setPassword(password.toCharArray())
                var entries = 0
                var totalBytes = 0L
                for (header in zip.fileHeaders) {
                    entries++
                    if (entries > MAX_ENTRIES) throw BackupException("备份文件包含过多内容")
                    val name = header.fileName.replace('\\', '/')
                    if (header.isDirectory) continue
                    val allowed = name == "manifest.json" || name == "records.json" ||
                        name == "buildings.json" || name.startsWith("media/")
                    if (!allowed || name.startsWith("/") ||
                        name.split('/').any { it == ".." } || name.contains('\u0000')
                    ) {
                        throw BackupException("备份文件包含不安全的路径")
                    }
                    val target = File(root, name).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) {
                        throw BackupException("备份文件包含不安全的路径")
                    }
                    target.parentFile?.mkdirs()
                    val entryLimit = if (name.endsWith(".json")) {
                        MAX_JSON_BYTES
                    } else {
                        MAX_SINGLE_FILE_BYTES
                    }
                    var entryBytes = 0L
                    zip.getInputStream(header).use { entryInput ->
                        target.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = entryInput.read(buffer)
                                if (read <= 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > entryLimit) {
                                    throw BackupException(
                                        if (name.endsWith(".json")) {
                                            "备份中的说明文件过大"
                                        } else {
                                            "备份中的单个文件过大"
                                        },
                                    )
                                }
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    throw BackupException("备份文件过大")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        } catch (error: ZipException) {
            throw BackupException("密码错误或备份文件损坏", error)
        } catch (error: BackupException) {
            throw error
        } finally {
            tempZip.delete()
        }
    }

    private fun extractZip(uri: Uri, targetDir: File) {
        val input = openInput(uri) ?: throw BackupException("无法读取所选文件")
        val root = targetDir.canonicalFile
        var entries = 0
        var totalBytes = 0L
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) throw BackupException("备份文件包含过多内容")
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val allowed = name == "manifest.json" || name == "records.json" ||
                        name == "buildings.json" || name.startsWith("media/")
                    if (!allowed || name.startsWith("/") || name.split('/').any { it == ".." } ||
                        name.contains('\u0000')
                    ) {
                        throw BackupException("备份文件包含不安全的路径")
                    }
                    val target = File(root, name).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) {
                        throw BackupException("备份文件包含不安全的路径")
                    }
                    target.parentFile?.mkdirs()
                    val entryLimit = if (name.endsWith(".json")) {
                        MAX_JSON_BYTES
                    } else {
                        MAX_SINGLE_FILE_BYTES
                    }
                    var entryBytes = 0L
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > entryLimit) {
                                throw BackupException(
                                    if (name.endsWith(".json")) {
                                        "备份中的说明文件过大"
                                    } else {
                                        "备份中的单个文件过大"
                                    },
                                )
                            }
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw BackupException("备份文件过大")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException("无法读取备份文件", error)
        }
    }

    private fun openInput(uri: Uri): InputStream? =
        if (uri.scheme == "file") {
            uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        } else {
            context.contentResolver.openInputStream(uri)
        }

    private fun openOutput(uri: Uri): OutputStream? =
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                runCatching {
                    File(path).parentFile?.mkdirs()
                    File(path).outputStream()
                }.getOrNull()
            }
        } else {
            context.contentResolver.openOutputStream(uri, "wt")
        }

    private inline fun <reified T> readJson(file: File): T {
        if (!file.isFile) throw BackupException("备份缺少 ${file.name}")
        if (file.length() > MAX_JSON_BYTES) throw BackupException("备份中的说明文件过大")
        return try {
            json.decodeFromString<T>(file.readText())
        } catch (error: Throwable) {
            throw BackupException("备份内容损坏：${file.name}", error)
        }
    }

    private fun ZipOutputStream.putJsonEntry(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun PreparedRecord.toBackupRecord(): BackupRecord = BackupRecord(
        location = record.location,
        orderNo = record.orderNo,
        gender = record.gender.name,
        phoneTail = record.phoneTail,
        floor = record.floor,
        roomNo = record.roomNo,
        doorIntact = record.doorIntact.name,
        canOpenFromAbove = record.canOpenFromAbove.name,
        noStairs = record.noStairs.name,
        willComeDown = record.willComeDown.name,
        personalityTags = record.personalityTags,
        personalityNote = record.personalityNote,
        remark = record.remark,
        climbNote = record.climbNote,
        archived = record.archived,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        phoneExt = record.phoneExt,
        accessCode = record.accessCode,
        elevatorType = record.elevatorType.name,
        deliveryMethod = record.deliveryMethod.name,
        isWarning = record.isWarning,
        attachments = files.map { file ->
            BackupAttachment(
                type = file.attachment.type.name,
                file = file.entryName,
                mimeType = file.attachment.mimeType,
                sizeBytes = file.attachment.sizeBytes,
                durationMs = file.attachment.durationMs,
                category = file.attachment.category.name,
            )
        },
    )

    private fun com.quabtun.louji.data.db.BuildingProfileEntity.toBackupBuilding() =
        BackupBuilding(
            location = location,
            elevatorType = elevatorType.name,
            accessCode = accessCode,
            doorIntact = doorIntact.name,
            canOpenFromAbove = canOpenFromAbove.name,
            lockerInfo = lockerInfo,
            accessTags = accessTags,
            notes = notes,
            coverPhotoPath = coverPhotoPath,
            pinned = pinned,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun PreparedImport.toEntity(now: Long): RecordEntity = RecordEntity(
        location = backup.location,
        orderNo = backup.orderNo,
        gender = runCatching { Gender.valueOf(backup.gender) }.getOrDefault(Gender.UNKNOWN),
        phoneTail = backup.phoneTail.take(8),
        floor = backup.floor.take(12),
        roomNo = backup.roomNo.take(24),
        doorIntact = runCatching { TriState.valueOf(backup.doorIntact) }
            .getOrDefault(TriState.UNKNOWN),
        canOpenFromAbove = runCatching { TriState.valueOf(backup.canOpenFromAbove) }
            .getOrDefault(TriState.UNKNOWN),
        noStairs = runCatching { TriState.valueOf(backup.noStairs) }
            .getOrDefault(TriState.UNKNOWN),
        willComeDown = runCatching { TriState.valueOf(backup.willComeDown) }
            .getOrDefault(TriState.UNKNOWN),
        personalityTags = backup.personalityTags,
        personalityNote = backup.personalityNote,
        remark = backup.remark,
        climbNote = backup.climbNote,
        archived = backup.archived,
        createdAt = createdAt,
        updatedAt = backup.updatedAt.takeIf { it > 0 } ?: now,
        phoneExt = backup.phoneExt.take(8),
        accessCode = backup.accessCode.take(24),
        elevatorType = runCatching { ElevatorType.valueOf(backup.elevatorType) }
            .getOrDefault(ElevatorType.UNKNOWN),
        deliveryMethod = runCatching { DeliveryMethod.valueOf(backup.deliveryMethod) }
            .getOrDefault(DeliveryMethod.UNKNOWN),
        isWarning = backup.isWarning,
    )

    private fun extensionOf(file: File): String {
        val extension = file.extension.lowercase()
        return if (extension.length in 1..8 && extension.all { it.isLetterOrDigit() }) extension else "bin"
    }

    private fun extensionForMime(mime: String, source: File): String {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        return extensionOf(source)
    }

    private fun mimeFor(source: File, type: MediaType): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension.lowercase())
            ?: if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg"

    private data class PreparedFile(
        val attachment: com.quabtun.louji.data.db.AttachmentEntity,
        val source: File,
        val entryName: String,
    )

    private data class PreparedRecord(
        val record: RecordEntity,
        val files: List<PreparedFile>,
    )

    private fun BackupBuilding.toEntity(): com.quabtun.louji.data.db.BuildingProfileEntity =
        com.quabtun.louji.data.db.BuildingProfileEntity(
            location = location.trim().take(120),
            elevatorType = runCatching { ElevatorType.valueOf(elevatorType) }
                .getOrDefault(ElevatorType.UNKNOWN),
            accessCode = accessCode.take(24),
            doorIntact = runCatching { com.quabtun.louji.model.TriState.valueOf(doorIntact) }
                .getOrDefault(com.quabtun.louji.model.TriState.UNKNOWN),
            canOpenFromAbove = runCatching {
                com.quabtun.louji.model.TriState.valueOf(canOpenFromAbove)
            }.getOrDefault(com.quabtun.louji.model.TriState.UNKNOWN),
            lockerInfo = lockerInfo.take(200),
            accessTags = accessTags.take(8).map { it.take(20) },
            notes = notes.take(500),
            coverPhotoPath = coverPhotoPath?.takeIf { it.startsWith("/") },
            pinned = pinned,
            createdAt = createdAt.takeIf { it > 0 } ?: clock(),
            updatedAt = updatedAt.takeIf { it > 0 } ?: clock(),
        )

    private data class PreparedImport(
        val backup: BackupRecord,
        val createdAt: Long,
        val sourceAttachments: List<Pair<BackupAttachment, File>>,
        val media: List<Pair<MediaDraft, Long>> = emptyList(),
    )

    private companion object {
        const val SUPPORTED_SCHEMA = 2
        const val MAX_RECORDS = 10_000
        const val MAX_ATTACHMENTS = 20
        const val MAX_ENTRIES = 20_000
        const val MAX_JSON_BYTES = 16L * 1024 * 1024
        const val MAX_SINGLE_FILE_BYTES = 500L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
        const val MAX_BUILDINGS = 5_000
        const val STALE_CACHE_MS = 60L * 60 * 1000
    }
}
