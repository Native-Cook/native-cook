package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsTheme as T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pearls → jeweller's cut → suspended crystal → magnetic lock → platinum level. */
@Composable
fun FrostHeartRewards(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    BlockSystemBars(darkIcons = false)

    var art by remember { mutableStateOf<FrostHeartRewardsArt?>(null) }
    var prepared by remember { mutableStateOf(false) }
    val driver = remember(autoPlay) { FrostHeartRewardsDriver(autoPlay, reduced) }
    driver.reduced = reduced
    val tick = remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf(false) }
    var spoken by remember { mutableStateOf(Triple(1650, 3, 0)) }
    val audio = remember(context) { FrostHeartRewardsAudio(context) }
    val tilt = remember(context) { FrostHeartRewardsTilt(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }

    fun buzz(rigid: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(if (rigid) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(if (rigid) 14 else 8, if (rigid) 120 else 50))
    }
    val cue = rememberUpdatedState { name: String ->
        when {
            name == "cutComplete" -> { audio.play("cutTing"); buzz(true) }
            name == "snap" -> buzz(true)
            name == "impact" -> buzz(false)
            else -> {
                audio.play(name)
                if (name.startsWith("claim")) buzz(false)
                if (name == "level") buzz(true)
            }
        }
    }
    driver.cue = { cue.value(it) }
    driver.announce = { balance, level, mask -> spoken = Triple(balance, level, mask) }
    driver.session = { session = it }

    LaunchedEffect(context) {
        art = withContext(Dispatchers.IO) { FrostHeartRewardsArt(context) }
    }
    DisposableEffect(audio) { onDispose { audio.close() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(T.BACKGROUND))) {
        val widthPx = constraints.maxWidth.toFloat()
        val scale = widthPx / 402f
        val insetTop = WindowInsets.statusBars.getTop(localDensity).toFloat()
        val offsetY = max(0f, insetTop - 70 * scale)
        val running = !reduced || session
        val currentArt = art

        LaunchedEffect(currentArt, scale) {
            val a = currentArt ?: return@LaunchedEffect
            if (scale <= 0f) return@LaunchedEffect
            withContext(Dispatchers.Default) { a.prepare(scale.toDouble()) }
            prepared = true
            tick.intValue++
        }
        LaunchedEffect(driver, lifecycle, running, prepared) {
            if (!running || !prepared) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                tilt.start()
                try {
                    var previous = 0L
                    while (true) withFrameNanos { nanos ->
                        val dt = min(if (previous == 0L) 1 / 60.0 else (nanos - previous) / 1e9, 0.05)
                        previous = nanos
                        tilt.step(dt)
                        driver.sensorRoll = tilt.roll0
                        driver.sensorPitch = tilt.pitch0
                        driver.step(dt)
                        tick.intValue++
                    }
                } finally {
                    tilt.stop()
                    audio.stop()
                }
            }
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(driver, scale, offsetY) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val x = start.x / scale
                    val y = (start.y - offsetY) / scale
                    val inHero = x >= 44 && x <= 369 && y >= 130 && y <= 389
                    val chip = T.CHIPS.indexOfFirst { c -> x >= c.body.x && x <= c.body.x + c.body.w && y >= c.body.y - 4 && y <= c.body.y + 40 }
                    if (!inHero && chip < 2) return@awaitEachGesture
                    if (inHero) driver.down(x.toDouble(), y.toDouble())
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, start)
                    down.consume()
                    var end = start
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) { cancelled = true; break }
                        end = change.position
                        tracker.addPosition(change.uptimeMillis, end)
                        if (inHero && change.pressed) {
                            driver.move((end.x / scale).toDouble(), ((end.y - offsetY) / scale).toDouble(),
                                ((end.x - start.x) / density).toDouble(), ((end.y - start.y) / density).toDouble())
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (inHero) {
                        val velocity = tracker.calculateVelocity()
                        driver.up((velocity.x / density).toDouble(), cancelled)
                    } else if (!cancelled && hypot(end.x - start.x, end.y - start.y) / scale < 12) {
                        driver.claim(chip - 2)
                        driver.report()
                    }
                    tick.intValue++
                }
            },
        ) {
            tick.intValue
            val a = art ?: return@Canvas
            if (!prepared) return@Canvas
            drawIntoCanvas { a.draw(it.nativeCanvas, size.width, offsetY, driver, reduced) }
        }

        // Accessibility: the hero, the bear, each chip and the spoken balance, placed like the reference's views.
        val dp = { pt: Double -> (pt * scale / localDensity.density).dp }
        fun place(x: Double, y: Double) = Modifier.offset { IntOffset((x * scale).roundToInt(), (y * scale + offsetY).roundToInt()) }
        Box(place(95.0, 225.0).size(dp(208.0), dp(142.0)).semantics {
            role = Role.Image
            contentDescription = "Frosted crystal heart. Rest a finger to breathe through the frost; hold to polish; drag to turn the gem. Tilt to move its fire."
            customActions = listOf(CustomAccessibilityAction("Reveal the next level") { driver.reveal(); tick.intValue++; true })
        })
        Box(place(95.0, 147.0).size(dp(160.0), dp(76.0)).semantics {
            role = Role.Image
            contentDescription = "Pink jelly bear. Tap for a jelly bounce."
            customActions = listOf(CustomAccessibilityAction("Bounce the bear") { driver.bounceBear(); tick.intValue++; true })
        })
        T.CHIPS.forEachIndexed { i, chip ->
            val claimed = i < 2 || (spoken.third and (1 shl (i - 2))) != 0
            Box(place(chip.body.x, chip.body.y - 4).size(dp(chip.body.w), dp(44.0)).semantics {
                if (i < 2) {
                    contentDescription = "${chip.value} UPs, already claimed"
                } else {
                    role = Role.Button
                    contentDescription = "Claim ${chip.value} UPs"
                    if (claimed) disabled()
                    onClick { driver.claim(i - 2); driver.report(); tick.intValue++; true }
                }
            })
        }
        Box(place(17.0, 402.0).size(dp(170.0), dp(44.0)).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "${"%,d".format(spoken.first)} points. Level ${spoken.second}."
        })
        Box(place(160.0, 75.0).size(dp(90.0), dp(22.0)).semantics { heading(); contentDescription = "my lumi" })
        Box(place(16.0, 479.0).size(dp(170.0), dp(16.0)).semantics { heading(); contentDescription = "UPs every day!" })
        Box(place(15.0, 647.0).size(dp(373.0), dp(127.0)).semantics {
            contentDescription = "All. Deals. Pharmacy. Beauty. Discover more at lumi: sportswear essentials. Plus 200 UPs. See what's in our latest collection and choose a new favourite. 2 hours 58 minutes."
        })
    }
}
