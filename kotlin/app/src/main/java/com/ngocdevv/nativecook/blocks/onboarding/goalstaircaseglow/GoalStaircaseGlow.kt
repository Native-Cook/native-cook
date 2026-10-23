package com.ngocdevv.nativecook.blocks.onboarding.goalstaircaseglow

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

val SampleSteps = listOf("PLAN", "ACTION", "FOCUS", "PATIENCE", "DISCIPLINE")

private val Background = Color(0xFF010101)

// The frames stop this long after the last thing that moves, until the next touch.
private const val IDLE = 1f

private fun stageText(size: Float, weight: FontWeight, spacing: Float, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * A black stage with two lights: a glowing ball at the lower left and a goal at the upper right.
 * The ball wonders how to get there; a tap calls a staircase of words, each further tap makes the
 * ball hop one step up (a long press runs the rest), every label lights as the ball lands on it,
 * and on the last hop the goal bursts. A tap after that fades everything out and starts again.
 *
 * @param steps The words on the stairs, from the lowest step up.
 * @param autoPlay Plays the whole climb by itself, every [LOOP] seconds.
 * @param onStep Called with the place in [steps] of the label the ball lands on.
 * @param onGoal Called when the ball reaches the goal.
 */
@Composable
fun GoalStaircaseGlow(
    steps: List<String> = SampleSteps,
    goalLabel: String = "GOAL",
    background: Color = Background,
    autoPlay: Boolean = false,
    onStep: ((index: Int) -> Unit)? = null,
    onGoal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The loop and the touches outlive a recomposition: they read what the caller passes through these.
    val still = rememberReducedMotion()
    val stepped by rememberUpdatedState(onStep)
    val reached by rememberUpdatedState(onGoal)
    val count by rememberUpdatedState(steps.size)
    val playing by rememberUpdatedState(autoPlay)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // The scene's clock counts in seconds from here.
    val epoch = remember { System.nanoTime() }
    fun now() = (System.nanoTime() - epoch) / 1e9f

    // The times the scene is drawn from.
    var plot by remember { mutableStateOf(Plot(0f, steps.size)) }
    // The clock as of the last frame. Read while drawing only.
    val clock = remember { mutableFloatStateOf(0f) }
    // Something moves until this time on the clock; after it the frames stop until the next touch.
    var busyUntil by remember { mutableFloatStateOf(INTRO) }
    var awake by remember { mutableStateOf(true) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // What waits for the ball to land.
    val landings = remember { mutableListOf<Job>() }

    // The words are laid out at one pixel to the dp, because the stage is drawn in dp.
    val measurer = rememberTextMeasurer()
    val labels = remember(steps, measurer) {
        val unit = Density(1f)
        steps.map {
            Words(
                measurer.measure(it, stageText(10f, FontWeight.Bold, 0.8f, Color.White.copy(alpha = 0.6f)), maxLines = 1, density = unit),
                measurer.measure(it, stageText(10f, FontWeight.Bold, 0.8f, LabelInk), maxLines = 1, density = unit),
            )
        }
    }
    val goal = remember(goalLabel, measurer) {
        val unit = Density(1f)
        Words(
            measurer.measure(goalLabel, stageText(15f, FontWeight.SemiBold, 1.5f, Color.White), maxLines = 1, density = unit),
            measurer.measure(goalLabel, stageText(15f, FontWeight.SemiBold, 1.5f, Gold), maxLines = 1, density = unit),
        )
    }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Keeps the frames coming for the next [seconds]. */
    fun wake(seconds: Float) {
        busyUntil = max(busyUntil, now() + seconds)
        awake = true
    }

    /**
     * Everything fades to black, and after a dark moment the scene appears again. Returns when,
     * on the clock: that is also the answer while a fade or the dark is already under way.
     */
    fun reset(): Float {
        val t = now()
        val current = plot.at(t)
        if (current.fade < NONE) return current.fade + FADE + DARK
        if (t < current.born) return current.born
        landings.forEach { it.cancel() }
        landings.clear()
        plot = Plot(current.born, current.stairs, current.hops, fade = t)
        wake(FADE + DARK + INTRO)
        return t + FADE + DARK
    }

    /** The next thing: the stairs, then a hop ([all]: every hop that is left), then starting over. */
    fun act(all: Boolean, byHand: Boolean) {
        val t = now()
        var current = plot.at(t)
        if (current.fade < NONE || t < current.born) return
        val last = count
        if (current.stairs >= NONE) {
            // The first touch calls the stairs; a long press goes on to climb them as they arrive.
            current = Plot(current.born, t, current.hops, current.fade)
            if (!all) {
                plot = current
                wake(last * STAIR_GAP + STAIR_IN)
                return
            }
        }
        val hops = current.hops.copyOf()
        var next = hops.indexOfFirst { it >= NONE }
        if (next < 0) {
            // The climb is over: a touch after the burst starts again.
            if (t > hops[last] + GOAL_HOP + BURST) reset()
            return
        }
        do {
            // A hop waits for the one before it to land, and for its label to be there.
            val free = if (next == 0) 0f else hops[next - 1] + HOP
            val ready = if (next < last) current.stairs + next * STAIR_GAP + STAIR_IN else 0f
            val from = max(t, max(free, ready))
            hops[next] = from
            val landing = from + (if (next < last) HOP else GOAL_HOP) - t
            val index = next
            landings += scope.launch {
                delay((max(0f, landing) * 1000).toLong())
                // The loop that plays by itself is silent.
                if (index < last) {
                    if (byHand) tap()
                    stepped?.invoke(index)
                } else {
                    if (byHand) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                    reached?.invoke()
                }
            }
            next++
        } while (all && next <= last)
        plot = Plot(current.born, current.stairs, hops, current.fade)
        wake(hops[next - 1] + GOAL_HOP + BURST - t)
    }

    fun press(all: Boolean) {
        // A touch takes over from the autoPlay loop, which starts again from the dark a while later.
        touches++
        tap()
        act(all, byHand = true)
    }

    // Another number of steps is another staircase: the scene starts over.
    LaunchedEffect(steps.size) {
        if (plot.hops.size != steps.size + 1) {
            landings.forEach { it.cancel() }
            landings.clear()
            plot = Plot(now(), steps.size)
            wake(INTRO)
        }
    }

    // The frames: while something moves, and for good while autoPlay is on.
    LaunchedEffect(awake, autoPlay) {
        if (!awake && !autoPlay) return@LaunchedEffect
        while (playing || now() < busyUntil + IDLE) {
            withFrameNanos { }
            clock.floatValue = now()
        }
        awake = false
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            // The loop starts over from the dark, when the scene appears again.
            delay((USER_HOLD * 1000).toLong())
            val born = reset()
            delay((max(0f, born - now()) * 1000).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(STAIRS_AT)
            act(all = false, byHand = false)
            until(CLIMB_AT)
            act(all = true, byHand = false)
            until(RESET_AT)
            reset()
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) { detectTapGestures(onTap = { press(false) }, onLongPress = { press(true) }) }
            .clearAndSetSemantics {
                contentDescription = "$goalLabel: ${steps.joinToString()}"
                role = Role.Button
                onClick("Takes the next step") {
                    press(false)
                    true
                }
            },
    ) {
        surface(0.dp) { _, _, w, h -> drawStage(w, h, clock.floatValue, plot, labels, goal, still) }
    }
}
