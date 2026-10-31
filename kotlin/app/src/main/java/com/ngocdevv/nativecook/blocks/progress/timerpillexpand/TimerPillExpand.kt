package com.ngocdevv.nativecook.blocks.progress.timerpillexpand

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds, identical to the React Native reference. */
internal const val LOOP = 9.0

private val BACKGROUND = Color(0xFFECEBE8)
private val INK = Color(0xFF1C1C1E)
private const val ORANGE = 0xFFFF9F0A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun easeOutBack(x: Double) = 1 + 2.3 * (x - 1).pow(3) + 1.3 * (x - 1).pow(2)
private fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun lerp(a: Float, b: Float, u: Double) = (a + (b - a) * u).toFloat()

private data class TimerPillExpandIsland(
    val width: Float,
    val height: Float,
    val radius: Float,
    val pill: Float,
    val card: Float,
    val bar: Float,
    val minutes: Float,
    val start: Float,
    val cancel: Float,
    val running: Double,
)

private fun islandAt(t: Double): TimerPillExpandIsland {
    var w = 200f
    var h = 58f
    var r = 29f
    if (t >= 0.8 && t < 2.9) {
        val u = easeOutBack(seg(t, 0.8, 1.3))
        w = lerp(200f, 312f, u)
        h = lerp(58f, 250f, u)
        r = lerp(29f, 40f, u)
    } else if (t >= 2.9 && t < 7.6) {
        val u = easeOutBack(seg(t, 2.9, 3.4))
        w = lerp(312f, 344f, u)
        h = lerp(250f, 100f, u)
        r = lerp(40f, 50f, u)
    } else if (t >= 7.6) {
        val u = easeOutBack(seg(t, 7.6, 8.1))
        w = lerp(344f, 200f, u)
        h = lerp(100f, 58f, u)
        r = lerp(50f, 29f, u)
    }
    return TimerPillExpandIsland(
        w, h, r,
        (1 - seg(t, 0.8, 1.0) + seg(t, 7.8, 8.1)).toFloat(),
        (seg(t, 1.0, 1.3) * (1 - seg(t, 2.9, 3.1))).toFloat(),
        (seg(t, 3.1, 3.4) * (1 - seg(t, 7.6, 7.8))).toFloat(),
        (24 + 2 * easeInOutCubic(seg(t, 1.6, 2.6))).toFloat(),
        (1 - 0.1 * sin(PI * seg(t, 2.6, 2.9))).toFloat(),
        (1 - 0.12 * sin(PI * seg(t, 7.3, 7.6))).toFloat(),
        max(0.0, t - 3.4),
    )
}

private class TimerPillExpandPlayback {
    var elapsed = 0.0
    var origin = 0.0
    var time by mutableDoubleStateOf(0.0)

    fun advance(dt: Double) {
        elapsed += dt
        time = ((elapsed - origin) % LOOP + LOOP) % LOOP
    }

    fun tap() {
        val next = if (time < 0.8) 0.8 else if (time < 2.6) 2.6 else if (time < 7.3) 7.3 else 0.8
        origin = elapsed - next
        time = next
    }
}

// One dark island changes shape: 0–.8 pill; .8–1.3 expand (back, k=1.3);
// 1.6–2.6 roll 24→26 (cubic in/out); 2.6–2.9 press Start; 2.9–3.4 bar;
// 3.4–7.6 count down; 7.3–7.6 press Cancel; 7.6–8.1 pill; 8.1–9 rest.
// The RN tap jumps the clock to the next change, then playback immediately continues.
// Reduced motion shows the legible t=3.6 bar and disables both the loop and taps.
@Composable
fun TimerPillExpand(
    modifier: Modifier = Modifier,
    brand: String = "Simmer",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = true)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val playback = remember { TimerPillExpandPlayback() }
    val painter = remember { TimerPillExpandPainter() }
    LaunchedEffect(active, reduced, autoPlay) {
        if (!active || reduced || !autoPlay) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            playback.advance(if (previous == 0L) 0.0 else (now - previous) / 1e9)
            previous = now
        }
    }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val text = TextStyle(
        fontFamily = FontFamily.SansSerif,
        color = INK,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    fun tap() {
        if (!reduced && autoPlay) {
            playback.tap()
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val width = constraints.maxWidth / density
        val height = constraints.maxHeight / density
        Canvas(Modifier.fillMaxSize()) {
            // Read the frame clock only while drawing, keeping the page out of recomposition.
            val t = if (reduced) 3.6 else if (autoPlay) playback.time else 0.0
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            painter.draw(canvas, width / 2, height * 0.46f, islandAt(t))
            canvas.restore()
        }

        // A transparent, transformed target keeps touch and accessibility bounds on the island.
        // Its layer follows the same clock without measuring or recomposing the page per frame.
        Box(
            Modifier.size(344.dp, 250.dp)
                .graphicsLayer {
                    val t = if (reduced) 3.6 else if (autoPlay) playback.time else 0.0
                    val s = islandAt(t)
                    scaleX = s.width / 344
                    scaleY = s.height / 250
                    translationX = (width / 2 - 172) * density
                    translationY = (height * 0.46f - 125) * density
                }
                .semantics {
                    contentDescription = "Timer"
                    role = Role.Button
                    if (!reduced && autoPlay) onClick { tap(); true }
                }
                .pointerInput(reduced, autoPlay) {
                    if (!autoPlay || reduced) return@pointerInput
                    detectTapGestures { tap() }
                },
        )

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = safe.calculateTopPadding() + 8.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(brand, style = text.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
                Spacer(Modifier.weight(1f))
                Text("Step 3 of 7", style = text.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = INK.copy(alpha = 0.55f)))
            }
            Text(
                "Let the ragù simmer",
                modifier = Modifier.padding(top = 14.dp),
                style = text.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            )
            Text(
                "Lid half on, lowest flame. Stir every few minutes so nothing catches.",
                modifier = Modifier.padding(top = 8.dp),
                style = text.copy(fontSize = 16.sp, lineHeight = 22.sp, color = INK.copy(alpha = 0.65f)),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = safe.calculateBottomPadding() + 14.dp),
        ) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(18.dp)) {
                Text("Chef's note", style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
                Text(
                    "It is ready when the oil pools on top and the sauce coats a spoon.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = text.copy(fontSize = 15.sp, lineHeight = 21.sp, color = INK.copy(alpha = 0.65f)),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(58.dp).background(INK, RoundedCornerShape(29.dp)), contentAlignment = Alignment.Center) {
                Text("Next step", style = text.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }
        }
    }
}

/** All coordinates are dp; paints and paths are reused across frames. */
private class TimerPillExpandPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { fontFeatureSettings = "tnum" }
    private val normalFace = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumFace = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semiboldFace = if (Build.VERSION.SDK_INT >= 28) Typeface.create(normalFace, 600, false) else mediumFace
    private val clip = Path()
    private val rect = RectF()
    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2C2C2E.toInt()
    }

    private fun label(canvas: AndroidCanvas, value: String, x: Float, cy: Float, size: Float, color: Int = WHITE, medium: Boolean = false, centered: Boolean = true) {
        text.textSize = size
        text.typeface = if (medium) mediumFace else semiboldFace
        text.color = color
        text.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        canvas.drawText(value, x, cy - (text.ascent() + text.descent()) / 2, text)
    }

    private inline fun layer(canvas: AndroidCanvas, s: TimerPillExpandIsland, alpha: Float, draw: () -> Unit) {
        if (alpha <= 0f) return
        canvas.saveLayerAlpha(0f, 0f, s.width, s.height, (alpha * 255).roundToInt().coerceIn(0, 255))
        draw()
        canvas.restore()
    }

    fun draw(canvas: AndroidCanvas, cx: Float, cy: Float, s: TimerPillExpandIsland) {
        canvas.save()
        canvas.translate(cx - s.width / 2, cy - s.height / 2)
        rect.set(0f, 0f, s.width, s.height)
        // RN's overflow:hidden also clips its own shadow; the reference has a clean edge.
        canvas.drawRoundRect(rect, s.radius, s.radius, islandPaint)
        clip.reset()
        clip.addRoundRect(rect, s.radius, s.radius, Path.Direction.CW)
        canvas.clipPath(clip)

        layer(canvas, s, s.pill) {
            text.textSize = 21f
            text.typeface = mediumFace
            val wordWidth = text.measureText("Timers")
            val left = (s.width - wordWidth - 22 - 28) / 2
            label(canvas, "Timers", left, s.height / 2, 21f, medium = true, centered = false)
            val wx = left + wordWidth + 22 + 14
            val wy = s.height / 2
            stroke.color = ORANGE
            stroke.strokeWidth = 2.5f
            stroke.strokeCap = Paint.Cap.BUTT
            canvas.drawCircle(wx, wy, 12.75f, stroke)
            canvas.save()
            canvas.rotate(-35f, wx, wy - 3)
            fill.color = ORANGE
            canvas.drawRoundRect(wx - 1.25f, wy - 7.5f, wx + 1.25f, wy + 1.5f, 2f, 2f, fill)
            canvas.restore()
        }
        layer(canvas, s, s.card) {
            canvas.save()
            canvas.clipRect(28f, 0f, 92f, s.height)
            for (n in 18..31) {
                val near = (1 - abs(n - s.minutes)).coerceIn(0f, 1f)
                val alpha = 0.28f + 0.72f * near
                val scale = 0.68f + 0.32f * near
                val rowY = 106 + (n - s.minutes) * 38
                canvas.save()
                canvas.scale(scale, scale, 60f, rowY + 19)
                label(canvas, n.toString(), 28f, rowY + 19, 40f, ((alpha * 255).roundToInt() shl 24) or 0xFFFFFF, centered = false)
                canvas.restore()
            }
            canvas.restore()
            // The RN mins label begins at y=111 and has its natural 29 dp line box.
            label(canvas, "mins", 92f, 125.5f, 24f, medium = true, centered = false)
            val bx = s.width - 18 - 37
            val by = s.height - 18 - 37
            canvas.save()
            canvas.scale(s.start, s.start, bx, by)
            fill.color = 0xFF0E3B12.toInt()
            canvas.drawCircle(bx, by, 37f, fill)
            label(canvas, "Start", bx, by, 19f, 0xFF32D74B.toInt())
            canvas.restore()
        }
        layer(canvas, s, s.bar) {
            val y = s.height / 2
            stroke.strokeWidth = 3f
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.color = 0x40FF9F0A
            rect.set(18f, y - 32, 82f, y + 32)
            canvas.drawArc(rect, -90f, 359.9f, false, stroke)
            stroke.color = ORANGE
            stroke.strokeCap = Paint.Cap.ROUND
            val end = (1 - s.running / (26 * 60) * 6).toFloat()
            canvas.drawArc(rect, -90f, 359.9f * end, false, stroke)
            val left = max(0, 26 * 60 - floor(s.running).toInt())
            val seconds = left % 60
            label(canvas, "${left / 60}:${if (seconds < 10) "0" else ""}$seconds", 50f, y, 17f)
            val cancelX = s.width - 14 - 64 - 10 - 32
            canvas.save()
            canvas.scale(s.cancel, s.cancel, cancelX, y)
            fill.color = 0xFF48484A.toInt()
            canvas.drawCircle(cancelX, y, 32f, fill)
            label(canvas, "Cancel", cancelX, y, 14f)
            canvas.restore()
            val pauseX = s.width - 14 - 32
            fill.color = 0xFF3A2500.toInt()
            canvas.drawCircle(pauseX, y, 32f, fill)
            label(canvas, "Pause", pauseX, y, 14f, ORANGE)
        }
        canvas.restore()
    }
}
