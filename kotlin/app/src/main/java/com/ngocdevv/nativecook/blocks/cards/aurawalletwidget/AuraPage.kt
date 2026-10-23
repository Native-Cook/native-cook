package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

// A stationary page. Before API 33 the same lamp math is rasterized once off
// the UI thread, at physical-pixel resolution, then reused as an ordinary bitmap.
@Composable
internal fun AuraPage(width: Int, height: Int, cardX: Float, cardY: Float, cardWidth: Float, density: Float) {
    val page = remember(cardX, cardY, cardWidth, density) {
        if (Build.VERSION.SDK_INT >= 33) AuraPageMaterial(cardX, cardY, cardWidth, density) else null
    }
    val bitmap by produceState<Bitmap?>(null, width, height, cardX, cardY, cardWidth, density) {
        if (Build.VERSION.SDK_INT < 33 && width > 0 && height > 0) {
            value = withContext(Dispatchers.Default) {
                auraPageBitmap(width, height, cardX, cardY, cardWidth, density)
            }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            if (page != null && Build.VERSION.SDK_INT >= 33) page.draw(canvas.nativeCanvas, size.width, size.height)
            else bitmap?.let { canvas.nativeCanvas.drawBitmap(it, 0f, 0f, paint) }
        }
    }
}

private fun auraPageBitmap(width: Int, height: Int, cardX: Float, cardY: Float, cardWidth: Float, density: Float): Bitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val qx = ((x + 0.5) / density - cardX) / cardWidth
            val qy = ((y + 0.5) / density - cardY) / cardWidth
            val leftWide = auraLamp(qx, qy, -0.06, 0.55, 0.44, if (qy < 0.55) 0.90 else 0.68) * 1.08
            val leftHot = auraLamp(qx, qy, -0.06, 0.85, 0.38, 0.34)
            val poolLeft = auraLamp(qx, qy, 0.27, 1.10, 0.44, 0.26)
            val poolRight = auraLamp(qx, qy, 0.91, 1.12, 0.53, 0.28)
            val fallWidth = 0.64 + (1.10 - 0.64) * auraEase((qy - 1.15) / (1.65 - 1.15))
            val fall = auraLamp(qx, qy, 0.90, 1.55, fallWidth, 0.72)
            val ceiling = auraEase((qy + 0.10) / 0.38)
            val dot = x * 0.06711056 + y * 0.00583715
            val random = 52.9829189 * (dot - floor(dot))
            val dither = (random - floor(random) - 0.5) / 255
            var color = 0xFF000000.toInt()
            for (component in 0..2) {
                val shift = (2 - component) * 8
                val page = ((0x040116 shr shift) and 255) / 255.0
                val leftCore = ((0xD9A8DC shr shift) and 255) / 255.0
                val leftEdge = ((0xA25CD8 shr shift) and 255) / 255.0
                val lowCore = ((0x5A12B8 shr shift) and 255) / 255.0
                val leftLight = (leftEdge + (leftCore - leftEdge) * leftHot) * leftWide
                val pool = 1 - (1 - leftCore * poolLeft * 0.30) *
                    (1 - lowCore * poolRight * 0.46) * (1 - lowCore * fall * 0.55)
                val channel = 1 - (1 - page * 0.62) * (1 - leftLight * ceiling) * (1 - pool * ceiling)
                color = color or (((channel + dither).coerceIn(0.0, 1.0) * 255).roundToInt() shl shift)
            }
            pixels[y * width + x] = color
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun auraLamp(x: Double, y: Double, centerX: Double, centerY: Double, reachX: Double, reachY: Double): Double {
    val dx = (x - centerX) / reachX
    val dy = (y - centerY) / reachY
    return exp(-dx * dx - dy * dy)
}
