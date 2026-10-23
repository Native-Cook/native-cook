package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Background = Color(0xFF000000)
private val Ink = Color(0xFFFFFFFF)
private val Mute = Color(0xFF8E8E93)
private val CardColor = Color(0xFF131316)
private val ChipColor = Color(0xFF1C1C1F)
private val Track = Color(0xFF1C1C1F)
private val ThumbColor = Color(0xFF2E2E33)
private val ButtonColor = Color(0xFF141417)
private val TagColor = Color(0xFFFF3B4E)
private val TabBarColor = Color(0xFF1C1C1F)
private val TabOn = Color(0xFF2A2A40)
private val Accent = LevelColors.Normal

// Layout, in dp.
private const val MARGIN = 16f
private const val NAV = 56f
private const val ROUND_BUTTON = 40f
private const val SEGMENT = 36f
private const val DONUT = 164f
private const val CHIP_HEIGHT = 62f
private const val TAB_HEIGHT = 58f
private const val BUSIEST_SHIFT = 14f

// The share of the morph over which the old text leaves, and the new one arrives at the end.
private const val HAND_OVER = 0.4f

// Motion.
private const val THUMB_FOR = 250
private const val DONUT_DAMPING = 14f
private const val DONUT_STIFFNESS = 120f
private const val DONUT_MASS = 0.9f
private const val JUMP_DIP = 0.3f
private const val JUMP_FOR = 220

// A swipe this long over the grid switches the view; it counts once it has gone 12 dp sideways.
private const val SWIPE = 40f
private const val SWIPE_START = 12f

// The autoPlay timeline, in seconds from the start of each loop.
const val LOOP = 8f
private val Steps = listOf(1.0f to 1, 5.4f to 0)

// Seconds after the last touch before the screen returns to the first view and the loop starts again.
private const val USER_HOLD = 3f

private val Levels = listOf("Low" to LevelColors.Low, "Normal" to LevelColors.Normal, "High" to LevelColors.High)

// Reanimated's spring in Compose's terms: the same mass-spring system, divided through by its mass.
private val DonutSpring = spring(
    dampingRatio = DONUT_DAMPING / (2 * sqrt(DONUT_STIFFNESS * DONUT_MASS)),
    stiffness = DONUT_STIFFNESS / DONUT_MASS,
    visibilityThreshold = 0.0001f,
)
private val ThumbEasing = Easing { 1 - (1 - it) * (1 - it) * (1 - it) }

// Reanimated's default timing curve.
private val QuadInOut = Easing { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

internal fun stressText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = stressText(17f, 22f, FontWeight.SemiBold, Ink)
private val SegmentStyle = stressText(14f, 18f, FontWeight.Medium, Mute)
private val DateStyle = stressText(14f, 18f, FontWeight.SemiBold, Ink)
private val TagStyle = stressText(11f, 14f, FontWeight.SemiBold, Ink)
private val SectionStyle = stressText(15f, 20f, FontWeight.SemiBold, Ink)
private val ChipLabelStyle = stressText(12f, 16f, FontWeight.Medium, Mute)
private val CountStyle = stressText(22f, 26f, FontWeight.SemiBold, Ink)
private val UnitStyle = stressText(12f, 16f, FontWeight.Normal, Mute)
private val CardTitleStyle = stressText(12f, 16f, FontWeight.Medium, Mute)
private val CardValueStyle = stressText(22f, 28f, FontWeight.SemiBold, Ink)
private val TabStyle = stressText(11f, 14f, FontWeight.SemiBold, Ink)

/** How far the old text (side 0) has gone or the new one (side 1) has come, as the morph runs 0 → 1. */
private fun handOver(m: Float, side: Int): Float {
    val from = if (side == 0) 0f else 1 - HAND_OVER
    return ((m - from) / HAND_OVER).coerceIn(0f, 1f)
}

/**
 * A stress patterns screen: a heat map of dots, one per two-hour period of the week, and a donut of
 * how many were low, normal or high. Switching to four weeks sends every dot to its day in the new
 * grid in a stagger while the grid shrinks, the donut's segments spring to their new lengths and
 * the counts roll; switching back runs it in reverse.
 *
 * @param days The first view of the heat map: two-hour periods of a week.
 * @param weeks The second: days of four weeks.
 * @param autoPlay Switches to the four weeks and back by itself every [LOOP] seconds.
 */
@Composable
fun StressDotGridDonut(
    title: String = "Patterns",
    days: Period = Days,
    weeks: Period = Weeks,
    donutLabel: String = "Levels",
    tagLabel: String = "Preview",
    autoPlay: Boolean = false,
    onViewChange: ((Period, Int) -> Unit)? = null,
) {
    val still by rememberUpdatedState(rememberReducedMotion())
    val periods by rememberUpdatedState(listOf(days, weeks))
    val viewChanged by rememberUpdatedState(onViewChange)
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableLongStateOf(0L) }

    val tallies = remember(days, weeks) { listOf(days.counts(), weeks.counts()) }
    val shares = remember(tallies) { tallies.map { t -> t.map { it.toFloat() / max(1, t.sum()) } } }

    // 0 shows the week's periods, 1 the four weeks; the donut follows on a spring of its own.
    val morph = remember { Animatable(0f) }
    val donut = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val fade = remember { Animatable(1f) }
    val thumb = remember { Animatable(0f) }

    fun choose(index: Int, auto: Boolean) {
        if (index == view) return
        view = index
        if (!auto) hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        viewChanged?.invoke(periods[index], index)
        val target = index.toFloat()
        scope.launch {
            if (still) {
                morph.snapTo(target)
                donut.snapTo(target)
                thumb.snapTo(target)
                fade.snapTo(JUMP_DIP)
                fade.animateTo(1f, tween(JUMP_FOR, easing = QuadInOut))
            }
        }
        if (still) return
        scope.launch { thumb.animateTo(target, tween(THUMB_FOR, easing = ThumbEasing)) }
        scope.launch { morph.animateTo(target, tween((MORPH_FOR * 1000).roundToInt(), easing = LinearEasing)) }
        scope.launch { donut.animateTo(target, DonutSpring) }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        lastTouch = System.nanoTime()
        touches += 1
    }

    // The autoPlay loop taps the tabs a finger would, each at its time from the start of the loop.
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay(((lastTouch + (USER_HOLD * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            choose(0, true)
            delay((MORPH_FOR * 1000).toLong() + 300)
        }
        var start = System.nanoTime()
        while (true) {
            for ((at, index) in Steps) {
                delay(((start + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                choose(index, true)
            }
            start += (LOOP * 1e9).toLong()
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bottom = max(insets.calculateBottomPadding().value, 12f)
    val first = { 1 - handOver(morph.value, 0) }
    val second = { handOver(morph.value, 1) }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(top = insets.calculateTopPadding())
                .padding(horizontal = MARGIN.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(NAV.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton { Icon(Icons.Filled.Person, null, tint = Ink, modifier = Modifier.size(17.dp)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = TitleStyle)
                    Icon(Icons.Filled.Lock, null, tint = Ink, modifier = Modifier.size(14.dp))
                }
                RoundButton { InfoGlyph(20f) }
            }

            BoxWithConstraints(
                Modifier
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(SEGMENT.dp)
                    .clip(CircleShape)
                    .background(Track)
                    .padding(3.dp),
            ) {
                val segmentWidth = maxWidth / 2
                Box(
                    Modifier
                        .offset { IntOffset((thumb.value * segmentWidth.toPx()).roundToInt(), 0) }
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(ThumbColor),
                )
                Row(Modifier.fillMaxSize()) {
                    periods.forEachIndexed { index, period ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { selected = index == view }
                                .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                                    touched()
                                    choose(index, false)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(period.label, style = SegmentStyle, color = if (index == view) Ink else Mute)
                        }
                    }
                }
            }

            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(days.range, style = DateStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = first() })
                    Text(weeks.range, style = DateStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = second() })
                }
                Tag(tagLabel)
            }

            BoxWithConstraints(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                DotGrid(
                    width = maxWidth.value,
                    days = days,
                    weeks = weeks,
                    morph = { morph.value },
                    opacity = { fade.value },
                    modifier = Modifier
                        .semantics { contentDescription = "Stress by ${if (view == 0) "period, last 7 days" else "day, last 4 weeks"}" }
                        .pointerInput(Unit) {
                            val start = SWIPE_START.dp.toPx()
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                touched()
                                var dx = 0f
                                var dy = 0f
                                // Like a pan that starts once it goes 12 dp sideways and fails if it goes 12 dp up or down first.
                                var sideways: Boolean? = null
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    dx += change.positionChange().x
                                    dy += change.positionChange().y
                                    if (sideways == null) {
                                        if (abs(dx) > start) sideways = true else if (abs(dy) > start) sideways = false
                                    }
                                    if (!event.changes.fastAny { it.pressed }) break
                                }
                                if (sideways == true) {
                                    touched()
                                    val distance = dx / density
                                    if (distance < -SWIPE) choose(1, false) else if (distance > SWIPE) choose(0, false)
                                }
                            }
                        },
                )
            }

            SectionTitle("Stress Levels", tagLabel)
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LevelDonut(DONUT, Levels.map { it.second }, shares[0], shares[1], { donut.value }, donutLabel)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Levels.forEachIndexed { i, (label, color) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(CHIP_HEIGHT.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(ChipColor)
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).background(color, CircleShape))
                                Text(label, style = ChipLabelStyle)
                            }
                            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                                Roller(tallies[view][i].toString(), CountStyle, 26.dp, still)
                                Box(Modifier.padding(start = 4.dp, bottom = 3.dp)) {
                                    Text(days.unit, style = UnitStyle, modifier = Modifier.graphicsLayer { alpha = first() })
                                    Text(weeks.unit, style = UnitStyle, modifier = Modifier.graphicsLayer { alpha = second() })
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("Stressful Periods", tagLabel)
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardColor)
                    .padding(16.dp),
            ) {
                Busiest(days, Modifier.graphicsLayer {
                    val m = handOver(morph.value, 0)
                    alpha = 1 - m
                    translationY = -BUSIEST_SHIFT.dp.toPx() * m
                })
                Busiest(weeks, Modifier.graphicsLayer {
                    val m = handOver(morph.value, 1)
                    alpha = m
                    translationY = BUSIEST_SHIFT.dp.toPx() * (1 - m)
                })
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((TAB_HEIGHT + bottom + 40).dp)
                .background(Brush.verticalGradient(listOf(Background.copy(alpha = 0f), Background))),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(TabBarColor)
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(percent = 50))
                .padding(4.dp),
        ) {
            Tab(Icons.Filled.WatchLater, 20f, "Today", on = false)
            Tab(Icons.Filled.GridView, 19f, "Patterns", on = true)
            Tab(Icons.Filled.Book, 19f, "Playbook", on = false)
        }
    }
}

@Composable
private fun RoundButton(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(ROUND_BUTTON.dp)
            .background(ButtonColor, CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The bare "i" of the info button, drawn from the icon font's glyph (a 512 grid). */
@Composable
private fun InfoGlyph(size: Float) {
    Canvas(Modifier.size(size.dp)) {
        val unit = this.size.width / 512f
        val width = 40f * unit
        drawLine(Ink, Offset(196f * unit, 220f * unit), Offset(260f * unit, 220f * unit), width, StrokeCap.Round)
        drawLine(Ink, Offset(260f * unit, 220f * unit), Offset(260f * unit, 392f * unit), width, StrokeCap.Round)
        drawLine(Ink, Offset(187f * unit, 396f * unit), Offset(325f * unit, 396f * unit), width, StrokeCap.Round)
        drawCircle(Ink, 32f * unit, Offset(256f * unit, 128f * unit))
    }
}

@Composable
private fun Tag(label: String) {
    Row(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TagColor.copy(alpha = 0.1f))
            .border(1.dp, TagColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Lock, null, tint = TagColor, modifier = Modifier.size(10.dp))
        Text(label, style = TagStyle)
    }
}

@Composable
private fun SectionTitle(title: String, tag: String) {
    Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SectionStyle, modifier = Modifier.weight(1f))
        Tag(tag)
    }
}

@Composable
private fun Busiest(period: Period, modifier: Modifier) {
    Column(modifier) {
        Text(period.busiestTitle, style = CardTitleStyle)
        Text(period.busiest, style = CardValueStyle, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Tab(icon: ImageVector, size: Float, label: String, on: Boolean) {
    Column(
        Modifier
            .width(86.dp)
            .height(TAB_HEIGHT.dp)
            .background(if (on) TabOn else Color.Transparent, RoundedCornerShape(percent = 50)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = if (on) Accent else Ink, modifier = Modifier.size(size.dp))
        Text(label, style = TabStyle, color = if (on) Accent else Ink)
    }
}
