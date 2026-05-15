# Plan: Reader Polish — 4 Features

**Branch:** `feat/reader-polish-4-features`  
**Base:** `master`  
**Created:** 2026-05-15  
**Scope:** 5 sequential steps, no parallelism (each builds on prior DB/UI state)

---

## Objective

Four targeted improvements to the no-scroll reader:

1. Tap any highlight → options to **delete** or **change colour**
2. Metadata pipeline guaranteed to resolve **title + author** automatically
3. **Single scrollbar** — suppress native Android bar, keep the silhouette track
4. **Full UI coherence pass** — consistent, elegant from library to reader

---

## Context Brief (for any agent picking up a step cold)

**Project:** Android PDF reader (`com.noscroll`). Kotlin + Jetpack Compose (library/notebook screens) + XML Views (reader screen). Room DB via `AnnotationDatabase`. No backend.

**Key files:**
- Reader activity: `app/src/main/java/com/noscroll/PdfViewerActivity.kt`
- PDF fragment: `app/src/main/java/com/noscroll/NoScrollPdfViewerFragment.kt`
- Highlight data: `data/HighlightEntity.kt`, `data/HighlightDao.kt`, `repository/HighlightRepository.kt`
- Metadata: `metadata/BookMetadataRepository.kt`, `metadata/GoogleBooksClient.kt`, `metadata/OpenLibraryClient.kt`, `metadata/MetadataLookupPrefs.kt`
- Library screen (Compose): `ui/LibraryScreen.kt`, `ui/PaperComponents.kt`, `ui/PaperTheme.kt`
- Notebook screen (Compose): `ui/NotebookScreen.kt`
- Reader layout: `res/layout/activity_pdf_viewer.xml`
- Theme: `res/values/themes.xml`, `res/values/colors.xml`

**Design language:** Warm paper tones (`PaperColors` object), serif type, gold accent (`#C9A84C`). Reader is dark (`#171615` bars). Library is light cream.

---

## Step 1 — Highlight data layer: add `updateColor`

**Dependencies:** none  
**Model tier:** default  
**Risk:** low (additive DB query + repo method)

### Context
`HighlightEntity` has `colorArgb: Int`. No update path exists — only insert and delete. The highlight color picker UI (`showHighlightColorPicker`) already exists in `PdfViewerActivity` for new highlights but is not wired to existing ones.

### Tasks

1. **`data/HighlightDao.kt`** — add:
   ```kotlin
   @Query("UPDATE highlights SET colorArgb = :color, updatedAtMillis = :ts WHERE id = :id")
   suspend fun updateColor(id: Long, color: Int, ts: Long = System.currentTimeMillis())
   ```

2. **`repository/HighlightRepository.kt`** — add:
   ```kotlin
   suspend fun updateColor(context: Context, id: Long, color: Int) =
       AnnotationDatabase.getInstance(context).highlightDao().updateColor(id, color)
   ```

### Verification
- Build compiles without errors (`./gradlew assembleDebug`)
- No existing tests broken

### Exit criteria
`HighlightRepository.updateColor` exists and compiles.

---

## Step 2 — Tap highlight → delete / recolour popup

**Dependencies:** Step 1  
**Model tier:** default  
**Risk:** low (UI-only change in existing method)

### Context
`PdfViewerActivity.onPdfPointTapped` already detects taps on highlight bounding boxes and calls `showHighlightActions(hit)`. That method currently shows `["Edit note", "Share quote", "Delete"]`. Need to insert "Change colour" which reuses the existing color circle picker but saves to the existing highlight instead of creating a new one.

### Tasks

1. **`PdfViewerActivity.kt` — `showHighlightActions`**  
   Change items array to `["Change colour", "Edit note", "Share quote", "Delete"]`.  
   Add `when (which)` case `0` calling a new private method `recolourHighlight(highlight)`.  
   Shift existing cases: `"Edit note"` → `1`, `"Share quote"` → `2`, `"Delete"` → `3`.

2. **`PdfViewerActivity.kt` — add `recolourHighlight(highlight: HighlightEntity)`**  
   Copy the circle-row builder from `showHighlightColorPicker` but on click call:
   ```kotlin
   lifecycleScope.launch {
       withContext(Dispatchers.IO) {
           HighlightRepository.updateColor(this@PdfViewerActivity, highlight.id, color)
       }
       reloadHighlights()
   }
   ```
   Dialog title: `"Change colour"`.

### Verification
- Tap a highlight in the reader → popup appears with 4 options
- Selecting a new colour updates the highlight rendering on screen immediately
- "Delete" still removes the highlight

### Exit criteria
All 4 actions (Change colour / Edit note / Share quote / Delete) reachable by single tap on any highlight.

---

## Step 3 — Metadata: guaranteed title + author resolution

**Dependencies:** none  
**Model tier:** default  
**Risk:** medium (touches metadata pipeline; cache entries are overwritten on upgrade)

### Context

**What works:** Google Books and Open Library clients are implemented. `MetadataLookupPrefs.isOnlineLookupEnabled` defaults to `true`. `lookupAuthorByTitle` runs when cached author is "Unknown Author" and online is enabled.

**What's broken / weak:**

a. `titlesMatch()` uses `String.contains()`. "Atomic Habits" does not contain "Atomic Habits: Tiny Changes, Remarkable Results" — but the reverse is true. This mostly works but fails for partial-word overlap edge cases (e.g., "The Road" vs "Road to Perdition"). Replace with **word-overlap ≥ 50%**.

b. `renderMetadata` appends `"  Review"` when `confidence < 0.5`. This is an internal debug label, not user-facing. **Remove it entirely.**

c. `PdfViewerActivity.openPdf` calls `loadMetadata`, then `onPdfLoaded` calls it again — two concurrent coroutines that both read stale cache. **Remove the call from `openPdf`; keep only `onPdfLoaded`.**

d. Change `onPdfLoaded` to call `loadMetadata(document.uri, allowOnlineOnce = true)` so the full online pipeline always runs on book open.

e. Top bar shows empty/spinner while resolving. Set an immediate filename-based fallback in `metadataText` before the async lookup completes.

### Tasks

1. **`metadata/BookMetadataRepository.kt` — replace `titlesMatch`**:
   ```kotlin
   private fun titlesMatch(expected: String, actual: String): Boolean {
       val left = normalizeTitle(expected).split(" ").filter { it.length > 2 }.toSet()
       val right = normalizeTitle(actual).split(" ").filter { it.length > 2 }.toSet()
       if (left.isEmpty() || right.isEmpty()) return false
       val smaller = if (left.size <= right.size) left else right
       val larger  = if (left.size <= right.size) right else left
       return smaller.count { it in larger }.toFloat() / smaller.size >= 0.5f
   }
   ```

2. **`PdfViewerActivity.kt` — `openPdf`**  
   Remove `metadataText.text = "Identifying book..."` and `loadMetadata(uri, allowOnlineOnce = false)`.  
   Instead set `metadataText.text = uri.lastPathSegment?.substringBeforeLast('.') ?: "..."` as an immediate placeholder.

3. **`PdfViewerActivity.kt` — `onPdfLoaded`**  
   Change `loadMetadata(document.uri, allowOnlineOnce = false)` → `loadMetadata(document.uri, allowOnlineOnce = true)`.

4. **`PdfViewerActivity.kt` — `renderMetadata`**  
   Remove the `review` variable and suffix. Use em-dash separator:
   ```kotlin
   private fun renderMetadata(metadata: BookMetadataEntity) {
       metadataText.text = "${metadata.title} — ${metadata.author}"
       metadataLookupButton.visibility =
           if (metadata.source == "manual" && !MetadataLookupPrefs.isOnlineLookupEnabled(this)) View.VISIBLE else View.GONE
   }
   ```

### Verification
- Open a well-known PDF → top bar resolves to `"Title — Author"` within a few seconds
- No "Review" string visible in the UI anywhere
- Only one metadata coroutine fires per book open (Logcat: single Google Books request)

### Exit criteria
Top bar shows `"<clean title> — <real author>"`. No "Review" badge. No duplicate network requests.

---

## Step 4 — Single scrollbar: suppress native, keep silhouette

**Dependencies:** none  
**Model tier:** default  
**Risk:** low (visibility/flag changes only)

### Context

Screenshots show two simultaneous scroll indicators:
- **Native Android scrollbar** — thin gray bar on the far-right edge of the PDF content area
- **Custom silhouette track** — darker, themed, appears ~30dp from right edge

`NoScrollPdfViewerFragment.disableScrollbars()` already runs recursively but the `androidx.pdf` `PdfView` may re-enable scrollbars post-layout. `isScrollbarFadingEnabled = true` still draws the bar briefly. `VerticalSeekBar` (`pageSeekbar`) is already always `GONE` but the XML and all references remain as dead code.

### Tasks

1. **`NoScrollPdfViewerFragment.kt` — strengthen `disableScrollbars`**:
   ```kotlin
   private fun disableScrollbars(view: View?) {
       view ?: return
       view.isVerticalScrollBarEnabled = false
       view.isHorizontalScrollBarEnabled = false
       view.isScrollbarFadingEnabled = false
       view.scrollBarSize = 0
       if (view is ViewGroup) {
           for (i in 0 until view.childCount) disableScrollbars(view.getChildAt(i))
       }
   }
   ```

2. **`NoScrollPdfViewerFragment.kt` — `onPdfViewCreated`**  
   Add a second deferred disabling after layout completes:
   ```kotlin
   pdfView.postDelayed({ disableScrollbars(pdfView.rootView) }, 400)
   ```

3. **`res/layout/activity_pdf_viewer.xml`** — delete the `<com.noscroll.VerticalSeekBar>` element (id `page_seekbar`).

4. **`PdfViewerActivity.kt`** — remove all `pageSeekbar` references:
   - Field declaration
   - `findViewById` call
   - `onProgressChanged` setup
   - All `pageSeekbar.visibility`, `pageSeekbar.max`, `pageSeekbar.progress`, `pageSeekbar.isEnabled`, `pageSeekbar.isDragging` usages

5. **`VerticalSeekBar.kt`** — delete the file.

### Verification
- Open any PDF → only the silhouette track visible on right edge, no native gray bar
- Silhouette thumb correctly positions and responds to touch drag
- Build has no `VerticalSeekBar` symbol references

### Exit criteria
No native scrollbar artifacts visible in any state. Silhouette track functions correctly for page navigation.

---

## Step 5 — UI coherence pass

**Dependencies:** Steps 3 (metadata bar updated), 4 (layout cleaned)  
**Model tier:** default  
**Risk:** medium (many files; no logic changes)

### Context

Full audit of visual inconsistencies across all screens:

| Area | Issue |
|------|-------|
| Reader FAB | `make_quote_fab` uses checkmark icon — wrong for "make quote" |
| Library chips | Selected/unselected text colours identical → low contrast |
| Library sort chips | Same visual weight as filter chips — hard to distinguish |
| `DocumentRow` | 3 stacked chips on right (Favorite/Identify/Remove) → crowded |
| `ContinueRow` source | Uses `books` not `visibleBooks` → bypasses active filter |
| `QuoteCardPreviewActivity` | Wrong theme (`PdfViewer` dark) for a light preview screen |
| Theme parents | Mix of `MaterialComponents` and `Material3` |
| Annotation dialog | `EditText` has no padding inside dialog |
| Notebook dividers | Divider inside row body causes double lines at section headers |
| Dead files | `bottom_sheet_share.xml`, `item_pdf_entry.xml`, `item_pdf_add.xml` |
| `PdfLibraryAdapter.kt` | Dead code (library uses Compose) |

### Tasks

1. **`res/values/themes.xml`**  
   - `Theme.NoScroll` parent: `Theme.Material3.DayNight.NoActionBar`  
   - `Theme.NoScroll.NoActionBar` parent: `Theme.Material3.Light.NoActionBar`  
   (Remove `DarkActionBar` variant)

2. **`AndroidManifest.xml`** — change `QuoteCardPreviewActivity` theme:  
   `android:theme="@style/Theme.NoScroll.NoActionBar"`

3. **`res/layout/activity_pdf_viewer.xml`** — `make_quote_fab`  
   Change `android:src="@drawable/ic_check_circle"` → `@drawable/ic_goto_page` (reuse existing "go" icon) as a stopgap until a proper quote icon is added.  
   Update `android:contentDescription` to `"Make quote card"` (already correct in strings.xml).

4. **`ui/LibraryScreen.kt` — `CompactChip`**  
   Pass `selected` into the `Text` color: selected → `PaperColors.Ink`, unselected → `PaperColors.Graphite`.  
   ```kotlin
   Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) PaperColors.Ink else PaperColors.Graphite, maxLines = 1)
   ```

5. **`ui/LibraryScreen.kt` — sort chips**  
   Give sort chips `height = 32.dp` (vs filter chips `38.dp`) and use `MaterialTheme.typography.labelSmall`.

6. **`ui/LibraryScreen.kt` — `ContinueRow` source**  
   Move `recent` computation to use `visibleBooks` instead of `books`.

7. **`ui/PaperComponents.kt` — `DocumentRow` actions**  
   Replace the right `Column` with a single `IconButton` wrapping `ic_more_vert` that opens a `DropdownMenu` with items: "Favourite" / "Unfavourite", "Identify", "Remove". Removes the crowded stacked-chips pattern.

8. **`PdfViewerActivity.kt` — dialog input padding**  
   In `showAnnotationDialog` and `showOcrSelectionDialog`, wrap `EditText` in a `FrameLayout`:
   ```kotlin
   val container = FrameLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
   container.addView(input)
   // Pass container to .setView(container)
   ```

9. **`ui/NotebookScreen.kt` — dividers**  
   Remove `Divider` from inside `NotebookQuoteRow`. Add it in the `items` block (after each row call) in `NotebookScreen`.

10. **Delete dead files** (verify each has no remaining references first):
    - `res/layout/bottom_sheet_share.xml`
    - `res/layout/item_pdf_entry.xml`
    - `res/layout/item_pdf_add.xml`
    - `java/com/noscroll/PdfLibraryAdapter.kt`

11. **`ui/LibraryScreen.kt` — empty state**  
    Replace `PaperCover("PDF", ...)` in empty state with a simple `Text("📚", style = MaterialTheme.typography.displayLarge)` or remove entirely and rely on the title + button below.

### Verification
- Library: chips clearly differentiate selected/unselected; sort chips visually subordinate to filter chips
- Document rows: single overflow icon per row; dropdown shows all 3 actions
- Quote preview opens with warm light background, not dark
- Annotation dialog inputs have clear breathing room
- Notebook has clean single dividers between items
- Build has zero references to deleted files
- All themes resolve without lint warnings about unknown parent

### Exit criteria
Full visual pass complete. Every screen uses Paper cream / Ink / Amber colour tokens consistently. No dead XML layouts. No mismatched theme parents.

---

## Rollback Strategy

All steps are additive or cosmetic. No schema migrations. Safe to revert any step via `git revert`:
- Steps 1/2: highlight data is safe; DB schema only adds a query, not a column
- Step 3: metadata cache is self-correcting on next lookup
- Step 4: `VerticalSeekBar.kt` deletion can be recovered from git history
- Step 5: purely cosmetic; each task is an independent diff

---

## Execution Order

```
Step 1 ──► Step 2        (highlight tap UX)
Step 3                   (metadata pipeline, independent)
Step 4                   (scrollbar, independent)
              └──────────────────┐
                               Step 5   (UI coherence, last)
```
