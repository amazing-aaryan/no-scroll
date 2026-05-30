package com.noscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noscroll.data.BookEntity
import com.noscroll.data.BookMetadataEntity
import com.noscroll.data.HighlightEntity
import com.noscroll.tutorial.TutorialAnchor
import com.noscroll.tutorial.TutorialController
import com.noscroll.tutorial.TutorialOverlay
import com.noscroll.tutorial.TutorialStepId

enum class LibraryFilter { All, Favorites, Highlights }
enum class LibrarySort { Recent, Title, Author, Added }

@Composable
fun LibraryScreen(
    books: List<BookEntity>,
    metadata: List<BookMetadataEntity>,
    highlights: List<HighlightEntity>,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onFavorite: (BookEntity) -> Unit,
    onIdentify: (BookEntity) -> Unit,
    onDelete: (BookEntity) -> Unit,
    onNotebook: () -> Unit,
    tutorialController: TutorialController? = null,
    onHelp: (() -> Unit)? = null
) {
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    var sort by remember { mutableStateOf(LibrarySort.Recent) }
    val metadataByUri = metadata.associateBy { it.bookUri }
    val highlightsByUri = highlights.groupBy { it.bookUri }
    val visibleBooks = books
        .filter { book ->
            when (filter) {
                LibraryFilter.All -> true
                LibraryFilter.Favorites -> book.isFavorite
                LibraryFilter.Highlights -> highlightsByUri[book.bookUri].orEmpty().isNotEmpty()
            }
        }
        .let { filtered ->
            when (sort) {
                LibrarySort.Recent -> filtered.sortedByDescending { it.lastOpenedAtMillis }
                LibrarySort.Title -> filtered.sortedBy { metadataTitle(metadataByUri[it.bookUri], it).lowercase() }
                LibrarySort.Author -> filtered.sortedBy { metadataByUri[it.bookUri]?.author.orEmpty().lowercase() }
                LibrarySort.Added -> filtered.sortedByDescending { it.addedAtMillis }
            }
        }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = PaperColors.Paper,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(PaperColors.Paper)
                    .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NoScroll",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PaperColors.Ink
                        )
                        Text(
                            "your reading space",
                            style = MaterialTheme.typography.labelMedium,
                            color = PaperColors.Muted,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (onHelp != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onHelp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("?", style = MaterialTheme.typography.labelLarge, color = PaperColors.Muted)
                        }
                    }
                    TutorialAnchor(TutorialStepId.LIBRARY_NOTEBOOK, tutorialController) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PaperColors.Ink)
                                .clickable(onClick = onNotebook)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Notebook",
                                style = MaterialTheme.typography.labelLarge,
                                color = PaperColors.Raised
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (books.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your shelves are empty.",
                    style = MaterialTheme.typography.titleLarge,
                    color = PaperColors.Ink,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Add a PDF to begin reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
                )
                TutorialAnchor(TutorialStepId.LIBRARY_IMPORT, tutorialController) {
                    ImportCard(onClick = onImport)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val recent = visibleBooks.filter { it.lastOpenedAtMillis > 0 }.take(2)
            if (recent.isNotEmpty()) {
                item {
                    Text(
                        "CONTINUE READING",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperColors.Muted,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
                items(recent, key = { "recent-${it.bookUri}" }) { book ->
                    ContinueCard(book, metadataByUri[book.bookUri], onOpen = { onOpen(book) })
                }
                item {
                    HorizontalDivider(
                        color = PaperColors.Hairline,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            item {
                TutorialAnchor(TutorialStepId.LIBRARY_FILTERS, tutorialController) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryFilter.values().forEach { option ->
                            CompactChip(label = option.name, selected = filter == option, onClick = { filter = option })
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibrarySort.values().forEach { option ->
                        CompactChip(label = option.name, selected = sort == option, onClick = { sort = option }, small = true)
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LIBRARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperColors.Muted,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "${visibleBooks.size} book${if (visibleBooks.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PaperColors.Muted
                    )
                }
            }

            item {
                TutorialAnchor(TutorialStepId.LIBRARY_IMPORT, tutorialController) {
                    ImportCard(onClick = onImport)
                }
            }
            item { HorizontalDivider(color = PaperColors.Hairline) }

            items(visibleBooks, key = { it.bookUri }) { book ->
                DocumentRow(
                    book = book,
                    metadata = metadataByUri[book.bookUri],
                    highlightCount = highlightsByUri[book.bookUri].orEmpty().size,
                    onOpen = { onOpen(book) },
                    onFavorite = { onFavorite(book) },
                    onIdentify = { onIdentify(book) },
                    onDelete = { onDelete(book) }
                )
                HorizontalDivider(color = PaperColors.Hairline)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    tutorialController?.let { TutorialOverlay(it) }
    }
}

@Composable
private fun ContinueCard(book: BookEntity, metadata: BookMetadataEntity?, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PaperColors.Raised)
            .border(1.dp, PaperColors.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaperCover(metadataTitle(metadata, book), uri = book.bookUri)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                metadataTitle(metadata, book),
                style = MaterialTheme.typography.titleMedium,
                color = PaperColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val author = metadata?.author?.takeIf { it.isNotBlank() && it != "Unknown Author" }
            if (author != null) {
                Text(
                    author,
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperColors.Graphite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress(book) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = PaperColors.Amber,
                trackColor = PaperColors.Hairline
            )
            Text(
                "Page ${book.lastPageIndex + 1}${if (book.pageCount > 0) " of ${book.pageCount}" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = PaperColors.Muted,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        Text("→", fontSize = 20.sp, color = PaperColors.Amber, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, PaperColors.Hairline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("+", fontSize = 24.sp, color = PaperColors.Muted, fontWeight = FontWeight.Light)
        Column(Modifier.padding(start = 14.dp)) {
            Text("Add a book", style = MaterialTheme.typography.titleMedium, color = PaperColors.Graphite)
            Text(
                "Import a PDF from your device",
                style = MaterialTheme.typography.labelMedium,
                color = PaperColors.Muted,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun CompactChip(label: String, selected: Boolean, onClick: () -> Unit, small: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(if (small) 30.dp else 36.dp)
            .widthIn(min = if (small) 52.dp else 68.dp)
            .clip(shape)
            .background(if (selected) PaperColors.Ink else PaperColors.Paper)
            .border(1.dp, if (selected) PaperColors.Ink else PaperColors.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = if (selected) PaperColors.Raised else PaperColors.Graphite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
