package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun SmokedClocheReveal(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun systemReduced() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(context) { mutableStateOf(systemReduced()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = systemReduced() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val art = remember(context) { SmokedClocheRevealArt(context) }
    var count by remember { mutableIntStateOf(0) }
    val driver = remember(autoPlay, reduced) { SmokedClocheRevealDriver(autoPlay, reduced, count) }
    val tick = remember(driver) { mutableLongStateOf(0L) }
    val wake = remember(driver) { Channel<Unit>(Channel.CONFLATED) }
    val presses = remember { List(4) { Animatable(1f) } }
    val pressJobs = remember { arrayOfNulls<kotlinx.coroutines.Job>(4) }
    val scope = rememberCoroutineScope()
    val pressCurve = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    var off by remember(driver) { mutableStateOf(false) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }

    fun changed() {
        if (driver.haptic >= 0) {
            val kind = driver.haptic
            driver.haptic = -1
            if (Build.VERSION.SDK_INT >= 29) {
                vibrator?.vibrate(VibrationEffect.createPredefined(when (kind) {
                    0 -> VibrationEffect.EFFECT_TICK
                    2 -> VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }))
            } else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 2) 18 else 12, if (kind == 2) 80 else 45))
        }
        off = driver.frame.lift > 0.98
        count = driver.count
        tick.longValue++
        wake.trySend(Unit)
    }

    fun press(index: Int, down: Boolean) {
        pressJobs[index]?.cancel()
        pressJobs[index] = scope.launch {
            presses[index].animateTo(if (!down) 1f else if (reduced) 0.72f else if (index == 3) 0.97f else 0.96f, tween(150, easing = pressCurve))
        }
    }

    DisposableEffect(art) { onDispose { art.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    val top = WindowInsets.statusBars.getTop(density) / density.density
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = remember(maxWidth, maxHeight, top, bottom) { SmokedClocheRevealLayout(maxWidth.value, maxHeight.value, top, bottom) }
        LaunchedEffect(driver, lifecycle, l) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                reduced = systemReduced()
                var previous = 0L
                while (true) {
                    if (!driver.needsFrames()) { wake.receive(); previous = 0L }
                    withFrameNanos { nanos ->
                        val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                        previous = nanos
                        driver.step(dt, l)
                        art.advance(l, driver.frame, reduced)
                        // Draw invalidation only. Semantics update at the two meaningful boundaries.
                        tick.longValue++
                        val nextOff = driver.frame.lift > 0.98
                        if (off != nextOff) off = nextOff
                        if (count != driver.count) count = driver.count
                        if (driver.haptic >= 0) changed()
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize().pointerInput(driver, l, reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val x = start.x / density.density
                val y = start.y / density.density
                val target = when {
                    l.inCta(x, y) -> 3
                    l.inIcon(x, y, 200f * l.sx, l.y(808f), 23f) -> 2
                    l.inIcon(x, y, 37f * l.sx, l.y(63f)) -> 0
                    l.inIcon(x, y, 364f * l.sx, l.y(63f)) -> 1
                    l.inDome(x, y) -> 4
                    else -> -1
                }
                if (target < 0) return@awaitEachGesture
                if (target == 4) driver.begin(x.toDouble(), y.toDouble()) else press(target, true)
                changed()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, start)
                var pan = false
                var maxDistance = 0f
                var end = start
                var released = false
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position
                        tracker.addPosition(change.uptimeMillis, end)
                        val distance = (end - start).getDistance() / density.density
                        maxDistance = maxOf(maxDistance, distance)
                        if (!pan && target == 4 && distance >= 4) { pan = true; driver.startPan() }
                        if (target == 4 && pan && change.pressed) {
                            driver.drag((end.x / density.density).toDouble(), (end.y / density.density).toDouble(), ((end.y - start.y) / density.density).toDouble())
                            changed()
                        }
                        change.consume()
                        if (!change.pressed) released = true
                    } while (change.pressed && event.changes.any { it.pressed })
                } finally {
                    if (target == 4) {
                        if (released) driver.end(pan, maxDistance <= 8, (tracker.calculateVelocity().y / density.density).toDouble())
                        else driver.finishTouch()
                    } else {
                        press(target, false)
                        // PressScale's 16 pt retention region, including a small release outside it.
                        if (released && target == 3 && end.x / density.density >= 236f * l.sx - 16 && end.y / density.density >= l.y(772f) - 16 && end.y / density.density <= l.y(772f) + 70f * l.k + 16) driver.addToCart()
                    }
                    changed()
                }
            }
        }) {
            tick.longValue
            drawIntoCanvas { art.draw(it.nativeCanvas, l, driver.frame, density.density, FloatArray(4) { i -> presses[i].value }, driver.count, reduced) }
        }

        Box(Modifier.offset((37f * l.sx - 22).dp, (l.y(63f) - 22).dp).size(44.dp).semantics {
            role = Role.Button; contentDescription = "Back"; onClick { true }
        })
        Box(Modifier.offset((364f * l.sx - 22).dp, (l.y(63f) - 22).dp).size(44.dp).semantics {
            role = Role.Button; contentDescription = "More"; onClick { true }
        })
        Box(Modifier.offset((l.cx - l.radius).dp, (l.cy - l.radius).dp).size((l.radius * 2).dp).semantics {
            role = Role.Button
            contentDescription = if (off) "Eggs Curry. Tap to cover it again" else "Cloche. Draw it up to uncover the dish"
            onClick { driver.activate(); changed(); true }
        })
        Box(Modifier.offset((33f * l.sx).dp, (l.y(636f) - 20).dp).width((225f * l.sx).dp).height(36.dp).semantics {
            heading(); contentDescription = "Eggs Curry"
        })
        Box(Modifier.offset((270f * l.sx).dp, (l.y(637f) - 12).dp).width((90f * l.sx).dp).height(24.dp).semantics { contentDescription = "25 Mins" })
        Box(Modifier.offset((33f * l.sx).dp, (l.y(682f) - 11).dp).width((330f * l.sx).dp).height(90.dp).semantics {
            contentDescription = "Eggs Curry with tomato and cucumbers, our chefs’ healthy, low-fat dish, smoked over applewood before it reaches you."
        })
        Box(Modifier.offset((33f * l.sx).dp, (l.y(780f) - 9).dp).width(120.dp).height(48.dp).semantics { contentDescription = "Total Price, $ 15.00" })
        Box(Modifier.offset((200f * l.sx - 23).dp, (l.y(808f) - 23).dp).size(46.dp).semantics {
            role = Role.Button; contentDescription = "Save"; onClick { true }
        })
        Box(Modifier.offset((236f * l.sx).dp, l.y(772f).dp).width((190f * l.sx).dp).height((70f * l.k).dp).semantics {
            role = Role.Button
            contentDescription = if (count > 0) "Add to cart, $count in the cart" else "Add to cart"
            onClick { driver.addToCart(); changed(); true }
        })
    }
}
