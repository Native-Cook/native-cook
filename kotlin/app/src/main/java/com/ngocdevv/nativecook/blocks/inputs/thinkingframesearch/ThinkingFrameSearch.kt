package com.ngocdevv.nativecook.blocks.inputs.thinkingframesearch

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** @param tint The two colours of the row's small gradient tile. */
data class ThinkingFrameSearchResult(val title: String, val detail: String, val tint: List<Color>)

private const val WIDTH = 350f

// The frame's border on the sides and below the field, and the band and border above it.
private const val BORDER = 5f
private const val FRAME_TOP = 26f
private const val BAND_HEIGHT = 22f
private const val FIELD_WIDTH = WIDTH - 2f * BORDER
private const val FIELD_HEIGHT = 48f
private const val FIELD_RADIUS = 12f

// Space between the field and the results panel.
private const val GAP = 8f
private const val PANEL_RADIUS = 14f
private const val PANEL_HEAD = 30f
private const val PANEL_FOOT = 6f
private const val ROW = 46f
private const val MAX_ROWS = 3

// The canvas overflows the component by this much on every side to fit the glow.
private val BLEED = 40.dp

// The frame: the glow fades in, grows with an overshoot and sharpens; later it melts.
private const val GLOW = 0.3f
private const val GROW = 0.7f
private const val SHARPEN = 0.9f
private const val BLOOM_BLUR = 18f
private const val MELT_BLUR = 14f
private const val MELT_DROP = 20f

// The gradient is twice the component's width and moves this many periods a second.
private const val FLOW_PERIOD = 700f
private const val FLOW_START = 0.68f
private const val FLOW_SPEED = 0.12f
private const val STATUS = 0.25f

// Seconds from the submit to the results.
private const val THINK = 3.1f

// autoPlay (seconds): type, submit, think, look at the results, clear, rest.
private const val LOOP = 9.2f
private const val TYPE_AT = 0.8f

// The typing never takes longer than this, however long the prompt.
private const val TYPE_SPAN = 1f
private const val SUBMIT_AT = 2f
private const val CLEAR_AT = 7.7f

// After a touch the script waits this long, then starts again from rest.
private const val RESUME_AFTER = 4f

// Seconds between two letters of the typed prompt: uneven, and the same every loop.
private val TYPE_GAPS = floatArrayOf(0.09f, 0.07f, 0.11f, 0.06f, 0.085f, 0.07f, 0.12f, 0.075f)

private val Ink = Color(0xFF141414)
private val Mute = Color(0xFF9A9A9A)
private val Orange = Color(0xFFF08A1C)

// Heights of the bars of the voice icon.
private val WAVE_BARS = floatArrayOf(5f, 11f, 15f, 9f, 4f)

// The three dots in front of the label: centre and radius in a 12-point box.
private val SPARK = listOf(Triple(6f, 2.6f, 2f), Triple(2.4f, 8.8f, 1.7f), Triple(9.6f, 8.8f, 1.7f))

// One period of the frame's colours, amber at both ends so it repeats without a seam; drawn twice.
private val FLOW = listOf(
    0f to Color(0xFFF5A623),
    0.18f to Color(0xFFF08A1C),
    0.42f to Color(0xFFC0306A),
    0.6f to Color(0xFF6B3FA0),
    0.74f to Color(0xFFC0306A),
    0.9f to Color(0xFFF08A1C),
    1f to Color(0xFFF5A623),
)
private val FlowStops = (FLOW.map { it.first / 2f to it.second } + FLOW.map { 0.5f + it.first / 2f to it.second }).toTypedArray()

// A slower back-out for the panel: it lands a few points past its height and settles.
private val SoftBack = CubicBezierEasing(0.3f, 1.3f, 0.6f, 1f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun searchText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val InputStyle = searchText(16f, 22f, Ink)
private val PlaceholderStyle = searchText(16f, 22f, Mute)
private val ThinkingStyle = searchText(12f, 16f, Color.White, FontWeight.SemiBold)
private val StatusStyle = searchText(12f, 16f, rgba(255, 255, 255, 0.78f), FontWeight.Medium)
private val HeadStyle = searchText(12f, 16f, Mute, FontWeight.Medium)
private val RowTitleStyle = searchText(15f, 20f, Ink, FontWeight.Medium)
private val RowDetailStyle = searchText(13f, 18f, Mute)

private fun smooth(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/** Ease-out that overshoots its target by a tenth before settling. */
private fun backOut(t: Float): Float {
    val u = t - 1f
    return 1f + 2.70158f * u * u * u + 1.70158f * u * u
}

/** How far into its period the gradient is, [since] seconds after the submit. */
private fun flowPhase(since: Float): Float {
    val phase = (FLOW_START - FLOW_SPEED * max(since, 0f)) % 1f
    return if (phase < 0f) phase + 1f else phase
}

/** 0…1 for a part of the panel that shows once the panel is [after] of the way open. */
private fun reveal(open: Float, after: Float) = ((open - after) / 0.25f).coerceIn(0f, 1f)

/** Where the gradient frame is in its life: growing out of the field, sharp, or melting away. */
private class FrameState(since: Float, melt: Float, still: Boolean) {
    private val sharp = smooth(since / SHARPEN)

    /** 0…1 with a small overshoot: how far the frame has grown out of the field. */
    val grow = if (still) 1f else backOut((since / GROW).coerceIn(0f, 1f))
    val blur = if (still) 0f else BLOOM_BLUR * (1f - sharp) + MELT_BLUR * melt
    val opacity = (since / GLOW).coerceIn(0f, 1f) * (1f - melt)

    /** Opacity of the texts in the band. */
    val label = ((sharp - 0.6f) / 0.3f).coerceIn(0f, 1f) * (1f - min(1f, melt * 3f))

    /** Top edge of the frame, and how far it has sunk towards the results. */
    val top = FRAME_TOP * (1f - grow)
    val drop = if (still) 0f else MELT_DROP * melt
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

// A blur that changes every frame; below API 31 the frame simply fades in sharp.
private fun blurOf(sigmaPx: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || sigmaPx < 0.5f) return null
    val radius = sigmaToRadius(sigmaPx)
    return BlurEffect(radius, radius, TileMode.Decal)
}

private enum class Phase { Idle, Thinking, Results }

/** Seconds since the field appeared, and the moments its drawing is timed from. */
private class Motion {
    var thinkAt = -100f
    var statusAt = -100f
    var keyAt = -100f

    // Reading it is what redraws the frame.
    val time = mutableFloatStateOf(0f)

    /** Seconds since the submit. */
    val since get() = time.floatValue - thinkAt
}

/**
 * Search field that thinks out loud. Submitting a query makes a blurred orange glow bloom behind
 * the field and sharpen into a flowing gradient frame, with "Thinking…" and the current step in a
 * band on top; the frame then melts downwards into a soft panel of results, which unrolls below
 * the field row by row.
 *
 * @param value Controlled text; leave null and the field keeps its own.
 * @param autoPlay Types [prompt], submits it, shows the results and starts over, without opening the keyboard.
 * @param statuses The steps named at the right of the band, one after another.
 * @param prompt What autoPlay types.
 * @param onSend Called with the query when the keyboard's search key is pressed.
 * @param onSelect Called with the row that was tapped; the panel closes and the field clears.
 */
@Composable
fun ThinkingFrameSearch(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Search or ask",
    thinkingLabel: String = "Thinking…",
    statuses: List<String> = listOf("Reading notes", "Checking calendar", "Looking for files"),
    resultsLabel: String = "Top results",
    results: List<ThinkingFrameSearchResult> = listOf(
        ThinkingFrameSearchResult("Standup notes", "Yesterday, 09:30", listOf(Color(0xFF6B3FA0), Color(0xFFC0306A))),
        ThinkingFrameSearchResult("Design review", "Yesterday, 14:00", listOf(Color(0xFFC0306A), Color(0xFFF08A1C))),
        ThinkingFrameSearchResult("yesterday.pdf", "Edited 18:12", listOf(Color(0xFFF08A1C), Color(0xFFF5A623))),
    ),
    prompt: String = "Yesterday",
    onSend: ((String) -> Unit)? = null,
    onSelect: ((ThinkingFrameSearchResult) -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var draft by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(Phase.Idle) }
    // Counts the submits, so a second one during the first restarts the steps.
    var run by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(0) }
    var lastStatus by remember { mutableIntStateOf(-1) }
    // False once a touch has taken the field from the autoPlay script.
    var handsOff by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    // The query that was just cleared, fading where it stood.
    var ghost by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val motion = remember { Motion() }
    // 0…1: the frame letting go of its shape, on the way to the results or back to rest.
    val melt = remember { Animatable(1f) }
    // 0…1: how far the results panel is unrolled.
    val panel = remember { Animatable(0f) }
    val ghostFade = remember { Animatable(0f) }

    val playing = auto && handsOff
    val text = value ?: draft
    val hasText = text.isNotEmpty()
    val rows = results.take(MAX_ROWS)
    val panelHeight = PANEL_HEAD + ROW * rows.size + PANEL_FOOT

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) motion.time.floatValue = (withFrameNanos { it } - start) / 1e9f
    }

    /** The glow blooms and sharpens into the frame; the results follow by themselves. */
    fun submit() {
        phase = Phase.Thinking
        run += 1
        lastStatus = -1
        status = 0
        motion.thinkAt = motion.time.floatValue
        motion.statusAt = motion.time.floatValue
        scope.launch { melt.snapTo(0f) }
        scope.launch { panel.animateTo(0f, tween(250, easing = EaseInOut)) }
    }

    /** Lets go of the frame and rolls the panel back up. */
    fun dismiss() {
        phase = Phase.Idle
        scope.launch { melt.animateTo(1f, tween(300, easing = EaseInOut)) }
        scope.launch { panel.animateTo(0f, tween(350, easing = EaseInOut)) }
    }

    /** Back to the empty field: [gone] is the query that fades where it stood. */
    fun clear(gone: String) {
        dismiss()
        ghost = gone
        scope.launch {
            ghostFade.snapTo(1f)
            ghostFade.animateTo(0f, tween(200, easing = EaseOut))
        }
        draft = ""
    }

    LaunchedEffect(phase, run) {
        if (phase != Phase.Thinking) return@LaunchedEffect
        val start = System.nanoTime()
        for (index in 1 until statuses.size) {
            sleepUntil(start + (THINK * index / statuses.size * 1e9).toLong())
            lastStatus = index - 1
            status = index
            motion.statusAt = motion.time.floatValue
        }
        sleepUntil(start + (THINK * 1e9).toLong())
        phase = Phase.Results
        launch { melt.animateTo(1f, tween(500, easing = EaseInOut)) }
        launch { panel.animateTo(1f, tween(700, easing = SoftBack)) }
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
                motion.keyAt = motion.time.floatValue
            }
            sleepUntil(start + (SUBMIT_AT * 1e9).toLong())
            submit()
            sleepUntil(start + (CLEAR_AT * 1e9).toLong())
            clear(prompt)
            lap += 1
            sleepUntil(epoch + (lap * LOOP * 1e9).toLong())
        }
    }

    // Any touch stops the script; it comes back a few seconds after the last one, or after
    // results that nobody touches.
    LaunchedEffect(auto, handsOff, phase, touches, text) {
        if (!auto || handsOff || phase == Phase.Thinking) return@LaunchedEffect
        delay((RESUME_AFTER * 1000).toLong())
        if (focused) return@LaunchedEffect
        clear(text)
        handsOff = true
    }

    fun touch() {
        handsOff = false
        touches += 1
    }

    fun edit(next: String) {
        if (!controlled) draft = next
        onValueChange?.invoke(next)
    }

    fun send() {
        if (!hasText) return
        touch()
        submit()
        focusManager.clearFocus()
        onSend?.invoke(text)
    }

    // It makes way at once, and comes back after the cleared query has faded.
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (hasText) 0f else 1f,
        animationSpec = if (hasText) snap() else tween(180, delayMillis = 150, easing = EaseOut),
        label = "placeholder",
    )

    Box(modifier.size(WIDTH.dp, (FRAME_TOP + FIELD_HEIGHT + GAP + panelHeight).dp)) {
        Backdrop(motion, melt, panel, panelHeight, still = reducedMotion)

        // The band on top of the frame: the pulsing dots and the label at the left, the step at the right.
        Row(
            Modifier
                .graphicsLayer {
                    val frame = FrameState(motion.since, melt.value, reducedMotion)
                    alpha = frame.label
                    translationY = (frame.top + frame.drop + 2f).dp.toPx()
                }
                .size(WIDTH.dp, BAND_HEIGHT.dp)
                .padding(horizontal = 14.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(12.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    SPARK.forEachIndexed { index, (x, y, r) ->
                        val pulse = if (reducedMotion) 1f else 0.6f + 0.4f * sin(motion.since * 6f - index * 1.4f)
                        drawCircle(Color.White, r, Offset(x, y), alpha = pulse)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            BasicText(thinkingLabel, style = ThinkingStyle, maxLines = 1)
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterEnd) {
                val slide = if (reducedMotion) 0f else 4f
                // The step that just ended leaves upwards while the next one arrives from below.
                BasicText(
                    statuses.getOrElse(lastStatus) { "" },
                    style = StatusStyle,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val swap = ((motion.time.floatValue - motion.statusAt) / STATUS).coerceIn(0f, 1f)
                        alpha = 1f - swap
                        translationY = -slide.dp.toPx() * swap
                    },
                )
                BasicText(
                    statuses.getOrElse(status) { "" },
                    style = StatusStyle,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val swap = ((motion.time.floatValue - motion.statusAt) / STATUS).coerceIn(0f, 1f)
                        alpha = swap
                        translationY = slide.dp.toPx() * (1f - swap)
                    },
                )
            }
        }

        Box(
            Modifier
                .offset(BORDER.dp, (FRAME_TOP + FIELD_HEIGHT + GAP).dp)
                .width(FIELD_WIDTH.dp)
                .graphicsLayer {
                    alpha = min(1f, panel.value.coerceIn(0f, 1f) * 3f)
                    shape = RoundedCornerShape(PANEL_RADIUS.dp)
                    clip = true
                }
                .background(Color.White)
                // The panel unrolls from its top edge, so the rows are uncovered one after another.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val open = if (reducedMotion) 1f else max(panel.value, 0f)
                    layout(placeable.width, (panelHeight.dp.toPx() * open).roundToInt()) { placeable.place(0, 0) }
                },
        ) {
            Column(Modifier.fillMaxWidth().height(panelHeight.dp)) {
                Box(
                    Modifier.height(PANEL_HEAD.dp).padding(start = 14.dp, top = 6.dp).graphicsLayer { alpha = reveal(panel.value, 0.15f) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(resultsLabel, style = HeadStyle, maxLines = 1)
                }
                rows.forEachIndexed { index, result ->
                    ResultRow(
                        result = result,
                        enabled = phase == Phase.Results,
                        modifier = Modifier.graphicsLayer {
                            val seen = reveal(panel.value, 0.3f + 0.18f * index)
                            alpha = seen
                            translationY = if (reducedMotion) 0f else -8.dp.toPx() * (1f - seen)
                        },
                        onClick = {
                            touch()
                            clear(text)
                            if (controlled) onValueChange?.invoke("")
                            onSelect?.invoke(result)
                        },
                    )
                }
            }
        }

        Box(
            Modifier
                .offset((BORDER + 16f).dp, FRAME_TOP.dp)
                .height(FIELD_HEIGHT.dp)
                .graphicsLayer { alpha = ghostFade.value }
                .clearAndSetSemantics {},
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(ghost, style = InputStyle, maxLines = 1)
        }

        Row(
            Modifier.offset(BORDER.dp, FRAME_TOP.dp).size(FIELD_WIDTH.dp, FIELD_HEIGHT.dp).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    touch()
                    edit(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(FIELD_HEIGHT.dp)
                    .clipToBounds()
                    .onFocusChanged { state ->
                        if (state.isFocused != focused) {
                            focused = state.isFocused
                            // A tap on the field takes it over from the script and starts from empty;
                            // a query typed by hand stays, and only its results go away.
                            if (focused && playing) clear(text) else if (focused && phase != Phase.Idle) dismiss()
                            touch()
                        }
                    }
                    .semantics { contentDescription = placeholder },
                textStyle = InputStyle,
                singleLine = true,
                cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { send() }),
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        BasicText(
                            placeholder,
                            style = PlaceholderStyle,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = placeholderAlpha },
                        )
                        field()
                        if (playing && hasText && phase == Phase.Idle) {
                            // The script does not focus the field, so until it submits it draws the
                            // caret itself: an invisible copy of the text pushes it to the end of the line.
                            Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
                                BasicText(text, style = InputStyle.copy(color = Color.Transparent), maxLines = 1)
                                Spacer(Modifier.width(1.dp))
                                Box(
                                    Modifier
                                        .size(2.dp, 19.dp)
                                        .graphicsLayer { alpha = caretOpacity(motion.time.floatValue - motion.keyAt) }
                                        .background(Orange, CircleShape),
                                )
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            VoiceButton(onClick = { onVoice?.invoke() })
        }
    }
}

/** Everything behind the texts: the halo of the results, the gradient frame, and the white field. */
@Composable
private fun Backdrop(motion: Motion, melt: Animatable<Float, *>, panel: Animatable<Float, *>, panelHeight: Float, still: Boolean) {
    val haloBloom = rememberBloom(18.dp)
    val shadowBloom = rememberBloom(12.dp)
    val lift = remember { SoftRoundRect(rgba(0, 0, 0, 0.08f), sigma = 6f) }

    // The gradient is two periods long and slides to the right, purple arriving from the left.
    fun flow(origin: Float): Brush {
        val phase = flowPhase(if (still) 0f else motion.since)
        return Brush.linearGradient(
            colorStops = FlowStops,
            start = Offset(origin - phase * FLOW_PERIOD, 0f),
            end = Offset(origin + (2f - phase) * FLOW_PERIOD, 0f),
        )
    }

    fun panelHeightNow() = if (still) panelHeight else panelHeight * max(panel.value, 0f)

    Box(Modifier.fillMaxSize().bleed(BLEED)) {
        // What is left of the frame: a soft halo of its colours around the results.
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = haloBloom
                alpha = if (haloBloom == null) 0f else 0.42f * panel.value.coerceIn(0f, 1f)
            },
        ) {
            surface(BLEED) { x, y, _, _ ->
                drawRoundRect(
                    flow(x),
                    Offset(x + BORDER + 2f, y + FRAME_TOP + FIELD_HEIGHT + GAP + 2f),
                    Size(FIELD_WIDTH - 4f, max(panelHeightNow() - 4f, 0f)),
                    CornerRadius(PANEL_RADIUS),
                )
            }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = shadowBloom
                alpha = if (shadowBloom == null) 0f else panel.value.coerceIn(0f, 1f)
            },
        ) {
            surface(BLEED) { x, y, _, _ ->
                drawRoundRect(
                    rgba(0, 0, 0, 0.1f),
                    Offset(x + BORDER + 10f, y + FRAME_TOP + FIELD_HEIGHT + GAP + 14f),
                    Size(FIELD_WIDTH - 20f, max(panelHeightNow() - 12f, 0f)),
                    CornerRadius(PANEL_RADIUS),
                )
            }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val frame = FrameState(motion.since, melt.value, still)
                alpha = frame.opacity
                renderEffect = blurOf(frame.blur.dp.toPx())
            },
        ) {
            surface(BLEED) { x, y, _, _ ->
                val frame = FrameState(motion.since, melt.value, still)
                drawRoundRect(
                    flow(x),
                    Offset(x + BORDER * (1f - frame.grow), y + frame.top + frame.drop),
                    Size(
                        FIELD_WIDTH + 2f * BORDER * frame.grow,
                        // A melting frame sinks a little and runs down twice as far, towards the results.
                        FIELD_HEIGHT + (FRAME_TOP + BORDER) * frame.grow + 2f * frame.drop,
                    ),
                    CornerRadius(FIELD_RADIUS + BORDER * frame.grow),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            surface(BLEED) { x, y, _, _ ->
                drawIntoCanvas {
                    lift.draw(it.nativeCanvas, x + BORDER + 6f, y + FRAME_TOP + 8f, FIELD_WIDTH - 12f, FIELD_HEIGHT - 8f, FIELD_RADIUS)
                }
                drawRoundRect(Color.White, Offset(x + BORDER, y + FRAME_TOP), Size(FIELD_WIDTH, FIELD_HEIGHT), CornerRadius(FIELD_RADIUS))
            }
        }
    }
}

@Composable
private fun ResultRow(result: ThinkingFrameSearchResult, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(ROW.dp)
            .background(if (pressed) Color(0xFFF2F2F2) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(Brush.linearGradient(result.tint), RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        BasicText(result.title, style = RowTitleStyle, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        BasicText(result.detail, style = RowDetailStyle, maxLines = 1)
    }
}

@Composable
private fun VoiceButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val dip by animateFloatAsState(if (pressed) 0.9f else 1f, tween(PRESS_SCALE_MS, easing = EaseOutStrong), label = "press")
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Voice search" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                WAVE_BARS.forEachIndexed { index, height ->
                    val x = 3.6f + 3.2f * index
                    drawLine(Mute, Offset(x, 10f - height / 2f), Offset(x, 10f + height / 2f), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}
