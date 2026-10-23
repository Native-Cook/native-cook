package com.ngocdevv.nativecook.blocks.buttons.generatebutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sweepStroke
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEIGHT = 52.dp
private val PAD_LEFT = 20.dp
private val PAD_RIGHT = 22.dp
private val ICON_SLOT = 24.dp
private val ICON_GAP = 10.dp
private const val RING = 4f

// The canvas overflows the button by this much on every side to fit the aura and the star field.
private val AURA = 120.dp
private const val LOOP_MS = 6000f
private const val STAR_GROUPS = 4

private val IdleLabel = Color(0xFF6B6B6B)
private val StarColor = Color(0xFFC4A8FF)
private val SmallStarLit = Color(0xFFCDB6FF)

private val LabelStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)

private fun Path.sparkle(cx: Float, cy: Float, r: Float, k: Float = 0.24f) {
    val c = r * k
    moveTo(cx, cy - r)
    quadraticTo(cx + c, cy - c, cx + r, cy)
    quadraticTo(cx + c, cy + c, cx, cy + r)
    quadraticTo(cx - c, cy + c, cx - r, cy)
    quadraticTo(cx - c, cy - c, cx, cy - r)
    close()
}

private fun Path.dot(cx: Float, cy: Float, r: Float) {
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}

// Same generator and seed as the React Native version, so the stars land in the same places.
private class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Float {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
    }
}

// Scatter sparkles around (never on top of) the button, split into groups that twinkle out of phase.
private fun starField(w: Float, h: Float, aura: Float): List<Path> {
    val rand = Mulberry32(7)
    val groups = List(STAR_GROUPS) { Path() }
    val cw = w + aura * 2
    val ch = h + aura * 2
    var placed = 0
    while (placed < 34) {
        val x = rand.next() * cw
        val y = rand.next() * ch
        val insideButton = x > aura - 14 && x < aura + w + 14 && y > aura - 14 && y < aura + h + 14
        // Keep stars inside the aura ellipse so they fade with it instead of hitting the canvas edge.
        val nx = (x - cw / 2) / (cw / 2)
        val ny = (y - ch / 2) / (ch / 2)
        if (insideButton || nx * nx + ny * ny > 0.82f) continue
        val size = rand.next()
        val group = groups[placed % STAR_GROUPS]
        if (size > 0.55f) group.sparkle(x, y, 1.4f + size * 2.4f) else group.dot(x, y, 0.5f + size * 1.2f)
        placed++
    }
    return groups
}

/** Everything that only depends on the button's size, rebuilt when the size changes. */
private class GenerateGeometry {
    private var w = Float.NaN
    private var h = Float.NaN

    var stars: List<Path> = emptyList()
        private set
    val pill = Path()
    val mainStar = Path()
    val lowStar = Path()
    val highStar = Path()
    var iconX = 0f
        private set

    val glow = SoftRoundRect(rgba(120, 74, 255, 0.85f), sigma = 18f)
    val shineBloom = sweepStroke(
        0f to rgba(255, 255, 255, 0f),
        0.62f to rgba(255, 255, 255, 0f),
        0.75f to rgba(255, 255, 255, 0.8f),
        0.88f to rgba(255, 255, 255, 0f),
        1f to rgba(255, 255, 255, 0f),
    )
    val shine = sweepStroke(
        0f to rgba(255, 255, 255, 0f),
        0.64f to rgba(255, 255, 255, 0f),
        0.75f to Color.White,
        0.86f to rgba(255, 255, 255, 0f),
        1f to rgba(255, 255, 255, 0f),
    )

    fun update(x: Float, y: Float, w: Float, h: Float, aura: Float, iconInset: Float) {
        if (w == this.w && h == this.h) return
        this.w = w
        this.h = h
        val cy = y + h / 2
        stars = starField(w, h, aura)
        pill.reset()
        pill.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
        iconX = x + iconInset
        mainStar.reset()
        mainStar.sparkle(iconX, cy, 7.4f)
        lowStar.reset()
        lowStar.sparkle(iconX - 8.4f, cy + 7.4f, 3.8f)
        highStar.reset()
        highStar.sparkle(iconX - 8.2f, cy - 8f, 1.9f)
    }
}

/**
 * "Generate Site" button: a dark pill that lights up into a glowing purple capsule with a
 * twinkling star field, a pulsing sparkle icon and a highlight running around its rim.
 *
 * @param active Forces the lit state. When null the button lights up on press/hover and lingers briefly.
 * @param lingerMs How long the glow stays after the finger lifts, so it isn't hidden under the thumb.
 */
@Composable
fun GenerateButton(
    label: String = "Generate Site",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean? = null,
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

    val lit = active ?: (pressed || hovered || lingering)

    // 0 = idle, 1 = fully lit.
    val p = remember { Animatable(0f) }
    // Phase of every looping detail, wrapped to 0..1. Each use multiplies it by a whole number
    // before sin(), so the wrap is seamless.
    val loop = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lit) {
        p.animateTo(if (lit) 1f else 0f, tween(if (lit) 420 else 520, easing = EaseOutStrong))
    }

    // Keep looping until the fade-out has finished, then stop asking for frames.
    val visible by remember { derivedStateOf { p.value > 0f } }
    val looping = !reducedMotion && (lit || visible)
    LaunchedEffect(looping) {
        if (!looping) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            loop.floatValue = (loop.floatValue + (now - last) / 1e6f / LOOP_MS) % 1f
            last = now
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val geometry = remember { GenerateGeometry() }
    val shineBloom = rememberBloom(3.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(AURA)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(AURA) { x, y, w, h ->
                    geometry.update(x, y, w, h, AURA.value, (PAD_LEFT + ICON_SLOT / 2).value + 3f)
                    drawBody(geometry, x, y, w, h, p.value, loop.floatValue)
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = shineBloom
                    alpha = if (shineBloom == null) 0f else p.value
                },
            ) {
                surface(AURA) { x, y, w, h ->
                    drawIntoCanvas {
                        // One lap of the rim highlight every 3s.
                        geometry.shineBloom.draw(it.nativeCanvas, x, y, w, h, h / 2, 3f, turns = 2f * loop.floatValue)
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(AURA) { x, y, w, h ->
                    geometry.update(x, y, w, h, AURA.value, (PAD_LEFT + ICON_SLOT / 2).value + 3f)
                    drawIntoCanvas {
                        geometry.shine.draw(
                            it.nativeCanvas, x + 0.5f, y + 0.5f, w - 1f, h - 1f, h / 2 - 0.5f, 1.2f,
                            turns = 2f * loop.floatValue,
                            alpha = p.value,
                        )
                    }
                    drawIcon(geometry, y + h / 2, p.value, loop.floatValue)
                }
            }
        }
        Row(
            modifier = Modifier.height(HEIGHT).padding(start = PAD_LEFT, end = PAD_RIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(ICON_SLOT + ICON_GAP))
            BasicText(
                text = label,
                style = LabelStyle,
                maxLines = 1,
                color = ColorProducer { lerp(IdleLabel, Color.White, p.value) },
            )
        }
    }
}

private fun DrawScope.drawBody(g: GenerateGeometry, x: Float, y: Float, w: Float, h: Float, p: Float, loop: Float) {
    val aura = x
    val cw = w + aura * 2
    val ch = h + aura * 2
    val cx = x + w / 2
    val cy = y + h / 2
    val r = h / 2
    val center = Offset(cx, cy)

    if (p > 0f) {
        scale(cw / 2, ch / 2, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to rgba(104, 58, 240, 0.62f),
                    0.45f to rgba(72, 36, 190, 0.3f),
                    1f to rgba(44, 20, 128, 0f),
                    center = center,
                    radius = 1f,
                ),
                radius = 1f,
                center = center,
                alpha = p,
            )
        }
        drawIntoCanvas { g.glow.draw(it.nativeCanvas, x - 2, y - 2, w + 4, h + 4, r + 2, alpha = p) }

        g.stars.forEachIndexed { index, path ->
            val wave = 0.5f + 0.5f * sin(TAU * ((index % 2) + 2) * loop + index * 1.7f)
            drawPath(path, StarColor, alpha = p * (0.18f + 0.72f * wave))
        }

        drawRoundRect(
            color = rgba(112, 74, 246, 0.42f),
            topLeft = Offset(x - RING, y - RING),
            size = Size(w + RING * 2, h + RING * 2),
            cornerRadius = CornerRadius(r + RING),
            alpha = p,
        )
    }

    drawRoundRect(Color(0xFF1C1C1C), Offset(x, y), Size(w, h), CornerRadius(r))

    if (p > 0f) {
        clipPath(g.pill) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF5A2FF0), Color(0xFF6A37F7)), startY = y, endY = y + h),
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(r),
                alpha = p,
            )
            val glowCenter = Offset(cx, y + h + 4)
            scale(w * 0.46f, h * 0.62f, pivot = glowCenter) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to rgba(196, 138, 255, 0.95f),
                        0.5f to rgba(160, 110, 255, 0.4f),
                        1f to rgba(130, 90, 255, 0f),
                        center = glowCenter,
                        radius = 1f,
                    ),
                    radius = 1f,
                    center = glowCenter,
                    alpha = p,
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(rgba(214, 196, 255, 0.5f), rgba(214, 196, 255, 0.06f), rgba(214, 196, 255, 0.22f)),
                    startY = y,
                    endY = y + h,
                ),
                topLeft = Offset(x + 0.5f, y + 0.5f),
                size = Size(w - 1, h - 1),
                cornerRadius = CornerRadius(r - 0.5f),
                style = Stroke(1f),
                alpha = p,
            )
        }
    }
}

private fun DrawScope.drawIcon(g: GenerateGeometry, cy: Float, p: Float, loop: Float) {
    val ix = g.iconX
    val beat = TAU * 4 * loop
    scale(1f - 0.14f * p * (0.5f + 0.5f * sin(beat)), pivot = Offset(ix, cy)) {
        drawPath(g.mainStar, Color(0xFF5E5E5E))
        drawPath(g.mainStar, Color.White, alpha = p)
    }
    scale(1f + 0.3f * p * sin(beat + 2.2f), pivot = Offset(ix - 8.4f, cy + 7.4f)) {
        drawPath(g.lowStar, Color(0xFF484848))
        drawPath(g.lowStar, SmallStarLit, alpha = p * 0.75f)
    }
    scale(1f + 0.55f * p * sin(beat + 4.4f), pivot = Offset(ix - 8.2f, cy - 8f)) {
        drawPath(g.highStar, Color(0xFF3C3C3C))
        drawPath(g.highStar, SmallStarLit, alpha = p * 0.75f)
    }
}
