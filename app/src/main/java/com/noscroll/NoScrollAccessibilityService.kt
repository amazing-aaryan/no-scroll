package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

class NoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    private var pendingConfirm: Runnable? = null
    private var pendingContentCheck: Runnable? = null

    private var cachedBgColor: Int = Color.BLACK
    private var lastColorSampleMs: Long = 0L
    private var reelsBlockActive = false

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 600L
        private const val CONFIRM_MS = 500L
        private const val CONTENT_DEBOUNCE_MS = 150L
        private const val COLOR_CACHE_MS = 1_000L

        // Right-side action strip — only present in full-screen Reels view.
        private val REELS_ACTION_KEYWORDS = setOf("like", "comment", "share", "send", "save", "bookmark")

        // Instagram bottom-nav tab descriptions across all recent app versions.
        private val NAV_TAB_KEYWORDS = setOf(
            "home", "search", "explore", "reels", "shop", "marketplace",
            "profile", "messages", "activity", "notifications"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Settings.canDrawOverlays(this)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android") return

        if (pkg == packageName) {
            if (event.className?.toString()?.endsWith("Activity") == true) {
                cancelAll()
                stopOverlay()
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
                    if (reelsBlockActive) hideOverlay()
                    reelsBlockActive = false
                    cancelPendingConfirm()
                    scheduleUpdate(DEBOUNCE_MS)
                } else {
                    cancelPendingContentCheck()
                    pendingUpdate?.let { handler.removeCallbacks(it) }
                    cancelPendingConfirm()
                    freezeOverlay()
                    pendingConfirm = Runnable { confirmOrStop() }
                    handler.postDelayed(pendingConfirm!!, CONFIRM_MS)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
                    cancelPendingContentCheck()
                    pendingContentCheck = Runnable { findAndUpdateOverlay() }
                    handler.postDelayed(pendingContentCheck!!, CONTENT_DEBOUNCE_MS)
                }
            }
        }
    }

    private fun scheduleUpdate(delayMs: Long) {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        pendingUpdate = Runnable { findAndUpdateOverlay() }
        handler.postDelayed(pendingUpdate!!, delayMs)
    }

    private fun cancelPendingConfirm() {
        pendingConfirm?.let { handler.removeCallbacks(it) }
        pendingConfirm = null
    }

    private fun cancelPendingContentCheck() {
        pendingContentCheck?.let { handler.removeCallbacks(it) }
        pendingContentCheck = null
    }

    private fun cancelAll() {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        cancelPendingConfirm()
        cancelPendingContentCheck()
    }

    private fun freezeOverlay() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_FREEZE
        })
    }

    private fun confirmOrStop() {
        pendingConfirm = null
        val root = rootInActiveWindow
        val activePkg = root?.packageName?.toString()
        root?.recycle()
        if (activePkg == INSTAGRAM_PKG || activePkg == INSTAGRAM_LITE_PKG) {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UNFREEZE
            })
            findAndUpdateOverlay()
        } else {
            stopOverlay()
        }
    }

    private data class ScanResult(val inReels: Boolean, val navBarTop: Int)

    /**
     * Single BFS that simultaneously detects Reels and locates the nav bar top.
     *
     * Reels: 2+ action buttons (Like/Comment/Share…) at centerX > 72 % of screen
     *        width. These only appear in full-screen Reels.
     *
     * Nav bar: 2+ clickable nodes whose description matches a known Instagram tab
     *          name (Home/Search/Profile…) in the bottom 25 % of the screen.
     *          Taking min(rect.top) over all matches gives the exact pixel row where
     *          the bar starts — works correctly on every screen size and density.
     *
     * The two keyword sets are disjoint and their screen regions do not overlap, so
     * neither detection can contaminate the other.
     */
    private fun scanInstagramTree(root: AccessibilityNodeInfo): ScanResult {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val rightEdge  = sw * 0.72f
        val navZoneTop = (sh * 0.75f).toInt()   // bottom 25 % catches nav on tall phones

        var reelsHits = 0
        var navTop = Int.MAX_VALUE
        var navHits  = 0

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node  = queue.removeFirst()
            val lower = node.contentDescription?.toString()?.lowercase() ?: ""
            val rect  = Rect()
            node.getBoundsInScreen(rect)

            if (!rect.isEmpty) {
                // ① Reels action strip — right edge only.
                if (rect.centerX() > rightEdge && REELS_ACTION_KEYWORDS.any { lower.contains(it) })
                    reelsHits++

                // ② Nav bar tabs — known descriptions, clickable, in bottom zone.
                if (rect.top >= navZoneTop && node.isClickable &&
                    NAV_TAB_KEYWORDS.any { lower.contains(it) }
                ) {
                    navHits++
                    if (rect.top < navTop) navTop = rect.top
                }
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }

        return ScanResult(
            inReels  = reelsHits >= 2,
            navBarTop = if (navHits >= 2 && navTop != Int.MAX_VALUE) navTop else -1
        )
    }

    /**
     * Resolves the pixel Y where the Reels block overlay must stop.
     * Uses the live accessibility-tree value when reliable (2+ nav tabs found);
     * otherwise computes from Android system resources — adapts to every device.
     */
    private fun resolveNavBarTop(treeValue: Int): Int {
        if (treeValue > 0) return treeValue
        val density = resources.displayMetrics.density
        val sysNavId  = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val sysNavPx  = if (sysNavId > 0) resources.getDimensionPixelSize(sysNavId) else 0
        val igTabPx   = (56f * density + 0.5f).toInt()   // Instagram tab bar is 56 dp on all phones
        return resources.displayMetrics.heightPixels - sysNavPx - igTabPx
    }

    private fun findAndUpdateOverlay() {
        val root = rootInActiveWindow ?: return
        val rect = Rect()

        try {
            val scan = scanInstagramTree(root)
            val inReels     = scan.inReels
            val treeNavTop  = scan.navBarTop

            if (inReels) {
                if (!reelsBlockActive) {
                    reelsBlockActive = true
                    val navBarY = resolveNavBarTop(treeNavTop)
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_BLOCK_REELS
                        putExtra("navBarY", navBarY)
                    })
                }
                return
            }

            if (reelsBlockActive) reelsBlockActive = false

            // Normal mode: small book overlay over the Reels nav tab.
            val reelsNode = findReelsNode(root) ?: run { hideOverlay(); return }
            reelsNode.getBoundsInScreen(rect)
            reelsNode.recycle()
            if (rect.isEmpty) { hideOverlay(); return }
        } finally {
            root.recycle()
        }

        sendOverlayIntent(rect, cachedBgColor)

        val now = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= 30 && now - lastColorSampleMs > COLOR_CACHE_MS) {
            lastColorSampleMs = now
            val sampleX = 2
            val sampleY = rect.top + rect.height() / 2
            sampleScreenColor(sampleX, sampleY) { color ->
                if (color != cachedBgColor) {
                    cachedBgColor = color
                    sendOverlayIntent(rect, color)
                }
            }
        }
    }

    @RequiresApi(30)
    private fun sampleScreenColor(x: Int, y: Int, onColor: (Int) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    buffer.close()
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    val color = soft?.getPixel(
                        x.coerceIn(0, soft.width - 1),
                        y.coerceIn(0, soft.height - 1)
                    ) ?: cachedBgColor
                    soft?.recycle()
                    onColor(color)
                }
                override fun onFailure(errorCode: Int) { /* keep cached */ }
            }
        )
    }

    private fun sendOverlayIntent(rect: Rect, bgColor: Int) {
        startService(Intent(this, OverlayService::class.java).apply {
            putExtra("x", rect.left)
            putExtra("y", rect.top)
            putExtra("w", rect.width())
            putExtra("h", rect.height())
            putExtra("bgColor", bgColor)
        })
    }

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

    private fun hideOverlay() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        })
    }

    private fun stopOverlay() {
        cancelAll()
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        })
    }

    override fun onInterrupt() = stopOverlay()

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }
}
