package com.ngocdevv.nativecook.blocks.navigation.magnifydock

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.max

/** How a layer of an icon is painted, in the icon's 100 × 100 box. Colours are 0xAARRGGBB. */
internal sealed class DockPaint {
    class Solid(val color: Long) : DockPaint()
    class Linear(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: LongArray, val stops: FloatArray) : DockPaint()
    /** A radial gradient about (x0, y0) of radius x1; y1 is unused. */
    class Radial(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: LongArray, val stops: FloatArray) : DockPaint()
}

/** One shape of an icon: an SVG path (absolute M/L/C/Z) filled and/or stroked, optionally blurred. */
internal class DockLayer(val d: String, val fill: DockPaint?, val stroke: DockPaint? = null, val width: Float = 0f, val blur: Float = 0f)

/** One app of the dock: its name and its icon's layers, clipped to the squircle. */
internal class DockApp(val name: String, val layers: List<DockLayer>)

/** A superellipse (n = 5) in the 100 × 100 box: the icon's outline. */
internal const val DOCK_SQUIRCLE = "M100 50 L99.98 63.69 L99.94 68.06 L99.86 71.23 L99.75 73.81 L99.61 76.01 L99.44 77.94 L99.24 79.68 L99.01 81.26 L98.74 82.71 L98.44 84.05 L98.11 85.3 L97.74 86.46 L97.35 87.55 L96.91 88.57 L96.44 89.52 L95.94 90.43 L95.39 91.27 L94.81 92.07 L94.19 92.82 L93.53 93.53 L92.82 94.19 L92.07 94.81 L91.27 95.39 L90.43 95.94 L89.52 96.44 L88.57 96.91 L87.55 97.35 L86.46 97.74 L85.3 98.11 L84.05 98.44 L82.71 98.74 L81.26 99.01 L79.68 99.24 L77.94 99.44 L76.01 99.61 L73.81 99.75 L71.23 99.86 L68.06 99.94 L63.69 99.98 L50 100 L36.31 99.98 L31.94 99.94 L28.77 99.86 L26.19 99.75 L23.99 99.61 L22.06 99.44 L20.32 99.24 L18.74 99.01 L17.29 98.74 L15.95 98.44 L14.7 98.11 L13.54 97.74 L12.45 97.35 L11.43 96.91 L10.48 96.44 L9.57 95.94 L8.73 95.39 L7.93 94.81 L7.18 94.19 L6.47 93.53 L5.81 92.82 L5.19 92.07 L4.61 91.27 L4.06 90.43 L3.56 89.52 L3.09 88.57 L2.65 87.55 L2.26 86.46 L1.89 85.3 L1.56 84.05 L1.26 82.71 L0.99 81.26 L0.76 79.68 L0.56 77.94 L0.39 76.01 L0.25 73.81 L0.14 71.23 L0.06 68.06 L0.02 63.69 L0 50 L0.02 36.31 L0.06 31.94 L0.14 28.77 L0.25 26.19 L0.39 23.99 L0.56 22.06 L0.76 20.32 L0.99 18.74 L1.26 17.29 L1.56 15.95 L1.89 14.7 L2.26 13.54 L2.65 12.45 L3.09 11.43 L3.56 10.48 L4.06 9.57 L4.61 8.73 L5.19 7.93 L5.81 7.18 L6.47 6.47 L7.18 5.81 L7.93 5.19 L8.73 4.61 L9.57 4.06 L10.48 3.56 L11.43 3.09 L12.45 2.65 L13.54 2.26 L14.7 1.89 L15.95 1.56 L17.29 1.26 L18.74 0.99 L20.32 0.76 L22.06 0.56 L23.99 0.39 L26.19 0.25 L28.77 0.14 L31.94 0.06 L36.31 0.02 L50 0 L63.69 0.02 L68.06 0.06 L71.23 0.14 L73.81 0.25 L76.01 0.39 L77.94 0.56 L79.68 0.76 L81.26 0.99 L82.71 1.26 L84.05 1.56 L85.3 1.89 L86.46 2.26 L87.55 2.65 L88.57 3.09 L89.52 3.56 L90.43 4.06 L91.27 4.61 L92.07 5.19 L92.82 5.81 L93.53 6.47 L94.19 7.18 L94.81 7.93 L95.39 8.73 L95.94 9.57 L96.44 10.48 L96.91 11.43 L97.35 12.45 L97.74 13.54 L98.11 14.7 L98.44 15.95 L98.74 17.29 L99.01 18.74 L99.24 20.32 L99.44 22.06 L99.61 23.99 L99.75 26.19 L99.86 28.77 L99.94 31.94 L99.98 36.31 Z"

/** Paths parsed once, on the main thread that draws them. */
private val DockPaths = HashMap<String, Path>()

internal fun dockPath(d: String): Path = DockPaths.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun stopsOf(colors: LongArray, stops: FloatArray) = Array(colors.size) { i -> stops[i] to Color(colors[i].toInt()) }

private fun brushOf(paint: DockPaint): Brush = when (paint) {
    is DockPaint.Solid -> androidx.compose.ui.graphics.SolidColor(Color(paint.color.toInt()))
    is DockPaint.Linear -> Brush.linearGradient(*stopsOf(paint.colors, paint.stops), start = Offset(paint.x0, paint.y0), end = Offset(paint.x1, paint.y1))
    is DockPaint.Radial -> Brush.radialGradient(*stopsOf(paint.colors, paint.stops), center = Offset(paint.x0, paint.y0), radius = paint.x1)
}

/** A blur of standard deviation [sigma] in this scope's units, at [pxPerUnit] pixels a unit, as Android's blur radius. */
private fun blurRadius(sigma: Float, pxPerUnit: Float): Float {
    val px = sigma * pxPerUnit
    // Android turns a blur radius into sigma = 0.57735 · radius + 0.5.
    return max(0.1f, (px - 0.5f) / 0.57735f) / pxPerUnit
}

/** Fills [path] in a solid [color] with a soft edge of [sigma] (Android 9+; earlier it is left out). */
private fun DrawScope.blurredFill(path: Path, color: Long, sigma: Float, pxPerUnit: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toInt()
        paint.maskFilter = BlurMaskFilter(blurRadius(sigma, pxPerUnit), BlurMaskFilter.Blur.NORMAL)
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/**
 * Draws one icon in its 100 × 100 box, the scope already scaled so one unit is [pxPerUnit]
 * pixels: a soft contact shadow, then its layers clipped to the squircle.
 */
internal fun DrawScope.drawDockIcon(app: DockApp, pxPerUnit: Float) {
    val outline = dockPath(DOCK_SQUIRCLE)
    translate(0f, 3f) { blurredFill(outline, 0x6B060A16, 4f, pxPerUnit) }
    clipPath(outline) {
        for (layer in app.layers) {
            val path = dockPath(layer.d)
            val fill = layer.fill
            if (fill != null) {
                if (layer.blur > 0f && fill is DockPaint.Solid) blurredFill(path, fill.color, layer.blur, pxPerUnit)
                else drawPath(path, brushOf(fill))
            }
            val stroke = layer.stroke
            if (stroke != null) {
                drawPath(path, brushOf(stroke), style = Stroke(width = layer.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// The dock's apps: invented names, each icon generated from the same source as the React Native
// version's MagnifyDockArt.ts, so both draw exactly the same art.
internal val DockApps: List<DockApp> = listOf(
    DockApp(
        "Postbox",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFF6CC6FF, 0xFF1A62DE), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer("M25 34 L75 34 C78.87 34 82 37.13 82 41 L82 69 C82 72.87 78.87 76 75 76 L25 76 C21.13 76 18 72.87 18 69 L18 41 C18 37.13 21.13 34 25 34 Z", fill = DockPaint.Solid(0x8C0B3A8F), blur = 3f),
            DockLayer("M25 30 L75 30 C78.87 30 82 33.13 82 37 L82 65 C82 68.87 78.87 72 75 72 L25 72 C21.13 72 18 68.87 18 65 L18 37 C18 33.13 21.13 30 25 30 Z", fill = DockPaint.Linear(50f, 30f, 50f, 72f, longArrayOf(0xFFFFFFFF, 0xFFE4EBF6), floatArrayOf(0f, 1f))),
            DockLayer("M21 34 L50 56 L79 34", fill = null, stroke = DockPaint.Solid(0xFFA9BCDA), width = 3.2f),
            DockLayer("M21 70 L42 53", fill = null, stroke = DockPaint.Solid(0xFFCDD8EA), width = 2.6f),
            DockLayer("M79 70 L58 53", fill = null, stroke = DockPaint.Solid(0xFFCDD8EA), width = 2.6f),
            DockLayer("M56.5 57 C56.5 60.59 53.59 63.5 50 63.5 C46.41 63.5 43.5 60.59 43.5 57 C43.5 53.41 46.41 50.5 50 50.5 C53.59 50.5 56.5 53.41 56.5 57 Z", fill = DockPaint.Linear(50f, 50.5f, 50f, 63.5f, longArrayOf(0xFFFF6B5C, 0xFFD63A2E), floatArrayOf(0f, 1f))),
            DockLayer("M53.6 57 C53.6 58.99 51.99 60.6 50 60.6 C48.01 60.6 46.4 58.99 46.4 57 C46.4 55.01 48.01 53.4 50 53.4 C51.99 53.4 53.6 55.01 53.6 57 Z", fill = null, stroke = DockPaint.Solid(0x59FFFFFF), width = 1.2f),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Margin",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFFFFFDF7, 0xFFEFE8D8), floatArrayOf(0f, 1f))),
            DockLayer("M0 0 L100 0 L100 27 L0 27 Z", fill = DockPaint.Linear(50f, 0f, 50f, 27f, longArrayOf(0xFFFFD862, 0xFFF5AE25), floatArrayOf(0f, 1f))),
            DockLayer("M0 27 L100 27 L100 29.2 L0 29.2 Z", fill = DockPaint.Solid(0x1A000000)),
            DockLayer("M14 45 L86 45", fill = null, stroke = DockPaint.Solid(0xFFD8CFBD), width = 2.2f),
            DockLayer("M14 59 L86 59", fill = null, stroke = DockPaint.Solid(0xFFD8CFBD), width = 2.2f),
            DockLayer("M14 73 L86 73", fill = null, stroke = DockPaint.Solid(0xFFD8CFBD), width = 2.2f),
            DockLayer("M14 87 L62 87", fill = null, stroke = DockPaint.Solid(0xFFD8CFBD), width = 2.2f),
            DockLayer("M27 29 L27 100", fill = null, stroke = DockPaint.Solid(0xBFEF8A78), width = 1.6f),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Hum",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFFFFB066, 0xFFF2622E), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer("M83 54 C83 72.23 68.23 87 50 87 C31.77 87 17 72.23 17 54 C17 35.77 31.77 21 50 21 C68.23 21 83 35.77 83 54 Z", fill = DockPaint.Solid(0x807A2A0C), blur = 3f),
            DockLayer("M83 51 C83 69.23 68.23 84 50 84 C31.77 84 17 69.23 17 51 C17 32.77 31.77 18 50 18 C68.23 18 83 32.77 83 51 Z", fill = DockPaint.Radial(42f, 42f, 40f, 0f, longArrayOf(0xFF3A3A40, 0xFF18181C, 0xFF0C0C0E), floatArrayOf(0f, 0.6f, 1f))),
            DockLayer("M78 51 C78 66.46 65.46 79 50 79 C34.54 79 22 66.46 22 51 C22 35.54 34.54 23 50 23 C65.46 23 78 35.54 78 51 Z", fill = null, stroke = DockPaint.Solid(0x14FFFFFF), width = 1f),
            DockLayer("M73 51 C73 63.7 62.7 74 50 74 C37.3 74 27 63.7 27 51 C27 38.3 37.3 28 50 28 C62.7 28 73 38.3 73 51 Z", fill = null, stroke = DockPaint.Solid(0x12FFFFFF), width = 1f),
            DockLayer("M68 51 C68 60.94 59.94 69 50 69 C40.06 69 32 60.94 32 51 C32 41.06 40.06 33 50 33 C59.94 33 68 41.06 68 51 Z", fill = null, stroke = DockPaint.Solid(0x0FFFFFFF), width = 1f),
            DockLayer("M28 30 C33 25 40 22 47 21", fill = null, stroke = DockPaint.Solid(0x47FFFFFF), width = 2.2f),
            DockLayer("M62 51 C62 57.63 56.63 63 50 63 C43.37 63 38 57.63 38 51 C38 44.37 43.37 39 50 39 C56.63 39 62 44.37 62 51 Z", fill = DockPaint.Linear(50f, 39f, 50f, 63f, longArrayOf(0xFFFFD36B, 0xFFFF8A3D), floatArrayOf(0f, 1f))),
            DockLayer("M52.6 51 C52.6 52.44 51.44 53.6 50 53.6 C48.56 53.6 47.4 52.44 47.4 51 C47.4 49.56 48.56 48.4 50 48.4 C51.44 48.4 52.6 49.56 52.6 51 Z", fill = DockPaint.Solid(0xFF1A1A1E)),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Wayfare",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFFEEF5E4, 0xFFD2E5C1), floatArrayOf(0f, 1f))),
            DockLayer("M0 0 L34 0 C30 12 18 20 0 22 Z", fill = DockPaint.Solid(0xFFB7DA9C)),
            DockLayer("M70 100 C72 86 86 78 100 80 L100 100 Z", fill = DockPaint.Solid(0xFFB7DA9C)),
            DockLayer("M-6 72 C22 60 38 84 60 68 C76 56 88 54 106 60", fill = null, stroke = DockPaint.Solid(0xFF7CC6F4), width = 11f),
            DockLayer("M-4 33 L104 50", fill = null, stroke = DockPaint.Solid(0xFFFFFFFF), width = 6f),
            DockLayer("M64 -4 L48 104", fill = null, stroke = DockPaint.Solid(0xFFFFFFFF), width = 5f),
            DockLayer("M-4 92 C28 82 66 98 104 86", fill = null, stroke = DockPaint.Solid(0xFFFFD36A), width = 5f),
            DockLayer("M61 63 C61 64.44 57.42 65.6 53 65.6 C48.58 65.6 45 64.44 45 63 C45 61.56 48.58 60.4 53 60.4 C57.42 60.4 61 61.56 61 63 Z", fill = DockPaint.Solid(0x38000000), blur = 1.5f),
            DockLayer("M53 62 C49 54 40 48 40 39 C40 31.8 45.8 26 53 26 C60.2 26 66 31.8 66 39 C66 48 57 54 53 62 Z", fill = DockPaint.Linear(53f, 26f, 53f, 62f, longArrayOf(0xFFFF7563, 0xFFDC2F26), floatArrayOf(0f, 1f))),
            DockLayer("M58 39 C58 41.76 55.76 44 53 44 C50.24 44 48 41.76 48 39 C48 36.24 50.24 34 53 34 C55.76 34 58 36.24 58 39 Z", fill = DockPaint.Solid(0xFFFFFFFF)),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Lumen",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFF575D66, 0xFF1F2227), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer("M84 54 C84 72.78 68.78 88 50 88 C31.22 88 16 72.78 16 54 C16 35.22 31.22 20 50 20 C68.78 20 84 35.22 84 54 Z", fill = DockPaint.Solid(0x73000000), blur = 3f),
            DockLayer("M84 52 C84 70.78 68.78 86 50 86 C31.22 86 16 70.78 16 52 C16 33.22 31.22 18 50 18 C68.78 18 84 33.22 84 52 Z", fill = DockPaint.Linear(50f, 18f, 50f, 86f, longArrayOf(0xFF41464E, 0xFF141518), floatArrayOf(0f, 1f))),
            DockLayer("M83 52 C83 70.23 68.23 85 50 85 C31.77 85 17 70.23 17 52 C17 33.77 31.77 19 50 19 C68.23 19 83 33.77 83 52 Z", fill = null, stroke = DockPaint.Solid(0x998A9099), width = 1.2f),
            DockLayer("M75 52 C75 65.81 63.81 77 50 77 C36.19 77 25 65.81 25 52 C25 38.19 36.19 27 50 27 C63.81 27 75 38.19 75 52 Z", fill = DockPaint.Solid(0xFF08090B)),
            DockLayer("M69 52 C69 62.49 60.49 71 50 71 C39.51 71 31 62.49 31 52 C31 41.51 39.51 33 50 33 C60.49 33 69 41.51 69 52 Z", fill = DockPaint.Radial(43f, 44f, 30f, 0f, longArrayOf(0xFF5A78F0, 0xFF23307A, 0xFF06070D), floatArrayOf(0f, 0.45f, 1f))),
            DockLayer("M59 52 C59 56.97 54.97 61 50 61 C45.03 61 41 56.97 41 52 C41 47.03 45.03 43 50 43 C54.97 43 59 47.03 59 52 Z", fill = DockPaint.Radial(48f, 50f, 10f, 0f, longArrayOf(0xFF1A1F3A, 0xFF020204), floatArrayOf(0f, 1f))),
            DockLayer("M47.26 41.75 C48.37 43.66 47.13 46.45 44.5 47.96 C41.87 49.48 38.84 49.16 37.74 47.25 C36.63 45.34 37.87 42.55 40.5 41.04 C43.13 39.52 46.16 39.84 47.26 41.75 Z", fill = DockPaint.Solid(0x99FFFFFF)),
            DockLayer("M60.2 60 C60.2 61.22 59.22 62.2 58 62.2 C56.78 62.2 55.8 61.22 55.8 60 C55.8 58.78 56.78 57.8 58 57.8 C59.22 57.8 60.2 58.78 60.2 60 Z", fill = DockPaint.Solid(0x4CFFFFFF)),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Fairweather",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFF5AD8D2, 0xFF1F8BC4), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer("M55 40 C55 49.94 46.94 58 37 58 C27.06 58 19 49.94 19 40 C19 30.06 27.06 22 37 22 C46.94 22 55 30.06 55 40 Z", fill = DockPaint.Solid(0x59FFD36B), blur = 4f),
            DockLayer("M53 40 C53 48.84 45.84 56 37 56 C28.16 56 21 48.84 21 40 C21 31.16 28.16 24 37 24 C45.84 24 53 31.16 53 40 Z", fill = DockPaint.Linear(37f, 24f, 37f, 56f, longArrayOf(0xFFFFE68A, 0xFFFFAD26), floatArrayOf(0f, 1f))),
            DockLayer("M67 65 C67 73.28 60.28 80 52 80 C43.72 80 37 73.28 37 65 C37 56.72 43.72 50 52 50 C60.28 50 67 56.72 67 65 Z", fill = DockPaint.Solid(0xFF1D6F9E), blur = 1.5f),
            DockLayer("M86 60 C86 69.94 77.94 78 68 78 C58.06 78 50 69.94 50 60 C50 50.06 58.06 42 68 42 C77.94 42 86 50.06 86 60 Z", fill = DockPaint.Solid(0xFF1D6F9E), blur = 1.5f),
            DockLayer("M49.5 71 C49.5 77.35 44.35 82.5 38 82.5 C31.65 82.5 26.5 77.35 26.5 71 C26.5 64.65 31.65 59.5 38 59.5 C44.35 59.5 49.5 64.65 49.5 71 Z", fill = DockPaint.Solid(0xFF1D6F9E), blur = 1.5f),
            DockLayer("M40 65 L78 65 C83.52 65 88 69.48 88 75 L88 75 C88 80.52 83.52 85 78 85 L40 85 C34.48 85 30 80.52 30 75 L30 75 C30 69.48 34.48 65 40 65 Z", fill = DockPaint.Solid(0xFF1D6F9E), blur = 1.5f),
            DockLayer("M67 62 C67 70.28 60.28 77 52 77 C43.72 77 37 70.28 37 62 C37 53.72 43.72 47 52 47 C60.28 47 67 53.72 67 62 Z", fill = DockPaint.Linear(50f, 40f, 50f, 82f, longArrayOf(0xFFFFFFFF, 0xFFDDE9FA), floatArrayOf(0f, 1f))),
            DockLayer("M86 57 C86 66.94 77.94 75 68 75 C58.06 75 50 66.94 50 57 C50 47.06 58.06 39 68 39 C77.94 39 86 47.06 86 57 Z", fill = DockPaint.Linear(50f, 40f, 50f, 82f, longArrayOf(0xFFFFFFFF, 0xFFDDE9FA), floatArrayOf(0f, 1f))),
            DockLayer("M49.5 68 C49.5 74.35 44.35 79.5 38 79.5 C31.65 79.5 26.5 74.35 26.5 68 C26.5 61.65 31.65 56.5 38 56.5 C44.35 56.5 49.5 61.65 49.5 68 Z", fill = DockPaint.Linear(50f, 40f, 50f, 82f, longArrayOf(0xFFFFFFFF, 0xFFDDE9FA), floatArrayOf(0f, 1f))),
            DockLayer("M40 62 L78 62 C83.52 62 88 66.48 88 72 L88 72 C88 77.52 83.52 82 78 82 L40 82 C34.48 82 30 77.52 30 72 L30 72 C30 66.48 34.48 62 40 62 Z", fill = DockPaint.Linear(50f, 40f, 50f, 82f, longArrayOf(0xFFFFFFFF, 0xFFDDE9FA), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
    DockApp(
        "Workshop",
        listOf(
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 100f, longArrayOf(0xFFDFE2E6, 0xFF9AA1AB), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 0f, 50f, 52f, longArrayOf(0x42FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f))),
            DockLayer("M79.5 53 L86.93 55.32 L86.11 61.07 L78.33 61.23 L75.55 67.75 L80.82 73.48 L77.24 78.04 L70.42 74.29 L64.75 78.55 L66.45 86.14 L61.06 88.31 L57.04 81.65 L50 82.5 L47.68 89.93 L41.93 89.11 L41.77 81.33 L35.25 78.55 L29.52 83.82 L24.96 80.24 L28.71 73.42 L24.45 67.75 L16.86 69.45 L14.69 64.06 L21.35 60.04 L20.5 53 L13.07 50.68 L13.89 44.93 L21.67 44.77 L24.45 38.25 L19.18 32.52 L22.76 27.96 L29.58 31.71 L35.25 27.45 L33.55 19.86 L38.94 17.69 L42.96 24.35 L50 23.5 L52.32 16.07 L58.07 16.89 L58.23 24.67 L64.75 27.45 L70.48 22.18 L75.04 25.76 L71.29 32.58 L75.55 38.25 L83.14 36.55 L85.31 41.94 L78.65 45.96 Z", fill = DockPaint.Solid(0x4C000000), blur = 2.5f),
            DockLayer("M79.5 50 L86.93 52.32 L86.11 58.07 L78.33 58.23 L75.55 64.75 L80.82 70.48 L77.24 75.04 L70.42 71.29 L64.75 75.55 L66.45 83.14 L61.06 85.31 L57.04 78.65 L50 79.5 L47.68 86.93 L41.93 86.11 L41.77 78.33 L35.25 75.55 L29.52 80.82 L24.96 77.24 L28.71 70.42 L24.45 64.75 L16.86 66.45 L14.69 61.06 L21.35 57.04 L20.5 50 L13.07 47.68 L13.89 41.93 L21.67 41.77 L24.45 35.25 L19.18 29.52 L22.76 24.96 L29.58 28.71 L35.25 24.45 L33.55 16.86 L38.94 14.69 L42.96 21.35 L50 20.5 L52.32 13.07 L58.07 13.89 L58.23 21.67 L64.75 24.45 L70.48 19.18 L75.04 22.76 L71.29 29.58 L75.55 35.25 L83.14 33.55 L85.31 38.94 L78.65 42.96 Z", fill = DockPaint.Linear(50f, 13f, 50f, 87f, longArrayOf(0xFF858C96, 0xFF474C54), floatArrayOf(0f, 1f))),
            DockLayer("M71 50 C71 61.6 61.6 71 50 71 C38.4 71 29 61.6 29 50 C29 38.4 38.4 29 50 29 C61.6 29 71 38.4 71 50 Z", fill = DockPaint.Linear(50f, 29f, 50f, 71f, longArrayOf(0xFFEEF0F2, 0xFFB3B8BF), floatArrayOf(0f, 1f))),
            DockLayer("M58.5 50 C58.5 54.69 54.69 58.5 50 58.5 C45.31 58.5 41.5 54.69 41.5 50 C41.5 45.31 45.31 41.5 50 41.5 C54.69 41.5 58.5 45.31 58.5 50 Z", fill = DockPaint.Linear(50f, 41f, 50f, 59f, longArrayOf(0xFF4D525A, 0xFF6D737C), floatArrayOf(0f, 1f))),
            DockLayer(DOCK_SQUIRCLE, fill = DockPaint.Linear(50f, 60f, 50f, 100f, longArrayOf(0x00000000, 0x1A000000), floatArrayOf(0f, 1f))),
        ),
    ),
)
