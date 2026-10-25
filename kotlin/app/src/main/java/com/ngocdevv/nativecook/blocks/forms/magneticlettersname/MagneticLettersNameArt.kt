package com.ngocdevv.nativecook.blocks.forms.magneticlettersname

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asComposePath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Magnetic Letters Name — everything drawn once, off the main thread, when the size is known:
 - the fridge door (brushed white steel, reflections, the freezer seam, two chrome handles, the
   engraved name line) as one screen-sized bitmap;
 - an atlas of the magnets' faces (glossy plastic with a 3 dp extruded side, a bevel and a gloss
   spot), one cell per magnet, at the screen's pixel ratio;
 - an atlas of glyph-shaped shadows, four per letter (contact → lifted: more blur, less alpha).
 A frame then only places sprites (two bitmap draws per magnet).
*/

/** The letters on the door: A–Z and a spare E and A. */
private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZEA"

/** Plastic colours: light (top of the face), base, deep (bottom), side (the extrusion). */
private val PLASTIC = arrayOf(
    intArrayOf(0xFFFF8A7A.toInt(), 0xFFE8483C.toInt(), 0xFFBC2C26.toInt(), 0xFF8A1D1A.toInt()),
    intArrayOf(0xFFFFBE6E.toInt(), 0xFFF28A2E.toInt(), 0xFFCA641B.toInt(), 0xFF954310.toInt()),
    intArrayOf(0xFFFFE893.toInt(), 0xFFF5C232.toInt(), 0xFFD59A16.toInt(), 0xFF9F6F0B.toInt()),
    intArrayOf(0xFF86D896.toInt(), 0xFF3FA45B.toInt(), 0xFF2A7B43.toInt(), 0xFF1C5630.toInt()),
    intArrayOf(0xFF86B9FF.toInt(), 0xFF3076D2.toInt(), 0xFF1F53A2.toInt(), 0xFF163B74.toInt()),
    intArrayOf(0xFFC29DF4.toInt(), 0xFF8858CB.toInt(), 0xFF603C9C.toInt(), 0xFF452A71.toInt()),
)

/** The loop's name gets its own colours (red, yellow, blue, green) so it reads as a set. */
private val SCRIPT_COLORS = intArrayOf(0, 2, 4, 3)

/** Shadow levels: blur sigma and alpha from resting flat on the steel (0) to held up (3). */
private val SHADOW_SIGMA = floatArrayOf(1.4f, 3.2f, 5.6f, 8.5f)
private val SHADOW_ALPHA = floatArrayOf(0.46f, 0.32f, 0.23f, 0.17f)
internal const val MAG_SHADOW_PX = 1.5f
internal const val MAG_SHADOW_LEVELS = 4
internal const val MAG_FACE_COLS = 7

internal class MagMagnet(val letter: Char, val color: Int, var home: MagPose, val w: Float, val r: Float)

internal class MagPile(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

internal class MagLayout(
    val w: Float,
    val h: Float,
    val seamY: Float,
    val lineY: Float,
    val letterY: Float,
    val lineX0: Float,
    val lineX1: Float,
    val pile: MagPile,
    val magnets: List<MagMagnet>,
    /** Index of the magnet each letter of the script uses. */
    val script: IntArray,
    /** Base draw order: higher on the pile first, so lower magnets overlap them. */
    val order: IntArray,
    val cell: Float,
    val shadowCell: Float,
    val capH: Float,
    val gap: Float,
)

/** The layout and its three baked bitmaps. */
internal class MagArt(
    val layout: MagLayout,
    val px: Float,
    val door: Bitmap,
    val faces: Bitmap,
    val shadows: Bitmap,
    val maxFit: Int,
    val scriptOf: IntArray,
    val autoSlots: List<MagPose>,
)

private fun hash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

private fun hash(n: Int) = hash(n.toDouble())

private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255).roundToInt(), r, g, b)

private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).roundToInt() shl 24)

private fun fill(color: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = if (alpha < 1f) withAlpha(color, alpha) else color
}

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, colors, pos, Shader.TileMode.CLAMP) }

/** A Gaussian blur of [sigma] (Skia's sigma, as in React Native) on a paint. */
private fun Paint.blur(sigma: Float): Paint {
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
    maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    return this
}

private fun Path.tight(): RectF {
    val b = asComposePath().getBounds()
    return RectF(b.left, b.top, b.right, b.bottom)
}

/** A heavy sans for the magnets (React Native on Android uses bold sans-serif). */
private fun glyphPaths(size: Float): List<Path> {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = size
    }
    return LETTERS.map { ch ->
        Path().also { paint.getTextPath(ch.toString(), 0, 1, 0f, 0f, it) }
    }
}

private class MagFrame(val seamY: Float, val lineY: Float, val pile: MagPile, val s: Float)

private fun frame(w: Float, h: Float, top: Float, bottom: Float): MagFrame {
    val seamY = top + 152
    val lineY = seamY + 128
    val pile = MagPile(22f, lineY + 46, w - 50, h - bottom - 104)
    val s = max(0.74f, min(1f, min(w / 393f, (pile.y1 - pile.y0) / 330f)))
    return MagFrame(seamY, lineY, pile, s)
}

private fun makeLayout(w: Float, h: Float, top: Float, bottom: Float, paths: List<Path>): MagLayout {
    val f = frame(w, h, top, bottom)
    val pile = f.pile
    val capH = paths[LETTERS.indexOf('H')].tight().height()
    val letterY = f.lineY - 5 - capH / 2
    val cell = ceil(capH * 1.9f)
    val shadowCell = cell + 28

    val script = IntArray(MAG_SCRIPT.length) { -1 }
    val bounds = paths.map { it.tight() }
    val magnets = LETTERS.mapIndexed { i, ch ->
        val b = bounds[i]
        val k = MAG_SCRIPT.indexOf(ch)
        val isScript = k >= 0 && script[k] == -1
        if (isScript) script[k] = i
        MagMagnet(
            letter = ch,
            color = if (isScript) SCRIPT_COLORS[k] else (i * 5 + 1) % PLASTIC.size,
            home = MagPose(),
            w = b.width() + 4,
            r = max(b.width(), capH) * 0.58f,
        )
    }

    // A loose heap: a jittered grid, then pushed apart a few times so magnets overlap only at
    // their edges. Seeded, so the heap is the same on every launch.
    val n = magnets.size
    val cols = 6
    val rows = ceil(n / cols.toFloat()).toInt()
    val cw = (pile.x1 - pile.x0) / cols
    val ch = (pile.y1 - pile.y0) / rows
    val slots = (0 until cols * rows).sortedBy { hash(it * 7.3) }
    val px = FloatArray(n)
    val py = FloatArray(n)
    for (i in 0 until n) {
        val slot = slots[i]
        val c = slot % cols
        val r = slot / cols
        px[i] = pile.x0 + ((c + 0.5 + (hash(i * 3.1) - 0.5) * 0.6) * cw).toFloat()
        py[i] = pile.y0 + ((r + 0.5 + (hash(i * 5.7) - 0.5) * 0.55) * ch).toFloat()
    }
    val minD = capH * 0.95f
    repeat(6) {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = px[j] - px[i]
                val dy = py[j] - py[i]
                val d = sqrt(dx * dx + dy * dy).let { if (it == 0f) 1f else it }
                if (d < minD) {
                    val push = (minD - d) / 2
                    px[i] -= dx / d * push; py[i] -= dy / d * push
                    px[j] += dx / d * push; py[j] += dy / d * push
                }
            }
        }
        for (i in 0 until n) {
            px[i] = min(pile.x1 - capH * 0.5f, max(pile.x0 + capH * 0.5f, px[i]))
            py[i] = min(pile.y1 - capH * 0.4f, max(pile.y0 + capH * 0.5f, py[i]))
        }
    }
    magnets.forEachIndexed { i, m ->
        m.home = MagPose(px[i], py[i], ((hash(i * 9.1) - 0.5) * 0.9).toFloat(), 0f, 1f)
    }
    val order = (0 until n).sortedBy { magnets[it].home.y }.toIntArray()
    return MagLayout(w, h, f.seamY, f.lineY, letterY, 30f, w - 72, pile, magnets, script, order, cell, shadowCell, capH, 5f)
}

private fun slotXs(l: MagLayout, name: IntArray): FloatArray {
    val xs = FloatArray(name.size)
    var x = l.lineX0
    for (k in name.indices) {
        val w = l.magnets[name[k]].w
        xs[k] = x + w / 2
        x += w + l.gap
    }
    return xs
}

private fun maxLettersFit(l: MagLayout) =
    min(MAG_MAX_LETTERS, floor((l.lineX1 - l.lineX0) / (l.capH * 0.95f + l.gap)).toInt())

private fun bitmap(w: Float, h: Float, draw: (Canvas) -> Unit): Bitmap {
    val b = Bitmap.createBitmap(max(1, ceil(w).toInt()), max(1, ceil(h).toInt()), Bitmap.Config.ARGB_8888)
    draw(Canvas(b))
    b.prepareToDraw()
    return b
}

/** Lays the door out for a [w] × [h] dp screen and bakes its bitmaps; call off the main thread. */
internal fun magBakeArt(w: Float, h: Float, top: Float, bottom: Float, density: Float): MagArt {
    val s = frame(w, h, top, bottom).s
    val paths = glyphPaths(52 * s)
    val l = makeLayout(w, h, top, bottom, paths)
    val px = min(3f, density)
    val xs = slotXs(l, l.script)
    val scriptOf = IntArray(l.magnets.size) { i -> l.script.indexOf(i) }
    val autoSlots = l.script.indices.map { k -> MagPose(xs[k], l.letterY, 0f, 0f, 1f) }
    return MagArt(
        layout = l,
        px = px,
        door = bakeDoor(l, top, px),
        faces = bakeFaces(l, paths, px),
        shadows = bakeShadows(l, paths),
        maxFit = maxLettersFit(l),
        scriptOf = scriptOf,
        autoSlots = autoSlots,
    )
}

/** The fridge door, the size of the screen, in pixels (scaled by [px]). */
private fun bakeDoor(l: MagLayout, top: Float, px: Float): Bitmap {
    val w = l.w
    val h = l.h
    val seamY = l.seamY
    val lineY = l.lineY
    return bitmap(w * px, h * px) { c ->
        c.scale(px, px)
        // Steel: a soft fall-off from the kitchen light above.
        c.drawRect(0f, 0f, w, h, linear(0f, 0f, 0f, h, intArrayOf(0xFFF6F6F3.toInt(), 0xFFE8E9E5.toInt(), 0xFFDFE1DD.toInt(), 0xFFD7D9D5.toInt()), floatArrayOf(0f, 0.35f, 0.7f, 1f)))
        // Brushing: thousands of horizontal hairlines, a little lighter or darker than the steel.
        val hair = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in 0 until (h * 3).toInt()) {
            val y = (hash(k + 1) * h).toFloat()
            val x0 = (hash(k * 3 + 2) * w * 0.8 - 40).toFloat()
            val len = (40 + hash(k * 7 + 3) * w * 0.9).toFloat()
            val light = hash(k * 11 + 4) > 0.45
            val a = (0.05 + hash(k * 13 + 5) * (if (light) 0.16 else 0.06)).toFloat()
            hair.color = if (light) argb(a, 255, 255, 255) else argb(a, 0x8E, 0x92, 0x90)
            c.drawRect(x0, y, x0 + len, y + 0.4f, hair)
        }
        // Reflections: a cool window light from the left and a broad soft band across the upper door.
        c.drawRect(0f, 0f, w * 0.4f, h, linear(0f, 0f, w * 0.4f, 0f, intArrayOf(argb(0.42f, 196, 212, 228), argb(0.12f, 214, 224, 234), argb(0f, 230, 236, 240)), floatArrayOf(0f, 0.5f, 1f)))
        c.save()
        c.rotate(-16f, w / 2, h / 2)
        c.drawRect(-w, h * 0.18f, w * 2, h * 0.31f, fill(0xFFFFFFFF.toInt(), 0.5f).blur(34f))
        c.drawRect(-w, h * 0.36f, w * 2, h * 0.385f, fill(0xFFFFFFFF.toInt(), 0.4f).blur(8f))
        c.drawRect(-w, h * 0.62f, w * 2, h * 0.71f, fill(0xFFB9BDBA.toInt(), 0.16f).blur(30f))
        c.restore()
        // Warm bounce from the kitchen floor.
        c.drawRect(0f, h * 0.72f, w, h, linear(0f, h * 0.72f, 0f, h, intArrayOf(argb(0f, 232, 214, 190), argb(0.24f, 228, 206, 178)), null))
        // The door's rolled edges, left and right.
        c.drawRect(0f, 0f, 10f, h, linear(0f, 0f, 10f, 0f, intArrayOf(argb(0.28f, 120, 124, 122), argb(0.5f, 255, 255, 255), argb(0f, 255, 255, 255)), floatArrayOf(0f, 0.35f, 1f)))
        c.drawRect(w - 8, 0f, w, h, linear(w - 8, 0f, w, 0f, intArrayOf(argb(0f, 255, 255, 255), argb(0.3f, 110, 114, 112)), null))

        // The gap between the freezer and fridge doors: a dark seam, the freezer's shaded lower
        // lip, the fridge door's lit top edge.
        c.drawRect(0f, seamY - 14, w, seamY, linear(0f, seamY - 14, 0f, seamY, intArrayOf(argb(0f, 140, 144, 142), argb(0.32f, 120, 124, 122)), null))
        c.drawRect(0f, seamY, w, seamY + 5, linear(0f, seamY, 0f, seamY + 5, intArrayOf(0xFF55595A.toInt(), 0xFF2F3335.toInt(), 0xFF4A4E4F.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        c.drawRect(0f, seamY + 5, w, seamY + 6.2f, fill(0xFFFFFFFF.toInt(), 0.95f))
        c.drawRect(0f, seamY + 6, w, seamY + 24, linear(0f, seamY + 6, 0f, seamY + 24, intArrayOf(argb(0.55f, 255, 255, 255), argb(0f, 255, 255, 255)), null))

        // Chrome handles on the right: the freezer's short one, the fridge's long one (below the
        // name line, so the backspace key has the door to itself).
        handle(c, w - 25, top + 26, seamY - 26)
        handle(c, w - 25, lineY + 44, min(lineY + 300, h * 0.7f))

        // The name line: an engraved groove (dark line, light line under it) and the strip's soft shade.
        c.drawRect(18f, lineY - l.capH - 30, 18f + l.lineX1 - 4, lineY + 8, fill(0xFF9EA3A0.toInt(), 0.1f).blur(14f))
        val gw = l.lineX1 - l.lineX0 + 12
        c.drawRect(l.lineX0 - 6, lineY, l.lineX0 - 6 + gw, lineY + 1, fill(0xFF6A6F6E.toInt(), 0.5f))
        c.drawRect(l.lineX0 - 6, lineY + 1, l.lineX0 - 6 + gw, lineY + 2, fill(0xFFFFFFFF.toInt(), 0.95f))

        // Grain, so the large pale flat does not read as vector.
        val grainLight = fill(0xFFFFFFFF.toInt(), 0.07f)
        val grainDark = fill(0xFF6C706E.toInt(), 0.07f)
        for (k in 0 until 5200) {
            val x = (hash(k * 1.7 + 9) * w).toFloat()
            val y = (hash(k * 2.3 + 4) * h).toFloat()
            c.drawRect(x, y, x + 0.7f, y + 0.7f, if (hash(k * 3.9) > 0.5) grainLight else grainDark)
        }
    }
}

/** A chrome bar handle standing off the door on two posts, with its contact shadow. */
private fun handle(c: Canvas, x: Float, y0: Float, y1: Float) {
    val w = 12f
    c.drawRoundRect(RectF(x - 7, y0 + 6, x - 7 + w + 2, y0 + 6 + y1 - y0), 7f, 7f, fill(0xFF3A3E40.toInt(), 0.3f).blur(6f))
    c.drawRoundRect(RectF(x - 1, y0 + 2, x - 1 + w, y0 + 2 + y1 - y0), 6f, 6f, fill(0xFF3A3E40.toInt(), 0.28f).blur(1.6f))
    for (py in floatArrayOf(y0 + 8, y1 - 14)) {
        c.drawRoundRect(RectF(x - 2, py, x + w + 2, py + 8), 3f, 3f, linear(x - 2, 0f, x + w + 2, 0f, intArrayOf(0xFF8D9294.toInt(), 0xFFE9ECEC.toInt(), 0xFF6F7476.toInt()), floatArrayOf(0f, 0.45f, 1f)))
    }
    c.drawRoundRect(
        RectF(x, y0, x + w, y1), 6f, 6f,
        linear(x, 0f, x + w, 0f, intArrayOf(0xFF6E7375.toInt(), 0xFFC9CDCE.toInt(), 0xFFFBFCFC.toInt(), 0xFF9EA3A5.toInt(), 0xFF5C6163.toInt()), floatArrayOf(0f, 0.28f, 0.42f, 0.7f, 1f)),
    )
    val rim = fill(0xFFFFFFFF.toInt(), 0.6f).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f }
    c.drawRoundRect(RectF(x + 0.4f, y0 + 0.4f, x + w - 0.4f, y1 - 0.4f), 6f, 6f, rim)
}

/** Moves the canvas so the cap box of glyph [b] is centred on (cx, cy). */
private fun placeGlyph(c: Canvas, b: RectF, l: MagLayout, cx: Float, cy: Float) {
    c.translate(cx - (b.left + b.width() / 2), cy + l.capH / 2)
}

/** The magnets' faces: one `cell` square per magnet, MAG_FACE_COLS across, at [px] pixels per dp. */
private fun bakeFaces(l: MagLayout, paths: List<Path>, px: Float): Bitmap {
    val cols = MAG_FACE_COLS
    val rows = ceil(l.magnets.size / cols.toFloat()).toInt()
    val cell = l.cell
    return bitmap(cols * cell * px, rows * cell * px) { c ->
        c.scale(px, px)
        l.magnets.forEachIndexed { i, m ->
            val p = paths[i]
            val col = PLASTIC[m.color]
            val cx = (i % cols) * cell + cell / 2
            val cy = (i / cols) * cell + cell / 2
            val b = p.tight()
            c.save()
            placeGlyph(c, b, l, cx, cy)
            // The extruded side: the glyph stacked down and to the right, ~3 dp deep.
            for (k in 6 downTo 1) {
                c.save()
                c.translate(k * 0.3f, k * 0.5f)
                c.drawPath(p, if (k == 6) fill(col[3]) else linear(0f, b.top, 0f, b.bottom, intArrayOf(col[2], col[3]), null))
                c.restore()
            }
            // The face: three stops, lighter at the top where the room light lands.
            c.drawPath(p, linear(0f, b.top, 0f, b.bottom, intArrayOf(col[0], col[1], col[2]), floatArrayOf(0f, 0.45f, 1f)))
            c.save()
            c.clipPath(p)
            // Bevel: a bright inner edge along the top, a darker one along the bottom.
            val hi = fill(0xFFFFFFFF.toInt(), 0.5f).apply { style = Paint.Style.STROKE; strokeWidth = 3f }.blur(0.6f)
            c.save()
            c.translate(0f, 1.4f)
            c.drawPath(p, hi)
            c.restore()
            val lo = fill(col[3], 0.45f).apply { style = Paint.Style.STROKE; strokeWidth = 3f }.blur(0.8f)
            c.save()
            c.translate(0f, -1.4f)
            c.drawPath(p, lo)
            c.restore()
            // Gloss: a soft specular spot up and to the left, and a thin sharp glint inside it.
            val gx = b.left + b.width() * 0.32f
            val gy = b.top + b.height() * 0.22f
            c.drawOval(
                RectF(gx - b.width() * 0.45f, gy - b.height() * 0.2f, gx + b.width() * 0.45f, gy + b.height() * 0.2f),
                radial(gx, gy, max(0.1f, b.width() * 0.5f), intArrayOf(argb(0.62f, 255, 255, 255), argb(0.18f, 255, 255, 255), argb(0f, 255, 255, 255)), floatArrayOf(0f, 0.45f, 1f)),
            )
            c.drawOval(RectF(gx - b.width() * 0.16f, gy - 2.2f, gx + b.width() * 0.1f, gy + 1.2f), fill(0xFFFFFFFF.toInt(), 0.75f).blur(0.9f))
            c.restore()
            val rim = fill(col[3], 0.35f).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }
            c.drawPath(p, rim)
            c.restore()
        }
    }
}

/** Glyph-shaped shadows: `shadowCell` squares, one row per magnet, one column per lift level. */
private fun bakeShadows(l: MagLayout, paths: List<Path>): Bitmap {
    val cell = l.shadowCell
    val px = MAG_SHADOW_PX
    return bitmap(MAG_SHADOW_LEVELS * cell * px, l.magnets.size * cell * px) { c ->
        c.scale(px, px)
        for (i in l.magnets.indices) {
            val b = paths[i].tight()
            for (lv in 0 until MAG_SHADOW_LEVELS) {
                c.save()
                placeGlyph(c, b, l, lv * cell + cell / 2, i * cell + cell / 2)
                c.drawPath(paths[i], fill(0xFF2C2A26.toInt(), SHADOW_ALPHA[lv]).blur(SHADOW_SIGMA[lv]))
                c.restore()
            }
        }
    }
}
