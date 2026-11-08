package com.quabtun.louji.ui.building

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.model.BuildingStats
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.util.canOpenLabel
import com.quabtun.louji.util.doorLabel
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a sanitised "building delivery guide" card. It never reads order numbers, phone tails,
 * room numbers or customer notes — only the public building facts.
 */
object BuildingShareCard {
    private const val WIDTH = 1080
    private const val PAD = 64

    fun render(
        context: Context,
        profile: BuildingProfileEntity,
        stats: BuildingStats,
        includeCode: Boolean,
    ): File {
        val facts = buildList {
            add("电梯：${profile.elevatorType.elevatorLabel()}")
            if (profile.doorIntact != TriState.UNKNOWN) add("门：${profile.doorIntact.doorLabel()}")
            if (profile.canOpenFromAbove != TriState.UNKNOWN) {
                add("上面：${profile.canOpenFromAbove.canOpenLabel()}")
            }
            if (profile.lockerInfo.isNotBlank()) add("外卖柜：${profile.lockerInfo}")
            if (includeCode && profile.accessCode.isNotBlank()) {
                add("门禁密码：#${profile.accessCode}")
            }
        }
        val tags = profile.accessTags
        val note = profile.notes.trim()
        val noteLines = if (note.isBlank()) emptyList() else wrap(note, 22)
        val height = 560 + facts.size * 64 + tags.size * 0 +
            (if (tags.isEmpty()) 0 else 96) + noteLines.size * 52 +
            (if (note.isBlank()) 0 else 130)

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B0E14"))

        val brand = paint(28f, Color.parseColor("#FFB800"), bold = true)
        val title = paint(64f, Color.WHITE, bold = true)
        val body = paint(30f, Color.parseColor("#E2E8F0"))
        val caption = paint(26f, Color.parseColor("#94A3B8"))

        // brand line
        canvas.drawRoundRect(
            RectF(PAD.toFloat(), PAD.toFloat(), PAD + 72f, PAD + 72f),
            18f, 18f, solid(Color.parseColor("#FFB800")),
        )
        canvas.drawText("楼", PAD + 16f, PAD + 54f, paint(40f, Color.parseColor("#111827"), bold = true))
        canvas.drawText("楼记 · 楼栋通行攻略", PAD + 96f, PAD + 52f, brand)

        var y = PAD + 160f
        canvas.drawText(profile.location, PAD.toFloat(), y, title)
        y += 52f
        val rateLine = if (stats.total > 0) {
            "累计 ${stats.total} 单 · 免爬率 ${stats.noStairRate}% · 会下楼 ${stats.willComeDown} 次"
        } else {
            "还没有送单记录"
        }
        canvas.drawText(rateLine, PAD.toFloat(), y, caption)
        y += 56f

        canvas.drawRoundRect(
            RectF(PAD.toFloat(), y, WIDTH - PAD.toFloat(), y + 3f),
            2f, 2f, solid(Color.parseColor("#242C3D")),
        )
        y += 56f

        facts.forEach { fact ->
            canvas.drawText(fact, PAD.toFloat(), y, body)
            y += 64f
        }

        if (tags.isNotEmpty()) {
            y += 8f
            canvas.drawText("通行提醒", PAD.toFloat(), y, caption)
            y += 46f
            var x = PAD.toFloat()
            tags.forEach { tag ->
                val textPaint = paint(28f, Color.parseColor("#FCA5A5"))
                val width = textPaint.measureText(tag) + 40f
                if (x + width > WIDTH - PAD) {
                    x = PAD.toFloat()
                    y += 62f
                }
                canvas.drawRoundRect(
                    RectF(x, y - 34f, x + width, y + 14f),
                    12f, 12f, solid(Color.parseColor("#450A0A")),
                )
                canvas.drawText(tag, x + 20f, y, textPaint)
                x += width + 14f
            }
            y += 66f
        }

        if (noteLines.isNotEmpty()) {
            y += 16f
            canvas.drawText("楼栋攻略", PAD.toFloat(), y, caption)
            y += 46f
            noteLines.forEach { line ->
                canvas.drawText(line, PAD.toFloat(), y, body)
                y += 52f
            }
        }

        y += 30f
        canvas.drawText(
            "由「楼记」生成 · 已隐去订单号、手机尾号、房号与客户评价",
            PAD.toFloat(),
            y,
            caption,
        )

        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "building-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun solid(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** Simple CJK-aware wrapping by character count (no platform text measurement dependency). */
    private fun wrap(text: String, charsPerLine: Int): List<String> =
        text.split('\n').flatMap { paragraph ->
            if (paragraph.length <= charsPerLine) {
                listOf(paragraph)
            } else {
                paragraph.chunked(charsPerLine)
            }
        }.filter { it.isNotBlank() }.take(8)
}
