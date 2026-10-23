package com.ngocdevv.nativecook.blocks.lists.wallettransactions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val WALLET_TRANSACTIONS_LOOP_MS = 9_000L

@Composable
fun WalletTransactions(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val scriptedAutoPlay = autoPlay && !reducedMotion
    val motion = remember { WalletTransactionsMotion(System.nanoTime() / 1_000_000.0) }
    val frameMs = remember { mutableDoubleStateOf(System.nanoTime() / 1_000_000.0) }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var awake by remember { mutableStateOf(scriptedAutoPlay) }
    var frameGeneration by remember { mutableIntStateOf(0) }
    var resumeToken by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val controller = view.context.walletTransactionsActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    fun interrupt() {
        val now = frameMs.doubleValue
        motion.interrupt(now, scriptedAutoPlay, reducedMotion)
        selectedFilter = motion.selectedFilter
        awake = true
        frameGeneration += 1
        resumeToken += 1
    }

    fun pick(index: Int) {
        motion.pick(index, frameMs.doubleValue, reducedMotion)
        selectedFilter = index
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LaunchedEffect(autoPlay, reducedMotion) {
        motion.reset(scriptedAutoPlay, System.nanoTime() / 1_000_000.0)
        selectedFilter = 0
        awake = scriptedAutoPlay
        frameGeneration += 1
    }

    LaunchedEffect(scriptedAutoPlay, awake, frameGeneration) {
        while (isActive && (scriptedAutoPlay || awake)) {
            withFrameNanos { nanos ->
                val now = nanos / 1_000_000.0
                frameMs.doubleValue = now
                if (scriptedAutoPlay && !motion.manual) {
                    val current = motion.values(now, autoPlay = true, reduced = reducedMotion)
                    val filter = current.fades[0].roundToInt().coerceIn(0, 2)
                    if (filter != selectedFilter) selectedFilter = filter
                }
            }
            if (!scriptedAutoPlay && !motion.isActive(frameMs.doubleValue)) awake = false
        }
    }

    LaunchedEffect(resumeToken, autoPlay, reducedMotion) {
        if (resumeToken == 0 || motion.heroActive) return@LaunchedEffect
        if (!scriptedAutoPlay) {
            delay(700)
            awake = motion.heroActive
            return@LaunchedEffect
        }
        delay(WalletTransactionsMotion.RESUME_AFTER_MS)
        if (!scriptedAutoPlay || motion.heroActive) return@LaunchedEffect
        motion.settleHome(frameMs.doubleValue, reducedMotion)
        selectedFilter = 0
        awake = true
        frameGeneration += 1
        delay(500)
        if (!scriptedAutoPlay) return@LaunchedEffect
        motion.restartAutoPlay(System.nanoTime() / 1_000_000.0)
        awake = true
        frameGeneration += 1
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WalletTransactionsColors.Band).pointerInput(scriptedAutoPlay, reducedMotion) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); interrupt(); while (true) { if (awaitPointerEvent().changes.none { it.pressed }) break } } },
    ) {
        val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val offsetY = if (safeTop > 0f) safeTop - 62f else -40f

        WalletTransactionsScreen(
            offsetY = offsetY,
            frameMs = frameMs,
            motion = motion,
            autoPlay = scriptedAutoPlay,
            reducedMotion = reducedMotion,
            selectedFilter = selectedFilter,
            onInterrupt = ::interrupt,
            onPick = ::pick,
            onHeroBegan = { point ->
                interrupt()
                motion.beginHero(point, frameMs.doubleValue, reducedMotion)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                awake = true
                frameGeneration += 1
            },
            onHeroChanged = motion::changeHero,
            onHeroEnded = {
                motion.endHero(frameMs.doubleValue, reducedMotion)
                awake = true.also { resumeToken += 1 }
                frameGeneration += 1
            },
        )
    }
}

private data class WalletTransactionsValues(
    val chip: Double = 0.0,
    val fades: DoubleArray = DoubleArray(4),
    val lift: Double = 0.0,
    val press: Double = 0.0,
    val tiltX: Double = 0.35,
    val tiltY: Double = -0.2,
    val fans: DoubleArray = DoubleArray(4),
)

private enum class WalletTransactionsCurve { Linear, EaseOut, Spring }

private data class WalletTransactionsTransition(
    val from: Double = 0.0,
    val to: Double = 0.0,
    val startMs: Double = 0.0,
    val durationMs: Double = 0.0,
    val delayMs: Double = 0.0,
    val curve: WalletTransactionsCurve = WalletTransactionsCurve.Linear,
    val omega: Double = 0.0,
    val zeta: Double = 1.0,
) {
    fun value(nowMs: Double): Double {
        val elapsed = (nowMs - startMs - delayMs) / 1_000.0
        if (elapsed <= 0.0) return from
        val progress = when (curve) {
            WalletTransactionsCurve.Linear -> (elapsed / max(durationMs / 1_000.0, 0.0001)).coerceIn(0.0, 1.0)
            WalletTransactionsCurve.EaseOut -> {
                val x = (elapsed / max(durationMs / 1_000.0, 0.0001)).coerceIn(0.0, 1.0)
                1.0 - (1.0 - x).pow(3)
            }
            WalletTransactionsCurve.Spring -> WalletTransactionsMotion.springStep(elapsed, omega, zeta)
        }
        return from + (to - from) * progress
    }
}

private class WalletTransactionsMotion(startMs: Double) {
    companion object {
        const val RESUME_AFTER_MS = 3_500L
        private const val ROW_FADE_MS = 180.0
        private const val ROW_STAGGER_MS = 30.0

        fun springStep(elapsed: Double, omega: Double, zeta: Double): Double {
            if (elapsed <= 0.0) return 0.0
            if (zeta >= 1.0) return 1.0 - (1.0 + omega * elapsed) * exp(-omega * elapsed)
            val damped = omega * sqrt(1.0 - zeta * zeta)
            return 1.0 - exp(-zeta * omega * elapsed) *
                (cos(damped * elapsed) + (zeta * omega / damped) * sin(damped * elapsed))
        }

        private fun clamp(value: Double): Double = value.coerceIn(0.0, 1.0)

        private fun easeOut(value: Double): Double {
            val x = clamp(value)
            return 1.0 - (1.0 - x).pow(3)
        }

        private fun scripted(time: Double, reduced: Boolean): WalletTransactionsValues {
            val fades = DoubleArray(4) { row ->
                val stagger = if (reduced) 0.0 else row * 0.030
                clamp((time - 3.900 - stagger) / 0.180) +
                    clamp((time - 5.500 - stagger) / 0.180) -
                    2.0 * clamp((time - 7.100 - stagger) / 0.180)
            }
            val fans = DoubleArray(4) { index ->
                if (reduced) 0.0 else {
                    springStep(time - 1.450 - index * 0.045, 20.0, 0.68) -
                        springStep(time - 2.900 - (3 - index) * 0.025, 18.0, 1.0)
                }
            }
            return WalletTransactionsValues(
                chip = springStep(time - 3.900, 18.5, 0.86) +
                    springStep(time - 5.500, 18.5, 0.86) -
                    2.0 * springStep(time - 7.100, 18.5, 0.86),
                fades = fades,
                lift = springStep(time - 1.320, 17.0, 0.72) -
                    springStep(time - 3.000, 15.7, 1.0),
                press = if (reduced) 0.0 else {
                    easeOut((time - 1.200) / 0.120) * (1.0 - easeOut((time - 1.320) / 0.160))
                },
                fans = fans,
            )
        }
    }

    var selectedFilter = 0
    var heroActive = false
    var manual = false
        private set

    private var loopStartMs = startMs
    private var snapshot = WalletTransactionsValues()
    private var chip = WalletTransactionsTransition()
    private var fades = Array(4) { WalletTransactionsTransition() }
    private var lift = WalletTransactionsTransition()
    private var press = WalletTransactionsTransition()
    private var fans = Array(4) { WalletTransactionsTransition() }
    private var tiltX = 0.35
    private var tiltY = -0.2
    private var activeUntilMs = 0.0

    fun reset(autoPlay: Boolean, nowMs: Double) {
        loopStartMs = nowMs
        manual = !autoPlay
        selectedFilter = 0
        heroActive = false
        snapshot = WalletTransactionsValues()
        activeUntilMs = 0.0
        setTransitions(snapshot, nowMs)
    }

    fun restartAutoPlay(nowMs: Double) {
        loopStartMs = nowMs
        manual = false
        selectedFilter = 0
        heroActive = false
    }

    fun interrupt(nowMs: Double, autoPlay: Boolean, reduced: Boolean) {
        if (manual) return
        snapshot = values(nowMs, autoPlay, reduced)
        manual = true
        selectedFilter = snapshot.fades[0].roundToInt().coerceIn(0, 2)
        setTransitions(snapshot, nowMs)
        tiltX = snapshot.tiltX
        tiltY = snapshot.tiltY
    }

    fun pick(index: Int, nowMs: Double, reduced: Boolean) {
        val current = values(nowMs, autoPlay = false, reduced = reduced)
        selectedFilter = index
        chip = WalletTransactionsTransition(
            current.chip, index.toDouble(), nowMs, if (reduced) ROW_FADE_MS else 340.0,
            curve = if (reduced) WalletTransactionsCurve.Linear else WalletTransactionsCurve.Spring,
            omega = 18.5, zeta = 0.86,
        )
        fades = Array(4) { row ->
            WalletTransactionsTransition(
                current.fades[row], index.toDouble(), nowMs, ROW_FADE_MS,
                if (reduced) 0.0 else row * ROW_STAGGER_MS, WalletTransactionsCurve.Linear,
            )
        }
        activeUntilMs = nowMs + 700.0
    }

    fun beginHero(point: Offset, nowMs: Double, reduced: Boolean) {
        val current = values(nowMs, autoPlay = false, reduced = reduced)
        heroActive = true
        tiltX = ((point.x / 127f) * 2f - 1f).coerceIn(-1f, 1f).toDouble()
        tiltY = ((point.y / 133f) * 2f - 1f).coerceIn(-1f, 1f).toDouble()
        press = WalletTransactionsTransition(current.press, 1.0, nowMs, 120.0, curve = WalletTransactionsCurve.EaseOut)
        lift = WalletTransactionsTransition(current.lift, 1.0, nowMs, 370.0, 120.0, WalletTransactionsCurve.Spring, 17.0, 0.72)
        fans = Array(4) { index ->
            WalletTransactionsTransition(current.fans[index], 1.0, nowMs, 320.0, 250.0 + index * 45.0, WalletTransactionsCurve.Spring, 20.0, 0.68)
        }
        activeUntilMs = Double.POSITIVE_INFINITY
    }

    fun changeHero(point: Offset) {
        val nx = (point.x / 127f) * 2f - 1f
        val ny = (point.y / 133f) * 2f - 1f
        tiltX = (nx / (1f + abs(nx) * 0.5f) * 1.5f).toDouble()
        tiltY = (ny / (1f + abs(ny) * 0.5f) * 1.5f).toDouble()
    }

    fun endHero(nowMs: Double, reduced: Boolean) {
        val current = values(nowMs, autoPlay = false, reduced = reduced)
        heroActive = false
        press = WalletTransactionsTransition(current.press, 0.0, nowMs, 160.0, curve = WalletTransactionsCurve.EaseOut)
        lift = WalletTransactionsTransition(current.lift, 0.0, nowMs, 400.0, curve = WalletTransactionsCurve.Spring, omega = 15.7, zeta = 1.0)
        fans = Array(4) { index ->
            WalletTransactionsTransition(current.fans[index], 0.0, nowMs, 350.0, (3 - index) * 25.0, WalletTransactionsCurve.Spring, 18.0, 1.0)
        }
        activeUntilMs = nowMs + 600.0
    }

    fun settleHome(nowMs: Double, reduced: Boolean) {
        pick(0, nowMs, reduced)
        endHero(nowMs, reduced)
    }

    fun values(nowMs: Double, autoPlay: Boolean, reduced: Boolean): WalletTransactionsValues {
        if (autoPlay && !manual) {
            val elapsed = ((nowMs - loopStartMs) % WALLET_TRANSACTIONS_LOOP_MS) / 1_000.0
            return scripted(max(0.0, elapsed), reduced)
        }
        return WalletTransactionsValues(
            chip = chip.value(nowMs),
            fades = DoubleArray(4) { fades[it].value(nowMs) },
            lift = lift.value(nowMs),
            press = if (reduced) 0.0 else press.value(nowMs),
            tiltX = tiltX,
            tiltY = tiltY,
            fans = DoubleArray(4) { if (reduced) 0.0 else fans[it].value(nowMs) },
        )
    }

    fun isActive(nowMs: Double): Boolean {
        return heroActive || nowMs < activeUntilMs
    }

    private fun setTransitions(values: WalletTransactionsValues, nowMs: Double) {
        chip = WalletTransactionsTransition(values.chip, values.chip, nowMs)
        lift = WalletTransactionsTransition(values.lift, values.lift, nowMs)
        press = WalletTransactionsTransition(values.press, values.press, nowMs)
        fades = Array(4) { WalletTransactionsTransition(values.fades[it], values.fades[it], nowMs) }
        fans = Array(4) { WalletTransactionsTransition(values.fans[it], values.fans[it], nowMs) }
    }
}

@Composable
private fun WalletTransactionsScreen(
    offsetY: Float,
    frameMs: MutableDoubleState,
    motion: WalletTransactionsMotion,
    autoPlay: Boolean,
    reducedMotion: Boolean,
    selectedFilter: Int,
    onInterrupt: () -> Unit,
    onPick: (Int) -> Unit,
    onHeroBegan: (Offset) -> Unit,
    onHeroChanged: (Offset) -> Unit,
    onHeroEnded: () -> Unit,
) {
    Box(Modifier.offset(y = offsetY.dp).size(402.dp, 874.dp)) {
        Box(
            Modifier.offset(y = (-200).dp).size(402.dp, 611.3.dp).background(
                Color.White,
                RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            ),
        )
        WalletTransactionsHeader(onInterrupt)
        WalletTransactionsBalance(onInterrupt)
        WalletTransactionsTiles(frameMs, motion, autoPlay, reducedMotion, onInterrupt, onHeroBegan, onHeroChanged, onHeroEnded)
        WalletTransactionsActions(onInterrupt)
        Box(
            Modifier.offset(y = 489.3.dp).size(402.dp, 1_200.dp).background(
                Color.White,
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            ),
        )
        Box(Modifier.offset(182.dp, 499.8.dp).size(38.dp, 6.dp).background(WalletTransactionsColors.Grabber, CircleShape))
        WalletTransactionsText("Transactions", 15.7f, 520.7f, 19f, FontWeight.SemiBold, WalletTransactionsColors.Ink, -0.2f, 0.9f)
        WalletTransactionsPress(
            label = "View all transactions",
            modifier = Modifier.offset(300.7.dp, 514.1.dp).size(77.dp, 28.5.dp),
            onTouch = onInterrupt,
        ) {
            Box(Modifier.fillMaxSize().background(WalletTransactionsColors.ChipBrush, RoundedCornerShape(14.25.dp))) {
                Row(Modifier.offset(x = 8.6.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text("View all", color = WalletTransactionsColors.Meta, fontSize = 14.5.sp, modifier = Modifier.graphicsLayer { scaleX = 0.93f })
                    WalletTransactionsGlyph(WalletTransactionsGlyphKind.Chevron, WalletTransactionsColors.Meta, Modifier.size(7.dp, 12.dp).offset(x = 4.5.dp))
                }
            }
        }
        WalletTransactionsChips(frameMs, motion, autoPlay, reducedMotion, selectedFilter, onInterrupt, onPick)
        WalletTransactionsRows(frameMs, motion, autoPlay, reducedMotion, selectedFilter)
        Box(
            Modifier.offset(y = 785.dp).size(402.dp, 89.dp).background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.55f),
                        1f to Color.White.copy(alpha = 0.75f),
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun WalletTransactionsHeader(onInterrupt: () -> Unit) {
    WalletTransactionsGlyph(WalletTransactionsGlyphKind.Brand, WalletTransactionsColors.Ink, Modifier.offset(16.dp, 72.7.dp).size(27.dp, 29.3.dp))
    WalletTransactionsText("VEN", 47f, 79f, 23f, FontWeight.ExtraBold, WalletTransactionsColors.Ink, 0.4f, italic = true)
    WalletTransactionsHeaderButton(284.9f, "Insights", WalletTransactionsGlyphKind.Chart, onInterrupt)
    WalletTransactionsHeaderButton(342.3f, "Cards", WalletTransactionsGlyphKind.Card, onInterrupt)
}

@Composable
private fun WalletTransactionsHeaderButton(
    x: Float,
    label: String,
    glyph: WalletTransactionsGlyphKind,
    onInterrupt: () -> Unit,
) {
    WalletTransactionsPress(label, Modifier.offset(x.dp, 64.8.dp).size(44.dp), onTouch = onInterrupt) {
        Box(Modifier.fillMaxSize().background(WalletTransactionsColors.ButtonBrush, CircleShape), contentAlignment = Alignment.Center) {
            WalletTransactionsGlyph(
                glyph,
                WalletTransactionsColors.Icon,
                if (glyph == WalletTransactionsGlyphKind.Chart) Modifier.size(22.dp) else Modifier.size(23.dp, 19.dp),
            )
        }
    }
}

@Composable
private fun WalletTransactionsBalance(onInterrupt: () -> Unit) {
    WalletTransactionsText("Balance", 14.7f, 148f, 17f, FontWeight.Medium, WalletTransactionsColors.Label)
    WalletTransactionsPress(
        label = "Account, Personal",
        modifier = Modifier.offset(301.dp, 138.7.dp).size(85.7.dp, 29.dp),
        onTouch = onInterrupt,
    ) {
        Row(
            Modifier.fillMaxSize()
                .background(WalletTransactionsColors.Pill, RoundedCornerShape(14.2.dp))
                .border(0.5.dp, WalletTransactionsColors.PillRim, RoundedCornerShape(14.2.dp)),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Personal", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            WalletTransactionsGlyph(WalletTransactionsGlyphKind.Chevron, Color.White, Modifier.size(7.dp, 12.dp), stroke = 2.1f)
        }
    }
    WalletTransactionsText("\$", 15f, 198f, 22f, FontWeight.Medium, WalletTransactionsColors.Grey)
    WalletTransactionsAmount("25 431", ".30", 35.4f, 192.7f, 44f)
}

@Composable
private fun WalletTransactionsTiles(
    frameMs: MutableDoubleState,
    motion: WalletTransactionsMotion,
    autoPlay: Boolean,
    reducedMotion: Boolean,
    onInterrupt: () -> Unit,
    onHeroBegan: (Offset) -> Unit,
    onHeroChanged: (Offset) -> Unit,
    onHeroEnded: () -> Unit,
) {
    val density = LocalDensity.current.density
    val latestOnHeroBegan = rememberUpdatedState(onHeroBegan)
    val latestOnHeroChanged = rememberUpdatedState(onHeroChanged)
    val latestOnHeroEnded = rememberUpdatedState(onHeroEnded)
    Box(
        Modifier.offset(15.dp, 264.dp).size(127.dp, 133.dp).graphicsLayer {
            val lift = motion.values(frameMs.doubleValue, autoPlay, reducedMotion).lift.coerceAtLeast(0.0).toFloat()
            alpha = 0.2f * lift.coerceAtMost(1f)
            translationY = (if (reducedMotion) 2f else 2f + 10f * lift) * density
            scaleX = 0.9f + 0.1f * lift.coerceAtMost(1f)
            scaleY = scaleX
        }.shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color.White, RoundedCornerShape(22.dp)),
    )
    Box(
        Modifier.offset(15.dp, 264.dp).size(127.dp, 133.dp).graphicsLayer {
            val values = motion.values(frameMs.doubleValue, autoPlay, reducedMotion)
            val lift = if (reducedMotion) 0f else values.lift.toFloat()
            translationX = values.tiltX.toFloat() * 3f * lift * density
            translationY = (-5f + values.tiltY.toFloat() * 1.5f) * lift * density
            scaleX = 1f - 0.03f * values.press.toFloat() + 0.04f * lift
            scaleY = scaleX
            rotationZ = values.tiltX.toFloat() * 1.6f * lift
        }.clip(RoundedCornerShape(22.dp)).background(WalletTransactionsColors.TileBrush, RoundedCornerShape(22.dp)),
    ) {
        WalletTransactionsTileIcon(WalletTransactionsGlyphKind.Command, WalletTransactionsColors.Invest)
        WalletTransactionsText("Invest", 49f, 21.3f, 15f, FontWeight.Normal, WalletTransactionsColors.Label)
        repeat(4) { index ->
            WalletTransactionsHoldingMark(
                index,
                Modifier.offset((13.2f + index * 21.9f).dp, (67f - 23.7f / 2f).dp).size(23.7.dp).graphicsLayer {
                    val fan = motion.values(frameMs.doubleValue, autoPlay, reducedMotion).fans[index].toFloat()
                    translationX = index * (27f - 21.9f) * fan * density
                    translationY = -3f * fan * density
                },
            )
        }
        WalletTransactionsAmount("70 850", "", 25.7f, 96.7f, 26f, 13.7f, 94.8f, 12f)
    }
    Box(
        Modifier.offset(15.dp, 264.dp).size(127.dp, 133.dp).semantics {
            role = Role.Button
            contentDescription = "Invest, 70 850 dollars"
        }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var began = false
                var ended = false
                try {
                    latestOnHeroBegan.value(down.position)
                    began = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        latestOnHeroChanged.value(change.position)
                        if (event.changes.none { it.pressed }) {
                            ended = true
                            latestOnHeroEnded.value()
                            break
                        }
                    }
                } finally {
                    // Pointer cancellation must release hero ownership so the resume task can run.
                    if (began && !ended) {
                        ended = true
                        latestOnHeroEnded.value()
                    }
                }
            }
        },
    )
    WalletTransactionsStandardTile(152f, 143.5f, "Shared, 1 200.50 dollars", onInterrupt) {
        WalletTransactionsTileIcon(WalletTransactionsGlyphKind.People, WalletTransactionsColors.Shared)
        WalletTransactionsText("Shared", 49f, 21.3f, 15f, FontWeight.Normal, WalletTransactionsColors.Label)
        WalletTransactionsAmount("1 200", ".50", 25.7f, 96.7f, 26f, 13.7f, 94.8f, 12f)
    }
    WalletTransactionsStandardTile(305f, 73f, "Loans", onInterrupt) {
        WalletTransactionsText("Loans", 14.3f, 16.7f, 15f, FontWeight.Normal, WalletTransactionsColors.Label)
        WalletTransactionsGlyph(WalletTransactionsGlyphKind.Arrow, WalletTransactionsColors.Ink, Modifier.offset(16.dp, 98.3.dp).size(18.dp, 15.dp))
    }
}

@Composable
private fun WalletTransactionsStandardTile(
    x: Float,
    width: Float,
    label: String,
    onInterrupt: () -> Unit,
    content: @Composable () -> Unit,
) {
    WalletTransactionsPress(label, Modifier.offset(x.dp, 264.dp).size(width.dp, 133.dp), onTouch = onInterrupt) {
        Box(Modifier.fillMaxSize().background(WalletTransactionsColors.TileBrush, RoundedCornerShape(22.dp))) { content() }
    }
}

@Composable
private fun WalletTransactionsTileIcon(kind: WalletTransactionsGlyphKind, color: Color) {
    Box(
        Modifier.offset(13.3.dp, 13.5.dp).size(27.4.dp).clip(RoundedCornerShape(7.dp)).background(WalletTransactionsColors.IconSquareBrush),
        contentAlignment = Alignment.Center,
    ) {
        WalletTransactionsGlyph(kind, color, if (kind == WalletTransactionsGlyphKind.People) Modifier.size(17.dp, 16.dp) else Modifier.size(16.dp))
    }
}

@Composable
private fun WalletTransactionsActions(onInterrupt: () -> Unit) {
    val items = listOf(
        WalletTransactionsAction(15.3f, 105f, "Send", WalletTransactionsGlyphKind.Send),
        WalletTransactionsAction(129f, 107f, "Receive", WalletTransactionsGlyphKind.Down),
        WalletTransactionsAction(247.7f, 80f, "Add", WalletTransactionsGlyphKind.Plus),
        WalletTransactionsAction(340f, 45f, "More", null),
    )
    items.forEach { item ->
        WalletTransactionsPress(item.label, Modifier.offset(item.x.dp, 427.8.dp).size(item.width.dp, 44.dp), onTouch = onInterrupt) {
            Box(Modifier.fillMaxSize().background(WalletTransactionsColors.ActionRimBrush, RoundedCornerShape(22.dp))) {
                Box(Modifier.offset(1.dp, 1.dp).size((item.width - 2f).dp, 42.dp).background(WalletTransactionsColors.Band, RoundedCornerShape(21.dp)))
                if (item.glyph != null) {
                    Box(Modifier.offset(7.dp, 7.dp).size(30.dp).background(WalletTransactionsColors.ActionCircleBrush, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(28.dp).background(WalletTransactionsColors.ActionCircleInnerBrush, CircleShape))
                        WalletTransactionsGlyph(item.glyph, Color.White, Modifier.size(18.dp))
                    }
                    WalletTransactionsText(item.label, 45f, 15.4f, 15f, FontWeight.Medium, Color(0xFFF2F2F2))
                } else {
                    WalletTransactionsGlyph(WalletTransactionsGlyphKind.More, WalletTransactionsColors.Dots, Modifier.align(Alignment.Center).size(20.dp, 4.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletTransactionsChips(
    frameMs: MutableDoubleState,
    motion: WalletTransactionsMotion,
    autoPlay: Boolean,
    reducedMotion: Boolean,
    selectedFilter: Int,
    onInterrupt: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val frames = listOf(15.7f to 43.6f, 67.3f to 91.3f, 167.3f to 75.7f)
    val labels = listOf("All", "Recurring", "Friends")
    Canvas(Modifier.offset(y = 551.dp).size(250.dp, 36.dp)) {
        frames.forEach { (x, width) ->
            drawRoundRect(WalletTransactionsColors.ChipBrush, Offset(x.dp.toPx(), 0f), Size(width.dp.toPx(), 36.dp.toPx()), CornerRadius(18.dp.toPx()))
        }
        val position = motion.values(frameMs.doubleValue, autoPlay, reducedMotion).chip.coerceIn(0.0, 2.0)
        val lower = floor(position).toInt().coerceAtMost(1)
        val fraction = (position - lower).toFloat()
        val x = frames[lower].first + (frames[lower + 1].first - frames[lower].first) * fraction
        val width = frames[lower].second + (frames[lower + 1].second - frames[lower].second) * fraction
        drawRoundRect(WalletTransactionsColors.ChipActiveBrush, Offset(x.dp.toPx(), 0f), Size(width.dp.toPx(), 36.dp.toPx()), CornerRadius(18.dp.toPx()))
    }
    frames.forEachIndexed { index, (x, width) ->
        WalletTransactionsPress(
            label = labels[index],
            modifier = Modifier.offset(x.dp, 551.dp).size(width.dp, 36.dp),
            selected = selectedFilter == index,
            onTouch = onInterrupt,
            onClick = { onPick(index) },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(labels[index], color = WalletTransactionsColors.ChipText, fontSize = 14.5.sp)
                Text(labels[index], color = WalletTransactionsColors.ChipActiveText, fontSize = 14.5.sp, modifier = Modifier.graphicsLayer {
                    val fade = motion.values(frameMs.doubleValue, autoPlay, reducedMotion).fades[0]
                    alpha = max(0.0, 1.0 - abs(fade - index)).toFloat()
                })
            }
        }
    }
}

@Composable
private fun WalletTransactionsRows(
    frameMs: MutableDoubleState,
    motion: WalletTransactionsMotion,
    autoPlay: Boolean,
    reducedMotion: Boolean,
    selectedFilter: Int,
) {
    val lists = listOf(WalletTransactionsData.All, WalletTransactionsData.Recurring, WalletTransactionsData.Friends)
    lists.forEachIndexed { listIndex, rows ->
        rows.forEachIndexed { rowIndex, row ->
            WalletTransactionsRowView(row, rowIndex, Modifier.graphicsLayer {
                val fade = motion.values(frameMs.doubleValue, autoPlay, reducedMotion).fades[rowIndex]
                alpha = max(0.0, 1.0 - abs(fade - listIndex) * if (reducedMotion) 1.0 else 2.0).toFloat()
            }, selectedFilter != listIndex)
        }
    }
}

@Composable
private fun WalletTransactionsRowView(row: WalletTransactionsRow, index: Int, modifier: Modifier, hidden: Boolean) {
    val top = 604.6f + index * 62.8f
    Box(modifier.size(402.dp, 874.dp).semantics(mergeDescendants = true) { if (!hidden) contentDescription = row.accessibilityLabel }) {
        WalletTransactionsRowMark(row, Modifier.offset(16.dp, top.dp).size(49.7.dp))
        WalletTransactionsText(row.name, 73.8f, top + 7.7f, 18f, FontWeight.Normal, WalletTransactionsColors.Ink)
        Row(
            Modifier.offset(73.8.dp, (top + 30f - 13f * CAP_OFFSET).dp).wrapContentSize(Alignment.TopStart, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.meta, color = WalletTransactionsColors.Meta, fontSize = 13.sp)
            if (row.date != null) {
                Text(" • ", color = WalletTransactionsColors.Meta, fontSize = 13.sp)
                Box(Modifier.size(15.dp, 12.dp)) {
                    WalletTransactionsGlyph(WalletTransactionsGlyphKind.Repeat, WalletTransactionsColors.Meta, Modifier.size(12.dp))
                }
                Text(row.date, color = WalletTransactionsColors.Meta, fontSize = 13.sp)
            }
        }
        Text(
            row.amountText,
            fontSize = 18.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.offset(y = (top + 17.2f - 18f * CAP_OFFSET).dp).width(380.7.dp).wrapContentSize(Alignment.TopEnd, unbounded = true),
        )
    }
}

@Composable
private fun WalletTransactionsRowMark(row: WalletTransactionsRow, modifier: Modifier) {
    if (row.icon == WalletTransactionsRowIcon.Puff) {
        WalletTransactionsGlyph(WalletTransactionsGlyphKind.Puff, Color.Transparent, modifier)
        return
    }
    Box(
        modifier
            .background(row.tint ?: Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, row.tint ?: WalletTransactionsColors.RowIconEdge, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (row.icon) {
            WalletTransactionsRowIcon.Dots -> WalletTransactionsGlyph(WalletTransactionsGlyphKind.Dots, Color.Transparent, Modifier.size(47.7.dp))
            WalletTransactionsRowIcon.Sphere -> WalletTransactionsGlyph(WalletTransactionsGlyphKind.Sphere, Color.Transparent, Modifier.size(47.7.dp))
            WalletTransactionsRowIcon.Word -> Text(row.word.orEmpty(), color = Color(0xFF6E6D6C), fontSize = 16.sp, letterSpacing = (-0.3).sp)
            WalletTransactionsRowIcon.Initials -> Text(row.word.orEmpty(), color = WalletTransactionsColors.Figure, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            WalletTransactionsRowIcon.Puff -> Unit
        }
    }
}

@Composable
private fun WalletTransactionsText(
    text: String,
    x: Float,
    capTop: Float,
    size: Float,
    weight: FontWeight,
    color: Color,
    tracking: Float = 0f,
    scaleX: Float = 1f,
    italic: Boolean = false,
) {
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = tracking.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = Modifier.offset(x.dp, (capTop - size * CAP_OFFSET).dp).wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
            this.scaleX = scaleX
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
    )
}

@Composable
private fun WalletTransactionsAmount(
    whole: String,
    cents: String,
    x: Float,
    capTop: Float,
    size: Float,
    dollarX: Float? = null,
    dollarCap: Float? = null,
    dollarSize: Float? = null,
) {
    if (dollarX != null && dollarCap != null && dollarSize != null) {
        WalletTransactionsText("\$", dollarX, dollarCap, dollarSize, FontWeight.Medium, WalletTransactionsColors.Grey)
    }
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = WalletTransactionsColors.Figure)); append(whole); pop()
            pushStyle(SpanStyle(color = WalletTransactionsColors.Grey)); append(cents); pop()
        },
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.6).sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = Modifier.offset(x.dp, (capTop - size * CAP_OFFSET).dp).wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
            scaleX = 0.95f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
    )
}

@Composable
private fun WalletTransactionsPress(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onTouch: () -> Unit,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.semantics {
            role = Role.Button
            contentDescription = label
            if (selected != null) this.selected = selected
            onClick { onTouch(); onClick(); true }
        }.pointerInput(onTouch, onClick) {
            // The gesture scope is restricted, so the press spring runs on the composable's scope.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onTouch()
                scope.launch { scale.animateTo(0.97f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)) }
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                }
                scope.launch {
                    scale.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh))
                    onClick()
                }
            }
        },
    ) { content() }
}

private data class WalletTransactionsAction(val x: Float, val width: Float, val label: String, val glyph: WalletTransactionsGlyphKind?)
private enum class WalletTransactionsRowIcon { Puff, Dots, Sphere, Word, Initials }

private data class WalletTransactionsRow(
    val id: String,
    val name: String,
    val meta: String,
    val date: String?,
    val amount: String,
    val cents: String,
    val icon: WalletTransactionsRowIcon,
    val word: String? = null,
    val tint: Color? = null,
) {
    val amountText: AnnotatedString
        get() = buildAnnotatedString {
            pushStyle(SpanStyle(color = WalletTransactionsColors.Figure)); append(amount); pop()
            pushStyle(SpanStyle(color = WalletTransactionsColors.Grey)); append(cents); pop()
        }
    val accessibilityLabel: String
        get() = "$name, $meta${date?.let { ", repeats, $it" }.orEmpty()}, $amount$cents"
}

private object WalletTransactionsData {
    val All = listOf(
        WalletTransactionsRow("aven-59", "Aven", "Online", "Nov 12, ‘26", "-\$59", ".99", WalletTransactionsRowIcon.Puff),
        WalletTransactionsRow("aven-109", "Aven", "Online", "Nov 14, ‘26", "-\$109", ".99", WalletTransactionsRowIcon.Dots),
        WalletTransactionsRow("sphere", "Studio Sphere", "Salary", null, "\$4,950", ".00", WalletTransactionsRowIcon.Sphere),
        WalletTransactionsRow("rivo", "Rivo Drive", "Online", null, "-\$24", ".23", WalletTransactionsRowIcon.Word, "Rivo"),
    )
    val Recurring = All.filter { it.date != null }
    val Friends = listOf(
        WalletTransactionsRow("nadia", "Nadia Rahman", "Friends", null, "+\$45", ".00", WalletTransactionsRowIcon.Initials, "NR", Color(0xFFE8F1E4)),
        WalletTransactionsRow("jonas", "Jonas Weber", "Friends", null, "-\$18", ".50", WalletTransactionsRowIcon.Initials, "JW", Color(0xFFE9ECFB)),
        WalletTransactionsRow("aiko", "Aiko Tanaka", "Friends", null, "-\$64", ".00", WalletTransactionsRowIcon.Initials, "AT", Color(0xFFFCEDE4)),
    )
}

private object WalletTransactionsColors {
    val Ink = Color(0xFF1F1F1E)
    val Figure = Color(0xFF272726)
    val Label = Color(0xFF737272)
    val Meta = Color(0xFF787777)
    val Grey = Color(0xFFA6A6A5)
    val Icon = Color(0xFF6F6E6D)
    val Pill = Color(0xFF8FA1FD)
    val PillRim = Color(0xFF8394F2)
    val Band = Color(0xFF1F1F1F)
    val Dots = Color(0xFF7D7D7C)
    val Grabber = Color(0xFFF1F1F0)
    val ChipText = Color(0xFF737372)
    val ChipActiveText = Color(0xFFEF60D9)
    val Invest = Color(0xFFF25EEA)
    val Shared = Color(0xFFF8B000)
    val RowIconEdge = Color(0xFFEFEFEF)
    val TileEdge = Color(0xFFF2F2F1)
    val RowGrey = listOf(Color(0xFFB4B4B3), Color(0xFFBEBEBD))
    const val SystemCapHeightRatio = 0.705f
    val TileBrush = Brush.verticalGradient(listOf(Color(0xFFEFF0EF), Color(0xFFF7F7F6), Color(0xFFFAFAF9)))
    val ButtonBrush = Brush.linearGradient(listOf(Color(0xFFEEEEEE), Color(0xFFFDFDFD)))
    val IconSquareBrush = Brush.verticalGradient(listOf(Color(0xFFD8D8D7), Color(0xFFE9E9E8)))
    val ChipBrush = Brush.verticalGradient(listOf(Color(0xFFEFEFEF), Color(0xFFF8F8F7), Color(0xFFFAFAF9)))
    val ChipActiveBrush = Brush.verticalGradient(listOf(Color(0xFFEEE6EC), Color(0xFFF6E8F3), Color(0xFFF6ECF4)))
    val ActionRimBrush = Brush.linearGradient(colorStops = arrayOf(0f to Color(0xFF6A6968), 0.4f to Band, 0.6f to Band, 1f to Color(0xFF5A5958)))
    val ActionCircleBrush = Brush.linearGradient(colorStops = arrayOf(0f to Color(0xFF4C4B4A), 0.4f to Color(0xFF262626), 0.6f to Color(0xFF2B2B2A), 1f to Color(0xFF444342)))
    val ActionCircleInnerBrush = Brush.linearGradient(listOf(Color(0xFF2A2A29), Color(0xFF383837)))
}

private const val CAP_OFFSET = 0.247f

private enum class WalletTransactionsGlyphKind {
    Brand, Chart, Card, Chevron, Arrow, Send, Down, Plus, More, Repeat, Command, People, Puff, Dots, Sphere,
}

private fun WalletTransactionsGlyphKind.baseSize(): Size = when (this) {
    WalletTransactionsGlyphKind.Brand -> Size(27f, 29.3f)
    WalletTransactionsGlyphKind.Chart -> Size(22f, 22f)
    WalletTransactionsGlyphKind.Card -> Size(23f, 19f)
    WalletTransactionsGlyphKind.Chevron -> Size(7f, 12f)
    WalletTransactionsGlyphKind.Arrow -> Size(18f, 15f)
    WalletTransactionsGlyphKind.Send,
    WalletTransactionsGlyphKind.Down,
    WalletTransactionsGlyphKind.Plus -> Size(18f, 18f)
    WalletTransactionsGlyphKind.More -> Size(20f, 4f)
    WalletTransactionsGlyphKind.Repeat -> Size(12f, 12f)
    WalletTransactionsGlyphKind.Command -> Size(16f, 16f)
    WalletTransactionsGlyphKind.People -> Size(17f, 16f)
    WalletTransactionsGlyphKind.Puff,
    WalletTransactionsGlyphKind.Dots,
    WalletTransactionsGlyphKind.Sphere -> Size(49.7f, 49.7f)
}

@Composable
private fun WalletTransactionsGlyph(
    kind: WalletTransactionsGlyphKind,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Float = 2f,
) {
    Canvas(modifier) {
        val base = kind.baseSize()
        scale(size.width / base.width, size.height / base.height, pivot = Offset.Zero) {
            when (kind) {
            WalletTransactionsGlyphKind.Brand -> drawWalletBrand(color)
            WalletTransactionsGlyphKind.Chart -> drawWalletChart(color)
            WalletTransactionsGlyphKind.Card -> drawWalletCard(color)
            WalletTransactionsGlyphKind.Chevron -> drawWalletPolyline(listOf(Offset(1.2f, 1.2f), Offset(5.6f, 6f), Offset(1.2f, 10.8f)), color, stroke)
            WalletTransactionsGlyphKind.Arrow -> {
                drawWalletLine(listOf(Offset(1f, 7.5f), Offset(16.8f, 7.5f)), color, 2f)
                drawWalletPolyline(listOf(Offset(10.6f, 1.2f), Offset(16.9f, 7.5f), Offset(10.6f, 13.8f)), color, 2f)
            }
            WalletTransactionsGlyphKind.Send -> {
                val path = Path().apply {
                    moveTo(1.6f, 9f)
                    lineTo(16.4f, 1.6f)
                    lineTo(12f, 9.4f)
                    lineTo(15.2f, 16.6f)
                    close()
                }
                drawPath(path, color, style = Stroke(1.9f, join = StrokeJoin.Round))
            }
            WalletTransactionsGlyphKind.Down -> {
                drawWalletLine(listOf(Offset(9f, 1.2f), Offset(9f, 16.4f)), color, 1.9f)
                drawWalletPolyline(listOf(Offset(2.6f, 10.2f), Offset(9f, 16.6f), Offset(15.4f, 10.2f)), color, 1.9f)
            }
            WalletTransactionsGlyphKind.Plus -> {
                drawWalletLine(listOf(Offset(9f, 1.2f), Offset(9f, 16.8f)), color, 1.9f)
                drawWalletLine(listOf(Offset(1.2f, 9f), Offset(16.8f, 9f)), color, 1.9f)
            }
            WalletTransactionsGlyphKind.More -> listOf(2f, 10f, 18f).forEach { drawCircle(color, 1.75f, Offset(it, 2f)) }
            WalletTransactionsGlyphKind.Repeat -> drawWalletRepeat(color)
            WalletTransactionsGlyphKind.Command -> drawWalletCommand(color)
            WalletTransactionsGlyphKind.People -> drawWalletPeople(color)
            WalletTransactionsGlyphKind.Puff -> drawWalletPuff()
            WalletTransactionsGlyphKind.Dots -> drawWalletDots()
            WalletTransactionsGlyphKind.Sphere -> drawWalletSphere()
            }
        }
    }
}

private fun DrawScope.drawWalletPolyline(points: List<Offset>, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawWalletLine(points: List<Offset>, color: Color, width: Float) {
    drawWalletPolyline(points, color, width)
}

private fun DrawScope.drawWalletBrand(color: Color) {
    val petals = listOf(
        Triple(9.6f, 4.6f, -62f), Triple(4.8f, 12.4f, 8f), Triple(5.7f, 21.4f, 52f),
        Triple(12.6f, 25f, 92f), Triple(20.6f, 20f, 146f),
    )
    petals.forEach { (x, y, angle) ->
        rotate(angle, Offset(x, y)) {
            drawOval(color, Offset(x - 4.6f, y - 2f), Size(9.2f, 4f))
        }
    }
    drawCircle(color, 4.6f, Offset(20.4f, 11.4f))
}

private fun DrawScope.drawWalletChart(color: Color) {
    drawRoundRect(color, Offset(1f, 1f), Size(20f, 20f), CornerRadius(6f), style = Stroke(1.9f))
    listOf(7f to 11.3f, 11f to 6.6f, 15f to 9.6f).forEach { (x, y) ->
        drawLine(color, Offset(x, y), Offset(x, 16f), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawWalletCard(color: Color) {
    drawRoundRect(color, Offset(1f, 1f), Size(21f, 17f), CornerRadius(4.6f), style = Stroke(1.9f))
    drawLine(color, Offset(1.5f, 6.6f), Offset(21.5f, 6.6f), 1.9f)
    drawLine(color, Offset(12.4f, 12.6f), Offset(14.4f, 12.6f), 2.6f, StrokeCap.Round)
    drawLine(color, Offset(16.9f, 12.6f), Offset(18.4f, 12.6f), 2.6f, StrokeCap.Round)
}

private fun DrawScope.drawWalletRepeat(color: Color) {
    val arc = Path().apply { addArc(Rect(1.9f, 1.4f, 11.1f, 10.6f), -85f, -225f) }
    drawPath(arc, color, style = Stroke(1.3f, cap = StrokeCap.Round))
    drawWalletPolyline(listOf(Offset(1.2f, 7.6f), Offset(2.6f, 9.6f), Offset(4.8f, 8.6f)), color, 1.3f)
    listOf(Offset(8.8f, 1.9f), Offset(10.4f, 3.6f), Offset(11f, 5.9f), Offset(10.4f, 8.2f), Offset(8.9f, 9.9f)).forEach {
        drawCircle(color, 0.65f, it)
    }
}

private fun DrawScope.drawWalletCommand(color: Color) {
    val path = Path().apply {
        moveTo(5.6f, 5.6f); lineTo(10.4f, 5.6f); lineTo(10.4f, 10.4f); lineTo(5.6f, 10.4f); close()
        moveTo(5.6f, 5.6f); lineTo(5.6f, 4.2f)
        cubicTo(5.6f, 2.76f, 4.44f, 1.6f, 3f, 1.6f); cubicTo(1.56f, 1.6f, 0.4f, 2.76f, 0.4f, 4.2f); cubicTo(0.4f, 5.64f, 1.56f, 6.8f, 3f, 6.8f); lineTo(5.6f, 6.8f)
        moveTo(10.4f, 5.6f); lineTo(10.4f, 4.2f)
        cubicTo(10.4f, 2.76f, 11.56f, 1.6f, 13f, 1.6f); cubicTo(14.44f, 1.6f, 15.6f, 2.76f, 15.6f, 4.2f); cubicTo(15.6f, 5.64f, 14.44f, 6.8f, 13f, 6.8f); lineTo(10.4f, 6.8f)
        moveTo(10.4f, 10.4f); lineTo(10.4f, 11.8f)
        cubicTo(10.4f, 13.24f, 11.56f, 14.4f, 13f, 14.4f); cubicTo(14.44f, 14.4f, 15.6f, 13.24f, 15.6f, 11.8f); cubicTo(15.6f, 10.36f, 14.44f, 9.2f, 13f, 9.2f); lineTo(10.4f, 9.2f)
        moveTo(5.6f, 10.4f); lineTo(5.6f, 11.8f)
        cubicTo(5.6f, 13.24f, 4.44f, 14.4f, 3f, 14.4f); cubicTo(1.56f, 14.4f, 0.4f, 13.24f, 0.4f, 11.8f); cubicTo(0.4f, 10.36f, 1.56f, 9.2f, 3f, 9.2f); lineTo(5.6f, 9.2f)
    }
    drawPath(path, color, style = Stroke(1.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawWalletPeople(color: Color) {
    drawCircle(color.copy(alpha = 0.32f), 3f, Offset(12.4f, 4.2f))
    val back = Path().apply {
        moveTo(10.8f, 8.6f)
        cubicTo(14.4f, 8.6f, 16.6f, 10.6f, 16.6f, 13.2f)
        cubicTo(16.6f, 14.6f, 15.8f, 15.4f, 14.4f, 15.4f)
        lineTo(11.6f, 15.4f)
        close()
    }
    drawPath(back, color.copy(alpha = 0.32f))
    drawCircle(color, 3.6f, Offset(6.4f, 4f))
    val front = Path().apply {
        moveTo(0.6f, 13.4f)
        cubicTo(0.6f, 10.4f, 3.2f, 8.6f, 6.4f, 8.6f)
        cubicTo(9.6f, 8.6f, 12.2f, 10.4f, 12.2f, 13.4f)
        cubicTo(12.2f, 14.8f, 11.4f, 15.6f, 10f, 15.6f)
        lineTo(2.8f, 15.6f)
        cubicTo(1.4f, 15.6f, 0.6f, 14.8f, 0.6f, 13.4f)
        close()
    }
    drawPath(front, color)
}

private fun DrawScope.drawWalletPuff() {
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB7B6B6), Color(0xFFC0BFBF))), cornerRadius = CornerRadius(12f))
    drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(9.94f, 11.04f), Size(29.82f, 30.81f), CornerRadius(11.9f))
    drawRoundRect(
        Brush.radialGradient(listOf(Color.White, Color(0xFFF6F6F6), Color(0xFFE2E2E2)), Offset(20.9f, 17.9f), 34.8f),
        Offset(9.94f, 9.44f),
        Size(29.82f, 30.81f),
        CornerRadius(11.9f),
    )
    drawRoundRect(Color(0xFFA9A9A8), Offset(22.37f, 18.39f), Size(4.97f, 12.92f), CornerRadius(2.48f))
}

private fun DrawScope.drawWalletDots() {
    val c = 24.85f
    drawRoundRect(Color(0xFF36C5F0), Offset(c - 8.4f, c - 8.4f), Size(7.6f, 3.4f), CornerRadius(1.7f))
    drawCircle(Color(0xFFE01E5A), 3.4f, Offset(c - 4.2f, c + 3.8f))
    drawRoundRect(Color(0xFF2EB67D), Offset(c + 1.4f, c - 9f), Size(3.4f, 7.6f), CornerRadius(1.7f))
    drawCircle(Color(0xFFECB22E), 3.4f, Offset(c + 4.6f, c + 4.4f))
}

private fun DrawScope.drawWalletSphere() {
    val c = 24.85f
    val radius = 12.6f
    val clip = Path().apply { addOval(Rect(c - radius, c - radius, c + radius, c + radius)) }
    clipPath(clip) {
        for (i in 0 until 5) {
            for (j in 0 until 5) {
                val u = (i + 0.5) / 5.0 * 2.0 - 1.0
                val v = (j + 0.5) / 5.0 * 2.0 - 1.0
                val x = c + (sin(u * 1.2) / sin(1.2) * radius * 0.86).toFloat()
                val y = c + (sin(v * 1.2) / sin(1.2) * radius * 0.86).toFloat()
                val width = (4.6 * cos(u * 1.05)).toFloat()
                val height = (4.6 * cos(v * 1.05)).toFloat()
                drawRoundRect(Color(0xFF2B2A2A), Offset(x - width / 2f, y - height / 2f), Size(width, height), CornerRadius(min(width, height) * 0.4f))
            }
        }
    }
}

@Composable
private fun WalletTransactionsHoldingMark(kind: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 23.7f, size.height / 23.7f, pivot = Offset.Zero) {
            val r = 23.7f / 2f
            drawCircle(Color(0xFFF7F7F6), r, Offset(r, r))
            drawCircle(if (kind == 2) Color(0xFF168FBD) else if (kind == 3) Color(0xFF050505) else Color.White, r - 1.3f, Offset(r, r))
            when (kind) {
            0 -> {
                val path = Path().apply {
                    moveTo(r + 4.7f, r - 6.7f)
                    cubicTo(r - 3.8f, r - 6.1f, r - 7.1f, r - 1.1f, r - 4.8f, r + 4.8f)
                    cubicTo(r - 1.4f, r + 8.1f, r + 4.5f, r + 4.8f, r + 4.7f, r - 6.7f)
                    close()
                }
                drawPath(path, Color(0xFF050505))
                val vein = Path().apply {
                    moveTo(r - 4.3f, r + 4.6f)
                    quadraticTo(r - 0.5f, r + 0.5f, r + 4.4f, r - 6.3f)
                }
                drawPath(vein, Color.White.copy(alpha = 0.72f), style = Stroke(1.15f, cap = StrokeCap.Round))
            }
            1 -> {
                val path = Path().apply {
                    moveTo(r - 8f, r + 2.9f)
                    cubicTo(r - 5.9f, r - 0.7f, r - 3.7f, r - 2.7f, r - 1.2f, r - 2.7f)
                    cubicTo(r + 1.6f, r - 2.7f, r + 2.1f, r + 2.6f, r + 5f, r + 2.6f)
                    cubicTo(r + 6.4f, r + 2.6f, r + 7.3f, r + 1.5f, r + 8f, r + 0.1f)
                }
                drawPath(path, Color(0xFF065FD6), style = Stroke(2.45f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            2 -> {
                val mark = Path().apply {
                    moveTo(r, r - 7f)
                    cubicTo(r + 4.6f, r - 7f, r + 7.2f, r - 4.2f, r + 6.6f, r - 0.5f)
                    cubicTo(r + 3.3f, r - 2.2f, r + 0.8f, r - 2.1f, r, r)
                    cubicTo(r - 0.8f, r - 2.1f, r - 3.3f, r - 2.2f, r - 6.6f, r - 0.5f)
                    cubicTo(r - 7.2f, r - 4.2f, r - 4.6f, r - 7f, r, r - 7f)
                    close()
                    moveTo(r, r)
                    cubicTo(r + 2.5f, r + 1.1f, r + 3.3f, r + 3.4f, r + 1.8f, r + 6.7f)
                    cubicTo(r + 5.7f, r + 6.1f, r + 7.4f, r + 2.9f, r + 6.6f, r - 0.5f)
                    cubicTo(r + 3.2f, r + 0.3f, r + 1.2f, r - 0.1f, r, r)
                    close()
                    moveTo(r, r)
                    cubicTo(r - 2.5f, r + 1.1f, r - 3.3f, r + 3.4f, r - 1.8f, r + 6.7f)
                    cubicTo(r - 5.7f, r + 6.1f, r - 7.4f, r + 2.9f, r - 6.6f, r - 0.5f)
                    cubicTo(r - 3.2f, r + 0.3f, r - 1.2f, r - 0.1f, r, r)
                    close()
                }
                drawPath(mark, Color.White)
                drawCircle(Color(0xFF168FBD), 1.3f, Offset(r, r))
            }
            else -> {
                val bars = listOf(
                    listOf(-6.4f, -5.7f, -18f, 8.6f),
                    listOf(-2.7f, -1.4f, 18f, 9.2f),
                    listOf(-6.1f, 3f, -18f, 8.2f),
                )
                bars.forEach { (x, y, angle, width) ->
                    rotate(angle, Offset(r, r)) {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(Color(0xFF4FE3C1), Color(0xFF9B5CF0))),
                            Offset(r + x, r + y),
                            Size(width, 2.8f),
                            CornerRadius(1.4f),
                        )
                    }
                }
            }
            }
        }
    }
}

private tailrec fun Context.walletTransactionsActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.walletTransactionsActivity()
    else -> null
}
