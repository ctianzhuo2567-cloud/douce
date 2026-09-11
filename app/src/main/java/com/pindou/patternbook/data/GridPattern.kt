package com.pindou.patternbook.data

data class GridCell(
    val row: Int,
    val column: Int,
    val code: String?,
    val confidence: Float = 0f,
)

data class GridPattern(
    val rows: Int,
    val columns: Int,
    val cells: List<GridCell>,
    val crop: NormalizedCrop,
) {
    private val cellMap: Map<Pair<Int, Int>, GridCell> by lazy {
        cells.associateBy { it.row to it.column }
    }

    fun cell(row: Int, column: Int): GridCell? = cellMap[row to column]

    fun counts(): Map<String, Int> = cells
        .mapNotNull { it.code }
        .groupingBy { it }
        .eachCount()

    fun normalized(): GridPattern = copy(
        rows = rows.coerceIn(1, 300),
        columns = columns.coerceIn(1, 300),
        cells = cells
            .filter { it.row in 0 until rows && it.column in 0 until columns }
            .distinctBy { it.row to it.column },
    )
}
