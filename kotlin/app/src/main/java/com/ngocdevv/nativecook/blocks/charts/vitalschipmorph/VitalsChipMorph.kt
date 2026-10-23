package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphTheme as Theme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Same width-based reference coordinate space as the accepted RN screen. */
@Composable
fun VitalsChipMorph(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    var screenReader by remember { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    val quiet = reduced || screenReader
    val controller = remember { VitalsChipMorphController() }
    val renderer = remember { VitalsChipMorphRenderer(context) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val revision = remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(0) }
    fun publish() {
        revision.intValue++
        if (current != controller.current) current = controller.current
        wake.trySend(Unit)
    }
    fun select(index: Int) {
        if (controller.selectChip(index)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        publish()
    }

    DisposableEffect(accessibility, context) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { screenReader = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose {
            accessibility?.removeTouchExplorationStateChangeListener(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    DisposableEffect(owner) {
        controller.foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, _ ->
            controller.foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            controller.resetClock()
            publish()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        var activityContext = context
        while (activityContext is android.content.ContextWrapper && activityContext !is android.app.Activity) {
            activityContext = activityContext.baseContext
        }
        val window = (activityContext as? android.app.Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = insets?.isAppearanceLightStatusBars
        insets?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) insets.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(autoPlay, quiet) {
        controller.configure(autoPlay, quiet)
        publish()
    }
    LaunchedEffect(controller) {
        while (isActive) {
            if (!controller.needsFrames()) {
                wake.receive()
                controller.resetClock()
            }
            withFrameNanos { timestamp ->
                controller.tick(timestamp / 1_000_000_000.0)
                revision.intValue++
                if (current != controller.current) current = controller.current
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Theme.colors.page)) {
        val refToDp = maxWidth.value / Theme.REF_W
        val refToPx = refToDp * density.density
        val safeTopPx = WindowInsets.safeDrawing.getTop(density)
        val offsetPx = safeTopPx - Theme.REF_INSET * refToPx
        Canvas(Modifier.fillMaxSize()) {
            revision.intValue
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(0f, offsetPx.toFloat())
                native.scale(refToPx.toFloat(), refToPx.toFloat())
                renderer.draw(native, controller, refToPx)
                native.restore()
            }
        }
        Theme.chips.forEachIndexed { index, chip ->
            Box(Modifier
                .offset {
                    revision.intValue
                    IntOffset(
                        ((chip.x + renderer.rowOffset(controller)) * refToPx - 4 * density.density).roundToInt(),
                        (safeTopPx - 4 * density.density).roundToInt(),
                    )
                }
                .size((chip.w * refToDp + 8).dp, (Theme.layout.chipH * refToDp + 8).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (chip.value.isEmpty()) "Summary" else "${chip.title}, ${chip.value}"
                    if (index !in Theme.SCREEN_CHIP) stateDescription = "Shows ${Theme.SCREEN_NAMES[Theme.CHIP_SCREEN[index]]}"
                    onClick {
                        select(index)
                        true
                    }
                }
                .pointerInput(index) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controller.chipPress(index, true)
                        publish()
                        val up = waitForUpOrCancellation()
                        controller.chipPress(index, false)
                        if (up != null) select(index) else publish()
                    }
                })
        }
        Box(Modifier
            .offset { IntOffset(0, (offsetPx + Theme.layout.heroTop * refToPx).roundToInt()) }
            .size(maxWidth, (Theme.layout.heroTouchH * refToDp).dp)
            .semantics {
                contentDescription = screenNames[current]
                stateDescription = screenValues[current]
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..2f, 1)
                setProgress {
                    select(Theme.SCREEN_CHIP[it.roundToInt().coerceIn(0, 2)])
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Increment metric") {
                        select(Theme.SCREEN_CHIP[(current + 1).coerceAtMost(2)])
                        true
                    },
                    CustomAccessibilityAction("Decrement metric") {
                        select(Theme.SCREEN_CHIP[(current - 1).coerceAtLeast(0)])
                        true
                    },
                )
            }
            .pointerInput(refToPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    controller.beginDrag()
                    publish()
                    var activated = false
                    var finished = false
                    var cancelled = false
                    try {
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) {
                                cancelled = true
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val displacement = change.position - down.position
                            if (!activated && abs(displacement.y) > 14 * density.density) {
                                cancelled = true
                                break
                            }
                            if (!activated && abs(displacement.x) > 10 * density.density) activated = true
                            if (activated) {
                                controller.drag(displacement.x / refToPx)
                                change.consume()
                                publish()
                            }
                            finished = !change.pressed
                        }
                    } finally {
                        val haptic = controller.endDrag(tracker.calculateVelocity().x / refToPx, cancelled || !activated)
                        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        publish()
                    }
                }
            })
        listOf("Insights", "Longevity", "Browse").forEachIndexed { index, title ->
            Box(Modifier
                .offset {
                    IntOffset(((Theme.layout.tabBar.centres[index] - Theme.layout.tabBar.pill.w / 2) * refToPx).roundToInt(),
                        (offsetPx + Theme.layout.tabBar.y * refToPx).roundToInt())
                }
                .size((Theme.layout.tabBar.pill.w * refToDp).dp, (Theme.layout.tabBar.h * refToDp).dp)
                .semantics {
                    role = Role.Tab
                    contentDescription = title
                    selected = index == 0
                })
        }
        Box(Modifier.size(1.dp).semantics { contentDescription = pageCopy[current] })
    }
}

private val screenNames = listOf("Readiness", "Fitness", "Heart")
private val screenValues = listOf("7 out of 10, ready", "375 of 500 calories, 19 of 30 exercise minutes, 4 of 12 stand hours", "62 beats per minute")
private val pageCopy = listOf(
    "Ready. You had a good night’s sleep last night and all your other metrics are typical. It looks like you’re rested and ready for today.",
    "Your Exercise Ring Looks Good. Your Exercise ring is ahead of where it usually is by now. Steps 5,253, Cardio Fitness 38 VO2 max.",
    "Cardio Fitness Trending Up. On average, your cardio fitness went up over the last month. Resting heart rate 55 BPM, recovery HRV 54 milliseconds.",
)
