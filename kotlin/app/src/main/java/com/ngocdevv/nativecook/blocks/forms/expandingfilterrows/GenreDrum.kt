package com.ngocdevv.nativecook.blocks.forms.expandingfilterrows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException

const val GENRE_PANEL = 236f
private const val BLUE = "#2f6bff"
private const val RADIUS = 280.0
private const val STEP = 7.5
private const val STEP_RAD = STEP * PI / 180.0
private val PITCH = RADIUS * sin(STEP_RAD)
private const val LINE = 30f
private const val LINE_WIDTH = 150f
private const val LEFT_RIM = -24f
private const val RIGHT_RIM = 27f
private const val RING = 12f
private val FADE_AT = doubleArrayOf(0.0, 1.2, 2.0, 3.0, 3.6)
private val FADE = doubleArrayOf(1.0, 1.0, .82, .32, 0.0)
private const val SWING = 2.5
private const val DRUM_MASS = 1.0
private const val DRUM_STIFFNESS = 190.0
private const val DRUM_DAMPING = 24.0
private const val SLOT_MASS = 1.0
private const val SLOT_STIFFNESS = 210.0
private const val SLOT_DAMPING = 20.0
private const val FLICK = .14
const val PICK_MS = 260L
private const val ARRIVE_MS = 320.0
private const val RING_MS = 140.0
private const val RING_BORDER = 1.5f
private const val RING_GAP = 9f
private const val RING_GROW = .25f
private const val PIP = 3.5f
private const val PIP_RADIUS = 2f
private const val PIP_ALPHA = .7f
private const val NAME_SIZE = 16f
private const val NAME_LINE = 22f
private const val EMPTY_SIZE = 14f
private const val EMPTY_LINE = 20f
private const val EMPTY_INK = "#5c5c62"
private const val INK = "#ffffff"
private const val TAP_DURATION = 500L
private enum class Side { Left, Right }
private class ColumnState { var coordinates: LayoutCoordinates? = null }
private fun frame(x: Float, y: Float, width: Float, height: Float) = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(max(0f, width).dp, max(0f, height).dp).graphicsLayer { translationX = x * density; translationY = y * density }
private fun nameStyle(size: Float, line: Float, ink: String, weight: Int = 500, align: TextAlign = TextAlign.Start) = TextStyle(
    color = expandingFilterRowsColor(ink), fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), fontFamily = FontFamily.SansSerif,
    platformStyle = PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), textAlign = align,
)
private fun faded(away: Double): Float {
    val distance = abs(away)
    val i = FADE_AT.indexOfFirst { it >= distance }
    if (i <= 0) return if (i == 0) FADE[0].toFloat() else FADE.last().toFloat()
    val u = (distance - FADE_AT[i - 1]) / (FADE_AT[i] - FADE_AT[i - 1])
    return (FADE[i - 1] + (FADE[i] - FADE[i - 1]) * u).toFloat()
}

/** Each side turns around an off-screen hub; the hit surface stays a fixed half panel. */
@Composable
fun GenreDrum(
    chosen: List<String>, others: List<String>, leftPosition: ExpandingFilterRowsValue, rightPosition: ExpandingFilterRowsValue,
    shown: ExpandingFilterRowsValue, moving: String?, fresh: String?, emptyLabel: String,
    onPick: (String) -> Unit, onDrop: (String) -> Unit, onTouch: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(GENRE_PANEL.dp).clipToBounds()) {
        val width = maxWidth.value
        if (chosen.isEmpty()) {
            Box(frame(0f, (GENRE_PANEL - EMPTY_LINE) / 2f, max(0f, width / 2f + LEFT_RIM), EMPTY_LINE)) {
                BasicText(emptyLabel, style = nameStyle(EMPTY_SIZE, EMPTY_LINE, EMPTY_INK, 400, TextAlign.End), modifier = Modifier.fillMaxSize())
            }
        }
        GenreColumn(Side.Left, chosen, leftPosition, shown, moving, fresh, onDrop, onTouch, enabled, frame(0f, 0f, width / 2f, GENRE_PANEL))
        GenreColumn(Side.Right, others, rightPosition, shown, moving, fresh, onPick, onTouch, enabled, frame(width / 2f, 0f, width / 2f, GENRE_PANEL))
    }
}
@Composable
private fun GenreColumn(side: Side, names: List<String>, position: ExpandingFilterRowsValue, shown: ExpandingFilterRowsValue, moving: String?, fresh: String?, onTap: (String) -> Unit, onTouch: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val state = remember { ColumnState() }
    val currentNames by rememberUpdatedState(names); val currentTap by rememberUpdatedState(onTap); val currentTouch by rememberUpdatedState(onTouch)
    val last = max(0, names.lastIndex)
    LaunchedEffect(last, position) { if (position.value > last) position.spring(last.toDouble(), DRUM_MASS, DRUM_STIFFNESS, DRUM_DAMPING) }
    val gesture = if (enabled) Modifier.pointerInput(state, position) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            position.cancel(); val grabbed = position.value; currentTouch()
            val tracker = VelocityTracker()
            fun root(point: Offset) = state.coordinates?.takeIf { it.isAttached }?.localToRoot(point) ?: point
            val initial = root(down.position); var activated: Offset? = null; var ended = false; var tapAllowed = true
            var latestY = down.position.y; var velocity = 0f; var latestTime = down.uptimeMillis
            tracker.addPosition(down.uptimeMillis, initial)
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    val point = root(finger.position); tracker.addPosition(finger.uptimeMillis, point); latestY = finger.position.y; latestTime = finger.uptimeMillis
                    velocity = tracker.calculateVelocity().y
                    val delta = point - initial
                    if (finger.position.x < 0f || finger.position.x > size.width || finger.position.y < 0f || finger.position.y > size.height) tapAllowed = false
                    if (finger.isConsumed) break
                    if (finger.pressed && activated == null && delta.getDistance() >= viewConfiguration.touchSlop) activated = point
                    val origin = activated
                    if (origin != null && finger.pressed) {
                        val target = grabbed - (point.y - origin.y) / density / PITCH
                        val bound = max(0, currentNames.lastIndex).toDouble()
                        val over = if (target < 0.0) target else if (target > bound) target - bound else 0.0
                        position.set(target - over + over / (1.0 + abs(over)) / 2.0); finger.consume()
                    }
                    if (!finger.pressed) { ended = true; break }
                }
            } catch (cancelled: CancellationException) {
                val carried = position.value - velocity / density * FLICK / PITCH
                position.spring(expandingFilterRowsRound(carried).coerceIn(0.0, max(0, currentNames.lastIndex).toDouble()), DRUM_MASS, DRUM_STIFFNESS, DRUM_DAMPING)
                throw cancelled
            }
            val carried = position.value - velocity / density * FLICK / PITCH
            position.spring(expandingFilterRowsRound(carried).coerceIn(0.0, max(0, currentNames.lastIndex).toDouble()), DRUM_MASS, DRUM_STIFFNESS, DRUM_DAMPING)
            if (ended && activated == null && tapAllowed && latestTime - down.uptimeMillis <= TAP_DURATION) {
                val angle = asin(((latestY / density - GENRE_PANEL / 2f) / RADIUS).coerceIn(-1.0, 1.0))
                val slot = expandingFilterRowsRound(position.value + angle / STEP_RAD).toInt()
                currentNames.getOrNull(slot)?.let { currentTap(it) }
            }
        }
    } else Modifier
    BoxWithConstraints(modifier.onGloballyPositioned { state.coordinates = it }.then(gesture)) {
        names.forEachIndexed { index, name -> key(name) {
            GenreLine(name, index, side, position, shown, name == moving, name == fresh, { currentTap(name) }, maxWidth.value)
        } }
    }
}
@Composable
private fun GenreLine(name: String, index: Int, side: Side, position: ExpandingFilterRowsValue, shown: ExpandingFilterRowsValue, moving: Boolean, fresh: Boolean, activate: () -> Unit, columnWidth: Float) {
    val slot = remember { ExpandingFilterRowsValue(index.toDouble()) }
    val present = remember { ExpandingFilterRowsValue(if (fresh) 0.0 else 1.0) }
    val ring = remember { ExpandingFilterRowsValue(if (moving) 1.0 else 0.0) }
    LaunchedEffect(index) { slot.spring(index.toDouble(), SLOT_MASS, SLOT_STIFFNESS, SLOT_DAMPING) }
    LaunchedEffect(moving) { present.time(if (moving) 0.0 else 1.0, if (moving) PICK_MS.toDouble() else ARRIVE_MS); ring.time(if (moving) 1.0 else 0.0, RING_MS, ExpandingFilterRowsCss) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { now -> slot.advance(now); present.advance(now); ring.advance(now) } }
    val turn = if (side == Side.Left) -SWING else SWING
    val x = if (side == Side.Left) columnWidth + LEFT_RIM - LINE_WIDTH else RIGHT_RIM
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val style = nameStyle(NAME_SIZE, NAME_LINE, INK, align = if (side == Side.Left) TextAlign.End else TextAlign.Start)
    val textSize = measurer.measure(AnnotatedString(name), style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(1, (LINE_WIDTH * density.density).roundToInt()))).size
    Box(frame(x, (GENRE_PANEL - LINE) / 2f, LINE_WIDTH, LINE).graphicsLayer {
        val away = slot.value - position.value + turn * (1.0 - min(1.0, shown.value))
        val angle = away * STEP_RAD; val out = RADIUS * (1.0 - cos(angle))
        translationX += (if (side == Side.Left) -out else out).toFloat() * density.density
        translationY += (RADIUS * sin(angle)).toFloat() * density.density
        rotationZ = ((if (side == Side.Left) angle else -angle) * 180.0 / PI).toFloat()
        transformOrigin = TransformOrigin(if (side == Side.Left) 1f else 0f, .5f)
        alpha = faded(away) * present.value.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha
    }.semantics(mergeDescendants = true) { role = Role.Button; contentDescription = name; stateDescription = if (side == Side.Left) "Chosen. Activate to remove." else "Activate to choose."; onClick { activate(); true } }) {
        if (side == Side.Right) {
            Box(frame(0f, (LINE - RING) / 2f, RING, RING).graphicsLayer { scaleX = 1f + RING_GROW * ring.value.toFloat(); scaleY = scaleX }.background(expandingFilterRowsColor(BLUE).copy(alpha = ring.value.toFloat()), RoundedCornerShape((RING / 2f).dp)).border(RING_BORDER.dp, expandingFilterRowsColor(BLUE), RoundedCornerShape((RING / 2f).dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(PIP.dp).background(expandingFilterRowsColor(if (moving) INK else BLUE).copy(alpha = if (moving) 1f else PIP_ALPHA), RoundedCornerShape((PIP_RADIUS).dp)))
            }
        }
        val textWidth = textSize.width / density.density; val textHeight = textSize.height / density.density
        val textX = if (side == Side.Left) LINE_WIDTH - textWidth else RING + RING_GAP
        BasicText(name, frame(textX, (LINE - textHeight) / 2f, textWidth, textHeight), style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
