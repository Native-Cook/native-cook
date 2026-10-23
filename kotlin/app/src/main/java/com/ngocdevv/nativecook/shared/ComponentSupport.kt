package com.ngocdevv.nativecook.shared

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

internal const val TAU = (Math.PI * 2).toFloat()

/** Strong ease-out used for every state change, same curve as the React Native versions. */
internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal const val PRESS_SCALE = 0.97f
internal const val PRESS_SCALE_MS = 130

internal fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

/** True when the user turned animations off in system settings ("Remove animations"). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Lays the content out [bleed] larger than its parent on every side without affecting the
 * parent's size, so glows and shadows have a real canvas to draw into.
 */
internal fun Modifier.bleed(bleed: Dp) = layout { measurable, constraints ->
    val b = bleed.roundToPx()
    val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth + b * 2, constraints.maxHeight + b * 2),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-b, -b) }
}

/**
 * Runs [block] in dp units inside a canvas produced by [bleed]: `x`/`y` is the button's top-left
 * corner and `w`/`h` its size, exactly like the Skia canvases in the React Native versions.
 */
internal inline fun DrawScope.surface(
    bleed: Dp,
    block: DrawScope.(x: Float, y: Float, w: Float, h: Float) -> Unit,
) {
    val b = bleed.roundToPx()
    val origin = b / density
    val w = (size.width - b * 2) / density
    val h = (size.height - b * 2) / density
    scale(density, density, pivot = Offset.Zero) { block(origin, origin, w, h) }
}

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
internal fun sigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/**
 * A blur applied after shading. A mask-filter blur on a gradient-painted stroke smears the
 * stroke's coverage but keeps the gradient's hard angular edges, which shows up as a wedge.
 * Needs API 31; null below that, where callers hide the bloom layer instead.
 */
@Composable
internal fun rememberBloom(sigma: Dp): RenderEffect? {
    val density = LocalDensity.current
    return remember(sigma, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = sigmaToRadius(with(density) { sigma.toPx() })
            BlurEffect(radius, radius, TileMode.Decal)
        } else {
            null
        }
    }
}

/** A solid rounded rect with a soft edge, for glows and shadows. Drawn in the current (dp) units. */
internal class SoftRoundRect(color: Color, sigma: Float) {
    private val baseAlpha = color.alpha
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = 1f).toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        paint.alpha = (baseAlpha * alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
}

/**
 * A capsule stroke painted with a sweep gradient that can be rotated around the capsule's centre.
 * Stops run clockwise from 3 o'clock, so 0.75 is the top of the button.
 */
internal class SweepStroke(private val colors: IntArray, private val stops: FloatArray) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    private var cx = Float.NaN
    private var cy = Float.NaN

    fun draw(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        strokeWidth: Float,
        turns: Float,
        alpha: Float = 1f,
    ) {
        val centerX = x + w / 2f
        val centerY = y + h / 2f
        val gradient = shader?.takeIf { cx == centerX && cy == centerY }
            ?: SweepGradient(centerX, centerY, colors, stops).also {
                shader = it
                cx = centerX
                cy = centerY
            }
        matrix.setRotate(turns * 360f, centerX, centerY)
        gradient.setLocalMatrix(matrix)
        paint.shader = gradient
        paint.strokeWidth = strokeWidth
        paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
}

internal fun sweepStroke(vararg stops: Pair<Float, Color>) = SweepStroke(
    IntArray(stops.size) { stops[it].second.toArgb() },
    FloatArray(stops.size) { stops[it].first },
)
