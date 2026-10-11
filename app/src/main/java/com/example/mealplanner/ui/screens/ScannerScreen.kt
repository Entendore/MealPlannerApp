package com.example.mealplanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mealplanner.R
import com.example.mealplanner.ui.components.CircleIconButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            val lifecycleOwner = LocalLifecycleOwner.current
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        val provider = ProcessCameraProvider.getInstance(ctx).get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy: ImageProxy ->
                            val mediaImage = proxy.image
                            if (mediaImage == null) proxy.close()
                            else {
                                val input = InputImage.fromMediaImage(
                                    mediaImage, proxy.imageInfo.rotationDegrees
                                )
                                scanner.process(input)
                                    .addOnSuccessListener { barcodes ->
                                        if (!handled.get()) {
                                            barcodes.firstOrNull()?.rawValue?.let { value ->
                                                if (handled.compareAndSet(false, true)) {
                                                    onResult(value)
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                        provider.unbindAll()
                        cameraRef = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // Top & bottom scrims
            Box(
                Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            )
            Box(
                Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            )

            // Corner-bracket reticle
            Box(Modifier.align(Alignment.Center).size(260.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val corner = 56f
                    val stroke = Stroke(width = 3.dp.toPx(),
                        cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val c = Color.White.copy(alpha = 0.95f)
                    fun bracket() = Path().apply {
                        moveTo(0f, corner); lineTo(0f, 0f); lineTo(corner, 0f)
                    }
                    drawPath(bracket(), c, style = stroke)
                    withTransform({ translate(size.width, 0f); rotate(90f, Offset.Zero) }) {
                        drawPath(bracket(), c, style = stroke)
                    }
                    withTransform({
                        translate(size.width, size.height); rotate(180f, Offset.Zero)
                    }) { drawPath(bracket(), c, style = stroke) }
                    withTransform({
                        translate(0f, size.height); rotate(270f, Offset.Zero)
                    }) { drawPath(bracket(), c, style = stroke) }
                }
            }

            // Top controls (frosted circles)
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                CircleIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.back),
                    onClick = onClose,
                    container = Color.White.copy(alpha = 0.2f),
                    tint = Color.White
                )
            }
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                CircleIconButton(
                    icon = if (torchOn) Icons.Default.FlashlightOn
                    else Icons.Default.FlashlightOff,
                    contentDescription = stringResource(R.string.torch),
                    onClick = {
                        torchOn = !torchOn
                        cameraRef?.cameraControl?.enableTorch(torchOn)
                    },
                    container = Color.White.copy(alpha = 0.2f),
                    tint = if (torchOn) Color(0xFFFFD54F) else Color.White
                )
            }

            // Bottom hint pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
            ) {
                Text(
                    stringResource(R.string.scan_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.camera_permission_rationale),
                        color = Color.White)
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }
        }
    }
}
