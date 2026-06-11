package com.noscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noscroll.discovery.LegalBookSearchResult

@Composable
fun OnlineBookSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    isConfigured: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    results: List<LegalBookSearchResult>,
    activeDownloadId: String?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onDownload: (LegalBookSearchResult) -> Unit
) {
    Scaffold(
        containerColor = PaperColors.Paper,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(PaperColors.Paper)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = PaperColors.Amber,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Legal PDF search",
                        style = MaterialTheme.typography.titleMedium,
                        color = PaperColors.Ink
                    )
                }
                Text(
                    "Search your configured authorized source and import the PDF directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperColors.Muted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (!isConfigured) {
                        NoticeCard(
                            title = "Backend not configured",
                            body = "Add LEGAL_BOOKS_API_BASE_URL to local.properties, then replace the placeholder endpoint mapping in LegalBookApiClient."
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title or author") },
                        singleLine = true
                    )
                    SearchActionRow(
                        onSearch = onSearch,
                        isLoading = isLoading
                    )
                    errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PaperColors.Amber,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Text(
                        "RESULTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperColors.Muted,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
                    )
                }
                items(results, key = { it.id ?: "${it.title}-${it.author}" }) { result ->
                    SearchResultRow(
                        result = result,
                        isDownloading = activeDownloadId == (result.id ?: result.title),
                        onDownload = { onDownload(result) }
                    )
                    HorizontalDivider(color = PaperColors.Hairline)
                }
            }
        }
    }
}

@Composable
private fun SearchActionRow(
    onSearch: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(PaperColors.Ink)
                .clickable(enabled = !isLoading, onClick = onSearch)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp),
                    color = PaperColors.Raised,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Search", style = MaterialTheme.typography.labelLarge, color = PaperColors.Raised)
            }
        }
        Text(
            "Uses your configured legal-source API.",
            style = MaterialTheme.typography.labelMedium,
            color = PaperColors.Muted,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SearchResultRow(
    result: LegalBookSearchResult,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                color = PaperColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                result.author,
                style = MaterialTheme.typography.bodyMedium,
                color = PaperColors.Graphite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            val meta = listOfNotNull(result.source, result.year, result.language, result.fileType)
                .joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = PaperColors.Muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, PaperColors.Ink, RoundedCornerShape(10.dp))
                .clickable(enabled = !isDownloading, onClick = onDownload)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (isDownloading) "Importing..." else "Get PDF",
                style = MaterialTheme.typography.labelLarge,
                color = PaperColors.Ink,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PaperColors.Raised)
            .border(1.dp, PaperColors.Hairline, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PaperColors.Ink)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = PaperColors.Graphite,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
