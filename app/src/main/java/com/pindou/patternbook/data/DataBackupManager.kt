package com.pindou.patternbook.data

import android.content.Context
import android.net.Uri
import com.pindou.patternbook.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val patternCount: Int,
    val ownedColorCount: Int,
)

class DataBackupManager(private val context: Context) {
    private val patternPreferences =
        context.getSharedPreferences(PATTERN_PREFERENCES, Context.MODE_PRIVATE)
    private val inventoryPreferences =
        context.getSharedPreferences(INVENTORY_PREFERENCES, Context.MODE_PRIVATE)
    private val settingsPreferences =
        context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    fun exportTo(destination: Uri): BackupSummary {
        val sourcePatterns = JSONArray(
            patternPreferences.getString(KEY_PATTERNS, "[]") ?: "[]",
        )
        val exportedPatterns = JSONArray()

        val outputStream = context.contentResolver.openOutputStream(destination, "w")
            ?: error("无法创建备份文件")
        ZipOutputStream(outputStream.buffered()).use { zip ->
            repeat(sourcePatterns.length()) { index ->
                val source = sourcePatterns.getJSONObject(index)
                val exported = JSONObject(source.toString())
                val imageUri = Uri.parse(source.getString("imageUri"))
                val imageName = "images/${index}_${safeName(source.optString("id"))}.${imageExtension(imageUri)}"

                zip.putNextEntry(ZipEntry(imageName))
                val imageInput = context.contentResolver.openInputStream(imageUri)
                    ?: error("无法读取图纸：${source.optString("title", "未命名图纸")}")
                imageInput.buffered().use { it.copyTo(zip) }
                zip.closeEntry()

                exported.put("imageUri", imageName)
                exportedPatterns.put(exported)
            }

            val manifest = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("application", "豆册")
                put("appVersion", BuildConfig.VERSION_NAME)
                put("createdAt", System.currentTimeMillis())
                put("patterns", exportedPatterns)
                put("inventoryPreferences", preferencesToJson(inventoryPreferences.all))
                put("settingsPreferences", preferencesToJson(settingsPreferences.all))
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return BackupSummary(
            patternCount = exportedPatterns.length(),
            ownedColorCount = ownedColorCount(inventoryPreferences.all[KEY_QUANTITIES] as? String),
        )
    }

    fun restoreFrom(source: Uri): BackupSummary {
        val stagingRoot = File(context.cacheDir, "douce_restore_staging")
        stagingRoot.deleteRecursively()
        check(stagingRoot.mkdirs()) { "无法准备恢复空间" }

        try {
            extractValidatedZip(source, stagingRoot)
            val manifestFile = File(stagingRoot, MANIFEST_NAME)
            require(manifestFile.isFile) { "备份中缺少 manifest.json" }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optInt("schemaVersion") == SCHEMA_VERSION) {
                "暂不支持这个备份版本"
            }

            val restoredPatterns = manifest.getJSONArray("patterns")
            val replacementDir = File(context.filesDir, "pattern_images_restore")
            replacementDir.deleteRecursively()
            check(replacementDir.mkdirs()) { "无法准备图纸恢复空间" }

            repeat(restoredPatterns.length()) { index ->
                val item = restoredPatterns.getJSONObject(index)
                val relativeName = item.getString("imageUri")
                require(relativeName.startsWith("images/")) { "备份图纸路径无效" }
                val stagedImage = safeChild(stagingRoot, relativeName)
                require(stagedImage.isFile) { "备份缺少第 ${index + 1} 张图纸" }

                val extension = stagedImage.extension.ifBlank { "img" }
                val target = File(replacementDir, "${index}_${UUID.randomUUID()}.$extension")
                stagedImage.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                item.put("imageUri", Uri.fromFile(target).toString())
            }

            installRestoredImages(replacementDir, restoredPatterns)

            val inventory = manifest.optJSONObject("inventoryPreferences") ?: JSONObject()
            val settings = manifest.optJSONObject("settingsPreferences") ?: JSONObject()
            check(replaceStringPreferences(inventoryPreferences, inventory)) {
                "无法恢复豆库数据"
            }
            check(replaceStringPreferences(settingsPreferences, settings)) {
                "无法恢复设置"
            }
            check(
                patternPreferences.edit()
                    .putString(KEY_PATTERNS, restoredPatterns.toString())
                    .commit(),
            ) { "无法恢复图纸信息" }

            return BackupSummary(
                patternCount = restoredPatterns.length(),
                ownedColorCount = ownedColorCount(inventory.optString(KEY_QUANTITIES)),
            )
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun installRestoredImages(replacementDir: File, patterns: JSONArray) {
        val currentDir = File(context.filesDir, PATTERN_IMAGE_DIRECTORY)
        val previousDir = File(context.filesDir, "pattern_images_previous")
        previousDir.deleteRecursively()

        if (currentDir.exists() && !currentDir.renameTo(previousDir)) {
            replacementDir.deleteRecursively()
            error("无法替换现有图纸")
        }
        if (!replacementDir.renameTo(currentDir)) {
            previousDir.renameTo(currentDir)
            error("无法安装恢复的图纸")
        }

        repeat(patterns.length()) { index ->
            val item = patterns.getJSONObject(index)
            val temporaryFile = File(Uri.parse(item.getString("imageUri")).path!!)
            item.put("imageUri", Uri.fromFile(File(currentDir, temporaryFile.name)).toString())
        }
        previousDir.deleteRecursively()
    }

    private fun extractValidatedZip(source: Uri, destination: File) {
        val inputStream = context.contentResolver.openInputStream(source)
            ?: error("无法读取备份文件")
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ENTRIES) { "备份文件项目过多" }
                val target = safeChild(destination, entry.name)
                if (entry.isDirectory) {
                    check(target.mkdirs() || target.isDirectory) { "无法创建恢复目录" }
                } else {
                    check(target.parentFile?.mkdirs() != false) { "无法创建恢复目录" }
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "备份文件过大" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun safeChild(root: File, relativeName: String): File {
        val child = File(root, relativeName)
        val rootPath = root.canonicalPath + File.separator
        require(child.canonicalPath.startsWith(rootPath)) { "备份包含不安全路径" }
        return child
    }

    private fun preferencesToJson(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            when (value) {
                is String, is Boolean, is Int, is Long, is Float -> put(key, value)
            }
        }
    }

    private fun replaceStringPreferences(
        preferences: android.content.SharedPreferences,
        values: JSONObject,
    ): Boolean {
        val editor = preferences.edit().clear()
        values.keys().forEach { key -> editor.putString(key, values.get(key).toString()) }
        return editor.commit()
    }

    private fun ownedColorCount(rawQuantities: String?): Int {
        if (rawQuantities.isNullOrBlank()) return 0
        return runCatching {
            val quantities = JSONObject(rawQuantities)
            quantities.keys().asSequence().count { quantities.optInt(it) > 0 }
        }.getOrDefault(0)
    }

    private fun imageExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        return when {
            mimeType.endsWith("png") -> "png"
            mimeType.endsWith("webp") -> "webp"
            mimeType.endsWith("gif") -> "gif"
            else -> "jpg"
        }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { UUID.randomUUID().toString() }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MANIFEST_NAME = "manifest.json"
        const val PATTERN_PREFERENCES = "pattern_library"
        const val INVENTORY_PREFERENCES = "mard_221_inventory"
        const val SETTINGS_PREFERENCES = "pattern_book_settings"
        const val PATTERN_IMAGE_DIRECTORY = "pattern_images"
        const val KEY_PATTERNS = "patterns"
        const val KEY_QUANTITIES = "quantities"
        const val MAX_ENTRIES = 1_000
        const val MAX_UNCOMPRESSED_BYTES = 1_500_000_000L
    }
}
