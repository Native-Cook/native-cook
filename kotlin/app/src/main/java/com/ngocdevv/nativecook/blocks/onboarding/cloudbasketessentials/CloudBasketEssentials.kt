
package com.ngocdevv.nativecook.blocks.onboarding.cloudbasketessentials

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

private const val LOOP = 10.0

@Composable
fun CloudBasketEssentials(autoPlay: Boolean=false, previewTime: Double?=null) {
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
        if(reduced) return 7.9
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
    Canvas(Modifier.fillMaxSize().background(Color(0xFF89ACB0))
        .semantics { contentDescription=previewTime?.let { "Cloud Basket Essentials preview "+(it*1000).roundToInt() } ?: "Cloud Basket Essentials";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=7.9;lastTouch=clock;true }) }
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
                if(travel<5) manual=7.9
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
        drawScene(CloudBasketEssentialsPainter(canvas),phase(),196.5+(point.x-196.5)*strength,426.0+(point.y-426.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class CloudBasketEssentialsPainter(val c: android.graphics.Canvas) {
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

    fun grain(x:Double,y:Double,w:Double,h:Double,alpha:Double) {
        reset(0xFFFFFF,alpha)
        paint.isFilterBitmap=true
        c.drawBitmap(grainBitmap,null,android.graphics.RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),paint)
    }

    fun path(key: String,hex: Int,width: Double=0.0,alpha: Double=1.0) { reset(hex,alpha,width);paths[key]?.let { c.drawPath(it,paint) } }
    fun clipPath(key: String) { paths[key]?.let { c.clipPath(it) } }
    companion object {

        val grainBitmap=run {
            var seed=41389L
            val pixels=IntArray(192*384) {
                seed=(seed*1664525L+1013904223L) and 0xffffffffL
                val shade=96+((seed ushr 24).toInt()%80)
                android.graphics.Color.rgb(shade,shade,shade)
            }
            android.graphics.Bitmap.createBitmap(pixels,192,384,android.graphics.Bitmap.Config.ARGB_8888)
        }

        val fonts=mapOf("regular" to android.graphics.Typeface.create("sans-serif",0),"bold" to android.graphics.Typeface.create("sans-serif",1),"heavy" to android.graphics.Typeface.create("sans-serif-black",0),"condensed" to android.graphics.Typeface.create("sans-serif-condensed",1),"mono" to android.graphics.Typeface.create("monospace",0),"serif" to android.graphics.Typeface.create("serif",1),"italic" to android.graphics.Typeface.create("serif",3))
        val paths=mapOf<String,android.graphics.Path>("M -107 -1 Q 0 12 107 -1" to android.graphics.Path().also { p -> p.moveTo(-107f,-1f);p.quadTo(0f,12f,107f,-1f) },
        "M -107 0 L -84 103 L 84 103 L 107 0" to android.graphics.Path().also { p -> p.moveTo(-107f,0f);p.lineTo(-84f,103f);p.lineTo(84f,103f);p.lineTo(107f,0f) },
        "M -107 0 L 107 0 L 84 103 L -84 103 Z" to android.graphics.Path().also { p -> p.moveTo(-107f,0f);p.lineTo(107f,0f);p.lineTo(84f,103f);p.lineTo(-84f,103f);p.close() },
        "M -107 0 Q 0 -16 107 0" to android.graphics.Path().also { p -> p.moveTo(-107f,0f);p.quadTo(0f,-16f,107f,0f) },
        "M -107 0 Q 0 13 107 0" to android.graphics.Path().also { p -> p.moveTo(-107f,0f);p.quadTo(0f,13f,107f,0f) },
        "M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z" to android.graphics.Path().also { p -> p.moveTo(-19f,-14f);p.cubicTo(-25f,-21f,-30f,-10f,-18f,-4f);p.cubicTo(-7f,-10f,-13f,-21f,-19f,-14f);p.close() },
        "M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z" to android.graphics.Path().also { p -> p.moveTo(-21f,-10f);p.cubicTo(-30f,-29f,-17f,-37f,-2f,-29f);p.cubicTo(15f,-38f,29f,-26f,19f,-10f);p.lineTo(18f,26f);p.lineTo(-19f,26f);p.close() },
        "M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z" to android.graphics.Path().also { p -> p.moveTo(-23f,-42f);p.lineTo(-5f,-42f);p.lineTo(-5f,5f);p.quadTo(9f,7f,9f,18f);p.quadTo(8f,30f,-6f,31f);p.lineTo(-30f,27f);p.quadTo(-36f,21f,-29f,12f);p.lineTo(-23f,5f);p.close() },
        "M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z" to android.graphics.Path().also { p -> p.moveTo(-25f,-11f);p.cubicTo(-38f,-33f,-19f,-46f,-2f,-36f);p.cubicTo(18f,-46f,36f,-27f,24f,-11f);p.lineTo(23f,32f);p.lineTo(-24f,32f);p.close() },
        "M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15" to android.graphics.Path().also { p -> p.moveTo(-27f,15f);p.lineTo(-27f,-14f);p.cubicTo(-27f,-53f,28f,-53f,28f,-14f);p.lineTo(28f,15f) },
        "M -28 -13 C -29 -48 27 -48 28 -13" to android.graphics.Path().also { p -> p.moveTo(-28f,-13f);p.cubicTo(-29f,-48f,27f,-48f,28f,-13f) },
        "M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z" to android.graphics.Path().also { p -> p.moveTo(-5f,-38f);p.lineTo(14f,-38f);p.lineTo(14f,9f);p.quadTo(31f,11f,31f,22f);p.quadTo(29f,34f,15f,34f);p.lineTo(-9f,29f);p.quadTo(-15f,22f,-8f,13f);p.lineTo(-5f,9f);p.close() },
        "M 38 -45 L 46 -36 L 46 10 L 38 15 Z" to android.graphics.Path().also { p -> p.moveTo(38f,-45f);p.lineTo(46f,-36f);p.lineTo(46f,10f);p.lineTo(38f,15f);p.close() },
        "M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z" to android.graphics.Path().also { p -> p.moveTo(4f,-16f);p.lineTo(6f,-10f);p.lineTo(13f,-10f);p.lineTo(8f,-6f);p.lineTo(10f,1f);p.lineTo(4f,-3f);p.lineTo(-2f,1f);p.lineTo(0f,-6f);p.lineTo(-5f,-10f);p.lineTo(2f,-10f);p.close() },
        "M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z" to android.graphics.Path().also { p -> p.moveTo(65.028f,217.280f);p.lineTo(64.084f,222.000f);p.lineTo(47.092f,222.000f);p.lineTo(63.140f,146.952f);p.lineTo(80.604f,144.592f);p.lineTo(66.444f,210.790f);p.quadTo(69.748f,213.150f,73.170f,213.150f);p.quadTo(79.896f,213.150f,85.029f,205.362f);p.quadTo(90.162f,197.574f,92.994f,185.538f);p.quadTo(95.826f,173.502f,95.826f,161.230f);p.quadTo(95.826f,149.666f,91.283f,144.297f);p.quadTo(86.740f,138.928f,78.598f,138.928f);p.quadTo(69.748f,138.928f,62.727f,142.409f);p.quadTo(55.706f,145.890f,51.694f,152.380f);p.quadTo(47.682f,158.870f,47.682f,167.602f);p.quadTo(47.682f,171.968f,48.508f,173.679f);p.quadTo(49.334f,175.390f,49.334f,175.626f);p.quadTo(43.198f,175.626f,39.953f,173.030f);p.quadTo(36.708f,170.434f,36.708f,164.652f);p.quadTo(36.708f,156.628f,42.726f,149.430f);p.quadTo(48.744f,142.232f,58.833f,137.866f);p.quadTo(68.922f,133.500f,80.604f,133.500f);p.quadTo(96.770f,133.500f,104.440f,143.058f);p.quadTo(112.110f,152.616f,112.110f,167.484f);p.quadTo(112.110f,180.936f,107.567f,193.975f);p.quadTo(103.024f,207.014f,95.472f,215.274f);p.quadTo(87.920f,223.534f,79.424f,223.534f);p.quadTo(72.344f,223.534f,65.028f,217.280f);p.close() })
    }
}

private fun drawScene(p:CloudBasketEssentialsPainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    p.grad(0.0,0.0,393.0,852.0,0x86ADB0,0x8AB2B4,0xA8C2C2)
    // Scalloped cloud silhouettes sit over a softer diagonal body.
    for (idx in 0 until 7) {
        val i=idx.toDouble()
        val x = -65.0+i*79.0
        val y=435.0+i*39.0
        p.save()
        p.blur(13.0)
        p.e(x+4.0,y+54.0,110.0,82.0,0xD9E2DC,0.64)
        p.e(x+27.0,y+71.0,107.0,85.0,0xEEF0E6,0.74)
        p.restore()
        p.blur(0.0)
    }
    for (idx in 0 until 18) {
        val i=idx.toDouble()
        val x = -25.0+i*28.0
        val y=420.0+i*15.0+13.0*sin(i*2.1)
        val radius=24.0+7.0*sin(i*1.7+1.0)
        p.save()
        p.blur(5.0)
        p.e(x+7.0,y+14.0,radius*1.14,radius*1.1,0xBCCFCB,0.40)
        p.e(x,y,radius,radius,0xEDF0E5,0.83)
        p.e(x-5.0,y-5.0,radius*0.76,radius*0.70,0xF8F7EB,0.34)
        p.restore()
        p.blur(0.0)
    }
    for (idx in 0 until 5) {
        val i=idx.toDouble()
        p.save()
        p.blur(25.0)
        p.e(-53.0+124.0*i,747.0+21.0*sin(i*2.7),130.0,114.0,0xE5EBE3,0.42)
        p.restore()
        p.blur(0.0)
    }
    val pixelX = listOf(48.0, 52.2, 56.4, 60.6, 64.8, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 64.8, 78.3, 82.5, 86.7, 90.9, 95.1, 78.3, 78.3, 78.3, 82.5, 86.7, 90.9, 95.1, 95.1, 95.1, 78.3, 82.5, 86.7, 90.9, 95.1, 108.6, 112.8, 117.0, 121.2, 125.4, 108.6, 108.6, 108.6, 112.8, 117.0, 121.2, 125.4, 125.4, 125.4, 108.6, 112.8, 117.0, 121.2, 125.4, 138.9, 143.1, 147.3, 151.5, 155.7, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 155.7, 169.2, 186.0, 169.2, 173.4, 186.0, 169.2, 173.4, 186.0, 169.2, 177.6, 186.0, 169.2, 181.8, 186.0, 169.2, 181.8, 186.0, 169.2, 186.0, 199.5, 203.7, 207.9, 212.1, 216.3, 207.9, 207.9, 207.9, 207.9, 207.9, 207.9, 229.8, 234.0, 238.2, 242.4, 246.6, 238.2, 238.2, 238.2, 238.2, 238.2, 229.8, 234.0, 238.2, 242.4, 246.6, 264.3, 268.5, 272.7, 260.1, 276.9, 260.1, 276.9, 260.1, 264.3, 268.5, 272.7, 276.9, 260.1, 276.9, 260.1, 276.9, 260.1, 276.9, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 294.6, 298.8, 303.0, 307.2, 320.7, 324.9, 329.1, 333.3, 337.5, 320.7, 320.7, 320.7, 324.9, 329.1, 333.3, 337.5, 337.5, 337.5, 320.7, 324.9, 329.1, 333.3, 337.5)
    val pixelY = listOf(94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 98.8, 98.8, 98.8, 103.6, 103.6, 103.6, 108.4, 108.4, 108.4, 113.2, 113.2, 113.2, 118.0, 118.0, 118.0, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 98.8, 98.8, 103.6, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 113.2, 118.0, 118.0, 122.8, 122.8, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8)
    for (idx in 0 until 167) {
        p.r(pixelX[idx],pixelY[idx],3.8,4.2,0xF0A0CC)
    }
    // Lobster capital D as one cached outline keeps the decorative initial identical across ports.
    p.path("M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z",0xF0A0CC)
    p.fit("ESIGNERS",236.0,120.0,92.0,237.0,0xF0A0CC,"condensed")
    p.save()
    p.translate(107.0,135.0)
    p.rotate(-8.0)
    p.e(0.0,0.0,19.0,9.5,0xF4EFD6)
    p.text("for",0.0,-8.0,13.0,0x799C9D,"italic",1.0)
    p.restore()
    var basketX=196.5+29.0*sin(6.283185307179586*t/10.0+0.8)*seg(t,0.65,1.0)*(1.0-seg(t,8.4,9.2))
    if (touching) { basketX=min(257.0,max(136.0,tx)) }
    var shake=0.0
    for (idx in 0 until 6) {
        val age=t-(1.95+idx.toDouble()*1.15)
        if (age >= 0.0) { shake=shake+2.3*exp(-9.0*age)*sin(19.0*age) }
    }
    val basketY=651.0+shake
    // Back wire and handles sit behind the falling objects.
    p.e(basketX,764.0,109.0,9.0,0x506B71,0.13)
    p.e(basketX,757.0,87.0,3.0,0x4A5964,0.19)
    p.save()
    p.translate(basketX,basketY)
    p.scale(0.86,1.0)
    p.path("M -107 0 L 107 0 L 84 103 L -84 103 Z",0xD99CB7,2.0)
    p.line(-96.0,-7.0,-9.0,-1.0,0xA62F67,4.0)
    p.line(9.0,-1.0,96.0,-7.0,0xB92A61,4.0)
    p.line(-95.0,-8.0,-10.0,-2.0,0xF17CA7,1.1)
    p.line(10.0,-2.0,95.0,-8.0,0xF69EB9,1.1)
    p.path("M -107 0 Q 0 -16 107 0",0xF8CBDD,3.0)
    p.restore()
    val xs = listOf(201,160,239,192,221,258)
    val ys = listOf(694,704,708,717,712,704)
    val angles = listOf(0,-14,3,0,5,5)
    for (idx in 0 until 6) {
        val i=idx.toDouble()
        val begin=0.85+i*1.15
        val raw=min(1.0,max(0.0,(t-begin)/1.1))
        val landed=max(0.0,t-begin-1.1)
        val exit=seg(t,8.4+i*0.025,9.1+i*0.025)
        val alpha=seg(t,begin,begin+0.12)*(1.0-exit)
        val targetY=(ys[idx]).toDouble()
        val y=300.0+(targetY-300.0)*raw*raw-14.0*exp(-11.0*landed)*abs(sin(15.0*landed))-110.0*exit
        val x=(xs[idx]).toDouble()+(basketX-196.5)*raw*raw+18.0*sin(raw*3.141592653589793)*(pick(idx % 2 == 0,1.0,-1.0))
        p.save()
        p.translate(x,y)
        p.rotate((angles[idx]).toDouble()+(1.0-raw)*pick(idx % 2 == 0,-14.0,14.0)-13.0*exit+5.0*exp(-10.0*landed)*sin(17.0*landed))
        p.scale(0.9+0.1*raw,0.9+0.1*raw)
        p.e(4.0,34.0,35.0,6.0,0x587073,0.13*alpha)
        if (idx == 0) {
            p.grad(-38.0,-50.0,76.0,65.0,0xF0EAD1,0xD9D2B8,0xB9BAA5,6.0,false,alpha)
            p.path("M 38 -45 L 46 -36 L 46 10 L 38 15 Z",0xA0AA9B,0.0,alpha)
            p.r(-32.0,-44.0,62.0,47.0,0x898E80,5.0,alpha)
            p.grad(-29.0,-41.0,56.0,41.0,0x223B46,0x64858A,0x263E47,5.0,false,alpha)
            p.e(-10.0,-31.0,16.0,5.0,0x99B5AD,0.23*alpha)
            p.text("hello.",0.0,-28.0,14.0,0xF7F2D7,"italic",1.0,alpha)
            p.r(-22.0,15.0,44.0,9.0,0xB9BEAC,2.0,alpha)
            p.grad(-37.0,25.0,81.0,17.0,0xE5E4CE,0xD1D4C4,0xADB9AA,2.0,false,alpha)
            for (row in 0 until 3) {
                for (col in 0 until 11) {
        p.r(-31.0+col.toDouble()*6.5,28.0+row.toDouble()*3.0,5.0,2.0,0x798F87,0.4,0.65*alpha)
    }
            }
            p.e(29.0,8.0,1.5,1.5,0x79A65E,alpha)
        }
        if (idx == 1) {
            p.r(-28.0,-42.0,57.0,80.0,0xA8B0AF,2.0,alpha)
            p.grad(-31.0,-45.0,57.0,79.0,0x234FD6,0x1C41BA,0x142C91,3.0,true,alpha)
            p.line(-26.0,-41.0,-26.0,29.0,0x90A0EA,1.0,0.75*alpha)
            p.text("Thoughts",-23.0,-30.0,11.0,0xF4EAD5,"italic",0.0,alpha)
            p.text("& tiny",-21.0,-11.0,12.0,0xF4EAD5,"italic",0.0,alpha)
            p.text("plans",-20.0,5.0,14.0,0xF4EAD5,"italic",0.0,alpha)
            p.e(6.0,24.0,5.0,5.0,0xF6DC55,alpha)
        }
        if (idx == 2) {
            p.path("M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z",0xECEBD9,0.0,alpha)
            p.path("M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z",0x28A65E,0.0,alpha)
            p.line(-23.0,-31.0,-7.0,-31.0,0xD8D7C8,3.0,alpha)
            p.line(-5.0,-27.0,12.0,-27.0,0xEAEADB,4.0,alpha)
            p.path("M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z",0xD94E4C,0.0,alpha)
            p.path("M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z",0xE2D94D,0.0,alpha)
        }
        if (idx == 3) {
            p.e(19.0,0.0,15.0,19.0,0x2B57AD,alpha)
            p.e(19.0,0.0,9.0,12.0,0xEFEBD7,alpha)
            p.grad(-23.0,-22.0,44.0,48.0,0xE7EEDB,0xFFF4DA,0xCACBB5,6.0,false,alpha)
            for (col in 0 until 5) {
        p.r(-19.0+col.toDouble()*8.0,-19.0,3.5,40.0,0x244AAB,1.0,alpha)
    }
            p.e(-1.0,-21.0,22.0,7.0,0xE8B4A0,alpha)
            p.e(-1.0,-21.0,17.0,4.0,0x8B332E,alpha)
            p.e(-5.0,-23.0,10.0,1.5,0xC57958,alpha)
        }
        if (idx == 4) {
            p.path("M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z",0xAC602A,0.0,alpha)
            p.path("M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z",0xF1C46A,0.0,alpha)
            p.e(-4.0,-2.0,16.0,19.0,0xFFE1A1,0.8*alpha)
            for (col in 0 until 7) {
                val j=col.toDouble()
                p.e(-14.0+4.5*j,-12.0+23.0*abs(sin(j*4.3)),1.0,1.3,0xB37B36,0.55*alpha)
            }
            p.r(-10.0,-7.0,18.0,13.0,0xF9DC5C,2.0,alpha)
            p.line(-9.0,-6.0,6.0,-6.0,0xFFF1A4,1.0,alpha)
        }
        if (idx == 5) {
            p.path("M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0x731D35,12.0,alpha)
            p.path("M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0xB92B4B,6.0,alpha)
            p.path("M -28 -13 C -29 -48 27 -48 28 -13",0xE27587,1.5,0.8*alpha)
            p.e(-27.0,13.0,11.0,18.0,0xA82745,alpha)
            p.e(-27.0,13.0,7.0,12.0,0x5B112C,alpha)
            p.e(-29.0,9.0,4.0,7.0,0xE06478,0.6*alpha)
            p.e(27.0,13.0,11.0,18.0,0xA82745,alpha)
            p.e(27.0,13.0,7.0,12.0,0x5B112C,alpha)
            p.e(25.0,9.0,4.0,7.0,0xE06478,0.6*alpha)
            p.r(-30.0,0.0,5.0,28.0,0xF28B95,2.0,0.45*alpha)
        }
        p.restore()
    }
    // Front wires occlude the packed objects instead of sitting behind them.
    p.save()
    p.translate(basketX,basketY)
    p.scale(0.86,1.0)
    p.path("M -107 0 L -84 103 L 84 103 L 107 0",0xD99CB7,3.0)
    p.path("M -107 0 L -84 103 L 84 103 L 107 0",0xFBC9DD,1.1)
    for (idx in 0 until 12) {
        val x = -98.0+idx.toDouble()*17.8
        p.line(x+1.7,6.0,x*0.8+1.7,101.0,0x7C4169,3.0,0.13)
        p.line(x,4.0,x*0.8,99.0,0xE3A5C1,1.8)
        p.line(x-0.8,4.0,x*0.8-0.8,99.0,0xF9D5E0,0.7)
    }
    for (idx in 0 until 4) {
        val y=21.0+idx.toDouble()*24.0
        val w=107.0-y*0.225
        p.line(-w,y,w,y,0xE2A0BB,1.8)
        p.line(-w,y-1.0,w,y-1.0,0xFBD4E0,0.7)
    }
    p.path("M -107 0 Q 0 13 107 0",0xD692B2,3.0)
    p.path("M -107 -1 Q 0 12 107 -1",0xFFD8E6,1.0)
    p.restore()
    p.grain(0.0,0.0,393.0,852.0,0.08)
}
