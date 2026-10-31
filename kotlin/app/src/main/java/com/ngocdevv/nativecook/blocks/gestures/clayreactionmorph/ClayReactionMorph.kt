package com.ngocdevv.nativecook.blocks.gestures.clayreactionmorph

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.*

private val ClayQuad = Easing { x -> if(x<0.5f) 2*x*x else 1-(-2*x+2)*(-2*x+2)/2 }
private val ClayOut = CubicBezierEasing(0.23f,1f,0.32f,1f)
private val ClayInOut = CubicBezierEasing(0.77f,0f,0.175f,1f)

/** Self-contained chat; all moving drawing reads animation state in draw/layer phases. */
@Composable
fun ClayReactionMorph(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val view=LocalView.current
    val scope=rememberCoroutineScope { object: MotionDurationScale { override val scaleFactor=1f } }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {context.contentResolver.unregisterContentObserver(observer)}
    }
    val engine=remember { ClayReactionMorphEngine(scope) { kind -> view.performHapticFeedback(when(kind){0->HapticFeedbackConstants.CLOCK_TICK;1->HapticFeedbackConstants.LONG_PRESS;else->HapticFeedbackConstants.CONTEXT_CLICK}) } }
    val family=remember { FontFamily(Font("clayreactionmorph/clayreactionmorph_Inter-Regular.ttf",context.assets,FontWeight.Normal),Font("clayreactionmorph/clayreactionmorph_Inter-Medium.ttf",context.assets,FontWeight.Medium),Font("clayreactionmorph/clayreactionmorph_Inter-SemiBold.ttf",context.assets,FontWeight.SemiBold)) }
    val typeface=remember { Typeface.createFromAsset(context.assets,"clayreactionmorph/clayreactionmorph_Inter-SemiBold.ttf") }
    val shader=remember { if(Build.VERSION.SDK_INT>=33) RuntimeShader(ClayReactionMorphAgsl) else null }
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    var bubble by remember { mutableStateOf(Rect(16f,500f,256f,568f)) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom=maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),12.dp)
    LaunchedEffect(autoPlay,reduced) {
        engine.auto=autoPlay && !reduced; engine.reduced=reduced; engine.looping=engine.auto
        var prev=0L
        while(isActive) withFrameNanos { now ->
            if(prev!=0L) engine.tick(min(0.05,(now-prev)/1e9))
            prev=now
        }
    }
    val config=LocalViewConfiguration.current
    val holdConfig=remember(config) { object: ViewConfiguration by config { override val longPressTimeoutMillis=280L } }
    BoxWithConstraints(Modifier.fillMaxSize().background(ClayReactionMorphTheme.page).onGloballyPositioned { rootCoordinates=it }.pointerInput(engine) {
        awaitPointerEventScope { while(true) { val event=awaitPointerEvent(PointerEventPass.Initial); if(event.changes.any { it.pressed && !it.previousPressed }) engine.takeOver() } }
    }) {
        val w=maxWidth.value
        Row(Modifier.fillMaxWidth().height(top+56.dp).drawBehind { drawLine(ClayReactionMorphTheme.line,Offset(0f,size.height),Offset(size.width,size.height),1f) }.padding(top=top,start=16.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(12.dp,20.dp).semantics { contentDescription="Back"; role=Role.Button }) { drawPath(Path().apply { moveTo(size.width*10/12,size.height*2/20); lineTo(size.width*2/12,size.height/2); lineTo(size.width*10/12,size.height*18/20) },ClayReactionMorphTheme.head,style=Stroke(2.4.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)) }
            Box(Modifier.size(36.dp).background(Color(0xFFE8A23A),CircleShape),contentAlignment=Alignment.Center) { BasicText("M",style=TextStyle(fontFamily=family,fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=Color(0xFF2A1A05))) }
            Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                BasicText("Mai Tran",Modifier.semantics { heading() },TextStyle(fontFamily=family,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=21.sp,color=ClayReactionMorphTheme.head))
                BasicText("Active now",style=TextStyle(fontFamily=family,fontSize=13.sp,lineHeight=17.sp,color=ClayReactionMorphTheme.mute))
            }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start=16.dp,end=16.dp,bottom=bottom+72.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            BasicText("Today 9:14 AM",Modifier.align(Alignment.CenterHorizontally).padding(bottom=8.dp),TextStyle(fontFamily=family,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=16.sp,color=ClayReactionMorphTheme.mute))
            ClayReactionMorphTheme.messages.forEachIndexed { index,message ->
                val incoming=index%2==0
                Row(Modifier.fillMaxWidth().then(if(index==4) Modifier.padding(top=14.dp) else Modifier),horizontalArrangement=if(incoming) Arrangement.Start else Arrangement.End) {
                    CompositionLocalProvider(LocalViewConfiguration provides holdConfig) {
                        val gesture=if(index==4) Modifier.pointerInput(engine) {
                            var lastX=0f; var lastMs=0L
                            detectDragGesturesAfterLongPress(onDragStart={ p -> engine.startDrag(); lastX=p.x; lastMs=0L },onDragEnd={ engine.release() },onDragCancel={engine.release()}) { change,delta ->
                                val millis=change.uptimeMillis
                                val velocity=if(lastMs>0) delta.x/density.density/max(0.001,(millis-lastMs)/1000.0) else 0.0
                                lastMs=millis; lastX+=delta.x
                                engine.drag(delta.x/density.density,velocity); change.consume()
                            }
                        }.pointerInput(engine) { awaitPointerEventScope { while(true) { val e=awaitPointerEvent(PointerEventPass.Initial); if(e.changes.any{it.pressed&&!it.previousPressed}) engine.pressDown(); if(e.changes.any{!it.pressed&&it.previousPressed}) engine.pressUp() } } } else Modifier
                        Box(Modifier.widthIn(max=(w-32).dp*0.74f).then(if(index==4) Modifier.onGloballyPositioned { coords -> rootCoordinates?.let { root -> val p=root.localPositionOf(coords,Offset.Zero); bubble=Rect(p.x/density.density,p.y/density.density,(p.x+coords.size.width)/density.density,(p.y+coords.size.height)/density.density) } }.graphicsLayer { val s=1-0.03f*engine.frame.press.toFloat(); scaleX=s;scaleY=s } else Modifier).then(gesture).background(if(incoming) ClayReactionMorphTheme.incoming else ClayReactionMorphTheme.outgoing,RoundedCornerShape(20.dp)).padding(horizontal=16.dp,vertical=11.dp).semantics(mergeDescendants=true) {
                            contentDescription="${if(incoming) "Mai" else "You"}: $message"
                            if(index==4) { stateDescription="Your reaction: ${ClayReactionMorphTheme.reactions[engine.badge]}"; customActions=listOf(CustomAccessibilityAction("Increase reaction") { engine.choose((engine.badge+1).coerceAtMost(3));true },CustomAccessibilityAction("Decrease reaction") {engine.choose((engine.badge-1).coerceAtLeast(0));true}); progressBarRangeInfo=ProgressBarRangeInfo(engine.badge.toFloat(),0f..3f,2); setProgress { engine.choose(it.roundToInt().coerceIn(0,3));true } }
                        }) { BasicText(message,style=TextStyle(fontFamily=family,fontSize=17.sp,lineHeight=23.sp,color=if(incoming) ClayReactionMorphTheme.head else Color.White)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start=16.dp,end=16.dp,bottom=bottom).height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(ClayReactionMorphTheme.field,CircleShape).semantics { contentDescription="Attach"; role=Role.Button },contentAlignment=Alignment.Center) { Canvas(Modifier.size(16.dp)) { drawLine(ClayReactionMorphTheme.head,Offset(size.width/2,2.dp.toPx()),Offset(size.width/2,14.dp.toPx()),2.dp.toPx(),StrokeCap.Round);drawLine(ClayReactionMorphTheme.head,Offset(2.dp.toPx(),size.height/2),Offset(14.dp.toPx(),size.height/2),2.dp.toPx(),StrokeCap.Round) } }
            Box(Modifier.weight(1f).height(40.dp).background(ClayReactionMorphTheme.field,CircleShape).border(1.dp,ClayReactionMorphTheme.line,CircleShape).padding(horizontal=16.dp),contentAlignment=Alignment.CenterStart) { BasicText("Message",style=TextStyle(fontFamily=family,fontSize=16.sp,color=ClayReactionMorphTheme.mute)) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val f=engine.frame; val time=engine.clock; val d=density.density
            val heroSize=min(132f,w*0.33f);val trayTop=bubble.top-60f
            val heroY=max(top.value+56+24+heroSize,trayTop-28-heroSize)
            val sx=bubble.right-4;val sy=bubble.top+2;val l=f.lift.toFloat();val lc=l.coerceIn(0f,1f);val arc=sin(PI*lc).toFloat()
            val x=sx+(w/2-sx)*l+36*arc;val y=sy+(heroY-sy)*l-24*arc;val radius=11+(heroSize-11)*l
            val live=if(reduced) 0.0 else ClayReactionMorphMotion.smooth(0.7,1.0,f.lift)
            val yaw=(if(reduced) 0.0 else 0.4*sin(PI*(f.r-floor(f.r))))+0.12*live*sin(time*1.3)
            if(Build.VERSION.SDK_INT>=33 && shader!=null) {
                shader.setFloatUniform("uDensity",d)
                shader.setFloatUniform("uObj",x,y,radius,1f)
                shader.setFloatUniform("uMorph",f.r.toFloat(),yaw.toFloat(),f.lean.toFloat(),f.squash.toFloat())
                shader.setFloatUniform("uLive",time.toFloat(),live.toFloat(),ClayReactionMorphMotion.smooth(0.35,1.0,f.lift).toFloat(),d)
                shader.setFloatUniform("uHole",bubble.left,bubble.top,bubble.width,bubble.height)
                shader.setFloatUniform("uDim",f.hold.toFloat(),20f,1-0.03f*f.press.toFloat(),0.043f)
                shader.setFloatUniform("uSeat",sx,sy,17f,(1-ClayReactionMorphMotion.smooth(0.0,0.22,f.lift)).toFloat())
                paint.shader=shader; drawContext.canvas.nativeCanvas.drawRect(0f,0f,size.width,size.height,paint)
            } else drawClayFallback(f,bubble,Offset(x*d,y*d),radius*d,Offset(sx*d,sy*d),d)
            drawClayTray(f,w,trayTop,d,typeface)
            val fingerAlpha=(0.85*f.finger).toFloat().coerceIn(0f,1f)
            val fingerCenter=Offset((bubble.left+bubble.width*0.42f+f.fingerDx.toFloat()*72)*d,(bubble.top+bubble.height/2)*d)
            val fingerR=22*d*(1-0.12f*f.press.toFloat())
            drawCircle(Color.White.copy(alpha=0.4f*fingerAlpha),fingerR,fingerCenter)
            drawCircle(Color.White.copy(alpha=0.8f*fingerAlpha),fingerR,fingerCenter,style=Stroke(1.5f*d))
        }
    }
}

private class ClayReactionMorphEngine(private val scope: CoroutineScope,private val haptic:(Int)->Unit) {
    var auto=false; var reduced=false; var looping=false
    var clock by mutableDoubleStateOf(0.0); private var autoStart=0.0
    private var scripted by mutableStateOf(ClayReactionMorphFrame())
    var badge by mutableIntStateOf(1); private set
    private val lift=Animatable(0f);private val r=Animatable(1f);private val hold=Animatable(0f);private val press=Animatable(0f);private val squash=Animatable(0f);private val lean=Animatable(0f);private val finger=Animatable(0f)
    private var fingerDx=0.0
    private var lastTouch=-100.0; private var dragging=false;private var flying=false;private var resuming=false;private var r0=1f;private var dx=0f
    private var flight: Job?=null;private var liftJob: Job?=null;private var resumeJob: Job?=null
    val frame: ClayReactionMorphFrame get()=if(looping) scripted else ClayReactionMorphFrame(lift.value.toDouble(),r.value.toDouble(),hold.value.toDouble(),press.value.toDouble(),squash.value.toDouble(),lean.value.toDouble(),finger.value.toDouble(),fingerDx)
    fun tick(dt: Double) {
        clock+=dt
        if(looping) {scripted=ClayReactionMorphMotion.frameAt(clock-autoStart);return}
        if(dragging||flying||lift.value>0.001f) lastTouch=clock
        if(auto&&!resuming&&clock-lastTouch>=2) {
            resuming=true
            resumeJob=scope.launch {
                if(abs(r.value-1f)>0.001f) r.animateTo(1f,tween(600,easing=ClayInOut))
                while(abs(squash.value)>0.002) delay(16)
                lift.snapTo(0f);r.snapTo(1f);squash.snapTo(0f);lean.snapTo(0f);hold.snapTo(0f)
                autoStart=clock-ClayReactionMorphMotion.REST_T;badge=1;looping=true;resuming=false
            }
        }
    }
    fun takeOver() {
        lastTouch=clock;resumeJob?.cancel();resuming=false
        if(!looping)return
        val f=scripted;fingerDx=f.fingerDx;looping=false
        scope.launch {
            lift.snapTo(f.lift.toFloat());r.snapTo(f.r.toFloat());hold.snapTo(f.hold.toFloat());press.snapTo(f.press.toFloat());squash.snapTo(f.squash.toFloat());lean.snapTo(f.lean.toFloat());finger.snapTo(f.finger.toFloat())
            launch {finger.animateTo(0f,tween(150,easing=ClayQuad))};launch{press.animateTo(0f,tween(150,easing=ClayQuad))};launch{squash.animateTo(0f,tween(200,easing=ClayQuad))}
            if(lift.value>0.001f) sendHome(r.value.roundToInt().coerceIn(0,3))
            else {launch {hold.animateTo(0f,tween(200,easing=ClayQuad))};launch {lean.animateTo(0f,tween(200,easing=ClayQuad))};launch{r.animateTo(r.value.roundToInt().toFloat(),tween(200,easing=ClayQuad))}}
        }
    }
    fun pressDown() { takeOver();scope.launch {press.animateTo(1f,tween(120,easing=ClayOut))} }
    fun pressUp() { lastTouch=clock;scope.launch {press.animateTo(0f,tween(150,easing=ClayOut))} }
    fun startDrag() {
        flight?.cancel();liftJob?.cancel();flying=false;dragging=true;r0=r.value;dx=0f;lastTouch=clock
        liftJob=scope.launch {
            launch {r.stop()}
            launch {lift.animateTo(1f,if(reduced)tween(220,easing=ClayOut) else claySpring(lift,1f,550,0.62f))}
            launch {hold.animateTo(1f,tween(200,easing=ClayOut))}
            launch {press.animateTo(0f,if(reduced)tween(180,easing=ClayOut) else claySpring(press,0f,350,0.7f))}
            if(!reduced) launch {squash.animateTo(-0.3f,tween(120,easing=ClayOut));squash.animateTo(0f,claySpring(squash,0f,500,0.5f))}
        }
        haptic(1)
    }
    fun drag(delta: Float,velocity: Double) {
        lastTouch=clock;dx+=delta
        var v=r0+dx/72f
        if(v<0) v=(-0.25*(1-exp(v/0.25))).toFloat()
        if(v>3) v=(3+0.25*(1-exp(-(v-3)/0.25))).toFloat()
        val before=r.value.coerceIn(0f,3f).roundToInt()
        scope.launch {r.snapTo(v)}
        if(v.coerceIn(0f,3f).roundToInt()!=before) haptic(0)
        if(!reduced)scope.launch {lean.animateTo((velocity/1400).coerceIn(-1.0,1.0).toFloat(),claySpring(lean,(velocity/1400).coerceIn(-1.0,1.0).toFloat(),300,0.8f))}
    }
    fun release() {lastTouch=clock;if(dragging){dragging=false;sendHome(r.value.roundToInt().coerceIn(0,3))}}
    private fun sendHome(k: Int) {
        flight?.cancel();liftJob?.cancel();flying=true
        flight=scope.launch {
            launch {r.animateTo(k.toFloat(),claySpring(r,k.toFloat(),450,1f))}
            launch {hold.animateTo(0f,tween(300,easing=ClayOut))}
            launch {lean.animateTo(0f,claySpring(lean,0f,400,1f))}
            lift.animateTo(0f,tween(((if(reduced)240 else 600)*lift.value.coerceIn(0.3f,1f)).roundToInt(),easing=ClayInOut))
            flying=false;badge=k;haptic(2);lastTouch=clock
            if(!reduced){squash.animateTo(0.7f,tween(90,easing=ClayOut));squash.animateTo(0f,claySpring(squash,0f,650,0.35f))}
        }
    }
    fun choose(k: Int) {takeOver();badge=k;scope.launch {r.animateTo(k.toFloat(),tween(350,easing=ClayInOut))}}
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClayTray(f: ClayReactionMorphFrame,w: Float,trayTop: Float,d: Float,typeface: Typeface) {
    val alpha=ClayReactionMorphMotion.smooth(0.2,1.0,f.hold).toFloat().coerceIn(0f,1f)
    if(alpha<=0)return
    val left=(w-288)/2-4;val y=trayTop+8*(1-f.hold.toFloat())
    drawRoundRect(Color(0xFF202028).copy(alpha=0.92f*alpha),Offset(left*d,y*d),Size(296*d,44*d),CornerRadius(22*d))
    drawRoundRect(Color.White.copy(alpha=0.08f*alpha),Offset(left*d,y*d),Size(296*d,44*d),CornerRadius(22*d),style=Stroke(d))
    val k=f.r.toFloat().coerceIn(0f,3f);val i=floor(k).toInt().coerceAtMost(2)
    val tint=clayColor(ClayReactionMorphTheme.tints[i],ClayReactionMorphTheme.tints[i+1],k-i)
    val pill=Offset((left+4+k*72)*d,(y+4)*d)
    drawRoundRect(tint.copy(alpha=0.24f*alpha),pill,Size(72*d,34*d),CornerRadius(17*d))
    drawRoundRect(tint.copy(alpha=0.6f*alpha),pill,Size(72*d,34*d),CornerRadius(17*d),style=Stroke(d))
    val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;textSize=14*d;textAlign=Paint.Align.CENTER}
    ClayReactionMorphTheme.reactions.forEachIndexed { index,name ->
        val near=max(0f,1-abs(k-index));text.color=clayColor(ClayReactionMorphTheme.mute,Color.White,near).copy(alpha=alpha).toArgb()
        val baseline=(y+22)*d-(text.ascent()+text.descent())/2
        drawContext.canvas.nativeCanvas.drawText(name,(left+4+72*index+36)*d,baseline,text)
    }
}

/** API 26–32: same geometry and colours with flat vector reactions. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClayFallback(f: ClayReactionMorphFrame,bubble: Rect,center: Offset,radius: Float,seat: Offset,d: Float) {
    val dim=Path().apply { fillType=PathFillType.EvenOdd;addRect(Rect(Offset.Zero,size));addRoundRect(RoundRect(Rect(bubble.left*d,bubble.top*d,bubble.right*d,bubble.bottom*d),CornerRadius(20*d))) }
    drawPath(dim,Color(0xFF000005).copy(alpha=(f.hold*0.64).toFloat().coerceIn(0f,1f)))
    val presence=(1-ClayReactionMorphMotion.smooth(0.0,0.22,f.lift)).toFloat()
    drawCircle(ClayReactionMorphTheme.page.copy(alpha=presence),19.5f*d,seat);drawCircle(Color(0xFF2B2B33).copy(alpha=presence),17*d,seat)
    val k=f.r.toFloat().coerceIn(0f,3f);val i=floor(k).toInt().coerceAtMost(2);val tint=clayColor(ClayReactionMorphTheme.tints[i],ClayReactionMorphTheme.tints[i+1],k-i)
    val index=k.roundToInt()
    if(index==1) {
        val heart=Path().apply {moveTo(center.x,center.y+radius*0.85f);cubicTo(center.x-radius*1.4f,center.y,center.x-radius*0.85f,center.y-radius*1.2f,center.x,center.y-radius*0.45f);cubicTo(center.x+radius*0.85f,center.y-radius*1.2f,center.x+radius*1.4f,center.y,center.x,center.y+radius*0.85f);close()}
        drawPath(heart,tint)
    } else if(index==0) {
        drawRoundRect(tint,center-Offset(radius*0.7f,radius*0.1f),Size(radius*1.4f,radius),CornerRadius(radius*0.28f))
        drawRoundRect(tint,center-Offset(radius*0.65f,radius*0.95f),Size(radius*0.5f,radius*1.4f),CornerRadius(radius*0.24f))
    } else {
        drawCircle(tint,radius,center);val ink=Color(0xFF3D1F12)
        for(side in listOf(-1,1)) if(index==3) drawOval(ink,center+Offset(side*radius*0.32f-radius*0.1f,-radius*0.3f),Size(radius*0.2f,radius*0.3f)) else drawArc(ink,190f,160f,false,center+Offset(side*radius*0.34f-radius*0.18f,-radius*0.3f),Size(radius*0.36f,radius*0.25f),style=Stroke(radius*0.07f))
        drawOval(Color(0xFF66101A),center+Offset(-radius*(if(index==3)0.19f else 0.5f),radius*0.1f),Size(radius*(if(index==3)0.38f else 1f),radius*0.55f))
    }
}

// Reanimated duration spring stiffness solver. Compose assumes mass 1, so use k / mass.
private fun claySpring(value: Animatable<Float, AnimationVector1D>, end: Float, duration: Int, ratio: Float): SpringSpec<Float> {
    val x0=(value.value-end).toDouble(); val v0=value.velocity.toDouble(); val mass=4.0; val threshold=6e-9
    if(abs(x0)+abs(v0)<1e-9)return spring(dampingRatio=ratio,stiffness=2000f,visibilityThreshold=0.00001f)
    val seconds=duration*1.5/1000
    fun fraction(k: Double): Double {
        val omega=sqrt(k/mass)*ratio
        val decay=exp(-omega*seconds)
        val x=(x0+(v0+x0*omega)*seconds)*decay
        val v=(x0+(v0+x0*omega)*seconds)*decay*(-omega)+(v0+x0*omega)*decay
        return (k*x*x+mass*v*v)/(k*x0*x0+mass*v0*v0)-threshold
    }
    var lo=2.220446049250313e-16;var hi=8000.0;var mid=(lo+hi)/2
    for(i in 0 until 100){mid=(lo+hi)/2;val diff=fraction(mid);if(abs(diff)<threshold*1e-3)break;if(diff>0)lo=mid else hi=mid}
    return spring(dampingRatio=ratio,stiffness=(mid/mass).toFloat(),visibilityThreshold=0.00001f)
}

private fun clayColor(a: Color,b: Color,t: Float): Color {
    fun channel(x: Float,y: Float)=((x.toDouble().pow(2.2)*(1-t)+y.toDouble().pow(2.2)*t).pow(1/2.2)).toFloat()
    return Color(channel(a.red,b.red),channel(a.green,b.green),channel(a.blue,b.blue),a.alpha+(b.alpha-a.alpha)*t)
}
