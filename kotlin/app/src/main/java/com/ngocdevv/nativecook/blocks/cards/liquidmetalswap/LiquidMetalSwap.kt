package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.first
import kotlin.math.min

@Composable
fun LiquidMetalSwap(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val view=LocalView.current
    val haptic=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var reduced by remember(context) { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val audio=remember(context,reduced) { if(reduced) null else LiquidMetalSwapAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    val currentAudio by rememberUpdatedState(audio)
    val driver=remember(autoPlay,haptic) {
        LiquidMetalSwapDriver(autoPlay && !reduced,reduced,{ currentAudio?.play(it) }, { kind ->
            haptic.performHapticFeedback(when(kind) {
                1 -> HapticFeedbackType.LongPress
                2 -> HapticFeedbackType.Confirm
                else -> HapticFeedbackType.ContextClick
            })
        })
    }
    LaunchedEffect(driver,reduced,autoPlay) { driver.configure(autoPlay,reduced) }
    val artwork=remember(context) { LiquidMetalSwapArtwork(context) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(old!=null) controller?.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if(!driver.needsFrames) {
                    val last=driver.revision
                    snapshotFlow { driver.revision }.first { it!=last }
                    previous=0L
                }
                withFrameNanos { now ->
                    val dt=min(0.05,if(previous==0L) 0.016 else (now-previous)/1e9)
                    previous=now; driver.step(dt)
                }
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=LiquidMetalSwapLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top.toDouble(),bottom.toDouble())
        Canvas(Modifier.fillMaxSize()) {
            // Snapshot reads happen in draw, never in the whole-screen composition.
            driver.revision
            drawIntoCanvas { artwork.draw(it.nativeCanvas,l,driver,density.density) }
        }
        Box(Modifier.offset(x=l.side.dp,y=l.barTop.dp).size(100.dp,44.dp).semantics { contentDescription="Swap"; heading() })
        Box(Modifier.offset(x=(l.w-160).dp,y=l.barTop.dp).size(144.dp,44.dp).semantics { contentDescription="Slippage 0.5%" })
        for((i,caption) in listOf("Rate","Price impact","Network fee").withIndex()) {
            Box(Modifier.offset(x=(l.side+4).dp,y=(l.rowsTop+i*36).dp).size((l.cardW-8).dp,36.dp).semantics {
                driver.revision
                val role=if(driver.frame.flip>0.5) 1 else 0
                val value=when(i) { 0 -> LiquidMetalSwapTheme.rates[role]; 1 -> "0.08%"; else -> "$0.12" }
                contentDescription="$caption, $value"
            })
        }
        Box(Modifier.offset(y=l.stageTop.dp).size(l.w.dp,l.stageH.dp)
            .semantics {
                driver.revision
                val role=if(driver.frame.flip>0.5) 1 else 0
                contentDescription="${LiquidMetalSwapTheme.tokens[role].name} coin, the token you hold"
                this.role=Role.Image
                if(!reduced) stateDescription="Drag sideways to turn it"
            }.liquidMetalSwapTouch(driver,false,density.density))
        for(i in 0..1) {
            Box(Modifier.offset(x=l.side.dp,y=(l.cardsTop+i*120).dp).size(l.cardW.dp,112.dp).semantics {
                driver.revision
                val role=if(driver.frame.flip>0.5) 1 else 0
                val token=LiquidMetalSwapTheme.tokens[if(i==0) role else 1-role]
                contentDescription="${if(i==0) "You pay" else "You receive"} ${token.amount} ${token.symbol}, ${token.fiat}"
            })
        }
        Box(Modifier.offset(x=l.side.dp,y=l.buttonTop.dp).size(l.cardW.dp,56.dp)
            .semantics {
                driver.revision
                val role=if(driver.frame.flip>0.5) 1 else 0
                val pay=LiquidMetalSwapTheme.tokens[role]; val get=LiquidMetalSwapTheme.tokens[1-role]
                this.role=Role.Button
                contentDescription="Hold to swap ${pay.amount} ${pay.symbol} for ${get.amount} ${get.symbol}"
                stateDescription="Hold for a second and a half. With TalkBack, double tap."
                onClick(label="Swap") { driver.swapNow(); true }
            }.liquidMetalSwapTouch(driver,true,density.density))
    }
}

private fun Modifier.liquidMetalSwapTouch(driver: LiquidMetalSwapDriver,hold: Boolean,density: Float): Modifier = pointerInput(driver,hold,density) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false)
        val start=down.position
        val tracker=VelocityTracker()
        tracker.addPosition(down.uptimeMillis,start)
        if(hold) driver.beginHold() else driver.beginTurn()
        down.consume()
        try {
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull { it.id==down.id } ?: break
                tracker.addPosition(change.uptimeMillis,change.position)
                if(!hold) driver.turn(((change.position.x-start.x)/density).toDouble(),((change.position.y-start.y)/density).toDouble())
                change.consume()
            } while(change.pressed)
        } finally {
            if(hold) driver.endHold() else driver.endTurn((tracker.calculateVelocity().x/density).toDouble())
        }
    }
}
