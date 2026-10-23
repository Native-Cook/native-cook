package com.ngocdevv.nativecook.blocks.onboarding.colorshiftcardstack

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class CardGlyph { People, Chart, Star, Play, Download }

/**
 * @param lines Two or three lines; the last word of the last line is written in the card's colour.
 * @param color The colour of that word, of the glyph and of the whole screen while the card is on top.
 */
data class StackCard(val lines: List<String>, val color: Color, val glyph: CardGlyph)

val SampleCards = listOf(
    StackCard(listOf("two", "hundred", "nineteen followers"), Color(0xFF1986EF), CardGlyph.People),
    StackCard(listOf("five thousand", "thirty two MRR"), Color(0xFFEE424A), CardGlyph.Chart),
    StackCard(listOf("one thousand", "fifty three stars"), Color(0xFF1C7132), CardGlyph.Star),
    StackCard(listOf("ten thousand", "eight hundred", "subscribers"), Color(0xFFF5333F), CardGlyph.Play),
    StackCard(listOf("six thousand", "fifty eight", "downloads"), Color(0xFFA7A0A2), CardGlyph.Download),
)

private val CardInk = Color(0xFF1C1C1E)

// A card's face on top of the stack, one place down and two places down.
private val Faces = listOf(Color(0xFFFFFDFF), Color(0xFFF0F0F2), Color(0xFFE4E4E8))

// The dark of the headline is made from the screen's colour: this much of it, plus this much of
// its brightness on every channel. The body is the same dark, thinner.
private const val INK_KEEP = 0.2f
private const val INK_LIGHT = 0.14f
private const val BODY_ALPHA = 0.8f

private const val SIDE = 24f
private const val HEADLINE = 50f
private const val HEADLINE_LINE = 68f

// On a short screen the headline is set smaller.
private const val SHORT = 700f
private const val HEADLINE_SHORT = 44f
private const val HEADLINE_LINE_SHORT = 60f
private const val BODY_GAP = 17f
private const val BODY_LINE = 18f
private const val STACK_GAP = 35f
private const val CARD_SIDE = 42f
private const val CARD_MAX = 340f
private const val CARD_HEIGHT = 142f
private const val CARD_RADIUS = 20f
private const val GLYPH = 16f
private const val BUTTON_GAP = 46f
private const val BUTTON_WIDTH = 120f
private const val BUTTON = 46f
private const val BOTTOM = 26f
private const val PRESS_SCALE = 0.96f
private const val PRESS_MS = 120

// A card leaving to the left goes this far, turns this much and fades over the last third of the way.
private const val LEAVE = 340f
private const val TURN = 8f
private const val FADE_FROM = 2f / 3f

// Each place down the stack: turned, moved right and up, and smaller.
private const val DEPTH_TURN = 2f
private const val DEPTH_X = 6f
private const val DEPTH_Y = -3f
private const val DEPTH_SHRINK = 0.04f

// The top card floats this far up and down, and the white word dims this much, once every WAVE seconds.
private const val FLOAT = 2f
private const val SHIMMER = 0.15f
private const val WAVE = 1.7f

/** autoPlay flicks the top card away every STEP seconds, after REST of rest. */
const val LOOP = 8.5f
private const val STEP = 1.7f
private const val REST = 1.1f

// The flick: the card is gone in FLICK_MS, the colours have changed in SHIFT_MS.
private const val FLICK_MS = 350
private val FlickEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private const val SHIFT_MS = 500

// The springs, for a mass of 1: the stack moves up a place with a small overshoot (damping ratio
// 0.7), and a released card goes where it is sent without a bounce.
private const val RISE_STIFFNESS = 200f
private const val RISE_DAMPING = 19.8f
private const val SNAP_STIFFNESS = 220f
private const val SNAP_DAMPING = 26.5f

// A card dragged this far, or let go this fast (dp a second), leaves; otherwise it comes back.
private const val COMMIT = 90f
private const val FLING = 600f

// After a touch autoPlay waits this long before it goes back to the first card.
private const val USER_HOLD = 3f

// Under reduced motion the cards change at once and the colours in one short step.
private const val JUMP_MS = 250

/** A spring given as the other stacks give it, by stiffness and damping for a mass of 1. */
private fun springOf(stiffness: Float, damping: Float) = spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness)

/** A place on the ring of cards: 0 up to the number of cards. */
private fun lap(value: Float, count: Int) = ((value % count) + count) % count

private fun lap(place: Int, count: Int) = ((place % count) + count) % count

/** How many places card [index] is behind [position]: from -1, just gone, up to the last place in the ring. */
private fun behind(index: Int, position: Float, count: Int) = lap(index - position + 1f, count) - 1f

/** Two colours mixed channel by channel, as the other stacks do it. */
private fun mix(from: Color, to: Color, amount: Float) = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
)

/** The dark made from a screen colour. */
private fun inkOf(color: Color, alpha: Float = 1f): Color {
    val light = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return Color(INK_KEEP * color.red + INK_LIGHT * light, INK_KEEP * color.green + INK_LIGHT * light, INK_KEEP * color.blue + INK_LIGHT * light, alpha)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun stackText(size: Float, lineHeight: Float, weight: FontWeight, color: Color = Color.Unspecified, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val BodyStyle = stackText(13f, BODY_LINE, FontWeight.Normal)
private val CardStyle = stackText(23f, 32f, FontWeight.Bold, CardInk, -0.4f)
private val ButtonStyle = stackText(13f, 18f, FontWeight.SemiBold, CardInk)

// The star's ten corners round (8, 8.4): outer 7.6, inner 3.2.
private val Star = Path().apply {
    for (corner in 0 until 10) {
        val radius = if (corner % 2 == 0) 7.6f else 3.2f
        val angle = corner * PI.toFloat() / 5f - PI.toFloat() / 2f
        val x = 8f + radius * cos(angle)
        val y = 8.4f + radius * sin(angle)
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
private val PlayMark = Path().apply {
    moveTo(6.4f, 5.4f); lineTo(10.8f, 8f); lineTo(6.4f, 10.6f); close()
}
private val DownMark = Path().apply {
    moveTo(8f, 4.2f); lineTo(8f, 11.2f)
    moveTo(5.2f, 8.6f); lineTo(8f, 11.4f); lineTo(10.8f, 8.6f)
}

/** A card's glyph in a 16 × 16 box, in the card's colour with details in the colour of its face. */
private fun DrawScope.glyph(kind: CardGlyph, color: Color) {
    val face = Faces[0]
    fun block(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color) = drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
    when (kind) {
        CardGlyph.People -> {
            drawCircle(color, 2.1f, Offset(11.6f, 5.3f))
            block(9.4f, 9.2f, 6f, 4.8f, 2.4f, color)
            drawCircle(face, 3.3f, Offset(5.6f, 4.5f))
            block(0.2f, 7.8f, 10.8f, 7f, 3.5f, face)
            drawCircle(color, 2.5f, Offset(5.6f, 4.5f))
            block(1f, 8.6f, 9.2f, 5.4f, 2.7f, color)
        }
        CardGlyph.Chart -> {
            block(0.5f, 0.5f, 15f, 15f, 4f, color)
            block(4f, 8.5f, 2f, 4f, 1f, face)
            block(7f, 4.5f, 2f, 8f, 1f, face)
            block(10f, 6.5f, 2f, 6f, 1f, face)
        }
        CardGlyph.Star -> drawPath(Star, color)
        CardGlyph.Play -> {
            block(0.5f, 2.5f, 15f, 11f, 3.2f, color)
            drawPath(PlayMark, face)
        }
        CardGlyph.Download -> {
            drawCircle(color, 7.5f, Offset(8f, 8f))
            drawPath(DownMark, face, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The position, the rise and the shade while a finger holds the card. */
private class Held(val position: Float, val rise: Float, val shade: Float)

/**
 * A typographic welcome on one flat colour, with a small stack of white cards to flick through.
 * Each card that comes to the top gives the whole screen its colour: the background blends to it
 * and the headline's dark, which is made from the background, follows.
 *
 * @param cards The cards, four or more; after the last comes the first again.
 * @param headline The headline's lines.
 * @param highlight The one word of the headline that is white.
 * @param autoPlay Flicks through the cards by itself, one every 1.7 s: [LOOP] seconds for the five it comes with.
 * @param onChange Called when another card comes to the top.
 */
@Composable
fun ColorShiftCardStack(
    cards: List<StackCard> = SampleCards,
    headline: List<String> = listOf("Thanks", "for being", "an early", "adopter"),
    highlight: String = "early",
    body: String = "Phase one, starts with a preconfigured\nfeed integration. We’ll add more over time.",
    buttonLabel: String = "Sign in",
    autoPlay: Boolean = false,
    onChange: ((card: StackCard, index: Int) -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop and the gesture outlive a recomposition: they read what the caller passes through these.
    val still by rememberUpdatedState(rememberReducedMotion())
    val items by rememberUpdatedState(cards)
    val changed by rememberUpdatedState(onChange)

    // Which card is in the hand, and how far it has left: 2.25 is card 2, a quarter gone. These
    // three are read while placing and drawing only.
    val position = remember { Animatable(0f) }
    // The place the stack behind it has moved up to.
    val rise = remember { Animatable(0f) }
    // The place on the ring of colours the screen is at.
    val shade = remember { Animatable(0f) }
    // Where the three are heading: the card on top, or on its way there. It keeps counting round the ring.
    var heading by remember { mutableIntStateOf(0) }
    // The three while a finger holds the card.
    var held by remember { mutableStateOf<Held?>(null) }
    // How far through its wave the float and the shimmer are, 0 to 1. Read while drawing only.
    val wave = remember { mutableFloatStateOf(0f) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun positionNow() = held?.position ?: position.value
    fun riseNow() = held?.rise ?: rise.value
    fun shadeNow() = held?.shade ?: shade.value

    /** The screen's colour: a plain blend of the two nearest cards on the ring. */
    fun screenColor(): Color {
        val place = lap(shadeNow(), items.size)
        val index = floor(place).toInt() % items.size
        return mix(items[index].color, items[(index + 1) % items.size].color, place - floor(place))
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun landed(to: Int, byHand: Boolean) {
        if (to == heading) return
        heading = to
        if (byHand) tick()
        val index = lap(to, items.size)
        changed?.invoke(items[index], index)
    }

    /** autoPlay's flick: the card in the hand leaves, the stack moves up and the colours change. */
    fun flick(to: Int) {
        landed(to, byHand = false)
        val place = to.toFloat()
        scope.launch { if (still) position.snapTo(place) else position.animateTo(place, tween(FLICK_MS, easing = FlickEasing)) }
        scope.launch { if (still) rise.snapTo(place) else rise.animateTo(place, springOf(RISE_STIFFNESS, RISE_DAMPING)) }
        scope.launch { shade.animateTo(place, tween(if (still) JUMP_MS else SHIFT_MS, easing = LinearEasing)) }
    }

    /** Lets the card go where the drag sent it; [speed] is in places a second. */
    fun release(from: Held, to: Int, speed: Float) {
        val place = to.toFloat()
        scope.launch {
            // The three take over from the finger where it left them, and only then is it let go.
            position.snapTo(if (still) place else from.position)
            rise.snapTo(if (still) place else from.rise)
            shade.snapTo(from.shade)
            held = null
            if (!still) {
                // A throw that points away from where the card is sent is dropped.
                launch { position.animateTo(place, springOf(SNAP_STIFFNESS, SNAP_DAMPING), initialVelocity = if ((place - from.position) * speed >= 0f) speed else 0f) }
                launch { rise.animateTo(place, springOf(RISE_STIFFNESS, RISE_DAMPING)) }
            }
            val shift = if (still) JUMP_MS else max(1, (SHIFT_MS * min(1f, abs(place - from.shade))).roundToInt())
            launch { shade.animateTo(place, tween(shift, easing = LinearEasing)) }
        }
        landed(to, byHand = true)
        touched()
    }

    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        val period = (WAVE * 1e9f).toLong()
        while (true) withFrameNanos { wave.floatValue = ((it - start) % period) / period.toFloat() }
    }

    LaunchedEffect(autoPlay, touches, held != null) {
        if (!autoPlay || held != null) return@LaunchedEffect
        var home = 0
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to the first card.
            home = (position.value / items.size).roundToInt() * items.size
            flick(home)
        }
        // One round of the cards after another, timed from one origin so that rounds do not drift.
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            val count = items.size
            for (k in 0 until count) {
                until(k * STEP + REST)
                flick(home + k + 1)
            }
            until(count * STEP)
            origin += (count * STEP * 1e9f).toLong()
            home += count
        }
    }

    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), sigma = 12f) }
    val buttonShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.15f), sigma = 5f) }

    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(screenColor()) }) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val short = maxHeight.value < SHORT
        val cardWidth = min(CARD_MAX, maxWidth.value - CARD_SIDE * 2f)
        val headlineStyle = stackText(if (short) HEADLINE_SHORT else HEADLINE, if (short) HEADLINE_LINE_SHORT else HEADLINE_LINE, FontWeight.ExtraBold, spacing = -1.5f)
        val ink = ColorProducer { inkOf(screenColor()) }

        Column(
            Modifier.matchParentSize().padding(bottom = (max(insets.calculateBottomPadding().value, 12f) + BOTTOM).dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(Modifier.padding(horizontal = SIDE.dp).clearAndSetSemantics {
                contentDescription = headline.joinToString(" ")
                heading()
            }) {
                for (line in headline) {
                    val at = if (highlight.isEmpty()) -1 else line.indexOf(highlight)
                    if (at < 0) {
                        BasicText(line, style = headlineStyle, maxLines = 1, color = ink)
                    } else {
                        Row {
                            BasicText(line.substring(0, at), style = headlineStyle, maxLines = 1, color = ink)
                            BasicText(
                                highlight,
                                style = headlineStyle,
                                maxLines = 1,
                                color = ColorProducer { Color.White.copy(alpha = 1f - SHIMMER * (0.5f - 0.5f * cos(wave.floatValue * TAU))) },
                            )
                            BasicText(line.substring(at + highlight.length), style = headlineStyle, maxLines = 1, color = ink)
                        }
                    }
                }
            }

            BasicText(
                body,
                style = BodyStyle,
                maxLines = 2,
                color = ColorProducer { inkOf(screenColor(), BODY_ALPHA) },
                modifier = Modifier.padding(start = SIDE.dp, end = SIDE.dp, top = BODY_GAP.dp),
            )

            // The cards. The one the position points at is in the hand: it leaves to the left as
            // the position moves on. The others wait behind it, as many places down as they are
            // behind the rise.
            Layout(
                content = {
                    cards.forEachIndexed { index, card ->
                        CardFace(card, shadow) { if (behind(index, positionNow(), cards.size) <= 0f) 0f else behind(index, riseNow(), cards.size) }
                    }
                },
                modifier = Modifier
                    .padding(top = STACK_GAP.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(cardWidth.dp, CARD_HEIGHT.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val leave = LEAVE.dp.toPx()
                            val from = held ?: Held(position.value, rise.value, shade.value)
                            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                            var travelled = 0f
                            var now = from
                            held = from
                            touched()
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    travelled = change.position.x - down.position.x
                                    // To the left the card in the hand leaves; to the right the one before it comes back.
                                    val by = -travelled / leave
                                    fun within(place: Float) = place.coerceIn(heading - 1f, heading + 1f)
                                    now = Held(within(from.position + by), within(from.rise + by), within(from.shade + by))
                                    held = now
                                    if (!change.pressed) break
                                    change.consume()
                                }
                            } finally {
                                val by = -travelled
                                val speed = -tracker.calculateVelocity().x
                                val to = heading + when {
                                    by > COMMIT.dp.toPx() || speed > FLING.dp.toPx() -> 1
                                    by < -COMMIT.dp.toPx() || speed < -FLING.dp.toPx() -> -1
                                    else -> 0
                                }
                                release(now, to, speed / leave)
                            }
                        }
                    }
                    .clearAndSetSemantics {
                        contentDescription = cards[lap(heading, cards.size)].lines.joinToString(" ")
                        customActions = listOf(
                            CustomAccessibilityAction("Next") { touched(); flick(heading + 1); true },
                            CustomAccessibilityAction("Previous") { touched(); flick(heading - 1); true },
                        )
                    },
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { index, placeable ->
                        val inHand = behind(index, positionNow(), cards.size) <= 0f
                        placeable.placeWithLayer(0, 0, zIndex = if (inHand) 100f else 90f - behind(index, riseNow(), cards.size) * 10f) {
                            val float = if (still) 0f else FLOAT.dp.toPx() * sin(wave.floatValue * TAU)
                            val gone = behind(index, positionNow(), cards.size)
                            if (gone <= 0f) {
                                // A leaving card fades over the last third of its way.
                                alpha = 1f - ((-gone - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f)
                                translationX = gone * LEAVE.dp.toPx()
                                translationY = float
                                rotationZ = TURN * gone
                                scaleX = 1f
                                scaleY = 1f
                            } else {
                                val down = behind(index, riseNow(), cards.size)
                                val depth = min(down, 2f)
                                // The card at the back of the ring comes into view as it reaches the third place.
                                alpha = (3f - down).coerceIn(0f, 1f)
                                translationX = DEPTH_X.dp.toPx() * depth
                                translationY = DEPTH_Y.dp.toPx() * depth + float * (1f - down).coerceIn(0f, 1f)
                                rotationZ = DEPTH_TURN * depth
                                scaleX = 1f - DEPTH_SHRINK * depth
                                scaleY = 1f - DEPTH_SHRINK * depth
                            }
                        }
                    }
                }
            }

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS), label = "press")
            Box(
                Modifier
                    .padding(top = BUTTON_GAP.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(BUTTON_WIDTH.dp, BUTTON.dp)
                    .graphicsLayer {
                        scaleX = press
                        scaleY = press
                    }
                    .drawBehind {
                        surface(0.dp) { _, _, w, h ->
                            drawIntoCanvas { buttonShadow.draw(it.nativeCanvas, 0f, 4f, w, h, 10f) }
                            drawRoundRect(Color.White, size = Size(w, h), cornerRadius = CornerRadius(10f))
                        }
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(interaction, indication = null, role = Role.Button) {
                        touched()
                        tick()
                        onSignIn?.invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(buttonLabel, style = ButtonStyle, maxLines = 1)
            }
        }
    }
}

/** A card's face. [down] answers how many places down the stack it is, while drawing. */
@Composable
private fun CardFace(card: StackCard, shadow: SoftRoundRect, down: () -> Float) {
    val last = card.lines.last()
    val cut = last.lastIndexOf(' ') + 1
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val places = down()
                val face = if (places < 1f) mix(Faces[0], Faces[1], max(0f, places)) else mix(Faces[1], Faces[2], min(1f, places - 1f))
                surface(0.dp) { _, _, w, h ->
                    // Only the two cards on top throw a shadow.
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 10f, w, h, CARD_RADIUS, alpha = (2f - places).coerceIn(0f, 1f)) }
                    drawRoundRect(face, size = Size(w, h), cornerRadius = CornerRadius(CARD_RADIUS))
                }
            },
    ) {
        Canvas(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 22.dp).size(GLYPH.dp)) {
            surface(0.dp) { _, _, _, _ -> glyph(card.glyph, card.color) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp)) {
            for (line in card.lines.dropLast(1)) BasicText(line, style = CardStyle, maxLines = 1, modifier = Modifier.height(32.dp))
            BasicText(
                buildAnnotatedString {
                    append(last.substring(0, cut))
                    withStyle(SpanStyle(color = card.color)) { append(last.substring(cut)) }
                },
                style = CardStyle,
                maxLines = 1,
                modifier = Modifier.height(32.dp),
            )
        }
    }
}
