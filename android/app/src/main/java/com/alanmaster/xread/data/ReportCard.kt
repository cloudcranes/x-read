package com.alanmaster.xread.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * 阅读报告分享卡：把一次月报/年报画成 1080 宽的 PNG 再走系统分享。
 *
 * 和 [MarkCard] 同一套做法与同一套纸色，不引第三方绘图库：
 * 固定宽度、按内容算高度、写完落 `cache/share/`，再交给已有的 FileProvider。
 */
object ReportCard {
    private const val WIDTH = 1080
    private const val PAD = 72
    private const val INNER = WIDTH - PAD * 2

    private const val BG = "#F6F0E2"
    private const val INK = "#1F1A14"
    private const val INK_3 = "#8A7F6A"
    private const val INK_4 = "#9A8F7A"
    private const val RULE = "#DCD2BE"
    private const val ACCENT = "#B3341F"
    private const val TRACK = "#E7DECB"

    private const val CHART_HEIGHT = 240f

    /**
     * @param bars 图表数据，`label` 是画在柱子下面的短标签（月视图给日，年视图给月），
     *             `seconds` 为那一段的阅读秒数；空列表时画空态文案。
     */
    fun share(context: Context, summary: ReportSummary, bars: List<Pair<String, Long>>): Boolean =
        runCatching {
            val bitmap = draw(summary, bars)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "x-read-report.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "拾页 · ${summary.period.longLabel}阅读报告")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享阅读报告"))
            true
        }.getOrDefault(false)

    private fun draw(summary: ReportSummary, bars: List<Pair<String, Long>>): Bitmap {
        val eyebrow = paint(30f, INK_3, Typeface.SANS_SERIF)
        val title = paint(64f, INK, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val subtitle = paint(32f, INK_3, Typeface.SERIF)
        val hero = paint(112f, INK, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val unit = paint(38f, INK_3, Typeface.SERIF)
        val statValue = paint(44f, INK, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val statLabel = paint(26f, INK_3, Typeface.SANS_SERIF)
        val barLabel = paint(24f, INK_4, Typeface.SANS_SERIF)
        val footer = paint(28f, INK_4, Typeface.SANS_SERIF)
        val empty = paint(36f, INK_3, Typeface.SERIF)

        val hasData = summary.totalSeconds > 0
        val chartTop = PAD + 30f + 84f + 48f + 150f + 56f + 60f + 34f + 44f
        val chartBottom = chartTop + CHART_HEIGHT
        val height = (chartBottom + 96f + PAD).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor(BG))

        var y = PAD + 30f
        canvas.drawText("拾页 · 阅读报告", PAD.toFloat(), y, eyebrow)

        y += 84f
        canvas.drawText(summary.period.longLabel, PAD.toFloat(), y, title)

        y += 48f
        val subtitleText = if (hasData) {
            "这个${if (summary.period.yearly) "年" else "月"}有 ${summary.activeDays} 天翻开过书"
        } else {
            "还没有读完的十分钟"
        }
        canvas.drawText(subtitleText, PAD.toFloat(), y, subtitle)

        y += 150f
        val (heroText, heroUnit) = heroOf(summary)
        canvas.drawText(heroText, PAD.toFloat(), y, hero)
        canvas.drawText(
            heroUnit,
            PAD + hero.measureText(heroText) + 16f,
            y,
            unit,
        )

        y += 56f
        canvas.drawRect(PAD.toFloat(), y, (WIDTH - PAD).toFloat(), y + 1.5f, ruleStroke())
        y += 60f
        drawStats(canvas, y, summary, statValue, statLabel)

        y = chartBottom
        if (hasData && bars.isNotEmpty()) {
            drawBars(canvas, chartTop, chartBottom, bars, barLabel)
        } else {
            canvas.drawText("读一点，这里就会有形状。", PAD.toFloat(), chartTop + CHART_HEIGHT / 2, empty)
        }

        canvas.drawRect(PAD.toFloat(), y + 44f, (WIDTH - PAD).toFloat(), y + 45.5f, ruleStroke())
        canvas.drawText("— 拾页", PAD.toFloat(), height - PAD.toFloat(), footer)
        val tail = if (hasData) {
            String.format(Locale.US, "累计 %.1f 小时", summary.hours)
        } else {
            "每天十分钟"
        }
        canvas.drawText(tail, WIDTH - PAD - footer.measureText(tail), height - PAD.toFloat(), footer)
        return bitmap
    }

    private fun heroOf(summary: ReportSummary): Pair<String, String> = when {
        summary.totalSeconds >= 3600 -> String.format(Locale.US, "%.1f", summary.hours) to "小时"
        summary.totalSeconds > 0 -> (summary.totalSeconds / 60).toString() to "分钟"
        else -> "0" to "分钟"
    }

    private fun drawStats(
        canvas: Canvas,
        baseline: Float,
        summary: ReportSummary,
        valuePaint: TextPaint,
        labelPaint: TextPaint,
    ) {
        val column = INNER / 3f
        val cells = listOf(
            summary.activeDays.toString() to "天在读",
            summary.longestStreak.toString() to "最长连续",
            (summary.bestDaySeconds / 60).toString() to "最长一天 / 分",
        )
        cells.forEachIndexed { index, (value, label) ->
            val x = PAD + column * index
            canvas.drawText(value, x, baseline, valuePaint)
            canvas.drawText(label, x, baseline + 34f, labelPaint)
        }
    }

    private fun drawBars(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        bars: List<Pair<String, Long>>,
        labelPaint: TextPaint,
    ) {
        val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        val gap = if (bars.size > 20) 6f else 10f
        val slot = (INNER - gap * (bars.size - 1)) / bars.size
        // 柱子太密时不画每根标签，避免糊成一片
        val labelEvery = when {
            bars.size <= 12 -> 1
            bars.size <= 16 -> 2
            else -> 5
        }
        val barPaint = paint(1f, ACCENT)
        val trackPaint = paint(1f, TRACK)
        val plotBottom = bottom - 42f

        bars.forEachIndexed { index, (_, seconds) ->
            val x = PAD + (slot + gap) * index
            canvas.drawRect(x, top, x + slot, plotBottom, trackPaint)
            if (seconds > 0) {
                val h = (seconds.toFloat() / max) * (plotBottom - top)
                canvas.drawRect(x, plotBottom - h.coerceAtLeast(6f), x + slot, plotBottom, barPaint)
            }
            if (index % labelEvery == 0) {
                val text = bars[index].first
                val tx = x + (slot - labelPaint.measureText(text)) / 2f
                canvas.drawText(text, tx, bottom - 8f, labelPaint)
            }
        }
        canvas.drawRect(PAD.toFloat(), plotBottom, (WIDTH - PAD).toFloat(), plotBottom + 1.5f, ruleStroke())
    }

    private fun ruleStroke() = paint(1f, RULE)

    private fun paint(size: Float, color: String, typeface: Typeface = Typeface.SANS_SERIF) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = Color.parseColor(color)
            this.typeface = typeface
        }
}
