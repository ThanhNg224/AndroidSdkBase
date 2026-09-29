package io.github.thanhng224.sdkbase.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    @Test
    fun blackOnWhiteIsTheMaximum() {
        assertEquals(21f, contrastRatio(black, white), 0.01f)
        assertEquals(21f, contrastRatio(white, black), 0.01f)
    }

    @Test
    fun identicalColoursHaveNoContrast() {
        assertEquals(1f, contrastRatio(white, white), 0.001f)
    }

    @Test
    fun greyOnWhiteIsJudgedAgainstTheMinimum() {
        // #777777 on white is ~4.48: just under AA; #767676 is ~4.54: just over.
        assertFalse(meetsContrast(Color(0xFF777777), white))
        assertTrue(meetsContrast(Color(0xFF767676), white))
    }

    @Test
    fun theMinimumIsAdjustable() {
        assertTrue(meetsContrast(Color(0xFF777777), white, minimum = 3f))
        assertFalse(meetsContrast(Color(0xFF767676), white, minimum = 7f))
    }
}
