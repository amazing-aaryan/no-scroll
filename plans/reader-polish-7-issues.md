# Reader Polish — 7 Issues
**Branch:** `codex/reader-selection-highlights-zen`
**Base:** current HEAD `5f2abbc`
**Created:** 2026-05-16

---

## Context Brief (cold-start summary)

**Project:** NoScroll — Android PDF reader that overlays a book icon on Instagram Reels to nudge reading.

**Key files:**
- `PdfViewerActivity.kt` — host activity; owns all UI, zen mode, overflow menu, selection rail
- `NoScrollPdfViewerFragment.kt` — wraps AndroidX `PdfViewerFragment`; injects selection menu items via `SelectionMenuItemPreparer`
- `BookMetadataRepository.kt` — metadata pipeline: cache → Groq Vision → OCR → network lookup
- `GroqVisionClient.kt` — sends cover bitmap to Groq LLaMA 3.2 Vision; `API_KEY` via `BuildConfig.GROQ_API_KEY`
- `NoScrollAccessibilityService.kt` — detects Instagram nav bar; `findReelsNode` looks for `contentDescription.contains("reels")`
- `MainActivity.kt` — routes to SetupActivity or shows a button to open the library
- `activity_pdf_viewer.xml` — layout: top bar (44dp) | PDF fragment | bottom bar (52dp); floating selection pill at bottom center

**Invariants:**
- `local.properties` holds `GROQ_API_KEY` — never commit
- `buildConfig true` + `buildConfigField` in `app/build.gradle` exposes it as `BuildConfig.GROQ_API_KEY`
- Back button already routes through `onBackPressed()` which exits zen first

---

## Issues → Steps mapping

| Issue | Step |
|-------|------|
| 1. Metadata (title/author) never resolves | Step 1 |
| 2. Reel overlay matches wrong nodes | Step 2 |
| 3. UI centering + back=zen exit | Step 3 |
| 4. Launch → Library automatically | Step 4 |
| 5. Selection menu: all 6 inline, kill secondary pill | Step 5 |
| 6. Remove bottom bar | Step 6 |
| 7. Slim top nav bar replaces overflow menu | Step 6 (coupled with 6) |

---

## Step 1 — Fix metadata pipeline
**Files:** `BookMetadataRepository.kt`, `GroqVisionClient.kt`
**Deps:** none

### Problem
- Cache short-circuit at line 30–32 returns stale entries with `"Unknown Author"` before Groq is tried when `source` is already `"cover_ocr"` or `"network"` — so repeated opens skip Vision entirely.
- `allowOnlineOnce=true` is passed from `onPdfLoaded` only on first load; if a stale cached row exists with bad data, it skips the Groq path.
- `GroqVisionClient.API_KEY` is now a `val` property getter (`get() = BuildConfig.GROQ_API_KEY`) — must verify `BuildConfig` is generated and non-empty.

### Fix
1. **Cache invalidation:** In `resolve()`, after the `manual` / `vision_ai` guard, also return cached only if `source == "network"` AND `confidence >= 0.8f` AND `author != "Unknown Author"`. Otherwise fall through to re-run the pipeline.
2. **Force re-run for bad cached:** If `cached != null && cached.author == "Unknown Author"` and `allowOnlineOnce=true`, delete the cached row before re-running Vision + network.
3. **Groq error logging:** Wrap `GroqVisionClient.identify()` and log the raw HTTP response code on failure so we can distinguish 401 (bad key) vs 429 (rate limit) vs network error. Use `android.util.Log.e("GroqVision", "HTTP $code")`.
4. **OCR fallback:** Verify `CoverPageOcr.extractText(bitmap)` returns a plain `String`. If it returns `com.google.mlkit.vision.text.Text`, extract `.text` property before passing to `extractFromCoverOcr`.

### Task list
- [ ] Read `CoverPageOcr.kt` — verify return type of `extractText`
- [ ] Fix cache short-circuit in `BookMetadataRepository.resolve()`
- [ ] Add force-re-run when cached has `"Unknown Author"` and `allowOnlineOnce=true`
- [ ] Add error logging in `GroqVisionClient.identify()`
- [ ] Build + install + test with a known PDF

### Verification
- Open a PDF with known title/author → metadata bar shows correct title within 5 seconds
- Re-open same PDF → uses cache, no network call
- Delete app data → re-runs pipeline from scratch

---

## Step 2 — Fix reel icon detection
**Files:** `NoScrollAccessibilityService.kt`
**Deps:** none

### Problem
`findReelsNode` traverses the **entire** accessibility tree and returns the first node whose `contentDescription` contains "reels". This matches reel post thumbnails, story items, and any text inside a reel — not just the bottom nav Reels tab.

Instagram's Reels nav tab characteristics (from reference image — clapperboard with play triangle):
- `contentDescription` = "Reels" (exact) or "Reels tab"
- Lives in the bottom nav bar: `nodeRect.top > screenHeight * 0.80`
- Width ≤ screenWidth / 3
- `isClickable == true`

### Fix
Replace `findReelsNode` with strict matching:

```kotlin
private fun findReelsNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    val screenHeight = resources.displayMetrics.heightPixels
    val screenWidth = resources.displayMetrics.widthPixels
    val navThreshold = (screenHeight * 0.80).toInt()
    val maxTabWidth = screenWidth / 3

    val queue = ArrayDeque<AccessibilityNodeInfo>()
    for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        val desc = node.contentDescription?.toString() ?: ""
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)

        val descMatch = desc.equals("Reels", ignoreCase = true) ||
            desc.equals("Reels tab", ignoreCase = true)
        val inNavBar = nodeRect.top >= navThreshold
        val narrowEnough = nodeRect.width() in 1..maxTabWidth

        if (descMatch && inNavBar && narrowEnough && node.isClickable) {
            queue.forEach { it.recycle() }
            return node
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        node.recycle()
    }
    return null
}
```

Also tighten `findBottomNavCenter` fallback: after finding the nav bar, try to find a child with desc containing "reels" before falling back to the center child.

### Task list
- [ ] Replace `findReelsNode` with strict version above
- [ ] Update `findBottomNavCenter` to try reels-desc match before center-child fallback
- [ ] Build + install + test on Instagram

### Verification
- Open Instagram → overlay appears exactly on the Reels tab icon
- Scroll through a reel post → overlay stays on nav tab, not on reel content
- Navigate to Home/Search tab → overlay icon moves or hides correctly

---

## Step 3 — UI centering polish (do AFTER Step 6)
**Files:** `activity_pdf_viewer.xml`, `PdfViewerActivity.kt`
**Deps:** Step 6

### Back button in zen (verify only)
`onBackPressed()` at line 832 already calls `setZenMode(false)` when `zenModeEnabled`. **No code change needed.** Verify on device.

### UI polish tasks
1. **Metadata bar title:** Increase `textSize` 12sp → 13sp. Add `android:fontFamily="sans-serif-light"`.
2. **Zen exit FAB:** Move `layout_gravity` from `top|end` to `top|center`. Increase `android:alpha` from 0.35 → 0.55.
3. **Selection pill margin:** After bottom bar removal, update `layout_marginBottom` from 60dp → 24dp (pill floats just above the PDF content now, no bottom bar below it).
4. **Top bar spacing:** After Step 6 adds 4 icon buttons, ensure `paddingEnd` is 4dp and buttons are evenly spaced — use `layout_weight` on a Spacer between title and button group if needed.

### Task list
- [ ] (Verify) Test back button exits zen on device — no code change if working
- [ ] Update metadata text size + font
- [ ] Update zen exit FAB position + alpha
- [ ] Update selection pill bottom margin
- [ ] Build + visual check

---

## Step 4 — Auto-launch Library on app start
**Files:** `MainActivity.kt`, `res/layout/activity_main.xml` (delete or keep as tombstone)
**Deps:** none

### Fix
In `MainActivity.route()`, replace the `else` branch:

```kotlin
// BEFORE:
else -> {
    setContentView(R.layout.activity_main)
    findViewById<MaterialButton>(R.id.open_library_btn).setOnClickListener {
        startActivity(Intent(this, PdfLibraryActivity::class.java))
    }
}

// AFTER:
else -> {
    startActivity(Intent(this, PdfLibraryActivity::class.java))
    finish()
}
```

Check if `activity_main.xml` and `R.id.open_library_btn` are referenced anywhere else before deleting them. If unused, delete the layout file to keep the project clean.

### Task list
- [ ] Edit `MainActivity.kt` else branch
- [ ] Grep for `activity_main` and `open_library_btn` usage — delete layout if unused
- [ ] Build + install + verify immediate library launch

### Verification
- Launch app with permissions granted → Library screen opens immediately
- No intermediate "Open Library" button screen appears

---

## Step 5 — Fix text selection menu + kill secondary pill
**Files:** `NoScrollPdfViewerFragment.kt`, `activity_pdf_viewer.xml`, `PdfViewerActivity.kt`
**Deps:** none (do before Step 6 to avoid merge conflicts)

### Problem A — Overflow items
The AndroidX `PdfViewerFragment` shows Copy + Select All natively, our 4 items (Highlight, Annotate, Quote, Share) go into overflow behind `...`. We want all 6 inline.

The framework puts items in overflow when total count > ~3. Since we can't remove Copy/Select All, we need shorter labels so all items fit the menu bar width. Use single-word labels: "Mark" → Highlight, "Note" → Annotate, "Card" → Quote, "Share" stays.

OR: keep current labels but test on device first — on wider screens they may already appear inline.

**Decision:** Test first. If already inline → no change. If in overflow → shorten to: `Mark`, `Note`, `Card`, `Share`.

### Problem B — Kill the secondary selection pill
Remove `selection_action_rail` entirely:

**XML changes** (`activity_pdf_viewer.xml`):
- Delete the entire `<FrameLayout android:id="@+id/selection_action_rail">` block (lines ~161–235 in current file)
- Delete the hidden `<LinearLayout android:visibility="gone">` stub block (lines ~124–158)

**Activity changes** (`PdfViewerActivity.kt`):
- Remove field: `private lateinit var selectionRail: View`
- Remove: `selectionRail = findViewById(R.id.selection_action_rail)`
- Remove all: `selectionRail.visibility = View.VISIBLE/GONE`
- Remove `setOnClickListener` for `action_highlight`, `action_annotate`, `action_quote`, `action_share`
- Remove those 4 `findViewById` calls from `setupControls()`
- The `currentSelection` field and `onPdfTextSelectionChanged` logic stays — used by `handleSelectionAction`

### Task list
- [ ] Test on device: are custom menu items inline or in overflow?
- [ ] Shorten labels if in overflow: "Mark" / "Note" / "Card" / "Share"
- [ ] Remove `selection_action_rail` FrameLayout from XML
- [ ] Remove hidden stub LinearLayout from XML
- [ ] Remove `selectionRail` field + all references from Activity
- [ ] Remove 4 action button `setOnClickListener` calls from `setupControls()`
- [ ] Build + test selection still triggers `handleSelectionAction` correctly

---

## Step 6 — Remove bottom bar + add slim top nav
**Files:** `activity_pdf_viewer.xml`, `PdfViewerActivity.kt`
**Deps:** Step 5

### Part A — Remove bottom bar

**XML:** Delete `<LinearLayout android:id="@+id/reader_bottom_bar">` block entirely.

**Activity:** Remove:
- Fields: `changeFab`, `gotoPageFab`, `makeQuoteFab`, `readerBottomBar`
- `findViewById` calls for all 4
- `setOnClickListener` for all 3 FABs in `setupControls()`
- `readerBottomBar.visibility` line in `setZenMode()`
- `showManualQuoteDialog()` method (was tied to makeQuoteFab — manual quote card is now access via highlight action flow)

### Part B — New slim top nav bar

**XML:** Replace the current top bar content:

```xml
<FrameLayout
    android:id="@+id/metadata_bar"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:background="#1B1917">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="8dp"
        android:paddingEnd="4dp">

        <!-- Library back button -->
        <ImageButton
            android:id="@+id/library_btn"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="My Library"
            android:src="@drawable/ic_book"
            android:tint="#80FFFFFF"
            android:padding="10dp" />

        <!-- Title / Author -->
        <TextView
            android:id="@+id/book_metadata_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="1"
            android:text="..."
            android:textColor="#D0F5F0E8"
            android:textSize="13sp"
            android:fontFamily="sans-serif-light"
            android:paddingStart="8dp"
            android:paddingEnd="8dp" />

        <!-- Zen mode toggle -->
        <ImageButton
            android:id="@+id/zen_btn"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Zen mode"
            android:src="@drawable/ic_check_circle"
            android:tint="#80FFFFFF"
            android:padding="10dp" />

        <!-- Highlights list -->
        <ImageButton
            android:id="@+id/highlights_nav_btn"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Highlights"
            android:src="@drawable/ic_book"
            android:tint="#80FFFFFF"
            android:padding="10dp" />

        <!-- Go to page -->
        <ImageButton
            android:id="@+id/goto_page_nav_btn"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Go to page"
            android:src="@drawable/ic_goto_page"
            android:tint="#80FFFFFF"
            android:padding="10dp" />

        <!-- Share -->
        <ImageButton
            android:id="@+id/share_nav_btn"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Share"
            android:src="@android:drawable/ic_menu_share"
            android:tint="#80FFFFFF"
            android:padding="10dp" />

        <!-- Metadata lookup (shown conditionally) -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/metadata_lookup_btn"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:text="@string/metadata_lookup"
            android:textColor="@color/colorAccent"
            android:textSize="11sp"
            android:visibility="gone" />

    </LinearLayout>
</FrameLayout>
```

**Activity wiring:**
```kotlin
// New fields:
private lateinit var libraryBtn: ImageButton
private lateinit var zenBtn: ImageButton
private lateinit var highlightsNavBtn: ImageButton
private lateinit var gotoPageNavBtn: ImageButton
private lateinit var shareNavBtn: ImageButton

// In setupControls():
libraryBtn.setOnClickListener { launchLibrary() }
zenBtn.setOnClickListener { setZenMode(!zenModeEnabled) }
highlightsNavBtn.setOnClickListener { showHighlightsDialog() }
gotoPageNavBtn.setOnClickListener { showGotoPageDialog() }
shareNavBtn.setOnClickListener { shareCurrentPage() }

// Remove: overflowMenuBtn, showOverflowMenu()
// Remove: metadataLookupButton long-press title remains

// Add:
private fun shareCurrentPage() {
    val metadata = currentMetadata ?: return
    val text = "${metadata.title} — ${metadata.author}"
    startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share"
    ))
}
```

**Zen mode update:** In `setZenMode()`, remove `readerBottomBar.visibility` line. Keep `metadataBar.visibility` toggle. The `ic_goto_page` drawable is reused for go-to-page button (already in project).

**Note on icons:** `@android:drawable/ic_menu_share` is a system drawable — available on all API levels. If a custom share icon exists in the project, use it instead. Check `res/drawable/` for `ic_share*`.

### Task list
- [ ] Remove bottom bar XML block
- [ ] Replace top bar XML with new 5-button layout (library + 4 actions)
- [ ] Remove old fields/listeners from Activity
- [ ] Add new fields + wire listeners in `setupControls()`
- [ ] Add `shareCurrentPage()` method
- [ ] Remove `showOverflowMenu()` and all its menu-item dispatch logic
- [ ] Check `res/drawable/` for `ic_share` — use if exists, else use system drawable
- [ ] Update `setZenMode()` — remove bottom bar reference, keep metadata bar toggle
- [ ] Build + install + full UI walkthrough

### Verification
- No bottom bar in any state
- Top bar: [Book] Title — Author [Zen][Highlights][GoPage][Share]
- All 4 top actions work
- Zen hides top bar; back or zen exit FAB restores it

---

## Execution order

```
Parallel first pass:
  Step 1 (metadata fix)
  Step 2 (reel detection)
  Step 4 (auto-launch library)
  Step 5 (selection menu + kill pill)

Sequential second pass:
  Step 6 (remove bottom bar + top nav)  ← after Step 5 to avoid XML conflicts
  Step 3 (UI polish)                    ← after Step 6 for layout to settle
```

---

## Rollback
Each step is a contained diff to 1–3 files. `git revert <step-commit>` per step. No DB schema changes. No new dependencies.

---

## Exit criteria
- [ ] Metadata bar shows correct title + author for known PDF within 5s
- [ ] Overlay appears on Instagram Reels nav tab only — not on reel post content
- [ ] Back button in zen mode exits zen (not activity)
- [ ] Launch app with perms → Library screen immediately, no button press
- [ ] Text selection → all custom actions visible inline; no bottom pill
- [ ] No bottom bar visible in any UI state
- [ ] Top bar: library icon + title + 4 action icons (Zen, Highlights, GoPage, Share)
- [ ] Zen mode: top bar hidden, only zen exit FAB visible
- [ ] `./gradlew assembleDebug` passes with no warnings
