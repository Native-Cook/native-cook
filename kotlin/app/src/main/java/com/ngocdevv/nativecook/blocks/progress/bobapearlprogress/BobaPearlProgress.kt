package com.ngocdevv.nativecook.blocks.progress.bobapearlprogress

import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/*
 Boba Pearl Progress — "Pearl Lane", an order brewing. A clear cup fills as the order progresses:
 pearls drop in and settle, milk tea pours, the cup is shaken, sealed and pierced by a straw; the
 percentage and a four-step tracker follow.

 Rendering: one Canvas over a backdrop bitmap made once; per frame the cup(s) and the panel are
 drawn from one scene computed from the loop time (or, under the finger, from a handful of
 springs). The frame loop writes plain fields and bumps one counter that only the draw reads, so
 the screen never recomposes per frame; outside autoPlay the loop sleeps once everything rests.

 Touch: drag the cup sideways to tip it (±12°, rubber band past it): the cup follows on a spring
 (ζ 0.75), the tea's surface stays level and sloshes behind (ζ 0.22), the pearls roll to the low
 side. "Shake it" rocks the cup for 1.4 s. Meanwhile the order keeps brewing. 2 s after the last
 touch the cup is swapped for an empty one (0.9 s) and the loop starts again. Reduced motion: the
 finished cup at 100 %, still; tipping follows the finger without slosh.
 */

/** Tip per dp of drag (radians) and the limit (12°). */
private const val TIP_GAIN = 0.0028f
private const val TIP_LIMIT = (12 * PI / 180).toFloat()
private const val CUP_K = 260f
private val CUP_C = 2 * 0.75f * sqrt(CUP_K)
private const val SLOSH_K = 90f
private val SLOSH_C = 2 * 0.22f * sqrt(SLOSH_K)
private const val ROLL_K = 70f
private val ROLL_C = 2 * 0.45f * sqrt(ROLL_K)
private const val SWAP = 0.9f
private const val READY_TC = 8.2f

private val Kicker = Color(0xFFA06A48)
private val Ink = Color(0xFF3B2418)
private val InkSoft = Color(0xFF8C6E5C)

private fun rubber(x: Float, limit: Float): Float {
    val a = abs(x)
    if (a <= limit) return x
    val over = a - limit
    return sign(x) * (limit + (over * 0.55f) / (1 + over / 0.25f))
}

private fun nowSeconds() = System.nanoTime() / 1e9

/** Clocks and springs shared by the frame loop, the gestures and the draw; plain fields. */
private class BobaState(val g: BobaGeo, reduced: Boolean) {
    var now = -1.0
    var start = -1.0
    var origin = 0.0
    var resumeAt = -1.0
    // The hand-driven cup.
    var tc = if (reduced) READY_TC else 0f
    var rot = 0f
    var rotV = 0f
    var rotTarget = 0f
    var tilt = 0f
    var tiltV = 0f
    var roll = 0f
    var rollV = 0f
    var shakeAt = -10.0
    var swapAt = -10.0
    var swapTc = 0f
    /** Step now, the step before, and when it changed. */
    var stepNow = if (reduced) 3 else 0
    var stepBefore = if (reduced) 3 else 0
    var stepAt = -10.0
    var pastLimit = false
    var touching = false
    val scene = BobaScene()
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)

    fun step(n: Double, dtIn: Float, auto: Boolean): Boolean {
        val prev = now
        if (prev < 0) {
            origin = n
            if (auto) start = n
        }
        now = n
        val dt = if (prev < 0) 0f else dtIn
        tick.intValue += 1
        if (start >= 0 && n >= start) return true
        // The order keeps brewing under the finger; the cup, its tea and pearls are springs.
        tc = min(READY_TC, tc + dt)
        val shake = bobaShakeRot((n - shakeAt).toFloat())
        val total = rot + shake
        val a = CUP_K * (rotTarget - rot) - CUP_C * rotV
        rotV += a * dt
        rot += rotV * dt
        val ts = SLOSH_K * (-total - tilt) - SLOSH_C * tiltV
        tiltV += ts * dt
        tilt += tiltV * dt
        val rs = ROLL_K * (total * 70 - roll) - ROLL_C * rollV
        rollV += rs * dt
        roll += rollV * dt
        val s = bobaStepOf(tc)
        if (s != stepNow) {
            stepBefore = stepNow
            stepNow = s
            stepAt = n
        }
        val r = resumeAt
        if (r >= 0 && n >= r) {
            resumeAt = -1.0
            rotTarget = 0f
            if (tc > 1.4f) {
                swapAt = n
                swapTc = tc
                start = n + SWAP + 0.05
            } else {
                start = n + 0.6
            }
        }
        if (auto || start >= 0 || resumeAt >= 0 || touching) return true
        // Outside autoPlay: sleep once the order is ready and nothing moves.
        val resting = tc >= READY_TC && abs(rotTarget - rot) < 1e-4f && abs(rotV) < 1e-3f &&
            abs(tilt + rot) < 1e-4f && abs(tiltV) < 1e-3f && abs(roll - rot * 70) < 0.01f && abs(rollV) < 0.01f &&
            n - shakeAt > 1.5 && n - swapAt > SWAP + 0.2 && n - stepAt > 0.35
        return !resting
    }

    fun takeOver() {
        val n = now
        val st = start
        if (st >= 0 && n >= st) {
            val t = ((n - st) % LOOP).toFloat()
            bobaLoopScene(g, t, t, scene)
            val c = if (scene.count > 0 && t < 9.0f) scene.cups[0] else null
            tc = if (t >= 8.9f) 0f else min(t, READY_TC)
            rot = c?.rot ?: 0f
            tilt = c?.tilt ?: 0f
            rotV = 0f
            tiltV = 0f
            roll = 0f
            rollV = 0f
            shakeAt = if (t > 5.0f && t < 6.4f) n - (t - 5.0f) else -10.0
            stepNow = bobaStepOf(tc)
            stepBefore = stepNow
            stepAt = -10.0
        }
        swapAt = -10.0
        start = -1.0
        resumeAt = -1.0
    }

    /** The scene at the last frame time. */
    fun scene(reduced: Boolean): BobaScene {
        val sc = scene
        val n = now
        val ambient = if (n < 0) 0f else ((((n - origin) % LOOP) + LOOP) % LOOP).toFloat()
        if (start >= 0 && n >= start) {
            bobaLoopScene(g, ((n - start) % LOOP).toFloat(), ambient, sc)
            return sc
        }
        val sh = (n - shakeAt).toFloat()
        val user = sc.cups[0]
        user.set(0f, rot + bobaShakeRot(sh), tc, if (reduced) 0f else tilt, if (reduced) 0f else roll, bobaShakeEnv(sh), sh)
        sc.count = 1
        sc.percent = bobaPercent(user.tc)
        sc.step = stepNow
        sc.prevStep = stepBefore
        sc.stepFade = min(1f, ((n - stepAt) / 0.3).toFloat())
        sc.pctFrom = 0f
        sc.pctTo = 0f
        sc.pctFade = 1f
        sc.clock = ambient
        val sw = (n - swapAt).toFloat()
        if (sw >= 0 && sw <= SWAP + 0.1f) {
            // The finished cup leaves, an empty one arrives.
            val s = sw / SWAP
            user.dx = -g.w * 0.8f * bobaEaseInOutCubic(bobaSeg(s, 0f, 0.55f))
            user.tc = swapTc
            sc.cups[1].set(g.w * 0.8f * (1 - bobaEaseOutBack(bobaSeg(s, 0.35f, 1f))), 0f, 0f, 0f, 0f, 0f, 0f)
            sc.count = 2
            sc.percent = if (s > 0.5f) 0f else bobaPercent(swapTc)
            sc.pctFrom = floor(bobaPercent(swapTc))
            sc.pctTo = 0f
            sc.pctFade = bobaEaseInOutCubic(bobaSeg(s, 0.4f, 0.6f))
            sc.step = if (s > 0.45f) 0 else bobaStepOf(swapTc)
            sc.prevStep = bobaStepOf(swapTc)
            sc.stepFade = if (s > 0.45f) min(1f, (s - 0.45f) / 0.33f) else 1f
        }
        return sc
    }
}

/**
 * An order-tracking screen for a bubble tea shop: a clear cup fills as the order progresses, with
 * a percentage and a four-step tracker under it. Drag the cup to tip it, press "Shake it" to rock it.
 *
 * @param autoPlay runs one order from an empty cup to "Ready" and swaps in the next cup (LOOP 10 s).
 */
@Composable
fun BobaPearlProgress(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val density = screen.density
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(screen) / density
    val bottom = insets.getBottom(screen) / density

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val state = remember(w, h, top, bottom, reduced) { BobaState(BobaGeo(w, h, top, bottom), reduced) }
        val g = state.g
        val backdrop = remember(g, density) { bobaBackdrop(g, density) }
        val backdropDst = remember(g, density) { RectF(0f, 0f, g.w * density, g.h * density) }
        val pearls = remember(g) { bobaPackPearls(g) }
        val cupArt = remember(g, density) { BobaCupArt(g, density) }
        val panel = remember(g, density) { BobaPanelArt(g, density) }

        LaunchedEffect(state, auto, reduced) {
            if (reduced) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { nanos ->
                    val dt = if (last < 0) 0f else min((nanos - last) / 1e9f, 1f / 30f)
                    last = nanos
                    state.step(nanos / 1e9, dt, auto)
                }
                if (!keep) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    last = -1L
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            state.tick.intValue
            val sc = state.scene(reduced)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawBitmap(backdrop, null, backdropDst, null)
                c.save()
                c.scale(density, density)
                for (i in 0 until sc.count) cupArt.draw(c, sc.cups[i], pearls, sc.clock)
                panel.draw(c, sc)
                c.restore()
            }
        }

        // The cup's touch area.
        Box(
            Modifier
                .offset((g.cx - g.rimW - 40).dp, (g.top - 40).dp)
                .size((g.rimW * 2 + 80).dp, (g.cupH + 70).dp)
                .semantics { contentDescription = "Your drink. Drag sideways to tip the cup." }
                .pointerInput(state, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        state.takeOver()
                        state.pastLimit = false
                        state.touching = true
                        state.wake.intValue += 1
                        var active = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                if (!change.pressed) break
                                val dx = (change.position.x - down.position.x) / density
                                if (!active && abs(change.position.x - down.position.x) + abs(change.position.y - down.position.y) > 2 * density) active = true
                                if (!active) continue
                                change.consume()
                                val raw = dx * TIP_GAIN
                                state.rotTarget = rubber(raw, TIP_LIMIT)
                                if (reduced) {
                                    state.rot = state.rotTarget
                                    state.tick.intValue += 1
                                }
                                val over = abs(raw) > TIP_LIMIT
                                if (over != state.pastLimit) {
                                    state.pastLimit = over
                                    if (over) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } finally {
                            state.rotTarget = 0f
                            state.touching = false
                            if (reduced) {
                                state.rot = 0f
                                state.tick.intValue += 1
                            }
                            if (auto) state.resumeAt = state.now + 2
                            state.wake.intValue += 1
                        }
                    }
                },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (top + 8).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("PEARL LANE", style = TextStyle(color = Kicker, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText(
                "Order #318",
                style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
            )
            BasicText("Brown Sugar Milk Tea · Large · 50% sweet", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Normal))
        }

        ShakeButton(
            Modifier
                .offset(0.dp, g.buttonY.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!reduced) {
                state.takeOver()
                val n = if (state.now < 0) nowSeconds() else maxOf(state.now, nowSeconds())
                state.shakeAt = n
                if (auto) state.resumeAt = n + 2
                state.wake.intValue += 1
            }
        }
    }
}

/** "Shake it": a dark pill that dips to 97 % under the finger and springs back. */
@Composable
private fun ShakeButton(modifier: Modifier, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed = remember { Animatable(0f) }
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> pressed.animateTo(1f, tween(110))
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 320f))
            }
        }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .graphicsLayer {
                val s = 1 - 0.03f * pressed.value
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(Color(0x4D3B2418), 1.dp, 2.dp, 26.dp)
            .cssBoxShadow(Color(0x333B2418), 10.dp, 24.dp, 26.dp)
            .clip(shape)
            .background(Ink)
            .drawWithContent {
                drawContent()
                // The inset top highlight.
                drawLine(Color(0x1FFFFFFF), Offset(26.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 26.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Shake it", style = TextStyle(color = Color(0xFFFBEBDD), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}
