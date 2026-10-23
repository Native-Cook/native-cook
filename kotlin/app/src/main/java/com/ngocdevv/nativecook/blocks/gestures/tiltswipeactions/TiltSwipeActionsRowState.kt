package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

/** swipe-haptics.ts SwipeHaptic: discrete tactile events only, never played while the demo runs. */
internal enum class SwipeHaptic { Open, Arm, Disarm, Undo, Tap, Commit }

/**
 * The state and the behaviour of one SwipeRow.tsx row: the finger and the demo script call the
 * same begin -> move -> end, so a scripted swipe is the same code path as a real one.
 */
internal class TiltRowState(
    val key: String,
    private val restoring: Boolean,
    private val width: Float,
    private val reduceMotion: Boolean,
    clock: MotionClock,
    private val setActiveKey: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val playHaptic: (SwipeHaptic) -> Unit,
) {
    val x = MotionValue(if (restoring) -width * 1.15f else 0f, clock)
    val height = MotionValue(if (restoring) 0f else 1f, clock)
    val tilt = MotionValue(0f, clock)
    val lift = MotionValue(0f, clock)

    /** Delete has taken the whole strip. */
    val full = MotionValue(0f, clock)

    /** Delete tile colour, white -> pink. */
    val red = MotionValue(0f, clock)

    /** Hides the actions while a row slides back in. */
    val strip = MotionValue(if (restoring) 0f else 1f, clock)
    val fade = MotionValue(if (restoring && reduceMotion) 0f else 1f, clock)

    private var start = 0f
    var dragging = false
        private set
    var removing = false
        private set
    private var opened = false

    private val fullLine = width * TiltSwipe.fullAt
    private val limit = width * 0.85f

    /** The effect of SwipeRow.tsx that runs once a restored row is on screen. */
    fun playRestore() {
        if (!restoring) return
        if (reduceMotion) {
            height.set(1f)
            x.set(0f)
            strip.set(1f)
            fade.timing(1f, TiltTiming.reduced)
            return
        }
        // Undo: the gap opens, then the row slides back in from where it left.
        height.spring(1f, TiltSprings.restore)
        x.spring(0f, TiltSprings.restore, delaySeconds = 0.12f) { done -> if (done) strip.set(1f) }
    }

    private fun haptic(kind: SwipeHaptic, silent: Boolean) {
        if (!silent) playHaptic(kind)
    }

    private fun snap(target: Float, velocity: Float) {
        if (reduceMotion) {
            x.timing(target, TiltTiming.reduced)
        } else {
            x.spring(target, TiltSprings.snap, velocity)
        }
    }

    private fun setFull(on: Boolean, silent: Boolean) {
        if ((full.value > 0.5f) == on) return
        val target = if (on) 1f else 0f
        if (reduceMotion) {
            full.timing(target, TiltTiming.reduced)
        } else {
            full.spring(target, TiltSprings.tileStretch)
        }
        red.timing(target, TiltTiming.colour)
        haptic(if (on) SwipeHaptic.Arm else SwipeHaptic.Disarm, silent)
    }

    private fun close(velocity: Float = 0f) {
        opened = false
        snap(0f, velocity)
        setFull(false, true)
    }

    private fun collapse() {
        val done = { finished: Boolean -> if (finished) onDelete(key) }
        if (reduceMotion) {
            height.timing(0f, TiltTiming.reducedGap, onEnd = done)
        } else {
            height.spring(0f, TiltSprings.collapse, onEnd = done)
        }
    }

    /** A full swipe, the Delete tile and the accessibility action all end up here. */
    private fun remove() {
        removing = true
        dragging = false
        lift.spring(0f, TiltSprings.lift)
        if (reduceMotion) {
            fade.timing(0f, TiltTiming.reduced) { finished -> if (finished) collapse() }
            return
        }
        // Keep leaning into the throw while it leaves.
        tilt.spring(TiltLean.max, TiltSprings.follow)
        x.timing(-width * 1.15f, TiltTiming.flyOut, EASE_OUT) { finished ->
            if (finished) {
                strip.set(0f)
                collapse()
            }
        }
    }

    fun begin() {
        if (removing) return
        x.cancelAnimation()
        start = x.value
        dragging = true
        setActiveKey(key)
        if (!reduceMotion) lift.spring(1f, TiltSprings.lift)
    }

    /** [translation] is the finger's travel since it went down, [velocity] its speed (dp, dp/s). */
    fun move(translation: Float, velocity: Float, silent: Boolean) {
        if (!dragging) return
        val raw = start + translation
        var next = raw
        if (raw > 0f) {
            next = raw * TiltSwipe.rightRubber
        } else if (raw < -limit) {
            next = -limit + (raw + limit) * TiltSwipe.fullRubber
        }
        val wasOpen = -x.value >= TiltSwipe.openAt
        x.set(next)
        if (!reduceMotion) {
            val lean = (-velocity * TiltLean.perVelocity).coerceIn(-TiltLean.max, TiltLean.max)
            tilt.spring(lean, TiltSprings.follow)
        }
        if (!wasOpen && -next >= TiltSwipe.openAt) haptic(SwipeHaptic.Open, silent)
        setFull(-next > fullLine, silent)
    }

    fun end(velocity: Float) {
        if (!dragging) return
        dragging = false
        lift.spring(0f, TiltSprings.lift)
        tilt.spring(0f, TiltSprings.follow)
        val uncovered = -x.value
        if (full.value > 0.5f || (velocity < TiltSwipe.deleteFlick && uncovered > width * TiltSwipe.deleteFlickAt)) {
            remove()
        } else if ((uncovered > TiltSwipe.openAt || velocity < TiltSwipe.openFlick) && velocity < 300f) {
            opened = true
            snap(OPEN_X, velocity)
        } else {
            close(velocity)
        }
    }

    /** Gesture.Tap().onEnd: a tap on an open row closes it. */
    fun tap() {
        if (opened) close()
    }

    /** Buttons and accessibility take the same path as a full swipe. */
    fun removeNow() {
        if (!removing) remove()
    }

    /** Another row took the touch: close this one. */
    fun onActiveKey(owner: String) {
        if (owner != key && !dragging && !removing && x.value != 0f) close()
    }

    /** The demo's finger as state: begin when it goes down, end when it lifts. */
    fun scriptTouch(down: Boolean, translation: Float, velocity: Float) {
        if (down) {
            if (!dragging) begin()
            move(translation, velocity, true)
        } else if (dragging) {
            move(translation, velocity, true)
            end(velocity)
        }
    }
}
