# Plan: In-App Tutorial — Spotlight + Tooltip

## Task
Add a first-launch, skippable, spotlight-style tutorial covering all 4 flows:
Setup → Library → Reader → Reels blocker + Notebook.

---

## Technical Solution

**Spotlight technique:** `Canvas` with `CompositingStrategy.Offscreen` + `BlendMode.Clear` punches a rounded-rect hole through a dark scrim. Target bounds captured via `onGloballyPositioned → boundsInWindow()`.

**State:** `TutorialController` — plain class holding `MutableState<TutorialStep?>` and a `MutableMap<TutorialStepId, Rect>` for registered anchor bounds. One instance per Activity, created with `remember`.

**Persistence:** `TutorialPrefs` (SharedPrefs wrapper) — one `Boolean` key per flow (`setup_done`, `library_done`, `reader_done`, `notebook_done`, `reels_done`). Tutorial shown once per flow, never again after `markDone()`.

**Integration:**
- Compose activities (Setup, Library, Notebook): wrap root `Box` content + add `TutorialOverlay` as topmost child
- `PdfViewerActivity` (XML + Fragment): add a `ComposeView` as `MATCH_PARENT` overlay in `FrameLayout` root
- `OverlayService` (floating book icon): View-based one-time tooltip sub-view

---

## Step Sequences

### Flow A — Setup (2 steps, shown in SetupActivity)
| ID | Anchor | Title | Body |
|----|--------|-------|------|
| `SETUP_OVERLAY` | "Grant" button (overlay perm row) | "Allow the floating icon" | "This lets the book icon appear over Instagram." |
| `SETUP_ACCESSIBILITY` | "Enable" button (accessibility row) | "Detect Reels" | "NoScroll uses this to find the Reels feed and show your book." |

Trigger: `TutorialPrefs.isSetupDone == false` on `SetupActivity.onCreate`.

### Flow B — Library (3 steps, shown in PdfLibraryActivity)
| ID | Anchor | Title | Body |
|----|--------|-------|------|
| `LIBRARY_IMPORT` | `ImportCard` (+ Add a book) | "Import your first book" | "Tap to bring in any PDF from your device." |
| `LIBRARY_FILTERS` | Filter chips row | "Filter and sort" | "Jump to favorites or books you've highlighted." |
| `LIBRARY_NOTEBOOK` | "Notebook" button (top bar) | "Your reading notebook" | "Every highlight and quote you save lives here." |

Trigger: `TutorialPrefs.isLibraryDone == false` on `PdfLibraryActivity.setContent` via `LaunchedEffect`.

### Flow C — Reader (3 steps, shown in PdfViewerActivity)
| ID | Anchor | Title | Body |
|----|--------|-------|------|
| `READER_SELECT` | PDF content area (center) | "Select to highlight" | "Long-press any text, then drag handles to extend." |
| `READER_ZEN` | Zen button (toolbar) | "Zen mode" | "Hide all chrome for distraction-free reading." |
| `READER_CONTROLS` | Metadata bar (top) | "Book controls" | "Tap anywhere to show or hide navigation and tools." |

Trigger: `TutorialPrefs.isReaderDone == false` on first `onPdfLoaded` callback in `PdfViewerActivity`.

### Flow D — Notebook + Reels (2 steps)
| ID | Anchor | Title | Body |
|----|--------|-------|------|
| `NOTEBOOK_HIGHLIGHTS` | Highlights tab / first highlight row | "Your highlights" | "Tap any entry to jump back to that page. Long-press for a quote card." |
| `REELS_OVERLAY` | Floating book icon (OverlayService) | "Your reading shortcut" | "Tap this to open your book. It floats over any app." |

`NOTEBOOK_HIGHLIGHTS` trigger: `TutorialPrefs.isNotebookDone == false` on `NotebookActivity.onCreate`.
`REELS_OVERLAY` trigger: first time `OverlayService` shows the book icon (first `showBookIcon()` call).

---

## Implementation Steps

### Step 1 — New package: `tutorial/`

**`tutorial/TutorialStep.kt`**
```kotlin
package com.noscroll.tutorial

import androidx.compose.ui.geometry.Rect

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
```

**`tutorial/TutorialPrefs.kt`**
```kotlin
package com.noscroll.tutorial

import android.content.Context

class TutorialPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    fun isSetupDone()    = p.getBoolean("setup_done",    false)
    fun isLibraryDone()  = p.getBoolean("library_done",  false)
    fun isReaderDone()   = p.getBoolean("reader_done",   false)
    fun isNotebookDone() = p.getBoolean("notebook_done", false)
    fun isReelsDone()    = p.getBoolean("reels_done",    false)

    fun markSetupDone()    = p.edit().putBoolean("setup_done",    true).apply()
    fun markLibraryDone()  = p.edit().putBoolean("library_done",  true).apply()
    fun markReaderDone()   = p.edit().putBoolean("reader_done",   true).apply()
    fun markNotebookDone() = p.edit().putBoolean("notebook_done", true).apply()
    fun markReelsDone()    = p.edit().putBoolean("reels_done",    true).apply()

    fun resetAll() = p.edit().clear().apply()
}
```

**`tutorial/TutorialController.kt`**
```kotlin
package com.noscroll.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

class TutorialController {
    private val boundsMap = mutableMapOf<TutorialStepId, Rect>()
    private var steps: List<TutorialStep> = emptyList()
    private var index = 0
    var onDone: (() -> Unit)? = null

    var current by mutableStateOf<TutorialStep?>(null)
        private set

    fun stepCount() = steps.size
    fun stepIndex() = index

    fun boundsFor(id: TutorialStepId): Rect? = boundsMap[id]

    fun registerBounds(id: TutorialStepId, rect: Rect) {
        boundsMap[id] = rect
    }

    fun start(sequence: List<TutorialStep>) {
        steps = sequence
        index = 0
        current = steps.firstOrNull()
    }

    fun advance() {
        index++
        current = steps.getOrNull(index)
        if (current == null) onDone?.invoke()
    }

    fun skip() {
        current = null
        onDone?.invoke()
    }
}
```

**`tutorial/TutorialStepDefs.kt`** — all 4 flow definitions as top-level `val` lists.

**`tutorial/TutorialAnchor.kt`**
```kotlin
@Composable
fun TutorialAnchor(
    id: TutorialStepId,
    controller: TutorialController,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            controller.registerBounds(
                id,
                Rect(r.left.toFloat(), r.top.toFloat(),
                     r.right.toFloat(), r.bottom.toFloat())
            )
        }
    ) { content() }
}
```

**`tutorial/TutorialOverlay.kt`**
- Root: `Box(Modifier.fillMaxSize().pointerInput(Unit) { consumeAllTouches() })`
- Layer 1: `Canvas` with `CompositingStrategy.Offscreen` → `drawRect(Color(0xCC000000))` then `drawRoundRect(BlendMode.Clear)` at anchor bounds + padding
- Layer 2: `TooltipCard` positioned above/below anchor, animated with `AnimatedVisibility` slide + fade
- TooltipCard: `PaperColors.Raised` bg, 1dp `PaperColors.Hairline` border, 16dp corner radius
  - Header: step indicator "1 / 3" labelSmall Muted
  - Title: titleMedium Ink
  - Body: bodyMedium Graphite
  - Footer row: `TextButton("Skip")` Muted + `Button("Next →")` Ink/Raised

---

### Step 2 — SetupActivity integration

- Wrap existing `Column` in `Box`
- `PermissionRow` composables for overlay + accessibility wrapped in `TutorialAnchor`
- `controller = remember { TutorialController() }` at top of `setContent`
- `LaunchedEffect(Unit)` → if `!prefs.isSetupDone()` call `controller.start(SetupTutorialSteps)`
- `controller.onDone = { prefs.markSetupDone() }`
- `TutorialOverlay(controller)` as last child of root `Box`

---

### Step 3 — LibraryScreen + PdfLibraryActivity integration

`LibraryScreen` signature gains `tutorialController: TutorialController?` (nullable, default null = tutorial disabled).

Wrap:
- `ImportCard(onClick)` → `TutorialAnchor(LIBRARY_IMPORT, controller) { ImportCard(...) }`
- Filter chips `Row` → `TutorialAnchor(LIBRARY_FILTERS, ...)`
- Notebook `Box` → `TutorialAnchor(LIBRARY_NOTEBOOK, ...)`

Add `TutorialOverlay` after `Scaffold` closing brace inside root `Box`.

In `PdfLibraryActivity.setContent`:
```kotlin
val controller = remember { TutorialController() }
val prefs = remember { TutorialPrefs(this) }
LaunchedEffect(Unit) {
    if (!prefs.isLibraryDone()) {
        controller.start(LibraryTutorialSteps)
        controller.onDone = { prefs.markLibraryDone() }
    }
}
```

---

### Step 4 — PdfViewerActivity integration

```kotlin
// In onCreate, after existing setup:
val tutorialController = TutorialController()
val prefs = TutorialPrefs(this)
val composeOverlay = ComposeView(this).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        NoScrollTheme { TutorialOverlay(tutorialController) }
    }
}
(window.decorView as FrameLayout).addView(
    composeOverlay,
    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
)

// In onPdfLoaded:
if (!prefs.isReaderDone()) {
    // Capture View bounds → register on controller
    val zenRect  = captureViewRect(zenBtn)
    val barRect  = captureViewRect(metadataBar)
    val rootRect = captureViewRect(window.decorView)
    tutorialController.registerBounds(TutorialStepId.READER_ZEN, zenRect)
    tutorialController.registerBounds(TutorialStepId.READER_CONTROLS, barRect)
    tutorialController.registerBounds(TutorialStepId.READER_SELECT, rootRect.center())
    tutorialController.start(ReaderTutorialSteps)
    tutorialController.onDone = { prefs.markReaderDone() }
}

// Helper:
fun captureViewRect(v: View): Rect {
    val arr = IntArray(2); v.getLocationInWindow(arr)
    return Rect(arr[0].toFloat(), arr[1].toFloat(),
                (arr[0] + v.width).toFloat(), (arr[1] + v.height).toFloat())
}
```

---

### Step 5 — NotebookScreen + NotebookActivity integration

Same Compose pattern as Library. Add `tutorialController` param to `NotebookScreen`. Wrap highlights tab `Tab` and first highlight `Row` in `TutorialAnchor`. Trigger in `NotebookActivity.setContent` via `LaunchedEffect`.

---

### Step 6 — OverlayService one-time reels tooltip

In `showBookIcon()` (where the book overlay view is first added to WindowManager):
```kotlin
val prefs = TutorialPrefs(this)
if (!prefs.isReelsDone()) {
    prefs.markReelsDone()
    val tooltip = inflateReelsTooltip()  // simple TextView with rounded bg
    windowManager?.addView(tooltip, tooltipLayoutParams())
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching { windowManager?.removeView(tooltip) }
    }, 4000L)
}
```

Tooltip style: white card, 12dp corner, shadow, "Tap to open your book" body text, auto-dismiss 4s or on tap.

---

## Key Files

| File | Op | Description |
|------|----|-------------|
| `tutorial/TutorialStep.kt` | Create | Step enum + data class |
| `tutorial/TutorialPrefs.kt` | Create | SharedPrefs (5 boolean keys) |
| `tutorial/TutorialController.kt` | Create | Compose MutableState machine |
| `tutorial/TutorialOverlay.kt` | Create | Spotlight canvas + tooltip card composable |
| `tutorial/TutorialAnchor.kt` | Create | `onGloballyPositioned` wrapper composable |
| `tutorial/TutorialStepDefs.kt` | Create | Step list vals for all 4 flows |
| `SetupActivity.kt` | Modify | Wrap content in Box, add anchors + controller |
| `ui/LibraryScreen.kt` | Modify | Add `tutorialController` param, wrap anchors |
| `PdfLibraryActivity.kt` | Modify | Wire controller + prefs in `setContent` |
| `PdfViewerActivity.kt` | Modify | Add ComposeView overlay, capture View bounds |
| `NotebookActivity.kt` | Modify | Wire controller + prefs |
| `ui/NotebookScreen.kt` | Modify | Add `tutorialController` param, wrap anchors |
| `OverlayService.kt` | Modify | One-time reels tooltip on first book icon show |

---

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Bounds null when overlay starts | Fallback to centered tooltip card; anchors register on first recomposition |
| `BlendMode.Clear` on older GPU | Try-catch around Canvas; fallback: Amber ring border rect, no cutout |
| ComposeView in XML activity z-order | Add to `window.decorView` FrameLayout (always top layer) |
| Reader tutorial fires mid-scroll | Delay start until `onPdfViewReady` + 300ms; cancel if user navigates away |
| Reels tooltip positioning off-screen | Constrain `y` to `statusBarHeight..screenHeight - tooltipHeight` |
| Tutorial re-triggers after forced-stop | `markDone()` called at `start()`, not at completion — safe against partial flows |

---

## SESSION_ID
- CODEX_SESSION: N/A (local plan)
- GEMINI_SESSION: N/A (local plan)
