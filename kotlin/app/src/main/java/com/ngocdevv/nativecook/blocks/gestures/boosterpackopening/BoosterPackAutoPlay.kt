package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

/** The period of the self-playing loop, in seconds. */
private const val LOOP = 15f

private const val TEAR_START = 1.0
private const val TEAR_END = 2.1
private val ADVANCE_TIMES = doubleArrayOf(5.2, 6.3, 7.4, 10.4)
private const val REPLAY_TIME = 13.6
private const val TOUCH_REST_MS = 2500.0

// The reference finger crosses the pack between these two frames of the reference video.
private const val TRACE_FIRST_FRAME = 13.0
private const val TRACE_LAST_FRAME = 90.0

/**
 * Plays the block by itself in a loop of [LOOP] seconds, through the entry points a finger uses:
 * a tear along the reference trace, the opening, four taps on the stack, and "Open another pack".
 * Every step is timed against the moment the loop began, so the period is exact. A touch stops
 * the script; [TOUCH_REST_MS] after the last touch the pack is sealed again and the loop goes on.
 */
internal class PackAutoPlay(
    private val scene: PackScene,
) {
    private var loopStart = Double.NaN
    private var running = true
    private var begun = false
    private var crossed = false
    private var advances = 0
    private var replayed = false
    private var touching = false
    private var resumeAt = Double.NaN

    init {
        scene.scripted = true
    }

    /** Where the scripted finger is, [fraction] of the way through its tear. */
    private fun fingerAt(fraction: Double): Point {
        val pack = scene.pack
        val frame = TRACE_FIRST_FRAME + (TRACE_LAST_FRAME - TRACE_FIRST_FRAME) * fraction
        val progress = traceProgressAtFrame(frame)
        val trace = referenceTracePoints(progress, scene.width, scene.height)
        val head = trace[trace.size - 1]
        val startX = ReferenceLayout.Trace.START_X * scene.width
        val endX = ReferenceLayout.Trace.END_X * scene.width
        // The reference trace stops short of the pack's edges; stretch it over the whole width.
        val across = (head.x - startX) / (endX - startX)
        return Point(
            // The last position lies past the far edge, which commits the cut.
            if (fraction >= 1) pack.x + pack.width + scene.width * 0.02 else pack.x + pack.width * across,
            clamp(head.y, pack.y + pack.height * 0.09, pack.y + pack.height * 0.26),
        )
    }

    private fun sealAndResume(nowMs: Double) {
        scene.reset()
        // The loop goes on from its own "Open another pack" moment, so its start is unchanged.
        loopStart = nowMs - REPLAY_TIME * 1000
        begun = true
        crossed = true
        advances = ADVANCE_TIMES.size
        replayed = true
        running = true
        scene.scripted = true
    }

    fun touchDown() {
        touching = true
        resumeAt = Double.NaN
        if (!running) return
        running = false
        scene.scripted = false
        // A scripted tear that is under way is released, as lifting the finger would.
        if (begun && !crossed) scene.cancelTear()
    }

    fun touchUp(nowMs: Double) {
        touching = false
        resumeAt = nowMs + TOUCH_REST_MS
    }

    /** The app came back to the front: the loop starts over from a sealed pack. */
    fun restart() {
        if (touching) return
        running = false
        resumeAt = 0.0
    }

    /** One frame of the script. Runs after the scene's clocks have advanced to [nowMs]. */
    fun frame(nowMs: Double) {
        if (loopStart.isNaN()) {
            loopStart = nowMs
            // The first loop starts as every later one does: with the idle light of a pack that
            // was sealed when the previous loop pressed "Open another pack".
            scene.primeIdle((LOOP - REPLAY_TIME) * 1000)
        }
        if (!running) {
            if (touching || resumeAt.isNaN() || nowMs < resumeAt) return
            sealAndResume(nowMs)
        }

        var elapsed = (nowMs - loopStart) / 1000
        if (elapsed >= LOOP) {
            loopStart += LOOP * 1000.0
            elapsed -= LOOP
            begun = false
            crossed = false
            advances = 0
            replayed = false
        }

        if (!begun && elapsed >= TEAR_START) {
            begun = true
            scene.beginCut(fingerAt(0.0))
        }
        if (begun && !crossed) {
            val fraction = clamp((elapsed - TEAR_START) / (TEAR_END - TEAR_START), 0.0, 1.0)
            scene.updateCut(fingerAt(fraction))
            crossed = fraction >= 1
        }
        if (advances < ADVANCE_TIMES.size && elapsed >= ADVANCE_TIMES[advances]) {
            advances += 1
            scene.cards.advance()
        }
        if (!replayed && elapsed >= REPLAY_TIME) {
            replayed = true
            scene.reset(lateMs = (elapsed - REPLAY_TIME) * 1000)
        }
    }
}
