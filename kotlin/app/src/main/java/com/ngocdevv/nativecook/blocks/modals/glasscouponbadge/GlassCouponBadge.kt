package com.ngocdevv.nativecook.blocks.modals.glasscouponbadge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithful port of the RN reference. A 210 dp coupon is projected through eleven slices of
// a 22 dp slab: yaw first, pitch second, focal distance 800 dp. LOOP = 6 s:
// periodic 14-degree yaw / 8-degree pitch sway; cubic turn 1.4–3.1 s, scale 1 -> 1.08 -> 1;
// landing wobble 3.1–3.9 s; twelve sparks 3.1–3.8 s; code shine 4.2–5.0 s.
// Drag: +0.5 / -0.4 degrees per dp, then a 0.8 s spring and 2 s rest before restarting.
// The shine clock freezes during manual control, just as it does in RN.

/** Seconds. */
const val LOOP = 6f
private const val SIZE = 210f
private const val THICK = 22f
private const val LAYERS = 11
private const val FOCAL = 800f
private val INK = Color(0xFF16141A)
private val PURPLE = Color(0xFF7C5CFF)

private data class GlassCouponBadgePose(val yaw: Float = 0f, val pitch: Float = 0f, val scale: Float = 1f, val sparks: Float = 0f)
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun normalizedYaw(yaw: Float) = ((yaw % 360 + 540) % 360) - 180

private fun poseAt(time: Float): GlassCouponBadgePose {
    val t = time % LOOP
    val w = (2 * PI * t / LOOP).toFloat()
    val x = t - 3.1f
    val wobble = if (x > 0 && x < 0.8f) 10 * exp(-5 * x) * sin(14 * x) else 0f
    return GlassCouponBadgePose(
        14 * sin(w) + 360 * easeInOutCubic(seg(t, 1.4f, 3.1f)) + wobble,
        8 * sin(2 * w),
        1 + 0.08f * sin(PI.toFloat() * seg(t, 1.4f, 3.1f)),
        if (t >= 3.1f && t < 3.8f) seg(t, 3.1f, 3.8f) else 0f,
    )
}

private class GlassCouponBadgeClock {
    var elapsed by mutableDoubleStateOf(0.0)
    var loopTime by mutableFloatStateOf(0f)
    var manual by mutableStateOf(false)
    var touching by mutableStateOf(false)
    var origin = 0.0
    var released = 0.0
    var dragYaw by mutableFloatStateOf(0f)
    var dragPitch by mutableFloatStateOf(0f)

    fun pose(reduced: Boolean): GlassCouponBadgePose {
        if (reduced) return GlassCouponBadgePose()
        if (touching) return GlassCouponBadgePose(dragYaw, dragPitch)
        if (manual) {
            val x = (elapsed - released).toFloat()
            val k = if (x < 0.8f) exp(-5 * x) * cos(10 * x) else 0f
            return GlassCouponBadgePose(dragYaw * k, dragPitch * k)
        }
        return poseAt(loopTime)
    }
}

/** Decode once, and cache filters and software Gaussian shadows instead of rasterizing per frame. */
private class GlassCouponBadgeArtwork(val image: Bitmap) {
    private val matrix = Matrix()
    private val src = floatArrayOf(0f, 0f, image.width.toFloat(), 0f, image.width.toFloat(), image.height.toFloat(), 0f, image.height.toFloat())
    private val quad = FloatArray(8)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edges = Array(LAYERS) { i ->
        val mid = 1 - abs(i.toFloat() / (LAYERS - 1) * 2 - 1)
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(android.graphics.Color.rgb(
                (200 - 60 * mid).roundToInt(), (204 - 90 * mid).roundToInt(), (232 - 10 * mid).roundToInt(),
            ), PorterDuff.Mode.SRC_IN)
        }
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, SIZE * 0.9f, intArrayOf(0x597C5CFF, 0x007C5CFF), null, Shader.TileMode.CLAMP)
    }
    private val sparksPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkColors = intArrayOf(0xFFFF8A1F.toInt(), 0xFF7C5CFF.toInt(), 0xFF4FC3FF.toInt())
    // Width steps of 2 dp are imperceptible through the fixed 14 dp Gaussian blur.
    private val shadows = Array(49) { index ->
        val width = SIZE * 0.7f * 0.35f + index * (SIZE * 0.7f * 0.65f / 48)
        Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888).also { bitmap ->
            AndroidCanvas(bitmap).apply {
                scale(2f, 2f)
                drawOval(120 - width / 2, 49f, 120 + width / 2, 71f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x4028145A
                    maskFilter = BlurMaskFilter((14f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
                })
            }
        }
    }
    private val shadowRect = RectF()

    fun draw(canvas: AndroidCanvas, cx: Float, cy: Float, pose: GlassCouponBadgePose) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, SIZE * 0.9f, glowPaint)
        canvas.restore()
        val sw = SIZE * 0.7f * max(0.35f, abs(cos(pose.yaw * PI.toFloat() / 180)))
        val index = ((sw / (SIZE * 0.7f) - 0.35f) / 0.65f * 48).roundToInt().coerceIn(0, 48)
        shadowRect.set(cx - 120, cy + SIZE * 0.6f + 11 - 60, cx + 120, cy + SIZE * 0.6f + 11 + 60)
        canvas.drawBitmap(shadows[index], null, shadowRect, facePaint)
        if (pose.sparks > 0f) {
            val p = pose.sparks
            val radius = 40 + 110 * (1 - (1 - p).pow(2)) + SIZE * 0.35f
            repeat(12) { i ->
                val a = (i * PI / 6 + 0.2).toFloat()
                sparksPaint.color = sparkColors[i % 3]
                sparksPaint.alpha = ((1 - p) * 255).roundToInt()
                canvas.drawCircle(cx + cos(a) * radius, cy + sin(a) * radius, 6 * (1 - p * 0.6f), sparksPaint)
            }
        }
        val yaw = pose.yaw * PI.toFloat() / 180
        val pitch = pose.pitch * PI.toFloat() / 180
        val cyw = cos(yaw)
        val syw = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val half = SIZE * pose.scale / 2
        val front = cyw * cp >= 0
        repeat(LAYERS) { n ->
            val i = if (front) n else LAYERS - 1 - n
            val z = -THICK / 2 + THICK * i / (LAYERS - 1)
            repeat(4) { corner ->
                val x = if (corner == 0 || corner == 3) -half else half
                val y = if (corner < 2) -half else half
                val x1 = x * cyw + z * syw
                val z1 = -x * syw + z * cyw
                val y2 = y * cp - z1 * sp
                val z2 = y * sp + z1 * cp
                val k = FOCAL / (FOCAL - z2)
                quad[corner * 2] = cx + x1 * k
                quad[corner * 2 + 1] = cy + y2 * k
            }
            // An exactly side-on quad has zero area. Never reuse the previous slice's matrix.
            if (!matrix.setPolyToPoly(src, 0, quad, 0, 4)) return@repeat
            canvas.save()
            canvas.concat(matrix)
            canvas.drawBitmap(image, 0f, 0f, if (n == LAYERS - 1) facePaint else edges[i])
            canvas.restore()
        }
    }
}

@Composable
private fun GlassCouponBadgeText(
    text: String, size: Int, weight: FontWeight, color: Color, modifier: Modifier = Modifier,
    tracking: Float = 0f, lineHeight: Int = size, centered: Boolean = false,
) {
    Text(text, modifier, style = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight,
        color = color, letterSpacing = tracking.sp, lineHeight = lineHeight.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ))
}

@Composable
fun GlassCouponBadge(modifier: Modifier = Modifier, brand: String = "Parcel", code: String = "SPRING25", autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val clock = remember { GlassCouponBadgeClock() }
    val artwork = remember(context) {
        context.assets.open("glasscouponbadge/glasscouponbadge_badge.png").use {
            GlassCouponBadgeArtwork(checkNotNull(BitmapFactory.decodeStream(it)).apply { this.density = Bitmap.DENSITY_NONE })
        }
    }
    LaunchedEffect(active, reduced, autoPlay, clock.manual) {
        if (!active || reduced || (!autoPlay && !clock.manual)) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            if (previous != 0L) clock.elapsed += (now - previous) / 1e9
            previous = now
            if (clock.manual) {
                if (!clock.touching && clock.elapsed - clock.released > 2.8) {
                    clock.manual = false
                    clock.origin = clock.elapsed
                    clock.loopTime = 0f
                }
            } else if (autoPlay) clock.loopTime = ((clock.elapsed - clock.origin) % LOOP).toFloat()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF4F2EF))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val sheetTop = height * 0.38f
        val cx = width / 2
        val cy = sheetTop + 30
        val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
        val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = (safeTop + 8).dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                GlassCouponBadgeText(brand, 20, FontWeight.Black, INK, lineHeight = 24)
                GlassCouponBadgeText("Bag (3)", 14, FontWeight.SemiBold, INK.copy(alpha = 0.6f), lineHeight = 17)
            }
            Spacer(Modifier.height(18.dp))
            val tileWidth = ((width - 40) * 0.47f).dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(listOf(0xFFF6C9B8, 0xFFC9D8F6), listOf(0xFFD9F0C9, 0xFFF0E2B8)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { color -> Box(Modifier.size(tileWidth, 170.dp).background(Color(color), RoundedCornerShape(20.dp))) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color(0x800C0818)))
        Box(Modifier.offset(y = sheetTop.dp).fillMaxWidth().height((height - sheetTop).dp).background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(40.dp, 5.dp).background(INK.copy(alpha = 0.15f), RoundedCornerShape(3.dp)))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 14).dp)) {
                GlassCouponBadgeText("REWARD UNLOCKED", 13, FontWeight.ExtraBold, PURPLE, Modifier.fillMaxWidth(), tracking = 1.6f, lineHeight = 16, centered = true)
                Spacer(Modifier.height(8.dp))
                GlassCouponBadgeText("25% off your next order", 28, FontWeight.Black, INK, Modifier.fillMaxWidth(), tracking = -0.8f, lineHeight = 32, centered = true)
                Spacer(Modifier.height(8.dp))
                GlassCouponBadgeText("For hitting five orders this spring. Valid on everything until 31 May.", 15, FontWeight.Normal, INK.copy(alpha = 0.6f), Modifier.fillMaxWidth(), lineHeight = 21, centered = true)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(PURPLE.copy(alpha = 0.07f))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 0.75.dp.toPx()
                        drawRoundRect(PURPLE.copy(alpha = 0.5f), Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(15.25.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 4.5.dp.toPx()))))
                    }
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        GlassCouponBadgeText(code, 20, FontWeight.Black, INK, tracking = 3f, lineHeight = 24)
                        GlassCouponBadgeText("Copy", 15, FontWeight.Bold, PURPLE, lineHeight = 18)
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val u = if (reduced) 0f else seg(clock.loopTime, 4.2f, 5f)
                        if (u > 0f && u < 1f) {
                            val x = (1.5f - 120 + 420 * u).dp.toPx()
                            val bandWidth = 60.dp.toPx()
                            val top = (-18.5).dp.toPx()
                            val bandHeight = size.height + 37.dp.toPx()
                            rotate(18f, Offset(x + bandWidth / 2, top + bandHeight / 2)) {
                                drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), x, x + bandWidth), Offset(x, top), Size(bandWidth, bandHeight))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(58.dp).background(INK, RoundedCornerShape(29.dp)), contentAlignment = Alignment.Center) {
                    GlassCouponBadgeText("Apply coupon", 17, FontWeight.Bold, Color.White, lineHeight = 21)
                }
            }
        }
        Box(Modifier.offset((cx - 125).dp, (cy - 125).dp).size(250.dp)
            .semantics { contentDescription = "Coupon. Drag to turn it." }
            .pointerInput(reduced) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = clock.pose(false)
                    clock.touching = true
                    clock.manual = true
                    clock.dragYaw = normalizedYaw(p.yaw)
                    clock.dragPitch = p.pitch
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    try {
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = (change.position - last) / density.density
                            clock.dragYaw += delta.x * 0.5f
                            clock.dragPitch = (clock.dragPitch - delta.y * 0.4f).coerceIn(-50f, 50f)
                            last = change.position
                            change.consume()
                        }
                    } finally {
                        clock.touching = false
                        clock.dragYaw = normalizedYaw(clock.dragYaw)
                        clock.released = clock.elapsed
                    }
                }
            })
        Canvas(Modifier.fillMaxSize()) {
            val pose = clock.pose(reduced)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density.density, density.density)
                artwork.draw(native, cx, cy, pose)
                native.restore()
            }
        }
    }
}
