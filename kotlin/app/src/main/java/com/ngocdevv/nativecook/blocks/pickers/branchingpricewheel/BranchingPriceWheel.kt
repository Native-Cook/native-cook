package com.ngocdevv.nativecook.blocks.pickers.branchingpricewheel

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 320f
private const val HEIGHT = 500f
private const val CORNER = 36f

// The row in the middle, and the distance between rows and between units.
private const val CENTER_Y = 250f
private const val ROW_PITCH = 50f
private const val UNIT_PITCH = 76f

// The left edge of the column's amounts, and of the large one in the shared cell.
private const val COLUMN_X = 70f
private const val CELL_X = 38f
private const val CELL_WIDTH = 140f
private const val CELL_HEIGHT = 40f
private const val SMALL_SIZE = 15f
private const val LARGE_SIZE = 28f
private const val CLOSE_X = 286f
private const val CLOSE_Y = 34f
private const val SAVE_X = 252f
private const val SAVE_Y = 446f
private const val SAVE_WIDTH = 84f
private const val SAVE_HEIGHT = 36f

// A drag picks its wheel after this many dp, and coasts for this long on the finger's speed.
private const val SLOP = 8f
private const val COAST = 0.18f
private const val DIMMED = 0.3f
private const val DIM_TIME = 0.2f
private const val FOLD_TIME = 0.12f

// The branch shoots out this long after the column is let go: 250 ms a number, 40 ms apart.
private const val BRANCH_AFTER = 0.3f
private const val BRANCH_RISE = 0.25f
private const val BRANCH_STAGGER = 0.04f
private const val BRANCH_TIME = BRANCH_RISE + BRANCH_STAGGER * 3f
private const val BRANCH_SLIDE = 20f

// The column is lit again this long after the units are let go.
private const val SNAP_TIME = 0.45f
private const val SAVED_TIME = 1f

private const val LOOP = 8.5f

// After a touch the loop waits this long, returns to the first amount, and starts over once it has settled.
private const val RESUME_AFTER = 2f
private const val SETTLE = 1f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

// Response 0.4 s, damping ratio 0.8.
private val SnapSpring = physical(247f, 25f)
private val PopSpring = physical(400f, 18f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Navy = Color(0xFF1B2540)
private val Green = Color(0xFF10D48A)
private val Pale = Color(0xFFCBD5E0)

private fun type(size: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeavyStyle = type(LARGE_SIZE, FontWeight.ExtraBold, Green)
private val ThinStyle = type(LARGE_SIZE, FontWeight.ExtraLight, Pale)
private val SaveStyle = type(12f, FontWeight.Bold, Color.White)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Past either end a wheel follows the finger at a third of its speed. */
private fun rubber(position: Float, last: Float) = when {
    position < 0f -> position / 3f
    position > last -> last + (position - last) / 3f
    else -> position
}

/**
 * How far out the branch's number in [place] (1 next to the selection) is, 0…1, when the branch as
 * a whole is at [branch]: each number sets off 40 ms after the one before it and takes 250 ms.
 */
private fun outAt(branch: Float, place: Int): Float {
    val t = clamp01((branch * BRANCH_TIME - BRANCH_STAGGER * (place - 1)) / BRANCH_RISE)
    return 1f - (1f - t).pow(3)
}

private enum class Axis { Column, Branch }

private sealed interface Step {
    data class ToUnit(val unit: Int) : Step
    data class ToTen(val ten: Int) : Step
}

/** What moves. */
private class WheelMotion(ten: Int, unit: Int, var kept: Int) {
    /** The column's position: the index of the ten in the middle, fractional while it turns. */
    val tens = Animatable(ten.toFloat())

    /** The branch's position: the unit in the shared cell, 0 for the ten itself. */
    val units = Animatable(unit.toFloat())

    /** 0 the branch folded into the selected amount … 1 out. */
    val branch = Animatable(1f)

    /** The opacity of the rest of the column: it dims while the units move. */
    val dim = Animatable(1f)

    /** Save's scale, and 0 its label … 1 the large check. */
    val pop = Animatable(1f)
    val saved = Animatable(0f)

    var ten by mutableIntStateOf(ten)
    var unit by mutableIntStateOf(unit)

    /** The drag's wheel once it is known. */
    var axis: Axis? = null

    /** What is still to come for the column's dimming, the branch and the check: a new one replaces it. */
    var dimming: Job? = null
    var branching: Job? = null
    var saving: Job? = null
}

/**
 * A price picker made of two wheels at right angles that share one cell. The tens run down a
 * column; the amount in the middle is large and green, and from it the units branch out to the
 * right in a hairline weight. Dragging up or down turns the column (the branch folds away while
 * it moves and shoots out again from the new ten); dragging sideways slides the units through the
 * shared cell, each one thickening and turning green as it arrives. Tapping an amount goes to it.
 *
 * @param range The lowest and the highest amount. The column lists every ten between them.
 * @param initial The amount selected at first.
 * @param currency Written after each amount.
 * @param autoPlay Slides the units and turns the column by itself, in a loop.
 * @param onChange Called with the amount after every step.
 * @param onSave Called with the amount when Save is tapped.
 */
@Composable
fun BranchingPriceWheel(
    range: IntRange = 30..100,
    initial: Int = 60,
    currency: String = "$",
    saveLabel: String = "Save",
    autoPlay: Boolean = false,
    onChange: ((amount: Int) -> Unit)? = null,
    onSave: ((amount: Int) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The column lists every ten from the lowest amount; the first amount is a place in it and a unit.
    val tens = remember(range) { (range.first..max(range.first, range.last) step 10).toList() }
    val firstAmount = initial.coerceIn(range.first, range.last)
    val firstTen = min(tens.lastIndex, (firstAmount - range.first) / 10)
    val firstUnit = firstAmount - tens[firstTen]
    val motion = remember(range, firstAmount) { WheelMotion(firstTen, firstUnit, firstAmount) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // The units a ten can take before the range ends.
    fun maxUnit(ten: Int) = (range.last - tens[ten.coerceIn(0, tens.lastIndex)]).coerceIn(0, 9)

    fun touch() {
        touches += 1
    }

    fun report() {
        val amount = tens[motion.ten] + motion.unit
        if (amount != motion.kept && !still) {
            // Save dips and springs back when there is something new to keep.
            scope.launch {
                motion.pop.snapTo(0.9f)
                motion.pop.animateTo(1f, PopSpring)
            }
        }
        changed?.invoke(amount)
    }

    fun passUnit(next: Int, tick: Boolean) {
        val to = next.coerceIn(0, maxUnit(motion.ten))
        if (to == motion.unit) return
        motion.unit = to
        // The units' tick is a shade softer than the column's.
        if (tick) view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        report()
    }

    fun passTen(next: Int, tick: Boolean) {
        val to = next.coerceIn(0, tens.lastIndex)
        if (to == motion.ten && motion.unit == 0) return
        motion.ten = to
        motion.unit = 0
        if (tick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        report()
    }

    // Under reduced motion nothing travels: every change is a jump.
    suspend fun fade(value: Animatable<Float, AnimationVector1D>, target: Float, seconds: Float) {
        if (still) value.snapTo(target) else value.animateTo(target, tween((seconds * 1000).toInt(), easing = EaseInOut))
    }

    fun dimColumn(dimmed: Boolean, thenLight: Boolean = false) {
        motion.dimming?.cancel()
        motion.dimming = scope.launch {
            fade(motion.dim, if (dimmed) DIMMED else 1f, DIM_TIME)
            if (thenLight) {
                delay(((SNAP_TIME - DIM_TIME) * 1000).toLong())
                fade(motion.dim, 1f, DIM_TIME)
            }
        }
    }

    // Folds the branch into the selected amount; the units go back to the ten itself.
    fun fold(thenOut: Boolean = false) {
        motion.branching?.cancel()
        motion.branching = scope.launch {
            fade(motion.branch, 0f, FOLD_TIME)
            if (thenOut) {
                if (still) {
                    motion.branch.animateTo(1f, tween((FOLD_TIME * 1000).toInt(), easing = LinearEasing))
                } else {
                    delay(((BRANCH_AFTER - FOLD_TIME) * 1000).toLong())
                    motion.branch.animateTo(1f, tween((BRANCH_TIME * 1000).toInt(), easing = LinearEasing))
                }
            }
        }
        if (motion.units.value != 0f) {
            scope.launch { if (still) motion.units.snapTo(0f) else motion.units.animateTo(0f, SnapSpring) }
        }
        dimColumn(false)
    }

    // Slides the branch until `unit` is in the shared cell; the column is lit again once it has snapped.
    fun slide(unit: Int, speed: Float = 0f, tick: Boolean) {
        passUnit(unit, tick)
        val target = motion.unit.toFloat()
        scope.launch { if (still) motion.units.snapTo(target) else motion.units.animateTo(target, SnapSpring, initialVelocity = speed) }
        dimColumn(true, thenLight = true)
    }

    // Turns the column until row `ten` is in the middle; the branch shoots out once it has snapped.
    fun turn(ten: Int, speed: Float = 0f, tick: Boolean) {
        fold(thenOut = true)
        passTen(ten, tick)
        val target = motion.ten.toFloat()
        scope.launch { if (still) motion.tens.snapTo(target) else motion.tens.animateTo(target, SnapSpring, initialVelocity = speed) }
    }

    // Keeps the amount: the label gives way to a large check for a second.
    fun save() {
        motion.kept = tens[motion.ten] + motion.unit
        motion.saving?.cancel()
        motion.saving = scope.launch {
            if (still) motion.saved.snapTo(1f) else launch { motion.saved.animateTo(1f, PopSpring) }
            delay((SAVED_TIME * 1000).toLong())
            fade(motion.saved, 0f, DIM_TIME)
        }
        onSave?.invoke(motion.kept)
    }

    fun tap(x: Float, y: Float) {
        if (abs(x - SAVE_X) < SAVE_WIDTH / 2f + 6f && abs(y - SAVE_Y) < SAVE_HEIGHT / 2f + 6f) return
        if (abs(x - CLOSE_X) < 22f && abs(y - CLOSE_Y) < 22f) return
        if (abs(y - CENTER_Y) < ROW_PITCH / 2f) {
            touch()
            slide(motion.unit + ((x - CELL_X - 30f) / UNIT_PITCH).roundToInt(), tick = true)
        } else {
            val row = motion.ten + ((y - CENTER_Y) / ROW_PITCH).roundToInt()
            if (row !in tens.indices) return
            touch()
            turn(row, tick = true)
        }
    }

    // The script a finger would follow: two units along and back, two tens down, one unit along
    // and back, and the column up again.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first amount, and start over once it has settled.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.axis != null) return@LaunchedEffect
            if (motion.ten != firstTen) turn(firstTen, tick = false)
            slide(firstUnit, tick = false)
            motion.kept = firstAmount
            delay((SETTLE * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var round = 0
        val far = min(tens.lastIndex, firstTen + 2)
        val script = listOf(
            1.0f to Step.ToUnit(1), 1.4f to Step.ToUnit(2), 2.6f to Step.ToUnit(0),
            3.3f to Step.ToTen(far), 4.9f to Step.ToUnit(1), 6.0f to Step.ToUnit(0), 6.6f to Step.ToTen(firstTen),
        )
        while (true) {
            for ((at, step) in script) {
                val due = begin + ((round * LOOP + at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                when (step) {
                    is Step.ToUnit -> slide(step.unit, tick = false)
                    is Step.ToTen -> turn(step.ten, tick = false)
                }
            }
            round += 1
        }
    }

    val shadow = remember { SoftRoundRect(Color(0xFF8FA3B8).copy(alpha = 0.28f), sigma = 28f) }
    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 18f, WIDTH, HEIGHT, CORNER) }
                }
            }
            .clip(RoundedCornerShape(CORNER.dp))
            .background(Color.White)
            // One drag over the card. Its first few dp decide the wheel: up and down turns the column,
            // sideways slides the branch. A touch that does not move is a tap on the amount under it.
            .pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var grabbed = 0f
                    var handled = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            handled = change.isConsumed
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = (change.position.x - down.position.x) / density
                        val dy = (change.position.y - down.position.y) / density
                        if (motion.axis == null) {
                            if (hypot(dx, dy) < SLOP) continue
                            touch()
                            if (abs(dx) > abs(dy)) {
                                motion.axis = Axis.Branch
                                grabbed = motion.units.value
                                dimColumn(true)
                            } else {
                                motion.axis = Axis.Column
                                grabbed = motion.tens.value
                                fold()
                            }
                        }
                        change.consume()
                        if (motion.axis == Axis.Branch) {
                            val at = rubber(grabbed - dx / UNIT_PITCH, maxUnit(motion.ten).toFloat())
                            scope.launch { motion.units.snapTo(at) }
                            passUnit(at.roundToInt(), tick = true)
                        } else {
                            val at = rubber(grabbed - dy / ROW_PITCH, tens.lastIndex.toFloat())
                            scope.launch { motion.tens.snapTo(at) }
                            passTen(at.roundToInt(), tick = true)
                        }
                    }
                    // The wheel coasts a little way on the finger's speed before it snaps.
                    val fling = tracker.calculateVelocity()
                    when (motion.axis) {
                        Axis.Branch -> {
                            val speed = -fling.x / density / UNIT_PITCH
                            slide((motion.units.value + speed * COAST).roundToInt().coerceIn(0, maxUnit(motion.ten)), speed, tick = true)
                        }
                        Axis.Column -> {
                            val speed = -fling.y / density / ROW_PITCH
                            turn((motion.tens.value + speed * COAST).roundToInt().coerceIn(0, tens.lastIndex), speed, tick = true)
                        }
                        null -> if (!handled) tap(down.position.x / density, down.position.y / density)
                    }
                    if (motion.axis != null) {
                        motion.axis = null
                        touch()
                    }
                }
            },
    ) {
        tens.forEachIndexed { index, amount ->
            // The selected ten is the branch's first number: the units push it out to the left.
            fun pushed() = if (index == motion.ten) motion.units.value else 0f
            fun near() = clamp01(1f - abs(index - motion.tens.value))
            Cell(
                label = "$amount$currency",
                heavy = { clamp01(1f - pushed()) },
                color = { lerp(Navy, Green, near()) },
                modifier = Modifier.graphicsLayer {
                    val away = index - motion.tens.value
                    val grow = mix(SMALL_SIZE / LARGE_SIZE, 1f, near())
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = (mix(COLUMN_X, CELL_X, near()) - pushed() * UNIT_PITCH).dp.toPx()
                    translationY = (CENTER_Y + away * ROW_PITCH - CELL_HEIGHT / 2f).dp.toPx()
                    scaleX = grow
                    scaleY = grow
                    alpha = clamp01(1f - pushed()) * clamp01((4.7f - abs(away)) / 0.7f) * (if (index == motion.ten) 1f else motion.dim.value)
                },
            )
        }

        for (place in 1..9) {
            if (place <= maxUnit(motion.ten)) {
                Cell(
                    label = "${tens[motion.ten] + place}$currency",
                    heavy = { clamp01(1f - abs(place - motion.units.value)) },
                    color = { Green },
                    modifier = Modifier.graphicsLayer {
                        val away = place - motion.units.value
                        // Numbers right of the selection wait for the branch, one after the other.
                        val ahead = place - motion.unit
                        val out = if (ahead >= 1) outAt(motion.branch.value, ahead) else 1f
                        val x = CELL_X + away * UNIT_PITCH - BRANCH_SLIDE * (1f - out)
                        translationX = x.dp.toPx()
                        // The branch stays on the selected row while the column turns.
                        translationY = (CENTER_Y + (motion.ten - motion.tens.value) * ROW_PITCH - CELL_HEIGHT / 2f).dp.toPx()
                        alpha = out * (if (away < 0f) clamp01(1f + away) else 1f) * clamp01((WIDTH - 20f - x) / 60f)
                    },
                )
            }
        }

        Canvas(
            Modifier
                .offset((CLOSE_X - 22f).dp, (CLOSE_Y - 22f).dp)
                .size(44.dp)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    onClose?.invoke()
                }
                .semantics { contentDescription = "Close" },
        ) {
            val arm = 5.dp.toPx()
            val line = 1.4.dp.toPx()
            drawLine(Navy, center + Offset(-arm, -arm), center + Offset(arm, arm), line, StrokeCap.Round)
            drawLine(Navy, center + Offset(arm, -arm), center + Offset(-arm, arm), line, StrokeCap.Round)
        }

        Box(
            Modifier
                .offset((SAVE_X - SAVE_WIDTH / 2f).dp, (SAVE_Y - SAVE_HEIGHT / 2f).dp)
                .size(SAVE_WIDTH.dp, SAVE_HEIGHT.dp)
                .graphicsLayer {
                    scaleX = motion.pop.value
                    scaleY = motion.pop.value
                }
                .clip(CircleShape)
                .background(Green)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    save()
                }
                .semantics { contentDescription = saveLabel },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.graphicsLayer { alpha = 1f - clamp01(motion.saved.value) },
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(saveLabel, style = SaveStyle, maxLines = 1)
                Check(9f, 7f, 1.8f)
            }
            Check(
                15f, 11f, 2.4f,
                Modifier.graphicsLayer {
                    val grow = mix(0.5f, 1f, motion.saved.value)
                    alpha = clamp01(motion.saved.value)
                    scaleX = grow
                    scaleY = grow
                },
            )
        }
    }
}

/**
 * One amount, set large. Its heavy face (in [color]) lies on its hairline face in pale grey, and
 * [heavy] cross-fades them: 1 is all heavy. The box is anchored at its left edge.
 */
@Composable
private fun Cell(label: String, heavy: () -> Float, color: () -> Color, modifier: Modifier) {
    Box(modifier.size(CELL_WIDTH.dp, CELL_HEIGHT.dp)) {
        BasicText(
            label,
            style = ThinStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.CenterStart, unbounded = true).graphicsLayer { alpha = 1f - heavy() },
        )
        BasicText(
            label,
            style = HeavyStyle,
            color = { color() },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.CenterStart, unbounded = true).graphicsLayer { alpha = heavy() },
        )
    }
}

/** A white check mark of [width] × [height] dp. */
@Composable
private fun Check(width: Float, height: Float, line: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width.dp, height.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.55f)
            lineTo(size.width * 0.36f, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(path, Color.White, style = Stroke(line.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
