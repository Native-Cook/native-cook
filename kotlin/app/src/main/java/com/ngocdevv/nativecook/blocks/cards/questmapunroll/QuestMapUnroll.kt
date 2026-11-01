package com.ngocdevv.nativecook.blocks.cards.questmapunroll

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
fun QuestMapUnroll(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art = remember(context) { QuestMapUnrollArt(context) }
    val driver = remember(autoPlay,reduced) { QuestMapUnrollDriver() }
    var revision by remember { mutableIntStateOf(0) }
    var awake by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(3) }
    fun buzz(arrival: Boolean = false) {
        view.performHapticFeedback(if(arrival) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK)
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if(old!=null) controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver,lifecycle,awake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(!(autoPlay && !reduced) && !awake) return@repeatOnLifecycle
            var previous = 0L
            while(true) {
                withFrameNanos { nanos ->
                    val wall = (if(previous==0L) 0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous=nanos
                    driver.step(wall,autoPlay && !reduced,reduced) { buzz(true) }
                    cardVisible=driver.frame.card>0.001
                    selected=driver.frame.level
                    revision++
                }
                if(!(autoPlay && !reduced) && !driver.moving) { awake=false; break }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val g = remember(maxWidth,maxHeight,top,bottom) { QuestMapUnrollGeometry(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom) }
        Canvas(Modifier.fillMaxSize().pointerInput(driver,g,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val sx=start.x/density.density.toDouble();val sy=start.y/density.density.toDouble()
                val map=g.contains(sx,sy)
                val shopTop=g.height-g.bottom-86
                val shop = if(sy>=shopTop && sy<=g.height-g.bottom-14) (0..3).firstOrNull { i ->
                    val x=16+i*(g.width-114)/3;sx>=x && sx<=x+82
                } ?: -1 else -1
                if(!map && shop<0) return@awaitEachGesture
                if(shop>=0) { driver.pressed=shop;revision++ }
                var end=start;var maxDistance=0.0;var dragged=false
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,start)
                down.consume()
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        end=change.position;tracker.addPosition(change.uptimeMillis,end)
                        val dy=(end.y-start.y)/density.density.toDouble()
                        maxDistance=max(maxDistance,hypot((end.x-start.x)/density.density.toDouble(),dy))
                        if(map && change.pressed) {
                            if(!dragged && abs(dy)>10) { dragged=true;driver.panStart();awake=true }
                            if(dragged) { driver.panUpdate(dy,g);revision++ }
                        }
                        change.consume()
                    } while(change.pressed)
                } finally {
                    if(map) {
                        if(dragged) { driver.panEnd(tracker.calculateVelocity().y/density.density.toDouble(),g);buzz() }
                        else if(maxDistance<=10) driver.tap(end.x/density.density.toDouble(),end.y/density.density.toDouble(),g) { buzz() }
                        awake=true
                    }
                    driver.pressed=-1;revision++
                }
            }
        }) {
            @Suppress("UNUSED_VARIABLE") val invalidate=revision
            drawIntoCanvas {
                val c=it.nativeCanvas
                c.save();c.scale(density.density,density.density)
                art.draw(c,g,driver.frame,reduced,driver.pressed)
                c.restore()
            }
        }
        Box(Modifier.offset(g.x.dp,(g.y-20).dp).size(g.w.dp,(g.h+40).dp).semantics {
            contentDescription="Level map. Drag down to unroll, tap a level to travel there"
            progressBarRangeInfo=ProgressBarRangeInfo(driver.frame.u.toFloat(),0f..1f)
        })
        Box(Modifier.offset(16.dp,(top+18).dp).size(44.dp).semantics { role=Role.Button;contentDescription="Back";onClick { true } })
        Box(Modifier.offset((g.width-60).dp,(top+18).dp).size(44.dp).semantics { role=Role.Button;contentDescription="Settings";onClick { true } })
        Box(Modifier.offset(((g.width-210)/2).dp,(top+18).dp).size(210.dp,44.dp).semantics { contentDescription="Magic power 334" })
        listOf("Daily puzzle","Gift bag","Free hints","Hint shop").forEachIndexed { i,label ->
            Box(Modifier.offset((16+i*(g.width-114)/3).dp,(g.height-g.bottom-86).dp).size(82.dp,72.dp).semantics { role=Role.Button;contentDescription=label;onClick { true } })
        }
        if(cardVisible) {
            val i=selected.coerceIn(0,5)
            Box(Modifier.offset(20.dp,(g.height-g.bottom-172).dp).size((g.width-40).dp,76.dp).semantics {
                liveRegion=LiveRegionMode.Polite
                val n=QuestMapUnrollMotion.stars[i]
                contentDescription="${i+1}. ${QuestMapUnrollMotion.names[i]}. "+if(n>0) "$n of 3 stars" else "New level · 4 spells to learn"
            })
        }
    }
}
