# Plan: Precise Reel Detection + Adaptive Overlay Background

**Branch:** `fix/reel-detection-adaptive-bg`  
**Base:** `codex/reader-selection-highlights-zen`  
**Priority:** Critical (icon placement is the core UX loop)

---

## Problem Statement

### P1 — Reel icon detection false positives
`findReelsNode()` in `NoScrollAccessibilityService.kt` relies solely on
content-description string matching (`"Reels"` / `"Reels tab"`) plus a loose
80%-of-screen-height threshold. This causes false positives because:
- Other accessibility nodes (e.g. banners, story headers) can carry the word
  "Reels" in their description.
- The nav-bar height threshold (0.80) is too wide — any node in the bottom 20%
  passes, even if it is not a nav tab.
- No check that the matched node is an actual tab *within* a nav-bar sibling
  group.
- The fallback `findBottomNavCenter` always picks the center child, which is
  wrong when Instagram's Reels tab is at position 3 of 5 (center) on some
  layouts but position 2 of 4 on others.

### P2 — Hardcoded black overlay background
`overlay_book.xml` sets `android:background="#000000"`. Instagram uses an
adaptive nav bar that is dark on dark-mode and white/light on light-mode (or
when viewing a white post). The black overlay stands out badly in light mode
and on AMOLED devices can look correct but still fail to match tinted nav
bars (e.g. translucent nav overlays in stories).

---

## Solution Overview

**Reel detection** — layered, increasingly strict matching:
1. Primary: `viewIdResourceName` containing `"reels"` or `"clips"` (Instagram's
   internal resource names for the Reels/Clips tab).
2. Secondary (fallback): content-description match, but gated by a full set of
   structural constraints:
   - Node must be a direct child of an identifiable nav-bar container.
   - Nav-bar container must span >= 80% screen width and sit in the bottom 12%
     of the screen (not just below 80%).
   - Node must be one of 4-6 sibling children of that container.
   - Node height must be < 15% of screen height.
   - Node's horizontal center must be within the middle 60% of the screen
     width (Reels is never the leftmost or rightmost tab).
3. Remove the stand-alone `findBottomNavCenter` fallback that blindly uses
   the center child. Replace with `findReelsInNavBar(navBar)` that iterates
   all children and scores each by resource-ID match -> description match ->
   position heuristic, returning the best-scored child.

**Adaptive background** — two-level detection:
1. When `findReelsNode` succeeds, walk up to its parent (the nav-bar
   container). Use `PixelCopy` (API 26+) to sample a 1x1 pixel from the
   nav-bar background, then pass the sampled `Int` color to `OverlayService`
   as an Intent extra.
2. Fallback (API < 26 or `PixelCopy` failure): detect system night mode via
   `resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK`.
   Use `Color.WHITE` in day mode, `Color.BLACK` in night mode.
3. `OverlayService.updateOverlay()` receives the `bgColor` Int extra and sets
   the inflated view's background programmatically via `view.setBackgroundColor`.
4. Remove the hardcoded `android:background` from `overlay_book.xml`.

---

## Dependency Graph

```
Step 1 --> Step 2 --> Step 3 --> Step 4
 (core         (overlay        (OverlayService  (verify
 detection     adaptive bg)    receives color)   & test)
 rewrite)
```

Steps 1-2 edit the same file; run them sequentially.
Step 3 edits a different file; can start once Step 1 interface is locked.

---

## Step 1 — Rewrite `findReelsNode` with layered constraints

**File:** `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`

### Context brief
Current `findReelsNode()` (lines 146-174) does a BFS over the full tree
and returns the first node whose content description equals "Reels" (case-
insensitive) and that is below 80% of screen height and narrower than
`screenWidth/3`. `findBottomNavCenter()` (lines 176-220) is the fallback.

### Task list
- [ ] Extract private helper `findNavBarNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo?`
  - BFS the tree
  - Return first node where ALL hold:
    - `nodeRect.top >= screenHeight * 0.88` (bottom 12%)
    - `nodeRect.width() >= screenWidth * 0.80`
    - `node.childCount in 4..6`
  - Recycle all other nodes along the way

- [ ] Replace current `findReelsNode` body with:
  1. Call `findNavBarNode(root)` -> `navBar`
  2. If `navBar == null` return null
  3. Iterate `navBar` children, call `scoreReelsChild(child, navBar)` per child
  4. Return child with highest score >= 2 (threshold to qualify)
  5. Recycle `navBar` and all non-selected children

- [ ] Implement `scoreReelsChild(child: AccessibilityNodeInfo, navBar: AccessibilityNodeInfo): Int`:
  - +3 if `viewIdResourceName?.lowercase()` contains `"reels"` or `"clips"`
  - +2 if contentDescription equals "Reels" or "Reels tab" (ignoreCase)
  - +1 if contentDescription contains "reels" (ignoreCase) but not the above
  - +1 if `child.isClickable`
  - -2 if child horizontal center is in leftmost 20% or rightmost 20% of the
    `navBar` bounds (Reels is never the extreme edge tab)
  - Score threshold to qualify: >= 2

- [ ] Delete `findBottomNavCenter()` entirely

- [ ] Update `findAndUpdateOverlay()`:
  - Keep `val reelsNode = findReelsNode(root)`
  - Replace `findBottomNavCenter(root, rect)` fallback with `hideOverlay(); return`
    (no nav bar found -> hide rather than guess)
  - After resolving `reelsNode`, also get `navBarRect` from `findNavBarNode`
    for use in Step 2 (or return both from `findReelsNode` as a data class)

### Verification
- `.\gradlew.bat assembleDebug` must pass
- Manual: open Instagram Reels tab, overlay appears
- Manual: Home/Profile/Search/DMs, overlay hides
- Manual: story view (nav bar gone), overlay hides

### Exit criteria
Build green. No false-positive overlay on non-Reels tabs.

---

## Step 2 — Adaptive background colour via `PixelCopy` + dark-mode fallback

**File:** `app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`

### Context brief
After Step 1, `findAndUpdateOverlay()` has the nav-bar rect. Use it to
sample the background colour before passing position to `OverlayService`.

### Task list
- [ ] Add imports: `android.view.PixelCopy`, `android.graphics.Bitmap`,
  `android.graphics.Color`, `android.content.res.Configuration`,
  `android.os.Build`, `android.view.WindowManager`, `android.content.Context`

- [ ] Implement `sampleNavBarBgColor(navBarRect: Rect): Int` (API >= 26 only):
  ```kotlin
  @RequiresApi(26)
  private fun sampleNavBarBgColor(navBarRect: Rect): Int {
      val sampleRect = Rect(
          navBarRect.left + 4, navBarRect.top + 4,
          navBarRect.left + 5, navBarRect.top + 5
      )
      val bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
      var result = Color.BLACK
      val latch = java.util.concurrent.CountDownLatch(1)
      try {
          @Suppress("DEPRECATION")
          val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
              .defaultDisplay
          PixelCopy.request(display, sampleRect, bm, { copyResult ->
              if (copyResult == PixelCopy.SUCCESS) result = bm.getPixel(0, 0)
              latch.countDown()
          }, Handler(Looper.getMainLooper()))
          latch.await(300, java.util.concurrent.TimeUnit.MILLISECONDS)
      } catch (_: Exception) {}
      bm.recycle()
      return result
  }
  ```

- [ ] Implement `detectBgColor(navBarRect: Rect): Int`:
  ```kotlin
  private fun detectBgColor(navBarRect: Rect): Int {
      if (Build.VERSION.SDK_INT >= 26) return sampleNavBarBgColor(navBarRect)
      val nightMode = resources.configuration.uiMode and
          Configuration.UI_MODE_NIGHT_MASK
      return if (nightMode == Configuration.UI_MODE_NIGHT_YES) Color.BLACK
             else Color.WHITE
  }
  ```

- [ ] In `findAndUpdateOverlay()`, after computing `rect`, compute `navBarRect`
  from the nav bar node and call `val bgColor = detectBgColor(navBarRect)`

- [ ] Add `bgColor` as extra in the startService Intent:
  ```kotlin
  putExtra("bgColor", bgColor)
  ```

### Verification
- `.\gradlew.bat assembleDebug` must pass
- Logcat: no PixelCopy exception in normal flow

### Exit criteria
Build green. `bgColor` extra visible in logcat when `Log.d` is temporarily added.

---

## Step 3 — Receive and apply adaptive background in `OverlayService`

**Files:** `app/src/main/java/com/noscroll/OverlayService.kt`,
`app/src/main/res/layout/overlay_book.xml`

### Context brief
`OverlayService.updateOverlay()` (line 72) inflates `overlay_book.xml`.
The XML currently hardcodes `android:background="#000000"` on the
`FrameLayout`. After this step, background is set programmatically.

### Task list
- [ ] `overlay_book.xml`: remove `android:background="#000000"` attribute
  from the root `<FrameLayout>`

- [ ] `OverlayService.onStartCommand()`: parse new extra:
  ```kotlin
  val bgColor = intent.getIntExtra("bgColor", Color.BLACK)
  updateOverlay(x, y, w, h, bgColor)
  ```

- [ ] Update `updateOverlay` signature:
  ```kotlin
  private fun updateOverlay(x: Int, y: Int, w: Int, h: Int, bgColor: Int = Color.BLACK)
  ```

- [ ] After `val view = LayoutInflater.from(this).inflate(R.layout.overlay_book, null)`:
  ```kotlin
  view.setBackgroundColor(bgColor)
  ```

- [ ] Add import `android.graphics.Color`

- [ ] Keep ACTION_HIDE/FREEZE/UNFREEZE/STOP paths unchanged

### Verification
- `.\gradlew.bat assembleDebug` must pass
- Manual light mode: overlay background matches light nav bar
- Manual dark mode: overlay background matches dark nav bar
- Story mode: overlay hidden (not shown with wrong background)

### Exit criteria
Build green. Overlay background visually matches Instagram nav bar in both
light and dark mode.

---

## Step 4 — Regression check + memory-leak audit

### Task list
- [ ] `.\gradlew.bat assembleDebug` — must be green
- [ ] Verify every early-return path in the new `findNavBarNode`,
  `findReelsNode`, and `scoreReelsChild` recycles all
  `AccessibilityNodeInfo` objects that were obtained but not returned
- [ ] Confirm `findAndUpdateOverlay` hides overlay correctly when:
  - Instagram story open (nav bar not present)
  - DMs open (nav bar hidden)
  - Non-Instagram app in foreground
- [ ] Confirm no `PixelCopy` timeout blocks the main thread >300 ms
  (the latch timeout is already 300 ms; verify no ANR in perfetto trace)
- [ ] Remove any temporary `Log.d` added during Steps 2-3
- [ ] Update `plans/reel-detection-adaptive-bg.md` with Done status

### Exit criteria
Full build green. All manual checks pass. No logcat errors in 60 s of use.

---

## Rollback strategy

If `CountDownLatch.await` on the accessibility thread causes an ANR:
1. Remove the latch entirely from `sampleNavBarBgColor`
2. Make it fully async: store result in `AtomicInteger bgColorCache`
3. `detectBgColor` returns `bgColorCache.get()` immediately (defaults to
   `Color.BLACK` until first successful sample)
4. `PixelCopy` callback updates `bgColorCache` for the next overlay update
   Worst case: first overlay shows with default color for one update cycle.

---

## Anti-patterns avoided

- No hardcoded tab indices (Reels position varies by region/version).
- No `Thread.sleep` on the main/accessibility thread.
- No `viewIdResourceName` as sole criterion (obfuscation changes IDs between
  Instagram versions).
- No screen-capture permission required (`PixelCopy` with a `Display` ref
  does not need `READ_FRAME_BUFFER` on modern APIs).
- No nav-bar color assumption — sampled at runtime every update cycle.
