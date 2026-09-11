package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    patternCount: Int,
    ownedColorCount: Int,
    versionName: String,
    backupBusy: Boolean,
    backupMessage: String?,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.displaySmall)
        Text(
            "资料保存在本机，可手动导出完整备份",
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsCard(title = "MARD 色号目录") {
            Text(
                "固定使用 MARD 221：A–H 与 M 系列，共 221 个色号。屏幕颜色仅供查找，实物对色以实体色卡为准。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "备份与恢复") {
            Text("已保存 $patternCount 张图纸", style = MaterialTheme.typography.titleMedium)
            Text("豆库已录入 $ownedColorCount / 221 色", style = MaterialTheme.typography.titleMedium)
            Text(
                "备份包含图纸原图、标签、制作状态、镜像设置和豆库库存。",
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onExportBackup,
                    enabled = !backupBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                    Text(" 导出备份")
                }
                OutlinedButton(
                    onClick = onImportBackup,
                    enabled = !backupBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                    Text(" 恢复备份")
                }
            }
            if (backupBusy) {
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("正在处理，请不要关闭应用")
                }
            }
            backupMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "恢复会在确认后覆盖手机里现有的图纸和库存。建议把 ZIP 同时保存到私人网盘或电脑。",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "当前版本") {
            Text("豆册 $versionName · 正式签名版", style = MaterialTheme.typography.titleMedium)
            Text(
                "全新安装只包含 MARD221 色号目录，不预置图纸或库存。",
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
