package com.example.mealplanner.util

import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.RecipeWithIngredients

object AllergenUtil {

    /** Canonical keys — stored in the DB, never localized in data. */
    val ALLERGENS = listOf(
        "Gluten", "Dairy", "Eggs", "Nuts", "Peanuts", "Soy", "Fish", "Shellfish", "Sesame"
    )
    val DIETS = listOf(
        "Vegetarian", "Vegan", "Pescatarian", "Gluten-free", "Dairy-free", "Keto"
    )

    /** Token-level keyword table. Matched as whole words (lowercased). */
    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "Gluten" to listOf(
            "flour", "wheat", "barley", "rye", "bread", "baguette", "pasta", "spaghetti",
            "penne", "macaroni", "noodle", "noodles", "semolina", "couscous", "crouton",
            "croutons", "biscuit", "croissant", "tortilla", "oats", "oat", "granola"
        ),
        "Dairy" to listOf(
            "milk", "cheese", "butter", "cream", "yogurt", "yoghurt", "mozzarella",
            "parmesan", "feta", "cheddar", "gouda", "ricotta", "mascarpone", "whey",
            "lactose", "custard", "ghee"
        ),
        "Eggs" to listOf("egg", "eggs", "mayonnaise", "mayo", "meringue", "omelette"),
        "Nuts" to listOf(
            "almond", "almonds", "walnut", "walnuts", "cashew", "cashews", "hazelnut",
            "hazelnuts", "pecan", "pecans", "pistachio", "pistachios", "macadamia",
            "marzipan", "praline"
        ),
        "Peanuts" to listOf("peanut", "peanuts", "satay"),
        "Soy" to listOf("soy", "soya", "soybean", "edamame", "tofu", "tempeh", "miso", "tamari"),
        "Fish" to listOf(
            "fish", "salmon", "tuna", "cod", "trout", "anchovy", "anchovies", "sardine",
            "sardines", "mackerel", "haddock"
        ),
        "Shellfish" to listOf(
            "shrimp", "shrimps", "prawn", "prawns", "crab", "lobster", "crayfish", "clam",
            "clams", "mussel", "mussels", "oyster", "oysters", "squid", "octopus",
            "scallop", "scallops"
        ),
        "Sesame" to listOf("sesame", "tahini")
    )

    /** "peanut butter" / "cocoa butter" / "apple butter": "butter" alone doesn't imply Dairy. */
    private val BUTTER_EXCEPTIONS = setOf("peanut", "peanuts", "cocoa", "cacao", "apple")

    private val tokenRegex = Regex("[a-z]+")

    fun detectAllergens(ingredientName: String): List<String> {
        val tokens = tokenRegex.findAll(ingredientName.lowercase()).toSet()
        return ALLERGENS.filter { allergen ->
            KEYWORDS.getValue(allergen).any { keyword ->
                if (keyword == "butter" && allergen == "Dairy" &&
                    tokens.any { it in BUTTER_EXCEPTIONS }
                ) false
                else keyword in tokens
            }
        }
    }

    /** Union of all ingredient allergens on a recipe. */
    val RecipeWithIngredients.allergenSet: Set<String>
        get() = ingredients.flatMap { it.allergens }.toSet()

    /** Allergens the profile forbids that this recipe contains. */
    fun RecipeWithIngredients.allergenClash(profile: DietaryProfile): Set<String> =
        allergenSet intersect profile.allergies.toSet()

    /** Active profile diets the recipe does NOT satisfy. */
    fun RecipeWithIngredients.dietClash(profile: DietaryProfile): List<String> =
        profile.diets.filter { it !in recipe.diets }

    fun RecipeWithIngredients.hasClash(profile: DietaryProfile): Boolean =
        allergenClash(profile).isNotEmpty() || dietClash(profile).isNotEmpty()
}
