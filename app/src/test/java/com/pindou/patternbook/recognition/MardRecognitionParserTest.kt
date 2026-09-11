package com.pindou.patternbook.recognition

import com.pindou.patternbook.mard.MardPaletteVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MardRecognitionParserTest {
    @Test
    fun keepsQuantitiesWithTheirOwnLegendCards() {
        val result = MardRecognitionParser.parseLines(
            lines = listOf("A1 (12)   A4 (16)   A20 (10)"),
            paletteVersion = MardPaletteVersion.MARD_221,
        )

        assertEquals(
            mapOf("A1" to 12, "A4" to 16, "A20" to 10),
            result.codes.associate { it.code to it.quantity },
        )
    }

    @Test
    fun correctsConservativeCharacterConfusions() {
        val result = MardRecognitionParser.parseLines(
            lines = listOf("H1O (65) FIS (159) B2Z (59)"),
            paletteVersion = MardPaletteVersion.MARD_221,
        )

        assertEquals(
            setOf("H10", "F15", "B22"),
            result.codes.map { it.code }.toSet(),
        )
    }

    @Test
    fun raisesEvidenceWhenIndependentPassesAgree() {
        val result = MardRecognitionParser.parsePasses(
            passes = listOf(
                listOf("B22 (63)"),
                listOf("B22 63"),
                listOf("B2Z (63)"),
            ),
            paletteVersion = MardPaletteVersion.MARD_221,
        )

        val code = result.codes.single()
        assertEquals("B22", code.code)
        assertEquals(63, code.quantity)
        assertTrue(code.confidence >= 0.82f)
    }

    @Test
    fun warnsInsteadOfHidingQuantityConflicts() {
        val result = MardRecognitionParser.parsePasses(
            passes = listOf(
                listOf("F7 (181)"),
                listOf("F7 (101)"),
            ),
            paletteVersion = MardPaletteVersion.MARD_221,
        )

        assertTrue(result.warnings.any { it.contains("F7") && it.contains("不一致") })
        assertTrue(result.codes.single().confidence < 0.82f)
    }

    @Test
    fun rejectsOutOfRangeCandidatesAfterCorrection() {
        val result = MardRecognitionParser.parseLines(
            lines = listOf("A99 (1) H99 (2) Z1 (3)"),
            paletteVersion = MardPaletteVersion.MARD_221,
        )

        assertTrue(result.codes.isEmpty())
    }
}
