package com.ngocdevv.nativecook.blocks.media.shapeshiftviewfinder

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.*

/** Native camera-shaped crop, six-band morphing outline, photo flip, zoom/rotation and shutter iris. */
@Composable
fun ShapeShiftViewfinderDemo(autoPlay:Boolean=true) {
    val context=LocalContext.current
    val engine=remember{ShapeShiftViewfinderEngine()}
    val art=remember(context){ShapeShiftViewfinderArt(context)}
    val reduced=remember{!ValueAnimator.areAnimatorsEnabled()}
    val intent=(context as? Activity)?.intent
    val debug=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
    val pin=remember{if(debug)intent?.getStringExtra("viewfinder-time")?.toDoubleOrNull() else null}
    val manual=remember{debug&&intent?.getBooleanExtra("viewfinder-manual",false)==true}
    val displayDensity=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(displayDensity)/displayDensity.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(displayDensity)/displayDensity.density.toDouble()
    val haptic=LocalHapticFeedback.current
    var f by remember{mutableStateOf(ShapeShiftViewfinderFrame())}
    val scope=rememberCoroutineScope()
    var burst by remember{mutableIntStateOf(0)}
    fun now()=SystemClock.uptimeMillis()/1000.0
    fun refresh(){
        f=engine.frame(now(),autoPlay,pin,manual,reduced).copy()
        if(reduced){val token=++burst;scope.launch {val start=now();while(now()-start<0.35&&token==burst)withFrameNanos{f=engine.frame(now(),autoPlay,pin,manual,true).copy()}}}
    }
    LaunchedEffect(autoPlay,reduced,pin,manual){refresh();if(reduced)return@LaunchedEffect;while(true)withFrameNanos{refresh()}}
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w=maxWidth.value.toDouble();val h=maxHeight.value.toDouble();val stageTop=max(top,20.0)+3
        val s=min(w/402,max(1.0,h-stageTop-max(bottom,12.0))/590);val left=(w-402*s)/2;val ctrlY=h-max(bottom,12.0)-80*s;val sh=(ctrlY-60*s-stageTop)/s;val sheetTop=h-max(bottom,12.0)+5*s-210*s
        Canvas(Modifier.fillMaxSize().pointerInput(reduced,top,bottom){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val w=size.width/density;val h=size.height/density;val st=max(top,20.0)+3
                val scale=min(w/402.0,max(1.0,h-st-max(bottom,12.0))/590);val ox=(w-402*scale)/2;val cy=h-max(bottom,12.0)-80*scale;val height=(cy-60*scale-st)/scale;val sheetY=h-max(bottom,12.0)+5*scale-210*scale
                val sx=down.position.x/density;val sy=down.position.y/density
                fun px(x:Double)=(x-ox-6*scale)/scale
                fun py(y:Double)=(y-st)/scale
                engine.takeOver(now())
                val points=ShapeShiftViewfinderGeometry.points(engine.f,height)
                fun inside(x:Double,y:Double):Boolean {var yes=false;var j=points.lastIndex;for(i in points.indices){val a=points[i];val b=points[j];if((a.y>y)!=(b.y>y)&&x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)yes=!yes;j=i};return yes}
                val kind=when {
                    engine.f.sheet>.5->if(sy>sheetY+120*scale)3 else 4
                    abs(sy-cy)<44*scale&&abs((sx-ox)/scale-80)<30->5
                    abs(sy-cy)<44*scale&&abs((sx-ox)/scale-201)<44->6
                    abs(sy-cy)<44*scale&&abs((sx-ox)/scale-322)<30->7
                    py(sy.toDouble())>height-62&&abs(px(sx.toDouble())-195)<40->8
                    inside(px(sx.toDouble()),py(sy.toDouble()))->1
                    else->0
                }
                if(kind==6)engine.capture(now())
                engine.held=true
                var startX=engine.f.x;var startY=engine.f.y;var startK=engine.f.k;var startRot=engine.f.rot
                val scrollStart=engine.rowScroll;var lastX=sx.toDouble();var lastY=sy.toDouble();var lastTime=now();var vx=0.0;var vy=0.0
                var moved=false;var transformed=false;var firstDistance=0.0;var firstAngle=0.0;var firstCX=0.0;var firstCY=0.0;var anchorX=0.0;var anchorY=0.0;var ended=false
                try {while(true){
                    val event=awaitPointerEvent();if(event.changes.any{it.isConsumed})break
                    val pressed=event.changes.filter{it.pressed}
                    val primary=event.changes.firstOrNull{it.id==down.id}?:event.changes.firstOrNull()?:break
                    val x=primary.position.x/density.toDouble();val y=primary.position.y/density.toDouble();val t=now()
                    if(pressed.size>=2&&kind==1){
                        val a=pressed[0].position;val b=pressed[1].position;val cx=(a.x+b.x)/2/density;val yy=(a.y+b.y)/2/density;val dist=hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble());val angle=atan2((b.y-a.y).toDouble(),(b.x-a.x).toDouble())
                        if(!transformed){transformed=true;firstDistance=max(dist,1.0);firstAngle=angle;startK=engine.f.k;startRot=engine.f.rot;startX=engine.f.x;startY=engine.f.y;firstCX=cx.toDouble();firstCY=yy.toDouble();anchorX=195+startX-px(firstCX);anchorY=height/2+startY-py(firstCY)}
                        engine.f.k=(startK*dist/firstDistance).coerceIn(.45,1.3);val ratio=engine.f.k/startK
                        engine.f.x=startX+anchorX*(ratio-1)+(cx-firstCX)/scale;engine.f.y=startY+anchorY*(ratio-1)+(yy-firstCY)/scale
                        val da=atan2(sin(angle-firstAngle),cos(angle-firstAngle));engine.f.rot=startRot+da*180/PI;moved=true
                    } else if(pressed.isNotEmpty()) {
                        if(t>lastTime){vx=(x-lastX)/(t-lastTime)/scale;vy=(y-lastY)/(t-lastTime)/scale}
                        val dx=(x-sx)/scale;val dy=(y-sy)/scale;moved=moved||hypot(dx,dy)>4
                        if(kind==1&&moved&&!transformed){engine.f.x=(startX+dx).coerceIn(-195.0,195.0);engine.f.y=(startY+dy).coerceIn(-height/2,height/2)}
                        if(kind==3){val count=if(engine.solidTarget)art.colors.size else art.dynamic.size;engine.rowScroll=(scrollStart-dx).coerceIn(0.0,max(0.0,count*50.0-326))}
                        if(kind==4)engine.f.sheet=(1-max(y-sy,0.0)/(210*scale)).coerceIn(0.0,1.0)
                    } else {
                        if(!moved){when(kind){
                            1->engine.morph(t,atan2(px(x)-195-engine.f.x,-(py(y)-height/2-engine.f.y))-(engine.f.rot+engine.f.lean)*PI/180)
                            3->{val localX=(x-ox-14*scale)/scale;val i=floor((localX-24+engine.rowScroll)/50).toInt();val palette=if(engine.solidTarget)art.colors else art.dynamic;if(i in palette.indices)engine.color(palette[i],i,t)}
                            4->{val ly=(y-sheetY)/scale;val lx=(x-ox-14*scale)/scale;if(ly in 72.0..108.0){engine.solidTarget=lx>187;engine.rowScroll=0.0;engine.selectedSwatch=0}else engine.sheet(t,false)}
                            5->engine.sheet(t)
                            7->engine.flip(t)
                            8->{engine.kGoal=1.0;engine.f.k=1.0}
                        }}
                        if(kind==4&&moved)engine.sheet(t,engine.f.sheet>=.6)
                        engine.release(t,transformed||kind!=1,vx,vy,height);ended=true;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);refresh();break
                    }
                    lastX=x;lastY=y;lastTime=t;event.changes.forEach{it.consume()};refresh()
                }} finally {if(!ended){engine.release(now(),true,height=height);if(kind==4)engine.sheet(now(),engine.f.sheet>=.6);refresh()}}
            }
        }){
            drawIntoCanvas{c->c.nativeCanvas.save();c.nativeCanvas.scale(displayDensity.density,displayDensity.density);art.draw(c.nativeCanvas,f,engine,size.width/displayDensity.density.toDouble(),size.height/displayDensity.density.toDouble(),top,bottom);c.nativeCanvas.restore()}
        }
        Box(Modifier.offset((left+6*s).dp,stageTop.dp).size((390*s).dp,(sh*s).dp).semantics {
            contentDescription="Change shape";stateDescription=ShapeShiftViewfinderGeometry.names[engine.target];role=Role.Button
            onClick{engine.morph(now(),0.0);refresh();true}
        })
        val controls=listOf("Background color","Take photo","Flip camera")
        for((i,label)in controls.withIndex()){
            val x=doubleArrayOf(80.0,201.0,322.0)[i];val diameter=if(i==1)80.0 else 44.0
            Box(Modifier.offset((left+(x-diameter/2)*s).dp,(ctrlY-diameter/2*s).dp).size((diameter*s).dp).semantics {
                contentDescription=label;role=Role.Button
                if(i==2)stateDescription=if(engine.flipTarget>.5)"Front" else "Back"
                onClick{when(i){0->engine.sheet(now());1->engine.capture(now());2->engine.flip(now())};refresh();true}
            })
        }
        Box(Modifier.offset((left+168*s).dp,(stageTop+(sh-62)*s).dp).size((66*s).dp,(38*s).dp).semantics {
            contentDescription="Zoom";stateDescription=String.format(java.util.Locale.US,"%.1f times",f.k);progressBarRangeInfo=ProgressBarRangeInfo(f.k.toFloat(),.45f..1.3f)
            setProgress{value->engine.takeOver(now());engine.kGoal=value.toDouble().coerceIn(.45,1.3);refresh();true}
        })
        if(f.sheet>.5){
            for((i,label)in listOf("Dynamic","Solid").withIndex())Box(Modifier.offset((left+(33+168*i)*s).dp,(sheetTop+72*s).dp).size((168*s).dp,(36*s).dp).semantics{contentDescription=label;role=Role.Button;onClick{engine.takeOver(now());engine.solidTarget=i==1;engine.rowScroll=0.0;refresh();true}})
            val palette=if(engine.solidTarget)art.colors else art.dynamic
            for((i,color)in palette.withIndex()){val x=left+(38+i*50-engine.rowScroll)*s;if(x<left+33*s||x+42*s>left+369*s)continue
                Box(Modifier.offset(x.dp,(sheetTop+128*s).dp).size((42*s).dp,(52*s).dp).semantics{contentDescription="Color ${i+1}";role=Role.Button;onClick{engine.color(color,i,now());refresh();true}})}
        }
    }
}

@Preview
@Composable
private fun ShapeShiftViewfinderDemoPreview(){ShapeShiftViewfinderDemo()}
