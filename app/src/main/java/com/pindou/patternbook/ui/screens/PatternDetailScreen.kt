package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.data.PatternStatus
import com.pindou.patternbook.mard.MardPaletteVersion
import com.pindou.patternbook.ui.components.UriImage
import com.pindou.patternbook.ui.theme.BerryPink
import com.pindou.patternbook.ui.theme.GrapePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternDetailScreen(
    pattern: PatternItem,
    paletteVersion: MardPaletteVersion,
    onBack: () -> Unit,
    onSelectLegend: () -> Unit,
    onUpdate: (PatternItem) -> Unit,
) {
    var scale by rememberSaveable(pattern.id) { mutableFloatStateOf(1f) }
    var offset by remember(pattern.id) { mutableStateOf(Offset.Zero) }
    var showEditDialog by rememberSaveable(pattern.id) { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 7f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(pattern.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑图纸信息", tint = GrapePurple)
                    }
                    IconButton(onClick = { onUpdate(pattern.copy(isFavorite = !pattern.isFavorite)) }) {
                        Icon(
                            if (pattern.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (pattern.isFavorite) "取消收藏" else "收藏",
                            tint = BerryPink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .transformable(transformState),
                    contentAlignment = Alignment.Center,
                ) {
                    UriImage(
                        uri = pattern.imageUri,
                        contentDescription = pattern.title,
                        modifier = Modifier.fillMaxSize(),
                        requestedSize = 2200,
                        contentScale = ContentScale.Fit,
                        imageModifier = Modifier.graphicsLayer {
                            scaleX = scale * if (pattern.mirrorHorizontal) -1f else 1f
                            scaleY = scale * if (pattern.mirrorVertical) -1f else 1f
                            translationX = offset.x
                            translationY = offset.y
                        },
                    )
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MirrorButton(
                    label = "左右镜像",
                    selected = pattern.mirrorHorizontal,
                    icon = { Icon(Icons.Rounded.Flip, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUpdate(pattern.copy(mirrorHorizontal = !pattern.mirrorHorizontal))
                        offset = Offset.Zero
                    },
                )
                MirrorButton(
                    label = "上下镜像",
                    selected = pattern.mirrorVertical,
                    icon = { Icon(Icons.Rounded.SwapVert, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUpdate(pattern.copy(mirrorVertical = !pattern.mirrorVertical))
                        offset = Offset.Zero
                    },
                )
            }

            Button(
                onClick = onSelectLegend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.CropFree, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (pattern.legendCrop == null) "框选色号说明" else "调整色号说明区域")
            }

            if (pattern.tags.isNotEmpty() || pattern.note.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (pattern.tags.isNotEmpty()) {
                            Text(
                                "标签：${pattern.tags.joinToString(" · ")}",
                                style = MaterialTheme.typography.labelLarge,
                                color = GrapePurple,
                            )
                        }
                        if (pattern.note.isNotBlank()) {
                            Text(
                                pattern.note,
                                modifier = Modifier.padding(top = if (pattern.tags.isEmpty()) 0.dp else 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                "当前按 ${paletteVersion.label} 校验；整图镜像只影响预览，色号说明区域单独保存。",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PatternStatus.entries.size) { index ->
                    val status = PatternStatus.entries[index]
                    FilterChip(
                        selected = pattern.status == status,
                        onClick = { onUpdate(pattern.copy(status = status)) },
                        label = { Text(status.label) },
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        PatternEditDialog(
            pattern = pattern,
            onDismiss = { showEditDialog = false },
            onSave = { title, tags, note ->
                onUpdate(pattern.copy(title = title, tags = tags, note = note))
                showEditDialog = false
            },
        )
    }
}

@Composable
private fun PatternEditDialog(
    pattern: PatternItem,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String) -> Unit,
) {
    var title by rememberSaveable(pattern.id) { mutableStateOf(pattern.title) }
    var tags by rememberSaveable(pattern.id) { mutableStateOf(pattern.tags.joinToString("，")) }
    var note by rememberSaveable(pattern.id) { mutableStateOf(pattern.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("整理图纸") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签") },
                    supportingText = { Text("用逗号分开，例如：甜品，小尺寸") },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedTags = tags.split(',', '，')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    onSave(title.trim().ifBlank { "未命名图纸" }, parsedTags, note.trim())
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MirrorButton(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) GrapePurple else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.size(7.dp))
        Text(label)
    }
}
