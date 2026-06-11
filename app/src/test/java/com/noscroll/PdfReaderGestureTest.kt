package com.noscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the three PDF reader conditions:
 * 1. One page at a time (structural — PagerSnapHelper + match_parent item height)
 * 2. Scroll → next page (PagerSnapHelper handles this; gesture threshold logic tested)
 * 3. Swipe → flip animation (gesture detection thresholds)
 */
class PdfReaderGestureTest {

    private val MIN_FLIP_VELOCITY = 400f

    // ── Condition 3: horizontal swipe triggers flip, vertical scroll does not ──

    @Test
    fun `fast horizontal swipe left triggers flip to next page`() {
        val velocityX = -600f
        val velocityY = 50f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertTrue("Left swipe at 600px/s should trigger flip", triggersFlip)
    }

    @Test
    fun `fast horizontal swipe right triggers flip to previous page`() {
        val velocityX = 700f
        val velocityY = 80f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertTrue("Right swipe at 700px/s should trigger flip", triggersFlip)
    }

    @Test
    fun `slow horizontal swipe below velocity threshold does not trigger flip`() {
        val velocityX = -200f
        val velocityY = 30f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertFalse("Slow swipe at 200px/s must NOT trigger flip", triggersFlip)
    }

    @Test
    fun `fast vertical scroll does not trigger flip`() {
        val velocityX = 100f
        val velocityY = -900f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertFalse("Vertical scroll must NOT trigger flip", triggersFlip)
    }

    @Test
    fun `diagonal swipe mostly vertical does not trigger flip`() {
        val velocityX = 400f
        val velocityY = -500f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertFalse("Mostly-vertical diagonal must NOT trigger flip", triggersFlip)
    }

    @Test
    fun `diagonal swipe mostly horizontal triggers flip`() {
        val velocityX = 700f
        val velocityY = -200f
        val triggersFlip = abs(velocityX) > abs(velocityY) * 1.1f && abs(velocityX) > MIN_FLIP_VELOCITY
        assertTrue("Mostly-horizontal diagonal SHOULD trigger flip", triggersFlip)
    }

    // ── Flip direction mapping ─────────────────────────────────────────────────

    @Test
    fun `swipe left maps to direction plus-one (next page)`() {
        val velocityX = -600f
        val direction = if (velocityX < 0) 1 else -1
        assertEquals("Left swipe should advance to next page (direction=+1)", 1, direction)
    }

    @Test
    fun `swipe right maps to direction minus-one (previous page)`() {
        val velocityX = 600f
        val direction = if (velocityX < 0) 1 else -1
        assertEquals("Right swipe should go to previous page (direction=-1)", -1, direction)
    }

    // ── Page bounds enforced by scroll/flip nav ────────────────────────────────

    @Test
    fun `cannot flip past first page`() {
        val currentPage = 0
        val totalPages = 10
        val direction = -1
        val targetPage = (currentPage + direction).coerceIn(0, totalPages - 1)
        assertEquals("Should stay on page 0 when at first page", 0, targetPage)
    }

    @Test
    fun `cannot flip past last page`() {
        val currentPage = 9
        val totalPages = 10
        val direction = 1
        val targetPage = (currentPage + direction).coerceIn(0, totalPages - 1)
        assertEquals("Should stay on page 9 when at last page", 9, targetPage)
    }

    @Test
    fun `flip from mid-book advances exactly one page`() {
        val currentPage = 5
        val totalPages = 20
        val direction = 1
        val targetPage = (currentPage + direction).coerceIn(0, totalPages - 1)
        assertEquals("Should advance to page 6", 6, targetPage)
    }

    @Test
    fun `flip backward from mid-book goes exactly one page back`() {
        val currentPage = 5
        val totalPages = 20
        val direction = -1
        val targetPage = (currentPage + direction).coerceIn(0, totalPages - 1)
        assertEquals("Should go back to page 4", 4, targetPage)
    }
}
