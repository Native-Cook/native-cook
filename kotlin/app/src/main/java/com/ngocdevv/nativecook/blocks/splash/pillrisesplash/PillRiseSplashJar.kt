package com.ngocdevv.nativecook.blocks.splash.pillrisesplash

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One jar of the onboarding scene, in dp for a 402 dp wide screen (scaled by the block).
 * [x], [y]: centre of the jar's mouth, from the middle of the jar stage. [body], [height], [mouth]:
 * widest part of the body, its height from the rim to the foot, and the mouth's width. [tilt]:
 * resting tilt in radians. [period], [phase]: seconds per bob, and where in the bob it starts.
 */
internal class PillRiseJarSpec(
    val photo: String,
    val x: Float,
    val y: Float,
    val body: Float,
    val height: Float,
    val mouth: Float,
    val tilt: Float,
    val period: Float,
    val phase: Float,
)

// Three glazed jars, back to front, placed around the middle of the jar stage.
internal val PillRiseJars = listOf(
    PillRiseJarSpec("photo-1541961017774-22349e4a1262", 8f, -150f, 82f, 120f, 48f, 0.1f, 3.1f, 0.4f),
    PillRiseJarSpec("photo-1580566176138-daa588058b59", 96f, -58f, 96f, 142f, 58f, 0.17f, 3.6f, 2.1f),
    PillRiseJarSpec("photo-1536849460588-696219a9e98d", -92f, -30f, 116f, 168f, 66f, -0.21f, 3.3f, 4.0f),
)

internal const val COIN_SLOTS = 5
// A coin hovers, spinning, for COIN_HOVER s while it sinks COIN_SINK dp, then falls with gravity.
private const val COIN_HOVER = 0.32f
private const val COIN_SINK = 8f
private const val COIN_GRAVITY = 1900f
private const val COIN_R = 12f
// Thickness of the coin's milled edge, seen as it turns.
private const val COIN_EDGE = 2.4f
// Spin while hovering and the extra spin a fall adds, in rad/s.
private const val SPIN = 7f
private const val SPIN_FALL = 9f

// The lip stands this far proud of the opening; the opening is seen from above, squashed.
private const val LIP = 4f
private const val RIM_SQUASH = 0.34f
// The glaze photo is drawn this many times larger than the jar.
private const val GLAZE_ZOOM = 1.7f

// A landing coin dips the jar by up to DIP dp, rings at DIP_FREQ rad/s and dies away at DIP_DECAY /s.
private const val DIP = 7f
private const val DIP_FREQ = 19f
private const val DIP_DECAY = 6f
// Gentle float: BOB dp up and down, WOBBLE rad of sway.
private const val BOB = 6f
private const val WOBBLE = 0.025f
// The jars come in over this share of the board's entrance, one after another.
private const val APPEAR_FROM = 0.2f
private const val APPEAR_SPAN = 0.4f
private const val APPEAR_LAG = 0.09f
// Dragging tilts a jar by its sideways speed, up to TILT_MAX rad.
private const val TILT_PER_SPEED = 0.00035f
private const val TILT_MAX = 0.4f

// Room around a jar for its bloom (three of its blur's sigmas).
private const val BLOOM_PAD = 90f

private const val TO_DEGREES = (180.0 / PI).toFloat()

/** Seconds from a coin's appearance to the moment it drops through the rim of a jar [drop] dp below. */
internal fun pillLandingTime(drop: Float) = COIN_HOVER + sqrt(2f * max(0f, drop - COIN_SINK) / COIN_GRAVITY)

/**
 * A jar placed on screen, with its outline in its own space: the origin is the centre of the mouth.
 * The body's top edge is the near half of the opening, so whatever is drawn before the body (a
 * coin) disappears into it. [x], [y] is the mouth at rest; [drop] the fall from the coins' line.
 */
internal class PillRisePlacedJar(val spec: PillRiseJarSpec, k: Float, val x: Float, val y: Float, val drop: Float) {
    val body = Path()
    val rimFront = Path()
    val lipFront = Path()
    val back: RectF
    val opening: RectF
    val bounds: RectF
    val glazeRect: RectF
    val half: Float
    val height: Float
    val mid: Float get() = y + height / 2f

    init {
        val rin = spec.mouth / 2f * k
        val rout = rin + LIP * k
        val ry = rin * RIM_SQUASH
        val ryo = rout * RIM_SQUASH
        val bw = spec.body / 2f * k
        val h = spec.height * k
        val neck = rout * 0.9f
        val neckY = h * 0.14f
        val collar = 7f * k
        opening = RectF(-rin, -ry, rin, ry)
        back = RectF(-rout, -ryo, rout, ryo)

        body.moveTo(-rout, 0f)
        body.lineTo(-rin, 0f)
        // From the left of the opening round its near side to the right.
        body.arcTo(opening, 180f, -180f, false)
        body.lineTo(rout, 0f)
        body.lineTo(rout, collar)
        body.lineTo(neck, collar + 3f * k)
        body.lineTo(neck, neckY)
        body.cubicTo(neck, neckY + h * 0.1f, bw, neckY + h * 0.04f, bw, neckY + h * 0.24f)
        body.cubicTo(bw * 1.03f, h * 0.62f, bw, h * 0.86f, bw * 0.84f, h - 4f * k)
        body.quadTo(0f, h + ry * 1.5f, -bw * 0.84f, h - 4f * k)
        body.cubicTo(-bw, h * 0.86f, -bw * 1.03f, h * 0.62f, -bw, neckY + h * 0.24f)
        body.cubicTo(-bw, neckY + h * 0.04f, -neck, neckY + h * 0.1f, -neck, neckY)
        body.lineTo(-neck, collar + 3f * k)
        body.lineTo(-rout, collar)
        body.close()

        rimFront.addArc(opening, 180f, -180f)
        lipFront.addArc(back, 172f, -164f)

        bounds = RectF(-bw * 1.05f, -ryo, bw * 1.05f, h + ry * 1.5f)
        glazeRect = RectF(
            bounds.left - bounds.width() * (GLAZE_ZOOM - 1f) / 2f,
            bounds.top - bounds.height() * (GLAZE_ZOOM - 1f) / 2f,
            bounds.right + bounds.width() * (GLAZE_ZOOM - 1f) / 2f,
            bounds.bottom + bounds.height() * (GLAZE_ZOOM - 1f) / 2f,
        )
        half = bw
        height = h
    }

    // The paints that depend on the jar's size, made once.
    private val fallback = LinearGradient(
        bounds.left, 0f, bounds.right, 0f,
        intArrayOf(0xFFC2352B.toInt(), 0xFFF0A63A.toInt(), 0xFF2F8FD6.toInt()), null, Shader.TileMode.CLAMP,
    )
    private val opener = LinearGradient(
        0f, opening.top, 0f, opening.bottom, 0xFF040405.toInt(), 0xFF2B2420.toInt(), Shader.TileMode.CLAMP,
    )
    private val roundness = LinearGradient(
        bounds.left, 0f, bounds.right, 0f,
        intArrayOf(0xD906040C.toInt(), 0x5906040C, 0x24FFFFFF, 0x00FFFFFF, 0x7306040C, 0xE606040C.toInt()),
        floatArrayOf(0f, 0.1f, 0.32f, 0.6f, 0.86f, 1f), Shader.TileMode.CLAMP,
    )
    private val shadow = LinearGradient(
        0f, 0f, 0f, height,
        intArrayOf(0x6B000000, 0x00000000, 0x00000000, 0x61000000), floatArrayOf(0f, 0.16f, 0.7f, 1f), Shader.TileMode.CLAMP,
    )
    private var glaze: BitmapShader? = null
    private var glazeOf: Bitmap? = null

    /** The photo spread over GLAZE_ZOOM times the jar, cropped to cover, so its strokes read as runs of glaze. */
    fun glazeShader(image: Bitmap?): Shader {
        if (image == null) return fallback
        if (glazeOf !== image) {
            val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(glazeRect.width() / image.width, glazeRect.height() / image.height)
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate(
                glazeRect.centerX() - image.width * scale / 2f,
                glazeRect.centerY() - image.height * scale / 2f,
            )
            shader.setLocalMatrix(m)
            glaze = shader
            glazeOf = image
        }
        return glaze!!
    }

    // The jar never changes shape in its own space, so it is drawn once into two bitmaps: what lies
    // behind the coins (the bloom, the far lip and the dark opening) and what lies in front of them
    // (the body and its shading). A frame then only places two images and the coins between them.
    private var under: Bitmap? = null
    private var over: Bitmap? = null
    private var bakedOf: Any? = null
    private var bakedScale = 0f
    val underRect = RectF(bounds.left - BLOOM_PAD, bounds.top - BLOOM_PAD, bounds.right + BLOOM_PAD, bounds.bottom + BLOOM_PAD)
    val overRect = RectF(bounds.left - 4f, bounds.top - 4f, bounds.right + 4f, bounds.bottom + 4f)

    /** The two baked layers for this photo (or the gradient while it loads), at [scale] px per dp. */
    fun layers(image: Bitmap?, scale: Float, p: PillRiseJarPaints): Pair<Bitmap, Bitmap> {
        val key: Any = image ?: fallback
        val u = under
        val o = over
        if (u != null && o != null && bakedOf === key && bakedScale == scale) return u to o
        val glaze = glazeShader(image)
        val nu = bake(underRect, scale) { c ->
            // A bloom of the jar's own colours behind it.
            p.bloom.shader = glaze
            c.drawPath(body, p.bloom)
            // The far side of the lip, then the dark inside of the jar.
            p.fill.shader = glaze
            p.fill.alpha = 255
            c.drawOval(back, p.fill)
            p.fill.shader = null
            p.fill.color = 0x29FFFFFF
            c.drawOval(back, p.fill)
            p.fill.shader = opener
            p.fill.alpha = 255
            c.drawOval(opening, p.fill)
        }
        val no = bake(overRect, scale) { c ->
            p.fill.shader = glaze
            p.fill.alpha = 255
            c.drawPath(body, p.fill)
            val clip = c.save()
            c.clipPath(body)
            // Round the body: dark at both edges, a little light left of centre; shadow under the lip and the foot.
            p.fill.shader = roundness
            c.drawPath(body, p.fill)
            p.fill.shader = shadow
            c.drawPath(body, p.fill)
            p.fill.shader = null
            // The glossy streak and the hot spot on the shoulder, then a cooler rim light on the right.
            p.rect.set(-half * 0.6f, height * 0.27f, -half * 0.6f + half * 0.17f, height * 0.77f)
            c.drawRoundRect(p.rect, half * 0.085f, half * 0.085f, p.streak)
            c.drawCircle(-half * 0.42f, height * 0.25f, 4.5f, p.spot)
            p.rect.set(half * 0.74f, height * 0.3f, half * 0.84f, height * 0.76f)
            c.drawRoundRect(p.rect, half * 0.05f, half * 0.05f, p.rimLight)
            c.restoreToCount(clip)
            p.stroke.strokeWidth = 1.2f
            p.stroke.color = 0x4D000000
            c.drawPath(lipFront, p.stroke)
            p.stroke.strokeWidth = 1.4f
            p.stroke.color = 0x8CFFFFFF.toInt()
            c.drawPath(rimFront, p.stroke)
        }
        under?.recycle()
        over?.recycle()
        under = nu
        over = no
        bakedOf = key
        bakedScale = scale
        return nu to no
    }

    private inline fun bake(rect: RectF, scale: Float, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, (rect.width() * scale).roundToInt()), max(1, (rect.height() * scale).roundToInt()), Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bitmap)
        c.scale(scale, scale)
        c.translate(-rect.left, -rect.top)
        draw(c)
        return bitmap
    }
}

/** What the jars need from a frame. */
internal class PillRiseJarFrame {
    var board = 0f
    var time = 0f
    var reducedMotion = false
    var offsetX = FloatArray(3)
    var offsetY = FloatArray(3)
    var speedX = FloatArray(3)
    var landed = FloatArray(3) { -100f }
    var coins = Array(COIN_SLOTS) { PillNoCoin }
    // Pixels per dp, for the baked jar layers.
    var scale = 1f
}

/** Reused paints for the jars and the coins. */
internal class PillRiseJarPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val bloom = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(30f), BlurMaskFilter.Blur.NORMAL)
        alpha = (0.22f * 255).toInt()
    }
    val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
        color = 0x80FFFFFF.toInt()
    }
    val spot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
        color = 0xD9FFFFFF.toInt()
    }
    val rimLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
        color = 0x4DC8E1FF
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val coinFace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -COIN_R, -COIN_R, COIN_R, COIN_R,
            intArrayOf(0xFFFFF4BF.toInt(), 0xFFF5C64F.toInt(), 0xFFC58A1E.toInt()), null, Shader.TileMode.CLAMP,
        )
    }
    val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val rect = RectF()
}

/**
 * A glazed jar floating in the dark: the photo is its glaze, shaded into a body by a dark edge on
 * both sides, a soft streak of light down its left and a lit rim. Coins fall into its mouth.
 */
internal fun drawPillRiseJar(canvas: Canvas, jar: PillRisePlacedJar, index: Int, image: Bitmap?, f: PillRiseJarFrame, p: PillRiseJarPaints) {
    val spec = jar.spec
    val t = f.time
    val reduced = f.reducedMotion
    val from = APPEAR_FROM + index * APPEAR_LAG
    val appear = PillEaseOut.transform(pillSeg(f.board, from, from + APPEAR_SPAN))
    val opacity = pillSeg(f.board, from, from + APPEAR_SPAN * 0.6f)
    if (opacity <= 0f) return
    val bob = if (reduced) 0f else BOB * sin((PI * 2 * t / spec.period + spec.phase).toFloat())
    val sway = if (reduced) 0f else WOBBLE * sin((PI * 2 * t / (spec.period * 1.3f) + spec.phase * 2f).toFloat())
    val since = t - f.landed[index]
    val dip = if (reduced || since < 0f || since > 1.4f) 0f else DIP * exp(-since * DIP_DECAY) * sin(since * DIP_FREQ)
    val tilt = (f.speedX[index] * TILT_PER_SPEED).coerceIn(-TILT_MAX, TILT_MAX)
    val rise = if (reduced) 0f else (1f - appear) * 46f
    val grow = if (reduced) 1f else 0.84f + 0.16f * appear

    val save = canvas.save()
    // Turned and scaled about the middle of the jar, not its mouth.
    canvas.translate(jar.x + f.offsetX[index], jar.y + f.offsetY[index] + bob + dip + rise + jar.height / 2f)
    canvas.rotate((spec.tilt + sway + tilt) * TO_DEGREES)
    canvas.scale(grow * (1f + dip * 0.005f), grow * (1f - dip * 0.008f))
    canvas.translate(0f, -jar.height / 2f)
    if (opacity < 1f) {
        // The jar fades as one piece, coins and all.
        val r = jar.underRect
        canvas.saveLayerAlpha(r.left, r.top, r.right, r.bottom, (opacity * 255).toInt())
    }

    val (under, over) = jar.layers(image, f.scale, p)
    canvas.drawBitmap(under, null, jar.underRect, p.image)
    for (slot in 0 until COIN_SLOTS) drawPillRiseCoin(canvas, slot, index, jar.drop, f, p)
    canvas.drawBitmap(over, null, jar.overRect, p.image)
    canvas.restoreToCount(save)
}

/** A gold coin that spins above a jar, then drops into it. Drawn in the jar's space. */
private fun drawPillRiseCoin(canvas: Canvas, slot: Int, jar: Int, drop: Float, f: PillRiseJarFrame, p: PillRiseJarPaints) {
    val coin = f.coins[slot]
    val t = f.time - coin.t0
    if (coin.jar != jar || t < 0f || t > 2f) return
    var x = 0f
    var y: Float
    var spin = 0f
    val alpha: Float
    var scale = 1f
    if (f.reducedMotion) {
        // No fall: the coin fades in at the mouth and fades away into it.
        y = -14f
        alpha = pillSeg(t, 0f, 0.15f) * (1f - pillSeg(t, 0.45f, 0.7f))
    } else {
        val land = pillLandingTime(drop) - COIN_HOVER
        val tf = max(0f, t - COIN_HOVER)
        y = if (t < COIN_HOVER) -drop + COIN_SINK * PillEaseOut.transform(t / COIN_HOVER)
        else -drop + COIN_SINK + 0.5f * COIN_GRAVITY * tf * tf
        // Fully swallowed once it is a coin's height inside the rim.
        alpha = pillSeg(t, 0f, 0.14f) * (if (y > COIN_R * 3f) 0f else 1f)
        x = coin.dx * (1f - PillEaseInOut.transform(min(1f, tf / max(land, 0.01f))))
        spin = SPIN * t + SPIN_FALL * tf
        scale = 0.7f + 0.3f * PillEaseOut.transform(pillSeg(t, 0f, 0.25f))
    }
    if (alpha <= 0f) return
    val save = canvas.save()
    canvas.translate(x, y)
    canvas.scale(scale, scale)
    if (alpha < 1f) canvas.saveLayerAlpha(-COIN_R - 2f, -COIN_R - 2f, COIN_R + 2f, COIN_R + COIN_EDGE + 2f, (alpha * 255).toInt())
    // The coin turns about its horizontal axis: its face flattens, and its edge shows under it.
    val flat = max(0.07f, abs(cos(spin)))
    p.rect.set(-COIN_R, -COIN_R * flat, COIN_R, COIN_R * flat)
    p.rect.offset(0f, COIN_EDGE * abs(sin(spin)))
    p.fill.shader = null
    p.fill.color = 0xFF94600F.toInt()
    canvas.drawOval(p.rect, p.fill)
    canvas.scale(1f, flat)
    canvas.drawCircle(0f, 0f, COIN_R, p.coinFace)
    p.stroke.strokeWidth = 1.3f
    p.stroke.color = 0xD9C48A1D.toInt()
    canvas.drawCircle(0f, 0f, COIN_R * 0.68f, p.stroke)
    p.fill.color = 0xCCFFFBE6.toInt()
    canvas.drawCircle(-COIN_R * 0.3f, -COIN_R * 0.35f, COIN_R * 0.22f, p.fill)
    canvas.restoreToCount(save)
}
