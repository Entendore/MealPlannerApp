package com.example.mealplanner.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.ui.components.CircleIconButton
import com.example.mealplanner.ui.components.ClashBanner
import com.example.mealplanner.ui.components.MetaChip
import com.example.mealplanner.ui.components.SectionHeader
import com.example.mealplanner.ui.allergenLabelRes
import com.example.mealplanner.ui.dietLabelRes
import com.example.mealplanner.ui.formatAmount
import com.example.mealplanner.ui.formatDuration
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.ui.mealTypeLabel
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.Durations
import com.example.mealplanner.util.ProfileQr
import com.example.mealplanner.util.QrShare
import com.example.mealplanner.util.RecipeQr
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun parseSteps(text: String): List<String> =
    text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: RecipeWithIngredients?,
    profile: DietaryProfile,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Recipe) -> Unit,
    onAddToPlan: (dates: List<String>, mealType: String, servings: Int, recipeId: Int) -> Unit,
    onExportPdf: (Uri) -> Unit,
    onDuplicate: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit
) {
    var showPlanDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showCookMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(onExportPdf) }
    val suggestedName = recipe?.recipe?.name
        ?.replace(Regex("[\\\\/:*?\"<>|]"), "")?.trim()
        ?.replace(Regex("\\s+"), "-")?.ifBlank { null }
        ?.let { "$it.pdf" } ?: "recipe.pdf"

    Scaffold(
        bottomBar = {
            if (recipe != null) {
                val steps = parseSteps(recipe.recipe.instructions)
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (steps.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showCookMode = true },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.cook_mode), maxLines = 1)
                            }
                        }
                        Button(
                            onClick = { showPlanDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(if (steps.isEmpty()) 2f else 1.6f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_to_meal_plan), maxLines = 1)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (recipe == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val r = recipe.recipe
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            if (r.photoPath != null) {
                // ---- Hero image with overlaid controls ----
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    AsyncImage(
                        model = File(r.photoPath!!),
                        contentDescription = stringResource(R.string.recipe_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.5f),
                                0.25f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
                    Row(Modifier.align(Alignment.TopStart)
                        .statusBarsPadding().padding(8.dp)) {
                        CircleIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBack,
                            container = Color.Black.copy(alpha = 0.35f),
                            tint = Color.White
                        )
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircleIconButton(
                            icon = if (r.isFavorite) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.toggle_favorite),
                            onClick = { onToggleFavorite(r.id) },
                            container = Color.Black.copy(alpha = 0.35f),
                            tint = if (r.isFavorite) Color(0xFFFF6B6B) else Color.White
                        )
                        CircleIconButton(Icons.Default.ContentCopy,
                            stringResource(R.string.duplicate),
                            { onDuplicate(r.id) },
                            container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                        CircleIconButton(Icons.Rounded.QrCode2,
                            stringResource(R.string.qr_share_title),
                            { showQrDialog = true },
                            container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                        CircleIconButton(Icons.Default.PictureAsPdf,
                            stringResource(R.string.export_recipe_pdf),
                            { exportLauncher.launch(suggestedName) },
                            container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                        CircleIconButton(Icons.Default.Edit, stringResource(R.string.edit),
                            { onEdit(r.id) },
                            container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                        CircleIconButton(Icons.Default.Delete, stringResource(R.string.delete),
                            { showDeleteDialog = true },
                            container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                    }
                    Text(
                        r.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                    )
                }
            } else {
                // ---- Plain header when there's no photo ----
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(r.name, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { onToggleFavorite(r.id) }) {
                        Icon(
                            if (r.isFavorite) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.toggle_favorite),
                            tint = if (r.isFavorite) Color(0xFFFF6B6B)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDuplicate(r.id) }) {
                        Icon(Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.duplicate))
                    }
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Rounded.QrCode2,
                            contentDescription = stringResource(R.string.qr_share_title))
                    }
                    IconButton(onClick = { exportLauncher.launch(suggestedName) }) {
                        Icon(Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.export_recipe_pdf))
                    }
                    IconButton(onClick = { onEdit(r.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }

            DetailMetaRow(r, recipe.ingredients.sumOf { it.price })

            // ---- Neutral dietary info line ----
            AllergenUtil.run {
                val allAllergens = recipe.allergenSet
                if (allAllergens.isNotEmpty() || r.diets.isNotEmpty()) {
                    val containsPart = allAllergens.takeIf { it.isNotEmpty() }?.let {
                        stringResource(R.string.contains_tags,
                            it.sorted().joinToString { tag -> stringResource(allergenLabelRes(tag)) })
                    }
                    val dietsPart = r.diets.takeIf { it.isNotEmpty() }?.let {
                        stringResource(R.string.diets_tags,
                            it.joinToString { tag -> stringResource(dietLabelRes(tag)) })
                    }
                    Text(
                        listOfNotNull(containsPart, dietsPart).joinToString("   ·   "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ---- Tags ----
            if (r.tags.isNotEmpty()) {
                Text(
                    stringResource(R.string.tags),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    r.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }

            // ---- Clash banner (red only when it clashes with the profile) ----
            AllergenUtil.run {
                ClashBanner(
                    allergenClash = recipe.allergenClash(profile).sorted(),
                    dietClash = recipe.dietClash(profile),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ---- Adapted-for note ----
            if (r.adaptedNote.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.adapted_for) + ": " + r.adaptedNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ---- Servings scaler (display-only) ----
            var displayServings by remember(r.id, r.servings) {
                mutableIntStateOf(r.servings.coerceAtLeast(1))
            }
            val factor = if (r.servings > 0)
                displayServings.toDouble() / r.servings else 1.0
            val totalCost = recipe.ingredients.sumOf { it.price }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.adjust_servings),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (totalCost > 0) {
                    Text(formatMoney(totalCost * factor),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                FilledTonalIconButton(
                    onClick = { if (displayServings > 1) displayServings-- },
                    enabled = displayServings > 1
                ) { Icon(Icons.Default.Remove,
                    contentDescription = stringResource(R.string.decrease_servings)) }
                Text("$displayServings", style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
                FilledTonalIconButton(onClick = { displayServings++ }) {
                    Icon(Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase_servings))
                }
                if (displayServings != r.servings) {
                    TextButton(onClick = { displayServings = r.servings }) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            // ---- Ingredients (scaled) ----
            SectionHeader(stringResource(R.string.ingredients))
            if (recipe.ingredients.isEmpty()) {
                Text(
                    stringResource(R.string.no_ingredients),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recipe.ingredients.forEach { ingredient ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f))
                        if (ingredient.quantity != 0.0) {
                            Text(
                                formatAmount(ingredient.quantity * factor, ingredient.unit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- Instructions (auto-numbered steps) ----
            SectionHeader(stringResource(R.string.instructions))
            val steps = parseSteps(r.instructions)
            if (steps.isEmpty()) {
                Text(
                    stringResource(R.string.no_instructions),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val alreadyNumbered = steps.size > 1 &&
                    steps.all { it.firstOrNull()?.isDigit() == true }
                steps.forEachIndexed { index, step ->
                    if (steps.size == 1 || alreadyNumbered) {
                        Text(step, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    } else {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Surface(shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(24.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(step, style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // ---- Plan dialog (batch) ----
    if (showPlanDialog && recipe != null) {
        PlanMealDialog(
            defaultServings = recipe.recipe.servings,
            onConfirm = { dates, mealType, servings ->
                onAddToPlan(dates, mealType, servings, recipe.recipe.id)
                showPlanDialog = false
            },
            onDismiss = { showPlanDialog = false }
        )
    }

    // ---- QR share dialog ----
    if (showQrDialog && recipe != null) {
        val qrBitmap = remember(recipe) { RecipeQr.generateBitmap(RecipeQr.encode(recipe), 720) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(stringResource(R.string.qr_share_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    if (qrBitmap == null) {
                        Text(stringResource(R.string.qr_too_large))
                    } else {
                        Text(stringResource(R.string.qr_share_hint, recipe.recipe.name),
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_share_title),
                            modifier = Modifier.size(240.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    qrBitmap?.let { bmp ->
                        TextButton(onClick = { QrShare.share(context, bmp, "recipe-qr.png") }) {
                            Icon(Icons.Default.Share, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.share_qr))
                        }
                    }
                    TextButton(onClick = { showQrDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }

    // ---- Delete dialog ----
    if (showDeleteDialog && recipe != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_recipe_title)) },
            text = { Text(stringResource(R.string.delete_recipe_message, recipe.recipe.name)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete(recipe.recipe) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ---- Cook mode ----
    if (showCookMode && recipe != null) {
        val steps = parseSteps(recipe.recipe.instructions)
        var stepIndex by remember { mutableIntStateOf(0) }
        val activity = context.findActivity()

        DisposableEffect(Unit) {
            activity?.window?.addFlags(FLAG_KEEP_SCREEN_ON)
            onDispose { activity?.window?.clearFlags(FLAG_KEEP_SCREEN_ON) }
        }

        val stepSeconds = remember(stepIndex) { Durations.parseSeconds(steps[stepIndex]) }
        var timerTotal by remember { mutableLongStateOf(0L) }
        var timerEndsAt by remember { mutableStateOf<Long?>(null) }
        var remainingSec by remember { mutableLongStateOf(0L) }
        var timerFinished by remember { mutableStateOf(false) }

        LaunchedEffect(stepIndex) {
            timerEndsAt = null; timerFinished = false; remainingSec = 0
        }
        LaunchedEffect(timerEndsAt) {
            while (timerEndsAt != null) {
                val rem = ((timerEndsAt!! - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                remainingSec = rem
                if (rem == 0L) {
                    timerEndsAt = null; timerFinished = true
                    runCatching {
                        RingtoneManager.getRingtone(context,
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play()
                    }
                    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                            as? VibratorManager)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    runCatching {
                        vibrator?.vibrate(
                            VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                    }
                } else delay(250)
            }
        }

        Dialog(
            onDismissRequest = { showCookMode = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCookMode = false }) {
                            Icon(Icons.Default.Close,
                                contentDescription = stringResource(R.string.close))
                        }
                        Text(
                            stringResource(R.string.step_of, stepIndex + 1, steps.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(48.dp))
                    }
                    LinearProgressIndicator(
                        progress = { (stepIndex + 1f) / steps.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            steps[stepIndex],
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        if (timerFinished) {
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.timer_done),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary)
                        } else if (timerEndsAt != null) {
                            Spacer(Modifier.height(24.dp))
                            Text(formatDuration(remainingSec),
                                style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (timerTotal > 0) 1f - remainingSec.toFloat() / timerTotal
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth(0.6f)
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                timerEndsAt = null; timerFinished = false; remainingSec = 0
                            }) { Text(stringResource(R.string.cancel)) }
                        } else if (stepSeconds != null) {
                            Spacer(Modifier.height(24.dp))
                            FilledTonalButton(onClick = {
                                timerTotal = stepSeconds
                                timerFinished = false
                                timerEndsAt = System.currentTimeMillis() + stepSeconds * 1000
                            }) {
                                Icon(Icons.Rounded.Timer, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.start_timer, formatDuration(stepSeconds)))
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (stepIndex > 0) stepIndex-- },
                            enabled = stepIndex > 0,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) { Text(stringResource(R.string.previous)) }
                        Button(
                            onClick = {
                                if (stepIndex < steps.lastIndex) stepIndex++
                                else showCookMode = false
                            },
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(
                                if (stepIndex < steps.lastIndex) stringResource(R.string.next)
                                else stringResource(R.string.done)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaRow(recipe: Recipe, cost: Double) {
    val totalMinutes = recipe.prepTimeMinutes + recipe.cookTimeMinutes
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetaChip(
            com.example.mealplanner.ui.components.categoryIcon(recipe.category),
            recipe.category
        )
        if (totalMinutes > 0) {
            MetaChip(Icons.Default.Schedule,
                stringResource(R.string.minutes_total, totalMinutes))
        }
        MetaChip(Icons.Default.Person,
            pluralStringResource(R.plurals.servings_count, recipe.servings, recipe.servings))
        if (cost > 0 && recipe.servings > 0) {
            MetaChip(Icons.Default.Payments,
                stringResource(R.string.cost_per_serving, formatMoney(cost / recipe.servings)))
        }
    }
}

/** Plan dialog with batch "also plan on" multi-select and a servings stepper. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlanMealDialog(
    defaultServings: Int,
    onConfirm: (dates: List<String>, mealType: String, servings: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val days = remember { (0..6L).map { today.plusDays(it) } }
    var selectedDay by remember { mutableStateOf(today) }
    var mealType by remember { mutableStateOf("Dinner") }
    var repeatDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var servings by remember { mutableIntStateOf(defaultServings.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_meal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.day), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        FilterChip(
                            selected = day == selectedDay,
                            onClick = { selectedDay = day },
                            label = {
                                Text(
                                    when (day) {
                                        today -> stringResource(R.string.today)
                                        today.plusDays(1) -> stringResource(R.string.tomorrow)
                                        else -> day.format(DateTimeFormatter.ofPattern("EEE d"))
                                    }
                                )
                            }
                        )
                    }
                }
                Text(stringResource(R.string.meal), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Breakfast", "Lunch", "Dinner").forEach { type ->
                        FilterChip(
                            selected = mealType == type,
                            onClick = { mealType = type },
                            label = { Text(mealTypeLabel(type)) }
                        )
                    }
                }
                Text(stringResource(R.string.repeat_on),
                    style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.drop(1).filter { it != selectedDay }.forEach { day ->
                        FilterChip(
                            selected = day in repeatDays,
                            onClick = {
                                repeatDays = if (day in repeatDays) repeatDays - day
                                else repeatDays + day
                            },
                            label = { Text(day.format(DateTimeFormatter.ofPattern("EEE d"))) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.servings),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { if (servings > 1) servings-- },
                        enabled = servings > 1
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text("$servings", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { servings++ }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val allDays = (listOf(selectedDay) + repeatDays).distinct().sorted()
                onConfirm(allDays.map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) },
                    mealType, servings)
            }) { Text(stringResource(R.string.add_to_plan)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
