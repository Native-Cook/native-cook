package com.ngocdevv.nativecook.blocks.charts.rainwindowforecast

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first

@Composable
fun RainWindowForecast(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun isReduced() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(isReduced()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = isReduced() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val art = remember(context) { RainWindowForecastArt(context) }
    val driver = remember(autoPlay, reduced) { RainWindowForecastDriver(autoPlay && !reduced) }
    val tick = remember(driver) { mutableDoubleStateOf(0.0) }
    val top = WindowInsets.statusBars.getTop(density) / density.density
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, lifecycle, reduced) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if (reduced && !driver.clearing) {
                    snapshotFlow { driver.clearing }.first { it }
                    previous = 0L
                }
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.0 else ((nanos - previous) / 1e9).coerceIn(0.0, 0.5)
                    previous = nanos
                    driver.step(dt, reduced)
                    tick.doubleValue = driver.now
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()
            .semantics {
                contentDescription = "Rainy window. Wipe to clear the fog. Aurenne, 14°, Light rain · stops in 25 min. Next hour: light rain now, stopping in 25 minutes."
            }
            .pointerInput(driver, density.density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    driver.begin((down.position.x / density.density).toDouble(), (down.position.y / density.density).toDouble())
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (p.pressed) driver.move((p.position.x / density.density).toDouble(), (p.position.y / density.density).toDouble())
                            p.consume()
                        } while (event.changes.any { it.pressed })
                    } finally { driver.end() }
                }
            }) {
            tick.doubleValue
            val w = size.width / density.density
            val h = size.height / density.density
            val f = driver.frame(w.toDouble(), h.toDouble())
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                art.draw(c, w, h, top, bottom, density.density, driver.rain, f)
                c.restore()
            }
        }
    }
}
