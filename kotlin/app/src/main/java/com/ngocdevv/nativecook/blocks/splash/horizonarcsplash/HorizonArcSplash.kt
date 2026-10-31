package com.ngocdevv.nativecook.blocks.splash.horizonarcsplash

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds. Layout, drawing and timing follow the React Native reference. */
const val LOOP = 8.5
private const val RIM = 250f
private val BACKGROUND = Color(0xFF000005)
private val ICE = Color(0xFF8EC5FF)
private val WORDS = listOf("Every", "trip", "starts", "with", "a", "horizon")

// 0–1.4: upper rim sweeps down; .9–2: words appear, staggered .12 s.
// 1.5–2.5: lower rim rises to 62%; 2.5–3.9: glow breathes ±20%.
// 3.9–4.7: planet grows 250→550 dp, its top moves 62→70%; words fade 3.9–4.3.
// 4.4–7.4: 12 meteors; 4.6–5.2: welcome fades in; 7.2–7.9: scene dims.
// 7.9–8.5: opening rim returns, identical to t=0. Reduced motion rests at t=6.
// A tap adds a 1.8 s sine-envelope shower, leaving the reference's loop uninterrupted.
private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0).toFloat()
private fun easeIn(x: Float) = x * x * x
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun pageAlpha(t: Double) = seg(t, 4.6, 5.2) * (1f - seg(t, 7.2, 7.7))

@Composable
fun HorizonArcSplash(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberReducedMotion()
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val clock = remember { mutableDoubleStateOf(if (autoPlay && !reduced) 0.0 else 6.0) }
    val showerAt = remember { mutableDoubleStateOf(-10.0) }
    LaunchedEffect(active, reduced, autoPlay) {
        if (reduced) {
            clock.doubleValue = 6.0
            return@LaunchedEffect
        }
        if (!autoPlay) clock.doubleValue = 6.0
        if (!active) return@LaunchedEffect
        val accumulated = elapsed.doubleValue
        val origin = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            elapsed.doubleValue = accumulated + (now - origin) / 1e9
            if (autoPlay) clock.doubleValue = elapsed.doubleValue % LOOP
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = insets.calculateTopPadding()
    val safeBottom = insets.calculateBottomPadding()
    val tap = {
        showerAt.doubleValue = elapsed.doubleValue
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        Unit
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(BACKGROUND)
            .semantics {
                contentDescription = "Farside. Tap for a meteor shower."
                if (!reduced) onClick(label = "Send a meteor shower") { tap(); true }
            }
            .pointerInput(reduced) {
                if (!reduced) detectTapGestures(onTap = { tap() })
            },
    ) {
        val painter = remember(maxWidth, maxHeight, density) {
            HorizonArcSplashPainter(maxWidth.value, maxHeight.value, density)
        }
        Canvas(Modifier.fillMaxSize()) {
            val age = elapsed.doubleValue - showerAt.doubleValue
            val shower = if (!reduced && age > 0.0 && age < 1.8) sin(PI * age / 1.8).toFloat() else 0f
            val c = drawContext.canvas.nativeCanvas
            c.save()
            c.scale(density, density)
            painter.draw(c, clock.doubleValue, shower)
            c.restore()
        }
        FlowRow(
            Modifier.offset(y = maxHeight * 0.42f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            WORDS.forEachIndexed { i, word ->
                Text(
                    "$word ",
                    modifier = Modifier.graphicsLayer {
                        val t = clock.doubleValue
                        val u = seg(t, 0.9 + i * 0.12, 1.4 + i * 0.12) * (1f - seg(t, 3.9, 4.3))
                        alpha = u
                        translationY = 8f * (1f - u) * density
                    },
                    style = label(24f, FontWeight.Medium, 29f).copy(letterSpacing = (-0.2).sp),
                )
            }
        }
        Column(
            Modifier.offset(y = safeTop + 60.dp).fillMaxWidth().padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = pageAlpha(clock.doubleValue)
                    translationY = 14f * (1f - alpha) * density
                },
        ) {
            Text("FARSIDE", style = label(15f, FontWeight.ExtraBold, 18f).copy(color = ICE, letterSpacing = 2.sp))
            Text(
                "Plan the trips\nbeyond the map",
                modifier = Modifier.padding(top = 12.dp),
                style = label(38f, FontWeight.Black, 42f).copy(letterSpacing = (-1.2).sp),
            )
            Text(
                "Routes, stays and the long way round, in one quiet place.",
                modifier = Modifier.padding(top = 10.dp).widthIn(max = 280.dp),
                style = label(16f, FontWeight.Normal, 22f, 0.7f),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp)
                .padding(bottom = safeBottom + 14.dp)
                .graphicsLayer {
                    alpha = pageAlpha(clock.doubleValue)
                    translationY = 14f * (1f - alpha) * density
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Start exploring", style = label(17f, FontWeight.Bold, 20f).copy(color = BACKGROUND))
            }
            Text(
                "I already have an account",
                modifier = Modifier.padding(top = 14.dp),
                style = label(15f, FontWeight.SemiBold, 18f, 0.75f),
            )
        }
    }
}

private fun label(size: Float, weight: FontWeight, line: Float, alpha: Float = 1f) = TextStyle(
    fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = 0.sp,
    color = Color.White.copy(alpha = alpha),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The three blurred circle strokes and soft beam are rasterized once, so the same material
 * works on API 26 and hardware canvases without a per-frame software blur or API 33 shader. */
private class HorizonArcSplashPainter(private val width: Float, private val height: Float, density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rimExtent = 340f
    private val rimRect = RectF()
    private val outer = rimBitmap(26f, 22f, 0xFF2E7BFF.toInt(), density)
    private val middle = rimBitmap(9f, 6f, 0xFF2E7BFF.toInt(), density)
    private val core = rimBitmap(2.5f, 0.8f, 0xFF8EC5FF.toInt(), density)
    private val beamPad = 125f
    private val beam = beamBitmap()
    private val beamRect = RectF(width * 0.35f - beamPad, -100f - beamPad,
        width * 0.85f + beamPad, -100f + height * 1.4f + beamPad)
    private var planetRadius = -1f
    private var planetTop = -1f
    private var planetGradient: Shader? = null

    private fun ink(alpha: Float = 1f): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = NativeColor.WHITE
        paint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        return paint
    }

    private fun rim(c: NativeCanvas, cx: Float, cy: Float, alpha: Float, glow: Float) {
        if (alpha <= 0.001f) return
        rimRect.set(cx - rimExtent, cy - rimExtent, cx + rimExtent, cy + rimExtent)
        c.drawBitmap(outer, null, rimRect, ink(alpha * 0.55f * glow))
        c.drawBitmap(middle, null, rimRect, ink(alpha * 0.9f))
        c.drawBitmap(core, null, rimRect, ink(alpha))
    }

    fun draw(c: NativeCanvas, t: Double, shower: Float) {
        val cx = width / 2f
        val dim = 1f - seg(t, 7.2, 7.9)
        val fadeIn = seg(t, 7.9, 8.5)
        if (t < 1.4) {
            val y = 40f + (height + 120f) * easeIn(seg(t, 0.0, 1.4))
            rim(c, cx, y - RIM, 1f, 1f)
        } else if (fadeIn > 0f) rim(c, cx, 40f - RIM, fadeIn, 1f)
        val rise = easeOut(seg(t, 1.5, 2.5))
        val swell = easeInOut(seg(t, 3.9, 4.7))
        if (rise > 0f && dim > 0f) {
            val top = height + 30f - (height + 30f - height * 0.62f) * rise + height * 0.08f * swell
            val breathe = 1f + 0.2f * sin(max(0.0, t - 2.5) * PI * 1.4).toFloat() * (1f - swell)
            val radius = RIM * (1f + 1.2f * swell)
            val cy = top + radius
            if (swell > 0f) {
                if (planetRadius != radius || planetTop != top) {
                    planetRadius = radius
                    planetTop = top
                    planetGradient = RadialGradient(cx, top, radius * 0.55f,
                        intArrayOf(0xFF3D8BFF.toInt(), 0xFF1A3FD6.toInt(), 0xFF08155C.toInt()),
                        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                }
                c.drawCircle(cx, cy, radius, ink(swell * dim).apply { shader = planetGradient })
                c.save()
                c.rotate(-32f, width, 0f)
                c.drawBitmap(beam, null, beamRect, ink(swell * dim * 0.85f))
                c.restore()
            }
            // Like RN, the stroke stays radius 250 even while the filled planet grows to 550.
            rim(c, cx, cy, dim, breathe)
        }
        val on = seg(t, 4.4, 4.8) * (1f - seg(t, 7.0, 7.4))
        val a = max(on, shower)
        if (a <= 0.01f) return
        for (i in 0 until 12) {
            val phase = ((t + i * 0.9 / 12 * 3.1) % 0.9 / 0.9).toFloat()
            val sx = width * (0.55f + 0.45f * ((i * 0.37f) % 1f)) + 60f
            val sy = height * (0.02f + 0.3f * ((i * 0.61f) % 1f)) - 40f
            val distance = 260f * phase
            val x = sx - distance * 0.5f
            val y = sy + distance * 0.87f
            val tail = 46f + 30f * ((i * 0.23f) % 1f)
            val tx = x + tail * 0.5f
            val ty = y - tail * 0.87f
            val opacity = a * sin(PI * phase).toFloat() * dim
            c.drawLine(tx, ty, x, y, ink(opacity).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 2f
                shader = LinearGradient(tx, ty, x, y,
                    intArrayOf(0x00FFFFFF, NativeColor.WHITE), null, Shader.TileMode.CLAMP)
            })
            c.drawCircle(x, y, 3.2f, ink(opacity))
        }
    }

    private fun blur(sigma: Float) = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)

    private fun rimBitmap(stroke: Float, sigma: Float, color: Int, density: Float): Bitmap {
        val px = ceil(rimExtent * 2f * density).toInt()
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bitmap ->
            NativeCanvas(bitmap).apply {
                scale(density, density)
                drawCircle(rimExtent, rimExtent, RIM, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = stroke
                    maskFilter = blur(sigma)
                })
            }
        }
    }

    private fun beamBitmap(): Bitmap {
        // A 40 dp blur needs no more than one pixel per dp in this immutable texture.
        val result = Bitmap.createBitmap(ceil(width * 0.5f + beamPad * 2f).toInt(),
            ceil(height * 1.4f + beamPad * 2f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = NativeCanvas(result)
        canvas.translate(beamPad - width * 0.35f, beamPad + 100f)
        canvas.drawRect(width * 0.35f, -100f, width * 0.85f, -100f + height * 1.4f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, height,
                    intArrayOf(0x002E7BFF, NativeColor.argb(140, 46, 123, 255)), null, Shader.TileMode.CLAMP)
                maskFilter = blur(40f)
            })
        return result
    }
}
