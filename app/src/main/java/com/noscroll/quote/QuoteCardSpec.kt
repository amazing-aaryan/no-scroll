package com.noscroll.quote

data class QuoteCardSpec(
    val quoteText: String,
    val bookTitle: String,
    val author: String,
    val pageNumber: Int,
    val theme: QuoteCardTheme = QuoteCardTheme.DARK
)
