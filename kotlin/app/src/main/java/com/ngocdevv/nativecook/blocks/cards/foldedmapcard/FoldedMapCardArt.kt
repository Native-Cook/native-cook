package com.ngocdevv.nativecook.blocks.cards.foldedmapcard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Folded Map Card — the printed map, drawn once into one bitmap (an atlas: the map's face on the
 left, the back of a panel on the right), plus the route and its elevation profile. A port of
 foldedMapArt.ts with the same terrain, contours, labels and grain.
*/

internal const val FOLDED_MAP_PROFILE_N = 36

internal class FoldedMapArt(
    /** The atlas at [scale] pixels per dp: (MW + pw) × MH dp. */
    val image: Bitmap,
    val scale: Float,
    /** The route in map coordinates: x0, y0, x1, y1, … */
    val route: FloatArray,
    /** Elevation along the route, 0 (trailhead) … 1 (summit), PROFILE_N samples. */
    val profile: FloatArray,
)

private fun hash(x: Double, y: Double): Double {
    val s = sin(x * 127.1 + y * 311.7) * 43758.5453
    return s - floor(s)
}

private fun vnoise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    var fx = x - ix
    var fy = y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hash(ix, iy)
    val b = hash(ix + 1, iy)
    val c = hash(ix, iy + 1)
    val d = hash(ix + 1, iy + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private fun fbm(x: Double, y: Double): Double =
    vnoise(x, y) * 0.5 + vnoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * 0.27 +
        vnoise(x * 4.1 + 9.1, y * 4.1 - 3.7) * 0.15 + vnoise(x * 8.3 - 2.2, y * 8.3 + 7.7) * 0.08

private const val PEAK_X = 0.86
private const val PEAK_Y = 0.26
private const val LAKE_X = 0.22
private const val LAKE_Y = 0.68

private fun sq(v: Double) = v * v

/** Terrain height at a normalised map point (0..1 across, 0..1 down). */
private fun terrain(nx: Double, ny: Double): Double {
    // Distance from the ridge line, running from the lower left toward the peak.
    val ax = 0.05
    val ay = 0.95
    val vx = PEAK_X - ax
    val vy = PEAK_Y - ay
    val t = max(0.0, min(1.0, ((nx - ax) * vx + (ny - ay) * vy) / (vx * vx + vy * vy)))
    val dx = nx - (ax + vx * t)
    val dy = ny - (ay + vy * t)
    val ridge = exp(-(dx * dx + dy * dy) / 0.03) * (0.25 + 0.45 * t)
    val peak = exp(-(sq(nx - PEAK_X) + sq(ny - PEAK_Y)) / 0.018) * 0.45
    val lake = exp(-(sq(nx - LAKE_X) + sq((ny - LAKE_Y) * 1.4)) / 0.012) * 0.3
    return ridge + peak - lake + fbm(nx * 4.2, ny * 3.1) * 0.32
}

private val ROUTE_CTRL = doubleArrayOf(
    0.07, 0.88, 0.15, 0.8, 0.24, 0.82, 0.31, 0.71, 0.29, 0.6, 0.38, 0.55, 0.46, 0.59, 0.52, 0.47, 0.6, 0.43, 0.66, 0.47, 0.72, 0.38,
    0.78, 0.33, 0.83, 0.3, 0.86, 0.26,
)

private fun catmull(ctrl: DoubleArray, samples: Int): FloatArray {
    val n = ctrl.size / 2
    val out = FloatArray((samples + 1) * 2)
    fun p(k: Int, c: Int) = ctrl[max(0, min(n - 1, k)) * 2 + c]
    for (s in 0..samples) {
        val f = s.toDouble() / samples * (n - 1)
        val i = min(n - 2, floor(f).toInt())
        val t = f - i
        for (c in 0 until 2) {
            val p0 = p(i - 1, c)
            val p1 = p(i, c)
            val p2 = p(i + 1, c)
            val p3 = p(i + 2, c)
            val t2 = t * t
            val t3 = t2 * t
            out[s * 2 + c] = (0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)).toFloat()
        }
    }
    return out
}

private fun typefaceOf(weight: Int, italic: Boolean): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, italic)
    } else {
        val bold = weight >= 600
        Typeface.create(
            Typeface.SANS_SERIF,
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
    }

private fun font(size: Float, weight: Int, italic: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = typefaceOf(weight, italic)
    textSize = size
}

private fun parse(color: String): Int = android.graphics.Color.parseColor(color)

private fun rgba(r: Int, g: Int, b: Int, a: Float): Int = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private fun label(c: Canvas, text: String, font: Paint, x: Float, y: Float, color: Int, halo: Boolean = true) {
    if (halo) {
        font.style = Paint.Style.STROKE
        font.strokeWidth = 2.4f
        font.strokeJoin = Paint.Join.ROUND
        font.color = rgba(240, 232, 212, 0.85f)
        c.drawText(text, x, y, font)
        font.style = Paint.Style.FILL
    }
    font.color = color
    c.drawText(text, x, y, font)
}

/** Builds the map atlas (call off the main thread). [scale] is pixels per dp, at most 3. */
internal fun buildFoldedMap(MW: Float, MH: Float, pw: Float, scale: Float): FoldedMapArt {
    val bold = font(9f, 700)
    val small = font(7.5f, 500)
    val italic = font(8.5f, 500, italic = true)
    val title = font(17f, 800)
    val AW = MW + pw

    // 1. The tint, one pixel per TINT dp, over the whole atlas.
    val tintStep = 2f
    val tw = ceil(AW / tintStep).toInt()
    val th = ceil(MH / tintStep).toInt()
    val tint = IntArray(tw * th)
    val e = 1.0 / max(MW, MH)
    for (y in 0 until th) {
        for (x in 0 until tw) {
            var r: Double
            var g: Double
            var b: Double
            if (x * tintStep < MW) {
                r = 239.0; g = 231.0; b = 210.0
                val nx = (x * tintStep / MW).toDouble()
                val ny = (y * tintStep / MH).toDouble()
                val h = terrain(nx, ny)
                val hx = terrain(nx + e, ny) - h
                val hy = terrain(nx, ny + e) - h
                // Hill shading: light from the upper left.
                val sh = max(-1.0, min(1.0, (-hx - hy) * 60))
                val lift = 1 + sh * 0.07
                val forest = vnoise(nx * 7 + 3, ny * 5.5 - 1) * 0.72 + vnoise(nx * 13, ny * 11) * 0.28
                val f = if (h < 0.55) max(0.0, min(1.0, (forest - 0.52) * 5)) * 0.75 * min(1.0, (0.55 - h) * 8) else 0.0
                r += (190 - r) * f
                g += (210 - g) * f
                b += (165 - b) * f
                val rock = max(0.0, min(1.0, (h - 0.62) * 6))
                r += (226 - r) * rock * 0.6
                g += (218 - g) * rock * 0.6
                b += (204 - b) * rock * 0.6
                r *= lift
                g *= lift
                b *= lift * 0.99
            } else {
                r = 243.0; g = 236.0; b = 219.0
            }
            tint[y * tw + x] = android.graphics.Color.rgb(min(255, r.toInt()), min(255, g.toInt()), min(255, b.toInt()))
        }
    }
    val tintImage = Bitmap.createBitmap(tint, tw, th, Bitmap.Config.ARGB_8888)

    val iw = ceil(AW * scale).toInt()
    val ih = ceil(MH * scale).toInt()
    val image = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
    val c = Canvas(image)
    c.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    c.drawBitmap(tintImage, android.graphics.Rect(0, 0, tw, th), android.graphics.RectF(0f, 0f, tw * tintStep, th * tintStep), paint)
    tintImage.recycle()

    c.save()
    c.clipRect(0f, 0f, MW, MH)
    // 2. Kilometre grid.
    val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = rgba(70, 120, 170, 0.22f)
    }
    run {
        var x = 46f
        while (x < MW) { c.drawLine(x, 0f, x, MH, grid); x += 58f }
        var y = 30f
        while (y < MH) { c.drawLine(0f, y, MW, y, grid); y += 58f }
    }

    // 3. Contours by marching squares on a 3 dp grid; every fifth level is an index contour.
    val step = 3f
    val cols = ceil(MW / step).toInt()
    val rows = ceil(MH / step).toInt()
    val hs = DoubleArray((cols + 1) * (rows + 1))
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (j in 0..rows) for (i in 0..cols) {
        val v = terrain((i * step / MW).toDouble(), (j * step / MH).toDouble())
        hs[j * (cols + 1) + i] = v
        lo = min(lo, v)
        hi = max(hi, v)
    }
    val minor = Path()
    val index = Path()
    val levels = 22
    for (l in 1 until levels) {
        val L = lo + (hi - lo) * l / levels
        val path = if (l % 5 == 0) index else minor
        for (j in 0 until rows) for (i in 0 until cols) {
            val a = hs[j * (cols + 1) + i]
            val b = hs[j * (cols + 1) + i + 1]
            val cc = hs[(j + 1) * (cols + 1) + i + 1]
            val d = hs[(j + 1) * (cols + 1) + i]
            val k = (if (a > L) 8 else 0) or (if (b > L) 4 else 0) or (if (cc > L) 2 else 0) or (if (d > L) 1 else 0)
            if (k == 0 || k == 15) continue
            val x0 = i * step
            val y0 = j * step
            val topX = (x0 + step * (L - a) / (b - a)).toFloat(); val topY = y0
            val rightX = x0 + step; val rightY = (y0 + step * (L - b) / (cc - b)).toFloat()
            val bottomX = (x0 + step * (L - d) / (cc - d)).toFloat(); val bottomY = y0 + step
            val leftX = x0; val leftY = (y0 + step * (L - a) / (d - a)).toFloat()
            fun line(ax: Float, ay: Float, bx: Float, by: Float) {
                path.moveTo(ax, ay)
                path.lineTo(bx, by)
            }
            when (k) {
                1, 14 -> line(leftX, leftY, bottomX, bottomY)
                2, 13 -> line(bottomX, bottomY, rightX, rightY)
                3, 12 -> line(leftX, leftY, rightX, rightY)
                4, 11 -> line(topX, topY, rightX, rightY)
                6, 9 -> line(topX, topY, bottomX, bottomY)
                7, 8 -> line(topX, topY, leftX, leftY)
                5 -> { line(topX, topY, rightX, rightY); line(leftX, leftY, bottomX, bottomY) }
                10 -> { line(topX, topY, leftX, leftY); line(bottomX, bottomY, rightX, rightY) }
            }
        }
    }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.6f
        color = rgba(160, 104, 58, 0.5f)
    }
    c.drawPath(minor, line)
    line.strokeWidth = 1.1f
    line.color = rgba(150, 92, 46, 0.78f)
    c.drawPath(index, line)

    // 4. Lake and stream.
    val lake = Path()
    for (i in 0..48) {
        val a = i / 48.0 * PI * 2
        val r = 1 + 0.18 * sin(a * 3 + 0.6) + 0.1 * sin(a * 5 + 2)
        val x = (LAKE_X * MW + cos(a) * 26 * r).toFloat()
        val y = (LAKE_Y * MH + sin(a) * 14 * r).toFloat()
        if (i == 0) lake.moveTo(x, y) else lake.lineTo(x, y)
    }
    lake.close()
    paint.color = parse("#A8CDD6")
    c.drawPath(lake, paint)
    line.strokeWidth = 1f
    line.color = parse("#5F96AB")
    c.drawPath(lake, line)
    val lx = (LAKE_X * MW).toFloat()
    val ly = (LAKE_Y * MH).toFloat()
    val stream = Path().apply {
        moveTo(lx + 18, ly + 10)
        cubicTo(lx + 40, ly + 30, lx + 8, MH - 30, lx + 34, MH + 4)
    }
    line.strokeWidth = 1.4f
    c.drawPath(stream, line)

    // 5. Symbols and labels.
    val px = (PEAK_X * MW).toFloat()
    val py = (PEAK_Y * MH).toFloat()
    val tri = Path().apply {
        moveTo(px, py - 5)
        lineTo(px + 4.5f, py + 3)
        lineTo(px - 4.5f, py + 3)
        close()
    }
    paint.color = parse("#5A3A22")
    c.drawPath(tri, paint)
    label(c, "Kestrel Peak", bold, px - bold.measureText("Kestrel Peak") - 8, py - 6, parse("#4A2E1A"))
    label(c, "2 214", small, px - small.measureText("2 214") - 8, py + 6, parse("#6A4A30"))
    label(c, "Alder Lake", italic, lx - 24, ly - 20, parse("#3F7F98"))
    val hutX = 0.46f * MW
    val hutY = 0.6f * MH
    paint.color = parse("#7A2E22")
    c.drawRect(hutX - 3, hutY - 3, hutX + 3, hutY + 3, paint)
    label(c, "Larch Hut", small, hutX + 6, hutY + 12, parse("#5A3A22"))
    label(c, "RIDGE LINE", small, 0.42f * MW, 0.36f * MH, rgba(90, 58, 34, 0.7f))
    c.restore()

    // 6. The title block in the lower right (the face the folded map shows), and the creases' wear.
    val tbx = MW - 106
    paint.color = rgba(250, 246, 234, 0.94f)
    c.drawRect(tbx, MH - 44, tbx + 98, MH - 8, paint)
    line.strokeWidth = 0.8f
    line.color = rgba(60, 40, 24, 0.7f)
    c.drawRect(tbx, MH - 44, tbx + 98, MH - 8, line)
    label(c, "RIDGE LINE", bold, tbx + 6, MH - 28, parse("#2E2016"), false)
    label(c, "Alder Valley · 1:25 000", small, tbx + 6, MH - 15, parse("#5A4632"), false)
    paint.color = rgba(255, 252, 240, 0.35f)
    c.drawRect(pw - 0.6f, 0f, pw + 0.6f, MH, paint)
    c.drawRect(pw * 2 - 0.6f, 0f, pw * 2 + 0.6f, MH, paint)

    // 7. The back of a panel: cover title and a legend.
    val bx = MW + 12
    label(c, "RIDGE LINE", title, bx, 40f, parse("#2E2016"), false)
    label(c, "TRAIL", title, bx, 62f, parse("#2E2016"), false)
    label(c, "Alder Valley · Sheet 7", small, bx, 80f, parse("#6A5640"), false)
    val legend = listOf(
        parse("#B5442E") to "Route",
        rgba(150, 92, 46, 0.8f) to "Contour 20 m",
        parse("#5F96AB") to "Water",
        parse("#9DB386") to "Forest",
    )
    legend.forEachIndexed { i, (col, text) ->
        val y = 112f + i * 18
        line.color = col
        line.strokeWidth = 2f
        c.drawLine(bx, y - 3.5f, bx + 14, y - 3.5f, line)
        label(c, text, small, bx + 20, y, parse("#4A3826"), false)
    }
    label(c, "Printed Oct 2026", small, bx, MH - 16, parse("#8A7458"), false)

    // 8. Paper grain at device resolution.
    val grain = IntArray(iw * ih)
    var seed = 1234567L
    for (i in grain.indices) {
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val v = seed.toDouble() / 0x7fffffff
        val dark = v < 0.5
        val a = floor(kotlin.math.abs(v - 0.5) * 2 * 22).toInt()
        grain[i] = if (dark) android.graphics.Color.argb(a, 60, 40, 20) else android.graphics.Color.argb(a, 255, 250, 235)
    }
    val grainImage = Bitmap.createBitmap(grain, iw, ih, Bitmap.Config.ARGB_8888)
    c.save()
    c.scale(1 / scale, 1 / scale)
    c.drawBitmap(grainImage, 0f, 0f, null)
    c.restore()
    grainImage.recycle()

    // Route and profile.
    val ctrl = DoubleArray(ROUTE_CTRL.size) { i -> if (i % 2 == 0) ROUTE_CTRL[i] * MW else ROUTE_CTRL[i] * MH }
    val route = catmull(ctrl, 150)
    val heights = DoubleArray(FOLDED_MAP_PROFILE_N) { s ->
        val k = (s.toDouble() / (FOLDED_MAP_PROFILE_N - 1) * (route.size / 2 - 1)).roundToInt()
        terrain((route[k * 2] / MW).toDouble(), (route[k * 2 + 1] / MH).toDouble())
    }
    val h0 = heights.first()
    val h1 = heights.last()
    val profile = FloatArray(FOLDED_MAP_PROFILE_N) { max(-0.1, (heights[it] - h0) / (h1 - h0)).toFloat() }
    return FoldedMapArt(image, scale, route, profile)
}

/** The bounds of the pin bitmap in dp around its tip. */
internal const val FOLDED_MAP_PIN_LEFT = -20f
internal const val FOLDED_MAP_PIN_TOP = -40f

/** A map pin with its tip at (20, 40) dp in a 40 × 46 dp bitmap: a red head with a cream centre, lit from the upper left. */
internal fun buildFoldedMapPin(scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(40 * scale).toInt(), ceil(46 * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    c.translate(-FOLDED_MAP_PIN_LEFT, -FOLDED_MAP_PIN_TOP)
    val head = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-3f, -7f, -10f, -13f, -10f, -21f)
        cubicTo(-10f, -27f, -5.5f, -31f, 0f, -31f)
        cubicTo(5.5f, -31f, 10f, -27f, 10f, -21f)
        cubicTo(10f, -13f, 3f, -7f, 0f, 0f)
        close()
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = rgba(30, 10, 4, 0.4f)
    p.maskFilter = BlurMaskFilter(sigmaToRadius(2f), BlurMaskFilter.Blur.NORMAL)
    c.save()
    c.translate(1.5f, 2f)
    c.drawPath(head, p)
    c.restore()
    p.maskFilter = null
    p.color = android.graphics.Color.BLACK
    p.shader = LinearGradient(
        -10f, -31f, 10f, -4f,
        intArrayOf(parse("#FF9A72"), parse("#E0643F"), parse("#A8381F")),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawPath(head, p)
    p.shader = null
    p.color = parse("#FFF4E6")
    c.drawCircle(0f, -21f, 4f, p)
    p.color = rgba(255, 236, 224, 0.55f)
    c.drawCircle(-4.5f, -25.5f, 2f, p)
    return bmp
}

/**
 * The screen behind the map: a forest-dark gradient in three stops with a soft light where the
 * map lies, a vignette and grain (AGSL, API 33+). Same maths as BACKDROP_SKSL, without the P3 step.
 */
internal const val FOLDED_MAP_BACKDROP_AGSL = """
uniform float2 size;

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 p) {
  float2 uv = p / size;
  float3 a = float3(0.105, 0.170, 0.135);
  float3 b = float3(0.080, 0.140, 0.108);
  float3 c0 = float3(0.050, 0.090, 0.070);
  float3 c = mix(a, b, smoothstep(0.0, 0.45, uv.y));
  c = mix(c, c0, smoothstep(0.45, 1.0, uv.y));
  float2 g = (uv - float2(0.5, 0.4)) * float2(1.0, size.y / size.x);
  c += float3(0.05, 0.06, 0.04) * exp(-dot(g, g) * 4.0);
  float2 v = uv - 0.5;
  c *= 1.0 - 0.4 * smoothstep(0.3, 0.9, length(v * float2(1.15, 0.9)));
  c += (h2(floor(p)) - 0.5) * 0.04;
  return half4(half3(c), 1.0);
}
"""
