package com.ngocdevv.nativecook.blocks.inputs.sendamplifypreview

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private val Names = listOf("Amplify", "Lift", "Whisper", "Drift")
private fun nowSeconds() = System.nanoTime() / 1e9
private fun ease(raw: Double): Double {
    val u = raw.coerceIn(0.0, 1.0)
    return u * u * (3 - 2 * u)
}
private fun rounded(value: Double) = floor(value + 0.5)

private data class SendAmplifyPreviewPose(
    val scale: Double = .16,
    val right: Double = 324.0,
    val bodyY: Double = 461.0,
    val focus: Double = 3.0,
    val railFocus: Double = 3.0,
    val selection: Double = 0.0,
    val rotation: Double = 0.0,
    val stretch: Double = 1.0,
    val ink: Double = 0.0,
    val impact: Double = 0.0,
    val impactProgress: Double = 0.0,
) { val selected get() = rounded(focus).toInt().coerceIn(0, 3) }

private data class SendAmplifyPreviewFrame(val time: Double, val width: Double, val height: Double, val bodyY: Double, val right: Double, val rotation: Double = 0.0)
private val WhisperFrames = listOf(
    SendAmplifyPreviewFrame(0.0,.5,.4,416.0,332.0),
    SendAmplifyPreviewFrame(.05,5.8,4.4,415.6,331.9),
    SendAmplifyPreviewFrame(.25,34.9,24.7,370.4,331.9),
    SendAmplifyPreviewFrame(.55,50.9,37.8,362.4,329.0),
    SendAmplifyPreviewFrame(1.15,50.9,37.8,362.4,329.0),
)
private val LiftFrames = listOf(
    SendAmplifyPreviewFrame(0.0,.5,.4,375.0,334.8),
    SendAmplifyPreviewFrame(.0667,11.6,8.7,366.8,334.8),
    SendAmplifyPreviewFrame(.30,74.2,55.3,299.8,327.5),
    SendAmplifyPreviewFrame(.5667,93.2,68.4,290.4,324.6),
    SendAmplifyPreviewFrame(.7333,93.2,68.4,290.4,324.6),
    SendAmplifyPreviewFrame(1.2833,40.8,30.6,315.1,330.4),
    SendAmplifyPreviewFrame(1.45,40.8,30.6,315.1,330.4),
)
private val AmplifyFrames = listOf(
    SendAmplifyPreviewFrame(0.0,40.8,30.6,315.1,330.4),
    SendAmplifyPreviewFrame(.0167,141.2,116.4,333.3,314.4,12.1),
    SendAmplifyPreviewFrame(.05,189.2,155.7,334.1,309.0,11.5),
    SendAmplifyPreviewFrame(.10,240.2,195.0,329.0,302.0,9.5),
    SendAmplifyPreviewFrame(.1667,285.3,225.6,312.2,295.5,5.0),
    SendAmplifyPreviewFrame(.20,260.5,203.8,299.8,299.8,3.0),
    SendAmplifyPreviewFrame(.25,206.7,157.2,283.8,307.0,1.36),
    SendAmplifyPreviewFrame(.30,133.9,101.9,273.6,320.0,-1.1),
    SendAmplifyPreviewFrame(.3333,64.0,48.0,267.1,329.0),
    SendAmplifyPreviewFrame(.35,37.8,27.7,262.7,331.9),
    SendAmplifyPreviewFrame(.45,40.8,30.6,264.2,330.4),
    SendAmplifyPreviewFrame(1.10,40.8,30.6,264.2,330.4),
)

/** Clamped Hermite interpolation preserves each measured rendered-body knot. */
private fun sample(frames: List<SendAmplifyPreviewFrame>, time: Double): SendAmplifyPreviewFrame {
    val i = (0 until frames.lastIndex).firstOrNull { time < frames[it + 1].time } ?: return frames.last()
    val a = frames[i]; val b = frames[i + 1]
    val previous = frames[max(0,i - 1)]; val next = frames[min(frames.lastIndex,i + 2)]
    val duration = b.time - a.time; val u = ((time - a.time) / duration).coerceIn(0.0,1.0)
    fun v(get: (SendAmplifyPreviewFrame) -> Double): Double {
        val x = get(a); val y = get(b)
        if (x == y) return x
        val m0 = if (i == 0) 0.0 else (y - get(previous)) / (b.time - previous.time)
        val m1 = if (i + 2 == frames.size) 0.0 else (get(next) - x) / (next.time - a.time)
        val value = (2*u*u*u-3*u*u+1)*x + (u*u*u-2*u*u+u)*m0*duration + (-2*u*u*u+3*u*u)*y + (u*u*u-u*u)*m1*duration
        return value.coerceIn(min(x,y),max(x,y))
    }
    return SendAmplifyPreviewFrame(time,v { it.width },v { it.height },v { it.bodyY },v { it.right },v { it.rotation })
}
private fun mix(a: SendAmplifyPreviewPose, b: SendAmplifyPreviewPose, p: Double): SendAmplifyPreviewPose {
    fun v(x: Double,y: Double) = x + (y-x)*p
    return SendAmplifyPreviewPose(v(a.scale,b.scale),v(a.right,b.right),v(a.bodyY,b.bodyY),v(a.focus,b.focus),v(a.railFocus,b.railFocus),v(a.selection,b.selection),v(a.rotation,b.rotation),v(a.stretch,b.stretch),v(a.ink,b.ink),v(a.impact,b.impact),v(a.impactProgress,b.impactProgress))
}

/** Every row restarts an independent event; row position never drives numeral strength. */
private fun eventPose(row: Int, raw: Double): SendAmplifyPreviewPose {
    val t = max(0.0,raw)
    if (row == 3) return SendAmplifyPreviewPose(scale=.14,bodyY=409.7,right=334.5,focus=3.0,railFocus=3.0,selection=1.0,ink=ease(t/.16))
    val values = sample(if(row == 2) WhisperFrames else if(row == 1) LiftFrames else AmplifyFrames,t)
    val angle = abs(values.rotation)*PI/180; val c = cos(angle); val sn = sin(angle)
    val determinant = max(.1,c*c-sn*sn)
    val sx = max(.001,(values.width*c-values.height*sn)/(270*determinant))
    val sy = max(.001,(values.height*c-values.width*sn)/(200*determinant))
    return SendAmplifyPreviewPose(scale=sx,stretch=sy/sx,bodyY=values.bodyY,right=values.right,rotation=values.rotation,
        focus=row.toDouble(),railFocus=if(row==0) 1-ease(t/.17) else row.toDouble(),selection=1.0,
        impact=if(row==0) ease((t-.35)/.10)*(1-ease((t-.48)/.27)) else 0.0,
        impactProgress=if(row==0) ease((t-.35)/.23) else 0.0)
}
private fun loopPose(raw: Double): SendAmplifyPreviewPose {
    val t = ((raw%LOOP)+LOOP)%LOOP
    return when {
        t<1.1 || t>=7 -> SendAmplifyPreviewPose()
        t<1.35 -> mix(SendAmplifyPreviewPose(),eventPose(3,.25),ease((t-1.1)/.25))
        t<2.95 -> eventPose(3,t-1.1)
        t<4.1 -> eventPose(2,t-2.95)
        t<5.55 -> eventPose(1,t-4.1)
        t<6.65 -> eventPose(0,t-5.55)
        else -> mix(eventPose(0,1.10),SendAmplifyPreviewPose(),ease((t-6.65)/.35))
    }
}

private class SendAmplifyPreviewMotion {
    var frame by mutableDoubleStateOf(nowSeconds())
    var manualLive by mutableStateOf(false)
    var epoch = nowSeconds()
    var lastTouch = Double.NEGATIVE_INFINITY
    var captured = SendAmplifyPreviewPose()
    var eventStarted = Double.NEGATIVE_INFINITY
    var releaseFocus = 3.0
    var dragStartFocus = 3.0
    var manualRow = 3
    var dragging = false
    var activated = false
    var hasSelection = false
    var lastHaptic = Double.NEGATIVE_INFINITY
    var hapticIndex = 3
    var resume: Job? = null
    fun pose(reduced: Boolean,autoPlay: Boolean,previewTime: Double?): SendAmplifyPreviewPose {
        val now = frame
        if(reduced) return SendAmplifyPreviewPose(focus=manualRow.toDouble(),railFocus=manualRow.toDouble(),selection=if(hasSelection) 1.0 else 0.0)
        if(previewTime!=null) return loopPose(previewTime)
        if(manualLive) {
            if(dragging && !activated) return captured
            val age = max(0.0,now-eventStarted)
            val shown = mix(captured,eventPose(manualRow,age),ease(age/.1))
            if(dragging) return shown
            val elapsed = now-lastTouch
            if(elapsed<2.6) return mix(shown,SendAmplifyPreviewPose(),ease((elapsed-2)/.6))
        }
        if(!autoPlay) return SendAmplifyPreviewPose()
        return loopPose(if(lastTouch==Double.NEGATIVE_INFINITY) now-epoch else now-lastTouch-2.6)
    }
}

/** Four independent message effects share a cached beveled numeral and glass controls. */
@Composable
fun SendAmplifyPreview(modifier: Modifier = Modifier, autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced = sendAmplifyPreviewReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val c = remember { SendAmplifyPreviewMotion() }
    val painter = remember(density) { SendAmplifyPreviewPainter(density) }
    val background = remember(painter) { painter.background.asImageBitmap() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c, autoPlay, reduced, previewTime, c.manualLive) {
        if (reduced || previewTime != null || (!autoPlay && !c.manualLive)) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    DisposableEffect(c) { onDispose { c.resume?.cancel() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }

    fun resumeLater() {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2650)
            if (!c.dragging) { c.manualLive=false; c.activated=false; c.frame=nowSeconds() }
        }
    }
    fun tick(index: Int,at: Double) {
        if(index!=c.hapticIndex && at-c.lastHaptic>.12) {
            c.hapticIndex=index; c.lastHaptic=at
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    fun beginEvent(row: Int) {
        c.frame=nowSeconds()
        val shown=c.pose(reduced,autoPlay,previewTime)
        c.captured=shown; c.manualRow=row; c.hasSelection=true; c.releaseFocus=row.toDouble()
        c.eventStarted=c.frame; c.activated=true; c.manualLive=true
        tick(row,c.frame)
    }
    fun select(focus: Double) {
        c.resume?.cancel(); beginEvent(rounded(focus).toInt().coerceIn(0,3))
        c.lastTouch=nowSeconds(); c.frame=c.lastTouch; resumeLater()
    }
    fun amplify() { select(0.0) }
    fun reset() {
        c.resume?.cancel(); c.captured=SendAmplifyPreviewPose(); c.releaseFocus=3.0
        c.manualRow=3; c.hasSelection=false; c.activated=false; c.lastTouch=Double.NEGATIVE_INFINITY
        c.epoch=nowSeconds(); c.manualLive=false; c.frame=nowSeconds()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFD6D6E2)), contentAlignment = Alignment.Center) {
        val fit = min(maxWidth.value / 393, maxHeight.value / 852)
        Box(Modifier.requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = fit; scaleY = fit }) {
            Image(background, null, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.draw(canvas, c.pose(reduced, autoPlay, previewTime), reduced)
                canvas.restore()
            }
            Box(Modifier.offset(12.dp, 184.dp).size(369.dp, 330.dp)
                .semantics {
                    contentDescription = "Message effect"
                    val pose = c.pose(reduced, autoPlay, previewTime)
                    stateDescription = Names[pose.selected]
                    progressBarRangeInfo = ProgressBarRangeInfo(pose.focus.toFloat().coerceIn(0f, 3f), 0f..3f, 2)
                    setProgress { select(it.toDouble().coerceIn(0.0, 3.0)); true }
                }
                .pointerInput(c, reduced, previewTime) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        c.resume?.cancel(); c.frame=nowSeconds()
                        c.captured=c.pose(reduced,autoPlay,previewTime)
                        c.dragStartFocus=c.captured.focus; c.releaseFocus=rounded(c.captured.focus)
                        c.activated=false; c.dragging=true; c.manualLive=true
                        val direct=down.position.x/density+12>330
                        fun move(x: Float,y: Float) {
                            val dx=(x-down.position.x)/density; val dy=(y-down.position.y)/density
                            val target=if(direct) (y/density+184-251)/52.0 else c.dragStartFocus+dy/52
                            c.releaseFocus=rounded(target.coerceIn(0.0,3.0))
                            if(max(abs(dx),abs(dy))>6 && (!c.activated || c.manualRow!=c.releaseFocus.toInt())) beginEvent(c.releaseFocus.toInt())
                            c.lastTouch=nowSeconds(); c.frame=c.lastTouch
                        }
                        move(down.position.x,down.position.y)
                        try {
                            while(true) {
                                val change=awaitPointerEvent().changes.firstOrNull { it.id==down.id } ?: break
                                if(!change.pressed) break
                                move(change.position.x,change.position.y); change.consume()
                            }
                        } finally {
                            if(!c.activated) beginEvent(c.releaseFocus.toInt())
                            c.dragging=false; c.lastTouch=nowSeconds(); c.frame=c.lastTouch; resumeLater()
                        }
                    }
                })
            Box(Modifier.offset(321.dp, 717.dp).size(44.dp)
                .semantics { contentDescription = "Preview amplify effect" }
                .clickable(interactionSource = null, indication = null, role = Role.Button) { amplify() })
            Box(Modifier.offset(337.dp, 441.dp).size(44.dp)
                .semantics { contentDescription = "Reset effect preview" }
                .clickable(interactionSource = null, indication = null, role = Role.Button) { reset() })
        }
    }
}

/** Static conversation and glass surfaces are rendered once, never rebuilt by the frame clock. */
private class SendAmplifyPreviewPainter(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false) else Typeface.create("sans-serif", Typeface.BOLD)
    val background = Bitmap.createBitmap((393 * density).roundToInt(), (852 * density).roundToInt(), Bitmap.Config.ARGB_8888).also {
        val canvas = AndroidCanvas(it)
        canvas.scale(density, density)
        static(canvas)
    }

    private fun color(hex: Int, alpha: Double = 1.0) = AndroidColor.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun fill(hex: Int, alpha: Double = 1.0) {
        paint.shader = null; paint.maskFilter = null; paint.xfermode = null; paint.clearShadowLayer()
        paint.style = Paint.Style.FILL; paint.color = color(hex, alpha)
    }
    private fun rect(c: AndroidCanvas, x: Float, y: Float, w: Float, h: Float, r: Float, hex: Int, alpha: Double = 1.0) {
        fill(hex, alpha); c.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
    private fun disc(c: AndroidCanvas, x: Float, y: Float, radius: Float, hex: Int, alpha: Double = 1.0) {
        fill(hex, alpha); c.drawCircle(x, y, radius, paint)
    }
    private fun label(c: AndroidCanvas, words: String, x: Float, y: Float, size: Float, hex: Int, weight: Typeface = normal, tracking: Float = 0f, alpha: Double = 1.0, right: Boolean = false) {
        text.shader = null; text.xfermode = null; text.maskFilter = null
        text.color = color(hex, alpha); text.textSize = size; text.typeface = weight
        text.letterSpacing = tracking / size
        val width = text.measureText(words)
        val left = if (right) x - width else x - width / 2
        val baseline = y - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2
        c.drawText(words, left, baseline, text)
    }
    private fun line(c: AndroidCanvas, x1: Float, y1: Float, x2: Float, y2: Float, hex: Int, width: Float, alpha: Double = 1.0) {
        fill(hex, alpha); paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1, y1, x2, y2, paint)
    }
    private fun softOval(c: AndroidCanvas, box: RectF, hex: Int, alpha: Double, blur: Float) {
        fill(hex, alpha); paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(box, paint); paint.maskFilter = null
    }

    private fun static(c: AndroidCanvas) {
        fill(0xFFFFFF)
        paint.shader = LinearGradient(0f, 0f, 0f, 852f, intArrayOf(color(0xC3BFC5), color(0xE9ECF4), color(0xDED9D7), color(0xD0C1BA), color(0xD6D6E2)), floatArrayOf(0f,.35f,.54f,.76f,1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 393f, 852f, paint)
        softOval(c, RectF(0f,79f,300f,249f), 0x8B8580, .23, 40f)
        softOval(c, RectF(242f,120f,402f,300f), 0xCBDCF2, .33, 48f)
        softOval(c, RectF(-4f,257.5f,306f,442.5f), 0xF7F6FA, .45, 42f)
        softOval(c, RectF(70f,565f,380f,715f), 0xBFAE9B, .23, 40f)

        // A cached transparent layer reproduces the blurred, deliberately unreadable conversation.
        // One pixel per design point is sufficient for this deliberately defocused layer.
        val conversation = Bitmap.createBitmap(393, 852, Bitmap.Config.ARGB_8888)
        val cc = AndroidCanvas(conversation)
        disc(cc, 196.5f, 88f, 17f, 0xBBC3C9)
        label(cc, "NV", 196.5f, 88f, 10f, 0x636E76, semibold)
        label(cc, "Nori Vale", 196.5f, 117f, 12f, 0x555C61, medium)
        line(cc, 29f, 91f, 23f, 97f, 0x66737C, 1.5f); line(cc, 23f, 97f, 29f, 103f, 0x66737C, 1.5f)
        rect(cc, 26f, 246f, 194f, 61f, 18f, 0xF9FAFA)
        label(cc, "A number for the", 104f, 266f, 14f, 0x637078)
        label(cc, "good days.", 77f, 283f, 14f, 0x637078)
        rect(cc, 239f, 568f, 128f, 48f, 20f, 0xAABFCB)
        label(cc, "Saved that one.", 303f, 592f, 13f, 0x637887)
        rect(cc, 41f, 329f, 57f, 5f, 2.5f, 0xA8B6BD, 0.65)
        rect(cc, 40f, 343f, 105f, 4f, 2f, 0xB7C1C4, 0.65)
        blurConversation(conversation, radius = 20)
        fill(0xFFFFFF, 0.13)
        paint.isFilterBitmap = true
        c.drawBitmap(conversation, null, RectF(0f, 0f, 393f, 852f), paint)
        conversation.recycle()

        label(c, "Send with feeling", 196.5f, 93f, 17f, 0x777478)
        rect(c, 87f, 118f, 220f, 29f, 7f, 0x9EA0A9, .28)
        rect(c, 89f, 121f, 106f, 25f, 6f, 0x000000, .06)
        rect(c, 89f, 120f, 106f, 25f, 6f, 0xFFFFFF, .96)
        label(c, "Bubble", 142f, 132.5f, 11f, 0x343339, semibold)
        label(c, "Scene", 252f, 132.5f, 11f, 0x343339, medium)
        softOval(c, RectF(24f, 719f, 369f, 769f), 0x5A6570, 0.06, 10f)
        rect(c, 24f, 714f, 345f, 50f, 25f, 0xFFFFFF, 0.48)
        fill(0xFFFFFF, 0.52); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        c.drawRoundRect(24f, 714f, 369f, 764f, 25f, 25f, paint)
        disc(c, 47f, 739f, 12f, 0x7C8A94, 0.09)
        line(c, 41f, 739f, 53f, 739f, 0x78848C, 1f); line(c, 47f, 733f, 47f, 745f, 0x78848C, 1f)
        label(c, "60", 84f, 739f, 16f, 0x4F5A62, medium)
        label(c, "Message", 142f, 739f, 12f, 0x9AA4AB)
        softOval(c, RectF(327f, 725f, 359f, 757f), 0x3983D8, 0.18, 5f)
        disc(c, 343f, 739f, 16f, 0x287AE0)
        line(c, 343f, 746f, 343f, 732f, 0xFFFFFF, 1.8f)
        line(c, 337f, 738f, 343f, 732f, 0xFFFFFF, 1.8f); line(c, 343f, 732f, 349f, 738f, 0xFFFFFF, 1.8f)
        label(c, "Preview only · no message is sent", 196.5f, 789f, 10f, 0x8A9095)
    }

    /** Three separable box passes blur premultiplied RGBA, including all interior text edges. */
    private fun blurConversation(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        val temporary = IntArray(pixels.size)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            pixels[i] = (a shl 24) or (((p ushr 16 and 255) * a / 255) shl 16) or (((p ushr 8 and 255) * a / 255) shl 8) or ((p and 255) * a / 255)
        }
        val diameter = radius * 2 + 1
        fun pass(source: IntArray, target: IntArray, horizontal: Boolean) {
            val count = if (horizontal) h else w
            val length = if (horizontal) w else h
            val step = if (horizontal) 1 else w
            for (line in 0 until count) {
                val base = if (horizontal) line * w else line
                var a = 0; var r = 0; var g = 0; var b = 0
                fun add(p: Int, sign: Int) {
                    a += (p ushr 24) * sign
                    r += (p ushr 16 and 255) * sign
                    g += (p ushr 8 and 255) * sign
                    b += (p and 255) * sign
                }
                for (offset in -radius..radius) add(source[base + offset.coerceIn(0, length - 1) * step], 1)
                for (position in 0 until length) {
                    target[base + position * step] = ((a / diameter) shl 24) or ((r / diameter) shl 16) or ((g / diameter) shl 8) or (b / diameter)
                    add(source[base + (position - radius).coerceIn(0, length - 1) * step], -1)
                    add(source[base + (position + radius + 1).coerceIn(0, length - 1) * step], 1)
                }
            }
        }
        repeat(3) { pass(pixels, temporary, true); pass(temporary, pixels, false) }
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            pixels[i] = if (a == 0) 0 else (a shl 24) or (min(255, (p ushr 16 and 255) * 255 / a) shl 16) or (min(255, (p ushr 8 and 255) * 255 / a) shl 8) or min(255, (p and 255) * 255 / a)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private val numeral by lazy { makeNumeral() }
    private val rail by lazy { bitmap(393, 852, 2) { c ->
        fill(0x000000, .035); paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(338f, 232f, 380f, 430f, 21f, 21f, paint)
        rect(c, 338f, 230f, 42f, 198f, 21f, 0xFFFFFF, .82)
        fill(0xFFFFFF, .72); paint.style = Paint.Style.STROKE; paint.strokeWidth = .7f
        c.drawRoundRect(338f,230f,380f,428f,21f,21f,paint)
        disc(c,359f,463f,11f,0x64626A)
        line(c,356f,460f,362f,466f,0xFFFFFF,1.2f); line(c,356f,466f,362f,460f,0xFFFFFF,1.2f)
    } }
    private fun bitmap(w: Int, h: Int, scale: Int, draw: (AndroidCanvas) -> Unit): Bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888).also {
        AndroidCanvas(it).apply { scale(scale.toFloat(), scale.toFloat()); draw(this) }
    }
    private fun image(c: AndroidCanvas, bitmap: Bitmap, rect: RectF, alpha: Double = 1.0) {
        fill(0xFFFFFF, alpha); paint.isFilterBitmap = true; c.drawBitmap(bitmap, null, rect, paint)
    }
    private fun gradient(c: AndroidCanvas, path: Path, colors: IntArray, positions: FloatArray, x1: Float, y1: Float, x2: Float, y2: Float) {
        fill(0xFFFFFF); paint.shader = LinearGradient(x1,y1,x2,y2,colors,positions,Shader.TileMode.CLAMP); c.drawPath(path,paint)
    }
    private fun hash(index: Int): Double { val value = sin(index * 12.9898) * 43758.5453; return value - floor(value) }
    private fun makeNumeral(): Bitmap = bitmap(296,300,3) { c ->
        val glyph=Path().apply {
            fillType=Path.FillType.EVEN_ODD
            moveTo(72f,0f); lineTo(112f,0f); lineTo(57f,77f)
            cubicTo(65f,73f,74f,71f,83f,72f); cubicTo(115f,75f,134f,99f,134f,132f)
            cubicTo(135f,172f,108f,200f,67f,200f); cubicTo(26f,200f,0f,176f,0f,141f)
            cubicTo(0f,122f,7f,104f,17f,88f); lineTo(72f,0f); close()
            addOval(36f,111f,97f,169f,Path.Direction.CW)
            addOval(142f,0f,270f,200f,Path.Direction.CW)
            addOval(179f,35f,233f,165f,Path.Direction.CW)
            transform(Matrix().apply { setTranslate(4f,83f) })
        }
        fill(0x121116); paint.setShadowLayer(3f,2f,3f,color(0x151319,.22)); c.drawPath(glyph,paint)
        for(depth in 7 downTo 1) { c.save(); c.translate(depth*.65f,depth*.5f); fill(0x17151A); c.drawPath(glyph,paint); c.restore() }
        c.save(); c.translate(-1.5f,-1.4f); fill(0x6D686C); c.drawPath(glyph,paint); c.restore()
        gradient(c,glyph,intArrayOf(color(0x474347),color(0x19171B),color(0x0D0C10),color(0x211E23)),floatArrayOf(0f,.25f,.73f,1f),0f,83f,35f,283f)
        val cloth = Path().apply {
            moveTo(164f,76f); cubicTo(181f,43f,203f,1f,219f,-3f)
            cubicTo(231f,-7f,242f,4f,249f,15f); cubicTo(265f,38f,273f,65f,272f,91f)
            cubicTo(272f,98f,269f,103f,264f,102f); cubicTo(230f,88f,192f,73f,164f,76f); close()
        }
        fill(0x000000,.18); paint.setShadowLayer(2f,0f,2f,color(0x000000,.18)); c.drawPath(cloth,paint)
        gradient(c,cloth,intArrayOf(color(0x7E0712),color(0xE42C32),color(0xBA111D),color(0x7F0711)),floatArrayOf(0f,.36f,.71f,1f),163f,29f,274f,89f)
        c.save(); c.clipPath(cloth)
        val fold = Path().apply { moveTo(219f,-2f); cubicTo(234f,29f,241f,57f,247f,84f) }
        fill(0x57000B,.12); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; c.drawPath(fold,paint); c.restore()
        val cuff = Path().apply {
            moveTo(151f,66f); cubicTo(186f,64f,245f,81f,274f,96f)
            cubicTo(277f,108f,273f,129f,266f,139f); cubicTo(227f,122f,173f,116f,138f,108f)
            cubicTo(138f,94f,144f,76f,151f,66f); close()
        }
        fill(0x000000,.30); paint.setShadowLayer(4f,0f,5f,color(0x000000,.30)); c.drawPath(cuff,paint)
        gradient(c,cuff,intArrayOf(color(0xFFFDF8),color(0xE8E7ED),color(0x9B9BA6)),floatArrayOf(0f,.5f,1f),188f,72f,208f,129f)
        c.save(); c.clipPath(cuff)
        for (i in 0 until 180) {
            val x = (138 + hash(i + 11) * 138).toFloat(); val y = (64 + hash(i + 97) * 79).toFloat()
            fill(if(i % 3 == 0) 0x8F8C99 else 0xFFFFFC,if(i % 3 == 0) .05 else .16)
            c.drawOval(x,y,x + (.5 + hash(i + 201)).toFloat(),y + (2 + hash(i + 33) * 3).toFloat(),paint)
        }
        c.restore()
        // Fine, unequal fibers break the lower contour; the cuff is cloth, not a plastic band.
        for (i in 0 until 96) {
            val u = i / 95.0; val x = (138 + 128 * u).toFloat(); val y = (108 + 31 * u - 8 * sin(PI * u)).toFloat()
            val length = (2 + 8 * hash(i + 317)).toFloat()
            fill(if(i % 4 == 0) 0xB9B6C2 else 0xEAE8EF,.68)
            c.drawOval(x,y - 2,x + (.7 + hash(i + 99)).toFloat(),y - 2 + length,paint)
        }
    }
    private val ink by lazy { bitmap(52,54,3) { c ->
        fill(0x938488); paint.setShadowLayer(1.2f,0f,0f,color(0x8F8792,.35))
        c.drawRoundRect(3f,3f,47f,49f,11f,11f,paint)
        fill(0x938488)
        val tail=Path().apply { moveTo(41f,41f); quadTo(42f,47f,50f,50f); quadTo(41f,50f,36f,46f); close() }
        c.drawPath(tail,paint)
        for(i in 0 until 320) {
            val x=(6+hash(i+39)*37).toFloat(); val y=(6+hash(i+477)*39).toFloat()
            fill(if(i%2==0) 0xE6DEE8 else 0x655B68,.10); c.drawOval(x,y,x+.65f,y+.65f,paint)
        }
    } }
    private val impact by lazy { bitmap(132,132,2) { c ->
        fill(0xFFFFFF)
        paint.shader=RadialGradient(66f,66f,62f,intArrayOf(color(0x28212C,.34),color(0x302837,.24),color(0x302837,0.0)),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,132f,132f,paint)
    } }
    fun draw(c: AndroidCanvas, shown: SendAmplifyPreviewPose, reduced: Boolean) {
        image(c,rail,RectF(0f,0f,393f,852f))
        for(i in 0 until 4) {
            val y=251f+i*52
            disc(c,359f,y,4.2f,0xABA8B4)
            if(i!=shown.selected || shown.selection<.5) label(c,Names[i].uppercase(),322f,y,8.2f,0x89838F,semibold,alpha=.78,right=true)
        }
        val railY=(251+shown.railFocus*52).toFloat()
        disc(c,359f,railY,12f,0x087CFA,shown.selection)
        line(c,359f,railY+5,359f,railY-5,0xFFFFFF,1.6f,shown.selection)
        line(c,355f,railY-1,359f,railY-5,0xFFFFFF,1.6f,shown.selection)
        line(c,359f,railY-5,363f,railY-1,0xFFFFFF,1.6f,shown.selection)
        val pose=if(reduced) SendAmplifyPreviewPose() else shown
        val progress=pose.impactProgress
        val w=(63+47*progress).toFloat(); val h=(76+74*progress).toFloat()
        val x=(302-21*progress).toFloat(); val y=(259+5*progress).toFloat()
        if(pose.impact>0) image(c,impact,RectF(x-w/2,y-h/2,x+w/2,y+h/2),pose.impact)
        val scale=pose.scale; val angle=pose.rotation*PI/180; val sy=scale*pose.stretch
        val width=270*scale*cos(angle)+200*sy*abs(sin(angle))
        val cx=(pose.right-width/2+9*scale*cos(angle)+33*sy*sin(angle)).toFloat()
        val cy=(pose.bodyY+9*scale*sin(angle)-33*sy*cos(angle)).toFloat()
        if(pose.ink>0) image(c,ink,RectF((pose.right-49).toFloat(),(pose.bodyY-27).toFloat(),(pose.right+3).toFloat(),(pose.bodyY+27).toFloat()),pose.ink)
        label(c,"SEND WITH "+Names[shown.selected].uppercase(),319f,(223+shown.focus*52).toFloat(),8.2f,0x68646E,semibold,.25f,shown.selection*.65,right=true)
        c.save(); c.translate(cx,cy); c.rotate(pose.rotation.toFloat()); c.scale(1f,pose.stretch.toFloat())
        if(pose.ink<1) image(c,numeral,RectF((-148*scale).toFloat(),(-150*scale).toFloat(),(148*scale).toFloat(),(150*scale).toFloat()),1-pose.ink)
        c.restore()
    }

}

@Composable
private fun sendAmplifyPreviewReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
