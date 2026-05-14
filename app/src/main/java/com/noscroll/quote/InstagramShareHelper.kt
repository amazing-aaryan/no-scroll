package com.noscroll.quote

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object InstagramShareHelper {
    private const val AUTHORITY = "com.noscroll.fileprovider"

    private fun saveBitmapToCache(activity: Activity, bitmap: Bitmap): Uri {
        val dir = File(activity.cacheDir, "quote_cards").apply { mkdirs() }
        val file = File(dir, "quote_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return FileProvider.getUriForFile(activity, AUTHORITY, file)
    }

    fun shareToStories(activity: Activity, bitmap: Bitmap) {
        val uri = saveBitmapToCache(activity, bitmap)
        val intent = Intent("com.instagram.android.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.instagram.android")
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            shareGeneric(activity, bitmap)
        }
    }

    fun shareToFeed(activity: Activity, bitmap: Bitmap) {
        val uri = saveBitmapToCache(activity, bitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            setPackage("com.instagram.android")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            shareGeneric(activity, bitmap)
        }
    }

    fun shareGeneric(activity: Activity, bitmap: Bitmap) {
        val uri = saveBitmapToCache(activity, bitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "Share quote"))
    }
}
