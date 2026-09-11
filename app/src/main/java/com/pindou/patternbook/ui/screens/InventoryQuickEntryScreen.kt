package com.pindou.patternbook.ui.screens

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.BeadStock
import com.pindou.patternbook.ui.theme.BerryPink
import com.pindou.patternbook.ui.theme.GrapePurple

private enum class QuickEntryMode(val label: String) {
    BATCH("批量记录"),
    PACKAGE("套装入库"),
}

private enum class PackageOperation(val label: String) {
    ADD("追加库存"),
    SET("设为数量"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryQuickEntryScreen(
    stocks: List<BeadStock>,
    onBack: () -> Unit,
    onApply: (Map<String, Int>) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(QuickEntryMode.BATCH) }
    var group by rememberSaveable { mutableStateOf("全部") }
    var packageAmount by rememberSaveable { mutableStateOf("1000") }
    var packageOperation by rememberSaveable { mutableStateOf(PackageOperation.ADD) }
    val drafts = remember { mutableStateMapOf<String, Int>() }
    var pendingTargets by remember { mutableStateOf<Map<String, Int>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("快捷入库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val packageValue = packageAmount.toIntOrNull()?.takeIf { it >= 0 }
            val enabled = if (mode == QuickEntryMode.BATCH) drafts.isNotEmpty() else packageValue != null
            Button(
                onClick = {
                    pendingTargets = if (mode == QuickEntryMode.BATCH) {
                        drafts.toMap()
                    } else {
                        stocks.associate { stock ->
                            val amount = packageValue ?: 0
                            val target = when (packageOperation) {
                                PackageOperation.ADD -> (stock.quantity ?: 0) + amount
                                PackageOperation.SET -> amount
                            }
                            stock.color.code to target
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (mode == QuickEntryMode.BATCH) "确认录入 ${drafts.size} 个色号" else "确认套装入库")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickEntryMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { mode = item },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (mode == QuickEntryMode.BATCH) {
                BatchEntryContent(
                    stocks = stocks,
                    group = group,
                    onGroupChange = { group = it },
                    drafts = drafts,
                )
            } else {
                PackageEntryContent(
                    amount = packageAmount,
                    onAmountChange = { packageAmount = it.filter(Char::isDigit).take(7) },
                    operation = packageOperation,
                    onOperationChange = { packageOperation = it },
                )
            }
        }
    }

    pendingTargets?.let { targets ->
        val changed = targets.count { (code, target) ->
            stocks.firstOrNull { it.color.code == code }?.quantity != target
        }
        AlertDialog(
            onDismissRequest = { pendingTargets = null },
            title = { Text("确认修改库存") },
            text = {
                Text(
                    if (mode == QuickEntryMode.PACKAGE) {
                        "将修改 $changed 个 MARD 221 色号。${packageOperation.label}不会删除目录外或未选中的记录。"
                    } else {
                        "将保存 $changed 个色号的新数量。未填写的色号保持不变。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onApply(targets); pendingTargets = null }) { Text("确认入库") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTargets = null }) { Text("再检查一下") }
            },
        )
    }
}

@Composable
private fun BatchEntryContent(
    stocks: List<BeadStock>,
    group: String,
    onGroupChange: (String) -> Unit,
    drafts: MutableMap<String, Int>,
) {
    val groups = listOf("全部", "A", "B", "C", "D", "E", "F", "G", "H", "M")
    val visible = stocks.filter { group == "全部" || it.color.group == group }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            EntryStat(drafts.size.toString(), "待录色号")
            EntryStat(drafts.values.sum().toString(), "录入后合计")
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(groups) { item ->
            FilterChip(
                selected = group == item,
                onClick = { onGroupChange(item) },
                label = { Text(item) },
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 14.dp),
    ) {
        items(visible, key = { it.color.code }) { stock ->
            BatchStockRow(
                stock = stock,
                draft = drafts[stock.color.code],
                onDraftChange = { value ->
                    if (value == null) drafts.remove(stock.color.code) else drafts[stock.color.code] = value
                },
            )
        }
    }
}

@Composable
private fun EntryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = GrapePurple, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BatchStockRow(stock: BeadStock, draft: Int?, onDraftChange: (Int?) -> Unit) {
    val swatch = runCatching { Color(parseColor(stock.color.hex)) }.getOrDefault(Color.LightGray)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(swatch, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(11.dp).background(Color.White, CircleShape))
        }
        Column(Modifier.width(58.dp).padding(start = 8.dp)) {
            Text(stock.color.code, fontWeight = FontWeight.Bold)
            Text(
                stock.quantity?.let { "$it" } ?: "未录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        OutlinedTextField(
            value = draft?.toString().orEmpty(),
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(7)
                onDraftChange(digits.toIntOrNull())
            },
            modifier = Modifier.width(82.dp),
            placeholder = { Text("数量") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(500, 1000, 1200).forEach { amount ->
                Text(
                    "+$amount",
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val base = draft ?: stock.quantity ?: 0
                            onDraftChange(base + amount)
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(9.dp))
                        .padding(vertical = 10.dp),
                    color = GrapePurple,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PackageEntryContent(
    amount: String,
    onAmountChange: (String) -> Unit,
    operation: PackageOperation,
    onOperationChange: (PackageOperation) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = BerryPink)
                    Text("MARD 221 全色套装", modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "一次处理 A–H 与 M 系列全部 221 个色号。",
                    modifier = Modifier.padding(top = 7.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("每色数量", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    suffix = { Text("颗") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("如何写入", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PackageOperation.entries.forEach { item ->
                        FilterChip(
                            selected = operation == item,
                            onClick = { onOperationChange(item) },
                            label = { Text(item.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    if (operation == PackageOperation.ADD) {
                        "在现有数量上增加；未录入色号从 0 开始。适合新买一套豆。"
                    } else {
                        "把每个色号直接改成填写数量。适合第一次建立豆库。"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
