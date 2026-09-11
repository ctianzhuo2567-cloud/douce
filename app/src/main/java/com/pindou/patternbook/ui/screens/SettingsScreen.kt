package com.pindou.patternbook.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    patternCount: Int,
    ownedColorCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.displaySmall)
        Text(
            "图纸、豆库和识别结果只保存在本机",
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

        SettingsCard(title = "本地资料") {
            Text("已保存 $patternCount 张图纸", style = MaterialTheme.typography.titleMedium)
            Text("豆库已录入 $ownedColorCount / 221 色", style = MaterialTheme.typography.titleMedium)
            Text(
                "完整 ZIP 备份与恢复将在下一步接入。",
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "当前版本") {
            Text("豆册 0.3.1 · 莓果纸艺主题", style = MaterialTheme.typography.titleMedium)
            Text(
                "图纸组合筛选、预览镜像、色号框选与 MARD 221 快捷入库已经建立。",
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
