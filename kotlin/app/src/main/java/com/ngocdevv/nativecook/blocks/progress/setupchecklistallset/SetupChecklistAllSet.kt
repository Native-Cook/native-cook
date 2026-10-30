package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetMotion.Live
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetMotion.Driver
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetMotion.D
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-frame state is read in drawing only, leaving the composition out of the animation loop. */
@Composable
fun SetupChecklistAllSet(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val pin = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) previewTime else null
    val art = remember(context) { SetupChecklistAllSetArt(context) }
    val audio = remember(context,reduced) { if (reduced) null else SetupChecklistAllSetAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(autoPlay,reduced,pin) { Driver() }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val scope = rememberCoroutineScope()
    val frame = remember(driver) { mutableStateOf(Live(pin ?: 0.0,pin ?: 0.0,SetupChecklistAllSetMotion.loopState(pin ?: 0.0))) }
    fun buzz(kind: Int) {
        val feedback = when (kind) {
            2 -> HapticFeedbackConstants.CLOCK_TICK
            3 -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            1 -> HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
    }
    fun after(outcome: String) {
        if (outcome == "uncheck") { audio?.play("uncheck"); buzz(2) }
        else {
            audio?.play("check"); buzz(0)
            if (outcome == "complete") {
                // Match RN's wall-clock setTimeout cues, independently of dropped frames.
                scope.launch {
                    delay(((0.4+D.morph)*1000).roundToLong())
                    audio?.play("complete")
                }
                scope.launch {
                    delay(((0.4+D.morph+0.2)*1000).roundToLong())
                    audio?.play("pop"); buzz(3)
                }
            }
        }
    }
    LaunchedEffect(driver,lifecycle,audio) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos-previous)/1e9,0.05)
                    previous = nanos; clock[0] += dt
                    if (pin != null) { driver.mode = 0; driver.t = pin; driver.amb = pin }
                    else driver.step(dt,clock[0],autoPlay && !reduced)
                    frame.value = if (driver.mode == 0) Live(driver.t,driver.amb,SetupChecklistAllSetMotion.loopState(driver.t))
                        else Live(if (reduced) clock[0]+10 else clock[0],driver.amb,driver.s.snapshot())
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Your setup checklist, 1 of 6 done. Add your name and bio."
            onClick(label = "Ticks the next item") {
                if (pin == null) {
                    driver.takeOver(clock[0])
                    val next = driver.s.done.indexOfFirst { !it }
                    if (next >= 0) driver.toggle(next,clock[0])
                }
                true
            }
        }
        .pointerInput(driver,reduced,pin,audio) {
            if (pin != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width/402.0
                val start = down.position
                driver.takeOver(clock[0]); down.consume()
                var end = start
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) { end = change.position; change.consume() }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                } finally {
                    val now = clock[0]
                    driver.last = now
                    if ((end-start).getDistance()/scale <= 14) {
                        val (kind,row) = driver.hit(end.x/scale,end.y/scale,now)
                        when (kind) {
                            1,3 -> { if (kind == 3) driver.s.pressRow = row; after(driver.toggle(row,now)) }
                            2 -> { driver.toggleRow(row,now); buzz(2) }
                            4 -> { driver.skip(row,now); buzz(2) }
                            5 -> if (driver.dismiss(now,false)) buzz(0)
                            6 -> { driver.s.ctaAt = now; audio?.play("pop"); buzz(3) }
                            7 -> { driver.s.laterAt = now; if (driver.dismiss(now+0.15,true)) buzz(0) }
                        }
                    }
                }
            }
        }
    ) {
        drawIntoCanvas { art.draw(it.nativeCanvas,size.width/402.0,frame.value) }
    }
}
