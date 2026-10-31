package com.ngocdevv.nativecook.blocks.modals.gemcutpaywall

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToInt

@Composable
fun GemCutPaywall(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val view=LocalView.current
    val haptic=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reducedState=remember(context){mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)}
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {reducedState.value=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {context.contentResolver.unregisterContentObserver(observer)}
    }
    val reduced=reducedState.value
    val audio=remember(context){GemCutPaywallAudio(context)}
    val driver=remember(autoPlay,reduced,audio,haptic) { GemCutPaywallDriver(autoPlay,reduced,audio) { kind ->
        haptic.performHapticFeedback(when(kind){0->HapticFeedbackType.TextHandleMove;2->HapticFeedbackType.Confirm;else->HapticFeedbackType.LongPress})
    } }
    val artwork=remember(context){GemCutPaywallArtwork(context)}
    val clock=remember(driver){mutableDoubleStateOf(GemCutPaywallDriver.now())}
    val wake=remember(driver){Channel<Unit>(Channel.CONFLATED)}
    DisposableEffect(audio){onDispose {audio.close()}}
    DisposableEffect(view) {
        val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose {if(old!=null)controller.isAppearanceLightStatusBars=old}
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Keep the loop's time stationary while the activity is backgrounded.
            val entered=GemCutPaywallDriver.now()
            if(entered-driver.tickTime>0.1){val gap=entered-driver.tickTime;driver.origin+=gap;driver.cutMove=driver.cutMove?.let {it.copy(start=it.start+gap)};driver.pitchMove=driver.pitchMove?.let {it.copy(start=it.start+gap)};driver.lastTouch+=gap;driver.flareTime+=gap;driver.tierTime+=gap}
            driver.tickTime=entered
            while(true) {
                if(reduced&&!driver.isMoving)wake.receive()
                withFrameNanos {clock.doubleValue=GemCutPaywallDriver.now()}
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val l=GemCutPaywallTheme.layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom)
        fun step(delta: Int): Boolean {
            val t=GemCutPaywallDriver.now();driver.takeover(t);driver.go((driver.tier+delta).coerceIn(0,2),t);wake.trySend(Unit);clock.doubleValue=t;return true
        }
        Canvas(Modifier.fillMaxSize().pointerInput(driver,l) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val sx=start.x/density.density;val sy=start.y/density.density
                val plan=sy>=l.planTop&&sy<=l.planTop+48&&sx>=16&&sx<=l.w-16
                val gem=sy<l.titleTop&&!(sy>=l.barTop&&sy<=l.barTop+44)
                val button=when {
                    sy>=l.ctaTop&&sy<=l.ctaTop+56&&sx>=16&&sx<=l.w-16 -> 2
                    sy>=l.barTop&&sy<=l.barTop+44&&sx>=8&&sx<=52 -> 0
                    sy>=l.barTop&&sy<=l.barTop+44&&sx>=l.w-92&&sx<=l.w-16 -> 1
                    else -> -1
                }
                if(button>=0) {
                    driver.press(button,true,GemCutPaywallDriver.now());wake.trySend(Unit)
                    try {
                        while(true) {val change=awaitPointerEvent().changes.firstOrNull {it.id==down.id}?:break;change.consume();if(!change.pressed)break}
                    } finally {driver.press(button,false,GemCutPaywallDriver.now());wake.trySend(Unit)}
                    return@awaitEachGesture
                }
                if(!plan&&!gem)return@awaitEachGesture
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                driver.begin(plan,GemCutPaywallDriver.now());wake.trySend(Unit)
                var last=start
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull {it.id==down.id}?:break
                        last=change.position;tracker.addPosition(change.uptimeMillis,last)
                        if(!change.pressed) { change.consume();break }
                        driver.drag(plan,((last.x-start.x)/density.density).toDouble(),((last.y-start.y)/density.density).toDouble(),GemCutPaywallDriver.now(),l)
                        change.consume()
                    }
                } finally {
                    val velocity=tracker.calculateVelocity()
                    driver.end(plan,(last.x/density.density-16).toDouble(),((last.x-start.x)/density.density).toDouble(),((last.y-start.y)/density.density).toDouble(),(velocity.x/density.density).toDouble(),(velocity.y/density.density).toDouble(),GemCutPaywallDriver.now(),l)
                    wake.trySend(Unit)
                }
            }
        }) {
            // The clock is read only in draw; the screen does not recompose on every frame.
            val now=clock.doubleValue
            val frame=driver.sample(now)
            drawIntoCanvas { artwork.draw(it.nativeCanvas,l,frame,driver,now,density.density) }
        }
        Box(Modifier.offset(0.dp,0.dp).size(l.w.dp,l.titleTop.coerceAtLeast(0.0).dp).semantics {
            contentDescription=listOf("A frosted rough stone","A gem with a single cut","A round brilliant diamond")[driver.tier]
            role=Role.Image
            onClick("Tap to flare the gem") {val t=GemCutPaywallDriver.now();driver.begin(false,t);driver.end(false,0.0,0.0,0.0,0.0,0.0,t,l);wake.trySend(Unit);true}
        })
        Box(Modifier.offset(16.dp,l.planTop.dp).size(l.planW.dp,48.dp).semantics {
            contentDescription="Plan";stateDescription="${GemCutPaywallTheme.names[driver.tier]}, $${GemCutPaywallTheme.prices[driver.tier]} a month"
            progressBarRangeInfo=ProgressBarRangeInfo(driver.tier.toFloat(),0f..2f,1)
            setProgress {value->val t=GemCutPaywallDriver.now();driver.takeover(t);driver.go(value.roundToInt().coerceIn(0,2),t);wake.trySend(Unit);true}
            customActions=listOf(CustomAccessibilityAction("Increment"){step(1)},CustomAccessibilityAction("Decrement"){step(-1)})
        })
        Box(Modifier.offset(8.dp,l.barTop.dp).size(44.dp).semantics {contentDescription="Close";role=Role.Button;onClick {true}})
        Box(Modifier.offset((l.w-92).dp,l.barTop.dp).size(76.dp,44.dp).semantics {contentDescription="Restore purchases";role=Role.Button;onClick {true}})
        Box(Modifier.offset(0.dp,l.titleTop.dp).size(l.w.dp,34.dp).semantics {contentDescription="Facet ${GemCutPaywallTheme.names[driver.tier]}";liveRegion=LiveRegionMode.Polite})
        Box(Modifier.offset(0.dp,l.priceTop.dp).size(l.w.dp,22.dp).semantics {contentDescription="$${GemCutPaywallTheme.prices[driver.tier]} a month"})
        Box(Modifier.offset(16.dp,l.noteTop.dp).size((l.w-32).dp,18.dp).semantics {contentDescription=GemCutPaywallTheme.notes[driver.tier]})
        for(i in 0..2)Box(Modifier.offset(32.dp,(l.featTop+i*36).dp).size((l.w-64).dp,24.dp).semantics {contentDescription="${GemCutPaywallTheme.features[i]}, ${if(driver.tier>=if(i==2)2 else 1)"included" else "not included"}"})
        Box(Modifier.offset(16.dp,l.ctaTop.dp).size((l.w-32).dp,56.dp).semantics {contentDescription=if(driver.tier==0)"Continue with Free" else "Start 7-day free trial";role=Role.Button;onClick {true}})
    }
}
