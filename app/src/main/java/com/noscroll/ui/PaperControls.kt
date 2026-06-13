package com.noscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PaperButtonTone {
    Ink,
    Sage,
    Quiet,
    Danger
}

@Composable
fun PaperActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PaperButtonTone = PaperButtonTone.Ink,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(8.dp)
    val background = when (tone) {
        PaperButtonTone.Ink -> PaperColors.Ink
        PaperButtonTone.Sage -> PaperColors.Sage
        PaperButtonTone.Quiet -> Color.Transparent
        PaperButtonTone.Danger -> Color.Transparent
    }
    val borderColor = when (tone) {
        PaperButtonTone.Ink -> PaperColors.Ink
        PaperButtonTone.Sage -> PaperColors.Sage
        PaperButtonTone.Quiet -> PaperColors.Hairline
        PaperButtonTone.Danger -> PaperColors.Danger
    }
    val contentColor = when {
        !enabled -> PaperColors.Muted
        tone == PaperButtonTone.Ink || tone == PaperButtonTone.Sage -> PaperColors.Raised
        tone == PaperButtonTone.Danger -> PaperColors.Danger
        else -> PaperColors.Ink
    }

    Box(
        modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 72.dp)
            .clip(shape)
            .background(if (enabled) background else PaperColors.SoftInk)
            .border(1.dp, if (enabled) borderColor else PaperColors.Hairline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun <T> PaperSegmentedSelector(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PaperColors.Muted,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                PaperChoiceChip(
                    label = optionLabel(option),
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    compact = compact
                )
            }
        }
    }
}

@Composable
fun PaperChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = if (compact) 36.dp else 42.dp)
            .widthIn(min = if (compact) 58.dp else 74.dp)
            .clip(shape)
            .background(if (selected) PaperColors.Ink else PaperColors.Raised)
            .border(1.dp, if (selected) PaperColors.Ink else PaperColors.Hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = if (compact) 8.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = if (selected) PaperColors.Raised else PaperColors.Graphite,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PaperMenuAction(
    label: String,
    detail: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) PaperColors.Danger else PaperColors.Ink,
            fontWeight = FontWeight.Medium
        )
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = PaperColors.Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
