package com.ngocdevv.nativecook.blocks.progress.sandhourglass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// A slow frame runs at most this many fixed ticks.
private const val MAX_TICKS = 3

// The glass, in dp at scale 1, around its centre. The grid fills the inside; the wall is GLASS_WALL
// thick. Each end: a gap, a narrow collar where the glass is set, then a pale oak cap.
private const val INNER_HALF_H = SAND_ROWS * SAND_CELL / 2f
private const val GLASS_WALL = 3f
private const val CAP_GAP = 3f
private const val COLLAR_H = 4f
private const val CAP_H = 15f
private const val CAP_HALF_W = 96f
private const val CAP_RADIUS = 6f
private const val HALF_HEIGHT = INNER_HALF_H + CAP_GAP + COLLAR_H + CAP_H
// The glass shrinks by up to LIFT while it lies on its side, as if lifted off the table to turn it.
private const val LIFT = 0.1f
// A drag turns the glass when it starts this far from its centre (dp).
private const val GRAB_MIN = 26f
private const val GRAB_MAX = 260f
// Gesture thresholds standing in for the React Native recognisers: a pan starts after 4 dp, a
// twist after a few degrees, and a tap is a touch lifted within 350 ms.
private const val PAN_SLOP = 4f
private const val TWIST_SLOP = 0.035
private const val TAP_MS = 350L
// A one-finger drag that starts this close to the ruler's line moves the ruler (dp).
private const val RULER_REACH = 48f

// Light comes from the upper left of the screen, whichever way up the glass is.
private const val LIGHT_X = -0.5
private const val LIGHT_Y = -0.866
// The shadow on the table: a point h above the table falls h·SHADOW_RUN to the right and
// h·SHADOW_RISE further back; the glass's depth shows as SHADOW_DEPTH of its width.
private const val SHADOW_RUN = 0.92f
private const val SHADOW_RISE = 0.2f
private const val SHADOW_DEPTH = 0.3f
// The light sweep that marks the end of a session (s).
private const val SWEEP_FOR = 1.15

private const val WALL_LIGHT = 0xFFF6F0E6.toInt()
private const val WALL = 0xFFECE3D5.toInt()
private const val WALL_DEEP = 0xFFDDD1BF.toInt()
private const val TABLE_NEAR = 0xFFD9CAB4.toInt()
private const val TABLE_FAR = 0xFFE4D8C6.toInt()
private const val INK = 0xFF2A2119.toInt()
private const val MUTED = 0xFF8B7A66.toInt()
private const val ACCENT = 0xFFC4532A.toInt()
private const val SHADOW = 0xFF4A3420.toInt()

/**
 * The grain shader. It reads one texel per cell and draws each grain as a small lit bead that
 * overlaps its neighbours a little, so a pile reads as packed sand and the stream as loose grains.
 * `light` points from a grain towards the light, in the glass's frame.
 */
private const val GRAIN_AGSL = """
uniform shader cells;
uniform float cell;
uniform float2 light;

half4 main(float2 p) {
  float2 g = p / cell;
  float2 c = floor(g);
  float2 f = g - c - 0.5;
  half4 best = half4(0.0);
  float cover = 0.0;
  float2 off = float2(0.0);
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float2 o = float2(float(i), float(j));
      half4 s = cells.eval(c + o + 0.5);
      if (s.a > 0.5) {
        // Each cell's grain sits a little off centre and varies in size, so the grid never shows.
        float2 cc = c + o;
        float2 h = fract(sin(float2(dot(cc, float2(127.1, 311.7)), dot(cc, float2(269.5, 183.3)))) * 43758.5453);
        float2 d = f - o - (h - 0.5) * 0.32;
        float r = 0.5 + 0.1 * h.x;
        float k = 1.0 - smoothstep(r - 0.1, r + 0.12, length(d));
        if (k > cover) {
          cover = k;
          best = s;
          off = d;
        }
      }
    }
  }
  if (cover <= 0.0) {
    return half4(0.0);
  }
  float lit = clamp(dot(off, light) / 0.6, -1.0, 1.0);
  float spec = 1.0 - smoothstep(0.0, 0.2, length(off - light * 0.24));
  half3 col = best.rgb * half(1.0 + 0.3 * lit) + half3(0.26, 0.24, 0.2) * half(spec);
  return half4(col * half(cover), half(cover));
}
"""

private fun withAlpha(color: Int, alpha: Double) =
    (((alpha.coerceIn(0.0, 1.0) * ((color ushr 24) and 0xFF)).roundToInt()) shl 24) or (color and 0xFFFFFF)

/** Where everything sits, in dp. */
private class GlassLayout(val width: Float, val height: Float, top: Float, bottom: Float) {
    val rulerY = height - bottom - 70
    val scale: Float
    val cx = width / 2
    val cy: Float
    val baseY: Float
    val horizonY: Float
    val timerY: Float

    init {
        val bandTop = top + 96
        val bandBottom = rulerY - 54 - 112
        scale = max(0.5f, min(1.08f, (bandBottom - bandTop) / (2 * HALF_HEIGHT)))
        val spare = bandBottom - bandTop - 2 * HALF_HEIGHT * scale
        cy = bandTop + spare * 0.4f + HALF_HEIGHT * scale
        baseY = cy + HALF_HEIGHT * scale
        horizonY = baseY - 64 * scale
        timerY = baseY + 82
    }
}

private fun weighted(size: Float, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}

/** The glass's outline from the grain grid's profile, the silhouette for the shadow, and every paint. */
private class GlassParts {
    val inner = Path()
    val outer = Path()
    // Highlights for the left half of the lower bulb; drawn mirrored for the other three quarters.
    val sheen = Path()
    val streak = Path()
    val rim = Path()
    val window = Path()
    val window2 = Path()
    /** Half the glass's silhouette width (caps included) at heights 0 … 2·HALF_HEIGHT from its base. */
    val profile = FloatArray(PROFILE_STEPS + 1)

    init {
        val h = INNER_HALF_H
        val steps = 140
        fun half(t: Double) = ((sandGlassHalfWidth(t) + 0.5) * SAND_CELL).toFloat()
        val xs = FloatArray(steps + 1)
        val outerXs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        for (i in 0..steps) {
            val y = -h + 2 * h * i / steps
            val t = abs(y.toDouble()) / h
            val w = half(t)
            // The wall keeps its thickness along the slope of the funnel.
            val slope = (half(min(1.0, t + 0.01)) - half(max(0.0, t - 0.01))) / (0.02f * h)
            xs[i] = w
            outerXs[i] = w + GLASS_WALL * sqrt(1 + slope * slope)
            ys[i] = y
        }
        fun trace(path: Path, side: FloatArray, capOut: Float) {
            path.moveTo(-side[0], -h - capOut)
            path.lineTo(side[0], -h - capOut)
            for (i in 0..steps) path.lineTo(side[i], ys[i])
            path.lineTo(side[steps], h + capOut)
            path.lineTo(-side[steps], h + capOut)
            for (i in steps downTo 0) path.lineTo(-side[i], ys[i])
            path.close()
        }
        trace(inner, xs, 0f)
        trace(outer, outerXs, CAP_GAP)

        // Strips along the left wall of the lower bulb, following its curve.
        fun strip(path: Path, from: Double, to: Double, inset: (Float) -> Float) {
            for (i in 0..40) {
                val t = from + (to - from) * i / 40
                val x = -inset(half(t))
                val y = (t * h).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        strip(sheen, 0.24, 0.9) { it * 0.78f }
        strip(streak, 0.34, 0.84) { it - 3.2f }
        strip(rim, 0.12, 0.96) { it - 1.4f }
        // A window behind the viewer, reflected in the bulb as two tall panes.
        strip(window, 0.36, 0.8) { it * 0.5f }
        strip(window2, 0.38, 0.78) { it * 0.38f }

        for (i in 0..PROFILE_STEPS) {
            val hh = 2 * HALF_HEIGHT * i / PROFILE_STEPS
            val y = abs(HALF_HEIGHT - hh)
            profile[i] = when {
                y > INNER_HALF_H + CAP_GAP + COLLAR_H -> CAP_HALF_W
                y > INNER_HALF_H -> half(1.0) + 7
                else -> half(min(1.0, (y / INNER_HALF_H).toDouble())) + GLASS_WALL
            }
        }
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val shadowPath = Path()
    val rect = RectF()
    val title = weighted(30f, 700)
    val task = weighted(15f, 500)
    val timer = weighted(64f, 600)
    val digit = (0..9).maxOf { timer.measureText(it.toString()) }
    val sub = weighted(15f, 600)
    val ruler = weighted(12f, 600)
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** A Skia blur of `sigma` (in the canvas's units) as a mask filter; none before API 28. */
    fun blur(sigma: Float, px: Float): BlurMaskFilter? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || sigma <= 0f) return null
        val key = (sigma * px * 8).roundToInt() * 1000 + (px * 10).roundToInt()
        return blurs.getOrPut(key) { BlurMaskFilter(max(0.01f, sigmaToRadius(sigma * px) / px), BlurMaskFilter.Blur.NORMAL) }
    }

    // The grid, one pixel per cell, and the shader that draws each pixel as a bead (API 33+).
    val bitmap: Bitmap = Bitmap.createBitmap(SAND_COLS, SAND_ROWS, Bitmap.Config.ARGB_8888)
    val grains = Paint().apply { isFilterBitmap = false }
    val grainShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(GRAIN_AGSL).also { shader ->
            val cells = BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
            cells.filterMode = BitmapShader.FILTER_MODE_NEAREST
            shader.setInputShader("cells", cells)
            shader.setFloatUniform("cell", SAND_CELL)
        }
    } else {
        null
    }

    companion object {
        const val PROFILE_STEPS = 64
    }
}

/** A horizontal cylinder gradient across [x0, x1]: dark at both ends, brightest at `light`. */
private fun turned(x0: Float, x1: Float, light: Float, dark: Long, mid: Long, bright: Long) = LinearGradient(
    x0, 0f, x1, 0f,
    intArrayOf(dark.toInt(), mid.toInt(), bright.toInt(), mid.toInt(), dark.toInt()),
    floatArrayOf(0f, light - 0.22f, light, light + 0.26f, 1f),
    Shader.TileMode.CLAMP,
)

/** A stroke in the canvas's units, softened by a Skia blur of `blur`. */
private fun stroke(c: NativeCanvas, p: GlassParts, path: Path, color: Int, alpha: Double, width: Float, blur: Float, px: Float) {
    if (alpha <= 0.003) return
    p.line.color = withAlpha(color, alpha)
    p.line.strokeWidth = width
    p.line.maskFilter = p.blur(blur, px)
    c.drawPath(path, p.line)
    p.line.maskFilter = null
}

private fun strokeLine(c: NativeCanvas, p: GlassParts, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, alpha: Double, width: Float, blur: Float, px: Float) {
    if (alpha <= 0.003) return
    p.line.color = withAlpha(color, alpha)
    p.line.strokeWidth = width
    p.line.maskFilter = p.blur(blur, px)
    c.drawLine(x0, y0, x1, y1, p.line)
    p.line.maskFilter = null
}

/** The room: a warm wall lit from the left, the table the glass stands on, and the heading. */
private fun drawRoom(c: NativeCanvas, l: GlassLayout, p: GlassParts, title: String, task: String, top: Float, density: Float) {
    p.fill.shader = RadialGradient(
        l.width * 0.1f, l.cy - 120, max(l.width, l.height) * 0.9f,
        intArrayOf(WALL_LIGHT, WALL, WALL_DEEP), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    p.fill.color = 0xFF000000.toInt()
    c.drawRect(0f, 0f, l.width, l.height, p.fill)
    p.fill.shader = LinearGradient(0f, l.horizonY, 0f, l.height, TABLE_FAR, TABLE_NEAR, Shader.TileMode.CLAMP)
    c.drawRect(0f, l.horizonY, l.width, l.height, p.fill)
    p.fill.shader = null
    // The table's back edge, softened.
    strokeLine(c, p, 0f, l.horizonY, l.width, l.horizonY, 0xFFBFAE96.toInt(), 0.45, 1f, 0.6f, density)
    strokeLine(c, p, 0f, l.horizonY + 1.2f, l.width, l.horizonY + 1.2f, 0xFFFFFAF2.toInt(), 0.5, 1f, 0.4f, density)
    p.title.color = INK
    c.drawText(title, 24f, top + 44, p.title)
    p.task.color = MUTED
    c.drawText(task, 24f, top + 68, p.task)
}

/** The shadow on the table, lying to the right, and the light the empty glass lets through it. */
private fun drawShadow(c: NativeCanvas, l: GlassLayout, p: GlassParts, e: HourglassEngine, s: Float, sin2: Float, density: Float) {
    val shade = 1 - 0.85f * sin2
    if (shade <= 0.02f) return
    val bottomY = l.cy + HALF_HEIGHT * s
    val rise = max(0f, l.baseY - bottomY)
    val n = p.profile.size
    val path = p.shadowPath
    path.rewind()
    for (i in 0 until n) {
        val h = rise + 2 * HALF_HEIGHT * i / (n - 1) * s
        val r = p.profile[i] * s * SHADOW_DEPTH
        val x = l.cx + h * SHADOW_RUN
        val y = l.baseY - h * SHADOW_RISE - r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in n - 1 downTo 0) {
        val h = rise + 2 * HALF_HEIGHT * i / (n - 1) * s
        val r = p.profile[i] * s * SHADOW_DEPTH
        path.lineTo(l.cx + h * SHADOW_RUN, l.baseY - h * SHADOW_RISE + r)
    }
    path.close()
    p.fill.color = withAlpha(SHADOW, 0.24 * shade)
    p.fill.maskFilter = p.blur(4.5f + 6 * sin2, density)
    c.drawPath(path, p.fill)
    // Where light passes through an empty bulb it gathers into a warm, brighter patch.
    val upIsFrameTop = cos(e.angle) >= 0
    val upper = if (upIsFrameTop) e.sand.upper else e.sand.lower
    val lower = if (upIsFrameTop) e.sand.lower else e.sand.upper
    val bulbs = arrayOf(
        floatArrayOf(HALF_HEIGHT + INNER_HALF_H * 0.5f, 1 - upper.toFloat() / SAND_GRAINS),
        floatArrayOf(HALF_HEIGHT - INNER_HALF_H * 0.5f, 1 - lower.toFloat() / SAND_GRAINS),
    )
    p.fill.maskFilter = p.blur(5f, density)
    for (b in bulbs) {
        val h = rise + b[0] * s
        val alpha = 0.55 * b[1] * shade
        if (alpha <= 0.005) continue
        val gx = l.cx + h * SHADOW_RUN
        val gy = l.baseY - h * SHADOW_RISE
        p.fill.color = withAlpha(0xFFFFF4DC.toInt(), alpha)
        p.rect.set(gx - 34 * s, gy - 7 * s, gx + 34 * s, gy + 7 * s)
        c.drawOval(p.rect, p.fill)
    }
    // Contact shadow under the base.
    p.fill.color = withAlpha(SHADOW, 0.34 * shade * shade)
    p.fill.maskFilter = p.blur(3f, density)
    p.rect.set(l.cx - CAP_HALF_W * s * 1.02f, l.baseY - 4, l.cx + CAP_HALF_W * s * 1.02f, l.baseY + 5)
    c.drawOval(p.rect, p.fill)
    p.fill.maskFilter = null
}

/** One end: a narrow collar where the glass is set, then a pale oak cap with a little grain. */
private fun drawCap(c: NativeCanvas, p: GlassParts, side: Float, light: Float, cosA: Float, px: Float) {
    // `side` is -1 for the cap above the glass's own middle, 1 below. All y here grow outwards.
    val collarNear = INNER_HALF_H + CAP_GAP
    val capNear = collarNear + COLLAR_H
    val capFar = capNear + CAP_H
    fun span(near: Float, far: Float) = if (side < 0) -far else near
    val collarHalf = ((sandGlassHalfWidth(1.0) + 0.5) * SAND_CELL).toFloat() + 7
    p.fill.shader = turned(-collarHalf, collarHalf, light, 0xFF5A3F27, 0xFF8D6842, 0xFFCFA97C)
    p.fill.color = 0xFF000000.toInt()
    val ct = span(collarNear - 1, capNear + 1)
    p.rect.set(-collarHalf, ct, collarHalf, ct + COLLAR_H + 2)
    c.drawRoundRect(p.rect, 1.5f, 1.5f, p.fill)
    val st = span(capNear, capFar)
    p.rect.set(-CAP_HALF_W, st, CAP_HALF_W, st + CAP_H)
    p.fill.shader = turned(-CAP_HALF_W, CAP_HALF_W, light, 0xFF7A5636, 0xFFB88D60, 0xFFEFD6B0)
    c.drawRoundRect(p.rect, CAP_RADIUS, CAP_RADIUS, p.fill)
    p.fill.shader = null
    // Grain: a few faint lines along the slab.
    c.save()
    c.clipRect(p.rect)
    for (g in 0 until 3) {
        val y = st + 3.5f + g * 4.2f
        strokeLine(c, p, -CAP_HALF_W + 8 + g * 11, y, CAP_HALF_W - 14 + g * 5, y + 0.6f, 0xFF6B4A2C.toInt(), 0.14, 0.8f, 0.3f, px)
    }
    c.restore()
    // The edge that faces the top of the screen catches the light; the other falls into shadow.
    val upFacing = max(0f, if (side < 0) cosA else -cosA)
    val downFacing = max(0f, if (side < 0) -cosA else cosA)
    val outerEdge = if (side < 0) -capFar + 0.7f else capFar - 0.7f
    if (upFacing > 0.01f) strokeLine(c, p, -CAP_HALF_W + 6, outerEdge, CAP_HALF_W - 6, outerEdge, 0xFFFFF6E6.toInt(), 0.7 * upFacing, 1.1f, 0.3f, px)
    if (downFacing > 0.01f) strokeLine(c, p, -CAP_HALF_W + 6, outerEdge, CAP_HALF_W - 6, outerEdge, 0xFF3B2715.toInt(), 0.35 * downFacing, 1.1f, 0.4f, px)
}

/** The glass and everything turned with it, around the origin. `sweep` is 0…1 across, or < 0. */
private fun drawHourglass(c: NativeCanvas, p: GlassParts, angle: Double, sweep: Double, px: Float) {
    val cosA = cos(angle).toFloat()
    val sinA = sin(angle).toFloat()
    // Where the turned parts are brightest (0…1 across) and how much each side of the glass faces
    // the light: the light stays on the left of the screen as the glass turns.
    val light = 0.5f - 0.17f * cosA
    val leftLit = (1 + cosA) / 2

    // The glass body: a little brighter than the room, darker towards the wall where it is thickest.
    p.fill.color = withAlpha(0xFFFFFFFF.toInt(), 0.2)
    c.drawPath(p.inner, p.fill)
    c.save()
    c.clipPath(p.inner)
    stroke(c, p, p.inner, 0xFF7B6650.toInt(), 0.2, 12f, 7f, px)
    c.restore()

    val shader = p.grainShader
    c.save()
    c.translate(-SAND_COLS * SAND_CELL / 2f, -INNER_HALF_H)
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lx = cosA * LIGHT_X + sinA * LIGHT_Y
        val ly = -sinA * LIGHT_X + cosA * LIGHT_Y
        shader.setFloatUniform("light", lx.toFloat(), ly.toFloat())
        p.grains.shader = shader
        c.drawRect(0f, 0f, SAND_COLS * SAND_CELL, SAND_ROWS * SAND_CELL, p.grains)
    } else {
        // Below Android 13 there is no runtime shader: each grain is drawn as its plain coloured cell.
        p.rect.set(0f, 0f, SAND_COLS * SAND_CELL, SAND_ROWS * SAND_CELL)
        c.drawBitmap(p.bitmap, null, p.rect, p.grains)
    }
    c.restore()

    // The wall: a crisp outer edge, a fainter inner one, and the strips the light makes.
    stroke(c, p, p.outer, 0xFF6E5844.toInt(), 0.5, 1.1f, 0f, px)
    stroke(c, p, p.inner, 0xFF6E5844.toInt(), 0.2, 0.8f, 0f, px)
    for (q in 0 until 4) {
        val sx = if (q % 2 == 0) 1f else -1f
        val sy = if (q < 2) 1f else -1f
        val lit = (if (sx > 0) leftLit else 1 - leftLit).toDouble()
        c.save()
        c.scale(sx, sy)
        stroke(c, p, p.sheen, 0xFFFFFFFF.toInt(), 0.32 * lit, 8f, 3.5f, px)
        stroke(c, p, p.window, 0xFFFFFFFF.toInt(), 0.5 * lit, 4.5f, 1.1f, px)
        stroke(c, p, p.window2, 0xFFFFFFFF.toInt(), 0.32 * lit, 3f, 1.1f, px)
        stroke(c, p, p.streak, 0xFFFFFFFF.toInt(), 0.85 * lit + 0.08, 1.4f, 0.3f, px)
        stroke(c, p, p.rim, 0xFFFFFAF0.toInt(), 0.16 + 0.3 * (1 - lit), 1.3f, 0.6f, px)
        c.restore()
    }
    // A small glint where the glass pinches into the neck.
    val glintX = -4.5f * cosA
    p.line.color = withAlpha(0xFFFFFFFF.toInt(), 0.8)
    p.line.strokeWidth = 1f
    p.line.maskFilter = p.blur(1.2f, px)
    p.rect.set(glintX - 1.2f, -7f, glintX + 1.2f, 7f)
    c.drawOval(p.rect, p.line)
    p.line.maskFilter = null

    // The end of a session: a band of light passes across the glass, left to right on screen.
    if (sweep in 0.0..1.0) {
        c.save()
        c.clipPath(p.outer)
        val dir = if (cosA >= 0) 1f else -1f
        val x = dir * (-150 + 300 * sweep.toFloat())
        p.fill.shader = LinearGradient(
            x - 46 * dir, -60 * dir, x + 46 * dir, 60 * dir,
            intArrayOf(0x00FFFFFF, 0x9EFFFAEE.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        p.fill.color = 0xFF000000.toInt()
        c.drawRect(-200f, -INNER_HALF_H - 10, 200f, INNER_HALF_H + 10, p.fill)
        p.fill.shader = null
        c.restore()
    }

    drawCap(c, p, -1f, light, cosA, px)
    drawCap(c, p, 1f, light, cosA, px)
}

/** Text with every digit in a slot of the same width, so a count does not jitter. Centred on cx. */
private fun drawTabular(c: NativeCanvas, text: String, cx: Float, y: Float, paint: Paint, digit: Float) {
    var total = 0f
    for (ch in text) total += if (ch in '0'..'9') digit else paint.measureText(ch.toString())
    var x = cx - total / 2
    for (ch in text) {
        val s = ch.toString()
        val w = paint.measureText(s)
        val slot = if (ch in '0'..'9') digit else w
        c.drawText(s, x + (slot - w) / 2, y, paint)
        x += slot
    }
}

private fun drawCentred(c: NativeCanvas, text: String, cx: Float, y: Float, paint: Paint, color: Int, alpha: Double) {
    if (alpha <= 0.005) return
    paint.color = withAlpha(color, alpha)
    c.drawText(text, cx - paint.measureText(text) / 2, y, paint)
}

/** A label swap in two halves: the old one fades out over the first half of `t`, the new one in over the second. */
private fun fadeOut(t: Double) = (1 - 2 * t).coerceIn(0.0, 1.0)

private fun fadeIn(t: Double) = (2 * t - 1).coerceIn(0.0, 1.0)

/** The timer and its line, then the ruler: a tick a minute, a longer one and a number every five. */
private fun drawReadout(c: NativeCanvas, l: GlassLayout, p: GlassParts, e: HourglassEngine) {
    val done = e.doneBlend
    if (fadeOut(done) > 0.005) {
        p.timer.color = withAlpha(INK, fadeOut(done))
        drawTabular(c, formatClock(e.secondsLeft), l.cx, l.timerY, p.timer, p.digit)
    }
    drawCentred(c, "Well done", l.cx, l.timerY, p.timer, INK, fadeIn(done))
    val run = e.runBlend
    drawCentred(c, "Turn the glass to begin", l.cx, l.timerY + 32, p.sub, MUTED, fadeOut(run))
    drawCentred(c, e.endsAtText(), l.cx, l.timerY + 32, p.sub, MUTED, fadeIn(run) * fadeOut(done))
    drawCentred(c, "${e.duration} minutes of focus", l.cx, l.timerY + 32, p.sub, ACCENT, fadeIn(done))

    val show = 1 - run
    if (show <= 0.01) return
    val halfSpan = l.width / 2 - 16
    p.line.strokeCap = Paint.Cap.ROUND
    for (m in MIN_MINUTES.toInt()..MAX_MINUTES.toInt()) {
        val x = l.cx + ((m - e.minutes) * RULER_PX).toFloat()
        val d = abs(x - l.cx) / halfSpan
        if (d >= 1) continue
        val fade = (1 - d * d) * show
        val major = m % 5 == 0
        val h = if (major) 22f else 12f
        p.line.color = withAlpha(INK, (if (major) 0.78 else 0.4) * fade)
        p.line.strokeWidth = if (major) 2f else 1.4f
        c.drawLine(x, l.rulerY - h / 2, x, l.rulerY + h / 2, p.line)
        if (major) drawCentred(c, "$m", x, l.rulerY + 34, p.ruler, MUTED, fade)
    }
    p.line.color = withAlpha(ACCENT, show)
    p.line.strokeWidth = 3f
    c.drawLine(l.cx, l.rulerY - 20, l.cx, l.rulerY + 20, p.line)
    p.line.strokeCap = Paint.Cap.BUTT
    p.fill.color = withAlpha(ACCENT, show)
    c.drawCircle(l.cx, l.rulerY - 25, 3.2f, p.fill)
}

/**
 * A focus timer with an hourglass as its clock: set the minutes on the ruler, turn the glass over and
 * a few thousand grains of terracotta sand, simulated one cell at a time, run through the neck at the
 * rate that empties it on time. The glass stands on a table in afternoon light, casting a long shadow
 * that brightens where light passes through the empty bulb. Turning it mid-session runs the sand back.
 *
 * @param autoPlay Sets a session on the ruler, turns the glass over, lets it run out and rests, on a loop.
 * @param title The heading; [task] the line under it.
 * @param seed Seed of the grains' shades and of the sand's random slides.
 */
@Composable
fun SandHourglass(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    title: String = "Focus",
    task: String = "Draft the quarterly letter",
    seed: Int = 20261014,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val engine = remember(seed) { HourglassEngine(seed) }
    val parts = remember { GlassParts() }
    var frame by remember { mutableIntStateOf(0) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(engine, auto, reducedMotion) {
        engine.looping = auto
        engine.still = reducedMotion
        engine.clock = 0.0
        engine.hold = 0.0
    }

    LaunchedEffect(engine, parts) {
        var last = -1L
        var lastWall = 0L
        var painted = false
        var ticksSeen = engine.userTicks
        var landingsSeen = engine.userLandings
        var completionsSeen = engine.userCompletions
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, MAX_TICKS * TICK)
                last = now
                if (now - lastWall >= 1_000_000_000L) {
                    lastWall = now
                    val cal = Calendar.getInstance()
                    engine.wallMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                }
                var pending = engine.acc + max(0.0, dt)
                var ticks = 0
                while (pending >= TICK && ticks < MAX_TICKS) {
                    engine.tick()
                    pending -= TICK
                    ticks += 1
                }
                engine.acc = min(pending, TICK)
                if (engine.userTicks > ticksSeen) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (engine.userLandings > landingsSeen) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if (engine.userCompletions > completionsSeen) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
                    )
                }
                ticksSeen = engine.userTicks
                landingsSeen = engine.userLandings
                completionsSeen = engine.userCompletions
                if (ticks > 0 || !painted) {
                    painted = true
                    val sand = engine.sand
                    sand.paint()
                    parts.bitmap.setPixels(sand.pixels, 0, SAND_COLS, 0, 0, SAND_COLS, SAND_ROWS)
                    frame += 1
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(WALL))
            .semantics {
                contentDescription = "Focus timer. Set the minutes on the ruler, then turn the hourglass over to start."
                onClick {
                    engine.flip(true)
                    true
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val layout = remember(width, height, safeTop, safeBottom) { GlassLayout(width, height, safeTop, safeBottom) }

        // The room, the table and the heading never move: drawn once.
        Box(
            Modifier.fillMaxSize().graphicsLayer().drawBehind {
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(density.density, density.density)
                    drawRoom(c, layout, parts, title, task, safeTop, density.density)
                    c.restore()
                }
            },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine, layout) { glassGestures(engine, layout, density.density) },
        ) {
            // Read so the canvas redraws every frame the clock ticks.
            if (frame < 0) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                val a = engine.angle
                val sin2 = (sin(a) * sin(a)).toFloat()
                val s = layout.scale * (if (reducedMotion) 1f else 1 - LIFT * sin2)
                drawShadow(c, layout, parts, engine, s, sin2, density.density)
                c.save()
                c.translate(layout.cx, layout.cy)
                c.rotate(Math.toDegrees(a).toFloat())
                c.scale(s, s)
                val age = engine.doneAge
                drawHourglass(c, parts, a, if (reducedMotion || age < 0) -1.0 else age / SWEEP_FOR, density.density * s)
                c.restore()
                drawReadout(c, layout, parts, engine)
                c.restore()
            }
        }
    }
}

/**
 * The race of the React Native recognisers in one loop: a one-finger drag on the ruler moves it, a
 * drag around the glass turns it by the finger's angle around its centre, a two-finger twist turns it
 * by the twist, and a touch lifted within 350 ms that never moved is a tap that sends it over.
 * Positions are in dp.
 */
private suspend fun PointerInputScope.glassGestures(engine: HourglassEngine, l: GlassLayout, density: Float) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val sx = down.position.x / density
        val sy = down.position.y / density
        tracker.resetTracking()
        tracker.addPointerInputChange(down)
        // 0 pending, 1 pan, 2 twist pending, 3 twist, 4 done, 5 ruler.
        var mode = 0
        var fingerLast = 0.0
        var twistIds: Pair<PointerId, PointerId>? = null
        var twistLast = 0.0
        var twistTotal = 0.0
        val twistTimes = DoubleArray(8)
        val twistValues = DoubleArray(8)
        var twistCount = 0
        fun twistAngle(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) =
            atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        fun twistVelocity(): Double {
            if (twistCount < 2) return 0.0
            val n = min(twistCount, 8)
            val first = (twistCount - n) % 8
            val last = (twistCount - 1) % 8
            val dt = twistTimes[last] - twistTimes[first]
            return if (dt > 0.001) (twistValues[last] - twistValues[first]) / dt else 0.0
        }
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            val primary = event.changes.firstOrNull { it.id == down.id }
            if (primary != null) tracker.addPointerInputChange(primary)
            event.changes.forEach { it.consume() }

            if (mode == 0 && pressed.size >= 2) {
                mode = 2
                twistIds = pressed[0].id to pressed[1].id
                twistLast = twistAngle(pressed[0].position, pressed[1].position)
                twistTotal = 0.0
                twistCount = 0
            }
            when (mode) {
                0 -> {
                    if (primary != null && primary.pressed) {
                        val x = primary.position.x / density
                        val y = primary.position.y / density
                        if (hypot(x - sx, y - sy) >= PAN_SLOP) {
                            if (engine.rulerLive && abs(sy - l.rulerY) < RULER_REACH) {
                                mode = 5
                                engine.grabRuler()
                                engine.dragRuler((x - sx).toDouble())
                            } else {
                                mode = 1
                                val r = hypot(sx - l.cx, sy - l.cy)
                                if (r in GRAB_MIN..GRAB_MAX) {
                                    engine.grabGlass()
                                    fingerLast = atan2((y - l.cy).toDouble(), (x - l.cx).toDouble())
                                }
                            }
                        }
                    }
                }
                5 -> if (primary != null && primary.pressed) engine.dragRuler((primary.position.x / density - sx).toDouble())
                1 -> {
                    if (primary != null && primary.pressed && engine.turning) {
                        val now = atan2((primary.position.y / density - l.cy).toDouble(), (primary.position.x / density - l.cx).toDouble())
                        var d = now - fingerLast
                        if (d > PI) d -= 2 * PI
                        if (d < -PI) d += 2 * PI
                        fingerLast = now
                        engine.turnGlass(engine.turned + d)
                    }
                }
                2, 3 -> {
                    val ids = twistIds
                    val a = ids?.let { (first, _) -> event.changes.firstOrNull { it.id == first && it.pressed } }
                    val b = ids?.let { (_, second) -> event.changes.firstOrNull { it.id == second && it.pressed } }
                    if (a != null && b != null) {
                        val now = twistAngle(a.position, b.position)
                        var d = now - twistLast
                        if (d > PI) d -= 2 * PI
                        if (d < -PI) d += 2 * PI
                        twistLast = now
                        twistTotal += d
                        twistTimes[twistCount % 8] = a.uptimeMillis / 1000.0
                        twistValues[twistCount % 8] = twistTotal
                        twistCount += 1
                        if (mode == 2 && abs(twistTotal) >= TWIST_SLOP) {
                            mode = 3
                            engine.grabGlass()
                        }
                        if (mode == 3) engine.turnGlass(twistTotal)
                    } else {
                        // One of the two fingers lifted: the twist ends at its speed.
                        if (mode == 3) engine.releaseGlass(twistVelocity())
                        mode = 4
                    }
                }
            }

            if (pressed.isEmpty()) {
                val up = primary ?: event.changes.first()
                when (mode) {
                    0 -> if (up.uptimeMillis - down.uptimeMillis <= TAP_MS &&
                        abs(sx - l.cx) <= CAP_HALF_W * l.scale + 20 && abs(sy - l.cy) <= HALF_HEIGHT * l.scale + 20
                    ) {
                        engine.flip(true)
                    }
                    5 -> engine.releaseRuler(tracker.calculateVelocity().x / density.toDouble())
                    1 -> if (engine.turning) {
                        // The finger's speed across the radius is the glass's spin.
                        val v = tracker.calculateVelocity()
                        val rx = up.position.x / density - l.cx
                        val ry = up.position.y / density - l.cy
                        val vx = v.x / density
                        val vy = v.y / density
                        engine.releaseGlass(((rx * vy - ry * vx) / max(rx * rx + ry * ry, 900f)).toDouble())
                    }
                }
                break
            }
        }
        // A finger lost to the system still lets go of the glass and the ruler.
        engine.releaseGlass(0.0)
        engine.releaseRuler(0.0)
    }
}
