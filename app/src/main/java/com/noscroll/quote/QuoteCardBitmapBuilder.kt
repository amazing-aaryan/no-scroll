package com.noscroll.quote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.roundToInt

object QuoteCardBitmapBuilder {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    fun build(context: Context, spec: QuoteCardSpec): Bitmap {
        val style = QuoteCardStyles.byId(spec.styleId)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, style)
        drawFrame(canvas, style)
        drawQuoteMarks(canvas, style)
        val quoteBottom = drawQuoteText(canvas, spec, style)
        drawAttribution(canvas, spec, style, quoteBottom)
        drawWatermark(canvas, style)
        @Suppress("UNUSED_VARIABLE")
        val ignoredContext = context
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, style: QuoteCardStylePack) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                style.bgStart, style.bgEnd, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        when (style.backgroundKind) {
            QuoteBackgroundKind.GRADIENT -> drawPaperGrain(canvas, style)
            QuoteBackgroundKind.OCEAN -> drawOcean(canvas)
            QuoteBackgroundKind.MOUNTAIN -> drawMountain(canvas)
            QuoteBackgroundKind.RAIN -> drawRain(canvas)
        }
    }

    private fun drawPaperGrain(canvas: Canvas, style: QuoteCardStylePack) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accentColor
            alpha = 10
            strokeWidth = 1f
        }
        for (i in 0 until 42) {
            val y = 35f + i * 32f
            canvas.drawLine(0f, y, WIDTH.toFloat(), y + ((i % 5) - 2) * 2f, paint)
        }
    }

    private fun drawOcean(canvas: Canvas) {
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFE6AA }
        canvas.drawCircle(835f, 255f, 86f, sun)
        val water = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0E5577.toInt() }
        canvas.drawRect(0f, 720f, WIDTH.toFloat(), HEIGHT.toFloat(), water)
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x42D9F4FF
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        for (i in 0 until 12) {
            val y = 780f + i * 42f
            val path = Path().apply {
                moveTo(-40f, y)
                cubicTo(180f, y - 22f, 340f, y + 24f, 560f, y)
                cubicTo(760f, y - 22f, 940f, y + 24f, 1120f, y)
            }
            canvas.drawPath(path, wavePaint)
        }
    }

    private fun drawMountain(canvas: Canvas) {
        val far = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x665B6C88 }
        canvas.drawPath(Path().apply {
            moveTo(0f, 760f); lineTo(220f, 480f); lineTo(410f, 740f)
            lineTo(610f, 420f); lineTo(1080f, 770f); close()
        }, far)
        val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA39475C.toInt() }
        canvas.drawPath(Path().apply {
            moveTo(0f, 930f); lineTo(300f, 560f); lineTo(520f, 910f)
            lineTo(760f, 610f); lineTo(1080f, 920f); lineTo(1080f, 1350f); lineTo(0f, 1350f); close()
        }, near)
        val mist = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFF5DD }
        canvas.drawOval(RectF(-130f, 670f, 1220f, 860f), mist)
    }

    private fun drawRain(canvas: Canvas) {
        val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332E6070 }
        canvas.drawCircle(260f, 340f, 260f, haze)
        canvas.drawCircle(870f, 640f, 320f, haze)
        val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44DDEFF5
            strokeWidth = 3f
        }
        for (i in 0 until 74) {
            val x = ((i * 83) % WIDTH).toFloat()
            val y = ((i * 137) % HEIGHT).toFloat()
            canvas.drawLine(x, y, x - 18f, y + 68f, streak)
        }
    }

    private fun drawFrame(canvas: Canvas, style: QuoteCardStylePack) {
        val panelRect = panelRect(style)
        when (style.panel) {
            QuotePanel.NONE -> Unit
            QuotePanel.SCRIM -> canvas.drawRoundRect(panelRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.panelColor
            })
            QuotePanel.PAPER -> {
                canvas.drawRoundRect(panelRect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.panelColor
                })
                canvas.drawRoundRect(panelRect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.accentColor
                    alpha = 120
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2f
                })
            }
            QuotePanel.GLASS -> {
                canvas.drawRoundRect(panelRect, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.panelColor
                })
                canvas.drawRoundRect(panelRect, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xAAFFFFFF.toInt()
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2f
                })
            }
        }
        if (style.layout == QuoteLayout.EDITORIAL) {
            canvas.drawRect(0f, 0f, 12f, HEIGHT.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentColor
                alpha = 210
            })
        }
        if (style.layout == QuoteLayout.BOOKPLATE) {
            canvas.drawRoundRect(RectF(72f, 86f, 1008f, 1264f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentColor
                alpha = 150
                this.style = Paint.Style.STROKE
                strokeWidth = 3f
            })
        }
    }

    private fun drawQuoteMarks(canvas: Canvas, style: QuoteCardStylePack) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor
            alpha = if (style.panel == QuotePanel.GLASS) 70 else 48
            textSize = if (style.layout == QuoteLayout.MINIMAL) 190f else 260f
            typeface = style.quoteTypeface.toTypeface()
        }
        val x = if (style.layout == QuoteLayout.CENTERED) 438f else 82f
        val y = if (style.layout == QuoteLayout.SCENIC_PANEL) 430f else 270f
        canvas.drawText("\u201c", x, y, paint)
    }

    private fun drawQuoteText(canvas: Canvas, spec: QuoteCardSpec, style: QuoteCardStylePack): Float {
        val bounds = quoteTextRect(style)
        val quote = spec.quoteText.trim().let {
            if (it.length <= style.maxQuoteChars) it else it.take(style.maxQuoteChars - 1).trimEnd() + "\u2026"
        }
        val align = when (style.layout) {
            QuoteLayout.CENTERED, QuoteLayout.SCENIC_PANEL, QuoteLayout.BOOKPLATE -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        var size = style.maxTextSize
        var layout: StaticLayout
        do {
            val paint = quotePaint(style, size)
            layout = buildTextLayout(quote, paint, bounds.width().roundToInt(), align)
            if (layout.height <= bounds.height() || size <= style.minTextSize) break
            size -= 2f
        } while (true)

        val paint = quotePaint(style, size)
        val maxLines = (bounds.height() / (size * 1.12f)).toInt().coerceAtLeast(1)
        layout = buildTextLayout(
            text = quote,
            paint = paint,
            width = bounds.width().roundToInt(),
            alignment = align,
            maxLines = maxLines,
            ellipsize = true
        )

        canvas.save()
        canvas.clipRect(bounds)
        val yOffset = if (style.layout == QuoteLayout.CENTERED || style.layout == QuoteLayout.BOOKPLATE) {
            ((bounds.height() - layout.height) / 2f).coerceAtLeast(0f)
        } else 0f
        canvas.translate(bounds.left, bounds.top + yOffset)
        layout.draw(canvas)
        canvas.restore()
        return bounds.top + yOffset + layout.height
    }

    private fun drawAttribution(canvas: Canvas, spec: QuoteCardSpec, style: QuoteCardStylePack, quoteBottom: Float) {
        val text = buildAttribution(spec)
        val bounds = attributionRect(style, quoteBottom)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.attributionColor
            textSize = if (style.layout == QuoteLayout.MINIMAL) 28f else 34f
            typeface = style.attributionTypeface.toTypeface()
            letterSpacing = if (style.layout == QuoteLayout.MINIMAL) 0.08f else 0f
        }
        val align = when (style.layout) {
            QuoteLayout.CENTERED, QuoteLayout.SCENIC_PANEL, QuoteLayout.BOOKPLATE -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val layout = buildTextLayout(
            text = text,
            paint = paint,
            width = bounds.width().roundToInt(),
            alignment = align,
            maxLines = 2,
            ellipsize = true
        )
        if (style.layout == QuoteLayout.EDITORIAL || style.layout == QuoteLayout.MINIMAL) {
            canvas.drawLine(bounds.left, bounds.top - 28f, bounds.left + 270f, bounds.top - 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentColor
                alpha = 180
                strokeWidth = 2f
            })
        }
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawWatermark(canvas: Canvas, style: QuoteCardStylePack) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor
            alpha = 92
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        canvas.drawText("noscroll", WIDTH - 240f, HEIGHT - 80f, paint)
    }

    private fun panelRect(style: QuoteCardStylePack): RectF = when (style.layout) {
        QuoteLayout.SCENIC_PANEL -> RectF(86f, 395f, 994f, 1030f)
        QuoteLayout.BOOKPLATE -> RectF(112f, 160f, 968f, 1135f)
        else -> RectF(0f, 0f, 0f, 0f)
    }

    private fun quoteTextRect(style: QuoteCardStylePack): RectF = when (style.layout) {
        QuoteLayout.EDITORIAL -> RectF(110f, 360f, 970f, 1030f)
        QuoteLayout.CENTERED -> RectF(120f, 360f, 960f, 1045f)
        QuoteLayout.SCENIC_PANEL -> RectF(148f, 500f, 932f, 855f)
        QuoteLayout.MINIMAL -> RectF(100f, 340f, 980f, 1040f)
        QuoteLayout.BOOKPLATE -> RectF(154f, 315f, 926f, 895f)
    }

    private fun attributionRect(style: QuoteCardStylePack, quoteBottom: Float): RectF = when (style.layout) {
        QuoteLayout.SCENIC_PANEL -> RectF(160f, 890f, 920f, 990f)
        QuoteLayout.BOOKPLATE -> RectF(180f, 980f, 900f, 1105f)
        else -> RectF(110f, (quoteBottom + 64f).coerceAtMost(1110f), 970f, 1260f)
    }

    private fun quotePaint(style: QuoteCardStylePack, size: Float): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor
            textSize = size
            typeface = style.quoteTypeface.toTypeface()
        }

    private fun buildTextLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment,
        maxLines: Int = Int.MAX_VALUE,
        ellipsize: Boolean = false
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
        if (ellipsize) builder.setEllipsize(TextUtils.TruncateAt.END)
        return builder.build()
    }

    private fun buildAttribution(spec: QuoteCardSpec): String {
        val title = spec.bookTitle.ifBlank { "Untitled" }
        val author = spec.author.takeUnless { it.isBlank() || it == "Unknown Author" }
        return if (author == null) "\u2014 $title, p. ${spec.pageNumber}"
        else "\u2014 $author, $title, p. ${spec.pageNumber}"
    }

    fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val paragraphs = text.split(Regex("\\n{2,}"))
        val lines = mutableListOf<String>()
        for (para in paragraphs) {
            if (lines.isNotEmpty()) lines += ""
            lines += wrapParagraph(para.replace('\n', ' '), paint, maxWidth)
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun wrapParagraph(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(Regex("\\s+"))) {
            if (word.isBlank()) continue
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = fitLongWord(word, paint, maxWidth)
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun fitLongWord(word: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(word) <= maxWidth) return word
        val bounds = Rect()
        var end = 1
        while (end <= word.length) {
            paint.getTextBounds(word, 0, end, bounds)
            if (bounds.width() > maxWidth) break
            end++
        }
        return word.take((end - 2).coerceAtLeast(1))
    }
}
