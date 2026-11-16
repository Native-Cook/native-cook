package com.ngocdevv.nativecook.blocks.progress.pourjargoal

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalMotion as M
import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

/**
 * Pour Jar Goal: a savings goal that is a jar of liquid. Drag the surface to add money (a 64-column wave solver at 120 Hz, one finger
 * or two), tap "Pour in" for $100, hold it to keep pouring; the amount rolls digit by digit, the header title morphs into an Undo toast.
 *
 * LOOP = 8 s ([PourJarGoalMotion.LOOP]): the jar lifts from 72 % to 75 %, $2,240 rolls to $2,340, the toast morphs in and out, then the
 * jar drains back to rest. A touch interrupts it; two seconds after the last touch it eases back and restarts.
 */
@Composable
fun PourJarGoal(
    autoPlay: Boolean = false,
    defaultValue: Double = 2240.0,
    max: Double = 3100.0,
    step: Double = 10.0,
    onBack: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
    haptics: Boolean = true,
    sound: Boolean = true,
    previewTime: Double? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { PourJarGoalHaptics.create(context) }
    val audio = remember(context, sound) { if (sound) PourJarGoalAudio(context) else null }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var undoAvailable by remember { mutableStateOf(false) }
    var accessible by remember { mutableStateOf(defaultValue) }
    var autoOn by remember { mutableStateOf(false) }
    BlockSystemBars(darkIcons = true)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!active) audio?.cue("stop", 0.0, 0.0)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val fontScale = densityOwner.fontScale.toDouble()
    val renderer = remember(context, fontScale) { PourJarGoalRenderer(context.assets, fontScale) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.PAGE))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val l = remember(w, h, insetTop, insetBottom, fontScale) { Theme.makeLayout(w, h, insetTop, insetBottom, fontScale) }
        val engine = remember(l, autoPlay, reduced, defaultValue, max, step) {
            val vessel = PourJarGoalVessel(Theme.VESSEL_W, Theme.VESSEL_H, Theme.VESSEL_TOP_R, Theme.VESSEL_BOTTOM_R)
            PourJarGoalEngine(autoPlay, reduced, vessel, l.jarW / vessel.width, l.jarH / vessel.height, 0.0, max, step, defaultValue)
        }
        SideEffect {
            engine.cue = { kind -> if (haptics) vibe.emit(kind) }
            engine.sound = { name, level, gain -> audio?.cue(name, level, gain) }
            engine.autoEnabled = autoOn
        }
        val dens = densityOwner.density
        val s = l.s

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            engine.resume()
            var previous = 0L
            var lastAccessible = Double.NaN
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(nanos / 1e9, dt)
                    if (engine.hasUndo != undoAvailable) undoAvailable = engine.hasUndo
                    if (engine.accessibleValue != lastAccessible) {
                        lastAccessible = engine.accessibleValue
                        accessible = engine.accessibleValue
                    }
                    tick.intValue++
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, l, engine, reduced)
            c.restore()
        }

        val headCY = l.headerY + 22 * s
        val undoRect = if (l.largeType) {
            doubleArrayOf(l.jarX, l.jarY + 17 * s, l.jarW, max(48.0, 14 * s * fontScale * 3.5 + 20 * s))
        } else doubleArrayOf(l.x(63.0), headCY - 24, l.x(339.0) - l.x(63.0), 48.0)
        val hitH = max(48.0, l.buttonH)

        fun inside(r: DoubleArray, x: Double, y: Double) = x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]
        val backRect = doubleArrayOf(l.x(19.0), l.headerY, 44.0, 44.0)
        val optionsRect = doubleArrayOf(l.x(339.0), l.headerY, 44.0, 44.0)
        val jarRect = doubleArrayOf(l.jarX, l.jarY, l.jarW, l.jarH)
        val pourRect = doubleArrayOf(l.x(22.0), l.buttonY, 234 * s, hitH)
        val autoRect = doubleArrayOf(l.x(266.0), l.buttonY, 114 * s, hitH)

        Box(
            Modifier.fillMaxSize().pointerInput(engine, l, dens, onBack, onOptions) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    when {
                        engine.hasUndo && inside(undoRect, x0, y0) -> {
                            down.consume()
                            if (tapEnded(down.id, undoRect, dens)) engine.undo()
                        }
                        inside(backRect, x0, y0) -> {
                            down.consume()
                            if (tapEnded(down.id, backRect, dens)) onBack?.invoke()
                        }
                        inside(optionsRect, x0, y0) -> {
                            down.consume()
                            if (tapEnded(down.id, optionsRect, dens)) onOptions?.invoke()
                        }
                        inside(autoRect, x0, y0) -> {
                            down.consume()
                            if (tapEnded(down.id, autoRect, dens)) {
                                autoOn = !autoOn
                                engine.autoEnabled = autoOn
                            }
                        }
                        inside(pourRect, x0, y0) -> {
                            down.consume()
                            engine.pourBegin()
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        finished = true
                                        engine.pourEnd()
                                        break
                                    }
                                }
                            } finally {
                                if (!finished) engine.pourCancel()
                            }
                        }
                        inside(jarRect, x0, y0) -> {
                            down.consume()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            engine.dragBegin(x0 - l.jarX, y0 - l.jarY)
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val first = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(first.uptimeMillis, first.position)
                                    if (!first.pressed) {
                                        first.consume()
                                        finished = true
                                        engine.dragEnd((tracker.calculateVelocity().y / dens).toDouble())
                                        break
                                    }
                                    val second: PointerInputChange? = event.changes.firstOrNull { it.id != down.id && it.pressed }
                                    engine.dragMove(
                                        (first.position.x / dens) - l.jarX, (first.position.y / dens) - l.jarY,
                                        second?.let { (it.position.x / dens) - l.jarX }, second?.let { (it.position.y / dens) - l.jarY },
                                        l.jarW,
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            } finally {
                                if (!finished) engine.dragCancel()
                            }
                        }
                    }
                }
            },
        )

        // accessibility: the jar as one adjustable control, and the buttons
        Box(
            Modifier
                .offset(l.jarX.dp, l.jarY.dp)
                .size(l.jarW.dp, l.jarH.dp)
                .semantics {
                    contentDescription = "${Theme.TITLE}. ${M.money(accessible)} of ${M.money(max)}. ${M.percentage(accessible, 0.0, max)} percent. ${Theme.ADJUSTABLE}"
                    stateDescription = M.money(accessible)
                    customActions = listOf(
                        CustomAccessibilityAction("Increase") { engine.adjust(1, false); true },
                        CustomAccessibilityAction("Decrease") { engine.adjust(-1, false); true },
                        CustomAccessibilityAction("Pour in") { engine.adjust(10, true); true },
                    )
                },
        )
        val buttons = listOf(
            Triple(Theme.BACK, backRect, { onBack?.invoke(); Unit }),
            Triple(Theme.MORE, optionsRect, { onOptions?.invoke(); Unit }),
            Triple("Pour in", pourRect, { engine.adjust(10, true) }),
            Triple("Automatic weekly saving", autoRect, { autoOn = !autoOn; engine.autoEnabled = autoOn }),
        )
        for ((label, r, action) in buttons) {
            Box(
                Modifier
                    .offset(r[0].dp, r[1].dp)
                    .size(r[2].dp, r[3].dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        onClick { action(); true }
                    },
            )
        }
        if (undoAvailable) {
            Box(
                Modifier
                    .offset(undoRect[0].dp, undoRect[1].dp)
                    .size(undoRect[2].dp, undoRect[3].dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Undo last pour"
                        onClick { engine.undo(); true }
                    },
            )
        }
    }
}

/** Waits for the pointer to lift; true when it lifted inside [rect] (points). */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.tapEnded(id: androidx.compose.ui.input.pointer.PointerId, rect: DoubleArray, dens: Float): Boolean {
    var inside = true
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        val x = change.position.x / dens
        val y = change.position.y / dens
        inside = x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3]
        change.consume()
        if (!change.pressed) return inside
    }
}
