# Fix: Highlight/Quote Bugs + Remove OCR Button

**Branch:** `fix/highlight-quote-bugs`
**Base:** `codex/reader-selection-highlights-zen`

## Problems

1. **Highlight not saved on "Quote" action** — `onPdfSelectionAction(QUOTE)` calls `openQuotePreview` + `clearSelection` but never writes to `HighlightRepository`. User loses the selection permanently.
2. **Garbled characters in quote text** — `TextSelection.text.toString()` from AndroidX PDF returns raw Unicode that may contain ligature code points (ﬁ/ﬂ), compatibility forms, or control chars. Visible as scrambled glyphs in the quote card. Fix: apply NFKC normalization and strip control chars at the `ReaderSelection` construction site.
3. **OCR page button** — user-requested removal from overflow menu. Dead code can also be deleted.

---

## Step 1 — Fix: quote action auto-saves highlight

**Context brief:** All highlight logic lives in `PdfViewerActivity.kt`. `onPdfSelectionAction` is the dispatch switch at line 202. `saveHighlightWithColor` at line 278 persists to `HighlightRepository` and reloads the view. Default highlight color constant is in `NoScrollPdfViewerFragment.DEFAULT_HIGHLIGHT_COLOR`.

**File:** `app/src/main/java/com/noscroll/PdfViewerActivity.kt`

**Task list:**
- [ ] In `onPdfSelectionAction` for `SelectionAction.QUOTE`, call `saveHighlightWithColor(selection, openNote = false, colorArgb = NoScrollPdfViewerFragment.DEFAULT_HIGHLIGHT_COLOR)` before calling `openQuotePreview`.
- [ ] Remove `clearSelection()` from the QUOTE branch — `saveHighlightWithColor` already calls it at the end of its coroutine.

**Before:**
```kotlin
SelectionAction.QUOTE -> {
    openQuotePreview(selection.text, selection.pageIndex)
    clearSelection()
}
```

**After:**
```kotlin
SelectionAction.QUOTE -> {
    saveHighlightWithColor(selection, openNote = false,
        colorArgb = NoScrollPdfViewerFragment.DEFAULT_HIGHLIGHT_COLOR)
    openQuotePreview(selection.text, selection.pageIndex)
}
```

**Verification:** Build passes. Select text → Quote → verify highlight renders on the page after returning to reader.

**Exit criteria:** `HighlightRepository.getForBook` returns the new entry after QUOTE action.

---

## Step 2 — Fix: normalize quote text to prevent garbled characters

**Context brief:** `ReaderSelection.text` is set in `NoScrollPdfViewerFragment.kt` at line 90: `text = it.text.toString()`. AndroidX PDF alpha `TextSelection.text` is a `CharSequence` that may contain PDF-internal Unicode (ligature chars, PUA code points, compatibility forms). NFKC normalization converts ﬁ→fi, ﬂ→fl, ﬃ→ffi etc. Stripping chars in `[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\uFFFD]` removes garbage.

**File:** `app/src/main/java/com/noscroll/NoScrollPdfViewerFragment.kt`

**Task list:**
- [ ] Add import: `import java.text.Normalizer`
- [ ] Replace `text = it.text.toString()` with:
  ```kotlin
  text = Normalizer.normalize(it.text.toString(), Normalizer.Form.NFKC)
      .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F�]"), "")
  ```

**Verification:** Build passes. Select text containing "fi" or "fl" ligatures in a PDF — characters should render correctly in the quote card.

**Exit criteria:** No replacement chars (□ or ) in quote cards from normal Latin-script PDFs.

---

## Step 3 — Remove OCR page button + dead code

**Context brief:** OCR page feature is accessed from `showOverflowMenu()` at line 585. It adds menu item id=2 "OCR page" and routes to `ocrCurrentPage()`. The feature is no longer wanted. Three methods handle it: `ocrCurrentPage()`, `showOcrSelectionDialog()`, `recognizeCurrentPage()`. Also check `PdfRenderer` import — `renderCoverBitmap` at line 532 still uses it for metadata cover, so keep the import.

**File:** `app/src/main/java/com/noscroll/PdfViewerActivity.kt`

**Task list:**
- [ ] In `showOverflowMenu()`: remove the "OCR page" menu item (id=2). Renumber "Share" to id=2 and update its click handler index from `3` to `2`.
- [ ] Delete methods `ocrCurrentPage()`, `showOcrSelectionDialog()`, `recognizeCurrentPage()` (~55 lines).
- [ ] Keep `android.graphics.pdf.PdfRenderer` import (used by `renderCoverBitmap`).

**Overflow menu after fix:**
```kotlin
popup.menu.add(0, 0, 0, "Highlights")
popup.menu.add(0, 1, 1, "Go to page")
popup.menu.add(0, 2, 2, "Share")
// click handler: 0->highlights, 1->goto, 2->shareCurrentPage()
```

**Verification:** Build passes. Overflow menu shows 3 items: Highlights, Go to page, Share. No OCR option.

**Exit criteria:** `assembleDebug` green. Overflow menu has no OCR entry.

---

## Execution order

Steps 1 and 3 both edit `PdfViewerActivity.kt`; step 2 edits `NoScrollPdfViewerFragment.kt`. Execute 1 → 2 → 3 sequentially, then one build at the end.

## Build command

```
.\gradlew.bat assembleDebug
```

## Rollback

```
git checkout app/src/main/java/com/noscroll/PdfViewerActivity.kt
git checkout app/src/main/java/com/noscroll/NoScrollPdfViewerFragment.kt
```
