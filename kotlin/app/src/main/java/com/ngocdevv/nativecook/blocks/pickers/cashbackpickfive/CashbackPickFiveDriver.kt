package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.Event
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.NEVER
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.LOOP_EVENTS
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.LOOP
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.MAX_SCROLL
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.RESUME
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.SPARKLE_AT
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.Frame
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.scrollOf
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.replay
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.frameAt
import kotlin.math.*

/** Live replay log, momentum and homing use exactly the same owner clock as the loop. */
internal class CashbackPickFiveDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var events = mutableListOf<Event>()
    var dragging = 0
    var scroll = 0.0
    var v = 0.0
    var sparkleAt = NEVER
    var homing = 0

    fun takeOver(now: Double) {
        last = now
        homing = 0
        if (mode == 2) {
            return
        }
        mode = 2
        val k = now - t
        events = LOOP_EVENTS.filter {
            it.t <= t
        }.map {
            it.copy(t = it.t + k)
        }.toMutableList()
        scroll = scrollOf(replay(events, now), now)
        v = 0.0
        sparkleAt = if (t >= SPARKLE_AT && t < SPARKLE_AT + 1.2) SPARKLE_AT + k else NEVER
    }

    fun push(e: Event) {
        events.add(e)
        if (events.size > 40) {
            val s = replay(events, e.t - 3)
            val keep = events.filter {
                it.t > e.t - 3
            }
            val base = s.picks.map {
                Event(e.t - 3.5, 0, it)
            }.toMutableList()
            base.add(Event(e.t - 3.5, 2, -1, scrollOf(s, e.t - 3)))
            events = (base + keep).toMutableList()
        }
    }

    fun step(dt: Double, now: Double, auto: Boolean) {
        amb = (amb + dt) % LOOP
        if (mode == 0) {
            if (auto) {
                t = (t + dt) % LOOP
            }
            return
        }
        if (dragging == 0 && abs(v) > 1) {
            scroll = (scroll + v * dt).coerceIn(0.0, MAX_SCROLL)
            v *= exp(-dt * 4)
            if (abs(v) < 5) {
                v = 0.0
            }
        }
        if (!auto || dragging == 1 || now - last < RESUME) {
            return
        }
        val s = replay(events, now)
        if (homing == 0) {
            homing = 1
            for ((k, i) in s.picks.indices.reversed().withIndex()) {
                push(Event(now + k * 0.25, 1, s.picks[i]))
            }
            if (scroll > 0.5) {
                push(Event(now - 0.75, 2, -1, scroll))
                push(Event(now, 2, -1, 0.0))
            }
            events.sortBy {
                it.t
            }
            v = 0.0
            return
        }
        if (s.picks.isEmpty() && s.flights.isEmpty() && now - s.panelAt > 0.6 && now - s.scrollAt > 0.8 && now - s.nopeAt > 0.6) {
            mode = 0
            t = 0.0
            events.clear()
            scroll = 0.0
        }
    }

    fun frame(now: Double): Frame {
        if (mode == 0) {
            return frameAt(t)
        }
        val s = replay(events, now)
        val scripted = if (now - s.scrollAt < 0.7) scrollOf(s, now) else scroll
        if (now - s.scrollAt < 0.7) {
            scroll = scripted
        }
        return Frame(amb, now, s, scripted, if (now - sparkleAt < 1.6) now - sparkleAt else -1.0)
    }
}
