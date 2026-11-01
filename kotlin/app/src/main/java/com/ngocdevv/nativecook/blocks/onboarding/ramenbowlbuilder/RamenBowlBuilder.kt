package com.ngocdevv.nativecook.blocks.onboarding.ramenbowlbuilder

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
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun RamenBowlBuilder(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=remember(context) { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    val art=remember(context) { RamenBowlBuilderArt(context) }
    val driver=remember { RamenBowlBuilderDriver() }
    val tick=remember { mutableIntStateOf(0) }
    val wake=remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val buzz: (Int)->Unit = remember(vibrator) {{ kind ->
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==0) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_HEAVY_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==0) 10 else 18,if(kind==0) 35 else 80))
    }}
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(old!=null) controller.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(driver,autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(isActive) {
                withFrameNanos { nanos ->
                    val wall=(if(previous==0L) 0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous=nanos
                    driver.step(wall,autoPlay,reduced,buzz)
                    if(count!=driver.frame.count) count=driver.frame.count
                    tick.intValue++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=constraints.maxWidth/density.density.toDouble()
        val h=constraints.maxHeight/density.density.toDouble()
        val g=remember(w,h) { RamenBowlBuilderMotion.Geo(w,h) }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            drawIntoCanvas { wrapper ->
                val c=wrapper.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                art.draw(c,w,h,density.density,g,driver.frame,reduced,top,bottom,pressed)
                c.restore()
            }
        }
        Box(Modifier.fillMaxWidth().offset(y=(top+120).dp).height(max(0.0,h-top-bottom-240).dp)
            .semantics { contentDescription="Ramen bowl with $count of 5 toppings. Drag a topping into the bowl" }
            .pointerInput(driver,g,top,bottom) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val yOffset=top+120
                    driver.begin(down.position.x/density.density.toDouble(),down.position.y/density.density.toDouble()+yOffset,g,buzz)
                    wake.intValue++
                    down.consume()
                    try {
                        do {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            driver.update(change.position.x/density.density.toDouble(),change.position.y/density.density.toDouble()+yOffset)
                            wake.intValue++; change.consume()
                        } while(change.pressed)
                    } finally { driver.end(g); wake.intValue++ }
                }
            })
        // The canvas draws the CTA; this independent semantics node keeps its exact RN label.
        val bw=art.ctaWidth(count)
        Box(Modifier.offset(x=(w-30-bw).dp,y=(h-bottom-84).dp).size(bw.dp,56.dp)
            .semantics {
                role=Role.Button
                contentDescription=if(count>=5) "Next step" else "${5-count} toppings to go"
                onClick { driver.next(g,buzz); wake.intValue++; true }
            }
            .pointerInput(driver,g,count) {
                awaitEachGesture {
                    val down=awaitFirstDown(); pressed=true; down.consume()
                    var inside=true
                    try {
                        do {
                            val e=awaitPointerEvent()
                            val change=e.changes.firstOrNull { it.id==down.id } ?: break
                            inside=change.position.x>=0 && change.position.x<=size.width && change.position.y>=0 && change.position.y<=size.height
                            change.consume()
                        } while(change.pressed)
                        if(inside) { driver.next(g,buzz); wake.intValue++ }
                    } finally { pressed=false }
                }
            })
    }
}
