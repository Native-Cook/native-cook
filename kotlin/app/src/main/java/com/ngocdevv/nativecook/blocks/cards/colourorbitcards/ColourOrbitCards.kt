
package com.ngocdevv.nativecook.blocks.cards.colourorbitcards

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
fun ColourOrbitCards(autoPlay: Boolean=false, previewTime: Double?=null) {
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
    Canvas(Modifier.fillMaxSize().background(Color(0xFFFCFBFD))
        .semantics { contentDescription=previewTime?.let { "Colour Orbit Cards preview "+(it*1000).roundToInt() } ?: "Colour Orbit Cards";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=2.5;lastTouch=clock;true }) }
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
                    val amount=delta.x/scale
                    manual=(origin+amount/90).coerceIn(0.0,LOOP-.001)
                    point=Offset((change.position.x/scale).toFloat(),(change.position.y/scale).toFloat())
                    val dt=max(1.0,(change.uptimeMillis-previousTime).toDouble())/1000
                    velocity=((change.position.x-previous.x)/scale/90/dt).coerceIn(-5.0,5.0)
                    previous=change.position;previousTime=change.uptimeMillis;lastTouch=clock
                    change.consume()
                } while(event.changes.any { it.pressed })
                if(travel<5) manual=2.5
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
        drawScene(ColourOrbitCardsPainter(canvas),phase(),196.5+(point.x-196.5)*strength,426.0+(point.y-426.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class ColourOrbitCardsPainter(val c: android.graphics.Canvas) {
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
        val paths=mapOf<String,android.graphics.Path>("M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z" to android.graphics.Path().also { p -> p.moveTo(-4f,84f);p.lineTo(-1f,93f);p.lineTo(9f,96f);p.lineTo(-1f,99f);p.lineTo(-4f,108f);p.lineTo(-7f,99f);p.lineTo(-17f,96f);p.lineTo(-7f,93f);p.close() },
        "M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z" to android.graphics.Path().also { p -> p.moveTo(-57f,-31f);p.lineTo(-70f,-43f);p.lineTo(-57f,-56f);p.lineTo(-45f,-44f);p.close() },
        "M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z" to android.graphics.Path().also { p -> p.moveTo(-63f,25f);p.lineTo(-38f,10f);p.lineTo(-18f,23f);p.lineTo(-23f,55f);p.lineTo(-54f,61f);p.lineTo(-70f,42f);p.close() },
        "M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z" to android.graphics.Path().also { p -> p.moveTo(-65f,-70f);p.cubicTo(-82f,-90f,-95f,-60f,-56f,-39f);p.cubicTo(-22f,-63f,-33f,-85f,-48f,-72f);p.cubicTo(-53f,-84f,-61f,-83f,-65f,-70f);p.close() },
        "M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z" to android.graphics.Path().also { p -> p.moveTo(-71f,82f);p.lineTo(-69f,64f);p.quadTo(-52f,54f,-36f,64f);p.lineTo(-32f,82f);p.lineTo(-43f,82f);p.lineTo(-48f,75f);p.lineTo(-57f,75f);p.lineTo(-61f,82f);p.close() },
        "M 18 8 L 53 -4 L 51 32 Z" to android.graphics.Path().also { p -> p.moveTo(18f,8f);p.lineTo(53f,-4f);p.lineTo(51f,32f);p.close() },
        "M 34 74 Q 41 79 48 74" to android.graphics.Path().also { p -> p.moveTo(34f,74f);p.quadTo(41f,79f,48f,74f) },
        "M 35 127 L 52 98 L 70 127 Z" to android.graphics.Path().also { p -> p.moveTo(35f,127f);p.lineTo(52f,98f);p.lineTo(70f,127f);p.close() },
        "M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z" to android.graphics.Path().also { p -> p.moveTo(45f,-103f);p.lineTo(54f,-96f);p.lineTo(70f,-100f);p.lineTo(75f,-94f);p.lineTo(52f,-86f);p.lineTo(36f,-91f);p.lineTo(39f,-96f);p.lineTo(49f,-93f);p.close() })
    }
}

private fun drawScene(p:ColourOrbitCardsPainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    val turn = 120.0*(seg(t,1.0,2.0)+seg(t,3.1,4.1)+seg(t,5.2,6.3))
    p.r(0.0,0.0,393.0,852.0,0xFCFBFD)
    p.e(196.5,508.0,130.0,8.0,0xBDBDC4,0.08)
    var order = mutableListOf(0,1,2)
    for (idx in 0 until 3) {
        for (jdx in 0 until 2) {
            val a = ((order[jdx]).toDouble()*120.0-turn)*3.141592653589793/180.0
            val b = ((order[jdx+1]).toDouble()*120.0-turn)*3.141592653589793/180.0
            if (cos(a) > cos(b)) {
                val hold = order[jdx]
                order[jdx] = order[jdx+1]
                order[jdx+1] = hold
            }
        }
    }
    for (idx in 0 until 3) {
        val card = order[idx]
        val theta = (card.toDouble()*120.0-turn)*3.141592653589793/180.0
        val depth = cos(theta)
        val scale = 0.72+0.28*(depth+1.0)/2.0
        val yaw = 0.5+0.5*abs(depth)
        val x = 196.5+143.0*sin(theta)
        val y = 335.0-13.0*depth
        p.save()
        p.translate(x,y)
        p.scale(scale*yaw,scale)
        p.skew(0.0,-0.12*sin(theta))
        p.rotate(2.5*sin(theta))
        p.e(4.0,152.0,80.0,9.0,0x6C7074,0.07)
        p.blur(1.8*max(0.0,1.0-depth))
        p.r(-85.0,-135.0,172.0,272.0,0x8A9493,12.0,0.7)
        p.grad(-84.0,-136.0,168.0,270.0,pick(card == 0,0xD6DEDC,pick(card == 1,0xF5F5F6,0xEEEFF0)),pick(card == 0,0xB9C7C5,pick(card == 1,0xFDFCFD,0xE3E5E6)),pick(card == 0,0xCAD3D0,pick(card == 1,0xF2F2F2,0xD8DCDD)),12.0)
        p.save()
        p.clip(-84.0,-136.0,168.0,270.0,12.0)
        if (card == 0) {
            p.text("h.",-70.0,-127.0,18.0,0x172224,"heavy")
            p.text("ONE",12.0,-129.0,24.0,0x202B2B,"heavy")
            p.save()
            p.translate(-68.0,104.0)
            p.rotate(-90.0)
            p.text("MORE GOOD",0.0,0.0,21.0,0x202B2B,"heavy")
            p.restore()
            p.save()
            p.translate(72.0,-86.0)
            p.rotate(90.0)
            p.text("DAYS",0.0,0.0,22.0,0x202B2B,"heavy")
            p.restore()
            for (jdx in 0 until 2) {
                val j = jdx.toDouble()
                val cx = -10.0+19.0*j
                val cy = -36.0+108.0*j
                p.e(cx-22.0,cy-28.0,12.0,13.0,0xEDB532)
                p.e(cx+22.0,cy-28.0,12.0,13.0,0xEDB532)
                p.e(cx,cy+3.0,38.0,36.0,0xC78A29,0.25)
                p.e(cx,cy,38.0,37.0,0xF9C840)
                p.e(cx,cy+17.0,29.0,19.0,0xDE9E20,0.20)
                p.e(cx-8.0,cy-11.0,24.0,26.0,0xFFE16D,0.30)
                p.e(cx-22.0,cy-28.0,6.0,8.0,0xFFE781,0.45)
                p.e(cx+22.0,cy-28.0,6.0,8.0,0xFFE781,0.45)
                p.radial(cx-12.0,cy-14.0,41.0,0xFFE783,0xFBC937,0.8)
                p.e(cx-11.0,cy-2.0,2.5,3.0,0x302720)
                p.e(cx+10.0,cy-2.0,2.5,3.0,0x302720)
                p.e(cx-19.0,cy+8.0,5.0,3.0,0xEF8A53,0.8)
                p.e(cx+18.0,cy+8.0,5.0,3.0,0xEF8A53,0.8)
                p.e(cx,cy+9.0,9.0,7.0,0xFFF3CA)
                p.e(cx,cy+6.0,3.0,2.1,0x4D3422)
                p.line(cx,cy+9.0,cx,cy+12.0,0x4D3422,1.1)
            }
        }
        if (card == 1) {
            p.text("h.",-71.0,-127.0,18.0,0x191D21,"heavy")
            p.save()
            p.translate(33.0,-95.0)
            p.rotate(24.0)
            p.r(-24.0,-12.0,65.0,27.0,0x3CEB69,2.0)
            p.text("PLAY",7.0,-5.0,10.0,0x122A17,"heavy",1.0)
            p.restore()
            p.path("M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z",0x4547DF)
            p.save()
            p.rotate(-17.0)
            p.r(-88.0,-26.0,176.0,31.0,0xFFDE11)
            p.restore()
            p.path("M 18 8 L 53 -4 L 51 32 Z",0x8F50D7)
            p.path("M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z",0xFE7724)
            p.e(41.0,67.0,26.0,26.0,0xFFE30D)
            p.e(34.0,63.0,1.5,2.0,0x342F1A)
            p.e(48.0,63.0,1.5,2.0,0x342F1A)
            p.path("M 34 74 Q 41 79 48 74",0x342F1A,1.2)
            p.save()
            p.translate(-47.0,98.0)
            p.rotate(-39.0)
            p.r(-38.0,-14.0,76.0,30.0,0x5153E8,3.0)
            p.text("GOOD STUFF",0.0,-6.0,9.0,0xE3E4FF,"heavy",1.0)
            p.restore()
            p.save()
            p.translate(72.0,116.0)
            p.rotate(14.0)
            p.r(-24.0,-24.0,48.0,48.0,0x25AB60,2.0)
            p.text("+",0.0,-17.0,29.0,0x0C6A37,"heavy",1.0)
            p.restore()
            p.e(-4.0,96.0,18.0,18.0,0xFBD519)
            p.path("M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z",0xFEF6A1)
        }
        if (card == 2) {
            p.text("h.",-72.0,-128.0,20.0,0xC8CECE,"heavy")
            p.text("travel",-73.0,-82.0,37.0,0xCBD1D2)
            p.text("love",-7.0,-33.0,39.0,0xCBD1D2)
            p.text("sports",-73.0,16.0,36.0,0xCBD1D2)
            p.text("game",-22.0,59.0,37.0,0xCBD1D2)
            p.text("hike",-72.0,100.0,34.0,0xCBD1D2)
            p.path("M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z",0xC9CFD0,2.2)
            p.path("M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z",0xC7CDCF,2.0)
            p.path("M 35 127 L 52 98 L 70 127 Z",0xBFC8C8,2.2)
            p.path("M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z",0xC6CDCF,2.4)
            p.line(-63.0,67.0,-63.0,73.0,0xC6CDCF,2.0)
            p.line(-66.0,70.0,-60.0,70.0,0xC6CDCF,2.0)
        }
        // The same contact chip sits above every printed surface.
        p.r(-13.0,-108.0,29.0,37.0,0x8A9192,5.0)
        p.grad(-11.5,-106.5,26.0,34.0,0xD0D3CE,0xAFB6B4,0xDDE0D8,4.0,true)
        p.line(-3.0,-106.0,-3.0,-97.0,0x7D8784,0.65)
        p.line(6.0,-106.0,6.0,-97.0,0x7D8784,0.65)
        p.r(-4.0,-97.0,11.0,15.0,0xB8BFBB,2.0)
        p.line(-11.0,-89.0,-4.0,-89.0,0x7D8784,0.65)
        p.line(7.0,-89.0,14.0,-89.0,0x7D8784,0.65)
        p.line(-3.0,-82.0,-3.0,-73.0,0x7D8784,0.65)
        p.line(6.0,-82.0,6.0,-73.0,0x7D8784,0.65)
        p.line(-81.0,-133.0,80.0,-133.0,0xFFFFFF,0.9,0.65)
        p.line(-82.0,-125.0,-82.0,125.0,0xFFFFFF,0.7,0.27+0.2*max(0.0,depth))
        p.line(82.0,-125.0,82.0,125.0,0x4A5552,0.8,0.2)
        p.restore()
        p.blur(0.0)
        p.restore()
    }
    p.text("Your everyday,",196.5,585.0,31.0,0x161719,"bold",1.0)
    p.text("with more play.",196.5,623.0,31.0,0x161719,"bold",1.0)
    p.text("Three little ways to make it yours.",196.5,681.0,14.0,0x939194,"regular",1.0)
    p.line(191.0,746.0,196.5,752.0,0xB1ADB3,1.3)
    p.line(196.5,752.0,202.0,746.0,0xB1ADB3,1.3)
}
