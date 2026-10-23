package com.ngocdevv.nativecook.blocks.navigation.bubbletabbar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BubbleTabBarIcon(
    // Icons in a 24 × 24 box: stroked paths and stroked circles (x, y, radius).
    val d: String,
    val circles: List<Triple<Float, Float, Float>> = emptyList(),
) {
    Profile("M4.5 20.5C4.5 16.6 7.9 14 12 14C16.1 14 19.5 16.6 19.5 20.5", listOf(Triple(12f, 8f, 4f))),
    Saved("M6.5 4.5C6.5 3.95 6.95 3.5 7.5 3.5H16.5C17.05 3.5 17.5 3.95 17.5 4.5V20.5L12 16.5L6.5 20.5Z"),
    Add("M12 4.5V19.5M4.5 12H19.5"),
    Search("M15.4 15.4L20 20", listOf(Triple(10.8f, 10.8f, 6.3f))),
    More("M4.5 7H19.5M4.5 12H19.5M4.5 17H19.5"),
}

data class BubbleTabBarTab(
    val label: String,
    val icon: BubbleTabBarIcon,
    /** The page's heading and the line under it. */
    val title: String,
    val caption: String,
)

val BubbleTabBarSamples = listOf(
    BubbleTabBarTab("Profile", BubbleTabBarIcon.Profile, "Profile", "Your account and activity"),
    BubbleTabBarTab("Saved", BubbleTabBarIcon.Saved, "Saved", "Things you kept for later"),
    BubbleTabBarTab("Add", BubbleTabBarIcon.Add, "Create", "Start something new"),
    BubbleTabBarTab("Search", BubbleTabBarIcon.Search, "Search", "Find people and posts"),
    BubbleTabBarTab("More", BubbleTabBarIcon.More, "More", "Settings, help and about"),
)

private val Screen = Color(0xFF202020)
private val Bar = Color.White
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF8A8A8A)
private val RowFill = Color(0xFF2A2A2A)
private val RowMark = Color(0xFF353535)

// The bar, in points. Its top edge is y 0; the bubble rises above it.
private const val BAR_H = 60f
private const val SIDE = 22f
private const val CORNER = 16f
private const val ICON = 24f
private const val ICON_Y = 22f
private const val LABEL_Y = 42f
private const val BUBBLE_R = 28f
// The bite the bubble sits in: 4 points wider than the bubble, with rounded shoulders.
private const val NOTCH_R = 32f
private const val FILLET = 8f
// Where the bubble's and the bite's centres rest, below the bar's top edge.
private const val REST_Y = 6f
// While the bubble travels the bite closes up (its centre rises this much) and the bubble lifts.
private const val FLAT_LIFT = 32f
private const val HOP_RISE = 4f
// How far from a tab the bubble must be before its icon starts to leave.
private const val REACH = 0.6f
// Room above the bar for the bubble.
private const val ROOM = 40f
// Polyline steps for each arc of the outline.
private const val CORNER_STEPS = 8
private const val FILLET_STEPS = 8
private const val NOTCH_STEPS = 28

// Motion. A hop is a spring of response 0.45 s at damping ratio 0.7 (stiffness 194.96, damping
// 19.55); a hop of more than one tab uses response 0.7 s (stiffness 80.57, damping 12.57). The bite
// follows the bubble 40 ms late, and closes over HOP_CLOSE.
private val Hop = spring<Float>(dampingRatio = 0.7f, stiffness = 194.96f)
private val LongHop = spring<Float>(dampingRatio = 0.7f, stiffness = 80.57f)
private const val NOTCH_DELAY = 0.04f
private const val HOP_CLOSE = 0.2f
private val QuadOut = Easing { 1f - (1f - it) * (1f - it) }
private const val PAGE_FADE = 0.25f
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

// The autoPlay loop: a hop every 1.6 s through the tabs, then one long glide back to the first.
private const val LOOP = 9f
private val Script = listOf(0.9f to 1, 2.5f to 2, 4.1f to 3, 5.7f to 4, 7.3f to 0)
private const val USER_HOLD = 3f

private fun text(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = text(34f, 41f, FontWeight.Bold, Color.White, -0.6f)
private val CaptionStyle = text(15f, 20f, FontWeight.Normal, Mute)
private val LabelStyle = text(11f, 13f, FontWeight.Medium, Ink)

private fun clamp01(value: Float) = value.coerceIn(0f, 1f)

private fun tabX(i: Int, count: Int, width: Float) = SIDE + (width - SIDE * 2) * (i + 0.5f) / count

/** The bar with a round bite centred at (cx, top + cy), its shoulders rounded by FILLET. In points. */
private fun outline(width: Float, height: Float, top: Float, cx: Float, cy: Float) = Path().apply {
    fun arc(x: Float, y: Float, r: Float, from: Float, to: Float, steps: Int) {
        for (k in 0..steps) {
            val a = from + (to - from) * k / steps
            lineTo(x + r * cos(a), y + r * sin(a))
        }
    }
    val pi = Math.PI.toFloat()
    val d = sqrt((NOTCH_R + FILLET).pow(2) - (FILLET - cy).pow(2))
    moveTo(0f, top + height)
    lineTo(0f, top + CORNER)
    arc(CORNER, top + CORNER, CORNER, pi, 1.5f * pi, CORNER_STEPS)
    arc(cx - d, top + FILLET, FILLET, -0.5f * pi, atan2(cy - FILLET, d), FILLET_STEPS)
    arc(cx, top + cy, NOTCH_R, atan2(FILLET - cy, -d), atan2(FILLET - cy, d), NOTCH_STEPS)
    arc(cx + d, top + FILLET, FILLET, atan2(cy - FILLET, -d), -0.5f * pi, FILLET_STEPS)
    arc(width - CORNER, top + CORNER, CORNER, 1.5f * pi, 2f * pi, CORNER_STEPS)
    lineTo(width, top + height)
    close()
}

private fun DrawScope.mark(icon: BubbleTabBarIcon, path: Path, x: Float, y: Float, scale: Float, alpha: Float, line: Float) {
    if (alpha <= 0.001f) return
    withTransform({
        translate(x, y)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-ICON / 2, -ICON / 2)
    }) {
        val stroke = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, Ink, alpha = alpha, style = stroke)
        for ((cx, cy, r) in icon.circles) drawCircle(Ink, r, Offset(cx, cy), alpha = alpha, style = stroke)
    }
}

/**
 * A white tab bar on a dark screen whose active tab rises out of the bar as a round bubble sitting
 * in a bite cut from the bar's edge. On a change the bite closes up, the bubble glides to the new
 * tab with a little overshoot and the bite opens under it again, 40 ms behind, like a liquid.
 */
@Composable
fun BubbleTabBar(
    tabs: List<BubbleTabBarTab> = BubbleTabBarSamples,
    initialIndex: Int = 0,
    /** Hops through every tab by itself every LOOP seconds, then glides back to the first. */
    autoPlay: Boolean = false,
    onChange: ((index: Int, tab: BubbleTabBarTab) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)
    val list by rememberUpdatedState(tabs)
    val still by rememberUpdatedState(reduceMotion)
    val paths = remember(tabs) { tabs.map { PathParser().parsePathString(it.icon.d).toPath() } }

    var index by remember { mutableIntStateOf(initialIndex) }
    var touches by remember { mutableIntStateOf(0) }
    // Positions in points; -1 until the width is known.
    val bubbleX = remember { Animatable(-1f) }
    val notchX = remember { Animatable(-1f) }
    val targetX = remember { Animatable(-1f) }
    // 0 → 1 over HOP_CLOSE at the start of each hop: how far the bite may have closed.
    val hop = remember { Animatable(1f) }
    // Read by the loop and the taps, which outlive the composition they started in.
    var barWidth by remember { mutableFloatStateOf(0f) }

    fun select(next: Int, auto: Boolean) {
        val width = barWidth
        if (width <= 0f) return
        val count = list.size
        val x = tabX(next, count, width)
        if (next == index && abs(bubbleX.value - x) < 0.5f) return
        val spec = if (abs(next - index) > 1) LongHop else Hop
        index = next
        scope.launch { targetX.snapTo(x) }
        scope.launch {
            hop.snapTo(0f)
            if (still) hop.snapTo(1f) else hop.animateTo(1f, tween((HOP_CLOSE * 1000).toInt(), easing = QuadOut))
        }
        scope.launch { if (still) bubbleX.snapTo(x) else bubbleX.animateTo(x, spec) }
        scope.launch {
            if (still) {
                notchX.snapTo(x)
            } else {
                delay((NOTCH_DELAY * 1000).toLong())
                notchX.animateTo(x, spec)
            }
        }
        if (!auto) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        changed?.invoke(next, list[next])
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay || list.isEmpty()) return@LaunchedEffect
        var origin = System.nanoTime()
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            origin = System.nanoTime()
            if (index != 0) {
                // Back to the first tab the way the loop ends, then the loop starts over from there.
                select(0, auto = true)
                origin += ((LOOP - Script.last().first) * 1e9f).toLong()
            }
        }
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((time, tab) in Script) {
                until(time)
                select(tab % list.size, auto = true)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Screen)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val width = maxWidth.value
        val count = tabs.size
        val pitch = (width - SIDE * 2) / count
        val barTotal = BAR_H + bottom

        // A new width puts the bubble on its tab at once.
        LaunchedEffect(width, count) {
            barWidth = width
            val x = tabX(index, count, width)
            bubbleX.snapTo(x)
            notchX.snapTo(x)
            targetX.snapTo(x)
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (top + 24f).dp)) {
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                tabs.forEachIndexed { i, tab ->
                    val alpha by animateFloatAsState(if (i == index) 1f else 0f, tween((PAGE_FADE * 1000).toInt(), easing = CssEase), label = "page")
                    Column(Modifier.graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText(tab.title, style = TitleStyle, maxLines = 1)
                        BasicText(tab.caption, style = CaptionStyle, maxLines = 1)
                    }
                }
            }
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (share in listOf(0.72f, 0.56f, 0.64f)) {
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp)).background(RowFill).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(RowMark))
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val room = maxWidth
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.width(room * share).height(10.dp).clip(CircleShape).background(RowMark))
                                Box(Modifier.width(room * share * 0.6f).height(8.dp).clip(CircleShape).background(RowMark))
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((ROOM + barTotal).dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val k = density
                withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                    val bx = bubbleX.value
                    // Nothing to place until the width is known.
                    if (bx < 0f) return@withTransform
                    // How much the bite is closed: as soon as a hop starts, and opening again as the bubble arrives.
                    val flat = min(hop.value, clamp01(abs(bx - targetX.value) / (pitch * REACH)))
                    val lift = REST_Y - HOP_RISE * flat
                    // The bite stays clear of the bar's rounded corners even when the bubble overshoots.
                    val cx = notchX.value.coerceIn(tabX(0, count, width) - 1.5f, tabX(count - 1, count, width) + 1.5f)
                    drawPath(outline(width, barTotal, ROOM, cx, REST_Y - FLAT_LIFT * flat), Bar)
                    for (i in 0 until count) {
                        val near = clamp01(1f - abs(bx - tabX(i, count, width)) / (pitch * REACH))
                        mark(tabs[i].icon, paths[i], tabX(i, count, width), ROOM + ICON_Y, 0.8f + 0.2f * (1f - near), 1f - near, 1.8f)
                    }
                    drawCircle(Bar, BUBBLE_R, Offset(bx, ROOM + lift))
                    for (i in 0 until count) {
                        val near = clamp01(1f - abs(bx - tabX(i, count, width)) / (pitch * REACH))
                        mark(tabs[i].icon, paths[i], bx, ROOM + lift, 1.1f - 0.1f * near, near, 2f)
                    }
                }
            }
            tabs.forEachIndexed { i, tab ->
                Box(
                    Modifier
                        .offset(x = (tabX(i, count, width) - pitch / 2).dp, y = (ROOM - 24f).dp)
                        .size(pitch.dp, (BAR_H + 24f).dp)
                        .semantics {
                            role = Role.Tab
                            selected = i == index
                            contentDescription = tab.label
                        }
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            touches++
                            select(i, auto = false)
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BasicText(tab.label, Modifier.padding(top = (24f + LABEL_Y).dp), style = LabelStyle, maxLines = 1)
                }
            }
        }
    }
}
