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
    private var pendingConfirm: Runnable? = null
    private var lastRect = Rect()

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 600L
        // After a foreign-package event the overlay is frozen (non-touchable) immediately.
        // We wait this long before checking whether Instagram is still the active window.
        private const val CONFIRM_MS = 500L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Bare system events — ignore entirely.
        if (pkg == "android") return

        // Our package: only stop overlay when an actual Activity comes to foreground.
        // WindowManager.addView() also fires with our package but className won't end with "Activity".
        if (pkg == packageName) {
            if (event.className?.toString()?.endsWith("Activity") == true) {
                cancelPendingConfirm()
                pendingUpdate?.let { handler.removeCallbacks(it) }
                stopOverlay()
            }
            return
        }

        if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
            // Instagram visible — cancel any freeze/confirm cycle and schedule overlay update.
            cancelPendingConfirm()
            pendingUpdate?.let { handler.removeCallbacks(it) }
            pendingUpdate = Runnable { findAndShowOverlay() }
            handler.postDelayed(pendingUpdate!!, DEBOUNCE_MS)
        } else {
            // Foreign package (WebView, notification shade, in-app browser, other app, etc.).
            // Freeze immediately so the overlay cannot intercept touches in the foreign app,
            // then confirm after a delay whether Instagram is still the active window.
            pendingUpdate?.let { handler.removeCallbacks(it) }
            cancelPendingConfirm()
            freezeOverlay()
            pendingConfirm = Runnable { confirmOrStop() }
            handler.postDelayed(pendingConfirm!!, CONFIRM_MS)
        }
    }

    private fun cancelPendingConfirm() {
        pendingConfirm?.let { handler.removeCallbacks(it) }
        pendingConfirm = null
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
            // Still in Instagram — unfreeze overlay and refresh its position.
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UNFREEZE
            })
            findAndShowOverlay()
        } else {
            stopOverlay()
        }
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

    private fun stopOverlay() {
        lastRect.setEmpty()
        pendingUpdate?.let { handler.removeCallbacks(it) }
        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
        )
    }

    override fun onInterrupt() {
        cancelPendingConfirm()
        stopOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingConfirm()
        stopOverlay()
    }
}
