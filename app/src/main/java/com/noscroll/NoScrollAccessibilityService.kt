package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
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

    private fun findAndUpdateOverlay() {
        val root = rootInActiveWindow ?: return
        try {
            val navBar = findNavBarNode(root)
            if (navBar == null) {
                hideOverlay()
                return
            }

            val navBarRect = Rect()
            navBar.getBoundsInScreen(navBarRect)

            val reelsNode = findReelsInNavBar(navBar, navBarRect)
            navBar.recycle()

            if (reelsNode == null) {
                hideOverlay()
                return
            }

            val rect = Rect()
            reelsNode.getBoundsInScreen(rect)
            reelsNode.recycle()

            if (rect.isEmpty) {
                hideOverlay()
                return
            }

            val bgColor = detectBgColor()

            startService(
                Intent(this, OverlayService::class.java).apply {
                    putExtra("x", rect.left)
                    putExtra("y", rect.top)
                    putExtra("w", rect.width())
                    putExtra("h", rect.height())
                    putExtra("bgColor", bgColor)
                }
            )
        } finally {
            root.recycle()
        }
    }

    private fun findNavBarNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        val navThreshold = (screenHeight * 0.88).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)

            if (nodeRect.top >= navThreshold &&
                nodeRect.width() >= screenWidth * 0.80f &&
                node.childCount in 4..6
            ) {
                queue.forEach { it.recycle() }
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return null
    }

    private fun findReelsInNavBar(navBar: AccessibilityNodeInfo, navBarRect: Rect): AccessibilityNodeInfo? {
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 1

        for (i in 0 until navBar.childCount) {
            val child = navBar.getChild(i) ?: continue
            val score = scoreReelsChild(child, navBarRect)
            if (score > bestScore) {
                bestNode?.recycle()
                bestNode = child
                bestScore = score
            } else {
                child.recycle()
            }
        }
        return bestNode
    }

    private fun scoreReelsChild(child: AccessibilityNodeInfo, navBarRect: Rect): Int {
        var score = 0
        val resId = child.viewIdResourceName?.lowercase() ?: ""
        val desc = child.contentDescription?.toString() ?: ""

        if (resId.contains("reels") || resId.contains("clips")) score += 3
        when {
            desc.equals("Reels", ignoreCase = true) || desc.equals("Reels tab", ignoreCase = true) -> score += 2
            desc.contains("reels", ignoreCase = true) -> score += 1
        }
        if (child.isClickable) score += 1

        val childRect = Rect()
        child.getBoundsInScreen(childRect)
        val navBarWidth = navBarRect.width().toFloat()
        if (navBarWidth > 0f) {
            val childCenter = childRect.centerX() - navBarRect.left
            val edgeZone = navBarWidth * 0.20f
            if (childCenter < edgeZone || childCenter > navBarWidth - edgeZone) score -= 2
        }

        return score
    }

    private fun detectBgColor(): Int {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES)
            Color.argb(220, 0, 0, 0)
        else
            Color.argb(220, 255, 255, 255)
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
