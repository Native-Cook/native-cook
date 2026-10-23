package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ParticleDispersion(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.particleDispersionActivity() }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val current = remember { arrayOfNulls<ParticleDispersionCameraView>(1) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        current[0]?.onCameraPermission(it)
    }
    val camera = remember(activity, owner) {
        ParticleDispersionCameraView(activity, owner, scope) { permission.launch(Manifest.permission.CAMERA) }
    }
    DisposableEffect(camera) {
        current[0] = camera
        owner.lifecycle.addObserver(camera)
        onDispose {
            current[0] = null
            camera.close()
        }
    }
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    AndroidView(
        factory = { camera },
        update = { it.updateInsets(top, bottom) },
        modifier = modifier.fillMaxSize(),
    )
}

private fun Context.particleDispersionActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.particleDispersionActivity()
    else -> error("Particle Dispersion requires a ComponentActivity.")
}
