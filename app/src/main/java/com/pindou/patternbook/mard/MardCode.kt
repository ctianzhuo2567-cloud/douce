package com.pindou.patternbook.mard

enum class MardPaletteVersion(val label: String) {
    MARD_221("MARD 221"),
    MARD_291("MARD 291"),
}

object MardCode {
    private val standard221 = linkedMapOf(
        "A" to 26,
        "B" to 32,
        "C" to 29,
        "D" to 26,
        "E" to 24,
        "F" to 25,
        "G" to 21,
        "H" to 23,
        "M" to 15,
    )
    private val extended291 = linkedMapOf(
        "P" to 23,
        "Q" to 5,
        "R" to 28,
        "T" to 1,
        "Y" to 5,
        "ZG" to 8,
    )

    fun normalize(raw: String, version: MardPaletteVersion): String? {
        val compact = raw
            .uppercase()
            .replace("MARD", "")
            .replace(Regex("[^A-Z0-9]"), "")
        val match = Regex("^([A-Z]{1,2})(\\d{1,2})$").matchEntire(compact) ?: return null
        val series = match.groupValues[1]
        val number = match.groupValues[2].toIntOrNull() ?: return null
        val limits = if (version == MardPaletteVersion.MARD_221) {
            standard221
        } else {
            standard221 + extended291
        }
        val maximum = limits[series] ?: return null
        return if (number in 1..maximum) "$series$number" else null
    }
}

