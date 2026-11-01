package com.ngocdevv.nativecook.blocks.modals.clawprizedrop

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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun ClawPrizeDrop(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { ClawPrizeDropArt(context) }
    val driver=remember(autoPlay,reduced) { ClawPrizeDropDriver() }
    val frame=remember(driver) { mutableStateOf<ClawPrizeDropMotion.Frame?>(null) }
    var geometry by remember { mutableStateOf<ClawPrizeDropMotion.Geo?>(null) }
    var wake by remember { mutableIntStateOf(0) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(success: Boolean) {
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(success)VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_HEAVY_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(success)30 else 18,80))
    }
    fun grab() { geometry?.let { buzz(false); driver.grab(it); wake++ } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(before!=null)controller.isAppearanceLightStatusBars=before }
    }
    LaunchedEffect(driver,lifecycle,geometry,wake) {
        val g=geometry ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if((!autoPlay || reduced) && driver.idle() && frame.value!=null) { delay(100); previous=0L; continue }
                withFrameNanos { nanos ->
                    val wall=if(previous==0L)0.016 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous=nanos
                    frame.value=driver.step(wall,g,autoPlay,reduced)
                    if(driver.success) buzz(true)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val g=remember(maxWidth,maxHeight,top) { ClawPrizeDropMotion.Geo(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top) }
        SideEffect { geometry=g }
        Canvas(Modifier.fillMaxSize().pointerInput(g,driver) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density.density
                val by=g.y+g.h-6
                val button=start.x>=g.width-96 && start.x<=g.width-32 && start.y>=by+110 && start.y<=by+174
                val glass=start.x>=g.x && start.x<=g.x+g.w && start.y>=g.y && start.y<=g.y+g.h
                var moved=false
                if(button) { driver.pressed=true; wake++ }
                while(true) {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    val point=change.position/density.density
                    val dx=(point.x-start.x).toDouble()
                    if(abs(dx)>10)moved=true
                    if(!change.pressed) {
                        if(button && !moved && point.x>=g.width-96 && point.x<=g.width-32 && point.y>=by+110 && point.y<=by+174)grab()
                        if(glass)driver.endPan()
                        driver.pressed=false; wake++; break
                    }
                    if(glass && moved) { driver.pan(dx,g); wake++; change.consume() }
                }
            }
        }) {
            val o=frame.value ?: ClawPrizeDropMotion.Frame(g.homeX)
            drawIntoCanvas { cv ->
                val c=cv.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                art.draw(c,g,o,reduced,driver.won,driver.pressed,density.density)
                c.restore()
            }
        }
        Box(Modifier.offset(16.dp,(top+8).dp).size(44.dp).semantics { role=Role.Button; contentDescription="Close"; onClick { true } })
        Box(Modifier.offset((g.width-116).dp,(top+12).dp).size(100.dp,36.dp).semantics { contentDescription="1,200 coins" })
        Box(Modifier.offset(28.dp,(g.y-86).dp).size(g.w.dp,52.dp).semantics { contentDescription="Grabbit" })
        Box(Modifier.offset(32.dp,(g.y+g.h-6+131).dp).size((g.w-64).dp,22.dp).semantics { contentDescription="Slide the claw, then grab · 3 tries left" })
        Box(Modifier.offset(g.x.dp,g.y.dp).size(g.w.dp,g.h.dp).semantics {
            contentDescription="Claw. Drag sideways to move it over a prize"

        })
        Box(Modifier.offset((g.width-96).dp,(g.y+g.h-6+110).dp).size(64.dp).semantics {
            role=Role.Button; contentDescription="Grab"; onClick { grab(); true }
        })
        ClawPrizeDropRewardAccessibility(frame, driver, g)
    }
}

@Composable
private fun ClawPrizeDropRewardAccessibility(
    frame: State<ClawPrizeDropMotion.Frame?>,
    driver: ClawPrizeDropDriver,
    g: ClawPrizeDropMotion.Geo,
) {
    if ((frame.value?.coupon ?: 0.0) > 0.5) {
        Box(Modifier.offset(44.dp,(g.y+g.h+21).dp).size((g.w-32).dp,58.dp).semantics {
            liveRegion=LiveRegionMode.Polite
            contentDescription="${ClawPrizeDropMotion.names[driver.won]}. ${ClawPrizeDropMotion.rewards[driver.won]}"
        })
    }
}
