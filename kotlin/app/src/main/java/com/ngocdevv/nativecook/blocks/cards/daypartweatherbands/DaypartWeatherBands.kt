package com.ngocdevv.nativecook.blocks.cards.daypartweatherbands

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val LOOP = 8f

private const val ROW = 72f
private const val PHASE = 2.0
private const val HOLD = 1.35
private const val MOVE = 0.65
private const val DAMPING = 0.78
private const val OMEGA = 2 * PI / 0.45
private val HeaderInk = Color(0xFFFBF6EE)
private val Background = Color(0xFF4E363B)

private data class WeatherBand(
    val label: String,
    val spoken: String,
    val hours: String,
    val temp: String,
    val feels: String,
    val condition: String,
    val note: String,
    val stats: List<Pair<String, String>>,
    val colors: List<Color>,
    val ink: Color,
)

private val Bands = listOf(
    WeatherBand(
        "MORNING", "Morning, minus 1 degree, sunny", "6–11 AM", "−1°", "Feels −4°",
        "Sunny", "Clear and crisp until noon",
        listOf("WIND" to "E 7 mph", "HUMIDITY" to "91%", "PRECIP" to "0%"),
        listOf(Color(0xFFEDC897), Color(0xFFE3BB88), Color(0xFFD9AB79)), Color(0xFF5A2F1F),
    ),
    WeatherBand(
        "DAY", "Day, plus 3 degrees, partly cloudy", "11 AM–5 PM", "+3°", "Feels +1°",
        "Partly Cloudy", "Clouds drift in after lunch",
        listOf("WIND" to "N 5 mph", "HUMIDITY" to "45%", "PRECIP" to "10%"),
        listOf(Color(0xFFE2A472), Color(0xFFDB9864), Color(0xFFCE8858)), Color(0xFF47210F),
    ),
    WeatherBand(
        "EVENING", "Evening, plus 1 degree, light rain", "5–10 PM", "+1°", "Feels −2°",
        "Light Rain", "Showers ease off by nine",
        listOf("WIND" to "W 12 mph", "HUMIDITY" to "88%", "PRECIP" to "70%"),
        listOf(Color(0xFFBA7465), Color(0xFFB1695A), Color(0xFFA15D52)), Color(0xFFFFF2E6),
    ),
    WeatherBand(
        "NIGHT", "Night, minus 2 degrees, light snow", "10 PM–6 AM", "−2°", "Feels −6°",
        "Light Snow", "Flurries settle before dawn",
        listOf("WIND" to "NW 9 mph", "HUMIDITY" to "84%", "PRECIP" to "60%"),
        listOf(Color(0xFF6B4D51), Color(0xFF5F4346), Color(0xFF4E363B)), Color(0xFFF7E9DD),
    ),
)

private fun wrap(t: Double): Double = ((t % LOOP) + LOOP) % LOOP

private fun springStep(s: Double): Double {
    val wd = OMEGA * sqrt(1 - DAMPING * DAMPING)
    return 1 - exp(-DAMPING * OMEGA * s) * (cos(wd * s) + DAMPING * OMEGA / wd * sin(wd * s))
}

private fun handoff(s: Double): Double = when {
    s <= 0 -> 0.0
    s >= MOVE -> 1.0
    else -> springStep(s) + (1 - springStep(MOVE)) * s / MOVE
}

private fun autoWeights(t: Double): FloatArray {
    val time = wrap(t)
    val k = floor(time / PHASE).toInt() % 4
    val p = handoff(time - k * PHASE - HOLD)
    // Preserve the spring's small overshoot, including the outgoing negative weight.
    return FloatArray(4).also {
        it[k] = (1 - p).toFloat()
        it[(k + 1) % 4] += p.toFloat()
    }
}

private fun rubber(f: Float): Float = when {
    f < 0 -> f * 0.55f / (1 - f * 1.2f)
    f > 3 -> 3 + (f - 3) * 0.55f / (1 + (f - 3) * 1.2f)
    else -> f
}

/** Frame values are read only during drawing, placement, or graphics-layer updates. */
private class WeatherPlayback(private val scope: CoroutineScope) {
    val weights = Array(4) { mutableFloatStateOf(if (it == 0) 1f else 0f) }
    val alpha = mutableFloatStateOf(1f)
    val clock = mutableDoubleStateOf(0.0)
    private val springs = List(4) { Animatable(if (it == 0) 1f else 0f) }
    private var transition: Job? = null
    private var idle: Job? = null
    private var clockOrigin = -1L
    private var loopOrigin = -1L
    private var resumeBand = -1
    private var playing = true
    private var reduced = false
    private var autoPlay = false
    var generation = 0
        private set

    fun restart(reducedMotion: Boolean, auto: Boolean) {
        takeControl()
        reduced = reducedMotion
        autoPlay = auto
        clockOrigin = -1L
        loopOrigin = -1L
        playing = true
        clock.doubleValue = if (reduced) 0.45 else 0.0
        setWeights(FloatArray(4) { if (it == 0) 1f else 0f })
    }

    fun frame(now: Long) {
        if (clockOrigin < 0) clockOrigin = now
        if (reduced) return
        clock.doubleValue = wrap((now - clockOrigin) / 1e9)
        if (resumeBand >= 0) {
            loopOrigin = now - (resumeBand * PHASE * 1e9).toLong()
            resumeBand = -1
            playing = true
        }
        if (!playing || !autoPlay) return
        if (loopOrigin < 0) loopOrigin = now
        setWeights(autoWeights((now - loopOrigin) / 1e9))
    }

    fun setWeights(values: FloatArray) {
        repeat(4) { weights[it].floatValue = values[it] }
    }

    fun dominant(): Int {
        var best = 0
        for (i in 1..3) if (weights[i].floatValue > weights[best].floatValue) best = i
        return best
    }

    fun focus(): Float = if (weights[0].floatValue < 0.01f || weights[3].floatValue < 0.01f) {
        weights[1].floatValue + 2 * weights[2].floatValue + 3 * weights[3].floatValue
    } else {
        dominant().toFloat()
    }

    fun top(index: Int, open: Float): Float {
        var top = 0f
        repeat(index) { top += ROW + (open - ROW) * weights[it].floatValue }
        return top
    }

    fun bandAt(y: Float, open: Float): Int {
        for (i in 3 downTo 1) if (y >= top(i, open)) return i
        return 0
    }

    fun takeControl() {
        generation++
        playing = false
        resumeBand = -1
        idle?.cancel()
        transition?.cancel()
        alpha.floatValue = 1f
    }

    fun drag(focus: Float) {
        val f = rubber(focus)
        setWeights(FloatArray(4) { max(0f, 1 - abs(f - it)) })
    }

    fun settle(target: Int) {
        transition?.cancel()
        transition = scope.launch {
            if (reduced) {
                // Manual timing preserves the fade at animator duration scale 0.
                crossFade(target)
            } else {
                coroutineScope {
                    repeat(4) { index ->
                        launch {
                            val animation = springs[index]
                            animation.snapTo(weights[index].floatValue)
                            animation.animateTo(
                                if (index == target) 1f else 0f,
                                spring(dampingRatio = 0.78f, stiffness = 195f),
                            ) { weights[index].floatValue = value }
                        }
                    }
                }
            }
        }
    }

    private suspend fun crossFade(target: Int) {
        alpha.floatValue = 1f
        val start = withFrameNanos { it }
        var elapsed = 0.0
        var jumped = false
        while (elapsed < 300) {
            withFrameNanos { now ->
                elapsed = (now - start) / 1e6
                if (elapsed < 110) {
                    alpha.floatValue = (1 - elapsed / 110).toFloat()
                } else {
                    if (!jumped) {
                        setWeights(FloatArray(4) { if (it == target) 1f else 0f })
                        jumped = true
                    }
                    alpha.floatValue = ((elapsed - 110) / 190).toFloat().coerceIn(0f, 1f)
                }
            }
        }
    }

    fun startIdleTimer() {
        val token = ++generation
        idle?.cancel()
        idle = scope.launch {
            delay(2_000)
            if (generation == token && !reduced && autoPlay) resumeBand = dominant()
        }
    }
}

/** Four sliding dayparts, sharing one deterministic eight-second day. */
@Composable
fun DaypartWeatherBands(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val playback = remember(scope) { WeatherPlayback(scope) }
    val density = LocalDensity.current
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var resumes by remember(lifecycle) { mutableIntStateOf(0) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle, playback) {
        val observer = LifecycleEventObserver { _, event ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) resumes++
            if (!active) playback.takeControl()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            playback.takeControl()
        }
    }

    LaunchedEffect(reduced, autoPlay, active, resumes, playback) {
        playback.restart(reduced, autoPlay)
        if (active && !reduced) {
            while (isActive) withFrameNanos { playback.frame(it) }
        }
    }

    fun select(index: Int, previous: Int = playback.dominant()) {
        if (index != previous) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playback.settle(index)
    }

    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val width = maxWidth.value
        val headerBottom = statusTop.value + 64
        val fullHeight = maxHeight.value - headerBottom
        val openHeight = fullHeight - navigationBottom.value - 3 * ROW
        val span = max(1f, openHeight - ROW)

        Box(
            Modifier.offset(y = headerBottom.dp).fillMaxWidth().height(fullHeight.dp)
                .pointerInput(reduced, active, span, density.density) {
                    if (!active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        playback.takeControl()
                        val token = playback.generation
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        var dragging = false
                        var startFocus = 0f
                        var startBand = playback.dominant()
                        var y = down.position.y
                        var maxMovement = 0f
                        var released = false
                        var endedAt = down.uptimeMillis
                        down.consume()
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (playback.generation != token || change.isConsumed) break
                                y = change.position.y
                                endedAt = change.uptimeMillis
                                tracker.addPointerInputChange(change)
                                val delta = (change.position - down.position) / density.density
                                maxMovement = max(maxMovement, delta.getDistance())
                                if (!dragging && abs(delta.y) >= 10f) {
                                    dragging = true
                                    startFocus = playback.focus()
                                    startBand = playback.dominant()
                                }
                                if (!change.pressed) {
                                    released = true
                                    change.consume()
                                    break
                                }
                                if (dragging && !reduced) playback.drag(startFocus - delta.y / span)
                                change.consume()
                            }
                        } finally {
                            if (playback.generation == token) {
                                val dy = (y - down.position.y) / density.density
                                if (released && dragging) {
                                    val target = if (reduced) {
                                        (startBand + if (dy < 0) 1 else -1).coerceIn(0, 3)
                                    } else {
                                        val velocityY = tracker.calculateVelocity().y / density.density
                                        val projected = startFocus - dy / span - 0.18f * velocityY / span
                                        floor(projected + 0.5f).toInt().coerceIn(0, 3)
                                    }
                                    select(target, startBand)
                                } else if (released && maxMovement < 10f && endedAt - down.uptimeMillis < 500) {
                                    select(playback.bandAt(y / density.density, openHeight))
                                } else if (dragging && !reduced) {
                                    playback.settle(playback.dominant())
                                }
                                playback.startIdleTimer()
                            }
                        }
                    }
                },
        ) {
            // Full-height surfaces overlap: later dayparts cover earlier details.
            Bands.forEachIndexed { index, band ->
                WeatherBandView(index, band, width, fullHeight, openHeight, playback)
            }
            // Full-height overlapping semantics would hide the earlier buttons.
            Bands.forEachIndexed { index, band ->
                Box(
                    Modifier.offset {
                        IntOffset(0, (playback.top(index, openHeight) * density.density).roundToInt())
                    }.fillMaxWidth().height(ROW.dp).clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = band.spoken
                        onClick {
                            playback.takeControl()
                            select(index)
                            playback.startIdleTimer()
                            true
                        }
                    },
                )
            }
        }
        WeatherHeader(statusTop.value, headerBottom)
        Box(
            Modifier.offset(y = headerBottom.dp).fillMaxWidth().height(10.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF28221A).copy(alpha = 0.16f), Color(0xFF28221A).copy(alpha = 0f)))),
        )
    }
}

private fun Modifier.reveal(
    playback: WeatherPlayback, index: Int, a: Float, b: Float, rise: Float, density: Float, grow: Float = 0f,
): Modifier = graphicsLayer {
    val x = ((playback.weights[index].floatValue - a) / (b - a)).coerceIn(0f, 1f)
    val u = 1 - x
    val e = 1 - u * u * u
    alpha = e * playback.alpha.floatValue
    translationY = rise * (1 - e) * density
    scaleX = 1 - grow + grow * e
    scaleY = scaleX
}

@Composable
private fun WeatherBandView(
    index: Int, band: WeatherBand, width: Float, fullHeight: Float, openHeight: Float, playback: WeatherPlayback,
) {
    val density = LocalDensity.current.density
    val hairTop = max(ROW + 200, openHeight - 100)
    val detailTop = ROW + max(4f, floor((hairTop - ROW - 160) / 2 + 0.5f))
    val columnWidth = (width - 48) / 3
    Box(
        Modifier.fillMaxWidth().height(fullHeight.dp)
            .graphicsLayer { translationY = playback.top(index, openHeight) * density }
            .clearAndSetSemantics { }
            .drawWithCache {
                val gradientHeight = min(openHeight, fullHeight) * density
                val fill = Brush.verticalGradient(
                    0f to band.colors[0], 0.45f to band.colors[1], 1f to band.colors[2], endY = gradientHeight,
                )
                val cast = Brush.verticalGradient(
                    listOf(Color(0xFF2E140E).copy(alpha = 0f), Color(0xFF2E140E).copy(alpha = 0.16f)),
                    startY = -14 * density, endY = 0f,
                )
                onDrawBehind {
                    if (index > 0) drawRect(cast, Offset(0f, -14 * density), Size(size.width, 14 * density))
                    drawRect(band.colors[2])
                    drawRect(fill, size = Size(size.width, gradientHeight))
                    if (index > 0) drawRect(Color.White.copy(alpha = 0.14f), size = Size(size.width, density))
                }
            },
    ) {
        WeatherText(band.label, Modifier.offset(24.dp, 16.dp), 11f, FontWeight.Bold, band.ink, tracking = 2.2f)
        WeatherText(band.temp, Modifier.offset(23.dp, 30.dp), 30f, FontWeight.Light, band.ink, 36f)
        WeatherText(
            band.hours, Modifier.align(Alignment.TopEnd).offset((-24).dp, 16.dp),
            12f, FontWeight.SemiBold, band.ink.copy(alpha = 0.74f),
        )
        WeatherText(
            band.feels, Modifier.align(Alignment.TopEnd).offset((-24).dp, 38.dp),
            13f, FontWeight.Normal, band.ink.copy(alpha = 0.84f),
        )
        Canvas(
            Modifier.offset(14.dp, detailTop.dp).size(160.dp)
                .reveal(playback, index, 0.15f, 0.8f, 18f, density, 0.28f),
        ) {
            clipRect {
                scale(density, density, Offset.Zero) { weatherIcon(index, playback.clock.doubleValue) }
            }
        }
        WeatherText(
            band.condition,
            Modifier.offset(184.dp, (detailTop + 42).dp).width((width - 204).dp)
                .reveal(playback, index, 0.35f, 0.9f, 10f, density),
            24f, FontWeight.Normal, band.ink, 30f,
        )
        // The RN animated style overrides the note's static opacity; use reveal alpha alone.
        WeatherText(
            band.note,
            Modifier.offset(184.dp, (detailTop + 78).dp).width((width - 204).dp)
                .reveal(playback, index, 0.42f, 0.95f, 10f, density),
            14f, FontWeight.Normal, band.ink, 19f, maxLines = 2,
        )
        Column(
            Modifier.offset(24.dp, hairTop.dp).width((width - 48).dp)
                .reveal(playback, index, 0.5f, 1f, 8f, density),
        ) {
            // RN hairlineWidth * 2 is two physical pixels, not two dp.
            Box(Modifier.fillMaxWidth().height((2f / density).dp).background(band.ink.copy(alpha = 0.2f)))
            Spacer(Modifier.height(16.dp))
            Row {
                band.stats.forEach { (caption, value) ->
                    Column(Modifier.width(columnWidth.dp)) {
                        WeatherText(caption, Modifier, 10.5f, FontWeight.Bold, band.ink.copy(alpha = 0.66f), tracking = 1.6f)
                        Spacer(Modifier.height(4.dp))
                        WeatherText(value, Modifier, 17f, FontWeight.Medium, band.ink, 22f)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherText(
    text: String, modifier: Modifier, size: Float, weight: FontWeight, ink: Color,
    lineHeight: Float? = null, tracking: Float = 0f, maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = ink, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif,
            lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
            // RN tracking is an absolute point distance, so it maps to sp.
            letterSpacing = tracking.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = maxLines, overflow = TextOverflow.Clip,
    )
}

@Composable
private fun WeatherHeader(statusTop: Float, height: Float) {
    Box(
        Modifier.fillMaxWidth().height(height.dp).clipToBounds()
            .background(Brush.verticalGradient(listOf(Color(0xFF94B19B), Color(0xFF8BA892), Color(0xFF83A08A)))),
    ) {
        Canvas(Modifier.offset(24.dp, (statusTop + 15).dp).size(14.dp, 18.dp)) {
            scale(density, density, Offset.Zero) { drawPath(WeatherArtwork.pin, HeaderInk) }
        }
        WeatherText("Larkhaven", Modifier.offset(46.dp, (statusTop + 11).dp), 17f, FontWeight.SemiBold, HeaderInk, 22f)
        WeatherText(
            "Tuesday, 10 November", Modifier.offset(46.dp, (statusTop + 34).dp),
            12f, FontWeight.Medium, HeaderInk.copy(alpha = 0.78f), 16f,
        )
        Canvas(Modifier.align(Alignment.TopEnd).offset((-24).dp, (statusTop + 23).dp).size(51.dp, 18.dp)) {
            scale(density, density, Offset.Zero) {
                repeat(4) { i ->
                    drawRoundRect(
                        HeaderInk.copy(alpha = 0.92f), Offset((i % 2) * 10f, 0.5f + (i / 2) * 10f),
                        Size(7f, 7f), CornerRadius(1.5f, 1.5f),
                    )
                }
                repeat(3) { i ->
                    val y = 1.5f + i * 6f
                    drawCircle(HeaderInk, 1.5f, Offset(34.5f, y + 1.5f))
                    drawRoundRect(
                        HeaderInk.copy(alpha = 0.92f), Offset(39f, y + 0.25f),
                        Size(12f, 2.5f), CornerRadius(1.25f, 1.25f),
                    )
                }
            }
        }
    }
}

private const val WEATHER_TAU = 2 * PI

/** Static artwork uses logical points inside each 160 × 160 icon canvas. */
private object WeatherArtwork {
    private fun circle(x: Float, y: Float, radius: Float) = Path().apply {
        addOval(Rect(x - radius, y - radius, x + radius, y + radius))
    }

    val cloud: Path = run {
        var shape = Path().apply {
            addRoundRect(RoundRect(Rect(-48f, 2f, 48f, 28f), CornerRadius(13f, 13f)))
        }
        for (puff in listOf(circle(-24f, 6f, 20f), circle(4f, -6f, 26f), circle(30f, 8f, 17f))) {
            shape = Path.combine(PathOperation.Union, shape, puff)
        }
        shape
    }

    val moon: Path = Path.combine(
        PathOperation.Difference, circle(62f, 58f, 26f), circle(75f, 47f, 22f),
    )

    val pin: Path = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(7f, 18f)
        cubicTo(7f, 18f, 0f, 10.5f, 0f, 7f)
        cubicTo(0f, 3.1f, 3.1f, 0f, 7f, 0f)
        cubicTo(10.9f, 0f, 14f, 3.1f, 14f, 7f)
        cubicTo(14f, 10.5f, 7f, 18f, 7f, 18f)
        close()
        addOval(Rect(4.4f, 4.4f, 9.6f, 9.6f))
    }

    val drop: Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(-2f, 8f)
    }

    val flake: Path = Path().apply {
        repeat(3) { i ->
            val a = i * PI / 3 + PI / 2
            val x = (cos(a) * 4.5).toFloat()
            val y = (sin(a) * 4.5).toFloat()
            moveTo(x, y)
            lineTo(-x, -y)
        }
    }

    fun hash(index: Int): Double {
        val value = sin(index * 12.9898 + 1.7) * 43758.5453
        return value - floor(value)
    }

    val rainX = doubleArrayOf(58.0, 73.0, 88.0, 103.0, 118.0, 133.0)
    val snowX = doubleArrayOf(54.0, 68.0, 82.0, 96.0, 110.0, 124.0, 140.0)
    val rainHash = DoubleArray(rainX.size) { hash(it) }
    val snowHash = DoubleArray(snowX.size) { hash(it + 11) }
}

private fun DrawScope.weatherRays(
    cx: Double, cy: Double, count: Int, r1: Double, length: Double,
    wobble: Double, turn: Double, c: Double, color: Color, width: Float,
) {
    repeat(count) { i ->
        val angle = turn + i * WEATHER_TAU / count - PI / 2
        val rayLength = length + wobble * sin(WEATHER_TAU * 2 * c / 8 + i * WEATHER_TAU * 2 / count)
        val ca = cos(angle)
        val sa = sin(angle)
        drawLine(
            color,
            Offset((cx + ca * r1).toFloat(), (cy + sa * r1).toFloat()),
            Offset((cx + ca * (r1 + rayLength)).toFloat(), (cy + sa * (r1 + rayLength)).toFloat()),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.weatherCloud(x: Double, y: Double, body: Color, rim: Color, zoom: Float = 1f) {
    translate(x.toFloat(), y.toFloat()) {
        scale(zoom, zoom, Offset.Zero) {
            drawPath(WeatherArtwork.cloud, rim)
            translate(top = 3f) { drawPath(WeatherArtwork.cloud, body) }
        }
    }
}

private fun weatherParticleFade(q: Double): Float {
    val enter = (q / 0.15).coerceIn(0.0, 1.0)
    val leave = ((q - 0.6) / 0.4).coerceIn(0.0, 1.0)
    return (enter * (1 - leave)).toFloat()
}

private fun DrawScope.weatherIcon(index: Int, c: Double) {
    when (index) {
        0 -> {
            val sun = Color(0xFFE57A3A)
            drawCircle(
                Color(0xFFFFF3DE).copy(alpha = 0.45f),
                (52 + 3 * sin(WEATHER_TAU * 2 * c / 8)).toFloat(), Offset(80f, 80f),
            )
            drawCircle(sun, 29f, Offset(80f, 80f), style = Stroke(4.5f))
            drawCircle(sun, 21f, Offset(80f, 80f))
            weatherRays(80.0, 80.0, 10, 38.0, 8.0, 3.0, c / 8 * (2 * WEATHER_TAU / 10), c, sun, 6f)
        }
        1 -> {
            val sun = Color(0xFFFFE3B3)
            val glow = 0.55 + 0.45 * (0.5 + 0.5 * sin(WEATHER_TAU * c / 8))
            drawCircle(Color(0xFFFFECC8).copy(alpha = (0.32 * glow).toFloat()), 38f, Offset(60f, 56f))
            weatherRays(60.0, 56.0, 8, 26.0, 6.0, 2.0, c / 8 * (WEATHER_TAU / 8), c, sun, 4.5f)
            drawCircle(sun, 18f, Offset(60f, 56f))
            weatherCloud(
                90 + 12 * sin(WEATHER_TAU * c / 8), 98 + 2 * sin(WEATHER_TAU * 2 * c / 8),
                Color(0xFF5C3534), Color(0xFF704543),
            )
        }
        2 -> {
            val sun = Color(0xFF7A3A30)
            weatherRays(50.0, 50.0, 7, 22.0, 5.0, 1.5, c / 8 * (WEATHER_TAU / 7), c, sun, 4f)
            drawCircle(sun, 15f, Offset(50f, 50f))
            WeatherArtwork.rainX.forEachIndexed { i, x ->
                val progress = c + WeatherArtwork.rainHash[i]
                val q = progress - floor(progress)
                translate((x - 4 - 6 * q).toFloat(), (112 + 38 * q).toFloat()) {
                    drawPath(
                        WeatherArtwork.drop, Color(0xFFF6D5AE), alpha = weatherParticleFade(q),
                        style = Stroke(4f, cap = StrokeCap.Round),
                    )
                }
            }
            weatherCloud(
                88.0, 80 + 2 * sin(WEATHER_TAU * 2 * c / 8), Color(0xFFF6D5AE), Color(0xFFFFE8CB),
            )
        }
        else -> {
            drawCircle(Color(0xFFF2A86C).copy(alpha = 0.18f), 40f, Offset(62f, 58f))
            rotate((8 * sin(WEATHER_TAU * c / 8)).toFloat(), Offset(62f, 58f)) {
                drawPath(WeatherArtwork.moon, Color(0xFFF2A86C))
            }
            WeatherArtwork.snowX.forEachIndexed { i, x ->
                val h = WeatherArtwork.snowHash[i]
                val progress = c / 2 + h
                val q = progress - floor(progress)
                translate((x + 4 * sin(WEATHER_TAU * (q + h))).toFloat(), (106 + 46 * q).toFloat()) {
                    rotate((60 * q).toFloat(), Offset.Zero) {
                        drawPath(
                            WeatherArtwork.flake, Color(0xFFF6DEC4), alpha = weatherParticleFade(q),
                            style = Stroke(1.8f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            weatherCloud(
                102 + 6 * sin(WEATHER_TAU * c / 8 + PI / 2), 88.0,
                Color(0xFFEBD0B0), Color(0xFFF7E3CB), 0.78f,
            )
        }
    }
}
