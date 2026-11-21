package com.ngocdevv.nativecook.blocks.gestures.trailparallaxtracker

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.*

/** Multi-plane walk with a continuous analytic fling and a separate native distance/waypoint state. */
@Composable
fun TrailParallaxTrackerDemo(autoPlay:Boolean=true){
 val context=LocalContext.current;val engine=remember{TrailParallaxTrackerEngine()};val art=remember(context){TrailParallaxTrackerArt(context)}
 val reduced=remember{!ValueAnimator.areAnimatorsEnabled()};val intent=(context as? Activity)?.intent;val debug=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
 val pin=remember{if(debug)intent?.getStringExtra("trail-time")?.toDoubleOrNull()else null};val manual=remember{debug&&intent?.getBooleanExtra("trail-manual",false)==true}
 val controller=remember(context){
  val window=(context as? Activity)?.window
  window?.let{WindowCompat.getInsetsController(it,it.decorView)}
 }
 DisposableEffect(controller){
  val oldStatus=controller?.isAppearanceLightStatusBars;val oldNavigation=controller?.isAppearanceLightNavigationBars
  onDispose{if(oldStatus!=null)controller?.isAppearanceLightStatusBars=oldStatus;if(oldNavigation!=null)controller?.isAppearanceLightNavigationBars=oldNavigation}
 }
 val haptic=LocalHapticFeedback.current;val scope=rememberCoroutineScope();var burst by remember{mutableIntStateOf(0)};var f by remember{mutableStateOf(TrailParallaxTrackerScore.frame(0.0))}
 val darkStatusIcons=TrailParallaxTrackerLight.hour(f.d)<3
 LaunchedEffect(controller,darkStatusIcons){
  controller?.isAppearanceLightStatusBars=darkStatusIcons
  controller?.isAppearanceLightNavigationBars=false
 }
 fun now()=SystemClock.uptimeMillis()/1000.0
 fun refresh(){f=engine.frame(now(),autoPlay,pin,manual,reduced).copy();if(reduced){val token=++burst;scope.launch{val start=now();while(now()-start<.6&&token==burst)withFrameNanos{f=engine.frame(now(),autoPlay,pin,manual,true).copy()}}}}
 LaunchedEffect(autoPlay,reduced,pin,manual){refresh();if(reduced)return@LaunchedEffect;while(true)withFrameNanos{refresh()}}
 BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF060606))){
  val scaleDp=maxWidth.value.toDouble()/402;val dy=maxHeight.value/scaleDp-874
  Canvas(Modifier.fillMaxSize().pointerInput(reduced){
   awaitEachGesture {
    val down=awaitFirstDown(requireUnconsumed=false);val s=size.width/402.0;val start=down.position;engine.begin(now());refresh()
    var lastX=start.x.toDouble();var lastTime=now();var velocity=0.0;var tx=0.0;var ty=0.0;var ended=false
    try{while(true){
     val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break;if(change.isConsumed)break
     val x=change.position.x.toDouble();val y=change.position.y.toDouble();val t=now();tx=x-start.x;ty=y-start.y
     if(change.pressed){if(t>lastTime)velocity=(x-lastX)/(t-lastTime)/s;engine.move(tx/s,t);lastX=x;lastTime=t;change.consume();refresh()}
     else{engine.end(velocity,t,false);if(hypot(tx,ty)<8*s){val base=500+size.height/s-874;engine.tap(x/s,y/s,base,t);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)};ended=true;refresh();break}
    }}finally{if(!ended){engine.end(0.0,now(),true);refresh()}}
   }
  }){val s=size.width/402.0;drawIntoCanvas{c->c.nativeCanvas.save();c.nativeCanvas.scale(s.toFloat(),s.toFloat());art.draw(c.nativeCanvas,f,size.height/s,reduced,s);c.nativeCanvas.restore()}}
  Box(Modifier.fillMaxSize().semantics {
   contentDescription="Trail distance";stateDescription=String.format(java.util.Locale.US,"%.1f miles walked, %.1f miles remaining",f.d.coerceIn(0.0,150.0),825.7-f.d.coerceIn(0.0,150.0))
   progressBarRangeInfo=ProgressBarRangeInfo(f.d.coerceIn(0.0,150.0).toFloat(),0f..150f,1499)
   setProgress{distance->engine.adjust(distance.toDouble(),now());refresh();true}
  })
  val foot=TrailParallaxTrackerMath.ground(200+f.d*24,500+dy)
  Box(Modifier.offset((164*scaleDp).dp,((foot-107)*scaleDp).dp).size((72*scaleDp).dp,(107*scaleDp).dp).semantics {
   contentDescription="Hiker";stateDescription="Tap to wave";role=Role.Button
   onClick{engine.begin(now());engine.tap(200.0,foot-45,500+dy,now());engine.end(0.0,now(),true);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);refresh();true}
  })
  val waypoint=TrailParallaxTrackerWorld.waypoints[f.cardTo.toInt().coerceIn(0,7)]
  Box(Modifier.offset((14*scaleDp).dp,((676+dy)*scaleDp).dp).size((374*scaleDp).dp,(95*scaleDp).dp).semantics{contentDescription=waypoint.name+". "+waypoint.note})
  for((i,label)in listOf("Trail","Map","Badge").withIndex())Box(Modifier.offset(((doubleArrayOf(65.0,189.0,313.0)[i])*scaleDp).dp,((801+dy)*scaleDp).dp).size((24*scaleDp).dp).semantics{contentDescription=label;stateDescription=if(i==0)"Selected" else ""})
 }
}
@Preview
@Composable
private fun TrailParallaxTrackerDemoPreview(){TrailParallaxTrackerDemo()}
