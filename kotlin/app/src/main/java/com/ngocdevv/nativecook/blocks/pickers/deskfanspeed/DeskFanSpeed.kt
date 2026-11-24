package com.ngocdevv.nativecook.blocks.pickers.deskfanspeed

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The film's period (s), as in fanMotion.ts. */
const val LOOP = FanMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val TOP = 18f
private const val HEAD_H = 30f
private const val GAP = 14f
private const val PICKER_H = 36f
private const val STATS_H = 74f
private const val BOTTOM = 16f
private const val PANEL_H = TOP + HEAD_H + GAP + PICKER_H + GAP + STATS_H + BOTTOM
private const val DOT = 44f

/** The airflow: height of each current as a fraction of the area, its wave's size (dp) and phase, and how strong it is. */
private val FLOW_Y = floatArrayOf(0.2f, 0.34f, 0.47f, 0.6f, 0.73f, 0.88f, 1.02f)
private val FLOW_AMP = floatArrayOf(11f, 15f, 12f, 16f, 10f, 13f, 9f)
private val FLOW_PHASE = floatArrayOf(0.4f, 1.9f, 3.1f, 4.4f, 5.5f, 0.9f, 2.5f)
private val FLOW_WEIGHT = floatArrayOf(0.62f, 1f, 0.8f, 0.95f, 0.6f, 0.72f, 0.5f)
private const val FLOW_DASH = 26f
private const val FLOW_STEPS = 28
private const val FLOW_STROKE = 1.5f
private const val FLOW_FULL_SPEED = 3.2
private const val FLOW_ALPHA = 0.34

private val HAIR_COLOR = Color(31, 42, 38).copy(alpha = 0.09f)

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

/**
 * A smart-home fan control as a 10-second film: a real 3D retro desk fan (seafoam enamel body, chrome cage, four brass
 * blades, four piano keys) on a studio sweep above a sheet with a segmented control. Each level spins the blades up or
 * down with inertia, presses its key on the base and strengthens the airflow lines; the head oscillates.
 *
 * Ported from the React Native block (react-native-filament): the same fan.glb and light in Filament, every moving node
 * posed from the loop's values (no imported animation). In the film the blade angle is a pure function of the loop's
 * time with exactly 20 turns per loop. The segmented control is Material 3. Picking a level sets it at once and the
 * motor chases it (tau 0.42 s up, 0.95 s down); drag sideways on the fan to aim the head. 2 s after the last touch the
 * fan goes Off, the blades coast to a quarter turn, and 1.3 s later the film restarts. Reduced motion: level 2 shown,
 * nothing spins or sways.
 */
@Composable
fun DeskFanSpeed(modifier: Modifier = Modifier, autoPlay: Boolean = true, onLevelChange: (Int) -> Unit = {}) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { DeskFanSpeedController(autoPlay, reduced) }
    val view = LocalView.current
    val changed by rememberUpdatedState(onLevelChange)

    // One clock for the Compose layers, read only in draw lambdas. The level and the two readings change only a few
    // times a second, and are read inside the sheet only, so the 3D stage never recomposes for them.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val level = remember(controller) { mutableIntStateOf(controller.level) }
    val rpm = remember(controller) { mutableIntStateOf(0) }
    val watts = remember(controller) { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                if (level.intValue != controller.level) level.intValue = controller.level
                val r = FanMotion.rpmAt(controller.speedNow)
                if (rpm.intValue != r) rpm.intValue = r
                val wt = FanMotion.wattsAt(controller.speedNow)
                if (watts.intValue != wt) watts.intValue = wt
            }
        }
    }
    val film: () -> Double = remember(controller) {
        {
            controller.frame(frameNanos / 1e6)
            controller.t
        }
    }
    val onLevel: (Int) -> Unit = { v ->
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        changed(v)
        level.intValue = v
        controller.userLevel(v)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(FanMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(260f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            DeskFanSpeedStage(controller, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Desk fan. Drag sideways to aim the fan" }
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
                                        controller.dragBegan()
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
                if (veil > 0f) drawRect(rgb(FanMotion.BACKDROP), alpha = veil)
                // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
                val shadeH = (top + 64) * density.density
                val b = rgb(FanMotion.BACKDROP)
                drawRect(
                    Brush.verticalGradient(0f to b, 0.55f to b.copy(alpha = 0.6f), 1f to b.copy(alpha = 0f), startY = 0f, endY = shadeH),
                    size = Size(size.width, shadeH),
                )
            }
        }

        Text(
            FanMotion.APP,
            style = type(17, 22, rgb(FanMotion.INK), FontWeight.SemiBold),
            modifier = Modifier.offset(x = GUTTER.dp, y = (top + 10).dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color(60, 48, 24).copy(alpha = 0.18f), offsetY = (-8).dp, blur = 24.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(FanMotion.PANEL))
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
            FanPanel(min(w, 560f) - GUTTER * 2, controller, level, rpm, watts, film, onLevel)
        }

        // the air: thin currents drifting left to right across the screen, as strong as the blades are fast
        FanAirflow(controller, film, Modifier.width(w.dp).height((stageH + 40).dp))
    }
}

/** Seven dashed currents: the paths are built once per size, the dash phase and alpha follow the blades. */
@Composable
private fun FanAirflow(controller: DeskFanSpeedController, film: () -> Double, modifier: Modifier) {
    val green = rgb(FanMotion.GREEN)
    Spacer(
        modifier.drawWithCache {
            val d = density
            val width = size.width / d
            val height = size.height / d
            val paths = List(FLOW_Y.size) { i ->
                Path().apply {
                    val y = height * FLOW_Y[i]
                    val amp = FLOW_AMP[i]
                    val phase = FLOW_PHASE[i]
                    for (s in 0..FLOW_STEPS) {
                        val k = s.toFloat() / FLOW_STEPS
                        val px = -20f + (width + 40f) * k
                        val py = y + amp * sin(k * PI * 2.2 + phase).toFloat() + amp * 0.35f * sin(k * PI * 5.1 + phase * 1.7).toFloat()
                        if (s == 0) moveTo(px * d, py * d) else lineTo(px * d, py * d)
                    }
                }
            }
            val period = FanMotion.FLOW_PERIOD * d
            onDrawBehind {
                film()
                val alpha = (FanMotion.clamp01(controller.speedNow / FLOW_FULL_SPEED) * FLOW_ALPHA).toFloat()
                if (alpha <= 0.002f) return@onDrawBehind
                // RN's phase -(frac(turns x 0.5) x 72), taken into 0..72 for the platform's dash effect
                val f = controller.turnsNow * FanMotion.FLOW_PER_TURN
                val frac = (f - floor(f)).toFloat()
                val phase = (1f - frac) * period
                for (i in paths.indices) {
                    val on = (FLOW_DASH * FLOW_WEIGHT[i] + 8f) * d
                    drawPath(
                        paths[i],
                        green,
                        alpha = alpha,
                        style = Stroke(
                            width = FLOW_STROKE * d,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, period - on), phase),
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun FanPanel(
    inner: Float,
    controller: DeskFanSpeedController,
    levelState: MutableIntState,
    rpm: MutableIntState,
    watts: MutableIntState,
    film: () -> Double,
    onLevel: (Int) -> Unit,
) {
    val hair = with(LocalDensity.current) { 1f.toDp() }
    Box(Modifier.width(inner.dp).height(PANEL_H.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = TOP.dp)) {
            val level = levelState.intValue
            Row(
                Modifier.fillMaxWidth().height(HEAD_H.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(FanMotion.ROOM, style = type(20, 26, rgb(FanMotion.INK), FontWeight.Bold, tracking = -0.3f))
                Text(if (level == 0) "Off" else "Speed $level", style = type(15, 20, rgb(FanMotion.MUTE), FontWeight.Medium, tabular = true))
            }
            FanLevelPicker(level, onLevel, Modifier.padding(top = GAP.dp).fillMaxWidth().height(PICKER_H.dp))
            Row(
                Modifier
                    .padding(top = GAP.dp)
                    .fillMaxWidth()
                    .height(STATS_H.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(rgb(FanMotion.CARD))
                    .border(hair, HAIR_COLOR, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Reading("Motor", "rpm", rpm, Modifier.weight(1f))
                Box(Modifier.width(hair).height(40.dp).background(HAIR_COLOR))
                Reading("Power", "W", watts, Modifier.weight(1f))
            }
        }
        // the film's finger on the segmented control
        val ghost = remember { DoubleArray(3) }
        Canvas(Modifier.fillMaxSize()) {
            val tt = film()
            if (!controller.filming) return@Canvas
            FanMotion.tapGhost(tt, ghost)
            val a = (ghost[0] * 0.9).toFloat()
            if (a <= 0.002f) return@Canvas
            val d = density
            val cell = inner / FanMotion.LEVELS.size
            val c = Offset(cell * (ghost[2].toFloat() + 0.5f) * d, (TOP + HEAD_H + GAP + PICKER_H / 2) * d)
            val k = 1 - 0.16f * ghost[1].toFloat()
            val r = DOT / 2 * k * d
            val ink = Color(47, 102, 87)
            drawCircle(ink, radius = r, center = c, alpha = 0.16f * a)
            drawCircle(ink, radius = r - 0.75f * k * d, center = c, alpha = 0.34f * a, style = Stroke(1.5f * k * d))
        }
    }
}

/** Off, 1, 2, 3: Material 3's single-choice segmented buttons (the system's segmented control on iOS). */
@Composable
private fun FanLevelPicker(level: Int, onLevel: (Int) -> Unit, modifier: Modifier) {
    val ink = rgb(FanMotion.INK)
    val mute = rgb(FanMotion.MUTE)
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = rgb(FanMotion.GREEN, 0.14f),
        activeContentColor = ink,
        activeBorderColor = rgb(FanMotion.GREEN, 0.34f),
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = mute,
        inactiveBorderColor = rgb(FanMotion.GREEN, 0.34f),
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleChoiceSegmentedButtonRow(modifier) {
            FanMotion.LEVELS.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == level,
                    onClick = { if (i != level) onLevel(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = FanMotion.LEVELS.size),
                    colors = colors,
                    contentPadding = PaddingValues(0.dp),
                    icon = {},
                    modifier = Modifier
                        .fillMaxHeight()
                        .semantics { contentDescription = if (i == 0) "Off" else "Speed $label" },
                ) {
                    Text(label, style = type(14, 18, if (i == level) ink else mute, if (i == level) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}

/** A number that follows the motor. */
@Composable
private fun Reading(label: String, unit: String, value: MutableIntState, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(label, style = type(13, 18, rgb(FanMotion.MUTE)))
        Row(Modifier.height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                value.intValue.toString(),
                style = type(26, 32, rgb(FanMotion.INK), FontWeight.Bold, tabular = true),
                modifier = Modifier.widthIn(min = 76.dp).alignByBaseline(),
            )
            Text(unit, style = type(14, 18, rgb(FanMotion.MUTE)), modifier = Modifier.alignByBaseline())
        }
    }
}
