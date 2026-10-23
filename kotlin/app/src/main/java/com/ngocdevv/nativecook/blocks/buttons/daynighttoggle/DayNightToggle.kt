package com.ngocdevv.nativecook.blocks.buttons.daynighttoggle

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 116f
private const val HEIGHT = 54f
private const val KNOB = 44f
private const val INSET = 5f
private const val TRAVEL = WIDTH - KNOB - INSET * 2

// The canvas overflows the toggle by this much on every side to fit its shadow.
private val BLEED = 24.dp

// Far larger than any tile; the container the toggle sits in is expected to clip it.
private val BACKDROP_BLEED = 1000.dp
private const val AUTOPLAY_MS = 2600L
private const val SLIDE_MS = 420
private const val FADE_MS = 180

private val EaseInOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private class DayNightPalette(
    val tile: Color,
    val track: Color,
    val back: Color,
    val mid: Color,
    val front: Color,
    val shadow: Color,
)

private val Night = DayNightPalette(
    tile = Color(0xFFE0EDF6),
    track = Color(0xFF102A38),
    back = Color(0xFF5D7B8F),
    mid = Color(0xFF3C5C75),
    front = Color(0xFF0D1E2B),
    shadow = rgba(16, 42, 56, 0.4f),
)
private val Day = DayNightPalette(
    tile = Color(0xFFF5F1E2),
    track = Color(0xFFF8E17B),
    back = Color(0xFFF3C552),
    mid = Color(0xFFEFB93D),
    front = Color(0xFFE29C1E),
    shadow = rgba(206, 150, 34, 0.42f),
)
private val KnobColor = Color(0xFFF6F6F6)

// Blends in plain sRGB components, like the React Native and Swift versions.
private fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

private fun polygon(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}

private fun Path.disc(x: Float, y: Float, r: Float) = addOval(Rect(x - r, y - r, x + r, y + r))

/** A ridge line and how far it drifts as the knob crosses. */
private class Ridge(val path: Path, val driftX: Float, val driftY: Float, val night: Color, val day: Color)

/** Everything is in the toggle's own 116 × 54 box. */
private class DayNightScene {
    val track = Path().apply { addRoundRect(RoundRect(0f, 0f, WIDTH, HEIGHT, CornerRadius(HEIGHT / 2))) }
    val stars = Path().apply {
        val points = floatArrayOf(
            54.9f, 4.4f, 73.9f, 5.9f, 88.7f, 8.6f, 61.1f, 13.8f, 77.9f, 15f, 88.7f, 18.3f, 69.4f, 25.1f, 91.2f, 37.8f,
        )
        for (i in points.indices step 2) disc(points[i], points[i + 1], 0.75f)
    }

    // The crescent is a disc with a second, offset disc cut out of it.
    val moonCenter = Offset(90.6f, 18.6f)
    val moonRadius = 12.2f
    val moonCut = Path().apply { disc(87.2f, 15.6f, 11.9f) }

    val sunCenter = Offset(24.3f, 20.7f)
    val sunRadius = 10f
    val sunGlowRadius = 16.5f
    val sunGlow = Brush.radialGradient(
        0f to rgba(255, 255, 255, 0.95f),
        0.55f to rgba(255, 252, 226, 0.9f),
        1f to rgba(255, 246, 190, 0f),
        center = sunCenter,
        radius = sunGlowRadius,
    )

    // Far to near. They run past the bottom edge so they can slide without a gap; the middle one
    // rises into the day's big dune.
    val ridges = listOf(
        Ridge(
            polygon(50f, 72f, 50f, 45f, 60f, 41f, 69f, 34f, 76f, 37.5f, 86f, 41f, 96f, 45f, 106f, 42f, 132f, 46f, 132f, 72f),
            driftX = -2f, driftY = 0f, night = Night.back, day = Day.back,
        ),
        Ridge(
            polygon(18f, 72f, 34f, 45f, 47f, 37f, 51.5f, 34.5f, 60.5f, 41f, 69f, 46f, 79f, 48.5f, 90f, 53f, 100f, 72f),
            driftX = -7f, driftY = -5f, night = Night.mid, day = Day.mid,
        ),
        Ridge(
            polygon(
                -4f, 72f, -4f, 48f, 10f, 45f, 22f, 49.5f, 34f, 52f, 47f, 47.5f, 57f, 52.5f, 70f, 51f, 84f, 54f,
                96f, 48f, 105f, 51.5f, 122f, 46f, 122f, 72f,
            ),
            driftX = -3f, driftY = 0f, night = Night.front, day = Day.front,
        ),
    )

    // The shadow under the track cross-fades between a navy and an amber one.
    val nightShadow = SoftRoundRect(Night.shadow, sigma = 8f)
    val dayShadow = SoftRoundRect(Day.shadow, sigma = 8f)
    val knobShadow = SoftRoundRect(rgba(0, 0, 0, 0.24f), sigma = 3f)
}

/**
 * Switch that slides from a night scene (crescent moon, stars, blue ridges) to a day scene (sun,
 * golden dunes). The knob hides the moon by day and the sun by night.
 *
 * @param value Controlled value; true is day (knob on the right). Leave null to let the toggle keep its own.
 * @param autoPlay Flips by itself every few seconds.
 * @param backdrop Also paints the area around the toggle: pale blue at night, cream by day.
 */
@Composable
fun DayNightToggle(
    value: Boolean? = null,
    onValueChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    backdrop: Boolean = false,
    contentDescription: String = "Day mode",
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    var inner by remember { mutableStateOf(false) }
    val on = value ?: inner
    val controlled by rememberUpdatedState(value != null)
    val onChange by rememberUpdatedState(onValueChange)
    val set = remember {
        { next: Boolean ->
            if (!controlled) inner = next
            onChange(next)
        }
    }

    // The knob slides while the scenery cross-fades a little faster underneath it.
    val knob = remember { Animatable(if (on) 1f else 0f) }
    val scene = remember { Animatable(if (on) 1f else 0f) }

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

    LaunchedEffect(on) {
        val target = if (on) 1f else 0f
        launch { scene.animateTo(target, tween(FADE_MS, easing = EaseInOutQuad)) }
        // Reduced motion keeps the cross-fade and drops the slide.
        if (reducedMotion) knob.snapTo(target) else knob.animateTo(target, tween(SLIDE_MS, easing = EaseOutStrong))
    }

    // Restarts on every change, so a tap never gets flipped straight back.
    LaunchedEffect(autoPlay, on) {
        if (!autoPlay) return@LaunchedEffect
        delay(AUTOPLAY_MS)
        set(!on)
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val art = remember { DayNightScene() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (backdrop) {
            Box(
                Modifier.matchParentSize().bleed(BACKDROP_BLEED).drawBehind {
                    drawRect(mix(Night.tile, Day.tile, scene.value))
                },
            )
        }
        Box(
            modifier = Modifier
                .size(WIDTH.dp, HEIGHT.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .toggleable(
                    value = on,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = { set(it) },
                )
                .semantics { this.contentDescription = contentDescription },
        ) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, _, _ ->
                        val k = knob.value
                        val s = scene.value
                        translate(x, y) {
                            drawIntoCanvas {
                                val r = (HEIGHT - 4) / 2
                                art.nightShadow.draw(it.nativeCanvas, 6f, 9f, WIDTH - 12, HEIGHT - 4, r, alpha = 1f - s)
                                art.dayShadow.draw(it.nativeCanvas, 6f, 9f, WIDTH - 12, HEIGHT - 4, r, alpha = s)
                            }

                            clipPath(art.track) {
                                drawPath(art.track, mix(Night.track, Day.track, s))

                                drawPath(art.stars, Color.White, alpha = 1f - s)
                                clipPath(art.moonCut, ClipOp.Difference) {
                                    drawCircle(KnobColor, art.moonRadius, art.moonCenter, alpha = 1f - s)
                                }

                                // The sun sits where the knob rests at night, so the knob uncovers it as it leaves.
                                scale(0.6f + 0.4f * s, pivot = art.sunCenter) {
                                    drawCircle(art.sunGlow, art.sunGlowRadius, art.sunCenter, alpha = s)
                                    drawCircle(Color(0xFFFDFDF6), art.sunRadius, art.sunCenter, alpha = s)
                                }

                                for (ridge in art.ridges) {
                                    translate(ridge.driftX * k, ridge.driftY * k) {
                                        drawPath(ridge.path, mix(ridge.night, ridge.day, s))
                                    }
                                }

                                val knobX = INSET + KNOB / 2 + TRAVEL * k
                                drawIntoCanvas {
                                    art.knobShadow.draw(
                                        it.nativeCanvas, knobX - KNOB / 2, HEIGHT / 2 + 1.5f - KNOB / 2, KNOB, KNOB, KNOB / 2,
                                    )
                                }
                                drawCircle(KnobColor, KNOB / 2, Offset(knobX, HEIGHT / 2))
                            }
                        }
                    }
                }
            }
        }
    }
}
