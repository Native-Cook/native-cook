package com.ngocdevv.nativecook.blocks.onboarding.bellpermissionprimer

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.*

private class BellLive(auto: Boolean) {
    var auto = auto; var clock=0.0; var start=0.0; var lastTouch=0.0; var resume=false
    var swing=0.0; var sv=0.0; var clap=0.0; var cv=0.0; var liftX=0.0; var liftY=0.0
    var suck=0.0; var expand=0.0; var dragging=false; var lifting=false; var dragFrom=0.0
    var phase=0; var phaseStart=0.0; var fromLift=0.0; var fromSuck=0.0; var fromExpand=0.0
    var opening:BellPermissionPrimerMotion.Spring?=null
    var releaseY:BellPermissionPrimerMotion.Spring?=null; var releaseX:BellPermissionPrimerMotion.Spring?=null
    var bannerOnOpen=false
    var ringNext=0; val starts=doubleArrayOf(-10.0,-10.0,-10.0)
}

@Composable
fun BellPermissionPrimer(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val haptic=LocalHapticFeedback.current
    val safeTop=WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val top=max(safeTop,44f)
    val bottom=max(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value,20f)
    val fonts=remember(context) { listOf("BarlowSemiCondensed-Bold.ttf","Barlow-Regular.ttf","Barlow-SemiBold.ttf","Barlow-Bold.ttf").map { FontFamily(Typeface(android.graphics.Typeface.createFromAsset(context.assets,"bellpermissionprimer/bellpermissionprimer_$it"))) } }
    val shader=remember { if(Build.VERSION.SDK_INT>=33) runCatching { RuntimeShader(BellPermissionPrimerShader.SOURCE) }.getOrNull() else null }
    val blobShader=remember { if(Build.VERSION.SDK_INT>=33) runCatching { RuntimeShader(BellPermissionPrimerShader.BLOB) }.getOrNull() else null }
    val audio=remember(context) { SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build() }
    val sounds=remember(audio) { listOf("ding","allow","banner").map { name -> context.assets.openFd("bellpermissionprimer/bellpermissionprimer_bpp-$name.wav").use { audio.load(it,1) } } }
    DisposableEffect(audio) { onDispose { audio.release() } }
    fun play(i:Int) { audio.play(sounds[i],if(i==0) .55f else .6f,if(i==0) .55f else .6f,1,0,1f) }
    fun buzz() { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val live=remember { BellLive(autoPlay && !reduced) }
    var frame by remember { mutableStateOf(BellPermissionPrimerMotion.frameAt(0.0)) }
    var running by remember { mutableStateOf(autoPlay && !reduced) }
    fun takeOver() { live.lastTouch=live.clock; live.resume=false; live.auto=false; live.sv=0.0; live.cv=0.0; running=true }
    fun open(touch:Boolean) { if(live.suck>.5 && live.expand>.5) return; if(touch) { play(1); buzz() }; live.phase=1; live.phaseStart=live.clock; live.fromLift=live.liftY; live.fromSuck=live.suck; live.fromExpand=live.expand; live.opening=BellPermissionPrimerMotion.Spring(from=live.expand,to=1.0,zeta=BellPermissionPrimerMotion.OPEN_ZETA,omega=BellPermissionPrimerMotion.OPEN_OMEGA); live.bannerOnOpen=touch && !reduced; running=true }
    fun close(touch:Boolean) { if(touch) buzz(); live.phase=4; live.phaseStart=live.clock; live.fromExpand=live.expand; live.fromSuck=live.suck; running=true }
    LaunchedEffect(lifecycle,running,autoPlay,reduced) {
        if(!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while(isActive) withFrameNanos { now ->
                val dt=min(if(last==0L) .016 else (now-last)/1e9,.5); last=now; live.clock+=dt
                if(live.auto && !reduced) {
                    val f=BellPermissionPrimerMotion.frameAt((live.clock-live.start)%10); frame=f
                    live.swing=f.swing; live.clap=f.clap; live.liftY=-150*f.lift; live.liftX=0.0; live.suck=f.suck; live.expand=f.expand
                } else {
                    val age=live.clock-live.phaseStart
                    when(live.phase) {
                        1 -> { val duration=if(reduced) .001 else .4*(1-abs(live.fromLift)/150).coerceIn(0.0,1.0); val p=if(duration==0.0) 1.0 else BellPermissionPrimerMotion.ease((age/duration).coerceIn(0.0,1.0)); live.liftY=live.fromLift+(-150-live.fromLift)*p; live.liftX*=1-p; if(p>=1) { live.phase=2; live.phaseStart=live.clock } }
                        2 -> { val p=(age/(if(reduced) .001 else .3)).coerceIn(0.0,1.0); live.suck=live.fromSuck+(1-live.fromSuck)*p*p; if(p>=1) {live.phase=3; live.phaseStart=live.clock; if(live.bannerOnOpen) play(2); live.bannerOnOpen=false} }
                        3 -> {
                            if(reduced) { val p=(age/.2).coerceIn(0.0,1.0); live.expand=live.fromExpand+(1-live.fromExpand)*BellPermissionPrimerMotion.out(p); if(p>=1) live.phase=0 }
                            else { val sample=live.opening!!.sample(age); live.expand=sample.first; if(sample.second) live.phase=0 }
                        }
                        4 -> { val p=(age/(if(reduced) .2 else .4)).coerceIn(0.0,1.0); live.expand=live.fromExpand*(1-BellPermissionPrimerMotion.ease(p)); if(p>=1) {live.phase=5; live.phaseStart=live.clock; live.liftY=0.0; live.liftX=0.0} }
                        5 -> { val p=(age/(if(reduced) .001 else .6)).coerceIn(0.0,1.0); live.suck=live.fromSuck*(1-BellPermissionPrimerMotion.bounce(p)); if(p>=1) {live.phase=0; if(!reduced) { live.sv+=1.2; live.cv+=2.0 } } }
                        6 -> { val y=live.releaseY!!.sample(age); val x=live.releaseX!!.sample(age); live.liftY=y.first; live.liftX=x.first; if(y.second && x.second) live.phase=0 }
                    }
                    if(!reduced) repeat(4) {
                        val h=min(dt,.05)/4; val w=2*PI/BellPermissionPrimerMotion.PERIOD
                        if(!live.dragging) { live.sv+=(-w*w*live.swing-2*.12*w*live.sv)*h; live.swing+=live.sv*h }
                        live.cv+=(-w*w*.8*live.clap-2*.1*w*live.cv)*h; live.clap+=live.cv*h
                        val rel=live.clap-live.swing
                        if(abs(rel)>.13) { val side=if(rel>0) 1 else -1; live.clap=live.swing+side*.13; val v=live.cv-live.sv; if(v*side>0) { if(v*side>.6) {live.starts[live.ringNext]=live.clock;live.ringNext=(live.ringNext+1)%3; buzz();play(0)};live.cv=live.sv-v*.35 } }
                    }
                    val rings=DoubleArray(3) { val age2=live.clock-live.starts[it]; if(age2>0 && age2<1.1) age2/1.1 else 0.0 }
                    frame=BellPermissionPrimerMotion.Frame(swing=live.swing,clap=live.clap,lift=-live.liftY/150,suck=live.suck,expand=live.expand,rings=rings,time=live.clock%10,liftX=live.liftX)
                    if(autoPlay && !reduced && !live.dragging && live.clock-live.lastTouch>=2) {
                        if(!live.resume) {live.resume=true;if(live.suck>.001 || live.expand>.001) close(false)}
                        else if(live.phase==0 && abs(live.swing)<.004 && abs(live.sv)<.02 && abs(live.clap)<.01 && abs(live.cv)<.05) {live.auto=true;live.start=live.clock;live.resume=false}
                    } else if((!autoPlay || reduced) && !live.dragging && live.phase==0 && abs(live.swing)<.004 && abs(live.sv)<.02 && rings.all { it==0.0 }) running=false
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFDFDFE))) {
        val w=maxWidth.value; val h=maxHeight.value; val sx=w/402f; val k=(h-top-bottom)/(874f-62-34)
        fun y(v:Float)=if(v<62) v*top/62 else top+(v-62)*k
        val px=200.5f*sx; val py=y(206f); val unit=116*sx; val discY=y(304f); val radius=114*sx
        val islandY=if(safeTop>=51f) 14f else safeTop+6f
        Canvas(Modifier.fillMaxSize()) {
            val d=density; fun p(v:Float)=v*d
            drawRect(brush=Brush.verticalGradient(listOf(Color(0xFFF6F9FD),Color(0xFFFDFDFE)),startY=p(y(190f)-30),endY=p(y(190f)+30)),size=androidx.compose.ui.geometry.Size(size.width,p(y(190f)+30)))
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(px-radius),p(discY-radius),p(px+radius),p(discY+radius))) }) {
                drawCircle(Brush.linearGradient(listOf(Color(0xFF6CC2FC),Color(0xFF4C86FA)),Offset(p(px-radius*.5f),p(discY-radius)),Offset(p(px+radius*.3f),p(discY+radius*.2f))),p(radius),Offset(p(px),p(discY)))
                val hy=y(296f); val ground=Path().apply { moveTo(p(px-radius*1.2f),p(hy+16*sx)); quadraticTo(p(px),p(hy-10*sx),p(px+radius*1.2f),p(hy+16*sx)); lineTo(p(px+radius*1.2f),p(discY+radius*1.2f)); lineTo(p(px-radius*1.2f),p(discY+radius*1.2f)); close() }
                drawPath(ground,Brush.verticalGradient(listOf(Color(0xFF4A93FD),Color(0xFF3550FB)),p(hy),p(discY+radius)))
            }
            val deco=arrayOf(floatArrayOf(34f,91f,14f),floatArrayOf(367f,88f,12f),floatArrayOf(218f,148f,12f),floatArrayOf(122f,110f,6f),floatArrayOf(327f,150f,7f),floatArrayOf(25f,170f,5f),floatArrayOf(110f,232f,10f),floatArrayOf(330f,258f,13f),floatArrayOf(32f,293f,7f),floatArrayOf(346f,344f,6f),floatArrayOf(301f,511f,13f),floatArrayOf(370f,583f,9f),floatArrayOf(323f,724f,11f),floatArrayOf(180f,635f,6f),floatArrayOf(70f,740f,5f))
            deco.forEachIndexed { i,v ->
                fun hash(n:Double):Double { val x=sin(n*91.3458)*47453.5453; return x-floor(x) }
                var cx=v[0]*sx+sin(2*PI*(frame.time/10+hash(i.toDouble()))).toFloat()*3
                var cy=y(v[1])+sin(2*PI*(2*frame.time/10+hash(i+3.0))).toFloat()*4
                frame.rings.forEach { rp -> if(rp>0) { val dx=cx-px; val dy=cy-(py+1.27f*unit); val dist=hypot(dx,dy).coerceAtLeast(.001f); val push=(9*exp(-((dist-rp*420)/26).pow(2))*(1-rp)).toFloat();cx+=dx/dist*push;cy+=dy/dist*push } }
                val r=v[2]*sx*.5f; val color=Color(0x8CBFDCFE)
                if(i==1 || i==2) { val shape=Path().apply { moveTo(p(cx-r*.7f),p(cy+r*.3f));quadraticTo(p(cx-r*.5f),p(cy-r*.6f),p(cx),p(cy-r*.65f));quadraticTo(p(cx+r*.5f),p(cy-r*.6f),p(cx+r*.7f),p(cy+r*.3f));lineTo(p(cx+r*.85f),p(cy+r*.55f));lineTo(p(cx-r*.85f),p(cy+r*.55f));close() };drawPath(shape,color);drawCircle(color,p(r*.15f),Offset(p(cx),p(cy-r*.8f))) }
                else if(i==0 || i==6 || i==7 || i==10 || i==11 || i==12) {
                    if(blobShader!=null && Build.VERSION.SDK_INT>=33) {
                        blobShader.setFloatUniform("center",p(cx),p(cy));blobShader.setFloatUniform("size",p(v[2]*sx));blobShader.setFloatUniform("index",i.toFloat())
                        drawIntoCanvas { it.nativeCanvas.drawRect(p(cx-v[2]*sx),p(cy-v[2]*sx),p(cx+v[2]*sx),p(cy+v[2]*sx),Paint().apply { this.shader=blobShader }) }
                    } else {
                        val shape=Path(); for(j in 0..72) { val angle=2*PI*j/72; val c=cos(angle);val si=sin(angle);val norm=sqrt(c*c+1.15*1.15*si*si);val rr=(.62-.08*sin(3*angle+i))/norm*v[2]*sx;val xx=p(cx+(rr*c).toFloat());val yy=p(cy+(rr*si).toFloat());if(j==0) shape.moveTo(xx,yy) else shape.lineTo(xx,yy) };shape.close();drawPath(shape,color)
                    }
                }
                else drawCircle(color,p(r),Offset(p(cx),p(cy)))
            }
            frame.rings.forEach { if(it>0) drawCircle(Color(0xFF8CBFFF).copy(alpha=((1-it)*.45).toFloat()),p((it*420).toFloat()),Offset(p(px),p(py+1.27f*unit)),style=Stroke(p(2+it.toFloat()*5))) }
            if(shader!=null && Build.VERSION.SDK_INT>=33) {
                shader.setFloatUniform("hero",p(px),p(py));shader.setFloatUniform("unit",p(unit));shader.setFloatUniform("pose",frame.swing.toFloat(),frame.clap.toFloat(),frame.suck.toFloat(),frame.expand.toFloat());shader.setFloatUniform("lift",p(frame.liftX.toFloat()),p((-150*frame.lift).toFloat()));shader.setFloatUniform("bob",p((frame.bob*unit).toFloat()));shader.setFloatUniform("island",p(w/2-62.5f),p(islandY),p(125f),p(36.3f));shader.setFloatUniform("slab",p(10*sx),p(islandY),p(w-20*sx),p(92f))
                drawIntoCanvas { it.nativeCanvas.drawRect(0f,0f,size.width,size.height,Paint().apply { this.shader=shader }) }
            } else {
                val s=(1-frame.suck).toFloat(); val cx=px+frame.liftX.toFloat(); val cy=py-(150*frame.lift).toFloat()
                rotate(Math.toDegrees(frame.swing).toFloat(),Offset(p(cx),p(cy))) { val path=Path().apply { moveTo(p(cx-.08f*unit*s),p(cy+.22f*unit*s)); cubicTo(p(cx-.42f*unit*s),p(cy+.25f*unit*s),p(cx-.36f*unit*s),p(cy+.85f*unit*s),p(cx-.62f*unit*s),p(cy+1.27f*unit*s)); lineTo(p(cx+.62f*unit*s),p(cy+1.27f*unit*s)); cubicTo(p(cx+.36f*unit*s),p(cy+.85f*unit*s),p(cx+.42f*unit*s),p(cy+.25f*unit*s),p(cx+.08f*unit*s),p(cy+.22f*unit*s));close() }; drawPath(path,Color.White);drawCircle(Color(0xFFF07BA1),p(.11f*unit*s),Offset(p(cx),p(cy+1.27f*unit*s))) }
                val e=frame.expand.toFloat(); drawRoundRect(Color.Black,Offset(p((w-125-(w-145)*e)/2),p(islandY)),androidx.compose.ui.geometry.Size(p(125+(w-145)*e),p(36.3f+(92-36.3f)*e)),androidx.compose.ui.geometry.CornerRadius(p(18+16*e)))
            }
            for(i in 0 until 260) { fun hash(n:Double):Double { val x=sin(n*91.3458)*47453.5453; return x-floor(x) }; val fi=i.toDouble(); val age=((frame.suck-.55-.3*hash(fi+9))/.45).coerceIn(0.0,1.0)*.5+frame.expand*.5; if(age>0 && age<1) { val a=hash(fi+2)*2*PI; val dx=cos(a); val dy=sin(a)*.6; val dist=(8+60*hash(fi+3))*sqrt(age); val xx=w/2+dx*(62.5+dist); val yy=islandY+36.3/2+dy*(36.3*.8+dist)+30*age*age; drawCircle(Color(0xFF8CBFFF).copy(alpha=((1-age)*(1-age)).toFloat()),p(((1.2+2.6*hash(fi+4))*(1-.5*age)).toFloat()),Offset(p(xx.toFloat()),p(yy.toFloat()))) } }
            if(frame.finger>0) { val fx=if(frame.fingerAt==0) px+(frame.fingerX*unit*.6).toFloat() else w*.6f; val fy=if(frame.fingerAt==0) py+unit*.95f-(150*frame.lift).toFloat() else islandY+92*.55f; val r=22*(1-.12*frame.fingerPress).toFloat(); drawCircle(Color(0xFF14285A).copy(alpha=(.08*frame.finger).toFloat()),p(r),Offset(p(fx),p(fy)));drawCircle(Color(0xFF14285A).copy(alpha=(.35*frame.finger).toFloat()),p(r),Offset(p(fx),p(fy)),style=Stroke(p(1.5f))) }
        }
        Box(Modifier.offset(y=(y(480f)-30).dp).fillMaxWidth(),contentAlignment=Alignment.Center) { Text("Notifications",fontFamily=fonts[0],fontSize=43.sp,lineHeight=52.sp,color=Color(0xFF25323B),textAlign=TextAlign.Center) }
        Box(Modifier.offset(x=((w-312*sx)/2).dp,y=(y(530f)-20).dp).width((312*sx).dp),contentAlignment=Alignment.Center) { Text("One quiet reminder from Mosswell on the mornings your plants are thirsty.",fontFamily=fonts[1],fontSize=22.sp,lineHeight=33.sp,color=Color(0xFF545C68),textAlign=TextAlign.Center) }
        Box(Modifier.offset(x=(144*sx).dp,y=y(656f).dp).size((114*sx).dp,44.dp).shadow(14.dp,RoundedCornerShape(22.dp),ambientColor=Color(0xFF489BFF),spotColor=Color(0xFF489BFF)).clip(RoundedCornerShape(22.dp)).background(Color(0xFF489BFF)).clickable { takeOver();open(true) },contentAlignment=Alignment.Center) { Text("ALLOW",fontFamily=fonts[2],fontSize=17.sp,letterSpacing=.6.sp,color=Color.White) }
        Box(Modifier.offset(x=(w/2-60).dp,y=(y(769f)-18).dp).size(120.dp,36.dp).clickable { takeOver() },contentAlignment=Alignment.Center) { Text("SKIP",fontFamily=fonts[3],fontSize=17.sp,letterSpacing=.4.sp,color=Color(0xFF4A8BDF)) }
        Box(Modifier.offset(x=(px-unit*.9f).dp,y=(py-10).dp).size((unit*1.8f).dp,(unit*1.45f).dp).pointerInput(unit,reduced) {
            awaitEachGesture { val down=awaitFirstDown(false); takeOver(); val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position); val start=down.position;live.dragFrom=live.swing;var lastChange=down
                do { val change=awaitPointerEvent().changes.firstOrNull { it.id==down.id } ?: break;tracker.addPosition(change.uptimeMillis,change.position); val dx=(change.position.x-start.x)/density;val dy=(change.position.y-start.y)/density;live.lastTouch=live.clock
                    if(!reduced && live.suck<.01 && !live.dragging && hypot(dx,dy)>4) live.dragging=true
                    if(live.dragging) { if(!live.lifting && dy < -24 && abs(dy)>abs(dx)) live.lifting=true; if(live.lifting) { val up=-dy;live.liftY=-(if(up<150) up else 150+(up-150)*.35).toDouble();live.liftX=dx*.35 } else {val raw=live.dragFrom+dx/(unit*1.6);live.swing=sign(raw)*abs(raw)/(1+abs(raw)/.8)};change.consume() };lastChange=change
                } while(lastChange.pressed)
                val velocity=tracker.calculateVelocity(); if(live.dragging) {live.dragging=false;if(live.lifting) {live.lifting=false;if(-live.liftY>112.5 || velocity.y/density < -900) open(true) else {live.phase=6;live.phaseStart=live.clock; val band=if(-live.liftY<150) 1.0 else .35; live.releaseY=BellPermissionPrimerMotion.Spring(from=live.liftY,to=0.0,velocity=velocity.y/density*band,zeta=.45);live.releaseX=BellPermissionPrimerMotion.Spring(from=live.liftX,to=0.0,velocity=velocity.x/density*.35,zeta=.6);live.sv+=velocity.x/density/1500;live.cv+=1.6}} else live.sv=(velocity.x/density/(unit*1.6)).toDouble().coerceIn(-6.0,6.0)} else if(!reduced && live.suck<.01) live.sv+=2.6
            }
        })
        if(frame.expand>.8) Row(Modifier.offset(x=(10*sx).dp,y=islandY.dp).size((w-20*sx).dp,92.dp).clip(RoundedCornerShape(34.dp)).clickable { takeOver();close(true) }.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C2A22)),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(22.dp)) {
                    drawRoundRect(Color(0xFF3FA56B),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.width*6/22))
                    val u=size.width/22f
                    val leaf=Path().apply { moveTo(11*u,17.5f*u);cubicTo(11*u,12*u,7.5f*u,9.5f*u,4.5f*u,9.5f*u);cubicTo(4.5f*u,13.5f*u,7*u,16*u,11*u,16*u);moveTo(11*u,17.5f*u);cubicTo(11*u,11.5f*u,14*u,8*u,17.5f*u,7.5f*u);cubicTo(17.5f*u,12*u,15*u,15.5f*u,11*u,15.5f*u) }
                    drawPath(leaf,Color.White,style=Stroke(width=1.5f*u,cap=StrokeCap.Round))
                }
            };Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("MOSSWELL",fontFamily=fonts[2],fontSize=12.sp,color=Color(0xFF8E949C));Text("now",fontFamily=fonts[1],fontSize=12.sp,color=Color(0xFF8E949C)) };Text("Time to water your monstera",fontFamily=fonts[2],fontSize=16.sp,color=Color.White,maxLines=1);Text("Its soil is dry 2 cm down. A glass of water will do.",fontFamily=fonts[1],fontSize=14.sp,color=Color(0xFFC9CED4),maxLines=1) }
        }
    }
}
