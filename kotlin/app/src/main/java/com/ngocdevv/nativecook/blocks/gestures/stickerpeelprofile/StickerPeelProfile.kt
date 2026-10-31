package com.ngocdevv.nativecook.blocks.gestures.stickerpeelprofile

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
import kotlin.math.*

@Composable
fun StickerPeelProfile(autoPlay:Boolean=false) {
    val context=LocalContext.current; val view=LocalView.current
    val density=LocalDensity.current.density
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { StickerPeelProfileArt(context) }
    val audio=remember(context,reduced) { if(reduced)null else StickerPeelProfileAudio(context) }
    val driver=remember(audio) { StickerPeelProfileDriver({ audio?.play(it) }, { kind -> view.performHapticFeedback(if(kind==0)HapticFeedbackConstants.CLOCK_TICK else if(kind==1)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY) }) }
    var tick by remember { mutableIntStateOf(0) }
    var awake by remember { mutableStateOf(false) }
    var closePressed by remember { mutableStateOf(false) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old=controller?.isAppearanceLightStatusBars; controller?.isAppearanceLightStatusBars=false
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(driver,lifecycle,autoPlay,reduced,awake) {
        if(!(autoPlay && !reduced) && !awake)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) withFrameNanos { ns ->
                val dt=if(previous==0L).016 else (ns-previous)/1e9; previous=ns
                driver.step(dt,autoPlay,reduced); tick++
                if(!(autoPlay && !reduced) && driver.drag<0 && driver.ss.none { it.f>0.00001 || abs(it.vf)>.001 || it.lift>0 || abs(it.sq)>.00001 || abs(it.vsq)>.001 || abs(it.vx)>.01 })awake=false
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value; val h=maxHeight.value
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription="Profile stickers: a bun, a heart, a slay label and a flame. Drag one to peel it off and stick it somewhere else; use two fingers to turn or resize it."
        }.pointerInput(driver,reduced,top,bottom) {
            awaitEachGesture {
                val first=awaitFirstDown(requireUnconsumed=false)
                val x=first.position.x/density; val y=first.position.y/density
                driver.layout(size.width/density.toDouble(),top.toDouble())
                val close=abs(x-size.width/density/2)<28 && abs(y-(size.height/density-bottom-42))<28
                closePressed=close
                if(!close) { driver.begin(x.toDouble(),y.toDouble(),reduced); awake=true }
                var initialDistance=0.0; var initialAngle=0.0; var transformed=false
                try {
                do {
                    val event=awaitPointerEvent(); val pressed=event.changes.filter { it.pressed }
                    if(pressed.size>=2) {
                        val a=pressed[0].position/density; val b=pressed[1].position/density
                        val dx=(b.x-a.x).toDouble(); val dy=(b.y-a.y).toDouble(); val angle=atan2(dy,dx)
                        if(!transformed) {
                            driver.twist=driver.pick(((a.x+b.x)/2).toDouble(),((a.y+b.y)/2).toDouble()); transformed=true
                            initialDistance=max(1.0,hypot(dx,dy)); initialAngle=angle
                            if(driver.twist>=0) { driver.k0=driver.ss[driver.twist].k; driver.r0=driver.ss[driver.twist].rot }
                        }
                        if(driver.twist>=0) {
                            val s=driver.ss[driver.twist]; s.k=(driver.k0*hypot(dx,dy)/initialDistance).coerceIn(.6,1.8)
                            s.rot=driver.r0+(angle-initialAngle)*180/PI; driver.last=driver.now
                        }
                    }
                    if(!close && pressed.isNotEmpty()) {
                        val p=pressed.first().position/density; driver.update(p.x.toDouble(),p.y.toDouble()); tick++
                    }
                    event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
                if(close)view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if(transformed)view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                } finally {
                    closePressed=false
                    if(!close)driver.end()
                    driver.twist=-1; tick++
                }
            }
        }) {
            tick
            driver.layout(size.width/density.toDouble(),top.toDouble())
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas; c.save(); c.scale(density,density)
                art.drawPage(c,size.width/density,size.height/density,top,bottom,density)
                art.draw(c,driver.ss,driver.order)
                art.drawClose(c,size.width/density,size.height/density,bottom,closePressed); c.restore()
            }
        }
        // These retain the RN labels without adding actions to its read-only controls.
        Box(Modifier.offset((w-112).dp,(top+10).dp).size(42.dp).semantics { contentDescription="Message"; role=Role.Button })
        Box(Modifier.offset((w-60).dp,(top+10).dp).size(42.dp).semantics { contentDescription="More"; role=Role.Button })
        listOf("Glass Garden by Mira Sol, 4 hours ago","Night Bus Home by Ode Kim, 4 hours ago","Fizz - Remix by Tavi, Rook, 4 hours ago").forEachIndexed { i,label ->
            Box(Modifier.offset(18.dp,(top+456+i*66).dp).size((w-36).dp,52.dp).semantics { contentDescription=label })
        }
        Box(Modifier.offset((w/2-28).dp,(h-bottom-70).dp).size(56.dp).semantics {
            contentDescription="Close"; role=Role.Button
            onClick { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); true }
        })
    }
}
