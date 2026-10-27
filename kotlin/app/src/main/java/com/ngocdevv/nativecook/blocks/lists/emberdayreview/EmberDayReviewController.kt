package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long after the last touch the demo takes the screen back (`setTimeout(..., 2000)` in React Native). */
private const val RESUME_DELAY_MS = 2000L

/** `withSpring(..., { duration: 400, dampingRatio: 1 })` as a Compose spring: Reanimated 4.5 reads `duration` as perceptual and solves the stiffness that settles
 * after 1.5 x 400 ms (`calculateNewStiffnessToMatchDuration`), 424.9 at damping ratio 1. */
private const val PAGE_SPRING_STIFFNESS = 424.9f

/**
 * The state machine of `EmberDayReview.tsx`: the loop clock `time`, the two hand-driven values (`page`, `reviewLift`),
 * the touch-pauses-autoplay rules and the actions of the screen. Everything the screen shows is derived from these,
 * so a frame is a function of loop time (that is what `previewTime` relies on).
 */
internal class EmberDayReviewController(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    initialAutoPlay: Boolean,
    initialPreview: Float?,
    initialReduced: Boolean,
) {
    var autoPlay by mutableStateOf(initialAutoPlay)
        private set
    var preview by mutableStateOf(initialPreview)
        private set
    var reduced by mutableStateOf(initialReduced)
        private set
    var foreground by mutableStateOf(true)
    var onCommit: ((Int) -> Unit)? = null

    var day by mutableStateOf(EmberDayReviewDay.Yesterday)
        private set
    var review by mutableStateOf(false)
        private set
    var completed by mutableStateOf(emptyList<Int>())
        private set
    var tasks by mutableStateOf(EmberDayReviewTodayTasks)
        private set
    var draft by mutableStateOf("")
    var committed by mutableStateOf(false)
        private set
    private var paused by mutableStateOf(false)

    /** The loop clock in seconds, `0..10`. Read it in draw and layer lambdas only. */
    val time = mutableFloatStateOf(initialPreview ?: if (initialReduced || !initialAutoPlay) EMBER_DAY_REVIEW_REST else 0f)
    private val page = mutableFloatStateOf(0f)
    private val reviewLift = mutableFloatStateOf(0f)

    private var holding = false
    private var reviewTarget = false

    /** True while the Add Action field has focus: the demo never takes the screen back then. */
    var editing = false

    private var timeJob: Job? = null
    private var pageJob: Job? = null
    private var reviewJob: Job? = null
    private var resumeJob: Job? = null

    /** `automatic` in React Native: the loop (or a frozen preview frame) drives the screen. */
    val automatic by derivedStateOf {
        preview != null || (autoPlay && !paused && foreground && !reduced && !review && !committed)
    }

    /** 0 = Yesterday, 1 = the Review page is down, 2 = Today: which scene the loop is in. */
    private val autoStage by derivedStateOf {
        if (!automatic) {
            0
        } else if (emberReviewProgress(time.floatValue) > 0.5f) {
            1
        } else if (emberPageProgress(time.floatValue) > 0.5f) {
            2
        } else {
            0
        }
    }

    val visibleDay: EmberDayReviewDay
        get() = if (automatic) {
            if (autoStage == 2) EmberDayReviewDay.Today else EmberDayReviewDay.Yesterday
        } else {
            day
        }

    val reviewVisible: Boolean
        get() = if (automatic) autoStage == 1 else review

    /** 0 on Yesterday, 1 on Today. */
    fun pageProgress(): Float = if (automatic) emberPageProgress(time.floatValue) else page.floatValue

    /** 0 with the day in place, 1 with the day dropped to the foot of the screen. */
    fun reviewProgress(): Float = if (automatic) emberReviewProgress(time.floatValue) else reviewLift.floatValue

    /** Re-reads the inputs the composable gets from its caller and from the system. */
    fun update(autoPlay: Boolean, preview: Float?, reduced: Boolean, onCommit: ((Int) -> Unit)?) {
        this.autoPlay = autoPlay
        this.preview = preview
        this.reduced = reduced
        this.onCommit = onCommit
    }

    /** The effect of React Native that starts, freezes or parks `time` whenever one of its inputs changes. */
    fun syncTime() {
        val frozen = preview
        if (frozen != null) {
            timeJob?.cancel()
            time.floatValue = frozen
            return
        }
        if (!foreground) {
            timeJob?.cancel()
            return
        }
        if (reduced || !autoPlay) {
            timeJob?.cancel()
            time.floatValue = EMBER_DAY_REVIEW_REST
        } else if (automatic) {
            startLoop()
        }
    }

    /** `withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: linear }), -1)` from 0. */
    private fun startLoop() {
        timeJob?.cancel()
        timeJob = scope.launch {
            while (true) {
                time.floatValue = 0f
                animate(
                    initialValue = 0f,
                    targetValue = EMBER_DAY_REVIEW_LOOP.toFloat(),
                    animationSpec = tween(EMBER_DAY_REVIEW_LOOP * 1000, easing = LinearEasing),
                ) { value, _ -> time.floatValue = value }
            }
        }
    }

    /**
     * A touch takes the screen: freezes the loop, keeps what is on screen as hand-driven state and starts the
     * 2 s countdown to giving the screen back (not while a finger is still down).
     */
    private fun touch() {
        resumeJob?.cancel()
        timeJob?.cancel()
        val pageNow = pageProgress()
        val reviewNow = reviewProgress()
        pageJob?.cancel()
        reviewJob?.cancel()
        page.floatValue = pageNow
        reviewLift.floatValue = reviewNow
        paused = true
        day = visibleDay
        reviewTarget = reviewVisible
        review = reviewVisible
        if (!holding) {
            startResumeCountdown()
        }
    }

    private fun startResumeCountdown() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(RESUME_DELAY_MS)
            if (!editing) {
                paused = false
            }
        }
    }

    /** A finger (or a fling) starts on the scrolling panel. */
    fun touchStart() {
        if (preview != null) {
            return
        }
        holding = true
        touch()
    }

    /** The finger lifted: whatever was mid-way settles, then the countdown starts. */
    fun touchEnd() {
        if (preview != null) {
            return
        }
        holding = false
        val reviewEnd = if (reviewTarget) 1f else 0f
        runReview(reviewEnd, if (reviewEnd == 1f) 280 else 240)
        runPage(if (day == EmberDayReviewDay.Today) 1f else 0f)
        startResumeCountdown()
    }

    private fun runReview(target: Float, durationMs: Int) {
        reviewJob?.cancel()
        if (reduced) {
            reviewLift.floatValue = target
            return
        }
        reviewJob = scope.launch {
            animate(
                initialValue = reviewLift.floatValue,
                targetValue = target,
                animationSpec = tween(durationMs, easing = EaseOutStrong),
            ) { value, _ -> reviewLift.floatValue = value }
        }
    }

    private fun runPage(target: Float) {
        pageJob?.cancel()
        if (reduced) {
            page.floatValue = target
            return
        }
        pageJob = scope.launch {
            animate(
                initialValue = page.floatValue,
                targetValue = target,
                animationSpec = spring(dampingRatio = 1f, stiffness = PAGE_SPRING_STIFFNESS, visibilityThreshold = 0.002f),
            ) { value, _ -> page.floatValue = value }
        }
    }

    private fun runTimeToRest() {
        timeJob?.cancel()
        if (reduced) {
            time.floatValue = EMBER_DAY_REVIEW_REST
            return
        }
        timeJob = scope.launch {
            animate(
                initialValue = time.floatValue,
                targetValue = EMBER_DAY_REVIEW_REST,
                animationSpec = tween(650, easing = EaseOutStrong),
            ) { value, _ -> time.floatValue = value }
        }
    }

    /** The Review page opens (the ... button, "0 open") or closes (Done). */
    fun showReview(open: Boolean) {
        if (preview != null) {
            return
        }
        touch()
        reviewTarget = open
        review = open
        runReview(if (open) 1f else 0f, if (open) 280 else 240)
    }

    /** A day is picked: its tab, or the Today button. */
    fun select(next: EmberDayReviewDay) {
        if (preview != null) {
            return
        }
        touch()
        day = next
        runPage(if (next == EmberDayReviewDay.Today) 1f else 0f)
        if (next == EmberDayReviewDay.Yesterday) {
            runTimeToRest()
        }
        haptics.emberDayReviewSelection()
    }

    /** An action is ticked or unticked. */
    fun toggleTask(index: Int) {
        if (preview != null) {
            return
        }
        touch()
        committed = false
        completed = if (completed.contains(index)) completed - index else completed + index
        haptics.emberDayReviewSelection()
    }

    /** Return in the Add Action field: the trimmed text joins the list unless it is empty or already there. */
    fun addTask() {
        if (preview != null) {
            return
        }
        val title = draft.trim()
        if (title.isEmpty()) {
            return
        }
        tasks = if (tasks.contains(title)) tasks else tasks + title
        draft = ""
        committed = false
        haptics.emberDayReviewImpact()
    }

    /** The foot button: Today on Yesterday, Commit on Today. */
    fun primaryPress() {
        if (preview != null) {
            return
        }
        if (visibleDay == EmberDayReviewDay.Yesterday) {
            select(EmberDayReviewDay.Today)
        } else {
            touch()
            committed = true
            onCommit?.invoke(completed.size)
            haptics.emberDayReviewSuccess()
        }
    }

    /** Focus entering or leaving the Add Action field (`onFocus` / `onBlur`). */
    fun focusChanged(focused: Boolean) {
        if (preview != null) {
            return
        }
        editing = focused
        touch()
    }
}
