package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import kotlin.math.max
import kotlin.math.min

/** Single-clock owner and draft state, matching the accepted React Native controller.ts. */
internal class PitStopReviewController(
    autoPlay: Boolean,
    anchor: Double = 0.0,
) {
    enum class Owner { DEMO, USER }
    enum class Cadence { STOPPED, ANIMATION }

    data class Segment(val from: Double, val to: Double, val rate: Double)

    data class Plan(
        val mode: Mode,
        val anchor: Double,
        val startedAt: Double,
        val segments: List<Segment>,
        val scene: Double,
        val quiet: Boolean,
        val quietFrom: Double,
        val quietTo: Double,
        val quietStart: Double,
    ) {
        enum class Mode { DEMO, PATH, STATIC }
    }

    companion object {
        const val QUIET_FADE = 0.12
        const val RESTART_DELAY = 2.0

        fun pathAt(segments: List<Segment>, elapsed: Double): Double {
            var remaining = max(0.0, elapsed)
            segments.forEach { segment ->
                val duration = (segment.to - segment.from) / segment.rate
                if (remaining < duration) return segment.from + remaining * segment.rate
                remaining -= duration
            }
            return segments.lastOrNull()?.to ?: 0.0
        }

        fun pathDuration(segments: List<Segment>): Double =
            segments.sumOf { segment -> (segment.to - segment.from) / segment.rate }

        fun planScene(plan: Plan, now: Double): Double = when (plan.mode) {
            Plan.Mode.DEMO -> PitStopReviewMotion.wrap(now - plan.anchor)
            Plan.Mode.PATH -> pathAt(plan.segments, now - plan.startedAt)
            Plan.Mode.STATIC -> plan.scene
        }

        fun planQuietPanel(plan: Plan, now: Double): Double {
            val amount = min(1.0, max(0.0, (now - plan.quietStart) / QUIET_FADE))
            return plan.quietFrom + (plan.quietTo - plan.quietFrom) * amount
        }
    }

    var autoPlay = autoPlay
        private set
    var owner = if (autoPlay) Owner.DEMO else Owner.USER
        private set
    var anchor = anchor
        private set
    var restartAt: Double? = null
        private set
    var suspendedAt: Double? = null
        private set
    var rating = 0
        private set
    var note = ""
        private set
    var editorOwned = false
        private set
    var destinationOwned = false
        private set
    var sharing = false
        private set
    var keyboardPresented = false
        private set
    var foreground = true
        private set
    var homeSelected = true
        private set
    var feedAtTop = true
        private set
    var quiet = false
        private set

    private var path: Pair<List<Segment>, Double>? = null
    private var still = 0.0
    private var draftPresentation = false
    private var fadeFrom = 0.0
    private var fadeTo = 0.0
    private var fadeStart = -1.0

    val eligible: Boolean
        get() = autoPlay && foreground && homeSelected && feedAtTop && !quiet &&
            !editorOwned && !destinationOwned && !sharing && !keyboardPresented

    val hasOwnedFlow: Boolean
        get() = editorOwned || destinationOwned

    private fun logical(now: Double): Double = suspendedAt ?: now

    private fun shouldDemo(time: Double): Boolean =
        eligible && (restartAt?.let { time >= it } ?: (owner == Owner.DEMO))

    private fun showsDraft(now: Double): Boolean =
        draftPresentation && !shouldDemo(logical(now))

    fun displayRating(now: Double): Int = if (showsDraft(now)) rating else 0

    fun displayNote(now: Double): String = if (showsDraft(now)) note else ""

    fun scene(now: Double): Double {
        val time = logical(now)
        if (shouldDemo(time)) return PitStopReviewMotion.wrap(time - (restartAt ?: anchor))
        val currentPath = path
        if (currentPath != null) return pathAt(currentPath.first, time - currentPath.second)
        return still
    }

    private fun quietPanel(now: Double): Double {
        val amount = min(1.0, max(0.0, (logical(now) - fadeStart) / QUIET_FADE))
        return fadeFrom + (fadeTo - fadeFrom) * amount
    }

    fun panel(now: Double): Double =
        if (quiet) quietPanel(now) else PitStopReviewMotion.panel(scene(now))

    fun panelVisible(now: Double): Boolean = editorOwned || panel(now) > 0.001

    fun plan(now: Double): Plan {
        val time = logical(now)
        if (quiet) return Plan(
            Plan.Mode.STATIC,
            0.0,
            0.0,
            emptyList(),
            0.0,
            true,
            fadeFrom,
            fadeTo,
            fadeStart,
        )
        if (shouldDemo(time)) return Plan(
            Plan.Mode.DEMO,
            restartAt ?: anchor,
            0.0,
            emptyList(),
            0.0,
            false,
            fadeFrom,
            fadeTo,
            fadeStart,
        )
        val currentPath = path
        if (currentPath != null && !pathDone(time)) return Plan(
            Plan.Mode.PATH,
            0.0,
            currentPath.second,
            currentPath.first,
            0.0,
            false,
            fadeFrom,
            fadeTo,
            fadeStart,
        )
        return Plan(
            Plan.Mode.STATIC,
            0.0,
            0.0,
            emptyList(),
            scene(now),
            false,
            fadeFrom,
            fadeTo,
            fadeStart,
        )
    }

    fun cadence(now: Double): Cadence {
        val time = logical(now)
        if (suspendedAt != null) return Cadence.STOPPED
        if (quiet) return if (time < fadeStart + QUIET_FADE) Cadence.ANIMATION else Cadence.STOPPED
        if (path != null && !pathDone(time)) return Cadence.ANIMATION
        if (!shouldDemo(time)) return Cadence.STOPPED
        return if (PitStopReviewMotion.moving(PitStopReviewMotion.wrap(time - (restartAt ?: anchor)))) {
            Cadence.ANIMATION
        } else {
            Cadence.STOPPED
        }
    }

    fun nextWakeDelay(now: Double): Double? {
        val time = logical(now)
        if (suspendedAt != null) return null
        if (quiet && time < fadeStart + QUIET_FADE) return fadeStart + QUIET_FADE - time
        val currentPath = path
        if (currentPath != null && !pathDone(time)) {
            return currentPath.second + pathDuration(currentPath.first) - time
        }
        val restart = restartAt
        if (restart != null && eligible && time < restart) {
            return restart - time + PitStopReviewMotion.T.press
        }
        if (!shouldDemo(time)) return null
        val phase = PitStopReviewMotion.wrap(time - (restartAt ?: anchor))
        return when {
            phase < PitStopReviewMotion.T.press -> PitStopReviewMotion.T.press - phase
            phase >= PitStopReviewMotion.T.hold && phase < PitStopReviewMotion.T.close ->
                PitStopReviewMotion.T.close - phase
            phase >= PitStopReviewMotion.T.rest ->
                PitStopReviewMotion.LOOP - phase + PitStopReviewMotion.T.press
            else -> null
        }
    }

    fun setAutoPlay(enabled: Boolean, now: Double) {
        if (autoPlay == enabled) return
        normalize(now)
        autoPlay = enabled
        if (!enabled) {
            restartAt = null
            if (owner == Owner.DEMO) {
                owner = Owner.USER
                settle(0.0)
            }
        } else if (eligible) {
            scheduleRestart(logical(now))
        }
    }

    fun openEditor(now: Double) {
        normalize(now)
        if (editorOwned) return
        val time = logical(now)
        val currentScene = scene(now)
        val panelNow = panel(now)
        take()
        editorOwned = true
        draftPresentation = true
        if (quiet) {
            settle(0.0)
            fadeTo(panelNow, 1.0, time)
            return
        }
        begin(toHold(currentScene, PitStopReviewMotion.M.manualRate), time)
    }

    fun closeEditor(now: Double) {
        normalize(now)
        if (!editorOwned && panel(now) <= 0.001) return
        val time = logical(now)
        val currentScene = scene(now)
        val panelNow = panel(now)
        draftPresentation = showsDraft(now)
        editorOwned = false
        keyboardPresented = false
        owner = Owner.USER
        if (quiet) {
            settle(0.0)
            fadeTo(panelNow, 0.0, time)
            restartAt = null
            return
        }
        val segments = toRest(currentScene)
        begin(segments, time)
        restartAt = if (eligible) time + pathDuration(segments) + RESTART_DELAY else null
    }

    fun setRating(value: Int, now: Double) {
        normalize(now)
        capture(now)
        rating = value.coerceIn(0, 5)
        rush(now)
    }

    fun setNote(value: String, now: Double) {
        normalize(now)
        capture(now)
        note = pitStopReviewLimitNote(value)
        rush(now)
    }

    fun setDestinationOwned(owned: Boolean, now: Double) {
        normalize(now)
        val time = logical(now)
        destinationOwned = owned
        owner = Owner.USER
        settle(0.0)
        restartAt = if (!owned && eligible) time + RESTART_DELAY else null
    }

    fun setHomeSelected(selected: Boolean, now: Double) {
        if (homeSelected == selected) return
        normalize(now)
        homeSelected = selected
        updateEligibility(now)
    }

    fun setFeedAtTop(top: Boolean, now: Double) {
        if (feedAtTop == top) return
        normalize(now)
        feedAtTop = top
        updateEligibility(now)
    }

    fun setQuiet(enabled: Boolean, now: Double) {
        if (quiet == enabled) return
        normalize(now)
        val time = logical(now)
        val panelNow = panel(now)
        draftPresentation = showsDraft(now)
        quiet = enabled
        owner = Owner.USER
        settle(if (editorOwned) PitStopReviewMotion.T.hold else 0.0)
        fadeTo(
            if (enabled) panelNow else if (editorOwned) 1.0 else 0.0,
            if (editorOwned) 1.0 else 0.0,
            time,
        )
        restartAt = if (!enabled && eligible) time + RESTART_DELAY else null
    }

    fun beginShare(now: Double): Boolean {
        normalize(now)
        capture(now)
        if (!editorOwned || rating == 0) return false
        sharing = true
        updateSuspension(now)
        return true
    }

    fun setForeground(active: Boolean, now: Double) {
        if (foreground == active) return
        if (!active) preserve(now)
        foreground = active
        updateSuspension(now)
    }

    fun setSharing(active: Boolean, now: Double) {
        if (sharing == active) return
        if (active) preserve(now)
        sharing = active
        updateSuspension(now)
    }

    fun setKeyboardPresented(active: Boolean, now: Double) {
        normalize(now)
        if (active) capture(now)
        if (active && editorOwned) settle(if (quiet) 0.0 else PitStopReviewMotion.T.hold)
        keyboardPresented = active
        updateSuspension(now)
    }

    private fun atRest(scene: Double): Boolean =
        scene < PitStopReviewMotion.T.tap || scene >= PitStopReviewMotion.T.rest

    private fun toHold(scene: Double, rate: Double): List<Segment> {
        if (scene >= PitStopReviewMotion.T.hold && scene <= PitStopReviewMotion.T.close) {
            return listOf(Segment(PitStopReviewMotion.T.hold, PitStopReviewMotion.T.hold, 1.0))
        }
        if (scene > PitStopReviewMotion.T.close && scene < PitStopReviewMotion.T.rest) {
            return listOf(
                Segment(scene, PitStopReviewMotion.T.rest, PitStopReviewMotion.M.closeRush),
                Segment(PitStopReviewMotion.T.tap, PitStopReviewMotion.T.hold, rate),
            )
        }
        if (atRest(scene)) {
            return listOf(Segment(PitStopReviewMotion.T.tap, PitStopReviewMotion.T.hold, rate))
        }
        return listOf(Segment(scene, PitStopReviewMotion.T.hold, rate))
    }

    private fun toRest(scene: Double): List<Segment> {
        if (atRest(scene)) return listOf(Segment(0.0, 0.0, 1.0))
        if (scene < PitStopReviewMotion.T.hold) {
            return listOf(
                Segment(scene, PitStopReviewMotion.T.hold, PitStopReviewMotion.M.closeRush),
                Segment(PitStopReviewMotion.T.close, PitStopReviewMotion.T.rest, 1.0),
            )
        }
        if (scene <= PitStopReviewMotion.T.close) {
            return listOf(Segment(PitStopReviewMotion.T.close, PitStopReviewMotion.T.rest, 1.0))
        }
        return listOf(Segment(scene, PitStopReviewMotion.T.rest, 1.0))
    }

    private fun pathDone(time: Double): Boolean {
        val current = path ?: return true
        return time >= current.second + pathDuration(current.first)
    }

    private fun begin(segments: List<Segment>, time: Double) {
        path = segments to time
    }

    private fun settle(scene: Double) {
        path = null
        still = scene
    }

    private fun take() {
        owner = Owner.USER
        destinationOwned = false
        restartAt = null
    }

    private fun fadeTo(from: Double, to: Double, time: Double) {
        fadeFrom = from
        fadeTo = to
        fadeStart = time
    }

    private fun normalize(now: Double) {
        val time = logical(now)
        val current = path
        if (current != null && pathDone(time)) settle(pathAt(current.first, Double.POSITIVE_INFINITY))
        val restart = restartAt
        if (restart != null && eligible && time >= restart) {
            owner = Owner.DEMO
            anchor = restart
            restartAt = null
            settle(0.0)
        }
    }

    private fun capture(now: Double) {
        if (editorOwned) return
        if (panel(now) <= 0.001) return
        val currentScene = scene(now)
        take()
        editorOwned = true
        draftPresentation = true
        if (quiet) {
            settle(0.0)
            fadeTo(1.0, 1.0, logical(now))
            return
        }
        if (currentScene >= PitStopReviewMotion.T.hold) {
            settle(PitStopReviewMotion.T.hold)
        } else {
            settle(currentScene)
        }
    }

    private fun rush(now: Double) {
        if (!editorOwned || quiet) return
        val time = logical(now)
        val currentScene = scene(now)
        restartAt = null
        owner = Owner.USER
        if (currentScene < PitStopReviewMotion.T.hold) {
            begin(
                listOf(
                    Segment(
                        max(currentScene, PitStopReviewMotion.T.tap),
                        PitStopReviewMotion.T.hold,
                        PitStopReviewMotion.M.rush,
                    ),
                ),
                time,
            )
        }
    }

    private fun updateEligibility(now: Double) {
        val time = logical(now)
        if (eligible) {
            if (owner != Owner.DEMO && !hasOwnedFlow) scheduleRestart(time)
        } else {
            restartAt = null
            if (owner == Owner.DEMO) {
                owner = Owner.USER
                settle(0.0)
            }
        }
    }

    private fun scheduleRestart(time: Double) {
        owner = Owner.USER
        settle(0.0)
        restartAt = time + RESTART_DELAY
    }

    private fun preserve(now: Double) {
        normalize(now)
        if (suspendedAt != null) return
        draftPresentation = showsDraft(now)
    }

    private fun updateSuspension(now: Double) {
        val shouldSuspend = !foreground || sharing || keyboardPresented
        if (shouldSuspend) {
            if (suspendedAt == null) suspendedAt = now
            return
        }
        val start = suspendedAt ?: return
        val pause = max(0.0, now - start)
        suspendedAt = null
        anchor += pause
        restartAt = restartAt?.plus(pause)
        path = path?.let { current -> current.first to current.second + pause }
        fadeStart += pause
        if (eligible && owner == Owner.USER && !hasOwnedFlow && restartAt == null) {
            scheduleRestart(now)
        }
    }
}
