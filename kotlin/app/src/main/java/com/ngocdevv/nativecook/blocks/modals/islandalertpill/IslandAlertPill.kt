package com.ngocdevv.nativecook.blocks.modals.islandalertpill

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A device of the page. [charge] is in percent; the first device follows the island's charge
 * instead. [tint] is the background of the icon's tile and [ink] the colour of its glyph.
 */
class IslandAlertPillDevice(
    val name: String,
    val detail: String,
    val charge: Int,
    val icon: ImageVector,
    val tint: Color,
    val ink: Color,
)

val IslandAlertPillDevices = listOf(
    IslandAlertPillDevice("This Phone", "Low Power Mode", 10, Icons.Rounded.Smartphone, Color(0xFFFFE3E0), Color(0xFFFF3B30)),
    IslandAlertPillDevice("Watch", "On wrist · 2 days left", 64, Icons.Rounded.Watch, Color(0xFFE2ECFF), Color(0xFF2F6BFF)),
    IslandAlertPillDevice("Earbuds", "In case · Connected", 82, Icons.Rounded.Headphones, Color(0xFFE0F5E6), Color(0xFF24A148)),
    IslandAlertPillDevice("Tablet", "At home · Charging", 47, Icons.Rounded.TabletAndroid, Color(0xFFFFF1D6), Color(0xFFE08A00)),
    IslandAlertPillDevice("Keyboard", "Paired · Desk", 91, Icons.Rounded.Dialpad, Color(0xFFEEE6FF), Color(0xFF7A4DFF)),
    IslandAlertPillDevice("Pencil", "Paired · Last used today", 28, Icons.Rounded.Edit, Color(0xFFDDF4F6), Color(0xFF0F9FAE)),
)

// How many devices the widget at the top shows as rings.
private const val RINGS = 4

// Below this a charge is drawn red.
private const val LOW = 20
private const val RING_SIZE = 62f
private const val RING_STROKE = 6f
private const val ROW_HEIGHT = 62

// The timeline of one loop, in seconds from its start: the pill rests, the alert grows it into the
// island, the track draws, the battery charges, the knob snaps back, the island shrinks.
const val LOOP = 9f
private const val PULSE_AT = 0f
private const val EXPAND_AT = 0.8f
private const val DRAW_AT = 1.6f
private const val CHARGE_AT = 2.4f
private const val CHARGE_FOR = 2.5f
private const val SNAP_AT = 5.4f
private const val COLLAPSE_AT = 6.2f

// After a touch the loop waits this long, then starts again from the compact pill.
private const val USER_HOLD = 3f

private const val PULSE_MS = 400
private const val PULSE_LOW = 0.5f
private const val SHAKE_MS = 40
private const val SHAKE_POINTS = 2f

// Springs as damping ratio and stiffness ((2π / duration)², for the 450, 350 and 700 ms ones).
private const val EXPAND_DAMPING = 0.72f
private const val COLLAPSE_DAMPING = 0.85f
private const val SPRING_STIFFNESS = 195f
private const val POP_DAMPING = 0.6f
private const val POP_STIFFNESS = 322f
private const val SNAP_DAMPING = 0.45f
private const val SNAP_STIFFNESS = 81f
private const val DRAW_MS = 500
private const val LABEL_MS = 300
private const val CONTENT_OUT_MS = 150
private const val FADE_MS = 200
private const val BREATH_MS = 600
private const val GLOW_LOW = 0.55f
private const val GLOW_REST = 0.8f

private const val COMPACT_WIDTH = 280f
private const val COMPACT_HEIGHT = 40f
private const val EXPANDED_HEIGHT = 100f
private const val EXPANDED_MARGIN = 20f
private const val EXPANDED_GAP = 51f
private const val COMPACT_RADIUS = 20f
private const val EXPANDED_RADIUS = 40f
private const val SIDE_PADDING = 20f
private const val LABEL_WIDTH = 74f
private const val TRACK_HEIGHT = 8f
private const val CHIP_WIDTH = 44f
private const val CHIP_HEIGHT = 24f
private const val GRAB_HEIGHT = 48f

private val Red = Color(0xFFFF3B30)
private val Track = Color(0xFF333333)
private val PageFill = Color(0xFFF2F2F5)
private val Green = Color(0xFF34C759)
private val RingTrack = Color(0xFFECECF0)
private val Ink = Color(0xFF111114)
private val Mute = Color(0xFF7C7C83)
private val Rule = Color(0x383C3C43)
private val BatteryEdge = Color(0x593C3C43)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun pillText(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    tabular: Boolean = false,
    tracking: Float = 0f,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val GlowShadow = SoftRoundRect(Red.copy(alpha = 0.7f), 9f)
// The island's two shadows: a tight one under it and a wide one further down.
private val IslandShadowNear = SoftRoundRect(Color.Black.copy(alpha = 0.16f), 4f)
private val IslandShadowFar = SoftRoundRect(Color.Black.copy(alpha = 0.24f), 20f)

/** A number that moves on its own curve; a new target starts from wherever the number is. */
private class Channel(initial: Float, private val scope: CoroutineScope) {
    private val animatable = Animatable(initial)
    private var job: Job? = null
    val value get() = animatable.value

    fun go(delayMs: Int = 0, run: suspend Animatable<Float, AnimationVector1D>.() -> Unit) {
        job?.cancel()
        job = scope.launch {
            if (delayMs > 0) delay(delayMs.toLong())
            animatable.run()
        }
    }

    fun set(value: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { animatable.snapTo(value) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private class Step(val at: Float, val run: () -> Unit)

/**
 * A page and, on top of it, a dynamic-island pill showing a low battery. An alert grows the pill into
 * an island with a track, a knob that carries the percent, and the time left; the knob can be
 * dragged to preview a charge and springs back. A long press expands the pill, a tap on the page
 * shrinks it.
 *
 * @param autoPlay Plays the alert by itself, in a loop, and leaves the pill compact.
 * @param level The charge the alert opens at, in percent.
 * @param chargedTo The charge the autoPlay run reaches.
 * @param remaining Written at the right of the expanded island.
 * @param devices What the page under the island lists.
 * @param onLevelChange Called with the percent while the knob is dragged.
 */
@Composable
fun IslandAlertPill(
    autoPlay: Boolean = false,
    level: Float = 10f,
    chargedTo: Float = 38f,
    remaining: String = "~15m",
    devices: List<IslandAlertPillDevice> = IslandAlertPillDevices,
    onLevelChange: ((percent: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val resting by rememberUpdatedState(level)
    val target by rememberUpdatedState(chargedTo)
    val reportLevel by rememberUpdatedState(onLevelChange)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var isOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    var lastReported by remember { mutableIntStateOf(-1) }

    // 0 is the compact pill, 1 the island; the spring may pass either end.
    val grow = remember { Channel(0f, scope) }
    // How much of the track is drawn, how far the knob has popped in, and the label's opacity.
    val draw = remember { Channel(0f, scope) }
    val pop = remember { Channel(0f, scope) }
    val label = remember { Channel(0f, scope) }
    val pulse = remember { Channel(1f, scope) }
    val shake = remember { Channel(0f, scope) }
    val glow = remember { Channel(GLOW_REST, scope) }
    val charge = remember { Channel(level, scope) }
    val percent by remember { derivedStateOf { charge.value.coerceIn(0f, 100f).roundToInt() } }

    fun expand() {
        if (expanded) return
        expanded = true
        isOpen = true
        if (reduced) {
            grow.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
        } else {
            grow.go { animateTo(1f, spring(EXPAND_DAMPING, SPRING_STIFFNESS)) }
            shake.go {
                for (point in listOf(SHAKE_POINTS, -SHAKE_POINTS, SHAKE_POINTS, -SHAKE_POINTS, 0f)) {
                    animateTo(point, tween(SHAKE_MS, easing = EaseInOutQuad))
                }
            }
        }
        val wait = ((DRAW_AT - EXPAND_AT) * 1000).roundToInt()
        draw.go(wait) { animateTo(1f, tween(if (reduced) FADE_MS else DRAW_MS, easing = EaseOutCubic)) }
        pop.go(wait + DRAW_MS / 2) {
            if (reduced) animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) else animateTo(1f, spring(POP_DAMPING, POP_STIFFNESS))
        }
        label.go(wait + (DRAW_MS * 0.6f).roundToInt()) { animateTo(1f, tween(LABEL_MS, easing = EaseInOutQuad)) }
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        glow.set(GLOW_REST)
        for (channel in listOf(draw, pop, label)) channel.go { animateTo(0f, tween(CONTENT_OUT_MS, easing = EaseInOutQuad)) }
        charge.go { animateTo(resting, tween(CONTENT_OUT_MS, easing = EaseInOutQuad)) }
        if (reduced) {
            grow.go {
                animateTo(0f, tween(FADE_MS, easing = EaseInOutQuad))
                if (!expanded) isOpen = false
            }
        } else {
            grow.go(CONTENT_OUT_MS) {
                animateTo(0f, spring(COLLAPSE_DAMPING, SPRING_STIFFNESS))
                if (!expanded) isOpen = false
            }
        }
    }

    fun startCharge() {
        glow.go {
            animateTo(GLOW_LOW, tween(BREATH_MS, easing = EaseInOutSine))
            while (true) {
                animateTo(1f, tween(BREATH_MS, easing = EaseInOutSine))
                animateTo(GLOW_LOW, tween(BREATH_MS, easing = EaseInOutSine))
            }
        }
        charge.go { animateTo(target, tween((CHARGE_FOR * 1000).toInt(), easing = LinearEasing)) }
    }

    fun snapBack() {
        glow.go { animateTo(GLOW_REST, tween(FADE_MS, easing = LinearEasing)) }
        charge.go {
            if (reduced) animateTo(resting, tween(FADE_MS, easing = LinearEasing)) else animateTo(resting, spring(SNAP_DAMPING, SNAP_STIFFNESS))
        }
    }

    fun pulseOnce() {
        pulse.go {
            animateTo(PULSE_LOW, tween(PULSE_MS, easing = EaseInOutQuad))
            animateTo(1f, tween(PULSE_MS, easing = EaseInOutQuad))
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            collapse()
            delay(600)
        }
        val steps = listOf(
            Step(PULSE_AT) { pulseOnce() },
            Step(EXPAND_AT) { expand() },
            Step(CHARGE_AT) { startCharge() },
            Step(SNAP_AT) { snapBack() },
            Step(COLLAPSE_AT) { collapse() },
        )
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (step in steps) {
                until(step.at)
                step.run()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The island takes the place of the status bar, which is hidden while the block is on screen.
    val window = view.context.activity()?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PageFill)
            .pointerInput(autoPlay) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (autoPlay) touches++
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding().value
        val compactTop = if (top >= 54f) 10f else top + 6f
        val expandedTop = compactTop + EXPANDED_GAP
        val expandedWidth = maxWidth.value - EXPANDED_MARGIN * 2
        val trackWidth = expandedWidth - SIDE_PADDING * 2 - LABEL_WIDTH

        Page(devices, percent, expandedTop + EXPANDED_HEIGHT + 20f, onTap = ::collapse)

        Island(
            grow, draw, pop, label, pulse, shake, glow, charge, percent,
            compactTop = compactTop, expandedTop = expandedTop, expandedWidth = expandedWidth, trackWidth = trackWidth,
            screenWidth = maxWidth.value, remaining = remaining, isOpen = isOpen,
            onLongPress = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                expand()
            },
            onGrab = { x ->
                glow.set(1f)
                charge.set((x / trackWidth * 100f).coerceIn(0f, 100f))
            },
            onDrag = { x ->
                val percentNow = (x / trackWidth * 100f).coerceIn(0f, 100f)
                charge.set(percentNow)
                val rounded = percentNow.roundToInt()
                if (rounded != lastReported) {
                    lastReported = rounded
                    reportLevel?.invoke(rounded)
                }
            },
            onRelease = {
                glow.go { animateTo(GLOW_REST, tween(FADE_MS, easing = LinearEasing)) }
                charge.go { animateTo(resting, spring(SNAP_DAMPING, SNAP_STIFFNESS)) }
            },
        )
    }
}

@Composable
private fun Page(devices: List<IslandAlertPillDevice>, percent: Int, top: Float, onTap: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .semantics { contentDescription = "Page" }
            .padding(start = 20.dp, end = 20.dp, top = top.dp),
    ) {
        BasicText("Batteries", Modifier.height(40.dp), style = pillText(34f, 40f, FontWeight.ExtraBold, Ink, tracking = -1f))
        BasicText(
            "${devices.size} devices · Updated just now",
            Modifier.padding(top = 2.dp).height(20.dp),
            style = pillText(15f, 20f, FontWeight.Normal, Mute),
        )

        Row(
            Modifier.padding(top = 16.dp).card().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            devices.take(RINGS).forEachIndexed { i, device ->
                val value = if (i == 0) percent else device.charge
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(RING_SIZE.dp).drawBehind { ring(value) }, contentAlignment = Alignment.Center) {
                        Icon(device.icon, contentDescription = null, Modifier.size(24.dp), tint = Ink)
                    }
                    BasicText(
                        "$value%",
                        Modifier.height(20.dp),
                        style = pillText(15f, 20f, FontWeight.Bold, if (value < LOW) Red else Ink, tabular = true, tracking = -0.2f),
                    )
                }
            }
        }

        BasicText(
            "DEVICES",
            Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp).height(18.dp),
            style = pillText(13f, 18f, FontWeight.SemiBold, Mute, tracking = 0.4f),
        )
        Column(Modifier.card()) {
            devices.forEachIndexed { i, device ->
                DeviceRow(device, if (i == 0) percent else device.charge, ruled = i > 0)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: IslandAlertPillDevice, value: Int, ruled: Boolean) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(device.tint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(device.icon, contentDescription = null, Modifier.size(21.dp), tint = device.ink)
        }
        Row(
            Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .fillMaxHeight()
                .drawBehind { if (ruled) drawRect(Rule, size = Size(size.width, 1f)) }
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(device.name, maxLines = 1, style = pillText(17f, 22f, FontWeight.SemiBold, Ink, tracking = -0.3f))
                BasicText(device.detail, maxLines = 1, style = pillText(13f, 17f, FontWeight.Normal, Mute))
            }
            BasicText(
                "$value%",
                Modifier.padding(end = 8.dp),
                style = pillText(15f, 20f, FontWeight.SemiBold, if (value < LOW) Red else Ink, tabular = true),
            )
            BatteryGlyph(value)
        }
    }
}

/** A battery ring of the widget: a grey track and the charge drawn clockwise from the top. */
private fun DrawScope.ring(value: Int) {
    val stroke = RING_STROKE.dp.toPx()
    val inset = stroke / 2
    val box = Size(size.width - stroke, size.height - stroke)
    drawArc(RingTrack, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
    drawArc(
        if (value < LOW) Red else Green,
        -90f,
        360f * value.coerceIn(0, 100) / 100f,
        false,
        Offset(inset, inset),
        box,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}

/** The small horizontal battery at the end of a row. */
@Composable
private fun BatteryGlyph(value: Int) {
    val colour = if (value < LOW) Red else if (value > 80) Green else Ink
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(25.dp, 12.dp)
                .border(1.dp, BatteryEdge, RoundedCornerShape(3.5.dp))
                .padding(2.5.dp),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(max(8, value) / 100f).background(colour, RoundedCornerShape(2.dp)))
        }
        Box(
            Modifier
                .padding(start = 1.dp)
                .size(2.dp, 4.dp)
                .background(BatteryEdge, RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp)),
        )
    }
}

/** The white card of the page, with a hairline and a soft shadow. */
private fun Modifier.card(): Modifier {
    val shape = RoundedCornerShape(26.dp)
    return fillMaxWidth()
        .dropShadow(shape, Shadow(1.dp, Color.Black.copy(alpha = 0.04f), offset = DpOffset(0.dp, 1.dp)))
        .dropShadow(shape, Shadow(12.dp, Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 8.dp)))
        .background(Color.White, shape)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Island(
    grow: Channel,
    draw: Channel,
    pop: Channel,
    label: Channel,
    pulse: Channel,
    shake: Channel,
    glow: Channel,
    charge: Channel,
    percent: Int,
    compactTop: Float,
    expandedTop: Float,
    expandedWidth: Float,
    trackWidth: Float,
    screenWidth: Float,
    remaining: String,
    isOpen: Boolean,
    onLongPress: () -> Unit,
    onGrab: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        Modifier
            .layout { measurable, constraints ->
                val g = max(grow.value, 0f)
                val w = ((COMPACT_WIDTH + (expandedWidth - COMPACT_WIDTH) * g) * density).roundToInt()
                val h = ((COMPACT_HEIGHT + (EXPANDED_HEIGHT - COMPACT_HEIGHT) * g) * density).roundToInt()
                val x = (((screenWidth - COMPACT_WIDTH - (expandedWidth - COMPACT_WIDTH) * g) / 2f + shake.value) * density).roundToInt()
                val y = ((compactTop + (expandedTop - compactTop) * g) * density).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
            }
            .drawBehind {
                val g = max(grow.value, 0f)
                val w = COMPACT_WIDTH + (expandedWidth - COMPACT_WIDTH) * g
                val h = COMPACT_HEIGHT + (EXPANDED_HEIGHT - COMPACT_HEIGHT) * g
                val radius = COMPACT_RADIUS + (EXPANDED_RADIUS - COMPACT_RADIUS) * min(g, 1.2f)
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        IslandShadowNear.draw(it.nativeCanvas, 0f, 4f, w, h, radius, g.coerceIn(0f, 1f))
                        IslandShadowFar.draw(it.nativeCanvas, 0f, 18f, w, h, radius, g.coerceIn(0f, 1f))
                    }
                    drawRoundRect(Color.Black, Offset.Zero, Size(w, h), CornerRadius(radius))
                }
            },
    ) {
        // The long press that opens the pill sits under everything else.
        Box(
            Modifier
                .matchParentSize()
                .combinedClickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = {}, onLongClick = onLongPress)
                .semantics { contentDescription = "Battery $percent percent" },
        )

        Row(
            Modifier
                .graphicsLayer { alpha = (1f - grow.value * 3f).coerceIn(0f, 1f) }
                .layout { measurable, c ->
                    val p = measurable.measure(Constraints.fixed(c.maxWidth, (COMPACT_HEIGHT * density).roundToInt()))
                    layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bolt, null, Modifier.size(16.dp).graphicsLayer { alpha = pulse.value }, tint = Red)
            BasicText("$percent", style = pillText(16f, 20f, FontWeight.SemiBold, Red, tabular = true))
        }

        Box(Modifier.offset(SIDE_PADDING.dp, 0.dp).size((expandedWidth - SIDE_PADDING * 2).dp, EXPANDED_HEIGHT.dp).then(if (isOpen) Modifier else Modifier.clearAndSetSemantics {})) {
            val centre = EXPANDED_HEIGHT / 2f
            Canvas(Modifier.offset(0.dp, (centre - TRACK_HEIGHT / 2f).dp).size(trackWidth.dp, TRACK_HEIGHT.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    val drawn = trackWidth * draw.value.coerceIn(0f, 1f)
                    val capsule = CornerRadius(TRACK_HEIGHT / 2f)
                    // The track is clipped to what has been drawn so far.
                    clipRect(right = drawn) {
                        drawRoundRect(Track, Offset.Zero, Size(trackWidth, TRACK_HEIGHT), capsule)
                        drawRoundRect(Red, Offset.Zero, Size(charge.value.coerceIn(0f, 100f) / 100f * trackWidth, TRACK_HEIGHT), capsule)
                    }
                }
            }
            val chipX = { clamp(charge.value.coerceIn(0f, 100f) / 100f * trackWidth - 4f, 0f, trackWidth - CHIP_WIDTH) }
            Box(
                Modifier
                    .offset(0.dp, (centre - CHIP_HEIGHT / 2f).dp)
                    .size(CHIP_WIDTH.dp, CHIP_HEIGHT.dp)
                    .graphicsLayer {
                        alpha = glow.value * pop.value.coerceIn(0f, 1f)
                        translationX = chipX() * density
                    }
                    .drawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            drawIntoCanvas { GlowShadow.draw(it.nativeCanvas, -2f, -2f, CHIP_WIDTH + 4f, CHIP_HEIGHT + 4f, CHIP_HEIGHT / 2f + 2f) }
                            drawRoundRect(Red, Offset.Zero, Size(CHIP_WIDTH, CHIP_HEIGHT), CornerRadius(CHIP_HEIGHT / 2f))
                        }
                    },
            )
            Row(
                Modifier
                    .offset(0.dp, (centre - CHIP_HEIGHT / 2f).dp)
                    .size(CHIP_WIDTH.dp, CHIP_HEIGHT.dp)
                    .graphicsLayer {
                        val p = max(pop.value, 0f)
                        alpha = (pop.value * 2f).coerceIn(0f, 1f)
                        scaleX = p
                        scaleY = p
                        translationX = chipX() * density
                    }
                    .clip(RoundedCornerShape(CHIP_HEIGHT.dp / 2))
                    .background(Red),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Bolt, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.6f))
                BasicText("$percent", style = pillText(16f, 20f, FontWeight.Bold, Color.White, tabular = true))
            }
            if (isOpen) {
                Box(
                    Modifier
                        .offset(0.dp, (centre - GRAB_HEIGHT / 2f).dp)
                        .size(trackWidth.dp, GRAB_HEIGHT.dp)
                        .pointerInput(trackWidth) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                onGrab(down.position.x / density)
                                drag(down.id) { change ->
                                    onDrag(change.position.x / density)
                                    change.consume()
                                }
                                onRelease()
                            }
                        },
                )
            }
            BasicText(
                remaining,
                Modifier
                    .offset((trackWidth + 12f).dp, (centre - 11f).dp)
                    .height(22.dp)
                    .graphicsLayer { alpha = label.value },
                style = pillText(16f, 22f, FontWeight.SemiBold, Red),
                maxLines = 1,
            )
        }
    }
}

private fun clamp(value: Float, low: Float, high: Float) = min(high, max(low, value))
