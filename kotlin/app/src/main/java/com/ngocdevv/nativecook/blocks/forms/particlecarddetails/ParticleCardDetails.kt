package com.ngocdevv.nativecook.blocks.forms.particlecarddetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Captured at Continue; callbacks receive that same submission through the wait. */
data class CardDetails(val name: String, val securityCode: String)
const val LOOP = 7.8
private const val TAP_AT = .8
private const val TYPE_AT = 1.05
private val LETTER_GAPS = doubleArrayOf(.11, .09, .13, .1, .16, .12, .09, .11)
private const val NEXT_FIELD_AFTER = .3
private const val CODE_AFTER = .24
private const val DIGIT_EVERY = .17
private const val PRESS_AFTER = .5
private const val PRESS_FOR = .15
private const val USER_HOLD = 3.0
private const val REST_POLL_MS = 100L
private const val TEXT_SWAP = .14
private const val FIELDS_OUT = .28
private const val CHROME_OUT = .2
private const val FIELDS_IN = .32
private const val CHROME_AFTER = .2
private const val CHROME_IN = .26
private const val SPIN_STEP = .1
private const val BARS = 8
private val SCREEN = Color(0xFF000000)
private val INK = Color(0xFFFFFFFF)
private val MUTE = Color(0xFF98989F)
private val FAINT = Color(0xFF48484C)
private val GROUP = Color(0xFF2C2C2E)
private val GROUP_GHOST = Color(0xFF35314C)
private val HAIRLINE = Color(0xFF444447)
private val CLOSE_FILL = Color(0xFF222224)
private val BUTTON_OFF = Color(0xFF38383A)
private val BUTTON_OFF_INK = Color(0xFF8E8E93)
private val BUTTON_ON = Color(0xFFF2F2F7)
private val BUTTON_PRESSED = Color(0xFFB9B9BF)
private val BUTTON_ON_INK = Color(0xFF000000)
private val CARET = Color(0xFF0A84FF)
private const val CLOSE = 44f
private const val CLOSE_LEFT = 15f
private const val CLOSE_TOP = 3f
private const val CLOSE_ICON = 25f
private const val CARD_MARGIN = 20f
private const val CARD_TOP = 75f
private const val CARD_RATIO = 1.586f
private const val TEXT_GAP = 76f
private const val TEXT_LEFT = 38f
private const val TEXT_RIGHT_EXTRA = 14f
private const val TEXTS = 71f
private const val FIELDS_GAP = 29f
private const val FIELDS_MARGIN = 22f
private const val ROW = 48.5f
private const val VALUE_LEFT = 131f
private const val VALUE_RIGHT = 16f
private const val BUTTON = 51f
private const val BUTTON_MARGIN = 38f
private const val KEYBOARD_GAP = 12f
private const val FIELDS_CLEAR = 16f
private const val FIELDS_SHRINK = .9
private const val FIELDS_RADIUS = 23f
private const val GHOST_BLUR = 16f
private const val GHOST_SPILL = 40f
private val GHOST_SAMPLES = floatArrayOf(-2.4f, -1.6f, -.8f, 0f, .8f, 1.6f, 2.4f)
private val GHOST_WEIGHTS = GHOST_SAMPLES.map { exp(-it * it / 2f) }.let { weights -> val sum = weights.sum(); weights.map { it / sum } }
private const val BOTTOM_MIN = 16f
private const val BOTTOM_EXTRA = 1f
private const val TITLE_SIZE = 19f
private const val TITLE_LINE = 23f
private const val SUBTITLE_SIZE = 19f
private const val SUBTITLE_LINE = 24f
private const val SPINNER = 16f
private const val SPINNER_LEFT = 8f
private const val BAR_WIDTH = 1.8f
private const val BAR_HEIGHT = 4.6f
private const val BAR_RADIUS = .9f
private const val BAR_OUT = 5f
private const val MANUAL_TOP = 18f
private const val MANUAL_SIZE = 12f
private const val MANUAL_LINE = 16f
private const val MANUAL_SLOP = 10f
private const val LABEL_SIZE = 14f
private const val LABEL_LINE = 18f
private const val INPUT_SIZE = 15f
private const val INPUT_LINE = 20f
private const val SECURE_SIZE = 22f
private const val SECURE_TRACKING = 1f
private const val CARET_WIDTH = 2f
private const val CARET_HEIGHT = 19f
private const val CARET_GAP = 1f
private const val CARET_RADIUS = 1f
private const val KEYBOARD_MS = 250.0
private const val BUTTON_PRESS_IN = 80.0
private const val BUTTON_PRESS_OUT = 220.0
private const val BUTTON_SCALE_MS = 120.0
private const val BUTTON_TEXT_MS = 220.0
private const val BUTTON_SCALE = .97
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private enum class Field { Name, Code }
private val Linear = Easing { it }
private val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private fun smooth(t: Double, from: Double, to: Double): Double { val u = ((t - from) / (to - from)).coerceIn(0.0, 1.0); return u * u * (3.0 - 2.0 * u) }
private fun rounded(value: Double): Double { val lower = floor(value); return if (value - lower < .5) lower else lower + 1.0 }
private fun jsTrim(text: String): String = text.trim { it == '\u0009' || it == '\u000A' || it == '\u000B' || it == '\u000C' || it == '\u000D' || it == '\u0020' || it == '\u00A0' || it == '\u1680' || it in '\u2000'..'\u200A' || it == '\u2028' || it == '\u2029' || it == '\u202F' || it == '\u205F' || it == '\u3000' || it == '\uFEFF' }
private fun gammaColor(a: Color, b: Color, t: Double): Color {
    val u = t.coerceIn(0.0, 1.0)
    fun byte(x: Float, y: Float) = rounded((x.toDouble().pow(2.2) + (y.toDouble().pow(2.2) - x.toDouble().pow(2.2)) * u).pow(1.0 / 2.2) * 255).toInt()
    return rgba(byte(a.red, b.red), byte(a.green, b.green), byte(a.blue, b.blue), 1f)
}
private class FrameTime { var now: Long? = null }
private class Scalar(initial: Double, private val clock: FrameTime) {
    var value by mutableStateOf(initial)
        private set
    private var from = initial; private var target = initial; private var began = 0L; private var duration = 0.0; private var ease = Linear
    private var active = false
    fun set(next: Double) { active = false; value = next }
    fun time(next: Double, ms: Double, curve: Easing = Linear, still: Boolean = false): Boolean {
        if (value == next || still || ms <= 0.0) { set(next); return true }
        val now = clock.now ?: System.nanoTime()
        if (!(active && target == next)) { from = value; began = now }
        target = next; duration = ms; ease = curve; active = true; return advance(now)
    }
    fun advance(now: Long): Boolean {
        if (!active) return false
        val u = ((now - began) / (duration * 1e6)).coerceIn(0.0, 1.0)
        value = from + (target - from) * (if (ease === Linear) u else ease.transform(u.toFloat()).toDouble())
        if (u >= 1.0) { value = target; active = false; return true }
        return false
    }
}
private class Tone(initial: Color, clock: FrameTime) {
    private val red = Scalar(initial.red * 255.0, clock); private val green = Scalar(initial.green * 255.0, clock); private val blue = Scalar(initial.blue * 255.0, clock)
    val color get() = rgba(rounded(red.value).toInt(), rounded(green.value).toInt(), rounded(blue.value).toInt(), 1f)
    fun time(next: Color, ms: Double) { red.time(next.red * 255.0, ms, CssEase); green.time(next.green * 255.0, ms, CssEase); blue.time(next.blue * 255.0, ms, CssEase) }
    fun advance(now: Long) { red.advance(now); green.advance(now); blue.advance(now) }
}
private class ParticleFieldGhostInk {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(1f, GROUP_GHOST.red, GROUP_GHOST.green, GROUP_GHOST.blue)
    }
    private val layerPaint = android.graphics.Paint()
    private var sigma = Float.NaN
    private val add = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, blur: Float, opacity: Float, scale: Float) {
        val canvasWidth = width + GHOST_SPILL * 2f
        val canvasHeight = height + GHOST_SPILL * 2f
        canvas.save()
        canvas.clipRect(0f, 0f, canvasWidth, canvasHeight)
        canvas.scale(scale, scale, GHOST_SPILL + width / 2f, GHOST_SPILL + height / 2f)
        if (Build.VERSION.SDK_INT >= 28) {
            if (sigma != blur) {
                sigma = blur
                paint.maskFilter = android.graphics.BlurMaskFilter(sigmaToRadius(blur), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            paint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(GHOST_SPILL, GHOST_SPILL, GHOST_SPILL + width, GHOST_SPILL + height, FIELDS_RADIUS, FIELDS_RADIUS, paint)
        } else {
            // Add the weighted coverage once, then apply the group's opacity.
            layerPaint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
            canvas.saveLayer(0f, 0f, canvasWidth, canvasHeight, layerPaint)
            paint.xfermode = add
            for (vertical in GHOST_SAMPLES.indices) for (horizontal in GHOST_SAMPLES.indices) {
                val dx = GHOST_SAMPLES[horizontal] * blur
                val dy = GHOST_SAMPLES[vertical] * blur
                paint.alpha = (GHOST_WEIGHTS[horizontal] * GHOST_WEIGHTS[vertical] * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawRoundRect(GHOST_SPILL + dx, GHOST_SPILL + dy, GHOST_SPILL + width + dx, GHOST_SPILL + height + dy, FIELDS_RADIUS, FIELDS_RADIUS, paint)
            }
            paint.xfermode = null
            canvas.restore()
        }
        canvas.restore()
    }
}

@Composable
private fun ParticleFieldsGhost(width: Float, height: Float, gone: Double, still: Boolean, modifier: Modifier = Modifier) {
    val ink = remember { ParticleFieldGhostInk() }
    Canvas(modifier.size((width + GHOST_SPILL * 2f).dp, (height + GHOST_SPILL * 2f).dp).clipToBounds().clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            val blur = 1f + GHOST_BLUR * gone.toFloat()
            val opacity = (smooth(gone, 0.0, .35) * (1.0 - smooth(gone, .4, 1.0))).toFloat()
            val scale = if (still) 1f else (1.0 - (1.0 - FIELDS_SHRINK) * gone).toFloat()
            ink.draw(drawContext.canvas.nativeCanvas, width, height, blur, opacity, scale)
        }
    }
}

private fun particleLimitInput(previous: TextFieldValue, proposed: TextFieldValue, maxLength: Int?): TextFieldValue {
    if (maxLength == null || previous.text == proposed.text) return proposed
    val old = previous.text; val next = proposed.text
    var first = 0
    while (first < min(old.length, next.length) && old[first] == next[first]) first++
    var oldEnd = old.length; var newEnd = next.length
    while (oldEnd > first && newEnd > first && old[oldEnd - 1] == next[newEnd - 1]) { oldEnd--; newEnd-- }
    val inserted = next.substring(first, newEnd)
    if (inserted.isEmpty()) return proposed
    var keep = (maxLength.coerceAtLeast(0) - (old.length - (oldEnd - first))).coerceAtLeast(0)
    if (inserted.length <= keep) return proposed
    if (keep > 0 && inserted[keep - 1].isHighSurrogate()) keep--
    val accepted = old.substring(0, first) + inserted.take(keep) + old.substring(oldEnd)
    if (accepted == old) return previous
    return TextFieldValue(accepted, TextRange(first + keep))
}

@Composable
private fun ParticleFieldRow(
    label: String,
    value: String,
    secure: Boolean,
    caret: Boolean,
    focused: Boolean,
    maxLength: Int?,
    focusRequester: FocusRequester,
    onChangeText: (String) -> Unit,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    onSubmit: () -> Unit,
    pointersEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var nativeFocused by remember { mutableStateOf(false) }
    val controlled = if (editing.text == value) editing else {
        val delta = value.length - editing.text.length
        val selection = if (focused) TextRange((editing.selection.start + delta).coerceIn(0, value.length), (editing.selection.end + delta).coerceIn(0, value.length)) else TextRange(value.length)
        TextFieldValue(value, selection)
    }
    androidx.compose.runtime.SideEffect { if (editing != controlled) editing = controlled }
    val labelStyle = particleStyle(LABEL_SIZE, LABEL_LINE, 600, INK)
    val inputStyle = particleStyle(INPUT_SIZE, INPUT_LINE, 400, INK)
    val mirrorStyle = particleStyle(if (secure) SECURE_SIZE else INPUT_SIZE, INPUT_LINE, 400, INK, if (secure) SECURE_TRACKING else 0f)
    val shown = if (secure) "•".repeat(value.length) else value
    BoxWithConstraints(modifier) {
        val rowWidth = maxWidth.value
        val valueWidth = max(0f, rowWidth - VALUE_LEFT - VALUE_RIGHT)
        val labelWidth = VALUE_LEFT - (TEXT_LEFT - FIELDS_MARGIN)
        val labelLayout = measurer.measure(AnnotatedString(label), labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = with(density) { labelWidth.dp.roundToPx() }.coerceAtLeast(0)))
        val mirrorLayout = measurer.measure(AnnotatedString(shown), mirrorStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = with(density) { valueWidth.dp.roundToPx() }.coerceAtLeast(0)))
        val labelHeight = labelLayout.size.height / density.density
        val mirrorWidth = mirrorLayout.size.width / density.density
        val mirrorHeight = mirrorLayout.size.height / density.density
        ParticleControl(label, true, pointersEnabled, Modifier.fillMaxSize().clearAndSetSemantics {}, scope, { focusRequester.requestFocus() }, controlRole = null) {}
        BasicText(label, Modifier.particleFrame(TEXT_LEFT - FIELDS_MARGIN, (ROW - labelHeight) / 2f, labelWidth, labelHeight).clearAndSetSemantics {}, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.particleFrame(VALUE_LEFT, 0f, valueWidth, ROW)) {
            if (!focused) {
                BasicText(shown, Modifier.particleFrame(0f, (ROW - mirrorHeight) / 2f, mirrorWidth, mirrorHeight).clearAndSetSemantics {}, style = mirrorStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (caret) Box(Modifier.particleFrame(mirrorWidth + CARET_GAP, (ROW - CARET_HEIGHT) / 2f, CARET_WIDTH, CARET_HEIGHT).background(CARET, RoundedCornerShape(CARET_RADIUS.dp)).clearAndSetSemantics {})
            }
            BasicTextField(
                value = controlled,
                onValueChange = { proposed ->
                    val accepted = particleLimitInput(controlled, proposed, maxLength)
                    editing = accepted
                    if (accepted.text != controlled.text) onChangeText(accepted.text)
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (focused) 1f else 0f }
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        if (focus.isFocused != nativeFocused) {
                            nativeFocused = focus.isFocused
                            if (nativeFocused) onFocus() else onBlur()
                        }
                    }
                    .semantics {
                        contentDescription = label
                        // The installed RN Android mapping rejects cc-name; only cc-csc is enabled.
                        if (secure) contentType = ContentType.CreditCardSecurityCode
                    },
                enabled = pointersEnabled,
                textStyle = inputStyle,
                singleLine = true,
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    keyboardType = if (secure) KeyboardType.NumberPassword else KeyboardType.Text,
                    imeAction = if (secure) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
                cursorBrush = SolidColor(CARET),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
            )
        }
    }
}

private data class AutoEvent(val at: Long, val order: Int, val run: () -> Unit)


/** Manual editing uses native inputs; the automatic tour only changes their values. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ParticleCardDetails(
    autoPlay: Boolean = false, demoName: String = "Maya Chen", demoSecurityCode: String = "482", securityCodeLength: Int = 3,
    processingDuration: Double = 2.7, title: String = "Card Details", subtitle: String = "Verify and complete your card information.",
    nameLabel: String = "Name", securityCodeLabel: String = "Security Code", continueLabel: String = "Continue",
    addingTitle: String = "Adding Card", addingSubtitle: String = "Contacting the Card Issuer…", manualLabel: String = "Enter Card Details Manually",
    onSubmit: ((CardDetails) -> Unit)? = null, onDone: ((CardDetails) -> Unit)? = null, onManual: (() -> Unit)? = null, onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion(); val reduced by rememberUpdatedState(still)
    val view = LocalView.current; val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val focusManager = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }; val codeFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope(); val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val submitCallback by rememberUpdatedState(onSubmit); val doneCallback by rememberUpdatedState(onDone)
    val manualCallback by rememberUpdatedState(onManual); val closeCallback by rememberUpdatedState(onClose)
    val back by rememberUpdatedState(processingDuration)
    var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<Field?>(null) }; var autoField by remember { mutableStateOf<Field?>(null) }
    var autoPressed by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var touches by remember { mutableIntStateOf(0) }
    var sent by remember { mutableStateOf(CardDetails("", "")) }; var pendingFinish by remember { mutableStateOf(false) }
    val clock = remember { FrameTime() }; val elapsed = remember { Scalar(-1.0, clock) }; val lift = remember { Scalar(0.0, clock) }; val raise = remember { Scalar(0.0, clock) }
    val buttonScale = remember { Scalar(1.0, clock) }; val buttonFill = remember { Tone(BUTTON_OFF, clock) }; val buttonInk = remember { Tone(BUTTON_OFF_INK, clock) }
    val mount = remember { System.nanoTime() }; val repeatStill = remember { still }; var twinkle by remember { mutableStateOf(0.0) }
    var crossed by remember { mutableStateOf(-1.0 >= processingDuration) }
    val currentBusy by rememberUpdatedState(busy)
    // Read through state: submit() is called through a reference kept from the first composition.
    val ready by rememberUpdatedState(jsTrim(name).length >= 2 && code.length == securityCodeLength)
    fun dismiss() { focusManager.clearFocus(force = true); keyboard?.hide() }
    fun touched() { autoField = null; autoPressed = false; touches++ }
    fun focus(field: Field) { touched(); focused = field }
    fun emptied() { name = ""; code = ""; doneCallback?.invoke(sent) }
    fun finish() { elapsed.set(-1.0); busy = false; pendingFinish = false }
    fun submit(byHand: Boolean) {
        if (busy || !ready) return
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        dismiss(); sent = CardDetails(jsTrim(name), code); autoField = null; busy = true
        val end = back + CHROME_AFTER + CHROME_IN
        submitCallback?.invoke(sent); elapsed.set(0.0)
        pendingFinish = elapsed.time(end, end * 1000.0)
    }
    val latestTouch by rememberUpdatedState(::touched); val latestSubmit by rememberUpdatedState(::submit); val latestDismiss by rememberUpdatedState(::dismiss)
    LaunchedEffect(autoPlay, touches, demoName, demoSecurityCode) {
        if (!autoPlay) return@LaunchedEffect
        val events = mutableListOf<AutoEvent>(); var serial = 0
        fun cycle(origin: Long) {
            fun at(seconds: Double, run: () -> Unit) { events += AutoEvent(origin + (seconds * 1e9).toLong(), serial++, run) }
            at(TAP_AT) { autoField = Field.Name }
            var moment = TYPE_AT
            demoName.indices.forEach { index ->
                if (index > 0) moment += LETTER_GAPS[(index - 1) % LETTER_GAPS.size]
                at(moment) { name = demoName.take(index + 1) }
            }
            moment += NEXT_FIELD_AFTER; at(moment) { autoField = Field.Code }; moment += CODE_AFTER
            demoSecurityCode.indices.forEach { index -> if (index > 0) moment += DIGIT_EVERY; at(moment) { code = demoSecurityCode.take(index + 1) } }
            moment += PRESS_AFTER; at(moment) { autoField = null; autoPressed = true }
            at(moment + PRESS_FOR) { autoPressed = false; latestSubmit(false) }
            at(LOOP) { cycle(origin + (LOOP * 1e9).toLong()) }
        }
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (currentBusy) delay(REST_POLL_MS)
            latestDismiss(); name = ""; code = ""
        }
        cycle(System.nanoTime())
        while (true) {
            val next = events.minWithOrNull(compareBy<AutoEvent> { it.at }.thenBy { it.order }) ?: break
            delay(max(0L, (next.at - System.nanoTime()) / 1_000_000L)); events.remove(next); next.run()
        }
    }
    DisposableEffect(view) {
        val controller = view.context.particleActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }; val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            clock.now = now
            try {
                val seconds = (now - mount).coerceAtLeast(0L) / 1e9
                twinkle = if (repeatStill) min(seconds, LOOP) else seconds % LOOP
                val completed = elapsed.advance(now)
                lift.advance(now); raise.advance(now); buttonScale.advance(now); buttonFill.advance(now); buttonInk.advance(now)
                val nowCrossed = elapsed.value >= back
                if (nowCrossed && !crossed) emptied()
                crossed = nowCrossed
                if (completed || pendingFinish) finish()
            } finally { clock.now = null }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(SCREEN)) {
        val width = maxWidth.value; val height = maxHeight.value
        val safeTop = safe.calculateTopPadding().value
        val cardWidth = max(1f, width - CARD_MARGIN * 2f); val cardHeight = cardWidth / CARD_RATIO
        val fieldsWidth = max(1f, width - FIELDS_MARGIN * 2f); val hairline = 1f / density.density; val fieldsHeight = ROW * 2f + hairline
        val fixed = safeTop + CARD_TOP + cardHeight + TEXTS + FIELDS_GAP + fieldsHeight
        val gap = min(TEXT_GAP, max(0f, height - fixed))
        val textsTop = safeTop + CARD_TOP + cardHeight + gap
        val fieldsTop = textsTop + TEXTS + FIELDS_GAP
        val fieldsBottom = fieldsTop + fieldsHeight
        val bottom = max(safe.calculateBottomPadding().value, BOTTOM_MIN) + BOTTOM_EXTRA
        val buttonRest = height - bottom - BUTTON
        val currentFieldsBottom by rememberUpdatedState(fieldsBottom); val currentButtonRest by rememberUpdatedState(buttonRest); val currentHeight by rememberUpdatedState(height)
        val ime = WindowInsets.ime.getBottom(density) / density.density
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density) / density.density
        LaunchedEffect(ime, imeTarget) {
            // Android reports the keyboard at its final position before these source tweens start.
            if (ime != imeTarget) return@LaunchedEffect
            val buttonTop = currentHeight - ime - KEYBOARD_GAP - BUTTON
            raise.time(if (ime > 0f) min(0f, buttonTop - currentButtonRest).toDouble() else 0.0, KEYBOARD_MS, CubicOut, reduced)
            lift.time(if (ime > 0f) min(0f, buttonTop - FIELDS_CLEAR - currentFieldsBottom).toDouble() else 0.0, KEYBOARD_MS, CubicOut, reduced)
        }
        val e = elapsed.value
        val asking = if (e < 0.0) 1.0 else if (e < processingDuration) 1.0 - smooth(e, 0.0, TEXT_SWAP) else smooth(e, processingDuration, processingDuration + TEXT_SWAP)
        val adding = if (e < 0.0) 0.0 else if (e < processingDuration) smooth(e, 0.0, TEXT_SWAP) else 1.0 - smooth(e, processingDuration, processingDuration + TEXT_SWAP)
        val gone = if (e < 0.0) 0.0 else if (e < processingDuration) smooth(e, 0.0, FIELDS_OUT) else 1.0 - smooth(e, processingDuration, processingDuration + FIELDS_IN)
        val chrome = if (e < 0.0) 1.0 else if (e < processingDuration) 1.0 - smooth(e, 0.0, CHROME_OUT) else smooth(e, processingDuration + CHROME_AFTER, processingDuration + CHROME_AFTER + CHROME_IN)
        val textWidth = max(1f, width - TEXT_LEFT * 2f - TEXT_RIGHT_EXTRA)
        val titleStyle = particleStyle(TITLE_SIZE, TITLE_LINE, 700, INK)
        val subtitleStyle = particleStyle(SUBTITLE_SIZE, SUBTITLE_LINE, 400, MUTE)
        val manualStyle = particleStyle(MANUAL_SIZE, MANUAL_LINE, 400, FAINT)
        fun measure(text: String, style: TextStyle, lines: Int = Int.MAX_VALUE) = measurer.measure(AnnotatedString(text), style, maxLines = lines, overflow = if (lines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(1, (textWidth * density.density).roundToInt()))).size
        val titleSize = measure(title, titleStyle, 1); val subtitleSize = measure(subtitle, subtitleStyle, 2)
        val addingSize = measure(addingTitle, titleStyle, 1); val addingSubSize = measure(addingSubtitle, subtitleStyle, 1); val manualSize = measure(manualLabel, manualStyle)
        val addingTitleHeight = max(addingSize.height / density.density, SPINNER)
        val manualTop = addingTitleHeight + addingSubSize.height / density.density + MANUAL_TOP
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = lift.value.toFloat() * density.density }) {
            ParticleControl("", true, true, Modifier.fillMaxSize(), scope, { latestDismiss() }, controlRole = null) {}
            ParticleCard(cardWidth, cardHeight, twinkle.toFloat(), PARTICLE_LOOP, e.toFloat(), still, Modifier.particleFrame(CARD_MARGIN, safeTop + CARD_TOP, cardWidth, cardHeight))
            Box(Modifier.particleFrame(TEXT_LEFT, textsTop, textWidth, TEXTS)) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = asking.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }.then(if (busy) Modifier.clearAndSetSemantics {} else Modifier)) {
                    BasicText(title, Modifier.particleFrame(0f, 0f, textWidth, titleSize.height / density.density), titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(subtitle, Modifier.particleFrame(0f, titleSize.height / density.density, textWidth, subtitleSize.height / density.density), subtitleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.particleFrame(0f, 0f, textWidth, manualTop + manualSize.height / density.density).graphicsLayer { alpha = adding.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }.then(if (!busy) Modifier.clearAndSetSemantics {} else Modifier)) {
                    BasicText(addingTitle, Modifier.particleFrame(0f, (addingTitleHeight - addingSize.height / density.density) / 2f, addingSize.width / density.density, addingSize.height / density.density), titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ParticleSpinner(e, Modifier.particleFrame(addingSize.width / density.density + SPINNER_LEFT, (addingTitleHeight - SPINNER) / 2f, SPINNER, SPINNER))
                    BasicText(addingSubtitle, Modifier.particleFrame(0f, addingTitleHeight, textWidth, addingSubSize.height / density.density), subtitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ParticleControl(manualLabel, true, busy, Modifier.particleFrame(-MANUAL_SLOP, manualTop - MANUAL_SLOP, manualSize.width / density.density + MANUAL_SLOP * 2f, manualSize.height / density.density + MANUAL_SLOP * 2f), scope, { latestTouch(); manualCallback?.invoke() }, controlRole = null, accessible = true) {
                        BasicText(manualLabel, Modifier.particleFrame(MANUAL_SLOP, MANUAL_SLOP, manualSize.width / density.density, manualSize.height / density.density), manualStyle)
                    }
                }
            }
            if (busy) ParticleFieldsGhost(fieldsWidth, fieldsHeight, gone, still, Modifier.particleFrame(FIELDS_MARGIN - GHOST_SPILL, fieldsTop - GHOST_SPILL, fieldsWidth + GHOST_SPILL * 2f, fieldsHeight + GHOST_SPILL * 2f))
            Box(Modifier.particleFrame(FIELDS_MARGIN, fieldsTop, fieldsWidth, fieldsHeight).graphicsLayer {
                alpha = (1.0 - smooth(gone, 0.0, .55)).toFloat(); scaleX = if (still) 1f else (1.0 - (1.0 - FIELDS_SHRINK) * gone).toFloat(); scaleY = scaleX
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.background(gammaColor(GROUP, GROUP_GHOST, min(1.0, gone * 2.0)), RoundedCornerShape(FIELDS_RADIUS.dp))) {
                ParticleFieldRow(nameLabel, name, false, autoField == Field.Name, focused == Field.Name, null, nameFocus,
                    { text -> latestTouch(); name = text }, { focus(Field.Name) }, { if (focused == Field.Name) focused = null }, { codeFocus.requestFocus() }, !busy,
                    Modifier.particleFrame(0f, 0f, fieldsWidth, ROW))
                Box(Modifier.particleFrame(TEXT_LEFT - FIELDS_MARGIN, ROW, max(0f, fieldsWidth - (TEXT_LEFT - FIELDS_MARGIN) * 2f), hairline).background(HAIRLINE))
                ParticleFieldRow(securityCodeLabel, code, true, autoField == Field.Code, focused == Field.Code, max(0, securityCodeLength), codeFocus,
                    { text -> latestTouch(); code = text.filter { it in '0'..'9' } }, { focus(Field.Code) }, { if (focused == Field.Code) focused = null }, { latestDismiss() }, !busy,
                    Modifier.particleFrame(0f, ROW + hairline, fieldsWidth, ROW))
            }
        }
        ParticleControl("Close", true, !busy, Modifier.particleFrame(CLOSE_LEFT, safeTop + CLOSE_TOP, CLOSE, CLOSE).graphicsLayer { alpha = chrome.toFloat() }, scope, { latestTouch(); latestDismiss(); closeCallback?.invoke() }) {
            Box(Modifier.fillMaxSize().background(CLOSE_FILL, RoundedCornerShape(CLOSE.dp / 2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = null, tint = INK, modifier = Modifier.size(CLOSE_ICON.dp)) }
        }
        ParticleControl(continueLabel, ready, !busy, Modifier.particleFrame(BUTTON_MARGIN, buttonRest, max(1f, width - BUTTON_MARGIN * 2f), BUTTON).graphicsLayer { translationY = raise.value.toFloat() * density.density }, scope, { latestTouch(); latestSubmit(true) }) { pressed ->
            val down = ready && (pressed || autoPressed)
            val target = if (!ready) BUTTON_OFF else if (down) BUTTON_PRESSED else BUTTON_ON
            LaunchedEffect(target) { buttonFill.time(target, if (down) BUTTON_PRESS_IN else BUTTON_PRESS_OUT) }
            LaunchedEffect(down) { buttonScale.time(if (down) BUTTON_SCALE else 1.0, BUTTON_SCALE_MS, CssEase) }
            LaunchedEffect(ready) { buttonInk.time(if (ready) BUTTON_ON_INK else BUTTON_OFF_INK, BUTTON_TEXT_MS) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = chrome.toFloat() }.graphicsLayer { scaleX = buttonScale.value.toFloat(); scaleY = scaleX }.background(buttonFill.color, RoundedCornerShape(BUTTON.dp / 2f)), contentAlignment = Alignment.Center) {
                BasicText(continueLabel, style = particleStyle(INPUT_SIZE, INPUT_LINE, 600, buttonInk.color), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
private fun Context.particleActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.particleActivity(); else -> null }
private fun Modifier.particleFrame(x: Float, y: Float, width: Float, height: Float): Modifier = wrapContentSize(Alignment.TopStart, unbounded = true).size(max(0f, width).dp, max(0f, height).dp).graphicsLayer { translationX = x * density; translationY = y * density }
private fun particleStyle(size: Float, line: Float, weight: Int, ink: Color, tracking: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), color = ink, fontFamily = FontFamily.SansSerif, letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
@Composable
private fun ParticleSpinner(elapsed: Double, modifier: Modifier) {
    Canvas(modifier.graphicsLayer { rotationZ = ((floor(max(0.0, elapsed) / SPIN_STEP) % BARS) * (360.0 / BARS)).toFloat() }.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            repeat(BARS) { index ->
                drawContext.canvas.save(); drawContext.canvas.nativeCanvas.rotate(index * (360f / BARS), SPINNER / 2f, SPINNER / 2f)
                drawRoundRect(MUTE.copy(alpha = .2f + .8f * index / (BARS - 1)), androidx.compose.ui.geometry.Offset((SPINNER - BAR_WIDTH) / 2f, SPINNER / 2f - BAR_OUT - BAR_HEIGHT / 2f), androidx.compose.ui.geometry.Size(BAR_WIDTH, BAR_HEIGHT), androidx.compose.ui.geometry.CornerRadius(BAR_RADIUS, BAR_RADIUS))
                drawContext.canvas.restore()
            }
        }
    }
}
private class PressState { var alive = true; var latestOut: Job? = null }

/** Retention follows Pressability; automatic actions bypass this native press wrapper. */
@Composable
private fun ParticleControl(label: String, enabled: Boolean, pointers: Boolean, modifier: Modifier, scope: CoroutineScope, action: () -> Unit, controlRole: Role? = Role.Button, accessible: Boolean = controlRole != null, content: @Composable (Boolean) -> Unit) {
    val state = remember { PressState() }; var pressed by remember { mutableStateOf(false) }
    val granted by rememberUpdatedState(enabled); val currentAction by rememberUpdatedState(action); val view = LocalView.current
    DisposableEffect(state) { state.alive = true; onDispose { state.alive = false; state.latestOut?.cancel() } }
    val semantics = if (!accessible) Modifier else Modifier.semantics(mergeDescendants = true) { if (controlRole != null) role = controlRole; contentDescription = label; if (!enabled) disabled(); onClick { if (granted) { currentAction(); true } else false } }
    val gesture = if (pointers) Modifier.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!granted || down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
            state.latestOut?.cancel(); state.latestOut = null
            var active = true; var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); pressed = true }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) pressed = false else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); pressed = false }
            }
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    val inside = finger.position.x > -PRESS_RETENTION * density && finger.position.x < size.width + PRESS_RETENTION * density && finger.position.y > -PRESS_RETENTION * density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density
                    if (inside != active) { active = inside; if (inside) pressIn() else pressOut() }
                    finger.consume()
                    if (!finger.pressed) { if (active) { pressOut(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); currentAction() }; break }
                }
                if (active) { active = false; pressOut() }
            } catch (cancelled: CancellationException) { if (active) scope.launch { yield(); if (state.alive) pressOut() }; throw cancelled }
        }
    } else Modifier
    Box(modifier.then(semantics).then(gesture)) { content(pressed) }
}
