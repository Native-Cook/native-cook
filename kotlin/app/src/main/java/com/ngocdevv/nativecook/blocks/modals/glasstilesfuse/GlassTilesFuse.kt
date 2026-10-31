package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/** Full-screen, offline connect sheet. The demo opts into the deterministic nine-second loop. */
@Composable
fun GlassTilesFuse(autoPlay:Boolean=false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    val driver=remember { GlassTilesFuseDriver() }
    val artwork=remember(context) { GlassTilesFuseArtwork(GlassTilesFuseTheme(context)) }
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
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
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
    SideEffect {
        driver.buzz={ kind -> haptic.performHapticFeedback(when(kind) { 2->HapticFeedbackType.Confirm; 1->HapticFeedbackType.LongPress; else->HapticFeedbackType.TextHandleMove }) }
    }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=remember(maxWidth,maxHeight,top,bottom) { GlassTilesFuseMotion.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom) }
        val connected by remember(autoPlay,reduced) { derivedStateOf { driver.revision; driver.frame(autoPlay,reduced).link>0.56 } }
        Canvas(Modifier.fillMaxSize()) {
            driver.revision
            val f=driver.frame(autoPlay,reduced)
            drawIntoCanvas { artwork.draw(it.nativeCanvas,l,f,density.density) }
        }
        // Semantic nodes stay at the visual positions. Per-frame shader state is read only in draw.
        Box(Modifier.offset(32.dp,l.textTop.dp).size((l.w-64).dp,76.dp).semantics(mergeDescendants=true) {
            liveRegion=LiveRegionMode.Polite
            contentDescription=if(connected) "Mailbox connected. Mina’s inbox is ready to use across your chats and apps."
                else "Mailbox. Connect a Mailbox account for Orbit to use across your chats and apps."
        })
        Box(Modifier.offset(0.dp,(l.sheetTop+24).dp).size(l.w.dp,(l.stageH-24).coerceAtLeast(0.0).dp)
            .semantics {
                contentDescription=if(connected) "Mailbox tile" else "Mailbox tile. Drag to move it. Once connecting, pull it out to finish."
            }
            .pointerInput(driver,l,reduced,autoPlay) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val origin=down.position
                    val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                    driver.begin(autoPlay,reduced)
                    var released=false
                    try {
                        while(!released) {
                            val e=awaitPointerEvent()
                            val change=e.changes.firstOrNull { it.id==down.id } ?: break
                            tracker.addPosition(change.uptimeMillis,change.position)
                            val delta=change.position-origin
                            val velocity=tracker.calculateVelocity()
                            if(change.pressed) driver.drag(delta.x/density.density.toDouble(),delta.y/density.density.toDouble(),velocity.x/density.density.toDouble())
                            else { driver.end(velocity.x/density.density.toDouble(),velocity.y/density.density.toDouble()); released=true }
                            change.consume()
                        }
                    } finally { if(!released) driver.end(0.0,0.0) }
                }
            })
        val interaction=remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) { driver.press(pressed) }
        Box(Modifier.offset(32.dp,l.buttonTop.dp).size((l.w-64).dp,52.dp)
            .semantics { contentDescription=if(connected) "Done" else "Connect Mailbox"; role=Role.Button }
            .clickable(interactionSource=interaction,indication=null,role=Role.Button) { driver.button(autoPlay,reduced) })
    }
}
