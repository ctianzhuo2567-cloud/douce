package com.pindou.patternbook.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BeadInventoryRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("mard_221_inventory", Context.MODE_PRIVATE)

    fun loadStocks(): List<BeadStock> {
        val quantities = readQuantities()
        val updatedAt = readUpdatedAt()
        return loadCatalog().map { color ->
            BeadStock(
                color = color,
                quantity = quantities[color.code],
                updatedAt = updatedAt[color.code] ?: 0L,
            )
        }
    }

    fun setQuantity(current: BeadStock, quantity: Int): BeadStock {
        return applyQuantities(listOf(current), mapOf(current.color.code to quantity)).first()
    }

    fun applyQuantities(
        currentStocks: List<BeadStock>,
        targetQuantities: Map<String, Int>,
    ): List<BeadStock> {
        if (targetQuantities.isEmpty()) return currentStocks
        val now = System.currentTimeMillis()
        val currentByCode = currentStocks.associateBy { it.color.code }
        val storedQuantities = readQuantities()
        val safeTargets = targetQuantities.mapValues { (_, quantity) -> quantity.coerceAtLeast(0) }
        val changes = safeTargets.mapNotNull { (code, quantityAfter) ->
            val quantityBefore = currentByCode[code]?.quantity ?: storedQuantities[code] ?: 0
            if (quantityAfter == quantityBefore && currentByCode[code]?.quantity != null) return@mapNotNull null
            StockAdjustment(
                code = code,
                delta = quantityAfter - quantityBefore,
                quantityAfter = quantityAfter,
                occurredAt = now,
            )
        }
        if (changes.isEmpty()) return currentStocks

        val quantities = storedQuantities.toMutableMap()
        val updatedAt = readUpdatedAt().toMutableMap()
        changes.forEach { change ->
            quantities[change.code] = change.quantityAfter
            updatedAt[change.code] = now
        }
        preferences.edit()
            .putString(KEY_QUANTITIES, quantities.toJson().toString())
            .putString(KEY_UPDATED_AT, updatedAt.toJson().toString())
            .putString(KEY_ADJUSTMENTS, prependAdjustments(changes).toString())
            .apply()
        val changedCodes = changes.associateBy { it.code }
        return currentStocks.map { stock ->
            changedCodes[stock.color.code]?.let { change ->
                stock.copy(quantity = change.quantityAfter, updatedAt = now)
            } ?: stock
        }
    }

    fun loadRecentAdjustments(limit: Int = 20): List<StockAdjustment> {
        val raw = preferences.getString(KEY_ADJUSTMENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(minOf(limit, array.length())) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        StockAdjustment(
                            code = item.getString("code"),
                            delta = item.getInt("delta"),
                            quantityAfter = item.getInt("quantityAfter"),
                            occurredAt = item.getLong("occurredAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadCatalog(): List<MardColor> = context.assets.open("mard221.json")
        .bufferedReader()
        .use { reader ->
            val colors = JSONObject(reader.readText()).getJSONArray("colors")
            buildList {
                repeat(colors.length()) { index ->
                    val color = colors.getJSONObject(index)
                    add(
                        MardColor(
                            code = color.getString("code"),
                            hex = color.getString("hex"),
                            group = color.getString("group"),
                        ),
                    )
                }
            }
        }

    private fun readQuantities(): Map<String, Int> = readObject(KEY_QUANTITIES) { getInt(it) }

    private fun readUpdatedAt(): Map<String, Long> = readObject(KEY_UPDATED_AT) { getLong(it) }

    private fun <T> readObject(key: String, value: JSONObject.(String) -> T): Map<String, T> {
        val raw = preferences.getString(key, null) ?: return emptyMap()
        return runCatching {
            val objectValue = JSONObject(raw)
            objectValue.keys().asSequence().associateWith { keyName -> value(objectValue, keyName) }
        }.getOrDefault(emptyMap())
    }

    private fun Map<String, *>.toJson() = JSONObject().apply {
        this@toJson.forEach { (key, value) -> put(key, value) }
    }

    private fun prependAdjustments(adjustments: List<StockAdjustment>): JSONArray {
        val result = JSONArray()
        adjustments.asReversed().forEach { adjustment -> result.put(adjustment.toJson()) }
        val existing = preferences.getString(KEY_ADJUSTMENTS, null)?.let(::JSONArray)
        if (existing != null) {
            val remaining = (MAX_HISTORY - result.length()).coerceAtLeast(0)
            repeat(minOf(existing.length(), remaining)) { index -> result.put(existing.get(index)) }
        }
        return result
    }

    private fun StockAdjustment.toJson() = JSONObject().apply {
        put("code", code)
        put("delta", delta)
        put("quantityAfter", quantityAfter)
        put("occurredAt", occurredAt)
    }

    private companion object {
        const val KEY_QUANTITIES = "quantities"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_ADJUSTMENTS = "adjustments"
        const val MAX_HISTORY = 100
    }
}
