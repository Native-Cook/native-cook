package com.ngocdevv.nativecook.blocks.modals.coachmarkspotlight

// Penny, a budgeting app, on its first run: the home screen, the tour over it and the autoPlay script.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlin.math.min

private const val GHOST = 44f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

/** A tap's press: in 100 ms before the finger lifts at `at`, out 120 ms after. */
private fun pressAt(t: Float, at: Float): Float {
    val d = t - at
    if (d <= -0.1f || d >= 0.12f) return 0f
    return if (d < 0f) 1f + d / 0.1f else 1f - d / 0.12f
}

/** The ring's breathing in the holds: phase and how much shows (fades at each hold's edges). */
private fun pulseAt(t: Float): Pair<Float, Float> {
    for (hold in HOLDS) {
        val start = hold[0]
        val end = hold[1]
        val tt = if (end > LOOP && t < end - LOOP) t + LOOP else t
        if (tt in start..end) {
            val phase = ((tt - start) / PULSE_PERIOD) % 1f
            val gate = seg(tt, start, 0.15f) * (1f - seg(tt, end - 0.15f, 0.15f))
            return phase to gate
        }
    }
    return 0f to 0f
}

/** The tour at loop time t. */
private fun applyPose(m: CoachMotion, t: Float) {
    val before = t < CmsT.reset
    val m1 = easeInOutCubic(seg(t, CmsT.morph1, MORPH))
    val m2 = easeInOutCubic(seg(t, CmsT.morph2, MORPH))
    val m3 = easeInOutCubic(seg(t, CmsT.morph3, MORPH))
    m.p.put(if (before) m1 + m2 - m3 else 0f)
    // The tooltip rides the cutout's curve TIP_LAG behind it, so it never arrives first.
    val s1 = easeInOutCubic(seg(t, CmsT.morph1 + TIP_LAG, MORPH))
    val s2 = easeInOutCubic(seg(t, CmsT.morph2 + TIP_LAG, MORPH))
    val s3 = easeInOutCubic(seg(t, CmsT.morph3 + TIP_LAG, MORPH))
    m.tipP.put(if (before) s1 + s2 - s3 else 0f)

    var x = 0f
    if (t > CmsT.dragStart && t < CmsT.dragEnd) x = GHOST_DRAG * easeInOutCubic((t - CmsT.dragStart) / (CmsT.dragEnd - CmsT.dragStart))
    else if (t >= CmsT.dragEnd) x = GHOST_DRAG * (1f - springAt(t - CmsT.dragEnd, DRAG_SPRING))
    m.tipX.put(x)

    val shown = if (t < CmsT.intro) 1f - easeInOutCubic(seg(t, CmsT.fadeOut, CmsT.fadeOutSpan)) else easeOutCubic(seg(t, CmsT.intro, 0.4f))
    m.shown.put(shown)
    // The spring's tail is forced to exactly 0 over the rest, so the loop's last frame is its first.
    m.iris.put(if (before) 0f else (1f - springAt(t - CmsT.intro, IRIS_SPRING)) * (1f - seg(t, CmsT.rest, 0.4f)))
    m.tipShow.put(
        if (t < CmsT.tipIn) 1f - easeInOutCubic(seg(t, CmsT.fadeOut, 0.25f)) else easeOutCubic(seg(t, CmsT.tipIn, CmsT.tipInSpan)),
    )
    m.visible = if (shown > 0.5f) 1 else 0

    val (phase, gate) = pulseAt(t)
    m.pulse.put(phase)
    m.pulseGate.put(gate)
    m.nextPress.put(pressAt(t, CmsT.next1))
    m.targetPress.put(pressAt(t, CmsT.tapCard))
    m.skipPress.put(pressAt(t, CmsT.skip))
}

/** The resting tour: first step, shown, still. */
private fun restPose(m: CoachMotion) {
    m.p.set(0f)
    m.tipP.set(0f)
    m.tipX.set(0f)
    m.shown.set(1f)
    m.tipShow.set(1f)
    m.iris.set(0f)
    m.pulse.set(0f)
    m.pulseGate.set(0f)
    m.nextPress.set(0f)
    m.skipPress.set(0f)
    m.targetPress.set(0f)
    m.step = 0
    m.visible = 1
}

private class GhostPoint(val t: Float, val x: Float, val y: Float)

private fun ghostAt(path: List<GhostPoint>, t: Float, xAxis: Boolean): Float {
    fun v(p: GhostPoint) = if (xAxis) p.x else p.y
    if (t <= path[0].t) return v(path[0])
    for (i in 1 until path.size) {
        if (t <= path[i].t) {
            val u = easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t))
            return mix(u, v(path[i - 1]), v(path[i]))
        }
    }
    return v(path.last())
}

private fun ghostOpacity(t: Float): Float {
    val a = seg(t, CmsT.ghostIn, 0.18f) * (1f - seg(t, CmsT.next1 + 0.15f, 0.2f))
    val b = seg(t, CmsT.tapCard - 0.4f, 0.2f) * (1f - seg(t, CmsT.tapCard + 0.15f, 0.2f))
    val c = seg(t, CmsT.dragStart - 0.4f, 0.2f) * (1f - seg(t, CmsT.dragEnd + 0.08f, 0.2f))
    val d = seg(t, CmsT.skip - 0.4f, 0.2f) * (1f - seg(t, CmsT.skip + 0.15f, 0.2f))
    return maxOf(a, b, c, d)
}

private fun ghostPress(t: Float): Float {
    val drag = seg(t, CmsT.dragStart - 0.08f, 0.08f) * (1f - seg(t, CmsT.dragEnd, 0.1f))
    return maxOf(pressAt(t, CmsT.next1), pressAt(t, CmsT.tapCard), pressAt(t, CmsT.skip), drag)
}

/** The driver's own state: clock, mode and the ghost finger. */
private class CmsDriver {
    var clock = 0f
    var mode by mutableIntStateOf(MODE_AUTO)
    var seen = 0
    var idle = 0f
    var rootHeld = 0
    val ghostX = mutableFloatStateOf(0f)
    val ghostY = mutableFloatStateOf(0f)
    val ghostO = CmsValue(0f)
    val ghostS = mutableFloatStateOf(0f)
}

private class CmsGeometry(val grabX: Float, val path: List<GhostPoint>)

@Composable
fun CoachMarkSpotlight(autoPlay: Boolean = true, steps: List<CoachStep> = STEPS) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val count = min(steps.size, 3)
    val shownSteps = remember(steps, count) { steps.take(count) }
    val m = remember { CoachMotion() }
    val driver = remember { CmsDriver() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PAPER[1])
            .clipToBounds()
            .pointerInput(m) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed() }) {
                            driver.rootHeld = event.changes.count { it.pressed }
                            m.touches += 1
                        }
                    }
                }
            },
    ) {
        val screenDensity = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val width = maxWidth.value
        val height = maxHeight.value
        val insetTop = insets.getTop(screenDensity) / screenDensity.density
        val insetBottom = insets.getBottom(screenDensity) / screenDensity.density
        val layout = remember(width, height, insetTop, insetBottom) { pennyLayout(width, height, insetTop, insetBottom) }

        /** The three things the tour explains, padded into cutouts: a circle, a wide card, a tab item. */
        val targets = remember(layout, count) {
            val fab = layout.fab
            val card = layout.card
            val tab = layout.tab
            val d = fab.w + PAD * 2f
            listOf(
                CmsBox(fab.x - PAD, fab.y - PAD, d, d, d / 2f),
                CmsBox(card.x - 6f, card.y - 6f, card.w + 12f, card.h + 12f, card.r + 6f),
                CmsBox(tab.x, tab.y, tab.w, tab.h, tab.r),
            ).take(count)
        }
        val tipW = min(width - 32f, TIP_MAX_W)
        val places = remember(targets, width, height, tipW) { tipPlaces(targets, width, height, tipW) }

        val geometry = remember(places, targets, tipW) {
            fun at(i: Int) = places[min(i, places.size - 1)]
            fun nextX(i: Int) = at(i).x + tipW - TIP_PAD - NEXT_W / 2f
            fun footY(i: Int) = at(i).y + TIP_H - TIP_PAD - FOOT_H / 2f
            fun skipX(i: Int) = at(i).x + tipW - TIP_PAD - NEXT_W - 6f - SKIP_W / 2f
            val card = targets[min(1, targets.size - 1)]
            val cardX = card.x + card.w * 0.62f
            val cardY = card.y + card.h * 0.55f
            val grabX = at(2).x + tipW * 0.36f
            val grabY = at(2).y + 64f
            val t = CmsT
            CmsGeometry(
                grabX,
                listOf(
                    GhostPoint(t.ghostIn, nextX(0) + 24f, footY(0) + 40f),
                    GhostPoint(t.next1 - 0.08f, nextX(0), footY(0)),
                    GhostPoint(t.next1 + 0.12f, nextX(0), footY(0)),
                    GhostPoint(t.next1 + 0.45f, nextX(0) + 12f, footY(0) + 30f),
                    GhostPoint(t.tapCard - 0.4f, cardX + 20f, cardY + 40f),
                    GhostPoint(t.tapCard - 0.08f, cardX, cardY),
                    GhostPoint(t.tapCard + 0.12f, cardX, cardY),
                    GhostPoint(t.tapCard + 0.45f, cardX + 10f, cardY + 30f),
                    GhostPoint(t.dragStart - 0.4f, grabX + 20f, grabY + 36f),
                    GhostPoint(t.dragStart - 0.04f, grabX, grabY),
                    GhostPoint(t.dragStart, grabX, grabY),
                    // While the finger drags, its x is the tooltip's (below).
                    GhostPoint(t.dragEnd, grabX + GHOST_DRAG, grabY),
                    GhostPoint(t.dragEnd + 0.3f, grabX + GHOST_DRAG + 12f, grabY + 24f),
                    GhostPoint(t.skip - 0.4f, skipX(1) + 20f, footY(1) + 36f),
                    GhostPoint(t.skip - 0.08f, skipX(1), footY(1)),
                    GhostPoint(t.skip + 0.12f, skipX(1), footY(1)),
                    GhostPoint(t.skip + 0.45f, skipX(1) + 10f, footY(1) + 30f),
                ),
            )
        }
        // Read every frame through State, so a new layout reaches the running loop.
        val geo by rememberUpdatedState(geometry)

        LaunchedEffect(m, playing, count) {
            val d = driver
            if (playing) {
                d.clock = 0f
                d.mode = MODE_AUTO
                m.driven = 1
            } else {
                // Reduce Motion or autoPlay off: the first step, shown and still.
                d.mode = MODE_MANUAL
                m.driven = 0
                d.ghostO.set(0f)
                restPose(m)
            }
            val wake = Channel<Unit>(Channel.CONFLATED)
            m.onAnimationStart { wake.trySend(Unit) }
            d.ghostO.onStart = { wake.trySend(Unit) }
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = min((now - last) / 1_000_000_000f, 0.048f)
                last = now
                if (dt <= 0f) continue
                d.ghostO.step(dt)
                val animating = m.advance(dt) || d.ghostO.active

                if (m.touches != d.seen) {
                    d.seen = m.touches
                    d.idle = 0f
                    if (d.mode != MODE_MANUAL) {
                        adoptTour(m, count)
                        d.ghostO.timing(0f, 0.12f)
                        d.mode = MODE_MANUAL
                    }
                }
                if (!playing) {
                    if (!animating) {
                        wake.receive()
                        last = withFrameNanos { it }
                    }
                    continue
                }

                if (d.mode != MODE_AUTO) {
                    // The ring keeps breathing on a settled step while the finger has the tour.
                    val settled = if (kotlin.math.abs(m.p.get() - m.step) < 0.02f && kotlin.math.abs(m.tipX.get()) < 2f) 1f else 0f
                    val gate = m.pulseGate.get()
                    m.pulse.set((m.pulse.get() + dt / PULSE_PERIOD) % 1f)
                    m.pulseGate.set(gate + (settled * m.shown.get() - gate) * min(1f, dt * 8f))
                }

                when (d.mode) {
                    MODE_MANUAL -> {
                        if (m.held > 0 || d.rootHeld > 0) {
                            d.idle = 0f
                        } else {
                            d.idle += dt
                            if (d.idle >= RESUME_AFTER) {
                                openTour(m)
                                d.mode = MODE_RETURN
                            }
                        }
                    }
                    MODE_RETURN -> if (tourAtRest(m)) {
                        d.clock = 0f
                        m.driven = 1
                        d.mode = MODE_AUTO
                    }
                    else -> {
                        var t = d.clock + dt
                        if (t >= LOOP) t -= LOOP
                        d.clock = t
                        applyPose(m, t)
                        val g = geo
                        val inDrag = t > CmsT.dragStart && t < CmsT.dragEnd
                        d.ghostX.floatValue = if (inDrag) g.grabX + m.tipX.get() else ghostAt(g.path, t, true)
                        d.ghostY.floatValue = ghostAt(g.path, t, false)
                        d.ghostO.set(ghostOpacity(t))
                        d.ghostS.floatValue = ghostPress(t)
                    }
                }
            }
        }

        PennyHome(layout, width)
        CoachMarks(shownSteps, targets, m, width, height, reducedMotion = reduced)
        Box(
            Modifier
                .size(GHOST.dp)
                .graphicsLayer {
                    alpha = if (driver.mode == MODE_AUTO) driver.ghostO.get() * 0.9f else 0f
                    translationX = (driver.ghostX.floatValue - GHOST / 2f) * density
                    translationY = (driver.ghostY.floatValue - GHOST / 2f) * density
                    val s = 1f - 0.16f * driver.ghostS.floatValue
                    scaleX = s
                    scaleY = s
                }
                .background(Color(236, 240, 236).copy(alpha = 0.34f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
    }
}

@Preview
@Composable
private fun CoachMarkSpotlightPreview() {
    CoachMarkSpotlight(autoPlay = false)
}
