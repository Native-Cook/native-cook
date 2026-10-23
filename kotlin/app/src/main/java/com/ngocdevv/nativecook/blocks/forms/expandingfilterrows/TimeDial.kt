package com.ngocdevv.nativecook.blocks.forms.expandingfilterrows

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.ViewConfiguration
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.bleed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

const val TIMES = 48
const val TIME_PANEL = 218f
private val DIAL_BLUE = Color(0xff2f6bff)
private val DIAL_INK = Color.White
private val DIAL_HAIRLINE = Color(0xff232326)
private const val OPTIONS = 58f
private const val CHIP = 28f
private const val DOT = 9f
private const val CLOCK = 118f
private const val CLOCK_LEFT = 16f
private const val TICK_FROM = 46f
private const val TICK_TO = 52f
private const val ARC = 51f
private const val ARC_STROKE = 4f
private const val LONG_HAND = 40f
private const val SHORT_HAND = 24f
private const val LINE = 23f
private const val COLUMN = 92f
private const val COLUMN_SHIFT = 48f
private val FADE_AT = doubleArrayOf(0.0, 1.0, 2.0, 2.7)
private val FADE = doubleArrayOf(1.0, .72, .32, 0.0)
private const val FLICK = .14
private const val CHIP_MS = 220.0
private const val CHIP_HIT = 6f
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val OPTIONS_PADDING = 14f
private const val CHIP_GAP = 8f
private const val CHIP_PADDING = 11f
private const val CHIP_BORDER = 1f
private const val DOT_GAP = 7f
private const val CHIP_FONT = 13.5f
private const val CHIP_LINE = 18f
private const val WHEEL_FONT = 17f
private const val END_ENTRANCE = 28f
private const val TICK_WIDTH = 1.5f
private const val LONG_WIDTH = 2f
private const val SHORT_WIDTH = 3.5f
private const val PIN = 3f
private val CHIP_INK = Color(0xffececee)
private val CHIP_FILL = Color(0xff1a1a1d)
private val CHIP_EDGE = Color(0xff29292d)
private val MAJOR_TICK = Color(0xff77777d)
private val MINOR_TICK = Color(0xff434348)
private val FACE_COLORS = arrayOf(0f to Color(0xff242427), .8f to Color(0xff202023), 1f to Color(0xff1b1b1e))
private val CHIP_EASE = CubicBezierEasing(0f, 0f, .58f, 1f)

fun timeLabel(half: Int): String {
    val hour = floor(half / 2.0).toInt()
    return "${((hour + 11) % 12) + 1}:${if (half % 2 != 0) "30" else "00"} ${if (hour < 12) "am" else "pm"}"
}
private fun dialRound(value: Double): Int { val low = floor(value); return (if (value - low < .5) low else low + 1).toInt() }
private fun dialOpacity(away: Double): Float {
    val distance = abs(away)
    for (index in 1 until FADE_AT.size) if (distance <= FADE_AT[index]) {
        val t = (distance - FADE_AT[index - 1]) / (FADE_AT[index] - FADE_AT[index - 1])
        return (FADE[index - 1] + (FADE[index] - FADE[index - 1]) * t).toFloat()
    }
    return 0f
}
private fun dialText(size: Float, height: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
fun TimeDial(
    start: ExpandingFilterRowsValue,
    end: ExpandingFilterRowsValue,
    range: ExpandingFilterRowsValue,
    ranged: Boolean,
    singleLabel: String,
    rangeLabel: String,
    onMode: (Boolean) -> Unit,
    onStart: (Int, Boolean) -> Unit,
    onEnd: (Int, Boolean) -> Unit,
    onTouch: () -> Unit,
    shown: ExpandingFilterRowsValue,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val singleDot = remember { ExpandingFilterRowsValue(if (ranged) 0.0 else 1.0) }
    val rangeDot = remember { ExpandingFilterRowsValue(if (ranged) 1.0 else 0.0) }
    LaunchedEffect(ranged) {
        singleDot.time(if (ranged) 0.0 else 1.0, CHIP_MS, CHIP_EASE)
        rangeDot.time(if (ranged) 1.0 else 0.0, CHIP_MS, CHIP_EASE)
    }
    LaunchedEffect(Unit) { while (true) withFrameNanos { singleDot.advance(it); rangeDot.advance(it) } }
    fun choose(next: Boolean) {
        onTouch()
        if (next != ranged) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onMode(next) }
    }
    BoxWithConstraints(modifier.height(TIME_PANEL.dp).then(if (enabled) Modifier else Modifier.clearAndSetSemantics {})) {
        val width = maxWidth.value
        val bodyHeight = TIME_PANEL - OPTIONS
        val wheelsWidth = (width - CLOCK_LEFT - CLOCK).coerceAtLeast(0f)
        val there = ((shown.value - .45) / .55).coerceIn(0.0, 1.0).toFloat()
        Row(Modifier.size(width.dp, OPTIONS.dp).graphicsLayer { alpha = ((shown.value - .15) / .45).coerceIn(0.0, 1.0).toFloat() }.padding(horizontal = OPTIONS_PADDING.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            DialOption(singleLabel, !ranged, singleDot, enabled, Modifier) { choose(false) }
            Box(Modifier.width(CHIP_GAP.dp))
            DialOption(rangeLabel, ranged, rangeDot, enabled, Modifier) { choose(true) }
        }
        Box(Modifier.offset(y = (OPTIONS - 1f / density.density).dp).size(width.dp, (1f / density.density).dp).graphicsLayer { alpha = ((shown.value - .15) / .45).coerceIn(0.0, 1.0).toFloat() }.background(DIAL_HAIRLINE))
        Box(Modifier.offset(y = OPTIONS.dp).size(width.dp, bodyHeight.dp).graphicsLayer { alpha = there; scaleX = .96f + .04f * there; scaleY = scaleX }) {
            DialClock(start.value, end.value, range.value, Modifier.offset(CLOCK_LEFT.dp, ((bodyHeight - CLOCK) / 2f).dp).size(CLOCK.dp))
            DialWheel(start, if (ranged) "Start time" else "Time", onTouch, onStart, enabled,
                Modifier.offset((CLOCK_LEFT + CLOCK + (wheelsWidth - COLUMN) / 2f).dp).size(COLUMN.dp, bodyHeight.dp)
                    .graphicsLayer { translationX = (-COLUMN_SHIFT * range.value * density.density).toFloat() })
            DialWheel(end, "End time", onTouch, onEnd, enabled && ranged,
                Modifier.offset((CLOCK_LEFT + CLOCK + (wheelsWidth - COLUMN) / 2f).dp).size(COLUMN.dp, bodyHeight.dp)
                    .graphicsLayer { translationX = ((COLUMN_SHIFT + (1 - range.value) * END_ENTRANCE) * density.density).toFloat(); alpha = range.value.toFloat() })
        }
    }
}

@Composable
private fun DialOption(label: String, chosen: Boolean, dot: ExpandingFilterRowsValue, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    val latestAction by rememberUpdatedState(action)
    val view = LocalView.current
    val density = LocalDensity.current
    val fraction = dot.value.toFloat()
    // The transparent overlay extends the touch target without adding layout space.
    Box(modifier) {
        Row(Modifier.height(CHIP.dp).background(CHIP_FILL, RoundedCornerShape(CHIP.dp / 2)).border(CHIP_BORDER.dp, CHIP_EDGE, RoundedCornerShape(CHIP.dp / 2)).padding(horizontal = (CHIP_PADDING + CHIP_BORDER).dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size((DOT * fraction).dp, DOT.dp).graphicsLayer { alpha = fraction }.background(DIAL_BLUE, RoundedCornerShape(DOT.dp / 2)))
            Box(Modifier.width((DOT_GAP * fraction).dp))
            BasicText(label, style = dialText(CHIP_FONT, CHIP_LINE, FontWeight.Medium, CHIP_INK), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
        }
        Box(Modifier.matchParentSize().bleed(CHIP_HIT.dp)
            .then(if (enabled) Modifier.semantics { role = Role.RadioButton; contentDescription = label; selected = chosen; onClick { latestAction(); true } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (finger.isConsumed) break
                        val active = finger.position.x > -PRESS_RETENTION * density.density && finger.position.x < size.width + PRESS_RETENTION * density.density && finger.position.y > -PRESS_RETENTION * density.density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density.density
                        finger.consume()
                        if (!finger.pressed) { if (active) { view.playSoundEffect(SoundEffectConstants.CLICK); latestAction() }; break }
                    }
                }
            } else Modifier))
    }
}

private class DialOwnership(var epoch: Int, var previous: Int) { var byHand = false }
private class DialCoordinates { var value: LayoutCoordinates? = null }

@Composable
private fun DialWheel(position: ExpandingFilterRowsValue, label: String, onTouch: () -> Unit, onSettle: (Int, Boolean) -> Unit, enabled: Boolean, modifier: Modifier) {
    val latestTouch by rememberUpdatedState(onTouch)
    val latestSettle by rememberUpdatedState(onSettle)
    val enabledNow by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val view = LocalView.current
    val slop = remember(view) { ViewConfiguration.get(view.context).scaledTouchSlop.toFloat() }
    val owner = remember(position) { DialOwnership(position.ownershipEpoch, dialRound(position.value).coerceIn(0, TIMES - 1)) }
    val coordinates = remember { DialCoordinates() }
    DisposableEffect(position) {
        val detach = position.observe { value ->
            if (owner.epoch != position.ownershipEpoch) { owner.epoch = position.ownershipEpoch; owner.byHand = false }
            val next = dialRound(value).coerceIn(0, TIMES - 1)
            if (owner.previous != next) { owner.previous = next; latestSettle(next, owner.byHand) }
        }
        onDispose(detach)
    }
    fun step(by: Int) {
        latestTouch(); owner.epoch = position.ownershipEpoch; owner.byHand = true
        position.spring((dialRound(position.value) + by).coerceIn(0, TIMES - 1).toDouble())
    }
    val semantics = if (!enabled) Modifier.clearAndSetSemantics {} else Modifier.semantics {
        contentDescription = label; stateDescription = timeLabel(dialRound(position.value).coerceIn(0, TIMES - 1))
        customActions = listOf(CustomAccessibilityAction("Increment") { step(1); true }, CustomAccessibilityAction("Decrement") { step(-1); true })
    }
    val gesture = Modifier.pointerInput(position) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabledNow || down.position.x < 0 || down.position.x > size.width || down.position.y < 0 || down.position.y > size.height) return@awaitEachGesture
            position.cancel()
            val grabbed = position.value
            owner.epoch = position.ownershipEpoch; owner.byHand = true; latestTouch()
            var active = false
            val initial = coordinates.value?.localToRoot(down.position) ?: down.position
            var origin = initial
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, initial) }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val rootPosition = coordinates.value?.localToRoot(change.position) ?: change.position
                    tracker.addPosition(change.uptimeMillis, rootPosition)
                    if (change.isConsumed || !enabledNow) break
                    if (!active && change.pressed && (rootPosition - initial).getDistanceSquared() >= slop * slop) { active = true; origin = rootPosition }
                    if (active && change.pressed) {
                        val to = grabbed - (rootPosition.y - origin.y) / density.density / LINE
                        val over = if (to < 0) to else if (to > TIMES - 1) to - (TIMES - 1) else 0.0
                        position.set(to - over + over / (1 + abs(over)) / 2)
                        change.consume()
                    }
                    if (!change.pressed) { if (active) change.consume(); break }
                }
            } finally {
                val carried = position.value - tracker.calculateVelocity().y / density.density * FLICK / LINE
                position.spring(dialRound(carried).coerceIn(0, TIMES - 1).toDouble()); latestTouch()
            }
        }
    }
    BoxWithConstraints(modifier.clipToBounds().then(semantics).onGloballyPositioned { coordinates.value = it }.then(if (enabled) gesture else Modifier)) {
        val height = maxHeight.value
        repeat(TIMES) { index ->
            val away = index - position.value
            BasicText(timeLabel(index), style = dialText(WHEEL_FONT, LINE, FontWeight.SemiBold, DIAL_INK).copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = ((height - LINE) / 2f).dp).size(COLUMN.dp, LINE.dp)
                    .graphicsLayer { translationY = (away * LINE * density.density).toFloat(); alpha = dialOpacity(away) }.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun DialClock(start: Double, end: Double, range: Double, modifier: Modifier) {
    val arc = remember { Path() }
    val face = remember { Brush.radialGradient(*FACE_COLORS, center = Offset(CLOCK / 2, CLOCK / 2), radius = CLOCK / 2) }
    Canvas(modifier.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            val center = Offset(CLOCK / 2f, CLOCK / 2f)
            drawCircle(face, CLOCK / 2f, center)
            repeat(12) { hour ->
                val angle = hour * PI / 6
                val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                drawLine(if (hour % 3 == 0) MAJOR_TICK else MINOR_TICK, center + direction * TICK_FROM, center + direction * TICK_TO, TICK_WIDTH, StrokeCap.Round)
            }
            val from = start * 15
            val sweep = ((end - start) * 15).coerceIn(.1, 359.9)
            arc.reset()
            arc.addArc(androidx.compose.ui.geometry.Rect(center.x - ARC, center.y - ARC, center.x + ARC, center.y + ARC), (from - 90).toFloat(), sweep.toFloat())
            drawPath(arc, DIAL_BLUE, alpha = range.toFloat(), style = Stroke(ARC_STROKE, cap = StrokeCap.Round))
            val minutes = start * 180 % 360 + 360
            val hours = end * 15 % 360 + 360
            val way = (hours - minutes + 540) % 360 - 180
            val longAngle = (minutes + way * range) * PI / 180
            val shortAngle = start * PI / 12
            drawLine(DIAL_INK, center, center + Offset(sin(longAngle).toFloat(), -cos(longAngle).toFloat()) * LONG_HAND, LONG_WIDTH, StrokeCap.Round)
            drawLine(DIAL_INK, center, center + Offset(sin(shortAngle).toFloat(), -cos(shortAngle).toFloat()) * SHORT_HAND, SHORT_WIDTH, StrokeCap.Round)
            drawCircle(DIAL_INK, PIN, center)
        }
    }
}

