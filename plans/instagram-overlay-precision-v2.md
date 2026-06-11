# Instagram Overlay Precision v2 — Multi-Session Blueprint

**Branch:** `fix/instagram-overlay-precision-v2`
**Base:** `codex/reader-selection-highlights-zen`
**Created:** 2026-05-31
**Status:** READY — Phase 1 is the entry point for a fresh session

---

## Mission Statement

The NoScroll Instagram overlay must enforce this exact surface map:

| Surface | Overlay behaviour |
|---------|------------------|
| Home feed (scrolled or not) | Full block below action bar; stories tray visible and tappable |
| Story viewer | No overlay |
| Reels tab | Full block (already works; needs regression guard) |
| Search — Explore grid (no query) | Full block below search bar |
| Search — Typing (keyboard up) | No block (already works) |
| Search — Results (query submitted, keyboard down) | No block — allow reading results |
| Search — Profile page opened from search | No block |
| Profile tab / any other tab | No block |

### Known Bugs (confirmed from code + dump analysis)

**BUG-1 (Home scroll gap):**
`storiesBarBottom` is derived from individual story-circle buttons
(`avatar_view` nodes capped at y≈542). The actual stories tray
(`reels_tray_container`) ends at y=612. When scrolled any amount, story
usernames (y=542–588) and padding (y=588–612) leak above the block.
Fix: capture `reels_tray_container` bounds directly for `storiesBarBottom`.

**BUG-2 (Nav-bar bottom gap):**
`navGapPx` is derived as 25% of navBarHeight clamped to [24dp, 48dp]. On a
1080×2340 device (density≈2.75) the nav bar is 144px → navGapPx=66px. This
creates a 66px strip of feed content visible just above the nav bar.
Fix: set block bottom = navBarTop (zero gap), removing the artificial gap.

**BUG-3 (Search results not distinguished from explore grid):**
`inSearchExplore` fires whenever `searchTabSelected && !searchBarFocused`.
This blocks BOTH the initial explore grid (correct) AND search results after
the user submits a query (wrong). The EditText still carries the query text
after the keyboard dismisses; checking `editText.text.isNotEmpty()` as
`searchBarHasText` separates these two states.

**BUG-4 (Profile page opened from search still blocked):**
When the user taps a profile card in search results, Instagram pushes the
profile screen. The bottom nav still shows "Search" selected, so
`searchTabSelected=true`. No profile-specific detection exists →
overlay blocks the profile. Fix: detect profile-page-specific node IDs
(`profile_header_container`, `follow_button`, `username_container`) to
exit block mode even when `searchTabSelected`.

**BUG-5 (Story viewer: back-to-home leaves block slow to restore):**
After exiting a story viewer, the 30s suppression window keeps the block
hidden even on the home feed. If the user then scrolls, they see unblocked
feed for up to 30s. Fix: reset `lastStoryTapMs` as soon as the home feed
is positively re-detected (storiesTrayPresent + inHome signal confirmed).

---

## Dependency Graph

```
Phase 1 (dump collection)
    |
Phase 2 (analysis — reads Phase 1 output)
    |
Phase 3 (BUG-1 + BUG-2 fix — home feed)   <-- can start from Phase 2 output alone
    |
Phase 4 (BUG-3 + BUG-4 fix — search precision)
    |
Phase 5 (BUG-5 fix — story viewer timing)
    |
Phase 6 (integration test — phone required)
    |
Phase 7 (cleanup + commit)
```

Phases 3–5 each touch `NoScrollAccessibilityService.kt` — run them
sequentially to avoid merge conflicts. Build must be green after each phase
before proceeding.

---

## Phase 1 — Dump Collection (research sub-agents + phone)

**Goal:** Capture fresh `uiautomator dump` XML files for every surface.
**Pre-condition:** Phone is connected via ADB (`adb devices` shows device).
**Files produced:** `dumps/` directory in project root.
**Agent tier:** Haiku (mechanical ADB commands, no reasoning needed).

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
Phone is connected via ADB. Run `adb devices` first to confirm.
You will collect accessibility-tree dumps from Instagram on the phone.
After each dump, pull the XML to the `dumps/` subdirectory.
Do NOT install anything. Do NOT modify source files.

### Step 1.1 — Create dumps directory

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\aarya\Desktop\no-scroll\dumps"
```

### Step 1.2 — Dump: Home feed, unscrolled

1. On phone: open Instagram, tap Home tab. Do NOT scroll.
2. Wait 3s for feed to load.
3. Run:
```
adb shell uiautomator dump /sdcard/dump_home_unscrolled.xml
adb pull /sdcard/dump_home_unscrolled.xml dumps/dump_home_unscrolled.xml
```

### Step 1.3 — Dump: Home feed, partially scrolled (stories still visible but shifted)

1. On phone: scroll home feed DOWN ~150px (so stories tray starts to slide up).
2. Run:
```
adb shell uiautomator dump /sdcard/dump_home_scroll_partial.xml
adb pull /sdcard/dump_home_scroll_partial.xml dumps/dump_home_scroll_partial.xml
```

### Step 1.4 — Dump: Home feed, fully scrolled (stories off screen)

1. On phone: scroll home feed all the way past stories tray. Some posts should be visible.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_home_scroll_full.xml
adb pull /sdcard/dump_home_scroll_full.xml dumps/dump_home_scroll_full.xml
```

### Step 1.5 — Dump: Story viewer (mid-story)

1. On phone: from home, tap any story to open the story viewer. Wait for it to load.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_story_viewer.xml
adb pull /sdcard/dump_story_viewer.xml dumps/dump_story_viewer.xml
```

### Step 1.6 — Dump: Search tab, explore grid (no query)

1. On phone: tap Search tab. Do NOT type anything. Wait for explore grid to load.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_search_explore.xml
adb pull /sdcard/dump_search_explore.xml dumps/dump_search_explore.xml
```

### Step 1.7 — Dump: Search tab, keyboard up (typing state)

1. On phone: tap search bar — keyboard appears. Do NOT type anything yet.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_search_typing.xml
adb pull /sdcard/dump_search_typing.xml dumps/dump_search_typing.xml
```

### Step 1.8 — Dump: Search tab, results showing

1. On phone: type "design" in search bar and press Enter/Search.
   Wait for results tabs (Top, Accounts, Audio, Tags, Places) to appear.
   Dismiss keyboard if still up.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_search_results.xml
adb pull /sdcard/dump_search_results.xml dumps/dump_search_results.xml
```

### Step 1.9 — Dump: Profile page opened from search

1. On phone: from search results (still on search tab), tap any Account result
   to open a user profile. Wait for profile to load.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_profile_from_search.xml
adb pull /sdcard/dump_profile_from_search.xml dumps/dump_profile_from_search.xml
```

### Step 1.10 — Dump: Reels tab

1. On phone: tap Reels (clips) tab. Let a reel load.
2. Run:
```
adb shell uiautomator dump /sdcard/dump_reels.xml
adb pull /sdcard/dump_reels.xml dumps/dump_reels.xml
```

### Verification

```powershell
Get-ChildItem dumps\
```

Should show 9 XML files. Each file must be > 10KB (non-empty dumps).

### Exit criteria

All 9 dump files present in `dumps/` and non-empty. Record file sizes.

---

## Phase 2 — Analysis Agent Team

**Goal:** Extract precise node IDs, bounds, and signals for each surface.
**Pre-condition:** Phase 1 complete, 9 dump XMLs in `dumps/`.
**Output:** `dumps/ANALYSIS.md` with findings.
**Agent tier:** Opus (reasoning required for cross-dump analysis).

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
Phase 1 has collected 9 XML dumps of Instagram screens into `dumps/`.
Your task is pure analysis — read the dumps and produce `dumps/ANALYSIS.md`.
Do NOT modify source files.

### Step 2.1 — Analyse home dumps (3 scroll states)

Read `dumps/dump_home_unscrolled.xml`, `dump_home_scroll_partial.xml`,
`dump_home_scroll_full.xml`.

For each dump, extract and record:

a) `reels_tray_container` node: resource-id, bounds `[left,top][right,bottom]`.
   Note if bounds are off-screen (top < 0) in scrolled dumps.

b) Individual story item buttons (content-desc containing "story" + " of "):
   list their bounds. Note the maximum `rect.bottom` value.

c) `main_feed_action_bar` bounds in each scroll state.

d) `tab_bar` bounds (nav bar). Record `top` value.

e) First feed post header bounds (`row_feed_profile_header` or similar).

f) The `layout_container_main` bounds.

Record findings in table form in ANALYSIS.md section "## HOME".

### Step 2.2 — Analyse story viewer dump

Read `dumps/dump_story_viewer.xml`.

Extract:
a) Is `reels_tray_container` present? Is `main_feed_action_bar` present?
b) Is the nav `tab_bar` present? What are its bounds?
c) What is the back button resource-id?
d) Are there any story-specific IDs (`story_viewer_*`, `ig_story_*`, etc.)?
e) Is there any EditText? If so, what is its resource-id and does it have text?

Record in ANALYSIS.md section "## STORY_VIEWER".

### Step 2.3 — Analyse search dumps (explore grid, typing, results)

Read all three search dumps.

For each, extract:
a) Search EditText resource-id. Is it present? Is `isFocused=true`? Does it have a `text` attribute?
b) `search_tab` node: `isSelected=true/false`.
c) Any explore-grid-specific IDs: `explore_*`, `explore_media_container`, photo grid nodes.
d) Any result-tab-row IDs: `search_results_*`, `search_tab_row`, filter chip IDs.
e) `tab_bar` bounds.
f) Search action bar / header bounds.

Record in ANALYSIS.md section "## SEARCH".

### Step 2.4 — Analyse profile-from-search dump

Read `dumps/dump_profile_from_search.xml`.

Extract:
a) Resource IDs in the profile header area: `profile_header_*`, `follow_button`,
   `unfollow_button`, `restricted_profile_*`, `username_container`, `biography_text`.
b) Is `search_tab` node `isSelected=true`?
c) Is there a search EditText? Is it visible?
d) Are there any `explore_*` or grid IDs visible?
e) What distinguishes this screen from the explore grid? List the IDs.

Record in ANALYSIS.md section "## PROFILE_FROM_SEARCH".

### Step 2.5 — Analyse reels dump

Read `dumps/dump_reels.xml`.

Extract:
a) `clips_tab` / `reels_tab` node: bounds, `isSelected`.
b) Action rail nodes (right edge, like/comment/share buttons): resource-ids and bounds.
c) Full-screen video container bounds.
d) `tab_bar` bounds.

Record in ANALYSIS.md section "## REELS".

### Step 2.6 — Synthesise fix specifications

Based on all findings, write ANALYSIS.md section "## FIX_SPECS" with:

a) `storiesBarBottom` fix: confirm `reels_tray_container` bounds approach is valid.
   State the exact `rect.bottom` value seen in the unscrolled home dump.

b) Search state detection: exact node IDs to check, exact conditions for each of
   the 4 search states (explore / typing / results / profile).

c) Profile page detection: minimum set of IDs that UNIQUELY identify a profile screen
   (not present on explore grid). List them explicitly.

d) Bottom gap: confirm `navBarRect.top` value. Confirm `navGapPx` formula and
   what removing it does to `resolvedBlockBottom`.

e) Story viewer re-entry: confirm whether `reels_tray_container` is present in the
   story viewer dump. State which condition safely identifies "we are back on home feed."

### Verification

```powershell
Get-Content dumps\ANALYSIS.md | Select-Object -First 5
```

Must start with "## HOME" and have sections HOME, STORY_VIEWER, SEARCH,
PROFILE_FROM_SEARCH, REELS, FIX_SPECS all present.

### Exit criteria

`dumps/ANALYSIS.md` exists and has all 6 sections populated with real node IDs and bounds.

---

## Phase 3 — BUG-1 + BUG-2 Fix (Home Feed)

**Goal:** Fix top gap when scrolling + bottom gap above nav bar.
**File:** `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`
**Pre-condition:** Phase 2 complete; `dumps/ANALYSIS.md` section HOME populated.
**Agent tier:** Sonnet (code modification).

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
File to edit: `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`
Read `dumps/ANALYSIS.md` section "HOME" and "FIX_SPECS" before coding.
Build must be green after every edit: `.\gradlew.bat assembleDebug`

### Step 3.1 — Read current code

Read `NoScrollAccessibilityService.kt` lines 240–500 (the `scanInstagramTree` method).
Note:
- `storiesBarBottom` detection: lines ~351–358
- `reels_tray_container` detection: lines ~289–293 (sets `storiesTrayPresent` but not `storiesBarBottom`)
- `adjustedBlockTop` computation: lines ~471–492
- `navGapPx` computation: lines ~429–441
- `resolvedBlockBottom` computation: line ~441

### Step 3.2 — Fix storiesBarBottom (BUG-1)

**Problem:** `storiesBarBottom` only captures story circle button bounds (y=542).
The `reels_tray_container` correctly ends at y=612 (where posts begin) but its
width is full-screen so the `isNarrowStoryItem` filter excludes it.

**Fix:** In the `reels_tray_container` detection block (around line 290), ALSO
set `storiesBarBottom` from the tray container bounds when:
- `!rect.isEmpty`
- `rect.top >= 0` (not scrolled off screen)
- `rect.top < (sh * 0.45f)` (within top portion of screen)

```kotlin
// Before:
if (viewIdLower.contains("reels_tray_container") || viewIdLower == "com.instagram.android:id/reels_tray") {
    storiesTrayPresent = true
}

// After:
if (viewIdLower.contains("reels_tray_container") || viewIdLower == "com.instagram.android:id/reels_tray") {
    storiesTrayPresent = true
    // Tray container bottom = exact y where feed posts begin.
    if (!rect.isEmpty && rect.top >= 0 && rect.top < (sh * 0.45f) && rect.bottom > storiesBarBottom) {
        storiesBarBottom = rect.bottom
    }
}
```

The existing per-item storiesBarBottom logic (~lines 351-358) acts as a fallback
when the container is off-screen. Keep it as-is.

### Step 3.3 — Fix bottom gap (BUG-2)

**Problem:** `navGapPx` subtracts 24–48dp from `resolvedNavTop`, leaving visible
feed content below the block.

Locate the `resolvedBlockBottom` computation (~line 441):

```kotlin
// Before:
val resolvedBlockBottom = (if (resolvedNavTop > 0) resolvedNavTop - navGapPx else resolveNavBarTop(resolvedNavTop))
    .coerceIn(0, sh)

// After: block extends to nav bar top edge (zero gap)
val resolvedBlockBottom = (if (resolvedNavTop > 0) resolvedNavTop else resolveNavBarTop(-1))
    .coerceIn(0, sh)
```

Do NOT remove the `navGapPx` variable — just stop subtracting it here.

### Step 3.4 — Build and run

```powershell
.\gradlew.bat assembleDebug
```

Must complete `BUILD SUCCESSFUL`.

### Step 3.5 — Deploy to phone

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Step 3.6 — Manual test: Home scrolling

On phone with the new APK:
1. Open Instagram, go to Home tab.
2. Verify: stories tray visible, overlay blocks feed content.
3. Scroll down slowly — verify overlay top edge tracks just below the stories
   tray bottom (no gap visible between tray and overlay top).
4. Scroll fully past stories — verify overlay top edge is just below the action bar.
5. Scroll back up — overlay adjusts back to below stories.
6. Verify no feed content strip visible above the nav bar.

```
adb shell screencap /sdcard/test_home.png && adb pull /sdcard/test_home.png
adb logcat -d -s NoScrollA11y:D | tail -10
```

### Verification commands

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -d -s NoScrollA11y:D | Select-Object -Last 50
```

### Exit criteria

- Build green.
- Home feed: no gap at top in any scroll position.
- Block extends to nav bar top (no content strip visible above nav).
- Stories tray still visible and tappable.
- Logcat shows `scan surface=HOME blockRect=[0,N][1080,M]` where N is at or below
  the stories tray bottom, M equals the nav bar top.

---

## Phase 4 — BUG-3 + BUG-4 Fix (Search Precision)

**Goal:** Distinguish explore grid / typing / results / profile within the search tab.
**File:** `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`
**Pre-condition:** Phase 3 complete (build green). Phase 2 ANALYSIS.md sections
"SEARCH" and "PROFILE_FROM_SEARCH" populated.
**Agent tier:** Sonnet (code modification).

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
The build is currently green. File to modify:
`app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`

Read `dumps/ANALYSIS.md` sections "SEARCH" and "PROFILE_FROM_SEARCH"
for the exact node IDs discovered in Phase 2.

The goal: the search tab has four states. Only the first should be blocked.

| State | Signal | Block? |
|-------|--------|--------|
| Explore grid (no query) | search EditText present, text empty, NOT focused | YES |
| Typing | search EditText focused | NO (already handled) |
| Results | search EditText has text, NOT focused | NO (new fix) |
| Profile page | profile-specific IDs visible | NO (new fix) |

Build must pass after every edit.

### Step 4.1 — Read current code

Read `NoScrollAccessibilityService.kt`. Note:
- `searchBarFocused` detection: line ~371-373
- `inSearchExplore` computation: lines ~449-451
- `scanInstagramTree` return struct: lines ~512-521

### Step 4.2 — Add `searchBarHasText` variable (BUG-3)

In `scanInstagramTree`, add:

```kotlin
var searchBarHasText = false   // search EditText has a non-empty query
```

In the BFS node loop, alongside the `isSearchEdit` detection (~line 370-372):

```kotlin
val isSearchEdit = node.className?.toString()?.contains("EditText") == true &&
    rect.top < (sh * 0.20f)
if (isSearchEdit && node.isFocused) searchBarFocused = true
// NEW: query submitted — keyboard dismissed but text remains
if (isSearchEdit && !node.text.isNullOrEmpty()) searchBarHasText = true
```

Update `inSearchExplore` to also exclude when a query exists:

```kotlin
// Before:
val inSearchExplore = (searchTabSelected || ...) &&
    !searchBarFocused && !inHome && !inReels && !directTabSelected

// After:
val inSearchExplore = (searchTabSelected || ...) &&
    !searchBarFocused && !searchBarHasText &&
    !inHome && !inReels && !directTabSelected
```

### Step 4.3 — Add profile-page detection (BUG-4)

**Problem:** Profile opened from search still has `searchTabSelected=true`.

Add in `companion object`:

```kotlin
// Profile page IDs — replace with actual IDs from dumps/ANALYSIS.md PROFILE_FROM_SEARCH
private val PROFILE_PAGE_IDS = setOf(
    "follow_button", "unfollow_button", "following_button",
    "profile_header_container", "biography_text",
    "profile_header_username", "profile_header_follow_and_insight_button"
)
```

**CRITICAL:** Replace the placeholder IDs above with the ACTUAL IDs from
`dumps/ANALYSIS.md` section PROFILE_FROM_SEARCH. The above are common
patterns — verify each against the real dump.

Add variable in `scanInstagramTree`:

```kotlin
var profilePageVisible = false
```

In the BFS loop, detect profile nodes:

```kotlin
if (PROFILE_PAGE_IDS.any { viewIdLower.contains(it) }) {
    profilePageVisible = true
}
```

Update `inSearchExplore`:

```kotlin
val inSearchExplore = (searchTabSelected || ...) &&
    !searchBarFocused && !searchBarHasText && !profilePageVisible &&
    !inHome && !inReels && !directTabSelected
```

### Step 4.4 — Update ScanResult for debug logging

Add to `ScanResult` data class:

```kotlin
private data class ScanResult(
    val blockSurface: BlockSurface?,
    val navBarTop: Int,
    val blockRect: Rect?,
    val navSelectionState: NavSelectionState,
    val directTabSelected: Boolean,
    val homeChromeHits: Int,
    val storiesBarBottom: Int,
    val searchBarHasText: Boolean,     // new
    val profilePageVisible: Boolean    // new
)
```

Update the `return ScanResult(...)` call and the debug `Log.d` string.

### Step 4.5 — Build

```powershell
.\gradlew.bat assembleDebug
```

### Step 4.6 — Deploy and test

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Manual test sequence on phone:
1. Tap Search tab → explore grid blocked.
2. Tap search bar → keyboard up → block removed.
3. Type "architecture" → press search → keyboard dismisses → block STAYS removed (results visible).
4. Tap an Account result → profile opens → profile NOT blocked.
5. Press back → return to search results → results still not blocked.
6. Press back → return to explore grid → block returns.
7. Go to Home tab → home still blocked correctly (regression check).
8. Go to Reels tab → reels still blocked (regression check).

```
adb logcat -d -s NoScrollA11y:D | tail -20
```

For results state logcat should show: `scan surface=null ... searchSelected=true searchFocused=false`
For explore grid: `scan surface=SEARCH_EXPLORE`

### Exit criteria

- Build green.
- Explore grid blocked.
- Search results unblocked.
- Profile from search unblocked.
- Returning to explore grid re-blocks.
- Home and Reels unaffected (regression check).

---

## Phase 5 — BUG-5 Fix (Story Viewer Re-entry Timing)

**Goal:** Reset story suppression window as soon as home feed is positively confirmed.
**File:** `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`
**Pre-condition:** Phase 4 complete (build green). Phase 2 ANALYSIS.md section
"STORY_VIEWER" populated.
**Agent tier:** Sonnet (targeted code modification).

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
Build is currently green.
File: `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`

The current story suppression logic suppresses the overlay for 30 seconds
after any story tap. This is too long: if the user exits the story viewer and
returns to the home feed, the overlay should restore within ~500ms, not 30s.

The fix: in `findAndUpdateOverlay()`, after the `scanInstagramTree()` call,
when `scan.blockSurface == BlockSurface.HOME` AND `scan.storiesBarBottom > 0`
(stories tray is visible on screen — impossible inside the story viewer),
reset `lastStoryTapMs = 0`.

Read `dumps/ANALYSIS.md` section STORY_VIEWER before coding to confirm:
- Whether `reels_tray_container` appears in the story viewer with off-screen bounds.
- Whether `scan.blockSurface == HOME` can be true inside the story viewer.
  (It should NOT be — the story viewer has no nav-bar tab selected / no home chrome.)

### Step 5.1 — Read current story suppression code

Read `findAndUpdateOverlay()` lines ~537–626. Note:
- `lastStoryTapMs` check: lines 538-541.
- How `blockSurface == HOME` is determined.

### Step 5.2 — Implement early suppression reset

In `findAndUpdateOverlay()`, after the `scanInstagramTree(root)` call and
after the `directTabSelected` early return (~line 555), add:

```kotlin
// Home feed positively confirmed and stories are on-screen → exit the story viewer
// suppression window early (don't wait the full 30s).
if (scan.blockSurface == BlockSurface.HOME && scan.storiesBarBottom > 0) {
    lastStoryTapMs = 0
}
```

Place this BEFORE the `if (blockSurface != null && blockRect != null)` block.

Verify this is safe: `scan.blockSurface == HOME` requires `homeTabSelected || storiesTrayPresent`
AND NOT in reels, NOT directTab, NOT searchTab. Inside a story viewer the nav
bar home tab is NOT selected → `blockSurface != HOME` → this code does NOT run
inside the story viewer.

### Step 5.3 — Build

```powershell
.\gradlew.bat assembleDebug
```

### Step 5.4 — Deploy and test story flow

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Manual test:
1. Open Instagram, go to Home. Verify block showing.
2. Tap a story → story viewer opens → verify block removed.
3. Swipe through 3 more stories → verify block stays removed.
4. Press back to exit story viewer → verify block appears within ~1 second.
5. Repeat with "swipe down to close story" gesture.

```
adb logcat -d -s NoScrollA11y:D | tail -20
```

### Exit criteria

- Build green.
- Block appears within 1s of exiting story viewer.
- Block does NOT appear while stories are being viewed.
- Block does NOT appear mid-swipe between stories.

---

## Phase 6 — Integration Testing (All Surfaces)

**Goal:** Regression test every surface defined in the mission statement.
**Pre-condition:** Phases 3–5 complete, build green, APK installed.
**Agent tier:** Haiku (test sequencing).
**Tools:** ADB screencap, logcat.

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
Build is green. APK is installed on connected phone.
Your task: run through every surface scenario, capture screenshots + logcat,
and produce `test-results/INTEGRATION_REPORT.md`.

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\aarya\Desktop\no-scroll\test-results"
```

### Step 6.1 — Test matrix

| ID | Surface | Expected overlay |
|----|---------|-----------------|
| T1 | Instagram → Home, unscrolled | Block covers feed; stories tray visible |
| T2 | Instagram → Home, scrolled past stories | Block covers full screen minus nav bar; no gap at top |
| T3 | Instagram → Home → tap story → story viewer | No overlay |
| T4 | Instagram → story → back → Home | Block restored within 1s |
| T5 | Instagram → Reels tab | Full block |
| T6 | Instagram → Search tab (idle, no query) | Block covers explore grid |
| T7 | Instagram → Search, tap bar (keyboard up) | No block |
| T8 | Instagram → Search, type + submit query | No block; results visible |
| T9 | Instagram → Search → tap account → profile | No block |
| T10 | Instagram → Search → back from profile → results | No block |
| T11 | Instagram → Search → back to explore grid | Block restored |
| T12 | Instagram → Profile tab | No overlay |
| T13 | Instagram → DMs | No overlay |

### Step 6.2 — Capture commands (repeat for each TN)

Navigate manually on phone to each surface, then:

```
adb shell screencap /sdcard/test_tN.png
adb pull /sdcard/test_tN.png test-results/test_tN.png
adb logcat -d -s NoScrollA11y:D 2>&1 | tail -3
```

### Step 6.3 — Write integration report

Create `test-results/INTEGRATION_REPORT.md` with:
- Header: date, branch, device model, Android version
- Table: | ID | Surface | Expected | Actual | PASS/FAIL | Screenshot |
- For failures: root cause and which Phase to revisit

### Step 6.4 — Bug triage

For any FAIL: note the Phase, the surface, and the exact unexpected behaviour.
Do NOT fix in this phase — only report.

### Exit criteria

All 13 tests PASS. If any FAIL, report is complete with root cause.

---

## Phase 7 — Cleanup + Commit

**Goal:** Clean up debug logs, remove temporary files, commit.
**Pre-condition:** Phase 6 INTEGRATION_REPORT shows all 13 PASS.
**Agent tier:** Haiku.

### Context brief for a fresh agent

You are working in `C:\Users\aarya\Desktop\no-scroll`.
All 13 integration tests pass. Time to clean up and commit.

### Step 7.1 — Check for unwrapped debug logs

In `NoScrollAccessibilityService.kt`, check for any temporary `Log.d` calls
added during Phases 3–5 that are NOT inside `if (BuildConfig.DEBUG)`.
The existing debug block (~lines 501–510) is intentional — keep it.

### Step 7.2 — Final build check

```powershell
.\gradlew.bat assembleDebug
```

Must be green.

### Step 7.3 — Review diff

```
git diff HEAD -- app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt
```

Expected changes only:
- `reels_tray_container` also sets `storiesBarBottom`
- `navGapPx` subtraction removed from `resolvedBlockBottom`
- `searchBarHasText` detection added
- `profilePageVisible` detection added + `PROFILE_PAGE_IDS` in companion object
- `inSearchExplore` updated conditions
- `lastStoryTapMs` early-reset in `findAndUpdateOverlay`
- `ScanResult` additions

### Step 7.4 — Commit

```
git add app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt
git commit -m "fix(overlay): precise surface detection for home, search, profile, stories"
```

### Step 7.5 — Update plan status

Edit top of this file: change `**Status:** READY` to `**Status:** COMPLETE`.

### Exit criteria

Build green. Single focused commit. Plan marked COMPLETE.

---

## Rollback Strategy

If any Phase breaks existing functionality:
1. `git stash` current changes.
2. `.\gradlew.bat assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk`
   to restore last green APK.
3. Return to the failing Phase and re-read ANALYSIS.md before re-attempting.

**`searchBarHasText` false positives** (e.g. stale text from a previous session):
Add: `isSearchEdit && !node.text.isNullOrEmpty() && rect.top >= 0 && rect.bottom <= sh`
to ensure only on-screen EditText nodes are checked.

**`profilePageVisible` false positives** (e.g. shared ID on explore grid):
Narrow `PROFILE_PAGE_IDS` to only IDs that are ABSENT from the explore grid
dump. Cross-check both `dump_search_explore.xml` and `dump_profile_from_search.xml`.

**`navGapPx = 0` clips nav bar icons** (block rect overlaps nav bar):
If `resolvedNavTop` is off by a few pixels, reduce block bottom by 2–4dp.
Add: `.coerceAtMost(resolvedNavTop - (2 * resources.displayMetrics.density).toInt())`

---

## Anti-patterns to Avoid

- Do NOT use tab position index (0,1,2,3,4) — Instagram rearranges tabs by region.
- Do NOT use content-description text for profile detection — it is localized.
- Do NOT use node count or depth heuristics — they break across app versions.
- Do NOT add Thread.sleep or blocking calls on the accessibility thread.
- Do NOT hardcode pixel values — always derive from `sh`/`sw` or `density`.
- Do NOT trust `searchTabSelected` alone to mean the explore grid is showing.
- Do NOT trust `storiesTrayPresent` to mean stories are on-screen — it stays in
  the tree when scrolled off. Use `storiesBarBottom > 0` for on-screen confirmation.
- Do NOT skip Phase 2 analysis and guess node IDs — always verify against real dumps.

---

## Key File Reference

| File | Lines | Topic |
|------|-------|-------|
| `NoScrollAccessibilityService.kt` | 249–521 | `scanInstagramTree` — all detection logic |
| `NoScrollAccessibilityService.kt` | 537–626 | `findAndUpdateOverlay` — overlay update decision |
| `NoScrollAccessibilityService.kt` | 443–499 | `adjustedBlockTop` + `blockRect` computation |
| `NoScrollAccessibilityService.kt` | 429–441 | `navGapPx` computation |
| `OverlayService.kt` | 214–251 | `showBlockRegion` — the block overlay renderer |
| `dumps/ANALYSIS.md` | all | Real node IDs from Phase 2 (populated at runtime) |
| `test-results/INTEGRATION_REPORT.md` | all | 13-row test results from Phase 6 |

---

## Session Entry Points — Copy-Paste These

**Phase 1** (need phone connected):
> "Phone is connected. Run Phase 1 of `plans/instagram-overlay-precision-v2.md` to collect Instagram accessibility dumps."

**Phase 2** (analysis only, no phone needed):
> "Phase 1 dumps are in `dumps/`. Run Phase 2 of `plans/instagram-overlay-precision-v2.md` to produce ANALYSIS.md."

**Phase 3** (home feed fix, need phone):
> "Phase 2 is done. ANALYSIS.md is in `dumps/`. Run Phase 3 of `plans/instagram-overlay-precision-v2.md` to fix home scroll gap and nav gap."

**Phase 4** (search fix, need phone):
> "Phase 3 is done. Run Phase 4 of `plans/instagram-overlay-precision-v2.md` to fix search tab precision."

**Phase 5** (story timing fix, need phone):
> "Phase 4 is done. Run Phase 5 of `plans/instagram-overlay-precision-v2.md` to fix story viewer re-entry timing."

**Phase 6** (integration test, need phone):
> "Phases 3–5 are done. Run Phase 6 of `plans/instagram-overlay-precision-v2.md` to run the full test matrix."

**Phase 7** (cleanup):
> "All 13 tests pass. Run Phase 7 of `plans/instagram-overlay-precision-v2.md` to clean up and commit."
