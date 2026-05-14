package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    private var lastRect = Rect()

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 600L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore our own overlay window events and bare system events — these fire
        // when WindowManager.addView is called and must not trigger stopOverlay.
        if (pkg == packageName || pkg == "android") return

        if (pkg != INSTAGRAM_PKG && pkg != INSTAGRAM_LITE_PKG) {
            stopOverlay()
            return
        }

        pendingUpdate?.let { handler.removeCallbacks(it) }
        pendingUpdate = Runnable { findAndShowOverlay() }
        handler.postDelayed(pendingUpdate!!, DEBOUNCE_MS)
    }

    private fun findAndShowOverlay() {
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

            if (rect.isEmpty) { stopOverlay(); return }

            lastRect.set(rect)
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

    // BFS on the accessibility tree; returns node caller must recycle, or null.
    private fun findReelsNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.contains("reels", ignoreCase = true)) {
                queue.forEach { it.recycle() }
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return null
    }

    // Fallback: locate the bottom nav bar and pick its center child.
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
            val center = navBar.getChild(childCount / 2)
            center?.getBoundsInScreen(outRect)
            center?.recycle()
        }
        navBar.recycle()
    }

    private fun hideOverlay() {
        lastRect.setEmpty()
        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE
            }
        )
    }

    private fun stopOverlay() {
        lastRect.setEmpty()
        pendingUpdate?.let { handler.removeCallbacks(it) }
        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
        )
    }

    override fun onInterrupt() = stopOverlay()

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }
}
