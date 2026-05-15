package com.noscroll.quote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.QuoteCardEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.PaperColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuoteCardPreviewActivity : AppCompatActivity() {
    private var spec by mutableStateOf<QuoteCardSpec?>(null)
    private var currentBitmap by mutableStateOf<Bitmap?>(null)
    private var bookUri: String = ""
    private var pageIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(PaperColors.Paper)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    currentBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Quote card preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 5f)
                                .padding(top = 24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuoteCardTheme.values().forEach { theme ->
                            OutlinedButton(
                                onClick = {
                                    spec = spec?.copy(theme = theme)
                                    render()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { finish() }) { Text("Close") }
                        Button(
                            onClick = { saveCurrentQuote() },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Sage),
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        Button(
                            onClick = { shareCurrentQuote() },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Ink),
                            modifier = Modifier.weight(1f)
                        ) { Text("Share") }
                    }
                }
            }
        }
        loadSpec()
    }

    private fun loadSpec() {
        val quote = intent.getStringExtra(EXTRA_QUOTE_TEXT).orEmpty()
        bookUri = intent.getStringExtra(EXTRA_BOOK_URI).orEmpty()
        pageIndex = intent.getIntExtra(EXTRA_PAGE_NUMBER, 0)
        lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                BookMetadataRepository.resolve(
                    context = this@QuoteCardPreviewActivity,
                    uri = Uri.parse(bookUri),
                    document = null,
                    allowOnlineOnce = true
                )
            }
            val title = metadata.title.takeUnless { isBadBookTitle(it) } ?: "Untitled"
            val author = metadata.author.takeUnless { it == "Unknown Author" }.orEmpty()
            spec = QuoteCardSpec(
                quoteText = quote,
                bookTitle = title.ifBlank { "Untitled" },
                author = author.ifBlank { "Unknown Author" },
                pageNumber = pageIndex + 1,
                theme = QuoteCardTheme.PAPER
            )
            render()
        }
    }

    private fun isBadBookTitle(title: String): Boolean =
        title.isBlank() || title.contains(";") || title.contains("=") || title.endsWith(" temp", ignoreCase = true)

    private fun render() {
        val nextSpec = spec ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QuoteCardBitmapBuilder.build(nextSpec)
            }
            currentBitmap = bitmap
        }
    }

    private fun saveCurrentQuote(onSaved: (() -> Unit)? = null) {
        val current = spec ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            AnnotationDatabase.getInstance(this@QuoteCardPreviewActivity).quoteCardDao().upsert(
                QuoteCardEntity(
                    bookUri = bookUri,
                    highlightId = null,
                    quoteText = current.quoteText,
                    pageIndex = pageIndex,
                    themeName = current.theme.name
                )
            )
            withContext(Dispatchers.Main) { onSaved?.invoke() }
        }
    }

    private fun shareCurrentQuote() {
        val bitmap = currentBitmap ?: return
        saveCurrentQuote {
            ShareBottomSheet.newInstance(bitmap).show(supportFragmentManager, "share")
        }
    }

    companion object {
        const val EXTRA_QUOTE_TEXT = "quote_text"
        const val EXTRA_BOOK_URI = "book_uri"
        const val EXTRA_PAGE_NUMBER = "page_number"
    }
}
