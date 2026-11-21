package com.ngocdevv.nativecook.blocks.animations.hourscrubsky

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
fun HourScrubSky(autoPlay:Boolean=true,onHourChange:((Int)->Unit)?=null,modifier:Modifier=Modifier) {
 val context=LocalContext.current;val activity=LocalActivity.current;val view=LocalView.current;val density=LocalDensity.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
 var barLight by remember { mutableStateOf(false) }
 DisposableEffect(context) { val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } };context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);onDispose { context.contentResolver.unregisterContentObserver(observer) } }
 val controller=remember(activity,view) { activity?.window?.let { WindowCompat.getInsetsController(it,view) } }
 DisposableEffect(controller) { val old=controller?.isAppearanceLightStatusBars;val nav=controller?.isAppearanceLightNavigationBars;onDispose { old?.let { controller?.isAppearanceLightStatusBars=it };nav?.let { controller?.isAppearanceLightNavigationBars=it } } }
 LaunchedEffect(controller,barLight) {
  controller?.isAppearanceLightStatusBars = !barLight
  controller?.isAppearanceLightNavigationBars = !barLight
 }
 val art by produceState<HourScrubSkyArtwork?>(null,context) { value=withContext(Dispatchers.Default) { HourScrubSkyArtwork(context.assets) } }
 val renderer=remember(art) { art?.let { HourScrubSkyRenderer(it) } };val engine=remember { HourScrubSkyEngine() };val tick=remember { mutableLongStateOf(0L) }
 SideEffect { engine.onHourChange=onHourChange;engine.buzz={ cue->view.performHapticFeedback(if(cue=="selection")HapticFeedbackConstants.CLOCK_TICK else if(cue=="rigid")HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY) } }
 LaunchedEffect(autoPlay,reduced) { engine.autoPlay=autoPlay;engine.reduced=reduced;engine.auto=autoPlay && !reduced }
 LaunchedEffect(engine) { if((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)!=0) { engine.pin=activity?.intent?.getStringExtra("hourScrubSkyTime")?.toDoubleOrNull();if(engine.pin!=null) { engine.time=engine.pin!!;engine.auto=true } } }
 LaunchedEffect(engine,renderer,lifecycle) {
  if(renderer==null)return@LaunchedEffect
  lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
   var previous=withFrameNanos { it }
   while(true) {
    val time=withFrameNanos { it }
    engine.advance(((time-previous)/1e9).coerceIn(0.0,.05))
    previous=time
    val hour=(engine.frame().h+12)%24
    barLight=hour<6 || hour>=19 || engine.ink[0]>.5
    tick.longValue=time
   }
  }
 }
 BoxWithConstraints(modifier.fillMaxSize()) {
  val width=maxWidth.value.toDouble();val height=maxHeight.value.toDouble();val s=min(width/402,height/870);val ch=height/s;val offset=(width-402*s)/2;val ppp=density.density*s;engine.height=ch
  Canvas(Modifier.fillMaxSize().semantics {
   contentDescription="Port Ansel weather. Drag hour ruler or sun through the day. Tap Play to run, pause or reset."
   tick.longValue
   stateDescription=HourScrubSkyMotion.pillLabel(floor(engine.frame().hp+.5).toInt().coerceIn(0,23))
   customActions=listOf(CustomAccessibilityAction("Play, pause or reset") { engine.tap(39.0,745.0);true },CustomAccessibilityAction("Next half hour") { engine.adjust(1.0);true },CustomAccessibilityAction("Previous half hour") { engine.adjust(-1.0);true },CustomAccessibilityAction("Change temperature unit") { engine.tap(80.0,590.0);true })
  }.pointerInput(engine,ppp,offset) {
   awaitEachGesture {
    val down=awaitFirstDown(requireUnconsumed=false);val start=down.position;val sx=(start.x/density.density-offset)/s;val sy=start.y/ppp
    engine.pressAt(sx,sy);val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,start);var active=false;var attempted=false;var released=false;var distance=0.0;var last=start
    try { while(true) {
      val event=awaitPointerEvent();val pointer=event.changes.firstOrNull { it.id==down.id }?:break;last=pointer.position;tracker.addPosition(pointer.uptimeMillis,pointer.position)
      val delta=pointer.position-start;distance=max(distance,hypot(delta.x.toDouble(),delta.y.toDouble())/density.density)
      if(!attempted && distance>2) { attempted=true;active=engine.beginDrag(sx,sy) }
      if(active)engine.move((pointer.position.x/density.density-offset)/s,pointer.position.y/ppp)
      if(!pointer.pressed) { released=true;break };pointer.consume()
    } } finally {
     if(active)engine.endDrag(if(released)tracker.calculateVelocity().x/ppp else 0.0,released)
     else if(released && !attempted && distance<=10)engine.tap((last.x/density.density-offset)/s,last.y/ppp)
     engine.releasePress()
    }
   }
  }) {
   tick.longValue;val c=drawContext.canvas.nativeCanvas;val r=renderer;c.drawColor(0xff3d7fe6.toInt());if(r!=null) { val save=c.save();c.translate((offset*density.density).toFloat(),0f);c.scale(ppp.toFloat(),ppp.toFloat());r.draw(c,engine,ch,offset/s);c.restoreToCount(save) }
  }
 }
}
