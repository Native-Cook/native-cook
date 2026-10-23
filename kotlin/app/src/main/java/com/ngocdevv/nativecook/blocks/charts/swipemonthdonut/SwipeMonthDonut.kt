package com.ngocdevv.nativecook.blocks.charts.swipemonthdonut

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val PageColor = Color(0xFFF2F2F7)
private val CardColor = Color(0xFFFFFFFF)
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF8E8E93)
private val Faint = Color(0xFFC7C7CC)
private val Rule = Color(0xFFE5E5EA)
private val Track = Color(0xFFE3E3E8)
private val Badge = Color(0xFFEFEFF4)

private const val MARGIN = 20f
private const val RING = 300f
private const val STROKE = 22f
private const val GAP = 4f
private const val POP = 6f
private const val BAR = 44f
private const val SEGMENTED_W = 172f
private const val SEGMENTED_H = 30f
private const val ROW = 44f
private const val DOT = 7f
private const val DOT_ACTIVE = 7f
private const val PLUS = 56f

// A swipe across this share of the ring turns one page; a fast flick turns one at most.
private const val DRAG_SHARE = 0.8f
private const val FLICK = 0.12f

// The pan starts after 10 dp sideways and fails after 14 dp up or down; a tap allows 10 dp.
private const val PAN_START = 10f
private const val PAN_FAIL = 14f
private const val TAP_SLOP = 10f
private const val PAGE_DAMPING = 24f
private const val PAGE_STIFFNESS = 200f
private const val POP_DAMPING = 13f
private const val POP_STIFFNESS = 260f
private const val POP_IN = 0.35f
private const val POP_OUT = 0.25f
private const val ROW_IN = 260
private const val ROW_OUT = 180
private const val ROW_STAGGER = 40L
private const val ROW_SHIFT = 12f
private const val CARD_RESIZE = 300
private const val THUMB_SLIDE = 220
private const val SETTLE_FOR = 300
private const val LIFT = 150
private const val ACCESSIBLE_TURN = 400

private val Smooth = Easing { easeInOut(it) }
private val OutCubic = Easing { 1 - (1 - it) * (1 - it) * (1 - it) }

// Reanimated's default timing curve.
private val Quad = Easing { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }

// Reanimated's springs (mass 1) in Compose's terms.
private val PageSpring = spring(PAGE_DAMPING / (2 * sqrt(PAGE_STIFFNESS)), PAGE_STIFFNESS, 0.0001f)
private val PopSpring = spring(POP_DAMPING / (2 * sqrt(POP_STIFFNESS)), POP_STIFFNESS, 0.0001f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun donutText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val KindStyle = donutText(13f, 18f, Ink, FontWeight.Medium)
private val MonthStyle = donutText(15f, 20f, Mute)
private val AmountStyle = donutText(44f, 52f, Ink, FontWeight.Bold, -0.8f)
private val ChangeStyle = donutText(15f, 20f, Mute)
private val IconStyle = donutText(19f, 24f, Ink)
private val RowLabelStyle = donutText(16f, 21f, Ink)
private val CountStyle = donutText(12f, 16f, Mute)
private val RowAmountStyle = donutText(16f, 21f, Mute)

private fun money(value: Float, currency: String) = currency + String.format(Locale.US, "%,d", value.roundToInt())

/** The page the pager is nearest, rounding halves up as JavaScript does. */
private fun nearest(page: Float) = floor(page + 0.5f)

/** One row of the list, with where it is and how far in it has come; a leaving row stays until it has gone. */
@Stable
private class ListRow(val index: Int, y: Float, alpha: Float, shift: Float) {
    val y = Animatable(y)
    val alpha = Animatable(alpha)
    val shift = Animatable(shift)
    var order by mutableIntStateOf(0)
    var amount by mutableStateOf(0f)
    var count by mutableIntStateOf(0)
    var leaving by mutableStateOf(false)
}

/**
 * A month of spending as a ring of round-capped segments, paged with a swipe. Between two months
 * every segment's start and length follow the finger, so categories shrink into their neighbours
 * or grow out of them; the total slides with the page and the list below re-flows. Tapping a
 * segment pops it out and shows its amount in the centre.
 *
 * @param categories The ring's categories, in its clockwise order.
 * @param months One page each; amounts follow the order of [categories].
 * @param autoPlay Swipes through the months by itself and taps one segment, every [LOOP] seconds.
 */
@Composable
fun SwipeMonthDonut(
    categories: List<Category> = Categories,
    months: List<Month> = Months,
    currency: String = "$",
    kinds: Pair<String, String> = "Expense" to "Income",
    autoPlay: Boolean = false,
    onMonthChange: ((Month, Int) -> Unit)? = null,
    onCategory: ((Category?) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    val still by rememberUpdatedState(rememberReducedMotion())
    val monthsNow by rememberUpdatedState(months)
    val categoriesNow by rememberUpdatedState(categories)
    val monthChanged by rememberUpdatedState(onMonthChange)
    val categoryPicked by rememberUpdatedState(onCategory)
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    val windowWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    val size = min(RING, windowWidth - MARGIN * 2) + POP * 2
    val radius = size / 2 - POP - STROKE / 2 - 1
    val inner = (radius - STROKE / 2) * 2 - 8
    val table = remember(months) { shares(months) }
    val tableNow by rememberUpdatedState(table)
    val last = months.size - 1

    var month by remember { mutableIntStateOf(0) }
    var focused by remember { mutableIntStateOf(-1) }
    var kind by remember { mutableIntStateOf(0) }
    var focus by remember { mutableIntStateOf(-1) }
    var auto by remember { mutableStateOf(autoPlay) }
    var touches by remember { mutableIntStateOf(0) }

    val page = remember { Animatable(0f, 0.0001f) }
    val out = remember { Animatable(0f, 0.0001f) }
    val thumb = remember { Animatable(0f) }

    // The shares between the two pages the pager is between.
    fun sharesAt(value: Float): List<Float> {
        val lastPage = tableNow.size - 1
        val p = value.coerceIn(0f, lastPage.toFloat())
        val from = min(lastPage, floor(p).toInt())
        val to = min(lastPage, from + 1)
        val u = p - from
        return tableNow[from].indices.map { tableNow[from][it] + (tableNow[to][it] - tableNow[from][it]) * u }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { nearest(page.value).coerceIn(0f, (monthsNow.size - 1).toFloat()).toInt() }.collect { next ->
            if (next != month) {
                month = next
                monthChanged?.invoke(monthsNow[next], next)
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { if (out.value > 0.01f) focus else -1 }.collect { focused = it }
    }

    // autoPlay drives the pager and the pop from the clock, frame by frame.
    LaunchedEffect(auto) {
        if (!auto) return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) {
            val time = withFrameNanos { it }
            val t = (((time - origin) / 1e9f) % LOOP + LOOP) % LOOP
            val p = if (still) nearest(pageAt(t)) else pageAt(t)
            val away = when {
                still -> if (t >= FOCUS_ON && t < FOCUS_OFF) 1f else 0f
                t < FOCUS_ON -> 0f
                t < FOCUS_OFF -> springOut((t - FOCUS_ON) / POP_IN)
                else -> 1 - clamp01((t - FOCUS_OFF) / POP_OUT)
            }
            focus = FOCUS_CATEGORY
            page.snapTo(p)
            out.snapTo(away)
        }
    }

    // A touch takes over at once; some seconds after the last one the pager glides home and plays again.
    fun touched() {
        touches += 1
    }
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay || touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        val timing = tween<Float>(if (still) 0 else (RETURN_FOR * 1000).toInt(), easing = Smooth)
        launch { page.animateTo(0f, timing) }
        launch { out.animateTo(0f, timing) }
        delay((RETURN_FOR * 1000).toLong())
        auto = true
    }

    // A tap off the ring stops autoPlay too: the pager settles on the nearest page.
    fun takeOver() {
        touched()
        if (!auto) return
        auto = false
        scope.launch { page.animateTo(nearest(page.value), tween(if (still) 0 else SETTLE_FOR, easing = Smooth)) }
        scope.launch { out.animateTo(0f, tween(if (still) 0 else (POP_OUT * 1000).toInt(), easing = Quad)) }
    }

    fun tap(position: Offset, density: Float) {
        val dx = position.x / density - size / 2
        val dy = position.y / density - size / 2
        var hit = -1
        if (abs(hypot(dx, dy) - radius) < STROKE) {
            // Clockwise from the top, in turns.
            val turn = ((atan2(dy, dx) / (Math.PI * 2).toFloat()) + 1.25f) % 1f
            var at = 0f
            sharesAt(page.value).forEachIndexed { i, share ->
                if (share > 0 && turn >= at && turn < at + share) hit = i
                at += share
            }
        }
        val showing = if (out.value > 0.5f) focus else -1
        if (hit < 0 || hit == showing) {
            scope.launch { out.animateTo(0f, tween(if (still) 0 else (POP_OUT * 1000).toInt(), easing = Quad)) }
            categoryPicked?.invoke(null)
            return
        }
        focus = hit
        scope.launch {
            out.snapTo(0f)
            if (still) out.snapTo(1f) else out.animateTo(1f, PopSpring)
        }
        hostView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        categoryPicked?.invoke(categoriesNow[hit])
    }

    DarkSystemBars()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val current = months[month]

    Box(Modifier.fillMaxSize().background(PageColor)) {
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().height(BAR.dp).padding(horizontal = MARGIN.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Glyph(22f, "More", ::takeOver) { unit ->
                    // The icon font's three dots, on a 512 grid.
                    for (x in listOf(96f, 256f, 416f)) drawCircle(Mute, 48f * unit, Offset(x * unit, 256f * unit))
                }
                Box(
                    Modifier
                        .size(SEGMENTED_W.dp, SEGMENTED_H.dp)
                        .background(Track, RoundedCornerShape(9.dp))
                        .padding(2.dp),
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset((thumb.value * (SEGMENTED_W / 2 - 2).dp.toPx()).roundToInt(), 0) }
                            .size((SEGMENTED_W / 2 - 2).dp, (SEGMENTED_H - 4).dp)
                            .shadow(1.5.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                            .background(Color.White, RoundedCornerShape(7.dp)),
                    )
                    Row(Modifier.fillMaxSize()) {
                        listOf(kinds.first, kinds.second).forEachIndexed { index, label ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .semantics { selected = kind == index }
                                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                                        takeOver()
                                        kind = index
                                        scope.launch {
                                            if (still) thumb.snapTo(index.toFloat()) else thumb.animateTo(index.toFloat(), tween(THUMB_SLIDE, easing = Smooth))
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = KindStyle) }
                        }
                    }
                }
                Glyph(24f, "History", ::takeOver) { unit ->
                    // The icon font's outlined clock, on a 512 grid.
                    val line = Stroke(32f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawCircle(Mute, 192f * unit, Offset(256f * unit, 256f * unit), style = Stroke(32f * unit))
                    val hands = Path().apply {
                        moveTo(256f * unit, 128f * unit)
                        lineTo(256f * unit, 272f * unit)
                        lineTo(352f * unit, 272f * unit)
                    }
                    drawPath(hands, Mute, style = line)
                }
            }

            Box(
                Modifier
                    .padding(top = 28.dp)
                    .size(size.dp)
                    .clearAndSetSemantics {
                        contentDescription = "${current.label}, ${money(current.total, currency)} spent"
                        customActions = listOf(
                            CustomAccessibilityAction("Next month") { turnBy(1, month, last, still, page, scope) { auto = false; touched() }; true },
                            CustomAccessibilityAction("Previous month") { turnBy(-1, month, last, still, page, scope) { auto = false; touched() }; true },
                        )
                    }
                    // Keyed on what the handler reads that is not state, so it is rebuilt when they change.
                    .pointerInput(size, last) {
                        val sizePx = size.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // A finger down stops autoPlay and the pager where they are.
                            auto = false
                            scope.launch { page.stop() }
                            val start = nearest(page.value)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var panning = false
                            var failed = false
                            var travel = 0f
                            var position = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                position = change.position
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dx = position.x - down.position.x
                                val dy = position.y - down.position.y
                                travel = max(travel, hypot(dx, dy))
                                if (!panning && !failed) {
                                    if (abs(dx) > PAN_START.dp.toPx()) {
                                        panning = true
                                        scope.launch { out.animateTo(0f, tween(if (still) 0 else LIFT, easing = Quad)) }
                                    } else if (abs(dy) > PAN_FAIL.dp.toPx()) {
                                        failed = true
                                    }
                                }
                                if (panning) {
                                    change.consume()
                                    var p = start - dx / (sizePx * DRAG_SHARE)
                                    // Past the first or last page the ring resists.
                                    if (p < 0) p /= 3
                                    if (p > last) p = last + (p - last) / 3
                                    scope.launch { page.snapTo(p) }
                                }
                                if (!event.changes.fastAny { it.pressed }) break
                            }
                            if (panning) {
                                val velocity = tracker.calculateVelocity().x
                                scope.launch {
                                    val flung = page.value - velocity / (sizePx * DRAG_SHARE) * FLICK
                                    val target = nearest(flung).coerceIn(start - 1, start + 1).coerceIn(0f, last.toFloat())
                                    if (target != start) hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    if (still) page.snapTo(target) else page.animateTo(target, PageSpring)
                                }
                            } else if (!failed && travel <= TAP_SLOP.dp.toPx()) {
                                tap(position, density)
                            }
                            touched()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                DonutRing(size, STROKE, GAP, POP, categories.map { it.color }, { sharesAt(page.value) }, { focus }, { out.value })
                Box(Modifier.size(inner.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1 - clamp01(out.value) }) {
                        months.forEachIndexed { index, m ->
                            MonthPage(m, currency, Modifier.graphicsLayer {
                                val away = index - page.value
                                // Reduced motion: pages cross-fade in place instead of sliding.
                                if (still) {
                                    alpha = clamp01(1 - abs(away) * 2)
                                } else {
                                    alpha = clamp01(1.4f - abs(away))
                                    translationX = away * inner.dp.toPx()
                                }
                            })
                        }
                    }
                    Column(
                        Modifier.graphicsLayer {
                            val away = clamp01(out.value)
                            alpha = away
                            scaleX = 0.94f + 0.06f * away
                            scaleY = scaleX
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (focused >= 0) {
                            val amount = current.amounts[focused]
                            val sum = current.total
                            Text("${categories[focused].icon} ${categories[focused].name}", style = MonthStyle)
                            Text(money(amount, currency), style = AmountStyle, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
                            Text("${if (sum > 0) (amount / sum * 100).roundToInt() else 0}% of the month", style = ChangeStyle)
                        }
                    }
                }
            }

            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(DOT.dp)
                            .graphicsLayer {
                                val near = clamp01(1 - abs(index - page.value))
                                alpha = 0.3f + 0.7f * near
                                scaleX = (DOT + (DOT_ACTIVE - DOT) * near) / DOT
                                scaleY = scaleX
                            }
                            .background(Ink, CircleShape),
                    )
                }
            }

            MonthList(months[month], month, categories, currency, still, Modifier.padding(top = 24.dp, start = MARGIN.dp, end = MARGIN.dp))
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (max(insets.calculateBottomPadding().value, 16f) + 12).dp)
                .size(PLUS.dp)
                .clip(CircleShape)
                .background(Ink)
                .clickable(role = Role.Button, onClickLabel = "Add expense") {
                    takeOver()
                    onAdd?.invoke()
                }
                .semantics { contentDescription = "Add expense" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * The screen is light, so the bar icons are switched to dark while the block is open (the
 * gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun DarkSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}

private fun turnBy(step: Int, month: Int, last: Int, still: Boolean, page: Animatable<Float, *>, scope: CoroutineScope, stop: () -> Unit) {
    val next = (month + step).coerceIn(0, last)
    stop()
    scope.launch { page.animateTo(next.toFloat(), tween(if (still) 0 else ACCESSIBLE_TURN, easing = Smooth)) }
}

/** A bare icon drawn from the icon font's paths, which have no Material twin. */
@Composable
private fun Glyph(size: Float, label: String, onClick: () -> Unit, draw: DrawScope.(unit: Float) -> Unit) {
    Canvas(
        Modifier
            .size(size.dp)
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { draw(this.size.width / 512f) }
}

/** The centre of one page: the month, its total, and the change from the month before. */
@Composable
private fun MonthPage(month: Month, currency: String, modifier: Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(month.label, style = MonthStyle)
        Text(money(month.total, currency), style = AmountStyle, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(if (month.change.up) Icons.Filled.ArrowCircleUp else Icons.Filled.ArrowCircleDown, null, tint = Faint, modifier = Modifier.size(17.dp))
            Text("${money(month.change.amount, currency)} (${month.change.percent}%)", style = ChangeStyle)
        }
    }
}

/**
 * The month's categories, largest first. When the month changes the rows that stay slide to their
 * new places, new rows fall in 12 dp with a stagger, and leaving ones drop 12 dp and fade.
 */
@Composable
private fun MonthList(month: Month, key: Int, categories: List<Category>, currency: String, still: Boolean, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<ListRow>() }
    val height = remember { Animatable(0f) }

    LaunchedEffect(key, month) {
        val target = categories.indices
            .filter { month.amounts[it] > 0 }
            .sortedByDescending { month.amounts[it] }
        val first = rows.isEmpty()
        val layoutSpec = tween<Float>(if (still || first) 0 else CARD_RESIZE, easing = Quad)
        scope.launch { height.animateTo(target.size * ROW, layoutSpec) }
        for (row in rows.toList()) {
            if (row.leaving || row.index in target) continue
            row.leaving = true
            if (still || first) {
                rows.remove(row)
                continue
            }
            scope.launch {
                launch { row.alpha.animateTo(0f, tween(ROW_OUT, easing = Quad)) }
                row.shift.animateTo(ROW_SHIFT, tween(ROW_OUT, easing = OutCubic))
                rows.remove(row)
            }
        }
        target.forEachIndexed { order, index ->
            val y = order * ROW
            val existing = rows.firstOrNull { it.index == index && !it.leaving }
            val row = existing ?: ListRow(index, y, if (still || first) 1f else 0f, if (still || first) 0f else -ROW_SHIFT).also { rows += it }
            row.order = order
            row.amount = month.amounts[index]
            row.count = month.counts[index]
            if (existing != null) {
                scope.launch { row.y.animateTo(y, layoutSpec) }
            } else if (!still && !first) {
                scope.launch {
                    delay(order * ROW_STAGGER)
                    launch { row.alpha.animateTo(1f, tween(ROW_IN, easing = Quad)) }
                    row.shift.animateTo(0f, tween(ROW_IN, easing = OutCubic))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .layout { measurable, constraints ->
                val h = (height.value * density).roundToInt()
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .padding(horizontal = 14.dp),
    ) {
        for (row in rows) {
            key(row) {
                ListRowView(row, categories[row.index], currency)
            }
        }
    }
}

@Composable
private fun ListRowView(row: ListRow, category: Category, currency: String) {
    Row(
        Modifier
            .offset { IntOffset(0, (row.y.value * density).roundToInt()) }
            .graphicsLayer {
                alpha = row.alpha.value
                translationY = row.shift.value * density
            }
            .fillMaxWidth()
            .height(ROW.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 14.dp).size(10.dp).background(category.color, RoundedCornerShape(3.dp)))
        Text(category.icon, style = IconStyle, modifier = Modifier.padding(end = 10.dp).width(26.dp))
        Row(
            Modifier
                .weight(1f)
                .height(ROW.dp)
                .drawRule(row.order > 0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category.name, style = RowLabelStyle, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f, fill = false))
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .defaultMinSize(20.dp, 20.dp)
                    .height(20.dp)
                    .background(Badge, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${row.count}", style = CountStyle) }
            Spacer(Modifier.weight(1f))
            Text(money(row.amount, currency), style = RowAmountStyle, modifier = Modifier.padding(end = 6.dp))
            Canvas(Modifier.size(16.dp)) {
                // The icon font's forward chevron, on a 512 grid.
                val unit = this.size.width / 512f
                val chevron = Path().apply {
                    moveTo(184f * unit, 112f * unit)
                    lineTo(328f * unit, 256f * unit)
                    lineTo(184f * unit, 400f * unit)
                }
                drawPath(chevron, Faint, style = Stroke(48f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A hairline across the top, between a row and the one above it. */
private fun Modifier.drawRule(on: Boolean) = if (!on) this else drawBehind {
    drawLine(Rule, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
}
