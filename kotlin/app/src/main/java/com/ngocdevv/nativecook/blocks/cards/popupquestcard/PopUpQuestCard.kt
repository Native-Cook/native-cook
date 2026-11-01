package com.ngocdevv.nativecook.blocks.cards.popupquestcard

import android.app.Activity
import android.content.ContextWrapper
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
fun PopUpQuestCard(autoPlay: Boolean = false) {
    val context=LocalContext.current; val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current.density
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val resources=remember(context) { PopUpQuestCardResources(context) }
    val art=remember(resources) { PopUpQuestCardArt(resources) }
    val driver=remember { PopUpQuestCardDriver() }
    val frame=remember { mutableStateOf(driver.pose) }
    val active=remember { mutableIntStateOf(0) }
    val signal=remember { mutableIntStateOf(0) }
    fun wake() { signal.intValue++ }
    fun buzz(selection: Boolean=false) { view.performHapticFeedback(if(selection)android.view.HapticFeedbackConstants.CLOCK_TICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }; val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(autoPlay,reduced,signal.intValue,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            do {
                withFrameNanos { ns ->
                    val wall=if(previous==0L)0.016 else ((ns-previous)/1e9).coerceIn(0.0,0.5); previous=ns
                    driver.step(wall,autoPlay,reduced); frame.value=driver.pose; active.intValue=driver.active
                }
            } while((autoPlay && !reduced) || !driver.settled)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout=PopUpQuestCardMotion.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom)
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription=if(frame.value.o>0.5)resources.quests[active.intValue].title+" pop-up scene. Drag sideways to look around, drag down to close" else "Recommended for you!"
            customActions=if(frame.value.o>0.5)listOf(CustomAccessibilityAction("Back to quests") { buzz(); driver.close(); wake(); true }) else resources.quests.mapIndexed { i,q -> CustomAccessibilityAction(q.title+", "+q.price) { buzz(); driver.open(i); wake(); true } }
        }.pointerInput(layout,driver) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false); val start=down.position; var end=start
                val wasOpen=driver.pose.o>0.5; val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
                var dragged=false
                val sx=start.x/density; val sy=start.y/density
                val back=PopUpQuestCardMotion.Box(16.0,layout.top+6,44.0,44.0)
                val sheetY=layout.sheetTop+(1-PopUpQuestCardMotion.smooth(0.25,1.0,driver.pose.o))*(layout.h-layout.sheetTop+40)
                val mayPan=wasOpen && sy<min(layout.heroH,sheetY) && !back.contains(sx.toDouble(),sy.toDouble())
                do {
                    val event=awaitPointerEvent(); val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    end=change.position; tracker.addPosition(change.uptimeMillis,end)
                    val tx=(end.x-start.x)/density.toDouble(); val ty=(end.y-start.y)/density.toDouble()
                    if(change.pressed && mayPan) {
                        if(!dragged && hypot(tx,ty)>=8) { driver.begin(tx,ty); dragged=true; wake() }
                        if(dragged) { driver.move(tx,ty,layout.heroH); frame.value=driver.pose }
                    }
                    change.consume()
                } while(change.pressed)
                if(dragged) { val v=tracker.calculateVelocity(); if(driver.end(v.x/density.toDouble(),v.y/density.toDouble(),layout.heroH))buzz(true); wake() }
                else if((end-start).getDistance()/density<8) {
                    if(wasOpen) { if(back.contains(sx.toDouble(),sy.toDouble())) { buzz(); driver.close(); wake() } }
                    else for(i in 0..3)if(layout.card(i).contains(sx.toDouble(),sy.toDouble())) { buzz(); driver.open(i); active.intValue=driver.active; wake() }
                }
            }
        }) {
            val pose=frame.value
            drawIntoCanvas { cv ->
                cv.nativeCanvas.save(); cv.nativeCanvas.scale(density,density)
                art.draw(cv.nativeCanvas,layout,pose,active.intValue,reduced)
                cv.nativeCanvas.restore()
            }
        }
    }
}
