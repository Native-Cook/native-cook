package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

/** One clock owns the pager, material debt, press feedback and button morph. */
internal class HoloMotion(val autoPlay: Boolean, val reduced: Boolean, val haptic: (Boolean) -> Unit) {
    var position = 0f
    var velocity = 0f
    var clock = 0f
    var elapsed = 0f
    var timeline = 0f
    var playing = autoPlay && !reduced
    var held = false
    var lastTouch = 0f
    var centre = 0
    var age = 900f
    var arrival = 0f
    var squeeze = 1f
    var buttonScale = 1f
    var pressed = -1
    var press = 1f
    var buttonHeld = false
    var buttonPress = 1f
    var added = 0
    var demoAdded = false
    var selection by mutableIntStateOf(0)
    var selected by mutableIntStateOf(0)
    val surge = FloatArray(4)
    private val owed = FloatArray(4)
    private var arrivedAt = -900f
    private var previous = 0f
    private var target = 0f
    private var from = 0f
    private var initialVelocity = 0f
    private var settleAt = 0f
    private var settling = false
    private var morphFrom = 0f
    private var labelFrom = 0f
    private var morphAt = 0f
    private var morphTarget = 0f
    var morph = 0f
    var labelMix = 0f
    private var pressFrom = 1f
    private var pressAt = 0f
    private var buttonFrom = 1f
    private var buttonAt = 0f

    fun touch() {
        if (demoAdded) added = added or 4
        demoAdded = false
        playing = false
        held = true
        lastTouch = elapsed
        settling = false
        squeeze = 1f
        buttonScale = 1f
    }
    fun release() {
        held = false
        lastTouch = elapsed
        pressFrom = press
        pressAt = elapsed
        buttonFrom = buttonPress
        buttonAt = elapsed
        buttonHeld = false
    }
    fun down(card: Int, button: Boolean) {
        touch()
        pressed = card
        buttonHeld = button
        pressFrom = press
        pressAt = elapsed
        buttonFrom = buttonPress
        buttonAt = elapsed
    }
    fun settle(value: Float, speed: Float = 0f) {
        target = value
        from = position
        initialVelocity = speed
        settleAt = elapsed
        settling = true
    }
    fun move(delta: Int) {
        touch()
        settle((HoloTheme.nearest(position) + delta).toFloat())
        release()
    }
    fun toggle() {
        touch()
        added = added xor (1 shl HoloTheme.index(HoloTheme.nearest(position)))
        release()
        if (!reduced) haptic(true)
    }
    private fun spring(t: Float, offset: Float, speed: Float): Float {
        val omega = sqrt(41f)
        return exp(-13f*t) * (offset*cos(omega*t) + (speed+13f*offset)/omega*sin(omega*t))
    }
    fun step(dt: Float) {
        elapsed += dt
        if (!reduced) clock = HoloTheme.mod(clock + dt, HoloTheme.loop)
        if (playing) {
            timeline += dt
            val state = HoloTheme.autoplay(timeline)
            position = state.pos
            squeeze = state.squeeze
            buttonScale = state.buttonScale
            demoAdded = state.added
            velocity = HoloTheme.autoVelocity(timeline)
            age = HoloTheme.autoAge(timeline)
            arrival = 1f-HoloTheme.bezier(age/900f)
        } else {
            if (settling) {
                val seconds = (elapsed-settleAt)/1000f
                position = if (reduced) from+(target-from)*HoloTheme.bezier(seconds/.22f)
                    else target+spring(seconds, from-target, initialVelocity)
                if ((reduced && seconds >= .22f) || (!reduced && seconds > 1.2f)) {
                    position = target
                    settling = false
                }
            }
            if (dt > 0) velocity = if (reduced) 0f else velocity +
                (((position-previous)*1000/dt).coerceIn(-8f, 8f)-velocity)*(1-exp(-dt/70))
            if (autoPlay && !reduced && !held && elapsed-lastTouch >= 3500) {
                val nearest = HoloTheme.nearest(position)
                if (abs(position-nearest) < .001f && abs(velocity) < .01f) {
                    position = nearest.toFloat()
                    timeline = floor(nearest/4f)*HoloTheme.loop + HoloTheme.index(nearest)*HoloTheme.beat
                    playing = true
                    settling = false
                } else if (!settling) settle(nearest.toFloat())
            }
        }
        previous = position
        val rounded = HoloTheme.nearest(position)
        if (centre != rounded) {
            centre = rounded
            arrivedAt = elapsed
            if (!reduced) owed[HoloTheme.index(rounded)] += 1
            if (!playing && !reduced) haptic(false)
        }
        if (!reduced) repeat(4) { i ->
            val paid = min(owed[i], 2f*sqrt(owed[i])*dt/2800f)
            surge[i] = HoloTheme.mod(surge[i]+paid, 1f)
            owed[i] -= paid
        }
        if (!playing) {
            age = if (reduced) 900f else (elapsed-arrivedAt).coerceIn(0f, 900f)
            arrival = if (reduced) 0f else 1f-HoloTheme.bezier(age/900)
        }
        press = if (held && pressed >= 0) pressFrom+(.97f-pressFrom)*HoloTheme.bezier((elapsed-pressAt)/120)
            else 1f+spring((elapsed-pressAt)/1000, pressFrom-1, 0f)
        buttonPress = if (buttonHeld) buttonFrom+(.97f-buttonFrom)*HoloTheme.bezier((elapsed-buttonAt)/120)
            else 1f+spring((elapsed-buttonAt)/1000, buttonFrom-1, 0f)
        selection = HoloTheme.index(rounded)
        val isAdded = (added and (1 shl selection)) != 0 || (selection == 2 && demoAdded)
        selected = if (isAdded) 1 else 0
        val desired = selected.toFloat()
        if (morphTarget != desired) {
            morphFrom = morph
            labelFrom = labelMix
            morphTarget = desired
            morphAt = elapsed
        }
        morph = morphFrom+(morphTarget-morphFrom)*HoloTheme.bezier((elapsed-morphAt)/(if (reduced) 150f else 320f))
        labelMix = labelFrom+(morphTarget-labelFrom)*((elapsed-morphAt)/(if (reduced) 150f else 300f)).coerceIn(0f, 1f)
    }
}
