package com.ngocdevv.nativecook.blocks.onboarding.constellationstepswelcome

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Constellation Steps Welcome — the constellations, their lines, stars and labels, as in
// constellationArt.ts, and the sky drawn without a shader below Android 13. In dp.

private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
private fun blur(sigma: Float) = BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/** Faint line art behind each constellation: the Swan, the Queen's throne and crown, the Hunter. */
private fun buildOutlines(): List<Path> {
    val swan = Path()
    // Tail fan, body, neck, head and beak.
    swan.moveTo(0f, -112f); swan.lineTo(-14f, -134f)
    swan.moveTo(0f, -112f); swan.lineTo(0f, -140f)
    swan.moveTo(0f, -112f); swan.lineTo(14f, -134f)
    swan.moveTo(-12f, -100f)
    swan.cubicTo(-20f, -60f, -18f, -10f, -5f, 40f)
    swan.cubicTo(-2f, 62f, 4f, 88f, 6f, 102f)
    swan.moveTo(12f, -100f)
    swan.cubicTo(22f, -62f, 20f, -12f, 11f, 40f)
    swan.cubicTo(10f, 62f, 14f, 88f, 18f, 102f)
    swan.addCircle(12f, 110f, 7f, Path.Direction.CW)
    swan.moveTo(15f, 117f); swan.lineTo(19f, 132f)
    // Wings: leading edge to the tip, trailing edge back, with a few feathers.
    swan.moveTo(-15f, -32f)
    swan.cubicTo(-52f, -64f, -102f, -44f, -132f, -6f)
    swan.cubicTo(-98f, 12f, -52f, 10f, -16f, 2f)
    swan.moveTo(16f, -40f)
    swan.cubicTo(54f, -74f, 98f, -62f, 126f, -28f)
    swan.cubicTo(96f, -8f, 54f, -6f, 18f, -6f)
    for (i in 0 until 4) {
        val x = -34f - i * 22f
        swan.moveTo(x, 8f - i * 1.5f)
        swan.lineTo(x - 8f, 20f - i * 3f)
        val xr = 36f + i * 22f
        swan.moveTo(xr, -6f - i * 3f)
        swan.lineTo(xr + 8f, 6f - i * 5f)
    }

    val throne = Path()
    throne.moveTo(-62f, 44f)
    throne.lineTo(-62f, -52f)
    throne.cubicTo(-62f, -86f, 62f, -86f, 62f, -52f)
    throne.lineTo(62f, 44f)
    throne.moveTo(-86f, 44f); throne.lineTo(86f, 44f)
    throne.moveTo(-86f, 54f); throne.lineTo(86f, 54f)
    throne.moveTo(-78f, 54f); throne.lineTo(-82f, 104f)
    throne.moveTo(78f, 54f); throne.lineTo(82f, 104f)
    throne.moveTo(-86f, 44f); throne.lineTo(-86f, 10f); throne.lineTo(-62f, 10f)
    throne.moveTo(86f, 44f); throne.lineTo(86f, 10f); throne.lineTo(62f, 10f)
    // The crown above.
    throne.moveTo(-30f, -96f)
    throne.lineTo(-30f, -112f)
    throne.lineTo(-16f, -102f)
    throne.lineTo(0f, -122f)
    throne.lineTo(16f, -102f)
    throne.lineTo(30f, -112f)
    throne.lineTo(30f, -96f)
    throne.close()
    throne.addCircle(0f, -128f, 3f, Path.Direction.CW)

    val hunter = Path()
    hunter.addCircle(0f, -124f, 11f, Path.Direction.CW)
    hunter.moveTo(-62f, -86f)
    hunter.cubicTo(-20f, -96f, 20f, -92f, 56f, -78f)
    // Raised arm with a club.
    hunter.moveTo(-62f, -86f)
    hunter.cubicTo(-82f, -104f, -92f, -126f, -84f, -150f)
    hunter.addOval(RectF(-96f, -186f, -74f, -146f), Path.Direction.CW)
    // Shield held out on the right.
    hunter.moveTo(72f, -118f)
    hunter.cubicTo(104f, -104f, 104f, -52f, 72f, -38f)
    hunter.moveTo(56f, -78f); hunter.lineTo(72f, -80f)
    // Torso, tunic and legs.
    hunter.moveTo(-58f, -80f)
    hunter.cubicTo(-40f, -40f, -30f, -10f, -24f, 2f)
    hunter.moveTo(52f, -72f)
    hunter.cubicTo(36f, -40f, 28f, -20f, 24f, -14f)
    hunter.moveTo(-24f, 2f)
    hunter.cubicTo(-44f, 30f, -54f, 44f, -58f, 54f)
    hunter.lineTo(64f, 50f)
    hunter.cubicTo(52f, 30f, 36f, 6f, 24f, -14f)
    hunter.moveTo(-30f, 54f); hunter.lineTo(-46f, 98f); hunter.lineTo(-58f, 112f)
    hunter.moveTo(34f, 52f); hunter.lineTo(62f, 104f); hunter.lineTo(76f, 114f)
    hunter.moveTo(0f, -4f); hunter.lineTo(5f, 44f)

    return listOf(swan, throne, hunter)
}

/** Draws the constellations; [density] turns Skia's device-pixel blur into local units. */
internal class ConstellationPainter(private val g: SkyGeo, private val density: Float) {
    private val outlines = buildOutlines()
    private val lines = Path()
    private val lit = FloatArray(8)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f / g.scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f / g.scale
        strokeCap = Paint.Cap.ROUND
        // Skia's blur here does not follow the canvas: 3 device pixels.
        if (canBlur) maskFilter = blur(max(0.6f, 3f / (density * g.scale)))
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f / g.scale
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.8f / g.scale
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
    }

    private fun drawOne(canvas: Canvas, k: Int, p: SkyPose) {
        val c = Constellations[k]
        val d = p.drawn[k]
        val fade = p.fade
        val n = c.edges.size
        lit.fill(0f)
        canvas.save()
        canvas.rotate(((p.theta - k * StepAngle) * 180f / PI).toFloat(), g.px, g.py)
        canvas.translate(g.cx, g.cy)
        canvas.scale(g.scale, g.scale)

        // Line art, fading in once the lines are mostly drawn.
        val art = 0.26f * skyClamp01((d - 0.55f) / 0.45f) * fade
        if (art > 0.005f) {
            outlinePaint.color = argb(196, 214, 255, art)
            canvas.drawPath(outlines[k], outlinePaint)
        }

        // The lines, star to star: a soft glow under a bright core, each drawn on in turn.
        if (d > 0.001f && fade > 0.005f) {
            lines.reset()
            for (i in 0 until n) {
                val e = skyClamp01(d * n - i)
                if (e <= 0f) continue
                val a = c.stars[c.edges[i][0]]
                val b = c.stars[c.edges[i][1]]
                lines.moveTo(a[0], a[1])
                lines.lineTo(a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e)
                lit[c.edges[i][0]] = max(lit[c.edges[i][0]], skyClamp01(e * 5f))
                lit[c.edges[i][1]] = max(lit[c.edges[i][1]], skyClamp01((e - 0.85f) / 0.15f))
            }
            glow.color = argb(255, 214, 140, 0.32f * fade)
            canvas.drawPath(lines, glow)
            core.color = argb(255, 238, 200, 0.92f * fade)
            canvas.drawPath(lines, core)
        }

        // Stars: always a little brighter than the field; lit ones bloom.
        val w = 2f * PI.toFloat() / LOOP
        for (s in c.stars.indices) {
            val st = c.stars[s]
            val l = lit[s] * fade
            val tw = 0.85f + 0.15f * sin(w * (3 + s % 3) * p.time + s * 1.7f + k)
            val r = st[2] * (1f + 0.35f * l)
            val hr = r * (3.5f + 3f * l)
            halo.shader = RadialGradient(st[0], st[1], hr, intArrayOf(argb(255, 236, 200, (0.34f + 0.34f * l) * tw), argb(255, 220, 170, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(st[0], st[1], hr, halo)
            dot.color = argb(255, 250, 238, (0.82f + 0.18f * l) * tw)
            canvas.drawCircle(st[0], st[1], r * (0.72f - 0.1f * l), dot)
            if (l > 0.02f && st[2] > 2.3f) {
                // A short four-point sparkle on the bright ones.
                sparkle.color = argb(255, 244, 220, 0.55f * l)
                val len = r * 3.2f
                canvas.drawLine(st[0] - len, st[1], st[0] + len, st[1], sparkle)
                canvas.drawLine(st[0], st[1] - len, st[0], st[1] + len, sparkle)
            }
        }
        val a = lit[c.alpha] * fade
        if (a > 0.01f) {
            val st = c.stars[c.alpha]
            text.color = argb(242, 222, 180, 0.85f * a)
            canvas.drawText(c.label, st[0] + 10f, st[1] + 4f, text)
        }
        canvas.restore()
    }

    /** Every constellation that can be on screen at this turn of the sky. */
    fun draw(canvas: Canvas, p: SkyPose) {
        for (k in Constellations.indices) {
            if (abs(p.theta - k * StepAngle) > StepAngle * 1.2f) continue
            drawOne(canvas, k, p)
        }
    }
}

// The shader's hash, in floats like the GPU (for the fallback's stars).
private fun fract(x: Float) = x - floor(x)

private fun hash(px: Float, py: Float): Float {
    var x = fract(px * 127.31f)
    var y = fract(py * 311.71f)
    val dd = x * (x + 34.17f) + y * (y + 34.17f)
    x += dd
    y += dd
    return fract(x * y)
}

/**
 * Below Android 13 (no AGSL): the screen gradient and vignette, a soft Milky Way band and the two
 * star layers of the shader's hashed grid, worked out once on the CPU and drawn as points that turn
 * with the sky. The stars do not twinkle and the band has no dust or rift.
 */
internal class ConstellationSkyFallback(private val g: SkyGeo) {
    private val background = Paint()
    private val vignette = Paint()
    private val band = Paint()
    private val points = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    /** Stars by size (3) and brightness (4): flat x, y arrays in sky coordinates. */
    private val buckets: Array<FloatArray>

    init {
        background.shader = LinearGradient(
            0f, 0f, 0f, g.h,
            intArrayOf(argb(6, 8, 23, 1f), argb(8, 12, 32, 1f), argb(11, 17, 41, 1f), argb(18, 22, 50, 1f), argb(35, 27, 56, 1f)),
            floatArrayOf(0f, 0.375f, 0.7f, 0.85f, 1f),
            Shader.TileMode.CLAMP,
        )
        vignette.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0, 0, argb(0, 0, 0, 0.28f), argb(0, 0, 0, 0.55f)), floatArrayOf(0f, 0.32f, 0.56f, 0.8f), Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(g.w, g.h * 1.25f); postTranslate(g.w * 0.5f, g.h * 0.42f) })
        }
        val lists = Array(12) { ArrayList<Float>() }
        val reach = sqrt(g.w * g.w / 4f + (g.h - g.py) * (g.h - g.py)) + 40f
        val bdx = 0.62f / sqrt(0.62f * 0.62f + 0.78f * 0.78f)
        val bdy = 0.78f / sqrt(0.62f * 0.62f + 0.78f * 0.78f)
        fun layer(size: Float, density: Float, ox: Float, oy: Float, bandOnly: Boolean) {
            val n = (reach / size).toInt() + 1
            for (j in -n..n) {
                for (i in -n..n) {
                    val cx = i.toFloat()
                    val cy = j.toFloat()
                    if (hash(cx + 1.13f, cy + 1.13f) < 1f - density) continue
                    val x = (cx + 0.22f + 0.56f * hash(cx + 2.71f, cy + 2.71f)) * size - ox
                    val y = (cy + 0.22f + 0.56f * hash(cx + 3.97f, cy + 3.97f)) * size - oy
                    if (x * x + y * y > reach * reach) continue
                    var gain = 1f
                    if (bandOnly) {
                        val dist = (x + 30f) * -bdy + (y - 470f) * bdx
                        val b = exp(-(dist * dist) / (118f * 118f))
                        gain = 0.55f * b + 0.08f
                        if (gain < 0.2f) continue
                    }
                    val mag = hash(cx + 7.31f, cy + 7.31f).pow(5)
                    val light = (0.35f + 1.85f * mag) * 0.86f * gain
                    val rad = 0.45f + 1.1f * mag
                    val si = if (rad < 0.7f) 0 else if (rad < 1.1f) 1 else 2
                    val bi = if (light < 0.2f) 0 else if (light < 0.4f) 1 else if (light < 0.8f) 2 else 3
                    val list = lists[si * 4 + bi]
                    list.add(x)
                    list.add(y)
                }
            }
        }
        layer(21f, 0.62f, 0f, 0f, bandOnly = false)
        layer(8.5f, 0.5f, 7.3f, 3.1f, bandOnly = true)
        buckets = Array(12) { lists[it].toFloatArray() }
    }

    fun draw(canvas: Canvas, theta: Float) {
        canvas.drawRect(0f, 0f, g.w, g.h, background)
        canvas.save()
        canvas.translate(g.px, g.py)
        canvas.rotate((theta * 180f / PI).toFloat())
        // The Milky Way: a soft band along (0.62, 0.78) through (-30, 470).
        canvas.save()
        canvas.translate(-30f, 470f)
        canvas.rotate((kotlin.math.atan2(0.78f, 0.62f) * 180f / PI).toFloat())
        band.shader = LinearGradient(0f, -150f, 0f, 150f, intArrayOf(0, argb(87, 97, 148, 0.16f), argb(87, 97, 148, 0.22f), argb(87, 97, 148, 0.16f), 0), floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-2400f, -150f, 2400f, 150f, band)
        canvas.restore()
        val sizes = floatArrayOf(1.0f, 1.6f, 2.6f)
        val alphas = floatArrayOf(0.14f, 0.3f, 0.6f, 0.95f)
        for (si in 0 until 3) {
            for (bi in 0 until 4) {
                val pts = buckets[si * 4 + bi]
                if (pts.isEmpty()) continue
                points.strokeWidth = sizes[si]
                points.color = argb(222, 230, 255, alphas[bi])
                canvas.drawPoints(pts, points)
            }
        }
        canvas.restore()
        canvas.drawRect(0f, 0f, g.w, g.h, vignette)
    }
}
