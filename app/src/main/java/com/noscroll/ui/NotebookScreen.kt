package com.noscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noscroll.quote.QuoteCardStyles
import com.noscroll.repository.NotebookState
import com.noscroll.tutorial.TutorialAnchor
import com.noscroll.tutorial.TutorialController
import com.noscroll.tutorial.TutorialOverlay
import com.noscroll.tutorial.TutorialStepId

@Composable
fun NotebookScreen(
    state: NotebookState,
    onBack: () -> Unit,
    onOpenBook: (String, Int) -> Unit,
    onShareQuote: (String, Int) -> Unit,
    onDeleteHighlight: (Long) -> Unit,
    onExport: () -> Unit,
    tutorialController: TutorialController? = null
) {
    var tab by remember { mutableIntStateOf(0) }
    val metadataByUri = state.metadata.associateBy { it.bookUri }
    val booksByUri = state.books.associateBy { it.bookUri }
    val annotationsByHighlight = state.annotations.associateBy { it.highlightId }
    val tabs = listOf(
        "Highlights ${state.highlights.size}",
        "Notes ${state.annotations.size}",
        "Quotes ${state.quotes.size}"
    )
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = PaperColors.Paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaperActionButton(label = "Back", onClick = onBack, tone = PaperButtonTone.Quiet)
                Text("Notebook", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                PaperActionButton(label = "Export", onClick = onExport, tone = PaperButtonTone.Quiet)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TutorialAnchor(TutorialStepId.NOTEBOOK_HIGHLIGHTS, tutorialController) {
                PaperSegmentedSelector(
                    label = "View",
                    options = tabs.indices.toList(),
                    selected = tab,
                    onSelected = { tab = it },
                    optionLabel = { tabs[it] },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> items(state.highlights, key = { "h-${it.id}" }) { highlight ->
                        NotebookQuoteRow(
                            title = safeTitle(metadataByUri[highlight.bookUri]?.title, booksByUri[highlight.bookUri]?.displayName),
                            detail = "Page ${highlight.pageIndex + 1}",
                            quote = highlight.quoteText,
                            note = null,
                            onClick = { onOpenBook(highlight.bookUri, highlight.pageIndex) },
                            onDelete = { onDeleteHighlight(highlight.id) }
                        )
                        HorizontalDivider(color = PaperColors.Hairline)
                    }
                    1 -> items(state.highlights.filter { annotationsByHighlight[it.id] != null }, key = { "n-${it.id}" }) { highlight ->
                        NotebookQuoteRow(
                            title = safeTitle(metadataByUri[highlight.bookUri]?.title, booksByUri[highlight.bookUri]?.displayName),
                            detail = "Page ${highlight.pageIndex + 1}",
                            quote = highlight.quoteText,
                            note = annotationsByHighlight[highlight.id]?.noteText,
                            onClick = { onOpenBook(highlight.bookUri, highlight.pageIndex) },
                            onDelete = { onDeleteHighlight(highlight.id) }
                        )
                        HorizontalDivider(color = PaperColors.Hairline)
                    }
                    2 -> items(state.quotes, key = { "q-${it.id}" }) { quote ->
                        NotebookQuoteRow(
                            title = safeTitle(metadataByUri[quote.bookUri]?.title, booksByUri[quote.bookUri]?.displayName ?: "Quote card"),
                            detail = QuoteCardStyles.byId(quote.themeName).name,
                            quote = quote.quoteText,
                            note = null,
                            onClick = { onOpenBook(quote.bookUri, quote.pageIndex) },
                            onDelete = null
                        )
                        HorizontalDivider(color = PaperColors.Hairline)
                    }
                }
            }
        }
    }
    tutorialController?.let { TutorialOverlay(it) }
    }
}

private fun safeTitle(value: String?, fallback: String? = "Untitled PDF"): String {
    val title = value.orEmpty()
    return if (
        title.isBlank() ||
        title.contains(";") ||
        title.contains("=") ||
        title.length > 80 ||
        title.endsWith(" temp", ignoreCase = true)
    ) {
        fallback ?: "Untitled PDF"
    } else {
        title
    }
}

@Composable
private fun NotebookQuoteRow(
    title: String,
    detail: String,
    quote: String,
    note: String?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = PaperColors.Muted)
            Text(
                quote,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = PaperColors.Graphite, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaperActionButton(
                    label = "Open",
                    onClick = onClick,
                    tone = PaperButtonTone.Quiet
                )
                if (onDelete != null) {
                    PaperActionButton(
                        label = if (confirmingDelete) "Confirm delete" else "Delete",
                        onClick = {
                            if (confirmingDelete) {
                                confirmingDelete = false
                                onDelete()
                            } else {
                                confirmingDelete = true
                            }
                        },
                        tone = PaperButtonTone.Danger
                    )
                    if (confirmingDelete) {
                        PaperActionButton(
                            label = "Cancel",
                            onClick = { confirmingDelete = false },
                            tone = PaperButtonTone.Quiet
                        )
                    }
                }
            }
    }
}
