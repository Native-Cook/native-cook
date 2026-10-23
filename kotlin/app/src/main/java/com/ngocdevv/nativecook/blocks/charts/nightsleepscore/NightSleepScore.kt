package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.accessibility.AccessibilityManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun nightSleepScoreNow(): Double = System.nanoTime() / 1_000_000_000.0

internal fun nightSleepScoreActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

const val NightSleepScoreLoop = NightSleepScoreMotion.loop

@Composable
fun NightSleepScore(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accessibilityManager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    val driver = remember { NightSleepScoreDriver() }
    val controller = driver.controller
    val sample = remember { mutableStateOf(NightSleepScoreSample(NightSleepScoreFrame.REST, NightSleepScoreSkyValues(), false)) }
    var tick by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    var active by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reducedMotion by remember(context) {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    var touchExploration by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager?.let(AccessibilityManagerCompat::isTouchExplorationEnabled) == true)
    }
    var tab by remember { mutableStateOf(NightSleepScoreTab.INSIGHTS) }
    var chartVisible by remember { mutableStateOf(true) }
    val quiet = reducedMotion || touchExploration

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> active = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> active = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(accessibilityManager) {
        val manager = accessibilityManager
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> touchExploration = enabled }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view, context) {
        val window = nightSleepScoreActivity(context)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = bars?.isAppearanceLightStatusBars
        val previousNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = true
        bars?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) bars?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) bars?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    // Applies the inputs (idempotent), then ticks every frame while anything moves and stops when nothing does.
    LaunchedEffect(controller, autoPlay, quiet, active, tab, chartVisible, tick) {
        val now = nightSleepScoreNow()
        controller.appear(autoPlay = autoPlay, quiet = quiet, at = now)
        if (controller.autoPlay != autoPlay) controller.setAutoPlay(autoPlay, now)
        if (controller.quiet != quiet) controller.setQuiet(quiet, now)
        controller.setActive(active, now)
        controller.setVisible(tab == NightSleepScoreTab.INSIGHTS && chartVisible, now)
        while (isActive) {
            val next = driver.sample(nightSleepScoreNow())
            sample.value = next
            if (!next.running) break
            withFrameNanos { }
        }
    }
    LaunchedEffect(tab, scrollState) { scrollState.scrollTo(0) }

    val readout = remember(sample, controller) { derivedStateOf { nightSleepScoreReadoutKey(sample.value.frame, controller) } }

    BoxWithConstraints(modifier.fillMaxSize().background(NightSleepScoreTheme.page)) {
        val scale = maxWidth.value / 402f
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
        val feedLayer = rememberGraphicsLayer()
        val actions = remember(controller, haptics, safeTop, screenHeight) {
            NightSleepScoreCardActions(
                controller, haptics, ::nightSleepScoreNow, wake = { tick += 1 },
                onChartVisibility = { visible -> if (chartVisible != visible) chartVisible = visible },
                safeTop = safeTop, screenHeight = screenHeight,
            )
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    feedLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(feedLayer)
                },
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(top = safeTop.dp, bottom = (112 * scale).dp),
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(min = (screenHeight + 180 * scale - 112 * scale - safeTop).dp)) {
                        NightSleepScoreMetricRail(scale)
                        if (tab == NightSleepScoreTab.INSIGHTS) {
                            Dashboard(scale, sample, readout, actions)
                        } else {
                            Destination(tab, scale)
                        }
                    }
                }
            }

            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(safeTop.dp).background(NightSleepScoreTheme.page).clearAndSetSemantics { })
            NightSleepScoreScrollEdgeBlur(scale, screenHeight, feedLayer, Modifier.align(Alignment.BottomStart))
            NightSleepScoreGlassTabBar(
                active = tab, scale = scale, still = quiet, screenWidth = screenWidth, screenHeight = screenHeight, feedLayer = feedLayer,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (NightSleepScoreTheme.Tab.bottom * scale).dp),
                onSelect = { next ->
                    if (next != tab) {
                        tab = next
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val now = nightSleepScoreNow()
                        controller.setVisible(next == NightSleepScoreTab.INSIGHTS && chartVisible, now, clearSelection = true)
                        tick += 1
                    }
                },
            )
        }
    }
}

@Composable
private fun Dashboard(scale: Float, sample: State<NightSleepScoreSample>, readout: State<NightSleepScoreReadoutKey>, actions: NightSleepScoreCardActions) {
    val theme = NightSleepScoreTheme
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height((75 * scale).dp).padding(start = (32 * scale).dp, end = (32 * scale).dp, top = (27.6f * scale).dp)) {
            NightSleepScoreText(
                "A Good Night’s Sleep",
                nightSleepScoreStyle(nightSleepScoreSp(34f, scale), nightSleepScoreSp(41f, scale), FontWeight.Bold, (-.45f * scale).sp),
                maxLines = 1,
            ) { NightSleepScoreTheme.Day.ink }
        }
        NightSleepScoreText(
            "You got about an hour more sleep last night than the night before, which helped lift your sleep score.",
            nightSleepScoreStyle(nightSleepScoreSp(22f, scale), nightSleepScoreSp(28f, scale)),
            Modifier.padding(horizontal = (32 * scale).dp).offset(0.dp, (-3 * scale).dp).width((338 * scale).dp).height((84 * scale).dp),
        ) { NightSleepScoreTheme.Day.secondary }
        NightSleepScoreSleepCard(scale, sample, readout, actions, Modifier.align(Alignment.CenterHorizontally).padding(top = (12 * scale).dp))
        ReadinessCard(scale, Modifier.align(Alignment.CenterHorizontally).padding(top = (11 * scale).dp))
    }
}

@Composable
private fun ReadinessCard(scale: Float, modifier: Modifier = Modifier) {
    val theme = NightSleepScoreTheme
    val G = NightSleepScoreTheme.Gauge
    val shape = RoundedCornerShape((28 * scale).dp)
    Box(
        modifier.size((370 * scale).dp, (290 * scale).dp)
            .shadow((12 * scale).dp, shape, ambientColor = theme.shadow, spotColor = theme.shadow)
            .clip(shape).background(theme.card).border(1.dp, Color.White.copy(alpha = .75f), shape),
    ) {
        NightSleepScoreText(
            "Readiness", nightSleepScoreStyle(nightSleepScoreSp(15f, scale), nightSleepScoreSp(18f, scale)),
            Modifier.offset((16 * scale).dp, (16.3f * scale).dp),
        ) { NightSleepScoreTheme.Day.secondary }
        NightSleepScoreText(
            "Ready", nightSleepScoreStyle(nightSleepScoreSp(19f, scale), nightSleepScoreSp(23f, scale), FontWeight.Bold),
            Modifier.offset((16 * scale).dp, (38 * scale).dp),
        ) { NightSleepScoreTheme.Day.ink }
        // Readiness gauge: a 110 x 80 box whose origin sits at (55, 57).
        Box(Modifier.offset(((G.cx - 55) * scale).dp, ((G.cy - 57) * scale).dp).size((110 * scale).dp, (80 * scale).dp).clearAndSetSemantics { contentDescription = "Readiness 7" }) {
            Canvas(Modifier.size((110 * scale).dp, (80 * scale).dp)) {
                val k = scale * density
                drawScale(k, k, pivot = Offset.Zero) {
                    fun arc(from: Float, to: Float) = nightSleepScoreArc(55f, 57f, G.r, from, to)
                    val style = Stroke(G.stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawPath(arc(G.from, G.to), G.track, style = style)
                    drawPath(arc(G.lightFrom, G.lightTo), G.light, style = style)
                    val a = G.dot * 2 * PI
                    drawCircle(G.blue, G.dotR, Offset(55f + G.r * cos(a).toFloat(), 57f + G.r * sin(a).toFloat()))
                }
            }
            NightSleepScoreText(
                "7",
                nightSleepScoreStyle(nightSleepScoreSp(G.numberSize, scale), nightSleepScoreSp(G.numberSize * 1.2f, scale), FontWeight.Bold)
                    .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.width((110 * scale).dp).offset(0.dp, ((57 + G.numberBase - G.numberSize * .36f - G.numberSize * .6f) * scale).dp),
            ) { G.blue }
        }
        Canvas(Modifier.offset((16 * scale).dp, (88 * scale).dp).size((338 * scale).dp, maxOf(1f, scale).dp).clearAndSetSemantics { }) { drawRect(NightSleepScoreTheme.Day.divider) }
        NightSleepScoreText(
            "Your training load has been lower than your normal level for the past few days and all your other metrics are in good shape. It looks like you’re ready for a demanding day.",
            nightSleepScoreStyle(nightSleepScoreSp(15f, scale), nightSleepScoreSp(20f, scale)),
            Modifier.offset((16 * scale).dp, (103 * scale).dp).width((338 * scale).dp),
        ) { NightSleepScoreTheme.Day.ink }
    }
}

@Composable
private fun Destination(tab: NightSleepScoreTab, scale: Float) {
    val theme = NightSleepScoreTheme
    val longevity = tab == NightSleepScoreTab.LONGEVITY
    val title = if (longevity) "A gentler evening" else "Sleep stages"
    val lines = if (longevity) {
        listOf("Small routines can make room for a calmer night.", "Keep your wake time steady when it feels comfortable.", "Dim bright lights during your wind-down.", "Leave a little quiet before bed.")
    } else {
        listOf("A quick guide to the colors in your night.", "Awake: 14 minutes marked in coral.", "REM: 1 hour 3 minutes marked in turquoise.", "Core: 5 hours 33 minutes marked in blue.", "Deep: 36 minutes marked in violet.")
    }
    val shape = RoundedCornerShape((28 * scale).dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = (16 * scale).dp).padding(top = (46 * scale).dp)
            .shadow((12 * scale).dp, shape, ambientColor = theme.shadow, spotColor = theme.shadow)
            .clip(shape).background(theme.card).border(1.dp, Color.White.copy(alpha = .75f), shape)
            .padding((24 * scale).dp),
    ) {
        NightSleepScoreText(
            title, nightSleepScoreStyle(nightSleepScoreSp(28f, scale), nightSleepScoreSp(34f, scale), FontWeight.Bold, (-.7f * scale).sp),
            Modifier.padding(bottom = (18 * scale).dp),
        ) { NightSleepScoreTheme.Day.ink }
        lines.forEachIndexed { i, text ->
            val style = nightSleepScoreStyle(nightSleepScoreSp(17f, scale), nightSleepScoreSp(24f, scale))
            val modifier = Modifier.fillMaxWidth().padding(bottom = if (i == lines.lastIndex) 0.dp else (18 * scale).dp).let {
                if (i > 0) it.clip(RoundedCornerShape((18 * scale).dp)).background(Color.White.copy(alpha = .72f)).padding((16 * scale).dp) else it
            }
            NightSleepScoreText(text, style, modifier) { NightSleepScoreTheme.Day.secondary }
        }
    }
}
