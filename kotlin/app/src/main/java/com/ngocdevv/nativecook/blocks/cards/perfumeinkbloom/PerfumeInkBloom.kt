package com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom

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
import kotlinx.coroutines.channels.Channel
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom.PerfumeInkBloomMotion as M

@Composable
fun PerfumeInkBloom(autoPlay: Boolean=false) {
    val context=LocalContext.current; val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { PerfumeInkBloomArt(context) }
    val audio=remember(context) { PerfumeInkBloomAudio(context) }
    val driver=remember(autoPlay,reduced) { PerfumeInkBloomDriver(autoPlay,reduced,{ audio.play(it) },{ view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }) }
    val wake=remember { Channel<Unit>(Channel.CONFLATED) }
    var invalidation by remember { mutableIntStateOf(0) }
    fun changed() { invalidation++; wake.trySend(Unit) }
    fun spray() { driver.spray(); changed() }
    DisposableEffect(audio) { onDispose { audio.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars; controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if(!driver.moving()) { wake.receive(); previous=0L }
                withFrameNanos { nanos ->
                    val dt=if(previous==0L)0.0 else min(.5,(nanos-previous)/1e9)
                    previous=nanos; driver.step(dt); invalidation++
                }
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=remember(maxWidth,maxHeight,top,bottom) { M.Layout(maxWidth.value,maxHeight.value,top,bottom) }
        Canvas(Modifier.fillMaxSize().pointerInput(driver,l) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density.density
                val velocity=VelocityTracker()
                velocity.addPosition(down.uptimeMillis,start)
                val hit=start.x>=l.cx-100*l.sx-10 && start.x<=l.cx+220*l.sx-10 && start.y>=l.capY-70*l.sx && start.y<=l.cy+100*l.sx+10*l.sx
                if(hit) { driver.takeOver(); changed() }
                var end=start; var origin=start; var dragging=false
                do {
                    val event=awaitPointerEvent(); val p=event.changes.firstOrNull { it.id==down.id }?:break
                    end=p.position/density.density
                    velocity.addPosition(p.uptimeMillis,end)
                    if(hit&&p.pressed) {
                        if(!dragging&&hypot(end.x-start.x,end.y-start.y)>=6) {
                            dragging=true; origin=end; driver.beginDrag(); changed()
                        } else if(dragging) {
                            driver.drag((end.x-origin.x).toDouble(),l.sx.toDouble()); changed()
                        }
                    }
                    if(hit)p.consume()
                } while(p.pressed)
                if(hit) { driver.release((end.x-origin.x).toDouble(),velocity.calculateVelocity().x.toDouble(),l.sx.toDouble()); if(!dragging&&hypot(end.x-start.x,end.y-start.y)<=10)spray(); changed() }
            }
        }) {
            @Suppress("UNUSED_VARIABLE") val redraw=invalidation
            drawIntoCanvas { c -> val n=c.nativeCanvas; n.save(); n.scale(density.density,density.density); art.draw(n,l,driver.frame); n.restore() }
        }
        @Composable fun target(x: Float,y: Float,w: Float,h: Float,label: String,button: Boolean=false,action: (() -> Unit)?=null) {
            Box(Modifier.offset(x.dp,y.dp).size(w.dp,h.dp).semantics {
                contentDescription=label; if(button)role=Role.Button
                if(action!=null)onClick { action(); true }
            })
        }
        target(l.cx-100*l.sx-10,l.capY-70*l.sx,320*l.sx,l.cy+100*l.sx-l.capY+80*l.sx,"Azure Tide Bloom. Tap to spray it. Drag sideways to tilt the bottle",true) { spray() }
        target(39*l.sx-22,l.y(83f)-22,44f,44f,"Back",true,{})
        target(358*l.sx-22,l.y(85f)-22,44f,44f,"Bag, 1 item",true,{})
        Box(Modifier.offset((32*l.sx).dp,(l.y(476f)-21).dp).size((l.w-32*l.sx).dp,42.dp).semantics { contentDescription=M.NAME; heading() })
        target(32*l.sx,l.y(513f)-14,200f,30f,"Eau de Parfum")
        target(32*l.sx,l.y(568f)-12,120f,24f,"Reviews")
        target(32*l.sx,l.y(603f)-10.5f,140f,21f,"4 out of 5 stars")
        target(288*l.sx,l.y(567f)-12,80f,24f,"Price")
        target(288*l.sx,l.y(603f)-15,80f,32f,"$148")
        target(32*l.sx,l.y(657f)-12.75f,312*l.sx,80f,M.BODY)
        target(31*l.sx,l.y(754f),77*l.sx,76*l.k,"Save",true,{})
        target(135*l.sx,l.y(754f),235*l.sx,76*l.k,"Buy now",true,{})
    }
}
