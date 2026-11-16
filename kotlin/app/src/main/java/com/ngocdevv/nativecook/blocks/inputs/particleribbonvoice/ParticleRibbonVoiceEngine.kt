package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceMotion as M
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Cues the host turns into haptics and sound (touch only; the loop is silent). */
internal object ParticleRibbonVoiceBeat {
    const val ONSET = 0 // a finger lands to talk: the twist leaves the left edge: light (+ the opening tone)
    const val RELEASE = 1 // the finger leaves after a real press: selection (+ the closing tone)
    const val PART = 2 // a finger takes hold of the ribbon: light
    const val CROSS = 3 // a crossing passes under a second finger: selection
    const val ANSWER = 4 // the reply's first word appears: success
}

/**
 * The frame loop of the RN block (`ParticleRibbonVoice.tsx`): the loop plays `frame` (a pure function of the loop time) with its caption layers and the
 * request's words; a finger hands everything to the live engine (the talk, the parting windows, the twists, the thread, the reply and its words), and two
 * seconds after the last touch the live frame blends back to the loop's rest in 0.6 s. Main thread, one `frame` per vsync. Not ported from the RN props:
 * controlled `state`, `level`, `mode = tapToToggle`, `touchable`, `colors`, `width`, `centerY`, `twistSpeed`, `quality`, `bloom`, `background`, `font`,
 * `paused`, `renderCaption`.
 */
internal class ParticleRibbonVoiceEngine(val autoPlay: Boolean, val reduce: Boolean, val s: Double, val yc0: Double, val demoFlow: Boolean = true) {
    var cue: (Int) -> Unit = {}
    var onPress: (Boolean) -> Unit = {}
    var onMode: (Int) -> Unit = {}

    var frame: PrvFrame = if (reduce) M.reducedFrame(0, 0.0, 1.0) else M.frameAt(0.0)
        private set
    var cap: DoubleArray = M.captionAt(0.0)
        private set
    var ask: DoubleArray = M.askAt(0.0)
        private set
    private val live = M.liveRest()
    private var loopT = 0.0
    private var playing = autoPlay && !reduce
    private var resuming = -1.0
    private var resumeFrom: PrvFrame = frame
    var mode = 0
        private set
    private var words = 0
    private var rLevel = 0.0
    var clock = 0.0
        private set
    private var cross = 0
    private var frames = 0

    private fun setMode(m: Int) {
        mode = m
        onMode(m)
    }

    private fun takeOver() {
        resuming = -1.0
        if (playing) {
            playing = false
            M.liveFromLoop(live, loopT)
        }
    }

    // ---- the frame ----

    fun frame(dtRaw: Double) {
        val dt = min(dtRaw, 0.05)
        clock += dt
        if (playing && !reduce) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            frame = M.frameAt(t)
            cap = M.captionAt(t)
            ask = M.askAt(t)
            val m = M.stateAt(t)
            if (mode != m) setMode(m)
            return
        }
        // the live engine
        M.liveStep(live, dt, demoFlow, -1.0, M.SPEED)
        val sLive = live
        // live: no transcript (the words are the host's), the control follows the finger or the listening state
        val next = DoubleArray(M.ASK_LAYERS)
        val held = if (sLive.talk > 0 || sLive.mode == 1) 1.0 else 0.0
        val ease = 1 - exp(-dt / 0.06)
        for (i in 0 until M.MAX_WORDS) next[i] = ask[i] * (1 - ease)
        next[M.MAX_WORDS] = ask[M.MAX_WORDS] + (held - ask[M.MAX_WORDS]) * ease
        ask = next
        if (mode != sLive.mode) setMode(sLive.mode)
        if (words != sLive.words) {
            val prev = words
            words = sLive.words
            if (words == 1 && prev == 0 && !playing && !reduce) cue(ParticleRibbonVoiceBeat.ANSWER)
        }
        if (cross != sLive.cross) {
            if (sLive.cross > cross) cue(ParticleRibbonVoiceBeat.CROSS)
            cross = sLive.cross
        }
        // under Reduce Motion: a still band, the level eased over 200 ms, no twists
        if (reduce) {
            rLevel += (sLive.level - rLevel) * (1 - exp(-dt / 0.07))
            frame = M.reducedFrame(sLive.mode, rLevel, 0.5 + 0.5 * sin((2 * PI * clock) / 2.4))
            cap = sLive.cap.copyOf()
            return
        }
        // idle for 2 s after the last touch: ease back to the loop's rest, then play it again
        if (autoPlay && sLive.mode == 0 && sLive.quiet >= M.RESUME_AFTER && sLive.talk == 0) {
            if (resuming < 0) {
                resumeFrom = M.liveFrame(sLive)
                resuming = 0.0
            } else {
                val nxt = resuming + dt / M.RESUME_EASE
                if (nxt >= 1) {
                    resuming = -1.0
                    loopT = 0.0
                    playing = true
                    return
                }
                resuming = nxt
                val k = M.smooth(0.0, 1.0, nxt)
                frame = M.blendFrame(resumeFrom, M.frameAt(0.0), k)
                val c0 = M.captionAt(0.0)
                val cc = c0.copyOf()
                for (i in 0 until M.CAPTION_LAYERS) cc[i] = sLive.cap[i] + (c0[i] - sLive.cap[i]) * k
                cap = cc
                return
            }
        } else if (resuming >= 0) {
            resuming = -1.0
        }
        frames += 1
        val asleep = sLive.mode == 0 && sLive.quiet > 4 && sLive.talk == 0 && kotlin.math.abs(sLive.level) < 0.01
        if (asleep && frames % 2 == 1) return // idle for 4 s: 30 fps
        frame = M.liveFrame(sLive)
        cap = sLive.cap.copyOf()
    }

    /** where each reply word's tint is taken from (design pt x of its centre), set by the renderer */
    val wordX = DoubleArray(M.MAX_WORDS) { 201.0 }

    // ---- the fingers (px of the screen / the strip's design pt) ----

    /** `x`, `y` in dp. */
    fun down(id: Int, x: Double, y: Double) {
        takeOver()
        val dx = x / s
        val dy = (y - yc0) / s
        val f = frame
        val c = M.lineY(dx, f)
        val tw = M.twistBase(dx, f)
        val parts = !reduce
        val kind = M.liveDown(live, id, dx, dy, c, parts, tw)
        if (kind == 1) {
            cue(ParticleRibbonVoiceBeat.ONSET)
            onPress(true)
        } else if (kind == 2) {
            cue(ParticleRibbonVoiceBeat.PART)
        }
    }

    fun move(id: Int, x: Double, y: Double) {
        val dx = x / s
        val dy = (y - yc0) / s
        val c = M.lineY(dx, frame)
        M.liveMove(live, id, dx, dy, c, 0.0)
    }

    fun up(id: Int) {
        val wasTalk = if (live.talk == id + 1) 10 else 0
        val r = wasTalk + M.liveUp(live, id, demoFlow)
        if (r >= 10) onPress(false)
        if (r == 11) cue(ParticleRibbonVoiceBeat.RELEASE)
    }

    /** TalkBack: toggle listening. */
    fun toggleListening() {
        playing = false
        resuming = -1.0
        if (live.mode == 1) {
            M.liveMode(live, if (demoFlow) 2 else 0)
        } else {
            live.talkLevel = 0.6
            live.talk = 0
            M.liveMode(live, 1)
        }
    }

}
