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

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 50L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

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
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val navBarRect = Rect()
            findNavBarRect(root, navBarRect)

            // No nav bar visible = full-screen story, camera, etc. — don't block
            if (navBarRect.isEmpty) { stopOverlay(); return }

            val storiesBottomY = findStoriesBottomY(root)
            val contentTopY = if (storiesBottomY > 0) storiesBottomY
                             else (screenHeight * 0.18).toInt()

            startService(
                Intent(this, OverlayService::class.java).apply {
                    putExtra("contentY", contentTopY)
                    putExtra("contentH", (navBarRect.top - contentTopY).coerceAtLeast(0))
                    putExtra("navX", navBarRect.left)
                    putExtra("navY", navBarRect.top)
                    putExtra("navW", navBarRect.width())
                    putExtra("navH", navBarRect.height())
                    putExtra("screenW", screenWidth)
                }
            )
        } finally {
            root.recycle()
        }
    }

    // BFS: find the bottom of the stories row by looking for story-labelled nodes.
    private fun findStoriesBottomY(root: AccessibilityNodeInfo): Int {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        var maxBottom = -1
        val nodeRect = Rect()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.contains("story", ignoreCase = true)) {
                node.getBoundsInScreen(nodeRect)
                if (nodeRect.bottom > maxBottom) maxBottom = nodeRect.bottom
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return maxBottom
    }

    // BFS: locate the bottom nav bar and return its full bounds.
    private fun findNavBarRect(root: AccessibilityNodeInfo, outRect: Rect) {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        val navThreshold = (screenHeight * 0.82).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            if (nodeRect.top >= navThreshold &&
                nodeRect.width() >= screenWidth * 0.8f &&
                node.childCount in 4..6
            ) {
                outRect.set(nodeRect)
                node.recycle()
                queue.forEach { it.recycle() }
                return
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        // Leave outRect empty — caller interprets empty as "nav bar not found"
    }

    private fun stopOverlay() {
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
