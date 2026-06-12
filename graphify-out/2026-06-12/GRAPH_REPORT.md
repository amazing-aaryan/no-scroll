# Graph Report - .  (2026-06-01)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 702 nodes · 1257 edges · 43 communities (39 shown, 4 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 48 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `86221987`
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

## God Nodes (most connected - your core abstractions)
1. `Text` - 18 edges
2. `TutorialPrefs` - 17 edges
3. `readme_no_scroll` - 17 edges
4. `AccessibilityEvent` - 13 edges
5. `Context` - 12 edges
6. `DocumentRow()` - 12 edges
7. `TutorialController` - 11 edges
8. `PageHolder` - 10 edges
9. `Int` - 10 edges
10. `Host` - 9 edges

## Surprising Connections (you probably didn't know these)
- `PermissionRow()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/SetupActivity.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `ShareRow()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/quote/ShareBottomSheet.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `ImportCard()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/ui/LibraryScreen.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `TooltipCard()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/tutorial/TutorialOverlay.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt
- `CompactChip()` --calls--> `Text`  [INFERRED]
  app/src/main/java/com/noscroll/ui/LibraryScreen.kt → app/src/main/java/com/noscroll/metadata/CoverPageOcr.kt

## Import Cycles
- None detected.

## Communities (43 total, 4 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (22): Bitmap, BookMetadataEntity, Boolean, Bundle, Float, HighlightEntity, Int, Intent (+14 more)

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (44): TutorialController, TutorialStep, BookEntity, BookMetadataEntity, Boolean, HighlightEntity, List, String (+36 more)

### Community 2 - "Community 2"
Cohesion: 0.08
Nodes (22): AccessibilityService, Bundle, Bundle, LegalBookSearchResult, List, String, Uri, Boolean (+14 more)

### Community 3 - "Community 3"
Cohesion: 0.06
Nodes (31): AnnotationDao, Context, Flow, List, Long, Flow, Int, List (+23 more)

### Community 4 - "Community 4"
Cohesion: 0.08
Nodes (19): Boolean, Float, Int, List, PdfDocument, PdfRect, String, Throwable (+11 more)

### Community 5 - "Community 5"
Cohesion: 0.12
Nodes (16): Int, Job, List, String, ViewGroup, Bitmap, Int, Job (+8 more)

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (8): Boolean, Bundle, String, Uri, Boolean, BookEntity, BookMetadataEntity, TutorialPrefs

### Community 7 - "Community 7"
Cohesion: 0.17
Nodes (9): AccessibilityEvent, AccessibilityNodeInfo, Int, Long, Rect, BlockSurface, NavSelectionState, ScanResult (+1 more)

### Community 8 - "Community 8"
Cohesion: 0.16
Nodes (10): Boolean, Int, Intent, View, IBinder, Notification, OverlayMode, Service (+2 more)

### Community 9 - "Community 9"
Cohesion: 0.22
Nodes (9): Bitmap, BookMetadataEntity, Boolean, Context, List, PdfDocument, String, Uri (+1 more)

### Community 10 - "Community 10"
Cohesion: 0.28
Nodes (8): Context, Int, List, String, Uri, JSONArray, PdfEntry, SharedPreferences

### Community 11 - "Community 11"
Cohesion: 0.25
Nodes (10): Bitmap, Float, List, String, Canvas, QuoteCardSpec, QuoteCardTheme, QuoteCardSpec (+2 more)

### Community 12 - "Community 12"
Cohesion: 0.22
Nodes (9): BookEntity, Boolean, Context, Flow, Int, List, Long, String (+1 more)

### Community 13 - "Community 13"
Cohesion: 0.11
Nodes (18): readme_accessibility_service, readme_android_manifest_xml, readme_android_phone, readme_android_studio, readme_com_instagram_android, readme_com_instagram_lite, readme_floating_action_button, readme_gradle (+10 more)

### Community 14 - "Community 14"
Cohesion: 0.14
Nodes (8): List, Rect, TutorialStepId, Unit, TutorialAnchor(), TutorialController, TooltipSide, TutorialStepId

### Community 15 - "Community 15"
Cohesion: 0.18
Nodes (8): BookEntity, Boolean, Flow, Int, List, Long, String, BookDao

### Community 16 - "Community 16"
Cohesion: 0.29
Nodes (7): Flow, HighlightEntity, Int, List, Long, String, HighlightDao

### Community 17 - "Community 17"
Cohesion: 0.36
Nodes (6): Any, Boolean, List, String, LegalBookDownloadLink, LegalBookSearchResult

### Community 18 - "Community 18"
Cohesion: 0.26
Nodes (7): Boolean, Context, File, List, String, Uri, HighlightEntity

### Community 20 - "Community 20"
Cohesion: 0.33
Nodes (6): Context, HighlightEntity, Int, List, Long, String

### Community 21 - "Community 21"
Cohesion: 0.25
Nodes (5): Flow, List, Long, AnnotationDao, AnnotationEntity

### Community 22 - "Community 22"
Cohesion: 0.53
Nodes (4): Activity, Bitmap, String, Uri

### Community 23 - "Community 23"
Cohesion: 0.27
Nodes (6): BookMetadataEntity, Flow, List, String, BookMetadataDao, BookMetadataDao

### Community 24 - "Community 24"
Cohesion: 0.27
Nodes (7): android, Bundle, String, BottomSheetDialogFragment, Dialog, newInstance(), ShareRow()

### Community 25 - "Community 25"
Cohesion: 0.42
Nodes (4): Int, String, OpenLibraryResult, org

### Community 26 - "Community 26"
Cohesion: 0.36
Nodes (4): Boolean, MotionEvent, Listener, PageTurnInterceptor

### Community 27 - "Community 27"
Cohesion: 0.43
Nodes (4): AnnotationEntity, Context, Long, String

### Community 28 - "Community 28"
Cohesion: 0.39
Nodes (4): Bitmap, List, String, CoverBlock

### Community 29 - "Community 29"
Cohesion: 0.38
Nodes (4): Context, String, Uri, EmbeddedPdfMetadata

### Community 31 - "Community 31"
Cohesion: 0.57
Nodes (3): Context, File, String

### Community 32 - "Community 32"
Cohesion: 0.53
Nodes (3): Int, String, GoogleBooksResult

### Community 33 - "Community 33"
Cohesion: 0.53
Nodes (3): List, PdfRect, String

### Community 35 - "Community 35"
Cohesion: 0.40
Nodes (3): BookMetadataEntity, Context, Uri

### Community 37 - "Community 37"
Cohesion: 0.50
Nodes (3): Context, Flow, NotebookState

## Knowledge Gaps
- **138 isolated node(s):** `Bundle`, `BlockSurface`, `NavSelectionState`, `SparseArray`, `Uri` (+133 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NoScrollTheme()` connect `Community 2` to `Community 0`, `Community 24`, `Community 6`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Why does `Text` connect `Community 1` to `Community 24`, `Community 2`, `Community 28`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **Why does `TextView` connect `Community 8` to `Community 0`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Are the 17 inferred relationships involving `Text` (e.g. with `PermissionRow()` and `.onCreate()`) actually correct?**
  _`Text` has 17 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Bundle`, `BlockSurface`, `NavSelectionState` to the rest of the system?**
  _138 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07418788410886742 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07227891156462585 - nodes in this community are weakly interconnected._