package com.ngocdevv.nativecook.blocks.forms.neonotpverify

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** autoPlay: one verification every LOOP seconds. */
const val LOOP = 6.9f
private const val TYPE_AT = 0.8f
private const val TYPE_EVERY = 0.32f
private const val KEY_FOR = 0.13f
private const val RESET_AFTER = 3.6f
private const val USER_HOLD = 3f
private const val TRACE_AT = 0.15f
private const val TRACE_FOR = 0.45f
private const val LIFT_AT = TRACE_AT + TRACE_FOR
private const val LIFT_FOR = 0.12f
private const val MERGE_AT = 0.74f
private const val DECK_FOR = 0.18f
private const val CLOSE_FOR = 0.12f
private const val TILE_AT = MERGE_AT + DECK_FOR + CLOSE_FOR - 0.06f
private const val TILE_IN = 0.08f
private const val POP_FOR = 0.14f
private const val CHECK_FOR = 0.22f
private const val TEXT_OUT = 0.2f
private const val TITLE_AFTER = 0.08f
private const val TITLE_IN = 0.3f
private const val SETTLED = 2.2f
private const val RESET_FOR = 0.5f
private const val SHAKE_AFTER = 0.1f
private val Shake = floatArrayOf(-10f, 9f, -7f, 5f, -2f, 0f)
private const val SHAKE_STEP = 0.065f
private const val WRONG_FOR = 0.62f

private val Screen = Color(0xFF0B0B0C)
private val Sheet = Color(0xFF1B1B1D)
private val Grabber = Color(0xFF5A5A5E)
private val Ink = Color.White
private val Mute = Color(0xFF7C7C82)
private val KeyInk = Color(0xFFF2F2F4)
private val BoxFill = Color(0xFF1F1F21)
private val BoxBorder = Color(0xFF2E2E31)
private val TileFill = Color(0xFF24120F)
private val Accent = Color(0xFFE0452A)
private val Neon = Color(0xFFFF5A2A)
private val Hot = Color(0xFFFFD9CB)
private val Warm = Color(0xFFFF9C7A)

private const val SHEET_RADIUS = 28f
private const val GRABBER_TOP = 13f
private const val TITLE_TOP = 52f
private const val SUBTITLE_TOP = 97f
private const val ROW_TOP = 184f
private const val RESEND_TOP = 289f
private const val PAD_TOP = 326f
private const val SUCCESS_TOP = 83f
private const val BOX = 70f
private const val BOX_RADIUS = 17f
private const val BOX_GAP = 15f
private const val ROW_MARGIN = 24f
private const val KEY_ROW = 54f
private const val KEY_ROW_MIN = 44f
private const val KEY_FLASH_WIDTH = 92f
private const val KEY_FLASH_HEIGHT = 46f
private val Keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "back"))
private const val LIFT = 0.08f
private val Lean = floatArrayOf(-2.5f, 1.5f, -1.5f, 2.5f)
private const val DECK_STEP = 15f
private const val DECK_TURN = -44f
private const val DECK_FRONT = 0.08f
private const val PERSPECTIVE = 500f
private const val SPILL = 30f
private const val HEAD = 0.42f
private const val TILE_ENTER = 1f + LIFT + DECK_FRONT
private const val TILE_BURST = 1.4f
private const val TILE_SPRING_OMEGA = 10f
private const val TILE_SPRING_ZETA = 0.6f
private const val TILE_CANVAS = 280f
private val Check = Path().apply { moveTo(-12f, 1.5f); lineTo(-4f, 9.5f); lineTo(13f, -9.5f) }
private const val HAZE_CANVAS = 360f
private const val HAZE_ROW_RX = 0.56f
private const val HAZE_ROW_RY = 124f
private const val HAZE_ROW_DROP = 28f
private const val HAZE_ROW_ALPHA = 0.4f
private const val HAZE_TILE_R = 150f
private const val HAZE_TILE_BURST = 0.36f
private const val HAZE_TILE_REST = 0.17f
private val FalloffAt = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
private val Falloff = floatArrayOf(1f, 0.976f, 0.905f, 0.794f, 0.655f, 0.5f, 0.345f, 0.206f, 0.095f, 0.024f, 0f)
private val Embers = listOf(
    floatArrayOf(-27f, 0f, -12f, 14f, 1.3f), floatArrayOf(-10f, 0.07f, -4f, 26f, 1f),
    floatArrayOf(6f, 0.03f, 5f, 18f, 1.5f), floatArrayOf(23f, 0.1f, 13f, 12f, 1.1f),
    floatArrayOf(-18f, 0.16f, -7f, 30f, 0.9f), floatArrayOf(15f, 0.2f, 8f, 24f, 1.2f),
)
private const val EMBER_FROM = 56f
private const val EMBER_GRAVITY = 110f
private const val EMBER_FOR = 1f
private const val DIGIT_IN_MS = 170
private const val FOCUS_IN_MS = 140
private const val FOCUS_BLUR_RADIUS = 12f
private const val KEY_ON_MS = 40
private const val KEY_OFF_MS = 320

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)
private fun smooth(t: Float, from: Float, to: Float): Float {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

/** What is left of a spring's way after it was let go at rest. */
private fun springLeft(t: Float): Float {
    if (t <= 0f) return 1f
    val damped = TILE_SPRING_OMEGA * sqrt(1f - TILE_SPRING_ZETA * TILE_SPRING_ZETA)
    return exp(-TILE_SPRING_ZETA * TILE_SPRING_OMEGA * t) * (cos(damped * t) + TILE_SPRING_ZETA * TILE_SPRING_OMEGA / damped * sin(damped * t))
}

private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val DigitEase = Easing { t -> val u = t - 1f; 1f + u * u * (3f * u + 2f) }
private enum class Status { Idle, Wrong, Verifying, Verified, Leaving }
private class Clocks {
    val clock = mutableFloatStateOf(-1f)
    val away = mutableFloatStateOf(0f)
    val shake = mutableFloatStateOf(0f)
    var verificationOrigin = 0L
    var resetOrigin = 0L
    var wrongOrigin = 0L
}
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val TitleStyle = textStyle(27f, 34f, FontWeight.SemiBold, Ink, -0.3f)
private val SubtitleStyle = textStyle(14f, 24f, FontWeight.Normal, Mute)
private val DigitStyle = textStyle(25f, 30f, FontWeight.Medium, Ink)
private val ResendPromptStyle = textStyle(14f, 20f, FontWeight.Normal, Mute)
private val ResendStyle = textStyle(14f, 20f, FontWeight.SemiBold, Ink)
private val KeyStyle = textStyle(26f, 32f, FontWeight.Normal, KeyInk)
private val TitleSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 27.sp, stepSize = 0.25.sp)

/** A phone verification sheet filled with its own number pad. Nothing is sent anywhere. */
@Composable
fun NeonOtpVerify(
    code: String = "6988",
    autoPlay: Boolean = false,
    title: String = "Let’s verify your number",
    subtitle: String = "We’ve sent a 4-digit code to your phone.\nIt’ll auto-verify once entered.",
    resendPrompt: String = "Didn’t receive the code?",
    resendLabel: String = "Resend",
    successTitle: String = "Verified successfully",
    accent: Color = Accent,
    neon: Color = Neon,
    onVerified: ((code: String) -> Unit)? = null,
    onRejected: ((code: String) -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val clocks = remember { Clocks() }
    val expected by rememberUpdatedState(code)
    val verified by rememberUpdatedState(onVerified)
    val rejected by rememberUpdatedState(onRejected)
    var digits by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Status.Idle) }
    var autoKey by remember { mutableStateOf<String?>(null) }
    var touches by remember { mutableIntStateOf(0) }
    var runToken by remember { mutableIntStateOf(0) }
    var byHand by remember { mutableStateOf(false) }

    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun notify(success: Boolean) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) {
            if (success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else HapticFeedbackConstants.CONTEXT_CLICK)
    }
    fun touched() { autoKey = null; touches++ }
    fun reset() { clocks.resetOrigin = System.nanoTime(); digits = ""; status = Status.Leaving }
    fun enter(key: String, hand: Boolean) {
        if (status != Status.Idle) return
        if (key == "back") { digits = digits.dropLast(1); return }
        if (digits.length >= expected.length) return
        val entered = digits + key
        digits = entered
        if (entered.length != expected.length) return
        byHand = hand
        if (entered == expected) {
            clocks.verificationOrigin = System.nanoTime()
            clocks.clock.floatValue = 0f
            status = Status.Verifying
            runToken++
        } else {
            clocks.wrongOrigin = System.nanoTime()
            status = Status.Wrong
            if (hand) notify(false)
            rejected?.invoke(entered)
        }
    }
    val latestEnter by rememberUpdatedState(::enter)
    val latestReset by rememberUpdatedState(::reset)

    LaunchedEffect(runToken) {
        if (clocks.clock.floatValue < 0f) return@LaunchedEffect
        val origin = clocks.verificationOrigin
        var arrived = false
        while (true) {
            val t = min(SETTLED, (withFrameNanos { it } - origin) / 1e9f)
            clocks.clock.floatValue = t
            if (!arrived && t >= TILE_AT) {
                arrived = true
                status = Status.Verified
                if (byHand) notify(true)
                verified?.invoke(expected)
            }
            if (t >= SETTLED) break
        }
    }
    LaunchedEffect(status == Status.Leaving) {
        if (status != Status.Leaving) return@LaunchedEffect
        val origin = clocks.resetOrigin
        do {
            val a = clamp01((withFrameNanos { it } - origin) / (RESET_FOR * 1e9f))
            clocks.away.floatValue = a
        } while (a < 1f)
        clocks.clock.floatValue = -1f
        clocks.away.floatValue = 0f
        runToken++
        status = Status.Idle
    }
    LaunchedEffect(status == Status.Wrong) {
        if (status != Status.Wrong) return@LaunchedEffect
        val origin = clocks.wrongOrigin
        while (true) {
            val t = (withFrameNanos { it } - origin) / 1e9f
            if (!still && t >= SHAKE_AFTER) {
                val step = ((t - SHAKE_AFTER) / SHAKE_STEP).toInt()
                if (step < Shake.size) {
                    val u = clamp01((t - SHAKE_AFTER - step * SHAKE_STEP) / SHAKE_STEP)
                    val eased = if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f
                    val from = if (step == 0) 0f else Shake[step - 1]
                    clocks.shake.floatValue = from + (Shake[step] - from) * eased
                } else clocks.shake.floatValue = 0f
            }
            if (t >= WRONG_FOR) break
        }
        digits = ""
        status = Status.Idle
    }
    LaunchedEffect(autoPlay, touches, code) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (true) {
                if (status == Status.Verified) latestReset()
                if (status != Status.Idle) { delay(100); continue }
                if (digits.isNotEmpty()) { digits = ""; delay(100); continue }
                break
            }
        }
        coroutineScope {
            var origin = System.nanoTime()
            suspend fun until(at: Long) { delay(max(0L, (at - System.nanoTime()) / 1_000_000)) }
            while (true) {
                val start = origin
                code.forEachIndexed { index, key ->
                    launch {
                        until(start + ((TYPE_AT + index * TYPE_EVERY) * 1e9f).toLong())
                        autoKey = key.toString()
                        latestEnter(key.toString(), false)
                        until(start + ((TYPE_AT + index * TYPE_EVERY + KEY_FOR) * 1e9f).toLong())
                        autoKey = null
                    }
                }
                launch {
                    until(start + ((TYPE_AT + (code.length - 1) * TYPE_EVERY + RESET_AFTER) * 1e9f).toLong())
                    latestReset()
                }
                until(start + (LOOP * 1e9f).toLong())
                origin += (LOOP * 1e9f).toLong()
            }
        }
    }

    fun Modifier.askMotion() = graphicsLayer {
        val a = clocks.away.floatValue
        val out = smooth(clocks.clock.floatValue, LIFT_AT, LIFT_AT + TEXT_OUT)
        alpha = if (a > 0f) smooth(a, 0.5f, 1f) else 1f - out
        translationY = if (a > 0f || still) 0f else -6.dp.toPx() * out
    }
    val idle = status == Status.Idle
    BoxWithConstraints(modifier.fillMaxSize().background(Screen)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val bottom = max(insets.calculateBottomPadding().value, 16f) + 6f
        val keyRow = ((maxHeight.value - insets.calculateTopPadding().value - 24f - PAD_TOP - bottom) / Keys.size).coerceIn(KEY_ROW_MIN, KEY_ROW)
        val count = code.length
        val box = min(BOX, (maxWidth.value - ROW_MARGIN * 2f - BOX_GAP * (count - 1)) / count)
        val rowCentre = ROW_TOP + BOX / 2f
        val width = maxWidth.value
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((PAD_TOP + keyRow * Keys.size + bottom).dp)
            .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)).background(Sheet)) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = GRABBER_TOP.dp).size(40.dp, 5.dp).background(Grabber, RoundedCornerShape(2.5.dp)))
            Column(Modifier.offset(y = TITLE_TOP.dp).fillMaxWidth().padding(horizontal = 24.dp).askMotion()
                .then(if (idle) Modifier else Modifier.clearAndSetSemantics {})) {
                BasicText(title, modifier = Modifier.fillMaxWidth(), style = TitleStyle, maxLines = 1, autoSize = TitleSize)
                BasicText(subtitle, modifier = Modifier.padding(top = (SUBTITLE_TOP - TITLE_TOP - 34f).dp).fillMaxWidth(), style = SubtitleStyle)
            }
            BasicText(successTitle, style = TitleStyle, maxLines = 1, autoSize = TitleSize,
                modifier = Modifier.offset(y = SUCCESS_TOP.dp).fillMaxWidth().padding(horizontal = 24.dp).graphicsLayer {
                    val shown = smooth(clocks.clock.floatValue, MERGE_AT + TITLE_AFTER, MERGE_AT + TITLE_AFTER + TITLE_IN)
                    alpha = if (clocks.clock.floatValue < 0f) 0f else shown * (1f - smooth(clocks.away.floatValue, 0f, 0.5f))
                    translationY = if (still) 0f else 10.dp.toPx() * (1f - shown)
                }.then(if (status == Status.Verified) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics {}))
            Haze(clocks, still, neon, width, Modifier.offset(y = (rowCentre - HAZE_CANVAS / 2f).dp))
            Box(Modifier.offset(y = ROW_TOP.dp).fillMaxWidth().height(BOX.dp).graphicsLayer { translationX = clocks.shake.floatValue.dp.toPx() }
                .semantics(mergeDescendants = true) { contentDescription = "Verification code"; stateDescription = digits.toCharArray().joinToString(" ") }) {
                // Draw back to front so the first box is in front of the fanned deck.
                for (index in (0 until count).reversed()) {
                    val home = (index - (count - 1) / 2f) * (box + BOX_GAP)
                    CodeBox(index, count, box, digits.getOrNull(index)?.toString() ?: "", idle && index == digits.length,
                        status == Status.Wrong, accent, neon, clocks, still,
                        Modifier.align(Alignment.Center).offset(x = home.dp))
                }
            }
            Tile(clocks, still, neon, box, Modifier.align(Alignment.TopCenter).offset(y = (rowCentre - TILE_CANVAS / 2f).dp))
            Row(Modifier.offset(y = RESEND_TOP.dp).fillMaxWidth().askMotion(), horizontalArrangement = Arrangement.Center) {
                BasicText("$resendPrompt ", style = ResendPromptStyle)
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box {
                    BasicText(resendLabel, style = ResendStyle, modifier = Modifier.graphicsLayer { alpha = if (pressed) 0.6f else 1f })
                    Box(Modifier.matchParentSize().bleed(12.dp).clickable(interaction, indication = null, enabled = idle, role = Role.Button) {
                        touched(); tick(); if (status == Status.Idle) digits = ""; onResend?.invoke()
                    }.semantics { contentDescription = resendLabel })
                }
            }
            Column(Modifier.offset(y = PAD_TOP.dp).fillMaxWidth().padding(horizontal = 20.dp).graphicsLayer {
                val t = clocks.clock.floatValue
                val a = clocks.away.floatValue
                val dim = if (t < 0f) 0f else if (a > 0f) 1f - smooth(a, 0.5f, 1f) else smooth(t, TRACE_AT, LIFT_AT)
                alpha = 1f - 0.74f * dim
            }) {
                for (row in Keys) Row(Modifier.fillMaxWidth()) {
                    for (key in row) Key(key, keyRow, autoKey == key, idle, Modifier.weight(1f)) { touched(); tick(); enter(key, true) }
                }
            }
        }
    }
}

/** RN CSS transitions keep their clock when system motion is reduced. */
@Composable
private fun cssTween(target: Float, milliseconds: Int): State<Float> {
    val value = remember { mutableFloatStateOf(target) }
    LaunchedEffect(target, milliseconds) {
        val from = value.floatValue
        val origin = System.nanoTime()
        do {
            val u = ((withFrameNanos { it } - origin) / (milliseconds * 1e6f)).coerceIn(0f, 1f)
            value.floatValue = from + (target - from) * CssEase.transform(u)
        } while (u < 1f)
    }
    return value
}

@Composable
private fun cssColorTween(target: Color, milliseconds: Int): State<Color> {
    val value = remember { mutableStateOf(target) }
    LaunchedEffect(target, milliseconds) {
        val from = value.value
        val origin = System.nanoTime()
        do {
            val u = ((withFrameNanos { it } - origin) / (milliseconds * 1e6f)).coerceIn(0f, 1f)
            val t = CssEase.transform(u)
            value.value = Color(from.red + (target.red - from.red) * t, from.green + (target.green - from.green) * t,
                from.blue + (target.blue - from.blue) * t, from.alpha + (target.alpha - from.alpha) * t)
        } while (u < 1f)
    }
    return value
}

@Composable
private fun CodeBox(index: Int, count: Int, size: Float, digit: String, focused: Boolean, wrong: Boolean,
                    accent: Color, neon: Color, clocks: Clocks, still: Boolean, modifier: Modifier) {
    val radius = BOX_RADIUS * size / BOX
    val home = (index - (count - 1) / 2f) * (size + BOX_GAP)
    val focus by cssTween(if (focused) 1f else 0f, FOCUS_IN_MS)
    val border by cssColorTween(if (focused || wrong) accent else BoxBorder, FOCUS_IN_MS)
    val paint = remember(size, neon, accent) { BoxPaint(size, radius, neon, accent) }
    Box(modifier.size(size.dp).graphicsLayer {
        // The spilling light must remain outside the box while the deck fades.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        val t = clocks.clock.floatValue
        val a = clocks.away.floatValue
        if (a > 0f) {
            val back = smooth(a, 0.5f, 1f)
            alpha = back; scaleX = 0.92f + 0.08f * back; scaleY = scaleX
            translationX = 0f; rotationZ = 0f
        } else {
            val lift = if (still) 0f else smooth(t, LIFT_AT, LIFT_AT + LIFT_FOR)
            val deck = if (still) 0f else smooth(t, MERGE_AT, MERGE_AT + DECK_FOR)
            val close = if (still) 0f else smooth(t, MERGE_AT + DECK_FOR, MERGE_AT + DECK_FOR + CLOSE_FOR)
            val rank = index - (count - 1) / 2f
            translationX = ((rank * DECK_STEP * (1f - close) - home) * deck).dp.toPx()
            alpha = if (t < 0f) 1f else 1f - smooth(t, TILE_AT, TILE_AT + TILE_IN)
            scaleX = 1f + LIFT * lift + if (index == 0) DECK_FRONT * deck else 0f
            scaleY = scaleX; rotationZ = Lean[index % Lean.size] * lift * (1f - close)
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val t = clocks.clock.floatValue
            val deck = if (still) 0f else smooth(t, MERGE_AT, MERGE_AT + DECK_FOR)
            val close = if (still) 0f else smooth(t, MERGE_AT + DECK_FOR, MERGE_AT + DECK_FOR + CLOSE_FOR)
            rotationY = if (index == 0 || clocks.away.floatValue > 0f) 0f else DECK_TURN * deck * (1f - close)
            transformOrigin = TransformOrigin(1f, 0.5f)
            cameraDistance = PERSPECTIVE.dp.toPx()
        }) {
            Canvas(Modifier.fillMaxSize().bleed(SPILL.dp)) {
                surface(SPILL.dp) { x, y, _, _ ->
                    val t = clocks.clock.floatValue
                    val end = if (still) 1f else clamp01((t - TRACE_AT) / TRACE_FOR)
                    val lit = if (t < TRACE_AT || clocks.away.floatValue > 0f) 0f else if (still) smooth(t, TRACE_AT, TRACE_AT + TRACE_FOR) else 1f
                    val body = lit * (0.5f + 0.5f * smooth(end, 0.82f, 1f))
                    val head = lit * (1f - smooth(end, 0.9f, 1f))
                    val inside = lit * smooth(t, TRACE_AT + TRACE_FOR * 0.45f, LIFT_AT) * (1f - 0.55f * smooth(t, LIFT_AT, MERGE_AT))
                    drawIntoCanvas { paint.focus.draw(it.nativeCanvas, x, y, size, size, radius, focus) }
                    drawRoundRect(BoxFill, Offset(x, y), Size(size, size), CornerRadius(radius))
                    drawRoundRect(border, Offset(x + 0.75f, y + 0.75f), Size(size - 1.5f, size - 1.5f), CornerRadius(radius - 0.75f), style = Stroke(1.5f))
                    drawRoundRect(Brush.verticalGradient(0f to neon.copy(alpha = 0x14 / 255f), 0.5f to neon.copy(alpha = 0x2E / 255f), 1f to neon.copy(alpha = 0x8C / 255f), startY = y, endY = y + size),
                        Offset(x, y), Size(size, size), CornerRadius(radius), alpha = inside)
                    drawIntoCanvas { paint.trace(it.nativeCanvas, end, lit, body, head) }
                }
            }
            val entered = remember(digit) { mutableFloatStateOf(if (digit.isEmpty() || still) 1f else 0f) }
            LaunchedEffect(digit) {
                if (digit.isEmpty() || still) return@LaunchedEffect
                val origin = withFrameNanos { it }
                do { entered.floatValue = clamp01((withFrameNanos { it } - origin) / (DIGIT_IN_MS * 1e6f)) } while (entered.floatValue < 1f)
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val t = clocks.clock.floatValue
                val dim = if (t < 0f || clocks.away.floatValue > 0f || index == 0) 0f else 0.5f * smooth(t, LIFT_AT, MERGE_AT)
                val gone = if (t < 0f || clocks.away.floatValue > 0f) 0f else smooth(t, MERGE_AT, MERGE_AT + DECK_FOR)
                val u = DigitEase.transform(entered.floatValue)
                alpha = (1f - dim) * (1f - gone) * u.coerceIn(0f, 1f)
                scaleX = (1f - 0.5f * gone) * (0.6f + 0.4f * u); scaleY = scaleX
            }, contentAlignment = Alignment.Center) { if (digit.isNotEmpty()) BasicText(digit, style = DigitStyle) }
        }
    }
}

/** Native mask filters keep the blurred strokes cheap and preserve the trace's exact path. */
private class BoxPaint(size: Float, radius: Float, neon: Color, accent: Color) {
    // CSS box-shadow blurRadius is twice a Gaussian sigma.
    val focus = SoftRect(accent.copy(alpha = 0.4f), FOCUS_BLUR_RADIUS / 2f)
    private val path = roundedOutline(size - 1.5f, radius - 0.75f, SPILL + 0.75f)
    private val measure = android.graphics.PathMeasure(path, false)
    private val segment = android.graphics.Path()
    private val headSegment = android.graphics.Path()
    private val body = stroke(neon, 6f, 9f)
    private val halo = stroke(neon, 7f, 12f)
    private val core = stroke(neon, 2f)
    private val head = stroke(Hot, 2f, 0.6f)
    fun trace(canvas: android.graphics.Canvas, end: Float, lit: Float, strength: Float, hot: Float) {
        segment.reset(); measure.getSegment(0f, measure.length * end, segment, true)
        headSegment.reset(); measure.getSegment(measure.length * max(0f, end - HEAD), measure.length * end, headSegment, true)
        body.draw(canvas, segment, strength); halo.draw(canvas, headSegment, hot)
        core.draw(canvas, segment, lit); head.draw(canvas, headSegment, hot)
    }
}

private fun stroke(color: Color, width: Float, blur: Float = 0f) = NeonStroke(color, width, blur)

/** Old hardware canvases cannot blur masks, so nested translucent strokes keep a soft falloff. */
private class NeonStroke(color: Color, private val width: Float, private val sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); style = Paint.Style.STROKE; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        if (sigma > 0f && Build.VERSION.SDK_INT >= 28) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    fun draw(canvas: android.graphics.Canvas, path: android.graphics.Path, alpha: Float) {
        if (Build.VERSION.SDK_INT >= 28 || sigma <= 0f) {
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
            return
        }
        val peak = min(1f, width / (sqrt((2f * Math.PI).toFloat()) * sigma))
        for (layer in 6 downTo 1) {
            val outer = layer * 0.5f
            val inner = (layer - 1) * 0.5f
            val weight = exp(-inner * inner / 2f) - exp(-outer * outer / 2f)
            paint.strokeWidth = width + 2f * outer * sigma
            paint.alpha = (alpha * peak * weight * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
        }
    }
}

private class SoftRect(color: Color, private val sigma: Float) {
    private val soft = SoftRoundRect(color, sigma)
    private val opacity = color.alpha
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.copy(alpha = 1f).toArgb() }
    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) {
        if (Build.VERSION.SDK_INT >= 28) { soft.draw(canvas, x, y, w, h, r, alpha); return }
        for (layer in 6 downTo 1) {
            val outer = layer * 0.5f
            val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            val spread = outer * sigma
            paint.alpha = (alpha * opacity * weight * 255f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(x - spread, y - spread, x + w + spread, y + h + spread, r + spread, r + spread, paint)
        }
    }
}

/** A rounded square beginning at the middle of its top edge, clockwise. */
private fun roundedOutline(size: Float, radius: Float, origin: Float) = android.graphics.Path().apply {
    val a = origin; val b = origin + size; val r = radius
    moveTo(a + size / 2f, a); lineTo(b - r, a)
    arcTo(b - r * 2f, a, b, a + r * 2f, -90f, 90f, false); lineTo(b, b - r)
    arcTo(b - r * 2f, b - r * 2f, b, b, 0f, 90f, false); lineTo(a + r, b)
    arcTo(a, b - r * 2f, a + r * 2f, b, 90f, 90f, false); lineTo(a, a + r)
    arcTo(a, a, a + r * 2f, a + r * 2f, 180f, 90f, false); close()
}

@Composable
private fun Haze(clocks: Clocks, still: Boolean, neon: Color, width: Float, modifier: Modifier) {
    val brush = remember(neon) { Brush.radialGradient(*Array(Falloff.size) { FalloffAt[it] to neon.copy(alpha = Falloff[it]) }, center = Offset.Zero, radius = 1f) }
    Canvas(modifier.fillMaxWidth().height(HAZE_CANVAS.dp)) {
        surface(0.dp) { _, _, _, _ ->
            val t = clocks.clock.floatValue
            if (t < 0f) return@surface
            val g = smooth(t, MERGE_AT, TILE_AT + TILE_IN)
            val rx = HAZE_ROW_RX * width + (HAZE_TILE_R - HAZE_ROW_RX * width) * g
            val ry = HAZE_ROW_RY + (HAZE_TILE_R - HAZE_ROW_RY) * g
            val grown = HAZE_ROW_ALPHA * smooth(t, TRACE_AT, LIFT_AT)
            val burst = if (still) HAZE_TILE_REST else HAZE_TILE_BURST + (HAZE_TILE_REST - HAZE_TILE_BURST) * smooth(t, TILE_AT + POP_FOR, TILE_AT + 0.8f)
            val opacity = (grown + (burst - grown) * g) * (1f - smooth(clocks.away.floatValue, 0f, 0.5f))
            withTransform({ translate(width / 2f, HAZE_CANVAS / 2f + HAZE_ROW_DROP * (1f - g)); scale(rx, ry, pivot = Offset.Zero) }) {
                drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = opacity)
            }
        }
    }
}

@Composable
private fun Tile(clocks: Clocks, still: Boolean, neon: Color, size: Float, modifier: Modifier) {
    val radius = BOX_RADIUS * size / BOX
    val paint = remember(neon, size) { TilePaint(neon, size, radius) }
    val checkMeasure = remember { PathMeasure().apply { setPath(Check, false) } }
    val checkPath = remember { Path() }
    Canvas(modifier.size(TILE_CANVAS.dp)) {
        surface(0.dp) { _, _, _, _ ->
            val t = clocks.clock.floatValue
            if (t < 0f) return@surface
            val a = clocks.away.floatValue
            val opacity = smooth(t, TILE_AT, TILE_AT + TILE_IN) * (1f - smooth(a, 0f, 0.5f))
            val age = t - TILE_AT
            val u = clamp01(age / POP_FOR)
            val up = TILE_ENTER + (TILE_BURST - TILE_ENTER) * (1f - (1f - u) * (1f - u))
            val k = if (still) 1f else (if (age < POP_FOR) up else 1f + (TILE_BURST - 1f) * springLeft(age - POP_FOR)) * (1f - 0.12f * smooth(a, 0f, 0.5f))
            val heat = if (still) 0f else 1f - smooth(t, TILE_AT + POP_FOR, TILE_AT + 0.75f)
            val center = Offset(TILE_CANVAS / 2f, TILE_CANVAS / 2f)
            scale(k, k, pivot = center) {
                val frame = (TILE_CANVAS - size) / 2f
                drawIntoCanvas { paint.glow(it.nativeCanvas, opacity, heat) }
                drawRoundRect(TileFill, Offset(frame, frame), Size(size, size), CornerRadius(radius), alpha = opacity)
                drawRoundRect(Brush.verticalGradient(listOf(neon.copy(alpha = 0x1F / 255f), neon.copy(alpha = 0x47 / 255f)), startY = frame, endY = frame + size),
                    Offset(frame, frame), Size(size, size), CornerRadius(radius), alpha = opacity * (0.2f + 0.8f * heat))
                drawRoundRect(neon, Offset(frame, frame), Size(size, size), CornerRadius(radius), alpha = opacity, style = Stroke(2f))
                drawRoundRect(Warm, Offset(frame, frame), Size(size, size), CornerRadius(radius), alpha = opacity * 0.8f * heat, style = Stroke(2f))
                val check = smooth(t, TILE_AT + 0.02f, TILE_AT + 0.02f + CHECK_FOR)
                val dot = smooth(t, TILE_AT - 0.04f, TILE_AT + 0.02f)
                checkPath.rewind(); checkMeasure.getSegment(0f, checkMeasure.length * check, checkPath, true)
                withTransform({ translate(center.x, center.y); scale(size / BOX, size / BOX, pivot = Offset.Zero) }) {
                    if (check > 0f) drawPath(checkPath, Ink, alpha = opacity * dot, style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    else drawCircle(Ink, 1.6f, Offset(-12f, 1.5f), alpha = opacity * dot)
                }
            }
            if (!still) for (ember in Embers) {
                val e = t - TILE_AT - ember[1]
                val alive = 0.9f * smooth(e, 0f, 0.06f) * (1f - smooth(e, EMBER_FOR * 0.4f, EMBER_FOR))
                val positive = max(0f, e)
                drawCircle(neon, ember[4], Offset(center.x + ember[0] + ember[2] * positive,
                    center.y + EMBER_FROM + ember[3] * positive + 0.5f * EMBER_GRAVITY * positive * positive), alpha = opacity * alive)
            }
        }
    }
}

private class TilePaint(neon: Color, private val size: Float, private val radius: Float) {
    private val flare = SoftRect(neon, 30f)
    private val bloom = stroke(neon, 12f, 24f)
    private val edge = stroke(neon, 5f, 7f)
    private val path = android.graphics.Path().apply {
        val x = (TILE_CANVAS - size) / 2f
        addRoundRect(x, x, x + size, x + size, radius, radius, android.graphics.Path.Direction.CW)
    }
    fun glow(canvas: android.graphics.Canvas, opacity: Float, heat: Float) {
        val x = (TILE_CANVAS - size) / 2f
        flare.draw(canvas, x, x, size, size, radius, opacity * 0.5f * heat)
        bloom.draw(canvas, path, opacity * (0.3f + 0.7f * heat))
        edge.draw(canvas, path, opacity)
    }
}

@Composable
private fun Key(label: String, height: Float, held: Boolean, enabled: Boolean, modifier: Modifier, onPress: () -> Unit) {
    if (label.isEmpty()) { Box(modifier.height(height.dp)); return }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = pressed || held
    val flash by cssTween(if (lit) 1f else 0f, if (lit) KEY_ON_MS else KEY_OFF_MS)
    Box(modifier.height(height.dp).clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onPress)
        .semantics { contentDescription = if (label == "back") "Delete" else label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(KEY_FLASH_WIDTH.dp, KEY_FLASH_HEIGHT.dp).graphicsLayer { alpha = flash }.background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp)))
        if (label == "back") Canvas(Modifier.size(25.dp)) {
            surface(0.dp) { _, _, _, _ ->
                val outline = Path().apply { moveTo(9f, 5f); lineTo(22f, 5f); lineTo(22f, 20f); lineTo(9f, 20f); lineTo(2f, 12.5f); close() }
                drawPath(outline, KeyInk, style = Stroke(1.7f, join = StrokeJoin.Round))
                drawLine(KeyInk, Offset(12f, 9f), Offset(18f, 16f), 1.7f, StrokeCap.Round)
                drawLine(KeyInk, Offset(18f, 9f), Offset(12f, 16f), 1.7f, StrokeCap.Round)
            }
        } else BasicText(label, style = KeyStyle)
    }
}
