package com.ngocdevv.nativecook.blocks.pickers.notchradialmenu

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One action of the menu.
 *
 * @param icon The glyph drawn on the chip.
 * @param label What a screen reader calls it.
 */
data class RadialMenuItem(val icon: ImageVector, val label: String)

/**
 * One of the two tabs beside the notch.
 *
 * @param icon The glyph while the tab is not selected.
 * @param selectedIcon The glyph while it is.
 */
data class RadialMenuTab(val icon: ImageVector, val selectedIcon: ImageVector, val label: String)

private const val WIDTH = 340f
private const val HEIGHT = 204f

// The bar's top edge, which is also the height of the button's centre.
private const val BAR_TOP = 124f
private const val BAR_HEIGHT = 80f
private const val TOP_CORNER = 6f
private const val BOTTOM_CORNER = 26f
private const val NOTCH = 34f
private const val SHOULDER = 18f
private const val BUTTON = 58f
private const val PLUS = 16f
private const val PLUS_LINE = 1.75f
private const val TAB_INSET = 90f
private const val TAB_WIDTH = 110f
private const val GLOW = 300f

// The chips: on an arc `SPREAD` degrees to each side of 12 o'clock, the middle ones further out.
private const val CHIP = 46f
private const val GLYPH = 21f
private const val SPREAD = 51f
private const val SIDE_REACH = 70f
private const val TOP_REACH = 84f

// Where a folded chip waits, in degrees clockwise from 12 o'clock: left of the button, a little low.
private const val FOLD = -115f
private const val FOLD_REACH = 0.2f
private const val FOLD_SCALE = 0.6f

private const val STAGGER = 0.05f

// A picked chip swells, and the menu closes this long after the tap.
private const val CLOSE_AFTER = 0.45f
private const val TAB_TIME = 0.2f
private const val FADE = 0.15f

// autoPlay (seconds): the button is tapped at `OPEN_AT`, a chip at `PICK_AT`, twice per loop.
private val OPEN_AT = floatArrayOf(1.0f, 4.2f)
private val PICK_AT = floatArrayOf(2.45f, 5.45f)
private const val LOOP = 7f

// After a touch the loop waits this long, closes the menu, and starts over once it has settled.
private const val RESUME_AFTER = 2f
private const val SETTLE = 0.6f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val OpenSpring = physical(260f, 23f)
private val CloseSpring = physical(300f, 34f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Lilac = Color(0xFFC77DFF)
private val Grey = Color(0xFF9A9A9A)

private val Items = listOf(
    RadialMenuItem(Icons.Rounded.PhoneInTalk, "Call"),
    RadialMenuItem(Icons.Rounded.MarkEmailRead, "Message"),
    RadialMenuItem(Icons.Rounded.Mic, "Voice note"),
)

private val Tabs = listOf(
    RadialMenuTab(Icons.Outlined.Home, Icons.Rounded.Home, "Home"),
    RadialMenuTab(Icons.Outlined.Person, Icons.Rounded.Person, "Profile"),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Where a chip is, measured from the button's centre; [turn] is in degrees. */
private class ChipPlace(val x: Float, val y: Float, val scale: Float, val turn: Float)

/**
 * Where chip [index] of [count] is at [progress] (0 folded behind the button … 1 on the arc; a spring
 * may carry it a little past either end): it swings clockwise about the button while moving outward
 * and growing, and its glyph turns with it.
 */
private fun place(index: Int, count: Int, progress: Float): ChipPlace {
    val along = if (count > 1) index / (count - 1f) * 2f - 1f else 0f
    val final = along * SPREAD
    val reach = mix(TOP_REACH, SIDE_REACH, abs(along))
    val angle = mix(FOLD, final, progress)
    val distance = reach * mix(FOLD_REACH, 1f, progress)
    val radians = Math.toRadians(angle.toDouble()).toFloat()
    return ChipPlace(
        x = distance * sin(radians),
        y = -distance * cos(radians),
        scale = max(0f, mix(FOLD_SCALE, 1f, progress)),
        turn = angle - final,
    )
}

/**
 * The bar, in dp with its top-left corner at ([left], [top]): square shoulders at the top, round
 * corners at the bottom, and in the middle of the top edge a cradle whose rim curves into it.
 */
private fun barPath(left: Float, top: Float): Path {
    val right = left + WIDTH
    val bottom = top + BAR_HEIGHT
    val mid = left + WIDTH / 2f
    // The shoulder's circle touches the top edge and the cradle's circle: its centre is this far out,
    // and the two circles meet `join` degrees below the top edge.
    val reach = sqrt(NOTCH * NOTCH + 2f * NOTCH * SHOULDER)
    val join = Math.toDegrees(asin(SHOULDER / (NOTCH + SHOULDER)).toDouble()).toFloat()
    fun circle(x: Float, y: Float, r: Float) = Rect(x - r, y - r, x + r, y + r)
    return Path().apply {
        moveTo(left, top + TOP_CORNER)
        arcTo(circle(left + TOP_CORNER, top + TOP_CORNER, TOP_CORNER), 180f, 90f, false)
        lineTo(mid - reach, top)
        arcTo(circle(mid - reach, top + SHOULDER, SHOULDER), -90f, 90f - join, false)
        arcTo(circle(mid, top, NOTCH), 180f - join, -(180f - 2f * join), false)
        arcTo(circle(mid + reach, top + SHOULDER, SHOULDER), 180f + join, 90f - join, false)
        lineTo(right - TOP_CORNER, top)
        arcTo(circle(right - TOP_CORNER, top + TOP_CORNER, TOP_CORNER), -90f, 90f, false)
        lineTo(right, bottom - BOTTOM_CORNER)
        arcTo(circle(right - BOTTOM_CORNER, bottom - BOTTOM_CORNER, BOTTOM_CORNER), 0f, 90f, false)
        lineTo(left + BOTTOM_CORNER, bottom)
        arcTo(circle(left + BOTTOM_CORNER, bottom - BOTTOM_CORNER, BOTTOM_CORNER), 90f, 90f, false)
        close()
    }
}

private val TabStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** What moves. */
private class MenuMotion(count: Int, tab: Int) {
    /** The button: 0 a plus on violet … 1 a cross on black. */
    val open = Animatable(0f)

    /** Each chip: 0 behind the button … 1 in its place on the arc; and its tap feedback. */
    val chips = List(count) { Animatable(0f) }
    val swell = List(count) { Animatable(0f) }
    val press = Animatable(0f)

    /** The tabs' colours: 0 the left one is lit … 1 the right one. */
    val lit = Animatable(tab.toFloat())

    var isOpen by mutableStateOf(false)
    var tab by mutableIntStateOf(tab)

    /** The chips' moves, and the close that follows a pick: opening or closing drops them. */
    var fan: List<Job> = emptyList()
    var closing: Job? = null
}

/**
 * A tab bar whose top edge dips into a notch that cradles a round button. Tapping the button turns
 * its plus into a cross and fans the actions out of it: the chips leave from behind the button on
 * the left and sweep clockwise onto an arc above it, the furthest one leading. Tapping a chip picks
 * it and folds the fan away; tapping the cross folds it without a pick.
 *
 * @param items The actions, left to right along the arc. Three sit best.
 * @param tabs The tab left of the notch and the one right of it.
 * @param initialTab The tab selected at first.
 * @param autoPlay Opens the menu, picks an action and closes it by itself, in a loop.
 * @param onSelect Called with the index of the action that was picked.
 * @param onOpenChange Called when the menu opens or closes.
 * @param onTabChange Called with the index of the tab that was selected.
 */
@Composable
fun NotchRadialMenu(
    items: List<RadialMenuItem> = Items,
    tabs: List<RadialMenuTab> = Tabs,
    initialTab: Int = 0,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onOpenChange: ((open: Boolean) -> Unit)? = null,
    onTabChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && items.isNotEmpty()
    val firstTab = initialTab.coerceIn(0, 1)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember(items.size) { MenuMotion(items.size, firstTab) }
    val picked by rememberUpdatedState(onSelect)
    val opened by rememberUpdatedState(onOpenChange)
    val tabbed by rememberUpdatedState(onTabChange)
    // Counts taps: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Up in 80 ms, a 40 ms hold, back in 180 ms: a tap.
    fun pulse(value: Animatable<Float, AnimationVector1D>) {
        scope.launch {
            value.animateTo(1f, tween(80, easing = LinearEasing))
            delay(40)
            value.animateTo(0f, tween(180, easing = LinearEasing))
        }
    }

    // Fans the chips out or folds them away, the last one leading by `STAGGER` per place.
    fun show(next: Boolean) {
        if (next == motion.isOpen) return
        motion.isOpen = next
        motion.closing?.cancel()
        motion.fan.forEach { it.cancel() }
        val target = if (next) 1f else 0f
        val spec = if (next) OpenSpring else CloseSpring
        scope.launch { if (still) motion.open.snapTo(target) else motion.open.animateTo(target, spec) }
        motion.fan = motion.chips.mapIndexed { index, chip ->
            scope.launch {
                if (still) {
                    chip.animateTo(target, tween((FADE * 1000).toInt(), easing = LinearEasing))
                } else {
                    delay(((motion.chips.size - 1 - index) * STAGGER * 1000).toLong())
                    chip.animateTo(target, spec)
                }
            }
        }
        opened?.invoke(next)
    }

    fun toggle() {
        pulse(motion.press)
        show(!motion.isOpen)
    }

    // Picks action `index`: its chip swells for a moment, then the menu closes.
    fun select(index: Int) {
        if (!motion.isOpen || index !in motion.swell.indices) return
        pulse(motion.swell[index])
        motion.closing?.cancel()
        motion.closing = scope.launch {
            delay((CLOSE_AFTER * 1000).toLong())
            show(false)
        }
        picked?.invoke(index)
    }

    fun pickTab(index: Int) {
        if (index == motion.tab) return
        motion.tab = index
        scope.launch {
            if (still) motion.lit.snapTo(index.toFloat()) else motion.lit.animateTo(index.toFloat(), tween((TAB_TIME * 1000).toInt(), easing = EaseInOut))
        }
        tabbed?.invoke(index)
    }

    fun touch() {
        touches += 1
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // The script a finger would follow: open, pick the middle action, open again, pick the last one.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the closed menu and the first tab, and start over.
            delay((RESUME_AFTER * 1000).toLong())
            show(false)
            pickTab(firstTab)
            delay((SETTLE * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        val picks = intArrayOf(items.size / 2, items.size - 1)
        while (true) {
            for (round in picks.indices) {
                until(OPEN_AT[round])
                toggle()
                until(PICK_AT[round])
                select(picks[round])
            }
            turn += 1
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        val bar = remember { barPath(0f, BAR_TOP) }
        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the Skia canvas of the React Native version. The glow reaches past the box.
            scale(density, density, pivot = Offset.Zero) {
                val center = Offset(WIDTH / 2f, BAR_TOP)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF3D2A70), Color(0xFF120B24)), center = center, radius = GLOW),
                    radius = GLOW,
                    center = center,
                )
                drawPath(bar, Color(0xFF050505))
                // The light the open menu throws on the bottom of the cradle.
                val rim = NOTCH - 1.5f
                drawArc(
                    brush = Brush.verticalGradient(listOf(Lilac.copy(alpha = 0f), Lilac), startY = BAR_TOP - rim, endY = BAR_TOP + rim),
                    startAngle = 21.6f,
                    sweepAngle = 136.8f,
                    useCenter = false,
                    topLeft = Offset(center.x - rim, center.y - rim),
                    size = Size(rim * 2f, rim * 2f),
                    alpha = 0.55f * clamp01(motion.open.value),
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                )
            }
        }

        tabs.take(2).forEachIndexed { side, tab ->
            Box(
                Modifier
                    .offset(((if (side == 0) TAB_INSET else WIDTH - TAB_INSET) - TAB_WIDTH / 2f).dp, BAR_TOP.dp)
                    .size(TAB_WIDTH.dp, BAR_HEIGHT.dp)
                    .clickable(interactionSource = null, indication = null, role = Role.Tab) {
                        touch()
                        pickTab(side)
                    }
                    .semantics {
                        contentDescription = tab.label
                        selected = motion.tab == side
                    },
            ) {
                // The grey and the lilac look lie on top of each other and cross-fade.
                TabLook(tab.icon, tab.label, Grey) { if (side == 0) motion.lit.value else 1f - motion.lit.value }
                TabLook(tab.selectedIcon, tab.label, Lilac) { if (side == 0) 1f - motion.lit.value else motion.lit.value }
            }
        }

        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .offset((WIDTH / 2f - CHIP / 2f).dp, (BAR_TOP - CHIP / 2f).dp)
                    .size(CHIP.dp)
                    .graphicsLayer {
                        val progress = motion.chips[index].value
                        // Under reduced motion a chip does not travel: it fades in where it belongs.
                        val at = place(index, items.size, if (still) 1f else progress)
                        val grow = at.scale * (1f + 0.12f * motion.swell[index].value)
                        translationX = at.x.dp.toPx()
                        translationY = at.y.dp.toPx()
                        rotationZ = at.turn
                        scaleX = grow
                        scaleY = grow
                        alpha = clamp01(progress / 0.3f)
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFE2A8FF), Color(0xFFB266F0))))
                    .clickable(enabled = motion.isOpen, interactionSource = null, indication = null, role = Role.Button) {
                        touch()
                        select(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = Color.White, modifier = Modifier.size(GLYPH.dp))
            }
        }

        Box(
            Modifier
                .offset((WIDTH / 2f - BUTTON / 2f).dp, (BAR_TOP - BUTTON / 2f).dp)
                .size(BUTTON.dp)
                .graphicsLayer {
                    val dip = 1f - 0.06f * motion.press.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFB06AF5), Color(0xFF7C3AED))))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    toggle()
                }
                .semantics { contentDescription = if (motion.isOpen) "Close menu" else "Open menu" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = clamp01(motion.open.value) }.background(Color(0xFF0A0A0A)))
            Canvas(Modifier.size(PLUS.dp).graphicsLayer { rotationZ = 45f * motion.open.value }) {
                val line = PLUS_LINE.dp.toPx()
                drawLine(Color.White, Offset(line / 2f, size.height / 2f), Offset(size.width - line / 2f, size.height / 2f), line, StrokeCap.Round)
                drawLine(Color.White, Offset(size.width / 2f, line / 2f), Offset(size.width / 2f, size.height - line / 2f), line, StrokeCap.Round)
            }
        }
    }
}

/** One look of a tab, an icon over its name in [color]; [opacity] is read while drawing. */
@Composable
private fun TabLook(icon: ImageVector, label: String, color: Color, opacity: () -> Float) {
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = opacity() },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        BasicText(label, style = TabStyle.copy(color = color), maxLines = 1)
    }
}
