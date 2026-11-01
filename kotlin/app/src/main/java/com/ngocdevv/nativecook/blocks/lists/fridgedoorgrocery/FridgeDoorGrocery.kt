package com.ngocdevv.nativecook.blocks.lists.fridgedoorgrocery

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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun FridgeDoorGrocery(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { FridgeDoorGroceryArt(context) }
    val driver=remember(autoPlay,reduced) { FridgeDoorGroceryDriver() }
    val frame=remember(driver) { mutableStateOf(FridgeDoorGroceryMotion.Frame()) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density.toDouble()
    fun buzz(kind:Int) {
        if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(when(kind) { 0->VibrationEffect.EFFECT_TICK; 1->VibrationEffect.EFFECT_HEAVY_CLICK; else->VibrationEffect.EFFECT_CLICK }))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==1)18 else 12,if(kind==1)80 else 45))
    }
    DisposableEffect(view) {
        val window=(context as? android.app.Activity)?.window
        val controller=window?.let { WindowCompat.getInsetsController(it,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(driver,autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                withFrameNanos { nanos ->
                    val wall=min(0.5,max(0.0,if(previous==0L)0.016 else (nanos-previous)/1e9))
                    previous=nanos
                    frame.value=driver.step(wall,autoPlay && !reduced,::buzz)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role=Role.Button
        contentDescription="Fridge. Drag the door to open it, tap an item to add it to the basket. Milk and eggs are running low"
        stateDescription="Basket, ${frame.value.bag} items"
        onClick {
            val g=FridgeDoorGroceryMotion.Geo(view.width/density.toDouble(),view.height/density.toDouble(),top)
            driver.tap(g.x,g.y,g,::buzz); true
        }
    }.pointerInput(driver,top,density) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val g=FridgeDoorGroceryMotion.Geo(size.width/density.toDouble(),size.height/density.toDouble(),top)
            val start=down.position
            val sx=start.x/density; val sy=start.y/density
            if(sx<g.x-30 || sx>g.x+g.w+50 || sy<g.y || sy>g.y+g.h)return@awaitEachGesture
            val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
            var end=start; var pan=false
            down.consume()
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull { it.id==down.id } ?: break
                end=change.position; tracker.addPosition(change.uptimeMillis,end)
                val dx=(end.x-start.x)/density
                if(!pan && abs(dx)>8) { pan=true; driver.drag=true; driver.scripted=false; driver.a0=driver.a; driver.v=0.0 }
                if(pan && change.pressed) { driver.a=FridgeDoorGroceryMotion.clamp(driver.a0-dx/(g.w*1.1)); driver.lastTouch=driver.now }
                change.consume()
            } while(change.pressed && event.changes.any { it.pressed })
            if(pan) {
                val vx=tracker.calculateVelocity().x/density
                driver.drag=false
                driver.target=if(vx< -400)1.0 else if(vx>400)0.0 else if(driver.a>0.35)1.0 else 0.0
                driver.v= -vx/(g.w*1.1); driver.lastTouch=driver.now
            } else if(hypot((end.x-start.x)/density,(end.y-start.y)/density)<=10)driver.tap(end.x/density.toDouble(),end.y/density.toDouble(),g,::buzz)
        }
    }) {
        drawIntoCanvas {
            val c=it.nativeCanvas
            c.save(); c.scale(density,density)
            art.draw(c,size.width/density.toDouble(),size.height/density.toDouble(),top,bottom,frame.value,reduced)
            c.restore()
        }
    }
}
