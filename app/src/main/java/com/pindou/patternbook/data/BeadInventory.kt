package com.pindou.patternbook.data

data class MardColor(
    val code: String,
    val hex: String,
    val group: String,
)

data class BeadStock(
    val color: MardColor,
    val quantity: Int? = null,
    val lowStockThreshold: Int = 100,
    val updatedAt: Long = 0L,
) {
    val isOwned: Boolean get() = (quantity ?: 0) > 0
    val isLow: Boolean get() = quantity?.let { it in 1..lowStockThreshold } == true
    val isEmpty: Boolean get() = quantity == 0
    val isUntracked: Boolean get() = quantity == null
}

data class StockAdjustment(
    val code: String,
    val delta: Int,
    val quantityAfter: Int,
    val occurredAt: Long,
)
