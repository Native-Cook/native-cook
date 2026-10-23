package com.ngocdevv.nativecook.blocks.buttons.likeburstbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val SIZE = 64f
private const val R = SIZE / 2

// The canvas overflows the button by this much on every side to fit the shadow and the burst.
private val BLEED = 28.dp
private const val UNLIKE_MS = 260
private const val AUTO_LIKED_MS = 2200L
private const val AUTO_UNLIKED_MS = 1300L

// The stroked heart is drawn a little smaller so its outer edge matches the filled one.
private const val OUTLINE_SCALE = 0.925f

// Liking is one clock in seconds; every part below is a window on it.
private val T_OUTLINE_SHRINK = 0f to 0.09f
private val T_OUTLINE_OUT = 0.05f to 0.09f
private val T_SQUASH = 0f to 0.3f
private val T_FILL_IN = 0.06f to 0.1f
private val T_DISC = 0.07f to 0.3f
private val T_DISC_SOLID = 0.1f to 0.22f
private val T_HOLE = 0.22f to 0.42f
private val T_POP = 0.22f to 0.55f
private val T_TINT_OUT = 0.22f to 0.3f
private val T_DOTS_FLY = 0.36f to 0.62f
private val T_DOTS_DRIFT = 0.62f to 0.95f
private val T_DOTS_IN = 0.36f to 0.42f
private val T_DOTS_OUT = 0.58f to 0.92f
private const val TOTAL = 1f

private val Red = Color(0xFFF0301F)
private val Tint = Color(0xFFFF8A7D)

// The burst: seven dots around the rim, clockwise from the top, in degrees.
private val Dots = listOf(
    -90f to Color(0xFFF2D94E),
    -35f to Color(0xFFC8375A),
    0f to Color(0xFF4466E8),
    45f to Color(0xFFC63BE0),
    135f to Color(0xFF5A3DE0),
    180f to Color(0xFF9BCF63),
    -145f to Color(0xFFD9763A),
)

private fun seg(t: Float, window: Pair<Float, Float>) =
    ((t - window.first) / (window.second - window.first)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private fun easeOutBack(x: Float): Float {
    val c = 2.2f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

private class LikeMotion(liked: Boolean) {
    // Resting unliked is burst 0 / fade 0; resting liked is burst TOTAL / fade 0. Unliking only
    // runs `fade` to 1, which looks exactly like burst 0 / fade 0, so the next like can rewind
    // both unseen.
    var burst by mutableFloatStateOf(if (liked) TOTAL else 0f)
    val fade = Animatable(0f)
    var shown = liked

    /** Counts likes; each one starts a fresh run of the clock. */
    var bursts by mutableIntStateOf(0)

    val shadow = SoftRoundRect(rgba(206, 120, 150, 0.42f), sigma = 9f)

    /** A heart about 27 wide, centred on the origin. */
    val heart = Path().apply {
        moveTo(0f, 11.5f)
        cubicTo(-1.7f, 10.1f, -13.3f, 2.2f, -13.3f, -5f)
        cubicTo(-13.3f, -9.7f, -9.8f, -12.6f, -6.2f, -12.6f)
        cubicTo(-3.6f, -12.6f, -1.3f, -11.2f, 0f, -9.1f)
        cubicTo(1.3f, -11.2f, 3.6f, -12.6f, 6.2f, -12.6f)
        cubicTo(9.8f, -12.6f, 13.3f, -9.7f, 13.3f, -5f)
        cubicTo(13.3f, 2.2f, 1.7f, 10.1f, 0f, 11.5f)
        close()
    }

    suspend fun show(liked: Boolean, still: Boolean) {
        if (shown == liked) return
        shown = liked
        when {
            // No burst with animations off: the heart simply cross-fades.
            still -> {
                burst = TOTAL
                fade.animateTo(if (liked) 0f else 1f, tween(160, easing = LinearEasing))
            }
            liked -> {
                fade.snapTo(0f)
                burst = 0f
                bursts++
            }
            else -> fade.animateTo(1f, tween(UNLIKE_MS, easing = LinearEasing))
        }
    }

    /** Runs the like clock once. Kept apart from [show] so unliking mid-burst lets it finish. */
    suspend fun runBurst() {
        val start = withFrameNanos { it }
        while (burst < TOTAL) {
            burst = ((withFrameNanos { it } - start) / 1e9f).coerceAtMost(TOTAL)
        }
    }
}

/**
 * Round white like button. Liking squashes it, swells a red disc behind the heart that opens into
 * a ring, pops the filled heart out of it and throws off a ring of coloured dots; unliking is a
 * quick dip back to the outline.
 *
 * @param value Controlled state. Leave `null` to let the button keep its own.
 * @param label Accessibility label; the button has no visible text.
 * @param autoPlay Likes and unlikes by itself with a pause in between.
 */
@Composable
fun LikeBurstButton(
    value: Boolean? = null,
    onValueChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    label: String = "Like",
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var inner by remember { mutableStateOf(false) }
    val liked = value ?: inner
    val controlled by rememberUpdatedState(value != null)
    val notify by rememberUpdatedState(onValueChange)
    val motion = remember { LikeMotion(liked) }

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

    LaunchedEffect(liked, reducedMotion) {
        motion.show(liked, reducedMotion)
    }
    LaunchedEffect(motion.bursts) {
        if (motion.bursts > 0) motion.runBurst()
    }

    // Re-armed after every change, so a tap simply restarts the pause.
    LaunchedEffect(autoPlay, reducedMotion, pressed, liked) {
        if (!autoPlay || reducedMotion || pressed) return@LaunchedEffect
        delay(if (liked) AUTO_LIKED_MS else AUTO_UNLIKED_MS)
        if (!controlled) inner = !liked
        notify(!liked)
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .size(SIZE.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = label }
            .toggleable(
                value = liked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    if (!controlled) inner = it
                    notify(it)
                },
            ),
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = motion.burst
                    val fade = motion.fade.value
                    val c = Offset(x + R, y + R)

                    // `fade` undoes the like: the filled heart dips and gives way to the outline again.
                    val emptied = seg(fade, 0.2f to 0.6f)
                    val dip = if (reducedMotion) 1f else 1f - 0.22f * sin(Math.PI.toFloat() * fade)

                    drawIntoCanvas {
                        motion.shadow.draw(it.nativeCanvas, c.x - (R - 4f), c.y + 9f - (R - 4f), (R - 4f) * 2, (R - 4f) * 2, R - 4f)
                    }
                    drawCircle(Color.White, R * (1f - 0.06f * sin(Math.PI.toFloat() * seg(t, T_SQUASH))), c)

                    // A red disc swells behind the heart, then hollows out from the centre into a thinning ring.
                    if (t > T_DISC.first && t < T_HOLE.second) {
                        val outer = R * (0.3f + 0.48f * easeOut(seg(t, T_DISC)))
                        val inner = outer * easeOut(seg(t, T_HOLE))
                        drawCircle(
                            Red,
                            radius = (outer + inner) / 2,
                            center = c,
                            alpha = 0.55f + 0.45f * seg(t, T_DISC_SOLID),
                            style = Stroke(outer - inner),
                        )
                    }

                    val shrink = seg(t, T_OUTLINE_SHRINK)
                    val outlineAlpha = max(1f - seg(t, T_OUTLINE_OUT), emptied)
                    if (outlineAlpha > 0f) {
                        val s = OUTLINE_SCALE * max(1f - shrink * shrink, 0.6f + 0.4f * emptied) * dip
                        withTransform({
                            translate(c.x, c.y)
                            scale(s, s, pivot = Offset.Zero)
                        }) {
                            drawPath(motion.heart, Red, alpha = outlineAlpha, style = Stroke(2f, join = StrokeJoin.Round))
                        }
                    }
                    val filledAlpha = seg(t, T_FILL_IN) * (1f - emptied)
                    if (filledAlpha > 0f) {
                        val s = (0.42f + 0.58f * easeOutBack(seg(t, T_POP))) * dip
                        withTransform({
                            translate(c.x, c.y)
                            scale(s, s, pivot = Offset.Zero)
                        }) {
                            drawPath(motion.heart, Red, alpha = filledAlpha)
                            // On the solid disc the heart reads as a lighter red.
                            drawPath(motion.heart, Tint, alpha = seg(t, T_DISC_SOLID) * (1f - seg(t, T_TINT_OUT)))
                        }
                    }

                    val out = seg(t, T_DOTS_OUT)
                    val dotRadius = 2.7f * easeOut(seg(t, T_DOTS_IN)) * (1f - out * out)
                    if (dotRadius > 0f) {
                        val reach = R * (0.9f + 0.42f * easeOut(seg(t, T_DOTS_FLY)) + 0.1f * seg(t, T_DOTS_DRIFT))
                        for ((angle, color) in Dots) {
                            val a = angle * Math.PI.toFloat() / 180f
                            drawCircle(color, dotRadius, Offset(c.x + cos(a) * reach, c.y + sin(a) * reach))
                        }
                    }
                }
            }
        }
    }
}
