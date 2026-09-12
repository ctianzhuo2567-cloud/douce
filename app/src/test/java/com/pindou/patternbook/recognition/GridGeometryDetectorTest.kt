package com.pindou.patternbook.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryDetectorTest {
    @Test
    fun detectsSmallSquareGrid() {
        val image = syntheticGrid(rows = 16, columns = 16, cellWidth = 20, cellHeight = 20)

        val result = GridGeometryDetector.detectLuminance(image.pixels, image.width, image.height)

        assertEquals(16, result.rows)
        assertEquals(16, result.columns)
        assertTrue(result.isUsable)
        assertTrue(result.confidence >= 0.58f)
    }

    @Test
    fun detectsDenseNonSquareGridWithMajorGuideLines() {
        val image = syntheticGrid(
            rows = 94,
            columns = 87,
            cellWidth = 9,
            cellHeight = 8,
            majorEvery = 10,
        )

        val result = GridGeometryDetector.detectLuminance(image.pixels, image.width, image.height)

        assertEquals(94, result.rows)
        assertEquals(87, result.columns)
        assertTrue(result.isUsable)
    }

    @Test
    fun acceptsSmallMarginAroundGrid() {
        val image = syntheticGrid(
            rows = 40,
            columns = 50,
            cellWidth = 14,
            cellHeight = 13,
            margin = 3,
        )

        val result = GridGeometryDetector.detectLuminance(image.pixels, image.width, image.height)

        assertEquals(40, result.rows)
        assertEquals(50, result.columns)
    }

    @Test
    fun refusesBlankImageInsteadOfInventingGrid() {
        val width = 480
        val height = 360
        val result = GridGeometryDetector.detectLuminance(
            luminance = IntArray(width * height) { 245 },
            width = width,
            height = height,
        )

        assertNull(result.rows)
        assertNull(result.columns)
        assertFalse(result.isUsable)
        assertTrue(result.warnings.isNotEmpty())
    }

    private data class TestImage(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    private fun syntheticGrid(
        rows: Int,
        columns: Int,
        cellWidth: Int,
        cellHeight: Int,
        majorEvery: Int = 0,
        margin: Int = 0,
    ): TestImage {
        val width = columns * cellWidth + 1 + margin * 2
        val height = rows * cellHeight + 1 + margin * 2
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            232 + ((x / cellWidth + y / cellHeight) % 3) * 6
        }

        for (column in 0..columns) {
            val x = margin + column * cellWidth
            val thickness = if (majorEvery > 0 && column % majorEvery == 0) 2 else 1
            repeat(thickness) { offset ->
                val drawX = (x + offset).coerceAtMost(width - 1)
                for (y in margin until height - margin) pixels[y * width + drawX] = 35
            }
        }
        for (row in 0..rows) {
            val y = margin + row * cellHeight
            val thickness = if (majorEvery > 0 && row % majorEvery == 0) 2 else 1
            repeat(thickness) { offset ->
                val drawY = (y + offset).coerceAtMost(height - 1)
                for (x in margin until width - margin) pixels[drawY * width + x] = 35
            }
        }
        return TestImage(pixels, width, height)
    }
}
