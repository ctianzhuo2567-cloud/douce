package com.pindou.patternbook.recognition

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GridGeometrySuggestion(
    val rows: Int?,
    val columns: Int?,
    val confidence: Float,
    val warnings: List<String> = emptyList(),
) {
    val isUsable: Boolean get() = rows != null && columns != null
}

/**
 * Detects regularly repeated grid lines from a tightly cropped pattern image.
 *
 * The detector deliberately does not alter the crop or persist a result. It only provides a
 * suggestion which must remain editable in the UI. This keeps a weak detection from damaging an
 * existing pattern.
 */
object GridGeometryDetector {
    private const val MAX_CELLS = 300
    private const val MIN_PITCH_PIXELS = 4

    fun detectArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): GridGeometrySuggestion {
        require(width > 2 && height > 2 && pixels.size >= width * height)

        val luminance = IntArray(width * height) { index ->
            val color = pixels[index]
            (Color.red(color) * 0.299f +
                Color.green(color) * 0.587f +
                Color.blue(color) * 0.114f).roundToInt()
        }
        return detectLuminance(luminance, width, height)
    }

    internal fun detectLuminance(
        luminance: IntArray,
        width: Int,
        height: Int,
    ): GridGeometrySuggestion {
        require(width > 2 && height > 2 && luminance.size >= width * height)
        val verticalEvidence = verticalLineEvidence(luminance, width, height)
        val horizontalEvidence = horizontalLineEvidence(luminance, width, height)
        val columns = detectAxis(verticalEvidence)
        val rows = detectAxis(horizontalEvidence)
        val confidence = listOfNotNull(rows?.confidence, columns?.confidence)
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
            ?: 0f

        val warnings = buildList {
            if (columns == null) add("未能稳定找到竖向网格线，请手动填写列数。")
            if (rows == null) add("未能稳定找到横向网格线，请手动填写行数。")
            if (rows != null && columns != null && confidence < 0.58f) {
                add("网格线规律性较弱，建议对照图纸坐标确认行列数。")
            }
        }
        return GridGeometrySuggestion(
            rows = rows?.cellCount,
            columns = columns?.cellCount,
            confidence = confidence,
            warnings = warnings,
        )
    }

    private data class AxisSuggestion(
        val cellCount: Int,
        val confidence: Float,
    )

    private data class PitchCandidate(
        val pitch: Int,
        val correlation: Float,
        val phaseScore: Float,
        val coverage: Float,
        val combinedScore: Float,
    )

    private fun detectAxis(rawEvidence: FloatArray): AxisSuggestion? {
        if (rawEvidence.size < 18) return null
        val evidence = normalizeEvidence(rawEvidence)
        val length = evidence.size
        val minimumPitch = maxOf(MIN_PITCH_PIXELS, length / MAX_CELLS)
        val maximumPitch = minOf(96, length / 3)
        if (minimumPitch > maximumPitch) return null

        val candidates = (minimumPitch..maximumPitch).map { pitch ->
            val correlation = autocorrelation(evidence, pitch)
            val phase = bestPhase(evidence, pitch)
            val combined = correlation * 0.48f + phase.first * 0.30f + phase.second * 0.22f
            PitchCandidate(
                pitch = pitch,
                correlation = correlation,
                phaseScore = phase.first,
                coverage = phase.second,
                combinedScore = combined,
            )
        }
        val strongest = candidates.maxByOrNull { it.combinedScore } ?: return null
        if (strongest.combinedScore < 0.30f || strongest.coverage < 0.38f) return null

        // Major guide lines (every 5 or 10 cells) can have a higher raw score than minor lines.
        // Prefer the smallest credible local peak so the fundamental cell pitch wins.
        val credibleFloor = strongest.combinedScore * 0.72f
        val selected = candidates.firstOrNull { candidate ->
            candidate.combinedScore >= credibleFloor &&
                candidate.coverage >= 0.44f &&
                isLocalPeak(candidates, candidate.pitch - minimumPitch)
        } ?: strongest

        val phase = bestPhaseIndex(evidence, selected.pitch)
        val firstLine = phase
        val intervals = ((length - 1 - firstLine).toFloat() / selected.pitch).roundToInt()
        val count = intervals.coerceIn(1, MAX_CELLS)
        if (count < 3) return null

        val edgeFit = edgeFit(length, firstLine, selected.pitch, count)
        val confidence = (
            selected.correlation * 0.34f +
                selected.phaseScore * 0.24f +
                selected.coverage * 0.27f +
                edgeFit * 0.15f
            ).coerceIn(0f, 1f)
        return AxisSuggestion(count, confidence)
    }

    private fun verticalLineEvidence(
        luminance: IntArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val result = FloatArray(width)
        val step = maxOf(1, height / 700)
        for (x in 1 until width - 1) {
            var score = 0f
            var samples = 0
            var y = 0
            while (y < height) {
                val index = y * width + x
                val neighbors = (luminance[index - 1] + luminance[index + 1]) * 0.5f
                score += (neighbors - luminance[index]).coerceAtLeast(0f)
                samples++
                y += step
            }
            result[x] = score / samples.coerceAtLeast(1)
        }
        return result
    }

    private fun horizontalLineEvidence(
        luminance: IntArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val result = FloatArray(height)
        val step = maxOf(1, width / 700)
        for (y in 1 until height - 1) {
            var score = 0f
            var samples = 0
            var x = 0
            while (x < width) {
                val index = y * width + x
                val neighbors = (luminance[index - width] + luminance[index + width]) * 0.5f
                score += (neighbors - luminance[index]).coerceAtLeast(0f)
                samples++
                x += step
            }
            result[y] = score / samples.coerceAtLeast(1)
        }
        return result
    }

    private fun normalizeEvidence(raw: FloatArray): FloatArray {
        val sorted = raw.sorted()
        val baseline = sorted[sorted.size / 2]
        val upper = sorted[(sorted.size * 0.92f).toInt().coerceIn(sorted.indices)]
        val scale = (upper - baseline).coerceAtLeast(1f)
        val normalized = FloatArray(raw.size) { index ->
            ((raw[index] - baseline) / scale).coerceIn(0f, 2.5f)
        }
        return FloatArray(raw.size) { index ->
            val from = maxOf(0, index - 1)
            val to = minOf(raw.lastIndex, index + 1)
            var maximum = 0f
            for (nearby in from..to) maximum = maxOf(maximum, normalized[nearby])
            maximum
        }
    }

    private fun autocorrelation(values: FloatArray, lag: Int): Float {
        val mean = values.average().toFloat()
        var numerator = 0f
        var leftSquare = 0f
        var rightSquare = 0f
        for (index in 0 until values.size - lag) {
            val left = values[index] - mean
            val right = values[index + lag] - mean
            numerator += left * right
            leftSquare += left * left
            rightSquare += right * right
        }
        val denominator = sqrt(leftSquare * rightSquare).coerceAtLeast(0.0001f)
        return ((numerator / denominator) + 1f).div(2f).coerceIn(0f, 1f)
    }

    private fun bestPhase(values: FloatArray, pitch: Int): Pair<Float, Float> {
        var bestScore = 0f
        var bestCoverage = 0f
        for (phase in 0 until pitch) {
            var score = 0f
            var hits = 0
            var count = 0
            var index = phase
            while (index < values.size) {
                val local = localMaximum(values, index, maxOf(1, pitch / 8))
                score += local.coerceAtMost(1.5f) / 1.5f
                if (local >= 0.45f) hits++
                count++
                index += pitch
            }
            if (count >= 3) {
                val average = score / count
                val coverage = hits.toFloat() / count
                if (average * 0.65f + coverage * 0.35f > bestScore * 0.65f + bestCoverage * 0.35f) {
                    bestScore = average
                    bestCoverage = coverage
                }
            }
        }
        return bestScore.coerceIn(0f, 1f) to bestCoverage.coerceIn(0f, 1f)
    }

    private fun bestPhaseIndex(values: FloatArray, pitch: Int): Int {
        var bestPhase = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (phase in 0 until pitch) {
            var score = 0f
            var index = phase
            while (index < values.size) {
                score += localMaximum(values, index, maxOf(1, pitch / 8))
                index += pitch
            }
            if (score > bestScore) {
                bestScore = score
                bestPhase = phase
            }
        }
        return bestPhase
    }

    private fun localMaximum(values: FloatArray, center: Int, radius: Int): Float {
        var maximum = 0f
        for (index in maxOf(0, center - radius)..minOf(values.lastIndex, center + radius)) {
            maximum = maxOf(maximum, values[index])
        }
        return maximum
    }

    private fun isLocalPeak(candidates: List<PitchCandidate>, index: Int): Boolean {
        val current = candidates[index].combinedScore
        val left = candidates.getOrNull(index - 1)?.combinedScore ?: current
        val right = candidates.getOrNull(index + 1)?.combinedScore ?: current
        return current >= left && current >= right
    }

    private fun edgeFit(length: Int, first: Int, pitch: Int, count: Int): Float {
        val final = first + pitch * count
        val tolerance = maxOf(2f, pitch * 0.7f)
        val firstScore = 1f - (first / tolerance).coerceIn(0f, 1f)
        val finalScore = 1f - (abs(length - 1 - final) / tolerance).coerceIn(0f, 1f)
        return (firstScore + finalScore) / 2f
    }
}
