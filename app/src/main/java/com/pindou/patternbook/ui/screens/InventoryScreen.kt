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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.BeadStock
import com.pindou.patternbook.ui.theme.BerryPink
import com.pindou.patternbook.ui.theme.GrapePurple

private enum class InventoryFilter(val label: String) {
    ALL("全部"),
    OWNED("有库存"),
    LOW("快用完"),
    EMPTY("缺货"),
    UNTRACKED("未录入"),
}

@Composable
fun InventoryScreen(
    stocks: List<BeadStock>,
    onSetQuantity: (BeadStock, Int) -> Unit,
    onOpenQuickEntry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(InventoryFilter.ALL) }
    var group by rememberSaveable { mutableStateOf("全部") }
    var editingCode by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingCode?.let { code -> stocks.firstOrNull { it.color.code == code } }

    val visible = stocks.filter { stock ->
        val matchesQuery = query.isBlank() || stock.color.code.contains(query.trim(), ignoreCase = true)
        val matchesGroup = group == "全部" || stock.color.group == group
        val matchesFilter = when (filter) {
            InventoryFilter.ALL -> true
            InventoryFilter.OWNED -> stock.isOwned
            InventoryFilter.LOW -> stock.isLow
            InventoryFilter.EMPTY -> stock.isEmpty
            InventoryFilter.UNTRACKED -> stock.isUntracked
        }
        matchesQuery && matchesGroup && matchesFilter
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            InventoryHeader(stocks, onOpenQuickEntry)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.uppercase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                placeholder = { Text("搜索色号，例如 A4") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(InventoryFilter.entries.size) { index ->
                    val item = InventoryFilter.entries[index]
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) },
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val groups = listOf("全部", "A", "B", "C", "D", "E", "F", "G", "H", "M")
                items(groups.size) { index ->
                    val item = groups[index]
                    FilterChip(
                        selected = group == item,
                        onClick = { group = item },
                        label = { Text(item) },
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "显示 " + visible.size + " / " + stocks.size + " 个色号",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                color = GrapePurple,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有符合条件的色号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(visible, key = { it.color.code }) { stock ->
                Box(
                    modifier = Modifier.padding(
                        start = if (visible.indexOf(stock) % 2 == 0) 18.dp else 0.dp,
                        end = if (visible.indexOf(stock) % 2 == 1) 18.dp else 0.dp,
                    ),
                ) {
                    StockCard(
                        stock = stock,
                        onClick = { editingCode = stock.color.code },
                        onAdjust = { delta -> onSetQuantity(stock, (stock.quantity ?: 0) + delta) },
                    )
                }
            }
        }
    }

    editing?.let { stock ->
        QuantityDialog(
            stock = stock,
            onDismiss = { editingCode = null },
            onSave = { quantity ->
                onSetQuantity(stock, quantity)
                editingCode = null
            },
        )
    }
}

@Composable
private fun InventoryHeader(stocks: List<BeadStock>, onOpenQuickEntry: () -> Unit) {
    val owned = stocks.count { it.isOwned }
    val low = stocks.count { it.isLow }
    val total = stocks.sumOf { it.quantity ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的豆仓", style = MaterialTheme.typography.displaySmall)
                Text(
                    "MARD 221 · 按颗记录",
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenQuickEntry, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                Text("补豆入库", modifier = Modifier.padding(start = 6.dp))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp),
            ) {
                InventoryStat("有库存", owned.toString() + "/221", GrapePurple, Modifier.weight(1f))
                InventoryStat("快用完", low.toString(), BerryPink, Modifier.weight(1f))
                InventoryStat("总库存", total.toString() + " 颗", GrapePurple, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InventoryStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StockCard(stock: BeadStock, onClick: () -> Unit, onAdjust: (Int) -> Unit) {
    val swatch = parseSwatchColor(stock.color.hex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stock.color.code, style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        stock.isUntracked -> "未录入"
                        stock.isEmpty -> "缺货"
                        stock.isLow -> "快用完"
                        else -> "库存充足"
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = if (stock.isLow) BerryPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(swatch, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                Text(
                    (stock.quantity ?: 0).toString() + " 颗",
                    color = contrastTextColor(swatch),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onAdjust(-100) }, enabled = (stock.quantity ?: 0) > 0) {
                    Icon(Icons.Rounded.Remove, contentDescription = "减少100颗")
                }
                IconButton(onClick = { onAdjust(100) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "增加100颗", tint = GrapePurple)
                }
            }
        }
    }
}

@Composable
private fun QuantityDialog(stock: BeadStock, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable(stock.color.code, stock.quantity) {
        mutableStateOf(stock.quantity?.toString().orEmpty())
    }
    val value = text.toIntOrNull()?.coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stock.color.code + " 库存") },
        text = {
            Column {
                Text("按颗记录；不确定时可以填大约数量。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(7) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("现有数量") },
                    suffix = { Text("颗") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0, 100, 500, 1000).forEach { amount ->
                        TextButton(onClick = { text = amount.toString() }) {
                            Text(amount.toString())
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { value?.let(onSave) }, enabled = value != null) {
                Text("保存数量")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun contrastTextColor(color: Color): Color {
    val brightness = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (brightness > 0.58f) Color.Black else Color.White
}

private fun parseSwatchColor(hex: String): Color =
    runCatching { Color(parseColor(hex)) }.getOrDefault(Color.LightGray)
