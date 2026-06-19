package com.noscroll.tutorial

val SetupTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.SETUP_OVERLAY,
        "Allow the NoScroll logo",
        "This lets the NoScroll logo appear over Instagram.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.SETUP_ACCESSIBILITY,
        "Detect Reels",
        "NoScroll uses this to find the Reels feed and show your book.",
        TooltipSide.Below
    )
)

val LibraryTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.LIBRARY_IMPORT,
        "Import your first book",
        "Tap to bring in any PDF from your device.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.LIBRARY_FILTERS,
        "Filter and sort",
        "Jump to favorites or books you've highlighted.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.LIBRARY_NOTEBOOK,
        "Your reading notebook",
        "Every highlight and quote you save lives here.",
        TooltipSide.Below
    )
)

val ReaderTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.READER_SELECT,
        "Long-press to highlight",
        "Press and hold any word in the page above, then drag the handles to extend your selection. Tap Highlight to save.",
        TooltipSide.Below,
        spotlightPaddingDp = 6f
    ),
    TutorialStep(
        TutorialStepId.READER_ZEN,
        "Zen mode",
        "Hides all controls for distraction-free reading. Tap the screen to bring them back.",
        TooltipSide.Above
    ),
    TutorialStep(
        TutorialStepId.READER_CONTROLS,
        "Show / hide controls",
        "Tap anywhere on the page to toggle the toolbar and metadata bar.",
        TooltipSide.Below
    )
)

val NotebookTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.NOTEBOOK_HIGHLIGHTS,
        "Your highlights",
        "Tap any entry to jump back to that page. Long-press for a quote card.",
        TooltipSide.Below
    )
)
