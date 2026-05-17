package com.noscroll

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.PopupMenu
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.pdf.PdfDocument
import androidx.pdf.PdfRect
import com.noscroll.data.BookMetadataEntity
import com.noscroll.data.BookmarkEntity
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.HighlightEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.metadata.CoverPageOcr
import com.noscroll.metadata.EditMetadataDialog
import com.noscroll.metadata.MetadataLookupPrefs
import com.noscroll.quote.QuoteCardBitmapBuilder
import com.noscroll.quote.QuoteCardPreviewActivity
import com.noscroll.quote.QuoteCardSpec
import com.noscroll.quote.ShareBottomSheet
import com.noscroll.repository.AnnotationRepository
import com.noscroll.repository.BookRepository
import com.noscroll.repository.HighlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity(), NoScrollPdfViewerFragment.Host {

    private lateinit var metadataBar: View
    private lateinit var metadataText: TextView
    private lateinit var libraryBtn: ImageButton
    private lateinit var zenBtn: ImageButton
    private lateinit var overflowNavBtn: ImageButton

    private lateinit var pdfFragment: NoScrollPdfViewerFragment

    private var currentUri: Uri? = null
    private var currentDocument: PdfDocument? = null
    private var currentMetadata: BookMetadataEntity? = null
    private var currentSelection: ReaderSelection? = null
    private var currentHighlights: List<HighlightEntity> = emptyList()
    private var currentPage = 0
    private var totalPages = 0
    private var zenModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        applyReaderSystemBarContrast()
        cleanupQuoteCache()

        metadataBar = findViewById(R.id.metadata_bar)
        metadataText = findViewById(R.id.book_metadata_text)
        libraryBtn = findViewById(R.id.library_btn)
        zenBtn = findViewById(R.id.zen_btn)
        overflowNavBtn = findViewById(R.id.overflow_nav_btn)

        pdfFragment = supportFragmentManager.findFragmentByTag(PDF_FRAGMENT_TAG) as? NoScrollPdfViewerFragment
            ?: NoScrollPdfViewerFragment().also { fragment ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.pdf_fragment_container, fragment, PDF_FRAGMENT_TAG)
                    .commitNow()
            }

        zenModeEnabled = getPreferences(MODE_PRIVATE).getBoolean(KEY_ZEN_MODE, false)
        setupControls()

        val savedUri = PdfStorage.getSelectedUri(this)
        if (savedUri != null) {
            currentPage = PdfStorage.getSavedPage(this)
            openPdf(savedUri, currentPage)
        } else {
            launchLibrary()
        }
    }

    private fun setupControls() {
        libraryBtn.setOnClickListener { launchLibrary() }
        zenBtn.setOnClickListener { setZenMode(!zenModeEnabled) }
        overflowNavBtn.setOnClickListener { showOverflowMenu() }

        metadataText.setOnLongClickListener {
            currentUri?.let { uri ->
                EditMetadataDialog.show(this, uri, currentMetadata) { metadata ->
                    currentMetadata = metadata
                    renderMetadata(metadata)
                }
            }
            true
        }
        setZenMode(zenModeEnabled, persist = false)
    }

    private fun openPdf(uri: Uri, startPage: Int) {
        try {
            currentUri = uri
            currentDocument = null
            currentSelection = null
            metadataText.text = uri.lastPathSegment?.substringBeforeLast('.') ?: "..."
            pdfFragment.load(uri)
            if (startPage > 0) {
                pdfFragment.scrollToPage(startPage)
            }
        } catch (_: Exception) {
            handleBadUri()
        }
    }

    override fun onPdfLoaded(document: PdfDocument) {
        currentDocument = document
        totalPages = document.pageCount
        pdfFragment.scrollToPage(currentPage)
        loadMetadata(document.uri, allowOnlineOnce = true)
        reloadHighlights()
        currentUri?.let { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                BookRepository.updateProgress(this@PdfViewerActivity, uri.toString(), currentPage, totalPages)
            }
        }
    }

    override fun onPdfLoadError(error: Throwable) {
        handleBadUri()
    }

    override fun onPdfViewportChanged(firstVisiblePage: Int) {
        currentPage = firstVisiblePage.coerceAtLeast(0)
        PdfStorage.savePage(this, currentPage)
        currentUri?.let { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                BookRepository.updateProgress(this@PdfViewerActivity, uri.toString(), currentPage, totalPages)
            }
        }
    }

    override fun onPdfTextSelectionChanged(selection: ReaderSelection?) {
        currentSelection = selection
    }

    override fun onPdfSelectionAction(action: SelectionAction) {
        handleSelectionAction(action)
    }

    override fun onPdfImmersiveRequest(enterImmersive: Boolean) {
        if (zenModeEnabled) setSystemBarsHidden(enterImmersive)
    }

    override fun onPdfPointTapped(pageIndex: Int, pdfX: Float, pdfY: Float) {
        if (currentSelection != null) return
        val hit = currentHighlights.firstOrNull { highlight ->
            highlight.pageIndex == pageIndex &&
                PdfSelectionCodec.decode(highlight.selectionBoundsJson).any { rect ->
                    rect.pageNum == pageIndex && rect.containsWithPadding(pdfX, pdfY, 4f)
                }
        } ?: return
        showHighlightActions(hit)
    }

    private fun handleSelectionAction(action: SelectionAction) {
        val selection = currentSelection ?: return
        when (action) {
            SelectionAction.HIGHLIGHT -> saveHighlight(selection, openNote = false)
            SelectionAction.ANNOTATE -> saveHighlight(selection, openNote = true)
            SelectionAction.QUOTE -> openQuotePreview(selection.text, selection.pageIndex)
            SelectionAction.SHARE -> shareQuote(selection.text, selection.pageIndex)
        }
    }

    private fun saveHighlight(selection: ReaderSelection, openNote: Boolean) {
        showHighlightColorPicker(selection, openNote)
    }

    private fun showHighlightColorPicker(selection: ReaderSelection, openNote: Boolean) {
        val colors = listOf(
            0x80FFEE77.toInt(),
            0x80AAFFD6.toInt(),
            0x80FFB3CC.toInt(),
            0x8099CCFF.toInt()
        )
        val dm = resources.displayMetrics
        val circleSize = (52 * dm.density).toInt()
        val margin = (12 * dm.density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(margin, margin * 2, margin, margin * 2)
        }
        var dialog: AlertDialog? = null
        colors.forEach { color ->
            val displayColor = (color and 0x00FFFFFF) or (0xCC shl 24)
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).also {
                    it.marginStart = margin
                    it.marginEnd = margin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(displayColor)
                }
                setOnClickListener {
                    dialog?.dismiss()
                    saveHighlightWithColor(selection, openNote, color)
                }
            })
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Highlight color")
            .setView(row)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveHighlightWithColor(selection: ReaderSelection, openNote: Boolean, colorArgb: Int) {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            val highlightId = withContext(Dispatchers.IO) {
                HighlightRepository.save(
                    this@PdfViewerActivity,
                    HighlightEntity(
                        bookUri = uri.toString(),
                        pageIndex = selection.pageIndex,
                        startCharIndex = -1,
                        endCharIndex = -1,
                        quoteText = selection.text,
                        selectionBoundsJson = PdfSelectionCodec.encode(selection.bounds),
                        colorArgb = colorArgb
                    )
                )
            }
            reloadHighlights()
            if (openNote) showAnnotationDialog(highlightId, selection.text)
            clearSelection()
        }
    }

    private fun showAnnotationDialog(highlightId: Long, quoteText: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "Note"
        }
        val container = FrameLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        container.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(quoteText.take(48))
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AnnotationRepository.upsert(this@PdfViewerActivity, input.text.toString(), highlightId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reloadHighlights() {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            val coloredBounds = withContext(Dispatchers.IO) {
                currentHighlights = HighlightRepository.getForBook(this@PdfViewerActivity, uri.toString())
                currentHighlights.flatMap { entity ->
                    PdfSelectionCodec.decode(entity.selectionBoundsJson).map { rect ->
                        Pair(rect, entity.colorArgb)
                    }
                }
            }
            pdfFragment.setSavedHighlights(coloredBounds)
        }
    }

    private fun openQuotePreview(quote: String, pageIndex: Int) {
        val uri = currentUri ?: return
        startActivity(
            Intent(this, QuoteCardPreviewActivity::class.java)
                .putExtra(QuoteCardPreviewActivity.EXTRA_QUOTE_TEXT, quote)
                .putExtra(QuoteCardPreviewActivity.EXTRA_BOOK_URI, uri.toString())
                .putExtra(QuoteCardPreviewActivity.EXTRA_PAGE_NUMBER, pageIndex)
        )
        clearSelection()
    }

    private fun shareQuote(quote: String, pageIndex: Int) {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            val metadata = currentMetadata ?: BookMetadataRepository.resolve(this@PdfViewerActivity, uri, currentDocument)
            val bitmap = withContext(Dispatchers.Default) {
                QuoteCardBitmapBuilder.build(
                    QuoteCardSpec(
                        quoteText = quote,
                        bookTitle = metadata.title,
                        author = metadata.author,
                        pageNumber = pageIndex + 1
                    )
                )
            }
            val attribution = "${quote.trim()}\n\n- ${metadata.author}, ${metadata.title}"
            ShareBottomSheet.newInstance(bitmap, attribution).show(supportFragmentManager, "share")
            clearSelection()
        }
    }

    private fun shareCurrentPage() {
        val metadata = currentMetadata ?: return
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${metadata.title} — ${metadata.author}")
            }, "Share"
        ))
    }

    private fun clearSelection() {
        currentSelection = null
        pdfFragment.clearSelection()
        if (zenModeEnabled) setZenMode(true, persist = false)
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, overflowNavBtn)
        popup.menu.add(0, 0, 0, "Highlights")
        popup.menu.add(0, 1, 1, "Go to page")
        popup.menu.add(0, 2, 2, "Share")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showHighlightsDialog()
                1 -> showGotoPageDialog()
                2 -> shareCurrentPage()
            }
            true
        }
        popup.show()
    }

    private fun showHighlightsDialog() {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            val highlights = withContext(Dispatchers.IO) {
                HighlightRepository.getForBook(this@PdfViewerActivity, uri.toString())
            }
            if (highlights.isEmpty()) {
                Toast.makeText(this@PdfViewerActivity, "No highlights yet", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = highlights.map { "p.${it.pageIndex + 1}  ${it.quoteText.take(72)}" }.toTypedArray()
            MaterialAlertDialogBuilder(this@PdfViewerActivity)
                .setTitle("Highlights")
                .setItems(labels) { _, which ->
                    val highlight = highlights[which]
                    MaterialAlertDialogBuilder(this@PdfViewerActivity)
                        .setItems(arrayOf("Go to page", "Share quote", "Delete")) { _, action ->
                            when (action) {
                                0 -> {
                                    currentPage = highlight.pageIndex
                                    pdfFragment.scrollToPage(highlight.pageIndex)
                                    PdfStorage.savePage(this@PdfViewerActivity, highlight.pageIndex)
                                }
                                1 -> openQuotePreview(highlight.quoteText, highlight.pageIndex)
                                2 -> lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        HighlightRepository.delete(this@PdfViewerActivity, highlight.id)
                                    }
                                    reloadHighlights()
                                    showHighlightsDialog()
                                }
                            }
                        }
                        .show()
                }
                .show()
        }
    }

    private fun showHighlightActions(highlight: HighlightEntity) {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf("Change colour", "Edit note", "Share quote", "Delete")) { _, which ->
                when (which) {
                    0 -> recolourHighlight(highlight)
                    1 -> showAnnotationDialog(highlight.id, highlight.quoteText)
                    2 -> openQuotePreview(highlight.quoteText, highlight.pageIndex)
                    3 -> lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            HighlightRepository.delete(this@PdfViewerActivity, highlight.id)
                        }
                        reloadHighlights()
                    }
                }
            }
            .show()
    }

    private fun recolourHighlight(highlight: HighlightEntity) {
        val colors = listOf(
            0x80FFEE77.toInt(),
            0x80AAFFD6.toInt(),
            0x80FFB3CC.toInt(),
            0x8099CCFF.toInt()
        )
        val dm = resources.displayMetrics
        val circleSize = (52 * dm.density).toInt()
        val margin = (12 * dm.density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(margin, margin * 2, margin, margin * 2)
        }
        var dialog: AlertDialog? = null
        colors.forEach { color ->
            val displayColor = (color and 0x00FFFFFF) or (0xCC shl 24)
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).also {
                    it.marginStart = margin
                    it.marginEnd = margin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(displayColor)
                }
                setOnClickListener {
                    dialog?.dismiss()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            HighlightRepository.updateColor(this@PdfViewerActivity, highlight.id, color)
                        }
                        reloadHighlights()
                    }
                }
            })
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Change colour")
            .setView(row)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ocrCurrentPage() {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            Toast.makeText(this@PdfViewerActivity, "Reading page text...", Toast.LENGTH_SHORT).show()
            val ocrSelection = withContext(Dispatchers.IO) {
                recognizeCurrentPage(uri, currentPage)
            }
            if (ocrSelection == null || ocrSelection.text.isBlank()) {
                Toast.makeText(this@PdfViewerActivity, getString(R.string.no_selectable_text), Toast.LENGTH_SHORT).show()
                return@launch
            }
            showOcrSelectionDialog(ocrSelection)
        }
    }

    private fun showOcrSelectionDialog(selection: ReaderSelection) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            setText(selection.text)
        }
        val container = FrameLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        container.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle("OCR page ${selection.pageIndex + 1}")
            .setView(container)
            .setPositiveButton("Quote") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) openQuotePreview(text, selection.pageIndex)
            }
            .setNeutralButton("Highlight") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    saveHighlight(selection.copy(text = text), openNote = false)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun recognizeCurrentPage(uri: Uri, pageIndex: Int): ReaderSelection? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            renderer.openPage(pageIndex).use { page ->
                val bitmapWidth = 1440
                val scale = bitmapWidth.toFloat() / page.width.toFloat()
                val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val recognized = CoverPageOcr.recognize(bitmap) ?: return null
                    val bounds = recognized.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            PdfRect(
                                pageIndex,
                                box.left / scale,
                                box.top / scale,
                                box.right / scale,
                                box.bottom / scale
                            )
                        }
                    ReaderSelection(
                        text = recognized.text.trim(),
                        bounds = bounds,
                        pageIndex = pageIndex
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun loadMetadata(uri: Uri, allowOnlineOnce: Boolean = false, forceOnline: Boolean = false) {
        android.util.Log.d("NoScrollMeta", "loadMetadata called: allowOnlineOnce=$allowOnlineOnce forceOnline=$forceOnline")
        lifecycleScope.launch {
            val coverBitmap = if (allowOnlineOnce || forceOnline) {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(400)
                    val bmp = renderCoverBitmap(uri)
                    android.util.Log.d("NoScrollMeta", "renderCoverBitmap: ${if (bmp != null) "${bmp.width}x${bmp.height}" else "NULL"}")
                    bmp
                }
            } else null
            val metadata = BookMetadataRepository.resolve(
                this@PdfViewerActivity, uri, currentDocument,
                allowOnlineOnce = allowOnlineOnce,
                coverBitmap = coverBitmap,
                forceOnline = forceOnline
            )
            coverBitmap?.recycle()
            android.util.Log.d("NoScrollMeta", "resolve result: title='${metadata.title}' author='${metadata.author}' source='${metadata.source}'")
            currentMetadata = metadata
            renderMetadata(metadata)
        }
    }

    private fun renderCoverBitmap(uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            renderer.openPage(0).use { page ->
                val width = 1080
                val scale = width.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NoScrollMeta", "renderCoverBitmap FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun renderMetadata(metadata: BookMetadataEntity) {
        metadataText.text = "${metadata.title} — ${metadata.author}"
    }

    private fun showGotoPageDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 - $totalPages"
        }
        val container = FrameLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        container.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle("Go to page")
            .setView(container)
            .setPositiveButton("Go") { _, _ ->
                val entered = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val safePage = (entered - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                currentPage = safePage
                pdfFragment.scrollToPage(safePage)
                PdfStorage.savePage(this, safePage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setZenMode(enabled: Boolean, persist: Boolean = true) {
        zenModeEnabled = enabled
        if (persist) getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_ZEN_MODE, enabled).apply()
        metadataBar.visibility = if (enabled) View.GONE else View.VISIBLE
        supportActionBar?.let { if (enabled) it.hide() else it.show() }
        pdfFragment.setZenToolboxVisible(!enabled)
        setSystemBarsHidden(enabled)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun setSystemBarsHidden(hidden: Boolean) {
        if (hidden) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            applyReaderSystemBarContrast()
        }
    }

    private fun applyReaderSystemBarContrast() {
        window.statusBarColor = Color.parseColor("#1B1917")
        window.navigationBarColor = Color.parseColor("#1B1917")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    private fun launchLibrary() {
        startActivity(Intent(this, PdfLibraryActivity::class.java))
    }

    private fun handleBadUri() {
        PdfStorage.clearUri(this)
        Toast.makeText(this, "Could not open PDF. Please choose another file.", Toast.LENGTH_LONG).show()
        launchLibrary()
    }

    private fun cleanupQuoteCache() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        File(cacheDir, "quote_cards").listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PdfStorage.getSelectedUri(this)?.let { uri ->
            currentPage = PdfStorage.getSavedPage(this)
            openPdf(uri, currentPage)
        }
    }

    override fun onPause() {
        super.onPause()
        PdfStorage.savePage(this, currentPage)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (zenModeEnabled) {
            setZenMode(false)
            return
        }
        val instagramIntent = packageManager.getLaunchIntentForPackage("com.instagram.android")
            ?: packageManager.getLaunchIntentForPackage("com.instagram.lite")
        if (instagramIntent != null) {
            instagramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(instagramIntent)
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        private const val PDF_FRAGMENT_TAG = "reader_pdf_fragment"
        private const val KEY_ZEN_MODE = "zen_mode_enabled"
    }
}

private fun PdfRect.containsWithPadding(x: Float, y: Float, padding: Float): Boolean =
    x >= left - padding && x <= right + padding && y >= top - padding && y <= bottom + padding
