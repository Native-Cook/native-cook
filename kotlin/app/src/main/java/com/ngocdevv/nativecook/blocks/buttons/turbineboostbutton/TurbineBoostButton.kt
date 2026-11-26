package com.ngocdevv.nativecook.blocks.buttons.turbineboostbutton

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.*

@Composable
fun TurbineBoostButton(autoPlay: Boolean=false) {
    val context=LocalContext.current; val view=LocalView.current; val density=LocalDensity.current.density
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active=lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val core=remember { TurbineBoostButtonMotion() }
    var percent by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(Build.VERSION.SDK_INT>=33) }
    fun reducedMotion()=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var reduced by remember { mutableStateOf(reducedMotion()) }
    val room: View=remember { if(Build.VERSION.SDK_INT>=33) TurbineBoostButtonRoom(context) else TurbineBoostButtonGlView(context) { ready=true } }
    val chrome=remember { TurbineBoostButtonChrome(context) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced=reducedMotion() } }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(active,autoPlay,reduced,ready) {
        core.setReduced(reduced); core.last=-1.0
        if(!active) { if(room is TurbineBoostButtonGlView) room.onPause(); return@LaunchedEffect }
        if(room is TurbineBoostButtonGlView) room.onResume()
        if(!ready) return@LaunchedEffect
        var seen=core.ticks
        while(true) withFrameNanos { now ->
            core.step(now/1e6,autoPlay)
            if(room is TurbineBoostButtonRoom) room.show(core.out)
            if(room is TurbineBoostButtonGlView) room.show(core.out)
            chrome.show(core.out); percent=floor(100*core.out.share+.5).toInt()
            repeat(max(0,core.ticks-seen)) {
                view.performHapticFeedback(if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
            }
            seen=core.ticks
            if(core.full) {
                core.full=false
                view.performHapticFeedback(if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                @Suppress("DEPRECATION") view.announceForAccessibility("Full boost")
            }
        }
    }
    DisposableEffect(room) { onDispose { if(room is TurbineBoostButtonGlView) room.onPause() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF000211))) {
        val w=maxWidth.value; val sy=maxHeight.value/852; val ox=(w-393)/2
        AndroidView(factory={ room },modifier=Modifier.fillMaxSize().clearAndSetSemantics {})
        AndroidView(factory={ chrome },modifier=Modifier.fillMaxSize().clearAndSetSemantics {})
        Box(Modifier.offset((ox+28).dp,(70*sy).dp).size(337.dp,(145*sy).dp).semantics { heading(); contentDescription="Volt performance mode, boost" })
        Box(Modifier.offset((ox+194.2f-192).dp,(252.1f*sy+196.5f-192).dp).size(384.dp)
            .semantics { contentDescription="Turbine fan, hold to spool up"; stateDescription="$percent percent" }
            .pointerInput(core,density) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false); val p=down.position/density
                    if(hypot(p.x-192,p.y-192)>192) return@awaitEachGesture
                    core.hold()
                    try { while(true) { val e=awaitPointerEvent(); val c=e.changes.firstOrNull { it.id==down.id } ?: break; c.consume(); if(!c.pressed) break } }
                    finally { core.release() }
                }
            })
        Box(Modifier.offset((ox+32).dp,(793*sy-29).dp).size(329.dp,58.dp)
            .semantics { role=Role.Button; contentDescription="Boost, hold to spool up"; stateDescription="$percent percent"; onClick { core.activate(); true } }
            .pointerInput(core) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false); down.consume(); core.hold()
                    try { while(true) { val e=awaitPointerEvent(); val c=e.changes.firstOrNull { it.id==down.id } ?: break; c.consume(); if(!c.pressed) break } }
                    finally { core.release() }
                }
            })
    }
}
