package com.noscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramBlockPolicyTest {
    @Test
    fun `reels block uses detected container bounds`() {
        val bounds = InstagramBlockPolicy.blockBounds(
            surface = InstagramBlockSurface.REELS,
            screenWidth = 1080,
            screenHeight = 2424,
            containerBounds = IntBounds(0, 142, 1080, 2361)
        )

        assertEquals(IntBounds(0, 142, 1080, 2361), bounds)
    }

    @Test
    fun `home block uses detected feed container bounds`() {
        val bounds = InstagramBlockPolicy.blockBounds(
            surface = InstagramBlockSurface.HOME,
            screenWidth = 1080,
            screenHeight = 2424,
            containerBounds = IntBounds(0, 142, 1080, 2235)
        )

        assertEquals(IntBounds(0, 142, 1080, 2235), bounds)
    }

    @Test
    fun `search block uses detected result container bounds`() {
        val bounds = InstagramBlockPolicy.blockBounds(
            surface = InstagramBlockSurface.SEARCH_EXPLORE,
            screenWidth = 1080,
            screenHeight = 2424,
            containerBounds = IntBounds(0, 289, 1080, 2361)
        )

        assertEquals(IntBounds(0, 289, 1080, 2361), bounds)
    }

    @Test
    fun `missing or too-small container produces no block`() {
        val bounds = InstagramBlockPolicy.blockBounds(
            surface = InstagramBlockSurface.HOME,
            screenWidth = 1080,
            screenHeight = 2424,
            containerBounds = IntBounds(0, 120, 200, 300)
        )

        assertNull(bounds)
    }
}
