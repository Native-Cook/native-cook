package com.ngocdevv.nativecook.blocks.cards.cashbackcloudcard

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun CashbackCloudCard(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { CashbackCloudCardArt(context) }
    val driver = remember(autoPlay, reduced) { CashbackCloudCardDriver() }
    val frame = remember(driver) { mutableStateOf(CashbackCloudCardMotion.Frame()) }
    var awake by remember(driver) { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, lifecycle, awake) {
        if (!(autoPlay && !reduced) && !awake) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val wall = (if (previous == 0L) 0.016 else (nanos - previous) / 1e9).coerceIn(0.0, 0.5)
                previous = nanos
                frame.value = driver.step(wall, autoPlay && !reduced) { success ->
                    haptic.performHapticFeedback(if (success) HapticFeedbackType.Confirm else HapticFeedbackType.TextHandleMove)
                }
                if (!(autoPlay && !reduced) && !driver.moving()) awake = false
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                val cv = it.nativeCanvas
                cv.save(); cv.scale(density.density, density.density)
                art.draw(cv, size.width / density.density.toDouble(), size.height / density.density.toDouble(), top, frame.value, reduced)
                cv.restore()
            }
        }
        Box(Modifier.offset(x = 20.dp, y = (top + 68).dp).size(210.dp, 25.dp).semantics { contentDescription = "Main balance" })
        Box(Modifier.offset(x = 42.dp, y = (top + 426).dp).size(180.dp, 60.dp).semantics { contentDescription = "Cashback this week" })
        Box(Modifier.offset(x = 36.dp, y = (top + 140).dp).size(maxWidth - 72.dp, 230.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Cashback cloud. Press and hold to collect your cashback"

            }
            .pointerInput(driver) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    driver.touch(true); awake = true
                    down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - start).getDistance() > 80 * density.density ||
                                change.position.x < 0 || change.position.x > size.width ||
                                change.position.y < 0 || change.position.y > size.height ||
                                event.changes.count { it.pressed } > 1) break
                            change.consume()
                        } while (change.pressed)
                    } finally { driver.touch(false) }
                }
            })
    }
}
