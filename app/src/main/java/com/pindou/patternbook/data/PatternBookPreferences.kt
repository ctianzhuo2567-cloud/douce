package com.pindou.patternbook.data

import android.content.Context
import com.pindou.patternbook.mard.MardPaletteVersion

class PatternBookPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("pattern_book_settings", Context.MODE_PRIVATE)

    var mardPaletteVersion: MardPaletteVersion
        get() = runCatching {
            MardPaletteVersion.valueOf(
                preferences.getString(KEY_MARD_VERSION, MardPaletteVersion.MARD_221.name)
                    ?: MardPaletteVersion.MARD_221.name,
            )
        }.getOrDefault(MardPaletteVersion.MARD_221)
        set(value) {
            preferences.edit().putString(KEY_MARD_VERSION, value.name).apply()
        }

    private companion object {
        const val KEY_MARD_VERSION = "mard_palette_version"
    }
}

