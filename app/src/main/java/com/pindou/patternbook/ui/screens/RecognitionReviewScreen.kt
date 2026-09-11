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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.recognition.RecognizedMardCode
import com.pindou.patternbook.ui.theme.GrapePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionReviewScreen(
    pattern: PatternItem,
    warnings: List<String>,
    swatches: Map<String, Color>,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onRecognizeAgain: () -> Unit,
) {
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
                        "MARD 221 · ${pattern.recognizedCodes.size} 种色号",
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

            if (pattern.recognizedCodes.isEmpty()) {
                item {
                    Text(
                        "没有可以确认的色号。请返回重新框选图例，尽量只保留色号和数量。",
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(pattern.recognizedCodes, key = { it.code }) { result ->
                    RecognizedCodeRow(result, swatches[result.code] ?: Color.LightGray)
                }
            }
        }
    }
}

@Composable
private fun RecognizedCodeRow(result: RecognizedMardCode, swatch: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(swatch, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(result.code, style = MaterialTheme.typography.titleMedium)
                Text(
                    result.sourceText.ifBlank { "来自图例说明" },
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    result.quantity?.let { "$it 颗" } ?: "数量待核对",
                    style = MaterialTheme.typography.titleMedium,
                    color = GrapePurple,
                )
                Text(
                    "把握 ${(result.confidence * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
