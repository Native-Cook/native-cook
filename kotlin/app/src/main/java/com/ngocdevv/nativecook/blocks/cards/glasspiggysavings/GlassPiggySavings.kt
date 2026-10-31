package com.ngocdevv.nativecook.blocks.cards.glasspiggysavings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import com.ngocdevv.nativecook.blocks.cards.glasspiggysavings.GlassPiggySavingsMotion as M

@Composable
fun GlassPiggySavings(autoPlay:Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val resources=remember(context) { GlassPiggySavingsResources(context) }
    val art=remember(resources) { GlassPiggySavingsArt(resources) }
    val driver=remember { GlassPiggySavingsDriver() }
    val audio=remember(context,reduced) { if(reduced) null else GlassPiggySavingsAudio(context) }
    val redraw=remember { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(0) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(success:Boolean) {
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(success) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(success) 24 else 12,45))
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null) controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,autoPlay,reduced,wake) {
        if(reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            try {
                while(true) {
                    if(!autoPlay && !driver.moving) awaitCancellation()
                    withFrameNanos { nanos ->
                        val dt=(if(previous==0L) 0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.05)
                        previous=nanos
                        driver.step(dt,autoPlay) { audio?.play("clink"); buzz(true) }
                        redraw.intValue++
                    }
                }
            } finally { audio?.stop() }
        }
    }
    val safeTop=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val safeBottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout=M.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),safeTop,safeBottom)
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,layout) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                var end=down.position
                driver.begin((end.x/density.density).toDouble(),(end.y/density.density).toDouble(),layout,reduced,{ audio?.play(it) },::buzz)
                redraw.intValue++; wake++; down.consume()
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        end=change.position
                        if(change.pressed) driver.update((end.x/density.density).toDouble(),(end.y/density.density).toDouble())
                        redraw.intValue++; change.consume()
                    } while(change.pressed && event.changes.any { it.pressed })
                } finally {
                    driver.end((end.x/density.density).toDouble(),(end.y/density.density).toDouble(),layout,reduced,::buzz)
                    redraw.intValue++; wake++
                }
            }
        }) {
            redraw.intValue
            drawIntoCanvas { target ->
                val c=target.nativeCanvas; c.save(); c.scale(density.density,density.density)
                art.draw(c,layout,driver.frame,density.density); c.restore()
            }
        }
        GlassPiggySavingsAccessibility(driver,layout,redraw) { i ->
            driver.drop(i,layout,reduced,lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)); redraw.intValue++; wake++
        }
    }
}

// This small subtree updates its text semantics independently of the screen's draw loop.
@Composable
private fun GlassPiggySavingsAccessibility(driver:GlassPiggySavingsDriver,l: M.Layout,tick:State<Int>,drop:(Int)->Unit) {
    tick.value
    Box(Modifier.offset(24.dp,(l.top+18).dp).size((l.w-48).dp,130.dp).semantics(mergeDescendants=true) {
        contentDescription="LISBON TRIP · SAVED SO FAR. ${GlassPiggySavingsArt.money(driver.frame.total)}. Goal $2,000 · by June 14"
    })
    repeat(3) { i ->
        Box(Modifier.offset((l.chipX(i)-50).dp,(l.chipY-22).dp).size(100.dp,44.dp).semantics {
            role=Role.Button
            contentDescription="Add $${M.CHIPS[i]} to the piggy bank"
            onClick { drop(i); true }
        })
    }
    Box(Modifier.offset(0.dp,(l.chipY+34).dp).size(l.w.dp,20.dp).semantics {
        contentDescription="Drag a coin into the piggy"
    })
    if(driver.frame.receipt().first>0.01) {
        Box(Modifier.offset(16.dp,(l.h-l.bottom-64+driver.frame.receipt().second).dp).size((l.w-32).dp,58.dp).semantics(mergeDescendants=true) {
            liveRegion=LiveRegionMode.Polite
            contentDescription="$${driver.frame.plus} saved to Lisbon trip. Just now · from Everyday account"
        })
    }
}
