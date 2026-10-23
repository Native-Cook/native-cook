package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign

/** AUTO → TOUCH → RETURN hand-off and the exact touch physics from use-places-motion.ts. */
internal class SavedPlacesMapController(
    private val autoPlay: Boolean,
    reducedMotion: Boolean,
    calm: Boolean,
) {
    /** Reduce Motion (animator scale 0): flat map, short fades. Followed live through [configure]. */
    private var reducedMotion = reducedMotion

    /** Reduce Motion or a screen reader: the loop is off and the screen rests at the pin's frame. */
    private var calm = calm
    private val loops get() = autoPlay && !calm

    private enum class Mode { AUTO, TOUCH, RETURN }

    private data class SheetTransition(
        val start: Double,
        val target: Double,
        val velocity: Double,
        val startedAt: Double,
        val spring: SavedPlacesMapMotion.Spring,
        val timing: Boolean,
    )

    companion object {
        const val HIT_NONE = 0
        const val HIT_CHECKBOX = 1
        const val HIT_TILE = 2
        const val HIT_MARKER = 10

        const val HAPTIC_NONE = 0
        const val HAPTIC_DETENT = 1
        const val HAPTIC_VISIT_ON = 2
        const val HAPTIC_SELECT = 3
    }

    var frame = SavedPlacesMapMotion.REST
        private set

    private var mode = if (loops) Mode.AUTO else Mode.TOUCH
    private var autoAnchor = Double.NaN
    private var previousTime = Double.NaN
    private var touchedAt = Double.NEGATIVE_INFINITY
    private var sheet = 0.0
    private var cluster = 0.0
    private var sheetTransition: SheetTransition? = null
    private var visitDirection = 0
    private var visitStartedAt = Double.NEGATIVE_INFINITY
    private var tapIndex = -1
    private var tapStartedAt = Double.NEGATIVE_INFINITY
    private var pressTarget = HIT_CHECKBOX
    private var pressStartedAt = Double.NEGATIVE_INFINITY
    private var pressReleasedAt = Double.NEGATIVE_INFINITY
    private var pressReleaseValue = 0.0
    private var grabStartedAt = Double.NEGATIVE_INFINITY
    private var grabReleasedAt = Double.NEGATIVE_INFINITY
    private var grabReleaseValue = 0.0
    private var armed = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var dragStartSheet = 0.0
    private var pausedAt = Double.NaN

    /** Follows Reduce Motion and the screen reader live: a loop switched off settles to the pin's frame and stays. */
    fun configure(reduced: Boolean, calmNow: Boolean, nowNanos: Long) {
        if (reduced == reducedMotion && calmNow == calm) {
            return
        }
        val now = nowNanos / 1_000_000_000.0
        val wasLooping = loops
        reducedMotion = reduced
        calm = calmNow
        if (wasLooping && !loops && mode == Mode.AUTO) {
            val loopTime = ((now - autoAnchor) % SavedPlacesMapMotion.LOOP + SavedPlacesMapMotion.LOOP) % SavedPlacesMapMotion.LOOP
            sheet = frame.sheet
            cluster = frame.cluster
            when {
                loopTime >= SavedPlacesMapMotion.T.check && loopTime < SavedPlacesMapMotion.T.uncheck -> {
                    // A tick in progress is unticked at once, as RN does when the loop is switched off.
                    visitDirection = 0
                    visitStartedAt = now
                }
                loopTime >= SavedPlacesMapMotion.T.uncheck -> {
                    visitDirection = 0
                    visitStartedAt = now - (loopTime - SavedPlacesMapMotion.T.uncheck)
                }
                else -> {
                    visitDirection = 0
                    visitStartedAt = Double.NEGATIVE_INFINITY
                }
            }
            if (frame.press > 0.0) {
                pressReleaseValue = frame.press
                pressReleasedAt = now
                pressStartedAt = Double.NEGATIVE_INFINITY
            }
            beginSheetTransition(0.0, 0.0, now, SavedPlacesMapMotion.Springs.sheetFall, timing = reduced)
            mode = Mode.RETURN
            touchedAt = Double.NEGATIVE_INFINITY
        }
    }

    fun setLifecycleActive(active: Boolean, nowNanos: Long) {
        val now = nowNanos / 1_000_000_000.0
        if (!active) {
            if (pausedAt.isNaN()) {
                pausedAt = now
            }
            return
        }
        if (!pausedAt.isNaN()) {
            val pause = (now - pausedAt).coerceAtLeast(0.0)
            if (!autoAnchor.isNaN()) {
                autoAnchor += pause
            }
            if (touchedAt.isFinite()) {
                touchedAt += pause
            }
            if (visitStartedAt.isFinite()) {
                visitStartedAt += pause
            }
            if (tapStartedAt.isFinite()) {
                tapStartedAt += pause
            }
            if (pressStartedAt.isFinite()) {
                pressStartedAt += pause
            }
            if (pressReleasedAt.isFinite()) {
                pressReleasedAt += pause
            }
            if (grabStartedAt.isFinite()) {
                grabStartedAt += pause
            }
            if (grabReleasedAt.isFinite()) {
                grabReleasedAt += pause
            }
            sheetTransition = sheetTransition?.let { transition ->
                transition.copy(startedAt = transition.startedAt + pause)
            }
            previousTime = Double.NaN
            pausedAt = Double.NaN
        }
    }

    fun tick(nowNanos: Long): Boolean {
        val now = nowNanos / 1_000_000_000.0
        if (autoAnchor.isNaN()) {
            autoAnchor = now
        }
        val before = frame
        val delta = if (previousTime.isNaN()) 0.0 else (now - previousTime).coerceIn(0.0, 0.25)
        previousTime = now

        if (mode == Mode.AUTO && !loops) {
            mode = Mode.TOUCH
        }
        if (mode == Mode.AUTO) {
            frame = SavedPlacesMapMotion.sample(now - autoAnchor)
            sheet = frame.sheet
            cluster = frame.cluster
            return frame != before
        }

        if (!dragging) {
            updateSheetTransition(now)
        }
        val targetCluster = SavedPlacesMapMotion.clamp01(sheet)
        cluster = if (reducedMotion) {
            targetCluster
        } else {
            val step = delta / SavedPlacesMapMotion.Cluster.dur
            when {
                abs(targetCluster - cluster) <= step -> targetCluster
                else -> cluster + sign(targetCluster - cluster) * step
            }
        }

        val visitTau = now - visitStartedAt
        val visit = SavedPlacesMapMotion.visitFrame(visitDirection, visitTau, reducedMotion)
        val tapPop = if (reducedMotion) 1.0 else SavedPlacesMapMotion.popScale(now - tapStartedAt)
        frame = SavedPlacesMapMotion.Frame(
            sheet = sheet,
            cluster = cluster,
            fill = visit.fill,
            draw = visit.draw,
            roll = visit.roll,
            badge = visit.badge,
            visitPop = visit.pop,
            tapIndex = tapIndex,
            tapPop = tapPop,
            press = pressValue(now),
            grab = grabValue(now),
            pressTarget = pressTarget,
        )

        if (mode == Mode.TOUCH && loops && !armed && now - touchedAt >= SavedPlacesMapMotion.RESUME_AFTER) {
            mode = Mode.RETURN
            beginSheetTransition(0.0, 0.0, now, SavedPlacesMapMotion.Springs.sheetFall, timing = false)
            if (visitDirection == 1) {
                visitDirection = 0
                visitStartedAt = now
            }
        } else if (
            mode == Mode.RETURN &&
            abs(sheet) < 0.001 &&
            cluster == 0.0 &&
            visitDirection == 0 &&
            now - visitStartedAt > 0.6
        ) {
            sheet = 0.0
            cluster = 0.0
            sheetTransition = null
            autoAnchor = now
            mode = if (loops) Mode.AUTO else Mode.TOUCH
            frame = SavedPlacesMapMotion.REST
        }
        return frame != before
    }

    /** Zero means frame cadence, positive means sleep to the next beat, null means event-only. */
    fun nextWakeDelayMillis(nowNanos: Long): Long? {
        val now = nowNanos / 1_000_000_000.0
        if (autoAnchor.isNaN()) {
            return 0L
        }
        if (mode == Mode.AUTO) {
            val loopTime = ((now - autoAnchor) % SavedPlacesMapMotion.LOOP + SavedPlacesMapMotion.LOOP) % SavedPlacesMapMotion.LOOP
            return when {
                loopTime < SavedPlacesMapMotion.T.rise - SavedPlacesMapMotion.Grab.lead -> {
                    ((SavedPlacesMapMotion.T.rise - SavedPlacesMapMotion.Grab.lead - loopTime) * 1_000).toLong()
                }
                loopTime < SavedPlacesMapMotion.T.fall + 1.6 -> 0L
                else -> ((SavedPlacesMapMotion.LOOP - loopTime + SavedPlacesMapMotion.T.rise - SavedPlacesMapMotion.Grab.lead) * 1_000).toLong()
            }
        }
        if (mode == Mode.RETURN || dragging || sheetTransition != null) {
            return 0L
        }
        if (abs(cluster - SavedPlacesMapMotion.clamp01(sheet)) > 0.00001) {
            return 0L
        }
        if (visitStartedAt.isFinite() && now - visitStartedAt < 1.2) {
            return 0L
        }
        if (tapStartedAt.isFinite() && now - tapStartedAt < 1.2) {
            return 0L
        }
        if (pressStartedAt.isFinite() && now - pressStartedAt < SavedPlacesMapMotion.Press.inDuration) {
            return 0L
        }
        if (pressReleasedAt.isFinite() && now - pressReleasedAt < 1.0) {
            return 0L
        }
        if (grabStartedAt.isFinite() || (grabReleasedAt.isFinite() && now - grabReleasedAt < 0.3)) {
            return 0L
        }
        if (armed) {
            // A stationary hold is event-driven; release/move will wake the conflated channel.
            return null
        }
        if (loops) {
            return ((touchedAt + SavedPlacesMapMotion.RESUME_AFTER - now).coerceAtLeast(0.0) * 1_000).toLong()
        }
        return null
    }

    fun onDown(x: Float, y: Float, unit: Float, nowNanos: Long) {
        val now = nowNanos / 1_000_000_000.0
        tick(nowNanos)
        downX = x
        downY = y
        val sheetTop = (SavedPlacesMapTheme.Sheet.top - SavedPlacesMapMotion.RISE * 3f * sheet.toFloat()) * unit
        armed = y >= sheetTop
        if (armed) {
            // RN's pan onBegin takes ownership anywhere on the sheet, before drag slop is crossed.
            enterTouch(now)
        }
        val hit = hit(x, y, unit)
        if (hit == HIT_CHECKBOX || hit == HIT_TILE) {
            pressTarget = hit
            pressStartedAt = now
            pressReleasedAt = Double.NEGATIVE_INFINITY
        }
    }

    fun onMove(x: Float, y: Float, unit: Float, density: Float, nowNanos: Long): Boolean {
        if (!armed) {
            return false
        }
        val translationY = y - downY
        if (!dragging && abs(translationY) < SavedPlacesMapMotion.Drag.slop * density) {
            return false
        }
        val now = nowNanos / 1_000_000_000.0
        if (!dragging) {
            enterTouch(now)
            dragging = true
            dragStartSheet = sheet
            sheetTransition = null
            grabStartedAt = now
            grabReleasedAt = Double.NEGATIVE_INFINITY
            if (pressStartedAt.isFinite()) {
                // Pan wins Gesture.Race: RN finalizes the tap recognizer at this exact hand-off.
                pressReleaseValue = pressValue(now)
                pressReleasedAt = now
                pressStartedAt = Double.NEGATIVE_INFINITY
            }
        }
        val raw = dragStartSheet - translationY / (SavedPlacesMapMotion.RISE * 3f * unit)
        sheet = SavedPlacesMapMotion.band(raw.toDouble())
        touchedAt = now
        return true
    }

    fun onUp(x: Float, y: Float, velocityY: Float, unit: Float, density: Float, nowNanos: Long): Int {
        val now = nowNanos / 1_000_000_000.0
        var haptic = HAPTIC_NONE
        if (dragging) {
            val normalizedVelocity = -velocityY / (SavedPlacesMapMotion.RISE * 3f * unit)
            val target = if (sheet + normalizedVelocity * SavedPlacesMapMotion.Drag.project > 0.5) 1.0 else 0.0
            val previousDetent = if (SavedPlacesMapMotion.clamp01(dragStartSheet) >= 0.5) 1 else 0
            beginSheetTransition(
                target = target,
                velocity = normalizedVelocity.toDouble(),
                now = now,
                spring = SavedPlacesMapMotion.Springs.sheetRise,
                timing = reducedMotion,
            )
            if (target.toInt() != previousDetent) {
                haptic = HAPTIC_DETENT
            }
        } else if (hypot((x - downX).toDouble(), (y - downY).toDouble()) <= 10f * density) {
            enterTouch(now)
            val hit = hit(x, y, unit)
            if (hit == HIT_CHECKBOX || hit == HIT_TILE) {
                if (now - visitStartedAt >= SavedPlacesMapMotion.TOGGLE_GAP) {
                    visitDirection = if (visitDirection == 1) 0 else 1
                    visitStartedAt = now
                    haptic = if (visitDirection == 1) HAPTIC_VISIT_ON else HAPTIC_SELECT
                }
            } else if (hit >= HIT_MARKER) {
                tapIndex = hit - HIT_MARKER
                tapStartedAt = now
                haptic = HAPTIC_SELECT
            }
        }

        if (pressStartedAt.isFinite()) {
            pressReleaseValue = pressValue(now)
            pressReleasedAt = now
            pressStartedAt = Double.NEGATIVE_INFINITY
        }
        if (grabStartedAt.isFinite()) {
            grabReleaseValue = grabValue(now)
            grabReleasedAt = now
            grabStartedAt = Double.NEGATIVE_INFINITY
        }
        dragging = false
        armed = false
        touchedAt = now
        return haptic
    }

    fun onCancel(nowNanos: Long) {
        val now = nowNanos / 1_000_000_000.0
        if (dragging) {
            beginSheetTransition(
                target = SavedPlacesMapMotion.clamp01(sheet).let { if (it > 0.5) 1.0 else 0.0 },
                velocity = 0.0,
                now = now,
                spring = SavedPlacesMapMotion.Springs.sheetRise,
                timing = reducedMotion,
            )
        }
        if (pressStartedAt.isFinite()) {
            pressReleaseValue = pressValue(now)
            pressReleasedAt = now
            pressStartedAt = Double.NEGATIVE_INFINITY
        }
        if (grabStartedAt.isFinite()) {
            grabReleaseValue = grabValue(now)
            grabReleasedAt = now
            grabStartedAt = Double.NEGATIVE_INFINITY
        }
        dragging = false
        armed = false
        touchedAt = now
    }

    fun setSheetDetent(open: Boolean, nowNanos: Long) {
        val now = nowNanos / 1_000_000_000.0
        enterTouch(now)
        beginSheetTransition(
            target = if (open) 1.0 else 0.0,
            velocity = 0.0,
            now = now,
            spring = SavedPlacesMapMotion.Springs.sheetRise,
            timing = reducedMotion,
        )
        touchedAt = now
    }

    fun toggleVisited(nowNanos: Long): Int {
        val now = nowNanos / 1_000_000_000.0
        enterTouch(now)
        if (now - visitStartedAt < SavedPlacesMapMotion.TOGGLE_GAP) {
            return HAPTIC_NONE
        }
        visitDirection = if (visitDirection == 1) 0 else 1
        visitStartedAt = now
        touchedAt = now
        return if (visitDirection == 1) HAPTIC_VISIT_ON else HAPTIC_SELECT
    }

    fun selectMarker(index: Int, nowNanos: Long): Int {
        if (index !in SavedPlacesMapTheme.markers.indices) {
            return HAPTIC_NONE
        }
        val now = nowNanos / 1_000_000_000.0
        enterTouch(now)
        tapIndex = index
        tapStartedAt = now
        touchedAt = now
        return HAPTIC_SELECT
    }

    private fun enterTouch(now: Double) {
        if (mode == Mode.AUTO) {
            val loopTime = ((now - autoAnchor) % SavedPlacesMapMotion.LOOP + SavedPlacesMapMotion.LOOP) % SavedPlacesMapMotion.LOOP
            if (loopTime >= SavedPlacesMapMotion.T.check && loopTime < SavedPlacesMapMotion.T.uncheck) {
                visitDirection = 1
                visitStartedAt = now - (loopTime - SavedPlacesMapMotion.T.check)
            } else if (loopTime >= SavedPlacesMapMotion.T.uncheck) {
                visitDirection = 0
                visitStartedAt = now - (loopTime - SavedPlacesMapMotion.T.uncheck)
            } else {
                visitDirection = 0
                visitStartedAt = Double.NEGATIVE_INFINITY
            }
            sheet = frame.sheet
            cluster = frame.cluster
            val target = if (sheet > 0.5) 1.0 else 0.0
            if (abs(sheet - target) > 0.001) {
                beginSheetTransition(target, 0.0, now, SavedPlacesMapMotion.Springs.sheetRise, timing = reducedMotion)
            }
        }
        mode = Mode.TOUCH
        touchedAt = now
    }

    private fun beginSheetTransition(
        target: Double,
        velocity: Double,
        now: Double,
        spring: SavedPlacesMapMotion.Spring,
        timing: Boolean,
    ) {
        sheetTransition = SheetTransition(sheet, target, velocity, now, spring, timing)
    }

    private fun updateSheetTransition(now: Double) {
        val transition = sheetTransition ?: return
        val tau = now - transition.startedAt
        if (transition.timing) {
            val progress = SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.seg(tau, 0.0, SavedPlacesMapMotion.Visit.reducedFade),
            )
            sheet = SavedPlacesMapMotion.lerp(transition.start, transition.target, progress)
            if (tau >= SavedPlacesMapMotion.Visit.reducedFade) {
                sheet = transition.target
                sheetTransition = null
            }
            return
        }
        sheet = SavedPlacesMapMotion.springValue(
            tau = tau,
            start = transition.start,
            target = transition.target,
            velocity = transition.velocity,
            spring = transition.spring,
        )
        if (tau > 1.5 || (abs(sheet - transition.target) < 0.0005 && tau > 0.35)) {
            sheet = transition.target
            sheetTransition = null
        }
    }

    private fun pressValue(now: Double): Double {
        if (pressStartedAt.isFinite()) {
            return SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.seg(now, pressStartedAt, pressStartedAt + SavedPlacesMapMotion.Press.inDuration),
            )
        }
        if (pressReleasedAt.isFinite()) {
            return SavedPlacesMapMotion.springValue(
                tau = now - pressReleasedAt,
                start = pressReleaseValue,
                target = 0.0,
                velocity = 0.0,
                spring = SavedPlacesMapMotion.Springs.press,
            ).coerceAtLeast(0.0)
        }
        return 0.0
    }

    private fun grabValue(now: Double): Double {
        if (grabStartedAt.isFinite()) {
            return SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.seg(now, grabStartedAt, grabStartedAt + SavedPlacesMapMotion.Grab.inDuration),
            )
        }
        if (grabReleasedAt.isFinite()) {
            val progress = SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.seg(now, grabReleasedAt, grabReleasedAt + SavedPlacesMapMotion.Grab.outDuration),
            )
            return SavedPlacesMapMotion.lerp(grabReleaseValue, 0.0, progress)
        }
        return 0.0
    }

    private fun hit(x: Float, y: Float, unit: Float): Int {
        val referenceX = x / unit
        val referenceY = y / unit
        val offset = SavedPlacesMapMotion.RISE * 3f * sheet.toFloat()
        val slop = 66f
        val checkboxCenterX = SavedPlacesMapTheme.Layout.checkboxX + SavedPlacesMapTheme.Layout.checkboxD / 2f
        val checkboxCenterY = SavedPlacesMapTheme.Layout.checkboxY + SavedPlacesMapTheme.Layout.checkboxD / 2f - offset
        if (abs(referenceX - checkboxCenterX) <= slop && abs(referenceY - checkboxCenterY) <= slop) {
            return HIT_CHECKBOX
        }
        if (
            referenceX >= SavedPlacesMapTheme.Tiles.xs[0] &&
            referenceX <= SavedPlacesMapTheme.Tiles.xs[0] + SavedPlacesMapTheme.Tiles.w &&
            referenceY >= SavedPlacesMapTheme.Tiles.y - offset &&
            referenceY <= SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h - offset
        ) {
            return HIT_TILE
        }
        if (referenceY < SavedPlacesMapTheme.Sheet.top - offset) {
            // The marker where it is drawn: on its ground spot, lifted and scaled while the map is pitched.
            val tilt = if (reducedMotion) 0.0 else SavedPlacesMapMotion.tiltOf(sheet)
            for (index in SavedPlacesMapTheme.markers.indices.reversed()) {
                val marker = SavedPlacesMapTheme.markers[index]
                val disc = discOf(marker, tilt)
                val right = disc.x + (SavedPlacesMapTheme.MarkerToken.discDx + SavedPlacesMapTheme.MarkerToken.hitW) * disc.m
                if (
                    referenceX >= disc.x - slop &&
                    referenceX <= right &&
                    abs(referenceY - disc.y) <= slop * max(1.0, disc.m)
                ) {
                    return HIT_MARKER + index
                }
            }
        }
        return HIT_NONE
    }

    private fun discOf(marker: SavedPlacesMapTheme.Marker, tilt: Double): SavedPlacesMapMotion.Disc {
        val cx = (marker.x - SavedPlacesMapTheme.MarkerToken.discDx).toDouble()
        val cy = (marker.baseline - SavedPlacesMapTheme.MarkerToken.discDy).toDouble()
        val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
        return SavedPlacesMapMotion.discAt(cx, cy, tilt, rank, SavedPlacesMapTheme.MarkerToken.disc / 2.0)
    }
}
