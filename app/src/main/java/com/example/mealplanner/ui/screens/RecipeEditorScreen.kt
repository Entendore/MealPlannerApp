package com.example.mealplanner.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mealplanner.R
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.ui.allergenLabel
import com.example.mealplanner.ui.components.UnitField
import com.example.mealplanner.ui.components.categoryIcon
import com.example.mealplanner.ui.currencySymbol
import com.example.mealplanner.ui.dietIcon
import com.example.mealplanner.ui.dietLabel
import com.example.mealplanner.ui.formatQuantity
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.BarcodeLookupResult
import com.example.mealplanner.util.RecipeImageStore
import com.example.mealplanner.util.RecipeQr
import com.example.mealplanner.util.SharedRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CATEGORIES = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Drink")

private val TAG_SUGGESTIONS = listOf(
    "One-pot", "30-min", "Kid-friendly", "Spicy", "Comfort food",
    "Meal prep", "Grill", "Slow cooker", "Freezer-friendly", "Under 10 ingredients"
)

private val INGREDIENT_SUGGESTIONS = listOf(
    "Milk" to "ml", "Water" to "ml", "Cream" to "ml", "Butter" to "g", "Flour" to "g",
    "Sugar" to "g", "Eggs" to "pc", "Rice" to "g", "Pasta" to "g", "Olive oil" to "tbsp",
    "Onion" to "pc", "Garlic" to "clove", "Tomato" to "pc", "Chicken breast" to "g",
    "Ground beef" to "g", "Salt" to "tsp", "Pepper" to "pinch", "Cheese" to "g",
    "Yogurt" to "g", "Potato" to "g", "Carrot" to "pc", "Lemon" to "pc", "Honey" to "tbsp",
    "Oats" to "g", "Spinach" to "g", "Mushrooms" to "g", "Parmesan" to "g",
    "Baking powder" to "tsp", "Vanilla extract" to "tsp"
)

private data class IngredientInput(
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val cost: String = "",
    val allergens: List<String> = emptyList()
)

/** Dirty-check snapshot. */
private data class EditorSnapshot(
    val name: String,
    val category: String,
    val prep: String,
    val cook: String,
    val servings: Int,
    val instructions: String,
    val photoPath: String?,
    val ingredients: List<IngredientInput>,
    val diets: List<String>,
    val tags: List<String>,
    val adaptedNote: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(
    recipeId: Int?,
    existing: RecipeWithIngredients?,
    onSave: (Recipe, List<Ingredient>) -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onBarcodeLookup: (String, (BarcodeLookupResult) -> Unit) -> Unit,
    scanResult: String?,
    onScanConsumed: () -> Unit
) {
    val ready = recipeId == null || existing?.recipe?.id == recipeId
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by remember { mutableStateOf(existing?.recipe?.photoPath) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val newPath = withContext(Dispatchers.IO) { RecipeImageStore.save(context, uri) }
            if (newPath != null) {
                RecipeImageStore.delete(photoPath)
                photoPath = newPath
            }
        }
    }

    var name by remember { mutableStateOf(existing?.recipe?.name ?: "") }
    var category by remember { mutableStateOf(existing?.recipe?.category ?: "Dinner") }
    var prepTime by remember {
        mutableStateOf(existing?.recipe?.prepTimeMinutes?.takeIf { it > 0 }?.toString() ?: "")
    }
    var cookTime by remember {
        mutableStateOf(existing?.recipe?.cookTimeMinutes?.takeIf { it > 0 }?.toString() ?: "")
    }
    var servings by remember { mutableIntStateOf(existing?.recipe?.servings?.takeIf { it > 0 } ?: 1) }
    var instructions by remember { mutableStateOf(existing?.recipe?.instructions ?: "") }
    var diets by remember { mutableStateOf(existing?.recipe?.diets ?: emptyList()) }
    var tags by remember { mutableStateOf(existing?.recipe?.tags ?: emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var adaptedNote by remember { mutableStateOf(existing?.recipe?.adaptedNote ?: "") }
    var ingredients by remember {
        mutableStateOf(
            existing?.ingredients?.map {
                IngredientInput(
                    it.name, formatQuantity(it.quantity), it.unit,
                    if (it.price > 0) formatQuantity(it.price) else "",
                    (it.allergens + AllergenUtil.detectAllergens(it.name)).distinct()
                )
            } ?: listOf(IngredientInput())
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addAllergenFor by remember { mutableStateOf<Int?>(null) }

    val initial = remember(existing) {
        EditorSnapshot(
            name = existing?.recipe?.name?.trim() ?: "",
            category = existing?.recipe?.category ?: "Dinner",
            prep = existing?.recipe?.prepTimeMinutes?.takeIf { it > 0 }?.toString() ?: "",
            cook = existing?.recipe?.cookTimeMinutes?.takeIf { it > 0 }?.toString() ?: "",
            servings = existing?.recipe?.servings?.takeIf { it > 0 } ?: 1,
            instructions = existing?.recipe?.instructions ?: "",
            photoPath = existing?.recipe?.photoPath,
            ingredients = existing?.ingredients?.map {
                IngredientInput(
                    it.name, formatQuantity(it.quantity), it.unit,
                    if (it.price > 0) formatQuantity(it.price) else "",
                    (it.allergens + AllergenUtil.detectAllergens(it.name)).distinct()
                )
            } ?: listOf(IngredientInput()),
            diets = existing?.recipe?.diets ?: emptyList(),
            tags = existing?.recipe?.tags ?: emptyList(),
            adaptedNote = existing?.recipe?.adaptedNote ?: ""
        )
    }
    fun snapshot() = EditorSnapshot(
        name = name.trim(), category = category, prep = prepTime, cook = cookTime,
        servings = servings, instructions = instructions, photoPath = photoPath,
        ingredients = ingredients, diets = diets, tags = tags, adaptedNote = adaptedNote
    )
    val isDirty = snapshot() != initial

    var showDiscardDialog by remember { mutableStateOf(false) }
    fun requestBack() { if (isDirty) showDiscardDialog = true else onBack() }
    BackHandler(enabled = isDirty) { requestBack() }

    // ---- scan handling: recipe payloads import; barcodes look up on OFF ----
    var pendingImport by remember { mutableStateOf<SharedRecipe?>(null) }
    var lookingUp by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun applyImport(shared: SharedRecipe) {
        name = shared.recipe.name
        category = shared.recipe.category
        prepTime = shared.recipe.prepTimeMinutes.takeIf { it > 0 }?.toString() ?: ""
        cookTime = shared.recipe.cookTimeMinutes.takeIf { it > 0 }?.toString() ?: ""
        servings = shared.recipe.servings.takeIf { it > 0 } ?: 1
        instructions = shared.recipe.instructions
        diets = shared.recipe.diets
        tags = shared.recipe.tags
        adaptedNote = shared.recipe.adaptedNote
        photoPath = null
        ingredients = shared.ingredients
            .map {
                IngredientInput(
                    it.name, formatQuantity(it.quantity), it.unit, "",
                    (it.allergens + AllergenUtil.detectAllergens(it.name)).distinct()
                )
            }
            .ifEmpty { listOf(IngredientInput()) }
        toast(context.getString(R.string.imported_ok))
    }

    LaunchedEffect(scanResult) {
        val raw = scanResult ?: return@LaunchedEffect
        onScanConsumed()
        if (RecipeQr.isRecipePayload(raw)) {
            val decoded = RecipeQr.decode(raw)
            if (decoded == null) toast(context.getString(R.string.invalid_code))
            else pendingImport = decoded
        } else {
            lookingUp = true
            onBarcodeLookup(raw) { result ->
                lookingUp = false
                when (result) {
                    is BarcodeLookupResult.Found -> {
                        ingredients = ingredients + IngredientInput(
                            name = result.ingredient.name,
                            quantity = formatQuantity(result.ingredient.quantity),
                            unit = result.ingredient.unit,
                            allergens = (result.ingredient.allergens +
                                AllergenUtil.detectAllergens(result.ingredient.name)).distinct()
                        )
                        toast(context.getString(R.string.product_added, result.ingredient.name))
                    }
                    BarcodeLookupResult.NotFound -> {
                        ingredients = ingredients + IngredientInput(name = raw)
                        toast(context.getString(R.string.barcode_not_found_added))
                    }
                    BarcodeLookupResult.Error ->
                        toast(context.getString(R.string.lookup_failed))
                }
            }
        }
    }

    fun updateIngredient(index: Int, updated: IngredientInput) {
        val merged = if (updated.name.trim() != ingredients.getOrNull(index)?.name?.trim()) {
            // Name changed: re-run detection and union, so typing "milk" adds Dairy…
            // and renaming to "oat drink" stops re-suggesting it, so a removal sticks.
            updated.copy(
                allergens = (updated.allergens + AllergenUtil.detectAllergens(updated.name))
                    .distinct()
            )
        } else updated
        ingredients = ingredients.mapIndexed { i, item -> if (i == index) merged else item }
    }

    fun save() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            errorMessage = context.getString(R.string.error_name_required)
            return
        }
        onSave(
            Recipe(
                id = existing?.recipe?.id ?: 0,
                name = trimmedName,
                category = category,
                prepTimeMinutes = prepTime.toIntOrNull() ?: 0,
                cookTimeMinutes = cookTime.toIntOrNull() ?: 0,
                servings = servings,
                instructions = instructions.trim(),
                photoPath = photoPath,
                diets = diets,
                adaptedNote = adaptedNote.trim(),
                isFavorite = existing?.recipe?.isFavorite ?: false,
                tags = tags
            ),
            ingredients.filter { it.name.isNotBlank() }.map {
                Ingredient(
                    name = it.name.trim(),
                    quantity = it.quantity.toDoubleOrNull() ?: 1.0,
                    unit = it.unit.trim(),
                    price = it.cost.toDoubleOrNull() ?: 0.0,
                    allergens = it.allergens.distinct()
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (recipeId == null) stringResource(R.string.new_recipe)
                        else stringResource(R.string.edit_recipe)) + if (isDirty) " •" else ""
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth()
                        .apply { }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .androidx.compose.foundation.layout.NavigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { requestBack() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { save() },
                        modifier = Modifier.weight(2f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_recipe))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Photo ----------
            if (photoPath != null) {
                Box {
                    AsyncImage(
                        model = File(photoPath!!),
                        contentDescription = stringResource(R.string.change_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    )
                    IconButton(
                        onClick = { RecipeImageStore.delete(photoPath); photoPath = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_photo))
                    }
                }
            } else {
                OutlinedCard(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.add_photo),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---------- Details ----------
            FormSection(stringResource(R.string.section_details)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text(stringResource(R.string.recipe_name)) },
                    isError = errorMessage != null,
                    supportingText = { errorMessage?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.category),
                    style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            leadingIcon = {
                                Icon(categoryIcon(option), contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = prepTime,
                        onValueChange = { prepTime = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.prep_minutes)) },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cookTime,
                        onValueChange = { cookTime = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.cook_minutes)) },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.servings),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f))
                    FilledTonalIconButton(
                        onClick = { if (servings > 1) servings-- },
                        enabled = servings > 1
                    ) { Icon(Icons.Default.Remove, contentDescription = null) }
                    Text(
                        "$servings",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp)
                    )
                    FilledTonalIconButton(onClick = { servings++ }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            // ---------- Tags ----------
            FormSection(stringResource(R.string.tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_SUGGESTIONS.forEach { suggestion ->
                        FilterChip(
                            selected = suggestion in tags,
                            onClick = {
                                tags = if (suggestion in tags) tags - suggestion
                                else tags + suggestion
                            },
                            label = { Text(suggestion) }
                        )
                    }
                    tags.filter { it !in TAG_SUGGESTIONS }.forEach { custom ->
                        FilterChip(
                            selected = true,
                            onClick = { tags = tags - custom },
                            label = { Text(custom) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null,
                                    modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text(stringResource(R.string.add_tag)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clean = newTag.trim().replace(",", "").take(24)
                            if (clean.isNotEmpty() &&
                                tags.none { it.equals(clean, ignoreCase = true) }
                            ) {
                                tags = tags + clean
                            }
                            newTag = ""
                        }) {
                            Icon(Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_tag))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- Dietary info ----------
            FormSection(stringResource(R.string.dietary_info)) {
                Text(stringResource(R.string.my_diets),
                    style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllergenUtil.DIETS.forEach { option ->
                        FilterChip(
                            selected = option in diets,
                            onClick = {
                                diets = if (option in diets) diets - option else diets + option
                            },
                            leadingIcon = {
                                Icon(dietIcon(option), contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            },
                            label = { Text(dietLabel(option)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = adaptedNote,
                    onValueChange = { adaptedNote = it },
                    label = { Text(stringResource(R.string.adapted_for)) },
                    supportingText = { Text(stringResource(R.string.adapted_for_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- Ingredients ----------
            FormSection(
                title = stringResource(R.string.ingredients) +
                    "  ·  ${ingredients.count { it.name.isNotBlank() }}",
                action = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_code))
                    }
                }
            ) {
                val available = INGREDIENT_SUGGESTIONS
                    .filter { (ingName, _) ->
                        ingredients.none { it.name.equals(ingName, ignoreCase = true) }
                    }
                if (available.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(available) { (sugName, sugUnit) ->
                            SuggestionChip(
                                onClick = {
                                    val base = ingredients.filterIndexed { i, inp ->
                                        !(i == ingredients.lastIndex && inp.name.isBlank())
                                    }
                                    ingredients = base + IngredientInput(
                                        name = sugName, unit = sugUnit)
                                },
                                label = { Text("+ $sugName") }
                            )
                        }
                    }
                }
                if (lookingUp) LinearProgressIndicator(Modifier.fillMaxWidth())
                ingredients.forEachIndexed { index, input ->
                    OutlinedCard(shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = input.quantity,
                                    onValueChange = {
                                        updateIngredient(index, input.copy(
                                            quantity = it.filter { c -> c.isDigit() || c == '.' }))
                                    },
                                    label = { Text(stringResource(R.string.qty)) },
                                    singleLine = true,
                                    keyboardOptions =
                                        KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(70.dp)
                                )
                                UnitField(
                                    value = input.unit,
                                    onChange = {
                                        updateIngredient(index, input.copy(unit = it))
                                    },
                                    modifier = Modifier.width(110.dp)
                                )
                                OutlinedTextField(
                                    value = input.name,
                                    onValueChange = {
                                        updateIngredient(index, input.copy(name = it))
                                    },
                                    label = { Text(stringResource(R.string.ingredient)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    ingredients = ingredients.filterIndexed { i, _ -> i != index }
                                    if (ingredients.isEmpty()) ingredients = listOf(IngredientInput())
                                }) {
                                    Icon(Icons.Default.Close, contentDescription =
                                        stringResource(R.string.remove_ingredient))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = input.cost,
                                    onValueChange = {
                                        updateIngredient(index, input.copy(
                                            cost = it.filter { c -> c.isDigit() || c == '.' }))
                                    },
                                    label = { Text(stringResource(R.string.ingredient_cost)) },
                                    prefix = { Text(currencySymbol()) },
                                    singleLine = true,
                                    keyboardOptions =
                                        KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(150.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.actual_price_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Allergen override chips (authoritative — removals stick)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                input.allergens.forEach { tag ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            updateIngredient(index,
                                                input.copy(allergens = input.allergens - tag))
                                        },
                                        label = { Text(allergenLabel(tag)) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null,
                                                modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                                AssistChip(
                                    onClick = { addAllergenFor = index },
                                    label = { Text(stringResource(R.string.add_allergen)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null,
                                            modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { ingredients = ingredients + IngredientInput() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_ingredient))
                }
            }

            // ---------- Instructions ----------
            FormSection(stringResource(R.string.instructions)) {
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.instructions)) },
                    supportingText = { Text(stringResource(R.string.instructions_hint)) },
                    minLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }

    if (pendingImport != null) {
        val shared = pendingImport!!
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_recipe_title)) },
            text = { Text(stringResource(R.string.import_recipe_message, shared.recipe.name)) },
            confirmButton = {
                TextButton(onClick = { applyImport(shared); pendingImport = null }) {
                    Text(stringResource(R.string.import_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    addAllergenFor?.let { index ->
        val current = ingredients.getOrNull(index)?.allergens.orEmpty()
        AlertDialog(
            onDismissRequest = { addAllergenFor = null },
            title = { Text(stringResource(R.string.add_allergen_title)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllergenUtil.ALLERGENS.filter { it !in current }.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                updateIngredient(index,
                                    ingredients[index].copy(
                                        allergens = ingredients[index].allergens + tag))
                                addAllergenFor = null
                            },
                            label = { Text(allergenLabel(tag)) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addAllergenFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Card-wrapped form section with a title and optional trailing action. */
@Composable
private fun FormSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}
