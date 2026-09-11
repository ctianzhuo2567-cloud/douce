package com.pindou.patternbook.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UriImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    requestedSize: Int = 900,
    contentScale: ContentScale = ContentScale.Crop,
    imageModifier: Modifier = Modifier,
    onImageSize: (IntSize) -> Unit = {},
) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, requestedSize) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sampleSize = calculateSampleSize(
                    original = IntSize(bounds.outWidth, bounds.outHeight),
                    requestedSize = requestedSize,
                )
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    LaunchedEffect(bitmap) {
        bitmap?.let { onImageSize(IntSize(it.width, it.height)) }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = imageModifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

private fun calculateSampleSize(original: IntSize, requestedSize: Int): Int {
    if (original.width <= 0 || original.height <= 0) return 1
    var sample = 1
    val longest = maxOf(original.width, original.height)
    while (longest / (sample * 2) >= requestedSize) sample *= 2
    return sample
}
