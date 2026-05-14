package com.noscroll

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.noscroll.databinding.ActivityPdfLibraryBinding

class PdfLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfLibraryBinding
    private lateinit var adapter: PdfLibraryAdapter

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = resolveDisplayName(uri)
        PdfStorage.addToLibrary(this, uri.toString(), name)
        refreshList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PdfLibraryAdapter(
            onSelect = { entry ->
                PdfStorage.setSelected(this, entry.uri)
                val intent = Intent(this, PdfViewerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            },
            onDelete = { entry ->
                PdfStorage.removeFromLibrary(this, entry.uri)
                refreshList()
            },
            onAddNew = { launchPicker() }
        )

        binding.libraryRecycler.layoutManager = GridLayoutManager(this, 2)
        binding.libraryRecycler.adapter = adapter

        binding.addPdfFab.hide()

        migrateLegacyUri()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun migrateLegacyUri() {
        val legacy = PdfStorage.getSavedUri(this) ?: return
        val library = PdfStorage.getLibrary(this)
        if (library.none { it.uri == legacy.toString() }) {
            val name = resolveDisplayName(legacy)
            PdfStorage.addToLibrary(this, legacy.toString(), name)
            PdfStorage.setSelected(this, legacy.toString())
        }
    }

    private fun refreshList() {
        val library = PdfStorage.getLibrary(this)
        val selected = PdfStorage.getSelectedUri(this)?.toString()
        adapter.submitList(library, selected)
    }

    private fun launchPicker() {
        try {
            pickPdf.launch(arrayOf("application/pdf"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment ?: "Unknown PDF"
        } catch (e: Exception) {
            "Unknown PDF"
        }
    }
}
