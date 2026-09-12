package com.pindou.patternbook.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.GridCell
import com.pindou.patternbook.data.GridPattern
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.ui.theme.GrapePurple
import kotlin.math.floor
import kotlin.math.min

private const val ERASE_GRID_CELL = "__ERASE_GRID_CELL__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridPatternEditorScreen(
    pattern: PatternItem,
    grid: GridPattern,
    swatches: Map<String, Color>,
    onBack: () -> Unit,
    onSave: (GridPattern) -> Unit,
) {
    var editable by remember(pattern.id, grid) { mutableStateOf(grid.normalized()) }
    var highlightCode by remember { mutableStateOf<String?>(null) }
    var additionalCodes by remember(pattern.id) { mutableStateOf<Set<String>>(emptySet()) }
    var showAddCodeDialog by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var editing by remember(pattern.id) { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val changed = editable != grid.normalized()
    val requestBack: () -> Unit = { if (changed) confirmLeave = true else onBack() }
    BackHandler(enabled = !showAddCodeDialog && !confirmLeave, onBack = requestBack)
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("保存本次修改？") },
            text = { Text("网格中有尚未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onSave(editable) }) { Text("保存并返回") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text("放弃修改") }
            },
        )
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 6f)
        pan += panChange
    }
    val counts = editable.counts()
    val paletteCodes = (counts.keys + pattern.recognizedCodes.map { it.code } + additionalCodes)
        .distinct()
        .sortedWith(
        compareBy<String> { it.firstOrNull() ?: 'Z' }
            .thenBy { it.drop(1).toIntOrNull() ?: 0 },
    )
    val normalizedCodeInput = codeInput.trim().uppercase()
    val canAddCode = normalizedCodeInput in swatches

    if (showAddCodeDialog) {
        AlertDialog(
            onDismissRequest = { showAddCodeDialog = false },
            title = { Text("添加 MARD221 色号") },
            text = {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.filter(Char::isLetterOrDigit).take(3) },
                    label = { Text("例如 A4、H16") },
                    supportingText = {
                        if (codeInput.isNotBlank() && !canAddCode) Text("该色号不在 MARD221 目录中")
                    },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canAddCode,
                    onClick = {
                        additionalCodes = additionalCodes + normalizedCodeInput
                        highlightCode = normalizedCodeInput
                        editing = true
                        codeInput = ""
                        showAddCodeDialog = false
                    },
                ) { Text("添加并选中") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCodeDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("图纸高亮助手") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { editing = !editing }) {
                        Text(if (editing) "结束编辑" else "编辑格子")
                    }
                    IconButton(
                        onClick = {
                            zoom = 1f
                            pan = Offset.Zero
                        },
                    ) {
                        Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "还原视图")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = { onSave(editable) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Text("保存独立网格", modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${editable.rows} × ${editable.columns}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "  ·  ${editable.cells.count { it.code != null }} 格已识别",
                    modifier = Modifier.weight(1f),
                    color = GrapePurple,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when (highlightCode) {
                        null -> "显示全部"
                        ERASE_GRID_CELL -> "擦除格子"
                        else -> "高亮 $highlightCode"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFD3D6DA))
                    .onSizeChanged { viewport = it }
                    .transformable(transformState)
                    .gridTapInput(
                        viewport = viewport,
                        rows = editable.rows,
                        columns = editable.columns,
                        zoom = zoom,
                        pan = pan,
                        onCellTap = { row, column ->
                            if (editing) {
                            when (val code = highlightCode) {
                                null -> Unit
                                ERASE_GRID_CELL -> editable = editable.copy(
                                    cells = editable.cells.filterNot {
                                        it.row == row && it.column == column
                                    },
                                )
                                else -> {
                                    val next = editable.cells
                                        .filterNot { it.row == row && it.column == column } +
                                        GridCell(row, column, code, 1f)
                                    editable = editable.copy(cells = next)
                                }
                            }
                            }
                        },
                    ),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                ) {
                    drawPatternGrid(
                        editable,
                        swatches,
                        highlightCode?.takeUnless { it == ERASE_GRID_CELL },
                    )
                }
            }

            Text(
                if (!editing) "查看模式：选色仅高亮。要修改格子，请先点击右上角“编辑格子”。" else when (highlightCode) {
                    null -> "选择下方色号后，点击格子可以把它改成该色号；双指缩放和拖动查看细节。"
                    ERASE_GRID_CELL -> "当前是擦除模式；点击误识别的格子可将其清空。"
                    else -> "当前高亮 $highlightCode；点击格子可补录或改成该色号。"
                },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    FilterChip(
                        selected = highlightCode == null,
                        onClick = { highlightCode = null },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = highlightCode == ERASE_GRID_CELL,
                        onClick = {
                            editing = true
                            highlightCode = if (highlightCode == ERASE_GRID_CELL) null else ERASE_GRID_CELL
                        },
                        label = { Text("擦除") },
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showAddCodeDialog = true },
                        label = { Text("+添加色号") },
                    )
                }
                items(paletteCodes.size) { index ->
                    val code = paletteCodes[index]
                    FilterChip(
                        selected = highlightCode == code,
                        onClick = { highlightCode = if (highlightCode == code) null else code },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp)
                                        .background(swatches[code] ?: Color.LightGray, CircleShape),
                                )
                                Text("$code ${counts[code] ?: 0}")
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.gridTapInput(
    viewport: IntSize,
    rows: Int,
    columns: Int,
    zoom: Float,
    pan: Offset,
    onCellTap: (Int, Int) -> Unit,
): Modifier = pointerInput(viewport, rows, columns, zoom, pan, onCellTap) {
    detectTapGestures { point ->
        if (viewport.width <= 0 || viewport.height <= 0) return@detectTapGestures
        val layout = gridLayout(viewport.width.toFloat(), viewport.height.toFloat(), rows, columns)
        val localX = (point.x - pan.x) / zoom - layout.originX
        val localY = (point.y - pan.y) / zoom - layout.originY
        val column = floor(localX / layout.cellSize).toInt()
        val row = floor(localY / layout.cellSize).toInt()
        if (row in 0 until rows && column in 0 until columns) {
            onCellTap(row, column)
        }
    }
}

private fun DrawScope.drawPatternGrid(
    grid: GridPattern,
    swatches: Map<String, Color>,
    highlightCode: String?,
) {
    val tile = 18f
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val dark = ((x / tile).toInt() + (y / tile).toInt()) % 2 == 0
            drawRect(
                color = if (dark) Color(0xFFDDE0E3) else Color(0xFFC8CCD0),
                topLeft = Offset(x, y),
                size = Size(tile, tile),
            )
            x += tile
        }
        y += tile
    }

    val layout = gridLayout(size.width, size.height, grid.rows, grid.columns)
    val cells = grid.cells.associateBy { it.row to it.column }

    for (row in 0 until grid.rows) {
        for (column in 0 until grid.columns) {
            val code = cells[row to column]?.code
            val alpha = if (code != null && highlightCode != null && code != highlightCode) 0.10f else 1f
            val topLeft = Offset(
                layout.originX + column * layout.cellSize,
                layout.originY + row * layout.cellSize,
            )
            if (code != null) {
                drawRect(
                    color = (swatches[code] ?: Color.LightGray).copy(alpha = alpha),
                    topLeft = topLeft,
                    size = Size(layout.cellSize, layout.cellSize),
                )
            }
            val major = row % 10 == 0 || column % 10 == 0
            drawRect(
                color = if (major) GrapePurple.copy(alpha = 0.72f) else Color(0xFFADB3B9).copy(alpha = 0.72f),
                topLeft = topLeft,
                size = Size(layout.cellSize, layout.cellSize),
                style = Stroke(width = if (major) 1.8f else 0.7f),
            )
            if (code != null && layout.cellSize >= 14f) {
                drawCellLabel(code, topLeft.x, topLeft.y, layout.cellSize, alpha)
            }
        }
    }
}

private fun DrawScope.drawCellLabel(code: String, x: Float, y: Float, cellSize: Float, alpha: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (cellSize > 24f) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            textSize = (cellSize * 0.42f).coerceAtLeast(8f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.alpha = (alpha * 255).toInt()
        }
        canvas.nativeCanvas.drawText(
            code,
            x + cellSize / 2f,
            y + cellSize / 2f - (paint.ascent() + paint.descent()) / 2f,
            paint,
        )
    }
}

private data class GridLayout(val originX: Float, val originY: Float, val cellSize: Float)

private fun gridLayout(width: Float, height: Float, rows: Int, columns: Int): GridLayout {
    val cellSize = min((width - 32f) / columns, (height - 32f) / rows).coerceAtLeast(2f)
    val gridWidth = columns * cellSize
    val gridHeight = rows * cellSize
    return GridLayout((width - gridWidth) / 2f, (height - gridHeight) / 2f, cellSize)
}
