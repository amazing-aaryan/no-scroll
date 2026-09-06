package com.noscroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.noscroll.redirect.AndroidBlockerDestination
import com.noscroll.redirect.DestinationLaunchResult
import androidx.core.app.NotificationCompat
import com.noscroll.tutorial.TutorialPrefs

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayMode: OverlayMode = OverlayMode.NONE

    private enum class OverlayMode {
        NONE,
        BOOK,
        BLOCK,
        TOUCH_BLOCK
    }

    companion object {
        private const val CHANNEL_ID = "noscroll_overlay"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.noscroll.STOP_OVERLAY"
        const val ACTION_HIDE = "com.noscroll.HIDE_OVERLAY"
        const val ACTION_FREEZE = "com.noscroll.FREEZE_OVERLAY"
        const val ACTION_UNFREEZE = "com.noscroll.UNFREEZE_OVERLAY"
        const val ACTION_BLOCK_REGION = "com.noscroll.BLOCK_REGION"
        const val ACTION_TOUCH_BLOCK_REGION = "com.noscroll.TOUCH_BLOCK_REGION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException on Android 12+ when system restarts the
            // service from the background without the accessibility service context.
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (windowManager == null) return START_NOT_STICKY // onCreate failed — don't restart
        if (intent == null) return START_STICKY // restarted by system after kill — wait for next real command
        if (intent.action == ACTION_BLOCK_REGION || intent.action == ACTION_TOUCH_BLOCK_REGION) {
            if (!Settings.canDrawOverlays(this)) {
                removeOverlayView()
                stopSelf()
                return START_NOT_STICKY
            }
            val x = intent.getIntExtra("x", 0)
            val y = intent.getIntExtra("y", 0)
            val w = intent.getIntExtra("w", resources.displayMetrics.widthPixels)
            val h = intent.getIntExtra("h", (resources.displayMetrics.heightPixels * 0.80f).toInt())
            showBlockRegion(x, y, w, h, visible = intent.action == ACTION_BLOCK_REGION)
            return START_STICKY
        }
        if (intent.action == ACTION_HIDE) {
            removeOverlayView()
            return START_STICKY
        }
        if (intent.action == ACTION_FREEZE) {
            setTouchable(false)
            return START_STICKY
        }
        if (intent.action == ACTION_UNFREEZE) {
            setTouchable(true)
            return START_STICKY
        }
        if (intent.action == ACTION_STOP) {
            removeOverlayView()
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val x = intent.getIntExtra("x", 0)
        val y = intent.getIntExtra("y", 0)
        val w = intent.getIntExtra("w", 120)
        val h = intent.getIntExtra("h", 120)
        val bgColor = intent.getIntExtra("bgColor", Color.BLACK)
        updateOverlay(x, y, w, h, bgColor)
        return START_STICKY
    }

    private fun applyDestinationAppearance(view: View?) {
        view ?: return
        val launcher = AndroidBlockerDestination(this)
        val destination = launcher.preferences.read()
        // The service receives frequent geometry updates. Resolve labels/icons only when
        // the selection changes or a new overlay is created, not on every scan.
        val key = destination.storedPackage.orEmpty()
        if (view.tag == key) return
        view.tag = key
        val label = launcher.displayName(destination)
        val smallIcon = view.findViewById<android.widget.ImageView>(R.id.book_icon)
        val image = smallIcon ?: view.findViewById<android.widget.ImageView>(R.id.blocker_destination_icon)
        val appIcon = launcher.appIcon(destination)
        image?.apply {
            clearColorFilter()
            if (appIcon != null) setImageDrawable(appIcon)
            else setImageResource(R.drawable.noscroll_logo_transparent_128)
            if (smallIcon != null && appIcon == null) setColorFilter(Color.BLACK)
            contentDescription = getString(R.string.blocker_open_destination, label)
        }
        view.contentDescription = getString(R.string.blocker_destination_hint, label)
        view.findViewById<TextView>(R.id.blocker_destination_label)?.text =
            getString(R.string.blocker_tap_destination, label)
    }

    private fun updateOverlay(x: Int, y: Int, w: Int, h: Int, bgColor: Int = Color.BLACK) {
        if (overlayMode != OverlayMode.BOOK) {
            removeOverlayView()
        }
        // Invert the sampled background so the overlay contrasts with Instagram's nav bar
        val invertedBg = Color.rgb(
            255 - Color.red(bgColor),
            255 - Color.green(bgColor),
            255 - Color.blue(bgColor)
        )
        val existing = overlayView?.layoutParams as? WindowManager.LayoutParams
        if (existing != null && existing.x == x && existing.y == y &&
            existing.width == w && existing.height == h) {
            overlayView?.setBackgroundColor(invertedBg)
            applyDestinationAppearance(overlayView)
            return
        }
        removeOverlayView()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_book, null)
        view.setBackgroundColor(invertedBg)
        applyDestinationAppearance(view)
        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        view.setOnClickListener { launchDestination() }
        view.setOnLongClickListener { chooseDestination(); true }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            overlayMode = OverlayMode.BOOK
        } catch (_: Exception) {
            overlayView = null
            overlayMode = OverlayMode.NONE
            stopSelf()
            return
        }

        showReelsTooltipOnce(x, y + h)
    }

    private fun showReelsTooltipOnce(iconX: Int, iconBottom: Int) {
        val prefs = TutorialPrefs(this)
        if (!prefs.hasOptedIn() || prefs.isReelsDone()) return
        prefs.markReelsDone()

        val dm = resources.displayMetrics
        val dp8 = (8 * dm.density).toInt()
        val dp12 = (12 * dm.density).toInt()
        val dp16 = (16 * dm.density).toInt()

        val tooltip = TextView(this).apply {
            text = getString(R.string.blocker_destination_hint, AndroidBlockerDestination(this@OverlayService).displayName())
            setTextColor(Color.parseColor("#171615"))
            textSize = 13f
            setPadding(dp16, dp12, dp16, dp12)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp12.toFloat()
            }
            elevation = dp8.toFloat()
        }

        val tooltipParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = iconX
            y = (iconBottom + dp8).coerceAtMost(dm.heightPixels - dp16 * 4)
        }

        try {
            windowManager?.addView(tooltip, tooltipParams)
            tooltip.setOnClickListener { runCatching { windowManager?.removeView(tooltip) } }
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { windowManager?.removeView(tooltip) } },
                4000L
            )
        } catch (_: Exception) {}
    }

    private fun setTouchable(enabled: Boolean) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val shouldAllowTouches = enabled
        params.flags = if (shouldAllowTouches) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    private fun blockRegionFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    private fun showBlockRegion(x: Int, y: Int, w: Int, h: Int, visible: Boolean) {
        val targetMode = if (visible) OverlayMode.BLOCK else OverlayMode.TOUCH_BLOCK
        if (overlayMode != targetMode) {
            removeOverlayView()
        }
        val overlayFlags = blockRegionFlags()
        val existing = overlayView?.layoutParams as? WindowManager.LayoutParams
        if (existing != null) {
            if (visible) applyDestinationAppearance(overlayView)
            var changed = false
            if (existing.x != x || existing.y != y || existing.width != w || existing.height != h) {
                existing.x = x
                existing.y = y
                existing.width = w
                existing.height = h
                changed = true
            }
            if (existing.flags != overlayFlags) {
                existing.flags = overlayFlags
                changed = true
            }
            if (changed) {
                try { windowManager?.updateViewLayout(overlayView, existing) } catch (_: Exception) {}
            }
            return
        }
        val view = if (visible) {
            LayoutInflater.from(this).inflate(R.layout.overlay_reels_block, null).apply {
                setBackgroundColor(Color.parseColor("#FF171615"))
                alpha = 1f
                isClickable = true
                isFocusable = false
                applyDestinationAppearance(this)
                setOnClickListener { launchDestination() }
                setOnLongClickListener { chooseDestination(); true }
                findViewById<View>(R.id.blocker_change_app).setOnClickListener { chooseDestination() }
            }
        } else {
            View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                isFocusable = false
            }
        }
        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags,
            if (visible) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
        if (!visible) {
            view.setOnTouchListener { _, _ -> true }
            view.setOnClickListener { }
        }
        try {
            windowManager?.addView(view, params)
            overlayView = view
            overlayMode = targetMode
        } catch (_: Exception) {
            overlayView = null
            overlayMode = OverlayMode.NONE
            stopSelf()
        }
    }

    private fun launchDestination() {
        val result = AndroidBlockerDestination(this).openSelected()
        if (result != DestinationLaunchResult.FAILED) {
            removeOverlayView()
        } else {
            Toast.makeText(this, R.string.blocker_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseDestination() {
        if (AndroidBlockerDestination(this).showChooser()) removeOverlayView()
        else Toast.makeText(this, R.string.blocker_launch_failed, Toast.LENGTH_LONG).show()
    }

    private fun removeOverlayView() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
        overlayMode = OverlayMode.NONE
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "NoScroll Overlay", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Active while Instagram is open"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoScroll active")
            .setContentText("NoScroll logo appears on Instagram and blocks distracting feeds")
            .setSmallIcon(R.drawable.ic_book)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
