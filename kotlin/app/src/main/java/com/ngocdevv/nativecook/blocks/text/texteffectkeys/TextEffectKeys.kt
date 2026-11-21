package com.ngocdevv.nativecook.blocks.text.texteffectkeys
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable fun TextEffectKeys(autoPlay:Boolean=true){
    val context=LocalContext.current;val view=LocalView.current;val activity=LocalActivity.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val density=LocalDensity.current
    val renderer=remember(context){TextEffectKeysRenderer(context)}
    val engine=remember{TextEffectKeysEngine(renderer.geo()).apply{val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;manual=debug&&activity?.intent?.getBooleanExtra("textEffectKeysManual",false)==true;pin=if(debug)activity?.intent?.getStringExtra("textEffectKeysTime")?.toDoubleOrNull()else null;onHaptic={kind->view.performHapticFeedback(when(kind){0->HapticFeedbackConstants.CLOCK_TICK;1->HapticFeedbackConstants.KEYBOARD_TAP;else->HapticFeedbackConstants.LONG_PRESS})}}}
    var reduced by remember{mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)}
    DisposableEffect(context){val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}};context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);onDispose{context.contentResolver.unregisterContentObserver(observer)}}
    DisposableEffect(activity,view){val controller=activity?.window?.let{WindowCompat.getInsetsController(it,view)};val status=controller?.isAppearanceLightStatusBars;val nav=controller?.isAppearanceLightNavigationBars;controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false;onDispose{status?.let{controller.isAppearanceLightStatusBars=it};nav?.let{controller.isAppearanceLightNavigationBars=it}}}
    var frame by remember{mutableStateOf(engine.frame)}
    LaunchedEffect(autoPlay,reduced,lifecycle){engine.configure(autoPlay,reduced);frame=engine.frame;lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){var previous=withFrameNanos{it};while(true){val now=withFrameNanos{it};engine.advance(((now-previous)/1e9).coerceIn(0.0,.05));previous=now;frame=engine.frame}}}
    val top=WindowInsets.statusBars.getTop(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()){
        val scale=maxWidth.value/402f;val pixels=scale*density.density;val oy=maxHeight.value/scale-874;val nav=max(top/scale+4,62+min(oy,0f));val fit=min(1f,max(.35f,(505+oy-nav-56)/(505-85)));val conversation=1-(1-fit)*min(1f,max(0f,(-oy-16)/140))
        Canvas(Modifier.fillMaxSize().semantics{contentDescription="Message to Odile. Text effects";stateDescription="Whaaaaat";customActions=listOf(CustomAccessibilityAction("Send message"){engine.send();true})+renderer.labels.mapIndexed{k,label->CustomAccessibilityAction(label){engine.choose(k);true}}}.pointerInput(pixels,oy,conversation,reduced){
            awaitEachGesture{val down=awaitFirstDown(false)
                fun y(raw:Float):Double{val y=raw/pixels-oy;return(if(y<505)505+(y-505)/conversation else y).toDouble()}
                engine.begin(down.position.x/pixels.toDouble(),y(down.position.y));val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position);var completed=false
                try{do{val event=awaitPointerEvent();val p=event.changes.firstOrNull{it.id==down.id}?:break;tracker.addPosition(p.uptimeMillis,p.position);val v=tracker.calculateVelocity();val x=p.position.x/pixels.toDouble();val yy=y(p.position.y)
                    if(p.pressed){engine.move(x,yy,v.x/pixels.toDouble(),v.y/pixels.toDouble()/(if(p.position.y/pixels-oy<505)conversation.toDouble()else 1.0));p.consume()}
                    if(!p.pressed){completed=true;engine.end(x,yy);break}
                }while(true)}finally{if(!completed)engine.end(0.0,0.0,true)}
            }
        }){val c=drawContext.canvas.nativeCanvas;val save=c.save();c.scale(pixels,pixels);renderer.draw(c,frame,engine.geo,oy.toDouble(),nav.toDouble(),conversation.toDouble(),maxHeight.value/scale.toDouble());c.restoreToCount(save)}
    }
}
