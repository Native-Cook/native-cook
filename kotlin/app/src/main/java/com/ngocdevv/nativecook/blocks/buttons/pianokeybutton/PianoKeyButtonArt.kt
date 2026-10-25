package com.ngocdevv.nativecook.blocks.buttons.pianokeybutton

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.buttons.pianokeybutton.PianoKeyButtonMotion.clamp01
import com.ngocdevv.nativecook.blocks.buttons.pianokeybutton.PianoKeyButtonMotion.easeOut
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Piano Key Button — the drawing, ported from pianoKeyArt.ts. One canvas: a baked background
 (lacquer, the lesson card, the fallboard rail, the hammer recess and the stave) and a baked
 overlay (the felt strip and the black keys), with the parts that move drawn between and over them
 every frame: hammers and strings in the recess, the three ivory keys, their labels, glows, ring
 waves and the stave's notes. Everything is in dp (the canvas is scaled by the density).
*/

internal fun pianoRgb(hex: Int) = (0xFF shl 24) or hex
internal fun pianoRgba(r: Int, g: Int, b: Int, a: Double) =
    ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

internal val PianoKeyColors = intArrayOf(pianoRgb(0xF2B35B), pianoRgb(0x5FC9C1), pianoRgb(0xEC8796))

internal class PianoKeyLabel(val title: String, val sub: String, val note: String)

internal val PianoKeyLabels = arrayOf(
    PianoKeyLabel("Warm up", "5 min", "C"),
    PianoKeyLabel("Lesson 14", "Triads", "E"),
    PianoKeyLabel("Free play", "Any key", "G"),
)

private val INK = pianoRgb(0x3A2C20)
private val INK_SOFT = pianoRgb(0x8C7660)

internal class PianoKeyScene(val w: Float, val h: Float, top: Float, bottom: Float) {
    val cardX = 16f
    val cardY = top + 108
    val cardW = w - 32
    val cardH = min(116f, max(96f, h * 0.13f)).roundToInt().toFloat()
    val recessTop = cardY + cardH + 30
    val feltY = recessTop + 36
    val feltH = 16f
    val keyTop = feltY + feltH
    val keyBottom = h - bottom - 54
    val gap = 5f
    val keyW = (w - 28 - gap * 2) / 3
    val keyX = floatArrayOf(14f, 14 + keyW + gap, 14 + 2 * (keyW + gap))
    val staffR = cardX + cardW - 20
    val staffL = staffR - min(150f, cardW * 0.42f)
    val staffGap = 7f
    val staffY = cardY + cardH / 2 + staffGap * 2 + 2

    /** Where the overlay image sits (it only covers what it draws). */
    val overlay = RectF(0f, feltY - 4, w, feltY - 4 + (keyBottom - feltY + 34))

    fun keyAt(x: Float, y: Float): Int {
        if (y < keyTop - 12 || y > keyBottom + 14) return -1
        for (i in 0 until 3) if (x >= keyX[i] - gap / 2 && x <= keyX[i] + keyW + gap / 2) return i
        return -1
    }
}

/** Paints, gradients and grain for the block (android.graphics, so it reads like the Skia code). */
internal class PianoKeyGfx {
    private val pool = Array(10) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var next = 0
    private val blurs = HashMap<Float, BlurMaskFilter>()
    /** Hardware canvases ignore mask filters below API 28: blurred shapes are then left out. */
    var liveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun paint(color: Int, alpha: Double = 1.0): Paint {
        val p = pool[next]
        next = (next + 1) % pool.size
        p.reset()
        p.isAntiAlias = true
        p.color = color
        if (alpha < 1) p.alpha = (p.alpha * clamp01(alpha)).roundToInt()
        return p
    }

    fun shaded(shader: Shader, alpha: Double = 1.0): Paint {
        val p = paint(0xFF000000.toInt())
        p.shader = shader
        if (alpha < 1) p.alpha = (255 * clamp01(alpha)).roundToInt()
        return p
    }

    fun blurred(color: Int, alpha: Double, sigma: Float, soft: Boolean = true): Paint {
        val p = paint(color, alpha)
        if (soft && !liveBlur) p.alpha = 0
        // Quantised to a quarter dp so a sigma that moves with the pose keeps a small cache.
        val q = (sigma * 4).roundToInt() / 4f
        p.maskFilter = blurs.getOrPut(q) { BlurMaskFilter(sigmaToRadius(q), BlurMaskFilter.Blur.NORMAL) }
        return p
    }

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)

    fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray? = null) =
        RadialGradient(x, y, max(0.01f, r), colors, pos, Shader.TileMode.CLAMP)

    companion object {
        val overlayMode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        val screenMode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

        /** [color] multiplied by the key's ivory, for laying a multiply wash over it with SRC_OVER. */
        fun onIvory(color: Int): Int {
            val iv = pianoRgb(0xF2E8D3)
            fun ch(sh: Int) = ((color shr sh and 0xFF) * (iv shr sh and 0xFF) / 255) shl sh
            return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
        }
    }
}

/** A tile of two-octave periodic value noise, grey around 0.5, at 2 px per dp (PianoKeyButtonArt.swift). */
internal class PianoKeyGrain(points: Int, cellX: Double, cellY: Double, seed: Int, heightPoints: Int = points) {
    private val bitmap: Bitmap
    private val shader: BitmapShader

    init {
        val wPx = points * 2
        val hPx = heightPoints * 2
        val nx = max(1, (points / cellX).roundToInt())
        val ny = max(1, (heightPoints / cellY).roundToInt())
        fun hash(x: Int, y: Int, s: Int): Double {
            var h = x * 374_761_393 + y * 668_265_263 + s * -2_048_144_777
            h = (h xor (h ushr 13)) * 1_274_126_177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65535.0
        }
        // Two-octave value noise, with the lattice hashed once and each column's and
        // row's cell and smoothstep weight worked out once, so the per-pixel work is a few
        // multiplies (it was hashing eight lattice points per pixel, which held up the first frame).
        class Octave(val px: Int, val py: Int, s: Int) {
            val lat = DoubleArray(px * py) { hash(it % px, it / px, s) }
            val x0 = IntArray(wPx)
            val x1 = IntArray(wPx)
            val sx = DoubleArray(wPx)
            init {
                for (i in 0 until wPx) {
                    val x = (i + 0.5) / wPx * px
                    val ix = floor(x).toInt()
                    val f = x - ix
                    x0[i] = ((ix % px) + px) % px
                    x1[i] = (((ix + 1) % px) + px) % px
                    sx[i] = f * f * (3 - 2 * f)
                }
            }
        }
        val o1 = Octave(nx, ny, seed)
        val o2 = Octave(nx * 2, ny * 2, seed + 17)
        val pixels = IntArray(wPx * hPx)
        for (j in 0 until hPx) {
            val v = (j + 0.5) / hPx
            val y1 = v * o1.py
            val iy1 = floor(y1).toInt()
            val f1 = y1 - iy1
            val sy1 = f1 * f1 * (3 - 2 * f1)
            val r10 = (((iy1 % o1.py) + o1.py) % o1.py) * o1.px
            val r11 = ((((iy1 + 1) % o1.py) + o1.py) % o1.py) * o1.px
            val y2 = v * o2.py
            val iy2 = floor(y2).toInt()
            val f2 = y2 - iy2
            val sy2 = f2 * f2 * (3 - 2 * f2)
            val r20 = (((iy2 % o2.py) + o2.py) % o2.py) * o2.px
            val r21 = ((((iy2 + 1) % o2.py) + o2.py) % o2.py) * o2.px
            for (i in 0 until wPx) {
                val l1 = o1.lat
                val a1 = l1[r10 + o1.x0[i]]
                val b1 = l1[r10 + o1.x1[i]]
                val c1 = l1[r11 + o1.x0[i]]
                val d1 = l1[r11 + o1.x1[i]]
                val s1 = o1.sx[i]
                val n1 = (a1 + (b1 - a1) * s1) * (1 - sy1) + (c1 + (d1 - c1) * s1) * sy1
                val l2 = o2.lat
                val a2 = l2[r20 + o2.x0[i]]
                val b2 = l2[r20 + o2.x1[i]]
                val c2 = l2[r21 + o2.x0[i]]
                val d2 = l2[r21 + o2.x1[i]]
                val s2 = o2.sx[i]
                val n2 = (a2 + (b2 - a2) * s2) * (1 - sy2) + (c2 + (d2 - c2) * s2) * sy2
                val n = n1 * 0.67 + n2 * 0.33
                val g = ((0.5 + (n - 0.5) * 1.6).coerceIn(0.0, 1.0) * 255).roundToInt()
                pixels[j * wPx + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        bitmap = Bitmap.createBitmap(pixels, wPx, hPx, Bitmap.Config.ARGB_8888)
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        }
    }

    /** Lays the grain over [rect] with `overlay` at [alpha]. */
    fun draw(c: Canvas, rect: RectF, alpha: Double, paint: Paint) {
        paint.reset()
        paint.shader = shader
        paint.alpha = (255 * alpha).roundToInt()
        paint.xfermode = PianoKeyGfx.overlayMode
        c.drawRect(rect, paint)
    }
}

internal class PianoKeyFonts {
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weight(Typeface.DEFAULT, 600); textSize = 17f }
    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = 13f }
    val note = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = 46f }
    val meter = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = 15f }

    companion object {
        fun weight(base: Typeface, w: Int): Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, w, false)
            else Typeface.create(base, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}

/** The static layers, baked once off the main thread. */
internal class PianoKeyLayers(val background: Bitmap, val overlay: Bitmap)

internal class PianoKeyGrains {
    /** Fine grain everywhere (cells ≈ 1.1 dp), tiling 128 dp. */
    val fine = PianoKeyGrain(128, 1.1, 1.1, 3)
    /** Felt nap: a little coarser across. */
    val felt = PianoKeyGrain(128, 0.64, 1.1, 11)
    /** Ivory: long faint streaks along the key. */
    val ivory = PianoKeyGrain(128, 1.1, 25.0, 5, 250)
}

internal object PianoKeyButtonArt {
    private fun bake(w: Float, h: Float, scale: Float, ox: Float, oy: Float, draw: (Canvas) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, ceil(w * scale).toInt()), max(1, ceil(h * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-ox, -oy)
        draw(c)
        return bmp
    }

    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) = c.drawRoundRect(x, y, x + w, y + h, r, r, p)

    fun bakeLayers(s: PianoKeyScene, fonts: PianoKeyFonts, grains: PianoKeyGrains, scale: Float): PianoKeyLayers {
        val g = PianoKeyGfx().apply { liveBlur = true }
        val grainPaint = Paint()
        val background = bake(s.w, s.h, scale, 0f, 0f) { c -> background(c, s, fonts, grains, g, grainPaint) }
        val r = s.overlay
        val overlay = bake(r.width(), r.height(), scale, r.left, r.top) { c -> overlay(c, s, grains, g, grainPaint) }
        return PianoKeyLayers(background, overlay)
    }

    /** Lacquer, card, fallboard and recess: everything behind the moving parts. */
    private fun background(c: Canvas, s: PianoKeyScene, fonts: PianoKeyFonts, grains: PianoKeyGrains, g: PianoKeyGfx, gp: Paint) {
        val w = s.w
        val h = s.h
        val full = RectF(0f, 0f, w, h)
        // Ebony lacquer with a warm pool of light where the keys are, a vignette and grain.
        c.drawRect(full, g.shaded(g.linear(0f, 0f, 0f, h, intArrayOf(pianoRgb(0x1E1612), pianoRgb(0x18110E), pianoRgb(0x130E0C), pianoRgb(0x0F0B0A)), floatArrayOf(0f, 0.35f, 0.75f, 1f))))
        c.drawRect(full, g.shaded(g.radial(w * 0.5f, s.keyTop + 40, h * 0.6f, intArrayOf(pianoRgba(120, 74, 38, 0.30), pianoRgba(70, 40, 22, 0.12), 0), floatArrayOf(0f, 0.45f, 1f))))
        c.drawRect(full, g.shaded(g.radial(w * 0.5f, h * 0.48f, h * 0.75f, intArrayOf(0, pianoRgba(4, 2, 1, 0.18), pianoRgba(4, 2, 1, 0.55)), floatArrayOf(0.45f, 0.78f, 1f))))
        grains.fine.draw(c, full, 0.07, gp)

        // The lesson card: two shadows, a three-stop body, a hairline highlight on top.
        rr(c, s.cardX, s.cardY + 14, s.cardW, s.cardH, 20f, g.blurred(0xFF000000.toInt(), 0.42, 16f))
        rr(c, s.cardX, s.cardY + 1.5f, s.cardW, s.cardH, 20f, g.blurred(0xFF000000.toInt(), 0.5, 1.5f))
        rr(c, s.cardX, s.cardY, s.cardW, s.cardH, 20f, g.shaded(g.linear(0f, s.cardY, 0f, s.cardY + s.cardH, intArrayOf(pianoRgb(0x33271F), pianoRgb(0x2A2019), pianoRgb(0x231A15)))))
        c.save()
        val card = Path().apply { addRoundRect(RectF(s.cardX, s.cardY, s.cardX + s.cardW, s.cardY + s.cardH), 20f, 20f, Path.Direction.CW) }
        c.clipPath(card)
        grains.fine.draw(c, RectF(s.cardX, s.cardY, s.cardX + s.cardW, s.cardY + s.cardH), 0.08, gp)
        c.restore()
        val edge = g.paint(pianoRgb(0xFFE7C8), 0.12).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        rr(c, s.cardX + 0.5f, s.cardY + 0.5f, s.cardW - 1, s.cardH - 1, 19.5f, edge)

        // The stave: five lines and a common-time sign.
        val line = g.paint(pianoRgb(0xE9D9C2), 0.32).apply { strokeWidth = 1f }
        for (i in 0 until 5) {
            val y = s.staffY - i * s.staffGap
            c.drawLine(s.staffL, y, s.staffR, y, line)
        }
        val bar = g.paint(pianoRgb(0xE9D9C2), 0.32).apply { strokeWidth = 1.2f }
        c.drawLine(s.staffR, s.staffY, s.staffR, s.staffY - 4 * s.staffGap, bar)
        fonts.meter.color = pianoRgba(0xE9, 0xD9, 0xC2, 0.5)
        c.drawText("4", s.staffL + 2, s.staffY - 2 * s.staffGap - 1, fonts.meter)
        c.drawText("4", s.staffL + 2, s.staffY + 0.5f, fonts.meter)

        // Fallboard rail: lacquer with a gold pinstripe, then the dark recess with the hammers' rail.
        val railY = s.recessTop - 14
        c.drawRect(0f, railY, w, railY + 14, g.shaded(g.linear(0f, railY, 0f, s.recessTop, intArrayOf(pianoRgb(0x2B211C), pianoRgb(0x1A1311), pianoRgb(0x100C0A)))))
        c.drawLine(0f, railY + 0.5f, w, railY + 0.5f, g.paint(pianoRgb(0xFFDDB0), 0.16))
        val pin = g.paint(pianoRgb(0xC9A15E), 0.55).apply { strokeWidth = 1f }
        c.drawLine(10f, railY + 7, w - 10, railY + 7, pin)
        c.drawRect(0f, s.recessTop, w, s.feltY, g.shaded(g.linear(0f, s.recessTop, 0f, s.feltY, intArrayOf(pianoRgb(0x080605), pianoRgb(0x140E0B), pianoRgb(0x1E1611)))))
        c.drawRect(0f, s.recessTop, w, s.recessTop + 8, g.blurred(0xFF000000.toInt(), 0.7, 4f))
        // A brass rail the strings run over, across the back of the recess.
        c.drawRect(0f, s.recessTop + 3, w, s.recessTop + 8, g.shaded(g.linear(0f, s.recessTop + 3, 0f, s.recessTop + 8, intArrayOf(pianoRgb(0x7C5B2E), pianoRgb(0xD8B678), pianoRgb(0x6A4B24)))))

        // The keybed floor in the gaps, and the keyslip under the keys.
        c.drawRect(0f, s.keyTop, w, s.keyBottom + 8, g.shaded(g.linear(0f, s.keyTop, 0f, s.keyBottom, intArrayOf(pianoRgb(0x0C0908), pianoRgb(0x130E0C), pianoRgb(0x0E0A09)))))
        val slipY = s.keyBottom + 8
        c.drawRect(0f, slipY, w, slipY + 14, g.shaded(g.linear(0f, slipY, 0f, slipY + 14, intArrayOf(pianoRgb(0x2E231D), pianoRgb(0x1C1512), pianoRgb(0x140F0D)))))
        c.drawLine(0f, slipY + 0.5f, w, slipY + 0.5f, g.paint(pianoRgb(0xFFE2BC), 0.14))
    }

    /** Felt strip and black keys: what sits in front of the hammers and over the ivory keys. */
    private fun overlay(c: Canvas, s: PianoKeyScene, grains: PianoKeyGrains, g: PianoKeyGfx, gp: Paint) {
        val w = s.w
        // Burgundy felt with a fuzzy nap and a soft shadow it throws onto the keys.
        val feltRect = RectF(0f, s.feltY, w, s.feltY + s.feltH)
        c.drawRect(feltRect, g.shaded(g.linear(0f, s.feltY, 0f, s.feltY + s.feltH, intArrayOf(pianoRgb(0x7A2433), pianoRgb(0x5E1A27), pianoRgb(0x45121D)))))
        c.save()
        c.clipRect(feltRect)
        grains.felt.draw(c, feltRect, 0.32, gp)
        c.restore()
        c.drawLine(0f, s.feltY + 0.5f, w, s.feltY + 0.5f, g.paint(pianoRgb(0xFFB3BE), 0.22))
        // Black keys sit in the gaps, over the upper half of the ivory keys.
        val bw = min(30f, s.keyW * 0.26f)
        val bh = (s.keyBottom - s.keyTop) * 0.52f
        for (i in 0 until 2) {
            val cx = s.keyX[i + 1] - s.gap / 2
            val x = cx - bw / 2
            val y = s.keyTop - 2
            // Two shadows on the ivory: tight contact and soft ambient, both falling down-screen.
            rr(c, x - 3, y + 10, bw + 6, bh + 6, 6f, g.blurred(pianoRgb(0x1A0E06), 0.38, 9f))
            rr(c, x - 0.5f, y, bw + 1, bh + 2, 4f, g.blurred(0xFF000000.toInt(), 0.6, 1.5f))
            rr(c, x, y, bw, bh, 4f, g.shaded(g.linear(x, 0f, x + bw, 0f, intArrayOf(pianoRgb(0x18130F), pianoRgb(0x2C2520), pianoRgb(0x1F1A16), pianoRgb(0x110E0C)), floatArrayOf(0f, 0.3f, 0.7f, 1f))))
            // The sloped front where a finger lands, catching a little light.
            rr(c, x + 2, y + bh - 18, bw - 4, 16f, 3f, g.shaded(g.linear(0f, y + bh - 18, 0f, y + bh, intArrayOf(pianoRgb(0x2A231E), pianoRgb(0x3D342D), pianoRgb(0x231D19)))))
            // Gloss: a long specular strip down the left of the top face.
            rr(c, x + bw * 0.22f, y + 4, bw * 0.16f, bh - 26, 2f, g.shaded(g.linear(0f, y, 0f, y + bh - 20, intArrayOf(pianoRgba(255, 236, 214, 0.20), pianoRgba(255, 236, 214, 0.07), pianoRgba(255, 236, 214, 0.0)))))
        }
    }
}

/** Draws the moving parts each frame; holds its reusable paints and paths. */
internal class PianoKeyPainter(private val fonts: PianoKeyFonts, private val grains: PianoKeyGrains) {
    private val g = PianoKeyGfx()
    private val grainPaint = Paint()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val keyPath = Path()
    private val src = Rect()
    private val rect = RectF()
    private val oval = RectF()

    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) = c.drawRoundRect(x, y, x + w, y + h, r, r, p)

    /** One frame: the baked layers with the moving parts between and over them. */
    fun frame(c: Canvas, s: PianoKeyScene, layers: PianoKeyLayers, pose: PianoKeyButtonMotion.Pose) {
        src.set(0, 0, layers.background.width, layers.background.height)
        rect.set(0f, 0f, s.w, s.h)
        c.drawBitmap(layers.background, src, rect, imagePaint)
        notes(c, s, pose)
        c.save()
        c.clipRect(0f, s.recessTop, s.w, s.feltY)
        hammers(c, s, pose)
        c.restore()
        for (i in pose.keys.indices) keyShadow(c, s, pose.keys[i], i)
        for (i in pose.keys.indices) key(c, s, i, pose.keys[i])
        src.set(0, 0, layers.overlay.width, layers.overlay.height)
        c.drawBitmap(layers.overlay, src, s.overlay, imagePaint)
        rings(c, s, pose)
    }

    /** One ivory key's outline: square under the felt, rounded at the front, narrowing as it pivots. */
    private fun keyOutline(x: Float, top: Float, w: Float, bottom: Float, inset: Float): Path {
        val r = 7f
        keyPath.rewind()
        keyPath.moveTo(x, top)
        keyPath.lineTo(x + w, top)
        keyPath.lineTo(x + w - inset, bottom - r)
        keyPath.quadTo(x + w - inset, bottom, x + w - inset - r, bottom)
        keyPath.lineTo(x + inset + r, bottom)
        keyPath.quadTo(x + inset, bottom, x + inset, bottom - r)
        keyPath.close()
        return keyPath
    }

    private fun travel(k: PianoKeyButtonMotion.Key) = max(-0.1, min(1.15, k.depth)).toFloat()

    /** Ambient shadow on the keyslip, smaller and tighter as the key goes down. */
    private fun keyShadow(c: Canvas, s: PianoKeyScene, k: PianoKeyButtonMotion.Key, i: Int) {
        val p = travel(k)
        val x = s.keyX[i]
        val bottom = s.keyBottom - 5 * p
        val inset = 3.2f * p
        val lip = 7 - 4 * p
        rr(c, x + 4 + inset, bottom + lip - 2, s.keyW - 8 - inset * 2, 10 - 4 * p, 5f, g.blurred(0xFF000000.toInt(), 0.55 - 0.15 * p, 6 - 2 * p))
    }

    private fun key(c: Canvas, s: PianoKeyScene, i: Int, k: PianoKeyButtonMotion.Key) {
        val p = travel(k)
        val x = s.keyX[i]
        val w = s.keyW
        // Pivoting down 6° around the far edge: the near end drops away, so it foreshortens and narrows.
        val bottom = s.keyBottom - 5 * p
        val inset = 3.2f * p
        val lip = 7 - 4 * p
        val glow = PianoKeyButtonMotion.keyGlow(k)

        // Front lip (the key's front face), darker ivory.
        rr(c, x + inset, bottom - 8, w - inset * 2, 8 + lip, 7f, g.shaded(g.linear(0f, bottom - 8, 0f, bottom + lip, intArrayOf(pianoRgb(0xD8C8AB), pianoRgb(0xC9B693), pianoRgb(0xAE9A78)))))

        val path = keyOutline(x, s.keyTop, w, bottom, inset)
        c.drawPath(path, g.shaded(g.linear(0f, s.keyTop, 0f, bottom, intArrayOf(pianoRgb(0xF7F0DF), pianoRgb(0xF2E8D3), pianoRgb(0xECE0C8), pianoRgb(0xE3D5B8)), floatArrayOf(0f, 0.4f, 0.8f, 1f))))
        c.save()
        c.clipPath(path)
        rect.set(x, s.keyTop, x + w, bottom)
        // Ivory grain, side shading (a highlight on the left edge, the right edge in shade).
        grains.ivory.draw(c, rect, 0.1, grainPaint)
        c.drawRect(rect, g.shaded(g.linear(x, 0f, x + w, 0f, intArrayOf(pianoRgba(255, 255, 250, 0.55), pianoRgba(255, 255, 250, 0.0), pianoRgba(80, 56, 30, 0.0), pianoRgba(80, 56, 30, 0.16)), floatArrayOf(0f, 0.08f, 0.86f, 1f))))
        // Going down it leaves the light: darker toward the front, and the neighbours shade its sides.
        if (p > 0.01f) {
            c.drawRect(rect, g.shaded(g.linear(0f, s.keyTop, 0f, bottom, intArrayOf(pianoRgba(60, 38, 18, 0.0), pianoRgba(60, 38, 18, 0.11 * p)))))
            val a = 0.16 * p
            c.drawRect(rect, g.shaded(g.linear(x, 0f, x + w, 0f, intArrayOf(pianoRgba(40, 24, 10, a), pianoRgba(40, 24, 10, 0.0), pianoRgba(40, 24, 10, 0.0), pianoRgba(40, 24, 10, a)), floatArrayOf(0f, 0.1f, 0.9f, 1f))))
        }
        // The felt's shadow across the top tightens as the key's far end lifts toward it.
        c.drawRect(x, s.keyTop, x + w, s.keyTop + 18, g.shaded(g.linear(0f, s.keyTop, 0f, s.keyTop + 16 - 7 * p, intArrayOf(pianoRgba(40, 10, 14, 0.42 + 0.12 * p), pianoRgba(40, 10, 14, 0.0)))))
        // A warm wash of the key's colour while it sounds.
        if (glow > 0.005) {
            // Multiply as in Skia. PorterDuff.Mode.MULTIPLY also multiplies alpha, so where the
            // gradient is clear it punched the key out to black: use the separable blend mode, and
            // below API 29 lay the colour already multiplied by the ivory over it.
            val a = clamp01(glow * 0.55)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wash = g.shaded(g.radial(x + w / 2, bottom - 60, w * 1.1f, intArrayOf(PianoKeyColors[i], 0)), a)
                wash.blendMode = BlendMode.MULTIPLY
                c.drawRect(rect, wash)
            } else {
                val tint = PianoKeyGfx.onIvory(PianoKeyColors[i])
                c.drawRect(rect, g.shaded(g.radial(x + w / 2, bottom - 60, w * 1.1f, intArrayOf(tint, tint and 0x00FFFFFF)), a))
            }
        }
        // Labels roll: the CTA leaves upward and fades, the note name comes up from below.
        val cx = x + w / 2
        val base = bottom - 44
        c.clipRect(x + 4, base - 50, x + w - 4, base + 26)
        val r = clamp01(k.label).toFloat()
        val scaleY = 1 - 0.02f * p
        if (r < 0.999f) {
            val up = -30 * r
            val t = PianoKeyLabels[i]
            val a = clamp01(1.0 - r * 1.7)
            fonts.title.color = INK
            fonts.title.alpha = (255 * a).roundToInt()
            c.drawText(t.title, cx - fonts.title.measureText(t.title) / 2, base - 8 + up, fonts.title)
            fonts.sub.color = INK_SOFT
            fonts.sub.alpha = (255 * a).roundToInt()
            c.drawText(t.sub, cx - fonts.sub.measureText(t.sub) / 2, base + 12 + up, fonts.sub)
        }
        if (r > 0.001f) {
            val n = PianoKeyLabels[i].note
            fonts.note.color = INK
            fonts.note.alpha = (255 * clamp01(r * 1.7 - 0.7)).roundToInt()
            c.save()
            c.translate(cx, base + 8 + 44 * (1 - r))
            c.scale(1f, scaleY)
            c.drawText(n, -fonts.note.measureText(n) / 2, 0f, fonts.note)
            c.restore()
        }
        c.restore()
        // A small marker of the key's colour above the label.
        c.drawCircle(cx, base - 64, 3.5f, g.paint(PianoKeyColors[i], 0.9))
    }

    private fun hammers(c: Canvas, s: PianoKeyScene, pose: PianoKeyButtonMotion.Pose) {
        for (i in pose.keys.indices) {
            val k = pose.keys[i]
            val cx = s.keyX[i] + s.keyW / 2
            // Strings: a unison of three per key, brightening and thickening while they ring.
            val ring = PianoKeyButtonMotion.stringRing(k)
            val sp = g.paint(pianoRgb(0xD9B97F), 0.28 + 0.6 * ring).apply {
                strokeWidth = (0.8 + 1.2 * ring).toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            for (j in -1..1) {
                val x = cx + j * 5
                c.drawLine(x, s.recessTop + 4, x, s.feltY, sp)
            }
            if (ring > 0.02) c.drawRect(cx - 9, s.recessTop + 4, cx + 9, s.feltY, g.blurred(PianoKeyColors[i], 0.35 * ring, 5f))
            // The hammer: walnut molding with a cream felt head, flicking up toward the strings.
            val lift = PianoKeyButtonMotion.hammerLift(k.downAge).toFloat()
            val hw = 40f
            val hh = 26f
            val y = s.feltY - 16 - 14 * lift
            rr(c, cx - hw / 2, y + 6, hw, hh, 9f, g.blurred(0xFF000000.toInt(), 0.55, 4 + 2 * lift))
            rr(c, cx - hw / 2, y, hw, hh, 9f, g.shaded(g.linear(cx - hw / 2, 0f, cx + hw / 2, 0f, intArrayOf(pianoRgb(0x4A3121), pianoRgb(0x7A5236), pianoRgb(0x3C281B)))))
            rr(c, cx - hw / 2 + 3, y + 1, hw - 6, 12f, 6f, g.shaded(g.linear(0f, y, 0f, y + 12, intArrayOf(pianoRgb(0xF1E6D2), pianoRgb(0xDCCDB3), pianoRgb(0xBBA88A)))))
        }
    }

    private fun rings(c: Canvas, s: PianoKeyScene, pose: PianoKeyButtonMotion.Pose) {
        val life = PianoKeyButtonMotion.RING_LIFE
        val delay = PianoKeyButtonMotion.RING_DELAY
        for (i in pose.keys.indices) {
            val k = pose.keys[i]
            if (k.downAge > life + delay * 2) continue
            val cx = s.keyX[i] + s.keyW / 2
            val cy = s.keyTop + (s.keyBottom - s.keyTop) * 0.62f
            for (j in 0 until 3) {
                val age = k.downAge - j * delay
                if (age <= 0 || age >= life) continue
                val u = age / life
                val radius = 22 + s.w * 0.85f * easeOut(u).toFloat()
                val fade = (1 - u).pow(1.6) * (1 - j * 0.22)
                val glow = g.blurred(PianoKeyColors[i], 0.5 * fade, 7f).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 10f
                    xfermode = PianoKeyGfx.screenMode
                }
                c.drawCircle(cx, cy, radius, glow)
                val line = g.paint(PianoKeyColors[i], 0.95 * fade).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (3 - 2 * u).toFloat()
                    xfermode = PianoKeyGfx.screenMode
                }
                c.drawCircle(cx, cy, radius, line)
            }
        }
    }

    private val heads = arrayOf(intArrayOf(-2), intArrayOf(0), intArrayOf(2), intArrayOf(-2, 2))

    private fun notes(c: Canvas, s: PianoKeyScene, pose: PianoKeyButtonMotion.Pose) {
        val gap = s.staffGap
        val x0 = s.staffL + 30
        val step = (s.staffR - 14 - x0) / 3
        // Steps above the bottom line (E4 = 0): C4 = -2, E4 = 0, G4 = 2; the chord stacks C4 and G4.
        val colors = intArrayOf(PianoKeyColors[0], PianoKeyColors[1], PianoKeyColors[2], PianoKeyColors[0])
        val paper = pianoRgb(0xE9D9C2)
        for (n in 0 until 4) {
            val x = x0 + step * n
            val lit = clamp01(pose.notes[n])
            for (st in heads[n]) {
                val y = s.staffY - st * gap / 2
                if (st == -2) c.drawLine(x - 8, y, x + 8, y, g.paint(paper, 0.45).apply { strokeWidth = 1f })
                val col = if (n == 3 && st == 2) PianoKeyColors[2] else colors[n]
                if (lit > 0.01) c.drawCircle(x, y, 10f, g.blurred(col, 0.55 * lit, 6f))
                c.save()
                c.rotate(-20f, x, y)
                oval.set(x - 5, y - 3.6f, x + 5, y + 3.6f)
                c.drawOval(oval, g.paint(if (lit > 0.5) col else paper, 0.5 + 0.5 * lit))
                c.restore()
            }
            val topStep = heads[n].last()
            val stem = g.paint(if (lit > 0.5) colors[n] else paper, 0.5 + 0.5 * lit).apply { strokeWidth = 1.2f }
            val sy = s.staffY - topStep * gap / 2
            c.drawLine(x + 4.6f, sy - 1, x + 4.6f, sy - 24, stem)
        }
    }
}
