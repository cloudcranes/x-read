package com.alanmaster.xread.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/**
 * 书摘卡片：把一条书摘画成 1080 宽的 PNG 再走系统分享。
 *
 * 不引第三方绘图库，StaticLayout 负责中文断行；高度按正文行数算，
 * 长摘录先截断，避免生成一张几千像素高的图。
 */
object MarkCard {
    private const val WIDTH = 1080
    private const val PAD = 72
    private const val MAX_CHARS = 420

    fun share(context: Context, entry: MarkEntry, bookTitle: String): Boolean = runCatching {
        val bitmap = draw(entry, bookTitle)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "x-read-mark.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, entry.mark.text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享书摘"))
        true
    }.getOrDefault(false)

    private fun draw(entry: MarkEntry, bookTitle: String): Bitmap {
        val body = entry.mark.text.trim().let {
            if (it.length > MAX_CHARS) it.take(MAX_CHARS) + "…" else it
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f
            color = Color.parseColor("#2B2620")
            typeface = Typeface.SERIF
        }
        val inner = WIDTH - PAD * 2
        val bodyLayout = StaticLayout.Builder
            .obtain(body, 0, body.length, bodyPaint, inner)
            .setLineSpacing(20f, 1.0f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            color = Color.parseColor("#8A7F6A")
            typeface = Typeface.SANS_SERIF
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 52f
            color = Color.parseColor("#1F1A14")
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = Color.parseColor("#9A8F7A")
        }

        val chapterLine = entry.chapterTitle.ifBlank { "" }
        val height = PAD * 2 + 90 + bodyLayout.height + 120
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F6F0E2"))

        canvas.drawText(bookTitle.take(24), PAD.toFloat(), PAD.toFloat() + 42f, titlePaint)
        var y = PAD + 42f + 46f
        if (chapterLine.isNotEmpty()) {
            canvas.drawText(chapterLine.take(28), PAD.toFloat(), y, headPaint)
            y += 16f
        }
        canvas.save()
        canvas.translate(PAD.toFloat(), y + 26f)
        bodyLayout.draw(canvas)
        canvas.restore()

        canvas.drawText("— 拾页书摘", PAD.toFloat(), height - PAD.toFloat(), footerPaint)
        return bitmap
    }
}
