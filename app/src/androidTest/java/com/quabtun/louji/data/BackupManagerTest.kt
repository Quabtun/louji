package com.quabtun.louji.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.RecordDraft
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var databaseA: LouJiDatabase
    private lateinit var databaseB: LouJiDatabase
    private lateinit var repositoryA: RecordRepository
    private lateinit var repositoryB: RecordRepository
    private lateinit var backupA: BackupManager
    private lateinit var backupB: BackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        databaseA = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databaseB = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val mediaA = LocalMediaStore(
            context,
            File(root, "media-a"),
            File(root, "thumbs-a"),
        )
        val mediaB = LocalMediaStore(
            context,
            File(root, "media-b"),
            File(root, "thumbs-b"),
        )
        repositoryA = RecordRepository(databaseA, mediaA) { 1_700_000_000_000L }
        repositoryB = RecordRepository(databaseB, mediaB) { 1_700_000_000_000L }
        backupA = BackupManager(context, repositoryA, mediaA) { 1_700_000_000_000L }
        backupB = BackupManager(context, repositoryB, mediaB) { 1_700_000_000_000L }
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        root.deleteRecursively()
    }

    @Test
    fun exportImportRoundTripAndMergeDedupe() = runBlocking {
        val image = File(root, "source.png").apply {
            writeBytes(Base64.decode(ONE_PIXEL_PNG, Base64.DEFAULT))
        }
        val recordId = repositoryA.save(
            draft = RecordDraft(
                location = "阳光花园",
                orderNo = "A1",
                phoneTail = "4821",
                floor = "7",
                roomNo = "301",
                noStairs = com.quabtun.louji.model.TriState.YES,
                personalityTags = listOf("好沟通"),
            ),
            recordId = null,
            pendingMedia = listOf(
                MediaDraft(
                    type = MediaType.IMAGE,
                    filePath = image.absolutePath,
                    thumbPath = null,
                    mimeType = "image/png",
                    sizeBytes = image.length(),
                    durationMs = 0,
                ),
            ),
        )
        assertTrue(recordId > 0)

        val zip = File(root, "backup.zip")
        val manifest = backupA.exportBackup(Uri.fromFile(zip))
        assertEquals(1, manifest.recordCount)
        assertEquals(1, manifest.mediaCount)
        assertTrue(zip.isFile)

        val summary = backupB.importBackup(Uri.fromFile(zip), ImportMode.MERGE)
        assertEquals(1, summary.imported)
        assertEquals(1, summary.media)
        val imported = repositoryB.allRecords().single()
        assertEquals("阳光花园", imported.record.location)
        assertEquals("A1", imported.record.orderNo)
        assertEquals("好沟通", imported.record.personalityTags.single())
        assertEquals(1, imported.attachments.size)
        assertTrue(File(imported.attachments.single().filePath).isFile)

        val duplicate = backupB.importBackup(Uri.fromFile(zip), ImportMode.MERGE)
        assertEquals(0, duplicate.imported)
        assertEquals(1, duplicate.skipped)
        assertEquals(1, repositoryB.allRecords().size)
    }

    @Test
    fun overwriteReplacesExistingRecords() = runBlocking {
        val zip = File(root, "empty.zip")
        backupA.exportBackup(Uri.fromFile(zip))

        repositoryB.save(
            draft = RecordDraft(location = "旧记录"),
            recordId = null,
            pendingMedia = emptyList(),
        )
        assertEquals(1, repositoryB.allRecords().size)

        val summary = backupB.importBackup(Uri.fromFile(zip), ImportMode.OVERWRITE)
        assertEquals(0, summary.imported)
        assertEquals(0, repositoryB.allRecords().size)
    }

    @Test
    fun rejectsZipSlipEntry() = runBlocking {
        val evil = File(root, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }
        assertThrows(BackupException::class.java) {
            runBlocking { backupB.importBackup(Uri.fromFile(evil), ImportMode.MERGE) }
        }
        assertFalse(File(root.parentFile, "escape.txt").exists())
    }

    private companion object {
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z3GQAAAAASUVORK5CYII="
    }
}
