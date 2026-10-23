package com.ngocdevv.nativecook.blocks.pickers.budgetscrubscale

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param icon Glyph shown on the chip.
 * @param amount The category's budget at first.
 */
data class BudgetCategory(val name: String, val icon: ImageVector, val color: Color, val amount: Int)

/** What the scale says about an amount of [from] or more. */
data class BudgetVerdict(val from: Int, val title: String, val lines: List<String>)

private const val WIDTH = 340f
private const val HEIGHT = 468f
private const val CORNER = 36f
private const val INSET = 24f

// The row of chips: a square, the open pill, and the gap between two.
private const val ROW_Y = 56f
private const val CHIP = 52f
private const val CHIP_RADIUS = 18f
private const val CHIP_GAP = 16f
private const val PILL_W = 150f
private const val PILL_H = 64f

// The white card and the amount on it.
private const val CARD_Y = 112f
private const val CARD_RADIUS = 30f
private const val AMOUNT_Y = 174f
private const val DIGITS_X = 62f
private const val DIGIT_W = 28f
private const val DIGIT_H = 56f

// Seconds a wheel lags behind the amount, and the digits a second at which it is a full smear.
private const val WHEEL_LAG = 0.05f
private const val SMEAR_AT = 30f

// The coloured panel, the ruler's centre line and the indicator.
private const val SCALE_Y = 236f
private const val RULER_Y = 282f
private const val INDICATOR_X = 48f

// Dp from one mark to the next.
private const val PITCH = 14f

// A mark stretches by the ruler's speed times this many seconds, up to `STREAK_MAX` dp.
private const val STREAK = 0.016f
private const val STREAK_MAX = 9f
private const val VERDICT_Y = 312f
private const val VERDICT_SHIFT = 12f
private const val VERDICT_TIME = 0.25f
private const val SAVE = 64f

// A flick carries the ruler on by its speed times this many seconds, over `GLIDE_TIME`.
private const val CARRY = 0.2f
private const val GLIDE_TIME = 1f

// The ruler's run to another category's amount.
private const val SWITCH_TIME = 0.5f

private const val LOOP = 7f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val ChipSpring = physical(220f, 24f)
private val SnapSpring = physical(300f, 26f)
private val SwitchCurve = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** Friction: starts fast and loses speed exponentially, at rest at the end. */
private val Glide = Easing { t -> (1f - exp(-5f * t)) / (1f - exp(-5f)) }

private val Indigo = Color(0xFF3D35E0)
private val Ink = Color(0xFF2E2E3A)
private val Lavender = Color(0xFFECEBFB)
private val Turquoise = Color(0xFF2EDDC0)

private val Categories = listOf(
    BudgetCategory("Cafe", Icons.Rounded.Restaurant, Color(0xFF3D35E0), 400),
    BudgetCategory("Grocery", Icons.Rounded.ShoppingCart, Color(0xFF2EDDC0), 650),
    BudgetCategory("Taxi", Icons.Rounded.DirectionsCar, Color(0xFFF5B83D), 170),
    BudgetCategory("Gym", Icons.Rounded.FitnessCenter, Color(0xFFF58A6B), 320),
    BudgetCategory("Bills", Icons.Rounded.Description, Color(0xFFF06FA5), 540),
)

private val Verdicts = listOf(
    BudgetVerdict(0, "Normal", listOf("It's ok to eat at your place", "Up to 5% of economy")),
    BudgetVerdict(600, "That's a lot", listOf("Sometimes, you can eat in cafe", "Up to 3% of economy")),
    BudgetVerdict(1200, "Are you crazy?", listOf("Eat in restaurants every day", "Blow off 30% of money")),
)

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ChipNameStyle = type(14f, Color.White, FontWeight.Light)
private val ChipAmountStyle = type(13f, Color.White, FontWeight.Bold)
private val CurrencyStyle = type(40f, Color(0xFFC9C9D1), FontWeight.Light)
private val DigitStyle = type(44f, Ink, FontWeight.ExtraBold)
private val VerdictTitleStyle = type(15f, Color.White, FontWeight.Bold)
private val VerdictLineStyle = type(11f, Color.White.copy(alpha = 0.7f))
private val SaveStyle = type(14f, Ink, FontWeight.Bold)

private fun grouped(n: Int): String {
    var digits = n.toString()
    var out = ""
    while (digits.length > 3) {
        out = "," + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return digits + out
}

/** What moves besides the ruler itself. */
private class ScaleMotion(count: Int, verdicts: Int, home: Int, amount: Int, verdict: Int) {
    /** Each chip from a white square, 0, to the coloured pill, 1, and its dip under the finger. */
    val expand = List(count) { Animatable(if (it == home) 1f else 0f) }
    val chipPress = List(count) { Animatable(0f) }

    /** How far the row of chips has slid left. */
    val scroll = Animatable(home * (CHIP + CHIP_GAP))
    val savePress = Animatable(0f)

    /** The indicator's twitch as a mark passes under it. */
    val twitch = Animatable(0f)

    /**
     * The four digit wheels, units first: the number each one has rolled to, how fast it is rolling
     * (digits a second) and how much of its place it takes, 0…1.
     */
    val wheels = FloatArray(4)
    val wheelSpeed = FloatArray(4)
    val presence = FloatArray(4)

    /** Dp a second the ruler is moving at. */
    var speed = 0f

    /** Counts frames, so what is drawn from the fields above is drawn again. */
    var frame by mutableLongStateOf(0L)

    /** The verdict on show, each verdict's opacity, and the side it is on while hidden: 1 below, -1 above. */
    var verdict = verdict
    val verdictShown = List(verdicts) { Animatable(if (it == verdict) 1f else 0f) }
    val verdictSide = FloatArray(verdicts) { 1f }

    /** Where the ruler is heading. */
    var heading = amount

    /** `hand`: a finger moved the ruler last, so its marks are felt. `held`: the finger is still down. */
    var hand = false
    var held = false

    init {
        var place = 1
        for (k in wheels.indices) {
            wheels[k] = (amount / place).toFloat()
            presence[k] = if (k == 0 || amount >= place) 1f else 0f
            place *= 10
        }
    }
}

/**
 * A budget panel built around a ruler. The ruler is dragged sideways under a fixed indicator and
 * flung with momentum; the amount above it counts along on rolling digits, and a verdict under it
 * rolls over when the amount crosses a threshold. The row of chips on top picks the category: the
 * chosen one opens into a coloured pill that carries its amount, and the ruler slides to it.
 *
 * @param verdicts In rising order of `from`; the first one should start at 0.
 * @param initial Index of the category that is open at first.
 * @param maximum The scale runs from 0 to [maximum], one mark every [step].
 * @param autoPlay Flings the ruler twice, picks another category and comes back by itself, in a loop.
 * @param onChange Called with the category's index and its amount when the ruler settles or another category opens.
 * @param onSave Called with every category's amount when Save is tapped.
 */
@Composable
fun BudgetScrubScale(
    categories: List<BudgetCategory> = Categories,
    verdicts: List<BudgetVerdict> = Verdicts,
    initial: Int = 0,
    maximum: Int = 2000,
    step: Int = 10,
    currency: String = "$",
    saveLabel: String = "Save",
    autoPlay: Boolean = false,
    onChange: ((index: Int, amount: Int) -> Unit)? = null,
    onSave: ((amounts: List<Int>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    val still = rememberReducedMotion()
    val home = initial.coerceIn(0, categories.lastIndex)
    val auto = autoPlay && !still
    val unit = max(1, step)
    val dpPerUnit = PITCH / unit
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)
    val saved by rememberUpdatedState(onSave)

    fun verdictFor(amount: Int) = verdicts.indexOfLast { amount >= it.from }.coerceAtLeast(0)

    fun snapped(value: Float) = ((value / unit).roundToInt() * unit).coerceIn(0, maximum)

    // Each category's amount, the open one's as of the last time the ruler settled.
    val amounts = remember(categories) { mutableStateListOf<Int>().apply { addAll(categories.map { it.amount.coerceIn(0, maximum) }) } }
    var opened by remember(categories) { mutableIntStateOf(home) }
    // The amount under the indicator; the ruler's position follows from it.
    val amount = remember(categories) { Animatable(amounts[home].toFloat()) }
    val motion = remember(categories, verdicts) {
        ScaleMotion(categories.size, verdicts.size, home, amounts[home], verdictFor(amounts[home]))
    }
    // The amount under the indicator rounded to a mark, for the open pill.
    val live by remember(amount, unit, maximum) { derivedStateOf { snapped(amount.value) } }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    val measurer = rememberTextMeasurer()
    // Digits are measured at one pixel a dp, and drawn on a canvas scaled to the screen's density.
    val digits = remember { (0..9).map { measurer.measure(it.toString(), DigitStyle, density = Density(1f)) } }
    val indicatorGlow = remember { SoftRoundRect(Turquoise.copy(alpha = 0.55f), sigma = 6f) }
    val saveShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.14f), sigma = 10f) }

    // Up to 1 in `up` ms and back to 0 in `down`.
    fun pulse(value: Animatable<Float, AnimationVector1D>, up: Int, down: Int) {
        if (still) return
        scope.launch {
            value.animateTo(1f, tween(up, easing = LinearEasing))
            value.animateTo(0f, tween(down, easing = LinearEasing))
        }
    }

    fun touch() {
        motion.hand = true
        touches += 1
    }

    // Makes `target` the open category's amount.
    fun keep(target: Int) {
        motion.heading = target
        if (amounts[opened] == target) return
        amounts[opened] = target
        changed?.invoke(opened, target)
    }

    // Sends the ruler to `target` over `time` seconds as if flung, and keeps it.
    fun fling(target: Float, time: Float) {
        val rounded = snapped(target)
        scope.launch { amount.animateTo(rounded.toFloat(), tween((time * 1000).toInt(), easing = Glide)) }
        keep(rounded)
    }

    // The ruler's run to `target`, the amount of the category that has just opened.
    fun run(target: Int) {
        motion.heading = target
        scope.launch {
            if (still) amount.snapTo(target.toFloat()) else amount.animateTo(target.toFloat(), tween((SWITCH_TIME * 1000).toInt(), easing = SwitchCurve))
        }
    }

    // Opens category `index`: its chip grows into the pill, the row slides it to the left edge, and
    // the ruler runs to its amount.
    fun select(index: Int) {
        if (index == opened || index !in categories.indices) return
        amounts[opened] = motion.heading
        val old = opened
        opened = index
        pulse(motion.chipPress[index], 80, 180)
        scope.launch { if (still) motion.expand[old].snapTo(0f) else motion.expand[old].animateTo(0f, ChipSpring) }
        scope.launch { if (still) motion.expand[index].snapTo(1f) else motion.expand[index].animateTo(1f, ChipSpring) }
        scope.launch {
            val offset = index * (CHIP + CHIP_GAP)
            if (still) motion.scroll.snapTo(offset) else motion.scroll.animateTo(offset, ChipSpring)
        }
        run(amounts[index])
        changed?.invoke(index, amounts[index])
    }

    // Back to the first category with every amount as it began.
    fun restore() {
        motion.hand = false
        select(home)
        categories.forEachIndexed { index, category -> amounts[index] = category.amount.coerceIn(0, maximum) }
        run(amounts[home])
    }

    // Every frame: the ruler's speed, the wheels, the mark under the indicator and the verdict.
    LaunchedEffect(motion, amount, still) {
        var before = withFrameNanos { it }
        var lastAmount = amount.value
        var notch = (amount.value / unit).roundToInt()
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - before) / 1e9f, 0.1f)
            before = now
            if (dt <= 0f) continue
            val value = amount.value
            motion.speed = (value - lastAmount) / dt * dpPerUnit
            lastAmount = value

            val whole = max(0, value.roundToInt())
            var place = 1
            var moved = motion.speed != 0f
            for (k in motion.wheels.indices) {
                val gap = (whole / place) - motion.wheels[k]
                val move = if (still || abs(gap) < 0.004f) gap else gap * (1f - exp(-dt / WHEEL_LAG))
                motion.wheelSpeed[k] = move / dt
                motion.wheels[k] += move
                val wanted = if (k == 0 || whole >= place) 1f else 0f
                val grown = if (still) wanted else motion.presence[k] + (wanted - motion.presence[k]) * (1f - exp(-dt / WHEEL_LAG))
                if (move != 0f || abs(grown - motion.presence[k]) > 0.0005f) moved = true
                motion.presence[k] = if (abs(wanted - grown) < 0.001f) wanted else grown
                place *= 10
            }
            if (moved) motion.frame += 1

            // A mark has come under the indicator: it twitches, and a ruler moved by hand clicks.
            val under = (value / unit).roundToInt()
            if (under != notch) {
                notch = under
                if (motion.hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (!still) {
                    launch {
                        motion.twitch.animateTo(1f, tween(50, easing = LinearEasing))
                        motion.twitch.animateTo(0f, tween(180, easing = LinearEasing))
                    }
                }
            }

            val next = verdictFor(whole)
            if (next != motion.verdict && next in motion.verdictShown.indices) {
                // The old title leaves upward, the new one comes up from below.
                val old = motion.verdict
                motion.verdictSide[old] = -1f
                motion.verdictSide[next] = 1f
                motion.verdict = next
                val time = if (still) 0 else (VERDICT_TIME * 1000).toInt()
                launch { motion.verdictShown[old].animateTo(0f, tween(time, easing = EaseOutStrong)) }
                launch { motion.verdictShown[next].animateTo(1f, tween(time, easing = EaseOutStrong)) }
            }
        }
    }

    // The script a finger would follow: two flings up the scale, the next category, and back.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, put every amount back and start over.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held) return@LaunchedEffect
            restore()
            delay(900)
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var round = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((round * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            until(1.0f)
            fling(maximum * 0.45f, 0.9f)
            until(2.4f)
            fling(maximum * 0.8f, 0.8f)
            until(3.8f)
            select((home + 1) % categories.size)
            until(5.1f)
            restore()
            round += 1
        }
    }

    // Where chip `index` is: its left edge, width and height in dp.
    fun chipFrame(index: Int): Triple<Float, Float, Float> {
        var left = INSET - motion.scroll.value
        for (i in 0 until index) left += CHIP + (PILL_W - CHIP) * motion.expand[i].value + CHIP_GAP
        val e = motion.expand[index].value
        return Triple(left, CHIP + (PILL_W - CHIP) * e, CHIP + (PILL_H - CHIP) * e)
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp).clip(RoundedCornerShape(CORNER.dp)).background(Color(0xFFF7F7F8))) {
        categories.forEachIndexed { index, category ->
            val glow = remember(category.color) { SoftRoundRect(category.color.copy(alpha = 0.4f), sigma = 10f) }
            val shown = if (index == opened) live else amounts[index]
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val (_, width, height) = chipFrame(index)
                        val placeable = measurable.measure(Constraints.fixed(width.dp.roundToPx(), height.dp.roundToPx()))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        val (left, _, height) = chipFrame(index)
                        val dip = 1f - 0.05f * motion.chipPress[index].value
                        translationX = left.dp.toPx()
                        translationY = (ROW_Y - height / 2f).dp.toPx()
                        scaleX = dip
                        scaleY = dip
                    }
                    .drawBehind {
                        val e = motion.expand[index].value.coerceIn(0f, 1f)
                        scale(density, density, pivot = Offset.Zero) {
                            drawIntoCanvas { glow.draw(it.nativeCanvas, 0f, 8f, size.width / density, size.height / density, CHIP_RADIUS, e) }
                        }
                    }
                    .clip(RoundedCornerShape(CHIP_RADIUS.dp))
                    .background(Color.White)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        touch()
                        select(index)
                    }
                    .semantics {
                        contentDescription = "${category.name}, $currency ${grouped(shown)}"
                        selected = index == opened
                    },
            ) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = motion.expand[index].value.coerceIn(0f, 1f) }.background(category.color))
                Box(
                    Modifier
                        .offset(10.dp, 4.dp)
                        .size(44.dp)
                        .graphicsLayer {
                            val e = motion.expand[index].value.coerceIn(0f, 1f)
                            alpha = e
                            translationY = (6f * e).dp.toPx()
                        }
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                )
                Box(
                    Modifier
                        .size(CHIP.dp)
                        .graphicsLayer {
                            val e = motion.expand[index].value.coerceIn(0f, 1f)
                            translationX = (6f * e).dp.toPx()
                            translationY = (6f * e).dp.toPx()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(category.icon, null, Modifier.size(24.dp).graphicsLayer { alpha = 1f - motion.expand[index].value.coerceIn(0f, 1f) }, tint = category.color)
                    Icon(category.icon, null, Modifier.size(24.dp).graphicsLayer { alpha = motion.expand[index].value.coerceIn(0f, 1f) }, tint = Color.White)
                }
                Column(
                    Modifier
                        .wrapContentSize(align = Alignment.CenterStart, unbounded = true)
                        .offset(66.dp, 0.dp)
                        .graphicsLayer { alpha = (motion.expand[index].value * 2f - 1f).coerceIn(0f, 1f) },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BasicText(category.name, style = ChipNameStyle, maxLines = 1, softWrap = false)
                    BasicText("$currency ${grouped(shown)}", style = ChipAmountStyle, maxLines = 1, softWrap = false)
                }
            }
        }

        // The white card with the amount on rolling digits.
        Box(
            Modifier
                .offset(0.dp, CARD_Y.dp)
                .size(WIDTH.dp, (HEIGHT - CARD_Y).dp)
                .background(Color.White, RoundedCornerShape(topStart = CARD_RADIUS.dp, topEnd = CARD_RADIUS.dp)),
        )
        At(currency, CurrencyStyle, INSET + 2f, AMOUNT_Y - 30f, 34f, 60f)
        Canvas(Modifier.offset(DIGITS_X.dp, (AMOUNT_Y - DIGIT_H / 2f).dp).size((DIGIT_W * 4f).dp, DIGIT_H.dp)) {
            motion.frame
            scale(density, density, pivot = Offset.Zero) { drawDigits(motion, digits) }
        }
        // A small calculator on a lavender tile.
        Canvas(Modifier.offset((WIDTH - INSET - 34f).dp, (AMOUNT_Y - 17f).dp).size(34.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                drawRoundRect(Lavender, size = Size(34f, 34f), cornerRadius = CornerRadius(10f))
                drawRoundRect(Indigo, Offset(11f, 9.5f), Size(12f, 15f), CornerRadius(2.5f))
                drawRect(Lavender, Offset(13f, 11.5f), Size(8f, 3f))
                for (key in 0 until 4) drawRect(Lavender, Offset(13f + (key % 2) * 5.2f, 16.3f + (key / 2) * 3.6f), Size(2.8f, 2.2f))
            }
        }

        // The coloured panel: the ruler under its indicator, the verdict and Save.
        Box(
            Modifier
                .offset(0.dp, SCALE_Y.dp)
                .size(WIDTH.dp, (HEIGHT - SCALE_Y).dp)
                .background(Indigo, RoundedCornerShape(topStart = CORNER.dp))
                .semantics {
                    contentDescription = categories[opened].name
                    stateDescription = "$currency ${grouped(live)}"
                    customActions = listOf(
                        CustomAccessibilityAction("More") {
                            touch()
                            val target = (motion.heading + unit).coerceIn(0, maximum)
                            scope.launch { if (still) amount.snapTo(target.toFloat()) else amount.animateTo(target.toFloat(), SnapSpring) }
                            keep(target)
                            true
                        },
                        CustomAccessibilityAction("Less") {
                            touch()
                            val target = (motion.heading - unit).coerceIn(0, maximum)
                            scope.launch { if (still) amount.snapTo(target.toFloat()) else amount.animateTo(target.toFloat(), SnapSpring) }
                            keep(target)
                            true
                        },
                    )
                }
                .pointerInput(categories, maximum, unit, still) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        motion.held = true
                        touch()
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var grab = 0f
                        var from = down.position.x
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging && abs(change.position.x - down.position.x) >= 4.dp.toPx()) {
                                dragging = true
                                grab = amount.value
                                from = change.position.x
                            }
                            if (dragging) {
                                // Past either end the ruler gives less and less, up to four marks.
                                val raw = grab - (change.position.x - from) / density / dpPerUnit
                                val over = if (raw < 0f) -raw else max(0f, raw - maximum)
                                val limit = unit * 4f
                                val give = limit * (1f - 1f / (over / limit + 1f))
                                val next = if (over <= 0f) raw else if (raw < 0f) -give else maximum + give
                                scope.launch { amount.snapTo(next) }
                                change.consume()
                            }
                        }
                        motion.held = false
                        touch()
                        if (!dragging) return@awaitEachGesture
                        // A flick carries the ruler on and friction stops it on a mark; a slow release just snaps.
                        val carry = -tracker.calculateVelocity().x / density / dpPerUnit * CARRY
                        val flung = !still && abs(carry) >= unit
                        val target = snapped(amount.value + if (flung) carry else 0f)
                        scope.launch {
                            when {
                                still -> amount.snapTo(target.toFloat())
                                flung -> amount.animateTo(target.toFloat(), tween((GLIDE_TIME * 1000).toInt(), easing = Glide))
                                else -> amount.animateTo(target.toFloat(), SnapSpring)
                            }
                        }
                        keep(target)
                    }
                },
        )
        Canvas(Modifier.offset(0.dp, (RULER_Y - 12f).dp).size(WIDTH.dp, 24.dp)) {
            motion.frame
            scale(density, density, pivot = Offset.Zero) {
                drawRuler(amount.value, if (still) 0f else motion.speed, unit, maximum, dpPerUnit)
            }
        }
        Box(
            Modifier
                .offset((INDICATOR_X - 3f).dp, (RULER_Y - 17f).dp)
                .size(6.dp, 34.dp)
                .graphicsLayer { scaleY = 1f + 0.1f * motion.twitch.value }
                .drawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { indicatorGlow.draw(it.nativeCanvas, 0f, 0f, 6f, 34f, 3f) }
                    }
                }
                .background(Turquoise, RoundedCornerShape(3.dp)),
        )

        verdicts.forEachIndexed { index, verdict ->
            Box(
                Modifier
                    .offset((INSET + 4f).dp, VERDICT_Y.dp)
                    .size(200.dp, 20.dp)
                    .graphicsLayer {
                        val shown = motion.verdictShown[index].value
                        alpha = shown
                        translationY = (motion.verdictSide[index] * VERDICT_SHIFT * (1f - shown)).dp.toPx()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(verdict.title, style = VerdictTitleStyle, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
            }
            Column(
                Modifier
                    .offset((INSET + 4f).dp, (VERDICT_Y + 28f).dp)
                    .graphicsLayer { alpha = motion.verdictShown[index].value },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                verdict.lines.forEach { BasicText(it, style = VerdictLineStyle, maxLines = 1, softWrap = false) }
            }
        }

        Box(
            Modifier
                .offset((WIDTH - INSET - SAVE).dp, (HEIGHT - INSET - SAVE).dp)
                .size(SAVE.dp)
                .graphicsLayer {
                    val dip = 1f - 0.06f * motion.savePress.value
                    scaleX = dip
                    scaleY = dip
                }
                .drawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { saveShadow.draw(it.nativeCanvas, 0f, 6f, SAVE, SAVE, 20f) }
                    }
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    pulse(motion.savePress, 80, 180)
                    amounts[opened] = motion.heading
                    saved?.invoke(amounts.toList())
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(saveLabel, style = SaveStyle, maxLines = 1)
        }
    }
}

/** A line of text in a box whose top-left corner is at [x], [y]. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, x: Float, y: Float, width: Float, height: Float) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp), contentAlignment = Alignment.CenterStart) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

/** Four wheels of digits, left-aligned: a wheel the amount does not reach takes no room. */
private fun DrawScope.drawDigits(motion: ScaleMotion, digits: List<TextLayoutResult>) {
    var x = 0f
    for (k in motion.wheels.indices.reversed()) {
        val presence = motion.presence[k]
        if (presence <= 0.01f) continue
        val rolled = motion.wheels[k]
        val digit = floor(rolled).toInt()
        val part = rolled - floor(rolled)
        // A wheel that is spinning smears: taller and fainter.
        val blur = min(1f, abs(motion.wheelSpeed[k]) / SMEAR_AT)
        clipRect(x, 0f, x + DIGIT_W * presence, DIGIT_H) {
            withTransform({
                translate(x + DIGIT_W * presence / 2f, DIGIT_H / 2f)
                scale(1f, 1f + 0.25f * blur, pivot = Offset.Zero)
            }) {
                for ((offset, shown) in listOf(-part to digit, 1f - part to digit + 1)) {
                    if (abs(offset) >= 1f) continue
                    val text = digits[((shown % 10) + 10) % 10]
                    drawText(
                        text,
                        topLeft = Offset(-text.size.width / 2f, offset * DIGIT_H - text.size.height / 2f),
                        alpha = presence * (1f - 0.45f * blur),
                    )
                }
            }
        }
        x += DIGIT_W * presence
    }
}

/**
 * The marks in sight: dot, dot, short tick, dot, dot, tall tick. A fast ruler streaks: every mark
 * stretches along its way into a dash and the ticks sink to the dots' height.
 */
private fun DrawScope.drawRuler(value: Float, speed: Float, unit: Int, maximum: Int, dpPerUnit: Float) {
    val stretch = min(abs(speed) * STREAK, STREAK_MAX)
    val flat = stretch / STREAK_MAX
    val first = max(0, floor((value - INDICATOR_X / dpPerUnit) / unit).toInt() - 1)
    val last = min(maximum / unit, ceil((value + (WIDTH - INDICATOR_X) / dpPerUnit) / unit).toInt() + 1)
    val color = Color.White.copy(alpha = 0.6f * (1f - 0.35f * stretch / STREAK_MAX))
    for (j in first..last) {
        val x = INDICATOR_X + (j * unit - value) * dpPerUnit
        val tall = if (j % 6 == 0) 12f else if (j % 6 == 3) 6f else 2.6f
        val height = tall + (2.6f - tall) * flat
        val width = (if (tall > 3f) 2f else 2.6f) + stretch
        drawRoundRect(color, Offset(x - width / 2f, 12f - height / 2f), Size(width, height), CornerRadius(1.3f))
    }
}
