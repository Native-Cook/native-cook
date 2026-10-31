package com.ngocdevv.nativecook.blocks.cards.friendradarmap

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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun FriendRadarMap(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val scale = density.density
    val top = WindowInsets.statusBars.getTop(density)/scale
    val bottom = WindowInsets.navigationBars.getBottom(density)/scale
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art = remember(context) { FriendRadarMapArt(context) }
    val driver = remember(autoPlay,reduced) { FriendRadarMapDriver() }
    val audio = remember(context,reduced) { if(reduced) null else FriendRadarMapAudio(context) }
    val tick = remember { mutableIntStateOf(0) }
    var labelIndex by remember { mutableIntStateOf(0) }
    var ordered by remember { mutableStateOf(false) }
    fun buzz(kind: Int = 0) { view.performHapticFeedback(if(kind==2) HapticFeedbackConstants.CONFIRM else if(kind==1) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK) }
    fun go(dir: Int) { driver.go(dir); audio?.play("glide"); buzz(); tick.intValue++ }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
        activity?.window?.let { WindowCompat.getInsetsController(it,view).isAppearanceLightStatusBars=false }
        onDispose { }
    }
    LaunchedEffect(driver,audio,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var before = 0L
            while (true) {
                withFrameNanos { n ->
                    val dt = if(before==0L) 0.016 else ((n-before)/1e9).coerceIn(0.0,0.5)
                    before=n
                    if(driver.step(dt,autoPlay,reduced)) { audio?.play("ordered"); buzz(2) }
                    labelIndex=driver.frame.labelIndex; ordered=driver.frame.fill>=1
                    tick.intValue++
                }
                if((!autoPlay || reduced) && !driver.holding && driver.frame.fill==0.0 && driver.frame.since>1.3) { delay(50); before=0L }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().semantics {
            val f=FriendRadarMapMotion.friends[labelIndex]
            contentDescription="${f.name.lowercase()} ${f.street.lowercase()}"
            stateDescription="Friend ${labelIndex+1} of 3"
            customActions=listOf(CustomAccessibilityAction("Next friend") { go(1); true },CustomAccessibilityAction("Previous friend") { go(-1); true })
        }.pointerInput(driver,scale,top,bottom,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/scale
                val w=size.width/scale; val h=size.height/scale
                val button=start.x>=20 && start.x<=w-20 && start.y>=h-bottom-86 && start.y<=h-bottom-22
                if(button && driver.frame.fill<1) { driver.holding=true; driver.last=driver.now; buzz(1) }
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                var end=down.position
                var maxX=0f
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        end=change.position; tracker.addPosition(change.uptimeMillis,end)
                        val translation=end/scale-start
                        maxX=max(maxX,abs(translation.x))
                        if(button && (hypot(translation.x,translation.y)>40 || end.x/scale<20 || end.x/scale>w-20 || end.y/scale<h-bottom-86 || end.y/scale>h-bottom-22)) { driver.holding=false; driver.last=driver.now }
                        change.consume()
                    } while(change.pressed)
                } finally {
                    val delta=end/scale-start
                    val velocity=tracker.calculateVelocity().x/scale
                    if(button) { driver.holding=false; driver.last=driver.now }
                    else if(maxX>14) { if(abs(delta.x)>40 || abs(velocity)>500) go(if(delta.x<0) 1 else -1) }
                    else {
                        val selected=FriendRadarMapMotion.friends[driver.frame.cur]
                        for(i in 0..2) {
                            if(i==driver.frame.cur) continue
                            val f=FriendRadarMapMotion.friends[i]
                            val x=w/2+f.x-selected.x; val y=top+h*0.3+f.y-selected.y-34
                            if(abs(end.x/scale-x)<36 && abs(end.y/scale-y)<36) { go(if((i-driver.frame.cur+3)%3==1) 1 else -1); break }
                        }
                    }
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas {
                val c=it.nativeCanvas; c.save(); c.scale(scale,scale)
                art.draw(c,size.width/scale,size.height/scale,top,bottom,driver.frame,reduced)
                c.restore()
            }
        }
        Box(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(64f)).align(androidx.compose.ui.Alignment.BottomCenter).padding(horizontal=androidx.compose.ui.unit.Dp(20f)).offset(y=androidx.compose.ui.unit.Dp(-bottom-22)).semantics {
            role=Role.Button
            contentDescription=if(ordered) "Ride on the way" else "Order a ride to ${FriendRadarMapMotion.friends[labelIndex].name.lowercase()} for 5 dollars, hold to confirm"
        })
    }
}
