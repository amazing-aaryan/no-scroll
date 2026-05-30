package com.noscroll.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.noscroll.ui.PaperColors

@Composable
fun TutorialOverlay(controller: TutorialController) {
    val step = controller.current ?: return
    val density = LocalDensity.current
    val bounds = controller.boundsFor(step.id)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Dark scrim with optional spotlight cutout — visual only, no touch blocking.
        // Touch blocking was causing all in-app buttons to stop working.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color(0xCC000000))
            if (bounds != null) {
                val pad = step.spotlightPaddingDp * density.density
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(bounds.left - pad, bounds.top - pad),
                    size = Size(bounds.width + pad * 2, bounds.height + pad * 2),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        val cardWidthDp = 280.dp

        if (bounds != null) {
            val leftDp  = with(density) { bounds.left.toDp() }
            val rightDp = with(density) { bounds.right.toDp() }
            val topDp   = with(density) { bounds.top.toDp() }
            val bottomDp = with(density) { bounds.bottom.toDp() }
            val cx   = leftDp + (rightDp - leftDp) / 2
            val xDp  = (cx - cardWidthDp / 2).coerceIn(16.dp, maxWidth - cardWidthDp - 16.dp)
            val padDp = step.spotlightPaddingDp.dp * 2
            val yDp = if (step.side == TooltipSide.Below) {
                (bottomDp + padDp).coerceIn(24.dp, maxHeight - 200.dp)
            } else {
                (topDp - padDp - 180.dp).coerceIn(24.dp, maxHeight - 200.dp)
            }

            // Layout properly updates both visual position AND hit-test area.
            // absoluteOffset/offset only move drawing, not touch bounds — buttons would be
            // unresponsive if we used those.
            val xPx = with(density) { xDp.roundToPx() }
            val yPx = with(density) { yDp.roundToPx() }
            Layout(content = {
                Box(Modifier.width(cardWidthDp)) { TooltipCard(step, controller) }
            }) { measurables, constraints ->
                val placeable = measurables[0].measure(
                    constraints.copy(minWidth = 0, minHeight = 0)
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(xPx, yPx)
                }
            }
        } else {
            // No bounds yet — show centered while anchors register
            Box(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(cardWidthDp)) { TooltipCard(step, controller) }
            }
        }
    }
}

@Composable
private fun TooltipCard(step: TutorialStep, controller: TutorialController) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PaperColors.Raised),
        border = BorderStroke(1.dp, PaperColors.Hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (controller.stepCount() > 1) {
                Text(
                    "${controller.stepIndex() + 1} / ${controller.stepCount()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperColors.Muted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(step.title, style = MaterialTheme.typography.titleMedium, color = PaperColors.Ink)
            Text(
                step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = PaperColors.Graphite,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { controller.skip() }) {
                    Text("Skip", color = PaperColors.Muted)
                }
                Button(
                    onClick = { controller.advance() },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Ink)
                ) {
                    val isLast = controller.stepIndex() + 1 >= controller.stepCount()
                    Text(if (isLast) "Done" else "Next →", color = PaperColors.Raised)
                }
            }
        }
    }
}
