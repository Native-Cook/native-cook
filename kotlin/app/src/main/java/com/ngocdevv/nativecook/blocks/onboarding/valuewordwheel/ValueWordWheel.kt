package com.ngocdevv.nativecook.blocks.onboarding.valuewordwheel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WheelIcon { Card, Bars, Shield, Bolt, Globe }

data class WheelItem(val word: String, val icon: WheelIcon)

val SampleWheelItems = listOf(
    WheelItem("Spend", WheelIcon.Card),
    WheelItem("Earn", WheelIcon.Bars),
    WheelItem("Save", WheelIcon.Shield),
    WheelItem("Pay", WheelIcon.Bolt),
    WheelItem("Invest", WheelIcon.Globe),
)

private val Ink = Color(0xFF1C1C1E)
private val SampleGlow = listOf(Color(0xFFCDEEFF), Color(0xFF09BFFF), Color(0xFF0093FB), Color(0xFF166AFF))
private val ButtonInk = Color(0xFF050505)

/** autoPlay: one word every STEP seconds, so the five words come round once a LOOP. */
internal const val STEP = 1.6f
internal const val LOOP = 8f

/** After a touch the wheel waits this long, then turns back to the first word and starts over. */
private const val USER_HOLD = 3f

// The roll: it overshoots the row by about two points and has settled in 0.45 s.
private const val SPRING_MASS = 1f
private const val SPRING_STIFFNESS = 180f
private const val SPRING_DAMPING = 20f

// A hundredth of a point: the spring stops there.
private const val AT_REST = 0.0001f
private val RollSpring = spring(
    dampingRatio = SPRING_DAMPING / (2f * sqrt(SPRING_STIFFNESS * SPRING_MASS)),
    stiffness = SPRING_STIFFNESS / SPRING_MASS,
    visibilityThreshold = AT_REST,
)

// A flick carries the wheel on by this many seconds of its speed.
private const val COAST = 0.18f

// The design is 393 dp wide; the light's widths scale with the screen.
private const val DESIGN_WIDTH = 393f
private const val LEFT = 56f
private const val ROW = 72f
private const val ICON = 28f
private const val ICON_GAP = 12f
private const val WORD = 34f
private const val WORD_LINE = 42f

// Two and a half rows show on each side of the focus line.
private const val WHEEL = ROW * 5f

// A word one row from the focus line, two rows from it, and how soft each row away makes it.
private const val DIM_NEAR = 0.12f
private const val DIM_FAR = 0.05f
private const val SOFTEN = 1.6f

// The icon of the word that comes into focus grows from this size, past its own, and settles.
private const val POP_FROM = 0.6f
private const val POP_BACK = 3f

// The light is one large ellipse whose centre lies below the screen, so its upper edge is a dome
// that stands higher in the middle than at the sides.
private const val DOME_RX = 553f
private const val DOME_RY = 576f
private const val DOME_BELOW = 119f

// Where the colours sit from the centre out to the edge: deepest, deepest, blue, cyan, pale, background.
private val DomeStops = floatArrayOf(0f, 0.4f, 0.644f, 0.852f, 0.94f, 1f)

// The dome rises and falls, and a brighter patch in it drifts from side to side.
private const val DOME_RISE = 10f
private const val DOME_PERIOD = 4f
private const val SPOT_RX = 260f
private const val SPOT_RY = 130f
private const val SPOT_ABOVE = 332f
private const val SPOT_DRIFT = 20f
private const val SPOT_PERIOD = 8f
private const val SPOT_ALPHA = 0.55f

// cos², so the patch has no rim.
private val SpotAt = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
private val SpotFalloff = floatArrayOf(1f, 0.905f, 0.655f, 0.345f, 0.095f, 0f)

// The dome's top, and the focus line above it.
private const val GLOW_TOP = DOME_RY - DOME_BELOW
private const val FOCUS_ABOVE = 138f

private const val TILE = 40f
private const val BUTTON = 50f
private const val BUTTON_SIDE = 40f
private const val PRESS_SCALE = 0.97f
private const val PRESS_MS = 130

private const val TURN = (Math.PI * 2).toFloat()

private class Part(val path: Path, val color: Color, val stroke: Float = 0f)

private fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
    Path().apply { addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(r))) }

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
    Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

// The five icons, each in a 28 × 28 box.
private val Glyphs = mapOf(
    WheelIcon.Card to listOf(
        Part(box(3f, 6f, 22f, 16f, 4f), Color(0xFF7B5CFF)),
        Part(Path().apply { addRect(Rect(3f, 10.5f, 25f, 14f)) }, Color(0xFFCFC4FF)),
        Part(box(6.5f, 16.5f, 6f, 2.5f, 1.25f), Color(0xFFCFC4FF)),
    ),
    WheelIcon.Bars to listOf(
        Part(box(3f, 14f, 6f, 11f, 2f), Color(0xFFFFB286)),
        Part(box(11f, 9f, 6f, 16f, 2f), Color(0xFFFF9E62)),
        Part(box(19f, 4f, 6f, 21f, 2f), Color(0xFFFF8A3D)),
    ),
    WheelIcon.Shield to listOf(
        Part(
            Path().apply {
                moveTo(14f, 2.5f); lineTo(24f, 6f); lineTo(24f, 13.2f)
                cubicTo(24f, 19.4f, 19.9f, 23.7f, 14f, 25.8f)
                cubicTo(8.1f, 23.7f, 4f, 19.4f, 4f, 13.2f)
                lineTo(4f, 6f); close()
            },
            Color(0xFF35C8F5),
        ),
        Part(
            Path().apply {
                moveTo(14f, 2.5f); lineTo(4f, 6f); lineTo(4f, 13.2f)
                cubicTo(4f, 19.4f, 8.1f, 23.7f, 14f, 25.8f)
                close()
            },
            Color(0xFF7FDDFA),
        ),
    ),
    WheelIcon.Bolt to listOf(
        Part(
            Path().apply {
                moveTo(16.5f, 2f); lineTo(5.5f, 15.5f); lineTo(12.5f, 15.5f); lineTo(11f, 26f)
                lineTo(22.5f, 12f); lineTo(15.3f, 12f); close()
            },
            Color(0xFFFF4D4D),
        ),
    ),
    WheelIcon.Globe to listOf(
        Part(oval(14f, 14f, 11.5f, 11.5f), Color(0xFF6C7BFF)),
        Part(oval(14f, 14f, 4.8f, 11.5f), Color(0xFFDDE1FF), stroke = 1.6f),
        Part(Path().apply { moveTo(2.5f, 14f); lineTo(25.5f, 14f) }, Color(0xFFDDE1FF), stroke = 1.6f),
    ),
)

// The app's mark on its tile: two joined dots and two loose ones, in a 40 × 40 box.
private val MarkDots = listOf(Triple(15f, 15f, 3f), Triple(15f, 25f, 3f), Triple(25f, 15f, 3f), Triple(25f, 25f, 2f))

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun wheelText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadlineStyle = wheelText(26f, 34f, FontWeight.Medium, Color.White, -0.4f)
private val CaptionStyle = wheelText(12.5f, 17f, FontWeight.Normal, Color.White.copy(alpha = 0.6f))
private val PrimaryStyle = wheelText(15f, 20f, FontWeight.SemiBold, ButtonInk)
private val SecondaryStyle = wheelText(14f, 19f, FontWeight.SemiBold, Color.White)

/** How far row [index] is from the focus line, in rows; the wheel repeats every [count] rows. */
private fun offsetOf(index: Int, position: Float, count: Int): Float {
    val d = index - position
    return d - count * (d / count).roundToInt()
}

private fun dimOf(d: Float): Float {
    val a = abs(d)
    return when {
        a <= 1f -> 1f - (1f - DIM_NEAR) * a
        a <= 2f -> DIM_NEAR - (DIM_NEAR - DIM_FAR) * (a - 1f)
        else -> max(0f, DIM_FAR * (3f - a))
    }
}

/** The icon's size: it pops as its word arrives from below, and shrinks as the word leaves upwards. */
private fun popOf(d: Float): Float {
    val u = min(1f, abs(d))
    if (d < 0f) return 1f - (1f - POP_FROM) * u
    val back = 1f + (POP_BACK + 1f) * (-u).pow(3) + POP_BACK * u * u
    return POP_FROM + (1f - POP_FROM) * back
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A sign-up entry screen. In the white upper half a wheel of single words turns one row at a
 * time: the word on the focus line is sharp and dark with its icon, the rows around it are faint
 * and soft, and the list never ends. Below, a blue light breathes behind the headline and the two
 * buttons. The wheel can be dragged, and a faint word can be tapped into focus.
 *
 * @param items The words of the wheel, in the order they come into focus; the first one starts there.
 * @param background Behind the wheel.
 * @param ink The words.
 * @param glow The light at the bottom, from its pale edge down to the deepest blue.
 * @param autoPlay Turns the wheel by one word every [STEP] seconds; a touch holds it for a while.
 * @param onChange Called with the place in [items] of the word that comes into focus.
 */
@Composable
fun ValueWordWheel(
    items: List<WheelItem> = SampleWheelItems,
    headline: String = "Your money,\nupgraded",
    caption: String = "Save, earn and invest\nwith one account for everything.",
    primaryLabel: String = "Get started",
    secondaryLabel: String = "I already have an account",
    background: Color = Color.White,
    ink: Color = Ink,
    glow: List<Color> = SampleGlow,
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val still by rememberUpdatedState(rememberReducedMotion())
    val words by rememberUpdatedState(items)
    val changed by rememberUpdatedState(onChange)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The screen is white under the status bar: dark icons while it is shown.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    // The wheel's place in rows: row n is on the focus line when it is n. It only ever grows or
    // shrinks; the words repeat every `items.size` rows. Read while drawing only.
    val position = remember { Animatable(0f, visibilityThreshold = AT_REST) }
    // The row the wheel is on, or on its way to.
    var target by remember { mutableIntStateOf(0) }
    // A finger is on the wheel.
    var holding by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Seconds, round and round, for the light. Read while drawing only.
    val clock = remember { mutableFloatStateOf(0f) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Turns the wheel to row [to], carrying on at [speed] rows a second, or at the speed it has. */
    fun roll(to: Int, speed: Float? = null) {
        target = to
        changed?.invoke(Math.floorMod(to, words.size))
        scope.launch {
            // Reduced motion: the word changes in place.
            if (still) position.snapTo(to.toFloat()) else position.animateTo(to.toFloat(), RollSpring, speed ?: position.velocity)
        }
    }

    /** A faint word that was tapped comes into focus. */
    fun pick(rowsAway: Int) {
        if (rowsAway != 0) tick()
        roll(target + rowsAway)
        touches++
    }

    LaunchedEffect(still) {
        if (still) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        val round = (LOOP * 1e9f).toLong()
        while (true) clock.floatValue = ((withFrameNanos { it } - origin) % round) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            // A touch takes over; a while later the wheel turns back to the first word and starts over.
            delay((USER_HOLD * 1000).toLong())
            while (holding) delay(100)
            val round = words.size
            roll((target.toFloat() / round).roundToInt() * round)
        }
        // Step n happens n × STEP after the origin, however late the one before it ran.
        val origin = System.nanoTime()
        var n = 1
        while (true) {
            delay(max(0L, (origin + (n * STEP * 1000f).toLong() * 1_000_000L - System.nanoTime()) / 1_000_000L))
            roll(target + 1)
            n++
        }
    }

    val canBlur = Build.VERSION.SDK_INT >= 31
    val count = items.size
    // Enough rows to fill the wheel however few words there are, and a whole number of rounds.
    val rows = count * ceil(8f / count).toInt()

    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val focusY = max(maxHeight.value - GLOW_TOP - FOCUS_ABOVE, insets.calculateTopPadding().value + 100f)
        val bottom = max(insets.calculateBottomPadding().value + 6f, 24f)

        // The light: a dome that breathes, and a brighter patch drifting in it.
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                val k = w / DESIGN_WIDTH
                val t = clock.floatValue
                val ring = listOf(glow[3], glow[3], glow[2], glow[1], glow[0], background)
                val dome = Brush.radialGradient(
                    colorStops = Array(ring.size) { DomeStops[it] to ring[it] },
                    center = Offset.Zero,
                    radius = 1f,
                )
                withTransform({
                    translate(w / 2f, h + DOME_BELOW + DOME_RISE * sin(t / DOME_PERIOD * TURN))
                    scale(DOME_RX * k, DOME_RY, pivot = Offset.Zero)
                }) {
                    drawCircle(dome, radius = 1f, center = Offset.Zero)
                }
                val spot = Brush.radialGradient(
                    colorStops = Array(SpotAt.size) { SpotAt[it] to glow[1].copy(alpha = SPOT_ALPHA * SpotFalloff[it]) },
                    center = Offset.Zero,
                    radius = 1f,
                )
                withTransform({
                    translate(w / 2f + SPOT_DRIFT * sin(t / SPOT_PERIOD * TURN), h - SPOT_ABOVE)
                    scale(SPOT_RX * k, SPOT_RY, pivot = Offset.Zero)
                }) {
                    drawCircle(spot, radius = 1f, center = Offset.Zero)
                }
            }
        }

        Box(
            Modifier
                .offset(y = (focusY - WHEEL / 2f).dp)
                .fillMaxWidth()
                .height(WHEEL.dp)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures { at -> pick(((at.y / density - WHEEL / 2f) / ROW).roundToInt()) }
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    val row = ROW.dp.toPx()
                    // The wheel's place under the finger.
                    var held = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            held = position.value
                            holding = true
                            touches++
                        },
                        onDragEnd = {
                            val speed = -tracker.calculateVelocity().y / row
                            holding = false
                            roll((held + speed * COAST).roundToInt(), speed)
                            touches++
                        },
                        onDragCancel = {
                            holding = false
                            roll(held.roundToInt(), 0f)
                            touches++
                        },
                    ) { change, dragged ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val to = held - dragged / row
                        // One tick for each row that crosses the focus line.
                        if (to.roundToInt() != held.roundToInt()) tick()
                        held = to
                        scope.launch { position.snapTo(to) }
                    }
                }
                .clearAndSetSemantics {
                    stateDescription = items[Math.floorMod(target, count)].word
                    customActions = listOf(
                        CustomAccessibilityAction("Next") { pick(1); true },
                        CustomAccessibilityAction("Previous") { pick(-1); true },
                    )
                },
        ) {
            // A row keeps its word; it is drawn at the nearest of its places on the endless wheel.
            for (index in 0 until rows) {
                key(index) {
                    val item = items[index % count]
                    Row(
                        Modifier
                            .graphicsLayer {
                                val d = offsetOf(index, position.value, rows)
                                translationY = (WHEEL / 2f + (d - 0.5f) * ROW).dp.toPx()
                                alpha = dimOf(d)
                                val sigma = (min(2f, abs(d)) * SOFTEN).dp.toPx()
                                renderEffect = if (canBlur && sigma > 0.5f) {
                                    val radius = sigmaToRadius(sigma)
                                    BlurEffect(radius, radius, TileMode.Decal)
                                } else {
                                    null
                                }
                            }
                            .padding(start = LEFT.dp)
                            .height(ROW.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(
                            Modifier.size(ICON.dp).graphicsLayer {
                                val pop = popOf(offsetOf(index, position.value, rows))
                                scaleX = pop
                                scaleY = pop
                            },
                        ) {
                            scale(size.width / ICON, pivot = Offset.Zero) {
                                for (part in Glyphs.getValue(item.icon)) {
                                    if (part.stroke > 0f) {
                                        drawPath(part.path, part.color, style = Stroke(part.stroke, cap = StrokeCap.Round))
                                    } else {
                                        drawPath(part.path, part.color)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(ICON_GAP.dp))
                        BasicText(item.word, style = wheelText(WORD, WORD_LINE, FontWeight.Medium, ink, -0.5f), maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = bottom.dp)) {
            Canvas(
                Modifier
                    .padding(start = LEFT.dp)
                    .size(TILE.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .clearAndSetSemantics {},
            ) {
                scale(size.width / TILE, pivot = Offset.Zero) {
                    drawLine(Ink, Offset(15f, 15f), Offset(15f, 25f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                    for ((x, y, r) in MarkDots) drawCircle(Ink, r, Offset(x, y))
                }
            }
            BasicText(headline, style = HeadlineStyle, modifier = Modifier.padding(start = LEFT.dp, end = LEFT.dp, top = 18.dp))
            BasicText(caption, style = CaptionStyle, modifier = Modifier.padding(start = LEFT.dp, end = LEFT.dp, top = 14.dp))
            Column(
                Modifier.padding(start = BUTTON_SIDE.dp, end = BUTTON_SIDE.dp, top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Pill(primaryLabel, filled = true) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPrimary?.invoke()
                }
                Pill(secondaryLabel, filled = false) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSecondary?.invoke()
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS), label = "press")
    Box(
        Modifier
            .fillMaxWidth()
            .height(BUTTON.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.12f), CircleShape)
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            .clip(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = if (filled) PrimaryStyle else SecondaryStyle, maxLines = 1)
    }
}
