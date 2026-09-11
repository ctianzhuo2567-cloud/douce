package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.ui.theme.GrapePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridRecognitionSetupScreen(
    pattern: PatternItem,
    onBack: () -> Unit,
    onStart: (rows: Int, columns: Int) -> Unit,
    recognizing: Boolean,
) {
    var rows by rememberSaveable(pattern.id) {
        mutableStateOf(pattern.gridPattern?.rows?.toString() ?: "50")
    }
    var columns by rememberSaveable(pattern.id) {
        mutableStateOf(pattern.gridPattern?.columns?.toString() ?: "50")
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
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("网格区域已框选", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "填写图纸的行数和列数后进行本地识别。识别结果可以逐格修改。",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = rows,
                onValueChange = { rows = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("行数") },
                supportingText = { Text("例如 50、87") },
                singleLine = true,
            )
            OutlinedTextField(
                value = columns,
                onValueChange = { columns = it.filter(Char::isDigit).take(3) },
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
