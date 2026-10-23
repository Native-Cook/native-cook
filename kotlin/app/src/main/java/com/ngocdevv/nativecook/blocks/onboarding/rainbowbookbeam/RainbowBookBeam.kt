package com.ngocdevv.nativecook.blocks.onboarding.rainbowbookbeam

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

private const val LOOP = 7.6
private fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

/** One self-contained splash. previewTime supports deterministic still previews. */
@Composable
fun RainbowBookBeam(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val frameTime = remember { mutableDoubleStateOf(0.0) }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val held = remember { mutableDoubleStateOf(0.0) }
    val released = remember { mutableDoubleStateOf(-1.0) }
    val touching = remember { mutableStateOf(false) }
    val tapAt = remember { mutableDoubleStateOf(-1.0) }
    val tapFrom = remember { mutableDoubleStateOf(0.0) }
    val interaction = remember { mutableIntStateOf(0) }
    val startEpoch = remember { mutableLongStateOf(-1L) }
    LaunchedEffect(active, reduced, autoPlay, previewTime, touching.value, released.doubleValue, interaction.intValue) {
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: 3.6; return@LaunchedEffect }
        if (!active || (!autoPlay && !touching.value && released.doubleValue < 0)) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                if (startEpoch.longValue < 0) startEpoch.longValue = now
                elapsed.doubleValue = (now - startEpoch.longValue) / 1e9
                val age = elapsed.doubleValue - released.doubleValue
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    tapAt.doubleValue >= 0 && elapsed.doubleValue - tapAt.doubleValue < (3.6 - tapFrom.doubleValue) / 2.125 -> tapFrom.doubleValue + max(0.0, elapsed.doubleValue - tapAt.doubleValue) * 2.125
                    released.doubleValue >= 0 && age < 2 -> held.doubleValue
                    released.doubleValue >= 0 && age < 2.6 -> held.doubleValue
                    released.doubleValue >= 0 -> if (autoPlay) (age - 2.6) % LOOP else 0.0
                    autoPlay -> elapsed.doubleValue % LOOP
                    else -> 0.0
                }
            }
            if (!autoPlay && !touching.value && released.doubleValue >= 0 && elapsed.doubleValue - released.doubleValue >= 2.6) break
        }
    }
    val painter = remember { RainbowBookBeamPainter() }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Oria reading. Stories open worlds. Drag upward to open the book." }
        .pointerInput(reduced, previewTime) {
            if (reduced || previewTime != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                interaction.intValue++
                if (!autoPlay) { startEpoch.longValue = -1; elapsed.doubleValue = 0.0 }
                val origin=frameTime.doubleValue
                touching.value = true; tapAt.doubleValue = -1.0
                held.doubleValue = origin
                var last = down.position
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        held.doubleValue = (origin-(last.y-down.position.y)/density/240*3.6).coerceIn(0.0,4.8)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (abs(last.y - down.position.y) < 8 * density) {
                        tapFrom.doubleValue=if(origin >= 1.05 && origin < 3.4) origin else 1.05
                        tapAt.doubleValue=elapsed.doubleValue
                        held.doubleValue=3.6
                        released.doubleValue=elapsed.doubleValue
                    } else { released.doubleValue=elapsed.doubleValue }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                } finally { touching.value = false }
            }
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.parseColor("#040305"))
        val scale = min(size.width / 393f, size.height / 852f)
        canvas.save(); canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2); canvas.scale(scale, scale)
        val recovery = if (reduced || previewTime != null || touching.value || released.doubleValue < 0 || elapsed.doubleValue - released.doubleValue >= 2.6) 1.0 else 1 - ease((elapsed.doubleValue - released.doubleValue - 2) / 0.6)
        painter.draw(canvas, frameTime.doubleValue, recovery)
        canvas.restore()
    }
}

private class RainbowBookBeamPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun hash(x: Double): Double { val v=sin(x*12.9898)*43758.5453;return v-floor(v) }
    private fun polygon(points: List<Pair<Double,Double>>) = Path().apply { moveTo(points[0].first.toFloat(),points[0].second.toFloat());points.drop(1).forEach { lineTo(it.first.toFloat(),it.second.toFloat()) };close() }
    private fun glow(c: NativeCanvas,path: Path,color: String,blur: Double,alpha: Double) { val p=ink(color,alpha);p.maskFilter=android.graphics.BlurMaskFilter(blur.toFloat(),android.graphics.BlurMaskFilter.Blur.NORMAL);c.drawPath(path,p) }

    private fun ink(color: String, alpha: Double = 1.0): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.color = android.graphics.Color.parseColor(color)
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return paint
    }
    private fun circle(x: Double, y: Double, radius: Double) = Path().apply { addCircle(x.toFloat(), y.toFloat(), radius.toFloat(), Path.Direction.CW) }
    private fun roundRect(x: Double, y: Double, width: Double, height: Double, radius: Double) = Path().apply {
        addRoundRect(RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat()), radius.toFloat(), radius.toFloat(), Path.Direction.CW)
    }
    private fun fill(c: NativeCanvas, path: Path, color: String, alpha: Double = 1.0) { c.drawPath(path, ink(color, alpha)) }
    private fun radial(c: NativeCanvas, x: Double, y: Double, radius: Double, color: String, alpha: Double = 1.0, startRadius: Double = 15.0) {
        val p = ink("#FFFFFF", alpha); val tint = android.graphics.Color.parseColor(color)
        p.shader = RadialGradient(x.toFloat(), y.toFloat(), radius.toFloat(), intArrayOf(tint, tint, tint and 0x00FFFFFF), floatArrayOf(0f, (startRadius / radius).toFloat(), 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), p)
    }
    private fun stroke(c: NativeCanvas, path: Path, color: String, width: Double, alpha: Double = 1.0) {
        val p = ink(color, alpha); p.style = Paint.Style.STROKE; p.strokeWidth = width.toFloat(); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; c.drawPath(path, p)
    }
    private fun gradient(c: NativeCanvas, path: Path, colors: List<String>, start: List<Double>, end: List<Double>, alpha: Double = 1.0, width: Double = 0.0) {
        val p = ink("#FFFFFF", alpha)
        p.shader = LinearGradient(start[0].toFloat(), start[1].toFloat(), end[0].toFloat(), end[1].toFloat(), colors.map { android.graphics.Color.parseColor(it) }.toIntArray(), null, Shader.TileMode.CLAMP)
        if (width > 0) { p.style = Paint.Style.STROKE; p.strokeWidth = width.toFloat() }
        c.drawPath(path, p)
    }
    private fun label(c: NativeCanvas, text: String, x: Double, y: Double, size: Double, color: String, bold: Boolean = false, alpha: Double = 1.0) {
        val p = ink(color, alpha); p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textSize = size.toFloat(); p.textAlign = Paint.Align.CENTER
        c.drawText(text, x.toFloat(), y.toFloat() - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    }
    private fun beamFill(c: NativeCanvas,path: Path,color: String,top: Double,alpha: Double) {
        val p=ink("#FFFFFF",alpha);val tint=android.graphics.Color.parseColor(color)
        p.shader=LinearGradient(196.5f,495f,196.5f,top.toFloat(),intArrayOf(android.graphics.Color.parseColor("#FFF4D1"),tint,tint and 0x00FFFFFF),floatArrayOf(0f,0.84f,1f),Shader.TileMode.CLAMP);c.drawPath(path,p)
    }
    private fun beamEdge(c:NativeCanvas,path:Path,top:Double,beam:Double) {
        val p=ink("#FFFFFF",beam);p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        p.shader=LinearGradient(196.5f,495f,196.5f,top.toFloat(),intArrayOf(0x29FFF1D8,0x52FFE0F5,0x00FFE0F5),floatArrayOf(0f,0.84f,1f),Shader.TileMode.CLAMP)
        p.strokeWidth=2.6f;p.maskFilter=android.graphics.BlurMaskFilter(2f,android.graphics.BlurMaskFilter.Blur.NORMAL);c.drawPath(path,p)
        p.maskFilter=null;p.strokeWidth=0.65f;c.drawPath(path,p)
    }
    fun draw(c: NativeCanvas,t: Double,r: Double) {
        val phase=t/LOOP*PI*2;val openingBase=ease((t-1.2)/0.72)*(1-ease((t-5.55)/0.85));val age=max(0.0,t-1.2)
        val recoil=0.24*sin(age*12)*exp(-age*2.9)*ease(age/0.1)*(1-ease((age-1.15)/0.35));val opening=max(0.0,openingBase+recoil)*r
        val beam=ease((t-1.72)/0.72)*(1-ease((t-4.8)/0.9))*r
        label(c,"ORIA / A NEW CHAPTER",196.5,106.0,11.0,"#A497A4");label(c,"Stories open worlds.",196.5,173.0,27.0,"#F8EEF5",true);label(c,"Follow your curiosity, one page at a time.",196.5,210.0,14.0,"#A497A4")
        val reflected=Path().apply{addOval(RectF(108.5f,548f,284.5f,566f),Path.Direction.CW)};glow(c,reflected,"#E64BB0",12.0,0.04+beam*0.18)
        if(beam>0.001){
            val palette=listOf("#FF3C77","#E63DAA","#E748C2","#FFEAA0","#F997D9")
            val edges=mutableListOf<Path>()
            for(band in 0..<5){
                val points=mutableListOf<Pair<Double,Double>>();val reflections=mutableListOf<Pair<Double,Double>>();val edge=Path()
                for(side in 0..1)for(step in 0..96){
                    val v=(if(side==0)step else 96-step)/96.0*beam;val width=(3+70*v.pow(0.8))*(1+0.045*sin(phase*4-v*6));val boundary=band+(if(side==0)0 else 1)-2.5
                    val sway=sin(v*20-phase*14)*5*v.pow(0.8)+10*v*v*sin(phase*2);val x=196.5+sway+boundary/2.5*width+sin(v*26-phase*14+boundary*0.8)*1.7*v
                    points.add(x to (495-238*v));if(side==1){if(step==0)edge.moveTo(x.toFloat(),(495-238*v).toFloat()) else edge.lineTo(x.toFloat(),(495-238*v).toFloat())};reflections.add((196.5+(x-196.5)*0.65*(1-0.55*v)) to (519+238*v*0.19))
                }
                val path=polygon(points);val mirror=polygon(reflections);if(band<4)edges.add(edge)
                glow(c,mirror,palette[band],4.0,0.09*r)
                val reflectionPaint=ink("#FFFFFF",0.2*r);val tint=android.graphics.Color.parseColor(palette[band]);reflectionPaint.shader=LinearGradient(196.5f,519f,196.5f,566f,intArrayOf(tint,tint and 0x00FFFFFF),null,Shader.TileMode.CLAMP);c.drawPath(mirror,reflectionPaint)
                glow(c,path,palette[band],13.0,0.27*beam);beamFill(c,path,palette[band],495-238*beam,0.88*beam)
            }
            for(edge in edges)beamEdge(c,edge,495-238*beam,beam)
            for(i in 0..<12){
                val v=(i*0.173+t/LOOP*3)%1;val sway=sin(v*20-phase*14)*5*v.pow(0.8)+10*v*v*sin(phase*2);val x=196.5+sway+(hash(i+3.0)-0.5)*(5+118*v.pow(0.8));val y=495-238*v;val radius=1.4+2.6*hash(i+54.0)
                val alpha=ease(v/0.08)*(1-ease((v-0.85)/0.15))*ease((beam-v)/0.12)*r
                glow(c,circle(x,y,radius*2),"#FFF7E9",6.0,alpha*0.7);fill(c,circle(x,y,radius),"#FFF7E9",alpha)
            }
        }
        val anticipate=sin(PI*((t-1.02)/0.22).coerceIn(0.0,1.0))
        c.save();c.rotate((-12*(1-opening)-6*anticipate*anticipate*r).toFloat(),196.5f,500f)
        val openAlpha=ease(opening/0.22);val closed=1-openAlpha
        if(closed>0.001){val solid=polygon(listOf(188.5 to 412.0,206.5 to 415.0,206.5 to 507.0,188.5 to 504.0));gradient(c,solid,listOf("#FFB826","#FF773A","#AF368F"),listOf(188.0,412.0),listOf(207.0,507.0),closed);stroke(c,solid,"#FFD256",0.9,closed);fill(c,polygon(listOf(188.5 to 412.0,191.5 to 413.0,191.5 to 505.0,188.5 to 504.0)),"#A52E8E",closed)}
        if(openAlpha>0.001){
            val angle=opening*PI/2;val span=8*cos(angle)+83*sin(angle);val tip=500-88*cos(angle)-18*sin(angle)
            for(side in listOf(-1.0,1.0)){
                val cover=Path().apply{moveTo(196.5f,501f);quadTo((196.5+side*span*0.54).toFloat(),(491-45*cos(angle)).toFloat(),(196.5+side*span).toFloat(),tip.toFloat());lineTo((196.5+side*(span+3*sin(angle))).toFloat(),(tip+8).toFloat());quadTo((196.5+side*span*0.55).toFloat(),(506-35*cos(angle)).toFloat(),196.5f,510f);close()}
                glow(c,cover,"#FF5B67",12.0,0.22*openAlpha);gradient(c,cover,listOf("#7D267F","#B82C9D","#F36663"),listOf(196.5+side*span,tip),listOf(196.5,510.0),openAlpha);stroke(c,cover,"#FF996B",0.7,openAlpha*0.7)
            }
            for(layer in 0..<3){
                val j=layer.toDouble();val leaf=ease((t-1.26-j*0.055)/0.72)*(1-ease((t-5.30-j*0.06)/0.82))*r;val a=leaf*PI/2;val w=8*cos(a)+81*sin(a);val tip=500-88*cos(a)-18*sin(a)-j*1.4;val curl=sin(leaf*PI).pow(2)*(7+j*3)*sin(phase*8+j)
                for(side in listOf(-1.0,1.0)){
                    val page=Path().apply{moveTo(196.5f,(498-j*0.6).toFloat());quadTo((196.5+side*w*0.48).toFloat(),(489-56*cos(a)-10*sin(a)-curl).toFloat(),(196.5+side*w).toFloat(),(tip-5).toFloat());lineTo((196.5+side*(w+2*sin(a))).toFloat(),(tip+3).toFloat());quadTo((196.5+side*w*0.55).toFloat(),(503-39*cos(a)-3*sin(a)).toFloat(),196.5f,(504-j*0.5).toFloat());close()}
                    gradient(c,page,if(layer==2)listOf("#FFD457","#FF9A3F","#FF583B")else listOf("#FF9E33","#E96C38","#B94B69"),listOf(196.5+side*w,tip-5),listOf(196.5,504.0),openAlpha);stroke(c,page,"#FFD256",0.65,openAlpha*(0.4+j*0.2))
                }
            }
            val spine=Path().apply{moveTo(188.5f,505f);quadTo(196.5f,517f,204.5f,505f)};stroke(c,spine,"#69BBD1",1.3,0.7*openAlpha)
        }
        c.restore()
        label(c,"One page. Endless places.",196.5,621.0,17.0,"#F8EEF5",true);fill(c,roundRect(42.0,700.0,309.0,54.0,27.0),"#F5D5EA");label(c,"Open your world",196.5,727.0,15.0,"#40152F",true);label(c,"A LITTLE WONDER, EVERY DAY",196.5,783.0,11.0,"#A497A4")
    }
}
