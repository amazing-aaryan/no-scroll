package com.noscroll

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.component.HighlightAnnotation
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.core.content.FileProvider
import com.noscroll.data.HighlightEntity
import java.io.File

object PdfHighlightExporter {
    private const val AUTHORITY = "com.noscroll.fileprovider"

    fun canWriteNativeHighlights(): Boolean =
        Build.VERSION.SDK_INT >= 36 ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18)

    fun exportAnnotatedPdf(context: Context, sourceUri: Uri, highlights: List<HighlightEntity>): Uri {
        if (!canWriteNativeHighlights()) {
            throw UnsupportedOperationException("Native PDF highlight export is not available on this device.")
        }
        val output = exportFile(context, "noscroll_highlights_${System.currentTimeMillis()}.pdf")
        val sourcePfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Could not open source PDF")
        val outputPfd = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
        )

        PdfRenderer(sourcePfd).use { renderer ->
            highlights.groupBy { it.pageIndex }.forEach { (pageIndex, pageHighlights) ->
                renderer.openPage(pageIndex).use { page ->
                    pageHighlights.forEach { highlight ->
                        val rects = PdfSelectionCodec.decode(highlight.selectionBoundsJson)
                            .filter { it.pageNum == pageIndex }
                            .map { RectF(it.left, it.top, it.right, it.bottom) }
                            .toMutableList()
                        if (rects.isNotEmpty()) {
                            page.addPageAnnotation(
                                HighlightAnnotation(rects).apply { setColor(highlight.colorArgb) }
                            )
                        }
                    }
                }
            }
            renderer.write(outputPfd, false)
        }

        return FileProvider.getUriForFile(context, AUTHORITY, output)
    }

    fun exportHighlightsText(context: Context, metadataTitle: String, highlights: List<HighlightEntity>): Uri {
        val output = exportFile(context, "noscroll_highlights_${System.currentTimeMillis()}.txt")
        output.writeText(
            buildString {
                appendLine(metadataTitle)
                appendLine()
                highlights.sortedWith(compareBy<HighlightEntity> { it.pageIndex }.thenBy { it.createdAtMillis })
                    .forEach { highlight ->
                        appendLine("p.${highlight.pageIndex + 1}")
                        appendLine(highlight.quoteText)
                        appendLine()
                    }
            }
        )
        return FileProvider.getUriForFile(context, AUTHORITY, output)
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun exportFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "pdf_exports").apply { mkdirs() }
        return File(dir, name)
    }
}
