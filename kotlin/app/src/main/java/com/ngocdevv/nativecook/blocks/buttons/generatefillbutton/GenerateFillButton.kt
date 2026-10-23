package com.ngocdevv.nativecook.blocks.buttons.generatefillbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 168f
private const val HEIGHT = 58f

// The canvas overflows the button by this much on every side so its edge is not clipped.
private val BLEED = 4.dp

// Radius of the gradient's rounded leading edge as it sweeps in from the left.
private const val LEAD = 44f
private const val SWEEP_MS = 2400
private const val FADE_MS = 500
private const val AUTOPLAY_PAUSE_MS = 1500L
private const val ICON_X = 29f

// Where the liquid rests when motion is reduced.
private const val STILL_TIME = 1f

private val Sheen = arrayOf(
    0f to rgba(125, 125, 125, 0.75f),
    0.45f to rgba(70, 70, 70, 0.35f),
    1f to rgba(0, 0, 0, 0f),
)
private val Flow = arrayOf(
    0f to Color(0xFF8B3CF5),
    0.22f to Color(0xFF5A5CF2),
    0.5f to Color(0xFF2F86F0),
    0.78f to Color(0xFF19A8F0),
    1f to Color(0xFF12C8DC),
)

/**
 * A soft blob of colour that wanders over the base gradient; positions are fractions of the size.
 */
private class Blob(
    r: Int, g: Int, b: Int,
    val radius: Float,
    val bx: Float, val sx: Float, val vx: Float, val px: Float,
    val by: Float, val sy: Float, val vy: Float,
) {
    val stops = arrayOf(0f to rgba(r, g, b, 0.85f), 0.5f to rgba(r, g, b, 0.4f), 1f to rgba(r, g, b, 0f))
}

private val Blobs = listOf(
    Blob(40, 70, 240, 0.9f, 0.45f, 0.3f, 0.6f, 2f, 0f, 0.3f, 0.8f),
    Blob(10, 200, 240, 0.95f, 0.62f, 0.25f, 0.9f, 0f, 0f, 0.25f, 0.7f),
    Blob(165, 60, 245, 0.85f, 0.1f, 0.12f, 0.7f, 1f, 0f, 0.15f, 0.5f),
    Blob(20, 210, 180, 0.6f, 0.95f, 0.08f, 0.5f, 0f, 0.3f, 0.1f, 0.6f),
)

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 21.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.2).sp,
)

private fun Path.sparkle(cx: Float, cy: Float, r: Float, k: Float = 0.3f) {
    val c = r * k
    reset()
    moveTo(cx, cy - r)
    quadraticTo(cx + c, cy - c, cx + r, cy)
    quadraticTo(cx + c, cy + c, cx, cy + r)
    quadraticTo(cx - c, cy + c, cx - r, cy)
    quadraticTo(cx - c, cy - c, cx, cy - r)
    close()
}

/**
 * Black "Generate" pill with a sparkle. Tapping it sweeps a violet-to-cyan gradient in from the
 * left behind a rounded edge, the label becomes "Generating" and the colours keep flowing until
 * the run ends and the pill fades back to black.
 *
 * @param activeLabel Shown while the gradient is flowing.
 * @param autoPlay Replays a generation by itself with a pause between runs.
 * @param generating Controlled state: `true` sweeps the gradient in and keeps it flowing, `false`
 *   returns to black. Leave null and a tap runs one generation of [durationMs] by itself.
 */
@Composable
fun GenerateFillButton(
    label: String = "Generate",
    activeLabel: String = "Generating",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    generating: Boolean? = null,
    durationMs: Long = 5600,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var own by remember { mutableStateOf(false) }
    val active = generating ?: own

    val sweep = remember { Animatable(0f) }
    val lit = remember { Animatable(0f) }
    val mix = remember { Animatable(0f) }
    var clock by remember { mutableFloatStateOf(STILL_TIME) }
    var flowing by remember { mutableStateOf(false) }
    val paths = remember { List(3) { Path() } }
    val layerPaint = remember { Paint() }

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

    LaunchedEffect(active) {
        if (active) {
            flowing = true
            sweep.snapTo(0f)
            lit.snapTo(1f)
            launch { mix.animateTo(1f, tween(160)) }
            sweep.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        } else {
            launch { mix.animateTo(0f, tween(160, delayMillis = 140)) }
            lit.animateTo(0f, tween(FADE_MS))
            // The colours keep moving until the fade back to black has finished.
            flowing = false
        }
    }

    // The wandering colours and the twinkle only run while lit, and never with reduced motion.
    LaunchedEffect(flowing, reducedMotion) {
        if (!flowing || reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        val from = clock
        while (true) {
            withFrameNanos { clock = from + (it - start) / 1e9f }
        }
    }

    // An uncontrolled run: generate for `durationMs`, then return.
    LaunchedEffect(own) {
        if (!own) return@LaunchedEffect
        delay(durationMs)
        own = false
    }

    LaunchedEffect(autoPlay, own, generating) {
        if (!autoPlay || own || generating != null) return@LaunchedEffect
        delay(AUTOPLAY_PAUSE_MS + FADE_MS)
        own = true
    }

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
                if (generating == null) own = true
            }
            .semantics { contentDescription = if (active) activeLabel else label }
            .size(WIDTH.dp, HEIGHT.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val cy = y + h / 2
                    val pill = paths[0]
                    pill.reset()
                    pill.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
                    drawPath(pill, Color.Black)

                    clipPath(pill) {
                        // Soft grey sheen in the lower right corner of the black pill.
                        drawRect(
                            brush = Brush.radialGradient(
                                *Sheen,
                                center = Offset(x + w * 0.92f, y + h * 1.05f),
                                radius = w * 0.55f,
                            ),
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                        )

                        if (lit.value > 0f) {
                            // Everything left of a big rounded edge that travels from the left end to past the right end.
                            val edge = -2f + sweep.value * (w + 14f)
                            val lead = paths[1]
                            lead.reset()
                            lead.addRoundRect(RoundRect(x - 200f, cy - LEAD, x + edge, cy + LEAD, CornerRadius(LEAD)))
                            layerPaint.alpha = lit.value
                            drawIntoCanvas { it.saveLayer(Rect(x, y, x + w, y + h), layerPaint) }
                            clipPath(lead) {
                                drawRect(
                                    brush = Brush.horizontalGradient(*Flow, startX = x, endX = x + w),
                                    topLeft = Offset(x, y),
                                    size = Size(w, h),
                                )
                                Blobs.forEachIndexed { index, blob ->
                                    val r = h * blob.radius
                                    val c = Offset(
                                        x + w * (blob.bx + blob.sx * sin(clock * blob.vx * 1.6f + blob.px)),
                                        cy + h * (blob.by + blob.sy * cos(clock * blob.vy * 1.6f + index)),
                                    )
                                    drawCircle(Brush.radialGradient(*blob.stops, center = c, radius = r), r, c)
                                }
                            }
                            drawIntoCanvas { it.restore() }
                        }
                    }

                    val ix = x + ICON_X
                    val star = paths[2]
                    star.sparkle(ix, cy, 10f)
                    drawPath(star, Color.White)
                    // Two small stars twinkle beside the big one while it is generating.
                    star.sparkle(ix - 9.5f, cy + 8.5f, 2.4f)
                    drawPath(star, Color.White, alpha = mix.value * (0.5f + 0.5f * sin(clock * 5.2f)))
                    star.sparkle(ix - 8.5f, cy - 9f, 1.8f)
                    drawPath(star, Color.White, alpha = mix.value * (0.5f + 0.5f * sin(clock * 4.1f + 2.4f)))
                }
            }
        }
        BasicText(
            text = label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.padding(start = 48.dp).graphicsLayer { alpha = 1f - mix.value },
        )
        // The longer word starts a little further left so it stays inside the pill.
        BasicText(
            text = activeLabel,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.padding(start = 44.dp).graphicsLayer { alpha = mix.value },
        )
    }
}
