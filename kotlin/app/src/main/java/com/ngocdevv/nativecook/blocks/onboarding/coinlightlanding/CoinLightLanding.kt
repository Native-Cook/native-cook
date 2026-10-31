package com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding

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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlin.math.*

@Composable
fun CoinLightLanding(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { CoinLightLandingArt(context) }
    val driver=remember(autoPlay,reduced) { CoinLightLandingDriver() }
    val frame=remember(driver) { mutableStateOf(driver.f.copy()) }
    val wake=remember { Channel<Unit>(Channel.CONFLATED) }
    val audio=remember(context,reduced) { if(reduced)null else CoinLightLandingAudio(context) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val overviewPressed=remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    fun buzz(kind: Int) {
        if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==2)VibrationEffect.EFFECT_DOUBLE_CLICK else if(kind==1)VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==1)18 else 12,if(kind==1)80 else 45))
    }
    fun cue(name: String) { audio?.play(name); buzz(if(name=="snap")2 else if(name=="spin")1 else 0) }
    fun become() { if(driver.become())cue("snap"); wake.trySend(Unit) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,bounds,top) {
        if(bounds.width==0f)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            do {
                withFrameNanos { stamp ->
                    val dt=if(previous==0L)0.0 else min(0.5,(stamp-previous)/1e9)
                    previous=stamp
                    frame.value=driver.step(dt,bounds.width/density.density.toDouble(),bounds.height/density.density.toDouble(),top.toDouble(),autoPlay,reduced)
                }
                if(!driver.moving(autoPlay,reduced)) { wake.receive(); previous=0L }
            } while(true)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value; val h=maxHeight.value
        Canvas(Modifier.fillMaxSize().onSizeChanged { bounds=androidx.compose.ui.geometry.Size(it.width.toFloat(),it.height.toFloat()) }.pointerInput(driver,reduced,top) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position; val x=start.x/density.density; val y=start.y/density.density
                val hit=abs(x-w/2)<=90 && abs(y-h*0.74)<=90
                if(!hit)return@awaitEachGesture
                if(driver.begin(x.toDouble(),y.toDouble()))cue("hum")
                wake.trySend(Unit)
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
                down.consume()
                try {
                    do {
                        val event=awaitPointerEvent(); val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if(change.pressed)driver.move((change.position.x/density.density).toDouble(),(change.position.y/density.density).toDouble())
                        change.consume()
                    } while(change.pressed)
                } finally {
                    val velocity=tracker.calculateVelocity()
                    driver.end((velocity.x/density.density).toDouble(),(velocity.y/density.density).toDouble(),top.toDouble(),reduced)?.let { cue(it) }
                    wake.trySend(Unit)
                }
            }
        }) {
            val f=frame.value
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas; c.save(); c.scale(density.density,density.density)
                art.draw(c,size.width/density.density,size.height/density.density,top,bottom,f,reduced,overviewPressed.value); c.restore()
            }
        }
        Box(Modifier.offset(22.dp,(top+10).dp).size(22.dp,36.dp).semantics { contentDescription="Plinth" })
        Box(Modifier.offset((w-58).dp,(top+10).dp).size(36.dp).semantics { contentDescription="Menu"; role=Role.Button })
        Box(Modifier.offset(22.dp,(top+58).dp).size((w-44).dp,80.dp).semantics { contentDescription="Get paid to create"; heading() })
        listOf("Sell your work on the Plinth Market","Keep 100% of your Market earnings","Refer creators and get up to 50% commission","").forEachIndexed { i,label ->
            Box(Modifier.offset(22.dp,(top+156+i*40).dp).size((w-44).dp,24.dp).semantics { contentDescription=if(i==3) "$${String.format(java.util.Locale.US,"%,d",frame.value.pay.roundToInt())} paid to creators in October" else label })
        }
        Box(Modifier.offset(22.dp,(top+332).dp).size(164.dp,44.dp).semantics { contentDescription="Become a creator"; role=Role.Button; onClick { become(); true } }.pointerInput(driver) {
            awaitEachGesture { awaitFirstDown(); do { val e=awaitPointerEvent() } while(e.changes.any { it.pressed }); become() }
        })
        Box(Modifier.offset(196.dp,(top+332).dp).size(176.dp,44.dp).semantics { contentDescription="Watch the overview"; role=Role.Button; onClick { buzz(0); true } }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(); overviewPressed.value=true
                try { do { val e=awaitPointerEvent() } while(e.changes.any { it.pressed }); buzz(0) }
                finally { overviewPressed.value=false }
            }
        })
        Box(Modifier.offset((w/2-90).dp,(h*0.74f-90).dp).size(180.dp).semantics { contentDescription="Gold coin. Drag it around to light the page, or drop it on Become a creator."; role=Role.Button; onClick { become(); true } })
    }
}
