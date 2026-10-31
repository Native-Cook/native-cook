package com.ngocdevv.nativecook.blocks.buttons.candyknobtoggle

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot
import kotlin.math.roundToInt

// Sprinkle's settings screen, faithfully ported from CandyKnobToggle.tsx.
// 300 x 128 dp pill; 112 dp candy, inset 8, travel 172, center at screen height / 2.
// LOOP 6 s: on 0–1.2; cubic roll left 1.2–2; landing 2–2.6; off until 4.2;
// cubic roll right 4.2–5; landing 5–5.6; on until 6. Rotation = position * 172 / 56 rad.
// Tap or drag (6 dp) interrupts playback; snap 0.8 s, then hold 2 s, resume this side.
// Reduced motion stops the clock and touch (initially on/rest); backgrounding freezes the clock.
@Composable
fun CandyKnobToggle(
    modifier: Modifier = Modifier,
    brand: String = "Sprinkle",
    title: String = "Treat\nmode",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val screenDensity = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = true)
    val motion = remember { CandyKnobToggleMotion() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val art = remember(context, screenDensity.density) { CandyKnobToggleArt(context.assets, screenDensity.density) }
    val headline = remember(context) { Typeface.createFromAsset(context.assets, CANDY_ASSETS + "BricolageHeadline.ttf") }
    val safeTop = WindowInsets.safeDrawing.getTop(screenDensity) / screenDensity.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screenDensity) / screenDensity.density
    // Per-frame state is read only by Canvas, never by composition or layout.
    LaunchedEffect(active, reduced, autoPlay, motion, wake.intValue) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { n ->
                val dt = if (previous == 0L) 0.0 else (n - previous) / 1e9
                previous = n
                motion.advance(dt, autoPlay)
                tick.intValue++
            }
            if (!autoPlay && !motion.manual && !motion.touching) break
        }
    }
    fun commit(target: Double) {
        motion.release(target)
        tick.intValue++
        wake.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val x = (width - 300f) / 2
        val y = height / 2 - 64
        val text = remember(width, height, safeTop, safeBottom, headline, brand, title, screenDensity) {
            CandyKnobToggleText(screenDensity.density, width, height, safeTop, safeBottom, headline, brand, title)
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val f = motion.frame
            val c = drawContext.canvas.nativeCanvas
            c.save()
            c.scale(screenDensity.density, screenDensity.density)
            c.drawColor(candyMix(0xD7EFB4, 0xF7C9DD, f.position))
            text.draw(c, f)
            art.draw(c, f, x, y)
            c.restore()
        }
        Box(
            Modifier.offset((x - 10).dp, (y - 20).dp).size(320.dp, 168.dp)
                .semantics {
                    contentDescription = "Treat mode"
                    role = Role.Switch
                    if (reduced) disabled()
                    onClick {
                        if (reduced) false else {
                            commit(if (motion.frame.position > 0.5) 0.0 else 1.0)
                            true
                        }
                    }
                }
                .pointerInput(motion, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val grab = motion.frame.position
                        var dragging = false
                        var cancelled = false
                        var end = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) { cancelled = true; break }
                            end = change
                            if (change.isConsumed) { cancelled = true; break }
                            val delta = change.position - down.position
                            if (!change.pressed) break
                            if (!dragging && hypot(delta.x, delta.y) >= 6 * this.density) dragging = true
                            if (dragging) {
                                change.consume()
                                motion.drag(grab + delta.x / this.density / CANDY_TRAVEL)
                                tick.intValue++
                            }
                        }
                        if (dragging) {
                            commit(if (motion.frame.position > 0.5) 1.0 else 0.0)
                        } else if (!cancelled && end.uptimeMillis - down.uptimeMillis <= 500 &&
                            end.position.x in 0f..size.width.toFloat() && end.position.y in 0f..size.height.toFloat()
                        ) {
                            commit(if (motion.frame.position > 0.5) 0.0 else 1.0)
                        }
                    }
                },
        )
    }
}

/** Layouts and glyph shaping are cached; only ink and state opacity change each frame. */
private class CandyKnobToggleText(
    private val density: Float,
    private val width: Float,
    private val height: Float,
    private val safeTop: Float,
    private val safeBottom: Float,
    headline: Typeface,
    brand: String,
    title: String,
) {
    private class Label(val layout: StaticLayout, val opacity: Float = 1f, val leading: Float = 0f) {
        val paint get() = layout.paint
    }
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(sans, 600, false)
        else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.argb(115, 255, 255, 255) }
    private fun label(
        value: String, size: Float, face: Typeface = sans, maxWidth: Float = width - 48,
        lineHeight: Float? = null, spacing: Float = 0f, alpha: Float = 1f,
    ): Label {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = size * this@CandyKnobToggleText.density
            letterSpacing = spacing / size
        }
        val builder = StaticLayout.Builder.obtain(value, 0, value.length, p, (maxWidth * density).roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        val leading = lineHeight?.let { it * density - (p.fontMetrics.descent - p.fontMetrics.ascent) } ?: 0f
        if (lineHeight != null) builder.setLineSpacing(leading, 1f)
        // RN keeps the font's descent in each explicit line-height box. StaticLayout only
        // applies extra spacing between lines, so shift its first baseline by the same leading.
        return Label(builder.build(), alpha, leading / density)
    }
    private val brandLabel = label(brand, 22f, headline)
    private val settings = label("Settings", 14f, semibold, alpha = 0.7f)
    private val titleLabel = label(title, 64f, headline, lineHeight = 64f, spacing = -2f)
    private val sub = label("Let the app show you the sweet stuff first.", 17f, maxWidth = 280f, lineHeight = 23f, alpha = 0.75f)
    private val on = label("On · sprinkles everywhere", 22f, headline)
    private val off = label("Off · just the gummies", 22f, headline)
    private val cardTitle = label("Daily treat reminder", 17f, bold, maxWidth = width - 76)
    private val cardMeta = label("Every day at 15:30", 15f, maxWidth = width - 76, alpha = 0.7f)
    private val foot = label("You can change this any time.", 13f, alpha = 0.6f)
    private fun naturalWidth(l: Label) = (0 until l.layout.lineCount).maxOf { l.layout.getLineWidth(it) } / density
    private fun h(l: Label) = l.layout.height / density
    private fun draw(c: NativeCanvas, label: Label, x: Float, y: Float, ink: Int, alpha: Double = 1.0) {
        if (alpha <= 0) return
        label.paint.color = ink
        label.paint.alpha = (255 * label.opacity * alpha).roundToInt().coerceIn(0, 255)
        c.save()
        c.translate(x, y + label.leading)
        c.scale(1 / density, 1 / density)
        label.layout.draw(c)
        c.restore()
    }
    fun draw(c: NativeCanvas, f: CandyKnobToggleFrame) {
        val ink = candyMix(0x24420E, 0x5A1233, f.position)
        val head = safeTop + 8
        draw(c, brandLabel, 24f, head + (34 - h(brandLabel)) / 2, ink)
        draw(c, settings, width - 24 - naturalWidth(settings), head + (34 - h(settings)) / 2, ink)
        val titleTop = head + 34 + 18
        draw(c, titleLabel, 24f, titleTop, ink)
        draw(c, sub, 24f, titleTop + titleLabel.layout.lineCount * 64 + 12, ink)
        val stateY = height / 2 + 64 + 34
        val onAlpha = candyClamp((f.position - 0.5) * 3)
        val offAlpha = candyClamp((0.5 - f.position) * 3)
        draw(c, on, (width - naturalWidth(on)) / 2, stateY + (8 * (1 - onAlpha)).toFloat(), ink, onAlpha)
        draw(c, off, (width - naturalWidth(off)) / 2, stateY + (8 * (1 - offAlpha)).toFloat(), ink, offAlpha)
        val footY = height - safeBottom - 18 - h(foot)
        val cardBottom = footY - 12
        val cardHeight = 18 + h(cardTitle) + 4 + h(cardMeta) + 18
        val cardTop = cardBottom - cardHeight
        c.drawRoundRect(20f, cardTop, width - 20, cardBottom, 24f, 24f, cardPaint)
        draw(c, cardTitle, 38f, cardTop + 18, ink)
        draw(c, cardMeta, 38f, cardTop + 18 + h(cardTitle) + 4, ink)
        draw(c, foot, (width - naturalWidth(foot)) / 2, footY, ink)
    }
}
