package com.pindou.patternbook.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalPatternRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("pattern_library", Context.MODE_PRIVATE)

    fun load(): List<PatternItem> {
        val raw = preferences.getString(KEY_PATTERNS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    add(array.getJSONObject(index).toPatternItem())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun importUris(uris: List<Uri>, existing: List<PatternItem>): List<PatternItem> {
        val knownNames = existing.mapTo(mutableSetOf()) { it.title }
        val imageDirectory = File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }

        return uris.mapNotNull { sourceUri ->
            runCatching {
                val id = UUID.randomUUID().toString()
                val displayName = displayName(sourceUri)
                val extension = displayName.substringAfterLast('.', "jpg")
                    .lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                    ?: "jpg"
                val target = File(imageDirectory, "$id.$extension")
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: error("无法读取所选图片")
                input.buffered().use { source ->
                    target.outputStream().buffered().use { output -> source.copyTo(output) }
                }

                val baseTitle = displayName.substringBeforeLast('.').ifBlank { "未命名图纸" }
                var title = baseTitle
                var suffix = 2
                while (!knownNames.add(title)) {
                    title = "$baseTitle（${suffix++}）"
                }

                PatternItem(
                    id = id,
                    title = title,
                    imageUri = Uri.fromFile(target).toString(),
                    createdAt = System.currentTimeMillis(),
                )
            }.getOrNull()
        }
    }

    fun save(items: List<PatternItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PATTERNS, array.toString()).apply()
    }

    private fun displayName(uri: Uri): String {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    private fun PatternItem.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("imageUri", imageUri)
        put("createdAt", createdAt)
        put("status", status.name)
        put("recognitionStatus", recognitionStatus.name)
        put("isFavorite", isFavorite)
        put("mirrorHorizontal", mirrorHorizontal)
        put("mirrorVertical", mirrorVertical)
        legendCrop?.let { crop ->
            put("legendCrop", JSONObject().apply {
                put("left", crop.left.toDouble())
                put("top", crop.top.toDouble())
                put("right", crop.right.toDouble())
                put("bottom", crop.bottom.toDouble())
            })
        }
        put("tags", JSONArray(tags))
        put("note", note)
    }

    private fun JSONObject.toPatternItem() = PatternItem(
        id = getString("id"),
        title = getString("title"),
        imageUri = getString("imageUri"),
        createdAt = getLong("createdAt"),
        status = enumValueOrDefault(optString("status"), PatternStatus.TO_SORT),
        recognitionStatus = enumValueOrDefault(
            optString("recognitionStatus"),
            RecognitionStatus.NOT_STARTED,
        ),
        isFavorite = optBoolean("isFavorite"),
        mirrorHorizontal = optBoolean("mirrorHorizontal"),
        mirrorVertical = optBoolean("mirrorVertical"),
        legendCrop = optJSONObject("legendCrop")?.let { crop ->
            NormalizedCrop(
                left = crop.optDouble("left", 0.08).toFloat(),
                top = crop.optDouble("top", 0.72).toFloat(),
                right = crop.optDouble("right", 0.92).toFloat(),
                bottom = crop.optDouble("bottom", 0.96).toFloat(),
            )
        },
        tags = optJSONArray("tags")?.let { array ->
            buildList {
                repeat(array.length()) { index -> add(array.getString(index)) }
            }
        }.orEmpty(),
        note = optString("note"),
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_PATTERNS = "patterns"
        const val IMAGE_DIRECTORY = "pattern_images"
    }
}
