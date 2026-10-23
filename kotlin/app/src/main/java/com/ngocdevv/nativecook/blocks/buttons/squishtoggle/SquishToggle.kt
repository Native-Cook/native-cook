package com.ngocdevv.nativecook.blocks.buttons.squishtoggle

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val WIDTH = 90f
private const val HEIGHT = 48f
private const val THUMB = 42f
private const val PAD = (HEIGHT - THUMB) / 2
private const val TRAVEL = WIDTH - PAD * 2 - THUMB

// The canvas overflows the switch by this much on every side to fit its soft shadow.
private val BLEED = 24.dp
private const val TRAVEL_MS = 420
private const val AUTO_PAUSE_MS = 1500

// Mid-travel the thumb is this much wider (and a little flatter), like a soft pad being dragged.
private const val STRETCH = 0.2f
private const val FLATTEN = 0.07f

// While a finger is down the thumb spreads this far towards the other side.
private const val PRESS_SPREAD = 6f

// The thumb's top face: a slightly smaller disc pushed up, leaving a bevel along the bottom.
private const val FACE_INSET = 2.2f
private const val FACE_LIFT = 1.5f

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private val TrackOff = Color(0xFFD3D7E1)
private val TrackOn = Color(0xFF82B56E)

private class SquishTogglePaints {
    val well = SoftRoundRect(rgba(148, 154, 180, 0.3f), sigma = 7f)
    val lip = SoftRoundRect(rgba(255, 255, 255, 0.9f), sigma = 0.8f)
    val thumbShadow = SoftRoundRect(rgba(28, 36, 48, 0.34f), sigma = 3.2f)
    val track = Path()

    // Inner shadow: a blurred ring pushed down-right so only its top-left band falls inside.
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(2.6f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun drawRing(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || alpha <= 0f) return
        ring.setARGB(
            (color.alpha * alpha * 255f).toInt().coerceIn(0, 255),
            (color.red * 255f).toInt(),
            (color.green * 255f).toInt(),
            (color.blue * 255f).toInt(),
        )
        val left = x - 3f + 1.2f
        val top = y - 3f + 2.4f
        canvas.drawRoundRect(left, top, left + w + 6f, top + h + 6f, h / 2 + 3f, h / 2 + 3f, ring)
    }
}

/**
 * Soft, inset switch: grey when off, green when on, with a bevelled thumb that stretches as it travels.
 *
 * @param value Controlled value. Leave null to let the switch keep its own state.
 * @param label Accessibility label; the switch has no visible text.
 * @param autoPlay Flip back and forth by itself with a pause between flips. Tapping still works.
 */
@Composable
fun SquishToggle(
    modifier: Modifier = Modifier,
    value: Boolean? = null,
    onValueChange: (Boolean) -> Unit = {},
    label: String = "Toggle",
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var inner by remember { mutableStateOf(false) }
    val on = value ?: inner
    val controlled by rememberUpdatedState(value != null)
    val notify by rememberUpdatedState(onValueChange)

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

    // Re-armed after every flip, so a tap simply restarts the pause.
    LaunchedEffect(autoPlay, reducedMotion, pressed, on) {
        if (!autoPlay || reducedMotion || pressed) return@LaunchedEffect
        delay((TRAVEL_MS + AUTO_PAUSE_MS).toLong())
        if (!controlled) inner = !on
        notify(!on)
    }

    // 0 = off, 1 = on.
    val p by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 160 else TRAVEL_MS, easing = EaseInOut),
        label = "travel",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 1f else 0f,
        animationSpec = tween(if (pressed) 130 else 260, easing = EaseOutStrong),
        label = "press",
    )
    // 0 freezes the stretch for reduced motion.
    val squish = if (reducedMotion) 0f else 1f

    val paints = remember { SquishTogglePaints() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics { contentDescription = label }
            .toggleable(
                value = on,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    if (!controlled) inner = it
                    notify(it)
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize().bleed(BLEED)) {
            surface(BLEED) { x, y, w, h -> drawToggle(paints, x, y, w, h, p, press, squish) }
        }
    }
}

private fun DrawScope.drawToggle(
    paints: SquishTogglePaints,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    p: Float,
    press: Float,
    squish: Float,
) {
    val r = h / 2
    val cy = y + h / 2

    // The switch sits in a shallow well: a soft shadow below, a light lip on the lower edge.
    drawIntoCanvas {
        paints.well.draw(it.nativeCanvas, x, y + 5f, w, h, r)
        paints.lip.draw(it.nativeCanvas, x + 0.5f, y + 1.2f, w, h, r)
    }

    drawRoundRect(TrackOff, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(TrackOn, Offset(x, y), Size(w, h), CornerRadius(r), alpha = p)

    // Thumb box: travels with `p`, stretches around the middle of the trip and spreads away from
    // the wall it is resting against while pressed.
    val bell = sin(PI.toFloat() * p)
    val tw = THUMB * (1f + squish * STRETCH * bell) + PRESS_SPREAD * press
    val th = THUMB * (1f - squish * FLATTEN * bell)
    val tx = x + PAD + THUMB / 2 + TRAVEL * p + PRESS_SPREAD * press * (0.5f - p) - tw / 2
    val ty = cy - th / 2

    paints.track.reset()
    paints.track.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r)))
    clipPath(paints.track) {
        drawIntoCanvas {
            paints.drawRing(it.nativeCanvas, x, y, w, h, rgba(74, 78, 108, 0.5f), 1f)
            paints.drawRing(it.nativeCanvas, x, y, w, h, rgba(44, 86, 40, 0.6f), p)
            paints.thumbShadow.draw(it.nativeCanvas, tx, ty + 3f, tw, th, th / 2)
        }
    }

    val top = y + PAD
    val bottom = y + h - PAD
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFFF7F7F6),
            0.6f to Color(0xFFE6E6E6),
            1f to Color(0xFFCFCFD1),
            startY = top,
            endY = bottom,
        ),
        topLeft = Offset(tx, ty),
        size = Size(tw, th),
        cornerRadius = CornerRadius(th / 2),
    )
    val fh = th - FACE_INSET * 2
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFF8F8F7), Color(0xFFECECEB)), startY = top, endY = bottom),
        topLeft = Offset(tx + FACE_INSET, ty + FACE_INSET - FACE_LIFT),
        size = Size(tw - FACE_INSET * 2, fh),
        cornerRadius = CornerRadius(fh / 2),
    )
}
