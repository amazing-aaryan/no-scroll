# Plan: Dark Mode + Selection/Note UI + Tutorial Polish

**Branch:** `codex/reader-selection-highlights-zen`
**Created:** 2026-06-05
**Objective:** Auto-adapt to system dark mode; replace ugly color-picker and note dialogs with polished Compose sheets; rewrite tutorial copy.

---

## Context Brief (cold-start safe)

**Stack:** Android / Kotlin / Jetpack Compose (Material3) + `androidx.pdf` PDF viewer fragment.

**Key files:**
| File | Role |
|------|------|
| `app/src/main/java/com/noscroll/ui/PaperTheme.kt` | Compose theme — `NoScrollTheme` + `PaperColors` |
| `app/src/main/res/values/themes.xml` | XML themes used by all Activities |
| `app/src/main/java/com/noscroll/PdfViewerActivity.kt` | Hosts PDF viewer, shows color-picker + note dialogs |
| `app/src/main/java/com/noscroll/tutorial/TutorialStepDefs.kt` | All tutorial copy |
| `app/src/main/java/com/noscroll/tutorial/TutorialOverlay.kt` | Compose tutorial card UI |
| `app/src/main/java/com/noscroll/tutorial/TutorialStep.kt` | `TutorialStep` data class + enums |

**Current bugs / root causes:**
1. `NoScrollTheme` calls `isSystemInDarkTheme()` but stores result in `val ignored` — always renders the light `PaperScheme`.
2. `themes.xml` hardcodes `#F7F3EA` (warm white) for `android:windowBackground` in every style — Activities flash white even if Compose is dark.
3. `showHighlightColorPicker()` builds a raw `LinearLayout` with `View` circles — unstyled, no dark mode.
4. `showAnnotationDialog()` uses a bare `EditText` inside `MaterialAlertDialogBuilder` — tiny hint, no quote context visible.
5. Tutorial copy is minimal and action-weak (e.g., "Zen mode" body: "Hides all controls…" gives no affordance cue).

---

## Step 1 — Dark mode: Compose theme + XML window backgrounds

**Files to edit:**
- `app/src/main/java/com/noscroll/ui/PaperTheme.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/colors.xml`
- **Create** `app/src/main/res/values-night/themes.xml`

**Tasks:**
1. In `PaperTheme.kt`, add `PaperDarkColors` object with these values:
   ```
   Paper     = Color(0xFF1A1816)   // warm near-black
   Raised    = Color(0xFF231F1C)   // slightly lifted surface
   Ink       = Color(0xFFF0EBE1)   // warm off-white text
   Graphite  = Color(0xFFB0A898)   // secondary text
   Muted     = Color(0xFF6E6560)   // placeholder / muted
   Hairline  = Color(0xFF3A3530)   // dividers
   Amber     = Color(0xFFD9A441)   // unchanged — accent
   Sage      = Color(0xFF77846F)   // unchanged — accent
   OverlayInk= Color(0xE8F0EBE1)
   ```
2. Add `private val PaperDarkScheme: ColorScheme = darkColorScheme(...)` mapping the same roles.
3. In `NoScrollTheme`, replace `val ignored = isSystemInDarkTheme()` with:
   ```kotlin
   val dark = isSystemInDarkTheme()
   MaterialTheme(colorScheme = if (dark) PaperDarkScheme else PaperScheme, ...)
   ```
4. In `values/colors.xml`, add `<color name="window_bg_light">#F7F3EA</color>` and `<color name="window_bg_dark">#1A1816</color>`. Replace every hardcoded `#F7F3EA` in `themes.xml` with `@color/window_bg_light`.
5. Create `values-night/themes.xml` — copy structure, override `android:windowBackground` to `@color/window_bg_dark`, status/nav bars to `#111009`.
6. In `values-night/themes.xml`, override `Dialog.Paper` surface to `#231F1C`, text to `#F0EBE1`.

**Verification:** Build succeeds. Toggle system dark mode → reader Activity background, dialogs, and Compose UI all flip without crash.

**Exit criteria:** `./gradlew assembleDebug` passes; no hardcoded hex in theme XML except inside `values-night/`.

---

## Step 2 — Color picker + Note dialog: Compose bottom sheets

**Files to edit:**
- `app/src/main/java/com/noscroll/PdfViewerActivity.kt`
- **Create** `app/src/main/java/com/noscroll/ui/HighlightActionSheet.kt`

**Context:** `PdfViewerActivity` already adds a `ComposeView` to `decorView` for the tutorial overlay. We add a second state-driven `ComposeView` for color picker and note sheets.

**Tasks:**

### 2a. Create `HighlightActionSheet.kt`

Two composables:

**`ColorPickerSheet`**
- `ModalBottomSheet` (Material3) with `containerColor = MaterialTheme.colorScheme.surface`
- Title text: passed-in `title` string, `titleMedium` style
- Row of 4 filled circles (52 dp each, 16 dp gap), color from `HIGHLIGHT_COLORS`
- Tap circle → `onPick(color)` + dismiss
- "Cancel" `TextButton` below row
- Sheet drag handle shown

**`NoteSheet`**
- `ModalBottomSheet` with same surface color
- Quote preview: up to 2 lines of `quoteText` in `bodyLarge` Serif italic, `Graphite` color, 16 dp top padding — gives the user visual context for what they're annotating
- `OutlinedTextField` for note: `minLines = 3`, `maxLines = 8`, `label = { Text("Your note") }`, fills full width with 16 dp horizontal padding
- Row at bottom: `TextButton("Cancel")` left, `Button("Save")` right (ink background)
- Pre-fills with `initialNote` if non-empty

### 2b. Wire into PdfViewerActivity

- Add two `MutableState` vars at Activity scope (nullable lambdas or sealed classes):
  ```kotlin
  private var pendingColorPick: ((Int) -> Unit)? = null
  private var pendingNoteData: NoteSheetData? = null   // data class: quoteText, initialNote, onSave
  ```
- Add a second `ComposeView` in `onCreate` after tutorial overlay, same `decorView` pattern:
  ```kotlin
  val actionSheet = ComposeView(this).apply {
      setViewCompositionStrategy(...)
      setContent {
          NoScrollTheme {
              pendingColorPick?.let { onPick ->
                  ColorPickerSheet(
                      title = colorPickerTitle,
                      colors = HIGHLIGHT_COLORS,
                      onPick = { color -> pendingColorPick = null; onPick(color) },
                      onDismiss = { pendingColorPick = null }
                  )
              }
              pendingNoteData?.let { data ->
                  NoteSheet(
                      quoteText = data.quoteText,
                      initialNote = data.initialNote,
                      onSave = { note -> pendingNoteData = null; data.onSave(note) },
                      onDismiss = { pendingNoteData = null }
                  )
              }
          }
      }
  }
  (window.decorView as FrameLayout).addView(actionSheet, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  ```
- Replace `showHighlightColorPicker(title, onPick)` body: set `colorPickerTitle = title; pendingColorPick = onPick`.
- Replace `showAnnotationDialog(highlightId, quoteText)` body: load existing note on IO, then set `pendingNoteData` with save callback that calls `AnnotationRepository.upsert`.
- Remove unused imports: `LinearLayout`, `GradientDrawable`, `EditText`, `InputType`, `Gravity`, `FrameLayout` (unless still needed for Go-to-page dialog — keep if so).

**Verification:** Open PDF → long-press → Highlight → bottom sheet appears with colored circles, dark-mode aware. Annotate → color picker → note sheet shows truncated quote + multi-line text field.

**Exit criteria:** Build passes; no `LinearLayout` or bare `View(this).apply { background = GradientDrawable() }` in `PdfViewerActivity`; both sheets respect dark mode.

---

## Step 3 — Tutorial: rewrite copy + TooltipCard polish

**Files to edit:**
- `app/src/main/java/com/noscroll/tutorial/TutorialStepDefs.kt`
- `app/src/main/java/com/noscroll/tutorial/TutorialOverlay.kt`
- `app/src/main/java/com/noscroll/tutorial/TutorialStep.kt`

### 3a. TutorialStep — add emoji field

Add `val emoji: String = ""` to the data class. `TooltipCard` renders it as large text before the title if non-empty.

### 3b. Rewrite TutorialStepDefs.kt

```kotlin
val SetupTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.SETUP_OVERLAY, emoji = "💬",
        title = "Float the book icon",
        body = "Tap \"Allow\" so the book icon can appear on top of Instagram — that's how you switch to reading with one tap.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.SETUP_ACCESSIBILITY, emoji = "🔍",
        title = "Let NoScroll see Reels",
        body = "This permission lets NoScroll detect when Reels start and swap them for your book. Nothing else is read.",
        TooltipSide.Below
    )
)

val LibraryTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.LIBRARY_IMPORT, emoji = "📥",
        title = "Add your first book",
        body = "Tap the + button to import any PDF from your device. You can also search for books online.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.LIBRARY_FILTERS, emoji = "🗂",
        title = "Sort and filter",
        body = "Use these tabs to jump to favourites, recently read, or books you've highlighted.",
        TooltipSide.Below
    ),
    TutorialStep(
        TutorialStepId.LIBRARY_NOTEBOOK, emoji = "📓",
        title = "Open your notebook",
        body = "Every highlight, note, and quote card you save ends up here — across all your books.",
        TooltipSide.Below
    )
)

val ReaderTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.READER_SELECT, emoji = "✍️",
        title = "Highlight any passage",
        body = "Long-press a word, drag the handles to extend your selection, then tap Highlight — or Annotate to add a note.",
        TooltipSide.Below, spotlightPaddingDp = 6f
    ),
    TutorialStep(
        TutorialStepId.READER_ZEN, emoji = "🧘",
        title = "Zen mode",
        body = "Tap this button to hide all chrome and read distraction-free. Tap anywhere on the page to bring controls back.",
        TooltipSide.Above
    ),
    TutorialStep(
        TutorialStepId.READER_CONTROLS, emoji = "👆",
        title = "Show or hide controls",
        body = "Tap anywhere on the page to toggle the toolbar. Long-press the title bar to edit book metadata.",
        TooltipSide.Below
    )
)

val NotebookTutorialSteps = listOf(
    TutorialStep(
        TutorialStepId.NOTEBOOK_HIGHLIGHTS, emoji = "🔖",
        title = "Your saved highlights",
        body = "Tap any entry to jump back to that page. Long-press for a shareable quote card.",
        TooltipSide.Below
    )
)
```

### 3c. TooltipCard improvements (TutorialOverlay.kt)

- Render emoji before title: `if (step.emoji.isNotEmpty()) Text(step.emoji, fontSize = 28.sp, modifier = Modifier.padding(bottom = 4.dp))`
- Corner radius 16 dp → 20 dp on the `Card`.
- Replace hardcoded `PaperColors.*` with `MaterialTheme.colorScheme.*`:
  - `PaperColors.Raised` → `MaterialTheme.colorScheme.surface`
  - `PaperColors.Hairline` → `MaterialTheme.colorScheme.outline`
  - `PaperColors.Ink` → `MaterialTheme.colorScheme.onSurface`
  - `PaperColors.Muted` → `MaterialTheme.colorScheme.onSurfaceVariant`
  - `PaperColors.Graphite` → `MaterialTheme.colorScheme.onSurfaceVariant`
- Step counter: replace `"${index+1} / ${count}"` with a dot-row progress indicator — N dots, filled dot for current step, hollow for future:
  ```kotlin
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      repeat(controller.stepCount()) { i ->
          Box(Modifier.size(6.dp).background(
              if (i == controller.stepIndex()) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.outline,
              CircleShape
          ))
      }
  }
  ```

**Verification:** Tutorial flow in light + dark mode. Steps show emojis, dot progress, correct colors.

**Exit criteria:** Build passes; no hardcoded `PaperColors.*` in `TutorialOverlay.kt`.

---

## Dependency Order

```
Step 1 (dark mode theme) ──► Step 2 (sheets inherit correct colorScheme)
Step 1 (dark mode theme) ──► Step 3 (tutorial card uses MaterialTheme)
Step 2 and Step 3 are independent of each other.
```

## Invariants (check after every step)

- `./gradlew assembleDebug` passes
- No hardcoded hex colors in Kotlin files (use `PaperColors.*` or `MaterialTheme.colorScheme.*`)
- `TutorialController`, `TutorialPrefs`, `TutorialAnchor` untouched
- Existing highlight save / annotation save logic unchanged — only presentation layer changes
