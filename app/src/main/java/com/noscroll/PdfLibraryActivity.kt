package com.noscroll

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.BookEntity
import com.noscroll.data.BookMetadataEntity
import com.noscroll.metadata.BookMetadataRepository
import com.noscroll.repository.BookRepository
import androidx.compose.runtime.LaunchedEffect
import com.noscroll.tutorial.LibraryTutorialSteps
import com.noscroll.tutorial.TutorialController
import com.noscroll.tutorial.TutorialPrefs
import com.noscroll.ui.LibraryScreen
import com.noscroll.ui.NoScrollTheme
import kotlinx.coroutines.launch

class PdfLibraryActivity : AppCompatActivity() {

    private val tutorialController = TutorialController()
    private lateinit var tutorialPrefs: TutorialPrefs

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = resolveDisplayName(uri)
        lifecycleScope.launch {
            BookRepository.importBook(this@PdfLibraryActivity, uri, name)
            try {
                BookMetadataRepository.resolve(
                    context = this@PdfLibraryActivity,
                    uri = uri,
                    document = null,
                    allowOnlineOnce = true
                )
            } catch (_: Exception) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            BookRepository.migrateLegacyLibrary(this@PdfLibraryActivity)
            identifyWeakLibraryMetadata()
        }
        tutorialPrefs = TutorialPrefs(this)
        setContent {
            NoScrollTheme {
                LaunchedEffect(Unit) {
                    if (tutorialPrefs.hasOptedIn() && !tutorialPrefs.isLibraryDone()) {
                        kotlinx.coroutines.delay(300)
                        tutorialController.start(LibraryTutorialSteps)
                        tutorialController.onDone = { tutorialPrefs.markLibraryDone() }
                    }
                }
                val books = BookRepository.observeBooks(this).collectAsStateWithLifecycle(emptyList()).value
                val metadata = AnnotationDatabase.getInstance(this)
                    .bookMetadataDao()
                    .observeAll()
                    .collectAsStateWithLifecycle(emptyList())
                    .value
                val highlights = AnnotationDatabase.getInstance(this)
                    .highlightDao()
                    .observeAll()
                    .collectAsStateWithLifecycle(emptyList())
                    .value
                LibraryScreen(
                    books = books,
                    metadata = metadata,
                    highlights = highlights,
                    onImport = { launchPicker() },
                    onOpen = { book ->
                        lifecycleScope.launch {
                            BookRepository.openBook(this@PdfLibraryActivity, book.bookUri)
                            startActivity(
                                Intent(this@PdfLibraryActivity, PdfViewerActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                            finish()
                        }
                    },
                    onFavorite = { book ->
                        lifecycleScope.launch {
                            BookRepository.setFavorite(this@PdfLibraryActivity, book.bookUri, !book.isFavorite)
                        }
                    },
                    onIdentify = { book ->
                        lifecycleScope.launch {
                            Toast.makeText(this@PdfLibraryActivity, "Identifying book...", Toast.LENGTH_SHORT).show()
                            try {
                                val metadata = BookMetadataRepository.resolve(
                                    context = this@PdfLibraryActivity,
                                    uri = Uri.parse(book.bookUri),
                                    document = null,
                                    allowOnlineOnce = true
                                )
                                Toast.makeText(
                                    this@PdfLibraryActivity,
                                    "${metadata.title} - ${metadata.author}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@PdfLibraryActivity,
                                    "Could not identify this PDF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDelete = { book ->
                        lifecycleScope.launch { BookRepository.delete(this@PdfLibraryActivity, book.bookUri) }
                    },
                    onNotebook = {
                        startActivity(Intent(this@PdfLibraryActivity, NotebookActivity::class.java))
                    },
                    tutorialController = tutorialController,
                    onHelp = {
                        tutorialPrefs.restartFrom()
                        tutorialController.start(LibraryTutorialSteps)
                        tutorialController.onDone = { tutorialPrefs.markLibraryDone() }
                    }
                )
            }
        }
    }

    private fun launchPicker() {
        try {
            pickPdf.launch(arrayOf("application/pdf"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun identifyWeakLibraryMetadata() {
        val db = AnnotationDatabase.getInstance(this)
        val books = db.bookDao().getAllOnce()
        books.forEach { book ->
            val metadata = db.bookMetadataDao().get(book.bookUri)
            if (needsAutomaticLookup(book, metadata)) {
                try {
                    BookMetadataRepository.resolve(
                        context = this,
                        uri = Uri.parse(book.bookUri),
                        document = null,
                        allowOnlineOnce = true
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun needsAutomaticLookup(book: BookEntity, metadata: BookMetadataEntity?): Boolean {
        val title = metadata?.title.orEmpty()
        val author = metadata?.author.orEmpty()
        return metadata == null ||
            metadata.confidence < 0.7f ||
            author.isBlank() ||
            author == "Unknown Author" ||
            title.isBlank() ||
            title == "Untitled PDF" ||
            title.equals(book.displayName, ignoreCase = true) ||
            title.contains(";") ||
            title.contains("=") ||
            title.endsWith(" temp", ignoreCase = true)
    }

    private fun resolveDisplayName(uri: Uri): String {
        val raw = try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment ?: "Unknown PDF"
        } catch (e: Exception) {
            "Unknown PDF"
        }
        return PdfLibraryAdapter.prettifyName(raw)
    }
}
