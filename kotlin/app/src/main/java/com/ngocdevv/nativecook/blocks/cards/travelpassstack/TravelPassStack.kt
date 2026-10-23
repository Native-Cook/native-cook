package com.ngocdevv.nativecook.blocks.cards.travelpassstack

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * @param photo Address of a wide photo, about 2 : 1.
 * @param tint Flat colour shown until the photo arrives.
 * @param glyph Path data of the glyph in a 20 × 20 box: absolute M, L, Q and Z commands separated by spaces.
 */
data class TravelPass(val name: String, val photo: String, val tint: Color, val glyph: String)

private const val PANEL_WIDTH = 350f
private const val PANEL_HEIGHT = 620f
private const val PANEL_RADIUS = 40f

private const val CARD_WIDTH = 310f
private const val CARD_HEIGHT = 184f
private const val CARD_RADIUS = 18f

// Height of the row with the glyph and the name.
private const val HEADER = 48f
private const val GLYPH = 20f
private const val GLYPH_STROKE = 1.6f
private const val GLYPH_LEFT = 22f
private const val TITLE_LEFT = 50f
private const val PHOTO_INSET = 24f
private const val PHOTO_WIDTH = CARD_WIDTH - PHOTO_INSET * 2f
private const val PHOTO_HEIGHT = 116f
private const val PHOTO_RADIUS = 12f
private const val SHADOW_OPACITY = 0.3f
private const val SHADOW_SIGMA = 16f

// Each card's layer overflows the card by this much on every side to fit its shadow.
private const val BLEED = 40f

// Top of the first full-size card, and the distance from one card's top to the next.
private const val TOP = 84f
private const val STEP = 106f

// Cards that fit at full size.
private const val SLOTS = 4

// A card pushed one step past the top: how far it still rises, and what is left of it.
private const val RECEDE_LIFT = 62f
private const val RECEDE_EASE = 12f
private const val RECEDE_SCALE = 0.19f
private const val RECEDE_OPACITY = 0.7f
private const val RECEDE_BLUR = 2.5f

// Steps past the first over which a receding card fades out completely.
private const val RECEDE_FADE = 0.7f

// The photo is taller than its window by this much on each side and slides inside it.
private const val PARALLAX_ROOM = 10f
private const val PARALLAX_STEP = 6f

private const val HAZE_HEIGHT = 64f
private const val HAZE_OPACITY = 0.5f

// The snap after a release: a spring on the scroll position, in cards.
private const val MASS = 1f
private const val STIFFNESS = 180f
private const val DAMPING = 20f

// Seconds of speed a release throws the stack ahead by.
private const val THROW_TIME = 0.15f
private const val RUBBER = 0.35f

// The gaps open by this share of a step per card/second of speed, up to a limit.
private const val STRETCH_GAIN = 0.03f
private const val STRETCH_MAX = 0.1f
private const val STRETCH_RATE = 10f

// autoPlay loop (seconds): a drag up by two cards, a pause, a drag back down, a rest.
private const val AUTO_CARDS = 2f
private const val DRAG_UP_AT = 0.8f
private const val DRAG_DOWN_AT = 3.4f
private const val DRAG_TIME = 0.9f

// Share of the way the finger covers before it lets go; the throw does the rest.
private const val DRAG_SHARE = 0.85f
private const val FLICK_ANGLE = 2.2f
private const val LOOP = 6.2f

// After a touch the loop waits this long, then goes home and rests this long before it restarts.
private const val USER_HOLD = 2f
private const val RESUME_REST = 1.2f

private val SamplePasses = listOf(
    TravelPass(
        name = "Kestrel Heli Transfer",
        photo = "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFF7C7C7C),
        glyph = "M 4 4.5 L 16 4.5 M 10 4.5 L 10 7.5 M 7 7.5 L 12 7.5 Q 16.5 7.5 16.5 11.5 Q 16.5 14 14 14 L 9 14 Q 6 14 6 11 L 6 8.5 Q 6 7.5 7 7.5 Z M 6 10 L 2 9 M 2 7.5 L 2 10.5 M 8 16.5 L 15 16.5",
    ),
    TravelPass(
        name = "First Light Lounge",
        photo = "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFF978477),
        glyph = "M 6 9 L 6 6.5 Q 6 4.5 8 4.5 L 12 4.5 Q 14 4.5 14 6.5 L 14 9 M 4 9 Q 4 8 5 8 Q 6 8 6 9 L 6 11.5 L 14 11.5 L 14 9 Q 14 8 15 8 Q 16 8 16 9 L 16 14 L 4 14 Z M 6 14 L 6 16 M 14 14 L 14 16",
    ),
    TravelPass(
        name = "Northwind Charter",
        photo = "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFFADB3B8),
        glyph = "M 3 10 L 17 10 M 11.5 10 L 7.5 3.5 M 11.5 10 L 7.5 16.5 M 5 10 L 3.2 7 M 5 10 L 3.2 13",
    ),
    TravelPass(
        name = "Hollow Pine Cabins",
        photo = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFF454732),
        glyph = "M 3 16 L 10 4 L 17 16 Z M 8.5 16 L 8.5 12.5 L 11.5 12.5 L 11.5 16",
    ),
    TravelPass(
        name = "Quiet Hours Spa",
        photo = "https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFF80A7A0),
        glyph = "M 10 4 Q 13.5 8.5 10 14 Q 6.5 8.5 10 4 Z M 10 14 Q 4.5 14 3 8.5 Q 6 8.5 7.4 10 M 10 14 Q 15.5 14 17 8.5 Q 14 8.5 12.6 10 M 5 16.5 L 15 16.5",
    ),
    TravelPass(
        name = "Coastliner Rail",
        photo = "https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=640&h=330&q=80",
        tint = Color(0xFF7E726B),
        glyph = "M 5.5 3.5 L 14.5 3.5 Q 16 3.5 16 5 L 16 12.5 Q 16 14 14.5 14 L 5.5 14 Q 4 14 4 12.5 L 4 5 Q 4 3.5 5.5 3.5 Z M 4 8.5 L 16 8.5 M 7 11.2 L 7.1 11.2 M 13 11.2 L 12.9 11.2 M 6.5 14 L 5 17 M 13.5 14 L 15 17",
    ),
)

private val NameStyle = TextStyle(
    fontSize = 15.5.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.1).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private class Placement(val top: Float, val scale: Float, val opacity: Float, val blur: Float, val parallax: Float)

/**
 * Where a card sits for [u] = its index minus the scroll: 0 is the top full-size slot, negative
 * values have been pushed past it and recede.
 */
private fun placement(u: Float, stretch: Float): Placement {
    val r = (-u).coerceIn(0f, 2f)
    val top = TOP + max(u, 0f) * STEP - (RECEDE_LIFT * r - RECEDE_EASE * r * r) + STEP * stretch * (max(u, -2f) - 1f)
    val opacity = if (r <= 1f) 1f - (1f - RECEDE_OPACITY) * r else max(0f, RECEDE_OPACITY * (1f - (r - 1f) / RECEDE_FADE))
    val parallax = ((1.5f - u) * PARALLAX_STEP).coerceIn(-PARALLAX_ROOM, PARALLAX_ROOM)
    return Placement(top, 1f - RECEDE_SCALE * r, opacity, RECEDE_BLUR * r, parallax)
}

/** A finger's stroke, 0…1: it starts from rest, speeds up and is still moving when it lets go. */
private fun flick(p: Float) = (1f - cos(p * FLICK_ANGLE)) / (1f - cos(FLICK_ANGLE))

/** Where the loop's finger holds the stack at [t] seconds, or null while it is lifted. */
private fun scriptFinger(t: Float, travel: Float): Float? = when {
    t >= DRAG_UP_AT && t < DRAG_UP_AT + DRAG_TIME -> travel * DRAG_SHARE * flick((t - DRAG_UP_AT) / DRAG_TIME)
    t >= DRAG_DOWN_AT && t < DRAG_DOWN_AT + DRAG_TIME -> travel * (1f - DRAG_SHARE * flick((t - DRAG_DOWN_AT) / DRAG_TIME))
    else -> null
}

/** The card a release lands on: the nearest one to where the speed would carry the stack. */
private fun snapTarget(scroll: Float, velocity: Float, limit: Float) =
    (scroll + velocity * THROW_TIME).roundToInt().toFloat().coerceIn(0f, limit)

private fun nearest(scroll: Float, limit: Float) = scroll.roundToInt().toFloat().coerceIn(0f, limit).toInt()

/** Past either end the stack follows the finger at a third of its speed. */
private fun rubber(scroll: Float, limit: Float) = when {
    scroll < 0f -> scroll * RUBBER
    scroll > limit -> limit + (scroll - limit) * RUBBER
    else -> scroll
}

/** Per-frame state of the stack: [scroll] counts cards pushed past the top. */
private class StackSim {
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f

    /** A real finger is on the stack. */
    var dragging = false

    /** The loop's own finger is on the stack. */
    var scripted = false

    /** [scroll] at the previous frame, to measure the finger's speed. */
    var seen = 0f
    var velocity = 0f
    var target = 0f
    val scroll = mutableFloatStateOf(0f)

    /** 0…[STRETCH_MAX]: how far the gaps are pulled open by the speed. */
    val stretch = mutableFloatStateOf(0f)
}

/**
 * A wallet of travel passes seen in perspective: wide photo cards overlap down a dusk panel, and
 * dragging scrolls them. Cards pushed past the top shrink, dim and blur as if they moved away,
 * new ones rise from under the bottom edge, and the gaps stretch with the speed of the finger.
 * Releasing snaps the stack to a card.
 *
 * @param passes The passes, first on top. Four fit at full size; the rest wait under the bottom edge.
 * @param autoPlay Drags the stack up by two cards, pauses, drags it back down and starts over.
 * @param skyTop The panel's gradient, top and bottom.
 * @param onSelect Called with the index of the card the stack snaps to when a drag ends.
 * @param onOpen Called with the index of a tapped card.
 */
@Composable
fun TravelPassStack(
    passes: List<TravelPass> = SamplePasses,
    autoPlay: Boolean = false,
    skyTop: Color = Color(0xFF3E4063),
    skyBottom: Color = Color(0xFF7A7594),
    cardColor: Color = Color(0xFF3A3A40),
    textColor: Color = Color.White,
    onSelect: ((Int) -> Unit)? = null,
    onOpen: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    // The furthest the stack scrolls: the last card then sits in the lowest full slot.
    val limit = max(0, passes.size - SLOTS).toFloat()
    val view = LocalView.current
    val sim = remember { StackSim() }
    val shadow = remember { SoftRoundRect(rgba(0, 0, 0, SHADOW_OPACITY), sigma = SHADOW_SIGMA) }

    LaunchedEffect(auto) {
        sim.hold = 0f
        sim.clock = 0f
        sim.scripted = false
        sim.target = 0f
        if (auto) {
            sim.scroll.floatValue = 0f
            sim.velocity = 0f
        }
    }

    LaunchedEffect(reducedMotion, limit) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt <= 0f) continue
            var scroll = sim.scroll.floatValue
            if (sim.dragging) {
                sim.velocity += ((scroll - sim.seen) / dt - sim.velocity) * 0.5f
            } else {
                if (sim.hold > 0f) {
                    sim.hold -= dt
                    // Time is up: the stack goes home and the loop starts over from its rest.
                    if (sim.hold <= 0f && looping) {
                        sim.target = 0f
                        sim.clock = LOOP - RESUME_REST
                    }
                } else if (looping) {
                    var t = sim.clock + dt
                    if (t >= LOOP) {
                        t -= LOOP
                        scroll = 0f
                        sim.velocity = 0f
                        sim.target = 0f
                    }
                    sim.clock = t
                    val finger = scriptFinger(t, min(AUTO_CARDS, limit))
                    if (finger != null) {
                        sim.velocity = (finger - scroll) / dt
                        scroll = finger
                        sim.scripted = true
                    } else if (sim.scripted) {
                        sim.scripted = false
                        sim.target = snapTarget(scroll, sim.velocity, limit)
                    }
                }
                if (!sim.scripted) {
                    if (reducedMotion) {
                        scroll = sim.target
                        sim.velocity = 0f
                    } else {
                        sim.velocity += (STIFFNESS * (sim.target - scroll) - DAMPING * sim.velocity) / MASS * dt
                        scroll += sim.velocity * dt
                    }
                }
                sim.scroll.floatValue = scroll
            }
            sim.seen = scroll
            val pull = if (reducedMotion) 0f else min(abs(sim.velocity) * STRETCH_GAIN, STRETCH_MAX)
            sim.stretch.floatValue += (pull - sim.stretch.floatValue) * (1f - exp(-dt * STRETCH_RATE))
        }
    }

    Box(
        modifier
            .size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)
            .clip(RoundedCornerShape(PANEL_RADIUS.dp))
            .background(Brush.verticalGradient(listOf(skyTop, skyBottom)))
            .pointerInput(limit) {
                var from = 0f
                var dragged = 0f
                var index = 0
                val release = {
                    sim.dragging = false
                    sim.target = snapTarget(sim.scroll.floatValue, sim.velocity, limit)
                    sim.hold = USER_HOLD
                    onSelect?.invoke(sim.target.toInt())
                }
                detectVerticalDragGestures(
                    onDragStart = {
                        from = sim.scroll.floatValue
                        dragged = 0f
                        index = nearest(from, limit)
                        sim.dragging = true
                        sim.scripted = false
                        sim.seen = from
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                ) { change, amountPx ->
                    change.consume()
                    dragged += amountPx / density
                    sim.scroll.floatValue = rubber(from - dragged / STEP, limit)
                    val now = nearest(sim.scroll.floatValue, limit)
                    if (now != index) {
                        index = now
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            },
    ) {
        passes.forEachIndexed { index, pass ->
            PassCard(
                pass = pass,
                index = index,
                sim = sim,
                shadow = shadow,
                cardColor = cardColor,
                textColor = textColor,
                onClick = {
                    sim.hold = USER_HOLD
                    onOpen?.invoke(index)
                },
            )
        }
        // A little dusk haze over the bottom edge, where the next cards wait.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HAZE_HEIGHT.dp)
                .background(Brush.verticalGradient(listOf(skyBottom.copy(alpha = 0f), skyBottom.copy(alpha = HAZE_OPACITY)))),
        )
    }
}

/** One pass: the glyph and the name over a wide photo. */
@Composable
private fun PassCard(
    pass: TravelPass,
    index: Int,
    sim: StackSim,
    shadow: SoftRoundRect,
    cardColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    val glyph = remember(pass.glyph) { PathParser().parsePathString(pass.glyph).toPath() }
    val interaction = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(CARD_RADIUS.dp)

    Box(
        Modifier
            .offset(((PANEL_WIDTH - CARD_WIDTH) / 2f - BLEED).dp, 0.dp)
            .size((CARD_WIDTH + BLEED * 2f).dp, (CARD_HEIGHT + BLEED * 2f).dp)
            .graphicsLayer {
                val place = placement(index - sim.scroll.floatValue, sim.stretch.floatValue)
                // Scales about the middle of the card's top edge.
                transformOrigin = TransformOrigin(0.5f, BLEED / (CARD_HEIGHT + BLEED * 2f))
                translationY = (place.top - BLEED).dp.toPx()
                scaleX = place.scale
                scaleY = place.scale
                alpha = place.opacity
                val sigma = place.blur.dp.toPx()
                renderEffect = if (sigma > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BlurEffect(sigmaToRadius(sigma), sigmaToRadius(sigma), TileMode.Decal)
                } else {
                    null
                }
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, BLEED, BLEED, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS) }
                }
            },
    ) {
        Box(
            Modifier
                .offset(BLEED.dp, BLEED.dp)
                .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                .background(cardColor, cardShape)
                .border(1.dp, rgba(255, 255, 255, 0.08f), cardShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = pass.name },
        ) {
            Canvas(Modifier.offset(GLYPH_LEFT.dp, ((HEADER - GLYPH) / 2f).dp).size(GLYPH.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(glyph, textColor, style = Stroke(GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            BasicText(
                text = pass.name,
                style = NameStyle.copy(color = textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(TITLE_LEFT.dp, ((HEADER - 20f) / 2f).dp)
                    .width((CARD_WIDTH - TITLE_LEFT - PHOTO_INSET).dp),
            )
            Box(
                Modifier
                    .offset(PHOTO_INSET.dp, HEADER.dp)
                    .size(PHOTO_WIDTH.dp, PHOTO_HEIGHT.dp)
                    .clip(RoundedCornerShape(PHOTO_RADIUS.dp))
                    .background(pass.tint),
            ) {
                // Taller than its window, so it has room to slide; the tint shows until it arrives.
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(pass.photo).crossfade(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight((PHOTO_HEIGHT + PARALLAX_ROOM * 2f).dp)
                        .graphicsLayer {
                            translationY = placement(index - sim.scroll.floatValue, sim.stretch.floatValue).parallax.dp.toPx()
                        },
                )
            }
        }
    }
}
