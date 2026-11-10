package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlin.math.*

@Composable
fun FilamentBulbDimmer(autoPlay:Boolean=false) {
    val context=LocalContext.current;val view=LocalView.current;val density=LocalDensity.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val resolver=LocalFontFamilyResolver.current
    val art=remember(context,resolver){FilamentBulbDimmerArt(arrayOf("Regular","Medium","SemiBold","Bold").map{val family=FontFamily(Font("filamentbulbdimmer/filamentbulbdimmer_Inter-$it.ttf",context.assets));resolver.resolve(family).value as Typeface}.toTypedArray())}
    val audio=remember(context){FilamentBulbDimmerAudio(context)}
    val driver=remember(autoPlay,reduced){FilamentBulbDimmerDriver(autoPlay && !reduced)}
    val revision=remember{mutableLongStateOf(0)};val announced=remember{mutableStateOf(Triple(true,78,2))};var wake by remember{mutableIntStateOf(0)}
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble();val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    val buzz:(Int)->Unit=remember(view){{kind->view.performHapticFeedback(if(kind==0)android.view.HapticFeedbackConstants.CLOCK_TICK else android.view.HapticFeedbackConstants.CONTEXT_CLICK);Unit}}
    val cue:(String)->Unit=remember(audio){{audio.play(it)}}
    DisposableEffect(audio){onDispose{audio.close()}}
    DisposableEffect(view){val activity=generateSequence(view.context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull();val controller=activity?.let{WindowCompat.getInsetsController(it.window,view)};val previous=controller?.isAppearanceLightStatusBars;controller?.isAppearanceLightStatusBars=false;onDispose{if(previous!=null)controller.isAppearanceLightStatusBars=previous}}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=remember(maxWidth,maxHeight,top,bottom){FilamentBulbDimmerTheme.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom)}
        LaunchedEffect(driver,l,lifecycle,wake){lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){var previous=0L;while(true){withFrameNanos{nanos->val dt=if(previous==0L)0.016 else ((nanos-previous)/1e9).coerceIn(0.0,.5);previous=nanos;val f=driver.step(dt,autoPlay,reduced,l,buzz,cue);val value=Triple(f.on==1.0,(f.level*100).roundToInt(),f.trail.roundToInt().coerceIn(0,4));if(announced.value!=value)announced.value=value;revision.longValue++};if(reduced && !driver.moving)awaitCancellation()}}}
        fun changed(){revision.longValue++;wake++}
        Canvas(Modifier.fillMaxSize().pointerInput(driver,l,reduced){awaitEachGesture{
            val down=awaitFirstDown(requireUnconsumed=false);val start=down.position/density.density
            driver.touch(0,start.x.toDouble(),start.y.toDouble(),0.0,0.0,l,reduced,buzz,cue);changed();if(driver.target==0)return@awaitEachGesture
            var end=start;var ok=false;down.consume()
            try{do{val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break;end=change.position/density.density;if(change.pressed)driver.touch(1,end.x.toDouble(),end.y.toDouble(),(end.x-start.x).toDouble(),(end.y-start.y).toDouble(),l,reduced,buzz,cue)else ok=true;revision.longValue++;change.consume()}while(change.pressed)}
            finally{driver.touch(if(ok)2 else 3,end.x.toDouble(),end.y.toDouble(),(end.x-start.x).toDouble(),(end.y-start.y).toDouble(),l,reduced,buzz,cue);changed()}
        }}){revision.longValue;drawIntoCanvas{art.draw(it.nativeCanvas,l,driver.frame,density.density,reduced)}}
        val (on,pct,selected)=announced.value
        Box(Modifier.offset((l.anchorX-130).dp,(l.restY-12).dp).size(260.dp,(3.74*l.r+8).dp).semantics{role=Role.Switch;contentDescription="Living Room Lamp";stateDescription=if(on)"On"else"Off";onClick("Pull down to switch, or swing it"){driver.takeOver();driver.toggle(false,buzz,cue);changed();true}})
        Box(Modifier.offset(l.toggle.left.dp,l.toggle.top.dp).size(l.toggle.width().dp,l.toggle.height().dp).semantics{role=Role.Switch;contentDescription="Lamp power";stateDescription=if(on)"On"else"Off";onClick{driver.powerSwitch(reduced,buzz,cue);changed();true}})
        Box(Modifier.offset((l.cx-l.rOut-10).dp,(l.cy-l.rOut-10).dp).size((2*l.rOut+20).dp,(l.rOut+l.leg+44).dp).semantics{contentDescription="Intensity";progressBarRangeInfo=ProgressBarRangeInfo(pct.toFloat(),0f..100f,19);setProgress{driver.takeOver();driver.setLevel(l,l.arcPoint(it/100.0).first,l.arcPoint(it/100.0).second,buzz);changed();true};customActions=listOf(CustomAccessibilityAction("Increment"){driver.adjust(.1);changed();true},CustomAccessibilityAction("Decrement"){driver.adjust(-.1);changed();true})})
        for(i in 0..4)Box(Modifier.offset((FilamentBulbDimmerTheme.xs[i]*l.sx-22).dp,(l.dotsY-22).dp).size(44.dp).semantics{role=Role.RadioButton;this.selected=selected==i;contentDescription="${FilamentBulbDimmerTheme.names[i]} light";onClick{driver.choose(i.toDouble(),reduced,buzz);changed();true}})
        Box(Modifier.offset((43*l.sx-22).dp,(l.y(65.0)-22).dp).size(44.dp).semantics{role=Role.Button;contentDescription="Back";onClick{true}})
    }
}
