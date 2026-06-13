package com.noscroll

internal enum class InstagramBlockSurface {
    HOME,
    REELS,
    SEARCH_EXPLORE
}

internal data class IntBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal object InstagramBlockPolicy {
    fun blockBounds(
        surface: InstagramBlockSurface?,
        screenWidth: Int,
        screenHeight: Int,
        containerBounds: IntBounds?
    ): IntBounds? {
        if (surface == null || screenWidth <= 0 || screenHeight <= 0) return null
        val bounds = containerBounds ?: return null

        val left = bounds.left.coerceIn(0, screenWidth)
        val top = bounds.top.coerceIn(0, screenHeight)
        val right = bounds.right.coerceIn(0, screenWidth)
        val bottom = bounds.bottom.coerceIn(0, screenHeight)

        if (right <= left || bottom <= top) return null
        val minWidth = (screenWidth * 0.50f).toInt()
        val minHeight = (screenHeight * 0.20f).toInt()
        if (right - left < minWidth || bottom - top < minHeight) return null
        return IntBounds(left, top, right, bottom)
    }
}
