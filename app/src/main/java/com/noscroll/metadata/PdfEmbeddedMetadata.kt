package com.noscroll.metadata

import android.content.Context
import android.net.Uri

data class EmbeddedPdfMetadata(val title: String?, val author: String?)

object PdfEmbeddedMetadata {
    fun extract(context: Context, uri: Uri): EmbeddedPdfMetadata? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(2 * 1024 * 1024)
                val count = input.read(buffer)
                if (count <= 0) ByteArray(0) else buffer.copyOf(count)
            } ?: return null
            val raw = bytes.toString(Charsets.ISO_8859_1)
            val title = pdfString(raw, "Title")
            val author = pdfString(raw, "Author")
            if (title == null && author == null) null else EmbeddedPdfMetadata(title, author)
        } catch (_: Exception) {
            null
        }
    }

    private fun pdfString(raw: String, key: String): String? {
        val literal = Regex("/$key\\s*\\(([^)]{1,240})\\)").find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\(", "(")
            ?.replace("\\)", ")")
            ?.trim()
        if (!literal.isNullOrBlank()) return literal
        return Regex("/$key\\s*<([0-9A-Fa-f]{4,480})>").find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.chunked(2)
            ?.mapNotNull { it.toIntOrNull(16)?.toChar() }
            ?.joinToString("")
            ?.replace("\u0000", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
