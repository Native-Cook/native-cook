
package com.ngocdevv.nativecook.blocks.pickers.horseshoetimeruler

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlin.math.*

private const val LOOP = 8.0

@Composable
fun HorseshoeTimeRuler(autoPlay: Boolean=false, previewTime: Double?=null) {
    val context=LocalContext.current
    val haptic=LocalHapticFeedback.current
    val view=androidx.compose.ui.platform.LocalView.current
    SideEffect { (view.context as? android.app.Activity)?.window?.let { window -> androidx.core.view.WindowInsetsControllerCompat(window,view).apply { isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false } } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var epoch by remember { mutableDoubleStateOf(-1.0) }
    var lastTouch by remember { mutableDoubleStateOf(-100.0) }
    var manual by remember { mutableDoubleStateOf(0.0) }
    var origin by remember { mutableDoubleStateOf(0.0) }
    var dragging by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset(196.5f,426.0f)) }
    var velocity by remember { mutableDoubleStateOf(0.0) }
    fun phase():Double {
        if(previewTime!=null) return previewTime.mod(LOOP)
        if(reduced) return 3.8
        if(lastTouch>=0) {
            val age=max(0.0,clock-lastTouch)
            if(dragging) return manual
            if(age<2) return (manual+velocity*age*exp(-8*age)).coerceIn(0.0,LOOP-.001)
            if(age<2.4) return manual*(1-smooth((age-2)/.4))
            return if(autoPlay) (age-2.4).mod(LOOP) else 0.0
        }
        return if(autoPlay && epoch>=0) max(0.0,clock-epoch).mod(LOOP) else 0.0
    }
    LaunchedEffect(autoPlay,reduced,previewTime,lifecycle) {
        if(reduced || previewTime!=null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epoch=-1.0;lastTouch=-100.0
            while(true) {
                if(!autoPlay && lastTouch<0) snapshotFlow { lastTouch }.first { it>=0 }
                withFrameNanos { nanos ->
                    val now=nanos/1_000_000_000.0
                    if(epoch<0) epoch=now
                    if(autoPlay || dragging || now-lastTouch<2.4) clock=now
                    if(!autoPlay && !dragging && lastTouch>=0 && now-lastTouch>=2.4) { lastTouch=-100.0;manual=0.0 }
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().background(Color(0xFF070708))
        .semantics { contentDescription=previewTime?.let { "Horseshoe Time Ruler preview "+(it*1000).roundToInt() } ?: "Horseshoe Time Ruler";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=3.8;lastTouch=clock;true }) }
        .pointerInput(reduced) {
            if(reduced) return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val scale=min(size.width/393.0,size.height/852.0)
                origin=phase();manual=origin;clock=System.nanoTime()/1_000_000_000.0;dragging=true;lastTouch=clock
                val initial=down.position
                var previous=initial
                var previousTime=down.uptimeMillis
                var travel=0.0
                point=Offset((initial.x/scale).toFloat(),(initial.y/scale).toFloat())
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    val delta=change.position-initial
                    travel=abs(delta.x.toDouble())+abs(delta.y.toDouble())
                    val amount=-delta.y/scale
                    manual=(origin+amount/90).coerceIn(0.0,LOOP-.001)
                    point=Offset((change.position.x/scale).toFloat(),(change.position.y/scale).toFloat())
                    val dt=max(1.0,(change.uptimeMillis-previousTime).toDouble())/1000
                    velocity=(-(change.position.y-previous.y)/scale/90/dt).coerceIn(-5.0,5.0)
                    previous=change.position;previousTime=change.uptimeMillis;lastTouch=clock
                    change.consume()
                } while(event.changes.any { it.pressed })
                if(travel<5) manual=3.8
                dragging=false;lastTouch=clock
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }) {
        val canvas=drawContext.canvas.nativeCanvas
        canvas.save()
        val scale=min(size.width/393.0,size.height/852.0)
        canvas.translate(((size.width-393*scale)/2).toFloat(),((size.height-852*scale)/2).toFloat())
        canvas.scale(scale.toFloat(),scale.toFloat())
        val strength=if(reduced || previewTime!=null) 0.0 else if(dragging) 1.0 else 1-smooth((clock-lastTouch-2)/.4)
        drawScene(HorseshoeTimeRulerPainter(canvas),phase(),196.5+(point.x-196.5)*strength,426.0+(point.y-426.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class HorseshoeTimeRulerPainter(val c: android.graphics.Canvas) {
    private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var blurFilter:android.graphics.BlurMaskFilter?=null
    fun blur(radius:Double) { blurFilter=if(radius>.35) android.graphics.BlurMaskFilter(radius.toFloat(),android.graphics.BlurMaskFilter.Blur.NORMAL) else null }
    private fun color(hex: Int,alpha: Double=1.0): Int = (alpha.coerceIn(0.0,1.0).times(255).toInt() shl 24) or hex
    private fun reset(hex: Int,alpha: Double=1.0,width: Double=0.0) { paint.shader=null;paint.maskFilter=blurFilter;paint.alpha=255;paint.color=color(hex,alpha);paint.style=if(width>0) android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL;paint.strokeWidth=width.toFloat();paint.strokeCap=android.graphics.Paint.Cap.ROUND;paint.strokeJoin=android.graphics.Paint.Join.ROUND }
    fun save() { c.save() }
    fun restore() { c.restore() }
    fun translate(x: Double,y: Double) { c.translate(x.toFloat(),y.toFloat()) }
    fun scale(x: Double,y: Double) { c.scale(x.toFloat(),y.toFloat()) }
    fun rotate(angle: Double) { c.rotate(angle.toFloat()) }
    fun skew(x:Double,y:Double) { c.skew(x.toFloat(),y.toFloat()) }
    fun r(x: Double,y: Double,w: Double,h: Double,hex: Int,radius: Double=0.0,alpha: Double=1.0) { reset(hex,alpha);c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),radius.toFloat(),radius.toFloat(),paint) }
    fun e(x: Double,y: Double,rx: Double,ry: Double,hex: Int,alpha: Double=1.0) { reset(hex,alpha);c.drawOval((x-rx).toFloat(),(y-ry).toFloat(),(x+rx).toFloat(),(y+ry).toFloat(),paint) }
    fun line(x: Double,y: Double,xx: Double,yy: Double,hex: Int,width: Double=1.0,alpha: Double=1.0) { reset(hex,alpha,width);c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),paint) }
    fun grad(x: Double,y: Double,w: Double,h: Double,a: Int,b: Int,d: Int,radius: Double=0.0,horizontal: Boolean=false,alpha: Double=1.0) { reset(0xFFFFFF);paint.shader=android.graphics.LinearGradient(x.toFloat(),y.toFloat(),(x+if(horizontal) w else 0.0).toFloat(),(y+if(horizontal) 0.0 else h).toFloat(),intArrayOf(color(a,alpha),color(b,alpha),color(d,alpha)),floatArrayOf(0f,.5f,1f),android.graphics.Shader.TileMode.CLAMP);c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),radius.toFloat(),radius.toFloat(),paint);paint.shader=null }
    fun radial(x: Double,y: Double,radius: Double,a: Int,b: Int,alpha: Double=1.0) { reset(0xFFFFFF);paint.shader=android.graphics.RadialGradient(x.toFloat(),y.toFloat(),radius.toFloat(),intArrayOf(color(a,alpha),color(b,0.0)),floatArrayOf(0f,1f),android.graphics.Shader.TileMode.CLAMP);c.drawCircle(x.toFloat(),y.toFloat(),radius.toFloat(),paint);paint.shader=null }
    fun clip(x: Double,y: Double,w: Double,h: Double,radius: Double=0.0) { val path=android.graphics.Path();path.addRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),radius.toFloat(),radius.toFloat(),android.graphics.Path.Direction.CW);c.clipPath(path) }
    fun text(value: String,x: Double,y: Double,size: Double,hex: Int,style: String="regular",align: Double=0.0,alpha: Double=1.0) { reset(hex,alpha);paint.textSize=size.toFloat();paint.typeface=fonts.getValue(style);paint.textAlign=if(align==1.0) android.graphics.Paint.Align.CENTER else if(align==2.0) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT; c.drawText(value,x.toFloat(),(y-paint.fontMetrics.ascent).toFloat(),paint) }
    fun fit(value:String,x:Double,y:Double,size:Double,width:Double,hex:Int,style:String="heavy") { paint.textSize=size.toFloat();paint.typeface=fonts.getValue(style);val measured=paint.measureText(value).toDouble();save();translate(x,y);scale(width/max(1.0,measured),1.0);text(value,0.0,0.0,size,hex,style,1.0);restore() }

    fun path(key: String,hex: Int,width: Double=0.0,alpha: Double=1.0) { reset(hex,alpha,width);paths[key]?.let { c.drawPath(it,paint) } }
    fun clipPath(key: String) { paths[key]?.let { c.clipPath(it) } }
    companion object {
        val fonts=mapOf("regular" to android.graphics.Typeface.create("sans-serif",0),"bold" to android.graphics.Typeface.create("sans-serif",1),"heavy" to android.graphics.Typeface.create("sans-serif-black",0),"condensed" to android.graphics.Typeface.create("sans-serif-condensed",1),"mono" to android.graphics.Typeface.create("monospace",0),"serif" to android.graphics.Typeface.create("serif",1),"italic" to android.graphics.Typeface.create("serif",3))
        val paths=mapOf<String,android.graphics.Path>("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145" to android.graphics.Path().also { p -> p.moveTo(82f,145f);p.lineTo(82f,548f);p.cubicTo(82f,700f,311f,700f,311f,548f);p.lineTo(311f,145f) })
    }
}

private fun drawScene(p:HorseshoeTimeRulerPainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    // A single path bends the linear day into a continuous horseshoe.
    val travel = 410.0*seg(t,1.0,3.8)*(1.0-seg(t,4.8,7.0))
    p.r(0.0,0.0,393.0,852.0,0x070708)
    p.save()
    p.translate(0.0,-22.0)
    p.grad(0.0,55.0,393.0,102.0,0x232225,0x262528,0x242326)
    p.r(53.0,97.0,70.0,30.0,0x2D2C2F,5.0)
    p.r(130.0,97.0,130.0,30.0,0x2D2C2F,5.0)
    p.r(267.0,97.0,72.0,30.0,0x2D2C2F,5.0)
    p.text("Friday",88.0,105.0,12.0,0xD6D4D9,"regular",1.0)
    p.text("23 Oct 2026",195.0,105.0,12.0,0xD6D4D9,"regular",1.0)
    p.text("13:36",303.0,105.0,12.0,0xD6D4D9,"regular",1.0)
    p.restore()
    p.save()
    p.translate(0.0,-32.0)
    p.clip(20.0,155.0,353.0,575.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x171619,111.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x050506,105.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x27262A,100.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x09090B,94.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x2F2E32,88.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x08080A,82.0)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x252428,74.0)
    val names = listOf("Morning pages","Studio review","A walk outside","Time to make","Lunch with team","Creative hour","Evening reset","Plan tomorrow")
    val times = listOf("08:00","09:30","11:00","12:15","13:30","15:00","17:30","19:00")
    for (idx in 0 until 64) {
        val i = idx.toDouble()
        var distance = i*18.0+travel
        if (distance >= 1150.0) { distance = distance-1150.0 }
        var x = 82.0
        var y = 155.0+distance
        var angle = 90.0
        if (distance > 393.0 && distance < 753.0) {
            val theta = 3.141592653589793-(distance-393.0)/114.5
            x = 196.5+114.5*cos(theta)
            y = 548.0+114.5*sin(theta)
            angle = theta*180.0/3.141592653589793-90.0
        }
        if (distance >= 753.0) {
            x = 311.0
            y = 548.0-(distance-753.0)
            angle = -90.0
        }
        p.save()
        p.translate(x,y)
        p.rotate(angle)
        p.line(0.0,-36.0,0.0,-32.0,0x545158,0.7,0.55)
        p.line(0.0,32.0,0.0,36.0,0x545158,0.7,0.55)
        p.restore()
    }
    for (idx in 0 until 8) {
        val i = idx.toDouble()
        var distance = 45.0+i*143.0+travel
        if (distance >= 1150.0) { distance = distance-1150.0 }
        var x = 82.0
        var y = 155.0+distance
        var angle = 90.0
        if (distance > 393.0 && distance < 753.0) {
            val theta = 3.141592653589793-(distance-393.0)/114.5
            x = 196.5+114.5*cos(theta)
            y = 548.0+114.5*sin(theta)
            angle = theta*180.0/3.141592653589793-90.0
        }
        if (distance >= 753.0) {
            x = 311.0
            y = 548.0-(distance-753.0)
            angle = -90.0
        }
        p.save()
        p.translate(x,y)
        p.rotate(angle)
        p.line(0.0,-36.0,0.0,36.0,pick(idx == 4,0xEF3B59,0x434147),pick(idx == 4,1.8,0.8))
        p.text(times[idx],8.0,-22.0,9.0,0x969199,"mono")
        p.text(names[idx],8.0,-7.0,10.0,0x8B858F)
        p.restore()
    }
    p.restore()
    p.save()
    p.translate(0.0,-48.0)
    p.line(196.5,616.0,196.5,729.0,0x117AFF,1.5,0.9)
    p.line(194.5,623.0,194.5,690.0,0x0A4D91,0.7,0.7)
    for (idx in 0 until 8) {
        val i = idx.toDouble()
        p.e(196.5,626.0+14.0*i,1.5,1.5,0x50A7FF,0.45)
    }
    p.radial(196.5,593.0,35.0,0x208EFF,0x070708,0.13)
    p.r(169.0,565.0,55.0,58.0,0x070708,28.0)
    p.e(196.5,593.0,25.0,25.0,0x0B1826)
    p.e(196.5,593.0,23.0,23.0,0x1987F9)
    p.e(196.5,593.0,20.0,20.0,0x050A0E)
    p.save()
    p.translate(196.5,593.0)
    p.rotate(-42.0+travel*0.085)
    p.line(0.0,0.0,12.0,-10.0,0x258FFF,2.2)
    p.line(0.0,0.0,-3.0,6.0,0x258FFF,2.0)
    p.e(0.0,0.0,2.2,2.2,0x59B9FF)
    p.restore()
    p.save()
    p.translate(196.5,553.0)
    p.rotate(-90.0)
    p.text("IN 18 HOURS",0.0,-6.0,9.0,0x77717D,"mono")
    p.restore()
    p.restore()
    p.save()
    p.translate(0.0,-14.0)
    p.e(42.0,756.0,7.0,7.0,0x157DE0)
    p.e(42.0,756.0,5.8,5.8,0x070708)
    p.line(42.0,756.0,42.0,752.0,0x268DF7,1.0)
    p.line(42.0,756.0,45.0,757.0,0x268DF7,1.0)
    p.text("Days",42.0,780.0,13.0,0x368FE6,"regular",1.0)
    p.text("Follow the day",196.5,780.0,14.0,0x368FE6,"regular",1.0)
    p.e(351.0,786.0,8.0,8.0,0x1F7FE5)
    p.e(351.0,786.0,6.5,6.5,0x070708)
    p.line(347.0,789.0,355.0,781.0,0x368FE6,1.2)
    p.line(350.0,781.0,355.0,781.0,0x368FE6,1.2)

    p.restore()
}
