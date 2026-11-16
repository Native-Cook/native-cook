package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Beam Shape Torch: the demo screen of Beam Control. The pin's magenta wallpaper with a film grain, and the capsule hanging from the
 * Dynamic Island with its torch; the component is [BeamShapeTorchControl].
 *
 * LOOP = 9 s ([BeamShapeTorchMotion.LOOP]): the beam squeezes to a pencil and lands on the ceiling, opens back to a fan, dims, the
 * capsule collapses into the island and grows again, the lamp ignites, rest. A touch interrupts it; two seconds after the last
 * touch it eases to rest and the loop restarts. Reduce Motion: no loop, the capsule fades instead of travelling.
 */
@Composable
fun BeamShapeTorch(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val wallpaper = remember(context.assets) { BeamShapeTorchWallpaper(context.assets) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    BlockSystemBars(darkIcons = false)
    // the wallpaper decodes off the main thread; its own colours stand in meanwhile, so nothing shifts
    LaunchedEffect(wallpaper) {
        image = withContext(Dispatchers.IO) { wallpaper.decode() }
    }
    Box(Modifier.fillMaxSize().background(Color(BeamShapeTorchTheme.wallTop))) {
        Canvas(Modifier.fillMaxSize()) {
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            wallpaper.draw(c, size.width / scale, size.height / scale, scale, image)
            c.restore()
        }
        BeamShapeTorchControl(autoPlay = autoPlay, previewTime = previewTime)
    }
}
