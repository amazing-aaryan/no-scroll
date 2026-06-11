# Reader Selection, Highlights, Metadata, Sharing, Zen Status

Implemented on branch `codex/reader-selection-highlights-zen`.

## Done

- Switched the reader host from `PdfRenderer` + `RecyclerView` to AndroidX PDF `PdfViewerFragment`.
- Raised Android requirements for AndroidX PDF alpha18:
  - `minSdk 28`
  - `compileSdk 36`
  - `compileSdkExtension 19`
  - Android Gradle Plugin `8.9.1`
  - Kotlin `2.1.20`
  - Room `2.7.2`
- Added Room v2 migration for selection bounds and richer metadata fields.
- Added PDF text selection capture via AndroidX PDF `PdfView.OnSelectionChangedListener`.
- Added selection actions:
  - `Highlight`
  - `Annotate`
  - `Quote`
  - `Share`
- Persisted highlights in app DB using selected text plus AndroidX PDF `PdfRect` bounds.
- Rendered saved highlights back into AndroidX PDF using `PdfView.setHighlights`.
- Added highlight list dialog with jump, note edit, share, and delete actions.
- Added direct saved-highlight page tap handling. Tapping a rendered highlight opens the same edit/share/delete actions.
- Added export:
  - Native annotated PDF export with platform `HighlightAnnotation` where supported by Android 36 / SDK extension 18+.
  - Text export fallback for all supported devices.
- Added OCR page fallback for scanned PDFs. It renders the current page, runs ML Kit OCR, maps line bounds back to PDF coordinates, and lets the user create a quote or saved highlight from the OCR text.
- Added metadata resolver v2:
  - Room cache
  - lightweight embedded `/Title` and `/Author` parser
  - first five pages selectable text ISBN extraction
  - Open Library ISBN lookup
  - Google Books ISBN/query lookup
  - OCR first page only when no selectable text and online lookup is explicitly allowed
  - filename/manual fallback
- Kept network lookup gated by `MetadataLookupPrefs` or one-shot user approval.
- Expanded share sheet rows:
  - Instagram Stories
  - Instagram Feed
  - Instagram Direct
  - Messages
  - More
- Added persistent zen mode with hidden chrome/system bars and a small exit handle.
- Back exits zen mode before leaving the reader.

## Notes

- The app DB remains the source of truth for reader highlights.
- Native annotated PDF export is device-gated because the required platform annotation APIs are only available on Android 36 / SDK extension 18+.
- On older devices, export still works as a portable highlights text file.
- The manual quote FAB remains as an overflow-style fallback path.

## Verification

- `.\gradlew.bat assembleDebug` passes.
