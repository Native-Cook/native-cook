package com.ngocdevv.nativecook.blocks.pickers.guestcountfigures

import android.app.Activity
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.pickers.guestcountfigures.GcfArt.drawFigure
import com.ngocdevv.nativecook.blocks.pickers.guestcountfigures.GcfArt.drawShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Guest Count Figures — the guests step of a stay-booking flow (a port of GuestCountFigures.swift).

 Look: a warm white screen, "Guests — Add up to 4 people, including yourself.", a row of little
 painted people standing on the floor (drawn in code, GuestCountFiguresArt.kt), a big rolling
 numeral between − and +, an "Add children" pill, a four-step progress bar and Back / Next.
 Each guest added hops in beside the others with a squash on landing while the group re-centres
 on a spring and greets the newcomer with a small hop; a guest removed crouches, rises and fades.
 "Add children" folds the big counter into two compact rows (Adults, Children); "Adults only"
 folds it back.

 autoPlay: LOOP = 10 s (GuestCountFiguresMotion.kt has the script).
 Touch: tap − / +, tap the pill, or drag sideways on the big numeral (or on a row) to scrub the
 count, one step every 32 dp. At most 4 people, at least 1 adult. A light haptic on each change.
 2 s after the last touch the screen eases back to one adult and the loop restarts.
 Reduced motion (animator duration scale 0): a still of 2 adults and 2 children in rows; touches
 still change the counts, without motion.

 Per frame the model runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private const val INK = 0x222222L
private const val MUTED = 0x717171L
private const val LINE = 0xDDDDDDL
private const val BG = 0xFCFBF9L

private class GcfHolder {
    var frame = GcfFrame()
    var running = false
}

/** Text drawn on the canvas, measured once at 1 dp = 1 px. */
private class GcfTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, color: Long, under: Boolean = false, track: Float = 0f) =
        m.measure(
            s,
            TextStyle(
                color = gcfHex(color), fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp,
                textDecoration = if (under) TextDecoration.Underline else null,
            ),
            density = unit,
        )

    val exit = t("Exit", 14f, FontWeight.Medium, INK)
    val title = t("Guests", 32f, FontWeight.SemiBold, INK, track = -0.4f)
    val sub = t("Add up to 4 people, including yourself.", 16f, FontWeight.Normal, 0x6A6A6A)
    val adults = t("Adults", 17f, FontWeight.Medium, INK)
    val aged = t("Aged 13 or above", 14f, FontWeight.Normal, MUTED)
    val children = t("Children", 17f, FontWeight.Medium, INK)
    val ages = t("Ages 2–12", 14f, FontWeight.Normal, MUTED)
    val add = t("Add children", 15f, FontWeight.Medium, INK)
    val only = t("Adults only", 15f, FontWeight.Medium, INK)
    val back = t("Back", 16f, FontWeight.SemiBold, INK, under = true)
    val next = t("Next", 16f, FontWeight.SemiBold, 0xFFFFFF)
    val digits = List(10) { t("$it", 100f, FontWeight.Bold, INK) }
}

/**
 * The guests step of a booking flow: a row of little people that grows and shrinks with the count.
 *
 * @param autoPlay plays the 10 s add-and-remove loop until touched.
 */
@Composable
fun GuestCountFigures(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val sim = remember(auto, reduced) { GcfSim() }
    val holder = remember { GcfHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { GcfTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(gcfHex(BG)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            GcfGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(sim, g, auto, reduced, wake.intValue) {
            fun publish(now: Double) {
                holder.frame = sim.frame(now, g.cx, auto, reduced)
                tick.intValue += 1
            }
            publish(System.nanoTime() / 1e9)
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        publish(now)
                        !auto && !sim.touching && now - sim.lastTouch > 3.2
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
                .semantics { contentDescription = "Guests. Add up to 4 people, including yourself." }
                .pointerInput(sim, g, auto, reduced) {
                    fun now() = System.nanoTime() / 1e9
                    fun feel() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val px = down.position.x / d.toDouble()
                        val py = down.position.y / d.toDouble()
                        val start = sim.state(now(), auto, reduced)
                        val grab = gcfHit(px, py, start, g)
                        sim.touching = true
                        sim.touch(now(), auto, reduced)
                        if (!holder.running) wake.intValue += 1
                        var moved = 0.0
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = (c.position.x - down.position.x) / d.toDouble()
                                val dy = (c.position.y - down.position.y) / d.toDouble()
                                moved = hypot(dx, dy)
                                if (!c.pressed) break
                                val steps = (dx / 32).toInt()
                                val t = now()
                                val s = sim.state(t, auto, reduced)
                                val n = when (grab) {
                                    GRAB_SCRUB_ADULTS -> s.copy(adults = gcfClampAdults(start.adults + steps, s))
                                    GRAB_SCRUB_CHILDREN -> s.copy(children = gcfClampChildren(start.children + steps, s))
                                    else -> null
                                }
                                if (n != null && sim.apply(n, GcfControl.NONE, t, auto, reduced)) feel()
                                else sim.touch(t, auto, reduced)
                            }
                            if (moved < 14) {
                                val t = now()
                                val s = sim.state(t, auto, reduced)
                                var press = GcfControl.NONE
                                val n: GcfState? = when (grab) {
                                    GcfControl.ADULT_MINUS -> s.copy(adults = gcfClampAdults(s.adults - 1, s))
                                    GcfControl.ADULT_PLUS -> s.copy(adults = gcfClampAdults(s.adults + 1, s))
                                    GcfControl.CHILD_MINUS -> s.copy(children = gcfClampChildren(s.children - 1, s))
                                    GcfControl.CHILD_PLUS -> s.copy(children = gcfClampChildren(s.children + 1, s))
                                    GcfControl.PILL -> if (s.rows) s.copy(rows = false, children = 0) else s.copy(rows = true)
                                    else -> null
                                }
                                if (n != null) {
                                    press = grab
                                    if (sim.apply(n, press, t, auto, reduced)) feel()
                                }
                            }
                        } finally {
                            sim.touching = false
                            sim.touch(now(), auto, reduced)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                gcfDraw(g, f, texts)
            }
        }
    }
}

// Grab kinds beyond the five controls.
private const val GRAB_NONE = -1
private const val GRAB_SCRUB_ADULTS = 10
private const val GRAB_SCRUB_CHILDREN = 11

private fun gcfClampAdults(n: Int, s: GcfState) = min(max(n, 1), GcfMotion.MAX_PEOPLE - s.children)
private fun gcfClampChildren(n: Int, s: GcfState) = min(max(n, 0), GcfMotion.MAX_PEOPLE - s.adults)

private fun gcfHit(x: Double, y: Double, s: GcfState, g: GcfGeo): Int {
    fun near(cx: Double, cy: Double, r: Double) = hypot(x - cx, y - cy) <= r
    if (abs(x - g.cx) < 90 && abs(y - g.pillY) < 26) return GcfControl.PILL
    if (s.rows) {
        if (near(g.rowMinusX, g.rowA, 26.0)) return GcfControl.ADULT_MINUS
        if (near(g.rowPlusX, g.rowA, 26.0)) return GcfControl.ADULT_PLUS
        if (near(g.rowMinusX, g.rowC, 26.0)) return GcfControl.CHILD_MINUS
        if (near(g.rowPlusX, g.rowC, 26.0)) return GcfControl.CHILD_PLUS
        if (abs(y - g.rowA) < 36) return GRAB_SCRUB_ADULTS
        if (abs(y - g.rowC) < 36) return GRAB_SCRUB_CHILDREN
    } else {
        if (near(g.cx - 120, g.numY, 30.0)) return GcfControl.ADULT_MINUS
        if (near(g.cx + 120, g.numY, 30.0)) return GcfControl.ADULT_PLUS
        if (abs(x - g.cx) < 96 && abs(y - g.numY) < 70) return GRAB_SCRUB_ADULTS
    }
    return GRAB_NONE
}

// Drawing (in dp).

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun mix(a: Long, b: Long, t: Double, alpha: Float = 1f): Color {
    val x = gcfHex(a)
    val y = gcfHex(b)
    val k = min(1.0, max(0.0, t)).toFloat()
    return Color(x.red + (y.red - x.red) * k, x.green + (y.green - x.green) * k, x.blue + (y.blue - x.blue) * k, alpha)
}

private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, leading: Boolean = false, alpha: Float = 1f) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    drawText(l, topLeft = Offset(if (leading) x.toFloat() else x.toFloat() - w / 2, y.toFloat() - h / 2), alpha = alpha)
}

private fun DrawScope.gcfDraw(g: GcfGeo, f: GcfFrame, tx: GcfTexts) {
    drawRect(gcfHex(BG), Offset.Zero, Size(g.w.toFloat(), g.h.toFloat()))
    header(g, tx)
    figures(g, f)
    counter(g, f, tx)
    footer(g, tx)
}

private fun DrawScope.header(g: GcfGeo, tx: GcfTexts) {
    val ew = tx.exit.size.width + 28f
    drawRoundRect(gcfHex(LINE), Offset(20f, (g.top + 6).toFloat()), Size(ew, 32f), CornerRadius(16f, 16f), style = Stroke(1f))
    text(tx.exit, 20.0 + ew / 2, g.top + 22)
    text(tx.title, g.cx, g.top + 78)
    text(tx.sub, g.cx, g.top + 114)
}

private fun DrawScope.figures(g: GcfGeo, f: GcfFrame) {
    for (fig in f.figures) {
        translate(fig.x.toFloat(), g.ground.toFloat()) {
            val k = fig.sx * (1 - min(fig.lift / 80, 0.5))
            drawShadow(GcfMotion.slot(fig.index).toFloat(), k.toFloat(), (fig.alpha * (1 - min(fig.lift / 40, 0.8))).toFloat())
        }
    }
    for (fig in f.figures) {
        val look = GcfArt.LOOKS[fig.index]
        val s = look.scale * GcfMotion.FIGURE_SCALE.toFloat()
        withTransform({
            translate(fig.x.toFloat(), (g.ground - fig.lift).toFloat())
            scale((fig.sx * s).toFloat(), (fig.sy * s).toFloat(), pivot = Offset.Zero)
        }) {
            if (fig.alpha < 0.999) {
                // Fade the figure as one layer, so its overlapping parts don't show through.
                drawIntoCanvas { it.saveLayer(Rect(-60f, -150f, 60f, 10f), Paint().apply { alpha = fig.alpha.toFloat() }) }
                drawFigure(look)
                drawIntoCanvas { it.restore() }
            } else {
                drawFigure(look)
            }
        }
    }
}

/** A digit wheel: the value's neighbours roll past the centre with their alpha and size falling off. */
private fun DrawScope.wheel(v: Double, px: Double, py: Double, scale: Double, alpha: Double, tx: GcfTexts) {
    if (alpha <= 0.001) return
    clipRect(
        (px - 80 * scale).toFloat(), (py - 64 * scale).toFloat(),
        (px + 80 * scale).toFloat(), (py + 64 * scale).toFloat(),
    ) {
        val base = floor(v).toInt()
        for (k in (base - 1)..(base + 2)) {
            if (k < 0 || k > 9) continue
            val o = k - v
            val a = max(0.0, 1 - 1.5 * abs(o)) * alpha
            if (a <= 0.001) continue
            val s = scale * (1 - 0.25 * min(1.0, abs(o)))
            withTransform({
                translate(px.toFloat(), (py + o * 72 * scale).toFloat())
                scale(s.toFloat(), s.toFloat(), pivot = Offset.Zero)
            }) {
                text(tx.digits[k], 0.0, 0.0, alpha = a.toFloat())
            }
        }
    }
}

private fun DrawScope.button(
    px: Double, py: Double, d: Double, plus: Boolean, disabled: Double, press: Double, alpha: Float,
) {
    if (alpha <= 0.001f) return
    val s = (1 - 0.12 * press).toFloat()
    withTransform({
        translate(px.toFloat(), py.toFloat())
        scale(s, s, pivot = Offset.Zero)
    }) {
        val r = (d / 2).toFloat()
        if (press > 0.001) drawCircle(gcfHex(0xF2F2F2, press.toFloat() * alpha), r, Offset.Zero)
        drawCircle(mix(LINE, 0xEEEEEE, disabled, alpha), r, Offset.Zero, style = Stroke(1f))
        val l = (d * 0.18).toFloat()
        val w = if (d > 40) 2f else 1.6f
        val c = mix(INK, 0xD2D2D2, disabled, alpha)
        drawLine(c, Offset(-l, 0f), Offset(l, 0f), w, StrokeCap.Round)
        if (plus) drawLine(c, Offset(0f, -l), Offset(0f, l), w, StrokeCap.Round)
    }
}

private fun DrawScope.counter(g: GcfGeo, f: GcfFrame, tx: GcfTexts) {
    val m = f.m
    val mc = min(1.0, max(0.0, m))

    // Adults: the big counter folding into its row.
    wheel(f.adultsV, lerp(g.cx, g.rowNumX, m), lerp(g.numY, g.rowA, m), lerp(1.0, 0.17, m), 1.0, tx)
    val bd = lerp(44.0, 36.0, mc)
    button(lerp(g.cx - 120, g.rowMinusX, m), lerp(g.numY, g.rowA, m), bd, false, f.disabled[0], f.press[0], 1f)
    button(lerp(g.cx + 120, g.rowPlusX, m), lerp(g.numY, g.rowA, m), bd, true, f.disabled[1], f.press[1], 1f)

    val aw = tx.adults.size.width.toDouble()
    val sw = tx.aged.size.width.toDouble()
    text(tx.adults, lerp(g.cx - aw / 2, 24.0, m), lerp(g.top + 468, g.top + 374, m), leading = true)
    text(tx.aged, lerp(g.cx - sw / 2, 24.0, m), lerp(g.top + 490, g.top + 396, m), leading = true)

    // Divider and the children row.
    val dp = GcfMotion.smooth((m - 0.3) / 0.5)
    if (dp > 0.001) {
        drawLine(gcfHex(0xEBEBEB), Offset(24f, g.divider.toFloat()), Offset((24 + (g.w - 48) * dp).toFloat(), g.divider.toFloat()), 1f)
    }
    val ca = GcfMotion.smooth((m - 0.35) / 0.65)
    if (ca > 0.001) {
        val a = ca.toFloat()
        translate(0f, (16 * (1 - ca)).toFloat()) {
            text(tx.children, 24.0, g.top + 452, leading = true, alpha = a)
            text(tx.ages, 24.0, g.top + 474, leading = true, alpha = a)
            wheel(f.childrenV, g.rowNumX, g.rowC, 0.17, ca, tx)
            button(g.rowMinusX, g.rowC, 36.0, false, f.disabled[2], f.press[2], a)
            button(g.rowPlusX, g.rowC, 36.0, true, f.disabled[3], f.press[3], a)
        }
    }

    // The pill: "Add children" ↔ "Adults only".
    val pw = lerp(tx.add.size.width.toDouble(), tx.only.size.width.toDouble(), mc) + 36
    val ps = (1 - 0.08 * f.press[4]).toFloat()
    withTransform({
        translate(g.cx.toFloat(), g.pillY.toFloat())
        scale(ps, ps, pivot = Offset.Zero)
    }) {
        val half = (pw / 2).toFloat()
        if (f.press[4] > 0.001) {
            drawRoundRect(gcfHex(0xF2F2F2, f.press[4].toFloat()), Offset(-half, -20f), Size(2 * half, 40f), CornerRadius(20f, 20f))
        }
        drawRoundRect(gcfHex(LINE), Offset(-half, -20f), Size(2 * half, 40f), CornerRadius(20f, 20f), style = Stroke(1f))
        val xf = GcfMotion.smooth((m - 0.3) / 0.4).toFloat()
        // The labels swap with a short roll: the old one leaves 8 dp up, the new one comes from below.
        text(tx.add, 0.0, -8.0 * xf, alpha = 1 - xf)
        text(tx.only, 0.0, 8.0 * (1 - xf), alpha = xf)
    }
}

private fun DrawScope.footer(g: GcfGeo, tx: GcfTexts) {
    val segW = ((g.w - 12) / 4).toFloat()
    for (i in 0 until 4) {
        drawRect(
            gcfHex(if (i < 2) INK else 0xE3E3E3),
            Offset(i * (segW + 4), (g.progressY - 2).toFloat()),
            Size(segW, 4f),
        )
    }
    text(tx.back, 24.0, g.footY, leading = true)
    val nx = (g.w - 24 - 112).toFloat()
    drawRoundRect(gcfHex(INK), Offset(nx, (g.footY - 24).toFloat()), Size(112f, 48f), CornerRadius(10f, 10f))
    text(tx.next, nx + 56.0, g.footY)
}
