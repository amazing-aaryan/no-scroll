package com.noscroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var contentOverlayView: View? = null
    private var navBarOverlayView: View? = null

    companion object {
        private const val CHANNEL_ID = "noscroll_overlay"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.noscroll.STOP_OVERLAY"
        const val ACTION_HIDE = "com.noscroll.HIDE_OVERLAY"

        // Light-mode Instagram feed background (#FAFAFA) and matching dark icon
        private val LIGHT_BG = Color.parseColor("#FAFAFA")
        private val LIGHT_ICON = Color.parseColor("#1A1A2E")

        // Dark-mode Instagram feed background (pure black) and white icon
        private val DARK_BG = Color.BLACK
        private val DARK_ICON = Color.WHITE

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY
        if (intent.action == ACTION_HIDE) {
            removeAllOverlays()
            return START_STICKY
        }
        if (intent.action == ACTION_STOP) {
            removeAllOverlays()
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val screenW = intent.getIntExtra("screenW", resources.displayMetrics.widthPixels)
        val contentY = intent.getIntExtra("contentY", 0)
        val contentH = intent.getIntExtra("contentH", 0)
        val navX = intent.getIntExtra("navX", 0)
        val navY = intent.getIntExtra("navY", 0)
        val navW = intent.getIntExtra("navW", screenW)
        val navH = intent.getIntExtra("navH", 120)

        if (contentH > 0) updateContentOverlay(0, contentY, screenW, contentH)
        updateNavBarOverlay(navX, navY, navW, navH)

        return START_STICKY
    }

    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun updateContentOverlay(x: Int, y: Int, w: Int, h: Int) {
        val existing = contentOverlayView?.layoutParams as? WindowManager.LayoutParams
        if (existing != null && existing.x == x && existing.y == y &&
            existing.width == w && existing.height == h) return
        removeContentOverlayView()

        val dark = isDarkMode()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_instagram_content, null)
        view.setBackgroundColor(if (dark) DARK_BG else LIGHT_BG)
        view.findViewById<ImageView>(R.id.content_overlay_icon)
            .setColorFilter(if (dark) DARK_ICON else LIGHT_ICON)

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        view.setOnClickListener {
            startActivity(
                Intent(this, PdfViewerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        windowManager?.addView(view, params)
        contentOverlayView = view
    }

    private fun updateNavBarOverlay(x: Int, y: Int, w: Int, h: Int) {
        val existing = navBarOverlayView?.layoutParams as? WindowManager.LayoutParams
        if (existing != null && existing.x == x && existing.y == y &&
            existing.width == w && existing.height == h) return
        removeNavBarOverlayView()

        val dark = isDarkMode()
        // Nav bar bg: semi-transparent version of the nav bar's own colour (~80% alpha)
        val navBg = if (dark) Color.argb(0xCC, 0x00, 0x00, 0x00)
                    else      Color.argb(0xCC, 0xFA, 0xFA, 0xFA)
        val iconColor = if (dark) DARK_ICON else LIGHT_ICON

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_instagram_nav, null)
        view.setBackgroundColor(navBg)
        view.findViewById<ImageView>(R.id.nav_overlay_icon).setColorFilter(iconColor)

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        windowManager?.addView(view, params)
        navBarOverlayView = view
    }

    private fun removeContentOverlayView() {
        contentOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            contentOverlayView = null
        }
    }

    private fun removeNavBarOverlayView() {
        navBarOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            navBarOverlayView = null
        }
    }

    private fun removeAllOverlays() {
        removeContentOverlayView()
        removeNavBarOverlayView()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllOverlays()
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
            .setContentText("Blocking Instagram feed — stories and nav bar accessible")
            .setSmallIcon(R.drawable.ic_book)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
