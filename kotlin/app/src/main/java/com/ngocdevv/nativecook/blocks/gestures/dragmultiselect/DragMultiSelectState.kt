package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** One autoplay period in milliseconds, shared with the React Native reference tokens. */
internal const val LOOP = DragMultiSelectTokens.LOOP

/** The selection engine, one animation clock, and the deterministic 13 second demo script. */
internal class DragMultiSelectState(
    parentScope: CoroutineScope,
    private val autoPlay: Boolean,
    private val reducedMotion: Boolean,
    private val scrollState: ScrollState,
    private val performHaptic: (Haptic) -> Unit,
) {
    internal enum class Haptic {
        ENTER,
        TICK,
        EXIT,
        EDGE,
        COMMIT,
    }

    private val ownerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + ownerJob)
    private val rowMotions = DragMultiSelectTasks.associate { task ->
        task.id to DragMultiSelectRowMotion()
    }
    private val modeValue = mutableFloatStateOf(0f)
    private val pressRowValue = mutableIntStateOf(-1)
    private val pressAnimation = DragMultiSelectAnimatedFloat(0f)
    private val shownAnimation = DragMultiSelectAnimatedFloat(0f)
    private val navFadeAnimation = DragMultiSelectAnimatedFloat(0f)
    private val breathAnimation = DragMultiSelectAnimatedFloat(0f)
    private val trashAnimation = DragMultiSelectAnimatedFloat(0f)
    private val ghostXAnimation = DragMultiSelectAnimatedFloat(0f)
    private val ghostYAnimation = DragMultiSelectAnimatedFloat(0f)
    private val ghostOpacityAnimation = DragMultiSelectAnimatedFloat(0f)
    private val ghostPressAnimation = DragMultiSelectAnimatedFloat(0f)
    private val scrollPosition = mutableFloatStateOf(0f)

    val mode: State<Float> = modeValue
    val pressRow: State<Int> = pressRowValue
    val pressP: State<Float> = pressAnimation
    val shown: State<Float> = shownAnimation
    val navFade: State<Float> = navFadeAnimation
    val breath: State<Float> = breathAnimation
    val trashKick: State<Float> = trashAnimation
    val ghostX: State<Float> = ghostXAnimation
    val ghostY: State<Float> = ghostYAnimation
    val ghostO: State<Float> = ghostOpacityAnimation
    val ghostPress: State<Float> = ghostPressAnimation
    val scrollOffset: State<Float> = scrollPosition

    var tasks by mutableStateOf(DragMultiSelectTasks)
        private set

    var selectedRows by mutableStateOf<Set<Int>>(emptySet())
        private set

    var isSelecting by mutableStateOf(false)
        private set

    var restoredIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var exitSnapshots by mutableStateOf<List<DragMultiSelectExitSnapshot>>(emptyList())
        private set

    var lockScroll by mutableStateOf(false)
        private set

    private var width = 0f
    private var height = 0f
    private var topInset = 0f
    private var bottomInset = 0f
    private var density = 1f
    private var scrollY = 0f
    private var dragging = false
    private var anchor = 0
    private var target = true
    private var current = -1
    private var base = BooleanArray(DragMultiSelectTasks.size)
    private var fingerY = 0f
    private var lastSpeed = 0f
    private var coast = 0f
    private var ambientClock = 0f
    private var demoTime = 0f
    private var paused = !autoPlay
    private var touching = false
    private var quiet = 0f
    private var tidy = -1f
    private var tidyFrom = 0f
    private var scriptDown = false
    private var disposed = false
    private var deleteRemaining = -1f
    private var restoredRemaining = -1f
    private var trashRiseRemaining = -1f

    init {
        scope.launch {
            runClock()
        }
    }

    val listTop: Float
        get() = topInset + DragMultiSelectLayout.navTop +
            DragMultiSelectLayout.nav / 2f - DragMultiSelectLayout.row / 2f

    fun configure(
        width: Float,
        height: Float,
        topInset: Float,
        bottomInset: Float,
        density: Float,
    ) {
        this.width = width
        this.height = height
        this.topInset = topInset
        this.bottomInset = bottomInset
        this.density = density
    }

    fun rowMotion(taskId: String): DragMultiSelectRowMotion {
        return checkNotNull(rowMotions[taskId]) { "Unknown drag-multi-select task: $taskId" }
    }

    fun isSelected(taskId: String): Boolean {
        val index = tasks.indexOfFirst { it.id == taskId }
        return index >= 0 && index in selectedRows
    }

    fun selectedAbove(taskId: String): Boolean {
        val index = tasks.indexOfFirst { it.id == taskId }
        return index >= 0 && index in selectedRows && index - 1 in selectedRows
    }

    fun selectedBelow(taskId: String): Boolean {
        val index = tasks.indexOfFirst { it.id == taskId }
        return index >= 0 && index in selectedRows && index + 1 in selectedRows
    }

    fun onScrollChanged(valuePixels: Int) {
        if (!dragging) {
            scrollY = valuePixels / density
            scrollPosition.floatValue = scrollY
        }
    }

    fun onTouchStart() {
        touching = true
        quiet = 0f
        tidy = -1f
        coast = 0f
        if (!autoPlay || paused) {
            return
        }
        paused = true
        showGhost(false)
        if (scriptDown) {
            scriptDown = false
            dragEnd()
            coast = 0f
        }
    }

    fun onTouchEnd() {
        touching = false
        quiet = 0f
    }

    fun anticipateAt(y: Float) {
        if (reducedMotion || isSelecting || tasks.isEmpty()) {
            return
        }
        pressRowValue.intValue = rowAt(y)
        pressAnimation.snapTo(0f)
        pressAnimation.timingTo(
            next = 1f,
            durationMillis = DragMultiSelectMotion.select.longPress,
            easing = DragMultiSelectEasing.EASE_OUT,
        )
    }

    fun cancelAnticipation() {
        pressAnimation.timingTo(
            next = 0f,
            durationMillis = DragMultiSelectMotion.timing.press,
            easing = DragMultiSelectEasing.EASE_OUT,
        )
    }

    fun holdCircleScroll() {
        if (isSelecting) {
            lockScroll = true
        }
    }

    fun releaseGestureLock() {
        if (!dragging) {
            lockScroll = false
        }
    }

    fun beginLongPress(y: Float) {
        if (tasks.isEmpty()) {
            return
        }
        pressAnimation.snapTo(0f)
        coast = 0f
        lockScroll = true
        val row = rowAt(y)
        fingerY = y
        if (!isSelecting) {
            enter(row, silent = false)
            dragBegin(row, forceTarget = true)
        } else {
            dragBegin(row, forceTarget = null)
        }
    }

    fun beginCircleDrag(y: Float) {
        if (!isSelecting || tasks.isEmpty()) {
            return
        }
        coast = 0f
        lockScroll = true
        fingerY = y
        dragBegin(rowAt(y), forceTarget = null)
    }

    fun dragTo(y: Float) {
        fingerY = y
        dragMove(silent = false)
    }

    fun endDrag() {
        cancelAnticipation()
        dragEnd()
    }

    fun toggleAt(y: Float) {
        if (isSelecting && tasks.isNotEmpty()) {
            toggle(rowAt(y), silent = false)
        }
    }

    fun toggle(taskId: String) {
        onTouchStart()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (isSelecting && index in tasks.indices) {
            toggle(index, silent = false)
        }
        onTouchEnd()
    }

    fun done() {
        onTouchStart()
        exit(silent = false)
        onTouchEnd()
    }

    fun deleteSelected() {
        deleteSelected(silent = false)
    }

    fun leaveFromToolbar() {
        done()
    }

    fun dispose() {
        disposed = true
        ownerJob.cancel()
    }

    private suspend fun runClock() {
        var previousNanos = withFrameNanos { it }
        while (coroutineContext.isActive && !disposed) {
            val nowNanos = withFrameNanos { it }
            val elapsedNanos = (nowNanos - previousNanos).coerceAtLeast(0L)
            previousNanos = nowNanos
            val delta = (elapsedNanos / NANOS_PER_SECOND)
                .coerceAtMost(DragMultiSelectTokens.View.Engine.maxFrameDeltaSeconds)
            tick(delta)
        }
    }

    private fun tick(delta: Float) {
        val scriptAdvanced = if (autoPlay && height > 0f) tickDemo(delta) else false
        if (!scriptAdvanced) {
            autoScroll(delta, silent = !paused)
            coast(delta)
            stepAnimations(delta)
            tickDeferredEffects(delta)
        }
        if (reducedMotion) {
            breathAnimation.snapTo(0f)
        } else {
            ambientClock = if (autoPlay && !paused) demoTime else ambientClock + delta
            breathAnimation.snapTo(sin((2f * PI.toFloat() * ambientClock) / DragMultiSelectMotion.timing.breathPeriod))
        }
    }

    private fun stepAnimations(delta: Float) {
        pressAnimation.step(delta)
        shownAnimation.step(delta)
        navFadeAnimation.step(delta)
        trashAnimation.step(delta)
        ghostXAnimation.step(delta)
        ghostYAnimation.step(delta)
        ghostOpacityAnimation.step(delta)
        ghostPressAnimation.step(delta)
        rowMotions.values.forEach { row ->
            row.setPress(0f)
            row.step(delta)
        }
        exitSnapshots.forEach { it.step(delta) }
        val pressedTaskId = tasks.getOrNull(pressRowValue.intValue)?.id
        if (pressedTaskId != null) {
            rowMotions[pressedTaskId]?.setPress(pressAnimation.value)
        }
    }

    private fun tickDeferredEffects(delta: Float) {
        if (trashRiseRemaining >= 0f) {
            trashRiseRemaining -= delta
            if (trashRiseRemaining <= 0f) {
                trashRiseRemaining = -1f
                trashAnimation.springTo(0f, DragMultiSelectMotion.spring.trash)
            }
        }
        if (deleteRemaining >= 0f) {
            deleteRemaining -= delta
            if (deleteRemaining <= 0f) {
                deleteRemaining = -1f
                exitSnapshots = emptyList()
            }
        }
        if (restoredRemaining >= 0f) {
            restoredRemaining -= delta
            if (restoredRemaining <= 0f) {
                restoredRemaining = -1f
                restoredIds = emptySet()
            }
        }
    }

    private fun tickDemo(delta: Float): Boolean {
        if (paused) {
            if (!touching) {
                quiet += delta
            }
            val resumeSeconds = DragMultiSelectTokens.View.Engine.touchResumeMillis / 1_000f
            if (tidy < 0f && quiet >= resumeSeconds && !dragging) {
                tidy = 0f
                tidyFrom = scrollY
                exit(silent = true)
            }
            if (tidy >= 0f) {
                tidy += delta
                val progress = (tidy / DragMultiSelectMotion.demo.tidy).coerceIn(0f, 1f)
                scrollTo(tidyFrom * (1f - cosineEase(progress)))
                if (progress >= 1f) {
                    tidy = -1f
                    resetLoopBoundary()
                    demoTime = 0f
                    paused = false
                }
            }
            return false
        }
        advanceDemoClock(delta)
        return true
    }

    private fun advanceDemoClock(delta: Float) {
        val loopSeconds = LOOP / 1_000f
        val total = demoTime + delta
        var remaining: Float
        if (total >= loopSeconds) {
            resetLoopBoundary()
            demoTime = 0f
            remaining = total % loopSeconds
        } else {
            remaining = delta
        }
        while (remaining > DragMultiSelectTokens.View.Engine.scriptEpsilon) {
            val segment = minOf(remaining, DragMultiSelectTokens.View.Engine.maxScriptStepSeconds)
            val previous = demoTime
            val next = previous + segment
            // RN processes physical integration before this frame's script events.
            autoScroll(segment, silent = true)
            coast(segment)
            runDemoEvents(previous, next, segment)
            stepAnimations(segment)
            tickDeferredEffects(segment)
            demoTime = next
            remaining = (remaining - segment).coerceAtLeast(0f)
        }
    }

    private fun runDemoEvents(
        previous: Float,
        next: Float,
        delta: Float,
    ) {
        val demo = DragMultiSelectMotion.demo
        val hold = demo.hold
        val midX = width * demo.fingerMidXRatio
        val circleX = width - DragMultiSelectLayout.circleRight -
            DragMultiSelectLayout.bandInset - DragMultiSelectLayout.circle / 2f
        if (cross(previous, next, hold.down)) {
            ghostXAnimation.snapTo(midX)
            ghostYAnimation.snapTo(rowY(hold.row))
            showGhost(true)
            setGhostPressed(1f, DragMultiSelectMotion.select.longPress)
            anticipateAt(hold.row.let(::rowY))
        }
        val holdCommit = hold.down + DragMultiSelectMotion.select.longPress / 1_000f
        if (cross(previous, next, holdCommit)) {
            pressAnimation.snapTo(0f)
            enter(hold.row, silent = true)
            dragBegin(hold.row, forceTarget = true)
            scriptDown = true
            fingerY = rowY(hold.row)
        }
        val drag = demo.drag
        if (scriptDown && next >= drag.first().at && next < demo.release) {
            var y = rowY(drag.last().row)
            for (index in 0 until drag.lastIndex) {
                val from = drag[index]
                val to = drag[index + 1]
                if (next >= from.at && next < to.at) {
                    val progress = cosineEase((next - from.at) / (to.at - from.at))
                    y = rowY(from.row) + (rowY(to.row) - rowY(from.row)) * progress
                    break
                }
            }
            fingerY = y
            ghostYAnimation.snapTo(y)
            dragMove(silent = true)
        }
        if (cross(previous, next, demo.release)) {
            scriptDown = false
            dragEnd()
            setGhostPressed(0f, DragMultiSelectMotion.timing.press)
            showGhost(false)
        }
        val tap = demo.tap
        if (cross(previous, next, tap.at - demo.tapFingerLead)) {
            ghostXAnimation.snapTo(circleX)
            ghostYAnimation.snapTo(rowY(tap.row))
            showGhost(true)
        }
        if (cross(previous, next, tap.at)) {
            toggle(tap.row, silent = true)
            showGhost(false)
        }
        val edge = demo.edge
        val edgeY = height - bottomInset - DragMultiSelectLayout.toolbar.height -
            DragMultiSelectLayout.toolbar.bottom - demo.edgeBottomClearance -
            DragMultiSelectLayout.edge * demo.edgeDepth
        if (cross(previous, next, edge.down)) {
            ghostXAnimation.snapTo(circleX)
            ghostYAnimation.snapTo(rowY(edge.row))
            showGhost(true)
            fingerY = rowY(edge.row)
            dragBegin(rowAt(fingerY), forceTarget = null)
            scriptDown = true
        }
        if (scriptDown && next >= edge.down && next < edge.up) {
            val start = listTop + DragMultiSelectLayout.row * (edge.row + 0.5f)
            val progress = cosineEase((next - edge.down) / (edge.reach - edge.down))
            val y = start + (edgeY - start) * progress
            fingerY = y
            ghostYAnimation.snapTo(y)
            dragMove(silent = true)
        }
        if (cross(previous, next, edge.up)) {
            scriptDown = false
            dragEnd()
            showGhost(false)
        }
        if (cross(previous, next, demo.done - demo.doneFingerLead)) {
            ghostXAnimation.snapTo(width - DragMultiSelectLayout.gutter - DragMultiSelectLayout.done.width / 2f)
            ghostYAnimation.snapTo(listTop + DragMultiSelectLayout.row / 2f)
            showGhost(true)
        }
        if (cross(previous, next, demo.done)) {
            exit(silent = true)
            showGhost(false)
            tidyFrom = scrollY
        }
        val homeDuration = DragMultiSelectMotion.timing.scrollHome / 1_000f
        if (next >= demo.scrollHome && next < demo.scrollHome + homeDuration + delta) {
            coast = 0f
            val progress = (next - demo.scrollHome) / homeDuration
            scrollTo(tidyFrom * (1f - cosineEase(progress)))
        }
        val remove = demo.remove
        if (cross(previous, next, remove.down)) {
            ghostXAnimation.snapTo(midX)
            ghostYAnimation.snapTo(rowY(remove.from))
            showGhost(true)
            setGhostPressed(1f, DragMultiSelectMotion.select.longPress)
            anticipateAt(rowY(remove.from))
        }
        val removeCommit = remove.down + DragMultiSelectMotion.select.longPress / 1_000f
        if (cross(previous, next, removeCommit)) {
            pressAnimation.snapTo(0f)
            enter(remove.from, silent = true)
            dragBegin(remove.from, forceTarget = true)
            scriptDown = true
            fingerY = rowY(remove.from)
        }
        if (scriptDown && next >= removeCommit && next < remove.up) {
            val progress = cosineEase((next - removeCommit) / (remove.up - demo.removeDragLead - removeCommit))
            val y = rowY(remove.from) + (rowY(remove.to) - rowY(remove.from)) * progress
            fingerY = y
            ghostYAnimation.snapTo(y)
            dragMove(silent = true)
        }
        if (cross(previous, next, remove.up)) {
            scriptDown = false
            dragEnd()
            setGhostPressed(0f, DragMultiSelectMotion.timing.press)
            showGhost(false)
        }
        if (cross(previous, next, remove.trash - demo.trashFingerLead)) {
            ghostXAnimation.snapTo(
                width / 2f + DragMultiSelectLayout.toolbar.width / 2f -
                    DragMultiSelectTokens.View.Toolbar.trashPointRightInset -
                    DragMultiSelectTokens.View.Toolbar.trashPointSlotHalf -
                    DragMultiSelectTokens.View.Toolbar.trashPointIconHalf,
            )
            ghostYAnimation.snapTo(
                height - bottomInset - DragMultiSelectLayout.toolbar.bottom -
                    DragMultiSelectLayout.toolbar.height / 2f,
            )
            showGhost(true)
        }
        if (cross(previous, next, remove.trash)) {
            showGhost(false)
            deleteSelected(silent = true)
        }
        if (cross(previous, next, remove.restore)) {
            restoreScriptRows()
        }
    }

    private fun enter(
        row: Int,
        silent: Boolean,
    ) {
        if (isSelecting || row !in tasks.indices) {
            return
        }
        isSelecting = true
        modeValue.floatValue = 1f
        animateToolbarTo(1f)
        navFadeAnimation.timingTo(
            next = 1f,
            durationMillis = DragMultiSelectMotion.timing.reduced,
            easing = DragMultiSelectEasing.EASE_OUT,
        )
        DragMultiSelectTasks.forEachIndexed { index, task ->
            val motion = checkNotNull(rowMotions[task.id])
            motion.setCircleVisible(true, index, reducedMotion)
        }
        publish(setOf(row))
        haptic(Haptic.ENTER, silent)
    }

    private fun exit(silent: Boolean) {
        if (!isSelecting) {
            return
        }
        isSelecting = false
        modeValue.floatValue = 0f
        dragging = false
        lockScroll = false
        lastSpeed = 0f
        publish(emptySet())
        animateToolbarTo(0f)
        navFadeAnimation.timingTo(
            next = 0f,
            durationMillis = DragMultiSelectMotion.timing.reduced,
            easing = DragMultiSelectEasing.EASE_OUT,
        )
        DragMultiSelectTasks.forEachIndexed { index, task ->
            val motion = checkNotNull(rowMotions[task.id])
            motion.setCircleVisible(false, index, reducedMotion)
        }
        haptic(Haptic.EXIT, silent)
    }

    private fun toggle(
        row: Int,
        silent: Boolean,
    ) {
        if (!isSelecting || row !in tasks.indices) {
            return
        }
        val next = selectedRows.toMutableSet()
        if (!next.add(row)) {
            next.remove(row)
        }
        publish(next)
        haptic(Haptic.TICK, silent)
    }

    private fun publish(next: Set<Int>) {
        val bounded = next.filterTo(linkedSetOf()) { it in tasks.indices }
        val previous = selectedRows
        for (index in DragMultiSelectTasks.indices) {
            val wasSelected = index in previous
            val selected = index in bounded
            if (wasSelected != selected) {
                val origin = when {
                    index - 1 in bounded -> -1f
                    index + 1 in bounded -> 1f
                    else -> 0f
                }
                val taskId = tasks.getOrNull(index)?.id ?: continue
                checkNotNull(rowMotions[taskId]).setSelected(selected, reducedMotion, origin)
            }
        }
        selectedRows = bounded
    }

    private fun dragBegin(
        row: Int,
        forceTarget: Boolean?,
    ) {
        if (row !in tasks.indices) {
            return
        }
        dragging = true
        anchor = row
        current = -1
        base = BooleanArray(tasks.size) { index -> index in selectedRows }
        target = forceTarget ?: !base[row]
    }

    private fun dragMove(silent: Boolean) {
        if (!dragging || tasks.isEmpty()) {
            return
        }
        val row = rowAt(fingerY)
        if (row == current) {
            return
        }
        current = row
        val from = minOf(anchor, row)
        val to = maxOf(anchor, row)
        val next = linkedSetOf<Int>()
        for (index in base.indices) {
            val selected = if (index in from..to) target else base[index]
            if (selected) {
                next += index
            }
        }
        if (next == selectedRows) {
            return
        }
        publish(next)
        haptic(Haptic.TICK, silent)
    }

    private fun dragEnd() {
        if (dragging && lastSpeed != 0f && !reducedMotion) {
            coast = lastSpeed * DragMultiSelectMotion.select.autoScrollMax
        }
        lastSpeed = 0f
        dragging = false
        lockScroll = false
    }

    private fun autoScroll(
        delta: Float,
        silent: Boolean,
    ) {
        if (!dragging || height <= 0f) {
            return
        }
        val bottomEdge = height - bottomInset - DragMultiSelectLayout.toolbar.height -
            DragMultiSelectLayout.toolbar.bottom - DragMultiSelectMotion.demo.edgeBottomClearance
        val speed = when {
            fingerY > bottomEdge - DragMultiSelectLayout.edge -> {
                ((fingerY - (bottomEdge - DragMultiSelectLayout.edge)) / DragMultiSelectLayout.edge).coerceAtMost(1f)
            }
            fingerY < listTop + DragMultiSelectLayout.edge -> {
                -((listTop + DragMultiSelectLayout.edge - fingerY) / DragMultiSelectLayout.edge).coerceAtMost(1f)
            }
            else -> 0f
        }
        if (speed != 0f && lastSpeed == 0f) {
            haptic(Haptic.EDGE, silent)
        }
        lastSpeed = speed
        if (speed == 0f) {
            return
        }
        scrollBy(speed * DragMultiSelectMotion.select.autoScrollMax * delta)
        dragMove(silent)
    }

    private fun coast(delta: Float) {
        if (dragging || coast == 0f) {
            return
        }
        val before = scrollY
        scrollBy(coast * delta)
        val next = coast * exp(-delta * DragMultiSelectMotion.select.coastDecay)
        val atBoundary = scrollY == before
        coast = if (abs(next) < DragMultiSelectMotion.select.coastStop || atBoundary) 0f else next
    }

    private fun deleteSelected(silent: Boolean) {
        val selected = selectedRows.sorted()
        if (selected.isEmpty()) {
            return
        }
        haptic(Haptic.COMMIT, silent)
        kickTrash()
        val ids = selected.mapNotNull { tasks.getOrNull(it)?.id }
        val previousTasks = tasks
        exitSnapshots = selected.mapIndexedNotNull { order, row ->
            previousTasks.getOrNull(row)?.let { task ->
                DragMultiSelectExitSnapshot(task, row, order, reducedMotion)
            }
        }
        restoredIds = emptySet()
        exit(silent)
        val nextTasks = previousTasks.filterNot { it.id in ids }
        preparePlacement(previousTasks, nextTasks)
        tasks = nextTasks
        val duration = if (reducedMotion) {
            DragMultiSelectMotion.timing.reduced
        } else {
            DragMultiSelectMotion.timing.streamOut +
                ids.lastIndex.coerceAtLeast(0) * DragMultiSelectMotion.timing.streamStagger
        }
        deleteRemaining = duration / 1_000f
    }

    private fun restoreScriptRows() {
        val existing = tasks.mapTo(hashSetOf()) { it.id }
        restoredIds = DragMultiSelectTasks.filter { it.id !in existing }.mapTo(linkedSetOf()) { it.id }
        val previousTasks = tasks
        preparePlacement(previousTasks, DragMultiSelectTasks)
        restoredIds.forEach { taskId ->
            rowMotions[taskId]?.restore(reducedMotion)
        }
        tasks = DragMultiSelectTasks
        exitSnapshots = emptyList()
        deleteRemaining = -1f
        restoredRemaining = (
            DragMultiSelectMotion.timing.restore +
                DragMultiSelectTokens.View.Engine.restoredCleanupDelayMillis
            ) / 1_000f
    }

    private fun kickTrash() {
        if (reducedMotion) {
            return
        }
        trashAnimation.snapTo(0f)
        trashAnimation.timingTo(
            next = 1f,
            durationMillis = DragMultiSelectMotion.timing.trashUp,
            easing = DragMultiSelectEasing.EASE_OUT,
        )
        trashRiseRemaining = DragMultiSelectMotion.timing.trashUp / 1_000f
    }

    private fun showGhost(visible: Boolean) {
        val next = if (visible) 1f else 0f
        if (reducedMotion) {
            ghostOpacityAnimation.snapTo(next)
            return
        }
        ghostOpacityAnimation.timingTo(
            next = next,
            durationMillis = (DragMultiSelectMotion.demo.fingerFade * 1_000f).roundToInt().toLong(),
        )
    }

    private fun setGhostPressed(
        next: Float,
        durationMillis: Long,
    ) {
        if (reducedMotion) {
            ghostPressAnimation.snapTo(next)
            return
        }
        ghostPressAnimation.timingTo(next, durationMillis)
    }

    private fun resetLoopBoundary() {
        tasks = DragMultiSelectTasks
        restoredIds = emptySet()
        exitSnapshots = emptyList()
        selectedRows = emptySet()
        isSelecting = false
        modeValue.floatValue = 0f
        pressRowValue.intValue = -1
        dragging = false
        lockScroll = false
        lastSpeed = 0f
        coast = 0f
        deleteRemaining = -1f
        restoredRemaining = -1f
        trashRiseRemaining = -1f
        scriptDown = false
        scrollTo(0f)
        pressAnimation.snapTo(0f)
        shownAnimation.snapTo(0f)
        navFadeAnimation.snapTo(0f)
        trashAnimation.snapTo(0f)
        ghostOpacityAnimation.snapTo(0f)
        ghostPressAnimation.snapTo(0f)
        breathAnimation.snapTo(0f)
        rowMotions.values.forEach { it.reset() }
    }

    private fun animateToolbarTo(target: Float) {
        if (reducedMotion) {
            shownAnimation.timingTo(target, DragMultiSelectMotion.timing.reduced)
        } else {
            shownAnimation.springTo(target, DragMultiSelectMotion.spring.toolbar)
        }
    }

    private fun rowAt(y: Float): Int {
        if (tasks.isEmpty()) {
            return 0
        }
        val raw = floor((y + scrollY - listTop) / DragMultiSelectLayout.row).toInt()
        return raw.coerceIn(tasks.indices)
    }

    private fun rowY(row: Int): Float {
        return listTop + DragMultiSelectLayout.row * (row + 0.5f) - scrollY
    }

    private fun scrollBy(delta: Float) {
        if (delta == 0f || density <= 0f) {
            return
        }
        scrollState.dispatchRawDelta(delta * density)
        scrollY = scrollState.value / density
        scrollPosition.floatValue = scrollY
    }

    private fun scrollTo(y: Float) {
        val max = scrollState.maxValue / density
        val target = y.coerceIn(0f, max)
        scrollState.dispatchRawDelta((target - scrollY) * density)
        scrollY = scrollState.value / density
        scrollPosition.floatValue = scrollY
    }

    private fun preparePlacement(
        previous: List<DragMultiSelectTask>,
        next: List<DragMultiSelectTask>,
    ) {
        if (reducedMotion) {
            return
        }
        next.forEachIndexed { nextIndex, task ->
            val previousIndex = previous.indexOfFirst { it.id == task.id }
            if (previousIndex >= 0 && previousIndex != nextIndex) {
                val offset = (previousIndex - nextIndex) * DragMultiSelectLayout.row
                rowMotions[task.id]?.moveFrom(offset)
            }
        }
    }

    private fun haptic(
        kind: Haptic,
        silent: Boolean,
    ) {
        if (!silent) {
            performHaptic(kind)
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

@Composable
internal fun rememberDragMultiSelectState(
    autoPlay: Boolean,
    reducedMotion: Boolean,
    scrollState: ScrollState,
): DragMultiSelectState {
    val parentScope = rememberCoroutineScope()
    val view = LocalView.current
    val currentView by rememberUpdatedState(view)
    val state = remember(autoPlay, reducedMotion, scrollState) {
        DragMultiSelectState(
            parentScope = parentScope,
            autoPlay = autoPlay,
            reducedMotion = reducedMotion,
            scrollState = scrollState,
            performHaptic = { kind -> performDragMultiSelectHaptic(currentView, kind) },
        )
    }
    DisposableEffect(state) {
        onDispose {
            state.dispose()
        }
    }
    return state
}

private fun performDragMultiSelectHaptic(
    view: View,
    kind: DragMultiSelectState.Haptic,
) {
    val feedback = when (kind) {
        DragMultiSelectState.Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        DragMultiSelectState.Haptic.ENTER -> HapticFeedbackConstants.LONG_PRESS
        DragMultiSelectState.Haptic.EXIT -> HapticFeedbackConstants.VIRTUAL_KEY
        DragMultiSelectState.Haptic.COMMIT -> HapticFeedbackConstants.LONG_PRESS
        DragMultiSelectState.Haptic.EDGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    }
    view.performHapticFeedback(feedback)
}

private fun cross(
    previous: Float,
    next: Float,
    at: Float,
): Boolean {
    return previous < at && next >= at
}

private fun cosineEase(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return (1f - cos(PI.toFloat() * clamped)) / 2f
}
