package com.ngocdevv.nativecook.blocks.onboarding.glowgestureguide

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class GlowGestureGuideRow(val title: String, val subtitle: String, val gesture: GlowGestureGuideKind)

val GlowGestureGuideSamples = listOf(
    GlowGestureGuideRow("Go forward", "Tap the screen", GlowGestureGuideKind.Tap),
    GlowGestureGuideRow("Pause and Seek", "Hold and slide left or right", GlowGestureGuideKind.Hold),
    GlowGestureGuideRow("Go back", "Tap the left edge", GlowGestureGuideKind.Edge),
    GlowGestureGuideRow("Move between stories", "Swipe left or right", GlowGestureGuideKind.Swipe),
)

private val Sky = listOf(Color(0xFF1D4F8F), Color(0xFF3A2C8A), Color(0xFF2A1259))
private val Floor = Color(12, 8, 24)
private val Glow = arrayOf(
    0f to Color(255, 138, 61).copy(alpha = 0.62f),
    0.45f to Color(224, 65, 47).copy(alpha = 0.38f),
    1f to Color(224, 65, 47).copy(alpha = 0f),
)

// Layout, in points.
private const val MARGIN = 24f
private const val HEADER_TOP = 48f
private const val SUBTITLE_W = 280f
private const val ROWS_TOP = 200f
private const val ROW_H = 80f
private const val ROW_GAP = 14f
private const val PITCH = ROW_H + ROW_GAP
private const val ICON = 46f
private const val ICON_LEFT = 20f
private const val TEXT_LEFT = ICON_LEFT + ICON + 18f
// A row's title and subtitle, stacked.
private const val TEXT_H = 43f
private const val RADIUS = 20f
private const val FOOTER_GAP = 40f
private const val FLOOR_H = 160f
// The glow: its radius, where its orbit is centred (a share of the width, points below the first
// row's top) and the orbit's radii.
private const val GLOW_R = 170f
private const val GLOW_X = 0.62f
private const val GLOW_Y = 70f
private const val ORBIT_X = 46f
private const val ORBIT_Y = 64f

// The highlight springs between rows (response 0.35 s at damping ratio 0.85: stiffness 322.27,
// damping 30.52) and stretches with its distance to the row it is heading for. The row under it
// grows by ACTIVE_SCALE and brightens.
private val Spring = spring<Float>(dampingRatio = 0.85f, stiffness = 322.27f)
private const val STRETCH = 0.3f
private const val STRETCH_MAX = 22f
private const val ACTIVE_SCALE = 0.05f
private const val TITLE_DIM = 0.82f
private const val SUBTITLE_DIM = 0.55f
private const val SUBTITLE_LIT = 0.7f

// The autoPlay loop: the highlight stops 1.35 s on each row, twice round from "Go forward", which is
// where it rests at the start and the end; the glow goes once round its orbit in the same time.
private const val LOOP = 10.8f
private const val STOP = 1.35f
private const val FIRST_HOP = 0.65f
private val Hops = listOf(1, 2, 3, 0, 1, 2, 3, 0)
private const val USER_HOLD = 3f

private fun text(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = text(26f, 32f, FontWeight.SemiBold, Color.White).copy(textAlign = TextAlign.Center)
private val SubtitleStyle = text(16f, 21f, FontWeight.Normal, Color.White.copy(alpha = 0.6f)).copy(textAlign = TextAlign.Center)
private val RowTitleStyle = text(19f, 23f, FontWeight.SemiBold, Color.White)
private val RowSubtitleStyle = text(16f, 20f, FontWeight.Normal, Color.White)
private val FooterStyle = text(17f, 22f, FontWeight.SemiBold, Color.White)

/**
 * A guide to the gestures of a story viewer on a deep blue-to-violet screen with a warm glow
 * drifting behind it. A frosted highlight springs from row to row, stretching as it travels; the
 * row it lands on grows and brightens and its hand icon acts the gesture out.
 */
@Composable
fun GlowGestureGuide(
    title: String = "Watching Stories",
    subtitle: String = "You can use these gestures to control playback.",
    rows: List<GlowGestureGuideRow> = GlowGestureGuideSamples,
    footer: String = "Tap to keep watching",
    /** The row highlighted first. */
    initialIndex: Int = 0,
    /** Walks the highlight through the rows by itself, twice every LOOP seconds. */
    autoPlay: Boolean = false,
    onSelect: ((index: Int, row: GlowGestureGuideRow) -> Unit)? = null,
    /** Called when the footer is tapped: the guide is done. */
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelect)
    val list by rememberUpdatedState(rows)
    val still by rememberUpdatedState(reduceMotion)

    var index by remember { mutableIntStateOf(initialIndex) }
    var touches by remember { mutableIntStateOf(0) }
    val highlight = remember { Animatable(initialIndex * PITCH) }
    // Seconds since each row was last chosen; each rests at the end of its little performance.
    val since = remember(rows.size) { List(rows.size) { Animatable(PLAY_FOR) } }
    // Turns of the glow's orbit since the screen appeared. Read while drawing only.
    val orbit = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { orbit.floatValue = ((it - start) / 1e9f / LOOP) % 1f }
        }
    }

    fun select(next: Int, auto: Boolean) {
        if (next == index) return
        index = next
        scope.launch { if (still) highlight.snapTo(next * PITCH) else highlight.animateTo(next * PITCH, Spring) }
        if (!still) {
            scope.launch {
                since[next].snapTo(0f)
                since[next].animateTo(PLAY_FOR, tween((PLAY_FOR * 1000).toInt(), easing = LinearEasing))
            }
        }
        if (!auto) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        selected?.invoke(next, list[next])
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay || list.isEmpty()) return@LaunchedEffect
        var origin = System.nanoTime()
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            origin = System.nanoTime()
            if (index != 0) {
                // Back to the first row the way the loop ends, then the loop starts over from there.
                select(0, auto = true)
                origin += ((LOOP - FIRST_HOP - (Hops.size - 1) * STOP) * 1e9f).toLong()
            }
        }
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            Hops.forEachIndexed { k, row ->
                until(FIRST_HOP + k * STOP)
                select(row % list.size, auto = true)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Sky[1])) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val width = maxWidth.value
        val cardW = width - MARGIN * 2
        val rowsTop = top + ROWS_TOP

        Box(
            Modifier.fillMaxSize().drawBehind {
                val k = density
                drawRect(Brush.verticalGradient(Sky))
                val a = orbit.floatValue * 2f * PI.toFloat()
                val c = Offset((width * GLOW_X + ORBIT_X * cos(a)) * k, (rowsTop + GLOW_Y + ORBIT_Y * sin(a)) * k)
                drawCircle(Brush.radialGradient(*Glow, center = c, radius = GLOW_R * k), GLOW_R * k, c)
                val floorTop = size.height - FLOOR_H * k
                drawRect(
                    Brush.verticalGradient(listOf(Floor.copy(alpha = 0f), Floor.copy(alpha = 0.85f)), floorTop, size.height),
                    Offset(0f, floorTop),
                    Size(size.width, FLOOR_H * k),
                )
            },
        )

        Column(
            Modifier.offset(y = (top + HEADER_TOP).dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(title, style = TitleStyle)
            BasicText(subtitle, Modifier.padding(top = 8.dp).width(SUBTITLE_W.dp), style = SubtitleStyle)
        }

        Box(
            Modifier
                .offset(x = MARGIN.dp, y = rowsTop.dp)
                // Height and place are read while laying out and drawing, so the motion does not recompose.
                .layout { measurable, _ ->
                    val h = ROW_H + min(STRETCH_MAX, abs(highlight.value - index * PITCH) * STRETCH)
                    val placeable = measurable.measure(Constraints.fixed(cardW.dp.roundToPx(), h.dp.roundToPx()))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val h = ROW_H + min(STRETCH_MAX, abs(highlight.value - index * PITCH) * STRETCH)
                    translationY = (highlight.value + (ROW_H - h) / 2f) * density
                }
                .clip(RoundedCornerShape(RADIUS.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0f))))
                .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(RADIUS.dp)),
        )

        rows.forEachIndexed { i, row ->
            // 1 when the highlight sits on this row, 0 a row away.
            fun active() = (1f - abs(highlight.value - i * PITCH) / PITCH).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = MARGIN.dp, y = (rowsTop + i * PITCH).dp)
                    .size(cardW.dp, ROW_H.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "${row.title}. ${row.subtitle}"
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        touches++
                        select(i, auto = false)
                    }
                    .graphicsLayer {
                        val s = 1f + ACTIVE_SCALE * active()
                        scaleX = s
                        scaleY = s
                    },
            ) {
                Canvas(Modifier.offset(x = ICON_LEFT.dp, y = ((ROW_H - ICON) / 2f).dp).size(ICON.dp)) {
                    drawGestureIcon(row.gesture, since[i].value, Color.White)
                }
                Column(Modifier.offset(x = TEXT_LEFT.dp, y = ((ROW_H - TEXT_H) / 2f).dp).padding(end = (TEXT_LEFT + 12f).dp)) {
                    BasicText(row.title, Modifier.graphicsLayer { alpha = TITLE_DIM + (1f - TITLE_DIM) * active() }, style = RowTitleStyle, maxLines = 1)
                    BasicText(
                        row.subtitle,
                        Modifier.graphicsLayer { alpha = SUBTITLE_DIM + (SUBTITLE_LIT - SUBTITLE_DIM) * active() },
                        style = RowSubtitleStyle,
                        maxLines = 1,
                    )
                }
            }
        }

        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (max(bottom, 16f) + FOOTER_GAP).dp)
                .semantics { role = Role.Button }
                .clickable(source, indication = null) {
                    touches++
                    onDone?.invoke()
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            BasicText(footer, Modifier.height(22.dp).graphicsLayer { alpha = if (pressed) 0.6f else 0.9f }, style = FooterStyle, maxLines = 1)
        }
    }
}
