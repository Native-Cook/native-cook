package com.ngocdevv.nativecook.blocks.pickers.datechipcalendar

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

// The reusable piece: a date chip that grows into an inline month grid and folds back.
// The range is one shape per month; the page around it only reads `open` to push its content.

internal val INK = Color(0xFF16262E)
internal val MUTE = Color(0xFF6E7F86)
internal val CARD = Color(0xFFFDFCF8)
internal val TIDE = Color(0xFF1C6B7A)
internal val TIDE_SOFT = Color(0xFFCFE4E6)
private val FAINT = rgba(22, 38, 46, 0.26f)
private val FILL = Color(0xFFEDF1EF)

// Layout, in dp.
internal const val CHIP_HEIGHT = 68f
internal const val GRID_PAD = 12f
internal const val TITLE_ROW = 40f
internal const val WEEK_ROW = 22f
internal const val DAY_ROW = 40f
internal const val GRID_ROWS = 6
internal const val GRID_BOTTOM = 12f
internal const val OPEN_HEIGHT = CHIP_HEIGHT + TITLE_ROW + WEEK_ROW + DAY_ROW * GRID_ROWS + GRID_BOTTOM
internal const val GRID_TOP = TITLE_ROW + WEEK_ROW
private const val BAND_INSET = 2f
private const val CAP = 36f
internal const val ROLL_LINE = 22f
private const val RADIUS_CLOSED = 20f
private const val RADIUS_OPEN = 24f
private const val PAN_SLOP = 8f

data class DateRange(val start: Int, val end: Int)

internal fun dateChipStyle(size: Float, weight: FontWeight, color: Color, line: Float = 0f, tabular: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (line > 0f) line.sp else TextUnit.Unspecified,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val CAPTION_STYLE = dateChipStyle(13f, FontWeight.Medium, MUTE)
private val ROLL_MUTED_STYLE = dateChipStyle(17f, FontWeight.SemiBold, MUTE)
private val ROLL_VALUE_STYLE = dateChipStyle(17f, FontWeight.SemiBold, INK, tabular = true)
private val DONE_STYLE = dateChipStyle(15f, FontWeight.SemiBold, CARD)
private val MONTH_STYLE = dateChipStyle(17f, FontWeight.Bold, INK)
private val WEEK_STYLE = dateChipStyle(12f, FontWeight.SemiBold, MUTE).copy(textAlign = TextAlign.Center)
private val DAY_STYLE = dateChipStyle(16f, FontWeight.Medium, INK, tabular = true)
private val DAY_MUTED_STYLE = DAY_STYLE.copy(color = FAINT)
private val DAY_BLOCKED_STYLE = DAY_STYLE.copy(color = FAINT, textDecoration = TextDecoration.LineThrough)
private val CAP_STYLE = dateChipStyle(16f, FontWeight.Bold, CARD, tabular = true)

private fun isBlocked(day: Int, blocked: IntArray): Boolean {
    for (b in blocked) if (b == day) return true
    return false
}

private fun blockedBetween(a: Int, b: Int, blocked: IntArray): Boolean {
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    for (d in blocked) if (d in lo..hi) return true
    return false
}

/** The furthest day from `anchor` towards `day` that a stay can reach without crossing a blocked night. */
private fun reach(anchor: Int, day: Int, blocked: IntArray): Int {
    if (anchor == day) return anchor
    val step = if (day > anchor) 1 else -1
    var out = anchor
    while (out != day) {
        val next = out + step
        if (isBlocked(next, blocked)) break
        out = next
    }
    return out
}

/** A cap's place in the grid (top-left, dp); a head between two weeks stays on the last column, then jumps to the next row. */
internal fun capPosition(day: Float, first: Int, offset: Int, cell: Float, out: FloatArray) {
    val index = day - first + offset
    var row = floor(index / 7f)
    var col = index - row * 7f
    if (col > 6.5f) {
        row += 1f
        col = 0f
    } else if (col > 6f) {
        col = 6f
    }
    out[0] = col * cell + (cell - CAP) / 2f
    out[1] = row * DAY_ROW + (DAY_ROW - CAP) / 2f
}

/**
 * The calendar's animated values (Reanimated shared values in RN, stepped by the screen's frame loop) and its
 * React state (Compose state here). The screen keeps `onChange`, `onActivity` and `reduced` current.
 */
class DateChipCalendarState internal constructor(
    internal val host: DateChipHost,
    internal val months: List<CalendarMonth>,
    internal val initialPage: Int,
    internal val today: Int,
    internal val blocked: IntArray,
    internal val scriptLabel: String,
) {
    internal val open = DateChipValue(host, 0f)
    internal val reveal = DateChipValue(host, 0f)
    internal val roll = DateChipValue(host, 0f)
    internal val hasValue = DateChipValue(host, 0f)
    internal val head = DateChipValue(host, -1f)
    internal val capStart = DateChipValue(host, 0f)
    internal val capEnd = DateChipValue(host, 0f)
    internal val pagePosition = DateChipValue(host, initialPage.toFloat())
    internal val press = DateChipValue(host, 0f)

    internal var start = -1f
        set(value) {
            if (field != value) {
                field = value
                host.dirty = true
            }
        }
    internal var complete = false

    // React state.
    var page by mutableIntStateOf(initialPage)
        internal set
    var isOpen by mutableStateOf(false)
        internal set
    var label by mutableStateOf(scriptLabel)
        internal set
    var hasRange by mutableStateOf(false)
        internal set

    internal var onChange: (DateRange?) -> Unit = {}
    internal var onActivity: () -> Unit = {}
    internal var haptic: () -> Unit = {}
    internal var reduced = false

    // Gesture bookkeeping.
    private var dragKind = 0 // 0 none, 1 range, 2 page
    private var lastDay = -1
    private var pageBase = initialPage

    private fun go(value: DateChipValue, to: Float, spec: DateChipSpring) {
        if (reduced) value.timing(to, REDUCED_SECONDS) else value.spring(to, spec)
    }

    private val rollSeconds get() = if (reduced) 0.12f else DateChipT.ROLL_SPAN

    fun openCalendar() {
        onActivity()
        haptic()
        isOpen = true
        go(open, 1f, OPEN_SPRING)
        reveal.timing(1f, if (reduced) 0.12f else REVEAL_IN)
    }

    fun fold() {
        onActivity()
        haptic()
        isOpen = false
        go(open, 0f, FOLD_SPRING)
        reveal.timing(0f, if (reduced) 0.12f else REVEAL_OUT)
    }

    private fun commit(a: Int, b: Int) {
        onActivity()
        val range = DateRange(minOf(a, b), maxOf(a, b))
        label = formatRange(range.start, range.end)
        hasRange = true
        onChange(range)
        hasValue.timing(1f, 0.16f)
        roll.timing(1f, rollSeconds)
    }

    fun clear() {
        onActivity()
        haptic()
        hasRange = false
        onChange(null)
        start = -1f
        head.set(-1f)
        complete = false
        capStart.set(0f)
        capEnd.set(0f)
        hasValue.timing(0f, 0.16f)
        roll.timing(0f, rollSeconds)
    }

    internal fun resetAll() {
        isOpen = false
        hasRange = false
        page = initialPage
        label = scriptLabel
    }

    internal fun adopt(opened: Boolean, a: Int, b: Int) {
        isOpen = opened
        if (a >= 0 && b != a) {
            hasRange = true
            label = formatRange(minOf(a, b), maxOf(a, b))
            onChange(DateRange(minOf(a, b), maxOf(a, b)))
        }
    }

    fun changePage(next: Int) {
        onActivity()
        val target = next.coerceIn(0, months.size - 1)
        page = target
        if (reduced) pagePosition.set(target.toFloat()) else pagePosition.spring(target.toFloat(), PAGE_SPRING)
    }

    /** A new first day replaces a finished stay: the chip and the total go back to empty until it has an end. */
    private fun dropRange() {
        hasRange = false
        onChange(null)
        hasValue.timing(0f, 0.16f)
        roll.timing(0f, rollSeconds)
    }

    private fun beginAt(day: Int) {
        if (complete) dropRange()
        start = day.toFloat()
        head.set(day.toFloat())
        complete = false
        capEnd.set(0f)
        capStart.set(0f)
        capStart.spring(1f, CAP_SPRING)
        lastDay = day
        haptic()
    }

    /** The bookable day under (x, y), relative to the grid (under the chip); -1 for none. */
    private fun dayAt(x: Float, y: Float, cell: Float): Int {
        val current = months.getOrNull(pagePosition.value.roundToInt()) ?: return -1
        val col = floor((x - GRID_PAD) / cell).toInt()
        val row = floor((y - GRID_TOP) / DAY_ROW).toInt()
        if (col < 0 || col > 6 || row < 0 || row >= GRID_ROWS) return -1
        val day = row * 7 + col - current.offset
        if (day < 0 || day >= current.days) return -1
        val value = current.first + day
        if (value < today || isBlocked(value, blocked)) return -1
        return value
    }

    internal fun tap(x: Float, y: Float, cell: Float) {
        val day = dayAt(x, y, cell)
        if (day < 0) return
        val s = start.toInt()
        if (start < 0f || complete || day == s || blockedBetween(s, day, blocked)) {
            beginAt(day)
            return
        }
        head.timing(day.toFloat(), DRAG_DAY * 2f)
        capEnd.spring(1f, CAP_SPRING)
        complete = true
        haptic()
        commit(s, day)
    }

    internal fun panStart(startX: Float, startY: Float, tx: Float, ty: Float, cell: Float) {
        val day = dayAt(startX, startY, cell)
        val s = start.toInt()
        val h = head.value.roundToInt()
        val onCap = day >= 0 && start >= 0f && (day == s || day == h)
        if (!onCap && abs(tx) > abs(ty) * 1.2f) {
            dragKind = 2
            pageBase = pagePosition.value.roundToInt()
            return
        }
        if (day < 0) {
            dragKind = 0
            return
        }
        dragKind = 1
        if (onCap && complete) {
            // Grab either end: the other one becomes the anchor.
            if (day == s) {
                start = h.toFloat()
                head.set(s.toFloat())
            }
            lastDay = day
            complete = false
        } else {
            beginAt(day)
        }
    }

    internal fun panUpdate(x: Float, y: Float, tx: Float, cell: Float, pageWidth: Float) {
        if (dragKind == 2) {
            var next = pageBase - tx / pageWidth
            val max = (months.size - 1).toFloat()
            if (next < 0f) next *= PAGE_RUBBER else if (next > max) next = max + (next - max) * PAGE_RUBBER
            pagePosition.set(next)
            return
        }
        if (dragKind != 1) return
        val raw = dayAt(x, y, cell)
        if (raw < 0) return
        val day = reach(start.toInt(), raw, blocked)
        if (day == lastDay) return
        lastDay = day
        head.timing(day.toFloat(), DRAG_DAY)
        capEnd.spring(if (day == start.toInt()) 0f else 1f, CAP_SPRING)
        haptic()
    }

    internal fun panEnd(vx: Float, pageWidth: Float) {
        if (dragKind == 2) {
            val base = pageBase
            val moved = pagePosition.value - base
            var next = base
            if (moved > PAGE_THRESHOLD || vx < -PAGE_VELOCITY) {
                next = base + 1
            } else if (moved < -PAGE_THRESHOLD || vx > PAGE_VELOCITY) {
                next = base - 1
            }
            next = next.coerceIn(0, months.size - 1)
            pagePosition.spring(next.toFloat(), PAGE_SPRING, velocity = -vx / pageWidth)
            if (next != base) page = next
        } else if (dragKind == 1) {
            val s = start.toInt()
            val e = lastDay
            if (e != s) {
                complete = true
                commit(s, e)
            }
        }
        dragKind = 0
    }
}

/** Remembers the calendar's state for these months. */
@Composable
fun rememberDateChipCalendarState(
    months: List<CalendarMonth>,
    initialPage: Int,
    today: Int,
    blocked: IntArray,
    scriptLabel: String,
): DateChipCalendarState = remember(months, initialPage, today, scriptLabel) {
    DateChipCalendarState(DateChipHost(), months, initialPage, today, blocked, scriptLabel)
}

/** CSS `inset 0 1px 0 color`: the box minus itself moved 1 dp down, inside the rounded box. */
private fun DrawScope.insetTop(color: Color, radiusPx: Float) {
    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx))) }
    val moved = Path().apply { addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), CornerRadius(radiusPx))) }
    clipPath(box) {
        clipPath(moved, ClipOp.Difference) { drawRect(color) }
    }
}

/** A chevron: an `size` dp square's two borders (2 dp), turned by `degrees`. `right`/`bottom` pick the borders. */
private fun DrawScope.drawBorderChevron(color: Color, sizeDp: Float, border: Float, degrees: Float, first: Int, second: Int) {
    val s = sizeDp.dp.toPx()
    val b = border.dp.toPx()
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    withTransform({ rotate(degrees, Offset(size.width / 2f, size.height / 2f)) }) {
        for (side in intArrayOf(first, second)) {
            when (side) {
                SIDE_LEFT -> drawRect(color, Offset(left, top), Size(b, s))
                SIDE_RIGHT -> drawRect(color, Offset(left + s - b, top), Size(b, s))
                SIDE_TOP -> drawRect(color, Offset(left, top), Size(s, b))
                else -> drawRect(color, Offset(left, top + s - b), Size(s, b))
            }
        }
    }
}

private const val SIDE_LEFT = 0
private const val SIDE_RIGHT = 1
private const val SIDE_TOP = 2
private const val SIDE_BOTTOM = 3

/**
 * @param width the card's width in dp.
 * @param today serial of the first day that can be booked.
 * @param blocked serials of the nights that are taken.
 */
@Composable
fun DateChipCalendar(
    state: DateChipCalendarState,
    width: Float,
    modifier: Modifier = Modifier,
    placeholder: String = "Add dates",
    caption: String = "Dates",
    reducedMotion: Boolean = false,
    onChange: (DateRange?) -> Unit = {},
    onActivity: () -> Unit = {},
) {
    val view = LocalView.current
    val localDensity = LocalDensity.current
    state.onChange = onChange
    state.onActivity = onActivity
    state.reduced = reducedMotion
    state.haptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val tick = state.host.tick
    val months = state.months
    val pageWidth = width - GRID_PAD * 2f
    val cell = pageWidth / 7f
    val isOpen = state.isOpen
    val page = state.page

    val shadowNear = remember { SoftRoundRect(rgba(16, 40, 48, 0.10f), sigma = 1f) }
    val shadowFar = remember { SoftRoundRect(rgba(16, 40, 48, 0.10f), sigma = 13f) }

    Box(
        modifier
            .layout { measurable, _ ->
                tick.longValue
                val w = (width * localDensity.density).roundToInt()
                val h = (mix(state.open.value, CHIP_HEIGHT, OPEN_HEIGHT) * localDensity.density).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                tick.longValue
                val s = 1f - 0.015f * state.press.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                tick.longValue
                val r = mix(clamp01(state.open.value), RADIUS_CLOSED, RADIUS_OPEN)
                val rp = r.dp.toPx()
                val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(rp))) }
                clipPath(box, ClipOp.Difference) {
                    scale(localDensity.density, localDensity.density, pivot = Offset.Zero) {
                        val wd = size.width / localDensity.density
                        val hd = size.height / localDensity.density
                        drawIntoCanvas {
                            shadowNear.draw(it.nativeCanvas, 0f, 1f, wd, hd, r)
                            shadowFar.draw(it.nativeCanvas, 0f, 10f, wd, hd, r)
                        }
                    }
                }
                drawRoundRect(CARD, cornerRadius = CornerRadius(rp))
                insetTop(rgba(255, 255, 255, 0.9f), rp)
            }
            .graphicsLayer {
                tick.longValue
                shape = RoundedCornerShape(mix(clamp01(state.open.value), RADIUS_CLOSED, RADIUS_OPEN).dp)
                clip = true
            },
    ) {
        // The chip.
        Row(
            Modifier
                .fillMaxWidth()
                .height(CHIP_HEIGHT.dp)
                .semantics { contentDescription = if (state.hasRange) "$caption, ${state.label}" else "$caption, $placeholder" }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    if (state.isOpen) state.fold() else state.openCalendar()
                }
                .padding(start = 16.dp, end = 88.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The calendar mark: a 36 dp tile, a tide band on top and a dot.
            Box(
                Modifier
                    .size(36.dp)
                    .drawBehind {
                        val r = CornerRadius(11.dp.toPx())
                        val tile = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                        drawPath(tile, TIDE_SOFT)
                        clipPath(tile) { drawRect(TIDE, size = Size(size.width, 10.dp.toPx())) }
                        val dot = 8.dp.toPx()
                        drawRoundRect(TIDE, Offset((size.width - dot) / 2f, 16.dp.toPx()), Size(dot, dot), CornerRadius(2.5f.dp.toPx()))
                    },
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText(caption, style = CAPTION_STYLE, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(ROLL_LINE.dp).clipToBounds()) {
                    Column(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .graphicsLayer {
                                tick.longValue
                                translationY = -clamp01(state.roll.value) * ROLL_LINE * density
                            },
                    ) {
                        Box(Modifier.height(ROLL_LINE.dp), contentAlignment = Alignment.CenterStart) {
                            BasicText(placeholder, style = ROLL_MUTED_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.height(ROLL_LINE.dp), contentAlignment = Alignment.CenterStart) {
                            BasicText(state.label, style = ROLL_VALUE_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // The trailing control: chevron, clear, or Done.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .absoluteOffset((-12f).dp, 0.dp)
                .size(72.dp, CHIP_HEIGHT.dp),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        tick.longValue
                        alpha = (1f - clamp01(state.open.value * 2.5f)) * (1f - state.hasValue.value)
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                // 9 dp square, right and bottom borders, turned 45°: points down. Margin right 8, lifted 4.
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .absoluteOffset(0.dp, (-4f).dp)
                        .size(9.dp)
                        .drawBehind { drawBorderChevron(MUTE, 9f, 2f, 45f, SIDE_RIGHT, SIDE_BOTTOM) },
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        tick.longValue
                        alpha = (1f - clamp01(state.open.value * 2.5f)) * state.hasValue.value
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(FILL, CircleShape)
                        .then(
                            if (state.hasRange && !isOpen) {
                                Modifier
                                    .semantics { contentDescription = "Clear dates" }
                                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { state.clear() }
                            } else {
                                Modifier
                            },
                        )
                        .drawBehind {
                            val l = 12.dp.toPx()
                            val t = 1.8f.dp.toPx()
                            for (deg in floatArrayOf(45f, -45f)) {
                                withTransform({ rotate(deg, center) }) {
                                    drawRoundRect(INK, Offset(center.x - l / 2f, center.y - t / 2f), Size(l, t), CornerRadius(1.dp.toPx()))
                                }
                            }
                        },
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        tick.longValue
                        alpha = clamp01((state.open.value - 0.4f) * 2.5f)
                        val s = 0.92f + 0.08f * clamp01(state.open.value)
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .height(32.dp)
                        .background(TIDE, RoundedCornerShape(16.dp))
                        .then(
                            if (isOpen) {
                                Modifier
                                    .semantics { contentDescription = "Done" }
                                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { state.fold() }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Done", style = DONE_STYLE)
                }
            }
        }

        // The grid: month pages under the chip, revealed with the card.
        Box(
            Modifier
                .absoluteOffset(0.dp, CHIP_HEIGHT.dp)
                .fillMaxWidth()
                .height((OPEN_HEIGHT - CHIP_HEIGHT).dp)
                .graphicsLayer {
                    tick.longValue
                    alpha = clamp01(state.reveal.value * 5f)
                }
                .then(
                    if (!isOpen) {
                        Modifier
                    } else {
                        Modifier.pointerInput(state, cell, pageWidth) {
                            // Tap and pan race: a pan once the finger travels 8 dp, a tap otherwise.
                            val slop = PAN_SLOP * density
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var panning = false
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val tx = (change.position.x - down.position.x) / density
                                    val ty = (change.position.y - down.position.y) / density
                                    if (!panning && (change.position - down.position).getDistance() > slop) {
                                        panning = true
                                        state.panStart(down.position.x / density, down.position.y / density, tx, ty, cell)
                                    }
                                    if (panning) {
                                        change.consume()
                                        state.panUpdate(change.position.x / density, change.position.y / density, tx, cell, pageWidth)
                                    }
                                    if (!change.pressed) {
                                        if (panning) {
                                            state.panEnd(tracker.calculateVelocity().x / density, pageWidth)
                                        } else {
                                            state.tap(change.position.x / density, change.position.y / density, cell)
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            Box(
                Modifier
                    .absoluteOffset(GRID_PAD.dp, 0.dp)
                    .width(pageWidth.dp)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                for (index in (page - 1)..(page + 1)) {
                    val month = months.getOrNull(index) ?: continue
                    key(month.key) {
                        DateChipMonthPage(state, month, index, pageWidth, cell)
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .absoluteOffset((-GRID_PAD).dp, 4.dp)
                    .height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DateChipArrow(left = true, enabled = page > 0, active = isOpen) { state.changePage(page - 1) }
                DateChipArrow(left = false, enabled = page < months.size - 1, active = isOpen) { state.changePage(page + 1) }
            }
        }
    }
}

@Composable
private fun DateChipArrow(left: Boolean, enabled: Boolean, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .background(FILL, CircleShape)
            .semantics { contentDescription = if (left) "Previous month" else "Next month" }
            .then(
                if (active && enabled) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClick() }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // An 8 dp square's two borders turned 45°, nudged 3 dp towards its open side.
                translate(left = if (left) 1.5f.dp.toPx() else (-1.5f).dp.toPx()) {
                    if (left) {
                        drawBorderChevron(INK, 8f, 2f, 45f, SIDE_LEFT, SIDE_BOTTOM)
                    } else {
                        drawBorderChevron(INK, 8f, 2f, 45f, SIDE_RIGHT, SIDE_TOP)
                    }
                }
            },
    )
}

/** One month: its title, the weekdays, and the grid (range, day numbers and caps) in one canvas. */
@Composable
private fun DateChipMonthPage(state: DateChipCalendarState, month: CalendarMonth, index: Int, width: Float, cell: Float) {
    val tick = state.host.tick
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val first = month.first
    val offset = month.offset
    val days = month.days
    val last = first + days - 1
    val cellPx = with(density) { cell.dp.toPx() }
    val rowPx = with(density) { DAY_ROW.dp.toPx() }
    val dayLayouts: List<TextLayoutResult> = remember(month, state.today, state.blocked, measurer, density) {
        List(days) { d ->
            val value = first + d
            val style = when {
                isBlocked(value, state.blocked) -> DAY_BLOCKED_STYLE
                value < state.today -> DAY_MUTED_STYLE
                else -> DAY_STYLE
            }
            measurer.measure((d + 1).toString(), style)
        }
    }
    val capLayouts: List<TextLayoutResult> = remember(month, measurer, density) {
        List(days) { d -> measurer.measure((d + 1).toString(), CAP_STYLE) }
    }
    val range = remember { Path() }
    val capPlace = remember { FloatArray(2) }
    val capShadowNear = remember { SoftRoundRect(rgba(16, 60, 70, 0.30f), sigma = 1f) }
    val capShadowFar = remember { SoftRoundRect(rgba(16, 60, 70, 0.22f), sigma = 5f) }
    val layerPaint = remember { Paint() }

    Column(
        Modifier
            .width(width.dp)
            .fillMaxHeight()
            .graphicsLayer {
                tick.longValue
                translationX = (index - state.pagePosition.value) * width * density.density
            },
    ) {
        Box(Modifier.height(TITLE_ROW.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(month.title, style = MONTH_STYLE, maxLines = 1)
        }
        Row(Modifier.height(WEEK_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
            for (label in WEEKDAYS) BasicText(label, Modifier.width(cell.dp), style = WEEK_STYLE)
        }
        Canvas(Modifier.fillMaxWidth().height((DAY_ROW * GRID_ROWS).dp)) {
            tick.longValue
            val s = state.start
            val h = state.head.value

            buildRange(range, s, h, first, offset, days, cellPx, rowPx, BAND_INSET.dp.toPx())
            drawPath(range, TIDE_SOFT)

            // Day cells rise in a diagonal stagger.
            val reveal = state.reveal.value
            for (d in 0 until days) {
                val place = d + offset
                val col = place % 7
                val row = place / 7
                val delay = (col + row) / 11f * CELL_SPREAD
                val u = seg(reveal, delay, CELL_SPAN)
                if (u <= 0f) continue
                val layout = dayLayouts[d]
                val x = col * cellPx + (cellPx - layout.size.width) / 2f
                val y = row * rowPx + (rowPx - layout.size.height) / 2f + (1f - u) * CELL_RISE.dp.toPx()
                drawText(layout, topLeft = Offset(x, y), alpha = u)
            }

            // The caps over the band's ends.
            if (s >= first && s <= last) {
                capPosition(s, first, offset, cell, capPlace)
                drawCap(capLayouts, (s.roundToInt() - first).coerceIn(0, days - 1), capPlace, state.capStart.value, capShadowNear, capShadowFar, layerPaint)
            }
            if (s >= 0f && h >= first && h < last + 1 && abs(h - s) > 0.5f) {
                capPosition(h, first, offset, cell, capPlace)
                val day = h.roundToInt() - first
                drawCap(capLayouts, day, capPlace, state.capEnd.value, capShadowNear, capShadowFar, layerPaint)
            }
        }
    }
}

/** A 36 dp tide circle with its day number, scaled 0.7 → 1 and faded in by `u`, as one layer. */
private fun DrawScope.drawCap(
    layouts: List<TextLayoutResult>,
    day: Int,
    place: FloatArray,
    u: Float,
    near: SoftRoundRect,
    far: SoftRoundRect,
    paint: Paint,
) {
    val alpha = clamp01(u * 2f)
    if (alpha <= 0f) return
    val d = density
    val cap = CAP * d
    val left = place[0] * d
    val top = place[1] * d
    val cx = left + cap / 2f
    val cy = top + cap / 2f
    val grow = 0.7f + 0.3f * u
    paint.alpha = alpha
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(left - 20f * d, top - 20f * d, left + cap + 20f * d, top + cap + 24f * d), paint)
        withTransform({ scale(grow, grow, Offset(cx, cy)) }) {
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas {
                    near.draw(it.nativeCanvas, place[0], place[1] + 1f, CAP, CAP, CAP / 2f)
                    far.draw(it.nativeCanvas, place[0], place[1] + 4f, CAP, CAP, CAP / 2f)
                }
            }
            drawCircle(TIDE, cap / 2f, Offset(cx, cy))
            // The number is that of the day the cap stands on (the head's rounded day while it travels).
            val layout = layouts.getOrNull(day)
            if (layout != null && day >= 0) {
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            }
        }
        canvas.restore()
    }
}
