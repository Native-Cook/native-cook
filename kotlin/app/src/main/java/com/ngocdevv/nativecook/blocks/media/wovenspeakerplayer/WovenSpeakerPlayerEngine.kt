package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerMotion as M
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/** Cues the host turns into haptics (touch only; autoplay is silent). */
internal object WovenSpeakerPlayerBeat {
    const val ONSET = 0 // finger lands on the mesh: light
    const val FIRST_NOTE = 1 // play: the first ring leaves the display: medium
    const val TICK = 2 // every 5 s of track crossed while turning: selection
    const val LIMIT = 3 // start or end of the track: rigid
    const val SETTLE = 4 // the position snaps to the second after a coast: light
    const val PAUSE = 5 // pause: the last ring reaches the rim: soft
    const val TRACK = 6 // track change: light
}

/**
 * The controller of the RN block (`WovenSpeaker.tsx`): the timeline plays `look`; a finger hands it to the live values (position, play / pause glyph,
 * bloom, lamp, the three text lines, the layers' angles, the dent, the pulses...). The dome is the jog wheel (the track scrubs 1:1, the weave turns 0.22 of
 * it, the layers shear, the coast carries the finger's speed), the centre and the two discs tap, the headline swipes the queue, the pill opens it.
 * Main thread, one `frame` per vsync. Not ported from the RN props: `position`, `level`, `beats`, `turnSeconds`, `strands`, `children`, controlled `playing` /
 * `index`, `onSeek` throttling beyond what is here, the `coast` override.
 */
internal class WovenSpeakerPlayerEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val queueSize: Int,
    val duration: Double = M.TRACK,
    val ambient: Boolean = true,
    val haptics: Boolean = true,
    val s: Double,
) {
    var cue: (Int) -> Unit = {}
    var emitPlaying: (Boolean) -> Unit = {}
    var emitSeek: (Double) -> Unit = {}
    var emitCommit: (Int) -> Unit = {}
    var emitIndex: (Int) -> Unit = {}
    var emitPress: (Boolean) -> Unit = {}
    var emitOpen: () -> Unit = {}

    private class Grip(
        var region: Int = 0, // 0 none, 1 mesh, 2 display, 3 outside
        var sub: Int = 0, // display: 1 centre, 2 previous, 3 next
        var a0: Double = 0.0,
        var prev: Double = 0.0,
        var acc: Double = 0.0,
        var turning: Boolean = false,
        var v: Double = 0.0,
        var vAt: Double = 0.0,
        var tickAt: Double = -9.0,
        var tickN: Int = 0,
        var atLimit: Boolean = false,
        var byTouchPlay: Boolean = false,
    )

    var look = WovenSpeakerPlayerLook()
        private set
    private var clock = 0.0
    private var loopT = 0.0
    private var auto = autoPlay && !reduce
    private val posL = WovenSpeakerPlayerAnim(0.0)
    private var xL = 0.0
    private var playingL = false
    private val glyphL = WovenSpeakerPlayerAnim(0.0)
    private val bloomL = WovenSpeakerPlayerAnim(0.0)
    private val lampL = WovenSpeakerPlayerAnim(0.0)
    private val titleL = WovenSpeakerPlayerAnim(0.0)
    private val lineL = WovenSpeakerPlayerAnim(0.0)
    private val pillL = WovenSpeakerPlayerAnim(0.0)
    private val reflL = WovenSpeakerPlayerAnim(0.0)
    private val handleL = WovenSpeakerPlayerAnim(0.0)
    private val pressL = WovenSpeakerPlayerAnim(1.0)
    private val turnL = WovenSpeakerPlayerAnim(0.0)
    private val psiOL = WovenSpeakerPlayerAnim(0.0)
    private val shearDL = WovenSpeakerPlayerAnim(0.0)
    private var prevPsi = 0.0
    private var prevShear = 0.0
    private var fingerL = 0.0
    private var dentXL = 0.0
    private var dentYL = 0.0
    private val dentL = WovenSpeakerPlayerAnim(0.0)
    private val pullL = WovenSpeakerPlayerAnim(0.0)
    private var pullAtL = 0.0
    private val headL = WovenSpeakerPlayerAnim(0.0)
    private var rings = doubleArrayOf(-9.0, 0.0, -9.0, 0.0, -9.0, 0.0, -9.0, 0.0)
    private var strongAt = -9.0
    private var playClock = 0.0
    private var nextBeat = 0.12
    private var beatN = 0
    val slideL = WovenSpeakerPlayerAnim(0.0)
    var indexL = 0
        private set
    private var touching = false
    private var touchedAt = -1e9
    private var coasting = false
    private var coastV = 0.0
    private var snapUntil = 0.0
    private var resuming = false
    private var grip = Grip()
    private var seekAt = -9.0
    private var pauseBeatAt = -1.0
    val pillPress = WovenSpeakerPlayerAnim(1.0)

    private val n = queueSize
    private val inOut = { x: Double -> M.easeInOut(x) }
    private val out = { x: Double -> M.easeOut(x) }

    /** Plays or pauses: every part follows with its own delay (the concept's choreography), from where it is now. */
    private fun setPlaying(next: Boolean, byTouch: Boolean) {
        if (playingL == next) return
        playingL = next
        val now = clock
        if (reduce) {
            for (a in listOf(glyphL, bloomL, lampL, titleL, lineL, pillL, reflL, handleL)) a.run(AnimStep.Timing(if (next) 1.0 else 0.0, 0.2))
        } else if (next) {
            glyphL.run(AnimStep.Timing(1.0, 0.26, inOut))
            bloomL.run(AnimStep.Delay(0.06), AnimStep.Timing(1.0, 0.3, out))
            lampL.run(AnimStep.Delay(0.12), AnimStep.Timing(1.0, 0.22, out))
            titleL.run(AnimStep.Delay(0.16), AnimStep.Timing(1.0, 0.24, inOut))
            lineL.run(AnimStep.Delay(0.2), AnimStep.Timing(1.0, 0.24, inOut))
            pillL.run(AnimStep.Delay(0.24), AnimStep.Timing(1.0, 0.24, inOut))
            reflL.run(AnimStep.Delay(0.33), AnimStep.Timing(1.0, 0.2, out))
            handleL.run(AnimStep.Delay(0.8), AnimStep.Timing(1.0, 0.25, out))
            pressL.run(AnimStep.Timing(0.9, 0.08, out), AnimStep.Spring(1.0, M.SNAP_SPRING))
        } else {
            glyphL.run(AnimStep.Timing(0.0, 0.22, inOut))
            pillL.run(AnimStep.Timing(0.0, 0.24, inOut))
            lineL.run(AnimStep.Delay(0.04), AnimStep.Timing(0.0, 0.24, inOut))
            titleL.run(AnimStep.Delay(0.08), AnimStep.Timing(0.0, 0.24, inOut))
            bloomL.run(AnimStep.Timing(0.0, 0.4, out))
            lampL.run(AnimStep.Timing(0.0, 0.4, out))
            reflL.run(AnimStep.Timing(0.0, 0.2, out))
            handleL.run(AnimStep.Timing(0.0, 0.3, out))
            pressL.run(AnimStep.Timing(0.9, 0.08, out), AnimStep.Spring(1.0, M.SNAP_SPRING))
            pauseBeatAt = now + M.PAUSE_BEAT_DELAY_MS / 1000
        }
        if (next) {
            playClock = 0.0
            nextBeat = M.FIRST_NOTE_DELAY_MS / 1000
            beatN = 0
        }
        grip.byTouchPlay = byTouch && next
        emitPlaying(next)
    }

    /** A finger takes the control: the demo's current frame becomes the live state, no jump. */
    private fun takeOver() {
        resuming = false
        if (!auto) return
        auto = false
        val k = look
        posL.set(k.pos)
        xL = k.pos
        val on = k.glyph > 0.5
        playingL = on
        glyphL.set(k.glyph)
        bloomL.set((k.bloom - M.BLOOM_REST) / (1 - M.BLOOM_REST))
        lampL.set((k.lamp - 0.12) / 0.88)
        titleL.set(k.title)
        lineL.set(k.line)
        pillL.set(k.pill)
        reflL.set(k.refl)
        handleL.set(k.handle)
        pressL.set(k.press)
        turnL.set(k.turn)
        psiOL.set(k.psiO)
        shearDL.set(k.psiO - k.psiI)
        prevPsi = k.psiO
        prevShear = k.psiO - k.psiI
        fingerL = k.finger
        dentXL = k.dentX
        dentYL = k.dentY
        dentL.set(k.dent)
        pullL.set(k.pull)
        pullAtL = k.pullAt
        rings = doubleArrayOf(-9.0, 0.0, -9.0, 0.0, -9.0, 0.0, -9.0, 0.0)
        playClock = 0.0
        nextBeat = M.FIRST_NOTE_DELAY_MS / 1000
        beatN = 0
        emitPlaying(on)
    }

    /** The finger (or the coast) turned the dome by `dA` rad: the position follows 1:1, the weave 0.22 of it, rubber past the ends. */
    private fun turnBy(dA: Double, shear: Boolean): Boolean {
        val turnSec = duration
        val dx = (dA / (2 * PI)) * turnSec
        val x0 = xL
        val x1 = x0 + dx
        val p0 = min(max(x0, 0.0), duration)
        val p1 = min(max(x1, 0.0), duration)
        val inDx = p1 - p0
        val outDx = dx - inDx
        val eff = ((inDx + M.RUBBER * outDx) / turnSec) * 2 * PI
        xL = x1
        posL.set(p1)
        psiOL.set(psiOL.value + M.GAIN * eff)
        if (shear && !reduce) shearDL.set(shearDL.value + (1 - M.INNER) * M.GAIN * eff)
        return x1 < 0 || x1 > duration
    }

    /** Snaps the position to the nearest second with a spring, and tells the host. */
    private fun snapToSecond() {
        val target = round(min(max(posL.value, 0.0), duration))
        xL = target
        snapUntil = clock + 0.45
        posL.run(AnimStep.Spring(target, M.SNAP_SECOND))
        cue(WovenSpeakerPlayerBeat.SETTLE)
        emitCommit(target.toInt())
    }

    private fun release(turned: Boolean, v: Double) {
        val now = clock
        touching = false
        touchedAt = now
        dentL.run(AnimStep.Spring(0.0, WovenSpeakerPlayerSpring(9.0, 150.0, 1.0)))
        emitPress(false)
        if (!turned) return
        turnL.run(AnimStep.Timing(0.0, 0.4, inOut))
        shearDL.run(AnimStep.Spring(0.0, M.RELAX))
        pullAtL = fingerL
        pullL.set(M.PULL)
        pullL.run(AnimStep.Delay(0.06), AnimStep.Timing(0.0, 1.2, out))
        val vMax = M.COAST_MAX_TURNS * 2 * PI * (-ln(M.COAST_DECAY) * 1000)
        val vv = max(-abs(vMax), min(abs(vMax), v))
        if (abs(vv) > 0.6 && xL >= 0 && xL <= duration) {
            coastV = vv
            coasting = true
        } else {
            if (xL < 0 || xL > duration) xL = min(max(xL, 0.0), duration)
            snapToSecond()
        }
    }

    private fun commitTrack(dir: Int, fromSwipe: Boolean) {
        val i = indexL
        val j = i + dir
        if (j < 0 || j >= n) {
            slideL.run(AnimStep.Timing(-dir * 14.0, 0.09), AnimStep.Spring(0.0, M.GLIDE))
            return
        }
        indexL = j
        slideL.set(slideL.value + dir * M.SLIDE_SPAN)
        slideL.run(AnimStep.Spring(0.0, if (fromSwipe) M.GLIDE else M.SNAP_SPRING))
        launchRing(clock, 1.6)
        cue(WovenSpeakerPlayerBeat.TRACK)
        emitIndex(j)
    }

    private fun launchRing(now: Double, amp: Double) {
        var at = 0
        var oldest = 1e9
        for (i in 0 until 4) {
            val t0 = rings[i * 2]
            if (t0 < oldest) {
                oldest = t0
                at = i
            }
        }
        rings[at * 2] = now
        rings[at * 2 + 1] = amp
    }

    /** 2 s after the last touch: ease back to the shot's rest frame, then the loop starts again at t = 0. */
    private fun rest() {
        resuming = true
        val ease = { x: Double -> M.easeInOut(x) }
        playingL = false
        posL.run(AnimStep.Timing(0.0, 0.6, ease))
        xL = 0.0
        for (a in listOf(glyphL, bloomL, lampL, titleL, lineL, pillL, reflL, handleL, turnL, psiOL, shearDL, dentL, pullL, slideL)) a.run(AnimStep.Timing(0.0, 0.6, ease))
        pressL.run(AnimStep.Timing(1.0, 0.6, ease))
        indexL = 0
    }

    private fun stepAnims(dt: Double) {
        for (a in listOf(posL, glyphL, bloomL, lampL, titleL, lineL, pillL, reflL, handleL, pressL, turnL, psiOL, shearDL, dentL, pullL, headL, slideL, pillPress)) a.step(dt)
    }

    /** One frame, `dtRaw` seconds since the last. */
    fun frame(dtRaw: Double) {
        val dt = min(dtRaw, 0.05)
        val now = clock + dt
        clock = now
        stepAnims(dt)
        if (auto) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            look = M.lookAt(t)
            return
        }
        // ---- live ----
        val touch = touching
        val g = grip
        // the coast: the angular speed decays at 0.996 per ms, the same mapping as the finger
        if (coasting) {
            var v = coastV * M.COAST_DECAY.pow(dt * 1000)
            coastV = v
            val limitHit = turnBy(v * dt, false)
            if (abs(v) < M.COAST_STOP || limitHit) {
                v = 0.0
                coasting = false
                if (limitHit) {
                    xL = min(max(xL, 0.0), duration)
                    cue(WovenSpeakerPlayerBeat.LIMIT)
                    headL.run(AnimStep.Timing(1.0, 0.12), AnimStep.Timing(0.0, 0.26))
                }
                snapToSecond()
            }
            if (now - seekAt >= 0.1) {
                seekAt = now
                emitSeek(posL.value)
            }
        }
        val busy = touch || coasting || now < snapUntil
        // playback: one second per second, nothing while a finger or a coast owns the position
        if (playingL && !busy) {
            val p = posL.value + dt
            if (p >= duration) {
                posL.set(duration)
                xL = duration
                setPlaying(false, false)
                cue(WovenSpeakerPlayerBeat.LIMIT)
            } else {
                posL.set(p)
                xL = p
            }
        }
        // pulses: on the beat grid; none while the cloth is under a finger
        if (playingL && !busy) {
            playClock += dt
            while (playClock >= nextBeat) {
                val nn = beatN
                val strong = nn % M.ACCENT_EVERY == 0
                launchRing(now, if (strong) M.RING_STRONG else M.RING_WEAK)
                if (strong) strongAt = now
                if (nn == 0 && g.byTouchPlay) cue(WovenSpeakerPlayerBeat.FIRST_NOTE)
                beatN = nn + 1
                nextBeat += M.BEAT
            }
        }
        if (pauseBeatAt > 0 && now >= pauseBeatAt) {
            pauseBeatAt = -1.0
            cue(WovenSpeakerPlayerBeat.PAUSE)
        }

        // ---- compose the look from the live values ----
        val k = WovenSpeakerPlayerLook()
        k.pos = posL.value
        k.glyph = glyphL.value
        k.press = pressL.value
        val bl = bloomL.value
        k.bloom = M.mix(M.BLOOM_REST, 1.0, bl)
        k.bloomR = M.mix(Theme.BLOOM_R0, Theme.BLOOM_R1, bl)
        val lp = lampL.value
        k.lamp = M.mix(0.12, 1.0, lp)
        k.reach = M.mix(18.0, 46.0, lp)
        k.title = titleL.value
        k.line = lineL.value
        k.pill = pillL.value
        k.refl = reflL.value
        k.turn = turnL.value
        val psiO = psiOL.value
        val sd = if (reduce) 0.0 else shearDL.value
        k.psiO = psiO
        k.psiI = psiO - sd
        k.shear = max(-1.0, min(1.0, sd / M.SHEAR_FULL))
        k.omO = abs(psiO - prevPsi) / max(dt, 0.001) / 60
        k.omI = abs(psiO - sd - (prevPsi - prevShear)) / max(dt, 0.001) / 60
        prevPsi = psiO
        prevShear = sd
        k.finger = fingerL
        k.dentX = dentXL
        k.dentY = dentYL
        k.dent = dentL.value
        k.pull = pullL.value
        k.pullAt = pullAtL
        k.handle = handleL.value
        k.head = headL.value
        if (!reduce) {
            for (i in 0 until 4) {
                val r = M.ringAt(now, rings[i * 2], rings[i * 2 + 1])
                if (r.a <= 0) continue
                when (i) {
                    0 -> { k.r0 = r.r; k.a0 = r.a }
                    1 -> { k.r1 = r.r; k.a1 = r.a }
                    2 -> { k.r2 = r.r; k.a2 = r.a }
                    else -> { k.r3 = r.r; k.a3 = r.a }
                }
            }
            k.pulse = M.pulseAt(now, strongAt)
        }
        k.wash = if (ambient) bl * (Theme.WASH_BASE + (Theme.WASH_BEAT - Theme.WASH_BASE) * min(k.pulse, 1.0)) else 0.0
        look = k

        // idle: 2 s after the last touch the demo comes back
        if (autoPlay && !reduce && !touch && !coasting && now >= snapUntil && now - touchedAt > M.RESUME_AFTER && touchedAt > -1e8) {
            if (!resuming) {
                rest()
            } else {
                val settled = abs(posL.value) < 0.02 && abs(psiOL.value) < 0.002 && abs(shearDL.value) < 0.002 && abs(glyphL.value) < 0.01 &&
                    abs(bloomL.value) < 0.01 && abs(dentL.value) < 0.05
                if (settled) {
                    resuming = false
                    touchedAt = -1e9
                    loopT = 0.0
                    auto = true
                }
            }
        }
    }

    // ---- the finger ----

    private fun rel(x: Double) = x / s - HIT_BOX / 2

    /** The finger lands in the dome's square (x, y in dp from its top-left). Returns false when it is outside the annulus and the display. */
    fun domeBegin(x: Double, y: Double): Boolean {
        val now = clock
        val px = rel(x)
        val py = rel(y)
        val r = hypot(px, py)
        val region = if (r < M.ANNULUS_INNER) 2 else if (r <= M.ANNULUS_OUTER) 1 else 3
        var sub = 0
        if (region == 2) {
            if (abs(py) < 34 && abs(px) < 34) sub = 1
            else if (abs(py) < 22 && abs(px + Theme.DISC_DX) < 22) sub = 2
            else if (abs(py) < 22 && abs(px - Theme.DISC_DX) < 22) sub = 3
        }
        val a = atan2(py, px)
        grip = Grip(region = region, sub = sub, a0 = a, prev = a, vAt = now, tickAt = now, tickN = floor(posL.value / M.TICK_EVERY).toInt())
        if (region == 3) return false
        takeOver()
        touching = true
        touchedAt = now
        coasting = false
        if (region == 1) {
            xL = posL.value
            val rr = min(r, Theme.DOME_R - 6)
            val kx = if (r > 0.001) rr / r else 1.0
            dentXL = px * kx
            dentYL = py * kx
            fingerL = a
            dentL.run(AnimStep.Timing(M.DENT, 0.08, out))
            cue(WovenSpeakerPlayerBeat.ONSET)
            emitPress(true)
        } else if (sub == 1) {
            pressL.run(AnimStep.Spring(0.9, M.SNAP_SPRING))
        }
        return true
    }

    fun domeMove(x: Double, y: Double, translationX: Double, translationY: Double) {
        val g = grip
        if (g.region != 1) return
        val now = clock
        val px = rel(x)
        val py = rel(y)
        val r = hypot(px, py)
        val a = atan2(py, px)
        val dA = M.wrapPi(a - g.prev)
        g.prev = a
        g.acc += dA
        val rr = min(max(r, M.ANNULUS_INNER), Theme.DOME_R - 6)
        val kx = if (r > 0.001) rr / r else 1.0
        dentXL = px * kx
        dentYL = py * kx
        fingerL = a
        val dtv = max(now - g.vAt, 0.004)
        g.v = g.v * 0.6 + 0.4 * (dA / dtv)
        g.vAt = now
        touchedAt = now
        if (!g.turning && hypot(translationX, translationY) > M.SLOP * s) {
            g.turning = true
            turnL.run(AnimStep.Spring(1.0, M.TURN_SPRING))
            g.atLimit = turnBy(g.acc, true)
        } else if (g.turning) {
            val hit = turnBy(dA, true)
            if (hit && !g.atLimit) {
                cue(WovenSpeakerPlayerBeat.LIMIT)
                headL.run(AnimStep.Timing(1.0, 0.12), AnimStep.Timing(0.0, 0.26))
            }
            g.atLimit = hit
            val nn = floor(posL.value / M.TICK_EVERY).toInt()
            if (nn != g.tickN && now - g.tickAt >= M.TICK_GAP) {
                g.tickAt = now
                cue(WovenSpeakerPlayerBeat.TICK)
            }
            g.tickN = nn
            if (now - seekAt >= 0.1) {
                seekAt = now
                emitSeek(posL.value)
            }
        }
    }

    fun domeEnd(translationX: Double, translationY: Double, success: Boolean) {
        val g = grip
        if (g.region == 0 || g.region == 3) {
            grip = Grip()
            return
        }
        val now = clock
        if (g.region == 1) {
            // a turn carries the finger's speed on (the last 80 ms); a press only lets the fabric spring back
            release(g.turning, if (now - g.vAt < M.V_TAIL * 3) g.v else 0.0)
        } else {
            touching = false
            touchedAt = now
            pressL.run(AnimStep.Spring(1.0, M.SNAP_SPRING))
            val moved = hypot(translationX, translationY) / s
            if (success && moved < M.SLOP) {
                when (g.sub) {
                    1 -> {
                        takeOver()
                        setPlaying(!playingL, true)
                    }
                    2 -> commitTrack(-1, false)
                    3 -> commitTrack(1, false)
                }
            }
        }
        grip = Grip()
    }

    // the headline is the queue's handle: swipe it (while playing) to change track
    fun titleBegin() {
        takeOver()
        touching = true
        touchedAt = clock
    }

    fun titleMove(translationX: Double) {
        if (!playingL) return
        var sx = translationX
        if ((indexL == 0 && sx > 0) || (indexL == n - 1 && sx < 0)) sx *= M.TITLE_RUBBER
        slideL.set(sx)
    }

    fun titleEnd(velocityX: Double) {
        touching = false
        touchedAt = clock
        if (!playingL) return
        val sx = slideL.value
        if (sx < -M.SWIPE || velocityX < -M.SWIPE_VELOCITY) commitTrack(1, true)
        else if (sx > M.SWIPE || velocityX > M.SWIPE_VELOCITY) commitTrack(-1, true)
        else slideL.run(AnimStep.Spring(0.0, M.GLIDE))
    }

    fun pillDown() {
        pillPress.run(AnimStep.Spring(0.97, M.SNAP_SPRING))
    }

    fun pillUp(success: Boolean) {
        pillPress.run(AnimStep.Spring(1.0, M.SNAP_SPRING))
        if (success && playingL) emitOpen()
    }

    // ---- accessibility ----

    fun seekBy(sec: Double) {
        takeOver()
        val target = min(max(round(posL.value + sec), 0.0), duration)
        xL = target
        posL.run(AnimStep.Timing(target, 0.2))
        emitCommit(target.toInt())
    }

    fun togglePlay() {
        takeOver()
        setPlaying(!playingL, false)
    }

    fun a11yTrack(dir: Int) {
        takeOver()
        commitTrack(dir, false)
    }

    val playing: Boolean get() = playingL
    companion object {
        /** the dome's touch square, design pt */
        const val HIT_BOX = 340.0
    }
}
