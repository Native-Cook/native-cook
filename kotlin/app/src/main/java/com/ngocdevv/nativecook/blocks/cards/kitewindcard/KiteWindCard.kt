package com.ngocdevv.nativecook.blocks.cards.kitewindcard

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/** Wind forecast. The draw lambda alone reads the per-frame clock. */
@Composable
fun KiteWindCard(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val art = remember(context) { KiteWindCardArt(context) }
    val touch = remember { KiteWindMotion.Touch() }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var revision by remember { mutableIntStateOf(0) }
    var hourIndex by remember { mutableIntStateOf(3) }
    fun tick() {
        val supported = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        if (!view.performHapticFeedback(supported)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun updateHour() {
        val h = KiteWindMotion.rounded(touch.frame(clock, reduced, autoPlay).p)
        if (h != hourIndex) { if (touch.dragging) tick(); hourIndex = h }
    }
    LaunchedEffect(lifecycle, reduced, autoPlay) {
        if (reduced) { revision++; updateHour(); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val base = clock
            var start: Long? = null
            while (true) withFrameNanos { nanos ->
                if (start == null) start = nanos
                clock = base+(nanos-start)/1e9
                touch.step(clock, autoPlay, reduced); updateHour()
            }
        }
    }
    fun adjust(to: Double): Boolean {
        touch.takeOver(clock, reduced, autoPlay)
        val previous = KiteWindMotion.rounded(touch.p(clock))
        val target = KiteWindMotion.rounded(to).coerceIn(0, 9)
        touch.go(target.toDouble(), clock, reduced)
        if (previous != target) tick()
        if (reduced) hourIndex = target
        revision++
        return true
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ox = (maxWidth.value-393)/2
        val fy = maxHeight.value/852
        Canvas(Modifier.fillMaxSize().pointerInput(touch, reduced, autoPlay, density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x/density.toDouble(); val y = down.position.y/density.toDouble()
                touch.takeOver(clock, reduced, autoPlay)
                val pose = art.pose(size.width/density.toDouble(), size.height/density.toDouble(), touch.frame(clock, reduced, autoPlay))
                val top = 694*size.height/density/852; val offset = (size.width/density-393)/2
                val zone = if (x >= offset+20 && x <= offset+373 && y >= top && y <= top+128) 1 else if (!reduced && hypot(x-pose.first, y-pose.second) <= 80) 2 else 0
                if (zone == 1) { touch.p0 = touch.p(clock); touch.dx = 0.0; touch.dragging = true }
                if (zone == 2) { val k = touch.pull(clock); touch.pulling = true; touch.kx = k.first; touch.ky = k.second }
                down.consume(); revision++
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dx = change.position.x/density-x; val dy = change.position.y/density-y
                            touch.last = clock
                            if (zone == 1) { touch.dx = dx; updateHour() }
                            if (zone == 2) {
                                val d = hypot(dx, dy); val k = if (d > 0) 30*(1-exp(-d/30))/d else 0.0
                                touch.kx = dx*k; touch.ky = dy*k
                            }
                            revision++
                        }
                        change.consume()
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (zone == 1) touch.go(KiteWindMotion.rounded(touch.p(clock)).toDouble(), clock, reduced)
                    if (zone == 2) { touch.pulling = false; touch.kFromX = touch.kx; touch.kFromY = touch.ky; touch.kAt = clock; touch.last = clock }
                    updateHour(); revision++
                }
            }
        }) {
            revision
            val c = drawContext.canvas.nativeCanvas
            c.save(); c.scale(density, density)
            art.draw(c, size.width/density.toDouble(), size.height/density.toDouble(), touch.frame(clock, reduced, autoPlay))
            c.restore()
        }
        val wind = KiteWindMotion.wind(hourIndex.toDouble())
        Box(Modifier.offset((ox+26).dp, (70*fy).dp).size(300.dp, (220*fy).dp).semantics {
            heading(); contentDescription = "Harbour Park, north-east wind, ${KiteWindMotion.rounded(wind)} kilometres per hour"
        })
        Box(Modifier.offset((ox+20).dp, (694*fy).dp).size(353.dp, 128.dp).semantics {
            contentDescription = "Wind"
            stateDescription = "${KiteWindMotion.hour(hourIndex.toDouble())}, ${KiteWindMotion.rounded(wind)} kilometres per hour, gusts ${KiteWindMotion.rounded(wind*1.4)}"
            progressBarRangeInfo = ProgressBarRangeInfo(hourIndex.toFloat(), 0f..9f, 8)
            setProgress { adjust(it.toDouble()) }
            customActions = listOf(
                CustomAccessibilityAction("Increment") { adjust(KiteWindMotion.rounded(touch.frame(clock, reduced, autoPlay).p)+1.0) },
                CustomAccessibilityAction("Decrement") { adjust(KiteWindMotion.rounded(touch.frame(clock, reduced, autoPlay).p)-1.0) },
            )
        })
    }
}
