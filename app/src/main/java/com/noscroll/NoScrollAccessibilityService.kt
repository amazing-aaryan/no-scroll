package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.os.Build
import com.noscroll.BuildConfig
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
    private var feedBlockActive = false
    private var lastStableBlockRect: Rect? = null
    private var lastStableBlockMs: Long = 0L

    // Polling — runs every POLL_INTERVAL_MS while Instagram is in the foreground.
    private var pollingActive = false
    private val pollRunnable = object : Runnable {
        override fun run() {
            findAndUpdateOverlay()
            if (pollingActive) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun startPolling() {
        if (pollingActive) return
        pollingActive = true
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        pollingActive = false
        handler.removeCallbacks(pollRunnable)
    }

    companion object {
        private const val TAG = "NoScrollA11y"
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val INSTAGRAM_LITE_PKG = "com.instagram.lite"
        private const val DEBOUNCE_MS = 600L
        private const val CONFIRM_MS = 500L
        private const val CONTENT_DEBOUNCE_MS = 150L
        private const val COLOR_CACHE_MS = 1_000L
        private const val BLOCK_STABILITY_GRACE_MS = 3_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val NAV_BAR_GAP_MIN_DP = 24f
        private const val NAV_BAR_GAP_MAX_DP = 48f
        private const val NAV_BAR_GAP_FRACTION = 0.25f

        // ── Language-independent view-ID fragments (primary detection) ──────────────
        private val REELS_TAB_IDS = setOf(
            "clips_tab", "reels_tab", "ig_bottom_navbar_reels_button", "clips_tab_icon"
        )
        private val HOME_TAB_IDS = setOf(
            "feed_tab", "home_tab", "ig_bottom_navbar_home_button", "feed_tab_icon"
        )
        private val OTHER_TAB_IDS = setOf(
            "search_tab", "profile_tab", "activity_tab", "shop_tab",
            "ig_bottom_navbar_search_button", "ig_bottom_navbar_profile_button",
            "direct_tab", "message_tab", "direct_inbox_tab"
        )
        private val MESSAGES_TAB_IDS = setOf(
            "direct_tab", "message_tab", "direct_inbox_tab", "direct_list_v2_tab"
        )
        private val NAV_BAR_CONTAINER_IDS = setOf(
            "tab_bar", "bottom_nav", "ig_bottom_navbar", "navigation_bar_view"
        )
        private val REELS_LIKE_IDS = setOf(
            "like_button", "row_clips_button_like", "row_feed_button_like", "clips_like_button"
        )
        private val REELS_COMMENT_IDS = setOf(
            "comment_button", "row_clips_button_comment", "row_feed_button_comment", "clips_comment_button"
        )
        private val REELS_SHARE_IDS = setOf(
            "share_button", "row_clips_button_send", "row_feed_button_share",
            "clips_share_button", "send_button"
        )
        private val REELS_ACTION_IDS =
            REELS_LIKE_IDS + REELS_COMMENT_IDS + REELS_SHARE_IDS +
            setOf("save_button", "bookmark_button", "row_clips_button_save")
        private val HOME_TOP_BAR_IDS = setOf(
            "main_feed_action_bar", "action_bar_container", "ig_bar_logo",
            "action_bar_title", "feed_header"
        )

        // ── Keyword fallbacks (secondary — English-only, used when IDs absent) ──
        private val REELS_ACTION_KEYWORDS = setOf("like", "comment", "share", "send", "save", "bookmark")
        private val REELS_ENGAGEMENT_KEYWORDS = setOf("like", "comment")
        private val HOME_TOP_BAR_KEYWORDS = setOf("instagram", "camera", "direct", "messages", "new post")

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
                    cancelPendingConfirm()
                    scheduleUpdate(DEBOUNCE_MS)
                    startPolling()
                } else {
                    stopPolling()
                    cancelPendingContentCheck()
                    pendingUpdate?.let { handler.removeCallbacks(it) }
                    cancelPendingConfirm()
                    freezeOverlay()
                    pendingConfirm = Runnable { confirmOrStop() }
                    handler.postDelayed(pendingConfirm!!, CONFIRM_MS)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
                    // Polling covers continuous updates; also do an immediate debounced refresh
                    // on scroll/content events for snappier response.
                    cancelPendingContentCheck()
                    pendingContentCheck = Runnable { findAndUpdateOverlay() }
                    handler.postDelayed(pendingContentCheck!!, CONTENT_DEBOUNCE_MS)
                    startPolling()
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
        stopPolling()
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

    private enum class BlockSurface {
        HOME,
        REELS,
        SEARCH_EXPLORE
    }

    private enum class NavSelectionState {
        BLOCKED,
        UNBLOCKED,
        UNKNOWN
    }

    private data class ScanResult(
        val blockSurface: BlockSurface?,
        val navBarTop: Int,
        val blockRect: Rect?,
        val navSelectionState: NavSelectionState,
        val directTabSelected: Boolean
    )

    /**
     * Single BFS that classifies the current Instagram surface and derives the
     * feed-content rectangle to block.
     *
     * Reels: compact right-edge action rail signature.
     * Home: selected Home tab + top-app-bar signature.
     * Block region: use the largest content container between top chrome and nav bar when
     * available; otherwise fall back to full-width bounds derived from detected chrome.
     */
    private fun scanInstagramTree(root: AccessibilityNodeInfo): ScanResult {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val rightEdge  = sw * 0.72f
        val navZoneTop = (sh * 0.75f).toInt()
        val topChromeZoneBottom = (sh * 0.22f).toInt()

        var reelsHits = 0
        var reelsEngagementHits = 0
        var navTop = Int.MAX_VALUE
        var navHits  = 0
        var topChromeBottom = 0
        var homeTabSelected = false
        var reelsTabSelected = false
        var otherTabSelected = false
        var directTabSelected = false
        var searchTabSelected = false
        var searchBarFocused = false   // true when user is actively typing
        var homeChromeHits = 0
        var bestContentRect: Rect? = null
        var bestContentArea = 0
        var mainContainerRect: Rect? = null
        var navBarRect: Rect? = null
        var actionBarRect: Rect? = null
        var storiesBarBottom = 0  // detected bottom of the stories strip on Home

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node  = queue.removeFirst()
            val lower = node.contentDescription?.toString()?.lowercase() ?: ""
            val textLower = node.text?.toString()?.lowercase() ?: ""
            val viewIdLower = node.viewIdResourceName?.lowercase() ?: ""
            val rect  = Rect()
            node.getBoundsInScreen(rect)

            if (!rect.isEmpty) {
                if (NAV_BAR_CONTAINER_IDS.any { viewIdLower.contains(it) } && !viewIdLower.contains("shadow")) {
                    navBarRect = Rect(rect)
                }
                if (viewIdLower.contains("action_bar") || viewIdLower.contains("main_feed_action_bar")) {
                    if (actionBarRect == null || rect.bottom > actionBarRect!!.bottom) {
                        actionBarRect = Rect(rect)
                    }
                }
                if (
                    viewIdLower.contains("layout_container_main") ||
                    viewIdLower.contains("swipeable_tab_view_pager") ||
                    viewIdLower == "android:id/list" ||
                    viewIdLower.contains("refreshable_container")
                ) {
                    val area = rect.width() * rect.height()
                    val existingArea = mainContainerRect?.let { it.width() * it.height() } ?: -1
                    if (area > existingArea) {
                        mainContainerRect = Rect(rect)
                    }
                }

                // ① Reels action strip — right edge, mid-content, compact actionable nodes.
                val inActionRailZone = rect.centerX() > rightEdge &&
                    rect.centerY() > (sh * 0.20f) &&
                    rect.centerY() < navZoneTop &&
                    rect.width() in 1..(sw * 0.28f).toInt() &&
                    rect.height() in 1..(sh * 0.18f).toInt()
                if (inActionRailZone && node.isClickable) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "RAIL node id=$viewIdLower desc=$lower bounds=$rect clickable=${node.isClickable}")
                    // ID check first (language-independent), keyword fallback second.
                    val idActionHit = REELS_ACTION_IDS.any { viewIdLower.contains(it) }
                    val kwActionHit = REELS_ACTION_KEYWORDS.any { lower.contains(it) }
                    if (idActionHit || kwActionHit) reelsHits++
                    val idEngageHit = (REELS_LIKE_IDS + REELS_COMMENT_IDS).any { viewIdLower.contains(it) }
                    val kwEngageHit = REELS_ENGAGEMENT_KEYWORDS.any { lower.contains(it) }
                    if (idEngageHit || kwEngageHit) reelsEngagementHits++
                }

                // ② Top chrome — ID check first (language-independent), keywords as fallback.
                val topChromeMatch =
                    HOME_TOP_BAR_IDS.any { viewIdLower.contains(it) } ||
                    (lower.isNotBlank() && HOME_TOP_BAR_KEYWORDS.any { lower.contains(it) || textLower.contains(it) || viewIdLower.contains(it) })
                // Widened to 0.90 — Instagram action bar spans ~73% of width on S24
                if (rect.top < topChromeZoneBottom && rect.width() < (sw * 0.90f).toInt() && topChromeMatch) {
                    homeChromeHits++
                    if (rect.bottom > topChromeBottom) topChromeBottom = rect.bottom
                }

                // ② Stories strip — content-desc is "reels tray container" (space, not underscore).
                //    Only count when the node is actually on-screen (rect.top >= 0), so scrolling
                //    past the stories resets storiesBarBottom to 0.
                val looksLikeStory = viewIdLower.contains("reels_tray") ||
                    lower.contains("reels tray") ||
                    lower.contains("story") || lower.contains("stories") ||
                    viewIdLower.contains("story") || viewIdLower.contains("stories")
                if (looksLikeStory && rect.top >= 0 && rect.top < (sh * 0.45f) && rect.bottom > storiesBarBottom) {
                    storiesBarBottom = rect.bottom
                }

                // ③ Nav bar tabs — ID check first (language-independent), keyword fallback.
                //    Also detect DM/Messages tab regardless of rect bounds (may be hidden during reel play).
                val isHomeTabId  = HOME_TAB_IDS.any { viewIdLower.contains(it) }
                val isReelsTabId = REELS_TAB_IDS.any { viewIdLower.contains(it) }
                val isOtherTabId = OTHER_TAB_IDS.any { viewIdLower.contains(it) }
                val isMsgTabId   = MESSAGES_TAB_IDS.any { viewIdLower.contains(it) }
                val isAnyTabId   = isHomeTabId || isReelsTabId || isOtherTabId || isMsgTabId
                val isAnyTabKw   = NAV_TAB_KEYWORDS.any { lower.contains(it) || textLower.contains(it) || viewIdLower.contains(it) }
                // Detect DM tab selected even when nav bar is hidden (empty bounds)
                if (isMsgTabId && (node.isSelected || node.isChecked)) directTabSelected = true
                // Search bar focus — user is actively typing → don't block search results
                val isSearchEdit = node.className?.toString()?.contains("EditText") == true &&
                    rect.top < (sh * 0.20f)
                if (isSearchEdit && node.isFocused) searchBarFocused = true
                if (rect.top >= navZoneTop && node.isClickable && (isAnyTabId || isAnyTabKw)) {
                    navHits++
                    if (rect.top < navTop) navTop = rect.top
                    if (node.isSelected || node.isChecked) {
                        val isSearchTabId = OTHER_TAB_IDS.any { viewIdLower.contains("search") && viewIdLower.contains(it) } ||
                            viewIdLower.contains("search_tab") || viewIdLower.contains("explore_tab")
                        when {
                            isHomeTabId ||
                            lower.contains("home") || textLower.contains("home") ||
                            viewIdLower.contains("home") || viewIdLower.contains("feed_tab") ->
                                homeTabSelected = true
                            isReelsTabId ||
                            lower.contains("reels") || lower.contains("clips") ||
                            textLower.contains("reels") || textLower.contains("clips") ||
                            viewIdLower.contains("reels") || viewIdLower.contains("clips") ->
                                reelsTabSelected = true
                            isMsgTabId -> directTabSelected = true
                            isSearchTabId ||
                            lower.contains("search") || lower.contains("explore") ||
                            textLower.contains("search") || textLower.contains("explore") ->
                                searchTabSelected = true
                            else -> otherTabSelected = true
                        }
                    }
                }

                // ④ Main feed/pager container — prefer the largest scrollable content region.
                val candidateTop = if (topChromeBottom > 0) topChromeBottom else 0
                val inContentBand = rect.top <= candidateTop + (sh * 0.12f) &&
                    rect.bottom > candidateTop + (sh * 0.25f) &&
                    rect.bottom <= navZoneTop + (sh * 0.08f)
                val looksLikeContent = rect.width() >= (sw * 0.60f).toInt() &&
                    rect.height() >= (sh * 0.25f).toInt() &&
                    (node.isScrollable || node.className?.toString()?.contains("RecyclerView") == true ||
                        node.className?.toString()?.contains("ViewPager") == true ||
                        node.className?.toString()?.contains("ListView") == true)
                if (inContentBand && looksLikeContent) {
                    val area = rect.width() * rect.height()
                    if (area > bestContentArea) {
                        bestContentArea = area
                        bestContentRect = Rect(rect)
                    }
                }
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }

        val heuristicNavTop = if (navHits >= 2 && navTop != Int.MAX_VALUE) navTop else -1
        val resolvedNavTop = maxOf(
            navBarRect?.top ?: -1,
            mainContainerRect?.bottom ?: -1,
            heuristicNavTop
        )
        val minGapPx = (resources.displayMetrics.density * NAV_BAR_GAP_MIN_DP + 0.5f).toInt()
        val maxGapPx = (resources.displayMetrics.density * NAV_BAR_GAP_MAX_DP + 0.5f).toInt()
        val navHeightPx = navBarRect?.height()
            ?: if (mainContainerRect != null && resolvedNavTop > 0) {
                (sh - mainContainerRect!!.bottom).coerceAtLeast(0)
            } else {
                0
            }
        val navGapPx = when {
            navHeightPx > 0 -> (navHeightPx * NAV_BAR_GAP_FRACTION).toInt().coerceIn(minGapPx, maxGapPx)
            else -> minGapPx
        }
        val resolvedBlockBottom = (if (resolvedNavTop > 0) resolvedNavTop - navGapPx else resolveNavBarTop(resolvedNavTop))
            .coerceIn(0, sh)
        val inReels = reelsHits >= 3 && reelsEngagementHits >= 1
        // storiesBarBottom > 0 is the key discriminator — stories only appear on the home feed.
        val inHome = (homeTabSelected || (homeChromeHits >= 1 && storiesBarBottom > 0)) && !inReels && !directTabSelected
        // Block search/explore grid unless user is actively typing (searchBarFocused).
        val inSearchExplore = (searchTabSelected || (navHits >= 2 && !homeTabSelected && !reelsTabSelected && !directTabSelected && !inHome && !searchBarFocused && homeChromeHits == 0)) &&
            !searchBarFocused && !inHome && !inReels && !directTabSelected
        val blockSurface = when {
            reelsTabSelected || inReels -> BlockSurface.REELS
            inHome -> BlockSurface.HOME
            inSearchExplore -> BlockSurface.SEARCH_EXPLORE
            else -> null
        }
        val navSelectionState = when {
            homeTabSelected || reelsTabSelected -> NavSelectionState.BLOCKED
            otherTabSelected || directTabSelected || searchTabSelected -> NavSelectionState.UNBLOCKED
            else -> NavSelectionState.UNKNOWN
        }
        val preferredTop = mainContainerRect?.top
            ?: actionBarRect?.bottom
            ?: if (blockSurface == BlockSurface.HOME && topChromeBottom > 0) topChromeBottom else null
            ?: bestContentRect?.top
        val blockTop = preferredTop?.coerceAtLeast(0) ?: 0
        // Block top derivation:
        // HOME: when stories are visible, start just below them; when scrolled past stories,
        //       start just below the action bar so no feed content leaks through.
        // SEARCH_EXPLORE: start just below the search bar (actionBarRect.bottom).
        val gapPx = (4 * resources.displayMetrics.density + 0.5f).toInt()
        val adjustedBlockTop = when (blockSurface) {
            BlockSurface.HOME -> {
                val actionBarBottom = actionBarRect?.bottom ?: blockTop
                // Cap storiesBarBottom: the "reels tray container" includes the first feed post.
                // Stories circles + names are at most 130dp below the action bar.
                val storiesCap = (actionBarBottom + (130 * resources.displayMetrics.density + 0.5f).toInt())
                    .coerceAtMost((sh * 0.32f).toInt())
                val effectiveStoriesBottom = if (storiesBarBottom in (actionBarBottom + 1)..storiesCap) {
                    storiesBarBottom
                } else if (storiesBarBottom > storiesCap) {
                    storiesCap
                } else {
                    0
                }
                if (effectiveStoriesBottom > actionBarBottom) {
                    // Stories visible — block starts just below the stories strip
                    (effectiveStoriesBottom + gapPx).coerceIn(0, sh)
                } else {
                    // Stories scrolled off-screen — block starts just below the action bar
                    (actionBarBottom + gapPx).coerceIn(0, sh)
                }
            }
            BlockSurface.SEARCH_EXPLORE -> {
                val searchBarBottom = actionBarRect?.bottom ?: blockTop
                (searchBarBottom + gapPx).coerceIn(0, sh)
            }
            else -> blockTop
        }
        val blockRect = if (blockSurface == null || resolvedBlockBottom <= adjustedBlockTop) {
            null
        } else {
            val left = mainContainerRect?.left ?: 0
            val right = mainContainerRect?.right ?: sw
            Rect(left.coerceIn(0, sw), adjustedBlockTop, right.coerceIn(0, sw), resolvedBlockBottom)
        }

        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "scan surface=$blockSurface homeSelected=$homeTabSelected reelsSelected=$reelsTabSelected " +
                "searchSelected=$searchTabSelected searchFocused=$searchBarFocused " +
                "reelsHits=$reelsHits engageHits=$reelsEngagementHits chromHits=$homeChromeHits " +
                "topChromeBottom=$topChromeBottom storiesBottom=$storiesBarBottom directTab=$directTabSelected " +
                "navHits=$navHits navTop=$resolvedNavTop navHeight=$navHeightPx navGap=$navGapPx " +
                "blockRect=$blockRect bestContent=$bestContentRect " +
                "mainContainer=$mainContainerRect navBar=$navBarRect actionBar=$actionBarRect navSelection=$navSelectionState"
        )

        return ScanResult(
            blockSurface = blockSurface,
            navBarTop = resolvedNavTop,
            blockRect = blockRect,
            navSelectionState = navSelectionState,
            directTabSelected = directTabSelected
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
            val blockSurface = scan.blockSurface
            val blockRect = scan.blockRect
            val navSelectionState = scan.navSelectionState

            // Hide everything while in DMs — no block, no reels icon.
            if (scan.directTabSelected) {
                hideOverlay()
                return
            }

            if (blockSurface != null && blockRect != null) {
                lastStableBlockRect = Rect(blockRect)
                lastStableBlockMs = System.currentTimeMillis()
                if (!feedBlockActive) {
                    feedBlockActive = true
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_BLOCK_REGION
                        putExtra("x", blockRect.left)
                        putExtra("y", blockRect.top)
                        putExtra("w", blockRect.width())
                        putExtra("h", blockRect.height())
                    })
                } else {
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_BLOCK_REGION
                        putExtra("x", blockRect.left)
                        putExtra("y", blockRect.top)
                        putExtra("w", blockRect.width())
                        putExtra("h", blockRect.height())
                    })
                }
                return
            }

            val now = System.currentTimeMillis()
            if (
                feedBlockActive &&
                navSelectionState != NavSelectionState.UNBLOCKED &&
                lastStableBlockRect != null &&
                now - lastStableBlockMs <= BLOCK_STABILITY_GRACE_MS
            ) {
                val stableRect = lastStableBlockRect!!
                startService(Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_BLOCK_REGION
                    putExtra("x", stableRect.left)
                    putExtra("y", stableRect.top)
                    putExtra("w", stableRect.width())
                    putExtra("h", stableRect.height())
                })
                return
            }

            if (feedBlockActive) feedBlockActive = false
            lastStableBlockRect = null

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

        var idMatchNode: AccessibilityNodeInfo? = null
        var descMatchNode: AccessibilityNodeInfo? = null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            val inNavBar = nodeRect.top >= navThreshold
            val narrowEnough = nodeRect.width() in 1..maxTabWidth

            if (inNavBar && narrowEnough && node.isClickable) {
                // ID match is language-independent — highest priority.
                if (idMatchNode == null && REELS_TAB_IDS.any { viewId.contains(it) }) {
                    idMatchNode = node
                    for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                    continue
                }
                // Description match — English fallback.
                if (descMatchNode == null &&
                    (desc.equals("Reels", ignoreCase = true) || desc.equals("Reels tab", ignoreCase = true))
                ) {
                    descMatchNode = node
                    for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                    continue
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }

        return if (idMatchNode != null) {
            descMatchNode?.recycle()
            idMatchNode
        } else {
            descMatchNode
        }
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
