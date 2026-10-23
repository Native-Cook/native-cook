package com.ngocdevv.nativecook.blocks.forms.eyewipepasswordfield

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/** autoPlay presses the eye at SHOW_AT and HIDE_AT of every LOOP seconds. */
const val LOOP = 6f
private const val SHOW_AT = 0.8f
private const val HIDE_AT = 3.5f
private const val PRESS_FOR = 0.12f
private const val USER_HOLD = 3f
private const val MARGIN = 32f
private const val MAX_WIDTH = 400f
private const val GAP = 14f
private const val BUTTON = 58f
private val EmailInk = Color(0xFFAEB4E6)
private val Placeholder = Color(0xFF5D648F)
private const val ICON = 21f
private val Mail = PathParser().parsePathString("M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z").toPath()
private val Email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** A quiet sign-in form whose eye floods the dark password field white and resolves its dots. */
@Composable
fun EyeWipePasswordField(
    autoPlay: Boolean = false,
    email: String = "alex@example.com",
    password: String = "91u2dhjk",
    minLength: Int = 6,
    title: String = "Welcome back",
    subtitle: String = "Sign in to pick up where you left off.",
    emailPlaceholder: String = "Email",
    submitLabel: String = "Sign in",
    forgotLabel: String = "Forgot password?",
    background: Color = Color(0xFF6FA0EE),
    fieldColor: Color = Color(0xFF121829),
    accent: Color = Color(0xFF3B3690),
    onToggle: ((shown: Boolean) -> Unit)? = null,
    onSubmit: ((email: String, password: String) -> Unit)? = null,
    onForgot: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val firstEmail by rememberUpdatedState(email)
    val firstPassword by rememberUpdatedState(password)
    val toggled by rememberUpdatedState(onToggle)
    var emailValue by remember { mutableStateOf(email) }
    var passwordValue by remember { mutableStateOf(password) }
    var shown by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }

    // This periwinkle screen needs dark status-bar icons; restore the gallery's style on exit.
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun dismiss() { keyboard?.hide(); focus.clearFocus() }
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun touched() { held = false; touches++ }
    fun toggle(byItself: Boolean) {
        if (!byItself) tick()
        shown = !shown
        toggled?.invoke(shown)
    }
    fun reset() { dismiss(); emailValue = firstEmail; passwordValue = firstPassword; shown = false }
    val latestToggle by rememberUpdatedState(::toggle)
    val latestReset by rememberUpdatedState(::reset)
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) { delay((USER_HOLD * 1000).toLong()); latestReset() }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) { delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000)) }
        while (true) {
            until(SHOW_AT); held = true
            until(SHOW_AT + PRESS_FOR); held = false; latestToggle(true)
            until(HIDE_AT); held = true
            until(HIDE_AT + PRESS_FOR); held = false; latestToggle(true)
            until(LOOP); origin += (LOOP * 1e9f).toLong()
        }
    }
    val valid = Email.matches(emailValue.trim()) && passwordValue.length >= minLength
    BoxWithConstraints(modifier.fillMaxSize().background(background)
        .pointerInput(Unit) { detectTapGestures { touched(); dismiss() } }) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val width = min(maxWidth.value - MARGIN * 2f, MAX_WIDTH)
        Box(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + 72.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.width(width.dp)) {
                BasicText(title, style = textStyle(32f, 38f, FontWeight.Bold, fieldColor, -0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(subtitle, style = textStyle(16f, 22f, FontWeight.Normal, fieldColor), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp).graphicsLayer { alpha = 0.62f })
                Box(Modifier.fillMaxWidth().height(FIELD.dp).background(fieldColor, RoundedCornerShape(RADIUS.dp))) {
                    Canvas(Modifier.align(Alignment.CenterStart).offset(x = (33f - ICON / 2f).dp).size(ICON.dp)) {
                        surface(0.dp) { _, _, _, _ -> scale(ICON / 24f, ICON / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawPath(Mail, Color.White) } }
                    }
                    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(EmailInk, EmailInk.copy(alpha = 0.4f))) {
                        BasicTextField(emailValue, onValueChange = { touched(); emailValue = it },
                            modifier = Modifier.fillMaxSize().padding(start = 61.dp, end = 20.dp).fieldTouch { touched() }
                                .semantics { contentDescription = emailPlaceholder },
                            textStyle = TextStyle(color = EmailInk, fontSize = 17.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            singleLine = true, cursorBrush = SolidColor(EmailInk),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (emailValue.isEmpty()) BasicText(emailPlaceholder, style = TextStyle(color = Placeholder, fontSize = 17.sp))
                                inner()
                            } })
                    }
                }
                Spacer(Modifier.height(GAP.dp))
                WipeField(passwordValue, onChangeText = { touched(); passwordValue = it }, shown = shown,
                    onToggle = { toggle(false) }, width = width, held = held, onTouch = { touched() }, fill = fieldColor)
                Spacer(Modifier.height((GAP + 10f).dp))
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val alpha by cssTween(if (!valid) 0.45f else if (pressed) 0.8f else 1f, 120)
                val scale by cssTween(if (pressed) 0.98f else 1f, 120)
                Box(Modifier.fillMaxWidth().height(BUTTON.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
                    .background(accent, RoundedCornerShape(RADIUS.dp))
                    .clickable(interaction, indication = null, enabled = valid, role = Role.Button) { touched(); tick(); dismiss(); onSubmit?.invoke(emailValue.trim(), passwordValue) },
                    contentAlignment = Alignment.Center) {
                    BasicText(submitLabel, style = textStyle(17f, 22f, FontWeight.SemiBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp)) {
                    val forgotInteraction = remember { MutableInteractionSource() }
                    val forgotPressed by forgotInteraction.collectIsPressedAsState()
                    BasicText(forgotLabel, style = textStyle(15f, 20f, FontWeight.Medium, fieldColor), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { this.alpha = if (forgotPressed) 0.45f else 0.7f })
                    Box(Modifier.matchParentSize().bleed(10.dp).clickable(forgotInteraction, indication = null, role = Role.Button) { touched(); onForgot?.invoke() }
                        .semantics { contentDescription = forgotLabel })
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
