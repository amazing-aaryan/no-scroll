package com.noscroll

import java.text.Normalizer

object PdfSelectionTextCleaner {
    fun clean(raw: CharSequence): String {
        val normalized = Normalizer.normalize(raw.toString(), Normalizer.Form.NFKC)
            .replace('\u00A0', ' ')
            .replace('\uFFFD', ' ')
            .replace(Regex("([\\p{L}])-[ \\t]*\\R[ \\t]*([\\p{L}])"), "$1$2")

        return normalized
            .filterIndexed { index, char ->
                when {
                    char == '%' -> isNumericPercent(normalized, index)
                    char.isISOControl() && char != '\n' && char != '\r' && char != '\t' -> false
                    else -> true
                }
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isNumericPercent(text: String, percentIndex: Int): Boolean {
        for (index in percentIndex - 1 downTo 0) {
            val char = text[index]
            if (!char.isWhitespace()) return char.isDigit()
        }
        return false
    }
}
