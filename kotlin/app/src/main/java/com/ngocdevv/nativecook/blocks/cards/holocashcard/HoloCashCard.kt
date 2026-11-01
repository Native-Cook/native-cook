package com.ngocdevv.nativecook.blocks.cards.holocashcard

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun HoloCashCard(autoPlay:Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val metrics=LocalDensity.current
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val state=remember { HoloCashCardMotion.State() }
    val art=remember(context) { HoloCashCardArt(context) }
    val audio=remember(context,reduced) { if(reduced)null else HoloCashCardAudio(context) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val tick=remember { mutableIntStateOf(0) }
    val pressed=remember { mutableIntStateOf(0) }
    var hidden by remember { mutableStateOf(false) }
    var boosted by remember { mutableStateOf(false) }
    fun buzz(kind:Int) {
        if(Build.VERSION.SDK_INT>=29) {
            vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==0)VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
        } else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==2)20L else 12L,45))
    }
    fun toggleBoost() {
        state.last=state.clock; state.changeBoost(!state.boost); boosted=state.boost
        audio?.play(if(state.boost)"boost" else "tap"); buzz(if(state.boost)2 else 0)
    }
    fun addFifty() {
        state.last=state.clock; state.extra+=5000000; state.value+=5000000; audio?.play("tap"); buzz(2)
    }
    fun hideBalance() { state.hideTarget=if(hidden)0.0 else 1.0; hidden=!hidden; buzz(0) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(art) { onDispose { art.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(state,autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) withFrameNanos { nanos ->
                val dt=min(if(previous==0L)0.016 else (nanos-previous)/1e9,0.05)
                previous=nanos; state.step(dt,autoPlay,reduced)
                if(boosted!=state.boost)boosted=state.boost
                tick.intValue++
            }
        }
    }
    val topInset=WindowInsets.statusBars.getTop(metrics)/metrics.density
    val bottomInset=WindowInsets.navigationBars.getBottom(metrics)/metrics.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=HoloCashCardLayout(maxWidth.value,maxHeight.value,topInset,bottomInset)
        Canvas(Modifier.fillMaxSize().pointerInput(state,l,audio) {
            fun target(x:Float,y:Float):Int {
                if(x>= -8 && x<=l.w+8 && y>=l.cardY-26 && y<=l.cardY+l.ch+26)return 1
                if(x>=l.w-112 && x<=l.w-56 && y>=l.top-8 && y<=l.top+48)return 2
                if(x>=l.w-66 && x<=l.w-10 && y>=l.top-8 && y<=l.top+48)return 3
                if(x>=l.w-94 && x<=l.w-25 && y>=l.balanceY+32 && y<=l.balanceY+90)return 4
                if(x>=18 && x<=l.w-18 && y>=l.actionsY && y<=l.actionsY+50)return if(x<l.w/2)5 else 6
                return 0
            }
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position; val hit=target(start.x/metrics.density,start.y/metrics.density)
                if(hit==0)return@awaitEachGesture
                pressed.intValue=hit
                if(hit==1) {
                    state.last=state.clock; state.on=true; state.son=false; state.tx=state.rx; state.ty=state.ry
                    audio?.play("shimmer")
                }
                down.consume()
                var end=start; var released=false
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id }?:break
                        end=change.position
                        if(hit==1 && change.pressed) {
                            state.last=state.clock
                            state.tx=(-(end.y-start.y)/metrics.density*0.14).coerceIn(-16.0,16.0)
                            state.ty=((end.x-start.x)/metrics.density*0.12).coerceIn(-16.0,16.0)
                        }
                        released=!change.pressed
                        change.consume()
                    } while(change.pressed && event.changes.any { it.pressed })
                } finally {
                    pressed.intValue=0
                    if(hit==1) { state.on=false; state.last=state.clock; buzz(1) }
                    else if(released && target(end.x/metrics.density,end.y/metrics.density)==hit) {
                        when(hit) { 2->hideBalance(); 4->toggleBoost(); 5->addFifty() }
                    }
                }
            }
        }) {
            tick.intValue // Read only in the drawing phase; no screen recomposition per frame.
            drawIntoCanvas { art.draw(it.nativeCanvas,l,state,metrics.density,pressed.intValue) }
        }
        fun Modifier.box(x:Float,y:Float,w:Float,h:Float)=offset(x.dp,y.dp).size(w.dp,h.dp)
        Box(Modifier.box(l.w-104,l.top,40f,40f).semantics {
            role=Role.Button; contentDescription=if(hidden)"Show balance" else "Hide balance"
            onClick { hideBalance(); true }
        })
        Box(Modifier.box(l.w-58,l.top,40f,40f).semantics {
            role=Role.Button; contentDescription="Support chat"; onClick { true }
        })
        Box(Modifier.box(18f,l.cardY,l.cw,l.ch).semantics {
            role=Role.Image; contentDescription="LOUDCASH debit card"
        })
        Box(Modifier.box(18f,l.balanceY+42,l.cw,42f).semantics {
            contentDescription=if(hidden)"Balance hidden" else "Balance 5,020 dollars and 20 cents, accruing"
        })
        Box(Modifier.box(l.w-84,l.balanceY+42,49f,38f).semantics {
            role=Role.Switch; contentDescription="Boost rewards to 8 percent"
            toggleableState=if(boosted)ToggleableState.On else ToggleableState.Off
            onClick { toggleBoost(); true }
        })
        Box(Modifier.box(18f,l.actionsY,(l.cw-10)/2,50f).semantics {
            role=Role.Button; contentDescription="Add 50 dollars"; onClick { addFifty(); true }
        })
        Box(Modifier.box(18+(l.cw+10)/2,l.actionsY,(l.cw-10)/2,50f).semantics {
            role=Role.Button; contentDescription="Withdraw"; onClick { true }
        })
    }
}
