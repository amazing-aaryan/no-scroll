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
        drawQuoteMarks(canvas, spec.theme)
        val quoteBottom = drawQuoteText(canvas, spec)
        drawAttribution(canvas, spec, quoteBottom + 70f)
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

    private fun drawQuoteMarks(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text
            alpha = 95
            textSize = 240f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("\"", 100f, 230f, paint)
    }

    private fun drawQuoteText(canvas: Canvas, spec: QuoteCardSpec): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.theme.text
            textSize = 62f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val text = spec.quoteText.trim().let {
            if (it.length <= 300) it else it.take(297).trimEnd() + "..."
        }
        val lines = wrapText(text, paint, WIDTH - 180f).take(10)
        var y = 360f
        for (line in lines) {
            canvas.drawText(line, 90f, y, paint)
            y += 78f
        }
        return y
    }

    private fun drawAttribution(canvas: Canvas, spec: QuoteCardSpec, requestedY: Float) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.theme.attribution
            textSize = 38f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val text = "- ${spec.author}, ${spec.bookTitle}, p.${spec.pageNumber}"
        val lines = wrapText(text, paint, WIDTH - 180f).take(3)
        var y = requestedY.coerceAtMost(1120f)
        for (line in lines) {
            canvas.drawText(line, 90f, y, paint)
            y += 52f
        }
    }

    private fun drawWatermark(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text
            alpha = 150
            textSize = 28f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("noscroll", 90f, HEIGHT - 80f, paint)
    }

    fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.replace('\n', ' ').split(Regex("\\s+"))) {
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
        return lines.ifEmpty { listOf("") }
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
