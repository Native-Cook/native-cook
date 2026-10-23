package com.ngocdevv.nativecook.blocks.onboarding.mergingstepdots

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class StepIcon { Sparkles, Bell, Seal }

/**
 * @param body Two lines, split with a line break.
 * @param tint The icon's colour.
 * @param tile The soft colour of the tile behind the icon.
 */
data class Step(val title: String, val body: String, val icon: StepIcon, val tint: Color, val tile: Color)

val SampleSteps = listOf(
    Step("Set it up once", "Three quick steps and\neverything is ready for you.", StepIcon.Sparkles, Color(0xFF0B6CF9), Color(0xFFE8F1FF)),
    Step("Stay in the loop", "Only the nudges you ask for,\nnever the noise.", StepIcon.Bell, Color(0xFF1CBD51), Color(0xFFE7F8EE)),
    Step("You're all set", "Tap finish and\nstart exploring.", StepIcon.Seal, Color(0xFFF59E0B), Color(0xFFFFF4E5)),
)

private val Background = Color(0xFFFDFDFD)
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF6B7280)
private val Green = Color(0xFF1CBD51)

// The pill for a moment when the flow is finished.
private val GreenBright = Color(0xFF3EC76B)
private val DotGrey = Color(0xFFD9D9D9)
private val Blue = Color(0xFF0B6CF9)
private val BluePressed = Color(0xFF0957C8)
private val Grey = Color(0xFFF2F2F2)
private val GreyPressed = Color(0xFFE2E2E2)

// The pager: dots SPACING apart, and one green pill that reaches from the first to the current one.
private const val PILL = 24f
private const val DOT_SIZE = 8f
private const val SPACING = 32f

// Room round the pill for the spring's overshoot.
private const val PAGER_PAD = 8f

// From the pager's middle down to the buttons.
private const val PAGER_GAP = 49f
private const val BUTTON = 56f
private const val SIDE = 26f
private const val BOTTOM = 22f
private const val BACK_WIDTH = 90f
private const val BUTTON_GAP = 8f

// "Back" comes out from under the main button at this size.
private const val BACK_SCALE = 0.6f
private const val CHECK_DISC = 18f

// A label leaves upwards and its successor arrives from below, both through a blur.
private const val LABEL_SLIDE = 6f
private const val LABEL_BLUR = 6f
private const val TILE = 168f
private const val TILE_RADIUS = 44f
private const val ICON = 72f

// A page next to the current one sits this far to its side, and its icon is this much smaller
// (after it) or larger (before it).
private const val SLIDE = 40f
private const val ZOOM = 0.28f

// The whole pager hops when the flow is finished.
private const val HOP = 10f

// The icons, in a 72 × 72 box. Sparkles: three four-point stars, each x, y and radius; PINCH is how
// far the sides of a star are pulled in.
private val Stars = listOf(Triple(30f, 41f, 25f), Triple(55f, 18f, 11f), Triple(57f, 55f, 8f))
private const val PINCH = 0.16f

private fun star(cx: Float, cy: Float, r: Float) = Path().apply {
    val k = r * PINCH
    moveTo(cx, cy - r)
    quadraticTo(cx + k, cy - k, cx + r, cy)
    quadraticTo(cx + k, cy + k, cx, cy + r)
    quadraticTo(cx - k, cy + k, cx - r, cy)
    quadraticTo(cx - k, cy - k, cx, cy - r)
    close()
}

private val Sparkles = Stars.map { (cx, cy, r) -> star(cx, cy, r) }

// The bell and, under it, its clapper.
private val Bell = PathParser().parsePathString(
    "M36 10.5C25.2 10.5 18 18.6 18 28.8L18 39.6C18 42.9 16.5 45.6 13.8 48.3C12.3 49.8 13.2 52.5 15.6 52.5L56.4 52.5" +
        "C58.8 52.5 59.7 49.8 58.2 48.3C55.5 45.6 54 42.9 54 39.6L54 28.8C54 18.6 46.8 10.5 36 10.5Z" +
        "M29.4 57C30.3 60 33 61.8 36 61.8C39 61.8 41.7 60 42.6 57Z",
).toPath()

// The bell's badge: a dot with a gap of the tile's colour round it.
private val Badge = Offset(51f, 19f)
private const val BADGE_DOT = 8.5f
private const val BADGE_GAP = 12.5f

// The seal: a disc with ten bumps on its edge, and a check cut out of it.
private const val SEAL_CORE = 24f
private const val SEAL_BUMPS = 10
private const val SEAL_BUMP = 8.6f
private const val SEAL_BUMP_AT = 21.5f
private val SealCheck = Path().apply {
    moveTo(25f, 37.5f); lineTo(32.5f, 45f); lineTo(47.5f, 28.5f)
}

// A check in an 18 × 18 box.
private val Check = Path().apply {
    moveTo(5.2f, 9.3f); lineTo(7.9f, 12f); lineTo(12.9f, 6.5f)
}

/** autoPlay: "Continue" at FIRST and every BETWEEN after it, HOLD on the last step, then back to the first. */
const val LOOP = 8f
private const val FIRST = 1.6f
private const val BETWEEN = 2f
private const val HOLD = 2.4f

// What is left of the loop once it is back on the first step.
private const val AFTER = 2f

// A button the loop presses looks pressed this long before it acts.
private const val PRESS_FOR = 0.14f

// After a touch autoPlay waits this long before it goes back to the first step.
private const val USER_HOLD = 3f

// The springs, for a mass of 1. The pill and the pages overshoot by about 3 dp of the 32 between
// two dots; the main button's left edge and "Back" keep the pace with less bounce over the longer
// way; the check's disc pops to 1.15 before it settles.
private const val DOTS_STIFFNESS = 170f
private const val DOTS_DAMPING = 16f
private const val BAR_STIFFNESS = 170f
private const val BAR_DAMPING = 19.5f
private const val POP_STIFFNESS = 300f
private const val POP_DAMPING = 18f
private const val POP_OUT_MS = 150

// The labels change over SWAP_MS: the old one is gone by SWAP_OUT of it, the new one starts at SWAP_IN.
private const val SWAP_MS = 300
private const val SWAP_OUT = 0.5f
private const val SWAP_IN = 1f / 3f
private const val PARTY_MS = 500

// Under reduced motion everything changes in one short step.
private const val JUMP_MS = 200
private const val SWIPE = 40f
private const val SWIPE_SPEED = 500f

/** Slow, fast, slow: what the other stacks ease a plain step with. */
private val InOutQuad = Easing { u -> if (u < 0.5f) 2f * u * u else 1f - 2f * (1f - u) * (1f - u) }

/** A spring given as the other stacks give it, by stiffness and damping for a mass of 1. */
private fun springOf(stiffness: Float, damping: Float) = spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun stepText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = stepText(28f, 34f, FontWeight.Bold, Ink)
private val BodyStyle = stepText(16f, 24f, FontWeight.Normal, Mute)
private val BackStyle = stepText(17f, 22f, FontWeight.SemiBold, Ink)
private val NextStyle = stepText(17f, 22f, FontWeight.SemiBold, Color.White)

/** Two colours mixed channel by channel, as the other stacks do it. */
private fun mix(from: Color, to: Color, amount: Float) = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
)

private enum class Pressed { Next, Back }

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** An icon in its 72 dp box. */
private fun DrawScope.icon(kind: StepIcon, tint: Color, tile: Color) {
    val middle = Offset(ICON / 2f, ICON / 2f)
    when (kind) {
        StepIcon.Sparkles -> Sparkles.forEach { drawPath(it, tint) }
        StepIcon.Bell -> {
            drawPath(Bell, tint)
            drawCircle(tile, BADGE_GAP, Badge)
            drawCircle(tint, BADGE_DOT, Badge)
        }
        StepIcon.Seal -> {
            drawCircle(tint, SEAL_CORE, middle)
            for (bump in 0 until SEAL_BUMPS) {
                val angle = bump.toFloat() / SEAL_BUMPS * TAU
                drawCircle(tint, SEAL_BUMP, Offset(middle.x + SEAL_BUMP_AT * cos(angle), middle.y + SEAL_BUMP_AT * sin(angle)))
            }
            drawPath(SealCheck, tile, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * The controls of an onboarding flow, with a page per step above them. The pager is one green pill
 * that stretches from the first dot to the current one, swallowing the dots on its way; "Back"
 * comes out from under the main button, which gives it room, and on the last step the button's
 * label changes to "Finish" behind a check that pops in.
 *
 * @param steps The pages, two or more; the last one turns the main button into "Finish".
 * @param autoPlay Goes through the steps and back by itself every [LOOP] seconds.
 * @param onStep Called with the step the flow moved to.
 * @param onDone Called when "Finish" is pressed.
 */
@Composable
fun MergingStepDots(
    steps: List<Step> = SampleSteps,
    continueLabel: String = "Continue",
    backLabel: String = "Back",
    finishLabel: String = "Finish",
    autoPlay: Boolean = false,
    onStep: ((index: Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val still = rememberReducedMotion()
    // The loop and the gesture outlive a recomposition: they read what the caller passes through these.
    val items by rememberUpdatedState(steps)
    val changed by rememberUpdatedState(onStep)
    val finished by rememberUpdatedState(onDone)

    var step by remember { mutableIntStateOf(0) }
    // A button held down by the autoPlay loop.
    var autoPressed by remember { mutableStateOf<Pressed?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Counts the presses of "Finish": each makes the pill flash and the pager hop.
    var parties by remember { mutableIntStateOf(0) }

    // The step, on its way from one to the next: the pill and the pages follow it. These five are
    // read while laying out and drawing only.
    val progress = remember { Animatable(0f) }
    // 0 with the main button alone, 1 once it has made room for "Back".
    val bar = remember { Animatable(0f) }
    // 0 for "Continue", 1 for "Finish".
    val swap = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    // 0 to 1 once after "Finish".
    val party = remember { Animatable(1f) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoPressed = null
        touches++
    }

    fun goTo(to: Int) {
        val next = to.coerceIn(0, items.size - 1)
        if (next == step) return
        step = next
        changed?.invoke(next)
    }

    fun forward() {
        touched()
        if (step < items.size - 1) {
            tick()
            goTo(step + 1)
            return
        }
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
        parties++
        finished?.invoke()
    }

    fun back() {
        touched()
        tick()
        goTo(step - 1)
    }

    /** A swipe over the page: on to the next step, or back; it does not finish the flow. */
    fun swiped(by: Int) {
        touched()
        if (step + by < 0 || step + by > items.size - 1) return
        tick()
        goTo(step + by)
    }

    val last = steps.size - 1
    LaunchedEffect(step, last, still) {
        val open = if (step > 0) 1f else 0f
        val done = if (step == last) 1f else 0f
        launch { progress.animateTo(step.toFloat(), if (still) tween(JUMP_MS, easing = InOutQuad) else springOf(DOTS_STIFFNESS, DOTS_DAMPING)) }
        launch { bar.animateTo(open, if (still) tween(JUMP_MS, easing = InOutQuad) else springOf(BAR_STIFFNESS, BAR_DAMPING)) }
        launch { swap.animateTo(done, tween(SWAP_MS, easing = LinearEasing)) }
        launch { check.animateTo(done, if (step == last && !still) springOf(POP_STIFFNESS, POP_DAMPING) else tween(POP_OUT_MS, easing = InOutQuad)) }
    }

    LaunchedEffect(parties) {
        if (parties == 0 || still) return@LaunchedEffect
        party.snapTo(0f)
        party.animateTo(1f, tween(PARTY_MS, easing = LinearEasing))
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            goTo(0)
        }
        // One round after another, timed from one origin so that rounds do not drift: each button
        // is pressed, then acts.
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            val end = items.size - 1
            for (k in 0 until end) {
                until(FIRST + k * BETWEEN - PRESS_FOR)
                autoPressed = Pressed.Next
                until(FIRST + k * BETWEEN)
                autoPressed = null
                goTo(k + 1)
            }
            val home = FIRST + (end - 1) * BETWEEN + HOLD
            until(home - PRESS_FOR)
            autoPressed = Pressed.Back
            until(home)
            autoPressed = null
            goTo(0)
            until(home + AFTER)
            origin += ((home + AFTER) * 1e9f).toLong()
        }
    }

    // The gallery's activity has light icons on both bars; this screen is pale.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bottom = max(insets.calculateBottomPadding().value, 12f) + BOTTOM
    val canBlur = Build.VERSION.SDK_INT >= 31

    Column(
        modifier.fillMaxSize().background(Background).padding(top = insets.calculateTopPadding(), bottom = bottom.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The pages, one over the other: a page is in place when the progress is on its index, and
        // fades out half a step away.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    var travelled = 0f
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            travelled = 0f
                            tracker.resetTracking()
                        },
                        onHorizontalDrag = { change, amount ->
                            travelled += amount
                            tracker.addPosition(change.uptimeMillis, change.position)
                        },
                        onDragEnd = {
                            val speed = tracker.calculateVelocity().x
                            if (travelled < -SWIPE.dp.toPx() || speed < -SWIPE_SPEED.dp.toPx()) swiped(1)
                            else if (travelled > SWIPE.dp.toPx() || speed > SWIPE_SPEED.dp.toPx()) swiped(-1)
                        },
                    )
                },
        ) {
            steps.forEachIndexed { index, item ->
                Column(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val by = index - progress.value
                            alpha = (1f - 2f * abs(by)).coerceIn(0f, 1f)
                            translationX = if (still) 0f else SLIDE.dp.toPx() * by
                        }
                        .then(if (index == step) Modifier else Modifier.clearAndSetSemantics {}),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(TILE.dp).background(item.tile, RoundedCornerShape(TILE_RADIUS.dp)), contentAlignment = Alignment.Center) {
                        Canvas(
                            Modifier.size(ICON.dp).graphicsLayer {
                                val scale = if (still) 1f else 1f - ZOOM * (index - progress.value).coerceIn(-1f, 1f)
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            surface(0.dp) { _, _, _, _ -> icon(item.icon, item.tint, item.tile) }
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    BasicText(item.title, style = TitleStyle, maxLines = 1)
                    Spacer(Modifier.height(12.dp))
                    BasicText(item.body, style = BodyStyle, maxLines = 2)
                }
            }
        }

        // The dots, and the one green pill that reaches from the first to the current one.
        Canvas(
            Modifier
                .size((PILL + SPACING * last).dp, PILL.dp)
                .graphicsLayer { translationY = -HOP.dp.toPx() * sin(PI.toFloat() * party.value) }
                .bleed(PAGER_PAD.dp)
                .clearAndSetSemantics { contentDescription = "Step ${step + 1} of ${steps.size}" },
        ) {
            val p = progress.value
            val bump = sin(PI.toFloat() * party.value)
            surface(PAGER_PAD.dp) { x, y, _, _ ->
                drawRoundRect(
                    mix(Green, GreenBright, bump),
                    topLeft = Offset(x, y),
                    size = Size(max(DOT_SIZE * 2f, PILL + SPACING * p), PILL),
                    cornerRadius = CornerRadius(PILL / 2f),
                )
                for (index in steps.indices) {
                    // The pill's end is half a step short of the next dot when it sets off, and covers it a quarter later.
                    val reached = if (index == 0) 1f else ((p - index + 0.5f) / 0.25f).coerceIn(0f, 1f)
                    drawCircle(mix(DotGrey, Color.White, reached), DOT_SIZE / 2f, Offset(x + PILL / 2f + SPACING * index, y + PILL / 2f))
                }
            }
        }

        Spacer(Modifier.height((PAGER_GAP - PILL / 2f).dp))

        Box(Modifier.padding(horizontal = SIDE.dp).fillMaxWidth().height(BUTTON.dp)) {
            // Under the main button, which hides it until it has moved over.
            val backSource = remember { MutableInteractionSource() }
            val backDown by backSource.collectIsPressedAsState()
            val backFill by animateColorAsState(if (backDown || autoPressed == Pressed.Back) GreyPressed else Grey, tween(100), label = "back")
            Box(
                Modifier
                    .size(BACK_WIDTH.dp, BUTTON.dp)
                    .graphicsLayer {
                        val scale = BACK_SCALE + (1f - BACK_SCALE) * bar.value
                        scaleX = scale
                        scaleY = scale
                        alpha = (bar.value * 1.5f).coerceIn(0f, 1f)
                    }
                    .background(backFill, CircleShape)
                    .clip(CircleShape)
                    .then(if (step > 0) Modifier else Modifier.clearAndSetSemantics {})
                    .clickable(backSource, indication = null, enabled = step > 0, role = Role.Button) { back() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(backLabel, style = BackStyle, maxLines = 1)
            }

            val nextSource = remember { MutableInteractionSource() }
            val nextDown by nextSource.collectIsPressedAsState()
            val nextFill by animateColorAsState(if (nextDown || autoPressed == Pressed.Next) BluePressed else Blue, tween(100), label = "next")
            Box(
                Modifier
                    // Its left edge moves over for "Back": the frame is set while laying out, so
                    // nothing else in the row is measured again.
                    .layout { measurable, constraints ->
                        val shift = ((BACK_WIDTH + BUTTON_GAP).dp.toPx() * bar.value).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(max(0, constraints.maxWidth - shift), constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(shift, 0) }
                    }
                    .background(nextFill, CircleShape)
                    .clip(CircleShape)
                    .clickable(nextSource, indication = null, role = Role.Button) { forward() }
                    .semantics { contentDescription = if (step == last) finishLabel else continueLabel },
                contentAlignment = Alignment.Center,
            ) {
                // A label leaves upwards and its successor arrives from below, both through a blur
                // where the system can blur (Android 12).
                BasicText(
                    continueLabel,
                    style = NextStyle,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer {
                            val gone = (swap.value / SWAP_OUT).coerceIn(0f, 1f)
                            alpha = 1f - gone
                            translationY = -LABEL_SLIDE.dp.toPx() * gone
                            val sigma = LABEL_BLUR.dp.toPx() * gone
                            renderEffect = if (canBlur && sigma > 0.5f) sigmaToRadius(sigma).let { BlurEffect(it, it, TileMode.Decal) } else null
                        }
                        .padding(12.dp)
                        .clearAndSetSemantics {},
                )
                Row(
                    Modifier
                        .graphicsLayer {
                            val here = ((swap.value - SWAP_IN) / (1f - SWAP_IN)).coerceIn(0f, 1f)
                            alpha = here
                            translationY = LABEL_SLIDE.dp.toPx() * (1f - here)
                            val sigma = LABEL_BLUR.dp.toPx() * (1f - here)
                            renderEffect = if (canBlur && sigma > 0.5f) sigmaToRadius(sigma).let { BlurEffect(it, it, TileMode.Decal) } else null
                        }
                        .padding(12.dp)
                        .clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(
                        Modifier.size(CHECK_DISC.dp).graphicsLayer {
                            val scale = max(0f, check.value)
                            scaleX = scale
                            scaleY = scale
                        },
                    ) {
                        surface(0.dp) { _, _, w, h ->
                            drawCircle(Color.White, w / 2f, Offset(w / 2f, h / 2f))
                            drawPath(Check, Blue, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(finishLabel, style = NextStyle, maxLines = 1)
                }
            }
        }
    }
}
