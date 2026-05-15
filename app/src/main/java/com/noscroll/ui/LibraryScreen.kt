package com.noscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noscroll.data.BookEntity
import com.noscroll.data.BookMetadataEntity
import com.noscroll.data.HighlightEntity

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
    onNotebook: () -> Unit
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

    Scaffold(
        containerColor = PaperColors.Paper,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(PaperColors.Paper)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NoScroll", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onNotebook) { Text("Notebook") }
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Ink)
                ) { Text("Import") }
            }
        }
    ) { padding ->
        if (books.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Import your first PDF", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Ink)) {
                    Text("Import")
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
                        "Continue Reading",
                        style = MaterialTheme.typography.labelLarge,
                        color = PaperColors.Graphite,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                items(recent, key = { "recent-${it.bookUri}" }) { book ->
                    ContinueRow(book, metadataByUri[book.bookUri], onOpen = { onOpen(book) })
                }
                item { Divider(color = PaperColors.Hairline) }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryFilter.values().forEach { option ->
                        CompactChip(
                            label = option.name,
                            selected = filter == option,
                            onClick = { filter = option }
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibrarySort.values().forEach { option ->
                        CompactChip(
                            label = option.name,
                            selected = sort == option,
                            onClick = { sort = option },
                            small = true
                        )
                    }
                }
            }
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
                Divider(color = PaperColors.Hairline)
            }
        }
    }
}

@Composable
private fun CompactChip(label: String, selected: Boolean, onClick: () -> Unit, small: Boolean = false) {
    Box(
        Modifier
            .height(if (small) 32.dp else 38.dp)
            .widthIn(min = if (small) 56.dp else 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PaperColors.Hairline else PaperColors.Paper)
            .border(1.dp, PaperColors.Hairline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = if (selected) PaperColors.Ink else PaperColors.Graphite,
            maxLines = 1
        )
    }
}

@Composable
private fun ContinueRow(book: BookEntity, metadata: BookMetadataEntity?, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaperCover(metadataTitle(metadata, book), uri = book.bookUri)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                metadataTitle(metadata, book),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Page ${book.lastPageIndex + 1}${if (book.pageCount > 0) " / ${book.pageCount}" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperColors.Graphite
            )
        }
        TextButton(onClick = onOpen) { Text("Open") }
    }
}
