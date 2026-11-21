package com.ngocdevv.nativecook.blocks.lists.arrowrankcards

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

/** Native nine-prism sign, arrow-led card turns and interruptible rank ruler. */
@Composable
fun ArrowRankCards(autoPlay: Boolean=true,modifier: Modifier=Modifier) {
 val context=LocalContext.current;val activity=LocalActivity.current;val view=LocalView.current;val density=LocalDensity.current
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(activity,view) {
  val controller=activity?.window?.let { WindowCompat.getInsetsController(it,view) };val oldStatus=controller?.isAppearanceLightStatusBars;val oldNav=controller?.isAppearanceLightNavigationBars
  controller?.isAppearanceLightStatusBars=true;controller?.isAppearanceLightNavigationBars=true
  onDispose { oldStatus?.let { controller?.isAppearanceLightStatusBars=it };oldNav?.let { controller?.isAppearanceLightNavigationBars=it } }
 }
 var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
 DisposableEffect(context) { val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } };context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);onDispose { context.contentResolver.unregisterContentObserver(observer) } }
 val art by produceState<ArrowRankCardsArtwork?>(null,context) { value=withContext(Dispatchers.Default) { ArrowRankCardsArtwork(context.assets) } }
 val renderer=remember(art) { art?.let { ArrowRankCardsRenderer(it) } }
 val engine=remember { ArrowRankCardsEngine() };val tick=remember { mutableLongStateOf(0) }
 SideEffect { engine.buzz={ cue->view.performHapticFeedback(if(cue=="selection")HapticFeedbackConstants.CLOCK_TICK else if(cue=="rigid")HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY) } }
 LaunchedEffect(autoPlay,reduced) { engine.autoPlay=autoPlay;engine.reduced=reduced;engine.auto=autoPlay && !reduced;if(reduced)engine.live=ArrowRankCardsMotion.rest() }
 LaunchedEffect(engine,activity) { if(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { engine.pin=activity?.intent?.getStringExtra("arrowRankCardsTime")?.toDoubleOrNull()?.takeIf { it.isFinite() };if(engine.pin!=null)engine.auto=true } }
 LaunchedEffect(engine,renderer,lifecycle) { if(renderer==null)return@LaunchedEffect;lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { var previous=withFrameNanos { it };while(true) { val time=withFrameNanos { it };engine.step((time-previous)/1e9);previous=time;tick.longValue=time } } }
 BoxWithConstraints(modifier.fillMaxSize()) {
  val s=maxWidth.value/402.0;val ppp=density.density*s;val inset=WindowInsets.statusBars.getTop(density)/density.density;val top=max(0.0,inset-62*s);val h=(maxHeight.value-top)/s;val deck=min(1.0,(h-204)/670);val lower=178+596*deck
  Canvas(Modifier.fillMaxSize().semantics {
   contentDescription="Popular album chart. Swipe in the arrow direction to turn the sign. Drag ruler to scrub ranks."
   customActions=listOf(CustomAccessibilityAction("Next chart") { engine.advance();true },CustomAccessibilityAction("Save album") { engine.bookmark();true })
  }.pointerInput(engine,ppp,deck,lower) {
   awaitEachGesture {
    val down=awaitFirstDown(requireUnconsumed=false);val start=down.position;val x=start.x/ppp;val y=(start.y-top*density.density)/ppp
    val region=if(x>=94 && x<=311 && y>=lower && y<=lower+64)2 else if(x>=321 && x<=385 && y>=178+57*deck && y<=178+121*deck)3 else if(y>=lower && y<=lower+64 && x>=330)3 else if(y>=lower && y<=lower+64 && x<=75)4 else if(x>=349 && y>=68 && y<=112)4 else if(x>=8 && x<=394 && y>=178+44*deck && y<=178+578*deck)1 else 0
    val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position);var active=false;var released=false;var distance=0.0
    if(region==2)engine.rulerBegin()
    try {
     while(true) {
      val event=awaitPointerEvent();val change=event.changes.firstOrNull { it.id==down.id }?:break
      tracker.addPosition(change.uptimeMillis,change.position);val delta=change.position-start;distance=max(distance,hypot(delta.x.toDouble(),delta.y.toDouble())/density.density)
      if(region==1 && !active && distance>2) { active=true;engine.begin() }
      if(region==1 && active)engine.move(delta.x/ppp,delta.y/ppp)
      if(region==2)engine.rulerMove(delta.x/ppp)
      if(!change.pressed) { released=true;break };change.consume()
     }
    } finally {
     val velocity=tracker.calculateVelocity();val vx=if(released)velocity.x/ppp else 0.0;val vy=if(released)velocity.y/ppp else 0.0
     if(region==1) { if(active)engine.end(vx,vy,released) else if(released)engine.hint() }
     else if(region==2)engine.rulerEnd(vx)
     else if(released && distance<10) { if(region==3)engine.bookmark() else if(region==4)engine.hint() }
    }
   }
  }) {
   tick.longValue;val c=drawContext.canvas.nativeCanvas;val r=renderer
   c.drawColor(0xffebebea.toInt());if(r!=null) { val save=c.save();c.translate(0f,(top*density.density).toFloat());c.scale(ppp.toFloat(),ppp.toFloat());r.draw(c,engine.frame(),h,deck,lower,engine.saved,engine.bookmarkScale);c.restoreToCount(save) }
  }
 }
}
