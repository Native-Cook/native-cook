package com.ngocdevv.nativecook.blocks.modals.perfectmonthgem

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import kotlin.math.*

@Composable
fun PerfectMonthGem(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val top = WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val driver = remember(autoPlay,reduced) { PerfectMonthGemDriver() }
    val art = remember(context) { PerfectMonthGemArt(context) }
    val audio = remember(context,reduced) { if (reduced) null else PerfectMonthGemAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val tick = remember(driver) { mutableIntStateOf(0) }
    var gem by remember { mutableStateOf(false) }
    fun buzz(kind: Int = 0) {
        if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind==0) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind==2) 25 else 12,if (kind==0) 35 else 60))
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,audio) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(.5,if (previous==0L) .016 else (nanos-previous)/1e9)
                previous=nanos
                driver.step(dt,autoPlay,reduced) { audio?.play("forge"); buzz(2) }
                if (gem != (driver.p>.5)) gem=driver.p>.5
                tick.intValue++
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val cy = top+196.0
        Canvas(Modifier.fillMaxSize().pointerInput(driver,audio,w,h,top,bottom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val sx=start.x/density; val sy=start.y/density
                val stage=sx>=w/2-120 && sx<=w/2+120 && sy>=cy-110 && sy<=cy+110
                val by=h-bottom-18-108
                val primary=sx>=18 && sx<=w-18 && sy>=by && sy<=by+54
                val secondary=sx>=18 && sx<=w-18 && sy>=by+60 && sy<=by+108
                if (!stage && !primary && !secondary) return@awaitEachGesture
                if (stage) driver.begin()
                val tracker=VelocityTracker().apply { addPosition(down.uptimeMillis,down.position) }
                var end=start
                var dragged=false
                var cancelled=false
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        end=change.position
                        tracker.addPosition(change.uptimeMillis,change.position)
                        val dx=(end.x-start.x)/density; val dy=(end.y-start.y)/density
                        if (stage && !dragged && hypot(dx,dy)>=6f) {
                            dragged=true; driver.held=true; driver.start=driver.p; driver.velocity=0.0
                            audio?.play("gather"); buzz(1)
                        }
                        if (stage && dragged) driver.drag(dy.toDouble())
                        change.consume()
                    } while (change.pressed && event.changes.any { it.pressed })
                } catch (e: kotlinx.coroutines.CancellationException) { cancelled=true; throw e }
                finally {
                    if (stage) {
                        if (dragged) driver.end(tracker.calculateVelocity().y/density.toDouble())
                        else if (!cancelled) { audio?.play(driver.tap()); buzz(0) }
                    } else if (!cancelled && (end-start).getDistance()/density<12) buzz(if (primary) 1 else 0)
                    tick.intValue++
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas { canvas ->
                val native=canvas.nativeCanvas
                native.save(); native.scale(density,density)
                art.draw(native,size.width/density.toDouble(),size.height/density.toDouble(),top.toDouble(),bottom.toDouble(),driver.p,driver.turn(autoPlay,reduced),reduced)
                native.restore()
            }
        }
        Box(Modifier.offset(x=(w/2-120).dp,y=(cy-110).dp).size(240.dp,220.dp).semantics {
            role=Role.Button
            contentDescription=if (gem) "Perfect Month badge, made of your October. Double-tap to see the days." else "October: 30 of 31 days on budget. Double-tap to forge the badge."
            onClick { driver.accessibleToggle(); buzz(); true }
        })
        val by=h-bottom-18-108
        Box(Modifier.offset(18.dp,by.dp).size((w-36).dp,54.dp).semantics { role=Role.Button; contentDescription="View badge"; onClick { buzz(1); true } })
        Box(Modifier.offset(18.dp,(by+60).dp).size((w-36).dp,48.dp).semantics { role=Role.Button; contentDescription="Go to home"; onClick { buzz(); true } })
        Box(Modifier.offset(28.dp,(cy+150).dp).size((w-56).dp,64.dp).semantics { if (gem) { heading(); contentDescription="New Badge unlocked!" } })
        Box(Modifier.offset(74.dp,(h*.645+43).dp).size((w-106).dp,20.dp).semantics { role=Role.Button; contentDescription="Give us a rating" })
    }
}
