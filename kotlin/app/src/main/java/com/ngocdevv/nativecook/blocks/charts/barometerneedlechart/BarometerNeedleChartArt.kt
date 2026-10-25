package com.ngocdevv.nativecook.blocks.charts.barometerneedlechart

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.SCALE_MAX
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.SCALE_MIN
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.SPAN
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.angleOf
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.clamp
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.pressureAt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 The barometer's art. Baked once: the navy chart-room background, the brass aneroid dial (bezel,
 ivory face, engraved scale, words, maker's mark) and the 48-hour chart card; a second bake holds
 what sits on the glass (reflections, the brass set hand and its knob). Drawn every frame: the
 needle, the header readout and the chart cursor.
 */

private const val INK = 0x2b2620
private const val AMBER = 0xe6bb62
private const val SEA = 0x9fc6c8

/** Plot range in hPa. */
private const val PLOT_MIN = 980.0
private const val PLOT_MAX = 1030.0

internal class BarometerFonts {
    val kicker = barometerFont(false, 11f, 700)
    val readout = barometerFont(false, 54f, 700)
    val unit = barometerFont(false, 17f, 500)
    val word = barometerFont(false, 22f, 600)
    val small = barometerFont(false, 13f, 500)
    val scale = barometerFont(true, 10f, 600)
    val dialWord = barometerFont(true, 10f, 700)
    val maker = barometerFont(true, 7.5f, 600)
    val chartTitle = barometerFont(false, 15f, 600)
    val label = barometerFont(false, 11f, 500)
    val tip = barometerFont(false, 12f, 600)

    /** The widest digit's advance in the readout font. */
    val digitW: Float = (0..9).maxOf { readout.measureText("$it") }
}

internal class BarometerRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class BarometerGeo(val w: Float, val h: Float, val top: Float, bottom: Float) {
    private val headerTop = top + 12
    val statsTop = h - max(bottom, 12f) - 12 - 56
    private val dialTop = headerTop + 104
    private val avail = statsTop - 12 - 168 - 20 - dialTop
    private val d = max(220f, min(min(w - 48, 330f), avail))
    /** Bezel outer radius, face radius, the scale's outer ring. */
    val rb = d / 2
    val rf = rb - 15
    val rs = rb - 26
    val cx = w / 2
    val cy = dialTop + rb
    private val cardY = cy + rb + 20
    val card = BarometerRect(16f, cardY, w - 32, statsTop - 12 - cardY)
    val plot = BarometerRect(card.x + 16, card.y + 50, card.w - 16 - 50, card.h - 50 - 30)
    val readoutBase = headerTop + 66

    fun plotX(hour: Double) = plot.x + ((hour + SPAN) / (2 * SPAN)).toFloat() * plot.w
    fun plotY(hpa: Double) = plot.y + (1 - (hpa - PLOT_MIN) / (PLOT_MAX - PLOT_MIN)).toFloat() * plot.h
    fun hourAtX(x: Float) = ((x - plot.x) / plot.w) * 2 * SPAN - SPAN

    /** Point on the dial at angle a (degrees from twelve o'clock, clockwise) and radius r. */
    fun polarX(a: Double, r: Float) = cx + sin(a * PI / 180).toFloat() * r
    fun polarY(a: Double, r: Float) = cy - cos(a * PI / 180).toFloat() * r
}

private fun c(vararg v: Pair<Int, Float>) = barometerColors(*v)
private fun f(vararg v: Float) = v

/** Text centred on angle a along a circle of radius r, tops outward. */
private fun arcText(canvas: Canvas, k: BarometerKit, g: BarometerGeo, str: String, a: Double, r: Float, font: Paint, hex: Int, alpha: Float, track: Float) {
    val widths = FloatArray(str.length) { font.measureText(str, it, it + 1) }
    var total = 0f
    for (i in widths.indices) total += widths[i] + if (i < widths.size - 1) track else 0f
    var s = -total / 2
    font.color = barometerArgb(hex, alpha)
    for (i in str.indices) {
        val mid = s + widths[i] / 2
        val ang = a + (mid / r) * 180 / PI
        canvas.save()
        canvas.translate(g.cx, g.cy)
        canvas.rotate(ang.toFloat())
        canvas.drawText(str, i, i + 1, -widths[i] / 2, -r, font)
        canvas.restore()
        s += widths[i] + track
    }
}

private fun drawBackground(canvas: Canvas, k: BarometerKit, g: BarometerGeo) {
    canvas.drawRect(0f, 0f, g.w, g.h, k.linear(0f, 0f, 0f, g.h, c(0x13232b to 1f, 0x0f1c24 to 1f, 0x0a1318 to 1f), f(0f, 0.5f, 1f)))
    // A warm lamp over the dial and a cool vignette at the corners.
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, g.cy - g.rb * 0.4f, g.w * 0.95f, c(0x6b5530 to 0.22f, 0x3a3a2c to 0.08f, 0 to 0f), f(0f, 0.5f, 1f)))
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, g.h * 0.45f, g.h * 0.75f, c(0 to 0f, 0x02070a to 0.1f, 0x02070a to 0.55f), f(0f, 0.6f, 1f)))
    k.grain(canvas, 0f, 0f, g.w, g.h, 0.07f, 3)
}

private fun drawDial(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts) {
    val cx = g.cx
    val cy = g.cy
    val rb = g.rb
    val rf = g.rf
    val rs = g.rs
    // Two shadows: soft ambient and a tight contact one.
    canvas.drawCircle(cx, cy + 16, rb, k.fill(0, 0.45f, 22f))
    canvas.drawCircle(cx, cy + 2, rb, k.fill(0, 0.55f, 3f))
    // Brass bezel: a sweep of light and dark bands reads as turned metal.
    canvas.drawCircle(
        cx, cy, rb,
        k.sweep(
            cx, cy,
            c(0x7a5622 to 1f, 0xf4dc9f to 1f, 0xa6782f to 1f, 0x5e4119 to 1f, 0xd9b56c to 1f, 0xfbe7b5 to 1f, 0x8c6428 to 1f, 0x7a5622 to 1f),
            f(0f, 0.12f, 0.27f, 0.42f, 0.58f, 0.7f, 0.86f, 1f),
        ),
    )
    canvas.drawCircle(cx, cy, rb, k.radial(cx, cy - rb * 0.3f, rb * 1.3f, c(0xffffff to 0.18f, 0xffffff to 0f, 0x2a1a06 to 0.35f), f(0f, 0.5f, 1f)))
    canvas.drawCircle(cx, cy, rb - 0.6f, k.stroke(0xfff1c8, 1.2f, 0.55f))
    canvas.drawCircle(cx, cy, rf + 4, k.stroke(0x4a3312, 2.5f, 0.8f))
    canvas.drawCircle(cx, cy, rf + 1.4f, k.stroke(0xf6dfa4, 1f, 0.7f))
    // Ivory face with a printed-paper grain.
    canvas.drawCircle(cx, cy, rf, k.radial(cx - rf * 0.15f, cy - rf * 0.2f, rf * 1.1f, c(0xf6eedb to 1f, 0xece1c8 to 1f, 0xd8c7a3 to 1f), f(0f, 0.62f, 1f)))
    canvas.save()
    canvas.clipPath(Path().apply { addCircle(cx, cy, rf, Path.Direction.CW) })
    k.grain(canvas, cx - rf, cy - rf, rf * 2, rf * 2, 0.16f, 11)
    // The bezel's shadow falls on the face.
    canvas.drawCircle(cx, cy, rf, k.radial(cx, cy + 4, rf, c(0x3a2a12 to 0f, 0x3a2a12 to 0f, 0x3a2a12 to 0.32f), f(0f, 0.86f, 1f)))
    canvas.restore()

    // Coloured zone band just inside the scale.
    val zones = listOf(
        Triple(SCALE_MIN, 990.0, 0x8e3b2e),
        Triple(990.0, 1005.0, 0x3e5a73),
        Triple(1005.0, 1018.0, 0x7d7158),
        Triple(1018.0, 1035.0, 0xa47b2c),
        Triple(1035.0, SCALE_MAX, 0xb5622a),
    )
    val bandR = rs - 3
    val bandRect = RectF(cx - bandR, cy - bandR, cx + bandR, cy + bandR)
    for ((a, b, col) in zones) {
        val start = (angleOf(a) - 90).toFloat()
        val sweep = (angleOf(b) - angleOf(a)).toFloat()
        canvas.drawPath(Path().apply { addArc(bandRect, start, sweep) }, k.stroke(col, 4f, 0.55f))
    }
    // The scale: two engraved rings and ticks every hPa, longer every 5 and 10.
    fun ring(r: Float) = Path().apply {
        addArc(RectF(cx - r, cy - r, cx + r, cy + r), (angleOf(SCALE_MIN) - 90).toFloat(), (angleOf(SCALE_MAX) - angleOf(SCALE_MIN)).toFloat())
    }
    canvas.drawPath(ring(rs), k.stroke(INK, 1.1f, 0.9f))
    canvas.drawPath(ring(rs - 6), k.stroke(INK, 0.6f, 0.6f))
    val ticks = Path()
    val majors = Path()
    for (v in SCALE_MIN.toInt()..SCALE_MAX.toInt()) {
        val a = angleOf(v.toDouble())
        val len = if (v % 10 == 0) 13f else if (v % 5 == 0) 9f else 5f
        val p = if (v % 10 == 0) majors else ticks
        p.moveTo(g.polarX(a, rs), g.polarY(a, rs))
        p.lineTo(g.polarX(a, rs - len), g.polarY(a, rs - len))
    }
    canvas.drawPath(ticks, k.stroke(INK, 0.7f, 0.8f))
    canvas.drawPath(majors, k.stroke(INK, 1.6f, 0.95f))
    var v = SCALE_MIN.toInt()
    while (v <= SCALE_MAX.toInt()) {
        arcText(canvas, k, g, "$v", angleOf(v.toDouble()), rs - 26, ft.scale, INK, 0.92f, 0.2f)
        v += 10
    }
    for (i in BarometerMotion.WORDS.indices) {
        arcText(canvas, k, g, BarometerMotion.WORDS[i].uppercase(), angleOf(BarometerMotion.WORD_AT[i]), rs - 48, ft.dialWord, 0x3a2d22, 0.95f, 1.6f)
    }
    // Maker's mark and a guilloché rosette around the arbor.
    k.spaced(canvas, "SALTMARSH & CO.", cx, cy + rf * 0.36f, ft.maker, 0x4a3b2c, 0.85f, 1.6f, 0.5f)
    k.spaced(canvas, "ANEROID · HPA", cx, cy + rf * 0.36f + 13, ft.maker, 0x4a3b2c, 0.55f, 1.4f, 0.5f)
    for (i in 0 until 24) {
        val a = i * 360.0 / 24
        canvas.drawCircle(g.polarX(a, rf * 0.11f), g.polarY(a, rf * 0.11f), rf * 0.075f, k.stroke(0x6b5636, 0.5f, 0.35f))
    }
}

private fun dashed(p: Paint, on: Float, off: Float): Paint {
    p.pathEffect = DashPathEffect(floatArrayOf(on, off), 0f)
    return p
}

private fun drawCard(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts) {
    val card = g.card
    val plot = g.plot
    canvas.drawRoundRect(card.x, card.y + 10, card.x + card.w, card.y + 10 + card.h, 22f, 22f, k.fill(0, 0.35f, 18f))
    canvas.drawRoundRect(card.x, card.y, card.x + card.w, card.y + card.h, 22f, 22f, k.linear(0f, card.y, 0f, card.y + card.h, c(0x1b2e36 to 1f, 0x15252c to 1f, 0x101d23 to 1f), f(0f, 0.5f, 1f)))
    canvas.drawRoundRect(card.x, card.y, card.x + card.w, card.y + card.h, 22f, 22f, k.stroke(0xffffff, 1f, 0.07f))
    canvas.drawLine(card.x + 22, card.y + 0.8f, card.x + card.w - 22, card.y + 0.8f, k.stroke(0xffffff, 1f, 0.1f))
    k.text(canvas, "48-hour pressure", card.x + 16, card.y + 28, ft.chartTitle, 0xf1ead9)
    // Legend: past (solid) and forecast (dashed).
    val lx = card.x + card.w - 16
    val fw = ft.label.measureText("Forecast")
    k.text(canvas, "Forecast", lx - fw, card.y + 27, ft.label, 0xd7d0bf, 0.6f)
    canvas.drawLine(lx - fw - 24, card.y + 23, lx - fw - 8, card.y + 23, dashed(k.stroke(SEA, 2f, 0.9f), 4f, 3f))
    val pw = ft.label.measureText("Past")
    val px = lx - fw - 36 - pw
    k.text(canvas, "Past", px, card.y + 27, ft.label, 0xd7d0bf, 0.6f)
    canvas.drawLine(px - 22, card.y + 23, px - 8, card.y + 23, k.stroke(AMBER, 2.2f))

    // Grid and the right-hand scale.
    var v = 990
    while (v <= 1030) {
        val y = g.plotY(v.toDouble())
        canvas.drawLine(plot.x, y, plot.x + plot.w, y, dashed(k.stroke(0xffffff, 1f, 0.06f), 2f, 4f))
        k.text(canvas, "$v", plot.x + plot.w + 10, y + 4, ft.label, 0xd7d0bf, 0.45f)
        v += 10
    }
    // The lines: area under the past, an uncertainty fan over the forecast.
    val step = 0.5
    val past = Path()
    val pastArea = Path().apply { moveTo(g.plotX(-SPAN), plot.y + plot.h) }
    var hr = -SPAN
    while (hr <= 0.001) {
        val x = g.plotX(hr)
        val y = g.plotY(pressureAt(hr))
        if (hr == -SPAN) past.moveTo(x, y) else past.lineTo(x, y)
        pastArea.lineTo(x, y)
        hr += step
    }
    pastArea.lineTo(g.plotX(0.0), plot.y + plot.h)
    pastArea.close()
    canvas.drawPath(pastArea, k.linear(0f, plot.y, 0f, plot.y + plot.h, c(AMBER to 0.3f, 0xb98a3c to 0.1f, AMBER to 0f), f(0f, 0.55f, 1f)))
    val fan = Path()
    hr = 0.0
    while (hr <= SPAN + 0.001) {
        val x = g.plotX(hr)
        val y = g.plotY(pressureAt(hr) + (hr / SPAN) * 5)
        if (hr == 0.0) fan.moveTo(x, y) else fan.lineTo(x, y)
        hr += step
    }
    hr = SPAN
    while (hr >= -0.001) {
        fan.lineTo(g.plotX(hr), g.plotY(pressureAt(hr) - (hr / SPAN) * 5))
        hr -= step
    }
    fan.close()
    canvas.drawPath(fan, k.fill(SEA, 0.1f))
    val future = Path()
    hr = 0.0
    while (hr <= SPAN + 0.001) {
        val x = g.plotX(hr)
        val y = g.plotY(pressureAt(hr))
        if (hr == 0.0) future.moveTo(x, y) else future.lineTo(x, y)
        hr += step
    }
    canvas.drawPath(past, k.stroke(AMBER, 4f, 0.25f, 3f))
    canvas.drawPath(past, k.stroke(AMBER, 2.2f))
    canvas.drawPath(future, dashed(k.stroke(SEA, 2f, 0.95f), 5f, 4f))
    // Storm note at the low, and the now line.
    val lowX = g.plotX(-14.0)
    val lowY = g.plotY(985.0)
    canvas.drawCircle(lowX, lowY, 2.5f, k.fill(0xe0775c))
    val gw = ft.label.measureText("Gale 985")
    k.text(canvas, "Gale 985", lowX - gw / 2, lowY + 18, ft.label, 0xe0775c, 0.85f)
    canvas.drawLine(g.plotX(0.0), plot.y - 6, g.plotX(0.0), plot.y + plot.h, dashed(k.stroke(0xffffff, 1f, 0.22f), 1f, 3f))
    val labels = arrayOf("−24 h", "−12 h", "Now", "+12 h", "+24 h")
    for (i in labels.indices) {
        val x = g.plotX(-SPAN + i * 12)
        val lw = ft.label.measureText(labels[i])
        val ax = if (i == 0) x else if (i == labels.size - 1) x - lw else x - lw / 2
        k.text(canvas, labels[i], ax, plot.y + plot.h + 20, ft.label, 0xd7d0bf, if (i == 2) 0.8f else 0.45f)
    }
}

/** Everything under the needle. */
internal fun barometerDrawBase(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts) {
    drawBackground(canvas, k, g)
    k.spaced(canvas, "SALTMARSH HARBOUR", 24f, g.top + 24, ft.kicker, 0xe9e0cc, 0.62f, 2.2f)
    k.text(canvas, "Pressure · updated 13:52", 24f, g.readoutBase + 24, ft.small, 0xe9e0cc, 0.5f)
    drawDial(canvas, k, g, ft)
    drawCard(canvas, k, g, ft)
}

/** What sits on and over the glass, in the dial's own box (origin at the bezel's top-left). */
internal fun barometerDrawGlass(canvas: Canvas, k: BarometerKit, g: BarometerGeo, setAngle: Double) {
    canvas.save()
    canvas.translate(-(g.cx - g.rb), -(g.cy - g.rb))
    val cx = g.cx
    val cy = g.cy
    val rf = g.rf
    // Brass set hand, under the glass's reflections.
    canvas.save()
    canvas.translate(cx, cy)
    canvas.rotate(setAngle.toFloat())
    canvas.drawRoundRect(-1.6f, -rf * 0.72f + 2, 1.6f, 2f, 1.6f, 1.6f, k.fill(0, 0.3f, 2f))
    val hand = Path().apply {
        moveTo(-2f, 0f)
        lineTo(-1.2f, -rf * 0.6f)
        lineTo(-5f, -rf * 0.6f)
        lineTo(0f, -rf * 0.74f)
        lineTo(5f, -rf * 0.6f)
        lineTo(1.2f, -rf * 0.6f)
        lineTo(2f, 0f)
        close()
    }
    canvas.drawPath(hand, k.linear(-4f, 0f, 4f, 0f, c(0x8a6526 to 1f, 0xf3d695 to 1f, 0x9c7330 to 1f), f(0f, 0.5f, 1f)))
    canvas.restore()
    // Knurled set knob on the glass.
    canvas.drawCircle(cx, cy + 2, 13f, k.fill(0, 0.4f, 3f))
    canvas.drawCircle(cx, cy, 13f, k.radial(cx - 4, cy - 5, 16f, c(0xfbe6ae to 1f, 0xc89b4a to 1f, 0x6e4d1c to 1f), f(0f, 0.55f, 1f)))
    val knurl = k.stroke(0x4a3312, 0.7f, 0.45f)
    for (i in 0 until 28) {
        val a = i * PI * 2 / 28
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        canvas.drawLine(cx + ca * 9.5f, cy + sa * 9.5f, cx + ca * 12.6f, cy + sa * 12.6f, knurl)
    }
    canvas.drawCircle(cx, cy, 8.5f, k.radial(cx - 2, cy - 3, 10f, c(0xfff3cf to 1f, 0xd4a85a to 1f, 0x8a6526 to 1f), f(0f, 0.6f, 1f)))
    // Glass: a crescent highlight top-left, a broad soft glare, a bright rim and a darker lower rim.
    canvas.save()
    canvas.clipPath(Path().apply { addCircle(cx, cy, rf, Path.Direction.CW) })
    val crescent = Path().apply {
        addCircle(cx, cy, rf - 3, Path.Direction.CW)
        addCircle(cx + rf * 0.12f, cy + rf * 0.14f, rf * 0.98f, Path.Direction.CW)
        fillType = Path.FillType.EVEN_ODD
    }
    canvas.drawPath(crescent, k.linear(cx - rf, cy - rf, cx, cy, c(0xffffff to 0.42f, 0xffffff to 0.12f, 0xffffff to 0f), f(0f, 0.45f, 1f)))
    canvas.drawRect(
        cx - rf, cy - rf, cx + rf, cy + rf,
        k.linear(cx - rf * 0.6f, cy - rf, cx + rf * 0.3f, cy + rf * 0.6f, c(0xffffff to 0f, 0xffffff to 0.09f, 0xffffff to 0.02f, 0xffffff to 0f), f(0.25f, 0.42f, 0.52f, 0.62f)),
    )
    canvas.drawCircle(cx, cy, rf, k.radial(cx, cy, rf, c(0x0b1a20 to 0f, 0x0b1a20 to 0f, 0x0b1a20 to 0.18f), f(0f, 0.9f, 1f)))
    canvas.restore()
    canvas.drawCircle(cx, cy, rf - 1, k.stroke(0xffffff, 1.2f, 0.22f))
    canvas.restore()
}

/** The needle's outline, pointing at twelve o'clock from the arbor. Built once per size. */
internal fun barometerNeedlePath(g: BarometerGeo): Path {
    val len = g.rf * 0.84f
    val tail = g.rf * 0.22f
    return Path().apply {
        moveTo(-2.2f, tail)
        lineTo(-0.7f, -len * 0.86f)
        lineTo(-3.4f, -len * 0.86f)
        lineTo(0f, -len)
        lineTo(3.4f, -len * 0.86f)
        lineTo(0.7f, -len * 0.86f)
        lineTo(2.2f, tail)
        close()
    }
}

private val NEEDLE_STEEL = c(0x0c1626 to 1f, 0x3a5680 to 1f, 0x101b2e to 1f)
private val ARBOR = c(0xfde9b6 to 1f, 0xbf913f to 1f, 0x6a4818 to 1f)
private val STOPS3 = f(0f, 0.6f, 1f)
private val STOPS_MID = f(0f, 0.5f, 1f)

/** The blued needle at `angle`, with its shadow on the face and the brass arbor cap. */
internal fun barometerDrawNeedle(canvas: Canvas, k: BarometerKit, g: BarometerGeo, body: Path, angle: Double) {
    val tail = g.rf * 0.22f
    // Shadow on the face first (the needle stands a few points off it).
    canvas.save()
    canvas.translate(g.cx + 2.5f, g.cy + 4.5f)
    canvas.rotate(angle.toFloat())
    canvas.drawPath(body, k.fill(0x2a1c0a, 0.28f, 2.2f))
    canvas.drawCircle(0f, tail - 3, 7f, k.stroke(0x2a1c0a, 2.4f, 0.22f, 2f))
    canvas.restore()
    canvas.save()
    canvas.translate(g.cx, g.cy)
    canvas.rotate(angle.toFloat())
    // Blued steel: dark at the edges, a cool sheen down the middle.
    canvas.drawPath(body, k.linear(-3.4f, 0f, 3.4f, 0f, NEEDLE_STEEL, STOPS_MID))
    canvas.drawCircle(0f, tail - 3, 7f, k.stroke(0x15233a, 2.4f))
    canvas.drawCircle(0f, tail - 3, 7f, k.stroke(0x5a7aa6, 0.6f, 0.5f))
    canvas.restore()
    // Brass arbor cap.
    canvas.drawCircle(g.cx, g.cy, 6.5f, k.radial(g.cx - 2, g.cy - 2, 8f, ARBOR, STOPS3))
}

private val DIGITS = Array(10) { "$it" }
private val arrowPath = Path().apply {
    moveTo(-5f, 0f)
    lineTo(4f, 0f)
    moveTo(1f, -3f)
    lineTo(4.5f, 0f)
    lineTo(1f, 3f)
}

/** An odometer: each column rolls from the one below it, so a held value rests on whole digits. */
private fun odometer(canvas: Canvas, k: BarometerKit, value: Double, x: Float, base: Float, font: Paint, digitW: Float, lineH: Float, hex: Int): Float {
    val r = BarometerMotion.softRound(value)
    val pos = doubleArrayOf(r, 0.0, 0.0, 0.0)
    for (i in 1 until 4) {
        val below = pos[i - 1]
        val m = ((below % 10) + 10) % 10
        pos[i] = floor(below / 10) + max(0.0, m - 9)
    }
    // The thousands column only shows from 1000 up; the others slide over to close the gap.
    val lead = clamp(pos[3], 0.0, 1.0).toFloat()
    val shift = -digitW * (1 - lead)
    val top = base - lineH * 0.78f
    canvas.save()
    canvas.clipRect(x - 4, top - 4, x - 4 + digitW * 4 + 8, top - 4 + lineH + 8)
    for (i in 0 until 4) {
        val col = 3 - i
        val cx = x + shift + col * digitW
        val d = ((pos[i] % 10) + 10) % 10
        val di = floor(d).toInt()
        val fr = (d - di).toFloat()
        val alphaCol = if (i == 3) lead else 1f
        for (j in 0 until 2) {
            val digit = (di + j) % 10
            if (i == 3 && digit == 0) continue
            val off = (j - fr) * lineH
            val a = alphaCol * (1 - min(1f, abs(j - fr)))
            if (a <= 0.01f) continue
            val s = DIGITS[digit]
            val w = font.measureText(s)
            k.text(canvas, s, cx + (digitW - w) / 2, base + off, font, hex, a)
        }
    }
    canvas.restore()
    return digitW * 4 + shift
}

/** Two words rolling: index i shown, rolling up to i + 1 as the fraction grows. Right-aligned at x. */
private fun rollWords(canvas: Canvas, k: BarometerKit, words: Array<String>, index: Double, x: Float, base: Float, font: Paint, hex: Int, travel: Float) {
    val i = clamp(floor(index), 0.0, (words.size - 1).toDouble()).toInt()
    val fr = (index - i).toFloat()
    for (j in 0 until 2) {
        val w = words.getOrNull(i + j) ?: continue
        val a = 1 - min(1f, abs(j - fr))
        if (a <= 0.01f) continue
        k.text(canvas, w, x - font.measureText(w), base + (j - fr) * travel, font, hex, a)
    }
}

private fun drawHeader(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts, hpa: Double) {
    val base = g.readoutBase
    val width = odometer(canvas, k, hpa, 24f, base, ft.readout, ft.digitW, 60f, 0xf4ecd8)
    k.text(canvas, "hPa", 24 + width + 8, base, ft.unit, 0xe9e0cc, 0.6f)
    // The forecast word and the trend against the set hand, rolling as they change.
    val right = g.w - 24
    canvas.save()
    canvas.clipRect(right - 160, base - 52, right, base + 12)
    rollWords(canvas, k, BarometerMotion.WORDS, BarometerMotion.wordIndex(hpa), right, base - 22, ft.word, 0xf4ecd8, 26f)
    val trend = BarometerMotion.trendIndex(hpa)
    val trendHex = if (trend > 1.5) 0xa9d5c9 else if (trend < 0.5) 0xe39a7f else 0xd7d0bf
    rollWords(canvas, k, BarometerMotion.TRENDS, trend, right, base + 1, ft.small, trendHex, 18f)
    canvas.restore()
    // Trend arrow left of the trend word, turning with the change.
    val delta = clamp((hpa - BarometerMotion.NOW_HPA) / 12, -1.0, 1.0)
    val tw = ft.small.measureText(BarometerMotion.TRENDS[BarometerMotion.jsRound(trend).coerceIn(0, 2)])
    canvas.save()
    canvas.translate(right - tw - 12, base - 4)
    canvas.rotate((-delta * 45).toFloat())
    canvas.drawPath(arrowPath, k.stroke(0xe9e0cc, 1.5f, 0.75f))
    canvas.restore()
}

private fun drawCursor(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts, hour: Double) {
    val h = clamp(hour, -24.0, 24.0)
    val x = g.plotX(h)
    val hpa = pressureAt(h)
    val y = g.plotY(hpa)
    val plot = g.plot
    canvas.drawLine(x, plot.y - 4, x, plot.y + plot.h, k.stroke(0xf4ecd8, 1f, 0.35f))
    val col = if (h > 0) 0x9fc6c8 else AMBER
    canvas.drawCircle(x, y, 10f, k.fill(col, 0.22f, 4f))
    canvas.drawCircle(x, y, 5.5f, k.fill(0x0f1c22))
    canvas.drawCircle(x, y, 5.5f, k.stroke(col, 2.2f))
    // Tooltip above the dot (below it near the top), kept inside the plot.
    val label = "${BarometerMotion.timeLabel(h)} · ${BarometerMotion.jsRound(hpa)} hPa"
    val tw = ft.tip.measureText(label)
    val bw = tw + 20
    val bx = min(max(x - bw / 2, plot.x - 4), plot.x + plot.w + 40 - bw)
    val above = y - 38 >= plot.y - 12
    val by = if (above) y - 38 else y + 14
    canvas.drawRoundRect(bx, by + 3, bx + bw, by + 27, 12f, 12f, k.fill(0, 0.35f, 6f))
    canvas.drawRoundRect(bx, by, bx + bw, by + 24, 12f, 12f, k.fill(0xf2ead6))
    k.text(canvas, label, bx + 10, by + 16, ft.tip, 0x1d2a2f)
}

/** Header readout and chart cursor, over everything else. */
internal fun barometerDrawOverlay(canvas: Canvas, k: BarometerKit, g: BarometerGeo, ft: BarometerFonts, hour: Double, hpaNeedle: Double) {
    drawHeader(canvas, k, g, ft, hpaNeedle)
    drawCursor(canvas, k, g, ft, hour)
}
