package com.noscroll

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.noscroll.repository.BookRepository
import com.noscroll.repository.HighlightRepository
import com.noscroll.repository.NotebookRepository
import com.noscroll.tutorial.NotebookTutorialSteps
import com.noscroll.tutorial.TutorialController
import com.noscroll.tutorial.TutorialPrefs
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.NotebookScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotebookActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                val controller = remember { TutorialController() }
                val prefs = remember { TutorialPrefs(this@NotebookActivity) }
                LaunchedEffect(Unit) {
                    if (prefs.hasOptedIn() && !prefs.isNotebookDone()) {
                        kotlinx.coroutines.delay(300)
                        controller.start(NotebookTutorialSteps)
                        controller.onDone = { prefs.markNotebookDone() }
                    }
                }
                val state = NotebookRepository.observe(this).collectAsStateWithLifecycle(
                    com.noscroll.repository.NotebookState()
                ).value
                NotebookScreen(
                    state = state,
                    onBack = { finish() },
                    onOpenBook = { uri, page ->
                        lifecycleScope.launch {
                            BookRepository.openBook(this@NotebookActivity, uri)
                            PdfStorage.savePage(this@NotebookActivity, page)
                            startActivity(Intent(this@NotebookActivity, PdfViewerActivity::class.java))
                        }
                    },
                    onShareQuote = { _, _ ->
                        Toast.makeText(this, "Open quote cards from source highlights", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteHighlight = { highlightId ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                HighlightRepository.delete(this@NotebookActivity, highlightId)
                            }
                            Toast.makeText(this@NotebookActivity, "Highlight deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    tutorialController = controller,
                    onExport = {
                        lifecycleScope.launch {
                            val uri = PdfStorage.getSelectedUri(this@NotebookActivity)
                            if (uri == null) {
                                Toast.makeText(this@NotebookActivity, "Open a book before exporting", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val highlights = withContext(Dispatchers.IO) {
                                HighlightRepository.getForBook(this@NotebookActivity, uri.toString())
                            }
                            if (highlights.isEmpty()) {
                                Toast.makeText(this@NotebookActivity, "No highlights to export", Toast.LENGTH_SHORT).show()
                            } else {
                                val exportUri = withContext(Dispatchers.IO) {
                                    PdfHighlightExporter.exportHighlightsText(this@NotebookActivity, "NoScroll highlights", highlights)
                                }
                                PdfHighlightExporter.shareUri(this@NotebookActivity, exportUri, "text/plain", "Export highlights")
                            }
                        }
                    }
                )
            }
        }
    }
}
