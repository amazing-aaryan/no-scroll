package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    private var pendingConfirm: Runnable? = null
    private var pendingContentCheck: Runnable? = null

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 600L
        private const val CONFIRM_MS = 500L
        // Content-change events fire very frequently inside Instagram (scroll, animation, etc.).
        // Debounce heavily so we only re-evaluate the nav bar a short time after things settle.
        private const val CONTENT_DEBOUNCE_MS = 100L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Settings.canDrawOverlays(this)) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "android") return

        // Our own package: only stop when a real Activity comes to foreground.
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
                // Only care about content changes inside Instagram — used to detect
                // when the bottom nav bar disappears (story, post, DMs, comments).
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

    // Checks whether the Reels button is actually on screen.
    // Shows the overlay if found, hides it (without stopping the service) if not.
    private fun findAndUpdateOverlay() {
        val root = rootInActiveWindow ?: return
        try {
            val rect = Rect()
            val reelsNode = findReelsNode(root)
            if (reelsNode != null) {
                reelsNode.getBoundsInScreen(rect)
                reelsNode.recycle()
            } else {
                findBottomNavCenter(root, rect)
            }

            if (rect.isEmpty) {
                // Nav bar not visible — hide overlay but keep service alive for fast re-show.
                hideOverlay()
                return
            }

            startService(
                Intent(this, OverlayService::class.java).apply {
                    putExtra("x", rect.left)
                    putExtra("y", rect.top)
                    putExtra("w", rect.width())
                    putExtra("h", rect.height())
                }
            )
        } finally {
            root.recycle()
        }
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

    private fun findBottomNavCenter(root: AccessibilityNodeInfo, outRect: Rect) {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        val navThreshold = (screenHeight * 0.82).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        var navBar: AccessibilityNodeInfo? = null
        outer@ while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            if (nodeRect.top >= navThreshold &&
                nodeRect.width() >= screenWidth * 0.8f &&
                node.childCount in 4..6
            ) {
                queue.forEach { it.recycle() }
                navBar = node
                break@outer
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }

        navBar ?: return
        val childCount = navBar.childCount
        if (childCount > 0) {
            // Prefer the child with "Reels" description; fall back to center child
            var target: AccessibilityNodeInfo? = null
            for (i in 0 until childCount) {
                val child = navBar.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                if (desc.contains("reels", ignoreCase = true)) {
                    target = child
                    break
                }
                child.recycle()
            }
            if (target == null) target = navBar.getChild(childCount / 2)
            target?.getBoundsInScreen(outRect)
            target?.recycle()
        }
        navBar.recycle()
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
