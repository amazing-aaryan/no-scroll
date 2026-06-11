package com.noscroll.quote

import android.graphics.Color

enum class QuoteCardTheme(
    val bgStart: Int,
    val bgEnd: Int,
    val text: Int,
    val attribution: Int,
    val accent: Int
) {
    PARCHMENT(
        Color.rgb(253, 248, 235), Color.rgb(244, 234, 210),
        Color.rgb(34, 27, 18), Color.rgb(120, 100, 70),
        Color.rgb(180, 140, 80)
    ),
    MIDNIGHT(
        Color.rgb(16, 16, 20), Color.rgb(24, 24, 32),
        Color.rgb(240, 235, 220), Color.rgb(155, 145, 125),
        Color.rgb(180, 140, 80)
    ),
    DUSK(
        Color.rgb(30, 18, 48), Color.rgb(72, 30, 55),
        Color.rgb(245, 225, 210), Color.rgb(185, 155, 135),
        Color.rgb(220, 130, 100)
    ),
    OCEAN(
        Color.rgb(8, 28, 58), Color.rgb(16, 50, 85),
        Color.rgb(220, 240, 248), Color.rgb(120, 175, 205),
        Color.rgb(70, 185, 210)
    ),
    FOREST(
        Color.rgb(12, 32, 20), Color.rgb(22, 52, 32),
        Color.rgb(220, 240, 215), Color.rgb(120, 175, 130),
        Color.rgb(90, 175, 100)
    ),
    CLAY(
        Color.rgb(240, 225, 210), Color.rgb(225, 200, 175),
        Color.rgb(60, 30, 15), Color.rgb(140, 85, 55),
        Color.rgb(185, 100, 60)
    )
}
