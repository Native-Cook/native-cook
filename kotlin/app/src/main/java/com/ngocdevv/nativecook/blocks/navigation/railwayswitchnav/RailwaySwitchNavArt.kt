package com.ngocdevv.nativecook.blocks.navigation.railwayswitchnav

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The signal box in android.graphics, a port of railwayArt.ts (Skia). Everything is in dp: the
// caller scales the canvas by the density first.

private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
private fun hex(c: Long): Int = c.toInt()
private fun blur(sigma: Float) = BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

internal const val MAP_MIN = 150f
internal const val FRAME_H = 172f
/** A car's length and the gap between cars, before the diagram's scale k. */
private const val CAR = 20f
private const val GAP = 2f

/** Geometry in dp: the track diagram panel and the lever frame under it. */
internal class RailGeo(val w: Float, val h: Float, mapTop: Float, mapH: Float) {
    val px = 16f
    val pw = w - 32f
    val py = mapTop
    val ph = mapH
    /** Sidings (one per section, above its lever). */
    val xs = FloatArray(COUNT) { px + pw * (it + 0.5f) / COUNT }
    val frame = mapTop + mapH + 12f
    /** Size of the train, switches and signals (1 on a 150 dp diagram, up to 1.4 on a taller one). */
    val k = max(1f, min(1.4f, mapH / MAP_MIN))
    val top = mapTop + 26f + 4f * k
    val trunk = mapTop + mapH - 32f
    val rc = 20f * k
    val plat = (trunk - rc - top) * 0.6f
    val pivot = frame + 124f
    val lever = 92f

    fun routeLength(from: Int, to: Int): Float {
        val a = trunk - rc - top
        return 2f * a + PI.toFloat() * rc + abs(xs[to] - xs[from]) - 2f * rc
    }

    /** A point on the route from one platform to another: down, round onto the trunk, along, round and up. */
    fun routePoint(from: Int, to: Int, s: Float, out: FloatArray): FloatArray {
        val dir = if (to > from) 1f else -1f
        val a = trunk - rc - top
        val arc = PI.toFloat() * rc / 2f
        val xf = xs[from]
        val xt = xs[to]
        val run = abs(xt - xf) - 2f * rc
        val yc = trunk - rc
        var d = max(0f, s)
        if (d <= a) return out.set2(xf, top + d)
        d -= a
        if (d <= arc) {
            val f = d / rc
            return out.set2(xf + dir * rc - dir * rc * cos(f), yc + rc * sin(f))
        }
        d -= arc
        if (d <= run) return out.set2(xf + dir * (rc + d), trunk)
        d -= run
        if (d <= arc) {
            val f = d / rc
            return out.set2(xt - dir * rc + dir * rc * sin(f), yc + rc * cos(f))
        }
        d -= arc
        return out.set2(xt, max(top, yc - d))
    }
}

private fun FloatArray.set2(x: Float, y: Float): FloatArray {
    this[0] = x
    this[1] = y
    return this
}

/** A tile of film grain (white or black by a hashed value), to stand in for Skia's overlay of fractal noise. */
internal fun railGrainTile(seed: Int): Bitmap {
    val n = 96
    val px = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            var h = x * 374761393 + y * 668265263 + seed * 144269
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val v = (h and 0xFFFF) / 65535f
            val a = (abs(v - 0.5f) * 2f * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (a shl 24) or (if (v > 0.5f) 0xFFFFFF else 0)
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

private fun screw(canvas: Canvas, x: Float, y: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(x - 1, y - 1, 4f, intArrayOf(hex(0xFFD9C38E), hex(0xFF8A6B33), hex(0xFF4A3816)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(x, y, 3.2f, p)
    val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.9f
        color = argb(40, 28, 10, 0.8f)
    }
    canvas.drawLine(x - 2, y + 1.2f, x + 2, y - 1.2f, slot)
}

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

/**
 * The enamel diagram panel with every track drawn dim, and the wooden lever frame. Recorded once
 * per geometry; [density] sizes the grain to device pixels, [grainA]/[grainB] are the two tiles.
 */
internal fun railRecordStatic(g: RailGeo, density: Float, grainA: Bitmap, grainB: Bitmap): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(max(1, g.w.roundToInt()), max(1, g.h.roundToInt()))

    // Diagram panel: two shadows, a painted face with a lit top edge, grain.
    val panel = rr(g.px, g.py, g.pw, g.ph)
    if (canBlur) {
        val amb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(4, 8, 7, 0.45f)
            maskFilter = blur(14f)
        }
        canvas.drawRoundRect(rr(g.px + 2, g.py + 12, g.pw - 4, g.ph), 18f, 18f, amb)
    }
    val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, g.py, 0f, g.py + g.ph, intArrayOf(hex(0xFF2A3D37), hex(0xFF22332E), hex(0xFF1B2925)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(panel, 18f, 18f, face)
    val grain = Paint().apply {
        shader = BitmapShader(grainA, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / density, 1f / density) })
        }
        alpha = (0.07f * 0.6f * 255f).roundToInt()
    }
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(panel, 18f, 18f, Path.Direction.CW) })
    canvas.drawRect(panel, grain)
    canvas.restore()
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = argb(214, 236, 222, 0.16f)
    }
    canvas.drawRoundRect(rr(g.px + 0.5f, g.py + 0.5f, g.pw - 1, g.ph - 1), 17.5f, 17.5f, rim)
    screw(canvas, g.px + 12, g.py + 12)
    screw(canvas, g.px + g.pw - 12, g.py + 12)
    screw(canvas, g.px + 12, g.py + g.ph - 12)
    screw(canvas, g.px + g.pw - 12, g.py + g.ph - 12)

    // Tracks: the trunk, each siding and its two curves (a wye), dim cream enamel.
    val track = Path()
    track.moveTo(g.px + 24, g.trunk)
    track.lineTo(g.px + g.pw - 24, g.trunk)
    val yc = g.trunk - g.rc
    for (i in 0 until COUNT) {
        val x = g.xs[i]
        track.moveTo(x, g.top)
        track.lineTo(x, yc)
        track.arcTo(rr(x - 2 * g.rc, yc - g.rc, 2 * g.rc, 2 * g.rc), 0f, 90f, true)
        track.arcTo(rr(x, yc - g.rc, 2 * g.rc, 2 * g.rc), 180f, -90f, true)
    }
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * g.k
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = argb(232, 224, 200, 0.26f)
    }
    canvas.drawPath(track, tp)
    // Insulated joints between track circuits and buffer stops at the platforms.
    val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.2f
        color = argb(20, 30, 27, 0.95f)
    }
    for (i in 0 until COUNT - 1) {
        val x = (g.xs[i] + g.xs[i + 1]) / 2
        canvas.drawLine(x, g.trunk - 3, x, g.trunk + 3, joint)
    }
    val stop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = argb(232, 224, 200, 0.45f)
    }
    for (i in 0 until COUNT) canvas.drawLine(g.xs[i] - 6, g.top - 4, g.xs[i] + 6, g.top - 4, stop)
    // Platforms beside the sidings.
    val plat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(232, 224, 200, 0.13f) }
    val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        color = argb(255, 214, 120, 0.35f)
    }
    for (i in 0 until COUNT) {
        canvas.drawRoundRect(rr(g.xs[i] - 9 - 8 * g.k, g.top + 4, 8 * g.k, g.plat), 2f, 2f, plat)
        canvas.drawLine(g.xs[i] - 8, g.top + 6, g.xs[i] - 8, g.top + 2 + g.plat, edgeP)
    }

    // Lever frame: a mahogany plinth with a brass rail, one dark slot per lever.
    val frameTop = g.frame
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, frameTop, 0f, g.h, intArrayOf(hex(0xFF5A2E1E), hex(0xFF47231A), hex(0xFF2C1610)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    val frameR = rr(0f, frameTop, g.w, g.h - frameTop + 30)
    if (canBlur) {
        val fs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(0, 0, 0, 0.45f)
            maskFilter = blur(12f)
        }
        canvas.drawRoundRect(rr(0f, frameTop - 6, g.w, 40f), 26f, 26f, fs)
    }
    canvas.drawRoundRect(frameR, 26f, 26f, body)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(frameR, 26f, 26f, Path.Direction.CW) })
    val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    for (i in 0 until 14) {
        val y = frameTop + 8 + i * 13
        gp.color = argb(20, 8, 4, 0.18f + 0.1f * sin(i * 2.1f))
        val b = Path()
        b.moveTo(0f, y)
        b.cubicTo(g.w * 0.3f, y + 4 * sin(i.toFloat()), g.w * 0.6f, y - 3 * cos(i * 1.3f), g.w, y + 2 * sin(i * 0.7f))
        canvas.drawPath(b, gp)
    }
    // Long streaks of grain, the Skia fractal noise's 0.7 × 0.05 base frequency.
    val fgrain = Paint().apply {
        shader = BitmapShader(grainB, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1.4f / density, 14f / density) })
        }
        alpha = (0.08f * 0.6f * 255f).roundToInt()
    }
    canvas.drawRect(0f, frameTop, g.w, g.h, fgrain)
    canvas.restore()
    val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        color = argb(255, 210, 170, 0.28f)
    }
    canvas.drawLine(24f, frameTop + 0.8f, g.w - 24, frameTop + 0.8f, lip)
    // Brass rail behind the levers.
    val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, frameTop + 40, 0f, frameTop + 48, intArrayOf(hex(0xFFF0D79A), hex(0xFFB08A42), hex(0xFF6E5220)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(rr(16f, frameTop + 40, g.w - 32, 8f), 4f, 4f, rail)
    for (i in 0 until COUNT) {
        val x = g.xs[i]
        val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x - 9, 0f, x + 9, 0f, intArrayOf(hex(0xFF0E0605), hex(0xFF1E0F0B), hex(0xFF0E0605)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rr(x - 9, frameTop + 30, 18f, g.pivot - frameTop - 22), 9f, 9f, slot)
        // The brass name plate under the lever (its text is Compose text).
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, g.pivot + 12, 0f, g.pivot + 36, intArrayOf(hex(0xFFEAD08F), hex(0xFFC29C52), hex(0xFF8A6A2E)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rr(x - 36, g.pivot + 12, 72f, 24f), 6f, 6f, plate)
        val pe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(70, 48, 14, 0.7f)
        }
        canvas.drawRoundRect(rr(x - 35.5f, g.pivot + 12.5f, 71f, 23f), 5.5f, 5.5f, pe)
        // Lamp bezel above the slot.
        val bez = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - 2, frameTop + 16, 10f, intArrayOf(hex(0xFFF3DDA2), hex(0xFF9C7A38), hex(0xFF4E3A14)), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(x, frameTop + 18, 8.5f, bez)
    }
    picture.endRecording()
    return picture
}

/** Lever colours, as in a real frame: signals red, routes blue, points black, the last yellow. */
private val LeverPaint = intArrayOf(hex(0xFFB5352B), hex(0xFF2D5E9E), hex(0xFF2A2D31), hex(0xFFC9961F))
private val LeverPaintDark = intArrayOf(hex(0xFF5E1712), hex(0xFF132B4D), hex(0xFF0E0F11), hex(0xFF6B4C09))

/** Everything that moves: route light, switch blades, signals, the train, lamps and levers. */
internal class RailPainter(private val g: RailGeo, density: Float) {
    private val pt = FloatArray(2)
    private val pt2 = FloatArray(2)
    private val route = Path()
    private val rect = RectF()
    private val rod = Path()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f * g.k
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // Skia's blur here does not follow the canvas scale: 4 device pixels.
        if (canBlur) maskFilter = blur(max(0.6f, 4f / density))
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * g.k
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.6f * g.k
        strokeCap = Paint.Cap.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = android.graphics.BlendMode.PLUS
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(0, 0, 0, 0.45f)
        if (canBlur) maskFilter = blur(2.2f)
    }
    private val carBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -4.6f, 0f, 4.6f, intArrayOf(hex(0xFFFFF1C9), hex(0xFFF2C14E), hex(0xFFB9861E)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val headGlow = Paint(plus).apply {
        shader = RadialGradient(CAR / 2 + 2, 0f, 12f, intArrayOf(argb(255, 244, 200, 0.55f), 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val rodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(4f, 0f, 8f, 0f, intArrayOf(hex(0xFF6F757C), hex(0xFFE8ECEF), hex(0xFF5A6066)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-4f, 0f, 4f, 0f, intArrayOf(hex(0xFF4F555B), hex(0xFFC9CFD4), hex(0xFFFFFFFF), hex(0xFF9BA2A8), hex(0xFF3E4348)), floatArrayOf(0f, 0.3f, 0.42f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val collar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-5f, 0f, 5f, 0f, intArrayOf(hex(0xFF6E5220), hex(0xFFF0D79A), hex(0xFF6E5220)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    private fun lamp(canvas: Canvas, x: Float, y: Float, r: Float, green: Float) {
        val gr = railClamp01(green)
        val red = 1f - gr
        val c1 = argb((255 * red + 150 * gr).roundToInt(), (90 * red + 255 * gr).roundToInt(), (70 * red + 150 * gr).roundToInt(), 1f)
        val c2 = argb((150 * red + 20 * gr).roundToInt(), (20 * red + 140 * gr).roundToInt(), (16 * red + 70 * gr).roundToInt(), 1f)
        core.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(hex(0xFFFFFBEA), c1, c2), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, core)
        val gc = if (red > 0.5f) argb(255, 80, 50, 0.35f) else argb(110, 255, 150, 0.35f)
        plus.shader = RadialGradient(x, y, r * 4f, intArrayOf(gc, 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 4f, plus)
    }

    private fun drawCar(canvas: Canvas, from: Int, to: Int, s: Float, lead: Boolean, tail: Boolean) {
        val a = g.routePoint(from, to, s, pt)
        val b = g.routePoint(from, to, s - (CAR - 2f) * g.k, pt2)
        val cx = (a[0] + b[0]) / 2f
        val cy = (a[1] + b[1]) / 2f
        val ang = (atan2(a[1] - b[1], a[0] - b[0]) * 180.0 / PI).toFloat()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(ang)
        canvas.scale(g.k, g.k)
        // Along +x is forward. Body, roof stripe, windows, light at the leading/trailing end.
        if (canBlur) {
            rect.set(-CAR / 2 + 1, -4.6f + 1.5f, CAR / 2 + 1, 4.6f + 1.5f)
            canvas.drawRoundRect(rect, 3.5f, 3.5f, carShadow)
        }
        rect.set(-CAR / 2, -4.6f, CAR / 2, 4.6f)
        canvas.drawRoundRect(rect, 3.5f, 3.5f, carBody)
        solid.color = hex(0xFF2B3A36)
        canvas.drawRect(-CAR / 2 + 3, -1.2f, CAR / 2 - 3, 1.2f, solid)
        solid.color = argb(210, 240, 236, 0.9f)
        for (i in 0 until 4) canvas.drawRect(-CAR / 2 + 4 + i * 3.4f, -0.7f, -CAR / 2 + 4 + i * 3.4f + 2.2f, 0.7f, solid)
        if (lead) {
            canvas.drawCircle(CAR / 2 + 2, 0f, 12f, headGlow)
            solid.color = hex(0xFFFFFDF2)
            canvas.drawCircle(CAR / 2 - 0.8f, -2.6f, 0.9f, solid)
            canvas.drawCircle(CAR / 2 - 0.8f, 2.6f, 0.9f, solid)
        }
        if (tail) {
            solid.color = hex(0xFFFF4A36)
            canvas.drawCircle(-CAR / 2 + 0.8f, -2.6f, 0.9f, solid)
            canvas.drawCircle(-CAR / 2 + 0.8f, 2.6f, 0.9f, solid)
        }
        canvas.restore()
    }

    private fun drawLever(canvas: Canvas, k: Int, pull: Float) {
        val x = g.xs[k]
        val p = max(-0.1f, min(1.1f, pull))
        // Pulled toward the viewer: the visible length shortens and the handle comes closer (larger).
        val len = g.lever * (1f - 0.42f * p)
        val topY = g.pivot - len
        val sc = 1f + 0.14f * p
        if (canBlur) {
            shadow.color = argb(0, 0, 0, 0.35f + 0.15f * p)
            shadow.maskFilter = blur(3f + 4f * p)
            rect.set(x - 4 + 2 + 3 * p, topY + 6 + 4 * p, x - 4 + 2 + 3 * p + 8, topY + 6 + 4 * p + len)
            canvas.drawRoundRect(rect, 4f, 4f, shadow)
        }
        // Painted rod, tapering toward the top, lit more as it turns to the light.
        rod.reset()
        rod.moveTo(x - 5, g.pivot)
        rod.lineTo(x - 3.4f, topY + 22)
        rod.lineTo(x + 3.4f, topY + 22)
        rod.lineTo(x + 5, g.pivot)
        rod.close()
        rodPaint.shader = LinearGradient(
            x - 5, 0f, x + 5, 0f,
            intArrayOf(LeverPaintDark[k], LeverPaint[k], argb(255, 245, 230, 0.35f + 0.25f * p), LeverPaint[k], LeverPaintDark[k]),
            floatArrayOf(0f, 0.3f, 0.42f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(rod, rodPaint)
        // Polished steel handle and the catch handle behind it.
        canvas.save()
        canvas.translate(x, topY + 22)
        canvas.scale(sc, sc)
        rect.set(4.2f, -22f, 7.6f, -4f)
        canvas.drawRoundRect(rect, 1.7f, 1.7f, catchPaint)
        canvas.drawRect(2f, -6f, 6f, -3.6f, catchPaint)
        rect.set(-4f, -26f, 4f, 2f)
        canvas.drawRoundRect(rect, 4f, 4f, grip)
        rect.set(-5f, 0f, 5f, 4f)
        canvas.drawRoundRect(rect, 1.5f, 1.5f, collar)
        canvas.restore()
    }

    fun draw(canvas: Canvas, p: RailPose) {
        if (p.route > 0.002f && p.from != p.to) {
            route.reset()
            val l = g.routeLength(p.from, p.to)
            val n = 48
            for (i in 0..n) {
                val q = g.routePoint(p.from, p.to, l * i / n, pt)
                if (i == 0) route.moveTo(q[0], q[1]) else route.lineTo(q[0], q[1])
            }
            glow.color = argb(255, 214, 120, 0.32f * p.route)
            canvas.drawPath(route, glow)
            line.color = argb(255, 236, 184, 0.95f * p.route)
            canvas.drawPath(route, line)
        }
        // Switch blades at the foot of each siding, and the point motor's lamp.
        val yc = g.trunk - g.rc
        for (k in 0 until COUNT) {
            val b = p.blades[k]
            val ang = b * 34f * PI.toFloat() / 180f
            val x0 = g.xs[k]
            val y0 = yc - 10f * g.k
            val set = abs(b)
            blade.color = argb((150 + 105 * set).roundToInt(), (150 + 61 * set).roundToInt(), (140 - 40 * set).roundToInt(), 0.7f + 0.3f * set)
            canvas.drawLine(x0, y0, x0 + sin(ang) * 17f * g.k, y0 + cos(ang) * 17f * g.k, blade)
            solid.color = hex(0xFFE8E0C8)
            canvas.drawCircle(x0, y0, 2.2f * g.k, solid)
            lamp(canvas, x0 - 14f * g.k, yc - 4f, 2.2f * g.k, if (set > 0.5f) 1f else 0f)
        }
        // Platform signals.
        for (k in 0 until COUNT) {
            solid.color = hex(0xFF0F1715)
            rect.set(g.xs[k] + 6 + 3 * g.k, g.top - 2, g.xs[k] + 6 + 3 * g.k + 9 * g.k, g.top - 2 + 14 * g.k)
            canvas.drawRoundRect(rect, 4.5f * g.k, 4.5f * g.k, solid)
            lamp(canvas, g.xs[k] + 6 + 7.5f * g.k, g.top - 2 + 7 * g.k, 3f * g.k, p.signals[k])
        }
        // The train: two cars, headlights ahead, tail lights behind.
        val car = CAR * g.k
        val gap = GAP * g.k
        val span = 2 * car + gap
        if (p.from == p.to) {
            // At rest on a platform: reuse a route that ends there.
            val other = if (p.to == 0) 1 else 0
            val lr = g.routeLength(other, p.to)
            drawCar(canvas, other, p.to, lr, lead = true, tail = false)
            drawCar(canvas, other, p.to, lr - car - gap, lead = false, tail = true)
        } else {
            val l = g.routeLength(p.from, p.to)
            val lead = span + (l - span) * p.run
            drawCar(canvas, p.from, p.to, lead, lead = true, tail = false)
            drawCar(canvas, p.from, p.to, lead - car - gap, lead = false, tail = true)
        }
        // Lever-frame lamps (repeaters) and the levers.
        for (k in 0 until COUNT) {
            lamp(canvas, g.xs[k], g.frame + 18, 5f, p.signals[k])
            drawLever(canvas, k, p.levers[k])
        }
    }
}
