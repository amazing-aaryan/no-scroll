package com.noscroll.quote

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.noscroll.R
import com.noscroll.data.AnnotationDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuoteCardPreviewActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var chipGroup: ChipGroup
    private var spec: QuoteCardSpec? = null
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote_card_preview)
        preview = findViewById(R.id.quote_preview)
        chipGroup = findViewById(R.id.theme_chips)

        findViewById<MaterialButton>(R.id.edit_quote_btn).setOnClickListener { showEditDialog() }
        findViewById<MaterialButton>(R.id.share_quote_btn).setOnClickListener {
            currentBitmap?.let { bitmap ->
                ShareBottomSheet.newInstance(bitmap).show(supportFragmentManager, "share")
            }
        }

        setupThemeChips()
        loadSpec()
    }

    private fun setupThemeChips() {
        QuoteCardTheme.values().forEach { theme ->
            val chip = Chip(this).apply {
                text = theme.name
                isCheckable = true
                id = theme.ordinal + 1
                setOnClickListener {
                    spec = spec?.copy(theme = theme)
                    render()
                }
            }
            chipGroup.addView(chip)
            if (theme == QuoteCardTheme.DARK) chip.isChecked = true
        }
    }

    private fun loadSpec() {
        val quote = intent.getStringExtra(EXTRA_QUOTE_TEXT).orEmpty()
        val uriString = intent.getStringExtra(EXTRA_BOOK_URI).orEmpty()
        val page = intent.getIntExtra(EXTRA_PAGE_NUMBER, 0)
        lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                AnnotationDatabase.getInstance(this@QuoteCardPreviewActivity)
                    .bookMetadataDao()
                    .get(uriString)
            }
            val title = metadata?.title ?: Uri.parse(uriString).lastPathSegment.orEmpty()
            val author = metadata?.author ?: "Unknown Author"
            spec = QuoteCardSpec(
                quoteText = quote,
                bookTitle = title.ifBlank { "Untitled" },
                author = author,
                pageNumber = page + 1
            )
            render()
        }
    }

    private fun render() {
        val nextSpec = spec ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QuoteCardBitmapBuilder.build(nextSpec)
            }
            currentBitmap = bitmap
            preview.setImageBitmap(bitmap)
        }
    }

    private fun showEditDialog() {
        val current = spec ?: return
        val quoteInput = EditText(this).apply {
            hint = "Quote"
            setText(current.quoteText)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val authorInput = EditText(this).apply {
            hint = "Author"
            setText(current.author)
            setSingleLine(true)
        }
        val titleInput = EditText(this).apply {
            hint = "Title"
            setText(current.bookTitle)
            setSingleLine(true)
        }
        val pageInput = EditText(this).apply {
            hint = "Page"
            setText(current.pageNumber.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(quoteInput)
            addView(authorInput)
            addView(titleInput)
            addView(pageInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit quote card")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                spec = current.copy(
                    quoteText = quoteInput.text.toString(),
                    author = authorInput.text.toString().ifBlank { "Unknown Author" },
                    bookTitle = titleInput.text.toString().ifBlank { "Untitled" },
                    pageNumber = pageInput.text.toString().toIntOrNull() ?: current.pageNumber
                )
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_QUOTE_TEXT = "quote_text"
        const val EXTRA_BOOK_URI = "book_uri"
        const val EXTRA_PAGE_NUMBER = "page_number"
    }
}
