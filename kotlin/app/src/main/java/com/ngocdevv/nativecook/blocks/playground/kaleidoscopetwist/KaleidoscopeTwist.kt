package com.ngocdevv.nativecook.blocks.playground.kaleidoscopetwist

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Jewel glass: the colour of each tone in full light, its pale highlight and its deep edge.
private val GlassTone = intArrayOf(0xff2d55, 0xffab1f, 0x19d08f, 0x2f6bff, 0xa04dff, 0x2fd7ff, 0xff5fb0, 0xffe03a)
private val PaleTone = intArrayOf(0xffd0da, 0xfff0c8, 0xd2ffef, 0xd6e3ff, 0xecd9ff, 0xd9f8ff, 0xffe0f0, 0xfffbd6)
private val DeepTone = intArrayOf(0x5c0018, 0x6a3300, 0x00442c, 0x0a1a66, 0x2e0866, 0x003f55, 0x5e0a38, 0x5f4a00)

// The view: its radius (dp, at most), and the window's centre as a share of the safe height.
private const val VIEW_MAX = 158f
private const val CENTER_AT = 0.47f
// The wedge of the chamber the mirrors see: its tip, and how far it reaches (cell units).
private const val APEX_X = 0f
private const val APEX_Y = (0.05 * CELL_R).toFloat()
private const val SPAN = (0.95 * CELL_R).toFloat()
// While the count morphs the image swirls by up to this much (rad per view radius).
private const val SWIRL = 0.55
// The brass: a fixed bezel BEZEL wide, then the knurled ring the hand turns, RING wide.
private const val BEZEL = 10f
private const val RING = 22f
private const val KNURLS = 132
// Iris blades over the seam of the loop.
private const val BLADES = 9
// Light falls on the glass from the upper left of the screen.
private const val LIGHT_X = -0.55
private const val LIGHT_Y = -0.83
// A drag turns the chamber; on release it keeps spinning at the finger's speed, at most MAX_SPIN.
private const val MAX_SPIN = 9.0
private const val GRAB_MIN = 18f
// Pinch: the count goes as 1 / scale^PINCH_POWER; past the range it resists.
private const val PINCH_POWER = 1.4
private const val RUBBER = 0.3
// After a touch the loop waits this long (s), then picks up again.
private const val USER_HOLD = 4.0
// A pan starts after this much travel (dp); a tap is a touch lifted within this long (ms).
private const val PAN_SLOP = 4f
private const val TAP_TIME = 300L

private val Backdrop = Color(0xFF0B090D)
private val Ink = Color(0xFFE9DCC0)
private val Muted = Color(0xFF8A7D66)
private val Faint = Color(0xFF5D5446)

// The chamber's image: CELL_SCALE px per cell unit, CELL_HALF units either side of its centre.
private const val CELL_SCALE = 2.5f
private const val CELL_HALF = (CELL_R * 1.05).toFloat()
private val CellPx = ceil(2 * CELL_HALF * CELL_SCALE).toInt()

/**
 * The view, the same shader as the React Native block's: every point of the round window is
 * folded into one wedge of the object cell, mirrored from one segment to the next. While the count
 * morphs, two counts are drawn and blended, with a swirl that peaks halfway. It runs as a render
 * effect on the layer the object cell is drawn into: `cell` is that layer, in its pixels, the cell's
 * centre at `cellCenter` and `unit` pixels per cell unit.
 */
private const val VIEW_AGSL = """
uniform shader cell;
uniform float2 center;
uniform float viewR;
uniform float2 cellCenter;
uniform float unit;
uniform float2 apex;
uniform float span;
uniform float segA;
uniform float segB;
uniform float mixB;
uniform float swirl;
uniform float2 rot;

// Folds a point of the window into the wedge that looks straight down into the chamber, where
// gravity keeps the pieces.
float2 fold(float2 d, float segs) {
  float r = length(d);
  float w = 6.28318530718 / segs;
  float a = atan(d.y, d.x) + swirl * r;
  float p = 2.0 * w;
  float m = a - p * floor(a / p);
  if (m > w) { m = p - m; }
  m += 1.57079632679 - 0.5 * w;
  return float2(cos(m), sin(m)) * r;
}

float2 toCell(float2 q) {
  float2 c = apex + q * span;
  return float2(rot.x * c.x + rot.y * c.y, -rot.y * c.x + rot.x * c.y);
}

half4 main(float2 p) {
  float2 d = (p - center) / viewR;
  half4 a = cell.eval(cellCenter + toCell(fold(d, segA)) * unit);
  half4 b = cell.eval(cellCenter + toCell(fold(d, segB)) * unit);
  half4 c = mix(a, b, half(mixB));
  float edge = clamp((1.0 - length(d)) * viewR, 0.0, 1.0);
  return c * half(edge);
}
"""

private fun rgb(hex: Int, alpha: Float = 1f) =
    android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), (hex shr 16) and 0xff, (hex shr 8) and 0xff, hex and 0xff)

/** A hash of two integers to [0, 1), with JavaScript's 32-bit wrapping maths. */
private fun hash(a: Int, b: Int): Double {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

/** Skia's blur sigma as the radius Android's blur mask filter takes. */
private fun blurRadius(sigma: Float) = max(0.1f, (sigma - 0.5f) / 0.57735f)

/**
 * The soft glow a Skia blur mask gives a disc of radius `ratio` σ: stop positions and strengths out
 * to `ratio + 3` σ, worked out once by integrating the Gaussian over the disc.
 */
private class DiscGlow(ratio: Double) {
    val outer = ratio + 3
    val stops = FloatArray(13)
    val strength = FloatArray(13)

    init {
        val rings = 36
        val spokes = 48
        for (k in 0..12) {
            val d = outer * k / 12
            var sum = 0.0
            for (i in 0 until rings) {
                val rho = ratio * (i + 0.5) / rings
                for (j in 0 until spokes) {
                    val th = 2 * PI * (j + 0.5) / spokes
                    val dx = rho * cos(th) - d
                    val dy = rho * sin(th)
                    sum += exp(-(dx * dx + dy * dy) / 2) * rho
                }
            }
            stops[k] = k / 12f
            strength[k] = min(1.0, sum * (ratio / rings) * (2 * PI / spokes) / (2 * PI)).toFloat()
        }
    }

    fun colors(hex: Int, alpha: Float) = IntArray(13) { rgb(hex, alpha * strength[it]) }
}

// A bead's glow: a disc of 1.3 r blurred by 0.55 r; a shard's: about 0.87 r blurred by 0.5 r.
private val BeadGlow = DiscGlow(1.3 / 0.55)
private val ShardGlow = DiscGlow(0.87 / 0.5)

// Sprites are baked for a bead of this radius (cell units), at this many px per unit.
private const val SPRITE_R = 16f
private const val SPRITE_SCALE = 3f
private const val GLOW_PX = 40

/** Paints, paths and sprites the object cell is drawn with, reused from frame to frame. */
private class CellPainter {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val roundStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val sprite = Paint(Paint.FILTER_BITMAP_FLAG)
    val add = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    val path = Path()
    val rect = RectF()
    // Drawn once per tone and stamped every frame, so the cell's software canvas stays quick: a
    // bead lit from the light's direction with the chamber at rest (turned to match the light),
    // and the soft glows a bead and a shard throw on the diffuser.
    val beads = Array(TONES) { bakeBeadSprite(it, this) }
    val beadGlows = Array(TONES) { bakeGlowSprite(BeadGlow, GlassTone[it], 0.32f) }
    val shardGlows = Array(TONES) { bakeGlowSprite(ShardGlow, GlassTone[it], 0.26f) }
}

private fun bakeGlowSprite(glow: DiscGlow, hex: Int, alpha: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(2 * GLOW_PX, 2 * GLOW_PX, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    val g = GLOW_PX.toFloat()
    c.drawCircle(g, g, g, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(g, g, g, glow.colors(hex, alpha), glow.stops, Shader.TileMode.CLAMP)
    })
    return bitmap
}

private fun bakeBeadSprite(tone: Int, p: CellPainter): Bitmap {
    val half = SPRITE_R + 1
    val px = ceil(2 * half * SPRITE_SCALE).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(SPRITE_SCALE, SPRITE_SCALE)
    c.translate(half, half)
    drawBeadBody(c, p, 0f, 0f, SPRITE_R, tone, LIGHT_X.toFloat(), LIGHT_Y.toFloat())
    return bitmap
}

/** The frosted diffuser behind the pieces: it does not change, so it is baked once. */
private fun bakeDiffuser(): Bitmap {
    val bitmap = Bitmap.createBitmap(CellPx, CellPx, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(CELL_SCALE, CELL_SCALE)
    c.translate(CELL_HALF, CELL_HALF)
    val r = CELL_R.toFloat()
    val bg = Paint().apply {
        shader = RadialGradient(0f, 0f, r * 1.4f, intArrayOf(rgb(0x22184a), rgb(0x100c2a), rgb(0x06050f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(-r * 1.5f, -r * 1.5f, r * 1.5f, r * 1.5f, bg)
    // Frost on the diffuser glass: a few soft tints.
    val frost = arrayOf(floatArrayOf(-0.4f, -0.5f, 0.5f), floatArrayOf(0.5f, -0.2f, 0.42f), floatArrayOf(-0.1f, 0.45f, 0.55f), floatArrayOf(0.35f, 0.6f, 0.3f))
    val tints = intArrayOf(0x4a2a8a, 0x123f6a, 0x3a1450, 0x0f4a52)
    for (k in frost.indices) {
        val (fx, fy, fr) = frost[k]
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rgb(tints[k], 0.35f)
            maskFilter = BlurMaskFilter(blurRadius(fr * r * 0.6f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(fx * r, fy * r, fr * r, p)
    }
    return bitmap
}

/** A round glass bead, lit from (lx, ly): the body, a caustic, a glint (baked into its sprite). */
private fun drawBeadBody(c: android.graphics.Canvas, p: CellPainter, x: Float, y: Float, r: Float, tone: Int, lx: Float, ly: Float) {
    p.fill.shader = RadialGradient(
        x + lx * r * 0.35f, y + ly * r * 0.35f, r * 1.25f,
        intArrayOf(rgb(PaleTone[tone], 0.94f), rgb(GlassTone[tone], 0.94f), rgb(DeepTone[tone], 0.94f)),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawCircle(x, y, r, p.fill)
    p.fill.shader = null
    // Light focused through the bead lands on its far side.
    val back = Math.toDegrees(atan2(-ly, -lx).toDouble()).toFloat()
    p.roundStroke.color = rgb(PaleTone[tone], 0.75f)
    p.roundStroke.strokeWidth = r * 0.16f
    p.rect.set(x - r * 0.66f, y - r * 0.66f, x + r * 0.66f, y + r * 0.66f)
    c.drawArc(p.rect, back - 38, 76f, false, p.roundStroke)
    p.fill.color = rgb(0xffffff, 0.9f)
    c.drawCircle(x + lx * r * 0.42f, y + ly * r * 0.42f, r * 0.2f, p.fill)
    p.stroke.color = rgb(DeepTone[tone], 0.6f)
    p.stroke.strokeWidth = 0.8f
    c.drawCircle(x, y, r - 0.4f, p.stroke)
}

/** A bead in the chamber turned `angleDeg`: its glow on the diffuser, then its sprite turned to the light. */
private fun drawBead(c: android.graphics.Canvas, p: CellPainter, x: Float, y: Float, r: Float, tone: Int, angleDeg: Float) {
    val outer = r * BeadGlow.outer.toFloat() * 0.55f
    p.rect.set(x - outer, y - outer, x + outer, y + outer)
    c.drawBitmap(p.beadGlows[tone], null, p.rect, p.add)
    val half = (SPRITE_R + 1) * r / SPRITE_R
    c.save()
    c.translate(x, y)
    c.rotate(-angleDeg)
    p.rect.set(-half, -half, half, half)
    c.drawBitmap(p.beads[tone], null, p.rect, p.sprite)
    c.restore()
}

/** A flat shard of glass: an irregular polygon, its bevelled edges catching the light. */
private fun drawShard(c: android.graphics.Canvas, p: CellPainter, x: Float, y: Float, r: Float, ang: Double, tone: Int, shape: Int, lx: Float, ly: Float) {
    val n = 5 + shape % 2
    val path = p.path
    path.rewind()
    for (k in 0 until n) {
        val a = ang + (k + (hash(shape, k) - 0.5) * 0.55) * 2 * PI / n
        val d = r * (0.72 + 0.3 * hash(shape, k + 9))
        val px = (x + cos(a) * d).toFloat()
        val py = (y + sin(a) * d).toFloat()
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    val outer = r * ShardGlow.outer.toFloat() * 0.5f
    p.rect.set(x - outer, y - outer, x + outer, y + outer)
    c.drawBitmap(p.shardGlows[tone], null, p.rect, p.add)
    p.fill.shader = LinearGradient(
        x + lx * r, y + ly * r, x - lx * r, y - ly * r,
        intArrayOf(rgb(PaleTone[tone], 0.82f), rgb(GlassTone[tone], 0.82f), rgb(DeepTone[tone], 0.82f)),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawPath(path, p.fill)
    p.fill.shader = null
    p.stroke.color = rgb(PaleTone[tone], 0.7f)
    p.stroke.strokeWidth = 0.9f
    c.drawPath(path, p.stroke)
}

/** The object cell drawn into its bitmap, for Android 12 and older. */
private fun drawCell(c: android.graphics.Canvas, st: KaleidoSim, diffuser: Bitmap, p: CellPainter) {
    c.save()
    c.scale(CELL_SCALE, CELL_SCALE)
    c.translate(CELL_HALF, CELL_HALF)
    drawCellContent(c, st, diffuser, p)
    c.restore()
}

/** The object cell this frame, in cell units about its centre: the diffuser and every piece on it. */
private fun drawCellContent(c: android.graphics.Canvas, st: KaleidoSim, diffuser: Bitmap, p: CellPainter) {
    p.rect.set(-CELL_HALF, -CELL_HALF, CELL_HALF, CELL_HALF)
    c.drawBitmap(diffuser, null, p.rect, p.sprite)
    // Light from the view's upper left, seen from inside the turned chamber.
    val ca = cos(st.angle)
    val sa = sin(st.angle)
    val lx = (ca * LIGHT_X + sa * LIGHT_Y).toFloat()
    val ly = (-sa * LIGHT_X + ca * LIGHT_Y).toFloat()
    val deg = Math.toDegrees(st.angle).toFloat()
    for (kind in intArrayOf(SHARD, BEAD, SEED)) {
        for (i in 0 until st.count) {
            if (st.kind[i] != kind) continue
            val r = st.grownRadius(i).toFloat()
            val x = st.x[i].toFloat()
            val y = st.y[i].toFloat()
            when (kind) {
                SHARD -> drawShard(c, p, x, y, r, st.ang[i], st.tone[i], st.shape[i], lx, ly)
                BEAD -> drawBead(c, p, x, y, r, st.tone[i], deg)
                else -> {
                    p.fill.color = rgb(GlassTone[st.tone[i]], 0.95f)
                    c.drawCircle(x, y, r, p.fill)
                    p.fill.color = rgb(0xffffff, 0.85f)
                    c.drawCircle(x + lx * r * 0.4f, y + ly * r * 0.4f, r * 0.35f, p.fill)
                }
            }
        }
    }
}

/** Everything the frame loop, the touch and the picture share, kept off Compose state. */
private class KaleidoStage {
    var sim: KaleidoSim? = null
    var acc = 0.0
    var looping = false
    // The eyepiece on this screen (dp).
    var width = 0f
    var height = 0f
    var cx = 0f
    var cy = 0f
    var viewR = 0f
    // Below Android 13 the chamber is drawn into one bitmap each frame, kept for the block's life.
    val cell: Bitmap by lazy { Bitmap.createBitmap(CellPx, CellPx, Bitmap.Config.ARGB_8888) }
    val cellCanvas by lazy { android.graphics.Canvas(cell) }
    val diffuser: Bitmap = bakeDiffuser()
    val painter = CellPainter()
    var fingerLast = 0.0
    var pinchFrom = SEG_START
    var shownEven = SEG_START
    // Bumped on every frame that stepped the simulation: the picture reads it, so it redraws.
    var frame by mutableIntStateOf(0)
    var facets by mutableIntStateOf(SEG_START.toInt())
}

/** Paints and shaders for the view, reused from frame to frame. */
private class ViewPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val image = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val view: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(VIEW_AGSL) else null
    val matrix = Matrix()
    val inverse = Matrix()
    val path = Path()
    val rect = RectF()
    // Shaders that depend only on where the eyepiece is.
    var layoutKey = Float.NaN
    var room: Shader? = null
    var vignette: Shader? = null
    var steel: Shader? = null
    var bezel: SweepGradient? = null
    var ring: SweepGradient? = null
    var shadow: Bitmap? = null
    var sheen: Bitmap? = null
}

private val BrassColors = intArrayOf(
    rgb(0x5b3d15), rgb(0xc99a45), rgb(0xfff0c0), rgb(0xb8862f), rgb(0x4a3010),
    rgb(0xa9782a), rgb(0xf4d690), rgb(0x7b5620), rgb(0x5b3d15),
)
private val BrassStops = floatArrayOf(0f, 0.12f, 0.2f, 0.32f, 0.5f, 0.64f, 0.72f, 0.86f, 1f)

/** Turned brass: a sweep of warm highlights around the eyepiece, starting at `shift` degrees. */
private fun brass(cx: Float, cy: Float, shift: Float) = SweepGradient(cx, cy, BrassColors, BrassStops).apply {
    setLocalMatrix(Matrix().apply { setRotate(shift, cx, cy) })
}

/** The soft parts of the view that never change, baked once at 2 px per dp. */
private fun bakeBlurred(w: Float, h: Float, draw: (android.graphics.Canvas) -> Unit): Bitmap {
    val bitmap = Bitmap.createBitmap(ceil(w * 2).toInt(), ceil(h * 2).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(2f, 2f)
    draw(c)
    return bitmap
}

private fun prepareLayout(s: KaleidoStage, p: ViewPaints) {
    val key = s.width * 1e4f + s.height + s.cy * 1e-3f
    if (p.layoutKey == key) return
    p.layoutKey = key
    val outerR = s.viewR + BEZEL + RING
    p.room = RadialGradient(
        s.cx, s.cy - 20, max(s.width, s.height) * 0.75f,
        intArrayOf(rgb(0x241c22), rgb(0x120e13), rgb(0x070608)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
    )
    p.vignette = RadialGradient(
        s.cx, s.cy, s.viewR,
        intArrayOf(rgb(0, 0f), rgb(0, 0f), rgb(0, 0.45f), rgb(0, 0.92f)), floatArrayOf(0f, 0.6f, 0.88f, 1f), Shader.TileMode.CLAMP,
    )
    p.steel = SweepGradient(
        s.cx, s.cy,
        intArrayOf(rgb(0x26262c), rgb(0x4b4b54), rgb(0x1b1b20), rgb(0x3d3d45), rgb(0x26262c)),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    ).apply { setLocalMatrix(Matrix()) }
    p.bezel = brass(s.cx, s.cy, -30f)
    p.ring = brass(s.cx, s.cy, -20f)
    // The eyepiece's shadow on the room (blur 22), and the sheen on its glass (blur 2.5).
    val sr = outerR + 6 + 66
    p.shadow = bakeBlurred(2 * sr, 2 * sr) { c ->
        c.drawCircle(sr, sr, outerR + 6, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rgb(0, 0.6f)
            maskFilter = BlurMaskFilter(blurRadius(22f), BlurMaskFilter.Blur.NORMAL)
        })
    }
    val gr = s.viewR + 10
    p.sheen = bakeBlurred(2 * gr, 2 * gr) { c ->
        val r = s.viewR * 0.82f
        c.drawArc(RectF(gr - r, gr - r, gr + r, gr + r), 198f, 52f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = rgb(0xffffff, 0.07f)
            maskFilter = BlurMaskFilter(blurRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
        })
    }
}

/**
 * A kaleidoscope seen through its brass eyepiece. Coloured glass beads and shards lie loose in a
 * round chamber and tumble as it turns; mirrors fold one wedge of it into a mandala of 6 to 12
 * segments. Drag around the window to turn the chamber, pinch to change the mirrors, tap to drop
 * another bead in.
 *
 * @param autoPlay Turns the chamber, shakes it and changes the mirrors on a 14 s loop.
 * @param seed Seed of the pieces in the chamber.
 */
@Composable
fun KaleidoscopeTwist(modifier: Modifier = Modifier, autoPlay: Boolean = false, seed: Int = 20261014) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val stage = remember { KaleidoStage() }
    val paints = remember { ViewPaints() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(stage, auto) {
        stage.looping = auto
        stage.sim?.hold = 0.0
    }

    LaunchedEffect(stage, seed, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                var st = stage.sim
                if (st == null) {
                    st = KaleidoSim(seed, stage.looping, reducedMotion)
                    stage.sim = st
                }
                st.looping = stage.looping
                st.reduced = reducedMotion
                val elapsed = if (last < 0) 0.016 else (now - last) / 1e9
                last = now
                val dt = min(max(0.0, elapsed), KAL_MAX_TICKS * KAL_TICK)
                var pending = stage.acc + dt
                var ticks = 0
                while (pending >= KAL_TICK && ticks < KAL_MAX_TICKS) {
                    st.step()
                    pending -= KAL_TICK
                    ticks += 1
                }
                stage.acc = min(pending, KAL_TICK)
                if (ticks == 0 && stage.frame > 0) return@withFrameNanos
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) drawCell(stage.cellCanvas, st, stage.diffuser, stage.painter)
                // The readout under the eyepiece follows the count the mirrors are heading for.
                val facets = if (st.pinching) max(SEG_MIN, min(SEG_MAX, jsRound(st.seg / 2) * 2)) else st.segTarget
                if (facets.toInt() != stage.facets) stage.facets = facets.toInt()
                stage.frame += 1
            }
        }
    }

    fun touched() {
        stage.sim?.hold = USER_HOLD
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .semantics {
                contentDescription = "Kaleidoscope with ${stage.facets} mirror segments. Drag around it to turn, pinch to change the mirrors, tap to drop a bead."
            }
            .pointerInput(stage, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dp = density.density
                    val startX = down.position.x / dp
                    val startY = down.position.y / dp
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    // 0 = not yet known, 1 = pan, 2 = pinch, 3 = over.
                    var mode = 0
                    var pinchDistance = 1f
                    var lastX = startX
                    var lastY = startY
                    var tapAt: Offset? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val first = event.changes.firstOrNull { it.id == down.id }
                            if (first != null && first.pressed) {
                                tracker.addPointerInputChange(first)
                                lastX = first.position.x / dp
                                lastY = first.position.y / dp
                            }
                            if (mode == 0 && pressed.size >= 2) {
                                mode = 2
                                pinchDistance = max(1f, (pressed[0].position - pressed[1].position).getDistance())
                                val st = stage.sim
                                if (st != null) {
                                    touched()
                                    st.pinching = true
                                    stage.pinchFrom = st.seg
                                    stage.shownEven = st.segTarget
                                }
                            }
                            if (mode == 0 && first != null && first.pressed && hypot(lastX - startX, lastY - startY) >= PAN_SLOP) {
                                mode = 1
                                val st = stage.sim
                                if (st != null && hypot(lastX - stage.cx, lastY - stage.cy) >= GRAB_MIN) {
                                    touched()
                                    st.dragging = true
                                    st.angVel = 0.0
                                    stage.fingerLast = atan2((lastY - stage.cy).toDouble(), (lastX - stage.cx).toDouble())
                                }
                            }
                            when (mode) {
                                1 -> {
                                    event.changes.forEach { it.consume() }
                                    val st = stage.sim
                                    if (st != null && st.dragging && first != null && first.pressed) {
                                        val now = atan2((lastY - stage.cy).toDouble(), (lastX - stage.cx).toDouble())
                                        var d = now - stage.fingerLast
                                        if (d > PI) d -= 2 * PI
                                        if (d < -PI) d += 2 * PI
                                        stage.fingerLast = now
                                        st.angle += d
                                    }
                                    if (first == null || !first.pressed) {
                                        if (st != null && st.dragging) {
                                            val v = tracker.calculateVelocity()
                                            val rx = lastX - stage.cx
                                            val ry = lastY - stage.cy
                                            val spin = ((rx * v.y / dp - ry * v.x / dp) / max(rx * rx + ry * ry, 900f)).toDouble()
                                            st.angVel = if (reducedMotion) 0.0 else spin.coerceIn(-MAX_SPIN, MAX_SPIN)
                                            st.dragging = false
                                            touched()
                                        }
                                        mode = 3
                                    }
                                }
                                2 -> {
                                    event.changes.forEach { it.consume() }
                                    val st = stage.sim
                                    if (pressed.size >= 2 && st != null && st.pinching) {
                                        val scale = (pressed[0].position - pressed[1].position).getDistance() / pinchDistance
                                        val raw = stage.pinchFrom / max(0.05, scale.toDouble()).pow(PINCH_POWER)
                                        var seg = raw
                                        if (raw < SEG_MIN) seg = SEG_MIN - (SEG_MIN - raw) * RUBBER
                                        if (raw > SEG_MAX) seg = SEG_MAX + (raw - SEG_MAX) * RUBBER
                                        st.seg = seg
                                        st.segVel = 0.0
                                        val even = max(SEG_MIN, min(SEG_MAX, jsRound(seg / 2) * 2))
                                        if (even != stage.shownEven) {
                                            stage.shownEven = even
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    } else {
                                        if (st != null && st.pinching) {
                                            st.setSegments(st.seg)
                                            st.pinching = false
                                            touched()
                                        }
                                        mode = 3
                                    }
                                }
                                0 -> if (first == null || !first.pressed) {
                                    if (first != null && first.uptimeMillis - down.uptimeMillis <= TAP_TIME) tapAt = Offset(lastX, lastY)
                                    mode = 3
                                }
                            }
                            if (pressed.isEmpty()) break
                        }
                    } finally {
                        // A gesture cut off half way (or a pointer input restarted under it) lets go
                        // of the chamber, so it is never left held and the script never stays paused.
                        stage.sim?.let {
                            it.dragging = false
                            if (it.pinching) {
                                it.setSegments(it.seg)
                                it.pinching = false
                            }
                        }
                    }
                    val at = tapAt ?: return@awaitEachGesture
                    val st = stage.sim ?: return@awaitEachGesture
                    val dx = (at.x - stage.cx) / stage.viewR
                    val dy = (at.y - stage.cy) / stage.viewR
                    val r = hypot(dx, dy).toDouble()
                    if (r > 1) return@awaitEachGesture
                    touched()
                    // The same fold the mirrors use, so the bead lands under the finger (and in every mirror).
                    val w = 2 * PI / max(2.0, jsRound(st.seg))
                    val period = 2 * w
                    val a = atan2(dy.toDouble(), dx.toDouble())
                    var m = a - period * floor(a / period)
                    if (m > w) m = period - m
                    m += PI / 2 - w / 2
                    val vx = APEX_X + cos(m) * r * SPAN
                    val vy = APEX_Y + sin(m) * r * SPAN
                    val c = cos(st.angle)
                    val s = sin(st.angle)
                    st.addPiece(c * vx + s * vy, -s * vx + c * vy)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        // At least 1 dp: a gradient or bitmap sized from a negative radius would throw on a narrow window.
        val viewR = min(VIEW_MAX, width / 2 - BEZEL - RING - 16).coerceAtLeast(1f)
        val cy = safeTop + (height - safeTop - safeBottom) * CENTER_AT
        val outerR = viewR + BEZEL + RING
        stage.width = width
        stage.height = height
        stage.cx = width / 2
        stage.cy = cy
        stage.viewR = viewR

        // The room, and below Android 13 the mirrors' view drawn from the chamber's bitmap.
        Canvas(Modifier.fillMaxSize()) {
            stage.frame
            val st = stage.sim
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                drawBackground(c, stage, st, paints)
                c.restore()
            }
        }

        // Android 13 and later: the chamber is drawn into its own layer each frame, on the GPU,
        // and the mirrors' shader folds that layer as a render effect.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val side = ceil(2 * max(viewR + 2, CELL_HALF + 1))
            Canvas(
                Modifier
                    .offset(x = (stage.cx - side / 2).dp, y = (cy - side / 2).dp)
                    .size(side.dp)
                    .graphicsLayer {
                        stage.frame
                        val st = stage.sim
                        renderEffect = if (st != null) viewEffect(stage, st, paints, reducedMotion, side, density.density) else null
                    },
            ) {
                stage.frame
                val st = stage.sim ?: return@Canvas
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(density.density, density.density)
                    c.translate(side / 2, side / 2)
                    drawCellContent(c, st, stage.diffuser, stage.painter)
                    c.restore()
                }
            }
        }

        // The window's fall-off and glass, the iris and the brass.
        Canvas(Modifier.fillMaxSize()) {
            stage.frame
            val st = stage.sim ?: return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                drawOverlay(c, stage, st, paints)
                c.restore()
            }
        }

        val textStyle = TextStyle(fontFamily = FontFamily.SansSerif, textAlign = TextAlign.Center)
        BasicText(
            text = "KALEIDOSCOPE",
            modifier = Modifier.fillMaxWidth().padding(top = (safeTop + 18f).dp),
            style = textStyle.copy(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.2.sp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().offset(y = (cy + outerR + 30f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = stage.facets.toString().padStart(2, '0'),
                style = textStyle.copy(color = Ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontFeatureSettings = "tnum"),
            )
            BasicText(
                text = "MIRROR SEGMENTS",
                style = textStyle.copy(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
            )
        }
        BasicText(
            text = "Drag to turn · Pinch for mirrors · Tap to drop a bead",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 22f).dp),
            style = textStyle.copy(color = Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** The room, its shadow, and below Android 13 the view drawn from the chamber's bitmap. */
private fun drawBackground(c: android.graphics.Canvas, s: KaleidoStage, st: KaleidoSim?, p: ViewPaints) {
    if (s.width <= 0f) return
    prepareLayout(s, p)
    val outerR = s.viewR + BEZEL + RING

    // The room: a warm dark with a little light around the eyepiece.
    p.fill.reset()
    p.fill.shader = p.room
    c.drawRect(0f, 0f, s.width, s.height, p.fill)
    p.fill.shader = null
    p.shadow?.let {
        val sr = outerR + 6 + 66
        p.rect.set(s.cx - sr, s.cy + 16 - sr, s.cx + sr, s.cy + 16 + sr)
        c.drawBitmap(it, null, p.rect, p.image)
    }
    if (st == null || s.frame == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val (lower, t) = segments(st)
    drawFallbackView(c, s, st, p, lower.toInt(), t.toFloat())
}

/** Two even counts either side of the morph, and how far the morph is from the lower one. */
private fun segments(st: KaleidoSim): Pair<Double, Double> {
    val seg = max(SEG_MIN - 1.5, min(SEG_MAX + 1.5, st.seg))
    val lower = max(4.0, floor(seg / 2) * 2)
    return lower to (seg - lower) / 2
}

/** Fall-off towards the window's edge, the eyepiece's glass, the iris and the brass. */
private fun drawOverlay(c: android.graphics.Canvas, s: KaleidoStage, st: KaleidoSim, p: ViewPaints) {
    if (s.width <= 0f || s.frame == 0) return
    prepareLayout(s, p)
    p.fill.reset()
    p.fill.isAntiAlias = true
    p.fill.shader = p.vignette
    c.drawCircle(s.cx, s.cy, s.viewR + 1, p.fill)
    p.fill.shader = null
    p.sheen?.let {
        val gr = s.viewR + 10
        p.rect.set(s.cx - gr, s.cy - gr, s.cx + gr, s.cy + gr)
        c.drawBitmap(it, null, p.rect, p.image)
    }
    drawIris(c, s, p, st.iris.toFloat())
    drawBrass(c, s, p, st.angle)
}

/**
 * The mirrors as a render effect on the chamber's layer (`side` dp square, the window and the
 * chamber both centred in it): two even counts either side of the morph, blended, with a swirl
 * that peaks halfway. The shader works in the layer's pixels.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun viewEffect(s: KaleidoStage, st: KaleidoSim, p: ViewPaints, reduced: Boolean, side: Float, density: Float): androidx.compose.ui.graphics.RenderEffect {
    val shader = p.view as RuntimeShader
    val (lower, t) = segments(st)
    val swirl = if (reduced) 0.0 else SWIRL * sin(PI * t)
    val mid = side / 2 * density
    shader.setFloatUniform("center", mid, mid)
    shader.setFloatUniform("viewR", s.viewR * density)
    shader.setFloatUniform("cellCenter", mid, mid)
    shader.setFloatUniform("unit", density)
    shader.setFloatUniform("apex", APEX_X, APEX_Y)
    shader.setFloatUniform("span", SPAN)
    shader.setFloatUniform("segA", lower.toFloat())
    shader.setFloatUniform("segB", lower.toFloat() + 2)
    shader.setFloatUniform("mixB", t.toFloat())
    shader.setFloatUniform("swirl", swirl.toFloat())
    shader.setFloatUniform("rot", cos(st.angle).toFloat(), sin(st.angle).toFloat())
    return RenderEffect.createRuntimeShaderEffect(shader, "cell").asComposeRenderEffect()
}

/**
 * Below Android 13 there is no runtime shader: each of the N segments is the chamber's bitmap,
 * turned (and mirrored on every other one) so its wedge looks down into the pile, clipped to its
 * slice of the window. While the count morphs, the next count fades in over it, without the swirl.
 */
private fun drawFallbackView(c: android.graphics.Canvas, s: KaleidoStage, st: KaleidoSim, p: ViewPaints, lower: Int, t: Float) {
    c.save()
    p.path.rewind()
    p.path.addCircle(s.cx, s.cy, s.viewR + 1, Path.Direction.CW)
    c.clipPath(p.path)
    c.drawColor(android.graphics.Color.BLACK)
    drawSegments(c, s, st, p, lower, 255)
    if (t > 0.01f) drawSegments(c, s, st, p, lower + 2, (t * 255).roundToInt())
    c.restore()
}

private fun drawSegments(c: android.graphics.Canvas, s: KaleidoStage, st: KaleidoSim, p: ViewPaints, count: Int, alpha: Int) {
    val w = 2 * PI / count
    val deg = Math.toDegrees(w).toFloat()
    val r = s.viewR + 2
    p.image.alpha = alpha
    for (k in 0 until count) {
        // Window → bitmap: normalise, fold this segment into the wedge, then into the cell's image.
        val m = p.matrix
        m.setTranslate(-s.cx, -s.cy)
        m.postScale(1 / s.viewR, 1 / s.viewR)
        if (k % 2 == 0) {
            m.postRotate(Math.toDegrees(PI / 2 - w / 2 - k * w).toFloat())
        } else {
            m.postScale(1f, -1f)
            m.postRotate(Math.toDegrees((k + 1) * w + PI / 2 - w / 2).toFloat())
        }
        m.postScale(SPAN, SPAN)
        m.postTranslate(APEX_X, APEX_Y)
        m.postRotate(-Math.toDegrees(st.angle).toFloat())
        m.postTranslate(CELL_HALF, CELL_HALF)
        m.postScale(CELL_SCALE, CELL_SCALE)
        if (!m.invert(p.inverse)) continue
        c.save()
        p.path.rewind()
        p.path.moveTo(s.cx, s.cy)
        p.rect.set(s.cx - r, s.cy - r, s.cx + r, s.cy + r)
        p.path.arcTo(p.rect, Math.toDegrees(k * w).toFloat(), deg + 0.3f)
        p.path.close()
        c.clipPath(p.path)
        c.drawBitmap(s.cell, p.inverse, p.image)
        c.restore()
    }
    p.image.alpha = 255
}

/** The iris over the window, `open` 0 (shut) … 1 (wide open): BLADES curved steel leaves. */
private fun drawIris(c: android.graphics.Canvas, s: KaleidoStage, p: ViewPaints, open: Float) {
    if (open >= 0.999f) return
    val cx = s.cx
    val cy = s.cy
    val viewR = s.viewR
    val a = viewR * 1.02f * open
    val turn = (1 - open) * 0.9f
    val blades = p.path
    blades.rewind()
    blades.fillType = Path.FillType.EVEN_ODD
    blades.addCircle(cx, cy, viewR + 1, Path.Direction.CW)
    // The opening: a polygon whose sides are the blades' edges, turning as it closes.
    for (k in 0..BLADES) {
        val th = turn + k * 2 * PI / BLADES
        val x = (cx + cos(th) * a).toFloat()
        val y = (cy + sin(th) * a).toFloat()
        if (k == 0) blades.moveTo(x, y) else blades.lineTo(x, y)
    }
    blades.close()
    p.fill.reset()
    p.fill.isAntiAlias = true
    p.fill.shader = p.steel
    c.drawPath(blades, p.fill)
    p.fill.shader = null
    blades.fillType = Path.FillType.WINDING
    // Each blade's leading edge runs from a corner of the opening out to the rim.
    p.stroke.shader = null
    for (k in 0 until BLADES) {
        val th = turn + k * 2 * PI / BLADES
        val t1 = th + 1.25
        val reach = a + (viewR - a) * 0.75f
        blades.rewind()
        blades.moveTo((cx + cos(th) * a).toFloat(), (cy + sin(th) * a).toFloat())
        blades.quadTo(
            (cx + cos(th + 0.55) * reach).toFloat(), (cy + sin(th + 0.55) * reach).toFloat(),
            (cx + cos(t1) * (viewR + 1)).toFloat(), (cy + sin(t1) * (viewR + 1)).toFloat(),
        )
        p.stroke.color = rgb(0x0b0b0e, 0.9f)
        p.stroke.strokeWidth = 1.2f
        c.drawPath(blades, p.stroke)
        c.save()
        c.translate(0.6f, 0.6f)
        p.stroke.color = rgb(0x9a9aa6, 0.5f)
        p.stroke.strokeWidth = 0.6f
        c.drawPath(blades, p.stroke)
        c.restore()
    }
}

/** The brass eyepiece: a fixed bezel and the knurled ring that turns with the chamber. */
private fun drawBrass(c: android.graphics.Canvas, s: KaleidoStage, p: ViewPaints, angle: Double) {
    val cx = s.cx
    val cy = s.cy
    val viewR = s.viewR
    val st = p.stroke
    st.strokeCap = Paint.Cap.BUTT
    // Bezel.
    st.color = android.graphics.Color.BLACK
    st.shader = p.bezel
    st.strokeWidth = BEZEL
    c.drawCircle(cx, cy, viewR + BEZEL / 2, st)
    // The knurled ring, a shade darker and a little proud of the bezel.
    val ringR = viewR + BEZEL + RING / 2
    st.shader = p.ring
    st.strokeWidth = RING
    c.drawCircle(cx, cy, ringR, st)
    st.shader = null
    st.color = rgb(0, 0.22f)
    c.drawCircle(cx, cy, ringR, st)
    val r0 = viewR + BEZEL + 3
    val r1 = viewR + BEZEL + RING - 3
    for (k in 0 until KNURLS) {
        val t = angle + k * 2 * PI / KNURLS
        val ct = cos(t).toFloat()
        val sn = sin(t).toFloat()
        st.color = rgb(0x2a1a06, 0.55f)
        st.strokeWidth = 1.3f
        c.drawLine(cx + ct * r0, cy + sn * r0, cx + ct * r1, cy + sn * r1, st)
        val u = t + 0.012
        val cu = cos(u).toFloat()
        val su = sin(u).toFloat()
        st.color = rgb(0xfff2c8, 0.35f)
        st.strokeWidth = 0.8f
        c.drawLine(cx + cu * r0, cy + su * r0, cx + cu * r1, cy + su * r1, st)
    }
    // An engraved index on the ring, so its turn reads at a glance.
    val tick = angle - PI / 2
    val ix = (cx + cos(tick) * ringR).toFloat()
    val iy = (cy + sin(tick) * ringR).toFloat()
    p.fill.reset()
    p.fill.isAntiAlias = true
    p.fill.color = rgb(0x2a1a06, 0.85f)
    c.drawCircle(ix, iy, 3.2f, p.fill)
    p.fill.color = rgb(0xfff2c8, 0.5f)
    c.drawCircle(ix - 0.6f, iy - 0.6f, 1.4f, p.fill)
    // Grooves and the rim's catchlights.
    st.strokeWidth = 1.4f
    st.color = rgb(0x1d1206, 0.9f)
    c.drawCircle(cx, cy, viewR + BEZEL, st)
    st.strokeWidth = 1.6f
    st.color = rgb(0x120b03)
    c.drawCircle(cx, cy, viewR + 0.5f, st)
    st.strokeWidth = 1.4f
    st.color = rgb(0x1d1206, 0.9f)
    c.drawCircle(cx, cy, viewR + BEZEL + RING, st)
    val outer = viewR + BEZEL + RING + 0.4f
    st.strokeWidth = 1.1f
    st.strokeCap = Paint.Cap.ROUND
    st.color = rgb(0xfff4d0, 0.55f)
    p.rect.set(cx - outer, cy - outer, cx + outer, cy + outer)
    c.drawArc(p.rect, 200f, 70f, false, st)
    st.strokeCap = Paint.Cap.BUTT
}
