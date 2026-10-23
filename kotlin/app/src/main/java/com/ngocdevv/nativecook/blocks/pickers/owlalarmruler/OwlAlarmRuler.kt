package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import android.app.Activity
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.pickers.owlalarmruler.OwlAlarmRulerMotion as Motion
import com.ngocdevv.nativecook.blocks.pickers.owlalarmruler.OwlAlarmRulerTheme as Theme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OwlAlarmRuler(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resolver = context.contentResolver
    var still by remember(resolver) {
        mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                still = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
    val runtime = remember(view) {
        OwlAlarmRulerRuntime(autoPlay, still,
            selectionHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            successHaptic = {
                val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                view.performHapticFeedback(feedback)
            },
        )
    }
    val assets = remember(context) { OwlAlarmRulerAssets(context.applicationContext) }
    val backScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(runtime, autoPlay, still) { runtime.configure(autoPlay, still) }
    LaunchedEffect(runtime) { runtime.runClock() }
    DisposableEffect(lifecycle, runtime) {
        runtime.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runtime.foreground(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> runtime.foreground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(Theme.colors.page)).clipToBounds()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.safeDrawing.getTop(density) / density.density
        val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density
        val geo = OwlAlarmRulerGeo(width, height, top, bottom, density.density)
        Canvas(Modifier.fillMaxSize()) {
            val frame = runtime.frame.value
            drawOwlAlarmRuler(frame, Motion.poseOf(frame, still), geo, assets,
                label = Motion.timeLabel(runtime.labelTick.intValue.toDouble()),
                backScale = backScale.value,
                still = still,
            )
        }
        val back = Theme.layout.back
        val backSlop = ((Theme.space.hit - back.d) / 2.0 + 2.0).toFloat()
        Box(Modifier.offset((Theme.space.backInset.toFloat() - backSlop).dp, (top + Theme.space.headerTop.toFloat() - backSlop).dp)
            .size((back.d.toFloat() + backSlop * 2).dp)
            .semantics {
                role = Role.Button
                contentDescription = Theme.copy.a11yBack
                onClick { true }
            }.pointerInput(backScale) {
                detectTapGestures(onPress = {
                    scope.launch {
                        backScale.animateTo(Motion.PRESS.scale.toFloat(), tween((Motion.PRESS.`in` * 1000).roundToInt(), easing = Easing { Motion.easeOut(it.toDouble()).toFloat() }))
                    }
                    tryAwaitRelease()
                    scope.launch {
                        backScale.animateTo(1f, tween((Motion.PRESS.out * 1000).roundToInt(), easing = Easing { Motion.easeOut(it.toDouble()).toFloat() }))
                    }
                })
            })
        val track = Theme.layout.track
        Box(Modifier.offset(((width - track.w) / 2).toFloat().dp, (top + Theme.space.headerTop + back.d / 2 - track.h / 2).toFloat().dp)
            .size(track.w.toFloat().dp, track.h.toFloat().dp)
            .semantics {
                contentDescription = "Setup progress"
                progressBarRangeInfo = ProgressBarRangeInfo(track.fill.roundToInt().toFloat(), 0f..track.w.roundToInt().toFloat())
            })
        for (index in Theme.layout.headline.indices) {
            val baseline = Theme.layout.headline[index].base
            Box(Modifier.offset(0.dp, (top + baseline - Theme.ASCENT * Theme.type.headline.size).toFloat().dp)
                .size(width.dp, (Theme.type.headline.size * 1.2).toFloat().dp)
                .semantics {
                    contentDescription = if (index == 0) Theme.copy.line1 else Theme.copy.line2
                    if (index == 0) heading()
                })
        }
        val touch = Theme.layout.ruler.touch
        OwlAlarmRulerTouch(runtime, density.density,
            Modifier.offset(0.dp, (top + touch.top).toFloat().dp).size(width.dp, touch.h.toFloat().dp))
        Box(Modifier.offset(0.dp, (top + Theme.layout.caption.base - Theme.ASCENT * Theme.type.caption.size).toFloat().dp)
            .size(width.dp, (Theme.type.caption.size * 1.2).toFloat().dp)
            .semantics { contentDescription = Theme.copy.caption })
        val ctaTop = height - bottom - Theme.space.ctaBottom - Theme.layout.cta.h
        OwlAlarmRulerCta(runtime, Modifier.offset(Theme.space.screenInset.toFloat().dp, ctaTop.toFloat().dp)
            .size((width - 2 * Theme.space.screenInset).toFloat().dp, Theme.layout.cta.h.toFloat().dp))
    }
}

@Composable
private fun OwlAlarmRulerTouch(runtime: OwlAlarmRulerRuntime, density: Float, modifier: Modifier) {
    Box(modifier.semantics {
        contentDescription = Theme.copy.a11yRuler
        stateDescription = "${Motion.timeLabel(runtime.labelTick.intValue.toDouble())}. ${Theme.copy.a11yRulerHint}"
        val current = Motion.mod(
            Theme.REST_MINUTES / Theme.STEP_MINUTES + runtime.labelTick.intValue,
            Theme.TICKS_PER_DAY,
        ).toFloat()
        progressBarRangeInfo = ProgressBarRangeInfo(
            current,
            0f..(Theme.TICKS_PER_DAY - 1).toFloat(),
            (Theme.TICKS_PER_DAY - 2).toInt(),
        )
        setProgress { target ->
            val direction = when {
                target > current -> 1.0
                target < current -> -1.0
                else -> 0.0
            }
            if (direction != 0.0) runtime.adjust(direction)
            direction != 0.0
        }
        customActions = listOf(
            CustomAccessibilityAction("Increment") {
                runtime.adjust(1.0)
                true
            },
            CustomAccessibilityAction("Decrement") {
                runtime.adjust(-1.0)
                true
            },
        )
    }.pointerInput(runtime, density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            runtime.rulerDown()
            var activated = false
            var ended = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = (change.position - down.position) / density
                    if (!activated) {
                        if (abs(delta.y) > 14f) break
                        if (abs(delta.x) > 6f) {
                            activated = true
                            runtime.dragStart()
                        }
                    }
                    if (activated && change.pressed) {
                        runtime.dragMove(delta.x.toDouble(), (tracker.calculateVelocity().x / density).toDouble())
                        change.consume()
                    }
                    if (!change.pressed) {
                        runtime.release(if (activated) (tracker.calculateVelocity().x / density).toDouble() else 0.0)
                        ended = true
                        break
                    }
                }
            } finally {
                if (!ended) runtime.release(0.0)
            }
        }
    })
}

@Composable
private fun OwlAlarmRulerCta(runtime: OwlAlarmRulerRuntime, modifier: Modifier) {
    Box(modifier.graphicsLayer {
        val scale = (1.0 - (1.0 - Motion.PRESS.scale) * runtime.frame.value.press).toFloat()
        scaleX = scale
        scaleY = scale
    }.semantics {
        role = Role.Button
        contentDescription = "${Theme.copy.cta} ${Motion.timeLabel(runtime.labelTick.intValue.toDouble())}"
        onClick {
            runtime.pressIn()
            runtime.pressOut(true)
            true
        }
    }.pointerInput(runtime) {
        detectTapGestures(onPress = {
            runtime.pressIn()
            runtime.pressOut(tryAwaitRelease())
        })
    })
}
