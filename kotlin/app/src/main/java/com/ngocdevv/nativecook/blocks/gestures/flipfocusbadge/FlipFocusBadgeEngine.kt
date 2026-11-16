package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeAnim.Step
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeMotion as M
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The controller of Flip Focus Badge on the main thread: port of RN `NumeralBadge`'s frame callback and gestures (the loop, a finger that takes the
 * badge from wherever the loop has it, 1:1 drag with velocity hand-off to a spring that settles on the nearer face, the press-and-hold, the ring's viscous
 * joint, the numeral exchange hidden behind the plate, detents, glints) and of `FlipFocusScreen`'s session (start on the back, stop on the front, the
 * hour, the total's roll, the line, the labels, the hint, the date range).
 *
 * Not ported: the device-turn source (Reanimated's rotation sensor; the finger and the loop only), the controlled `running` / `face` / `yaw` / `orbit` /
 * `depth` / `tint` / `plate` / `edge` / `gloss` props and the dev pin hook.
 */
internal class FlipFocusBadgeEngine(
    private val autoPlay: Boolean,
    private val reduce: Boolean,
    private val timeScale: Double,
    private val minSession: Double,
    private val totalSeconds: Double,
    private val s: Double,
) {
    var haptics: FlipFocusBadgeHaptics? = null
    var onStart: (() -> Unit)? = null
    var onStop: ((Int) -> Unit)? = null
    var onHour: ((Int) -> Unit)? = null
    /** called when the session starts or stops (the screen reader's state) */
    var onRunning: ((Boolean) -> Unit)? = null

    // ---- the badge's state ----------------------------------------------------------------------------------------------
    private val yaw = FlipFocusBadgeAnim(0.0)
    private val pitch = FlipFocusBadgeAnim(0.0)
    private val depth = FlipFocusBadgeAnim(1.0)
    private var ringV = 0.0
    private var ringW = 0.0
    private var valueV = floor(totalSeconds / 3600)
    private var risen = true
    private val press = FlipFocusBadgeAnim(1.0)
    private val popPlus = FlipFocusBadgeAnim(1.0)
    private val popHr = FlipFocusBadgeAnim(1.0)
    private var holdV = false
    private var touching = false
    private var ended = true
    private var lastTx = 0.0
    private var lastTy = 0.0
    private var lastTouch = -1e9
    private var lastK = 0
    private var lastCue = -1.0
    private var lastFace = 0
    private var glintAt = -10.0
    private var glintOn = false
    /** the badge's opacity (the screen-reader flip under Reduce Motion fades it) */
    val fade = FlipFocusBadgeAnim(1.0)
    private var clockT = 0.0
    private var loopT = 0.0
    /** whether the loop plays */
    var isPlaying = autoPlay && !reduce
        private set
    private var resuming = false
    private var lapped = 1e9
    private val rest = M.lookAt(0.0)
    val look = FlipFocusBadgeLook()

    // ---- the session ------------------------------------------------------------------------------------------------------
    private var totalSecs = totalSeconds
    private var elapsed = 0.0
    var running = false
        private set
    private var clockV = 0.0
    private var orbitV = totalSeconds / 3600
    private val hintLive = FlipFocusBadgeAnim(0.0)
    private val labelLive = FlipFocusBadgeAnim(0.0)
    private val rollLive = FlipFocusBadgeAnim(1.0)
    private var hoursSeen = floor(totalSeconds / 3600).toInt()
    /** the numeral the badge works towards (the hours of the day's total) */
    var valueN = floor(totalSeconds / 3600).toInt()
        private set
    private var labPrevA = valueN
    private var labPrevB = valueN
    private var rollFromS = fmt(totalSeconds)
    private var rollToS = fmt(totalSeconds)
    private var playingMirror = isPlaying
    var range = 0
        private set
    val dateFade = FlipFocusBadgeAnim(if (reduce) 1.0 else 0.0)
    val totalFade = FlipFocusBadgeAnim(1.0)
    private val itemW = (Theme.SEG_W - 2 * Theme.SEG_PAD - 3 * Theme.SEG_GAP) / 4
    private fun pillXFor(i: Int) = (Theme.SEG_PAD + i * (itemW + Theme.SEG_GAP)) * s
    val pill = FlipFocusBadgeAnim(pillXFor(0))
    /** -1 none, 0 help, 1 settings: the pressed disc (opacity 0.7) */
    var discPressed = -1

    // the page's values: the loop's while it plays, the session's when a finger has taken over
    var pFill = 0.4
    var pHint = 0.0
    var pLabel = 0.0
    var pRoll = 0.0
    var pSweep = -1.0

    init {
        look.copyFrom(rest)
        if (!reduce) dateFade.run(Step.Timing(1.0, 0.2))
        pageValues()
    }

    val labA: Int get() = if (isPlaying) floor(totalSeconds / 3600).toInt() else labPrevA
    val labB: Int get() = if (isPlaying) floor(totalSeconds / 3600).toInt() + 1 else labPrevB
    val rollFrom: String get() = if (isPlaying) fmt(totalSeconds) else rollFromS
    val rollTo: String get() = if (isPlaying) fmt(totalSeconds + M.SESSION_SECS) else rollToS

    private fun nearestFace(angle: Double) = Math.round(angle / 180).toDouble() * 180

    private fun faceOf(angle: Double): Int {
        val k = Math.round(angle / 180).toInt()
        return ((k % 2) + 2) % 2
    }

    // ---- the frame -----------------------------------------------------------------------------------------------------------
    fun frame(dtIn: Double) {
        val dt = min(dtIn, 0.05)
        yaw.step(dt); pitch.step(dt); depth.step(dt); press.step(dt); popPlus.step(dt); popHr.step(dt); fade.step(dt)
        hintLive.step(dt); labelLive.step(dt); rollLive.step(dt); dateFade.step(dt); totalFade.step(dt); pill.step(dt)
        badgeFrame(dt)
        pageFrame(dt)
        if (isPlaying != playingMirror) {
            playingMirror = isPlaying
            if (playingMirror) resetSession()
        }
        pageValues()
    }

    private fun cueOnce(fn: () -> Unit, now: Double) {
        if (now - lastCue < M.MIN_GAP_S) return
        lastCue = now
        fn()
    }

    private fun startRise() {
        depth.run(Step.Spring(1.0, M.RISE))
        popPlus.run(Step.Delay(0.04), Step.Spring(1.0, M.POP))
        popHr.run(Step.Delay(0.08), Step.Spring(1.0, M.POP))
        risen = true
    }

    private fun badgeFrame(dt: Double) {
        val now = clockT + dt
        clockT = now
        if (isPlaying) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            M.lookAt(t, look)
            return
        }
        // idle for 2 s after a touch: ease the live badge back to the pin's pose, then the loop plays again
        if (autoPlay && !reduce && !touching) {
            val idle = now - lastTouch
            if (idle > M.IDLE_RESUME && !resuming) {
                resuming = true
                val home = Math.round(yaw.value / 360).toDouble() * 360
                yaw.run(Step.Spring(home, M.GLIDE))
                pitch.run(Step.Spring(0.0, M.GLIDE))
                depth.run(Step.Spring(1.0, M.GLIDE))
                press.run(Step.Spring(1.0, M.GLIDE))
                popPlus.set(1.0)
                popHr.set(1.0)
            }
            if (idle > M.IDLE_RESUME + M.RESUME_MS / 1000 + 0.5 && resuming) {
                resuming = false
                loopT = 0.0
                isPlaying = true
                M.lookAt(0.0, look)
                return
            }
        }
        val yawNow = yaw.value
        // the ring chases the plate through a viscous joint and keeps turning a little after the plate stops
        var ringYaw = yawNow
        if (!reduce) {
            M.jointStep(ringV, ringW, yawNow, dt, jointOut)
            ringV = jointOut[0]
            ringW = jointOut[1]
            ringYaw = M.limitLag(yawNow, ringV)
        } else {
            ringV = yawNow
        }
        // a numeral exchange waits until the front is hidden (turned away or edge-on); it punches out again as it returns
        val front = cos(yawNow * FlipFocusBadgeGeometry.D2R)
        val target = valueN.toDouble()
        if (Math.round(valueV) != Math.round(target) && risen) {
            if (front < 0.02) {
                valueV = target
                depth.set(M.HOLD_DEPTH)
                popPlus.set(0.6)
                popHr.set(0.6)
                risen = false
            } else if (front > 0.5 && !holdV) {
                // facing us while it changes: press flat, exchange, punch out
                risen = false
                depth.run(
                    Step.Timing(M.HOLD_DEPTH, 0.16, done = {
                        valueV = valueN.toDouble()
                        popPlus.set(0.6)
                        popHr.set(0.6)
                        startRise()
                    }),
                )
            }
        }
        if (!risen && front > 0.25 && Math.round(valueV) == Math.round(target) && !holdV) {
            startRise()
            if (!touching) haptics?.success()
        }
        // detents at every face-on angle crossed by a finger or a settling spring
        val kk = floor(yawNow / 180).toInt()
        if (kk != lastK) {
            lastK = kk
            if (touching) cueOnce({ haptics?.detent() }, now)
        }
        // the satellite's lap
        val lap = floor(orbitV)
        if (lap > lapped) {
            lapped = lap
            glintAt = now
            glintOn = true
            haptics?.lap()
        } else if (lap < lapped) lapped = lap
        // glints: the sparkles catch the light in turn, 60 ms apart
        val g = now - glintAt
        fun pulse(c: Double): Double {
            val x = (g - c) / 0.07
            return if (glintOn && !reduce) exp(-x * x) else 0.0
        }
        // sway of the studio: a hand's tremor, 0.12 Hz (Reduce Motion: still)
        val env = if (reduce) 0.0 else ((5 * PI) / 180) * sin(2 * PI * 0.123 * now)
        val orbitFrac = orbitV - floor(orbitV)
        look.copyFrom(rest)
        look.yaw = yawNow
        look.pitch = pitch.value
        look.ringYaw = ringYaw
        look.depth = depth.value
        look.value = valueV
        look.clock = clockV
        look.sat = orbitFrac
        look.glintPlus = pulse(0.1)
        look.glintHead = pulse(0.16)
        look.glintSat = pulse(0.22)
        look.press = press.value
        look.popPlus = popPlus.value
        look.popHr = popHr.value
        look.env = env
        look.tPhase = now
        // the hold's long-press timer, in the frame's clock
        if (touching && downActive) {
            val dist = hypot(lastTx, lastTy)
            if (!holdV && !holdFailed) {
                if (dist > HOLD_MAX_DIST) holdFailed = true
                else if ((now - downClock) * 1000 >= M.HOLD_MS) holdStart()
            } else if (holdV && dist > HOLD_MAX_DIST) holdFinalize()
        }
    }

    private val jointOut = DoubleArray(2)

    private fun pageFrame(dt: Double) {
        if (isPlaying) return
        if (running) {
            val el = elapsed + dt * timeScale
            elapsed = el
            clockV = el
            val total = totalSecs + el
            orbitV = total / 3600
            val hoursNow = floor(total / 3600).toInt()
            if (hoursNow > hoursSeen) {
                hoursSeen = hoursNow
                hourJS(hoursNow)
            }
        } else {
            orbitV = totalSecs / 3600
        }
    }

    private fun pageValues() {
        if (isPlaying) {
            pFill = look.fill; pHint = look.hint; pLabel = look.labelK; pRoll = look.sheetK; pSweep = look.sweep
        } else {
            val frac = (totalSecs + elapsed) / 3600
            pFill = frac - floor(frac); pHint = hintLive.value; pLabel = labelLive.value; pRoll = rollLive.value; pSweep = -1.0
        }
    }

    /** the loop restarted: the live session state goes back to the pin's */
    private fun resetSession() {
        running = false
        elapsed = 0.0
        totalSecs = totalSeconds
        orbitV = totalSeconds / 3600
        hintLive.set(0.0)
        hoursSeen = floor(totalSeconds / 3600).toInt()
        rollLive.set(1.0)
        labelLive.set(0.0)
        valueN = floor(totalSeconds / 3600).toInt()
        labPrevA = valueN
        labPrevB = valueN
        rollFromS = fmt(totalSeconds)
        rollToS = fmt(totalSeconds)
        onRunning?.invoke(false)
    }

    // ---- the session ---------------------------------------------------------------------------------------------------------
    private fun hourJS(hours: Int) {
        valueN = hours
        val prevB = labPrevB
        labPrevA = prevB
        labPrevB = hours
        labelLive.set(0.0)
        labelLive.run(Step.Timing(1.0, 0.3, done = {
            labPrevA = hours
            labPrevB = hours
        }))
        onHour?.invoke(hours)
    }

    private fun start() {
        if (running) return
        running = true
        elapsed = 0.0
        clockV = 0.0
        hintLive.run(Step.Timing(1.0, 0.3))
        onRunning?.invoke(true)
        onStart?.invoke()
    }

    private fun stop() {
        if (!running) return
        val el = elapsed
        running = false
        hintLive.run(Step.Timing(0.0, 0.3))
        onRunning?.invoke(false)
        if (el < minSession) {
            // a look, not a start: nothing is counted
            elapsed = 0.0
            haptics?.discard()
            return
        }
        val before = totalSecs
        val after = before + el
        totalSecs = after
        elapsed = 0.0
        rollFromS = fmt(before)
        rollToS = fmt(after)
        rollLive.set(0.0)
        rollLive.run(Step.Delay(0.2), Step.Timing(1.0, 0.7))
        onStop?.invoke(Math.round(el).toInt())
    }

    private fun onFace(face: Int) {
        if (face == 1) start() else stop()
    }

    /** the day / week / month / year control */
    fun selectRange(i: Int) {
        if (i == range) return
        range = i
        if (reduce) {
            pill.set(pillXFor(i))
            dateFade.set(1.0)
            totalFade.set(1.0)
        } else {
            pill.run(Step.Spring(pillXFor(i), M.SNAP))
            dateFade.set(0.0)
            dateFade.run(Step.Timing(1.0, 0.2))
            totalFade.set(if (i == 0) 1.0 else 0.0)
            if (i != 0) totalFade.run(Step.Timing(1.0, 0.2))
        }
    }

    // ---- touch ---------------------------------------------------------------------------------------------------------------
    private var downActive = false
    private var panActive = false
    private var holdFailed = false
    private var downClock = 0.0

    private fun begin() {
        val now = clockT
        if (isPlaying) {
            // the finger takes the badge from wherever the loop has it: no jump
            val l = look
            yaw.set(l.yaw)
            pitch.set(l.pitch)
            depth.set(l.depth)
            ringV = l.ringYaw
            ringW = 0.0
            valueV = l.value
            popPlus.set(l.popPlus)
            popHr.set(l.popHr)
            press.set(l.press)
            lastK = floor(l.yaw / 180).toInt()
            lastFace = faceOf(l.yaw)
            risen = true
        }
        isPlaying = false
        resuming = false
        touching = true
        yaw.cancel()
        pitch.cancel()
        press.cancel()
        lastTouch = now
        press.run(Step.Spring(1.03, M.SNAP))
        haptics?.onset()
    }

    private fun finish() {
        touching = false
        lastTouch = clockT
        press.run(Step.Spring(1.0, M.SNAP))
    }

    private fun land(to: Double) {
        val f = faceOf(to)
        if (f != lastFace) {
            lastFace = f
            onFace(f)
            haptics?.land()
        }
    }

    /** release: carry the finger's velocity into a spring that settles on the nearer face (a tap settles with none) */
    private fun settle(vx: Double, vy: Double) {
        val v = vx * M.YAW_PER_PT
        val cur = yaw.value
        val travel = max(-M.MAX_SKEW, min(M.MAX_SKEW, v * M.PROJECT))
        val to = nearestFace(cur + travel)
        yaw.run(Step.Spring(to, M.SETTLE, velocity = v, done = { land(to) }))
        pitch.run(Step.Spring(0.0, M.TILT, velocity = -vy * M.PITCH_PER_PT))
    }

    private fun holdStart() {
        // the numeral presses into the glass while the finger holds it, and pops back out on release
        holdV = true
        depth.run(Step.Spring(M.HOLD_DEPTH, M.SNAP))
        haptics?.press()
    }

    private fun holdFinalize() {
        if (!holdV) return
        holdV = false
        depth.run(Step.Spring(1.0, M.RISE))
        popPlus.set(0.6)
        popPlus.run(Step.Delay(0.001), Step.Delay(0.04), Step.Spring(1.0, M.POP))
        popHr.set(0.6)
        popHr.run(Step.Delay(0.001), Step.Delay(0.08), Step.Spring(1.0, M.POP))
        haptics?.press()
    }

    /** a finger lands on the badge (gesture handler's onBegin) */
    fun badgeDown() {
        lastTx = 0.0
        lastTy = 0.0
        ended = false
        panActive = false
        holdFailed = false
        downActive = true
        begin()
        downClock = clockT
    }

    /** the finger moved: its translation from where it landed, dp */
    fun badgeMove(tx: Double, ty: Double) {
        if (!downActive) return
        if (!panActive && hypot(tx, ty) >= 2.0) panActive = true
        if (panActive) {
            // 1:1: yaw 0.6 degrees per pt, pitch 0.35 (clamped at 28 degrees with a rubber band)
            val dx = tx - lastTx
            val dy = ty - lastTy
            yaw.set(yaw.value + dx * M.YAW_PER_PT)
            pitch.set(M.rubber(pitch.value + dy * M.PITCH_PER_PT, M.PITCH_MAX, M.PITCH_RUBBER))
        }
        lastTx = tx
        lastTy = ty
    }

    /** the finger lifted (vx, vy in dp/s) or the gesture was cancelled */
    fun badgeUp(vx: Double, vy: Double, cancelled: Boolean) {
        if (!downActive) return
        downActive = false
        if (panActive && !cancelled) {
            ended = true
            settle(vx, vy)
        }
        if (!ended) {
            ended = true
            settle(0.0, 0.0)
        }
        holdFinalize()
        finish()
    }

    /** the screen reader's activate: flip to the other face */
    fun a11yFlip() {
        val cur = yaw.value
        val to = nearestFace(cur) + 180
        isPlaying = false
        lastTouch = clockT
        if (reduce) {
            fade.run(Step.Timing(0.0, 0.1, done = { yaw.set(to) }), Step.Timing(1.0, 0.1))
            onFace(faceOf(to))
        } else {
            yaw.run(Step.Spring(to, M.SETTLE, done = { land(to) }))
        }
    }

    companion object {
        const val HOLD_MAX_DIST = 18.0

        fun fmt(secs: Double): String {
            val sec = max(0, Math.round(secs).toInt())
            val h = sec / 3600
            val m = (sec % 3600) / 60
            return "${h}h ${m.toString().padStart(2, '0')}m"
        }
    }
}
