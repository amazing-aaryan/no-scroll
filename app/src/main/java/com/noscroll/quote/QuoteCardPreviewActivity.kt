package com.noscroll.quote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.QuoteCardEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.PaperActionButton
import com.noscroll.ui.PaperButtonTone
import com.noscroll.ui.PaperColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuoteCardPreviewActivity : AppCompatActivity() {
    private var spec by mutableStateOf<QuoteCardSpec?>(null)
    private var currentBitmap by mutableStateOf<Bitmap?>(null)
    private var bookUri: String = ""
    private var pageIndex: Int = 0
    private var renderGeneration: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(PaperColors.Paper)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    currentBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 5f)
                                .padding(top = 10.dp)
                                .shadow(9.dp, RoundedCornerShape(3.dp)),
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

                    StylePicker(
                        selectedStyleId = spec?.styleId ?: QuoteCardStyles.DEFAULT_ID,
                        onSelected = { style ->
                            rememberLastStyle(style.id)
                            spec = spec?.copy(styleId = style.id)
                            render()
                        }
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaperActionButton(label = "Close", onClick = { finish() }, tone = PaperButtonTone.Quiet)
                        PaperActionButton(
                            label = "Save",
                            onClick = { saveCurrentQuote() },
                            tone = PaperButtonTone.Sage,
                            modifier = Modifier.weight(1f)
                        )
                        PaperActionButton(
                            label = "Share",
                            onClick = { shareCurrentQuote() },
                            modifier = Modifier.weight(1f)
                        )
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
                styleId = loadLastStyle()
            )
            render()
        }
    }

    private fun isBadBookTitle(title: String): Boolean =
        title.isBlank() || title.contains(";") || title.contains("=") || title.endsWith(" temp", ignoreCase = true)

    private fun render() {
        val nextSpec = spec ?: return
        val generation = ++renderGeneration
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QuoteCardBitmapBuilder.build(this@QuoteCardPreviewActivity, nextSpec)
            }
            if (generation == renderGeneration && spec?.styleId == nextSpec.styleId) {
                currentBitmap = bitmap
            } else {
                bitmap.recycle()
            }
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
                    themeName = current.styleId
                )
            )
            withContext(Dispatchers.Main) { onSaved?.invoke() }
        }
    }

    private fun shareCurrentQuote() {
        val bitmap = currentBitmap
        val current = spec
        if (bitmap == null || current == null) {
            Toast.makeText(this, "Still loading preview...", Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentQuote {
            ShareBottomSheet.newInstance(bitmap, shareText = buildShareText(current))
                .show(supportFragmentManager, "share")
        }
    }

    private fun buildShareText(current: QuoteCardSpec): String {
        val attribution = if (current.author.isBlank() || current.author == "Unknown Author") {
            "${current.bookTitle}, p. ${current.pageNumber}"
        } else {
            "${current.author}, ${current.bookTitle}, p. ${current.pageNumber}"
        }
        return "\"${current.quoteText.trim()}\"\n\n$attribution\nnoscroll"
    }

    private fun loadLastStyle(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_STYLE, QuoteCardStyles.DEFAULT_ID)
            ?: QuoteCardStyles.DEFAULT_ID

    private fun rememberLastStyle(styleId: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_STYLE, styleId).apply()
    }

    companion object {
        const val EXTRA_QUOTE_TEXT = "quote_text"
        const val EXTRA_BOOK_URI = "book_uri"
        const val EXTRA_PAGE_NUMBER = "page_number"
        private const val PREFS = "quote_card_prefs"
        private const val KEY_LAST_STYLE = "last_style_id"
    }
}

@Composable
private fun StylePicker(
    selectedStyleId: String,
    onSelected: (QuoteCardStylePack) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Style packs",
            style = MaterialTheme.typography.labelSmall,
            color = PaperColors.Muted,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(QuoteCardStyles.all, key = { it.id }) { style ->
                StylePackCard(
                    style = style,
                    selected = style.id == selectedStyleId,
                    onClick = { onSelected(style) }
                )
            }
        }
    }
}

@Composable
private fun StylePackCard(
    style: QuoteCardStylePack,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(shape)
            .background(PaperColors.Raised)
            .border(1.5.dp, if (selected) Color(style.accentColor) else PaperColors.Hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp)
    ) {
        StyleSwatch(style, Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(7.dp))
        Text(
            text = style.name,
            style = MaterialTheme.typography.labelMedium,
            color = PaperColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = style.category,
            style = MaterialTheme.typography.labelSmall,
            color = PaperColors.Muted,
            maxLines = 1
        )
    }
}

@Composable
private fun StyleSwatch(style: QuoteCardStylePack, modifier: Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Color(style.bgStart), Color(style.bgEnd))))) {
        Canvas(Modifier.fillMaxSize()) {
            when (style.backgroundKind) {
                QuoteBackgroundKind.OCEAN -> {
                    drawCircle(Color(0x66FFE6AA), radius = size.width * 0.12f, center = Offset(size.width * 0.78f, size.height * 0.28f))
                    drawRect(Color(0xAA0E5577), topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.42f))
                }
                QuoteBackgroundKind.MOUNTAIN -> {
                    val path = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width * 0.32f, size.height * 0.42f)
                        lineTo(size.width * 0.55f, size.height)
                        lineTo(size.width * 0.78f, size.height * 0.48f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, Color(0xAA39475C))
                }
                QuoteBackgroundKind.RAIN -> {
                    for (i in 0 until 10) {
                        val x = (i * size.width / 9f)
                        drawLine(Color(0x66DDEFF5), Offset(x, 0f), Offset(x - 12f, size.height), strokeWidth = 1.5f)
                    }
                }
                QuoteBackgroundKind.GRADIENT -> Unit
            }
            if (style.panel != QuotePanel.NONE) {
                drawRoundRect(
                    color = Color(style.panelColor),
                    topLeft = Offset(size.width * 0.14f, size.height * 0.26f),
                    size = Size(size.width * 0.72f, size.height * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
            }
            if (style.layout == QuoteLayout.BOOKPLATE) {
                drawRoundRect(
                    color = Color(style.accentColor),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.14f),
                    size = Size(size.width * 0.76f, size.height * 0.72f),
                    style = Stroke(width = 1.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
        Text(
            text = "Aa",
            fontSize = 24.sp,
            fontFamily = if (style.quoteTypeface.family == QuoteFontFamily.SANS) FontFamily.SansSerif else FontFamily.Serif,
            fontStyle = if (style.quoteTypeface.style == android.graphics.Typeface.ITALIC) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (style.quoteTypeface.style == android.graphics.Typeface.BOLD) FontWeight.Bold else FontWeight.Normal,
            color = Color(style.textColor),
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center
        )
    }
}
