package com.ngocdevv.nativecook.blocks.onboarding.ribbonroadonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.channels.Channel
import kotlin.math.min

@Composable
fun RibbonRoadOnboarding(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val audio=remember(context,reduced) { if(reduced)null else RibbonRoadOnboardingAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value; val h=maxHeight.value
        val top=WindowInsets.statusBars.getTop(density)/density.density
        val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
        val driver=remember(w,h,reduced) { RibbonRoadOnboardingDriver(w.toDouble(),h.toDouble()) }
        val art=remember(w,h,top,bottom,density.density) { RibbonRoadOnboardingArt(context,w,h,top,bottom,density.density) }
        DisposableEffect(art) { onDispose { art.close() } }
        val tick=remember(driver) { mutableIntStateOf(0) }
        val wake=remember(driver) { Channel<Unit>(Channel.CONFLATED) }
        fun invalidate() { tick.intValue++; wake.trySend(Unit) }
        fun buzz(kind: Int) { view.performHapticFeedback(when(kind) { 0 -> HapticFeedbackConstants.CLOCK_TICK; 1 -> HapticFeedbackConstants.KEYBOARD_TAP; else -> HapticFeedbackConstants.LONG_PRESS }) }
        fun start() { if(driver.start()) { buzz(2); audio?.play("zip") }; invalidate() }
        LaunchedEffect(driver,lifecycle,autoPlay,reduced) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous=0L
                while(true) {
                    // Reduced motion parks the clock after touch-driven springs/button decay settle.
                    if(reduced && !driver.on && driver.z<0 && (driver.clock-driver.last>=3)) {
                        wake.receive(); previous=0L
                    }
                    withFrameNanos { nanos ->
                        val dt=min(if(previous==0L)0.016 else (nanos-previous)/1e9,0.05)
                        previous=nanos; driver.step(dt,autoPlay,reduced); tick.intValue++
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,audio) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun px(x: Float)=x/density.density
                val sx=px(down.position.x); val sy=px(down.position.y)
                val y=h-bottom-80
                val hit=if(sy in y..y+56)when {
                    sx in 24f..w-156 -> 0
                    sx in w-146..w-90 -> 1
                    sx in w-80..w-24 -> 2
                    else -> -1
                } else -1
                driver.pressed=hit
                if(hit<0 && driver.begin(sx.toDouble(),sy.toDouble())) { audio?.play("stretch"); buzz(0) }
                invalidate(); down.consume()
                var ended=false
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        val x=px(change.position.x); val yy=px(change.position.y)
                        if(!change.pressed) {
                            ended=true
                            if(hit>=0) {
                                val inside=yy in y..y+56 && when(hit) { 0 -> x in 24f..w-156; 1 -> x in w-146..w-90; else -> x in w-80..w-24 }
                                if(hit==0 && inside)start()
                            } else if(driver.end()) { audio?.play("twang"); buzz(1) }
                            change.consume(); break
                        }
                        if(hit<0)driver.move(x.toDouble(),yy.toDouble())
                        change.consume(); invalidate()
                    }
                } finally {
                    if(!ended && hit<0 && driver.end()) { audio?.play("twang"); buzz(1) }
                    driver.pressed= -1; invalidate()
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas
                c.save(); c.scale(density.density,density.density); art.draw(c,driver,reduced); c.restore()
            }
        }
        Box(Modifier.offset(28.dp,(top+22).dp).size((w-56).dp,108.dp).semantics { heading(); contentDescription="Try new social connections with our assistance" })
        Box(Modifier.offset(88.dp,(h-bottom-150).dp).size((w-176).dp,42.dp).semantics { contentDescription="Your personal assistant to enrich your social life" })
        for(i in 0..2) {
            val x=when(i) { 0 -> 24f; 1 -> w-146; else -> w-80 }
            Box(Modifier.offset(x.dp,(h-bottom-80).dp).size((if(i==0)w-180 else 56f).dp,56.dp).semantics {
                role=Role.Button
                contentDescription=when(i) { 0 -> "Get started"; 1 -> "Continue with email"; else -> "Continue with a passkey" }
                onClick { if(i==0)start(); true }
            })
        }
    }
}
