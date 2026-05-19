package com.noscroll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noscroll.data.BookMetadataEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.metadata.CoverPageOcr
import com.noscroll.metadata.EditMetadataDialog
import com.noscroll.quote.QuoteCardPreviewActivity
import com.noscroll.repository.BookRepository
import com.noscroll.repository.HighlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var metadataBar: View
    private lateinit var metadataText: TextView
    private lateinit var libraryBtn: ImageButton
    private lateinit var zenBtn: ImageButton
    private lateinit var overflowNavBtn: ImageButton
    private lateinit var pdfContainer: FrameLayout
    private lateinit var pdfRecyclerView: RecyclerView

    private var pdfRenderer: PdfRenderer? = null
    private var pdfParcelFd: ParcelFileDescriptor? = null

    private var currentUri: Uri? = null
    private var currentMetadata: BookMetadataEntity? = null
    private var currentPage = 0
    private var totalPages = 0
    private var zenModeEnabled = false
    private var isSliding = false

    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isSliding) return false
                e1 ?: return false
                val dx = abs(e2.x - e1.x)
                val dy = abs(e2.y - e1.y)
                val isHorizontal = dx > dy && (abs(velocityX) > MIN_FLIP_VELOCITY || dx > dp(56).toFloat())
                if (isHorizontal) {
                    val dir = if (velocityX < 0) 1 else -1
                    val targetPage = currentPage + dir
                    if (targetPage in 0 until totalPages) {
                        goToPage(targetPage, dir)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pdf_viewer)
        applyReaderSystemBarContrast()
        cleanupQuoteCache()

        metadataBar    = findViewById(R.id.metadata_bar)
        metadataText   = findViewById(R.id.book_metadata_text)
        libraryBtn     = findViewById(R.id.library_btn)
        zenBtn         = findViewById(R.id.zen_btn)
        overflowNavBtn = findViewById(R.id.overflow_nav_btn)
        pdfContainer   = findViewById(R.id.pdf_container)
        pdfRecyclerView = findViewById(R.id.pdf_recycler_view)

        pdfRecyclerView.layoutManager = LinearLayoutManager(this)
        PagerSnapHelper().attachToRecyclerView(pdfRecyclerView)
        // Intercept horizontal touches so RecyclerView doesn't also scroll vertically.
        pdfRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var startX = 0f
            private var startY = 0f
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startX = e.x; startY = e.y }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(e.x - startX)
                        val dy = abs(e.y - startY)
                        if (dx > dy && dx > dp(8)) return true
                    }
                }
                return false
            }
        })
        pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val pos = lm.findFirstCompletelyVisibleItemPosition()
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?: lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos != currentPage) {
                    currentPage = pos
                    PdfStorage.savePage(this@PdfViewerActivity, pos)
                    updateProgressAsync(pos)
                }
            }
        })

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
        closePdfRenderer()
        currentUri = uri
        metadataText.text = uri.lastPathSegment?.substringBeforeLast('.') ?: "..."
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: run { handleBadUri(); return }
            pdfParcelFd = pfd
            val renderer = PdfRenderer(pfd)
            pdfRenderer = renderer
            totalPages = renderer.pageCount

            val screenWidth = resources.displayMetrics.widthPixels
            pdfRecyclerView.adapter = PdfPageAdapter(renderer, screenWidth)

            val safeStart = startPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            (pdfRecyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(safeStart, 0)
            currentPage = safeStart

            loadMetadata(uri, allowOnlineOnce = true)
            updateProgressAsync(safeStart)
        } catch (_: Exception) {
            handleBadUri()
        }
    }

    private fun closePdfRenderer() {
        pdfRecyclerView.adapter = null
        try { pdfRenderer?.close() } catch (_: Exception) {}
        pdfRenderer = null
        try { pdfParcelFd?.close() } catch (_: Exception) {}
        pdfParcelFd = null
    }

    // ── Page navigation ────────────────────────────────────────────────────────

    private fun goToPage(targetPage: Int, direction: Int) {
        val safe = targetPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (safe == currentPage || isSliding) return
        isSliding = true

        val uri = currentUri ?: run { isSliding = false; return }
        val fromPage = currentPage
        val cw = pdfContainer.width.toFloat().coerceAtLeast(1f)
        val ch = pdfContainer.height.toFloat().coerceAtLeast(1f)

        lifecycleScope.launch {
            val exitBmp  = renderPageBitmap(uri, fromPage, cw.toInt(), ch.toInt())
            val enterBmp = renderPageBitmap(uri, safe,     cw.toInt(), ch.toInt())

            if (exitBmp == null || enterBmp == null) {
                exitBmp?.recycle(); enterBmp?.recycle()
                isSliding = false
                pdfRecyclerView.smoothScrollToPosition(safe)
                return@launch
            }

            val offscreen = if (direction > 0) cw else -cw

            val matchAll = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            val overlay = FrameLayout(this@PdfViewerActivity).apply {
                setBackgroundColor(Color.parseColor("#171615"))
            }

            val exitView = ImageView(this@PdfViewerActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(exitBmp)
            }
            val enterView = ImageView(this@PdfViewerActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(enterBmp)
                translationX = offscreen
            }

            overlay.addView(exitView,  matchAll)
            overlay.addView(enterView, matchAll)
            pdfContainer.addView(overlay, matchAll)
            pdfRecyclerView.suppressLayout(true)

            val interp = DecelerateInterpolator()
            val exitAnim  = ObjectAnimator.ofFloat(exitView,  "translationX", 0f, -offscreen).apply {
                duration = 300L; interpolator = interp
            }
            val enterAnim = ObjectAnimator.ofFloat(enterView, "translationX", offscreen, 0f).apply {
                duration = 300L; interpolator = interp
            }

            AnimatorSet().apply {
                playTogether(exitAnim, enterAnim)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (pdfRecyclerView.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(safe, 0)
                        pdfRecyclerView.suppressLayout(false)
                        pdfRecyclerView.post {
                            pdfContainer.removeView(overlay)
                            exitBmp.recycle()
                            enterBmp.recycle()
                            currentPage = safe
                            PdfStorage.savePage(this@PdfViewerActivity, safe)
                            updateProgressAsync(safe)
                            isSliding = false
                        }
                    }
                })
                start()
            }
        }
    }

    private suspend fun renderPageBitmap(uri: Uri, pageIndex: Int, containerWidth: Int, containerHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
                renderer = PdfRenderer(pfd)
                val safe = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safe).use { page ->
                    val scale = containerWidth.toFloat() / page.width.coerceAtLeast(1)
                    val pageH = (page.height * scale).toInt().coerceAtLeast(1)
                    val pageBmp = Bitmap.createBitmap(containerWidth, pageH, Bitmap.Config.ARGB_8888)
                    pageBmp.eraseColor(Color.WHITE)
                    page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBmp
                }
            } catch (_: Exception) { null } finally {
                renderer?.close()
                pfd?.close()
            }
        }

    private fun updateProgressAsync(page: Int) {
        currentUri?.let { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                BookRepository.updateProgress(this@PdfViewerActivity, uri.toString(), page, totalPages)
            }
        }
    }

    // ── Highlights ─────────────────────────────────────────────────────────────

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
                                0 -> jumpToPage(highlight.pageIndex)
                                1 -> openQuotePreview(highlight.quoteText, highlight.pageIndex)
                                2 -> lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        HighlightRepository.delete(this@PdfViewerActivity, highlight.id)
                                    }
                                    showHighlightsDialog()
                                }
                            }
                        }
                        .show()
                }
                .show()
        }
    }

    // ── Quotes / sharing ───────────────────────────────────────────────────────

    private fun openQuotePreview(quote: String, pageIndex: Int) {
        val uri = currentUri ?: return
        startActivity(
            Intent(this, QuoteCardPreviewActivity::class.java)
                .putExtra(QuoteCardPreviewActivity.EXTRA_QUOTE_TEXT, quote)
                .putExtra(QuoteCardPreviewActivity.EXTRA_BOOK_URI, uri.toString())
                .putExtra(QuoteCardPreviewActivity.EXTRA_PAGE_NUMBER, pageIndex)
        )
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

    // ── OCR ───────────────────────────────────────────────────────────────────

    private fun ocrCurrentPage() {
        val uri = currentUri ?: return
        lifecycleScope.launch {
            Toast.makeText(this@PdfViewerActivity, "Reading page text...", Toast.LENGTH_SHORT).show()
            val ocrSelection = withContext(Dispatchers.IO) { recognizeCurrentPage(uri, currentPage) }
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
                    ReaderSelection(text = recognized.text.trim(), bounds = emptyList(), pageIndex = pageIndex)
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

    // ── Metadata ──────────────────────────────────────────────────────────────

    private fun loadMetadata(uri: Uri, allowOnlineOnce: Boolean = false, forceOnline: Boolean = false) {
        lifecycleScope.launch {
            val coverBitmap = if (allowOnlineOnce || forceOnline) {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(400)
                    renderCoverBitmap(uri)
                }
            } else null
            val metadata = BookMetadataRepository.resolve(
                this@PdfViewerActivity, uri, null,
                allowOnlineOnce = allowOnlineOnce,
                coverBitmap = coverBitmap,
                forceOnline = forceOnline
            )
            coverBitmap?.recycle()
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
        } catch (_: Exception) { null } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun renderMetadata(metadata: BookMetadataEntity) {
        metadataText.text = "${metadata.title} — ${metadata.author}"
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    private fun jumpToPage(page: Int) {
        val safe = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        currentPage = safe
        (pdfRecyclerView.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(safe, 0)
        PdfStorage.savePage(this, safe)
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
                jumpToPage(entered - 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, overflowNavBtn)
        popup.menu.add(0, 0, 0, "Highlights")
        popup.menu.add(0, 1, 1, "Go to page")
        popup.menu.add(0, 2, 2, "OCR page")
        popup.menu.add(0, 3, 3, "Share")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showHighlightsDialog()
                1 -> showGotoPageDialog()
                2 -> ocrCurrentPage()
                3 -> shareCurrentPage()
            }
            true
        }
        popup.show()
    }

    // ── Zen mode ───────────────────────────────────────────────────────────────

    private fun setZenMode(enabled: Boolean, persist: Boolean = true) {
        zenModeEnabled = enabled
        if (persist) getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_ZEN_MODE, enabled).apply()
        metadataBar.visibility = if (enabled) View.GONE else View.VISIBLE
        supportActionBar?.let { if (enabled) it.hide() else it.show() }
        setSystemBarsHidden(enabled)
    }

    // ── System bars ────────────────────────────────────────────────────────────

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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun launchLibrary() { startActivity(Intent(this, PdfLibraryActivity::class.java)) }

    private fun handleBadUri() {
        PdfStorage.clearUri(this)
        Toast.makeText(this, "Could not open PDF. Please choose another file.", Toast.LENGTH_LONG).show()
        launchLibrary()
    }

    private fun cleanupQuoteCache() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        File(cacheDir, "quote_cards").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

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

    override fun onDestroy() {
        super.onDestroy()
        closePdfRenderer()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (zenModeEnabled) { setZenMode(false); return }
        val instagramIntent = packageManager.getLaunchIntentForPackage("com.instagram.android")
            ?: packageManager.getLaunchIntentForPackage("com.instagram.lite")
        instagramIntent?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        private const val KEY_ZEN_MODE = "zen_mode_enabled"
        private const val MIN_FLIP_VELOCITY = 250f
    }
}

