package com.ngocdevv.nativecook.blocks.charts.focusinhometiles
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

@Composable fun FocusInHomeTiles(autoPlay:Boolean=true){
    val context=LocalContext.current;val view=LocalView.current;val activity=LocalActivity.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val density=LocalDensity.current
    val renderer=remember(context){FocusInHomeTilesRenderer(context)}
    val engine=remember{FocusInHomeTilesEngine().apply{val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;manual=debug&&activity?.intent?.getBooleanExtra("focusInHomeTilesManual",false)==true;pin=if(debug)activity?.intent?.getStringExtra("focusInHomeTilesTime")?.toDoubleOrNull()else null;onHaptic={kind->view.performHapticFeedback(when(kind){0->HapticFeedbackConstants.KEYBOARD_TAP;1->HapticFeedbackConstants.VIRTUAL_KEY;else->HapticFeedbackConstants.CLOCK_TICK})}}}
    var reduced by remember{mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)}
    DisposableEffect(context){val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}};context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);onDispose{context.contentResolver.unregisterContentObserver(observer)}}
    DisposableEffect(activity,view){val controller=activity?.window?.let{WindowCompat.getInsetsController(it,view)};val status=controller?.isAppearanceLightStatusBars;val nav=controller?.isAppearanceLightNavigationBars;controller?.isAppearanceLightStatusBars=true;controller?.isAppearanceLightNavigationBars=true;onDispose{status?.let{controller.isAppearanceLightStatusBars=it};nav?.let{controller.isAppearanceLightNavigationBars=it}}}
    var frame by remember{mutableStateOf(engine.frame)}
    LaunchedEffect(autoPlay,reduced,lifecycle){engine.configure(autoPlay,reduced);frame=engine.frame;lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){var previous=withFrameNanos{it};while(true){val now=withFrameNanos{it};engine.advance(((now-previous)/1e9).coerceIn(0.0,.05));previous=now;frame=engine.frame}}}
    BoxWithConstraints(Modifier.fillMaxSize()){
        val scale=maxWidth.value/402f;val pixels=scale*density.density;val height=maxHeight.value/scale
        LaunchedEffect(height){engine.viewport(height.toDouble());frame=engine.frame}
        Canvas(Modifier.fillMaxSize().semantics{contentDescription="Smart home tiles";stateDescription="Page ${(floor(frame.s/780+.5).toInt()+1).coerceIn(1,3)} of 3";customActions=listOf(CustomAccessibilityAction("Next page"){engine.page(1);true},CustomAccessibilityAction("Previous page"){engine.page(-1);true},CustomAccessibilityAction("Focus header"){engine.focus(0);true},CustomAccessibilityAction("Focus Bedroom"){engine.focus(1);true},CustomAccessibilityAction("Focus Live Chat"){engine.focus(2);true})}.pointerInput(pixels,reduced){awaitEachGesture{
            val down=awaitFirstDown(false);engine.begin(down.position.x/pixels.toDouble(),down.position.y/pixels.toDouble());val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position);var completed=false
            try{do{val event=awaitPointerEvent();val p=event.changes.firstOrNull{it.id==down.id}?:break;tracker.addPosition(p.uptimeMillis,p.position);val x=p.position.x/pixels.toDouble();val y=p.position.y/pixels.toDouble();if(p.pressed){engine.move(x,y);p.consume()};if(!p.pressed){completed=true;engine.end(x,y,tracker.calculateVelocity().y/pixels.toDouble());break}}while(true)}finally{if(!completed)engine.end(0.0,0.0,0.0,true)}
        }}){val c=drawContext.canvas.nativeCanvas;val save=c.save();c.scale(pixels,pixels);renderer.draw(c,frame,height.toDouble(),pixels.toDouble(),engine.clock);c.restoreToCount(save)}
    }
}
