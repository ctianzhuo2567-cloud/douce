package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.data.RecognitionStatus
import com.pindou.patternbook.ui.components.UriImage
import com.pindou.patternbook.ui.theme.BerryPink
import com.pindou.patternbook.ui.theme.GrapePurple
import com.pindou.patternbook.ui.theme.PaleMauve

@Composable
fun RecognitionScreen(
    patterns: List<PatternItem>,
    onOpenPattern: (PatternItem) -> Unit,
    onRecognize: (PatternItem) -> Unit,
    recognizingId: String?,
    onGoToLibrary: () -> Unit,
) {
    val pending = patterns.filter { it.recognitionStatus != RecognitionStatus.CONFIRMED }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("色号识别", style = MaterialTheme.typography.displaySmall)
            Text(
                "先识别有 MARD 色号说明的图纸",
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { RecognitionFlowCard() }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("等待处理", style = MaterialTheme.typography.titleLarge)
                Text("${pending.size} 张", color = GrapePurple, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (pending.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoToLibrary),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = GrapePurple)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("还没有待识别图纸", style = MaterialTheme.typography.titleMedium)
                            Text("先从图纸库导入图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                    }
                }
            }
        } else {
            items(pending, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPattern(item) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UriImage(
                            uri = item.imageUri,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                            requestedSize = 300,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                item.recognitionStatus.label,
                                modifier = Modifier.padding(top = 5.dp),
                                color = BerryPink,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        TextButton(
                            onClick = { onRecognize(item) },
                            enabled = recognizingId == null,
                        ) {
                            Text(if (recognizingId == item.id) "识别中…" else "开始识别")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionFlowCard() {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("识别流程", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            RecognitionStep("1", "框选说明", "只截取色号和数量区域", BerryPink) {
                Icon(Icons.Rounded.ContentCut, contentDescription = null)
            }
            RecognitionStep("2", "本地识别", "放大后按 MARD 合法色号校验", GrapePurple) {
                Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
            }
            RecognitionStep("3", "人工确认", "低把握结果不会自动通过", PaleMauve) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RecognitionStep(
    number: String,
    title: String,
    subtitle: String,
    color: Color,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("$number  $title", style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
