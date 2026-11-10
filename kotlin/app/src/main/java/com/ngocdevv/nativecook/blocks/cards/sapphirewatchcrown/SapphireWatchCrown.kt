package com.ngocdevv.nativecook.blocks.cards.sapphirewatchcrown

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun SapphireWatchCrown(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { SapphireWatchCrownArt(context) }
    val audio=remember(context) { SapphireWatchCrownAudio(context) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver=remember(autoPlay,reduced) {
        SapphireWatchCrownDriver(autoPlay,reduced,{ kind ->
            if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(when(kind) { 2 -> VibrationEffect.EFFECT_TICK; 1 -> VibrationEffect.EFFECT_HEAVY_CLICK; else -> VibrationEffect.EFFECT_CLICK }))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==1)18 else 10,if(kind==1)80 else 45))
        },audio::play)
    }
    val invalidation=remember(driver) { mutableLongStateOf(0) }
    var wake by remember(driver) { mutableIntStateOf(0) }
    var selected by remember(driver) { mutableIntStateOf(1) }
    var selectedSize by remember(driver) { mutableIntStateOf(1) }
    var back by remember(driver) { mutableStateOf(false) }
    var pressed by remember { mutableIntStateOf(0) }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    DisposableEffect(audio) { onDispose { audio.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(before!=null)controller.isAppearanceLightStatusBars=before }
    }
    LaunchedEffect(driver,lifecycle,wake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            do {
                withFrameNanos { time ->
                    val dt=(if(previous==0L)0.0 else (time-previous)/1e9).coerceIn(0.0,.5)
                    previous=time; driver.step(dt); invalidation.longValue++
                    selected=driver.frame.selectedMetal; back=driver.frame.flip>.5
                }
            } while(driver.moving)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=remember(maxWidth,maxHeight,top,bottom) { SapphireWatchCrownLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom) }
        val scale=density.density
        fun position(x: Double,y: Double)=Modifier.offset { IntOffset((x*scale).roundToInt(),(y*scale).roundToInt()) }
        fun start() { driver.takeOver(); wake++ }
        Canvas(Modifier.fillMaxSize()) {
            invalidation.longValue
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas
                c.save(); c.scale(scale,scale); art.draw(c,l,driver,scale,pressed); c.restore()
            }
        }
        Box(position(l.cx-l.r*1.2,l.cy-l.r*1.2).size((l.r*2.4).dp)
            .semantics {
                role=Role.Button
                contentDescription=if(back)"The watch from behind, its movement running" else "The watch. Swipe to turn it over"
                onClick { driver.flip(); wake++; true }
            }
            .pointerInput(driver,l) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false); val origin=down.position
                    val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,origin)
                    var dx=0.0
                    down.consume(); start()
                    do {
                        val e=awaitPointerEvent(); val change=e.changes.firstOrNull { it.id==down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        dx=(change.position.x-origin.x)/scale.toDouble(); change.consume()
                    } while(change.pressed)
                    val vx=tracker.calculateVelocity().x/scale
                    if(abs(dx)>40 || abs(vx)>500)driver.turnOver(if(driver.frame.flip>.5)0.0 else 1.0)
                    driver.end(); wake++
                }
            })
        Box(position(l.crownX-30,l.crownY-30).size(60.dp)
            .semantics {
                contentDescription="Crown. Slide along it to set the time"
                customActions=listOf(CustomAccessibilityAction("Increment") { driver.adjust(true); wake++; true },CustomAccessibilityAction("Decrement") { driver.adjust(false); wake++; true })
            }
            .pointerInput(driver,l) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false); val origin=down.position
                    var winding=false
                    down.consume(); start()
                    try {
                        do {
                            val event=awaitPointerEvent(); val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            val dx=(change.position.x-origin.x)/scale.toDouble(); val dy=(change.position.y-origin.y)/scale.toDouble()
                            if(change.pressed) {
                                if(!winding && hypot(dx,dy)>=2) { winding=true; driver.windStart() }
                                if(winding)driver.wind(dx,dy)
                            }
                            change.consume()
                        } while(change.pressed)
                    } finally { driver.end(); wake++ }
                }
            })
        repeat(2) { i ->
            Box(position((if(i==0)26 else 307)*l.sx,l.y(62.0)).size(68.dp,(68*l.k).dp)
                .semantics { contentDescription=if(i==0)"Back" else "Cart, 1 item"; role=Role.Button; onClick { true } }
                .pointerInput(i) { awaitEachGesture { awaitFirstDown(); pressed=i+1; do { val e=awaitPointerEvent() } while(e.changes.any { it.pressed }); pressed=0 } })
        }
        repeat(4) { i ->
            Box(position(SapphireWatchCrownLayout.swatches[i]*l.sx-20,l.y(683.0)-20).size(40.dp)
                .semantics { contentDescription=SapphireWatchCrownArt.metalNames[i]; this.selected=i==selected; role=Role.RadioButton }
                .clickable { driver.pick(i); wake++ })
            Box(position(SapphireWatchCrownLayout.sizes[i]*l.sx-22,l.y(683.0)-20).size(44.dp,40.dp)
                .semantics { contentDescription="${SapphireWatchCrownArt.sizes[i]} millimetres"; this.selected=i==selectedSize; role=Role.RadioButton }
                .clickable { driver.sizeAt=i; selectedSize=i; invalidation.longValue++ })
        }
        Box(position(175*l.sx,l.y(768.0)).size((201*l.sx).dp,(72*l.k).dp)
            .semantics { contentDescription="Buy Online"; role=Role.Button; onClick { true } }
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); pressed=3; do { val e=awaitPointerEvent() } while(e.changes.any { it.pressed }); pressed=0 } })
        for((s,x,y) in listOf(Triple("Store Online",118.0,77.0),Triple("Easy Shopping",118.0,112.0),Triple("Maison Aurel (Swiss Made)",26.0,580.0),Triple("7.9/10",320.0,580.0),Triple("Color",26.0,640.0),Triple("Sizing",228.0,640.0),Triple("$ 450.23",26.0,768.0))) {
            Box(position(x*l.sx,l.y(y)).size(140.dp,24.dp).semantics { contentDescription=s; if(s=="Store Online")heading() })
        }
    }
}
