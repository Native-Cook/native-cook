package com.ngocdevv.nativecook.blocks.onboarding.shapeshufflewelcome

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.max

private val Background = Color.White
private val Ink = Color(0xFF202124)
private val Glyph = Color(0xFF3C4043)

// Where the title's middle and the ground the pieces stand on are, as shares of the screen's height.
private const val TITLE_Y = 0.196f
private const val GROUND_Y = 0.587f
private const val SUBTITLE_GAP = 20f
private const val BUTTON_WIDTH = 108f
private const val BUTTON_HEIGHT = 32f
private const val BUTTON_RADIUS = 4f
private const val BUTTON_RIGHT = 22f
private const val BUTTON_BOTTOM = 9f
private const val MORE_RIGHT = 13f
private const val MORE_TOP = 25f
private const val MORE_DOT = 3f
private const val MORE_GAP = 3f

// The still life is drawn in a box this large round the scene's middle on the ground, with room for a throw.
private const val STAGE_WIDTH = 340f
private const val STAGE_ABOVE = 300f
private const val STAGE_BELOW = 60f

// A touch within this of the scene counts as one on the still life.
private const val REACH_SIDE = 130f
private const val REACH_ABOVE = 180f
private const val REACH_BELOW = 24f
private const val PRESS = 0.96f

// Reduced motion: one composition fades out, the next in.
private const val FADE = 0.3

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun welcomeText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = welcomeText(19f, 24f, FontWeight.Normal, Ink)
private val SubtitleStyle = welcomeText(13f, 18f, FontWeight.Normal, Ink)
private val ButtonStyle = welcomeText(12f, 16f, FontWeight.Medium, Color.White)

/** What shows: the change from one composition to another that began at [t0], on the screen's clock. */
private class Stage(val from: Int, val to: Int, val t0: Double)

/** A piece taken out of its place by a finger: by how much (dp), and when it was let go (negative while held). */
private class Drag(val index: Int, val dx: Float, val dy: Float, val released: Double)

/** autoPlay's plan: when the next change starts, and when the loop may start over after a touch. */
private class Plan {
    var next = Double.POSITIVE_INFINITY
    var resume = Double.POSITIVE_INFINITY
}

/**
 * A welcome screen whose only picture is a small still life of blocks that keeps rebuilding
 * itself: the same eleven pieces tumble, stretch, round off and change colour into four different
 * buildings, each landing with a springy overshoot.
 *
 * @param tint The button's colour.
 * @param autoPlay Rebuilds the still life by itself, one composition every [STEP] seconds, back at the first after [LOOP].
 */
@Composable
fun ShapeShuffleWelcome(
    title: String = "Welcome home",
    subtitle: String = "Control your content and devices from one place.",
    buttonLabel: String = "Get Started",
    tint: Color = Color(0xFF4F46E5),
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The frame loop and the touch handlers outlive a recomposition: they read what the caller passes through this.
    val auto by rememberUpdatedState(autoPlay)

    // Seconds since the first frame. Read while drawing, and when a touch needs the time.
    val clock = remember { mutableDoubleStateOf(0.0) }
    // The first composition, as if it had just been arrived at: every loop begins like this one.
    var stage by remember { mutableStateOf(Stage(COUNT - 1, 0, -CHANGE)) }
    var drag by remember { mutableStateOf(Drag(-1, 0f, 0f, 0.0)) }
    val plan = remember { Plan() }

    LaunchedEffect(autoPlay) {
        plan.next = if (autoPlay) stage.t0 + STEP else Double.POSITIVE_INFINITY
        plan.resume = Double.POSITIVE_INFINITY
    }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            val now = (withFrameNanos { it } - first) / 1e9
            clock.doubleValue = now
            if (!auto) continue
            val s = stage
            if (now >= plan.resume && now >= s.t0 + CHANGE) {
                // The touches are over: back to the first composition, and on with the loop from there.
                plan.resume = Double.POSITIVE_INFINITY
                stage = if (s.to == 0) Stage(0, 0, now - CHANGE) else Stage(s.to, 0, now)
                plan.next = stage.t0 + STEP
            } else if (now >= plan.next) {
                // Timed from when it was due, not from this frame: every loop is the same length.
                stage = Stage(s.to, (s.to + 1) % COUNT, plan.next)
                plan.next += STEP
            }
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from the first composition a while later. */
    fun touched() {
        if (!auto) return
        plan.next = Double.POSITIVE_INFINITY
        plan.resume = clock.doubleValue + USER_HOLD
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val width by rememberUpdatedState(maxWidth.value)
        val ground by rememberUpdatedState(maxHeight.value * GROUND_Y)
        val insets = WindowInsets.safeDrawing.asPaddingValues()

        Box(
            Modifier
                .matchParentSize()
                // A tap on the still life rebuilds it at once.
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val x = at.x / density
                        val y = at.y / density
                        if (abs(x - width / 2f) < REACH_SIDE && y > ground - REACH_ABOVE && y < ground + REACH_BELOW) {
                            touched()
                            val s = stage
                            if (clock.doubleValue >= s.t0 + CHANGE) {
                                stage = Stage(s.to, (s.to + 1) % COUNT, clock.doubleValue)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                }
                // A piece can be pulled out of its place; it springs back.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at ->
                            touched()
                            val x = at.x / density - width / 2f
                            val y = ground - at.y / density
                            // The piece in front, among those the finger is on.
                            var hit = -1
                            Pieces.forEachIndexed { index, piece ->
                                val spot = piece.spots[stage.to]
                                val reach = max(spot.w, spot.h) / 2 + 6
                                if (spot.w > 0 && abs(x - spot.x) < reach && abs(y - spot.y) < reach) hit = index
                            }
                            drag = Drag(hit, 0f, 0f, -1.0)
                        },
                        onDragEnd = { drag = Drag(drag.index, drag.dx, drag.dy, clock.doubleValue) },
                        onDragCancel = { drag = Drag(drag.index, drag.dx, drag.dy, clock.doubleValue) },
                    ) { _, amount ->
                        val d = drag
                        if (d.released < 0) drag = Drag(d.index, d.dx + amount.x / density, d.dy + amount.y / density, -1.0)
                    }
                },
        )

        BasicText(
            title, style = TitleStyle, maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (maxHeight.value * TITLE_Y - 12f).dp).padding(horizontal = 24.dp),
        )
        BasicText(
            subtitle, style = SubtitleStyle, maxLines = 2,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (maxHeight.value * TITLE_Y + SUBTITLE_GAP).dp).padding(horizontal = 32.dp),
        )

        val outline = remember { Path() }
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (maxHeight.value * GROUND_Y - STAGE_ABOVE).dp)
                .size(STAGE_WIDTH.dp, (STAGE_ABOVE + STAGE_BELOW).dp)
                .graphicsLayer {
                    // Reduced motion: the still life fades out and in round a change.
                    val e = clock.doubleValue - stage.t0
                    alpha = if (still && e >= 0 && e < FADE * 2) (abs(e - FADE) / FADE).toFloat() else 1f
                }
                .clearAndSetSemantics {},
        ) {
            surface(0.dp) { _, _, _, _ ->
                val s = stage
                val d = drag
                val now = clock.doubleValue
                val e = now - s.t0
                // Reduced motion: no travel. The pieces change places while the still life is faded out.
                val shown = if (e < FADE) s.from else s.to
                Pieces.forEachIndexed { index, piece ->
                    val p = if (still) pieceAt(index, shown, shown, 0.0) else pieceAt(index, s.from, s.to, e)
                    val left = if (d.index == index) dragLeft(if (d.released < 0) -1.0 else now - d.released).toFloat() else 0f
                    outline.outline(piece.steps, p)
                    withTransform({
                        translate(STAGE_WIDTH / 2f + p.x.toFloat() + d.dx * left, STAGE_ABOVE - p.y.toFloat() + d.dy * left)
                        rotate(p.rotation.toFloat(), Offset.Zero)
                    }) {
                        drawPath(outline, p.color)
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = insets.calculateTopPadding() + MORE_TOP.dp, end = MORE_RIGHT.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    touched()
                    onMore?.invoke()
                }
                .semantics { contentDescription = "More" }
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(MORE_GAP.dp),
        ) {
            repeat(3) { Box(Modifier.size(MORE_DOT.dp).background(Glyph, CircleShape)) }
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) PRESS else 1f, tween(if (pressed) 100 else 160), label = "press")
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = BUTTON_RIGHT.dp, bottom = max(insets.calculateBottomPadding().value, 16f).dp + BUTTON_BOTTOM.dp)
                .size(BUTTON_WIDTH.dp, BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(tint, RoundedCornerShape(BUTTON_RADIUS.dp))
                .clickable(interaction, indication = null, role = Role.Button) {
                    touched()
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onStart?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, style = ButtonStyle, maxLines = 1)
        }
    }
}
