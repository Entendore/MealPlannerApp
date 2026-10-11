package com.example.mealplanner.util

enum class UnitType { VOLUME, WEIGHT, COUNT }

data class UnitDef(
    val key: String,             // canonical stored form, e.g. "ml", "cup"
    val type: UnitType,
    val mlPerUnit: Double = 0.0, // VOLUME only
    val gPerUnit: Double = 0.0,  // WEIGHT only
    val plural: String = key,    // display when quantity > 1
    val aliases: List<String> = emptyList()
)

object Units {

    // US customary volumes; cup = 236.59 ml (see Notes re: US vs UK cups)
    val VOLUME = listOf(
        UnitDef("ml", UnitType.VOLUME, mlPerUnit = 1.0,
            aliases = listOf("milliliter", "milliliters", "millilitre", "millilitres")),
        UnitDef("cl", UnitType.VOLUME, mlPerUnit = 10.0,
            aliases = listOf("centiliter", "centiliters", "centilitre", "centilitres")),
        UnitDef("dl", UnitType.VOLUME, mlPerUnit = 100.0,
            aliases = listOf("deciliter", "deciliters", "decilitre", "decilitres")),
        UnitDef("l", UnitType.VOLUME, mlPerUnit = 1000.0,
            aliases = listOf("liter", "liters", "litre", "litres")),
        UnitDef("tsp", UnitType.VOLUME, mlPerUnit = 4.93,
            aliases = listOf("teaspoon", "teaspoons")),
        UnitDef("tbsp", UnitType.VOLUME, mlPerUnit = 14.79,
            aliases = listOf("tablespoon", "tablespoons")),
        UnitDef("fl oz", UnitType.VOLUME, mlPerUnit = 29.57, plural = "fl oz",
            aliases = listOf("floz", "fluid ounce", "fluid ounces")),
        UnitDef("cup", UnitType.VOLUME, mlPerUnit = 236.59, plural = "cups",
            aliases = listOf("cups")),
        UnitDef("pint", UnitType.VOLUME, mlPerUnit = 473.18, plural = "pints",
            aliases = listOf("pints", "pt")),
        UnitDef("quart", UnitType.VOLUME, mlPerUnit = 946.35, plural = "quarts",
            aliases = listOf("quarts", "qt")),
        UnitDef("gallon", UnitType.VOLUME, mlPerUnit = 3785.41, plural = "gallons",
            aliases = listOf("gallons", "gal"))
    )

    val WEIGHT = listOf(
        UnitDef("g", UnitType.WEIGHT, gPerUnit = 1.0,
            aliases = listOf("gram", "grams", "gramme", "grammes")),
        UnitDef("kg", UnitType.WEIGHT, gPerUnit = 1000.0,
            aliases = listOf("kilogram", "kilograms", "kilo", "kilos")),
        UnitDef("oz", UnitType.WEIGHT, gPerUnit = 28.35,
            aliases = listOf("ounce", "ounces")),
        UnitDef("lb", UnitType.WEIGHT, gPerUnit = 453.59, plural = "lbs",
            aliases = listOf("lbs", "pound", "pounds"))
    )

    val COUNT = listOf(
        UnitDef("pc", UnitType.COUNT, plural = "pcs",
            aliases = listOf("pcs", "piece", "pieces", "unit", "units", "item", "items")),
        UnitDef("clove", UnitType.COUNT, plural = "cloves", aliases = listOf("cloves")),
        UnitDef("pinch", UnitType.COUNT, plural = "pinches", aliases = listOf("pinches")),
        UnitDef("slice", UnitType.COUNT, plural = "slices", aliases = listOf("slices")),
        UnitDef("can", UnitType.COUNT, plural = "cans", aliases = listOf("cans", "tin", "tins")),
        UnitDef("bottle", UnitType.COUNT, plural = "bottles", aliases = listOf("bottles")),
        UnitDef("pack", UnitType.COUNT, plural = "packs",
            aliases = listOf("packs", "packet", "packets", "bag", "bags")),
        UnitDef("head", UnitType.COUNT, plural = "heads", aliases = listOf("heads"))
    )

    val ALL = VOLUME + WEIGHT + COUNT

    private val lookup: Map<String, UnitDef> = buildMap {
        ALL.forEach { def ->
            put(def.key, def)
            def.aliases.forEach { put(it.lowercase(), def) }
        }
    }

    /** "Cups" → cup, "LBS" → lb, "cloves" → clove. Unknown text → null (stays free-form). */
    fun resolve(raw: String): UnitDef? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        lookup[s]?.let { return it }
        if (s.endsWith("es")) lookup[s.dropLast(2)]?.let { return it }
        if (s.endsWith("s")) lookup[s.dropLast(1)]?.let { return it }
        return null
    }

    fun typeOf(raw: String): UnitType? = resolve(raw)?.type

    /** "2" + cup → "2 cups"; "½" + cup → "½ cup"; abbreviations never pluralize. */
    fun displayForm(def: UnitDef, quantity: Double): String =
        if (quantity <= 1.0 + 1e-9) def.key else def.plural

    fun suggestions(query: String, limit: Int = 8): List<UnitDef> {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) listOf("ml", "l", "g", "kg", "cup", "tbsp", "tsp", "pc")
        else return ALL.filter { def ->
            def.key.contains(q) || def.aliases.any { it.contains(q) }
        }
        return base.mapNotNull { lookup[it] }.take(limit)
    }
}
