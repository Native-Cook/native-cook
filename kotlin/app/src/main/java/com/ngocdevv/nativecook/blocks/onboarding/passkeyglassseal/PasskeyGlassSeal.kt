package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PasskeyGlassSeal(autoPlay:Boolean=false){
    val context=LocalContext.current;val view=LocalView.current;val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val driver=remember(autoPlay,reduced){PasskeyGlassSealDriver(autoPlay&&!reduced,reduced){kind->
        view.performHapticFeedback(when(kind){1->HapticFeedbackConstants.CONFIRM;2->HapticFeedbackConstants.CONTEXT_CLICK;else->HapticFeedbackConstants.CLOCK_TICK})
    }}
    val art=remember{PasskeyGlassSealArt()}
    val tick=remember(driver){mutableIntStateOf(0)};val wake=remember(driver){mutableIntStateOf(0)}
    var status by remember(driver){mutableStateOf("Scanning…")}
    DisposableEffect(view){
        val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)};val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose{if(previous!=null)controller.isAppearanceLightStatusBars=previous}
    }
    LaunchedEffect(driver,lifecycle){
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            var previous=0L
            while(true){
                if(!driver.running){val before=wake.intValue;snapshotFlow{wake.intValue}.first{it!=before};previous=0L}
                withFrameNanos{nanos->
                    val dt=if(previous==0L)0.0 else ((nanos-previous)/1e9).coerceIn(0.0,0.5);previous=nanos
                    driver.step(dt);tick.intValue++
                    val next=PasskeyGlassSealMotion.status(driver.frame);if(status!=next)status=next
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val top=WindowInsets.statusBars.getTop(density)/density.density;val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
        val l=remember(maxWidth,maxHeight,top,bottom){PasskeyGlassSealArt.Layout(maxWidth.value,maxHeight.value,top,bottom)}
        Canvas(Modifier.fillMaxSize()){
            tick.intValue
            drawIntoCanvas{canvas->val c=canvas.nativeCanvas;c.save();c.scale(density.density,density.density);art.draw(c,l,driver.frame,driver.now-driver.autoStart,reduced);c.restore()}
        }
        Box(Modifier.offset((120*l.sx-27).dp,(l.y(170f)-27).dp).size(230.dp,54.dp).semantics{heading();contentDescription="ORVANE"})
        Box(Modifier.offset(l.x.dp,l.cy.dp).size(l.cw.dp,l.ch.dp).semantics{
            role=Role.Button
            contentDescription=if(status=="Unlocked")"Welcome back, Mira Okafor. Tap to lock" else "Passkey. Touch and hold to sign in"
            onClick(label="Activate"){driver.activate();wake.intValue++;true}
        }.pointerInput(driver,l){
            awaitEachGesture{
                val down=awaitFirstDown(requireUnconsumed=false);down.consume();driver.begin();wake.intValue++
                var distance=0f;var elapsed=0L;var finished=false
                val velocity=VelocityTracker();velocity.addPosition(down.uptimeMillis,down.position)
                try{do{val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break
                    distance=maxOf(distance,(change.position-down.position).getDistance()/density.density);elapsed=change.uptimeMillis-down.uptimeMillis
                    velocity.addPosition(change.uptimeMillis,change.position)
                    driver.drag(((change.position.x-down.position.x)/density.density).toDouble(),l.cw.toDouble())
                    if(!change.pressed)finished=true
                    change.consume()
                }while(event.changes.any{it.pressed})}finally{driver.end(distance<=10&&elapsed<=500,velocity.calculateVelocity().x/density.density.toDouble(),l.cw.toDouble(),cancelled=!finished);wake.intValue++}
            }
        }){
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                tick.intValue
                val a=if(reduced)0.0 else driver.frame[4]-PasskeyGlassSealMotion.nearestFace(driver.frame[4])
                rotationY=(180*a).toFloat()
                // Keep the gesture surface still: translation and release speed are screen distances.
                val scale=(1+0.07*abs(sin(PI*a))).toFloat()
                // RN scales z too; graphicsLayer's x/y scale needs the equivalent camera distance.
                cameraDistance=1100*density.density/scale
                scaleX=scale;scaleY=scale
            }){
                tick.intValue
                drawIntoCanvas{canvas->val c=canvas.nativeCanvas;c.save();c.scale(density.density,density.density);art.drawCard(c,l,driver.frame,driver.now-driver.autoStart,reduced);c.restore()}
            }
        }
        Canvas(Modifier.fillMaxSize()){
            tick.intValue
            drawIntoCanvas{canvas->val c=canvas.nativeCanvas;c.save();c.scale(density.density,density.density);art.drawOverlay(c,l,driver.frame);c.restore()}
        }
        Box(Modifier.offset(0.dp,(l.y(730f)-13).dp).fillMaxWidth().height(24.dp).semantics{contentDescription=status;liveRegion=LiveRegionMode.Polite})
        if(status=="Unlocked"){
            val pressed=remember{Animatable(0f)};val scope=rememberCoroutineScope()
            Box(Modifier.align(Alignment.TopCenter).offset(y=(l.y(730f)+34).dp).height(44.dp)
                .graphicsLayer{scaleX=if(reduced)1f else 1-0.04f*pressed.value;scaleY=scaleX;alpha=if(reduced)1-0.28f*pressed.value else 1f}
                .border(1.dp,Color.White.copy(alpha=0.5f),RoundedCornerShape(22.dp)).padding(horizontal=28.dp)
                .semantics(mergeDescendants=true){role=Role.Button;contentDescription="Continue";onClick{true}}
                .pointerInput(reduced){awaitEachGesture{
                    val down=awaitFirstDown();down.consume();scope.launch{pressed.animateTo(1f,tween(150,easing=CubicBezierEasing(0.23f,1f,0.32f,1f)))}
                    try{do{val e=awaitPointerEvent();e.changes.forEach{it.consume()}}while(e.changes.any{it.pressed})}
                    finally{scope.launch{pressed.animateTo(0f,tween(150,easing=CubicBezierEasing(0.23f,1f,0.32f,1f)))}}
                }},contentAlignment=Alignment.Center){
                Text("Continue",color=Color.White,fontSize=(15/density.fontScale).sp,fontWeight=FontWeight.Normal,letterSpacing=(1.5f/density.fontScale).sp)
            }
        }
    }
}
