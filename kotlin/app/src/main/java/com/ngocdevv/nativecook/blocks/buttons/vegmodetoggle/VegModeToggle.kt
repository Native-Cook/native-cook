package com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle.VmtArt.broccoli
import com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle.VmtArt.drumstick
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Veg Mode Toggle — the diet switch of an invented food-delivery menu ("Tiffin Lane")
 (a port of VegModeToggle.swift).

 Look: a warm white screen, "What's for dinner?", and a big pill toggle: a light grey track with a
 black knob covering two thirds of it. The free end holds the mode's dish, drawn in code, popping out
 of the toggle like a sticker: a glossy broccoli bunch for Veg, a drumstick and a halved egg for
 Non veg. The labels sit in a strip that slides the other way inside the knob, so the knob is a
 window that swaps them. Below, "24 veg dishes" rolls to "38 non-veg dishes" and the three dish rows
 swap with a stagger.

 Flip: the knob slides on a UI spring and stretches in travel, the old dish sinks below the track's
 bottom line, the new one rises from it with overshoot, a lean that settles and a squash; its egg or
 floret follows 60 ms late (VegModeToggleMotion.kt has the springs and the script).

 autoPlay: LOOP = 7 s (tap to Non veg, hold, drag back, release to Veg, rest).
 Touch: tap the toggle to flip; drag the knob to scrub between the two dishes, release to snap to
 the nearer end (with the finger's speed). A light haptic on each commit. 2 s after the last touch
 it springs back to Veg and the loop restarts.
 Reduced motion (animator duration scale 0): the Veg state, still; a tap switches with short springs
 and the dishes fade.

 Per frame the model runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private const val PAPER = 0xF6F5F1L
private const val INK = 0x171717L
private const val MUTED = 0x8A877FL
private const val RULE = 0xE6E3DCL

private const val TRACK_W = 252.0
private const val TRACK_H = 104.0
private const val KNOB_W = 168.0

private class VmtGeo(val w: Double, val h: Double, val top: Double) {
    val cx get() = w / 2
    val trackX get() = (w - TRACK_W) / 2
    val trackY get() = top + 248
    val headerY get() = top + 440
    val rowsY get() = top + 470
}

private class VmtHolder {
    var frame = VmtFrame()
    var running = false
}

private class VmtDish(val name: TextLayoutResult, val note: TextLayoutResult, val price: TextLayoutResult)

/** Text drawn on the canvas, measured once at 1 dp = 1 px. */
private class VmtTexts(m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private val t = { s: String, size: Float, weight: FontWeight, color: Long, track: Float, tnum: Boolean ->
        m.measure(
            s,
            TextStyle(
                color = vmtHex(color), fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp,
                fontFeatureSettings = if (tnum) "tnum" else null,
            ),
            density = unit,
        )
    }
    private fun t(s: String, size: Float, weight: FontWeight, color: Long, track: Float = 0f) =
        t(s, size, weight, color, track, false)

    val deliver = t("Deliver to", 13f, FontWeight.Normal, MUTED)
    val home = t("Home · 2.1 km", 16f, FontWeight.SemiBold, INK)
    val eyebrow = t("TIFFIN LANE", 12f, FontWeight.SemiBold, 0xA09C93, track = 1.6f)
    val title = t("What's for dinner?", 30f, FontWeight.Bold, INK, track = -0.4f)
    val veg = t("Veg", 25f, FontWeight.Medium, 0xFAFAF7)
    val nonVeg = t("Non veg", 25f, FontWeight.Medium, 0xFAFAF7)
    val counts = (24..39).associateWith { t("$it", 17f, FontWeight.SemiBold, INK, 0f, true) }
    val words = listOf(t("veg dishes", 17f, FontWeight.SemiBold, INK), t("non-veg dishes", 17f, FontWeight.SemiBold, INK))
    val sort = t("Sort · Popular", 14f, FontWeight.Normal, MUTED)
    val dishes = listOf(
        listOf(
            Triple("Paneer tikka bowl", "Charred paneer, mint rice", "$9.50"),
            Triple("Garden pulao", "Saffron rice, peas, cashew", "$8.00"),
            Triple("Masala corn cakes", "Crisp, with green chutney", "$6.50"),
        ),
        listOf(
            Triple("Smoky drumsticks", "Two pieces, tandoor spice", "$11.00"),
            Triple("Egg curry bowl", "Two eggs, onion gravy", "$9.00"),
            Triple("Chicken pulao", "Saffron rice, fried onion", "$10.50"),
        ),
    ).map { side ->
        side.map {
            VmtDish(
                t(it.first, 17f, FontWeight.SemiBold, INK),
                t(it.second, 14f, FontWeight.Normal, MUTED),
                t(it.third, 16f, FontWeight.SemiBold, INK),
            )
        }
    }
}

/**
 * The diet toggle of a food-delivery menu, with the mode's dish popping out of its free end.
 *
 * @param autoPlay plays the 7 s flip-and-drag loop until touched.
 */
@Composable
fun VegModeToggle(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto, reduced) { VmtDriver() }
    val holder = remember { VmtHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { VmtTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(vmtHex(PAPER)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop) {
            VmtGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble())
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

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Veg mode. Switches the menu between veg and non-veg dishes."
                    role = Role.Switch
                    onClick {
                        val now = System.nanoTime() / 1e9
                        driver.session(now, auto, reduced).tap(now)
                        if (!holder.running) wake.intValue += 1
                        true
                    }
                }
                .pointerInput(driver, g, auto, reduced) {
                    fun now() = System.nanoTime() / 1e9
                    val travel = TRACK_W - KNOB_W
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.toDouble()
                        val px = down.position.x / d
                        val py = down.position.y / d
                        val inside = px >= g.trackX - 24 && px <= g.trackX + TRACK_W + 24 &&
                            py >= g.trackY - 36 && py <= g.trackY + TRACK_H + 24
                        if (!inside) return@awaitEachGesture
                        val sim = driver.session(now(), auto, reduced)
                        driver.touching = true
                        sim.beginDrag(now())
                        val p0 = sim.drag?.p ?: sim.mode.toDouble()
                        val mode0 = sim.mode
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
                                if (!c.pressed) break
                                c.consume()
                                sim.dragTo(vmtRubber(p0 - dx / travel))
                                driver.touched(now())
                            }
                        } finally {
                            val t = now()
                            if (moved < 8) {
                                sim.release(t, 0.0, target = 1 - mode0, delay = VmtMotion.ENTER_DELAY)
                            } else {
                                val pv = -tracker.calculateVelocity().x / d / travel
                                sim.release(t, min(12.0, max(-12.0, pv)))
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            driver.touching = false
                            driver.touched(t)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                vmtDraw(g, f, texts)
            }
        }
    }
}

/** Past either end the knob gives a little, then holds. */
private fun vmtRubber(raw: Double): Double = when {
    raw < 0 -> { val o = -raw; -0.5 * o / (1 + o / 0.25) }
    raw > 1 -> { val o = raw - 1; 1 + 0.5 * o / (1 + o / 0.25) }
    else -> raw
}

// Drawing (in dp).

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

private fun DrawScope.vmtDraw(g: VmtGeo, f: VmtFrame, tx: VmtTexts) {
    drawRect(vmtHex(PAPER), Offset.Zero, Size(g.w.toFloat(), g.h.toFloat()))
    header(g, tx)
    toggle(g, f, tx)
    list(g, f, tx)
}

private fun DrawScope.header(g: VmtGeo, tx: VmtTexts) {
    text(tx.deliver, 24.0, g.top + 18, anchor = -1)
    text(tx.home, 24.0, g.top + 38, anchor = -1)
    val sx = (g.w - 44).toFloat()
    val sy = (g.top + 28).toFloat()
    drawCircle(vmtHex(0xDEDBD4), 20f, Offset(sx, sy), style = Stroke(1f))
    drawCircle(vmtHex(INK), 6.5f, Offset(sx - 1.5f, sy - 1.5f), style = Stroke(1.8f))
    drawLine(vmtHex(INK), Offset(sx + 3.3f, sy + 3.3f), Offset(sx + 7f, sy + 7f), 1.8f, StrokeCap.Round)
    text(tx.eyebrow, g.cx, g.top + 104)
    text(tx.title, g.cx, g.top + 136)
}

private fun roundRect(x: Double, y: Double, w: Double, h: Double) = Path().apply {
    val r = (h / 2).toFloat()
    addRoundRect(RoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), CornerRadius(r, r)))
}

private val vmtBlur4 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    style = android.graphics.Paint.Style.STROKE
    strokeWidth = 6f
    color = android.graphics.Color.argb(26, 0, 0, 0)
    maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
}

private val vmtKnobShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = vmtHex(0x1B1B1B).toArgb()
    setShadowLayer(5f, 0f, 2f, android.graphics.Color.argb(41, 0, 0, 0))
}

private fun DrawScope.toggle(g: VmtGeo, f: VmtFrame, tx: VmtTexts) {
    val h = TRACK_H
    val w = TRACK_W
    val k = KNOB_W
    translate(g.trackX.toFloat(), g.trackY.toFloat()) {
        // Track, with a soft inner shadow along its top.
        val track = roundRect(0.0, 0.0, w, h)
        drawPath(track, vmtHex(0xE7E5DF))
        clipPath(track) {
            val shifted = roundRect(0.0, 3.0, w, h)
            drawIntoCanvas { it.nativeCanvas.drawPath(shifted.asAndroidPath(), vmtBlur4) }
        }

        // Knob: slides by p, stretches with its speed, shrinks a little while pressed.
        val travel = w - k
        val kc = w - k / 2 - travel * f.p
        val kw = k * (1 + 0.10 * min(1.0, abs(f.pv) / 5))
        val ps = 1 - 0.03 * f.press
        val left = min(max(kc - kw / 2, -10.0), w - kw + 10)
        val rx = left + kw * (1 - ps) / 2
        val ry = h * (1 - ps) / 2
        val rw = kw * ps
        val rh = h * ps
        val knob = roundRect(rx, ry, rw, rh)

        dishes(f, front = false)

        drawIntoCanvas { it.nativeCanvas.drawPath(knob.asAndroidPath(), vmtKnobShadow) }
        drawPath(
            knob,
            Brush.verticalGradient(listOf(vmtHex(0x2B2B2B), vmtHex(0x111111)), ry.toFloat(), (ry + rh).toFloat()),
        )
        clipPath(knob) {
            translate(0f, 1.2f) {
                drawPath(
                    knob,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f)),
                        (ry - 1.2).toFloat(), (ry - 1.2 + h * 0.45).toFloat(),
                    ),
                    style = Stroke(1.5f),
                )
            }
            // The labels ride a strip that moves against the knob: the knob is their window.
            // Each label fades as it leaves the middle, so a half-way knob is not two cut words.
            for ((label, dx) in listOf(tx.veg to f.p * k, tx.nonVeg to -(1 - f.p) * k)) {
                val a = 1 - VmtMotion.smooth(0.25, 0.6, abs(dx) / k)
                if (a > 0.001) text(label, kc + dx, h / 2, alpha = a.toFloat())
            }
        }

        dishes(f, front = true)
    }
}

private data class VmtItem(val on: Boolean, val a: Double, val v: Double, val a2: Double, val veg: Boolean)

/** The dishes on one side of the knob, cut by the track's bottom line, with their soft shadow. */
private fun DrawScope.dishes(f: VmtFrame, front: Boolean) {
    val h = TRACK_H.toFloat()
    clipRect(-400f, -400f, 800f, h) {
        val items = listOf(
            VmtItem(f.bFront == front, f.b, f.bv, f.b2, true),
            VmtItem(f.mFront == front, f.m, f.mv, f.m2, false),
        )
        for ((on, a, v, a2, veg) in items) {
            if (!on || a <= 0.002) continue
            val alpha = if (f.reduced) VmtMotion.clamp01(a).toFloat() else 1f
            fun paint(pen: VmtPen) {
                scale(h, h, Offset.Zero) {
                    if (veg) broccoli(a, v, a2, f.reduced, pen) else drumstick(a, v, a2, f.reduced, pen)
                }
            }
            val bounds = Rect(-400f, -400f, 800f, h)
            drawIntoCanvas { c -> c.saveLayer(bounds, Paint().apply { this.alpha = 0.10f * alpha }) }
            translate(0f, 4f) { paint(VmtPen(this, silhouette = true, blur = 6f, unit = h)) }
            drawIntoCanvas { c -> c.restore() }
            if (alpha < 0.999f) {
                drawIntoCanvas { c -> c.saveLayer(bounds, Paint().apply { this.alpha = alpha }) }
                paint(VmtPen(this, silhouette = false, blur = 0f, unit = h))
                drawIntoCanvas { c -> c.restore() }
            } else {
                paint(VmtPen(this, silhouette = false, blur = 0f, unit = h))
            }
        }
    }
}

/** How far row i has swapped to Non veg (the top row leads both ways). */
private fun rowSwap(f: VmtFrame, i: Int): Double {
    val d = 0.12 * i
    return if (f.qTarget >= 0.5) VmtMotion.smooth((f.q - d) / 0.64) else 1 - VmtMotion.smooth(((1 - f.q) - d) / 0.64)
}

private fun DrawScope.mark(x: Float, y: Float, veg: Boolean, alpha: Float) {
    val tone = vmtHex(if (veg) 0x2E9E47 else 0xB5442C, alpha)
    drawRoundRect(tone, Offset(x - 8, y - 8), Size(16f, 16f), CornerRadius(3f, 3f), style = Stroke(1.5f))
    if (veg) {
        drawCircle(tone, 3.6f, Offset(x, y))
    } else {
        drawPath(Path().apply { moveTo(x, y - 4.3f); lineTo(x + 4, y + 2.6f); lineTo(x - 4, y + 2.6f); close() }, tone)
    }
}

private fun DrawScope.list(g: VmtGeo, f: VmtFrame, tx: VmtTexts) {
    // "24 veg dishes": the count rolls like an odometer, the words swap with row 0.
    val y = g.headerY
    val numW = tx.counts.getValue(38).size.width.toDouble()
    val v = 24 + 14 * VmtMotion.clamp01(f.q)
    val n = floor(v)
    val fr = v - n
    clipRect(0f, (y - 13).toFloat(), g.w.toFloat(), (y + 13).toFloat()) {
        text(tx.counts.getValue(n.toInt()), 24.0, y - 20 * fr, anchor = -1, alpha = (1 - fr).toFloat())
        if (fr > 0.001) text(tx.counts.getValue(n.toInt() + 1), 24.0, y + 20 * (1 - fr), anchor = -1, alpha = fr.toFloat())
        val l0 = rowSwap(f, 0)
        if (1 - l0 > 0.001) text(tx.words[0], 24 + numW + 5, y - 10 * l0, anchor = -1, alpha = (1 - l0).toFloat())
        if (l0 > 0.001) text(tx.words[1], 24 + numW + 5, y + 10 * (1 - l0), anchor = -1, alpha = l0.toFloat())
    }
    text(tx.sort, g.w - 24, y, anchor = 1)
    drawRect(vmtHex(RULE), Offset(24f, (y + 26).toFloat()), Size((g.w - 48).toFloat(), 1f))

    for (i in 0 until 3) {
        val top = g.rowsY + 76 * i
        val l = rowSwap(f, i)
        clipRect(0f, (top + 4).toFloat(), g.w.toFloat(), (top + 72).toFloat()) {
            for (k in 0 until 2) {
                val a = (if (k == 0) 1 - l else l).toFloat()
                if (a <= 0.001f) continue
                val dish = tx.dishes[k][i]
                translate(0f, (if (k == 0) -10 * l else 10 * (1 - l)).toFloat()) {
                    mark(32f, (top + 24).toFloat(), k == 0, a)
                    text(dish.name, 52.0, top + 24, anchor = -1, alpha = a)
                    text(dish.note, 52.0, top + 47, anchor = -1, alpha = a)
                    text(dish.price, g.w - 24, top + 24, anchor = 1, alpha = a)
                }
            }
        }
        drawRect(vmtHex(RULE), Offset(24f, (top + 75).toFloat()), Size((g.w - 48).toFloat(), 1f))
    }
}
