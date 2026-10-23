package com.ngocdevv.nativecook.blocks.navigation.orbitingtoptabs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Tabs = listOf("NEW IN", "BROWSE", "ARCHIVE REVIVAL", "TRAIL RUNNER")
private val BarItems = listOf("HOME", "STYLE", "SEARCH", "SELL", "PROFILE")

private val Paper = Color.White
private val BarFill = Color.Black

// The tabs sit on the rim of a wheel whose hub is RADIUS dp below their line; neighbours are STEP
// degrees apart, which is PITCH dp along the rim.
private const val RADIUS = 560f
private const val STEP = 13f
private const val STEP_RADIANS = STEP * PI.toFloat() / 180f
private const val PITCH = RADIUS * STEP_RADIANS

// The middle of the tabs' line and the top of the pages, below the top safe-area inset.
private const val TABS_Y = 41f
private const val PAGES_Y = 61f
private const val TAB_WIDTH = 150f
private const val TAB_HEIGHT = 32f

// A tab one step from the centre is this faint, and so is every tab beyond.
private const val DIM = 0.55f

// The page that leaves moves at this part of the finger's speed, under the one that comes in.
private const val OUT = 0.85f

// Past the first and the last page a drag moves the pages by this part of the finger's way.
private const val RUBBER = 0.25f

// The settle, about 0.4 s, passing the page by a few dp: mass 1, stiffness 170, damping 21.
private val Settle = spring(dampingRatio = 0.8053f, stiffness = 170f, visibilityThreshold = 0.0005f)

// A release goes to the page the drag would reach in this many seconds at its speed.
private const val PROJECT = 0.18f

// Reduced motion: the pages cross-fade over this long.
private const val FADE = 250
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private const val BAR = 58f

private const val USER_HOLD = 3000L
private const val LOOP = 9f

// The loop that plays by itself: the page that is due, in seconds from the start of a loop.
private val Script = listOf(1.0f to 2, 3.0f to 3, 5.0f to 2, 5.5f to 1)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Top tabs set on an arc, like the rim of a large wheel whose hub is far below the screen. Paging
 * turns the wheel: the next tab climbs to the top centre and straightens while the page comes in
 * over the one that leaves, its picture trailing behind its text.
 *
 * @param tabs the tabs' names, in the order of their pages.
 * @param initialTab the tab the screen opens on.
 * @param barItems the names on the bottom bar; the first one is the current one.
 * @param autoPlay pages through the tabs by itself, back at the first page every [LOOP] seconds.
 */
@Composable
fun OrbitingTopTabs(
    modifier: Modifier = Modifier,
    tabs: List<String> = Tabs,
    initialTab: Int = 1,
    barItems: List<String> = BarItems,
    autoPlay: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Everything the functions below read is state: they are called long after this composition.
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val picked by rememberUpdatedState(onSelect)
    val last by rememberUpdatedState(tabs.lastIndex)
    val home by rememberUpdatedState(initialTab)

    var current by remember { mutableIntStateOf(initialTab) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The page in the middle of the screen, as a number: 1.5 is half way between two pages.
    val position = remember { Animatable(initialTab.toFloat()) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun turnTo(index: Int, velocity: Float = 0f) {
        scope.launch {
            if (reduced) position.animateTo(index.toFloat(), tween(FADE, easing = EaseOutCubic))
            else position.animateTo(index.toFloat(), Settle, initialVelocity = velocity)
        }
        if (index != current) {
            current = index
            picked?.invoke(index)
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun press(index: Int) {
        touched()
        if (index != current) tick()
        turnTo(index)
    }

    fun released(index: Int, velocity: Float) {
        if (index != current) tick()
        turnTo(index, velocity)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay(USER_HOLD)
            // Back to rest; the pages have settled by the time the loop first turns them.
            turnTo(home)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            // Each step turns to the page that is due at its time, whatever the steps before it did.
            for ((at, page) in Script) {
                until(at)
                turnTo(page)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The page is white, so the status bar's icons are dark while the block is open.
    DisposableEffect(view) {
        val bars = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val before = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) bars.isAppearanceLightStatusBars = before }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding().value
    val bottom = insets.calculateBottomPadding().value
    val pagesTop by rememberUpdatedState(top + PAGES_Y)
    val barHeight = BAR + bottom

    Box(
        modifier.fillMaxSize().background(Paper).pointerInput(Unit) {
            val tracker = VelocityTracker()
            var from = 0f
            var across = 0f
            // How many pixels of the drag make one page: the screen's width on the pages, the
            // tabs' spacing on the wheel.
            var perPage = 1f
            var onWheel = false
            detectHorizontalDragGestures(
                onDragStart = { at ->
                    onWheel = at.y < pagesTop.dp.toPx()
                    perPage = if (onWheel) PITCH.dp.toPx() else size.width.toFloat()
                    from = position.value
                    across = 0f
                    tracker.resetTracking()
                    touched()
                },
                onDragEnd = {
                    // Pages a second. A drag of the pages goes one page at most; the wheel may spin further.
                    val velocity = -tracker.calculateVelocity().x / perPage
                    val start = from.roundToInt()
                    val reach = if (onWheel) last else 1
                    val aim = (position.value + velocity * PROJECT).roundToInt()
                    released(aim.coerceIn(max(0, start - reach), min(last, start + reach)), velocity)
                },
                onDragCancel = { released(position.value.roundToInt().coerceIn(0, last), 0f) },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    across += amount
                    val to = from - across / perPage
                    val held = if (to < 0f) to * RUBBER else if (to > last) last + (to - last) * RUBBER else to
                    scope.launch { position.snapTo(held) }
                },
            )
        },
    ) {
        // The pages. The one that comes in lies over the one that leaves.
        BoxWithConstraints(Modifier.padding(top = pagesTop.dp, bottom = barHeight.dp).fillMaxSize().clipToBounds()) {
            val width = maxWidth.value
            val height = maxHeight.value
            tabs.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val away = index - position.value
                            if (reduced) alpha = 1f - min(1f, abs(away))
                            else translationX = size.width * (if (away >= 0f) away else OUT * away)
                        }
                        .then(if (index == current) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    // The picture trails the page while it comes in, and has caught up when the page is in place.
                    ShopPage(index, width, height, drift = { if (reduced) 0f else PARALLAX * (index - position.value).coerceIn(0f, 1f) })
                }
            }
        }

        // The wheel's rim: a tab is upright at the top centre, tilted and lower as it turns away.
        Box(Modifier.fillMaxWidth().height(pagesTop.dp).clipToBounds().background(Paper)) {
            tabs.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (top + TABS_Y - TAB_HEIGHT / 2f).dp)
                        .size(TAB_WIDTH.dp, TAB_HEIGHT.dp)
                        .graphicsLayer {
                            val away = index - position.value
                            alpha = 1f - (1f - DIM) * min(1f, abs(away))
                            if (reduced) {
                                // The tabs slide along a straight line.
                                translationX = (away * PITCH).dp.toPx()
                            } else {
                                val turn = away * STEP_RADIANS
                                translationX = (RADIUS * sin(turn)).dp.toPx()
                                translationY = (RADIUS * (1f - cos(turn))).dp.toPx()
                                rotationZ = away * STEP
                            }
                        }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { press(index) }
                        .semantics { selected = index == current },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = caps(), maxLines = 1)
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barHeight.dp).background(BarFill).padding(start = 8.dp, end = 8.dp, bottom = bottom.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            barItems.forEachIndexed { index, label ->
                BasicText(
                    label,
                    Modifier.weight(1f),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 7.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center,
                        textDecoration = if (index == 0) TextDecoration.Underline else null,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
