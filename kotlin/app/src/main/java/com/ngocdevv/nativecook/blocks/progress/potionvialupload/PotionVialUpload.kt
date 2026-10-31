package com.ngocdevv.nativecook.blocks.progress.potionvialupload

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
fun PotionVialUpload(autoPlay:Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    val insets=WindowInsets.systemBars
    val top=insets.getTop(density)/density.density.toDouble()
    val bottom=insets.getBottom(density)/density.density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context){PotionVialUploadArt(context)}
    val driver=remember(autoPlay,reduced){PotionVialUploadDriver(if(reduced)10.4 else 0.0)}
    val audio=remember(context,reduced){if(reduced)null else PotionVialUploadAudio(context)}
    val clock=remember(driver){doubleArrayOf(0.0)}
    val tick=remember(driver){mutableIntStateOf(0)}
    fun buzz(kind:Int) {
        view.performHapticFeedback(when(kind) { 3 -> if(Build.VERSION.SDK_INT>=30)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS; 2 -> HapticFeedbackConstants.CLOCK_TICK; 1 -> HapticFeedbackConstants.LONG_PRESS; else -> HapticFeedbackConstants.KEYBOARD_TAP })
    }
    fun event(ev:Int,sound:Boolean) {
        if(!sound)return
        when(ev) { 1 -> { audio?.play("pop"); buzz(0) }; 2 -> { audio?.play("gulp"); buzz(1) }; 3 -> audio?.play("clink"); 4 -> { audio?.play("chime"); buzz(3) } }
    }
    val onEvent by rememberUpdatedState(::event)
    DisposableEffect(art) { onDispose{art.close()} }
    DisposableEffect(audio) { onDispose{audio?.close()} }
    DisposableEffect(view) {
        val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)}
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose{if(previous!=null)controller.isAppearanceLightStatusBars=previous}
    }
    LaunchedEffect(driver,lifecycle,reduced,autoPlay) {
        if(reduced)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true)withFrameNanos { nanos ->
                val dt=min(if(previous==0L)0.016 else (nanos-previous)/1e9,0.05); previous=nanos; clock[0]+=dt; val now=clock[0]
                if(driver.mode==1 && !driver.fileX.isNaN()) {
                    val dist=hypot(driver.fileX-339,driver.fileY)
                    if(dist<88 && driver.gem.corked && driver.gem.homeAt<0) { driver.gem.pop(0.8); onEvent(1,true) }
                    else if(dist>110 && !driver.gem.corked) { driver.gem.recork(now); onEvent(3,true) }
                }
                if(driver.fileBack<1)driver.fileBack=min(1.0,(now-driver.fileBackAt)/0.5)
                val ev=driver.step(dt,now,autoPlay); onEvent(ev,driver.sound); tick.intValue++
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role=Role.Button
        contentDescription="Upload spells.zip, 48.2 megabytes, into the potion vial."
        onClick(label="Plays the upload.") {
            if(!reduced) { driver.t=0.6; driver.mode=0; driver.sound=true; driver.last=clock[0]; tick.intValue++ }; true
        }
    }.pointerInput(driver,reduced,top,bottom) {
        if(reduced)return@pointerInput
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val geo=PotionVialUploadGeo(size.width/density.density.toDouble(),size.height/density.density.toDouble(),top,bottom)
            fun local(x:Float,y:Float)=PotionVialUploadMotion.Point((x/density.density-geo.ox)/geo.s,(y/density.density-geo.cy)/geo.s)
            val start=local(down.position.x,down.position.y)
            val f=PotionVialUploadFrame(driver.t,driver,clock[0],geo,false)
            val inCard=start.y>=geo.cardY-8 && start.y<=geo.cardY+76 && start.x>=68 && start.x<=334
            val target=if(!driver.gem.corked && hypot(start.x-f.gemX,start.y-f.gemY)<34)2 else if(hypot(start.x-f.spX,start.y-f.spY)<26)3 else if(inCard && f.slotEmpty<0.5)1 else if(abs(start.y)<40 && start.x>10 && start.x<345)4 else 0
            driver.last=clock[0]
            when(target) {
                1 -> { driver.mode=1; driver.t=0.0; driver.fileX=106.0; driver.fileY=geo.cardY+34; if(!driver.gem.corked)driver.gem.recork(clock[0]) }
                2 -> { driver.mode=2; driver.gem.holdX=start.x-315.5; driver.gem.holdY=start.y-14 }
                3 -> { driver.kickAt=clock[0]; driver.kickDir=if(f.spX>200)-1.0 else 1.0; audio?.play("skitter"); buzz(2) }
            }
            var end=start; down.consume()
            try {
                do {
                    val e=awaitPointerEvent(); val change=e.changes.firstOrNull{it.id==down.id}?:break
                    end=local(change.position.x,change.position.y)
                    if(change.pressed) {
                        driver.last=clock[0]
                        if(target==1) { driver.fileX=106+end.x-start.x; driver.fileY=geo.cardY+34+end.y-start.y }
                        else if(target==2) { driver.gem.holdX=end.x-315.5; driver.gem.holdY=end.y-14 }
                    }
                    change.consume()
                } while(change.pressed)
            } finally {
                val now=clock[0]; val tap=hypot(end.x-start.x,end.y-start.y)<10; driver.last=now
                if(target==1) {
                    val dist=if(driver.fileX.isNaN())999.0 else hypot(driver.fileX-339,driver.fileY)
                    if(tap) { driver.fileX=Double.NaN; driver.t=0.6; driver.mode=0; driver.sound=true }
                    else if(dist<88) { driver.drop(now); event(2,true) }
                    else { driver.fileFromX=driver.fileX; driver.fileFromY=driver.fileY; driver.fileBack=0.0; driver.fileBackAt=now; driver.fileX=Double.NaN; driver.mode=3; if(!driver.gem.corked)driver.gem.recork(now) }
                } else if(target==2) { driver.gem.holdX=Double.NaN; driver.mode=3; buzz(0) }
                else if(target==4 && tap) {
                    if(driver.t>=8.5 && driver.t<11.6) { driver.t=11.59; driver.mode=0; driver.sound=true }
                    else if(driver.t<0.6 || driver.mode==3) { driver.t=0.6; driver.mode=0; driver.sound=true }
                }; tick.intValue++
            }
        }
    }) {
        tick.intValue
        val geo=PotionVialUploadGeo(size.width/density.density.toDouble(),size.height/density.density.toDouble(),top,bottom)
        val frame=PotionVialUploadFrame(driver.t,driver,clock[0],geo,reduced)
        drawIntoCanvas { canvas -> val c=canvas.nativeCanvas; c.save(); c.scale(density.density,density.density); art.draw(c,geo,frame,density.density.toDouble()); c.restore() }
    }
}
