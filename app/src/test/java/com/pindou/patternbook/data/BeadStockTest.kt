package com.pindou.patternbook.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeadStockTest {
    private val color = MardColor(code = "A4", hex = "#F7EC5C", group = "A")

    @Test
    fun distinguishesUntrackedFromEmpty() {
        val untracked = BeadStock(color = color, quantity = null)
        val empty = BeadStock(color = color, quantity = 0)

        assertTrue(untracked.isUntracked)
        assertFalse(untracked.isEmpty)
        assertTrue(empty.isEmpty)
        assertFalse(empty.isUntracked)
    }

    @Test
    fun marksPositiveQuantityAtThresholdAsLow() {
        assertTrue(BeadStock(color = color, quantity = 100).isLow)
        assertFalse(BeadStock(color = color, quantity = 101).isLow)
    }
}
