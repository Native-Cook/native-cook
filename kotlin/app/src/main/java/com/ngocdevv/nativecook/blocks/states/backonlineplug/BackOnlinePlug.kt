package com.ngocdevv.nativecook.blocks.states.backonlineplug

import android.database.ContentObserver
import android.os.Build
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.*

@Composable
fun BackOnlinePlug(autoPlay: Boolean = false) {
    val context = LocalContext.current; val view = LocalView.current; val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.value.isAtLeast(Lifecycle.State.RESUMED)
    val art = remember(context) { BackOnlinePlugArt(context) }
    val engine = remember { BackOnlinePlugEngine() }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f) }
    var revision by remember { mutableLongStateOf(0) }
    var online by remember { mutableStateOf(false) }
    var engaged by remember { mutableStateOf(false) }
    var fadeRequest by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val feedback = remember(view) { {
        view.performHapticFeedback(if(Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        @Suppress("DEPRECATION")
        view.announceForAccessibility("Back online")
    } }
    fun toggle() {
        if(reduced) { engine.fadeFrom = engine.fade; engine.fadeTo = if(engine.fade < .5) 1.0 else 0.0; engine.fadeElapsed = 0.0; engine.fadeAt = System.nanoTime(); fadeRequest++; online = engine.fadeTo == 1.0; if(online) feedback() }
        else { engaged = true; engine.touch.takeOver(engine.clock); engine.touch.toggle(engine.clock) }
        revision++
    }
    LaunchedEffect(reduced) {
        online = if(reduced) engine.fadeTo >= .5 else (engine.touch.manual || autoPlay) && engine.touch.online(engine.clock) && (!engine.touch.manual || engine.clock >= engine.touch.vAt)
    }
    LaunchedEffect(active,reduced,autoPlay,engaged) {
        if(!active || reduced || (!autoPlay && !engaged)) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while(true) {
            val now = withFrameNanos { it }; val dt = max(0.0,(now-previous)/1e9); previous = now
            engine.clock += dt; engine.touch = engine.touch.step(engine.clock,min(.1,dt),autoPlay)
            val live = (engine.touch.manual || autoPlay) && engine.touch.online(engine.clock) && (!engine.touch.manual || engine.clock >= engine.touch.vAt)
            if(live != online) { online = live; if(live && engine.touch.manual) feedback() }
            revision++
        }
    }
    // A separate fade clock starts on each reduced-motion activation and stops after 200 ms.
    LaunchedEffect(reduced,fadeRequest,active) {
        if(!reduced || !active) return@LaunchedEffect
        while(engine.fadeElapsed < .2) {
            withFrameNanos { }
            // Start at the user's action, not at the first available frame under rendering load.
            engine.fadeElapsed = (System.nanoTime()-engine.fadeAt)/1e9
            engine.fade = engine.fadeFrom+(engine.fadeTo-engine.fadeFrom)*BackOnlinePlugMotion.clamp(engine.fadeElapsed/.2); revision++
        }
    }
    // Read the value in composition so the semantics modifier is rebuilt on a state change.
    val semanticOnline = online
    val headingLabel = if(semanticOnline) "Back online. Your 3 drafts are syncing now." else "Still offline. Plug back in to sync 3 drafts."
    val actionLabel = if(semanticOnline) "Disconnect" else "Reconnect"
    val actionValue = if(semanticOnline) "Online" else "Offline"
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = BackOnlinePlugArt.Layout(maxWidth.value,maxHeight.value)
        Canvas(Modifier.fillMaxSize().pointerInput(reduced,l.width,l.height) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                val x = down.position.x/density; val y = down.position.y/density
                val onButton = l.buttonHit(x,y)
                if(!reduced) { engaged = true; engine.touch.takeOver(engine.clock) }
                val onPlug = l.plugHit(x,y,if(reduced) (if(engine.fade < .5) BackOnlinePlugMotion.off else BackOnlinePlugMotion.on) else engine.touch.frame(engine.clock))
                if(!reduced) { if(onPlug && engine.touch.canGrab(engine.clock)) engine.touch.grab(engine.clock); engine.touch.touching = true; engine.touch.lastTouch = engine.clock }
                down.consume(); var moved = 0.0; var upTime = down.uptimeMillis; var cancelled = true
                try {
                    while(true) {
                        val event = awaitPointerEvent(); val p = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = (p.position.x-down.position.x)/density; val dy = (p.position.y-down.position.y)/density
                        moved = max(moved,hypot(dx.toDouble(),dy.toDouble())); upTime = p.uptimeMillis
                        if(!reduced && onPlug && engine.touch.pk == 1) engine.touch.drag(dx.toDouble(),dy.toDouble(),engine.clock)
                        p.consume(); revision++
                        if(!p.pressed) { cancelled = false; break }
                    }
                } finally {
                    val tap = !cancelled && upTime-down.uptimeMillis < 300 && moved < 10
                    if(reduced) { if(tap && (onPlug || (onButton && engine.fade < .5))) toggle() }
                    else {
                        if(onPlug && engine.touch.pk == 1) engine.touch.release(engine.clock,tap)
                        else if(tap && onPlug) engine.touch.toggle(engine.clock)
                        else if(tap && onButton && engine.touch.vk != 1) engine.touch.toggle(engine.clock)
                        engine.touch.touching = false; engine.touch.lastTouch = engine.clock
                    }
                    revision++
                }
            }
        }) {
            @Suppress("UNUSED_VARIABLE") val redraw = revision
            drawIntoCanvas { target ->
                val c = target.nativeCanvas; c.save(); c.scale(density,density)
                if(reduced) {
                    art.draw(c,l,BackOnlinePlugMotion.off)
                    if(engine.fade > 0) { c.saveLayerAlpha(0f,0f,l.width,l.height,(engine.fade*255).roundToInt()); art.draw(c,l,BackOnlinePlugMotion.on); c.restore() }
                } else art.draw(c,l,if(engine.touch.manual || autoPlay) engine.touch.frame(engine.clock) else BackOnlinePlugMotion.off)
                c.restore()
            }
        }
        Box(Modifier.offset((l.ox+30).dp,(100*l.fy).dp).size(300.dp,(300*l.fy).dp).semantics {
            heading(); contentDescription = headingLabel
        })
        Box(Modifier.offset((l.ox+32).dp,(772*l.fy).dp).size(if(semanticOnline) 160.dp else 329.dp,if(semanticOnline) 44.dp else 58.dp).semantics {
            role = Role.Button; contentDescription = actionLabel; stateDescription = actionValue
            onClick { toggle(); true }
        })
    }
}

private class BackOnlinePlugEngine {
    var clock = 0.0; var touch = BackOnlinePlugMotion.Touch()
    var fade = 0.0; var fadeFrom = 0.0; var fadeTo = 0.0; var fadeElapsed = .2
    var fadeAt = 0L
}
