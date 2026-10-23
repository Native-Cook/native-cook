package com.ngocdevv.nativecook.blocks.splash.liquidfolderyear

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

private const val LOOP = 8.8
private fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

/** One self-contained splash. previewTime supports deterministic still previews. */
@Composable
fun LiquidFolderYear(autoPlay: Boolean = false, previewTime: Double? = null) {
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
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: 4.8; return@LaunchedEffect }
        if (!active || (!autoPlay && !touching.value && released.doubleValue < 0)) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                if (startEpoch.longValue < 0) startEpoch.longValue = now
                elapsed.doubleValue = (now - startEpoch.longValue) / 1e9
                val age = elapsed.doubleValue - released.doubleValue
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    tapAt.doubleValue >= 0 && elapsed.doubleValue - tapAt.doubleValue < (4.8 - tapFrom.doubleValue) / 2.8461538461538463 -> tapFrom.doubleValue + max(0.0, elapsed.doubleValue - tapAt.doubleValue) * 2.8461538461538463
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
    val painter = remember { LiquidFolderYearPainter() }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Vela yearbook. A folder turns into liquid and the new year. Drag to reveal." }
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
                        held.doubleValue = (origin+(last.x-down.position.x)/size.width*5.2).coerceIn(0.0,5.2)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (abs(last.x - down.position.x) < 8 * density) {
                        tapFrom.doubleValue=if(origin >= 1.1 && origin < 4.6) origin else 1.1
                        tapAt.doubleValue=elapsed.doubleValue
                        held.doubleValue=4.8
                        released.doubleValue=elapsed.doubleValue
                    } else { released.doubleValue=elapsed.doubleValue }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                } finally { touching.value = false }
            }
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.parseColor("#030305"))
        val scale = min(size.width / 393f, size.height / 852f)
        canvas.save(); canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2); canvas.scale(scale, scale)
        val recovery = if (reduced || previewTime != null || touching.value || released.doubleValue < 0 || elapsed.doubleValue - released.doubleValue >= 2.6) 1.0 else 1 - ease((elapsed.doubleValue - released.doubleValue - 2) / 0.6)
        painter.draw(canvas, frameTime.doubleValue, recovery)
        canvas.restore()
    }
}

private class LiquidFolderYearPainter {
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
    private val shader = if(android.os.Build.VERSION.SDK_INT>=33) android.graphics.RuntimeShader(liquidFolderYearShader) else null
    fun draw(c: NativeCanvas,t: Double,r: Double) {
        if(android.os.Build.VERSION.SDK_INT>=33 && shader!=null){shader.setFloatUniform("uTime",t.toFloat());shader.setFloatUniform("uRecovery",r.toFloat());val p=ink("#FFFFFF");p.shader=shader;c.drawRect(0f,0f,393f,852f,p)}else fallback(c,t,r)
        val active=ease((t-1.4)/0.5)*(1-ease((t-4.6)/0.5))*r;for(i in 0..<24){val phase=(t/LOOP*3+hash(i+4.0))%1;val a=i*2.39996;val radius=58+85*phase;fill(c,circle(196.5+cos(a)*radius,405+sin(a)*radius*0.65,0.6+1.2*hash(i+5.0)),if(i%3==0)"#7EF9EA" else "#E9A0FF",active*sin(phase*PI).pow(2))}
        label(c,"VELA / YEARBOOK",196.5,122.0,11.0,"#908899");label(c,"Make room for more.",196.5,191.0,27.0,"#F4EEF8",true);label(c,"A new year. A whole new kind of canvas.",196.5,228.0,14.0,"#908899");label(c,"New possibilities",196.5,568.0,13.0,"#F4EEF8",true);label(c,"Let’s start something new.",196.5,599.0,13.0,"#A79AAE");stroke(c,roundRect(49.0,687.0,295.0,52.0,26.0),"#6E4282",1.0);label(c,"Create your yearbook",196.5,713.0,15.0,"#EEDDF5",true);label(c,"IDEAS HAVE A WAY OF FINDING YOU",196.5,776.0,11.0,"#908899")
    }
    // API26–32 preserves the splash/return cadence with a shaded vector membrane.
    private fun fallback(c: NativeCanvas,t: Double,r: Double) {
        val morph=ease((t-2.65)/1.65)*(1-ease((t-5.9)/1.2))*r
        val fluid=ease((t-1.75)/1.35)*(1-ease((t-6.25)/1.25))*r
        val splash=(ease((t-1.85)/0.65)*(1-ease((t-3.4)/0.9))+ease((t-6.0)/0.45)*(1-ease((t-6.8)/0.65)))*r
        val contour=mutableListOf<Pair<Double,Double>>()
        for(i in 0..96){val a=i/96.0*PI*2;val radius=1+splash*(sin(a*5-t/LOOP*PI*6)*0.19+sin(a*8+t/LOOP*PI*4)*0.11);contour.add((196.5+cos(a)*(60+57*splash)*radius) to (405+sin(a)*(55+22*splash)*radius))}
        val folder=roundRect(135.3,382.6,122.4,74.8,6.0).apply{addPath(roundRect(135.3,369.0,63.9,24.5,3.4));addPath(roundRect(155.0,343.8,77.5,91.1,2.4))}
        val shape=if(fluid<0.01)folder else polygon(contour)
        val body=1-ease((morph-0.66)/0.28)
        if(body>0.001){
            gradient(c,shape,if(fluid<0.1)listOf("#7114F1","#B500F8","#250D39")else listOf("#18F0AF","#00E6D7","#B500F8"),listOf(125.0,350.0),listOf(270.0,450.0),body)
            glow(c,shape,"#B500F8",10.0,body*fluid*0.15)
            stroke(c,shape,"#E7B3FF",1.0,body*0.7)
            val shine=polygon(listOf(139.0 to 367.0,191.0 to 351.0,253.0 to 391.0,225.0 to 395.0));gradient(c,shine,listOf("#FFFFFF","#00FFFFFF"),listOf(170.0,352.0),listOf(213.0,401.0),body*0.18)
        }
        if(morph>0.66){val p=ink("#FFFFFF",ease((morph-0.66)/0.28));p.textSize=83f;p.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);p.textAlign=Paint.Align.CENTER;p.shader=LinearGradient(70f,405f,325f,405f,intArrayOf(android.graphics.Color.parseColor("#18F0AF"),android.graphics.Color.parseColor("#00E6D7"),android.graphics.Color.parseColor("#B500F8")),null,Shader.TileMode.CLAMP);c.drawText("2026",196.5f,435f,p)}
        for(i in 0..<7){val u=((t-2.15-i*0.045)/1.7).coerceIn(0.0,1.0);val life=sin(u*PI).pow(2)*r;val a=i*2.399963;val travel=ease(u);val x=196.5+cos(a)*(0.55+1.75*travel)*68;val y=405+(sin(a)*(0.30+0.83*travel)+0.18*travel*travel)*68;val radius=(0.045+i%3*0.018)*life*68;fill(c,circle(x,y,radius),if(i%2==0)"#3EEDD6" else "#D095FA",ease(life/0.3))}
    }
}
