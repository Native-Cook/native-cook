
package com.ngocdevv.nativecook.blocks.lists.foldingweekagenda

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

private const val LOOP = 7.2

@Composable
fun FoldingWeekAgenda(autoPlay: Boolean=false, previewTime: Double?=null) {
    val context=LocalContext.current
    val haptic=LocalHapticFeedback.current
    val view=androidx.compose.ui.platform.LocalView.current
    SideEffect { (view.context as? android.app.Activity)?.window?.let { window -> androidx.core.view.WindowInsetsControllerCompat(window,view).apply { isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true } } }
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
        if(reduced) return 0.0
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
    Canvas(Modifier.fillMaxSize().background(Color(0xFFFCFCFA))
        .semantics { contentDescription=previewTime?.let { "Folding Week Agenda preview "+(it*1000).roundToInt() } ?: "Folding Week Agenda";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=2.4;lastTouch=clock;true }) }
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
                if(travel<5) manual=2.4
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
        drawScene(FoldingWeekAgendaPainter(canvas),phase(),196.5+(point.x-196.5)*strength,426.0+(point.y-426.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class FoldingWeekAgendaPainter(val c: android.graphics.Canvas) {
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
        val paths=mapOf<String,android.graphics.Path>("M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z" to android.graphics.Path().also { p -> p.moveTo(12f,45f);p.lineTo(12f,40f);p.cubicTo(14f,34f,22f,36f,22f,29f);p.cubicTo(16f,28f,17f,24f,14f,23f);p.lineTo(17f,17f);p.cubicTo(12f,3f,36f,-1f,39f,12f);p.cubicTo(42f,20f,35f,27f,32f,31f);p.lineTo(33f,36f);p.lineTo(40f,49f);p.lineTo(14f,49f);p.close() },
        "M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z" to android.graphics.Path().also { p -> p.moveTo(143f,786f);p.lineTo(149f,780f);p.lineTo(155f,786f);p.lineTo(155f,792f);p.lineTo(143f,792f);p.close() })
    }
}

private fun drawScene(p:FoldingWeekAgendaPainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    // A continuous calendar surface folds into the next appointment.
    val fold = seg(t,1.1,2.0)*(1.0-seg(t,3.6,4.6))
    val oldAlpha = 1.0-seg(fold,0.25,0.65)
    val compactAlpha = seg(fold,0.35,0.8)
    val panelHeight = 340.0-280.0*fold
    p.r(0.0,0.0,393.0,852.0,0xFCFCFA)
    p.radial(196.0,478.0-276.0*fold,192.0,0xF3DDB2,0xFCFCFA,0.32*(1.0-fold))
    p.r(24.0,108.0,345.0,panelHeight,0xCECBC5,24.0,0.08)
    p.r(24.0,106.0,345.0,panelHeight,0x191A19,24.0)
    p.save()
    p.clip(24.0,106.0,345.0,panelHeight,24.0)
    p.text("Upcoming",44.0,130.0-27.0*fold,24.0,0xF1F1EA,"regular",0.0,oldAlpha)
    p.text("In the next 2 weeks",44.0,163.0-24.0*fold,21.0,0x70746F,"regular",0.0,oldAlpha)
    for (idx in 0 until 3) {
        val i = idx.toDouble()
        p.e(335.0,138.0+8.0*i-50.0*fold,1.3,1.3,0x92978F,oldAlpha)
    }
    val week = listOf("MON","TUE","WED","THU","FRI","SAT","SUN")
    for (idx in 0 until 14) {
        val i = idx.toDouble()
        val column = (idx % 7).toDouble()
        val row = floor(i/7.0)
        val x = 58.0+46.0*column
        val y = 358.0+47.0*row-225.0*fold
        p.e(x,y,17.5,17.5,pick(idx == 4,0xE5E7DF,0x30332E),oldAlpha)
        p.text(str(i+8.0),x,y-7.0,12.0,pick(idx == 4,0x222922,0xE1E4DB),"regular",1.0,oldAlpha)
        if (idx < 7) { p.text(week[idx],x,325.0-215.0*fold,8.0,0x777D73,"mono",1.0,oldAlpha) }
        if (idx == 4) {
            p.e(x-6.0,y-10.0,2.0,2.0,0xCB78D8,oldAlpha)
            p.e(x,y-11.0,2.0,2.0,0x9CD657,oldAlpha)
            p.e(x+6.0,y-10.0,2.0,2.0,0x83C5FF,oldAlpha)
        }
    }
    p.r(42.0,124.0,28.0,28.0,0xEBEDE4,7.0,compactAlpha)
    p.line(50.0,133.0,62.0,133.0,0x6F766B,1.2,compactAlpha)
    p.line(50.0,139.0,59.0,139.0,0x6F766B,1.2,compactAlpha)
    p.text("Next appointment",83.0,120.0,10.0,0x90998D,"regular",0.0,compactAlpha)
    p.text("Studio check-in · 10:30",83.0,137.0,14.0,0xF2F3EB,"regular",0.0,compactAlpha)
    p.line(330.0,144.0,340.0,134.0,0xE4E6DF,1.5,compactAlpha)
    p.line(332.0,134.0,340.0,134.0,0xE4E6DF,1.5,compactAlpha)
    p.line(340.0,134.0,340.0,142.0,0xE4E6DF,1.5,compactAlpha)
    p.restore()
    val titles = listOf("Studio check-in","Color notes","The season ahead","Wellness review","A quieter morning","Movement session")
    val subtitles = listOf("Action needed","Upcoming","Your personal roadmap","Completed","Completed","Completed")
    val months = listOf("Mar","Mar","Jan","Jan","Dec","Nov")
    val days = listOf("22","12","08","08","18","29")
    val inks = listOf(0xFFAE23,0xEAEDE5,0xFF8A3B,0xE9EDE5,0xD38A54,0xE4E6DE)
    p.save()
    p.clip(0.0,180.0-12.0*fold,393.0,640.0+12.0*fold)
    for (idx in 0 until 6) {
        val i = idx.toDouble()
        val follow = seg(t,1.1+0.035*i,2.0+0.035*i)*(1.0-seg(t,3.6+0.035*i,4.6+0.035*i))
        val y = 469.0+91.0*i-280.0*follow
        p.r(24.0,y+2.0,345.0,81.0,0xD9DBD5,17.0,0.09)
        p.grad(24.0,y,345.0,81.0,0xF6F7F4,0xF8F9F6,0xF5F6F4,17.0)
        p.text(months[idx],40.0,y+14.0,12.0,0x969D92)
        p.text(days[idx],40.0,y+33.0,23.0,0x747D70)
        p.line(84.0,y+16.0,84.0,y+64.0,0xE8EBE4,1.0)
        p.r(100.0,y+15.0,49.0,49.0,inks[idx],9.0)
        if (idx == 0) {
            p.save()
            p.translate(100.0,y+15.0)
            p.path("M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z",0x251C15)
            p.restore()
        }
        if (idx == 1 || idx == 3) {
            for (jdx in 0 until 3) {
                val j = jdx.toDouble()
                p.r(110.0+10.0*j,y+26.0,3.0,27.0,0xC4C9BC,1.0)
                p.r(109.0+10.0*j,y+21.0,5.0,8.0,pick(jdx == 0,0xA4CE54,pick(jdx == 1,0xD57AC5,0x89BCEA)),1.0)
            }
        }
        if (idx == 2) {
            for (jdx in 0 until 4) {
                val j = jdx.toDouble()
                p.e(111.0+24.0*(jdx % 2).toDouble(),y+28.0+23.0*floor(j/2.0),10.0,10.0,0xFFD755,0.5)
            }
            p.line(103.0,y+39.0,145.0,y+39.0,0xFFE17F,1.2,0.65)
        }
        if (idx > 3) {
            p.e(124.0,y+31.0,10.0,11.0,0x572E21)
            p.r(111.0,y+42.0,26.0,22.0,0x674130,9.0)
        }
        p.text(titles[idx],162.0,y+21.0,14.0,0x373D34)
        p.text(subtitles[idx],162.0,y+44.0,12.0,0x9CA495)
        if (idx < 2) {
            p.e(337.0,y+41.0,15.0,15.0,0xEFF1EA)
            p.r(332.0,y+39.0,10.0,9.0,0xCAD0C3,3.0)
            p.line(334.0,y+38.0,334.0,y+34.0,0xAFBAA7,1.0)
            p.line(340.0,y+38.0,340.0,y+34.0,0xAFBAA7,1.0)
            p.line(334.0,y+34.0,340.0,y+34.0,0xAFBAA7,1.0)
        }
        if (idx == 2) { p.text("›",338.0,y+23.0,29.0,0x333B2F,"regular",1.0) }
        if (idx > 2) {
            p.e(337.0,y+40.0,11.0,11.0,0xA2E8CB)
            p.line(332.0,y+40.0,336.0,y+44.0,0xFAFFF7,1.8)
            p.line(336.0,y+44.0,342.0,y+36.0,0xFAFFF7,1.8)
        }
    }
    p.restore()
    p.grad(0.0,808.0,393.0,44.0,0xFCFCFA,0xFCFCFA,0xFCFCFA,0.0,false,0.95)
    p.save()
    p.translate(196.5,0.0)
    p.scale(160.0/147.0,1.0)
    p.translate(-196.5,0.0)
    p.r(123.0,762.0,147.0,50.0,0x070C08,26.0)
    p.e(149.0,787.0,18.0,18.0,0x2E372E)
    p.path("M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z",0xF2F7EC)
    for (idx in 0 until 3) {
        val i = idx.toDouble()
        p.r(180.0+4.0*i,787.0-3.0*i,2.5,8.0+3.0*i,0xA2ADA0,1.0)
        p.line(219.0+3.0*i,782.0+3.0*i,225.0+3.0*i,782.0+3.0*i,0x9EAA9C,1.4)
        p.e(253.0,782.0+5.0*i,1.2,1.2,0x9EAA9C)
    }
    p.restore()
}
