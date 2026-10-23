package com.ngocdevv.nativecook.blocks.inputs.glowborderchatbar

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private const val WIDTH = 350f
private const val HEIGHT = 92f
private const val RADIUS = 22f

// The canvas overflows the field by this much on every side to fit the bloom.
private val BLEED = 44.dp

// The greeting lines up with the field's text, not with its border.
private const val INSET = 16f
private const val SEND = 34f

// Width of the fade at the right end of the chip row.
private const val FADE = 40f

// Seconds the light takes to go around once at rest.
private const val LAP = 3.5f

// Extra laps a sent prompt adds, and the seconds they take.
private const val BOOST_LAPS = 1f
private const val BOOST = 1.6f

// Where the hot spot stays under reduced motion, in laps from the middle of the top edge.
private const val STILL_LAP = 0.07f

// Sideways stretch of the light's gradient: the square root of the field's proportions.
private val STRETCH = sqrt(WIDTH / HEIGHT)

// How much of the bloom inside the field is covered again.
private const val VEIL = 0.5f

// autoPlay (seconds): the prompt is typed, sent, and the bar rests until the loop closes.
// The loop is a whole number of laps, so the light is back where it started.
private const val LOOP = 7f
private const val TYPE_AT = 0.8f

// The typing never takes longer than this, however long the prompt.
private const val TYPE_SPAN = 2f
private const val SEND_AT = 3.25f
private const val PRESS = 0.13f

// After a touch the script waits this long, then starts again from rest.
private const val RESUME_AFTER = 4f

// Seconds between two letters of the typed prompt: uneven, and the same every loop.
private val TYPE_GAPS = floatArrayOf(0.07f, 0.055f, 0.09f, 0.06f, 0.075f, 0.05f, 0.11f, 0.065f)

private const val GHOST = 0.28f

// The send button lights up with a little overshoot: mass 1, stiffness 320, damping 18.
private const val POP_STIFFNESS = 320f
private val POP_RATIO = 18f / (2f * sqrt(POP_STIFFNESS))

private val FieldTop = Color(0xFF242424)
private val FieldBottom = Color(0xFF1E1E1E)
private val Rim = Color(0xFF3D3547)
private val Lilac = Color(0xFFD9A8FF)
private val Pink = Color(0xFFFF7AD9)
private val Violet = Color(0xFFA855F7)
private val Clear = rgba(168, 85, 247, 0f)

// Sweep stops run clockwise from 3 o'clock. The hot spot sits at 0.5 with a short lilac nose ahead
// of it and a long pink and violet tail behind; a dimmer pink streak runs on the opposite side.
private val LightStops = listOf(
    0f to Pink,
    0.025f to rgba(168, 85, 247, 0.55f),
    0.06f to Clear,
    0.33f to Clear,
    0.37f to rgba(168, 85, 247, 0.7f),
    0.43f to Pink,
    0.475f to Color.White,
    0.505f to Color.White,
    0.525f to Lilac,
    0.56f to Clear,
    0.88f to Clear,
    0.93f to rgba(168, 85, 247, 0.5f),
    1f to Pink,
)

private const val MARK = "M10 0C10.9 6 14 9.1 20 10C14 10.9 10.9 14 10 20C9.1 14 6 10.9 0 10C6 9.1 9.1 6 10 0Z"
private const val PLUS = "M9 3.5V14.5M3.5 9H14.5"
private const val SLIDERS = "M3 6H8.2M13.2 6H15M3 12H4.8M9.8 12H15"
private const val MIC = "M13.8 8.6A4.8 4.8 0 0 1 4.2 8.6M9 13.4V16"
private const val CHEVRON = "M2 3.5L5 6.5L8 3.5"
private const val ARROW = "M9 15.5V2.5M3.8 7.7L9 2.5L14.2 7.7"

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun barText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val GreetingStyle = barText(17f, 22f, Color.White)
private val TitleStyle = barText(24f, 30f, Color.White, FontWeight.Medium, -0.3f)
private val LeadStyle = barText(15f, 20f, Color(0xFF8A8A8A))
private val ModelStyle = barText(15f, 20f, Color.White, FontWeight.SemiBold)
private val PlaceholderStyle = barText(15f, 20f, Color(0xFF6E6E6E))
private val InputStyle = barText(15f, 20f, Color.White)
private val ChipStyle = barText(14f, 18f, Color(0xFF7A7A7A))

private fun smooth(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/** 0…1 over time: up in [rise] seconds, held, then down in [fall]. */
private fun envelope(t: Float, rise: Float, hold: Float, fall: Float): Float {
    if (t <= 0f) return 0f
    if (t < rise) return smooth(t / rise)
    return 1f - smooth((t - rise - hold) / fall)
}

/**
 * Direction, seen from the centre of the field, of the point [laps] of the way around its outline
 * (clockwise from the middle of the top edge). Turning the gradient by this angle instead of a
 * steady one makes the light travel at a constant speed along the border.
 */
private fun angleAt(laps: Float): Float {
    val halfW = WIDTH / 2f
    val halfH = HEIGHT / 2f
    val d = (laps - floor(laps)) * 2f * (WIDTH + HEIGHT)
    var x = -halfW
    var y = -halfH
    if (d < halfW) {
        x = d
    } else if (d < halfW + HEIGHT) {
        x = halfW
        y = d - halfW - halfH
    } else if (d < halfW + HEIGHT + WIDTH) {
        x = halfW - (d - halfW - HEIGHT)
        y = halfH
    } else if (d < halfW + 2f * HEIGHT + WIDTH) {
        y = halfH - (d - halfW - HEIGHT - WIDTH)
    } else {
        x = d - 2f * HEIGHT - WIDTH - 3f * halfW
    }
    // Measured in the gradient's stretched space.
    return atan2(y, x / STRETCH)
}

/** When each letter of the prompt appears, in seconds from the start of the loop. */
private fun keyTimes(prompt: String): List<Float> {
    val at = ArrayList<Float>(prompt.length)
    var t = 0f
    for (index in prompt.indices) {
        at += t
        t += TYPE_GAPS[index % TYPE_GAPS.size]
    }
    val scale = min(1f, TYPE_SPAN / max(t, 0.001f))
    return at.map { TYPE_AT + it * scale }
}

/** The drawn caret stays lit for a moment after a letter, then blinks once a second. */
private fun caretOpacity(sinceKey: Float) = if (sinceKey % 1f < 0.55f) 1f else 0f

private suspend fun sleepUntil(nanos: Long) {
    val millis = (nanos - System.nanoTime()) / 1_000_000
    if (millis > 0) delay(millis)
}

/** Seconds since the bar appeared, and the moments its border and overlays react to. */
private class Glow {
    var flareAt = -10f
    var boostAt = -10f
    var keyAt = -10f
    var sentAt = -10f

    // Reading it is what redraws the border.
    val time = mutableFloatStateOf(0f)

    val flare get() = envelope(time.floatValue - flareAt, 0.16f, 0.45f, 0.8f)
    val boost get() = envelope(time.floatValue - boostAt, 0.25f, 0.9f, 0.45f)

    /** Whole laps bring the hot spot back to the middle of the top edge. */
    fun angle(still: Boolean): Float {
        val now = time.floatValue
        val laps = if (still) STILL_LAP else now / LAP + BOOST_LAPS * smooth((now - boostAt) / BOOST)
        return angleAt(laps) - PI.toFloat()
    }
}

/**
 * A rounded-rect stroke painted with the light's sweep gradient. The gradient is drawn in a space
 * stretched sideways, so its streaks keep a similar length on the long and the short edges.
 */
private class LightStroke {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()
    private var shader: SweepGradient? = null

    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, strokeWidth: Float, angle: Float) {
        val cx = x + w / 2f
        val cy = y + h / 2f
        val gradient = shader ?: SweepGradient(
            cx, cy,
            IntArray(LightStops.size) { LightStops[it].second.toArgb() },
            FloatArray(LightStops.size) { LightStops[it].first },
        ).also { shader = it }
        matrix.setRotate(Math.toDegrees(angle.toDouble()).toFloat(), cx, cy)
        matrix.postScale(STRETCH, 1f, cx, cy)
        gradient.setLocalMatrix(matrix)
        paint.shader = gradient
        paint.strokeWidth = strokeWidth
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
}

private class BorderPaints {
    val bloom = LightStroke()
    val wide = LightStroke()
    val crisp = LightStroke()
}

/**
 * Dark assistant chat bar whose border is alive: a white hot spot with pink and violet tails keeps
 * travelling around the rounded field, the whole border flares when a prompt starts, and the light
 * runs a faster, brighter lap after it is sent. The model picker sits inside the placeholder
 * sentence, the mic gives way to the send button once there is text, and the chips fill the field.
 *
 * @param value Controlled text; leave null and the bar keeps its own.
 * @param autoPlay Types [prompt], sends it and starts over, without ever opening the keyboard.
 * @param lead The grey word in front of the model picker.
 * @param models A tap on the picker moves to the next of these.
 * @param prompt What autoPlay types; about thirty characters fit the field.
 * @param backdrop Colour behind the bar: the chip row fades into it at its right end.
 * @param onSend Called with the text when the send button or the keyboard's send key is pressed.
 */
@Composable
fun GlowBorderChatbar(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    greeting: String = "Hi Alex",
    title: String = "What’s the opening move?",
    lead: String = "Ask",
    placeholder: String = "anything...",
    models: List<String> = listOf("Swift", "Balanced", "Deep"),
    chips: List<String> = listOf("Create image", "Compose music", "Help me learn", "Boost my day"),
    prompt: String = "Plan a weekend in Lisbon",
    backdrop: Color = Color.Black,
    onSend: ((String) -> Unit)? = null,
    onModelChange: ((String) -> Unit)? = null,
    onChip: ((String) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    onTools: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val focusManager = LocalFocusManager.current
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var draft by remember { mutableStateOf("") }
    var model by remember { mutableIntStateOf(0) }
    // False once a touch has taken the field from the autoPlay script.
    var handsOff by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var scriptPress by remember { mutableStateOf(false) }
    // The text that was just sent, on its way out.
    var ghost by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val glow = remember { Glow() }

    val playing = auto && handsOff
    val text = value ?: draft
    val hasText = text.isNotEmpty()
    val modelName = if (models.isEmpty()) "" else models[model % models.size]

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) glow.time.floatValue = (withFrameNanos { it } - start) / 1e9f
    }

    LaunchedEffect(hasText) {
        // The border flares whenever a prompt begins, whoever types it.
        if (hasText) glow.flareAt = glow.time.floatValue
    }

    /** Clears the field with the sent text lifting away, and sends the light on its fast lap. */
    fun submit(sent: String) {
        ghost = sent
        glow.sentAt = glow.time.floatValue
        glow.boostAt = glow.time.floatValue
    }

    LaunchedEffect(playing, prompt) {
        if (!playing) return@LaunchedEffect
        val keys = keyTimes(prompt)
        val epoch = System.nanoTime()
        var lap = 0
        while (true) {
            val start = epoch + (lap * LOOP * 1e9).toLong()
            keys.forEachIndexed { index, seconds ->
                sleepUntil(start + (seconds * 1e9).toLong())
                draft = prompt.take(index + 1)
                glow.keyAt = glow.time.floatValue
            }
            sleepUntil(start + (SEND_AT * 1e9).toLong())
            scriptPress = true
            sleepUntil(start + ((SEND_AT + PRESS) * 1e9).toLong())
            scriptPress = false
            submit(prompt)
            draft = ""
            lap += 1
            sleepUntil(epoch + (lap * LOOP * 1e9).toLong())
        }
    }

    // Any touch stops the script; it comes back a few seconds after the last one.
    LaunchedEffect(touches, auto) {
        if (touches == 0 || !auto) return@LaunchedEffect
        delay((RESUME_AFTER * 1000).toLong())
        if (focused) return@LaunchedEffect
        draft = ""
        handsOff = true
    }

    fun touch() {
        handsOff = false
        scriptPress = false
        touches += 1
    }

    fun edit(next: String) {
        if (!controlled) draft = next
        onValueChange?.invoke(next)
    }

    fun send() {
        if (!hasText) return
        touch()
        submit(text)
        edit("")
        focusManager.clearFocus()
        onSend?.invoke(text)
    }

    // The mic steps aside as soon as there is something to send, and the send button lights up.
    val arm by animateFloatAsState(
        targetValue = if (hasText) 1f else 0f,
        animationSpec = if (reducedMotion) tween(150) else spring(POP_RATIO, POP_STIFFNESS),
        label = "arm",
    )
    // It makes way at once, and comes back after the sent text has lifted off.
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (hasText) 0f else 1f,
        animationSpec = if (hasText) snap() else tween(180, delayMillis = 150, easing = EaseOut),
        label = "placeholder",
    )

    Column(modifier.width(WIDTH.dp)) {
        Row(Modifier.padding(start = INSET.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(Icons.mark, Brush.linearGradient(listOf(Lilac, Violet), Offset.Zero, Offset(20f, 20f)))
                }
            }
            Spacer(Modifier.width(8.dp))
            BasicText(greeting, style = GreetingStyle, maxLines = 1)
        }
        BasicText(title, style = TitleStyle, maxLines = 1, modifier = Modifier.padding(start = INSET.dp, top = 6.dp))

        Box(Modifier.padding(top = 14.dp).size(WIDTH.dp, HEIGHT.dp)) {
            Border(glow, still = reducedMotion)

            Row(
                Modifier.offset(16.dp, 14.dp).width((WIDTH - 32f).dp).height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(lead, style = LeadStyle, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Tap(
                    scale = 0.96f,
                    label = "Model: $modelName",
                    onClick = {
                        if (models.isNotEmpty()) {
                            touch()
                            model = (model + 1) % models.size
                            onModelChange?.invoke(models[model])
                        }
                    },
                ) {
                    Row(Modifier.height(24.dp).clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
                        // The next name rolls in from below as the old one leaves upwards.
                        AnimatedContent(
                            targetState = modelName,
                            transitionSpec = {
                                val fade = tween<Float>(180, easing = EaseOutStrong)
                                if (reducedMotion) {
                                    fadeIn(fade) togetherWith fadeOut(fade)
                                } else {
                                    (slideInVertically(tween(180, easing = EaseOutStrong)) { it } + fadeIn(fade)) togetherWith
                                        (slideOutVertically(tween(180, easing = EaseOutStrong)) { -it } + fadeOut(fade))
                                }
                            },
                            label = "model",
                        ) { name ->
                            BasicText(name, style = ModelStyle, maxLines = 1)
                        }
                        Spacer(Modifier.width(3.dp))
                        Glyph(Icons.chevron, 1.7f, size = 10f)
                    }
                }
                Spacer(Modifier.width(8.dp))

                BasicTextField(
                    value = text,
                    onValueChange = {
                        touch()
                        edit(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clipToBounds()
                        .onFocusChanged { state ->
                            if (state.isFocused != focused) {
                                focused = state.isFocused
                                // A tap on the field takes it over from the script and starts from empty.
                                if (focused && playing) draft = ""
                                touch()
                            }
                        }
                        .semantics { contentDescription = "$lead $modelName $placeholder" },
                    textStyle = InputStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(Lilac),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { field ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            BasicText(
                                placeholder,
                                style = PlaceholderStyle,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer { alpha = placeholderAlpha },
                            )
                            field()
                            // The sent text lifts off the line and fades.
                            BasicText(
                                ghost,
                                style = InputStyle,
                                maxLines = 1,
                                modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                                    val sent = ((glow.time.floatValue - glow.sentAt) / GHOST).coerceIn(0f, 1f)
                                    val out = 1f - (1f - sent).pow(3)
                                    alpha = 1f - out
                                    translationY = if (reducedMotion) 0f else -10.dp.toPx() * out
                                },
                            )
                            if (playing && hasText) {
                                // The script does not focus the field, so it draws the caret itself:
                                // an invisible copy of the text pushes it to the end of the line.
                                Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(text, style = InputStyle.copy(color = Color.Transparent), maxLines = 1)
                                    Spacer(Modifier.width(1.dp))
                                    Box(
                                        Modifier
                                            .size(2.dp, 18.dp)
                                            .graphicsLayer { alpha = caretOpacity(glow.time.floatValue - glow.keyAt) }
                                            .background(Lilac, CircleShape),
                                    )
                                }
                            }
                        }
                    },
                )
            }

            Row(Modifier.offset(9.dp, (HEIGHT - 10f - 33f).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Tap(scale = 0.9f, label = "Attach", onClick = { onAttach?.invoke() }) {
                    IconBox { Glyph(Icons.plus, 1.7f) }
                }
                Tap(scale = 0.9f, label = "Tools", onClick = { onTools?.invoke() }) {
                    IconBox {
                        Glyph(Icons.sliders, 1.5f) {
                            drawCircle(Color.White, 2.1f, Offset(10.7f, 6f), style = Stroke(1.5f))
                            drawCircle(Color.White, 2.1f, Offset(7.3f, 12f), style = Stroke(1.5f))
                        }
                    }
                }
            }

            Box(
                Modifier
                    .offset((WIDTH - 10f - SEND - 6f - 32f).dp, (HEIGHT - 10f - 33f).dp)
                    .graphicsLayer {
                        alpha = 1f - arm.coerceIn(0f, 1f)
                        translationX = if (reducedMotion) 0f else 12.dp.toPx() * arm
                        scaleX = 1f - 0.4f * arm
                        scaleY = 1f - 0.4f * arm
                    },
            ) {
                Tap(scale = 0.9f, label = "Dictate", enabled = !hasText, onClick = { onMic?.invoke() }) {
                    IconBox {
                        Glyph(Icons.mic, 1.5f) {
                            drawRoundRect(Color.White, Offset(6.4f, 2f), Size(5.2f, 8.6f), CornerRadius(2.6f))
                        }
                    }
                }
            }

            Box(Modifier.offset((WIDTH - 10f - SEND).dp, (HEIGHT - 10f - SEND).dp)) {
                Tap(scale = 0.9f, label = "Send", held = scriptPress, onClick = { send() }) {
                    Box(Modifier.size(SEND.dp).background(rgba(255, 255, 255, 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(Icons.arrow, 2.1f, color = rgba(255, 255, 255, 0.55f))
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = arm.coerceIn(0f, 1f)
                                    scaleX = 0.6f + 0.4f * arm
                                    scaleY = 0.6f + 0.4f * arm
                                }
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(Icons.arrow, 2.1f, color = Color.Black)
                        }
                    }
                }
            }
        }

        Box(Modifier.padding(top = 12.dp).size(WIDTH.dp, 36.dp)) {
            Row(
                // Room for the last chip to clear the fade once the row is scrolled to its end.
                Modifier.horizontalScroll(rememberScrollState()).padding(end = FADE.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (chip in chips) {
                    Tap(
                        scale = 0.96f,
                        label = chip,
                        onClick = {
                            touch()
                            edit(chip)
                            onChip?.invoke(chip)
                        },
                    ) {
                        Box(
                            Modifier.height(36.dp).background(Color(0xFF1C1C1C), CircleShape).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(chip, style = ChipStyle, maxLines = 1)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(FADE.dp, 36.dp)
                    .background(Brush.horizontalGradient(listOf(backdrop.copy(alpha = 0f), backdrop))),
            )
        }
    }
}

/** The field and its border: the travelling light, the flare of a new prompt, the lap of a sent one. */
@Composable
private fun Border(glow: Glow, still: Boolean) {
    val paints = remember { BorderPaints() }
    val bloom = rememberBloom(7.dp)
    val wideBloom = rememberBloom(14.dp)
    val haloBloom = rememberBloom(16.dp)
    val softBloom = rememberBloom(6.dp)
    val fill = remember { Brush.verticalGradient(listOf(FieldTop, FieldBottom), startY = BLEED.value, endY = BLEED.value + HEIGHT) }

    Box(Modifier.fillMaxSize().bleed(BLEED)) {
        Canvas(Modifier.fillMaxSize()) {
            surface(BLEED) { x, y, w, h -> drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(RADIUS)) }
        }
        // The blurs are applied after shading, so the bloom follows the border instead of
        // smearing into an angular wedge.
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = bloom
                alpha = if (bloom == null) 0f else 0.85f
            },
        ) {
            surface(BLEED) { x, y, w, h ->
                drawIntoCanvas { paints.bloom.draw(it.nativeCanvas, x, y, w, h, RADIUS, 5f, glow.angle(still)) }
            }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = wideBloom
                alpha = if (wideBloom == null) 0f else glow.boost
            },
        ) {
            surface(BLEED) { x, y, w, h ->
                drawIntoCanvas { paints.wide.draw(it.nativeCanvas, x, y, w, h, RADIUS, 9f, glow.angle(still)) }
            }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = haloBloom
                alpha = if (haloBloom == null) 0f else glow.flare * 0.8f
            },
        ) {
            surface(BLEED) { x, y, w, h -> drawRoundRect(Violet, Offset(x, y), Size(w, h), CornerRadius(RADIUS), style = Stroke(10f)) }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = softBloom
                alpha = if (softBloom == null) 0f else glow.flare
            },
        ) {
            surface(BLEED) { x, y, w, h -> drawRoundRect(Lilac, Offset(x, y), Size(w, h), CornerRadius(RADIUS), style = Stroke(5f)) }
        }
        Canvas(Modifier.fillMaxSize()) {
            surface(BLEED) { x, y, w, h ->
                // A veil of the field's own colour keeps the bloom that spills inwards quieter
                // than the one outside.
                drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(RADIUS), alpha = VEIL)
                drawRoundRect(
                    Rim, Offset(x + 0.75f, y + 0.75f), Size(w - 1.5f, h - 1.5f), CornerRadius(RADIUS - 0.75f), style = Stroke(1.5f),
                )
                drawIntoCanvas {
                    paints.crisp.draw(it.nativeCanvas, x + 1f, y + 1f, w - 2f, h - 2f, RADIUS - 1f, 2f, glow.angle(still))
                }
                drawRoundRect(
                    Color.White, Offset(x + 1f, y + 1f), Size(w - 2f, h - 2f), CornerRadius(RADIUS - 1f),
                    style = Stroke(2f), alpha = glow.flare,
                )
            }
        }
    }
}

private object Icons {
    val mark = parse(MARK)
    val plus = parse(PLUS)
    val sliders = parse(SLIDERS)
    val mic = parse(MIC)
    val chevron = parse(CHEVRON)
    val arrow = parse(ARROW)

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()
}

/** A stroked icon drawn in points; [extra] adds the parts that are not plain strokes of the path. */
@Composable
private fun Glyph(path: Path, width: Float, size: Float = 18f, color: Color = Color.White, extra: DrawScope.() -> Unit = {}) {
    Canvas(Modifier.size(size.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            extra()
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun IconBox(content: @Composable () -> Unit) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { content() }
}

/** A tappable part of the bar: dips while pressed (or while the script presses it), with a light haptic. */
@Composable
private fun Tap(
    scale: Float,
    label: String,
    onClick: () -> Unit,
    held: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val dip by animateFloatAsState(
        targetValue = if (pressed || held) scale else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}
