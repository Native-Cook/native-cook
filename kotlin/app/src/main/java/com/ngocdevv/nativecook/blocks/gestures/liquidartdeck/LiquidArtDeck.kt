package com.ngocdevv.nativecook.blocks.gestures.liquidartdeck

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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

internal class LiquidArtDeckAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds=mutableMapOf<String,Int>()
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)loaded.add(id) }
        for(name in listOf("like","pass","undo"))context.assets.openFd("liquidartdeck/liquidartdeck_lad-$name.wav").use { sounds[name]=pool.load(it,1) }
    }
    fun play(name: String) {
        val id=sounds[name]?:return
        if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let { pool.stop(it) }
        val v=if(name=="pass").55f else .75f
        streams[name]=pool.play(id,v,v,1,0,1f)
    }
    fun close()=pool.release()
}

@Composable
fun LiquidArtDeck(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current.density
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density.toDouble()
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val driver=remember(autoPlay,reduced) { LiquidArtDeckMotion.Driver() }
    val art=remember(context) { LiquidArtDeckArt(context) }
    val audio=remember(context,reduced) { if(reduced)null else LiquidArtDeckAudio(context) }
    val tick=remember { mutableIntStateOf(0) }
    val count=remember { mutableIntStateOf(12) }
    val front=remember { mutableIntStateOf(0) }
    val vibrator=remember { context.getSystemService(Vibrator::class.java) }
    fun act(kind: Int) {
        if(driver.act(kind)) {
            audio?.play(if(kind==3)"undo" else if(kind==1)"like" else "pass")
            if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==3)VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(12,45))
            tick.intValue++
        }
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,reduced) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true)withFrameNanos { ns ->
                val dt=min(.5,if(previous==0L).016 else (ns-previous)/1e9);previous=ns
                driver.step(dt,autoPlay,reduced)
                tick.intValue++
                if(count.intValue!=driver.deck.count)count.intValue=driver.deck.count
                if(front.intValue!=driver.deck.cur)front.intValue=driver.deck.cur
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value.toDouble();val h=maxHeight.value.toDouble();val cy=top+340
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position;var end=start
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,start)
                val x=start.x/density;val y=start.y/density
                val action=if(y>=cy+214 && y<=cy+272) {
                    if(abs(x-(w/2-74))<=29)2 else if(abs(x-w/2)<=23)3 else if(abs(x-(w/2+74))<=29)1 else 0
                } else 0
                driver.touch()
                val d=driver.deck
                if(action==0 && d.fly<0 && d.cur<6) { d.held=1;d.vx=0.0;d.vy=0.0 }
                down.consume()
                do {
                    val event=awaitPointerEvent();val change=event.changes.firstOrNull { it.id==down.id }?:break
                    end=change.position;tracker.addPosition(change.uptimeMillis,end)
                    if(change.pressed && d.held==1) {
                        driver.last=driver.now;d.dx=(end.x-start.x)/density.toDouble();d.dy=(end.y-start.y)/density*.6
                    }
                    change.consume()
                } while(change.pressed && event.changes.any { it.pressed })
                val v=tracker.calculateVelocity()
                if(action>0 && (end-start).getDistance()/density<12)act(action)
                else if(d.held==1) {
                    d.held=0;driver.last=driver.now
                    val proj=d.dx+v.x/density*.18
                    if(proj>120)act(1) else if(proj< -120)act(2)
                    else { d.vx=v.x/density.toDouble();d.vy=v.y/density*.6 }
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas;c.save();c.scale(density,density)
                art.draw(c,w,h,top,bottom,driver.deck,driver.drawingNow(autoPlay && !reduced),driver.phase,reduced);c.restore()
            }
        }
        val i=min(front.intValue,5)
        Box(Modifier.offset((w/2-141).dp,(cy-192).dp).size(282.dp,384.dp).semantics {
            contentDescription=if(front.intValue<6)"${LiquidArtDeckMotion.titles[i]} by ${LiquidArtDeckMotion.authors[i].drop(1)}, ${LiquidArtDeckMotion.prices[i]}. Swipe right to add to favourites, left to pass." else "No more art"
            onClick("Add to favourites") { act(1);true }
            customActions=listOf(CustomAccessibilityAction("Pass") { act(2);true })
        })
        for(kind in listOf(2,3,1)) {
            val x=if(kind==2)w/2-103 else if(kind==3)w/2-23 else w/2+45
            val s=if(kind==3)46 else 58
            Box(Modifier.offset(x.dp,(cy+214+if(kind==3)6 else 0).dp).size(s.dp).semantics {
                role=Role.Button;contentDescription=if(kind==2)"Pass" else if(kind==3)"Undo" else "Add to favourites"
                onClick { act(kind);true }
            })
        }
        Box(Modifier.offset((w*.625-20).dp,(h-bottom-57).dp).size(40.dp).semantics { contentDescription="Favourites, ${count.intValue}" })
    }
}
