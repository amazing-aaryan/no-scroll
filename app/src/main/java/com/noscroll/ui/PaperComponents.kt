package com.noscroll.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noscroll.R
import com.noscroll.data.BookEntity
import com.noscroll.data.BookMetadataEntity
import com.noscroll.PdfThumbnailCache

@Composable
fun DocumentRow(
    book: BookEntity,
    metadata: BookMetadataEntity?,
    highlightCount: Int,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onIdentify: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = metadataTitle(metadata, book)
    val needsIdentity = needsMetadataIdentification(metadata, book)
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    Row(
            modifier = modifier
                .fillMaxWidth()
                .background(PaperColors.Paper)
                .padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaperCover(title = title, uri = book.bookUri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    metadata?.author?.takeIf { it.isNotBlank() } ?: fileSizeLabel(book.fileSizeBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperColors.Graphite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress(book) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = PaperColors.Amber,
                    trackColor = PaperColors.Hairline
                )
                if (highlightCount > 0) {
                    Text(
                        "$highlightCount highlights",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperColors.Muted,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
            PaperActionButton(
                label = "Open",
                onClick = onOpen,
                tone = PaperButtonTone.Quiet,
                modifier = Modifier.padding(start = 12.dp)
            )
            Box {
                IconButton(onClick = {
                    confirmingDelete = false
                    menuExpanded = true
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "More options",
                        tint = PaperColors.Graphite
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                        confirmingDelete = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = PaperColors.Raised,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (confirmingDelete) {
                            PaperMenuAction(
                                label = "Remove book",
                                detail = "Removes it from NoScroll only.",
                                destructive = true,
                                onClick = {
                                    menuExpanded = false
                                    confirmingDelete = false
                                    onDelete()
                                }
                            )
                            PaperMenuAction(
                                label = "Cancel",
                                onClick = { confirmingDelete = false }
                            )
                        } else {
                            PaperMenuAction(
                                label = if (book.isFavorite) "Unfavourite" else "Favourite",
                                detail = "Updates this book's library status.",
                                onClick = {
                                    menuExpanded = false
                                    onFavorite()
                                }
                            )
                            if (needsIdentity) {
                                PaperMenuAction(
                                    label = "Identify",
                                    detail = "Look up title and author.",
                                    onClick = {
                                        menuExpanded = false
                                        onIdentify()
                                    }
                                )
                            }
                            PaperMenuAction(
                                label = "Remove",
                                detail = "Requires confirmation.",
                                destructive = true,
                                onClick = { confirmingDelete = true }
                            )
                        }
                    }
                }
            }
    }
}
@Composable
fun PaperCover(title: String, modifier: Modifier = Modifier, uri: String? = null) {
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(initialValue = null, uri) {
        value = uri?.let { bookUri ->
            PdfThumbnailCache.getOrCreate(context, bookUri)?.absolutePath?.let { path ->
                BitmapFactory.decodeFile(path)
            }
        }
    }.value
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 72.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PaperColors.Raised),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                title.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal
                ),
                color = PaperColors.Sage
            )
        }
    }
}

fun metadataTitle(metadata: BookMetadataEntity?, book: BookEntity): String {
    val candidate = metadata?.title.orEmpty()
    return if (
        candidate.isBlank() ||
        candidate.contains(";") ||
        candidate.contains("=") ||
        candidate.length > 80 ||
        candidate.endsWith(" temp", ignoreCase = true)
    ) {
        book.displayName
    } else {
        candidate
    }
}

fun needsMetadataIdentification(metadata: BookMetadataEntity?, book: BookEntity): Boolean {
    val title = metadataTitle(metadata, book)
    val author = metadata?.author.orEmpty()
    return metadata == null ||
        author.isBlank() ||
        author == "Unknown Author" ||
        title == "Untitled PDF" ||
        title.equals(book.displayName, ignoreCase = true)
}

fun progress(book: BookEntity): Float =
    if (book.pageCount <= 0) 0f else (book.lastPageIndex + 1).toFloat() / book.pageCount.toFloat()

fun fileSizeLabel(bytes: Long): String =
    when {
        bytes <= 0L -> "PDF document"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    }
