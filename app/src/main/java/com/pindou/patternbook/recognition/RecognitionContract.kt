package com.pindou.patternbook.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
}

class OnDeviceMardRecognitionService(private val context: Context) : MardRecognitionService {
    private val codePattern =
        Regex("(?<![A-Z0-9])([A-HM])\\s*[-_ ]?\\s*(\\d{1,2})(?!\\d)")
    private val quantityPattern =
        Regex("(?:\\((\\d{1,6})\\)|(?<![A-Z0-9])\\d{1,6}(?![A-Z0-9]))")

    override suspend fun recognizeLegend(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop?,
    ): RecognitionResult {
        val bitmap = loadBitmap(imageUri, crop)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            parseLegend(text.text, paletteVersion, crop)
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
        val bitmap = loadBitmap(imageUri, crop)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            val candidates = text.textBlocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    val parsed = parseCode(element.text, paletteVersion) ?: return@mapNotNull null
                    val column = ((box.centerX().toFloat() / bitmap.width) * columns)
                        .toInt()
                        .coerceIn(0, columns - 1)
                    val row = ((box.centerY().toFloat() / bitmap.height) * rows)
                        .toInt()
                        .coerceIn(0, rows - 1)
                    GridCell(
                        row = row,
                        column = column,
                        code = parsed.first,
                        confidence = parsed.second,
                    )
                }

            val selected = linkedMapOf<Pair<Int, Int>, GridCell>()
            candidates.forEach { candidate ->
                val key = candidate.row to candidate.column
                val previous = selected[key]
                if (previous == null || candidate.confidence > previous.confidence) {
                    selected[key] = candidate
                }
            }

            val warnings = buildList {
                if (selected.isEmpty()) {
                    add("没有识别到网格内的 MARD221 色号，请重新校准区域或确认图片文字清晰。")
                }
                if (selected.size < rows * columns / 20) {
                    add("目前只识别到少量格子，建议放大图片后检查网格尺寸和识别结果。")
                }
                add("自动识别只会填入读到的色号；空白格和低清格子需要在编辑页人工补齐。")
            }
            GridRecognitionResult(
                grid = if (selected.isEmpty()) null else GridPattern(
                    rows = rows,
                    columns = columns,
                    cells = selected.values.toList(),
                    crop = crop,
                ),
                warnings = warnings,
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun loadBitmap(uri: Uri, crop: NormalizedCrop?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: error("无法读取图纸图片")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片尺寸无法读取" }

        val sample = calculateSampleSize(maxOf(bounds.outWidth, bounds.outHeight), 3200)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("无法解码图纸图片")

        if (crop == null) return original
        val left = (original.width * crop.left).toInt().coerceIn(0, original.width - 1)
        val top = (original.height * crop.top).toInt().coerceIn(0, original.height - 1)
        val right = (original.width * crop.right).toInt().coerceIn(left + 1, original.width)
        val bottom = (original.height * crop.bottom).toInt().coerceIn(top + 1, original.height)
        val cropped = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
        if (cropped !== original) original.recycle()
        return cropped
    }

    private fun parseLegend(
        rawText: String,
        paletteVersion: MardPaletteVersion,
        crop: NormalizedCrop?,
    ): RecognitionResult {
        val found = linkedMapOf<String, RecognizedMardCode>()
        rawText.lines().forEach { line ->
            codePattern.findAll(line).forEach { match ->
                val code = MardCode.normalize(
                    match.groupValues[1] + match.groupValues[2],
                    paletteVersion,
                ) ?: return@forEach
                val codeNumber = match.groupValues[2].toIntOrNull()
                val quantity = quantityPattern.findAll(line)
                    .map { it.groupValues[1].ifBlank { it.value }.toIntOrNull() }
                    .firstOrNull { it != null && it != codeNumber }
                val confidence = if (match.value.replace(" ", "").length <= 3) 0.92f else 0.78f
                val candidate = RecognizedMardCode(code, quantity, confidence, line.trim())
                val previous = found[code]
                if (previous == null || (previous.quantity == null && quantity != null)) {
                    found[code] = candidate
                }
            }
        }

        val warnings = buildList {
            if (crop == null) {
                add("未框选色号说明，结果可能混入图纸网格中的色号；建议框选后再识别。")
            }
            if (found.isEmpty()) {
                add("没有识别到可确认的 MARD221 色号，请检查图片清晰度或重新框选说明区域。")
            }
            add("识别结果仅作整理参考，请逐项核对原图后确认。")
        }
        return RecognitionResult(found.values.toList(), warnings)
    }

    private fun parseCode(raw: String, paletteVersion: MardPaletteVersion): Pair<String, Float>? {
        val match = codePattern.find(raw.uppercase()) ?: return null
        val code = MardCode.normalize(
            match.groupValues[1] + match.groupValues[2],
            paletteVersion,
        ) ?: return null
        val compact = match.value.replace(Regex("[^A-Z0-9]"), "")
        val confidence = if (compact == code) 0.94f else 0.78f
        return code to confidence
    }

    private fun calculateSampleSize(longest: Int, requestedSize: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= requestedSize) sample *= 2
        return sample
    }
}
