package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

import kotlin.math.abs
import kotlin.math.exp

/**
 * Owns the small amount of state that is not part of the closed-form auto-play timeline.
 * All methods are called from Compose's UI coroutine or pointer scope.
 */
internal class PinDropLocationDriver(
    private val autoPlay: Boolean,
    private val reduced: Boolean,
    private val haptic: (Int) -> Unit,
) {
    private enum class Property { BEND, LON, LAT, FALL, ZOOM, PLACES, LIGHTS }
    private enum class Curve { DEFAULT, OUT_CUBIC, IN_CUBIC, EASE_IN_OUT, BEZIER_IN_OUT }

    private data class Tween(
        val property: Property,
        val from: Double,
        val to: Double,
        val start: Double,
        val delay: Double,
        val duration: Double,
        val curve: Curve,
        val done: (() -> Unit)? = null,
    ) {
        fun sample(now: Double): Pair<Double, Boolean> {
            val local = now - start - delay
            if (local <= 0.0) return from to false
            val p = if (duration <= 0.0) 1.0 else (local / duration).coerceIn(0.0, 1.0)
            val eased = when (curve) {
                Curve.DEFAULT -> if (p < 0.5) 2.0 * p * p else 1.0 - (-2.0 * p + 2.0) * (-2.0 * p + 2.0) / 2.0
                Curve.OUT_CUBIC -> PinDropLocationMotion.easeOut(p)
                Curve.IN_CUBIC -> PinDropLocationMotion.easeIn(p)
                Curve.EASE_IN_OUT -> if (p < 0.5) 2.0 * p * p else 1.0 - (-2.0 * p + 2.0) * (-2.0 * p + 2.0) / 2.0
                Curve.BEZIER_IN_OUT -> PinDropLocationMotion.bezier(p, 0.77, 0.0, 0.175, 1.0)
            }
            return (from + (to - from) * eased) to (p >= 1.0)
        }
    }

    var now = 0.0
        private set
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var manual = !autoPlay
    private var resumeAsked = false

    var allowed = false
        private set
    var dragging = false
        private set

    private var bend = 0.0
    private var lon = 0.0
    private var lat = 0.0
    private var fall = 1.0
    private var lights = 0.0
    private var zoom = 0.0
    private var places = 0.0
    private var landedAt = -1.0
    private var finger = 0.0
    private var fingerAt = 0.0
    private var fingerU = 0.0
    private var press = 0.0

    private var lon0 = 0.0
    private var lat0 = 0.0
    private var lonVelocity = 0.0
    private var decayStart = 0.0
    private var decayLast = 0.0
    private val tweens = mutableListOf<Tween>()

    val moving: Boolean
        get() = autoPlay || dragging || tweens.isNotEmpty() || abs(lonVelocity) > 0.0001 || (!reduced && landedAt >= 0.0 && lights > 0.0)

    private fun value(property: Property): Double = when (property) {
        Property.BEND -> bend
        Property.LON -> lon
        Property.LAT -> lat
        Property.FALL -> fall
        Property.ZOOM -> zoom
        Property.PLACES -> places
        Property.LIGHTS -> lights
    }

    private fun set(property: Property, value: Double) {
        when (property) {
            Property.BEND -> bend = value
            Property.LON -> lon = value
            Property.LAT -> lat = value
            Property.FALL -> fall = value
            Property.ZOOM -> zoom = value
            Property.PLACES -> places = value
            Property.LIGHTS -> lights = value
        }
    }

    private fun animate(
        property: Property,
        to: Double,
        duration: Double,
        curve: Curve,
        delay: Double = 0.0,
        done: (() -> Unit)? = null,
    ) {
        tweens.removeAll { it.property == property }
        tweens += Tween(property, value(property), to, now, delay, duration, curve, done)
    }

    private fun applyTweens() {
        val completed = ArrayList<Tween>()
        for (tween in tweens.toList()) {
            val sample = tween.sample(now)
            set(tween.property, sample.first)
            if (sample.second) completed += tween
        }
        for (tween in completed) {
            tweens.remove(tween)
            tween.done?.invoke()
        }
    }

    private fun copy(frame: PinDropLocationMotion.Frame) {
        bend = frame.bend
        lon = frame.lon
        lat = frame.lat
        fall = frame.fall
        lights = frame.lights
        zoom = frame.zoom
        places = frame.places
        allowed = frame.time >= 5.4 && frame.time < 8.0
        finger = frame.finger
        fingerAt = frame.fingerAt
        fingerU = frame.fingerU
        press = frame.press
        landedAt = if (frame.since >= 0.0) now - frame.since else -1.0
    }

    fun frame(): PinDropLocationMotion.Frame {
        val since = if (landedAt >= 0.0) now - landedAt else -1.0
        return PinDropLocationMotion.Frame(
            time = now,
            bend = bend,
            lon = lon,
            lat = lat,
            hover = 0.0,
            fall = fall.coerceIn(0.0, 1.0),
            since = since,
            lights = lights.coerceIn(0.0, 1.0),
            zoom = zoom.coerceIn(0.0, 1.0),
            places = places.coerceIn(0.0, 1.0),
            allowed = if (allowed) 1.0 else 0.0,
            finger = finger.coerceIn(0.0, 1.0),
            fingerAt = fingerAt,
            fingerU = fingerU,
            press = press.coerceIn(0.0, 1.0),
        )
    }

    fun step(dt: Double): PinDropLocationMotion.Frame {
        val delta = dt.coerceIn(0.0, 0.5)
        now += delta
        if (!manual && autoPlay) {
            tweens.clear()
            lonVelocity = 0.0
            copy(PinDropLocationMotion.frameAt(now - autoStart))
            return frame()
        }

        applyTweens()
        if (abs(lonVelocity) > 0.0001) {
            // This is Reanimated's rigidDecay integration: elapsed time is capped to 64 ms,
            // while the slope factor (0.1) uses elapsed time from the animation start each frame.
            val deltaDecay = (now - decayLast).coerceIn(0.0, 0.064)
            val elapsedMilliseconds = maxOf(0.0, now - decayStart) * 1000.0
            val velocity = lonVelocity * exp(-(1.0 - 0.994) * elapsedMilliseconds * 0.1)
            lon += velocity * deltaDecay
            lonVelocity = if (abs(velocity) < 1.0) 0.0 else velocity
            decayLast = now
        } else {
            lonVelocity = 0.0
        }
        if (landedAt >= 0.0) lights = lights.coerceIn(0.0, 1.0)

        if (autoPlay && !dragging && now - lastTouch >= 2.0) {
            if (!resumeAsked) {
                resumeAsked = true
                lonVelocity = 0.0
                if (zoom > 0.001) setAllow(false)
                animate(Property.LON, 0.0, 1.2, Curve.BEZIER_IN_OUT)
                animate(Property.LAT, 0.0, 1.2, Curve.BEZIER_IN_OUT)
                animate(Property.LIGHTS, 0.0, 1.2, Curve.BEZIER_IN_OUT)
                animate(Property.BEND, 0.0, 0.3, Curve.DEFAULT)
                if (fall < 0.999) dropIn(0.2)
            } else {
                val still = zoom < 0.001 && bend < 0.001 && abs(lon) < 0.001 && abs(lat) < 0.001 && fall > 0.999 && lights < 0.001
                if (still && now - lastTouch > 3.4) {
                    resumeAsked = false
                    autoStart = now
                    manual = false
                }
            }
        }
        return frame()
    }

    private fun land() {
        landedAt = now
        lights = 1.0
        haptic(0)
    }

    private fun dropIn(delay: Double) {
        if (reduced) {
            fall = 1.0
            land()
            return
        }
        animate(Property.FALL, 1.0, 0.42, Curve.IN_CUBIC, delay, ::land)
    }

    private fun takeOver() {
        lastTouch = now
        resumeAsked = false
        if (!manual) {
            manual = true
            finger = 0.0
            press = 0.0
            if (bend > 0.001 && !dragging) animate(Property.BEND, 0.0, 0.4, Curve.DEFAULT)
            if (fall < 0.999 && !dragging) dropIn(0.2)
            if (zoom > 0.001 && zoom < 0.999) setAllow(zoom > 0.5)
        }
    }

    fun setAllow(open: Boolean) {
        allowed = open
        val timing = if (reduced) 0.2 else 0.9
        animate(Property.ZOOM, if (open) 1.0 else 0.0, timing, if (reduced) Curve.EASE_IN_OUT else Curve.BEZIER_IN_OUT)
        animate(Property.PLACES, if (open) 1.0 else 0.0, if (reduced) timing else if (open) 0.9 else 0.3, if (reduced) Curve.EASE_IN_OUT else Curve.OUT_CUBIC)
    }

    fun allow() {
        if (allowed) return
        takeOver()
        setAllow(true)
        haptic(2)
    }

    fun beginMap() { takeOver() }

    fun accessibilityShift(direction: Int) {
        takeOver()
        animate(Property.LON, lon + direction * 0.6, 0.4, Curve.EASE_IN_OUT)
    }

    fun startPan() {
        if (dragging) return
        takeOver()
        if (zoom > 0.01) setAllow(false)
        tweens.removeAll { it.property == Property.BEND || it.property == Property.FALL || it.property == Property.LON || it.property == Property.LAT }
        lonVelocity = 0.0
        dragging = true
        lon0 = lon
        lat0 = lat
        if (!reduced) {
            animate(Property.BEND, 1.0, 0.38, Curve.OUT_CUBIC)
            animate(Property.FALL, 0.0, 0.34, Curve.IN_CUBIC)
            haptic(1)
        }
    }

    fun updatePan(translationX: Double, translationY: Double, radius: Double) {
        if (!dragging) return
        lastTouch = now
        lon = lon0 - translationX / radius.coerceAtLeast(1.0)
        lat = (lat0 + translationY / radius.coerceAtLeast(1.0)).coerceIn(-0.9, 0.9)
    }

    fun finishPan(velocityX: Double, radius: Double, cancelled: Boolean) {
        if (!dragging) return
        if (cancelled) {
            if (!reduced && fall < 0.999 && bend > 0.5) {
                animate(Property.BEND, 0.0, 0.4, Curve.DEFAULT)
                dropIn(0.3)
            } else {
                dropIn(0.0)
            }
        } else {
            if (!reduced) {
                lonVelocity = -velocityX / radius.coerceAtLeast(1.0)
                decayStart = now
                decayLast = now
            }
            animate(Property.BEND, 0.0, if (reduced) 0.001 else 0.52, if (reduced) Curve.DEFAULT else Curve.BEZIER_IN_OUT, if (reduced) 0.0 else 0.25)
            dropIn(if (reduced) 0.0 else 0.52)
            // RN's onFinalize runs after onEnd and intentionally overrides these tweens when
            // the globe had already lifted the pin (including a normal pointer-up).
            if (!reduced && fall < 0.999 && bend > 0.5) {
                animate(Property.BEND, 0.0, 0.4, Curve.DEFAULT)
                dropIn(0.3)
            }
        }
        dragging = false
        lastTouch = now
    }

    fun tapMap() {
        takeOver()
        if (!reduced && fall > 0.999 && bend < 0.01) hop()
    }

    private fun hop() {
        tweens.removeAll { it.property == Property.FALL }
        animate(Property.FALL, 0.82, 0.22, Curve.OUT_CUBIC) {
            animate(Property.FALL, 1.0, 0.26, Curve.IN_CUBIC, done = ::land)
        }
        haptic(1)
    }
}
