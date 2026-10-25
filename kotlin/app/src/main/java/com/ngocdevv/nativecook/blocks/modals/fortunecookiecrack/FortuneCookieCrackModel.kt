package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.CLOSE_AT
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.CRACK_AT
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.LOOP
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.OPEN_AT
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.SQUEEZE_AT
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.TAP_FROM
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * One clock (ported from FortuneCookieCrackModel in the Swift file). A finger moves it: holding
 * plays the squeeze, a tap when open plays the close. Only [clock] is Compose state, and it is read
 * only in draw and graphicsLayer lambdas.
 */
internal class FortuneCookieCrackModel(private val looping: Boolean, private val haptic: (Int) -> Unit) {
    var clock by mutableDoubleStateOf(0.0)
        private set

    var touchActive = false
        private set
    private var running = looping
    private var user = false
    private var pressing = false
    private var pressFor = 0.0
    private var target = -1.0
    private var idle = 0.0
    private var relaxFrom: Double? = null
    private var relaxT = 0.0
    private var level = -1

    val resting get() = !user && !running && !touchActive

    /** Advances the clock by dt seconds (already clamped by the caller). */
    fun step(dt: Double) {
        var clock = this.clock
        if (user) {
            if (pressing) {
                pressFor += dt
                idle = 0.0
                val c = max(clock, SQUEEZE_AT) + dt
                if (c >= CRACK_AT) {
                    pressing = false
                    target = OPEN_AT
                }
                clock = min(c, CRACK_AT)
            } else if (target >= 0) {
                var c = clock + dt
                if (c >= target) {
                    c = if (target >= LOOP) 0.0 else target
                    target = -1.0
                }
                clock = c
            } else {
                val from = relaxFrom
                if (from != null) {
                    // Let go before it snapped: the squeeze relaxes (280 ms, ease-in-out).
                    relaxT += dt
                    val x = min(1.0, relaxT / 0.28)
                    val e = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
                    clock = from + (SQUEEZE_AT - from) * e
                    if (x >= 1) relaxFrom = null
                }
                idle += dt
                if (idle > IDLE) {
                    // Back to the loop: a cookie left open folds itself back up; a closed one starts over.
                    user = false
                    relaxFrom = null
                    val isOpen = clock >= OPEN_AT && clock < CLOSE_AT
                    clock = if (isOpen) CLOSE_AT else if (clock < CRACK_AT) 0.0 else clock
                    running = looping || isOpen
                }
            }
            // Haptics only for what a finger caused: the snap, and the slip lying open.
            val now = if (clock >= OPEN_AT - 0.01) 2 else if (clock >= CRACK_AT) 1 else 0
            if (user && level >= 0 && now > level) haptic(if (now == 1) 0 else 1)
            level = if (user) now else -1
        } else if (running) {
            level = -1
            var t = clock + dt
            if (t >= LOOP) {
                t -= LOOP
                if (!looping) {
                    t = 0.0
                    running = false
                }
            }
            clock = t
        }
        if (clock != this.clock) this.clock = clock
    }

    private fun onCookie(x: Float, y: Float, l: FortuneLayout) = abs(x - l.cx) < 140 && y > l.cy - 100 && y < l.cy + 90

    /** A finger lands. Returns whether the cookie took it. */
    fun down(x: Float, y: Float, l: FortuneLayout): Boolean {
        touchActive = true
        val c = clock
        val closed = c < SQUEEZE_AT + 0.05 || c >= 8.4
        val open = c >= OPEN_AT - 0.01 && c < CLOSE_AT
        if (!onCookie(x, y, l) || !(closed || open)) return false
        user = true
        running = false
        idle = 0.0
        relaxFrom = null
        level = -1
        if (closed) {
            clock = SQUEEZE_AT
            pressing = true
            pressFor = 0.0
            target = -1.0
        } else {
            // Open: a touch folds the fortune back into the cookie.
            target = LOOP
            clock = max(c, CLOSE_AT)
        }
        return true
    }

    fun up() {
        touchActive = false
        if (pressing) {
            pressing = false
            if (pressFor < TAP) {
                clock = max(clock, TAP_FROM)
                target = OPEN_AT
            } else {
                relaxFrom = clock
                relaxT = 0.0
            }
        }
        idle = 0.0
    }

    fun cancel() {
        touchActive = false
        if (pressing) {
            pressing = false
            relaxFrom = clock
            relaxT = 0.0
        }
    }

    /** The Close button folds an open fortune back up. Returns whether it did. */
    fun close(): Boolean {
        val c = clock
        if (!(c >= OPEN_AT - 0.01 && c < CLOSE_AT)) return false
        user = true
        running = false
        idle = 0.0
        clock = CLOSE_AT
        target = LOOP
        return true
    }

    private companion object {
        /** Seconds after the last touch before the loop starts again. */
        const val IDLE = 2.0
        /** A touch shorter than this is a tap: it cracks the cookie at once. */
        const val TAP = 0.22
    }
}
