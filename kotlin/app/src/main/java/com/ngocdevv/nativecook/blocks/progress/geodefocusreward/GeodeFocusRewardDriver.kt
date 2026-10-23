package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import kotlin.math.*

internal fun geodeFocusNow() = System.nanoTime() / 1_000_000_000.0
internal enum class GeodeFocusRewardEventKind { Hold, Replay, Release }
internal data class GeodeFocusRewardEvent(val stamp: Double, val from: GeodeFocusRewardFrame, val kind: GeodeFocusRewardEventKind,
    val opens: Boolean = false, val velocity: Double = 0.0, val cancelled: Boolean = false, val yawTarget: Double = 0.0, var passedTwo: Boolean = false) {
    fun sample(age: Double) = when (kind) {
        GeodeFocusRewardEventKind.Hold -> from
        GeodeFocusRewardEventKind.Replay -> GeodeFocusRewardMath.replay(from, age)
        GeodeFocusRewardEventKind.Release -> GeodeFocusRewardMath.released(from, opens, velocity, age, cancelled, yawTarget)
    }
}
internal class GeodeFocusRewardDriver {
    var epoch = geodeFocusNow()
    var capture = GeodeFocusRewardMath.rest
    var press: Double? = null
    var dx = 0.0; var dy = 0.0
    var yaw = GeodeFocusRewardYaw()
    var event: GeodeFocusRewardEvent? = null
    var kept = false; var collapsed = false; var fractureTicked = false; var previousProgress = 0.0
    var quietReplay: Double? = null
    var displayed = capture; var displayedAt = -100.0
    private var collapseStamp = -100.0; private var collapseFrom = 1.0; private var collapseTarget = 1.0; private var collapseDuration = .24
    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): GeodeFocusRewardFrame {
        if (reduced) return GeodeFocusRewardMath.reduced
        if (preview != null) return GeodeFocusRewardMath.authored(max(0.0, preview))
        press?.let { return GeodeFocusRewardMath.held(capture, max(0.0, now - it), dx, dy, yaw.sample(now).first) }
        event?.let { val age = max(0.0, now - it.stamp); return if (age < 2) it.sample(age) else GeodeFocusRewardMath.recovering(it.sample(2.0), age - 2) }
        return if (autoPlay) GeodeFocusRewardMath.authored(max(0.0, now - epoch)) else GeodeFocusRewardMath.rest
    }
    fun visible(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?) = if (now - displayedAt < .2) displayed else frame(now, autoPlay, reduced, preview)
    fun setCollapsed(value: Boolean, now: Double, duration: Double) {
        collapseFrom = rewardOpacity(now); collapseTarget = if (value) 0.0 else 1.0; collapseStamp = now; collapseDuration = duration; collapsed = value
    }
    fun rewardOpacity(now: Double): Double {
        val q = GeodeFocusRewardMath.bezier((now - collapseStamp) / collapseDuration, 0.0, 0.0, .58, 1.0)
        return collapseFrom + (collapseTarget - collapseFrom) * q
    }
    fun quietOpacity(now: Double): Double {
        val start = quietReplay ?: return 1.0; val age = max(0.0, now - start)
        val down = GeodeFocusRewardMath.bezier(age / .09, 0.0, 0.0, .58, 1.0)
        val up = GeodeFocusRewardMath.bezier((age - .09) / .09, 0.0, 0.0, .58, 1.0)
        return 1 - .22 * down * (1 - up)
    }
    fun fading(now: Double) = now - collapseStamp < collapseDuration || quietReplay?.let { now - it < .18 } == true
    fun finish(now: Double, velocity: Double, cancelled: Boolean, autoPlay: Boolean, reduced: Boolean, preview: Double?) {
        val current = visible(now, autoPlay, reduced, preview)
        val target = (capture.hero.manualYaw + .09 * dx).coerceIn(-12.0, 12.0)
        press = null; dx = 0.0; dy = 0.0; yaw = GeodeFocusRewardYaw()
        event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Release, !cancelled && current.hero.p >= .62, velocity, cancelled, target)
    }
    fun complete(now: Double, reduced: Boolean): Boolean {
        var changed = false
        event?.let { e ->
            if (press == null && !e.passedTwo && now - e.stamp >= 2) { e.passedTwo = true; if (collapsed) setCollapsed(false, now, if (reduced) .18 else .20); changed = true }
            if (press == null && now - e.stamp >= 2.65) { epoch = e.stamp + 2.65; event = null; kept = false; fractureTicked = false; changed = true }
        }
        quietReplay?.let { if (now - it >= .18) { quietReplay = null; changed = true } }
        return changed
    }
    fun nextDeadline(): Double? = listOfNotNull(event?.let { it.stamp + if (it.passedTwo) 2.65 else 2.0 }, quietReplay?.plus(.18)).minOrNull()
    fun clear(now: Double) {
        press = null; event = null; quietReplay = null; dx = 0.0; dy = 0.0; yaw = GeodeFocusRewardYaw()
        collapsed = false; kept = false; fractureTicked = false; collapseFrom = 1.0; collapseTarget = 1.0; collapseStamp = -100.0; epoch = now
    }
}
