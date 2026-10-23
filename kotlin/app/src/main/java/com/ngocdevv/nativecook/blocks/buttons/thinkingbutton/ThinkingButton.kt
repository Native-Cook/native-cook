package com.ngocdevv.nativecook.blocks.buttons.thinkingbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val WIDTH = 220f
private const val HEIGHT = 64f

// The liquid is drawn this much past the pill so blurred edges never show inside it.
private val SPILL = 24.dp
private const val SPILL_F = 24f
private const val PHASE_MS = 2800L
private const val FADE_MS = 500

// Where the liquid rests when motion is reduced.
private const val STILL_TIME = 1.2f
private const val STEPS = 24

private val Rainbow = arrayOf(
    0.02f to Color(0xFFF28A1C),
    0.2f to Color(0xFFE8C21A),
    0.36f to Color(0xFF58D04A),
    0.52f to Color(0xFF1FC7C4),
    0.62f to Color(0xFF2F6FE8),
    0.8f to Color(0xFFC035E0),
    1f to Color(0xFF7A2CF0),
)

// Fluted glass over the silver look: each strip refracts the bands by its own amount.
private val FluteShift = floatArrayOf(0f, 0.1f, -0.05f, 0.14f, 0.03f, -0.08f)
private val FluteTint = floatArrayOf(0f, 0.05f, 0.02f, 0.07f, 0.02f, 0.04f)
private val Silver = arrayOf(
    0f to Color(0xFF5D5D60),
    0.2f to Color(0xFF8E8E90),
    0.36f to Color(0xFFB9B7B3),
    0.42f to Color(0xFF7A5F45),
    0.47f to Color(0xFF0C0B0B),
    0.6f to Color(0xFF0A0A0B),
    0.75f to Color(0xFF2A2A2D),
    1f to Color(0xFF5D5D60),
)

// Band normal (the bands run about 24° off horizontal) and the distance between two bands.
private const val BAND_NX = -0.41f
private const val BAND_NY = 0.91f
private const val BAND_PERIOD = 130f

private val NavyColors = listOf(Color(0xFF07031C), Color(0xFF0E0632))
private val Fog = arrayOf(
    0f to rgba(190, 190, 198, 0f),
    0.6f to rgba(190, 190, 198, 0.7f),
    1f to rgba(226, 230, 229, 1f),
)
private val Blue = arrayOf(
    0f to Color(0xFF1B3FD0),
    0.14f to Color(0xFF1A8FE6),
    0.38f to Color(0xFF14B6DC),
    1f to Color(0xFF19B0D8),
)
private val Red = arrayOf(
    0f to Color(0xFFEA5A50),
    0.3f to Color(0xFFDE3A32),
    0.7f to Color(0xFFD41A1A),
    1f to Color(0xFFCF1717),
)
private val SeamGlow = rgba(255, 150, 205, 0.55f)
private val Glass = arrayOf(
    0f to Color.White.copy(alpha = 0.1f),
    0.5f to Color.White.copy(alpha = 0.04f),
    1f to Color.White.copy(alpha = 0.4f),
)
private val Rim = arrayOf(
    0f to Color.White.copy(alpha = 0.42f),
    0.5f to Color.White.copy(alpha = 0.16f),
    1f to Color.White.copy(alpha = 0.7f),
)
private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 22.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.1.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 2f), blurRadius = 8f),
)

/** Height of the rainbow's surface at [u] (0–1 across the pill), as a fraction of the height. */
private fun waveLevel(u: Float, t: Float) =
    0.5f + 0.12f * sin(TAU * (u * 1.1f + t * 0.21f)) + 0.07f * sin(TAU * (u * 2.3f - t * 0.33f) + 1.3f)

/** Everything below the rainbow's surface, lowered by [drop]. */
private fun Path.wave(x: Float, y: Float, w: Float, h: Float, t: Float, drop: Float) {
    val x0 = x - SPILL_F
    val x1 = x + w + SPILL_F
    reset()
    moveTo(x0, y + h + SPILL_F)
    for (i in 0..STEPS) {
        val px = x0 + (x1 - x0) * i / STEPS
        lineTo(px, y + h * waveLevel((px - x) / w, t) + drop)
    }
    lineTo(x1, y + h + SPILL_F)
    close()
}

/** Where the blue meets the red at [v] (0–1 down the pill), as a fraction of the width. */
private fun seamLevel(v: Float, t: Float) =
    0.46f + 0.05f * sin(TAU * (v * 0.9f + t * 0.27f)) + 0.03f * sin(TAU * (v * 1.7f - t * 0.41f) + 1f)

/** The seam between blue and red; [closed] adds everything to its right (the red side). */
private fun Path.seam(x: Float, y: Float, w: Float, h: Float, t: Float, closed: Boolean) {
    val y0 = y - SPILL_F
    val y1 = y + h + SPILL_F
    reset()
    for (i in 0..STEPS) {
        val py = y0 + (y1 - y0) * i / STEPS
        val px = x + w * seamLevel((py - y) / h, t)
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    if (closed) {
        lineTo(x + w + SPILL_F, y1)
        lineTo(x + w + SPILL_F, y0)
        close()
    }
}

/**
 * AI status pill. Its label steps through "Thinking…", "Searching…", "Planning…" and each phase
 * fills the pill with its own flowing liquid (rainbow wave, fluted silver, blue meeting red) under
 * a glassy rim. The soft edges need API 31; older devices draw the same shapes unblurred.
 *
 * @param labels One label per look: rainbow, silver, blue-to-red.
 * @param autoPlay Steps through the three phases by itself. A tap always advances to the next one.
 * @param phase Controlled phase index (0–2). Leave null to let the button keep its own.
 */
@Composable
fun ThinkingButton(
    labels: List<String> = listOf("Thinking...", "Searching...", "Planning..."),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    phase: Int? = null,
    onPhaseChange: (Int) -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var own by remember { mutableIntStateOf(0) }
    val current = (((phase ?: own) % 3) + 3) % 3
    val advance by rememberUpdatedState {
        val next = (current + 1) % 3
        if (phase == null) own = next
        onPhaseChange(next)
    }

    var clock by remember { mutableFloatStateOf(STILL_TIME) }
    val paths = remember { List(4) { Path() } }
    val bloom5 = rememberBloom(5.dp)
    val bloom8 = rememberBloom(8.dp)
    val bloom4 = rememberBloom(4.dp)
    val bloomGlass = rememberBloom(3.5.dp)

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // The liquid always flows; only reduced motion stops it.
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { clock = STILL_TIME + (it - start) / 1e9f }
        }
    }

    // Keyed on `current` so a tap restarts the wait instead of being cut short.
    LaunchedEffect(autoPlay, current) {
        if (!autoPlay) return@LaunchedEffect
        delay(PHASE_MS)
        advance()
    }

    val fade0 by animateFloatAsState(if (current == 0) 1f else 0f, tween(FADE_MS), label = "rainbow")
    val fade1 by animateFloatAsState(if (current == 1) 1f else 0f, tween(FADE_MS), label = "silver")
    val fade2 by animateFloatAsState(if (current == 2) 1f else 0f, tween(FADE_MS), label = "blueRed")
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                advance()
            }
            .semantics { contentDescription = labels[current] }
            .size(WIDTH.dp, HEIGHT.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Keeps the pill opaque half-way through a cross-fade.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(percent = 50))) {
            Canvas(Modifier.matchParentSize()) { drawRect(Color(0xFF0B0B12)) }

            // Rainbow wave over navy, with a pale fog pooling at the bottom.
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fade0 }) {
                Canvas(Modifier.matchParentSize()) { drawRect(Brush.verticalGradient(NavyColors)) }
                Box(Modifier.matchParentSize().bleed(SPILL)) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = bloom5 }) {
                        if (fade0 <= 0f) return@Canvas
                        surface(SPILL) { x, y, w, h ->
                            // The colours drift a little sideways under the surface.
                            val drift = 14f * sin(clock * 0.6f)
                            paths[0].wave(x, y, w, h, clock, 0f)
                            drawPath(
                                paths[0],
                                Brush.horizontalGradient(*Rainbow, startX = x + drift, endX = x + w + drift),
                            )
                        }
                    }
                    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = bloom8 }) {
                        if (fade0 <= 0f) return@Canvas
                        surface(SPILL) { x, y, w, h ->
                            paths[1].wave(x, y, w, h, clock, h * 0.2f)
                            drawPath(paths[1], Brush.verticalGradient(*Fog, startY = y + h * 0.55f, endY = y + h))
                        }
                    }
                }
            }

            // Silver bands seen through fluted glass.
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = fade1 }) {
                if (fade1 <= 0f) return@Canvas
                surface(0.dp) { x, y, w, h ->
                    val strip = w / FluteShift.size
                    for (index in FluteShift.indices) {
                        val s = (clock / 3.8f % 1f) * BAND_PERIOD + FluteShift[index] * BAND_PERIOD
                        val e = s + BAND_PERIOD
                        val left = x + strip * index
                        // Half a point of overlap hides the seam between strips.
                        drawRect(
                            brush = Brush.linearGradient(
                                *Silver,
                                start = Offset(x + w / 2 + BAND_NX * s, y + h / 2 + BAND_NY * s),
                                end = Offset(x + w / 2 + BAND_NX * e, y + h / 2 + BAND_NY * e),
                                tileMode = TileMode.Repeated,
                            ),
                            topLeft = Offset(left, y),
                            size = Size(strip + 0.5f, h),
                        )
                        drawRect(Color.White, Offset(left, y), Size(strip, h), alpha = FluteTint[index])
                    }
                }
            }

            // Blue on the left, red pushing in from the right along a glowing seam.
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fade2 }) {
                Canvas(Modifier.matchParentSize()) { drawRect(Brush.horizontalGradient(*Blue)) }
                Box(Modifier.matchParentSize().bleed(SPILL)) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = bloom4 }) {
                        if (fade2 <= 0f) return@Canvas
                        surface(SPILL) { x, y, w, h ->
                            paths[2].seam(x, y, w, h, clock, closed = true)
                            drawPath(paths[2], Brush.horizontalGradient(*Red, startX = x + w * 0.42f, endX = x + w))
                        }
                    }
                    if (bloom5 != null) {
                        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = bloom5 }) {
                            if (fade2 <= 0f) return@Canvas
                            surface(SPILL) { x, y, w, h ->
                                paths[3].seam(x, y, w, h, clock, closed = false)
                                drawPath(paths[3], SeamGlow, style = Stroke(5f))
                            }
                        }
                    }
                }
            }

            // Glass: a soft light hugging the inside of the edge, strongest along the bottom.
            if (bloomGlass != null) {
                Box(Modifier.matchParentSize().bleed(SPILL)) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = bloomGlass }) {
                        surface(SPILL) { x, y, w, h ->
                            drawRoundRect(
                                brush = Brush.verticalGradient(*Glass, startY = y, endY = y + h),
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(h / 2),
                                style = Stroke(7f),
                            )
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { x, y, w, h ->
                drawRoundRect(
                    brush = Brush.verticalGradient(*Rim, startY = y, endY = y + h),
                    topLeft = Offset(x + 0.5f, y + 0.5f),
                    size = Size(w - 1f, h - 1f),
                    cornerRadius = CornerRadius(h / 2 - 0.5f),
                    style = Stroke(1f),
                )
            }
        }
        BasicText(labels[0], style = LabelStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = fade0 })
        BasicText(labels[1], style = LabelStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = fade1 })
        BasicText(labels[2], style = LabelStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = fade2 })
    }
}
