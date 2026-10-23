package com.ngocdevv.nativecook.blocks.forms.shieldpasswordstrength

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 0: too short to judge, 1: weak, 2: fair, 3: strong. */
typealias Strength = Int
/** autoPlay: one password every LOOP seconds. The glow's breath and caret blink divide it. */
const val LOOP = 10.5f
private val BurstAt = floatArrayOf(0.8f, 2.5f, 3.9f)
private const val TYPE_EVERY = 0.11f
private const val SUBMIT_AT = 6f
private const val RESET_AT = 8.7f
private const val USER_HOLD = 3f
private const val BREATH = LOOP / 5f
private const val BLINK = LOOP / 10f
private const val SHIFT = 0.9f
private const val PALE_AT = 0.28f
private const val COLOR_FROM = 0.15f
private const val COLOR_TO = 0.7f
private const val PALE_SHIELD = 0.62f
private const val PALE_GLOW = 0.7f
private const val PALE_FORM = 0.45f
private const val HINT_CLEAR = 0.25f
private const val HINT_STEP = 0.022f
private const val FORM_OUT = 0.4f
private const val GLIDE_AT = 0.45f
private const val GLIDE_FOR = 0.9f
private const val CHECK_AT = 1.5f
private const val CHECK_FOR = 0.45f
private const val POP = 0.12f
private const val SECURED_END = CHECK_AT + CHECK_FOR + 0.2f
private const val RESET_FOR = 0.75f

private val Background = Color(0xFFF5F9FC)
private val Ink = Color(0xFF1F2933)
private val Dot = Color(0xFF5A6472)
private val Icon = Color(0xFF64748B)
private val Caret = Color(0xFF8A94A0)
private val Field = Color.White
private val Hints = listOf("Enter Your Password", "Add more characters to strengthen it", "Nice, try adding a symbol", "Perfect! Strong & secure")
private val Colors = listOf(Color(0xFF2FE0EA), Color(0xFFE5173F), Color(0xFFC85A0A), Color(0xFF1FC955))
private val HintColors = listOf(Color(0xFF8A94A0), Color(0xFFE5173F), Color(0xFFC85A0A), Color(0xFF17A34A))
private val Demo = listOf("maple", "Sy7", "-river")
private const val SHIELD_WIDTH = 53f
private const val SHIELD_HEIGHT = 61f
private const val SHIELD_CORNER = 7f
private const val GLOW = 104f
private const val GLOW_ALPHA = 0.44f
private val FalloffAt = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
private val Falloff = floatArrayOf(1f, 0.976f, 0.905f, 0.794f, 0.655f, 0.5f, 0.345f, 0.206f, 0.095f, 0.024f, 0f)
private const val BREATH_BY = 0.06f
private const val SHIELD_TO_FIELD = 154f
private const val SHIELD_AT = 0.4f
private const val FIELD_WIDTH = 346f
private const val FIELD_MARGIN = 28f
private const val FIELD_HEIGHT = 40f
private const val FIELD_SHADOW_BLEED = 28f
private const val LABEL_GAP = 10f
private const val HINT_GAP = 7f
private const val TEXT_LEFT = 28f
private const val DOT_SIZE = 6f
private const val DOT_GAP = 4f
private const val ICONS = 15f
private const val HINT_HEIGHT = 14f
private const val KEYBOARD_GAP = 16f
private const val KEYHOLE_X = SHIELD_WIDTH / 2f
private const val KEYHOLE_Y = 23f
private const val KEYHOLE_RADIUS = 7.6f
private const val KEYHOLE_STEM = 5f
private const val KEYHOLE_BOTTOM = 41f
private val Check = Path().apply { moveTo(15.5f, 30.5f); lineTo(23f, 37.8f); lineTo(37.8f, 22.8f) }
private const val CHECK_STROKE = 4.2f
private const val DOT_IN_MS = 420
private val EaseOutCubic = Easing { t -> val u = 1f - t; 1f - u * u * u }

private fun smooth(t: Float, from: Float, to: Float): Float {
    val u = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}
private fun bump(t: Float, from: Float, peak: Float, to: Float) = smooth(t, from, peak) * (1f - smooth(t, peak, to))

/** Length first, then variety: five to judge, eight and two kinds for fair, twelve and three for strong. */
private fun defaultStrength(password: String): Strength {
    if (password.length < 5) return 0
    val kinds = listOf(Regex("[a-z]"), Regex("[A-Z]"), Regex("\\d"), Regex("[^A-Za-z0-9]")).count { it.containsMatchIn(password) }
    if (password.length >= 12 && kinds >= 3) return 3
    if (password.length >= 8 && kinds >= 2) return 2
    return 1
}
private enum class Phase { Form, Secured, Leaving }
private class Clocks(initial: Color) {
    val time = mutableFloatStateOf(0f)
    val shift = mutableFloatStateOf(SHIFT)
    val secured = mutableFloatStateOf(-1f)
    val leaving = mutableFloatStateOf(0f)
    var from = initial
    var to = initial
    var securedOrigin = 0L
    var leavingOrigin = 0L
    fun pale() = bump(shift.floatValue, 0f, PALE_AT, SHIFT)
    fun colour(alpha: Float = 1f): Color {
        val u = smooth(shift.floatValue, COLOR_FROM, COLOR_TO)
        return rgba(((from.red + (to.red - from.red) * u) * 255f).roundToInt(),
            ((from.green + (to.green - from.green) * u) * 255f).roundToInt(),
            ((from.blue + (to.blue - from.blue) * u) * 255f).roundToInt(), alpha)
    }
    fun mark() = smooth(secured.floatValue, CHECK_AT, CHECK_AT + CHECK_FOR) * (1f - smooth(leaving.floatValue, 0f, 0.35f))
    fun glide(still: Boolean) = if (still) 0f else smooth(secured.floatValue, GLIDE_AT, GLIDE_AT + GLIDE_FOR) * (1f - smooth(leaving.floatValue, 0.1f, 0.9f))
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Password strength changes the shield and retypes the hint; submitting a strong one secures it. */
@Composable
fun ShieldPasswordStrength(
    autoPlay: Boolean = false,
    demoPassword: List<String> = Demo,
    label: String = "Password",
    hints: List<String> = Hints,
    colors: List<Color> = Colors,
    hintColors: List<Color> = HintColors,
    strengthOf: (password: String) -> Strength = ::defaultStrength,
    onChangePassword: ((password: String, strength: Strength) -> Unit)? = null,
    onSecured: ((password: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val input = remember { FocusRequester() }
    val clocks = remember { Clocks(colors[0]) }
    val strength by rememberUpdatedState(strengthOf)
    val changed by rememberUpdatedState(onChangePassword)
    val finished by rememberUpdatedState(onSecured)
    val givenHints by rememberUpdatedState(hints)
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Form) }
    var hintLevel by remember { mutableIntStateOf(0) }
    var hintCount by remember { mutableIntStateOf(hints[0].length) }
    var shownLevel by remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    var runToken by remember { mutableIntStateOf(0) }
    var byHand by remember { mutableStateOf(false) }
    val level = strengthOf(password)

    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun dismiss() { keyboard?.hide(); focus.clearFocus() }
    fun touched() { touches++ }
    fun change(next: String) { password = next; changed?.invoke(next, strength(next)) }
    fun secure(hand: Boolean) {
        if (phase != Phase.Form || strength(password) < 3) return
        byHand = hand
        dismiss()
        clocks.securedOrigin = System.nanoTime()
        clocks.secured.floatValue = 0f
        phase = Phase.Secured
        runToken++
    }
    fun reset() {
        if (phase != Phase.Secured) return
        clocks.leavingOrigin = System.nanoTime()
        phase = Phase.Leaving
        change("")
    }
    val latestChange by rememberUpdatedState(::change)
    val latestSecure by rememberUpdatedState(::secure)
    val latestReset by rememberUpdatedState(::reset)
    LaunchedEffect(Unit) {
        val origin = System.nanoTime()
        while (true) clocks.time.floatValue = max(0f, (withFrameNanos { it } - origin) / 1e9f) % LOOP
    }
    LaunchedEffect(level, colors, hints) {
        if (shownLevel == level) return@LaunchedEffect
        clocks.from = colors[shownLevel]; clocks.to = colors[level]
        shownLevel = level
        clocks.shift.floatValue = 0f
        val origin = System.nanoTime()
        coroutineScope {
            launch {
                while (true) {
                    val t = min(SHIFT, max(0f, (withFrameNanos { it } - origin) / 1e9f))
                    clocks.shift.floatValue = t
                    if (t >= SHIFT) break
                }
            }
            delay((HINT_CLEAR * 1000).toLong())
            hintLevel = level; hintCount = 0
            for (count in 1..hints[level].length) {
                val at = origin + ((HINT_CLEAR + count * HINT_STEP) * 1e9f).toLong()
                delay(max(0L, (at - System.nanoTime()) / 1_000_000))
                hintCount = count
            }
        }
    }
    LaunchedEffect(runToken) {
        if (clocks.secured.floatValue < 0f) return@LaunchedEffect
        val origin = clocks.securedOrigin
        var checked = false
        while (true) {
            val t = min(SECURED_END, max(0f, (withFrameNanos { it } - origin) / 1e9f))
            clocks.secured.floatValue = t
            if (!checked && t >= CHECK_AT + CHECK_FOR / 2f) {
                checked = true
                if (byHand) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                finished?.invoke(password)
            }
            if (t >= SECURED_END) break
        }
    }
    LaunchedEffect(phase == Phase.Leaving) {
        if (phase != Phase.Leaving) return@LaunchedEffect
        val origin = clocks.leavingOrigin
        do { clocks.leaving.floatValue = ((withFrameNanos { it } - origin) / (RESET_FOR * 1e9f)).coerceIn(0f, 1f) } while (clocks.leaving.floatValue < 1f)
        clocks.secured.floatValue = -1f; clocks.leaving.floatValue = 0f; runToken++
        phase = Phase.Form
    }
    LaunchedEffect(autoPlay, touches, demoPassword) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (true) {
                if (phase == Phase.Secured) latestReset()
                if (phase != Phase.Form) { delay(100); continue }
                dismiss(); revealed = false
                if (password.isNotEmpty()) {
                    latestChange("")
                    delay(((HINT_CLEAR + givenHints[0].length * HINT_STEP) * 1000 + 200).toLong())
                    continue
                }
                break
            }
        }
        coroutineScope {
            var origin = System.nanoTime()
            suspend fun until(at: Long) { delay(max(0L, (at - System.nanoTime()) / 1_000_000)) }
            while (true) {
                val start = origin
                var typed = ""
                demoPassword.forEachIndexed { index, burst -> burst.forEachIndexed { place, letter ->
                    typed += letter
                    val text = typed
                    launch {
                        until(start + (((BurstAt.getOrNull(index) ?: BurstAt.last()) + place * TYPE_EVERY) * 1e9f).toLong())
                        latestChange(text)
                    }
                } }
                launch { until(start + (SUBMIT_AT * 1e9f).toLong()); latestSecure(false) }
                launch { until(start + (RESET_AT * 1e9f).toLong()); latestReset() }
                until(start + (LOOP * 1e9f).toLong()); origin += (LOOP * 1e9f).toLong()
            }
        }
    }
    val shieldPaint = remember { ShieldPaint() }
    val checkMeasure = remember { PathMeasure().apply { setPath(Check, false) } }
    val checkDrawn = remember { Path() }
    val fieldPaint = remember { FieldPaint() }
    val inForm = phase == Phase.Form
    BoxWithConstraints(modifier.fillMaxSize().background(Background).pointerInput(Unit) { detectTapGestures { dismiss() } }) {
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        val fieldWidth = min(FIELD_WIDTH, screenWidth - FIELD_MARGIN * 2f)
        val shieldY = screenHeight * SHIELD_AT
        val drop = screenHeight / 2f - shieldY
        val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp().value }
        val hintBottom = shieldY + SHIELD_TO_FIELD + FIELD_HEIGHT / 2f + HINT_GAP + HINT_HEIGHT
        val liftTarget = if (imeHeight > 0f) min(0f, screenHeight - imeHeight - KEYBOARD_GAP - hintBottom) else 0f
        val lift by animateFloatAsState(liftTarget, if (still) snap() else tween(250, easing = EaseOutCubic), label = "keyboard lift")
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = lift.dp.toPx() }) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, w, _ ->
                    val center = Offset(w / 2f, shieldY)
                    val pale = clocks.pale()
                    val color = clocks.colour()
                    val breath = if (still) 0f else BREATH_BY * sin((2f * Math.PI).toFloat() * clocks.time.floatValue / BREATH)
                    val glide = clocks.glide(still)
                    val glow = Brush.radialGradient(*Array(Falloff.size) { FalloffAt[it] to color.copy(alpha = Falloff[it] * GLOW_ALPHA * (1f - PALE_GLOW * pale)) }, center = center, radius = GLOW)
                    withTransform({ translate(0f, drop * glide); scale(1f + breath, 1f + breath, pivot = center) }) { drawCircle(glow, GLOW, center) }
                    val secured = clocks.secured.floatValue
                    val leaving = clocks.leaving.floatValue
                    val swap = bump(secured, CHECK_AT - 0.1f, CHECK_AT + 0.12f, CHECK_AT + CHECK_FOR) * if (leaving > 0f) 0f else 1f
                    val pop = if (still) 0f else POP * bump(secured, CHECK_AT, CHECK_AT + CHECK_FOR * 0.45f, CHECK_AT + CHECK_FOR + 0.15f)
                    withTransform({ translate(0f, drop * glide); scale(1f + pop, 1f + pop, pivot = center); translate(center.x - SHIELD_WIDTH / 2f, shieldY - SHIELD_HEIGHT / 2f) }) {
                        drawIntoCanvas { shieldPaint.draw(it.nativeCanvas, clocks.colour(1f - PALE_SHIELD * max(pale, 0.75f * swap))) }
                        val mark = clocks.mark()
                        val keyOpacity = 1f - smooth(mark, 0f, 0.5f)
                        drawCircle(Color.White, KEYHOLE_RADIUS, Offset(KEYHOLE_X, KEYHOLE_Y), alpha = keyOpacity)
                        drawRoundRect(Color.White, Offset(KEYHOLE_X - KEYHOLE_STEM / 2f, KEYHOLE_Y), Size(KEYHOLE_STEM, KEYHOLE_BOTTOM - KEYHOLE_Y), CornerRadius(KEYHOLE_STEM / 2f), alpha = keyOpacity)
                        checkDrawn.rewind(); checkMeasure.getSegment(0f, checkMeasure.length * mark, checkDrawn, true)
                        drawPath(checkDrawn, Color.White, alpha = smooth(mark, 0.3f, 0.6f), style = Stroke(CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            if (phase == Phase.Secured) Box(Modifier.offset(x = (screenWidth / 2f - SHIELD_WIDTH / 2f - 16f).dp,
                y = (screenHeight / 2f - SHIELD_HEIGHT / 2f - 16f).dp).size((SHIELD_WIDTH + 32f).dp, (SHIELD_HEIGHT + 32f).dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { touched(); reset() }
                .semantics { contentDescription = "Change password" })
            Column(Modifier.align(Alignment.TopCenter).offset(y = (shieldY + SHIELD_TO_FIELD - FIELD_HEIGHT / 2f).dp).width(fieldWidth.dp)
                .graphicsLayer {
                    // The label sits above this layer and the field shadow spills outside it.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    val secured = clocks.secured.floatValue
                    val leaving = clocks.leaving.floatValue
                    alpha = if (secured < 0f) 1f else if (leaving > 0f) smooth(leaving, 0.5f, 1f) else 1f - smooth(secured, 0f, FORM_OUT)
                }.then(if (inForm) Modifier else Modifier.clearAndSetSemantics {})) {
                Box(Modifier.fillMaxWidth().height(FIELD_HEIGHT.dp).graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = 1f - PALE_FORM * clocks.pale()
                }.pointerInput(inForm) { if (inForm) detectTapGestures { input.requestFocus() } }) {
                    BasicText(label, style = textStyle(13f, 17f, FontWeight.Bold, Ink), modifier = Modifier.offset(y = (-LABEL_GAP - 17f).dp))
                    Canvas(Modifier.fillMaxSize().bleed(FIELD_SHADOW_BLEED.dp)) {
                        surface(FIELD_SHADOW_BLEED.dp) { x, y, _, _ ->
                            drawIntoCanvas { fieldPaint.draw(it.nativeCanvas, x, y, fieldWidth) }
                            drawRoundRect(Field, Offset(x, y), Size(fieldWidth, FIELD_HEIGHT), CornerRadius(FIELD_HEIGHT / 2f))
                            if (level == 3) drawRoundRect(colors[3].copy(alpha = 0x73 / 255f), Offset(x + 0.5f, y + 0.5f), Size(fieldWidth - 1f, FIELD_HEIGHT - 1f), CornerRadius((FIELD_HEIGHT - 1f) / 2f), style = Stroke(1f))
                        }
                    }
                    // Draw what the real field holds, with a custom caret that also blinks at rest.
                    Row(Modifier.offset(x = TEXT_LEFT.dp).width((fieldWidth - 83f).dp).height(FIELD_HEIGHT.dp).clipToBounds(),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DOT_GAP.dp)) {
                        if (revealed) BasicText(password, style = textStyle(14f, 18f, FontWeight.Medium, Dot), maxLines = 1)
                        else for (index in password.indices) PasswordDot(index, still)
                        if (inForm && (focused || password.isEmpty())) Box(Modifier.size(1.5.dp, 15.dp)
                            .graphicsLayer { alpha = if (clocks.time.floatValue % BLINK < BLINK / 2f) 1f else 0f }.background(Caret, CircleShape))
                    }
                    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.Transparent, Color.Transparent)) {
                        BasicTextField(password, onValueChange = { touched(); change(it.take(24)) }, enabled = inForm,
                            modifier = Modifier.offset(x = TEXT_LEFT.dp).width((fieldWidth - TEXT_LEFT - 72f).dp).height(FIELD_HEIGHT.dp)
                                .focusRequester(input).onFocusChanged { if (it.isFocused && !focused) touched(); focused = it.isFocused }
                                .semantics { contentDescription = label; contentType = ContentType.NewPassword },
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 14.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            cursorBrush = SolidColor(Color.Transparent), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { dismiss(); touched(); secure(true) }),
                            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } })
                    }
                    Box(Modifier.align(Alignment.CenterEnd).offset(x = (-29).dp).size(26.dp, FIELD_HEIGHT.dp)) {
                        EyeIcon(revealed, Modifier.align(Alignment.Center).size(ICONS.dp))
                        Box(Modifier.matchParentSize().bleed(8.dp).clickable(remember { MutableInteractionSource() }, indication = null, enabled = inForm, role = Role.Button) { touched(); revealed = !revealed }
                            .semantics { contentDescription = if (revealed) "Hide password" else "Show password" })
                    }
                    LockIcon(Modifier.align(Alignment.CenterEnd).offset(x = (-14).dp).size((ICONS - 1f).dp))
                }
                Spacer(Modifier.height(HINT_GAP.dp))
                BasicText(hints[hintLevel].take(hintCount), style = textStyle(10f, HINT_HEIGHT, FontWeight.Normal, hintColors[hintLevel]), maxLines = 1,
                    modifier = Modifier.height(HINT_HEIGHT.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
private fun PasswordDot(index: Int, still: Boolean) {
    val age = remember(index) { mutableFloatStateOf(if (still) 1f else 0f) }
    LaunchedEffect(index) {
        if (still) return@LaunchedEffect
        val origin = System.nanoTime()
        do { age.floatValue = ((withFrameNanos { it } - origin) / (DOT_IN_MS * 1e6f)).coerceIn(0f, 1f) } while (age.floatValue < 1f)
    }
    Box(Modifier.size(DOT_SIZE.dp).graphicsLayer {
        val u = age.floatValue
        alpha = if (u < 0.35f) 0.15f + 0.2f * u / 0.35f else 0.35f + 0.65f * (u - 0.35f) / 0.65f
        scaleX = if (u < 0.35f) 0.5f + 0.5f * u / 0.35f else 1f; scaleY = scaleX
    }.background(Dot, CircleShape))
}

private class ShieldPaint {
    private val path = android.graphics.Path().apply {
        moveTo(SHIELD_WIDTH / 2f, 0f); lineTo(SHIELD_WIDTH, 3.5f); lineTo(SHIELD_WIDTH, 40f)
        lineTo(SHIELD_WIDTH / 2f, SHIELD_HEIGHT); lineTo(0f, 40f); lineTo(0f, 3.5f); close()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { pathEffect = CornerPathEffect(SHIELD_CORNER) }
    fun draw(canvas: android.graphics.Canvas, color: Color) { paint.color = color.toArgb(); canvas.drawPath(path, paint) }
}
private class FieldPaint {
    private val broad = SoftRoundRect(rgba(100, 116, 139, 0.14f), 7f)
    private val close = SoftRoundRect(rgba(100, 116, 139, 0.08f), 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(100, 116, 139, 1f).toArgb() }
    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Float) {
        if (Build.VERSION.SDK_INT >= 28) {
            broad.draw(canvas, x, y + 4f, width, FIELD_HEIGHT, FIELD_HEIGHT / 2f)
            close.draw(canvas, x, y + 1f, width, FIELD_HEIGHT, FIELD_HEIGHT / 2f)
        } else {
            for (layer in 6 downTo 1) {
                val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
                val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
                for ((sigma, dy, alpha) in listOf(Triple(7f, 4f, 0.14f), Triple(1f, 1f, 0.08f))) {
                    val spread = sigma * outer
                    paint.alpha = (alpha * weight * 255f).toInt()
                    canvas.drawRoundRect(x - spread, y + dy - spread, x + width + spread, y + dy + FIELD_HEIGHT + spread, FIELD_HEIGHT / 2f + spread, FIELD_HEIGHT / 2f + spread, paint)
                }
            }
        }
    }
}

@Composable
private fun EyeIcon(revealed: Boolean, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, w, _ ->
            val k = w / 24f
            scale(k, k, pivot = Offset.Zero) {
                val eye = Path().apply { moveTo(2f, 12f); cubicTo(6f, 5f, 18f, 5f, 22f, 12f); cubicTo(18f, 19f, 6f, 19f, 2f, 12f); close() }
                drawPath(eye, Icon, style = Stroke(2f, join = StrokeJoin.Round))
                drawCircle(Icon, 3f, Offset(12f, 12f))
                if (!revealed) drawLine(Icon, Offset(3f, 3f), Offset(21f, 21f), 2f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LockIcon(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, w, _ ->
            val k = w / 24f
            scale(k, k, pivot = Offset.Zero) {
                drawRoundRect(Icon, Offset(5f, 10f), Size(14f, 12f), CornerRadius(2f))
                val arch = Path().apply { moveTo(8f, 11f); lineTo(8f, 7f); cubicTo(8f, 1f, 16f, 1f, 16f, 7f); lineTo(16f, 11f) }
                drawPath(arch, Icon, style = Stroke(2f, cap = StrokeCap.Round))
            }
        }
    }
}
