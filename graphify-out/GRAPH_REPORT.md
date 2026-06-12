# Graph Report - no-scroll  (2026-06-12)

## Corpus Check
- 73 files · ~23,541 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 823 nodes · 1741 edges · 53 communities (48 shown, 5 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 63 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `8d1c9750`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]

## God Nodes (most connected - your core abstractions)
1. `PdfViewerActivity` - 57 edges
2. `NoScrollPdfViewerFragment` - 24 edges
3. `NoScrollAccessibilityService` - 22 edges
4. `Text` - 21 edges
5. `OverlayService` - 17 edges
6. `TutorialPrefs` - 17 edges
7. `readme_no_scroll` - 17 edges
8. `LibraryScreen()` - 16 edges
9. `NoScroll — Product Description` - 15 edges
10. `AccessibilityEvent` - 14 edges

## Surprising Connections (you probably didn't know these)
- `PermissionRow()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/SetupActivity.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `ShareRow()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/quote/ShareBottomSheet.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `TutorialOverlay()` --calls--> `Canvas`  [INFERRED]
  app/src/main/java/com/noscroll/tutorial/TutorialOverlay.kt → app/src/main/java/com/noscroll/quote/QuoteCardBitmapBuilder.kt
- `PdfViewerActivity` --references--> `NoScrollPdfViewerFragment`  [EXTRACTED]
  app/src/main/java/com/noscroll/PdfViewerActivity.kt → app/src/main/java/com/noscroll/NoScrollPdfViewerFragment.kt
- `TooltipCard()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/tutorial/TutorialOverlay.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt

## Import Cycles
- None detected.

## Communities (53 total, 5 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (29): Boolean, Bitmap, BookMetadataEntity, Boolean, Bundle, Float, HighlightEntity, Int (+21 more)

### Community 1 - "Community 1"
Cohesion: 0.08
Nodes (51): TutorialController, TutorialStepId, TutorialController, TutorialStep, BookEntity, BookMetadataEntity, Boolean, HighlightEntity (+43 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (21): Bundle, Intent, Bundle, Bundle, LegalBookSearchResult, List, String, Uri (+13 more)

### Community 3 - "Community 3"
Cohesion: 0.21
Nodes (8): Flow, Int, List, Long, String, BookmarkEntity, BookmarkDao, BookmarkEntity

### Community 4 - "Community 4"
Cohesion: 0.10
Nodes (22): Boolean, Float, Int, List, PdfDocument, PdfRect, ReaderSelection, SelectionAction (+14 more)

### Community 5 - "Community 5"
Cohesion: 0.14
Nodes (19): Int, Job, List, String, ViewGroup, Bitmap, Int, Job (+11 more)

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (12): BookEntity, BookMetadataEntity, Boolean, Bundle, String, TutorialPrefs, Uri, Boolean (+4 more)

### Community 7 - "Community 7"
Cohesion: 0.22
Nodes (10): AccessibilityEvent, AccessibilityNodeInfo, Int, Long, Rect, BlockSurface, NavSelectionState, NoScrollAccessibilityService (+2 more)

### Community 8 - "Community 8"
Cohesion: 0.21
Nodes (11): Boolean, Int, Intent, View, IBinder, OverlayMode, OverlayService, Notification (+3 more)

### Community 9 - "Community 9"
Cohesion: 0.25
Nodes (10): Bitmap, BookMetadataEntity, Boolean, Context, List, PdfDocument, String, Uri (+2 more)

### Community 10 - "Community 10"
Cohesion: 0.31
Nodes (9): Context, Int, List, String, Uri, JSONArray, PdfEntry, PdfStorage (+1 more)

### Community 11 - "Community 11"
Cohesion: 0.24
Nodes (11): Bitmap, Float, List, String, Canvas, QuoteCardBitmapBuilder, QuoteCardSpec, QuoteCardTheme (+3 more)

### Community 12 - "Community 12"
Cohesion: 0.26
Nodes (10): BookEntity, Boolean, Context, Flow, Int, List, Long, String (+2 more)

### Community 13 - "Community 13"
Cohesion: 0.11
Nodes (18): readme_accessibility_service, readme_android_manifest_xml, readme_android_phone, readme_android_studio, readme_com_instagram_android, readme_com_instagram_lite, readme_floating_action_button, readme_gradle (+10 more)

### Community 14 - "Community 14"
Cohesion: 0.15
Nodes (9): List, Rect, TutorialStep, TutorialStepId, Unit, TutorialController, TooltipSide, TutorialStep (+1 more)

### Community 15 - "Community 15"
Cohesion: 0.18
Nodes (8): BookEntity, Boolean, Flow, Int, List, Long, String, BookDao

### Community 16 - "Community 16"
Cohesion: 0.26
Nodes (7): Flow, HighlightEntity, Int, List, Long, String, HighlightDao

### Community 17 - "Community 17"
Cohesion: 0.40
Nodes (7): Any, Boolean, List, String, LegalBookApiClient, LegalBookDownloadLink, LegalBookSearchResult

### Community 18 - "Community 18"
Cohesion: 0.27
Nodes (9): Boolean, Context, File, HighlightEntity, List, String, Uri, HighlightEntity (+1 more)

### Community 20 - "Community 20"
Cohesion: 0.36
Nodes (7): Context, HighlightEntity, Int, List, Long, String, HighlightRepository

### Community 21 - "Community 21"
Cohesion: 0.26
Nodes (6): AnnotationEntity, Flow, List, Long, AnnotationDao, AnnotationEntity

### Community 22 - "Community 22"
Cohesion: 0.55
Nodes (5): Activity, Bitmap, String, Uri, InstagramShareHelper

### Community 23 - "Community 23"
Cohesion: 0.27
Nodes (5): BookMetadataEntity, Flow, List, String, BookMetadataDao

### Community 24 - "Community 24"
Cohesion: 0.31
Nodes (8): android, Bundle, String, BottomSheetDialogFragment, Dialog, newInstance(), ShareBottomSheet, ShareRow()

### Community 25 - "Community 25"
Cohesion: 0.45
Nodes (5): Int, String, OpenLibraryClient, OpenLibraryResult, org

### Community 26 - "Community 26"
Cohesion: 0.18
Nodes (10): AnnotationDao, Context, BookDao, BookmarkDao, BookMetadataDao, AnnotationDatabase, getInstance(), HighlightDao (+2 more)

### Community 27 - "Community 27"
Cohesion: 0.44
Nodes (5): AnnotationEntity, Context, Long, String, AnnotationRepository

### Community 28 - "Community 28"
Cohesion: 0.42
Nodes (5): Bitmap, List, String, CoverBlock, CoverPageOcr

### Community 29 - "Community 29"
Cohesion: 0.39
Nodes (5): Context, String, Uri, EmbeddedPdfMetadata, PdfEmbeddedMetadata

### Community 30 - "Community 30"
Cohesion: 0.12
Nodes (15): Architecture, Book Metadata, Collections, Core Mechanism: Instagram Interception, Highlights System, NoScroll — Product Description, Notebook, PDF Library (+7 more)

### Community 31 - "Community 31"
Cohesion: 0.57
Nodes (4): Context, File, String, PdfThumbnailCache

### Community 32 - "Community 32"
Cohesion: 0.52
Nodes (4): Int, String, GoogleBooksClient, GoogleBooksResult

### Community 33 - "Community 33"
Cohesion: 0.52
Nodes (4): List, PdfRect, String, PdfSelectionCodec

### Community 34 - "Community 34"
Cohesion: 0.60
Nodes (3): Boolean, Context, MetadataLookupPrefs

### Community 35 - "Community 35"
Cohesion: 0.40
Nodes (4): BookMetadataEntity, Context, Uri, EditMetadataDialog

### Community 36 - "Community 36"
Cohesion: 0.60
Nodes (3): Boolean, Context, PermissionUtils

### Community 37 - "Community 37"
Cohesion: 0.47
Nodes (4): Context, Flow, NotebookRepository, NotebookState

### Community 38 - "Community 38"
Cohesion: 0.83
Nodes (3): Bitmap, String, BitmapHolder

### Community 43 - "Community 43"
Cohesion: 0.30
Nodes (8): AccessibilityService, Boolean, Bundle, Intent, String, TutorialPrefs, PermissionRow(), SetupActivity

### Community 44 - "Community 44"
Cohesion: 0.18
Nodes (7): Flow, List, Long, BookCollectionDao, BookCollectionEntity, BookCollectionDao, BookCollectionEntity

### Community 45 - "Community 45"
Cohesion: 0.22
Nodes (6): Flow, List, Long, QuoteCardDao, QuoteCardEntity, QuoteCardEntity

### Community 46 - "Community 46"
Cohesion: 0.22
Nodes (8): Build, Caveats, First-run setup, How it works, NoScroll, Prerequisites, Troubleshooting, Using the app

### Community 47 - "Community 47"
Cohesion: 0.22
Nodes (8): [2026-05-15 08:34] Session: diagnose and fix all app crashes + navigation loop, Fix 1 — `UninitializedPropertyAccessException: _toolboxView`, Fix 2 — `InflateException: Error inflating ToolBoxView`, Fix 3 — `IllegalStateException: Can't scrollToPage without PdfDocument`, Fix 4 — `BadTokenException: permission denied for window type 2038`, Fix 5 — OverlayService restart-loop / Samsung deep-sleep, Fix 6 — Accessibility service spamming OverlayService without permission, reasoning.md — no-scroll

## Knowledge Gaps
- **150 isolated node(s):** `PreToolUse`, `Bundle`, `BlockSurface`, `NavSelectionState`, `ReaderSelection` (+145 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PdfViewerActivity` connect `Community 0` to `Community 2`, `Community 4`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Why does `TutorialOverlay()` connect `Community 1` to `Community 0`, `Community 11`, `Community 43`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Why does `Text` connect `Community 1` to `Community 24`, `Community 2`, `Community 43`, `Community 28`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Are the 20 inferred relationships involving `Text` (e.g. with `PermissionRow()` and `.onCreate()`) actually correct?**
  _`Text` has 20 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PreToolUse`, `Bundle`, `BlockSurface` to the rest of the system?**
  _150 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07623456790123456 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07622504537205081 - nodes in this community are weakly interconnected._