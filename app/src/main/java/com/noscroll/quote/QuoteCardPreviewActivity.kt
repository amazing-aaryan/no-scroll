package com.noscroll.quote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    currentBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 5f)
                                .padding(top = 16.dp)
                                .shadow(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Quote card preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Choose style",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = PaperColors.Graphite,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(QuoteCardTheme.values().toList()) { theme ->
                                val isSelected = spec?.theme == theme
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            spec = spec?.copy(theme = theme)
                                            render()
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(theme.bgStart),
                                                        Color(theme.bgEnd)
                                                    )
                                                )
                                            )
                                            .then(
                                                if (isSelected)
                                                    Modifier.border(2.5.dp, Color(theme.accent), CircleShape)
                                                else
                                                    Modifier.border(0.5.dp, Color(0x22000000), CircleShape)
                                            )
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        textAlign = TextAlign.Center,
                                        color = PaperColors.Ink.copy(alpha = if (isSelected) 1f else 0.55f)
                                    )
                                }
                            }
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
                theme = QuoteCardTheme.PARCHMENT
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
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Still loading preview…", Toast.LENGTH_SHORT).show()
            return
        }
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
