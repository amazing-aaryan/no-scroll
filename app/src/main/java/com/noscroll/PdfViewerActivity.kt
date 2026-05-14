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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var changeFab: FloatingActionButton
    private lateinit var gotoPageFab: FloatingActionButton
    private lateinit var pageSeekbar: SeekBar

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var totalPages = 0
    private var seekbarTracking = false

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

        recyclerView = findViewById(R.id.pdf_recycler)
        changeFab = findViewById(R.id.change_pdf_fab)
        gotoPageFab = findViewById(R.id.goto_page_fab)
        pageSeekbar = findViewById(R.id.page_seekbar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        PagerSnapHelper().attachToRecyclerView(recyclerView)

        // Translate seekbar to right edge after layout (rotation 270° makes width→height, height→width)
        pageSeekbar.post {
            val screenWidth = resources.displayMetrics.widthPixels
            pageSeekbar.translationX = screenWidth / 2f - pageSeekbar.height / 2f
        }

        pageSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) recyclerView.scrollToPosition(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) { seekbarTracking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) { seekbarTracking = false }
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val page = lm.findFirstCompletelyVisibleItemPosition()
                    if (page >= 0) {
                        currentPage = page
                        PdfStorage.savePage(this@PdfViewerActivity, page)
                        if (!seekbarTracking) pageSeekbar.progress = page
                    }
                }
            }
        })

        changeFab.setOnClickListener { launchLibrary() }
        gotoPageFab.setOnClickListener { showGotoPageDialog() }

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
                val entered = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val safePage = (entered - 1).coerceIn(0, totalPages - 1)
                recyclerView.scrollToPosition(safePage)
                pageSeekbar.progress = safePage
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

    private fun launchPicker() {
        try {
            pickPdf.launch(arrayOf("application/pdf"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdf(uri: Uri, startPage: Int) {
        closeCurrentPdf()
        try {
            parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                ?: run { handleBadUri(); return }
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount
            val screenWidth = resources.displayMetrics.widthPixels
            recyclerView.adapter = PdfPageAdapter(pdfRenderer!!, screenWidth)
            val safePage = startPage.coerceIn(0, totalPages - 1)
            recyclerView.scrollToPosition(safePage)
            pageSeekbar.max = (totalPages - 1).coerceAtLeast(1)
            pageSeekbar.progress = safePage
            pageSeekbar.visibility = View.VISIBLE
        } catch (e: Exception) {
            handleBadUri()
        }
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
        totalPages = 0
        pageSeekbar.visibility = View.INVISIBLE
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
