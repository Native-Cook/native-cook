package com.ngocdevv.nativecook.blocks.inputs.rotaryphonedial

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Drawing for the rotary dial on an Android canvas already scaled to dp, step for step as the React
// Native Skia drawing. The light comes from the upper left throughout: bevels are bright toward it
// and dark away from it, shadows fall down and right. The chrome's reflections are a sweep gradient
// around the dial's centre that does not turn with the wheel (a polished disc spinning in place
// keeps its reflections), so only the holes, the shadows they cast and the numbers they reveal move.

internal class DialGeometry(
    val width: Float,
    val height: Float,
    val cx: Float,
    val cy: Float,
    /** The enamel number plate. */
    val plateR: Float,
    /** The finger wheel. */
    val wheelR: Float,
    /** Radius of the circle the holes sit on, and of each hole. */
    val ringR: Float,
    val holeR: Float,
    /** The opening in the middle that holds the number card. */
    val hubR: Float,
    /** The paper strip's window. */
    val strip: RectF,
)

/** A family at a CSS weight, as React Native asks Android for it. */
internal fun dialTypeface(family: String, weight: Int): Typeface {
    val base = Typeface.create(family, Typeface.NORMAL)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}

// Bakelite body, enamel plate, ink, chrome.
private const val BODY_HI = 0xFF2F6253.toInt()
internal const val DIAL_BODY = 0xFF163E33.toInt()
private const val BODY_LO = 0xFF06150F.toInt()
private const val PLATE = 0xFFF1E8D3.toInt()
private const val PLATE_EDGE = 0xFFD8CAAA.toInt()
private const val INK = 0xFF201D18.toInt()
private const val RED = 0xFFA23A2C.toInt()
private const val PAPER = 0xFFF4EDDC.toInt()
private val Chrome = intArrayOf(
    0xFFD6D8DA.toInt(), 0xFFFAFAF9.toInt(), 0xFF979BA0.toInt(), 0xFFE6E7E8.toInt(), 0xFF6A6F74.toInt(),
    0xFFCDD0D2.toInt(), 0xFFFBFBFA.toInt(), 0xFF8C9095.toInt(), 0xFFD6D8DA.toInt(),
)
private val ChromeStops = floatArrayOf(0f, 0.1f, 0.24f, 0.38f, 0.5f, 0.62f, 0.76f, 0.88f, 1f)

// The autoPlay finger reaches in from this direction (degrees, clockwise from the right).
private const val FINGER_FROM = 58f
// How far the finger shows before it has faded out, in fingertip radii.
private const val FINGER_LENGTH = 4.6f

// Letters over the digits, as on old exchange dials.
private val Letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PRS", "TUV", "WXY", "OPER")

/** The name and number on the card in the middle. */
private const val CARD_NAME = "ELMWOOD"
internal const val CARD_NUMBER = "5-0193"

/** An ARGB colour from 0…255 channels and a 0…1 alpha. */
private fun argb(r: Int, g: Int, b: Int, a: Double) = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

private fun rad(deg: Double) = deg * PI / 180

/** Paints and paths reused from frame to frame. */
internal class DialPainter(g: DialGeometry) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    // Paths kept from frame to frame, so the renderer can reuse their blurred shadows: the wheel's
    // is rebuilt only when it turns, the hook and the finger never.
    private val wheel = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private var wheelPhi = Double.NaN
    private val hook = Path()
    private val finger = Path()
    private val rect = RectF()
    private val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val blurs = HashMap<Float, BlurMaskFilter>()

    private val digitFace = dialTypeface("sans-serif-condensed", 700)
    private val cardFace = dialTypeface("sans-serif-medium", 700)
    private val stripFace = dialTypeface("monospace", 600)
    private val digitSize = (g.holeR * 0.98f).roundToInt().toFloat()
    private val cardNumberSize = (g.hubR * 0.36f).roundToInt().toFloat()
    private val chrome = SweepGradient(g.cx, g.cy, Chrome, ChromeStops)

    private fun blur(sigma: Float) = blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    private fun Paint.reset(color: Int): Paint {
        shader = null
        maskFilter = null
        this.color = color
        alpha = color ushr 24
        return this
    }

    /** A light-to-dark linear gradient across a box around (x, y) of half-size r, lit from the upper left. */
    private fun bevel(x: Float, y: Float, r: Float, light: Int, dark: Int) =
        LinearGradient(x - r * 0.7f, y - r * 0.7f, x + r * 0.7f, y + r * 0.7f, light, dark, Shader.TileMode.CLAMP)

    private fun soft(color: Int, sigma: Float) = fill.reset(color).apply { maskFilter = blur(sigma) }

    /** Text centred on x with its baseline on y; letter-spaced text is placed a glyph at a time. */
    private fun Canvas.centredText(s: String, x: Float, y: Float, face: Typeface, size: Float, color: Int, tracking: Float) {
        text.typeface = face
        text.textSize = size
        text.color = color
        if (tracking == 0f) {
            drawText(s, x - text.measureText(s) / 2, y, text)
            return
        }
        var total = 0f
        for (i in s.indices) total += text.measureText(s, i, i + 1) + (if (i > 0) tracking else 0f)
        var at = x - total / 2
        for (i in s.indices) {
            drawText(s, i, i + 1, at, y, text)
            at += text.measureText(s, i, i + 1) + tracking
        }
    }

    private fun Canvas.strokeCircle(x: Float, y: Float, r: Float, width: Float, paint: Paint = line) {
        paint.strokeWidth = width
        drawCircle(x, y, r, paint)
    }

    /** Everything that never moves: the phone's body, the strip's window, the enamel plate. */
    fun Canvas.drawStatic(g: DialGeometry) {
        val (width, height) = g.width to g.height
        val cx = g.cx
        val cy = g.cy
        val plateR = g.plateR
        // Bakelite, lit from the upper left, with a broad soft sheen.
        fill.reset(DIAL_BODY).shader = RadialGradient(
            width * 0.28f, height * 0.16f, height * 1.05f,
            intArrayOf(BODY_HI, DIAL_BODY, BODY_LO), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        drawRect(0f, 0f, width, height, fill)
        save()
        rotate(-28f, width * 0.3f, height * 0.22f)
        rect.set(width * 0.3f - width * 0.55f, height * 0.22f - 38f, width * 0.3f + width * 0.55f, height * 0.22f + 38f)
        drawOval(rect, soft(argb(255, 255, 255, 0.07), 26f))
        restore()

        // The strip's window: a chrome frame sunk into the body, paper inside.
        val s = g.strip
        rect.set(s.left - 4, s.top + 2, s.right + 8, s.bottom + 14)
        drawRoundRect(rect, 14f, 14f, soft(argb(0, 0, 0, 0.5), 8f))
        fill.reset(INK).shader = bevel(s.centerX(), s.centerY(), s.width() / 2, 0xFFF7F7F6.toInt(), 0xFF5E6368.toInt())
        rect.set(s.left - 6, s.top - 6, s.right + 6, s.bottom + 6)
        drawRoundRect(rect, 14f, 14f, fill)
        fill.reset(INK).shader = LinearGradient(
            0f, s.top, 0f, s.bottom,
            intArrayOf(0xFFE3D9C2.toInt(), PAPER, 0xFFEDE4CF.toInt()), floatArrayOf(0f, 0.28f, 1f), Shader.TileMode.CLAMP,
        )
        drawRoundRect(s, 9f, 9f, fill)
        save()
        path.reset()
        path.addRoundRect(s, 9f, 9f, Path.Direction.CW)
        clipPath(path)
        // Ruled line, perforations along both edges, a shadow under the frame's top lip.
        line.reset(argb(162, 58, 44, 0.35)).strokeWidth = 0.8f
        drawLine(s.left, s.top + s.height() * 0.74f, s.right, s.top + s.height() * 0.74f, line)
        var x = s.left + 10
        val dot = fill.reset(argb(70, 55, 35, 0.22))
        while (x < s.right) {
            drawCircle(x, s.top + 7, 2.1f, dot)
            drawCircle(x, s.bottom - 7, 2.1f, dot)
            x += 14
        }
        drawRect(s.left, s.top - 10, s.right, s.top + 4, soft(argb(40, 28, 10, 0.32), 5f))
        restore()

        // The dial's well: a dark recess, a chrome bezel, then the enamel plate.
        drawCircle(cx + 4, cy + 9, plateR * 1.13f, soft(argb(0, 0, 0, 0.55), 16f))
        drawCircle(cx, cy, plateR * 1.1f, fill.reset(BODY_LO))
        fill.reset(INK).shader = bevel(cx, cy, plateR * 1.08f, 0xFFF4F4F3.toInt(), 0xFF4D5257.toInt())
        drawCircle(cx, cy, plateR * 1.075f, fill)
        drawCircle(cx, cy, plateR * 1.04f, fill.reset(0xFF2A2C2E.toInt()))
        fill.reset(INK).shader = RadialGradient(
            cx - plateR * 0.3f, cy - plateR * 0.35f, plateR * 1.5f,
            intArrayOf(0xFFFBF6EA.toInt(), PLATE, PLATE_EDGE), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        drawCircle(cx, cy, plateR, fill)
        // Fine enamel rings near the rim.
        line.reset(argb(32, 29, 24, 0.55))
        strokeCircle(cx, cy, plateR - 4, 0.8f)
        line.reset(argb(32, 29, 24, 0.18))
        strokeCircle(cx, cy, g.wheelR + 3, 0.8f)

        // The numbers, printed on the plate under each hole's resting place.
        for (i in 0 until HOLES) {
            val a = rad(holeRest(i))
            val hx = cx + g.ringR * cos(a).toFloat()
            val hy = cy + g.ringR * sin(a).toFloat()
            val d = HoleDigits[i]
            val label = Letters[if (d == 0) 9 else d - 1]
            if (label.isNotEmpty()) {
                centredText(label, hx, hy - g.holeR * 0.3f, cardFace, 7.5f, RED, 0.8f)
                centredText(d.toString(), hx, hy + g.holeR * 0.54f, digitFace, digitSize, INK, 0f)
            } else {
                centredText(d.toString(), hx, hy + g.holeR * 0.31f, digitFace, digitSize, INK, 0f)
            }
            // A small dot on the outer ring marks each position.
            drawCircle(cx + (plateR - 9) * cos(a).toFloat(), cy + (plateR - 9) * sin(a).toFloat(), 1.6f, fill.reset(INK))
        }
    }

    /** The finger wheel's metal: a disc with the ten holes and the middle cut out (even-odd). */
    private fun wheelPath(g: DialGeometry, phi: Double): Path {
        if (phi == wheelPhi) return wheel
        wheelPhi = phi
        wheel.reset()
        wheel.fillType = Path.FillType.EVEN_ODD
        wheel.addCircle(g.cx, g.cy, g.wheelR, Path.Direction.CW)
        for (i in 0 until HOLES) {
            val a = rad(holeRest(i) + phi)
            wheel.addCircle(g.cx + g.ringR * cos(a).toFloat(), g.cy + g.ringR * sin(a).toFloat(), g.holeR, Path.Direction.CW)
        }
        wheel.addCircle(g.cx, g.cy, g.hubR, Path.Direction.CW)
        return wheel
    }

    /** The wheel, wound `phi` degrees clockwise: its shadow on the plate, the metal, the bevels. */
    fun Canvas.drawWheel(g: DialGeometry, phi: Double) {
        val cx = g.cx
        val cy = g.cy
        val metal = wheelPath(g, phi)

        // Its shadow on the plate, seen around the rim and inside the holes.
        save()
        path.reset()
        path.addCircle(cx, cy, g.plateR, Path.Direction.CW)
        clipPath(path)
        translate(2.5f, 4.5f)
        drawPath(metal, soft(argb(48, 30, 10, 0.42), 4.5f))
        restore()

        fill.reset(INK).shader = chrome
        drawPath(metal, fill)
        save()
        clipPath(metal)
        // Turned-metal rings, and a darkening toward the rim for the disc's curvature.
        for (k in 0 until 34) {
            val r = g.hubR + (g.wheelR - g.hubR) * (k + 0.5f) / 34
            line.reset(if (k % 2 == 0) argb(255, 255, 255, 0.10) else argb(0, 0, 0, 0.07))
            strokeCircle(cx, cy, r, 0.6f)
        }
        fill.reset(INK).shader = RadialGradient(
            cx, cy, g.wheelR,
            intArrayOf(0, 0, argb(0, 0, 0, 0.22)), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP,
        )
        drawCircle(cx, cy, g.wheelR, fill)
        // Inside each hole the wall shows: dark on the side away from the light, bright on the other.
        for (i in 0 until HOLES) {
            val a = rad(holeRest(i) + phi)
            val x = cx + g.ringR * cos(a).toFloat()
            val y = cy + g.ringR * sin(a).toFloat()
            line.reset(INK).shader = bevel(x, y, g.holeR, 0xFF3A3E43.toInt(), 0xFFFFFFFF.toInt())
            strokeCircle(x, y, g.holeR + 0.6f, 3.2f)
        }
        restore()
        // The rim's bevel, and the hub's retaining ring.
        line.reset(INK).shader = bevel(cx, cy, g.wheelR, 0xFFFFFFFF.toInt(), 0xFF41454A.toInt())
        strokeCircle(cx, cy, g.wheelR - 1.1f, 2.2f)
        line.reset(INK).shader = bevel(cx, cy, g.hubR, 0xFF50555A.toInt(), 0xFFFFFFFF.toInt())
        strokeCircle(cx, cy, g.hubR + 1.5f, 5f)
    }

    /** The finger stop: a curved chrome hook reaching in over the holes' track, twitched by `kick`. */
    fun Canvas.drawStop(g: DialGeometry, kick: Double) {
        val outer = g.plateR + 4
        val inner = g.ringR - g.holeR * 0.25f
        if (hook.isEmpty) {
            // Drawn along the +x axis, then turned to the stop's angle.
            hook.moveTo(outer, -9f)
            hook.cubicTo(outer - (outer - inner) * 0.45f, -8f, inner + 10, -6.5f, inner + 3, -4f)
            hook.quadTo(inner - 3, -1.5f, inner + 0.5f, 3.6f)
            hook.cubicTo(inner + 12, 6.5f, outer - (outer - inner) * 0.4f, 9f, outer, 10f)
            hook.close()
        }
        save()
        translate(g.cx, g.cy)
        rotate((STOP_ANGLE + kick * 0.04).toFloat())
        save()
        translate(2f, 4f)
        drawPath(hook, soft(argb(20, 12, 4, 0.45), 3.5f))
        restore()
        fill.reset(INK).shader = LinearGradient(
            0f, -9f, 0f, 10f,
            intArrayOf(0xFFFDFDFC.toInt(), 0xFFC9CCCF.toInt(), 0xFF7C8187.toInt(), 0xFFB5B8BB.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawPath(hook, fill)
        line.reset(argb(30, 30, 30, 0.45)).strokeWidth = 0.8f
        drawPath(hook, line)
        // The screw that holds it to the plate.
        drawCircle(outer - 7, 0.5f, 4.2f, fill.reset(0xFF6E7378.toInt()))
        fill.reset(INK).shader = bevel(outer - 7, 0.5f, 4f, 0xFFFFFFFF.toInt(), 0xFF83888D.toInt())
        drawCircle(outer - 7, 0.5f, 3.4f, fill)
        line.reset(argb(40, 40, 40, 0.6)).strokeWidth = 1f
        line.strokeCap = Paint.Cap.ROUND
        drawLine(outer - 9.3f, -1.4f, outer - 4.7f, 2.4f, line)
        line.strokeCap = Paint.Cap.BUTT
        restore()
    }

    /** The number card in the middle, under a clear dome; `press` (0…1) pushes it in. */
    fun Canvas.drawCard(g: DialGeometry, press: Double) {
        val cx = g.cx
        val cy = g.cy
        val r = g.hubR - 2
        val p = press.toFloat()
        save()
        val k = 1 - 0.035f * p
        scale(k, k, cx, cy)
        fill.reset(INK).shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.3f, r * 1.4f,
            intArrayOf(0xFFFFFBF1.toInt(), PAPER, 0xFFDCD0B5.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        drawCircle(cx, cy, r, fill)
        // The card's printed border and the shadow of the retaining ring on it.
        line.reset(RED)
        strokeCircle(cx, cy, r - 7, 1.2f)
        strokeCircle(cx, cy, r - 10, 0.6f)
        centredText(CARD_NAME, cx, cy - r * 0.12f, cardFace, 9f, RED, 2.2f)
        centredText(CARD_NUMBER, cx, cy + r * 0.34f, stripFace, cardNumberSize, INK, 0.6f)
        line.reset(argb(40, 28, 10, 0.28)).maskFilter = blur(3f)
        strokeCircle(cx + 1, cy + 2, r + 1, 6f)
        line.maskFilter = null
        // The clear dome: a broad sheen and a crisp window highlight, dimmer when pressed.
        save()
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        clipPath(path)
        fill.reset(0xFFFFFFFF.toInt()).shader = LinearGradient(
            cx - r, cy - r, cx + r * 0.2f, cy + r * 0.2f,
            argb(255, 255, 255, 0.55), argb(255, 255, 255, 0.0), Shader.TileMode.CLAMP,
        )
        fill.alpha = ((1 - 0.5f * p) * 255).roundToInt()
        rect.set(cx - r * 1.05f, cy - r * 1.02f, cx - r * 1.05f + r * 1.5f, cy - r * 1.02f + r * 0.95f)
        drawOval(rect, fill)
        restore()
        save()
        rotate(-38f, cx, cy)
        rect.set(cx - r * 0.42f, cy - r * 0.78f, cx - r * 0.42f + r * 0.5f, cy - r * 0.78f + 4.5f)
        drawRoundRect(rect, 2.25f, 2.25f, soft(argb(255, 255, 255, 0.85 * (1 - 0.6 * press)), 1.2f))
        restore()
        if (press > 0.001) drawCircle(cx, cy, r * 0.94f, soft(argb(0, 0, 0, 0.14 * press), 8f))
        restore()
    }

    /**
     * The autoPlay finger, seen from above: a fingertip pressed into the hole, reaching in from the
     * lower right as a right hand's would, with a nail and a soft shadow, fading out toward the hand
     * so it reads as a touch rather than an object.
     */
    fun Canvas.drawFinger(g: DialGeometry, angle: Double, shown: Double) {
        if (shown <= 0.01) return
        val a = rad(angle)
        val x = g.cx + g.ringR * cos(a).toFloat()
        val y = g.cy + g.ringR * sin(a).toFloat()
        val r = g.holeR * 0.82f
        val length = r * FINGER_LENGTH
        save()
        translate(x, y)
        rotate(FINGER_FROM)
        saveLayer(-r * 3, -r * 4, length + r * 3, r * 4, null)
        // Along +x from the tip (at the origin) back toward the hand, widening a little.
        if (finger.isEmpty) {
            finger.moveTo(0f, -r)
            finger.cubicTo(length * 0.4f, -r * 1.02f, length * 0.7f, -r * 1.12f, length, -r * 1.2f)
            finger.lineTo(length, r * 1.2f)
            finger.cubicTo(length * 0.7f, r * 1.12f, length * 0.4f, r * 1.02f, 0f, r)
            rect.set(-r, -r, r, r)
            finger.arcTo(rect, 90f, 180f)
            finger.close()
        }
        save()
        translate(3f, 5f)
        drawPath(finger, soft(argb(30, 15, 5, 0.34), 7f))
        restore()
        fill.reset(INK).shader = LinearGradient(
            0f, -r, 0f, r,
            intArrayOf(0xFFF0CDB8.toInt(), 0xFFE2B39B.toInt(), 0xFFB57E66.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        drawPath(finger, fill)
        fill.reset(INK).shader = LinearGradient(
            0f, -r * 0.6f, 0f, r * 0.6f,
            intArrayOf(0xFFFBE4DA.toInt(), 0xFFE9BFB0.toInt(), 0xFFC9968A.toInt()), null, Shader.TileMode.CLAMP,
        )
        rect.set(-r * 0.55f, -r * 0.56f, -r * 0.55f + r * 1.15f, -r * 0.56f + r * 1.12f)
        drawRoundRect(rect, r * 0.5f, r * 0.5f, fill)
        rect.set(-r * 0.3f, -r * 0.42f, r * 0.3f, -r * 0.42f + r * 0.22f)
        drawOval(rect, soft(argb(255, 255, 255, 0.55), 1.5f))
        // Fade toward the hand, and the whole finger in and out with `shown`.
        fade.shader = LinearGradient(r * 1.4f, 0f, length, 0f, argb(0, 0, 0, shown), 0, Shader.TileMode.CLAMP)
        drawRect(-r * 2, -r * 3, length + r * 2, r * 3, fade)
        restore()
        restore()
    }

    /** Pulse dots at the strip's right end: one fills for each number that passes the stop. */
    fun Canvas.drawPulses(g: DialGeometry, pulses: Int, shown: Double) {
        if (shown <= 0.01) return
        val s = g.strip
        val y = s.top + s.height() * 0.5f
        for (k in 0 until 10) {
            val x = s.right - 18 - (9 - k) * 9
            if (k < pulses) {
                drawCircle(x, y, 2.6f, fill.reset(argb(162, 58, 44, 0.9 * shown)))
            } else {
                line.reset(argb(32, 29, 24, 0.28 * shown))
                strokeCircle(x, y, 2.4f, 0.9f)
            }
        }
    }
}
