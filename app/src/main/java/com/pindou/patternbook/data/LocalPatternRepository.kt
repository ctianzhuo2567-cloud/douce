package com.pindou.patternbook.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
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
        val knownUris = existing.mapTo(mutableSetOf()) { it.imageUri }
        return uris.mapNotNull { uri ->
            if (!knownUris.add(uri.toString())) return@mapNotNull null

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            PatternItem(
                id = UUID.randomUUID().toString(),
                title = displayName(uri).substringBeforeLast('.').ifBlank { "未命名图纸" },
                imageUri = uri.toString(),
                createdAt = System.currentTimeMillis(),
            )
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
    }
}
