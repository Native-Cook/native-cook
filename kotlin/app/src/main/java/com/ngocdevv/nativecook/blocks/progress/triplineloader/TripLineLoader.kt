package com.ngocdevv.nativecook.blocks.progress.triplineloader

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

const val LOOP = 8.0f

/** A trip drawn one leg at a time; drag the line artwork to scrub its eight-second story. */
@Composable
fun TripLineLoader(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberLiveReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val playing by rememberUpdatedState(autoPlay)
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val foreground by rememberUpdatedState(active)
    val playback = remember { TripLineLoaderPlayback() }
    val shadow = remember(density) { TripLineLoaderShadow(density) }
    BlockSystemBars(darkIcons = true)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            playback.generation++
        }
    }

    fun impact() {
        val now = System.nanoTime()
        if (!reduced && now - playback.lastHaptic >= 100_000_000L) {
            playback.lastHaptic = now
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(autoPlay, reducedMotion, active) { playback.restart() }

    // Only mode boundaries recompose; visual time is read by drawing and opacity lambdas.
    LaunchedEffect(playback.mode, playback.generation, autoPlay, reducedMotion, active) {
        if (!autoPlay || reducedMotion || !active || playback.mode != TripLineLoaderPlayback.Mode.Playing) {
            return@LaunchedEffect
        }
        val token = playback.generation
        var previousTick = TripLineLoaderMotion.tickBucket(0.0)
        while (playback.generation == token) {
            withFrameNanos { now ->
                if (playback.generation == token) {
                    val elapsed = max(0.0, (now - playback.origin) / 1e9)
                    playback.setTime(TripLineLoaderMotion.wrap(elapsed))
                    val tick = TripLineLoaderMotion.tickBucket(elapsed)
                    if (tick > previousTick) impact()
                    previousTick = tick
                }
            }
        }
    }

    // A cancelled gesture also changes the token, so an old idle timer cannot own a new drag.
    LaunchedEffect(playback.generation, reducedMotion, active) {
        val token = playback.generation
        if (reducedMotion || !active || playback.dragging || playback.mode != TripLineLoaderPlayback.Mode.Held) {
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                animate(
                    initialValue = playback.lean.floatValue,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 246.7401f),
                ) { value, _ ->
                    // Swift response 0.4 s: stiffness = (2π / 0.4)² for unit mass.
                    if (playback.generation == token) playback.lean.floatValue = value
                }
            }
            delay(2_000)
            if (playback.generation != token || playback.dragging || reduced || !foreground) return@coroutineScope
            val from = TripLineLoaderMotion.wrap(playback.held)
            val to = if (from < 4) 0.0 else LOOP.toDouble()
            val duration = min(0.9, 0.35 + 0.08 * abs(to - from))
            val start = withFrameNanos { it }
            playback.returnFrom = from
            playback.returnTo = to
            playback.returnStart = start
            playback.returnDuration = duration
            playback.mode = TripLineLoaderPlayback.Mode.Returning
            var elapsed = 0.0
            while (elapsed < duration && playback.generation == token) {
                withFrameNanos { now ->
                    elapsed = max(0.0, (now - start) / 1e9)
                    if (playback.generation == token) playback.setTime(playback.timeAt(now, playing))
                }
            }
            if (playback.generation == token && !playback.dragging && !reduced && foreground) playback.restart()
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(TripLineLoaderPalette.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val width = maxWidth.value.toDouble()
        val route = remember(width) {
            TripLineLoaderTrimmedPath(TripLineLoaderArtwork.line(68.0, 8.0, width - 68, 8.0))
        }
        TripLineLoaderChrome()
        Box(
            Modifier.offset(y = 150.dp).fillMaxWidth().height(250.dp)
                .semantics { contentDescription = "Trip loading animation. Drag sideways to scrub." }
                .pointerInput(reducedMotion, active, density) {
                    if (reducedMotion || !active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val frozen = playback.timeAt(System.nanoTime(), playing)
                        playback.generation++
                        val token = playback.generation
                        playback.dragging = true
                        playback.held = frozen
                        playback.setTime(frozen)
                        playback.mode = TripLineLoaderPlayback.Mode.Held
                        val startX = down.position.x
                        var previousScrub = frozen
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        down.consume()
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (playback.generation != token || reduced || !foreground || change.isConsumed) break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val next = frozen + (change.position.x - startX) / density * LOOP / 320.0
                                if (TripLineLoaderMotion.tickBucket(next) != TripLineLoaderMotion.tickBucket(previousScrub)) impact()
                                previousScrub = next
                                playback.held = next
                                playback.setTime(TripLineLoaderMotion.wrap(next))
                                val vx = tracker.calculateVelocity().x / density
                                playback.lean.floatValue = if (vx.isFinite()) (vx / 900f).coerceIn(-1f, 1f) * 6f else 0f
                                change.consume()
                            }
                        } finally {
                            if (playback.generation == token && playback.dragging) {
                                playback.dragging = false
                                playback.generation++
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, Offset.Zero) {
                    TripLineLoaderDrawing.shadow(this, shadow, playback.time.value, width, reducedMotion)
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = playback.lean.floatValue
                    transformOrigin = TransformOrigin(0.5f, 112f / 250f)
                },
            ) {
                scale(density, density, Offset.Zero) {
                    TripLineLoaderDrawing.icons(this, playback.time.value, width, reducedMotion)
                }
            }
        }
        TripLineLoaderRoute(Modifier.offset(y = 416.dp), width, route, playback.time, reducedMotion)
        repeat(3) { index ->
            TripLineLoaderRow(
                index, width - 48, playback.time, playback.rowStatus[index], reducedMotion,
                Modifier.offset(x = 24.dp, y = (476 + index * 78).dp),
            )
        }
        BasicText(
            "Matching fares, rides and rooms",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                // Place the baseline, rather than the line-box bottom, above the safe area.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable[FirstBaseline]) { placeable.placeRelative(0, 0) }
                },
            style = tripLineLoaderTextStyle(13, color = TripLineLoaderPalette.muted),
            maxLines = 1,
        )
    }
}

private class TripLineLoaderPlayback {
    enum class Mode { Playing, Held, Returning }
    var mode by mutableStateOf(Mode.Playing)
    var generation by mutableIntStateOf(0)
    var dragging by mutableStateOf(false)
    var origin by mutableLongStateOf(System.nanoTime())
    var held by mutableStateOf(0.0)
    var returnFrom by mutableStateOf(0.0)
    var returnTo by mutableStateOf(0.0)
    var returnStart by mutableLongStateOf(0L)
    var returnDuration by mutableStateOf(0.0)
    var lastHaptic = 0L
    val time = mutableStateOf(0.0)
    val lean = mutableFloatStateOf(0f)
    // Accessibility observes only threshold changes, never the per-frame clock.
    val rowStatus = List(3) { mutableIntStateOf(if (it == 0) 1 else 0) }

    fun setTime(t: Double) {
        time.value = t
        for (i in 0..2) {
            val row = TripLineLoaderMotion.row(i, t, reduced = false)
            rowStatus[i].intValue = if (row.done > 0.5) 2 else if (row.active > 0.5) 1 else 0
        }
    }

    fun timeAt(now: Long, autoPlay: Boolean): Double = when (mode) {
        Mode.Playing -> if (autoPlay) TripLineLoaderMotion.wrap(max(0.0, (now - origin) / 1e9)) else 0.0
        Mode.Held -> TripLineLoaderMotion.wrap(held)
        Mode.Returning -> {
            val e = TripLineLoaderMotion.inOut(TripLineLoaderMotion.seg((now - returnStart) / 1e9, 0.0, returnDuration))
            TripLineLoaderMotion.wrap(returnFrom + (returnTo - returnFrom) * e)
        }
    }

    fun restart() {
        generation++
        dragging = false
        lean.floatValue = 0f
        origin = System.nanoTime()
        setTime(0.0)
        mode = Mode.Playing
    }
}

private object TripLineLoaderPalette {
    val bg = Color(0xFFFFFBF8)
    val card = Color(0xFFFFFFFF)
    val cardBorder = Color(0xFFF1E6E1)
    val coral = Color(0xFFE64C3B)
    val coralSoft = Color(0xFFFCE9E6)
    val ink = Color(0xFF2A1F1D)
    val muted = Color(0xFF8D7F7A)
    val track = Color(0xFFE9DDD8)
}

private fun tripLineLoaderTextStyle(
    size: Int, weight: FontWeight = FontWeight.Normal, color: Color = TripLineLoaderPalette.ink,
) = TextStyle(
    fontSize = size.sp, fontWeight = weight, color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun TripLineLoaderChrome() {
    Row(
        Modifier.offset(x = 24.dp, y = 8.dp).fillMaxWidth().padding(end = 48.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            "Farewind",
            style = tripLineLoaderTextStyle(17, FontWeight.SemiBold, TripLineLoaderPalette.coral).copy(letterSpacing = 0.2.sp),
        )
        Spacer(Modifier.weight(1f))
        BasicText("Cancel", style = tripLineLoaderTextStyle(15, color = TripLineLoaderPalette.muted))
    }
    Box(
        Modifier.offset(y = 68.dp).fillMaxWidth().padding(horizontal = 24.dp).height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Building your trip",
            style = tripLineLoaderTextStyle(30, FontWeight.Bold).copy(letterSpacing = (-0.4).sp),
            maxLines = 1,
        )
    }
    Box(
        Modifier.offset(y = 112.dp).fillMaxWidth().padding(horizontal = 24.dp).height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Lisbon to Kyoto · 12–18 Mar · 2 guests",
            style = tripLineLoaderTextStyle(15, color = TripLineLoaderPalette.muted), maxLines = 1,
        )
    }
}

@Composable
private fun TripLineLoaderRoute(
    modifier: Modifier, width: Double, path: TripLineLoaderTrimmedPath, time: State<Double>, reduced: Boolean,
) {
    val trackStyle = remember { Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))) }
    Box(modifier.fillMaxWidth().height(40.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, Offset.Zero) {
                drawPath(path.path, TripLineLoaderPalette.track, style = trackStyle)
                val t = time.value
                val fill = TripLineLoaderMotion.route(t, reduced)
                path.draw(this, fill.from, fill.to, TripLineLoaderPalette.coral, 2.5f)
                val done = TripLineLoaderMotion.row(2, t, reduced).done
                val pulse = if (reduced) 0.0 else sin(PI * TripLineLoaderMotion.seg(t, 6.22, 6.52)) * 0.4
                for (x in doubleArrayOf(68.0, width - 68)) {
                    val r = 3.5 * if (x == 68.0) 1.0 else 1 + pulse
                    drawCircle(TripLineLoaderPalette.track, r.toFloat(), Offset(x.toFloat(), 8f))
                    drawCircle(
                        TripLineLoaderPalette.coral, r.toFloat(), Offset(x.toFloat(), 8f),
                        alpha = if (x == 68.0) 1f else done.toFloat(),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            TripLineLoaderCity("LIS", "Lisbon", Alignment.Start)
            Spacer(Modifier.weight(1f))
            TripLineLoaderCity("KIX", "Kyoto", Alignment.End)
        }
    }
}

@Composable
private fun TripLineLoaderCity(code: String, name: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            BasicText(code, style = tripLineLoaderTextStyle(13, FontWeight.SemiBold).copy(letterSpacing = 0.6.sp))
        }
        BasicText(name, style = tripLineLoaderTextStyle(12, color = TripLineLoaderPalette.muted))
    }
}

@Composable
private fun TripLineLoaderRow(
    index: Int, width: Double, time: State<Double>, status: State<Int>, reduced: Boolean, modifier: Modifier,
) {
    val copy = TripLineLoaderArtwork.rows[index]
    val check = remember { TripLineLoaderTrimmedPath(TripLineLoaderArtwork.check) }
    val card = remember(width) { TripLineLoaderArtwork.roundedRect(0.0, 0.0, width, 68.0, 16.0) }
    val border = remember(width) { TripLineLoaderArtwork.roundedRect(0.5, 0.5, width - 1, 67.0, 15.5) }
    Box(
        modifier.width(width.dp).height(68.dp).drawBehind {
            val state = TripLineLoaderMotion.row(index, time.value, reduced)
            scale(density, density, Offset.Zero) {
                drawPath(card, TripLineLoaderPalette.card)
                drawPath(card, TripLineLoaderPalette.coralSoft, alpha = state.active.toFloat())
                drawPath(border, TripLineLoaderPalette.cardBorder, style = Stroke(1f))
                drawPath(border, TripLineLoaderPalette.coral, alpha = (0.35 * state.active).toFloat(), style = Stroke(1f))
                translate(16f, 22f) { TripLineLoaderDrawing.status(this, check, state, time.value, reduced) }
            }
        }.clearAndSetSemantics {
            contentDescription = copy.title + ", " + copy.detail
            stateDescription = when {
                reduced || status.value == 2 -> copy.price
                status.value == 1 -> "Searching"
                else -> "Waiting"
            }
        },
    ) {
        Column(
            Modifier.offset(x = 56.dp, y = 14.dp).width((width - 56 - 16 - 82 - 8).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(copy.title, style = tripLineLoaderTextStyle(16, FontWeight.SemiBold), maxLines = 1)
            BasicText(
                copy.detail, style = tripLineLoaderTextStyle(13, color = TripLineLoaderPalette.muted),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.offset(x = (width - 98).dp).width(82.dp).height(68.dp), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                "Waiting", Modifier.graphicsLayer { alpha = TripLineLoaderMotion.row(index, time.value, reduced).waiting.toFloat() },
                style = tripLineLoaderTextStyle(13, color = TripLineLoaderPalette.muted), maxLines = 1,
            )
            BasicText(
                "Searching…", Modifier.graphicsLayer { alpha = TripLineLoaderMotion.row(index, time.value, reduced).searching.toFloat() },
                style = tripLineLoaderTextStyle(13, FontWeight.Medium, TripLineLoaderPalette.coral), maxLines = 1,
            )
            BasicText(
                copy.price, Modifier.graphicsLayer { alpha = TripLineLoaderMotion.row(index, time.value, reduced).done.toFloat() },
                style = tripLineLoaderTextStyle(15, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), maxLines = 1,
            )
        }
    }
}

private object TripLineLoaderMotion {
    val ticks = doubleArrayOf(1.62, 3.92, 6.22)
    fun clamp(x: Double) = min(1.0, max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun into(x: Double) = x * x * x
    fun inOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun back(x: Double) = 1 + 2.70158 * (x - 1).pow(3) + 1.70158 * (x - 1).pow(2)
    fun softBack(x: Double): Double {
        val e = back(x)
        return if (e <= 1) e else 1 + (e - 1) * 0.4
    }
    fun mix(a: Double, b: Double, x: Double) = a + (b - a) * x
    fun wrap(t: Double): Double {
        val r = t % LOOP
        return if (r < 0) r + LOOP else r
    }
    fun tickBucket(t: Double) = ticks.sumOf { floor((t - it) / LOOP).toInt() }
    data class Row(
        val active: Double, val done: Double, val waiting: Double,
        val searching: Double, val pop: Double, val check: Double,
    )
    data class Route(val from: Double, val to: Double)

    fun row(i: Int, t: Double, reduced: Boolean): Row {
        if (reduced) return Row(0.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        val tick = ticks[i]
        val reset = 6.90 + (2 - i) * 0.06
        val fade = inOut(seg(t, reset, reset + 0.2))
        val done = out(seg(t, tick, tick + 0.15)) * (1 - fade)
        val arrival = if (i == 0) 1.0 else out(seg(t, ticks[i - 1], ticks[i - 1] + 0.2))
        val departure = out(seg(t, tick, tick + 0.15))
        val active = arrival * (1 - departure) + if (i == 0) inOut(seg(t, 7.10, 7.30)) else 0.0
        val pop = if (t < tick) 0.6 else 0.6 + 0.4 * back(seg(t, tick, tick + 0.3))
        return Row(active, done, max(0.0, 1 - active - done), active, pop, out(seg(t, tick + 0.08, tick + 0.30)))
    }

    fun route(t: Double, reduced: Boolean): Route {
        if (reduced) return Route(0.0, 1.0)
        if (t >= 7.35) return Route(0.0, 0.0)
        val to = 0.6 * inOut(seg(t, 1.62, 2.12)) + 0.2 * inOut(seg(t, 3.92, 4.42)) + 0.2 * inOut(seg(t, 6.22, 6.72))
        return Route(inOut(seg(t, 6.85, 7.35)), to)
    }
}

/** Each immutable source path is measured once; only this reusable segment changes while drawing. */
private class TripLineLoaderTrimmedPath(val path: Path) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    private val segment = Path()
    fun draw(scope: DrawScope, from: Double, to: Double, color: Color, width: Float, alpha: Float = 1f) {
        if (to <= from || alpha <= 0f) return
        segment.reset()
        measure.getSegment((from * measure.length).toFloat(), (to * measure.length).toFloat(), segment, true)
        scope.drawPath(segment, color, alpha, Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Software blur also works on API 26–27, where a hardware canvas ignores BlurMaskFilter. */
private class TripLineLoaderShadow(private val density: Float) {
    private val bitmap = Bitmap.createBitmap(ceil(162 * density).toInt(), ceil(41 * density).toInt(), Bitmap.Config.ARGB_8888)
    private val canvas = android.graphics.Canvas(bitmap)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = TripLineLoaderPalette.coral.toArgb()
        maskFilter = BlurMaskFilter((4 * density - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    private val imagePaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    fun draw(scope: DrawScope, width: Double, factor: Double, opacity: Double) {
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        paint.alpha = (255 * 0.12 * opacity).toInt()
        canvas.drawOval(
            ((81 - 65 * factor) * density).toFloat(), 16 * density,
            ((81 + 65 * factor) * density).toFloat(), 25 * density, paint,
        )
        destination.set(
            (width / 2 - 81).toFloat(), 193.5f,
            (width / 2 - 81 + bitmap.width / density).toFloat(), 193.5f + bitmap.height / density,
        )
        scope.drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, null, destination, imagePaint) }
    }
}

private object TripLineLoaderDrawing {
    private val M = TripLineLoaderMotion
    private val A = TripLineLoaderArtwork
    private val P = TripLineLoaderPalette
    private val shadowPhases = arrayOf(
        doubleArrayOf(1.40, 1.80, 1.90, 2.26, 0.9),
        doubleArrayOf(3.70, 4.10, 4.20, 4.56, 0.7),
        doubleArrayOf(6.00, 6.40, 6.50, 6.86, 1.0),
    )
    private val pills = arrayOf(1.56 to 64.0, 3.86 to 56.0, 6.16 to 64.0)
    private val lanes = arrayOf(20.0 to 44.0, 76.0 to 30.0, 98.0 to 48.0)
    private val dynamicLine = Path()
    private val lineMeasure = PathMeasure()
    private val lineSegment = Path()

    fun status(scope: DrawScope, check: TripLineLoaderTrimmedPath, state: TripLineLoaderMotion.Row, t: Double, reduced: Boolean) {
        with(scope) {
            drawCircle(P.track, 11f, Offset(12f, 12f), style = Stroke(2f))
            if (!reduced && state.active > 0) {
                drawArc(
                    P.coral, (360 * t - 90).toFloat(), 90f, false, Offset(1f, 1f), Size(22f, 22f),
                    alpha = state.active.toFloat(), style = Stroke(2f, cap = StrokeCap.Round),
                )
            }
            scale(state.pop.toFloat(), state.pop.toFloat(), Offset(12f, 12f)) {
                drawCircle(P.coral, 12f, Offset(12f, 12f), alpha = state.done.toFloat())
                check.draw(this, 0.0, state.check, Color.White, 2f, state.done.toFloat())
            }
        }
    }

    fun shadow(scope: DrawScope, shadow: TripLineLoaderShadow, t: Double, width: Double, reduced: Boolean) {
        var factor = 1.0
        var opacity = 1.0
        if (!reduced) {
            for ((start, end, draw, live, next) in shadowPhases) {
                if (t < start) break
                val gather = M.into(M.seg(t, start, end))
                val appear = M.out(M.seg(t, draw, live))
                factor = M.mix(M.mix(factor, 0.45, gather), next, appear)
                opacity = M.mix(1 - 0.5 * gather, 1.0, appear)
            }
            val planePresence = if (t < 1.8) 1 - M.into(M.seg(t, 1.4, 1.8)) else M.out(M.seg(t, 6.5, 6.86))
            factor *= 1 - 0.04 * sin(PI * t) * planePresence
        }
        shadow.draw(scope, width, factor, opacity)
    }

    fun icons(scope: DrawScope, t: Double, width: Double, reduced: Boolean) {
        if (reduced) {
            icon(scope, TripLineLoaderArtwork.Icon.Plane, 0.0, 10.0, 0.0, width, true)
            return
        }
        if (t < 1.8 || t >= 6.5) {
            val clock = if (t < 1.8) t + 8 else t
            icon(scope, TripLineLoaderArtwork.Icon.Plane, t, clock - 6.5, M.into(M.seg(clock, 9.4, 9.8)), width, false)
        }
        if (t >= 1.9 && t < 4.1) {
            icon(scope, TripLineLoaderArtwork.Icon.Taxi, t, t - 1.9, M.into(M.seg(t, 3.7, 4.1)), width, false)
        }
        if (t >= 4.2 && t < 6.4) {
            icon(scope, TripLineLoaderArtwork.Icon.Hotel, t, t - 4.2, M.into(M.seg(t, 6.0, 6.4)), width, false)
        }
        for ((start, length) in pills) {
            if (t < start || t >= start + 0.72) continue
            val grown = length * M.out(M.seg(t, start, start + 0.24))
            val stretched = grown + 40 * M.out(M.seg(t, start + 0.24, start + 0.42))
            val trim = 0.5 * M.into(M.seg(t, start + 0.42, start + 0.72))
            if (stretched > 0 && trim < 0.5) {
                line(scope, width / 2 - stretched * 0.6, 112.0, stretched * 1.2, trim, 1 - trim, 3f)
            }
        }
    }

    private fun icon(
        scope: DrawScope, kind: TripLineLoaderArtwork.Icon, t: Double,
        elapsed: Double, gather: Double, width: Double, reduced: Boolean,
    ) {
        val plane = kind == TripLineLoaderArtwork.Icon.Plane
        val hotel = kind == TripLineLoaderArtwork.Icon.Hotel
        val build = if (plane && t < 1.8 && !reduced) M.inOut(M.seg(t, 0.8, 1.3)) else 0.0
        val anticipation = if (plane && t < 1.8 && !reduced) M.out(M.seg(t, 1.3, 1.4)) else 0.0
        val bob = if (reduced) 0.0 else if (plane) 2 * sin(PI * t) else if (kind == TripLineLoaderArtwork.Icon.Taxi) 0.8 * abs(sin(4 * PI * t)) else 0.0
        val pitch = if (plane && !reduced) 1.2 * sin(PI * t + PI / 2) else 0.0
        val appear = M.seg(elapsed, 0.0, 0.36)
        val opacity = (1 - M.seg(gather, 0.7, 1.0)).toFloat()
        val anchorY = if (hotel) 120f else 60f
        scope.withTransform({
            translate((width / 2).toFloat(), (112 + if (plane) bob else 0.0).toFloat())
            rotate(pitch.toFloat(), Offset.Zero)
            scale(1.4f, 1.4f, Offset.Zero)
            translate((-88 + 5 * build * (1 - gather)).toFloat(), -60f)
            scale(
                M.mix(1 - 0.03 * anticipation, 0.55, gather).toFloat(),
                M.mix(1 + 0.05 * anticipation, 0.06, gather).toFloat(), Offset(88f, 60f),
            )
            scale(
                if (hotel) 1f else M.mix(1.15, 1.0, M.out(appear)).toFloat(),
                M.mix(0.3, 1.0, if (hotel) M.out(appear) else M.softBack(appear)).toFloat(), Offset(88f, anchorY),
            )
        }) {
            val strokes = when (kind) {
                TripLineLoaderArtwork.Icon.Plane -> A.plane
                TripLineLoaderArtwork.Icon.Taxi -> A.taxi
                TripLineLoaderArtwork.Icon.Hotel -> A.hotel
            }
            for (stroke in strokes) {
                val part = stroke.part
                withTransform({
                    if (kind == TripLineLoaderArtwork.Icon.Taxi && part != TripLineLoaderArtwork.Part.TaxiCloud) {
                        translate(0f, (bob / 1.4).toFloat())
                    }
                    when (part) {
                        TripLineLoaderArtwork.Part.PlaneCloud -> translate(if (reduced) 0f else (-8 * min(M.wrap(t + 1.5), 3.3) / 3.3).toFloat(), 0f)
                        TripLineLoaderArtwork.Part.TaxiCloud -> translate((-10 * M.seg(t, 2.55, 3.70)).toFloat(), 0f)
                        TripLineLoaderArtwork.Part.HotelCloud -> translate((-8 * M.seg(t, 4.90, 6.00)).toFloat(), 0f)
                        is TripLineLoaderArtwork.Part.Spoke -> rotate((4 * PI * t * 180 / PI).toFloat(), Offset(part.x.toFloat(), 86f))
                        TripLineLoaderArtwork.Part.Tree -> rotate((3 * sin(2 * PI * t)).toFloat(), Offset(25f, 116f))
                        else -> Unit
                    }
                }) {
                    when (part) {
                        is TripLineLoaderArtwork.Part.Wheel -> {
                            val amount = M.out(M.seg(elapsed, stroke.delay, stroke.delay + 0.32))
                            drawCircle(P.bg, 11f, Offset(part.x.toFloat(), 86f), alpha = opacity * amount.toFloat())
                        }
                        is TripLineLoaderArtwork.Part.Window -> {
                            val light = M.out(M.seg(t, 4.95 + part.i * 0.1, 5.10 + part.i * 0.1))
                            drawPath(stroke.path, P.coral, alpha = opacity * (0.85 * light * (1 - gather)).toFloat())
                        }
                        else -> Unit
                    }
                    val duration = if (hotel) 0.30 else 0.32
                    val drawn = M.out(M.seg(elapsed, stroke.delay, stroke.delay + duration))
                    val from = 0.5 * 0.85 * gather
                    stroke.trimmed.draw(this, from, min(drawn, 1 - from), P.coral, stroke.width, opacity)
                }
            }
            if (plane) {
                val phase = if (reduced) 0.0 else (t % 0.5) / 0.5
                for ((y, end) in lanes) {
                    dashes(
                        this, y, 0.0, end, 18.0, 8.0, phase, 1 + 0.5 * build, 0.55 + 0.45 * build,
                        M.out(M.seg(elapsed, 6 * 0.035, 6 * 0.035 + 0.32)), gather, opacity,
                    )
                }
            } else if (kind == TripLineLoaderArtwork.Icon.Taxi) {
                dashes(
                    this, 102.0, 4.0, 172.0, 16.0, 9.0, (t % 0.5) / 0.5, 1.0, 1.0,
                    M.out(M.seg(elapsed, 8 * 0.035, 8 * 0.035 + 0.32)), gather, opacity,
                )
            }
        }
    }

    private fun dashes(
        scope: DrawScope, y: Double, start: Double, end: Double, long: Double, gap: Double,
        phase: Double, lengthScale: Double, alpha: Double, drawn: Double, gather: Double, opacity: Float,
    ) {
        val period = (long + 5) * lengthScale + 2 * gap
        val offset = phase * period
        val from = 0.5 * 0.85 * gather
        val to = min(drawn, 1 - from)
        if (to <= from) return
        scope.clipRect(start.toFloat(), (y - 3).toFloat(), end.toFloat(), (y + 3).toFloat()) {
            for (i in -1..6) {
                val x = start + i * period - offset
                line(this, x, y, long * lengthScale, from, to, 2.2f, opacity * alpha.toFloat())
                line(this, x + long * lengthScale + gap, y, 5 * lengthScale, from, to, 2.2f, opacity * alpha.toFloat())
            }
        }
    }

    private fun line(
        scope: DrawScope, x: Double, y: Double, length: Double, from: Double, to: Double,
        width: Float, alpha: Float = 1f,
    ) {
        if (to <= from || length <= 0) return
        // Dynamic straight lines reuse their paths and measure; caps keep their original width.
        dynamicLine.reset()
        dynamicLine.moveTo(x.toFloat(), y.toFloat())
        dynamicLine.lineTo((x + length).toFloat(), y.toFloat())
        lineMeasure.setPath(dynamicLine, false)
        lineSegment.reset()
        lineMeasure.getSegment((from * lineMeasure.length).toFloat(), (to * lineMeasure.length).toFloat(), lineSegment, true)
        scope.drawPath(lineSegment, P.coral, alpha, Stroke(width, cap = StrokeCap.Round))
    }
}

private object TripLineLoaderArtwork {
    enum class Icon { Plane, Taxi, Hotel }
    sealed interface Part {
        data object Plain : Part
        data object PlaneCloud : Part
        data object TaxiCloud : Part
        data object HotelCloud : Part
        data class Wheel(val x: Double) : Part
        data class Spoke(val x: Double) : Part
        data object Tree : Part
        data class Window(val i: Int) : Part
    }
    class Stroke(
        val path: Path, val slot: Int, val extra: Double = 0.0,
        val width: Float = 2.2f, val part: Part = Part.Plain,
    ) {
        val delay get() = slot * 0.035 + extra
        val trimmed = TripLineLoaderTrimmedPath(path)
    }
    data class Row(val title: String, val detail: String, val price: String)
    val rows = listOf(
        Row("Flight", "LIS → KIX · 1 stop · 16h 05m", "€612"),
        Row("Transfer", "Airport taxi to Gion · 48 min", "€38"),
        Row("Stay", "Kamo River Inn · 6 nights", "€486"),
    )
    val check = polyline(listOf(7.0 to 12.5, 10.5 to 16.0, 17.0 to 9.0))

    val plane: List<Stroke> by lazy {
        val outline = rounded(listOf(17.5 to 33.2, 26.6 to 32.6, 44.9 to 44.8, 151.3 to 44.8), 3.0)
        outline.quadraticTo(166f, 45f, 165.9f, 56.9f)
        appendRounded(
            listOf(165.9 to 56.9, 157.4 to 63.6, 114.8 to 63.6, 73.8 to 94.9, 55.5 to 95.8, 78.9 to 63.6, 35.2 to 63.6, 17.5 to 33.2),
            3.0, outline,
        )
        outline.close()
        listOf(
            Stroke(outline, 0),
            Stroke(rounded(listOf(76.8 to 44.8, 54.0 to 20.4, 73.8 to 18.9, 107.2 to 44.8), 3.0), 1),
            Stroke(line(29.7, 52.4, 44.0, 52.4), 2), Stroke(line(50.5, 52.4, 50.6, 52.4), 2),
            Stroke(line(128.5, 52.4, 146.0, 52.4), 3), Stroke(line(152.3, 52.4, 156.9, 52.4), 3),
            Stroke(cloud(152.0, 16.0, 22.0, 12.0), 4, part = Part.PlaneCloud),
            Stroke(cloud(128.0, 104.0, 30.0, 15.0), 5, part = Part.PlaneCloud),
        )
    }

    val taxi: List<Stroke> by lazy {
        buildList {
            add(Stroke(rounded(listOf(17.2 to 87.4, 21.5 to 65.7, 38.7 to 55.8, 137.6 to 55.8, 155.6 to 70.0, 161.2 to 87.4), 3.0, true), 0))
            add(Stroke(rounded(listOf(36.5 to 55.8, 51.6 to 33.4, 96.8 to 31.3, 114.0 to 55.8), 3.0), 1))
            add(Stroke(line(68.8, 32.0, 68.8, 79.9), 2))
            add(Stroke(line(69.5, 83.0, 69.6, 83.0), 2))
            add(Stroke(line(80.8, 67.8, 88.1, 67.8), 3))
            add(Stroke(quad(19.0, 72.0, 24.0, 58.0, 38.7, 56.0), 4))
            add(Stroke(quad(137.6, 56.0, 156.0, 58.0, 160.0, 80.0), 5))
            for ((i, x) in listOf(43.8, 128.0).withIndex()) {
                val wheel = Path().apply {
                    val bounds = Rect((x - 11).toFloat(), 75f, (x + 11).toFloat(), 97f)
                    arcTo(bounds, -90f, 180f, true)
                    arcTo(bounds, 90f, 180f, false)
                }
                add(Stroke(wheel, 6 + i, part = Part.Wheel(x)))
                add(Stroke(line(x, 79.0, x, 83.0), 6 + i, part = Part.Spoke(x)))
            }
            add(Stroke(cloud(128.0, 14.0, 32.0, 16.0), 9, part = Part.TaxiCloud))
        }
    }

    val hotel: List<Stroke> by lazy {
        buildList {
            add(Stroke(line(14.0, 116.0, 162.0, 116.0), 0))
            add(Stroke(polyline(listOf(43.0 to 116.0, 43.0 to 14.0, 132.5 to 14.0, 132.5 to 116.0)), 1))
            add(Stroke(line(62.0, 9.0, 113.0, 9.0), 2, width = 3.1f))
            for (x in listOf(60.0, 87.75, 115.5)) add(Stroke(line(x, 24.0, x + 0.1, 24.0), 3))
            add(Stroke(polyline(listOf(77.0 to 116.0, 77.0 to 92.0, 98.0 to 92.0, 98.0 to 116.0)), 4))
            for ((row, y) in listOf(72.0, 56.0, 40.0).withIndex()) {
                for ((column, x) in listOf(62.0, 87.75, 113.5).withIndex()) {
                    val i = row * 3 + column
                    val window = roundedRect(x - 4.5, y - 3.5, 9.0, 7.0, 1.5)
                    add(Stroke(window, 5, extra = i * 0.03, part = Part.Window(i)))
                }
            }
            // Swift's ellipse starts at the right midpoint and proceeds clockwise in screen space.
            add(Stroke(Path().apply {
                moveTo(33f, 93f)
                cubicTo(33f, 99.07513f, 29.418278f, 104f, 25f, 104f)
                cubicTo(20.581722f, 104f, 17f, 99.07513f, 17f, 93f)
                cubicTo(17f, 86.92487f, 20.581722f, 82f, 25f, 82f)
                cubicTo(29.418278f, 82f, 33f, 86.92487f, 33f, 93f)
                close()
            }, 6, part = Part.Tree))
            add(Stroke(line(25.0, 96.0, 25.0, 116.0), 6, part = Part.Tree))
            add(Stroke(line(25.0, 104.0, 29.0, 100.0), 6, part = Part.Tree))
            add(Stroke(cloud(148.0, 112.0, 22.0, 9.0), 7))
            add(Stroke(cloud(154.0, 4.0, 28.0, 13.0), 8, part = Part.HotelCloud))
        }
    }

    fun line(x1: Double, y1: Double, x2: Double, y2: Double) = polyline(listOf(x1 to y1, x2 to y2))

    /** Swift's continuous corners, including the right-midpoint start used by window trims. */
    fun roundedRect(x: Double, y: Double, width: Double, height: Double, radius: Double): Path {
        val path = Path()
        val right = x + width
        val bottom = y + height
        val a = 1.5286649465560913
        val b = 1.0884900093078613
        val c = 0.8684070110321045
        val d = 0.6314939856529236
        val e = 0.3728240132331848
        val f = 0.16906000673770905
        val g = 0.07491140067577362
        fun corner(cx: Double, cy: Double, quarter: Int) {
            fun point(dx: Double, dy: Double): Offset {
                val delta = when (quarter) {
                    0 -> -dx to -dy
                    1 -> dy to -dx
                    2 -> dx to dy
                    else -> -dy to dx
                }
                return Offset((cx + delta.first * radius).toFloat(), (cy + delta.second * radius).toFloat())
            }
            fun curve(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
                val p1 = point(x1, y1)
                val p2 = point(x2, y2)
                val p3 = point(x3, y3)
                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            val start = point(0.0, a)
            path.lineTo(start.x, start.y)
            curve(0.0, b, 0.0, c, g, d)
            curve(f, e, e, f, d, g)
            curve(c, 0.0, b, 0.0, a, 0.0)
        }
        path.moveTo(right.toFloat(), (y + height / 2).toFloat())
        corner(right, bottom, 0)
        corner(x, bottom, 1)
        corner(x, y, 2)
        corner(right, y, 3)
        path.close()
        return path
    }

    private fun polyline(points: List<Pair<Double, Double>>) = Path().apply {
        for ((i, point) in points.withIndex()) {
            if (i == 0) moveTo(point.first.toFloat(), point.second.toFloat())
            else lineTo(point.first.toFloat(), point.second.toFloat())
        }
    }

    private fun quad(x: Double, y: Double, cx: Double, cy: Double, ex: Double, ey: Double) = Path().apply {
        moveTo(x.toFloat(), y.toFloat())
        quadraticTo(cx.toFloat(), cy.toFloat(), ex.toFloat(), ey.toFloat())
    }

    private fun cloud(x: Double, y: Double, w: Double, h: Double) = Path().apply {
        moveTo((x - w * 0.32).toFloat(), (y + h / 2).toFloat())
        lineTo((x + w * 0.32).toFloat(), (y + h / 2).toFloat())
        cubicTo(
            (x + w * 0.58).toFloat(), (y + h / 2).toFloat(), (x + w * 0.56).toFloat(), (y - h * 0.10).toFloat(),
            (x + w * 0.29).toFloat(), (y - h * 0.12).toFloat(),
        )
        cubicTo(
            (x + w * 0.16).toFloat(), (y - h * 0.70).toFloat(), (x - w * 0.17).toFloat(), (y - h * 0.70).toFloat(),
            (x - w * 0.24).toFloat(), (y - h * 0.14).toFloat(),
        )
        cubicTo(
            (x - w * 0.54).toFloat(), (y - h * 0.28).toFloat(), (x - w * 0.61).toFloat(), (y + h / 2).toFloat(),
            (x - w * 0.32).toFloat(), (y + h / 2).toFloat(),
        )
        close()
    }

    private fun rounded(points: List<Pair<Double, Double>>, radius: Double, closed: Boolean = false): Path {
        val path = Path()
        val first = points.firstOrNull() ?: return path
        if (closed && points.size > 2) {
            val before = inset(first, points.last(), radius)
            val after = inset(first, points[1], radius)
            path.moveTo(before.first.toFloat(), before.second.toFloat())
            path.quadraticTo(first.first.toFloat(), first.second.toFloat(), after.first.toFloat(), after.second.toFloat())
            appendRounded(points + listOf(first, points[1]), radius, path, before)
            path.close()
        } else {
            path.moveTo(first.first.toFloat(), first.second.toFloat())
            appendRounded(points, radius, path)
        }
        return path
    }

    private fun appendRounded(
        points: List<Pair<Double, Double>>, radius: Double, path: Path, closeAt: Pair<Double, Double>? = null,
    ) {
        if (points.size <= 1) return
        val limit = if (closeAt == null) points.size - 1 else points.size - 2
        for (i in 1 until limit) {
            val p = points[i]
            val before = inset(p, points[i - 1], radius)
            val after = inset(p, points[i + 1], radius)
            path.lineTo(before.first.toFloat(), before.second.toFloat())
            path.quadraticTo(p.first.toFloat(), p.second.toFloat(), after.first.toFloat(), after.second.toFloat())
        }
        val end = closeAt ?: points.last()
        path.lineTo(end.first.toFloat(), end.second.toFloat())
    }

    private fun inset(p: Pair<Double, Double>, q: Pair<Double, Double>, radius: Double): Pair<Double, Double> {
        val dx = q.first - p.first
        val dy = q.second - p.second
        val distance = hypot(dx, dy)
        val fraction = min(radius, distance / 2) / max(distance, 0.001)
        return p.first + dx * fraction to p.second + dy * fraction
    }
}
