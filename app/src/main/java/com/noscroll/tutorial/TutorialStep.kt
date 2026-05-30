package com.noscroll.tutorial

enum class TutorialStepId {
    SETUP_OVERLAY, SETUP_ACCESSIBILITY,
    LIBRARY_IMPORT, LIBRARY_FILTERS, LIBRARY_NOTEBOOK,
    READER_SELECT, READER_ZEN, READER_CONTROLS,
    NOTEBOOK_HIGHLIGHTS, REELS_OVERLAY
}

enum class TooltipSide { Above, Below }

data class TutorialStep(
    val id: TutorialStepId,
    val title: String,
    val body: String,
    val side: TooltipSide = TooltipSide.Below,
    val spotlightPaddingDp: Float = 16f
)
