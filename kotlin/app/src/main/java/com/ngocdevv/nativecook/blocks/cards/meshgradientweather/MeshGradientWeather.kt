package com.ngocdevv.nativecook.blocks.cards.meshgradientweather

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.cards.meshgradientweather.MeshGradientWeatherMotion.Sym
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ngocdevv.nativecook.blocks.cards.meshgradientweather.MeshGradientWeatherMotion as M
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One loop of the film, in seconds (the React Native `LOOP`). */
const val MESH_GRADIENT_WEATHER_LOOP = M.LOOP

private const val ITEM = 62f
private const val RETURN_S = 1.1
private val Ink = Color(0xFF10142C)
private val ShadowInk = Ink.copy(alpha = 0.22f)
private val RangeColors = listOf(Color(0xFF8FD3E8), Color(0xFFB5E2B0), Color(0xFFF3D67F), Color(0xFFF2A862))

private class Day(val day: String, val symbol: Sym, val lo: Double, val hi: Double)

private val Days = listOf(
    Day("Today", Sym.CloudSunRain, 11.0, 20.0),
    Day("Fri", Sym.Sun, 12.0, 22.0),
    Day("Sat", Sym.Sun, 13.0, 23.0),
    Day("Sun", Sym.Cloud, 12.0, 19.0),
    Day("Mon", Sym.Rain, 10.0, 17.0),
)
private const val WEEK_LO = 10.0
private const val WEEK_HI = 23.0
private const val DAY_COL = 52f
private const val NUM_COL = 34f

private enum class Mode { Auto, Manual, Ret }

private sealed interface HourAnim {
    data class Fixed(val h: Double) : HourAnim
    data class Timing(val from: Double, val to: Double, val at: Double, val duration: Double, val settle: Boolean) : HourAnim
}

/**
 * The hour the row shows, as a function of the clock. The film (one 16 s loop) only moves the row; a touch takes it
 * over, and 2 s after the last touch the row scrolls back to Now in 1.1 s and the film starts again.
 */
private class WeatherClock(film: Boolean) {
    var mode by mutableStateOf(if (film) Mode.Auto else Mode.Manual)
    var loopStart by mutableDoubleStateOf(0.0)
    var anim by mutableStateOf<HourAnim>(HourAnim.Fixed(0.0))
    var retFrom by mutableDoubleStateOf(0.0)
    var retAt by mutableDoubleStateOf(Double.POSITIVE_INFINITY)
    var dragging by mutableStateOf(false)

    /** Seconds since the block appeared, advanced once per frame. */
    val now: MutableDoubleState = mutableDoubleStateOf(0.0)
    var originNanos = -1L

    fun wallNow(): Double = if (originNanos < 0) 0.0 else (System.nanoTime() - originNanos) / 1e9

    private fun loopT(now: Double): Double {
        val start = if (mode == Mode.Ret) retAt + RETURN_S else loopStart
        return ((now - start) % M.LOOP + M.LOOP) % M.LOOP
    }

    fun hour(now: Double): Double {
        if (mode == Mode.Ret) {
            val p = min(1.0, (now - retAt) / RETURN_S)
            if (p < 1) return retFrom * (1 - M.easeInOut(p))
        }
        if (mode != Mode.Manual) return M.filmHour(loopT(now))
        return when (val a = anim) {
            is HourAnim.Fixed -> a.h
            is HourAnim.Timing -> {
                val x = M.clamp01((now - a.at) / a.duration)
                val f = if (a.settle) 1 - (1 - x).pow(3) else M.easeInOut(x)
                a.from + (a.to - a.from) * f
            }
        }
    }

    fun gate(now: Double): Double {
        if (mode == Mode.Manual) return 1.0
        if (mode == Mode.Ret && now - retAt < RETURN_S) return 0.0
        return M.filmRainGate(loopT(now))
    }

    /** Stops the film (or a running return) where it is. */
    fun pause() {
        if (mode != Mode.Manual) {
            anim = HourAnim.Fixed(hour(wallNow()))
            retAt = Double.POSITIVE_INFINITY
            mode = Mode.Manual
        }
    }
}

/**
 * Soft Weather: a weather screen whose whole background is a living mesh gradient. The hour the hourly row is
 * scrolled to is the one number the sky, the temperature, the condition and the tiles are drawn from, so scrubbing
 * the row morphs the mesh through the day: dawn, a sunny noon, an afternoon of rain on the glass, dusk, night.
 *
 * Drag the row (it snaps per hour, with a tick) or tap an hour to take over; 2 s after the last touch the row
 * scrolls back to Now and the film starts again, while the drift never resets. With animations removed the sky
 * holds still at the hour shown and the film does not run; the row still scrubs it.
 */
@Composable
fun MeshGradientWeather(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val filmState = rememberUpdatedState(film)
    val reducedState = rememberUpdatedState(reduced)
    val clock = remember { WeatherClock(film) }
    val sky = remember { MeshGradientWeatherSky() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val idle = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                if (clock.originNanos < 0) clock.originNanos = t
                clock.now.doubleValue = (t - clock.originNanos) / 1e9
            }
        }
    }
    LaunchedEffect(film) {
        if (!film && clock.mode != Mode.Manual) clock.pause()
        if (film && clock.mode == Mode.Manual && !clock.dragging) {
            clock.loopStart = clock.wallNow()
            clock.mode = Mode.Auto
        }
    }

    val hour: () -> Double = { clock.hour(clock.now.doubleValue) }

    // A selection tick per hour while the row is in the finger's hands.
    LaunchedEffect(Unit) {
        var last = -1
        snapshotFlow { if (clock.mode == Mode.Manual) hour().roundToInt() else -1 }.collect { v ->
            if (last >= 0 && v >= 0 && v != last) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            last = v
        }
    }

    /** Restarts the 2 s wait after the last touch. */
    fun touched(settling: Double = 0.0) {
        idle[0]?.cancel()
        idle[0] = scope.launch {
            delay(((2 + settling) * 1000).toLong())
            if (!filmState.value || clock.mode != Mode.Manual || clock.dragging) return@launch
            val now = clock.wallNow()
            clock.retFrom = clock.hour(now)
            clock.retAt = now
            clock.mode = Mode.Ret
            delay((RETURN_S * 1000).toLong())
            if (clock.mode == Mode.Ret) {
                clock.loopStart = clock.retAt + RETURN_S
                clock.retAt = Double.POSITIVE_INFINITY
                clock.mode = Mode.Auto
            }
        }
    }

    val touch = remember {
        object : RowTouch {
            override fun dragStart(): Double {
                val cur = clock.hour(clock.wallNow())
                clock.pause()
                clock.dragging = true
                return cur
            }

            override fun drag(h: Double) {
                clock.anim = HourAnim.Fixed(h.coerceIn(0.0, M.lastHour))
                touched()
            }

            override fun dragEnd(cur: Double, velocityItems: Double) {
                clock.dragging = false
                val c = cur.coerceIn(0.0, M.lastHour)
                // a fast-decelerating scroll view that snaps per hour
                val target = (c - velocityItems * 0.1).roundToInt().toDouble().coerceIn(0.0, M.lastHour)
                val d = 0.25 + 0.06 * min(abs(target - c), 4.0)
                clock.anim = HourAnim.Timing(c, target, clock.wallNow(), d, settle = true)
                touched(d)
            }

            override fun tap(i: Int) {
                val now = clock.wallNow()
                val cur = clock.hour(now)
                clock.pause()
                val d = 0.3 + 0.03 * min(abs(i - cur), 6.0)
                clock.anim = HourAnim.Timing(cur, i.toDouble(), now, d, settle = false)
                touched(d)
            }
        }
    }

    // Where the sky sits in the window, so each glass card can draw the part of it behind itself.
    val skyOrigin = remember { FloatArray(2) }
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF7C86C8))
            .onGloballyPositioned { val p = it.positionInWindow(); skyOrigin[0] = p.x; skyOrigin[1] = p.y },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740
        val contentW = min(width, 480f)
        val frame: () -> Unit = {
            val now = clock.now.doubleValue
            val h = clock.hour(now)
            val amb = if (reducedState.value) 0.0 else now % M.LOOP
            sky.update(now, h, amb, M.rain(h) * clock.gate(now), width, height, density)
        }
        val glass = GlassContext(sky, skyOrigin, frame, density)

        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { frame(); sky.drawSky(this) },
        )

        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Page(
                hour = hour,
                cardW = (contentW - 32f),
                compact = compact,
                glass = glass,
                touch = touch,
                modifier = Modifier.padding(top = top + (if (compact) 12.dp else 24.dp), bottom = bottom + 16.dp),
            )
        }
    }
}

private interface RowTouch {
    fun dragStart(): Double
    fun drag(h: Double)
    fun dragEnd(cur: Double, velocityItems: Double)
    fun tap(i: Int)
}

private class GlassContext(
    val sky: MeshGradientWeatherSky,
    val skyOrigin: FloatArray,
    val frame: () -> Unit,
    val density: Float,
)

// ------------------------------------------------------------------------------------------------ page

private val BaseText = TextStyle(
    color = Color.White,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
private fun shadowed(style: TextStyle): TextStyle {
    val d = LocalDensity.current
    return style.copy(shadow = Shadow(ShadowInk, Offset(0f, with(d) { 1.dp.toPx() }), with(d) { 7.dp.toPx() }))
}

@Composable
private fun Page(hour: () -> Double, cardW: Float, compact: Boolean, glass: GlassContext, touch: RowTouch, modifier: Modifier) {
    val tempSize = if (compact) 84f else 100f
    Column(modifier.width(cardW.dp)) {
        // header
        Column(Modifier.padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MonoSymbol(Mono.Location, 13.dp, Color.White.copy(alpha = 0.9f))
                Text(
                    "My Location",
                    style = shadowed(BaseText.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.88f))),
                )
            }
            Text(
                "Porto",
                modifier = Modifier.padding(top = 2.dp),
                style = shadowed(BaseText.copy(fontSize = (if (compact) 28 else 34).sp, lineHeight = (if (compact) 34 else 41).sp, fontWeight = FontWeight.Bold)),
            )
            TempText(hour, tempSize)
            Box(Modifier.height((if (compact) 24 else 26).dp)) {
                val condStyle = shadowed(BaseText.copy(fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold))
                M.conditions.forEachIndexed { i, c ->
                    Text(
                        c,
                        style = condStyle,
                        modifier = Modifier.graphicsLayer {
                            val w = M.condWeight(i, hour()).toFloat()
                            alpha = w
                            translationY = (1 - w) * 6.dp.toPx()
                        },
                    )
                }
            }
            Text(
                "H:20°  L:11°",
                modifier = Modifier.padding(top = 2.dp),
                style = shadowed(BaseText.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.88f), fontFeatureSettings = "tnum")),
            )
        }

        // the hourly card
        GlassCard(glass, id = 0, radius = 26.dp, modifier = Modifier.padding(top = if (compact) 18.dp else 22.dp)) {
            Column {
                CardHead(Mono.CloudRain, "Rain from 2 PM, clearing by 6 PM.")
                Rule()
                HourRow(hour, cardW, touch)
            }
        }

        Row(
            Modifier.padding(top = 12.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tile(glass, 1, Mono.Umbrella, "Chance of rain", { "${M.sample(M.rainPct, it).roundToInt()}%" }, hour, "Bring a jacket after lunch.", Modifier.weight(1f))
            Tile(glass, 2, Mono.Wind, "Wind", { "${M.sample(M.wind, it).roundToInt()} km/h" }, hour, "From the northwest.", Modifier.weight(1f))
        }

        Forecast(glass, hour, cardW, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TempText(hour: () -> Double, size: Float) {
    val temp by remember { derivedStateOf { M.sample(M.temps, hour()).roundToInt() } }
    Text(
        "$temp°",
        modifier = Modifier
            .height((size * 1.08f).dp)
            .offset(x = (-size * 0.05f).dp)
            .wrapContentWidth(Alignment.Start, unbounded = true),
        style = shadowed(BaseText.copy(fontSize = size.sp, lineHeight = (size * 1.08f).sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum")),
        softWrap = false,
    )
}

/** A Liquid Glass card: the sky behind it through the glass tone curve, and a hairline rim. */
@Composable
private fun GlassCard(glass: GlassContext, id: Int, radius: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val origin = remember { FloatArray(2) }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .onGloballyPositioned { val p = it.positionInWindow(); origin[0] = p.x; origin[1] = p.y }
            .border(Dp.Hairline, Color.White.copy(alpha = 0.28f), shape),
    ) {
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    glass.frame()
                    val r = radius.toPx()
                    val path = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                    }
                    clipPath(path) {
                        val d = glass.density
                        glass.sky.drawGlass(
                            this, id,
                            (origin[0] - glass.skyOrigin[0]) / d, (origin[1] - glass.skyOrigin[1]) / d,
                            size.width / d, size.height / d,
                        )
                    }
                },
        )
        content()
    }
}

@Composable
private fun CardHead(icon: Mono, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)
            .height(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MonoSymbol(icon, 17.dp, Color.White.copy(alpha = 0.75f))
        Text(text, style = BaseText.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.86f)), maxLines = 1)
    }
}

@Composable
private fun Rule() {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(with(LocalDensity.current) { 1f.toDp() })
            .background(Color.White.copy(alpha = 0.3f)),
    )
}

@Composable
private fun HourRow(hour: () -> Double, cardW: Float, touch: RowTouch) {
    val pad = (cardW - ITEM) / 2
    val itemPx = with(LocalDensity.current) { ITEM.dp.toPx() }
    Box(
        Modifier
            .width(cardW.dp)
            .height(108.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                var from = 0.0
                var dx = 0f
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { from = touch.dragStart(); dx = 0f; tracker.resetTracking() },
                    onDragEnd = { touch.dragEnd(from - dx / itemPx, tracker.calculateVelocity().x / itemPx.toDouble()) },
                    onDragCancel = { touch.dragEnd(from - dx / itemPx, 0.0) },
                ) { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    dx += amount
                    touch.drag(from - dx / itemPx)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val rowX = p.x - (pad.dp.toPx() - hour().toFloat() * itemPx)
                    val i = (rowX / itemPx).toInt()
                    if (rowX >= 0 && i in M.hours.indices) touch.tap(i)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = (pad + 3).dp)
                .width((ITEM - 6).dp)
                .height(92.dp)
                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                .border(Dp.Hairline, Color.White.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
        )
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = (pad - hour().toFloat() * ITEM).dp.toPx() },
        ) {
            val label = BaseText.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
            val temp = BaseText.copy(fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            M.hours.forEach { hr ->
                Column(
                    Modifier
                        .width(ITEM.dp)
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = "${hr.label}, ${hr.temp.toInt()} degrees" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(hr.label, style = label, softWrap = false)
                    WeatherSymbol(hr.symbol, 28.dp, 33.dp, Modifier.padding(vertical = 8.dp))
                    Text("${hr.temp.toInt()}°", style = temp, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    glass: GlassContext,
    id: Int,
    icon: Mono,
    title: String,
    value: (Double) -> String,
    hour: () -> Double,
    note: String,
    modifier: Modifier,
) {
    GlassCard(glass, id, 22.dp, modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoSymbol(icon, 14.dp, Color.White.copy(alpha = 0.72f))
                Text(title, style = BaseText.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f)))
            }
            val v by remember { derivedStateOf { value(hour()) } }
            Text(
                v,
                modifier = Modifier.padding(top = 8.dp),
                style = BaseText.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                softWrap = false,
            )
            Text(
                note,
                modifier = Modifier.padding(top = 4.dp),
                style = BaseText.copy(
                    fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f), lineBreak = LineBreak.Simple,
                ),
            )
        }
    }
}

@Composable
private fun Forecast(glass: GlassContext, hour: () -> Double, width: Float, modifier: Modifier) {
    val track = width - 32 - DAY_COL - 30 - NUM_COL * 2 - 8 * 4
    fun x(v: Double) = ((v - WEEK_LO) / (WEEK_HI - WEEK_LO) * track).toFloat()
    GlassCard(glass, 3, 22.dp, modifier) {
        Column(Modifier.padding(bottom = 4.dp)) {
            CardHead(Mono.Calendar, "5-Day Forecast")
            Rule()
            val day = BaseText.copy(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            Days.forEachIndexed { i, d ->
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(33.dp)
                        .drawBehind {
                            if (i > 0) drawRect(Color.White.copy(alpha = 0.18f), Offset.Zero, Size(size.width, 1f))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(d.day, style = day, modifier = Modifier.width(DAY_COL.dp), softWrap = false)
                    Box(Modifier.size(30.dp, 22.dp), contentAlignment = Alignment.Center) {
                        WeatherSymbol(d.symbol, 22.dp, 32.dp, sunK = 0.68f)
                    }
                    Text(
                        "${d.lo.toInt()}°", modifier = Modifier.width(NUM_COL.dp), softWrap = false,
                        style = day.copy(color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.End),
                    )
                    Box(Modifier.size(track.dp, 10.dp), contentAlignment = Alignment.CenterStart) {
                        Spacer(
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val th = 5.dp.toPx()
                                    val y = (size.height - th) / 2
                                    drawRoundRect(ShadowInk, Offset(0f, y), Size(size.width, th), CornerRadius(th / 2))
                                    val a = x(d.lo).dp.toPx()
                                    val b = x(d.hi).dp.toPx()
                                    drawRoundRect(
                                        Brush.horizontalGradient(RangeColors, 0f, size.width),
                                        Offset(a, y), Size(b - a, th), CornerRadius(th / 2),
                                    )
                                },
                        )
                        if (i == 0) {
                            Box(
                                Modifier
                                    .graphicsLayer { translationX = (x(M.sample(M.temps, hour())) - 5).dp.toPx() }
                                    .size(10.dp)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, Ink.copy(alpha = 0.35f), CircleShape),
                            )
                        }
                    }
                    Text("${d.hi.toInt()}°", style = day, modifier = Modifier.width(NUM_COL.dp), softWrap = false)
                }
            }
        }
    }
}
