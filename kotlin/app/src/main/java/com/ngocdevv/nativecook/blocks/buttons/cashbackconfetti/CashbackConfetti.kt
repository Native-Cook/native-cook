package com.ngocdevv.nativecook.blocks.buttons.cashbackconfetti

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

private const val LOOP = 6.0
private fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

/** One self-contained splash. previewTime supports deterministic still previews. */
@Composable
fun CashbackConfetti(autoPlay: Boolean = false, previewTime: Double? = null) {
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
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: 2.2; return@LaunchedEffect }
        if (!active || (!autoPlay && !touching.value && released.doubleValue < 0)) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                if (startEpoch.longValue < 0) startEpoch.longValue = now
                elapsed.doubleValue = (now - startEpoch.longValue) / 1e9
                val age = elapsed.doubleValue - released.doubleValue
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    tapAt.doubleValue >= 0 && elapsed.doubleValue - tapAt.doubleValue < (3.1 - tapFrom.doubleValue) / 2.05 -> tapFrom.doubleValue + max(0.0, elapsed.doubleValue - tapAt.doubleValue) * 2.05
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
    val painter = remember { CashbackConfettiPainter() }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Perks rewards. Claim your cashback. Drag to celebrate." }
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
                        held.doubleValue = (origin+(last.x-down.position.x)/size.width*3.0).coerceIn(0.0,3.0)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (abs(last.x - down.position.x) < 8 * density) {
                        tapFrom.doubleValue=if(origin >= 1.05 && origin < 2.6) origin else 1.05
                        tapAt.doubleValue=elapsed.doubleValue
                        held.doubleValue=3.1
                        released.doubleValue=elapsed.doubleValue
                    } else { released.doubleValue=elapsed.doubleValue }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                } finally { touching.value = false }
            }
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.parseColor("#050207"))
        val scale = min(size.width / 393f, size.height / 852f)
        canvas.save(); canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2); canvas.scale(scale, scale)
        val recovery = if (reduced || previewTime != null || touching.value || released.doubleValue < 0 || elapsed.doubleValue - released.doubleValue >= 2.6) 1.0 else 1 - ease((elapsed.doubleValue - released.doubleValue - 2) / 0.6)
        painter.draw(canvas, frameTime.doubleValue, recovery)
        canvas.restore()
    }
}

private class CashbackConfettiPainter {
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
    fun draw(c: NativeCanvas,t: Double,r: Double) {
        gradient(c,roundRect(0.0,0.0,393.0,852.0,0.0),listOf("#050207","#0B030F","#241027"),listOf(196.0,100.0),listOf(196.0,852.0))
        val event=ease((t-1.2)/0.35)*(1-ease((t-3)/0.5))*r;val caption=ease((t-1.75)/0.4)*(1-ease((t-3.9)/0.6))*r
        label(c,"PERKS / REWARDS",196.5,126.0,11.0,"#997B9E");label(c,"A little extra.",196.5,190.0,29.0,"#F1DBF5",true);label(c,"From your everyday moments.",196.5,229.0,14.0,"#997B9E")
        for(wave in 0..<2){val u=(t-1.52-wave*0.16)/1.35;if(u>0&&u<1){val q=1-(1-u).pow(3);val alpha=ease(u/0.09)*(1-u).pow(2)*0.62*r;stroke(c,roundRect(133.5-48*q,337-22*q,126+96*q,46+44*q,23+22*q),"#C453CE",0.8,alpha)}}
        val anticipation=sin(PI*((t-1.05)/0.2).coerceIn(0.0,1.0));val age=max(0.0,t-1.25)
        val impulse=sin(age*17)*exp(-age*5.2)*ease(age/0.08)*(1-ease((age-0.75)/0.3));val kick=(-0.025*anticipation*anticipation+0.15*impulse)*r
        c.save();c.translate(196.5f,360f);c.scale((1+kick).toFloat(),(1+kick).toFloat());c.translate(-196.5f,-360f)
        glow(c,roundRect(134.0,337.0,125.0,46.0,23.0),"#AA2DB7",16.0,0.11+0.16*event);fill(c,roundRect(133.5,337.0,126.0,46.0,23.0),"#100512");stroke(c,roundRect(133.5,337.0,126.0,46.0,23.0),"#A448AD",1.2)
        c.save();c.rotate((-14-9*impulse*r).toFloat(),154f,360f);fill(c,roundRect(140.0,343.0,28.0,35.0,2.0),"#7B258D");fill(c,roundRect(142.0,341.0,24.0,33.0,1.5),"#F7C0FA");stroke(c,roundRect(145.0,344.0,18.0,27.0,0.5),"#C16CCE",0.5);label(c,"$",154.0,358.0,20.0,"#80278F",true);c.restore()
        val value=45*ease((t-1.25)/0.7)*(1-ease((t-3.9)/0.6))*r;val expanded=ease(value/10)
        c.save();c.clipRect(174f,344f,248f,376f);label(c,"$",199-9*expanded,360.0,22.0,"#DE57E8",true)
        for(column in 0..<2){val turns=if(column==0)floor(value/10)+ease(value%10-9) else value;val digit=floor(turns).toInt()%10;val offset=turns-floor(turns);val x=if(column==0)205.0 else 218.0
            for(row in 0..1){val number=(digit+row)%10;if(column==0&&number==0)continue;label(c,number.toString(),x,360+(row-offset)*32,22.0,"#DE57E8",true)}}
        c.restore();c.restore()
        for(i in 0..<22){val seed=hash(i+2.0);val q=(t-1.43-i%5*0.028)/(1.25+0.25*seed);if(q>0&&q<1){val side=if(i%2==0)-1.0 else 1.0;val u=(1-exp(-3*q))/(1-exp(-3.0));val x=196.5+side*(29+(77+38*seed)*u);val y=360-(78+45*seed)*q+(100+36*hash(i+31.0))*q*q
            val alpha=ease(q/0.055)*(1-ease((q-0.62)/0.38))*r;val flutter=cos(q*(12+9*seed)+seed*6.28);val w=(3+3*seed)*(0.18+0.82*abs(flutter));val h=8+5*hash(i+23.0)
            c.save();c.translate(x.toFloat(),y.toFloat());c.rotate((side*(q*4+seed*2)*180/PI).toFloat());val p=Path().apply{moveTo((-w/2).toFloat(),(-h/2).toFloat());quadTo(0f,(-h/2-3*flutter).toFloat(),(w/2).toFloat(),(-h/2).toFloat());lineTo((w/2).toFloat(),(h/2).toFloat());quadTo(0f,(h/2-3*flutter).toFloat(),(-w/2).toFloat(),(h/2).toFloat());close()}
            gradient(c,p,if(flutter>0)listOf("#FEA8FF","#BF3BD0","#6E217F") else listOf("#7C278F","#DD65E8","#A839BA"),listOf(-w,0.0),listOf(w,0.0),alpha);stroke(c,p,"#FFD3FF",0.35,alpha*max(0.0,flutter)*0.65);c.restore()}}
        label(c,"LIFETIME CASHBACK",196.5,445-16*caption,11.0,"#997B9E");label(c,"$1,367 earned so far",196.5,476-16*caption,14.0,"#E1ABE6");fill(c,roundRect(42.0,652.0,309.0,54.0,27.0),"#E9B7EF");label(c,"Claim your cashback",196.5,679.0,15.0,"#36103E",true);label(c,"GOOD THINGS ADD UP",196.5,745.0,11.0,"#997B9E")
    }
}
