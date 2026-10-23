package com.ngocdevv.nativecook.blocks.modals.leveluptakeover

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.hypot

internal const val BADGE_WIDTH = 152f
internal const val BADGE_HEIGHT = 132f

/** Room round the badge for its shadow and sparkles. */
private const val MARGIN = 28f

/** The badge's box, margin included. */
internal const val BADGE_BOX_WIDTH = BADGE_WIDTH + MARGIN * 2
internal const val BADGE_BOX_HEIGHT = BADGE_HEIGHT + MARGIN * 2

private const val TOP = MARGIN
private const val BOTTOM = MARGIN + BADGE_HEIGHT
private const val SHINE_WIDTH = 30f

/** A flat-top hexagon [inset] points inside the badge, its corners rounded by [corner]. */
private fun hexagon(inset: Float, corner: Float): Path {
    val w = BADGE_WIDTH - inset * 2
    val h = BADGE_HEIGHT - inset * 2
    val quarter = w / 4
    val ox = MARGIN + inset
    val oy = MARGIN + inset
    val points = listOf(
        Offset(ox + quarter, oy),
        Offset(ox + w - quarter, oy),
        Offset(ox + w, oy + h / 2),
        Offset(ox + w - quarter, oy + h),
        Offset(ox + quarter, oy + h),
        Offset(ox, oy + h / 2),
    )
    return Path().apply {
        points.indices.forEach { i ->
            val p = points[(i + points.size - 1) % points.size]
            val c = points[i]
            val n = points[(i + 1) % points.size]
            // Cut each corner short along both edges and round it with a quad.
            val a = hypot(c.x - p.x, c.y - p.y)
            val b = hypot(n.x - c.x, n.y - c.y)
            val sx = c.x + (p.x - c.x) / a * corner
            val sy = c.y + (p.y - c.y) / a * corner
            if (i == 0) moveTo(sx, sy) else lineTo(sx, sy)
            quadraticTo(c.x, c.y, c.x + (n.x - c.x) / b * corner, c.y + (n.y - c.y) / b * corner)
        }
        close()
    }
}

/** A four-pointed sparkle centred on (x, y). */
private fun sparkle(x: Float, y: Float, r: Float): Path {
    val k = r * 0.22f
    return Path().apply {
        moveTo(x, y - r)
        quadraticTo(x + k, y - k, x + r, y)
        quadraticTo(x + k, y + k, x, y + r)
        quadraticTo(x - k, y + k, x - r, y)
        quadraticTo(x - k, y - k, x, y - r)
        close()
    }
}

private val Bezel = hexagon(0f, 22f)
private val Rim = hexagon(9f, 17f)
private val Body = hexagon(14f, 15f)
private val Face = hexagon(26f, 11f)
private val Sparkles = listOf(
    sparkle(MARGIN + BADGE_WIDTH - 4f, MARGIN + 8f, 9f),
    sparkle(MARGIN + BADGE_WIDTH + 12f, MARGIN + 30f, 5f),
    sparkle(MARGIN + 8f, MARGIN + BADGE_HEIGHT - 10f, 6f),
)

/** The band of light leans forward: x moves left by 0.45 of y. */
private val Lean = Matrix().apply { values[Matrix.SkewX] = -0.45f }

/** A blurred copy of a path, offset down, for the bezel's two shadows (Skia's blur is a sigma). */
private class PathShadow(color: Color, sigma: Float, val dy: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
}

private val BezelShadows = listOf(PathShadow(Color(214, 128, 0).copy(alpha = 0.38f), 16f, 12f), PathShadow(Color(160, 90, 0).copy(alpha = 0.25f), 3f, 2f))
private val BezelPath = Bezel.asAndroidPath()

private fun levelStyle(density: Float) = TextStyle(
    color = Color.White,
    fontSize = 64.sp,
    lineHeight = 72.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = (-1).sp,
    shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFD06F00), Offset(0f, 3f * density), 0.5f * density),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun vertical(from: Float, to: Float, vararg stops: Pair<Float, Color>) = Brush.verticalGradient(*stops, startY = from, endY = to)

/**
 * The level badge: a white bezel round a glossy gold hexagon, the number on its face, a band of
 * light that sweeps across once and a few sparkles. [sweep] (0 to 1) is the band's trip from the
 * left of the badge to its right; [twinkle] is the sparkles' opacity.
 */
@Composable
internal fun Badge(level: Int, sweep: () -> Float, twinkle: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.size(BADGE_BOX_WIDTH.dp, BADGE_BOX_HEIGHT.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drawIntoCanvas { canvas ->
                        BezelShadows.forEach { shadow ->
                            canvas.nativeCanvas.save()
                            canvas.nativeCanvas.translate(0f, shadow.dy)
                            canvas.nativeCanvas.drawPath(BezelPath, shadow.paint)
                            canvas.nativeCanvas.restore()
                        }
                    }
                }
                drawPath(Bezel, vertical(TOP, BOTTOM, 0f to Color.White, 1f to Color(0xFFFFF1C9)))
                drawPath(Rim, vertical(TOP + 9f, BOTTOM - 9f, 0f to Color(0xFFFFB81A), 1f to Color(0xFFE47400)))
                drawPath(Body, vertical(TOP + 14f, BOTTOM - 14f, 0f to Color(0xFFFFD84A), 1f to Color(0xFFFFA812)))
                drawPath(Face, vertical(TOP + 26f, BOTTOM - 26f, 0f to Color(0xFFFFE98A), 0.55f to Color(0xFFFFC21A), 1f to Color(0xFFFFB000)))
                drawPath(Face, Color.White.copy(alpha = 0.55f), style = Stroke(2f))
                clipPath(Body) {
                    // Gloss over the upper half, and the moving band of light.
                    val gloss = BADGE_HEIGHT * 0.48f
                    drawRect(
                        vertical(TOP, TOP + gloss, 0f to Color.White.copy(alpha = 0.5f), 1f to Color.White.copy(alpha = 0.06f)),
                        Offset(MARGIN, TOP), androidx.compose.ui.geometry.Size(BADGE_WIDTH, gloss),
                    )
                    withTransform({
                        translate(MARGIN - SHINE_WIDTH * 2 + sweep() * (BADGE_WIDTH + SHINE_WIDTH * 4), 0f)
                        transform(Lean)
                    }) {
                        val band = Rect(0f, TOP - 8f, SHINE_WIDTH, TOP - 8f + BADGE_HEIGHT + 16f)
                        drawRect(Color.White.copy(alpha = 0.6f), band.topLeft, band.size)
                    }
                }
                val sparkle = twinkle()
                if (sparkle > 0f) Sparkles.forEach { drawPath(it, Color.White, alpha = sparkle) }
            }
        }
        val density = LocalDensity.current.density
        Text("$level", style = remember(density) { levelStyle(density) })
    }
}
