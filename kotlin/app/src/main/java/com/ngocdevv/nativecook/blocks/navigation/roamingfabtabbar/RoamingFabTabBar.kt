package com.ngocdevv.nativecook.blocks.navigation.roamingfabtabbar

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private val Page = Color.White
private val Ink = Color(0xFF111111)
private val Outline = Color(0xFF55555A)
private val Mute = Color(0xFF9A9AA0)
private val Placeholder = Color(0xFFF1F1F3)
private val Avatar = Color(0xFFE2E2E6)
private val AddFill = Color(0xFFEEF4FF)

// The widths of the first line of the nine rows and of the four cards.
private val Rows = listOf(120f, 150f, 100f, 140f, 110f, 130f, 152f, 104f, 136f)
private val Cards = listOf(110f, 150f, 130f, 120f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun pageText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = pageText(34f, TITLE_LINE, FontWeight.Bold, Ink)
private val HintStyle = pageText(16f, 21f, FontWeight.Normal, Mute)
private val TimeStyle = pageText(13f, 17f, FontWeight.Normal, Mute)

/**
 * A plain four-icon tab bar and one round button that changes place and purpose with the tab. The
 * icon of the chosen tab turns into a heavier drawing and wiggles; the button shrinks away on the
 * tabs that have no use for it, pops in again, and slides along an arc between its two places while
 * one glyph turns into the other.
 *
 * @param titles The titles of the four pages, in the order of the tabs.
 * @param times The times at the end of the rows of the last page.
 * @param accent The round button.
 * @param autoPlay Goes through the tabs by itself, every [LOOP] seconds.
 * @param onTabChange Called with the tab's place, 0 to 3.
 * @param onAction Called with the tab the round button was pressed on: 0 to start something, 3 to write.
 */
@Composable
fun RoamingFabTabBar(
    titles: List<String> = listOf("Home", "Search", "Add friends", "Chats"),
    searchHint: String = "Find people and rooms",
    addLabel: String = "Add",
    times: List<String> = listOf("9:41", "9:12", "Mon", "Sun", "Fri", "Thu", "Wed", "Tue", "Mon"),
    accent: Color = Color(0xFF1274FF),
    autoPlay: Boolean = false,
    onTabChange: ((index: Int) -> Unit)? = null,
    onAction: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through this.
    val tabChanged by rememberUpdatedState(onTabChange)

    // The tab that is chosen, and the one that was.
    var tab by remember { mutableIntStateOf(0) }
    var previous by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // How far the change of tab has run, 0 to 1 at a constant speed.
    val clock = remember { Animatable(1f) }
    // The button: 0 at the first tab's place and 1 at the last tab's, how much of it there is, and how far it is pressed in.
    val place = remember { Animatable(0f) }
    val shown = remember { Animatable(1f) }
    val pressed = remember { Animatable(0f) }
    val shadow = remember(accent) { SoftRoundRect(accent.copy(alpha = SHADOW_ALPHA), sigma = SHADOW_BLUR) }

    // The page is white: dark icons on the system bars while it shows.
    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (lightStatusBars != null) controller.isAppearanceLightStatusBars = lightStatusBars
            if (lightNavigationBars != null) controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun choose(next: Int) {
        if (next == tab) return
        val hides = next == 1 || next == 2
        val hidden = tab == 1 || tab == 2
        val target = if (next == 3) 1f else 0f
        fun timing(seconds: Float, ease: Easing) = tween<Float>((seconds * 1000).toInt(), easing = if (reducedMotion) LinearEasing else ease)
        previous = tab
        tab = next
        tabChanged?.invoke(next)
        scope.launch {
            clock.snapTo(0f)
            clock.animateTo(1f, tween((SWITCH * 1000).toInt(), easing = LinearEasing))
        }
        scope.launch {
            if (hides) {
                shown.animateTo(0f, timing(SHRINK, ShrinkEase))
            } else if (hidden) {
                // Out of sight, the button takes its new place at once and pops in there.
                place.snapTo(target)
                shown.animateTo(1f, timing(POP, PopEase))
            } else {
                place.animateTo(target, timing(if (reducedMotion) REDUCED_SLIDE else SLIDE, SlideEase))
            }
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to rest; the loop's first second covers the way there.
            choose(0)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, index) in Steps) {
                until(at)
                choose(index)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val pressing = remember { MutableInteractionSource() }
    val down by pressing.collectIsPressedAsState()
    LaunchedEffect(down) {
        pressed.animateTo(if (down) 1f else 0f, tween(((if (down) PRESS_IN else PRESS_OUT) * 1000).toInt()))
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val width = maxWidth.value
        val barTop = maxHeight.value - max(insets.calculateBottomPadding().value, BAR_BOTTOM) - BAR
        val homeX = width / 2f
        val chatX = width - CHAT_INSET

        for (index in 0..3) {
            Column(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // A page fades in when its tab is chosen and out when another is.
                        val changed = clamp01(clock.value * SWITCH / PAGE_FADE)
                        alpha = if (index == tab) changed else if (index == previous) 1f - changed else 0f
                    }
                    .then(if (index == tab) Modifier else Modifier.clearAndSetSemantics {})
                    .padding(start = MARGIN.dp, end = MARGIN.dp, top = (top + TITLE_TOP).dp),
            ) {
                BasicText(titles[index], style = TitleStyle, maxLines = 1, modifier = Modifier.height(TITLE_LINE.dp).semantics { heading() })
                Spacer(Modifier.height(6.dp))
                when (index) {
                    0 -> for (line in Cards) {
                        Spacer(Modifier.height(14.dp))
                        Column(
                            Modifier.fillMaxWidth().height(132.dp).background(Placeholder, RoundedCornerShape(24.dp)).padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(Modifier.size(line.dp, 12.dp).background(Avatar, CircleShape))
                            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                                repeat(4) { Box(Modifier.size(40.dp).background(Avatar, CircleShape).border(2.dp, Placeholder, CircleShape)) }
                            }
                            Box(Modifier.size(80.dp, 10.dp).background(Avatar, CircleShape))
                        }
                    }
                    1 -> {
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(44.dp).background(Placeholder, CircleShape), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.align(Alignment.TopStart).offset(16.dp, 14.dp).size(13.dp).border(2.dp, Mute, CircleShape))
                            Box(Modifier.align(Alignment.TopStart).offset(26.dp, 26.dp).size(6.dp, 2.dp).rotate(45f).background(Mute, CircleShape))
                            BasicText(searchHint, style = HintStyle, maxLines = 1, modifier = Modifier.padding(start = 44.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        People {}
                    }
                    2 -> People {
                        Box(Modifier.size(60.dp, 30.dp).background(AddFill, CircleShape), contentAlignment = Alignment.Center) {
                            BasicText(addLabel, style = pageText(14f, 18f, FontWeight.SemiBold, accent), maxLines = 1)
                        }
                    }
                    else -> People { row -> BasicText(times[row % times.size], style = TimeStyle, maxLines = 1) }
                }
            }
        }

        Box(Modifier.offset(0.dp, barTop.dp).fillMaxWidth().height((maxHeight.value - barTop).dp).background(Page))

        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, _ ->
                val t = clock.value
                val changed = clamp01(t * SWITCH / ICON_FADE)
                for (index in 0..3) {
                    val icon = Icons[index]
                    // The chosen tab's icon wiggles in on its pivot.
                    val wiggled = if (index == tab && !reducedMotion) t else 1f
                    val chosen = if (index == tab) changed else if (index == previous) 1f - changed else 0f
                    translate(w * (index + 0.5f) / 4f + icon.pivot.x, barTop + ICON_Y + icon.pivot.y) {
                        rotate(wiggle(wiggled), Offset.Zero) {
                            scale(swell(wiggled), swell(wiggled), Offset.Zero) {
                                translate(-icon.pivot.x, -icon.pivot.y) {
                                    drawPieces(icon.off, Outline, 1f - chosen)
                                    drawPieces(icon.on, Ink, chosen)
                                }
                            }
                        }
                    }
                }

                val travelled = place.value
                val there = shown.value
                // Under reduced motion the button does not travel: it fades out at one place and in at the other.
                val p = if (reducedMotion) (if (travelled < 0.5f) 0f else 1f) else travelled
                val radius = mix(HOME_SIZE, CHAT_SIZE, p) / 2f
                val size = max(0f, (if (reducedMotion) 1f else there) * (1f - PRESS * pressed.value))
                val fade = if (reducedMotion) clamp01(there) * abs(travelled * 2f - 1f) else 1f
                val glyph = Stroke(GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                translate(mix(homeX, chatX, p), barTop - mix(HOME_RISE, CHAT_RISE, p) - ARC * sin(PI.toFloat() * clamp01(p))) {
                    scale(size, size, Offset.Zero) {
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, -radius, -radius + SHADOW_Y, radius * 2f, radius * 2f, radius, fade) }
                        drawCircle(accent, radius, Offset.Zero, alpha = fade)
                        // One glyph turns into the other on the way across, and the plus makes a quarter turn as it shrinks away.
                        rotate(if (reducedMotion) 0f else -180f * p + 90f * (1f - clamp01(there)), Offset.Zero) {
                            drawPath(Plus, Color.White, alpha = (1f - clamp01(p)) * fade, style = glyph)
                        }
                        rotate(if (reducedMotion) 0f else 180f * (1f - p), Offset.Zero) {
                            scale(CHAT_SIZE / HOME_SIZE * COMPOSE_SCALE, CHAT_SIZE / HOME_SIZE * COMPOSE_SCALE, Offset.Zero) {
                                drawPath(Bubble, Color.White, alpha = clamp01(p) * fade, style = glyph)
                                drawPath(Pencil, Color.White, alpha = clamp01(p) * fade, style = glyph)
                            }
                        }
                    }
                }
            }
        }

        for (index in 0..3) {
            Box(
                Modifier
                    .offset((width * (index + 0.5f) / 4f - TAB_HIT).dp, (barTop + ICON_Y - TAB_HIT).dp)
                    .size((TAB_HIT * 2f).dp)
                    .semantics {
                        contentDescription = titles[index]
                        selected = tab == index
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                        touched()
                        if (tab != index) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        choose(index)
                    },
            )
        }

        if (tab == 0 || tab == 3) {
            val hit = if (tab == 3) CHAT_SIZE else HOME_SIZE
            Box(
                Modifier
                    .offset(((if (tab == 3) chatX else homeX) - hit / 2f).dp, (barTop - (if (tab == 3) CHAT_RISE else HOME_RISE) - hit / 2f).dp)
                    .size(hit.dp)
                    .semantics { contentDescription = if (tab == 3) "New chat" else "Start a room" }
                    .clickable(pressing, indication = null, role = Role.Button) {
                        touched()
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onAction?.invoke(tab)
                    },
            )
        }
    }
}

/** One drawing of an icon, drawn as one layer so that a cut-out detail does not show through while it fades. */
private fun DrawScope.drawPieces(pieces: List<Piece>, color: Color, opacity: Float) {
    if (opacity <= 0f) return
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-16f, -16f, 16f, 16f), androidx.compose.ui.graphics.Paint().apply { alpha = opacity })
        for (piece in pieces) {
            val shade = if (piece.paint == Coat.Stroke || piece.paint == Coat.Fill) color else Page
            if (piece.paint == Coat.Fill || piece.paint == Coat.CutFill) {
                drawPath(piece.path, shade)
            } else {
                drawPath(piece.path, shade, style = Stroke(piece.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        canvas.restore()
    }
}

/** Nine people: a round picture and two lines, with whatever the page puts at the end of a row. */
@Composable
private fun ColumnScope.People(end: @Composable (row: Int) -> Unit) {
    Rows.forEachIndexed { row, line ->
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Placeholder, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.size(line.dp, 12.dp).background(Avatar, CircleShape))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(80.dp, 10.dp).background(Avatar, CircleShape))
            }
            end(row)
        }
    }
}
