package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarMotion as M
import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarController as C
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Stamp Diary Calendar: peel a day's stamp off the calendar, stamp it into the collage, send it home. */
@Composable
fun StampDiaryCalendar(autoPlay: Boolean = false) {
    StampDiaryCalendarContent(autoPlay)
}

@Composable
private fun StampDiaryCalendarContent(autoPlay: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember { StampDiaryCalendarState() }
    val art = rememberStampDiaryCalendarArtwork()
    val painter = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) StampDiaryCalendarAgslPainter() else StampDiaryCalendarCanvasPainter()
    }
    val mainLayer = rememberGraphicsLayer()
    val reduceMotion = rememberReduceMotion(context)
    val screenReader = rememberScreenReader(context)
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)

    val currentAutoPlay by rememberUpdatedState(autoPlay)
    LaunchedEffect(autoPlay, reduceMotion, screenReader) {
        state.configure(currentAutoPlay, reduceMotion, screenReader)
    }
    state.controller.onActor = { state.actorMemory = it }
    state.controller.onEmptyDay = { state.emptyDay = it }
    state.controller.onHaptic = { performHaptic(view, it) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.controller.resumeClock(StampDiaryCalendarState.clock())
                state.wakeUp()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // The frame loop: one pose per frame, then sleep until a touch or a setting wakes it.
    LaunchedEffect(state) {
        while (true) {
            withFrameNanos { }
            val now = StampDiaryCalendarState.clock()
            state.pose = state.controller.tick(now)
            state.emptyAlpha = state.controller.emptyAlpha(now).toFloat()
            if (!state.controller.isRunning(now)) state.wake.receive()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StampDiaryCalendarColors.page)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val s = widthPx / StampDiaryCalendarTheme.REF_WIDTH
        val space = StampDiaryCalendarSpace(widthPx, heightPx, s, statusTop - StampDiaryCalendarTheme.REF_SAFE_TOP * s)
        val month = state.month
        val menuHaptic = { performHaptic(view, C.Haptic.SELECTION) }

        StampDiaryCalendarBackdrop(space, month)
        StampDiaryCalendarDays(space, month, onSelect = { day ->
            state.controller.select(day, month, StampDiaryCalendarState.clock())
            state.wakeUp()
        })
        TouchSurface(state, space)
        StampDiaryCalendarTitle(space, month, onMonth = { state.openMenu(menuHaptic) })
        StampDiaryCalendarReveal(state, space)
        StampDiaryCalendarMovingLayers(state, space, art, painter, mainLayer)
        StampDiaryCalendarHeader(
            space, mainLayer,
            onClose = {
                state.controller.sendHome(StampDiaryCalendarState.clock())
                state.wakeUp()
            },
            onMonth = { state.openMenu(menuHaptic) },
        )
        StampDiaryCalendarMenu(
            space, mainLayer, month, state.menuOpen, reduceMotion,
            onClose = { state.menuOpen = false },
            onPick = { state.go(it, menuHaptic) },
        )
    }
}

/** The calendar's pan: observes every pointer without consuming it, so nothing underneath fights it. */
@Composable
private fun TouchSurface(state: StampDiaryCalendarState, space: StampDiaryCalendarSpace) {
    val currentSpace by rememberUpdatedState(space)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val sp = currentSpace
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val origin = down.position
                    state.controller.touchBegan(origin.x / sp.s, (origin.y - sp.oyPx) / sp.s, StampDiaryCalendarState.clock())
                    state.wakeUp()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                            state.controller.touchMoved(
                                (change.position.x - origin.x) / sp.s, (change.position.y - origin.y) / sp.s,
                                StampDiaryCalendarState.clock(),
                            )
                            state.wakeUp()
                        }
                        if (!change.pressed) break
                    }
                    val velocityY = tracker.calculateVelocity().y / sp.s
                    state.controller.touchEnded(velocityY, StampDiaryCalendarState.clock())
                    state.wakeUp()
                }
            },
    )
}

private fun performHaptic(view: View, kind: C.Haptic) {
    val constant = when (kind) {
        C.Haptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
        C.Haptic.LIGHT -> HapticFeedbackConstants.VIRTUAL_KEY
        C.Haptic.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
    }
    view.performHapticFeedback(constant)
}

/** Reduce Motion on Android: the animator duration scale is zero. */
@Composable
private fun rememberReduceMotion(context: Context): Boolean {
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    LaunchedEffect(Unit) {
        while (true) {
            reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            kotlinx.coroutines.delay(1000)
        }
    }
    return reduced
}

@Composable
private fun rememberScreenReader(context: Context): Boolean {
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

@Preview
@Composable
private fun StampDiaryCalendarPreview() {
    StampDiaryCalendar(autoPlay = true)
}
