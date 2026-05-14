package com.noscroll.quote

import android.graphics.Color

enum class QuoteCardTheme(
    val bgStart: Int,
    val bgEnd: Int,
    val text: Int,
    val attribution: Int
) {
    DARK(Color.rgb(26, 26, 46), Color.rgb(22, 33, 62), Color.WHITE, Color.rgb(170, 170, 170)),
    LIGHT(Color.rgb(250, 250, 250), Color.rgb(240, 240, 240), Color.rgb(26, 26, 26), Color.rgb(102, 102, 102)),
    SEPIA(Color.rgb(245, 230, 200), Color.rgb(237, 217, 163), Color.rgb(62, 39, 35), Color.rgb(141, 110, 99)),
    BLAZE(Color.rgb(255, 107, 53), Color.rgb(192, 57, 43), Color.WHITE, Color.rgb(255, 215, 204))
}
