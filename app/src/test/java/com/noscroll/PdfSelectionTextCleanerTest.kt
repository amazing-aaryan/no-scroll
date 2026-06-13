package com.noscroll

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSelectionTextCleanerTest {
    @Test
    fun `removes stray percent signs from extracted quote text`() {
        val raw = "We % hold these truths to % be self-evident%"

        assertEquals(
            "We hold these truths to be self-evident",
            PdfSelectionTextCleaner.clean(raw)
        )
    }

    @Test
    fun `keeps numeric percentage signs`() {
        val raw = "Only 10% of the class held 50 % of the votes."

        assertEquals(
            "Only 10% of the class held 50 % of the votes.",
            PdfSelectionTextCleaner.clean(raw)
        )
    }

    @Test
    fun `joins hyphenated line breaks`() {
        val raw = "The govern-\n ment must remain account-\r\nable."

        assertEquals(
            "The government must remain accountable.",
            PdfSelectionTextCleaner.clean(raw)
        )
    }

    @Test
    fun `normalizes hidden controls and whitespace`() {
        val raw = "A\u0000  quote\uFFFD\nwith\tspacing"

        assertEquals(
            "A quote with spacing",
            PdfSelectionTextCleaner.clean(raw)
        )
    }
}
