package com.ngocdevv.nativecook.blocks.pickers.keypadthemewave

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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Keypad Theme Wave — the theme step of an invented calculator app's onboarding ("tallo")
 (a port of KeypadThemeWave.swift).

 Look: a cream page; at the top a preview card of the calculator (display "0", a 4 × 5 keypad of round keys
 and the app's wordmark); under it a strip of swatches, each a tiny striped thumbnail of one theme, whose
 centred swatch is bigger, framed in white and is the selection; then "Customize your calculator", a line
 of help and a dark Continue button. Everything is drawn in code (KeypadThemeWaveModel.kt holds the ten
 palettes and the timing).

 Hero: when the selection changes, the new theme sweeps across the preview. The card's colour is revealed
 by a circle from the corner the new swatch came from, and the keys follow on anti-diagonals from the same
 corner, 60 ms apart: each key drains its old colour into its centre, stands for a beat as a hollow ring in
 the new theme's line colour, then the new fill blooms out of the centre with a small overshoot.

 autoPlay: LOOP = 10 s. Rest on Classic; the strip glides to Marigold (1.0 s), Lilac (3.2 s) and Mint
 (5.2 s), each sending a wave from the right, then sweeps back five swatches to Classic (7.2 s), whose
 wave comes from the left; rest from 8.5 s. Every frame is a function of the loop time.
 Touch: drag or flick the strip, it snaps to the centred swatch and sends a wave; tap a swatch to pick it;
 tap the keys to calculate (the delete tag removes a digit). 2 s after the last touch the display clears,
 the strip glides home to Classic and the loop restarts.
 Reduced motion: no loop; a change crossfades the card and keys in 0.25 s.
*/

private const val PAGE = 0xFAF6F0L
private const val INK = 0x2A2622L
private const val MUTED = 0x77716AL

private fun hex(v: Long, a: Double = 1.0) = Color(
    ((v shr 16) and 0xFF).toInt() / 255f,
    ((v shr 8) and 0xFF).toInt() / 255f,
    (v and 0xFF).toInt() / 255f,
    a.toFloat().coerceIn(0f, 1f),
)

private fun mixHex(a: Long, b: Long, t: Double): Color {
    fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toInt() / 255.0
    return Color(
        KtwModel.lerp(ch(a, 16), ch(b, 16), t).toFloat(),
        KtwModel.lerp(ch(a, 8), ch(b, 8), t).toFloat(),
        KtwModel.lerp(ch(a, 0), ch(b, 0), t).toFloat(),
    )
}

private fun Double.f() = toFloat()

private sealed class KtwGrab {
    object None : KtwGrab()
    object Strip : KtwGrab()
    class Key(val r: Int, val c: Int) : KtwGrab()
    object Delete : KtwGrab()
    object Other : KtwGrab()
}

private class KtwGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx = w / 2
    val cardX = 24.0
    val cardW = w - 48
    val cardTop = top + 45
    val cardH = 352.0
    val displayY = cardTop + 52
    val displayRight = cx + 117
    val stripY = cardTop + cardH + 64
    val titleY = stripY + 98
    val buttonY = h - max(bottom, 16.0) - 41
    val revealMax = hypot(cardW, cardH) + 24

    fun keyX(c: Int) = cx + (c - 2) * KtwModel.PITCH
    fun keyY(r: Int) = cardTop + 124 + r * KtwModel.PITCH

    /** The card corner a change's colour comes from. */
    fun cornerX(dir: Int) = if (dir > 0) cardX + cardW else cardX
    val cornerY = cardTop + cardH

    fun hit(x: Double, y: Double): KtwGrab {
        if (abs(y - stripY) < 34 && x > cardX - 8 && x < cardX + cardW + 8) return KtwGrab.Strip
        for (r in 0 until 4) for (c in 0 until 5) {
            if (!KtwModel.isKey(r, c)) continue
            if (hypot(x - keyX(c), y - keyY(r)) < KtwModel.PITCH / 2) return KtwGrab.Key(r, c)
        }
        if (hypot(x - (cx + 134), y - displayY) < 22) return KtwGrab.Delete
        return KtwGrab.Other
    }

    /** Which swatch (in steps from the centred one) sits under x. */
    fun swatchOffset(x: Double, scroll: Double): Int {
        val base = KtwModel.round(scroll)
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (k in -6..6) {
            val d = k + base - scroll
            val dist = abs(cx + KtwModel.swatchX(d) - x)
            if (dist < bestDist) {
                bestDist = dist
                best = k
            }
        }
        return best
    }
}

/** Text measured once, in dp (density 1); colours are given when drawn. */
private class KtwTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    fun t(s: String, size: Float, weight: FontWeight, track: Float = 0f) = m.measure(
        s,
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
        density = unit,
    )

    val labels = KtwModel.LABELS.mapIndexed { r, row ->
        row.mapIndexed { c, s ->
            val role = KtwModel.role(r, c)
            when {
                s == "AC" -> t(s, 16f, FontWeight.SemiBold)
                role == 2 -> t(s, 23f, FontWeight.Medium)
                role == 1 -> t(s, 18f, FontWeight.Medium)
                else -> t(s, 21f, FontWeight.Medium)
            }
        }
    }
    val brand = t("tallo", 13f, FontWeight.Black, -0.3f)
    val brandSub = t("CALC", 7.5f, FontWeight.SemiBold, 1.6f)
    val title1 = t("Customize", 32f, FontWeight.Bold, -0.6f)
    val title2 = t("your calculator", 32f, FontWeight.Bold, -0.6f)
    val subtitle = t("Swipe the strip to pick colors you love.", 15f, FontWeight.Normal)
    val cta = t("Continue", 17f, FontWeight.SemiBold)

    private val displays = HashMap<String, TextLayoutResult>()
    fun display(s: String): TextLayoutResult = displays.getOrPut(s) {
        if (displays.size > 64) displays.clear()
        t(s, if (s.length > 7) 34f else 40f, FontWeight.Medium, -0.5f)
    }
}

private class KtwHolder {
    var scene = KtwScene()
}

@Composable
fun KeypadThemeWave(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    BlockSystemBars(darkIcons = true)
    val driver = remember(auto, reduced) { KtwDriver() }
    val holder = remember { KtwHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { KtwTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(hex(PAGE)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            KtwGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            while (true) {
                val idle = withFrameNanos { n ->
                    val now = n / 1e9
                    holder.scene = driver.frame(now, auto, reduced)
                    tick.intValue += 1
                    !auto && !driver.busy(now, reduced)
                }
                if (idle) break
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun kick() {
            wake.intValue += 1
        }
        fun step(by: Int) {
            if (driver.tapSwatch(by, now(), auto, reduced)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            kick()
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Customize your calculator. Theme ${KtwModel.THEMES[driver.applied].name}."
                    customActions = listOf(
                        CustomAccessibilityAction("Next theme") { step(1); true },
                        CustomAccessibilityAction("Previous theme") { step(-1); true },
                    )
                }
                .pointerInput(driver, g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val x0 = (down.position.x / d).toDouble()
                        val y0 = (down.position.y / d).toDouble()
                        val grab = g.hit(x0, y0)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        when (grab) {
                            is KtwGrab.Strip -> driver.beginDrag(now(), auto, reduced)
                            is KtwGrab.Key -> driver.pressDown(grab.r, grab.c, now(), auto)
                            is KtwGrab.Delete -> driver.touch(now(), auto)
                            else -> {}
                        }
                        kick()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            if (grab is KtwGrab.Strip) {
                                if (driver.drag(((c.position.x - down.position.x) / d).toDouble(), now())) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            if (!c.pressed) break
                            if (grab !is KtwGrab.Other) c.consume()
                        }
                        val moved = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d)
                        val t = now()
                        when (grab) {
                            is KtwGrab.Strip -> {
                                if (moved < 8) {
                                    driver.cancelDrag(t)
                                    val k = g.swatchOffset((last.x / d).toDouble(), driver.scroll(t, reduced))
                                    if (k != 0 && driver.tapSwatch(k, t, auto, reduced)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                } else if (driver.endDrag((tracker.calculateVelocity().x / d).toDouble(), t, reduced)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                }
                            }
                            is KtwGrab.Key -> if (moved < 14) {
                                driver.pressKey(grab.r, grab.c, t)
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            is KtwGrab.Delete -> if (moved < 14) {
                                driver.backspace(t)
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            else -> {}
                        }
                        kick()
                    }
                },
        ) {
            tick.intValue
            val s = holder.scene
            scale(density, density, pivot = Offset.Zero) {
                ktwDraw(g, s, texts)
            }
        }
    }
}

// Drawing (in dp).

private val blurPaint = android.graphics.Paint()

private fun DrawScope.ktwDraw(g: KtwGeo, s: KtwScene, tx: KtwTexts) {
    drawRect(hex(PAGE), Offset.Zero, Size(g.w.f(), g.h.f()))
    card(g, s, tx)
    keys(g, s, tx)
    strip(g, s)
    copy(g, tx)
}

/** Draws measured text: anchor −1 starts at x, 0 centres on x, 1 ends at x; vertically centred on y. */
private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, color: Color, anchor: Int = 0) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    val left = when {
        anchor < 0 -> x.f()
        anchor > 0 -> x.f() - w
        else -> x.f() - w / 2
    }
    drawText(l, color = color, topLeft = Offset(left, y.f() - h / 2))
}

/** A soft (blurred) fill or stroke of a path. Android's blur radius is 1.5 × SwiftUI's. */
private fun DrawScope.blurred(path: Path, color: Color, radius: Double, dy: Double = 0.0, strokeWidth: Double = 0.0) {
    blurPaint.reset()
    blurPaint.isAntiAlias = true
    blurPaint.color = color.toArgb()
    if (strokeWidth > 0) {
        blurPaint.style = android.graphics.Paint.Style.STROKE
        blurPaint.strokeWidth = strokeWidth.f()
    }
    blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas {
        val c = it.nativeCanvas
        c.save()
        c.translate(0f, dy.f())
        c.drawPath(path.asAndroidPath(), blurPaint)
        c.restore()
    }
}

private fun roundRectPath(x: Double, y: Double, w: Double, h: Double, r: Double) = Path().apply {
    addRoundRect(RoundRect(Rect(x.f(), y.f(), (x + w).f(), (y + h).f()), CornerRadius(r.f())))
}

private fun circlePath(x: Double, y: Double, r: Double) = Path().apply {
    addOval(Rect((x - r).f(), (y - r).f(), (x + r).f(), (y + r).f()))
}

/** The card: its shadow, the base theme and a revealed circle for each change. */
private fun DrawScope.card(g: KtwGeo, s: KtwScene, tx: KtwTexts) {
    val shape = roundRectPath(g.cardX, g.cardTop, g.cardW, g.cardH, 26.0)
    blurred(shape, Color.Black.copy(alpha = 0.08f), 15.0, 14.0)
    blurred(shape, Color.Black.copy(alpha = 0.06f), 1.5, 1.0)
    clipPath(shape) {
        cardFace(g, s, KtwModel.THEMES[s.base], tx, 1.0)
        for (ch in s.changes) {
            if (s.now < ch.at) continue
            val tau = s.now - ch.at
            val theme = KtwModel.THEMES[ch.to]
            if (s.reduced) {
                cardFace(g, s, theme, tx, KtwModel.easeInOutCubic(min(1.0, tau / KtwModel.REDUCED_DUR)))
                continue
            }
            val p = KtwModel.reveal(tau)
            if (p <= 0) continue
            val ox = g.cornerX(ch.dir)
            val oy = g.cornerY
            val r = p * g.revealMax
            clipPath(circlePath(ox, oy, r)) {
                cardFace(g, s, theme, tx, 1.0)
                // A soft light band just inside the moving edge, gone as the reveal completes.
                val rim = 1 - KtwModel.seg(tau, KtwModel.REVEAL_DUR * 0.6, KtwModel.REVEAL_DUR)
                if (rim > 0 && r > 6) {
                    blurred(circlePath(ox, oy, r - 6), Color.White.copy(alpha = (0.22 * rim).f()), 5.0, strokeWidth = 8.0)
                }
            }
        }
    }
}

/** One theme's card: background, display and wordmark, at an opacity (reduced motion fades them). */
private fun DrawScope.cardFace(g: KtwGeo, s: KtwScene, t: KtwTheme, tx: KtwTexts, alpha: Double) {
    drawRect(hex(t.bg, alpha), Offset(g.cardX.f(), g.cardTop.f()), Size(g.cardW.f(), g.cardH.f()))
    text(tx.display(s.display), g.displayRight, g.displayY, hex(t.display, alpha), anchor = 1)
    // The delete tag: a label shape pointing left with a small cross.
    val x = g.cx + 134
    val y = g.displayY
    val tag = Path().apply {
        moveTo((x - 10).f(), y.f())
        lineTo((x - 5).f(), (y - 6.5).f())
        lineTo((x + 8).f(), (y - 6.5).f())
        quadraticTo((x + 9.5).f(), (y - 6.5).f(), (x + 9.5).f(), (y - 5).f())
        lineTo((x + 9.5).f(), (y + 5).f())
        quadraticTo((x + 9.5).f(), (y + 6.5).f(), (x + 8).f(), (y + 6.5).f())
        lineTo((x - 5).f(), (y + 6.5).f())
        close()
    }
    val faint = hex(t.display, 0.45 * alpha)
    drawPath(tag, faint, style = Stroke(1.4f, join = StrokeJoin.Round))
    drawLine(faint, Offset((x - 1).f(), (y - 2.6).f()), Offset((x + 4.2).f(), (y + 2.6).f()), 1.4f, StrokeCap.Round)
    drawLine(faint, Offset((x + 4.2).f(), (y - 2.6).f()), Offset((x - 1).f(), (y + 2.6).f()), 1.4f, StrokeCap.Round)
    // The wordmark in the keypad's empty cell.
    val px = g.keyX(0)
    val py = g.keyY(3)
    text(tx.brand, px, py - 4, hex(t.display, 0.45 * alpha))
    text(tx.brandSub, px + 0.8, py + 8, hex(t.display, 0.4 * alpha))
}

private fun DrawScope.keys(g: KtwGeo, s: KtwScene, tx: KtwTexts) {
    val big = KtwModel.KEY_RADIUS.f()
    for (r in 0 until 4) for (col in 0 until 5) {
        if (!KtwModel.isKey(r, col)) continue
        val role = KtwModel.role(r, col)
        val st = KtwModel.keyState(s, r, col)
        val from = KtwModel.THEMES[st.from]
        val to = KtwModel.THEMES[st.to]
        val l = KtwModel.look(st.u, s.reduced)
        val pr = KtwModel.press(s.now - s.presses[r * 5 + col])
        val label = tx.labels[r][col]
        val sc = (l.scale * (1 - 0.09 * pr)).f()
        withTransform({
            translate(g.keyX(col).f(), g.keyY(r).f())
            scale(sc, sc, pivot = Offset.Zero)
        }) {
            fun put(hexColor: Long, a: Double) {
                if (a > 0.004) text(label, 0.0, 0.0, hex(hexColor, a))
            }
            if (s.reduced) {
                drawCircle(mixHex(from.fill(role), to.fill(role), l.mix), big, Offset.Zero)
                put(from.ink(role), 1 - l.mix)
                put(to.ink(role), l.mix)
            } else {
                if (l.oldR > 0.2) drawCircle(hex(from.fill(role)), l.oldR.f(), Offset.Zero)
                put(from.ink(role), l.oldLabel)
                if (l.ring > 0.004) {
                    drawCircle(hex(to.ring, l.ring), big - 0.7f, Offset.Zero, style = Stroke(1.4f))
                }
                put(to.ring, l.ringLabel)
                if (l.newR > 0.2) drawCircle(hex(to.fill(role)), l.newR.f(), Offset.Zero)
                put(to.ink(role), l.newLabel)
            }
            if (pr > 0.004) drawCircle(hex(to.ink(role), 0.16 * pr), big, Offset.Zero)
        }
    }
}

/** The swatch strip: an endless row of striped thumbnails, the centred one larger and framed. */
private fun DrawScope.strip(g: KtwGeo, s: KtwScene) {
    val n = KtwModel.COUNT.toDouble()
    val items = ArrayList<Pair<Int, Double>>()
    for (i in 0 until KtwModel.COUNT) {
        val d0 = KtwModel.wrap(i - s.scroll)
        for (d in doubleArrayOf(d0 - n, d0, d0 + n)) {
            if (abs(KtwModel.swatchX(d)) < g.cardW / 2 + 24) items.add(i to d)
        }
    }
    items.sortByDescending { abs(it.second) }
    clipRect(g.cardX.f(), (g.stripY - 40).f(), (g.cardX + g.cardW).f(), (g.stripY + 40).f()) {
        for ((i, d) in items) {
            val a = min(abs(d), 1.0)
            val bigness = 1 - a
            val w = 32 + 12 * bigness
            val h = 30 + 12 * bigness
            val rad = 5 + 7 * bigness
            val x = g.cx + KtwModel.swatchX(d)
            val left = x - w / 2
            val top = g.stripY - h / 2
            val shape = roundRectPath(left, top, w, h, rad)
            if (bigness > 0.01) blurred(shape, Color.Black.copy(alpha = (0.2 * bigness).f()), 6.0, 3.0)
            val t = KtwModel.THEMES[i]
            clipPath(shape) {
                var y = top
                for ((c, f) in listOf(t.bg to 0.34, t.op to 0.22, t.fn to 0.22, t.digit to 0.22)) {
                    drawRect(hex(c), Offset(left.f(), y.f()), Size(w.f(), (h * f + 0.5).f()))
                    y += h * f
                }
            }
            if (bigness > 0.01) {
                drawPath(
                    roundRectPath(left + 1.5, top + 1.5, w - 3, h - 3, rad - 1.5),
                    hex(0xFFFFFF, bigness),
                    style = Stroke(3f),
                )
            }
        }
        // The ends fade into the page.
        val fade = 30f
        val page = hex(PAGE)
        val clear = hex(PAGE, 0.0)
        val left = g.cardX.f()
        val right = (g.cardX + g.cardW).f()
        val top = (g.stripY - 40).f()
        drawRect(Brush.horizontalGradient(listOf(page, clear), left, left + fade), Offset(left, top), Size(fade, 80f))
        drawRect(Brush.horizontalGradient(listOf(clear, page), right - fade, right), Offset(right - fade, top), Size(fade, 80f))
    }
}

private fun DrawScope.copy(g: KtwGeo, tx: KtwTexts) {
    text(tx.title1, g.cardX + 2, g.titleY, hex(INK), anchor = -1)
    text(tx.title2, g.cardX + 2, g.titleY + 41, hex(INK), anchor = -1)
    text(tx.subtitle, g.cardX + 2, g.titleY + 84, hex(MUTED), anchor = -1)
    val pill = roundRectPath(g.cardX, g.buttonY - 27, g.cardW, 54.0, 27.0)
    blurred(pill, Color.Black.copy(alpha = 0.14f), 10.0, 6.0)
    drawPath(pill, hex(INK))
    text(tx.cta, g.cx, g.buttonY, hex(PAGE))
}

// Driver

/** Turns the clock and the finger into scenes. */
private class KtwDriver {
    private var autoStart: Double? = null
    var manual = false
        private set
    var dragging = false
        private set
    private var lastTouch = -1e9
    private var returnAt: Double? = null

    private var base = KtwModel.HOME
    private val changes = ArrayList<KtwChange>()

    // The strip: a spring from (anchorX, anchorV) at anchorAt towards target, or the finger.
    private var target = KtwModel.HOME.toDouble()
    private var anchorAt = -1e9
    private var anchorX = 0.0
    private var anchorV = 0.0
    private var dragFrom = 0.0
    private var dragDx = 0.0
    private var detent = 0

    private val calc = KtwCalc()
    private val presses = DoubleArray(20) { -1e9 }

    val applied: Int get() = changes.lastOrNull()?.to ?: base

    fun scroll(now: Double, reduced: Boolean): Double {
        if (dragging) return dragFrom - dragDx / KtwModel.SWATCH_PITCH
        val tau = now - anchorAt
        if (reduced) return target + anchorX * (1 - KtwModel.easeInOutCubic(KtwModel.seg(tau, 0.0, 0.2)))
        return target + KtwModel.spring(anchorX, anchorV, tau)
    }

    private fun velocity(now: Double, reduced: Boolean): Double {
        if (dragging || reduced) return 0.0
        return KtwModel.springVelocity(anchorX, anchorV, now - anchorAt)
    }

    /** True while something still moves (the frame loop stops otherwise). */
    fun busy(now: Double, reduced: Boolean): Boolean {
        if (dragging || changes.isNotEmpty() || returnAt != null) return true
        if (abs(scroll(now, reduced) - target) > 1e-4) return true
        return presses.any { now - it < 0.5 }
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean): KtwScene {
        if (auto && manual && !dragging) {
            if (returnAt == null && now >= lastTouch + IDLE_BEFORE_HOME) goHome(now, reduced)
            val r = returnAt
            if (r != null && now >= r + HOME_DUR) {
                manual = false
                returnAt = null
                autoStart = now
                base = KtwModel.HOME
                changes.clear()
            }
        }
        if (auto && !manual) {
            val start = autoStart ?: now.also { autoStart = it }
            val t = (now - start) % KtwModel.LOOP
            return KtwModel.autoScene(t)
        }
        // Changes that are complete everywhere become the base.
        while (changes.isNotEmpty() && now > changes[0].at + KtwModel.SETTLE) {
            base = changes[0].to
            changes.removeAt(0)
        }
        return KtwScene(
            now = now,
            base = base,
            changes = changes.toList(),
            scroll = scroll(now, reduced),
            display = calc.display,
            presses = presses.copyOf(),
            reduced = reduced,
        )
    }

    /** The finger takes over from autoPlay: carry on from what the loop shows now. */
    fun touch(now: Double, auto: Boolean) {
        lastTouch = now
        returnAt = null
        if (!auto || manual) return
        manual = true
        val start = autoStart ?: now
        val cycles = kotlin.math.floor((now - start) / KtwModel.LOOP)
        val loopStart = start + cycles * KtwModel.LOOP
        val t = now - loopStart
        base = KtwModel.HOME
        changes.clear()
        // A move that has started keeps its wave, even if the wave starts a moment later.
        for (c in KtwModel.AUTO_CHANGES) if (c.at - KtwModel.WAVE_AFTER_MOVE <= t) changes.add(KtwChange(c.at + loopStart, c.to, c.dir))
        val a = KtwModel.autoScroll(t)
        target = a.target
        anchorAt = now
        anchorX = a.s - a.target
        anchorV = a.v
    }

    private fun send(to: Int, dir: Int, now: Double): Boolean {
        if (to == applied) return false
        changes.add(KtwChange(now, to, if (dir >= 0) 1 else -1))
        return true
    }

    private fun settle(t: Double, now: Double, reduced: Boolean) {
        val s = scroll(now, reduced)
        val v = velocity(now, reduced)
        target = t
        anchorAt = now
        anchorX = s - t
        anchorV = v
    }

    fun beginDrag(now: Double, auto: Boolean, reduced: Boolean) {
        touch(now, auto)
        dragFrom = scroll(now, reduced)
        dragDx = 0.0
        detent = KtwModel.round(dragFrom).toInt()
        dragging = true
    }

    /** Follows the finger; true when a swatch crosses the centre. */
    fun drag(dx: Double, now: Double): Boolean {
        lastTouch = now
        dragDx = dx
        val d = KtwModel.round(dragFrom - dx / KtwModel.SWATCH_PITCH).toInt()
        if (d != detent) {
            detent = d
            return true
        }
        return false
    }

    fun cancelDrag(now: Double) {
        if (!dragging) return
        val s = dragFrom - dragDx / KtwModel.SWATCH_PITCH
        dragging = false
        target = KtwModel.round(s)
        anchorAt = now
        anchorX = s - target
        anchorV = 0.0
    }

    /** Snaps to the swatch the flick lands on; true when that sends a wave. */
    fun endDrag(vx: Double, now: Double, reduced: Boolean): Boolean {
        if (!dragging) return false
        lastTouch = now
        val s = dragFrom - dragDx / KtwModel.SWATCH_PITCH
        val v = (-vx / KtwModel.SWATCH_PITCH).coerceIn(-40.0, 40.0)
        dragging = false
        val r = KtwModel.round(s)
        val t = KtwModel.round(s + v * 0.2).coerceIn(r - 4, r + 4)
        target = t
        anchorAt = now
        anchorX = s - t
        anchorV = if (reduced) 0.0 else v
        val dir = if (t > dragFrom) 1 else if (t < dragFrom) -1 else if (v >= 0) 1 else -1
        return send(KtwModel.mod(t.toInt()), dir, now)
    }

    /** Picks the swatch `by` steps from the centred one; true when that sends a wave. */
    fun tapSwatch(by: Int, now: Double, auto: Boolean, reduced: Boolean): Boolean {
        touch(now, auto)
        val s = scroll(now, reduced)
        val t = KtwModel.round(s) + by
        settle(t, now, reduced)
        return send(KtwModel.mod(t.toInt()), if (by >= 0) 1 else -1, now)
    }

    fun pressDown(r: Int, c: Int, now: Double, auto: Boolean) {
        touch(now, auto)
        presses[r * 5 + c] = now
    }

    fun pressKey(r: Int, c: Int, now: Double) {
        lastTouch = now
        calc.press(KtwModel.LABELS[r][c])
    }

    fun backspace(now: Double) {
        lastTouch = now
        calc.backspace()
    }

    /** Clear the display and glide the strip home to Classic. */
    private fun goHome(now: Double, reduced: Boolean) {
        returnAt = now
        calc.clear()
        val s = scroll(now, reduced)
        val r = KtwModel.round(s)
        val k = r - KtwModel.mod(r.toInt()) + KtwModel.HOME
        var best = k
        for (cand in doubleArrayOf(k - KtwModel.COUNT, k + KtwModel.COUNT)) if (abs(cand - s) < abs(best - s)) best = cand
        settle(best, now, reduced)
        send(KtwModel.HOME, if (best >= s) 1 else -1, now)
    }

    companion object {
        const val IDLE_BEFORE_HOME = 2.0
        const val HOME_DUR = 1.4
    }
}
