package com.ngocdevv.nativecook.blocks.media.prismspectrumplayer

import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 Prism Spectrum Player — a "now playing" screen where a white beam enters a glass prism and leaves
 as a rainbow fan; each of the fan's twelve spectral bands is an equaliser bar.

 Rendering: one Canvas. The hero is one AGSL shader over the whole screen (a drawn stand-in below
 Android 13), then the static controls (a bitmap made once) and the moving ones (a handful of
 draws). The frame loop writes plain fields and bumps one counter that only the draw reads; the
 texts are composed once.

 Touch: drag across the hero to turn the prism (±25°, rubber band past it, momentum on release);
 the fan follows on a spring and opens wider as it turns. The play button plays and pauses. 2 s
 after the last touch the screen eases back to rest (paused, prism level, 1:12) in 0.8 s and the
 loop starts again. Reduced motion: no loop; the prism still turns under a finger, play/pause fades.
 */

/** Radians of prism turn per dp of horizontal drag. */
private const val DRAG_GAIN = 0.0042f
/** The fan's spring (stiffness, damping) under the finger. */
private const val FAN_K = 420f
private val FAN_C = 2 * 0.72f * sqrt(FAN_K)
private const val RETURN_S = 0.8f

private val Ink = Color(0xFFEEEAF8)
private fun nowSeconds() = System.nanoTime() / 1e9

/** Clocks and hand-driven values shared by the frame loop, the touches and the draw; plain fields. */
private class PrismState(val g: PrismGeo, private val reduced: Boolean) {
    // The clock: `now` is the frame time; the loop runs from `start` (−1 while a finger is in
    // charge); `origin` drives the ambient motion (bars, motes, glints), which never restarts.
    var now = -1.0
    var start = -1.0
    var origin = 0.0
    var resumeAt = -1.0
    private val rest = prismLoopPose(0f, PrismPose())
    val rot = PrismAnim(rest.rot)
    var fan = rest.fan
    var fanV = 0f
    val play = PrismAnim(rest.play)
    val beam = PrismAnim(rest.beam)
    val icon = PrismAnim(rest.icon)
    val elapsed = PrismAnim(rest.elapsed)
    var playing = false
    var grab = 0f
    var pastLimit = false
    var touching = false
    val pose = PrismPose()
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)

    private fun loopRunning() = start >= 0 && now >= start

    fun step(n: Double, dt: Float, auto: Boolean): Boolean {
        rot.step(n, dt)
        play.step(n, dt)
        beam.step(n, dt)
        icon.step(n, dt)
        elapsed.step(n, dt)
        tick.intValue += 1
        val animating = rot.active || play.active || beam.active || icon.active || elapsed.active
        // Under reduced motion only the fades run; the clock stays at rest, as in React Native.
        if (reduced) return animating || touching
        val prev = now
        if (prev < 0) {
            origin = n
            if (auto) start = n
        }
        now = n
        val d = if (prev < 0) 0f else dt
        if (!loopRunning()) {
            // The fan follows the prism on a spring; a playing track advances.
            val a = FAN_K * (rot.value - fan) - FAN_C * fanV
            fanV += a * d
            fan += fanV * d
            if (playing) elapsed.set(elapsed.value + d)
        }
        // 2 s after the last touch: ease back to rest, then run the loop from t = 0.
        val r = resumeAt
        if (r >= 0 && n >= r) {
            resumeAt = -1.0
            playing = false
            rot.tween(n, rest.rot, RETURN_S)
            play.tween(n, rest.play, RETURN_S)
            beam.tween(n, rest.beam, RETURN_S)
            icon.tween(n, rest.icon, 0.3f)
            elapsed.tween(n, rest.elapsed, RETURN_S)
            start = n + RETURN_S + 0.05
        }
        val fanMoving = abs(fanV) > 1e-4f || abs(rot.value - fan) > 1e-4f
        // Outside autoPlay a paused, resting screen lets the frame loop sleep (the glints pause).
        return auto || start >= 0 || resumeAt >= 0 || animating || touching || playing || fanMoving
    }

    /** Hands the loop's current pose to the hand-driven state. */
    fun takeOver() {
        if (loopRunning()) {
            val p = prismLoopPose(((now - start) % LOOP).toFloat(), PrismPose())
            rot.set(p.rot)
            fan = p.fan
            fanV = 0f
            play.set(p.play)
            beam.set(p.beam)
            icon.set(p.icon)
            elapsed.set(p.elapsed)
            playing = p.play > 0.5f
        } else if (start >= 0) {
            // Mid-return: keep where the easing got to.
            rot.set(rot.value)
            play.set(play.value)
            beam.set(beam.value)
            elapsed.set(elapsed.value)
        }
        start = -1.0
        resumeAt = -1.0
    }

    fun released(auto: Boolean) {
        if (auto) resumeAt = max(now, nowSeconds()) + 2
    }

    /** The pose at the last frame. */
    fun pose(): PrismPose {
        if (loopRunning()) return prismLoopPose(((now - start) % LOOP).toFloat(), pose)
        pose.rot = rot.value
        pose.fan = if (reduced) rot.value else fan
        pose.play = play.value
        pose.beam = beam.value
        pose.icon = icon.value
        pose.elapsed = elapsed.value
        pose.pressPlay = 0f
        pose.pressBack = 0f
        return pose
    }

    fun ambient() = if (now < 0) 0f else ((((now - origin) % LOOP) + LOOP) % LOOP).toFloat()
}

/**
 * A "now playing" screen: a white beam enters a glass prism and leaves as a rainbow fan whose
 * twelve bands are the equaliser. Drag the hero to turn the prism; the play button plays and pauses.
 *
 * @param autoPlay presses play, turns the prism both ways, pauses and skips back 5 s (LOOP 8 s).
 */
@Composable
fun PrismSpectrumPlayer(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val density = screen.density
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(screen) / density
    val bottom = insets.getBottom(screen) / density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(PRISM_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val press = remember { Animatable(0f) }
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> press.animateTo(1f, tween(110, easing = EaseOutStrong))
                is PressInteraction.Release, is PressInteraction.Cancel -> press.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 322f))
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val state = remember(w, h, top, bottom, reduced) { PrismState(PrismGeo(w, h, top, bottom), reduced) }
        val g = state.g
        val fonts = remember { PrismFonts() }
        val staticArt = remember(g, density) { prismStaticBitmap(g, fonts, density) }
        val staticDst = remember(g, density) { RectF(0f, 0f, g.w * density, g.h * density) }
        val dynamicArt = remember(g, density) { PrismDynamicArt(g, fonts, density) }
        val fallback = remember(g) { PrismFallback(g) }
        val ports = remember { FloatArray(4) }

        LaunchedEffect(state, auto) {
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
            val pressNow = press.value
            val pose = state.pose()
            val clock = state.ambient()
            scale(density, density, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    prismPorts(g, pose.rot, ports)
                    shader.setFloatUniform("size", max(1f, g.w), max(1f, g.h))
                    shader.setFloatUniform("unit", g.u)
                    shader.setFloatUniform("prism", g.cx, g.cy, g.r, pose.rot)
                    shader.setFloatUniform("ports", ports[0], ports[1], ports[2], ports[3])
                    shader.setFloatUniform("fan", prismFanDir(pose.fan), prismFanSpread(pose.fan, pose.play), pose.beam, pose.play)
                    shader.setFloatUniform("clk", clock, LOOP)
                    shader.setFloatUniform("lens", g.lmin, g.lmax)
                    drawRect(brush, Offset.Zero, Size(g.w, g.h))
                } else {
                    drawIntoCanvas { fallback.draw(it.nativeCanvas, pose, clock) }
                }
            }
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawBitmap(staticArt, null, staticDst, null)
                c.save()
                c.scale(density, density)
                val pressPlay = if (state.start >= 0 && state.now >= state.start) pose.pressPlay else pressNow
                dynamicArt.draw(c, pose, pressPlay)
                c.restore()
            }
        }

        // The hero's touch area: drag sideways to turn the prism.
        Box(
            Modifier
                .offset(0.dp, (top + 48).dp)
                .fillMaxWidth()
                .height(max(1f, g.heroBottom - top - 48).dp)
                .semantics { contentDescription = "Glass prism splitting the beam into a spectrum. Drag sideways to turn it." }
                .pointerInput(state, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        state.takeOver()
                        state.grab = state.rot.value
                        state.pastLimit = false
                        state.touching = true
                        state.wake.intValue += 1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var active = false
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    ended = true
                                    break
                                }
                                if (!active && (change.position - down.position).getDistance() > 4 * density) active = true
                                if (!active) continue
                                change.consume()
                                val raw = state.grab + (change.position.x - down.position.x) / density * DRAG_GAIN
                                state.rot.set(prismRubberBand(raw, PRISM_ROT_LIMIT))
                                val over = abs(raw) > PRISM_ROT_LIMIT
                                if (over != state.pastLimit) {
                                    state.pastLimit = over
                                    if (over) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                if (reduced) state.fan = state.rot.value
                                state.tick.intValue += 1
                            }
                        } finally {
                            if (active && ended) {
                                // Momentum: aim a little past where the finger let go, inside the limits.
                                val v = tracker.calculateVelocity().x / density * DRAG_GAIN
                                val target = max(-PRISM_ROT_LIMIT, min(PRISM_ROT_LIMIT, state.rot.value + v * 0.12f))
                                state.rot.spring(target, v, 0.45f, 0.8f)
                            }
                            state.touching = false
                            state.released(auto)
                            state.wake.intValue += 1
                        }
                    }
                },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, end = 64.dp, top = (top + 10).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText("PLAYING FROM ALBUM", style = TextStyle(color = Ink.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, textAlign = TextAlign.Center))
            BasicText("Glasshouse", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 72.dp, top = g.titleTop.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                "Low Tide Lanterns",
                style = TextStyle(color = Color(0xFFF3F1FA), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "Iva North",
                style = TextStyle(color = Ink.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The play button (drawn in the Canvas), with 8 dp of slop around it.
        Box(
            Modifier
                .offset((g.w / 2 - PRISM_PLAY_R - 8).dp, (g.controlsY - PRISM_PLAY_R - 8).dp)
                .size((PRISM_PLAY_R * 2 + 16).dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Play or pause" }
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    state.takeOver()
                    val next = !state.playing
                    state.playing = next
                    val n = nowSeconds()
                    val fade = if (reduced) 0.3f else 0.6f
                    state.play.tween(n, if (next) 1f else 0f, fade)
                    state.beam.tween(n, if (next) 1f else PRISM_REST_BEAM, fade)
                    state.icon.tween(n, if (next) 1f else 0f, 0.25f)
                    state.released(auto)
                    state.wake.intValue += 1
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 84.dp, end = 72.dp, bottom = (bottom + 23).dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            BasicText("UP NEXT", style = TextStyle(color = Ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            BasicText(
                "Paper Harbour · Iva North",
                style = TextStyle(color = Ink.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

