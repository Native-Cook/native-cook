package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min

@Composable
fun GlassPeakChartDemo() {
    GlassPeakChart(autoPlay = true)
    GlassPeakChartSystemBars()
}

/** The full-screen glass chart. Touching pauses autoplay; it returns home and resumes after 3.5 s at rest. */
@Composable
fun GlassPeakChart(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val renderer = remember(context, autoPlay, reducedMotion) {
        GlassPeakChartView(context, autoPlay, reducedMotion)
    }
    var period by remember { mutableIntStateOf(0) }
    var value by remember { mutableIntStateOf(300) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(renderer) {
        renderer.onStateChanged = { nextPeriod, nextValue, nextBusy ->
            if (nextPeriod != period) period = nextPeriod
            if (nextValue != value) value = nextValue
            if (nextBusy != busy) busy = nextBusy
        }
        onDispose { renderer.onStateChanged = null }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val scaleDp = min(maxWidth.value / GlassPeakDesignWidth, maxHeight.value / GlassPeakDesignHeight)
        val originX = (maxWidth.value - GlassPeakDesignWidth * scaleDp) / 2f
        val originY = (maxHeight.value - GlassPeakDesignHeight * scaleDp) / 2f

        AndroidView(
            factory = { renderer },
            update = { it.setReducedMotion(reducedMotion) },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(renderer, scaleDp, originX, originY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val designX = (down.position.x / density - originX) / scaleDp
                        val designY = (down.position.y / density - originY) / scaleDp
                        val action = when {
                            designX in 238f..354f && designY in 210f..254f -> GlassPeakAction.Period
                            designX in 40f..208f && designY in 210f..254f -> GlassPeakAction.Refresh
                            else -> null
                        }
                        val chart = designX in GlassPeakCard.x..(GlassPeakCard.x + GlassPeakCard.width) &&
                            designY in GlassPeakCard.y..(GlassPeakCard.y + GlassPeakCard.height)
                        val tracker = VelocityTracker()
                        renderer.contact(true)
                        if (action != null) renderer.press(action, true)
                        if (chart && action == null) renderer.fingerDown(designX, designY)
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val x = (change.position.x / density - originX) / scaleDp
                            val y = (change.position.y / density - originY) / scaleDp
                            if (chart && action == null && change.pressed) {
                                val velocity = tracker.calculateVelocity().x / density / scaleDp
                                renderer.fingerMove(x, y, velocity)
                                change.consume()
                            }
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity().x / density / scaleDp
                                if (action != null) {
                                    renderer.press(action, false)
                                    val stillInside = when (action) {
                                        GlassPeakAction.Period -> x in 238f..354f && y in 210f..254f
                                        GlassPeakAction.Refresh -> x in 40f..208f && y in 210f..254f
                                    }
                                    if (stillInside) renderer.request(action)
                                } else if (chart) {
                                    renderer.fingerUp(velocity)
                                } else {
                                    renderer.contact(false)
                                }
                                released = true
                            }
                        }
                        if (!released) {
                            if (action != null) renderer.press(action, false)
                            if (chart && action == null) renderer.fingerUp(0f)
                            renderer.contact(false)
                        }
                    }
                },
        )

        Box(
            Modifier
                .offset((originX + GlassPeakCard.x * scaleDp).dp, (originY + 258f * scaleDp).dp)
                .size((GlassPeakCard.width * scaleDp).dp, (236f * scaleDp).dp)
                .semantics {
                    contentDescription = "Conversion rate chart"
                    stateDescription = "$value percent, ${GlassPeakPeriods[period].label}"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..GlassPeakPeriods[period].value)
                    customActions = listOf(
                        CustomAccessibilityAction("Previous point") {
                            renderer.accessibilityAdjust(-1)
                            true
                        },
                        CustomAccessibilityAction("Next point") {
                            renderer.accessibilityAdjust(1)
                            true
                        },
                    )
                },
        )
        Box(
            Modifier
                .offset((originX + 40f * scaleDp).dp, (originY + 210f * scaleDp).dp)
                .size((168f * scaleDp).dp, (44f * scaleDp).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (busy) "Refresh data, updating" else "Refresh data"
                    onClick {
                        renderer.press(GlassPeakAction.Refresh, true)
                        renderer.press(GlassPeakAction.Refresh, false)
                        renderer.request(GlassPeakAction.Refresh)
                        true
                    }
                },
        )
        Box(
            Modifier
                .offset((originX + 238f * scaleDp).dp, (originY + 210f * scaleDp).dp)
                .size((116f * scaleDp).dp, (44f * scaleDp).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Period: ${GlassPeakPeriods[period].label}. Shows the next period"
                    onClick {
                        renderer.press(GlassPeakAction.Period, true)
                        renderer.press(GlassPeakAction.Period, false)
                        renderer.request(GlassPeakAction.Period)
                        true
                    }
                },
        )
    }
}

@Composable
private fun GlassPeakChartSystemBars() {
    val activity = LocalActivity.current ?: return
    val view = activity.window.decorView
    DisposableEffect(activity, view) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
