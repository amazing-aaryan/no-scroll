package com.noscroll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailCache {

    private fun cacheDir(context: Context) =
        File(context.cacheDir, "thumbnails").also { it.mkdirs() }

    fun thumbnailFile(context: Context, uri: String): File =
        File(cacheDir(context), "${uri.hashCode()}.jpg")

    suspend fun getOrCreate(context: Context, uri: String): File? =
        withContext(Dispatchers.IO) {
            val file = thumbnailFile(context, uri)
            if (file.exists()) return@withContext file
            try {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                    ?: return@withContext null
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                file
            } catch (e: Exception) {
                null
            }
        }
}
