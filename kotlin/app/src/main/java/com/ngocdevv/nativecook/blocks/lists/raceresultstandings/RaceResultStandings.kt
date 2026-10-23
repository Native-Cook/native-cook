package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.lists.raceresultstandings.RaceResultStandingsMotion as Motion
import com.ngocdevv.nativecook.blocks.lists.raceresultstandings.RaceResultStandingsTheme as Theme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

/** A pixel-scaled leaderboard with one shared animation clock and independent semantic nodes. */
@Composable
fun RaceResultStandings(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val renderer = remember(context) { RaceResultStandingsRenderer(context) }
    val controller = remember { RaceResultStandingsController(autoPlay) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val revision = remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(0) }
    // Each row owns its semantic rank. These states change only at an overtake or final flag,
    // never on the per-frame drawing clock.
    val replayPlaces = remember { List(Theme.drivers.size) { mutableIntStateOf(-1) } }
    val replayRowScratch = remember { DoubleArray(Theme.drivers.size) }
    val replayPlaceScratch = remember { IntArray(Theme.drivers.size) }
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var reader by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    val quiet = reader || reduced
    val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat()

    fun syncReplayPlaces() {
        Motion.replayPlaces(controller.frame.rp, replayRowScratch, replayPlaceScratch)
        for (index in Theme.drivers.indices) {
            val place = replayPlaceScratch[index]
            if (replayPlaces[index].intValue != place) {
                replayPlaces[index].intValue = place
            }
        }
    }

    fun publish() {
        syncReplayPlaces()
        shown = controller.shown
        revision.intValue++
        wake.trySend(Unit)
    }

    fun updateNow() {
        controller.tick(System.nanoTime() / 1e9)
    }

    DisposableEffect(context, accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { reader = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose {
            accessibility.removeTouchExplorationStateChangeListener(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = bars?.isAppearanceLightStatusBars
        val oldNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (oldStatus != null) bars.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) bars.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    DisposableEffect(lifecycle, controller) {
        controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ ->
            controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            wake.trySend(Unit)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoPlay, quiet) {
        controller.configure(autoPlay, quiet)
        publish()
    }
    LaunchedEffect(controller) {
        while (isActive) {
            val previousFrame = controller.frame
            val previousBack = controller.backPress
            val previousTrack = controller.trackPress
            val previousStretch = controller.stretch
            val previousFade = controller.fade
            if (controller.needsFrames) {
                withFrameNanos { stamp -> controller.tick(stamp / 1e9) }
            } else {
                val timeout = controller.nextWakeMillis()
                if (timeout == null) wake.receive() else withTimeoutOrNull(timeout) { wake.receive() }
                updateNow()
            }
            if (controller.consumeLightsOutHaptic()) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            // Only semantic rank changes cross composition. The frame counter stays in draw/layer.
            syncReplayPlaces()
            shown = controller.shown
            if (controller.frame != previousFrame || controller.backPress != previousBack || controller.trackPress != previousTrack ||
                controller.stretch != previousStretch || controller.fade != previousFade
            ) {
                revision.intValue++
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(Theme.colors.base))) {
        val widthPx = with(density) { maxWidth.toPx() }
        val widthDp = maxWidth.value.toDouble()
        val scale = widthPx / Theme.REF_W
        val origin = safeTop - Theme.REF_TOP * scale
        val titleTop = Theme.layout.title.baseline - Theme.ASCENT * Theme.layout.title.size - 8
        val titleLeft = Theme.layout.title.x - 12
        val titleRight = Theme.layout.sort.x + Theme.layout.sort.w + 12
        val titleHeight = max(Theme.space.hit, 1.219 * Theme.layout.title.size + 16)

        fun haptic() {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(controller, widthPx, safeTop, quiet, density.density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateNow()
                    val referenceX = down.position.x / scale
                    val referenceY = (down.position.y - origin) / scale
                    val target = when {
                        referenceX in titleLeft..titleRight && referenceY in titleTop..(titleTop + titleHeight) -> 1
                        abs(referenceX - Theme.layout.back.cx) <= Theme.layout.back.d / 2 &&
                            abs(referenceY - Theme.layout.back.cy) <= Theme.layout.back.d / 2 -> 2
                        !quiet && referenceX in Theme.layout.track.x..(Theme.layout.track.x + Theme.layout.track.w) &&
                            referenceY in Theme.layout.track.y..(Theme.layout.track.y + Theme.layout.track.h) -> 3
                        else -> 0
                    }
                    controller.touchDown(target)
                    publish()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragging = false
                    var failed = false
                    var released = false
                    var elapsedMillis = 0L
                    try {
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            elapsedMillis = change.uptimeMillis - down.uptimeMillis
                            val dx = (change.position.x - down.position.x) / density.density
                            val dy = (change.position.y - down.position.y) / density.density
                            updateNow()
                            if (!dragging && !failed && !quiet) {
                                if (change.isConsumed || dx > Motion.DRAG.failOffset || abs(dy) > Motion.DRAG.failOffset) failed = true
                                else if (dx < -Motion.DRAG.activeOffset) {
                                    dragging = controller.beginDrag()
                                }
                            }
                            if (dragging) {
                                controller.drag(dx.toDouble(), widthDp)
                                change.consume()
                            }
                            if (!change.pressed) {
                                released = true
                                if (dragging) {
                                    if (controller.endDrag(tracker.calculateVelocity().x / density.density.toDouble(), false)) haptic()
                                }
                                val maxDuration = if (target == 1) 800 else 500
                                val tap = !dragging && !failed && abs(dx) <= 10 && abs(dy) <= 10 && elapsedMillis <= maxDuration
                                if (controller.touchUp(target, tap)) haptic()
                                if (target != 0 && tap) change.consume()
                            }
                            publish()
                        }
                    } finally {
                        if (!released) {
                            if (dragging) controller.endDrag(0.0, true)
                            controller.touchUp(target, false)
                            publish()
                        }
                    }
                }
            },
        ) {
            if (revision.intValue >= 0) drawIntoCanvas {
                renderer.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    safeTop,
                    controller.frame,
                    controller.backPress,
                    controller.trackPress,
                    controller.stretch,
                    controller.fade,
                )
            }
        }

        fun bounds(x: Double, y: Double, width: Double, height: Double): Modifier {
            return Modifier
                .offset((x * scale / density.density).dp, ((origin + y * scale) / density.density).dp)
                .size((width * scale / density.density).dp, (height * scale / density.density).dp)
        }

        Box(bounds(titleLeft, titleTop, titleRight - titleLeft, titleHeight).semantics {
            role = Role.Button
            contentDescription = "${Theme.sessions[shown].word} results"
            onClick("Switches to ${Theme.sessions[1 - shown].word} results") {
                updateNow()
                controller.accessibleSwitch()
                publish()
                true
            }
        })
        Box(bounds(Theme.layout.back.cx - Theme.layout.back.d / 2, Theme.layout.back.cy - Theme.layout.back.d / 2, Theme.layout.back.d, Theme.layout.back.d).semantics {
            role = Role.Button
            contentDescription = "Back"
        })
        Box(bounds(20.0, 130.0, 260.0, 55.0).semantics {
            contentDescription = "${Theme.copy.round}. ${Theme.copy.event}"
        })
        for (slot in 0..2) {
            val driver = Theme.drivers[Theme.sessions[shown].order[slot]]
            Box(bounds(Theme.layout.podium.markRight[slot] - Theme.layout.podium.markW, 199.0, 65.0, 20.0).semantics {
                contentDescription = driver.code
            })
        }
        Box(bounds(Theme.layout.stats.lap.x, 243.0, 325.0, 36.0).semantics {
            contentDescription = "${Theme.sessions[shown].fastestLap}. ${Theme.copy.lapLabel}"
        })
        Box(bounds(Theme.layout.stats.day.x, 291.0, 145.0, 36.0).semantics {
            contentDescription = "${Theme.copy.dayValue}. ${Theme.copy.dayLabel}"
        })
        Box(bounds(Theme.layout.stats.pit.x, 291.0, 160.0, 36.0).semantics {
            contentDescription = "${Theme.copy.pitValue}. ${Theme.copy.pitLabel}"
        })
        if (!quiet) {
            Box(
                bounds(Theme.layout.track.x, Theme.layout.track.y, Theme.layout.track.w, Theme.layout.track.h)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Replay the race"
                        onClick {
                            updateNow()
                            controller.accessibleReplay()
                            publish()
                            true
                        }
                    },
            )
        }
        // Compose the semantic tree in the shown session's reading order, just like RN mounts rows.
        for (index in Theme.sessions[shown].order) {
            val anchor = Theme.layout.rows.firstRule - Theme.space.rowPitch
            RaceResultStandingsSemanticRow(
                modifier = bounds(0.0, anchor, Theme.REF_W, Theme.space.rowPitch),
                index = index,
                shown = shown,
                replayPlace = replayPlaces[index],
                controller = controller,
                revision = revision,
                scale = scale,
            )
        }
    }
}

/** Keeps a replay-rank state change local to the one TalkBack row whose label changed. */
@Composable
private fun RaceResultStandingsSemanticRow(
    modifier: Modifier,
    index: Int,
    shown: Int,
    replayPlace: MutableIntState,
    controller: RaceResultStandingsController,
    revision: MutableIntState,
    scale: Double,
) {
    val driver = Theme.drivers[index]
    val team = Theme.teams.getValue(driver.team)
    val racing = replayPlace.intValue
    val slot = if (racing < 0) Theme.SLOT_OF[shown][index] else racing % Motion.REPLAY_FLAGGED
    val flagged = racing < 0 || racing >= Motion.REPLAY_FLAGGED
    val label = "${slot + 1}. ${driver.name}, ${team.name}" +
        if (flagged) ", ${Theme.sessions[shown].points[slot]} points" else ", racing"

    Box(
        modifier
            .graphicsLayer {
                revision.intValue
                val frame = controller.frame
                val row = if (frame.rp > 0.0) {
                    Motion.replayRow(index, frame.rp).y
                } else {
                    val fromSlot = Theme.SLOT_OF[frame.from][index]
                    val toSlot = Theme.SLOT_OF[1 - frame.from][index]
                    val progress = Motion.rowProgress(frame.u, toSlot)
                    fromSlot + (toSlot - fromSlot) * progress
                }
                translationY = (row * Theme.space.rowPitch * scale).toFloat()
            }
            .semantics {
                contentDescription = label
            },
    )
}
