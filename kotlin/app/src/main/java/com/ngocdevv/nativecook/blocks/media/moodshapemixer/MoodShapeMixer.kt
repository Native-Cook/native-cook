package com.ngocdevv.nativecook.blocks.media.moodshapemixer

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.blocks.media.moodshapemixer.MoodShapeMixerModel as M
import kotlin.math.floor
import kotlin.math.pow

private fun moodShapeMixerNow(): Double = System.nanoTime() / 1e9

/** The same smoothstep, cubic-out and cubic-in interpolation used by Swift. */
private data class MoodShapeMixerAnim(val from: Double, val to: Double, val start: Double, val duration: Double, val curve: Int = 0) {
    fun at(time: Double): Double {
        if (duration <= 0) return to
        val p = M.segment(time - start, 0.0, duration)
        val eased = when (curve) {
            1 -> 1 - (1 - p).pow(3)
            2 -> p.pow(3)
            else -> M.ease(p)
        }
        return M.mix(from, to, eased)
    }
    companion object {
        fun still(value: Double) = MoodShapeMixerAnim(value, value, 0.0, 0.0)
    }
}

private class MoodShapeMixerController(val reduced: Boolean, val autoPlay: Boolean, val renderTime: Double?) {
    val origin = moodShapeMixerNow()
    var autoStart: Double? = if (autoPlay && !reduced) origin else null
    var chooser = MoodShapeMixerAnim.still(1.0)
    var panel = MoodShapeMixerAnim.still(if (reduced) 1.0 else 0.0)
    var panelClosing = false
    var panelOrigin = 0.0
    var mood = MoodShapeMixerAnim.still(5.0)
    var lastMood = 5
    var dragging = false
    var asleep = !autoPlay || reduced
    var resumeAt: Double? = null
    var restartAt: Double? = null
    var frame by mutableDoubleStateOf(origin)

    fun shown(at: Double): M.Shown {
        renderTime?.let { return M.scripted(it) }
        autoStart?.let { if (at >= it && !reduced) return M.scripted(at - it) }
        return M.Shown(chooser.at(at), panel.at(at), mood.at(at), at - origin, closing = panelClosing, origin = panelOrigin)
    }
    fun takeOver(): M.Shown {
        resumeAt = null
        restartAt = null
        val s = shown(moodShapeMixerNow())
        autoStart = null
        chooser = MoodShapeMixerAnim.still(s.chooser)
        panel = MoodShapeMixerAnim.still(s.panel)
        mood = MoodShapeMixerAnim.still(s.m)
        panelClosing = s.closing
        panelOrigin = s.origin
        asleep = false
        return s
    }
    fun animate(from: Double, to: Double, duration: Double, curve: Int = 0): MoodShapeMixerAnim =
        MoodShapeMixerAnim(from, to, moodShapeMixerNow(), if (reduced) 0.0 else duration, curve)

    fun scheduleResume() {
        if (reduced) {
            asleep = true
            return
        }
        resumeAt = moodShapeMixerNow() + 2
    }
    fun advance(now: Double) {
        resumeAt?.let { due ->
            if (now >= due && !dragging) {
                val s = shown(now)
                panelClosing = true
                panelOrigin = 0.0
                chooser = MoodShapeMixerAnim(s.chooser, 1.0, due, 0.5)
                panel = MoodShapeMixerAnim(s.panel, 0.0, due, 0.5)
                mood = MoodShapeMixerAnim(s.m, 5.0, due, 0.5)
                resumeAt = null
                restartAt = due + 0.55
            }
        }
        restartAt?.let { due ->
            if (now >= due) {
                panelClosing = false
                panelOrigin = 0.0
                autoStart = if (autoPlay) due else null
                asleep = !autoPlay
                restartAt = null
            }
        }
        frame = now
    }
}

/** A compact feeling slider, faint activity chooser and static line-drawn desk. */
@Composable
fun MoodShapeMixer(modifier: Modifier = Modifier, autoPlay: Boolean = false, renderTime: Double? = null) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val c = remember(reduced, autoPlay, renderTime) { MoodShapeMixerController(reduced, autoPlay, renderTime) }
    val art = remember { MoodShapeMixerArt() }
    var touchSerial by remember { mutableIntStateOf(0) }

    fun redraw() {
        c.frame = moodShapeMixerNow()
        touchSerial += 1
    }
    fun lightHaptic() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun openPanel() {
        val s = c.takeOver()
        c.panelClosing = false
        c.panelOrigin = if (s.panel < 0.1 && s.chooser < 0.5) 1.0 else s.origin
        c.chooser = c.animate(s.chooser, 1.0, 0.3)
        c.panel = c.animate(s.panel, 1.0, 0.4, 1)
        lightHaptic()
        c.scheduleResume()
        redraw()
    }
    fun close() {
        val s = c.takeOver()
        if (s.panel > 0.1) {
            c.panelClosing = true
            c.panelOrigin = 0.0
            c.panel = c.animate(s.panel, 0.0, 0.37)
        } else c.chooser = c.animate(s.chooser, 0.0, 0.4)
        lightHaptic()
        c.scheduleResume()
        redraw()
    }
    fun selectMood(value: Double): Boolean {
        val s = c.takeOver()
        c.mood = c.animate(s.m, M.clamp(value), 0.26, 1)
        lightHaptic()
        c.scheduleResume()
        redraw()
        return true
    }

    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                if (status != null) controller.isAppearanceLightStatusBars = status
                if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
            }
        }
    }

    LaunchedEffect(c, touchSerial) {
        if (renderTime != null || reduced || c.asleep) return@LaunchedEffect
        while (!c.asleep) withFrameNanos { c.advance(it / 1e9) }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val sx = maxWidth.value / 393f
        val sy = maxHeight.value / 852f
        val showOpen by remember(c) { derivedStateOf { c.shown(c.frame).let { it.chooser > 0.5 && it.panel < 0.1 } } }
        val showSlider by remember(c) { derivedStateOf { c.shown(c.frame).panel > 0.8 } }
        val showClose by remember(c) { derivedStateOf { c.shown(c.frame).chooser > 0.1 } }
        val showRoomPill by remember(c) { derivedStateOf { c.shown(c.frame).chooser < 0.1 } }
        Canvas(
            Modifier.fillMaxSize().pointerInput(c) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = c.shown(moodShapeMixerNow())
                    val px = down.position.x / (size.width / 393.0)
                    val py = down.position.y / (size.height / 852.0)
                    val progress = if (s.closing) 1.0 else s.panel
                    val itemWidth = M.itemWidth(s.m)
                    val sliderTop = M.mix(652.0, 656.0, progress)
                    val sliderLeft = M.mix(196.5 - itemWidth / 2, 10.2, progress)
                    val sliderWidth = M.mix(itemWidth, 372.6, progress)
                    if (s.panel <= 0.8 || px !in sliderLeft..(sliderLeft + sliderWidth) || py !in sliderTop..(sliderTop + 68)) return@awaitEachGesture
                    val initial = c.takeOver().m
                    c.dragging = true
                    c.lastMood = M.nearest(initial)
                    redraw()
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = (change.position.x - down.position.x) / (size.width / 393.0)
                        val mood = M.clamp(initial + delta / 47.8)
                        c.mood = MoodShapeMixerAnim.still(mood)
                        val nearest = M.nearest(mood)
                        if (nearest != c.lastMood) {
                            lightHaptic()
                            c.lastMood = nearest
                        }
                        c.frame = moodShapeMixerNow()
                        change.consume()
                    }
                    c.dragging = false
                    val mood = c.mood.to
                    c.mood = c.animate(mood, floor(mood + 0.5), 0.26, 1)
                    c.scheduleResume()
                    redraw()
                }
            },
        ) {
            val shown = c.shown(c.frame)
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(size.width / 393f, size.height / 852f)
                art.draw(canvas, shown, reduced)
                canvas.restore()
            }
        }
        if (showOpen) {
            Box(
                Modifier.offset((68.5f * sx).dp, (652f * sy).dp).size((256f * sx).dp, (68f * sy).dp)
                    .graphicsLayer {
                        scaleX = (M.itemWidth(c.shown(c.frame).m) / 256).toFloat()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { openPanel() }
                    .semantics { contentDescription = "Feeling, " + M.moods[M.nearest(c.shown(c.frame).m)].name.lowercase().replaceFirstChar { it.uppercase() } },
            )
        }
        if (showSlider) {
            Box(
                Modifier.offset((10.2f * sx).dp, (652f * sy).dp).size((372.6f * sx).dp, (68f * sy).dp)
                    .graphicsLayer {
                        val shown = c.shown(c.frame)
                        val progress = if (shown.closing) 1.0 else shown.panel
                        translationY = (4 * progress * sy * density).toFloat()
                        scaleX = (M.mix(M.itemWidth(shown.m), 372.6, progress) / 372.6).toFloat()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .semantics {
                        val m = c.shown(c.frame).m
                        contentDescription = "Feeling slider"
                        stateDescription = M.moods[M.nearest(m)].name.lowercase().replaceFirstChar { it.uppercase() }
                        progressBarRangeInfo = ProgressBarRangeInfo(m.toFloat(), 0f..6f, 5)
                        setProgress { selectMood(it.toDouble()) }
                        customActions = listOf(
                            CustomAccessibilityAction("Previous feeling") { selectMood(floor(m + 0.5) - 1) },
                            CustomAccessibilityAction("Next feeling") { selectMood(floor(m + 0.5) + 1) },
                        )
                    },
            )
        }
        if (showClose) {
            Box(
                Modifier.offset((172.5f * sx).dp, (760f * sy).dp).size((48f * sx).dp, (48f * sy).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { close() }
                    .semantics { contentDescription = if (c.shown(c.frame).panel > 0.1) "Close feeling picker" else "Close activities" },
            )
        }
        if (showRoomPill) {
            Box(
                Modifier.offset((127.5f * sx).dp, (765f * sy).dp).size((138f * sx).dp, (50f * sy).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { openPanel() }
                    .semantics { contentDescription = "Feeling, " + M.moods[M.nearest(c.shown(c.frame).m)].name.lowercase().replaceFirstChar { it.uppercase() } },
            )
        }
    }
}
