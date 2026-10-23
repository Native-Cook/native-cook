package com.ngocdevv.nativecook.blocks.forms.lacedbootstudio

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Eleven continuous leather-thread spans answer the finger, then tighten row by row. */
@Composable
fun LacedBootStudio(autoPlay:Boolean=false,previewTime:Double?=null,modifier:Modifier=Modifier){
 val context=LocalContext.current;val resources=remember(context.applicationContext){LacedBootStudioResources.load(context.applicationContext)}
 val painter=remember(resources){LacedBootStudioPainter(resources)};val driver=remember{LacedBootStudioDriver()};val clock=remember{mutableDoubleStateOf(lacedBootStudioNow())}
 var revision by remember{mutableIntStateOf(0)};var visible by remember{mutableStateOf(true)};val reduced=rememberLacedBootStudioReduced()
 val latestAuto=rememberUpdatedState(autoPlay);val latestReduced=rememberUpdatedState(reduced);val latestPreview=rememberUpdatedState(previewTime)
 val view=LocalView.current;val density=LocalDensity.current;val lifecycle=LocalLifecycleOwner.current;val back=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
 LacedBootStudioSystemBars()
 fun wake(reschedule:Boolean=true){clock.doubleValue=lacedBootStudioNow();if(reschedule)revision++}
 fun impact(){view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)}
 fun act(kind:String,index:Int=0):Boolean{if(!visible)return false;val ok=driver.act(kind,index,lacedBootStudioNow(),latestAuto.value,latestReduced.value,latestPreview.value);if(ok){impact();if(kind=="save")view.announceForAccessibility("Pair saved");wake()};return ok}
 DisposableEffect(lifecycle,driver){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){driver.clear(lacedBootStudioNow());visible=false}else if(event==Lifecycle.Event.ON_START){driver.epoch=lacedBootStudioNow();driver.rendered=null;visible=true}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);driver.clear(lacedBootStudioNow())}}
 LaunchedEffect(reduced){driver.reducedChanged(lacedBootStudioNow());wake()}
 LaunchedEffect(autoPlay,previewTime){driver.clear(lacedBootStudioNow());wake()}
 LaunchedEffect(autoPlay,reduced,previewTime,revision,visible){while(isActive&&visible){val now=lacedBootStudioNow();driver.complete(now,reduced);clock.doubleValue=now;if(driver.needsFrames(autoPlay,reduced,previewTime))withFrameNanos{clock.doubleValue=it/1_000_000_000.0}else{val deadline=driver.nextDeadline()?:break;delay(max(1L,((deadline-now)*1000).toLong()))}}}
 BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF2EDE8))){
  val scale=min(maxWidth.value/393f,maxHeight.value/852f);val ox=(maxWidth.value-393*scale)/2;val oy=(maxHeight.value-852*scale)/2;val pixels=scale*density.density
  Box(Modifier.offset(ox.dp,oy.dp).size((393*scale).dp,(852*scale).dp)){
   Canvas(Modifier.fillMaxSize()){
    val now=clock.doubleValue;val frame=driver.frame(now,autoPlay,reduced,previewTime);driver.rendered=frame;driver.renderedDate=now
    val c=drawContext.canvas.nativeCanvas;c.save();c.scale(pixels,pixels);painter.draw(c,frame,driver.selectedLeather,driver.selectedSole,driver.favorite);c.restore()
   }
   fun Modifier.frame(x:Float,y:Float,w:Float,h:Float)=offset((x*scale).dp,(y*scale).dp).size((w*scale).dp,(h*scale).dp)
   fun Modifier.button(label:String,action:()->Unit)=semantics{contentDescription=label}.clickable(interactionSource=MutableInteractionSource(),indication=null,role=Role.Button,onClick=action)
   Box(Modifier.frame(23f,114f,347f,383f).pointerInput(pixels,reduced,previewTime,visible){
    awaitEachGesture{
     val down=awaitFirstDown(requireUnconsumed=false)
     if(!visible||latestReduced.value||latestPreview.value!=null){waitForUpOrCancellation();return@awaitEachGesture}
     fun point(p:androidx.compose.ui.geometry.Offset)=LacedBootStudioPoint(p.x/pixels+23.0,p.y/pixels+114.0)
     var last=point(down.position)
     if(!driver.begin(last,lacedBootStudioNow(),latestAuto.value,latestReduced.value,latestPreview.value)){waitForUpOrCancellation();return@awaitEachGesture}
     val owner=driver.session!!.id;var normal=false;down.consume();wake()
     try{while(true){val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break;if(change.isConsumed)break;last=point(change.position);val before=driver.session?.progress?:0.0;val active=driver.session?.supportActive==true
       if(driver.move(last,lacedBootStudioNow())){if(kotlin.math.floor((driver.session?.progress?:0.0)+1e-8)>kotlin.math.floor(before+1e-8))impact();wake(!active)}
       change.consume();if(!change.pressed){normal=true;break}}
     }finally{if(driver.session?.id==owner){driver.finish(last,lacedBootStudioNow(),!normal);wake()}}
    }
   })
   Box(Modifier.frame(198f,136f,149f,344f).semantics{
    val s=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime);contentDescription="Alder Lace Boot lacing";stateDescription="${s.rows} of 6 rows, ${(s.tensions[5]*100).roundToInt()} percent tension"
    customActions=listOf(CustomAccessibilityAction("Lace next row"){act("row",1)},CustomAccessibilityAction("Remove last row"){act("row",-1)})
   })
   Box(Modifier.frame(18f,55f,44f,44f).button("Back"){if(visible&&previewTime==null&&driver.session==null){driver.clear(lacedBootStudioNow());wake();back?.onBackPressed()}})
   Box(Modifier.frame(331f,55f,44f,44f).button("Save pair"){act("save")})
   Box(Modifier.frame(79f,68f,235f,18f).semantics{contentDescription="ATELIER ROWE"})
   Box(Modifier.frame(24f,523f,296f,30f).semantics{contentDescription="Alder Lace Boot";heading()})
   Box(Modifier.frame(24f,560f,345f,18f).semantics{contentDescription="Hand-finished leather"})
   Box(Modifier.frame(329f,516f,44f,44f).semantics{contentDescription=if(driver.favorite)"Remove from favorites"else"Add to favorites"}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button){act("favorite")})
   for(i in 0..2)Box(Modifier.frame(16f+40*i,618f,44f,44f).semantics{contentDescription=listOf("Tan leather","Charcoal leather","Chestnut leather")[i];selected=driver.selectedLeather==i}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button){act("leather",i)})
   for(i in 0..1)Box(Modifier.frame(216f+58*i,618f,52f,44f).semantics{contentDescription=if(i==0)"Dark sole"else"Natural sole";selected=driver.selectedSole==i}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button){act("sole",i)})
   Box(Modifier.frame(24f,678f,345f,16f).semantics{contentDescription="Pull the lace through each eyelet."})
   Box(Modifier.frame(24f,754f,345f,54f).semantics{val s=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime);contentDescription=if(s.savedMix>.5)"Pair saved, 245 dollars"else"Save this pair, 245 dollars"}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button){act("save")})
  }
 }
}
@Composable private fun rememberLacedBootStudioReduced():Boolean{
 val resolver=LocalContext.current.contentResolver
 fun read()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 var reduced by remember(resolver){mutableStateOf(read())}
 DisposableEffect(resolver){val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){reduced=read()}};resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);reduced=read();onDispose{resolver.unregisterContentObserver(observer)}}
 return reduced
}
@Composable private fun LacedBootStudioSystemBars(){val window=LocalActivity.current?.window?:return;val view=LocalView.current
 DisposableEffect(window,view){val c=WindowCompat.getInsetsController(window,view);val status=c.isAppearanceLightStatusBars;val nav=c.isAppearanceLightNavigationBars;c.isAppearanceLightStatusBars=true;c.isAppearanceLightNavigationBars=true;c.show(WindowInsetsCompat.Type.statusBars());onDispose{c.isAppearanceLightStatusBars=status;c.isAppearanceLightNavigationBars=nav}}}
