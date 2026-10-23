package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

/** A to-do list whose long-press drag selects and reversibly unselects a contiguous range. */
@Composable
fun DragMultiSelect(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val state = rememberDragMultiSelectState(
        autoPlay = autoPlay,
        reducedMotion = reducedMotion,
        scrollState = scrollState,
    )
    val topInset = WindowInsets.safeDrawing.getTop(density) / density.density
    val bottomInset = WindowInsets.safeDrawing.getBottom(density) / density.density
    val listTop = topInset + DragMultiSelectLayout.navTop +
        DragMultiSelectLayout.nav / 2f - DragMultiSelectLayout.row / 2f

    DragMultiSelectSystemBars(view)

    LaunchedEffect(state, scrollState) {
        snapshotFlow { scrollState.value }.collect(state::onScrollChanged)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DragMultiSelectColors.background)
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    state.onTouchStart()
                    try {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            pressed = event.changes.any { it.pressed }
                        }
                    } finally {
                        state.onTouchEnd()
                    }
                }
            },
    ) {
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        SideEffect {
            state.configure(
                width = screenWidth,
                height = screenHeight,
                topInset = topInset,
                bottomInset = bottomInset,
                density = density.density,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state, screenWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x / density.density
                        val downY = down.position.y / density.density
                        var lastY = downY
                        var activated = false
                        state.anticipateAt(downY)
                        val startsInCircleColumn = state.isSelecting &&
                            downX >= screenWidth - DragMultiSelectLayout.circleColumn
                        if (startsInCircleColumn) {
                            state.holdCircleScroll()
                        }
                        val outcome = withTimeoutOrNull(DragMultiSelectMotion.select.longPress) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull DragMultiSelectGestureOutcome.CANCELLED
                                lastY = change.position.y / density.density
                                if (!change.pressed) {
                                    return@withTimeoutOrNull DragMultiSelectGestureOutcome.UP
                                }
                                val verticalDistance = abs(lastY - downY)
                                if (
                                    startsInCircleColumn &&
                                    verticalDistance > DragMultiSelectMotion.select.dragSlop
                                ) {
                                    state.beginCircleDrag(downY)
                                    state.dragTo(lastY)
                                    change.consume()
                                    return@withTimeoutOrNull DragMultiSelectGestureOutcome.DRAGGING
                                }
                                val distance = (change.position - down.position).getDistance()
                                if (!startsInCircleColumn && distance > viewConfiguration.touchSlop) {
                                    state.cancelAnticipation()
                                    return@withTimeoutOrNull DragMultiSelectGestureOutcome.MOVED
                                }
                            }
                        }
                        when (outcome) {
                            null -> {
                                state.beginLongPress(lastY)
                                activated = true
                            }
                            DragMultiSelectGestureOutcome.DRAGGING -> activated = true
                            DragMultiSelectGestureOutcome.UP -> {
                                state.cancelAnticipation()
                                state.toggleAt(downY)
                                state.releaseGestureLock()
                            }
                            DragMultiSelectGestureOutcome.MOVED,
                            DragMultiSelectGestureOutcome.CANCELLED,
                            -> {
                                state.cancelAnticipation()
                                state.releaseGestureLock()
                            }
                        }
                        if (activated) {
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    state.dragTo(change.position.y / density.density)
                                    change.consume()
                                }
                            } finally {
                                state.endDrag()
                            }
                        }
                    }
                }
                .verticalScroll(
                    state = scrollState,
                    enabled = !state.lockScroll,
                )
                .padding(
                    top = listTop.dp,
                    bottom = (
                        bottomInset + DragMultiSelectLayout.toolbar.height + DragMultiSelectSpace.xxl
                        ).dp,
                ),
        ) {
            state.tasks.forEachIndexed { index, task ->
                key(task.id) {
                    val rowMotion = state.rowMotion(task.id)
                    DragMultiSelectTaskRow(
                        task = task,
                        index = index,
                        state = state,
                        reduceMotion = reducedMotion,
                        modifier = Modifier.graphicsLayer {
                            translationY = (rowMotion.placement.value + rowMotion.restoreY.value).dp.toPx()
                            alpha = rowMotion.restoreOpacity.value
                        },
                        onToggle = { state.toggle(task.id) },
                    )
                }
            }
        }

        state.exitSnapshots.forEach { snapshot ->
            key("exit-${snapshot.task.id}") {
                DragMultiSelectTaskRow(
                    task = snapshot.task,
                    index = snapshot.row,
                    state = state,
                    reduceMotion = reducedMotion,
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            val progress = snapshot.progress.value
                            translationX = if (reducedMotion) 0f else screenWidth.dp.toPx() * progress
                            translationY = (
                                listTop + snapshot.row * DragMultiSelectLayout.row -
                                    state.scrollOffset.value
                                ).dp.toPx()
                            alpha = if (reducedMotion) 1f - progress else 1f
                        },
                    onToggle = {},
                )
            }
        }

        DragMultiSelectNavBar(
            state = state,
            topInset = topInset.dp,
            onDone = state::done,
        )
        DragMultiSelectToolbar(
            state = state,
            selectedCount = state.selectedRows.size,
            bottomInset = bottomInset.dp,
            reduceMotion = reducedMotion,
            onWhen = state::leaveFromToolbar,
            onMove = state::leaveFromToolbar,
            onDelete = state::deleteSelected,
        )
        if (autoPlay && !DragMultiSelectTokens.QA_HIDE_FINGER) {
            Box(
                modifier = Modifier
                    .size(DragMultiSelectLayout.finger.dp)
                    .graphicsLayer {
                        alpha = state.ghostO.value
                        translationX = (state.ghostX.value - DragMultiSelectLayout.finger / 2f).dp.toPx()
                        translationY = (state.ghostY.value - DragMultiSelectLayout.finger / 2f).dp.toPx()
                        val scale = 1f + DragMultiSelectMotion.demo.fingerPressedScale *
                            state.ghostPress.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBehind {
                        drawCircle(DragMultiSelectColors.finger)
                        drawCircle(
                            color = DragMultiSelectColors.fingerEdge,
                            radius = size.minDimension / 2f - 0.5.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    },
            )
        }
    }
}

@Composable
private fun DragMultiSelectSystemBars(view: android.view.View) {
    DisposableEffect(view) {
        val window = findActivity(view.context)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}

private enum class DragMultiSelectGestureOutcome {
    UP,
    MOVED,
    DRAGGING,
    CANCELLED,
}
