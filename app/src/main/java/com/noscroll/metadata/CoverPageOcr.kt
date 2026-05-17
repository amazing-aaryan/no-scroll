package com.noscroll.metadata

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class CoverBlock(
    val text: String,
    val area: Int,
    val centerYFraction: Float  // 0 = top of image, 1 = bottom
)

object CoverPageOcr {
    suspend fun extractText(bitmap: Bitmap): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun recognize(bitmap: Bitmap): Text? {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (_: Exception) {
            null
        }
    }

    // Returns text blocks sorted by bounding-box area descending.
    // Larger area ≈ larger font ≈ more prominent — reliably identifies title on cover pages.
    suspend fun recognizeBlocks(bitmap: Bitmap): List<CoverBlock> {
        val result = recognize(bitmap) ?: return emptyList()
        val imageHeight = bitmap.height.toFloat().coerceAtLeast(1f)
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val text = block.text.replace('\n', ' ').trim()
            if (text.isBlank()) return@mapNotNull null
            CoverBlock(
                text = text,
                area = box.width() * box.height(),
                centerYFraction = box.exactCenterY() / imageHeight
            )
        }.sortedByDescending { it.area }
    }
}
