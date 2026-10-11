package com.example.mealplanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.ui.allergenLabel
import com.example.mealplanner.ui.components.dietIcon
import com.example.mealplanner.ui.dietLabel
import com.example.mealplanner.ui.theme.ThemePrefs
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.ProfileQr
import com.example.mealplanner.util.QrShare
import com.example.mealplanner.util.RecipeQr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MealPlannerViewModel,
    scanResult: String?,
    onScanConsumed: () -> Unit,
    onScanProfile: () -> Unit
) {
    val context = LocalContext.current
    val profile = viewModel.profile.collectAsState().value

    var showShareDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<com.example.mealplanner.data.DietaryProfile?>(null) }

    LaunchedEffect(scanResult) {
        val raw = scanResult ?: return@LaunchedEffect
        onScanConsumed()
        when {
            ProfileQr.isProfilePayload(raw) -> {
                val decoded = ProfileQr.decode(raw)
                if (decoded == null) {
                    Toast.makeText(context, R.string.invalid_code, Toast.LENGTH_SHORT).show()
                } else {
                    pendingImport = decoded
                }
            }
            RecipeQr.isRecipePayload(raw) ->
                Toast.makeText(context, R.string.scan_recipe_on_recipes_tab,
                    Toast.LENGTH_LONG).show()
            else ->
                Toast.makeText(context, R.string.invalid_code, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Allergies ----
            OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.my_allergies),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.my_allergies_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AllergenUtil.ALLERGENS.forEach { tag ->
                            FilterChip(
                                selected = tag in profile.allergies,
                                onClick = { viewModel.toggleAllergy(tag) },
                                leadingIcon = {
                                    if (tag in profile.allergies) {
                                        Icon(Icons.Rounded.Warning, contentDescription = null,
                                            modifier = Modifier.size(16.dp))
                                    }
                                },
                                label = { Text(allergenLabel(tag)) }
                            )
                        }
                    }
                }
            }

            // ---- Diets ----
            OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.my_diets),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.my_diets_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AllergenUtil.DIETS.forEach { tag ->
                            FilterChip(
                                selected = tag in profile.diets,
                                onClick = { viewModel.toggleDiet(tag) },
                                leadingIcon = {
                                    Icon(dietIcon(tag), contentDescription = null,
                                        modifier = Modifier.size(16.dp))
                                },
                                label = { Text(dietLabel(tag)) }
                            )
                        }
                    }
                }
            }

            // ---- QR share / import ----
            OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.share_profile_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.share_profile_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_profile))
                        }
                        Button(onClick = onScanProfile, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scan_profile))
                        }
                    }
                }
            }

            // ---- Appearance ----
            OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.theme),
                        style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = ThemePrefs.themeMode == ThemePrefs.SYSTEM,
                            onClick = { ThemePrefs.set(context, ThemePrefs.SYSTEM) },
                            shape = SegmentedButtonDefaults.itemShape(0, 3)
                        ) { Text(stringResource(R.string.theme_system)) }
                        SegmentedButton(
                            selected = ThemePrefs.themeMode == ThemePrefs.LIGHT,
                            onClick = { ThemePrefs.set(context, ThemePrefs.LIGHT) },
                            shape = SegmentedButtonDefaults.itemShape(1, 3)
                        ) { Text(stringResource(R.string.theme_light)) }
                        SegmentedButton(
                            selected = ThemePrefs.themeMode == ThemePrefs.DARK,
                            onClick = { ThemePrefs.set(context, ThemePrefs.DARK) },
                            shape = SegmentedButtonDefaults.itemShape(2, 3)
                        ) { Text(stringResource(R.string.theme_dark)) }
                    }
                }
            }

            // ---- Disclaimer ----
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.profile_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showShareDialog) {
        val qrBitmap = remember(profile) {
            ProfileQr.generateBitmap(ProfileQr.encode(profile), 720)
        }
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share_profile_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_profile_hint),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.share_profile),
                            modifier = Modifier.size(240.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    qrBitmap?.let { bmp ->
                        TextButton(onClick = { QrShare.share(context, bmp, "profile-qr.png") }) {
                            Text(stringResource(R.string.share_qr))
                        }
                    }
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }

    pendingImport?.let { incoming ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_profile_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_profile_message))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "•  " + stringResource(R.string.my_allergies) + ": " +
                            incoming.allergies.joinToString { allergenLabel(it) }
                                .ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "•  " + stringResource(R.string.my_diets) + ": " +
                            incoming.diets.joinToString { dietLabel(it) }.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importProfile(incoming)
                    Toast.makeText(context, R.string.profile_imported, Toast.LENGTH_SHORT).show()
                    pendingImport = null
                }) { Text(stringResource(R.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
