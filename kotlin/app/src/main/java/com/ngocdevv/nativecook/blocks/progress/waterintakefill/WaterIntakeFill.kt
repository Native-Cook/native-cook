package com.ngocdevv.nativecook.blocks.progress.waterintakefill

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class WaterEntry(val label: String, val time: String)

private const val WIDTH = 320f
private const val HEIGHT = 404f
private const val RADIUS = 34f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 36.dp

// Where the water's surface sits when the tank is empty and when the goal is reached.
private const val Y_EMPTY = 292f
private const val Y_FULL = 112f

// Top of the lavender "Today" panel.
private const val PANEL_Y = 304f

// The percentage rides just under the surface, but never sinks below the chips.
private const val PCT_LEFT = 20f
private const val PCT_MAX_TOP = 190f
private const val PCT_X = 74f

// Vertical drag distance that spans the whole goal, and the step the drag snaps to.
private const val DRAG_SPAN = Y_EMPTY - Y_FULL
private const val DRAG_STEP = 50f

// autoPlay loop (seconds): three drinks, a long look at the result, then the tank drains.
private val POUR_AT = floatArrayOf(0.6f, 2.0f, 3.4f)
private val POUR_SHARE = floatArrayOf(0.385f, 0.615f, 1f)
private const val DRAIN_AT = 7.6f
private const val LOOP = 8.8f

// After a touch the loop waits this long before it drains and starts over.
private const val USER_HOLD = 6f

// The level follows its target like a critically damped spring.
private const val OMEGA = 6f

private const val DROP_LIFE = 0.85f
private const val GRAVITY = 520f
private const val BUBBLE_SPAN = 210f

private class WaterDrop(val x: Float, val vx: Float, val vy: Float, val r: Float)

private val Drops = listOf(
    WaterDrop(0.16f, -22f, 120f, 2.6f), WaterDrop(0.27f, 14f, 165f, 2.1f),
    WaterDrop(0.38f, -8f, 205f, 3.0f), WaterDrop(0.49f, 20f, 150f, 1.8f),
    WaterDrop(0.58f, -16f, 190f, 2.4f), WaterDrop(0.69f, 6f, 135f, 2.0f),
    WaterDrop(0.8f, 24f, 175f, 2.8f), WaterDrop(0.9f, -12f, 110f, 1.7f),
)

private class WaterBubble(val x: Float, val speed: Float, val r: Float, val phase: Float)

private val Bubbles = listOf(
    WaterBubble(0.08f, 22f, 2.2f, 10f), WaterBubble(0.2f, 31f, 1.4f, 120f),
    WaterBubble(0.34f, 18f, 2.8f, 60f), WaterBubble(0.46f, 27f, 1.6f, 170f),
    WaterBubble(0.57f, 20f, 2.0f, 95f), WaterBubble(0.68f, 34f, 1.3f, 30f),
    WaterBubble(0.79f, 24f, 2.5f, 150f), WaterBubble(0.91f, 29f, 1.7f, 75f),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun waterText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val PercentStyle = waterText(54f, 60f, FontWeight.Light, rgba(255, 255, 255, 0.94f), -1.5f)
private val TitleStyle = waterText(24f, 27f, FontWeight.Medium, Color.White, -0.4f)
private val DimStyle = waterText(24f, 27f, FontWeight.Medium, rgba(214, 240, 248, 0.5f), -0.4f)
private val ChipStyle = waterText(11.5f, 16f, FontWeight.Medium, rgba(255, 255, 255, 0.82f))
private val CustomStyle = waterText(12.5f, 17f, FontWeight.SemiBold, Color(0xFF1D3A2C))
private val LogTitleStyle = waterText(23f, 28f, FontWeight.Medium, Color(0xFF1B1A24), -0.3f)
private val LogStyle = waterText(11.5f, 16f, FontWeight.Medium, Color(0xFF2A2834))

/** Y of the resting surface for [level] millilitres. */
private fun baseline(level: Float, goal: Float) = Y_EMPTY - (Y_EMPTY - Y_FULL) * (level / goal).coerceIn(0f, 1f)

/**
 * Y of the surface at [x]. [energy] (0…1) is how hard the water was just disturbed: it raises the
 * waves and rocks the whole surface from side to side. [layer] offsets the phase of each wave.
 */
private fun surfaceAt(x: Float, base: Float, time: Float, energy: Float, layer: Int): Float {
    val amp = 1.7f + 6.5f * energy
    val slope = 0.085f * energy * sin(time * 5.4f)
    val dir = if (layer == 1) -1f else 1f
    return base +
        slope * (x - WIDTH / 2f) +
        amp * sin(x * 0.026f + time * 2.2f * dir + layer * 1.9f) +
        amp * 0.55f * sin(x * 0.047f - time * 3.1f * dir + layer * 0.7f)
}

private fun Path.wave(base: Float, time: Float, energy: Float, layer: Int, lift: Float, closed: Boolean): Path {
    rewind()
    for (i in 0..32) {
        val x = WIDTH * i / 32f
        val y = surfaceAt(x, base, time, energy, layer) + lift
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    if (closed) {
        lineTo(WIDTH, HEIGHT)
        lineTo(0f, HEIGHT)
        close()
    }
    return this
}

/** Millilitres the autoPlay loop wants in the tank at [t] seconds. */
private fun scriptTarget(t: Float, amount: Float): Float {
    if (t >= DRAIN_AT) return 0f
    var share = 0f
    for (i in POUR_AT.indices) if (t >= POUR_AT[i]) share = POUR_SHARE[i]
    return (amount * share / 10f).roundToInt() * 10f
}

private fun grouped(n: Int): String {
    var digits = n.toString()
    var out = ""
    while (digits.length > 3) {
        out = "," + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return digits + out
}

/** Per-frame state of the water. */
private class WaterSim(initial: Float) {
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var target = initial
    var velocity = 0f

    /** 0…1: how hard the water was just disturbed. */
    var energy = 0f
    var splashAt = -10f
    val level = mutableFloatStateOf(initial)

    // Written last in a frame: reading it is what redraws the card.
    val time = mutableFloatStateOf(0f)
}

private class WaterPaths {
    val shadow = SoftRoundRect(rgba(4, 30, 48, 0.5f), sigma = 16f)
    val card = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, WIDTH, HEIGHT), CornerRadius(RADIUS))) }
    val shaftA = Path().apply {
        moveTo(150f, 0f); lineTo(236f, 0f); lineTo(96f, HEIGHT); lineTo(10f, HEIGHT); close()
    }
    val shaftB = Path().apply {
        moveTo(268f, 0f); lineTo(300f, 0f); lineTo(160f, HEIGHT); lineTo(128f, HEIGHT); close()
    }

    // A tumbler ghosted into the background, as in the reference.
    val glass = Path().apply {
        moveTo(96f, 304f); lineTo(96f, 152f); quadraticTo(96f, 130f, 118f, 130f)
        lineTo(202f, 130f); quadraticTo(224f, 130f, 224f, 152f); lineTo(224f, 304f)
    }
    val far = Path()
    val body = Path()
    val near = Path()
    val crest = Path()
    val bubbles = Path()
    val drops = Path()
}

/**
 * Hydration tracker: a tank that fills with rippling water as the day's drinks are logged. The big
 * percentage rides the surface (and sinks under it once there is room), the millilitres in the
 * heading count along, every drink throws a splash and rocks the water, and bubbles keep rising.
 * Tap a chip or drag up and down on the tank to add water.
 *
 * @param progress Controlled share of the daily goal, 0…1.
 * @param autoPlay Pours the day's drinks in one after another up to [amount], holds, drains and starts over.
 * @param amount Millilitres drunk at the resting frame.
 * @param goal The daily goal in millilitres.
 * @param quickAmounts The two quick-add chips, in millilitres.
 * @param customAmount What a tap on the custom pill adds.
 * @param entries The latest log rows, newest first.
 * @param onAdd Called with the amount added and the new total when a chip is tapped or the level is dragged.
 */
@Composable
fun WaterIntakeFill(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Water",
    amount: Int = 650,
    goal: Int = 2000,
    unit: String = "ml",
    quickAmounts: List<Int> = listOf(150, 250),
    customLabel: String = "Custom",
    customAmount: Int = 500,
    logTitle: String = "Today",
    entries: List<WaterEntry> = listOf(WaterEntry("Water (+250)", "12:31"), WaterEntry("Water (+150)", "12:31")),
    onAdd: ((added: Int, total: Int) -> Unit)? = null,
    onCustom: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val auto = autoPlay && !controlled && !reducedMotion
    val rest = if (controlled) (progress ?: 0f).coerceIn(0f, 1f) * goal else amount.toFloat()
    val looping by rememberUpdatedState(auto)
    val view = LocalView.current

    val sim = remember { WaterSim(if (auto) 0f else rest) }
    // Where the level should end up under reduced motion, which integrates nothing per frame.
    var settle by remember { mutableFloatStateOf(rest) }
    // Drinks added by hand, newest first: they push the given entries down the log.
    var added by remember { mutableStateOf(listOf<WaterEntry>()) }

    fun moveTo(ml: Float) {
        sim.target = ml
        settle = ml
    }

    LaunchedEffect(auto, rest) {
        sim.hold = 0f
        sim.clock = 0f
        moveTo(if (auto) 0f else rest)
    }

    LaunchedEffect(reducedMotion, settle) {
        if (!reducedMotion) return@LaunchedEffect
        animate(sim.level.floatValue, settle, animationSpec = tween(250, easing = EaseOutStrong)) { value, _ ->
            sim.level.floatValue = value
        }
    }

    LaunchedEffect(reducedMotion, amount, goal) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            val now = sim.time.floatValue + dt
            if (sim.hold > 0f) {
                sim.hold -= dt
                // Time is up: the loop takes over again by draining what was added.
                if (sim.hold <= 0f && looping) sim.clock = DRAIN_AT
            } else if (looping) {
                var t = sim.clock + dt
                if (t >= LOOP) t -= LOOP
                sim.clock = t
                val next = scriptTarget(t, amount.toFloat())
                if (next > sim.target) sim.splashAt = now
                sim.target = next
            }
            val x = sim.level.floatValue
            sim.velocity += (OMEGA * OMEGA * (sim.target - x) - 2f * OMEGA * sim.velocity) * dt
            sim.level.floatValue = x + sim.velocity * dt
            // Fast changes disturb the water; it calms down by itself.
            sim.energy = max(sim.energy * exp(-dt * 1.5f), min(1f, abs(sim.velocity) / goal * 2.2f))
            sim.time.floatValue = now
        }
    }

    val shownMl by remember { derivedStateOf { (sim.level.floatValue / 10f).roundToInt() * 10 } }
    val percent = min(100, (shownMl * 100 + goal / 2) / goal)

    fun add(ml: Int) {
        if (controlled) {
            onAdd?.invoke(ml, rest.roundToInt() + ml)
            return
        }
        val total = min(goal, sim.target.roundToInt() + ml)
        moveTo(total.toFloat())
        sim.splashAt = sim.time.floatValue
        sim.hold = USER_HOLD
        added = (listOf(WaterEntry("$title (+$ml)", entries.firstOrNull()?.time ?: "")) + added).take(2)
        onAdd?.invoke(ml, total)
    }

    val paths = remember { WaterPaths() }
    val goalF = goal.toFloat()

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // Card, sky, ghosted tumbler and the body of the water: everything behind the percentage.
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val time = sim.time.floatValue
                    val base = baseline(sim.level.floatValue, goalF)
                    drawIntoCanvas { paths.shadow.draw(it.nativeCanvas, x + 14, y + 22, WIDTH - 28, HEIGHT - 14, RADIUS) }
                    translate(x, y) {
                        clipPath(paths.card) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    0f to Color(0xFF082C47),
                                    0.45f to Color(0xFF0F4F6C),
                                    1f to Color(0xFF1B7890),
                                    start = Offset.Zero,
                                    end = Offset(WIDTH * 0.25f, HEIGHT),
                                ),
                                size = Size(WIDTH, HEIGHT),
                            )
                            // Two soft shafts of light falling across the screen.
                            drawPath(
                                paths.shaftA,
                                Brush.verticalGradient(
                                    listOf(rgba(255, 255, 255, 0.07f), rgba(255, 255, 255, 0.015f)), startY = 0f, endY = HEIGHT,
                                ),
                            )
                            drawPath(paths.shaftB, rgba(255, 255, 255, 0.035f))
                            drawPath(paths.glass, rgba(255, 255, 255, 0.13f), style = Stroke(1.5f))
                            drawLine(rgba(255, 255, 255, 0.3f), Offset(112f, 130f), Offset(208f, 130f), 2f, StrokeCap.Round)
                            drawPath(paths.far.wave(base, time, sim.energy, 1, -5f, true), rgba(104, 208, 226, 0.42f))
                            drawPath(
                                paths.body.wave(base, time, sim.energy, 0, 0f, true),
                                Brush.verticalGradient(listOf(Color(0xFF3FB0C9), Color(0xFF1F89AA)), startY = Y_FULL, endY = PANEL_Y),
                            )
                        }
                    }
                }
            }
        }

        // The percentage and everything in front of it, clipped to the card.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(RADIUS.dp))) {
            BasicText(
                text = "$percent%",
                style = PercentStyle,
                maxLines = 1,
                modifier = Modifier
                    .offset(PCT_LEFT.dp, 0.dp)
                    .width(200.dp)
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        val time = sim.time.floatValue
                        val base = baseline(sim.level.floatValue, goalF)
                        val tilt = 0.085f * sim.energy * sin(time * 5.4f)
                        translationY = min(surfaceAt(PCT_X, base, time, sim.energy, 0) + 6f, PCT_MAX_TOP).dp.toPx()
                        rotationZ = Math.toDegrees((atan(tilt) * 0.7f).toDouble()).toFloat()
                    },
            )
            // A tinted front wave, bubbles, the splash, and the log panel.
            Canvas(Modifier.fillMaxSize()) {
                surface(0.dp) { _, _, _, _ ->
                    val time = sim.time.floatValue
                    val base = baseline(sim.level.floatValue, goalF)
                    drawPath(paths.near.wave(base, time, sim.energy, 2, 4f, true), rgba(74, 196, 220, 0.3f))
                    drawPath(paths.crest.wave(base, time, sim.energy, 0, 0f, false), rgba(226, 250, 255, 0.6f), style = Stroke(1.3f))

                    paths.bubbles.rewind()
                    for (b in Bubbles) {
                        val bx = b.x * WIDTH + 4f * sin(time * 1.3f + b.phase)
                        val by = PANEL_Y - (time * b.speed + b.phase) % BUBBLE_SPAN
                        // A bubble exists only under water: it vanishes as it reaches the surface.
                        if (by > surfaceAt(bx, base, time, sim.energy, 0) + b.r + 5f) {
                            paths.bubbles.addOval(Rect(Offset(bx, by), b.r))
                        }
                    }
                    drawPath(paths.bubbles, rgba(255, 255, 255, 0.38f), style = Stroke(1f))

                    val splash = time - sim.splashAt
                    if (splash > 0f && splash < DROP_LIFE) {
                        paths.drops.rewind()
                        for (d in Drops) {
                            val dx = d.x * WIDTH + d.vx * splash
                            val rise = d.vy * splash - 0.5f * GRAVITY * splash * splash
                            if (rise > 0f) {
                                val r = d.r * (1f - 0.5f * (splash / DROP_LIFE))
                                paths.drops.addOval(Rect(Offset(dx, surfaceAt(dx, base, time, sim.energy, 0) - rise), r))
                            }
                        }
                        drawPath(paths.drops, rgba(204, 244, 252, 0.92f))
                    }

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFF1E6EF), Color(0xFFDCCFE8)),
                            start = Offset(0f, PANEL_Y),
                            end = Offset(WIDTH, HEIGHT),
                        ),
                        topLeft = Offset(0f, PANEL_Y),
                        size = Size(WIDTH, HEIGHT - PANEL_Y + 40f),
                        cornerRadius = CornerRadius(30f),
                    )
                }
            }
        }

        Column(
            Modifier
                .offset(22.dp, 20.dp)
                .width((WIDTH - 44f).dp)
                .clearAndSetSemantics {
                    contentDescription = "$title today"
                    stateDescription = "$shownMl of $goal $unit, $percent%"
                    progressBarRangeInfo = ProgressBarRangeInfo(shownMl.toFloat(), 0f..goalF)
                },
        ) {
            BasicText(title, style = TitleStyle, maxLines = 1)
            BasicText(
                buildAnnotatedString {
                    append("today: ")
                    withStyle(SpanStyle(color = Color.White)) { append(grouped(shownMl)) }
                    append(" of ${grouped(goal)}")
                },
                style = DimStyle,
                maxLines = 1,
            )
            BasicText(unit, style = DimStyle, maxLines = 1)
        }

        // Dragging up and down on the tank raises and lowers the level in 50 ml steps.
        Box(
            Modifier
                .offset(0.dp, 104.dp)
                .size(WIDTH.dp, 140.dp)
                .pointerInput(controlled, goal) {
                    if (controlled) return@pointerInput
                    var from = 0f
                    var last = 0f
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            from = sim.target
                            last = sim.target
                            dragged = 0f
                        },
                        onDragEnd = { if (last != from) onAdd?.invoke((last - from).roundToInt(), last.roundToInt()) },
                    ) { change, amountPx ->
                        change.consume()
                        dragged += amountPx / density
                        val raw = from - dragged / DRAG_SPAN * goal
                        val next = ((raw / DRAG_STEP).roundToInt() * DRAG_STEP).coerceIn(0f, goal.toFloat())
                        if (next != last) {
                            last = next
                            moveTo(next)
                            sim.hold = USER_HOLD
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                },
        )

        Row(Modifier.offset(20.dp, 254.dp).height(28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (ml in quickAmounts) {
                WaterPressable(pressedScale = 0.94f, label = "Add $ml $unit", onClick = { add(ml) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(rgba(12, 44, 62, 0.62f), CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(9.dp, 1.4.dp).background(rgba(255, 255, 255, 0.85f), CircleShape))
                            Box(Modifier.size(1.4.dp, 9.dp).background(rgba(255, 255, 255, 0.85f), CircleShape))
                        }
                        Spacer(Modifier.width(7.dp))
                        BasicText("$ml $unit", style = ChipStyle, maxLines = 1)
                    }
                }
            }
        }

        Box(Modifier.offset((WIDTH - 18f - 80f).dp, 249.dp)) {
            WaterPressable(
                pressedScale = PRESS_SCALE,
                label = customLabel,
                onClick = {
                    onCustom?.invoke()
                    add(customAmount)
                },
            ) {
                Box(
                    Modifier.size(80.dp, 38.dp).background(Color(0xFFF1E40C), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(customLabel, style = CustomStyle, maxLines = 1)
                }
            }
        }

        Column(Modifier.offset(24.dp, (PANEL_Y + 13f).dp).width((WIDTH - 48f).dp)) {
            BasicText(logTitle, style = LogTitleStyle, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            for (row in (added + entries).take(2)) {
                Row(
                    Modifier.fillMaxWidth().height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(row.label, style = LogStyle, maxLines = 1)
                    BasicText(row.time, style = LogStyle, maxLines = 1)
                }
            }
        }
    }
}

/** A tappable part of the card: scales down while pressed, with a light haptic. */
@Composable
private fun WaterPressable(pressedScale: Float, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}
