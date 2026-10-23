package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import kotlin.math.max
import kotlin.math.roundToInt

/** Android host for the single native state machine shared by demo and real interaction. */
internal class GlassPeakSimulation(autoPlay: Boolean, reducedMotion: Boolean) {
    internal val state = GlassPeakNativeMotion.create(autoPlay, reducedMotion)
    var frame = GlassPeakNativeMotion.toFrame(state).render()
        private set
    var onHaptic: ((GlassPeakHaptic) -> Unit)? = null
    var onAccessibilityValue: ((String) -> Unit)? = null
    private var presentationAge = 0.0
    private var seenLight = 0
    private var seenSelection = 0
    private var seenMedium = 0
    private var seenHeavy = 0
    private var seenAnnouncements = 0

    val period: Int get() = state.f.period
    val value: Int get() = state.f.value.roundToInt()
    val busy: Boolean get() = state.mode == "refresh" || state.mode == "opening" ||
        (state.mode == "fade" && state.fadePeriod == state.from.period)

    private fun reportedValue(nodeU: Double): Int {
        val period = GlassPeakNativeContract.periods[state.f.period]
        val value = when (state.mode) {
            "morph" -> period.value * (GlassPeakNativeContract.heightAt(
                GlassPeakNativeContract.curves[state.f.period], period.nodes[state.node],
            ) / GlassPeakNativeContract.heightAt(GlassPeakNativeContract.curves[state.f.period], period.nodes[2])).coerceAtMost(1.0)
            "rest" -> period.value * (GlassPeakNativeContract.heightAt(state.f.ys, nodeU) /
                max(.001, GlassPeakNativeContract.heightAt(state.f.ys, state.nodes[2]))).coerceAtMost(1.0)
            else -> state.f.value
        }
        return value.roundToInt()
    }

    fun step(delta: Double) {
        presentationAge += delta.coerceIn(0.0, .05)
        if (presentationAge >= .3) GlassPeakNativeMotion.stepDemo(state, delta)
        publish()
    }

    private fun publish() {
        val picture = GlassPeakNativeMotion.toFrame(state)
        picture.presented *= GlassPeakNativeMotion.easeOut(presentationAge / .3)
        frame = picture.render()
        if (state.light != seenLight) onHaptic?.invoke(GlassPeakHaptic.Light)
        if (state.selection != seenSelection) onHaptic?.invoke(GlassPeakHaptic.Selection)
        if (state.medium != seenMedium) onHaptic?.invoke(GlassPeakHaptic.Medium)
        if (state.heavy != seenHeavy) onHaptic?.invoke(GlassPeakHaptic.Heavy)
        seenLight = state.light
        seenSelection = state.selection
        seenMedium = state.medium
        seenHeavy = state.heavy
        if (state.announcements != seenAnnouncements) {
            val node = if (state.settling) state.nodes[state.node] else state.orbU
            val label = if (kotlin.math.abs(node - state.nodes[state.node]) < .002)
                arrayOf("point 1", "valley", "peak", "point 4")[state.node] else "between points"
            onAccessibilityValue?.invoke("${reportedValue(node)} percent, $label, ${GlassPeakPeriods[state.f.period].label}")
            seenAnnouncements = state.announcements
        }
    }

    fun contact(down: Boolean) = GlassPeakNativeMotion.contact(state, down)
    fun press(action: GlassPeakAction, down: Boolean, user: Boolean) = GlassPeakNativeMotion.press(state, action, down, user)
    fun request(action: GlassPeakAction, user: Boolean) = GlassPeakNativeMotion.request(state, action, user)
    fun fingerDown(x: Float, y: Float, user: Boolean) = GlassPeakNativeMotion.fingerDown(state, x.toDouble(), y.toDouble(), user)
    fun fingerMove(x: Float, y: Float, velocityX: Float, user: Boolean) {
        GlassPeakNativeMotion.fingerMove(state, x.toDouble(), y.toDouble(), velocityX.toDouble())
    }
    fun fingerUp(velocityX: Float, user: Boolean) = GlassPeakNativeMotion.fingerUp(state, velocityX.toDouble(), user)
    fun accessibilityAdjust(direction: Int) = GlassPeakNativeMotion.adjust(state, direction)
}
