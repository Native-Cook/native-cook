package com.ngocdevv.nativecook.blocks.media.vinylswapscratch

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/*
 Vinyl Swap Scratch — a turntable "now playing" screen you can scratch, and whose whole colour
 changes with the record on the platter. A port of the SwiftUI block (VinylSwapScratch.swift); the
 numbers are the same.

 Look: a black record (diameter 1.12 × the screen width) cropped by the left edge, centred at
 29 % / 35.5 % of the screen, its label drawn in code (two invented albums: "Slow Satellites" by
 The Amber Rooms, a saffron sunburst; "Glass Harbor" by Noa Lind, cream with teal rings). The
 background is the label's colour, blurred and tinted (ochre to near-black for A, teal for B).
 A chrome tonearm pivots at the top right. Under the record a slim waveform strip (bars that
 scroll with the platter's angle, a fixed playhead), then the title, then three dark pills.

 autoPlay timeline (LOOP = 10 s, VinylSwapScratchMotion.kt):
   0.0 – 1.5    album A plays (≈ 218 °/s, rounded so the platter turns 5 whole times a loop).
   1.3 – 1.5    a finger lands on the record; 1.5 the platter stops under it in 0.12 s.
   1.62 – 3.16  three baby scratches (back 55°, 75°, 95° and forward again, 0.6 / 0.5 / 0.44 s):
                the waveform scrubs back and forth, bars smear and flare in the accent colour,
                arcs flicker at the rim.
   3.2          release: the motor pulls the platter back up (spring ω 12, ζ 0.45, ~20 % overshoot).
   4.6 – 6.4    "next" is pressed: the arm lifts and swings out (0.4 s), the record slides off to
                the left (0.3 – 0.8 s, ease-in), record B slides in from the right (0.6 s on, spring
                ω 11, ζ 0.62), the colours cross-fade (0.4 – 1.4 s), the title changes (0.45 – 1.05 s),
                the arm swings back and drops (1.3 – 1.8 s). The waveform hushes while the stylus is up.
   7.7 – 9.5    "previous": the same, B back to A.
   10.0         the same frame as 0.0.

 Touch: a finger on the record stops the platter under it (0.12 s), turns it with the finger
 (the angle around the centre) and scrubs the waveform; on release the motor spins it back up.
 Pause stops the platter over 0.8 s, play spins it up; previous/next change the record. 2 s
 after the last touch the screen goes back to album A playing and the loop starts again.
 Reduced motion: nothing spins and nothing plays by itself; changing the record cross-fades in
 0.3 s; the record still turns under a finger.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

/** The platter under the hand: from [date] at [angle], either the motor pulling it from [from] or a brake over [duration]. */
private class VinylSpin(val date: Double, val angle: Double, val brake: Boolean, val from: Double, val duration: Double = 0.0)

private class VinylHand(val pointer: Double, val turned: Double, val velocity: Double, val moved: Double, val ticked: Double)

private class VinylController(val reduced: Boolean) {
    /** The loop's t = 0, and the angle added to the loop's own (so a hand-off keeps the record still). */
    var autoStart by mutableStateOf<Double?>(null)
    var angleOffset by mutableDoubleStateOf(0.0)
    // The hand-driven state.
    var album by mutableIntStateOf(0)
    /** A record change: when, from, to. */
    var change by mutableStateOf<Triple<Double, Int, Int>?>(null)
    var motorOn by mutableStateOf(true)
    var spin by mutableStateOf(VinylSpin(nowSeconds(), 0.0, false, if (reduced) 0.0 else VinylMotion.omega))
    var hand by mutableStateOf<VinylHand?>(null)
    /** Reduced motion: frames run only while something cross-fades or a finger is down. */
    var busy by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    val motorSpeed get() = if (reduced || !motorOn) 0.0 else VinylMotion.omega

    fun pose(at: Double): VinylMotion.Pose {
        val start = autoStart
        if (start != null && at >= start) {
            val t = (at - start) % VinylMotion.LOOP
            return VinylMotion.loopPose(t).also { it.angle += angleOffset }
        }
        val pl = platter(at)
        var p = VinylMotion.Pose.rest(album)
        val c = change
        if (c != null) {
            val e = at - c.first
            if (e < VinylMotion.SWAP_LENGTH) VinylMotion.swap(p, e, c.second, c.third, reduced) else p = VinylMotion.Pose.rest(c.third)
        }
        p.angle = pl[0]
        p.speed = pl[1]
        p.clock = at % 1000
        return p
    }

    /** The hand-driven platter: angle and speed. */
    fun platter(at: Double): DoubleArray {
        val s = spin
        val t = max(0.0, at - s.date)
        val r = if (s.brake) VinylMotion.brake(s.from, s.duration, t) else VinylMotion.approach(s.from, motorSpeed, t)
        var a = s.angle + r[0]
        var v = r[1]
        val h = hand
        if (h != null) {
            a += h.turned
            v += h.velocity * exp(-max(0.0, at - h.moved) / 0.08)
        }
        return doubleArrayOf(a, v)
    }

    /** Freezes whatever plays into the hand-driven state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val start = autoStart
        if (start != null) {
            if (now >= start) {
                val t = (now - start) % VinylMotion.LOOP
                val p = VinylMotion.loopPose(t)
                val s = VinylMotion.loopState(t)
                album = s.first
                change = s.second?.let { Triple(now - (t - it.first), it.second, it.third) }
                motorOn = true
                spin = VinylSpin(now, p.angle + angleOffset, false, p.speed)
            }
            autoStart = null
        }
        val c = change
        if (c != null && now - c.first >= VinylMotion.SWAP_LENGTH) {
            album = c.third
            change = null
        }
    }
}

@Composable
fun VinylSwapScratch(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val c = remember(reduced) { VinylController(reduced) }
    val scope = rememberCoroutineScope()
    val painter = remember { VinylPainter() }

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
    }
    LaunchedEffect(c, c.busy) {
        c.frame = nowSeconds()
        if (!reduced || c.busy) while (true) withFrameNanos { c.frame = nowSeconds() }
    }

    fun markBusy(seconds: Double) {
        c.busy = true
        scope.launch {
            delay((seconds * 1000).toLong())
            if (c.hand == null) c.busy = false
        }
    }

    /** 2 s after the last touch: back to album A playing, then the loop from its start. */
    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            if (c.hand != null) return@launch
            val now = nowSeconds()
            c.takeOver(now)
            var ready = now
            val ch = c.change
            if (ch == null && c.album != 0) {
                c.change = Triple(now, c.album, 0)
                ready = now + VinylMotion.SWAP_LENGTH
            } else if (ch != null) {
                ready = ch.first + VinylMotion.SWAP_LENGTH
            }
            if (!c.motorOn) {
                val pl = c.platter(now)
                c.motorOn = true
                c.spin = VinylSpin(now, pl[0], false, pl[1])
            }
            // Let the motor settle (1 s after its last push) so the hand-off keeps the speed.
            ready = max(ready, c.spin.date + 1)
            c.angleOffset = c.platter(ready)[0]
            c.autoStart = ready
        }
    }

    fun togglePlay() {
        val now = nowSeconds()
        c.takeOver(now)
        if (reduced) markBusy(0.4)
        val pl = c.platter(now)
        c.motorOn = !c.motorOn
        c.spin = if (c.motorOn) VinylSpin(now, pl[0], false, pl[1]) else VinylSpin(now, pl[0], true, pl[1], 0.8)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scheduleResume()
    }

    fun skip() {
        val now = nowSeconds()
        c.takeOver(now)
        if (reduced) markBusy(0.4)
        if (c.change != null) return
        c.change = Triple(now, c.album, 1 - c.album)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF15110A))) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val g = remember(maxWidth, maxHeight, top, bottom) { VinylGeo(maxWidth.value, maxHeight.value, top, bottom) }
        val pose by remember(c) { derivedStateOf { c.pose(c.frame) } }
        val shownAlbum by remember(c) { derivedStateOf { if (pose.titleAlpha[1] > pose.titleAlpha[0]) 1 else 0 } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Now playing ${vinylAlbums[shownAlbum].title} by ${vinylAlbums[shownAlbum].artist}" }
                .pointerInput(c, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val cx = g.center.x
                        val cy = g.center.y
                        val sx = down.position.x / density
                        val sy = down.position.y / density
                        if (hypot(sx - cx, sy - cy) > g.r + 8 || !(sx < g.w * 0.82f || sy > g.pivot.y + 60)) return@awaitEachGesture
                        var now = nowSeconds()
                        c.takeOver(now)
                        val pl = c.platter(now)
                        c.spin = VinylSpin(now, pl[0], true, pl[1], VinylMotion.STOP_FOR)
                        var pointer = Math.toDegrees(atan2((sy - cy).toDouble(), (sx - cx).toDouble()))
                        c.hand = VinylHand(pointer, 0.0, 0.0, now, 0.0)
                        c.busy = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val h = c.hand ?: break
                            now = nowSeconds()
                            val x = change.position.x / density
                            val y = change.position.y / density
                            val next = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble()))
                            var d = next - pointer
                            if (d > 180) d -= 360
                            if (d < -180) d += 360
                            pointer = next
                            val dt = max(1.0 / 120, now - h.moved)
                            val velocity = 0.6 * h.velocity * exp(-dt / 0.08) + 0.4 * d / dt
                            val turned = h.turned + d
                            var ticked = h.ticked
                            if (abs(turned - ticked) >= 15) {
                                ticked = turned
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            c.hand = VinylHand(pointer, turned, velocity, now, ticked)
                        }
                        now = nowSeconds()
                        val a = c.platter(now)[0]
                        c.hand = null
                        c.spin = VinylSpin(now, a, false, 0.0)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (reduced) markBusy(0.1) else c.busy = false
                        scheduleResume()
                    }
                },
        ) {
            val p = pose
            scale(density, density, Offset.Zero) {
                with(painter) {
                    background(g, p.mix)
                    for (i in intArrayOf(1, 0)) {
                        if (p.discAlpha[i] > 0.001 && abs(p.discX[i]) < 1) {
                            disc(g, i, Offset(g.center.x + (p.discX[i] * g.slide).toFloat(), g.center.y), p.angle, p.discAlpha[i].toFloat())
                        }
                    }
                    scratchMarks(g, p)
                    finger(g, p)
                    arm(g, p.arm, p.lift)
                    waveform(g, p)
                }
            }
        }

        // Title and artist of both records, cross-fading; the speed tag on the right.
        Box(Modifier.offset(x = 24.dp, y = g.titleTop.dp).width((g.w - 48).dp)) {
            for (i in 0..1) {
                Column(
                    Modifier.graphicsLayer {
                        alpha = pose.titleAlpha[i].toFloat()
                        translationX = (pose.titleX[i] * density).toFloat()
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(vinylAlbums[i].title, style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    BasicText(vinylAlbums[i].artist, style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontSize = 15.sp), maxLines = 1)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 6.dp)
                    .height(24.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "33⅓ RPM",
                    style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                )
            }
        }

        // Controls.
        val playing = c.motorOn || c.autoStart != null
        Row(Modifier.offset(x = 24.dp, y = g.controlsTop.dp).width((g.w - 48).dp), verticalAlignment = Alignment.Top) {
            VinylPill(if (playing) VinylIcon.Pause else VinylIcon.Play, if (playing) "PAUSE" else "PLAY", 72f, { pose.press[0] }, ::togglePlay)
            Spacer(Modifier.weight(1f))
            VinylPill(VinylIcon.Prev, "PREV", 60f, { pose.press[1] }, ::skip)
            Spacer(Modifier.width(12.dp))
            VinylPill(VinylIcon.Next, "NEXT", 60f, { pose.press[2] }, ::skip)
        }
    }
}

private enum class VinylIcon { Play, Pause, Prev, Next }

@Composable
private fun VinylPill(icon: VinylIcon, label: String, width: Float, press: () -> Double, action: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    Column(
        Modifier
            .graphicsLayer {
                val s = if (pressed) 0.94f else 1f
                scaleX = s
                scaleY = s
            }
            .semantics {
                role = Role.Button
                contentDescription = label.lowercase().replaceFirstChar { it.uppercase() }
                onClick { currentAction(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { currentAction() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width.dp, 36.dp)) {
            val p = press().toFloat()
            val d = density
            val s = 1 - 0.08f * p
            scale(s, s) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.22f * p), cornerRadius = r)
                drawRoundRect(Color.Black.copy(alpha = 0.34f - 0.14f * p), cornerRadius = r)
                drawRoundRect(
                    Color.White.copy(alpha = 0.1f + 0.2f * p),
                    topLeft = Offset(0.5f * d, 0.5f * d), size = Size(size.width - d, size.height - d), cornerRadius = CornerRadius(size.height / 2 - 0.5f * d),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(d),
                )
                scale(d, d, Offset(size.width / 2, size.height / 2)) {
                    vinylIcon(icon, Offset(size.width / 2, size.height / 2))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            label,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace, letterSpacing = (1.2 / 9).em,
            ),
        )
    }
}

/** The pill glyphs, about 13 units tall, centred on [c] (in a scope scaled to dp about c). */
private fun DrawScope.vinylIcon(icon: VinylIcon, c: Offset) {
    val ink = Color.White.copy(alpha = 0.92f)
    fun tri(x0: Float, x1: Float) = Path().apply {
        moveTo(c.x + x0, c.y - 6f)
        lineTo(c.x + x1, c.y)
        lineTo(c.x + x0, c.y + 6f)
        close()
    }
    when (icon) {
        VinylIcon.Pause -> {
            drawRoundRect(ink, Offset(c.x - 5f, c.y - 6f), Size(3.5f, 12f), CornerRadius(1f))
            drawRoundRect(ink, Offset(c.x + 1.5f, c.y - 6f), Size(3.5f, 12f), CornerRadius(1f))
        }
        VinylIcon.Play -> drawPath(tri(-4.5f, 6f), ink)
        VinylIcon.Prev -> {
            drawRoundRect(ink, Offset(c.x - 6f, c.y - 6f), Size(2.5f, 12f), CornerRadius(1f))
            drawPath(tri(6f, -3.5f), ink)
        }
        VinylIcon.Next -> {
            drawRoundRect(ink, Offset(c.x + 3.5f, c.y - 6f), Size(2.5f, 12f), CornerRadius(1f))
            drawPath(tri(-6f, 3.5f), ink)
        }
    }
}
