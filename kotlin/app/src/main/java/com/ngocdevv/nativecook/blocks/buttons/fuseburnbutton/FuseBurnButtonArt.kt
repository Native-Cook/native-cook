package com.ngocdevv.nativecook.blocks.buttons.fuseburnbutton

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Drawing helpers. The backdrop, the poster and the capsule's base are baked once into a bitmap;
 the fuse, its sparks and its smoke are rebuilt each frame into a handful of reused paths (never
 one node per particle).
 */

internal class FuseRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class FuseLayout(
    val width: Float,
    val height: Float,
    val headerTop: Float,
    val poster: FuseRect,
    val rowsTop: Float,
    val capsule: FuseRect,
    val hintTop: Float,
)

private const val HEADER = 86f
private const val ROWS = 76f
private const val CAPSULE_H = 60f

internal fun fuseLayout(width: Float, height: Float, top: Float, bottom: Float): FuseLayout {
    val headerTop = top + 14f
    val hintTop = height - max(bottom, 14f) - 22f
    val capsuleY = hintTop - 18f - CAPSULE_H
    val w = min(width - 48f, 420f)
    val x = (width - w) / 2f
    val rowsTop = capsuleY - 30f - ROWS
    val posterY = headerTop + HEADER + 8f
    val posterH = max(150f, min(460f, rowsTop - 18f - posterY))
    return FuseLayout(width, height, headerTop, FuseRect(x, posterY, w, posterH), rowsTop, FuseRect(x, capsuleY, w, CAPSULE_H), hintTop)
}

private fun argb(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt().coerceIn(0, 255), r, g, b)

/**
 * Draws once into a bitmap `w` × `h` px: through a picture into a hardware bitmap on API 28+ (so
 * runtime shaders and blurs render exactly as on screen), into a plain bitmap below that.
 */
private fun fuseBake(w: Int, h: Int, draw: (Canvas) -> Unit): Bitmap? {
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

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
internal fun fuseSigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/** Everything that never moves: the backdrop, the poster with its shadow and rim, the capsule's shadows and base. */
internal fun fuseBakeStatic(l: FuseLayout, scale: Float): Bitmap? {
    val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            RuntimeShader(FUSE_BACKDROP_AGSL).apply {
                setFloatUniform("size", l.width, l.height)
                setFloatUniform("warm", l.width / 2f, l.capsule.y + l.capsule.h / 2f)
            }
        }.getOrNull()
    } else {
        null
    }
    return fuseBake(ceil(l.width * scale).toInt(), ceil(l.height * scale).toInt()) { c ->
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p.shader = shader as RuntimeShader
            c.drawRect(0f, 0f, l.width, l.height, p)
        } else {
            fuseBackdropFallback(c, l, p)
        }
        p.shader = null
        val poster = l.poster
        // The poster, with two shadows and a hairline of light on its top edge.
        p.color = argb(0, 0, 8, 0.55f)
        p.maskFilter = BlurMaskFilter(fuseSigmaToRadius(22f), BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(poster.x, poster.y + 14f, poster.x + poster.w, poster.y + poster.h + 4f, 24f, 24f, p)
        p.maskFilter = null
        fuseDrawPoster(c, poster)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.shader = LinearGradient(
            0f, poster.y, 0f, poster.y + poster.h,
            intArrayOf(argb(255, 255, 255, 0.22f), argb(255, 255, 255, 0.03f), argb(255, 255, 255, 0.06f)),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(poster.x + 0.5f, poster.y + 0.5f, poster.x + poster.w - 0.5f, poster.y + poster.h - 0.5f, 23.5f, 23.5f, p)
        p.shader = null
        p.style = Paint.Style.FILL

        val cap = l.capsule
        val r = cap.h / 2f
        p.color = argb(0, 0, 10, 0.6f)
        p.maskFilter = BlurMaskFilter(fuseSigmaToRadius(18f), BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(cap.x + 6f, cap.y + 14f, cap.x + cap.w - 6f, cap.y + 14f + cap.h, r, r, p)
        p.color = argb(0, 0, 8, 0.7f)
        p.maskFilter = BlurMaskFilter(fuseSigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(cap.x, cap.y + 1.5f, cap.x + cap.w, cap.y + 1.5f + cap.h, r, r, p)
        p.maskFilter = null
        p.color = 0xFF000000.toInt()
        p.shader = LinearGradient(
            0f, cap.y, 0f, cap.y + cap.h,
            intArrayOf(0xFF2D3768.toInt(), 0xFF20284D.toInt(), 0xFF171D3A.toInt()), null, Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(cap.x, cap.y, cap.x + cap.w, cap.y + cap.h, r, r, p)
    }
}

/** Below Android 13: the backdrop's gradient, warm pool and vignette from plain gradients. */
private fun fuseBackdropFallback(c: Canvas, l: FuseLayout, p: Paint) {
    p.shader = LinearGradient(
        0f, 0f, 0f, l.height,
        intArrayOf(0xFF131933.toInt(), 0xFF0D1226.toInt(), 0xFF090C1B.toInt()),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, l.width, l.height, p)
    c.save()
    val wy = l.capsule.y + l.capsule.h / 2f
    c.scale(1f, 120f / (l.width * 0.7f), l.width / 2f, wy)
    p.shader = RadialGradient(
        l.width / 2f, wy, l.width * 0.7f * 1.2f,
        intArrayOf(argb(23, 10, 5, 1f), argb(23, 10, 5, 0f)), null, Shader.TileMode.CLAMP,
    )
    p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    c.drawRect(-l.width, wy - l.width * 2f, l.width * 2f, wy + l.width * 2f, p)
    p.xfermode = null
    c.restore()
    p.shader = RadialGradient(
        l.width / 2f, l.height * 0.45f, max(l.width, l.height) * 0.75f,
        intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, l.width, l.height, p)
    p.shader = null
}

/** The event poster: a rooftop at dusk, string lights, a skyline. */
private fun fuseDrawPoster(c: Canvas, p: FuseRect) {
    c.save()
    val clip = Path().apply { addRoundRect(p.x, p.y, p.x + p.w, p.y + p.h, 24f, 24f, Path.Direction.CW) }
    c.clipPath(clip)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        p.x, p.y, p.x, p.y + p.h,
        intArrayOf(0xFF0B1030.toInt(), 0xFF1E1D55.toInt(), 0xFF5B2A6E.toInt(), 0xFFD8694A.toInt(), 0xFFF0A35E.toInt()),
        floatArrayOf(0f, 0.38f, 0.62f, 0.84f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint)
    // A warm glow on the horizon.
    paint.shader = RadialGradient(
        p.x + p.w * 0.55f, p.y + p.h * 0.86f, p.w * 0.6f,
        intArrayOf(argb(255, 170, 100, 0.55f), argb(255, 120, 80, 0.15f), argb(255, 120, 80, 0f)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint)
    paint.shader = null
    // Stars.
    for (i in 0 until 46) {
        val sx = p.x + fuseHash(i * 3.1) * p.w
        val sy = p.y + fuseHash(i * 7.7 + 2) * p.h * 0.5f
        paint.color = argb(220, 228, 255, 0.25f + fuseHash(i + 9) * 0.6f)
        c.drawCircle(sx, sy, 0.5f + fuseHash(i + 4) * 0.9f, paint)
    }
    // The moon with a halo.
    val mx = p.x + p.w * 0.78f
    val my = p.y + p.h * 0.2f
    paint.color = 0xFF000000.toInt()
    paint.shader = RadialGradient(mx, my, 60f, intArrayOf(argb(255, 236, 210, 0.35f), argb(255, 236, 210, 0f)), null, Shader.TileMode.CLAMP)
    c.drawCircle(mx, my, 60f, paint)
    paint.shader = RadialGradient(
        mx - 5f, my - 5f, 22f,
        intArrayOf(0xFFFFF4DF.toInt(), 0xFFF3DCB6.toInt(), 0xFFD9B98F.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawCircle(mx, my, 16f, paint)
    paint.shader = null
    // Skyline, two layers.
    val ground = p.y + p.h
    val win = Paint().apply { color = argb(255, 196, 120, 0.75f) }
    for (layer in 0 until 2) {
        paint.color = if (layer == 0) 0xFF3A2559.toInt() else 0xFF140F2B.toInt()
        var x = p.x - 6f
        var k = layer * 50
        while (x < p.x + p.w) {
            val bw = 18f + fuseHash(k) * 30f
            val bh = (if (layer == 0) 0.28f else 0.16f) * p.h + fuseHash(k + 1) * p.h * (if (layer == 0) 0.22f else 0.18f)
            c.drawRect(x, ground - bh, x + bw - 2f, ground, paint)
            if (layer == 1) {
                var wy = ground - bh + 6f
                while (wy < ground - 6f) {
                    var wx = x + 4f
                    while (wx < x + bw - 6f) {
                        if (fuseHash(wx * 0.37 + wy * 1.3) > 0.72f) c.drawRect(wx, wy, wx + 2f, wy + 3f, win)
                        wx += 6f
                    }
                    wy += 7f
                }
            }
            x += bw
            k += 2
        }
    }
    // The rooftop's string lights: a sagging wire with warm bulbs.
    val y0 = p.y + p.h * 0.34f
    val y1 = p.y + p.h * 0.3f
    val wire = Path().apply {
        moveTo(p.x - 4f, y0)
        quadTo(p.x + p.w * 0.5f, p.y + p.h * 0.52f, p.x + p.w + 4f, y1)
    }
    val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = argb(20, 16, 40, 0.9f)
    }
    c.drawPath(wire, wp)
    val bulbGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(255, 190, 110, 0.5f)
        maskFilter = BlurMaskFilter(fuseSigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL)
    }
    val bulb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE2A8.toInt() }
    for (i in 1 until 14) {
        val u = i / 14f
        val bx = (1 - u) * (1 - u) * (p.x - 4f) + 2 * u * (1 - u) * (p.x + p.w * 0.5f) + u * u * (p.x + p.w + 4f)
        val by = (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * (p.y + p.h * 0.52f) + u * u * y1 + 4f
        c.drawCircle(bx, by, 7f, bulbGlow)
        c.drawCircle(bx, by, 2.2f, bulb)
    }
    // Vignette inside the poster.
    paint.color = 0xFF000000.toInt()
    paint.shader = RadialGradient(
        p.x + p.w / 2f, p.y + p.h * 0.45f, max(p.w, p.h) * 0.75f,
        intArrayOf(argb(5, 6, 20, 0f), argb(5, 6, 20, 0.45f)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint)
    c.restore()
}

/** Small per-sample crinkle of the charcoal, so the burnt cord looks curled, not drawn. */
private fun crinkle(i: Int) = sin(i * 0.9f) * 0.9f + sin(i * 2.3f + 1f) * 0.5f

private fun addRange(b: Path, f: FuseLine, i0: Int, i1: Int, wobble: Boolean) {
    if (i1 - i0 < 1) return
    for (i in i0..i1) {
        val o = if (wobble) crinkle(i) else 0f
        val x = f.xs[i] + f.nx[i] * o
        val y = f.ys[i] + f.ny[i] * o
        if (i == i0) b.moveTo(x, y) else b.lineTo(x, y)
    }
}

private fun addTicks(b: Path, f: FuseLine, i0: Int, i1: Int) {
    var i = i0 - (i0 % 3)
    while (i <= i1) {
        if (i >= i0) {
            val nx = f.nx[i]
            val ny = f.ny[i]
            // Along the cord is the normal turned a quarter; a twist runs diagonally across it.
            val tx = -ny
            val ty = nx
            b.moveTo(f.xs[i] - nx * 2f - tx * 1.4f, f.ys[i] - ny * 2f - ty * 1.4f)
            b.lineTo(f.xs[i] + nx * 2f + tx * 1.4f, f.ys[i] + ny * 2f + ty * 1.4f)
        }
        i += 3
    }
}

/** The per-frame paths, reused. */
internal class FusePaths {
    val cord = Path()
    val ticks = Path()
    val charcoal = Path()
    val ember = Path()
    val hot = Path()
    val warm = Path()
    val smoke = Path()
    val at = FloatArray(4)
}

/** The cord for a burn at b with the start re-woven up to w. */
internal fun fuseCordPaths(out: FusePaths, f: FuseLine, b: Float, w: Float) {
    val last = FUSE_SAMPLES - 1
    val ib = (fuseClamp01(b) * last).roundToInt()
    val iw = (fuseClamp01(w) * last).roundToInt()
    out.cord.rewind()
    out.ticks.rewind()
    out.charcoal.rewind()
    out.ember.rewind()
    addRange(out.cord, f, ib, last, false)
    addTicks(out.ticks, f, ib, last)
    if (iw > 0) {
        addRange(out.cord, f, 0, min(iw, last), false)
        addTicks(out.ticks, f, 0, min(iw, ib))
    }
    addRange(out.charcoal, f, iw, ib, true)
    addRange(out.ember, f, max(iw, ib - 18), ib, true)
}

// Sparks thrown by the burning fuse: each is a pure function of the time, its seed and the
// spark's place, and lives LOOP / k s with a whole k, so the loop repeats exactly.
private const val SPARKS = 96
private const val GRAVITY = 300f

private fun frac(x: Float) = x - floor(x)

/** Hot and warm streaks of the sparks; a burst adds 30 radial ones from the capsule. */
internal fun fuseSparkPaths(out: FusePaths, f: FuseLine, pose: FusePose, cx: Float, cy: Float) {
    val hot = out.hot
    val warm = out.warm
    hot.rewind()
    warm.rewind()
    if (pose.spark > 0.01f) {
        fuseAt(f, pose.b, out.at)
        val sx = out.at[0]
        val sy = out.at[1]
        val nx = out.at[2]
        val ny = out.at[3]
        // Along the fuse (clockwise): the normal turned a quarter.
        val tx = -ny
        val ty = nx
        for (i in 0 until SPARKS) {
            if (fuseHash(i + 5.5) > pose.spark) continue
            val k = 12 + floor(fuseHash(i + 0.7) * 12f).toInt()
            val life = LOOP / k
            val phase = frac(pose.time / life + fuseHash(i + 2.1))
            val age = phase * life
            // Thrown outward and up, a little back along the fuse; emitted from where the spark was.
            val spread = (fuseHash(i + 3.3) - 0.5f) * 2.2f
            val speed = 50f + fuseHash(i + 8.8) * 110f
            val dirX = nx * cos(spread) - ny * sin(spread) - tx * 0.35f
            val dirY = ny * cos(spread) + nx * sin(spread) - ty * 0.35f - 0.45f
            val ex = sx - tx * pose.speed * age * 0.6f
            val ey = sy - ty * pose.speed * age * 0.6f
            val vx = dirX * speed
            val vy = dirY * speed
            val x = ex + vx * age
            val y = ey + vy * age + 0.5f * GRAVITY * age * age
            val len = 0.018f + (1f - phase) * 0.02f
            val target = if (phase < 0.45f) hot else warm
            target.moveTo(x, y)
            target.lineTo(x - vx * len, y - (vy + GRAVITY * age) * len)
        }
    }
    if (pose.burstAge >= 0f && pose.burstAge < 0.9f) {
        val a = pose.burstAge
        for (i in 0 until 30) {
            val ang = i / 30f * Math.PI.toFloat() * 2f + fuseHash(i + 40) * 0.2f
            val speed = 170f + fuseHash(i + 41) * 170f
            val travel = speed * (1f - exp(-3.2f * a)) / 3.2f
            val x = cx + cos(ang) * (travel + 20f) * 1.6f
            val y = cy + sin(ang) * (travel + 8f) + 60f * a * a
            val tail = 10f * exp(-2.5f * a)
            val target = if (a < 0.35f) hot else warm
            if (a > 0.75f && fuseHash(i + 42) > (0.9f - a) * 6f) continue
            target.moveTo(x, y)
            target.lineTo(x - cos(ang) * tail * 1.6f, y - sin(ang) * tail)
        }
    }
}

/** Soft smoke puffs rising from the burnt cord (drawn blurred, as one path). */
internal fun fuseSmokePath(out: FusePaths, f: FuseLine, pose: FusePose) {
    val b = out.smoke
    b.rewind()
    val amount = min(1f, pose.b * 8f) * (1f - pose.w)
    if (amount < 0.01f) return
    for (j in 0 until 9) {
        val k = 3 + floor(fuseHash(j + 60) * 3f).toInt()
        val life = LOOP / k
        val phase = frac(pose.time / life + fuseHash(j + 61))
        // Born along the burnt part, nearer the spark.
        val u = pose.b * (1f - fuseHash(j + 62) * 0.5f * (1f - phase * 0.3f))
        fuseAt(f, u, out.at)
        val rise = phase * 70f
        val drift = sin(phase * 5f + j) * 10f * phase
        val r = (6f + phase * 12f) * sin(Math.PI.toFloat() * phase) * amount
        if (r > 0.5f) b.addCircle(out.at[0] + drift, out.at[1] - rise, r, Path.Direction.CW)
    }
}

/** Opacity of the smoke (fades in with the burn, out with the re-weave). */
internal fun fuseSmokeOpacity(pose: FusePose) = 0.2f * min(1f, pose.b * 8f) * (1f - pose.w)
