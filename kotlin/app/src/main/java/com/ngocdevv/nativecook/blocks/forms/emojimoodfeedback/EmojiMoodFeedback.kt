package com.ngocdevv.nativecook.blocks.forms.emojimoodfeedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A slider answer and its colours: worst, neutral and best. */
data class Mood(val label: String, val word: String, val background: Color, val ink: Color, val shade: Color, val button: Color)
private val Moods = listOf(
    Mood("Bad", "BAD", Color(0xFFF67A66), Color(0xFF6B0B0D), Color(0xFFE65F4E), Color(0xFFB22423)),
    Mood("Not bad", "NOT BAD", Color(0xFFF7BC42), Color(0xFF4A2605), Color(0xFFE4A62C), Color(0xFF8A5E0F)),
    Mood("Good", "GOOD", Color(0xFFB8F25A), Color(0xFF17390A), Color(0xFFA1DB43), Color(0xFF3D6A1C)),
)
private val EyeWidth = floatArrayOf(62f, 106f, 136f)
private val EyeHeight = floatArrayOf(62f, 34f, 136f)
private val EyeX = floatArrayOf(58f, 66f, 74f)
private val EyeY = floatArrayOf(-30f, -46f, -14f)
private val MouthWidth = floatArrayOf(66f, 62f, 62f)
private val MouthBow = floatArrayOf(-20f, -16f, 22f)
private val MouthY = floatArrayOf(12f, 18f, 68f)
private const val MOUTH_STROKE = 12f
private const val BROW_TILT = 24f
private const val MOUTH_TILT = 16f
private const val EYE_MIN = 12f
private const val FACE = 260f
private const val SIDE = 24f
private const val HEADER = 56f
private const val ICON = 44f
private const val WORD = 60f
private const val WORD_ROW = 76f
private const val TRACK_INSET = 48f
private const val TRACK = 4f
private const val STOP = 10f
private const val THUMB = 44f
private const val THUMB_MOUTH = 16f
private val ThumbBow = floatArrayOf(-4.5f, -3f, 5f)
private const val BAR = 56f
private const val SUBMIT = 128f
private const val NOTE = 148f
private const val NOTE_GAP = 16f
private const val NOTE_SUBMIT = 44f
private const val SHAPE_MASS = 1f
private const val SHAPE_STIFFNESS = 190f
private const val SHAPE_DAMPING = 14f
private const val TONE_MS = 400
private const val WORD_MS = 480
private const val SNAP_MASS = 1f
private const val SNAP_STIFFNESS = 260f
private const val SNAP_DAMPING = 20f
private const val HELD_SCALE = 1.12f
private const val NOTE_MS = 320
private const val THANKS_MS = 380
private const val HOP = 28f
private const val HOP_MASS = 1f
private const val HOP_STIFFNESS = 240f
private const val HOP_DAMPING = 9f
const val LOOP = 8f
private data class Drag(val at: Float, val to: Float, val lasts: Float)
private val Drags = listOf(Drag(0.8f, 1f, 0.6f), Drag(2.7f, 0f, 0.6f), Drag(4.7f, 2f, 1.1f))
private const val USER_HOLD = 3f
private const val RESET_MS = 500
private const val SHAPE_REDUCED_MS = 220
private const val HELD_IN_MS = 120
private const val HELD_OUT_MS = 180
private const val TRACK_CALL_MS = 140
private const val HOP_DELAY_MS = 120
private const val HOP_UP_MS = 170
private const val KEYBOARD_MS = 220
private const val NOTE_LIMIT = 280
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val Close = PathParser().parsePathString("M1 1L13 13M13 1L1 13").toPath()
private val Info = PathParser().parsePathString("M10 9.2L10 14.2").toPath()
private val Send = PathParser().parsePathString("M1 6L15 6M10.5 1.5L15 6L10.5 10.5").toPath()
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f }
private val QuadOut = Easing { 1f - (1f - it).pow(2) }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val CubicInOut = Easing { t -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f }
private val WordEase = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
private val WordFit = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = WORD.sp, stepSize = 0.25.sp)
private fun table(p: Float, values: FloatArray): Float { val i = if (p < 1f) 0 else 1; return values[i] + (values[i + 1] - values[i]) * (p - i) }
private fun bump(t: Float): Float { val sine = sin(PI.toFloat() * t.coerceIn(0f, 1f)); return sine * sine }
private fun colorAt(p: Float, colors: List<Color>): Color {
    val t = p.coerceIn(0f, 2f); val i = min(1, floor(t).toInt()); val u = t - i
    fun channel(a: Float, b: Float) = ((a.toDouble().pow(2.2) + (b.toDouble().pow(2.2) - a.toDouble().pow(2.2)) * u).pow(1.0 / 2.2) * 255).roundToInt() / 255f
    return Color(channel(colors[i].red, colors[i + 1].red), channel(colors[i].green, colors[i + 1].green), channel(colors[i].blue, colors[i + 1].blue), colors[i].alpha + (colors[i + 1].alpha - colors[i].alpha) * u)
}
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** The frame clock ignores duration scale, matching the source's explicit ReduceMotion.Never. */
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var target = initial
    private var velocity = 0f
    private var mass = 1f
    private var stiffness = 1f
    private var damping = 1f
    private var energy0 = 0f
    private var springing = false
    private var origin = 0L
    private var duration = 0f
    private var from = initial
    private var curve: Easing = QuadInOut
    private var timing = false
    private var completed: (() -> Unit)? = null
    fun cancel() { timing = false; springing = false; completed = null; velocity = 0f }
    fun direct(next: Float) { cancel(); state.floatValue = next; target = next; velocity = 0f }
    fun spring(next: Float, m: Float, k: Float, c: Float) {
        if (value == next) { direct(next); return }
        if (springing && target == next) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        energy0 = 0.5f * k * (value - next).pow(2)
        mass = m; stiffness = k; damping = c; target = next; springing = true; timing = false; completed = null
    }
    fun tween(next: Float, ms: Int, easing: Easing = QuadInOut, higherOrder: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (value == next && !higherOrder) { direct(next); onFinished?.invoke(); return }
        if (timing && target == next) { duration = ms * 1e6f; curve = easing; completed = onFinished; return }
        from = value; target = next; duration = ms * 1e6f; curve = easing; origin = System.nanoTime(); timing = true; springing = false; completed = onFinished; velocity = 0f
    }
    fun advance(now: Long, dt: Float) {
        if (timing) {
            val t = ((now - origin) / duration).coerceIn(0f, 1f)
            state.floatValue = from + (target - from) * curve.transform(t)
            if (t >= 1f) { timing = false; val finish = completed; completed = null; finish?.invoke(); return }
        }
        if (!springing) return
        val omega = sqrt(stiffness / mass); val decay = damping / (2f * mass); val e = value - target
        val next: Float
        if (decay < omega) {
            val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt)
            next = envelope * (e * c + (velocity + decay * e) / w * s)
            velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s)
        } else {
            val envelope = exp(-omega * dt); val b = velocity + omega * e
            next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt)
        }
        state.floatValue = target + next
        val energy = 0.5f * stiffness * next * next + 0.5f * mass * velocity * velocity
        if (energy0 == 0f || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { state.floatValue = target; velocity = 0f; springing = false }
    }
}

private fun String.jsTrim() = trim { it in '\u0009'..'\u000D' || it == '\u0020' || it == '\u00A0' || it == '\u1680' || it in '\u2000'..'\u200A' || it == '\u2028' || it == '\u2029' || it == '\u202F' || it == '\u205F' || it == '\u3000' || it == '\uFEFF' }
private fun noteEdit(text: String): String {
    val limited = text.take(NOTE_LIMIT)
    return if (limited.lastOrNull()?.isHighSurrogate() == true) limited.dropLast(1) else limited
}

/** A three-answer feedback form whose face morphs with its slider and moves aside for a note. */
@Composable
fun EmojiMoodFeedback(
    moods: List<Mood> = Moods,
    initialMood: Double = 2.0,
    question: String = "How was your shopping\nexperience?",
    noteLabel: String = "Add note",
    submitLabel: String = "Submit",
    thanksTitle: String = "Thank you!",
    thanksText: String = "Your feedback helps us get better.",
    thanksDuration: Double = 2.4,
    autoPlay: Boolean = false,
    onMoodChange: ((index: Int, mood: Mood) -> Unit)? = null,
    onSubmit: ((index: Int, note: String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val answers = if (moods.size == 3) moods else Moods
    val start = floor(initialMood + 0.5).toInt().coerceIn(0, 2)
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val latestStart by rememberUpdatedState(start)
    val latestMoods by rememberUpdatedState(answers)
    val changed by rememberUpdatedState(onMoodChange)
    val submitted by rememberUpdatedState(onSubmit)
    val view = LocalView.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    var index by remember { mutableIntStateOf(start) }
    var note by remember { mutableStateOf("") }
    var noteOpen by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var byHand by remember { mutableStateOf(false) }
    var grip by remember { mutableFloatStateOf(0f) }
    var hopAt by remember { mutableStateOf<Long?>(null) }
    val thumb = remember { Motion(start.toFloat()) }
    val shape = remember { Motion(start.toFloat()) }
    val tone = remember { Motion(start.toFloat()) }
    val slide = remember { Motion(start.toFloat()) }
    val held = remember { Motion(0f) }
    val noteShown = remember { Motion(0f) }
    val thanks = remember { Motion(0f) }
    val hop = remember { Motion(0f) }
    val keyboard = remember { Motion(0f) }
    fun touched() { touches++ }
    fun select(to: Int) { touched(); byHand = true; thumb.spring(to.toFloat(), SNAP_MASS, SNAP_STIFFNESS, SNAP_DAMPING) }
    fun dragTo(to: Float, seconds: Float) {
        byHand = false; held.tween(1f, HELD_IN_MS)
        thumb.tween(to, (seconds * 1000).roundToInt(), CubicInOut) { held.tween(0f, HELD_OUT_MS) }
    }
    fun closeNote() { keyboardController?.hide(); focus.clearFocus(); noteOpen = false; noteShown.tween(0f, NOTE_MS, CubicOut) }
    fun openNote() { touched(); noteOpen = true; noteShown.tween(1f, NOTE_MS, CubicOut) }
    fun submit() {
        if (sent) return
        touched(); view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY); closeNote(); sent = true
        submitted?.invoke(index, note.jsTrim()); thanks.tween(1f, THANKS_MS, CubicOut)
        if (!reduced) hopAt = System.nanoTime() + HOP_DELAY_MS * 1_000_000L
    }
    fun reset() {
        sent = false; note = ""; thanks.tween(0f, THANKS_MS, CubicOut); byHand = false
        thumb.tween(latestStart.toFloat(), RESET_MS, CubicInOut)
    }
    fun react() {
        val next = floor(thumb.value + 0.5f).toInt().coerceIn(0, 2)
        if (next == index) return
        index = next
        if (reduced) shape.tween(next.toFloat(), SHAPE_REDUCED_MS) else shape.spring(next.toFloat(), SHAPE_MASS, SHAPE_STIFFNESS, SHAPE_DAMPING)
        tone.tween(next.toFloat(), TONE_MS, QuadOut); slide.tween(next.toFloat(), WORD_MS, WordEase)
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        changed?.invoke(next, latestMoods[next])
    }
    val latestReset by rememberUpdatedState(::reset)
    val latestDrag by rememberUpdatedState(::dragTo)
    val latestReact by rememberUpdatedState(::react)
    val busy by rememberUpdatedState(noteOpen || sent)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }; val dt = ((now - last) / 1e9f).coerceIn(0f, 0.064f); last = now
            if (hopAt?.let { now >= it } == true) {
                hopAt = null
                hop.tween(-HOP, HOP_UP_MS, QuadOut, higherOrder = true) { hop.spring(0f, HOP_MASS, HOP_STIFFNESS, HOP_DAMPING) }
            }
            thumb.advance(now, dt); latestReact()
            shape.advance(now, dt); tone.advance(now, dt); slide.advance(now, dt); held.advance(now, dt)
            noteShown.advance(now, dt); thanks.advance(now, dt); hop.advance(now, dt); keyboard.advance(now, dt)
        }
    }
    LaunchedEffect(noteOpen) { if (noteOpen) { focusRequester.requestFocus(); keyboardController?.show() } }
    LaunchedEffect(sent, thanksDuration) {
        if (sent && thanksDuration > 0.0) { delay((thanksDuration * 1000).toLong()); latestReset() }
    }
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (busy) delay(100)
            latestReset(); delay((RESET_MS + 300).toLong())
        }
        coroutineScope {
            var origin = System.nanoTime()
            while (true) {
                val cycle = origin
                for (drag in Drags) launch {
                    delay(max(0L, (cycle + (drag.at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                    latestDrag(drag.to, drag.lasts)
                }
                delay(max(0L, (cycle + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9f).toLong()
            }
        }
    }
    val backgrounds = answers.map { it.background }; val inks = answers.map { it.ink }; val shades = answers.map { it.shade }; val buttons = answers.map { it.button }
    val background = colorAt(tone.value, backgrounds)
    val ink = colorAt(tone.value, inks)
    val shade = colorAt(tone.value, shades)
    val button = colorAt(tone.value, buttons)
    val mood = answers[index]
    val form = !noteOpen && !sent
    val formOpacity = (1f - noteShown.value) * (1f - thanks.value)
    var questionHeight by remember { mutableFloatStateOf(56f) }
    var fullHeight by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(background)) {
        val width = maxWidth.value; val height = maxHeight.value
        if (height > fullHeight) fullHeight = height
        val inset = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val top = inset.calculateTopPadding().value + 14f
        val bottom = max(inset.calculateBottomPadding().value, 16f) + 6f
        val ime = WindowInsets.ime.getBottom(density) / density.density
        val covered = max(0f, ime - max(0f, fullHeight - height))
        LaunchedEffect(covered) { keyboard.tween(covered, KEYBOARD_MS, CubicOut) }
        val faceSlotHeight = max(0f, height - top - bottom - HEADER - 30f - questionHeight - WORD_ROW - 40f - THUMB - 8f - 20f - 64f - BAR)
        val faceSlotTop = top + HEADER + 30f + questionHeight
        val faceCentre = faceSlotTop + faceSlotHeight / 2f
        val fit = min(1f, faceSlotHeight / (FACE - 40f))
        val cardTop = height - max(keyboard.value, bottom) - NOTE_GAP - NOTE
        val noteRoomTop = inset.calculateTopPadding().value + 14f + HEADER
        val room = cardTop - noteRoomTop
        val n = noteShown.value * (1f - thanks.value)
        val squeeze = min(1f, room / (FACE - 30f))
        val faceShift = ((noteRoomTop + cardTop) / 2f - faceCentre) * n + (height * 0.42f - faceCentre) * thanks.value + hop.value
        val faceScale = fit * (1f + (squeeze - 1f) * n)
        val step = max(0.01f, (width - TRACK_INSET * 2f) / 2f)
        Column(Modifier.padding(top = top.dp).fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
            Row(Modifier.fillMaxWidth().height(HEADER.dp).padding(horizontal = SIDE.dp).graphicsLayer { alpha = 1f - thanks.value; compositingStrategy = CompositingStrategy.ModulateAlpha },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                HeaderIcon(false, ink, !sent) { touched(); if (noteOpen) closeNote() else onClose?.invoke() }
                HeaderIcon(true, ink, !sent) { touched(); onInfo?.invoke() }
            }
            BasicText(question, style = textStyle(22f, 28f, FontWeight.Medium, ink, -0.2f).copy(textAlign = TextAlign.Center),
                onTextLayout = { questionHeight = it.size.height / density.density },
                modifier = Modifier.padding(top = 30.dp, start = 40.dp, end = 40.dp).fillMaxWidth().graphicsLayer { alpha = formOpacity; compositingStrategy = CompositingStrategy.ModulateAlpha }.semantics { heading() })
            Box(Modifier.fillMaxWidth().height(faceSlotHeight.dp).then(if (noteOpen) Modifier.pointerInput(Unit) { detectTapGestures { closeNote() } } else Modifier), contentAlignment = Alignment.Center) {
                Face(width, shape, ink, still, Modifier.wrapContentSize(Alignment.Center, unbounded = true).requiredSize(width.dp, FACE.dp).graphicsLayer {
                    translationY = faceShift * density.density; scaleX = faceScale; scaleY = faceScale
                }.semantics { contentDescription = "${mood.label} face"; role = Role.Image })
            }
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = formOpacity; compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                Box(Modifier.fillMaxWidth().height(WORD_ROW.dp).clearAndSetSemantics {}) {
                    for ((i, item) in answers.withIndex()) BasicText(item.word, style = textStyle(WORD, WORD_ROW, FontWeight.Black, shade, -2.5f).copy(textAlign = TextAlign.Center), maxLines = 1, autoSize = WordFit,
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(WORD_ROW.dp).graphicsLayer {
                            val away = i - slide.value
                            if (still) alpha = 1f - min(1f, abs(away)) else translationX = away * width * density.density
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        })
                }
                Box(Modifier.padding(top = 40.dp, start = (TRACK_INSET - THUMB / 2f).dp, end = (TRACK_INSET - THUMB / 2f).dp).fillMaxWidth().height(THUMB.dp)
                    .then(if (form) Modifier.pointerInput(step) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            thumb.cancel(); byHand = true; held.tween(1f, HELD_IN_MS)
                            val x = down.position.x / density.density
                            val centre = THUMB / 2f + thumb.value * step
                            val onThumb = abs(x - centre) <= THUMB / 2f
                            grip = if (onThumb) centre - x else 0f
                            if (!onThumb) thumb.tween(((x - THUMB / 2f) / step).coerceIn(0f, 2f), TRACK_CALL_MS, QuadOut)
                            touched(); down.consume()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed || pointer.isConsumed) break
                                    thumb.direct(((pointer.position.x / density.density + grip - THUMB / 2f) / step).coerceIn(0f, 2f)); pointer.consume()
                                }
                            } finally {
                                held.tween(0f, HELD_OUT_MS)
                                thumb.spring(floor(thumb.value + 0.5f).coerceIn(0f, 2f), SNAP_MASS, SNAP_STIFFNESS, SNAP_DAMPING); touched()
                            }
                        }
                    } else Modifier).semantics {
                        contentDescription = "${question.replaceFirst('\n', ' ')}, ${mood.label}"
                        progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..2f, 1)
                        customActions = listOf(CustomAccessibilityAction("Increment") { select(min(2, index + 1)); true }, CustomAccessibilityAction("Decrement") { select(max(0, index - 1)); true })
                    }) { Slider(step, thumb, held, shape, ink, background) }
                Row(Modifier.padding(top = 8.dp, start = (TRACK_INSET - THUMB / 2f).dp, end = (TRACK_INSET - THUMB / 2f).dp).fillMaxWidth().height(20.dp)) {
                    for ((i, item) in answers.withIndex()) Box(Modifier.weight(1f).height(20.dp), contentAlignment = if (i == 0) Alignment.CenterStart else if (i == 2) Alignment.CenterEnd else Alignment.Center) {
                        BasicText(item.label, style = textStyle(14f, 20f, FontWeight.Medium, ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = 1f - 0.55f * min(1f, abs(tone.value - i)) })
                        Box(Modifier.matchParentSize().bleed(10.dp).clickable(remember { MutableInteractionSource() }, indication = null, enabled = form, role = Role.Button) { select(i) }.semantics { contentDescription = item.label; selected = index == i })
                    }
                }
                Row(Modifier.padding(top = 64.dp, start = SIDE.dp, end = SIDE.dp).fillMaxWidth().height(BAR.dp).clip(RoundedCornerShape((BAR / 2f).dp)).background(ink.copy(alpha = ink.alpha * 0.08f)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(BAR.dp).clickable(remember { MutableInteractionSource() }, indication = null, enabled = form, role = Role.Button) { openNote() }.padding(start = 26.dp, end = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicText(note.jsTrim().ifEmpty { noteLabel }, style = textStyle(15f, 20f, FontWeight.Medium, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SubmitPill(submitLabel, button, background, SUBMIT, BAR, form) { submit() }
                }
            }
        }
        val noteAlpha = noteShown.value * (1f - thanks.value)
        Box(Modifier.offset(x = SIDE.dp, y = cardTop.dp).width(max(0f, width - SIDE * 2f).dp).height(NOTE.dp).graphicsLayer {
            alpha = noteAlpha; translationY = if (still) 0f else (1f - noteShown.value) * 32f * density.density; compositingStrategy = CompositingStrategy.ModulateAlpha
        }.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)).border(1.5.dp, mood.ink.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            .then(if (noteOpen) Modifier else Modifier.clearAndSetSemantics {})) {
            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(mood.ink, mood.ink)) {
                BasicTextField(note, { val next = noteEdit(it); if (next != note) { touched(); note = next } }, enabled = noteOpen, readOnly = !noteOpen, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), textStyle = textStyle(16f, 22f, FontWeight.Normal, mood.ink), cursorBrush = SolidColor(mood.ink),
                    modifier = Modifier.padding(1.5.dp).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = (NOTE_SUBMIT + 16f).dp).fillMaxSize().focusRequester(focusRequester).semantics { contentDescription = noteLabel },
                    decorationBox = { inner -> Box { if (note.isEmpty()) BasicText(noteLabel, style = textStyle(16f, 22f, FontWeight.Normal, mood.ink.copy(alpha = 128f / 255f))); inner() } })
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 11.5.dp, bottom = 11.5.dp)) { SubmitPill(submitLabel, button, background, 112f, NOTE_SUBMIT, noteOpen && !sent) { submit() } }
        }
        Column(Modifier.offset(y = (height * 0.42f + FACE / 2f * fit - 4f).dp).padding(horizontal = 32.dp).fillMaxWidth().graphicsLayer {
            alpha = thanks.value; translationY = if (still) 0f else (1f - thanks.value) * 18f * density.density; compositingStrategy = CompositingStrategy.ModulateAlpha
        }.then(if (sent) Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics {}), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(thanksTitle, style = textStyle(34f, 40f, FontWeight.ExtraBold, ink, -0.6f).copy(textAlign = TextAlign.Center))
            BasicText(thanksText, style = textStyle(16f, 22f, FontWeight.Medium, ink).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = 0.75f })
        }
    }
}

/** Rounded bars and a single cubic mouth extend past the tables when the face spring overshoots. */
@Composable
private fun Face(width: Float, shape: State<Float>, ink: Color, still: Boolean, modifier: Modifier) {
    Canvas(modifier.clipToBounds()) { surface(0.dp) { _, _, _, _ ->
        val s = shape.value
        val w = max(EYE_MIN, table(s, EyeWidth)); val h = max(EYE_MIN, table(s, EyeHeight)); val r = min(w, h) / 2f
        val cy = FACE / 2f + table(s, EyeY)
        val x = table(s, EyeX)
        val brow = if (still) 0f else bump(s) * BROW_TILT
        val left = Offset(width / 2f - x, cy); val right = Offset(width / 2f + x, cy)
        withTransform({ rotate(-brow, left) }) { drawRoundRect(ink, Offset(left.x - w / 2f, left.y - h / 2f), Size(w, h), CornerRadius(r)) }
        withTransform({ rotate(brow, right) }) { drawRoundRect(ink, Offset(right.x - w / 2f, right.y - h / 2f), Size(w, h), CornerRadius(r)) }
        val half = table(s, MouthWidth) / 2f; val bow = table(s, MouthBow)
        val mouthCentre = Offset(width / 2f, FACE / 2f + table(s, MouthY))
        val y = mouthCentre.y - bow / 2f; val handle = y + bow / 0.75f
        val path = Path().apply { moveTo(mouthCentre.x - half, y); cubicTo(mouthCentre.x - half, handle, mouthCentre.x + half, handle, mouthCentre.x + half, y) }
        withTransform({ rotate(if (still) 0f else bump(s - 1f) * MOUTH_TILT, mouthCentre) }) { drawPath(path, ink, style = Stroke(MOUTH_STROKE, cap = StrokeCap.Round)) }
    } }
}

/** The held disc can overflow the slider's fixed hit area by a few points. */
@Composable
private fun Slider(step: Float, thumb: State<Float>, held: State<Float>, shape: State<Float>, ink: Color, background: Color) {
    Canvas(Modifier.fillMaxSize().bleed(6.dp).clearAndSetSemantics {}) { surface(6.dp) { x, y, _, _ ->
        withTransform({ translate(x, y) }) {
            drawRoundRect(ink.copy(alpha = ink.alpha * 0.16f), Offset(THUMB / 2f, (THUMB - TRACK) / 2f), Size(step * 2f, TRACK), CornerRadius(TRACK / 2f))
            for (stop in 0..2) drawCircle(ink.copy(alpha = ink.alpha * 0.22f), STOP / 2f, Offset(THUMB / 2f + stop * step, THUMB / 2f))
            withTransform({ translate(thumb.value * step, 0f); scale(1f + (HELD_SCALE - 1f) * held.value, 1f + (HELD_SCALE - 1f) * held.value, Offset(THUMB / 2f, THUMB / 2f)) }) {
                drawCircle(ink, THUMB / 2f, Offset(THUMB / 2f, THUMB / 2f))
                val bow = table(shape.value, ThumbBow); val cy = THUMB / 2f + 5f - bow / 2f; val left = THUMB / 2f - THUMB_MOUTH / 2f; val handle = cy + bow / 0.75f
                drawPath(Path().apply { moveTo(left, cy); cubicTo(left, handle, left + THUMB_MOUTH, handle, left + THUMB_MOUTH, cy) }, background, style = Stroke(2.6f, cap = StrokeCap.Round))
            }
        }
    } }
}

@Composable
private fun HeaderIcon(info: Boolean, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(ICON.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape((ICON / 2f).dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
            if (info) {
                drawCircle(ink, 8.5f, Offset(10f, 10f), style = Stroke(1.8f))
                drawPath(Info, ink, style = Stroke(1.8f, cap = StrokeCap.Round)); drawCircle(ink, 1.1f, Offset(10f, 6.1f))
            } else withTransform({ translate(3f, 3f) }) { drawPath(Close, ink, style = Stroke(2f, cap = StrokeCap.Round)) }
        } }
        Box(Modifier.matchParentSize().bleed(6.dp).clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (info) "About this question" else "Close" })
    }
}
@Composable
private fun SubmitPill(label: String, fill: Color, paper: Color, width: Float, height: Float, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.size(width.dp, height.dp).then(if (enabled) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (pressed) 0.8f else 1f }.background(fill, RoundedCornerShape((BAR / 2f).dp)), contentAlignment = Alignment.Center) {
            Row(Modifier.wrapContentSize(Alignment.Center, unbounded = true), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, style = textStyle(15f, 20f, FontWeight.SemiBold, paper))
                Canvas(Modifier.size(16.dp, 12.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ -> drawPath(Send, paper, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
            }
        }
    }
}
