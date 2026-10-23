package com.ngocdevv.nativecook.blocks.onboarding.skyglasssteps

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** @param title Its lines are broken by hand with "\n". */
data class SkyGlassStep(val title: String, val subtitle: String)

/** A row of the first step's preview. */
data class SkyGlassGoal(val label: String, val icon: ImageVector, val color: Color)

/** A card of the second step's preview, from the back of the stack to the front. */
data class SkyGlassLesson(val category: String, val icon: ImageVector, val title: String)

val SampleSkyGlassSteps = listOf(
    SkyGlassStep("Take a short test", "To get your personal plan"),
    SkyGlassStep("Understand your\nwell-being state", "To spark your natural drive"),
    SkyGlassStep("Recalibrate", "Train your mind to focus on practical\noutcomes in any state"),
)

val SampleSkyGlassGoals = listOf(
    SkyGlassGoal("Overcome trauma", Icons.Rounded.Diamond, Color(0xFF3B82F6)),
    SkyGlassGoal("Get things done", Icons.Rounded.LocalFlorist, Color(0xFFEC4899)),
    SkyGlassGoal("Improve well-being", Icons.Rounded.Eco, Color(0xFF65A30D)),
    SkyGlassGoal("Sharpen focus", Icons.Rounded.Adjust, Color(0xFFF97316)),
)

val SampleSkyGlassLessons = listOf(
    SkyGlassLesson("Daily tasks", Icons.Rounded.CheckBox, "Small step\nstep"),
    SkyGlassLesson("Practice", Icons.Rounded.Bolt, "Define your\nhabits"),
    SkyGlassLesson("Learning", Icons.AutoMirrored.Rounded.MenuBook, "Procrastination\ncycle"),
)

private val SkyColors = listOf(Color(0xFF2C57A4), Color(0xFF4A78BD), Color(0xFF7FA3D6))
private val Glass = rgba(58, 74, 102, 0.35f)
private val Accent = Color(0xFF5BA80F)
private val Ink = Color(0xFF1F2937)
private val Mute = Color(0xFF6B7280)
private val Day = Color(0xFF374151)
private val LessonBar = Color(0xFFB5D98B)
private val TextShadow = rgba(16, 36, 84, 0.28f)
private val LessonShadow = rgba(16, 28, 60, 0.2f)

// The screen is laid out for at least this height and scaled down where it is shorter.
private const val MIN_HEIGHT = 720f

// The cards stand this far above the bottom edge, or clear of the navigation bar.
private const val CARD_BOTTOM = 44f
private const val CARD_CLEAR = 8f

// A finger has to travel this far sideways before it takes hold of the carousel.
private const val SLOP = 8f

private const val HEAD_MARGIN = 24f
private const val SUBLINE_GAP = 14f

// Inside a card, from its top left corner.
private const val CHIP_TOP = 33f
private const val CHIP_WIDTH = 56f
private const val CHIP_HEIGHT = 24f
private const val TITLE_TOP = 72f
private const val SUBTITLE_GAP = 5f
private const val TEXT_MARGIN = 20f
private const val PREVIEW_LEFT = 34f
private const val PREVIEW_TOP = 172f
private const val PREVIEW_WIDTH = 230f
private const val PREVIEW_HEIGHT = 244f

private const val ROW_HEIGHT = 55f
private const val ROW_GAP = 8f
private const val ROW_RADIUS = 14f
private const val TILE = 36f

// A lesson card is laid out at the front card's size and scaled about the middle of its top edge.
private const val LESSON_HEIGHT = 88f
private val LessonTops = floatArrayOf(18f, 82f, 156f)
private val LessonScales = floatArrayOf(0.78f, 0.9f, 1f)
private val LessonFills = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.5f), Color.White)

// The grid: seven columns and four rows of squares, placed by their middles.
private const val COLUMNS = 7
private const val SQUARES = 28
private const val SQUARE = 20f
private const val SQUARE_RADIUS = 5.5f
private const val SQUARE_X = 28f
private const val SQUARE_Y = 98f
private const val SQUARE_PITCH_X = 29f
private const val SQUARE_PITCH_Y = 39f
private const val DAYS_Y = 62f
private const val HEADER_Y = 26f
private const val PANEL_MARGIN = 18f

// The lower rows fade out.
private val RowFades = floatArrayOf(1f, 0.8f, 0.45f, 0.25f)
private const val EMPTY_ALPHA = 0.6f

// The check is two strokes, from A by B to C; the short one takes CHECK_SHORT of the drawing.
private val CheckA = Offset(-4.5f, 0.4f)
private val CheckB = Offset(-1.3f, 3.4f)
private val CheckC = Offset(4.5f, -3f)
private const val CHECK_SHORT = 0.38f
private const val CHECK_AFTER = 0.35f

private val NoToggles = List(SQUARES) { 0.0 }

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun skyText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f, align: TextAlign = TextAlign.Start) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadlineStyle = skyText(34f, 40f, FontWeight.SemiBold, Color.White, -0.5f, TextAlign.Center)
private val SublineStyle = skyText(16f, 24f, FontWeight.Normal, Color.White.copy(alpha = 0.85f), align = TextAlign.Center)
private val ChipStyle = skyText(12f, 16f, FontWeight.Medium, Color.White)
private val TitleStyle = skyText(21f, 27f, FontWeight.SemiBold, Color.White, -0.2f, TextAlign.Center)
private val SubtitleStyle = skyText(13f, 19f, FontWeight.Normal, Color.White.copy(alpha = 0.7f), align = TextAlign.Center)
private val RowStyle = skyText(13.5f, 18f, FontWeight.SemiBold, Ink)
private val CategoryStyle = skyText(11f, 16f, FontWeight.Normal, Mute)
private val LessonStyle = skyText(19f, 24f, FontWeight.SemiBold, Ink, -0.2f)
private val PanelTitleStyle = skyText(13f, 18f, FontWeight.SemiBold, Ink)
private val BadgeStyle = skyText(11.5f, 16f, FontWeight.Medium, Mute)
private val DayStyle = skyText(11f, 16f, FontWeight.Normal, Day, align = TextAlign.Center)

/** For a text whose lines are its own: it shrinks before it would wrap. */
private fun shrinking(style: TextStyle, least: Float) =
    TextAutoSize.StepBased(minFontSize = style.fontSize * least, maxFontSize = style.fontSize, stepSize = 0.5.sp)

private fun lines(text: String) = text.count { it == '\n' } + 1

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** What made the focus change: a finger, the loop that plays by itself, or the return to rest. */
private enum class How { Swipe, Auto, Rest }

/** A finger on the carousel: where it has it, where it took it, and the card it started from. */
private data class Drag(val position: Double, val from: Double, val origin: Int)

/** The screen in the block's own dp, and how much those are scaled to fit it. */
private data class Frame(val fit: Float, val width: Float, val height: Float, val top: Float, val cardTop: Float) {
    val cardLeft get() = (width - CARD_WIDTH) / 2f
}

/**
 * An introduction in three steps under a drifting sky. A headline floats on the sky, and the
 * steps are panes of frosted glass in a carousel without end; each pane shows a small preview of
 * its step that plays when the card comes to the middle. Swipe to move on, tap a row or a square
 * to change it.
 *
 * @param headline Its lines are broken by hand with "\n".
 * @param stepLabel The word on each card's chip; the step's number follows it.
 * @param steps The three cards: a short test, a stack of lessons, a routine.
 * @param goals Four rows for the first card.
 * @param lessons Three lesson cards for the second card, back to front.
 * @param days Seven column heads for the third card's grid.
 * @param checkedDays The squares of the grid that tick by themselves, counted along the rows from 0 to 27.
 * @param skyColors The sky from top to bottom.
 * @param glassColor What a pane of glass adds over the blurred sky.
 * @param accentColor The checked squares.
 * @param inkColor The text on the light parts of the previews.
 * @param autoPlay Goes through the steps by itself every [LOOP] seconds.
 */
@Composable
fun SkyGlassSteps(
    headline: String = "It took a lot of\nsteps to become\nwho you are",
    subline: String = "It will take 3 steps to start\ntransformation",
    stepLabel: String = "Step",
    steps: List<SkyGlassStep> = SampleSkyGlassSteps,
    goals: List<SkyGlassGoal> = SampleSkyGlassGoals,
    lessons: List<SkyGlassLesson> = SampleSkyGlassLessons,
    routineTitle: String = "New routine",
    routineBadge: String = "Mastered",
    days: List<String> = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"),
    checkedDays: List<Int> = listOf(1, 4, 5, 8, 11),
    skyColors: List<Color> = SkyColors,
    glassColor: Color = Glass,
    accentColor: Color = Accent,
    inkColor: Color = Ink,
    autoPlay: Boolean = false,
    onStepChange: ((step: Int) -> Unit)? = null,
    onGoalSelect: ((index: Int) -> Unit)? = null,
    onDayToggle: ((index: Int, checked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    // The functions below outlive a recomposition: they read what the caller passes through these.
    val stepChanged by rememberUpdatedState(onStepChange)
    val goalSelected by rememberUpdatedState(onGoalSelect)
    val dayToggled by rememberUpdatedState(onDayToggle)
    // For each square its place among those that tick by themselves, or -1.
    val order by rememberUpdatedState(remember(checkedDays) { checkedDays.sorted().let { sorted -> List(SQUARES) { sorted.indexOf(it) } } })

    // The sky is dark enough for white text: the status bar's icons are light while it shows.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    // The clock's zero: every time below is in seconds since then.
    val origin = remember { System.nanoTime() }
    // The screen opens as it is LEAD after the last card was left: the first step in, and still.
    var slide by remember { mutableStateOf(Slide(page = 0, from = -1.0, velocity = 0.0, at = -LEAD)) }
    var focus by remember { mutableStateOf(Focus(page = 0, was = 2, held = QUIET, at = -LEAD, warm = false, row = 0.0)) }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var pull by remember { mutableStateOf(Pull(x = 0.0, released = 0.0)) }
    var pick by remember { mutableStateOf(Pick(row = -1, from = 0.0, at = 0.0)) }
    // When a finger last changed each square: positive for checked, negative for cleared.
    var toggles by remember { mutableStateOf(NoToggles) }
    // With reduced motion the sky is still: the clock stops once a change has played out.
    var settled by remember { mutableDoubleStateOf(QUIET) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause; it waits while a finger holds.
    var touches by remember { mutableIntStateOf(0) }
    // Seconds since `origin`, while something moves. Read while drawing only.
    val clock = remember { mutableDoubleStateOf(0.0) }

    fun now() = (System.nanoTime() - origin) / 1e9

    /** Where the carousel is right now. */
    fun place() = drag?.position ?: slideAt(slide, now(), still)

    /** Runs the clock until the last change has played out. */
    fun settle() {
        settled = now() + QUIET
    }

    /** Gives a page the focus: its preview starts from the beginning, and the one before fades. */
    fun focusOn(page: Int, how: How) {
        val t = now()
        val old = focus
        val step = stepOf(page)
        val same = step == stepOf(old.page)
        val e = t - old.at
        focus = Focus(
            page = page,
            was = if (same) old.was else stepOf(old.page),
            // A card that had started over was already whole.
            held = if (same) old.held else if (old.warm) max(e, 1.0) else e,
            at = t,
            warm = same,
            row = if (same && step == 0) rowAt(e, if (old.warm) old.row else 0.0, pick, t, still) else 0.0,
        )
        // What a finger changed on a card is forgotten when the card comes round again.
        if (step == 0) pick = Pick(row = -1, from = 0.0, at = 0.0)
        if (step == 2) toggles = NoToggles
        settle()
        if (how == How.Swipe) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        stepChanged?.invoke(step)
    }

    /** Sends the carousel to a page. Taps beside the card, the loop that plays by itself and the return to rest all come through here. */
    fun goTo(page: Int, how: How) {
        slide = Slide(page, from = place(), velocity = 0.0, at = now())
        drag = null
        settle()
        if (page != focus.page || how == How.Rest) focusOn(page, how)
    }

    /** One card on or back. */
    fun move(by: Int, how: How) = goTo(slide.page + by, how)

    /** Back to the first step, the short way round. */
    fun rest() = goTo((place() / STEPS).roundToInt() * STEPS, How.Rest)

    /** Step 1: moves the white fill to a row. */
    fun choose(row: Int) {
        val t = now()
        pick = Pick(row, from = rowAt(t - focus.at, if (focus.warm) focus.row else 0.0, pick, t, still), at = t)
        settle()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        goalSelected?.invoke(row)
    }

    /** Step 3: checks or clears a square. */
    fun toggle(index: Int) {
        // Zero stands for "never touched", so a touch is never stamped with it.
        val t = max(now(), 0.001)
        val checked = if (toggles[index] != 0.0) toggles[index] > 0 else order[index] >= 0
        toggles = toggles.mapIndexed { i, at -> if (i == index) (if (checked) -t else t) else at }
        settle()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        dayToggled?.invoke(index, !checked)
    }

    /** A finger takes hold of the carousel. */
    fun take() {
        val at = place()
        drag = Drag(position = at, from = at, origin = at.roundToInt())
        pull = Pull(x = 0.0, released = -1.0)
        settle()
    }

    /** The finger has moved [moved] dp since it took hold. */
    fun dragTo(moved: Float) {
        val finger = drag ?: return
        val at = finger.from - moved / PITCH
        drag = finger.copy(position = at)
        pull = Pull(x = moved.toDouble(), released = -1.0)
        // The card nearest the middle has the focus, also under a finger.
        if (at.roundToInt() != focus.page) focusOn(at.roundToInt(), How.Swipe)
    }

    /** The finger lets go at [speed] dp a second. */
    fun letGo(speed: Float) {
        val finger = drag ?: return
        val t = now()
        val velocity = (-speed / PITCH).coerceIn(-FLICK_MAX, FLICK_MAX)
        // A flick carries one card further at most.
        val page = (finger.position + velocity * FLICK).roundToInt().coerceIn(finger.origin - 1, finger.origin + 1)
        drag = null
        slide = Slide(page, from = finger.position, velocity = velocity, at = t)
        pull = Pull(x = pull.x, released = t)
        touches++
        if (page != focus.page) focusOn(page, How.Swipe)
        settle()
    }

    /** A tap at a point of the block's own dp: on a row, on a square, or on a card at the side. */
    fun tapped(x: Float, y: Float) {
        if (y < 0f || y > CARD_HEIGHT) return
        touches++
        // The slivers of the cards on either side bring those cards in.
        if (x < 0f || x > CARD_WIDTH) {
            move(if (x < 0f) -1 else 1, How.Swipe)
            return
        }
        val px = x - PREVIEW_LEFT
        val py = y - PREVIEW_TOP
        if (px < 0f || px > PREVIEW_WIDTH || py < 0f || py > PREVIEW_HEIGHT) return
        when (stepOf(slide.page)) {
            0 -> {
                val row = (py / (ROW_HEIGHT + ROW_GAP)).toInt()
                if (row < 4 && py - row * (ROW_HEIGHT + ROW_GAP) <= ROW_HEIGHT) choose(row)
            }
            2 -> {
                val column = ((px - SQUARE_X) / SQUARE_PITCH_X).roundToInt()
                val row = ((py - SQUARE_Y) / SQUARE_PITCH_Y).roundToInt()
                val near = abs(px - SQUARE_X - column * SQUARE_PITCH_X) <= SQUARE_PITCH_X / 2f &&
                    abs(py - SQUARE_Y - row * SQUARE_PITCH_Y) <= SQUARE_PITCH_Y / 2f
                if (near && column in 0 until COLUMNS && row in 0 until SQUARES / COLUMNS) toggle(row * COLUMNS + column)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = now()
            // The sky never rests; with reduced motion it is still, and nothing is drawn again
            // once a change has played out, until a touch or the loop sets something moving.
            clock.doubleValue = if (still) min(t, settled) else t
            if (still && t >= settled) snapshotFlow { settled }.first { it > now() }
        }
    }

    val holding by remember { derivedStateOf { drag != null } }
    LaunchedEffect(autoPlay, touches, holding) {
        if (!autoPlay || holding) return@LaunchedEffect
        var start = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // A loop starts LEAD after the first step took the focus.
            rest()
            start = System.nanoTime() + (LEAD * 1e9).toLong()
        }
        while (true) {
            for (at in AUTO_SWIPES) {
                until(at)
                move(1, How.Auto)
            }
            start += (LOOP * 1e9).toLong()
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val headlineStyle = remember(density) {
        HeadlineStyle.copy(shadow = Shadow(TextShadow, Offset(0f, density.density), 12f * density.density))
    }
    val sublineStyle = remember(density) {
        SublineStyle.copy(shadow = Shadow(TextShadow, Offset(0f, density.density), 8f * density.density))
    }
    val lessonShadow = remember { SoftRoundRect(LessonShadow, sigma = 8f) }

    BoxWithConstraints(modifier.fillMaxSize().background(skyColors.first())) {
        val fit = min(1f, maxHeight.value / MIN_HEIGHT)
        val height = maxHeight.value / fit
        val frame = Frame(
            fit = fit,
            width = maxWidth.value / fit,
            height = height,
            top = insets.calculateTopPadding().value / fit,
            cardTop = height - max(CARD_BOTTOM, insets.calculateBottomPadding().value / fit + CARD_CLEAR) - CARD_HEIGHT,
        )

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val t = clock.doubleValue
            val unit = this.density * frame.fit
            scale(unit, unit, pivot = Offset.Zero) {
                drawSky(
                    width = frame.width,
                    height = frame.height,
                    turn = if (still) 0f else ((t / LOOP) % 1.0).toFloat(),
                    position = drag?.position ?: slideAt(slide, t, still),
                    cardTop = frame.cardTop,
                    colors = skyColors,
                    glass = glassColor,
                )
            }
        }

        // Everything else stands in the block's own dp, scaled with it from the top left corner.
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(frame.width.dp, frame.height.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = frame.fit
                    scaleY = frame.fit
                },
        ) {
            Column(
                Modifier
                    .offset(y = frame.top.dp)
                    .size(frame.width.dp, max(0f, frame.cardTop - frame.top).dp)
                    .graphicsLayer { translationX = parallaxAt(pull, clock.doubleValue, still).dp.toPx() }
                    .padding(horizontal = HEAD_MARGIN.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The lines are the headline's own, so it shrinks before it would wrap.
                BasicText(headline, Modifier.semantics { heading() }, style = headlineStyle, maxLines = lines(headline), autoSize = shrinking(HeadlineStyle, 0.7f))
                Spacer(Modifier.height(SUBLINE_GAP.dp))
                BasicText(subline, style = sublineStyle, maxLines = lines(subline), autoSize = shrinking(SublineStyle, 0.7f))
            }

            steps.take(STEPS).forEachIndexed { index, step ->
                Box(
                    Modifier
                        .offset(frame.cardLeft.dp, frame.cardTop.dp)
                        .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                        .graphicsLayer {
                            val position = drag?.position ?: slideAt(slide, clock.doubleValue, still)
                            translationX = (offsetOf(index, position) * PITCH).toFloat().dp.toPx()
                        }
                        .clearAndSetSemantics {},
                ) {
                    Box(
                        Modifier
                            .offset(((CARD_WIDTH - CHIP_WIDTH) / 2f).dp, CHIP_TOP.dp)
                            .size(CHIP_WIDTH.dp, CHIP_HEIGHT.dp)
                            .background(Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("$stepLabel ${index + 1}", style = ChipStyle, maxLines = 1)
                    }
                    Column(
                        Modifier.offset(TEXT_MARGIN.dp, TITLE_TOP.dp).width((CARD_WIDTH - TEXT_MARGIN * 2f).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(step.title, style = TitleStyle, maxLines = lines(step.title), autoSize = shrinking(TitleStyle, 0.8f))
                        Spacer(Modifier.height(SUBTITLE_GAP.dp))
                        BasicText(step.subtitle, style = SubtitleStyle, maxLines = lines(step.subtitle), autoSize = shrinking(SubtitleStyle, 0.8f))
                    }
                    Box(Modifier.offset(PREVIEW_LEFT.dp, PREVIEW_TOP.dp).size(PREVIEW_WIDTH.dp, PREVIEW_HEIGHT.dp)) {
                        when (index) {
                            0 -> Goals(goals, inkColor) {
                                val t = clock.doubleValue
                                val here = focus
                                val preview = previewAt(0, here, t)
                                GoalsPose(preview, rowAt(preview.e, if (preview.warm) here.row else 0.0, pick, t, still), still)
                            }
                            1 -> lessons.take(3).forEachIndexed { depth, lesson ->
                                Lesson(depth, lesson, inkColor, lessonShadow) {
                                    val preview = previewAt(1, focus, clock.doubleValue)
                                    val pose = enter(preview.e, depth * LESSON_STAGGER, LESSON_SHIFT, preview.warm, still)
                                    val lift = if (depth == 2) liftAt(preview.e, still).toFloat() else 0f
                                    Entrance(pose.opacity * preview.shown, pose.y - lift)
                                }
                            }
                            else -> Routine(routineTitle, routineBadge, days, checkedDays.size, accentColor, inkColor, still) {
                                RoutinePose(previewAt(2, focus, clock.doubleValue), clock.doubleValue, order, toggles)
                            }
                        }
                    }
                }
            }

            // The carousel as one thing a screen reader can step through.
            val shown = stepOf(focus.page)
            val current = steps[min(shown, steps.lastIndex)]
            Box(
                Modifier
                    .offset(y = frame.cardTop.dp)
                    .size(frame.width.dp, CARD_HEIGHT.dp)
                    .semantics {
                        contentDescription = "$stepLabel ${shown + 1}"
                        stateDescription = "${current.title.replace('\n', ' ')}. ${current.subtitle.replace('\n', ' ')}"
                        progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..(STEPS - 1).toFloat(), STEPS - 2)
                        setProgress { value ->
                            val by = value.roundToInt() - shown
                            if (by != 0) {
                                touches++
                                move(by.coerceIn(-1, 1), How.Swipe)
                            }
                            by != 0
                        }
                    },
            )
        }

        // One touch is a swipe of the carousel or a tap.
        Box(
            Modifier.fillMaxSize().pointerInput(frame) {
                // Pixels to one of the block's own dp.
                val unit = this.density * frame.fit
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var grab = 0f
                    var dragging = false
                    var far = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val across = (change.position.x - down.position.x) / unit
                        if (!change.pressed) {
                            if (dragging) {
                                letGo(tracker.calculateVelocity().x / unit)
                            } else if (!far) {
                                tapped(change.position.x / unit - frame.cardLeft, change.position.y / unit - frame.cardTop)
                            }
                            break
                        }
                        if ((change.position - down.position).getDistance() > SLOP * unit) far = true
                        if (!dragging && abs(across) > SLOP) {
                            dragging = true
                            grab = across
                            take()
                        }
                        if (dragging) {
                            dragTo(across - grab)
                            change.consume()
                        }
                    }
                    // A touch that was taken away still lets the carousel go.
                    letGo(0f)
                }
            },
        )
    }
}

/** Step 1 at one moment: its clock, the row the fill is on, and whether motion is reduced. */
private class GoalsPose(val preview: Preview, val row: Double, val still: Boolean) {
    fun part(index: Int) = enter(preview.e, index * ROW_STAGGER, ROW_SHIFT, preview.warm, still)
}

/** Step 1: four goals; a white fill marks one and steps down the list. */
@Composable
private fun Goals(goals: List<SkyGlassGoal>, ink: Color, pose: () -> GoalsPose) {
    Box(
        Modifier
            .size(PREVIEW_WIDTH.dp, ROW_HEIGHT.dp)
            .graphicsLayer {
                val now = pose()
                // The fill comes in with the row it is on.
                val part = now.part(now.row.roundToInt())
                alpha = part.opacity * now.preview.shown
                translationY = ((now.row * (ROW_HEIGHT + ROW_GAP)).toFloat() + part.y).dp.toPx()
            }
            .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(ROW_RADIUS.dp)),
    )
    goals.take(4).forEachIndexed { index, goal ->
        Row(
            Modifier
                .offset(y = (index * (ROW_HEIGHT + ROW_GAP)).dp)
                .size(PREVIEW_WIDTH.dp, ROW_HEIGHT.dp)
                .graphicsLayer {
                    val now = pose()
                    val part = now.part(index)
                    alpha = part.opacity * now.preview.shown
                    translationY = part.y.dp.toPx()
                }
                .background(Color.White.copy(alpha = 0.26f), RoundedCornerShape(ROW_RADIUS.dp))
                .padding(start = 10.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(TILE.dp).background(Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(goal.icon, contentDescription = null, tint = goal.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            BasicText(goal.label, Modifier.weight(1f), style = RowStyle.copy(color = ink), maxLines = 1)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

/** Step 2: one lesson card: the front card's layout, scaled about the middle of its top edge. */
@Composable
private fun Lesson(depth: Int, lesson: SkyGlassLesson, ink: Color, shadow: SoftRoundRect, pose: () -> Entrance) {
    val front = depth == 2
    Box(
        Modifier
            .offset(y = LessonTops[depth].dp)
            .size(PREVIEW_WIDTH.dp, LESSON_HEIGHT.dp)
            .graphicsLayer {
                val now = pose()
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = LessonScales[depth]
                scaleY = LessonScales[depth]
                translationY = now.y.dp.toPx()
                alpha = now.opacity
                // The front card's shadow lies outside its box, which a layer of its own would cut off.
                if (front) compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind {
                if (front) surface(0.dp) { _, _, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 6f, w, h, 14f) } }
            }
            .background(LessonFills[depth], RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.offset(12.dp, 11.5.dp).size(6.dp, 65.dp).background(LessonBar, RoundedCornerShape(3.dp)))
        Row(Modifier.offset(28.dp, 11.dp).height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(lesson.icon, contentDescription = null, tint = Mute, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(5.dp))
            BasicText(lesson.category, style = CategoryStyle, maxLines = 1)
        }
        BasicText(
            lesson.title,
            Modifier.offset(28.dp, 30.dp).width((PREVIEW_WIDTH - 40f).dp),
            style = LessonStyle.copy(color = ink),
            maxLines = 2,
        )
    }
}

/** Step 3 at one moment: its clock, the screen's clock, and what ticks by itself or by a finger. */
private class RoutinePose(val preview: Preview, val t: Double, val order: List<Int>, val toggles: List<Double>)

/** Step 3: a month of a routine; squares tick green one by one, and a finger can change any of them. */
@Composable
private fun Routine(title: String, badge: String, days: List<String>, ticks: Int, accent: Color, ink: Color, still: Boolean, pose: () -> RoutinePose) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val preview = pose().preview
                val panel = panelAt(preview.e, preview.warm, still)
                alpha = panel.opacity * preview.shown
                scaleX = panel.scale
                scaleY = panel.scale
            }
            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.offset(PANEL_MARGIN.dp, (HEADER_Y - 9f).dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(title, style = PanelTitleStyle.copy(color = ink), maxLines = 1)
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset((-PANEL_MARGIN).dp, (HEADER_Y - 9f).dp)
                .height(18.dp)
                .graphicsLayer {
                    val scale = pulseAt(pose().preview.e, ticks, still)
                    scaleX = scale
                    scaleY = scale
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Mute, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            BasicText(badge, style = BadgeStyle, maxLines = 1)
        }
        days.take(COLUMNS).forEachIndexed { index, day ->
            BasicText(
                day,
                Modifier.offset((SQUARE_X + (index - 0.5f) * SQUARE_PITCH_X).dp, (DAYS_Y - 8f).dp).width(SQUARE_PITCH_X.dp),
                style = DayStyle,
                maxLines = 1,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val now = pose()
            scale(density, density, pivot = Offset.Zero) {
                for (i in 0 until SQUARES) {
                    val row = i / COLUMNS
                    val fade = RowFades[row]
                    val tick = tickAt(now.preview.e, now.order[i], now.toggles[i], now.t, still)
                    withTransform({
                        translate(SQUARE_X + (i % COLUMNS) * SQUARE_PITCH_X, SQUARE_Y + row * SQUARE_PITCH_Y)
                        scale(tick.pop, tick.pop, pivot = Offset.Zero)
                    }) {
                        val corner = Offset(-SQUARE / 2f, -SQUARE / 2f)
                        drawRoundRect(Color.White, corner, Size(SQUARE, SQUARE), CornerRadius(SQUARE_RADIUS), alpha = EMPTY_ALPHA * fade)
                        if (tick.q > 0f) {
                            drawRoundRect(accent, corner, Size(SQUARE, SQUARE), CornerRadius(SQUARE_RADIUS), alpha = smooth(tick.q.toDouble(), 0.0, 0.6).toFloat() * fade)
                            // With motion the check is drawn stroke by stroke; without, it fades in whole.
                            val drawn = if (still) 1f else ((tick.q - CHECK_AFTER) / (1f - CHECK_AFTER)).coerceIn(0f, 1f)
                            val white = (if (still) tick.q else 1f) * fade
                            if (drawn > 0f) {
                                val short = min(1f, drawn / CHECK_SHORT)
                                drawLine(Color.White, CheckA, CheckA + (CheckB - CheckA) * short, strokeWidth = 2f, cap = StrokeCap.Round, alpha = white)
                            }
                            if (drawn > CHECK_SHORT) {
                                val long = (drawn - CHECK_SHORT) / (1f - CHECK_SHORT)
                                drawLine(Color.White, CheckB, CheckB + (CheckC - CheckB) * long, strokeWidth = 2f, cap = StrokeCap.Round, alpha = white)
                            }
                        }
                    }
                }
            }
        }
    }
}
