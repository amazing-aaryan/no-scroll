package com.noscroll

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.noscroll.data.BookMetadataEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.metadata.EditMetadataDialog
import com.noscroll.metadata.MetadataLookupPrefs
import com.noscroll.quote.QuoteCardPreviewActivity
import kotlinx.coroutines.launch
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var changeFab: FloatingActionButton
    private lateinit var gotoPageFab: FloatingActionButton
    private lateinit var makeQuoteFab: FloatingActionButton
    private lateinit var pageSeekbar: VerticalSeekBar
    private lateinit var metadataText: TextView
    private lateinit var metadataLookupButton: MaterialButton

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null
    private var currentMetadata: BookMetadataEntity? = null
    private var currentPage = 0
    private var totalPages = 0

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        PdfStorage.saveUri(this, uri)
        PdfStorage.savePage(this, 0)
        openPdf(uri, startPage = 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        cleanupQuoteCache()

        recyclerView  = findViewById(R.id.pdf_recycler)
        changeFab     = findViewById(R.id.change_pdf_fab)
        gotoPageFab   = findViewById(R.id.goto_page_fab)
        makeQuoteFab  = findViewById(R.id.make_quote_fab)
        pageSeekbar   = findViewById(R.id.page_seekbar)
        metadataText  = findViewById(R.id.book_metadata_text)
        metadataLookupButton = findViewById(R.id.metadata_lookup_btn)

        recyclerView.layoutManager = LinearLayoutManager(this)
        PagerSnapHelper().attachToRecyclerView(recyclerView)

        pageSeekbar.onProgressChanged = { progress, fromUser ->
            if (fromUser) {
                recyclerView.scrollToPosition(progress)
                currentPage = progress
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm   = rv.layoutManager as LinearLayoutManager
                    val page = lm.findFirstCompletelyVisibleItemPosition()
                    if (page >= 0) {
                        currentPage = page
                        PdfStorage.savePage(this@PdfViewerActivity, page)
                        if (!pageSeekbar.isDragging) {
                            pageSeekbar.progress = page
                            pageSeekbar.showAndFade()
                        }
                    }
                }
            }
        })

        changeFab.setOnClickListener { launchLibrary() }
        gotoPageFab.setOnClickListener { showGotoPageDialog() }
        makeQuoteFab.setOnClickListener { showManualQuoteDialog() }
        metadataText.setOnLongClickListener {
            currentUri?.let { uri ->
                EditMetadataDialog.show(this, uri, currentMetadata) { metadata ->
                    currentMetadata = metadata
                    renderMetadata(metadata)
                }
            }
            true
        }
        metadataLookupButton.setOnClickListener { confirmOnlineLookup() }

        val savedUri = PdfStorage.getSelectedUri(this)
        if (savedUri != null) {
            currentPage = PdfStorage.getSavedPage(this)
            openPdf(savedUri, startPage = currentPage)
        } else {
            launchLibrary()
        }
    }

    private fun showGotoPageDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 – $totalPages"
        }
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val entered  = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val safePage = (entered - 1).coerceIn(0, totalPages - 1)
                (recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(safePage, 0)
                pageSeekbar.progress = safePage
                pageSeekbar.showAndFade()
                currentPage = safePage
                PdfStorage.savePage(this, safePage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = PdfStorage.getSelectedUri(this)
        if (uri != null && pdfRenderer == null) {
            currentPage = PdfStorage.getSavedPage(this)
            openPdf(uri, startPage = currentPage)
        }
    }

    private fun launchLibrary() {
        startActivity(Intent(this, PdfLibraryActivity::class.java))
    }

    private fun openPdf(uri: Uri, startPage: Int) {
        closeCurrentPdf()
        try {
            currentUri = uri
            metadataText.text = "..."
            metadataLookupButton.visibility = View.GONE
            parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                ?: run { handleBadUri(); return }
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            totalPages  = pdfRenderer!!.pageCount
            val screenWidth = resources.displayMetrics.widthPixels
            recyclerView.adapter = PdfPageAdapter(pdfRenderer!!, screenWidth)
            val safePage = startPage.coerceIn(0, totalPages - 1)
            pageSeekbar.max      = (totalPages - 1).coerceAtLeast(1)
            pageSeekbar.progress = safePage
            pageSeekbar.visibility = View.VISIBLE
            pageSeekbar.showAndFade()
            recyclerView.post {
                (recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(safePage, 0)
            }
            loadMetadata(uri)
        } catch (e: Exception) {
            handleBadUri()
        }
    }

    private fun loadMetadata(uri: Uri, allowOnlineOnce: Boolean = false) {
        lifecycleScope.launch {
            val metadata = BookMetadataRepository.resolve(this@PdfViewerActivity, uri, allowOnlineOnce)
            currentMetadata = metadata
            renderMetadata(metadata)
        }
    }

    private fun renderMetadata(metadata: BookMetadataEntity) {
        metadataText.text = "${metadata.title} - ${metadata.author}"
        metadataLookupButton.visibility =
            if (metadata.source == "manual" && !MetadataLookupPrefs.isOnlineLookupEnabled(this)) View.VISIBLE else View.GONE
    }

    private fun confirmOnlineLookup() {
        val uri = currentUri ?: return
        AlertDialog.Builder(this)
            .setTitle("Look up book info online?")
            .setMessage("This runs OCR on the first page and sends a short title-like snippet to Google Books. It is optional.")
            .setPositiveButton("Allow once") { _, _ ->
                metadataLookupButton.visibility = View.GONE
                loadMetadata(uri, allowOnlineOnce = true)
            }
            .setNeutralButton("Always allow") { _, _ ->
                MetadataLookupPrefs.setOnlineLookupEnabled(this, true)
                metadataLookupButton.visibility = View.GONE
                loadMetadata(uri)
            }
            .setNegativeButton("No thanks") { _, _ ->
                metadataLookupButton.visibility = View.GONE
            }
            .show()
    }

    private fun showManualQuoteDialog() {
        val uri = currentUri ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            hint = "Quote text"
        }
        AlertDialog.Builder(this)
            .setTitle("Make quote card")
            .setView(input)
            .setPositiveButton("Preview") { _, _ ->
                val quote = input.text.toString().trim()
                if (quote.isBlank()) return@setPositiveButton
                startActivity(
                    Intent(this, QuoteCardPreviewActivity::class.java)
                        .putExtra(QuoteCardPreviewActivity.EXTRA_QUOTE_TEXT, quote)
                        .putExtra(QuoteCardPreviewActivity.EXTRA_BOOK_URI, uri.toString())
                        .putExtra(QuoteCardPreviewActivity.EXTRA_PAGE_NUMBER, currentPage)
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleBadUri() {
        PdfStorage.clearUri(this)
        Toast.makeText(this, "Could not open PDF. Please choose another file.", Toast.LENGTH_LONG).show()
        launchLibrary()
    }

    private fun closeCurrentPdf() {
        recyclerView.adapter = null
        pdfRenderer?.close()
        pdfRenderer = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
        currentUri = null
        currentMetadata = null
        totalPages = 0
        pageSeekbar.visibility = View.INVISIBLE
    }

    private fun cleanupQuoteCache() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        File(cacheDir, "quote_cards").listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    override fun onPause() {
        super.onPause()
        PdfStorage.savePage(this, currentPage)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCurrentPdf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val instagramIntent = packageManager.getLaunchIntentForPackage("com.instagram.android")
            ?: packageManager.getLaunchIntentForPackage("com.instagram.lite")
        if (instagramIntent != null) {
            instagramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(instagramIntent)
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
