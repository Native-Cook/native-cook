package com.ngocdevv.nativecook.blocks.splash.particlefoldsplash

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

private const val LOOP = 8.0
private fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

/** One self-contained splash. previewTime supports deterministic still previews. */
@Composable
fun ParticleFoldSplash(autoPlay: Boolean = false, previewTime: Double? = null) {
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
    LaunchedEffect(active, reduced, autoPlay, previewTime) {
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: 4.8; return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsed.doubleValue += (now - previous) / 1e9; previous = now
                val age = elapsed.doubleValue - released.doubleValue
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    released.doubleValue >= 0 && age < 2 -> held.doubleValue
                    released.doubleValue >= 0 && age < 2.6 -> held.doubleValue * (1 - ease((age - 2) / 0.6))
                    released.doubleValue >= 0 -> if (autoPlay) (age - 2.6) % LOOP else 0.0
                    autoPlay -> elapsed.doubleValue % LOOP
                    else -> 0.0
                }
            }
        }
    }
    val painter = remember { ParticleFoldSplashPainter() }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Forme. Small pieces. A bigger picture. Drag to fold the particles." }
        .pointerInput(reduced, previewTime) {
            if (reduced || previewTime != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touching.value = true
                held.doubleValue = (down.position.x / size.width * 7.4).coerceIn(0.0, 7.4)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                var last = down.position
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        held.doubleValue = (last.x / size.width * 7.4).coerceIn(0.0, 7.4)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (abs(last.x - down.position.x) < 8 * density) held.doubleValue = 4.8
                } finally { touching.value = false; released.doubleValue = elapsed.doubleValue }
            }
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.parseColor("#060708"))
        val scale = min(size.width / 393f, size.height / 852f)
        canvas.save(); canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2); canvas.scale(scale, scale)
        painter.draw(canvas, frameTime.doubleValue)
        canvas.restore()
    }
}

private class ParticleFoldSplashPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    fun draw(c: NativeCanvas,t: Double) {
        val fold=ease((t-0.6)/2)*(1-ease((t-5)/2.4))
        val turn=ease((t-2.6)/4.8)*PI*2;val yaw=-0.55*fold+turn;val pitch=-0.35*fold
        for(face in 0..5)for(row in 0..8)for(col in 0..8){
            val u=col/4.0-1;val v=row/4.0-1;var x=u;var y=v;var z=-1.0
            when(face){1->z=1.0;2->{x=-1.0;z=u};3->{x=1.0;z=u};4->{y=-1.0;z=v};5->{y=1.0;z=v}}
            val index=face*81+row*9+col;val flatX=(index%27-13)*8.0;val flatY=(index/27-8.5)*8
            x=flatX*(1-fold)+x*68*fold;y=flatY*(1-fold)+y*68*fold;z*=68*fold
            val rx=x*cos(yaw)+z*sin(yaw);val rz=-x*sin(yaw)+z*cos(yaw)
            val ry=y*cos(pitch)-rz*sin(pitch);val depth=y*sin(pitch)+rz*cos(pitch);val perspective=400/(400+depth)
            val near=(0.5-depth/240).coerceIn(0.0,1.0);val edge=if(row==0||row==8||col==0||col==8)fold else 0.0
            val radius=0.9+near*0.7+0.35*edge
            fill(c,circle(196.5+rx*perspective,350+ry*perspective,radius),"#FFFFFF",min(1.0,0.18+0.72*near+0.18*edge))
        }
        label(c,"forme",196.5,175.0,34.0,"#FFFFFF",true);label(c,"EVERY DETAIL FINDS ITS PLACE",196.5,488.0,11.0,"#85888F")
        label(c,"Small pieces.",196.5,568.0,30.0,"#FFFFFF",true);label(c,"A bigger picture.",196.5,605.0,30.0,"#FFFFFF",true)
        label(c,"Give your next idea a little dimension.",196.5,649.0,14.0,"#A5A5AE")
        fill(c,roundRect(28.0,700.0,337.0,54.0,27.0),"#FFFFFF");label(c,"Bring it together",196.5,727.0,15.0,"#060708",true)
        label(c,"Made of possibilities",196.5,783.0,11.0,"#85888F")
    }

}
