package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardMotion as M
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Colors
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Layout
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Space
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.max

private fun nowSeconds(): Double = System.nanoTime() / 1e9

/**
 * A cardio summary built around a living heart: an ECG trace sweeps across it and each R-peak makes the
 * heart beat; scrubbing the range chart sets the heart to that half hour's BPM.
 */
@Composable
fun PulseHeartCard(autoPlay: Boolean = false) {
    val quiet = rememberReducedMotion() || rememberScreenReader()
    val engine = remember { PulseHeartCardEngine(autoPlay) }
    // Bumped every display frame while the heart moves; only draw and graphicsLayer blocks read it.
    val tick = remember { mutableLongStateOf(0L) }
    // Bumped whenever the clock effect has to start again (a touch, a reset, a scene change).
    var generation by remember { mutableIntStateOf(0) }
    // The selected sample, for text and accessibility (changes on a touch, not per frame).
    var selected by remember { mutableIntStateOf(-1) }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(autoPlay) {
        engine.setAutoPlay(autoPlay, nowSeconds())
        selected = -1
        generation += 1
    }
    LaunchedEffect(quiet) {
        engine.setQuiet(quiet, nowSeconds())
        generation += 1
    }
    DisposableEffect(engine, lifecycle) {
        engine.onSelectionCleared = {
            selected = -1
            generation += 1
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.setForeground(true, nowSeconds())
                Lifecycle.Event.ON_STOP -> engine.setForeground(false, nowSeconds())
                else -> return@LifecycleEventObserver
            }
            if (engine.selectedIndex < 0) selected = -1
            generation += 1
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            engine.onSelectionCleared = null
        }
    }

    // The clock: one step per display frame while something moves; asleep (no frames) while the heart rests.
    LaunchedEffect(engine, generation) {
        while (true) {
            val wake = engine.wakeAt
            if (wake == null) {
                androidx.compose.runtime.withFrameNanos { nanos ->
                    engine.step(nanos / 1e9)
                    tick.longValue += 1
                }
            } else if (wake.isInfinite()) {
                tick.longValue += 1
                awaitCancellation()
            } else {
                delay(max(((wake - nowSeconds()) * 1000).toLong(), 1L))
                androidx.compose.runtime.withFrameNanos { nanos ->
                    engine.step(nanos / 1e9)
                    tick.longValue += 1
                }
            }
        }
    }

    fun buzz() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Colors.background)) {
        val widthDp = maxWidth
        val scale = widthDp.value / PulseHeartCardTheme.REFERENCE_WIDTH
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val insetTop = insets.calculateTopPadding()
        val insetBottom = insets.calculateBottomPadding()
        val contentHeight = max(maxHeight.value - insetTop.value, (PulseHeartCardTheme.REFERENCE_HEIGHT - PulseHeartCardTheme.REFERENCE_SAFE_TOP) * scale)

        fun scrub(x: Float) {
            val wasAsleep = engine.wakeAt != null
            val index = M.clampIndex(((x / scale - 3.9f) / 7.25f).toDouble())
            val result = engine.select(index, nowSeconds())
            if (result.haptic) buzz()
            selected = index
            if (result.changed || wasAsleep) generation += 1
        }

        fun adjust(step: Int) {
            val current = if (selected >= 0) selected else M.barBounds.size - 1
            val index = (current + step).coerceIn(0, M.samples.size - 1)
            engine.select(index, nowSeconds())
            engine.release(nowSeconds())
            selected = index
            generation += 1
        }

        fun resetFromHeart() {
            engine.resetNow(nowSeconds())
            selected = -1
            generation += 1
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(insetTop))
            Box(Modifier.size(widthDp, contentHeight.dp)) {
                Stage(
                    scale = scale,
                    width = widthDp.value,
                    engine = engine,
                    tick = tick,
                    selected = selected,
                    onTouch = ::scrub,
                    onEnd = {
                        engine.release(nowSeconds())
                        generation += 1
                    },
                    onAdjust = ::adjust,
                    onHeart = ::resetFromHeart,
                )
            }
            Spacer(Modifier.height(max(insetBottom.value, 16f).dp))
        }
        PulseHeartCardTabBar(
            scale = scale,
            still = quiet,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (Layout.TAB_BOTTOM * scale).dp),
        )
    }
}

private fun top(referenceY: Float, scale: Float) = (referenceY - PulseHeartCardTheme.REFERENCE_SAFE_TOP) * scale

@Composable
private fun Stage(
    scale: Float,
    width: Float,
    engine: PulseHeartCardEngine,
    tick: androidx.compose.runtime.State<Long>,
    selected: Int,
    onTouch: (Float) -> Unit,
    onEnd: () -> Unit,
    onAdjust: (Int) -> Unit,
    onHeart: () -> Unit,
) {
    val copyLeft = (width - Layout.HEADLINE_WIDTH * scale) / 2
    val bpm = if (selected >= 0) M.samples[selected] else M.REST_BPM.toInt()

    // The heart canvas (one shader) and the reading printed on it.
    PulseHeartCardHeartCanvas(
        engine = engine,
        tick = tick,
        scale = scale,
        width = width.dp,
        modifier = Modifier.offset(y = top(Layout.HEART_CANVAS_TOP, scale).dp),
    )
    PulseHeartCardReading(scale = scale, width = width, bpm = bpm, engine = engine, tick = tick)

    PulseHeartCardMetricStrip(scale = scale, width = width, bpm = bpm, engine = engine, tick = tick, onHeart = onHeart)

    PulseHeartCardText(
        text = "Cardio Fitness\nTrending Up",
        size = Layout.HEADLINE_SIZE * scale, line = Layout.HEADLINE_LINE * scale, weight = FontWeight.Bold, color = Colors.ink,
        maxLines = 2,
        modifier = Modifier
            .offset(x = (Layout.HEADLINE_LEFT * scale).dp, y = top(Layout.HEADLINE_TOP, scale).dp)
            .size((Layout.HEADLINE_WIDTH * scale).dp, (Layout.HEADLINE_HEIGHT * scale).dp)
            .clearAndSetSemantics { contentDescription = "Cardio Fitness. Trending Up." },
    )
    PulseHeartCardText(
        text = "On average, your cardio fitness\nwent up over the last month. This is\ngreat for your long-term health.",
        size = Layout.BODY_SIZE * scale, line = Layout.BODY_LINE * scale, weight = FontWeight.Normal, color = Colors.muted,
        maxLines = 3,
        modifier = Modifier
            .offset(x = (Layout.BODY_LEFT * scale).dp, y = top(Layout.BODY_TOP, scale).dp)
            .size((Layout.BODY_WIDTH * scale).dp, (Layout.BODY_HEIGHT * scale).dp)
            .clearAndSetSemantics {
                contentDescription = "On average, your cardio fitness went up over the last month. This is great for your long-term health."
            },
    )

    PulseHeartCardChart(
        scale = scale,
        engine = engine,
        tick = tick,
        selected = selected,
        onTouch = onTouch,
        onEnd = onEnd,
        onAdjust = onAdjust,
        modifier = Modifier.offset(x = (width / 2 - Layout.CHART_WIDTH / 2 * scale).dp, y = top(Layout.CHART_TOP, scale).dp),
    )

    SupportRow(
        scale, copyLeft, top(Layout.SUPPORT_TOP, scale), "Supporting metrics",
        listOf("Resting Heart Rate" to "55 BPM", "Recovery HRV" to "54 MS"),
    )
    // The pin's next row, mostly under the glass tab bar.
    SupportRow(
        scale, copyLeft, top(Layout.SUPPORT_NEXT_TOP, scale), "More metrics",
        listOf("Walking Heart Rate" to "98 BPM", "Cardio Recovery" to "28 BPM"),
    )
}

/** The reading rides 60 % of the beat and flashes white on the R-peak. */
@Composable
private fun PulseHeartCardReading(scale: Float, width: Float, bpm: Int, engine: PulseHeartCardEngine, tick: androidx.compose.runtime.State<Long>) {
    val numberCentre = Layout.NUMBER_TOP + Layout.NUMBER_LINE / 2
    val unitCentre = Layout.UNIT_TOP + Layout.UNIT_LINE / 2

    fun Modifier.ride(centreY: Float, flash: Boolean) = graphicsLayer {
        val frame = engine.frame
        val ride = 1f + 0.6f * frame.beat.body.toFloat()
        scaleX = ride
        scaleY = ride
        translationY = ((centreY - Layout.HEART_CENTRE_Y) * (ride - 1f) + 0.6f * frame.beat.lift.toFloat()) * scale * density
        if (flash) alpha = 0.85f * frame.beat.flash.toFloat()
        tick.value
    }

    Box(
        Modifier
            .offset(x = (width / 4).dp, y = top(Layout.NUMBER_TOP, scale).dp)
            .size((width / 2).dp, ((Layout.UNIT_TOP + Layout.UNIT_LINE - Layout.NUMBER_TOP) * scale).dp)
            .semantics(mergeDescendants = false) {
                contentDescription = "Heart rate"
                stateDescription = "$bpm beats per minute"
            },
    )
    Box(Modifier.clearAndSetSemantics { }) {
        PulseHeartCardLineBox(
            text = bpm.toString(),
            size = Layout.NUMBER_SIZE * scale, line = Layout.NUMBER_LINE * scale, weight = FontWeight.SemiBold, color = Colors.ink,
            width = width.dp, tabular = true, tracking = Layout.NUMBER_TRACKING * scale,
            modifier = Modifier.offset(y = top(Layout.NUMBER_TOP, scale).dp).ride(numberCentre, flash = false),
        )
        PulseHeartCardLineBox(
            text = "BPM",
            size = Layout.UNIT_SIZE * scale, line = Layout.UNIT_LINE * scale, weight = FontWeight.Medium, color = Colors.bpmUnit,
            width = width.dp,
            modifier = Modifier.offset(y = top(Layout.UNIT_TOP, scale).dp).ride(unitCentre, flash = false),
        )
        PulseHeartCardLineBox(
            text = "BPM",
            size = Layout.UNIT_SIZE * scale, line = Layout.UNIT_LINE * scale, weight = FontWeight.Medium, color = Colors.flash,
            width = width.dp,
            modifier = Modifier.offset(y = top(Layout.UNIT_TOP, scale).dp).ride(unitCentre, flash = true),
        )
    }
}

@Composable
private fun PulseHeartCardMetricStrip(
    scale: Float,
    width: Float,
    bpm: Int,
    engine: PulseHeartCardEngine,
    tick: androidx.compose.runtime.State<Long>,
    onHeart: () -> Unit,
) {
    Row(
        Modifier.width(width.dp).height((Layout.STRIP_HEIGHT * scale).dp).clipToBounds(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        MetricChip("Fitness", "5,253 steps", Layout.CHIP_FITNESS, scale, active = false, modifier = Modifier) {
            PulseHeartCardFitnessIcon(scale)
        }
        Spacer(Modifier.width(((Space.CHIP_GAP + 1) * scale).dp))
        MetricChip(
            "Heart", "$bpm BPM", Layout.CHIP_HEART, scale, active = true,
            modifier = Modifier.pulseHeartCardPress(Role.Button, "Heart, $bpm BPM", onHeart),
        ) {
            PulseHeartCardHeartIcon(
                size = Layout.CHIP_ICON * scale,
                modifier = Modifier.graphicsLayer {
                    val grow = 1f + 1.5f * engine.frame.beat.body.toFloat()
                    scaleX = grow
                    scaleY = grow
                    tick.value
                },
            )
        }
        Spacer(Modifier.width((Space.CHIP_GAP * scale).dp))
        MetricChip("Vitals", "Typical", Layout.CHIP_VITALS, scale, active = false, modifier = Modifier) {
            PulseHeartCardVitalsIcon(scale)
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    width: Float,
    scale: Float,
    active: Boolean,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape((Layout.STRIP_RADIUS * scale).dp)
    Row(
        modifier
            .size((width * scale).dp, (Layout.STRIP_HEIGHT * scale).dp)
            .clip(shape)
            .background(if (active) Color.White else Colors.card)
            .then(if (active) Modifier else Modifier.border((Space.HAIRLINE * scale).dp, Colors.border, shape))
            .padding(horizontal = (Space.CHIP_PADDING * scale).dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size((Layout.CHIP_ICON * scale).dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.padding(start = (Space.CHIP_ICON_GAP * scale).dp).weight(1f)) {
            PulseHeartCardText(title, 16 * scale, 19 * scale, FontWeight.Normal, Colors.muted, Modifier.height((19 * scale).dp))
            PulseHeartCardText(
                value, 17 * scale, 21 * scale, FontWeight.SemiBold, Colors.ink,
                Modifier.height((21 * scale).dp), tabular = true, fitDownTo = 17 * scale * 0.78f,
            )
        }
    }
}

@Composable
private fun SupportRow(scale: Float, left: Float, y: Float, label: String, items: List<Pair<String, String>>) {
    Row(
        Modifier
            .offset(x = left.dp, y = y.dp)
            .width((Layout.HEADLINE_WIDTH * scale).dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.Top,
    ) {
        items.forEachIndexed { index, (title, value) ->
            if (index > 0) Spacer(Modifier.width((Space.METRIC_GAP * scale).dp))
            SupportMetric(title, value, scale)
        }
    }
}

@Composable
private fun SupportMetric(title: String, value: String, scale: Float) {
    Column(
        Modifier
            .width((Layout.SUPPORT_WIDTH * scale).dp)
            .clearAndSetSemantics { contentDescription = "$title, $value" },
    ) {
        Row(Modifier.height((Layout.SUPPORT_TITLE_LINE * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            PulseHeartCardText(
                title, Layout.SUPPORT_TITLE_SIZE * scale, Layout.SUPPORT_TITLE_LINE * scale, FontWeight.SemiBold, Colors.heartDark,
                Modifier.weight(1f, fill = false),
            )
            PulseHeartCardChevron(scale)
        }
        PulseHeartCardText(
            value, Layout.SUPPORT_VALUE_SIZE * scale, Layout.SUPPORT_VALUE_LINE * scale, FontWeight.SemiBold, Colors.supportValue,
            Modifier.padding(top = (Layout.SUPPORT_VALUE_GAP * scale).dp).height((Layout.SUPPORT_VALUE_LINE * scale).dp),
        )
        PulseHeartCardText(
            "Typical", Layout.SUPPORT_NOTE_SIZE * scale, Layout.SUPPORT_NOTE_LINE * scale, FontWeight.Normal, Colors.muted,
            Modifier.height((Layout.SUPPORT_NOTE_LINE * scale).dp),
        )
    }
}
