package com.ngocdevv.nativecook.blocks.buttons.glassloadingbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private val WIDTH = 172.dp
private val HEIGHT = 118.dp
private const val RADIUS = 54f

// The canvas overflows the button by this much on every side to fit the shadow.
private val BLEED = 28.dp
private const val LOAD_MS = 5200f
private const val REST_MS = 1300L

// Where the colours are frozen when the system asks for reduced motion.
private const val STILL_TIME = 2.4f

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val NumberStyle = TextStyle(
    color = Color(0xFFDCDCDE),
    fontSize = 48.sp,
    lineHeight = 50.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    platformStyle = NoFontPadding,
)
private val PercentStyle = TextStyle(
    color = Color(0xFFCFCFD2),
    fontSize = 29.sp,
    fontWeight = FontWeight.SemiBold,
    platformStyle = NoFontPadding,
)
private val CaptionStyle = TextStyle(
    color = Color(0xFFECECEC),
    fontSize = 13.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.3.sp,
    platformStyle = NoFontPadding,
)

// Loading that surges and stalls a little instead of counting at a constant rate.
private fun loadCurve(t: Float) = t - 0.04f * sin(6f * Math.PI.toFloat() * t)

/**
 * A blurry colour that travels around the inside of the rim and swells in and out of view.
 *
 * @param angle Start angle on the rim (radians, clockwise from 3 o'clock).
 * @param speed Angular speed in rad/s.
 * @param pulse Rate of the brightness swell in rad/s.
 */
private class GlassBlob(
    r: Int,
    g: Int,
    b: Int,
    val angle: Float,
    val speed: Float,
    val pulse: Float,
    val phase: Float,
    val radius: Float,
) {
    // Centred on the origin; the canvas is translated to where the blob is.
    val brush = Brush.radialGradient(
        0f to rgba(r, g, b, 0.85f),
        0.5f to rgba(r, g, b, 0.4f),
        1f to rgba(r, g, b, 0f),
        center = Offset.Zero,
        radius = radius,
    )
}

private class GlassLoadingPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.4f), sigma = 12f)
    val clip = Path()
    private var w = Float.NaN
    private var h = Float.NaN

    val blobs = listOf(
        GlassBlob(58, 96, 190, angle = 3.4f, speed = 0.42f, pulse = 0.9f, phase = 0f, radius = 78f),
        GlassBlob(190, 62, 150, angle = 1.3f, speed = 0.34f, pulse = 0.7f, phase = 2.1f, radius = 72f),
        GlassBlob(150, 48, 48, angle = 2.5f, speed = -0.27f, pulse = 1.1f, phase = 4f, radius = 66f),
        GlassBlob(34, 122, 92, angle = 0.2f, speed = 0.31f, pulse = 0.8f, phase = 5.2f, radius = 60f),
    )

    fun update(x: Float, y: Float, w: Float, h: Float) {
        if (w == this.w && h == this.h) return
        this.w = w
        this.h = h
        clip.reset()
        clip.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(RADIUS)))
    }
}

/**
 * Dark liquid-glass capsule with a large percentage and a caption. Blurry blue, magenta, red and
 * green light drifts around the inside of its rim while the number counts up.
 *
 * @param label Caption under the percentage.
 * @param autoPlay Loads from 0 to 100 by itself, rests, and starts over.
 * @param progress Controlled progress, 0…1. When null a tap runs one load from 0 to 100.
 */
@Composable
fun GlassLoadingButton(
    label: String = "LOADING",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    progress: Float? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(0) }
    // Bumped to (re)start a load; 0 means nothing has been started yet.
    var run by remember { mutableIntStateOf(0) }

    val time = remember { mutableFloatStateOf(STILL_TIME) }

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

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue += min((now - last) / 1e6f, 64f) / 1000f
            last = now
        }
    }

    LaunchedEffect(run, autoPlay) {
        if (run == 0 && !autoPlay) return@LaunchedEffect
        val start = withFrameMillis { it }
        // Writing the same whole number again does not recompose, so this only costs a frame callback.
        while (true) {
            val t = min(1f, (withFrameMillis { it } - start) / LOAD_MS)
            percent = (100f * loadCurve(t)).roundToInt()
            if (t >= 1f) break
        }
        if (autoPlay) {
            delay(REST_MS)
            run++
        }
    }

    val shown = progress?.let { (100f * it.coerceIn(0f, 1f)).roundToInt() } ?: percent

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { GlassLoadingPaints() }
    val glowRadius = with(LocalDensity.current) { 8.dp.toPx() }
    val numberStyle = remember(glowRadius) {
        NumberStyle.copy(shadow = Shadow(color = rgba(255, 255, 255, 0.28f), blurRadius = glowRadius))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                run++
            }
            .semantics { contentDescription = "$label $shown%" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    paints.update(x, y, w, h)
                    val cx = x + w / 2
                    val cy = y + h / 2
                    val center = Offset(cx, cy)
                    val t = time.floatValue

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x, y + 8, w, h, RADIUS) }
                    drawRoundRect(Color(0xFF1B1B1B), Offset(x, y), Size(w, h), CornerRadius(RADIUS))

                    clipPath(paints.clip) {
                        for (blob in paints.blobs) {
                            val a = blob.angle + blob.speed * t
                            translate(cx + (w / 2 - 12) * cos(a), cy + (h / 2 - 6) * sin(a)) {
                                drawCircle(
                                    brush = blob.brush,
                                    radius = blob.radius,
                                    center = Offset.Zero,
                                    alpha = 0.25f + 0.75f * (0.5f + 0.5f * sin(blob.pulse * t + blob.phase)),
                                )
                            }
                        }
                        // Keeps the middle dark so the colours read as light caught in the rim of the glass.
                        scale(1f, h / w, pivot = center) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to rgba(24, 24, 24, 0.94f),
                                    0.42f to rgba(24, 24, 24, 0.72f),
                                    1f to rgba(24, 24, 24, 0f),
                                    center = center,
                                    radius = w * 0.52f,
                                ),
                                radius = w * 0.52f,
                                center = center,
                            )
                        }
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to rgba(255, 255, 255, 0.07f),
                                0.5f to rgba(255, 255, 255, 0f),
                                1f to rgba(255, 255, 255, 0.02f),
                                startY = y,
                                endY = y + h,
                            ),
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                        )
                    }

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(255, 255, 255, 0.3f),
                            0.5f to rgba(255, 255, 255, 0.07f),
                            1f to rgba(255, 255, 255, 0.16f),
                            startY = y,
                            endY = y + h,
                        ),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(w - 1, h - 1),
                        cornerRadius = CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.size(WIDTH, HEIGHT).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            // Roboto's taller line box pushes the block down; these lifts put both lines where iOS has them.
            Row(Modifier.offset(y = (-2.5).dp)) {
                BasicText(text = shown.toString(), style = numberStyle, modifier = Modifier.alignByBaseline())
                BasicText(
                    text = "%",
                    style = PercentStyle,
                    modifier = Modifier.alignByBaseline().padding(start = 1.dp),
                )
            }
            // Tucked up under the digits, as in the reference.
            BasicText(text = label, style = CaptionStyle, maxLines = 1, modifier = Modifier.offset(y = (-7).dp))
        }
    }
}
