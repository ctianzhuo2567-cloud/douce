package com.pindou.patternbook.mard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MardCodeTest {
    @Test
    fun normalizesCommonOcrFormats() {
        assertEquals("A1", MardCode.normalize("(A01)", MardPaletteVersion.MARD_221))
        assertEquals("B7", MardCode.normalize("b-07", MardPaletteVersion.MARD_221))
        assertEquals("ZG8", MardCode.normalize("MARD ZG 08", MardPaletteVersion.MARD_291))
    }

    @Test
    fun rejectsUnknownOrOutOfRangeCodes() {
        assertNull(MardCode.normalize("I1", MardPaletteVersion.MARD_291))
        assertNull(MardCode.normalize("A0", MardPaletteVersion.MARD_221))
        assertNull(MardCode.normalize("ZG9", MardPaletteVersion.MARD_291))
        assertNull(MardCode.normalize("P1", MardPaletteVersion.MARD_221))
    }
}
