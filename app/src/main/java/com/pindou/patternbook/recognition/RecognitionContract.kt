package com.pindou.patternbook.recognition

import android.net.Uri
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

interface MardRecognitionService {
    suspend fun recognizeLegend(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
    ): RecognitionResult
}

/**
 * Deliberate placeholder. The first UI slice can be tested without pretending
 * OCR is ready; the on-device recognizer will replace this implementation.
 */
class PendingOnDeviceRecognitionService : MardRecognitionService {
    override suspend fun recognizeLegend(
        imageUri: Uri,
        paletteVersion: MardPaletteVersion,
    ): RecognitionResult = RecognitionResult(
        codes = emptyList(),
        warnings = listOf("本地 OCR 尚未接入，请先保留原图并手动核对色号。"),
    )
}

