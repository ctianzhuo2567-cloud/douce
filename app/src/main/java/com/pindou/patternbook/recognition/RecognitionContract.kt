package com.pindou.patternbook.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pindou.patternbook.data.GridCell
import com.pindou.patternbook.data.GridPattern
import com.pindou.patternbook.data.NormalizedCrop
import com.pindou.patternbook.mard.MardCode
import com.pindou.patternbook.mard.MardPaletteVersion
import kotlin.math.roundToInt

data class RecognizedMardCode(
    val code: String,
    val quantity: Int?,
    val confidence: Float,
    val sourceText: String,
)

data class RecognitionResult(
    val codes: List<RecognizedMardCode>,
    val warnings: List<String>,
)

data class GridRecognitionResult(
    val grid: GridPattern?,
    val warnings: List<String>,
)

interface MardRecognitionService {
    suspend fun recognizeLegend(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop? = null,
    ): RecognitionResult

    suspend fun recognizeGrid(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop,
        rows: Int,
        columns: Int,
    ): GridRecognitionResult

    suspend fun suggestGridGeometry(
        imageUri: Uri,
        crop: NormalizedCrop,
    ): GridGeometrySuggestion
}

/** Pure parser kept separate from ML Kit so its correction rules can be regression tested. */
object MardRecognitionParser {
    private val candidatePattern = Regex(
        "(?<![A-Z0-9])([A-HMN])\\s*[-_ ]?\\s*([0-9OILSZBG]{1,2})(?![A-Z0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val quantityPattern = Regex(
        "(?:[（(]\\s*(\\d{1,6})\\s*[）)]|(?<![A-Z0-9])(\\d{1,6})(?![A-Z0-9]))",
    )

    private data class Candidate(
        val code: String,
        val quantity: Int?,
        val sourceText: String,
        val passIndex: Int,
        val exact: Boolean,
    )

    fun parseLines(
        lines: List<String>,
        paletteVersion: MardPaletteVersion,
    ): RecognitionResult = parsePasses(listOf(lines), paletteVersion)

    fun parsePasses(
        passes: List<List<String>>,
        paletteVersion: MardPaletteVersion,
    ): RecognitionResult {
        val candidates = passes.flatMapIndexed { passIndex, lines ->
            lines.flatMap { line -> parseLine(line, paletteVersion, passIndex) }
        }
        val warnings = mutableListOf<String>()
        val results = linkedMapOf<String, RecognizedMardCode>()

        candidates.groupBy { it.code }.forEach { (code, matches) ->
            val passAgreement = matches.map { it.passIndex }.distinct().size
            val quantityCounts = matches.mapNotNull { it.quantity }.groupingBy { it }.eachCount()
            val quantity = quantityCounts.maxByOrNull { it.value }?.key
            val quantityConflict = quantityCounts.size > 1
            val exact = matches.any { it.exact }

            var evidence = when {
                passAgreement >= 2 && exact -> 0.92f
                passAgreement >= 2 -> 0.78f
                exact -> 0.68f
                else -> 0.48f
            }
            if (quantityConflict) {
                evidence = (evidence - 0.20f).coerceAtLeast(0.30f)
                warnings += "$code 的数量在不同识别结果中不一致，请核对。"
            }
            val preferred = matches.firstOrNull { it.quantity == quantity && it.exact }
                ?: matches.firstOrNull { it.quantity == quantity }
                ?: matches.first()
            results[code] = RecognizedMardCode(
                code = code,
                quantity = quantity,
                confidence = evidence,
                sourceText = preferred.sourceText,
            )
        }

        if (results.isEmpty()) {
            warnings += "没有识别到可确认的 MARD221 色号，请检查图片清晰度或重新框选说明区域。"
        }
        return RecognitionResult(results.values.toList(), warnings.distinct())
    }

    fun normalizeCandidate(
        raw: String,
        paletteVersion: MardPaletteVersion,
    ): String? {
        val compact = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (compact.length !in 2..3) return null
        val rawSeries = compact.first()
        if (rawSeries !in "ABCDEFGHMN") return null
        val series = if (rawSeries == 'N') 'M' else rawSeries
        val digits = compact.drop(1).map { character ->
            when (character) {
                'O', 'Q', 'D' -> '0'
                'I', 'L' -> '1'
                'Z' -> '2'
                'S' -> '5'
                'G' -> '6'
                'B' -> '8'
                else -> character
            }
        }.joinToString("")
        return MardCode.normalize("$series$digits", paletteVersion)
    }

    private fun parseLine(
        line: String,
        paletteVersion: MardPaletteVersion,
        passIndex: Int,
    ): List<Candidate> {
        val uppercase = line.uppercase()
        val matches = candidatePattern.findAll(uppercase).toList()
        return matches.mapIndexedNotNull { index, match ->
            val rawCode = match.groupValues[1] + match.groupValues[2]
            val code = normalizeCandidate(rawCode, paletteVersion) ?: return@mapIndexedNotNull null
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: uppercase.length
            val followingText = uppercase.substring(match.range.last + 1, nextStart)
            val quantityMatch = quantityPattern.find(followingText)
            val quantity = quantityMatch?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                ?.toIntOrNull()
            val compactRaw = rawCode.replace(Regex("[^A-Z0-9]"), "")
            Candidate(
                code = code,
                quantity = quantity,
                sourceText = line.trim(),
                passIndex = passIndex,
                exact = MardCode.normalize(compactRaw, paletteVersion) == code,
            )
        }
    }
}

class OnDeviceMardRecognitionService(private val context: Context) : MardRecognitionService {
    override suspend fun recognizeLegend(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop?,
    ): RecognitionResult {
        val bitmap = loadBitmap(
            uri = imageUri,
            crop = crop,
            maximumLongSide = 3200,
            minimumShortSide = 640,
        )
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val passes = mutableListOf<List<String>>()
            passes += recognizeLines(recognizer, bitmap)
            listOf(false, true).forEach { inverted ->
                val enhanced = createTextContrastBitmap(bitmap, inverted)
                try {
                    passes += recognizeLines(recognizer, enhanced)
                } finally {
                    enhanced.recycle()
                }
            }
            val parsed = MardRecognitionParser.parsePasses(passes, paletteVersion)
            parsed.copy(
                warnings = buildList {
                    addAll(parsed.warnings)
                    if (crop == null) {
                        add("未框选色号说明，结果可能混入图纸网格中的色号；建议框选后再识别。")
                    }
                    add("已使用原图、增强和反色三路识别；待确认项目请对照原图修改。")
                }.distinct(),
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    override suspend fun recognizeGrid(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop,
        rows: Int,
        columns: Int,
    ): GridRecognitionResult {
        require(rows in 1..300 && columns in 1..300) { "网格尺寸需要在 1 到 300 之间" }
        val bitmap = loadBitmap(imageUri, crop, maximumLongSide = 4096)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val candidates = recognizeGridBands(
                recognizer = recognizer,
                bitmap = bitmap,
                paletteVersion = paletteVersion,
                rows = rows,
                columns = columns,
            )
            val selected = selectGridCandidates(candidates)

            val warnings = buildList {
                if (selected.isEmpty()) {
                    add("没有识别到网格内的 MARD221 色号，请重新校准区域或确认图片文字清晰。")
                }
                if (selected.size < rows * columns / 20) {
                    add("目前只识别到少量格子，请检查框选区域和行列数。")
                }
                add("已按行分段放大，并使用原图与反色两路 OCR；低置信度格子请对照原图抽查。")
            }
            GridRecognitionResult(
                grid = if (selected.isEmpty()) null else GridPattern(
                    rows = rows,
                    columns = columns,
                    cells = selected,
                    crop = crop,
                ),
                warnings = warnings,
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private data class GridCandidate(
        val row: Int,
        val column: Int,
        val code: String,
        val confidence: Float,
        val passIndex: Int,
    )

    private fun recognizeGridBands(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
        paletteVersion: MardPaletteVersion,
        rows: Int,
        columns: Int,
    ): List<GridCandidate> {
        val candidates = mutableListOf<GridCandidate>()
        val bandRows = 18
        val overlapRows = 1
        var coreStartRow = 0
        while (coreStartRow < rows) {
            val coreEndRow = minOf(rows, coreStartRow + bandRows)
            val sampleStartRow = maxOf(0, coreStartRow - overlapRows)
            val sampleEndRow = minOf(rows, coreEndRow + overlapRows)
            val top = (bitmap.height * sampleStartRow.toFloat() / rows).roundToInt()
                .coerceIn(0, bitmap.height - 1)
            val bottom = (bitmap.height * sampleEndRow.toFloat() / rows).roundToInt()
                .coerceIn(top + 1, bitmap.height)
            val band = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
            val cellWidth = band.width.toFloat() / columns
            val cellHeight = bitmap.height.toFloat() / rows
            val requestedScale = (26f / minOf(cellWidth, cellHeight)).coerceIn(1f, 3f)
            val widthLimitScale = 4096f / band.width
            val scale = minOf(requestedScale, widthLimitScale.coerceAtLeast(1f))
            val working = if (scale > 1.05f) {
                Bitmap.createScaledBitmap(
                    band,
                    (band.width * scale).roundToInt(),
                    (band.height * scale).roundToInt(),
                    true,
                )
            } else {
                band
            }

            try {
                val passes = listOf(
                    working to false,
                    createTextContrastBitmap(working, inverted = true) to true,
                )
                try {
                    passes.forEachIndexed { passIndex, (passBitmap, shouldRecycle) ->
                        val text = Tasks.await(
                            recognizer.process(InputImage.fromBitmap(passBitmap, 0)),
                        )
                        text.textBlocks
                            .flatMap { it.lines }
                            .flatMap { it.elements }
                            .forEach { element ->
                                val box = element.boundingBox ?: return@forEach
                                val code = MardRecognitionParser.normalizeCandidate(
                                    element.text,
                                    paletteVersion,
                                ) ?: return@forEach
                                val sourceX = box.centerX().toFloat() / passBitmap.width * bitmap.width
                                val sourceY = top + box.centerY().toFloat() / passBitmap.height * band.height
                                val row = ((sourceY / bitmap.height) * rows).toInt()
                                    .coerceIn(0, rows - 1)
                                if (row !in coreStartRow until coreEndRow) return@forEach
                                val column = ((sourceX / bitmap.width) * columns).toInt()
                                    .coerceIn(0, columns - 1)
                                val exact = MardCode.normalize(element.text, paletteVersion) == code
                                candidates += GridCandidate(
                                    row = row,
                                    column = column,
                                    code = code,
                                    confidence = if (exact) 0.70f else 0.48f,
                                    passIndex = passIndex,
                                )
                            }
                        if (shouldRecycle) passBitmap.recycle()
                    }
                } finally {
                    passes.filter { it.second && !it.first.isRecycled }.forEach { it.first.recycle() }
                }
            } finally {
                if (working !== band) working.recycle()
                band.recycle()
            }
            coreStartRow = coreEndRow
        }
        return candidates
    }

    private fun selectGridCandidates(candidates: List<GridCandidate>): List<GridCell> {
        return candidates
            .groupBy { Triple(it.row, it.column, it.code) }
            .map { (key, matches) ->
                val passAgreement = matches.map { it.passIndex }.distinct().size
                GridCell(
                    row = key.first,
                    column = key.second,
                    code = key.third,
                    confidence = if (passAgreement >= 2) 0.90f else matches.maxOf { it.confidence },
                )
            }
            .groupBy { it.row to it.column }
            .mapNotNull { (_, cells) -> cells.maxByOrNull { it.confidence } }
    }

    override suspend fun suggestGridGeometry(
        imageUri: Uri,
        crop: NormalizedCrop,
    ): GridGeometrySuggestion {
        val bitmap = loadBitmap(
            uri = imageUri,
            crop = crop,
            maximumLongSide = 1800,
        )
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            GridGeometryDetector.detectArgb(pixels, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(
        uri: Uri,
        crop: NormalizedCrop?,
        maximumLongSide: Int,
        minimumShortSide: Int = 0,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = context.contentResolver.openInputStream(uri)?.use { input ->
            // BitmapFactory intentionally returns null when only bounds are requested.
            BitmapFactory.decodeStream(input, null, bounds)
            true
        } ?: false
        check(boundsRead) { "无法读取图纸图片" }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片尺寸无法读取" }

        val region = crop?.let {
            Rect(
                (bounds.outWidth * it.left).roundToInt().coerceIn(0, bounds.outWidth - 1),
                (bounds.outHeight * it.top).roundToInt().coerceIn(0, bounds.outHeight - 1),
                (bounds.outWidth * it.right).roundToInt().coerceIn(1, bounds.outWidth),
                (bounds.outHeight * it.bottom).roundToInt().coerceIn(1, bounds.outHeight),
            )
        } ?: Rect(0, 0, bounds.outWidth, bounds.outHeight)
        require(region.width() > 0 && region.height() > 0) { "框选区域无效" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(maxOf(region.width(), region.height()), maximumLongSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = if (crop == null) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val decoder = BitmapRegionDecoder.newInstance(input, false)
                    ?: error("无法读取框选区域")
                try {
                    decoder.decodeRegion(region, options)
                } finally {
                    decoder.recycle()
                }
            }
        } ?: error("无法解码图纸图片")

        if (minimumShortSide <= 0 || minOf(decoded.width, decoded.height) >= minimumShortSide) {
            return decoded
        }
        val requestedScale = minimumShortSide.toFloat() / minOf(decoded.width, decoded.height)
        val allowedScale = maximumLongSide.toFloat() / maxOf(decoded.width, decoded.height)
        val scale = minOf(requestedScale, allowedScale, 4f)
        if (scale <= 1.05f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt(),
            (decoded.height * scale).roundToInt(),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun recognizeLines(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): List<String> {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        return text.textBlocks.flatMap { block -> block.lines.map { it.text } }
    }

    private fun createTextContrastBitmap(source: Bitmap, inverted: Boolean): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val luminance = (
                Color.red(color) * 0.299f +
                    Color.green(color) * 0.587f +
                    Color.blue(color) * 0.114f
                ).roundToInt()
            val contrasted = when {
                luminance < 88 -> 0
                luminance > 190 -> 255
                else -> ((luminance - 88) * 2.5f).roundToInt().coerceIn(0, 255)
            }
            val value = if (inverted) 255 - contrasted else contrasted
            pixels[index] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun calculateSampleSize(longest: Int, requestedSize: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= requestedSize) sample *= 2
        return sample
    }
}
