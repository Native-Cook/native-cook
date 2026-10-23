package com.ngocdevv.nativecook.blocks.sheets.dockexpandsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** @param caption A line under the label. */
data class Mode(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val caption: String? = null,
)

private val MODES = listOf(
    Mode("Classic", Icons.Filled.Star, Color(0xFF4B8DF8), "Sunrise, noon and sunset"),
    Mode("Daily", Icons.Filled.WbSunny, Color(0xFFF7A23B), "Your day around the light"),
    Mode("Photography", Icons.Filled.PhotoCamera, Color(0xFFC057EA), "Golden and blue hours"),
    Mode("Mindfulness", Icons.Filled.Eco, Color(0xFF44D07A), "Quiet moments to pause"),
    Mode("Hiking", Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFFD6A878), "Daylight left on the trail"),
    Mode("Moon Watching", Icons.Filled.DarkMode, Color(0xFF8B6CF6), "Moonrise and phase"),
)

private val TIMES = listOf(
    listOf("Sunrise", "6:08"),
    listOf("Golden hour", "7:12"),
    listOf("Solar noon", "12:41"),
    listOf("Sunset", "19:14"),
)

/** The autoPlay loop, in seconds from its start. */
const val LOOP = 10f
private const val PRESS_AT = 2.6f
private const val OPEN_AT = 2.9f
private const val PICK_AT = 4.6f
private const val CLOSE_AT = 6.2f
private const val TRAVEL_AT = 7.2f
private const val TRAVEL_BACK_AT = 9.1f
private const val REST_AT = 9.7f
// The mode the loop moves the tick to, by place in the list.
private const val PICKED = 2
// Seconds a touch keeps the loop away.
private const val USER_HOLD = 3f

private val NIGHT = Color(0xFF05060A)
private val GLASS = Color.White.copy(alpha = 0.1f)
private val GLASS_PRESSED = Color.White.copy(alpha = 0.25f)
private val GLASS_EDGE = Color.White.copy(alpha = 0.12f)
private val MUTED = Color.White.copy(alpha = 0.5f)
private val TIP_CLOSE = Color(0xFF14160C)
private val TICK_INK = Color(0xFF0B0F17)
// React Native's hairline width doubled, on a 3x screen.
private val HAIRLINE = (2f / 3f).dp
private val PILL = Color(0xFF161C2C)
private val LIST = listOf(Color(0xFF101B33), Color(0xFF0A0D14))
private val INK = Color.White
private val SOFT = Color.White.copy(alpha = 0.8f)
private val YELLOW = Color(0xFFE9FF70)
private val TICK_GREEN = Color(0xFFB8E04C)
private val LOCATION = Color(0xFF3B9BE0)

private const val MARGIN = 20f
private const val CONTROL = 48f
private const val CENTER_WIDTH = 136f
private const val MERGED_HEIGHT = 52f
private const val DOCK_LIFT = 32f
private const val TOOLTIP_HEIGHT = 30f
private const val TOOLTIP_GAP = 44f
private const val LINE = 1.5f
private const val ROW = 68f
private const val ROWS_TOP = 84f
private const val HEADER = 52f
private const val TRAVEL_TIP_WIDTH = 262f
private const val PRESS_SCALE = 0.92f
private const val PULSE = 0.03f
private const val PULSE_MS = 625
private const val DISMISS_DRAG = 80f

// A 550 ms spring with damping ratio 0.88: stiffness is (2π / duration)².
private val MORPH_SPRING = spring<Float>(dampingRatio = 0.88f, stiffness = 130f)
private const val TICK_MS = 250
private const val TRAVEL_MS = 1600
private const val FADE_MS = 400
private const val REDUCED_MS = 160
// Reanimated's easings: the default for withTiming, Easing.inOut(sin) and Easing.out(cubic).
private val QUAD = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private val SINE = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val CUBIC_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

/** Smooth 0 → 1 between [from] and [to]. */
private fun ramp(value: Float, from: Float, to: Float): Float {
    val t = min(1f, max(0f, (value - from) / (to - from)))
    return t * t * (3f - 2f * t)
}

/**
 * A night-sky screen with a dock of three glass controls. Pressing the sun button merges the dock
 * into one "Modes" pill that grows upward into a full-screen list, in one container whose frame,
 * corner radius and contents move together; closing runs the same motion back. A tooltip tied to
 * the button by a line breathes while the dock is at rest.
 *
 * @param modes The rows of the list the dock opens into.
 * @param selected The mode that starts out ticked, by place in [modes].
 * @param autoPlay Plays the whole interaction by itself, over and over, and gives way to a touch.
 */
@Composable
fun DockExpandSheet(
    modes: List<Mode> = MODES,
    selected: Int = 1,
    autoPlay: Boolean = false,
    place: String = "Lisbon",
    title: String = "Modes",
    hint: String = "Modes to match your routine",
    travelHint: String = "Long press and slide to time travel",
    times: List<List<String>> = TIMES,
    onSelect: ((Mode, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val modeRows by rememberUpdatedState(modes)
    val firstSelected by rememberUpdatedState(selected)
    val selectCallback by rememberUpdatedState(onSelect)
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // 0 dock, 1 full screen.
    val morph = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    // Which tooltip shows: 0 the first, 1 the second.
    val tip = remember { Animatable(0f) }
    val ruler = remember { Animatable(0f) }
    val tick = remember { Animatable(selected.toFloat()) }
    var mode by remember { mutableIntStateOf(selected) }
    var isOpen by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var swiped by remember { mutableFloatStateOf(0f) }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun move(to: Float) = scope.launch {
        if (reducedMotion) morph.animateTo(to, tween(REDUCED_MS)) else morph.animateTo(to, MORPH_SPRING)
    }

    fun startPulse() {
        if (reducedMotion) return
        scope.launch {
            pulse.snapTo(0f)
            pulse.animateTo(1f, infiniteRepeatable(tween(PULSE_MS, easing = SINE), RepeatMode.Reverse))
        }
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        scope.launch { pulse.animateTo(0f, tween(120)) }
        move(1f)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        move(0f)
    }

    fun choose(index: Int) {
        mode = index
        scope.launch { tick.animateTo(index.toFloat(), tween(if (reducedMotion) REDUCED_MS else TICK_MS, easing = CUBIC_OUT)) }
        selectCallback?.invoke(modeRows[index], index)
    }

    fun setPress(down: Boolean) {
        scope.launch { press.animateTo(if (down) 1f else 0f, tween(if (down) 80 else 160, easing = QUAD)) }
    }

    fun travel(on: Boolean) {
        scope.launch { tip.animateTo(if (on) 1f else 0f, tween(FADE_MS, easing = QUAD)) }
        scope.launch {
            if (on) ruler.animateTo(1f, tween(TRAVEL_MS, easing = LinearEasing)) else ruler.animateTo(0f, tween(FADE_MS, easing = QUAD))
        }
    }

    /** Everything back as it was at the start, with no animation. */
    fun rest() {
        scope.launch {
            for (value in listOf(morph, pulse, press, tip, ruler)) value.snapTo(0f)
            tick.snapTo(firstSelected.toFloat())
        }
        isOpen = false
        mode = firstSelected
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        val steps = listOf(
            0f to { startPulse() },
            PRESS_AT to { setPress(true) },
            OPEN_AT to { setPress(false); open() },
            PICK_AT to { choose(PICKED) },
            CLOSE_AT to { close() },
            TRAVEL_AT to { travel(true) },
            TRAVEL_BACK_AT to { travel(false) },
            REST_AT to { rest() },
        )
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, run) in steps) {
                until(at)
                run()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(NIGHT)) {
        val width = maxWidth
        val height = maxHeight
        val dockTop = height - bottomInset - DOCK_LIFT.dp - CONTROL.dp
        val pillTop = dockTop - ((MERGED_HEIGHT - CONTROL) / 2f).dp
        val horizon = topInset + 250.dp
        val rulerTop = horizon + 36.dp
        val widthPx by rememberUpdatedState(with(localDensity) { width.toPx() })
        val heightPx by rememberUpdatedState(with(localDensity) { height.toPx() })
        val pillTopPx by rememberUpdatedState(with(localDensity) { pillTop.toPx() })

        // The page: sky and tooltips.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = ramp(morph.value, 0.2f, 1f)
                    alpha = 1f - t
                    scaleX = lerp(1f, 0.94f, t)
                    scaleY = lerp(1f, 0.94f, t)
                },
        ) {
            SkyPage(width, horizon, rulerTop, { ruler.value }, times)

            Box(
                Modifier
                    .offset(x = MARGIN.dp, y = dockTop - TOOLTIP_GAP.dp - TOOLTIP_HEIGHT.dp)
                    .graphicsLayer { alpha = 1f - tip.value },
            ) {
                Box(Modifier.offset(x = (CONTROL / 2f - LINE / 2f).dp, y = TOOLTIP_HEIGHT.dp).size(LINE.dp, TOOLTIP_GAP.dp).background(YELLOW))
                Tooltip(
                    hint,
                    Modifier.graphicsLayer {
                        val scale = 1f + PULSE * pulse.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
                )
            }

            Box(
                Modifier
                    .offset(x = (width - TRAVEL_TIP_WIDTH.dp) / 2, y = rulerTop + 92.dp)
                    .graphicsLayer { alpha = tip.value },
            ) {
                Box(Modifier.offset(x = (TRAVEL_TIP_WIDTH / 2f - LINE / 2f).dp, y = (-48).dp).size(LINE.dp, 48.dp).background(YELLOW))
                Box(Modifier.offset(x = (TRAVEL_TIP_WIDTH / 2f - 4f).dp, y = (-52).dp).size(8.dp).background(YELLOW, CircleShape))
                Tooltip(travelHint, Modifier.width(TRAVEL_TIP_WIDTH.dp))
            }
        }

        // The dock.
        Row(
            Modifier
                .offset(x = MARGIN.dp, y = dockTop)
                .width(width - MARGIN.dp * 2)
                .height(CONTROL.dp)
                .graphicsLayer { alpha = 1f - ramp(morph.value, 0f, 0.2f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val latestOpen by rememberUpdatedState(::open)
            Box(
                Modifier
                    .size(CONTROL.dp)
                    .graphicsLayer {
                        val scale = lerp(1f, PRESS_SCALE, press.value)
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBehind { drawCircle(if (press.value > 0.5f) GLASS_PRESSED else GLASS) }
                    .border(HAIRLINE, GLASS_EDGE, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                touched()
                                setPress(true)
                                tryAwaitRelease()
                                setPress(false)
                            },
                            onTap = { if (!isOpen) latestOpen() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = "Modes", tint = INK, modifier = Modifier.size(21.dp))
            }
            Row(
                Modifier
                    .size(CENTER_WIDTH.dp, CONTROL.dp)
                    .graphicsLayer { scaleX = lerp(1f, (widthPx / density - MARGIN * 2f) / CENTER_WIDTH, ramp(morph.value, 0f, 0.3f)) }
                    .background(GLASS, CircleShape)
                    .border(HAIRLINE, GLASS_EDGE, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.Navigation, null, tint = LOCATION, modifier = Modifier.size(14.dp))
                Text(place, color = SOFT, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.size(CONTROL.dp).background(GLASS, CircleShape).border(HAIRLINE, GLASS_EDGE, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GridView, null, tint = SOFT, modifier = Modifier.size(18.dp))
            }
        }

        // The one container: a pill at the dock that grows to the full screen.
        Box(
            Modifier
                .offset {
                    val grow = ramp(morph.value, 0.2f, 1f)
                    IntOffset(lerp(MARGIN * density, 0f, grow).roundToInt(), lerp(pillTopPx, 0f, grow).roundToInt())
                }
                .layout { measurable, _ ->
                    val grow = ramp(morph.value, 0.2f, 1f)
                    val w = lerp(widthPx - MARGIN * 2f * density, widthPx, grow).roundToInt()
                    val h = lerp(MERGED_HEIGHT * density, heightPx, grow).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val grow = ramp(morph.value, 0.2f, 1f)
                    alpha = ramp(morph.value, 0f, 0.12f)
                    shape = RoundedCornerShape(lerp(MERGED_HEIGHT / 2f, 0f, grow).dp)
                    clip = true
                }
                .background(PILL),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ramp(morph.value, 0.3f, 0.8f) }
                    .background(Brush.verticalGradient(LIST)),
            )

            Box(Modifier.fillMaxWidth().offset(y = topInset + ROWS_TOP.dp)) {
                Column {
                    modes.forEachIndexed { index, item ->
                        ModeRow(item, index, selected = index == mode, morph = { morph.value }, enabled = isOpen) {
                            touched()
                            choose(index)
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = MARGIN.dp)
                        .height(ROW.dp)
                        .graphicsLayer { translationY = tick.value * ROW * density },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .dropShadow(CircleShape, Shadow(radius = 8.dp, color = TICK_GREEN, alpha = 0.45f))
                            .background(TICK_GREEN, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, null, tint = TICK_INK, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HEADER.dp)
                    .offset { IntOffset(0, (lerp(0f, topInset.toPx() + 4f * density, ramp(morph.value, 0.2f, 1f))).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = isOpen,
                        state = rememberDraggableState { swiped += it },
                        onDragStarted = {
                            swiped = 0f
                            touched()
                        },
                        onDragStopped = { if (swiped > DISMISS_DRAG * localDensity.density) close() },
                    )
                    .padding(horizontal = MARGIN.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.align(Alignment.CenterStart).size(34.dp).background(GLASS, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SwapVert, null, tint = SOFT, modifier = Modifier.size(16.dp))
                }
                Text(title, color = INK, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(34.dp)
                        .background(GLASS, CircleShape)
                        .clickable(enabled = isOpen, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            touched()
                            close()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = SOFT, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun Tooltip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(TOOLTIP_HEIGHT.dp)
            .dropShadow(CircleShape, Shadow(radius = 10.dp, color = YELLOW, alpha = 0.35f))
            .background(YELLOW, CircleShape)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // The close mark sits at the end when the pill is given a width; the text keeps 8 dp from it.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            modifier = Modifier.padding(end = 8.dp),
            color = Color(0xFF0D0D0D),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            softWrap = false,
        )
        Box(Modifier.size(18.dp).background(TIP_CLOSE, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, null, tint = YELLOW, modifier = Modifier.size(10.dp))
        }
    }
}

/** A row of the list: it fades in and slides up 16 dp a little after the row above. */
@Composable
private fun ModeRow(mode: Mode, index: Int, selected: Boolean, morph: () -> Float, enabled: Boolean, onPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW.dp)
            .graphicsLayer {
                val t = ramp(morph(), 0.45f + index * 0.04f, 0.85f + index * 0.04f)
                alpha = t
                translationY = lerp(16f, 0f, t) * density
            }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPress)
            .padding(horizontal = MARGIN.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(mode.color.copy(alpha = 0.15f), WELL_SHAPE)
                .border(HAIRLINE, mode.color.copy(alpha = 0.25f), WELL_SHAPE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(mode.icon, contentDescription = null, tint = mode.color, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mode.label, color = INK, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            if (mode.caption != null) {
                Text(mode.caption, color = MUTED, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val WELL_SHAPE = RoundedCornerShape(13.dp)
