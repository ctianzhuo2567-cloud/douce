package com.pindou.patternbook.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BerryPaperColorScheme = lightColorScheme(
    primary = BerryPink,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SoftLilac,
    onPrimaryContainer = InkPlum,
    secondary = GrapePurple,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = ColorTokens.GrapeMist,
    onSecondaryContainer = InkPlum,
    tertiary = DustyRose,
    onTertiary = InkPlum,
    background = PaperPink,
    onBackground = InkPlum,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = InkPlum,
    surfaceVariant = SoftLilac,
    onSurfaceVariant = MutedPlum,
    outline = LineLilac,
    error = ColorTokens.Error,
)

private object ColorTokens {
    val GrapeMist = androidx.compose.ui.graphics.Color(0xFFF1EBF1)
    val Error = androidx.compose.ui.graphics.Color(0xFFB64B68)
}

@Composable
fun PatternBookTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PaperPink.toArgb()
            window.navigationBarColor = PaperPink.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = BerryPaperColorScheme,
        typography = PatternBookTypography,
        content = content,
    )
}
