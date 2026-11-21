package com.ngocdevv.nativecook.blocks.pickers.twinringtimepicker

import android.graphics.BlurMaskFilter
import android.graphics.Color as AC
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.graphics.Typeface
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

private const val LOOP = 9.0
private const val CTA_HEIGHT = 64.0
private fun twinRingCtaTop(offset: Double, height: Double, navigationInset: Double): Double =
    min(751 + 2 * offset, height - navigationInset - CTA_HEIGHT)
private fun trRound(x:Double) = floor(x+.5)
private fun trSeg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
private fun trMix(a: Double,b: Double,k: Double) = a+(b-a)*k
private fun trInOut(k: Double) = if(k<.5) 4*k*k*k else 1-(-2*k+2).pow(3)/2
private fun trSpring(t: Double, from: Double, to: Double, d: Double, k: Double): Double {
    if(t<=0) return from
    val x=from-to; val w=sqrt(k); val z=d/(2*w)
    if(z<1) { val wd=w*sqrt(1-z*z); return to+exp(-z*w*t)*(x*cos(wd*t)+z*w*x/wd*sin(wd*t)) }
    return to+exp(-w*t)*(x+w*x*t)
}
private fun trH(t: Double): Double = when {
    t<1.85 -> 8.0
    t<3 -> trSpring(t-1.85,8.0,9.0,15.1,93.4)
    t<3.7 -> trMix(9.0,11.0,trInOut(trSeg(t,3.0,3.7)))
    t<3.85 -> 11.0
    t<4.55 -> trMix(11.0,13.0,trInOut(trSeg(t,3.85,4.55)))
    t<7.15 -> 13.0
    t<8.45 -> trMix(13.0,8.0,trInOut(trSeg(t,7.15,8.45)))
    else -> 8.0
}
private fun trM(t: Double): Double = when {
    t<.6 -> 6.0
    t<.7 -> trMix(6.0,5.85,trSeg(t,.6,.7).pow(3))
    t<2.1 -> { val s=(t-.7).coerceAtMost(1.4); val raw=5.85+6.15*(1-exp(-3*s))/(1-exp(-4.2)); raw-.22/(2*PI)*sin(2*PI*raw)*exp(-3*s) }
    t<7 -> 12.0
    t<8.3 -> trMix(12.0,6.0,trInOut(trSeg(t,7.0,8.3)))
    else -> 6.0
}
private data class TwinRingTimePickerFrame(val h:Double=8.0,val m:Double=6.0,val open:Double=0.0,val fill:Double=0.0,val cta:Double=1.0,val kick:Double=0.0,val hs:Double=0.0,val ms:Double=0.0,val ampm:Double=0.0,val flash:Double=0.0)
private fun trFrame(t: Double): TwinRingTimePickerFrame {
    val h=trH(t); val m=trM(t); val dt=1.0/120
    val o=when { t>=5.4&&t<7 -> trSpring(t-5.4,0.0,1.0,22.1,157.9).coerceAtMost(1.04); t>=7&&t<8.3 -> 1-trInOut(trSeg(t,7.0,8.3)); else -> 0.0 }
    val fill=when { t<5.6 -> 0.0; t<6.3 -> 1-(1-trSeg(t,5.6,6.3)).pow(3); t<7 -> 1.0; t<8.3 -> 1-trInOut(trSeg(t,7.0,8.3)); else -> 0.0 }
    val cta=when { t>=5.3&&t<5.4 -> 1-.03*trSeg(t,5.3,5.4).pow(3); t>=5.4&&t<5.8 -> trSpring(t-5.4,.97,1.0,19.0,195.0); else -> 1.0 }
    return TwinRingTimePickerFrame(h,m,o,fill,cta,if(t>=1.85&&t<2.3) sin(PI*trSeg(t,1.85,2.25)) else 0.0,abs(trH((t+dt).coerceAtMost(8.999))-h)/dt,abs(trM((t+dt).coerceAtMost(8.999))-m)/dt,flash=if(t>=1.85&&t<1.97) 1-trSeg(t,1.85,1.97) else 0.0)
}
private fun trAdvance(x:Double,v:Double,target:Double,d:Double,k:Double,dt:Double):Pair<Double,Double> {
    val w=sqrt(k/4); val z=d/(2*sqrt(k*4)); val initial=x-target
    if(z<1) { val wd=w*sqrt(1-z*z); val b=(v+z*w*initial)/wd; val e=exp(-z*w*dt); val co=cos(wd*dt); val si=sin(wd*dt); val pos=e*(initial*co+b*si); return (target+pos) to (e*((-z*w)*(initial*co+b*si)-initial*wd*si+b*wd*co)) }
    val e=exp(-w*dt); val b=v+w*initial; val pos=e*(initial+b*dt); return (target+pos) to (e*(b-w*(initial+b*dt)))
}
private class TwinRingTimePickerLive {
    var h=8.0; var m=6.0; var hv=0.0; var mv=0.0; var ht=8.0; var mt=6.0
    var open=0.0; var fill=0.0; var openTarget=0.0; var ov=0.0
    var hd=40.4; var hk=503.5; var md=40.4; var mk=503.5
    var fillFrom=0.0; var fillTarget=0.0; var fillElapsed=0.0; var fillDuration=.7
    var ampm=0.0; var ampmVelocity=0.0
    var hLast=8.0; var mLast=6.0; var oLast=0.0; var aLast=0.0
    var he=0.0; var me=0.0; var oe=0.0; var ae=0.0
    var hReduced=false; var mReduced=false; var oReduced=false; var aReduced=false; var fillReduced=false
    var lastHapticH=8.0; var lastHapticM=6.0
    var hp=8.0; var mp=6.0; var hs=0.0; var ms=0.0
    fun setOpen(value:Double,reduced:Boolean) { openTarget=value; fillFrom=fill; fillTarget=value; fillElapsed=0.0; fillDuration=if(value==0.0) .5 else .7; fillReduced=reduced }
    var homing=false; var auto=true; var touching=false; var held by mutableIntStateOf(0); var t=0.0; var idle=0.0; var lastTick=0.0; var clock=0.0
    fun take(f:TwinRingTimePickerFrame) { if(homing) { ht=h; mt=m; hv=0.0; mv=0.0; homing=false }; if(auto) { h=trRound(f.h*1000)/1000; m=f.m; ht=h; mt=m; open=f.open; fill=f.fill; openTarget=open; oLast=open; ov=0.0; oe=0.0; fillFrom=fill; fillTarget=fill; fillElapsed=fillDuration; hv=0.0; mv=0.0; lastHapticH=trRound(h); lastHapticM=trRound(m); auto=false }; hp=h; mp=m; idle=0.0 }
}

/** Two coupled time wheels with a deterministic nine-second demonstration. */
@Composable
fun TwinRingTimePicker(autoPlay: Boolean = true) {
    val context=LocalContext.current; val view=LocalView.current; val scope=rememberCoroutineScope()
    val activity=LocalActivity.current
    val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
    val manual=remember { debug && activity?.intent?.getBooleanExtra("twinRingTimePickerManual",false)==true }
    var pin by remember { mutableStateOf(if(debug) activity?.intent?.getStringExtra("twinRingTimePickerTime")?.toDoubleOrNull() else null) }
    val window=LocalActivity.current?.window
    DisposableEffect(window,view) {
        val controller=window?.let { WindowCompat.getInsetsController(it,view) }
        val oldStatus=controller?.isAppearanceLightStatusBars
        val oldNav=controller?.isAppearanceLightNavigationBars
        onDispose { oldStatus?.let { controller.isAppearanceLightStatusBars=it }; oldNav?.let { controller.isAppearanceLightNavigationBars=it } }
    }
    SideEffect { window?.let { WindowCompat.getInsetsController(it,view) }?.apply { isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true } }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val onest=remember { Typeface.createFromAsset(context.assets,"twinringtimepicker/twinringtimepicker_Onest_ExtraBold.ttf") }
    val tight=remember { Typeface.createFromAsset(context.assets,"twinringtimepicker/twinringtimepicker_InterTight_SemiBold.ttf") }
    val live=remember { TwinRingTimePickerLive().apply { auto=autoPlay&&!reduced&&!manual } }
    var frame by remember { mutableStateOf(TwinRingTimePickerFrame()) }
    var pressed by remember { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current.density
    val status=WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val navigationBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)

    fun confirmTime() {
        pin = null
        live.take(frame)
        val opening = live.open <= .5
        live.setOpen(if (opening) 1.0 else 0.0, reduced)
        if (opening) scope.launch {
            delay(650)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    fun adjustTime(hours: Boolean, step: Int): Boolean {
        pin = null
        live.take(frame)
        live.setOpen(0.0, reduced)
        if (hours) { live.ht = trRound(live.h) + step; live.hLast = Double.NaN }
        else { live.mt = trRound(live.m) + step; live.mLast = Double.NaN }
        return true
    }

    LaunchedEffect(autoPlay,reduced,lifecycle) {
        live.auto=autoPlay&&!reduced&&!manual
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        var last=withFrameNanos { it }
        while(true) {
            val n=withFrameNanos { it }; val dt=((n-last)/1e9).coerceIn(0.0,.05); last=n; live.clock+=dt
            pin?.let { pinned ->
                val next=trFrame(((pinned%LOOP)+LOOP)%LOOP)
                val hour=((trRound(next.h).toInt()%24)+24)%24
                frame=next.copy(ampm=if(hour>=12) 1.0 else 0.0)
            }
            if(pin!=null) continue
            if(dt>0) { live.hs=trMix(live.hs,abs(live.h-live.hp)/dt,.5); live.ms=trMix(live.ms,abs(live.m-live.mp)/dt,.5) }
            live.hp=live.h; live.mp=live.m
            if(live.auto && !reduced) {
                live.t=(live.t+dt)%LOOP; val next=trFrame(live.t)
                val hour=((trRound(next.h).toInt()%24)+24)%24; val target=if(hour>=12) 1.0 else 0.0
                if(target!=live.aLast) {
                    if((target>live.ampm&&live.ampmVelocity<0)||(target<live.ampm&&live.ampmVelocity>0)) live.ampmVelocity=0.0
                    live.ae=.5*322.3*(live.ampm-target).pow(2)+2*live.ampmVelocity.pow(2); live.aLast=target
                }
                val (a,av)=trAdvance(live.ampm,live.ampmVelocity,target,30.5,322.3,dt)
                val energy=.5*322.3*(a-target).pow(2)+2*av.pow(2)
                if(live.ae==0.0||energy/live.ae<=6e-9) { live.ampm=target; live.ampmVelocity=0.0 } else { live.ampm=a; live.ampmVelocity=av }
                frame=next.copy(ampm=live.ampm); continue
            }
            if(reduced && live.auto) { live.take(frame) }
            if(!live.touching) {
                live.idle+=dt
                if(autoPlay&&!reduced&&!manual&&live.open<.01&&live.idle>2) {
                    val homed=abs(live.h-8)<.01&&abs(live.m-6)<.01&&live.hs<.15&&live.ms<.15
                    if(homed&&live.fill<.01) {
                        live.h=8.0; live.m=6.0; live.ht=8.0; live.mt=6.0; live.hv=0.0; live.mv=0.0; live.hp=8.0; live.mp=6.0; live.hs=0.0; live.ms=0.0
                        live.homing=false; live.t=0.0; live.auto=true; frame=trFrame(0.0); continue
                    } else if(!homed&&!live.homing) {
                        live.homing=true; live.ht=8.0; live.mt=6.0; live.hd=22.1; live.hk=157.9; live.md=22.1; live.mk=157.9
                    }
                }
            }
            fun clip(x:Double,target:Double,velocity:Double) = if((target>x&&velocity<0)||(target<x&&velocity>0)) 0.0 else velocity
            fun energy(x:Double,target:Double,velocity:Double,k:Double) = .5*k*(x-target)*(x-target)+2*velocity*velocity
            if(live.held==1) live.hLast=live.ht else {
                if(live.hLast!=live.ht) { live.hv=clip(live.h,live.ht,live.hv); live.he=energy(live.h,live.ht,live.hv,live.hk); live.hReduced=reduced; live.hLast=live.ht }
                val (x,v)=if(live.hReduced) live.ht to 0.0 else trAdvance(live.h,live.hv,live.ht,live.hd,live.hk,dt)
                if(live.he==0.0||energy(x,live.ht,v,live.hk)/live.he<=6e-9) { live.h=live.ht; live.hv=0.0 } else { live.h=x; live.hv=v }
            }
            if(live.held!=2) {
                val before=floor((live.m+.5)/12).toInt()
                if(live.mLast!=live.mt) { live.mv=clip(live.m,live.mt,live.mv); live.me=energy(live.m,live.mt,live.mv,live.mk); live.mReduced=reduced; live.mLast=live.mt }
                val (x,v)=if(live.mReduced) live.mt to 0.0 else trAdvance(live.m,live.mv,live.mt,live.md,live.mk,dt)
                if(live.me==0.0||energy(x,live.mt,v,live.mk)/live.me<=6e-9) { live.m=live.mt; live.mv=0.0 } else { live.m=x; live.mv=v }
                val after=floor((live.m+.5)/12).toInt()
                if(after!=before&&!live.homing) { live.ht=trRound(live.h)+(after-before); live.hd=15.1; live.hk=93.4; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
            } else live.mLast=live.mt
            if(live.oLast!=live.openTarget) { live.ov=clip(live.open,live.openTarget,live.ov); live.oe=energy(live.open,live.openTarget,live.ov,157.9); live.oReduced=reduced; live.oLast=live.openTarget }
            val (x,v)=if(live.oReduced) live.openTarget to 0.0 else trAdvance(live.open,live.ov,live.openTarget,22.1,157.9,dt)
            if(live.oe==0.0||energy(x,live.openTarget,v,157.9)/live.oe<=6e-9) { live.open=live.openTarget; live.ov=0.0 } else { live.open=x; live.ov=v }
            live.fillElapsed=(live.fillElapsed+dt).coerceAtMost(live.fillDuration)
            val fk=(live.fillElapsed/live.fillDuration).coerceIn(0.0,1.0); val eased=if(fk<.5) 2*fk*fk else 1-(-2*fk+2).pow(2)/2
            live.fill=if(live.fillReduced) live.fillTarget else trMix(live.fillFrom,live.fillTarget,eased)
            val hour=((trRound(live.h).toInt()%24)+24)%24
            val target=if(hour>=12) 1.0 else 0.0
            if(live.aLast!=target) { live.ampmVelocity=clip(live.ampm,target,live.ampmVelocity); live.ae=energy(live.ampm,target,live.ampmVelocity,322.3); live.aReduced=reduced; live.aLast=target }
            val (ampm,av)=if(live.aReduced) target to 0.0 else trAdvance(live.ampm,live.ampmVelocity,target,30.5,322.3,dt)
            if(live.ae==0.0||energy(ampm,target,av,322.3)/live.ae<=6e-9) { live.ampm=target; live.ampmVelocity=0.0 } else { live.ampm=ampm; live.ampmVelocity=av }
            val dh=trRound(live.h); val dm=trRound(live.m)
            if(!live.homing&&(dh!=live.lastHapticH||dm!=live.lastHapticM)&&live.clock-live.lastTick>=.1) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); live.lastTick=live.clock }
            live.lastHapticH=dh; live.lastHapticM=dm
            frame=TwinRingTimePickerFrame(live.h,live.m,live.open,live.fill,1.0,0.0,live.hs,live.ms,live.ampm)
        }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        val hour = Math.floorMod(trRound(frame.h).toInt(), 24)
        val minute = (Math.floorMod(trRound(frame.m).toInt(), 12) * 5).toString().padStart(2, '0')
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        val period = if (hour >= 12) "PM" else "AM"
        contentDescription = "Wake-up time"
        stateDescription = "$hour12:$minute $period"
        customActions = listOf(
            CustomAccessibilityAction("Add an hour") { adjustTime(true, 1) },
            CustomAccessibilityAction("Subtract an hour") { adjustTime(true, -1) },
            CustomAccessibilityAction("Add five minutes") { adjustTime(false, 1) },
            CustomAccessibilityAction("Subtract five minutes") { adjustTime(false, -1) },
        )
        onClick(label = if (frame.open > .5) "Edit time" else "Continue") { confirmTime(); true }
    }.pointerInput(autoPlay,reduced,navigationBottom) {
        awaitEachGesture {
            val down=awaitFirstDown(false)
            try {
            val scale=size.width/402f; val dy=(size.height/scale-874)/2
            val px=down.position.x/scale; val py=down.position.y/scale
            val ctaTop = twinRingCtaTop(dy.toDouble(), size.height.toDouble() / scale, navigationBottom.toDouble() / scale)
            val button = px in 27f..376f && py >= ctaTop && py <= ctaTop + CTA_HEIGHT
            val wheel = !button && py in (255 + dy)..(735 + dy)
            if(wheel) { pin=null; live.take(frame); live.touching=true }; if(button) pressed=true
            if(wheel&&live.open>.05) { live.setOpen(0.0,reduced); live.held=0 }
            else if(wheel) { live.held=if(px<201) 1 else 2; view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            val start=if(live.held==1) live.h else live.m; val per=if(live.held==1) 155*PI*.1 else 150*PI*.1
            var previousCarry=floor((live.m+.5)/12).toInt()
            val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position); var total=0f; var endX=px; var endY=py; var released=false
            fun settle(velocity:Double) {
                if(live.held==1) { live.ht=trRound(live.h+velocity*.3); live.hv=velocity; live.hLast=Double.NaN; live.hd=40.4; live.hk=503.5 }
                else if(live.held==2) { live.mt=trRound(live.m+velocity*.3); live.mv=velocity; live.mLast=Double.NaN; live.md=40.4; live.mk=503.5 }
            }
            do {
                val event=awaitPointerEvent(); val change=event.changes.firstOrNull { it.id==down.id } ?: break
                tracker.addPosition(change.uptimeMillis,change.position); total=change.position.y-down.position.y
                endX=change.position.x/scale; endY=change.position.y/scale; if(!change.pressed) released=true
                if(live.held!=0&&change.pressed) {
                    val value=start+total/scale/per
                    if(live.held==1) { live.h=value; live.ht=value; live.hv=0.0 } else { live.m=value; live.mt=value; live.mv=0.0 }
                    val carry=floor((live.m+.5)/12).toInt(); if(carry!=previousCarry) { live.ht=trRound(live.h)+(carry-previousCarry); live.hd=15.1; live.hk=93.4; previousCarry=carry; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                    change.consume()
                }
            } while(change.pressed)
            if(live.held!=0) {
                val vel=(tracker.calculateVelocity().y/scale/per).coerceIn(-14.0,14.0)
                settle(if(released) vel else 0.0)
            } else if(button&&released&&endX>=27&&endX<=376&&endY>=ctaTop&&endY<=ctaTop+CTA_HEIGHT) {
                confirmTime()
            }
            if(wheel || button) live.idle=0.0
            } finally {
                if(live.held==1) { live.ht=trRound(live.ht); live.hLast=Double.NaN; live.hd=40.4; live.hk=503.5 }
                else if(live.held==2) { live.mt=trRound(live.mt); live.mLast=Double.NaN; live.md=40.4; live.mk=503.5 }
                live.held=0; live.touching=false; pressed=false; live.idle=0.0
            }
        }
    }) {
        val scale=size.width/402f; val height=size.height/scale; val dy=(height-874)/2; val c=drawContext.canvas.nativeCanvas
        c.save(); c.scale(scale,scale); c.clipRect(0f,0f,402f,height)
        val ctaTop = twinRingCtaTop(dy.toDouble(), height.toDouble(), navigationBottom.toDouble() / scale)
        drawTwinRingTimePicker(c,frame,dy.toDouble(),status.toDouble()/(scale/density),ctaTop,onest,tight,live.held,pressed,scale/density)
        c.restore()
    }
}

private fun interpolateForTwinRing(a:Int,b:Int,k:Double):Int { val t=k.coerceIn(0.0,1.0); return AC.rgb((AC.red(a)+(AC.red(b)-AC.red(a))*t).roundToInt(),(AC.green(a)+(AC.green(b)-AC.green(a))*t).roundToInt(),(AC.blue(a)+(AC.blue(b)-AC.blue(a))*t).roundToInt()) }

private fun drawTwinRingTimePicker(c:android.graphics.Canvas,f:TwinRingTimePickerFrame,dy:Double,status:Double,ctaTop:Double,onest:Typeface,tight:Typeface,held:Int,pressed:Boolean,dpScale:Float) {
    fun col(s:String)=AC.parseColor(s)
    val page=col("#EEEDEB"); val ink=col("#0B0B0B"); val ring=col("#D4D4D4"); val yellow=col("#F5B400")
    fun paint(color:Int,style:Paint.Style=Paint.Style.FILL)=Paint(3).apply { this.color=color; this.style=style }
    fun rect(x:Double,y:Double,w:Double,h:Double,r:Double,color:Int) { c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint(color)) }
    fun line(x:Double,y:Double,x2:Double,y2:Double,color:Int,width:Float=1f) { c.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),paint(color).apply { strokeWidth=width; strokeCap=Paint.Cap.ROUND }) }
    fun tracked(value:String,x:Double,y:Double,p:Paint,tracking:Float,center:Boolean) {
        val widths=FloatArray(value.length); p.getTextWidths(value,widths)
        var at=x.toFloat()-(if(center) (widths.sum()+tracking*(value.length-1))/2 else 0f)
        p.textAlign=Paint.Align.LEFT
        for(i in value.indices) { c.drawText(value,i,i+1,at,y.toFloat(),p); at+=widths[i]+tracking }
    }
    fun text(value:String,x:Double,y:Double,size:Float,color:Int,font:Typeface,align:Paint.Align=Paint.Align.CENTER,tracking:Float=0f) { tracked(value,x,y,paint(color).apply { textSize=size; typeface=font },tracking,align==Paint.Align.CENTER) }
    c.drawColor(interpolateForTwinRing(page,col("#F3ECDF"),f.open))
    val backTop=max(67.0,status+8); rect(23.0,backTop,44.0,44.0,22.0,col("#E2E2E2"))
    line(53.0,backTop+22,37.5,backTop+22,ink,2f); line(43.0,backTop+16,37.0,backTop+22,ink,2f); line(37.0,backTop+22,43.0,backTop+28,ink,2f)
    rect(111.0,backTop+16,180.0,12.0,6.0,col("#D6D6D6")); rect(111.0,backTop+16,trMix(41.0,64.0,f.fill),12.0,6.0,ink)
    val titleTop=max(156.0,status+100); val titleMetrics=paint(ink).apply { textSize=29.5f; typeface=tight }.fontMetrics
    val titleBaseline=titleTop+33.0/2-(titleMetrics.ascent+titleMetrics.descent)/2
    text("When do you usually",201.0,titleBaseline,29.5f,ink,tight,tracking=-1f/dpScale); text("wake up?",201.0,titleBaseline+33,29.5f,ink,tight,tracking=-1f/dpScale)
    c.save(); c.translate(0f,dy.toFloat())
    val o=f.open.coerceIn(0.0,1.0); val left=-44-36*max(f.open,0.0); val right=446+36*max(f.open,0.0)
    for((i,cx) in listOf(left,right).withIndex()) {
        c.drawCircle(cx.toFloat(),495f,226f,paint(if(held==i+1) col("#CACACA") else ring,Paint.Style.STROKE).apply { strokeWidth=38f })
        val turn=(if(i==0) f.h else -f.m)*PI/10
        for(j in 0..<100) {
            val a=j*PI/50+turn; val r=207.0; val length=if(j%5==0) 12 else 6
            line(cx+cos(a)*r,495+sin(a)*r,cx+cos(a)*(r+length),495+sin(a)*(r+length),col("#B8B8B8"),1.5f)
        }
        val mesh=atan2(442.0-495,201.0-(if(i==0) -44.0 else 446.0)); val r=243.0; val flash=Path()
        for(j in 0..12) { val a=mesh-7/r+14/r*j/12; val x=(cx+cos(a)*r).toFloat(); val y=(495+sin(a)*r).toFloat(); if(j==0) flash.moveTo(x,y) else flash.lineTo(x,y) }
        c.drawPath(flash,paint(col("#BEBEBE"),Paint.Style.STROKE).apply { strokeWidth=4f; strokeCap=Paint.Cap.ROUND; alpha=(f.flash*255).roundToInt().coerceIn(0,255) })
    }
    line(201.0,487.0,201.0,503.0, interpolateForTwinRing(page,col("#C6C6C6"),1-o),1f)
    fun interpolate(a:Int,b:Int,k:Double):Int { val t=k.coerceIn(0.0,1.0); return AC.rgb((AC.red(a)+(AC.red(b)-AC.red(a))*t).roundToInt(),(AC.green(a)+(AC.green(b)-AC.green(a))*t).roundToInt(),(AC.blue(a)+(AC.blue(b)-AC.blue(a))*t).roundToInt()) }
    val colors=listOf(ink,col("#A8A8A8"),col("#C9C9C9"),col("#DDDDDD"))
    for(side in 0..1) for(j in -4..4) {
        val value=if(side==0) f.h else f.m; val v=trRound(value).toInt()+j; val k=v-value; val a=abs(k)
        val sc=when { a<1 -> trMix(1.0,.84,a); a<2 -> trMix(.84,.74,a-1); a<3 -> trMix(.74,.66,a-2); else -> .66 }
        val depth=when { a<1 -> 0.0; a<2 -> trMix(0.0,1.5,a-1); a<3 -> trMix(1.5,4.0,a-2); else -> 4.0 }
        val alpha=(if(a<3.3) 1.0 else 1-trSeg(a,3.3,3.9))*(if(a<.5) 1.0 else 1-o)
        if(alpha<=0) continue
        val angle=if(side==0) -k*PI/10 else PI+k*PI/10; val radius=if(side==0) 155 else 150; val cx=if(side==0) left else right
        val x=cx+cos(angle)*radius; val y=495+sin(angle)*radius; val rotation=if(side==0) angle else angle-PI
        val label=if(side==0) { val n=((v-1)%12+12)%12+1; if(n<10) "0$n" else "$n" } else { val n=((v*5)%60+60)%60; if(n<10) "0$n" else "$n" }
        val ci=floor(a.coerceAtMost(2.999)).toInt(); val color=interpolate(colors[ci],colors[ci+1],a-ci)
        val size=sc*trMix(1.0,1+.28*o,1-a.coerceAtMost(1.0)); val blur=depth+min(14.0,(if(side==0) f.hs else f.ms)*2.4)
        c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate((rotation*180/PI).toFloat()); c.scale(size.toFloat(),size.toFloat())
        val p=paint(color).apply { typeface=onest; textSize=56f; textAlign=Paint.Align.CENTER; this.alpha=(alpha*255).roundToInt().coerceIn(0,255) }
        if(Build.VERSION.SDK_INT>=31 && c.isHardwareAccelerated && blur>.3) {
            val node=RenderNode("TwinRingTimePicker numeral"); node.setPosition(-128,-128,128,128)
            val recording=node.beginRecording(); recording.drawText(label,128f,148f,p); node.endRecording()
            node.setRenderEffect(RenderEffect.createBlurEffect(depth.toFloat().coerceAtLeast(.0001f),blur.toFloat().coerceAtLeast(.0001f),Shader.TileMode.DECAL))
            c.drawRenderNode(node)
        } else { if(blur>.3) p.maskFilter=BlurMaskFilter(blur.toFloat(),BlurMaskFilter.Blur.NORMAL); c.drawText(label,0f,20f,p) }
        c.restore()
    }
    c.drawCircle(201f,489f,4.5f,paint(ink)); c.drawCircle(201f,506f,4.5f,paint(ink))
    line(201.0,465.0,201.0,481.0,interpolate(page,yellow,1-trSeg(o,0.0,.3)),1f)
    val rise=42*(1-(1-o).pow(3)); val angle=90*(f.m-6-(f.h-8)); val sc=1+.25*f.kick
    c.save(); c.translate(201f,(442-rise-3*f.kick).toFloat()); c.rotate(angle.toFloat()); c.scale(sc.toFloat(),sc.toFloat())
    c.drawCircle(0f,0f,46f,paint(yellow).apply { alpha=(.35*trSeg(o,.3,1.0)*255).roundToInt(); maskFilter=BlurMaskFilter(18f,BlurMaskFilter.Blur.NORMAL) })
    val path=Path()
    for(i in 0..120) { val t=i/120.0*2*PI; val r=trMix(25.0,32.0,o)*(1-(1-o)*.26*(.5-.5*cos(4*t))); val x=(cos(t)*r).toFloat(); val y=(sin(t)*r).toFloat(); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }; path.close()
    c.save(); c.translate(0f,1f); c.drawPath(path,paint(col("#8A6400")).apply { alpha=46; maskFilter=BlurMaskFilter(1f,BlurMaskFilter.Blur.NORMAL) }); c.restore()
    val pinColor=interpolate(yellow,col("#F29A1E"),trSeg(f.h,11.5,12.5)*(if(f.h<20) 1 else 0)); c.drawPath(path,paint(pinColor)); c.drawPath(path,paint(col("#FFD24A"),Paint.Style.STROKE).apply { strokeWidth=1f; alpha=153 })
    c.drawCircle(19f,0f,1.5f,paint(col("#C98F00")).apply { alpha=((1-trSeg(o,0.0,.4))*255).roundToInt() }); c.restore()
    val ampmAlpha=if(o<.4) 1-trSeg(o,0.0,.15) else trSeg(o,.55,.9)
    c.save(); c.translate((if(o>.4) 184 else 338).toFloat(),(if(o>.4) 535 else 483).toFloat())
    val ak=f.ampm; val fade=trSeg(ak,.3,.7)
    val amPaint=paint(ink).apply { textSize=23f; typeface=onest; alpha=((1-fade)*ampmAlpha*255).roundToInt().coerceIn(0,255) }
    val pmPaint=paint(ink).apply { textSize=23f; typeface=onest; alpha=(fade*ampmAlpha*255).roundToInt().coerceIn(0,255) }
    fun ampmText(label:String,translation:Double,p:Paint) {
        val tracking=-.5f/dpScale; val metrics=p.fontMetrics
        c.save(); c.translate(0f,translation.toFloat()); tracked(label,0.0,(-metrics.ascent).toDouble(),p,tracking,false); c.restore()
    }
    ampmText("AM",12*ak,amPaint); ampmText("PM",-12*(1-ak),pmPaint); c.restore(); c.restore()
    val ctaCenter = ctaTop + CTA_HEIGHT / 2
    c.save(); c.scale(f.cta.toFloat(),f.cta.toFloat(),201f,ctaCenter.toFloat()); rect(27.0,ctaTop,349.0,CTA_HEIGHT,22.0,col(if(pressed) "#B23500" else "#C93C00"))
    val ctaMetrics=paint(ink).apply { textSize=20f; typeface=tight }.fontMetrics
    text("Continue",45.0,ctaCenter-(ctaMetrics.ascent+ctaMetrics.descent)/2,20f,col("#FFFBF8"),tight,Paint.Align.LEFT)
    line(335.5,ctaCenter,356.0,ctaCenter,col("#FFFBF8"),2.5f); line(350.0,ctaCenter-5.5,356.0,ctaCenter,col("#FFFBF8"),2.5f); line(356.0,ctaCenter,350.0,ctaCenter+5.5,col("#FFFBF8"),2.5f); c.restore()
}
