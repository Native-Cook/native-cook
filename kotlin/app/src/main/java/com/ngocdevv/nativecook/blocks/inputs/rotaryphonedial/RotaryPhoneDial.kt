package com.ngocdevv.nativecook.blocks.inputs.rotaryphonedial

import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// Layout, in dp: the strip under the header, the dial below it, the hint at the bottom.
private const val SIDE = 24f
private const val HEADER = 54f
private const val STRIP_H = 74f
private const val FOOTER = 56f
// The plate's radius: at most this share of the width, and this many dp.
private const val PLATE_SHARE = 0.42f
private const val PLATE_MAX = 186f
// The wheel and its holes, as shares of the plate and wheel.
private const val WHEEL = 0.93f
private const val RING = 0.71f
private const val HOLE = 0.158f
private const val HUB = 0.4f
// The strip keeps the last MAX_DIGITS digits, grouped 3 · 3 · 4 like a phone number.
private const val MAX_DIGITS = 10
// Long press on the card clears the strip after CLEAR_PRESS s, unless the finger strays CARD_SLOP dp.
private const val CLEAR_PRESS = 0.55
private const val CARD_SLOP = 14f
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 3.5

private val Pale = Color(0xF1 / 255f, 0xE8 / 255f, 0xD3 / 255f, 0.62f)
private val Ink = Color(0xFF201D18)
private val Monospace = FontFamily.Monospace

// Reanimated's springs with a duration and a damping ratio, solved for their stiffness the way
// Reanimated solves them: the drop (420 ms, 0.55) and the size settling (360 ms, 0.7).
private val Drop = spring<Float>(dampingRatio = 0.55f, stiffness = 1231.2f)
private val DropScale = spring<Float>(dampingRatio = 0.7f, stiffness = 1045.5f)
// Reanimated's default timing curve, ease-in-out quad.
private val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private const val USER_ID = 1L

/** One typed digit on the strip, with its drop-in and its fade-out. */
private class TypedDigit(val id: Int, val value: Int) {
    val drop = Animatable(-22f)
    val scale = Animatable(1.18f)
    val alpha = Animatable(0f)
}

/** A little typewriter wobble per position, so the digits do not sit on a ruler. */
private fun jitterRotate(index: Int): Float {
    val a = sin(index * 12.9898 + 4.1) * 43758.5453
    return ((a - floor(a) - 0.5) * 4).toFloat()
}

private fun jitterY(index: Int): Float {
    val b = sin(index * 78.233 + 1.7) * 24634.6345
    return ((b - floor(b) - 0.5) * 1.6).toFloat()
}

private fun jitterInk(index: Int): Float {
    val a = sin(index * 12.9898 + 4.1) * 43758.5453
    return (0.8 + (a - floor(a)) * 0.2).toFloat()
}

/** The mechanism, the autoPlay clock and the touch in progress, shared by the frame loop and the gestures. */
private class DialState(reduced: Boolean) {
    val sim = DialSim(reduced)
    val finger = DialFinger()
    var clock = 0.0
    var hold = 0.0
    var now = 0.0
    var pulseShown = 0.0
    // What the touch in progress does: 0 nothing, 1 the wheel, 2 the card.
    var touch = 0
    var cardStart = 0.0
    var cardFired = false
    val frame = mutableIntStateOf(0)
}

/**
 * A vintage rotary dial on a bottle-green bakelite body. Put a finger in a hole and turn the wheel
 * clockwise to the finger stop; let go and it runs home at a governed speed, a pulse for each
 * number, and the digit drops onto the paper tape above. Letting go early returns without dialling;
 * holding the number card clears the tape.
 *
 * @param autoPlay dials a number, clears it with a press on the card, and starts over.
 * @param onDigit called with each digit as it is dialled.
 */
@Composable
fun RotaryPhoneDial(modifier: Modifier = Modifier, autoPlay: Boolean = false, onDigit: ((Int) -> Unit)? = null) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val dialled by rememberUpdatedState(onDigit)
    val state = remember(reducedMotion) { DialState(reducedMotion) }
    val digits = remember { mutableStateListOf<TypedDigit>() }
    val nextId = remember { intArrayOf(0) }
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density

    fun addDigit(value: Int, byUser: Boolean) {
        val d = TypedDigit(nextId[0]++, value)
        digits.add(d)
        while (digits.size > MAX_DIGITS) digits.removeAt(0)
        if (byUser) dialled?.invoke(value)
    }

    fun clearDigits() {
        val leaving = digits.toList()
        for (d in leaving) {
            scope.launch {
                // Removed even if the fade is cut short (by the digit's own drop-in starting late).
                try {
                    d.alpha.animateTo(0f, tween(220, easing = InOutQuad))
                } finally {
                    digits.remove(d)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Rotary dial. Number dialled: ${digits.joinToString(" ") { it.value.toString() }.ifEmpty { "none" }}. " +
                    "Drag a hole to the stop to dial; hold the card to clear."
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val g = remember(width, height, safeTop, safeBottom) {
            val stripY = safeTop + HEADER
            val top = stripY + STRIP_H + 20
            val bottom = height - safeBottom - FOOTER
            // Never below a usable size, even on a very short window.
            val plateR = maxOf(40f, minOf(width * PLATE_SHARE, (bottom - top) / 2 * 0.86f, PLATE_MAX))
            val wheelR = plateR * WHEEL
            DialGeometry(
                width = width,
                height = height,
                cx = width / 2,
                cy = (top + bottom) / 2,
                plateR = plateR,
                wheelR = wheelR,
                ringR = wheelR * RING,
                holeR = wheelR * HOLE,
                hubR = wheelR * HUB,
                strip = RectF(SIDE, stripY, width - SIDE, stripY + STRIP_H),
            )
        }
        val painter = remember(g) { DialPainter(g) }

        LaunchedEffect(state, auto) {
            state.clock = 0.0
            state.hold = 0.0
        }

        LaunchedEffect(state, auto) {
            var last = -1L
            val s = state.sim
            while (true) {
                withFrameNanos { nanos ->
                    // The script and the mechanism share one clock, capped so a slow frame cannot skip ahead.
                    val raw = if (last < 0) 1.0 / 60 else (nanos - last) / 1e9
                    last = nanos
                    val dt = min(raw, MAX_FRAME)
                    if (dt <= 0) return@withFrameNanos
                    state.now += dt
                    state.finger.hole = -1
                    // A finger resting in a hole keeps the script away.
                    if (s.touch != -1L && !s.scripted) state.hold = USER_HOLD
                    if (state.hold > 0) {
                        state.hold -= dt
                        if (state.hold <= 0) state.clock = 0.0
                    } else if (auto) {
                        val before = state.clock
                        val t = (before + dt) % LOOP
                        state.clock = t
                        s.script(before, t, state.finger)
                    }
                    // A held card clears the strip once it has been held long enough.
                    if (state.touch == 2 && !state.cardFired && state.now - state.cardStart >= CLEAR_PRESS) {
                        state.cardFired = true
                        s.clear(true)
                        s.cardPress(false)
                    }
                    s.step(dt)
                    val showing = if (s.dialling || (s.mode == MODE_DRAG && s.reached)) 1.0 else 0.0
                    state.pulseShown += (showing - state.pulseShown) * (1 - exp(-dt * (if (showing > 0) 14 else 5)))
                    if (s.events != 0) {
                        val events = s.events
                        val byUser = s.eventsByUser
                        s.events = 0
                        s.eventsByUser = false
                        if (events and EVENT_DIGIT != 0) addDigit(s.digit, byUser)
                        if (events and EVENT_CLEAR != 0) clearDigits()
                        if (byUser) {
                            when {
                                events and EVENT_CLEAR != 0 -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                events and EVENT_STOP != 0 -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                events and EVENT_TICK != 0 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                    state.frame.intValue += 1
                }
            }
        }

        // Everything that never moves is drawn once, on its own layer.
        Canvas(Modifier.fillMaxSize().graphicsLayer {}) {
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.scale(density, density)
                with(painter) { n.drawStatic(g) }
                n.restore()
            }
        }

        // The moving parts, on a layer of their own so a new frame does not redraw the backdrop.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {}
                .pointerInput(state, g) {
                    fun angle(x: Float, y: Float) = atan2((y / density - g.cy).toDouble(), (x / density - g.cx).toDouble()) * 180 / Math.PI
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val s = state.sim
                        val dx = down.position.x / density - g.cx
                        val dy = down.position.y / density - g.cy
                        // A new gesture means no finger of ours is down: a turn left over from a
                        // gesture that was cut short lets go now.
                        if (s.touch != -1L && !s.scripted) s.release(s.touch)
                        state.touch = 0
                        // The wheel only moves with a finger in a hole: anywhere else the touch is not for it.
                        if (s.touch == -1L || s.scripted) {
                            val hole = s.holeAt(angle(down.position.x, down.position.y), hypot(dx, dy).toDouble(), g.holeR.toDouble(), g.ringR.toDouble())
                            if (hole >= 0) {
                                state.hold = USER_HOLD
                                s.cancelScripted()
                                s.grab(USER_ID, hole, angle(down.position.x, down.position.y), false)
                                state.touch = 1
                            } else if (hypot(dx, dy) < g.hubR) {
                                // Holding the number card clears the strip.
                                state.hold = USER_HOLD
                                s.cancelScripted()
                                s.cardPress(true)
                                state.touch = 2
                                state.cardStart = state.now
                                state.cardFired = false
                            }
                        }
                        if (state.touch == 0) return@awaitEachGesture
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (state.touch == 1) {
                                    change.consume()
                                    state.hold = USER_HOLD
                                    s.drag(USER_ID, angle(change.position.x, change.position.y))
                                } else if (state.touch == 2) {
                                    val moved = (change.position - down.position).getDistance() / density
                                    if (moved > CARD_SLOP) {
                                        s.cardPress(false)
                                        state.touch = 0
                                        break
                                    }
                                }
                            }
                        } finally {
                            // Also when the gesture is cancelled (the layout changed, the block closed).
                            if (state.touch == 1) s.release(USER_ID)
                            if (state.touch == 2) s.cardPress(false)
                            state.touch = 0
                        }
                    }
                },
        ) {
            state.frame.intValue
            val s = state.sim
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.scale(density, density)
                with(painter) {
                    n.drawWheel(g, s.phi)
                    n.drawStop(g, s.kick)
                    n.drawCard(g, s.card)
                    val f = state.finger
                    if (f.hole >= 0) n.drawFinger(g, f.angle, f.shown)
                    n.drawPulses(g, s.pulses, state.pulseShown)
                }
                n.restore()
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE.dp, end = SIDE.dp, top = (safeTop + 16).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                "ROTARY DIAL",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = Pale, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
            )
            BasicText(
                "Elmwood $CARD_NUMBER",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = Pale, fontSize = 13.sp, fontWeight = FontWeight.Normal, fontFamily = Monospace),
            )
        }

        // The typed digits, left to right on the paper, grouped 3 · 3 · 4.
        Row(
            Modifier
                .offset(x = (g.strip.left + 18).dp, y = g.strip.top.dp)
                .width((g.width - (g.strip.left + 18) - (g.strip.left + 104)).coerceAtLeast(0f).dp)
                .height(STRIP_H.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.forEachIndexed { index, d ->
                androidx.compose.runtime.key(d.id) {
                    TypedDigitView(d, index, reducedMotion)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = SIDE.dp, end = SIDE.dp, bottom = (safeBottom + 24).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BasicText(
                "Turn a hole to the stop · Hold the card to clear",
                style = TextStyle(color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

/** One typed digit: it drops onto the strip from above, a little large, and settles with a spring. */
@Composable
private fun TypedDigitView(d: TypedDigit, index: Int, reduced: Boolean) {
    LaunchedEffect(d) {
        if (reduced) {
            d.drop.snapTo(0f)
            d.scale.snapTo(1f)
            d.alpha.animateTo(1f, tween(200, easing = InOutQuad))
            return@LaunchedEffect
        }
        launch { d.alpha.animateTo(1f, tween(90, easing = LinearEasing)) }
        launch { d.drop.animateTo(0f, Drop) }
        d.scale.animateTo(1f, DropScale)
    }
    BasicText(
        d.value.toString(),
        modifier = Modifier
            .padding(start = if (index == 3 || index == 6) 12.dp else 0.dp)
            .graphicsLayer {
                translationY = (d.drop.value + jitterY(d.id)) * density
                scaleX = d.scale.value
                scaleY = d.scale.value
                rotationZ = jitterRotate(d.id)
                alpha = d.alpha.value
            },
        style = TextStyle(
            color = Ink.copy(alpha = jitterInk(d.id)),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Monospace,
        ),
    )
}
