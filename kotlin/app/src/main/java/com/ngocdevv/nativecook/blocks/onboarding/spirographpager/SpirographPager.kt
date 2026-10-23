package com.ngocdevv.nativecook.blocks.onboarding.spirographpager

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Ink = Color(0xFF16305C)
private val Accent = Color(0xFF1B3F95)
private val Mute = Color(0xFF8A8F9A)

/** The artwork's centre, as a fraction of the screen's height. */
private const val ART_AT = 0.387f
private const val STROKE = 0.75f
private const val MARGIN = 24f
private const val BUTTON = 36f

/** The button's centre from the right edge and from the bottom safe area. */
private const val BUTTON_RIGHT = 41f
private const val BUTTON_UP = 58f
private const val TITLE_GAP = 16f

/** A down arrow in a 14 × 14 box. */
private val Arrow = Path().apply {
    moveTo(7f, 1.5f); lineTo(7f, 12.5f)
    moveTo(2.5f, 8f); lineTo(7f, 12.5f); lineTo(11.5f, 8f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val TitleStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.8).sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val BodyStyle = TextStyle(
    color = Mute,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * The rings on their way from where they were ([from], RINGS × 6 numbers) to one figure and on to
 * a second, both given as places in the list of figures (two per page). The texts leave the page
 * [leaving] and arrive at [arriving].
 */
private class Run(val from: FloatArray, val first: Int, val second: Int, val leaving: Int, val arriving: Int)

/** The run's clock going from [from] to [end] seconds. [late]: how long ago it was due to start. */
private class Play(val from: Float, val end: Float, val late: Float = 0f, val then: (() -> Unit)? = null)

private tailrec fun Context.pagerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.pagerActivity()
    else -> null
}

/**
 * An intro pager whose only artwork is one set of hairline ellipses. Every page arranges them
 * into another figure (a cone, two turning spheres, a lens, an hourglass…), and a change of page
 * sends each ellipse from its place in one figure to its place in the next while the texts slide.
 * Swipe up or tap the round arrow for the next page, swipe down for the one before.
 *
 * @param pages The pages: a title, the figure the page arrives with and the one it settles into.
 * @param body The two lines under every title.
 * @param ink The lines of the figures.
 * @param accent The titles and the round button.
 * @param autoPlay Goes through the first four pages and back to the first by itself, every [LOOP] seconds.
 * @param onPageChange Called with the page's index when a page becomes the one shown.
 */
@Composable
fun SpirographPager(
    pages: List<SpirographPage> = SamplePages,
    body: String = "Discover how we can\nelevate your business",
    ink: Color = Ink,
    accent: Color = Accent,
    autoPlay: Boolean = false,
    onPageChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The gestures and the loop outlive a recomposition: what they read is state.
    val calm by rememberUpdatedState(rememberReducedMotion())
    // Two figures per page: the one it arrives with and the one it settles into.
    val figures by rememberUpdatedState(remember(pages) { pages.flatMap { listOf(it.figure, it.settled ?: it.figure) } })
    val count by rememberUpdatedState(pages.size)
    val changed by rememberUpdatedState(onPageChange)

    // The block opens on the first page as if it had just arrived there.
    var run by remember { mutableStateOf(Run(flat(pages[0].figure), first = 0, second = 1, leaving = 0, arriving = 0)) }
    // Seconds into the run. Read while drawing only; a drag sets it by hand, between 0 and MOVE.
    val r = remember { mutableFloatStateOf(MOVE) }
    var play by remember { mutableStateOf<Play?>(Play(MOVE, runLength(true))) }
    // The page shown: it changes when a change of page is certain.
    var page by remember { mutableIntStateOf(0) }
    // Seconds since the block appeared, for the breathing and the spheres' turn.
    val clock = remember { mutableFloatStateOf(0f) }
    // The drag in progress: the page it started on, and its way (1 to the next page, -1 to the one
    // before, 2 before it has one, 0 when there is none).
    var home by remember { mutableIntStateOf(0) }
    var way by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    /** Where every ring is now, as the flat list a run starts from. */
    fun snapshot(): FloatArray {
        val now = run
        return FloatArray(RINGS * 6) { ringValue(now.from, figures[now.first], figures[now.second], r.floatValue, it / 6, it % 6) }
    }

    /** A change of page is on its way and can no longer be stopped. */
    fun changing() = run.leaving != run.arriving && r.floatValue < MOVE && page == run.arriving

    fun turn(to: Int, hand: Boolean) {
        if (to == page) return
        page = to
        if (hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        changed?.invoke(to)
    }

    /** Starts the change to page [to]: the rings go to its figure, wait, and settle. */
    fun goTo(to: Int, hand: Boolean, late: Float = 0f) {
        if (changing()) return
        run = Run(snapshot(), first = to * 2, second = to * 2 + 1, leaving = page, arriving = to)
        r.floatValue = 0f
        turn(to, hand)
        play = Play(0f, runLength(true), late)
    }

    /** Brings the rings from wherever they are to the settled figure of the page shown. */
    fun resettle() {
        run = Run(snapshot(), first = page * 2 + 1, second = page * 2 + 1, leaving = page, arriving = page)
        r.floatValue = 0f
        play = Play(0f, MOVE)
    }

    fun dragStarted() {
        // A change of page that is on its way is left to finish.
        if (changing()) return
        play = null
        run = Run(snapshot(), first = page * 2 + 1, second = page * 2 + 1, leaving = page, arriving = page)
        r.floatValue = 0f
        home = page
        way = 2
    }

    fun dragged(distance: Float) {
        if (way == 0) return
        val to = if (distance < 0f) 1 else -1
        if (to != way) {
            // Up goes to the next page's figure (after the last comes the first), down to the settled
            // figure of the page before; the first page has none before it.
            way = to
            val target = if (to > 0) (home + 1) % count else home - 1
            run = Run(
                run.from,
                first = if (target < 0) home * 2 + 1 else if (to > 0) target * 2 else target * 2 + 1,
                second = if (target < 0) home * 2 + 1 else target * 2 + 1,
                leaving = home,
                arriving = if (target < 0) home else target,
            )
        }
        val far = if (run.arriving == home) 0f else min(0.999f, abs(distance) / DRAG)
        r.floatValue = far * MOVE
    }

    fun released(velocity: Float) {
        if (way == 0) return
        val flick = if (way > 0) -velocity else velocity
        val far = r.floatValue / MOVE
        way = 0
        if (run.arriving != home && (far > COMMIT || (far > 0.05f && flick > 600f))) {
            // Up arrives at the figure and lets it settle; down arrives at the settled figure.
            turn(run.arriving, hand = true)
            play = Play(r.floatValue, runLength(run.first != run.second))
        } else {
            play = Play(r.floatValue, 0f, then = ::resettle)
        }
    }

    LaunchedEffect(play) {
        val now = play ?: return@LaunchedEffect
        val length = abs(now.end - now.from)
        val origin = withFrameNanos { it }
        while (true) {
            val passed = (withFrameNanos { it } - origin) / 1e9f + now.late
            if (passed >= length) break
            r.floatValue = if (now.end >= now.from) now.from + passed else now.from - passed
        }
        r.floatValue = now.end
        play = null
        now.then?.invoke()
    }

    LaunchedEffect(calm) {
        if (calm) return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) clock.floatValue = (withFrameNanos { it } - origin) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        var origin = System.nanoTime()
        if (touches > 0) {
            // Three seconds of rest, then back to the first page, which the loop starts from the
            // moment its figure has arrived.
            var quiet = 0f
            while (quiet < USER_HOLD) {
                delay(100)
                quiet = if (play != null || way != 0) 0f else quiet + 0.1f
            }
            goTo(0, hand = false)
            origin = System.nanoTime() + (MOVE * 1e9f).toLong()
        }

        /** Waits for the moment [seconds] into the loop, and says how late it woke up. */
        suspend fun until(seconds: Float): Float {
            val due = origin + (seconds * 1e9f).toLong()
            delay(max(0L, (due - System.nanoTime()) / 1_000_000))
            return (System.nanoTime() - due) / 1e9f
        }
        while (true) {
            for (at in NEXT_AT) {
                val late = until(at)
                goTo((page + 1) % count, hand = false, late = late)
            }
            goTo(0, hand = false, late = until(HOME_AT))
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The gallery's bars have light icons; this is a white screen and needs dark ones.
    DisposableEffect(view) {
        val controller = view.context.pagerActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    var distance = 0f
                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        distance = over
                    } ?: return@awaitEachGesture
                    dragStarted()
                    dragged(distance / density)
                    tracker.addPosition(drag.uptimeMillis, drag.position)
                    verticalDrag(drag.id) { change ->
                        distance += change.positionChange().y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        dragged(distance / density)
                    }
                    released(tracker.calculateVelocity().y / density)
                }
            },
    ) {
        val inset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding().value
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2f
        val cy = (height * ART_AT).roundToInt().toFloat()
        val buttonY = height - max(inset, 16f) - BUTTON_UP

        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                val now = run
                val seconds = r.floatValue
                val first = figures[now.first]
                val second = figures[now.second]
                val time = if (calm) 0f else clock.floatValue
                val size = if (calm) 1f else breath(time)
                // A hairline is never thinner than one pixel of the screen.
                val pen = Stroke(max(STROKE, 1f / density))

                fun oval(x: Float, y: Float, w: Float, h: Float, alpha: Float) {
                    if (alpha <= 0.002f) return
                    drawOval(ink, Offset(cx + (x - w / 2f) * size, cy + (y - h / 2f) * size), Size(w * size, h * size), alpha, pen)
                }

                for (k in 0 until RINGS) {
                    if (calm) {
                        // Nothing travels: the figure the rings come from fades into the one they go to.
                        val settling = seconds >= MOVE
                        val u = if (settling) ease((seconds - MOVE - SETTLE_AFTER) / SETTLE) else ease(seconds / MOVE)
                        val to = if (settling) second[k] else first[k]
                        if (settling) {
                            oval(first[k][0], first[k][1], first[k][2], first[k][3], first[k][4] * (1f - u))
                        } else {
                            oval(now.from[k * 6], now.from[k * 6 + 1], now.from[k * 6 + 2], now.from[k * 6 + 3], now.from[k * 6 + 4] * (1f - u))
                        }
                        oval(to[0], to[1], to[2], to[3], to[4] * u)
                    } else {
                        val spin = ringValue(now.from, first, second, seconds, k, 5)
                        oval(
                            ringValue(now.from, first, second, seconds, k, 0),
                            ringValue(now.from, first, second, seconds, k, 1),
                            ringValue(now.from, first, second, seconds, k, 2) * turned(k, spin, time),
                            ringValue(now.from, first, second, seconds, k, 3),
                            ringValue(now.from, first, second, seconds, k, 4),
                        )
                    }
                }
            }
        }

        pages.forEachIndexed { index, item ->
            if (index == run.leaving || index == run.arriving) {
                // The texts of a page that is leaving (-1), staying (0) or arriving (1).
                val role = if (run.leaving == run.arriving) 0 else if (index == run.arriving) 1 else -1
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = MARGIN.dp, end = (BUTTON_RIGHT + BUTTON / 2f + 12f).dp, bottom = (height - buttonY - 16f).dp)
                        .graphicsLayer {
                            val u = textMoved(r.floatValue)
                            val slide = if (calm) 0f else TEXT_SLIDE.dp.toPx()
                            alpha = if (role == 0) 1f else if (role < 0) 1f - u else u
                            translationY = if (role == 0) 0f else if (role < 0) -slide * u else slide * (1f - u)
                        }
                        .then(if (index == page) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    BasicText(item.title, style = TitleStyle.copy(color = accent))
                    Spacer(Modifier.height(TITLE_GAP.dp))
                    BasicText(body, style = BodyStyle)
                }
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val dim by animateFloatAsState(if (pressed) 0.5f else 1f, tween(120), label = "next")
        Box(
            Modifier
                .offset((width - BUTTON_RIGHT - BUTTON / 2f - 10f).dp, (buttonY - BUTTON / 2f - 10f).dp)
                .clickable(interaction, indication = null, role = Role.Button) { goTo((page + 1) % count, hand = true) }
                .semantics { contentDescription = "Next: " + pages[(page + 1) % pages.size].title.replace('\n', ' ') }
                .padding(10.dp)
                .size(BUTTON.dp)
                .border(1.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp).graphicsLayer { alpha = dim }) {
                surface(0.dp) { _, _, _, _ -> drawPath(Arrow, accent, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
}

private fun flat(figure: Figure) = FloatArray(RINGS * 6) { figure[it / 6][it % 6] }
