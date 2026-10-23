package com.ngocdevv.nativecook.blocks.media.inkgradientsongcard

import android.annotation.TargetApi
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Ink Gradient Song Card — a "make your first song" sheet whose background is ink being stirred in
 water, over a dimmed music app. A port of the SwiftUI block (InkGradientSongCard.swift); the
 numbers are the same.

 Look: behind, a dark music app ("Explore" in Instrument Serif 32 sp, a search button, a banner,
 four release cards drawn in code, a tab bar with a glowing red centre tab), dimmed by black 60 %.
 In front, a card (84 % of the width up to 340 dp, 62 % of the height up to 540 dp, corner 26,
 centred at 53 % of the height) filled with an AGSL RuntimeShader on Android 13+ (the same code as
 InkGradientSongCard.metal): three clouds — burgundy #6B1020, signal red #E0251F, lilac #C98BE8 —
 from two warps of four-octave value noise, with thresholds nudged per 0.5 dp grain cell so every
 edge is sprayed and stippled, and a film grain on top. Below Android 13 the clouds are two soft
 blurred discs of red and lilac drifting over burgundy, without the grain. On the card: a round
 close button, "Make your / first song" (Instrument Serif 40 sp, white, two 42 dp lines from 46 dp
 down), a two-line subtitle (14 sp, white 90 %; both texts carry a soft black shadow), three tilted
 prints (100 × 126, a 3 dp white border, corner 12, art drawn in code: a studio microphone under a
 lamp, a record on teal, a sun over water) and a pale pink footer (#F8D8DE, 60 dp) reading "Let's
 make music".

 Motion (LOOP = 8 s), all of it periodic in the loop:
   • the ink drifts on circles of the loop's phase (once and twice a loop);
   • it is stirred by a point travelling a figure eight across the card (x: 28 % of the width,
     cos(2πt/8); y: 20 % of the height, sin(4πt/8), around the middle), swirling the ink around it
     by up to 1.1 rad, fading over about a third of the card;
   • the red and lilac thresholds breathe once a loop (±0.05, ±0.04), so the colours trade places;
   • each print floats ±3 dp and ±1.2° on its own phase.
 Touch: drag on the card to stir the ink yourself: the stirring point follows the finger (eased in
 over 0.25 s) and swirls 2.4 rad, and the prints lean towards the finger by depth (0.03, 0.05, 0.07
 of its distance from the card's centre). 2 s after release the stir eases back to its figure eight
 over 0.8 s. The footer button dips to 97 % while pressed. Light haptic when a stir starts and on
 the button.
 Reduced motion: the ink, the stir and the prints are still; a drag does not stir.
 */

private const val LOOP = 8.0
private const val FONT_ASSET = "inkgradientsongcard/inkgradientsongcard_InstrumentSerif-Regular.ttf"
private val AppBackground = Color(0xFF141114)
private val Footer = Color(0xFFF8D8DE)
private val FooterInk = Color(0xFF3A1A22)
private val Burgundy = Color(0xFF6B1020)
private val Red = Color(0xFFE0251F)
private val Lilac = Color(0xFFC98BE8)

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun easeOut(x: Double): Double {
    val t = min(max(x, 0.0), 1.0)
    return 1 - (1 - t).pow(3)
}

private fun easeInOut(x: Double): Double {
    val t = min(max(x, 0.0), 1.0)
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

/** The figure eight the ink is stirred along when no finger is on it (card dp). */
private fun inkPath(t: Double, w: Double, h: Double): DoubleArray {
    val a = 2 * PI * t / LOOP
    return doubleArrayOf(w * (0.5 + 0.28 * cos(a)), h * (0.45 + 0.2 * sin(2 * a)))
}

private class InkController(val reduced: Boolean) {
    var appear = nowSeconds()
    /** A finger stirring the ink: x, y (card dp) and when it came down; the release: x, y, when, amount. */
    var stir by mutableStateOf<DoubleArray?>(null)
    var released by mutableStateOf<DoubleArray?>(null)
    var buttonDown by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())

    /** Loop time in seconds (0 under reduced motion). */
    fun t(): Double {
        if (reduced) return 0.0
        val c = frame - appear
        return c - floor(c / LOOP) * LOOP
    }

    /** The stirring point (card dp), how hard it stirs, and how much a finger leads. */
    fun stirring(w: Double, h: Double): DoubleArray {
        val auto = inkPath(t(), w, h)
        var weight = 0.0
        var px = auto[0]
        var py = auto[1]
        val s = stir
        val r = released
        if (s != null) {
            weight = easeOut((frame - s[2]) / 0.25)
            px = s[0]
            py = s[1]
        } else if (r != null) {
            // Held where the finger left for 2 s, then back to the figure eight over 0.8 s.
            weight = r[3] * (1 - easeInOut((frame - r[2] - 2) / 0.8))
            px = r[0]
            py = r[1]
        }
        return doubleArrayOf(auto[0] + (px - auto[0]) * weight, auto[1] + (py - auto[1]) * weight, 1.1 + 1.3 * weight, weight)
    }
}

/** Back, left, right: offset from the stack's centre, tilt, float phase, depth for the lean. */
private val PrintSpots = arrayOf(
    doubleArrayOf(6.0, -26.0, 4.0, 0.0, 0.03),
    doubleArrayOf(-64.0, 10.0, -12.0, 2.1, 0.05),
    doubleArrayOf(60.0, 20.0, 10.0, 4.2, 0.07),
)

private class Release(val title: String, val artist: String, val from: Long, val to: Long)

private val Releases = listOf(
    Release("Neon Harbor", "Ivy Lane", 0xFFFF8A3D, 0xFF7B2CBF),
    Release("Slow Bloom", "The Quiet Hours", 0xFF2EC4B6, 0xFF1B4DB1),
    Release("Paper Moon", "Ada Rook", 0xFFF15BB5, 0xFF3A0CA3),
    Release("Low Tide", "Marlow", 0xFFFEE440, 0xFFF3722C),
)

@Composable
fun InkGradientSongCard(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val serif = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(reduced) { InkController(reduced) }
    val ink = remember { InkPainter() }

    LaunchedEffect(c) {
        c.appear = nowSeconds()
        if (reduced) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(AppBackground)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val density = LocalDensity.current.density
        val cw = min(w * 0.84, 340.0)
        val ch = min(h * 0.62, 540.0)
        val cx0 = (w - cw) / 2
        val cy0 = h * 0.53 - ch / 2

        // The app behind, dimmed, and the card's shadow.
        InkApp(w, h, top, bottom, serif)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.6f))
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.Black.copy(alpha = 0.45f).toArgb()
                // SwiftUI's shadow radius 30 is about a Gaussian sigma of 15.
                maskFilter = BlurMaskFilter((15 * density - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            }
            drawContext.canvas.nativeCanvas.drawRoundRect(
                (cx0 * density).toFloat(), ((cy0 + 16) * density).toFloat(), ((cx0 + cw) * density).toFloat(), ((cy0 + ch + 16) * density).toFloat(),
                26 * density, 26 * density, shadow,
            )
        }

        Box(
            Modifier
                .offset(x = cx0.dp, y = cy0.dp)
                .size(cw.dp, ch.dp)
                .clip(RoundedCornerShape(26.dp))
                .pointerInput(c) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val p0 = down.position / density
                        if (p0.y > ch - 60) {
                            c.buttonDown = true
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                            c.buttonDown = false
                            return@awaitEachGesture
                        }
                        if (reduced) return@awaitEachGesture
                        val at = nowSeconds()
                        c.stir = doubleArrayOf(p0.x.toDouble(), p0.y.toDouble(), at)
                        c.released = null
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        var last = p0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position / density
                            c.stir = doubleArrayOf(last.x.toDouble(), last.y.toDouble(), at)
                            change.consume()
                        }
                        val end = nowSeconds()
                        c.released = doubleArrayOf(last.x.toDouble(), last.y.toDouble(), end, easeOut((end - at) / 0.25))
                        c.stir = null
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize().semantics { invisibleToUser() }) {
                val st = c.stirring(cw, ch)
                ink.draw(this, c.t(), st, density, cw, ch)
            }
            // Close.
            Canvas(
                Modifier
                    .offset(x = (cw - 45).dp, y = 15.dp)
                    .size(30.dp)
                    .semantics { contentDescription = "Close"; role = Role.Button },
            ) {
                drawCircle(Color.White.copy(alpha = 0.2f))
                val o = 10 * density
                val s = 10 * density
                drawLine(Color.White, Offset(o, o), Offset(o + s, o + s), 1.8f * density, StrokeCap.Round)
                drawLine(Color.White, Offset(o + s, o), Offset(o, o + s), 1.8f * density, StrokeCap.Round)
            }
            val shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2 * density), 8 * density)
            Column(Modifier.fillMaxWidth().offset(y = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (line in listOf("Make your", "first song")) {
                    Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                        BasicText(line, style = TextStyle(fontFamily = serif, fontSize = 40.sp, color = Color.White, shadow = shadow), maxLines = 1)
                    }
                }
            }
            Box(Modifier.offset(x = 32.dp, y = 142.dp).width((cw - 64).dp).height(40.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    "Hum a tune, describe a mood, or add a photo to start a song.",
                    style = TextStyle(
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
                        shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, density), 6 * density),
                    ),
                    maxLines = 2,
                )
            }
            // Prints.
            val stackX = cw / 2
            val stackY = ch - 60 - 128
            for (i in 0 until 3) {
                val s = PrintSpots[i]
                Canvas(
                    Modifier
                        .offset(x = (stackX + s[0] - 50).dp, y = (stackY + s[1] - 63).dp)
                        .size(100.dp, 126.dp)
                        .graphicsLayer {
                            val t = c.t()
                            val float = if (reduced) 0.0 else sin(2 * PI * t / LOOP + s[3])
                            val st = c.stirring(cw, ch)
                            val leanX = (st[0] - cw / 2) * s[4] * st[3]
                            val leanY = (st[1] - ch / 2) * s[4] * st[3]
                            translationX = (leanX * density).toFloat()
                            translationY = ((3 * float + leanY) * density).toFloat()
                            rotationZ = (s[2] + 1.2 * float + leanX * 0.15).toFloat()
                        },
                ) { drawPrint(i, density) }
            }
            // Footer button.
            val scale by animateFloatAsState(if (c.buttonDown) 0.97f else 1f, tween(120), label = "button")
            Box(
                Modifier
                    .offset(y = (ch - 60).dp)
                    .width(cw.dp)
                    .height(60.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(Footer)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Let's make music", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FooterInk), maxLines = 1)
            }
        }
    }
}

/** The music app behind the sheet, drawn simply; it is dimmed and never moves. */
@Composable
private fun InkApp(w: Double, h: Double, top: Double, bottom: Double, serif: FontFamily) {
    val colW = (w - 52) / 2
    Canvas(Modifier.fillMaxSize().semantics { invisibleToUser() }) {
        val d = density
        drawRect(AppBackground)
        // Search button.
        drawCircle(Color.White.copy(alpha = 0.08f), 18 * d, Offset(((w - 40) * d).toFloat(), ((top + 28) * d).toFloat()))
        drawCircle(
            Color.White.copy(alpha = 0.85f), 5.5f * d, Offset(((w - 41.5) * d).toFloat(), ((top + 25.5) * d).toFloat()),
            style = Stroke(1.8f * d),
        )
        drawLine(
            Color.White.copy(alpha = 0.85f), Offset(((w - 37.5) * d).toFloat(), ((top + 30.5) * d).toFloat()),
            Offset(((w - 33) * d).toFloat(), ((top + 35) * d).toFloat()), 1.8f * d, StrokeCap.Round,
        )
        // Banner.
        drawRoundRect(
            Color(0xFF2A2328), Offset(20 * d, ((top + 64) * d).toFloat()), Size(((w - 40) * d).toFloat(), 48 * d), CornerRadius(14 * d),
        )
        // Releases.
        Releases.forEachIndexed { i, r ->
            val x = 20 + (i % 2) * (colW + 12)
            val y = top + 128 + (i / 2) * 292
            val o = Offset((x * d).toFloat(), (y * d).toFloat())
            val sz = Size((colW * d).toFloat(), 220 * d)
            drawRoundRect(Brush.linearGradient(listOf(Color(r.from), Color(r.to)), o, o + Offset(sz.width, sz.height)), o, sz, CornerRadius(18 * d))
        }
        // Tab bar with a glowing red centre tab.
        val barY = h - max(bottom, 12.0) - 56
        drawRect(Color(0xFF0E0C0E), Offset(0f, (barY * d).toFloat()), Size(size.width, size.height - (barY * d).toFloat()))
        for (k in 0 until 5) {
            if (k == 2) continue
            val x = w * (0.1 + 0.2 * k)
            drawRoundRect(
                Color.White.copy(alpha = 0.35f), Offset(((x - 9) * d).toFloat(), ((barY + 19) * d).toFloat()), Size(18 * d, 18 * d), CornerRadius(5 * d),
            )
        }
        val mid = Offset((w / 2 * d).toFloat(), ((barY + 28) * d).toFloat())
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Red.copy(alpha = 0.8f).toArgb()
            maskFilter = BlurMaskFilter((14 * d - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        drawContext.canvas.nativeCanvas.drawCircle(mid.x, mid.y, 26 * d, glow)
        drawCircle(Red, 22 * d, mid)
    }
    BasicText(
        "Explore",
        Modifier.offset(x = 20.dp, y = (top + 8).dp).height(40.dp),
        style = TextStyle(fontFamily = serif, fontSize = 32.sp, color = Color.White),
        maxLines = 1,
    )
    Box(Modifier.offset(x = 36.dp, y = (top + 64).dp).width((w - 72).dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
        BasicText(
            "New: remix any track with your voice",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f)),
            maxLines = 1,
        )
    }
    Releases.forEachIndexed { i, r ->
        val x = 20 + (i % 2) * (colW + 12)
        val y = top + 128 + (i / 2) * 292
        Column(Modifier.offset(x = x.dp, y = (y + 232).dp)) {
            BasicText(r.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
            BasicText(r.artist, style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f)), maxLines = 1)
        }
    }
}

/** A print: a 3 dp white border around art drawn in code, with a soft shadow. */
private fun DrawScope.drawPrint(kind: Int, d: Float) {
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = 0.3f).toArgb()
        // SwiftUI's shadow radius 10 is about a Gaussian sigma of 5.
        maskFilter = BlurMaskFilter((5 * d - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(0f, 6 * d, size.width, size.height + 6 * d, 12 * d, 12 * d, shadow)
    drawRoundRect(Color.White, cornerRadius = CornerRadius(12 * d))
    val w = size.width - 6 * d
    val h = size.height - 6 * d
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(3 * d, 3 * d, 3 * d + w, 3 * d + h), CornerRadius(9 * d))) }
    clipPath(clip) {
        translate(3 * d, 3 * d) {
            when (kind) {
                0 -> {
                    // A studio microphone under a warm lamp.
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFF4C27A), Color(0xFFB4562E)), 0f, h), size = Size(w, h))
                    drawRect(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0f)), Offset(0.72f * w, 0.24f * h), 0.4f * w),
                        size = Size(w, h),
                    )
                    val ink = Color(0xFF2A1A14)
                    drawRoundRect(ink, Offset(0.38f * w, 0.28f * h), Size(0.18f * w, 0.3f * h), CornerRadius(0.09f * w))
                    drawLine(ink, Offset(0.47f * w, 0.58f * h), Offset(0.47f * w, 0.9f * h), 3 * d)
                    drawOval(ink, Offset(0.3f * w, 0.87f * h), Size(0.34f * w, 0.07f * h))
                }
                1 -> {
                    // A record on teal.
                    drawRect(Brush.linearGradient(listOf(Color(0xFF2E6F73), Color(0xFF1D3F4A)), Offset.Zero, Offset(w, h)), size = Size(w, h))
                    val cc = Offset(0.5f * w, 0.55f * h)
                    drawCircle(Color(0xFF141414), 0.4f * w, cc)
                    for (k in floatArrayOf(0.33f, 0.27f, 0.21f)) drawCircle(Color.White.copy(alpha = 0.1f), k * w, cc, style = Stroke(d))
                    drawCircle(Red, 0.13f * w, cc)
                    drawCircle(Color(0xFF1D3F4A), 0.025f * w, cc)
                }
                else -> {
                    // A sun over water.
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF9A8B), Color(0xFF6B2C5C)), 0f, 0.64f * h), size = Size(w, h))
                    drawCircle(Color(0xFFFFE1A8), 0.2f * w, Offset(0.5f * w, 0.34f * h + 0.2f * w))
                    drawRect(Color(0xFF2A0F22), Offset(0f, 0.64f * h), Size(w, 0.36f * h))
                    floatArrayOf(0.7f, 0.77f, 0.84f).forEachIndexed { k, y ->
                        val half = (0.16f - 0.04f * k) * w
                        drawRoundRect(Color(0xFFFFE1A8).copy(alpha = 0.7f), Offset(0.5f * w - half, y * h), Size(2 * half, 2 * d), CornerRadius(d))
                    }
                }
            }
        }
    }
}

/** The card's ink: the AGSL shader on Android 13+, soft discs below. */
private class InkPainter {
    private val shader = if (Build.VERSION.SDK_INT >= 33) InkShader() else null
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(scope: DrawScope, t: Double, st: DoubleArray, d: Float, cw: Double, ch: Double) {
        val tau = (2 * PI * t / LOOP).toFloat()
        if (shader != null) {
            shader.update(scope.size.width, scope.size.height, 0.5f * d, tau, (st[0] * d).toFloat(), (st[1] * d).toFloat(), st[2].toFloat())
            scope.drawRect(ShaderBrush(shader.shader))
            return
        }
        scope.drawRect(Burgundy)
        val canvas = scope.drawContext.canvas.nativeCanvas
        blob.maskFilter = BlurMaskFilter((40 * d - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        blob.color = Red.toArgb()
        canvas.drawCircle(((cw * (0.65 + 0.2 * cos(tau.toDouble()))) * d).toFloat(), ((ch * (0.7 + 0.12 * sin(2.0 * tau))) * d).toFloat(), (0.38 * cw * d).toFloat(), blob)
        blob.color = Lilac.toArgb()
        canvas.drawCircle(((cw * (0.3 + 0.2 * sin(tau.toDouble()))) * d).toFloat(), ((ch * (0.3 + 0.12 * cos(tau.toDouble()))) * d).toFloat(), (0.4 * cw * d).toFloat(), blob)
        blob.color = Lilac.copy(alpha = 0.8f).toArgb()
        canvas.drawCircle(((st[0]) * d).toFloat(), ((st[1]) * d).toFloat(), (0.18 * cw * d).toFloat(), blob)
    }
}

/** The ink's AGSL shader: the same code as InkGradientSongCard.metal, in pixels. */
@TargetApi(33)
private class InkShader {
    val shader = RuntimeShader(SOURCE)

    fun update(w: Float, h: Float, grain: Float, tau: Float, sx: Float, sy: Float, strength: Float) {
        shader.setFloatUniform("uSize", w, h)
        shader.setFloatUniform("uGrain", grain)
        shader.setFloatUniform("uTau", tau)
        shader.setFloatUniform("uStir", sx, sy)
        shader.setFloatUniform("uStrength", strength)
    }

    companion object {
        private const val SOURCE = """
uniform float2 uSize;
uniform float uGrain;
uniform float uTau;
uniform float2 uStir;
uniform float uStrength;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.3;
        a *= 0.5;
    }
    return v;
}

half4 main(float2 position) {
    float2 uv = position / uSize.y;
    float2 s = uStir / uSize.y;
    // The stir: a swirl around the stirring point, fading over about a third of the card.
    float2 d = uv - s;
    float ang = uStrength * exp(-dot(d, d) / 0.045);
    float c = cos(ang);
    float sn = sin(ang);
    float2 w = s + float2(c * d.x - sn * d.y, sn * d.x + c * d.y);
    float2 p = w * 2.2;
    float2 q = float2(fbm(p + 0.8 * float2(cos(uTau), sin(uTau))),
                      fbm(p + float2(5.2, 1.3) + 0.8 * float2(sin(uTau), -cos(uTau))));
    float f = fbm(p + 2.2 * q + 0.6 * float2(cos(uTau + 1.0), sin(2.0 * uTau)));

    // Spray: each grain cell nudges the thresholds, so the edges between clouds stipple.
    float2 cell = floor(position / uGrain);
    float g1 = hash(cell) - 0.5;
    float g2 = hash(cell + float2(37.0, 17.0)) - 0.5;

    float3 burgundy = float3(0.420, 0.063, 0.125);
    float3 red = float3(0.878, 0.145, 0.122);
    float3 lilac = float3(0.788, 0.545, 0.910);
    float redAmount = smoothstep(0.55, 0.61, q.x + 0.22 * (uv.y - 0.5) + 0.05 * sin(uTau) + 0.16 * g1);
    float lilacAmount = smoothstep(0.57, 0.63, f + 0.04 * cos(uTau) + 0.16 * g2);
    float3 col = mix(burgundy, red, redAmount);
    col = mix(col, lilac, lilacAmount);
    // Film grain over everything.
    col *= 0.94 + 0.12 * hash(cell + float2(91.0, 7.0));
    return half4(half3(col), 1.0);
}
"""
    }
}
