package com.noscroll.quote

import android.graphics.Color
import android.graphics.Typeface

enum class QuoteBackgroundKind {
    GRADIENT,
    OCEAN,
    MOUNTAIN,
    RAIN
}

enum class QuoteLayout {
    EDITORIAL,
    CENTERED,
    SCENIC_PANEL,
    MINIMAL,
    BOOKPLATE
}

enum class QuotePanel {
    NONE,
    PAPER,
    SCRIM,
    GLASS
}

enum class QuoteFontFamily {
    SERIF,
    SANS,
    MONO
}

data class QuoteTypefaceSpec(
    val family: QuoteFontFamily,
    val style: Int
) {
    fun toTypeface(): Typeface {
        val base = when (family) {
            QuoteFontFamily.SERIF -> Typeface.SERIF
            QuoteFontFamily.SANS -> Typeface.SANS_SERIF
            QuoteFontFamily.MONO -> Typeface.MONOSPACE
        }
        return Typeface.create(base, style)
    }
}

data class QuoteCardStylePack(
    val id: String,
    val name: String,
    val category: String,
    val backgroundKind: QuoteBackgroundKind,
    val bgStart: Int,
    val bgEnd: Int,
    val textColor: Int,
    val attributionColor: Int,
    val accentColor: Int,
    val panelColor: Int,
    val layout: QuoteLayout,
    val panel: QuotePanel,
    val quoteTypeface: QuoteTypefaceSpec,
    val attributionTypeface: QuoteTypefaceSpec,
    val maxQuoteChars: Int = 620,
    val maxTextSize: Float = 62f,
    val minTextSize: Float = 34f
)

object QuoteCardStyles {
    const val DEFAULT_ID = "parchment_editorial"

    val all: List<QuoteCardStylePack> = listOf(
        QuoteCardStylePack(
            id = DEFAULT_ID,
            name = "Parchment Editorial",
            category = "Classic",
            backgroundKind = QuoteBackgroundKind.GRADIENT,
            bgStart = Color.rgb(253, 248, 235),
            bgEnd = Color.rgb(244, 234, 210),
            textColor = Color.rgb(34, 27, 18),
            attributionColor = Color.rgb(120, 100, 70),
            accentColor = Color.rgb(180, 140, 80),
            panelColor = Color.TRANSPARENT,
            layout = QuoteLayout.EDITORIAL,
            panel = QuotePanel.NONE,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.NORMAL)
        ),
        QuoteCardStylePack(
            id = "midnight_library",
            name = "Midnight Library",
            category = "Classic",
            backgroundKind = QuoteBackgroundKind.GRADIENT,
            bgStart = Color.rgb(16, 16, 20),
            bgEnd = Color.rgb(28, 27, 34),
            textColor = Color.rgb(242, 236, 220),
            attributionColor = Color.rgb(174, 160, 132),
            accentColor = Color.rgb(205, 159, 88),
            panelColor = Color.argb(120, 0, 0, 0),
            layout = QuoteLayout.EDITORIAL,
            panel = QuotePanel.NONE,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.NORMAL)
        ),
        QuoteCardStylePack(
            id = "forest_margin",
            name = "Forest Margin",
            category = "Classic",
            backgroundKind = QuoteBackgroundKind.GRADIENT,
            bgStart = Color.rgb(12, 32, 20),
            bgEnd = Color.rgb(27, 58, 36),
            textColor = Color.rgb(226, 240, 218),
            attributionColor = Color.rgb(150, 189, 144),
            accentColor = Color.rgb(126, 184, 104),
            panelColor = Color.TRANSPARENT,
            layout = QuoteLayout.CENTERED,
            panel = QuotePanel.NONE,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SANS, Typeface.NORMAL)
        ),
        QuoteCardStylePack(
            id = "ocean_still",
            name = "Ocean Still",
            category = "Scenic",
            backgroundKind = QuoteBackgroundKind.OCEAN,
            bgStart = Color.rgb(166, 207, 224),
            bgEnd = Color.rgb(20, 74, 106),
            textColor = Color.WHITE,
            attributionColor = Color.rgb(214, 232, 238),
            accentColor = Color.rgb(116, 204, 218),
            panelColor = Color.argb(165, 10, 30, 42),
            layout = QuoteLayout.SCENIC_PANEL,
            panel = QuotePanel.SCRIM,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SANS, Typeface.NORMAL),
            maxQuoteChars = 420
        ),
        QuoteCardStylePack(
            id = "mountain_dawn",
            name = "Mountain Dawn",
            category = "Scenic",
            backgroundKind = QuoteBackgroundKind.MOUNTAIN,
            bgStart = Color.rgb(239, 186, 142),
            bgEnd = Color.rgb(54, 73, 98),
            textColor = Color.rgb(48, 35, 28),
            attributionColor = Color.rgb(106, 78, 60),
            accentColor = Color.rgb(180, 99, 62),
            panelColor = Color.argb(218, 255, 248, 232),
            layout = QuoteLayout.SCENIC_PANEL,
            panel = QuotePanel.PAPER,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.NORMAL),
            maxQuoteChars = 420
        ),
        QuoteCardStylePack(
            id = "rain_window",
            name = "Rain Window",
            category = "Scenic",
            backgroundKind = QuoteBackgroundKind.RAIN,
            bgStart = Color.rgb(42, 48, 58),
            bgEnd = Color.rgb(12, 17, 24),
            textColor = Color.rgb(238, 243, 244),
            attributionColor = Color.rgb(182, 198, 204),
            accentColor = Color.rgb(142, 190, 200),
            panelColor = Color.argb(132, 220, 238, 240),
            layout = QuoteLayout.SCENIC_PANEL,
            panel = QuotePanel.GLASS,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SANS, Typeface.NORMAL),
            maxQuoteChars = 420
        ),
        QuoteCardStylePack(
            id = "minimal_ink",
            name = "Minimal Ink",
            category = "Modern",
            backgroundKind = QuoteBackgroundKind.GRADIENT,
            bgStart = Color.rgb(249, 248, 244),
            bgEnd = Color.rgb(238, 236, 229),
            textColor = Color.rgb(16, 17, 18),
            attributionColor = Color.rgb(92, 88, 80),
            accentColor = Color.rgb(16, 17, 18),
            panelColor = Color.TRANSPARENT,
            layout = QuoteLayout.MINIMAL,
            panel = QuotePanel.NONE,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SANS, Typeface.BOLD),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.MONO, Typeface.NORMAL),
            maxTextSize = 58f,
            minTextSize = 30f
        ),
        QuoteCardStylePack(
            id = "classic_bookplate",
            name = "Classic Bookplate",
            category = "Classic",
            backgroundKind = QuoteBackgroundKind.GRADIENT,
            bgStart = Color.rgb(250, 244, 229),
            bgEnd = Color.rgb(232, 218, 192),
            textColor = Color.rgb(52, 34, 22),
            attributionColor = Color.rgb(116, 82, 55),
            accentColor = Color.rgb(146, 95, 56),
            panelColor = Color.argb(45, 255, 255, 255),
            layout = QuoteLayout.BOOKPLATE,
            panel = QuotePanel.PAPER,
            quoteTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.ITALIC),
            attributionTypeface = QuoteTypefaceSpec(QuoteFontFamily.SERIF, Typeface.NORMAL)
        )
    )

    private val byId = all.associateBy { it.id }
    private val legacy = mapOf(
        "PARCHMENT" to DEFAULT_ID,
        "MIDNIGHT" to "midnight_library",
        "DUSK" to "rain_window",
        "OCEAN" to "ocean_still",
        "FOREST" to "forest_margin",
        "CLAY" to "classic_bookplate"
    )

    fun byId(id: String?): QuoteCardStylePack {
        val normalized = id?.trim().orEmpty()
        val mapped = legacy[normalized.uppercase()] ?: normalized
        return byId[mapped] ?: byId[DEFAULT_ID]!!
    }
}
