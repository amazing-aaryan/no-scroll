package com.noscroll

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.noscroll.BuildConfig
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    private var pendingConfirm: Runnable? = null
    private var pendingContentCheck: Runnable? = null
    private var pendingUpdateTrigger: String = "unknown"
    private var pendingUpdateEventMs: Long = 0L

    private var feedBlockActive = false
    private var lastStableBlockRect: Rect? = null
    private var lastStableBlockMs: Long = 0L
    private var lastStoryTapMs = 0L
    private var dmSharedMediaSessionActive = false
    private var dmSharedMediaAllowedUntilMs = 0L
    private var lastBlockedScrollRollbackMs = 0L
    private var lastOverlayCommandKey: String? = null
    private var lastOverlayCommandMs: Long = 0L
    // Set on every window-state-change while block is active. Suppresses re-show for
    // PROACTIVE_HIDE_WINDOW_MS so the story viewer has time to land in the a11y tree.
    private var proactiveHideMs = 0L

    // Polling — runs every POLL_INTERVAL_MS while Instagram is in the foreground.
    private var pollingActive = false
    private val pollRunnable = object : Runnable {
        override fun run() {
            findAndUpdateOverlay("poll", SystemClock.uptimeMillis())
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
        private const val WINDOW_RETRY_MS = 32L
        private const val CONFIRM_MS = 120L
        private const val CONTENT_DEBOUNCE_MS = 0L
        private const val BLOCK_STABILITY_GRACE_MS = 250L
        private const val STORY_VIEWER_WINDOW_MS = 30_000L
        private const val DM_SHARED_MEDIA_WINDOW_MS = 120_000L
        private const val BLOCKED_SCROLL_ROLLBACK_COOLDOWN_MS = 450L
        private const val DUPLICATE_OVERLAY_COMMAND_WINDOW_MS = 100L
        private const val PROACTIVE_HIDE_WINDOW_MS = 80L   // suppress re-show after proactive hide
        private const val POLL_INTERVAL_MS = 500L

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
        private val DIRECT_THREAD_IDS = setOf(
            "thread_fragment_container", "direct_thread_header", "message_thread_container",
            "message_list", "row_thread_composer_container", "direct_thread_content_below_action_bar"
        )
        private val DIRECT_SHARED_MEDIA_IDS = setOf(
            "reel_share_item_view", "message_content_portrait_xma_container",
            "media_constraint_layout", "media_container", "caption_container",
            "caption_title", "direct_reel_share_legibility_gradient_footer",
            "message_content_generic_xma_container", "xma_bubble_container",
            "message_content_horizontal_placeholder_container"
        )
        private val PROFILE_PAGE_IDS = setOf(
            "profile_action_bar", "action_bar_username_container", "row_profile_header",
            "profile_header_container", "profile_header_metrics", "profile_header_post_count",
            "profile_header_followers", "profile_header_following", "profile_tab_layout",
            "profile_media_grid"
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
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
                    val source = event.source
                    val desc = source?.contentDescription?.toString()?.lowercase() ?: ""
                    // Story tray items: "X's story, 2 of 27, Unseen/Seen." — also catch
                    // variants that omit the "of N" counter (older IG versions, Lite).
                    if (desc.contains("story")) {
                        lastStoryTapMs = System.currentTimeMillis()
                        cancelAll()
                        hideOverlay()
                    }
                    val sharedMediaClick = source?.let {
                        val inDirectThread = it.isInsideNodeWithId(DIRECT_THREAD_IDS)
                        val hasSharedMedia = it.isInsideNodeWithId(DIRECT_SHARED_MEDIA_IDS) ||
                            it.containsDescendantWithId(DIRECT_SHARED_MEDIA_IDS)
                        inDirectThread && hasSharedMedia
                    } == true
                    if (sharedMediaClick) {
                        dmSharedMediaSessionActive = true
                        dmSharedMediaAllowedUntilMs = System.currentTimeMillis() + DM_SHARED_MEDIA_WINDOW_MS
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "dm shared media click armed until=$dmSharedMediaAllowedUntilMs " +
                                    "sourceId=${source?.viewIdResourceName} desc=$desc"
                            )
                        }
                        hideOverlay()
                    } else if (NAV_TAB_KEYWORDS.any { desc.contains(it) }) {
                        dmSharedMediaSessionActive = false
                        dmSharedMediaAllowedUntilMs = 0L
                    }
                    source?.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == INSTAGRAM_PKG || pkg == INSTAGRAM_LITE_PKG) {
                    cancelPendingConfirm()
                    val hadFeedBlockActive = feedBlockActive
                    if (feedBlockActive) {
                        // Hide before the a11y tree reflects the new surface — story viewer
                        // keeps the full home-feed tree accessible, so scan-based detection
                        // can lag. Suppress re-show for PROACTIVE_HIDE_WINDOW_MS.
                        proactiveHideMs = System.currentTimeMillis()
                        feedBlockActive = false
                        lastStableBlockRect = null
                        hideOverlay()
                    }
                    findAndUpdateOverlay("window-state", event.eventTime)
                    val retryDelayMs = if (hadFeedBlockActive) {
                        PROACTIVE_HIDE_WINDOW_MS + WINDOW_RETRY_MS
                    } else {
                        WINDOW_RETRY_MS
                    }
                    scheduleUpdate(retryDelayMs, "window-state-retry", event.eventTime)
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
                    if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && feedBlockActive) {
                        maybeRollbackBlockedScroll(event)
                    }
                    // Polling is only a backup; content and scroll changes scan on the next
                    // main-loop turn so surface switches are not held behind a timer.
                    cancelPendingContentCheck()
                    val contentEventTimeMs = event.eventTime
                    val contentTrigger =
                        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) "view-scrolled" else "content-changed"
                    pendingContentCheck = Runnable {
                        pendingContentCheck = null
                        findAndUpdateOverlay(contentTrigger, contentEventTimeMs)
                    }
                    if (CONTENT_DEBOUNCE_MS <= 0L) {
                        handler.post(pendingContentCheck!!)
                    } else {
                        handler.postDelayed(pendingContentCheck!!, CONTENT_DEBOUNCE_MS)
                    }
                    startPolling()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Settings.canDrawOverlays(this)) {
            startPolling()
            findAndUpdateOverlay("service-connected", SystemClock.uptimeMillis())
        }
    }

    private fun scheduleUpdate(delayMs: Long, trigger: String, eventTimeMs: Long) {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        pendingUpdateTrigger = trigger
        pendingUpdateEventMs = eventTimeMs
        pendingUpdate = Runnable {
            pendingUpdate = null
            findAndUpdateOverlay(pendingUpdateTrigger, pendingUpdateEventMs)
        }
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
            findAndUpdateOverlay("confirm-instagram", SystemClock.uptimeMillis())
        } else {
            stopOverlay()
        }
    }

    private enum class NavSelectionState {
        BLOCKED,
        UNBLOCKED,
        UNKNOWN
    }

    private data class ScanResult(
        val blockSurface: InstagramBlockSurface?,
        val navBarTop: Int,
        val blockRect: Rect?,
        val reelsTabRect: Rect?,
        val navSelectionState: NavSelectionState,
        val directTabSelected: Boolean,
        val directThreadActive: Boolean,
        val directSharedMediaPresent: Boolean,
        val directSharedViewerActive: Boolean,
        val profilePageActive: Boolean,
        val searchBarFocused: Boolean,
        val homeChromeHits: Int,
        val storiesBarBottom: Int,
        val storyViewerActive: Boolean
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
        var directThreadActive = false
        var directSharedMediaPresent = false
        var directSharedViewerActive = false
        var searchTabSelected = false
        var searchBarFocused = false   // true when user is actively typing
        var profilePageActive = false
        var storiesTrayPresent = false // reels_tray_container ID found — reliable home indicator
        var swipeNavigationContainerPresent = false
        var storyViewerActive = false  // swipe_navigation_container without bottom-nav chrome
        var homeChromeHits = 0
        var bestContentRect: Rect? = null
        var bestContentArea = 0
        var mainContainerRect: Rect? = null
        var reelsContainerRect: Rect? = null
        var reelsContainerScore = 0
        var homeContainerRect: Rect? = null
        var homeContainerScore = 0
        var searchContainerRect: Rect? = null
        var searchContainerScore = 0
        var navBarRect: Rect? = null
        var actionBarRect: Rect? = null
        var reelsTabRect: Rect? = null
        var storiesBarBottom = 0  // detected bottom of the stories strip on Home

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        while (queue.isNotEmpty()) {
            val node  = queue.removeFirst()
            val lower = node.contentDescription?.toString()?.lowercase() ?: ""
            val textLower = node.text?.toString()?.lowercase() ?: ""
            val viewIdLower = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""
            val rect  = Rect()
            node.getBoundsInScreen(rect)

            // Detect stories tray container regardless of bounds — it remains in the
            // accessibility tree even when scrolled off screen, making it a reliable
            // "we are on the Instagram home feed" signal.
            if (viewIdLower.contains("reels_tray_container") || viewIdLower == "com.instagram.android:id/reels_tray") {
                storiesTrayPresent = true
            }
            // Story viewer overlay — swipe_navigation_container is the full-screen root
            // of the story viewer (bounds [0,0][sw,sh]). It is present in the tree even
            // when main_feed_action_bar and reels_tray_container are also accessible,
            // making it the only reliable "we are inside the story viewer" signal.
            if (viewIdLower.contains("swipe_navigation_container")) {
                swipeNavigationContainerPresent = true
            }
            if (DIRECT_THREAD_IDS.any { viewIdLower.contains(it) }) {
                directThreadActive = true
            }
            if (DIRECT_SHARED_MEDIA_IDS.any { viewIdLower.contains(it) }) {
                directSharedMediaPresent = true
            }
            if (PROFILE_PAGE_IDS.any { viewIdLower.contains(it) }) {
                profilePageActive = true
            }
            if (viewIdLower.contains("reply_bar_container_and_title") ||
                viewIdLower.contains("reel_viewer_message_composer") ||
                viewIdLower.contains("reply_bar_edittext") ||
                viewIdLower.contains("sender_username_or_fullname")) {
                directSharedViewerActive = true
            }

            if (!rect.isEmpty) {
                if (NAV_BAR_CONTAINER_IDS.any { viewIdLower.contains(it) } && !viewIdLower.contains("shadow")) {
                    navBarRect = Rect(rect)
                }
                // Only track action bar nodes within the top 25% — prevents action_bar_root
                // (full-screen container) from inflating actionBarRect.bottom.
                if ((viewIdLower.contains("action_bar") || viewIdLower.contains("main_feed_action_bar")) &&
                    rect.bottom < (sh * 0.25f).toInt()) {
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
                val parent = node.parent
                val parentIdLower = parent?.viewIdResourceName?.lowercase() ?: ""
                parent?.recycle()
                val area = rect.width() * rect.height()
                val largeSurfaceRect = rect.width() >= (sw * 0.70f).toInt() &&
                    rect.height() >= (sh * 0.45f).toInt() &&
                    rect.bottom > (sh * 0.75f).toInt()
                val scrollableContent = node.isScrollable ||
                    className.contains("RecyclerView") ||
                    className.contains("ViewPager") ||
                    className.contains("ListView")
                val containerIdentity = "$viewIdLower $parentIdLower $className"
                val isRootChromeContainer =
                    viewIdLower.contains("action_bar_root") ||
                        viewIdLower == "android:id/content" ||
                        viewIdLower.endsWith(":id/content")

                if (!isRootChromeContainer && largeSurfaceRect) {
                    val reelsScore = when {
                        containerIdentity.contains("clips_viewer_view_pager") -> 7_000_000 + area
                        containerIdentity.contains("clips_swipe_refresh_container") -> 6_000_000 + area
                        containerIdentity.contains("root_clips_layout") -> 5_000_000 + area
                        containerIdentity.contains("clips") && scrollableContent -> 4_000_000 + area
                        containerIdentity.contains("layout_container_main") -> 3_000_000 + area
                        containerIdentity.contains("swipeable_tab_view_pager") -> 2_000_000 + area
                        else -> 0
                    }
                    if (reelsScore > reelsContainerScore) {
                        reelsContainerScore = reelsScore
                        reelsContainerRect = Rect(rect)
                    }

                    val homeScore = when {
                        containerIdentity.contains("feed") && scrollableContent -> 7_000_000 + area
                        containerIdentity.contains("timeline") && scrollableContent -> 6_000_000 + area
                        containerIdentity.contains("recycler_view") && scrollableContent -> 5_000_000 + area
                        containerIdentity.contains("refreshable_container") -> 4_000_000 + area
                        containerIdentity.contains("layout_container_main") -> 3_000_000 + area
                        containerIdentity.contains("swipeable_tab_view_pager") -> 2_000_000 + area
                        else -> 0
                    }
                    if (homeScore > homeContainerScore) {
                        homeContainerScore = homeScore
                        homeContainerRect = Rect(rect)
                    }

                    val searchScore = when {
                        containerIdentity.contains("recycler_view") && scrollableContent -> 7_000_000 + area
                        containerIdentity.contains("explore") && scrollableContent -> 6_000_000 + area
                        containerIdentity.contains("grid") && scrollableContent -> 5_000_000 + area
                        containerIdentity.contains("layout_container_swipeable") -> 4_000_000 + area
                        containerIdentity.contains("swipeable_tab_view_pager") -> 2_000_000 + area
                        else -> 0
                    }
                    if (searchScore > searchContainerScore) {
                        searchContainerScore = searchScore
                        searchContainerRect = Rect(rect)
                    }
                }

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

                // ② Stories strip — use individual story circle nodes (narrow, < 28% screen width).
                //    The reels_tray_container extends to y=612 which includes the first feed post;
                //    individual story items end at y≈542 (correct stories-only boundary).
                //    Width filter also excludes story VIEWER content which is full-screen (1080px).
                //    rect.top >= 0 ensures off-screen (scrolled past) stories are excluded.
                val looksLikeStory = (viewIdLower.contains("reels_tray") ||
                    lower.contains("reels tray") ||
                    lower.contains("story") || lower.contains("stories") ||
                    viewIdLower.contains("story") || viewIdLower.contains("stories"))
                val isNarrowStoryItem = rect.width() in 1..(sw * 0.28f).toInt()
                if (looksLikeStory && isNarrowStoryItem && rect.top >= 0 && rect.top < (sh * 0.45f) && rect.bottom > storiesBarBottom) {
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
                    if (isReelsTabId ||
                        lower.contains("reels") || lower.contains("clips") ||
                        textLower.contains("reels") || textLower.contains("clips") ||
                        viewIdLower.contains("reels") || viewIdLower.contains("clips")) {
                        reelsTabRect = Rect(rect)
                    }
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
                val selectedNavChild = rect.top >= navZoneTop &&
                    rect.width() <= (sw * 0.25f).toInt() &&
                    rect.height() <= (sh * 0.10f).toInt() &&
                    (viewIdLower.contains("tab") ||
                        parentIdLower.contains("tab_bar") ||
                        parentIdLower.contains("bottom_nav") ||
                        parentIdLower.contains("ig_bottom_navbar") ||
                        parentIdLower.endsWith("_tab"))
                if (selectedNavChild && (node.isSelected || node.isChecked)) {
                    val navIdentity = "$viewIdLower $parentIdLower $lower $textLower"
                    when {
                        HOME_TAB_IDS.any { navIdentity.contains(it) } ||
                            navIdentity.contains("home") ||
                            navIdentity.contains("feed_tab") ->
                            homeTabSelected = true
                        REELS_TAB_IDS.any { navIdentity.contains(it) } ||
                            navIdentity.contains("reels") ||
                            navIdentity.contains("clips") ->
                            reelsTabSelected = true
                        MESSAGES_TAB_IDS.any { navIdentity.contains(it) } ||
                            navIdentity.contains("message") ||
                            navIdentity.contains("direct") ->
                            directTabSelected = true
                        navIdentity.contains("search") ||
                            navIdentity.contains("explore") ->
                            searchTabSelected = true
                        else -> otherTabSelected = true
                    }
                }

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
        val mainContainerBottom = mainContainerRect?.bottom ?: -1
        val mainContainerLooksLikeContent = mainContainerBottom in ((sh * 0.55f).toInt() until sh)
        val resolvedNavTop = when {
            navBarRect != null -> navBarRect!!.top
            heuristicNavTop > 0 -> heuristicNavTop
            mainContainerLooksLikeContent -> mainContainerBottom
            else -> resolveNavBarTop(-1)
        }.coerceIn(0, sh)
        val navHeightPx = when {
            navBarRect != null -> navBarRect!!.height()
            resolvedNavTop in 1 until sh -> sh - resolvedNavTop
            else -> 0
        }
        val navGapPx = 0
        val resolvedBlockBottom = resolvedNavTop.coerceIn(0, sh)
        val inReels = reelsHits >= 3 && reelsEngagementHits >= 1
        // storiesTrayPresent: reels_tray_container ID persists in the accessibility tree even
        // when scrolled off screen, so it reliably identifies the home feed regardless of
        // scroll position. It is absent on Search, Profile, DMs, and Reels tab.
        val homeChromeFeedPresent = homeChromeHits >= 2 && navHits >= 2
        val inHome = (homeTabSelected || storiesTrayPresent || homeChromeFeedPresent) &&
            !inReels &&
            !directTabSelected &&
            !directThreadActive &&
            !searchTabSelected
        // Block search/explore grid unless user is actively typing (searchBarFocused).
        val inSearchExplore = (searchTabSelected || (navHits >= 2 && !homeTabSelected && !reelsTabSelected && !directTabSelected && !inHome && !searchBarFocused && homeChromeHits == 0)) &&
            !searchBarFocused && !profilePageActive && !inHome && !inReels && !directTabSelected
        storyViewerActive = swipeNavigationContainerPresent &&
            !directSharedViewerActive &&
            !inSearchExplore &&
            !inReels
        val blockSurface = when {
            reelsTabSelected || inReels -> InstagramBlockSurface.REELS
            inHome -> InstagramBlockSurface.HOME
            inSearchExplore -> InstagramBlockSurface.SEARCH_EXPLORE
            else -> null
        }
        val navSelectionState = when {
            homeTabSelected || reelsTabSelected -> NavSelectionState.BLOCKED
            otherTabSelected || directTabSelected || searchTabSelected -> NavSelectionState.UNBLOCKED
            else -> NavSelectionState.UNKNOWN
        }
        // Block top derivation:
        // HOME: when stories are visible, start just below them; when scrolled past stories,
        //       start just below the action bar so no feed content leaks through.
        // SEARCH_EXPLORE: start just below the search bar (actionBarRect.bottom).
        val gapPx = (4 * resources.displayMetrics.density + 0.5f).toInt()
        val adjustedBlockTop = when (blockSurface) {
            InstagramBlockSurface.HOME -> {
                val actionBarBottom = actionBarRect?.bottom ?: 0
                when {
                    storiesBarBottom > 0 ->
                        // Stories visible — block starts just below the story items
                        (storiesBarBottom + gapPx).coerceIn(0, sh)
                    actionBarBottom > 0 ->
                        // Stories off-screen but action bar still visible — block just below it
                        (actionBarBottom + gapPx).coerceIn(0, sh)
                    else ->
                        // Both scrolled off — block from content area top (y≈91, just below status bar)
                        ((mainContainerRect?.top ?: 91) + gapPx).coerceIn(0, sh)
                }
            }
            InstagramBlockSurface.SEARCH_EXPLORE -> {
                val searchBarFallbackBottom = (sh * 0.12f).toInt()
                val searchBarBottom = maxOf(actionBarRect?.bottom ?: 0, searchBarFallbackBottom, 0)
                (searchBarBottom + gapPx).coerceIn(0, sh)
            }
            InstagramBlockSurface.REELS -> 0
            else -> 0
        }
        val blockContainerRect = when (blockSurface) {
            InstagramBlockSurface.REELS -> reelsContainerRect ?: mainContainerRect ?: bestContentRect
            InstagramBlockSurface.HOME -> homeContainerRect ?: mainContainerRect ?: bestContentRect
            InstagramBlockSurface.SEARCH_EXPLORE -> searchContainerRect ?: bestContentRect ?: mainContainerRect
            null -> null
        }
        val blockBounds = InstagramBlockPolicy.blockBounds(
            surface = blockSurface,
            screenWidth = sw,
            screenHeight = sh,
            containerBounds = blockContainerRect?.let { IntBounds(it.left, it.top, it.right, it.bottom) }
        )
        val blockRect = blockBounds?.let { Rect(it.left, it.top, it.right, it.bottom) }

        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "scan surface=$blockSurface homeSelected=$homeTabSelected reelsSelected=$reelsTabSelected " +
                "searchSelected=$searchTabSelected searchFocused=$searchBarFocused " +
                "profilePage=$profilePageActive " +
                "reelsHits=$reelsHits engageHits=$reelsEngagementHits chromHits=$homeChromeHits " +
                "topChromeBottom=$topChromeBottom storiesBottom=$storiesBarBottom directTab=$directTabSelected " +
                "directThread=$directThreadActive directShared=$directSharedMediaPresent " +
                "directViewer=$directSharedViewerActive " +
                "navHits=$navHits navTop=$resolvedNavTop navHeight=$navHeightPx navGap=$navGapPx " +
                "blockRect=$blockRect blockContainer=$blockContainerRect " +
                "reelsContainer=$reelsContainerRect/$reelsContainerScore " +
                "homeContainer=$homeContainerRect/$homeContainerScore " +
                "searchContainer=$searchContainerRect/$searchContainerScore " +
                "reelsTab=$reelsTabRect bestContent=$bestContentRect " +
                "mainContainer=$mainContainerRect navBar=$navBarRect actionBar=$actionBarRect navSelection=$navSelectionState"
        )

        return ScanResult(
            blockSurface = blockSurface,
            navBarTop = resolvedNavTop,
            blockRect = blockRect,
            reelsTabRect = reelsTabRect,
            navSelectionState = navSelectionState,
            directTabSelected = directTabSelected,
            directThreadActive = directThreadActive,
            directSharedMediaPresent = directSharedMediaPresent,
            directSharedViewerActive = directSharedViewerActive,
            profilePageActive = profilePageActive,
            searchBarFocused = searchBarFocused,
            homeChromeHits = homeChromeHits,
            storiesBarBottom = storiesBarBottom,
            storyViewerActive = storyViewerActive
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

    private fun findAndUpdateOverlay(trigger: String, eventTimeMs: Long) {
        val startedAtMs = SystemClock.uptimeMillis()
        val now = System.currentTimeMillis()
        if (lastStoryTapMs > 0 && now - lastStoryTapMs < STORY_VIEWER_WINDOW_MS) {
            logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-story-tap-window", null)
            hideOverlay()
            return
        }
        if (proactiveHideMs > 0 && now - proactiveHideMs < PROACTIVE_HIDE_WINDOW_MS) {
            logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-proactive-window", null)
            hideOverlay()
            return
        }
        val root = rootInActiveWindow ?: run {
            logOverlayDecision(trigger, eventTimeMs, startedAtMs, "no-root", null)
            return
        }
        try {
            val scanStartedAtMs = SystemClock.uptimeMillis()
            val scan = scanInstagramTree(root)
            val scanMs = SystemClock.uptimeMillis() - scanStartedAtMs
            val blockSurface = scan.blockSurface
            val blockRect = scan.blockRect
            val navSelectionState = scan.navSelectionState

            // Story viewer overlays the home feed but keeps it accessible in the tree.
            // swipe_navigation_container is the definitive story viewer root — hide immediately.
            if (scan.storyViewerActive) {
                if (feedBlockActive) feedBlockActive = false
                lastStableBlockRect = null
                logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-story-viewer scanMs=$scanMs", null)
                hideOverlay()
                return
            }

            if (dmSharedMediaAllowedUntilMs > 0L && now > dmSharedMediaAllowedUntilMs) {
                dmSharedMediaSessionActive = false
                dmSharedMediaAllowedUntilMs = 0L
            }

            // Hide everything while in DMs — no block, no reels icon. Keep a freshly armed
            // shared-media session alive while Instagram is navigating from the thread to viewer.
            if (dmSharedMediaSessionActive &&
                now <= dmSharedMediaAllowedUntilMs &&
                !scan.directSharedViewerActive &&
                !scan.directThreadActive &&
                !scan.directTabSelected &&
                scan.profilePageActive) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "dm shared media containment back from profile")
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-dm-containment scanMs=$scanMs", null)
                hideOverlay()
                return
            }

            if (scan.directTabSelected || scan.directThreadActive) {
                if (scan.directTabSelected && !scan.directThreadActive) {
                    dmSharedMediaSessionActive = false
                    dmSharedMediaAllowedUntilMs = 0L
                }
                logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-direct scanMs=$scanMs", null)
                hideOverlay()
                return
            }

            if (blockSurface != null && blockRect != null) {
                if (scan.directSharedViewerActive) {
                    dmSharedMediaSessionActive = true
                    dmSharedMediaAllowedUntilMs = now + DM_SHARED_MEDIA_WINDOW_MS
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "dm shared media viewer detected surface=$blockSurface rect=$blockRect")
                    }
                }
                val useDmTouchBlock = scan.directSharedViewerActive &&
                    (dmSharedMediaSessionActive || now <= dmSharedMediaAllowedUntilMs)
                if (!scan.directSharedViewerActive && dmSharedMediaSessionActive) {
                    dmSharedMediaSessionActive = false
                    dmSharedMediaAllowedUntilMs = 0L
                }
                feedBlockActive = true
                lastStableBlockRect = Rect(blockRect)
                lastStableBlockMs = now
                if (useDmTouchBlock) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "dm shared media touch block active rect=$blockRect until=$dmSharedMediaAllowedUntilMs")
                    logOverlayDecision(trigger, eventTimeMs, startedAtMs, "touch-block-$blockSurface scanMs=$scanMs", blockRect)
                    sendTouchBlockOverlay(blockRect)
                } else {
                    logOverlayDecision(trigger, eventTimeMs, startedAtMs, "block-$blockSurface scanMs=$scanMs", blockRect)
                    sendBlockOverlay(blockRect)
                }
                return
            }

            if (scan.reelsTabRect != null &&
                scan.homeChromeHits > 0 &&
                navSelectionState != NavSelectionState.UNBLOCKED) {
                if (feedBlockActive) feedBlockActive = false
                lastStableBlockRect = null
                logOverlayDecision(trigger, eventTimeMs, startedAtMs, "book scanMs=$scanMs", scan.reelsTabRect)
                sendBookOverlay(scan.reelsTabRect)
                return
            }

            if (
                feedBlockActive &&
                navSelectionState != NavSelectionState.UNBLOCKED &&
                !scan.searchBarFocused &&
                scan.homeChromeHits > 0 &&  // story viewer has no home action bar → drop immediately
                lastStableBlockRect != null &&
                now - lastStableBlockMs <= BLOCK_STABILITY_GRACE_MS
            ) {
                val stableRect = lastStableBlockRect!!
                logOverlayDecision(trigger, eventTimeMs, startedAtMs, "stable-block scanMs=$scanMs", stableRect)
                sendBlockOverlay(stableRect)
                return
            }

            if (feedBlockActive) feedBlockActive = false
            lastStableBlockRect = null

            logOverlayDecision(trigger, eventTimeMs, startedAtMs, "hide-no-surface scanMs=$scanMs", null)
            hideOverlay()
        } finally {
            root.recycle()
        }
    }

    private fun logOverlayDecision(
        trigger: String,
        eventTimeMs: Long,
        startedAtMs: Long,
        decision: String,
        rect: Rect?
    ) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.uptimeMillis()
        val eventLagMs = (startedAtMs - eventTimeMs).coerceAtLeast(0L)
        val totalMs = nowMs - startedAtMs
        Log.d(TAG, "decision trigger=$trigger eventLagMs=$eventLagMs totalMs=$totalMs decision=$decision rect=$rect")
    }

    private fun maybeRollbackBlockedScroll(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastBlockedScrollRollbackMs < BLOCKED_SCROLL_ROLLBACK_COOLDOWN_MS) return
        if (!isForwardScroll(event)) return
        val root = rootInActiveWindow ?: return
        try {
            val scan = scanInstagramTree(root)
            val isDmSharedViewer = scan.directSharedViewerActive &&
                (dmSharedMediaSessionActive || now <= dmSharedMediaAllowedUntilMs)
            if (scan.blockSurface != null && !scan.searchBarFocused && !isDmSharedViewer) {
                val target = findBlockedScrollableNode(root, scan.blockSurface)
                if (target != null) {
                    lastBlockedScrollRollbackMs = now
                    val rolledBack = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "blocked scroll rollback surface=${scan.blockSurface} " +
                                "success=$rolledBack target=${target.viewIdResourceName}"
                        )
                    }
                    target.recycle()
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun isForwardScroll(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when {
                event.scrollDeltaY > 0 -> return true
                event.scrollDeltaY < 0 -> return false
            }
        }
        if (event.fromIndex >= 0 && event.toIndex >= 0 && event.toIndex != event.fromIndex) {
            return event.toIndex > event.fromIndex
        }
        if (event.scrollY >= 0 && event.maxScrollY > 0) {
            return event.scrollY > 0
        }
        return true
    }

    private fun findBlockedScrollableNode(
        root: AccessibilityNodeInfo,
        blockSurface: InstagramBlockSurface
    ): AccessibilityNodeInfo? {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it) }

        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewIdLower = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""
            val rect = Rect()
            node.getBoundsInScreen(rect)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }

            val area = rect.width() * rect.height()
            val isLarge = rect.width() >= (sw * 0.70f).toInt() &&
                rect.height() >= (sh * 0.50f).toInt()
            val isScrollablePager = node.isScrollable &&
                (className.contains("ViewPager") || className.contains("RecyclerView"))
            val score = when {
                blockSurface == InstagramBlockSurface.REELS &&
                    viewIdLower.contains("clips_viewer_view_pager") &&
                    node.isScrollable -> 4_000_000 + area
                blockSurface == InstagramBlockSurface.REELS &&
                    viewIdLower.contains("clips_swipe_refresh_container") &&
                    node.isScrollable -> 3_000_000 + area
                blockSurface == InstagramBlockSurface.REELS &&
                    viewIdLower.contains("root_clips_layout") &&
                    isScrollablePager -> 2_000_000 + area
                blockSurface == InstagramBlockSurface.SEARCH_EXPLORE &&
                    viewIdLower.contains("recycler_view") &&
                    isScrollablePager -> 4_000_000 + area
                blockSurface == InstagramBlockSurface.SEARCH_EXPLORE &&
                    (viewIdLower.contains("explore") || viewIdLower.contains("grid")) &&
                    isScrollablePager -> 3_000_000 + area
                blockSurface == InstagramBlockSurface.HOME &&
                    (viewIdLower.contains("feed") || viewIdLower.contains("timeline")) &&
                    isScrollablePager -> 4_000_000 + area
                isLarge && isScrollablePager && !viewIdLower.contains("swipeable_tab_view_pager") -> 1_000_000 + area
                else -> 0
            }
            val isLargeScrollable = node.isScrollable &&
                rect.width() >= (sw * 0.70f).toInt() &&
                rect.height() >= (sh * 0.50f).toInt() &&
                (className.contains("ViewPager") || className.contains("RecyclerView"))

            if (score > 0 || isLargeScrollable) {
                val candidateScore = if (score > 0) score else area
                if (candidateScore > bestScore) {
                    best?.recycle()
                    best = node
                    bestScore = candidateScore
                } else {
                    node.recycle()
                }
            } else {
                node.recycle()
            }
        }
        return best
    }

    private fun sendBlockOverlay(blockRect: Rect) {
        sendOverlayIntent(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_BLOCK_REGION
            putExtra("x", blockRect.left)
            putExtra("y", blockRect.top)
            putExtra("w", blockRect.width())
            putExtra("h", blockRect.height())
        })
    }

    private fun sendTouchBlockOverlay(blockRect: Rect) {
        sendOverlayIntent(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TOUCH_BLOCK_REGION
            putExtra("x", blockRect.left)
            putExtra("y", blockRect.top)
            putExtra("w", blockRect.width())
            putExtra("h", blockRect.height())
        })
    }

    private fun hideOverlay() {
        sendOverlayIntent(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        })
    }

    private fun sendBookOverlay(reelsTabRect: Rect) {
        val size = (48f * resources.displayMetrics.density + 0.5f).toInt()
        val x = (reelsTabRect.centerX() - size / 2).coerceAtLeast(0)
        val y = (reelsTabRect.centerY() - size / 2).coerceAtLeast(0)
        sendOverlayIntent(Intent(this, OverlayService::class.java).apply {
            putExtra("x", x)
            putExtra("y", y)
            putExtra("w", size)
            putExtra("h", size)
        })
    }

    private fun stopOverlay() {
        cancelAll()
        sendOverlayIntent(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        })
    }

    private fun sendOverlayIntent(intent: Intent) {
        val nowMs = SystemClock.uptimeMillis()
        val commandKey = overlayCommandKey(intent)
        if (commandKey == lastOverlayCommandKey &&
            nowMs - lastOverlayCommandMs < DUPLICATE_OVERLAY_COMMAND_WINDOW_MS) {
            return
        }
        lastOverlayCommandKey = commandKey
        lastOverlayCommandMs = nowMs
        runCatching { startService(intent) }
            .onFailure { error ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "overlay service command failed action=${intent.action}", error)
                }
                feedBlockActive = false
                lastStableBlockRect = null
                lastOverlayCommandKey = null
            }
    }

    private fun overlayCommandKey(intent: Intent): String {
        val action = intent.action ?: "BOOK"
        val x = intent.getIntExtra("x", Int.MIN_VALUE)
        val y = intent.getIntExtra("y", Int.MIN_VALUE)
        val w = intent.getIntExtra("w", Int.MIN_VALUE)
        val h = intent.getIntExtra("h", Int.MIN_VALUE)
        return "$action:$x:$y:$w:$h"
    }

    private fun AccessibilityNodeInfo.isInsideNodeWithId(idFragments: Set<String>): Boolean {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth < 8) {
            val viewId = current.viewIdResourceName?.lowercase() ?: ""
            if (idFragments.any { viewId.contains(it) }) {
                if (current !== this) current.recycle()
                return true
            }
            val parent = current.parent
            if (current !== this) current.recycle()
            current = parent
            depth++
        }
        current?.takeIf { it !== this }?.recycle()
        return false
    }

    private fun AccessibilityNodeInfo.containsDescendantWithId(
        idFragments: Set<String>,
        maxDepth: Int = 8
    ): Boolean {
        if (maxDepth <= 0) return false
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            try {
                val viewId = child.viewIdResourceName?.lowercase() ?: ""
                if (idFragments.any { viewId.contains(it) }) return true
                if (child.containsDescendantWithId(idFragments, maxDepth - 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    override fun onInterrupt() = stopOverlay()

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }
}
