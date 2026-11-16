package com.ngocdevv.nativecook.blocks.forms.receiptinkextract

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val LOOP = 8.0
private val receiptInkExtractStarts = doubleArrayOf(2.01474, 2.20682, 2.42403)
private fun receiptInkExtractSegment(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun receiptInkExtractOut(x: Double) = 1.0 - (1.0 - x).pow(3)
private fun receiptInkExtractEase(x: Double) = if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3) / 2.0
private fun receiptInkExtractInverse(x: Double) = if (x < 0.5) (x / 4.0).pow(1.0 / 3.0) else 1.0 - ((1.0 - x) / 4.0).pow(1.0 / 3.0)
private fun receiptInkExtractNow() = System.nanoTime() / 1_000_000_000.0
private fun Context.receiptInkExtractActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.receiptInkExtractActivity()
    else -> null
}
private class ReceiptInkExtractMotion {
    var epoch = receiptInkExtractNow()
    var lastTouch: Double? = null
    var dragging = false
    var scrub = 0.0
    var detent = -1
}

/** A local, deterministic receipt-to-ink transfer; no camera, OCR or network. */
@Composable
fun ReceiptInkExtract(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember { ReceiptInkExtractMotion() }
    val drawing = remember { ReceiptInkExtractDrawing() }
    var now by remember { mutableDoubleStateOf(receiptInkExtractNow()) }
    var interacting by remember { mutableStateOf(false) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    fun reduced() = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    var reducedMotion by remember { mutableStateOf(reduced()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reducedMotion = reduced() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view) {
        val controller = context.receiptInkExtractActivity()?.window?.let { WindowInsetsControllerCompat(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                oldStatus?.let { controller.isAppearanceLightStatusBars = it }
                oldNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
    LaunchedEffect(autoPlay, reducedMotion, interacting) {
        if (!reducedMotion && (autoPlay || interacting)) {
            while (isActive) withFrameNanos { now = it / 1_000_000_000.0 }
        }
    }
    Canvas(
        Modifier.fillMaxSize().background(Color(0xFFF3F1EB))
            .semantics {
                contentDescription = "Tallyleaf. Import receipt. Morrow bowl 12.80, Citrus tea 4.60, Oat square 7.20. Total 24.60. Drag down to lift the receipt ink into three rows."
            }
            .pointerInput(reducedMotion) {
                if (!reducedMotion) awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    resetJob?.cancel(); interacting = true; motion.dragging = true
                    fun update(y: Float) {
                        val scale = min(size.width / 393.0, size.height / 852.0)
                        val oy = (size.height - 852.0 * scale) / 2.0
                        motion.scrub = (((y - oy) / scale - 288.0) / 290.0).coerceIn(0.0, 1.0)
                        motion.lastTouch = receiptInkExtractNow()
                        val next = receiptInkExtractStarts.count { 1.0 + 2.2 * receiptInkExtractInverse(motion.scrub) >= it }
                        if (next > 0 && next != motion.detent) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); motion.detent = next }
                    }
                    update(down.position.y); down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) update(change.position.y)
                        change.consume()
                    } while (event.changes.any { it.id == down.id && it.pressed })
                    motion.dragging = false; motion.detent = -1
                    val released = receiptInkExtractNow(); motion.lastTouch = released
                    resetJob = scope.launch {
                        delay(2600)
                        if (!motion.dragging && motion.lastTouch == released) {
                            motion.epoch = receiptInkExtractNow(); motion.lastTouch = null; interacting = false; now = receiptInkExtractNow()
                        }
                    }
                }
            },
    ) {
        val age = motion.lastTouch?.let { now - it } ?: 100.0
        val returning = !motion.dragging && age > 2.0 && age < 2.6
        val touchMix = if (motion.lastTouch == null) 0.0 else if (motion.dragging || age <= 2.0) 1.0 else 1.0 - receiptInkExtractEase(receiptInkExtractSegment(age, 2.0, 2.6))
        val time = if (motion.lastTouch != null && age < 2.6) 1.0 + 2.2 * receiptInkExtractInverse(motion.scrub)
        else (if (motion.lastTouch == null) now - motion.epoch else (age - 2.6).coerceAtLeast(0.0)) % LOOP
        drawing.draw(drawContext.canvas.nativeCanvas, size.width, size.height, if (autoPlay || interacting) time else 0.0, reducedMotion, returning, touchMix)
    }
}

private class ReceiptInkExtractDrawing {
    private val ink = NativeColor.rgb(38, 60, 56)
    private val muted = NativeColor.rgb(124, 135, 128)
    private val coral = NativeColor.rgb(239, 115, 94)
    private val rim = NativeColor.rgb(213, 204, 181)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var globalAlpha = 1.0
    private val path = Path()
    private val paper = Path().apply {
        moveTo(-108f, -147f)
        for (i in 0 until 27) { lineTo(-104f + i * 8, -144f); lineTo(-100f + i * 8, -147f) }
        lineTo(108f, 147f)
        for (i in 0 until 27) { lineTo(104f - i * 8, 144f); lineTo(100f - i * 8, 147f) }
        close()
    }
    private val paperGradient = LinearGradient(-108f, -147f, 108f, 147f, intArrayOf(0xFFFFFDF3.toInt(), 0xFFF7F1DF.toInt(), 0xFFEAE1CB.toInt()), null, Shader.TileMode.CLAMP)
    private val foldGradient = LinearGradient(0f, -9f, 0f, 3f, intArrayOf(0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    private val fonts = mutableMapOf<String, Typeface>()
    private fun setup(color: Int, alpha: Double = 1.0, stroke: Float? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.alpha = (255 * (alpha * globalAlpha).coerceIn(0.0, 1.0)).toInt()
        paint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        if (stroke != null) { paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND }
    }
    private fun face(mono: Boolean, weight: Int): Typeface = fonts.getOrPut("$mono:$weight") {
        val base = Typeface.create(if (mono) "monospace" else "sans-serif", Typeface.NORMAL)
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun text(c: NativeCanvas, label: String, x: Double, y: Double, size: Float, color: Int = ink, weight: Int = 400, mono: Boolean = false, align: Paint.Align = Paint.Align.LEFT, alpha: Double = 1.0, tracking: Float = 0f, shadow: Double = 0.0, shadowRadius: Float = 2f, shadowY: Float = 0f) {
        setup(color, alpha); paint.textSize = size; paint.typeface = face(mono, weight); paint.textAlign = align
        if (shadow > 0) paint.setShadowLayer(shadowRadius, 0f, shadowY, NativeColor.argb((255 * shadow * alpha * globalAlpha).toInt(), 38, 60, 56))
        val fm = paint.fontMetrics; val baseline = y.toFloat() - (fm.ascent + fm.descent) / 2f
        if (tracking == 0f) c.drawText(label, x.toFloat(), baseline, paint)
        else {
            val widths = FloatArray(label.length); paint.getTextWidths(label, widths)
            val total = widths.sum() + tracking * (label.length - 1)
            var cursor = x.toFloat() - when (align) { Paint.Align.CENTER -> total / 2; Paint.Align.RIGHT -> total; else -> 0f }
            paint.textAlign = Paint.Align.LEFT
            label.forEachIndexed { i, letter -> c.drawText(letter.toString(), cursor, baseline, paint); cursor += widths[i] + tracking }
        }
    }
    private fun layer(c: NativeCanvas, alpha: Double, draw: () -> Unit) {
        val previous = globalAlpha
        globalAlpha *= alpha.coerceIn(0.0, 1.0)
        draw()
        globalAlpha = previous
    }
    fun draw(c: NativeCanvas, width: Float, height: Float, time: Double, reduced: Boolean, returning: Boolean, touchMix: Double) {
        val scale = min(width / 393f, height / 852f)
        c.save(); c.translate((width - 393f * scale) / 2f, (height - 852f * scale) / 2f); c.scale(scale, scale)
        text(c,"Tallyleaf",36.0,87.0,16f,weight=600,tracking=-0.3f)
        path.reset();path.moveTo(338f,91f);path.quadTo(331f,75f,349f,75f);path.quadTo(354f,88f,338f,91f);setup(coral);c.drawPath(path,paint)
        text(c,"IMPORT / 01",36.0,127.0,10f,muted,500,tracking=1.8f)
        text(c,"Import receipt",36.0,161.0,30f,weight=600,tracking=-1f)
        text(c,"One receipt. Three clear details.",36.0,190.0,13f,muted)
        text(c,"Drag down to lift the ink",196.5,704.0,12f,muted,500,align=Paint.Align.CENTER)
        text(c,"DEMO RECEIPT · ON DEVICE",196.5,729.0,9f,muted,500,true,Paint.Align.CENTER,0.75)
        c.translate(36.5f,228f)
        val q=DoubleArray(3) { i -> if(reduced) 1.0 else receiptInkExtractOut(receiptInkExtractSegment(time,receiptInkExtractStarts[i],receiptInkExtractStarts[i]+0.4))*(1-receiptInkExtractEase(receiptInkExtractSegment(time,5.2+i*0.06,6.68+i*0.06)))*(if(returning) touchMix else 1.0) }
        setup(muted,0.35,1.5f)
        for(corner in arrayOf(floatArrayOf(10f,28f,17f,17f),floatArrayOf(310f,28f,-17f,17f),floatArrayOf(10f,392f,17f,-17f),floatArrayOf(310f,392f,-17f,-17f))) {
            val(x,y,dx,dy)=corner;path.reset();path.moveTo(x,y+dy);path.lineTo(x,y);path.lineTo(x+dx,y);c.drawPath(path,paint)
        }
        c.save();c.translate(160f,211f);c.rotate(-12f)
        layer(c,1-0.68*q[2]) {
            c.save();c.translate(0f,2f);setup(rim);c.drawPath(paper,paint);c.restore()
            setup(0xFFF5F0DE.toInt());paint.setShadowLayer(18f,0f,16f,NativeColor.argb((33 * globalAlpha).toInt(),38,60,56));c.drawPath(paper,paint)
            setup(0xFFF7F1DF.toInt());paint.shader=paperGradient;paint.setShadowLayer(2f,0f,2f,NativeColor.argb((43 * globalAlpha).toInt(),38,60,56));c.drawPath(paper,paint)
            setup(rim,stroke=0.6f);c.drawPath(paper,paint)
        }
        layer(c,1-0.78*q[2]) {
            setup(ink,0.045,0.5f)
            for(i in 0 until 64) {val x=((i*67+19)%210-105).toFloat();val y=((i*43+11)%286-143).toFloat();c.drawLine(x,y,x+3+i%5,y+0.4f,paint)}
            setup(NativeColor.WHITE);paint.shader=foldGradient;c.drawRect(-107f,-9f,107f,3f,paint)
            text(c,"MORROW",0.0,-111.0,15f,weight=700,mono=true,align=Paint.Align.CENTER)
            text(c,"M A R K E T",0.0,-94.0,8f,muted,mono=true,align=Paint.Align.CENTER)
            text(c,"08 NOV 26 · 12:41",0.0,-74.0,8f,muted,mono=true,align=Paint.Align.CENTER)
            setup(muted,0.4,0.6f);paint.pathEffect=DashPathEffect(floatArrayOf(2f,3f),0f)
            for(y in floatArrayOf(-59f,89f)) c.drawLine(-88f,y,88f,y,paint)
            text(c,"ITEM",-88.0,-51.0,7f,muted,mono=true)
            text(c,"AMOUNT",88.0,-51.0,7f,muted,mono=true,align=Paint.Align.RIGHT)
            text(c,"TOTAL",-88.0,110.0,12f,weight=700,mono=true)
            text(c,"24.60",88.0,110.0,14f,weight=700,mono=true,align=Paint.Align.RIGHT)
            text(c,"A little good, every day.",0.0,129.0,7f,muted,mono=true,align=Paint.Align.CENTER)
        }
        c.restore()
        val names=arrayOf("Morrow bowl","Citrus tea","Oat square");val amounts=arrayOf("12.80","4.60","7.20")
        for(i in 0 until 3) {
            val p=q[i];val localY=-38.0+i*50;val radians=-12.0*PI/180
            val sx=160-sin(radians)*localY;val sy=211+cos(radians)*localY;val targetY=146.0+i*49
            layer(c,p*p) {
                val rect=RectF(28f,(targetY-21.5).toFloat(),292f,(targetY+21.5).toFloat())
                setup(0xFFF9F8F1.toInt());paint.setShadowLayer(5f,0f,3f,NativeColor.argb((20 * globalAlpha).toInt(),38,60,56));c.drawRoundRect(rect,9f,9f,paint)
                setup(muted,0.14,0.7f);c.drawRoundRect(rect,9f,9f,paint)
            }
            c.save();c.translate((sx+(160-sx)*p).toFloat(),(sy+(targetY-sy)*p-8*sin(PI*p)).toFloat());c.rotate((-12*(1-p)).toFloat())
            val shadow=0.13*sin(PI*p)
            text(c,names[i],-88.0,0.0,14f,weight=500,mono=true,shadow=shadow,shadowRadius=(2+3*p).toFloat(),shadowY=(4*sin(PI*p)).toFloat())
            text(c,amounts[i],88.0,0.0,14f,weight=500,mono=true,align=Paint.Align.RIGHT,shadow=shadow,shadowRadius=(2+3*p).toFloat(),shadowY=(4*sin(PI*p)).toFloat())
            c.restore()
            val a=receiptInkExtractSegment(p,0.9,1.0);setup(coral,a);c.drawCircle(268f,targetY.toFloat(),5f,paint)
            setup(NativeColor.WHITE,a,1f);path.reset();path.moveTo(265.5f,targetY.toFloat());path.lineTo(267.3f,(targetY+1.6).toFloat());path.lineTo(270.5f,(targetY-2).toFloat());c.drawPath(path,paint)
        }
        text(c,"3 details extracted",160.0,385.0,12f,weight=600,align=Paint.Align.CENTER,alpha=q[2])
        text(c,"24.60 total",160.0,405.0,11f,muted,mono=true,align=Paint.Align.CENTER,alpha=q[2])
        val scan=receiptInkExtractEase(receiptInkExtractSegment(time,1.0,3.2));val y=(60+290*scan).toFloat()
        val beam=if(reduced) 0.0 else receiptInkExtractSegment(time,1.0,1.15)*(1-receiptInkExtractSegment(time,3.05,3.2))*(if(returning) touchMix else 1.0)
        layer(c,beam) {
            setup(coral);paint.shader=LinearGradient(0f,y-28,0f,y,intArrayOf(0x00EF735E,0x1FEF735E),null,Shader.TileMode.CLAMP);c.drawRect(18f,y-28,302f,y,paint)
            setup(coral,0.22);paint.setShadowLayer(5f,0f,0f,NativeColor.argb((153 * globalAlpha).toInt(),239,115,94));c.drawRect(18f,y-2,302f,y+2,paint)
            setup(coral);c.drawRect(18f,y-0.75f,302f,y+0.75f,paint);c.drawCircle(18f,y,1.5f,paint);c.drawCircle(302f,y,1.5f,paint)
        }
        c.restore()
    }
}
