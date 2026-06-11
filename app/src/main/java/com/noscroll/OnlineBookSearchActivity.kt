package com.noscroll

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.BookMetadataEntity
import com.noscroll.discovery.LegalBookApiClient
import com.noscroll.discovery.LegalBookDownloadLink
import com.noscroll.discovery.LegalBookSearchResult
import com.noscroll.repository.BookRepository
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.OnlineBookSearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class OnlineBookSearchActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    private var query by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var results by mutableStateOf<List<LegalBookSearchResult>>(emptyList())
    private var activeDownloadId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                OnlineBookSearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    isConfigured = LegalBookApiClient.isConfigured(),
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    results = results,
                    activeDownloadId = activeDownloadId,
                    onBack = { finish() },
                    onSearch = { runSearch() },
                    onDownload = { result -> downloadAndImport(result) }
                )
            }
        }
    }

    private fun runSearch() {
        val currentQuery = query.trim()
        if (currentQuery.isBlank()) {
            errorMessage = "Enter a title or author."
            results = emptyList()
            return
        }
        if (!LegalBookApiClient.isConfigured()) {
            errorMessage = "Set LEGAL_BOOKS_API_BASE_URL in local.properties."
            results = emptyList()
            return
        }
        lifecycleScope.launch {
            isLoading = true
            errorMessage = null
            val books = withContext(Dispatchers.IO) { LegalBookApiClient.searchBooks(currentQuery) }
            results = books
            errorMessage = if (books.isEmpty()) "No legal PDF matches found." else null
            isLoading = false
        }
    }

    private fun downloadAndImport(result: LegalBookSearchResult) {
        lifecycleScope.launch {
            activeDownloadId = result.id ?: result.title
            errorMessage = null
            try {
                val links = withContext(Dispatchers.IO) { LegalBookApiClient.resolveDownloadLinks(result) }
                val pdfLink = pickPdfLink(links)
                if (pdfLink == null) {
                    errorMessage = "No PDF download link was returned for this result."
                    return@launch
                }
                val uri = withContext(Dispatchers.IO) { downloadPdf(pdfLink, result) }
                BookRepository.importBook(this@OnlineBookSearchActivity, uri, result.title)
                AnnotationDatabase.getInstance(this@OnlineBookSearchActivity)
                    .bookMetadataDao()
                    .upsert(
                        BookMetadataEntity(
                            bookUri = uri.toString(),
                            title = result.title,
                            author = result.author,
                            source = result.source ?: "legal_api",
                            confidence = 0.95f,
                            coverUrl = result.coverUrl,
                            lastLookupAtMillis = System.currentTimeMillis()
                        )
                    )
                BookRepository.openBook(this@OnlineBookSearchActivity, uri.toString())
                startActivity(
                    Intent(this@OnlineBookSearchActivity, PdfViewerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Download failed."
                Toast.makeText(this@OnlineBookSearchActivity, "Could not import PDF", Toast.LENGTH_SHORT).show()
            } finally {
                activeDownloadId = null
            }
        }
    }

    private fun pickPdfLink(links: List<LegalBookDownloadLink>): LegalBookDownloadLink? =
        links.firstOrNull { it.mimeType?.contains("pdf", ignoreCase = true) == true }
            ?: links.firstOrNull { it.url.substringBefore('?').endsWith(".pdf", ignoreCase = true) }
            ?: links.firstOrNull()

    private fun downloadPdf(link: LegalBookDownloadLink, result: LegalBookSearchResult): Uri {
        val request = Request.Builder().url(link.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed with HTTP ${response.code}")
            val body = response.body ?: error("Empty download response")
            val directory = File(filesDir, "books").apply { mkdirs() }
            val filename = buildFilename(result)
            val target = File(directory, filename)
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                target
            )
        }
    }

    private fun buildFilename(result: LegalBookSearchResult): String {
        val base = "${result.title} ${result.author}"
            .replace(Regex("[^A-Za-z0-9._ -]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { "book-${System.currentTimeMillis()}" }
        return "$base.pdf"
    }
}
