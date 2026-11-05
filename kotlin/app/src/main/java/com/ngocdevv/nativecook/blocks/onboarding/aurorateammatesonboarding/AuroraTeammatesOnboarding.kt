package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlin.math.floor
import kotlin.math.roundToInt
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL

@Composable
fun AuroraTeammatesOnboarding(autoPlay:Boolean=false,onComplete:()->Unit={},previewTime:Double?=null) {
    val context=LocalContext.current;val view=LocalView.current;val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context){AuroraTeammatesOnboardingArt(context)}
    val tilt=remember(context){AuroraTeammatesOnboardingTilt(context)}
    val driver=remember(autoPlay,reduced,previewTime){AuroraTeammatesOnboardingDriver(autoPlay,reduced,previewTime)}
    val revision=remember{mutableLongStateOf(0)};var wake by remember{mutableIntStateOf(0)};var page by remember{mutableIntStateOf(0)};var completed by remember{mutableStateOf(false)}
    val complete by rememberUpdatedState(onComplete)
    val buzz:(Int)->Unit=remember(view){{kind->view.performHapticFeedback(if(kind==2)HapticFeedbackConstants.CONFIRM else if(kind==1)HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK);Unit}}
    DisposableEffect(view){
        val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)};val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose{if(previous!=null)controller.isAppearanceLightStatusBars=previous}
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout=remember(maxWidth,maxHeight){AuroraTeammatesOnboardingTheme.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble())}
        remember(driver,layout){driver.prepare(layout)}
        LaunchedEffect(driver,layout,lifecycle,wake){
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
                if(!reduced&&previewTime==null)tilt.start()
                try {var previous=0L
                    while(true){withFrameNanos{nanos->
                        val dt=if(previous==0L).016 else ((nanos-previous)/1e9).coerceIn(0.0,.05);previous=nanos
                        driver.step(dt,layout,tilt,buzz,complete)
                        val nextPage=(floor(AuroraTeammatesOnboardingMotion.wrap(driver.position)+.5).toInt()%3)
                        if(page!=nextPage)page=nextPage
                        val done=driver.scene[GL.finCheck]>.5;if(completed!=done)completed=done
                        revision.longValue++
                    };if(previewTime!=null||(reduced&&!driver.moving))awaitCancellation()}
                } finally {tilt.stop()}
            }
        }
        fun changed(){revision.longValue++;wake++}
        Canvas(Modifier.fillMaxSize().pointerInput(driver,layout,reduced,previewTime){
            if(previewTime!=null)return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false);val start=down.position/density.density
                driver.begin(start.x.toDouble(),start.y.toDouble(),layout);changed()
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position);var end=start;var released=false
                down.consume()
                try {do {val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break
                    end=change.position/density.density;tracker.addPosition(change.uptimeMillis,change.position)
                    val velocity=tracker.calculateVelocity()
                    if(change.pressed)driver.move(end.x.toDouble(),end.y.toDouble(),(end.x-start.x).toDouble(),(end.y-start.y).toDouble(),(velocity.x/density.density).toDouble(),layout)else released=true
                    revision.longValue++;change.consume()
                } while(change.pressed)} finally {val velocity=tracker.calculateVelocity();driver.end(end.x.toDouble(),end.y.toDouble(),(end.x-start.x).toDouble(),(end.y-start.y).toDouble(),(velocity.x/density.density).toDouble(),layout,!released);changed()}
            }
        }){revision.longValue;drawIntoCanvas{art.draw(it.nativeCanvas,layout,driver.scene,driver.buttonPress,density.density,reduced)}}
        // The visible letters remain Canvas content; TalkBack receives one adjustable page and button.
        Box(Modifier.fillMaxSize().semantics {
            contentDescription=AuroraTeammatesOnboardingTheme.descriptions[page];stateDescription="Page ${page+1} of 3"
            progressBarRangeInfo=ProgressBarRangeInfo(page.toFloat(),0f..2f,1)
            setProgress{target->driver.accessibilityStep(target.roundToInt().coerceIn(0,2)-page);changed();true}
            customActions=listOf(CustomAccessibilityAction("Next page"){driver.accessibilityStep(1);changed();true},CustomAccessibilityAction("Previous page"){driver.accessibilityStep(-1);changed();true})
        })
        Box(Modifier.offset((layout.cx-layout.buttonW/2).dp,(layout.h-layout.buttonH).dp).size(layout.buttonW.dp,layout.buttonH.dp).graphicsLayer {
            revision.longValue;translationY=(-driver.scene[GL.buttonBottom]*density.density).toFloat()
        }.semantics {
            role=Role.Button;contentDescription=if(completed)"You're all set"else"Next"
            onClick("Shows the next onboarding page"){driver.next();changed();true}
        })
    }
}
