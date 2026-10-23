package com.ngocdevv.nativecook.blocks.onboarding.themeflavorcarousel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param deep The darkest tone: a soft spot at the top of the card.
 * @param mid The card's base, and the tint of the button.
 * @param light The pale tone that comes up from the bottom left corner.
 * @param ink The button's label and glyph on this flavor.
 */
data class Flavor(val name: String, val deep: Color, val mid: Color, val light: Color, val ink: Color)

val SampleFlavors = listOf(
    Flavor("Mint", Color(0xFF0B9077), Color(0xFF669990), Color(0xFFE7F6F5), Color(0xFF0A4F43)),
    Flavor("Clay", Color(0xFFA17D6F), Color(0xFFC0A087), Color(0xFFFCF6F8), Color(0xFF3A2F2B)),
    Flavor("Graphite", Color(0xFF121013), Color(0xFF716F73), Color(0xFFF7F5F9), Color(0xFF2B2B2E)),
    Flavor("Azure", Color(0xFF009EFB), Color(0xFF0C6ED9), Color(0xFFECF6FA), Color(0xFF0B4F8F)),
    Flavor("Cobalt", Color(0xFF1259BA), Color(0xFF2362B6), Color(0xFFEBEFFA), Color(0xFF123C7C)),
    Flavor("Lagoon", Color(0xFF0DA1AF), Color(0xFF3093C9), Color(0xFFECF3F4), Color(0xFF0B5560)),
)

private val Background = Color(0xFFFDFDFD)
private val Title = Color(0xFF1C1C1E)
private val Subtitle = Color(0xFF3C3C43)

// The texts once the screen has taken a dark flavor.
private val OnDark = Color(0xFFFFFFFF)
private val OnDarkSubtitle = Color(0xFFE9EEF2)

// From the top of the safe area: the title, the two lines under it, then the cards.
private const val TOP = 12f
private const val TEXT_LINE = 24f
private const val TEXT_GAP = 8f
private const val TEXT_SIDE = 40f
private const val HEADER = TOP + TEXT_LINE + TEXT_GAP + TEXT_LINE * 2f + 21f
private const val SIDE = 50f
private const val CARD_MAX = 340f

// A card is this much taller than wide when the screen has the room.
private const val CARD_RATIO = 503f / 292f
private const val RADIUS = 34f
private const val GAP = 20f

// A card next to the middle one is smaller and sits lower.
private const val SHRINK = 0.04f
private const val DROP = 8f
private const val SHADOW_Y = 12f
private const val SHADOW_SIGMA = 15f
private const val SHADOW = 0.18f
private const val BUTTON = 52f
private const val BUTTON_SIDE = 51f
private const val BUTTON_GAP = 70f
private const val BOTTOM = 22f
private const val BORDER = 1.5f
private const val BORDER_ALPHA = 0.7f

// How much of the flavor the bottom of the button takes; its top stays white.
private const val WASH = 0.3f
private const val GLOW_Y = 6f
private const val GLOW_SIGMA = 7f
private const val GLOW = 0.35f
private const val GLYPH = 20f
private const val PRESS_SCALE = 0.96f
private const val PRESS_MS = 120

// The mesh of a card, in fractions of its width and height: a deep spot at the top, a pale one
// rising from the bottom left corner, both soft discs on the mid tone.
private const val DEEP_X = 0.5f
private const val DEEP_Y = 0.12f
private const val DEEP_RX = 1.3f
private const val DEEP_RY = 0.56f
private const val LIGHT_X = 0.04f
private const val LIGHT_Y = 1.02f
private const val LIGHT_RX = 1.35f
private const val LIGHT_RY = 0.84f

// How a disc fades from its centre to its edge.
private val Falloff = floatArrayOf(1f, 0.9f, 0.5f, 0.1f, 0f)
private val FalloffAt = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

// The deep spot goes round a circle of DRIFT dp, one turn in DRIFT_TURN seconds, while the pale
// one grows and shrinks by BREATH; each card is PHASE radians ahead of the one before.
private const val DRIFT = 20f
private const val DRIFT_TURN = 4.8f
private const val BREATH = 0.06f
private const val PHASE = 0.9f

/** autoPlay goes round the six flavors once; a flavor rests, then the carousel travels to the next. */
const val LOOP = 9.6f
private const val STEP = 1.6f
private const val REST = 0.8f
private const val TRAVEL = 0.8

// autoPlay swipes like a finger: it pulls the cards PULL_BY of a card in PULL seconds, speeding
// up, and lets go at that speed.
private const val PULL = 0.26
private const val PULL_BY = 0.4

// What a released carousel settles with (damping ratio 0.8).
private const val MASS = 1.0
private const val STIFFNESS = 260.0
private const val DAMPING = 25.8

// A flick carries the carousel this many seconds of travel ahead, and at most this many cards.
private const val FLICK = 0.15
private const val FLICK_MAX = 0.6

// The fastest a release may throw the cards, in cards a second.
private const val THROW_MAX = 8.0

// After a touch autoPlay waits this long before it goes back to the first flavor.
private const val USER_HOLD = 3f

// The screen takes a flavor: the card in the middle grows over it.
private const val APPLY = 0.6f
private val ApplyEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// Under reduced motion the carousel moves in one short step.
private const val JUMP = 0.22f

// A check in a 20 × 20 box.
private val Check = Path().apply {
    moveTo(6.3f, 10.3f); lineTo(8.9f, 12.8f); lineTo(13.8f, 7.5f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun flavorText(size: Float, lineHeight: Float, weight: FontWeight) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = flavorText(20f, TEXT_LINE, FontWeight.Medium)
private val SubtitleStyle = flavorText(16f, TEXT_LINE, FontWeight.Normal)
private val ButtonStyle = flavorText(16f, 20f, FontWeight.Medium)

/** A place on the ring of cards: 0 up to the number of cards. */
private fun lap(value: Double, count: Int) = ((value % count) + count) % count

private fun lap(card: Int, count: Int) = ((card % count) + count) % count

/** How far card [index] is from the middle, in cards, going round the short way. */
private fun away(index: Int, position: Double, count: Int) = lap(index - position + count / 2.0, count) - count / 2.0

/** A released spring [t] seconds on: how far it still is from its target, having started [from] away at [velocity]. */
private fun springAway(t: Double, from: Double, velocity: Double): Double {
    val omega = sqrt(STIFFNESS / MASS)
    val zeta = DAMPING / (2.0 * sqrt(STIFFNESS * MASS))
    val omega1 = omega * sqrt(1.0 - zeta * zeta)
    return exp(-zeta * omega * t) * (from * cos(omega1 * t) + (velocity + zeta * omega * from) / omega1 * sin(omega1 * t))
}

/** autoPlay's swipe [t] seconds in, as a part of one card: the pull of a finger, then the spring of a release. */
private fun swipeAt(t: Double): Double {
    if (t < PULL) return PULL_BY * (t / PULL) * (t / PULL)
    return 1.0 + springAway(t - PULL, PULL_BY - 1.0, 2.0 * PULL_BY / PULL)
}

/** Two colours mixed channel by channel, as the other stacks do it. */
private fun mix(from: Color, to: Color, amount: Float) = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
)

private enum class TravelKind {
    /** autoPlay: the pull of a finger, then the spring of a release. */
    Swipe,

    /** A release, with the speed the finger had. */
    Spring,

    /** Reduced motion: one short step. */
    Jump,
}

/**
 * One move of the carousel, in cards: where it was, where it lands, and how it gets there.
 *
 * @param velocity Cards a second at the start of a spring.
 * @param start When it set off, on the clock of `System.nanoTime()`.
 */
private class Travel(val from: Double, val to: Double, val velocity: Double, val start: Long, val kind: TravelKind) {
    fun positionAt(now: Long): Double {
        val t = max(0.0, (now - start) / 1e9)
        return when (kind) {
            TravelKind.Swipe -> if (t >= TRAVEL) to else from + (to - from) * swipeAt(t)
            TravelKind.Spring -> to + springAway(t, from - to, velocity)
            TravelKind.Jump -> {
                val u = min(t / JUMP, 1.0)
                from + (to - from) * if (u < 0.5) 2.0 * u * u else 1.0 - 2.0 * (1.0 - u) * (1.0 - u)
            }
        }
    }
}

/** The chosen flavor coming over the screen, or leaving it. */
private class Dress(val from: Float, val to: Float, val start: Long, val duration: Float) {
    fun at(now: Long): Float {
        val u = ((now - start) / 1e9f / duration).coerceIn(0f, 1f)
        return from + (to - from) * ApplyEasing.transform(u)
    }
}

/** A rounded rectangle with a soft edge in any colour, for the shadows and the glow. Drawn in the current (dp) units. */
private class SoftFill(sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(canvas: android.graphics.Canvas, color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        paint.color = color.toArgb()
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
}

/** A soft disc of one colour, fading from its centre to its edge, in a box of radius 1. */
private fun disc(color: Color) = Brush.radialGradient(
    colorStops = Array(Falloff.size) { FalloffAt[it] to color.copy(alpha = Falloff[it]) },
    center = Offset.Zero,
    radius = 1f,
)

private class MeshBrushes(flavor: Flavor) {
    val deep = disc(flavor.deep)
    val light = disc(flavor.light)
}

/**
 * A flavor's mesh, drawn from the corner of a card of the given size: a deep spot at the top and a
 * pale one rising from the bottom left corner, both soft discs on the mid tone.
 *
 * @param turn How far round the deep spot is, 0 to 1.
 */
private fun DrawScope.mesh(flavor: Flavor, brushes: MeshBrushes, index: Int, turn: Float, w: Float, h: Float) {
    val angle = turn * TAU + index * PHASE
    drawRect(flavor.mid, size = Size(w, h))
    withTransform({
        translate(DEEP_X * w + DRIFT * cos(angle), DEEP_Y * h + DRIFT * sin(angle))
        scale(DEEP_RX * w, DEEP_RY * h, pivot = Offset.Zero)
    }) {
        drawCircle(brushes.deep, radius = 1f, center = Offset.Zero)
    }
    val breath = 1f + BREATH * sin(angle)
    withTransform({
        translate(LIGHT_X * w, LIGHT_Y * h)
        scale(LIGHT_RX * w * breath, LIGHT_RY * h * breath, pivot = Offset.Zero)
    }) {
        drawCircle(brushes.light, radius = 1f, center = Offset.Zero)
    }
}

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * A personalisation step: a carousel of tall mesh-gradient cards, one per colour "flavor", that
 * goes round without an end, and a glassy button that takes the colours of the card in the middle
 * as it passes. The button gives the whole screen that flavor: the card grows over it.
 *
 * @param flavors The cards, in order; the last is followed by the first. At least four.
 * @param autoPlay Swipes through the flavors by itself, one every 1.6 s: [LOOP] seconds for the six it comes with.
 * @param onChange Called when another flavor comes to the middle.
 * @param onSelect Called when the button gives the screen the flavor in the middle.
 */
@Composable
fun ThemeFlavorCarousel(
    flavors: List<Flavor> = SampleFlavors,
    title: String = "Select or Create a Flavor",
    subtitle: String = "Flavors theme backgrounds, buttons, and more",
    buttonLabel: String = "Select Flavor",
    autoPlay: Boolean = false,
    onChange: ((flavor: Flavor, index: Int) -> Unit)? = null,
    onSelect: ((flavor: Flavor, index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The loop and the gesture outlive a recomposition: they read what the caller passes through these.
    val still by rememberUpdatedState(rememberReducedMotion())
    val items by rememberUpdatedState(flavors)
    val changed by rememberUpdatedState(onChange)
    val picked by rememberUpdatedState(onSelect)

    // How the carousel last set off; where it is now follows from the time since.
    var travel by remember { mutableStateOf(Travel(0.0, 0.0, 0.0, Long.MIN_VALUE / 2, TravelKind.Spring)) }
    // Where the carousel is, in cards. It keeps counting past the last flavor, which the first
    // follows. Read while drawing only.
    val position = remember { mutableDoubleStateOf(0.0) }
    // A finger holds the carousel.
    var held by remember { mutableStateOf(false) }
    // The card the carousel is on or heading to; like the position, it keeps counting.
    var centre by remember { mutableIntStateOf(0) }
    // The flavor the screen took, and whether it still has it: the colours stay while it leaves.
    var chosen by remember { mutableIntStateOf(0) }
    var chosenOn by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf(Dress(0f, 0f, 0L, APPLY)) }
    // 0 to 1 as the chosen flavor covers the screen. Read while drawing only.
    val covered = remember { mutableFloatStateOf(0f) }
    // How far round the meshes' deep spots are, 0 to 1. Read while drawing only.
    val turn = remember { mutableFloatStateOf(0f) }
    val appeared = remember { System.nanoTime() }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Under reduced motion the frame clock only runs while something is changing.
    var stirs by remember { mutableIntStateOf(0) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun landed(to: Int, byHand: Boolean) {
        if (to == centre) return
        centre = to
        if (byHand) tick()
        val index = lap(to, items.size)
        changed?.invoke(items[index], index)
    }

    /** Lets the carousel go: it settles on a card with the spring, or in one short step. */
    fun settle(from: Double, to: Int, velocity: Double) {
        // A throw that points away from the card it lands on is dropped.
        val towards = (to - from) * velocity >= 0.0
        travel = Travel(from, to.toDouble(), if (towards) velocity else 0.0, System.nanoTime(), if (still) TravelKind.Jump else TravelKind.Spring)
        stirs++
    }

    /**
     * autoPlay's swipe to the next card. It is timed from the moment it was [due], not from the
     * moment the loop woke up for it, so that every round moves alike to the frame.
     */
    fun swipe(to: Int, due: Long) {
        travel = Travel(travel.positionAt(due), to.toDouble(), 0.0, due, if (still) TravelKind.Jump else TravelKind.Swipe)
        landed(to, byHand = false)
        stirs++
    }

    /** One card on or back, for the accessibility actions. */
    fun nudge(by: Int) {
        if (chosenOn) return
        touched()
        val to = centre + by
        settle(travel.positionAt(System.nanoTime()), to, 0.0)
        landed(to, byHand = true)
    }

    fun dress(on: Boolean) {
        val now = System.nanoTime()
        chosenOn = on
        cover = Dress(cover.at(now), if (on) 1f else 0f, now, if (still) JUMP else APPLY)
        stirs++
    }

    fun select() {
        touched()
        tick()
        if (chosenOn) {
            dress(false)
            return
        }
        chosen = lap(centre, items.size)
        dress(true)
        picked?.invoke(items[chosen], chosen)
    }

    /** Back to the first flavor on a plain screen; answers the card the carousel is going to. */
    fun rest(): Int {
        dress(false)
        val from = travel.positionAt(System.nanoTime())
        val home = (from / items.size).roundToInt() * items.size
        settle(from, home, 0.0)
        landed(home, byHand = false)
        return home
    }

    // One clock for everything that is drawn: the meshes, the carousel and the flavor over the screen.
    LaunchedEffect(still, if (still) stirs else 0) {
        val until = System.nanoTime() + (APPLY * 1e9f).toLong()
        while (true) {
            val now = withFrameNanos { it }
            if (!still) turn.floatValue = ((now - appeared) % (DRIFT_TURN * 1e9f).toLong()) / (DRIFT_TURN * 1e9f)
            if (!held) position.doubleValue = travel.positionAt(now)
            covered.floatValue = cover.at(now)
            if (still && now > until) break
        }
    }

    LaunchedEffect(autoPlay, touches, held) {
        if (!autoPlay || held) return@LaunchedEffect
        var home = 0
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            home = rest()
        }
        // One round of the flavors after another, timed from one origin so that rounds do not drift.
        var origin = System.nanoTime()
        fun at(seconds: Float) = origin + (seconds * 1e9f).toLong()
        suspend fun until(seconds: Float) = delay(max(0L, (at(seconds) - System.nanoTime()) / 1_000_000))
        while (true) {
            val count = items.size
            for (k in 0 until count) {
                until(k * STEP + REST)
                swipe(home + k + 1, due = at(k * STEP + REST))
            }
            until(count * STEP)
            origin += (count * STEP * 1e9f).toLong()
            home += count
        }
    }

    // The gallery's activity has light icons on both bars; this screen is pale until it takes a dark flavor.
    val dark = chosenOn && flavors[chosen].deep.luminance() < 0.6f
    DisposableEffect(view, dark) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }

    /** A colour of the flavors at the carousel's place on their ring, between the two nearest cards. */
    fun tone(pick: (Flavor) -> Color): Color {
        val place = lap(position.doubleValue, items.size)
        val index = floor(place).toInt() % items.size
        return mix(pick(items[index]), pick(items[(index + 1) % items.size]), (place - floor(place)).toFloat())
    }

    val brushes = remember(flavors) { flavors.map(::MeshBrushes) }
    val shadow = remember { SoftFill(SHADOW_SIGMA) }
    val glow = remember { SoftFill(GLOW_SIGMA) }
    val cardPath = remember { Path() }
    val coverPath = remember { Path() }
    val sheen = remember { Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), startY = 0f, endY = BUTTON) }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = max(insets.calculateBottomPadding().value, 12f) + BOTTOM
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        val cardWidth = min(CARD_MAX, screenWidth - SIDE * 2f)
        val room = screenHeight - top - HEADER - BUTTON_GAP - BUTTON - bottom
        val cardHeight = max(0f, min(room, cardWidth * CARD_RATIO))
        val cardX = (screenWidth - cardWidth) / 2f
        val cardY = top + HEADER + (room - cardHeight) / 2f
        // The gesture reads the distance from one card to the next when a finger comes down.
        val step by rememberUpdatedState(cardWidth + GAP)

        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, w, _ ->
                cardPath.rewind()
                cardPath.addRoundRect(RoundRect(0f, 0f, cardWidth, cardHeight, CornerRadius(RADIUS)))
                val at = position.doubleValue
                flavors.forEachIndexed { index, flavor ->
                    val by = away(index, at, flavors.size).toFloat()
                    // A card that is off the screen is not drawn.
                    if (abs(by) * (cardWidth + GAP) - cardWidth >= w / 2f) return@forEachIndexed
                    val aside = min(1f, abs(by))
                    withTransform({
                        translate(cardX + by * (cardWidth + GAP), cardY + DROP * aside)
                        scale(1f - SHRINK * aside, 1f - SHRINK * aside, pivot = Offset(cardWidth / 2f, cardHeight / 2f))
                    }) {
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, flavor.mid.copy(alpha = SHADOW), 0f, SHADOW_Y, cardWidth, cardHeight, RADIUS) }
                        clipPath(cardPath) { mesh(flavor, brushes[index], index, turn.floatValue, cardWidth, cardHeight) }
                    }
                }
            }
        }

        // The chosen flavor's mesh growing to the whole screen from where its card is now, which may
        // still be on its way to the middle. Under reduced motion it does not grow: it fades in.
        Canvas(Modifier.matchParentSize().graphicsLayer { alpha = if (still) covered.floatValue else 1f }) {
            val grown = covered.floatValue
            if (grown <= 0f) return@Canvas
            surface(0.dp) { _, _, w, h ->
                val t = if (still) 1f else grown
                val by = away(chosen, position.doubleValue, flavors.size).toFloat()
                val aside = min(1f, abs(by))
                val scale = 1f - SHRINK * aside
                val fromX = cardX + by * (cardWidth + GAP) + cardWidth * (1f - scale) / 2f
                val fromY = cardY + DROP * aside + cardHeight * (1f - scale) / 2f
                val fromWidth = cardWidth * scale
                val fromHeight = cardHeight * scale
                val box = Rect(Offset(fromX * (1f - t), fromY * (1f - t)), Size(fromWidth + (w - fromWidth) * t, fromHeight + (h - fromHeight) * t))
                coverPath.rewind()
                coverPath.addRoundRect(RoundRect(box, CornerRadius(RADIUS * scale * (1f - t))))
                clipPath(coverPath) {
                    withTransform({
                        translate(box.left, box.top)
                        scale(box.width / cardWidth, box.height / cardHeight, pivot = Offset.Zero)
                    }) {
                        mesh(flavors[chosen], brushes[chosen], chosen, turn.floatValue, cardWidth, cardHeight)
                    }
                }
            }
        }

        Column(Modifier.matchParentSize().padding(top = (top + TOP).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // On a dark flavor the texts turn white as it covers the screen.
            val onDark = flavors[chosen].deep.luminance() < 0.6f
            BasicText(
                title,
                style = TitleStyle,
                maxLines = 1,
                color = ColorProducer { mix(Title, if (onDark) OnDark else Title, covered.floatValue) },
                modifier = Modifier.height(TEXT_LINE.dp),
            )
            BasicText(
                subtitle,
                style = SubtitleStyle,
                maxLines = 2,
                color = ColorProducer { mix(Subtitle, if (onDark) OnDarkSubtitle else Subtitle, covered.floatValue) },
                modifier = Modifier.padding(top = TEXT_GAP.dp, start = TEXT_SIDE.dp, end = TEXT_SIDE.dp).height((TEXT_LINE * 2f).dp),
            )

            // The area a finger swipes in: the cards and a little under them.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (chosenOn) return@awaitEachGesture
                            val stepPx = step.dp.toPx()
                            val grabbed = position.doubleValue
                            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                            held = true
                            touched()
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    position.doubleValue = grabbed - (change.position.x - down.position.x) / stepPx
                                    if (!change.pressed) break
                                    change.consume()
                                }
                            } finally {
                                val from = position.doubleValue
                                val velocity = (-tracker.calculateVelocity().x / stepPx).toDouble().coerceIn(-THROW_MAX, THROW_MAX)
                                val to = (from + (velocity * FLICK).coerceIn(-FLICK_MAX, FLICK_MAX)).roundToInt()
                                held = false
                                settle(from, to, velocity)
                                landed(to, byHand = true)
                                touched()
                            }
                        }
                    }
                    .semantics {
                        contentDescription = "Flavor"
                        stateDescription = flavors[lap(centre, flavors.size)].name
                        customActions = listOf(
                            CustomAccessibilityAction("Next") { nudge(1); true },
                            CustomAccessibilityAction("Previous") { nudge(-1); true },
                        )
                    },
            )

            Spacer(Modifier.height((BUTTON_GAP / 2f).dp))

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS), label = "press")
            Row(
                Modifier
                    .padding(horizontal = BUTTON_SIDE.dp)
                    .fillMaxWidth()
                    .height(BUTTON.dp)
                    .graphicsLayer {
                        scaleX = press
                        scaleY = press
                    }
                    .drawBehind {
                        // The button's colours follow the carousel: they are read off the ring of flavors at its position.
                        val tint = tone { it.mid }
                        surface(0.dp) { _, _, w, h ->
                            drawIntoCanvas { glow.draw(it.nativeCanvas, tint.copy(alpha = GLOW), 0f, GLOW_Y, w, h, h / 2f) }
                            drawRoundRect(mix(Color.White, tint, WASH), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                            drawRoundRect(sheen, size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                            drawRoundRect(
                                tint.copy(alpha = BORDER_ALPHA),
                                topLeft = Offset(BORDER / 2f, BORDER / 2f),
                                size = Size(w - BORDER, h - BORDER),
                                cornerRadius = CornerRadius((h - BORDER) / 2f),
                                style = Stroke(BORDER),
                            )
                        }
                    }
                    .clip(CircleShape)
                    .clickable(interaction, indication = null, role = Role.Button) { select() }
                    .semantics { selected = chosenOn },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A check in a ring; the ring fills once the screen has the flavor.
                Canvas(Modifier.size(GLYPH.dp)) {
                    val ink = tone { it.ink }
                    val on = covered.floatValue
                    surface(0.dp) { _, _, _, _ ->
                        val middle = Offset(GLYPH / 2f, GLYPH / 2f)
                        drawCircle(ink, 8.25f, middle, alpha = on)
                        drawCircle(ink, 8.25f, middle, style = Stroke(1.5f))
                        drawPath(Check, ink, alpha = 1f - on, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(Check, Color.White, alpha = on, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Spacer(Modifier.width(8.dp))
                BasicText(buttonLabel, style = ButtonStyle, maxLines = 1, color = ColorProducer { tone { it.ink } })
            }

            Spacer(Modifier.height(bottom.dp))
        }
    }
}
