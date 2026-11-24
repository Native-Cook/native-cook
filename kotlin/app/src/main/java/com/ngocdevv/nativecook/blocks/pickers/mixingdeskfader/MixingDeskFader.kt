package com.ngocdevv.nativecook.blocks.pickers.mixingdeskfader

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** The film's period (s), as in mixerMotion.ts. */
const val LOOP = MixerMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val TOP = 18f
private const val HEAD_H = 30f
private const val GAP = 14f
private const val PICKER_H = 36f
private const val STRIPS_H = 62f
private const val BOTTOM = 16f
private const val PANEL_H = TOP + HEAD_H + GAP + PICKER_H + GAP + STRIPS_H + BOTTOM
private const val DOT = 44f

/** The REC light breathes this many times a loop. */
private const val REC_BREATHS = 3

/** RNGH: the pan activates after 6 dp vertically and fails past 40 dp sideways; a tap fails past 10 dp or 500 ms. */
private const val PAN_ACTIVE = 6f
private const val PAN_FAIL_X = 40f
private const val TAP_SLOP = 10f
private const val TAP_MAX_MS = 500L

private val HAIR_COLOR = Color(243, 240, 234).copy(alpha = 0.09f)

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
 * A podcast recording app's desk as a 10-second film: a real 3D mixing desk (brushed aluminium deck between walnut
 * cheeks, four faders with soft caps, gain knobs, lit mute buttons and eight-LED meters) on a dark studio sweep above a
 * sheet with the presets and each channel's level. The film rides Music down, brings Host to 0 dB, mutes SFX and
 * recalls two presets; the meters follow the signal all the time.
 *
 * Ported from the React Native block (react-native-filament): the same mixer.glb and light in Filament, every moving
 * node and every LED posed from the loop's values (no imported animation). The presets are Material 3's segmented
 * buttons. Drag a fader (the nearest projected track; a detent at 0 dB) or tap a mute button; 2 s after the last touch
 * Intro is recalled and 1.5 s later the film restarts. Reduced motion: the Interview mix with the meters still.
 */
@Composable
fun MixingDeskFader(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onFader: (channel: Int, value: Double) -> Unit = { _, _ -> },
    onMute: (channel: Int, muted: Boolean) -> Unit = { _, _ -> },
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { MixingDeskFaderController(autoPlay, reduced) }
    val view = LocalView.current
    val faderChanged by rememberUpdatedState(onFader)
    val muteChanged by rememberUpdatedState(onMute)

    // One clock for the Compose layers, read only in draw lambdas. The preset and the four levels change only a few
    // times a second and are read inside the sheet only, so the 3D stage never recomposes for them.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val preset = remember(controller) { mutableIntStateOf(controller.preset) }
    val labels = remember(controller) {
        Array(MixerMotion.CHANNELS) { mutableStateOf(MixerMotion.dbLabel(controller.pose[it], controller.pose[8 + it])) }
    }
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                if (preset.intValue != controller.preset) preset.intValue = controller.preset
                val p = controller.pose
                for (c in labels.indices) {
                    val s = MixerMotion.dbLabel(p[c], p[8 + c])
                    if (labels[c].value != s) labels[c].value = s
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
    val onPreset: (Int) -> Unit = { p ->
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        preset.intValue = p
        controller.userPreset(p)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(MixerMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(260f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            MixingDeskFaderStage(controller, Modifier.fillMaxSize())
            val ghost = remember { DoubleArray(4) }
            val spot = remember { DoubleArray(2) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Mixing desk. Drag a fader up or down; tap a mute button" }
                    .pointerInput(controller, w, stageH) {
                        val d = this.density
                        val sw = w.toDouble()
                        val sh = stageH.toDouble()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            controller.dragDown((down.position.x / d).toDouble(), (down.position.y / d).toDouble(), sw, sh)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dx = 0f
                            var dy = 0f
                            var active = false
                            var failed = false
                            var tapOk = true
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.positionChange()
                                dx += delta.x
                                dy += delta.y
                                if (abs(dx) / d > TAP_SLOP || abs(dy) / d > TAP_SLOP) tapOk = false
                                if (!active && !failed) {
                                    if (abs(dx) / d > PAN_FAIL_X) {
                                        failed = true
                                    } else if (abs(dy) / d >= PAN_ACTIVE) {
                                        active = true
                                        tapOk = false
                                        controller.dragBegan()
                                    }
                                }
                                if (active) {
                                    if (controller.dragMoved((dx / d).toDouble(), (dy / d).toDouble())) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (active) {
                                        val v = tracker.calculateVelocity()
                                        val c = controller.dragEnded((v.x / d).toDouble(), (v.y / d).toDouble())
                                        if (c >= 0) faderChanged(c, controller.releasedTo)
                                    } else if (tapOk && change.uptimeMillis - down.uptimeMillis <= TAP_MAX_MS) {
                                        val r = controller.tap((change.position.x / d).toDouble(), (change.position.y / d).toDouble(), sw, sh)
                                        if (r >= 0) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            muteChanged(r / 2, r % 2 == 1)
                                        }
                                    }
                                    break
                                }
                            }
                            controller.dragFinalized()
                        }
                    },
            ) {
                val tt = film()
                val veil = 1f - controller.shown.toFloat()
                if (veil > 0f) drawRect(rgb(MixerMotion.BACKDROP), alpha = veil)
                val dd = this.density
                // the film's finger on the desk, on the cap it rides or the button it taps
                if (controller.filming) {
                    MixerMotion.deskGhost(tt, ghost)
                    val a = (ghost[0] * 0.9).toFloat()
                    if (a > 0.002f) {
                        val c = ghost[2].toInt()
                        val sw = (size.width / dd).toDouble()
                        val sh = (size.height / dd).toDouble()
                        if (ghost[3] > 0.5) {
                            MixerMotion.muteOnScreen(c, controller.leanNow, sw, sh, spot)
                        } else {
                            MixerMotion.capOnScreen(c, controller.pose[c], controller.leanNow, sw, sh, spot)
                        }
                        val k = 1 - 0.16f * ghost[1].toFloat()
                        val r = DOT / 2 * k * dd
                        val center = Offset(spot[0].toFloat() * dd, spot[1].toFloat() * dd)
                        val ink = rgb(MixerMotion.INK)
                        drawCircle(ink, radius = r, center = center, alpha = 0.18f * a)
                        drawCircle(ink, radius = r - 0.75f * k * dd, center = center, alpha = 0.6f * a, style = Stroke(1.5f * k * dd))
                    }
                }
                // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
                val shadeH = (top + 64) * dd
                val b = rgb(MixerMotion.BACKDROP)
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
            Text(MixerMotion.APP, style = type(17, 22, rgb(MixerMotion.INK), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
            Text(MixerMotion.STUDIO, style = type(15, 20, rgb(MixerMotion.MUTE)), modifier = Modifier.alignByBaseline())
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.45f), offsetY = (-8).dp, blur = 28.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(MixerMotion.PANEL))
                // the sheet takes its touches: the stage runs a little way under its corners
                .pointerInput(Unit) {}
                .drawWithContent {
                    drawContent()
                    val r = PANEL_R.dp.toPx()
                    clipRect(bottom = r) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.14f),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(size.width - 1f, r * 3),
                            cornerRadius = CornerRadius(r - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            MixerPanel(min(w, 560f) - GUTTER * 2, controller, preset, labels, film, onPreset)
        }
    }
}

@Composable
private fun MixerPanel(
    inner: Float,
    controller: MixingDeskFaderController,
    presetState: MutableIntState,
    labels: Array<MutableState<String>>,
    film: () -> Double,
    onPreset: (Int) -> Unit,
) {
    val hair = with(LocalDensity.current) { 1f.toDp() }
    Box(Modifier.width(inner.dp).height(PANEL_H.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = TOP.dp)) {
            Row(
                Modifier.fillMaxWidth().height(HEAD_H.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(MixerMotion.EPISODE, style = type(20, 26, rgb(MixerMotion.INK), FontWeight.Bold, tracking = -0.3f))
                RecPill(film)
            }
            PresetPicker(presetState.intValue, onPreset, Modifier.padding(top = GAP.dp).fillMaxWidth().height(PICKER_H.dp))
            Row(
                Modifier
                    .padding(top = GAP.dp)
                    .fillMaxWidth()
                    .height(STRIPS_H.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(rgb(MixerMotion.CARD))
                    .border(hair, HAIR_COLOR, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (c in 0 until MixerMotion.CHANNELS) {
                    Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        if (c > 0) Box(Modifier.width(hair).height(34.dp).background(HAIR_COLOR))
                        Strip(c, labels[c], Modifier.weight(1f))
                    }
                }
            }
        }
        // the film's finger on the presets
        val ghost = remember { DoubleArray(3) }
        Canvas(Modifier.fillMaxSize()) {
            val tt = film()
            if (!controller.filming) return@Canvas
            MixerMotion.presetGhost(tt, ghost)
            val a = (ghost[0] * 0.9).toFloat()
            if (a <= 0.002f) return@Canvas
            val d = density
            val cell = inner / MixerMotion.PRESET_NAMES.size
            val c = Offset(cell * (ghost[2].toFloat() + 0.5f) * d, (TOP + HEAD_H + GAP + PICKER_H / 2) * d)
            val k = 1 - 0.16f * ghost[1].toFloat()
            val r = DOT / 2 * k * d
            val ink = rgb(MixerMotion.INK)
            drawCircle(ink, radius = r, center = c, alpha = 0.14f * a)
            drawCircle(ink, radius = r - 0.75f * k * d, center = c, alpha = 0.5f * a, style = Stroke(1.5f * k * d))
        }
    }
}

/** The REC pill; its dot breathes three times a loop. */
@Composable
private fun RecPill(film: () -> Double) {
    val red = rgb(MixerMotion.REC)
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(rgb(MixerMotion.REC, 0.14f))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier.size(8.dp).clip(CircleShape).drawBehind {
                val tt = film()
                val o = 0.45 + 0.55 * (0.5 + 0.5 * cos(2 * PI * REC_BREATHS * tt / MixerMotion.LOOP))
                drawRect(red, alpha = o.toFloat())
            },
        )
        Text("REC", style = type(13, 16, red, FontWeight.Bold, tracking = 0.4f))
    }
}

/** Intro, Interview, Outro: Material 3's single-choice segmented buttons (the system's segmented control on iOS). */
@Composable
private fun PresetPicker(preset: Int, onPreset: (Int) -> Unit, modifier: Modifier) {
    val ink = rgb(MixerMotion.INK)
    val mute = rgb(MixerMotion.MUTE)
    val edge = Color(118, 116, 110).copy(alpha = 0.36f)
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = rgb(MixerMotion.CARD),
        activeContentColor = ink,
        activeBorderColor = edge,
        inactiveContainerColor = Color(118, 116, 110).copy(alpha = 0.24f),
        inactiveContentColor = mute,
        inactiveBorderColor = edge,
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleChoiceSegmentedButtonRow(modifier) {
            MixerMotion.PRESET_NAMES.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == preset,
                    onClick = { if (i != preset) onPreset(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = MixerMotion.PRESET_NAMES.size),
                    colors = colors,
                    contentPadding = PaddingValues(0.dp),
                    icon = {},
                    modifier = Modifier.fillMaxHeight().semantics { contentDescription = "$label preset" },
                ) {
                    Text(label, style = type(14, 18, if (i == preset) ink else mute, if (i == preset) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}

/** One channel's name and level. */
@Composable
private fun Strip(c: Int, label: MutableState<String>, modifier: Modifier) {
    Column(modifier.padding(start = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(rgb(MixerMotion.CHANNEL_COLOURS[c])))
            Text(MixerMotion.NAMES[c], style = type(13, 18, rgb(MixerMotion.MUTE)))
        }
        Text(
            label.value,
            style = type(15, 20, rgb(MixerMotion.INK), FontWeight.SemiBold, tabular = true),
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}
