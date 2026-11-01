package com.ngocdevv.nativecook.blocks.charts.stepparade

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
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
import kotlinx.coroutines.flow.first
import kotlin.math.*

internal class StepParadeDriver(auto: Boolean) {
    var h=if(auto)6.0 else StepParadeMotion.NOW
    var hv=0.0
    var drag=false
    var h0=StepParadeMotion.NOW
    var lastTouch=-100.0
    var scripted=false
    var loopT=0.0
    var sel=-1
    var bubbleSelected=-1
    var lastHour=0
    var clock=0.0
    var awake by mutableStateOf(false)
    fun step(wall: Double,auto: Boolean,reduced: Boolean) {
        val dt=min(wall,0.05);clock+=wall
        if(auto&&!drag&&clock-lastTouch>3.5&&(scripted||abs(h-StepParadeMotion.NOW)<0.01)) {
            if(!scripted) { scripted=true;loopT=7.0;sel=-1 }
            loopT=(loopT+wall)%StepParadeMotion.LOOP;h=StepParadeMotion.hour(loopT);hv=0.0
        } else {
            scripted=false
            if(!drag) {
                val k=if(reduced)400.0 else 18.0
                hv+=(k*(StepParadeMotion.NOW-h)-2*sqrt(k)*hv)*dt;h+=hv*dt
                if(!auto&&abs(h-StepParadeMotion.NOW)<0.00001&&abs(hv)<0.00001) { h=StepParadeMotion.NOW;hv=0.0;awake=false }
            }
        }
    }
}

@Composable
fun StepParade(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val auto=autoPlay&&!reduced
    val driver=remember(auto,reduced) { StepParadeDriver(auto) }
    val art=remember(context) { StepParadeArt(context) }
    val frame=remember(driver) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if(!auto&&!driver.awake) { snapshotFlow { driver.awake }.first { it };previous=0L }
                withFrameNanos { nanos ->
                    val wall=(if(previous==0L)0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous=nanos;driver.step(wall,auto,reduced);frame.intValue++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width=maxWidth.value.toDouble()
        val height=maxHeight.value.toDouble()
        Canvas(Modifier.fillMaxSize().pointerInput(driver,width,top) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val scale=density.density
                val sy=start.y/scale
                if(sy<top+268||sy>top+520||start.x/scale<16||start.x/scale>width-16)return@awaitEachGesture
                var panning=false
                var panOffsetX=0.0
                var tapLeftScene=false
                var finalTime=down.uptimeMillis
                var final=start
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id }?:break
                    final=change.position
                    finalTime=change.uptimeMillis
                    if(final.x/scale<16 || final.x/scale>width-16 || final.y/scale<top+268 || final.y/scale>top+520)tapLeftScene=true
                    val dx=(final.x-start.x)/scale
                    if(abs(dx)>8||panning) {
                        if (!panning) {
                            panOffsetX = dx.toDouble()
                            panning = true
                            driver.drag = true
                            driver.scripted = false
                            driver.h0 = driver.h
                            driver.hv = 0.0
                            driver.lastHour = floor(driver.h).toInt()
                        }
                        var hour=driver.h0+(dx-panOffsetX)/(width-32)*16
                        if(hour<6)hour=6-(1-exp(-(6-hour)))*0.4
                        if(hour>21.5)hour=21.5+(1-exp(-(hour-21.5)))*0.3
                        driver.h=hour
                        val hr=floor(hour).toInt()
                        if(hr!=driver.lastHour) { driver.lastHour=hr;view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        driver.lastTouch=driver.clock;driver.awake=true;change.consume();frame.intValue++
                    }
                    if(!change.pressed)break
                } while(true)
                driver.drag=false;driver.lastTouch=driver.clock;driver.awake=true
                if(!panning&&!tapLeftScene&&finalTime-down.uptimeMillis<=500) {
                    var best=-1;var bd=34.0
                    for(i in 0..5) {
                        val p=StepParadeMotion.place(width,top,i,StepParadeMotion.steps(i,driver.h))
                        val d=hypot(final.x/scale-p.x,final.y/scale-(p.y-30*p.s))
                        if(d<bd) { bd=d;best=i }
                    }
                    driver.sel=if(best==driver.sel)-1 else best
                    driver.bubbleSelected=driver.sel
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                frame.intValue++
            }
        }) {
            frame.intValue
            drawIntoCanvas { c ->
                val nc=c.nativeCanvas
                nc.save();nc.scale(density.density,density.density)
                art.draw(nc,width,height,top,bottom,driver.h,driver.sel,driver.bubbleSelected)
                nc.restore()
            }
        }
        Box(Modifier.offset(16.dp,(top+8).dp).size(104.dp,36.dp).semantics { role=Role.Button;contentDescription="Showing friends. Change group" })
        Box(Modifier.offset((width-54).dp,(top+7).dp).size(38.dp).semantics { contentDescription="Your profile" })
        Box(Modifier.offset(16.dp,(top+60).dp).size((width-32).dp,53.dp).semantics { contentDescription="This week's winners: Monday Leo, Tuesday You, Wednesday Kofi, Thursday Leo, Friday Ana, Saturday Juno, today Leo" })
        Box(Modifier.offset((width/2-110).dp,(top+159).dp).size(220.dp,64.dp).semantics { contentDescription="Your steps today";frame.intValue;stateDescription=StepParadeMotion.number(StepParadeMotion.steps(1,driver.h)) })
        Box(Modifier.offset(16.dp,(top+268).dp).size((width-32).dp,252.dp).semantics { contentDescription="Your friends walking through the day. Drag sideways to replay the day" })
        for(i in 0..1)Box(Modifier.offset((16+i*(width-20)/2).dp,(top+590).dp).size(((width-44)/2).dp,64.dp).semantics { contentDescription=if(i==0)"Distance" else "Active time" })
        for(i in 0..3)Box(Modifier.offset((i*width/4).dp,(height-bottom-54).dp).size((width/4).dp,48.dp).semantics { role=Role.Tab;selected=i==1;contentDescription=listOf("Home","Friends","Challenges","Me")[i] })
    }
}
