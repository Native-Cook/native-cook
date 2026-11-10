package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlin.math.*

@Composable
fun VitaminCapsuleDose(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun reduceMotion() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(context) { mutableStateOf(reduceMotion()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = reduceMotion() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val art = remember(context) { VitaminCapsuleDoseArt(context) }
    val audio = remember(context) { VitaminCapsuleDoseAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver = remember(autoPlay, reduced, audio) {
        VitaminCapsuleDoseDriver(autoPlay, reduced, audio::play) { kind ->
            if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(
                if (kind == 2) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_TICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 2) 20 else 8, if (kind == 2) 60 else 35))
        }
    }
    // Medicine/checks already use snapshot state. Publish only discrete mode changes for the action label.
    val semanticMode = remember(driver) { mutableIntStateOf(driver.frame.mode) }
    fun syncSemanticMode() {
        if (semanticMode.intValue != driver.frame.mode) semanticMode.intValue = driver.frame.mode
    }
    val revision = remember(driver) { mutableIntStateOf(0) }
    val wake = remember(driver) { Channel<Unit>(Channel.CONFLATED) }
    fun changed() { syncSemanticMode(); revision.intValue++; wake.trySend(Unit) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val control = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = control?.isAppearanceLightStatusBars
        control?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) control.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if (!driver.moving()) { wake.receive(); previous = 0L }
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.0 else min(0.05, (nanos - previous) / 1e9)
                    previous = nanos; driver.step(dt); syncSemanticMode(); revision.intValue++
                }
            }
        }
    }
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = VitaminCapsuleDoseLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottom)
        SideEffect { driver.layout = l }
        Canvas(Modifier.fillMaxSize().pointerInput(driver, l) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position / density.density
                val hero = start.x >= l.cx - l.unit * 1.25 && start.x <= l.cx + l.unit * 1.25 &&
                    start.y >= l.cy - l.unit * 1.15 && start.y <= l.cy + l.unit * 1.05
                val log = start.x >= 25*l.sx && start.x <= 378*l.sx && start.y >= l.y(777.0) && start.y <= l.y(833.0)
                val row = l.rowsY.indexOfFirst { abs(start.x - l.checkX) < 22 && abs(start.y - it) < 22 }
                val stir = !hero && driver.frame.mode == 2 && start.y >= l.floor-70 && start.y <= l.floor+14
                val target = hero || log || row >= 0 || stir
                if (target) { driver.touch(); changed(); down.consume() }
                if (log) driver.beginHold()
                if (stir) { driver.stirX = start.x.toDouble(); driver.stirY = start.y.toDouble(); driver.stirDown = true }
                val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, down.position)
                var end = start; var released = false; var isPull = false; var decided = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position / density.density
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = (end.x-start.x).toDouble(); val dy = (end.y-start.y).toDouble()
                        if (target) { driver.lastTouch = driver.now; change.consume() }
                        if (hero && change.pressed && hypot(dx,dy)>4) {
                            if (!decided) {
                                decided = true
                                val along = (dx-dy)*sqrt(0.5)/max(1.0,hypot(dx,dy))
                                isPull = driver.frame.a == 0.0 && driver.frame.k == 0.0 && driver.frame.mode == 0 && driver.frame.sep < .05 && along > .6 && !reduced
                            }
                            val velocity = tracker.calculateVelocity()
                            if (isPull) driver.pull((dx-dy)*sqrt(0.5)/l.unit)
                            else driver.turn(dx,dy,(velocity.x/density.density).toDouble(),(velocity.y/density.density).toDouble())
                        }
                        if (stir) driver.stir(end.x.toDouble(),end.y.toDouble())
                        if (!change.pressed) released = true
                    } while (change.pressed && event.changes.any { it.pressed })
                } finally {
                    val dx=(end.x-start.x).toDouble();val dy=(end.y-start.y).toDouble()
                    val velocity=tracker.calculateVelocity()
                    if (released && hero) {
                        if (isPull) driver.endPull((velocity.x/density.density).toDouble(),(velocity.y/density.density).toDouble())
                        else if (hypot(dx,dy)<8 && driver.frame.mode==2) driver.close()
                        else driver.endTurn(dx,(velocity.x/density.density).toDouble())
                    }
                    if (released && row>=0 && hypot(dx,dy)<12) driver.undo(row)
                    if (log) driver.endHold()
                    driver.dragging=false;driver.stirDown=false
                    if (target) { driver.lastTouch=driver.now;changed() }
                }
            }
        }) {
            revision.intValue // invalidate drawing without recomposing the screen every frame
            drawIntoCanvas { output ->
                val canvas=output.nativeCanvas
                canvas.save();canvas.scale(density.density,density.density);art.draw(canvas,l,driver);canvas.restore()
            }
        }
        val medicine=VitaminCapsuleDoseTheme.meds[driver.med]
        Box(Modifier.offset((l.cx-l.unit*1.25).dp,(l.cy-l.unit*1.15).dp).size((l.unit*2.5).dp,(l.unit*2.2).dp).semantics {
            contentDescription=medicine.name
            stateDescription="${medicine.kind}, ${medicine.dose}"
            customActions=listOf(
                CustomAccessibilityAction("Next medicine") { driver.touch();driver.select(1);changed();true },
                CustomAccessibilityAction("Previous medicine") { driver.touch();driver.select(-1);changed();true },
                CustomAccessibilityAction(if(semanticMode.intValue==2)"Close capsule" else "Open capsule") {
                    driver.touch()
                    if(driver.frame.mode==2)driver.close() else if(driver.frame.a==0.0 && !reduced) { driver.pull(.55);driver.endPull() }
                    changed();true
                },
            )
        })
        Box(Modifier.offset((25*l.sx).dp,(l.y(338.0)-20).dp).size((300*l.sx).dp,70.dp).semantics {
            contentDescription="${medicine.name}. ${medicine.kind}, ${medicine.dose}";heading()
        })
        for (i in 0..1) Box(Modifier.offset(((if(i==0)25 else 210)*l.sx).dp,l.chipTop.dp).size((168*l.sx).dp,l.chipH.dp).semantics {
            contentDescription=if(i==0)"Daily Dosage: ${medicine.daily}" else "Each Day: ${medicine.each}"
        })
        Box(Modifier.offset((25*l.sx).dp,(l.y(512.0)-15).dp).size(150.dp,30.dp).semantics { contentDescription="Schedule";heading() })
        for (row in 0..2) {
            val on=driver.checks[driver.med*3+row]==1
            Box(Modifier.offset((25*l.sx).dp,(l.rowsY[row]-22).dp).size((353*l.sx).dp,44.dp).semantics {
                role=Role.Checkbox;toggleableState=if(on)ToggleableState.On else ToggleableState.Off
                contentDescription=medicine.times[row]+if(on)", taken: undo" else ""
                if(on)onClick("Undo dose") { driver.undo(row);changed();true }
            })
        }
        Box(Modifier.offset((25*l.sx).dp,l.y(777.0).dp).size((353*l.sx).dp,(56*l.k).dp).semantics {
            role=Role.Button;contentDescription="Log Medication";stateDescription="Press and hold to log this dose"
            onClick("Log this dose") { driver.touch();driver.takeDose();changed();true }
        })
        Box(Modifier.offset((35*l.sx-14).dp,(l.y(124.0)-20).dp).size(100.dp,40.dp).semantics {role=Role.Button;contentDescription="Back"})
        Box(Modifier.offset((353*l.sx-24).dp,(l.y(349.0)-24).dp).size(48.dp).semantics {role=Role.Button;contentDescription="Edit ${medicine.name}"})
        Box(Modifier.offset((270*l.sx).dp,(l.y(512.0)-16).dp).size((110*l.sx).dp,32.dp).semantics {role=Role.Button;contentDescription="All logs"})
        Box(Modifier.offset((25*l.sx).dp,l.y(689.0).dp).size((353*l.sx).dp,(49*l.k).dp).semantics {role=Role.Button;contentDescription="Export to PDF"})
    }
}
