package com.ngocdevv.nativecook.blocks.onboarding.neoncardflight

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private fun neonNow() = System.nanoTime() / 1_000_000_000.0
private fun neonSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun neonEase(p: Double) = p * p * (3 - 2 * p)
private fun neonOut(p: Double) = 1 - (1 - p).pow(3)
private fun neonMix(a: Double, b: Double, p: Double) = a + (b - a) * p
private fun neonFract(p: Double) = p - floor(p)
private fun neonColor(hex: Int, alpha: Double = 1.0) = (hex and 0xFFFFFF) or ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24)
private data class NeonCardFlightFrame(val time: Double, val reset: Double)
private class NeonCardFlightMotion {
    var epoch = neonNow()
    var manual: Double? = null
    var released: Double? = null
    var dragging = false
    var grab = 0.0
    var threshold = false
    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, renderTime: Double?): NeonCardFlightFrame {
        if (renderTime != null) return NeonCardFlightFrame(renderTime % LOOP, 0.0)
        if (reduced) return NeonCardFlightFrame(5.4, 0.0)
        manual?.let { return NeonCardFlightFrame(it, released?.let { stamp -> neonEase(neonSeg(now - stamp, 2.0, 2.65)) } ?: 0.0) }
        return NeonCardFlightFrame(if (autoPlay) max(now - epoch, 0.0) % LOOP else 0.0, 0.0)
    }
}

/** The same card homography, cached sculpture and single loop clock as the reviewed Swift reference. */
@Composable
fun NeonCardFlight(autoPlay: Boolean = false, renderTime: Double? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberNeonCardFlightReducedMotion()
    val motion = remember { NeonCardFlightMotion() }
    val clock = remember { mutableDoubleStateOf(neonNow()) }
    var wake by remember { mutableIntStateOf(0) }
    val currentAutoPlay = rememberUpdatedState(autoPlay)
    val currentReduced = rememberUpdatedState(reduced)
    val currentRenderTime = rememberUpdatedState(renderTime)
    val painter = remember(context) { NeonCardFlightPainter(context) }
    DisposableEffect(painter) { onDispose { painter.close() } }
    NeonCardFlightSystemBars()
    LaunchedEffect(autoPlay, reduced, renderTime, wake) {
        clock.doubleValue = neonNow()
        if (renderTime != null || reduced || (!autoPlay && motion.manual == null)) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { clock.doubleValue = it / 1_000_000_000.0 }
            val released = motion.released
            if (!motion.dragging && released != null && clock.doubleValue - released >= 2.65) {
                motion.epoch = released + 2.65
                motion.manual = null
                motion.released = null
                if (!autoPlay) break
            }
        }
    }
    val finish: () -> Unit = {
        if (currentRenderTime.value == null) {
            val now = neonNow()
            motion.manual = 5.4; motion.released = now; motion.dragging = false
            clock.doubleValue = now; wake++
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFFFFDFF))) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 852f)
        val pixelScale = scale * density.density
        val left = (maxWidth.value - 393f * scale) / 2f
        val top = (maxHeight.value - 852f * scale) / 2f
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save(); c.translate(left * density.density, top * density.density); c.scale(pixelScale, pixelScale)
                c.clipRect(0f, 0f, 393f, 852f)
                val frame = motion.frame(clock.doubleValue, currentAutoPlay.value, currentReduced.value, currentRenderTime.value)
                painter.hero(c, frame.time, 1 - frame.reset)
                if (frame.reset > 0) painter.hero(c, 0.0, frame.reset)
                painter.static(c)
                c.restore()
            }
        }
        fun place(x: Float, y: Float, width: Float, height: Float) = Modifier
            .offset { IntOffset(((left + x * scale) * density.density).roundToInt(), ((top + y * scale) * density.density).roundToInt()) }
            .size((width * scale).dp, (height * scale).dp)
        fun adjust(delta: Double): Boolean {
            if (currentRenderTime.value != null || currentReduced.value) return false
            val now = neonNow()
            val time = motion.frame(now, currentAutoPlay.value, currentReduced.value, null).time
            motion.manual = (time + delta).coerceIn(0.0, 6.15); motion.released = now
            clock.doubleValue = now; wake++
            return true
        }
        Box(place(0f, 193f, 393f, 483f)
            .semantics {
                contentDescription = "Card flight. Drag right to fly the card and assemble the objects. Drag left to rewind."
                customActions = listOf(CustomAccessibilityAction("Advance card flight") { adjust(0.7) }, CustomAccessibilityAction("Rewind card flight") { adjust(-0.7) })
            }
            .pointerInput(scale, reduced, renderTime) {
                var total = 0f
                detectDragGestures(
                    onDragStart = {
                        if (currentRenderTime.value == null && !currentReduced.value) {
                            val now = neonNow()
                            motion.grab = motion.frame(now, currentAutoPlay.value, currentReduced.value, null).time
                            motion.manual = motion.grab; motion.dragging = true; motion.released = null
                            motion.threshold = motion.grab >= 3.5; total = 0f; clock.doubleValue = now; wake++
                        }
                    },
                    onDrag = { change, amount ->
                        if (motion.dragging) {
                            change.consume(); total += amount.x
                            motion.manual = (motion.grab + total / pixelScale / 55).coerceIn(0.0, 6.15)
                            val threshold = (motion.manual ?: 0.0) >= 3.5
                            if (threshold != motion.threshold) {
                                motion.threshold = threshold
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            clock.doubleValue = neonNow()
                        }
                    },
                    onDragEnd = { if (motion.dragging) { motion.dragging = false; motion.released = neonNow(); clock.doubleValue = neonNow(); wake++ } },
                    onDragCancel = { if (motion.dragging) { motion.dragging = false; motion.released = neonNow(); clock.doubleValue = neonNow(); wake++ } }
                )
            })
        Box(place(17f, 691f, 359f, 47f)
            .semantics { contentDescription = "Log in to Nova <18"; role = Role.Button }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = finish))
        Box(place(17f, 751f, 359f, 47f)
            .semantics { contentDescription = "Create a new account"; role = Role.Button }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = finish))
    }
}

@Composable
private fun rememberNeonCardFlightReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun NeonCardFlightSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true; controller.isAppearanceLightNavigationBars = true
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.isAppearanceLightStatusBars = status; controller.isAppearanceLightNavigationBars = navigation }
    }
}

private class NeonCardFlightPainter(context: Context) {
    private val images = (NeonCardFlightGeometry.pieces.map { "${it.name}-final" } + listOf("card","card-intro")).distinct().associateWith { name ->
        context.assets.open("neoncardflight/neoncardflight_$name.png").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!! }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private val matrix = Matrix()
    private val header = Bitmap.createBitmap(786, 1704, Bitmap.Config.ARGB_8888)
    private val src = FloatArray(8)
    private val dest = FloatArray(8)
    init {
        val c = NativeCanvas(header); c.scale(2f, 2f); drawStatic(c)
    }
    private fun bitmap(c: NativeCanvas, name: String, x: Double, y: Double, w: Double, h: Double, alpha: Double) {
        val image = images.getValue(name); rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        paint.shader = null; paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt(); c.drawBitmap(image, null, rect, paint)
    }
    fun static(c: NativeCanvas) {
        paint.shader = null; paint.alpha = 255; rect.set(0f, 0f, 393f, 852f); c.drawBitmap(header, null, rect, paint)
    }
    fun hero(c: NativeCanvas, time: Double, alpha: Double) {
        scene(c, time, alpha * (1 - neonEase(neonSeg(time, 6.22, 6.72))))
        if (time >= 6.72) scene(c, 0.0, alpha * neonEase(neonSeg(time, 6.72, 7.32)))
    }
    private fun pieces(c: NativeCanvas, time: Double, foreground: Boolean) {
        for (piece in NeonCardFlightGeometry.pieces) {
            if (piece.foreground != foreground) continue
            val pose = NeonCardFlightGeometry.pose(piece, time)
            if (pose.alpha <= 0) continue
            c.save();c.translate(pose.x.toFloat(),pose.y.toFloat());c.rotate(pose.roll.toFloat());c.scale(pose.scale.toFloat(),pose.scale.toFloat())
            matrix.setValues(floatArrayOf(pose.a.toFloat(),pose.c.toFloat(),pose.tx.toFloat(),pose.b.toFloat(),pose.d.toFloat(),pose.ty.toFloat(),0f,0f,1f));c.concat(matrix)
            bitmap(c,"${piece.name}-final",-piece.w/2,-piece.h/2,piece.w,piece.h,pose.alpha);c.restore()
        }
    }
    private fun accents(c: NativeCanvas, time: Double) {
        val s = max(time - 0.65, 0.0); if (s < 2.4) return
        val keys = NeonCardFlightGeometry.accent
        val x = NeonCardFlightGeometry.track(s, keys, 1); val y = NeonCardFlightGeometry.track(s, keys, 2)
        val roll = NeonCardFlightGeometry.track(s, keys, 3); val alpha = neonEase(neonSeg(s, 2.4, 2.55))
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER }
        c.save(); c.translate(x.toFloat(), y.toFloat()); c.rotate(roll.toFloat())
        for (i in 0..2) {
            val dx = (i-1)*20f
            val path = android.graphics.Path().apply { moveTo(dx-8,-8f); lineTo(dx+2,0f); lineTo(dx-8,8f) }
            stroke.color = neonColor(0xABBF00,alpha); stroke.strokeWidth = 4.2f; c.save(); c.translate(0f,1.4f); c.drawPath(path,stroke); c.restore()
            stroke.color = neonColor(0xE8FF00,alpha); stroke.strokeWidth = 3.2f; c.drawPath(path,stroke)
        }
        c.restore()
    }
    private fun card(c: NativeCanvas, pose: NeonCardFlightGeometry.Card) {
        if (!pose.visible) return
        val image = images.getValue(if(pose.intro)"card-intro" else "card")
        val weights = if (kotlin.math.hypot(pose.trailX,pose.trailY)>0.01) doubleArrayOf(0.06,0.10,0.16,0.25,1.0) else doubleArrayOf(1.0)
        src[0] = 0f; src[1] = 0f; src[2] = image.width.toFloat(); src[3] = 0f
        src[4] = image.width.toFloat(); src[5] = image.height.toFloat(); src[6] = 0f; src[7] = image.height.toFloat()
        for (i in weights.indices) {
            val lag = (weights.size-1-i).toDouble()
            pose.points.forEachIndexed { index, point -> dest[index*2]=(point.x-pose.trailX*lag).toFloat(); dest[index*2+1]=(point.y-pose.trailY*lag).toFloat() }
            matrix.setPolyToPoly(src,0,dest,0,4)
            paint.shader = null; paint.xfermode = null; paint.alpha = (weights[i]*255).roundToInt().coerceIn(0,255)
            c.drawBitmap(image,matrix,paint)
        }
    }
    private fun scene(c: NativeCanvas, time: Double, alpha: Double) {
        if (alpha <= 0) return
        val layer = alpha < 1
        if (layer) c.saveLayerAlpha(0f,0f,393f,852f,(alpha*255).roundToInt().coerceIn(0,255))
        val source = max(time-0.65,0.0); val inFront = source >= 3.35 && source <= 4.05
        val pose = NeonCardFlightGeometry.card(time)
        speedlines(c,time,1.0); if (!inFront) accents(c,time)
        pieces(c,time,false); if (pose.behindForeground) card(c,pose)
        pieces(c,time,true); if (!pose.behindForeground) card(c,pose)
        if (inFront) accents(c,time)
        if (layer) c.restore()
    }
    private fun speedlines(c: NativeCanvas, time: Double, alpha: Double) {
        if (time >= 2.55) return
        val travel = max(time - 0.65, 0.0); val colors = intArrayOf(0xBAB3C4,0xCF408F,0xD8D3DD,0xC5BDCC,0xBDB5C9,0xE2E800,0xD5D2DB,0xBCB3C2,0xC9C4D0,0xA272AD,0xC8C0CD,0xD2CDD6,0xC1BCC8,0xF36B3F,0xD8D3DE,0xC9C4CF)
        c.save(); c.clipRect(0f, 198f, 393f, 852f)
        for (i in 0 until 192) {
            val hash = neonFract(sin(i * 12.9898 + 3.2) * 43758.5453); val angle = i * 2.399963 + hash * 0.4
            val unit = neonFract(hash + travel * (0.95 + hash * 0.30)); val radius = 35 + unit.pow(1.3) * 185; val length = 12 + unit.pow(1.15) * (115 + 200 * max(sin(angle), 0.0))
            val x = cos(angle); val y = sin(angle)
            val sx = (196 + x * radius).toFloat(); val sy = (352 + y * radius).toFloat()
            val ex = (196 + x * (radius + length)).toFloat(); val ey = (352 + y * (radius + length)).toFloat()
            val accent = i % 16 == 1 || i % 16 == 5 || i % 16 == 9 || i % 16 == 13
            val opacity = (0.35 + 0.60 * unit) * (if (accent) 0.90 else 0.85) * alpha
            line.alpha = 255; line.strokeWidth = ((if (accent) 1.0 else 0.8) + unit.pow(1.2) * (if (accent) 4.8 else 5.5)).toFloat()
            line.shader = LinearGradient(sx, sy, ex, ey, intArrayOf(neonColor(colors[i % 16], opacity * 0.10), neonColor(colors[i % 16], opacity)), null, Shader.TileMode.CLAMP)
            val width = line.strokeWidth
            line.alpha = 41; line.strokeWidth = width + 2.2f; c.drawLine(sx, sy, ex, ey, line)
            line.alpha = 255; line.strokeWidth = width; c.drawLine(sx, sy, ex, ey, line)
        }
        line.shader = null; c.restore()
    }
    private fun text(c: NativeCanvas, value: String, x: Float, y: Float, size: Float, weight: Int = 0, centered: Boolean = false, tracking: Float = 0f, color: Int = 0x131619) {
        paint.shader = null; paint.alpha = 255; paint.color = neonColor(color); paint.textSize = size
        paint.typeface = Typeface.create(when (weight) { 2 -> "sans-serif-black"; 1 -> "sans-serif-medium"; else -> "sans-serif" }, Typeface.NORMAL)
        val baseline = if (centered) y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2 else y - paint.fontMetrics.ascent
        if (tracking == 0f) c.drawText(value, if (centered) x - paint.measureText(value) / 2 else x, baseline, paint)
        else {
            val widths = FloatArray(value.length); paint.getTextWidths(value, widths); var dx = x
            value.forEachIndexed { i, char -> c.drawText(char.toString(), dx, baseline, paint); dx += widths[i] + tracking }
        }
    }
    private fun drawStatic(c: NativeCanvas) {
        paint.shader = null; paint.color = neonColor(0x000000, 0.32); c.drawRect(10f, 64f, 383f, 64.5f, paint)
        text(c, "N", 19f, 73f, 15f, 2); text(c, "Welcome to Nova <18", 38f, 74f, 12f)
        text(c, "INDEPENDENCE STARTS", 21f, 111f, 27f, 2, tracking = -0.65f); text(c, "HERE", 21f, 139.5f, 27f, 2, tracking = -0.65f)
        paint.color = neonColor(0x131619); c.drawRoundRect(17f, 691f, 376f, 738f, 23.5f, 23.5f, paint)
        text(c, "Log in to Nova <18", 196.5f, 714.5f, 13f, 1, centered = true, color = 0xFFFFFF)
        paint.color = NativeColor.WHITE; paint.setShadowLayer(8f, 0f, 4f, neonColor(0x000000, 0.08)); c.drawRoundRect(17f, 751f, 376f, 798f, 23.5f, 23.5f, paint)
        paint.setShadowLayer(3f, 0f, 2f, neonColor(0x000000, 0.10)); c.drawRoundRect(17f, 751f, 376f, 798f, 23.5f, 23.5f, paint); paint.clearShadowLayer()
        text(c, "Create a new account", 196.5f, 774.5f, 13f, 1, centered = true)
    }
    fun close() { images.values.forEach { it.recycle() }; header.recycle() }
}
