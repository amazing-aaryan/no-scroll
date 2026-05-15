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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur
import androidx.lifecycle.lifecycleScope
import androidx.pdf.PdfDocument
import androidx.pdf.PdfRect
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.noscroll.data.BookMetadataEntity
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
import com.noscroll.repository.HighlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity(), NoScrollPdfViewerFragment.Host {

    private lateinit var changeFab: FloatingActionButton
    private lateinit var gotoPageFab: FloatingActionButton
    private lateinit var makeQuoteFab: FloatingActionButton
    private lateinit var pageSeekbar: VerticalSeekBar
    private lateinit var metadataBar: View
    private lateinit var metadataText: TextView
    private lateinit var metadataLookupButton: MaterialButton
    private lateinit var selectionRail: View
    private lateinit var zenExitHandle: FloatingActionButton
    private lateinit var overflowMenuBtn: ImageButton
    private lateinit var readerBottomBar: View
    private lateinit var blurTopBar: BlurView
    private lateinit var blurBottomBar: BlurView
    private lateinit var blurPill: BlurView

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
        cleanupQuoteCache()

        changeFab = findViewById(R.id.change_pdf_fab)
        gotoPageFab = findViewById(R.id.goto_page_fab)
        makeQuoteFab = findViewById(R.id.make_quote_fab)
        pageSeekbar = findViewById(R.id.page_seekbar)
        metadataBar = findViewById(R.id.metadata_bar)
        metadataText = findViewById(R.id.book_metadata_text)
        metadataLookupButton = findViewById(R.id.metadata_lookup_btn)
        selectionRail = findViewById(R.id.selection_action_rail)
        zenExitHandle = findViewById(R.id.zen_exit_handle)
        overflowMenuBtn = findViewById(R.id.overflow_menu_btn)
        readerBottomBar = findViewById(R.id.reader_bottom_bar)
        blurTopBar = findViewById(R.id.blur_top_bar)
        blurBottomBar = findViewById(R.id.blur_bottom_bar)
        blurPill = selectionRail as BlurView
        setupBlurViews()

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

    @Suppress("DEPRECATION")
    private fun setupBlurViews() {
        val rootView = window.decorView as android.view.ViewGroup
        val darkNavyOverlay = Color.parseColor("#B0000810")
        val pillOverlay = Color.parseColor("#90101520")

        fun makeAlgo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffectBlur()
        } else {
            RenderScriptBlur(this)
        }

        blurTopBar.setupWith(rootView, makeAlgo())
            .setBlurRadius(22f)
            .setOverlayColor(darkNavyOverlay)
            .setBlurAutoUpdate(true)

        blurBottomBar.setupWith(rootView, makeAlgo())
            .setBlurRadius(22f)
            .setOverlayColor(darkNavyOverlay)
            .setBlurAutoUpdate(true)

        blurPill.setupWith(rootView, makeAlgo())
            .setBlurRadius(18f)
            .setOverlayColor(pillOverlay)
            .setBlurAutoUpdate(true)
    }

    private fun setupControls() {
        pageSeekbar.onProgressChanged = { progress, fromUser ->
            if (fromUser) {
                currentPage = progress
                pdfFragment.scrollToPage(progress)
                PdfStorage.savePage(this, progress)
            }
        }

        changeFab.setOnClickListener { launchLibrary() }
        gotoPageFab.setOnClickListener { showGotoPageDialog() }
        makeQuoteFab.setOnClickListener { showManualQuoteDialog() }
        overflowMenuBtn.setOnClickListener { showOverflowMenu() }
        zenExitHandle.setOnClickListener { setZenMode(false) }
        metadataLookupButton.setOnClickListener { confirmOnlineLookup() }

        metadataText.setOnLongClickListener {
            currentUri?.let { uri ->
                EditMetadataDialog.show(this, uri, currentMetadata) { metadata ->
                    currentMetadata = metadata
                    renderMetadata(metadata)
                }
            }
            true
        }

        findViewById<MaterialButton>(R.id.action_highlight).setOnClickListener {
            handleSelectionAction(SelectionAction.HIGHLIGHT)
        }
        findViewById<MaterialButton>(R.id.action_annotate).setOnClickListener {
            handleSelectionAction(SelectionAction.ANNOTATE)
        }
        findViewById<MaterialButton>(R.id.action_quote).setOnClickListener {
            handleSelectionAction(SelectionAction.QUOTE)
        }
        findViewById<MaterialButton>(R.id.action_share).setOnClickListener {
            handleSelectionAction(SelectionAction.SHARE)
        }
        setZenMode(zenModeEnabled, persist = false)
    }

    private fun openPdf(uri: Uri, startPage: Int) {
        try {
            currentUri = uri
            currentDocument = null
            currentSelection = null
            selectionRail.visibility = View.GONE
            metadataText.text = "Identifying book..."
            metadataLookupButton.visibility = View.GONE
            pdfFragment.load(uri)
            if (startPage > 0) {
                pdfFragment.scrollToPage(startPage)
            }
            loadMetadata(uri, allowOnlineOnce = false)
        } catch (_: Exception) {
            handleBadUri()
        }
    }

    override fun onPdfLoaded(document: PdfDocument) {
        currentDocument = document
        totalPages = document.pageCount
        pageSeekbar.max = (totalPages - 1).coerceAtLeast(1)
        pageSeekbar.progress = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        pageSeekbar.visibility = if (zenModeEnabled) View.INVISIBLE else View.VISIBLE
        pdfFragment.scrollToPage(pageSeekbar.progress)
        loadMetadata(document.uri, allowOnlineOnce = false)
        reloadHighlights()
    }

    override fun onPdfLoadError(error: Throwable) {
        handleBadUri()
    }

    override fun onPdfViewportChanged(firstVisiblePage: Int) {
        currentPage = firstVisiblePage.coerceAtLeast(0)
        PdfStorage.savePage(this, currentPage)
        if (!pageSeekbar.isDragging) {
            pageSeekbar.progress = currentPage.coerceIn(0, pageSeekbar.max)
            if (!zenModeEnabled) pageSeekbar.showAndFade()
        }
    }

    override fun onPdfTextSelectionChanged(selection: ReaderSelection?) {
        currentSelection = selection
        selectionRail.visibility = if (selection == null) View.GONE else View.VISIBLE
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
            0x80FFE566.toInt(),
            0x8066CC77.toInt(),
            0x80FF88CC.toInt(),
            0x804FC3F7.toInt()
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
        MaterialAlertDialogBuilder(this)
            .setTitle(quoteText.take(48))
            .setView(input)
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

    private fun clearSelection() {
        currentSelection = null
        selectionRail.visibility = View.GONE
        pdfFragment.clearSelection()
        if (zenModeEnabled) setZenMode(true, persist = false)
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
                    currentPage = highlight.pageIndex
                    pdfFragment.scrollToPage(highlight.pageIndex)
                    PdfStorage.savePage(this@PdfViewerActivity, highlight.pageIndex)
                    showHighlightActions(highlight)
                }
                .show()
        }
    }

    private fun showHighlightActions(highlight: HighlightEntity) {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf("Edit note", "Share quote", "Delete")) { _, which ->
                when (which) {
                    0 -> showAnnotationDialog(highlight.id, highlight.quoteText)
                    1 -> openQuotePreview(highlight.quoteText, highlight.pageIndex)
                    2 -> lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            HighlightRepository.delete(this@PdfViewerActivity, highlight.id)
                        }
                        reloadHighlights()
                    }
                }
            }
            .show()
    }

    private fun exportHighlights() {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            val highlights = withContext(Dispatchers.IO) {
                HighlightRepository.getForBook(this@PdfViewerActivity, uri.toString())
            }
            if (highlights.isEmpty()) {
                Toast.makeText(this@PdfViewerActivity, "No highlights to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val metadata = currentMetadata
            val title = metadata?.let { "${it.title} - ${it.author}" } ?: "NoScroll highlights"
            val nativeAvailable = PdfHighlightExporter.canWriteNativeHighlights()
            val options = if (nativeAvailable) {
                arrayOf("Annotated PDF", "Highlights text")
            } else {
                arrayOf("Highlights text")
            }
            MaterialAlertDialogBuilder(this@PdfViewerActivity)
                .setTitle("Export highlights")
                .setItems(options) { _, which ->
                    lifecycleScope.launch {
                        try {
                            if (nativeAvailable && which == 0) {
                                val exportUri = withContext(Dispatchers.IO) {
                                    PdfHighlightExporter.exportAnnotatedPdf(this@PdfViewerActivity, uri, highlights)
                                }
                                PdfHighlightExporter.shareUri(
                                    this@PdfViewerActivity,
                                    exportUri,
                                    "application/pdf",
                                    "Export annotated PDF"
                                )
                            } else {
                                val exportUri = withContext(Dispatchers.IO) {
                                    PdfHighlightExporter.exportHighlightsText(this@PdfViewerActivity, title, highlights)
                                }
                                PdfHighlightExporter.shareUri(
                                    this@PdfViewerActivity,
                                    exportUri,
                                    "text/plain",
                                    "Export highlights"
                                )
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@PdfViewerActivity,
                                "Could not export highlights: ${e.message ?: "unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
        }
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
        MaterialAlertDialogBuilder(this)
            .setTitle("OCR page ${selection.pageIndex + 1}")
            .setView(input)
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

    private fun loadMetadata(uri: Uri, allowOnlineOnce: Boolean = false) {
        lifecycleScope.launch {
            val metadata = BookMetadataRepository.resolve(this@PdfViewerActivity, uri, currentDocument, allowOnlineOnce)
            currentMetadata = metadata
            renderMetadata(metadata)
        }
    }

    private fun renderMetadata(metadata: BookMetadataEntity) {
        val review = if (metadata.confidence in 0.01f..0.49f) "  Review" else ""
        metadataText.text = "${metadata.title} - ${metadata.author}$review"
        metadataLookupButton.visibility =
            if (metadata.source == "manual" && !MetadataLookupPrefs.isOnlineLookupEnabled(this)) View.VISIBLE else View.GONE
    }

    private fun confirmOnlineLookup() {
        val uri = currentUri ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Look up book info online?")
            .setMessage("This may send an ISBN, filename tokens, or a short cover-page OCR snippet to book lookup services. It is optional.")
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

    private fun showGotoPageDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 - $totalPages"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Go to page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val entered = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val safePage = (entered - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                currentPage = safePage
                pageSeekbar.progress = safePage
                pageSeekbar.showAndFade()
                pdfFragment.scrollToPage(safePage)
                PdfStorage.savePage(this, safePage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualQuoteDialog() {
        val uri = currentUri ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            hint = "Quote text"
        }
        MaterialAlertDialogBuilder(this)
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

    private fun setZenMode(enabled: Boolean, persist: Boolean = true) {
        zenModeEnabled = enabled
        if (persist) getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_ZEN_MODE, enabled).apply()
        metadataBar.visibility = if (enabled) View.GONE else View.VISIBLE
        readerBottomBar.visibility = if (enabled) View.GONE else View.VISIBLE
        pageSeekbar.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        zenExitHandle.visibility = if (enabled) View.VISIBLE else View.GONE
        supportActionBar?.let { if (enabled) it.hide() else it.show() }
        pdfFragment.setZenToolboxVisible(!enabled)
        setSystemBarsHidden(enabled)
    }

    private fun showOverflowMenu() {
        val items = arrayOf(
            getString(R.string.highlights),
            getString(R.string.export_highlights),
            if (zenModeEnabled) getString(R.string.exit_zen_mode) else getString(R.string.zen_mode)
        )
        MaterialAlertDialogBuilder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHighlightsDialog()
                    1 -> exportHighlights()
                    2 -> setZenMode(!zenModeEnabled)
                }
            }
            .show()
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        if (hidden) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
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
