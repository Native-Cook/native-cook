
package com.ngocdevv.nativecook.blocks.playground.chromesmilecrane

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
fun ChromeSmileCrane(autoPlay: Boolean=false, previewTime: Double?=null) {
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
    var point by remember { mutableStateOf(Offset(260f,426.0f)) }
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
    Canvas(Modifier.fillMaxSize().background(Color(0xFF08ACCB))
        .semantics { contentDescription=previewTime?.let { "Chrome Smile Crane preview "+(it*1000).roundToInt() } ?: "Chrome Smile Crane";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=3.8;lastTouch=clock;true }) }
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
        drawScene(ChromeSmileCranePainter(canvas),phase(),260+(point.x-260)*strength,426.0+(point.y-426.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class ChromeSmileCranePainter(val c: android.graphics.Canvas) {
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
        val paths=mapOf<String,android.graphics.Path>("M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z" to android.graphics.Path().also { p -> p.moveTo(-12f,11f);p.quadTo(0f,25f,13f,11f);p.quadTo(1f,19f,-12f,11f);p.close() },
        "M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z" to android.graphics.Path().also { p -> p.moveTo(-12f,12f);p.quadTo(0f,25f,13f,11f);p.quadTo(1f,19f,-12f,12f);p.close() },
        "M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z" to android.graphics.Path().also { p -> p.moveTo(-15f,4f);p.lineTo(-31f,31f);p.lineTo(-25f,47f);p.lineTo(-19f,34f);p.lineTo(-5f,10f);p.close() },
        "M -15 5 L -27 31" to android.graphics.Path().also { p -> p.moveTo(-15f,5f);p.lineTo(-27f,31f) },
        "M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z" to android.graphics.Path().also { p -> p.moveTo(-15f,5f);p.lineTo(-27f,31f);p.lineTo(-23f,38f);p.lineTo(-18f,31f);p.lineTo(-7f,10f);p.close() },
        "M -15 5 L -27 31 L -24 32 L -11 7 Z" to android.graphics.Path().also { p -> p.moveTo(-15f,5f);p.lineTo(-27f,31f);p.lineTo(-24f,32f);p.lineTo(-11f,7f);p.close() },
        "M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z" to android.graphics.Path().also { p -> p.moveTo(-5f,5f);p.lineTo(-7f,32f);p.lineTo(-1f,41f);p.lineTo(4f,30f);p.lineTo(6f,5f);p.close() },
        "M -5 5 L -7 32 L -3 34 L 0 5 Z" to android.graphics.Path().also { p -> p.moveTo(-5f,5f);p.lineTo(-7f,32f);p.lineTo(-3f,34f);p.lineTo(0f,5f);p.close() },
        "M 124 654 L 136 664 L 136 730 L 124 743 Z" to android.graphics.Path().also { p -> p.moveTo(124f,654f);p.lineTo(136f,664f);p.lineTo(136f,730f);p.lineTo(124f,743f);p.close() },
        "M 136 730 L 256 730 L 270 743 L 124 743 Z" to android.graphics.Path().also { p -> p.moveTo(136f,730f);p.lineTo(256f,730f);p.lineTo(270f,743f);p.lineTo(124f,743f);p.close() },
        "M 270 654 L 256 664 L 256 730 L 270 743 Z" to android.graphics.Path().also { p -> p.moveTo(270f,654f);p.lineTo(256f,664f);p.lineTo(256f,730f);p.lineTo(270f,743f);p.close() },
        "M 296 304 L 307 307 L 307 496 L 301 516 Z" to android.graphics.Path().also { p -> p.moveTo(296f,304f);p.lineTo(307f,307f);p.lineTo(307f,496f);p.lineTo(301f,516f);p.close() },
        "M 338 262 L 326 277 L 321 610 L 333 606 Z" to android.graphics.Path().also { p -> p.moveTo(338f,262f);p.lineTo(326f,277f);p.lineTo(321f,610f);p.lineTo(333f,606f);p.close() },
        "M 55 262 L 67 277 L 72 610 L 61 606 Z" to android.graphics.Path().also { p -> p.moveTo(55f,262f);p.lineTo(67f,277f);p.lineTo(72f,610f);p.lineTo(61f,606f);p.close() },
        "M 58 558 L 333 558 L 351 585 L 42 585 Z" to android.graphics.Path().also { p -> p.moveTo(58f,558f);p.lineTo(333f,558f);p.lineTo(351f,585f);p.lineTo(42f,585f);p.close() },
        "M 63 562 L 329 562 L 341 582 L 52 582 Z" to android.graphics.Path().also { p -> p.moveTo(63f,562f);p.lineTo(329f,562f);p.lineTo(341f,582f);p.lineTo(52f,582f);p.close() },
        "M 75 304 L 317 304 L 303 316 L 81 311 Z" to android.graphics.Path().also { p -> p.moveTo(75f,304f);p.lineTo(317f,304f);p.lineTo(303f,316f);p.lineTo(81f,311f);p.close() },
        "M 89 299 L 106 299 L 86 432 L 86 365 Z" to android.graphics.Path().also { p -> p.moveTo(89f,299f);p.lineTo(106f,299f);p.lineTo(86f,432f);p.lineTo(86f,365f);p.close() })
    }
}

private fun drawScene(p:ChromeSmileCranePainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    val descend = seg(t,2.0,3.0)*(1.0-seg(t,3.4,4.5))
    val grip = seg(t,3.0,3.4)*(1.0-seg(t,5.4,5.8))
    var clawX = 260.0-132.0*seg(t,1.0,2.0)+132.0*seg(t,4.5,5.4)
    if (touching) { clawX = min(287.0,max(105.0,tx)) }
    val clawY = 337.0+149.0*descend
    var ballX = 128.0
    var ballY = 510.0
    var ballAlpha = 1.0
    if (t >= 3.0 && t < 5.4) { ballX=128.0+(clawX-128.0)*seg(t,3.0,3.15);ballY=510.0+(clawY+32.0-510.0)*seg(t,3.0,3.15) }
    if (t >= 5.4 && t < 6.2) {
        val fall = min(1.0,max(0.0,(t-5.4)/0.8))
        ballX=260.0-63.0*fall
        ballY=369.0+335.0*fall*fall
    }
    if (t >= 6.2 && t < 7.0) { ballX=197.0;ballY=704.0;ballAlpha=1.0-seg(t,6.3,6.8) }
    if (t >= 7.0) { ballAlpha=seg(t,7.0,7.4) }
    p.grad(0.0,0.0,393.0,852.0,0x04BADD,0x1888AD,0x126B8B)
    p.radial(192.0,270.0,410.0,0x1BD7E3,0x137D9E,0.42)
    // The narrow upper case tapers into the wide, forward-projecting deck.
    p.grad(53.0,222.0,287.0,402.0,0x47515C,0xCCD0C7,0x707D84,22.0,true)
    p.path("M 55 262 L 67 277 L 72 610 L 61 606 Z",0x293B48)
    p.path("M 338 262 L 326 277 L 321 610 L 333 606 Z",0x273746)
    p.grad(66.0,297.0,261.0,314.0,0xA1A4A0,0xC6C8C1,0xA0AAA8,3.0,true)
    p.save()
    p.translate(196.5,68.0)
    p.scale(0.92,0.85)
    p.translate(-196.5,0.0)
    p.grad(47.0,187.0,299.0,91.0,0x778B95,0x354452,0x152C43,20.0)
    p.r(56.0,196.0,281.0,70.0,0xC6CFCE,13.0)
    p.r(59.0,199.0,275.0,63.0,0x13233E,11.0)
    p.grad(63.0,203.0,267.0,53.0,0x07122D,0x133C88,0x0A1739,8.0)
    p.radial(197.0,229.0,148.0,0x2D73DC,0x172F68,0.6)
    val letters = listOf("S","M","I","L","E")
    for (idx in 0 until 5) {
        val i = idx.toDouble()
        p.save()
        p.blur(3.0)
        p.text(letters[idx],112.0+42.0*i,207.0,32.0,0x6BAFFF,"heavy",1.0)
        p.restore()
        p.blur(0.0)
        p.text(letters[idx],112.0+42.0*i,207.0,32.0,0xE8FFF8,"heavy",1.0)
    }
    p.restore()
    // Reflected smiles live behind a shallow upper glass band, as in the source.
    p.grad(69.0,298.0,255.0,49.0,0x9FA5A5,0xD3D5CE,0x969F9E,3.0)
    p.save()
    p.clip(74.0,304.0,245.0,34.0,4.0)
    p.grad(74.0,304.0,245.0,34.0,0x737C81,0xADB3AE,0x696E76,4.0)
    p.blur(2.0)
    for (idx in 0 until 3) {
        val i=idx.toDouble()
        p.e(128.0+65.0*i,326.0,25.0,12.0,0xE7C64B,0.32)
        p.e(120.0+65.0*i,324.0,2.0,3.0,0x7E6248,0.27)
        p.e(134.0+65.0*i,324.0,2.0,3.0,0x7E6248,0.27)
    }
    p.restore()
    p.blur(0.0)
    p.path("M 75 304 L 317 304 L 303 316 L 81 311 Z",0xF5F2DF,0.0,0.14)
    p.line(70.0,347.0,322.0,347.0,0xF8F5E4,1.3,0.7)
    p.save()
    p.translate(196.5,119.0)
    p.scale(0.92,0.86)
    p.translate(-196.5,0.0)
    p.r(73.0,287.0,247.0,256.0,0x546B7B,35.0)
    p.r(76.0,289.0,241.0,251.0,0xECF0DF,32.0)
    p.r(79.0,292.0,235.0,245.0,0x7E96AE,30.0)
    p.r(82.0,295.0,229.0,239.0,0x0B2697,28.0)
    p.save()
    p.clip(83.0,296.0,227.0,237.0,27.0)
    p.grad(83.0,296.0,227.0,237.0,0x1247D2,0x1628A3,0x121F87)
    p.radial(194.0,318.0,158.0,0x2B8BF4,0x182B9B,0.85)
    p.grad(83.0,513.0,227.0,20.0,0x2C3CB7,0x3151CC,0x6383E3)
    val xs = listOf(128,244,203,285,166,221,262)
    val ys = listOf(510,457,474,490,495,514,525)
    val radii = listOf(31,34,33,32,31,34,33)
    for (idx in 0 until 7) {
        val index = pick(idx == 6,0,idx+1)
        val i = index.toDouble()
        val radius = (radii[index]).toDouble()
        var bx = (xs[index]).toDouble()
        var by = (ys[index]).toDouble()
        var alpha = 1.0
        if (index == 0) { bx=ballX;by=ballY;alpha=ballAlpha }
        if (index > 0) { by=by+0.6*sin(6.283185307179586*t/8.0+i)*seg(t,0.8,1.1)*(1.0-seg(t,7.0,7.4)) }
        p.e(bx+3.0,by+6.0,radius+1.0,radius,0x070F4B,0.22*alpha)
        p.save()
        p.clip(bx-radius,by-radius,radius*2.0,radius*2.0,radius)
        p.grad(bx-radius,by-radius,radius*2.0,radius*2.0,0xFFE96D,0xF8C82A,0xAD7414,0.0,true,alpha)
        p.radial(bx-radius*0.32,by-radius*0.48,radius*1.35,0xFFFFAE,0xFFD332,0.84*alpha)
        p.e(bx-radius*0.4,by-radius*0.64,radius*0.16,radius*0.07,0xFFFBD2,0.5*alpha)
        if (index == 0) {
            p.r(bx-24.0,by-8.0,21.0,14.0,0x27303B,5.0,alpha)
            p.r(bx+3.0,by-8.0,21.0,14.0,0x27303B,5.0,alpha)
            p.line(bx-24.0,by-9.0,bx+24.0,by-9.0,0x1A2230,2.0,alpha)
            p.line(bx-3.0,by-4.0,bx+3.0,by-4.0,0x1A2230,2.0,alpha)
            p.line(bx-21.0,by-5.0,bx-13.0,by-7.0,0x8496A4,1.5,alpha*0.65)
            p.line(bx+6.0,by-5.0,bx+14.0,by-7.0,0x8496A4,1.5,alpha*0.65)
            p.save()
            p.translate(bx,by)
            p.path("M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z",0x673E1E,0.0,alpha)
            p.restore()
        }
        if (index > 0 && index < 3) {
            p.line(bx-15.0,by-5.0,bx-8.0,by-7.0,0x86661C,2.0,alpha)
            p.line(bx+8.0,by-7.0,bx+15.0,by-5.0,0x86661C,2.0,alpha)
            p.e(bx-18.0,by+5.0,6.0,3.0,0xF8A160,0.6*alpha)
            p.e(bx+18.0,by+5.0,6.0,3.0,0xF8A160,0.6*alpha)
            p.e(bx,by+8.0,8.0,6.0,0xA46B20,alpha)
            p.e(bx,by+5.0,8.0,4.0,0xFFDD53,alpha)
        }
        if (index >= 3.0 && index < 6) {
            p.e(bx-11.0,by-5.0,3.0,5.0,0x5C481F,alpha)
            p.e(bx+11.0,by-5.0,3.0,5.0,0x5C481F,alpha)
            p.e(bx,by+10.0,14.0,9.0,0x8E4E22,alpha)
            p.e(bx,by+6.0,12.0,4.0,0xFFFADE,alpha)
            p.e(bx,by+15.0,6.0,3.0,0xF19263,alpha)
        }
        if (index >= 6.0) {
            p.e(bx-11.0,by-5.0,3.0,4.0,0x604624,alpha)
            p.line(bx+7.0,by-5.0,bx+15.0,by-5.0,0x604624,2.0,alpha)
            p.e(bx,by+10.0,9.0,5.0,0x9E6227,alpha)
        }
        p.restore()
    }
    // A twisted cable with bright and dark facets; the fingers close around the ball.
    p.line(clawX,301.0,clawX,clawY-9.0,0x1D3766,5.0,0.55)
    for (idx in 0 until 15) {
        val i = idx.toDouble()
        val cy = 304.0+(clawY-317.0)*i/14.0
        p.line(clawX-3.0,cy,clawX+3.0,cy+4.0,0xD4DFD4,1.6)
        p.line(clawX+3.0,cy+4.0,clawX-3.0,cy+8.0,0x6E8DAD,1.2)
    }
    p.save()
    p.translate(clawX,clawY)
    p.grad(-20.0,-10.0,40.0,17.0,0x526D81,0xF3F4DD,0x738B9C,2.0,true)
    p.line(-20.0,-10.0,20.0,-10.0,0xF8F9E8,1.2)
    for (idx in 0 until 2) {
        p.save()
        p.scale(pick(idx == 0,1.0,-1.0),1.0)
        p.rotate(22.0*(1.0-grip))
        p.path("M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z",0x526D81)
        p.path("M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z",0xD2D9D0)
        p.path("M -15 5 L -27 31 L -24 32 L -11 7 Z",0xF5F5DC)
        p.path("M -15 5 L -27 31",0xFCFAE7,0.65,0.75)
        p.restore()
    }
    p.path("M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z",0xC7D1D0)
    p.path("M -5 5 L -7 32 L -3 34 L 0 5 Z",0xF6F4DB)
    p.restore()
    p.path("M 89 299 L 106 299 L 86 432 L 86 365 Z",0xD3F7FF,0.0,0.12)
    p.path("M 296 304 L 307 307 L 307 496 L 301 516 Z",0xA7D2FF,0.0,0.08)
    p.restore()
    p.line(87.0,532.0,307.0,532.0,0xDFE7DA,1.1,0.8)
    p.restore()
    // The base continues into the lower crop; the prize window remains fully visible.
    p.save()
    p.translate(196.5,-33.755)
    p.scale(1.08,1.145)
    p.translate(-196.5,0.0)
    p.grad(49.0,619.0,295.0,155.0,0x86979F,0xC6CFCA,0x9BA8A9,2.0,true)
    p.line(56.0,620.0,56.0,771.0,0xD7E1D8,1.0,0.5)
    p.grad(62.0,625.0,24.0,140.0,0xB6C5C4,0xDAE4DA,0xADBEBF,1.0,true,0.28)
    p.grad(315.0,625.0,14.0,140.0,0x667C8B,0x91A4AB,0xA3B3B6,1.0,true,0.28)
    p.line(337.0,620.0,337.0,771.0,0x344E61,1.0,0.6)
    p.r(115.0,646.0,164.0,105.0,0x597B8F,8.0)
    p.r(119.0,649.0,156.0,99.0,0xC8D9D6,6.0)
    p.r(123.0,653.0,148.0,91.0,0x0C286B,4.0)
    p.save()
    p.clip(124.0,654.0,146.0,89.0,3.0)
    p.grad(124.0,654.0,146.0,89.0,0x1238B5,0x244DCF,0x5F84D4)
    p.radial(202.0,686.0,81.0,0x6B9DF2,0x122C8B,0.5)
    p.path("M 124 654 L 136 664 L 136 730 L 124 743 Z",0x072777)
    p.path("M 270 654 L 256 664 L 256 730 L 270 743 Z",0x204AB6)
    p.path("M 136 730 L 256 730 L 270 743 L 124 743 Z",0x91AED9)
    if (ballY > 630.0) {
        p.e(ballX+3.0,ballY+25.0,28.0,5.0,0x071B5F,0.35*ballAlpha)
        p.e(ballX,ballY,29.0,29.0,0xE8B81B,ballAlpha)
        p.save()
        p.clip(ballX-29.0,ballY-29.0,58.0,58.0,29.0)
        p.radial(ballX-9.0,ballY-14.0,44.0,0xFFF58C,0xE9BE17,0.9*ballAlpha)
        p.r(ballX-23.0,ballY-7.0,20.0,13.0,0x29303C,5.0,ballAlpha)
        p.r(ballX+3.0,ballY-7.0,20.0,13.0,0x29303C,5.0,ballAlpha)
        p.line(ballX-23.0,ballY-8.0,ballX+23.0,ballY-8.0,0x202939,1.8,ballAlpha)
        p.line(ballX-3.0,ballY-3.0,ballX+3.0,ballY-3.0,0x202939,2.0,ballAlpha)
        p.save()
        p.translate(ballX,ballY)
        p.path("M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z",0x693F22,0.0,ballAlpha)
        p.restore()
        p.restore()
    }
    p.restore()
    p.line(122.0,747.0,273.0,747.0,0xEBF0DB,1.5,0.8)
    p.restore()
    p.save()
    p.translate(196.5,123.0)
    p.scale(1.045,0.9)
    p.translate(-196.5,0.0)
    p.path("M 58 558 L 333 558 L 351 585 L 42 585 Z",0xD8DFD9)
    p.path("M 63 562 L 329 562 L 341 582 L 52 582 Z",0x1B3E99)
    p.grad(43.0,584.0,307.0,35.0,0xAEBFC3,0x7F929C,0xD6E2DF,3.0,true)
    p.line(46.0,586.0,346.0,586.0,0xF5F8ED,1.1)
    for (idx in 0 until 24) {
        val i = idx.toDouble()
        p.line(59.0,625.0+6.0*i,332.0,625.0+6.0*i,0xFFFFFF,0.45,0.035)
    }
    for (idx in 0 until 2) {
        val i = idx.toDouble()
        val press = seg(t,2.8,3.0)*(1.0-seg(t,3.2,3.45))
        p.e(91.0+45.0*i,574.0,20.0,6.5,0x17315A,0.7)
        p.e(91.0+45.0*i,571.0+press,18.0,6.0,0x465765)
        p.e(91.0+45.0*i,569.0+press*2.0,17.0,5.0,0xD9DDD4)
        p.e(89.0+45.0*i,568.0+press*2.0,12.0,2.3,0xFAFBEC,0.9)
    }
    val lever = (clawX-196.5)/75.0
    p.e(281.0,575.0,25.0,8.0,0x0F2A52,0.45)
    p.e(281.0,573.0,22.0,8.0,0x6C899E)
    p.e(281.0,571.0,18.0,6.0,0xD7E0D2)
    p.line(281.0,568.0,281.0+lever*8.0,548.0,0x3A4E5B,10.0)
    p.line(279.0,567.0,279.0+lever*8.0,548.0,0xD3E1D6,3.0)
    p.e(281.0+lever*8.0,542.0,10.0,11.0,0x202C33)
    p.radial(278.0+lever*8.0,537.0,12.0,0x7C8984,0x1C2D36,0.5)
    p.restore()
    p.grain(0.0,0.0,393.0,852.0,0.035)
}
