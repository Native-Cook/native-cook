package com.ngocdevv.nativecook.blocks.navigation.periodstatsswitcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** A day or a month to remember: its figure and when it was. */
data class Mark(val value: Double, val whenLabel: String, val caption: String)

/** A label under the chart, and the slot it stands under. */
data class Tick(val at: Int, val text: String, val strong: Boolean = false)

/**
 * Everything the screen shows for one period.
 *
 * @param name The switch's word for it.
 * @param labels What the row under the switch calls this period, then the ones before it.
 * @param scale The chart's top value.
 * @param bars Bar heights as fractions of the chart, one to a slot; 0 leaves the slot empty.
 * @param missed Slots whose goal was not met, drawn grey.
 * @param change This period against the last one.
 * @param percent The same as a percentage.
 */
data class Stats(
    val name: String,
    val labels: List<String>,
    val total: Double,
    val distance: Double,
    val floors: Double,
    val calories: Double,
    val scale: Double,
    val bars: List<Float>,
    val missed: List<Int> = emptyList(),
    val axis: List<Tick>,
    val average: Double,
    val averageCaption: String,
    val change: Double,
    val percent: Int,
    val changeCaption: String,
    val best: Mark? = null,
    val worst: Mark? = null,
)

val SamplePeriods = listOf(
    Stats(
        name = "Week", labels = listOf("Today, 28 Aug", "19 – 25 Aug", "12 – 18 Aug", "5 – 11 Aug"),
        total = 5052.0, distance = 3.4, floors = 19.0, calories = 278.0, scale = 5984.0,
        bars = listOf(0.85f, 0.75f, 0.73f, 0f, 0f, 0f, 0f),
        axis = listOf("26\nMon", "27\nTue", "28\nWed", "29\nThu", "30\nFri", "31\nSat", "1\nSun").mapIndexed { at, text -> Tick(at, text, strong = at == 2) },
        average = 5408.0, averageCaption = "Daily avg.", change = -391.0, percent = -7, changeCaption = "vs last week avg.",
    ),
    Stats(
        name = "Month", labels = listOf("Aug 2024", "Jul 2024", "Jun 2024", "May 2024"),
        total = 162_779.0, distance = 110.5, floors = 332.0, calories = 3262.0, scale = 9525.0,
        bars = listOf(
            0.52f, 0.68f, 0.61f, 0.8f, 0.47f, 0.74f, 0.9f, 0.63f, 0.95f, 0.3f, 0.58f, 0.86f, 0.71f, 0.66f, 0.92f, 0.55f, 0.78f, 0.84f, 0.6f, 0.49f,
            0.73f, 0.88f, 0.64f, 0.57f, 0.7f, 0.81f, 0.62f, 0.76f, 0f, 0f,
        ),
        missed = listOf(8, 9),
        axis = listOf(0, 4, 8, 12, 16, 20, 24, 28).map { Tick(it, "${it + 1}") },
        average = 5813.0, averageCaption = "Daily avg.", change = -583.0, percent = -10, changeCaption = "vs last month avg.",
        best = Mark(9525.0, "9 Aug", "Best day"), worst = Mark(1441.0, "10 Aug", "Worst day"),
    ),
    Stats(
        name = "Year", labels = listOf("2024", "2023", "2022", "2021"),
        total = 1_172_691.0, distance = 811.3, floors = 2382.0, calories = 13_075.0, scale = 198_312.0,
        bars = listOf(0.6f, 0.64f, 0.66f, 0.74f, 0.8f, 0.86f, 0.95f, 0.7f, 0f, 0f, 0f, 0f),
        axis = listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov").mapIndexed { index, text -> Tick(index * 2, text) },
        average = 146_586.0, averageCaption = "Monthly avg.", change = 17_766.0, percent = 12, changeCaption = "vs last year avg.",
        best = Mark(198_312.0, "Jul", "Best month"), worst = Mark(110_127.0, "Jan", "Worst month"),
    ),
)

// Earlier periods are made from the given one: every figure times one of these, the bars turned round.
private val Earlier = listOf(1.0, 0.86, 1.12, 0.93)
private const val TURN = 3

private val Page = Color(0xFFF1F0F7)
private val Navy = Color(0xFF212557)
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF8D8CA0)
private val Faint = Color(0xFFC8C7D4)
private val Track = Color(0xFFE4E3EC)
private val SegmentInk = Color(0xFF55546A)
private val Green = Color(0xFF26CE47)
private val Panel = Color(0xFFDFEDE8)
private val PanelInk = Color(0xFF7FA08F)
private val Missed = Color(0xFFC3D2CB)
private val Red = Color(0xFFE5484D)
private val Gold = Color(0xFFF2B022)
private val Strip = Color(0xFFE8E7EF)

private const val MARGIN = 24f

// From the safe area's top.
private const val SWITCH_TOP = 20f
private const val SWITCH = 30f
private const val SWITCH_LEFT = 60f
private const val SWITCH_INSET = 2f
private const val PERIOD_TOP = 67f
private const val DISC_TOP = 116f
private const val DISC = 226f
private const val RING = 12f
private const val FIGURE_TOP = 100f
private const val FIGURE = 46f
private const val FIGURE_LINE = 52f

// Everything from the totals down: where it starts under the disc, and how far it rises without it.
private const val BODY_TOP = 360f
private const val BODY_RISE = 170f

// Inside that body.
private const val CHART_TOP = 62f
private const val CHART = 160f
private const val BAR_WIDEST = 30f
private const val BAR_SHARE = 0.62f
private const val AXIS_TOP = CHART_TOP + CHART + 8f
private const val LEGEND_TOP = CHART_TOP + CHART + 44f
private const val CARDS_TOP = LEGEND_TOP + 48f
private const val CARD = 92f
private const val CARD_TALL = 106f
private const val CARD_GAP = 12f

// The switch's capsule and the layout follow the period on a quick spring (mass 1, stiffness 420,
// damping 34) and a 350 ms ease.
private const val CAPSULE_STIFFNESS = 420f
private const val CAPSULE_DAMPING_RATIO = 0.83f
private const val SHIFT_FOR = 350
private val ShiftEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

// The disc comes back as a ring that fills, then turns solid.
private const val RING_AFTER = 150
private const val RING_FOR = 800
private val RingEase = CubicBezierEasing(0.5f, 1f, 0.89f, 1f)
private const val SOLID_FROM = 0.85f

// A digit column turns to its new digit; columns start one after another, left to right.
private const val ROLL_FOR = 650
private const val ROLL_STEP = 40

// The chart's clock, in seconds: the old bars sink, then the new ones grow one after another on a
// spring that passes their height by about 6%.
private const val SINK = 0.15f
private const val BAR_STEP = 0.012f
private const val BAR_OMEGA = 20f
private const val BAR_ZETA = 0.667f
private const val BAR_REST = 0.9f
private const val CHART_FOR = SINK + 30 * BAR_STEP + BAR_REST
private const val CROSS = 0.25f
private const val SWAP = 250

// The second row of cards follows the first.
private const val ROW_AFTER = 60
private const val ROW_RISE = 10f
private const val SWIPE = 50f

/** autoPlay: Week, Month, Year and back to Week. */
const val LOOP = 10f
private val Steps = listOf(2.4f to 1, 4.8f to 2, 7.4f to 0)

/** After a touch autoPlay waits this long before it goes back to the first period and starts over. */
private const val USER_HOLD = 3f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun statsText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f, italic: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val SegmentStyle = statsText(13f, 18f, FontWeight.Medium, SegmentInk)
private val PeriodStyle = statsText(15f, 20f, FontWeight.Medium, Ink)
private val BigStyle = statsText(FIGURE, FIGURE_LINE, FontWeight.ExtraBold, Navy, spacing = -1f, italic = true)
private val DiscStyle = statsText(46f, 52f, FontWeight.Bold, Navy, spacing = -0.5f)
private val TotalStyle = statsText(24f, 28f, FontWeight.Bold, Navy)
private val UnitStyle = statsText(11f, 14f, FontWeight.Bold, Navy)
private val CaptionStyle = statsText(11f, 14f, FontWeight.Normal, Mute)
private val NoteStyle = statsText(13f, 18f, FontWeight.Normal, Mute)
private val DiscNoteStyle = statsText(12f, 16f, FontWeight.Normal, Mute)
private val ScaleStyle = statsText(10f, 12f, FontWeight.Normal, PanelInk)
private val AxisStyle = statsText(11f, 13f, FontWeight.Normal, Mute).copy(textAlign = TextAlign.Center)
private val CardStyle = statsText(22f, 28f, FontWeight.Bold, Navy)
private val PercentStyle = statsText(11f, 14f, FontWeight.Bold, Red)
private val WhenStyle = statsText(11f, 14f, FontWeight.SemiBold, Ink)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

/** How much of its height a bar has [t] seconds after it started to grow. */
private fun grownAt(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= BAR_REST) return 1f
    val damped = BAR_OMEGA * sqrt(1f - BAR_ZETA * BAR_ZETA)
    return 1f - exp(-BAR_ZETA * BAR_OMEGA * t) * (cos(damped * t) + BAR_ZETA * BAR_OMEGA / damped * sin(damped * t))
}

private fun grouped(value: Double): String {
    var digits = abs(value).roundToLong().toString()
    var out = ""
    while (digits.length > 3) {
        out = "," + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return digits + out
}

/** The period as it was [back] steps ago. */
private fun Stats.earlier(back: Int): Stats {
    if (back == 0) return this
    val by = Earlier[back % Earlier.size]
    val count = bars.size
    return copy(
        total = total * by,
        distance = distance * by,
        floors = floors * by,
        calories = calories * by,
        average = average * by,
        change = -change * by,
        percent = -percent,
        bars = bars.indices.map { bars[(it + back * TURN) % count] },
        missed = missed.map { (it + count - (back * TURN) % count) % count },
    )
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A statistics screen under a three-way switch. Changing the period rebuilds what is under it:
 * the goal disc gives way to a large rolling total and the page closes up, every figure rolls to
 * its new digits, the old bars sink and a new set grows from the baseline, left to right.
 *
 * @param periods The periods of the switch, shortest first; the first one shows the goal disc.
 * @param goal The first period's goal, written in its disc.
 * @param autoPlay Goes from period to period by itself every [LOOP] seconds.
 * @param onSelect Called with the period's place in [periods] when the switch moves.
 */
@Composable
fun PeriodStatsSwitcher(
    periods: List<Stats> = SamplePeriods,
    goal: Double = 3000.0,
    unit: String = "steps",
    totalCaption: String = "Total Walk",
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val given by rememberUpdatedState(periods)
    val chosen by rememberUpdatedState(onSelect)

    var period by remember { mutableIntStateOf(0) }
    // How many periods back the arrows have gone.
    var back by remember { mutableIntStateOf(0) }
    // The bars that are leaving the chart, and how often the chart's clock has started.
    var left by remember { mutableStateOf<Stats?>(null) }
    var switches by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The chart's clock in seconds, read while drawing only.
    val clock = remember { mutableFloatStateOf(CHART_FOR) }
    // The disc's ring: 0 is empty, 1 full and solid.
    val ring = remember { Animatable(1f) }

    val first = period == 0
    val stats = periods[period].earlier(back)
    val week = periods[0].earlier(if (first) back else 0)
    // 1 while the first period, with its disc, is shown.
    val disc = animateFloatAsState(if (first) 1f else 0f, tween(SHIFT_FOR, easing = ShiftEase), label = "disc")
    val capsule = animateFloatAsState(period.toFloat(), spring(CAPSULE_DAMPING_RATIO, CAPSULE_STIFFNESS), label = "capsule")

    // The page is light: the status bar's icons are dark while it is on the screen.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Shows a period, [steps] back: the figures roll by themselves, the chart's clock starts over. */
    fun show(index: Int, steps: Int) {
        if (index == period && steps == back) return
        val changed = index != period
        left = given[period].earlier(back)
        period = index
        back = steps
        clock.floatValue = 0f
        switches++
        if (!changed) return
        if (index == 0 && !reducedMotion) {
            scope.launch {
                ring.snapTo(0f)
                ring.animateTo(1f, tween(RING_FOR, delayMillis = RING_AFTER, easing = RingEase))
            }
        }
        chosen?.invoke(index)
    }

    fun select(index: Int) {
        if (index == period) return
        tick()
        show(index, 0)
    }

    fun step(by: Int) {
        val next = back + by
        if (next < 0 || next >= given[period].labels.size) return
        tick()
        show(period, next)
    }

    LaunchedEffect(switches) {
        if (switches == 0) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (clock.floatValue < CHART_FOR) clock.floatValue = (withFrameNanos { it } - start) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The loop starts over from the first period, once it has settled there.
            show(0, 0)
            delay((RING_AFTER + RING_FOR) * 2L)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            Steps.forEach { (at, to) ->
                until(at)
                show(to, 0)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .clipToBounds()
            // A touch takes over from the autoPlay loop, which starts again from rest a while later.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                }
            }
            .pointerInput(Unit) {
                var across = 0f
                detectHorizontalDragGestures(
                    onDragStart = { across = 0f },
                    onDragEnd = {
                        val next = period + if (across < 0f) 1 else -1
                        if (abs(across) > SWIPE.dp.toPx() && next in given.indices) select(next)
                    },
                ) { _, amount -> across += amount }
            },
    ) {
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value
        val width = maxWidth.value
        val slot = (width - MARGIN * 2f) / stats.bars.size
        val cardWidth = (width - MARGIN * 2f - CARD_GAP) / 2f
        val up = stats.change >= 0
        val rise = with(LocalDensity.current) { ROW_RISE.dp.roundToPx() }

        // Everything from the totals down rides up when the disc goes.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = (top + BODY_TOP - BODY_RISE * (1f - disc.value)).dp.toPx() }) {
            Row(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth()) {
                Total(String.format(Locale.US, "%.1f", stats.distance), "KM", "Total distance")
                Total(grouped(stats.floors), "", "Total floors")
                Total(grouped(stats.calories), "kcal", "Total calories")
            }

            // The chart: one clock for the set that sinks and the set that grows.
            Box(
                Modifier
                    .offset(y = CHART_TOP.dp)
                    .fillMaxWidth()
                    .height(CHART.dp)
                    .background(Panel)
                    .drawBehind {
                        left?.let { bars(it, leaving = true, clock.floatValue, reducedMotion) }
                        bars(stats, leaving = false, clock.floatValue, reducedMotion)
                    }
                    .semantics { contentDescription = "${stats.name} chart" },
            ) {
                Crossfade(grouped(stats.scale), animationSpec = tween(SWAP), modifier = Modifier.padding(start = MARGIN.dp, top = 6.dp), label = "scale") {
                    BasicText(it, style = ScaleStyle)
                }
            }
            Crossfade(period, animationSpec = tween(SWAP), modifier = Modifier.offset(y = AXIS_TOP.dp).fillMaxWidth().height(28.dp), label = "axis") { shown ->
                val set = if (shown == period) stats else periods[shown]
                val each = (width - MARGIN * 2f) / set.bars.size
                Box(Modifier.fillMaxSize()) {
                    set.axis.forEach { label ->
                        BasicText(
                            label.text,
                            style = if (label.strong) AxisStyle.copy(color = Ink, fontWeight = FontWeight.Bold) else AxisStyle,
                            modifier = Modifier.offset(x = (MARGIN + each * label.at + each / 2f - 30f).dp).width(60.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = MARGIN.dp).offset(y = LEGEND_TOP.dp).fillMaxWidth().height(24.dp).background(Strip, RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp, 1.5.dp).background(Mute))
                Spacer(Modifier.width(5.dp))
                BasicText("Goal line", style = CaptionStyle)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(7.dp).background(Green, RoundedCornerShape(1.5.dp)))
                Spacer(Modifier.width(5.dp))
                BasicText("Goal meet", style = CaptionStyle)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(7.dp).background(Missed, RoundedCornerShape(1.5.dp)))
                Spacer(Modifier.width(5.dp))
                BasicText("Goal not meet", style = CaptionStyle)
            }

            Row(Modifier.padding(horizontal = MARGIN.dp).offset(y = CARDS_TOP.dp), horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp)) {
                StatCard(cardWidth, CARD) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
                    Roll(grouped(stats.average), CardStyle, 28f)
                    BasicText(stats.averageCaption, style = CaptionStyle, maxLines = 1)
                }
                Box {
                    StatCard(cardWidth, CARD) {
                        Box(Modifier.size(22.dp).background(if (up) Green else Red, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (up) Icons.Filled.NorthEast else Icons.Filled.SouthWest, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Row {
                            val signed = CardStyle.copy(color = if (up) Green else Red)
                            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { BasicText(if (up) "+" else "-", style = signed) }
                            Roll(grouped(stats.change), signed, 28f)
                        }
                        BasicText(stats.changeCaption, style = CaptionStyle, maxLines = 1)
                    }
                    BasicText(
                        "${if (up) "+" else "-"}${abs(stats.percent)}%",
                        style = PercentStyle.copy(color = if (up) Green else Red),
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    )
                }
            }

            // The second row belongs to the longer periods; it follows the first in and out.
            AnimatedContent(
                period,
                transitionSpec = {
                    (fadeIn(tween(SWAP, delayMillis = ROW_AFTER)) + slideInVertically(tween(SWAP, delayMillis = ROW_AFTER)) { rise }) togetherWith
                        fadeOut(tween((SINK * 1000).toInt(), easing = LinearEasing))
                },
                modifier = Modifier.padding(horizontal = MARGIN.dp).offset(y = (CARDS_TOP + CARD + CARD_GAP).dp),
                label = "marks",
            ) { shown ->
                val set = if (shown == period) stats else periods[shown]
                Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp)) {
                    listOfNotNull(set.best, set.worst).takeIf { it.size == 2 }?.forEachIndexed { index, mark ->
                        StatCard(cardWidth, CARD_TALL) {
                            Icon(
                                if (index == 0) Icons.Filled.EmojiEvents else Icons.Filled.SentimentDissatisfied,
                                contentDescription = null,
                                tint = if (index == 0) Gold else Mute,
                                modifier = Modifier.size(22.dp),
                            )
                            Roll(grouped(mark.value), CardStyle, 28f)
                            BasicText(mark.whenLabel, style = WhenStyle, maxLines = 1)
                            BasicText(mark.caption, style = CaptionStyle, maxLines = 1)
                        }
                    }
                }
            }
        }

        // The headline: a large total for the longer periods, a goal disc for the first.
        Column(Modifier.offset(MARGIN.dp, (top + FIGURE_TOP).dp).graphicsLayer { alpha = clamp01(1f - disc.value * 2f) }) {
            Roll(grouped(if (first) week.total else stats.total), BigStyle, FIGURE_LINE)
            BasicText(totalCaption, style = NoteStyle, modifier = Modifier.padding(top = 4.dp))
        }

        // Away from the first period the disc holds zeros, so that its figure counts up when it returns.
        val figure = grouped(week.total).let { text -> if (first) text else text.map { if (it.isDigit()) '0' else it }.joinToString("") }
        val caption = "of ${grouped(goal)} $unit"
        Box(
            Modifier
                .offset(((width - DISC) / 2f).dp, (top + DISC_TOP).dp)
                .size(DISC.dp)
                .graphicsLayer {
                    val shown = disc.value
                    alpha = shown * shown
                    translationX = -(1f - shown) * (width / 2f - MARGIN - DISC * 0.1f).dp.toPx()
                    scaleX = mix(0.2f, 1f, shown)
                    scaleY = scaleX
                }
                .drawBehind {
                    val stroke = RING.dp.toPx()
                    drawCircle(Color.White)
                    drawArc(
                        Green, -90f, 360f * clamp01(ring.value), useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            DiscFace(figure, caption, Navy, Mute)
            // The solid disc, with its ring of dots, comes over the last part of the ring's fill.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = clamp01((ring.value - SOLID_FROM) / (1f - SOLID_FROM)) }
                    .drawBehind {
                        drawCircle(Green)
                        val gap = (PI * (DISC - 32f) / 48f).toFloat().dp.toPx()
                        drawCircle(
                            Color.White,
                            radius = size.width / 2f - 16.dp.toPx(),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, gap - 0.01f))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                DiscFace(figure, caption, Color.White, Color.White.copy(alpha = 0.8f))
            }
        }

        // The switch.
        Row(Modifier.offset(y = (top + SWITCH_TOP).dp).padding(end = MARGIN.dp).fillMaxWidth().height(SWITCH.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(SWITCH_LEFT.dp, SWITCH.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClose?.invoke() }
                    .semantics { contentDescription = "Close" }
                    .padding(start = (MARGIN - 3f).dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Track, RoundedCornerShape(8.dp)).padding(SWITCH_INSET.dp)) {
                val segment = maxWidth / periods.size
                Box(
                    Modifier
                        .graphicsLayer { translationX = capsule.value * segment.toPx() }
                        .size(segment, maxHeight)
                        .shadow(1.5.dp, RoundedCornerShape(7.dp))
                        .background(Color.White, RoundedCornerShape(7.dp)),
                )
                Row(Modifier.fillMaxSize()) {
                    periods.forEachIndexed { index, item ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { select(index) }
                                .semantics { selected = index == period },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(item.name, style = if (index == period) SegmentStyle.copy(color = Ink) else SegmentStyle)
                        }
                    }
                }
            }
        }

        // The period, with the arrows to the ones before it.
        Row(
            Modifier.offset(y = (top + PERIOD_TOP).dp).padding(horizontal = (MARGIN - 7f).dp).fillMaxWidth().height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Arrow(Icons.Filled.ChevronLeft, "Earlier", enabled = back < stats.labels.size - 1) { step(1) }
            Crossfade(stats.labels[back], animationSpec = tween(SWAP), modifier = Modifier.weight(1f), label = "period") {
                BasicText(it, style = PeriodStyle.copy(textAlign = TextAlign.Center), maxLines = 1, modifier = Modifier.fillMaxWidth())
            }
            Arrow(Icons.Filled.ChevronRight, "Later", enabled = back > 0) { step(-1) }
        }
    }
}

/** Draws one set of bars [t] seconds into the chart's clock. */
private fun DrawScope.bars(set: Stats, leaving: Boolean, t: Float, still: Boolean) {
    val margin = MARGIN.dp.toPx()
    val slot = (size.width - margin * 2f) / set.bars.size
    val wide = min(BAR_WIDEST.dp.toPx(), slot * BAR_SHARE)
    val round = CornerRadius(2.dp.toPx())
    set.bars.forEachIndexed { index, share ->
        if (share <= 0f) return@forEachIndexed
        // Reduced motion: the two sets cross-fade at full height.
        val fade = clamp01(t / CROSS)
        val grown = if (still) 1f else if (leaving) 1f - clamp01(t / SINK) else grownAt(t - SINK - index * BAR_STEP)
        val height = size.height * share * grown
        if (height <= 0f) return@forEachIndexed
        val left = margin + slot * index + (slot - wide) / 2f
        val path = Path().apply {
            addRoundRect(
                RoundRect(left, size.height - height, left + wide, size.height, topLeftCornerRadius = round, topRightCornerRadius = round),
            )
        }
        drawPath(path, if (index in set.missed) Missed else Green, alpha = if (still) (if (leaving) 1f - fade else fade) else 1f)
    }
}

/** A figure whose digits roll to a new value, the leftmost first. [line] is the height of its one line, in dp. */
@Composable
private fun Roll(text: String, style: TextStyle, line: Float) {
    Row(Modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { index, character ->
            // A column keeps its place from the right, so that a longer figure grows at the left.
            key(text.length - index, character.isDigit()) {
                if (character.isDigit()) {
                    val at = animateFloatAsState(
                        character.digitToInt().toFloat(),
                        tween(ROLL_FOR, delayMillis = index * ROLL_STEP, easing = EaseOutStrong),
                        label = "digit",
                    )
                    // A strip of the ten digits behind a one-line window, which is open at its sides so that an italic is not cut.
                    Box(
                        Modifier.height(line.dp).drawWithContent {
                            clipRect(left = -size.height, right = size.width + size.height) { this@drawWithContent.drawContent() }
                        },
                    ) {
                        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { translationY = -at.value * line.dp.toPx() }) {
                            repeat(10) { digit ->
                                Box(Modifier.height(line.dp), contentAlignment = Alignment.Center) { BasicText("$digit", style = style) }
                            }
                        }
                    }
                } else {
                    Box(Modifier.height(line.dp), contentAlignment = Alignment.Center) { BasicText(character.toString(), style = style) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Total(figure: String, unit: String, caption: String) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Roll(figure, TotalStyle, 28f)
            BasicText(unit, style = UnitStyle, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
        BasicText(caption, style = CaptionStyle, maxLines = 1)
    }
}

@Composable
private fun StatCard(width: Float, height: Float, content: @Composable () -> Unit) {
    Column(Modifier.size(width.dp, height.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
        content()
    }
}

@Composable
private fun DiscFace(figure: String, caption: String, ink: Color, note: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = ink, modifier = Modifier.size(30.dp))
        Roll(figure, DiscStyle.copy(color = ink), 52f)
        BasicText(caption, style = DiscNoteStyle.copy(color = note), maxLines = 1)
    }
}

@Composable
private fun Arrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Ink else Faint, modifier = Modifier.size(22.dp))
    }
}
