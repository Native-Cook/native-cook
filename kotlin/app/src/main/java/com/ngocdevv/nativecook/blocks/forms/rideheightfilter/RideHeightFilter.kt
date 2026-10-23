package com.ngocdevv.nativecook.blocks.forms.rideheightfilter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A clearance chip, its explanation, ride height and matching car count. */
data class Clearance(val label: String, val heading: String, val text: String, val examples: String, val height: Float, val count: Int)
private val Options = listOf(
    Clearance("Sport", "Up to 12 cm", "Made for race tracks and perfect roads. Speed bumps have to be taken very, very carefully.", "coupés, roadsters, hot hatches", 0f, 128),
    Clearance("Low", "From 12 to 15 cm", "These cars are built for good roads. Be a little careful with kerbs when you park.", "saloons, estates, city hatchbacks", 0.28f, 342),
    Clearance("Medium", "From 15 to 20 cm", "Kerbs stop being a worry, and a track through the woods is fine. Real off-road is still out of reach.", "crossovers, compact SUVs", 0.55f, 517),
    Clearance("High", "From 20 to 24 cm", "Proper off-roaders start here, next to quite ordinary city crossovers that only look the part.", "large SUVs, pickups", 0.78f, 266),
    Clearance("Off-road", "More than 24 cm", "Aerodynamics suffer, but it will go almost anywhere. This is the clearance of big 4x4s.", "body-on-frame 4x4s, lifted pickups", 1f, 94),
)
private val Backdrop = Color(0xFF101012)
private val Behind = Color(0xFF3A3A3E)
private val Sheet = Color.White
private val Ink = Color(0xFF141414)
private val Text = Color(0xFF222222)
private val Mute = Color(0xFF8A8A8F)
private val Grabber = Color(0xFFD6D6D9)
private val Chip = Color(0xFFEFEFEF)
private val ChipPressed = Color(0xFFDCDCDC)
private val ChipOn = Color(0xFF141414)
private val Link = Color(0xFF2BA6C9)
private const val SHEET_TOP = 14f
private const val SHEET_RADIUS = 26f
private const val MARGIN = 16f
private const val HEADER = 52f
private const val CHIP_HEIGHT = 46f
private const val CHIP_RADIUS = 13f
private const val CHIP_GAP = 8f
private const val APPLY = 56f
private const val LIFT = 18f
private const val SUSPENSION_MASS = 1f
private const val SUSPENSION_STIFFNESS = 170f
private const val SUSPENSION_DAMPING = 13f
private const val LEAD_MASS = 1f
private const val LEAD_STIFFNESS = 330f
private const val LEAD_DAMPING = 24f
private const val PITCH = 0.0045f
private const val CHIP_FADE = 300
private const val TEXT_FADE = 160
private const val LOOP = 8.6f
private const val FIRST_AT = 0.8f
private const val STEP = 1.4f
private const val PRESS_FOR = 0.14f
private val Tour = intArrayOf(4, 1, 2, 3, 0)
private const val USER_HOLD = 3f
private const val RETURN = 0.9f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The same retargeting, velocity clipping and energy cutoff as the source spring. */
private class Suspension(initial: Float, private val mass: Float, private val stiffness: Float, private val damping: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var target = initial
    private var velocity = 0f
    private var initialEnergy = 0f
    private var active = false
    fun to(next: Float, still: Boolean) {
        if (still) { target = next; state.floatValue = next; velocity = 0f; active = false; return }
        if (active && target == next) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        initialEnergy = 0.5f * stiffness * (value - next) * (value - next)
        target = next; active = true
    }
    fun advance(dt: Float) {
        if (!active) return
        val omega = sqrt(stiffness / mass)
        val decay = damping / (2f * mass)
        val e = value - target
        val next: Float
        if (decay < omega) {
            val damped = sqrt(omega * omega - decay * decay)
            val sine = sin(damped * dt); val cosine = cos(damped * dt); val envelope = exp(-decay * dt)
            next = envelope * (e * cosine + (velocity + decay * e) / damped * sine)
            velocity = envelope * (velocity * cosine - (decay * velocity + omega * omega * e) / damped * sine)
        } else {
            val envelope = exp(-omega * dt)
            val b = velocity + omega * e
            next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt)
        }
        state.floatValue = target + next
        val energy = 0.5f * stiffness * next * next + 0.5f * mass * velocity * velocity
        if (initialEnergy == 0f || energy / initialEnergy <= SPRING_ENERGY_THRESHOLD) { state.floatValue = target; velocity = 0f; active = false }
    }
}

/** A clearance filter whose car body answers the chips by moving on its suspension. */
@Composable
fun RideHeightFilter(
    options: List<Clearance> = Options,
    selected: Int = 0,
    perRow: Int = 3,
    autoPlay: Boolean = false,
    title: String = "Ground clearance",
    resetLabel: String = "Reset",
    examplesLabel: String = "Typical cars",
    applyLabel: (count: Int) -> String = { "Show $it cars" },
    linkColor: Color = Link,
    onChange: ((index: Int, option: Clearance) -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onApply: ((index: Int, option: Clearance) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) { Box(modifier.fillMaxSize().background(Backdrop)); return }
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val givenOptions by rememberUpdatedState(options)
    val initial by rememberUpdatedState(selected.coerceIn(0, options.lastIndex))
    val changed by rememberUpdatedState(onChange)
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    var chosen by remember { mutableIntStateOf(initial) }
    var autoPressed by remember { mutableStateOf<Int?>(null) }
    var touches by remember { mutableIntStateOf(0) }
    val lift = remember { Suspension(options[initial].height * LIFT, SUSPENSION_MASS, SUSPENSION_STIFFNESS, SUSPENSION_DAMPING) }
    val lead = remember { Suspension(options[initial].height * LIFT, LEAD_MASS, LEAD_STIFFNESS, LEAD_DAMPING) }
    val pitch = remember { derivedStateOf { (lead.value - lift.value) * PITCH } }
    fun setChoice(index: Int, notify: Boolean) {
        chosen = index
        val target = givenOptions[index].height * LIFT
        lift.to(target, reduced); lead.to(target, reduced)
        if (notify) changed?.invoke(index, givenOptions[index])
    }
    fun touched() { autoPressed = null; touches++ }
    fun haptic() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val latestSetChoice by rememberUpdatedState(::setChoice)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }; val dt = ((now - last) / 1e9f).coerceIn(0f, 0.064f); last = now
            lift.advance(dt); lead.advance(dt)
        }
    }
    LaunchedEffect(autoPlay, touches, options, selected) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) { delay((USER_HOLD * 1000).toLong()); latestSetChoice(initial, false); delay((RETURN * 1000).toLong()) }
        coroutineScope {
            var origin = System.nanoTime()
            while (true) {
                val start = origin
                Tour.forEachIndexed { order, stop -> launch {
                    val index = stop % options.size
                    val at = FIRST_AT + order * STEP
                    delay(max(0L, (start + ((at - PRESS_FOR) * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                    autoPressed = index
                    delay(max(0L, (start + (at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                    autoPressed = null; latestSetChoice(index, false)
                } }
                delay(max(0L, (start + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9f).toLong()
            }
        }
    }
    val index = chosen.coerceIn(0, options.lastIndex)
    val option = options[index]
    val rows = options.indices.chunked(perRow.coerceAtLeast(1))
    BoxWithConstraints(modifier.fillMaxSize().background(Backdrop)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val sheetTop = insets.calculateTopPadding().value + SHEET_TOP
        val sheetHeight = height - sheetTop
        val bottom = max(insets.calculateBottomPadding().value, 16f) + 8f
        val pictureHeight = BOX_HEIGHT / BOX_WIDTH * width
        val chipsHeight = rows.size * CHIP_HEIGHT + max(0, rows.size - 1) * CHIP_GAP
        val fixed = 8f + 5f + HEADER + 18f + pictureHeight + 26f + chipsHeight + 30f + 128f + APPLY + bottom
        val space = max(0f, sheetHeight - fixed)
        Box(Modifier.offset(y = (sheetTop - 10f).dp).padding(horizontal = MARGIN.dp).fillMaxWidth().height(40.dp).background(Behind, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
        Box(Modifier.offset(y = sheetTop.dp).fillMaxWidth().height(sheetHeight.dp).clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)).background(Sheet)) {
            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp, 5.dp).background(Grabber, RoundedCornerShape(2.5.dp)))
                Box(Modifier.fillMaxWidth().height(HEADER.dp), contentAlignment = Alignment.Center) {
                    BasicText(title, style = textStyle(17f, 22f, FontWeight.Bold, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val closeInteraction = remember { MutableInteractionSource() }
                    val closePressed by closeInteraction.collectIsPressedAsState()
                    Box(Modifier.align(Alignment.CenterStart).offset(x = (MARGIN - 4f).dp).size(28.dp, HEADER.dp)) {
                        Canvas(Modifier.align(Alignment.Center).size(28.dp).graphicsLayer { alpha = if (closePressed) 0.4f else 1f }.clearAndSetSemantics {}) {
                            surface(0.dp) { _, _, _, _ -> drawLine(Ink, Offset(6f, 6f), Offset(22f, 22f), 2f, StrokeCap.Round); drawLine(Ink, Offset(22f, 6f), Offset(6f, 22f), 2f, StrokeCap.Round) }
                        }
                        Box(Modifier.matchParentSize().bleed(8.dp).clickable(closeInteraction, indication = null, role = Role.Button) { touched(); onClose?.invoke() }.semantics { contentDescription = "Close" })
                    }
                    val resetInteraction = remember { MutableInteractionSource() }
                    val resetPressed by resetInteraction.collectIsPressedAsState()
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = MARGIN.dp).height(HEADER.dp), contentAlignment = Alignment.Center) {
                        BasicText(resetLabel, style = textStyle(16f, 21f, FontWeight.Normal, linkColor), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = if (resetPressed) 0.4f else 1f })
                        Box(Modifier.matchParentSize().bleed(8.dp).clickable(resetInteraction, indication = null, role = Role.Button) { touched(); haptic(); setChoice(initial, true); onReset?.invoke() }.semantics { contentDescription = resetLabel })
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(pictureHeight.dp).clearAndSetSemantics { contentDescription = "A car with ${option.label} clearance beside a kerb"; role = Role.Image }) { CarPicture(width, lift, pitch, LIFT) }
                Spacer(Modifier.height(26.dp))
                Column(Modifier.padding(horizontal = MARGIN.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                    for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                        for (item in row) FilterChip(options[item].label, item == index, item == autoPressed) { touched(); haptic(); setChoice(item, true) }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = MARGIN.dp).height(128.dp)) {
                    IndependentFade(index, CopyText(option.heading, option.text, "$examplesLabel: ${option.examples}"), TEXT_FADE, still) { copy ->
                        Column(Modifier.fillMaxWidth()) {
                            BasicText(copy.heading, style = textStyle(19f, 24f, FontWeight.Bold, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(copy.text, style = textStyle(16f, 22f, FontWeight.Normal, Text), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            BasicText(copy.examples, style = textStyle(14f, 19f, FontWeight.Normal, Mute), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(space.dp))
                val applyInteraction = remember { MutableInteractionSource() }
                val applyPressed by applyInteraction.collectIsPressedAsState()
                val applyAlpha by cssTween(if (applyPressed) 0.75f else 1f, 110)
                Box(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().height(APPLY.dp).graphicsLayer { alpha = applyAlpha }.background(Ink, RoundedCornerShape(16.dp))
                    .clickable(applyInteraction, indication = null, role = Role.Button) { touched(); haptic(); onApply?.invoke(index, option) }, contentAlignment = Alignment.Center) {
                    IndependentFade(index, applyLabel(option.count), TEXT_FADE, still, Modifier.fillMaxSize()) { text ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(text, style = textStyle(17f, 22f, FontWeight.SemiBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(bottom.dp))
            }
        }
    }
}

/** CSS transitions continue under reduced motion; the suspension and layout fades do not. */
@Composable
private fun cssTween(target: Float, milliseconds: Int): State<Float> {
    val value = remember { mutableFloatStateOf(target) }
    LaunchedEffect(target, milliseconds) {
        val from = value.floatValue; val origin = System.nanoTime()
        do {
            val u = ((withFrameNanos { it } - origin) / (milliseconds * 1e6f)).coerceIn(0f, 1f)
            value.floatValue = from + (target - from) * CssEase.transform(u)
        } while (u < 1f)
    }
    return value
}

@Composable
private fun cssColor(target: Color, milliseconds: Int): State<Color> {
    val value = remember { mutableStateOf(target) }
    LaunchedEffect(target, milliseconds) {
        val from = value.value; val origin = System.nanoTime()
        do {
            val u = ((withFrameNanos { it } - origin) / (milliseconds * 1e6f)).coerceIn(0f, 1f)
            val t = CssEase.transform(u)
            value.value = Color(from.red + (target.red - from.red) * t, from.green + (target.green - from.green) * t,
                from.blue + (target.blue - from.blue) * t, from.alpha + (target.alpha - from.alpha) * t)
        } while (u < 1f)
    }
    return value
}

@Composable
private fun FilterChip(label: String, on: Boolean, held: Boolean, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed || held
    val fill by cssColor(if (on) ChipOn else if (down) ChipPressed else Chip, CHIP_FADE)
    val ink by cssColor(if (on) Color.White else Text, CHIP_FADE)
    val scale by cssTween(if (down) 0.96f else 1f, 120)
    Box(Modifier.height(CHIP_HEIGHT.dp).clickable(interaction, indication = null, role = Role.RadioButton, onClick = onPress).semantics { selected = on }) {
        Box(Modifier.height(CHIP_HEIGHT.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(fill, RoundedCornerShape(CHIP_RADIUS.dp)).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            BasicText(label, style = textStyle(16f, 21f, FontWeight.Normal, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class CopyText(val heading: String, val text: String, val examples: String)

/** Key reversals create new entering text while each departing snapshot finishes its own fade. */
private class FadeDeck<T>(var chosen: Int, var current: T) {
    data class Departure<T>(val id: Long, val value: T, val origin: Long)
    var sequence = 0L
    var entering: Long? = null
    var departing = emptyList<Departure<T>>()
    fun update(next: Int, value: T, still: Boolean) {
        if (next != chosen) {
            val now = System.nanoTime()
            sequence++
            if (!still) departing = departing + Departure(sequence, current, now)
            entering = if (still) null else now
            chosen = next
        }
        current = value
        if (still) { departing = emptyList(); entering = null }
    }
}

@Composable
private fun <T> IndependentFade(chosen: Int, value: T, milliseconds: Int, still: Boolean, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val deck = remember { FadeDeck(chosen, value).apply { entering = if (still) null else System.nanoTime() } }
    var now by remember { mutableStateOf(System.nanoTime()) }
    deck.update(chosen, value, still)
    val duration = milliseconds * 1_000_000L
    LaunchedEffect(deck.sequence, still) {
        while (deck.entering != null || deck.departing.isNotEmpty()) {
            val frame = withFrameNanos { it }
            deck.departing = deck.departing.filter { frame - it.origin < duration }
            if (deck.entering?.let { frame - it >= duration } == true) deck.entering = null
            now = frame
        }
    }
    fun progress(origin: Long) = QuadInOut.transform(((now - origin).toFloat() / duration).coerceIn(0f, 1f))
    Box(modifier) {
        for (layer in deck.departing) key(layer.id) {
            Box(Modifier.graphicsLayer { alpha = 1f - progress(layer.origin); compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(layer.value) }
        }
        Box(Modifier.graphicsLayer { alpha = deck.entering?.let(::progress) ?: 1f; compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(deck.current) }
    }
}
