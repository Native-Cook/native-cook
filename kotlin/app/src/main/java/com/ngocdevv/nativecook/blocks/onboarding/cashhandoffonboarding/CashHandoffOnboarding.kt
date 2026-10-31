package com.ngocdevv.nativecook.blocks.onboarding.cashhandoffonboarding

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

@Composable
fun CashHandoffOnboarding(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val driver = remember { CashHandoffOnboardingDriver() }
    val art = remember(context) { CashHandoffOnboardingArt(CashHandoffOnboardingResources(context)) }
    val audio = remember(context,reduced) { if(reduced) null else CashHandoffOnboardingAudio(context) }
    var tick by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(density)/density.density
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density
    fun cue(kind: Int) {
        if(kind<0)return
        audio?.play(if(kind==0) "flutter" else if(kind==2) "catch" else "return")
        view.performHapticFeedback(if(kind==0) HapticFeedbackConstants.CLOCK_TICK else if(kind==2) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(before!=null)controller.isAppearanceLightStatusBars=before }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=CashHandoffOnboardingMotion.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top.toDouble(),bottom.toDouble())
        LaunchedEffect(auto,running,l,lifecycle) {
            if(!auto && !running)return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous=0L
                while(isActive) {
                    withFrameNanos { nanos ->
                        val dt=if(previous==0L)0.0 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
                        previous=nanos
                        driver.step(dt,l,auto)
                        tick++
                        if(!auto && !driver.running)running=false
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(l,auto,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density.density
                val buttonY=l.height-l.bottom-16-89
                val cta=start.x>=20 && start.x<=l.width-20 && start.y>=buttonY && start.y<=buttonY+56
                val stage=start.y<l.panel
                if(cta)pressed=true
                if(stage) { cue(driver.begin(start.x.toDouble(),start.y.toDouble())); running=true }
                down.consume()
                var end=start
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    end=change.position/density.density
                    if(stage && change.pressed)driver.move(end.x.toDouble(),end.y.toDouble())
                    change.consume()
                } while(change.pressed)
                if(stage)cue(driver.end(l))
                if(cta && end.x>=20 && end.x<=l.width-20 && end.y>=buttonY && end.y<=buttonY+56)view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                pressed=false
            }
        }) {
            tick
            if(driver.now==0.0)driver.step(0.0,l,auto)
            val sc=driver.scene
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas
                c.save();c.scale(density.density,density.density)
                art.draw(c,l,sc,driver.time,driver.drawNow(auto),pressed)
                c.restore()
            }
        }
        Box(Modifier.offset(x=24.dp,y=(l.top+40).dp).width((l.width-48).dp).height(72.dp).semantics {
            heading();contentDescription="RECEIVE"
        })
        Box(Modifier.offset(x=24.dp,y=(l.top+116).dp).width((l.width-48).dp).height(40.dp).semantics {
            contentDescription="Accept money directly from friends or family\nwithout the usual bank delays."
        })
        Box(Modifier.fillMaxWidth().height(l.panel.dp).semantics {
            role=Role.Button
            contentDescription="A hand holding out banknotes to an open hand. Drag the notes across to send them."
        })
        Box(Modifier.offset(y=(l.height-l.bottom-33).dp).fillMaxWidth().height(17.dp).semantics {
            contentDescription="Already have an account? Log in"
        })
        Box(Modifier.offset(x=20.dp,y=(l.height-l.bottom-16-89).dp).width((l.width-40).dp).height(56.dp).semantics {
            role=Role.Button;contentDescription="Get started"
            onClick { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); true }
        })
    }
}
