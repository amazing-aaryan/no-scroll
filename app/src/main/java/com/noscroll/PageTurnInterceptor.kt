package com.noscroll

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class PageTurnInterceptor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onSwipeLeft()
        fun onSwipeRight()
    }

    var listener: Listener? = null
    var isAnimating = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var startX = 0f
    private var startY = 0f
    private var intercepting = false

    // Single GestureDetector fed by both onInterceptTouchEvent and onTouchEvent so
    // it sees the full DOWN → MOVE → UP sequence needed for reliable fling detection.
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isAnimating) return false
                val dx = e2.x - (e1?.x ?: return false)
                val dy = e2.y - (e1.y)
                if (abs(dx) > abs(dy) * 1.1f && abs(velocityX) > MIN_FLING_VELOCITY) {
                    if (dx < 0) listener?.onSwipeLeft() else listener?.onSwipeRight()
                    return true
                }
                return false
            }
        }
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                intercepting = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!intercepting && !isAnimating) {
                    val dx = abs(ev.x - startX)
                    val dy = abs(ev.y - startY)
                    // Intercept only when gesture is clearly horizontal
                    if (dx > touchSlop && dx > dy * 1.5f) {
                        intercepting = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                intercepting = false
            }
        }
        return intercepting
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return true
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 350f
    }
}
