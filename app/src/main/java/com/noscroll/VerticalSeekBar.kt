package com.noscroll

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var max = 1
        set(value) { field = maxOf(value, 1); invalidate() }

    var progress = 0
        set(value) { field = value.coerceIn(0, max); invalidate() }

    var isDragging = false
        private set

    var onProgressChanged: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    private val dp = resources.displayMetrics.density

    private val trackW = 2f * dp
    private val thumbW = 5f * dp
    private val thumbH = 44f * dp

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
    }
    private val thumbActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1A2E.toInt()
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * dp
        textAlign = Paint.Align.CENTER
    }

    private val trackRect  = RectF()
    private val thumbRect  = RectF()
    private val bubbleRect = RectF()

    private val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
        duration   = 500
        startDelay = 1500
    }

    override fun onDraw(canvas: Canvas) {
        val cx   = width / 2f
        val padV = thumbH / 2f

        // Track
        trackRect.set(cx - trackW / 2f, padV, cx + trackW / 2f, height - padV)
        canvas.drawRoundRect(trackRect, trackW, trackW, trackPaint)

        // Thumb
        val frac     = progress.toFloat() / max
        val thumbTop = frac * (height - thumbH)
        thumbRect.set(cx - thumbW / 2f, thumbTop, cx + thumbW / 2f, thumbTop + thumbH)
        canvas.drawRoundRect(thumbRect, thumbW / 2f, thumbW / 2f,
            if (isDragging) thumbActivePaint else thumbPaint)

        // Page-number bubble while dragging
        if (isDragging) {
            val label   = "${progress + 1}"
            val bubbleH = 26f * dp
            val bubbleW = maxOf(textPaint.measureText(label) + 16f * dp, bubbleH)
            val thumbCy = thumbTop + thumbH / 2f
            val bubbleR = bubbleH / 2f
            bubbleRect.set(
                cx - thumbW / 2f - 8f * dp - bubbleW,
                thumbCy - bubbleH / 2f,
                cx - thumbW / 2f - 8f * dp,
                thumbCy + bubbleH / 2f
            )
            canvas.drawRoundRect(bubbleRect, bubbleR, bubbleR, bubblePaint)
            canvas.drawText(
                label,
                bubbleRect.centerX(),
                bubbleRect.centerY() + textPaint.textSize * 0.35f,
                textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                fadeOut.cancel()
                alpha = 1f
                isDragging = true
                val padV    = thumbH / 2f
                val frac    = ((event.y - padV) / (height - thumbH)).coerceIn(0f, 1f)
                val newProg = (frac * max).roundToInt()
                if (newProg != progress) {
                    progress = newProg
                    onProgressChanged?.invoke(progress, true)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                fadeOut.start()
                invalidate()
                return true
            }
        }
        return false
    }

    fun showAndFade() {
        fadeOut.cancel()
        alpha = 1f
        fadeOut.start()
    }
}
