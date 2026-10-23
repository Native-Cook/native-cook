package com.ngocdevv.nativecook.blocks.navigation.stackgridswitcher

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Figure { Chevron, Disc, Rings, Stripes, Square, Dots, Wave, Triangle }

/**
 * @param colors The cover's gradient, top to bottom.
 * @param figure The one figure drawn on the cover, in [tint].
 * @param edge The sleeve's front edge, where the title is written.
 * @param light The edge is light: its title is written in black.
 */
data class Sleeve(
    val title: String,
    val artist: String,
    val colors: List<Color>,
    val figure: Figure,
    val tint: Color,
    val edge: Color,
    val light: Boolean = false,
)

data class Section(val title: String, val items: List<Sleeve>)

private fun sleeve(title: String, artist: String, top: Long, bottom: Long, figure: Figure, tint: Long, edge: Long, light: Boolean = false) =
    Sleeve(title, artist, listOf(Color(top), Color(bottom)), figure, Color(tint), Color(edge), light)

val SampleSections = listOf(
    Section(
        "Albums",
        listOf(
            sleeve("Open Window", "Lowe", 0xFFFFFFFF, 0xFFD9DBE0, Figure.Chevron, 0xFF1F4FD8, 0xFFC9CBD1, light = true),
            sleeve("Night Bus", "Ada Renn", 0xFF0B0B0C, 0xFF4A2E1C, Figure.Disc, 0xFFF28A2E, 0xFF1A1310),
            sleeve("Quiet Arcs", "Tomas Vale", 0xFFE6E6E8, 0xFFCFCFD4, Figure.Rings, 0xFF111111, 0xFFB9B9BF, light = true),
            sleeve("Paper Weather", "Jae Min", 0xFF7B3FE4, 0xFF2A1E7A, Figure.Stripes, 0xFFFF8CC6, 0xFF4B2FA6),
            sleeve("Red Room", "Ostrow", 0xFFFFFFFF, 0xFFF1F1F1, Figure.Square, 0xFFD92B2B, 0xFFE4E4E6, light = true),
            sleeve("Gold Standard", "Mara Wilde", 0xFF0A0A0A, 0xFF1B1708, Figure.Dots, 0xFFD8B24A, 0xFF2A2412),
            sleeve("Blue Hour", "Dean Laurel", 0xFF1E8C8C, 0xFF06181C, Figure.Wave, 0xFFD6F2EE, 0xFF123C40),
            sleeve("Half Light", "Noor Sato", 0xFF2B2B2F, 0xFF121214, Figure.Rings, 0xFFFFFFFF, 0xFF0E0E10),
            sleeve("Soft Machines", "Iri", 0xFFB9A3F2, 0xFF9C86E0, Figure.Triangle, 0xFF6B52C8, 0xFF7E66D6),
            sleeve("Salt Lines", "Peder Holm", 0xFFF4E9D8, 0xFFE3CDAA, Figure.Stripes, 0xFF9C5B2E, 0xFFD4BC96, light = true),
            sleeve("Low Sun", "Cass Arden", 0xFFF2A03D, 0xFFC63D2F, Figure.Disc, 0xFFFFE9B8, 0xFF9E2F25),
            sleeve("Glass Garden", "Yun Hale", 0xFF0E3B2E, 0xFF061712, Figure.Dots, 0xFF7BE0A8, 0xFF0A241C),
            sleeve("Field Notes", "Rowan Pike", 0xFFDDE7F2, 0xFFB8C8DB, Figure.Wave, 0xFF28456B, 0xFFA6B7CC, light = true),
            sleeve("Night Shift", "Odile", 0xFF15182B, 0xFF05060D, Figure.Chevron, 0xFF7C8CFF, 0xFF0B0D1A),
            sleeve("Plain Song", "The Marrows", 0xFFEFE6F0, 0xFFD9C6DC, Figure.Square, 0xFF5A2A62, 0xFFC7B1CB, light = true),
        ),
    ),
    Section(
        "Playlists",
        listOf(
            sleeve("Morning Commute", "24 songs", 0xFFFFD9A8, 0xFFF29A6B, Figure.Disc, 0xFFFFFFFF, 0xFFD9794C),
            sleeve("Deep Focus", "41 songs", 0xFF10233F, 0xFF060B17, Figure.Rings, 0xFF6FA8FF, 0xFF0A1528),
            sleeve("Sunday Kitchen", "18 songs", 0xFFF6F1E4, 0xFFE6DCC2, Figure.Dots, 0xFFC2542B, 0xFFD6CAA9, light = true),
            sleeve("Long Drive", "56 songs", 0xFF3C2A6B, 0xFF140E2B, Figure.Wave, 0xFFFFB36B, 0xFF22184A),
            sleeve("Rain Room", "33 songs", 0xFFC9D6DC, 0xFF9FB2BC, Figure.Stripes, 0xFF3D5560, 0xFF8DA1AB, light = true),
            sleeve("After Hours", "27 songs", 0xFF1A1A1E, 0xFF000000, Figure.Triangle, 0xFFE0457B, 0xFF101013),
            sleeve("Fresh Finds", "50 songs", 0xFFD4F2C4, 0xFF8FD39A, Figure.Chevron, 0xFF1E5B3A, 0xFF79BD86, light = true),
            sleeve("Slow Dance", "15 songs", 0xFFF2C4D0, 0xFFD98AA4, Figure.Square, 0xFF7A2440, 0xFFC4728E),
            sleeve("Run Club", "38 songs", 0xFFFFE14D, 0xFFF2A81D, Figure.Stripes, 0xFF1A1A1E, 0xFFD99114, light = true),
        ),
    ),
)

private val Page = Color(0xFF9198AC)
private val PageLight = Color(0xFFB4B8C6)
private val SegmentFill = Color.White.copy(alpha = 0.25f)
private val SegmentInk = Color(0xFF111111)
private val SegmentMute = Color.White.copy(alpha = 0.85f)
// No blur behind the dock here, so it is a little more solid than the frosted one of the other stacks.
private val DockFill = Color.White.copy(alpha = 0.62f)
private val DockInk = Color(0xFF1A1A1E)
private val DockMute = Color(0xFF7C7C88)

// The top bar, under the safe area's top edge.
private const val BAR_TOP = 6f
private const val BAR = 35f
private const val BAR_SIDE = 22f
private const val SEGMENT = 81f
private const val SEGMENT_INSET = 2.5f
private const val SEGMENT_SLIDE = 250
private const val TOUCH = 44f
private const val DOCK_WIDTH = 188f
private const val DOCK_HEIGHT = 55f

// The stack: the middle of the first sleeve's front edge under the safe area's top edge, the
// distance from one sleeve to the next, the edge's height and the room at the sides.
private const val STACK_TOP = 66f
private const val PITCH = 86f
private const val STRIP = 14f
private const val STACK_SIDE = 36f
// The eye is level with this fraction of the screen's height: sleeves above it show their
// underside, sleeves below it their cover.
private const val EYE = 0.453f
// A sleeve lies flat. Its far edge is drawn at this scale, and its cover rises (or hangs) by up to
// LIFT_MAX dp, reached REACH dp from eye level; at eye level only the front edge shows.
private const val FAR = 0.7f
private const val LIFT_MAX = 71f
private const val REACH = 300f
private const val SHADE = 0.3f
private const val PRESS_SCALE = 0.05f
private const val PRESS_MS = 140

// The grid.
private const val COLUMNS = 3
private const val GRID_TOP = 45f
private const val GRID_SIDE = 4.5f
private const val GRID_GAP = 6f
private const val GRID_CAMERA = 800f

// A switch, in seconds from the tap: the view on screen leaves for LEAVE, the page is empty for a
// beat, and from ARRIVE the other view assembles on springs, one piece after another.
private const val LEAVE = 0.32f
private const val ARRIVE = 0.6f
private const val SPREAD = 120f
private const val LEAVE_SCALE = 0.8f
private const val LEAVE_BLUR = 16f
private const val ARRIVE_BLUR = 12f
private const val ARRIVE_CLEAR = 0.4f
private const val SLEEVE_STAGGER = 0.035f
private const val SLEEVE_RANKS = 8
private const val SLEEVE_FROM = 0.6f
private const val LABEL_AT = 1.05f
private const val LABEL_IN = 0.3f
private const val COVER_STAGGER = 0.03f
private const val COVER_RANKS = 20
private const val COVER_TILT = 70f
private const val COVER_SHIFT = 60f
private const val COVER_LEAVE = 0.22f
private const val COVER_LEAVE_STAGGER = 0.01f
private const val PIECE_IN = 0.12f
// The spring of every arriving piece (mass 1, stiffness 220, damping 20), as a function of time:
// 1 - e^(-DECAY t) (cos(RATE t) + DECAY / RATE sin(RATE t)); it is taken as settled after SPRING_FOR.
private const val SPRING_DECAY = 10f
private const val SPRING_RATE = 10.954f
private const val SPRING_FOR = 0.7f
private const val GLYPH_FROM = 0.25f
private const val GLYPH_TO = 0.55f
// The clock stops here, and the toggle takes another press.
private const val SWITCH_END = 1.75f
// With reduced motion the two views cross-fade.
private const val FADE = 0.25f
// Changing the segment: what is shown fades and blurs out, the other half comes in the same way.
private const val SWAP = 0.5f
private const val SWAP_BLUR = 10f

// The loop: the toggle is pressed to go to the grid, and again to come back.
const val LOOP = 9f
private const val TO_GRID_AT = 1.2f
private const val TO_STACK_AT = 5f
private const val USER_HOLD = 3f

// Glyphs in boxes of 24 dp.
private fun strokes(vararg lines: List<Pair<Float, Float>>) = Path().apply {
    lines.forEach { line ->
        line.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    }
}

private val Sort = strokes(listOf(7f to 20f, 7f to 4f), listOf(3f to 8f, 7f to 4f), listOf(17f to 4f, 17f to 20f), listOf(21f to 16f, 17f to 20f))
private val Lines = strokes(listOf(5f to 6f, 19f to 6f), listOf(5f to 10f, 19f to 10f), listOf(5f to 14f, 19f to 14f), listOf(5f to 18f, 19f to 18f))
private val Squares = Path().apply {
    listOf(5f to 5f, 14f to 5f, 5f to 14f, 14f to 14f).forEach { (x, y) ->
        moveTo(x, y); lineTo(x + 5f, y); lineTo(x + 5f, y + 5f); lineTo(x, y + 5f); close()
    }
}
private val DockSleeves = strokes(listOf(5f to 4f, 5f to 20f), listOf(9f to 4f, 9f to 20f))
private val DockSleeve = strokes(listOf(12.5f to 4f, 19f to 6f, 19f to 18f, 12.5f to 20f)).apply { close() }
private val DockSmile = Path().apply {
    moveTo(8.5f, 14f)
    cubicTo(9.4f, 15.6f, 10.6f, 16.4f, 12f, 16.4f)
    cubicTo(13.4f, 16.4f, 14.6f, 15.6f, 15.5f, 14f)
}
private val DockList = strokes(listOf(4f to 6f, 16f to 6f), listOf(4f to 11f, 13f to 11f), listOf(4f to 16f, 10f to 16f), listOf(17.5f to 16.5f, 17.5f to 9f, 20.5f to 10f))

// The figures of the covers, in boxes of 100 units.
private val ChevronPath = strokes(listOf(28f to 32f, 50f to 68f, 72f to 32f))
private val WavePath = Path().apply {
    moveTo(14f, 54f)
    cubicTo(26f, 34f, 38f, 34f, 50f, 54f)
    cubicTo(62f, 74f, 74f, 74f, 86f, 54f)
}
private val TrianglePath = strokes(listOf(50f to 26f, 76f to 72f, 24f to 72f)).apply { close() }

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val StripStyle = TextStyle(fontSize = 8.5.sp, lineHeight = 12.sp, lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val SegmentStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false))

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private fun smooth(x: Float, from: Float, to: Float): Float {
    val t = clamp01((x - from) / (to - from))
    return t * t * (3f - 2f * t)
}

private fun easeIn(t: Float): Float {
    val c = clamp01(t)
    return c * c * c
}

/** Where an arriving piece is, [t] seconds after it set off: 0 to 1 with one small overshoot. */
private fun spring(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= SPRING_FOR) return 1f
    return 1f - exp(-SPRING_DECAY * t) * (cos(SPRING_RATE * t) + SPRING_DECAY / SPRING_RATE * sin(SPRING_RATE * t))
}

/** How far out of focus a view is, against ARRIVE_BLUR: going out as it leaves, coming in as it assembles. */
private fun viewBlur(t: Float, leaving: Boolean) =
    if (leaving) LEAVE_BLUR / ARRIVE_BLUR * easeIn(t / LEAVE) else 1f - smooth(t, ARRIVE, ARRIVE + ARRIVE_CLEAR)

/** A blur of [sigma] pixels, from Android 12 on; before that the views only fade and move. */
private fun blurOf(sigma: Float): RenderEffect? =
    if (Build.VERSION.SDK_INT >= 31 && sigma > 0.3f) sigmaToRadius(sigma).let { BlurEffect(it, it, TileMode.Decal) } else null

/** Where things are on a screen of a given size, in dp. */
private class Frame(val width: Float, val height: Float, val top: Float, val bottom: Float) {
    val stackTop = top + STACK_TOP
    val eye = height * EYE
    val sleeve = width - STACK_SIDE * 2
    val cell = (width - GRID_SIDE * 2 - GRID_GAP * (COLUMNS - 1)) / COLUMNS
}

/** Where a sleeve's front edge is, how big and how visible the sleeve is, and its title. */
private class SleevePose(val y: Float, val scale: Float, val opacity: Float, val label: Float)

private fun sleevePose(index: Int, t: Float, toGrid: Boolean, scroll: Float, frame: Frame, still: Boolean): SleevePose {
    val base = frame.stackTop + index * PITCH - scroll
    if (still) {
        val there = smooth(t, 0f, FADE)
        return SleevePose(base, 1f, if (toGrid) 1f - there else there, 1f)
    }
    if (toGrid) {
        // Leaving: the sleeves above eye level rise and the others sink, shrinking.
        val u = easeIn(t / LEAVE)
        return SleevePose(base + (if (base < frame.eye) -SPREAD else SPREAD) * u, 1f - (1f - LEAVE_SCALE) * u, 1f - smooth(u, 0.4f, 1f), 1f)
    }
    // Arriving: from eye level out to their shelves, the middle ones first.
    val middle = ((frame.eye - frame.stackTop + scroll) / PITCH).roundToInt()
    val since = t - ARRIVE - min(abs(index - middle), SLEEVE_RANKS) * SLEEVE_STAGGER
    // A sleeve whose shelf is off the screen is put there without the trip across it.
    val a = if (base < -PITCH || base > frame.height + PITCH) 1f else spring(since)
    return SleevePose(frame.eye + (base - frame.eye) * a, SLEEVE_FROM + (1f - SLEEVE_FROM) * a, smooth(since, 0f, PIECE_IN), smooth(t, LABEL_AT, LABEL_AT + LABEL_IN))
}

/** The turn in degrees of a flat sleeve's cover about its front edge, and the camera that draws it, [h] dp below eye level. */
private class Turn(val degrees: Float, val camera: Float)

private fun tilt(h: Float, size: Float): Turn {
    val near = 1f - min(abs(h) / REACH, 1f)
    val lift = LIFT_MAX * (1f - near * near)
    val angle = acos(min(lift / (size * FAR), 1f))
    val degrees = Math.toDegrees(angle.toDouble()).toFloat()
    // Past a right angle the cover hangs below the edge and is seen from underneath.
    return Turn(if (h >= 0f) degrees else 180f - degrees, FAR * size * sin(angle) / (1f - FAR))
}

/** How a cover of the grid is tipped (in degrees), moved and shown. */
private class CoverPose(val tilt: Float, val shift: Float, val opacity: Float)

private fun coverPose(index: Int, count: Int, t: Float, toGrid: Boolean, still: Boolean): CoverPose {
    if (still) {
        val there = smooth(t, 0f, FADE)
        return CoverPose(0f, 0f, if (toGrid) there else 1f - there)
    }
    if (toGrid) {
        // Arriving in reading order: tipped back and off to the top left, then flat in its cell.
        val since = t - ARRIVE - min(index, COVER_RANKS) * COVER_STAGGER
        val b = spring(since)
        return CoverPose(COVER_TILT * (1f - b), -COVER_SHIFT * (1f - b), smooth(since, 0f, PIECE_IN))
    }
    // Leaving in reverse order: tipping back.
    val v = easeIn((t - min(count - 1 - index, COVER_RANKS) * COVER_LEAVE_STAGGER) / COVER_LEAVE)
    return CoverPose(COVER_TILT * v, -COVER_SHIFT * 0.5f * v, 1f - smooth(v, 0.3f, 1f))
}

/**
 * A music library with two views of the same sleeves. The stack shows them lying flat, one above
 * the other, in perspective: above eye level you look at their underside, below it at their covers,
 * and scrolling moves them through that. The toggle at the top right blows the stack apart and
 * assembles a flat grid of the covers, and back.
 *
 * @param sections The two halves of the library; the segmented control chooses one.
 * @param initialGrid Starts on the grid instead of the stack.
 * @param autoPlay Presses the view toggle by itself, to the grid and back, every [LOOP] seconds.
 * @param onSelect Called with a sleeve or a cover that was tapped.
 * @param onViewChange Called when the toggle changes the view.
 */
@Composable
fun StackGridSwitcher(
    modifier: Modifier = Modifier,
    sections: List<Section> = SampleSections,
    initialGrid: Boolean = false,
    autoPlay: Boolean = false,
    onSelect: ((Sleeve) -> Unit)? = null,
    onViewChange: ((grid: Boolean) -> Unit)? = null,
    onSort: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val initial by rememberUpdatedState(initialGrid)
    val selected by rememberUpdatedState(onSelect)
    val changed by rememberUpdatedState(onViewChange)

    // The view shown or arriving.
    var grid by remember { mutableStateOf(initialGrid) }
    // The segment chosen, and the one whose sleeves are drawn: it follows half a swap later.
    var segment by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Seconds since the toggle was pressed; SWITCH_END at rest.
    val clock = remember { Animatable(SWITCH_END) }
    // The sleeve under a finger, and how far it has come forward.
    var pressed by remember { mutableIntStateOf(-1) }
    val press = remember { Animatable(0f) }
    // 0 to 1 across a change of segment; 0 at rest.
    val swap = remember { Animatable(0f) }
    // How far the toggle is pressed in, and where the segmented control's capsule is.
    val push = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val stackScroll = rememberScrollState()
    val gridScroll = rememberScrollState()

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** The toggle: the view on screen leaves and the other one assembles. */
    fun flip(next: Boolean, auto: Boolean) {
        if (clock.value < SWITCH_END || next == grid) return
        if (!auto) tick()
        scope.launch {
            push.animateTo(1f, tween(90))
            push.animateTo(0f, tween(200))
        }
        grid = next
        changed?.invoke(next)
        // Started at once, so that no frame shows the new view with the old clock.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clock.snapTo(0f)
            clock.animateTo(SWITCH_END, tween((SWITCH_END * 1000).toInt(), easing = LinearEasing))
        }
    }

    /** The segmented control: the capsule slides, and what is shown is swapped while it is out of sight. */
    fun choose(next: Int, auto: Boolean = false) {
        if (next == segment || swap.value > 0f) return
        if (!auto) tick()
        segment = next
        scope.launch { slide.animateTo(next.toFloat(), tween(SEGMENT_SLIDE, easing = EaseOutStrong)) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            swap.animateTo(0.5f, tween((SWAP * 500).toInt(), easing = LinearEasing))
            shown = next
            stackScroll.scrollTo(0)
            gridScroll.scrollTo(0)
            swap.animateTo(1f, tween((SWAP * 500).toInt(), easing = LinearEasing))
            swap.snapTo(0f)
        }
    }

    /** The state every loop starts from, set at once. */
    suspend fun rest() {
        clock.snapTo(SWITCH_END)
        swap.snapTo(0f)
        slide.snapTo(0f)
        push.snapTo(0f)
        press.snapTo(0f)
        grid = initial
        segment = 0
        shown = 0
        stackScroll.scrollTo(0)
        gridScroll.scrollTo(0)
    }

    fun lift(index: Int, down: Boolean) {
        if (down) pressed = index
        scope.launch { press.animateTo(if (down) 1f else 0f, tween(PRESS_MS)) }
    }

    fun select(item: Sleeve) {
        tick()
        selected?.invoke(item)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            // After a touch: back to the first view of the first segment, then the loop again.
            delay((USER_HOLD * 1000).toLong())
            while (clock.value < SWITCH_END || swap.value > 0f) delay(100)
            if (segment != 0) {
                choose(0, auto = true)
                delay((SWAP * 1000).toLong() + 100)
            }
            if (grid != initial) {
                flip(initial, auto = true)
                delay((SWITCH_END * 1000).toLong() + 100)
            }
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            rest()
            until(TO_GRID_AT)
            flip(!initial, auto = true)
            until(TO_STACK_AT)
            flip(initial, auto = true)
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(PageLight, Page), Offset(size.width / 2f, size.height * 0.45f), size.height * 0.55f))
            }
            // A touch takes over from the autoPlay loop, which starts again from rest a while later.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val frame = Frame(maxWidth.value, maxHeight.value, insets.calculateTopPadding().value, max(insets.calculateBottomPadding().value, 12f))
        val items = sections[shown.coerceIn(0, sections.lastIndex)].items

        // Both views, through a change of segment.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val there = smooth(abs(swap.value - 0.5f) * 2f, 0f, 1f)
                alpha = there
                if (!reducedMotion) {
                    scaleX = 0.97f + 0.03f * there
                    scaleY = scaleX
                    renderEffect = blurOf((SWAP_BLUR * (1f - there)).dp.toPx())
                }
            },
        ) {
            Stack(items, shown, frame, grid, reducedMotion, clock, stackScroll, pressed, press, ::lift, ::select)
            Grid(items, shown, frame, grid, reducedMotion, clock, gridScroll, ::select)
        }

        Row(
            Modifier
                .offset((BAR_SIDE - (TOUCH - 24f) / 2f).dp, (frame.top + BAR_TOP - (TOUCH - BAR) / 2f).dp)
                .size((frame.width - BAR_SIDE * 2 + TOUCH - 24f).dp, TOUCH.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(TOUCH.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Sort") { onSort?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph { drawPath(Sort, Color.White, style = it(1.8f)) }
            }

            Box(Modifier.size((SEGMENT * 2).dp, BAR.dp).background(SegmentFill, CircleShape)) {
                Box(
                    Modifier
                        .offset(SEGMENT_INSET.dp, SEGMENT_INSET.dp)
                        .size((SEGMENT - SEGMENT_INSET).dp, (BAR - SEGMENT_INSET * 2).dp)
                        .graphicsLayer { translationX = (slide.value * (SEGMENT - SEGMENT_INSET)).dp.toPx() }
                        .background(Color.White, CircleShape),
                )
                Row {
                    sections.take(2).forEachIndexed { index, section ->
                        val color by animateColorAsState(if (segment == index) SegmentInk else SegmentMute, tween(SEGMENT_SLIDE), label = "segment")
                        Box(
                            Modifier
                                .size(SEGMENT.dp, BAR.dp)
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { choose(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(section.title, style = SegmentStyle, maxLines = 1, color = { color })
                        }
                    }
                }
            }

            Box(
                Modifier
                    .size(TOUCH.dp)
                    .clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (grid) "Show as a stack" else "Show as a grid",
                    ) { flip(!grid, auto = false) }
                    .graphicsLayer {
                        alpha = 1f - 0.35f * push.value
                        scaleX = 1f - 0.14f * push.value
                        scaleY = scaleX
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The toggle shows the view that is on: lines for the stack, squares for the grid.
                fun squares(): Float {
                    val m = smooth(clock.value, GLYPH_FROM, GLYPH_TO)
                    return if (grid) m else 1f - m
                }
                Glyph(Modifier.graphicsLayer { alpha = 1f - squares(); scaleX = 0.7f + 0.3f * alpha; scaleY = scaleX }) {
                    drawPath(Lines, Color.White, style = it(1.8f))
                }
                Glyph(Modifier.graphicsLayer { alpha = squares(); scaleX = 0.7f + 0.3f * alpha; scaleY = scaleX }) {
                    drawPath(Squares, Color.White, style = it(1.6f))
                }
            }
        }

        Row(
            Modifier
                .offset(((frame.width - DOCK_WIDTH) / 2f).dp, (frame.height - frame.bottom - DOCK_HEIGHT).dp)
                .size(DOCK_WIDTH.dp, DOCK_HEIGHT.dp)
                .background(DockFill, CircleShape)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph {
                drawPath(DockSleeves, DockInk, style = it(1.8f))
                drawPath(DockSleeve, DockInk, style = Fill)
            }
            Glyph {
                drawCircle(DockMute, 8.2f, Offset(12f, 12f), style = it(1.6f))
                drawCircle(DockMute, 1f, Offset(9.2f, 10f))
                drawCircle(DockMute, 1f, Offset(14.8f, 10f))
                drawPath(DockSmile, DockMute, style = it(1.6f))
            }
            Glyph {
                drawPath(DockList, DockMute, style = it(1.6f))
                drawCircle(DockMute, 1.9f, Offset(15.8f, 16.8f), style = it(1.6f))
            }
        }
    }
}

/** A glyph in a box of 24 dp; [draw] works in those 24 units and is given a round stroke of a width in dp. */
@Composable
private fun Glyph(modifier: Modifier = Modifier, draw: DrawScope.(stroke: (Float) -> Stroke) -> Unit) {
    Canvas(modifier.size(24.dp)) {
        val k = size.width / 24f
        val stroke = { width: Float -> Stroke(width * density / k, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        scale(k, k, pivot = Offset.Zero) { draw(stroke) }
    }
}

/** A cover made in code: a gradient and one figure, in a box of 100 units. */
@Composable
private fun CoverArt(item: Sleeve, shaded: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
            val box = Size(100f, 100f)
            drawRect(Brush.verticalGradient(item.colors, 0f, 100f), size = box)
            val tint = item.tint
            when (item.figure) {
                Figure.Chevron -> drawPath(ChevronPath, tint, style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Figure.Disc -> drawCircle(tint, 22f, Offset(50f, 50f))
                Figure.Rings -> listOf(14f, 24f, 34f).forEach { drawCircle(tint, it, Offset(50f, 50f), style = Stroke(1.5f)) }
                Figure.Stripes -> listOf(26f, 38f, 50f, 62f, 74f).forEach { drawRect(tint, Offset(22f, it - 2.5f), Size(56f, 5f)) }
                Figure.Square -> drawRect(tint, Offset(34f, 34f), Size(32f, 32f))
                Figure.Dots -> listOf(26f, 42f, 58f, 74f).forEach { y -> listOf(26f, 42f, 58f, 74f).forEach { x -> drawCircle(tint, 3f, Offset(x, y)) } }
                Figure.Wave -> drawPath(WavePath, tint, style = Stroke(3f, cap = StrokeCap.Round))
                Figure.Triangle -> drawPath(TrianglePath, tint)
            }
            // The far half of a sleeve lying flat is in shade.
            if (shaded) drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SHADE), Color.Transparent), 0f, 100f), size = box)
        }
    }
}

/** The sleeves lying flat, drawn from the scroll position of a column of clear rows that take the taps. */
@Composable
private fun Stack(
    items: List<Sleeve>,
    shown: Int,
    frame: Frame,
    grid: Boolean,
    still: Boolean,
    clock: Animatable<Float, AnimationVector1D>,
    scroll: ScrollState,
    pressed: Int,
    press: Animatable<Float, AnimationVector1D>,
    onLift: (Int, Boolean) -> Unit,
    onSelect: (Sleeve) -> Unit,
) {
    val size = frame.sleeve
    val lift by rememberUpdatedState(onLift)
    val select by rememberUpdatedState(onSelect)
    Box(
        // The view shown is the upper one: a layer above would keep the touches from this one, even unseen and disabled.
        Modifier.zIndex(if (grid) 0f else 1f).fillMaxSize().graphicsLayer {
            renderEffect = if (still) null else blurOf((ARRIVE_BLUR * viewBlur(clock.value, leaving = grid)).dp.toPx())
        },
    ) {
        items.forEachIndexed { index, item ->
            key(shown, index) {
                CoverArt(
                    item,
                    shaded = true,
                    Modifier
                        .offset(x = STACK_SIDE.dp)
                        .size(size.dp)
                        .graphicsLayer {
                            val pose = sleevePose(index, clock.value, grid, scroll.value / density, frame, still)
                            val turn = tilt(pose.y - frame.eye, size)
                            val forward = if (pressed == index) 1f + PRESS_SCALE * press.value else 1f
                            alpha = pose.opacity
                            // Turned about its front edge, which is the box's bottom edge.
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (pose.y - size).dp.toPx()
                            rotationX = turn.degrees
                            cameraDistance = turn.camera * density / 72f
                            scaleX = pose.scale * forward
                            scaleY = scaleX
                        },
                )
                Box(
                    Modifier
                        .offset(x = STACK_SIDE.dp)
                        .size(size.dp, STRIP.dp)
                        .graphicsLayer {
                            val pose = sleevePose(index, clock.value, grid, scroll.value / density, frame, still)
                            val forward = if (pressed == index) 1f + PRESS_SCALE * press.value else 1f
                            alpha = pose.opacity
                            translationY = (pose.y - STRIP / 2f).dp.toPx()
                            scaleX = pose.scale * forward
                            scaleY = scaleX
                        }
                        .background(item.edge),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(item.title) }
                            append(" ${item.artist}")
                        },
                        style = StripStyle.copy(color = if (item.light) Color.Black else Color.White),
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = sleevePose(index, clock.value, grid, scroll.value / density, frame, still).label },
                    )
                }
            }
        }
        // The rows are there only while the stack is the view shown.
        if (!grid) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Spacer(Modifier.height((frame.stackTop - PITCH / 2f).dp))
                items.forEachIndexed { index, item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(PITCH.dp)
                            .semantics { contentDescription = "${item.title}, ${item.artist}" }
                            .pointerInput(shown, index) {
                                detectTapGestures(
                                    onPress = {
                                        lift(index, true)
                                        tryAwaitRelease()
                                        lift(index, false)
                                    },
                                    onTap = { select(item) },
                                )
                            },
                    )
                }
                Spacer(Modifier.height((frame.height - frame.eye - PITCH / 2f).dp))
            }
        }
    }
}

/** The flat grid of covers. */
@Composable
private fun Grid(
    items: List<Sleeve>,
    shown: Int,
    frame: Frame,
    grid: Boolean,
    still: Boolean,
    clock: Animatable<Float, AnimationVector1D>,
    scroll: ScrollState,
    onSelect: (Sleeve) -> Unit,
) {
    val cell = frame.cell
    val rows = (items.size + COLUMNS - 1) / COLUMNS
    Box(
        Modifier
            .zIndex(if (grid) 1f else 0f)
            .fillMaxSize()
            .graphicsLayer { renderEffect = if (still) null else blurOf((ARRIVE_BLUR * viewBlur(clock.value, leaving = !grid)).dp.toPx()) }
            .verticalScroll(scroll, enabled = grid),
    ) {
        Box(Modifier.fillMaxWidth().height((frame.top + GRID_TOP + rows * (cell + GRID_GAP) + frame.bottom + DOCK_HEIGHT + 12f).dp)) {
            items.forEachIndexed { index, item ->
                key(shown, index) {
                    val source = remember { MutableInteractionSource() }
                    val down by source.collectIsPressedAsState()
                    val sink by animateFloatAsState(if (down) 0.96f else 1f, tween(PRESS_MS), label = "sink")
                    Box(
                        Modifier
                            .offset((GRID_SIDE + (index % COLUMNS) * (cell + GRID_GAP)).dp, (frame.top + GRID_TOP + (index / COLUMNS) * (cell + GRID_GAP)).dp)
                            .size(cell.dp)
                            .graphicsLayer {
                                val pose = coverPose(index, items.size, clock.value, grid, still)
                                alpha = pose.opacity
                                translationX = pose.shift.dp.toPx()
                                translationY = pose.shift.dp.toPx()
                                rotationX = pose.tilt
                                cameraDistance = GRID_CAMERA * density / 72f
                            }
                            .semantics { contentDescription = "${item.title}, ${item.artist}" }
                            // The covers are there while the stack is shown, unseen: they take no touches then.
                            .clickable(source, indication = null, enabled = grid, role = Role.Button) { onSelect(item) },
                    ) {
                        CoverArt(item, shaded = false, Modifier.fillMaxSize().graphicsLayer { scaleX = sink; scaleY = sink })
                    }
                }
            }
        }
    }
}
