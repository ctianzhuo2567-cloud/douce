package com.pindou.patternbook.data

enum class PatternStatus(val label: String) {
    TO_SORT("待整理"),
    READY("待制作"),
    IN_PROGRESS("制作中"),
    DONE("已完成"),
}

enum class RecognitionStatus(val label: String) {
    NOT_STARTED("未识别"),
    NEEDS_REVIEW("待确认"),
    CONFIRMED("已确认"),
}

data class PatternItem(
    val id: String,
    val title: String,
    val imageUri: String,
    val createdAt: Long,
    val status: PatternStatus = PatternStatus.TO_SORT,
    val recognitionStatus: RecognitionStatus = RecognitionStatus.NOT_STARTED,
    val isFavorite: Boolean = false,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val legendCrop: NormalizedCrop? = null,
    val tags: List<String> = emptyList(),
    val note: String = "",
)

data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val DEFAULT = NormalizedCrop(left = 0.08f, top = 0.72f, right = 0.92f, bottom = 0.96f)
    }
}
