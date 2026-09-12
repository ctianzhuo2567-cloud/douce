package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.recognition.GridGeometrySuggestion
import com.pindou.patternbook.ui.theme.GrapePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridRecognitionSetupScreen(
    pattern: PatternItem,
    onBack: () -> Unit,
    onStart: (rows: Int, columns: Int) -> Unit,
    suggestion: GridGeometrySuggestion?,
    detecting: Boolean,
    onRetryDetection: () -> Unit,
    recognizing: Boolean,
) {
    var rows by rememberSaveable(pattern.id) {
        mutableStateOf(pattern.gridPattern?.rows?.toString().orEmpty())
    }
    var columns by rememberSaveable(pattern.id) {
        mutableStateOf(pattern.gridPattern?.columns?.toString().orEmpty())
    }
    var manuallyEdited by rememberSaveable(pattern.id) { mutableStateOf(false) }
    LaunchedEffect(suggestion) {
        if (!manuallyEdited && suggestion?.isUsable == true) {
            rows = suggestion.rows.toString()
            columns = suggestion.columns.toString()
        }
    }
    val rowValue = rows.toIntOrNull()?.takeIf { it in 1..300 }
    val columnValue = columns.toIntOrNull()?.takeIf { it in 1..300 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("校准网格") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("自动测算网格", style = MaterialTheme.typography.titleLarge)
                    when {
                        detecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 12.dp),
                                color = GrapePurple,
                            )
                            Text(
                                "正在分析横线和竖线的重复间距…",
                                modifier = Modifier.padding(top = 10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        suggestion?.isUsable == true -> {
                            Text(
                                "建议 ${suggestion.rows} 行 × ${suggestion.columns} 列 · ${confidenceLabel(suggestion.confidence)}",
                                modifier = Modifier.padding(top = 6.dp),
                                color = GrapePurple,
                            )
                            Text(
                                "请对照图纸边缘的坐标确认；数值不对可直接修改。",
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> Text(
                            "自动测算未得到稳定结果，请手动填写行列数。",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!detecting && suggestion?.isUsable != true) {
                OutlinedButton(
                    onClick = onRetryDetection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新测算")
                }
            }
            suggestion?.warnings?.forEach { warning ->
                Text(
                    warning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = rows,
                onValueChange = {
                    manuallyEdited = true
                    rows = it.filter(Char::isDigit).take(3)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("行数") },
                supportingText = { Text("例如 50、87") },
                singleLine = true,
            )
            OutlinedTextField(
                value = columns,
                onValueChange = {
                    manuallyEdited = true
                    columns = it.filter(Char::isDigit).take(3)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("列数") },
                supportingText = { Text("例如 50、94") },
                singleLine = true,
            )
            Text(
                "图纸只在本机处理，不会上传原图。不同作者的网格线粗细不同，识别完成后请抽查。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { onStart(rowValue ?: 0, columnValue ?: 0) },
                enabled = rowValue != null && columnValue != null && !recognizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.GridOn, contentDescription = null)
                Text(
                    if (recognizing) "正在识别网格…" else "开始识别网格",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (rowValue == null || columnValue == null) {
                Text(
                    "行数和列数需要填写 1–300 之间的整数。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "将生成 $rowValue × $columnValue 的独立网格。",
                    color = GrapePurple,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun confidenceLabel(confidence: Float): String = when {
    confidence >= 0.76f -> "高置信度"
    confidence >= 0.58f -> "中置信度"
    else -> "低置信度"
}
