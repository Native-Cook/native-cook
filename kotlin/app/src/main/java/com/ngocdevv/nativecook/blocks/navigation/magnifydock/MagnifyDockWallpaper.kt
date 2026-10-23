package com.ngocdevv.nativecook.blocks.navigation.magnifydock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * The wallpaper: a blue-hour mountain range drawn in four ridges that fade into the haze with
 * distance, under a sky that warms towards the horizon and a low sun behind the farthest ridge.
 * Each ridge is a fixed midpoint-displacement profile of 65 heights (0 to 1), the same as the
 * React Native version's.
 */

private val Ridges = listOf(
    doubleArrayOf(
        0.0, 0.11, 0.193, 0.21, 0.233, 0.239, 0.283, 0.325, 0.282, 0.323, 0.28, 0.29, 0.241, 0.285, 0.293, 0.359,
        0.457, 0.388, 0.365, 0.381, 0.433, 0.424, 0.488, 0.534, 0.534, 0.514, 0.512, 0.603, 0.635, 0.689, 0.763,
        0.918, 0.994, 1.0, 0.946, 0.922, 0.984, 0.869, 0.803, 0.826, 0.778, 0.758, 0.744, 0.775, 0.723, 0.665, 0.625,
        0.549, 0.546, 0.53, 0.492, 0.432, 0.325, 0.323, 0.362, 0.342, 0.392, 0.389, 0.414, 0.441, 0.389, 0.334,
        0.235, 0.125, 0.08,
    ),
    doubleArrayOf(
        0.174, 0.197, 0.186, 0.163, 0.126, 0.108, 0.097, 0.116, 0.112, 0.035, 0.009, 0.0, 0.042, 0.073, 0.132, 0.159,
        0.135, 0.171, 0.255, 0.27, 0.347, 0.387, 0.49, 0.501, 0.585, 0.593, 0.637, 0.651, 0.653, 0.801, 0.898, 0.936,
        1.0, 0.904, 0.828, 0.746, 0.698, 0.688, 0.708, 0.696, 0.639, 0.655, 0.664, 0.677, 0.728, 0.694, 0.723, 0.731,
        0.695, 0.664, 0.601, 0.556, 0.459, 0.376, 0.336, 0.306, 0.269, 0.262, 0.188, 0.218, 0.233, 0.256, 0.208,
        0.173, 0.19,
    ),
    doubleArrayOf(
        0.512, 0.568, 0.594, 0.585, 0.496, 0.553, 0.696, 0.739, 0.767, 0.726, 0.781, 0.851, 0.95, 0.889, 0.894,
        0.983, 1.0, 0.996, 0.947, 0.999, 0.974, 0.907, 0.897, 0.907, 0.874, 0.854, 0.795, 0.718, 0.638, 0.692, 0.663,
        0.625, 0.563, 0.535, 0.551, 0.554, 0.515, 0.537, 0.562, 0.531, 0.556, 0.619, 0.658, 0.65, 0.732, 0.683,
        0.644, 0.695, 0.734, 0.639, 0.642, 0.606, 0.54, 0.434, 0.409, 0.413, 0.387, 0.357, 0.358, 0.313, 0.362,
        0.274, 0.087, 0.001, 0.0,
    ),
    doubleArrayOf(
        0.804, 0.841, 0.8, 0.814, 0.901, 0.911, 0.95, 0.961, 0.951, 0.982, 0.995, 0.972, 1.0, 0.967, 0.871, 0.904,
        0.88, 0.849, 0.851, 0.763, 0.733, 0.65, 0.539, 0.507, 0.49, 0.501, 0.485, 0.425, 0.366, 0.302, 0.255, 0.108,
        0.039, 0.0, 0.02, 0.052, 0.091, 0.149, 0.17, 0.169, 0.179, 0.226, 0.233, 0.226, 0.213, 0.275, 0.275, 0.377,
        0.454, 0.459, 0.492, 0.455, 0.477, 0.47, 0.534, 0.584, 0.709, 0.786, 0.804, 0.817, 0.77, 0.787, 0.763, 0.698,
        0.697,
    ),
)

/** Each ridge: its foot and height as fractions of the screen's height, its colour at the crest and at the foot. */
private class RidgeStyle(val base: Float, val rise: Float, val crest: Color, val foot: Color)

private val RidgeStyles = listOf(
    RidgeStyle(0.53f, 0.14f, Color(0xFF9AA6BB), Color(0xFFCDC6C6)),
    RidgeStyle(0.62f, 0.13f, Color(0xFF6C7E9C), Color(0xFFA2A5B3)),
    RidgeStyle(0.72f, 0.12f, Color(0xFF45577A), Color(0xFF747E98)),
    RidgeStyle(0.86f, 0.1f, Color(0xFF222F47), Color(0xFF161F30)),
)

private val Sky = arrayOf(
    0f to Color(0xFF1D3150),
    0.28f to Color(0xFF3D5880),
    0.42f to Color(0xFF8B93A8),
    0.52f to Color(0xFFE5BD9C),
    0.6f to Color(0xFFF2CFAE),
)

// The sun: centre and radius as fractions of the screen's width (x, r, halo) and height (y).
private const val SUN_X = 0.7f
private const val SUN_Y = 0.455f
private const val SUN_R = 0.045f
private const val SUN_HALO = 0.24f

/** The wallpaper for a screen [w] × [h] dp, the scope in dp. */
internal fun DrawScope.drawDockWallpaper(w: Float, h: Float) {
    drawRect(Brush.linearGradient(*Sky, start = Offset(0f, 0f), end = Offset(0f, h)))
    val c = Offset(w * SUN_X, h * SUN_Y)
    drawCircle(
        Brush.radialGradient(listOf(Color(255, 228, 196).copy(alpha = 0.55f), Color(255, 228, 196).copy(alpha = 0f)), center = c, radius = w * SUN_HALO),
        radius = w * SUN_HALO,
        center = c,
    )
    drawCircle(Color(0xFFFFF2DF), radius = w * SUN_R, center = c)
    for ((i, heights) in Ridges.withIndex()) {
        val s = RidgeStyles[i]
        val path = Path()
        val n = heights.size - 1
        for (k in 0..n) {
            val x = -2f + (w + 4f) * k / n
            val y = (s.base - s.rise * heights[k].toFloat()) * h
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(w + 2f, h + 2f)
        path.lineTo(-2f, h + 2f)
        path.close()
        val top = (s.base - s.rise) * h
        val bottom = if (i == Ridges.size - 1) h else (s.base + 0.1f) * h
        drawPath(path, Brush.linearGradient(listOf(s.crest, s.foot), start = Offset(0f, top), end = Offset(0f, bottom)))
    }
}
