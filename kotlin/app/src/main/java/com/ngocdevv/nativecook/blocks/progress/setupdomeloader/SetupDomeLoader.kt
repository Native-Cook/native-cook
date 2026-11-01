package com.ngocdevv.nativecook.blocks.progress.setupdomeloader

import android.app.Activity
import android.content.ContextWrapper
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun SetupDomeLoader(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val scale=density.density
    val top=WindowInsets.statusBars.getTop(density)/scale.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/scale.toDouble()
    val art=remember { SetupDomeLoaderArt(context.assets) }
    val driver=remember { SetupDomeLoaderDriver() }
    val redraw=remember { mutableIntStateOf(0) }
    var accessibilityDone by remember { mutableStateOf(false) }
    val haptics=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=remember { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    val view=LocalView.current
    DisposableEffect(view) {
        var c=context
        while(c is ContextWrapper && c !is Activity) c=c.baseContext
        val controller=(c as? Activity)?.let { WindowCompat.getInsetsController(it.window,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(old!=null) controller.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(autoPlay,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while(true) {
                withFrameNanos { now ->
                    val wall=if(last==0L) 0.016 else ((now-last)/1e9).coerceIn(0.0,0.5)
                    last=now
                    if(driver.step(wall,autoPlay)) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    accessibilityDone=SetupDomeLoaderMotion.phase(driver.t).swap>=0.5
                    redraw.intValue++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value.toDouble(); val h=maxHeight.value.toDouble()
        val g=remember(w,h) { SetupDomeLoaderGeo(w,h) }
        Canvas(Modifier.fillMaxSize().pointerInput(w,h) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val x=start.x/scale; val y=start.y/scale
                val inDome=x>=24 && x<=w-24 && y>=g.by-g.h-g.r*0.5 && y<=g.by+g.r*0.5
                val inCta=SetupDomeLoaderMotion.clamp(SetupDomeLoaderMotion.phase(driver.t).swap*2-1)>0.01 && x>=20 && x<=w-20 && y>=h-bottom-70 && y<=h-bottom-16
                driver.ctaPressed=inCta
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
                var end=start; var pan=false; var endTime=down.uptimeMillis; var tapEligible=true
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    end=change.position; endTime=change.uptimeMillis; tracker.addPosition(change.uptimeMillis,end)
                    if(hypot((end.x-start.x)/scale,(end.y-start.y)/scale)>10 || event.changes.count { it.pressed }>1) tapEligible=false
                    if(inDome && abs((end.x-start.x)/scale)>10) pan=true
                    if(inDome && pan && change.pressed) driver.pan(tracker.calculateVelocity().x/scale.toDouble(),false)
                    change.consume()
                } while(change.pressed)
                driver.ctaPressed=false
                if(inDome) {
                    if(pan) driver.pan(tracker.calculateVelocity().x/scale.toDouble(),true)
                    else if(tapEligible && endTime-down.uptimeMillis<=500 && hypot((end.x-start.x)/scale,(end.y-start.y)/scale)<=10) {
                        driver.tap(); haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                } else if(inCta && hypot((end.x-start.x)/scale,(end.y-start.y)/scale)<10) {
                    driver.restart(); haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }) {
            redraw.intValue
            drawIntoCanvas {
                val cv=it.nativeCanvas
                cv.save(); cv.scale(scale,scale)
                art.scene(cv,g,driver,reduced)
                art.ui(cv,g,top,bottom,driver.t,driver.ctaPressed)
                cv.restore()
            }
        }
        Box(Modifier.offset(((w-80)/2).dp,(top+13.5).dp).size(80.dp,25.dp).semantics { contentDescription="Kinto" })
        Box(Modifier.offset(6.dp,(top-2).dp).size(56.dp).semantics { role=Role.Button; contentDescription="Back"; onClick { true } })
        Box(Modifier.offset((w-62).dp,(top-2).dp).size(56.dp).semantics { role=Role.Button; contentDescription="Help"; onClick { true } })
        Box(Modifier.offset(24.dp,(g.by-g.h-g.r*0.5).dp).size((w-48).dp,(g.h+g.r).dp).semantics {
            role=Role.Image
            contentDescription="Your wallet, card and money floating in a glass case while the account is set up"
        })
        Box(Modifier.offset(24.dp,(g.by+g.ry*1.42+52).dp).size((w-48).dp,230.dp).semantics {
            heading(); contentDescription=if(accessibilityDone) "You're all set. Your card and wallet are ready to use." else "We're setting up your account. Verifying your details. Creating your card. Securing your wallet. This can take up to 2 minutes."
        })
        Box(Modifier.offset(20.dp,(h-bottom-70).dp).size((w-40).dp,54.dp).semantics {
            role=Role.Button; contentDescription="Continue"; onClick { driver.restart(); haptics.performHapticFeedback(HapticFeedbackType.ContextClick); true }
        })
    }
}
