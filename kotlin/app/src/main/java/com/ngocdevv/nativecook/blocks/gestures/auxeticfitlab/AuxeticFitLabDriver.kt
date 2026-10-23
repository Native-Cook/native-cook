package com.ngocdevv.nativecook.blocks.gestures.auxeticfitlab

import kotlin.math.max

internal fun auxeticFitLabNow() = System.nanoTime() / 1_000_000_000.0
internal enum class AuxeticFitLabOwner { Left, Right, Pinch }
internal data class AuxeticFitLabSession(val owner: AuxeticFitLabOwner, val generation: Int, val stamp: Double,
    val capture: AuxeticFitLabFrame, val anchor: Double, var coordinate: Double, var input: AuxeticFitLabInput,
    var lastMovement: Double? = null, var movementInput: AuxeticFitLabInput? = null)
internal data class AuxeticFitLabEvent(val stamp: Double, val held: AuxeticFitLabFrame, val release: AuxeticFitLabRelease?)

internal class AuxeticFitLabDriver {
    var epoch = auxeticFitLabNow()
    var generation = 0
    var session: AuxeticFitLabSession? = null
    var event: AuxeticFitLabEvent? = null
    var saved = false
    var reducedAngle = 42.0
    var quietSave: Double? = null
    var previousDetent = AuxeticFitLabDetent.Closed
    var hapticZone = AuxeticFitLabDetent.Closed
    var lastTick = -100.0
    var rendered = AuxeticFitLabMath.rest
    var renderedAt = -100.0

    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): AuxeticFitLabFrame {
        if (reduced) return AuxeticFitLabMath.staticFrame(reducedAngle)
        if (preview != null) return AuxeticFitLabMath.authored(max(0.0, preview))
        session?.let { return it.input.frame.frozen() }
        event?.let {
            val age = max(0.0, now - it.stamp)
            val held = it.release?.let { release -> AuxeticFitLabMath.released(release, minOf(age, 2.0)) } ?: it.held
            return if (age < 2) held else AuxeticFitLabMath.recovering(held, age - 2)
        }
        return if (autoPlay) AuxeticFitLabMath.authored(max(0.0, now - epoch)) else AuxeticFitLabMath.rest
    }
    fun displayed(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?) =
        if (!reduced && preview == null && now - renderedAt < .12) rendered else frame(now, autoPlay, reduced, preview)

    fun begin(owner: AuxeticFitLabOwner, anchor: Double, now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): Boolean {
        if (reduced || preview != null || session != null) return false
        val current = frame(now, autoPlay, reduced, preview)
        val shown = displayed(now, autoPlay, reduced, preview)
        val capture = shown.copy(angularVelocity = current.angularVelocity,
            left = shown.left.copy(offsetVelocityX = current.left.offsetVelocityX, scaleVelocity = current.left.scaleVelocity),
            right = shown.right.copy(offsetVelocityX = current.right.offsetVelocityX, scaleVelocity = current.right.scaleVelocity),
            arrowGainVelocity = current.arrowGainVelocity)
        generation++
        session = AuxeticFitLabSession(owner, generation, now, capture, anchor, anchor, AuxeticFitLabMath.begin(capture))
        event = null; quietSave = null
        hapticZone = AuxeticFitLabMath.nearestDetent(capture.angle, 0.0, previousDetent)
        return true
    }
    /** Equal coordinates never replace the last real movement's velocity or timestamp. */
    fun changed(owner: AuxeticFitLabOwner, coordinate: Double, velocity: Double, now: Double): Boolean {
        val s = session ?: return false
        if (s.owner != owner || coordinate == s.coordinate) return false
        val input = if (owner == AuxeticFitLabOwner.Pinch) AuxeticFitLabMath.pinch(s.capture, coordinate / s.anchor, velocity / s.anchor)
        else AuxeticFitLabMath.drag(s.capture, if (owner == AuxeticFitLabOwner.Left) AuxeticFitLabSide.Left else AuxeticFitLabSide.Right,
            coordinate - s.anchor, velocity)
        s.lastMovement = now; s.movementInput = input; s.coordinate = coordinate; s.input = input
        return true
    }
    fun finish(owner: AuxeticFitLabOwner, now: Double, cancelled: Boolean): Boolean {
        val s = session ?: return false
        if (s.owner != owner) return false
        val moved = s.lastMovement; val input = s.movementInput
        val velocity = if (moved != null && input != null) AuxeticFitLabReleaseVelocity.User(input.requestedWidthVelocity,
            input.actualAngularVelocity, max(0.0, now - moved))
        else AuxeticFitLabReleaseVelocity.Inherited(s.capture, max(0.0, now - s.stamp))
        val release = AuxeticFitLabMath.makeRelease(s.input, velocity, previousDetent, cancelled)
        release.target?.let { previousDetent = it }
        event = AuxeticFitLabEvent(now, s.input.frame.frozen(), release); session = null
        return true
    }
    fun save(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): Boolean {
        if (session != null) return false
        saved = true
        if (reduced) quietSave = now
        else event = AuxeticFitLabEvent(now, displayed(now, autoPlay, reduced, preview).frozen(), null)
        return true
    }
    fun adjust(direction: Int, now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): Boolean {
        if (session != null || preview != null) return false
        val current = displayed(now, autoPlay, reduced, preview)
        val angle = (current.angle + if (direction > 0) 5 else -5).coerceIn(4.0, 42.0)
        if (angle == current.angle) return false
        if (reduced) reducedAngle = angle else event = AuxeticFitLabEvent(now, AuxeticFitLabMath.staticFrame(angle), null)
        return true
    }
    fun tick(angle: Double, now: Double): Boolean {
        val zone = AuxeticFitLabMath.nearestDetent(angle, 0.0, hapticZone)
        if (zone == hapticZone) return false
        hapticZone = zone
        if (now - lastTick < .20) return false
        lastTick = now
        return true
    }
    fun complete(now: Double): Boolean {
        var changed = false
        event?.let { if (session == null && now - it.stamp >= 2.60) {
            event = null; saved = false; previousDetent = AuxeticFitLabDetent.Closed
            epoch = it.stamp + 2.60; changed = true
        } }
        quietSave?.let { if (now - it >= 2) { quietSave = null; saved = false; changed = true } }
        return changed
    }
    fun nextDeadline() = listOfNotNull(event?.stamp?.plus(2.60), quietSave?.plus(2)).minOrNull()
    fun clear(now: Double) {
        generation++; session = null; event = null; quietSave = null; saved = false
        previousDetent = AuxeticFitLabDetent.Closed; hapticZone = AuxeticFitLabDetent.Closed; epoch = now
    }
}
