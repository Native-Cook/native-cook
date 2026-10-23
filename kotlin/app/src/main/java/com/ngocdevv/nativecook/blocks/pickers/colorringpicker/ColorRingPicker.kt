package com.ngocdevv.nativecook.blocks.pickers.colorringpicker

import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 340f

// The square the ring is drawn in; the slots sit on a circle of `RADIUS` around its centre.
private const val BOX = 240f
private const val RADIUS = 98f

// A circle's width across and its line, on the ring and in the middle.
private const val SMALL = 26f
private const val SMALL_LINE = 2.5f
private const val LARGE = 100f
private const val LARGE_LINE = 10f

// A finger this close to a slot's centre picks it.
private const val REACH = 22f
private const val BUTTON_W = 146f
private const val BUTTON_H = 36f

// autoPlay (seconds): the first pick, then one every `STEP_TIME`.
private const val LOOP = 8f
private const val FIRST_AT = 1f
private const val STEP_TIME = 1.15f

// Where each pick is, in turns of the ring clockwise from the resting colour.
private val Turns = listOf(0.125f, 0.5f, 0.25f, 0.875f, 0.4375f, 0f)

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val TravelSpring = physical(220f, 21f)

private val Colors = listOf(
    Color(0xFFE5383B), Color(0xFF3DDC97), Color(0xFF8E9196), Color(0xFFF06A8C),
    Color(0xFF1B1E22), Color(0xFF1B8DB8), Color(0xFF1FA463), Color(0xFFF3C1AE),
    Color(0xFF2456E0), Color(0xFFC6F221), Color(0xFFF0743E), Color(0xFFA9A2D6),
    Color(0xFF5FA8A8), Color(0xFFB58A93), Color(0xFFF2E14C), Color(0xFF6A2FBF),
)

private val TitleStyle = TextStyle(
    color = Color.White,
    fontSize = 30.sp,
    fontWeight = FontWeight.Light,
    fontFamily = FontFamily.Serif,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val MessageStyle = TextStyle(
    color = Color(0xFF8A8F98),
    fontSize = 15.sp,
    lineHeight = 21.sp,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val ActionStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Centre of slot [index] in the ring's box, in dp. */
private fun place(index: Int, count: Int): Offset {
    val angle = index.toFloat() / max(count, 1) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
    return Offset(BOX / 2f + cos(angle) * RADIUS, BOX / 2f + sin(angle) * RADIUS)
}

/** The slot under a finger at [x], [y] dp, or -1. */
private fun slotAt(x: Float, y: Float, count: Int): Int {
    var best = -1
    var nearest = REACH
    for (i in 0 until count) {
        val at = place(i, count)
        val distance = hypot(x - at.x, y - at.y)
        if (distance < nearest) {
            nearest = distance
            best = i
        }
    }
    return best
}

/**
 * A colour picker laid out as a ring. Every colour is a small outlined circle on the ring, except
 * the chosen one: its place is empty and it sits in the middle as a large, thick ring. Tapping a
 * circle (or sliding a finger over it) swaps the two: the large ring shrinks back to its place while
 * the tapped one travels to the middle and grows.
 *
 * @param colors The colours, clockwise from 12 o'clock.
 * @param selection The colour chosen at first; [autoPlay] comes back to it at the end of every loop.
 * @param autoPlay Picks one colour after another by itself, in a loop.
 * @param onChange Called with the index of the colour that was picked.
 * @param onFinish Called with the index of the chosen colour when the button is tapped.
 */
@Composable
fun ColorRingPicker(
    colors: List<Color> = Colors,
    selection: Int = 1,
    title: String = "Pick a color",
    message: String = "Color coding a space makes it easier\nto spot when you need it.",
    action: String = "FINISH & SAVE",
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    onFinish: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = colors.size
    val auto = autoPlay && !still && count > 1
    val rest = selection.coerceIn(0, max(count - 1, 0))
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // Each colour's place: 0 in its slot on the ring … 1 in the middle, large.
    val lifts = remember(count, rest) { List(count) { Animatable(if (it == rest) 1f else 0f) } }
    // The swell of a circle that was just tapped.
    val swells = remember(count, rest) { List(count) { Animatable(0f) } }
    val press = remember { Animatable(0f) }
    var selected by remember(count, rest) { mutableIntStateOf(rest) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // A finger is on the ring.
    var holding by remember { mutableStateOf(false) }

    // Up in 80 ms, a 40 ms hold, back in 180 ms: a tap.
    fun pulse(value: Animatable<Float, *>) {
        scope.launch {
            value.animateTo(1f, tween(80, easing = LinearEasing))
            delay(40)
            value.animateTo(0f, tween(180, easing = LinearEasing))
        }
    }

    // Picks colour `index`: it travels to the middle while the one that was there goes back to its slot.
    fun select(index: Int) {
        if (index !in 0 until count || index == selected) return
        val old = selected
        selected = index
        // Under reduced motion nothing travels: the two circles change places at once.
        scope.launch { if (still) lifts[old].snapTo(0f) else lifts[old].animateTo(0f, TravelSpring) }
        scope.launch { if (still) lifts[index].snapTo(1f) else lifts[index].animateTo(1f, TravelSpring) }
        if (!still) pulse(swells[index])
        changed?.invoke(index)
    }

    fun pick(index: Int) {
        if (index < 0 || index == selected) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        select(index)
    }

    // The script a finger would follow: six picks on alternating sides of the ring, the last one back
    // on the colour the loop started with.
    LaunchedEffect(auto, touches, holding, count, rest) {
        if (!auto || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first colour and start over.
            delay((RESUME_AFTER * 1000).toLong())
            select(rest)
        }
        // Every pick is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            Turns.forEachIndexed { n, part ->
                val due = begin + ((turn * LOOP + FIRST_AT + n * STEP_TIME) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                select((rest + (part * count).roundToInt()) % count)
            }
            turn += 1
        }
    }

    Column(modifier.width(WIDTH.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
            BasicText(title, style = TitleStyle, maxLines = 1, modifier = Modifier.wrapContentHeight(unbounded = true))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
            BasicText(message, style = MessageStyle, modifier = Modifier.wrapContentHeight(unbounded = true))
        }
        Spacer(Modifier.height(22.dp))

        Box(
            Modifier
                .size(BOX.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "${selected + 1} of $count"
                }
                .pointerInput(count, rest) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        holding = true
                        touches += 1
                        // A slot is picked once while the finger stays on it.
                        var over = slotAt(down.position.x / density, down.position.y / density, count)
                        pick(over)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val slot = slotAt(change.position.x / density, change.position.y / density, count)
                            if (slot != over) {
                                over = slot
                                pick(slot)
                            }
                            change.consume()
                        }
                        holding = false
                        touches += 1
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val middle = BOX / 2f
                fun ring(i: Int) {
                    val t = lifts[i].value
                    val from = place(i, count)
                    val line = max(1.5f, mix(SMALL_LINE, LARGE_LINE, t))
                    // A tapped circle swells in place before it sets off.
                    val across = max(8f, mix(SMALL, LARGE, t)) * (1f + 0.25f * swells[i].value * (1f - clamp01(t * 3f)))
                    drawCircle(
                        color = colors[i],
                        radius = ((across - line) / 2f).dp.toPx(),
                        center = Offset(mix(from.x, middle, t).dp.toPx(), mix(from.y, middle, t).dp.toPx()),
                        style = Stroke(line.dp.toPx()),
                    )
                }
                // The chosen circle is drawn last, over the one it crosses on its way in.
                for (i in 0 until count) if (i != selected) ring(i)
                if (selected in 0 until count) ring(selected)
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(BUTTON_W.dp, BUTTON_H.dp)
                .graphicsLayer {
                    val dip = 1f - 0.06f * press.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(CircleShape)
                .background(Color(0xFFF2431E))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touches += 1
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (!still) pulse(press)
                    onFinish?.invoke(selected)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = ActionStyle, maxLines = 1)
        }
    }
}
