package com.pindou.patternbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pindou.patternbook.ui.PatternBookApp
import com.pindou.patternbook.ui.theme.PatternBookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PatternBookTheme {
                PatternBookApp()
            }
        }
    }
}

