package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/** Draw invalidation observes [revision] only in the draw phase; the static background is cached by the renderer. */
@Composable
internal fun GlassLockKeyScene(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(context, simulation.geometry, density) {
        GlassLockKeyRenderer(context, simulation.geometry, density)
    }
    Canvas(modifier = modifier) {
        revision.value
        drawIntoCanvas { renderer.draw(it.nativeCanvas, simulation) }
    }
}
