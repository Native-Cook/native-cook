package com.ngocdevv.nativecook.blocks.lists.workoutstacklist

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

@Composable
fun WorkoutStackList(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reduced = rememberReducedMotion()
    val motion = remember { WorkoutStackMotion(autoPlay && !reduced) }
    val painter = remember(context) { WorkoutStackListPainter(context) }
    val scope = rememberCoroutineScope()

    var frameTick by remember { mutableLongStateOf(0L) }
    var interactionGeneration by remember { mutableIntStateOf(0) }
    var pressedCategory by remember { mutableStateOf<Int?>(null) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var completionJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            resumeJob?.cancel()
            completionJob?.cancel()
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    LaunchedEffect(autoPlay, reduced) {
        resumeJob?.cancel()
        completionJob?.cancel()
        motion.reset(autoPlay && !reduced)
        interactionGeneration += 1
    }

    LaunchedEffect(interactionGeneration, reduced) {
        var firstFrame = true
        while (isActive && (motion.hasMotion() || firstFrame)) {
            withFrameNanos { now ->
                motion.advance(now)
                frameTick += 1L
                firstFrame = false
            }
        }
    }

    fun wakeMotion() {
        interactionGeneration += 1
    }

    fun scheduleResume() {
        if (!autoPlay || reduced) return
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(WorkoutStackListSpec.Motion.ResumeAfterMs)
            if (reduced) return@launch
            val duration = motion.returnToRest(System.nanoTime())
            wakeMotion()
            delay(duration)
            if (reduced) return@launch
            motion.restartAutoplay(System.nanoTime())
            wakeMotion()
        }
    }

    fun finishBack() {
        completionJob?.cancel()
        val duration = if (reduced) WorkoutStackListSpec.Motion.ReducedFadeMs else WorkoutStackListSpec.Motion.BackMs
        completionJob = scope.launch {
            delay(duration)
            if (!motion.autoplaying) motion.finishBack()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(WorkoutStackListSpec.Colors.Background)
            .semantics { contentDescription = "Workout class browser" }
            .pointerInput(autoPlay, reduced, motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPoint = Offset(down.position.x / density.density, down.position.y / density.density)
                    resumeJob?.cancel()
                    completionJob?.cancel()
                    val now = System.nanoTime()
                    motion.beginTouch(now)
                    wakeMotion()
                    val start = motion.scene()
                    val detail = start.open > .5f && start.leave < .5f
                    val back = detail && workoutBackHitArea(downPoint)
                    val day = if (detail && !back) workoutDayAt(downPoint, start) else null
                    val category = if (!detail) workoutCategoryAt(downPoint, start, size.width / density.density) else null
                    val target = when {
                        back -> WorkoutTouchTarget.Back
                        day != null -> WorkoutTouchTarget.Day(day)
                        detail -> WorkoutTouchTarget.Detail
                        else -> WorkoutTouchTarget.Deck
                    }
                    pressedCategory = category
                    if (category != null) motion.pressCard(true, now)
                    if (back) motion.pressBack(true, now)

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var released = false
                    var cancelled = false
                    var moved = false
                    var releasePoint = downPoint
                    var translationY = 0f

                    while (!released) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            cancelled = true
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        releasePoint = Offset(change.position.x / density.density, change.position.y / density.density)
                        translationY = releasePoint.y - downPoint.y
                        val translationX = releasePoint.x - downPoint.x
                        if (abs(translationY) >= 10f || abs(translationX) >= 10f) {
                            if (!moved) {
                                moved = true
                                motion.pressCard(false, System.nanoTime())
                                motion.pressBack(false, System.nanoTime())
                            }
                            if (abs(translationY) >= 10f) change.consume()
                        }

                        if (moved) {
                            when (target) {
                                WorkoutTouchTarget.Deck -> {
                                    val maximum = WorkoutCategories.size / 2f
                                    var value = start.deck - translationY / WorkoutStackListSpec.Motion.DeckDrag
                                    if (value < 0f) value *= .3f
                                    if (value > maximum) value = maximum + (value - maximum) * .3f
                                    motion.updateDeck(value)
                                }
                                WorkoutTouchTarget.Detail -> {
                                    if (start.month <= .5f) {
                                        var value = start.scroll - translationY / WorkoutStackListSpec.Detail.CollapsedShift
                                        if (value < 0f) value *= .3f
                                        if (value > 1f) value = 1f + (value - 1f) * .3f
                                        motion.updateScroll(value)
                                    }
                                }
                                else -> Unit
                            }
                        }

                        if (!change.pressed) released = true
                    }

                    val velocityY = tracker.calculateVelocity().y / density.density
                    val endNow = System.nanoTime()
                    motion.pressCard(false, endNow)
                    motion.pressBack(false, endNow)
                    if (!cancelled && !moved) {
                        when (target) {
                            WorkoutTouchTarget.Deck -> category?.let { selected ->
                                motion.openCategory(selected, reduced, endNow)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            WorkoutTouchTarget.Back -> {
                                motion.back(reduced, endNow)
                                finishBack()
                            }
                            is WorkoutTouchTarget.Day -> {
                                motion.pickDay(target.value, reduced, endNow)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            WorkoutTouchTarget.Detail -> Unit
                        }
                    } else if (!cancelled) {
                        when (target) {
                            WorkoutTouchTarget.Deck -> {
                                val projected = motion.deck - velocityY * .2f / WorkoutStackListSpec.Motion.DeckDrag
                                val selected = round(projected).coerceIn(0f, WorkoutCategories.size / 2f)
                                motion.snapDeck(selected, -velocityY / WorkoutStackListSpec.Motion.DeckDrag, endNow)
                                if (selected != round(start.deck)) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            WorkoutTouchTarget.Detail -> {
                                if (start.month > .5f) {
                                    if (translationY < -30f || velocityY < -500f) motion.snapMonth(0f, -velocityY / 400f, endNow)
                                } else if (motion.scroll >= .98f && (translationY > 30f || velocityY > 500f) && start.scroll >= .98f) {
                                    motion.snapScroll(1f, 0f, endNow)
                                    motion.snapMonth(1f, velocityY / 400f, endNow)
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                } else {
                                    val projected = motion.scroll - velocityY * .2f / WorkoutStackListSpec.Detail.CollapsedShift
                                    motion.snapScroll(if (projected > .5f) 1f else 0f, -velocityY / WorkoutStackListSpec.Detail.CollapsedShift, endNow)
                                }
                            }
                            else -> Unit
                        }
                    }
                    pressedCategory = null
                    wakeMotion()
                    scheduleResume()
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            frameTick
            drawIntoCanvas { composeCanvas ->
                val nativeCanvas = composeCanvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.scale(density.density, density.density)
                painter.draw(
                    nativeCanvas,
                    width,
                    height,
                    motion.scene(),
                    reduced,
                    pressedCategory,
                    motion.cardPress,
                    motion.backPress,
                )
                nativeCanvas.restore()
            }
        }
        WorkoutAccessibilityLayer(
            motion = motion,
            width = width,
            onCategory = { index ->
                motion.beginTouch(System.nanoTime())
                motion.openCategory(index, reduced, System.nanoTime())
                wakeMotion()
                scheduleResume()
            },
            onBack = {
                motion.beginTouch(System.nanoTime())
                motion.back(reduced, System.nanoTime())
                finishBack()
                wakeMotion()
                scheduleResume()
            },
            onDay = { selected ->
                motion.beginTouch(System.nanoTime())
                motion.pickDay(selected, reduced, System.nanoTime())
                wakeMotion()
                scheduleResume()
            },
        )
    }
}

@Composable
private fun WorkoutAccessibilityLayer(
    motion: WorkoutStackMotion,
    width: Float,
    onCategory: (Int) -> Unit,
    onBack: () -> Unit,
    onDay: (Int) -> Unit,
) {
    val scene = motion.scene()
    val detail = scene.open > .5f && scene.leave < .5f
    if (!detail) {
        WorkoutCategories.forEachIndexed { index, category ->
            val pose = workoutDeckPose(index - scene.deck)
            if (pose.opacity > .01f) {
                val cardWidth = WorkoutStackListSpec.Deck.Width * pose.scale
                val left = (width - WorkoutStackListSpec.Deck.Width) / 2f + WorkoutStackListSpec.DeckShift + (WorkoutStackListSpec.Deck.Width - cardWidth) / 2f
                Box(
                    Modifier
                        .offset(left.dp, pose.top.dp)
                        .size(cardWidth.dp, (WorkoutStackListSpec.Deck.Height * pose.scale).dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = category.name
                            onClick { onCategory(index); true }
                        },
                )
            }
        }
    } else {
        Box(
            Modifier
                .offset((WorkoutStackListSpec.Header.Side - 12f).dp, (WorkoutStackListSpec.Header.Top - 12f).dp)
                .size((WorkoutStackListSpec.Header.Icon + 24f).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Back"
                    onClick { onBack(); true }
                },
        )
        val cell = (WorkoutStackListSpec.ScreenWidth - WorkoutStackListSpec.Detail.WeekSide * 2f) / 7f
        val calendarShift = WorkoutStackListSpec.Detail.CollapsedShift * (1f - scene.scroll)
        WorkoutStackListSpec.Weeks.forEachIndexed { rowIndex, week ->
            if (rowIndex == 0 || scene.month > .01f) {
                week.forEachIndexed { column, day ->
                    if (day != 0) {
                        Box(
                            Modifier
                                .offset(
                                    (WorkoutStackListSpec.Detail.WeekSide + cell * column).dp,
                                    (calendarShift + WorkoutStackListSpec.Detail.NumbersTop - 12f + WorkoutStackListSpec.Detail.MonthRow * rowIndex).dp,
                                )
                                .size(cell.dp, 34.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "April $day"
                                    onClick { onDay(day); true }
                                },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface WorkoutTouchTarget {
    data object Deck : WorkoutTouchTarget
    data object Detail : WorkoutTouchTarget
    data object Back : WorkoutTouchTarget
    data class Day(val value: Int) : WorkoutTouchTarget
}

private fun workoutBackHitArea(point: Offset): Boolean {
    return point.x in (WorkoutStackListSpec.Header.Side - 12f)..(WorkoutStackListSpec.Header.Side + WorkoutStackListSpec.Header.Icon + 12f) &&
        point.y in (WorkoutStackListSpec.Header.Top - 12f)..(WorkoutStackListSpec.Header.Top + WorkoutStackListSpec.Header.Icon + 12f)
}

private fun workoutCategoryAt(point: Offset, scene: WorkoutStackScene, width: Float): Int? {
    WorkoutCategories.indices.forEach { index ->
        val pose = workoutDeckPose(index - scene.deck)
        if (pose.opacity > .01f) {
            val cardWidth = WorkoutStackListSpec.Deck.Width * pose.scale
            val left = (width - WorkoutStackListSpec.Deck.Width) / 2f + WorkoutStackListSpec.DeckShift + (WorkoutStackListSpec.Deck.Width - cardWidth) / 2f
            if (point.x in left..(left + cardWidth) && point.y in pose.top..(pose.top + WorkoutStackListSpec.Deck.Height * pose.scale)) return index
        }
    }
    return null
}

private fun workoutDayAt(point: Offset, scene: WorkoutStackScene): Int? {
    val cell = (WorkoutStackListSpec.ScreenWidth - WorkoutStackListSpec.Detail.WeekSide * 2f) / 7f
    val localY = point.y - WorkoutStackListSpec.Detail.CollapsedShift * (1f - scene.scroll)
    WorkoutStackListSpec.Weeks.forEachIndexed { rowIndex, week ->
        if (rowIndex > 0 && scene.month <= .01f) return@forEachIndexed
        val rowTop = WorkoutStackListSpec.Detail.NumbersTop - 12f + WorkoutStackListSpec.Detail.MonthRow * rowIndex
        if (localY in rowTop..(rowTop + 34f)) {
            val column = ((point.x - WorkoutStackListSpec.Detail.WeekSide) / cell).toInt()
            if (column in week.indices && week[column] != 0) return week[column]
        }
    }
    return null
}
