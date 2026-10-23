package com.ngocdevv.nativecook.blocks.playground.bubblewrappop

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
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

// Layout: the sheet sits SIDE dp from the screen's sides, between the counter and the hint.
private const val SIDE = 18.0
private const val HEADER = 118.0
private const val FOOTER = 64.0
private const val SHEET_RADIUS = 26f

// The backdrop: pastel light pooling behind the film, drifting once per LOOP.
private val Ink = Color(0xFF2A2433)
private val InkSoft = Color(0xFF7A6F86)
private val Paper = Color(0xFFFBF3EA)
// The collapse from dome to flat film after a pop, s.
private const val COLLAPSE = 0.12
// The air rings: AIR_RINGS per pop, thrown at AIR_SPEED dp/s and slowed with AIR_DRAG s.
private const val AIR_RINGS = 7
private const val AIR_SPEED = 170.0
private const val AIR_DRAG = 0.15
private const val SHOCK_TIME = 0.24

// autoPlay (seconds): eight presses pop a smiley, a finger sweeps every row, and once the sheet is
// empty a fresh one slides in before the loop ends.
private const val LOOP = 12.0
private const val SMILE_AT = 0.5
private const val SMILE_STEP = 0.6
private const val PRESS_HOLD = 0.56
private const val SWEEP_AT = 5.75
private const val SWEEP_ROW = 0.3
private const val SCRIPT_ID = 1000L
private const val USER_HOLD = 3.0

// The backdrop, in screen dp: four pastel pools of light on a warm white, drifting with `phase`,
// with a fine grain.
private const val BACKDROP_AGSL = """
uniform float2 res;
uniform float phase;
float blob(float2 p, float2 c, float r) { float2 d = p - c; return exp(-dot(d, d) / (r * r)); }
half4 main(float2 p) {
  float2 uv = p / res.y;
  float ax = res.x / res.y;
  float3 col = float3(0.99, 0.958, 0.93);
  col = mix(col, float3(1.0, 0.75, 0.62), 0.9 * blob(uv, float2(ax * (0.16 + 0.08 * sin(phase)), 0.2 + 0.05 * cos(phase)), 0.3));
  col = mix(col, float3(0.98, 0.62, 0.76), 0.8 * blob(uv, float2(ax * (0.88 + 0.06 * cos(phase)), 0.4 + 0.06 * sin(phase)), 0.29));
  col = mix(col, float3(0.72, 0.66, 1.0), 0.85 * blob(uv, float2(ax * (0.28 + 0.07 * cos(phase)), 0.68 + 0.05 * sin(phase)), 0.34));
  col = mix(col, float3(0.6, 0.83, 1.0), 0.8 * blob(uv, float2(ax * (0.82 + 0.05 * sin(phase)), 0.88 + 0.04 * cos(phase)), 0.3));
  float g = fract(sin(dot(floor(p * 2.0), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
  return half4(half3(col + g * 0.02), 1.0);
}"""

// Crinkles in the film: long, thin crease highlights from stretched value noise.
private const val CRINKLE_AGSL = """
uniform float strength;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float2 q = float2(p.x * 0.82 + p.y * 0.57, -p.x * 0.57 + p.y * 0.82);
  float n = noise(q * float2(0.06, 0.008)) * 0.7 + noise(q * float2(0.02, 0.03) + 7.0) * 0.3;
  float ridge = pow(1.0 - abs(n * 2.0 - 1.0), 14.0);
  float a = ridge * strength;
  return half4(a, a, a, a);
}"""

// A paint's colour scales what its shader draws, so a shaded paint starts from opaque black.
private const val SHADED = 0xFF000000.toInt()

private fun clamp01(v: Double) = min(1.0, max(0.0, v))

private fun easeInOut(t: Double): Double {
    val x = clamp01(t)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

private fun hash(a: Double, b: Double): Double {
    val v = sin(a * 127.1 + b * 311.7) * 43758.5453
    return v - floor(v)
}

private fun white(a: Double) = Color(1f, 1f, 1f, a.toFloat().coerceIn(0f, 1f)).toArgb()

private fun plum(a: Double) = Color(0.25f, 0.16f, 0.36f, a.toFloat().coerceIn(0f, 1f)).toArgb()

private class Stroke(val at: Double, val dur: Double, val pts: DoubleArray)

/** Where autoPlay's fingers go on a sheet at rest: a smiley of presses, then a sweep. */
private fun buildScript(s: BubbleSheet): List<Stroke> {
    val w = s.right - s.left
    val h = s.bottom - s.top
    val targets = mutableListOf(0.33 to 0.3, 0.67 to 0.3)
    for (k in 0 until 6) {
        val a = (20 + k * 28) * PI / 180
        targets.add((0.5 - cos(a) * 0.27) to (0.52 + sin(a) * 0.2))
    }
    val used = mutableListOf<Int>()
    val out = mutableListOf<Stroke>()
    targets.forEachIndexed { k, (tx, ty) ->
        var best = -1
        var bestD = Double.POSITIVE_INFINITY
        for (i in 0 until s.count) {
            if (i in used) continue
            val d = hypot(s.bx[i] - tx * w, s.by[i] - ty * h)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        used.add(best)
        out.add(Stroke(SMILE_AT + k * SMILE_STEP, PRESS_HOLD, doubleArrayOf(s.left + s.bx[best], s.top + s.by[best])))
    }
    // The sweep: from the first bubble of the top row, across each row and down, side to side.
    val rows = s.rowOf[s.count - 1] + 1
    val pts = ArrayList<Double>()
    val x0 = s.left + s.bx[0] - s.radius * 0.6
    val x1 = s.left + s.bx[s.count - 1] + s.radius * 0.6
    for (r in 0 until rows) {
        val y = s.top + s.by[r * 6]
        val leftToRight = r % 2 == 0
        pts.addAll(listOf(if (leftToRight) x0 else x1, y, if (leftToRight) x1 else x0, y))
    }
    out.add(Stroke(SWEEP_AT, rows * SWEEP_ROW, pts.toDoubleArray()))
    return out
}

/** A point along a stroke's polyline, `u` of the way by length. */
private fun along(pts: DoubleArray, u: Double): Pair<Double, Double> {
    val n = pts.size / 2
    if (n == 1) return pts[0] to pts[1]
    var total = 0.0
    for (i in 1 until n) total += hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
    var d = clamp01(u) * total
    for (i in 1 until n) {
        val l = hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
        if (d <= l || i == n - 1) {
            val t = if (l > 0) min(1.0, d / l) else 0.0
            return (pts[2 * i - 2] + (pts[2 * i] - pts[2 * i - 2]) * t) to (pts[2 * i - 1] + (pts[2 * i + 1] - pts[2 * i - 1]) * t)
        }
        d -= l
    }
    return pts[pts.size - 2] to pts[pts.size - 1]
}

/**
 * Draws once into a bitmap `w` × `h` px: through a picture into a hardware bitmap on API 28+ (so
 * runtime shaders and blurs render exactly as they do on screen), into a plain bitmap below that.
 * Null if the bitmap cannot be made; callers then draw live.
 */
private fun bakeBitmap(w: Int, h: Int, draw: (Canvas) -> Unit): Bitmap? {
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            draw(picture.beginRecording(w, h))
            picture.endRecording()
            Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/**
 * The parts of the sheet that look the same on every bubble, baked once per bubble size so a frame
 * draws bitmaps instead of dozens of blurs and gradients: a resting dome's shadow and caustic
 * (`under`), its gloss (`gloss`: rim light, rim, specular and glint), and each bubble's settled
 * wrinkle (`wrinkles`, one cell per bubble, since each is seeded by its index). A bubble that is
 * pressed, collapsing or fading in is still drawn live. Sprites are placed on whole pixels, so they
 * show exactly as the live drawing would.
 */
private class BubbleSprites {
    private var r = -1f
    private var density = 0f
    private var count = -1
    var under: Bitmap? = null
        private set
    var gloss: Bitmap? = null
        private set
    var wrinkles: Bitmap? = null
        private set
    private var cols = 1
    private var cell = 0
    /** The canvas's offset from the screen in dp (the shake), for snapping to pixels. */
    var ox = 0f
    var oy = 0f
    private val src = android.graphics.Rect()
    private val dst = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun prepare(r: Float, density: Float, count: Int, paints: BubblePaints) {
        if (r == this.r && density == this.density && count == this.count) return
        this.r = r
        this.density = density
        this.count = count
        under = null
        gloss = null
        wrinkles = null
        if (!(r > 0f) || !r.isFinite() || !(density > 0f) || !density.isFinite() || count <= 0) return
        // The shadow reaches 1.08 r plus its blur from the centre; the gloss stays inside r.
        val underPx = side(r * 1.2f + 12f)
        under = bakeBitmap(underPx, underPx) { c ->
            c.scale(density, density)
            val h = underPx / density / 2
            drawUnder(c, h, h, r, 0f, paints)
        }
        val glossPx = side(r + 4f)
        gloss = bakeBitmap(glossPx, glossPx) { c ->
            c.scale(density, density)
            val h = glossPx / density / 2
            drawGloss(c, h, h, r, r, r, 0.0, 1.0, paints)
        }
        cell = side(r + 3f)
        cols = ceil(kotlin.math.sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = (count + cols - 1) / cols
        wrinkles = bakeBitmap(cols * cell, rows * cell) { c ->
            c.scale(density, density)
            val h = cell / density / 2
            for (i in 0 until count) {
                val cx = (i % cols) * cell / density + h
                val cy = (i / cols) * cell / density + h
                drawWrinkle(c, cx, cy, r, i * 1.7 + 0.3, 1.0, paints)
            }
        }
    }

    /** An even number of pixels covering `half` dp either side of a centre. */
    private fun side(half: Float): Int = 2 * ceil(half * density).toInt()

    /** Draws `bitmap` (or a `size` px cell of it at `sx`, `sy`) centred on (x, y) dp, on whole pixels. */
    private fun place(c: Canvas, bitmap: Bitmap, sx: Int, sy: Int, size: Int, x: Float, y: Float, alpha: Float) {
        val half = size / 2f
        val px = Math.round((ox + x) * density - half).toFloat()
        val py = Math.round((oy + y) * density - half).toFloat()
        dst.set(px / density - ox, py / density - oy, (px + size) / density - ox, (py + size) / density - oy)
        src.set(sx, sy, sx + size, sy + size)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        c.drawBitmap(bitmap, src, dst, paint)
    }

    /** A resting dome's shadow and caustic; false if it has to be drawn live. */
    fun under(c: Canvas, x: Float, y: Float): Boolean {
        val b = under ?: return false
        place(c, b, 0, 0, b.width, x, y, 1f)
        return true
    }

    /** A resting dome's gloss; false if it has to be drawn live. */
    fun gloss(c: Canvas, x: Float, y: Float): Boolean {
        val b = gloss ?: return false
        place(c, b, 0, 0, b.width, x, y, 1f)
        return true
    }

    /** Bubble `i`'s settled wrinkle; false if it has to be drawn live. */
    fun wrinkle(c: Canvas, i: Int, x: Float, y: Float, alpha: Float): Boolean {
        val b = wrinkles ?: return false
        if (i < 0 || i >= count) return false
        place(c, b, (i % cols) * cell, (i / cols) * cell, cell, x, y, alpha)
        return true
    }
}

/**
 * The backdrop and the film's crinkles. On API 33+ they are the React Native shaders in AGSL; below
 * that the backdrop is its four pools drawn as Gaussian radial gradients (no grain), the domes
 * magnify them by redrawing them scaled inside each dome, and the film has no crinkles.
 */
private class BubbleShaders {
    private val plain: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(BACKDROP_AGSL) else null
    private val lens: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(BACKDROP_AGSL) else null
    val crinkle: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(CRINKLE_AGSL).apply { setFloatUniform("strength", 0.13f) }
    } else {
        null
    }
    private val matrix = Matrix()
    private val clipPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var width = 0f
    private var height = 0f
    private var phase = 0f
    // The film's crinkles never move, so they are baked once at the sheet's size and drawn as a
    // bitmap. The backdrop drifts with `phase`, so it is drawn live: one shader pass for the screen
    // and one small one per dome.
    private var crinkleBitmap: Bitmap? = null
    private var crinkleW = 0f
    private var crinkleH = 0f

    fun frame(width: Float, height: Float, phase: Float) {
        this.width = width
        this.height = height
        this.phase = phase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (s in listOf(plain, lens)) {
                if (s is RuntimeShader) {
                    s.setFloatUniform("res", width, height)
                    s.setFloatUniform("phase", phase)
                }
            }
        }
    }

    /** The crinkles for a sheet `w` × `h` dp, baked once; null below API 33 (the film has none there). */
    fun crinkles(w: Float, h: Float, density: Float): Bitmap? {
        val shader = crinkle ?: return null
        if (w == crinkleW && h == crinkleH) return crinkleBitmap
        crinkleW = w
        crinkleH = h
        crinkleBitmap = null
        if (w <= 0 || h <= 0 || !density.isFinite() || density <= 0) return null
        crinkleBitmap = bakeBitmap(ceil(w * density).toInt(), ceil(h * density).toInt()) { pc ->
            pc.scale(density, density)
            paint.reset()
            paint.color = SHADED
            paint.shader = shader
            pc.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        return crinkleBitmap
    }

    /** Fills `rect` (or `oval`) with the backdrop, magnified `m` times about (lx, ly). */
    fun fill(c: Canvas, rect: RectF, oval: Boolean, alpha: Float, lx: Float = 0f, ly: Float = 0f, m: Float = 1f) {
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = (alpha * 255).roundToInt()
        if (plain != null && lens != null) {
            if (m == 1f) {
                paint.shader = plain
            } else {
                matrix.setTranslate(lx, ly)
                matrix.preScale(m, m)
                matrix.preTranslate(-lx, -ly)
                lens.setLocalMatrix(matrix)
                paint.shader = lens
            }
            if (oval) c.drawOval(rect, paint) else c.drawRect(rect, paint)
            paint.shader = null
            return
        }
        c.save()
        if (oval) {
            clipPath.reset()
            clipPath.addOval(rect, Path.Direction.CW)
            c.clipPath(clipPath)
        } else {
            c.clipRect(rect)
        }
        if (alpha < 1f) c.saveLayerAlpha(rect, (alpha * 255).roundToInt())
        c.scale(m, m, lx, ly)
        pools(c)
        if (alpha < 1f) c.restore()
        c.restore()
    }

    private val base = Color(0.99f, 0.958f, 0.93f).toArgb()
    private val pools = listOf(
        floatArrayOf(1f, 0.75f, 0.62f, 0.9f, 0.3f),
        floatArrayOf(0.98f, 0.62f, 0.76f, 0.8f, 0.29f),
        floatArrayOf(0.72f, 0.66f, 1f, 0.85f, 0.34f),
        floatArrayOf(0.6f, 0.83f, 1f, 0.8f, 0.3f),
    )

    /** The backdrop without a shader: each pool `mix`ed in is the same as its colour laid over at its weight. */
    private fun pools(c: Canvas) {
        val ph = phase.toDouble()
        val ax = width / height
        val centres = listOf(
            (ax * (0.16 + 0.08 * sin(ph))) to (0.2 + 0.05 * cos(ph)),
            (ax * (0.88 + 0.06 * cos(ph))) to (0.4 + 0.06 * sin(ph)),
            (ax * (0.28 + 0.07 * cos(ph))) to (0.68 + 0.05 * sin(ph)),
            (ax * (0.82 + 0.05 * sin(ph))) to (0.88 + 0.04 * cos(ph)),
        )
        paint.reset()
        paint.color = base
        c.drawRect(-width, -height, width * 2, height * 2, paint)
        val stops = 10
        for ((k, pool) in pools.withIndex()) {
            val r = pool[4] * height * 2.6f
            val colours = IntArray(stops) {
                val t = it / (stops - 1f) * 2.6f
                Color(pool[0], pool[1], pool[2], (pool[3] * exp(-(t * t).toDouble())).toFloat()).toArgb()
            }
            val positions = FloatArray(stops) { it / (stops - 1f) }
            val cx = (centres[k].first * height).toFloat()
            val cy = (centres[k].second * height).toFloat()
            paint.shader = RadialGradient(cx, cy, r, colours, positions, Shader.TileMode.CLAMP)
            c.drawRect(-width, -height, width * 2, height * 2, paint)
        }
        paint.shader = null
    }
}

/** The paints and paths the scene reuses every frame. */
private class BubblePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val rect = RectF()
    val oval = RectF()
    val sheetRect = RectF()
    val clip = Path()
    val path = Path()
    val lines = Path()
    val matrix = Matrix()
    private val digits = Array(10) { "$it" }
    private var countLabel = ""
    private var countFor = -1

    fun digit(d: Int): String = digits[((d % 10) + 10) % 10]

    fun countLabel(count: Int): String {
        if (count != countFor) {
            countFor = count
            countLabel = "/ $count"
        }
        return countLabel
    }

    // The film's whitening and its sheen, made once for a sheet size and moved with local matrices.
    private var filmW = -1f
    private var filmH = -1f
    var filmShade: Shader? = null
        private set
    var sheen: Shader? = null
        private set

    fun film(w: Float, h: Float) {
        if (w == filmW && h == filmH) return
        filmW = w
        filmH = h
        filmShade = LinearGradient(0f, 0f, w, h, intArrayOf(white(0.2), white(0.1), white(0.18)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        sheen = LinearGradient(-90f, 0f, 90f, h * 0.6f, intArrayOf(white(0.0), white(0.16), white(0.0)), null, Shader.TileMode.CLAMP)
    }
    private val blurs = HashMap<Float, BlurMaskFilter>()
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 46f
        typeface = weighted(400)
        color = Ink.toArgb()
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        typeface = weighted(400)
        color = InkSoft.toArgb()
    }

    fun blur(sigma: Float): BlurMaskFilter = blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    fun solid(color: Int, sigma: Float = 0f): Paint = fill.apply {
        reset()
        isAntiAlias = true
        this.color = color
        maskFilter = if (sigma > 0f) blur(sigma) else null
    }

    fun line(color: Int, width: Float, sigma: Float = 0f): Paint = stroke.apply {
        reset()
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
        maskFilter = if (sigma > 0f) blur(sigma) else null
    }

    private fun weighted(weight: Int): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}

/** The sheet's state between frames: the simulation, the autoPlay clock and the script's fingers. */
private class BubbleMotion {
    var sheet: BubbleSheet? = null
    var script: List<Stroke> = emptyList()
    var strokeDown = BooleanArray(0)
    var clock = 0.0
    var looping = false
    var hold = 0.0
    /** Seconds since the block appeared, for the backdrop's drift. */
    var elapsed = 0.0
}

/**
 * A sheet of bubble wrap over a soft pastel backdrop that shows through the clear film. Each bubble
 * is a glossy dome that magnifies the colours behind it. Pressed, a bubble squashes, trembles and
 * pops into a crumpled film with a burst of tiny air rings; a slide pops a whole row, and the film's
 * edge can be tugged. A rolling counter shows how many are popped; when none are left a fresh sheet
 * slides in.
 */
@Composable
fun BubbleWrapPop(
    modifier: Modifier = Modifier,
    /** Pops a smiley, sweeps the rest, and lets a fresh sheet slide in, on a loop. */
    autoPlay: Boolean = false,
    /** Called with the running count after each pop by hand. */
    onPop: ((Int, Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val motion = remember { BubbleMotion() }
    val paints = remember { BubblePaints() }
    val shaders = remember { BubbleShaders() }
    val sprites = remember { BubbleSprites() }
    var tick by remember { mutableIntStateOf(0) }
    val popCallback by rememberUpdatedState(onPop)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Paper)
            .semantics { contentDescription = "Bubble wrap" },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val sheetTop = safeTop + HEADER
        val sheetBottom = height - safeBottom - FOOTER

        LaunchedEffect(auto) {
            motion.looping = auto
            motion.clock = 0.0
            motion.hold = 0.0
        }

        LaunchedEffect(width, sheetTop, sheetBottom, reducedMotion) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 30)
                    last = now
                    if (width <= 0 || dt <= 0) return@withFrameNanos
                    motion.elapsed += dt
                    var s = motion.sheet
                    if (s == null || s.width != width || s.top != sheetTop || s.bottom != sheetBottom) {
                        s = BubbleSheet(width, SIDE, sheetTop, width - SIDE, sheetBottom, reducedMotion)
                        motion.sheet = s
                        motion.script = buildScript(s)
                        motion.strokeDown = BooleanArray(motion.script.size)
                    }
                    if (motion.hold > 0) {
                        motion.hold -= dt
                        if (motion.hold <= 0) motion.clock = 0.0
                    } else if (motion.looping) {
                        val t = (motion.clock + dt) % LOOP
                        motion.clock = t
                        motion.script.forEachIndexed { i, k ->
                            val u = (t - k.at) / k.dur
                            val (x, y) = along(k.pts, u)
                            if (u >= 0 && u < 1) {
                                if (!motion.strokeDown[i]) {
                                    s.down(SCRIPT_ID + i, x, y, true)
                                    motion.strokeDown[i] = true
                                } else {
                                    s.move(SCRIPT_ID + i, x, y)
                                }
                            } else if (motion.strokeDown[i]) {
                                s.up(SCRIPT_ID + i)
                                motion.strokeDown[i] = false
                            }
                        }
                    }
                    s.step(dt)
                    if (s.events != 0) {
                        if (s.eventsByUser && s.events and EVENT_POP != 0) {
                            view.performHapticFeedback(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
                            )
                            popCallback?.invoke(s.pops, s.count)
                        }
                        if (s.events and EVENT_SWAP != 0 && s.userFingers() == 0 && motion.hold > 0) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        s.events = 0
                        s.eventsByUser = false
                    }
                    tick++
                }
            }
        }

        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Every finger, from the moment it lands, so a press starts building at once.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var first = true
                        do {
                            val event = if (first) currentEvent else awaitPointerEvent()
                            first = false
                            val s = motion.sheet
                            for (c in event.changes) {
                                if (s == null) continue
                                val px = c.position.x / density.density.toDouble()
                                val py = c.position.y / density.density.toDouble()
                                val id = c.id.value
                                when {
                                    c.pressed && !c.previousPressed -> {
                                        // A real touch pauses the loop and lifts autoPlay's fingers.
                                        motion.hold = USER_HOLD
                                        s.cancelScripted()
                                        motion.strokeDown.fill(false)
                                        s.down(id, px, py, false)
                                    }
                                    c.pressed -> if (c.position != c.previousPosition) {
                                        motion.hold = USER_HOLD
                                        s.move(id, px, py)
                                    }
                                    c.previousPressed -> s.up(id)
                                }
                                c.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            tick
            val s = motion.sheet ?: return@Canvas
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                drawScene(nc, s, width.toFloat(), height.toFloat(), motion.elapsed, safeTop.toFloat(), paints, shaders, sprites, density.density)
                nc.restore()
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 18).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val kicker = TextStyle(color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
            BasicText("POPPED", style = kicker)
            BasicText("BUBBLE WRAP · A4", style = kicker)
        }
        BasicText(
            "Press to pop · Slide across a row · Tug the edge",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 26).dp),
            style = TextStyle(
                color = InkSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** The whole scene in dp, under the light camera shake. */
private fun drawScene(
    c: Canvas,
    s: BubbleSheet,
    width: Float,
    height: Float,
    elapsed: Double,
    safeTop: Float,
    paints: BubblePaints,
    shaders: BubbleShaders,
    sprites: BubbleSprites,
    density: Float,
) {
    shaders.frame(width, height, ((elapsed % LOOP) / LOOP * PI * 2).toFloat())
    sprites.prepare(s.radius.toFloat(), density, s.count, paints)
    paints.film((s.right - s.left).toFloat(), (s.bottom - s.top).toFloat())
    c.save()
    sprites.ox = s.shakeX.toFloat()
    sprites.oy = s.shakeY.toFloat()
    c.translate(sprites.ox, sprites.oy)
    // The backdrop overhangs the screen for the shake.
    paints.rect.set(-12f, -12f, width + 12, height + 12)
    shaders.fill(c, paints.rect, false, 1f)
    if (s.swap > 0) {
        val e = easeInOut(s.swap).toFloat()
        val away = width * 1.1f
        drawSheet(c, s, -e * away, 0f, 1, paints, shaders, sprites, density)
        drawSheet(c, s, (1 - e) * away, 0f, 2, paints, shaders, sprites, density)
    } else {
        drawSheet(c, s, s.tugX.toFloat(), s.tugY.toFloat(), 0, paints, shaders, sprites, density)
    }
    drawCounter(c, s, 24f, safeTop + 92, paints)
    c.restore()
}

/** An intact dome's shadow and caustic, drawn under the film. */
private fun drawUnder(c: Canvas, x: Float, y: Float, r: Float, squash: Float, paints: BubblePaints) {
    c.drawCircle(x + r * 0.12f, y + r * 0.2f, r * (0.96f + squash * 0.12f), paints.solid(plum(0.13 * (1 - squash * 0.5)), 3.5f))
    // Light focused by the dome onto what is behind it.
    paints.rect.set(x + r * 0.05f, y + r * 0.32f, x + r * 0.75f, y + r * 0.66f)
    c.drawOval(paints.rect, paints.solid(white(0.42 * (1 - squash)), 4f))
}

private fun drawDome(
    c: Canvas,
    x: Float,
    y: Float,
    r: Float,
    squash: Double,
    leanX: Double,
    leanY: Double,
    alpha: Double,
    paints: BubblePaints,
    shaders: BubbleShaders,
    sprites: BubbleSprites,
) {
    // Squashed, the dome flattens and bulges out, away from the finger.
    val height = 1 - 0.6 * squash
    val grow = 1 + 0.17 * squash
    val cx = (x - leanX * r * 0.14 * squash).toFloat()
    val cy = (y - leanY * r * 0.14 * squash).toFloat()
    val rx = (r * grow * (1 + abs(leanY) * 0.05 * squash)).toFloat()
    val ry = (r * grow * (1 + abs(leanX) * 0.05 * squash)).toFloat()
    val oval = paints.oval
    oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
    // Through the dome the backdrop is magnified and pushed toward the light.
    shaders.fill(c, oval, true, alpha.toFloat(), cx + r * 0.18f, cy + r * 0.22f, (1 + 0.26 * height).toFloat())
    // A resting dome's gloss is the same on every bubble: a baked sprite.
    if (squash <= 0.0 && alpha >= 1.0 && sprites.gloss(c, cx, cy)) return
    drawGloss(c, cx, cy, rx, ry, r, squash, alpha, paints)
}

/** The dome's plastic over its magnified view: rim light, rim, pressure band, specular and glint. */
private fun drawGloss(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, r: Float, squash: Double, a: Double, paints: BubblePaints) {
    val height = 1 - 0.6 * squash
    val oval = paints.oval
    oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
    // The plastic is thicker toward the rim and catches more light there.
    val fresnel = paints.solid(SHADED).apply {
        shader = RadialGradient(
            cx, cy, max(rx, ry),
            intArrayOf(white(0.03 * a), white(0.06 * a), white(0.34 * a), white(0.1 * a)),
            floatArrayOf(0f, 0.6f, 0.93f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawOval(oval, fresnel)
    fresnel.shader = null
    // Rim: lit on the upper left, shadowed on the lower right.
    val rim = paints.line(SHADED, 1.1f).apply {
        shader = LinearGradient(
            cx - rx, cy - ry, cx + rx, cy + ry,
            intArrayOf(white(0.75 * a), white(0.12 * a), plum(0.16 * a)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    paints.rect.set(cx - rx + 0.6f, cy - ry + 0.6f, cx + rx - 0.6f, cy + ry - 0.6f)
    c.drawOval(paints.rect, rim)
    rim.shader = null
    // Pressure shows as a tight bright band along the rim.
    if (squash > 0.02) {
        paints.rect.set(cx - rx + 2, cy - ry + 2, cx + rx - 2, cy + ry - 2)
        c.drawOval(paints.rect, paints.line(white(0.4 * squash * a), 2.2f, 1.4f))
    }
    // Specular: a sharp window on the upper left that flattens and slides as the dome squashes,
    // and a small glint low on the right.
    c.save()
    c.translate((cx - rx * (0.36 + 0.12 * squash)).toFloat(), (cy - ry * (0.4 - 0.1 * squash)).toFloat())
    c.rotate(-36f)
    val sw = (r * (0.42 + 0.2 * squash)).toFloat()
    val shh = (r * 0.2 * height).toFloat()
    paints.rect.set(-sw / 2, -shh / 2, sw / 2, shh / 2)
    c.drawOval(paints.rect, paints.solid(white(0.9 * a * (0.55 + 0.45 * height)), 0.9f))
    c.restore()
    c.drawCircle(cx + rx * 0.4f, cy + ry * 0.42f, r * 0.08f, paints.solid(white(0.55 * a * height), 1.2f))
}

/** A popped bubble: the film it leaves, crumpled flat with a few creases. */
private fun drawWrinkle(c: Canvas, x: Float, y: Float, r: Float, seed: Double, alpha: Double, paints: BubblePaints) {
    if (alpha < 0.01) return
    // A soft, lumpy outline: two seeded waves around the circle, joined with smooth curves.
    val n = 18
    val ph1 = hash(seed, 1.0) * PI * 2
    val ph2 = hash(seed, 2.0) * PI * 2
    val px = FloatArray(n)
    val py = FloatArray(n)
    for (i in 0 until n) {
        val a = i.toDouble() / n * PI * 2
        val rr = r * (0.86 + 0.07 * sin(a * 3 + ph1) + 0.05 * sin(a * 5 + ph2) + 0.03 * (hash(i.toDouble(), seed) - 0.5))
        px[i] = (x + cos(a) * rr).toFloat()
        py[i] = (y + sin(a) * rr).toFloat()
    }
    val film = paints.path
    film.reset()
    film.moveTo((px[n - 1] + px[0]) / 2, (py[n - 1] + py[0]) / 2)
    for (i in 0 until n) {
        val j = (i + 1) % n
        film.quadTo(px[i], py[i], (px[i] + px[j]) / 2, (py[i] + py[j]) / 2)
    }
    film.close()
    val fill = paints.solid(SHADED).apply {
        shader = RadialGradient(x - r * 0.2f, y - r * 0.25f, r * 1.2f, white(0.34 * alpha), white(0.12 * alpha), Shader.TileMode.CLAMP)
    }
    c.drawPath(film, fill)
    fill.shader = null
    c.drawPath(film, paints.line(white(0.7 * alpha), 0.9f).apply { strokeJoin = Paint.Join.ROUND })
    // Creases: a light line with a faint shadow beside it.
    val lines = paints.lines
    lines.reset()
    for (k in 0 until 5) {
        val kd = k.toDouble()
        val a = hash(seed + kd, 3.1) * PI * 2
        val from = r * (0.05 + 0.25 * hash(kd, seed + 1))
        val to = r * (0.5 + 0.3 * hash(seed + 2, kd))
        val bend = (hash(seed, kd + 9) - 0.5) * 0.5
        lines.moveTo((x + cos(a) * from).toFloat(), (y + sin(a) * from).toFloat())
        lines.lineTo((x + cos(a + bend) * (from + to) * 0.55).toFloat(), (y + sin(a + bend) * (from + to) * 0.55).toFloat())
        lines.lineTo((x + cos(a) * to).toFloat(), (y + sin(a) * to).toFloat())
    }
    val ink = paints.line(plum(0.14 * alpha), 0.8f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    c.save()
    c.translate(0.6f, 0.7f)
    c.drawPath(lines, ink)
    c.restore()
    ink.color = white(0.85 * alpha)
    c.drawPath(lines, ink)
}

/** The burst: a shock ring and small rings of air thrown out and fading. */
private fun drawBurst(c: Canvas, x: Float, y: Float, r: Float, age: Double, seed: Double, paints: BubblePaints) {
    val k = age / SHOCK_TIME
    if (k < 1) {
        val e = 1 - (1 - k) * (1 - k)
        c.drawCircle(x, y, (r * (0.85 + 1.25 * e)).toFloat(), paints.line(white(0.7 * (1 - k)), (2.4 * (1 - k) + 0.4).toFloat()))
    }
    val life = clamp01(age / POP_LIFE)
    if (life >= 1) return
    val fade = (1 - life).pow(1.4)
    val ring = paints.line(white(0.85 * fade), 1.1f)
    for (j in 0 until AIR_RINGS) {
        val jd = j.toDouble()
        val a = jd / AIR_RINGS * PI * 2 + hash(seed, jd) * 0.8
        val v = AIR_SPEED * (0.6 + 0.8 * hash(jd, seed))
        val d = r * 0.7 + v * AIR_DRAG * (1 - exp(-age / AIR_DRAG))
        val rr = 1.2 + 3.2 * life * (0.6 + 0.6 * hash(seed + jd, 2.0))
        c.drawCircle((x + cos(a) * d).toFloat(), (y + sin(a) * d - 10 * life).toFloat(), rr.toFloat(), ring)
    }
}

/** The sheet: look 1 draws every bubble popped long ago, 2 every bubble whole; 0 the sheet as it is. */
private fun drawSheet(
    c: Canvas,
    s: BubbleSheet,
    ox: Float,
    oy: Float,
    look: Int,
    paints: BubblePaints,
    shaders: BubbleShaders,
    sprites: BubbleSprites,
    density: Float,
) {
    val l = s.left.toFloat() + ox
    val t = s.top.toFloat() + oy
    val w = (s.right - s.left).toFloat()
    val h = (s.bottom - s.top).toFloat()
    c.drawRoundRect(l, t + 14, l + w, t + h + 14, SHEET_RADIUS, SHEET_RADIUS, paints.solid(plum(0.16), 20f))
    // Clear film over the backdrop: a little whitening, the backdrop redrawn so the shadow above
    // only shows around the edges.
    c.save()
    val clip = paints.clip
    clip.reset()
    clip.addRoundRect(l, t, l + w, t + h, SHEET_RADIUS, SHEET_RADIUS, Path.Direction.CW)
    c.clipPath(clip)
    val sheetRect = paints.sheetRect
    sheetRect.set(l, t, l + w, t + h)
    shaders.fill(c, sheetRect, false, 1f)
    val r = s.radius.toFloat()
    for (i in 0 until s.count) {
        val popped = look == 1 || (look == 0 && s.popped[i])
        if (popped) continue
        val x = l + s.bx[i].toFloat()
        val y = t + s.by[i].toFloat()
        val squash = if (look == 0) clamp01(s.pressure[i]).toFloat() else 0f
        if (squash <= 0f && sprites.under(c, x, y)) continue
        drawUnder(c, x, y, r, squash, paints)
    }
    val film = paints.solid(SHADED)
    paints.filmShade?.let { shade ->
        paints.matrix.setTranslate(l, t)
        shade.setLocalMatrix(paints.matrix)
        film.shader = shade
        c.drawRect(sheetRect, film)
        film.shader = null
    }
    val crinkles = shaders.crinkles(w, h, density)
    if (crinkles != null) {
        c.drawBitmap(crinkles, null, sheetRect, paints.bitmap)
    } else {
        shaders.crinkle?.let { crinkle ->
            paints.matrix.setTranslate(l, t)
            crinkle.setLocalMatrix(paints.matrix)
            film.shader = crinkle
            c.drawRect(sheetRect, film)
            film.shader = null
        }
    }
    // A broad sheen across the film that slides as the film is tugged.
    paints.sheen?.let { sheen ->
        val sx = l + w * 0.3f + (s.tugX * 2.2).toFloat()
        paints.matrix.setTranslate(sx, t)
        sheen.setLocalMatrix(paints.matrix)
        film.shader = sheen
        c.drawRect(sheetRect, film)
        film.shader = null
    }

    for (i in 0 until s.count) {
        val x = l + s.bx[i].toFloat()
        val y = t + s.by[i].toFloat()
        if (look == 2 || (look == 0 && !s.popped[i])) {
            var squash = if (look == 0) clamp01(s.pressure[i]) else 0.0
            // Close to bursting it trembles.
            if (squash > 0.7 && !s.calm) squash += sin(s.time * 95 + i) * 0.04 * ((squash - 0.7) / 0.3)
            drawDome(c, x, y, r, squash, s.leanX[i], s.leanY[i], 1.0, paints, shaders, sprites)
            continue
        }
        val age = if (look == 1) 10.0 else s.popAge[i]
        val k = clamp01(age / COLLAPSE)
        if (k < 1) drawDome(c, x, y, (r * (1 - 0.25 * k)).toFloat(), 1.0, s.leanX[i], s.leanY[i], 1 - k, paints, shaders, sprites)
        // A settled wrinkle is a baked sprite; one still fading in is drawn live.
        if (k >= 1 && sprites.wrinkle(c, i, x, y, 1f)) continue
        drawWrinkle(c, x, y, r, i * 1.7 + 0.3, k, paints)
    }
    // The film's edge.
    val edge = paints.line(SHADED, 1.2f).apply {
        shader = LinearGradient(l, t, l, t + h, white(0.85), white(0.35), Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(l + 0.6f, t + 0.6f, l + w - 0.6f, t + h - 0.6f, SHEET_RADIUS - 0.6f, SHEET_RADIUS - 0.6f, edge)
    edge.shader = null
    c.restore()
    // Bursts fly past the film's edge.
    if (look == 0) {
        for (i in 0 until s.count) {
            if (s.popped[i] && s.popAge[i] >= 0 && s.popAge[i] < POP_LIFE) {
                drawBurst(c, l + s.bx[i].toFloat(), t + s.by[i].toFloat(), r, s.popAge[i], i * 3.3 + 1, paints)
            }
        }
    }
}

/** The count, as an odometer: each digit rolls through to the next. */
private fun drawCounter(c: Canvas, s: BubbleSheet, x: Float, baseline: Float, paints: BubblePaints) {
    val big = paints.big
    val cell = big.measureText("0")
    val lineH = 50f
    val shown = max(0.0, s.shown)
    c.save()
    val places = if (s.count >= 100) 3 else 2
    c.clipRect(x - 4, baseline - lineH + 6, x - 4 + cell * places + 8, baseline + 8)
    for (place in 0 until places) {
        val unit = 10.0.pow(places - 1 - place)
        // A higher digit turns only while the digit below rolls from 9 to 10.
        val below = shown % unit
        val pos = floor(shown / unit) + (if (unit > 1) max(0.0, below - (unit - 1)) else 0.0)
        val whole = floor(pos)
        val frac = pos - whole
        for (k in 0 until 2) {
            val digit = (((whole.toInt() + k) % 10) + 10) % 10
            val y = (baseline - (frac - k) * lineH).toFloat()
            val glyph = paints.digit(digit)
            val gw = big.measureText(glyph)
            c.drawText(glyph, x + place * cell + (cell - gw) / 2, y, big)
        }
    }
    c.restore()
    c.drawText(paints.countLabel(s.count), x + cell * places + 10, baseline, paints.small)
}
