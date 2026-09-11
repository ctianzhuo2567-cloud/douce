package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.patternbook.data.NormalizedCrop
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.ui.components.UriImage
import com.pindou.patternbook.ui.theme.BerryPink
import kotlin.math.roundToInt

private const val MIN_CROP_SIZE = 0.08f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropSelectionScreen(
    pattern: PatternItem,
    onBack: () -> Unit,
    onSave: (NormalizedCrop) -> Unit,
) {
    var crop by remember(pattern.id) { mutableStateOf(pattern.legendCrop ?: NormalizedCrop.DEFAULT) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val imageRect = fittedImageRect(viewport, imageSize)
    val density = LocalDensity.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("框选色号说明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
            Text(
                "拖动粉色框移动区域，拖动两个圆点调整大小。尽量只保留色号与数量。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewport = it },
                ) {
                    UriImage(
                        uri = pattern.imageUri,
                        contentDescription = pattern.title,
                        modifier = Modifier.fillMaxSize(),
                        requestedSize = 2400,
                        contentScale = ContentScale.Fit,
                        onImageSize = { imageSize = it },
                    )

                    if (imageRect.width > 0f && imageRect.height > 0f) {
                        val left = imageRect.left + crop.left * imageRect.width
                        val top = imageRect.top + crop.top * imageRect.height
                        val right = imageRect.left + crop.right * imageRect.width
                        val bottom = imageRect.top + crop.bottom * imageRect.height
                        val shade = Color(0x990F0715)

                        Box(Modifier.fillMaxWidth().height(with(density) { top.toDp() }).background(shade))
                        Box(
                            Modifier
                                .offset { IntOffset(0, bottom.roundToInt()) }
                                .fillMaxWidth()
                                .height(with(density) { (viewport.height - bottom).coerceAtLeast(0f).toDp() })
                                .background(shade),
                        )
                        Box(
                            Modifier
                                .offset { IntOffset(0, top.roundToInt()) }
                                .size(
                                    width = with(density) { left.toDp() },
                                    height = with(density) { (bottom - top).toDp() },
                                )
                                .background(shade),
                        )
                        Box(
                            Modifier
                                .offset { IntOffset(right.roundToInt(), top.roundToInt()) }
                                .size(
                                    width = with(density) { (viewport.width - right).coerceAtLeast(0f).toDp() },
                                    height = with(density) { (bottom - top).toDp() },
                                )
                                .background(shade),
                        )

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                                .size(
                                    width = with(density) { (right - left).toDp() },
                                    height = with(density) { (bottom - top).toDp() },
                                )
                                .border(3.dp, BerryPink, RoundedCornerShape(8.dp))
                                .pointerInput(imageRect, crop) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val newLeft = (crop.left + drag.x / imageRect.width)
                                            .coerceIn(0f, 1f - crop.width)
                                        val newTop = (crop.top + drag.y / imageRect.height)
                                            .coerceIn(0f, 1f - crop.height)
                                        crop = crop.copy(
                                            left = newLeft,
                                            top = newTop,
                                            right = newLeft + crop.width,
                                            bottom = newTop + crop.height,
                                        )
                                    }
                                },
                        ) {
                            ResizeHandle(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset((-11).dp, (-11).dp)
                                    .pointerInput(imageRect, crop) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            crop = crop.copy(
                                                left = (crop.left + drag.x / imageRect.width)
                                                    .coerceIn(0f, crop.right - MIN_CROP_SIZE),
                                                top = (crop.top + drag.y / imageRect.height)
                                                    .coerceIn(0f, crop.bottom - MIN_CROP_SIZE),
                                            )
                                        }
                                    },
                            )
                            ResizeHandle(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(11.dp, 11.dp)
                                    .pointerInput(imageRect, crop) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            crop = crop.copy(
                                                right = (crop.right + drag.x / imageRect.width)
                                                    .coerceIn(crop.left + MIN_CROP_SIZE, 1f),
                                                bottom = (crop.bottom + drag.y / imageRect.height)
                                                    .coerceIn(crop.top + MIN_CROP_SIZE, 1f),
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onSave(crop) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("保存说明区域")
            }
        }
    }
}

@Composable
private fun ResizeHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .background(Color.White, CircleShape)
            .border(4.dp, BerryPink, CircleShape),
    )
}

private fun fittedImageRect(viewport: IntSize, image: IntSize): Rect {
    if (viewport.width == 0 || viewport.height == 0 || image.width == 0 || image.height == 0) {
        return Rect.Zero
    }
    val viewportRatio = viewport.width.toFloat() / viewport.height
    val imageRatio = image.width.toFloat() / image.height
    return if (imageRatio > viewportRatio) {
        val height = viewport.width / imageRatio
        val top = (viewport.height - height) / 2f
        Rect(0f, top, viewport.width.toFloat(), top + height)
    } else {
        val width = viewport.height * imageRatio
        val left = (viewport.width - width) / 2f
        Rect(left, 0f, left + width, viewport.height.toFloat())
    }
}
