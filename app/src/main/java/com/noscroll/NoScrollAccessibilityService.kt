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

        private val REELS_ACTION_KEYWORDS = listOf("like", "comment", "share", "send", "save", "bookmark")
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

    /**
     * Single tree scan that detects both:
     * - Whether we are in Reels (2+ action buttons on the right edge)
     * - Where the bottom nav bar starts (topmost clickable tab in the bottom 20%)
     *
     * Returns Pair(inReels, navBarTop). navBarTop is -1 if not found in tree.
     */
    private fun scanInstagramTree(root: AccessibilityNodeInfo): Pair<Boolean, Int> {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val rightThreshold = screenWidth * 0.72f
        val navThreshold = (screenHeight * 0.80).toInt()

        var reelsMatches = 0
        var navBarTop = Int.MAX_VALUE

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString() ?: ""
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (!rect.isEmpty) {
                // Reels action buttons: right edge of screen
                if (rect.centerX() > rightThreshold) {
                    val lower = desc.lowercase()
                    if (REELS_ACTION_KEYWORDS.any { lower.contains(it) }) reelsMatches++
                }

                // Bottom nav bar: clickable, in bottom 20%, tab-sized width
                val tabMinW = screenWidth / 8
                val tabMaxW = screenWidth / 2
                if (rect.top >= navThreshold && node.isClickable &&
                    rect.width() in tabMinW..tabMaxW
                ) {
                    if (rect.top < navBarTop) navBarTop = rect.top
                }
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }

        val inReels = reelsMatches >= 2
        val navTop = if (navBarTop != Int.MAX_VALUE) navBarTop else -1
        return Pair(inReels, navTop)
    }

    /** Best-effort nav bar top: accessibility tree first, then resource estimate. */
    private fun resolveNavBarTop(treeNavTop: Int): Int {
        if (treeNavTop > 0) return treeNavTop
        val screenHeight = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        val sysNavId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val sysNavPx = if (sysNavId > 0) resources.getDimensionPixelSize(sysNavId) else 0
        val instagramTabPx = (56 * density + 0.5f).toInt()
        return screenHeight - sysNavPx - instagramTabPx
    }

    private fun findAndUpdateOverlay() {
        val root = rootInActiveWindow ?: return
        val rect = Rect()

        try {
            val (inReels, treeNavTop) = scanInstagramTree(root)

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
