package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone.TrophySavingsMilestoneMotion.Mode

/** Main-thread owner. Only the drawing snapshot changes each frame, never the screen composition. */
internal class TrophySavingsMilestoneRibbonState {
    data class Drawing(val progress: Double = 0.0, val pose: Double = 0.0,
        val scene: TrophySavingsMilestoneCelebration.Frame = TrophySavingsMilestoneCelebration.rest())
    var autoPlay = false
    var previewTime: Double? = null
    var reduced = false
    var active by mutableStateOf(true)
    var overlay by mutableStateOf(false); private set
    var generation by mutableIntStateOf(0); private set
    var reducedFeedback by mutableIntStateOf(0); private set
    var ticking by mutableStateOf(false); private set
    var resumeDeadline by mutableStateOf<Double?>(null); private set
    val presented = mutableStateOf(Drawing())
    private var motion = TrophySavingsMilestoneMotion.State()
    private var sceneEpoch = now()
    val canActivate get() = active && !overlay && previewTime == null
    val canDrag get() = canActivate && !reduced
    val dragging get() = motion.mode == Mode.DRAG
    val impactAllowed get() = canDrag && motion.mode == Mode.EVENT && motion.eventUserPeak && motion.hapticSent

    fun reset() {
        generation++; resumeDeadline = null; reducedFeedback = 0
        val at = now()
        val mode = if (autoPlay && active && !reduced && previewTime == null && !overlay) Mode.LOOP else if (overlay) Mode.PAUSED else Mode.IDLE
        val p = if (reduced) 0.0 else previewTime?.let { TrophySavingsMilestoneMotion.loop(it) } ?: 0.0
        val pose = if (reduced) 0.0 else previewTime?.let { TrophySavingsMilestoneMotion.loopPose(it) } ?: 0.0
        motion = TrophySavingsMilestoneMotion.State(mode, generation, p, pose, at)
        presented.value = Drawing(p, pose, previewTime?.let { TrophySavingsMilestoneCelebration.gallery(it, generation, reduced) }
            ?: TrophySavingsMilestoneCelebration.rest(generation, reduced))
        sceneEpoch = at
        ticking = mode == Mode.LOOP
    }
    fun tick(at: Double = now()): Boolean {
        if (!canDrag || !ticking) return false
        if (motion.mode == Mode.LOOP) {
            val scene = TrophySavingsMilestoneCelebration.gallery(at - motion.epoch, generation)
            presented.value = Drawing(scene.progress, TrophySavingsMilestoneMotion.loopPose(at - motion.epoch), scene)
            sceneEpoch = at - scene.clock
            return false
        }
        if (motion.mode != Mode.EVENT && motion.mode != Mode.DRAG) return false
        val old = presented.value.scene
        val p = TrophySavingsMilestoneMotion.presentedProgress(motion, at)
        val scene = TrophySavingsMilestoneCelebration.advance(old, max(old.clock, at - sceneEpoch), p, generation)
        presented.value = Drawing(p, TrophySavingsMilestoneMotion.presentedPose(motion, at), scene)
        if (motion.mode == Mode.DRAG) return false
        val age = max(0.0, at - motion.eventStarted)
        val impact = motion.eventUserPeak && !motion.hapticSent && age >= TrophySavingsMilestoneMotion.peakTime(motion)
        if (impact) motion = motion.copy(hapticSent = true)
        val end = TrophySavingsMilestoneMotion.endTime(motion)
        val effectEnd = max(scene.contactStart, scene.wakeStarts.maxOrNull() ?: -1.0) + TrophySavingsMilestoneCelebration.SETTLE * scene.cadence
        if (age >= end && scene.clock >= effectEnd) {
            resumeDeadline = motion.eventStarted + max(2.0, end)
            motion = motion.copy(mode = Mode.WAITING, progress = 0.0, pose = 0.0, direction = 0.0)
            presented.value = Drawing(scene = TrophySavingsMilestoneCelebration.rest(generation))
            ticking = false
        }
        return impact
    }
    fun resumeGallery(token: Int, deadline: Double) {
        if (generation != token || motion.mode != Mode.WAITING || !canDrag) return
        resumeDeadline = null
        motion = motion.copy(mode = if (autoPlay) Mode.LOOP else Mode.IDLE, progress = 0.0, pose = 0.0, direction = 0.0, epoch = deadline)
        presented.value = Drawing(scene = TrophySavingsMilestoneCelebration.rest(generation))
        sceneEpoch = deadline; ticking = autoPlay
    }
    fun demonstrate() {
        if (!canActivate) return
        if (reduced) { reducedFeedback++; return }
        val at = now()
        if (motion.mode == Mode.EVENT && motion.eventDemo && at - motion.eventStarted < TrophySavingsMilestoneMotion.peakTime(motion) + TrophySavingsMilestoneMotion.PEAK_HOLD) return
        startEvent(TrophySavingsMilestoneMotion.presentedProgress(motion, at), true, true, at)
    }
    fun drag(translation: Double, velocity: Double = 0.0) {
        if (!canDrag) return
        val at = now()
        if (motion.mode != Mode.DRAG) {
            val p = TrophySavingsMilestoneMotion.presentedProgress(motion, at)
            val raw = TrophySavingsMilestoneMotion.presentedPose(motion, at)
            val pose = if (p <= 0.000001 && raw >= 1.999999) 0.0 else raw
            var direction = if (p > 0) { if (motion.direction == 0.0) 1.0 else motion.direction } else 0.0
            if (direction == 0.0 && abs(translation) > 0.5) direction = if (translation < 0) -1.0 else 1.0
            generation++; resumeDeadline = null
            motion = motion.copy(mode = Mode.DRAG, generation = generation, progress = p, pose = pose,
                contactOrigin = p, contactPoseOrigin = pose, contactDirection = direction, contactVelocity = 0.0,
                translationOrigin = translation, direction = if (direction != 0.0) direction else motion.direction, eventUserPeak = false)
            preserveOrResetScene(p)
            sceneEpoch = at - presented.value.scene.clock
            presented.value = Drawing(p, pose, presented.value.scene)
            ticking = true
        }
        var direction = motion.contactDirection
        if (direction == 0.0 && abs(translation) > 0.5) direction = if (translation < 0) -1.0 else 1.0
        val p = TrophySavingsMilestoneMotion.clamp(motion.contactOrigin + direction * (translation - motion.translationOrigin) / 120)
        val pose = if (motion.contactPoseOrigin >= 1) 2 - p else p
        motion = motion.copy(progress = p, pose = pose, contactDirection = direction,
            contactVelocity = (direction * velocity / 120).coerceIn(-4.0, 4.0), direction = if (direction != 0.0) direction else motion.direction)
        presented.value = Drawing(p, pose, presented.value.scene)
    }
    fun finishContact(cancelled: Boolean) {
        if (!dragging || !canDrag) return
        startEvent(motion.progress, false, !cancelled && motion.progress >= 0.985, now(), if (cancelled) 0.0 else motion.contactVelocity)
    }
    fun updateOverlay(visible: Boolean) {
        if (overlay == visible) return
        if (visible) {
            val at = now(); val p = if (reduced) 0.0 else TrophySavingsMilestoneMotion.presentedProgress(motion, at)
            val pose = if (reduced) 0.0 else TrophySavingsMilestoneMotion.presentedPose(motion, at)
            generation++; overlay = true; resumeDeadline = null; ticking = false
            motion = motion.copy(generation = generation, mode = Mode.PAUSED, progress = p, pose = pose, eventUserPeak = false)
            presented.value = Drawing(p, pose, presented.value.scene.copy(progress = p))
            sceneEpoch = 0.0
        } else {
            overlay = false
            if (!canDrag) return
            startEvent(motion.progress, false, false, now())
        }
    }
    private fun preserveOrResetScene(p: Double) {
        if (p <= 0.000001 && TrophySavingsMilestoneCelebration.isSettled(presented.value.scene))
            presented.value = presented.value.copy(scene = TrophySavingsMilestoneCelebration.rest(generation))
    }
    private fun startEvent(p: Double, demo: Boolean, userPeak: Boolean, at: Double, velocity: Double = 0.0) {
        motion = TrophySavingsMilestoneMotion.beginEvent(motion, p, demo, userPeak, at, velocity)
        generation = motion.generation; resumeDeadline = null
        if (demo) preserveOrResetScene(motion.progress)
        sceneEpoch = at - presented.value.scene.clock
        presented.value = Drawing(motion.progress, motion.pose, presented.value.scene)
        ticking = true
    }
    companion object { fun now() = System.nanoTime() / 1_000_000_000.0 }
}
