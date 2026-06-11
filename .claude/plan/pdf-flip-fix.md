# Implementation Plan: PDF Page Flip Fix + Page Separation

## Task Type
- [x] Frontend/UI — Android PdfViewerActivity animation + RecyclerView layout

---

## Problem Diagnosis

### 1. Simultaneous dual-view animation looks like "two pages colliding"
Current code runs exitView and enterView animations at the same time:
- At t=190ms (midpoint): exitView is at ±35°, enterView is at ∓35°
- Both are visible simultaneously, overlapping at odd angles
- Root cause of the bad look — confirmed by Codex analysis

### 2. Page separation non-existent
- `item_pdf_page.xml` has no margin between items
- Pages flow together as one continuous strip

### 3. Fling detection correct in principle, threshold may be slightly high
- `dispatchTouchEvent` → GestureDetector sees all events before RecyclerView
- Vertical RecyclerView does NOT intercept horizontal swipes
- `MIN_FLIP_VELOCITY = 600f` may be too high for slower deliberate swipes

---

## Reference: Official Android Card Flip Pattern
(Source: Android card-flip training docs, EasyFlipViewPager library)
- Two-phase **sequential** animation: exit phase completes, then enter phase begins
- Exit page becomes invisible at 90° (edge-on), content swaps, enter page appears from 90°
- Camera distance formula: `density * 8000f–16000f`

---

## Correct Pivot Logic (derived from prior PageFlipTransformer + Android card-flip docs)

**Forward (swipe left → next page):**
- Exit:  `pivotX = containerWidth` (right edge), `rotationY: 0f → -90f`, AccelerateInterpolator
- Enter: `pivotX = 0f` (left edge), `rotationY: 90f → 0f`, DecelerateInterpolator, startDelay = exit duration

**Backward (swipe right → prev page):**
- Exit:  `pivotX = 0f` (left edge), `rotationY: 0f → +90f`
- Enter: `pivotX = containerWidth` (right edge), `rotationY: -90f → 0f`, startDelay = exit duration

---

## Technical Solution

Two ImageViews in an overlay FrameLayout, animated via a single `AnimatorSet.playTogether` with staggered `startDelay`:
- exitRot (200ms, Accelerate, starts at 0ms)
- enterAlpha flash from 0→1 (10ms, starts at 190ms — makes enter visible at swap point)
- enterRot (200ms, Decelerate, starts at 200ms)

Camera distance: `resources.displayMetrics.density * 16000f`

---

## Implementation Steps

### Step 1 — Page visual separation
**File: `app/src/main/res/layout/item_pdf_page.xml`**
- Add `android:layout_marginBottom="20dp"` to root ImageView
- Add `android:layout_marginTop="4dp"` for consistent top spacing

### Step 2 — Fix flipToPage() animation
**File: `app/src/main/java/com/noscroll/PdfViewerActivity.kt`**

Replace the existing `flipToPage()` body with:

```kotlin
private fun flipToPage(targetPage: Int, direction: Int) {
    if (isFlipAnimating) return
    val safe = targetPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    if (safe == currentPage) return
    isFlipAnimating = true

    val uri = currentUri ?: run { isFlipAnimating = false; return }
    val fromPage = currentPage
    val w = resources.displayMetrics.widthPixels

    lifecycleScope.launch {
        val exitBmp = renderPageBitmap(uri, fromPage, w)
        val enterBmp = renderPageBitmap(uri, safe, w)

        if (exitBmp == null || enterBmp == null) {
            exitBmp?.recycle(); enterBmp?.recycle()
            isFlipAnimating = false; return@launch
        }

        val cw = pdfContainer.width.toFloat().coerceAtLeast(1f)
        val ch = pdfContainer.height.toFloat().coerceAtLeast(1f)
        val camDist = resources.displayMetrics.density * 16000f

        // Forward → exit pivots at right edge, enter at left edge (and vice versa for backward)
        val exitPivotX    = if (direction > 0) cw   else 0f
        val exitEndRot    = if (direction > 0) -90f  else 90f
        val enterPivotX   = if (direction > 0) 0f   else cw
        val enterStartRot = if (direction > 0) 90f   else -90f

        val overlay  = FrameLayout(this@PdfViewerActivity).apply {
            setBackgroundColor(Color.parseColor("#171615"))
        }
        val matchAll = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val exitView = ImageView(this@PdfViewerActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(exitBmp)
            cameraDistance = camDist
            pivotX = exitPivotX
            pivotY = ch / 2f
        }
        val enterView = ImageView(this@PdfViewerActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(enterBmp)
            cameraDistance = camDist
            pivotX = enterPivotX
            pivotY = ch / 2f
            rotationY = enterStartRot
            alpha = 0f
        }

        overlay.addView(enterView, matchAll)
        overlay.addView(exitView,  matchAll)
        pdfContainer.addView(overlay, matchAll)
        pdfRecyclerView.suppressLayout(true)

        val HALF = 200L
        val exitRot    = ObjectAnimator.ofFloat(exitView,  "rotationY", 0f, exitEndRot).also {
            it.duration = HALF; it.interpolator = AccelerateInterpolator()
        }
        val enterAlpha = ObjectAnimator.ofFloat(enterView, "alpha", 0f, 1f).also {
            it.duration = 10; it.startDelay = HALF - 10
        }
        val enterRot   = ObjectAnimator.ofFloat(enterView, "rotationY", enterStartRot, 0f).also {
            it.duration = HALF; it.interpolator = DecelerateInterpolator(); it.startDelay = HALF
        }

        AnimatorSet().apply {
            playTogether(exitRot, enterAlpha, enterRot)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    pdfContainer.removeView(overlay)
                    exitBmp.recycle(); enterBmp.recycle()
                    pdfRecyclerView.suppressLayout(false)
                    (pdfRecyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(safe, 0)
                    currentPage = safe
                    PdfStorage.savePage(this@PdfViewerActivity, safe)
                    updateProgressAsync(safe)
                    isFlipAnimating = false
                }
            })
            start()
        }
    }
}
```

### Step 3 — Tune fling detection thresholds
In the `swipeDetector` lambda:
- Change ratio `1.3f` → `1.1f`

In companion object:
- Change `MIN_FLIP_VELOCITY = 600f` → `MIN_FLIP_VELOCITY = 400f`

### Step 4 — Add AccelerateInterpolator import
```kotlin
import android.view.animation.AccelerateInterpolator
```

---

## Key Files

| File | Operation | Description |
|------|-----------|-------------|
| `app/src/main/res/layout/item_pdf_page.xml` | Edit | Add bottom margin 20dp + top margin 4dp |
| `app/src/main/java/com/noscroll/PdfViewerActivity.kt` | Edit | Replace flipToPage(), tune thresholds, add import |

---

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| `pivotY` set before view height known | Use `pdfContainer.height / 2f` — container already measured at call time |
| Rapid repeated swipes | `isFlipAnimating` guard blocks new flip until current completes |
| Config change during animation | `onDestroy` → `closePdfRenderer()` runs; orphaned overlay is harmless on Activity teardown |
| `enterView` visible too early | `enterAlpha` fires at HALF-10ms, 10ms before enterRot, flash is imperceptible |

---

## SESSION_ID
- CODEX_SESSION: codex-1779087974
- GEMINI_SESSION: (unavailable — Gemini CLI not installed)
