package com.ngocdevv.nativecook.blocks.inputs.elasticstringquiz

import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.SoundPool
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

/** A three-cord synonym quiz. The event composer drives both touch and the twelve-second demo. */
@Composable
fun ElasticStringQuiz(autoPlay:Boolean=true,sound:Boolean=true){
    val context=LocalContext.current;val view=LocalView.current;val activity=LocalActivity.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val density=LocalDensity.current
    val renderer=remember(context){ElasticStringQuizRenderer(context)}
    val engine=remember{ElasticStringQuizEngine(renderer.script(),renderer.words).apply{
        val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        manual=debug&&activity?.intent?.getBooleanExtra("elasticStringQuizManual",false)==true
        pin=if(debug)activity?.intent?.getStringExtra("elasticStringQuizTime")?.toDoubleOrNull()else null
        onHaptic={kind->view.performHapticFeedback(when(kind){0->HapticFeedbackConstants.KEYBOARD_TAP;1->HapticFeedbackConstants.VIRTUAL_KEY;2->HapticFeedbackConstants.LONG_PRESS;else->HapticFeedbackConstants.CLOCK_TICK})}
    }}
    val currentSound by rememberUpdatedState(sound)
    DisposableEffect(context){
        val pool=SoundPool.Builder().setMaxStreams(6).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        val ids=listOf("pluck_c4","pluck_e4","pluck_g4","resolve","snap").map{context.assets.openFd("elasticstringquiz/elasticstringquiz_$it.wav").use{fd->pool.load(fd,1)}}
        engine.onSound={i,level->if(currentSound&&i in ids.indices){val volume=level.coerceIn(.15,1.0).toFloat();pool.play(ids[i],volume,volume,1,0,1f)}}
        onDispose{engine.onSound=null;pool.release()}
    }
    var reduced by remember{mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)}
    DisposableEffect(context){val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}};context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);onDispose{context.contentResolver.unregisterContentObserver(observer)}}
    DisposableEffect(activity,view){val controller=activity?.window?.let{WindowCompat.getInsetsController(it,view)};val status=controller?.isAppearanceLightStatusBars;val nav=controller?.isAppearanceLightNavigationBars;controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false;onDispose{status?.let{controller.isAppearanceLightStatusBars=it};nav?.let{controller.isAppearanceLightNavigationBars=it}}}
    var frame by remember{mutableStateOf(engine.frame)}
    val word = renderer.words[frame.w.toInt()]
    LaunchedEffect(autoPlay,reduced,lifecycle){engine.configure(autoPlay,reduced);frame=engine.frame;lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){var previous=withFrameNanos{it};while(true){val now=withFrameNanos{it};engine.advance(((now-previous)/1e9).coerceIn(0.0,.05));previous=now;frame=engine.frame}}}
    val safeTop=WindowInsets.statusBars.getTop(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()){
        val scale=maxWidth.value/402f;val pixels=scale*density.density;val dy=(maxHeight.value/scale-874)/2;val top=-dy;val bottom=maxHeight.value/scale-dy;val hudY=(max(safeTop,20f)+12)/scale-dy
        Canvas(Modifier.fillMaxSize().semantics{
            contentDescription="Synonym quiz. $word";stateDescription="Choose anchor, gather, or wander"
            customActions=renderer.answers.mapIndexed{i,label->CustomAccessibilityAction(label){engine.begin(i);engine.end();true}}+CustomAccessibilityAction("Continue round"){engine.continueRound();true}
        }.pointerInput(pixels,dy,reduced){
            awaitEachGesture{
                val down=awaitFirstDown(false);val x=down.position.x/pixels;val y=down.position.y/pixels-dy
                val bead=if(y in 618f..728f)(0..2).firstOrNull{abs(x-listOf(81f,201f,321f)[it])<=44}else null
                val recap=engine.frame.recapA>.5
                if(!recap){if(bead!=null)engine.begin(bead)else engine.strumBegin(x.toDouble(),y.toDouble())}
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position);var completed=false
                try{
                    do{
                        val event=awaitPointerEvent();val p=event.changes.firstOrNull{it.id==down.id}?:break;tracker.addPosition(p.uptimeMillis,p.position);val v=tracker.calculateVelocity()
                        if(p.pressed&&!recap){if(bead!=null)engine.move(((p.position.x-down.position.x)/pixels).toDouble(),((p.position.y-down.position.y)/pixels).toDouble(),v.y/pixels.toDouble())else engine.strumMove((p.position.x/pixels).toDouble(),(p.position.y/pixels-dy).toDouble(),v.x/pixels.toDouble());p.consume()}
                        if(!p.pressed){completed=true;if(recap)engine.continueRound()else if(bead!=null)engine.end()else engine.strumEnd();break}
                    }while(true)
                }finally{if(!completed){if(bead!=null)engine.end(true)else engine.strumEnd()}}
            }
        }){
            val c=drawContext.canvas.nativeCanvas;val save=c.save();c.scale(pixels,pixels);c.translate(0f,dy)
            renderer.draw(c,frame,top.toDouble(),bottom.toDouble(),hudY.toDouble(),(pixels/density.density*view.resources.displayMetrics.density).toDouble(),engine.matched)
            c.restoreToCount(save)
        }
    }
}
