package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar.CabinClimateCarMotion as M
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

@Composable
fun CabinClimateCar(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    val art = remember(context) { CabinClimateCarArt(context) }
    val audio = remember(context) { CabinClimateCarAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    val driver = remember(autoPlay,reduced) { CabinClimateCarDriver() }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val frame = remember(driver) { mutableStateOf(M.frameAt(0.0)) }
    fun sample() {
        val f = driver.frame(clock[0])
        if (reduced) { f.amb = 0.0; f.tempAt = 10.0; f.heat = f.temp; f.leg = if (f.legOn) 1.0 else 0.0 }
        frame.value = f
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            try {
                while (true) {
                    withFrameNanos { nanos ->
                        val dt = min(if (previous == 0L) 0.016 else (nanos-previous)/1e9,0.05)
                        previous = nanos; clock[0] += dt
                        if (driver.step(dt,clock[0],autoPlay && !reduced)) audio.play("tick",reduced,view)
                        sample()
                    }
                }
            } finally { audio.stop() }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Cabin controls. Temperature 70 degrees. Distributed legroom. Fan auto."
            onClick(label = "Warm the cabin a degree") {
                driver.takeOver(clock[0]); driver.setTemp(driver.temp+1,clock[0]); sample(); true
            }
        }
        .pointerInput(driver,reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pinScale = size.width/720.0
                val x = down.position.x/pinScale; val y = down.position.y/pinScale
                val startY = down.position.y
                var now = clock[0]
                driver.takeOver(now); driver.released = 0
                val startTemp = driver.temp
                fun inside(left: Double,top: Double,width: Double,height: Double) =
                    x > left && x < left+width && y > top && y < top+height
                driver.pressing = when {
                    inside(30.0,580.0,322.0,490.0) ->
                        if (abs(y-670) < 70) 1 else if (abs(y-978) < 70) 2 else 6
                    inside(369.0,580.0,321.0,235.0) -> 3
                    inside(369.0,833.0,321.0,237.0) -> 4
                    abs(x-360) < 85 && abs(y-181) < 39 -> 5
                    else -> 0
                }
                driver.pressAt = now; driver.repeatAt = now
                down.consume()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        now = clock[0]; driver.last = now
                        val dy = (change.position.y-startY)/pinScale
                        if (driver.pressing in listOf(1,2,6) && abs(dy) > 18) driver.pressing = 6
                        if (driver.pressing == 6 && driver.setTemp(floor(startTemp-dy/36+0.5),if (reduced) now-2 else now)) {
                            audio.play("tick",reduced,view)
                        }
                        change.consume()
                    }
                } finally {
                    now = clock[0]; driver.last = now
                    val was = driver.pressing
                    driver.pressing = 0; driver.released = was; driver.releaseAt = now
                    when (was) {
                        1,2 -> if (now-driver.pressAt <= 0.45 &&
                            driver.setTemp(driver.temp+(if (was == 1) 1 else -1),if (reduced) now-2 else now)) {
                            audio.play("tick",reduced,view)
                        }
                        3 -> { driver.toggleLeg(if (reduced) now-2 else now); audio.play("slide",reduced,view) }
                        4 -> { driver.fan = (driver.fan+1)%3; driver.fanAt = now; audio.play("air",reduced,view) }
                        5 -> audio.play("tap",reduced,view)
                    }
                }
            }
        }
    ) {
        drawIntoCanvas { art.draw(it.nativeCanvas,size.width,size.height,frame.value) }
    }
}
