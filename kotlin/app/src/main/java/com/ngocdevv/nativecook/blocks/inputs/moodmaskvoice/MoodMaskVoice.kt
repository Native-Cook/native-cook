package com.ngocdevv.nativecook.blocks.inputs.moodmaskvoice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// React Native reference, number for number: Instrument Serif Italic copy above a room
// gradient that warms with the mask's mood. LOOP = 8 seconds:
// 0–1 rest; 1–3.4 listen (0.3 s smoothstep ramps); 3.4–4 warm (cubic);
// 3.8–6.4 speak (0.3 s ramps); 6.4–7.4 cool (cubic); 7.4–8 rest.
// Holding anywhere listens. Release immediately starts the reply at t = 3.4, as in RN.
// Reduced motion keeps the calm, legible resting state and disables the hold gesture.

/** Seconds. */
const val LOOP = 8f
private const val FACE_W = 236f
private const val FACE_H = 354f
private val BACKGROUND = Color(0xFF050508)
private val INK = Color(0xFFF4F2FF)
private val DIM = Color(0xFF7D7A8F)
private val CALM_TOP = 0xFF4F5BFF.toInt()
private val CALM_BOTTOM = 0xFFB07BFF.toInt()
private val WARM_TOP = 0xFFFF3B2E.toInt()
private val WARM_BOTTOM = 0xFFFFB25C.toInt()

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
private fun cubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun mixColor(a: Int, b: Int, u: Float): Int {
    fun channel(shift: Int) = (((a shr shift) and 255) * (1 - u) + ((b shr shift) and 255) * u).roundToInt()
    return android.graphics.Color.rgb(channel(16), channel(8), channel(0))
}

private data class Voice(val t: Float = 0f, val mood: Float = 0f, val listen: Float = 0f, val speak: Float = 0f) {
    val reply: Float get() = smooth((mood - 0.3f) / 0.4f)
    val idle: Float get() = (1 - max(listen, reply)).coerceIn(0f, 1f)
}

private fun voiceAt(t: Float) = Voice(
    t,
    cubic(seg(t, 3.4f, 4f)) * (1 - cubic(seg(t, 6.4f, 7.4f))),
    smooth(seg(t, 1f, 1.3f)) * (1 - smooth(seg(t, 3.1f, 3.4f))),
    smooth(seg(t, 3.8f, 4.1f)) * (1 - smooth(seg(t, 6.1f, 6.4f))),
)

private class MoodMaskVoiceMotion {
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var voice by mutableStateOf(Voice())

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val v = voice
        if (touching) {
            voice = Voice(elapsed.toFloat(), max(0f, v.mood - dt.toFloat() * 2),
                min(1f, v.listen + dt.toFloat() * 4), max(0f, v.speak - dt.toFloat() * 4))
        } else if (autoPlay) {
            voice = voiceAt(((elapsed - origin) % LOOP).toFloat())
        }
    }

    fun release() {
        touching = false
        origin = elapsed - 3.4
    }
}

/** Rasterize blurs once; Android hardware canvases do not support mask filters. */
private fun blurSprite(width: Int, height: Int, sigma: Float, solid: Boolean = false, draw: (AndroidCanvas, Paint) -> Unit): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f),
            if (solid) BlurMaskFilter.Blur.SOLID else BlurMaskFilter.Blur.NORMAL)
    }
    draw(AndroidCanvas(bitmap), paint)
    bitmap.prepareToDraw()
    return bitmap
}

private class MoodMaskVoiceArt(private val mask: Bitmap) {
    private val paddedMask = Bitmap.createBitmap(mask.width + 8, mask.height + 8, Bitmap.Config.ARGB_8888).apply {
        AndroidCanvas(this).drawBitmap(mask, 4f, 4f, null)
        prepareToDraw()
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint()
    private val skyPaint = Paint()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val glow = blurSprite(720, 720, 70f) { c, p -> c.drawCircle(360f, 360f, 150f, p) }
    private val eye = blurSprite(58, 29, 4f, true) { c, p -> c.drawRoundRect(12f, 12f, 46f, 17f, 2.5f, 2.5f, p) }
    private val mouths = List(65) { i ->
        val open = i / 64f
        blurSprite(108, 66, 4f) { c, p ->
            val w = (30 + 8 * open) * 2
            val h = (1 + 16 * open) * 2
            c.drawRoundRect(54 - w / 2, 33 - h / 2, 54 + w / 2, 33 + h / 2, 16f, 16f, p)
        }
    }
    // COLOR is native on API 29+. Older devices use a cached luminosity-preserving atlas.
    private val fallback = if (Build.VERSION.SDK_INT < 29) List(17) { colorizedMask(mask, it / 16f) } else emptyList()
    private var lastMood = Float.NaN
    private var lastY = Float.NaN
    private var skyMood = Float.NaN
    private var skyHeight = Float.NaN
    private var glowTint = 0
    private val eyeTint = PorterDuffColorFilter(0xFFE9E4FF.toInt(), PorterDuff.Mode.SRC_IN)
    private val mouthTint = PorterDuffColorFilter(0xFF14060F.toInt(), PorterDuff.Mode.SRC_IN)

    fun draw(c: AndroidCanvas, width: Float, height: Float, safeTop: Float, safeBottom: Float, v: Voice) {
        val cx = width / 2
        val faceX = cx - FACE_W / 2
        val faceY = safeTop + 64
        val waveY = faceY + FACE_H + 40
        val micY = height - safeBottom - 70
        if (skyMood != v.mood || skyHeight != height) {
            skyMood = v.mood
            skyHeight = height
            skyPaint.shader = LinearGradient(0f, 0f, 0f, height,
                mixColor(0xFF0B0920.toInt(), 0xFF1E0A0C.toInt(), v.mood),
                0xFF050508.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, width, height, skyPaint)
        val glowColor = mixColor(CALM_BOTTOM, WARM_TOP, v.mood)
        if (glowTint != glowColor) {
            glowTint = glowColor
            tintPaint.colorFilter = PorterDuffColorFilter(glowColor, PorterDuff.Mode.SRC_IN)
        }
        tintPaint.alpha = ((0.32f + 0.18f * v.mood + 0.1f * v.speak) * 255).roundToInt()
        c.drawBitmap(glow, null, RectF(cx - 360, faceY + FACE_H / 2 - 360, cx + 360, faceY + FACE_H / 2 + 360), tintPaint)

        c.save()
        val s = 1 + 0.02f * v.listen + 0.015f * v.speak * sin(v.t * 5)
        c.scale(s, s, cx, faceY + FACE_H / 2)
        val faceBounds = RectF(faceX, faceY, faceX + FACE_W, faceY + FACE_H)
        if (Build.VERSION.SDK_INT >= 29) {
            if (lastMood != v.mood || lastY != faceY) {
                lastMood = v.mood
                lastY = faceY
                gradientPaint.shader = LinearGradient(cx, faceY, cx, faceY + FACE_H,
                    mixColor(CALM_TOP, WARM_TOP, v.mood), mixColor(CALM_BOTTOM, WARM_BOTTOM, v.mood), Shader.TileMode.CLAMP)
                gradientPaint.blendMode = BlendMode.COLOR
            }
            // DST_IN must cover beyond the gradient's fractional-pixel outer edge.
            // A transparent image border clears that coverage without clipping the mask.
            val border = 4 * FACE_W / mask.width
            val maskBounds = RectF(faceX - border, faceY - border,
                faceX + FACE_W + border, faceY + FACE_H + border)
            val layer = c.saveLayer(maskBounds, null)
            c.drawBitmap(mask, null, faceBounds, imagePaint)
            c.drawRect(faceBounds, gradientPaint)
            c.drawBitmap(paddedMask, null, maskBounds, maskPaint)
            c.restoreToCount(layer)
        } else {
            val index = v.mood * 16
            val low = index.toInt().coerceIn(0, 16)
            val fraction = if (low < 16) index - low else 0f
            val layer = c.saveLayer(faceBounds, null)
            // Add weighted premultiplied images so the feathered edge keeps its alpha.
            imagePaint.alpha = ((1 - fraction) * 255).roundToInt()
            c.drawBitmap(fallback[low], null, faceBounds, imagePaint)
            if (fraction > 0) {
                imagePaint.alpha = (fraction * 255).roundToInt()
                imagePaint.xfermode = additive
                c.drawBitmap(fallback[low + 1], null, faceBounds, imagePaint)
                imagePaint.xfermode = null
            }
            imagePaint.alpha = 255
            c.restoreToCount(layer)
        }
        imagePaint.colorFilter = eyeTint
        imagePaint.alpha = ((0.75f + 0.25f * max(v.listen, v.speak)) * 255).roundToInt()
        val eyeY = faceY + FACE_H * 0.488f
        for (x in floatArrayOf(0.31f, 0.69f)) {
            val eyeX = faceX + FACE_W * x
            c.drawBitmap(eye, null, RectF(eyeX - 29, eyeY - 14.5f, eyeX + 29, eyeY + 14.5f), imagePaint)
        }
        val mouthOpen = v.speak * (0.25f + 0.75f * abs(sin(v.t * 9)) * (0.6f + 0.4f * sin(v.t * 3.1f)))
        imagePaint.alpha = (smooth(mouthOpen * 6) * 255).roundToInt()
        imagePaint.colorFilter = mouthTint
        val mouthX = faceX + FACE_W * 0.49f
        val mouthY = faceY + FACE_H * 0.712f
        c.drawBitmap(mouths[(mouthOpen * 64).roundToInt().coerceIn(0, 64)], null,
            RectF(mouthX - 27, mouthY - 16.5f, mouthX + 27, mouthY + 16.5f), imagePaint)
        imagePaint.alpha = 255
        imagePaint.colorFilter = null
        c.restore()

        val level = max(v.listen, v.speak)
        fillPaint.color = mixColor(0xFFC9C3FF.toInt(), WARM_BOTTOM, v.mood)
        fillPaint.alpha = ((0.35f + 0.55f * level) * 255).roundToInt()
        val left = cx - 27 * 8f / 2
        repeat(28) { k ->
            val edge = sin(PI.toFloat() * (k + 0.5f) / 28)
            val wave = abs(sin(v.t * 6.2f + k * 0.7f)) * (0.5f + 0.5f * sin(k * 1.3f + v.t * 3.1f))
            val h = 3 + 34 * level * edge * wave
            c.drawRoundRect(left + k * 8 - 1.5f, waveY - h / 2, left + k * 8 + 1.5f, waveY + h / 2, 1.5f, 1.5f, fillPaint)
        }
        fillPaint.color = android.graphics.Color.WHITE
        fillPaint.alpha = ((0.12f + 0.3f * v.listen) * 255).roundToInt()
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = 1.5f
        c.drawCircle(cx, micY, 40 + 16 * v.listen * (0.5f + 0.5f * sin(v.t * 7)), fillPaint)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = 0xFF16141F.toInt()
        c.drawCircle(cx, micY, 34f, fillPaint)
        fillPaint.color = 0xFFF4F2FF.toInt()
        c.drawRoundRect(cx - 6, micY - 14, cx + 6, micY + 6, 6f, 6f, fillPaint)
        c.drawRect(cx - 1, micY + 9, cx + 1, micY + 15, fillPaint)
    }
}

/** Nonseparable Color blend for the pre-29 fallback atlas. */
private fun colorizedMask(mask: Bitmap, mood: Float): Bitmap {
    val pixels = IntArray(mask.width * mask.height)
    mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
    val top = mixColor(CALM_TOP, WARM_TOP, mood)
    val bottom = mixColor(CALM_BOTTOM, WARM_BOTTOM, mood)
    fun rgb(color: Int) = floatArrayOf((color shr 16 and 255) / 255f, (color shr 8 and 255) / 255f, (color and 255) / 255f)
    for (y in 0 until mask.height) {
        val source = rgb(mixColor(top, bottom, (y + 0.5f) / mask.height))
        val sourceLum = source[0] * 0.3f + source[1] * 0.59f + source[2] * 0.11f
        for (x in 0 until mask.width) {
            val i = y * mask.width + x
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) / 255f
            if (alpha == 0f) continue
            val lum = ((pixel shr 16 and 255) * 0.3f + (pixel shr 8 and 255) * 0.59f + (pixel and 255) * 0.11f) / 255f
            val out = FloatArray(3) { source[it] + lum - sourceLum }
            val low = out.min()
            val high = out.max()
            if (low < 0) for (k in 0..2) out[k] = lum + (out[k] - lum) * lum / (lum - low)
            if (high > 1) for (k in 0..2) out[k] = lum + (out[k] - lum) * (1 - lum) / (high - lum)
            fun channel(k: Int) = ((out[k] * alpha + source[k] * (1 - alpha)) * 255).roundToInt().coerceIn(0, 255)
            pixels[i] = android.graphics.Color.argb(pixel ushr 24, channel(0), channel(1), channel(2))
        }
    }
    return Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888).apply { prepareToDraw() }
}

@Composable
fun MoodMaskVoice(
    modifier: Modifier = Modifier,
    name: String = "Mira",
    idle: String = "Hold to talk",
    listening: String = "Listening…",
    reply: String = "That’s wonderful news. Tell me everything!",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = false)
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reduced = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val motion = remember { MoodMaskVoiceMotion() }
    val serif = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "moodmaskvoice/moodmaskvoice_InstrumentSerif-Italic.ttf")))
    }
    val art = remember(context) {
        val mask = context.assets.open("moodmaskvoice/moodmaskvoice_mask.png").use { BitmapFactory.decodeStream(it)!! }
        mask.prepareToDraw()
        MoodMaskVoiceArt(mask)
    }
    LaunchedEffect(active, reduced, autoPlay) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
            previous = now
            motion.advance(dt, autoPlay)
        }
    }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safe.calculateTopPadding().value
    val safeBottom = safe.calculateBottomPadding().value
    val captionTop = safeTop + 64 + FACE_H + 40 + 36
    val captionStyle = TextStyle(fontFamily = serif, fontSize = 30.sp, lineHeight = 36.sp,
        color = INK, textAlign = TextAlign.Center, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        // RN applies these line heights even below the font's intrinsic 1.3-em height.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight))

    Box(modifier.fillMaxSize().background(BACKGROUND)) {
        Box(Modifier.fillMaxSize().pointerInput(motion, reduced) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                motion.touching = true
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }
                } finally {
                    motion.release()
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$name, voice assistant. Hold to talk." }) {
                val c = drawContext.canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                art.draw(c, size.width / density, size.height / density, safeTop, safeBottom, motion.voice)
                c.restore()
            }
            Row(Modifier.offset(y = (safeTop + 12).dp).fillMaxWidth().height(34.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFF7CF0B0), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(name, style = captionStyle.copy(fontSize = 28.sp, lineHeight = 32.sp))
            }
            Box(Modifier.offset(y = captionTop.dp).fillMaxWidth().height(76.dp).padding(horizontal = 28.dp)) {
                Text(idle, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = motion.voice.idle }, style = captionStyle.copy(color = DIM))
                Text(listening, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = motion.voice.listen }, style = captionStyle)
                Text(reply, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = motion.voice.reply }, style = captionStyle)
            }
        }
    }
}
