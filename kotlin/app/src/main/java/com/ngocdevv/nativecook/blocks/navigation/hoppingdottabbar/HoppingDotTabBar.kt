package com.ngocdevv.nativecook.blocks.navigation.hoppingdottabbar

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** An icon in a 24 × 24 box, from SVG path data made of M, L, C and Z: its outline, and the lines inside it. */
class HoppingIcon(outline: String, detail: String) {
    internal val outline = PathParser().parsePathString(outline).toPath()
    internal val detail = PathParser().parsePathString(detail).toPath()
}

object HoppingIcons {
    val Home = HoppingIcon(
        outline = "M4.5 11L12 4.5L19.5 11L19.5 19.5L4.5 19.5Z",
        detail = "M10 19.5L10 16C10 14.9 10.9 14 12 14C13.1 14 14 14.9 14 16L14 19.5",
    )
    val Messages = HoppingIcon(
        outline = "M5 19L5 11.5C5 7.36 8.36 4 12.5 4C16.64 4 20 7.36 20 11.5C20 15.64 16.64 19 12.5 19Z",
        detail = "M9.5 11.5L9.51 11.5M12.5 11.5L12.51 11.5M15.5 11.5L15.51 11.5",
    )
    val Saved = HoppingIcon(
        outline = "M12 19.5C12 19.5 4 15 4 9.5C4 7 6 5 8.3 5C9.9 5 11.3 5.9 12 7.2C12.7 5.9 14.1 5 15.7 5C18 5 20 7 20 9.5C20 15 12 19.5 12 19.5Z",
        detail = "M7 9.6C7 8.6 7.7 7.8 8.6 7.8",
    )
    val Profile = HoppingIcon(
        outline = "M12 4C13.93 4 15.5 5.57 15.5 7.5C15.5 9.43 13.93 11 12 11C10.07 11 8.5 9.43 8.5 7.5C8.5 5.57 10.07 4 12 4Z" +
            "M8.5 14.5L15.5 14.5C16.88 14.5 18 15.62 18 17C18 18.38 16.88 19.5 15.5 19.5L8.5 19.5C7.12 19.5 6 18.38 6 17C6 15.62 7.12 14.5 8.5 14.5Z",
        detail = "M10.6 8.1C11 9 13 9 13.4 8.1",
    )
}

/**
 * @param cards The heights of the placeholder cards on the tab's page.
 * @param avatar A round picture with two lines above the cards.
 */
class HoppingTab(val title: String, val icon: HoppingIcon, val cards: List<Int>, val avatar: Boolean = false)

val SampleHoppingTabs = listOf(
    HoppingTab("Home", HoppingIcons.Home, listOf(150, 96, 96)),
    HoppingTab("Messages", HoppingIcons.Messages, listOf(72, 72, 72, 72)),
    HoppingTab("Saved", HoppingIcons.Saved, listOf(96, 150, 96)),
    HoppingTab("Profile", HoppingIcons.Profile, listOf(96, 96), avatar = true),
)

private val Field = Color(0xFF244FF6)
private val Panel = Color(0xFF2E59FE)
private val Idle = Color(0xFFB4BEEF)
private val Active = Color(0xFF3B55E6)
private val Card = Color.White.copy(alpha = 0.14f)
private val Line = Color.White.copy(alpha = 0.4f)

private const val SIDE = 16.0
private const val BAR_HEIGHT = 78.0
private const val TOP_RADIUS = 8.0
private const val BOTTOM_RADIUS = 32.0

// The page runs on behind the bar this far, so that it shows through a notch.
private const val PANEL_UNDER = 24.0

// The notch: its width along the bar's top edge and its depth at rest. The shoulders and the tip
// are rounded by control points this share of the width from each end of a side.
private const val NOTCH = 44.0
private const val NOTCH_DEPTH = 16.0
private const val SHOULDER = 0.22
private const val TIP = 0.16

// The dot rests in the notch, its centre this far under the bar's top edge.
private const val DOT = 10.0
private const val DOT_SINK = 5.0
private const val ICON = 24.0
private const val ICON_Y = 40.0
private const val ICON_LIFT = 4.0
private const val ICON_STROKE = 1.6f

// The page: its content's inset in the panel, and the title's place under the safe area.
private const val INSET = 24.0
private const val TITLE_TOP = 37.0
private const val AVATAR = 72.0

/** Seconds. The loop goes once round the tabs: a hop every HOP_EVERY from HOP_AT. */
const val LOOP = 9.0
private const val HOP_AT = 2.0
private const val HOP_EVERY = 1.8

// One hop. The dot leaves straight up and starts across after LIFT; it is in the air for AIR plus
// AIR_PER_TAB for each tab it crosses, on a parabola ARC high for a neighbour and ARC_PER_TAB
// higher for each tab more.
private const val LIFT = 0.1
private const val AIR = 0.4
private const val AIR_PER_TAB = 0.05
private const val AIR_MAX = 0.6
private const val ARC = 14.0
private const val ARC_PER_TAB = 23.0
private const val ARC_MIN = 8.0
private const val ARC_MAX = 90.0

// The notch it left closes; the one it lands in starts to open just before it arrives, goes a
// fifth too deep under its weight and comes back, while the dot bounces once.
private const val CLOSE = 0.2
private const val OPEN_BEFORE = 0.05
private const val OPEN = 0.32
private const val PRESS = 2.6
private const val BOUNCE = 2.0
private const val BOUNCE_FOR = 0.16

// An icon takes or loses its colour and its lift in this time.
private const val TINT = 0.2

// The page that leaves slides away from where the dot goes; the new one comes from that side.
private const val PAGE_OUT = 0.25
private const val PAGE_IN = 0.28
private const val PAGE_SLIDE = 16.0

// The clock of a hop runs this long, well past its last movement.
private const val REST = 1.2

// With reduced motion the notch and the dot fade out, then in at the new tab, each in this time.
private const val FADE = 0.15

// After a touch the loop waits, then goes back to its first tab and waits for that to end.
private const val USER_HOLD = 3.0
private const val SETTLE = 1.2

/**
 * Where a hop started: everything that was moving is picked up where it was.
 *
 * @param from The tab whose page is leaving.
 * @param x The dot's place along the bar, in tabs.
 * @param up Its height over its rest.
 * @param depth Each notch's depth, 0 to 1.
 * @param tint Each icon's colour, 0 to 1.
 * @param presence How much of the leaving page shows.
 * @param shift How far it has slid.
 */
private class Hop(
    val target: Int,
    val from: Int,
    val x: Double,
    val up: Double,
    val depth: DoubleArray,
    val tint: DoubleArray,
    val presence: Double,
    val shift: Double,
)

/**
 * @param page The page that shows.
 * @param presence How much of it shows.
 * @param shift How far aside it is.
 * @param cover With reduced motion: how far the shown tab's notch is filled in.
 */
private class Sample(
    val depth: DoubleArray,
    val tint: DoubleArray,
    val dotX: Double,
    val dotUp: Double,
    val dotAlpha: Double,
    val page: Int,
    val presence: Double,
    val shift: Double,
    val cover: Double,
)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

private fun smooth(t: Double): Double {
    val u = clamp01(t)
    return u * u * (3 - 2 * u)
}

/** Ease-out that goes past 1 by a fifth and comes back. */
private fun pressed(t: Double): Double {
    val u = clamp01(t) - 1
    return 1 + (PRESS + 1) * u * u * u + PRESS * u * u
}

private fun resting(tab: Int, count: Int) = Hop(tab, tab, tab.toDouble(), 0.0, DoubleArray(count), DoubleArray(count), 0.0, 0.0)

/** Everything that moves, [t] seconds into a hop. */
private fun sample(hop: Hop, t: Double, reduced: Boolean): Sample {
    val count = hop.depth.size
    if (reduced) {
        val arrived = t >= FADE
        val shown = if (arrived) hop.target else hop.from
        val visible = if (arrived) clamp01((t - FADE) / FADE) else hop.presence * (1 - clamp01(t / FADE))
        return Sample(
            depth = DoubleArray(count) { if (it == shown) 1.0 else 0.0 },
            tint = DoubleArray(count) { if (it == shown) visible else 0.0 },
            dotX = shown.toDouble(), dotUp = 0.0, dotAlpha = visible, page = shown, presence = visible, shift = 0.0, cover = 1 - visible,
        )
    }

    val target = hop.target.toDouble()
    val distance = abs(target - hop.x)
    val air = min(AIR + AIR_PER_TAB * distance, AIR_MAX)
    val arc = (ARC + ARC_PER_TAB * (distance - 1)).coerceIn(ARC_MIN, ARC_MAX)
    val flown = clamp01(t / air)
    val landing = t - (air - OPEN_BEFORE)
    val depth = DoubleArray(count)
    val tint = DoubleArray(count)
    for (i in 0 until count) {
        val closing = hop.depth[i] * (1 - clamp01(t / CLOSE) * clamp01(t / CLOSE))
        val fading = hop.tint[i] * (1 - clamp01(t / TINT))
        depth[i] = if (i == hop.target) max(closing, if (landing > 0) pressed(landing / OPEN) else 0.0) else closing
        tint[i] = if (i == hop.target) max(fading, clamp01(landing / TINT)) else fading
    }
    val bounce = clamp01((t - air) / BOUNCE_FOR)
    val side = if (target > hop.x) 1.0 else if (target < hop.x) -1.0 else 0.0
    val gone = clamp01(t / PAGE_OUT)
    val come = smooth(landing / PAGE_IN)
    return Sample(
        depth = depth,
        tint = tint,
        dotX = hop.x + (target - hop.x) * clamp01((t - LIFT) / (air - LIFT)),
        // Gravity: a parabola from where the dot was to its rest, then one small bounce.
        dotUp = if (t < air) hop.up * (1 - flown) + 4 * arc * flown * (1 - flown) else 4 * BOUNCE * bounce * (1 - bounce),
        dotAlpha = 1.0,
        page = if (landing < 0) hop.from else hop.target,
        presence = if (landing < 0) hop.presence * (1 - gone) else come,
        shift = if (landing < 0) hop.shift + (-side * PAGE_SLIDE - hop.shift) * gone else side * PAGE_SLIDE * (1 - come),
        cover = 0.0,
    )
}

private fun Path.notch(x: Double, d: Double) {
    val half = NOTCH / 2
    lineTo((x - half).toFloat(), 0f)
    cubicTo((x - half + NOTCH * SHOULDER).toFloat(), 0f, (x - NOTCH * TIP).toFloat(), d.toFloat(), x.toFloat(), d.toFloat())
    cubicTo((x + NOTCH * TIP).toFloat(), d.toFloat(), (x + half - NOTCH * SHOULDER).toFloat(), 0f, (x + half).toFloat(), 0f)
}

/** The bar's outline in its own box, in dp: a notch in the top edge over each tab that has one. */
private fun Path.bar(width: Double, depth: DoubleArray) {
    // A quarter circle as one curve.
    val k = 0.5523
    val w = width.toFloat()
    val h = BAR_HEIGHT.toFloat()
    val top = TOP_RADIUS.toFloat()
    val bottom = BOTTOM_RADIUS.toFloat()
    val topK = (TOP_RADIUS * k).toFloat()
    val bottomK = (BOTTOM_RADIUS * k).toFloat()
    val tab = width / depth.size
    rewind()
    moveTo(top, 0f)
    for (i in depth.indices) {
        val d = depth[i] * NOTCH_DEPTH
        if (d < 0.05) continue
        notch(tab * (i + 0.5), d)
    }
    lineTo(w - top, 0f)
    cubicTo(w - top + topK, 0f, w, top - topK, w, top)
    lineTo(w, h - bottom)
    cubicTo(w, h - bottom + bottomK, w - bottom + bottomK, h, w - bottom, h)
    lineTo(bottom, h)
    cubicTo(bottom - bottomK, h, 0f, h - bottom + bottomK, 0f, h - bottom)
    lineTo(0f, top)
    cubicTo(0f, top - topK, top - topK, 0f, top, 0f)
    close()
}

private val IconStroke = Stroke(width = ICON_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawIcon(icon: HoppingIcon, color: Color, alpha: Float) {
    drawPath(icon.outline, color, alpha = 0.18f * alpha)
    drawPath(icon.outline, color, alpha = alpha, style = IconStroke)
    drawPath(icon.detail, color, alpha = 0.7f * alpha, style = IconStroke)
}

private val TitleStyle = TextStyle(
    color = Color.White,
    fontSize = 34.sp,
    lineHeight = 41.sp,
    fontWeight = FontWeight.Bold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Seconds on the clock a hop is timed against. */
private fun clock() = System.nanoTime() / 1e9

private fun Density.px(value: Double) = (value * density).toFloat()

/**
 * A tab bar whose indicator is a white dot resting in a notch of the bar's top edge. Choosing a
 * tab makes the dot hop there on a parabola, higher the further it goes, while the notch it left
 * closes and a new one opens under it as it lands. The page above changes with it.
 *
 * @param initialTab The tab that is open at first, from 0.
 * @param field The screen.
 * @param panel The lighter page on it.
 * @param idle The icons when idle.
 * @param active The chosen icon.
 * @param autoPlay Goes round the tabs by itself every [LOOP] seconds.
 * @param onSelect Called with the tab a tap chose.
 */
@Composable
fun HoppingDotTabBar(
    tabs: List<HoppingTab> = SampleHoppingTabs,
    initialTab: Int = 0,
    field: Color = Field,
    panel: Color = Panel,
    bar: Color = Color.White,
    idle: Color = Idle,
    active: Color = Active,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    // The loop outlives a recomposition: it reads these as state.
    val count by rememberUpdatedState(tabs.size)
    val first by rememberUpdatedState(initialTab.coerceIn(0, max(tabs.size - 1, 0)))
    val picked by rememberUpdatedState(onSelect)

    // The open tab; null until one is chosen.
    var chosen by remember { mutableStateOf<Int?>(null) }
    // The hop in progress or the last one, and when it started.
    var hop by remember { mutableStateOf(resting(first, count)) }
    var start by remember { mutableDoubleStateOf(-1e9) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The frame clock runs while a hop does. Read while drawing only.
    val now = remember { mutableDoubleStateOf(clock()) }

    fun index() = chosen ?: first

    /** Opens a tab: the same path for a tap and for the loop, which is silent. */
    fun select(target: Int, byHand: Boolean) {
        if (target == index()) return
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val moment = clock()
        val current = sample(hop, moment - start, reduced)
        // Everything that was moving is picked up where it was.
        hop = Hop(target, current.page, current.dotX, current.dotUp, current.depth, current.tint, current.presence, current.shift)
        start = moment
        chosen = target
        if (byHand) picked?.invoke(target)
    }

    LaunchedEffect(start) {
        val until = start + REST
        while (clock() < until) now.doubleValue = withFrameNanos { it } / 1e9
        now.doubleValue = clock()
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The loop starts over from the tab it was given.
            select(first, byHand = false)
            delay((SETTLE * 1000).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (origin + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (k in 0 until count) {
                until(HOP_AT + k * HOP_EVERY)
                select((index() + 1) % count, byHand = false)
            }
            until(LOOP)
            origin += (LOOP * 1e9).toLong()
        }
    }

    // Where everything is at the clock's moment. Read in draw and layer blocks only, so a moving
    // value redraws without recomposing the screen.
    val state by remember(reduced) { derivedStateOf { sample(hop, now.doubleValue - start, reduced) } }
    val outline = remember { Path() }
    val filler = remember { Path() }

    BoxWithConstraints(modifier.fillMaxSize().background(field).clipToBounds()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value.toDouble()
        val bottom = insets.calculateBottomPadding().value.toDouble()
        val barWidth = maxWidth.value - SIDE * 2
        val barTop = maxHeight.value - max(bottom, 20.0) - BAR_HEIGHT
        val tabWidth = barWidth / max(tabs.size, 1)

        Box(Modifier.offset(x = SIDE.dp).size(barWidth.dp, (barTop + PANEL_UNDER).dp).background(panel))

        tabs.forEachIndexed { k, tab ->
            Page(
                tab = tab,
                modifier = Modifier
                    .offset(x = (SIDE + INSET).dp, y = (top + TITLE_TOP).dp)
                    .width((barWidth - INSET * 2).dp)
                    .graphicsLayer {
                        val on = state.page == k
                        alpha = if (on) state.presence.toFloat() else 0f
                        translationX = if (on) px(state.shift) else 0f
                    }
                    .then(if (k == index()) Modifier else Modifier.clearAndSetSemantics {}),
            )
        }

        Canvas(Modifier.offset(x = SIDE.dp, y = barTop.dp).size(barWidth.dp, BAR_HEIGHT.dp)) {
            val moment = state
            // Everything here is drawn in dp.
            scale(density, density, pivot = Offset.Zero) {
                outline.bar(barWidth, moment.depth)
                drawPath(outline, bar)
                if (moment.cover > 0) {
                    // One notch at rest, closed along the top edge: what fills it in.
                    val x = tabWidth * (moment.page + 0.5)
                    filler.rewind()
                    filler.moveTo((x - NOTCH / 2).toFloat(), 0f)
                    filler.notch(x, NOTCH_DEPTH)
                    filler.close()
                    drawPath(filler, bar, alpha = moment.cover.toFloat())
                }
                tabs.forEachIndexed { k, tab ->
                    val tint = moment.tint[k].toFloat()
                    translate(left = (tabWidth * (k + 0.5) - ICON / 2).toFloat(), top = (ICON_Y - ICON / 2).toFloat() - ICON_LIFT.toFloat() * tint) {
                        drawIcon(tab.icon, idle, 1f)
                        // The chosen colour fades in over the idle one.
                        if (tint > 0f) drawIcon(tab.icon, active, tint)
                    }
                }
            }
        }

        Row(Modifier.offset(x = SIDE.dp, y = barTop.dp).size(barWidth.dp, BAR_HEIGHT.dp)) {
            tabs.forEachIndexed { k, tab ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            touches++
                            select(k, byHand = true)
                        }
                        .semantics {
                            contentDescription = tab.title
                            selected = k == index()
                        },
                )
            }
        }

        // The dot flies over the page, so it is the top layer.
        Box(
            Modifier
                .size(DOT.dp)
                .graphicsLayer {
                    alpha = state.dotAlpha.toFloat()
                    translationX = px(SIDE + tabWidth * (state.dotX + 0.5) - DOT / 2)
                    translationY = px(barTop + DOT_SINK - DOT / 2 - state.dotUp)
                }
                .background(bar, CircleShape),
        )
    }
}

/** A tab's page: its title over placeholder cards. */
@Composable
private fun Page(tab: HoppingTab, modifier: Modifier = Modifier) {
    Column(modifier) {
        BasicText(tab.title, style = TitleStyle, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
        if (tab.avatar) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(AVATAR.dp).background(Card, CircleShape))
                Lines()
            }
        }
        tab.cards.forEach { height ->
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(height.dp).background(Card, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Lines()
            }
        }
    }
}

@Composable
private fun Lines() {
    Column {
        Box(Modifier.size(140.dp, 10.dp).background(Line, RoundedCornerShape(5.dp)))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(90.dp, 10.dp).background(Line, RoundedCornerShape(5.dp)))
    }
}
