package com.pindou.patternbook.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pindou.patternbook.recognition.RecognizedMardCode
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
                    title = baseTitle + "（" + (suffix++) + "）"
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
        check(preferences.edit().putString(KEY_PATTERNS, array.toString()).commit()) {
            "无法保存图纸信息"
        }
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
            put("legendCrop", crop.toJson())
        }
        put("tags", JSONArray(tags))
        put("note", note)
        put("recognizedCodes", JSONArray().apply {
            recognizedCodes.forEach { result ->
                put(JSONObject().apply {
                    put("code", result.code)
                    result.quantity?.let { put("quantity", it) }
                    put("confidence", result.confidence.toDouble())
                    put("sourceText", result.sourceText)
                })
            }
        })
        gridPattern?.let { grid ->
            put("gridPattern", JSONObject().apply {
                put("rows", grid.rows)
                put("columns", grid.columns)
                put("crop", grid.crop.toJson())
                put("cells", JSONArray().apply {
                    grid.cells.forEach { cell ->
                        put(JSONObject().apply {
                            put("row", cell.row)
                            put("column", cell.column)
                            cell.code?.let { put("code", it) }
                            put("confidence", cell.confidence.toDouble())
                        })
                    }
                })
            })
        }
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
        legendCrop = optJSONObject("legendCrop")?.toCrop(),
        tags = optJSONArray("tags")?.let { array ->
            buildList {
                repeat(array.length()) { index -> add(array.getString(index)) }
            }
        }.orEmpty(),
        note = optString("note"),
        recognizedCodes = optJSONArray("recognizedCodes")?.let { array ->
            buildList {
                repeat(array.length()) { index ->
                    val result = array.getJSONObject(index)
                    add(
                        RecognizedMardCode(
                            code = result.getString("code"),
                            quantity = if (result.has("quantity")) result.optInt("quantity") else null,
                            confidence = result.optDouble("confidence", 0.0).toFloat(),
                            sourceText = result.optString("sourceText"),
                        ),
                    )
                }
            }
        }.orEmpty(),
        gridPattern = optJSONObject("gridPattern")?.let { grid ->
            val rows = grid.optInt("rows", 1)
            val columns = grid.optInt("columns", 1)
            GridPattern(
                rows = rows,
                columns = columns,
                crop = grid.optJSONObject("crop")?.toCrop() ?: NormalizedCrop.DEFAULT,
                cells = grid.optJSONArray("cells")?.let { array ->
                    buildList {
                        repeat(array.length()) { index ->
                            val cell = array.getJSONObject(index)
                            add(
                                GridCell(
                                    row = cell.optInt("row"),
                                    column = cell.optInt("column"),
                                    code = cell.optString("code").ifBlank { null },
                                    confidence = cell.optDouble("confidence", 0.0).toFloat(),
                                ),
                            )
                        }
                    }
                }.orEmpty(),
            ).normalized()
        },
    )

    private fun NormalizedCrop.toJson() = JSONObject().apply {
        put("left", left.toDouble())
        put("top", top.toDouble())
        put("right", right.toDouble())
        put("bottom", bottom.toDouble())
    }

    private fun JSONObject.toCrop() = NormalizedCrop(
        left = optDouble("left", 0.08).toFloat(),
        top = optDouble("top", 0.72).toFloat(),
        right = optDouble("right", 0.92).toFloat(),
        bottom = optDouble("bottom", 0.96).toFloat(),
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_PATTERNS = "patterns"
        const val IMAGE_DIRECTORY = "pattern_images"
    }
}
