package com.ngocdevv.nativecook.blocks.cards.ingotcoinswitch

import android.app.Activity
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max

/*
 Ingot Coin Switch — the Explore screen of an invented investing app ("kesta")
 (a port of IngotCoinSwitch.swift).

 Look: a warm off-white page, a headline and a subline, and in the middle a ring of ten metal ingots
 (gold, copper, silver, platinum, palladium) standing on edge and leaning outward like a crown, turning
 slowly. Under it a ticker for the piece in front, the "Stocks / Commodities" switch and a dark CTA.
 Everything is drawn in code: each piece is a 3D body projected with perspective and shaded face by face,
 with a highlight band that slides across its face as it turns (IngotCoinSwitchModel.kt).

 Hero: switching to "Stocks" morphs every ingot into a thick frosted coin with an embossed letter: the
 outline goes from a rounded bar to a circle, the body thins, and each piece turns a quarter turn so the
 coins stand like the pages of a book, going translucent at the peak. The morph runs as a wave from the
 front piece to the sides; switching back undoes it.

 autoPlay: LOOP = 10 s. Rest on ingots (2.4 s), tap "Stocks" and morph, hold the coins, tap
 "Commodities" at 6.4 s and morph back, rest. The ring turns half a turn per loop (its pieces repeat
 every five), so the last frame is the first.
 Touch: tap a pill to switch; drag sideways to spin the ring, it keeps the finger's speed and slows
 down. 2 s after the last touch the ring turns home, the pieces go back to ingots and the loop restarts.
 Reduced motion (animator duration scale 0): the ring stands still; the switch crossfades the shapes
 and the copy.

 Per frame the driver runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private const val PAPER = 0xF5F4F1L
private const val INK = 0x16181BL
private const val MUTED = 0x6F6B65L

private fun hex(v: Long, a: Float = 1f) = Color(
    ((v shr 16) and 0xFF).toInt() / 255f, ((v shr 8) and 0xFF).toInt() / 255f, (v and 0xFF).toInt() / 255f, a,
)

private fun col(c: IcsRgb, a: Float = 1f) = Color(c.r.toFloat(), c.g.toFloat(), c.b.toFloat(), a)

private fun Double.f() = toFloat()

internal class IcsGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx get() = w / 2
    val ringY get() = top + 386
    val tickerY get() = top + 562
    val toggleY get() = top + 620
    val ctaY get() = h - max(bottom, 16.0) - 30

    val stocksRect get() = Rect((cx - 134).f(), (toggleY - 22).f(), (cx - 26).f(), (toggleY + 22).f())
    val commRect get() = Rect((cx - 16).f(), (toggleY - 22).f(), (cx + 134).f(), (toggleY + 22).f())

    /** 1 for "Stocks", 0 for "Commodities", null elsewhere. */
    fun pill(x: Float, y: Float): Int? {
        val s = stocksRect
        val c = commRect
        if (x >= s.left - 4 && x <= s.right + 4 && y >= s.top - 8 && y <= s.bottom + 8) return 1
        if (x >= c.left - 4 && x <= c.right + 4 && y >= c.top - 8 && y <= c.bottom + 8) return 0
        return null
    }
}

/** Text drawn on the canvas, measured once at 1 dp = 1 px. */
internal class IcsTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, color: Long, track: Float = 0f) = m.measure(
        s,
        TextStyle(color = hex(color), fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
        density = unit,
    )

    val brand = t("kesta", 22f, FontWeight.Black, INK, -0.6f)
    val avatar = t("AL", 13f, FontWeight.SemiBold, INK)
    val eyebrow = t("EXPLORE", 12f, FontWeight.SemiBold, 0x8B8780, 1.6f)
    val headlines = listOf(listOf("Own real metal,", "from $1."), listOf("A slice of 2,000+", "companies."))
        .map { lines -> lines.map { t(it, 31f, FontWeight.Bold, INK, -0.6f) } }
    val sublines = listOf(
        listOf("Gold, silver, copper and more, held in", "insured vaults. Capital at risk."),
        listOf("Fractional shares from $1 and no fees on", "your first 50 trades. Capital at risk."),
    ).map { lines -> lines.map { t(it, 15f, FontWeight.Normal, MUTED) } }
    val ctas = listOf("Explore commodities", "Explore stocks").map { t(it, 17f, FontWeight.SemiBold, 0xFFFFFF) }
    val pillsDark = listOf("Commodities", "Stocks").map { t(it, 16f, FontWeight.SemiBold, INK) }
    val pillsLight = listOf("Commodities", "Stocks").map { t(it, 16f, FontWeight.SemiBold, 0xFFFFFF) }
    val metalNames = IcsModel.METALS.map { t(it, 14f, FontWeight.SemiBold, INK) }
    val metalPrices = IcsModel.METAL_PRICES.map { t(it, 14f, FontWeight.Normal, MUTED) }
    val metalMoves = IcsModel.METAL_MOVES.map { t(it, 14f, FontWeight.SemiBold, 0x2F9E6A) }
    val stockNames = IcsModel.TICKERS.map { t(it, 14f, FontWeight.SemiBold, INK) }
    val stockPrices = IcsModel.STOCK_PRICES.map { t(it, 14f, FontWeight.Normal, MUTED) }
    val stockMoves = IcsModel.STOCK_MOVES.map { t(it, 14f, FontWeight.SemiBold, 0x2F9E6A) }
}

private class IcsHolder {
    var frame = IcsFrame()
    var running = false
}

/**
 * The Explore screen of an investing app: a turning ring of metal ingots that morph into embossed
 * coins when the "Stocks / Commodities" switch changes.
 *
 * @param autoPlay plays the 10 s switch loop until touched.
 */
@Composable
fun IngotCoinSwitch(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto, reduced) { IcsDriver() }
    val holder = remember { IcsHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { IcsTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(hex(PAPER)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            IcsGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            holder.frame = driver.frame(System.nanoTime() / 1e9, auto, reduced)
            tick.intValue += 1
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        holder.frame = driver.frame(now, auto, reduced)
                        tick.intValue += 1
                        !auto && driver.settled(now)
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun tap(mode: Int) {
            if (driver.tap(mode, now(), auto, reduced)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (!holder.running) wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Explore. Switch between commodities and stocks."
                    customActions = listOf(
                        CustomAccessibilityAction("Show stocks") { tap(1); true },
                        CustomAccessibilityAction("Show commodities") { tap(0); true },
                    )
                }
                .pointerInput(driver, g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val pill = g.pill(down.position.x / d, down.position.y / d)
                        val spin = pill == null && !reduced
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (spin) {
                            driver.beginDrag(now(), auto, reduced)
                            if (!holder.running) wake.intValue += 1
                        }
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            if (spin) driver.drag(((c.position.x - down.position.x) / d).toDouble(), now())
                            if (!c.pressed) break
                            c.consume()
                        }
                        if (spin) {
                            driver.endDrag((tracker.calculateVelocity().x / d).toDouble(), now())
                            if (!holder.running) wake.intValue += 1
                        } else if (pill != null) {
                            val moved = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d)
                            if (moved < 12) tap(pill)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                icsDraw(g, f, texts)
            }
        }
    }
}

// Drawing (in dp).

private val blurPaint = android.graphics.Paint()
private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
private val markMatrix = Matrix()

private fun DrawScope.icsDraw(g: IcsGeo, f: IcsFrame, tx: IcsTexts) {
    drawRect(hex(PAPER), Offset.Zero, Size(g.w.f(), g.h.f()))
    header(g, tx)
    copy(g, f, tx)
    floor(g, f)
    ring(g, f)
    ticker(g, f, tx)
    toggle(g, f, tx)
    cta(g, f, tx)
}

private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, alpha: Float = 1f, anchor: Int = 0) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    val left = if (anchor < 0) x.f() else x.f() - w / 2
    drawText(l, topLeft = Offset(left, y.f() - h / 2), alpha = alpha)
}

/** A soft (blurred) fill of a path, offset by dy. Android's blur radius is 1.5 × SwiftUI's. */
private fun DrawScope.blurred(path: Path, color: Color, radius: Double, dy: Double = 0.0) {
    blurPaint.reset()
    blurPaint.isAntiAlias = true
    blurPaint.color = color.toArgb()
    blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas {
        val c = it.nativeCanvas
        c.save()
        c.translate(0f, dy.f())
        c.drawPath(path.asAndroidPath(), blurPaint)
        c.restore()
    }
}

private fun DrawScope.header(g: IcsGeo, tx: IcsTexts) {
    val y = g.top + 26
    text(tx.brand, 24.0, y, anchor = -1)
    val ax = g.w - 41
    drawCircle(hex(0xE7E4DE), 17f, Offset(ax.f(), y.f()))
    text(tx.avatar, ax, y)
    text(tx.eyebrow, g.cx, g.top + 74)
}

/** The visible copy layers: (mode, opacity, rise). */
private fun copyLayers(f: IcsFrame): List<Triple<Int, Double, Double>> {
    if (f.copyFrom == f.copyTo) return listOf(Triple(f.copyTo, 1.0, 0.0))
    val k = IcsModel.copyFade(f.copyU)
    val out = ArrayList<Triple<Int, Double, Double>>()
    if (k[0] > 0.001) out.add(Triple(f.copyFrom, k[0], 0.0))
    if (k[1] > 0.001) out.add(Triple(f.copyTo, k[1], k[2]))
    return out
}

private fun DrawScope.copy(g: IcsGeo, f: IcsFrame, tx: IcsTexts) {
    for ((mode, alpha, rise) in copyLayers(f)) {
        val a = alpha.f()
        text(tx.headlines[mode][0], g.cx, g.top + 106 + rise, a)
        text(tx.headlines[mode][1], g.cx, g.top + 142 + rise, a)
        text(tx.sublines[mode][0], g.cx, g.top + 184 + rise, a)
        text(tx.sublines[mode][1], g.cx, g.top + 204 + rise, a)
    }
}

/** A soft shadow on the floor under the ring. */
private fun DrawScope.floor(g: IcsGeo, f: IcsFrame) {
    val m = f.m.average()
    val y = g.ringY + IcsModel.lerp(124.0, 100.0, m)
    val w = IcsModel.lerp(310.0, 340.0, m)
    blurred(Path().apply { addOval(Rect((g.cx - w / 2).f(), (y - 16).f(), (g.cx + w / 2).f(), (y + 16).f())) }, Color.Black.copy(alpha = 0.13f), 14.0)
    blurred(Path().apply { addOval(Rect((g.cx - w * 0.36).f(), (y - 5).f(), (g.cx + w * 0.36).f(), (y + 5).f())) }, Color.Black.copy(alpha = 0.1f), 5.0)
}

private fun polygon(pts: FloatArray) = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    close()
}

private fun DrawScope.ring(g: IcsGeo, f: IcsFrame) {
    for (p in IcsModel.pieces(f, g.cx, g.ringY)) {
        if (p.opacity < 0.999) {
            drawIntoCanvas { c ->
                c.saveLayer(Rect(0f, 0f, g.w.f(), g.h.f()), Paint().apply { alpha = p.opacity.f() })
                piece(p)
                c.restore()
            }
        } else {
            piece(p)
        }
    }
}

private fun DrawScope.piece(p: IcsPiece) {
    if (p.silhouette.size >= 6) drawPath(polygon(p.silhouette), col(p.silhouetteColor))
    for (q in p.quads) drawPath(polygon(q.points), col(q.color))
    val style = p.index % IcsModel.STYLES
    for (cap in p.caps) {
        val path = polygon(cap.points)
        val span = hypot(cap.toX - cap.fromX, cap.toY - cap.fromY)
        if (span > 0.5) {
            val stops = Array(cap.colors.size) { IcsModel.CAP_STOPS[it].f() to col(cap.colors[it]) }
            drawPath(path, Brush.linearGradient(*stops, start = Offset(cap.fromX.f(), cap.fromY.f()), end = Offset(cap.toX.f(), cap.toY.f())))
        } else {
            drawPath(path, col(cap.colors[4]))
        }
        if (hypot(cap.bottomX - cap.topX, cap.bottomY - cap.topY) > 0.5) {
            drawPath(
                path,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.White.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.07f),
                    start = Offset(cap.topX.f(), cap.topY.f()), end = Offset(cap.bottomX.f(), cap.bottomY.f()),
                ),
            )
        }
        if (cap.ingotAlpha > 0.01) mark(cap, IcsArt.ingotMarks[style], IcsArt.INGOT_W, IcsArt.INGOT_H, cap.ingotAlpha)
        if (cap.coinAlpha > 0.01) mark(cap, IcsArt.coinMarks[style], IcsArt.COIN_W, IcsArt.COIN_W, cap.coinAlpha)
    }
}

/** Draws a mark's bitmap through the face's projection. */
private fun DrawScope.mark(cap: IcsCap, bmp: android.graphics.Bitmap, w: Float, h: Float, alpha: Double) {
    val t = cap.transform
    markMatrix.setValues(floatArrayOf(t[0].f(), t[2].f(), t[4].f(), t[1].f(), t[3].f(), t[5].f(), 0f, 0f, 1f))
    bitmapPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    drawIntoCanvas {
        val c = it.nativeCanvas
        c.save()
        c.concat(markMatrix)
        c.drawBitmap(bmp, null, RectF(-w / 2, -h / 2, w / 2, h / 2), bitmapPaint)
        c.restore()
    }
}

/** The price of the piece in front. */
private fun DrawScope.ticker(g: IcsGeo, f: IcsFrame, tx: IcsTexts) {
    val y = g.tickerY
    val w = 214.0
    val chip = Path().apply {
        addRoundRect(RoundRect((g.cx - w / 2).f(), (y - 16).f(), (g.cx + w / 2).f(), (y + 16).f(), CornerRadius(16f, 16f)))
    }
    drawPath(chip, Color.White.copy(alpha = 0.85f))
    drawPath(chip, hex(0xE3DFD8), style = Stroke(1f))
    val i = IcsModel.frontIndex(f)
    val alpha = IcsModel.frontAlpha(f).f()
    val style = i % IcsModel.STYLES
    val stock = f.m[i] >= 0.5
    val base = IcsRgb.hex(if (stock) IcsModel.COIN_HEX[style] else IcsModel.INGOT_HEX[style])
    val name = if (stock) tx.stockNames[style] else tx.metalNames[style]
    val price = if (stock) tx.stockPrices[style] else tx.metalPrices[style]
    val move = if (stock) tx.stockMoves[style] else tx.metalMoves[style]
    val wn = name.size.width.toDouble()
    val wp = price.size.width.toDouble()
    val wm = move.size.width.toDouble()
    val total = 8 + 7 + wn + 9 + wp + 9 + wm
    var x = g.cx - total / 2
    drawCircle(col(base.mix(IcsRgb(0.0, 0.0, 0.0), 0.08), alpha), 4f, Offset((x + 4).f(), y.f()))
    x += 15
    text(name, x, y, alpha, -1)
    x += wn + 9
    text(price, x, y, alpha, -1)
    x += wp + 9
    text(move, x, y, alpha, -1)
}

/** "Stocks / Commodities": outlined pills with a dark capsule sliding between them. */
private fun DrawScope.toggle(g: IcsGeo, f: IcsFrame, tx: IcsTexts) {
    val stocks = g.stocksRect
    val comm = g.commRect
    for (r in listOf(stocks, comm)) {
        drawRoundRect(hex(INK), Offset(r.left + 0.75f, r.top + 0.75f), Size(r.width - 1.5f, r.height - 1.5f), CornerRadius(22f, 22f), style = Stroke(1.5f))
    }
    val s = f.sel
    val x = IcsModel.lerp(comm.left.toDouble(), stocks.left.toDouble(), s).f()
    val w = max(44.0, IcsModel.lerp(comm.width.toDouble(), stocks.width.toDouble(), s)).f()
    val capsule = Path().apply { addRoundRect(RoundRect(x, stocks.top, x + w, stocks.bottom, CornerRadius(22f, 22f))) }
    // Dark labels everywhere, white ones only inside the capsule.
    val rects = listOf(comm, stocks)
    for (k in 0..1) text(tx.pillsDark[k], rects[k].center.x.toDouble(), rects[k].center.y.toDouble())
    drawPath(capsule, hex(INK))
    clipPath(capsule) {
        for (k in 0..1) text(tx.pillsLight[k], rects[k].center.x.toDouble(), rects[k].center.y.toDouble())
    }
}

private fun DrawScope.cta(g: IcsGeo, f: IcsFrame, tx: IcsTexts) {
    val r = RoundRect(24f, (g.ctaY - 28).f(), (g.w - 24).f(), (g.ctaY + 28).f(), CornerRadius(28f, 28f))
    val pill = Path().apply { addRoundRect(r) }
    blurred(pill, Color.Black.copy(alpha = 0.18f), 14.0, 8.0)
    drawPath(pill, hex(INK))
    translate(0f, 0.5f) {
        drawRoundRect(
            Color.White.copy(alpha = 0.12f), Offset(24.5f, (g.ctaY - 27.5).f()), Size((g.w - 49).f(), 55f),
            CornerRadius(27.5f, 27.5f), style = Stroke(1f),
        )
    }
    for ((mode, alpha, rise) in copyLayers(f)) {
        text(tx.ctas[mode], g.cx, g.ctaY + rise, alpha.f())
    }
}
