package com.example.mealplanner.util

import com.example.mealplanner.data.ShoppingItem
import kotlin.math.round

/** Merges compatible units per ingredient: volume via ml, weight via g. */
object ShoppingMerger {

    private class Acc {
        var base = 0.0
        var price = 0.0
        val allergens = mutableSetOf<String>()
        val defs = linkedSetOf<UnitDef>()
    }

    /** Merges volume units (via ml) and weight units (via g) per ingredient name. */
    fun merge(items: List<ShoppingItem>): List<ShoppingItem> {
        data class Key(val name: String, val family: String) // "vol" | "wt" | "raw:<unit>"

        val accs = linkedMapOf<Key, Acc>()
        items.forEach { item ->
            val def = Units.resolve(item.unit)
            val key = when (def?.type) {
                UnitType.VOLUME -> Key(item.name, "vol")
                UnitType.WEIGHT -> Key(item.name, "wt")
                else -> Key(item.name, "raw:" + item.unit.trim().lowercase())
            }
            val acc = accs.getOrPut(key) { Acc() }
            when (def?.type) {
                UnitType.VOLUME -> acc.base += item.totalQuantity * def.mlPerUnit
                UnitType.WEIGHT -> acc.base += item.totalQuantity * def.gPerUnit
                else -> acc.base += item.totalQuantity
            }
            acc.price += item.totalPrice
            acc.allergens += item.allergens.split(',').filter { it.isNotBlank() }
            if (def != null) acc.defs += def
        }

        return accs.map { (key, acc) ->
            when (key.family) {
                "vol" -> {
                    val (unitKey, factor) = pick(acc.defs, acc.base) { it.mlPerUnit }
                    ShoppingItem(key.name, unitKey, round2(acc.base / factor), round2(acc.price),
                        acc.allergens.sorted().joinToString(","))
                }
                "wt" -> {
                    val (unitKey, factor) = pick(acc.defs, acc.base) { it.gPerUnit }
                    ShoppingItem(key.name, unitKey, round2(acc.base / factor), round2(acc.price),
                        acc.allergens.sorted().joinToString(","))
                }
                else -> {
                    val original = items.first {
                        it.name == key.name && it.unit.trim().lowercase() == key.family.removePrefix("raw:")
                    }
                    ShoppingItem(key.name, original.unit, round2(acc.base), round2(acc.price),
                        acc.allergens.sorted().joinToString(","))
                }
            }
        }.sortedBy { it.name.lowercase() }
    }

    /** Largest used unit that keeps the value ≥ 1; smallest used unit as fallback. */
    private fun pick(defs: Set<UnitDef>, base: Double, factor: (UnitDef) -> Double): Pair<String, Double> {
        val sorted = defs.sortedByDescending(factor)
        val chosen = sorted.firstOrNull { base / factor(it) >= 1.0 - 1e-9 } ?: sorted.last()
        return chosen.key to factor(chosen)
    }

    private fun round2(x: Double) = round(x * 100) / 100
}
