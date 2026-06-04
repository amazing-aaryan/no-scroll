package com.noscroll

import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import java.text.Normalizer
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.PdfRect
import androidx.pdf.selection.ContextMenuComponent
import androidx.pdf.selection.SelectionMenuComponent
import androidx.pdf.selection.Selection
import androidx.pdf.selection.model.TextSelection
import androidx.pdf.view.Highlight
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment

class NoScrollPdfViewerFragment : PdfViewerFragment() {
    interface Host {
        fun onPdfLoaded(document: PdfDocument)
        fun onPdfLoadError(error: Throwable)
        fun onPdfViewReady()
        fun onPdfViewportChanged(firstVisiblePage: Int)
        fun onPdfTextSelectionChanged(selection: ReaderSelection?)
        fun onPdfSelectionAction(action: SelectionAction)
        fun onPdfImmersiveRequest(enterImmersive: Boolean)
        fun onPdfPointTapped(pageIndex: Int, pdfX: Float, pdfY: Float)
    }

    private var currentPdfView: PdfView? = null
    private var highlights: List<Highlight> = emptyList()
    private var pageLocations: SparseArray<RectF> = SparseArray()
    private var zoomLevel: Float = 1f
    private var pendingToolboxVisible: Boolean? = null
    private var pendingScrollPage: Int? = null

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        (activity as? Host)?.onPdfLoaded(document)
        pendingScrollPage?.let { page -> scrollToPage(page) }
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        (activity as? Host)?.onPdfLoadError(error)
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        super.onRequestImmersiveMode(enterImmersive)
        (activity as? Host)?.onPdfImmersiveRequest(enterImmersive)
    }

    @androidx.annotation.OptIn(ExperimentalPdfApi::class)
    @ExperimentalPdfApi
    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        currentPdfView = pdfView
        pendingToolboxVisible?.let { visible ->
            try { isToolboxVisible = visible } catch (_: Exception) {}
            pendingToolboxVisible = null
        }
        pdfView.addOnViewportChangedListener(
            object : PdfView.OnViewportChangedListener {
                override fun onViewportChanged(
                    firstVisiblePage: Int,
                    visiblePagesCount: Int,
                    pageLocations: SparseArray<RectF>,
                    zoomLevel: Float
                ) {
                    this@NoScrollPdfViewerFragment.pageLocations = SparseArray<RectF>().apply {
                        for (i in 0 until pageLocations.size()) {
                            put(pageLocations.keyAt(i), RectF(pageLocations.valueAt(i)))
                        }
                    }
                    this@NoScrollPdfViewerFragment.zoomLevel = zoomLevel.coerceAtLeast(0.01f)
                    (activity as? Host)?.onPdfViewportChanged(firstVisiblePage)
                }
            }
        )
        pdfView.addOnSelectionChangedListener(
            object : PdfView.OnSelectionChangedListener {
                override fun onSelectionChanged(newSelection: Selection?) {
                    val textSelection = newSelection as? TextSelection
                    val readerSelection = textSelection
                        ?.takeIf { it.text.isNotBlank() }
                        ?.let {
                            ReaderSelection(
                                text = Normalizer.normalize(it.text.toString(), Normalizer.Form.NFKC),
                                bounds = it.bounds,
                                pageIndex = it.bounds.firstOrNull()?.pageNum ?: pdfView.firstVisiblePage
                            )
                        }
                    (activity as? Host)?.onPdfTextSelectionChanged(readerSelection)
                }
            }
        )
        try {
            pdfView.addSelectionMenuItemPreparer(
                object : PdfView.SelectionMenuItemPreparer {
                    override fun onPrepareSelectionMenuItems(components: MutableList<ContextMenuComponent>) {
                        components.add(selectionMenuItem(SelectionAction.HIGHLIGHT, "Highlight"))
                        components.add(selectionMenuItem(SelectionAction.ANNOTATE, "Annotate"))
                        components.add(selectionMenuItem(SelectionAction.QUOTE, "Quote"))
                        components.add(selectionMenuItem(SelectionAction.SHARE, "Share"))
                    }
                }
            )
        } catch (_: Exception) { }
        pdfView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                notifyPdfPointTap(event.x, event.y)
            }
            false
        }
        if (highlights.isNotEmpty()) {
            try { pdfView.setHighlights(highlights) } catch (_: IllegalStateException) { }
        }
        (activity as? Host)?.onPdfViewReady()
    }

    fun load(uri: Uri) {
        documentUri = uri
    }

    fun scrollToPage(page: Int) {
        val view = currentPdfView
        if (view == null) {
            pendingScrollPage = page
            return
        }
        pendingScrollPage = null
        view.post {
            try { currentPdfView?.scrollToPage(page) } catch (_: Exception) {}
        }
    }

    fun clearSelection() {
        currentPdfView?.clearCurrentSelection()
    }

    fun setSavedHighlights(coloredBounds: List<Pair<PdfRect, Int>>) {
        highlights = coloredBounds.map { (rect, color) ->
            val c = migrateColor(color)
            Highlight(rect, (c and 0x00FFFFFF) or (0xDD shl 24))
        }
        // PdfView.setHighlights throws if called before the document is loaded.
        // onPdfViewReady fires before onLoadDocumentSuccess, so guard here.
        try { currentPdfView?.setHighlights(highlights) } catch (_: IllegalStateException) { }
    }

    private fun migrateColor(color: Int): Int = when (color) {
        0x80FFE566.toInt(), 0x80FFF176.toInt(), 0x80FFF59D.toInt() -> 0x80FFEE77.toInt()
        0x8066CC77.toInt(), 0x80AED581.toInt(), 0x80DCEDC8.toInt() -> 0x80AAFFD6.toInt()
        0x80FF88CC.toInt(), 0x80F48FB1.toInt(), 0x80F8BBD0.toInt() -> 0x80FFB3CC.toInt()
        0x804FC3F7.toInt(), 0x8090CAF9.toInt(), 0x80BBDEFB.toInt() -> 0x8099CCFF.toInt()
        else -> color
    }

    fun setZenToolboxVisible(visible: Boolean) {
        try {
            isToolboxVisible = visible
            pendingToolboxVisible = null
        } catch (_: UninitializedPropertyAccessException) {
            pendingToolboxVisible = visible
        }
    }

    private fun selectionMenuItem(action: SelectionAction, label: String): ContextMenuComponent =
        SelectionMenuComponent(
            key = action.name,
            label = label,
            contentDescription = label
        ) {
            (activity as? Host)?.onPdfSelectionAction(action)
            close()
        }

    private fun notifyPdfPointTap(x: Float, y: Float) {
        for (i in 0 until pageLocations.size()) {
            val page = pageLocations.keyAt(i)
            val pageRect = pageLocations.valueAt(i)
            if (pageRect.contains(x, y)) {
                val pdfX = (x - pageRect.left) / zoomLevel
                val pdfY = (y - pageRect.top) / zoomLevel
                (activity as? Host)?.onPdfPointTapped(page, pdfX, pdfY)
                return
            }
        }
    }

    companion object {
        const val DEFAULT_HIGHLIGHT_COLOR: Int = 0x80FFEE77.toInt()
    }
}

data class ReaderSelection(
    val text: String,
    val bounds: List<PdfRect>,
    val pageIndex: Int
)

enum class SelectionAction {
    HIGHLIGHT,
    ANNOTATE,
    QUOTE,
    SHARE
}
