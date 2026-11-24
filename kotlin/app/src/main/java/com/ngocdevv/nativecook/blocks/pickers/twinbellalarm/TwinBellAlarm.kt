package com.ngocdevv.nativecook.blocks.pickers.twinbellalarm

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The film's period (s), as in alarmMotion.ts. */
const val LOOP = AlarmMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val TOP = 20f
private const val READ_H = 46f
private const val SUB_H = 20f
private const val WHEEL_GAP = 6f
private const val WHEEL_H = 148f
private const val CARD_GAP = 8f
private const val CARD_H = 56f
private const val CARD_PAD = 16f
private const val BOTTOM = 14f
private const val PANEL_H = TOP + READ_H + SUB_H + WHEEL_GAP + WHEEL_H + CARD_GAP + CARD_H + BOTTOM
private const val WHEEL_TOP = TOP + READ_H + SUB_H + WHEEL_GAP
private const val CARD_TOP = WHEEL_TOP + WHEEL_H + CARD_GAP
private const val DRAG_PT = 64f
private const val MINUTES_DX = 8f
private const val SWITCH_DX = 26f
private const val DOT = 44f
private const val ROW = 34f

private val HOURS = List(24) { it }
private val MINUTES = List(60 / AlarmMotion.WHEEL_STEP) { it * AlarmMotion.WHEEL_STEP }

private fun rgb(hex: Int, a: Float = 1f): Color = Color(hex or (0xFF shl 24)).copy(alpha = a)

private fun type(size: Int, line: Int, color: Color, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, tabular: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

private fun clockText(minutes: Int): String {
    val h = (minutes / 60) % 24
    return "${(h + 11) % 12 + 1}:${(minutes % 60).toString().padStart(2, '0')}"
}

private fun ringsIn(minutes: Int): String {
    val d = (minutes - AlarmMotion.NOW_MIN + 1440) % 1440
    return "Rings in ${d / 60} h ${d % 60} min"
}

/**
 * Setting an alarm as a 10-second film: a real 3D twin-bell clock on a studio sweep above a sheet with a time wheel
 * and a switch. The hands follow the wheel; switching the alarm on makes the clock ring, the hammer chattering between
 * the bells while the clock hops and rocks and the camera moves in.
 *
 * Ported from the React Native block (react-native-filament): the same alarm.glb and light in Filament, with every
 * pivot driven from the loop's values. As in the React Native Android build, the wheel is two snapping columns
 * (24-hour); the switch is Material 3. Drag across the clock to look round it. 2 s after the last touch the alarm
 * goes back to 7:00 and off, and 1 s later the film restarts. Reduced motion: a still at 6:30 with the alarm on.
 */
@Composable
fun TwinBellAlarm(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { TwinBellAlarmController(autoPlay, reduced) }
    val view = LocalView.current

    // One clock for the Compose layers, read only in draw lambdas. The sheet's two states change only with the film's
    // steps, and are read inside the sheet, so the 3D stage never recomposes for them.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val minutes = remember(controller) { mutableIntStateOf(controller.wheel) }
    val on = remember(controller) { mutableStateOf(controller.armed) }
    LaunchedEffect(controller) {
        var buzzes = controller.buzzes
        var ticks = controller.ticks
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                if (minutes.intValue != controller.wheel) minutes.intValue = controller.wheel
                if (on.value != controller.armed) on.value = controller.armed
                if (controller.buzzes != buzzes) {
                    buzzes = controller.buzzes
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                if (controller.ticks != ticks) {
                    ticks = controller.ticks
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
    val film: () -> Double = remember(controller) {
        {
            controller.frame(frameNanos / 1e6)
            controller.t
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(AlarmMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(200f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            TwinBellAlarmStage(controller, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Alarm clock. Drag sideways to look round the clock" }
                    .pointerInput(controller, w) {
                        val d = this.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dx = 0f
                            var dy = 0f
                            var active = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.positionChange()
                                dx += delta.x
                                dy += delta.y
                                if (!active) {
                                    if (abs(dy) / d > 28) break
                                    if (abs(dx) / d >= 8) {
                                        active = true
                                        controller.dragBegan(w.toDouble())
                                    }
                                }
                                if (active) {
                                    controller.dragMoved((dx / d).toDouble(), w.toDouble())
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (active) controller.dragEnded((tracker.calculateVelocity().x / d).toDouble(), w.toDouble())
                                    break
                                }
                            }
                            if (active) controller.dragFinalized()
                        }
                    },
            ) {
                film()
                val veil = 1f - controller.shown.toFloat()
                if (veil > 0f) drawRect(rgb(AlarmMotion.BACKDROP), alpha = veil)
                // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
                val shadeH = (top + 72) * density.density
                val b = rgb(AlarmMotion.BACKDROP)
                drawRect(
                    Brush.verticalGradient(
                        0f to b,
                        0.55f to b.copy(alpha = 0.6f),
                        1f to b.copy(alpha = 0f),
                        startY = 0f,
                        endY = shadeH,
                    ),
                    size = Size(size.width, shadeH),
                )
            }
        }

        Row(
            modifier = Modifier.offset(y = (top + 10).dp).fillMaxWidth().padding(horizontal = GUTTER.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(AlarmMotion.APP, style = type(17, 22, rgb(AlarmMotion.INK), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
            Text(AlarmMotion.REPEATS, style = type(15, 20, rgb(AlarmMotion.INK, 0.62f)), modifier = Modifier.alignByBaseline())
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color(0x1B, 0x22, 0x38).copy(alpha = 0.2f), offsetY = (-8).dp, blur = 48.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(AlarmMotion.PANEL))
                // the sheet takes its touches: the stage runs a little way under its corners
                .pointerInput(Unit) {}
                .drawWithContent {
                    drawContent()
                    val r = PANEL_R.dp.toPx()
                    clipRect(bottom = r) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.95f),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(size.width - 1f, r * 3),
                            cornerRadius = CornerRadius(r - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            AlarmPanel(min(w, 560f) - GUTTER * 2, controller, minutes, on, film)
        }
    }
}

@Composable
private fun AlarmPanel(
    inner: Float,
    controller: TwinBellAlarmController,
    minutesState: MutableIntState,
    onState: MutableState<Boolean>,
    film: () -> Double,
) {
    val density = LocalDensity.current
    val minutes = minutesState.intValue
    val on = onState.value
    Box(Modifier.width(inner.dp).height(PANEL_H.dp)) {
        Column(Modifier.padding(top = TOP.dp)) {
            Row(Modifier.height(READ_H.dp)) {
                Text(
                    clockText(minutes),
                    style = type(42, READ_H.toInt(), rgb(AlarmMotion.INK), FontWeight.Bold, tracking = -0.8f, tabular = true),
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    if (minutes % 1440 < 720) "AM" else "PM",
                    style = type(20, 26, rgb(AlarmMotion.MUTE), FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 6.dp).alignByBaseline(),
                )
            }
            Text(
                if (on) ringsIn(minutes) else "Alarm is off",
                style = type(15, SUB_H.toInt(), if (on) Color(0xFF9A6A05) else rgb(AlarmMotion.MUTE), tabular = true),
                modifier = Modifier.height(SUB_H.dp),
            )
            AlarmWheel(
                minutes = minutes,
                width = inner,
                onHeld = controller::wheelHeld,
                onMinutes = { v ->
                    minutesState.intValue = v
                    controller.userMinutes(v)
                },
                modifier = Modifier.padding(top = WHEEL_GAP.dp),
            )
            Row(
                modifier = Modifier
                    .padding(top = CARD_GAP.dp)
                    .fillMaxWidth()
                    .height(CARD_H.dp)
                    .cssBoxShadow(Color(0x1B, 0x22, 0x38).copy(alpha = 0.07f), offsetY = 3.dp, blur = 20.dp, radius = 18.dp)
                    .background(rgb(AlarmMotion.CARD), RoundedCornerShape(18.dp))
                    .border(with(density) { 1f.toDp() }, rgb(AlarmMotion.INK, 0.08f), RoundedCornerShape(18.dp))
                    .padding(horizontal = CARD_PAD.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alarm", style = type(17, 22, rgb(AlarmMotion.INK)))
                Switch(
                    checked = on,
                    onCheckedChange = { v ->
                        onState.value = v
                        controller.userToggle(v)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = rgb(AlarmMotion.YELLOW),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(120, 122, 134).copy(alpha = 0.3f),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Alarm" },
                )
            }
        }
        // the film's finger, on the wheel and on the switch
        val ghost = remember { DoubleArray(3) }
        Canvas(Modifier.fillMaxSize()) {
            val tt = film()
            if (!controller.filming) return@Canvas
            AlarmMotion.wheelGhost(tt, ghost)
            drawGhost(inner / 2 + MINUTES_DX, WHEEL_TOP + WHEEL_H / 2 + ghost[2].toFloat() * DRAG_PT, ghost[0], ghost[1])
            AlarmMotion.toggleGhost(tt, ghost)
            drawGhost(inner - CARD_PAD - SWITCH_DX, CARD_TOP + CARD_H / 2, ghost[0], ghost[1])
        }
    }
}

private fun DrawScope.drawGhost(x: Float, y: Float, opacity: Double, pressed: Double) {
    val a = (opacity * 0.9).toFloat()
    if (a <= 0.002f) return
    val d = density
    val c = Offset(x * d, y * d)
    val r = DOT / 2 * (1 - 0.16f * pressed.toFloat()) * d
    val ink = Color(40, 44, 60)
    drawCircle(ink, radius = r, center = c, alpha = 0.16f * a)
    drawCircle(ink, radius = r - 0.75f * d, center = c, alpha = 0.3f * a, style = Stroke(1.5f * d))
}

/** The time wheel: two snapping columns, 24-hour, as the React Native block has on Android. */
@Composable
private fun AlarmWheel(minutes: Int, width: Float, onHeld: (Boolean) -> Unit, onMinutes: (Int) -> Unit, modifier: Modifier = Modifier) {
    val h = minutes / 60
    val m = ((minutes % 60).toFloat() / AlarmMotion.WHEEL_STEP).roundToInt() * AlarmMotion.WHEEL_STEP
    Box(modifier.width(width.dp).height(WHEEL_H.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROW.dp)
                .background(rgb(AlarmMotion.MUTE, 0.12f), RoundedCornerShape(10.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelColumn(HOURS, h, onHeld) { v -> onMinutes(v * 60 + m % 60) }
            Text(":", style = type(23, 28, rgb(AlarmMotion.INK)), modifier = Modifier.padding(horizontal = 4.dp))
            WheelColumn(MINUTES, m % 60, onHeld) { v -> onMinutes(h * 60 + v) }
        }
    }
}

/** One snapping column. The film moves it by state; a finger's choice is reported when the column comes to rest. */
@Composable
private fun WheelColumn(values: List<Int>, value: Int, onHeld: (Boolean) -> Unit, onValue: (Int) -> Unit) {
    val index = max(0, values.indexOf(value))
    val state = rememberLazyListState(index)
    val dragged by state.interactionSource.collectIsDraggedAsState()
    var touched by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val report by rememberUpdatedState(onValue)
    val held by rememberUpdatedState(onHeld)

    LaunchedEffect(index, touched) {
        if (!touched) state.animateScrollToItem(index)
    }
    LaunchedEffect(state) {
        snapshotFlow { dragged to state.isScrollInProgress }.collect { (isDragged, scrolling) ->
            if (isDragged && !touched) {
                touched = true
                held(true)
            } else if (touched && !isDragged && !scrolling) {
                val rowPx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                val at = state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset.toFloat() / rowPx).roundToInt()
                val picked = values[at.coerceIn(values.indices)]
                if (picked != currentValue) report(picked)
                held(false)
                touched = false
            }
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = ((WHEEL_H - ROW) / 2).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(72.dp, WHEEL_H.dp)
            .semantics { contentDescription = "Alarm time" },
    ) {
        items(values) { v ->
            Box(Modifier.fillMaxWidth().height(ROW.dp), contentAlignment = Alignment.Center) {
                Text(
                    v.toString().padStart(2, '0'),
                    style = type(23, 28, if (v == value) rgb(AlarmMotion.INK) else rgb(AlarmMotion.MUTE, 0.6f), tabular = true),
                )
            }
        }
    }
}
