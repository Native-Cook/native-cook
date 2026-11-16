package com.ngocdevv.nativecook.blocks.pickers.heatshapepicker

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Four heater modes share one continuously deformed body of light. */
@Composable
fun HeatShapePicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    val motion = remember { HeatShapePickerMotion() }
    val art = remember { HeatShapePickerArt() }
    val scope = rememberCoroutineScope()
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle, art) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { active = true; motion.reset() }
            if (event == Lifecycle.Event.ON_PAUSE) { active = false; resumeJob?.cancel() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); resumeJob?.cancel(); art.dispose() }
    }
    LaunchedEffect(autoPlay, reduced) { resumeJob?.cancel(); motion.reset() }
    LaunchedEffect(autoPlay, reduced, active, motion.live) {
        if (active && (autoPlay || motion.live)) {
            while (true) withFrameNanos { motion.now = it / 1e9 }
        }
    }

    fun scheduleResume() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(2000)
            if (!motion.dragging) {
                motion.now = System.nanoTime() / 1e9
                val selected = motion.transition?.target ?: 0
                if (autoPlay) { motion.epoch = (motion.transition?.at ?: motion.now - 2) + 2 - selected * 2; motion.transition = null }
                motion.live = !autoPlay && !reduced
            }
        }
    }
    fun select(index: Int, haptic: Boolean = true) {
        resumeJob?.cancel()
        motion.now = System.nanoTime() / 1e9
        motion.transition = HeatShapePickerTransition(motion.frame(autoPlay, reduced), index, motion.now)
        motion.live = true
        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (!motion.dragging) scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) {
            HeatShapePickerLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top.toDouble(), bottom.toDouble())
        }
        Canvas(Modifier.fillMaxSize()) {
            val now = motion.now
            val frame = motion.frame(autoPlay, reduced)
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    art.draw(canvas.nativeCanvas, frame, layout, reduced, motion.dragX.state(now).first,
                        motion.dragY.state(now).first, motion.grip.state(now).first, motion.direction)
                }
            }
        }
        HeatShapePickerText("LIVING ROOM · 21°", 12f, FontWeight.SemiBold,
            Modifier.fillMaxWidth().offset(y = layout.y(78.0).dp), .55f, 1.8f)
        HeatShapePickerText("Heater mode", 30f, FontWeight.SemiBold,
            Modifier.fillMaxWidth().offset(y = layout.y(102.0).dp))
        for (i in 0..3) {
            val labelModifier = Modifier.fillMaxWidth().graphicsLayer {
                val f = motion.frame(autoPlay, reduced)
                alpha = f.labels[i].toFloat(); translationY = f.labelY[i].toFloat() * density.density
            }.clearAndSetSemantics { }
            HeatShapePickerText(HeatShapePickerModel.names[i], 36f, FontWeight.Bold,
                Modifier.offset(y = layout.y(548.0).dp).then(labelModifier))
            HeatShapePickerText(HeatShapePickerModel.captions[i], 15f, FontWeight.Normal,
                Modifier.offset(y = layout.y(598.0).dp).then(labelModifier), .55f)
            Box(
                Modifier.offset(x = (layout.x(28 + 87.0 * i) - 6).dp, y = (layout.y(676.0) - 6).dp)
                    .size(88.dp)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { select(i) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${HeatShapePickerModel.names[i]}. ${HeatShapePickerModel.captions[i]}"
                        selected = motion.frame(autoPlay, reduced).selected == i
                    },
            ) {
                Box(Modifier.offset(6.dp, 6.dp).size(76.dp)
                    .background(Color.White.copy(alpha = .05f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val selected = motion.frame(autoPlay, reduced).chips[i]
                        scale(density.density, density.density, pivot = Offset.Zero) {
                            drawIntoCanvas { art.glyph(it.nativeCanvas, i, selected) }
                        }
                    }
                    HeatShapePickerText(HeatShapePickerModel.names[i], 11f, FontWeight.Medium,
                        Modifier.fillMaxWidth().offset(y = 56.dp).graphicsLayer {
                            alpha = (.55 + .45 * motion.frame(autoPlay, reduced).chips[i]).toFloat()
                        }.clearAndSetSemantics { })
                }
            }
        }
        val hit = 2.8 * 110 * layout.sx
        Box(Modifier.offset((layout.width / 2 - hit / 2).dp, (layout.y(372.0) - hit / 2).dp)
            .size(hit.dp).clearAndSetSemantics { }
            .pointerInput(motion, autoPlay, reduced, layout) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val localCenter = Offset(size.width / 2f, size.height / 2f)
                    if ((down.position - localCenter).getDistance() > size.width / 2f) return@awaitEachGesture
                    down.consume()
                    resumeJob?.cancel()
                    motion.now = System.nanoTime() / 1e9
                    motion.heldFrame = motion.frame(autoPlay, reduced)
                    motion.dragging = true; motion.live = true
                    motion.grip.retarget(1.0, motion.now)
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.VIRTUAL_KEY)
                    var translation = Offset.Zero
                    var canceled = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            translation = (change.position - down.position) / density.density
                            motion.now = System.nanoTime() / 1e9
                            val x = translation.x.toDouble(); val y = translation.y.toDouble()
                            val length = hypot(x, y); val follow = if (length > 0) min(26.0, length * .22) / length else 0.0
                            motion.dragX.retarget(x * follow, motion.now); motion.dragY.retarget(y * follow, motion.now)
                            if (length > 1) motion.direction = atan2(x, -y)
                            if (!change.pressed) { change.consume(); break }
                            if (change.isConsumed) { canceled = true; break }
                            change.consume()
                        }
                    } finally {
                        motion.now = System.nanoTime() / 1e9
                        val source = motion.frame(autoPlay, reduced)
                        motion.heldFrame = null
                        motion.transition = HeatShapePickerTransition(source, source.selected, motion.now, settling = true)
                        motion.dragging = false
                        motion.grip.retarget(0.0, motion.now, releasing = true)
                        motion.dragX.retarget(0.0, motion.now, releasing = true); motion.dragY.retarget(0.0, motion.now, releasing = true)
                        if (!canceled && abs(translation.x) > 44) {
                            val selected = motion.frame(autoPlay, reduced).selected
                            select((selected + if (translation.x < 0) 1 else 3) % 4, haptic = false)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        scheduleResume()
                    }
                }
            })
    }
}

@Composable
private fun HeatShapePickerText(text: String, size: Float, weight: FontWeight, modifier: Modifier, opacity: Float = 1f, tracking: Float = 0f) {
    BasicText(text, modifier, style = TextStyle(
        color = Color.White.copy(alpha = opacity), fontSize = size.sp, fontWeight = weight,
        letterSpacing = tracking.sp, textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ), maxLines = 1)
}
