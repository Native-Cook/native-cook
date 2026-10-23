package com.ngocdevv.nativecook.blocks.charts.semicirclespendgauge

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A category of the gauge.
 *
 * @param color The segment's colour.
 * @param ink The icon's glyph.
 * @param tint The icon's circle.
 */
data class SpendCategory(val name: String, val color: Color, val ink: Color, val tint: Color, val icon: SemicircleSpendGaugeIcon)

/**
 * A period of the dropdown.
 *
 * @param label The dropdown's label: "Last Week".
 * @param span The word in the footer: "weekly".
 * @param limit The whole half circle stands for the limit.
 * @param amounts One amount per category, in the same order.
 */
data class SpendPeriod(val label: String, val span: String, val limit: Int, val amounts: List<Double>)

private val Categories = listOf(
    SpendCategory("Shopping", Color(0xFF375EFB), Color(0xFF2748C8), Color(0xFFE8EEFF), SemicircleSpendGaugeIcon.Shopping),
    SpendCategory("Utilities", Color(0xFF2CBDF2), Color(0xFF1B93B8), Color(0xFFE1F6FD), SemicircleSpendGaugeIcon.Receipt),
    SpendCategory("Others", Color(0xFF9AA4B2), Color(0xFF5F6368), Color(0xFFF3F4F5), SemicircleSpendGaugeIcon.Coins),
)

private val Periods = listOf(
    SpendPeriod("Last Week", "weekly", 2000, listOf(900.0, 600.0, 300.0)),
    SpendPeriod("Last Month", "monthly", 8000, listOf(2880.0, 2150.0, 1390.0)),
)

private val Page = Color(0xFFDFE0E2)
private val CardColor = Color(0xFFFFFFFF)
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF5F6368)
private val Caption = Color(0xFF6B6B6B)
private val LineColor = Color(0xFFE8E8E8)
private val Border = Color(0xFFE3E3E3)
private val Track = Color(0xFFE6E7E9)
private val Pressed = Color(0xFFF2F3F5)
private val InfoColor = Color(0xFF8E9196)

private val MARGIN = 16.dp
private val MAX_CARD = 420.dp
private val RADIUS = 28.dp
private val PAD = 18.dp
// The gauge: a half ring that pops a segment out radially, with a hairline gap between segments.
private const val GAUGE = 260f
private const val THICKNESS = 27f
private const val GAP = 2f
private const val GAUGE_TOP = 22f
private const val POP = 8f
// Taps this far outside the ring still pick a segment.
private const val TAP_SLOP = 14f
private const val DIMMED = 0.35f
private val ICON = 40.dp
private const val ICON_POP = 1.1f
private const val GAUGE_WIDTH = GAUGE + POP * 2
private const val GAUGE_HEIGHT = GAUGE / 2 + POP

// Motion. Springs in React Native's mass, stiffness and damping.
private class SpringParams(val damping: Float, val stiffness: Float, val mass: Float) {
    fun spec(): AnimationSpec<Float> = spring(
        dampingRatio = damping / (2 * sqrt(stiffness * mass)),
        stiffness = stiffness / mass,
        visibilityThreshold = 0.0005f,
    )
}
private val POP_SPRING = SpringParams(damping = 13f, stiffness = 240f, mass = 0.8f)
private val FLOW_SPRING = SpringParams(damping = 17f, stiffness = 120f, mass = 1f)
private const val DIM_FOR = 220
private const val ROLL = 600
private const val MENU_FOR = 200
private const val MENU_FROM = 0.9f
private const val REDUCED_FADE = 160

// The autoPlay timeline, in seconds from the start of each loop: each segment pops in turn, the
// selection clears, then the dropdown switches to the next period and back.
const val LOOP = 10f
private val POP_AT = listOf(0.8f, 1.9f, 3.0f)
private const val CLEAR_AT = 4.1f
private val OPEN_AT = listOf(4.8f, 7.4f)
private val PICK_AT = listOf(5.5f, 8.1f)
// The menu row lights this long before the autoPlay finger picks it.
private const val PRESS_FOR = 0.18f
private const val USER_HOLD = 3f
// How long the card takes to come back to rest after a touch, before the loop restarts.
private const val SETTLE = 1.2f

private val OutCubic = Easing { 1 - (1 - it).pow(3) }
private val InOutQuad = Easing { if (it < 0.5f) 2 * it * it else 1 - (2 - 2 * it).pow(2) / 2 }

private fun cents(symbol: String, value: Double): String {
    val hundredths = (value * 100).roundToLong()
    val whole = floor(hundredths / 100.0).toLong()
    val fraction = hundredths % 100
    return "$symbol${NumberFormat.getIntegerInstance(Locale.US).format(whole)}.${fraction.toString().padStart(2, '0')}"
}

/** One segment of the half ring between two fractions of it, pushed out along its middle. */
private fun segment(cx: Float, cy: Float, start: Float, end: Float, pop: Float, gapStart: Boolean, gapEnd: Boolean): Path? {
    val outer = GAUGE / 2
    val inner = outer - THICKNESS
    // Half the gap, as an angle at the ring's middle.
    val half = (GAP / 2 / (outer - THICKNESS / 2)) * 180f / PI.toFloat()
    val a0 = 180f + start * 180f + if (gapStart) half else 0f
    val a1 = 180f + end * 180f - if (gapEnd) half else 0f
    val sweep = a1 - a0
    if (sweep <= 0f) return null
    val middle = (a0 + a1) / 2 * PI.toFloat() / 180f
    val x = cx + cos(middle) * POP * pop
    val y = cy + sin(middle) * POP * pop
    return Path().apply {
        arcTo(Rect(x - outer, y - outer, x + outer, y + outer), a0, sweep, true)
        arcTo(Rect(x - inner, y - inner, x + inner, y + inner), a0 + sweep, -sweep, false)
        close()
    }
}

/** A rolling amount, read in its own scope so a roll recomposes only the text. */
@Composable
private fun Amount(value: () -> Float, symbol: String, style: TextStyle, minSize: Float, modifier: Modifier = Modifier) {
    BasicText(
        cents(symbol, value().toDouble()),
        modifier,
        style = style,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize.sp, maxFontSize = style.fontSize),
    )
}

/**
 * A spending summary card around a half-donut gauge: the half circle is the period's limit and
 * each category takes its share of it. Tapping a segment pops it out and dims the rest while the
 * centre rolls to its total; the dropdown switches the period, and the segments re-flow on a
 * spring as every amount rolls to its new value.
 *
 * @param categories Up to three categories, drawn left to right.
 * @param initialPeriod The period shown at rest.
 * @param autoPlay Pops each segment in turn and switches the period and back, every [LOOP] seconds.
 * @param onSelect Called with the category's place, or -1 when the selection is cleared.
 */
@Composable
fun SemicircleSpendGauge(
    title: String = "Spending Summary",
    categories: List<SpendCategory> = Categories,
    periods: List<SpendPeriod> = Periods,
    initialPeriod: Int = 0,
    symbol: String = "$",
    spentLabel: String = "SPENT",
    autoPlay: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
    onPeriodChange: ((Int, SpendPeriod) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val rest = min(max(0, initialPeriod), periods.size - 1)
    val count = min(3, categories.size)
    val currentPeriods by rememberUpdatedState(periods)
    val selectChanged by rememberUpdatedState(onSelect)
    val periodChanged by rememberUpdatedState(onPeriodChange)

    var period by remember { mutableIntStateOf(rest) }
    var selected by remember { mutableIntStateOf(-1) }
    var menuOpen by remember { mutableStateOf(false) }
    var autoPressed by remember { mutableIntStateOf(-1) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun fractions(index: Int): List<Float> {
        val p = currentPeriods[index]
        var sum = 0f
        return List(count) { i ->
            sum = min(1f, sum + (p.amounts.getOrElse(i) { 0.0 } / p.limit).toFloat())
            sum
        }
    }
    fun total(index: Int) = currentPeriods[index].amounts.take(count).sum().toFloat()

    // Where each segment ends along the half ring (0 left, 1 right), its pop and its dimming, and
    // the numbers on the card: the centre, then each category.
    val ends = remember { fractions(rest).map { Animatable(it) } }
    val pops = remember { List(count) { Animatable(0f) } }
    val dims = remember { List(count) { Animatable(0f) } }
    val centre = remember { Animatable(total(rest)) }
    val columns = remember { List(count) { Animatable(periods[rest].amounts.getOrElse(it) { 0.0 }.toFloat()) } }
    val menu = remember { Animatable(0f) }

    LaunchedEffect(period) {
        val next = fractions(period)
        val target = currentPeriods[period].amounts
        ends.forEachIndexed { i, value ->
            launch { if (reduced) value.snapTo(next[i]) else value.animateTo(next[i], FLOW_SPRING.spec()) }
        }
        columns.forEachIndexed { i, value ->
            val to = target.getOrElse(i) { 0.0 }.toFloat()
            launch { if (reduced) value.snapTo(to) else value.animateTo(to, tween(ROLL, easing = OutCubic)) }
        }
    }

    LaunchedEffect(selected, period) {
        val target = if (selected < 0) total(period) else currentPeriods[period].amounts[selected].toFloat()
        launch { if (reduced) centre.snapTo(target) else centre.animateTo(target, tween(ROLL, easing = OutCubic)) }
        pops.forEachIndexed { i, value ->
            launch { if (reduced) value.snapTo(0f) else value.animateTo(if (i == selected) 1f else 0f, POP_SPRING.spec()) }
        }
        dims.forEachIndexed { i, value ->
            val off = if (selected >= 0 && i != selected) 1f else 0f
            launch { value.animateTo(off, tween(if (reduced) REDUCED_FADE else DIM_FOR, easing = InOutQuad)) }
        }
    }

    LaunchedEffect(menuOpen) {
        menu.animateTo(if (menuOpen) 1f else 0f, tween(if (reduced) REDUCED_FADE else MENU_FOR, easing = OutCubic))
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun touched() {
        touches++
    }

    fun select(index: Int, byHand: Boolean) {
        val next = if (index == selected) -1 else index
        selected = next
        if (byHand) tick()
        selectChanged?.invoke(next)
    }

    fun pick(index: Int, byHand: Boolean) {
        menuOpen = false
        autoPressed = -1
        if (index == period) return
        selected = -1
        period = index
        if (byHand) tick()
        periodChanged?.invoke(index, currentPeriods[index])
    }

    LaunchedEffect(autoPlay, touches, rest) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to rest, then the loop starts once everything has settled.
            menuOpen = false
            autoPressed = -1
            selected = -1
            period = rest
            delay((SETTLE * 1000).toLong())
        }
        val next = (rest + 1) % currentPeriods.size
        val events = buildList<Pair<Float, () -> Unit>> {
            POP_AT.forEachIndexed { i, at -> add(at to { select(i, false) }) }
            add(CLEAR_AT to { select(-1, false) })
            OPEN_AT.forEachIndexed { i, at ->
                val target = if (i == 0) next else rest
                add(at to { menuOpen = true })
                add(PICK_AT[i] - PRESS_FOR to { autoPressed = target })
                add(PICK_AT[i] to { pick(target, false) })
            }
        }
        var origin = System.nanoTime()
        while (true) {
            // Each step at its own time from the start of the loop, so every loop is the same.
            for ((at, run) in events) {
                delay(max(0L, (origin + (at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                run()
            }
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val current = periods[period]
    val centreLabel = if (selected < 0) spentLabel else categories[selected].name.uppercase()

    BoxWithConstraints(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.Center) {
        val cardWidth = min(MAX_CARD.value, maxWidth.value - MARGIN.value * 2).dp
        Box(
            Modifier
                .width(cardWidth)
                .dropShadow(RoundedCornerShape(RADIUS), Shadow(radius = 40.dp, color = Color.Black, offset = DpOffset(0.dp, 16.dp), alpha = 0.06f))
                .background(CardColor, RoundedCornerShape(RADIUS)),
        ) {
            Column(Modifier.padding(start = PAD, end = PAD, bottom = PAD)) {
                // Header.
                Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SemicircleSpendGaugeGlyph(SemicircleSpendGaugeIcon.Pie, 22.dp, Mute)
                        BasicText(
                            title,
                            style = TextStyle(color = Ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    }
                    Row(
                        Modifier
                            .height(38.dp)
                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Period: ${current.label}",
                            ) {
                                touched()
                                menuOpen = !menuOpen
                            }
                            .padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(current.label, style = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 20.sp))
                        SemicircleSpendGaugeGlyph(SemicircleSpendGaugeIcon.Chevron, 18.dp, Ink)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(LineColor))

                // Gauge.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                touched()
                                // The touch is measured in the padded area the gauge is centred in.
                                val dx = (offset.x - size.width / 2f).toDp().value
                                val dy = offset.y.toDp().value - (GAUGE_TOP + GAUGE_HEIGHT)
                                val distance = hypot(dx, dy)
                                if (distance < GAUGE / 2 - THICKNESS - TAP_SLOP || distance > GAUGE / 2 + TAP_SLOP || dy > TAP_SLOP) {
                                    if (selected >= 0) select(selected, true)
                                    return@detectTapGestures
                                }
                                // The fraction along the half ring, 0 at the left end.
                                val along = min(1f, max(0f, 1 - atan2(-dy, dx) / PI.toFloat()))
                                val index = fractions(period).indexOfFirst { along <= it }
                                if (index >= 0) select(index, true) else if (selected >= 0) select(selected, true)
                            }
                        }
                        .padding(top = GAUGE_TOP.dp, bottom = 18.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.size(GAUGE_WIDTH.dp, GAUGE_HEIGHT.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            scale(density, density, pivot = Offset.Zero) {
                                val cx = GAUGE_WIDTH / 2
                                val cy = GAUGE_HEIGHT
                                // The grey rest of the half ring: the part of the limit not spent.
                                segment(cx, cy, ends[count - 1].value, 1f, 0f, gapStart = true, gapEnd = false)?.let { drawPath(it, Track) }
                                for (i in 0 until count) {
                                    val start = if (i == 0) 0f else ends[i - 1].value
                                    val end = ends[i].value
                                    val path = segment(cx, cy, start, end, pops[i].value, i > 0, i < count - 1 || end < 0.999f) ?: continue
                                    drawPath(path, categories[i].color, alpha = 1 - (1 - DIMMED) * dims[i].value)
                                }
                            }
                        }
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = (POP + THICKNESS + 8).dp)
                                .offset(y = 2.dp)
                                .clearAndSetSemantics { contentDescription = "$centreLabel ${cents(symbol, centre.value.toDouble())}" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                centreLabel,
                                style = TextStyle(color = Caption, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 1.6.sp),
                                maxLines = 1,
                            )
                            Amount(
                                { centre.value },
                                symbol,
                                TextStyle(
                                    color = Ink,
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.8).sp,
                                    fontFeatureSettings = "tnum",
                                    textAlign = TextAlign.Center,
                                ),
                                minSize = 16f,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(LineColor))

                // Columns.
                Row(Modifier.padding(vertical = 18.dp)) {
                    for (i in 0 until count) {
                        val category = categories[i]
                        Box(
                            Modifier
                                .weight(1f)
                                .drawBehind {
                                    if (i > 0) drawRect(LineColor, size = size.copy(width = 1.dp.toPx()))
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                ) {
                                    touched()
                                    select(i, true)
                                }
                                .semantics { contentDescription = "${category.name}, ${cents(symbol, current.amounts.getOrElse(i) { 0.0 })}" },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(
                                Modifier
                                    .padding(horizontal = 6.dp)
                                    .graphicsLayer { alpha = 1 - (1 - DIMMED) * dims[i].value },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(ICON)
                                        .graphicsLayer {
                                            val s = 1 + (ICON_POP - 1) * pops[i].value
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .background(category.tint, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SemicircleSpendGaugeGlyph(category.icon, 20.dp, category.ink)
                                }
                                Spacer(Modifier.height(14.dp))
                                BasicText(
                                    category.name,
                                    style = TextStyle(color = Mute, fontSize = 15.sp, lineHeight = 20.sp),
                                    maxLines = 1,
                                )
                                Amount(
                                    { columns[i].value },
                                    symbol,
                                    TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                    minSize = 9f,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                // Footer.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(
                        buildAnnotatedString {
                            append("Your ${current.span} spending limit is ")
                            withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.SemiBold)) { append("$symbol${current.limit}.") }
                        },
                        Modifier.weight(1f),
                        style = TextStyle(color = Mute, fontSize = 14.sp, lineHeight = 19.sp),
                        maxLines = 1,
                    )
                    SemicircleSpendGaugeGlyph(SemicircleSpendGaugeIcon.Info, 16.dp, InfoColor)
                }
            }

            if (menuOpen) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Close menu",
                        ) {
                            touched()
                            menuOpen = false
                        },
                )
            }

            // The dropdown, growing from the pill's corner.
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 62.dp, end = PAD)
                    .width(168.dp)
                    .graphicsLayer {
                        alpha = menu.value
                        val s = if (reduced) 1f else MENU_FROM + (1 - MENU_FROM) * menu.value
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .dropShadow(RoundedCornerShape(14.dp), Shadow(radius = 28.dp, color = Color.Black, offset = DpOffset(0.dp, 10.dp), alpha = 0.12f))
                    .background(CardColor, RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(vertical = 6.dp)
                    .then(if (menuOpen) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                periods.forEachIndexed { i, p ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressedNow by interaction.collectIsPressedAsState()
                    Row(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(if (pressedNow || autoPressed == i) Pressed else Color.Transparent, RoundedCornerShape(9.dp))
                            .semantics { this.selected = i == period }
                            .clickable(interactionSource = interaction, indication = null, enabled = menuOpen) {
                                touched()
                                pick(i, true)
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BasicText(p.label, style = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 20.sp))
                        if (i == period) SemicircleSpendGaugeGlyph(SemicircleSpendGaugeIcon.Check, 18.dp, categories.firstOrNull()?.color ?: Ink)
                    }
                }
            }
        }
    }
}
