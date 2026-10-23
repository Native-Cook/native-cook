package com.ngocdevv.nativecook.blocks.onboarding.liquidswipepages

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * @param color The page's colour.
 * @param ink The colour of everything written on it.
 */
data class LiquidPage(val title: String, val body: String, val color: Color, val ink: Color, val art: ArtKind)

val SamplePages = listOf(
    LiquidPage("Best Digital Solution", "Everything your team needs, in one tidy place.", Color(0xFF392EB8), Color(0xFF35D0F5), ArtKind.Tiles),
    LiquidPage("Achieve Your Goal", "Set a target and watch the steps add up.", Color(0xFF32C96F), Color(0xFFF6F1A7), ArtKind.Flag),
    LiquidPage("Increase Your Value", "Small wins compound. We keep the score.", Color(0xFFE76239), Color(0xFFFFD9C2), ArtKind.Coins),
)

private const val MARGIN = 28f

// The texts stop short of the bump.
private const val TEXT_RIGHT = 96f

// The picture's centre, as a share of the screen's height.
private const val ART_Y = 0.3f
private const val TITLE_GAP = 18f
private const val BODY_HEIGHT = 48f
private const val PAGER_GAP = 42f
private const val DASH = 14f
private const val DASH_WIDE = 22f
private const val DASH_HEIGHT = 3f
private const val DASH_GAP = 6f
private const val BUTTON_GAP = 82f
private const val BUTTON_WIDTH = 152f
private const val BUTTON_WIDE = 180f
private const val BUTTON_HEIGHT = 58f
private const val BUTTON_RADIUS = 8f
private const val BUTTON_STROKE = 1.5f
private val ArrowInk = Color.White.copy(alpha = 0.8f)
private const val RING = 36f
private val Chevron = Path().apply { moveTo(-3f, -6f); lineTo(3f, 0f); lineTo(-3f, 6f) }

// The right side of the screen takes the drag; a tap this close to the arrow plays the wave.
private const val GRIP = 80f
private const val ARROW_REACH = 28f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun pageText(size: Float, lineHeight: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = pageText(24f, 30f, FontWeight.Bold)
private val BodyStyle = pageText(15f, 24f, FontWeight.Normal)
private val ButtonStyle = pageText(14f, 18f, FontWeight.Bold, 0.4f)

/** autoPlay's plan: when the next wave starts, and when the loop may start over after a touch. */
private class Plan {
    var next = Double.POSITIVE_INFINITY
    var resume = Double.POSITIVE_INFINITY

    // The travel a drag started from, and how far the finger has gone since, in dp.
    var grabbed = 0.0
    var dragged = 0.0
}

/**
 * Onboarding pages that change with a liquid wave. The next page waits behind the right edge as a
 * bump with an arrow in it; dragging the bump pulls a wave of the next page's colour across the
 * screen, and letting go past the middle sends it to the left side, where it settles with one
 * soft overshoot. The pages' content stays where it is: the wave covers one and uncovers the next.
 *
 * @param pages The pages, in order; the last one's wave leads back to the first.
 * @param skipLabel The button of every page but the last, which goes to the last page.
 * @param doneLabel The last page's button.
 * @param autoPlay Pulls the wave by itself, one page every [PERIOD] seconds, back at the first page after [LOOP].
 * @param onPageChange Called when a wave has brought a page in.
 * @param onDone Called by the last page's button.
 */
@Composable
fun LiquidSwipePages(
    pages: List<LiquidPage> = SamplePages,
    skipLabel: String = "SKIP",
    doneLabel: String = "GET STARTED",
    autoPlay: Boolean = false,
    onPageChange: ((index: Int) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The frame loop and the touch handlers outlive a recomposition: they read what the caller passes through these.
    val items by rememberUpdatedState(pages)
    val auto by rememberUpdatedState(autoPlay)
    val changed by rememberUpdatedState(onPageChange)
    val skipped by rememberUpdatedState(onSkip)
    val done by rememberUpdatedState(onDone)

    // Seconds since the first frame. Read while drawing, and when a touch needs the time.
    val clock = remember { mutableDoubleStateOf(0.0) }
    var wave by remember { mutableStateOf(resting(0, -LEAD)) }
    val plan = remember { Plan() }

    LaunchedEffect(autoPlay) {
        // Periods start on the ambient beat, so that what moves by itself looks the same in each.
        plan.next = if (autoPlay) ceil(clock.doubleValue / AMBIENT) * AMBIENT + DRAG_AT else Double.POSITIVE_INFINITY
        plan.resume = Double.POSITIVE_INFINITY
    }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            val now = (withFrameNanos { it } - first) / 1e9
            clock.doubleValue = now
            val before = wave
            val w = settle(before, now)
            if (w !== before) {
                wave = w
                if (w.page != before.page) changed?.invoke(w.page)
            }
            if (!auto || w.mode != Mode.Rest) continue
            if (now >= plan.resume) {
                // Back to the first page if a touch left another one showing, then on with the loop.
                plan.resume = Double.POSITIVE_INFINITY
                var free = max(now, w.t0 + LEAD)
                if (w.page != 0) {
                    wave = script(w.page, 0, now, SKIP_DRAG, SKIP_SPEED)
                    free = now + SKIP_DRAG + RELEASE / SKIP_SPEED + LEAD
                }
                plan.next = ceil(free / AMBIENT) * AMBIENT + DRAG_AT
            } else if (now >= plan.next) {
                // Timed from when it was due, not from this frame: every loop is the same length.
                wave = script(w.page, (w.page + 1) % items.size, plan.next, DRAG_FOR, 1.0)
                plan.next += PERIOD
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val width by rememberUpdatedState(maxWidth.value.toDouble())
        val height by rememberUpdatedState(maxHeight.value.toDouble())
        val bottom = max(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding().value - 5f, 24f)

        /** This frame. Called while drawing, so that the moving clock recomposes nothing. */
        fun shape() = resolve(wave, clock.doubleValue, width, height, items.size, still)

        /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
        fun touched(now: Double) {
            if (!auto) return
            plan.next = Double.POSITIVE_INFINITY
            plan.resume = now + USER_HOLD
            val w = wave
            // A wave the loop is still dragging out is dropped; one it has let go runs to its end.
            if (w.mode == Mode.Script && now < w.t0 + w.dragFor) {
                val from = held(w, now, width, height, still)
                wave = w.copy(mode = Mode.Backing, t0 = now, travel = from.travel, cy = from.cy)
            }
        }

        /** Plays a whole wave to [target], as the autoPlay loop does. */
        fun play(target: Int, dragFor: Double, speed: Double): Boolean {
            val now = clock.doubleValue
            touched(now)
            val w = settle(wave, now)
            if (w.mode != Mode.Rest || target == w.page) return false
            wave = script(w.page, target, now, dragFor, speed)
            return true
        }

        fun grab(y: Double) {
            val now = clock.doubleValue
            touched(now)
            val w = settle(wave, now)
            if (w.mode != Mode.Rest && w.mode != Mode.Backing) return
            val from = held(w, now, width, height, still)
            plan.grabbed = from.travel
            plan.dragged = 0.0
            wave = Wave(Mode.Hand, w.page, (w.page + 1) % items.size, now, travel = from.travel, cy = y, cyFrom = from.cy)
        }

        fun letGo(velocity: Double) {
            val w = wave
            if (w.mode != Mode.Hand) return
            val now = clock.doubleValue
            val from = held(w, now, width, height, still)
            val complete = w.travel > width * COMPLETE_AT || velocity < -FLICK
            wave = w.copy(mode = if (complete) Mode.LetGo else Mode.Backing, t0 = now, travel = from.travel, cy = from.cy)
            if (complete) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        fun press() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (settle(wave, clock.doubleValue).page == items.size - 1) {
                touched(clock.doubleValue)
                done?.invoke()
            } else if (play(items.size - 1, SKIP_DRAG, SKIP_SPEED)) {
                skipped?.invoke()
            }
        }

        // The pages that are composed: this changes only when a wave is let go or arrives.
        val showing by remember { derivedStateOf { shape().let { it.page to it.incoming } } }
        val (page, incoming) = showing
        val last = pages.size - 1

        Box(Modifier.matchParentSize().drawBehind { drawRect(items[shape().page].color) })

        PageContent(
            pages[page], page, pages.size, if (page == last) doneLabel else skipLabel, wide = page == last,
            enabled = incoming < 0, clock = clock, still = still, bottom = bottom, reveal = { 1f }, onPress = ::press,
        )

        val outline = remember { Path() }
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                val s = shape()
                val colors = items
                outline.outline(s, w, h)
                drawPath(outline, lerp(colors[(s.page + 1) % colors.size].color, colors[s.tint].color, s.mix.toFloat()))
                // Reduced motion: the next page's colour fades in over the screen.
                if (s.fade > 0) drawRect(colors[s.tint].color, alpha = s.fade.toFloat())
                translate((w - s.reach - s.depth + ARROW_INSET).toFloat(), s.cy.toFloat()) {
                    drawCircle(ArrowInk, (RING - 1.5f) / 2f, Offset.Zero, alpha = s.arrow.toFloat(), style = Stroke(1.5f))
                    drawPath(Chevron, ArrowInk, alpha = s.arrow.toFloat(), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        if (incoming >= 0) {
            // Over the wave, and not to be touched before it has arrived.
            PageContent(
                pages[incoming], incoming, pages.size, if (incoming == last) doneLabel else skipLabel, wide = incoming == last,
                enabled = false, clock = clock, still = still, bottom = bottom, reveal = { shape().reveal.toFloat() }, onPress = {},
            )
        }

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(GRIP.dp)
                .fillMaxHeight()
                .semantics {
                    role = Role.Button
                    contentDescription = "Next page"
                    onClick { play((settle(wave, clock.doubleValue).page + 1) % items.size, TAP_DRAG, 1.0) }
                }
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val near = abs(at.x / density - (GRIP - STRIP - BUMP + ARROW_INSET)) < ARROW_REACH && abs(at.y / density - height * BUMP_Y) < ARROW_REACH
                        if (near && play((settle(wave, clock.doubleValue).page + 1) % items.size, TAP_DRAG, 1.0)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { at ->
                            tracker.resetTracking()
                            grab(at.y.toDouble() / density)
                        },
                        onDragEnd = { letGo(tracker.calculateVelocity().x.toDouble() / density) },
                        // A drag the system took away springs back.
                        onDragCancel = { letGo(0.0) },
                    ) { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        plan.dragged -= amount.x / density
                        val w = wave
                        if (w.mode == Mode.Hand) wave = w.copy(travel = max(0.0, plan.grabbed + plan.dragged), cy = change.position.y.toDouble() / density)
                    }
                },
        )
    }
}

/**
 * What is written and drawn on one page. It never moves with the wave: the wave uncovers it.
 *
 * @param enabled Only the page that is showing takes touches.
 * @param reveal How far the page has come in, read while drawing: it fades, and its picture grows.
 */
@Composable
private fun PageContent(
    page: LiquidPage,
    index: Int,
    count: Int,
    label: String,
    wide: Boolean,
    enabled: Boolean,
    clock: DoubleState,
    still: Boolean,
    bottom: Float,
    reveal: () -> Float,
    onPress: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = reveal() }.then(if (enabled) Modifier else Modifier.clearAndSetSemantics {})) {
        Artwork(
            page.art, page.color, clock, still,
            Modifier
                .offset((maxWidth.value - ART_WIDTH).dp / 2, (maxHeight.value * ART_Y - ART_HEIGHT / 2f).dp)
                .graphicsLayer {
                    val scale = if (still) 1f else 0.9f + 0.1f * reveal()
                    scaleX = scale
                    scaleY = scale
                },
        )

        Column(Modifier.align(Alignment.BottomStart).padding(start = MARGIN.dp, end = TEXT_RIGHT.dp, bottom = bottom.dp)) {
            BasicText(page.title, style = TitleStyle.copy(color = page.ink), maxLines = 1)
            Spacer(Modifier.height(TITLE_GAP.dp))
            BasicText(page.body, style = BodyStyle.copy(color = page.ink.copy(alpha = 0.85f)), maxLines = 2, modifier = Modifier.height(BODY_HEIGHT.dp))
            Spacer(Modifier.height(PAGER_GAP.dp))
            Row(
                Modifier.height(DASH_HEIGHT.dp).clearAndSetSemantics { contentDescription = "Page ${index + 1} of $count" },
                horizontalArrangement = Arrangement.spacedBy(DASH_GAP.dp),
            ) {
                for (dash in 0 until count) {
                    Box(
                        Modifier
                            .size((if (dash == index) DASH_WIDE else DASH).dp, DASH_HEIGHT.dp)
                            .background(page.ink.copy(alpha = if (dash == index) 1f else 0.4f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(BUTTON_GAP.dp))
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val dim by animateFloatAsState(if (pressed) 0.6f else 1f, tween(120), label = "button")
            Box(
                Modifier
                    .size((if (wide) BUTTON_WIDE else BUTTON_WIDTH).dp, BUTTON_HEIGHT.dp)
                    .graphicsLayer { alpha = dim }
                    .border(BUTTON_STROKE.dp, page.ink, RoundedCornerShape(BUTTON_RADIUS.dp))
                    .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onPress),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = ButtonStyle.copy(color = page.ink), maxLines = 1)
            }
        }
    }
}
