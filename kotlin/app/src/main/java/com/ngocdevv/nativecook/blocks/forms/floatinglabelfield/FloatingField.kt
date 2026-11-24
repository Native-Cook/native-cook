package com.ngocdevv.nativecook.blocks.forms.floatinglabelfield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 FloatingField: an outlined text field whose placeholder becomes its label.

 On focus the placeholder rides a slight arc up to the top border and shrinks to 78 %, a notch
 opens in the border for it, and an accent outline is drawn around the field from one side of
 the notch to the other. A valid value draws a check at the right; a failed one shakes the field
 once, turns the outline and label to the error colour and slides a helper line down. A secure
 field shows its characters as dots that pop in, and a four-segment strength bar.

 The field is a real text field. Everything that moves reads one `FieldPose`, inside draw and
 layer lambdas only, so a running animation never recomposes it.
*/

internal object FieldColors {
    val fill = Color(0xFFFBF9F4)
    val hairline = Color(0xFFD8D1C2)
    val ink = Color(0xFF1E2A24)
    val muted = Color(0xFF7C8279)
    val accent = Color(0xFF1F5C45)
    val accentTint = Color(0xFFDFEBE2)
    val error = Color(0xFFB4442C)
    val errorTint = Color(0xFFF6E1DA)
    val track = Color(0xFFE4DECF)
    val strength = listOf(Color(0xFFB4442C), Color(0xFFC98A1B), Color(0xFF6E8B3D), Color(0xFF2F7D55))
}

private const val RADIUS = 16f
private const val PAD_X = 18f
private const val LABEL_LINE = 22f
private const val FLOAT_SCALE = 0.78f
/** The floated label sits 6 dp further in than the placeholder, and swings 7 dp out on the way. */
private const val FLOAT_DX = 6f
private const val ARC = 7f
private const val NOTCH_PAD = 6f
private const val INSET = 1f
private const val BADGE_R = 11f
private const val DOT = 8f
internal const val DOT_PITCH = 13f
private const val MAX_DOTS = 24
private const val CARET_H = 22f
private val STRENGTH_WORDS = listOf("Weak", "Fair", "Good", "Strong")

/** The text style of a field's value; the loop's caret table is measured with the same one. */
internal val FieldTextStyle = TextStyle(
    fontSize = 17.sp,
    fontWeight = FontWeight.Normal,
    color = FieldColors.ink,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun outlinePath(w: Float, h: Float, notchLeft: Float, notchRight: Float) = Path().apply {
    val r = RADIUS - INSET
    val k = r * 0.5523f
    val x0 = INSET
    val y0 = INSET
    val x1 = w - INSET
    val y1 = h - INSET
    moveTo(notchRight, y0)
    lineTo(x1 - r, y0)
    cubicTo(x1 - r + k, y0, x1, y0 + r - k, x1, y0 + r)
    lineTo(x1, y1 - r)
    cubicTo(x1, y1 - r + k, x1 - r + k, y1, x1 - r, y1)
    lineTo(x0 + r, y1)
    cubicTo(x0 + r - k, y1, x0, y1 - r + k, x0, y1 - r)
    lineTo(x0, y0 + r)
    cubicTo(x0, y0 + r - k, x0 + r - k, y0, x0 + r, y0)
    lineTo(notchLeft, y0)
}

private fun checkPath(cx: Float, cy: Float) = Path().apply {
    moveTo(cx - 4.6f, cy + 0.4f)
    lineTo(cx - 1.4f, cy + 3.5f)
    lineTo(cx + 4.8f, cy - 3.4f)
}

/** Border, notch, outline and badges, in dp units. */
private class FieldArt(val w: Float, val h: Float, val notchLeft: Float, val notchRight: Float) {
    val outline = outlinePath(w, h, notchLeft, notchRight)
    val badgeX = w - 27f
    val badgeY = h / 2f
    val check = checkPath(badgeX, badgeY)
    private val outlineMeasure = PathMeasure().apply { setPath(outline, false) }
    private val checkMeasure = PathMeasure().apply { setPath(check, false) }
    private val outlineLength = outlineMeasure.length
    private val checkLength = checkMeasure.length
    val trimmed = Path()
    val trimmedCheck = Path()
    val notch = Path()

    fun trim(start: Float, end: Float) {
        trimmed.rewind()
        outlineMeasure.getSegment(start * outlineLength, end * outlineLength, trimmed, true)
    }

    fun trimCheck(end: Float) {
        trimmedCheck.rewind()
        checkMeasure.getSegment(0f, end * checkLength, trimmedCheck, true)
    }

    /** The two halves of the border under the label, pulling apart from its middle. */
    fun openNotch(open: Float) {
        notch.rewind()
        if (open >= 0.999f) return
        val middle = (notchLeft + notchRight) / 2f
        val half = (notchRight - notchLeft) / 2f * open
        notch.moveTo(notchLeft, INSET)
        notch.lineTo(middle - half, INSET)
        notch.moveTo(middle + half, INSET)
        notch.lineTo(notchRight, INSET)
    }
}

private fun DrawScope.drawField(art: FieldArt, pose: FieldPose) {
    val float = pose.float
    val error = pose.error
    val valid = pose.valid
    val start = min(pose.lineStart, pose.lineEnd)
    val end = pose.lineEnd
    val lineColor = lerp(FieldColors.accent, FieldColors.error, error)

    art.openNotch(clamp01((float - 0.2f) / 0.45f))
    drawPath(art.outline, FieldColors.hairline, style = Stroke(1f))
    drawPath(art.notch, FieldColors.hairline, style = Stroke(1f))
    if (pose.lineEnd - pose.lineStart > 0.002f) {
        art.trim(start, end)
        drawPath(art.trimmed, lineColor, style = Stroke(2f, cap = StrokeCap.Round))
    }
    // The outline closes the notch too while the label is still a placeholder (an empty field's error).
    val drawn = clamp01(pose.lineEnd - pose.lineStart)
    val stub = drawn * drawn * drawn * drawn
    if (stub > 0.001f) drawPath(art.notch, lineColor, alpha = stub, style = Stroke(2f))

    val center = Offset(art.badgeX, art.badgeY)
    val okOpacity = clamp01(valid * 2.5f) * (1f - error)
    if (okOpacity > 0.001f) {
        val s = 0.6f + 0.4f * easeOutBack(clamp01(valid / 0.6f))
        scale(s, center) {
            drawCircle(FieldColors.accentTint, BADGE_R, center, alpha = okOpacity)
            val okEnd = clamp01((valid - 0.25f) / 0.75f)
            if (okEnd > 0f) {
                art.trimCheck(okEnd)
                drawPath(
                    art.trimmedCheck,
                    FieldColors.accent,
                    alpha = okOpacity,
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
    val badOpacity = clamp01(error * 2f)
    if (badOpacity > 0.001f) {
        val s = 0.6f + 0.4f * easeOutBack(clamp01(error / 0.7f))
        scale(s, center) {
            drawCircle(FieldColors.errorTint, BADGE_R, center, alpha = badOpacity)
            drawLine(
                FieldColors.error,
                Offset(art.badgeX, art.badgeY - 5f),
                Offset(art.badgeX, art.badgeY + 1.2f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
                alpha = badOpacity,
            )
            drawCircle(FieldColors.error, 1.15f, Offset(art.badgeX, art.badgeY + 4.8f), alpha = badOpacity)
        }
    }
}

@Composable
internal fun FloatingField(
    label: String,
    pose: FieldPose,
    width: Dp,
    height: Dp,
    helper: String,
    secure: Boolean,
    state: TextFieldState,
    caretTable: FloatArray?,
    focusRequester: FocusRequester,
    keyboardOptions: KeyboardOptions,
    maxLength: Int,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var labelPx by remember { mutableIntStateOf(0) }
    val w = width.value
    val h = height.value
    val notchRight = min(w - 56f, PAD_X + FLOAT_DX + labelPx / density * FLOAT_SCALE + NOTCH_PAD)
    val art = remember(w, h, notchRight) { FieldArt(w, h, PAD_X, notchRight) }
    val shape = remember { RoundedCornerShape(RADIUS.dp) }
    val transformation = remember(secure, maxLength) {
        InputTransformation.maxLength(if (secure) MAX_DOTS else maxLength)
    }
    val wasFocused = remember { booleanArrayOf(false) }

    Box(
        modifier
            .size(width, height)
            .graphicsLayer { translationX = shakeX(pose.shake) * this.density },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .dropShadow(shape, Shadow(radius = 2.dp, color = Color(31, 45, 36).copy(alpha = 0.07f), offset = DpOffset(0.dp, 1.dp)))
                .dropShadow(shape, Shadow(radius = 22.dp, color = Color(31, 45, 36).copy(alpha = 0.06f), offset = DpOffset(0.dp, 8.dp)))
                .background(FieldColors.fill, shape)
                .innerShadow(shape, Shadow(radius = 0.5.dp, color = Color.White.copy(alpha = 0.9f), offset = DpOffset(0.dp, 1.dp))),
        )
        Canvas(Modifier.fillMaxSize()) {
            scale(this.density, Offset.Zero) { drawField(art, pose) }
        }

        val inputModifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pose.text }
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                if (focus.isFocused != wasFocused[0]) {
                    wasFocused[0] = focus.isFocused
                    onFocusChange(focus.isFocused)
                }
            }
        val decorator: @Composable (@Composable () -> Unit) -> Unit = { inner ->
            Box(
                Modifier.fillMaxSize().padding(start = PAD_X.dp, end = 48.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        }
        if (secure) {
            BasicSecureTextField(
                state = state,
                modifier = inputModifier,
                inputTransformation = transformation,
                textStyle = FieldTextStyle.copy(color = Color.Transparent),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = { onSubmit() },
                cursorBrush = SolidColor(Color.Transparent),
                decorator = { inner -> decorator(inner) },
                textObfuscationMode = TextObfuscationMode.Hidden,
            )
            // The characters, drawn as dots so they can pop in.
            Canvas(
                Modifier
                    .offset(x = PAD_X.dp, y = (h - DOT).dp / 2f)
                    .size((MAX_DOTS * DOT_PITCH).dp, DOT.dp)
                    .graphicsLayer { alpha = pose.text },
            ) {
                val count = pose.count
                val u = this.density
                for (i in 0 until MAX_DOTS) {
                    val c = clamp01(count - i)
                    if (c <= 0f) break
                    val s = if (c >= 1f) 1f else 0.4f + 0.6f * easeOutBack(c)
                    drawCircle(
                        FieldColors.ink,
                        radius = DOT / 2f * s * u,
                        center = Offset((i * DOT_PITCH + DOT / 2f) * u, DOT / 2f * u),
                        alpha = clamp01(c * 2f),
                    )
                }
            }
        } else {
            BasicTextField(
                state = state,
                modifier = inputModifier,
                inputTransformation = transformation,
                textStyle = FieldTextStyle,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = { onSubmit() },
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(FieldColors.accent),
                decorator = { inner -> decorator(inner) },
            )
        }

        // The drawn caret: the loop's, and a secure field's.
        Box(
            Modifier
                .offset(x = PAD_X.dp, y = (h - CARET_H).dp / 2f)
                .size(2.dp, CARET_H.dp)
                .graphicsLayer {
                    val x = when {
                        secure -> pose.count * DOT_PITCH + 1f
                        caretTable != null -> caretTable.getOrElse(pose.frame) { -1f }
                        else -> -1f
                    }
                    alpha = if (x < 0f) 0f else pose.caret * pose.text
                    translationX = max(0f, x) * this.density
                }
                .background(FieldColors.accent, RoundedCornerShape(1.dp)),
        )

        BasicText(
            text = label,
            modifier = Modifier
                .offset(x = PAD_X.dp, y = (h - LABEL_LINE).dp / 2f)
                .onSizeChanged { labelPx = it.width }
                .graphicsLayer {
                    val f = pose.float
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = (FLOAT_DX * f + ARC * sin(PI.toFloat() * f)) * this.density
                    translationY = -h / 2f * f * this.density
                    val s = 1f - (1f - FLOAT_SCALE) * f
                    scaleX = s
                    scaleY = s
                },
            style = TextStyle(
                fontSize = 17.sp,
                lineHeight = LABEL_LINE.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
            maxLines = 1,
            color = {
                val resting = lerp(FieldColors.muted, FieldColors.accent, pose.focus)
                lerp(resting, FieldColors.error, pose.error)
            },
        )

        if (helper.isNotEmpty()) {
            BasicText(
                text = helper,
                modifier = Modifier
                    .offset(x = PAD_X.dp, y = height + 6.dp)
                    .width(width - (PAD_X * 2).dp)
                    .graphicsLayer {
                        val e = pose.error
                        alpha = e
                        translationY = -7f * (1f - e) * this.density
                    },
                style = TextStyle(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = FieldColors.error,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (secure) StrengthBar(pose, width - (PAD_X * 2).dp, Modifier.offset(x = PAD_X.dp, y = height + 11.dp))
    }
}

private fun strengthColor(strength: Float): Color {
    val colors = FieldColors.strength
    if (strength <= 1f) return colors[0]
    if (strength >= 4f) return colors[3]
    val low = strength.toInt()
    return lerp(colors[low - 1], colors[low], strength - low)
}

/** Four segments that fill from the left, and the word of the current level. */
@Composable
private fun StrengthBar(pose: FieldPose, width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width, 16.dp)
            .graphicsLayer { alpha = pose.float * (1f - pose.error) },
    ) {
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .width(width - 58.dp)
                .height(4.dp),
        ) {
            val strength = pose.strength
            val fill = strengthColor(strength)
            val gap = 5f * density
            val segment = (size.width - gap * 3f) / 4f
            val radius = CornerRadius(2f * density)
            for (i in 0 until 4) {
                val x = i * (segment + gap)
                drawRoundRect(FieldColors.track, Offset(x, 0f), Size(segment, size.height), radius)
                val part = clamp01(strength - i)
                if (part > 0f) {
                    // The fill stays inside the track's round ends.
                    val clip = Path().apply {
                        addRoundRect(
                            RoundRect(x, 0f, x + segment, size.height, radius),
                        )
                    }
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(clip)
                    drawRect(fill, Offset(x, 0f), Size(segment * part, size.height))
                    drawContext.canvas.restore()
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).size(46.dp, 16.dp)) {
            STRENGTH_WORDS.forEachIndexed { index, word ->
                BasicText(
                    text = word,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            val d = pose.strength - (index + 1)
                            alpha = clamp01(1f - abs(d) * 2f)
                            translationY = -5f * d * density
                        },
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    maxLines = 1,
                    color = { strengthColor(pose.strength) },
                )
            }
        }
    }
}
