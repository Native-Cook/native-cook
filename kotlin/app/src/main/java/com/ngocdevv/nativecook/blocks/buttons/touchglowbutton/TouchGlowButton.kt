package com.ngocdevv.nativecook.blocks.buttons.touchglowbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sin

private val HEIGHT = 64.dp
private val PAD_X = 58.dp
private const val RADIUS = 14f

// The canvas overflows the button by this much on every side to fit the glow underneath.
private val BLEED = 40.dp
private const val GLOW_RADIUS = 84f
private const val RIM_RADIUS = 100f

// Where the wandering light is frozen when the system asks for reduced motion.
private const val STILL_TIME = 1.2f

private val EaseInOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private val LabelStyle = TextStyle(color = Color(0xFFFAFAFF), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

private class TouchGlowPaints {
    val under = SoftRoundRect(rgba(84, 44, 214, 0.34f), sigma = 16f)
    val clip = Path()
    private var w = Float.NaN
    private var h = Float.NaN

    // All three are centred on the origin; the canvas is translated to where the light is.
    val violet = glow(124, 58, 237, 0.62f, 0.26f)
    val blue = glow(48, 86, 220, 0.7f, 0.28f)
    val rim = Brush.radialGradient(
        0f to rgba(206, 194, 255, 1f),
        0.45f to rgba(124, 92, 255, 0.55f),
        1f to rgba(124, 92, 255, 0f),
        center = Offset.Zero,
        radius = RIM_RADIUS,
    )

    private fun glow(r: Int, g: Int, b: Int, peak: Float, mid: Float) = Brush.radialGradient(
        0f to rgba(r, g, b, peak),
        0.45f to rgba(r, g, b, mid),
        1f to rgba(r, g, b, 0f),
        center = Offset.Zero,
        radius = GLOW_RADIUS,
    )

    fun update(x: Float, y: Float, w: Float, h: Float) {
        if (w == this.w && h == this.h) return
        this.w = w
        this.h = h
        clip.reset()
        clip.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(RADIUS)))
    }

    /** The stretch of border nearest the light, which sits at ([px], [py]) in canvas units. */
    fun DrawScope.drawRim(x: Float, y: Float, w: Float, h: Float, px: Float, py: Float, inset: Float, width: Float, alpha: Float) {
        translate(px, py) {
            drawRoundRect(
                brush = rim,
                topLeft = Offset(x + inset - px, y + inset - py),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(RADIUS - inset),
                style = Stroke(width),
                alpha = alpha,
            )
        }
    }
}

/**
 * Dark indigo button whose border and inner glow light up around a point: under the finger while
 * it is held (drag to move the light), or wandering by itself with [autoPlay].
 *
 * @param autoPlay Lets the light wander around the button by itself; a finger still takes it over.
 */
@Composable
fun TouchGlowButton(
    label: String = "Button",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    val time = remember { mutableFloatStateOf(STILL_TIME) }
    // Finger position in the button's own coordinates (dp), and how much it has taken the light over.
    val fingerX = remember { mutableFloatStateOf(0f) }
    val fingerY = remember { mutableFloatStateOf(0f) }
    val touch = remember { Animatable(0f) }

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

    LaunchedEffect(pressed) {
        // Lingers after release so the glow isn't hidden under the thumb the whole time.
        touch.animateTo(if (pressed) 1f else 0f, tween(if (pressed) 180 else 700, easing = EaseInOutQuad))
    }

    val wandering = autoPlay && !reducedMotion
    LaunchedEffect(wandering) {
        if (!wandering) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue += min((now - last) / 1e6f, 64f) / 1000f
            last = now
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { TouchGlowPaints() }
    val rimBloom = rememberBloom(4.dp)

    // Where the light is, in dp from the button's top-left corner.
    // By itself it sweeps side to side while bobbing between the top and bottom edges.
    fun lightX(w: Float): Float {
        if (!autoPlay) return fingerX.floatValue
        val ax = w / 2 + (w / 2 - 6) * sin(0.9f * time.floatValue)
        return ax + (fingerX.floatValue - ax) * touch.value
    }

    fun lightY(h: Float): Float {
        if (!autoPlay) return fingerY.floatValue
        val ay = h / 2 + (h / 2 - 4) * sin(1.7f * time.floatValue + 1f)
        return ay + (fingerY.floatValue - ay) * touch.value
    }

    fun lit() = if (autoPlay) 1f else touch.value

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Only watches where the finger is; the click below still handles the press.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            fingerX.floatValue = change.position.x / density
                            fingerY.floatValue = change.position.y / density
                        }
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    paints.update(x, y, w, h)
                    val lx = lightX(w)
                    val lit = lit()
                    // The glow is violet on the left of the button and turns blue towards the right.
                    val mix = (lx / w).coerceIn(0f, 1f)

                    drawIntoCanvas {
                        paints.under.draw(it.nativeCanvas, x + 8, y + 12, w - 16, h - 4, RADIUS, alpha = 0.6f + 0.4f * lit)
                    }
                    drawRoundRect(Color(0xFF0E0428), Offset(x, y), Size(w, h), CornerRadius(RADIUS))

                    clipPath(paints.clip) {
                        translate(x + lx, y + lightY(h)) {
                            drawCircle(paints.violet, GLOW_RADIUS, Offset.Zero, alpha = lit * (1f - mix))
                            drawCircle(paints.blue, GLOW_RADIUS, Offset.Zero, alpha = lit * mix)
                        }
                    }

                    // Resting border: a little brighter towards the bottom-left and top-right corners.
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to rgba(124, 92, 255, 0.5f),
                            0.5f to rgba(124, 92, 255, 0.13f),
                            1f to rgba(124, 92, 255, 0.42f),
                            start = Offset(x, y + h),
                            end = Offset(x + w, y),
                        ),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(w - 1, h - 1),
                        cornerRadius = CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = rimBloom
                    alpha = if (rimBloom == null) 0f else 0.6f * lit()
                },
            ) {
                surface(BLEED) { x, y, w, h ->
                    with(paints) { drawRim(x, y, w, h, x + lightX(w), y + lightY(h), inset = 0f, width = 4f, alpha = 1f) }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    with(paints) {
                        drawRim(x, y, w, h, x + lightX(w), y + lightY(h), inset = 0.75f, width = 1.5f, alpha = lit())
                    }
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}
