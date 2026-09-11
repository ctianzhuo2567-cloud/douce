package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.data.PatternStatus
import com.pindou.patternbook.ui.components.UriImage
import com.pindou.patternbook.ui.theme.BerryPink
import com.pindou.patternbook.ui.theme.GrapePurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val UNTAGGED_FILTER = "__untagged__"

@Composable
fun LibraryScreen(
    patterns: List<PatternItem>,
    onImport: () -> Unit,
    onOpenPattern: (PatternItem) -> Unit,
    onToggleFavorite: (PatternItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<PatternStatus?>(null) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    val availableTags = patterns.flatMap { it.tags }.distinct().sorted()

    val visible = patterns.filter { item ->
        val matchesQuery = query.isBlank() ||
            item.title.contains(query, ignoreCase = true) ||
            item.tags.any { it.contains(query, ignoreCase = true) }
        val matchesStatus = statusFilter == null || item.status == statusFilter
        val matchesTag = when (tagFilter) {
            null -> true
            UNTAGGED_FILTER -> item.tags.isEmpty()
            else -> tagFilter in item.tags
        }
        val matchesFavorite = !favoriteOnly || item.isFavorite
        matchesQuery && matchesStatus && matchesTag && matchesFavorite
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(patterns = patterns, onImport = onImport)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                placeholder = { Text("搜索图纸或标签") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "标签",
                    modifier = Modifier.padding(start = 20.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = tagFilter == null && !favoriteOnly,
                            onClick = {
                                tagFilter = null
                                favoriteOnly = false
                            },
                            label = { Text("全部") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = favoriteOnly,
                            onClick = { favoriteOnly = !favoriteOnly },
                            label = { Text("收藏") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = tagFilter == UNTAGGED_FILTER,
                            onClick = { tagFilter = UNTAGGED_FILTER },
                            label = { Text("无标签") },
                        )
                    }
                    items(availableTags.size) { index ->
                        val tag = availableTags[index]
                        FilterChip(
                            selected = tagFilter == tag,
                            onClick = { tagFilter = tag },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "制作状态",
                    modifier = Modifier.padding(start = 20.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("全部") },
                        )
                    }
                    items(PatternStatus.entries.size) { index ->
                        val status = PatternStatus.entries[index]
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = status },
                            label = { Text(status.label) },
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "显示 " + visible.size + " / " + patterns.size + " 张",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = GrapePurple,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyLibrary(hasPatterns = patterns.isNotEmpty(), onImport = onImport)
            }
        } else {
            items(visible, key = { it.id }) { item ->
                Box(
                    modifier = Modifier.padding(
                        start = if (visible.indexOf(item) % 2 == 0) 18.dp else 0.dp,
                        end = if (visible.indexOf(item) % 2 == 1) 18.dp else 0.dp,
                    ),
                ) {
                    PatternCard(
                        item = item,
                        onClick = { onOpenPattern(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(patterns: List<PatternItem>, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的图纸", style = MaterialTheme.typography.displaySmall)
                Text(
                    "MARD 221 图纸册",
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onImport, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Upload, contentDescription = null)
                Text("上传图纸", modifier = Modifier.padding(start = 6.dp))
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
                LibraryStat("全部", patterns.size, Modifier.weight(1f))
                LibraryStat("待制作", patterns.count { it.status == PatternStatus.READY }, Modifier.weight(1f))
                LibraryStat("制作中", patterns.count { it.status == PatternStatus.IN_PROGRESS }, Modifier.weight(1f))
                LibraryStat("已完成", patterns.count { it.status == PatternStatus.DONE }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryStat(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), color = GrapePurple, style = MaterialTheme.typography.titleLarge)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PatternCard(
    item: PatternItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            UriImage(
                uri = item.imageUri,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.92f),
                requestedSize = 720,
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (item.isFavorite) "取消收藏" else "收藏",
                    tint = BerryPink,
                )
            }
        }

        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusTicket(item.status.label)
                Text(
                    SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date(item.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusTicket(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun EmptyLibrary(hasPatterns: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (hasPatterns) "没有匹配的图纸" else "暂无图纸",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (hasPatterns) "换一个筛选条件试试" else "添加图纸后即可整理和识别",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasPatterns) {
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.padding(top = 18.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("上传图纸")
            }
        }
    }
}
