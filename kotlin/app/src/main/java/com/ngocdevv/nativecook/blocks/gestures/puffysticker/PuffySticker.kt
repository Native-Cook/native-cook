package com.ngocdevv.nativecook.blocks.gestures.puffysticker

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun PuffySticker(autoPlay: Boolean = false) {
    val context=LocalContext.current
    DisposableEffect(context) {
        val window=(context as? Activity)?.window
        val controller=window?.let {WindowCompat.getInsetsController(it,it.decorView)}
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose {if(previous!=null) controller?.isAppearanceLightStatusBars=previous}
    }
    val density=LocalDensity.current.density
    val haptics=LocalHapticFeedback.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {context.contentResolver.unregisterContentObserver(observer)}
    }
    val audio=remember(context) { PuffyStickerAudio(context) }
    DisposableEffect(audio) { onDispose {audio.close()} }
    val state=remember(audio,haptics) { PuffyStickerState(audio::play,audio::stop) { kind -> haptics.performHapticFeedback(when(kind) {"select"->HapticFeedbackType.TextHandleMove;"success"->HapticFeedbackType.Confirm;else->HapticFeedbackType.LongPress}) } }
    val renderer=remember(context) { PuffyStickerRenderer(context) }
    var clock by remember { mutableFloatStateOf(0f) }
    val owner=LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ -> active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED);if(!active) audio.stop("inflate") }
        owner.lifecycle.addObserver(observer);onDispose {owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(autoPlay,reduced) {
        state.auto=autoPlay;state.reduced=reduced;state.manual=!(autoPlay && !reduced);state.autoStart=state.time
        if(reduced) {audio.stop("inflate");state.squish.set(0f);state.touch=0f;state.tapPress=0f;state.flatPress=0f;state.axis=0f}
    }
    LaunchedEffect(active) {
        if(!active) return@LaunchedEffect
        var previous=0L
        while(isActive) withFrameNanos { now ->
            val dt=if(previous==0L) .016f else min(.05f,(now-previous)/1_000_000_000f)
            previous=now;state.frame(state.time+dt);clock=state.time
        }
    }
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(PuffyStickerTheme.PAGE))) {
        val layout=PuffyStickerTheme.Layout(maxWidth.value,maxHeight.value,top,bottom)
        val selected by remember { derivedStateOf { clock;state.shape.value.roundToInt().coerceIn(0,3) } }
        val puffy by remember { derivedStateOf { clock;state.puff.value>.5f } }
        Canvas(Modifier.fillMaxSize()) {
            clock // Redraw only the Canvas for per-frame state.
            drawIntoCanvas { renderer.draw(it.nativeCanvas,layout,state,density) }
        }
        Box(Modifier.offset(16.dp,layout.barTop.dp).width(70.dp).height(44.dp).clickable(role=Role.Button,onClick=state::cancel).semantics {contentDescription="Cancel"})
        Box(Modifier.offset((layout.w-16-renderer.doneWidth).dp,layout.barTop.dp).width(renderer.doneWidth.dp).height(44.dp).clickable(role=Role.Button,onClick=state::done).semantics {contentDescription="Done"})
        Box(Modifier.offset(90.dp,layout.barTop.dp).width((layout.w-180).dp).height(44.dp).semantics {heading();contentDescription="New sticker"})
        Box(Modifier.offset((layout.stageX-layout.sticker*1.1f).dp,(layout.stageY-layout.sticker*1.1f).dp).size((layout.sticker*2.2f).dp)
            .semantics {
                contentDescription="${PuffyStickerTheme.labels[selected]} sticker, ${if(puffy) "puffy" else "flat"}. Hold to puff it up, drag to move it."
                stateDescription=if(puffy) "Puffy" else "Flat"
                onClick("Toggle puff") {state.setPuff(!puffy);true}
                progressBarRangeInfo=ProgressBarRangeInfo(if(puffy) 1f else 0f,0f..1f)
                setProgress {state.setPuff(it>.5f);true}
            }.pointerInput(state,density) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false);down.consume();state.begin()
                    val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                    var canceled=true
                    try {
                        while(true) {
                            val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                            tracker.addPosition(change.uptimeMillis,change.position)
                            val speed=tracker.calculateVelocity()
                            if(!change.pressed) {state.end(speed.x/density,speed.y/density);canceled=false;break}
                            val delta=(change.position-down.position)/density
                            state.drag(delta.x,delta.y,speed.x/density,speed.y/density);change.consume()
                        }
                    } finally {if(canceled) state.end(0f,0f)}
                }
            })
        Box(Modifier.offset(0.dp,layout.hintTop.dp).fillMaxWidth().height(20.dp).semantics { contentDescription=if(puffy) "Drag it, or pick another shape" else "Hold the sticker to puff it up" })
        Row(Modifier.offset((layout.w/2-100).dp,layout.switchTop.dp).size(200.dp,40.dp).selectableGroup().padding(4.dp)) {
            for(i in 0..1) Box(Modifier.size(96.dp,32.dp).selectable(selected=(i==1)==puffy,role=Role.RadioButton,onClick={state.setPuff(i==1)}).semantics {contentDescription=if(i==0) "Flat" else "Puffy"})
        }
        Row(Modifier.offset(layout.trayLeft.dp,layout.trayTop.dp).size(304.dp,64.dp).pointerInput(state,density) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false);state.takeOver();state.thumbDown=(down.position.x/density/80).toInt().coerceIn(0,3)
                var scrub=false
                try { while(true) {
                    val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                    if(abs(change.position.x-down.position.x)/density>6) scrub=true
                    if(!change.pressed) {
                        state.thumbDown=-1
                        state.lastTouch=state.time
                        if(scrub) state.snapShape()
                        break
                    }
                    if(scrub) { state.thumbDown=-1;state.scrub((change.position.x/density-32)/80);change.consume() }
                } } finally {state.thumbDown=-1}
            }
        },horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            for(i in 0..3) Box(Modifier.size(64.dp).clickable(role=Role.Button) {state.goShape(i)}.semantics {contentDescription=PuffyStickerTheme.labels[i];this.selected=selected==i})
        }
    }
}
