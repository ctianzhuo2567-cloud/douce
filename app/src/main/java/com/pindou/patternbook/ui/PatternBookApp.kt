package com.pindou.patternbook.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pindou.patternbook.BuildConfig
import com.pindou.patternbook.data.BeadInventoryRepository
import com.pindou.patternbook.data.BeadStock
import com.pindou.patternbook.data.DataBackupManager
import com.pindou.patternbook.data.GridPattern
import com.pindou.patternbook.data.LocalPatternRepository
import com.pindou.patternbook.data.NormalizedCrop
import com.pindou.patternbook.data.PatternItem
import com.pindou.patternbook.data.RecognitionStatus
import com.pindou.patternbook.mard.MardPaletteVersion
import com.pindou.patternbook.recognition.OnDeviceMardRecognitionService
import com.pindou.patternbook.recognition.GridGeometrySuggestion
import com.pindou.patternbook.ui.screens.CropSelectionScreen
import com.pindou.patternbook.ui.screens.GridPatternEditorScreen
import com.pindou.patternbook.ui.screens.GridRecognitionSetupScreen
import com.pindou.patternbook.ui.screens.InventoryQuickEntryScreen
import com.pindou.patternbook.ui.screens.InventoryScreen
import com.pindou.patternbook.ui.screens.LibraryScreen
import com.pindou.patternbook.ui.screens.PatternDetailScreen
import com.pindou.patternbook.ui.screens.RecognitionReviewScreen
import com.pindou.patternbook.ui.screens.RecognitionScreen
import com.pindou.patternbook.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class AppSection(val label: String) {
    LIBRARY("图纸"),
    INVENTORY("豆仓"),
    RECOGNITION("识别"),
    SETTINGS("设置"),
}

private val DefaultGridCrop = NormalizedCrop(
    left = 0.03f,
    top = 0.08f,
    right = 0.97f,
    bottom = 0.82f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternBookApp() {
    val context = LocalContext.current
    val repository = remember { LocalPatternRepository(context) }
    val inventoryRepository = remember { BeadInventoryRepository(context) }
    val backupManager = remember { DataBackupManager(context) }
    val recognitionService = remember { OnDeviceMardRecognitionService(context) }
    val coroutineScope = rememberCoroutineScope()
    val patterns = remember { mutableStateListOf<PatternItem>().apply { addAll(repository.load()) } }
    val stocks = remember { mutableStateListOf<BeadStock>().apply { addAll(inventoryRepository.loadStocks()) } }
    val swatches = remember(stocks.size) {
        stocks.associate { stock ->
            stock.color.code to runCatching {
                Color(android.graphics.Color.parseColor(stock.color.hex))
            }.getOrDefault(Color.LightGray)
        }
    }

    var activeSection by rememberSaveable { mutableStateOf(AppSection.LIBRARY) }
    var selectedPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var cropPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var recognizeAfterCropPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var recognizingPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var gridCropPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var gridSetupPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var gridEditorPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGridCrop by remember { mutableStateOf<NormalizedCrop?>(null) }
    var pendingGridSuggestion by remember { mutableStateOf<GridGeometrySuggestion?>(null) }
    var gridSuggestingPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var gridRecognizingId by rememberSaveable { mutableStateOf<String?>(null) }
    var quickEntryOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
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
        reviewPatternId = null
        gridCropPatternId = null
        gridSetupPatternId = null
        gridEditorPatternId = null
        pendingGridCrop = null
        pendingGridSuggestion = null
        gridSuggestingPatternId = null
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
                backupMessage = "恢复完成：" + summary.patternCount + " 张图纸，" +
                    summary.ownedColorCount + " 个库存色号"
            }.onFailure { error ->
                backupMessage = "恢复失败：" + (error.message ?: "文件不可用")
            }
            backupBusy = false
        }
    }

    fun startLegendRecognition(pattern: PatternItem) {
        if (recognizingPatternId != null) return
        if (pattern.legendCrop == null) {
            recognizeAfterCropPatternId = pattern.id
            cropPatternId = pattern.id
            return
        }

        recognizingPatternId = pattern.id
        recognitionError = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    recognitionService.recognizeLegend(
                        imageUri = Uri.parse(pattern.imageUri),
                        paletteVersion = paletteVersion,
                        crop = pattern.legendCrop,
                    )
                }
            }
            result.onSuccess { recognized ->
                val current = patterns.firstOrNull { it.id == pattern.id } ?: pattern
                val updated = current.copy(
                    recognizedCodes = recognized.codes,
                    recognitionStatus = if (recognized.codes.isEmpty()) {
                        RecognitionStatus.NOT_STARTED
                    } else {
                        RecognitionStatus.NEEDS_REVIEW
                    },
                )
                updatePattern(updated)
                reviewWarnings = recognized.warnings
                reviewPatternId = pattern.id
            }.onFailure { error ->
                recognitionError = "色号识别失败：" + (error.message ?: "无法读取图片")
            }
            recognizingPatternId = null
        }
    }

    fun openGrid(pattern: PatternItem) {
        if (pattern.gridPattern != null) {
            gridEditorPatternId = pattern.id
        } else {
            pendingGridSuggestion = null
            gridCropPatternId = pattern.id
        }
    }

    fun startGridGeometrySuggestion(pattern: PatternItem, crop: NormalizedCrop) {
        if (gridSuggestingPatternId != null) return
        gridSuggestingPatternId = pattern.id
        pendingGridSuggestion = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    recognitionService.suggestGridGeometry(
                        imageUri = Uri.parse(pattern.imageUri),
                        crop = crop,
                    )
                }
            }
            if (gridSetupPatternId == pattern.id) {
                pendingGridSuggestion = result.getOrElse { error ->
                    GridGeometrySuggestion(
                        rows = null,
                        columns = null,
                        confidence = 0f,
                        warnings = listOf(error.message ?: "无法分析网格线"),
                    )
                }
            }
            gridSuggestingPatternId = null
        }
    }

    fun startGridRecognition(pattern: PatternItem, crop: NormalizedCrop, rows: Int, columns: Int) {
        if (gridRecognizingId != null) return
        gridRecognizingId = pattern.id
        recognitionError = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    recognitionService.recognizeGrid(
                        imageUri = Uri.parse(pattern.imageUri),
                        paletteVersion = paletteVersion,
                        crop = crop,
                        rows = rows,
                        columns = columns,
                    )
                }
            }
            result.onSuccess { recognized ->
                val grid = recognized.grid
                if (grid == null) {
                    recognitionError = recognized.warnings.joinToString(separator = "\n")
                } else {
                    val current = patterns.firstOrNull { it.id == pattern.id } ?: pattern
                    updatePattern(current.copy(gridPattern = grid))
                    pendingGridCrop = null
                    gridSetupPatternId = null
                    gridEditorPatternId = pattern.id
                }
            }.onFailure { error ->
                recognitionError = "网格识别失败：" + (error.message ?: "无法读取图片")
            }
            gridRecognizingId = null
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
                backupMessage = "备份完成：" + summary.patternCount + " 张图纸，" +
                    summary.ownedColorCount + " 个库存色号"
            }.onFailure { error ->
                backupMessage = "备份失败：" + (error.message ?: "无法写入文件")
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
            text = { Text("恢复备份会替换手机里现有的全部图纸、标签、制作状态和豆仓库存。此操作不能撤销。") },
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

    recognitionError?.let { message ->
        AlertDialog(
            onDismissRequest = { recognitionError = null },
            title = { Text("无法完成识别") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { recognitionError = null }) { Text("知道了") }
            },
        )
    }

    BackHandler(
        enabled = gridEditorPatternId != null ||
            gridSetupPatternId != null ||
            gridCropPatternId != null ||
            reviewPatternId != null ||
            quickEntryOpen ||
            cropPatternId != null ||
            selectedPatternId != null ||
            activeSection != AppSection.LIBRARY,
    ) {
        when {
            gridEditorPatternId != null -> gridEditorPatternId = null
            gridSetupPatternId != null -> {
                gridSetupPatternId = null
                pendingGridCrop = null
                pendingGridSuggestion = null
            }
            gridCropPatternId != null -> gridCropPatternId = null
            reviewPatternId != null -> reviewPatternId = null
            quickEntryOpen -> quickEntryOpen = false
            cropPatternId != null -> {
                cropPatternId = null
                recognizeAfterCropPatternId = null
            }
            selectedPatternId != null -> selectedPatternId = null
            else -> activeSection = AppSection.LIBRARY
        }
    }

    val cropPattern = cropPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (cropPattern != null) {
        CropSelectionScreen(
            pattern = cropPattern,
            onBack = {
                cropPatternId = null
                recognizeAfterCropPatternId = null
            },
            onSave = { crop ->
                val updated = cropPattern.copy(legendCrop = crop)
                updatePattern(updated)
                cropPatternId = null
                if (recognizeAfterCropPatternId == cropPattern.id) {
                    recognizeAfterCropPatternId = null
                    startLegendRecognition(updated)
                }
            },
        )
        return
    }

    val gridCropPattern = gridCropPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (gridCropPattern != null) {
        CropSelectionScreen(
            pattern = gridCropPattern,
            initialCrop = gridCropPattern.gridPattern?.crop ?: DefaultGridCrop,
            title = "框选网格区域",
            instruction = "只框住有行列格子的主体区域，不要包含标题、坐标数字和底部图例。",
            saveLabel = "下一步：填写网格尺寸",
            onBack = { gridCropPatternId = null },
            onSave = { crop ->
                pendingGridCrop = crop
                pendingGridSuggestion = null
                gridCropPatternId = null
                gridSetupPatternId = gridCropPattern.id
                startGridGeometrySuggestion(gridCropPattern, crop)
            },
        )
        return
    }

    val gridSetupPattern = gridSetupPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (gridSetupPattern != null) {
        GridRecognitionSetupScreen(
            pattern = gridSetupPattern,
            onBack = {
                gridSetupPatternId = null
                pendingGridCrop = null
                pendingGridSuggestion = null
            },
            onStart = { rows, columns ->
                startGridRecognition(
                    pattern = gridSetupPattern,
                    crop = pendingGridCrop ?: DefaultGridCrop,
                    rows = rows,
                    columns = columns,
                )
            },
            suggestion = pendingGridSuggestion,
            detecting = gridSuggestingPatternId == gridSetupPattern.id,
            onRetryDetection = {
                startGridGeometrySuggestion(
                    pattern = gridSetupPattern,
                    crop = pendingGridCrop ?: DefaultGridCrop,
                )
            },
            recognizing = gridRecognizingId == gridSetupPattern.id,
        )
        return
    }

    val reviewPattern = reviewPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (reviewPattern != null) {
        RecognitionReviewScreen(
            pattern = reviewPattern,
            warnings = reviewWarnings,
            swatches = swatches,
            paletteVersion = paletteVersion,
            onBack = { reviewPatternId = null },
            onConfirm = {
                updatePattern(reviewPattern.copy(recognitionStatus = RecognitionStatus.CONFIRMED))
                reviewPatternId = null
            },
            onRecognizeAgain = {
                reviewPatternId = null
                startLegendRecognition(reviewPattern)
            },
            onResultsChange = { results ->
                val current = patterns.firstOrNull { it.id == reviewPattern.id } ?: reviewPattern
                updatePattern(
                    current.copy(
                        recognizedCodes = results,
                        recognitionStatus = if (results.isEmpty()) {
                            RecognitionStatus.NOT_STARTED
                        } else {
                            RecognitionStatus.NEEDS_REVIEW
                        },
                    ),
                )
            },
        )
        return
    }

    val gridEditorPattern = gridEditorPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    val grid = gridEditorPattern?.gridPattern
    if (gridEditorPattern != null && grid != null) {
        GridPatternEditorScreen(
            pattern = gridEditorPattern,
            grid = grid,
            swatches = swatches,
            onBack = { gridEditorPatternId = null },
            onSave = { updatedGrid: GridPattern ->
                updatePattern(gridEditorPattern.copy(gridPattern = updatedGrid))
                gridEditorPatternId = null
            },
        )
        return
    }

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

    val selectedPattern = selectedPatternId?.let { id -> patterns.firstOrNull { it.id == id } }
    if (selectedPattern != null) {
        PatternDetailScreen(
            pattern = selectedPattern,
            paletteVersion = paletteVersion,
            onBack = { selectedPatternId = null },
            onSelectLegend = {
                recognizeAfterCropPatternId = null
                cropPatternId = selectedPattern.id
            },
            onRecognize = { startLegendRecognition(selectedPattern) },
            recognizing = recognizingPatternId == selectedPattern.id,
            onOpenRecognitionReview = {
                reviewWarnings = emptyList()
                reviewPatternId = selectedPattern.id
            },
            onOpenGrid = { openGrid(selectedPattern) },
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
                    onRecognize = ::startLegendRecognition,
                    recognizingId = recognizingPatternId,
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
                        exportBackupLauncher.launch("豆册备份-" + timestamp + ".zip")
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
