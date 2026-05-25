package com.example.bloodlink

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "QrScanner"

/**
 * Full-screen QR scanner using camera. Calls onScanned with the decoded string (e.g. screening ID)
 * or onCancel when user closes. Requires CAMERA permission.
 */
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            val barcodeScanner = BarcodeScanning.getClient()
            setImageAnalysisAnalyzer(executor, ImageAnalysis.Analyzer { imageProxy ->
                scanQrFromImageProxy(imageProxy, barcodeScanner) { value ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        unbind()
                        onScanned(value)
                    }
                }
                imageProxy.close()
            })
        }
    }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Text(
                "Camera permission is needed to scan QR",
                Modifier.padding(24.dp).align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        LaunchedEffect(Unit) { onRequestPermission() }
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraController.unbind()
            } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.controller = cameraController }
        )
        LaunchedEffect(hasCameraPermission) {
            if (hasCameraPermission) {
                try {
                    cameraController.bindToLifecycle(lifecycleOwner)
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                }
            }
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun scanQrFromImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: return
    val rotation = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
            if (raw.isNotBlank()) onResult(raw.trim())
        }
        .addOnFailureListener { e -> Log.w(TAG, "Barcode scan failed", e) }
}
