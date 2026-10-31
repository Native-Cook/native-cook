package com.ngocdevv.nativecook.blocks.gestures.jellyscaleexchange

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.math.floor

/** The offline jelly shop. Hold to pour, then exchange the pile for a star coin. */
@Composable
fun JellyScaleExchange(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    val driver=remember { JellyScaleExchangeDriver() }
    val artwork=remember(context) { JellyScaleExchangeArtwork(JellyScaleExchangeTheme(context)) }
    DisposableEffect(context) {
        val activity=generateSequence<Context>(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.window?.let { WindowCompat.getInsetsController(it,it.decorView) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null) controller?.isAppearanceLightStatusBars=previous }
    }
    val resolver=context.contentResolver
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> active=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SideEffect { driver.buzz={ success -> haptic.performHapticFeedback(if(success) HapticFeedbackType.Confirm else HapticFeedbackType.TextHandleMove) } }
    LaunchedEffect(autoPlay,reduced) { driver.reset(autoPlay,reduced) }
    LaunchedEffect(active,autoPlay,reduced) {
        if(!active) return@LaunchedEffect
        var previous=0L
        while(true) {
            if(!driver.needsFrames(autoPlay,reduced)) {
                snapshotFlow { driver.revision }.drop(1).first()
                previous=0L
            }
            withFrameNanos { nanos ->
                if(previous!=0L) driver.tick(((nanos-previous)/1e9).coerceAtMost(0.05),autoPlay,reduced)
                previous=nanos
            }
        }
    }
    val top=WindowInsets.safeDrawing.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.safeDrawing.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(driver,reduced) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            driver.takeOver(reduced)
            do { val event=awaitPointerEvent(PointerEventPass.Initial) } while(event.changes.any { it.pressed })
        }
    }) {
        val l=remember(maxWidth,maxHeight,top,bottom) { JellyScaleExchangeMotion.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom) }
        val count by remember { derivedStateOf { driver.revision; if(driver.frame.coin>0.6) 0 else floor(driver.frame.pour+0.0001).toInt() } }
        Canvas(Modifier.fillMaxSize()) {
            driver.revision
            drawIntoCanvas { artwork.draw(it.nativeCanvas,l,driver.frame,density.density,reduced) }
        }
        Box(Modifier.offset(20.dp,(top+10).dp).size((l.w-170).coerceAtLeast(1.0).dp,54.dp)
            .semantics { heading(); contentDescription="Jelly shop. Swap jellies for points" })
        Box(Modifier.offset((l.pillX-60).dp,(l.pillY-20).dp).size(120.dp,40.dp).semantics { contentDescription="Points" })
        Box(Modifier.offset((l.pillX-110).dp,(l.pillY-10).dp).size(44.dp,20.dp)
            .semantics { contentDescription="+80"; liveRegion=LiveRegionMode.Polite })
        Box(Modifier.offset((l.w/2-l.lcdW/2).dp,(l.lcdY-l.lcdH/2).dp).size(l.lcdW.dp,l.lcdH.dp)
            .semantics { contentDescription="$count jellies on the scale"; liveRegion=LiveRegionMode.Polite })
        val bw=(l.w-52)/2
        Box(Modifier.offset(20.dp,l.buttonsTop.dp).size(bw.dp,56.dp)
            .semantics {
                role=Role.Button; contentDescription="Pour jellies. Pours while held"
                onClick("Pour one jelly") { driver.accessibilityPour(reduced); true }
            }
            .pointerInput(driver,reduced,density) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    driver.pressPour(true,reduced); down.consume()
                    try {
                        do {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            if((change.position-down.position).getDistance()>400*density.density) break
                            change.consume()
                        } while(change.pressed)
                    } finally { driver.pressPour(false,reduced) }
                }
            })
        Box(Modifier.offset((l.w-20-bw).dp,l.buttonsTop.dp).size(bw.dp,56.dp)
            .semantics {
                role=Role.Button; contentDescription="Exchange $count jellies"
                if(count==0) disabled()
                onClick { driver.takeOver(reduced); driver.exchange(reduced); true }
            }
            .pointerInput(driver,reduced,density) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    driver.pressSwap(true,reduced); down.consume()
                    var activate=false
                    try {
                        do {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            if((change.position-down.position).getDistance()>10*density.density) break
                            change.consume()
                            if(!change.pressed&&change.uptimeMillis-down.uptimeMillis<=500) activate=true
                        } while(change.pressed)
                    } finally { driver.pressSwap(false,reduced,activate) }
                }
            })
    }
}
