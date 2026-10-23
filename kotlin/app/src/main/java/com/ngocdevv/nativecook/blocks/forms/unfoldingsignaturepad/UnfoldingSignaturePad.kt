package com.ngocdevv.nativecook.blocks.forms.unfoldingsignaturepad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP = 10.4f
private const val PRESS_FOR = .12f
private const val START_AT = .8f
private const val SIGN_AT = 1.75f
private const val SIGN_FOR = 2.6f
private const val SIGN_LIFT = .12f
private const val FINISH_AT = 4.75f
private const val EDIT_AT = 6.4f
private const val CLEAR_AT = 7.5f
private const val FOLD_AT = 8.3f
private const val USER_HOLD = 3f
private const val RESET_FOR = .9f
private const val CARD_WIDTH = 300f
private const val CARD_HEIGHT = 372f
private const val CARD_RADIUS = 36f
private const val STAGE_MARGIN = 28f
private const val STAGE_WIDTH = CARD_WIDTH + STAGE_MARGIN * 2f
private const val STAGE_HEIGHT = CARD_HEIGHT + STAGE_MARGIN * 2f
private val DASH = floatArrayOf(9f, 7f)
private const val OUTLINE = 2.2f
private const val BUTTON = 64f
private const val START_WIDTH = 206f
private const val DONE_WIDTH = 214f
private const val INSET = 10f
private const val OPEN_WIDTH = CARD_WIDTH - INSET * 2f
private const val DROP = CARD_HEIGHT / 2f - INSET - BUTTON / 2f
private const val EDIT = 64f
private const val EDIT_GAP = 12f
private const val HEADER_Y = -CARD_HEIGHT / 2f + 44f
private const val HEADER_INSET = 37f
private const val HEADER_ICON = 24f
private const val HIT = 48f
private const val PAD_TOP = -CARD_HEIGHT / 2f + 74f
private const val PAD_BOTTOM = DROP - BUTTON / 2f - 8f
private const val PAD_INSET = 8f
private const val LABEL = 18f
private const val LABEL_WEIGHT = 500
private const val LABEL_ICON = 28f
private const val LABEL_DISC = 24f
private const val LABEL_GAP = 8f
private const val WORD_GAP = 5f
private const val LETTER_BLUR = 5f
private const val LETTER_DROP = 3f
private const val LETTER_STAGGER = .4f
private const val PRESS_SCALE = .06f
private const val INK_FOLDED = .25f
private const val INK_STEP = 1.5f
private const val SIGNATURE_STEP = 3f
const val SIGNATURE_WIDTH = 200f
const val SIGNATURE_HEIGHT = 96f
private const val SIGNATURE_Y = -12f
private data class Spring(val mass: Float, val stiffness: Float, val damping: Float)
private val UNFOLD = Spring(1f, 120f, 14f)
private const val WORDING = 320
private const val DARKEN = 280
private const val INK_OUT = 260
private const val PRESS_IN = 70
private const val PRESS_OUT = 180
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private val LIGHT = intArrayOf(244, 244, 248)
private val DARK = intArrayOf(35, 35, 38)
private val TEXT = intArrayOf(42, 42, 46)
private val WHITE = intArrayOf(255, 255, 255)
private val LIGHT_COLOR = Color(0xFFF4F4F8)
private val DARK_COLOR = Color(0xFF232326)
private val OUTLINE_COLOR = Color(0xFFDADADF)
private val HEADER_COLOR = Color(0xFFB5B5BA)
private val TITLE = intArrayOf(154, 154, 160)
private val POINTER = Color(0xFF111113)
private const val SHADOW_SIGMA = 10f
private const val SHADOW_ALPHA = .07f
private const val LETTER_SPILL = LETTER_BLUR * 3f
private const val PEN_REST = .4f
private const val PEN_FADE_AFTER = .1f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private const val SPRING_FRAME_CAP = .064f
private val QuadInOut = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val Linear = Easing { it }
private const val ICON_CLEAR = "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
private const val ICON_PENCIL = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
private const val ICON_CROSS = "M8.4 8.4L15.6 15.6M15.6 8.4L8.4 15.6"
private const val ICON_CHECK = "M7.2 12.4L10.6 15.6L16.8 8.8"
private const val ICON_SCRIBBLE = "M2.5 21.5C3.5 14.5 6.5 9 9 9.6C11.6 10.3 7.6 20.6 10.4 21.4C12.4 22 13.4 19.4 14.6 17.2"
private val SIGNATURE = listOf(
    "M5 86C7 60 11 32 17 10C24 34 33 62 42 82",
    "M1 60C15 56 31 52 46 49",
    "M56 60C68 50 82 28 84 14C85 5 78 4 75 14C69 32 66 54 72 63C77 69 86 57 93 47C99 38 97 32 92 36C85 42 86 57 93 61C100 64 109 54 115 45C121 36 131 27 143 24C159 21 179 24 198 26",
    "M114 22C120 33 129 48 138 59",
    "M112 88C141 80 171 69 195 58C200 55 201 51 197 51",
)
private val ClearPath = PathParser().parsePathString(ICON_CLEAR).toPath()
private val PencilPath = PathParser().parsePathString(ICON_PENCIL).toPath()
private val CrossPath = PathParser().parsePathString(ICON_CROSS).toPath()
private val CheckPath = PathParser().parsePathString(ICON_CHECK).toPath()
private val ScribblePath = PathParser().parsePathString(ICON_SCRIBBLE).toPath()
private enum class Mode { Idle, Open, Done }
private fun smooth(t: Float, from: Float, to: Float): Float { val u = ((t - from) / (to - from)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
private fun mix(a: IntArray, b: IntArray, t: Float): Color { val u = t.coerceIn(0f, 1f); return rgba((a[0] + (b[0] - a[0]) * u).roundToInt(), (a[1] + (b[1] - a[1]) * u).roundToInt(), (a[2] + (b[2] - a[2]) * u).roundToInt(), 1f) }
private fun color(value: String): Color = UnfoldingSignaturePadCSS.color(value)
private fun buttonX(open: Float, dark: Float) = -(EDIT_GAP + EDIT) / 2f * dark * (1f - open)
private fun buttonWidth(open: Float, dark: Float): Float { val closed = START_WIDTH + (DONE_WIDTH - START_WIDTH) * dark; return closed + (OPEN_WIDTH - closed) * open }
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private val LabelStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = LABEL.sp, fontWeight = FontWeight(LABEL_WEIGHT), platformStyle = PlatformTextStyle(includeFontPadding = false))
private data class Sample(val time: Float, val x: Float, val y: Float, val down: Boolean)
private fun script(strokes: List<String>): List<Sample> {
    val contours = strokes.mapNotNull { svg -> runCatching { android.graphics.PathMeasure(PathParser().parsePathString(svg).toPath().asAndroidPath(), false) }.getOrNull()?.takeIf { it.length > 0f } }
    val length = contours.sumOf { it.length.toDouble() }.toFloat()
    val speed = length / (SIGN_FOR - SIGN_LIFT * (contours.size - 1))
    val out = mutableListOf<Sample>()
    var time = 0f
    val point = FloatArray(2)
    for (contour in contours) {
        val steps = max(1, ceil(contour.length / SIGNATURE_STEP).toInt())
        for (i in 0..steps) {
            val distance = contour.length * i / steps
            contour.getPosTan(distance, point, null)
            out += Sample(time + distance / speed, point[0] - SIGNATURE_WIDTH / 2f, point[1] - SIGNATURE_HEIGHT / 2f + SIGNATURE_Y, i == 0)
        }
        time += contour.length / speed + SIGN_LIFT
    }
    return out
}
private fun inkPath(strokes: List<List<Float>>): Path = Path().apply {
    for (p in strokes) {
        if (p.size < 2) continue
        moveTo(p[0], p[1])
        if (p.size == 2) lineTo(p[0] + .01f, p[1])
        var i = 2
        while (i < p.size - 2) { quadraticTo(p[i], p[i + 1], (p[i] + p[i + 2]) / 2f, (p[i + 1] + p[i + 3]) / 2f); i += 2 }
        if (p.size > 2) lineTo(p[p.size - 2], p[p.size - 1])
    }
}

/** The pen alone ignores duration scale; the other clocks mirror System reduction. */
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var target = initial
    private var velocity = 0f
    private var config = Spring(1f, 1f, 1f)
    private var energy0 = 0f
    private var springing = false
    private var springTimestamp = 0L
    private var timing = false
    private var origin = 0L
    private var duration = 0f
    private var from = initial
    private var curve: Easing = QuadInOut
    private var completion: (() -> Unit)? = null
    fun direct(next: Float) { completion = null; timing = false; springing = false; velocity = 0f; state.floatValue = next; target = next }
    fun spring(next: Float, spring: Spring, still: Boolean, startingAt: Long = System.nanoTime()) {
        if (still || (value == next)) { direct(next); return }
        if (springing && target == next && config == spring) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        if (!springing) springTimestamp = startingAt
        energy0 = .5f * spring.stiffness * (value - next).pow(2)
        target = next; config = spring; springing = true; timing = false; completion = null
    }
    fun tween(next: Float, ms: Int, ease: Easing = QuadInOut, still: Boolean = false, startingAt: Long = System.nanoTime(), onFinished: (() -> Unit)? = null) {
        if (still || (value == next)) { direct(next); onFinished?.invoke(); return }
        if (timing && target == next) { duration = ms * 1e6f; curve = ease; completion = onFinished; return }
        from = value; target = next; origin = startingAt; duration = ms * 1e6f; curve = ease; timing = true; springing = false; velocity = 0f; completion = onFinished
    }
    fun advance(now: Long) {
        if (timing) { val t = ((now - origin) / duration).coerceIn(0f, 1f); state.floatValue = from + (target - from) * curve.transform(t); if (t >= 1f) { timing = false; val finished = completion; completion = null; finished?.invoke() } }
        if (!springing) return
        val dt = min(SPRING_FRAME_CAP, ((now - springTimestamp) / 1e9f).coerceAtLeast(0f)); springTimestamp = now
        val omega = sqrt(config.stiffness / config.mass); val decay = config.damping / (2f * config.mass); val e = value - target
        val next: Float
        if (decay < omega) { val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt); next = envelope * (e * c + (velocity + decay * e) / w * s); velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s) }
        else { val envelope = exp(-omega * dt); val b = velocity + omega * e; next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt) }
        state.floatValue = target + next
        val energy = .5f * config.stiffness * next * next + .5f * config.mass * velocity * velocity
        if (energy0 == 0f || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { state.floatValue = target; velocity = 0f; springing = false }
    }
}


private data class Letter(val text: String, val x: Float)
private data class Word(val width: Float, val letters: List<Letter>)
private fun word(text: String, measured: TextLayoutResult, density: Float): Word {
    val width = (0 until measured.lineCount).maxOfOrNull { measured.getLineRight(it) - measured.getLineLeft(it) } ?: 0f
    return Word(width / density, text.indices.map { Letter(text.substring(it, it + 1), measured.getBoundingBox(it).left / density) })
}
private data class Label(val shared: String, val sharedWidth: Float, val height: Float, val start: Word, val finish: Word, val done: Word) {
    private fun left(icon: Float, first: Float, second: Float) = -(icon + LABEL_GAP + first + WORD_GAP + second) / 2f
    val startLeft = left(LABEL_ICON, start.width, sharedWidth)
    val finishLeft = left(LABEL_ICON, finish.width, sharedWidth)
    val doneLeft = left(LABEL_DISC, sharedWidth, done.width)
}
private class FrameTime { var now: Long? = null }

/** A dashed signing card that unfolds from, and folds back into, its label. */
@Composable
fun UnfoldingSignaturePad(
    autoPlay: Boolean = false,
    sharedWord: String = "Signing",
    startWord: String = "Start",
    finishWord: String = "Finish",
    doneWord: String = "Done",
    title: String = "Sign",
    inkColor: String = "#111113",
    inkWidth: Float = 3f,
    background: String = "#ffffff",
    signature: List<String> = SIGNATURE,
    onStart: (() -> Unit)? = null,
    onSign: ((List<List<Double>>) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val startCallback by rememberUpdatedState(onStart)
    val signCallback by rememberUpdatedState(onSign)
    val clearCallback by rememberUpdatedState(onClear)
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = LabelStyle.copy(fontSize = (LABEL / displayDensity.fontScale).sp)
    val samples = remember(signature) { script(signature) }
    val currentSamples by rememberUpdatedState(samples)
    val label = remember(sharedWord, startWord, finishWord, doneWord, displayDensity) {
        fun measure(text: String) = textMeasurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = (STAGE_WIDTH * displayDensity.density).roundToInt()))
        val shared = measure(sharedWord)
        val sharedWidth = (0 until shared.lineCount).maxOfOrNull { shared.getLineRight(it) - shared.getLineLeft(it) } ?: 0f
        Label(sharedWord, sharedWidth / displayDensity.density, shared.size.height / displayDensity.density, word(startWord, measure(startWord), displayDensity.density), word(finishWord, measure(finishWord), displayDensity.density), word(doneWord, measure(doneWord), displayDensity.density))
    }
    val heading = remember(title, displayDensity) { textMeasurer.measure(AnnotatedString(title), style, constraints = Constraints(maxWidth = (STAGE_WIDTH * displayDensity.density).roundToInt())) }
    val headingWidth = ((0 until heading.lineCount).maxOfOrNull { heading.getLineRight(it) - heading.getLineLeft(it) } ?: 0f) / displayDensity.density
    val headingHeight = heading.size.height / displayDensity.density
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    var mode by remember { mutableStateOf(Mode.Idle) }
    var touches by remember { mutableIntStateOf(0) }
    var ink by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    var before by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    var written by remember { mutableIntStateOf(0) }
    val frameTime = remember { FrameTime() }
    val controlScope = rememberCoroutineScope()
    val open = remember { Motion(0f) }
    val wording = remember { Motion(0f) }
    val dark = remember { Motion(0f) }
    val inkShown = remember { Motion(1f) }
    val buttonPress = remember { Motion(0f) }
    val editPress = remember { Motion(0f) }
    val clearPress = remember { Motion(0f) }
    val closePress = remember { Motion(0f) }
    val pen = remember { Motion(-1f) }
    val shadow = remember { SoftRoundRect(rgba(40, 40, 70, SHADOW_ALPHA), SHADOW_SIGMA) }
    fun timestamp() = frameTime.now ?: System.nanoTime()
    fun hold(press: Motion, down: Boolean) { press.tween(if (down) 1f else 0f, if (down) PRESS_IN else PRESS_OUT, still = reduced, startingAt = timestamp()) }
    fun unfold() {
        before = ink.map { it.toList() }
        mode = Mode.Open; open.spring(1f, UNFOLD, reduced, timestamp())
        wording.tween(1f, WORDING, still = reduced, startingAt = timestamp())
        dark.tween(0f, DARKEN, still = reduced, startingAt = timestamp())
        startCallback?.invoke()
    }
    fun fold(strokes: List<List<Float>>): Boolean {
        val signed = strokes.isNotEmpty()
        mode = if (signed) Mode.Done else Mode.Idle
        open.spring(0f, UNFOLD, reduced, timestamp())
        if (signed) dark.tween(1f, DARKEN, still = reduced, startingAt = timestamp())
        else wording.tween(0f, WORDING, still = reduced, startingAt = timestamp())
        return signed
    }
    fun finish(byItself: Boolean) {
        val strokes = ink.map { it.toList() }
        if (!fold(strokes)) { clearCallback?.invoke(); return }
        if (!byItself) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        signCallback?.invoke(strokes.map { stroke -> stroke.map { it.toDouble() } })
    }
    fun erase() {
        inkShown.tween(0f, INK_OUT, still = reduced, startingAt = timestamp()) { ink = emptyList(); inkShown.direct(1f) }
    }
    fun wipe() { erase(); clearCallback?.invoke() }
    fun close() { ink = before.map { it.toList() }; inkShown.direct(1f); fold(ink) }
    fun reset() {
        pen.direct(-1f); mode = Mode.Idle; open.spring(0f, UNFOLD, reduced, timestamp())
        wording.tween(0f, WORDING, still = reduced, startingAt = timestamp())
        dark.tween(0f, DARKEN, still = reduced, startingAt = timestamp())
        erase()
    }
    fun sign() { written = 0; pen.direct(0f); pen.tween(SIGN_FOR + PEN_REST, ((SIGN_FOR + PEN_REST) * 1000).roundToInt(), Linear, startingAt = timestamp()) }
    fun touched() { pen.direct(-1f); listOf(buttonPress, editPress, clearPress, closePress).forEach { hold(it, false) }; touches++ }
    fun penDown(x: Float, y: Float) { ink = ink + listOf(listOf(x, y)) }
    fun penMove(x: Float, y: Float) {
        val last = ink.lastOrNull() ?: return
        if (hypot(x - last[last.size - 2], y - last[last.size - 1]) < INK_STEP) return
        ink = ink.dropLast(1) + listOf(last + listOf(x, y))
    }
    val latestUnfold by rememberUpdatedState(::unfold)
    val latestFinish by rememberUpdatedState(::finish)
    val latestWipe by rememberUpdatedState(::wipe)
    val latestReset by rememberUpdatedState(::reset)
    val latestSign by rememberUpdatedState(::sign)
    val latestHold by rememberUpdatedState(::hold)
    val latestTouched by rememberUpdatedState(::touched)
    val latestPenDown by rememberUpdatedState(::penDown)
    val latestPenMove by rememberUpdatedState(::penMove)
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) { delay((USER_HOLD * 1000).toLong()); latestReset(); delay((RESET_FOR * 1000).toLong()) }
        var origin = System.nanoTime()
        while (true) {
            val events = listOf<Pair<Float, () -> Unit>>(
                START_AT to { latestHold(buttonPress, true) },
                START_AT + PRESS_FOR to { latestHold(buttonPress, false); latestUnfold() },
                SIGN_AT to { latestSign() },
                FINISH_AT to { latestHold(buttonPress, true) },
                FINISH_AT + PRESS_FOR to { latestHold(buttonPress, false); latestFinish(true) },
                EDIT_AT to { latestHold(editPress, true) },
                EDIT_AT + PRESS_FOR to { latestHold(editPress, false); latestUnfold() },
                CLEAR_AT to { latestHold(clearPress, true) },
                CLEAR_AT + PRESS_FOR to { latestHold(clearPress, false); latestWipe() },
                FOLD_AT to { latestHold(buttonPress, true) },
                FOLD_AT + PRESS_FOR to { latestHold(buttonPress, false); latestFinish(true) },
            )
            for ((time, action) in events) { delay(max(0L, (origin + (time * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); action() }
            delay(max(0L, (origin + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9f).toLong()
        }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            frameTime.now = now
            try {
                listOf(open, wording, dark, inkShown, buttonPress, editPress, clearPress, closePress, pen).forEach { it.advance(now) }
                if (pen.value >= 0f) {
                    val data = currentSamples
                    var next = written
                    while (next < data.size && data[next].time <= pen.value) {
                        val sample = data[next]
                        if (sample.down) latestPenDown(sample.x, sample.y) else latestPenMove(sample.x, sample.y)
                        next++
                    }
                    if (next != written) written = next
                }
            } finally { frameTime.now = null }
        }
    }
    val strokes = remember(ink) { inkPath(ink) }
    Box(modifier.fillMaxSize().background(color(background)).padding(top = safe.calculateTopPadding(), bottom = safe.calculateBottomPadding()), contentAlignment = Alignment.Center) {
        Box(Modifier.wrapContentSize(Alignment.Center, unbounded = true).size(STAGE_WIDTH.dp, STAGE_HEIGHT.dp).clipToBounds()) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, _, _ ->
                    withTransform({ translate(STAGE_WIDTH / 2f, STAGE_HEIGHT / 2f) }) {
                        val o = open.value; val k = dark.value; val x = buttonX(o, k)
                        val w = buttonWidth(o, k) + INSET * 2f * o
                        val top = -BUTTON / 2f + (-CARD_HEIGHT / 2f + BUTTON / 2f) * o
                        val bottom = DROP * o + BUTTON / 2f + INSET * o
                        val r = BUTTON / 2f + (CARD_RADIUS - BUTTON / 2f) * o.coerceIn(0f, 1f)
                        drawRoundRect(OUTLINE_COLOR, Offset(x - w / 2f, top), Size(w, max(BUTTON, bottom - top)), CornerRadius(r), alpha = smooth(o, .12f, .6f), style = Stroke(OUTLINE, pathEffect = PathEffect.dashPathEffect(DASH)))
                        val header = smooth(o, .45f, .95f)
                        withTransform({ translate(-CARD_WIDTH / 2f + HEADER_INSET, HEADER_Y); scale(1f - .18f * clearPress.value, 1f - .18f * clearPress.value, Offset.Zero); rotate(clearPress.value * .5f * 180f / PI.toFloat(), Offset.Zero); translate(-13f, -13f); scale(26f / 24f, 26f / 24f, Offset.Zero) }) { drawPath(ClearPath, HEADER_COLOR, alpha = header) }
                        withTransform({ translate(CARD_WIDTH / 2f - HEADER_INSET, HEADER_Y); scale(1f - .18f * closePress.value, 1f - .18f * closePress.value, Offset.Zero) }) {
                            drawCircle(HEADER_COLOR, HEADER_ICON / 2f, Offset.Zero, alpha = header)
                            withTransform({ translate(-12f, -12f) }) { drawPath(CrossPath, Color.White, alpha = header, style = Stroke(2.6f, cap = StrokeCap.Round)) }
                        }
                    }
                }
            }
            BasicText(title, Modifier.offset(x = (STAGE_WIDTH / 2f - headingWidth / 2f).dp, y = (STAGE_HEIGHT / 2f + HEADER_Y - headingHeight / 2f).dp).width((headingWidth + 2f).dp).height(headingHeight.dp).graphicsLayer { alpha = smooth(open.value, .45f, .95f); compositingStrategy = CompositingStrategy.ModulateAlpha }.clearAndSetSemantics {}, style = style.copy(color = rgba(TITLE[0], TITLE[1], TITLE[2], 1f)))
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, _, _ ->
                    withTransform({ translate(STAGE_WIDTH / 2f, STAGE_HEIGHT / 2f) }) {
                        val o = open.value; val k = dark.value; val x = buttonX(o, k); val width = buttonWidth(o, k)
                        val inkAlpha = inkShown.value * smooth(o, .1f, .7f)
                        val inkScale = INK_FOLDED + (1f - INK_FOLDED) * o
                        withTransform({ translate(x, DROP * o); scale(inkScale, inkScale, Offset.Zero); translate(0f, -DROP) }) {
                            drawPath(strokes, color(inkColor), alpha = inkAlpha, style = Stroke(inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            if (samples.isNotEmpty()) {
                                val next = min(written, samples.lastIndex); val last = max(0, next - 1)
                                val span = samples[next].time - samples[last].time
                                val u = if (span > 0f) ((pen.value - samples[last].time) / span).coerceIn(0f, 1f) else 1f
                                val px = samples[last].x + (samples[next].x - samples[last].x) * u
                                val py = samples[last].y + (samples[next].y - samples[last].y) * u
                                withTransform({ translate(px - 3f, py - 21f) }) { drawPath(PencilPath, POINTER, alpha = inkAlpha * smooth(pen.value, 0f, .15f) * (1f - smooth(pen.value, SIGN_FOR + PEN_FADE_AFTER, SIGN_FOR + PEN_REST))) }
                            }
                        }
                        val out = (k * (1f - o)).coerceIn(0f, 1f)
                        val end = x + width / 2f
                        val editAlpha = smooth(k * (1f - o), .15f, .7f)
                        withTransform({ translate(end - EDIT / 2f - 4f + (EDIT + EDIT_GAP + 4f) * out, DROP * o); scale(1f - PRESS_SCALE * 1.5f * editPress.value, 1f - PRESS_SCALE * 1.5f * editPress.value, Offset.Zero) }) {
                            drawCircle(LIGHT_COLOR, EDIT / 2f, Offset.Zero, alpha = editAlpha)
                            withTransform({ translate(-12f, -12f) }) { drawPath(PencilPath, DARK_COLOR, alpha = editAlpha) }
                        }
                        val sw = width - 16f
                        if (Build.VERSION.SDK_INT >= 28) drawIntoCanvas { shadow.draw(it.nativeCanvas, x - sw / 2f, DROP * o - BUTTON / 2f + 10f, sw, BUTTON - 8f, BUTTON / 2f) }
                        else for (i in 6 downTo 1) {
                            val spread = SHADOW_SIGMA * i / 3f
                            drawRoundRect(rgba(40, 40, 70, SHADOW_ALPHA * exp(-.5f * (i / 2f).pow(2)) / 3f), Offset(x - sw / 2f - spread, DROP * o - BUTTON / 2f + 10f - spread), Size(sw + spread * 2f, BUTTON - 8f + spread * 2f), CornerRadius(BUTTON / 2f + spread))
                        }
                        val pressScale = 1f - PRESS_SCALE * buttonPress.value
                        val bw = width * pressScale; val bh = BUTTON * pressScale
                        drawRoundRect(mix(LIGHT, DARK, k), Offset(x - bw / 2f, DROP * o - bh / 2f), Size(bw, bh), CornerRadius(bh / 2f))
                    }
                }
            }
            LabelView(label, style, open, wording, dark, buttonPress)
            val middleX = STAGE_WIDTH / 2f; val middleY = STAGE_HEIGHT / 2f
            if (mode == Mode.Open) {
                Box(Modifier.offset(x = (STAGE_MARGIN + PAD_INSET).dp, y = (middleY + PAD_TOP).dp).size((CARD_WIDTH - PAD_INSET * 2f).dp, (PAD_BOTTOM - PAD_TOP).dp).semantics { contentDescription = "$title: draw your signature here" }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        latestTouched(); latestPenDown(down.position.x / displayDensity.density - CARD_WIDTH / 2f + PAD_INSET, down.position.y / displayDensity.density + PAD_TOP)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!finger.pressed || finger.isConsumed) break
                            latestPenMove(finger.position.x / displayDensity.density - CARD_WIDTH / 2f + PAD_INSET, finger.position.y / displayDensity.density + PAD_TOP); finger.consume()
                        }
                    }
                })
                Control("Clear", clearPress, Modifier.offset(x = (STAGE_MARGIN + HEADER_INSET - HIT / 2f).dp, y = (middleY + HEADER_Y - HIT / 2f).dp).size(HIT.dp), controlScope, ::touched, ::hold, ::wipe)
                Control("Close", closePress, Modifier.offset(x = (STAGE_MARGIN + CARD_WIDTH - HEADER_INSET - HIT / 2f).dp, y = (middleY + HEADER_Y - HIT / 2f).dp).size(HIT.dp), controlScope, ::touched, ::hold, ::close)
                Control("$finishWord $sharedWord", buttonPress, Modifier.offset(x = (middleX - OPEN_WIDTH / 2f).dp, y = (middleY + DROP - BUTTON / 2f).dp).size(OPEN_WIDTH.dp, BUTTON.dp), controlScope, ::touched, ::hold) { finish(false) }
            } else if (mode == Mode.Idle) {
                Control("$startWord $sharedWord", buttonPress, Modifier.offset(x = (middleX - START_WIDTH / 2f).dp, y = (middleY - BUTTON / 2f).dp).size(START_WIDTH.dp, BUTTON.dp), controlScope, ::touched, ::hold, ::unfold)
            } else {
                val doneLeft = middleX - (DONE_WIDTH + EDIT_GAP + EDIT) / 2f
                Box(Modifier.offset(x = doneLeft.dp, y = (middleY - BUTTON / 2f).dp).size(DONE_WIDTH.dp, BUTTON.dp).semantics { contentDescription = "$sharedWord $doneWord" })
                Control("Edit signature", editPress, Modifier.offset(x = (doneLeft + DONE_WIDTH + EDIT_GAP).dp, y = (middleY - EDIT / 2f).dp).size(EDIT.dp), controlScope, ::touched, ::hold, ::unfold)
            }
        }
    }
}

@Composable
private fun LabelView(label: Label, style: TextStyle, open: Motion, wording: Motion, dark: Motion, press: Motion) {
    val middleX = STAGE_WIDTH / 2f; val middleY = STAGE_HEIGHT / 2f
    val w = wording.value; val k = dark.value
    val tint = mix(TEXT, WHITE, smooth(k, .1f, .6f))
    val iconX = label.startLeft + (label.finishLeft - label.startLeft) * w + (label.doneLeft - label.finishLeft) * k
    val firstX = iconX + LABEL_ICON + LABEL_GAP
    val firstWidth = label.start.width + (label.finish.width - label.start.width) * w
    val afterFirst = iconX + LABEL_ICON + LABEL_GAP + firstWidth + WORD_GAP
    val afterDisc = iconX + LABEL_DISC + LABEL_GAP
    val sharedX = afterFirst + (afterDisc - afterFirst) * smooth(k, .15f, 1f)
    Box(Modifier.fillMaxSize().graphicsLayer {
        translationX = buttonX(open.value, dark.value) * density; translationY = DROP * open.value * density
        scaleX = 1f - PRESS_SCALE * press.value; scaleY = scaleX; compositingStrategy = CompositingStrategy.ModulateAlpha
    }.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                withTransform({ translate(middleX + iconX, middleY - LABEL_ICON / 2f) }) {
                    val alpha = 1f - smooth(k, 0f, .5f)
                    drawPath(ScribblePath, tint, alpha = alpha, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    withTransform({ translate(11.5f, 1.5f); scale(.68f, .68f, Offset.Zero) }) { drawPath(PencilPath, tint, alpha = alpha) }
                }
            }
        }
        for ((index, letter) in label.start.letters.withIndex()) {
            val span = 1f + LETTER_STAGGER * (label.start.letters.size - 1)
            val shown = 1f - (w * span - LETTER_STAGGER * index).coerceIn(0f, 1f)
            LetterView(letter, shown, middleX + firstX, middleY - label.height / 2f, label.height, style.copy(color = tint))
        }
        for ((index, letter) in label.finish.letters.withIndex()) {
            val span = 1f + LETTER_STAGGER * (label.finish.letters.size - 1)
            val finishing = (w * span - LETTER_STAGGER * index).coerceIn(0f, 1f)
            val done = (k * span - LETTER_STAGGER * index).coerceIn(0f, 1f)
            LetterView(letter, finishing * (1f - done), middleX + firstX, middleY - label.height / 2f, label.height, style.copy(color = tint))
        }
        BasicText(label.shared, Modifier.offset(x = (middleX + sharedX).dp, y = (middleY - label.height / 2f).dp).width((label.sharedWidth + 2f).dp).height(label.height.dp), style = style.copy(color = tint))
        for ((index, letter) in label.done.letters.withIndex()) {
            val span = 1f + LETTER_STAGGER * (label.done.letters.size - 1)
            val shown = (k * span - LETTER_STAGGER * index).coerceIn(0f, 1f)
            LetterView(letter, shown, middleX + sharedX + label.sharedWidth + WORD_GAP, middleY - label.height / 2f, label.height, style.copy(color = tint))
        }
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                val alpha = smooth(k, .3f, .9f); val scale = .5f + .5f * smooth(k, .3f, 1f)
                withTransform({ translate(middleX + iconX + LABEL_DISC / 2f, middleY); scale(scale, scale, Offset.Zero) }) {
                    drawCircle(Color.White, LABEL_DISC / 2f, Offset.Zero, alpha = alpha)
                    withTransform({ translate(-12f, -12f) }) { drawPath(CheckPath, DARK_COLOR, alpha = alpha, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
        }
    }
}

@Composable
private fun LetterView(letter: Letter, shown: Float, x: Float, y: Float, height: Float, style: TextStyle) {
    Box(Modifier.offset(x = (x + letter.x - LETTER_SPILL).dp, y = (y - LETTER_SPILL).dp).size((LABEL * 2f + LETTER_SPILL * 2f).dp, (height + LETTER_SPILL * 2f).dp).graphicsLayer {
        alpha = shown * (if (Build.VERSION.SDK_INT < 31) 1f - .28f * (1f - shown) else 1f)
        translationY = LETTER_DROP * (1f - shown) * density
        val sigma = LETTER_BLUR * (1f - shown) * density
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0f) BlurEffect(sigmaToRadius(sigma), sigmaToRadius(sigma), TileMode.Decal) else null
    }) {
        BasicText(letter.text, Modifier.offset(x = LETTER_SPILL.dp, y = LETTER_SPILL.dp).width((LABEL * 2f).dp).height(height.dp), style = style)
    }
}

private class PressState {
    var latestOut: Job? = null
    var alive = true
}

/** Fixed hit frames follow the source's mode, while their canvas pictures follow the spring. */
@Composable
private fun Control(label: String, held: Motion, modifier: Modifier, scope: CoroutineScope, touched: () -> Unit, hold: (Motion, Boolean) -> Unit, act: () -> Unit) {
    val view = LocalView.current
    val state = remember { PressState() }
    val currentTouch by rememberUpdatedState(touched)
    val currentHold by rememberUpdatedState(hold)
    val currentAct by rememberUpdatedState(act)
    DisposableEffect(state) {
        state.alive = true
        onDispose { state.alive = false; state.latestOut?.cancel() }
    }
    fun activate() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); currentAct() }
    Box(modifier.semantics { role = Role.Button; contentDescription = label; onClick { activate(); true } }.pointerInput(held) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.latestOut?.cancel(); state.latestOut = null
            var active = true
            var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); currentTouch(); currentHold(held, true) }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) currentHold(held, false)
                else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); currentHold(held, false) }
            }
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    if (!finger.pressed) {
                        if (active) { pressOut(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); activate() }
                        finger.consume(); break
                    }
                    val inside = finger.position.x > -PRESS_RETENTION * density && finger.position.x < size.width + PRESS_RETENTION * density && finger.position.y > -PRESS_RETENTION * density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density
                    if (inside != active) { active = inside; if (inside) pressIn() else pressOut() }
                    finger.consume()
                }
                if (active) { active = false; pressOut() }
            } catch (cancelled: CancellationException) {
                if (active) scope.launch {
                    // Disposal resets Pressability without dispatching another onPressOut.
                    yield()
                    if (state.alive) pressOut()
                }
                throw cancelled
            }
        }
    })
}

private object UnfoldingSignaturePadCSS {
    private val names: Map<String, Long> = mapOf(
        "transparent" to 0x00000000L,
        "aliceblue" to 0xf0f8ffffL,
        "antiquewhite" to 0xfaebd7ffL,
        "aqua" to 0x00ffffffL,
        "aquamarine" to 0x7fffd4ffL,
        "azure" to 0xf0ffffffL,
        "beige" to 0xf5f5dcffL,
        "bisque" to 0xffe4c4ffL,
        "black" to 0x000000ffL,
        "blanchedalmond" to 0xffebcdffL,
        "blue" to 0x0000ffffL,
        "blueviolet" to 0x8a2be2ffL,
        "brown" to 0xa52a2affL,
        "burlywood" to 0xdeb887ffL,
        "burntsienna" to 0xea7e5dffL,
        "cadetblue" to 0x5f9ea0ffL,
        "chartreuse" to 0x7fff00ffL,
        "chocolate" to 0xd2691effL,
        "coral" to 0xff7f50ffL,
        "cornflowerblue" to 0x6495edffL,
        "cornsilk" to 0xfff8dcffL,
        "crimson" to 0xdc143cffL,
        "cyan" to 0x00ffffffL,
        "darkblue" to 0x00008bffL,
        "darkcyan" to 0x008b8bffL,
        "darkgoldenrod" to 0xb8860bffL,
        "darkgray" to 0xa9a9a9ffL,
        "darkgreen" to 0x006400ffL,
        "darkgrey" to 0xa9a9a9ffL,
        "darkkhaki" to 0xbdb76bffL,
        "darkmagenta" to 0x8b008bffL,
        "darkolivegreen" to 0x556b2fffL,
        "darkorange" to 0xff8c00ffL,
        "darkorchid" to 0x9932ccffL,
        "darkred" to 0x8b0000ffL,
        "darksalmon" to 0xe9967affL,
        "darkseagreen" to 0x8fbc8fffL,
        "darkslateblue" to 0x483d8bffL,
        "darkslategray" to 0x2f4f4fffL,
        "darkslategrey" to 0x2f4f4fffL,
        "darkturquoise" to 0x00ced1ffL,
        "darkviolet" to 0x9400d3ffL,
        "deeppink" to 0xff1493ffL,
        "deepskyblue" to 0x00bfffffL,
        "dimgray" to 0x696969ffL,
        "dimgrey" to 0x696969ffL,
        "dodgerblue" to 0x1e90ffffL,
        "firebrick" to 0xb22222ffL,
        "floralwhite" to 0xfffaf0ffL,
        "forestgreen" to 0x228b22ffL,
        "fuchsia" to 0xff00ffffL,
        "gainsboro" to 0xdcdcdcffL,
        "ghostwhite" to 0xf8f8ffffL,
        "gold" to 0xffd700ffL,
        "goldenrod" to 0xdaa520ffL,
        "gray" to 0x808080ffL,
        "green" to 0x008000ffL,
        "greenyellow" to 0xadff2fffL,
        "grey" to 0x808080ffL,
        "honeydew" to 0xf0fff0ffL,
        "hotpink" to 0xff69b4ffL,
        "indianred" to 0xcd5c5cffL,
        "indigo" to 0x4b0082ffL,
        "ivory" to 0xfffff0ffL,
        "khaki" to 0xf0e68cffL,
        "lavender" to 0xe6e6faffL,
        "lavenderblush" to 0xfff0f5ffL,
        "lawngreen" to 0x7cfc00ffL,
        "lemonchiffon" to 0xfffacdffL,
        "lightblue" to 0xadd8e6ffL,
        "lightcoral" to 0xf08080ffL,
        "lightcyan" to 0xe0ffffffL,
        "lightgoldenrodyellow" to 0xfafad2ffL,
        "lightgray" to 0xd3d3d3ffL,
        "lightgreen" to 0x90ee90ffL,
        "lightgrey" to 0xd3d3d3ffL,
        "lightpink" to 0xffb6c1ffL,
        "lightsalmon" to 0xffa07affL,
        "lightseagreen" to 0x20b2aaffL,
        "lightskyblue" to 0x87cefaffL,
        "lightslategray" to 0x778899ffL,
        "lightslategrey" to 0x778899ffL,
        "lightsteelblue" to 0xb0c4deffL,
        "lightyellow" to 0xffffe0ffL,
        "lime" to 0x00ff00ffL,
        "limegreen" to 0x32cd32ffL,
        "linen" to 0xfaf0e6ffL,
        "magenta" to 0xff00ffffL,
        "maroon" to 0x800000ffL,
        "mediumaquamarine" to 0x66cdaaffL,
        "mediumblue" to 0x0000cdffL,
        "mediumorchid" to 0xba55d3ffL,
        "mediumpurple" to 0x9370dbffL,
        "mediumseagreen" to 0x3cb371ffL,
        "mediumslateblue" to 0x7b68eeffL,
        "mediumspringgreen" to 0x00fa9affL,
        "mediumturquoise" to 0x48d1ccffL,
        "mediumvioletred" to 0xc71585ffL,
        "midnightblue" to 0x191970ffL,
        "mintcream" to 0xf5fffaffL,
        "mistyrose" to 0xffe4e1ffL,
        "moccasin" to 0xffe4b5ffL,
        "navajowhite" to 0xffdeadffL,
        "navy" to 0x000080ffL,
        "oldlace" to 0xfdf5e6ffL,
        "olive" to 0x808000ffL,
        "olivedrab" to 0x6b8e23ffL,
        "orange" to 0xffa500ffL,
        "orangered" to 0xff4500ffL,
        "orchid" to 0xda70d6ffL,
        "palegoldenrod" to 0xeee8aaffL,
        "palegreen" to 0x98fb98ffL,
        "paleturquoise" to 0xafeeeeffL,
        "palevioletred" to 0xdb7093ffL,
        "papayawhip" to 0xffefd5ffL,
        "peachpuff" to 0xffdab9ffL,
        "peru" to 0xcd853fffL,
        "pink" to 0xffc0cbffL,
        "plum" to 0xdda0ddffL,
        "powderblue" to 0xb0e0e6ffL,
        "purple" to 0x800080ffL,
        "rebeccapurple" to 0x663399ffL,
        "red" to 0xff0000ffL,
        "rosybrown" to 0xbc8f8fffL,
        "royalblue" to 0x4169e1ffL,
        "saddlebrown" to 0x8b4513ffL,
        "salmon" to 0xfa8072ffL,
        "sandybrown" to 0xf4a460ffL,
        "seagreen" to 0x2e8b57ffL,
        "seashell" to 0xfff5eeffL,
        "sienna" to 0xa0522dffL,
        "silver" to 0xc0c0c0ffL,
        "skyblue" to 0x87ceebffL,
        "slateblue" to 0x6a5acdffL,
        "slategray" to 0x708090ffL,
        "slategrey" to 0x708090ffL,
        "snow" to 0xfffafaffL,
        "springgreen" to 0x00ff7fffL,
        "steelblue" to 0x4682b4ffL,
        "tan" to 0xd2b48cffL,
        "teal" to 0x008080ffL,
        "thistle" to 0xd8bfd8ffL,
        "tomato" to 0xff6347ffL,
        "turquoise" to 0x40e0d0ffL,
        "violet" to 0xee82eeffL,
        "wheat" to 0xf5deb3ffL,
        "white" to 0xffffffffL,
        "whitesmoke" to 0xf5f5f5ffL,
        "yellow" to 0xffff00ffL,
        "yellowgreen" to 0x9acd32ffL
    )
    private val hex = Regex("""^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val formats = listOf(
        Triple("rgb", false, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", false, Regex("""hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hwb", false, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hwb", true, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"""))
    )
    private fun packed(value: Long) = rgba((value shr 24).toInt() and 255, (value shr 16).toInt() and 255, (value shr 8).toInt() and 255, (value and 255) / 255f)
    private fun byte(value: Double): Int {
        if (!value.isFinite()) return 0
        val low = kotlin.math.floor(value)
        return (if (value - low < .5) low else low + 1).toInt()
    }
    private fun hue(p: Double, q: Double, input: Double): Double {
        var t = input
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < .5 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
    fun color(source: String): Color {
        names[source]?.let { return packed(it) }
        if (hex.matches(source)) {
            var digits = source.drop(1)
            if (digits.length == 3 || digits.length == 4) digits = digits.map { "$it$it" }.joinToString("")
            if (digits.length == 6) digits += "ff"
            digits.toLongOrNull(16)?.let { return packed(it) }
        }
        for ((kind, alpha, pattern) in formats) {
            val match = pattern.find(source) ?: continue
            val values = match.groupValues.drop(1).map { it.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
            val opacity = if (alpha) byte(values[3].coerceIn(0.0, 1.0) * 255) / 255f else 1f
            if (kind == "rgb") return rgba(values[0].coerceIn(0.0, 255.0).toInt(), values[1].coerceIn(0.0, 255.0).toInt(), values[2].coerceIn(0.0, 255.0).toInt(), opacity)
            val h = ((values[0] % 360 + 360) % 360) / 360
            val second = (values[1] / 100).coerceIn(0.0, 1.0)
            val third = (values[2] / 100).coerceIn(0.0, 1.0)
            val channels = if (kind == "hwb") {
                if (second + third >= 1) List(3) { second / (second + third) }
                else listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(0.0, 1.0, it) * (1 - second - third) + second }
            } else {
                val q = if (third < .5) third * (1 + second) else third + second - third * second
                val p = 2 * third - q
                listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(p, q, it) }
            }
            return rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return Color.Transparent
    }
}

