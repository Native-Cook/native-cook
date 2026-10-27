package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

// MARK: - Placement (SwiftUI `.position` is the centre of a view, in canvas points)

/** Centres the element on (x, y) of its parent, whatever the element's size. */
internal fun Modifier.at(x: Float, y: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.minWidth, constraints.minHeight) {
        placeable.place((x.dp.toPx() - placeable.width / 2f).roundToInt(), (y.dp.toPx() - placeable.height / 2f).roundToInt())
    }
}

/** `.frame(width:, alignment: .leading).position(...)`: left edge at `left`, centred on `y`. */
internal fun Modifier.leading(left: Float, y: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.minWidth, constraints.minHeight) {
        placeable.place(left.dp.roundToPx(), (y.dp.toPx() - placeable.height / 2f).roundToInt())
    }
}

/** A canvas-point size in sp that ignores the user's font scale, as the SwiftUI block uses fixed sizes. */
@Composable
internal fun ptText(value: Float): TextUnit {
    val density = LocalDensity.current
    return with(density) { value.dp.toSp() }
}

// MARK: - Text

@Composable
internal fun TBText(
    text: String,
    size: Float,
    color: Color = TBColors.ink,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
    align: TextAlign = TextAlign.Center,
    lineHeight: Float? = null,
    italic: Boolean = false,
    tabular: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        softWrap = false,
        style = TextStyle(
            color = color,
            fontSize = ptText(size),
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = ptText(tracking),
            textAlign = align,
            lineHeight = ptText(lineHeight ?: (size * 1.19f)),
            fontFeatureSettings = if (tabular) "tnum" else null,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

/** Number formatted like `value.formatted(.number.precision(.fractionLength(decimals)))` in en_US. */
internal fun formatAmount(value: Double, decimals: Int): String = String.format(java.util.Locale.US, "%,.${decimals}f", value)

/**
 * Drives a counter the way the reference's numbers settle: most of the distance in a fast first
 * move (`fastShare` with time constant `fast`), then a long tail (`slow`).
 */
@Composable
internal fun rememberSettle(start: Double, target: Double, fastShare: Double, fast: Double, slow: Double, duration: Double, reduced: Boolean, delaySeconds: Double = 0.0): Double {
    var value by remember { mutableStateOf(if (reduced) target else start) }
    LaunchedEffect(reduced) {
        if (reduced) { value = target; return@LaunchedEffect }
        delay((delaySeconds * 1000).toLong())
        val began = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = (now - began) / 1e9
            if (t >= duration) break
            value = target - (target - start) * (fastShare * exp(-t / fast) + (1 - fastShare) * exp(-t / slow))
        }
        value = target
    }
    return value
}

// MARK: - Entrances

/** 0 → 1 once, when the element enters: a timing curve or a SwiftUI spring, after `delay` seconds. */
@Composable
internal fun rememberEntrance(reduced: Boolean, delay: Float = 0f, duration: Float = 0.55f, spring: Pair<Float, Float>? = null, easing: Easing = RevealCurve): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.animateTo(1f, tween(200, easing = EaseOut))
            return@LaunchedEffect
        }
        delay((delay * 1000).toLong())
        val spec: AnimationSpec<Float> = if (spring != null) swiftSpring(spring.first, spring.second) else tween((duration * 1000).toInt(), easing = easing)
        progress.animateTo(1f, spec)
    }
    return progress.asState()
}

/**
 * `tbReveal`: fades in, settles up by `y` and unblurs. Blur needs Android 12; older devices only
 * fade and move.
 */
@Composable
internal fun Reveal(reduced: Boolean, modifier: Modifier = Modifier, delay: Float = 0f, y: Float = 12f, blur: Float = 8f, duration: Float = 0.55f, content: @Composable () -> Unit) {
    val progress by rememberEntrance(reduced, delay, duration)
    Box(
        modifier
            .graphicsLayer {
                alpha = progress
                translationY = if (reduced) 0f else (1 - progress) * y.dp.toPx()
            }
            .then(if (!reduced && blur > 0 && progress < 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(((1 - progress) * blur).dp) else Modifier),
    ) { content() }
}

/** `tbWipe`: reveals its content left to right behind a soft edge. */
@Composable
internal fun Wipe(reduced: Boolean, modifier: Modifier = Modifier, delay: Float = 0f, duration: Float = 0.5f, content: @Composable () -> Unit) {
    val sweep = remember { Animatable(if (reduced) 1f else 0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        launch {
            delay(if (reduced) 0 else (delay * 1000).toLong())
            fade.animateTo(1f, tween(if (reduced) 200 else 250, easing = EaseOut))
        }
        if (!reduced) {
            delay((delay * 1000).toLong())
            sweep.animateTo(1f, tween((duration * 1000).toInt(), easing = WipeCurve))
        }
    }
    Box(
        modifier
            .graphicsLayer { alpha = fade.value; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // A gradient three widths wide (white to 50 %, clear from 62 %), slid in from the left.
                val width = size.width * 3
                val left = -(1 - sweep.value) * size.width * 1.9f
                drawRect(
                    Brush.horizontalGradient(0f to Color.White, 0.5f to Color.White, 0.62f to Color.Transparent, startX = left, endX = left + width),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) { content() }
}

/** Pops in from `from` with a lively spring (the little marks next to headings). */
@Composable
internal fun PopIn(reduced: Boolean, delay: Float, modifier: Modifier = Modifier, from: Float = 0.4f, response: Float = 0.45f, damping: Float = 0.6f, content: @Composable () -> Unit) {
    val progress by rememberEntrance(reduced, delay, spring = response to damping)
    Box(modifier.graphicsLayer {
        alpha = progress.coerceAtMost(1f)
        val scale = if (reduced) 1f else from + (1 - from) * progress
        scaleX = scale
        scaleY = scale
    }) { content() }
}

// MARK: - Headings

/** Two-line centred heading used by four of the six scenes (Medium 32.8). */
@Composable
internal fun Heading(first: String, second: String, reduced: Boolean, x: Float = 200f, y: Float = 603.5f) {
    Column(Modifier.at(x, y), horizontalAlignment = Alignment.CenterHorizontally) {
        Reveal(reduced, delay = 0.05f, y = 8f) { HeadingText(first) }
        Reveal(reduced, delay = 0.22f, y = 10f, blur = 10f, modifier = Modifier.layout { m, c ->
            val p = m.measure(c)
            layout(p.width, p.height - 3.dp.roundToPx()) { p.place(0, -3.dp.roundToPx()) }
        }) { HeadingText(second) }
    }
}

@Composable
internal fun HeadingText(text: String, weight: FontWeight = FontWeight.Medium, size: Float = 32.8f, tracking: Float = -0.8f) {
    TBText(text, size, weight = weight, tracking = tracking)
}

@Composable
internal fun Caption(text: String, color: Color = TBColors.caption) {
    TBText(text, 14.4f, color = color, tracking = -0.15f, lineHeight = 20f)
}

// MARK: - Buttons

/** A tap target that dims and shrinks a little while pressed; `onLongPress` fires after 0.8 s. */
@Composable
internal fun PressScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    selected: Boolean? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longPress by rememberUpdatedState(onLongPress)
    Box(
        modifier
            .semantics {
                role = Role.Button
                if (label != null) contentDescription = label
                if (selected != null) this.selected = selected
            }
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.9f else 1f
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = withTimeoutOrNull(800) { waitForUpOrCancellation() }
                    pressed = false
                    when {
                        up == null && longPress != null -> longPress?.invoke()
                        up != null -> click()
                    }
                }
            },
    ) { content() }
}

private const val CtaWidth = 343f
private const val CtaHeight = 62f

/**
 * The big white CTA: a pill with a pale lower lip, so it reads as a raised key. Pressing swells
 * it 6 %, as the reference does under the finger. `pressed` shows the swell without a touch (the
 * QA walkthrough); `dim` is the grey veil while a scene changes.
 */
@Composable
internal fun PrimaryButton(title: String, pro: Boolean, dim: Float, pressed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null) {
    var touching by remember { mutableStateOf(false) }
    val swell = remember { Animatable(0f) }
    LaunchedEffect(touching || pressed) { swell.animateTo(if (touching || pressed) 1f else 0f, swiftSpring(0.28f, 0.7f)) }
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(CtaWidth.dp, CtaHeight.dp)
            .semantics { role = Role.Button; contentDescription = title }
            .graphicsLayer {
                val scale = 1 + 0.06f * swell.value
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    touching = true
                    val up = waitForUpOrCancellation()
                    touching = false
                    if (up != null) click()
                }
            }
            .drawBehind {
                val r = 27.dp.toPx()
                dropShadow(Color.White.copy(alpha = (if (pro) 0.04f else 0.07f) + 0.15f * swell.value), (14 + 4 * swell.value).dp.toPx(), 0f, r)
                val lip = if (pro) Brush.horizontalGradient(listOf(hex(0xd8c9a8), hex(0x9fb8cc))) else Brush.verticalGradient(listOf(hex(0xe4e4e4), hex(0xcdcdcd)))
                drawRoundRect(lip, cornerRadius = CornerRadius(r))
                val faceHeight = size.height - 3.dp.toPx()
                val face = if (pro) Brush.horizontalGradient(listOf(hex(0xf1e2c4), hex(0xdcdcd2), hex(0xb6cfe2)))
                else Brush.verticalGradient(listOf(hex(0xfefefe), hex(0xfdfdfd), hex(0xf3f3f3)), endY = faceHeight)
                drawRoundRect(face, size = Size(size.width, faceHeight), cornerRadius = CornerRadius(26.dp.toPx()))
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(listOf(white(0.9f), white(0f)), endY = faceHeight / 2),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, faceHeight - stroke),
                    cornerRadius = CornerRadius(26.dp.toPx() - stroke / 2),
                    style = Stroke(stroke),
                )
            }
            .drawWithContent {
                drawContent()
                drawRoundRect(Color.Black.copy(alpha = 0.42f * dim), cornerRadius = CornerRadius(27.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, -1.dp.roundToPx()) } },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TBText(title, if (pro) 15.6f else 17f, color = if (pro) Color.Black else TBColors.ctaText, weight = if (pro) FontWeight.Bold else FontWeight.SemiBold, tracking = -0.2f)
            icon?.invoke()
        }
    }
}

// MARK: - Surfaces

/** A soft shadow of a rounded rectangle the size of the draw scope, drawn with a blurred paint. */
internal fun DrawScope.dropShadow(color: Color, radius: Float, dy: Float, corner: Float) {
    if (color.alpha <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(radius, 0f, dy, color.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
    }
}

/**
 * A rounded card: gradient fill, gradient border drawn inside the edge (SwiftUI `strokeBorder`),
 * optional drop shadow, content on top.
 */
@Composable
internal fun Panel(
    width: Float,
    height: Float,
    radius: Float,
    fill: List<Color>,
    modifier: Modifier = Modifier,
    stroke: List<Color>? = null,
    strokeWidth: Float = 1f,
    diagonalStroke: Boolean = false,
    shadow: Triple<Color, Float, Float>? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .size(width.dp, height.dp)
            .drawBehind {
                val r = radius.dp.toPx()
                if (shadow != null) dropShadow(shadow.first, shadow.second.dp.toPx(), shadow.third.dp.toPx(), r)
                drawRoundRect(if (fill.size == 1) Brush.verticalGradient(listOf(fill[0], fill[0])) else Brush.verticalGradient(fill), cornerRadius = CornerRadius(r))
                if (stroke != null) {
                    val w = strokeWidth.dp.toPx()
                    val brush = if (diagonalStroke) Brush.linearGradient(stroke, start = Offset.Zero, end = Offset(size.width, size.height)) else Brush.verticalGradient(stroke)
                    drawRoundRect(brush, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r - w / 2), style = Stroke(w))
                }
            },
    ) { content() }
}

// MARK: - Marks and glyphs (same geometry as the SwiftUI shapes)

/** The scalloped "bot" seal: a soft nine-lobed blob. */
internal fun sealPath(size: Float, lobes: Int = 9, depth: Float = 0.045f) = Path().apply {
    val steps = 216
    for (i in 0..steps) {
        val angle = i.toFloat() / steps * 2 * PI.toFloat() - PI.toFloat() / 2
        val radius = (0.5f - depth + depth * cos(angle * lobes)) * size
        val x = size / 2 + cos(angle) * radius
        val y = size / 2 + sin(angle) * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

internal enum class MarkStyle { Blue, White, Outline }

/** The seal with two eyes: blue next to headings, white on cards, outlined inside "Bots". */
@Composable
internal fun Mark(style: MarkStyle = MarkStyle.Blue, size: Float = 33f, rotation: Float = 0f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp).graphicsLayer { rotationZ = rotation }) {
        val s = this.size.width
        val seal = sealPath(s)
        when (style) {
            MarkStyle.Blue -> drawPath(seal, Brush.verticalGradient(listOf(hex(0x2f62ff), hex(0x5d93ff), hex(0x87b6ff))))
            MarkStyle.White -> drawPath(seal, Brush.verticalGradient(listOf(Color.White, hex(0xe6e6e6))))
            MarkStyle.Outline -> {
                drawPath(seal, hex(0x0f0f0f))
                drawPath(seal, Color.White, style = Stroke(s * 0.09f))
            }
        }
        val eyeW = s * 0.075f
        val eyeH = s * 0.27f
        val gap = s * (if (style == MarkStyle.Outline) 0.1f else 0.12f)
        val eye = if (style == MarkStyle.Outline) Color.White else hex(0x141414)
        rotate(if (style == MarkStyle.Outline) 18f else 0f) {
            drawRoundRect(eye, Offset(s / 2 - gap / 2 - eyeW, s / 2 - eyeH / 2), Size(eyeW, eyeH), CornerRadius(eyeW / 2))
            drawRoundRect(eye, Offset(s / 2 + gap / 2, s / 2 - eyeH / 2), Size(eyeW, eyeH), CornerRadius(eyeW / 2))
        }
    }
}

@Composable
internal fun BackArrow(width: Float, height: Float, color: Color = Color.White, lineWidth: Float = 1.7f) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w, h / 2); lineTo(0f, h / 2)
            moveTo(h / 2, 0f); lineTo(0f, h / 2); lineTo(h / 2, h)
        }
        drawPath(path, color, style = Stroke(lineWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A candlestick chart: an L axis and two candles with wicks. */
@Composable
internal fun Candles(width: Float, height: Float, color: Color = hex(0x9a9a9a)) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val w = size.width
        val h = size.height
        val px = 1.dp.toPx()
        val axis = Path().apply { moveTo(0.7f * px, 0f); lineTo(0.7f * px, h - 0.7f * px); lineTo(w, h - 0.7f * px) }
        drawPath(axis, color, style = Stroke(1.3f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for ((x, top, bottom) in listOf(Triple(0.38f, 0.12f, 0.62f), Triple(0.72f, 0f, 0.5f))) {
            drawLine(color, Offset(w * x, h * top), Offset(w * x, h * bottom + 0.1f * h), strokeWidth = 1.1f * px)
            drawRoundRect(color, Offset(w * x - w * 0.11f, h * (top + 0.15f)), Size(w * 0.22f, h * 0.32f), CornerRadius(1.2f * px), style = Stroke(1.2f * px))
        }
    }
}

@Composable
internal fun BellCurve(width: Float, height: Float, color: Color = hex(0x9a9a9a), lineWidth: Float = 1.3f) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val w = size.width
        val h = size.height
        val px = 1.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f); lineTo(0f, h); lineTo(w, h)
            moveTo(px, h - px)
            cubicTo(w * 0.35f, h - 2 * px, w / 2 - 2 * px, h * 0.2f, w / 2 + px, h * 0.2f)
            cubicTo(w / 2 + 4 * px, h * 0.2f, w - w * 0.25f, h - px, w, h - px)
        }
        drawPath(path, color, style = Stroke(lineWidth * px))
    }
}

@Composable
internal fun Bars(width: Float, height: Float, color: Color = hex(0x9a9a9a), lineWidth: Float = 1.3f) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val w = size.width / 3
        val h = size.height
        val r = CornerRadius(1.dp.toPx())
        val stroke = Stroke(lineWidth.dp.toPx())
        drawRoundRect(color, Offset(0f, h * 0.45f), Size(w, h * 0.55f), r, style = stroke)
        drawRoundRect(color, Offset(w, 0f), Size(w, h), r, style = stroke)
        drawRoundRect(color, Offset(2 * w, h * 0.3f), Size(w, h * 0.7f), r, style = stroke)
    }
}

/** —o— : the Configure Bot glyph. */
@Composable
internal fun SliderGlyph(width: Float, height: Float, color: Color = Color.White, lineWidth: Float = 1.3f) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val r = size.height / 2
        val stroke = lineWidth.dp.toPx()
        drawLine(color, Offset(0f, r), Offset(size.width / 2 - r, r), stroke)
        drawCircle(color, r, Offset(size.width / 2, r), style = Stroke(stroke))
        drawLine(color, Offset(size.width / 2 + r, r), Offset(size.width, r), stroke)
    }
}

@Composable
internal fun HexNut(width: Float, height: Float, color: Color = Color.White, lineWidth: Float = 1.5f) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            for (i in 0..6) {
                val angle = i / 6f * 2 * PI.toFloat() - PI.toFloat() / 2
                val x = w / 2 + cos(angle) * w / 2
                val y = h / 2 + sin(angle) * h / 2
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color, style = Stroke(lineWidth.dp.toPx()))
        drawCircle(color, w * 0.17f, Offset(w / 2, h / 2), style = Stroke(lineWidth.dp.toPx()))
    }
}

/** Faint 0.9 pt dot grid used inside the bot screens and the results tray. */
@Composable
internal fun DotField(modifier: Modifier = Modifier, spacing: Float = 5f, opacity: Float = 0.05f) {
    Canvas(modifier.fillMaxSize()) {
        val step = spacing.dp.toPx()
        val brush = shaderBrush(DotsShader) {
            setFloatUniform("spacing", step)
            setFloatUniform("opacity", opacity)
        }
        if (brush != null) {
            drawRect(brush)
        } else {
            val dot = white(opacity)
            val radius = 0.45.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    drawCircle(dot, radius, Offset(x, y))
                    y += step
                }
                x += step
            }
        }
    }
}

/** Uptime in seconds, for the CTA's double-press guard. */
internal fun uptimeSeconds() = SystemClock.uptimeMillis() / 1000.0

/** A row with SwiftUI-like spacing between children. */
@Composable
internal fun HStack(spacing: Float, modifier: Modifier = Modifier, alignment: Alignment.Vertical = Alignment.CenterVertically, horizontalCenter: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val arrangement = if (horizontalCenter) Arrangement.spacedBy(spacing.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(spacing.dp)
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = alignment, content = content)
}

/** A float that counts up from 0 in seconds while `running`. */
@Composable
internal fun rememberClock(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val began = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.floatValue = (it - began) / 1e9f }
        }
    }
    return time
}
