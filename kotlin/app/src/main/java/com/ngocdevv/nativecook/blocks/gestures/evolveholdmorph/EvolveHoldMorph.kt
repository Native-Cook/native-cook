package com.ngocdevv.nativecook.blocks.gestures.evolveholdmorph

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
import kotlin.math.*

@Composable
fun EvolveHoldMorph(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val driver = remember(autoPlay,reduced){ EvolveHoldMorphDriver() }
    val frame = remember(driver){ mutableStateOf(EvolveHoldMorphFrame()) }
    val stage by remember(frame) { derivedStateOf { frame.value.stage } }
    val art = remember(context){ EvolveHoldMorphArt(context) }
    val vibrator = remember(context){ context.getSystemService(Vibrator::class.java) }
    fun buzz(kind: Int){
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==0)VibrationEffect.EFFECT_TICK else if(kind==2)VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==2)24 else 10,60))
    }
    DisposableEffect(view){
        val activity = generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)}
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose{if(before!=null) controller.isAppearanceLightStatusBars=before}
    }
    LaunchedEffect(driver,lifecycle,reduced){
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            var previous=0L
            while(true){
                withFrameNanos{ nanos->
                    val wall = (if(previous==0L)0.016 else (nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous=nanos
                    frame.value=driver.step(wall,autoPlay,reduced,::buzz)
                }
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    fun touch(pet: Boolean) = Modifier.pointerInput(driver,reduced){
        awaitEachGesture{
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            var cancelled=false
            var end=start
            val startTime=down.uptimeMillis
            var endTime=startTime
            driver.begin();down.consume()
            try{
                do{
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull{it.id==down.id}?:break
                    end=change.position;endTime=change.uptimeMillis
                    if((end-start).getDistance()/density.density>80 && !cancelled){cancelled=true;driver.end()}
                    change.consume()
                }while(change.pressed && event.changes.any{it.pressed})
            }finally{
                driver.end()
                if(pet && !cancelled && endTime-startTime<=200 && (end-start).getDistance()/density.density<=10){
                    driver.hopV=380.0;driver.lastTouch=driver.now;buzz(1)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val w=maxWidth.value;val h=maxHeight.value;val cy=round(h*0.43f);val s=round(w*0.42f)
        Canvas(Modifier.fillMaxSize()){
            drawIntoCanvas{ canvas ->
                val native=canvas.nativeCanvas
                native.save();native.scale(density.density,density.density)
                art.draw(native,w,h,density.density,top,bottom,frame.value)
                native.restore()
            }
        }
        Box(Modifier.offset(40.dp,(cy-s*0.95f).dp).size((w-80).dp,(s*1.75f).dp)
            .then(touch(true)).semantics{
                role=Role.Button
                contentDescription="Pip the ${EvolveHoldMorphMotion.names[stage]}. Tap to play, press and hold to evolve"
                onClick{driver.hopV=380.0;driver.lastTouch=driver.now;buzz(1);true}
                onLongClick(label="Hold to evolve"){driver.begin();true}
            })
        Box(Modifier.offset(20.dp,(h-bottom-80).dp).size((w-40).dp,60.dp)
            .then(touch(false)).semantics{
                role=Role.Button;contentDescription=EvolveHoldMorphMotion.cta[stage]
                onClick(label="Press and hold until the ring is full"){driver.begin();true}
            })
        Box(Modifier.offset(20.dp,(h-bottom-164).dp).size((w-40).dp,64.dp).semantics{contentDescription="Evolution energy full, 120 of 120"})
        Box(Modifier.offset((w-90).dp,(top+16).dp).size(70.dp,34.dp).semantics{contentDescription="Level ${4+stage}"})
    }
}
