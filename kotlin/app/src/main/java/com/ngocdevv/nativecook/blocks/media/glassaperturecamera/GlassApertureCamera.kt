package com.ngocdevv.nativecook.blocks.media.glassaperturecamera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
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
fun GlassApertureCamera(autoPlay: Boolean = false) {
    val context=LocalContext.current; val view=LocalView.current; val density=LocalDensity.current; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context){GlassApertureCameraArt(context)}; val driver=remember(autoPlay,reduced,view){GlassApertureCameraDriver(autoPlay,reduced){view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)}}; val audio=remember(context){GlassApertureCameraAudio(context)}
    val redraw = remember { mutableIntStateOf(0) }
    val top=max(WindowInsets.statusBars.getTop(density)/density.density,44f); val bottom=max(WindowInsets.navigationBars.getBottom(density)/density.density,20f)
    DisposableEffect(audio){onDispose{audio.close()}}
    DisposableEffect(view){val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull();val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)};val previous=controller?.isAppearanceLightStatusBars;controller?.isAppearanceLightStatusBars=false;onDispose{if(previous!=null)controller.isAppearanceLightStatusBars=previous}}
    LaunchedEffect(driver,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){var previous=0L;while(true)withFrameNanos { n -> val dt=if(previous==0L)0.0 else (n-previous)/1e9;previous=n;driver.step(dt);redraw.intValue++ }}}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value
    val height = maxHeight.value
    Canvas(Modifier.fillMaxSize().semantics { contentDescription="Viewfinder, a street at dusk. Pinch to set the aperture, tap to focus." }.pointerInput(driver,top,bottom){
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false);val scale=density.density;val w=size.width/scale;val h=size.height/scale;val k=(h-top-bottom)/778f;fun y(v:Float)=top+(v-62)*k
            val start = down.position / scale
            val sx = w / 402f
            fun control(x: Float, radius: Float) =
                abs(start.x - x * sx) <= radius && abs(start.y - y(710f)) <= radius
            val shutter = control(199f, 26f)
            val tool = abs(start.y-y(607f)) <= 22 && listOf(48f, 148f, 250f, 349f).any {
                start.x >= it*sx-22 && start.x <= it*sx-22+(if(it==250f)56 else 44)
            }
            val inView = start.y < y(770f) && !shutter && !control(82f,20f) && !control(316f,20f) && !tool
            var end = start
            var endTime = down.uptimeMillis
            var moved = false
            var pinching = false
            var didPinch = false
            var baseDistance = 0.0
            down.consume()
            do {val event=awaitPointerEvent();val active=event.changes.filter{it.pressed};val first=event.changes.firstOrNull{it.id==down.id};if(first!=null){endTime=first.uptimeMillis;end=first.position/scale;if((end-start).getDistance()>10)moved=true}
                if(inView && !shutter && active.size>=2){val d=(active[0].position-active[1].position).getDistance().toDouble();if(!pinching && d>0){pinching=true;didPinch=true;baseDistance=d;driver.beginPinch()} else if(pinching && d>0 && driver.pinch(d/baseDistance)){view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);audio.play(true)}}
                if(pinching && active.size<2){pinching=false;driver.endPinch()}
                event.changes.forEach{it.consume()}
            } while(event.changes.any{it.pressed})
            if(pinching)driver.endPinch() else if(!didPinch && !moved){if(shutter){driver.shoot();view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);audio.play(false)}else if(inView && endTime-down.uptimeMillis<=500){driver.focus(end.x.toDouble(),end.y.toDouble(),w.toDouble(),w*2622.0/1206.0);view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)}}
        }
    }) { redraw.intValue; drawIntoCanvas { val c=it.nativeCanvas;c.save();c.scale(density.density,density.density);art.draw(c,size.width/density.density,size.height/density.density,top,bottom,driver.frame,driver.ringX.toFloat(),driver.ringY.toFloat(),driver.automatic,reduced,driver.loopTime);c.restore() } }
    GlassApertureCameraSemantics(redraw, driver, width, height, top, bottom) {
        driver.shoot()
        audio.play(false)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    }
}

@Composable
private fun GlassApertureCameraSemantics(
    redraw: IntState,
    driver: GlassApertureCameraDriver,
    width: Float,
    height: Float,
    top: Float,
    bottom: Float,
    shoot: () -> Unit,
) {
    redraw.intValue
    val scaleX = width / 402f
    val layoutScale = (height - top - bottom) / 778f
    fun y(v: Float) = if (v < 62) v * top / 62 else top + (v - 62) * layoutScale
    fun Modifier.labelAt(x: Float, centerY: Float, label: String) =
        offset((x * scaleX - 22).dp, (centerY - 22).dp).size(44.dp).semantics {
            contentDescription = label
        }

    val f = driver.frame.fnum
    Box(Modifier.offset((201 * scaleX - 34).dp, (y(360f) - 128 * scaleX - 44).dp)
        .size(68.dp, 30.dp).semantics {
            contentDescription = "Aperture f ${String.format(java.util.Locale.US, "%.1f", f)}"
            progressBarRangeInfo = ProgressBarRangeInfo(GlassApertureCameraMotion.uOf(f).toFloat(), 0f..1f, 20)
            setProgress { driver.adjust(it.toDouble()); true }
        })
    Box(Modifier.labelAt(48f, y(607f), "Favourites"))
    Box(Modifier.labelAt(148f, y(607f), "One shot"))
    Box(Modifier.labelAt(250f, y(607f), "Burst of 5"))
    Box(Modifier.labelAt(349f, y(607f), "Edit"))
    Box(Modifier.labelAt(82f, y(710f), "Flash").semantics { role = Role.Button })
    Box(Modifier.labelAt(316f, y(710f), "Flip camera").semantics { role = Role.Button })
    Box(Modifier.labelAt(199f, y(710f), "Take photo").semantics {
        role = Role.Button
        onClick { shoot(); true }
    })
    Box(Modifier.offset(0.dp, y(770f).dp).size(width.dp, 70.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = if (driver.frame.sent > .5) "Sent to 4 friends" else "Friends @mira. Sharable with other apps"
    })
}

private class GlassApertureCameraAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>();private val tick:Int;private val shutter:Int
    init {pool.setOnLoadCompleteListener{_,id,status->if(status==0)loaded.add(id)};tick=context.assets.openFd("glassaperturecamera/glassaperturecamera_tick.wav").use{pool.load(it,1)};shutter=context.assets.openFd("glassaperturecamera/glassaperturecamera_shutter.wav").use{pool.load(it,1)}}
    fun play(isTick:Boolean){val id=if(isTick)tick else shutter;val volume=if(isTick).45f else .7f;if(id in loaded && manager?.ringerMode==AudioManager.RINGER_MODE_NORMAL)pool.play(id,volume,volume,1,0,1f)}
    fun close(){pool.release()}
}
