package com.ngocdevv.nativecook.blocks.modals.firstlootreward

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun FirstLootReward(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    val driver=remember(autoPlay,reduced) { FirstLootRewardDriver() }
    val art=remember { FirstLootRewardArt() }
    val tilt=remember(context) { FirstLootRewardTilt(context) }
    val audio=remember(context,reduced) { if(reduced)null else FirstLootRewardAudio(context) }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    var tick by remember { mutableIntStateOf(0) }
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    fun buzz(success: Boolean) {
        if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(if(success)VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(success)24L else 12L,45))
    }
    fun finish(target: Int,ok: Boolean) {
        val result=driver.end(target,ok,reduced)
        if(result==0 || result==3)audio?.play("tap")
        if(result==2 || result==3)audio?.play("open")
        if(result>=0)buzz(result>=2)
        tick++
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,reduced) {
        if(reduced)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tilt.start()
            try {
                var previous=0L
                while(true)withFrameNanos { nanos ->
                    val dt=min(if(previous==0L)0.016 else (nanos-previous)/1e9,0.05); previous=nanos
                    driver.step(dt,autoPlay); tilt.step(dt); tick++
                }
            } finally { tilt.stop() }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { dimensions=it }) {
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,top,bottom) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val d=density.density
                val x=start.x/d; val y=start.y/d; val w=size.width/d; val h=size.height/d
                val by=top+182; val btnY=h-bottom-68; val bw=(w-48)/2
                val target=if(kotlin.math.abs(x-w/2)<=110 && kotlin.math.abs(y-by)<=110)1
                else if(y>=btnY && y<=btnY+52 && x>=20 && x<=20+bw)2
                else if(y>=btnY && y<=btnY+52 && x>=28+bw && x<=w-20)3 else 0
                val accepted=target!=0 && driver.begin(target,reduced)
                if(accepted && target==1)buzz(false)
                var exceeded=false; var elapsed=0L; var canceled=true
                down.consume(); tick++
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id }?:break
                        val dx=(change.position.x-start.x)/d; val dy=(change.position.y-start.y)/d
                        elapsed=change.uptimeMillis-down.uptimeMillis
                        if(hypot(dx.toDouble(),dy.toDouble())>10)exceeded=true
                        if(accepted && target==1 && change.pressed)driver.move(dx.toDouble(),dy.toDouble())
                        change.consume()
                        if(!change.pressed) { canceled=false; break }
                    } while(true)
                } finally {
                    if(accepted)finish(target,!canceled && !exceeded && elapsed<=2000)
                }
            }
        }) {
            @Suppress("UNUSED_EXPRESSION")
            tick // Read frame state only in the drawing phase, not in composition.
            drawIntoCanvas {
                val c=it.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                art.draw(c,size.width/density.density.toDouble(),size.height/density.density.toDouble(),top,bottom,driver.frame(tilt.x,tilt.y)); c.restore()
            }
        }
        val w=dimensions.width/density.density.toDouble()
        val h=dimensions.height/density.density.toDouble()
        fun activate(target: Int): Boolean {
            if(driver.begin(target,reduced)) { if(target==1)buzz(false); finish(target,true) }; return true
        }
        Box(Modifier.offset((w/2-110).dp,(top+72).dp).size(220.dp).semantics {
            role=Role.Button; contentDescription="Loot chest badge"; onClick(label="Opens the chest.") { activate(1) }
        })
        Box(Modifier.offset(20.dp,(top+351).dp).width((w-40).coerceAtLeast(0.0).dp).height(68.dp).semantics { heading(); contentDescription="You just earned your first Loot." })
        Box(Modifier.offset(36.dp,(top+434).dp).width((w-72).coerceAtLeast(0.0).dp).height(40.dp).semantics { contentDescription="Nice start! Keep playing, keep winning, and watch your rewards stack up." })
        Box(Modifier.offset(20.dp,(h-bottom-225).dp).width((w-40).coerceAtLeast(0.0).dp).height(133.dp).semantics { contentDescription="Announcement. Something big is coming 🎉. Ember Rift drops October 24. Get ready to play, compete, and earn exclusive Loot." })
        for(i in 0..1) {
            Box(Modifier.offset((20+i*((w-48)/2+8)).dp,(h-bottom-68).dp).width(((w-48)/2).coerceAtLeast(0.0).dp).height(52.dp).semantics {
                role=Role.Button; contentDescription=if(i==0)"Keep playing" else "View loot"; onClick { activate(i+2) }
            })
        }
    }
}
