package com.ngocdevv.nativecook.blocks.progress.espressobrewprogress

import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The film's period (s), as in espressoMotion.ts. */
const val LOOP = EspressoMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val TOP = 18f
private const val HEAD_H = 30f
private const val GAP = 14f
private const val CONTROLS_H = 40f
private const val PICKER_H = 36f
private const val STATS_H = 104f
private const val BOTTOM = 16f
private const val PANEL_H = TOP + HEAD_H + GAP + CONTROLS_H + GAP + STATS_H + BOTTOM
private const val BUTTON_W = 128f
private const val DOT = 44f

private val HAIR_COLOR = Color(34, 38, 42).copy(alpha = 0.09f)

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

private fun fixed1(v: Double): String = String.format(Locale.US, "%.1f", v)

/**
 * A coffee machine app pulling a shot, as a 10-second film: a real 3D espresso machine (slate enamel body, brushed
 * steel front, chrome group head with a light band, walnut portafilter, a pressure gauge) with a glass cup on the drip
 * tray, above a sheet with a segmented control, a Brew button and the shot's readings.
 *
 * Ported from the React Native block (react-native-filament): the same espresso.glb and light in Filament, every moving
 * node posed from the loop's values (no imported animation). Single/Double is Material 3's segmented buttons and Brew a
 * filled Material 3 button. Brew plays the film's timeline from 1.35 s to 7.75 s at the chosen dose and holds the full
 * cup (Brew again changes the cup first); drag sideways on the stage to look round the machine. 2 s after the last
 * touch a full cup is changed, the orbit springs home and 0.9 s later the film restarts. Reduced motion: a poured double,
 * nothing moves.
 */
@Composable
fun EspressoBrewProgress(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onBrew: (Int) -> Unit = {},
    onShotReady: () -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { EspressoBrewProgressController(autoPlay, reduced) }
    val view = LocalView.current
    val brewed by rememberUpdatedState(onBrew)
    val ready by rememberUpdatedState(onShotReady)

    // One clock for the Compose layers, read only in draw lambdas. The status, the dose and the readings change only a
    // few times a second and are read inside the sheet only, so the 3D stage never recomposes for them.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val status = remember(controller) { mutableIntStateOf(EspressoMotion.statusAt(controller.t)) }
    val doseShown = remember(controller) { mutableIntStateOf(controller.doseSel) }
    val readings = remember(controller) { Array(3) { mutableStateOf("0.0") } }
    LaunchedEffect(controller) {
        var prevStatus = status.intValue
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                val tt = controller.t
                val s = EspressoMotion.statusAt(tt)
                if (s != prevStatus) {
                    // a finger's shot is done: one tap of the haptics, and the host hears of it
                    if (s == 3 && !controller.filming) {
                        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                        view.performHapticFeedback(c)
                        ready()
                    }
                    prevStatus = s
                }
                if (status.intValue != s) status.intValue = s
                if (doseShown.intValue != controller.doseSel) doseShown.intValue = controller.doseSel
                val time = fixed1(EspressoMotion.shotSecondsAt(tt))
                val yield = fixed1(EspressoMotion.lerp(EspressoMotion.DOSE_G[0].toDouble(), EspressoMotion.DOSE_G[1].toDouble(), controller.doseNow) * EspressoMotion.fillAt(tt))
                val bar = fixed1(EspressoMotion.barAt(tt))
                if (readings[0].value != time) readings[0].value = time
                if (readings[1].value != yield) readings[1].value = yield
                if (readings[2].value != bar) readings[2].value = bar
            }
        }
    }
    val film: () -> Double = remember(controller) {
        {
            controller.frame(frameNanos / 1e6)
            controller.t
        }
    }
    val onDose: (Int) -> Unit = { v ->
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        doseShown.intValue = v
        controller.userDose(v)
    }
    val onBrewPress: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        brewed(controller.doseSel)
        controller.userBrew()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(EspressoMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(260f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            EspressoBrewProgressStage(controller, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Espresso machine. Drag sideways to look round the machine" }
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
                                    // RNGH: activeOffsetX ±8, failOffsetY ±24
                                    if (abs(dy) / d > 24) break
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
                if (veil > 0f) drawRect(rgb(EspressoMotion.BACKDROP), alpha = veil)
                // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
                val shadeH = (top + 64) * density.density
                val b = rgb(EspressoMotion.BACKDROP)
                drawRect(
                    Brush.verticalGradient(0f to b, 0.55f to b.copy(alpha = 0.6f), 1f to b.copy(alpha = 0f), startY = 0f, endY = shadeH),
                    size = Size(size.width, shadeH),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = GUTTER.dp).offset(y = (top + 10).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(EspressoMotion.APP, style = type(17, 22, rgb(EspressoMotion.INK), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
            Text(EspressoMotion.MACHINE, style = type(15, 20, rgb(EspressoMotion.MUTE)), modifier = Modifier.alignByBaseline())
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color(60, 44, 24).copy(alpha = 0.16f), offsetY = (-8).dp, blur = 24.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(EspressoMotion.PANEL))
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
            EspressoPanel(min(w, 560f) - GUTTER * 2, controller, status, doseShown, readings, film, onDose, onBrewPress)
        }
    }
}

private fun buttonLabel(status: Int): String = when (status) {
    0 -> "Brew"
    3 -> "Brew again"
    else -> "Brewing"
}

@Composable
private fun EspressoPanel(
    inner: Float,
    controller: EspressoBrewProgressController,
    statusState: MutableIntState,
    doseState: MutableIntState,
    readings: Array<MutableState<String>>,
    film: () -> Double,
    onDose: (Int) -> Unit,
    onBrew: () -> Unit,
) {
    val hair = with(LocalDensity.current) { 1f.toDp() }
    val pickerW = inner - BUTTON_W - 12
    Box(Modifier.width(inner.dp).height(PANEL_H.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = TOP.dp)) {
            val status = statusState.intValue
            val dose = doseState.intValue
            Row(
                Modifier.fillMaxWidth().height(HEAD_H.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(EspressoMotion.RECIPE, style = type(20, 26, rgb(EspressoMotion.INK), FontWeight.Bold, tracking = -0.3f))
                Text(
                    EspressoMotion.STATUS[status],
                    style = type(15, 20, rgb(if (status == 2) EspressoMotion.ACCENT else EspressoMotion.MUTE), FontWeight.Medium),
                )
            }
            Row(
                Modifier.padding(top = GAP.dp).fillMaxWidth().height(CONTROLS_H.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DosePicker(dose, onDose, Modifier.width(pickerW.dp).height(PICKER_H.dp))
                val busy = status == 1 || status == 2 || status == 4
                Box(Modifier.width(BUTTON_W.dp), contentAlignment = Alignment.Center) {
                    BrewButton(buttonLabel(status), busy, onBrew)
                }
            }
            Column(
                Modifier
                    .padding(top = GAP.dp)
                    .fillMaxWidth()
                    .height(STATS_H.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(rgb(EspressoMotion.CARD))
                    .border(hair, HAIR_COLOR, RoundedCornerShape(16.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Reading("Time", "s", readings[0], Modifier.weight(1f))
                    Box(Modifier.padding(horizontal = 12.dp).width(hair).height(36.dp).background(HAIR_COLOR))
                    Reading("Yield", "g", readings[1], Modifier.weight(1f))
                    Box(Modifier.padding(horizontal = 12.dp).width(hair).height(36.dp).background(HAIR_COLOR))
                    Reading("Pressure", "bar", readings[2], Modifier.weight(1f))
                }
                // the cup's fill, as a bar that grows from the left
                Spacer(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(rgb(EspressoMotion.ACCENT, 0.14f))
                        .drawWithContent {
                            val k = EspressoMotion.fillAt(film()).toFloat()
                            if (k <= 0f) return@drawWithContent
                            val r = 2.dp.toPx()
                            drawRoundRect(rgb(EspressoMotion.ACCENT), size = Size(size.width * k, size.height), cornerRadius = CornerRadius(r * k, r))
                        },
                )
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val g = EspressoMotion.DOSE_G[dose]
                    Text("1 : 2 ratio · ${g / 2} g in", style = type(12, 16, rgb(EspressoMotion.MUTE), tabular = true))
                    Text("Target $g g", style = type(12, 16, rgb(EspressoMotion.MUTE), tabular = true))
                }
            }
        }
        // the film's finger on the segmented control and the button
        val ghost = remember { DoubleArray(3) }
        Canvas(Modifier.fillMaxSize()) {
            val tt = film()
            if (!controller.filming) return@Canvas
            EspressoMotion.tapGhost(tt, ghost)
            val a = (ghost[0] * 0.9).toFloat()
            if (a <= 0.002f) return@Canvas
            val d = density
            val x = if (ghost[2] == 2.0) inner - BUTTON_W / 2 else pickerW * (if (ghost[2] == 0.0) 0.25f else 0.75f)
            val c = Offset(x * d, (TOP + HEAD_H + GAP + CONTROLS_H / 2) * d)
            val k = 1 - 0.16f * ghost[1].toFloat()
            val r = DOT / 2 * k * d
            val accent = rgb(EspressoMotion.ACCENT)
            drawCircle(accent, radius = r, center = c, alpha = 0.16f * a)
            drawCircle(accent, radius = r - 0.75f * k * d, center = c, alpha = 0.38f * a, style = Stroke(1.5f * k * d))
        }
    }
}

/** Single / Double: Material 3's single-choice segmented buttons (the system's segmented control on iOS). */
@Composable
private fun DosePicker(dose: Int, onDose: (Int) -> Unit, modifier: Modifier) {
    val ink = rgb(EspressoMotion.INK)
    val mute = rgb(EspressoMotion.MUTE)
    val edge = Color(118, 116, 110).copy(alpha = 0.3f)
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = rgb(EspressoMotion.CARD),
        activeContentColor = ink,
        activeBorderColor = edge,
        inactiveContainerColor = Color(118, 116, 110).copy(alpha = 0.16f),
        inactiveContentColor = mute,
        inactiveBorderColor = edge,
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleChoiceSegmentedButtonRow(modifier) {
            EspressoMotion.DOSES.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == dose,
                    onClick = { if (i != dose) onDose(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = EspressoMotion.DOSES.size),
                    colors = colors,
                    contentPadding = PaddingValues(0.dp),
                    icon = {},
                    modifier = Modifier.fillMaxHeight().semantics { contentDescription = label },
                ) {
                    Text(label, style = type(14, 18, if (i == dose) ink else mute, if (i == dose) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}

/** Brew: a filled Material 3 capsule in the accent (the system's prominent button on iOS), disabled while brewing. */
@Composable
private fun BrewButton(label: String, busy: Boolean, onBrew: () -> Unit) {
    val panel = rgb(EspressoMotion.PANEL)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onBrew,
            enabled = !busy,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = rgb(EspressoMotion.ACCENT),
                contentColor = panel,
                disabledContainerColor = rgb(EspressoMotion.INK, 0.06f),
                disabledContentColor = rgb(EspressoMotion.INK, 0.3f),
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(36.dp).wrapContentWidth(unbounded = true),
        ) {
            Icon(if (busy) Icons.Filled.WaterDrop else Icons.Filled.Coffee, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = type(15, 20, if (busy) rgb(EspressoMotion.INK, 0.3f) else panel, FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** A number that follows the shot. */
@Composable
private fun Reading(label: String, unit: String, value: MutableState<String>, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = type(13, 18, rgb(EspressoMotion.MUTE)))
        Row(Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value.value,
                style = type(22, 28, rgb(EspressoMotion.INK), FontWeight.Bold, tabular = true),
                modifier = Modifier.widthIn(min = 44.dp).alignByBaseline(),
                maxLines = 1,
            )
            Text(unit, style = type(13, 18, rgb(EspressoMotion.MUTE)), modifier = Modifier.alignByBaseline())
        }
    }
}
