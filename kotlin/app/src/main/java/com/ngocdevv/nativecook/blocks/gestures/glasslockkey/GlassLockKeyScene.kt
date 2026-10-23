package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

/** Draws the seven registered replica layers; [revision] is observed only by the draw phase. */
@Composable
fun GlassLockKeyScene(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assets = remember(context) { GlassLockKeyAssets(context) }
    val renderer = remember(assets) { GlassLockKeyRenderer(assets) }

    Canvas(modifier = modifier) {
        revision.value
        val displayDensity = density
        val widthDp = size.width / displayDensity
        val heightDp = size.height / displayDensity
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            canvas.save()
            canvas.scale(displayDensity, displayDensity)
            renderer.draw(canvas, simulation, widthDp, heightDp)
            canvas.restore()
        }
    }
}
