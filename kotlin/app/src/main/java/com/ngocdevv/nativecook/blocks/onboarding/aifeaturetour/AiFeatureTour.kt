package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToLong
import com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour.AiFeatureTourMotion as M

/** Canvas reads frame state during drawing, so the whole screen does not recompose every frame. */
@Composable
fun AiFeatureTour(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pinned = if (debug) previewTime else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val art = remember(context) { AiFeatureTourArt(context) }
    val audio = remember(context) { AiFeatureTourAudio(context) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    val scope = rememberCoroutineScope()
    val driver = remember(auto, reduced, pinned) { M.Driver() }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val frame = remember(driver) { mutableStateOf(M.frameAt(pinned ?: 0.0)) }
    fun button(x: Double, y: Double): Boolean {
        val b = AiFeatureTourTheme.UI.button
        return x > b.x - 10 && x < b.x + b.w + 10 && y > b.y - 10 && y < b.y + b.h + 10
    }
    fun finalize(x: Double, y: Double) {
        val now = clock[0]
        driver.last = now
        if (driver.pressing == 1) {
            driver.pressing = 0
            if (!button(x, y)) return
            audio.play("tap", reduced)
            if (reduced || now - driver.flyAt < M.LOOP - M.T.press) return
            driver.flyAt = now
            audio.play("whoosh", false)
            scope.launch {
                delay(((M.T.sparkle - M.T.press) * 1000).roundToLong())
                audio.play("sparkle", false)
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            }
            return
        }
        val rows = AiFeatureTourTheme.UI.rows
        for (i in 0 until M.ICONS) {
            val ty = rows.y0 + i * rows.pitch
            if (y > ty - 70 && y < ty + 130) {
                if (now - driver.actAt[i] < M.ACT) return
                driver.actAt[i] = if (reduced) now - M.ACT else now
                audio.play("tick", reduced)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return
            }
        }
    }
    // Callback values are immutable for a driver's lifetime, or owned mutable state.
    val finalizeLatest by rememberUpdatedState(::finalize)
    LaunchedEffect(driver, lifecycle) {
        if (pinned != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    clock[0] += dt
                    driver.step(dt, clock[0], auto)
                    frame.value = driver.frame(clock[0])
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Halden Intelligence can: create check ins, understand your trends, create workout templates, log food, create a training plan. Continue."
            onClick(label = "Continue") {
                driver.takeOver(clock[0])
                driver.flyAt = clock[0]
                true
            }
        }
        .pointerInput(driver) {
            if (pinned != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 720.0
                driver.takeOver(clock[0])
                if (button(down.position.x / scale, down.position.y / scale)) {
                    driver.pressing = 1
                    driver.pressAt = clock[0]
                }
                var end = down.position
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        end = change.position
                        change.consume()
                    } while (change.pressed)
                } finally {
                    // RN onFinalize also runs when recognition is cancelled.
                    finalizeLatest(end.x / scale, end.y / scale)
                }
            }
        }
    ) {
        drawIntoCanvas { art.draw(it.nativeCanvas, size.width, size.height, frame.value) }
    }
}
