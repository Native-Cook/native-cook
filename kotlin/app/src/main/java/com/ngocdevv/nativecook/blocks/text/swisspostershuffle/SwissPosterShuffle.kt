package com.ngocdevv.nativecook.blocks.text.swisspostershuffle

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

// RN reference: two six-panel strips, each repeating its first two panels. Four 2 s steps
// hold for 1.3 s and slide for 0.7 s (ease-in-out cubic). The reversed right strip moves down,
// 80 ms behind the left. Drag one panel height per step; release snaps for 0.35 s, holds 2 s,
// then resumes from that step. Reduced motion keeps the initial grid still.
/** Seconds. */
const val LOOP = 8.0
private const val STEP = 2.0
private const val MOVE_START = 1.3
private const val LAG = 0.08
private const val SNAP = 0.35
private const val HOLD = 2.0
private const val BLACK = 0xFF0B0B0B.toInt()
private const val WHITE = 0xFFF6F5F0.toInt()
private const val RED = 0xFFE8261C.toInt()
private val PAPER = Color(0xFFEFEDE6)

private fun ease(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun wrap(step: Double): Double = ((step % 4) + 4) % 4
private fun stepAt(time: Double): Double {
    val u = ((time % LOOP) + LOOP) % LOOP
    val k = floor(u / STEP)
    return k + ease(((u - k * STEP - MOVE_START) / (STEP - MOVE_START)).coerceIn(0.0, 1.0))
}

/** Frame state is read only in the canvas, never by the screen's composition. */
private class Playback {
    var elapsed by mutableDoubleStateOf(0.0)
    var revision by mutableIntStateOf(0)
    var manual by mutableStateOf(false)
    var touching = false
    var left = 0.0
    var right = 0.0
    private var origin = 0.0
    private var released = 0.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0

    fun frame(now: Double, autoPlay: Boolean) {
        if (!touching) {
            if (manual) {
                val since = now - released
                val s = if (since < SNAP) from + (snapTo - from) * ease((since / SNAP).coerceIn(0.0, 1.0)) else snapTo
                left = s
                right = s
                if (since > SNAP + HOLD) {
                    manual = false
                    origin = now - wrap(snapTo) * STEP
                }
            } else if (autoPlay) {
                val time = now - origin
                left = stepAt(time)
                right = stepAt(time - LAG)
            }
        }
        elapsed = now
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = left
        right = left
        lastStep = floor(left + 0.5)
        revision++
    }

    fun drag(translationY: Float, panelHeight: Float): Boolean {
        left = grabbed - translationY / panelHeight
        right = left
        val nearest = floor(left + 0.5) // JS Math.round, including negative half steps.
        val crossed = nearest != lastStep
        lastStep = nearest
        revision++
        return crossed
    }

    fun finish() {
        if (!touching) return
        touching = false
        from = left
        snapTo = floor(left + 0.5)
        released = elapsed
        revision++
    }
}

private data class Panel(val bg: Int, val fg: Int, val word: String, val turn: Float, val size: Float, val end: Boolean)
private val LEFT = listOf(
    Panel(BLACK, WHITE, "Typo", 0f, 76f, true),
    Panel(WHITE, BLACK, "Week", -90f, 80f, true),
    Panel(RED, WHITE, "Seminar", 90f, 60f, false),
    Panel(BLACK, WHITE, "2026", 180f, 74f, true),
)
private val RIGHT = listOf(
    Panel(WHITE, BLACK, "in\nMotion", 0f, 46f, false),
    Panel(RED, WHITE, "Monday\n13:30–16:30\nRoom H4.07", 0f, 19f, false),
    Panel(BLACK, WHITE, "Typo", 90f, 76f, true),
    Panel(WHITE, BLACK, "Week", 180f, 76f, false),
)

/** Rasterize the static type once; each frame only translates the resulting images. */
private fun panelImage(panel: Panel, width: Float, height: Float, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(ceil(width * density).toInt(), ceil(height * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.scale(density, density)
    canvas.drawColor(panel.bg)
    canvas.clipRect(0f, 0f, width, height)
    val sideways = abs(panel.turn) == 90f
    val w = if (sideways) height else width
    val h = if (sideways) width else height
    val large = panel.size > 30f
    val tracking = if (large) -panel.size * 0.04f else 0f
    val lineHeight = panel.size * if (large) 0.95f else 1.2f
    val face = if (large) Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 600, false)
    else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = panel.fg
        typeface = face
        textSize = panel.size
        letterSpacing = tracking / textSize
    }
    val lines = panel.word.split('\n')
    // RN adjustsFontSizeToFit retains its specified tracking and line height when shrinking.
    if (lines.maxOf { paint.measureText(it) } > w - 20f) {
        var low = 4f
        var high = panel.size
        repeat(16) {
            paint.textSize = (low + high) / 2
            paint.letterSpacing = tracking / paint.textSize
            if (lines.maxOf { paint.measureText(it) } <= w - 20f) low = paint.textSize else high = paint.textSize
        }
        paint.textSize = low
        paint.letterSpacing = tracking / low
    }
    canvas.translate(width / 2, height / 2)
    canvas.rotate(panel.turn)
    val top = if (panel.end) h / 2 - 10 - lines.size * lineHeight else -h / 2 + 10
    val metrics = paint.fontMetrics
    val baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
    lines.forEachIndexed { i, line -> canvas.drawText(line, -w / 2 + 10, baseline + i * lineHeight, paint) }
    return bitmap
}

@Composable
fun SwissPosterShuffle(
    modifier: Modifier = Modifier,
    title: String = "Typo Week 2026 · Seminar",
    whenText: String = "Monday, 13:30–16:30 · Room H4.07",
    action: String = "Save ticket",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val playback = remember { Playback() }
    DisposableEffect(lifecycle, playback) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!active) playback.finish()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced, autoPlay, playback.manual) {
        if (!active || reduced || (!autoPlay && !playback.manual)) return@LaunchedEffect
        val base = playback.elapsed
        var firstFrame = 0L
        while (true) withFrameNanos { frame ->
            if (firstFrame == 0L) firstFrame = frame
            playback.frame(base + (frame - firstFrame) / 1e9, autoPlay)
        }
    }

    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(modifier.fillMaxSize().background(PAPER)) {
        val width = constraints.maxWidth / density
        val height = constraints.maxHeight / density
        val posterTop = insets.calculateTopPadding().value + 8f
        val posterHeight = (height * 0.68f - posterTop).roundToInt().toFloat()
        val panelHeight = posterHeight / 2
        val columnWidth = width / 2
        val destination = remember { RectF() }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val images = remember(columnWidth, panelHeight, density) {
            val left = LEFT.map { panelImage(it, columnWidth, panelHeight, density) }
            val right = RIGHT.map { panelImage(it, columnWidth, panelHeight, density) }
            (left + left.take(2)) to (right + right.take(2)).reversed()
        }
        Canvas(
            Modifier.offset(y = posterTop.dp).fillMaxWidth().height(posterHeight.dp)
                .clipToBounds().background(Color(BLACK))
                .semantics { contentDescription = "Typo Week poster. Drag to rearrange." }
                .pointerInput(panelHeight, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        playback.begin()
                        var activated = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed || change.isConsumed) break
                                val translation = change.position - down.position
                                if (!activated && hypot(translation.x, translation.y) >= 4 * density) activated = true
                                if (activated) {
                                    change.consume()
                                    if (playback.drag(translation.y / density, panelHeight)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        } finally {
                            playback.finish()
                        }
                    }
                },
        ) {
            playback.elapsed
            playback.revision
            val leftStep = if (reduced) 0.0 else playback.left
            val rightStep = if (reduced) 0.0 else playback.right
            val canvas = drawContext.canvas.nativeCanvas
            val saved = canvas.save()
            canvas.scale(density, density)
            canvas.clipRect(0f, 0f, width, posterHeight)
            fun strip(bitmaps: List<Bitmap>, x: Float, offset: Float) {
                bitmaps.forEachIndexed { i, bitmap ->
                    val y = i * panelHeight + offset
                    if (y < posterHeight && y + panelHeight > 0) {
                        destination.set(x, y, x + columnWidth, y + panelHeight)
                        canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
                    }
                }
            }
            strip(images.first, 0f, (-wrap(leftStep) * panelHeight).toFloat())
            strip(images.second, columnWidth, (-(4 - wrap(rightStep)) * panelHeight).toFloat())
            canvas.restoreToCount(saved)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = insets.calculateBottomPadding() + 16.dp),
        ) {
            Text(title, style = ticketStyle(24f, 30f, FontWeight.SemiBold, Color(BLACK), -0.4f))
            Spacer(Modifier.height(6.dp))
            Text(whenText, style = ticketStyle(15f, 20f, FontWeight.Normal, Color(0xFF6B6A66)))
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).background(Color(BLACK), RoundedCornerShape(4.dp))
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = ticketStyle(17f, 20f, FontWeight.SemiBold, Color.White))
            }
        }
    }
}

private fun ticketStyle(size: Float, lineHeight: Float, weight: FontWeight, color: Color, tracking: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
