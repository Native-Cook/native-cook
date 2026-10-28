package com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder

import android.app.Activity
import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.blob
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.ingredient
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.omelette
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.piece
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.plate
import com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder.OtbArt.ringShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Omelette Topping Builder — step 2 of an invented breakfast-ordering app ("Early Fold"): fold
 fillings into your omelette (a port of OmeletteToppingBuilder.swift).

 Look: a light grey screen, a title bar ("Omelette", a round back button, "Step 2 of 3"), a white
 ceramic plate on a pale placemat with a folded yellow omelette, all drawn in code. Below, a shelf
 of five ingredients in a ring (Red Bell Pepper, Baby Spinach, Smoked Bacon, Cherry Tomato, Button
 Mushroom), the centred one on a white disc with its name and add-on price, and a dark "Continue"
 button carrying the total.

 Hero (points / scatter): the centred ingredient is tossed onto the omelette and bursts into seeded
 pieces — red pepper cubes, spinach leaves, bacon strips, tomato halves, mushroom slices — that hop
 outward, spin to their angle and land with a squash-and-bounce. They stay and accumulate; the
 omelette squashes on each impact and the total rolls up. The ingredient pops back on the shelf with
 a check, and the shelf slides on to the next one.

 autoPlay: LOOP = 9 s (rest, add pepper, spinach and bacon, hold, everything lifts off and the shelf
 glides back to the pepper, rest). OmeletteToppingBuilderMotion.kt has the script.
 Touch: tap the centred ingredient to add it (an added one gives a small shake); tap a side one to
 centre it; swipe the shelf; tap the plate to lift off the last topping. A light haptic when a
 topping lands and on undo. 2 s after the last touch every topping lifts off, the shelf springs back
 to the pepper and the loop restarts.
 Reduced motion (animator duration scale 0): the rest frame, still; a tap fades the pieces in at
 their spots, without the flight.

 Per frame the model runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private const val PAPER = 0xEDEDECL
private const val INK = 0x17181CL
private const val MUTED = 0x8C8C90L

private const val SPACING = 124.0
private const val ICON = 80.0

private class OtbGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx get() = w / 2
    val plateY get() = top + 268
    val shelfY get() = top + 536
    val nameY get() = top + 614
    val priceY get() = top + 637
    val buttonY get() = h - bottom - 72
}

private class OtbHolder {
    var frame = OtbFrame()
    var running = false
}

/** Text drawn on the canvas, measured once at 1 dp = 1 px. */
private class OtbTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, color: Long, tnum: Boolean = false) = m.measure(
        s,
        TextStyle(color = otbHex(color), fontSize = size.sp, fontWeight = weight, fontFeatureSettings = if (tnum) "tnum" else null),
        density = unit,
    )

    val title = t("Omelette", 20f, FontWeight.SemiBold, INK)
    val step = t("Step 2 of 3", 13f, FontWeight.Normal, MUTED)
    val hint = t("Tap a filling to fold it in", 15f, FontWeight.Normal, MUTED)
    val cont = t("Continue", 17f, FontWeight.SemiBold, 0xFFFFFF)
    val names = OtbMotion.names.map { t(it, 17f, FontWeight.SemiBold, INK) }
    val prices = OtbMotion.cents.map { t("+" + OtbMotion.money(it), 14f, FontWeight.Normal, MUTED) }
    private val totals = HashMap<Int, TextLayoutResult>()
    fun total(cents: Int) = totals.getOrPut(cents) { t(OtbMotion.money(cents), 17f, FontWeight.SemiBold, 0xFFFFFF, tnum = true) }
}

/**
 * Step 2 of a breakfast order: tap ingredients to toss them onto the omelette, where they scatter.
 *
 * @param autoPlay plays the 9 s add-three-toppings loop until touched.
 */
@Composable
fun OmeletteToppingBuilder(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
    val driver = remember(auto, reduced) { OtbDriver() }
    val holder = remember { OtbHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { OtbTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(otbHex(PAPER)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            OtbGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            fun publish(now: Double) {
                holder.frame = driver.frame(now, auto, reduced)
                tick.intValue += 1
            }
            publish(System.nanoTime() / 1e9)
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        publish(now)
                        !auto && !driver.touching && now - driver.lastTouch > 4.4
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun act(block: (OtbSim, Double) -> Unit): Boolean {
            val t = now()
            block(driver.session(t, auto, reduced), t)
            driver.touched(t)
            if (!holder.running) wake.intValue += 1
            return true
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Omelette fillings. Adds the centred filling to the omelette."
                    role = Role.Button
                    onClick { act { s, t -> s.tapCentre(t) } }
                    customActions = listOf(
                        CustomAccessibilityAction("Next filling") { act { s, t -> s.select(1, t) } },
                        CustomAccessibilityAction("Remove last filling") { act { s, t -> s.removeLast(t) } },
                    )
                }
                .pointerInput(driver, g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.toDouble()
                        val px = down.position.x / d
                        val py = down.position.y / d
                        val onShelf = abs(py - g.shelfY) < 66
                        val onPlate = !onShelf && hypot(px - g.cx, py - g.plateY) < 150
                        if (!onShelf && !onPlate) return@awaitEachGesture
                        val sim = driver.session(now(), auto, reduced)
                        if (onShelf) sim.beginDrag(now())
                        val s0 = sim.drag ?: 0.0
                        driver.touching = true
                        driver.touched(now())
                        if (!holder.running) wake.intValue += 1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = 0.0
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(c.uptimeMillis, c.position)
                                val dx = (c.position.x - down.position.x) / d
                                val dy = (c.position.y - down.position.y) / d
                                moved = max(moved, hypot(dx, dy))
                                if (onShelf && abs(dx) > 8) sim.dragTo(s0 - dx / SPACING)
                                driver.touched(now())
                                if (!c.pressed) break
                                c.consume()
                            }
                        } finally {
                            val t = now()
                            if (onShelf) {
                                if (moved < 8) {
                                    sim.release(t, 0.0)
                                    val dx = px - g.cx
                                    if (abs(dx) < 56) {
                                        if (sim.tapCentre(t)) {
                                            view.postDelayed(
                                                { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                                                if (reduced) 0L else (OtbMotion.LAND * 1000).toLong(),
                                            )
                                        }
                                    } else {
                                        val k = max(-2, min(2, OtbMotion.round(dx / SPACING).toInt()))
                                        sim.select(if (k == 0) (if (dx > 0) 1 else -1) else k, t)
                                    }
                                } else {
                                    val v = -tracker.calculateVelocity().x / d / SPACING
                                    sim.release(t, min(12.0, max(-12.0, v)))
                                }
                            } else if (moved < 10 && sim.removeLast(t)) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            driver.touching = false
                            driver.touched(t)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                otbDraw(g, f, texts)
            }
        }
    }
}

// Drawing (in dp).

private fun Double.f() = toFloat()

private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, anchor: Int = 0, alpha: Float = 1f) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    val left = when (anchor) {
        -1 -> x.toFloat()
        1 -> x.toFloat() - w
        else -> x.toFloat() - w / 2
    }
    drawText(l, topLeft = Offset(left, y.toFloat() - h / 2), alpha = alpha)
}

private fun DrawScope.otbDraw(g: OtbGeo, f: OtbFrame, tx: OtbTexts) {
    drawRect(otbHex(PAPER), Offset.Zero, Size(g.w.f(), g.h.f()))
    header(g, tx)
    plate(g.cx, g.plateY)
    plateContents(g, f)
    shelf(g, f)
    labels(g, f, tx)
    button(g, f, tx)
    for (tp in f.toppings) flight(g, f, tp)
}

private fun DrawScope.header(g: OtbGeo, tx: OtbTexts) {
    val y = g.top + 28
    ringShadow(40.0, y + 2, 19.0, 25.0, 0.08)
    drawCircle(otbHex(0xFFFFFF), 20f, Offset(40f, y.f()))
    val chev = Path().apply {
        moveTo(43.5f, (y - 6.5).f())
        lineTo(37f, y.f())
        lineTo(43.5f, (y + 6.5).f())
    }
    drawPath(chev, otbHex(INK), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    text(tx.title, g.cx, y)
    text(tx.step, g.w - 24, y, anchor = 1)
    text(tx.hint, g.cx, g.top + 64)
}

/** The omelette's squash: a damped dip on each impact. */
private fun squash(f: OtbFrame): Double {
    if (f.reduced) return 0.0
    var k = 0.0
    for (tp in f.toppings) {
        val tau = f.t - tp.ta - OtbMotion.LAND
        if (tau > 0 && tau < 1.5) k += 0.035 * exp(-7 * tau) * sin(20 * tau)
    }
    return k
}

/** Rotates a vector by -a (a screen vector into a frame turned by a). */
private fun unturn(x: Double, y: Double, a: Double) = (x * cos(a) + y * sin(a)) to (-x * sin(a) + y * cos(a))

private fun DrawScope.plateContents(g: OtbGeo, f: OtbFrame) {
    val k = squash(f)
    withTransform({
        translate(g.cx.f(), g.plateY.f())
        scale((1 - k).f(), (1 - k).f(), pivot = Offset.Zero)
        rotate((OtbMotion.ANGLE * 180 / PI).f(), pivot = Offset.Zero)
        translate(0f, (-OtbMotion.CENTROID_Y).f())
    }) {
        omelette(unturn(2.0, 5.0, OtbMotion.ANGLE), unturn(0.0, 1.5, OtbMotion.ANGLE))
        if (!f.reduced) {
            for (tp in f.toppings) {
                // A soft puff where the ingredient lands.
                val u = (f.t - tp.ta - OtbMotion.LAND) / 0.36
                if (u < 0 || u >= 1) continue
                val (lx, ly) = OtbMotion.landing(tp.kind)
                val e = OtbMotion.easeOut(u)
                drawCircle(
                    otbHex(0xFFF6D6, (0.55 * (1 - u)).f()), (10 + 46 * e).f(), Offset(lx.f(), ly.f()),
                    style = Stroke((7 * (1 - e) + 0.5).f()),
                )
            }
        }
        for (tp in f.toppings) pieces(f, tp)
    }
}

private fun DrawScope.pieces(f: OtbFrame, tp: OtbTopping) {
    val k = tp.kind
    val t = f.t
    val (lx0, ly0) = OtbMotion.landing(k)
    val tau = t - tp.ta - OtbMotion.LAND
    if (!f.reduced && tau < 0) return
    for (p in OtbMotion.pieces[k]) {
        var alpha = 1.0
        var x = p.x
        var y = p.y
        var sc = 1.0
        var rot = p.rot
        var hop = 0.0
        if (f.reduced) {
            alpha = OtbMotion.clamp01((t - tp.ta) / 0.3)
        } else {
            val dist = hypot(p.x - lx0, p.y - ly0)
            val dur = 0.28 + 0.26 * dist / OtbMotion.R
            val u = OtbMotion.clamp01((tau - p.delay) / dur)
            val e = OtbMotion.easeOut(u)
            x = lx0 + (p.x - lx0) * e
            y = ly0 + (p.y - ly0) * e
            hop = sin(PI * u) * (8 + 18 * dist / OtbMotion.R)
            sc = 1 + hop / 60
            rot = p.rot + (1 - e) * p.spin
            val tl = tau - p.delay - dur
            if (tl > 0) sc *= 1 + 0.16 * exp(-8 * tl) * sin(-26 * tl)
        }
        val tr = tp.tr
        if (tr != null) {
            val w = OtbMotion.clamp01((t - tr - (if (f.reduced) 0.0 else p.lift)) / (if (f.reduced) 0.3 else OtbMotion.LIFT_DUR))
            if (w >= 1) continue
            if (f.reduced) {
                alpha *= 1 - w
            } else {
                val lift = OtbMotion.easeIn(w)
                alpha *= 1 - lift
                sc *= 1 + 0.5 * lift
                val up = unturn(0.0, -18 * lift, OtbMotion.ANGLE)
                x += up.first
                y += up.second
                hop += 30 * lift
            }
        }
        if (alpha <= 0.001) continue
        val s = sc * p.size * OtbMotion.pieceScale[k]
        val turn = OtbMotion.ANGLE + rot
        val light = unturn(-0.55, -0.83, turn)
        val sh = unturn(1.0 + hop * 0.35, 1.6 + hop * 0.6, turn)
        withTransform({
            translate(x.f(), y.f())
            rotate((rot * 180 / PI).f(), pivot = Offset.Zero)
            scale(s.f(), s.f(), pivot = Offset.Zero)
        }) {
            piece(k, p, light.first, light.second, sh.first / s, sh.second / s, max(0.0, 1 - hop / 40), alpha.f())
        }
    }
}

/** The omelette point (x, y) on the screen, without the squash. */
private fun world(g: OtbGeo, x: Double, y: Double): Pair<Double, Double> {
    val a = OtbMotion.ANGLE
    val dy = y - OtbMotion.CENTROID_Y
    return (g.cx + x * cos(a) - dy * sin(a)) to (g.plateY + x * sin(a) + dy * cos(a))
}

/** The latest topping of kind k that has been added by t. */
private fun latest(f: OtbFrame, k: Int) = f.toppings.lastOrNull { it.kind == k && it.ta <= f.t }

private fun DrawScope.shelf(g: OtbGeo, f: OtbFrame) {
    val y = g.shelfY
    ringShadow(g.cx, y + 8, 44.0, 66.0, 0.07)
    ringShadow(g.cx, y + 2, 47.0, 52.0, 0.10)
    drawCircle(OtbArt.radial(listOf(st(0xFFFFFF, 1.0, 0.0), st(0xF7F7F6, 1.0, 1.0)), g.cx - 14, y - 16, 64.0), 48f, Offset(g.cx.f(), y.f()))
    for (i in 0 until OtbMotion.KINDS) {
        val d = OtbMotion.slot(i, f.s)
        val ad = abs(d)
        if (ad > 2.1) continue
        var x = g.cx + d * SPACING
        val sc0 = 1 - 0.28 * min(1.0, ad)
        val alpha = 1 - OtbMotion.smooth((ad - 1.2) / 0.8)
        var pop = 1.0
        var badge = 0.0
        val tp = latest(f, i)
        if (tp != null) {
            val tau = f.t - tp.ta
            if (f.reduced) {
                badge = OtbMotion.clamp01(tau / 0.3)
            } else {
                pop = if (tau < OtbMotion.RESTOCK_AT) 0.0 else OtbMotion.easeOutBack((tau - OtbMotion.RESTOCK_AT) / OtbMotion.RESTOCK_DUR)
                badge = pop
            }
            val tr = tp.tr
            if (tr != null) badge *= 1 - OtbMotion.clamp01((f.t - tr) / 0.2)
        }
        if (f.shakeKind == i) {
            val tau = f.t - f.shakeAt
            if (tau >= 0 && tau < OtbMotion.SHAKE_DUR) x += 7 * sin(tau * 42) * (1 - tau / OtbMotion.SHAKE_DUR)
        }
        val size = ICON * sc0 * pop
        if (size < 0.5 || alpha <= 0.001) continue
        withTransform({
            translate(x.f(), y.f())
            scale(size.f(), size.f(), pivot = Offset.Zero)
        }) { ingredient(i, true, alpha.f()) }
        if (badge > 0.01) {
            val bx = x + 27 * sc0
            val by = y - 26 * sc0
            val r = 10 * sc0 * badge
            val a = alpha.f()
            drawCircle(otbHex(0x000000, 0.10f), (r + 2.5).f(), Offset(bx.f(), (by + 1).f()), alpha = a)
            drawCircle(otbHex(0xFFFFFF), (r + 2).f(), Offset(bx.f(), by.f()), alpha = a)
            drawCircle(otbHex(INK), r.f(), Offset(bx.f(), by.f()), alpha = a)
            val tick = Path().apply {
                moveTo((bx - r * 0.42).f(), (by + r * 0.02).f())
                lineTo((bx - r * 0.1).f(), (by + r * 0.34).f())
                lineTo((bx + r * 0.45).f(), (by - r * 0.32).f())
            }
            drawPath(tick, otbHex(0xFFFFFF), alpha = a, style = Stroke(max(0.5, 2 * sc0 * badge).f(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.labels(g: OtbGeo, f: OtbFrame, tx: OtbTexts) {
    for (i in 0 until OtbMotion.KINDS) {
        val d = OtbMotion.slot(i, f.s)
        val a = OtbMotion.clamp01(1 - abs(d) * 1.8)
        if (a <= 0.001) continue
        val x = g.cx + 40 * d
        text(tx.names[i], x, g.nameY, alpha = a.f())
        text(tx.prices[i], x, g.priceY, alpha = a.f())
    }
}

private fun DrawScope.button(g: OtbGeo, f: OtbFrame, tx: OtbTexts) {
    val rect = Rect(20f, g.buttonY.f(), (g.w - 20).f(), (g.buttonY + 56).f())
    val shape = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(18f))) }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(46, 0, 0, 0)
        maskFilter = BlurMaskFilter(17f, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas {
        val c = it.nativeCanvas
        c.save()
        c.translate(0f, 6f)
        c.drawPath(shape.asAndroidPath(), paint)
        c.restore()
    }
    drawPath(shape, OtbArt.linear(listOf(st(0x2A2B31, 1.0, 0.0), st(0x17181C, 1.0, 1.0)), 0.0, rect.top.toDouble(), 0.0, rect.bottom.toDouble()))
    clipPath(shape) {
        withTransform({ translate(0f, 1f) }) {
            drawPath(shape, otbHex(0xFFFFFF, 0.10f), style = Stroke(1.2f))
        }
    }
    val my = rect.center.y.toDouble()
    text(tx.cont, rect.left + 24.0, my, anchor = -1)

    val now = OtbMotion.total(f, f.t)
    val right = rect.right - 24.0
    clipRect(rect.center.x, rect.top + 14, rect.right, rect.bottom - 14) {
        val tc = OtbMotion.lastChange(f, f.t)
        if (tc != null && f.t - tc < OtbMotion.PRICE_ROLL) {
            val u = OtbMotion.easeOut((f.t - tc) / OtbMotion.PRICE_ROLL)
            val old = OtbMotion.total(f, tc - 1e-6)
            val dir = if (now >= old) 1.0 else -1.0
            text(tx.total(old), right, my - 22 * u * dir, anchor = 1, alpha = (1 - u).f())
            text(tx.total(now), right, my + 22 * (1 - u) * dir, anchor = 1, alpha = u.f())
        } else {
            text(tx.total(now), right, my, anchor = 1)
        }
    }
}

/** The ingredient tossed from the shelf onto the omelette, then collapsing into its pieces. */
private fun DrawScope.flight(g: OtbGeo, f: OtbFrame, tp: OtbTopping) {
    if (f.reduced) return
    val tau = f.t - tp.ta
    if (tau < 0 || tau >= OtbMotion.LAND + OtbMotion.COLLAPSE) return
    val sx = g.cx + tp.sx * SPACING
    val sy = g.shelfY
    val (lx, ly) = OtbMotion.landing(tp.kind)
    val (wx, wy) = world(g, lx, ly)
    var x = sx
    var y = sy
    var sc: Double
    var rot = 0.0
    var alpha = 1.0
    var lift = 0.0
    if (tau < OtbMotion.PRESS) {
        sc = 1 - 0.1 * OtbMotion.easeOut(tau / OtbMotion.PRESS)
    } else if (tau < OtbMotion.LAND) {
        val e = OtbMotion.easeInOut((tau - OtbMotion.PRESS) / (OtbMotion.LAND - OtbMotion.PRESS))
        x = OtbMotion.bezier(sx, wx + 50, wx, e)
        y = OtbMotion.bezier(sy, wy - 70, wy, e)
        sc = 0.9 - 0.18 * e + 0.35 * sin(PI * e)
        rot = 25 * PI / 180 * e
        lift = sin(PI * e)
    } else {
        val v = (tau - OtbMotion.LAND) / OtbMotion.COLLAPSE
        x = wx
        y = wy
        sc = 0.72 * (1 - OtbMotion.easeIn(v))
        rot = 25 * PI / 180
        alpha = 1 - v
    }
    blob(x + 6 + 16 * lift, y + 34 * sc + 22 * lift, max(1e-3, 28 * sc), max(1e-3, 8 * sc), 0x000000, 0.16 * (1 - 0.5 * lift) * alpha, 1f)
    val size = ICON * sc
    if (size < 0.5) return
    withTransform({
        translate(x.f(), y.f())
        rotate((rot * 180 / PI).f(), pivot = Offset.Zero)
        scale(size.f(), size.f(), pivot = Offset.Zero)
    }) { ingredient(tp.kind, false, alpha.f()) }
}
