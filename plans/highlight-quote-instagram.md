# no-scroll — Highlight, Annotate & Instagram Quote Share

**Objective:** Add text selection + highlighting with notes to the PDF reader, extract book metadata (author/title) automatically, generate styled quote cards, and share directly to Instagram Stories or Feed from within the app.

**Stack:** Pure Kotlin Android, minSdk 26, compileSdk 34, Kotlin 1.9.22
**Base:** Existing PdfViewerActivity + PdfPageAdapter (PdfRenderer + RecyclerView/PagerSnapHelper)
**Mode:** Feature branch per step -> PRs into main

---

## Architecture

Current implementation status after audit:

- Implemented: Room foundation, metadata cache/manual edit, filename + explicit Google Books lookup, quote card rendering, preview activity, FileProvider-backed image sharing, Instagram Stories/Feed intents, quote cache cleanup.
- Partially implemented: metadata lookup. It does not extract embedded PDF metadata yet; online lookup renders page 1, OCRs it locally, and sends a short title-like snippet to Google Books only after explicit consent.
- Not implemented: real PDF text selection, saved highlight overlays, annotation toolbar, highlight panel, pdfium text coordinate mapping.
- UX bridge: viewer has a manual "Make quote card" FAB so quote sharing is usable before text selection exists.

Recommended next delivery slice:

1. Add pdfium text layer behind `SelectablePdfPageView`.
2. Persist highlight ranges and draw overlays from `HighlightEntity`.
3. Replace manual quote entry with selected-text quote action.
4. Add highlights bottom sheet after persistence works.

Core modules:

- `com.noscroll.data`: Room entities, DAOs, database.
- `com.noscroll.metadata`: cached metadata, manual edit, online opt-in.
- `com.noscroll.quote`: Canvas card builder, preview, FileProvider/Instagram sharing.
- `PdfViewerActivity`: opens PDFs, shows metadata, launches quote preview.

---

## Dependency Graph

Implemented dependency graph:

```text
PdfViewerActivity
  -> BookMetadataRepository -> AnnotationDatabase.bookMetadataDao
  -> QuoteCardPreviewActivity

QuoteCardPreviewActivity
  -> AnnotationDatabase.bookMetadataDao
  -> QuoteCardBitmapBuilder
  -> ShareBottomSheet -> InstagramShareHelper -> FileProvider

HighlightRepository / AnnotationRepository
  -> AnnotationDatabase (ready, not yet wired to selection UI)
```

Missing planned graph:

```text
SelectablePdfPageView -> PdfTextLayer(pdfium) -> SelectionCallback
SelectionCallback -> SelectionToolbar -> HighlightRepository
HighlightRepository -> overlay rect rendering -> HighlightsPanelBottomSheet
```

Steps 2 and 3 run IN PARALLEL (both depend only on Step 1).
Step 5 blocks on BOTH Step 2 AND Step 4.

---

## Step 1 — Foundation: Dependencies, Room DB, Data Models

**Branch:** feat/highlight-foundation
**Model:** default | **Reversible:** yes

### Context Brief
Current app has no Room, no networking, no text extraction. PdfStorage uses SharedPreferences.
Need to add all infrastructure before feature work begins.

### Tasks

1a. Add JitPack to settings.gradle repositories block:


1b. Add kotlin-kapt plugin to app/build.gradle:


1c. Add to app/build.gradle dependencies:


1d. Create com/noscroll/data/HighlightEntity.kt:


1e. Create com/noscroll/data/AnnotationEntity.kt:


1f. Create com/noscroll/data/BookMetadataEntity.kt:


1g. Create DAOs (com/noscroll/data/):
- HighlightDao:     getForBook(uri), getForPage(uri, page), insert(), deleteById()
- AnnotationDao:    getForHighlight(id), upsert(), deleteForHighlight()
- BookMetadataDao:  get(uri), upsert()

1h. Create com/noscroll/data/AnnotationDatabase.kt — singleton Room database with all 3 entities.

### Verification


### Exit Criteria
- assembleDebug exits 0
- Room kapt generates AnnotationDatabase_Impl
- No existing behaviour changes

---

## Step 2 -- Book Metadata Pipeline

**Branch:** feat/book-metadata | **Depends on:** Step 1 | **Parallel with:** Step 3
**Model:** strongest

### Context Brief
Given a PDF URI, produce BookMetadataEntity(title, author). Sources tried in order:
1. PdfiumCore.getDocumentMeta() -- embedded PDF info dict, on-device, always tried first
2. Filename fallback -- always available offline
3. OPTIONAL (user opt-in only): ML Kit OCR on page 0 + Google Books API search

**Privacy constraint:** The OCR + Google Books path sends up to 150 chars of cover-page text
off-device to googleapis.com. Users may open private/internal PDFs, not only public books.
This network lookup MUST be gated behind explicit user consent -- never triggered automatically.
Default is offline-only (sources 1 and 2 above).

Google Books endpoint (used only when opt-in is ON):
https://www.googleapis.com/books/v1/volumes?q={query}&maxResults=3&printType=books
Free, no API key required.

### Tasks

2a. Create com/noscroll/metadata/PdfEmbeddedMetadata.kt:
Open PDF with PdfiumCore, call core.getDocumentMeta(doc) which returns a Meta object with .title and .author.
Return EmbeddedMeta(title, author) -- null fields if blank/missing. Close doc + pfd in finally.

2b. Create com/noscroll/metadata/CoverPageOcr.kt:
suspend fun extractText(bitmap: Bitmap): String
Wrap ML Kit TextRecognition.getClient().process(InputImage.fromBitmap(bitmap, 0)).await().text
Return "" on any exception (model not downloaded, etc).
This function is only called when opt-in is confirmed -- do not call from resolve() directly.

2c. Create com/noscroll/metadata/GoogleBooksClient.kt:
fun search(query: String): GoogleBooksResult?
OkHttp GET to Books API URL with URLEncoder.encode(query.take(100)).
Parse JSON: items[0].volumeInfo.title and .authors[0].
Return null on any exception or empty results.
This function is only called when opt-in is confirmed.

2d. Create com/noscroll/metadata/MetadataLookupPrefs.kt:
```kotlin
object MetadataLookupPrefs {
    private const val PREFS = "metadata_prefs"
    private const val KEY_ONLINE_LOOKUP = "online_lookup_enabled"

    fun isOnlineLookupEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONLINE_LOOKUP, false)   // DEFAULT OFF

    fun setOnlineLookupEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONLINE_LOOKUP, enabled).apply()
}
```

2e. Create com/noscroll/metadata/BookMetadataRepository.kt:
```
suspend fun resolve(context, uri): BookMetadataEntity
  1. Check Room cache -- return immediately if found
  2. PdfEmbeddedMetadata.extract() -- if title+author non-null, save+return with source="embedded"
  3. If MetadataLookupPrefs.isOnlineLookupEnabled(context):
       a. renderCoverPage() with existing PdfRenderer pattern
       b. CoverPageOcr.extractText(cover) on Main dispatcher
       c. Build query from embedded partial + OCR text (first 150 chars)
       d. GoogleBooksClient.search(query) -- if result, save+return with source="ocr_google_books"
  4. Fallback: uri.lastPathSegment.removeSuffix(".pdf") as title, "Unknown Author", source="manual"
     Save with source="manual" -- metadata bar will show prompt to enable lookup or edit manually.

suspend fun saveManual(context, uri, title, author)
  Upsert BookMetadataEntity with source="manual"
```

2f. Add INTERNET permission to AndroidManifest.xml:
<uses-permission android:name="android.permission.INTERNET" />
Note: this permission is declared but network calls only occur when opt-in is ON.

2g. Create EditMetadataDialog.kt:
AlertDialog with two EditText fields (title, author).
On confirm -> BookMetadataRepository.saveManual().

2h. Opt-in prompt in PdfViewerActivity metadata bar (wired in Step 7a):
When source == "manual" and onlineLookupEnabled == false, metadata bar shows a chip:
  "Look up title online?"  [Enable] [Dismiss]
Tapping [Enable]:
  1. Show a one-time disclosure dialog:
     "To identify this book, the app will send a portion of its cover page text to Google Books.
      This is optional and only happens for books with unknown metadata."
     Buttons: [Allow] [No thanks]
  2. If [Allow]: MetadataLookupPrefs.setOnlineLookupEnabled(true) -> re-run resolve() -> update bar
  3. If [No thanks]: dismiss chip, do not set flag
This lookup can also be toggled globally in app Settings (Step 7g).

### Verification
- PDF with embedded metadata: source == "embedded", no network call (verify in logcat)
- PDF without metadata, opt-in OFF: source == "manual", filename shown, no network call
- PDF without metadata, opt-in ON after explicit user tap: source == "ocr_google_books" if found
- Second call returns from Room cache (no network)

### Exit Criteria
- assembleDebug exits 0
- Embedded metadata PDF: no network
- Opt-in is OFF by default (SharedPreferences key absent = false)
- Network lookup only fires after explicit Allow tap
- Second call returns from Room cache

---

## Step 3 -- Text Selection Infrastructure

**Branch:** feat/text-selection | **Depends on:** Step 1 | **Parallel with:** Step 2
**Model:** strongest (complex coordinate math + gestures)

### Context Brief
Android PdfRenderer provides no text position API. pdfium-android (PdfiumCore) does.
Open PDF twice: PdfRenderer for bitmap rendering (existing code), PdfiumCore for text extraction.
Both use separate ParcelFileDescriptors in read mode "r" on same content:// URI. Safe.

Key coordinate math (PDF origin is bottom-left):

The ImageView uses scaleType="fitCenter" (or equivalent). The rendered bitmap may not fill
the full view -- there can be letterbox padding top/bottom or left/right. Mapping touch
coordinates via (x / view.width) is WRONG for letterboxed pages. Instead use the ImageView's
imageMatrix which encodes the exact scale + translation applied to the bitmap.

  Step A -- touch view coords -> bitmap pixel coords:
    val inverseMatrix = Matrix()
    imageView.imageMatrix.invert(inverseMatrix)
    val pts = floatArrayOf(touchEvent.x, touchEvent.y)
    inverseMatrix.mapPoints(pts)
    val bitmapX = pts[0].coerceIn(0f, bitmap.width.toFloat())
    val bitmapY = pts[1].coerceIn(0f, bitmap.height.toFloat())

  Step B -- bitmap pixel coords -> PDF unit coords (flip Y):
    normX = bitmapX / bitmap.width
    normY = bitmapY / bitmap.height
    pdfX  = normX * pdfPageWidth
    pdfY  = (1 - normY) * pdfPageHeight
    charIdx = PdfiumCore.textGetCharIndexAtPos(textPage, pdfX, pdfY, tolX=5, tolY=5)

  Step C -- PDF rect -> bitmap pixel coords -> view coords (for drawing highlights):
    bitmapRect = RectF(
      r.left / pdfWidth * bitmap.width,
      (1 - r.top / pdfHeight) * bitmap.height,
      r.right / pdfWidth * bitmap.width,
      (1 - r.bottom / pdfHeight) * bitmap.height
    )
    // Apply forward imageMatrix to map bitmap pixels -> view pixels for drawing in HighlightOverlayView
    val viewRect = RectF(bitmapRect)
    imageView.imageMatrix.mapRect(viewRect)

PdfTextLayer receives and returns bitmap-pixel coordinates. The view <-> bitmap mapping
lives entirely in SelectablePdfPageView using the live imageMatrix.

### Tasks

3a. Create com/noscroll/selection/PdfTextLayer.kt (Closeable):
Fields: PdfiumCore, ParcelFileDescriptor, doc handle, textPage handle, pageIndex.
init: core.openPage(doc, pageIndex); textPage = core.newTextPage(doc, pageIndex)
val charCount: Int = core.textCountChars(textPage)

fun charIndexAt(bitmapX: Float, bitmapY: Float, bitmapW: Int, bitmapH: Int): Int
  -- Receives bitmap pixel coords (already transformed from view via imageMatrix inverse)
  -- normX = bitmapX / bitmapW;  normY = bitmapY / bitmapH
  -- pdfX  = normX * pdfPageWidth;  pdfY = (1 - normY) * pdfPageHeight
  -- return core.textGetCharIndexAtPos(textPage, pdfX, pdfY, 5.0, 5.0)

fun selectionRects(start: Int, end: Int, bitmapW: Int, bitmapH: Int): List<RectF>
  -- core.textCountRects(textPage, start, end-start) + textGetRect(textPage, i)
  -- Each rect returned in BITMAP pixel coords (caller applies imageMatrix for view drawing):
       left   = r.left / pdfWidth * bitmapW
       top    = (1 - r.top / pdfHeight) * bitmapH
       right  = r.right / pdfWidth * bitmapW
       bottom = (1 - r.bottom / pdfHeight) * bitmapH

fun textForRange(start, end): String -- core.textGetText(textPage, start, end-start)
override fun close(): closeTextPage -> closePage -> closeDocument -> pfd.close() in finally

3b. Create SelectionState.kt:
data class SelectionState(val startChar: Int, val endChar: Int, val text: String, val rects: List<RectF>)

3c. Create SelectionCallback.kt (interface):
fun onTextSelected(pageIndex: Int, state: SelectionState)
fun onSelectionCleared()

3d. Create HighlightDisplayData.kt:
data class HighlightDisplayData(val id: Long, val colorArgb: Int, val rects: List<RectF>)

3e. Create HighlightOverlayView.kt (custom transparent View):
onDraw(canvas):
  - Selection rects: Paint(color=0x66FFEB3B, style=FILL)
  - Saved highlight rects: Paint(color=highlight.colorArgb, style=FILL)
Public: setSelectionRects(List<RectF>), setHighlights(List<HighlightDisplayData>), invalidate()

3f. Create SelectablePdfPageView.kt (FrameLayout):
Children: AppCompatImageView + HighlightOverlayView (both MATCH_PARENT).

**Lifecycle -- explicit bind/reset (Fix for RecyclerView reuse):**
```
fun resetForPage(pageIndex: Int, uri: Uri) {
    // Close previous PdfTextLayer if page or URI changed
    if (currentPageIndex != pageIndex || currentUri != uri) {
        textLayer?.close()
        textLayer = null
        clearSelectionState()
    }
    currentPageIndex = pageIndex
    currentUri = uri
}
```
This MUST be called from PdfPageAdapter.onBindViewHolder() before any other bind work.
It is also called from onViewRecycled() with sentinel values to force close on recycle.
onDetachedFromWindow() still calls textLayer?.close() as a safety net.

**Touch -> bitmap coord conversion (Fix for letterboxing):**
In GestureDetectorCompat callbacks, convert event coords to bitmap coords using imageMatrix:
```
private fun viewToBitmapCoords(x: Float, y: Float): FloatArray {
    val inv = Matrix()
    imageView.imageMatrix.invert(inv)
    val pts = floatArrayOf(x, y)
    inv.mapPoints(pts)
    return pts  // [bitmapX, bitmapY] clamped to [0, bitmap.width/height]
}
```

**Highlight rect drawing -- apply forward imageMatrix:**
When calling overlay.setHighlights(), transform all bitmapRect coords to view coords:
```
private fun bitmapToViewRect(bitmapRect: RectF): RectF {
    val viewRect = RectF(bitmapRect)
    imageView.imageMatrix.mapRect(viewRect)
    return viewRect
}
```
HighlightOverlayView draws in view pixel space, so all rects passed to it must be view-space.

GestureDetectorCompat on overlayView:
  onLongPress: load PdfTextLayer lazily (via resetForPage params); call viewToBitmapCoords();
    charIdx = textLayer.charIndexAt(bitmapX, bitmapY, bitmap.width, bitmap.height);
    set selStart=selEnd=charIdx; start selection
  onScroll: extend selEnd; recompute bitmap rects via textLayer.selectionRects(); convert to
    view rects via bitmapToViewRect(); update overlay; invalidate
  onSingleTapUp (selection active): clear; callback.onSelectionCleared()
  onUp after drag: if selEnd > selStart -> callback.onTextSelected(pageIndex, state)
If charCount==0 on long-press: Snackbar("No text layer -- highlights unavailable")
Public: setBitmap(Bitmap), setHighlights(List<HighlightDisplayData>), clearSelection()

3g. Update item_pdf_page.xml:
Replace <ImageView> with <com.noscroll.selection.SelectablePdfPageView>
Set scaleType="fitCenter" explicitly so imageMatrix is always populated.

3h. Update PdfPageAdapter:
Add params: selectionCallback: SelectionCallback?, pdfUri: Uri

onBindViewHolder(holder, position):
  holder.pageView.resetForPage(position, pdfUri)   // MUST be first -- closes stale PdfTextLayer
  holder.pageView.setBitmap(...)
  holder.pageView.setHighlights(highlightsForPage)
  holder.pageView.setCallback(selectionCallback)

onViewRecycled(holder):
  holder.pageView.resetForPage(-1, Uri.EMPTY)       // forces close + clear on recycle

### Verification
- assembleDebug exits 0
- Long-press on text page -> yellow overlay appears
- SelectionCallback.onTextSelected fires with non-empty text
- Scanned PDF: Snackbar shown, no crash
- Existing scroll / page-flip unchanged

### Exit Criteria
- Long-press shows selection
- Correct text returned
- No crash on scanned PDFs
- Scroll unchanged

---

## Step 4 -- Highlight & Annotation Storage

**Branch:** feat/highlight-storage | **Depends on:** Step 3 | **Model:** default

### Context Brief
Wire SelectionCallback into a popup toolbar and Room persistence.
Saved highlights re-render on each page bind: load HighlightEntity list from Room,
compute pixel rects via PdfTextLayer, pass as HighlightDisplayData to the overlay.

### Tasks

4a. Create SelectionToolbar.kt (PopupWindow):
Anchored above topmost selection rect. 4 buttons with lambdas:
  Highlight (yellow icon), Annotate (pencil), Quote (share icon), Cancel

4b. Create HighlightRepository.kt:
suspend fun save(ctx, entity): Long
suspend fun getForPage(ctx, uri, page): List<HighlightEntity>
suspend fun getForBook(ctx, uri): List<HighlightEntity>
suspend fun delete(ctx, id)
All delegate to AnnotationDatabase.getInstance(ctx).highlightDao()

4c. Create AnnotationRepository.kt:
suspend fun upsert(ctx, noteText, highlightId)
suspend fun get(ctx, highlightId): AnnotationEntity?
suspend fun delete(ctx, highlightId)

4d. Create AnnotationInputDialog.kt:
AlertDialog with multiline EditText. Title shows first 40 chars of quote.
On confirm -> AnnotationRepository.upsert()

4e. Update PdfViewerActivity (implement SelectionCallback):
onTextSelected() -> show SelectionToolbar:
  Highlight -> HighlightRepository.save(HighlightEntity(...)) -> reload page highlights -> clearSelection()
  Annotate  -> save highlight first -> show AnnotationInputDialog
  Quote     -> store (pdfUri, pageIndex, selectedText) -> startActivity(QuoteCardPreviewActivity with extras)
  Cancel    -> clearSelection()

On page scroll-idle: launch coroutine on IO:
  entities = HighlightRepository.getForPage(uri, currentPage)
  displayData = entities.map { h ->
    val rects = PdfTextLayer(...).use { it.selectionRects(h.startCharIndex, h.endCharIndex, bitmapW, bitmapH) }
    HighlightDisplayData(h.id, h.colorArgb, rects)
  }
  Main: adapter.setHighlightsForPage(currentPage, displayData)

4f. Long-press on saved highlight rect:
In SelectablePdfPageView: if hit-test lands on an existing HighlightDisplayData rect,
show context menu (PopupMenu or AlertDialog): [Edit Note] [Delete] [Share as Quote]

### Verification
1. Select text -> Highlight -> rect persists after scroll away + return
2. Annotate -> dialog -> note saved
3. Delete -> rect removed immediately
4. App kill + restart -> highlights still present (Room)

### Exit Criteria
- Highlights survive restart
- No crash on scanned PDF long-press
- FK cascade: delete highlight removes its annotation

---

## Step 5 -- Quote Card Builder

**Branch:** feat/quote-card | **Depends on:** Steps 2 AND 4 BOTH complete | **Model:** strongest

### Context Brief
Launch QuoteCardPreviewActivity with intent extras:
  EXTRA_QUOTE_TEXT (String), EXTRA_BOOK_URI (String), EXTRA_PAGE_NUMBER (Int)

Activity loads BookMetadataEntity from Room, generates styled 1080x1350 px Bitmap (4:5 ratio
= optimal for Instagram feed posts), shows preview, lets user switch theme, then triggers
ShareBottomSheet for sharing.

### Tasks

5a. Create QuoteCardTheme.kt (enum):
  DARK  -- bgStart=#1A1A2E, bgEnd=#16213E, text=WHITE, attr=#AAAAAA
  LIGHT -- bgStart=#FAFAFA, bgEnd=#F0F0F0, text=#1A1A1A, attr=#666666
  SEPIA -- bgStart=#F5E6C8, bgEnd=#EDD9A3, text=#3E2723, attr=#8D6E63
  BLAZE -- bgStart=#FF6B35, bgEnd=#C0392B, text=WHITE,   attr=#FFD7CC

5b. Create QuoteCardSpec.kt:
data class QuoteCardSpec(val quoteText: String, val bookTitle: String, val author: String,
                         val pageNumber: Int, val theme: QuoteCardTheme = QuoteCardTheme.DARK)

5c. Create QuoteCardBitmapBuilder.kt (Canvas API):
Canvas size: 1080x1350 px (WIDTH x HEIGHT constants)
drawBackground: LinearGradient(0, 0, 0, HEIGHT, bgStart, bgEnd, CLAMP) -- vertical gradient
drawQuoteMarks: large unicode left-double-quote char at top-left, 240sp, 40% opacity (decorative)
drawQuoteText: Typeface.SERIF italic, 62sp, starts at y=360, wrapText() for line breaks
drawAttribution: "-- {author}, {bookTitle}, p.{pageNumber}", 38sp serif, 60px gap below quote
drawWatermark: "noscroll", 28sp monospace, bottom-left, ~60% white opacity

wrapText(text, paint, maxWidth): split on spaces, build lines that fit within maxWidth.
Cap quoteText at 300 chars with ellipsis before rendering.

5d. Create activity_quote_card_preview.xml:
  ImageView (fills ~75% screen height, centered)
  HorizontalScrollView at bottom: 4 chip buttons (DARK / LIGHT / SEPIA / BLAZE)
  Row of two buttons: "Edit" (outlined) + "Share" (filled primary)

5e. Create QuoteCardPreviewActivity.kt:
onCreate:
  1. Read intent extras
  2. Load BookMetadataEntity from Room on IO dispatcher
  3. Build QuoteCardSpec(quoteText, title, author, page, DARK)
  4. Generate bitmap on IO -> set in ImageView on Main
Theme chip tap -> rebuild bitmap on IO with new theme -> update preview
"Edit" -> AlertDialog EditTexts (quote, author, title, page) -> rebuild on confirm
"Share" -> ShareBottomSheet.newInstance(currentBitmap).show(supportFragmentManager, "share")

Register in AndroidManifest: android:screenOrientation="portrait", android:exported="false"

### Verification
- Launch with test intent -> 1080x1350 preview renders
- All 4 themes display without crash
- 80-word quote wraps without overflow
- Attribution shows correct Room metadata

### Exit Criteria
- All 4 themes render correctly
- Text wraps at all lengths
- Metadata from Room populates attribution

---

## Step 6 -- Instagram Share Flow

**Branch:** feat/instagram-share | **Depends on:** Step 5 | **Model:** default

### Context Brief
Two Instagram intents:
1. Stories: Intent("com.instagram.android.share.ADD_TO_STORY").setDataAndType(uri, "image/jpeg")
2. Feed:    Intent(ACTION_SEND, type=image/jpeg).setPackage("com.instagram.android")

Both require FileProvider (content:// URI). Bitmap saved to cacheDir/quote_cards/*.jpg.
ShareBottomSheet shown from inside QuoteCardPreviewActivity -- user picks destination,
then Instagram opens for the final post step.

### Tasks

6a. Add FileProvider to AndroidManifest.xml:
`xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.noscroll.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths"/>
</provider>
`

6b. Create app/src/main/res/xml/file_provider_paths.xml:
`xml
<paths>
    <cache-path name="shared_images" path="quote_cards/"/>
</paths>
`

6c. Create InstagramShareHelper.kt:
private fun saveBitmapToCache(activity, bitmap): Uri
  Write JPEG to cacheDir/quote_cards/quote_{timestamp}.jpg
  Return FileProvider.getUriForFile(activity, "com.noscroll.fileprovider", file)

fun shareToStories(activity, bitmap):
  Build Intent("com.instagram.android.share.ADD_TO_STORY").setDataAndType(uri, "image/jpeg")
  Add FLAG_GRANT_READ_URI_PERMISSION
  resolveActivity() check -> startActivity or fallback to shareGeneric

fun shareToFeed(activity, bitmap):
  Intent(ACTION_SEND, type=image/jpeg).setPackage("com.instagram.android")
  putExtra(EXTRA_STREAM, uri) + FLAG_GRANT_READ_URI_PERMISSION
  try startActivity catch ActivityNotFoundException -> shareGeneric

fun shareGeneric(activity, bitmap):
  Intent.createChooser(ACTION_SEND + image/jpeg + EXTRA_STREAM, "Share quote")

6d. Create ShareBottomSheet.kt (BottomSheetDialogFragment):
Companion factory: newInstance(bitmap) stores bitmap in BitmapHolder singleton.
3 rows: "Instagram Stories" / "Instagram Feed" / "Share via..."
onDismiss: BitmapHolder.bitmap = null

6e. Wire: QuoteCardPreviewActivity Share button -> ShareBottomSheet.newInstance(currentBitmap).show()

### Verification
- Instagram installed: Stories row opens Stories composer with card
- Instagram absent: system chooser appears
- No FileUriExposedException in logcat

### Exit Criteria
- Stories share works (Instagram 300+)
- Feed share works (Instagram 300+)
- No FileUriExposedException
- Fallback fires when Instagram absent

---

## Step 7 -- Integration & UI Polish

**Branch:** feat/highlights-integration | **Depends on:** Steps 4 AND 6 | **Model:** default

### Tasks

7a. Book metadata bar in PdfViewerActivity:
Small TextView at top: "Book Title -- Author" (shows "..." while loading).
On create: coroutine -> BookMetadataRepository.resolve() -> update text.
Long-press on bar -> EditMetadataDialog to correct.

When resolved entity has source == "manual" AND MetadataLookupPrefs.isOnlineLookupEnabled() == false:
Show a dismissible chip below the bar: "Tap to look up book info online"
Tapping chip -> one-time disclosure AlertDialog:
  "To identify this book, a portion of its cover page text will be sent to Google Books.
   This happens only for books with unknown metadata."
  [Allow once] [Always allow] [No thanks]
  - Allow once: run resolve() with a one-shot flag bypassing the prefs check; do not save pref
  - Always allow: MetadataLookupPrefs.setOnlineLookupEnabled(true); run resolve(); hide chip permanently
  - No thanks: dismiss chip for this session; do not set pref

Also add a Settings toggle ("Look up book info online") in a new SettingsActivity or via
PreferenceFragmentCompat. This gives users a persistent way to toggle the pref without needing
the chip prompt.

7g. Create SettingsActivity.kt (or PreferenceFragment):
Single toggle: "Look up book info online" (default: off).
Description: "Sends cover page text to Google Books to find the book title and author."
Linked from PdfViewerActivity overflow menu.

7b. Highlights FAB in PdfViewerActivity:
"Bookmarks" FAB. Tap -> HighlightsPanelBottomSheet.show().

7c. Create HighlightsPanelBottomSheet.kt (BottomSheetDialogFragment):
RecyclerView of all highlights for current book.
Row: page chip + first 60 chars of quoteText + share icon.
Tap row -> dismiss, scroll to page, flash highlight (alpha pulse 100%->40% over 500ms).
Share icon -> startActivity(QuoteCardPreviewActivity) with that highlight's data.

7d. Scanned PDF handling:
charCount==0 on long-press -> Snackbar("No text layer -- highlights unavailable"). No crash.

7e. Cache cleanup:
MainActivity.onCreate(): delete files in cacheDir/quote_cards/ older than 24 hours.

7f. Memory hygiene:
PdfTextLayer.close() in SelectablePdfPageView.onDetachedFromWindow()
BitmapHolder.bitmap = null in ShareBottomSheet.onDismiss()

7g. Proguard (app/proguard-rules.pro):
-keep class com.shockwave.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn com.shockwave.**

### Verification (full end-to-end)
1. Open app -> open PDF
2. Metadata bar shows title and author
3. Long-press on text -> selection appears
4. Tap Highlight -> saved, persists after restart
5. Tap Quote -> QuoteCardPreviewActivity opens with attribution pre-filled
6. Switch to SEPIA theme -> preview updates
7. Tap Share -> ShareBottomSheet appears
8. Instagram Stories -> opens Instagram with card as story background
9. Back in app -> Highlights FAB -> panel shows saved highlight
10. Tap row -> jumps to page and flashes highlight

### Exit Criteria
- Full flow works without crash
- assembleDebug exits 0
- Highlights survive app kill + restart
- Metadata resolves correctly for a real PDF

---

## Invariants (all steps)

- .\\gradlew.bat assembleDebug exits 0
- No FileUriExposedException
- No crash on scanned PDFs (charCount == 0)
- PdfTextLayer always closed in finally block
- PdfTextLayer closed on RecyclerView recycle (onViewRecycled) AND on page/uri change (resetForPage)
- Room queries always on IO dispatcher
- BitmapHolder cleared after use
- Network metadata lookup (Google Books API) only fires after explicit user opt-in -- never automatically
- MetadataLookupPrefs.isOnlineLookupEnabled() defaults to false; opt-in is per app setting, not per document

---

## Known Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| PdfiumCore + PdfRenderer both open same URI | Separate ParcelFileDescriptors in read mode "r" -- safe for content:// URIs |
| Google Books returns wrong book | Manual edit always available; source field visible to user |
| Instagram removes Stories intent | resolveActivity() check; fallback: Stories -> Feed -> Generic |
| pdfium-android JitPack unavailable | Pin commit: com.github.barteksc:pdfium-android:342bc92 |
| PdfTextLayer slow on large PDFs | Load lazily on first long-press; cache in LruCache<Int, PdfTextLayer> |
| Quote text overflows card | wrapText() + 300-char cap with ellipsis |
| ML Kit model not downloaded | Exception caught; falls through to Google Books with filename |
| **[RESOLVED] Privacy: OCR text sent to Google without consent** | Google Books lookup behind explicit opt-in gate (MetadataLookupPrefs, default OFF). Disclosure dialog shown before first network call. Embedded metadata + filename used offline by default. |
| **[RESOLVED] Coordinate math ignores fitCenter letterboxing** | Touch -> bitmap via imageMatrix.invert(). Highlight rects -> view via imageMatrix.mapRect(). PdfTextLayer operates only in bitmap coords; view-space conversion owned by SelectablePdfPageView. |
| **[RESOLVED] PdfTextLayer leaked on RecyclerView rebind** | resetForPage() called from onBindViewHolder (closes stale layer if page/uri changed) and onViewRecycled (forces close). onDetachedFromWindow() as safety net only. |

---

## Parallel Execution Map

`
[Step 1: Foundation -- Room + all deps + entities + DAOs]
        |
        |---> [Step 2: Book Metadata] ----------------------->|
        |       embedded -> OCR -> Google Books -> fallback    |
        |                                                      |
        |---> [Step 3: Text Selection]                        |
                  PdfiumCore text layer + gesture handler      |
                        |                                      |
               [Step 4: Highlight & Annotation Storage]        |
                        |                                      v
                        |-------------> [Step 5: Quote Card Builder] <-|
                                                  |
                                        [Step 6: Instagram Share]
                                                  |
                                        [Step 7: Integration & Polish]
`

Effort estimates:
  Step 1: 2-3h  (config + boilerplate)
  Step 2: 3-4h  (3 data sources + orchestration)
  Step 3: 5-6h  (coordinate math + gestures -- hardest step)
  Step 4: 3-4h  (toolbar + Room wiring)
  Step 5: 3-4h  (Canvas rendering + preview activity)
  Step 6: 2-3h  (FileProvider + intents)
  Step 7: 2-3h  (polish + panel)
  Total:  20-27h
