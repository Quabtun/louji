package com.quabtun.louji

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test is a no-op unless it is explicitly started with
 * `-e louji.seed true`. It creates clearly synthetic demo records for screenshots.
 */
@RunWith(AndroidJUnit4::class)
class SeedDemoDataTest {
    @Test
    fun seedDemoData() {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("louji.seed") != "true") return
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        val repository = app.container.repository
        val mediaStore = app.container.mediaStore
        runBlocking {
            repository.clearAll()
            mediaStore.sweepOrphans(emptySet())
            val photo = createDemoPhoto(mediaStore)

            repository.save(
                draft = RecordDraft(
                    location = "阳光花园 3 栋",
                    orderNo = "A12345",
                    gender = Gender.FEMALE,
                    phoneTail = "4821",
                    floor = "7",
                    roomNo = "2-301",
                    doorIntact = TriState.YES,
                    canOpenFromAbove = TriState.YES,
                    noStairs = TriState.YES,
                    willComeDown = TriState.YES,
                    personalityTags = listOf("好沟通", "爽快"),
                    personalityNote = "电话会接，放门口即可。",
                    remark = "门禁在左手边，电梯到 7 楼。",
                ),
                recordId = null,
                pendingMedia = listOf(photo),
            )
            repository.save(
                draft = RecordDraft(
                    location = "锦绣小区 6 号楼",
                    orderNo = "B7788",
                    gender = Gender.MALE,
                    phoneTail = "1366",
                    floor = "12",
                    roomNo = "1201",
                    doorIntact = TriState.YES,
                    canOpenFromAbove = TriState.NO,
                    noStairs = TriState.NO,
                    willComeDown = TriState.NO,
                    personalityTags = listOf("要求多", "必须送上门"),
                    personalityNote = "电梯高峰很慢，必须送到门口。",
                    remark = "电梯高峰很慢，尽量走货梯。",
                ),
                recordId = null,
                pendingMedia = emptyList(),
            )
            repository.save(
                draft = RecordDraft(
                    location = "老城公寓 A 座",
                    orderNo = "C0921",
                    gender = Gender.FEMALE,
                    phoneTail = "9087",
                    floor = "3",
                    roomNo = "305",
                    doorIntact = TriState.NO,
                    canOpenFromAbove = TriState.UNKNOWN,
                    noStairs = TriState.YES,
                    willComeDown = TriState.YES,
                    personalityTags = listOf("电话不接"),
                    personalityNote = "到了放门口，不要敲门。",
                    remark = "单元门坏了，直接推开。",
                ),
                recordId = null,
                pendingMedia = emptyList(),
            )
            repository.save(
                draft = RecordDraft(
                    location = "江景苑 2 栋",
                    orderNo = "D5566",
                    gender = Gender.MALE,
                    phoneTail = "2145",
                    floor = "21",
                    roomNo = "2103",
                    doorIntact = TriState.YES,
                    canOpenFromAbove = TriState.YES,
                    noStairs = TriState.NO,
                    willComeDown = TriState.YES,
                    personalityTags = listOf("爽快"),
                    personalityNote = "电梯坏过一次，先问保安。",
                    remark = "保安会问楼栋，报 2 栋。",
                ),
                recordId = null,
                pendingMedia = emptyList(),
            )
        }
    }

    private suspend fun createDemoPhoto(mediaStore: com.quabtun.louji.data.LocalMediaStore): MediaDraft {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val width = 720
        val height = 960
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(214, 210, 198))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(38, 41, 43)
        canvas.drawRect(80f, 120f, 640f, 820f, paint)
        paint.color = Color.rgb(250, 247, 238)
        canvas.drawRect(120f, 180f, 600f, 760f, paint)
        paint.color = Color.rgb(30, 33, 35)
        canvas.drawRect(390f, 420f, 540f, 760f, paint)
        val arch = Path().apply {
            moveTo(390f, 470f)
            cubicTo(390f, 350f, 540f, 350f, 540f, 470f)
            lineTo(540f, 760f)
            lineTo(390f, 760f)
            close()
        }
        paint.color = Color.rgb(250, 247, 238)
        canvas.drawPath(arch, paint)
        paint.color = Color.rgb(255, 200, 61)
        canvas.drawRect(150f, 680f, 250f, 760f, paint)
        canvas.drawRect(250f, 620f, 350f, 760f, paint)
        canvas.drawRect(350f, 560f, 390f, 760f, paint)
        paint.color = Color.rgb(28, 31, 33)
        paint.textSize = 38f
        canvas.drawText("3 栋 · 2-301", 120f, 88f, paint)
        paint.textSize = 25f
        paint.color = Color.rgb(90, 95, 98)
        canvas.drawText("楼记演示照片 · 非真实客户", 120f, 900f, paint)

        val file = File(context.cacheDir, "demo-photo-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        val draft = mediaStore.importFromUri(Uri.fromFile(file))
        file.delete()
        return draft
    }
}
