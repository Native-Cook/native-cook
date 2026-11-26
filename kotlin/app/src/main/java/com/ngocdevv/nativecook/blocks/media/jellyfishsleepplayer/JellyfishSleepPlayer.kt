package com.ngocdevv.nativecook.blocks.media.jellyfishsleepplayer

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.MotionEvent
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.abs

/** The same photograph and clocks, rendered at the window's physical resolution. No audio service is implied. */
@Composable
fun JellyfishSleepPlayer(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val systemView = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val core = remember { JellyfishSleepPlayerMotion() }
    val heading=remember { View(context).apply {
        isFocusable=true; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityHeading(this,true)
    } }
    val previous=remember { jellyfishSleepPlayerButton(context,"Previous track",core) }
    val play=remember { jellyfishSleepPlayerButton(context,"Pause",core) }
    val next=remember { jellyfishSleepPlayerButton(context,"Next track",core) }
    var textureReady by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    val room: View = remember {
        if(Build.VERSION.SDK_INT>=33) JellyfishSleepPlayerSea(context)
        else JellyfishSleepPlayerGlView(context) { textureReady=true }
    }
    val chrome = remember { JellyfishSleepPlayerChrome(context) }
    fun render() {
        val f=core.out
        if(room is JellyfishSleepPlayerSea) room.show(f)
        if(room is JellyfishSleepPlayerGlView) room.show(f)
        chrome.show(f)
        play.contentDescription=if(f.playing) "Pause" else "Play"
        val shown=if(f.k>0.5) f.b else f.a
        heading.contentDescription=JellyfishSleepPlayerChrome.names[shown]+", "+JellyfishSleepPlayerChrome.metas[shown]
    }
    @Suppress("DEPRECATION") fun skip(dir: Int) {
        val target=core.changeTrack(dir)
        // SEGMENT_TICK is the system's light tick; CLOCK_TICK is available on API 26–33.
        systemView.performHapticFeedback(if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        systemView.announceForAccessibility(JellyfishSleepPlayerChrome.names[target]) // Required explicit track announcement.
    }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(active,reduced,autoPlay,textureReady) {
        core.setReduced(reduced)
        if(!active) { if(room is JellyfishSleepPlayerGlView) room.onPause(); return@LaunchedEffect }
        if(room is JellyfishSleepPlayerGlView) room.onResume()
        if(!textureReady) { render(); return@LaunchedEffect }
        // Even Reduce Motion retains its quiet four-second light breathing.
        while(true) withFrameNanos { now -> core.step(now/1e9,autoPlay); render() }
    }
    DisposableEffect(room) { onDispose { if(room is JellyfishSleepPlayerGlView) room.onPause() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF020C1C))) {
        val w=maxWidth.value; val h=maxHeight.value; val ox=(w-393)/2; val sy=h/852; val top=776*sy
        AndroidView(factory={room},modifier=Modifier.fillMaxSize().clearAndSetSemantics {})
        AndroidView(factory={chrome},modifier=Modifier.fillMaxSize().clearAndSetSemantics {})
        Box(Modifier.offset(0.dp,(190*sy).dp).size(w.dp,(480*sy).dp).clearAndSetSemantics {}.pointerInput(core,density) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                var moveSamples=0
                core.touchDown()
                try {
                    while(true) {
                        val change=awaitPointerEvent().changes.firstOrNull { it.id==down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if(!change.pressed) {
                            val dx=(change.position.x-down.position.x)/density
                            val dy=(change.position.y-down.position.y)/density
                            val measured=tracker.calculateVelocity().x/density
                            // A low-cadence renderer may coalesce a short fling into down/up.
                            // Keep the 500 pt/s threshold when the quadratic tracker lacks samples.
                            val elapsed=maxOf(0.016f,(change.uptimeMillis-down.uptimeMillis)/1000f)
                            val velocity=if(moveSamples<2 && abs(measured)<0.001f) dx/elapsed else measured
                            if(abs(dx)>=abs(dy) && (abs(dx)>=40 || abs(velocity)>500)) skip(if((if(abs(dx)>=40) dx else velocity)<0) 1 else -1)
                            break
                        }; moveSamples++; change.consume()
                    }
                } finally { core.touchUp() }
            }
        })
        AndroidView(factory={heading},modifier=Modifier.offset((ox+32).dp,(676*sy).dp).size(300.dp,(56*sy).dp))
        AndroidView(factory={previous},modifier=Modifier.offset((ox+76).dp,(top+4).dp).size(48.dp),update={it.setOnClickListener { skip(-1) }})
        AndroidView(factory={play},modifier=Modifier.offset((ox+156.5f).dp,top.dp).size(80.dp,56.dp),update={it.setOnClickListener { core.togglePlay() }})
        AndroidView(factory={next},modifier=Modifier.offset((ox+269).dp,(top+4).dp).size(48.dp),update={it.setOnClickListener { skip(1) }})
    }
}

private fun jellyfishSleepPlayerButton(context: android.content.Context,label: String,core: JellyfishSleepPlayerMotion)=Button(context).apply {
    text=""; background=null; stateListAnimator=null; minWidth=0; minHeight=0; minimumWidth=0; minimumHeight=0
    contentDescription=label; isSoundEffectsEnabled=false
    setOnTouchListener { _,event ->
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> core.touchDown()
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> core.touchUp()
        }; false
    }
}
