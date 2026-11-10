package com.ngocdevv.nativecook.blocks.gestures.serumdropperroutine

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
import kotlinx.coroutines.flow.first
import kotlin.math.*

@Composable
fun SerumDropperRoutine(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current.density
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val rememberedTicks=remember { booleanArrayOf(true,true,false,false) }
    val driver=remember(autoPlay,reduced) {
        SerumDropperRoutineDriver(autoPlay && !reduced,rememberedTicks) { kind ->
            if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==0)VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==2)24 else 10,if(kind==2)70 else 35))
        }
    }
    val art=remember(context) { SerumDropperRoutineArt(context) }
    var revision by remember { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(0) }
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(before!=null)controller.isAppearanceLightStatusBars=before }
    }
    DisposableEffect(art) { onDispose { art.close() } }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if(!driver.moving) {
                    val old=wake
                    snapshotFlow { wake }.first { it!=old }
                    previous=0L
                }
                withFrameNanos { nano ->
                    val dt=min(0.5,if(previous==0L)0.016 else (nano-previous)/1e9)
                    previous=nano; driver.step(dt); revision++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=SerumDropperRoutineLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top.toDouble(),bottom.toDouble())
        Canvas(Modifier.fillMaxSize().pointerInput(driver,l,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val x=down.position.x/density; val y=down.position.y/density
                val check=SerumDropperRoutineLayout.cards.indexOfFirst { b ->
                    abs(x-(b[0]+134)*l.sx)<=22 && abs(y-l.y(b[1])-32)<=22
                }
                val hold=check<0 && x>=26*l.sx && x<=191*l.sx-46 && y>=l.y(308.0)+40 && y<=l.y(308.0)+210*l.k
                if(hold) { driver.begin(reduced); wake++; revision++ }
                down.consume()
                var valid=true
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        if(hypot((change.position.x-down.position.x).toDouble(),(change.position.y-down.position.y).toDouble())>12*density)valid=false
                        change.consume()
                    } while(change.pressed)
                } finally {
                    if(hold)driver.end(reduced) else if(check>=0 && valid)driver.toggle(check)
                    wake++; revision++
                }
            }
        }) {
            @Suppress("UNUSED_EXPRESSION")
            revision
            drawIntoCanvas { art.draw(it.nativeCanvas,l,driver.frame,driver.ticks,density) }
        }
        // Native semantics keep the exact labels and independent checkbox actions.
        Box(Modifier.offset((26*l.sx).dp,(l.y(308.0)+40).dp).size((165*l.sx-46).dp,(210*l.k-40).dp).semantics {
            role=Role.Button; contentDescription="Fruit oil. Touch and hold to apply it"
        })
        SerumDropperRoutineLayout.cards.forEachIndexed { i,b ->
            Box(Modifier.offset(((b[0]+134)*l.sx-22).dp,(l.y(b[1])+10).dp).size(44.dp).semantics {
                role=Role.Checkbox; contentDescription="Step ${i+1} done"; stateDescription=if(revision.let { driver.ticks[i] })"Checked" else "Unchecked"
                onClick { driver.toggle(i); wake++; revision++; true }
            })
        }
        listOf("Back","More","Scan your face").forEachIndexed { i,label ->
            Box(Modifier.offset((if(i==2)200*l.sx-36 else (if(i==0)46 else 352)*l.sx-22).dp,(if(i==2)l.y(795.0)-36 else l.y(98.0)-22).dp).size((if(i==2)72 else 44).dp).semantics { role=Role.Button; contentDescription=label; onClick { true } })
        }
        Box(Modifier.offset((80*l.sx).dp,(l.y(100.0)-12).dp).size(150.dp,24.dp).semantics { heading(); contentDescription="Daily routine" })
        doubleArrayOf(25.0,105.0,185.0,264.0,345.0).forEachIndexed { i,x ->
            Box(Modifier.offset((x*l.sx).dp,l.y(150.0).dp).size((75*l.sx).dp,(100*l.k).dp).semantics { contentDescription="${arrayOf("Mon","Tue","Wed","Thu","Fri")[i]} ${i+5}${if(i==1)", today" else ""}" })
        }
        SerumDropperRoutineLayout.labels.forEachIndexed { i,p ->
            Box(Modifier.offset((p.first*l.sx).dp,(l.y(p.second)-10).dp).size((165*l.sx).dp,64.dp).semantics { contentDescription="${SerumDropperRoutineArt.names[i].replace('\n',' ')}, Step ${i+1}" })
        }
    }
}
