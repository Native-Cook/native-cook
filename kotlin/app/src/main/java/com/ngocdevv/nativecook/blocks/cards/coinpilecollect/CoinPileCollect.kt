package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.cards.coinpilecollect.CpcTheme as T

/** The loop of the autoplay, in seconds (the RN block's `LOOP`). */
const val CoinPileCollectLoop = CpcLOOP

/**
 * Coin Pile Collect: the "Today's offers" screen of an invented bank. The teal tile is a live pile of heavy satin coins whose sum is
 * the amount; the Collect pill is a hopper you hold (the pile pours in, nearest coins lit cyan), slide the thumb away from the pill to
 * throttle it to single coins, tap to pour it all, put a finger in the pile to push coins around. LOOP = 8 s of autoplay; a touch
 * interrupts it and it resumes 2 s after the last touch.
 */
@Composable
fun CoinPileCollect(
    autoPlay: Boolean = false,
    amountCents: Int = T.AMOUNT_CENTS,
    tilt: Boolean = true,
    haptics: Boolean = true,
    sound: Boolean = false,
    onCollect: ((collected: Double, remaining: Double) -> Unit)? = null,
    onEmpty: ((total: Double) -> Unit)? = null,
    onRefill: (() -> Unit)? = null,
    previewTime: Double? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    BlockSystemBars(darkIcons = false)

    val vibe = remember(context) { CoinPileCollectHaptics.create(context) }
    val audio = remember(context, sound) { if (sound) CoinPileCollectAudio(context) else null }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var spoken by remember { mutableStateOf(T.a11yAmount(T.formatMoney(amountCents.toDouble()))) }
    var empty by remember { mutableStateOf(false) }
    val onCollectState by rememberUpdatedState(onCollect)
    val onEmptyState by rememberUpdatedState(onEmpty)
    val onRefillState by rememberUpdatedState(onRefill)

    val cfg = remember { cpcMakeCfg() }
    val sim = remember(cfg, amountCents, autoPlay, reduced, haptics, sound) {
        CoinPileCollectSim(cfg, amountCents, autoPlay, reduced) { kind, a, b ->
            when (kind) {
                CPC_CUE_PRESS -> if (haptics) vibe.press()
                CPC_CUE_COIN -> if (haptics) vibe.coin()
                CPC_CUE_CLICK -> {
                    if (haptics) vibe.click()
                    audio?.clink()
                }
                CPC_CUE_LATCH -> if (a > 0 && haptics) vibe.latch()
                CPC_CUE_COLLECT -> onCollectState?.invoke(a / 100, b / 100)
                CPC_CUE_EMPTY -> {
                    if (haptics) vibe.empty()
                    audio?.thunk()
                    empty = true
                    spoken = T.A11Y_EMPTY
                    onEmptyState?.invoke(a / 100)
                }
                CPC_CUE_REFILL -> {
                    empty = false
                    spoken = T.a11yAmount(T.formatMoney(amountCents.toDouble()))
                    onRefillState?.invoke()
                }
            }
        }
    }
    val fontScale = density.fontScale
    val renderer = remember(context, fontScale) { CoinPileCollectRenderer(context.assets, fontScale) }
    val tick = remember { mutableIntStateOf(0) }

    // sideways gravity from the device's own gravity sensor (live mode only), with a dead zone
    var gravityG by remember { mutableStateOf(0f) }
    DisposableEffect(tilt, active) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                gravityG = -e.values[0] / 9.81f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (tilt && active && sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(T.rgb(T.PAGE)))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(density) { WindowInsets.statusBars.getTop(density).toDp().value }.toDouble()
        val insetBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp().value }.toDouble()
        val l = remember(w, h, insetTop, insetBottom) { T.makeLayout(w, h, insetTop, insetBottom) }
        val dens = density.density
        val s = l.s

        LaunchedEffect(sim, active, previewTime) {
            if (!active) return@LaunchedEffect
            if (previewTime != null) sim.pin(previewTime)
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    val g = max(-CpcGesture.tiltG, min(CpcGesture.tiltG, gravityG.toDouble()))
                    val gx = if (tilt && abs(g) >= 0.04) g * 2600 else 0.0
                    sim.frame(dt, gx)
                    tick.intValue++
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, l, sim.a, cfg, sim.theta, scale)
            c.restore()
        }

        val hopX = l.ox + T.TEAL_X * s
        val hopY = l.oy + T.TEAL_Y * s
        val hopW = T.TEAL_W * s
        val hopH = T.TEAL_H * s
        Box(
            Modifier
                .offset(hopX.dp, hopY.dp)
                .size(hopW.dp, hopH.dp)
                .pointerInput(sim, s, dens) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        sim.touchBegan(down.position.x / dens / s, down.position.y / dens / s)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                sim.touchMoved(change.position.x / dens / s, change.position.y / dens / s)
                            }
                        } finally {
                            sim.touchEnded()
                        }
                    }
                },
        )

        // accessibility: the title, the live amount and the Collect button
        Box(
            Modifier
                .offset((l.ox + T.TITLE_X * s).dp, (l.oy + T.TITLE_CAPTOP * s - 8 * s).dp)
                .size((240 * s).dp, (120 * s).dp)
                .semantics {
                    heading()
                    contentDescription = T.A11Y_TITLE
                },
        )
        Box(
            Modifier
                .offset(hopX.dp, hopY.dp)
                .size(hopW.dp, (100 * s).dp)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Box(
            Modifier
                .offset((hopX + T.PILL_X * s).dp, (hopY + T.PILL_Y * s).dp)
                .size((T.PILL_W * s).dp, (T.PILL_H * s).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (empty) T.A11Y_EMPTY else T.a11yCollect(T.formatMoney(amountCents.toDouble()))
                    stateDescription = T.HINT_COLLECT
                    if (empty) disabled()
                    onClick { sim.pour(); true }
                },
        )
    }
}
