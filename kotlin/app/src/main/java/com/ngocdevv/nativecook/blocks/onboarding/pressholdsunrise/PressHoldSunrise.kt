package com.ngocdevv.nativecook.blocks.onboarding.pressholdsunrise

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Cream = Color(0xFFFBF8F1)
private val Ink = Color(0xFF251C21)
private val Body = Color(0xFF2B2B2B)
private val Dot = Color(0xFFEFE9DC)
private val DiscFill = Color(0xFFFFFDFF)
private val Accent = listOf(Color(0xFFFBD28A), Color(0xFFF5A742))
private val Night = Color(0xFF221F23)

// The tunnel's dark shapes are a little warmer than the page they end in.
private val Shade = Color(0xFF2B2529)
private val NightDot = Color(0xFF2C292D)
private val NightInk = Color(0xFFF4EFE6)
private val NightDisc = Color(0xFF343134)
private val Ball = listOf(Color(0xFFFECA78), Color(0xFFF9A53A))
private val Track = Color(0xFF3D3A3D)
private val TrackOn = Color(0xFFF4D58A)
private val Secondary = Color(0xFF3B383B)

// Where things are, in dp from the top safe area, the screen's bottom or a disc's centre.
private const val MARK_Y = 60f
private const val NAME_Y = 128f
private const val TAGLINE_Y = 178f
private const val HEADING_Y = 111f

/** The welcome's disc and the first page's, as fractions of the screen's height. */
private const val DISC_AT = 0.61f
private const val NIGHT_DISC_AT = 0.506f
private const val NIGHT_DISC_SIZE = 219f
private const val LABEL_BELOW = 39f
private const val HINT_ABOVE = 30f
private const val PAGER_BELOW = 159f
private const val PAGER_WIDTH = 110f
private const val PAGER_SAG = 6f
private const val ACTION = 44f
private const val ACTION_STEP = 53f
private const val ACTION_MARGIN = 24f

/** The finger does not have to hit the small button: this much round the disc's centre takes the hold. */
private const val HOLD_TARGET = 132f

// The halftone field: 2 dp dots on a 10 dp grid, from above the disc to below it.
private const val GRID = 10f
private const val FIELD_ABOVE = 230f
private const val FIELD_BELOW = 130f
private const val FIELD_FADE = 70f

/** The three balls: x and y from the disc's centre, and the diameter. The last is in front. */
private val Balls = listOf(floatArrayOf(-19f, -54f, 83f), floatArrayOf(56f, -33f, 70f), floatArrayOf(0f, 44f, 116f))

/** Turn of each tunnel shape when it is born, in degrees. */
private val Turns = floatArrayOf(45f, 0f, 10f, 0f, 0f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun sunriseText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, serif: Boolean = false, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = sunriseText(28f, 34f, Ink, serif = true)
private val TaglineStyle = sunriseText(15f, 19f, Body)
private val HintStyle = sunriseText(10f, 14f, Ink, FontWeight.Medium, spacing = 0.6f)
private val ButtonLabelStyle = sunriseText(16f, 20f, Ink, serif = true)
private val HeadingStyle = sunriseText(22f, 27f, NightInk, serif = true)
private val ActionStyle = sunriseText(14f, 18f, Night, FontWeight.SemiBold)

private enum class Holder { Hand, Auto }

/**
 * The scene on its way from one time to another. [late] is how long ago it was due, so that a
 * timer that fires late does not shift it. An early release is [springy]; everything else runs at
 * one second a second.
 */
private class Run(val from: Float, val to: Float, val duration: Float, val landing: Float, val late: Float = 0f, val springy: Boolean = false)

private tailrec fun Context.sunriseActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.sunriseActivity()
    else -> null
}

/** Dots on a grid across the width, smaller towards the field's edges, which reads as fading out. */
private fun halftone(width: Float, top: Float, bottom: Float) = Path().apply {
    var y = top + GRID / 2f
    while (y < bottom) {
        var x = (width / 2f) % GRID
        while (x < width) {
            val size = smooth(min(x, width - x), 0f, FIELD_FADE) * smooth(min(y - top, bottom - y), 0f, FIELD_FADE)
            if (size > 0.08f) addOval(androidx.compose.ui.geometry.Rect(x - size, y - size, x + size, y + size))
            x += GRID
        }
        y += GRID
    }
}

/**
 * A welcome screen whose one button has to be held: it grows until it fills its disc, then
 * bursts over the screen and a tunnel of shapes, dark and orange in turn, carries the app from
 * the light welcome into its dark first page. Tapping the mark there goes back the same way.
 *
 * @param name The app's name under its mark.
 * @param buttonLabel Under the bolt, inside the button once it has grown.
 * @param hint The line at the bottom of the welcome.
 * @param heading The first page's heading.
 * @param accent The button and the orange shapes, top to bottom.
 * @param pages Number of pages the pager's arc stands for; the first is the one shown.
 * @param autoPlay Holds the button by itself every [LOOP] seconds, and comes back to the welcome.
 * @param onDone Called when the hold completes and the tunnel starts.
 * @param onBack Called when the mark on the first page is tapped and the welcome returns.
 */
@Composable
fun PressHoldSunrise(
    name: String = "Motes.",
    tagline: String = "Tiny changes,\nremarkable results.",
    buttonLabel: String = "Get Started",
    hint: String = "PRESS AND HOLD TO GET STARTED",
    heading: String = "Create a few impactful habits\nand adjust them over time.",
    primaryLabel: String = "Create first habit",
    secondaryLabel: String = "Continue with an account",
    accent: List<Color> = Accent,
    pages: Int = 5,
    autoPlay: Boolean = false,
    onDone: (() -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The gestures and the loop outlive a recomposition: what they read is state.
    val calm by rememberUpdatedState(rememberReducedMotion())
    val done by rememberUpdatedState(onDone)
    val backed by rememberUpdatedState(onBack)

    // The scene time. Read while drawing only, so a moving scene redraws and does not recompose.
    val scene = remember { mutableFloatStateOf(0f) }
    var run by remember { mutableStateOf<Run?>(null) }
    // The hold in progress: whose it is, for the haptic at its end and for a touch that interrupts.
    var holder by remember { mutableStateOf<Holder?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Where the scene is: 0 on the welcome at rest, 1 on the first page at rest, 2 on the way.
    val place by remember { derivedStateOf { if (run != null) 2 else if (scene.floatValue == AT_B) 1 else 0 } }
    val dark by remember { derivedStateOf { night(scene.floatValue, calm) } }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** The finger goes down on the button: the scene runs to the first page unless it is let go in time. */
    fun press(by: Holder, late: Float = 0f) {
        val s = scene.floatValue
        if (s >= HOLD) return
        holder = by
        val to = if (calm) HOLD + CALM_FADE else AT_B
        run = Run(from = s, to = to, duration = to - s, landing = AT_B, late = late)
    }

    /** Let go before the hold was complete: the button springs back. */
    fun release() {
        holder = null
        val s = scene.floatValue
        if (run == null || s <= 0f || s >= HOLD) return
        run = Run(from = s, to = 0f, duration = RELEASE, landing = 0f, springy = true)
    }

    fun goBack(late: Float = 0f) {
        if (run != null || scene.floatValue != AT_B) return
        val to = if (calm) AT_B + CALM_FADE else END
        // The end of the way back is the welcome at rest.
        run = Run(from = AT_B, to = to, duration = to - AT_B, landing = 0f, late = late)
    }

    /** A touch takes over from the autoPlay loop, which starts again from the welcome a while later. */
    fun touched() {
        if (holder == Holder.Auto) release()
        touches++
    }

    LaunchedEffect(run) {
        val now = run ?: return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) {
            val x = ((withFrameNanos { it } - origin) / 1e9f + now.late) / now.duration
            if (x >= 1f) break
            val before = scene.floatValue
            scene.floatValue = now.from + (now.to - now.from) * (if (now.springy) releaseEase(x) else x)
            if (before < HOLD && scene.floatValue >= HOLD) {
                if (holder == Holder.Hand) {
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                }
                holder = null
                done?.invoke()
            }
        }
        scene.floatValue = now.landing
        run = null
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            // Three seconds of rest, counted from the end of what the touch set off.
            var quiet = 0f
            while (quiet < USER_HOLD) {
                delay(100)
                quiet = if (run == null) quiet + 0.1f else 0f
            }
            // The loop starts over from the welcome.
            while (run != null || scene.floatValue != 0f) {
                goBack()
                delay(100)
            }
        }
        var origin = System.nanoTime()

        /** Waits for the moment [seconds] into the loop, and says how late it woke up. */
        suspend fun until(seconds: Float): Float {
            val due = origin + (seconds * 1e9f).toLong()
            delay(max(0L, (due - System.nanoTime()) / 1_000_000))
            return (System.nanoTime() - due) / 1e9f
        }
        while (true) {
            press(Holder.Auto, late = until(PRESS_AT))
            goBack(late = until(BACK_AT))
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The gallery's bars have light icons; the welcome is a light screen and needs dark ones.
    DisposableEffect(view, dark) {
        val controller = view.context.sunriseActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !dark
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Cream)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val inset = insets.calculateBottomPadding().value
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2f
        val cy = (height * DISC_AT).roundToInt().toFloat()
        val nightY = (height * NIGHT_DISC_AT).roundToInt().toFloat()
        // The tunnel's shapes grow until they reach the corner furthest from the disc.
        val reach = hypot(cx, max(cy, height - cy)) * REACH
        val bottom = max(inset + 6f, 20f)

        val dots = remember(width, cy) { halftone(width, cy - FIELD_ABOVE, cy + FIELD_BELOW) }
        val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.06f), sigma = 15f) }
        val orange = remember(accent) { Brush.verticalGradient(accent, startY = -1f, endY = 1f) }
        val ballBrushes = remember(nightY) {
            Balls.map { (_, y, size) -> Brush.verticalGradient(Ball, startY = nightY + y - size / 2f, endY = nightY + y + size / 2f) }
        }
        val pager = remember(cx, nightY) {
            Path().apply {
                moveTo(cx - PAGER_WIDTH / 2f, nightY + PAGER_BELOW)
                quadraticTo(cx, nightY + PAGER_BELOW + PAGER_SAG * 2f, cx + PAGER_WIDTH / 2f, nightY + PAGER_BELOW)
            }
        }
        val pagerMeasure = remember(pager) { PathMeasure().apply { setPath(pager, false) } }
        val pagerDrawn = remember { Path() }
        val shape = remember { Grown() }

        // The welcome's field of dots and its disc.
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, _, _ ->
                drawPath(dots, Dot)
                scale(discScale(scene.floatValue), pivot = Offset(cx, cy)) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, cx - DISC / 2f, cy - DISC / 2f + 8f, DISC, DISC, DISC / 2f) }
                    drawCircle(DiscFill, DISC / 2f, Offset(cx, cy))
                }
            }
        }

        Box(Modifier.matchParentSize().graphicsLayer { alpha = textAlpha(scene.floatValue, calm) }.then(if (place == 1) Modifier.clearAndSetSemantics {} else Modifier)) {
            Mark(Ink, Cream, Modifier.align(Alignment.TopCenter).offset(y = (top + MARK_Y - 15f).dp))
            BasicText(name, Modifier.fillMaxWidth().offset(y = (top + NAME_Y - 17f).dp), style = NameStyle, maxLines = 1)
            BasicText(tagline, Modifier.fillMaxWidth().offset(y = (top + TAGLINE_Y - 9.5f).dp), style = TaglineStyle)
            BasicText(hint, Modifier.fillMaxWidth().offset(y = (height - max(inset, 12f) - HINT_ABOVE - 7f).dp), style = HintStyle, maxLines = 1)
        }

        // The orange circle, the shapes that follow it, the bolt, and the first page's picture.
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                val s = scene.floatValue
                fill(Round, cx, cy - buttonRise(s), buttonRadius(s, reach, calm), 0f, 1f, brush = orange)

                if (calm) {
                    drawRect(Night, size = androidx.compose.ui.geometry.Size(w, h), alpha = dusk(s))
                } else {
                    for (index in 0..4) {
                        grown(s, index, reach, shape)
                        when (index) {
                            0 -> fill(Square, cx, cy, shape.radius, Turns[0] + shape.turn, shape.alpha, color = Shade)
                            1 -> fill(Scallop, cx, cy, shape.radius, Turns[1] + shape.turn, shape.alpha, brush = orange)
                            2 -> fill(Squircle, cx, cy, shape.radius, Turns[2] + shape.turn, shape.alpha, color = Shade)
                            3 -> fill(Blob, cx, cy, shape.radius, Turns[3] + shape.turn, shape.alpha, brush = orange)
                            else -> fill(Round, cx, cy, shape.radius, Turns[4] + shape.turn, shape.alpha, color = Night)
                        }
                    }
                    // The way back, the last to shrink first: it lies under the others.
                    shrunk(s, 2, reach, shape)
                    fill(Square, cx, cy, shape.radius, Turns[0] + shape.turn, shape.alpha, color = Shade)
                    shrunk(s, 1, reach, shape)
                    fill(Scallop, cx, cy, shape.radius, Turns[1] + shape.turn, shape.alpha, brush = orange)
                    shrunk(s, 0, reach, shape)
                    fill(Round, cx, cy, shape.radius, shape.turn, shape.alpha, color = Night)
                }

                translate(cx - 8f, cy - BUTTON_RISE - 8f) { drawPath(Bolt, Color.Black, alpha = labelAlpha(s, calm)) }

                val page = pageAlpha(s, calm)
                if (page > 0f) {
                    val disc = arrived(s, DISC_AFTER, DISC_IN, calm)
                    drawPath(dots, NightDot, alpha = page * disc)
                    drawCircle(NightDisc, NIGHT_DISC_SIZE / 2f * (0.8f + 0.2f * disc), Offset(cx, nightY), alpha = page * disc)
                    Balls.forEachIndexed { index, (x, y, size) ->
                        translate(0f, -ballLift(s, index, calm)) {
                            drawCircle(ballBrushes[index], size / 2f, Offset(cx + x, nightY + y), alpha = page * ballAlpha(s, index, calm))
                        }
                    }
                    val drawn = pagerDrawn(s, calm)
                    if (drawn > 0f) {
                        val stroke = Stroke(2f, cap = StrokeCap.Round)
                        pagerDrawn.rewind()
                        pagerMeasure.getSegment(0f, pagerMeasure.length * drawn, pagerDrawn, true)
                        drawPath(pagerDrawn, Track, alpha = page, style = stroke)
                        pagerDrawn.rewind()
                        pagerMeasure.getSegment(0f, pagerMeasure.length * min(drawn, 1f / max(1, pages)), pagerDrawn, true)
                        drawPath(pagerDrawn, TrackOn, alpha = page, style = stroke)
                    }
                }
            }
        }

        BasicText(
            buttonLabel,
            Modifier.fillMaxWidth().offset(y = (cy + LABEL_BELOW - 10f).dp).graphicsLayer { alpha = labelAlpha(scene.floatValue, calm) }.clearAndSetSemantics {},
            style = ButtonLabelStyle,
            maxLines = 1,
        )

        // A layer that is not shown still takes touches, so only one of the two screens is there to touch.
        if (place != 1) {
            Box(
                Modifier
                    .offset((cx - HOLD_TARGET / 2f).dp, (cy - HOLD_TARGET / 2f).dp)
                    .size(HOLD_TARGET.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            tap()
                            press(Holder.Hand)
                            waitForUpOrCancellation()
                            release()
                        }
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "$buttonLabel. $hint"
                        onClick {
                            press(Holder.Hand)
                            true
                        }
                    },
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (top + MARK_Y - 15f - 14f).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        tap()
                        goBack()
                        backed?.invoke()
                    }
                    .padding(14.dp)
                    .size(26.dp, 30.dp)
                    .semantics { contentDescription = "Back to the welcome" },
            )
        }

        Mark(
            Color.White,
            Night,
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (top + MARK_Y - 15f).dp)
                .graphicsLayer { alpha = pageAlpha(scene.floatValue, calm) * arrived(scene.floatValue, 0f, MARK_IN, calm) },
        )
        BasicText(
            heading,
            Modifier
                .fillMaxWidth()
                .offset(y = (top + HEADING_Y - 13.5f).dp)
                .graphicsLayer {
                    val shown = arrived(scene.floatValue, HEADING_AFTER, HEADING_IN, calm)
                    alpha = pageAlpha(scene.floatValue, calm) * shown
                    translationX = HEADING_SLIDE.dp.toPx() * (1f - shown)
                }
                .then(if (place == 1) Modifier else Modifier.clearAndSetSemantics {}),
            style = HeadingStyle,
        )
        Action(scene, calm, 0, primaryLabel, Color.White, Night, enabled = place == 1, Modifier.offset(y = (height - bottom - ACTION - ACTION_STEP).dp)) {
            tap()
            onPrimary?.invoke()
        }
        Action(scene, calm, 1, secondaryLabel, Secondary, Color.White, enabled = place == 1, Modifier.offset(y = (height - bottom - ACTION).dp)) {
            tap()
            onSecondary?.invoke()
        }
    }
}

/** Fills a shape drawn round the origin, moved to ([x], [y]), turned and scaled to [radius]. */
private fun DrawScope.fill(path: Path, x: Float, y: Float, radius: Float, turn: Float, alpha: Float, color: Color = Color.Unspecified, brush: Brush? = null) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        rotate(turn, Offset.Zero)
        scale(radius, radius, Offset.Zero)
    }) {
        if (brush != null) drawPath(path, brush, alpha) else drawPath(path, color, alpha)
    }
}

/** The app's mark: a rounded square with a dot low in it. */
@Composable
private fun Mark(fill: Color, dot: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(26.dp, 30.dp).background(fill, RoundedCornerShape(9.dp)).clearAndSetSemantics {}) {
        Box(Modifier.offset(9.dp, 15.dp).size(9.dp).background(dot, CircleShape))
    }
}

@Composable
private fun Action(
    scene: androidx.compose.runtime.FloatState,
    calm: Boolean,
    index: Int,
    label: String,
    fill: Color,
    ink: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dim by animateFloatAsState(if (pressed) 0.75f else 1f, tween(120), label = "action")
    Box(
        modifier
            .padding(horizontal = ACTION_MARGIN.dp)
            .fillMaxWidth()
            .height(ACTION.dp)
            .graphicsLayer {
                val shown = arrived(scene.floatValue, ACTION_AFTER + index * ACTION_GAP, ACTION_IN, calm)
                alpha = pageAlpha(scene.floatValue, calm) * shown * dim
                translationY = ACTION_RISE.dp.toPx() * (1f - shown)
            }
            .background(fill, CircleShape)
            .then(
                if (enabled) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier.clearAndSetSemantics {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = ActionStyle.copy(color = ink), maxLines = 1)
    }
}
