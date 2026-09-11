package com.pindou.patternbook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pindou.patternbook.BuildConfig
import com.pindou.patternbook.data.BeadInventoryRepository
import com.pindou.patternbook.data.BeadStock
import com.pindou.patternbook.data.DataBackupManager
import com.pindou.patternbook.data.LocalPatternRepository
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.mard.MardPaletteVersion
import com.pindou.patternbook.ui.screens.CropSelectionScreen
import com.pindou.patternbook.ui.screens.InventoryQuickEntryScreen
import com.pindou.patternbook.ui.screens.InventoryScreen
import com.pindou.patternbook.ui.screens.LibraryScreen
import com.pindou.patternbook.ui.screens.PatternDetailScreen
import com.pindou.patternbook.ui.screens.RecognitionScreen
import com.pindou.patternbook.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class AppSection(val label: String) {
    LIBRARY("图纸"),
    INVENTORY("豆库"),
    RECOGNITION("识别"),
    SETTINGS("设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternBookApp() {
    val context = LocalContext.current
    val repository = remember { LocalPatternRepository(context) }
    val inventoryRepository = remember { BeadInventoryRepository(context) }
    val backupManager = remember { DataBackupManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val patterns = remember { mutableStateListOf<PatternItem>().apply { addAll(repository.load()) } }
    val stocks = remember { mutableStateListOf<BeadStock>().apply { addAll(inventoryRepository.loadStocks()) } }

    var activeSection by rememberSaveable { mutableStateOf(AppSection.LIBRARY) }
    var selectedPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var cropPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var quickEntryOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val paletteVersion = MardPaletteVersion.MARD_221

    fun persist() = repository.save(patterns)
    fun updatePattern(updated: PatternItem) {
        val index = patterns.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            patterns[index] = updated
            persist()
        }
    }
    fun setStockQuantity(stock: BeadStock, quantity: Int) {
        val index = stocks.indexOfFirst { it.color.code == stock.color.code }
        if (index >= 0) stocks[index] = inventoryRepository.setQuantity(stocks[index], quantity)
    }
    fun applyStockQuantities(targets: Map<String, Int>) {
        val updated = inventoryRepository.applyQuantities(stocks, targets)
        updated.forEachIndexed { index, stock -> stocks[index] = stock }
    }
    fun refreshAfterRestore() {
        selectedPatternId = null
        cropPatternId = null
        patterns.clear()
        patterns.addAll(repository.load())
        stocks.clear()
        stocks.addAll(inventoryRepository.loadStocks())
    }
    fun restoreBackup(uri: Uri) {
        backupBusy = true
        backupMessage = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { backupManager.restoreFrom(uri) }
            }
            result.onSuccess { summary ->
                refreshAfterRestore()
                backupMessage = "恢复完成：${summary.patternCount} 张图纸，${summary.ownedColorCount} 个库存色号"
            }.onFailure { error ->
                backupMessage = "恢复失败：${error.message ?: "文件不可用"}"
            }
            backupBusy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val existingPatterns = patterns.toList()
        coroutineScope.launch {
            val imported = withContext(Dispatchers.IO) {
                repository.importUris(uris, existingPatterns)
            }
            if (imported.isNotEmpty()) {
                patterns.addAll(0, imported)
                persist()
            }
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        backupMessage = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { backupManager.exportTo(uri) }
            }
            result.onSuccess { summary ->
                backupMessage = "备份完成：${summary.patternCount} 张图纸，${summary.ownedColorCount} 个库存色号"
            }.onFailure { error ->
                backupMessage = "备份失败：${error.message ?: "无法写入文件"}"
            }
            backupBusy = false
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("覆盖当前资料？") },
            text = { Text("恢复备份会替换手机里现有的全部图纸、标签、制作状态和豆库库存。此操作不能撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        restoreBackup(uri)
                    },
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            },
        )
    }

    val selectedPattern = selectedPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    val cropPattern = cropPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (quickEntryOpen) {
        InventoryQuickEntryScreen(
            stocks = stocks,
            onBack = { quickEntryOpen = false },
            onApply = { targets ->
                applyStockQuantities(targets)
                quickEntryOpen = false
            },
        )
        return
    }
    if (cropPattern != null) {
        CropSelectionScreen(
            pattern = cropPattern,
            onBack = { cropPatternId = null },
            onSave = { crop ->
                updatePattern(cropPattern.copy(legendCrop = crop))
                cropPatternId = null
            },
        )
        return
    }
    if (selectedPattern != null) {
        PatternDetailScreen(
            pattern = selectedPattern,
            paletteVersion = paletteVersion,
            onBack = { selectedPatternId = null },
            onSelectLegend = { cropPatternId = selectedPattern.id },
            onUpdate = ::updatePattern,
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppSection.entries.forEach { section ->
                    val icon = when (section) {
                        AppSection.LIBRARY -> Icons.Rounded.CollectionsBookmark
                        AppSection.INVENTORY -> Icons.Rounded.Inventory2
                        AppSection.RECOGNITION -> Icons.Rounded.DocumentScanner
                        AppSection.SETTINGS -> Icons.Rounded.Settings
                    }
                    NavigationBarItem(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        icon = { Icon(icon, contentDescription = section.label) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeSection == AppSection.LIBRARY) {
                FloatingActionButton(
                    onClick = { importLauncher.launch(arrayOf("image/*")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "导入图纸")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (activeSection) {
                AppSection.LIBRARY -> LibraryScreen(
                    patterns = patterns,
                    onImport = { importLauncher.launch(arrayOf("image/*")) },
                    onOpenPattern = { selectedPatternId = it.id },
                    onToggleFavorite = { updatePattern(it.copy(isFavorite = !it.isFavorite)) },
                )
                AppSection.INVENTORY -> InventoryScreen(
                    stocks = stocks,
                    onSetQuantity = ::setStockQuantity,
                    onOpenQuickEntry = { quickEntryOpen = true },
                )
                AppSection.RECOGNITION -> RecognitionScreen(
                    patterns = patterns,
                    onOpenPattern = { selectedPatternId = it.id },
                    onGoToLibrary = { activeSection = AppSection.LIBRARY },
                )
                AppSection.SETTINGS -> SettingsScreen(
                    patternCount = patterns.size,
                    ownedColorCount = stocks.count { it.isOwned },
                    versionName = BuildConfig.VERSION_NAME,
                    backupBusy = backupBusy,
                    backupMessage = backupMessage,
                    onExportBackup = {
                        val timestamp = LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"),
                        )
                        exportBackupLauncher.launch("豆册备份-$timestamp.zip")
                    },
                    onImportBackup = {
                        restoreBackupLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream"),
                        )
                    },
                )
            }
        }
    }
}
