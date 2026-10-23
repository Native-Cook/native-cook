package com.ngocdevv.nativecook.blocks.gestures.stickerboard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A sticker: its shape, its art, where it rests on the board and how it is turned.
 *
 * @param a Radius for round shapes; width for the rounded photo and the bubble (dp).
 * @param b Height for the rounded photo, the bubble and the heart (dp).
 * @param photo Unsplash id for a photo sticker.
 * @param color Fill of a drawn sticker.
 * @param text Words on the bubble or the badge.
 * @param homeX Resting place, as a share of the board's width.
 * @param homeY Resting place, as a share of its height between the safe areas.
 * @param turn Resting turn, degrees.
 */
class StickerDef(
    val kind: StickerKind,
    val a: Float,
    val b: Float,
    val homeX: Float,
    val homeY: Float,
    val turn: Float,
    val photo: String? = null,
    val color: Long = 0xFFFFC531,
    val text: String? = null,
)

enum class StickerKind { Circle, Rounded, Star, Smiley, Bubble, Heart, Badge }

internal class LoadedPhoto(val bitmap: Bitmap, val at: Float)

/**
 * A sticker's own art shape, its outline with the white border around it, how far it reaches, and
 * the rings it crumples along: its outline and a lumpy paper ball of radius 1, as x, y pairs on the
 * same rays from its middle, and how light (+) or dark (−) each facet of the ball is.
 */
internal class StickerShape(val art: Path, val outline: Path, val extent: Float, index: Int) {
    // Hit testing at a quarter-unit grain.
    private val region = Region().apply {
        val fine = Path(outline).apply { transform(Matrix().apply { setScale(4f, 4f) }) }
        setPath(fine, Region(-4000, -4000, 4000, 4000))
    }

    fun contains(x: Float, y: Float) = region.contains((x * 4f).roundToInt(), (y * 4f).roundToInt())

    val bounds = RectF().also { art.computeBounds(it, true) }

    val ring: FloatArray = sampleRing()
    val ball: FloatArray = ballRing((index + 1).toFloat())
    val shades = FloatArray(RING / FACET_STEP) { hash01((index + 1) * 3.7f + it * 5.3f) * 2f - 1f }

    /** The outermost edge of the outline along RING rays from its middle. */
    private fun sampleRing(): FloatArray {
        val out = FloatArray(RING * 2)
        for (i in 0 until RING) {
            val a = -PI / 2 + i.toDouble() / RING * PI * 2
            val cx = cos(a).toFloat()
            val cy = sin(a).toFloat()
            // March in from outside to the first point inside, then halve the step to the edge.
            var inside = 0f
            var r = extent + 2f
            while (r > 0f) {
                if (contains(cx * r, cy * r)) {
                    inside = r
                    break
                }
                r -= 1f
            }
            var lo = inside
            var hi = inside + 1f
            repeat(8) {
                val mid = (lo + hi) / 2f
                if (contains(cx * mid, cy * mid)) lo = mid else hi = mid
            }
            out[2 * i] = cx * lo
            out[2 * i + 1] = cy * lo
        }
        return out
    }
}

// Points around a sticker's outline, sampled at even angles from its middle; every fourth one starts
// a facet of the paper ball.
internal const val RING = 48
internal const val FACET_STEP = 4
// Chords between ring points drawn as creases on the ball.
private val CREASES = intArrayOf(2, 21, 7, 31, 13, 40, 25, 45, 35, 10, 18, 29)

/** A repeatable pseudo-random number in [0, 1). */
private fun hash01(n: Float): Float {
    val s = sin(n.toDouble() * 127.1 + 311.7) * 43758.5453
    return (s - kotlin.math.floor(s)).toFloat()
}

/** The ring of a crumpled ball of radius 1 on the same rays: lumpy, with a few deep folds. */
private fun ballRing(seed: Float): FloatArray {
    val raw = FloatArray(RING) { 0.8f + 0.3f * hash01(seed * 7.13f + it * 1.91f) }
    val out = FloatArray(RING * 2)
    for (i in 0 until RING) {
        val a = -PI / 2 + i.toDouble() / RING * PI * 2
        var f = 0.5f * raw[i] + 0.25f * (raw[(i + RING - 1) % RING] + raw[(i + 1) % RING])
        // A fold every so often bites into the ball.
        if (i % 7 == 3) f -= 0.13f
        if (i % 7 == 0) f += 0.06f
        out[2 * i] = cos(a).toFloat() * f
        out[2 * i + 1] = sin(a).toFloat() * f
    }
    return out
}

/** A five-pointed star, its points rounded by the stroke drawn over it. */
private fun starPath(r: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.5f
        if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
    }
    close()
}

private fun heartPath(w: Float, h: Float) = Path().apply {
    moveTo(0f, h * 0.95f)
    cubicTo(-w * 0.2f, h * 0.7f, -w, h * 0.35f, -w, -h * 0.25f)
    cubicTo(-w, -h * 0.85f, -w * 0.25f, -h * 1.05f, 0f, -h * 0.5f)
    cubicTo(w * 0.25f, -h * 1.05f, w, -h * 0.85f, w, -h * 0.25f)
    cubicTo(w, h * 0.35f, w * 0.2f, h * 0.7f, 0f, h * 0.95f)
    close()
}

/** A speech bubble: a pill with a tail off its lower left. */
private fun bubblePath(w: Float, h: Float): Path {
    val body = Path().apply { addRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h / 2f, h / 2f, Path.Direction.CW) }
    val tail = Path().apply {
        moveTo(-w * 0.28f, h * 0.3f)
        quadTo(-w * 0.32f, h * 0.62f, -w * 0.46f, h * 0.78f)
        quadTo(-w * 0.18f, h * 0.7f, -w * 0.06f, h * 0.4f)
        close()
    }
    body.op(tail, Path.Op.UNION)
    return body
}

/** A scalloped seal: a circle with sixteen bumps around it. */
private fun badgePath(r: Float) = Path().apply {
    val bumps = 16
    for (i in 0..bumps) {
        val a = i.toFloat() / bumps * PI.toFloat() * 2f
        if (i == 0) {
            moveTo(cos(a) * r, sin(a) * r)
            continue
        }
        val m = a - PI.toFloat() / bumps
        quadTo(cos(m) * r * 1.16f, sin(m) * r * 1.16f, cos(a) * r, sin(a) * r)
    }
    close()
}

@Suppress("DEPRECATION")
internal fun shapeOf(index: Int, s: StickerDef): StickerShape {
    val art = when (s.kind) {
        StickerKind.Circle, StickerKind.Smiley -> Path().apply { addCircle(0f, 0f, s.a, Path.Direction.CW) }
        StickerKind.Rounded -> Path().apply { addRoundRect(-s.a / 2f, -s.b / 2f, s.a / 2f, s.b / 2f, 18f, 18f, Path.Direction.CW) }
        StickerKind.Star -> starPath(s.a)
        StickerKind.Heart -> heartPath(s.a, s.b)
        StickerKind.Bubble -> bubblePath(s.a, s.b)
        StickerKind.Badge -> badgePath(s.a)
    }
    val stroke = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = BORDER * 2f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val border = Path()
    stroke.getFillPath(art, border)
    val outline = Path(art)
    outline.op(border, Path.Op.UNION)
    val r = RectF()
    outline.computeBounds(r, true)
    val extent = max(1f, max(hypot(r.left, r.top), hypot(r.right, r.bottom)))
    return StickerShape(art, outline, extent, index)
}

// Cork: warm ground, fine granules, darker flecks and a soft vignette. Android 13+; below that a
// plain warm ground with the same vignette stands in.
internal const val CORK_AGSL = """
uniform float2 size;

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
  float fine = noise(xy * 0.9);
  float mid = noise(xy * 0.22);
  float wide = noise(xy * 0.035);
  float3 base = float3(0.73, 0.54, 0.34);
  float3 col = base * (0.8 + 0.22 * mid + 0.14 * wide) * (0.88 + 0.22 * fine);
  float fleck = step(0.9, hash(floor(xy * 0.45))) * smoothstep(0.3, 0.8, noise(xy * 0.7));
  col *= 1.0 - 0.45 * fleck;
  float2 uv = xy / size - 0.5;
  col *= 1.0 - 0.9 * dot(uv, uv);
  return half4(half3(col), 1.0);
}
"""

private fun argb(hex: Long, a: Float = 1f) = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (hex and 0xFFFFFF).toInt()

/** Every paint and scratch object the picture needs, made once. */
internal class StickerPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val layer = Paint()
    val atop = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    val cork = Paint()
    val corkShader: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(CORK_AGSL) else null
    var corkFor = -1f
    private val heavy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 800, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val bold = text(heavy, 18f)
    val small = text(heavy, 13f)
    val hand = text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 17f)
    val dash = DashPathEffect(floatArrayOf(3f, 2.5f), 0f)
    val matrix = Matrix()
    val rect = RectF()
    val src = Rect()
    val bounds = Rect()
    val path = Path()
    val keep = Path()
    val ring = Path()
    val poly = Path()
    val tri = Path()
    val bin = BinArt()
    val frame = BinFrame()
    val at = FloatArray(2)

    private fun text(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
    }

    /** A string's width as Skia's `measureText` gives it: its ink bounds plus a point either side. */
    fun measure(paint: Paint, text: String): Float {
        paint.getTextBounds(text, 0, text.length, bounds)
        return if (bounds.isEmpty) 0f else bounds.width() + 2f
    }
}

/** Draws one frame the way the React Native version records its Skia picture, in dp. */
internal fun drawBoard(canvas: Canvas, e: StickerBoardEngine, defs: List<StickerDef>, shapes: List<StickerShape>, p: StickerPaints, title: String, reduced: Boolean, density: Float) {
    val width = e.width
    val height = e.height
    if (width <= 0f) return
    val k = e.k

    // The cork, or its plain stand-in.
    val cork = p.corkShader
    if (cork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (p.corkFor != width * 10000f + height) {
            (cork as RuntimeShader).setFloatUniform("size", max(1f, width), max(1f, height))
            p.corkFor = width * 10000f + height
        }
        p.cork.shader = cork
    } else if (p.corkFor != width * 10000f + height) {
        p.cork.shader = RadialGradient(
            width / 2f, height / 2f, hypot(width, height) / 2f,
            intArrayOf(argb(0xBA8A57), argb(0xA97E50), argb(0x6E5234)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        p.corkFor = width * 10000f + height
    }
    canvas.drawRect(0f, 0f, width, height, p.cork)

    drawTape(canvas, e, p, title, density)

    val q = e.peel
    if (e.live.size != defs.size || shapes.size != defs.size) return
    for (i in e.order) {
        if (i !in e.live.indices) continue
        // A sticker rising out of the pile keeps a copy right under the one that covered it.
        for (r in e.live.indices) {
            val up = e.live[r]
            if (up.rise < 1f && up.riseUnder == i && up.gone < 0f && !(q.index == r && q.detached)) drawOne(canvas, e, defs, shapes, p, r, reduced, 1f)
        }
        val s = e.live[i]
        if (s.gone >= 0f) continue
        if (q.index == i && q.detached) continue
        drawOne(canvas, e, defs, shapes, p, i, reduced, if (s.rise < 1f) easeInOut(s.rise) else 1f)
    }

    // The bin, a ball dropping into it, a sticker crumpling over it, and a loose one above it all.
    val f = p.frame
    f.set(e.bin, k, width, height, e.bottom, reduced)
    drawBin(canvas, p.bin, f, e.bin, k) {
        if (!reduced) {
            for (i in e.live.indices) {
                val s = e.live[i]
                if (s.gone < CRUMPLE || s.gone >= TOSS_END) continue
                val t = clamp01((s.gone - CRUMPLE) / DROP)
                val y0 = -(BIN_BODY + BIN_HOVER)
                val y1 = -BIN_BODY + BIN_MOUTH_DROP + 6f
                val turn = s.goneTurn + BALL_SPIN + 120f * t
                canvas.save()
                canvas.translate(0f, y0 + (y1 - y0) * t * t - sin(PI.toFloat() * t) * BALL_HOP)
                canvas.scale(1f / max(0.0001f, f.sx), 1f / max(0.0001f, f.sy))
                canvas.rotate(turn - f.rot)
                drawCrumpled(canvas, e, defs[i], shapes[i], p, i, 1f, k * s.goneScale, BALL * k * (1f - 0.15f * t), turn)
                canvas.restore()
            }
        }
    }
    for (i in e.live.indices) {
        val s = e.live[i]
        if (s.gone < 0f || s.gone >= CRUMPLE) continue
        val t = clamp01(s.gone / CRUMPLE)
        if (reduced) {
            // No crumple: it fades where it was let go.
            p.layer.alpha = ((1f - t) * 255f).roundToInt()
            canvas.saveLayer(null, p.layer)
            canvas.translate(s.goneX, s.goneY)
            canvas.rotate(s.goneTurn)
            canvas.scale(k * s.goneScale, k * s.goneScale)
            drawFace(canvas, e, defs[i], shapes[i], p, i, s.goneTurn)
            canvas.restore()
            continue
        }
        val c = easeInOut(t)
        val m = easeOut(t)
        f.point(0f, -(BIN_BODY + BIN_HOVER), p.at)
        val x = s.goneX + (p.at[0] - s.goneX) * m
        val y = s.goneY + (p.at[1] - s.goneY) * m
        val turn = s.goneTurn + BALL_SPIN * c
        val from = k * s.goneScale
        val radius = BALL * k
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(turn)
        // Its shadow on the board, shrinking with it.
        val a = -turn * PI.toFloat() / 180f
        val ox = 5f * k
        val oy = 14f * k
        crumplePath(p.poly, shapes[i], c, from, radius)
        p.shadow.color = argb(0x2A1505, 0.3f)
        p.shadow.blur(7f * k)
        canvas.save()
        canvas.translate(ox * cos(a) - oy * sin(a), ox * sin(a) + oy * cos(a))
        canvas.drawPath(p.poly, p.shadow)
        canvas.restore()
        p.shadow.maskFilter = null
        drawCrumpled(canvas, e, defs[i], shapes[i], p, i, c, from, radius, turn)
        canvas.restore()
    }
    if (q.index in e.live.indices && q.detached && e.live[q.index].gone < 0f) drawOne(canvas, e, defs, shapes, p, q.index, reduced, 1f)
}

/** A strip of masking tape across the top, with the board's name in marker. */
private fun drawTape(canvas: Canvas, e: StickerBoardEngine, p: StickerPaints, title: String, density: Float) {
    val k = e.k
    canvas.save()
    canvas.translate(e.width / 2f, e.top + 30f * k)
    canvas.rotate(-2.5f)
    val tw = 184f * k
    val th = 30f * k
    val jag = p.path
    jag.reset()
    jag.moveTo(-tw / 2f, -th / 2f)
    jag.lineTo(tw / 2f, -th / 2f)
    for (j in 0..6) jag.lineTo(tw / 2f + if (j % 2 == 0) 0f else -3f * k, -th / 2f + th * j / 6f)
    jag.lineTo(-tw / 2f, th / 2f)
    for (j in 6 downTo 0) jag.lineTo(-tw / 2f + if (j % 2 == 0) 0f else 3f * k, -th / 2f + th * j / 6f)
    jag.close()
    // A shadow blurred by 2 px of the screen, as the React Native version blurs it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        p.shadow.maskFilter = BlurMaskFilter(sigmaToRadius(2f / density), BlurMaskFilter.Blur.NORMAL)
    }
    p.shadow.color = (0x40 shl 24) or 0x3C1E05
    canvas.save()
    canvas.translate(0f, 1.5f)
    canvas.drawPath(jag, p.shadow)
    canvas.restore()
    p.shadow.maskFilter = null
    p.fill.shader = null
    p.fill.color = argb(0xF4E9C4, 0.92f)
    canvas.drawPath(jag, p.fill)
    p.hand.color = argb(0x3F2D1B)
    canvas.drawText(title, -p.measure(p.hand, title) / 2f, 6f, p.hand)
    canvas.restore()
}

private fun drawOne(canvas: Canvas, e: StickerBoardEngine, defs: List<StickerDef>, shapes: List<StickerShape>, p: StickerPaints, i: Int, reduced: Boolean, fade: Float) {
    val k = e.k
    val s = e.live[i]
    val q = e.peel
    val sq = s.squash
    val grow = if (reduced) 1f else 1f + LIFT_GROW * min(s.lift, 1.4f)
    val shrink = if (q.index == i && q.detached) 1f - MAGNET_SHRINK * clamp01(e.bin.magnet) else 1f
    val base = k * s.scale * grow * shrink
    val turn = s.turn + s.sway
    var alpha = fade
    var extra = 1f
    if (s.spawn >= 0f) {
        val t = clamp01(s.spawn / SPAWN_TIME)
        alpha *= t
        extra = if (reduced) 1f else 1f + 0.25f * (1f - easeOut(t))
    }
    if (alpha < 0.004f) return
    val layer = alpha < 0.995f
    if (layer) {
        p.layer.alpha = (alpha * 255f).roundToInt()
        canvas.saveLayer(null, p.layer)
    }
    canvas.save()
    canvas.translate(s.x, s.y)
    canvas.rotate(turn)
    canvas.scale(base * extra * (1f + sq), base * extra * (1f - sq))
    val peeled = q.index == i
    var cx = 0f
    var cy = 0f
    var fx = 0f
    var fy = 0f
    if (peeled) {
        cx = q.cx
        cy = q.cy
        if (q.flatten >= 0f) {
            val t = easeOut(clamp01(q.flatten / FLATTEN_TIME))
            fx = q.flatX + (q.cx - q.flatX) * t
            fy = q.flatY + (q.cy - q.flatY) * t
        } else if (q.detached) {
            fx = q.cx
            fy = q.cy
        } else {
            val f = toLocal(s, k, q.fx, q.fy)
            fx = f[0]
            fy = f[1]
        }
    }
    val len = hypot(fx - cx, fy - cy)
    if (peeled && len > 0.5f) {
        // The shadow of the part still stuck down, then the face with its flap.
        val nx = (fx - cx) / len
        val ny = (fy - cy) / len
        canvas.save()
        keepSide(p.keep, (cx + fx) / 2f, (cy + fy) / 2f, nx, ny)
        canvas.clipPath(p.keep)
        drawShadow(canvas, shapes[i].outline, p, s.lift, turn, base, alpha)
        canvas.restore()
        drawPeeled(canvas, e, defs[i], shapes[i], p, i, cx, cy, fx, fy, turn, base)
    } else {
        drawShadow(canvas, shapes[i].outline, p, s.lift, turn, base, alpha)
        drawFace(canvas, e, defs[i], shapes[i], p, i, turn)
    }
    canvas.restore()
    if (layer) canvas.restore()
}

/** The half of the plane on the finger's side of the fold. */
private fun keepSide(path: Path, mx: Float, my: Float, nx: Float, ny: Float) {
    val far = 2000f
    val px = -ny
    val py = nx
    path.reset()
    path.moveTo(mx + px * far, my + py * far)
    path.lineTo(mx - px * far, my - py * far)
    path.lineTo(mx - px * far + nx * far, my - py * far + ny * far)
    path.lineTo(mx + px * far + nx * far, my + py * far + ny * far)
    path.close()
}

/** A soft blur in the canvas's own units; hardware canvases honour mask filters from Android 9. */
private fun Paint.blur(sigma: Float) {
    maskFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) else null
}

/** The shadow a sticker casts, offset in screen terms however it is turned or scaled. */
private fun drawShadow(canvas: Canvas, outline: Path, p: StickerPaints, lift: Float, turn: Float, scale: Float, alpha: Float) {
    val l = min(lift, 1.6f)
    val ox = SHADOW_NEAR[0] + (SHADOW_FAR[0] - SHADOW_NEAR[0]) * l
    val oy = SHADOW_NEAR[1] + (SHADOW_FAR[1] - SHADOW_NEAR[1]) * l
    val blur = SHADOW_NEAR[2] + (SHADOW_FAR[2] - SHADOW_NEAR[2]) * l
    val a = -turn * PI.toFloat() / 180f
    p.shadow.color = argb(0x2A1505, alpha * (0.42f - 0.1f * min(l, 1f)))
    p.shadow.blur(blur / max(0.01f, scale))
    canvas.save()
    canvas.translate((ox * cos(a) - oy * sin(a)) / scale, (ox * sin(a) + oy * cos(a)) / scale)
    canvas.drawPath(outline, p.shadow)
    canvas.restore()
    p.shadow.maskFilter = null
}

/**
 * A sticker with a corner lifted. The fold is the line halfway between the corner and the finger,
 * square to the pull: the face is kept on the finger's side, and the part beyond the fold is
 * mirrored across it to show its pale backing, lit at the crease, casting a shadow on the rest.
 */
private fun drawPeeled(canvas: Canvas, e: StickerBoardEngine, def: StickerDef, shape: StickerShape, p: StickerPaints, i: Int, cx: Float, cy: Float, fx: Float, fy: Float, turn: Float, scale: Float) {
    val len = max(0.5f, hypot(fx - cx, fy - cy))
    val nx = (fx - cx) / len
    val ny = (fy - cy) / len
    val mx = (cx + fx) / 2f
    val my = (cy + fy) / 2f
    val d = mx * nx + my * ny
    p.matrix.setValues(floatArrayOf(1f - 2f * nx * nx, -2f * nx * ny, 2f * d * nx, -2f * nx * ny, 1f - 2f * ny * ny, 2f * d * ny, 0f, 0f, 1f))
    val outline = shape.outline

    canvas.save()
    keepSide(p.keep, mx, my, nx, ny)
    canvas.clipPath(p.keep)
    drawFace(canvas, e, def, shape, p, i, turn)
    // The flap's shadow on the face below it, nudged away from the crease.
    p.shadow.color = argb(0x1E1004, 0.34f)
    p.shadow.blur(5f / max(0.01f, scale))
    canvas.save()
    canvas.translate(nx * 4f, ny * 4f + 2f)
    canvas.concat(p.matrix)
    canvas.drawPath(outline, p.shadow)
    canvas.restore()
    p.shadow.maskFilter = null
    // The flap: the backing paper, bright along the crease and greyer towards its tip.
    canvas.saveLayer(null, null)
    canvas.save()
    canvas.concat(p.matrix)
    p.fill.shader = null
    p.fill.color = argb(0xECE7DC)
    canvas.drawPath(outline, p.fill)
    canvas.restore()
    p.atop.shader = LinearGradient(
        mx, my, mx + nx * len / 2f, my + ny * len / 2f,
        intArrayOf(argb(0xFFFFFF), argb(0xF1ECE2), argb(0xC9C1B2)),
        floatArrayOf(0f, 0.18f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(mx - 600f, my - 600f, mx + 600f, my + 600f, p.atop)
    canvas.restore()
    canvas.restore()
}

/** The sticker's face in its own units: white border, art, and a gloss that stays put as it turns. */
private fun drawFace(canvas: Canvas, e: StickerBoardEngine, def: StickerDef, shape: StickerShape, p: StickerPaints, i: Int, turn: Float) {
    val art = shape.art
    val outline = shape.outline
    val fill = p.fill
    fill.shader = null
    fill.style = Paint.Style.FILL
    fill.color = argb(0xFBFAF6)
    canvas.drawPath(outline, fill)
    p.stroke.strokeWidth = 0.8f
    p.stroke.color = argb(0x462D14, 0.18f)
    p.stroke.pathEffect = null
    canvas.drawPath(outline, p.stroke)

    if (def.photo != null) {
        fill.color = argb(0xD8CFC2)
        canvas.drawPath(art, fill)
        val loaded = e.loaded[i]
        if (loaded != null) {
            canvas.save()
            canvas.clipPath(art)
            val r = shape.bounds
            val bitmap = loaded.bitmap
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val fit = max(r.width() / iw, r.height() / ih)
            val sw = r.width() / fit
            val sh = r.height() / fit
            p.src.set(((iw - sw) / 2f).roundToInt(), ((ih - sh) / 2f).roundToInt(), ((iw + sw) / 2f).roundToInt(), ((ih + sh) / 2f).roundToInt())
            p.art.alpha = (clamp01((e.elapsed - loaded.at) / IMAGE_FADE) * 255f).roundToInt()
            canvas.drawBitmap(bitmap, p.src, r, p.art)
            canvas.restore()
        }
        p.stroke.strokeWidth = 1f
        p.stroke.color = argb(0x000000, 0.12f)
        canvas.drawPath(art, p.stroke)
    } else {
        fill.color = (0xFF000000 or def.color).toInt()
        canvas.drawPath(art, fill)
        when (def.kind) {
            StickerKind.Star -> {
                // Round the star's points with a stroke of its own colour.
                p.stroke.strokeWidth = 5f
                p.stroke.strokeJoin = Paint.Join.ROUND
                p.stroke.color = fill.color
                canvas.drawPath(art, p.stroke)
                p.stroke.strokeJoin = Paint.Join.MITER
            }
            StickerKind.Smiley -> {
                val r = def.a
                fill.color = argb(0x3B2A12)
                p.rect.set(-r * 0.38f, -r * 0.42f, -r * 0.18f, -r * 0.12f)
                canvas.drawOval(p.rect, fill)
                p.rect.set(r * 0.18f, -r * 0.42f, r * 0.38f, -r * 0.12f)
                canvas.drawOval(p.rect, fill)
                p.stroke.strokeWidth = r * 0.1f
                p.stroke.strokeCap = Paint.Cap.ROUND
                p.stroke.color = fill.color
                p.rect.set(-r * 0.52f, -r * 0.42f, r * 0.52f, r * 0.54f)
                canvas.drawArc(p.rect, 25f, 130f, false, p.stroke)
                p.stroke.strokeCap = Paint.Cap.BUTT
                fill.color = argb(0xFF6E5A, 0.45f)
                canvas.drawCircle(-r * 0.56f, r * 0.14f, r * 0.13f, fill)
                canvas.drawCircle(r * 0.56f, r * 0.14f, r * 0.13f, fill)
            }
            StickerKind.Bubble, StickerKind.Badge -> {
                val text = def.text ?: ""
                val font = if (def.kind == StickerKind.Bubble) p.bold else p.small
                val w = p.measure(font, text)
                font.color = argb(0xFFFFFF)
                if (def.kind == StickerKind.Badge) {
                    p.stroke.strokeWidth = 1.4f
                    p.stroke.color = argb(0xFFFFFF, 0.75f)
                    p.stroke.pathEffect = p.dash
                    canvas.drawCircle(0f, 0f, def.a * 0.78f, p.stroke)
                    p.stroke.pathEffect = null
                    canvas.drawText(text, -w / 2f, 5f, font)
                } else {
                    canvas.drawText(text, -w / 2f, 6f, font)
                }
            }
            else -> Unit
        }
    }
    // The vinyl gloss: a soft band along a fixed screen diagonal, whatever the sticker's turn.
    val extent = shape.extent
    val a = -turn * PI.toFloat() / 180f
    val dx = cos(a) * extent - sin(a) * extent
    val dy = sin(a) * extent + cos(a) * extent
    fill.color = argb(0x000000)
    fill.shader = LinearGradient(
        -dx, -dy, dx, dy,
        intArrayOf(argb(0xFFFFFF, 0f), argb(0xFFFFFF, 0.26f), argb(0xFFFFFF, 0f), argb(0xFFFFFF, 0.08f)),
        floatArrayOf(0.3f, 0.42f, 0.55f, 0.8f),
        Shader.TileMode.CLAMP,
    )
    canvas.save()
    canvas.clipPath(outline)
    canvas.drawRect(-extent, -extent, extent, extent, fill)
    canvas.restore()
    fill.shader = null
}

/** Coordinate `axis` (0 for x, 1 for y) of ring point `i`, part way from the outline to the ball. */
private fun ringAt(shape: StickerShape, i: Int, axis: Int, c: Float, from: Float, radius: Float): Float {
    val n = min(shape.ring.size, shape.ball.size) / 2
    if (n <= 0) return 0f
    val j = ((i % n) + n) % n
    return shape.ring[2 * j + axis] * from * (1f - c) + shape.ball[2 * j + axis] * radius * c
}

/** The ring of a sticker part way to a paper ball: its outline at `c` = 0, the ball at `c` = 1. */
private fun crumplePath(path: Path, shape: StickerShape, c: Float, from: Float, radius: Float) {
    path.reset()
    val n = min(shape.ring.size, shape.ball.size) / 2
    for (i in 0 until n) {
        val x = ringAt(shape, i, 0, c, from, radius)
        val y = ringAt(shape, i, 1, c, from, radius)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/**
 * A sticker crumpling into a paper ball, in screen dp around its middle. Its face shrinks inside the
 * crumpling outline, so its colours show between the creases at first; then facets of light and
 * shade, a paler paper and a few sharp creases make it read as a ball.
 */
private fun drawCrumpled(canvas: Canvas, e: StickerBoardEngine, def: StickerDef, shape: StickerShape, p: StickerPaints, i: Int, c: Float, from: Float, radius: Float, turn: Float) {
    if (min(shape.ring.size, shape.ball.size) / 2 < 3) return
    val poly = p.poly
    crumplePath(poly, shape, c, from, radius)
    canvas.save()
    canvas.clipPath(poly)
    val face = from + (radius * 1.35f / shape.extent - from) * c
    canvas.save()
    canvas.scale(face, face)
    drawFace(canvas, e, def, shape, p, i, turn)
    canvas.restore()
    // The paper shows more and more as it balls up.
    p.fill.shader = null
    p.fill.style = Paint.Style.FILL
    p.fill.color = argb(0xF4EFE4, 0.5f * c * c)
    canvas.drawPaint(p.fill)
    // Facets fanned from a point off the middle, each tilted to the light or away from it.
    val qx = radius * 0.24f * c
    val qy = -radius * 0.18f * c
    for (j in shape.shades.indices) {
        val i0 = j * FACET_STEP
        val i1 = i0 + FACET_STEP
        val sh = shape.shades[j]
        p.fill.color = argb(if (sh >= 0f) 0xFFFFFF else 0x5A3F22, kotlin.math.abs(sh) * (if (sh >= 0f) 0.42f else 0.32f) * c)
        p.tri.reset()
        p.tri.moveTo(qx, qy)
        p.tri.lineTo(ringAt(shape, i0, 0, c, from, radius), ringAt(shape, i0, 1, c, from, radius))
        p.tri.lineTo(ringAt(shape, i1, 0, c, from, radius), ringAt(shape, i1, 1, c, from, radius))
        p.tri.close()
        canvas.drawPath(p.tri, p.fill)
    }
    // Sharp creases: a dark fold with a lit edge beside it.
    val st = p.stroke
    st.pathEffect = null
    st.strokeCap = Paint.Cap.ROUND
    for (j in 0 until CREASES.size / 2) {
        val a = CREASES[2 * j] % RING
        val b = CREASES[2 * j + 1] % RING
        val ax = ringAt(shape, a, 0, c, from, radius)
        val ay = ringAt(shape, a, 1, c, from, radius)
        val bx = ringAt(shape, b, 0, c, from, radius)
        val by = ringAt(shape, b, 1, c, from, radius)
        p.tri.reset()
        p.tri.moveTo(ax, ay)
        p.tri.lineTo((ax + bx) / 2f + qx * 0.5f, (ay + by) / 2f + qy * 0.5f)
        p.tri.lineTo(bx, by)
        st.strokeWidth = 0.9f
        st.color = argb(0x4A3218, 0.45f * c)
        canvas.drawPath(p.tri, st)
        canvas.save()
        canvas.translate(0.7f, 0.7f)
        st.strokeWidth = 0.8f
        st.color = argb(0xFFFFFF, 0.7f * c)
        canvas.drawPath(p.tri, st)
        canvas.restore()
    }
    canvas.restore()
    // A crisp paper edge.
    st.strokeWidth = 0.8f
    st.strokeJoin = Paint.Join.ROUND
    st.color = argb(0x6B4F30, 0.2f + 0.25f * c)
    canvas.drawPath(poly, st)
    st.strokeJoin = Paint.Join.MITER
    st.strokeCap = Paint.Cap.BUTT
}
