package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun TinyWorldPicker(autoPlay: Boolean = false) {
    val context=LocalContext.current;val density=LocalDensity.current;val haptics=LocalHapticFeedback.current
    val reduced=remember {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
    val audio=remember(context) {TinyWorldPickerAudio(context)}
    val art=remember(context) {TinyWorldPickerArt(context)}
    val driver=remember {TinyWorldPickerDriver({kind -> haptics.performHapticFeedback(if(kind==0)HapticFeedbackType.TextHandleMove else if(kind==1)HapticFeedbackType.VirtualKey else HapticFeedbackType.LongPress)}, {audio.play(it)})}
    var pressed by remember {mutableIntStateOf(-1)}
    var redraw by remember {mutableIntStateOf(0)}
    var shown by remember {mutableIntStateOf(0)}
    val currentAuto by rememberUpdatedState(autoPlay)
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    DisposableEffect(audio,art) {onDispose {audio.close();art.close()}}
    LaunchedEffect(driver,reduced) {
        var previous=0L
        while(true)withFrameNanos {now ->
            val wall=if(previous==0L)0.016 else (now-previous)/1e9;previous=now
            driver.step(wall,currentAuto,reduced);redraw++
            if(shown!=driver.f.cur)shown=driver.f.cur
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value;val h=maxHeight.value;val cy=round(h*0.47f)
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val tick=redraw
            val c=drawContext.canvas.nativeCanvas
            c.save();c.scale(density.density,density.density)
            art.draw(c,w,h,top,bottom,density.density,driver.f,pressed);c.restore()
        }
        val wd=TinyWorldPickerWorld.all[shown]
        Box(Modifier.offset(y=(cy-170).dp).fillMaxWidth().height(340.dp)
            .semantics {
                contentDescription="World";stateDescription="${wd.name}, ${wd.status.lowercase()}, world ${shown+1} of 4"
                progressBarRangeInfo=ProgressBarRangeInfo(shown.toFloat(),0f..3f,2)
                setProgress {value -> driver.goTo(value.roundToInt().coerceIn(0,3),reduced);true}
                customActions=listOf(CustomAccessibilityAction("Next world") {driver.goTo(shown+1,reduced);true},CustomAccessibilityAction("Previous world") {driver.goTo(shown-1,reduced);true})
            }.pointerInput(driver,w,reduced) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false);driver.begin()
                    val start=down.position;val startTime=down.uptimeMillis
                    val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                    var dragging=false;var failed=false;var last=start;var activationX=0f
                    do {
                        val ev=awaitPointerEvent();val change=ev.changes.firstOrNull {it.id==down.id}?:break
                        last=change.position;tracker.addPosition(change.uptimeMillis,change.position)
                        val dx=(last.x-start.x)/density.density;val dy=(last.y-start.y)/density.density
                        if(!dragging) {
                            if(abs(dy)>24)failed=true
                            if(!failed && abs(dx)>8) {dragging=true;activationX=dx;driver.start()}
                        }
                        if(dragging) {driver.update((dx-activationX).toDouble(),w.toDouble(),reduced);change.consume()}
                        if(!change.pressed) {
                            if(dragging)driver.end((tracker.calculateVelocity().x/density.density).toDouble(),w.toDouble(),reduced)
                            else if(change.uptimeMillis-startTime<=260)driver.tap(reduced)
                            break
                        }
                    } while(true)
                }
            })
        @Composable fun hit(label: String,x: Float,y: Float,width: Float,height: Float,pressedIndex: Int = -1,action: ()->Unit={}) {
            val interactions=remember {MutableInteractionSource()}
            val down by interactions.collectIsPressedAsState()
            LaunchedEffect(down) {if(down && pressedIndex>=0)pressed=pressedIndex else if(pressed==pressedIndex)pressed=-1}
            Box(Modifier.offset(x=(x-width/2).dp,y=(y-height/2).dp).size(width.dp,height.dp).clickable(interactionSource=interactions,indication=null,onClick=action).semantics {contentDescription=label;role=Role.Button})
        }
        TinyWorldPickerWorld.all.forEachIndexed {i,world -> hit("${world.name}, world ${i+1} of 4",w/2-45+i*30,cy+274,30f,44f) {driver.goTo(i,reduced)} }
        hit("Snapshot",w-38,cy-154,44f,44f,pressedIndex=0);hit("Rewards",w-38,cy-98,44f,44f,pressedIndex=1);hit("World map",w-38,cy-42,44f,44f,pressedIndex=2)
        hit("World info",42f,h-bottom-40,52f,52f,pressedIndex=3);hit("Collection, 2 new",w-42,h-bottom-40,52f,52f,pressedIndex=4)
        hit("${wd.cta}, ${wd.name}",w/2,h-bottom-40,w-160,56f,pressedIndex=5)
        Box(Modifier.offset(16.dp,(top+6).dp).size(42.dp).semantics {contentDescription="Level 20"})
        Box(Modifier.offset(66.dp,(top+11).dp).size(145.dp,32.dp).semantics {contentDescription="Energy 17,963"})
        Box(Modifier.offset((w-156).dp,(top+6).dp).size(140.dp,32.dp).semantics {contentDescription="13,401,935 coins"})
        Box(Modifier.offset((w-176).dp,(top+46).dp).size(160.dp,32.dp).semantics {contentDescription="18,313 of 20,000 stars"})
        Box(Modifier.offset(24.dp,(cy+140).dp).size((w-48).dp,80.dp).semantics {contentDescription="${wd.name}, ${wd.status}";heading()})
        Box(Modifier.offset(24.dp,(cy+226).dp).size((w-48).dp,20.dp).semantics {contentDescription=wd.stats.mapIndexed {i,s -> "${listOf("best run","tasks","chests")[i]} $s"}.joinToString(", ")})
    }
}
