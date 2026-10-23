package com.ngocdevv.nativecook.blocks.navigation.livetimertab

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

val SampleParagraphs = listOf(
    "Few things fill a kitchen the way warm bread and melting cheese do. The smell drifts out of the oven long before the timer ends, and everyone nearby finds a reason to come and look.",
    "Here a whole wheel of brie bakes on a ring of baguette slices rubbed with garlic. A little rosemary and a thread of olive oil are all it needs: the cheese turns soft and glossy while the bread crisps at its edges.",
    "It suits a quiet evening for two as well as a table full of friends. Bring it out in its dish while it still bubbles, and let people tear and dip as they talk.",
)
val SampleList = listOf("1 wheel of brie, about 250 g", "1 baguette, sliced", "3 cloves of garlic", "2 sprigs of rosemary", "Olive oil and flaky salt")

private val Page = Color(0xFFFCFCFC)
private val Bar = Color.White
private val Ink = Color(0xFF111111)
private val Mute = Color(0xFF8A8A8A)
private val Body = Color(0xFF3A3A3A)
private val Inactive = Color(0xFF9A9A9A)
private val BoxFill = Color(0xFFE6E6E6)
private val HeroFill = Color(0xFFEFEFEF)
private val RowFill = Color(0xFFEDEDED)

// The greys of the black button and of the light box, 0 to 1.
private const val BUTTON_GREY = 0x16 / 255f
private const val BOX_GREY = 0xE6 / 255f

// The cover behind the status bar fades out over this height under it, and comes in over this much scrolling.
private const val COVER_FADE = 16f
private const val COVER_OVER = 40f
private const val CONTROL_SIZE = 19f
private const val PILL_SIZE = 17f

// The widths of the first line of the profile's rows.
private val Rows = listOf(150f, 190f, 120f)

/** A circle of the picture: its middle from the picture's own, its radius and its grey. */
private class Dot(val x: Float, val y: Float, val r: Float, val color: Color)

// The picture at the top of the page, seen from above: a board, a pan with a wheel of brie in a ring
// of bread, and two bowls.
private val HeroArt = listOf(
    Dot(0f, 132f, 84f, Color(0xFFDCDCDC)),
    Dot(0f, 132f, 66f, Color(0xFF1C1C1C)),
    Dot(0f, 132f, 58f, Color(0xFF2B2B2B)),
    Dot(49f, 132f, 11f, Color(0xFFCFCFCF)),
    Dot(24.5f, 174.4f, 11f, Color(0xFFCFCFCF)),
    Dot(-24.5f, 174.4f, 11f, Color(0xFFCFCFCF)),
    Dot(-49f, 132f, 11f, Color(0xFFCFCFCF)),
    Dot(-24.5f, 89.6f, 11f, Color(0xFFCFCFCF)),
    Dot(24.5f, 89.6f, 11f, Color(0xFFCFCFCF)),
    Dot(49f, 132f, 7.5f, Color(0xFFE6E6E6)),
    Dot(24.5f, 174.4f, 7.5f, Color(0xFFE6E6E6)),
    Dot(-24.5f, 174.4f, 7.5f, Color(0xFFE6E6E6)),
    Dot(-49f, 132f, 7.5f, Color(0xFFE6E6E6)),
    Dot(-24.5f, 89.6f, 7.5f, Color(0xFFE6E6E6)),
    Dot(24.5f, 89.6f, 7.5f, Color(0xFFE6E6E6)),
    Dot(0f, 132f, 34f, Color(0xFFF4F4F4)),
    Dot(0f, 132f, 28f, Color(0xFFFFFFFF)),
    Dot(-9f, 125f, 2.5f, Color(0xFF8A8A8A)),
    Dot(8f, 138f, 2.5f, Color(0xFF8A8A8A)),
    Dot(3f, 120f, 2f, Color(0xFFB4B4B4)),
    Dot(-6f, 141f, 2f, Color(0xFFB4B4B4)),
    Dot(-136f, 66f, 28f, Color(0xFFDCDCDC)),
    Dot(-136f, 66f, 21f, Color(0xFFF7F7F7)),
    Dot(134f, 196f, 32f, Color(0xFFDCDCDC)),
    Dot(134f, 196f, 24f, Color(0xFFC9C9C9)),
)

// The outer tabs' icons in a 26 dp box.
private val Hat = Path().apply {
    moveTo(7.5f, 17.2f)
    cubicTo(3.2f, 16.6f, 2.6f, 10.2f, 7.4f, 9.6f)
    cubicTo(8f, 5.6f, 18f, 5.6f, 18.6f, 9.6f)
    cubicTo(23.4f, 10.2f, 22.8f, 16.6f, 18.5f, 17.2f)
    lineTo(18.5f, 21f)
    quadraticTo(18.5f, 22.5f, 17f, 22.5f)
    lineTo(9f, 22.5f)
    quadraticTo(7.5f, 22.5f, 7.5f, 21f)
    close()
    moveTo(7.5f, 19f)
    lineTo(18.5f, 19f)
}
private val Person = Path().apply {
    addOval(Rect(13f - 4.2f, 8.6f - 4.2f, 13f + 4.2f, 8.6f + 4.2f))
    moveTo(4.8f, 22.5f)
    cubicTo(4.8f, 17.5f, 8.5f, 15.6f, 13f, 15.6f)
    cubicTo(17.5f, 15.6f, 21.2f, 17.5f, 21.2f, 22.5f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun timerText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = timerText(28f, TITLE_LINE, FontWeight.Bold, Ink)
private val SubtitleStyle = timerText(15f, SUBTITLE_LINE, FontWeight.Normal, Mute)
private val StartStyle = timerText(17f, 22f, FontWeight.Medium, Color.White)
private val BodyStyle = timerText(17f, BODY_LINE, FontWeight.Normal, Body)
private val ListTitleStyle = timerText(20f, 26f, FontWeight.SemiBold, Ink)
private val ItemStyle = timerText(17f, BODY_LINE + 4f, FontWeight.Normal, Body)
private val TitleFit = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp, stepSize = 0.5.sp)

/**
 * A recipe page with a timer and a three-tab bar. "Start oven" turns into a box that counts down;
 * once that box has scrolled off the screen, the stopwatch in the tab bar swells into a pill that
 * carries the time, and when the box comes back the pill collapses and hands the time back to it.
 *
 * @param paragraphs The text of the page, one string for each paragraph.
 * @param profileTitle The title of the page behind the third tab.
 * @param duration What the timer counts down from, in seconds.
 * @param autoPlay Starts the timer, scrolls the page away and back and stops the timer by itself, every [LOOP] seconds.
 * @param onStop Called with the seconds that were left when the timer is stopped by hand.
 * @param onDone Called when the time is up.
 * @param onTabChange Called with 0 for the recipe or 1 for the profile.
 */
@Composable
fun LiveTimerTab(
    title: String = "Baked Brie & Garlic Bread",
    subtitle: String = "25 min · Serves 4",
    paragraphs: List<String> = SampleParagraphs,
    listTitle: String = "Ingredients",
    list: List<String> = SampleList,
    profileTitle: String = "Profile",
    startLabel: String = "Start oven",
    duration: Int = TIMER,
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onStop: ((remaining: Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onTabChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val rollShift = with(density) { ROLL_SHIFT.dp.roundToPx() }
    // The loop and the count outlive a recomposition: they read what the caller passes through these.
    val started by rememberUpdatedState(onStart)
    val done by rememberUpdatedState(onDone)
    val tabChanged by rememberUpdatedState(onTabChange)
    val total by rememberUpdatedState(duration)

    // When the timer was started, on the system's nanosecond clock; null while it is idle.
    var startedAt by remember { mutableStateOf<Long?>(null) }
    // Whole seconds counted since then.
    var counted by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    // The timer's control has scrolled out of what shows of the page.
    var away by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // 0 the black button, 1 the light box.
    val run = remember { Animatable(0f) }
    // 0 the stopwatch, 1 the pill.
    val pill = remember { Animatable(0f) }
    // 0 the recipe, 1 the profile.
    val turn = remember { Animatable(0f) }
    // The pill is handing the time back to the box, and how far under the box it is, in dp.
    var ghosting by remember { mutableStateOf(false) }
    var ghostDrop by remember { mutableFloatStateOf(0f) }
    // The scroll the loop has under way.
    var gliding by remember { mutableStateOf<Job?>(null) }

    val running = startedAt != null
    // The time lives in the tab bar while its box is out of sight.
    val live = running && (away || tab != 0)
    val time = clock(duration - 1 - counted)

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

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun select() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
        gliding?.cancel()
    }

    fun start(at: Long = System.nanoTime()) {
        if (startedAt != null) return
        counted = 0
        startedAt = at
        scope.launch { run.animateTo(1f, tween((START * 1000).toInt(), easing = EaseOut)) }
        started?.invoke()
    }

    fun stop() {
        if (startedAt == null) return
        startedAt = null
        scope.launch { run.animateTo(0f, tween((STOP * 1000).toInt(), easing = EaseOut)) }
    }

    /** Scrolls the page to [y] dp in [seconds], as the loop does. */
    fun glide(y: Float, seconds: Float) {
        gliding?.cancel()
        gliding = scope.launch { scroll.animateScrollTo(with(density) { y.dp.roundToPx() }, tween((seconds * 1000).toInt(), easing = Glide)) }
    }

    fun choose(index: Int) {
        if (index == tab) return
        tab = index
        tabChanged?.invoke(index)
        scope.launch { turn.animateTo(index.toFloat(), tween((TAB_FADE * 1000).toInt())) }
    }

    // The time moves to the tab bar, or back to its box.
    LaunchedEffect(live) {
        // The time rises back to its box only when the box is there to take it.
        ghosting = !live && startedAt != null && tab == 0 && !reducedMotion
        pill.animateTo(if (live) 1f else 0f, tween(((if (live) FORM else COLLAPSE) * 1000).toInt(), easing = if (live && !reducedMotion) Swell else EaseOut))
    }

    // The count: one tick a second, each due at a whole number of seconds from the start.
    LaunchedEffect(startedAt) {
        val from = startedAt ?: return@LaunchedEffect
        var n = 1
        while (true) {
            delay(max(0L, (from + n * 1_000_000_000L - System.nanoTime()) / 1_000_000))
            if (n >= total) {
                stop()
                done?.invoke()
                break
            }
            counted = n
            n++
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to rest; the loop's first stretch covers the way there.
            stop()
            choose(0)
            glide(0f, SCROLL_HOME)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(START_AT)
            start(origin + (START_AT * 1e9f).toLong())
            until(SCROLL_AT)
            glide(AUTO_SCROLL, SCROLL_UP)
            until(BACK_AT)
            glide(0f, SCROLL_BACK)
            until(STOP_AT)
            stop()
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val width = maxWidth.value
        val height = maxHeight.value
        val barHeight = TAB_BAR + max(insets.calculateBottomPadding().value, TAB_BOTTOM)
        // What shows of the page: from under the status bar to where the lines start to fade.
        val shownTop by rememberUpdatedState(top)
        val shownBottom by rememberUpdatedState(height - barHeight)
        val pillY by rememberUpdatedState(height - barHeight + TAB_BAR / 2f)

        // The control leaves when its middle passes an edge of what shows, and returns a little further in.
        LaunchedEffect(scroll, density) {
            snapshotFlow { scroll.value }.collect { y ->
                val middle = CONTROL_CENTRE - y / density.density
                val edge = if (away) HYSTERESIS else -HYSTERESIS
                val now = middle < shownTop + edge || middle > shownBottom - edge
                if (now != away) {
                    ghostDrop = pillY - middle
                    away = now
                }
            }
        }

        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - turn.value }
                .then(if (tab == 0) Modifier else Modifier.clearAndSetSemantics {})
                .verticalScroll(scroll, enabled = tab == 0)
                .heightIn(min = (height + AUTO_SCROLL).dp)
                .padding(bottom = (barHeight + FADE).dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(HERO.dp).clipToBounds().drawBehind {
                    drawRect(HeroFill)
                    surface(0.dp) { _, _, w, _ -> for (dot in HeroArt) drawCircle(dot.color, dot.r, Offset(w / 2f + dot.x, dot.y)) }
                },
            )
            Column(Modifier.padding(horizontal = MARGIN.dp)) {
                Spacer(Modifier.height(TITLE_GAP.dp))
                BasicText(title, style = TitleStyle, maxLines = 1, autoSize = TitleFit, modifier = Modifier.fillMaxWidth().height(TITLE_LINE.dp).semantics { heading() })
                Spacer(Modifier.height(SUBTITLE_GAP.dp))
                BasicText(subtitle, style = SubtitleStyle, maxLines = 1, modifier = Modifier.height(SUBTITLE_LINE.dp))
                Spacer(Modifier.height(CONTROL_GAP.dp))

                // The time that rises from the pill is drawn over the text below.
                Box(Modifier.fillMaxWidth().height(CONTROL.dp).zIndex(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .graphicsLayer { alpha = 1f - clamp01(pill.value) }
                            // The button contracts into the box without the page being composed again.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(mix(constraints.maxWidth.toFloat(), BOX_WIDTH.dp.toPx(), run.value).roundToInt(), CONTROL.dp.roundToPx()),
                                )
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            .drawBehind {
                                val grey = mix(BUTTON_GREY, BOX_GREY, run.value)
                                drawRoundRect(Color(grey, grey, grey), cornerRadius = CornerRadius(CONTROL_RADIUS.dp.toPx()))
                            }
                            .semantics { contentDescription = if (running) "$time, stop the timer" else startLabel }
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = tab == 0, role = Role.Button) {
                                tap()
                                if (running) {
                                    val left = duration - counted
                                    stop()
                                    onStop?.invoke(left)
                                } else {
                                    start()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(startLabel, style = StartStyle, maxLines = 1, softWrap = false, modifier = Modifier.graphicsLayer { alpha = 1f - run.value })
                    }
                    // The box's time is not in the box: on its way back from the pill it rises to its
                    // place from under the page, growing, and the box fades in around it.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = running,
                        enter = if (reducedMotion) EnterTransition.None else slideInVertically(tween<IntOffset>(RollMs, easing = EaseOut)) { -rollShift } + fadeIn(tween(RollMs, easing = EaseOut)),
                        exit = fadeOut(tween((TIME_OUT * 1000).toInt())),
                        modifier = Modifier.graphicsLayer {
                            val way = clamp01(pill.value)
                            alpha = run.value * (if (ghosting) clamp01((1f - way) / GHOST_IN) * mix(1f, 0.5f, way) else 1f - way)
                            translationY = if (ghosting) (way * ghostDrop).dp.toPx() else 0f
                            val size = if (ghosting) mix(1f, GHOST_SCALE, way) else 1f
                            scaleX = size
                            scaleY = size
                        },
                    ) {
                        RollingTime(time, CONTROL_SIZE, Ink, FontWeight.Medium, still = reducedMotion)
                    }
                }

                for (paragraph in paragraphs) {
                    Spacer(Modifier.height(BODY_GAP.dp))
                    BasicText(paragraph, style = BodyStyle)
                }
                Spacer(Modifier.height((BODY_GAP + 8f).dp))
                BasicText(listTitle, style = ListTitleStyle, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                for (item in list) BasicText(item, style = ItemStyle, maxLines = 1)
            }
        }

        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = turn.value }
                .then(if (tab == 1) Modifier else Modifier.clearAndSetSemantics {})
                .background(Page)
                .padding(start = MARGIN.dp, end = MARGIN.dp, top = (top + TITLE_GAP).dp),
        ) {
            BasicText(profileTitle, style = TitleStyle, maxLines = 1, modifier = Modifier.height(TITLE_LINE.dp).semantics { heading() })
            for (line in Rows) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(BoxFill, RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Box(Modifier.size(line.dp, 12.dp).background(BoxFill, CircleShape))
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.size(90.dp, 12.dp).background(RowFill, CircleShape))
                    }
                }
            }
        }

        // The cover behind the status bar comes in as the picture's last points pass under it.
        Box(
            Modifier
                .fillMaxWidth()
                .height((top + COVER_FADE).dp)
                .graphicsLayer { alpha = clamp01((scroll.value / density.density - (HERO - top - COVER_OVER)) / COVER_OVER) * (1f - turn.value) }
                .background(Brush.verticalGradient(0f to Page, top / (top + COVER_FADE) to Page, 1f to Page.copy(alpha = 0f))),
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(FADE.dp).background(Brush.verticalGradient(listOf(Bar.copy(alpha = 0f), Bar))))
            Box(Modifier.fillMaxWidth().height(barHeight.dp).background(Bar)) {
                TabIcon(Hat, if (tab == 0) Ink else Inactive, "Recipe", tab == 0, Modifier.offset((TAB_INSET - TAB_BAR / 2f).dp, 0.dp)) {
                    if (tab != 0) select()
                    choose(0)
                }
                TabIcon(Person, if (tab == 1) Ink else Inactive, profileTitle, tab == 1, Modifier.offset((width - TAB_INSET - TAB_BAR / 2f).dp, 0.dp)) {
                    if (tab != 1) select()
                    choose(1)
                }

                val capsule = remember { Path() }
                Box(
                    Modifier
                        .offset(((width - PILL_WIDTH) / 2f).dp, 0.dp)
                        .size(PILL_WIDTH.dp, TAB_BAR.dp)
                        .semantics { contentDescription = if (live) "Timer, $time" else "Timer" }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            select()
                            choose(0)
                            gliding?.cancel()
                            gliding = scope.launch { scroll.animateScrollTo(0) }
                        }
                        .drawBehind { surface(0.dp) { _, _, w, h -> drawStopwatch(pill.value, reducedMotion, w / 2f, h / 2f) } },
                    contentAlignment = Alignment.Center,
                ) {
                    // The time shows through the pill only: while the pill is small, so is what is seen of it.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = if (reducedMotion) clamp01(pill.value) else 1f }
                            .drawWithContent {
                                val swollen = if (reducedMotion) 1f else pill.value
                                val w = mix(DIAL, PILL_WIDTH, swollen).dp.toPx()
                                val h = mix(DIAL, PILL_HEIGHT, swollen).dp.toPx()
                                val middle = size.height / 2f + mix(DIAL_DROP, 0f, swollen).dp.toPx()
                                capsule.rewind()
                                capsule.addRoundRect(RoundRect(Rect(Offset((size.width - w) / 2f, middle - h / 2f), Size(w, h)), CornerRadius(h / 2f)))
                                clipPath(capsule) { this@drawWithContent.drawContent() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = live,
                            enter = if (reducedMotion) fadeIn(tween(RollMs)) else slideInVertically(tween<IntOffset>(RollMs, easing = EaseOut)) { -rollShift } + fadeIn(tween(RollMs, easing = EaseOut)),
                            exit = fadeOut(tween((TIME_OUT * 1000).toInt())),
                        ) {
                            RollingTime(time, PILL_SIZE, Color.White, FontWeight.SemiBold, still = reducedMotion)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The stopwatch around ([x], [y]), in dp. Its ring is the pill: it widens, darkens and rises to the
 * middle of the bar by [pill]. Under reduced motion the ring stays as it is and fades out under the pill.
 */
private fun DrawScope.drawStopwatch(pill: Float, reducedMotion: Boolean, x: Float, y: Float) {
    val way = clamp01(pill)
    val swollen = if (reducedMotion) 1f else pill
    val w = mix(DIAL, PILL_WIDTH, swollen)
    val h = mix(DIAL, PILL_HEIGHT, swollen)
    val drop = mix(DIAL_DROP, 0f, swollen)
    val inset = TAB_STROKE / 2f
    val hand = HAND * 0.7071f

    if (reducedMotion) {
        drawCircle(Ink, DIAL / 2f - inset, Offset(x, y + DIAL_DROP), alpha = 1f - way, style = Stroke(TAB_STROKE))
        drawLine(Ink, Offset(x, y + DIAL_DROP), Offset(x + hand, y + DIAL_DROP - hand), TAB_STROKE, StrokeCap.Round, alpha = 1f - way)
    } else {
        drawLine(Ink, Offset(x, y + drop), Offset(x + hand, y + drop - hand), TAB_STROKE, StrokeCap.Round, alpha = 1f - clamp01(pill * 2.5f))
    }

    // The dash above the ring.
    val moved = if (reducedMotion) way else pill
    val tickY = y + mix(DIAL_DROP, 0f, moved) - mix(DIAL, PILL_HEIGHT, moved) / 2f - TICK_GAP
    drawLine(Ink, Offset(x - TICK / 2f + inset, tickY), Offset(x + TICK / 2f - inset, tickY), TAB_STROKE, StrokeCap.Round)

    val shown = if (reducedMotion) way else 1f
    drawRoundRect(Ink, Offset(x - w / 2f, y + drop - h / 2f), Size(w, h), CornerRadius(h / 2f), alpha = clamp01(swollen) * shown)
    drawRoundRect(
        Ink,
        Offset(x - w / 2f + inset, y + drop - h / 2f + inset),
        Size(w - TAB_STROKE, h - TAB_STROKE),
        CornerRadius((h - TAB_STROKE) / 2f),
        alpha = shown,
        style = Stroke(TAB_STROKE),
    )
}

@Composable
private fun TabIcon(path: Path, color: Color, label: String, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(TAB_BAR.dp)
            .semantics {
                contentDescription = label
                selected = chosen
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(TAB_ICON.dp)) {
            surface(0.dp) { _, _, _, _ -> drawPath(path, color, style = Stroke(TAB_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}
