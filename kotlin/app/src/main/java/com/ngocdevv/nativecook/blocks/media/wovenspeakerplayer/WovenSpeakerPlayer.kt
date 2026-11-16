package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerMotion as M
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Woven Speaker Player: a black dome of two woven strand layers over a round display, and the page under it. The dome is the jog wheel: drag round it
 * and the track scrubs 1:1 (360 degrees = the track's length) while the two layers turn at different rates, so the diamonds of the weave shear and settle
 * like cloth; press it and the fabric dents under the finger; play and the music travels through the cloth as rings. The three lines of text under the
 * dome follow the playing state, the headline is the queue's handle (swipe it to change track), the centre and the two discs tap, the pill opens the queue.
 *
 * LOOP = 8 s ([WovenSpeakerPlayerMotion.LOOP]): the speaker is played at 0.4, the dome is turned from 3 to 9 o'clock (0:01 -> 1:40) 1.5-2.6 with the weave
 * shearing, released at 2.9 and coasts to 1:46 (snapping to the second at 3.4), turned back to 0:00 5.68-6.4, paused at 6.6, rest from 7.25. A touch takes
 * over; two seconds after the last one the demo comes back. Reduce Motion: no pulses or shear, 200 ms fades. The dome is an AGSL shader on API 33+, a
 * strand drawing below.
 * Not ported from the RN props: `position`, `level`, `beats`, `turnSeconds`, `strands`, `tokens`, `children`, controlled `playing` / `index`, `coast`, `fibre`.
 */
@Composable
fun WovenSpeakerPlayer(
    autoPlay: Boolean = false,
    queue: List<WovenSpeakerPlayerTrack>? = null,
    size: Double = Theme.DEFAULT_SIZE,
    accent: Int = Theme.accent,
    ambient: Boolean = true,
    haptics: Boolean = true,
    onPlayingChange: ((Boolean) -> Unit)? = null,
    onSeek: ((Double) -> Unit)? = null,
    onSeekCommit: ((Int) -> Unit)? = null,
    onIndexChange: ((Int) -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    accessibilityLabel: String = Theme.POSITION,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { WovenSpeakerPlayerHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11yPos by remember { mutableIntStateOf(0) }
    var a11yPlaying by remember { mutableStateOf(false) }
    var a11yIndex by remember { mutableIntStateOf(0) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.page))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, size, insetTop, insetBottom) { Theme.Layout(w, h, size, insetTop, insetBottom) }
        val s = layout.s
        val dens = densityOwner.density
        val tracks = remember(queue) { (if (queue != null && queue.isNotEmpty()) queue else Theme.DEMO_QUEUE).take(6) }
        val accentTail = remember(accent) { tailOf(accent) }
        val params = remember(tracks, accent, accentTail, ambient) {
            WovenSpeakerPlayerRenderer.Params(tracks, M.TRACK, accent, accentTail, Theme.ink, Theme.mute, ambient, 128, 0.5)
        }
        val renderer = remember { WovenSpeakerPlayerRenderer(context.assets) }
        val engine = remember(autoPlay, reduced, tracks.size, s, ambient, haptics) {
            WovenSpeakerPlayerEngine(autoPlay, reduced, tracks.size, M.TRACK, ambient, haptics, s)
        }
        val cbPlaying by rememberUpdatedState(onPlayingChange)
        val cbSeek by rememberUpdatedState(onSeek)
        val cbCommit by rememberUpdatedState(onSeekCommit)
        val cbIndex by rememberUpdatedState(onIndexChange)
        val cbOpen by rememberUpdatedState(onOpenQueue)
        SideEffect {
            engine.cue = { beat -> if (haptics) vibe.emit(beat) }
            engine.emitPlaying = { p -> a11yPlaying = p; cbPlaying?.invoke(p) }
            engine.emitSeek = { sec -> cbSeek?.invoke(sec) }
            engine.emitCommit = { sec -> a11yPos = sec; cbCommit?.invoke(sec) }
            engine.emitIndex = { i -> a11yIndex = i; cbIndex?.invoke(i) }
            engine.emitOpen = { cbOpen?.invoke() }
        }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    engine.frame(dt)
                    tick.intValue++
                }
            }
        }

        val half = WovenSpeakerPlayerEngine.HIT_BOX / 2 * s
        val domeL = layout.cx - half
        val domeT = layout.cy - half
        val titleL = layout.x(-190.0)
        val titleT = layout.y(Theme.TITLE_Y) - 28 * s
        val titleW = 380 * s
        val titleH = 88 * s
        val pillL = layout.x(-Theme.PILL_W / 2) - 4
        val pillT = layout.y(Theme.PILL_Y) - 4 * s
        val pillW = Theme.PILL_W * s + 8
        val pillH = Theme.PILL_H * s + 8 * s

        Canvas(
            Modifier.fillMaxSize().pointerInput(engine, dens, layout) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x = down.position.x / dens.toDouble()
                    val y = down.position.y / dens.toDouble()
                    val inDome = x >= domeL && x <= domeL + 2 * half && y >= domeT && y <= domeT + 2 * half
                    val inPill = x >= pillL && x <= pillL + pillW && y >= pillT && y <= pillT + pillH
                    val inTitle = x >= titleL && x <= titleL + titleW && y >= titleT && y <= titleT + titleH
                    val mode = if (inDome) 1 else if (inPill) 2 else if (inTitle) 3 else 0
                    if (mode == 0) return@awaitEachGesture
                    if (mode == 1 && !engine.domeBegin(x - domeL, y - domeT)) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    if (mode == 2) engine.pillDown()
                    if (mode == 3) engine.titleBegin()
                    tick.intValue++
                    var finished = false
                    var active3 = false
                    var failed3 = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val px = change.position.x / dens.toDouble()
                            val py = change.position.y / dens.toDouble()
                            val tx = (change.position.x - down.position.x) / dens.toDouble()
                            val ty = (change.position.y - down.position.y) / dens.toDouble()
                            when (mode) {
                                1 -> {
                                    engine.domeMove(px - domeL, py - domeT, tx, ty)
                                    if (!change.pressed) {
                                        engine.domeEnd(tx, ty, true)
                                        finished = true
                                    }
                                }
                                2 -> if (!change.pressed) {
                                    val inside = px >= pillL && px <= pillL + pillW && py >= pillT && py <= pillT + pillH
                                    engine.pillUp(inside && change.uptimeMillis - down.uptimeMillis <= 600)
                                    finished = true
                                }
                                3 -> {
                                    if (!failed3 && !active3 && abs(ty) > 18) failed3 = true
                                    if (!failed3 && !active3 && abs(tx) > 8) active3 = true
                                    if (active3) engine.titleMove(tx)
                                    if (!change.pressed) {
                                        engine.titleEnd(if (active3) tracker.calculateVelocity().x / dens.toDouble() else 0.0)
                                        finished = true
                                    }
                                }
                            }
                            if (finished) break
                            tick.intValue++
                        }
                    } finally {
                        if (!finished) {
                            when (mode) {
                                1 -> engine.domeEnd(0.0, 0.0, false)
                                2 -> engine.pillUp(false)
                                3 -> engine.titleEnd(0.0)
                            }
                        }
                        tick.intValue++
                    }
                }
            },
        ) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, this.size.width / scale, this.size.height / scale, scale, layout, engine, params)
            c.restore()
        }

        // static nodes over the drawing, so the controls are focusable and labelled
        fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
        val durationSec = M.TRACK.toInt()
        fun words(sec: Int): String {
            val m = sec / 60
            val r = sec % 60
            return "$m ${if (m == 1) "minute" else "minutes"} $r ${if (r == 1) "second" else "seconds"}"
        }
        Box(
            spot(domeL, domeT, 2 * half, 2 * half).semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                stateDescription = "${words(a11yPos)} of ${words(durationSec)}"
                progressBarRangeInfo = ProgressBarRangeInfo(a11yPos.toFloat(), 0f..durationSec.toFloat())
                setProgress { value ->
                    engine.seekBy(value.toDouble() - a11yPos)
                    tick.intValue++
                    true
                }
            },
        )
        Box(
            spot(layout.cx - 28 * s, layout.cy - 28 * s, 56 * s, 56 * s).semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (a11yPlaying) Theme.PAUSE else Theme.PLAY
                onClick { engine.togglePlay(); tick.intValue++; true }
            },
        )
        for (d in intArrayOf(-1, 1)) {
            Box(
                spot(layout.cx + d * Theme.DISC_DX * s - 22 * s, layout.cy - 22 * s, 44 * s, 44 * s).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (d < 0) Theme.PREVIOUS else Theme.NEXT
                    onClick { engine.a11yTrack(d); tick.intValue++; true }
                },
            )
        }
        Box(
            spot(titleL, titleT, titleW, titleH).semantics(mergeDescendants = true) {
                heading()
                contentDescription = if (a11yPlaying) tracks.getOrElse(a11yIndex) { tracks[0] }.title else Theme.HEADLINE
            },
        )
        Box(
            spot(pillL, pillT, pillW, pillH).semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (a11yPlaying) Theme.PILL_PLAYING else Theme.PILL_TEXT
                onClick { if (a11yPlaying) cbOpen?.invoke(); true }
            },
        )
    }
}

/** The arc's lighter tail for a custom accent (RN `tailOf`). */
private fun tailOf(accent: Int): Int {
    if (accent == Theme.accent) return Theme.accentTail
    val c = Theme.rgbOf(accent)
    fun m(v: Double) = Math.round(min(1.0, v + (1 - v) * 0.32) * 255).toInt()
    return android.graphics.Color.rgb(m(c[0].toDouble()), m(c[1] * 0.92), m(c[2] * 0.8))
}
