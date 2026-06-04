package com.noscroll.quote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint

object QuoteCardBitmapBuilder {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    fun build(spec: QuoteCardSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, spec.theme)
        drawAccentStrip(canvas, spec.theme)
        drawQuoteMarks(canvas, spec.theme)
        val quoteBottom = drawQuoteText(canvas, spec)
        drawSeparator(canvas, spec.theme, quoteBottom + 56f)
        drawAttribution(canvas, spec, quoteBottom + 96f)
        drawWatermark(canvas, spec.theme)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                theme.bgStart, theme.bgEnd, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawAccentStrip(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            alpha = 210
        }
        canvas.drawRect(0f, 0f, 12f, HEIGHT.toFloat(), paint)
    }

    private fun drawQuoteMarks(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text
            alpha = 55
            textSize = 280f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", 80f, 270f, paint)
    }

    private fun drawQuoteText(canvas: Canvas, spec: QuoteCardSpec): Float {
        // 716px available: card 1350 - start 390 - footer reserve 244
        val availableHeight = 716f
        val textAreaWidth = WIDTH - 200f
        val text = spec.quoteText.trim().let {
            if (it.length <= 600) it else it.take(597).trimEnd() + "…"
        }
        val serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

        var textSize = 62f
        var lineHeight = 82f
        var lines = emptyList<String>()
        while (textSize >= 32f) {
            lineHeight = textSize * (82f / 62f)
            val probe = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSize
                typeface = serifItalic
            }
            lines = wrapText(text, probe, textAreaWidth)
            if (lines.size * lineHeight <= availableHeight) break
            textSize -= 2f
        }

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.theme.text
            this.textSize = textSize
            typeface = serifItalic
        }
        val maxLines = (availableHeight / lineHeight).toInt()
        var y = 390f
        for (line in lines.take(maxLines)) {
            if (line.isNotBlank()) canvas.drawText(line, 110f, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun drawSeparator(canvas: Canvas, theme: QuoteCardTheme, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            alpha = 180
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val clampedY = y.coerceAtMost(1240f)
        canvas.drawLine(110f, clampedY, 380f, clampedY, paint)
    }

    private fun drawAttribution(canvas: Canvas, spec: QuoteCardSpec, requestedY: Float) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.theme.attribution
            textSize = 38f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val text = if (spec.author.isBlank()) "— ${spec.bookTitle}, p. ${spec.pageNumber}"
                   else "— ${spec.author}, ${spec.bookTitle}, p. ${spec.pageNumber}"
        val lines = wrapText(text, paint, WIDTH - 200f).take(3)
        var y = requestedY.coerceAtMost(1260f)
        for (line in lines) {
            canvas.drawText(line, 110f, y, paint)
            y += 54f
        }
    }

    private fun drawWatermark(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text
            alpha = 100
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("noscroll", WIDTH - 240f, HEIGHT - 80f, paint)
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
