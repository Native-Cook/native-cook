package com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.blurred
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.bottle
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.bead
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.cart
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.drops
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.red
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.stickers
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.stream
import com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart.SscArt.text
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 Sauce Squeeze Cart — the add-to-cart moment of an invented grocery app ("Pantry Run")
 (a port of SauceSqueezeCart.swift).

 Look: a pink product page for "Tomato Ketchup" by an invented brand, PLUMPO. A glossy red squeeze
 bottle, drawn in code (a pillowy body with three bulges per side, a white two-tier cap pointing down,
 a label with a tomato), floats over a cream card with the name, chips, the price and "Hold to add".

 Hero: holding squeezes the bottle (squash and stretch, a trembling waist, a bead at the nozzle);
 releasing shoots a thick glossy stream that whips down the screen while a red pool rises until the
 whole screen is sauce. Stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an
 arrow, a sparkle), then the red retracts up the screen with drips hanging from it, the page comes
 back and the cart in the header bumps with a "+1". SauceSqueezeCartMotion.kt has the timeline.

 autoPlay: LOOP = 8 s (rest, squeeze, shoot and flood, stickers, drain, rest).
 Touch: press and hold the bottle or the button; the longer the hold, the harder the squeeze. Release
 past 40 % to shoot, below it the bottle springs back. A light haptic when the squeeze is armed, at
 the release and when the screen is red. 2 s after the last touch (and once a shot has played out)
 the loop restarts.
 Reduced motion (animator duration scale 0): the page still; a full hold fades the red and its
 stickers in and out.

 Per frame the driver runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private const val CREAM = 0xFFF4FAL
private const val CTA_W = 200.0
private const val BOTTLE_SCALE = 1.12

internal class SscGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx get() = w / 2
    val cardY get() = h - 318
    val ctaY get() = h - max(bottom, 16.0) - 40
    val ctaX get() = w - 24 - CTA_W
    val bottleY get() = top + 250
    /** The stickers gather around the middle of the red screen. */
    val stickerY get() = (top + h) / 2 - 30

    fun bottleX(f: SscFrame) = cx + f.shake
    fun bottleCY(f: SscFrame) = bottleY + f.lift + 3 * f.bob
    fun bottleAngle(f: SscFrame) = -7 + f.spin + 1.5 * f.sway

    /** The nozzle's tip on the screen. */
    fun nozzle(f: SscFrame): Pair<Double, Double> {
        val a = bottleAngle(f) * PI / 180
        val d = (SscArt.NOZZLE_Y - 2) * BOTTLE_SCALE
        return Pair(bottleX(f) - sin(a) * d, bottleCY(f) + cos(a) * d)
    }

    /** The bottle and the button take the squeeze. */
    fun hit(x: Double, y: Double): Boolean {
        val bottle = x >= cx - 120 && x <= cx + 120 && y >= bottleY - 150 && y <= bottleY + 180
        val cta = x >= ctaX - 12 && x <= ctaX + CTA_W + 12 && y >= ctaY - 42 && y <= ctaY + 42
        return bottle || cta
    }
}

/** Text drawn on the canvas, measured once at 1 dp = 1 px. */
internal class SscTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, color: Long, track: Float = 0f) = m.measure(
        s,
        TextStyle(color = sscHex(color), fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
        density = unit,
    )

    val brand = t("PLUMPO", 10f, FontWeight.ExtraBold, 0xC22018, 1.4f)
    val tag = t("IN YOUR CART", 18f, FontWeight.ExtraBold, SSC_PLUM)
    val added = t("+1 ADDED", 15f, FontWeight.ExtraBold, SSC_PLUM)
    val title = t("Pantry Run", 16f, FontWeight.Bold, SSC_PLUM)
    val eyebrow = t("PLUMPO · 500 ML", 12f, FontWeight.SemiBold, SSC_MUTED_PLUM, 1.4f)
    val name = t("Tomato Ketchup", 30f, FontWeight.ExtraBold, SSC_PLUM)
    val subtitle = t("Sun-ripe, slow-cooked, a pinch of smoke.", 15f, FontWeight.Normal, 0x7A4A70)
    val chips = listOf("★ 4.8", "Vegan", "No added sugar").map { t(it, 13f, FontWeight.SemiBold, 0x5B1450) }
    val price = t("$3.80", 28f, FontWeight.ExtraBold, SSC_PLUM)
    val perBottle = t("per bottle", 12f, FontWeight.Normal, SSC_MUTED_PLUM)
    val cta = t("Hold to add", 18f, FontWeight.Bold, 0xFFFFFF)
    val plusOne = t("+1", 13f, FontWeight.ExtraBold, 0xFFFFFF)
}

private class SscHolder {
    var frame = SscFrame()
    var running = false
}

/**
 * The product page of a grocery app where holding squeezes the sauce bottle and releasing floods
 * the screen with sauce as the add-to-cart confirmation.
 *
 * @param autoPlay plays the 8 s squeeze-and-flood loop until touched.
 */
@Composable
fun SauceSqueezeCart(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
    val driver = remember(auto, reduced) { SscDriver() }
    val holder = remember { SscHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { SscTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(sscHex(SSC_PINK)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            SscGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            var prev = driver.frame(System.nanoTime() / 1e9, auto, reduced)
            holder.frame = prev
            tick.intValue += 1
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        val f = driver.frame(now, auto, reduced)
                        // Haptics on the moments of a touch-driven squeeze: armed, and the screen gone red.
                        if (driver.manual) {
                            if (driver.holding && prev.s < SscMotion.THRESHOLD && f.s >= SscMotion.THRESHOLD) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            val a = prev.tau
                            val b = f.tau
                            if (a != null && b != null && a < SscMotion.COVERED_AT && b >= SscMotion.COVERED_AT) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                        prev = f
                        holder.frame = f
                        tick.intValue += 1
                        val shotRunning = driver.shotAt?.let { now - it < SscMotion.SHOT_END } ?: false
                        !auto && !driver.touching && !shotRunning && now - driver.lastTouch > 3
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
                    contentDescription = "Tomato Ketchup, 3 dollars 80. Hold to squeeze the bottle and add it to your cart."
                    role = Role.Button
                    onClick {
                        driver.shoot(System.nanoTime() / 1e9, auto, reduced)
                        if (!holder.running) wake.intValue += 1
                        true
                    }
                }
                .pointerInput(driver, g, auto, reduced) {
                    fun now() = System.nanoTime() / 1e9
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.toDouble()
                        if (!g.hit(down.position.x / d, down.position.y / d)) return@awaitEachGesture
                        driver.begin(now(), auto, reduced)
                        if (!holder.running) wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                            }
                        } finally {
                            if (driver.end(now())) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (!holder.running) wake.intValue += 1
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                sscDraw(g, f, texts)
            }
        }
    }
}

// Drawing (in dp).

private fun DrawScope.sscDraw(g: SscGeo, f: SscFrame, tx: SscTexts) {
    drawRect(sscHex(SSC_PINK), Offset.Zero, Size(g.w.toFloat(), g.h.toFloat()))
    header(g, f, tx)
    card(g, f, tx)

    // The bottle.
    withTransform({
        translate(g.bottleX(f).toFloat(), g.bottleCY(f).toFloat())
        rotate(g.bottleAngle(f).toFloat(), Offset.Zero)
        scale(BOTTLE_SCALE.toFloat(), BOTTLE_SCALE.toFloat(), Offset.Zero)
    }) {
        bottle(f, tx)
        if (f.tau == null && !f.reduced) bead(f.s)
    }

    val tau = f.tau ?: return
    if (f.reduced) {
        val a = SscMotion.redFade(tau)
        if (a > 0.001) drawRect(sscHex(SSC_SAUCE, a.toFloat()), Offset.Zero, Size(g.w.toFloat(), g.h.toFloat()))
    } else {
        val (rx, ry) = g.nozzle(f)
        if (SscMotion.streamVisible(tau)) {
            drops(rx, ry, tau)
            stream(rx, ry, tau, g.h)
        }
        red(tau, g.w, g.h)
    }
    stickers(tau, g.cx, g.stickerY, f.reduced, tx)
}

private fun DrawScope.header(g: SscGeo, f: SscFrame, tx: SscTexts) {
    val y = g.top + 30
    val plum = sscHex(SSC_PLUM)
    drawCircle(Color.White.copy(alpha = 0.55f), 22f, Offset(42f, y.toFloat()))
    val chev = Path().apply {
        moveTo(45f, (y - 7).toFloat()); lineTo(38f, y.toFloat()); lineTo(45f, (y + 7).toFloat())
    }
    drawPath(chev, plum, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    text(tx.title, g.cx, y)

    // Cart: bumps when the sauce lands in it, a "+1" floats up.
    val cx = g.w - 42
    val tau = f.tau
    val k = 1 + 0.6 * (tau?.let { SscMotion.cartBump(it) } ?: 0.0)
    withTransform({
        translate(cx.toFloat(), y.toFloat())
        scale(k.toFloat(), k.toFloat(), Offset.Zero)
    }) {
        drawCircle(Color.White.copy(alpha = 0.55f), 22f, Offset.Zero)
        cart(0.0, 0.0, 0.95, plum)
    }
    if (tau != null) {
        val (scale, rise, alpha) = SscMotion.plusOne(tau)
        if (scale > 0.01 && alpha > 0.01) {
            withTransform({
                translate((cx - 26).toFloat(), (y - 12 - rise).toFloat())
                scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
            }) {
                drawPath(SscArt.roundRect(-17.0, -11.0, 34.0, 22.0, 11.0), sscHex(SSC_SAUCE), alpha = alpha.toFloat())
                text(tx.plusOne, 0.0, 0.0, alpha.toFloat())
            }
        }
    }
}

private fun DrawScope.card(g: SscGeo, f: SscFrame, tx: SscTexts) {
    val top = g.cardY
    val shape = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                0f, top.toFloat(), g.w.toFloat(), (g.h + 2).toFloat(),
                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f),
                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f),
            ),
        )
    }
    drawPath(shape, sscHex(CREAM))

    text(tx.eyebrow, 24.0, top + 34, anchor = -1)
    text(tx.name, 24.0, top + 68, anchor = -1)
    text(tx.subtitle, 24.0, top + 100, anchor = -1)

    var x = 24.0
    for (chip in tx.chips) {
        val w = chip.size.width + 24.0
        drawPath(SscArt.roundRect(x, top + 125, w, 30.0, 15.0), sscHex(0xF9DDF0))
        text(chip, x + w / 2, top + 140)
        x += w + 8
    }

    val cy = g.ctaY
    text(tx.price, 24.0, cy - 9, anchor = -1)
    text(tx.perBottle, 24.0, cy + 17, anchor = -1)

    // "Hold to add": pressed in, a darker fill grows from the left with the squeeze.
    val k = (1 - 0.04 * f.press).toFloat()
    withTransform({
        translate((g.ctaX + CTA_W / 2).toFloat(), cy.toFloat())
        scale(k, k, Offset.Zero)
    }) {
        val pill = SscArt.roundRect(-CTA_W / 2, -30.0, CTA_W, 60.0, 30.0)
        blurred(pill, sscHex(0x9A0010, 0.28f), 12.0, dy = 8 * (1 - 0.5 * f.press))
        drawPath(pill, sscHex(SSC_SAUCE))
        clipPath(pill) {
            drawRect(
                sscHex(0xB80002),
                Offset((-CTA_W / 2).toFloat(), -30f),
                Size((CTA_W * SscMotion.clamp01(f.s)).toFloat(), 60f),
            )
            drawPath(
                SscArt.roundRect(-CTA_W / 2, -28.5, CTA_W, 60.0, 30.0),
                Color.White.copy(alpha = 0.18f),
                style = Stroke(1.5f),
            )
        }
        text(tx.cta, 0.0, 0.0)
    }
}
