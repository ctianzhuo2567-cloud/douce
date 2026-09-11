package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.mard.MardCode
import com.pindou.patternbook.mard.MardPaletteVersion
import com.pindou.patternbook.recognition.RecognizedMardCode
import com.pindou.patternbook.ui.theme.GrapePurple

private data class CodeEditorState(
    val originalCode: String?,
    val code: String,
    val quantity: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionReviewScreen(
    pattern: PatternItem,
    warnings: List<String>,
    swatches: Map<String, Color>,
    paletteVersion: MardPaletteVersion,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onRecognizeAgain: () -> Unit,
    onResultsChange: (List<RecognizedMardCode>) -> Unit,
) {
    var editor by remember { mutableStateOf<CodeEditorState?>(null) }

    editor?.let { initial ->
        CodeEditorDialog(
            initial = initial,
            existingCodes = pattern.recognizedCodes.map { it.code }.toSet(),
            paletteVersion = paletteVersion,
            onDismiss = { editor = null },
            onSave = { code, quantity ->
                val replacement = RecognizedMardCode(
                    code = code,
                    quantity = quantity,
                    confidence = 1f,
                    sourceText = if (initial.originalCode == null) "人工添加" else "人工修改",
                )
                val updated = if (initial.originalCode == null) {
                    pattern.recognizedCodes + replacement
                } else {
                    pattern.recognizedCodes.map { current ->
                        if (current.code == initial.originalCode) replacement else current
                    }
                }
                onResultsChange(updated.sortedByMardCode())
                editor = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("整理色号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onRecognizeAgain) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("重新识别", modifier = Modifier.padding(start = 4.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirm,
                enabled = pattern.recognizedCodes.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Text("确认并保存", modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(pattern.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "MARD 221 · " + pattern.recognizedCodes.size + " 种色号 · " +
                            pattern.recognizedCodes.sumOf { it.quantity ?: 0 } + " 颗",
                        modifier = Modifier.padding(top = 4.dp),
                        color = GrapePurple,
                    )
                }
            }

            if (warnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("识别提示", style = MaterialTheme.typography.titleMedium)
                            warnings.forEach { warning ->
                                Text(
                                    "· $warning",
                                    modifier = Modifier.padding(top = 5.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        editor = CodeEditorState(
                            originalCode = null,
                            code = "",
                            quantity = "",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("补充漏识别的色号", modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (pattern.recognizedCodes.isEmpty()) {
                item {
                    Text(
                        "没有识别到色号。可以手动补充，或返回重新框选图例。",
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(pattern.recognizedCodes, key = { it.code }) { result ->
                    RecognizedCodeRow(
                        result = result,
                        swatch = swatches[result.code] ?: Color.LightGray,
                        onEdit = {
                            editor = CodeEditorState(
                                originalCode = result.code,
                                code = result.code,
                                quantity = result.quantity?.toString().orEmpty(),
                            )
                        },
                        onDelete = {
                            onResultsChange(pattern.recognizedCodes.filterNot { it.code == result.code })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognizedCodeRow(
    result: RecognizedMardCode,
    swatch: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (confidenceLabel, confidenceColor) = when {
        result.confidence >= 0.82f -> "高可信" to Color(0xFF14805E)
        result.confidence >= 0.58f -> "待确认" to Color(0xFF9A6800)
        else -> "需核对" to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(swatch, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.code, style = MaterialTheme.typography.titleMedium)
                    Text(
                        confidenceLabel,
                        modifier = Modifier.padding(start = 8.dp),
                        color = confidenceColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    result.sourceText.ifBlank { "来自图例说明" },
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                result.quantity?.let { "$it 颗" } ?: "待填数量",
                style = MaterialTheme.typography.titleSmall,
                color = GrapePurple,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "修改 " + result.code)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "删除 " + result.code,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CodeEditorDialog(
    initial: CodeEditorState,
    existingCodes: Set<String>,
    paletteVersion: MardPaletteVersion,
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit,
) {
    var codeInput by remember(initial) { mutableStateOf(initial.code) }
    var quantityInput by remember(initial) { mutableStateOf(initial.quantity) }
    var error by remember(initial) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.originalCode == null) "添加色号" else "修改色号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase().take(4)
                        error = null
                    },
                    label = { Text("MARD221 色号") },
                    placeholder = { Text("例如 A1、B22") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = {
                        quantityInput = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    label = { Text("数量（可不填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = MardCode.normalize(codeInput, paletteVersion)
                    val quantity = quantityInput.toIntOrNull()
                    error = when {
                        normalized == null -> "请输入有效的 MARD221 色号"
                        normalized != initial.originalCode && normalized in existingCodes ->
                            "$normalized 已经在列表中"
                        quantityInput.isNotBlank() && (quantity == null || quantity <= 0) ->
                            "数量需要大于 0"
                        else -> null
                    }
                    if (error == null && normalized != null) {
                        onSave(normalized, quantity)
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun List<RecognizedMardCode>.sortedByMardCode(): List<RecognizedMardCode> {
    val seriesOrder = listOf("A", "B", "C", "D", "E", "F", "G", "H", "M")
    return sortedWith(
        compareBy(
            { result -> seriesOrder.indexOf(result.code.takeWhile { it.isLetter() }) },
            { result -> result.code.dropWhile { it.isLetter() }.toIntOrNull() ?: 0 },
        ),
    )
}
