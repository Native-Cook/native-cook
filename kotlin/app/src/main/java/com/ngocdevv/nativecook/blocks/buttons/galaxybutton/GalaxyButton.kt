package com.ngocdevv.nativecook.blocks.buttons.galaxybutton

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEIGHT = 58.dp
private val PAD_X = 46.dp

// The canvas overflows the button by this much on every side to fit the nebula and its sparkles.
private val BLEED = 92.dp

// One trip of the light around the border.
private const val LAP_MS = 7000f

// The light travels just inside the border, where the reference's cursor sits.
private const val LIGHT_INSET = 5f
private const val NEBULA_R = 86f
private const val RIM_REACH = 78f
private const val SPARKLE_GROUPS = 3

// Where the light rests (as a fraction of a lap) when the system asks for reduced motion.
private const val STILL_LAP = 0.3f

private val DustColor = Color(0xFFA9D8F4)
private val LabelStyle = TextStyle(
    color = Color(0xFFDCDADE),
    fontSize = 15.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 1.sp,
)

// Same generator and seed as the React Native version, so the dust lands in the same places.
private class GalaxyRandom(seed: Int) {
    private var a = seed

    fun next(): Float {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
    }
}

// Dust scattered around the light (the origin), split into groups that twinkle out of phase.
private fun galaxySparkles(): List<Path> {
    val rand = GalaxyRandom(23)
    val groups = List(SPARKLE_GROUPS) { Path() }
    for (i in 0 until 33) {
        val angle = rand.next() * TAU
        val distance = 22f + rand.next() * 58f
        val r = 0.4f + rand.next() * rand.next() * 1.2f
        val cx = cos(angle) * distance
        val cy = sin(angle) * distance
        groups[i % SPARKLE_GROUPS].addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }
    return groups
}

/** A point [lap] (0..1, clockwise from the top-left) of the way around a capsule inset from the button. */
private fun capsulePoint(lap: Float, x: Float, y: Float, w: Float, h: Float, inset: Float): Offset {
    val r = h / 2 - inset
    val cy = y + h / 2
    val straight = w - h
    val arc = PI.toFloat() * r
    var d = (lap - floor(lap)) * (2 * straight + 2 * arc)
    if (d < straight) return Offset(x + h / 2 + d, cy - r)
    d -= straight
    if (d < arc) return Offset(x + w - h / 2 + r * sin(d / r), cy - r * cos(d / r))
    d -= arc
    if (d < straight) return Offset(x + w - h / 2 - d, cy + r)
    d -= straight
    return Offset(x + h / 2 - r * sin(d / r), cy + r * cos(d / r))
}

/**
 * "JOIN TODAY!" pill on deep navy: a cyan light travels around the border inside a soft nebula
 * with drifting dust.
 *
 * @param autoPlay Keeps the light circling. When off it appears on press/hover and lingers briefly.
 * @param lingerMs How long the light stays after the finger lifts, so it isn't hidden under the thumb.
 */
@Composable
fun GalaxyButton(
    label: String = "JOIN TODAY!",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    lingerMs: Long = 1600,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(false) }
    val linger by rememberUpdatedState(lingerMs)

    LaunchedEffect(interaction) {
        var lingerJob: Job? = null
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    lingerJob?.cancel()
                    pressed = true
                    lingering = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = false
                    lingerJob = launch {
                        delay(linger)
                        lingering = false
                    }
                }
            }
        }
    }

    val lit = autoPlay || pressed || hovered || lingering

    // 0 = dark, 1 = fully lit.
    val p = remember { Animatable(0f) }
    // Measured in laps around the border, wrapped to 0..1. Every other use multiplies it by a whole
    // number before sin(), so the wrap is seamless.
    val loop = remember { mutableFloatStateOf(STILL_LAP) }

    LaunchedEffect(lit) {
        p.animateTo(if (lit) 1f else 0f, tween(if (lit) 420 else 600, easing = EaseOutStrong))
    }

    // Keep the light moving until the fade-out has finished, then stop asking for frames.
    val visible by remember { derivedStateOf { p.value > 0f } }
    val looping = !reducedMotion && (lit || visible)
    LaunchedEffect(looping) {
        if (!looping) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            loop.floatValue = (loop.floatValue + (now - last) / 1e6f / LAP_MS) % 1f
            last = now
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val sparkles = remember { galaxySparkles() }
    val pill = remember { Path() }
    val bloom = rememberBloom(3.5.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    pill.reset()
                    pill.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
                    drawBody(sparkles, pill, x, y, w, h, p.value, loop.floatValue)
                }
            }
            // Only the stretch of border near the light is lit: a radial gradient centred on it paints the stroke.
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = bloom
                    alpha = if (bloom == null) 0f else p.value
                },
            ) {
                surface(BLEED) { x, y, w, h ->
                    val light = capsulePoint(loop.floatValue, x, y, w, h, LIGHT_INSET)
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            0f to rgba(64, 170, 240, 0.75f),
                            0.45f to rgba(64, 170, 240, 0.3f),
                            1f to rgba(64, 170, 240, 0f),
                            center = light,
                            radius = RIM_REACH,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2),
                        style = Stroke(4f),
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val light = capsulePoint(loop.floatValue, x, y, w, h, LIGHT_INSET)
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            0f to Color(0xFF56B8F6),
                            0.5f to rgba(60, 164, 236, 0.85f),
                            1f to rgba(60, 164, 236, 0f),
                            center = light,
                            radius = RIM_REACH,
                        ),
                        topLeft = Offset(x + 1, y + 1),
                        size = Size(w - 2, h - 2),
                        cornerRadius = CornerRadius(h / 2 - 1),
                        style = Stroke(2f),
                        alpha = p.value,
                    )
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}

private fun DrawScope.drawBody(
    sparkles: List<Path>,
    pill: Path,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    p: Float,
    loop: Float,
) {
    val r = h / 2
    val light = capsulePoint(loop, x, y, w, h, LIGHT_INSET)

    if (p > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to rgba(48, 128, 172, 0.46f),
                0.5f to rgba(30, 86, 132, 0.18f),
                1f to rgba(20, 60, 110, 0f),
                center = light,
                radius = NEBULA_R,
            ),
            radius = NEBULA_R,
            center = light,
            alpha = p,
        )
        // Each group of dust wanders a few points around the light and twinkles at its own pace.
        sparkles.forEachIndexed { index, path ->
            val wave = 0.5f + 0.5f * sin(TAU * (index + 4) * loop + index * 1.9f)
            translate(
                left = light.x + 4f * sin(TAU * (index + 2) * loop + index * 2.1f),
                top = light.y + 4f * cos(TAU * (index + 3) * loop + index * 1.3f),
            ) {
                drawPath(path, DustColor, alpha = p * (0.15f + 0.55f * wave))
            }
        }
    }

    drawRoundRect(Color(0xFF050308), Offset(x, y), Size(w, h), CornerRadius(r))
    if (p > 0f) {
        // The light tints the glass from the inside.
        clipPath(pill) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to rgba(26, 58, 74, 0.9f),
                    0.5f to rgba(22, 48, 64, 0.4f),
                    1f to rgba(20, 44, 60, 0f),
                    center = light,
                    radius = RIM_REACH,
                ),
                radius = RIM_REACH,
                center = light,
                alpha = p,
            )
        }
    }
    drawRoundRect(
        color = rgba(60, 54, 78, 0.8f),
        topLeft = Offset(x + 0.75f, y + 0.75f),
        size = Size(w - 1.5f, h - 1.5f),
        cornerRadius = CornerRadius(r - 0.75f),
        style = Stroke(1.5f),
    )
}
