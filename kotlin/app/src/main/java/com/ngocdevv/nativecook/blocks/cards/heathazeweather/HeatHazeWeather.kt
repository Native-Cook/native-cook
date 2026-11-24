// Heat Haze Weather — a scorching afternoon you can scrub to evening (HeatHazeWeather.tsx).
//
// Dayline's today screen: the scene behind it is one AGSL runtime shader (HhwScene.kt), a desert road
// drawn in code with heat haze rising from it. The hour slider moves the afternoon: the sun sinks, the
// haze calms, the colours cool, the temperature rolls and the UV chip follows the forecast.
//
// The reusable piece is this composable: give it a place, a date, a forecast (an hourly temperature and
// UV index from 12 PM to 9 PM, and the sunset) and three stats.
//
// autoPlay: LOOP = 10 s. A ghost finger drags the hour from 1 PM to 3:30 PM (the haze builds and the
// temperature counts up), rests on the road's mirage to add a shimmer, drags on to 8 PM (it calms),
// then back to 1 PM; frame 10 s equals frame 0. The slider (with momentum and rubber-banding) or a touch
// on the scene (a local shimmer under the finger) takes over at once; IDLE seconds after the last touch
// the hour eases back to 1 PM and the loop restarts when the haze's flow next wraps. Reduced motion:
// 1 PM, still.
//
// Thread model: one withFrameNanos loop steps HhwEngine on the main thread; the scene and every label
// read it inside draw / graphicsLayer lambdas, so a frame recomposes nothing. API 26–32 draw the scene
// without the shader (HhwDrawnScene), so there is no haze there.
package com.ngocdevv.nativecook.blocks.cards.heathazeweather

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class HeatHazeStat(val label: String, val value: String)

private val STATS = listOf(
    HeatHazeStat("Wind", "NE 9 km/h"),
    HeatHazeStat("Humidity", "11%"),
    HeatHazeStat("Air", "Good · 38"),
)

private val INK = Color(0xFFFBF6EE)
private val MUTE = rgba(251, 246, 238, 0.74f)
/** the temperature's digit height, dp */
private const val DIGIT = 104f
private val DIGITS = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
private val UV_NUMBERS = (0..11).toList()
private val UV_WORDS = listOf("Low", "Moderate", "High", "Very high", "Extreme")
private val CONDITIONS = listOf("Hot and hazy", "Scorching", "Cooling off")
private const val GHOST = 46f
private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
/** after the return, the rest waits for the haze's flow to wrap, so the loop starts on its own phase */
private const val MODE_WAIT = 3


/** Reanimated's default withTiming (ease in-out quad), stepped by the engine */
private class Fade(initial: Float) {
    var value = initial
        private set
    private var from = initial
    private var to = initial
    private var length = 0f
    private var elapsed = 0f

    fun snap(v: Float) {
        value = v
        to = v
        length = 0f
    }

    fun to(target: Float, seconds: Float) {
        from = value
        to = target
        length = seconds
        elapsed = 0f
    }

    fun step(dt: Float) {
        if (length <= 0f) return
        elapsed += dt
        val p = elapsed / length
        if (p >= 1f) {
            snap(to)
            return
        }
        val e = if (p < 0.5f) 2f * p * p else 1f - 2f * (1f - p) * (1f - p)
        value = from + (to - from) * e
    }
}

/** the clock, the script, the fingers and the scene's uniforms; everything on the main thread */
internal class HhwEngine {
    /** bumped once a frame; the draw and graphicsLayer lambdas read it */
    var tick by mutableIntStateOf(0)
        private set
    /** the hour rounded to the half, for the host and for accessibility; changes every half hour */
    var half by mutableDoubleStateOf(H_REST)
        private set

    var auto = false
    var width = 1f
    var height = 1f
    var temps: List<Double> = HeatHazeForecast().temps
    var uvs: List<Double> = HeatHazeForecast().uv
    var sunset = HeatHazeForecast().sunset
    var onTick: () -> Unit = {}
    var onHourChange: ((Double) -> Unit)? = null
    /** the slider track's width, dp */
    var trackW = 1f

    private var clock = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    var hour = H_REST
        private set
    /** the hour under the finger (and its momentum after release) */
    private var touchHour = H_REST
    private var flow = 0.0
    private var lastTouch = -10.0
    private var returnAt = 0.0
    private var returnFrom = H_REST
    private var grabHour = H_REST
    private var sliderDown = false
    private var sceneDown = false
    var touchX = 0f
        private set
    var touchY = 0f
        private set
    private var touchOn = 0f
    private var lastWhole = floor(H_REST)
    private val sliderGhost = Fade(0f)
    private val sceneGhost = Fade(0f)
    val sliderGhostA get() = sliderGhost.value
    var sliderGhostP = 0f
        private set
    val sceneGhostA get() = sceneGhost.value
    var sceneGhostX = 0f
        private set
    var sceneGhostY = 0f
        private set

    // the slider's throw after release: a spring onto the nearest whole hour (HeatHazeWeather.tsx settleHour)
    private var settling = false
    private var settleTo = H_REST
    private var settleVelocity = 0.0

    /** the condition line: 1 = the film's hour-driven cross-fade, 0 = fade to the leading one under the finger */
    var condFilm = 1.0
        private set
    var condPicked = 0.0
        private set
    private var condFrom = 0.0
    private var condTo = 0
    private var condAt = 0.0
    private var condLast = -1

    val uniforms = SceneUniforms()
    private val sg = DoubleArray(2)
    private val gg = DoubleArray(4)
    private val sun = FloatArray(2)

    private fun takeOver() {
        if (mode != MODE_TOUCH) {
            touchHour = hour
            sliderGhost.to(0f, 0.15f)
            sceneGhost.to(0f, 0.15f)
            mode = MODE_TOUCH
        }
        lastTouch = clock
    }

    fun slideBegin(x: Float) {
        takeOver()
        settling = false
        sliderDown = true
        val w = max(trackW, 1f).toDouble()
        val range = H_MAX - H_MIN
        val thumbX = (touchHour - H_MIN) / range * w
        // a touch away from the thumb jumps it there
        if (abs(x - thumbX) > 22) touchHour = H_MIN + clamp01(x / w) * range
        grabHour = touchHour - x / w * range
    }

    fun slideUpdate(x: Float) {
        lastTouch = clock
        val w = max(trackW, 1f).toDouble()
        val raw = grabHour + x / w * (H_MAX - H_MIN)
        // rubber band past either end
        val over = if (raw < H_MIN) raw - H_MIN else if (raw > H_MAX) raw - H_MAX else 0.0
        val band = over / (1 + abs(over) / 0.6)
        touchHour = if (over == 0.0) raw else (if (over < 0) H_MIN else H_MAX) + band * 0.55
    }

    /**
     * The finger lifted with this velocity, dp/s. The throw lands where a 0.994 decay would (v × 0.166 s), on the
     * nearest whole hour: a whole hour's forecast is whole numbers, so the counters never rest half rolled.
     */
    fun slideEnd(velocityX: Float) {
        val w = max(trackW, 1f).toDouble()
        val v = velocityX / w * (H_MAX - H_MIN)
        settleTo = Math.round(min(max(touchHour + v * 0.166, H_MIN), H_MAX)).toDouble()
        settleVelocity = v
        settling = true
    }

    /** a cancelled drag still lets go: what is past either end comes back */
    fun slideCancel() = slideEnd(0f)

    fun slideFinalize() {
        sliderDown = false
        lastTouch = clock
    }

    fun pokeBegin(x: Float, y: Float) {
        takeOver()
        sceneDown = true
        touchX = x
        touchY = y
    }

    fun pokeMove(x: Float, y: Float) {
        lastTouch = clock
        touchX = x
        touchY = y
    }

    fun pokeEnd() {
        sceneDown = false
        lastTouch = clock
    }

    /** spring stiffness 140, damping 22, mass 1, from the release velocity */
    private fun stepSettle(dt: Float) {
        if (!settling) return
        val n = 4
        val h = dt.toDouble() / n
        var x = touchHour
        var v = settleVelocity
        repeat(n) {
            val a = -140.0 * (x - settleTo) - 22.0 * v
            v += a * h
            x += v * h
        }
        if (abs(x - settleTo) < 0.0005 && abs(v) < 0.005) {
            x = settleTo
            v = 0.0
            settling = false
        }
        touchHour = x
        settleVelocity = v
        lastTouch = clock
    }

    /** reduced motion: 1 PM, still */
    fun still() {
        mode = MODE_AUTO
        hour = H_REST
        flow = 0.0
        touchOn = 0f
        sliderGhost.snap(0f)
        sceneGhost.snap(0f)
        publish()
    }

    fun step(dt: Float) {
        clock += dt
        val now = clock
        stepSettle(dt)
        sliderGhost.step(dt)
        sceneGhost.step(dt)
        var h = hour
        var aim = if (sceneDown) 1f else 0f
        var tx = touchX
        var ty = touchY
        val m = mode

        if (m == MODE_AUTO) {
            if (!auto) autoStart = now
            val t = if (auto) (now - autoStart) % LOOP else 0.0
            flow = t % Haze.FLOW
            h = hourAt(t)
            sliderGhostAt(t, sg)
            sliderGhost.snap(sg[0].toFloat())
            sliderGhostP = sg[1].toFloat()
            sceneGhostAt(t, gg)
            tx = (gg[0] * width).toFloat()
            ty = (gg[1] * height).toFloat()
            aim = gg[3].toFloat()
            sceneGhostX = tx
            sceneGhostY = ty
            sceneGhost.snap(gg[2].toFloat())
            // the ghost's shimmer is part of the film: it follows the script exactly
            touchOn = aim
        } else {
            flow = (flow + dt) % Haze.FLOW
            if (m == MODE_TOUCH) {
                h = touchHour
                if (auto && !sliderDown && !sceneDown && now - lastTouch >= IDLE) {
                    returnFrom = h
                    returnAt = now
                    mode = MODE_RETURN
                }
            } else if (m == MODE_RETURN) {
                val u = seg(now - returnAt, 0.0, RETURN)
                h = returnFrom + (H_REST - returnFrom) * smooth(u)
                if (u >= 1) mode = MODE_WAIT
            } else {
                h = H_REST
                // start the loop as the flow wraps, so the haze carries on without a jump
                if (flow < dt * 1.5) {
                    autoStart = now
                    flow = 0.0
                    mode = MODE_AUTO
                }
            }
            touchOn += (aim - touchOn) * (1f - exp(-dt * TOUCH_EASE))
        }
        touchX = tx
        touchY = ty
        hour = h

        // the condition line: the film cross-fades with the hour; under the finger the leading one fades in alone
        val lead = if (mode == MODE_AUTO) -1 else conditionOf(h)
        if (lead < 0) {
            condFilm = 1.0
        } else if (condLast < 0) {
            condFilm = 0.0
            condPicked = lead.toDouble()
            condFrom = condPicked
            condTo = lead
            condAt = now - 1.0
        } else if (lead != condTo) {
            condFrom = condPicked
            condTo = lead
            condAt = now
        }
        if (lead >= 0) {
            val u = seg(now - condAt, 0.0, 0.22)
            condPicked = condFrom + (condTo - condFrom) * easeInOutQuad(u)
        }
        condLast = lead

        // a light tick on each whole hour the finger passes
        val whole = floor(h)
        if (whole != lastWhole) {
            lastWhole = whole
            if (m == MODE_TOUCH && whole >= H_MIN && whole <= H_MAX) onTick()
        }
        publish()
    }

    private fun publish() {
        val h = hour
        val dusk = duskAt(h, sunset)
        val heat = heatAt(sample(temps, h), dusk)
        sunAt(h, sunset, width, height, sun)
        uniforms.sunX = sun[0]
        uniforms.sunY = sun[1]
        uniforms.heat = heat.toFloat()
        uniforms.dusk = dusk.toFloat()
        uniforms.phase = (flow / Haze.FLOW).toFloat()
        uniforms.touchX = touchX
        uniforms.touchY = touchY
        uniforms.touchOn = touchOn
        val rounded = round(h * 2) / 2
        if (rounded != half) {
            half = rounded
            onHourChange?.invoke(rounded)
        }
        tick += 1
    }
}

@Composable
fun HeatHazeWeather(
    modifier: Modifier = Modifier,
    place: String = "Saltbrush Flats",
    date: String = "Thursday, 14 July",
    forecast: HeatHazeForecast = HeatHazeForecast(),
    stats: List<HeatHazeStat> = STATS,
    autoPlay: Boolean = true,
    /** the hour the slider rests on, whole or half (13.0 = 1 PM) */
    onHourChange: ((Double) -> Unit)? = null,
) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    BlockSystemBars(darkIcons = false)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2C69AE))
            .clipToBounds(),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val engine = remember { HhwEngine() }
        val scene = remember(density) {
            val runtime = if (Build.VERSION.SDK_INT >= 33) {
                runCatching { HhwRuntimeScene(density) }
                    .onFailure { Log.w("HeatHazeWeather", "AGSL scene unavailable, drawing the fallback", it) }
                    .getOrNull()
            } else {
                null
            }
            runtime ?: HhwDrawnScene()
        }
        /** where the card sits on the screen, dp, for its frosted copy of the scene */
        var cardAt by remember { mutableStateOf(Offset.Zero) }
        var screenAt by remember { mutableStateOf(Offset.Zero) }
        val frost = remember(density) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val r = sigmaToRadius(6f * density)
                BlurEffect(r, r, TileMode.Clamp)
            } else {
                null
            }
        }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.width = width
            engine.height = height
            engine.temps = forecast.temps
            engine.uvs = forecast.uv
            engine.sunset = forecast.sunset
            engine.onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            engine.onHourChange = onHourChange
        }
        LaunchedEffect(engine, reduced) {
            if (reduced) {
                engine.still()
                return@LaunchedEffect
            }
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
                    last = now
                    engine.step(dt)
                }
            }
        }

        // the scene; a touch on it adds a local shimmer under the finger
        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screenAt = it.positionInRoot() / density }
                .pointerInput(engine, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        engine.pokeBegin(first.position.x / density, first.position.y / density)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                engine.pokeMove(change.position.x / density, change.position.y / density)
                            }
                        } finally {
                            engine.pokeEnd()
                        }
                    }
                },
        ) {
            engine.tick
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                val saved = canvas.save()
                canvas.scale(density, density)
                scene.draw(canvas, width, height, engine.uniforms)
                canvas.restoreToCount(saved)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = insets.calculateTopPadding() + if (compact) 6.dp else 12.dp,
                    bottom = maxOf(insets.calculateBottomPadding(), 12.dp) + if (compact) 4.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                HhwLabel(place, 22f, FontWeight.Bold, INK, spacing = -0.3f, shadow = Shadow(rgba(20, 30, 60, 0.25f), Offset.Zero, 8f * density))
                HhwLabel(date, 15f, FontWeight.Medium, MUTE, Modifier.padding(top = 2.dp))
                Row(
                    Modifier
                        .padding(top = if (compact) 0.dp else 4.dp)
                        .height(DIGIT.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Digit(engine, tens = true, density)
                    Digit(engine, tens = false, density)
                    HhwLabel("°", 64f, FontWeight.Medium, INK, Modifier.padding(start = 2.dp), line = 84f)
                }
                Box(Modifier.offset(y = (-2).dp).height(26.dp)) {
                    CONDITIONS.forEachIndexed { i, label ->
                        HhwLabel(
                            label, 20f, FontWeight.SemiBold, INK,
                            Modifier.graphicsLayer {
                                engine.tick
                                val f = engine.condFilm
                                val k = (f * conditionAt(engine.hour, i) + (1 - f) * clamp01(1 - abs(engine.condPicked - i))).toFloat()
                                alpha = k
                                translationY = 6f * (1f - k) * density
                            },
                            spacing = -0.2f,
                        )
                    }
                }
                UvChip(engine, density)
            }

            // the card: a frosted copy of the scene behind it, a warm shade, the slider and the stats
            Box(
                Modifier
                    .fillMaxWidth()
                    .cssBoxShadow(rgba(20, 12, 8, 0.18f), 2.dp, 4.dp, 26.dp)
                    .cssBoxShadow(rgba(20, 12, 8, 0.22f), 18.dp, 40.dp, 26.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border((1f / density).dp, rgba(251, 246, 238, 0.3f), RoundedCornerShape(26.dp))
                    .onGloballyPositioned { cardAt = it.boundsInRoot().topLeft / density }
                    // the card is solid to touch: only its slider takes a finger
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            first.consume()
                        }
                    },
            ) {
                Canvas(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = frost
                            clip = true
                        },
                ) {
                    engine.tick
                    val dx = cardAt.x - screenAt.x
                    val dy = cardAt.y - screenAt.y
                    drawIntoCanvas {
                        val canvas = it.nativeCanvas
                        val saved = canvas.save()
                        canvas.scale(density, density)
                        canvas.translate(-dx, -dy)
                        scene.draw(canvas, width, height, engine.uniforms)
                        canvas.restoreToCount(saved)
                    }
                }
                // the dark blur's own tint, then the card's warm shade
                Box(Modifier.matchParentSize().background(rgba(12, 10, 12, if (frost == null) 0.3f else 0.14f)))
                Box(Modifier.matchParentSize().background(rgba(28, 20, 18, 0.28f)))
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HhwLabel("Hourly", 17f, FontWeight.Bold, INK, Modifier.alignByBaseline())
                        HhwLabel("Sunset ${formatHour(forecast.sunset)}", 13f, FontWeight.Medium, MUTE, Modifier.alignByBaseline())
                    }
                    HhwHourSlider(engine, density, enabled = !reduced)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .drawBehind {
                                drawRect(rgba(251, 246, 238, 0.22f), size = size.copy(height = 1f))
                            }
                            .padding(top = 12.dp),
                    ) {
                        for (s in stats) {
                            Column(Modifier.weight(1f)) {
                                HhwLabel(s.label, 12f, FontWeight.Medium, MUTE)
                                HhwLabel(s.value, 15f, FontWeight.SemiBold, INK, Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // the ghost finger on the road
        Box(
            Modifier
                .graphicsLayer {
                    engine.tick
                    translationX = (engine.sceneGhostX - GHOST / 2f) * density
                    translationY = (engine.sceneGhostY - GHOST / 2f) * density
                    alpha = (engine.sceneGhostA * 0.9f).coerceIn(0f, 1f)
                }
                .size(GHOST.dp)
                .background(rgba(251, 246, 238, 0.16f), CircleShape)
                .border(1.5.dp, rgba(251, 246, 238, 0.62f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(rgba(251, 246, 238, 0.5f), CircleShape))
        }
    }
}

/** UV, its number rolling, a dot, and its band's word crossfading */
@Composable
private fun UvChip(engine: HhwEngine, density: Float) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier
            .padding(top = 8.dp)
            .height(30.dp)
            .background(rgba(16, 24, 48, 0.24f), shape)
            .border((1f / density).dp, rgba(251, 246, 238, 0.32f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhwLabel("UV", 13f, FontWeight.Bold, MUTE)
        Box(
            Modifier
                .padding(start = 5.dp)
                .width(22.dp)
                .height(30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            for (n in UV_NUMBERS) {
                HhwLabel(
                    "$n", 15f, FontWeight.Bold, INK,
                    Modifier.graphicsLayer {
                        engine.tick
                        val at = rolled(sample(engine.uvs, engine.hour) + 0.5, 0.6)
                        val d = (n - at).toFloat()
                        alpha = (1f - abs(d)).coerceIn(0f, 1f)
                        translationY = 6f * d * density
                    },
                )
            }
        }
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(3.dp)
                .background(MUTE, CircleShape),
        )
        Box(
            Modifier
                .width(74.dp)
                .height(30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            UV_WORDS.forEachIndexed { i, word ->
                HhwLabel(
                    word, 14f, FontWeight.SemiBold, INK,
                    Modifier.graphicsLayer {
                        engine.tick
                        val d = (i - uvBandAt(sample(engine.uvs, engine.hour))).toFloat()
                        alpha = (1f - abs(d)).coerceIn(0f, 1f)
                        translationY = 6f * d * density
                    },
                )
            }
        }
    }
}

/** one digit of the temperature: a strip of 0…9, 0 that slides under a 104 dp window */
@Composable
private fun Digit(engine: HhwEngine, tens: Boolean, density: Float) {
    Box(Modifier.height(DIGIT.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    engine.tick
                    val temp = rolled(sample(engine.temps, engine.hour))
                    translationY = -DIGIT * density * stripOf(temp, tens).toFloat()
                },
        ) {
            for (d in DIGITS) {
                Box(Modifier.height(DIGIT.dp), contentAlignment = Alignment.Center) {
                    HhwLabel(
                        "$d", 96f, FontWeight.Bold, INK,
                        spacing = -3f, line = DIGIT,
                        shadow = Shadow(rgba(20, 30, 60, 0.22f), Offset.Zero, 14f * density),
                    )
                }
            }
        }
    }
}

/** text in dp, so the system's font scale does not move the page */
@Composable
internal fun HhwLabel(
    text: String,
    size: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    line: Float = 0f,
    shadow: Shadow? = null,
) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.dp.toSp() },
            fontWeight = weight,
            letterSpacing = with(density) { spacing.dp.toSp() },
            lineHeight = if (line > 0f) with(density) { line.dp.toSp() } else TextStyle.Default.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFeatureSettings = "tnum",
            shadow = shadow,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
