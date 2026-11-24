package com.ngocdevv.nativecook.blocks.pickers.datechipcalendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Harbor, a stay-booking form: the screen and the autoPlay script around the reusable DateChipCalendar.

private val PAPER = Color(0xFFEEF2EF)
private val HAIRLINE = rgba(22, 38, 46, 0.09f)
private val FILL = Color(0xFFE6ECE9)

// Layout, in dp.
private const val SIDE = 16f
private const val HEADER = 72f
private const val ART_MIN = 64f
private const val ART_MAX = 220f
private const val GAP = 12f
private const val OPTION = 58f
private const val FACTS = 76f
private const val NOTE = 20f
private const val BAR = 76f
private const val GHOST = 44f
private const val RATE = 179
private const val BREAKFAST = 18

private val FACT_ROWS = listOf("2" to "Bedrooms", "4.9" to "Guest rating", "40 m" to "To the water")

private val MONTHS = makeCalendarMonths(2026, 10, 7)
private const val DEC = 1
private val TODAY = calendarSerial(2026, 10, 24)
private val BLOCKED = intArrayOf(
    calendarSerial(2026, 11, 3),
    calendarSerial(2026, 11, 4),
    calendarSerial(2026, 11, 5),
    calendarSerial(2026, 11, 26),
    calendarSerial(2026, 11, 27),
)
private val SCRIPT_START = calendarSerial(2026, 11, 9)
private val SCRIPT_END = calendarSerial(2026, 11, 21)
private val SCRIPT_LABEL = formatRange(SCRIPT_START, SCRIPT_END)
private val DEC_FIRST = MONTHS[DEC].first
private val DEC_OFFSET = MONTHS[DEC].offset

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

private val TAPS = floatArrayOf(DateChipT.OPEN, DateChipT.PICK, DateChipT.DONE, DateChipT.CLEAR)

private val BRAND_STYLE = dateChipStyle(15f, FontWeight.Bold, TIDE)
private val TITLE_STYLE = dateChipStyle(28f, FontWeight.Bold, INK, line = 34f)
private val ART_TITLE_STYLE = dateChipStyle(17f, FontWeight.Bold, Color(0xFFFFF9EE))
private val ART_META_STYLE = dateChipStyle(13f, FontWeight.Medium, rgba(255, 249, 238, 0.86f))
private val OPTION_TITLE_STYLE = dateChipStyle(16f, FontWeight.SemiBold, INK)
private val OPTION_META_STYLE = dateChipStyle(13f, FontWeight.Normal, MUTE)
private val STEP_STYLE = dateChipStyle(16f, FontWeight.SemiBold, INK, tabular = true).copy(textAlign = TextAlign.Center)
private val FACT_VALUE_STYLE = dateChipStyle(18f, FontWeight.Bold, INK, tabular = true)
private val FACT_LABEL_STYLE = dateChipStyle(12f, FontWeight.Normal, MUTE)
private val NOTE_STYLE = dateChipStyle(13f, FontWeight.Normal, MUTE)
private val TOTAL_CAPTION_STYLE = dateChipStyle(13f, FontWeight.Medium, MUTE)
private val TOTAL_MUTED_STYLE = dateChipStyle(17f, FontWeight.SemiBold, MUTE)
private val TOTAL_VALUE_STYLE = dateChipStyle(17f, FontWeight.Bold, INK, tabular = true)
private val RESERVE_STYLE = dateChipStyle(17f, FontWeight.SemiBold, Color(0xFFF7FBFA))

private fun totalLabel(nights: Int, breakfast: Boolean): String {
    val total = nights * (RATE + if (breakfast) BREAKFAST else 0)
    return "$${NumberFormat.getIntegerInstance(Locale.US).format(total)} · $nights ${if (nights == 1) "night" else "nights"}"
}

private fun ghostPress(t: Float): Float {
    var press = 0f
    for (tap in TAPS) {
        val d = t - tap
        if (d > -0.1f && d < 0.12f) press = max(press, if (d < 0f) 1f + d / 0.1f else 1f - d / 0.12f)
    }
    // The finger stays down through the drag.
    if (t >= DateChipT.DRAG_START - 0.08f && t <= DateChipT.DRAG_START + DateChipT.DRAG_SPAN) press = 1f
    return press
}

private fun ghostOpacity(t: Float): Float {
    val a = seg(t, DateChipT.GHOST_IN, 0.2f) * (1f - seg(t, DateChipT.OPEN + 0.26f, 0.2f))
    val b = seg(t, DateChipT.PICK - 0.42f, 0.2f) * (1f - seg(t, DateChipT.DONE + 0.16f, 0.2f))
    val c = seg(t, DateChipT.CLEAR - 0.45f, 0.2f) * (1f - seg(t, DateChipT.CLEAR + 0.18f, 0.2f))
    return max(a, max(b, c))
}

/** The screen's frame loop (RN `useFrameCallback`) and its autoPlay script. */
internal class HarborEngine(val calendar: DateChipCalendarState, playing: Boolean) {
    val host = calendar.host
    val ghostO = DateChipValue(host, 0f)
    var ghostX = 0f
    var ghostY = 0f
    var ghostS = 0f

    var mode = if (playing) MODE_AUTO else MODE_MANUAL
        private set
    private var playing = playing
    var touches = 0
    private var seen = 0
    var held = 0
    private var idle = 0f
    private var clock = 0f
    private var lastFrame = -1L

    var range by mutableStateOf<DateRange?>(null)
        private set
    /** The total keeps the last range's nights while it rolls out, so a cleared range never flashes the script's. */
    var shownNights by mutableIntStateOf(SCRIPT_END - SCRIPT_START)
        private set

    fun changeRange(next: DateRange?) {
        range = next
        if (next != null) shownNights = next.end - next.start
    }

    // Where the ghost finger goes, in screen dp.
    var gridX = 0f
    var gridY = 0f
    var cell = 1f
    var chipX = 0f
    var chipY = 0f
    var doneX = 0f
    var clearX = 0f
    private val centre = FloatArray(2)

    private fun dayCentre(day: Float) {
        val index = day - DEC_FIRST + DEC_OFFSET
        var row = floor(index / 7f)
        var col = index - row * 7f
        if (col > 6.5f) {
            row += 1f
            col = 0f
        } else if (col > 6f) {
            col = 6f
        }
        centre[0] = gridX + (col + 0.5f) * cell
        centre[1] = gridY + (row + 0.5f) * DAY_ROW
    }

    fun setPlaying(next: Boolean) {
        if (next == playing) return
        playing = next
        if (next || mode != MODE_AUTO) return
        // Reduced motion or autoPlay off: the resting form, nothing scripted.
        mode = MODE_MANUAL
        ghostO.set(0f)
        val m = calendar
        m.open.set(0f)
        m.reveal.set(0f)
        m.roll.set(0f)
        m.hasValue.set(0f)
        m.start = -1f
        m.head.set(-1f)
        m.complete = false
        m.capStart.set(0f)
        m.capEnd.set(0f)
        m.press.set(0f)
        m.resetAll()
        host.dirty = true
    }

    fun frame(nanos: Long) {
        val dt = if (lastFrame < 0) 1f / 60f else min((nanos - lastFrame) / 1e9f, 0.048f)
        lastFrame = nanos
        if (dt <= 0f) return
        if (playing) script(dt)
        host.step(dt)
        host.publish()
    }

    /**
     * A finger went down or a control was used. The takeover runs at once, not on the next frame: a tap whose press
     * and release land in one frame would otherwise open the chip and then have the late takeover fold it again.
     */
    fun touched() {
        touches += 1
        if (playing) noteTouches()
    }

    private fun noteTouches() {
        if (touches == seen) return
        seen = touches
        idle = 0f
        if (mode != MODE_MANUAL) {
            if (mode == MODE_AUTO) takeOver()
            mode = MODE_MANUAL
            host.dirty = true
        }
    }

    private fun script(dt: Float) {
        val m = calendar
        noteTouches()
        when (mode) {
            MODE_MANUAL -> {
                if (held > 0) {
                    idle = 0f
                    return
                }
                idle += dt
                if (idle >= RESUME_AFTER) {
                    settle()
                    mode = MODE_RETURN
                }
            }
            MODE_RETURN -> {
                if (abs(m.open.value) < 0.01f && m.roll.value < 0.01f && abs(m.pagePosition.value - DEC) < 0.01f) {
                    m.open.set(0f)
                    m.reveal.set(0f)
                    m.roll.set(0f)
                    m.hasValue.set(0f)
                    m.pagePosition.set(DEC.toFloat())
                    m.start = -1f
                    m.head.set(-1f)
                    m.complete = false
                    m.capStart.set(0f)
                    m.capEnd.set(0f)
                    m.resetAll()
                    range = null
                    shownNights = SCRIPT_END - SCRIPT_START
                    clock = 0f
                    mode = MODE_AUTO
                    host.dirty = true
                }
            }
            else -> {
                var t = clock + dt
                if (t >= LOOP) t -= LOOP
                clock = t
                applyPose(t)

                // The ghost finger: chip, first day, along the range, Done, clear.
                var x = chipX
                var y = chipY
                if (t >= DateChipT.OPEN + 0.3f && t < DateChipT.DRAG_START) {
                    dayCentre(SCRIPT_START.toFloat())
                    val u = easeInOutCubic(seg(t, DateChipT.PICK - 0.42f, 0.34f))
                    x = mix(u, chipX, centre[0])
                    y = mix(u, chipY, centre[1])
                } else if (t >= DateChipT.DRAG_START && t < DateChipT.COMMIT) {
                    dayCentre(m.head.value)
                    x = centre[0]
                    y = centre[1]
                } else if (t >= DateChipT.COMMIT && t < DateChipT.FOLD + 0.2f) {
                    dayCentre(SCRIPT_END.toFloat())
                    val u = easeInOutCubic(seg(t, DateChipT.COMMIT + 0.3f, 0.44f))
                    x = mix(u, centre[0], doneX)
                    y = mix(u, centre[1], chipY)
                } else if (t >= DateChipT.CLEAR - 0.5f) {
                    x = clearX
                    y = chipY
                }
                val s = ghostPress(t)
                if (x != ghostX || y != ghostY || s != ghostS) {
                    ghostX = x
                    ghostY = y
                    ghostS = s
                    host.dirty = true
                }
                ghostO.set(ghostOpacity(t))
            }
        }
    }

    /** Every animated value of the calendar at loop time t. */
    private fun applyPose(t: Float) {
        val m = calendar
        val up = springAt(t - DateChipT.OPEN, OPEN_SPRING)
        val down = springAt(t - DateChipT.FOLD, FOLD_SPRING)
        m.open.set(if (t < DateChipT.FOLD) up else max(0f, 1f - down))
        m.reveal.set(seg(t, DateChipT.OPEN + REVEAL_DELAY, REVEAL_IN) * (1f - seg(t, DateChipT.FOLD, REVEAL_OUT)))

        val picked = t >= DateChipT.PICK && t < DateChipT.CLEAR
        m.start = if (picked) SCRIPT_START.toFloat() else -1f
        m.head.set(
            if (picked) {
                mix(easeInOutCubic(seg(t, DateChipT.DRAG_START, DateChipT.DRAG_SPAN)), SCRIPT_START.toFloat(), SCRIPT_END.toFloat())
            } else {
                -1f
            },
        )
        m.capStart.set(if (picked) max(0f, springAt(t - DateChipT.PICK, CAP_SPRING)) else 0f)
        m.capEnd.set(if (picked) max(0f, springAt(t - DateChipT.DRAG_START, CAP_SPRING)) else 0f)
        m.complete = t >= DateChipT.COMMIT && t < DateChipT.CLEAR

        val rollIn = easeInOutCubic(seg(t, DateChipT.COMMIT, DateChipT.ROLL_SPAN))
        val rollOut = easeInOutCubic(seg(t, DateChipT.CLEAR, DateChipT.ROLL_SPAN))
        m.roll.set(rollIn * (1f - rollOut))
        m.hasValue.set(seg(t, DateChipT.COMMIT, 0.16f) * (1f - seg(t, DateChipT.CLEAR, 0.16f)))
        m.press.set(if (t > DateChipT.OPEN - 0.1f && t < DateChipT.OPEN + 0.12f) ghostPress(t) else 0f)
    }

    /** A touch mid-loop: what the script was showing becomes real. */
    private fun takeOver() {
        val m = calendar
        ghostO.timing(0f, 0.12f)
        m.press.set(0f)
        val head = m.head.value
        if (head >= 0f) m.head.timing(head.roundToInt().toFloat(), 0.1f)
        if (m.start >= 0f && abs(head - m.start) > 0.5f) {
            m.complete = true
            m.capEnd.timing(1f, 0.1f)
            m.hasValue.timing(1f, 0.16f)
            m.roll.timing(1f, 0.2f)
        } else {
            m.hasValue.timing(0f, 0.16f)
            m.roll.timing(0f, 0.2f)
        }
        if (m.open.value > 0.5f) {
            m.open.spring(1f, OPEN_SPRING)
            m.reveal.timing(1f, 0.16f)
        } else {
            m.open.spring(0f, FOLD_SPRING)
            m.reveal.timing(0f, 0.16f)
        }
        m.adopt(m.open.value > 0.5f, m.start.toInt(), head.roundToInt())
    }

    private fun settle() {
        val m = calendar
        m.open.spring(0f, FOLD_SPRING)
        m.reveal.timing(0f, REVEAL_OUT)
        m.roll.timing(0f, DateChipT.ROLL_SPAN)
        m.hasValue.timing(0f, 0.16f)
        m.pagePosition.spring(DEC.toFloat(), FOLD_SPRING)
    }

    /** The ghost finger: hidden outside autoPlay. */
    fun ghostAlpha() = if (mode == MODE_AUTO) ghostO.value * 0.9f else 0f
}

/**
 * @param autoPlay plays the 10 s loop until a finger takes over; it resumes 2 s after the last touch.
 */
@Composable
fun HarborBooking(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onReserve: (DateRange) -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val calendar = rememberDateChipCalendarState(MONTHS, DEC, TODAY, BLOCKED, SCRIPT_LABEL)
    val engine = remember(calendar) { HarborEngine(calendar, playing) }
    val reserve by rememberUpdatedState(onReserve)
    var guests by remember { mutableIntStateOf(2) }
    var breakfast by remember { mutableStateOf(false) }
    val tick = calendar.host.tick

    LaunchedEffect(engine, playing) { engine.setPlaying(playing) }
    LaunchedEffect(engine) {
        while (true) withFrameNanos { engine.frame(it) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAPER)
            .clipToBounds()
            // Any finger on the screen interrupts autoPlay; the loop waits while one is down.
            .pointerInput(engine) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) {
                            engine.held = event.changes.count { it.pressed }
                            engine.touched()
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val width = maxWidth.value
        val height = maxHeight.value
        val bars = WindowInsets.systemBars
        val top = bars.getTop(density) / density.density
        val insetBottom = bars.getBottom(density) / density.density

        val cardWidth = width - SIDE * 2f
        val barHeight = BAR + max(insetBottom, 12f)
        // The picture takes what is left, as long as the open calendar still clears the bar.
        val room = height - top - HEADER - GAP - barHeight
        val fitsOpen = room - OPEN_HEIGHT - 8f
        val fillsClosed = room - CHIP_HEIGHT - GAP - (OPTION * 2f + FACTS + NOTE + GAP * 3f) - 16f
        val artHeight = max(ART_MIN, min(ART_MAX, min(fitsOpen, fillsClosed)))
        val chipTop = top + HEADER + artHeight + GAP
        engine.cell = (cardWidth - GRID_PAD * 2f) / 7f
        engine.gridX = SIDE + GRID_PAD
        engine.gridY = chipTop + CHIP_HEIGHT + GRID_TOP
        engine.chipX = SIDE + cardWidth / 2f
        engine.chipY = chipTop + CHIP_HEIGHT / 2f
        engine.doneX = SIDE + cardWidth - 42f
        engine.clearX = SIDE + cardWidth - 27f

        val range = engine.range
        val nights = engine.shownNights
        val total = remember(nights, breakfast) { totalLabel(nights, breakfast) }

        // Header.
        Column(Modifier.fillMaxWidth().padding(top = (top + 8f).dp, start = (SIDE + 4f).dp, end = (SIDE + 4f).dp)) {
            BasicText("Harbor", style = BRAND_STYLE)
            BasicText("Book your stay", Modifier.padding(top = 1.dp), style = TITLE_STYLE)
        }

        // The picture.
        Box(
            Modifier
                .absoluteOffset(SIDE.dp, (top + HEADER).dp)
                .size(cardWidth.dp, artHeight.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF23566A)),
        ) {
            HarbourArt(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp)) {
                BasicText("Saltline Cottage", style = ART_TITLE_STYLE)
                BasicText("Port Ellis · $$RATE a night", style = ART_META_STYLE)
            }
        }

        // Below the chip, moved by transform only.
        Column(
            Modifier
                .absoluteOffset(SIDE.dp, (chipTop + CHIP_HEIGHT + GAP).dp)
                .size(cardWidth.dp, (OPTION * 2f + FACTS + NOTE + GAP * 3f).dp)
                .graphicsLayer {
                    tick.longValue
                    translationY = calendar.open.value * (OPEN_HEIGHT - CHIP_HEIGHT) * density.density
                },
            verticalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            HarborOption("Guests", "Sleeps up to 4") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HarborStep(plus = false, label = "Fewer guests") { guests = max(1, guests - 1) }
                    BasicText(guests.toString(), Modifier.widthIn(min = 16.dp), style = STEP_STYLE)
                    HarborStep(plus = true, label = "More guests") { guests = min(4, guests + 1) }
                }
            }
            HarborOption("Breakfast", "$$BREAKFAST a night") {
                Switch(
                    checked = breakfast,
                    onCheckedChange = { breakfast = it },
                    // The page is always light: pin the off state too, or a phone in dark mode draws a dark track.
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = TIDE,
                        checkedThumbColor = Color.White,
                        checkedBorderColor = TIDE,
                        uncheckedTrackColor = Color(0xFFE2E8E5),
                        uncheckedThumbColor = MUTE,
                        uncheckedBorderColor = MUTE,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth().height(FACTS.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in FACT_ROWS) {
                    Column(
                        Modifier
                            .weight(1f)
                            .height(FACTS.dp)
                            .background(FILL, RoundedCornerShape(16.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BasicText(value, style = FACT_VALUE_STYLE)
                        BasicText(label, Modifier.padding(top = 1.dp), style = FACT_LABEL_STYLE)
                    }
                }
            }
            Box(Modifier.height(NOTE.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                BasicText("Free cancellation until 7 days before check-in.", style = NOTE_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // The chip and its calendar.
        Box(Modifier.absoluteOffset(SIDE.dp, chipTop.dp)) {
            DateChipCalendar(
                state = calendar,
                width = cardWidth,
                reducedMotion = reduced,
                onChange = { engine.changeRange(it) },
                onActivity = { engine.touched() },
            )
        }

        // The bottom bar.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(barHeight.dp)
                .cssBoxShadow(rgba(16, 40, 48, 0.06f), (-6f).dp, 18.dp, 0.dp)
                .background(CARD)
                .drawBehind { drawRect(HAIRLINE, size = Size(size.width, 1f)) }
                .padding(start = (SIDE + 4f).dp, end = (SIDE + 4f).dp, bottom = max(insetBottom, 12f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                BasicText("Total", style = TOTAL_CAPTION_STYLE)
                Box(Modifier.fillMaxWidth().height(ROLL_LINE.dp).clipToBounds()) {
                    Column(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .graphicsLayer {
                                tick.longValue
                                translationY = -clamp01(calendar.roll.value) * ROLL_LINE * density.density
                            },
                    ) {
                        Box(Modifier.height(ROLL_LINE.dp), contentAlignment = Alignment.CenterStart) {
                            BasicText("Select dates", style = TOTAL_MUTED_STYLE)
                        }
                        Box(Modifier.height(ROLL_LINE.dp), contentAlignment = Alignment.CenterStart) {
                            BasicText(total, style = TOTAL_VALUE_STYLE, maxLines = 1)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .graphicsLayer {
                        tick.longValue
                        alpha = 0.42f + 0.58f * calendar.hasValue.value
                    }
                    .height(48.dp)
                    .background(TIDE, RoundedCornerShape(24.dp))
                    .semantics { contentDescription = "Reserve" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = range != null, role = Role.Button) {
                        range?.let { reserve(it) }
                    }
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Reserve", style = RESERVE_STYLE)
            }
        }

        // The ghost finger.
        Box(
            Modifier
                .size(GHOST.dp)
                .graphicsLayer {
                    tick.longValue
                    alpha = engine.ghostAlpha()
                    translationX = (engine.ghostX - GHOST / 2f) * density.density
                    translationY = (engine.ghostY - GHOST / 2f) * density.density
                    val s = 1f - 0.16f * engine.ghostS
                    scaleX = s
                    scaleY = s
                }
                .background(rgba(90, 110, 116, 0.34f), CircleShape)
                .border(1.dp, rgba(255, 255, 255, 0.72f), CircleShape),
        )
    }
}

@Composable
private fun HarborOption(title: String, meta: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(OPTION.dp)
            .cssBoxShadow(rgba(16, 40, 48, 0.07f), 1.dp, 2.dp, 18.dp)
            .cssBoxShadow(rgba(16, 40, 48, 0.05f), 6.dp, 14.dp, 18.dp)
            .background(CARD, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = OPTION_TITLE_STYLE)
            BasicText(meta, style = OPTION_META_STYLE)
        }
        trailing()
    }
}

@Composable
private fun HarborStep(plus: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(FILL, CircleShape)
            .semantics { contentDescription = label }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClick() }
            .drawBehind {
                val l = 12.dp.toPx()
                val t = 2.dp.toPx()
                val r = CornerRadius(1.dp.toPx())
                drawRoundRect(INK, Offset(center.x - l / 2f, center.y - t / 2f), Size(l, t), r)
                if (plus) drawRoundRect(INK, Offset(center.x - t / 2f, center.y - l / 2f), Size(t, l), r)
            },
    )
}

/** The harbour at dusk, drawn from a 360 × 132 view box that covers the box (xMinYMid slice). */
@Composable
private fun HarbourArt(modifier: Modifier) {
    val shapes = remember {
        HarbourShapes(
            hill = Path().apply {
                moveTo(0f, 82f)
                cubicTo(40f, 66f, 86f, 70f, 130f, 80f)
                cubicTo(170f, 88f, 196f, 76f, 232f, 78f)
                lineTo(232f, 92f)
                lineTo(0f, 92f)
                close()
            },
            glints = Path().apply {
                moveTo(136f, 96f)
                lineTo(216f, 96f)
                moveTo(150f, 106f)
                lineTo(202f, 106f)
                moveTo(164f, 116f)
                lineTo(192f, 116f)
            },
            house = Path().apply {
                moveTo(58f, 92f)
                lineTo(58f, 70f)
                lineTo(76f, 58f)
                lineTo(94f, 70f)
                lineTo(94f, 92f)
                close()
            },
            roof = Path().apply {
                moveTo(54f, 72f)
                lineTo(76f, 55f)
                lineTo(98f, 72f)
            },
            hull = Path().apply {
                moveTo(108f, 98f)
                lineTo(144f, 98f)
                lineTo(138f, 106f)
                lineTo(114f, 106f)
                close()
            },
            sail = Path().apply {
                moveTo(126f, 98f)
                lineTo(126f, 76f)
                lineTo(140f, 94f)
                close()
            },
        )
    }
    val sky = remember { Brush.verticalGradient(0f to Color(0xFF23566A), 0.55f to Color(0xFFE0A77E), 1f to Color(0xFFF4D7A8), startY = 0f, endY = 132f) }
    val sea = remember { Brush.verticalGradient(0f to Color(0xFF3E7E86), 0.5f to Color(0xFF1F5766), 1f to Color(0xFF143F4C), startY = 86f, endY = 132f) }
    Canvas(modifier) {
        val k = max(size.width / 360f, size.height / 132f)
        clipRect {
            translate(0f, (size.height - 132f * k) / 2f) {
                scale(k, k, pivot = Offset.Zero) {
                    drawRect(sky, size = Size(360f, 132f))
                    drawCircle(Color(0xFFFBE9C6), 20f, Offset(176f, 70f), alpha = 0.92f)
                    drawPath(shapes.hill, Color(0xFF2C5A63), alpha = 0.75f)
                    drawRect(sea, Offset(0f, 86f), Size(360f, 46f))
                    drawPath(shapes.glints, Color(0xFFF8DDB1), alpha = 0.5f, style = Stroke(2f, cap = StrokeCap.Round))
                    drawPath(shapes.house, Color(0xFFF2EADB))
                    drawPath(shapes.roof, Color(0xFF7C4A38), style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawRoundRect(Color(0xFF34565F), Offset(71f, 78f), Size(10f, 14f), CornerRadius(1.5f))
                    drawPath(shapes.hull, Color(0xFFF2EADB))
                    drawPath(shapes.sail, Color(0xFFFBF3E4))
                }
            }
        }
    }
}

private class HarbourShapes(val hill: Path, val glints: Path, val house: Path, val roof: Path, val hull: Path, val sail: Path)
