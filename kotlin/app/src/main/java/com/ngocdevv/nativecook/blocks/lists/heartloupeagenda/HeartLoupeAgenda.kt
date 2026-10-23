
package com.ngocdevv.nativecook.blocks.lists.heartloupeagenda

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

private const val LOOP = 9.0

@Composable
fun HeartLoupeAgenda(autoPlay: Boolean=false, previewTime: Double?=null) {
    val context=LocalContext.current
    val titleFont=remember(context) { android.graphics.Typeface.createFromAsset(context.assets,"heartloupeagenda/heartloupeagenda_lobster.ttf") }
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
    var point by remember { mutableStateOf(Offset(196.5f,420.0f)) }
    var velocity by remember { mutableDoubleStateOf(0.0) }
    fun phase():Double {
        if(previewTime!=null) return previewTime.mod(LOOP)
        if(reduced) return 4.5
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
    Canvas(Modifier.fillMaxSize().background(Color(0xFFC71D35))
        .semantics { contentDescription=previewTime?.let { "Heart Loupe Agenda preview "+(it*1000).roundToInt() } ?: "Heart Loupe Agenda";stateDescription="Interactive motion preview";customActions=listOf(CustomAccessibilityAction("Preview effect") { clock=System.nanoTime()/1_000_000_000.0;manual=4.5;lastTouch=clock;true }) }
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
                if(travel<5) manual=4.5
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
        drawScene(HeartLoupeAgendaPainter(canvas,titleFont),phase(),196.5+(point.x-196.5)*strength,420.0+(point.y-420.0)*strength,strength>0)
        canvas.restore()
    }
}

private fun smooth(value: Double): Double { val u=value.coerceIn(0.0,1.0);return u*u*u*(10+u*(-15+6*u)) }
private fun seg(t: Double,a: Double,b: Double) = smooth((t-a)/(b-a))
private fun <T> pick(condition: Boolean,a: T,b: T): T = if(condition) a else b
private fun str(value: Double) = value.toInt().toString()
private class HeartLoupeAgendaPainter(val c: android.graphics.Canvas,val titleFont:android.graphics.Typeface) {
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
    fun layerAlpha(alpha:Double) { c.saveLayerAlpha(null,(alpha.coerceIn(0.0,1.0)*255).toInt()) }

    fun grain(x:Double,y:Double,w:Double,h:Double,alpha:Double) {
        reset(0xFFFFFF,alpha)
        paint.isFilterBitmap=true
        c.drawBitmap(grainBitmap,null,android.graphics.RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),paint)
    }

    fun sticker(x:Double,y:Double,width:Double) {
        val value="My Little Loop"
        paint.textSize=100f;paint.typeface=titleFont
        val measured=paint.measureText(value).toDouble()+40
        save();translate(x-width/2,y);scale(width/measured,width*.27/170)
        reset(0x640B21,0.6,20.0);paint.textSize=100f;paint.typeface=titleFont;paint.textAlign=android.graphics.Paint.Align.LEFT
        c.drawText(value,25f,132f,paint)
        reset(0xFFF0DB,1.0,18.0);paint.textSize=100f;paint.typeface=titleFont;c.drawText(value,20f,125f,paint)
        reset(0x170E16,1.0,10.0);paint.textSize=100f;paint.typeface=titleFont;c.drawText(value,20f,125f,paint)
        reset(0xC51221);paint.textSize=100f;paint.typeface=titleFont;c.drawText(value,20f,125f,paint)
        restore()
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
        val paths=mapOf<String,android.graphics.Path>("M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z" to android.graphics.Path().also { p -> p.moveTo(-10f,-3f);p.lineTo(-21f,-12f);p.lineTo(-25f,-3f);p.lineTo(-20f,7f);p.lineTo(-9f,4f);p.close() },
        "M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66" to android.graphics.Path().also { p -> p.moveTo(-116f,-48f);p.cubicTo(-118f,-88f,-76f,-119f,-42f,-94f);p.moveTo(32f,-97f);p.cubicTo(71f,-128f,117f,-101f,127f,-66f) },
        "M -131 -9 C -127 51 -63 95 -8 133" to android.graphics.Path().also { p -> p.moveTo(-131f,-9f);p.cubicTo(-127f,51f,-63f,95f,-8f,133f) },
        "M -132 -8 Q -112 61 -45 99" to android.graphics.Path().also { p -> p.moveTo(-132f,-8f);p.quadTo(-112f,61f,-45f,99f) },
        "M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z" to android.graphics.Path().also { p -> p.moveTo(-139f,-35f);p.cubicTo(-136f,-96f,-72f,-126f,-16f,-77f);p.cubicTo(-38f,-87f,-97f,-82f,-112f,-31f);p.close() },
        "M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11" to android.graphics.Path().also { p -> p.moveTo(-144f,-31f);p.lineTo(-69f,-31f);p.cubicTo(-88f,-72f,-28f,-72f,-45f,-31f);p.lineTo(2f,-31f);p.lineTo(2f,16f);p.cubicTo(-39f,-2f,-39f,55f,2f,39f);p.lineTo(2f,124f);p.moveTo(-70f,-127f);p.lineTo(-70f,-74f);p.cubicTo(-29f,-92f,-29f,-33f,-70f,-49f);p.lineTo(-70f,11f);p.lineTo(-116f,11f);p.cubicTo(-99f,52f,-155f,52f,-139f,11f) },
        "M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9" to android.graphics.Path().also { p -> p.moveTo(-20f,-9f);p.lineTo(-20f,-34f);p.cubicTo(-20f,-66f,23f,-66f,23f,-34f);p.lineTo(23f,-9f) },
        "M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z" to android.graphics.Path().also { p -> p.moveTo(-29f,-5f);p.quadTo(-31f,-22f,-16f,-17f);p.quadTo(-1f,-12f,2f,-4f);p.quadTo(11f,-22f,27f,-16f);p.quadTo(38f,-10f,28f,8f);p.lineTo(24f,24f);p.lineTo(-20f,24f);p.close() },
        "M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21" to android.graphics.Path().also { p -> p.moveTo(-29f,0f);p.lineTo(29f,0f);p.moveTo(0f,-29f);p.lineTo(0f,29f);p.moveTo(-21f,-21f);p.cubicTo(8f,-13f,10f,12f,-21f,21f);p.moveTo(21f,-21f);p.cubicTo(-8f,-13f,-10f,12f,21f,21f) },
        "M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z" to android.graphics.Path().also { p -> p.moveTo(-34f,-6f);p.quadTo(-36f,-27f,-17f,-22f);p.quadTo(-1f,-17f,2f,-9f);p.quadTo(8f,-27f,29f,-21f);p.quadTo(44f,-14f,33f,9f);p.lineTo(28f,29f);p.lineTo(-25f,29f);p.close() },
        "M -5 -6 Q -1 -10 5 -5" to android.graphics.Path().also { p -> p.moveTo(-5f,-6f);p.quadTo(-1f,-10f,5f,-5f) },
        "M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z" to android.graphics.Path().also { p -> p.moveTo(-56f,-8f);p.cubicTo(-71f,-31f,-92f,-2f,-50f,27f);p.cubicTo(-13f,-1f,-34f,-30f,-47f,-10f);p.close() },
        "M -74 -97 Q -31 -115 -5 -75" to android.graphics.Path().also { p -> p.moveTo(-74f,-97f);p.quadTo(-31f,-115f,-5f,-75f) },
        "M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21" to android.graphics.Path().also { p -> p.moveTo(-83f,-85f);p.lineTo(-52f,28f);p.lineTo(43f,21f);p.lineTo(79f,-91f);p.moveTo(-52f,28f);p.lineTo(0f,-74f);p.lineTo(43f,21f);p.moveTo(-52f,28f);p.lineTo(0f,140f);p.lineTo(43f,21f) },
        "M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z" to android.graphics.Path().also { p -> p.moveTo(0f,-74f);p.cubicTo(-90f,-160f,-160f,-70f,-136f,0f);p.cubicTo(-118f,75f,-52f,99f,0f,140f);p.cubicTo(52f,100f,118f,75f,136f,0f);p.cubicTo(160f,-70f,88f,-160f,0f,-74f);p.close() },
        "M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z" to android.graphics.Path().also { p -> p.moveTo(0f,-74f);p.lineTo(-83f,-85f);p.lineTo(-136f,0f);p.lineTo(-52f,28f);p.lineTo(0f,140f);p.lineTo(43f,21f);p.lineTo(136f,0f);p.lineTo(79f,-91f);p.close() },
        "M 0 136 L -102 226 L -265 74" to android.graphics.Path().also { p -> p.moveTo(0f,136f);p.lineTo(-102f,226f);p.lineTo(-265f,74f) },
        "M 0 139 L -102 229 L -265 77" to android.graphics.Path().also { p -> p.moveTo(0f,139f);p.lineTo(-102f,229f);p.lineTo(-265f,77f) },
        "M 0 141 L -102 231 L -265 79" to android.graphics.Path().also { p -> p.moveTo(0f,141f);p.lineTo(-102f,231f);p.lineTo(-265f,79f) },
        "M 0 145 L -102 235 L -265 83" to android.graphics.Path().also { p -> p.moveTo(0f,145f);p.lineTo(-102f,235f);p.lineTo(-265f,83f) },
        "M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z" to android.graphics.Path().also { p -> p.moveTo(10f,-3f);p.lineTo(21f,-12f);p.lineTo(25f,-3f);p.lineTo(20f,7f);p.lineTo(9f,4f);p.close() },
        "M 121 26 Q 77 94 8 131" to android.graphics.Path().also { p -> p.moveTo(121f,26f);p.quadTo(77f,94f,8f,131f) },
        "M 5 -75 C 57 -132 129 -100 139 -42" to android.graphics.Path().also { p -> p.moveTo(5f,-75f);p.cubicTo(57f,-132f,129f,-100f,139f,-42f) },
        "M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z" to android.graphics.Path().also { p -> p.moveTo(85f,-108f);p.cubicTo(159f,-86f,159f,-5f,106f,59f);p.cubicTo(145f,-15f,131f,-65f,85f,-108f);p.close() })
    }
}

private fun drawScene(p:HeartLoupeAgendaPainter,t:Double,tx:Double,ty:Double,touching:Boolean) {
    val open=seg(t,0.9,1.8)*(1.0-seg(t,6.6,7.6))
    val drift=seg(t,1.8,2.1)*(1.0-seg(t,5.6,5.9))
    val scroll=310.0*seg(t,1.8,5.6)*(1.0-seg(t,5.6,6.6))
    var lensX=196.5+18.0*sin(6.283185307179586*t/9.0)*drift
    var lensY=420.0+95.0*open+12.0*sin(12.566370614359172*t/9.0)*drift
    if (touching) { lensX=min(278.0,max(114.0,tx));lensY=min(648.0,max(320.0,ty)) }
    val lensScale=0.65+0.35*open
    p.r(0.0,0.0,393.0,852.0,0xB51027)
    p.grad(24.0,0.0,345.0,852.0,0xCA1A2B,0xC8172A,0xBE1429)
    p.line(24.0,0.0,24.0,852.0,0x6A1525,1.0,0.7)
    p.line(369.0,0.0,369.0,852.0,0x731527,1.0,0.7)
    // Small candy hearts frame one slanted, outlined script masthead.
    val hx = listOf(169,196,117,38,49,341,350,300,316)
    val hy = listOf(72,64,118,345,457,405,477,670,692)
    for (idx in 0 until 9) {
        val i=idx.toDouble()
        p.save()
        p.translate((hx[idx]).toDouble(),(hy[idx]).toDouble())
        p.rotate(-28.0+11.0*i)
        p.scale(pick(idx < 3,0.055,0.035),pick(idx < 3,0.055,0.035))
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x661729,35.0,0.35)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF0D8,21.0)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE95153,5.0)
        p.restore()
    }
    p.save()
    p.translate(58.0,88.0)
    p.rotate(-28.0)
    p.line(0.0,6.0,0.0,40.0,0xE0B8A4,5.0)
    p.scale(0.22,0.22)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xA20B21)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF14D58,0.0,0.35)
    p.path("M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z",0xFFF1D8)
    p.restore()
    p.save()
    p.translate(196.5,123.0)
    p.rotate(-10.0)
    p.sticker(0.0,0.0,375.0)
    p.restore()
    p.save()
    p.translate(80.0,236.0)
    p.rotate(-28.0)
    p.fit("A LITTLE EVERYDAY",0.0,-6.0,13.0,139.0,0xFFF0D9,"condensed")
    p.fit("CLUB FIELD NOTES",0.0,9.0,12.0,122.0,0xFFF0D9,"condensed")
    p.restore()
    p.save()
    p.translate(303.0,231.0)
    p.rotate(9.0)
    p.text("OCT.",0.0,-6.0,37.0,0x280D18,"heavy",1.0)
    p.restore()
    p.save()
    p.translate(332.0,326.0)
    p.rotate(18.0)
    p.e(4.0,47.0,47.0,7.0,0x6F112E,0.5)
    p.grad(-43.0,-42.0,86.0,79.0,0x8DADFF,0x4F7BE8,0x315BC9,7.0,true)
    p.r(-37.0,-37.0,74.0,67.0,0x244EA5,4.0)
    p.e(-3.0,-5.0,29.0,29.0,0xF36D3B)
    p.e(-3.0,-5.0,23.0,23.0,0xE74236)
    p.e(-3.0,-5.0,18.0,18.0,0xFF8B4E)
    p.e(-3.0,-5.0,8.0,8.0,0xFADE8B)
    p.e(-3.0,-5.0,2.0,2.0,0x3A3B72)
    p.line(28.0,-27.0,28.0,4.0,0xD8E7CD,3.0)
    p.line(28.0,4.0,11.0,17.0,0xD8E7CD,3.0)
    p.r(6.0,14.0,12.0,5.0,0x9BC0C9,1.0)
    p.e(-29.0,27.0,3.0,3.0,0xE9E1AD)
    p.line(-41.0,39.0,42.0,39.0,0xF07D8B,2.0)
    p.restore()
    p.save()
    p.translate(48.0,384.0)
    p.rotate(13.0)
    p.scale(0.20,0.20)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x751D58)
    p.path("M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z",0xD657AF,3.0)
    p.path("M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21",0xF6A8D5,2.0)
    p.path("M -74 -97 Q -31 -115 -5 -75",0xFAD5E9,5.0)
    p.restore()
    p.save()
    p.translate(355.0,639.0)
    p.rotate(11.0)
    p.scale(0.8,0.8)
    p.e(3.0,6.0,32.0,32.0,0x6A1433,0.35)
    p.e(0.0,0.0,30.0,30.0,0xEA718C)
    p.path("M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21",0x8A3154,2.0)
    p.e(-12.0,-15.0,7.0,3.0,0xF7AFB4,0.6)
    p.restore()
    // A split-colour puzzle heart, a nested candy and a small heart handbag.
    p.save()
    p.translate(43.0,632.0)
    p.rotate(-31.0)
    p.scale(0.28,0.28)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEAA9BF)
    p.save()
    p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    p.r(-150.0,-150.0,150.0,310.0,0xA9D5E4)
    p.path("M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11",0x5E889C,3.0)
    p.restore()
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF8D7DB,7.0)
    p.restore()
    p.save()
    p.translate(66.0,755.0)
    p.rotate(-12.0)
    p.scale(0.36,0.36)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xAA1129)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF6C7BA,7.0)
    for (idx in 0 until 4) {
        val scale=0.8-idx.toDouble()*0.17
        p.save()
        p.scale(scale,scale)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFE0CB,5.0)
        p.restore()
    }
    p.e(-70.0,-73.0,10.0,7.0,0xFFF0D5,0.7)
    p.restore()
    p.save()
    p.translate(325.0,753.0)
    p.rotate(-8.0)
    p.path("M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9",0xFFEBD2,5.0)
    p.path("M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z",0xFBE7CE)
    p.path("M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z",0xB00D2B)
    p.e(6.0,3.0,3.0,4.0,0xE1B55D)
    p.line(5.0,7.0,3.0,17.0,0xEED28E,2.0)
    p.e(3.0,17.0,3.0,3.0,0xF3D79A)
    p.line(-26.0,-4.0,-24.0,18.0,0xE77B87,1.5,0.7)
    p.restore()
    val candyX = listOf(52,343,298,102)
    val candyY = listOf(477,472,688,697)
    for (idx in 0 until 4) {
        p.save()
        p.translate((candyX[idx]).toDouble(),(candyY[idx]).toDouble())
        p.rotate(-30.0+idx.toDouble()*37.0)
        p.path("M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z",0xEB4A5F)
        p.path("M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z",0xAE0E2D)
        p.e(1.0,2.0,12.0,10.0,0x790E28,0.5)
        p.e(0.0,0.0,11.0,10.0,0xD9163B)
        p.e(-2.0,-3.0,7.0,5.0,0xF96075,0.65)
        p.path("M -5 -6 Q -1 -10 5 -5",0xFFE8D5,1.3,0.8)
        p.line(-19.0,-6.0,-13.0,-1.0,0xFFACAF,1.0,0.8)
        p.restore()
    }
    // A metal dispenser remains visible before the ivory schedule unrolls.
    p.r(123.0,264.0,149.0,376.0,0x690D23,2.0,0.6)
    p.grad(126.0,266.0,143.0,368.0,0x55494A,0xE0D1BE,0x66514D,2.0,true)
    p.r(132.0,306.0,131.0,320.0,0x332E2D)
    p.grad(136.0,309.0,123.0,313.0,0x454342,0x8F8A7D,0x5B5751)
    p.grad(142.0,314.0,15.0,302.0,0x8B8C80,0xB4B3A0,0x7E8175,0.0,true,0.5)
    p.grad(246.0,314.0,8.0,302.0,0x26292A,0xAAA99B,0x474D47,0.0,true,0.5)
    p.grad(132.0,271.0,131.0,36.0,0x8E7772,0xE5D5C5,0xBEAAA0,1.0)
    p.r(144.0,276.0,107.0,25.0,0xF1E9D7,1.0)
    p.text("MY LITTLE LOOP",197.0,278.0,9.0,0x9B2135,"condensed",1.0)
    p.text("FIELD SCHEDULE",197.0,288.0,10.0,0x892337,"condensed",1.0)
    val dates = listOf("19","20","21","22","23","24","25","26","27","28")
    val first = listOf("OPEN","CONCEPT","PROCESS","COLOUR","LOOP","COVER","STUDIO","SOFT","PAPER","DAWN")
    val second = listOf("STUDIO","NOTES","FILM","MIX","TEASER","PHOTO","PREVIEW","HOURS","PARTY","CLUB")
    for (layer in 0 until 2) {
        if (layer == 0 || open > 0.0001) {
            if (layer == 0) { p.save() } else { p.layerAlpha(open) }
            if (layer == 1) {
                p.translate(lensX,lensY)
                p.scale(lensScale,lensScale)
                p.rotate(-5.0)
                p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
                p.scale(1.7,1.7)
                p.translate(-lensX,-lensY)
                p.r(0.0,0.0,393.0,852.0,0xC8172A)
            }
            if (open > 0.0001) {
                p.r(130.0,310.0,135.0,490.0*open,0x64142A,0.0,0.25)
                p.grad(133.0,308.0,129.0,490.0*open,0xF8F6ED,0xFFFDF4,0xE6E4DA)
                p.save()
                p.clip(134.0,309.0,127.0,max(0.001,488.0*open))
                for (idx in 0 until 10) {
                    val i=idx.toDouble()
                    val y=333.0+i*118.0-scroll
                    if (y > 180.0 && y < 820.0) {
                        p.line(135.0,y-16.0,260.0,y-16.0,0x463C32,0.9)
                        p.text(dates[idx],139.0,y-10.0,12.0,0xAC253B,"bold")
                        if (idx % 3 == 0) {
                            p.fit(first[idx],200.0,y+9.0,24.0,109.0,0x25201F,"condensed")
                            p.fit(second[idx],200.0,y+37.0,28.0,109.0,0x25201F,"condensed")
                        }
                        if (idx % 3 == 1) {
                            p.fit(first[idx],200.0,y+9.0,23.0,109.0,0x2B211E,"condensed")
                            p.fit(second[idx],201.0,y+36.0,25.0,105.0,0x2B211E,"italic")
                        }
                        if (idx % 3 == 2) {
                            p.fit(first[idx],200.0,y+9.0,23.0,109.0,0xAC293C,"condensed")
                            p.fit(second[idx],200.0,y+35.0,30.0,109.0,0x25201F,"condensed")
                        }
                        p.text("OCT / CLUB FIELD NOTES",198.0,y+82.0,5.3,0x73604D,"mono",1.0)
                    }
                }
                p.restore()
                p.line(134.0,309.0,134.0,308.0+490.0*open,0xFFF9E2,0.7)
            }
            p.restore()
        }
    }
    // An articulated metal arm joins the heart without hiding the agenda text.
    p.layerAlpha(open)
    p.translate(lensX,lensY)
    p.scale(lensScale,lensScale)
    p.rotate(-5.0)
    p.path("M 0 145 L -102 235 L -265 83",0x6C1D38,19.0,0.45)
    p.path("M 0 141 L -102 231 L -265 79",0x566272,16.0)
    p.path("M 0 139 L -102 229 L -265 77",0xCED7CE,11.0)
    p.path("M 0 136 L -102 226 L -265 74",0xFFF3DF,3.0)
    p.e(-102.0,229.0,15.0,15.0,0x6B7881)
    p.e(-102.0,228.0,12.0,12.0,0xE6E8D8)
    p.e(-102.0,228.0,7.0,7.0,0x818B98)
    p.e(-104.0,225.0,3.0,3.0,0xFFF5DE)
    p.e(-265.0,77.0,17.0,17.0,0x9EABB0)
    p.e(-265.0,77.0,12.0,12.0,0xE7E8D5)
    p.e(-265.0,77.0,5.0,5.0,0x66758A)
    p.save()
    p.translate(7.0,11.0)
    p.blur(7.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x641A32,22.0,0.42)
    p.restore()
    p.blur(0.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x86877F,22.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE2DFCA,17.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xB9BDB5,10.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEDE3DA,3.0)
    p.save()
    p.translate(-1.5,-2.0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFCE6D6,1.5,0.8)
    p.restore()
    p.save()
    p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    p.radial(-40.0,-55.0,170.0,0xFFF5DA,0xFFF5DA,0.12)
    p.path("M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z",0xFFF4E2,0.0,0.16)
    p.path("M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z",0xF5D6D9,0.0,0.19)
    p.restore()
    p.e(-96.0+10.0*open,-82.0-7.0*open,8.0,2.0,0xFFFCEB,0.7)
    p.line(-97.0+10.0*open,-91.0-7.0*open,-95.0+10.0*open,-73.0-7.0*open,0xFFF9E7,1.2,0.65)
    p.path("M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66",0xFFF9E8,3.5,0.6+0.25*open)
    p.path("M 121 26 Q 77 94 8 131",0xE686A1,2.0,0.8)
    p.path("M -132 -8 Q -112 61 -45 99",0xA8E1DA,1.5,0.8)
    p.path("M -131 -9 C -127 51 -63 95 -8 133",0x7A2333,5.0,0.36)
    p.path("M 5 -75 C 57 -132 129 -100 139 -42",0xFFF3DD,4.5,0.8)
    val flash=seg(t,0.95,1.15)*(1.0-seg(t,1.25,1.6))
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF9E5,0.0,0.74*flash)
    p.restore()
    p.grain(0.0,0.0,393.0,852.0,0.055)
}
