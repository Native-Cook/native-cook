package com.ngocdevv.nativecook.blocks.navigation.elevatorfloornav

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.navigation.elevatorfloornav.ElevatorMotion.FLOORS
import com.ngocdevv.nativecook.blocks.navigation.elevatorfloornav.ElevatorMotion.clamp01
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/*
 Elevator Floor Nav — the drawing, ported from elevatorArt.ts. One canvas over baked bitmaps: the
 lobby (green marble, the floor dial's face, the button panel), one poster per floor (the store's
 section, drawn in code), the two brushed-steel doors (with their glass vision panels cut out),
 and the brass surround. Every frame: the posters slide behind the doorway (blurred with the car's
 speed), the doors slide, a highlight sweeps their brushed steel, then the needle, the lanterns
 and the lit buttons. Compose has no per-draw image filter on a canvas, so each poster also gets
 three vertically blurred copies (σ 4, 8 and 16 dp, baked once) that cross-fade with the speed.
*/

internal class ElevatorFloor(val name: String, val tagline: String, val colors: IntArray, val items: Array<Pair<String, String>>)

internal val ElevatorStore = arrayOf(
    ElevatorFloor(
        "Shoes", "Loafers, boots & the cobbler’s bench", intArrayOf(elevatorRgb(0x6B3127), elevatorRgb(0x8C4434), elevatorRgb(0x4A211B)),
        arrayOf("Oxford loafer" to "£185", "Chelsea boot" to "£240", "Suede mule" to "£150"),
    ),
    ElevatorFloor(
        "Fashion", "Autumn tailoring & knitwear", intArrayOf(elevatorRgb(0x24324F), elevatorRgb(0x344870), elevatorRgb(0x18223A)),
        arrayOf("Wool overcoat" to "£420", "Merino crew" to "£95", "Pleated trouser" to "£140"),
    ),
    ElevatorFloor(
        "Beauty", "The fragrance hall & apothecary", intArrayOf(elevatorRgb(0x7D4B56), elevatorRgb(0x9E6673), elevatorRgb(0x523039)),
        arrayOf("Fig eau de parfum" to "£88", "Rose balm" to "£24", "Cedar candle" to "£42"),
    ),
    ElevatorFloor(
        "Home", "Linen, stoneware & lamps", intArrayOf(elevatorRgb(0x46523D), elevatorRgb(0x5F7052), elevatorRgb(0x2E3628)),
        arrayOf("Stoneware jug" to "£48", "Linen throw" to "£120", "Brass lamp" to "£210"),
    ),
    ElevatorFloor(
        "Café", "The rooftop café · open till nine", intArrayOf(elevatorRgb(0x73492A), elevatorRgb(0x946238), elevatorRgb(0x4B2F1A)),
        arrayOf("Flat white" to "£4", "Pear tart" to "£7", "Afternoon tea" to "£32"),
    ),
)

internal class ElevatorFonts {
    val title = ElevatorFloorNavGfx.font(Typeface.SERIF, 400, 50f)
    val caps = ElevatorFloorNavGfx.font(Typeface.DEFAULT, 700, 10f)
    val body = ElevatorFloorNavGfx.font(Typeface.DEFAULT, 400, 14f)
    val item = ElevatorFloorNavGfx.font(Typeface.DEFAULT, 600, 12f)
    val price = ElevatorFloorNavGfx.font(Typeface.DEFAULT, 400, 12f)
    val numeral = ElevatorFloorNavGfx.font(Typeface.SERIF, 700, 16f)
    val button = ElevatorFloorNavGfx.font(Typeface.SERIF, 700, 19f)
}

internal class ElevatorScene(val w: Float, val h: Float, top: Float, bottom: Float) {
    val dialR = min(90f, (h - top - bottom) * 0.11f)
    val dialY = top + 86 + dialR
    val dialX = w / 2
    val doorX = 20f
    val doorY = dialY + 26
    val doorW = w - 40
    val panelH = 112f
    val panelY = h - bottom - panelH - 10
    val doorH = panelY - 18 - doorY
    val buttonR = 25f
    val buttonY = panelY + 44
    val buttonX = FloatArray(FLOORS) { i -> doorX + 22 + buttonR + (doorW - 44 - buttonR * 2) * i / (FLOORS - 1) }
    /** Where the frame image sits (it only covers what it draws). */
    val frameRect = RectF(doorX - 60, doorY - 50, doorX - 60 + doorW + 120, doorY - 50 + doorH + 110)
}

/** The needle's angle (degrees from straight up) for a floor. */
internal fun elevatorNeedleAngle(floor: Double) = -66 + (floor - 1) * 132 / (FLOORS - 1)

/** Baked layers; posters after the first arrive from the background thread, so some may be null. */
internal class ElevatorLayers(val lobby: Bitmap, val doorL: Bitmap, val doorR: Bitmap, val frame: Bitmap, first: Bitmap) {
    @Volatile var posters: Array<Bitmap?> = arrayOfNulls<Bitmap>(FLOORS).also { it[0] = first }
    /** Vertically blurred copies of each poster at BLUR_LEVELS[1..]. */
    @Volatile var blurred: Array<Array<Bitmap?>> = Array(FLOORS) { arrayOfNulls<Bitmap>(3) }
}

internal val ElevatorBlurLevels = floatArrayOf(0f, 4f, 8f, 16f)

private val BRASS = intArrayOf(elevatorRgb(0x6A4B1F), elevatorRgb(0xC59E57), elevatorRgb(0xF1DCA4), elevatorRgb(0xB48A42), elevatorRgb(0x5A3F18))
private val BRASS_POS = floatArrayOf(0f, 0.3f, 0.46f, 0.7f, 1f)

internal object ElevatorFloorNavArt {
    private fun hash(n: Double): Double {
        val s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    private fun clipRound(c: Canvas, r: RectF, radius: Float) {
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
    }

    private val g = ElevatorFloorNavGfx().apply { liveBlur = true }

    /** What the resting frame needs: lobby, doors, frame, floor 1. */
    @Synchronized
    fun bakeBase(s: ElevatorScene, fonts: ElevatorFonts, scale: Float): ElevatorLayers {
        val gp = Paint()
        val marble = ElevatorFloorNavGrain(498, 500, 83.0, 50.0, 51, pxPerPoint = 1)
        val plate = ElevatorFloorNavGrain(128, 200, 1.1, 100.0, 61)
        val brush = ElevatorFloorNavGrain(500, 128, 250.0, 1.1, 81)
        val lobby = ElevatorFloorNavGfx.bake(s.w, s.h, scale) { c -> lobby(c, s, fonts, gp, marble, plate) }
        val doorL = ElevatorFloorNavGfx.bake(s.doorW / 2, s.doorH, scale) { c -> door(c, s, true, gp, brush) }
        val doorR = ElevatorFloorNavGfx.bake(s.doorW / 2, s.doorH, scale) { c -> door(c, s, false, gp, brush) }
        val r = s.frameRect
        val frame = ElevatorFloorNavGfx.bake(r.width(), r.height(), scale, r.left, r.top) { c -> frame(c, s) }
        return ElevatorLayers(lobby, doorL, doorR, frame, poster(s, 0, fonts, scale, gp, posterGrain))
    }

    private val posterGrain by lazy { ElevatorFloorNavGrain(128, 128, 0.91, 0.91, 70) }

    /** The other floors (floor 4 first: the loop rides there), then the blurred copies of every floor. */
    @Synchronized
    fun bakeRest(s: ElevatorScene, fonts: ElevatorFonts, scale: Float, layers: ElevatorLayers, job: Job? = null) {
        val gp = Paint()
        for (f in intArrayOf(3, 1, 2, 4)) {
            // An abandoned bake (new size) stops here instead of holding the lock bakeBase waits on.
            job?.ensureActive()
            val next = layers.posters.copyOf()
            next[f] = poster(s, f, fonts, scale, gp, posterGrain)
            layers.posters = next
        }
        for (f in intArrayOf(0, 3, 1, 2, 4)) {
            val src = layers.posters[f] ?: continue
            val w = max(1, s.doorW.roundToInt())
            val h = max(1, s.doorH.roundToInt())
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val copies = arrayOfNulls<Bitmap>(3)
            for (k in 1..3) copies[k - 1] = blurY(small, ElevatorBlurLevels[k], job)
            val next = layers.blurred.copyOf()
            next[f] = copies
            layers.blurred = next
        }
    }

    /** A vertical Gaussian blur of [src] (1 px per dp) with sigma in px, edges clamped. */
    private fun blurY(src: Bitmap, sigma: Float, job: Job?): Bitmap {
        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val radius = ceil(sigma * 3).toInt()
        val kernel = FloatArray(radius * 2 + 1) { i -> exp(-((i - radius) * (i - radius)) / (2f * sigma * sigma)) }
        val sum = kernel.sum()
        for (i in kernel.indices) kernel[i] /= sum
        val out = IntArray(w * h)
        for (x in 0 until w) {
            job?.ensureActive()
            for (y in 0 until h) {
                var a = 0f
                var r = 0f
                var gg = 0f
                var b = 0f
                for (k in -radius..radius) {
                    val yy = min(h - 1, max(0, y + k))
                    val p = inPx[yy * w + x]
                    val kw = kernel[k + radius]
                    a += (p ushr 24) * kw
                    r += ((p shr 16) and 0xFF) * kw
                    gg += ((p shr 8) and 0xFF) * kw
                    b += (p and 0xFF) * kw
                }
                out[y * w + x] = (a.roundToInt().coerceIn(0, 255) shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (gg.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Marble lobby, the floor dial's face and numerals, and the button panel with unlit buttons. */
    private fun lobby(c: Canvas, s: ElevatorScene, fonts: ElevatorFonts, gp: Paint, marble: ElevatorFloorNavGrain, plateGrain: ElevatorFloorNavGrain) {
        val w = s.w
        val h = s.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, g.shaded(g.linear(0f, 0f, w * 0.3f, h, intArrayOf(elevatorRgb(0x17312B), elevatorRgb(0x12271F), elevatorRgb(0x0E1F19), elevatorRgb(0x0A1813)), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        marble.draw(c, full, 0.2, gp)
        // Marble veins: a few long seeded curves, pale and soft.
        for (i in 0 until 7) {
            val b = Path()
            var x = hash(i.toDouble()) * w
            var y = -20.0
            b.moveTo(x.toFloat(), y.toFloat())
            while (y < h + 20) {
                val nx = x + (hash(i * 13 + y) - 0.5) * 90
                val ny = y + 60 + hash(i * 7 + y) * 60
                b.quadTo((x + (hash(i + y * 3) - 0.5) * 60).toFloat(), ((y + ny) / 2).toFloat(), nx.toFloat(), ny.toFloat())
                x = nx
                y = ny
            }
            val vein = g.stroke(g.paint(elevatorRgb(0xCFE3D8), 0.05 + 0.05 * hash(i * 5.0)), (0.8 + hash(i * 9.0) * 1.4).toFloat())
            vein.maskFilter = g.blur(0.8f)
            c.drawPath(b, vein)
        }
        c.drawRect(full, g.shaded(g.radial(w / 2, h * 0.45f, h * 0.75f, intArrayOf(0, elevatorRgba(1, 6, 4, 0.25), elevatorRgba(1, 6, 4, 0.6)), floatArrayOf(0.4f, 0.75f, 1f))))

        // Sunburst rays behind the dial.
        val ray = g.paint(elevatorRgb(0xC59E57), 0.22).apply { strokeWidth = 1f }
        val R = s.dialR
        for (i in 0..16) {
            val a = (-80 + 160.0 * i / 16) * (PI / 180)
            c.drawLine(
                (s.dialX + sin(a) * (R + 6)).toFloat(), (s.dialY - cos(a) * (R + 6)).toFloat(),
                (s.dialX + sin(a) * (R + 26)).toFloat(), (s.dialY - cos(a) * (R + 26)).toFloat(), ray,
            )
        }
        // Dial: a brass half-moon bezel round a cream enamel face, numerals on the arc.
        val half = Path().apply {
            addArc(RectF(s.dialX - R - 8, s.dialY - R - 8, s.dialX + R + 8, s.dialY + R + 8), 180f, 180f)
            close()
        }
        c.save()
        c.translate(0f, 8f)
        c.drawPath(half, g.blurred(0xFF000000.toInt(), 0.5, 10f))
        c.restore()
        c.drawPath(half, g.shaded(g.linear(s.dialX - R, 0f, s.dialX + R, 0f, BRASS, BRASS_POS)))
        val face = Path().apply {
            addArc(RectF(s.dialX - R, s.dialY - R, s.dialX + R, s.dialY + R), 180f, 180f)
            close()
        }
        c.drawPath(face, g.shaded(g.radial(s.dialX, s.dialY - R * 0.6f, R * 1.3f, intArrayOf(elevatorRgb(0xF6EDD8), elevatorRgb(0xEADCBE), elevatorRgb(0xD5C29E)))))
        for (i in 0..16) {
            val a = (-66 + 132.0 * i / 16) * (PI / 180)
            val long = i % 4 == 0
            val r0 = R - 6
            val r1 = R - if (long) 16 else 11
            val tick = g.stroke(g.paint(elevatorRgb(0x5A4526), 0.7), if (long) 1.6f else 0.9f, round = true)
            c.drawLine(
                (s.dialX + sin(a) * r0).toFloat(), (s.dialY - cos(a) * r0).toFloat(),
                (s.dialX + sin(a) * r1).toFloat(), (s.dialY - cos(a) * r1).toFloat(), tick,
            )
        }
        fonts.numeral.color = elevatorRgb(0x3A2C18)
        for (f in 1..FLOORS) {
            val a = elevatorNeedleAngle(f.toDouble()) * PI / 180
            val label = f.toString()
            val r = R - 31
            val tw = fonts.numeral.measureText(label)
            c.drawText(label, (s.dialX + sin(a) * r - tw / 2).toFloat(), (s.dialY - cos(a) * r + 6).toFloat(), fonts.numeral)
        }
        c.drawLine(s.dialX - R - 8, s.dialY, s.dialX + R + 8, s.dialY, g.paint(elevatorRgb(0x3A2C18), 0.5))

        // Button panel: a brass plate with a bevel, five recessed rings, the sections' names.
        val py = s.panelY
        val ph = s.panelH
        g.rr(c, s.doorX, py + 10, s.doorW, ph, 18f, g.blurred(0xFF000000.toInt(), 0.5, 14f))
        g.rr(c, s.doorX, py + 1.5f, s.doorW, ph, 18f, g.blurred(0xFF000000.toInt(), 0.6, 1.5f))
        val plate = RectF(s.doorX, py, s.doorX + s.doorW, py + ph)
        c.drawRoundRect(plate, 18f, 18f, g.shaded(g.linear(s.doorX, py, s.doorX + s.doorW * 0.6f, py + ph, intArrayOf(elevatorRgb(0xB08944), elevatorRgb(0x8C6A31), elevatorRgb(0x6E5124), elevatorRgb(0x5A4220)), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        c.save()
        clipRound(c, plate, 18f)
        plateGrain.draw(c, plate, 0.3, gp)
        c.restore()
        g.rr(c, s.doorX + 0.5f, py + 0.5f, s.doorW - 1, ph - 1, 17.5f, g.stroke(g.paint(elevatorRgb(0xFFE7B0), 0.45), 1f))
        fonts.caps.color = elevatorRgba(0x2B, 0x1E, 0x0C, 0.82)
        for (i in 0 until FLOORS) {
            val x = s.buttonX[i]
            val y = s.buttonY
            val r = s.buttonR
            c.drawCircle(x, y + 1, r + 4, g.blurred(elevatorRgb(0x2A1C08), 0.6, 2f))
            c.drawCircle(x, y, r + 3, g.shaded(g.linear(x - r, y - r, x + r, y + r, intArrayOf(elevatorRgb(0xF5E0A8), elevatorRgb(0xB28B45), elevatorRgb(0x6B4E1F)))))
            c.drawCircle(x, y, r - 1, g.paint(elevatorRgb(0x2A2116)))
            val label = ElevatorStore[i].name.uppercase()
            c.drawText(label, x - fonts.caps.measureText(label) / 2, y + r + 22, fonts.caps)
        }
    }

    /** One floor of the store, as the doorway shows it. */
    private fun poster(s: ElevatorScene, f: Int, fonts: ElevatorFonts, scale: Float, gp: Paint, grain: ElevatorFloorNavGrain): Bitmap {
        val fl = ElevatorStore[f]
        val W = s.doorW
        val H = s.doorH
        return ElevatorFloorNavGfx.bake(W, H, scale) { c ->
            val full = RectF(0f, 0f, W, H)
            c.drawRect(full, g.shaded(g.linear(0f, 0f, W * 0.4f, H, fl.colors)))
            c.drawRect(full, g.shaded(g.radial(W * 0.3f, H * 0.15f, H * 0.8f, intArrayOf(elevatorRgba(255, 240, 215, 0.18), elevatorRgba(255, 240, 215, 0.0)))))
            grain.draw(c, full, 0.12, gp)
            val ink = elevatorRgb(0xF7EEDD)
            val pad = 24f
            fonts.caps.color = ink
            fonts.caps.alpha = (255 * 0.7).roundToInt()
            c.drawText("FLOOR ${f + 1}", pad, 40f, fonts.caps)
            fonts.title.color = ink
            c.drawText(fl.name, pad - 2, 92f, fonts.title)
            fonts.body.color = ink
            fonts.body.alpha = (255 * 0.75).roundToInt()
            c.drawText(fl.tagline, pad, 118f, fonts.body)
            // Three arched display windows with a still life each.
            val gap = 10f
            val tw = (W - pad * 2 - gap * 2) / 3
            val ty = 146f
            val th = max(110f, H - ty - 64)
            for (i in 0 until 3) {
                val x = pad + i * (tw + gap)
                val path = Path().apply {
                    moveTo(x, ty + th)
                    lineTo(x, ty + tw / 2)
                    arcTo(RectF(x, ty, x + tw, ty + tw), 180f, 180f, false)
                    lineTo(x + tw, ty + th)
                    close()
                }
                c.drawPath(path, g.shaded(g.linear(0f, ty, 0f, ty + th, intArrayOf(elevatorRgba(255, 244, 226, 0.16), elevatorRgba(255, 244, 226, 0.08), elevatorRgba(0, 0, 0, 0.12)))))
                c.drawPath(path, g.stroke(g.paint(elevatorRgb(0xF7E3B8), 0.35), 1f))
                c.save()
                c.clipPath(path)
                val cx = x + tw / 2
                val floorY = ty + th - 18
                // A plinth and an object: shapes vary by floor and window, lit from the upper left.
                c.drawRect(cx - tw * 0.32f, floorY - 10, cx + tw * 0.32f, floorY + 18, g.shaded(g.linear(0f, floorY - 10, 0f, floorY + 18, intArrayOf(elevatorRgb(0xF2E6D0), elevatorRgb(0xCDBB9A), elevatorRgb(0xA8926E)))))
                c.drawOval(RectF(cx - tw * 0.3f, floorY - 16, cx + tw * 0.3f, floorY - 6), g.blurred(0xFF000000.toInt(), 0.25, 4f))
                val kind = (f + i) % 4
                val o = g.shaded(g.radial(cx - tw * 0.12f, floorY - tw * 0.45f, tw * 0.95f, intArrayOf(elevatorRgb(0xFFF7EA), elevatorRgb(0xE6D2B2), elevatorRgb(0xA88E6A), fl.colors[2]), floatArrayOf(0f, 0.35f, 0.75f, 1f)))
                when (kind) {
                    0 -> c.drawCircle(cx, floorY - tw * 0.27f, tw * 0.25f, o)
                    1 -> {
                        val vh = th * 0.5f
                        val v = Path().apply {
                            moveTo(cx - tw * 0.08f, floorY - 10)
                            cubicTo(cx - tw * 0.36f, floorY - vh * 0.45f, cx - tw * 0.1f, floorY - vh * 0.75f, cx - tw * 0.07f, floorY - vh)
                            lineTo(cx + tw * 0.07f, floorY - vh)
                            cubicTo(cx + tw * 0.1f, floorY - vh * 0.75f, cx + tw * 0.36f, floorY - vh * 0.45f, cx + tw * 0.08f, floorY - 10)
                            close()
                        }
                        c.drawPath(v, o)
                    }
                    2 -> for (k in 0 until 3) {
                        val r = tw * (0.3f - k * 0.07f)
                        g.rr(c, cx - r, floorY - 10 - (k + 1) * th * 0.13f, r * 2, th * 0.13f, 4f, o)
                    }
                    else -> {
                        g.rr(c, cx - tw * 0.13f, floorY - th * 0.55f, tw * 0.26f, th * 0.55f - 10, tw * 0.13f, o)
                        c.drawCircle(cx, floorY - th * 0.55f, tw * 0.16f, o)
                    }
                }
                c.restore()
                val item = fl.items[i]
                fonts.item.color = ink
                fonts.item.alpha = (255 * 0.92).roundToInt()
                c.drawText(item.first, x, ty + th + 22, fonts.item)
                fonts.price.color = ink
                fonts.price.alpha = (255 * 0.6).roundToInt()
                c.drawText(item.second, x, ty + th + 40, fonts.price)
            }
        }
    }

    /** One brushed-steel door (left or right), its vision panel cut out. */
    private fun door(c: Canvas, s: ElevatorScene, left: Boolean, gp: Paint, brush: ElevatorFloorNavGrain) {
        val W = s.doorW / 2
        val H = s.doorH
        val full = RectF(0f, 0f, W, H)
        c.drawRect(full, g.shaded(g.linear(0f, 0f, W, 0f, intArrayOf(elevatorRgb(0x7E858B), elevatorRgb(0xB6BCC1), elevatorRgb(0x9DA4AA), elevatorRgb(0x868D93)), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        // Brushing: long horizontal streaks (the right door's tile is offset so they differ).
        if (!left) {
            c.save()
            c.translate(-173f, -61f)
            brush.draw(c, RectF(173f, 61f, 173f + W, 61f + H), 0.5, gp)
            c.restore()
        } else {
            brush.draw(c, full, 0.5, gp)
        }
        // Deco engraving: nested chevrons in the middle, a brass trim on the meeting edge.
        val edgeX = if (left) W else 0f
        val dir = if (left) -1f else 1f
        val eng = g.stroke(g.paint(elevatorRgb(0x4E555B), 0.55), 1f)
        for (k in 0 until 4) {
            val b = Path()
            val y = H * 0.56f + k * 12
            b.moveTo(edgeX + dir * 22, y)
            b.lineTo(edgeX + dir * (W * 0.55f), y - 26)
            b.lineTo(edgeX + dir * (W - 18), y - 26)
            c.drawPath(b, eng)
            c.save()
            c.translate(0f, 1f)
            c.drawPath(b, g.stroke(g.paint(0xFFFFFFFF.toInt(), 0.25), 1f))
            c.restore()
        }
        val tx = if (left) W - 5 else 0f
        c.drawRect(tx, 0f, tx + 5, H, g.shaded(g.linear(tx, 0f, tx + 5, 0f, BRASS, BRASS_POS)))
        val ex = if (left) 0f else W - 1
        c.drawRect(ex, 0f, ex + 1, H, g.paint(elevatorRgb(0x2C3135), 0.8))
        // Vision panel: a tall slot near the meeting edge.
        val vx = if (left) W - 30 else 14f
        val vy = H * 0.12f
        val vh = H * 0.34f
        g.rr(c, vx - 3, vy - 3, 22f, vh + 6, 4f, g.shaded(g.linear(vx - 3, 0f, vx + 19, 0f, BRASS, BRASS_POS)))
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        g.rr(c, vx, vy, 16f, vh, 2f, hole)
    }

    /** The brass surround of the doorway, transparent inside. */
    private fun frame(c: Canvas, s: ElevatorScene) {
        val x = s.doorX
        val y = s.doorY
        val w = s.doorW
        val h = s.doorH
        val t = 10f
        val outer = RectF(x - t, y - t, x + w + t, y + h + t)
        val inner = RectF(x, y, x + w, y + h)
        g.rr(c, x - t, y - t + 12, w + t * 2, h + t * 2, 8f, g.blurred(0xFF000000.toInt(), 0.5, 14f))
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(outer, 8f, 8f, Path.Direction.CW)
            addRoundRect(inner, 2f, 2f, Path.Direction.CW)
        }
        c.drawPath(ring, g.shaded(g.linear(x, y, x + w, y + h, BRASS, BRASS_POS)))
        g.rr(c, x - t + 0.5f, y - t + 0.5f, w + t * 2 - 1, h + t * 2 - 1, 7.5f, g.stroke(g.paint(elevatorRgb(0xFFF0C8), 0.4), 1f))
        c.drawRoundRect(inner, 2f, 2f, g.stroke(g.paint(elevatorRgb(0x2A1D0A), 0.7), 1.5f))
        // An inner shadow where the frame meets the doorway.
        c.save()
        clipRound(c, inner, 2f)
        g.rr(c, x - 3, y - 3, w + 6, h + 6, 4f, g.stroke(g.blurred(0xFF000000.toInt(), 0.55, 6f), 8f))
        c.restore()
        // Stepped lintel ornament at the top centre.
        for (k in 0 until 3) {
            val sw = 70f - k * 20
            c.drawRect(s.w / 2 - sw / 2, y - t - 6 - k * 5, s.w / 2 + sw / 2, y - t - k * 5, g.shaded(g.linear(s.w / 2 - sw / 2, 0f, s.w / 2 + sw / 2, 0f, BRASS, BRASS_POS)))
        }
    }
}

/** Draws the moving parts each frame; holds its reusable paints and paths. */
internal class ElevatorPainter(private val fonts: ElevatorFonts) {
    private val g = ElevatorFloorNavGfx()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val lantern = Path()
    private val oval = RectF()
    private val buttonPaint = Paint(fonts.button)

    private fun image(c: Canvas, img: Bitmap, x: Float, y: Float, w: Float, h: Float, p: Paint) {
        src.set(0, 0, img.width, img.height)
        dst.set(x, y, x + w, y + h)
        c.drawBitmap(img, src, dst, p)
    }

    fun frame(c: Canvas, s: ElevatorScene, layers: ElevatorLayers, pose: ElevatorPose) {
        image(c, layers.lobby, 0f, 0f, s.w, s.h, imagePaint)

        // The store behind the doorway: each floor a poster stacked by height, moving with the car.
        c.save()
        c.clipRect(s.doorX, s.doorY, s.doorX + s.doorW, s.doorY + s.doorH)
        val step = s.doorH + 30
        val blur = min(16.0, pose.speed * 9).toFloat()
        val posters = layers.posters
        val blurred = layers.blurred
        for (f in 0 until FLOORS) {
            val y = (s.doorY + (pose.car - (f + 1)) * step).toFloat()
            if (y > s.doorY + s.doorH || y + s.doorH < s.doorY) continue
            val img = posters[f]
            if (img == null) {
                c.drawRect(s.doorX, y, s.doorX + s.doorW, y + s.doorH, g.paint(ElevatorStore[f].colors[1]))
                continue
            }
            image(c, img, s.doorX, y, s.doorW, s.doorH, imagePaint)
            // Motion blur: cross-fade between the baked copies that bracket the blur.
            if (blur > 0.3) {
                val copies = blurred[f]
                var k = 0
                while (k < 2 && blur > ElevatorBlurLevels[k + 1]) k += 1
                val lo = if (k == 0) null else copies[k - 1]
                val hi = copies[k]
                if (lo != null) image(c, lo, s.doorX, y, s.doorW, s.doorH, imagePaint)
                if (hi != null) {
                    val u = clamp01(((blur - ElevatorBlurLevels[k]) / (ElevatorBlurLevels[k + 1] - ElevatorBlurLevels[k])).toDouble())
                    fadePaint.alpha = (255 * u).roundToInt()
                    image(c, hi, s.doorX, y, s.doorW, s.doorH, fadePaint)
                }
            }
        }
        // The shaft between floors: a dark gap with a brass floor sill.
        for (f in 0 until FLOORS - 1) {
            val y = (s.doorY + (pose.car - (f + 1)) * step - 30).toFloat()
            if (y > s.doorY + s.doorH || y + 30 < s.doorY) continue
            c.drawRect(s.doorX, y, s.doorX + s.doorW, y + 30, g.paint(elevatorRgb(0x0B0D0C)))
            c.drawRect(s.doorX, y + 26, s.doorX + s.doorW, y + 30, g.shaded(g.linear(0f, y + 26, 0f, y + 30, BRASS, BRASS_POS)))
        }
        // The doors: open, each hides behind its jamb; closed, they meet in the middle.
        val half = s.doorW / 2
        val travel = ((1 - pose.doors) * (half + 6)).toFloat()
        if (pose.doors > 0.002) {
            val lx = s.doorX - travel
            val rx = s.doorX + half + travel
            c.drawRect(lx + half - 2, s.doorY, lx + half + 8, s.doorY + s.doorH, g.blurred(0xFF000000.toInt(), 0.35, 6f))
            image(c, layers.doorL, lx, s.doorY, half, s.doorH, imagePaint)
            image(c, layers.doorR, rx, s.doorY, half, s.doorH, imagePaint)
            // Glass in the vision panels: a smoky tint with a sliver of reflection.
            val vy = s.doorY + s.doorH * 0.12f
            val vh = s.doorH * 0.34f
            for (gx in floatArrayOf(lx + half - 30, rx + 14)) {
                c.drawRect(gx, vy, gx + 16, vy + vh, g.paint(elevatorRgb(0x1A2A26), 0.35))
                c.drawRect(gx + 3, vy, gx + 6, vy + vh, g.paint(0xFFFFFFFF.toInt(), 0.12))
            }
            // A broad highlight fixed in the room, so it sweeps across the steel as the doors move.
            for (dx in floatArrayOf(lx, rx)) {
                c.save()
                c.clipRect(dx, s.doorY, dx + half, s.doorY + s.doorH)
                c.drawRect(s.doorX, s.doorY, s.doorX + s.doorW, s.doorY + s.doorH, g.shaded(g.linear(s.doorX + s.doorW * 0.2f, 0f, s.doorX + s.doorW * 0.6f, 0f, intArrayOf(elevatorRgba(255, 255, 255, 0.0), elevatorRgba(255, 255, 255, 0.22), elevatorRgba(255, 255, 255, 0.0)))))
                c.restore()
            }
        }
        c.restore()
        image(c, layers.frame, s.frameRect.left, s.frameRect.top, s.frameRect.width(), s.frameRect.height(), imagePaint)

        // Needle, with its shadow on the enamel and a brass cap.
        val a = elevatorNeedleAngle(pose.needle) * PI / 180
        val R = s.dialR
        val tipX = (s.dialX + sin(a) * (R - 12)).toFloat()
        val tipY = (s.dialY - cos(a) * (R - 12)).toFloat()
        c.drawLine(s.dialX + 2, s.dialY + 3, tipX + 2, tipY + 3, g.stroke(g.blurred(0xFF000000.toInt(), 0.35, 2f), 3f, round = true))
        c.drawLine((s.dialX - sin(a) * 10).toFloat(), (s.dialY + cos(a) * 10).toFloat(), tipX, tipY, g.stroke(g.paint(elevatorRgb(0x1E1812)), 2.6f, round = true))
        c.drawCircle(s.dialX, s.dialY, 8f, g.shaded(g.radial(s.dialX - 2, s.dialY - 2, 9f, intArrayOf(elevatorRgb(0xF7E4B0), elevatorRgb(0xB68C42), elevatorRgb(0x5E4219)))))
        // Hall lanterns either side of the dial.
        lantern(c, s.dialX - R - 30, s.dialY - 22, true, pose.ding[0])
        lantern(c, s.dialX + R + 30, s.dialY - 22, false, pose.ding[1])
        // A ring of light from the dial when the bell rings.
        val bell = max(pose.ding[0], pose.ding[1])
        if (bell > 0.01) {
            val ring = g.stroke(g.blurred(elevatorRgb(0xFFC867), 0.5 * bell, 4f), 2f)
            ring.xfermode = ElevatorFloorNavGfx.plus
            c.drawCircle(s.dialX, s.dialY, (R + 14 + (1 - bell) * 40).toFloat(), ring)
        }

        // Buttons: glass discs that dip when pressed and glow amber while their floor is called.
        for (i in 0 until FLOORS) {
            val x = s.buttonX[i]
            val y = s.buttonY
            val lit = clamp01(pose.lit[i])
            val dip = pose.dip[i].toFloat()
            val r = (s.buttonR - 3) * (1 - 0.06f * dip)
            if (lit > 0.01) {
                val halo = g.blurred(elevatorRgb(0xFFB547), 0.6 * lit, 12f)
                halo.xfermode = ElevatorFloorNavGfx.plus
                c.drawCircle(x, y, s.buttonR + 8, halo)
            }
            val by = y + 1.5f * dip
            c.drawCircle(x, by, r, g.shaded(g.radial(x - r * 0.35f, y - r * 0.4f, r * 1.6f, intArrayOf(elevatorRgb(0xEFE6D4), elevatorRgb(0xD4C6AA), elevatorRgb(0xA8977A)))))
            if (lit > 0.01) c.drawCircle(x, by, r, g.shaded(g.radial(x - r * 0.35f, y - r * 0.4f, r * 1.6f, intArrayOf(elevatorRgb(0xFFF4D6), elevatorRgb(0xFFC66B), elevatorRgb(0xE08A2E))), lit))
            oval.set(x - r * 0.6f, y - r * 0.8f, x + r * 0.3f, y - r * 0.35f)
            c.drawOval(oval, g.paint(0xFFFFFFFF.toInt(), 0.35))
            val label = (i + 1).toString()
            buttonPaint.color = if (lit > 0.5) elevatorRgb(0x5A2E05) else elevatorRgb(0x3A2C18)
            c.drawText(label, x - buttonPaint.measureText(label) / 2, y + 7 + 1.5f * dip, buttonPaint)
        }
    }

    private fun lantern(c: Canvas, x: Float, y: Float, up: Boolean, glow: Double) {
        val d = if (up) -1f else 1f
        lantern.rewind()
        lantern.moveTo(x, y + d * 9)
        lantern.lineTo(x - 10, y - d * 7)
        lantern.lineTo(x + 10, y - d * 7)
        lantern.close()
        c.drawPath(lantern, g.paint(elevatorRgb(0x3A2E1E)))
        if (glow > 0.01) {
            val halo = g.blurred(elevatorRgb(0xFFB547), 0.9 * glow, 9f)
            halo.xfermode = ElevatorFloorNavGfx.plus
            c.drawCircle(x, y, 18f, halo)
            c.drawPath(lantern, g.paint(elevatorRgb(0xFFD58A), glow))
        }
        c.drawPath(lantern, g.stroke(g.paint(elevatorRgb(0xC59E57)), 1.2f))
    }
}
