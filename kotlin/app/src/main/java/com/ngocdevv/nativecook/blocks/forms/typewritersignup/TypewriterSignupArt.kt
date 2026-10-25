package com.ngocdevv.nativecook.blocks.forms.typewritersignup

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
import android.os.Build
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Typewriter Sign Up — static artwork, rendered once into bitmaps off the main thread, and the
 geometry they share (a port of typewriterSignupArt.ts). Front view of a desk typewriter: a cream
 sheet stands up out of the carriage, the black rubber platen and its chrome knobs move with the
 carriage, a chrome paper bail rides over the sheet, and under the platen the typebar basket shows
 its fan of nine bars around the chrome segment. Everything is in dp (= pt).
*/

internal const val TW_PAPER_W = 300f
internal const val TW_PAPER_H = 340f
internal const val TW_TOP_PAD = 76f
internal const val TW_LINE_H = 34f
internal const val TW_TEXT_X = 108f
private val LABELS = arrayOf("NAME", "EMAIL", "PASSWORD")
internal const val TW_PLATEN_R = 22f
private const val PLATEN_OVER = 44f

/** Parses "#rrggbb" or "rgba(r,g,b,a)" into an ARGB int. */
internal fun twColor(s: String): Int {
    if (s.startsWith("#")) return (0xFF000000 or s.substring(1).toLong(16)).toInt()
    val parts = s.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
    val a = ((if (parts.size > 3) parts[3] else 1f) * 255f + 0.5f).toInt()
    return (a shl 24) or (parts[0].toInt() shl 16) or (parts[1].toInt() shl 8) or parts[2].toInt()
}

internal object TwColors {
    val desk = arrayOf("#2b211b", "#1c1613", "#110d0b")
    val paper = arrayOf("#f6efe0", "#f0e6d1", "#e7dbc2")
    const val INK = 0xFF2B2420.toInt()
    const val CREAM = 0xFFF1E8D8.toInt()
    const val CREAM_SOFT = 0x9EF1E8D8.toInt()
    val enamel = arrayOf("#24302a", "#17201c", "#0d1310")
    val chrome = arrayOf("#5c6166", "#e9edf0", "#9aa1a8", "#3d4247")
    const val BRASS = 0xFFCFAE6D.toInt()
}

/** The machine's layout: W, H, cx and every y are machine points (screen = machine × scale). */
internal class TwGeo(
    val W: Float, val H: Float, val cx: Float, val scale: Float,
    val screenW: Float, val screenH: Float, val formTop: Float,
    val sy: Float, val platenY: Float, val railY: Float,
    val segX: Float, val segY: Float, val deckY: Float, val machineBottom: Float,
    val headerTop: Float, val compact: Boolean,
)

private const val FORM_H_REGULAR = 3 * 56f + 2 * 8f + 16f + 52f
private const val BELOW_LINE = 8f + TW_PLATEN_R * 2 + 4f + 82f + 58f
private const val SHEET_RISE = TW_TOP_PAD + 2 * TW_LINE_H
private const val MAX_SCALE = 1.25f

internal fun twMakeGeo(W: Float, H: Float, insetTop: Float, insetBottom: Float): TwGeo {
    val compact = H < 740f
    var scale = 1f
    val headerTop: Float
    val sy: Float
    if (compact) {
        headerTop = insetTop + 18f
        sy = headerTop + 64f + 10f + 96f
    } else {
        headerTop = insetTop + 12f
        val sheetLimit = headerTop + 84f + 10f
        val machineBottom = H - insetBottom - 16f - FORM_H_REGULAR - 16f
        scale = max(1f, min(MAX_SCALE, (machineBottom - sheetLimit) / (SHEET_RISE + BELOW_LINE)))
        sy = machineBottom / scale - BELOW_LINE
    }
    val platenY = sy + 8f + TW_PLATEN_R
    val railY = platenY + TW_PLATEN_R + 4f
    val segY = railY + 82f
    val deckY = segY - 6f
    val machineBottom = segY + 58f
    val mw = W / scale
    return TwGeo(
        W = mw, H = H / scale, cx = mw / 2, scale = scale, screenW = W, screenH = H,
        formTop = machineBottom * scale + 16f,
        sy = sy, platenY = platenY, railY = railY, segX = mw / 2, segY = segY, deckY = deckY,
        machineBottom = machineBottom, headerTop = headerTop, compact = compact,
    )
}

internal const val TW_SEG_R0 = 30f
private const val SEG_R1 = 66f
private fun barAngle(i: Int) = (PI + ((i + 0.5) / TYPEWRITER_BARS) * PI).toFloat()

/** Typebar i at extension e (0 rest … 1 at the platen) into out = [pivot x, pivot y, tip x, tip y]. */
internal fun twBarTip(g: TwGeo, i: Int, e: Float, out: FloatArray) {
    val a0 = barAngle(i)
    val px = g.segX + cos(a0) * TW_SEG_R0
    val py = g.segY + sin(a0) * TW_SEG_R0
    val l0 = SEG_R1 - TW_SEG_R0
    val tx = g.cx - px
    val ty = g.sy - 5f - py
    var a1 = atan2(ty, tx)
    while (a1 - a0 > PI) a1 -= (2 * PI).toFloat()
    while (a1 - a0 < -PI) a1 += (2 * PI).toFloat()
    val a = a0 + (a1 - a0) * e
    val len = l0 + (hypot(tx, ty) - l0) * e
    out[0] = px; out[1] = py; out[2] = px + cos(a) * len; out[3] = py + sin(a) * len
}

internal fun twTypeface(base: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false)
    else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

internal fun twTextPaint(base: Typeface, weight: Int, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = twTypeface(base, weight)
    textSize = size
    this.color = color
}

/** One pre-rendered piece of art and the rect it covers, in its own coordinates. */
internal class TwArt(val bitmap: Bitmap, val rect: RectF)

internal class TwArtSet(val background: TwArt, val paper: TwArt, val carriage: TwArt, val body: TwArt, val deck: TwArt)

private fun paint(color: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    if (alpha < 1f) this.alpha = (alpha * 255f + 0.5f).toInt()
}

private fun paint(color: String, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = twColor(color)
    this.alpha = ((this.alpha / 255f) * alpha * 255f + 0.5f).toInt()
}

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: Array<String>, pos: FloatArray? = null) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x0, y0, x1, y1, colors.map { twColor(it) }.toIntArray(), pos, Shader.TileMode.CLAMP)
    }

private fun sigmaRadius(sigma: Float) = max(0.5f, (sigma - 0.5f) / 0.57735f)

private fun shadow(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, blur: Float, dy: Float, alpha: Float) {
    val p = paint(0xFF000000.toInt(), alpha)
    p.maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL)
    c.drawRoundRect(RectF(x, y + dy, x + w, y + dy + h), r, r, p)
}

/** Renders `draw` (in its own coordinates over `rect`) into a bitmap at `px` pixels per unit. */
private fun record(rect: RectF, px: Float, draw: (Canvas) -> Unit): TwArt {
    val bw = max(1, (rect.width() * px).toInt() + 1)
    val bh = max(1, (rect.height() * px).toInt() + 1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    c.translate(-rect.left, -rect.top)
    draw(c)
    bmp.prepareToDraw()
    return TwArt(bmp, RectF(rect.left, rect.top, rect.left + bw / px, rect.top + bh / px))
}

/** Every static piece, rendered at `density` pixels per dp (the machine also × its scale). */
internal fun twMakeArt(g: TwGeo, density: Float): TwArtSet {
    val mpx = density * g.scale
    return TwArtSet(
        background = makeBackground(g, density),
        paper = makePaper(mpx),
        carriage = makeCarriage(g, mpx),
        body = makeBodyBack(g, mpx),
        deck = makeDeck(g, mpx),
    )
}

private fun makeBackground(g: TwGeo, px: Float) = record(RectF(0f, 0f, g.screenW, g.screenH), px) { c ->
    val full = RectF(0f, 0f, g.screenW, g.screenH)
    c.drawRect(full, linear(0f, 0f, 0f, g.screenH, TwColors.desk, floatArrayOf(0f, 0.55f, 1f)))
    val lamp = Paint().apply {
        shader = RadialGradient(
            g.screenW / 2, g.sy * g.scale - 40f, g.screenW * 0.9f,
            intArrayOf(twColor("rgba(255,214,160,0.13)"), twColor("rgba(255,214,160,0.04)"), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(full, lamp)
    val vig = Paint().apply {
        shader = RadialGradient(
            g.screenW / 2, g.screenH * 0.45f, g.screenH * 0.75f,
            intArrayOf(0, twColor("rgba(0,0,0,0.18)"), twColor("rgba(0,0,0,0.55)")),
            floatArrayOf(0.45f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(full, vig)
    // Wood grain: long faint strokes, seeded.
    val grain = paint("#f3d9b8", 0.035f).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    val path = Path()
    for (i in 0 until 46) {
        val y = twHash(i * 3.1f) * g.screenH
        path.moveTo(-10f, y)
        path.cubicTo(
            g.screenW * 0.3f, y + (twHash(i + 7f) - 0.5f) * 30f,
            g.screenW * 0.7f, y + (twHash(i + 11f) - 0.5f) * 30f,
            g.screenW + 10f, y + (twHash(i + 13f) - 0.5f) * 20f,
        )
    }
    c.drawPath(path, grain)
    val dots = paint("#ffffff", 0.025f)
    for (i in 0 until 700) c.drawCircle(twHash(i * 1.7f) * g.screenW, twHash(i * 2.3f + 1f) * g.screenH, 0.6f, dots)

    val sans = Typeface.DEFAULT
    c.drawText("INKWELL", 24f, g.headerTop + 12f, twTextPaint(sans, 700, 11f, TwColors.BRASS))
    c.drawText("Start your first draft", 24f, g.headerTop + 46f, twTextPaint(sans, 700, if (g.compact) 25f else 28f, TwColors.CREAM))
    if (!g.compact) c.drawText("Three lines and the page is yours.", 24f, g.headerTop + 74f, twTextPaint(sans, 400, 15f, TwColors.CREAM_SOFT))
}

private fun makePaper(px: Float) = record(RectF(-50f, -50f, TW_PAPER_W + 50f, TW_PAPER_H + 60f), px) { c ->
    // The sheet's soft shadow travels with it.
    shadow(c, 4f, 10f, TW_PAPER_W - 8f, TW_PAPER_H, 2f, 14f, 0f, 0.42f)
    val r = RectF(0f, 0f, TW_PAPER_W, TW_PAPER_H)
    c.drawRect(r, linear(0f, 0f, TW_PAPER_W, TW_PAPER_H, TwColors.paper, floatArrayOf(0f, 0.5f, 1f)))
    c.drawRect(r, linear(0f, 0f, TW_PAPER_W, 0f, arrayOf("rgba(120,96,60,0.10)", "rgba(120,96,60,0)", "rgba(120,96,60,0)", "rgba(120,96,60,0.12)"), floatArrayOf(0f, 0.08f, 0.92f, 1f)))
    c.drawRect(r, linear(0f, 0f, 0f, TW_PAPER_H, arrayOf("rgba(255,255,255,0.35)", "rgba(255,255,255,0)", "rgba(90,70,40,0.10)"), floatArrayOf(0f, 0.2f, 1f)))
    val fibre = paint("#8a6f4a", 0.06f).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; strokeCap = Paint.Cap.ROUND }
    for (i in 0 until 260) {
        val x = twHash(i * 5.3f) * TW_PAPER_W
        val y = twHash(i * 7.9f + 2f) * TW_PAPER_H
        val a = twHash(i * 1.3f) * PI.toFloat()
        val l = 2f + twHash(i * 3.7f) * 6f
        c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, fibre)
    }
    val speck = paint("#6b5536", 0.08f)
    for (i in 0 until 500) c.drawCircle(twHash(i * 2.1f + 9f) * TW_PAPER_W, twHash(i * 4.4f + 3f) * TW_PAPER_H, 0.45f, speck)

    val mono = Typeface.MONOSPACE
    val header = twTextPaint(mono, 500, 8f, twColor("#7c6a55")).apply { alpha = 230 }
    c.drawText("INKWELL  ·  NEW WRITER", 24f, 38f, header)
    c.drawText("DRAFT 0001", TW_PAPER_W - 24f - header.measureText("DRAFT 0001"), 38f, header)
    c.drawRect(RectF(24f, 48f, TW_PAPER_W - 24f, 48.8f), paint("#7c6a55", 0.5f))
    c.drawRect(RectF(TW_TEXT_X - 10f, 56f, TW_TEXT_X - 9.2f, TW_PAPER_H - 14f), paint("#c0504a", 0.28f))
    for (l in 0 until 7) {
        val y = TW_TOP_PAD + l * TW_LINE_H + 6f
        c.drawRect(RectF(20f, y, TW_PAPER_W - 20f, y + 0.7f), paint("#5b7a9a", 0.13f))
    }
    val label = twTextPaint(mono, 600, 8.5f, twColor("#8d7b66"))
    LABELS.forEachIndexed { l, t -> c.drawText(t, 24f, TW_TOP_PAD + l * TW_LINE_H - 1f, label) }
}

/** The carriage, in carriage coordinates: x = 0 at the sheet's left edge, y as on the canvas. */
private fun makeCarriage(g: TwGeo, px: Float): TwArt {
    val x0 = -PLATEN_OVER
    val w = TW_PAPER_W + PLATEN_OVER * 2
    val top = g.platenY - TW_PLATEN_R
    val by = g.sy - 52f
    return record(RectF(x0 - 40f, by - 20f, x0 + w + 40f, top + TW_PLATEN_R * 2 + 50f), px) { c ->
        shadow(c, x0, top, w, TW_PLATEN_R * 2, TW_PLATEN_R, 10f, 10f, 0.45f)
        c.drawRoundRect(
            RectF(x0, top, x0 + w, top + TW_PLATEN_R * 2), 6f, 6f,
            linear(0f, top, 0f, top + TW_PLATEN_R * 2, arrayOf("#3d3833", "#57514b", "#25211e", "#110f0d", "#070605"), floatArrayOf(0f, 0.22f, 0.5f, 0.82f, 1f)),
        )
        c.drawRect(RectF(x0 + 6f, top + 6f, x0 + w - 6f, top + 7.2f), paint("#ffffff", 0.14f))
        for (kx in floatArrayOf(x0 - 16f, x0 + w + 2f)) {
            shadow(c, kx, top - 8f, 14f, TW_PLATEN_R * 2 + 16f, 5f, 4f, 3f, 0.4f)
            c.drawRoundRect(RectF(kx, top - 8f, kx + 14f, top + TW_PLATEN_R * 2 + 8f), 5f, 5f, linear(kx, 0f, kx + 14f, 0f, TwColors.chrome, floatArrayOf(0f, 0.35f, 0.65f, 1f)))
            val ridge = paint("#202326", 0.35f)
            for (i in 0 until 9) c.drawRect(RectF(kx + 1f, top - 4f + i * 5.6f, kx + 13f, top - 4f + i * 5.6f + 0.8f), ridge)
        }
        // Paper bail.
        c.drawRect(RectF(x0 + 8f, by + 3f, x0 + w - 8f, by + 6f), paint("#000000", 0.18f))
        c.drawRoundRect(RectF(x0 + 8f, by, x0 + w - 8f, by + 3.2f), 1.6f, 1.6f, linear(0f, by, 0f, by + 3.2f, arrayOf("#f2f4f6", "#9aa0a6", "#4a4f54")))
        for (wx in floatArrayOf(70f, TW_PAPER_W - 70f)) {
            shadow(c, wx - 7f, by - 3f, 14f, 10f, 4f, 2f, 2f, 0.35f)
            c.drawRoundRect(RectF(wx - 7f, by - 3f, wx + 7f, by + 7f), 4f, 4f, linear(0f, by - 3f, 0f, by + 7f, arrayOf("#4a443f", "#1d1a18", "#0b0a09")))
        }
    }
}

/** The machine behind the bars: rail, enamel body with the basket opening, the chrome segment, the type guide. */
private fun makeBodyBack(g: TwGeo, px: Float) = record(RectF(0f, g.sy - 20f, g.W, g.machineBottom + 110f), px) { c ->
    val left = 10f
    val w = g.W - 20f
    val top = g.railY + 7f
    val h = g.machineBottom - top
    shadow(c, left, top, w, h, 26f, 26f, 18f, 0.5f)
    shadow(c, left, top, w, h, 26f, 3f, 2f, 0.4f)
    c.drawRoundRect(RectF(left, top, left + w, g.machineBottom), 26f, 26f, linear(0f, top, 0f, g.machineBottom, TwColors.enamel, floatArrayOf(0f, 0.5f, 1f)))
    // Basket opening.
    val open = RectF(g.segX - 120f, g.segY - 72f, g.segX + 120f, g.segY + 72f)
    c.save()
    c.clipRect(0f, 0f, g.W, g.segY)
    c.drawOval(open, linear(0f, g.segY - 72f, 0f, g.segY, arrayOf("#050404", "#0d0b0a", "#171311")))
    c.drawOval(open, paint("#9aa1a8", 0.35f).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
    c.restore()
    // Chrome segment the bars pivot on.
    val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        shader = LinearGradient(g.segX - 30f, 0f, g.segX + 30f, 0f, TwColors.chrome.map { twColor(it) }.toIntArray(), floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawArc(RectF(g.segX - TW_SEG_R0, g.segY - TW_SEG_R0, g.segX + TW_SEG_R0, g.segY + TW_SEG_R0), 180f, 180f, false, seg)
    // Rail.
    c.drawRect(RectF(0f, g.railY + 2f, g.W, g.railY + 9f), paint("#000000", 0.35f))
    c.drawRect(RectF(0f, g.railY, g.W, g.railY + 6f), linear(0f, g.railY, 0f, g.railY + 6f, arrayOf("#f0f2f4", "#a3a9af", "#50555a")))
    // Type guide: a small chrome plate with a V notch under the typing point.
    val gy = g.sy + 3f
    val guide = Path().apply {
        moveTo(g.cx - 15f, gy + 2f); lineTo(g.cx - 3f, gy + 2f); lineTo(g.cx, gy + 7f); lineTo(g.cx + 3f, gy + 2f)
        lineTo(g.cx + 15f, gy + 2f); lineTo(g.cx + 12f, gy + 14f); lineTo(g.cx - 12f, gy + 14f); close()
    }
    c.drawPath(guide, linear(0f, gy, 0f, gy + 14f, arrayOf("#f5f7f8", "#a9afb5", "#5a5f64")))
}

/** The deck's front panel over the bar pivots, with its nameplate. */
private fun makeDeck(g: TwGeo, px: Float) = record(RectF(0f, g.deckY - 4f, g.W, g.machineBottom + 4f), px) { c ->
    val left = 10f
    val w = g.W - 20f
    val top = g.deckY
    val h = g.machineBottom - top
    c.drawRoundRect(RectF(left, top, left + w, top + h), 22f, 22f, linear(0f, top, 0f, top + h, arrayOf("#2c3a33", "#1b2621", "#101713"), floatArrayOf(0f, 0.4f, 1f)))
    c.drawRect(RectF(left + 22f, top + 0.5f, left + w - 22f, top + 1.5f), paint("#ffffff", 0.16f))
    c.drawRect(RectF(left + 18f, top + 7f, left + w - 18f, top + 8.6f), linear(0f, 0f, g.W, 0f, arrayOf("rgba(207,174,109,0)", "rgba(207,174,109,0.7)", "rgba(207,174,109,0)")))
    val plate = twTextPaint(Typeface.MONOSPACE, 700, 9f, twColor("#3a2a14"))
    val label = "INKWELL  No. 4"
    val tw = plate.measureText(label)
    val py = top + 24f
    c.drawRoundRect(RectF(g.cx - tw / 2 - 12f, py - 4f, g.cx + tw / 2 + 12f, py + 16f), 4f, 4f, linear(0f, py - 4f, 0f, py + 16f, arrayOf("#e6cf98", "#b48a45", "#7d5b26")))
    c.drawText(label, g.cx - tw / 2, py + 10f, plate)
}
