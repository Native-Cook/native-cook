package com.ngocdevv.nativecook.blocks.forms.eyewipepasswordfield

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.roundToInt

const val FIELD = 64f
const val RADIUS = 14f
/** The wipe from disc to field and back, in milliseconds, with the same curve both ways. */
const val WIPE = 340
private val WipeCurve = CubicBezierEasing(0.55f, 0f, 0.15f, 1f)
private val PressCurve = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }
private const val PRESS = 90
private const val DISC = 33f
private const val EYE_INSET = 43f
private const val LOCK_X = 33f
private const val LOCK = 21f
private const val DOTS_FROM = 64f
private const val PITCH = 12.5f
private const val DOT = 5f
private const val FONT_SIZE = 17f
private const val BLEED = 28f
private const val TEXT_FROM = 0.1f
private const val TEXT_TO = 0.6f
private const val TEXT_BLUR = 3.5f
private const val EYE_FROM = 0.15f
private const val EYE_TO = 0.85f
private const val EYE = 9.5f
private const val LID = 5.5f
private const val LASH_GAP = 2.6f
private const val LASH = 3.6f
private val Lashes = listOf(0.2f to -0.5f, 0.5f to 0f, 0.8f to 0.5f)
private const val PUPIL = 2.9f
private const val STROKE = 2.1f
private val Lock = PathParser().parsePathString("M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6z").toPath()

private fun mix(from: Color, to: Color, t: Float) = Color(
    ((from.red + (to.red - from.red) * t) * 255f).roundToInt(),
    ((from.green + (to.green - from.green) * t) * 255f).roundToInt(),
    ((from.blue + (to.blue - from.blue) * t) * 255f).roundToInt(),
)

private fun smooth(t: Float, from: Float, to: Float): Float {
    val u = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/** Observe the landing finger without consuming the text field's own selection gesture. */
@Composable
internal fun Modifier.fieldTouch(onTouch: () -> Unit): Modifier {
    val touched by rememberUpdatedState(onTouch)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touched()
            waitForUpOrCancellation()
        }
    }
}

/** The eye's two lids and three lashes, which vanish and regrow on the other side of the lid. */
private fun eyePath(path: Path, open: Float) {
    path.rewind()
    val bow = 2f * LID * (1f - 2f * open)
    path.moveTo(-EYE, 0f); path.quadraticTo(0f, 2f * LID, EYE, 0f)
    path.moveTo(-EYE, 0f); path.quadraticTo(0f, bow, EYE, 0f)
    val side = 1f - 2f * open
    val reach = sqrt(abs(side))
    for ((t, dx) in Lashes) {
        val x = -EYE + 2f * EYE * t
        val y = bow * 2f * t * (1f - t)
        val dy = if (side > 0f) 1f else -1f
        val length = sqrt(dx * dx + dy * dy)
        path.moveTo(x + dx / length * LASH_GAP * reach, y + dy / length * LASH_GAP * reach)
        path.lineTo(x + dx / length * (LASH_GAP + LASH) * reach, y + dy / length * (LASH_GAP + LASH) * reach)
    }
}

/** A password field whose eye stretches its disc into a white wipe and resolves dots into text. */
@Composable
fun WipeField(
    value: String,
    onChangeText: (value: String) -> Unit,
    shown: Boolean,
    onToggle: () -> Unit,
    width: Float,
    held: Boolean = false,
    onTouch: (() -> Unit)? = null,
    maxLength: Int = 14,
    placeholder: String = "Password",
    placeholderColor: Color = Color(0xFF5D648F),
    fill: Color = Color(0xFF121829),
    wipe: Color = Color.White,
    dotColor: Color = Color(0xFF5B5FE0),
    textColor: Color = Color(0xFF5F5F9E),
    eyeColor: Color = Color(0xFF3B3690),
    eyeOpenColor: Color = Color(0xFF4B3FB5),
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var hasManualPress by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (shown) 1f else 0f, if (still) snap() else tween(WIPE, easing = WipeCurve), label = "wipe")
    val pressed = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val manuallyPressed by interaction.collectIsPressedAsState()
    val touched by rememberUpdatedState(onTouch)
    LaunchedEffect(held) {
        if (still) pressed.snapTo(if (held) 1f else 0f)
        else pressed.animateTo(if (held) 1f else 0f, tween(PRESS, easing = PressCurve))
    }
    LaunchedEffect(manuallyPressed) {
        if (!manuallyPressed && !hasManualPress) return@LaunchedEffect
        hasManualPress = true
        if (manuallyPressed) touched?.invoke()
        if (still) pressed.snapTo(if (manuallyPressed) 1f else 0f)
        else pressed.animateTo(if (manuallyPressed) 1f else 0f, tween(if (manuallyPressed) PRESS else PRESS * 2, easing = PressCurve))
    }
    val shadow = remember { WipeShadow() }
    val flood = remember { Path() }
    val eye = remember { Path() }
    val blink = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) {
            val t = ((withFrameNanos { it } - origin) / 1e9f) % 1f
            blink.floatValue = when {
                t <= 0.5f -> 1f
                t < 0.56f -> 1f - (t - 0.5f) / 0.06f
                t < 0.94f -> 0f
                else -> (t - 0.94f) / 0.06f
            }
        }
    }
    fun resolved() = smooth(progress, TEXT_FROM, TEXT_TO)
    fun Modifier.blurredText(dots: Boolean) = graphicsLayer {
        val resolved = resolved()
        val opacity = if (dots) 1f - resolved else resolved
        val blur = TEXT_BLUR * if (dots) resolved else 1f - resolved
        // Android 12 can blur the text layer; earlier versions dim it during the cross-fade.
        alpha = opacity * if (Build.VERSION.SDK_INT < 31) 1f - 0.5f * blur / TEXT_BLUR else 1f
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > 0.001f) {
            val radius = sigmaToRadius(with(density) { blur.dp.toPx() })
            BlurEffect(radius, radius, TileMode.Decal)
        } else null
    }
    Box(modifier.width(width.dp).height(FIELD.dp)) {
        Canvas(Modifier.fillMaxSize().bleed(BLEED.dp).clearAndSetSemantics {}) {
            surface(BLEED.dp) { x, y, _, _ ->
                withTransform({ translate(x, y) }) {
                    val p = progress
                    val disc = DISC * (1f - 0.08f * pressed.value * (1f - p))
                    val left = (width - EYE_INSET - disc / 2f) * (1f - p)
                    val right = width - (EYE_INSET - disc / 2f) * (1f - p)
                    val height = disc + (FIELD - disc) * p
                    val radius = height / 2f + (RADIUS - height / 2f) * smooth(p, 0.75f, 1f)
                    flood.rewind(); flood.addRoundRect(RoundRect(left, (FIELD - height) / 2f, right, (FIELD + height) / 2f, CornerRadius(radius)))
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, width, smooth(p, 0.5f, 1f)) }
                    drawRoundRect(fill, size = Size(width, FIELD), cornerRadius = CornerRadius(RADIUS))
                    fun lock(color: Color) = withTransform({ translate(LOCK_X - LOCK / 2f, FIELD / 2f - LOCK / 2f - 1f); scale(LOCK / 24f, LOCK / 24f, pivot = Offset.Zero) }) { drawPath(Lock, color) }
                    lock(Color.White)
                    drawPath(flood, wipe)
                    clipPath(flood) { lock(fill) }

                }
            }
        }
        Canvas(Modifier.fillMaxSize().blurredText(true).clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ -> for (index in value.indices) drawCircle(dotColor, DOT / 2f, Offset(DOTS_FROM + index * PITCH, FIELD / 2f)) }
        }
        Box(Modifier.fillMaxSize().blurredText(false).clearAndSetSemantics {}) {
            for (index in value.indices) Box(Modifier.offset(x = (DOTS_FROM + index * PITCH - FONT_SIZE).dp).size((FONT_SIZE * 2f).dp, FIELD.dp), contentAlignment = Alignment.Center) {
                BasicText(value[index].toString(), style = TextStyle(color = textColor, fontSize = FONT_SIZE.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                val open = smooth(progress, EYE_FROM, EYE_TO)
                eyePath(eye, open)
                val tint = mix(eyeColor, eyeOpenColor, open)
                withTransform({ translate(width - EYE_INSET, FIELD / 2f - 2.5f * (1f - open)); scale(1f - 0.08f * pressed.value, 1f - 0.08f * pressed.value, pivot = Offset.Zero) }) {
                    drawPath(eye, tint, style = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(tint, PUPIL * smooth(open, 0.45f, 1f), Offset.Zero)
                }
            }
        }
        CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.Transparent, Color.Transparent)) {
            BasicTextField(value, onValueChange = { onChangeText(it.take(maxLength)) },
                modifier = Modifier.width((width - (EYE_INSET * 2f - 16f)).dp).height(FIELD.dp).fieldTouch { touched?.invoke() }
                    .onFocusChanged { focused = it.isFocused }.filterTextContextMenuComponents { false }.semantics { contentDescription = placeholder },
                textStyle = TextStyle(color = Color.Transparent, fontSize = FONT_SIZE.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                singleLine = true, cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(start = DOTS_FROM.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) BasicText(placeholder, style = TextStyle(color = placeholderColor, fontSize = FONT_SIZE.sp))
                    inner()
                } })
        }
        if (focused) Canvas(Modifier.offset(x = (DOTS_FROM + value.length * PITCH - PITCH / 2f - 1f).dp, y = (FIELD / 2f - 11f).dp)
            .size(2.dp, 22.dp).graphicsLayer { alpha = blink.floatValue }.clearAndSetSemantics {}) {
            drawRoundRect(dotColor, cornerRadius = CornerRadius(1.dp.toPx()))
        }
        Box(Modifier.align(Alignment.CenterEnd).width((EYE_INSET * 2f - 16f).dp).height(FIELD.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (shown) "Hide password" else "Show password" })
    }
}

/** Native blur on supported hardware; six soft layers retain the lilac shadow on Android 8. */
private class WipeShadow {
    private val shadowColor = rgba(70, 60, 190, 0.3f)
    private val soft = SoftRoundRect(shadowColor, 12f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadowColor.copy(alpha = 1f).toArgb() }
    fun draw(canvas: android.graphics.Canvas, width: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT >= 28) { soft.draw(canvas, 4f, 10f, width - 8f, FIELD - 6f, RADIUS, alpha); return }
        for (layer in 6 downTo 1) {
            val outer = layer * 0.5f
            val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            val spread = outer * 12f
            paint.alpha = (alpha * shadowColor.alpha * weight * 255f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(4f - spread, 10f - spread, width - 4f + spread, FIELD + 4f + spread, RADIUS + spread, RADIUS + spread, paint)
        }
    }
}
