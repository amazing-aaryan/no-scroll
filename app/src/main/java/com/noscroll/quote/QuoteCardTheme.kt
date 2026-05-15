package com.noscroll.quote

import android.graphics.Color

enum class QuoteCardTheme(
    val bgStart: Int,
    val bgEnd: Int,
    val text: Int,
    val attribution: Int
) {
    PAPER(Color.rgb(251, 248, 240), Color.rgb(247, 243, 234), Color.rgb(23, 22, 21), Color.rgb(102, 97, 90)),
    INK(Color.rgb(23, 22, 21), Color.rgb(49, 46, 42), Color.rgb(251, 248, 240), Color.rgb(221, 213, 200)),
    SAGE(Color.rgb(119, 132, 111), Color.rgb(91, 105, 84), Color.rgb(251, 248, 240), Color.rgb(221, 213, 200)),
    NIGHT(Color.rgb(29, 31, 35), Color.rgb(16, 17, 19), Color.WHITE, Color.rgb(190, 186, 178))
}
