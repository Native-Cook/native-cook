package com.ngocdevv.nativecook.blocks.animations.gearpuckspeedline

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.SystemClock
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Full native 10-second score, manual mode swipe/snap, throttle and line pluck. */
@Composable
fun GearPuckSpeedLineDemo(autoPlay:Boolean=true) {
    val context=LocalContext.current
    val score=remember(context){GearPuckSpeedLineScore()}
    val engine=remember(score){GearPuckSpeedLineEngine(score)}
    val art=remember(context){GearPuckSpeedLineArt(context)}
    val reduced=remember{!ValueAnimator.areAnimatorsEnabled()}
    val intent=(context as? Activity)?.intent
    val debug=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE !=0
    val pin=remember{if(debug)intent?.getStringExtra("gear-time")?.toDoubleOrNull() else null}
    val manual=remember{debug&&intent?.getBooleanExtra("gear-manual",false)==true}
    val haptic=LocalHapticFeedback.current
    var frame by remember{mutableStateOf(score.frame(0.0))}
    fun now()=SystemClock.uptimeMillis()/1000.0
    fun refresh(){frame=engine.frame(now(),autoPlay,reduced,pin,manual).copy()}
    LaunchedEffect(autoPlay,reduced,pin,manual) {
        refresh()
        if(reduced)return@LaunchedEffect
        while(true)withFrameNanos {refresh()}
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF070A10))) {
        val scaleDp=min(maxWidth.value/402f,maxHeight.value/732f)
        val leftDp=(maxWidth.value-402f*scaleDp)/2
        Canvas(Modifier.fillMaxSize().pointerInput(reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val s=min(size.width/402.0,size.height/732.0)
                val left=(size.width-402*s)/2
                val sx=(down.position.x-left)/s;val sy=down.position.y/s
                engine.begin(sx,sy,now(),reduced)
                var lastX=sx;var lastY=sy;var lastT=now();var velocity=0.0
                var ended=false
                try {while(true){
                    val event=awaitPointerEvent();val c=event.changes.firstOrNull{it.id==down.id}?:break
                    if(c.isConsumed)break
                    val x=(c.position.x-left)/s;val y=c.position.y/s;val t=now()
                    if(c.pressed){
                        if(t>lastT)velocity=(x-lastX)/(t-lastT)
                        engine.move(x,y,x-sx,y-sy);lastX=x;lastY=y;lastT=t;c.consume();refresh()
                    } else {
                        engine.end(x,x-sx,y-sy,velocity,t,false);ended=true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);refresh();break
                    }
                }} finally {
                    if(!ended){engine.end(lastX,lastX-sx,lastY-sy,0.0,now(),true);refresh()}
                }
            }
        }) {
            val s=min(size.width/402.0,size.height/732.0)
            val left=(size.width-402*s)/2
            drawIntoCanvas{c->c.nativeCanvas.save();c.nativeCanvas.translate(left.toFloat(),0f);c.nativeCanvas.scale(s.toFloat(),s.toFloat());art.draw(c.nativeCanvas,frame,size.height/s);c.nativeCanvas.restore()}
        }
        listOf("P","R","D","S","M").forEachIndexed {i,label->
            Box(Modifier.offset((leftDp+(39+66*i)*scaleDp).dp,(620*scaleDp).dp)
                .size((60*scaleDp).dp,(88*scaleDp).dp).semantics {
                    contentDescription="Drive mode $label";stateDescription=if(engine.target==i)"Selected" else "";role=Role.Button
                    onClick{engine.select(i,now());haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);refresh();true}
                })
        }
        Box(Modifier.offset((leftDp+(frame.crest.toFloat()-100)*scaleDp).dp,(230*scaleDp).dp)
            .size((200*scaleDp).dp,(170*scaleDp).dp).semantics {
                contentDescription="Speed";stateDescription="${frame.speed.roundToInt()} miles per hour"
                progressBarRangeInfo=ProgressBarRangeInfo(frame.speed.toFloat(),0f..197f)
                setProgress{value->engine.begin(frame.crest,300.0,now(),reduced);engine.shown.speed=value.toDouble().coerceIn(0.0,doubleArrayOf(0.0,0.0,132.0,197.0,197.0)[engine.target]);engine.end(frame.crest,0.0,0.0,0.0,now(),true);refresh();true}
            })
    }
}

@Preview
@Composable
private fun GearPuckSpeedLineDemoPreview(){GearPuckSpeedLineDemo()}
