package com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/**
 * The block fills the screen edge to edge and keeps its content clear of the status bar itself.
 * The screen is light, so the system bars get dark icons while it shows.
 */
@Composable
fun EtchSketchSignatureDemo() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars ?: false
        val nav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            controller?.isAppearanceLightStatusBars = status
            controller?.isAppearanceLightNavigationBars = nav
        }
    }
    EtchSketchSignature(autoPlay = true)
}

@Preview
@Composable
private fun EtchSketchSignatureDemoPreview() {
    EtchSketchSignatureDemo()
}
