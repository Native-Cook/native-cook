package com.ngocdevv.nativecook.blocks.sheets.drawercabinetsheet

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 Drawer Cabinet Sheet — layout, the items, and the art drawn once, off the main thread:
 - the desk: warm paper, a green felt mat with stitching, a ruler, a to-do card, a succulent, a
   notebook and a mug;
 - the drawer (the sheet): an oak front with a brass bar pull, and behind it the box — birch floor,
   oak walls, walnut dividers, a navy felt pen tray, inner shadows — in one bitmap;
 - the cabinet's top board the drawer slides out from under;
 - an atlas of the supplies (pens, a marker, a pencil, paper clips, binder clips, an eraser, tape,
   sticky notes) and one of their soft shadows.
*/

/** Front panel depth, interior depth, wall width (dp). */
internal const val DRAWER_PANEL = 60f
internal const val DRAWER_DEPTH = 384f
private const val WALL = 10f
internal const val DRAWER_SHADOW_PAD = 30f
internal const val DRAWER_ATLAS_W = 300f

internal enum class DrawerKind { Pen, Marker, Pencil, Clip, Binder, Eraser, Tape, Notes }

internal class DrawerItem(
    val kind: DrawerKind,
    val name: String,
    val w: Float,
    val h: Float,
    val x: Float,
    val y: Float,
    val rot: Float,
    val color: Int,
    val spring: DrawerSpring,
)

private val PEN = DrawerSpring(9.0, 0.3, 14.0, 0.35, 0.04)
private val CLIP = DrawerSpring(14.0, 0.5, 10.0, 0.25, 0.25)
private val BINDER = DrawerSpring(10.0, 0.35, 12.0, 0.3, 0.15)
private val HEAVY = DrawerSpring(7.0, 0.15, 17.0, 0.55, 0.04)
private val TAPE = DrawerSpring(12.0, 0.25, 11.0, 0.3, 0.03)

private fun col(c: Long) = c.toInt()

/** The supplies at their places, in interior coordinates (x from the left wall, y from the front panel). */
internal fun drawerItems(wi: Float): List<DrawerItem> {
    val pw = min(250f, wi - 70)
    val cx = wi / 2
    val l = wi * 0.52f
    val clips = listOf(
        floatArrayOf(0.18f, 152f, 0.4f) to 0xFFB8BEC4, floatArrayOf(0.42f, 146f, -0.7f) to 0xFFF07C67, floatArrayOf(0.28f, 182f, 1.2f) to 0xFFB8BEC4,
        floatArrayOf(0.62f, 176f, 0.1f) to 0xFF6CC4A8, floatArrayOf(0.2f, 218f, -0.3f) to 0xFFF2C14E, floatArrayOf(0.55f, 222f, 0.9f) to 0xFFB8BEC4,
    ).map { (v, c) -> DrawerItem(DrawerKind.Clip, "Paper clip", 30f, 11f, l * v[0], v[1], v[2], col(c), CLIP) }
    return listOf(
        DrawerItem(DrawerKind.Pen, "Blue fineliner", pw, 15f, cx - 6, 30f, -0.02f, col(0xFF2F5BD8), PEN),
        DrawerItem(DrawerKind.Pen, "Black gel pen", pw - 14, 15f, cx + 4, 60f, 0.025f, col(0xFF22252B), PEN),
        DrawerItem(DrawerKind.Marker, "Coral marker", pw - 40, 19f, cx - 10, 91f, -0.015f, col(0xFFE8573F), PEN),
    ) + clips + listOf(
        DrawerItem(DrawerKind.Eraser, "Eraser", 58f, 26f, wi * 0.66f, 156f, 0.18f, col(0xFFF3A6B2), HEAVY),
        DrawerItem(DrawerKind.Binder, "Binder clip", 28f, 24f, wi * 0.87f, 160f, -0.35f, col(0xFF2A2C31), BINDER),
        DrawerItem(DrawerKind.Binder, "Binder clip", 28f, 24f, wi * 0.9f, 204f, 0.5f, col(0xFF2A2C31), BINDER),
        DrawerItem(DrawerKind.Pencil, "Pencil", min(124f, wi * 0.4f), 11f, wi * 0.71f, 222f, -0.06f, col(0xFFF2B632), PEN),
        DrawerItem(DrawerKind.Tape, "Tape", 76f, 76f, wi * 0.25f, 315f, 0f, col(0xFFE9C9A0), TAPE),
        DrawerItem(DrawerKind.Notes, "Sticky notes", 88f, 88f, wi * 0.7f, 314f, 0.07f, col(0xFFF9DC6A), HEAVY),
    )
}

internal class DrawerLayout(
    val w: Float,
    val h: Float,
    val top: Float,
    val boardY: Float,
    val half: Float,
    val full: Float,
    val sheetX: Float,
    val sheetW: Float,
    /** The interior's left edge on screen and its width. */
    val ix: Float,
    val iw: Float,
    val matY: Float,
)

internal fun drawerLayout(w: Float, h: Float, top: Float, bottom: Float): DrawerLayout {
    val boardY = h - bottom - 64
    val full = max(220f, min(DRAWER_DEPTH, boardY - DRAWER_PANEL - (top + 236)))
    val sheetX = 12f
    val sheetW = w - 24
    return DrawerLayout(w, h, top, boardY, (full * 0.6f).roundToInt().toFloat(), full, sheetX, sheetW, sheetX + WALL, sheetW - WALL * 2, top + 104)
}

internal class DrawerCell(val x: Float, val y: Float, val w: Float, val h: Float)

internal class DrawerArt(
    val layout: DrawerLayout,
    val items: List<DrawerItem>,
    val px: Float,
    val cells: List<DrawerCell>,
    val faces: Bitmap,
    val shadows: Bitmap,
    val desk: Bitmap,
    val sheet: Bitmap,
    val board: Bitmap,
    val table: Array<DoubleArray>,
)

/** Lays the screen out, bakes every bitmap and the autoPlay table; call off the main thread. */
internal fun drawerBakeArt(w: Float, h: Float, top: Float, bottom: Float, density: Float): DrawerArt {
    val l = drawerLayout(w, h, top, bottom)
    val items = drawerItems(l.iw)
    val px = min(3f, density)
    val (cells, height) = atlasCells(items, DRAWER_ATLAS_W)
    val faces = snapshot(DRAWER_ATLAS_W, height, px) { c -> items.forEachIndexed { i, it -> drawItem(c, it, cells[i].x + PAD, cells[i].y + PAD) } }
    val shadows = snapshot(DRAWER_ATLAS_W, height, px) { c ->
        items.forEachIndexed { i, it ->
            val x = cells[i].x + PAD
            val y = cells[i].y + PAD
            val r = if (it.kind == DrawerKind.Tape) it.w / 2 else min(it.h / 2, 6f)
            val clip = it.kind == DrawerKind.Clip
            c.drawRoundRect(RectF(x, y, x + it.w, y + it.h), r, r, fill(0xFF2A1806, if (clip) 0.3f else 0.42f).blur(if (clip) 1.5f else 3f))
        }
    }
    return DrawerArt(
        l, items, px, cells, faces, shadows,
        desk = bakeDesk(l, px),
        sheet = bakeSheet(l, px),
        board = bakeBoard(l, px),
        table = drawerBuildTable(items.map { it.spring }, l.half.toDouble(), l.full.toDouble()),
    )
}

private fun hash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

private fun hash(n: Int) = hash(n.toDouble())

private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)

private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).roundToInt().coerceIn(0, 255) shl 24)

private fun fill(color: Long, alpha: Float = 1f) = fill(color.toInt(), alpha)

private fun fill(color: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = if (alpha < 1f) withAlpha(color, alpha) else color
}

private fun ints(vararg c: Long) = IntArray(c.size) { c[it].toInt() }

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, colors, pos, Shader.TileMode.CLAMP) }

private fun Paint.blur(sigma: Float): Paint {
    maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
    return this
}

private fun Paint.stroke(w: Float): Paint {
    style = Paint.Style.STROKE
    strokeWidth = w
    strokeCap = Paint.Cap.ROUND
    return this
}

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

private fun Canvas.rrect(x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) = drawRoundRect(rr(x, y, w, h), r, r, p)

private fun Canvas.rect(x: Float, y: Float, w: Float, h: Float, p: Paint) = drawRect(x, y, x + w, y + h, p)

private fun font(size: Float, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, Typeface.BOLD)
    textSize = size
}

private fun Paint.colored(color: Long, alpha: Float = 1f) = apply { this.color = withAlpha(color.toInt(), alpha) }

private fun snapshot(w: Float, h: Float, px: Float, draw: (Canvas) -> Unit): Bitmap {
    val b = Bitmap.createBitmap(max(1, ceil(w * px).toInt()), max(1, ceil(h * px).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.scale(px, px)
    draw(c)
    b.prepareToDraw()
    return b
}

/** Wood: a three-stop base and long, slightly wavy grain lines. */
private fun wood(c: Canvas, x: Float, y: Float, w: Float, h: Float, base: IntArray, grain: Long, seed: Int, alongX: Boolean = true) {
    c.rect(x, y, w, h, if (alongX) linear(0f, y, 0f, y + h, base, floatArrayOf(0f, 0.5f, 1f)) else linear(x, 0f, x + w, 0f, base, floatArrayOf(0f, 0.5f, 1f)))
    val n = ((if (alongX) h else w) / 2.2f).roundToInt()
    val p = Paint(Paint.ANTI_ALIAS_FLAG).stroke(0.6f)
    val b = Path()
    for (i in 0 until n) {
        b.rewind()
        val o = (i + hash(i + seed) * 0.8) * 2.2
        val amp = 0.6 + hash(i * 3 + seed) * 1.6
        val len = if (alongX) w else h
        var s = 0f
        while (s <= len) {
            val d = (o + sin(s * 0.02 + i * 0.7 + seed) * amp).toFloat()
            if (alongX) {
                if (s == 0f) b.moveTo(x + s, y + d) else b.lineTo(x + s, y + d)
            } else if (s == 0f) {
                b.moveTo(x + d, y + s)
            } else {
                b.lineTo(x + d, y + s)
            }
            s += 12
        }
        p.color = withAlpha(grain.toInt(), (0.05 + hash(i * 7 + seed) * 0.12).toFloat())
        c.drawPath(b, p)
    }
}

/** The desk, the mat and what lies on it, the size of the screen. */
private fun bakeDesk(l: DrawerLayout, px: Float) = snapshot(l.w, l.h, px) { c ->
    val w = l.w
    val h = l.h
    val matY = l.matY
    c.rect(0f, 0f, w, h, linear(0f, 0f, 0f, h, ints(0xFFF3ECE1, 0xFFEDE3D4, 0xFFE4D7C4), floatArrayOf(0f, 0.5f, 1f)))
    val gl = fill(0xFFFFFFFF, 0.05f)
    val gd = fill(0xFF6B5238, 0.05f)
    for (i in 0 until 4000) c.rect((hash(i * 1.3) * w).toFloat(), (hash(i * 2.9) * h).toFloat(), 0.7f, 0.7f, if (hash(i * 5.1) > 0.5) gl else gd)
    val mx = 16f
    val mw = w - 32
    val mh = l.boardY - DRAWER_PANEL - 14 - matY
    c.rrect(mx, matY + 10, mw, mh, 22f, fill(0xFF3B2A18, 0.22f).blur(14f))
    c.rrect(mx, matY + 1, mw, mh, 22f, fill(0xFF3B2A18, 0.3f).blur(1.5f))
    c.rrect(mx, matY, mw, mh, 22f, linear(0f, matY, 0f, matY + mh, ints(0xFF3E6152, 0xFF2F5042, 0xFF27443A), floatArrayOf(0f, 0.5f, 1f)))
    val fl = fill(0xFF9FC2B1, 0.09f)
    val fd = fill(0xFF13261F, 0.09f)
    for (i in 0 until 9000) c.rect(mx + (hash(i * 1.7) * mw).toFloat(), matY + (hash(i * 2.3) * mh).toFloat(), 0.8f, 0.8f, if (hash(i * 3.3) > 0.5) fl else fd)
    val stitch = fill(0xFFB9D3C4, 0.5f).stroke(1f).apply { pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) }
    c.drawRoundRect(rr(mx + 8, matY + 8, mw - 16, mh - 16), 16f, 16f, stitch)
    val label = font(10f)
    c.drawText("DESK", mx + 20, matY + 28, label.colored(0xFFCFE3D8, 0.55f))
    // A clear ruler along the top, a to-do card and a succulent, a notebook and a mug: the desk is in use.
    val ry = matY + 36
    c.rrect(mx + 40, ry + 4, 210f, 26f, 4f, fill(0xFF0B1712, 0.25f).blur(4f))
    c.rrect(mx + 40, ry, 210f, 26f, 4f, fill(0xFFDDEFF2, 0.32f))
    c.rrect(mx + 40, ry, 210f, 26f, 4f, fill(0xFFFFFFFF, 0.55f).stroke(0.8f))
    val tick = fill(0xFF14261F, 0.55f).stroke(0.6f)
    for (i in 0..40) c.drawLine(mx + 46 + i * 5, ry, mx + 46 + i * 5, ry + (if (i % 5 == 0) 9 else 5), tick)
    val cy0 = matY + 196
    c.save()
    c.rotate(3f, mx + 100, cy0 + 46)
    c.rect(mx + 32, cy0 + 6, 136f, 94f, fill(0xFF0B1712, 0.35f).blur(6f))
    c.rect(mx + 30, cy0, 136f, 94f, linear(0f, cy0, 0f, cy0 + 94, ints(0xFFFBF7EE, 0xFFF3ECDD), null))
    c.rect(mx + 30, cy0 + 22, 136f, 1f, fill(0xFFE07A6A, 0.6f))
    c.drawText("TODAY", mx + 40, cy0 + 16, label.colored(0xFF7A6A58))
    for (i in 0 until 3) {
        val ly = cy0 + 40 + i * 18
        c.rect(mx + 40, ly - 6, 8f, 8f, fill(0xFF5A6E9A, 0.8f).stroke(1f))
        val ink = Path().apply {
            moveTo(mx + 56, ly)
            quadTo(mx + 80 + i * 6, ly - 4, mx + 104 + i * 10, ly)
            quadTo(mx + 120, ly + 2, mx + 136 - i * 8, ly - 1)
        }
        c.drawPath(ink, fill(0xFF3B5BA8, 0.7f).stroke(1.2f))
    }
    c.restore()
    val px0 = w - 74
    val py0 = cy0 + 40
    c.drawCircle(px0 + 4, py0 + 7, 32f, fill(0xFF0B1712, 0.4f).blur(8f))
    c.drawCircle(px0, py0, 30f, linear(px0 - 30, 0f, px0 + 30, 0f, ints(0xFFB9643C, 0xFFD98457, 0xFF9A4E2C), floatArrayOf(0f, 0.4f, 1f)))
    c.drawCircle(px0, py0, 25f, fill(0xFF4A3426))
    for (i in 0 until 9) {
        val a = i / 9.0 * PI * 2
        val lx = px0 + (cos(a) * 11).toFloat()
        val ly = py0 + (sin(a) * 11).toFloat()
        c.save()
        c.rotate((a * 180 / PI).toFloat(), lx, ly)
        c.drawOval(rr(lx - 11, ly - 5.5f, 22f, 11f), linear(lx - 11, 0f, lx + 11, 0f, ints(0xFF5E9C6E, 0xFF8CCB94, 0xFF4C8A5C), floatArrayOf(0f, 0.6f, 1f)))
        c.restore()
    }
    c.drawCircle(px0, py0, 6f, radial(px0, py0, 6f, ints(0xFFB6E3B4, 0xFF6FAF7A), null))
    val nx = mx + 26
    val ny = matY + mh - 236
    c.save()
    c.rotate(-4f, nx + 80, ny + 100)
    c.rrect(nx + 4, ny + 10, 164f, 214f, 10f, fill(0xFF0B1712, 0.45f).blur(10f))
    c.rrect(nx + 2, ny + 3, 164f, 212f, 9f, fill(0xFFF4EEE2))
    c.rrect(nx, ny, 164f, 212f, 9f, linear(nx, ny, nx + 164, ny + 212, ints(0xFF2D3E63, 0xFF22314F, 0xFF1A2640), floatArrayOf(0f, 0.5f, 1f)))
    c.rect(nx + 132, ny, 8f, 212f, linear(nx + 132, 0f, nx + 140, 0f, ints(0xFF9B2F2A, 0xFFC9453C, 0xFF8A2925), floatArrayOf(0f, 0.5f, 1f)))
    c.drawText("IDEAS · 2026", nx + 16, ny + 32, label.colored(0xFFD9C9A3, 0.8f))
    c.restore()
    val gx = w - 92
    val gy = ny + 70
    c.drawCircle(gx + 4, gy + 8, 36f, fill(0xFF0B1712, 0.4f).blur(9f))
    c.drawCircle(gx, gy, 34f, linear(gx - 34, 0f, gx + 34, 0f, ints(0xFFE9E4DA, 0xFFFFFFFF, 0xFFCFC7B9), floatArrayOf(0f, 0.4f, 1f)))
    c.drawCircle(gx, gy, 27f, radial(gx - 6, gy - 6, 30f, ints(0xFF7A4A2A, 0xFF4B2A16, 0xFF2E190D), floatArrayOf(0f, 0.6f, 1f)))
    c.drawOval(rr(gx - 14, gy - 16, 14f, 7f), fill(0xFFFFFFFF, 0.35f).blur(2f))
    c.rrect(gx + 32, gy - 6, 18f, 12f, 6f, fill(0xFFE2DBCF).stroke(5f))
}

/** The drawer: front panel and box, with room above for its shadow on the desk. */
private fun bakeSheet(l: DrawerLayout, px: Float) = snapshot(l.sheetW, DRAWER_SHADOW_PAD + DRAWER_PANEL + DRAWER_DEPTH, px) { c ->
    val w = l.sheetW
    val y0 = DRAWER_SHADOW_PAD
    val panel = DRAWER_PANEL
    val depth = DRAWER_DEPTH
    c.rrect(4f, y0 - 10, w - 8, panel + 30, 14f, fill(0xFF3B2A18, 0.3f).blur(12f))
    // The box: walls, birch floor, the navy felt pen tray, walnut dividers, inner shadows.
    val iy = y0 + panel
    val iw = w - WALL * 2
    wood(c, 0f, iy, w, depth, ints(0xFFA87B4D, 0xFF94693F, 0xFF7E5733), 0xFF3E2612, 3)
    wood(c, WALL, iy, iw, depth, ints(0xFFEAD7B6, 0xFFE2CCA6, 0xFFD8BF95), 0xFF9A7448, 7)
    c.rrect(WALL + 6, iy + 4, iw - 12, 110f, 8f, linear(0f, iy, 0f, iy + 114, ints(0xFF2B3452, 0xFF323C5E, 0xFF283050), floatArrayOf(0f, 0.5f, 1f)))
    val fl = fill(0xFF8C97C2, 0.12f)
    val fd = fill(0xFF141A2E, 0.12f)
    for (i in 0 until 2200) c.rect(WALL + 6 + (hash(i * 1.1) * (iw - 12)).toFloat(), iy + 4 + (hash(i * 2.7) * 110).toFloat(), 0.8f, 0.8f, if (hash(i * 3.9) > 0.5) fl else fd)
    val walnut = ints(0xFF8A5A33, 0xFF6B4426, 0xFF4E301A)
    fun div(x: Float, y: Float, dw: Float, dh: Float) {
        c.rect(x, y + 3, dw, dh, fill(0xFF3A240F, 0.35f).blur(3f))
        c.rect(x, y, dw, dh, if (dw > dh) linear(0f, y, 0f, y + dh, walnut, floatArrayOf(0f, 0.4f, 1f)) else linear(x, 0f, x + dw, 0f, walnut, floatArrayOf(0f, 0.4f, 1f)))
        c.rect(x, y, dw, 1f, fill(0xFFC79A6A, 0.7f))
    }
    div(WALL, iy + 118, iw, 10f)
    div(WALL, iy + 246, iw, 10f)
    div(WALL + iw * 0.52f, iy + 128, 10f, 118f)
    div(WALL + iw * 0.5f + 6, iy + 256, 10f, 118f)
    div(0f, iy + depth - 10, w, 10f)
    // Shade along the walls and under the front panel's lip.
    c.rect(WALL, iy, 14f, depth, linear(WALL, 0f, WALL + 14, 0f, intArrayOf(argb(0.35f, 60, 36, 14), argb(0f, 60, 36, 14)), null))
    c.rect(w - WALL - 14, iy, 14f, depth, linear(w - WALL - 14, 0f, w - WALL, 0f, intArrayOf(argb(0f, 60, 36, 14), argb(0.35f, 60, 36, 14)), null))
    c.rect(0f, iy, w, 22f, linear(0f, iy, 0f, iy + 22, intArrayOf(argb(0.45f, 40, 24, 10), argb(0f, 40, 24, 10)), null))
    // The front: an oak plank with a bevelled top edge, a brass label holder and the bar pull.
    c.save()
    c.clipPath(Path().apply { addRoundRect(rr(0f, y0, w, panel), 12f, 12f, Path.Direction.CW) })
    wood(c, 0f, y0, w, panel, ints(0xFFC48F5A, 0xFFB07B48, 0xFF93633A), 0xFF4A2C12, 11)
    c.restore()
    c.rrect(0.5f, y0 + 0.5f, w - 1, panel - 1, 12f, fill(0xFFF1C894, 0.5f).stroke(1f))
    c.rect(8f, y0 + panel - 3, w - 16, 3f, fill(0xFF5A3618, 0.35f))
    val cx = w / 2
    val py = y0 + panel / 2
    c.rrect(cx - 66, py + 3, 132f, 16f, 8f, fill(0xFF3A2108, 0.5f).blur(4f))
    for (sx in floatArrayOf(cx - 56, cx + 56)) c.drawCircle(sx, py, 6.5f, radial(sx - 2, py - 2, 8f, ints(0xFFFFF0C2, 0xFFD7A85A, 0xFF8A5E22), floatArrayOf(0f, 0.5f, 1f)))
    c.rrect(cx - 64, py - 6, 128f, 12f, 6f, linear(0f, py - 6, 0f, py + 6, ints(0xFFFBE3A2, 0xFFE0B25E, 0xFFB4843A, 0xFF7E561C), floatArrayOf(0f, 0.35f, 0.7f, 1f)))
    c.rrect(cx - 58, py - 4.5f, 116f, 2f, 1f, fill(0xFFFFFFFF, 0.55f))
    val lx = 26f
    c.rrect(lx, py - 11, 70f, 22f, 4f, linear(0f, py - 11, 0f, py + 11, ints(0xFFF2D08A, 0xFFB8893E), null))
    c.rect(lx + 3, py - 8, 64f, 16f, fill(0xFFF7F1E4))
    c.drawText("SUPPLIES", lx + 13, py + 3, font(8f, mono = true).colored(0xFF4A3A2A))
}

/** The cabinet's top board, from its edge to the bottom of the screen. */
private fun bakeBoard(l: DrawerLayout, px: Float): Bitmap {
    val w = l.w
    val h = l.h - l.boardY + 20
    return snapshot(w, h, px) { c ->
        c.rect(0f, 0f, w, 20f, linear(0f, 0f, 0f, 20f, intArrayOf(argb(0f, 40, 22, 8), argb(0.28f, 40, 22, 8)), null))
        wood(c, 0f, 20f, w, h - 20, ints(0xFFB98552, 0xFFA47142, 0xFF8A5C34), 0xFF4A2C12, 23)
        c.rect(0f, 20f, w, 2.5f, fill(0xFF2A1708))
        c.rect(0f, 22.5f, w, 1.2f, fill(0xFFF3CF9A, 0.8f))
        c.rect(0f, 23.7f, w, 10f, linear(0f, 23.7f, 0f, 34f, intArrayOf(argb(0.25f, 255, 226, 180), argb(0f, 255, 226, 180)), null))
    }
}

private const val PAD = 12f

/** Where each item's sprite sits in the atlas (dp, before px), packed in rows; and the atlas height. */
private fun atlasCells(items: List<DrawerItem>, width: Float): Pair<List<DrawerCell>, Float> {
    val cells = ArrayList<DrawerCell>()
    var x = 0f
    var y = 0f
    var row = 0f
    for (it in items) {
        val cw = it.w + PAD * 2
        val chh = it.h + PAD * 2
        if (x + cw > width) {
            x = 0f
            y += row
            row = 0f
        }
        cells.add(DrawerCell(x, y, cw, chh))
        x += cw
        row = max(row, chh)
    }
    return cells to y + row
}

/** Skia's arcToTangent on an Android path: a corner from the current point toward (x1, y1) then (x2, y2), rounded by r. */
private class TangentPath {
    val path = Path()
    private var cx = 0f
    private var cy = 0f

    fun moveTo(x: Float, y: Float) { path.moveTo(x, y); cx = x; cy = y }

    fun lineTo(x: Float, y: Float) { path.lineTo(x, y); cx = x; cy = y }

    fun arcToTangent(x1: Float, y1: Float, x2: Float, y2: Float, r: Float) {
        var ax = cx - x1
        var ay = cy - y1
        var bx = x2 - x1
        var by = y2 - y1
        val la = sqrt(ax * ax + ay * ay)
        val lb = sqrt(bx * bx + by * by)
        if (la < 1e-4f || lb < 1e-4f || r <= 0f) { lineTo(x1, y1); return }
        ax /= la; ay /= la; bx /= lb; by /= lb
        val cosT = (ax * bx + ay * by).coerceIn(-1f, 1f)
        val theta = acos(cosT)
        if (theta < 1e-4f || theta > PI.toFloat() - 1e-4f) { lineTo(x1, y1); return }
        val d = r / tan(theta / 2)
        val t1x = x1 + ax * d
        val t1y = y1 + ay * d
        val t2x = x1 + bx * d
        val t2y = y1 + by * d
        var mx = ax + bx
        var my = ay + by
        val lm = sqrt(mx * mx + my * my)
        mx /= lm; my /= lm
        val dist = r / sin(theta / 2)
        val ox = x1 + mx * dist
        val oy = y1 + my * dist
        path.lineTo(t1x, t1y)
        val start = Math.toDegrees(atan2((t1y - oy).toDouble(), (t1x - ox).toDouble())).toFloat()
        var sweep = Math.toDegrees(atan2((t2y - oy).toDouble(), (t2x - ox).toDouble())).toFloat() - start
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f
        path.arcTo(RectF(ox - r, oy - r, ox + r, oy + r), start, sweep)
        cx = t2x
        cy = t2y
    }
}

private fun drawItem(c: Canvas, it: DrawerItem, x: Float, y: Float) {
    val w = it.w
    val h = it.h
    when (it.kind) {
        DrawerKind.Pen, DrawerKind.Marker -> {
            val cap = if (it.kind == DrawerKind.Marker) w * 0.3f else w * 0.26f
            c.rrect(x, y, w, h, h / 2, linear(0f, y, 0f, y + h, ints(0xFFFFFFFF, 0xFFE9E6E0, 0xFFBDB7AD), floatArrayOf(0f, 0.35f, 1f)))
            c.rrect(x + w - cap, y - 0.5f, cap, h + 1, h / 2, linear(0f, y, 0f, y + h, intArrayOf(it.color, it.color, 0xFF0E0E14.toInt()), floatArrayOf(0f, 0.45f, 1f)))
            c.rect(x + w - cap - 10, y + h * 0.18f, 26f, 2.4f, fill(0xFFC9CED4))
            c.rect(x + w - cap * 0.8f, y + h * 0.5f - 1.2f, cap * 0.75f, 2.4f, fill(0xFFFFFFFF, 0.25f))
            val tip = Path().apply { moveTo(x + 2, y + h / 2); lineTo(x + 14, y + 1.5f); lineTo(x + 14, y + h - 1.5f); close() }
            c.drawPath(tip, linear(0f, y, 0f, y + h, ints(0xFF5A5A60, 0xFF2A2A30), null))
            c.rect(x + 14, y + h * 0.5f - 0.8f, w * 0.5f, 1.6f, fill(it.color, 0.55f))
            c.rect(x + h / 2, y + 2.5f, w - h, 2f, fill(0xFFFFFFFF, 0.7f))
        }
        DrawerKind.Pencil -> {
            c.rect(x + 14, y, w - 30, h, linear(0f, y, 0f, y + h, ints(0xFFFFD66E, 0xFFF2B632, 0xFFC98A16), floatArrayOf(0f, 0.5f, 1f)))
            c.rect(x + 14, y + h / 3, w - 30, 0.8f, fill(0xFF9A6A10, 0.5f))
            c.drawPath(Path().apply { moveTo(x, y + h / 2); lineTo(x + 14, y); lineTo(x + 14, y + h); close() }, fill(0xFFE8C9A0))
            c.drawPath(Path().apply { moveTo(x, y + h / 2); lineTo(x + 5, y + h * 0.32f); lineTo(x + 5, y + h * 0.68f); close() }, fill(0xFF3A3A40))
            c.rect(x + w - 16, y, 7f, h, linear(0f, y, 0f, y + h, ints(0xFFE6E8EA, 0xFFA8ADB2), null))
            c.rrect(x + w - 10, y, 10f, h, 3f, linear(0f, y, 0f, y + h, ints(0xFFF7B5BE, 0xFFD97E8C), null))
        }
        DrawerKind.Clip -> {
            val p = TangentPath()
            val r1 = h / 2
            p.moveTo(x + w - 6, y + h - 2.5f)
            p.lineTo(x + r1, y + h - 2.5f)
            p.arcToTangent(x + 1, y + h - 2.5f, x + 1, y + h / 2, r1 - 1.5f)
            p.arcToTangent(x + 1, y + 1, x + r1, y + 1, r1 - 1.5f)
            p.lineTo(x + w - r1, y + 1)
            p.arcToTangent(x + w - 1, y + 1, x + w - 1, y + h / 2, r1 - 1.5f)
            p.arcToTangent(x + w - 1, y + h - 1, x + w - r1, y + h - 1, r1 - 1.5f)
            p.lineTo(x + 7, y + h - 1)
            c.drawPath(p.path, linear(0f, y, 0f, y + h, intArrayOf(0xFFFFFFFF.toInt(), it.color, 0xFF5E646A.toInt()), floatArrayOf(0f, 0.45f, 1f)).stroke(1.6f))
        }
        DrawerKind.Binder -> {
            c.drawPath(
                Path().apply { moveTo(x + 2, y + h); lineTo(x + w - 2, y + h); lineTo(x + w - 6, y + h * 0.45f); lineTo(x + 6, y + h * 0.45f); close() },
                linear(0f, y + h * 0.45f, 0f, y + h, ints(0xFF4A4C52, 0xFF1E2024), null),
            )
            c.rect(x + 6, y + h * 0.45f, w - 12, 1f, fill(0xFF8A8E96))
            c.drawPath(Path().apply { moveTo(x + 8, y + h * 0.5f); lineTo(x + 6, y + 1); lineTo(x + w - 6, y + 1); lineTo(x + w - 8, y + h * 0.5f) }, fill(0xFFD6DADE).stroke(1.6f))
        }
        DrawerKind.Eraser -> {
            c.rrect(x, y, w, h, 5f, linear(0f, y, 0f, y + h, intArrayOf(0xFFFFD6DC.toInt(), it.color, 0xFFD9808E.toInt()), floatArrayOf(0f, 0.5f, 1f)))
            c.rect(x + w * 0.38f, y, w * 0.62f, h, linear(0f, y, 0f, y + h, ints(0xFFFFFFFF, 0xFFE8EEF4, 0xFFB9C4CF), floatArrayOf(0f, 0.5f, 1f)))
            c.rect(x + w * 0.38f, y, 1.2f, h, fill(0xFF8E9AA6, 0.6f))
            c.drawText("SOFT", x + w * 0.5f, y + h / 2 + 3, font(8f).colored(0xFF2F5BD8))
        }
        DrawerKind.Tape -> {
            val cx = x + w / 2
            val cy = y + h / 2
            c.drawCircle(cx, cy, w / 2, radial(cx - 8, cy - 8, w * 0.6f, ints(0xFFFBE8CC, 0xFFE9C9A0, 0xFFC69A68), floatArrayOf(0f, 0.6f, 1f)))
            for (i in 0 until 9) c.drawCircle(cx, cy, w / 2 - 3 - i * 2.2f, fill(0xFFFFFFFF, 0.08f + (i % 2) * 0.06f).stroke(0.6f))
            c.drawCircle(cx, cy, w * 0.3f, linear(cx - 20, 0f, cx + 20, 0f, ints(0xFFB48A5C, 0xFFD7B184, 0xFFA47B4E), floatArrayOf(0f, 0.5f, 1f)))
            c.drawCircle(cx, cy, w * 0.25f, fill(0xFFE2CCA6))
            c.drawCircle(cx, cy, w * 0.25f, fill(0xFF6E4C2A, 0.35f).stroke(1.2f))
        }
        DrawerKind.Notes -> {
            for (k in 3 downTo 1) c.rect(x + k * 0.8f, y + k * 1.2f, w, h, fill(if (k % 2 == 1) 0xFFE3C24E else 0xFFEFD25E))
            c.rect(x, y, w, h, linear(x, y, x + w, y + h, intArrayOf(0xFFFFEB8A.toInt(), it.color, 0xFFEFCB4A.toInt()), floatArrayOf(0f, 0.5f, 1f)))
            val pen = fill(0xFF3B5BA8, 0.75f).stroke(1.3f)
            c.drawPath(Path().apply { moveTo(x + 12, y + 26); quadTo(x + 30, y + 18, x + 44, y + 26); quadTo(x + 58, y + 32, x + 72, y + 24) }, pen)
            c.drawPath(Path().apply { moveTo(x + 12, y + 44); quadTo(x + 26, y + 38, x + 40, y + 45) }, pen)
            c.drawPath(Path().apply { moveTo(x + w - 18, y + h); lineTo(x + w, y + h - 18); lineTo(x + w, y + h); close() }, fill(0xFFC9A63A, 0.6f))
        }
    }
}
