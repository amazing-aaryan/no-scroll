package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

class NoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    // Cached after first screenshot so tab switches are instant
    private var cachedContentBgColor: Int? = null
    private var cachedNavBgColor: Int? = null

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
            // No nav bar = full-screen story / camera — don't block
            if (navBarRect.isEmpty) { stopOverlay(); return }

            val storiesBottomY = findStoriesBottomY(root)
            val contentTopY = if (storiesBottomY > 0) storiesBottomY
                              else (screenHeight * 0.18).toInt()
            val contentH = (navBarRect.top - contentTopY).coerceAtLeast(0)

            val cachedContent = cachedContentBgColor
            val cachedNav = cachedNavBgColor

            if (cachedContent != null && cachedNav != null) {
                // Already sampled — apply instantly (no screenshot needed)
                sendToOverlayService(contentTopY, contentH, navBarRect, screenWidth,
                    cachedContent, cachedNav)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // First appearance: sample the real pixel colours from the screen
                sampleAndShowOverlay(contentTopY, contentH, navBarRect, screenWidth)
            } else {
                // Pre-Android 11 fallback: infer from system dark/light mode
                val contentColor = fallbackContentColor()
                val navColor = Color.BLACK
                cachedContentBgColor = contentColor
                cachedNavBgColor = navColor
                sendToOverlayService(contentTopY, contentH, navBarRect, screenWidth,
                    contentColor, navColor)
            }
        } finally {
            root.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun sampleAndShowOverlay(
        contentTopY: Int, contentH: Int, navBarRect: Rect, screenWidth: Int
    ) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer, screenshot.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshot.hardwareBuffer.close()

                    val contentColor: Int
                    val navColor: Int
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        val cx = screenWidth / 2
                        // Sample just below the stories row for feed background
                        val contentSampleY = (contentTopY + 8).coerceIn(0, bitmap.height - 1)
                        contentColor = fullyOpaque(bitmap.getPixel(cx, contentSampleY))
                        // Sample centre of nav bar
                        val navSampleY = navBarRect.centerY().coerceIn(0, bitmap.height - 1)
                        navColor = fullyOpaque(bitmap.getPixel(cx, navSampleY))
                        bitmap.recycle()
                    } else {
                        contentColor = fallbackContentColor()
                        navColor = Color.BLACK
                    }

                    cachedContentBgColor = contentColor
                    cachedNavBgColor = navColor
                    sendToOverlayService(contentTopY, contentH, navBarRect, screenWidth,
                        contentColor, navColor)
                }

                override fun onFailure(errorCode: Int) {
                    val contentColor = fallbackContentColor()
                    val navColor = Color.BLACK
                    cachedContentBgColor = contentColor
                    cachedNavBgColor = navColor
                    sendToOverlayService(contentTopY, contentH, navBarRect, screenWidth,
                        contentColor, navColor)
                }
            })
    }

    private fun fallbackContentColor(): Int =
        if (isDarkMode()) Color.BLACK else Color.parseColor("#FAFAFA")

    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    // Ensure sampled pixel is fully opaque regardless of compositing alpha
    private fun fullyOpaque(color: Int): Int = color or 0xFF000000.toInt()

    private fun sendToOverlayService(
        contentTopY: Int, contentH: Int, navBarRect: Rect, screenWidth: Int,
        contentBgColor: Int, navBgColor: Int
    ) {
        startService(Intent(this, OverlayService::class.java).apply {
            putExtra("contentY", contentTopY)
            putExtra("contentH", contentH)
            putExtra("navX", navBarRect.left)
            putExtra("navY", navBarRect.top)
            putExtra("navW", navBarRect.width())
            putExtra("navH", navBarRect.height())
            putExtra("screenW", screenWidth)
            putExtra("contentBgColor", contentBgColor)
            putExtra("navBgColor", navBgColor)
        })
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
        // Leave outRect empty — caller treats empty as "nav bar not visible"
    }

    private fun stopOverlay() {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        cachedContentBgColor = null
        cachedNavBgColor = null
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
