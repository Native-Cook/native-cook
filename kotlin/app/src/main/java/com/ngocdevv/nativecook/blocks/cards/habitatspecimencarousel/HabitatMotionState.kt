package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private const val LOOK_DURATION = 520f
private const val LOOK_RATIO = 0.9f
private const val NEVER = Double.MAX_VALUE

/**
 * Everything the carousel's motion is made of, stepped once a frame by one clock (useHabitatMotion in
 * HabitatMotion.ts). The values are plain fields read in draw and layer lambdas; `tick` changes every
 * frame so those lambdas run again without the screen recomposing.
 */
internal class HabitatMotion(
    var autoPlay: Boolean,
    val previewTime: Float?,
    previewReducedMotion: Boolean?,
    systemReduced: Boolean,
    initialGap: Float,
    initialWidth: Float,
) {
    /** Changes every frame and on every gesture; read it in a draw or graphicsLayer lambda to follow the motion. */
    var tick by mutableLongStateOf(0L)
        private set

    var gap = initialGap
    /** Screen width, for the head's look towards the finger. */
    var width = initialWidth
    var onSignature: () -> Unit = {}
    var onLand: () -> Unit = {}
    var onPet: () -> Unit = {}

    var reduced = previewReducedMotion ?: systemReduced
    private val initialTime = if (previewTime == null) 0f else clamp(previewTime / LOOP) % 1f

    val rawPosition = HabitatAnim(timelinePosition(initialTime))
    /** The demo timeline, 0..1. */
    var timeline = initialTime
    /** The ambient loop for idle life, 0..1 (frozen under Reduced Motion). */
    var time = initialTime
    val influence = HabitatAnim(0f)
    val position get() = wrapPosition(rawPosition.value)
    private var dragging = false
    /** True once a person has taken over from the demo; the hint leaves for good when it first turns on. */
    var manual = false
        private set
    var touchedOnce by mutableStateOf(false)
        private set
    private var dragOrigin = 0f
    var now = 0.0
        private set
    private var resumeAt = 0.0
    private var active = false
    private var resumePending = false
    var selectedIndex by mutableIntStateOf(nearestIndex(timelinePosition(initialTime)))
        private set
    private val initialIndex = nearestIndex(timelinePosition(initialTime))

    /** Signature progress 0..1 and the specimen performing it; both 0/idle outside a move. */
    val power = HabitatAnim(if (previewTime == null) 0f else timelineSignature(initialTime))
    var powerIndex = initialIndex
    /** Seconds since the move began, running past it while its particles land (FX_S); -1 when idle. */
    val fx = HabitatAnim(if (previewTime == null) -1f else timelineEffect(initialTime))
    /** After a person's move the demo resumes mid-slot; its own move's afterlife must not replay then. */
    private var fxHeld = false
    private var autoTab = timelineTab(initialTime)
    var demoTab by mutableIntStateOf(timelineTab(initialTime))
        private set
    /** Anticipation while a finger rests on the specimen, 0..1. */
    val press = HabitatAnim(0f)
    /** Where the head looks, -1 (left) .. 1 (right), springing back to 0. */
    val look = HabitatAnim(0f)
    /** Petting under a hold: amount 0..1, seconds since it began, and the clock at release. */
    val pet = HabitatAnim(0f)
    var petClock = 0f
    var petUntil = -1f
    /** One landing squash (0 -> 1 over LAND_MS) for the specimen that just arrived. */
    val landing = HabitatAnim(1f)
    var landingIndex = initialIndex
    private var landed = initialIndex
    /** The finger on the stage (stage points): x, y, velocity x, y, down; and the last tap: x, y, frame ms. */
    val touch = floatArrayOf(-999f, -999f, 0f, 0f, 0f)
    val tapAt = doubleArrayOf(0.0, 0.0, -1.0)
    private var touchClock = 0.0

    /** The key light over the baked volume (x, y, amount) and the phone's tilt that leans it. */
    val light = floatArrayOf(-0.5f, -0.6f, 1f)
    val tilt = floatArrayOf(0f, 0f)
    private var restX = 0f
    private var restY = 0f
    private var restKnown = false
    /** The gravity sensor in m/s^2 (0, 0 when there is none). */
    var gravityX = 0f
    var gravityY = 0f

    /**
     * The habitat front's slow current, held still outside a crossing (where it has no effect) so
     * resting art never redraws.
     */
    val frontPhase: Float
        get() {
            val p = position
            return if (p - floor(p) > 0.0005f && !reduced) (PI * 2).toFloat() * (time % 1f) else 0f
        }

    private val anims = ArrayList<HabitatAnim>(listOf(rawPosition, influence, power, fx, press, look, pet, landing))

    /** Lets another part of the screen (the tab underline, the panel reveal) be stepped by the same clock. */
    fun clocked(anim: HabitatAnim): HabitatAnim { anims.add(anim); return anim }

    init {
        if (previewTime != null) {
            rawPosition.set(timelinePosition(clamp(previewTime / LOOP)))
            power.set(timelineSignature(initialTime))
            powerIndex = timelinePerformer(initialTime)
            fx.set(timelineEffect(initialTime))
            autoTab = timelineTab(initialTime)
        }
        updateLight()
    }

    /** Read in a draw or graphicsLayer lambda to run it again each frame. */
    fun observe(): Long = tick

    private fun bump() { tick += 1 }

    // ---- Focus and the app's lifecycle ----

    private fun suspendManual() {
        rawPosition.cancel()
        influence.cancel(); influence.set(0f)
        dragging = false
        resumePending = true
    }

    private fun restoreManual() {
        resumePending = true
        if (!manual) return
        rawPosition.spring(jsRound(rawPosition.value), 400f, 1f, always = reduced)
    }

    /** The screen is shown and the app is in the foreground (useFocusEffect + AppState). */
    fun setActive(next: Boolean) {
        if (active == next) return
        active = next
        if (next) restoreManual() else suspendManual()
    }

    fun setReduced(next: Boolean) {
        reduced = next
        if (next) { influence.cancel(); influence.set(0f) }
    }

    // ---- The frame ----

    /** One frame of the clock: `timestampMs` since the screen appeared, `dtMs` since the previous frame. */
    fun frame(timestampMs: Double, dtMs: Float) {
        val dt = min(dtMs, 100f) / 1000f
        for (anim in anims) anim.step(dt)
        if (active && previewTime == null) tick(timestampMs, dtMs)
        updateKeyLight(dtMs)
        react()
        bump()
    }

    private fun tick(timestamp: Double, sincePrevious: Float) {
        val elapsed = min(sincePrevious, 64f) / (LOOP * 1000f)
        if (!reduced) time = (time + elapsed) % 1f
        now = timestamp
        // Hearts follow the wall clock (slow frames must not slow the stream), within reason.
        val seconds = min(sincePrevious, 250f) / 1000f
        if (pet.value > 0f || petClock < petUntil + 1.4f) petClock += seconds
        if (!autoPlay) return
        if (resumePending) {
            resumePending = false
            if (manual) resumeAt = timestamp + 2000
            return
        }
        if (manual) {
            if (dragging || timestamp < resumeAt) return
            val index = nearestIndex(rawPosition.value)
            // Resume after this specimen's demo move, so a person never sees it replayed out of turn.
            rawPosition.set(index.toFloat()); timeline = SNAP_TIMES[index] + 2.4f / LOOP; manual = false; fxHeld = true
            return
        }
        val p = (timeline + elapsed) % 1f
        timeline = p; rawPosition.set(timelinePosition(p))
        val tab = timelineTab(p)
        if (tab != autoTab) autoTab = tab
        if (reduced) { power.set(0f); fx.set(-1f); return }
        power.set(timelineSignature(p))
        val effect = timelineEffect(p)
        // A person's move keeps its performer and clock until the demo's next move begins.
        if (fxHeld) { if (effect < 0) fxHeld = false }
        else {
            if (effect != fx.value) fx.set(effect)
            powerIndex = timelinePerformer(p)
        }
    }

    private fun react() {
        if (demoTab != autoTab) demoTab = autoTab
        val index = nearestIndex(rawPosition.value)
        if (index != selectedIndex) selectedIndex = index
        // A specimen lands when its hop ends (8% before the slot), whether the demo, a fling or a slow drag brought it.
        val p = rawPosition.value
        val near = nearestIndex(p)
        val landedNow = if (abs(specimenDistance(near, p)) < 0.08f) near else -1
        if (landedNow >= 0 && landedNow != landed) {
            landed = landedNow
            if (reduced) return
            landingIndex = landedNow
            landing.set(0f)
            landing.timing(1f, LAND_MS.toFloat())
            if (manual) onLand()
        }
    }

    private fun updateKeyLight(dtMs: Float) {
        if (!(reduced || (gravityX == 0f && gravityY == 0f))) {
            val gx = gravityX / 9.81f
            val gy = gravityY / 9.81f
            if (!restKnown) { restX = gx; restY = gy; restKnown = true }
            val a = min(1f, dtMs / 1000f / 2.5f)
            val baseX = restX
            val baseY = restY
            restX = baseX + (gx - baseX) * a
            restY = baseY + (gy - baseY) * a
            val targetX = max(-1f, min(1f, (gx - baseX) * 3))
            val targetY = max(-1f, min(1f, (baseY - gy) * 3))
            tilt[0] = tilt[0] + (targetX - tilt[0]) * 0.18f
            tilt[1] = tilt[1] + (targetY - tilt[1]) * 0.18f
        }
        updateLight()
    }

    private fun updateLight() {
        val still = reduced
        val drift = if (still) 0f else sin(time * (PI * 2).toFloat() * 2f)
        val tx = if (still) 0f else tilt[0]
        val ty = if (still) 0f else tilt[1]
        light[0] = -0.5f + drift * 0.12f + tx * 0.55f
        light[1] = -0.6f + ty * 0.45f
        light[2] = 1f
    }

    // ---- Gestures: a pan, a tap and a hold race for each touch (Gesture.Race(pan, hold, tap)) ----

    private fun runnable() = active

    /** The finger lands: the pan's onBegin, the tap's onBegin and the touch tracker's onTouchesDown. */
    fun touchDown(x: Float, y: Float) {
        touch[0] = x; touch[1] = y; touch[2] = 0f; touch[3] = 0f; touch[4] = 1f
        touchClock = now
        if (runnable()) {
            rawPosition.cancel(); influence.cancel()
            setManual(true); dragging = true; dragOrigin = rawPosition.value; resumeAt = NEVER
            // Feedback lands with the finger: the specimen gathers itself and turns towards the touch.
            if (!reduced) {
                press.timing(1f, 140f, PRESS_IN)
                look.spring(clamp((x - width / 2) / (width * 0.4f), -1f, 1f), LOOK_DURATION, LOOK_RATIO)
            }
        }
        bump()
    }

    /** onTouchesMove: the finger's position and a smoothed velocity for the particle world. */
    fun touchMove(x: Float, y: Float) {
        val elapsed = max(8.0, now - touchClock).toFloat() / 1000f
        val vx = touch[2] + ((x - touch[0]) / elapsed - touch[2]) * 0.5f
        val vy = touch[3] + ((y - touch[1]) / elapsed - touch[3]) * 0.5f
        touch[0] = x; touch[1] = y; touch[2] = vx; touch[3] = vy; touch[4] = 1f
        touchClock = now
    }

    fun touchUp() { touch[4] = 0f }

    /** The pan became active or moved: `translationX` since the touch began, its velocity, and the finger's x on the stage. */
    fun panUpdate(translationX: Float, velocityX: Float, x: Float) {
        if (!dragging || !active) return
        // Choose Your Ball's rule: one wipe, one specimen. The drag tracks the finger 1:1 up to one
        // slot from where it began, then resists asymptotically instead of stopping dead.
        val home = jsRound(dragOrigin)
        val drift = dragOrigin - translationX / gap - home
        val over = abs(drift) - PAN_REACH
        rawPosition.set(if (over <= 0) home + drift else home + sign(drift) * (PAN_REACH + over / (1 + over * PAN_RESIST)))
        influence.set(if (reduced) 0f else gestureInfluence(translationX, velocityX, gap))
        if (!reduced) look.spring(clamp((x - width / 2) / (width * 0.4f), -1f, 1f), LOOK_DURATION, LOOK_RATIO)
        bump()
    }

    fun panEnd(velocityX: Float) {
        if (!dragging) return
        val velocity = -velocityX / gap
        val home = jsRound(dragOrigin)
        val projected = rawPosition.value + velocity * FLING_PROJECTION_S
        val target = jsRound(max(home - PAN_REACH, min(home + PAN_REACH, projected)))
        // The same landing spring as the ball: settles without a visible bounce but overshoots once.
        rawPosition.spring(target, 18f, 1f, 130f, velocity, always = reduced)
        influence.spring(0f, 650f, 1f, always = reduced)
        dragging = false; resumeAt = now + 2000
    }

    /** The pan's onFinalize, whatever ended the touch. */
    fun panFinalize() {
        look.spring(0f, LOOK_DURATION, LOOK_RATIO)
        if (!dragging) return
        rawPosition.spring(jsRound(rawPosition.value), 400f, 1f, always = reduced)
        influence.spring(0f, 650f, 1f, always = reduced)
        dragging = false; resumeAt = now + 2000
    }

    /** The tap's onFinalize (it failed, or ended). */
    fun tapFinalize() {
        press.timing(0f, 200f, PRESS_IN)
        if (pet.value == 0f) look.spring(0f, LOOK_DURATION, LOOK_RATIO)
    }

    /** A tap on a resting specimen performs its signature move; a drag always wins the race. */
    fun tapEnd(x: Float, y: Float) {
        if (!active || reduced) return
        // The release sends a ring of air through the particles.
        tapAt[0] = x.toDouble(); tapAt[1] = y.toDouble(); tapAt[2] = now
        val current = rawPosition.value
        val index = nearestIndex(current)
        if (abs(specimenDistance(index, current)) > 0.04f) return
        val running = power.value
        if (running > 0f && running < 0.72f) return
        rawPosition.cancel(); rawPosition.set(jsRound(current))
        setManual(true); dragging = false; resumeAt = now + SIGNATURE_MS + 2000
        powerIndex = index
        power.set(0f)
        power.timing(1f, SIGNATURE_MS.toFloat())
        fx.set(0f)
        fx.timing(FX_S, FX_S * 1000f)
        onSignature()
    }

    /** A hold pets the specimen: it squints, wiggles and hearts rise for as long as the finger stays. */
    fun holdStart() {
        if (!active) return
        val current = rawPosition.value
        if (abs(specimenDistance(nearestIndex(current), current)) > 0.04f) return
        rawPosition.cancel(); rawPosition.set(jsRound(current))
        setManual(true); dragging = false; resumeAt = NEVER
        petClock = 0f; petUntil = Float.MAX_VALUE
        pet.timing(1f, 220f, PRESS_IN)
        onPet()
    }

    fun holdFinalize() {
        // Only a hold that became a pet releases one; every failed hold (each tap) ends here too.
        if (petUntil <= 1e9f) return
        petUntil = petClock
        pet.timing(0f, 260f, PRESS_IN)
        look.spring(0f, LOOK_DURATION, LOOK_RATIO)
        resumeAt = now + 2000
    }

    /** Any other control the person touches keeps the demo paused for `ms`. */
    fun hold(ms: Float) {
        if (!active) return
        if (!manual) { rawPosition.cancel(); rawPosition.set(jsRound(rawPosition.value)) }
        setManual(true); resumeAt = now + ms
    }

    /** Go to specimen `index` by the shortest way round. */
    fun select(index: Int) {
        if (!active) return
        rawPosition.cancel()
        val current = rawPosition.value
        val target = current + specimenDistance(index, current)
        influence.spring(0f, 650f, 1f, always = reduced)
        setManual(true); dragging = false; resumeAt = now + 2000
        rawPosition.spring(target, 18f, 1f, 130f, always = reduced)
    }

    private fun setManual(value: Boolean) {
        manual = value
        if (value) touchedOnce = true
    }

    /** True while a drag holds the carousel. */
    val isDragging get() = dragging
}
