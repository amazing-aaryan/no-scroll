package com.noscroll

import androidx.pdf.PdfRect

object PdfSelectionCodec {
    fun encode(bounds: List<PdfRect>): String =
        bounds.joinToString(";") { rect ->
            listOf(rect.pageNum, rect.left, rect.top, rect.right, rect.bottom).joinToString(",")
        }

    fun decode(value: String): List<PdfRect> =
        value.split(';')
            .mapNotNull { raw ->
                val parts = raw.split(',')
                if (parts.size != 5) return@mapNotNull null
                val page = parts[0].toIntOrNull() ?: return@mapNotNull null
                val left = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val top = parts[2].toFloatOrNull() ?: return@mapNotNull null
                val right = parts[3].toFloatOrNull() ?: return@mapNotNull null
                val bottom = parts[4].toFloatOrNull() ?: return@mapNotNull null
                PdfRect(page, left, top, right, bottom)
            }
}
