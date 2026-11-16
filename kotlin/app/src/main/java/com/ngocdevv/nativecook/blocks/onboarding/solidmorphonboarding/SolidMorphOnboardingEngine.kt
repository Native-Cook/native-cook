package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingTheme as Theme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign

/** Cues the host turns into haptics (touch only; the loop is silent). */
internal object SolidMorphOnboardingBeat {
    const val CONTACT = 0 // the 4 pt cushion stop, or the rubber at either end: rigid
    const val TICK = 1 // the glyph reveals (progress .9 of a page): selection
    const val ARRIVAL = 2 // a page arrives after a real interaction: light
}

/**
 * The controller of the RN block (`SolidMorphPager.tsx`): the loop plays `progress` (0 .. pages - 1) by itself; a drag moves it 1:1 (320 dp per page) with
 * rubber at the ends, a press on the solid compresses its cushion and tilts it, a release springs to the nearest page (a fast flick chains one more), a tap
 * on a side pages that way, the buttons press, and two seconds after the last touch the demo comes back through a 0.6 s blend. Main thread, one `frame`
 * per vsync. Not ported from the RN props: `index` / `progress` (controlled), `renderFooter`, `tokens`, custom glyph sources, the rotation-sensor tilt,
 * `SolidMorph` (the solid alone).
 */
internal class SolidMorphOnboardingEngine(
    val pages: List<SolidMorphOnboardingPage>,
    val autoPlay: Boolean,
    val reduced: Boolean,
    val layout: Theme.Layout,
    val hover: Boolean = true,
) {
    var cue: (Int) -> Unit = {}
    var onIndexChange: (Int, Boolean) -> Unit = { _, _ -> }
    var ready = false

    private val count = pages.size
    private val end = count - 1
    val p = SolidMorphOnboardingAnim(0.0)
    val rate = SolidMorphOnboardingAnim(0.0)
    val cushion = SolidMorphOnboardingAnim(0.0)
    val tiltX = SolidMorphOnboardingAnim(0.0)
    val tiltY = SolidMorphOnboardingAnim(0.0)
    val base = SolidMorphOnboardingAnim(0.0)
    val lift = SolidMorphOnboardingAnim(0.0)
    val contactPulse = SolidMorphOnboardingAnim(0.0)
    val basePulse = SolidMorphOnboardingAnim(0.0)
    val primaryPress = SolidMorphOnboardingAnim(0.0)
    val secondaryPress = SolidMorphOnboardingAnim(0.0)
    val visibility = SolidMorphOnboardingAnim(1.0)

    var playing = autoPlay && !reduced
        private set
    var clock = 0.0 // loop time, s
        private set
    var selected = 0
        private set
    private var origin = -1.0
    private var pauseAt = -1.0
    private var touchedAt = -1e9
    private var touch = false
    private var startP = 0.0
    private var target = 0.0
    private var sampleTime = 0.0
    private var lastFrame = 0.0
    private var resetFrom = -1.0
    private var resetClock = 0.0
    private var resetPhase = 0.0
    private var chainTarget = 0.0
    private var horizontal = false
    private var contact = false
    private var limit = false
    private var userActive = false
    private var lastNotice = -1e9
    private var hitObject = false
    private var nowMs = 0.0
    private var prevP: Double? = null
    private var prevPage = 0
    private var pendingNotice: DoubleArray? = null // (fire at ms, page)

    /** Whether the pager is in the foreground (RN `AppState`); the clock holds while it is not. */
    fun onForeground(on: Boolean) {
        if (!on && playing) pauseAt = lastFrame
    }

    private fun stepAnims(dt: Double) {
        for (a in listOf(p, rate, cushion, tiltX, tiltY, base, lift, contactPulse, basePulse, primaryPress, secondaryPress, visibility)) a.step(dt)
    }

    /** `reducedPage`: fade out, jump to the page, fade in (RN: two halves of the return fade). */
    private fun reducedPage(to: Double) {
        p.cancel()
        visibility.run(
            AnimStep.Timing(
                0.0, M.RETURN_FADE / 2,
                done = {
                    p.set(to)
                    visibility.run(AnimStep.Timing(1.0, M.RETURN_FADE / 2))
                },
            ),
        )
    }

    // ---- the frame ----

    /** One frame; `tsMs` is the frame time in ms, `dt` seconds since the last. */
    fun frame(tsMs: Double, dt: Double) {
        nowMs = tsMs
        stepAnims(min(dt, 0.05))
        react()
        pendingNotice?.let { n ->
            if (tsMs >= n[0]) {
                pendingNotice = null
                lastNotice = tsMs
                selected = n[1].toInt()
                onIndexChange(selected, userActive && !playing)
            }
        }
        checkArrival()
        if (!ready || reduced) return
        lastFrame = tsMs
        if (origin < 0) {
            origin = tsMs
            pauseAt = -1.0
        }
        if (playing) {
            if (pauseAt >= 0) {
                origin += tsMs - pauseAt
                pauseAt = -1.0
            }
            val time = ((tsMs - origin) / 1000) % M.LOOP
            clock = time
            p.set((M.progressAt(time) * end) / 2)
            rate.set((M.rateAt(time) * end) / 2)
        } else {
            if (hover || touch || abs(rate.value) > 0.0001) clock = (clock + dt) % M.LOOP
            if (pauseAt < 0) pauseAt = tsMs
            if (!touch && autoPlay && nowMs - touchedAt > M.RESUME_AFTER * 1000) {
                val reset = M.smooth(M.RESUME_AFTER * 1000, M.TOUCH_RESUME, nowMs - touchedAt)
                if (reset < 1) {
                    if (resetFrom < 0) {
                        userActive = false
                        p.cancel()
                        resetFrom = p.value
                        resetClock = clock
                        resetPhase = round(clock / (M.LOOP / 4)) * (M.LOOP / 4)
                        tiltX.run(AnimStep.Timing(0.0, M.RESET_DURATION))
                        tiltY.run(AnimStep.Timing(0.0, M.RESET_DURATION))
                    }
                    val blend = M.ease(reset)
                    p.set(resetFrom * (1 - blend))
                    clock = resetClock + (resetPhase - resetClock) * blend
                } else {
                    p.set(0.0)
                    tiltX.set(0.0)
                    tiltY.set(0.0)
                    lift.set(0.0)
                    base.set(0.0)
                    cushion.set(0.0)
                    resetFrom = -1.0
                    playing = true
                    origin = tsMs
                    pauseAt = -1.0
                    clock = 0.0
                }
            }
        }
    }

    /** RN's two `useAnimatedReaction`s on the progress: the page notice (rate-limited to 10 a second) and the velocity sample with the glyph tick. */
    private fun react() {
        val value = p.value
        val previous = prevP
        if (previous != null && value == previous) return
        prevP = value
        val v = round(M.clamp(value, 0.0, end.toDouble())).toInt()
        if (v != prevPage) {
            prevPage = v
            val delay = max(0.0, 100 - (nowMs - lastNotice))
            pendingNotice = doubleArrayOf(nowMs + delay, v.toDouble())
        }
        if (playing) return
        if (previous != null && userActive) {
            val forwardReveal = value > previous && floor(value + 0.1) > floor(previous + 0.1)
            val reverseReveal = value < previous && floor(value - 0.1) < floor(previous - 0.1)
            if (forwardReveal || reverseReveal) cue(SolidMorphOnboardingBeat.TICK)
        }
        val time = nowMs
        if (previous != null && sampleTime > 0) {
            val velocity = (value - previous) / max(0.008, (time - sampleTime) / 1000)
            rate.set(velocity)
            rate.run(AnimStep.Timing(0.0, M.VELOCITY_FADE))
        }
        sampleTime = time
    }

    /** Pages to `to` on the lathe spring, carrying the current rate. */
    fun go(to: Int) {
        val velocity = rate.value
        playing = false
        arrivalWatch = false
        p.cancel()
        val t = M.clamp(to.toDouble(), 0.0, end.toDouble())
        target = t
        touchedAt = nowMs
        userActive = true
        resetFrom = -1.0
        sampleTime = nowMs
        if (reduced) reducedPage(t) else p.run(AnimStep.Spring(t, M.LATHE, velocity))
        cushion.run(AnimStep.Spring(0.0, M.CUSHION))
    }

    // ---- the finger ----

    fun panBegin(x: Double, y: Double) {
        val wasPlaying = playing
        if (wasPlaying) {
            lift.set(lift.value + M.bobAt(clock))
            base.set(base.value + M.baseAt(clock))
        }
        playing = false
        rate.run(AnimStep.Timing(0.0, M.VELOCITY_FADE))
        sampleTime = nowMs
        touch = true
        touchedAt = nowMs
        arrivalWatch = false
        p.cancel()
        cushion.cancel()
        lift.cancel()
        base.cancel()
        tiltX.cancel()
        tiltY.cancel()
        startP = p.value
        chainTarget = if (wasPlaying || abs(target - p.value) > 0.8) round(p.value) else target
        horizontal = false
        resetFrom = -1.0
        contact = false
        limit = false
        userActive = true
        val l = layout
        val isHero = abs(x - l.heroX) < l.heroRadius * 1.3 && abs(y - l.heroY) < l.heroRadius
        hitObject = isHero
        if (isHero && !reduced) cushion.run(AnimStep.Spring(0.23, M.GLIDE))
    }

    fun panUpdate(x: Double, y: Double, tx: Double, ty: Double) {
        touchedAt = nowMs
        if (reduced) return
        val l = layout
        if (abs(tx) >= M.PRESS_SLOP || horizontal || !hitObject) {
            if (!horizontal) cushion.run(AnimStep.Spring(0.0, M.GLIDE))
            horizontal = true
            val tracked = tx - (if (hitObject) sign(tx) * M.PRESS_SLOP else 0.0)
            val raw = startP - tracked / M.DRAG / l.s
            p.set(M.rubber(raw, end.toDouble()))
            if ((raw < -0.04 || raw > end + 0.04) && !limit) {
                limit = true
                cue(SolidMorphOnboardingBeat.CONTACT)
            }
        } else {
            val down = max(0.0, ty)
            val compress = M.pressAt(down / l.s)
            cushion.set(max(0.23, compress))
            if (compress >= 0.99 && !contact) {
                contact = true
                contactPulse.run(AnimStep.Timing(1.0, 0.001), AnimStep.Timing(0.0, 0.12))
                cue(SolidMorphOnboardingBeat.CONTACT)
            }
        }
        if (hitObject) {
            tiltX.set(M.clamp((x - l.heroX) / l.heroRadius, -1.0, 1.0) * M.TILT_LIMIT)
            tiltY.set(M.clamp((y - l.heroY) / l.heroRadius, -1.0, 1.0) * M.TILT_LIMIT)
        }
    }

    /** `vx`, `vy` in dp per second, `tx` the translation. */
    fun panEnd(vx: Double, vy: Double, tx: Double) {
        val l = layout
        val velocity = -vx / M.DRAG / l.s
        val value = if (reduced) startP - tx / M.DRAG / l.s else p.value
        val fast = abs(vx) > 400 && (horizontal || reduced)
        val to = M.clamp(if (fast) round(chainTarget) + sign(velocity) else round(value + velocity * M.PROJECTION), 0.0, end.toDouble())
        target = to
        if (reduced) {
            reducedPage(to)
        } else {
            p.run(AnimStep.Spring(to, M.LATHE, velocity))
            arrivalWatch = to != round(startP)
        }
        if (!reduced) {
            cushion.run(AnimStep.Spring(0.0, M.CUSHION))
            lift.run(AnimStep.Spring(0.0, M.CUSHION))
            base.run(AnimStep.Spring(0.0, M.BASE))
            tiltX.run(AnimStep.Spring(0.0, M.PRECESSION, vx * M.PRECESSION_VELOCITY))
            tiltY.run(AnimStep.Spring(0.0, M.PRECESSION, vy * M.PRECESSION_VELOCITY))
        }
    }

    /** true while the page spring started by a release should end with the arrival bump */
    private var arrivalWatch = false

    fun panFinalize(success: Boolean) {
        touch = false
        touchedAt = nowMs
        if (!reduced && !success) {
            lift.run(AnimStep.Spring(0.0, M.CUSHION))
            base.run(AnimStep.Spring(0.0, M.BASE))
            cushion.run(AnimStep.Spring(0.0, M.CUSHION))
            tiltX.run(AnimStep.Spring(0.0, M.CUSHION))
            tiltY.run(AnimStep.Spring(0.0, M.CUSHION))
        }
    }

    /** The arrival: when the release's spring has settled on a new page, the solid hops, the base answers and a light cue plays. */
    fun checkArrival() {
        if (arrivalWatch && !p.running) {
            arrivalWatch = false
            lift.run(AnimStep.Timing(4.0, 0.07), AnimStep.Spring(0.0, M.CUSHION))
            base.run(AnimStep.Delay(0.3), AnimStep.Timing(-1.5, 0.05), AnimStep.Spring(0.0, M.BASE))
            basePulse.run(AnimStep.Delay(0.3), AnimStep.Timing(1.0, 0.001), AnimStep.Timing(0.0, 0.2))
            cue(SolidMorphOnboardingBeat.ARRIVAL)
        }
    }

    fun tap(x: Double, width: Double) {
        if (!hitObject) go(round(p.value).toInt() + (if (x > width / 2) 1 else -1))
    }

    // ---- the buttons ----

    private fun press(v: SolidMorphOnboardingAnim) {
        playing = false
        rate.run(AnimStep.Timing(0.0, M.VELOCITY_FADE))
        sampleTime = nowMs
        touchedAt = nowMs
        v.run(AnimStep.Spring(1.0, M.GLIDE))
    }

    fun primaryDown() = press(primaryPress)
    fun primaryUp() = primaryPress.run(AnimStep.Spring(0.0, M.GLIDE))
    fun secondaryDown() = press(secondaryPress)
    fun secondaryUp() = secondaryPress.run(AnimStep.Spring(0.0, M.GLIDE))

    fun touched() {
        touchedAt = nowMs
    }

}
