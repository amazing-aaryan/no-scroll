package com.noscroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    companion object {
        private const val CHANNEL_ID = "noscroll_overlay"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.noscroll.STOP_OVERLAY"
        const val ACTION_HIDE = "com.noscroll.HIDE_OVERLAY"
        const val ACTION_FREEZE = "com.noscroll.FREEZE_OVERLAY"
        const val ACTION_UNFREEZE = "com.noscroll.UNFREEZE_OVERLAY"
        const val ACTION_BLOCK_REELS = "com.noscroll.BLOCK_REELS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY // restarted by system after kill — wait for next real command
        if (intent.action == ACTION_BLOCK_REELS) {
            val navBarY = intent.getIntExtra("navBarY", -1)
            showReelsBlock(navBarY)
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

    private fun applyIconTint(view: View?, bgColor: Int) {
        val imageView = view?.findViewById<android.widget.ImageView>(R.id.book_icon) ?: return
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255.0
        imageView.setColorFilter(if (luminance > 0.5) Color.BLACK else Color.WHITE)
    }

    private fun updateOverlay(x: Int, y: Int, w: Int, h: Int, bgColor: Int = Color.BLACK) {
        val existing = overlayView?.layoutParams as? WindowManager.LayoutParams
        if (existing != null && existing.x == x && existing.y == y &&
            existing.width == w && existing.height == h) {
            overlayView?.setBackgroundColor(bgColor)
            applyIconTint(overlayView, bgColor)
            return
        }
        removeOverlayView()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_book, null)
        view.setBackgroundColor(bgColor)
        applyIconTint(view, bgColor)
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

        view.setOnClickListener {
            startActivity(
                Intent(this, PdfViewerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun setTouchable(enabled: Boolean) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = if (enabled) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    private fun showReelsBlock(navBarY: Int) {
        removeOverlayView()
        val screenHeight = resources.displayMetrics.heightPixels
        val height = if (navBarY > 0) navBarY else (screenHeight * 0.80).toInt()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_reels_block, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        view.setOnClickListener {
            startActivity(
                Intent(this, PdfViewerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun removeOverlayView() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
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
            .setContentText("Book icon appears on Instagram's Reels tab")
            .setSmallIcon(R.drawable.ic_book)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
