package com.ngocdevv.nativecook.blocks.sheets.bundlepackbox

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import kotlin.math.*

@Composable
fun BundlePackBox(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { BundlePackBoxArt(context) }
    val driver=remember(autoPlay,reduced) { BundlePackBoxDriver() }
    var frame by remember(driver) { mutableStateOf(BundlePackBoxMotion.Frame()) }
    var revision by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val packed by remember { derivedStateOf { frame.packed } }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(success: Boolean=false) {
        if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(if(success)VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_HEAVY_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(success)24 else 18,80))
    }
    fun toggle() { buzz(); driver.toggle(); revision++ }
    DisposableEffect(driver) { driver.success={buzz(true)};onDispose {driver.success=null} }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) {(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let {WindowCompat.getInsetsController(it.window,view)}
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose {if(previous!=null)controller.isAppearanceLightStatusBars=previous}
    }
    LaunchedEffect(driver,lifecycle,revision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            do {
                withFrameNanos { nanos ->
                    val wall=(if(previous==0L)0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.5); previous=nanos
                    frame=driver.step(wall,autoPlay && !reduced,reduced)
                }
            } while(!reduced || driver.pinch || abs(driver.p-driver.target)>0.0001 || abs(driver.v)>0.001)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value.toDouble();val h=maxHeight.value.toDouble()
        val ctaY=h-bottom-96
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,w,h,top,bottom) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density.density
                val isButton=start.x>=22 && start.x<=w-22 && start.y>=ctaY && start.y<=ctaY+56
                val onStage=start.y>=top+60 && start.y<=min(art.sheetTop,top+60+h*0.56)
                pressed=isButton
                var pinching=false;var scale=1.0;var velocity=0.0;var lastTime=down.uptimeMillis;var last=down.position
                try {
                    do {
                        val event=awaitPointerEvent()
                        val active=event.changes.count {it.pressed}
                        val pointer=event.changes.firstOrNull {it.id==down.id}
                        if(pointer!=null)last=pointer.position
                        if(onStage && active>=2) {
                            if(!pinching) {driver.begin();pinching=true;revision++}
                            val zoom=event.calculateZoom().toDouble()
                            val time=event.changes.maxOf {it.uptimeMillis}
                            val next=scale*zoom
                            if(time>lastTime)velocity=(next-scale)*1000/(time-lastTime)
                            scale=next;lastTime=time;driver.update(scale)
                            event.changes.forEach {it.consume()}
                        } else if(pinching)break
                        if(isButton) {val point=last/density.density;pressed=point.x>=22 && point.x<=w-22 && point.y>=ctaY && point.y<=ctaY+56}
                    } while(event.changes.any {it.pressed})
                    if(pinching) {driver.end(velocity);buzz();revision++}
                    else if(isButton && pressed)toggle()
                } finally {if(driver.pinch) {driver.end(velocity);revision++};pressed=false}
            }
        }) {
            val sample=frame;val down=pressed
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas;c.save();c.scale(density.density,density.density)
                art.draw(c,w,h,top,bottom,sample,reduced,down);c.restore()
            }
        }
        Box(Modifier.offset(16.dp,(top+6).dp).size(44.dp).semantics {contentDescription="Back";role=Role.Button;onClick {true}})
        Box(Modifier.offset((w/2-50).dp,(top+6).dp).width(100.dp).height(44.dp).semantics {contentDescription="Kitform"})
        Box(Modifier.offset((w-60).dp,(top+6).dp).size(44.dp).semantics {contentDescription="Bag, 1 item";role=Role.Button;onClick {true}})
        Box(Modifier.offset(0.dp,(top+60).dp).fillMaxWidth().height((h*0.56).dp).semantics {role=Role.Image;contentDescription=if(packed)"The kit packed and taped in its box" else "The kit floating apart. Pinch to pack it"})
        if(packed)Box(Modifier.offset((w/2+30).dp,(round(h*0.29)-40).dp).width(90.dp).height(44.dp).semantics {contentDescription="READY TO SHIP"})
        Box(Modifier.offset(22.dp,(ctaY-165).dp).width((w-44).dp).height(165.dp).semantics {contentDescription="BUNDLE · 5 PIECES. Home Gym Starter. Kettlebell 8 kg, adjustable dumbbell, travel mat, 750 ml bottle and a light band. $129. $166. Save 22%"})
        Box(Modifier.offset(22.dp,ctaY.dp).width((w-44).dp).height(56.dp).semantics {role=Role.Button;contentDescription=if(packed)"Unpack the bundle" else "Pack the bundle";onClick {toggle();true}})
        Box(Modifier.offset(22.dp,(ctaY+66).dp).width((w-44).dp).height(16.dp).semantics {contentDescription="Tip: pinch the kit together to pack it yourself"})
    }
}
