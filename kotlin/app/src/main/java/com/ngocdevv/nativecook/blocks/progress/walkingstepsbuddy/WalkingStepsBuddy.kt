package com.ngocdevv.nativecook.blocks.progress.walkingstepsbuddy

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.provider.Settings
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun hex(c: Int, a: Float = 1f) = Color(0xFF000000.toInt() or c).copy(alpha = a)
private fun mixColor(a: Int, b: Int, f: Double) = Color(WalkingStepsBuddyArt.mix(a, b, f))

/**
 * The step-rewards screen of an invented neighbourhood-walk app. Bao, a round steamed-bun buddy with
 * a tote and a baguette, walks without end while the street scrolls by in layers: clouds drift and
 * dissolve, hills and trees slide slowly, the bench, the walk sign, the planter and the grass pass
 * fast. Every footfall adds a chunk to today's count (it rolls like a drum) and the goal bar
 * advances; passing 1,000 steps pops confetti, a check badge and a "+10 coins" bubble. The sun sets,
 * then rises again as the count rolls back to 0: a fresh day, and the loop starts over (LOOP = 9 s).
 *
 * Drag sideways to walk Bao yourself (back and forth, the world and the legs move together); on
 * release the walk carries on at its own pace. Tap Bao for a hop. Reduced motion: the rest frame.
 */
@Composable
fun WalkingStepsBuddy(autoPlay: Boolean = false) {
    val m = WalkingStepsBuddyMotion
    val art = WalkingStepsBuddyArt
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsOff(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { WalkingStepsBuddyTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                clock.doubleValue = base + (now - startNanos) / 1e9
            }
            if (!autoPlay && !touch.touching && clock.doubleValue > touch.restsAfter) {
                awake.value = false
                break
            }
        }
    }
    val loopT: () -> Double = { if (reduced) 0.0 else touch.loopTime(clock.doubleValue, autoPlay) }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(hex(WalkingStepsBuddyArt.PAGE))) {
        val k = maxWidth.value / 393f
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val g = m.ground(top)
        val s = g + 38
        val u: (Double) -> Dp = { (it * k).dp }
        val ts: (Double) -> TextUnit = { with(density) { (it * k).dp.toSp() } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Bao walking down a sunny street" }
                .pointerInput(reduced, k, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        if (down.position.y / scale > g + 40) return@awaitEachGesture
                        if (reduced) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            return@awaitEachGesture
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        touch.begin(clock.doubleValue, autoPlay)
                        awake.value = true
                        var last = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            val d = change.position - down.position
                            touch.move(d.x / scale.toDouble(), d.y / scale.toDouble())
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        val vx = tracker.calculateVelocity().x / scale
                        if (touch.end(clock.doubleValue, vx.toDouble(), last.x / scale.toDouble(), last.y / scale.toDouble(), g, autoPlay)) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                },
        ) {
            val c = clock.doubleValue
            val t = loopT()
            val d = if (reduced) 0.0 else touch.distance(c, autoPlay)
            val hop = if (reduced) 0.0 to 1.0 else m.hop(c - touch.hopClock)
            val pose = m.pose(d)
            pose.lift = hop.first
            pose.sy *= hop.second
            pose.sx *= 2 - hop.second
            val happy = !reduced && (m.happy(t) || c - touch.hopClock < 0.75)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(size.width / 393f, size.width / 393f)
            art.drawScene(canvas, d, t, g, pose, happy)
            art.drawBar(canvas, t, m.steps(m.countPhase(t)), s + 10)
            canvas.restore()
        }

        // Navigation bar.
        Box(Modifier.offset(0.dp, u(top)).fillMaxWidth().height(u(44.0))) {
            Text(
                "Neighborhood walk", color = hex(WalkingStepsBuddyArt.INK), fontSize = ts(17.0), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
            )
            Icon(Modifier.offset(u(8.0 + 16), u(13.0)).size(u(12.0), u(18.0)).semantics { contentDescription = "Back" }, k, art::drawChevron)
            Icon(Modifier.offset(u(393.0 - 10 - 76 + 8), u(11.0)).size(u(22.0)).semantics { contentDescription = "Share" }, k, art::drawShare)
            Icon(Modifier.offset(u(393.0 - 10 - 38 + 8), u(11.0)).size(u(22.0)).semantics { contentDescription = "Settings" }, k, art::drawGear)
        }

        Text(
            "Steps today", color = hex(WalkingStepsBuddyArt.LABEL), fontSize = ts(14.0), fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(u(20.0), u(top + 68 - 10)).clearAndSetSemantics {}, maxLines = 1,
        )
        Icon(Modifier.offset(u(20.0), u(top + 106 - 14)).size(u(36.0), u(28.0)).clearAndSetSemantics {}, k, art::drawShoe)
        val phase: () -> Double = { m.countPhase(loopT()) }
        val shown by remember { derivedStateOf { m.countStrings[min(phase().roundToInt(), m.counts.size - 1)] } }
        CountDrum(
            phase, 52.0, k,
            TextStyle(color = hex(WalkingStepsBuddyArt.INK), fontSize = ts(44.0), fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            Modifier.offset(u(62.0), u(top + 106 - 26)).semantics { contentDescription = "Steps today, $shown" },
        )

        // Milestones under the bar.
        for (i in 0 until 4) {
            val step by remember { derivedStateOf { (m.pillMix(i, loopT()) * 24).roundToInt() } }
            val mix = step / 24.0
            Text(
                m.milestoneLabels[i], color = mixColor(WalkingStepsBuddyArt.MUTED, WalkingStepsBuddyArt.INK, mix),
                fontSize = ts(13.0), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                modifier = Modifier.offset(u(m.tickX[i] - 40), u(s + 44 - 9)).width(u(80.0)).clearAndSetSemantics {},
            )
            Row(
                Modifier
                    .offset(u(m.tickX[i] - 36), u(s + 76 - 15))
                    .size(u(72.0), u(30.0))
                    .graphicsLayer {
                        val bump = (1 + 0.08 * sin(PI * m.pillMix(i, loopT()))).toFloat()
                        scaleX = bump; scaleY = bump
                    }
                    .background(mixColor(0xF1F3F5, 0xFFEFD2, mix), RoundedCornerShape(50))
                    .semantics {
                        contentDescription = "${m.milestoneLabels[i]}, ${m.rewards[i]} coins, ${if (mix > 0.5) "reached" else "not reached"}"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u(5.0), Alignment.CenterHorizontally),
            ) {
                Icon(Modifier.size(u(14.0)), k) { c -> art.drawCoin(c, 7.0, 7.0, 7.0, mix) }
                Text(
                    m.rewards[i], color = mixColor(0xB4BCC6, 0xE07A12, mix), fontSize = ts(13.0), fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false,
                )
            }
        }

        // Reward card.
        Column(
            Modifier
                .offset(u(16.0), u(s + 116))
                .width(u(361.0))
                .shadow(u(10.0), RoundedCornerShape(u(22.0)), ambientColor = hex(0x3A4A5C, 0.5f), spotColor = hex(0x3A4A5C, 0.35f))
                .background(Color.White, RoundedCornerShape(u(22.0)))
                .padding(vertical = u(8.0)),
        ) {
            RewardRow(k, u, ts, art::drawCalendar, "Check in every day", "1 coin a day", true)
            RewardRow(k, u, ts, art::drawGift, "Open the lucky box", "Up to 200 coins a day", false)
            RewardRow(k, u, ts, art::drawPlane, "Walk with a friend", "Up to 1,000 coins each", false)
        }

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(size.width / 393f, size.width / 393f)
            art.drawCelebration(canvas, loopT(), s + 16)
            canvas.restore()
        }
    }
}

/** A small icon drawn in points on a native canvas. */
@Composable
private fun Icon(modifier: Modifier, k: Float, draw: (NativeCanvas) -> Unit) {
    Canvas(modifier) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(k * density, k * density)
        draw(canvas)
        canvas.restore()
    }
}

@Composable
private fun RewardRow(
    k: Float,
    u: (Double) -> Dp,
    ts: (Double) -> TextUnit,
    icon: (NativeCanvas) -> Unit,
    title: String,
    detail: String,
    claim: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(u(70.0)).padding(horizontal = u(18.0)).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Modifier.size(u(44.0)).clearAndSetSemantics {}, k, icon)
        Spacer(Modifier.width(u(14.0)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(u(3.0))) {
            Text(title, color = hex(WalkingStepsBuddyArt.INK), fontSize = ts(16.0), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detail, color = hex(WalkingStepsBuddyArt.MUTED), fontSize = ts(13.0), maxLines = 1)
        }
        if (claim) {
            Row(
                Modifier
                    .height(u(32.0))
                    .background(hex(0xF3F4F6), RoundedCornerShape(50))
                    .padding(horizontal = u(12.0))
                    .semantics { role = Role.Button; contentDescription = "Claim" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u(5.0)),
            ) {
                Icon(Modifier.size(u(16.0)), k) { c -> WalkingStepsBuddyArt.drawCoin(c, 8.0, 8.0, 8.0, 1.0) }
                Text("Claim", color = hex(WalkingStepsBuddyArt.INK), fontSize = ts(13.0), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** The count on a drum: rows sit `row` apart and roll up with the phase, fading away from the middle. */
@Composable
private fun CountDrum(phase: () -> Double, row: Double, k: Float, style: TextStyle, modifier: Modifier) {
    val items = WalkingStepsBuddyMotion.countStrings
    val base by remember { derivedStateOf { floor(phase()).toInt() } }
    val density = LocalDensity.current
    val rowPx = with(density) { (row * k).dp.toPx() }
    Box(
        modifier
            .size((220 * k).dp, (row * k).dp)
            .graphicsLayer {
                val frac = phase() - floor(phase())
                val bump = (1 + 0.05 * sin(PI * frac)).toFloat()
                scaleX = bump; scaleY = bump
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clipToBounds(),
    ) {
        for (o in 0..1) {
            val j = base + o
            if (j < 0 || j >= items.size) continue
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val d = j - phase()
                        translationY = (d * rowPx).toFloat()
                        alpha = max(0.0, 1 - 1.6 * abs(d)).toFloat()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(items[j], style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun animationsOff(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
