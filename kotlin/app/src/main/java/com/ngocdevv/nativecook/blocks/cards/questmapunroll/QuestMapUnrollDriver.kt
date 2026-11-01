package com.ngocdevv.nativecook.blocks.cards.questmapunroll

import kotlin.math.*

internal class QuestMapUnrollDriver {
    var now = 0.0
    var u = 0.0
    var v = 0.0
    var target = 0.0
    var dragging = false
    var u0 = 0.0
    var wisp = 2.0
    var wispTo = 2.0
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    var cardT = -10.0
    var pressed = -1
    var frame = QuestMapUnrollMotion.Frame()
    var moving = false
    fun step(wall: Double, auto: Boolean, reduced: Boolean, arrival: () -> Unit) {
        now += wall
        val dt = min(wall, 0.05)
        if (auto && !dragging && now - lastTouch > 3.5 && (scripted || (u < 0.001 && target == 0.0 && wisp == 2.0))) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT + wall) % QuestMapUnrollMotion.LOOP
            val f = QuestMapUnrollMotion.at(loopT)
            u = f.u; target = if (u > 0.5) 1.0 else 0.0
            wisp = f.wisp; wispTo = wisp
            frame = f.copy(t = now)
            return
        }
        scripted = false
        if (!dragging) {
            val k = if (reduced) 400.0 else 70.0
            val c = 2 * 0.95 * sqrt(k)
            v += (k * (target - u) - c * v) * dt
            u = QuestMapUnrollMotion.clamp(u + v * dt)
        }
        if (wisp != wispTo) {
            val dir = if (wispTo > wisp) 1 else -1
            wisp += dir * dt * if (reduced) 20.0 else 1.1
            if ((dir > 0 && wisp >= wispTo) || (dir < 0 && wisp <= wispTo)) {
                wisp = wispTo; cardT = now; arrival()
            }
        }
        val cu = now - cardT
        val card = if (cu >= 0 && cu < 2.2) QuestMapUnrollMotion.smoother(cu / 0.35) * (1 - QuestMapUnrollMotion.smoother((cu - 1.8) / 0.4)) else 0.0
        frame = QuestMapUnrollMotion.Frame(u, wisp, now, card, floor(wispTo + 0.5).toInt())
        moving = dragging || abs(v) > 0.000001 || abs(u - target) > 0.000001 || wisp != wispTo || card > 0 || (!reduced && u > 0)
    }
    fun panStart() { dragging = true; scripted = false; u0 = u; moving = true }
    fun panUpdate(dy: Double, g: QuestMapUnrollGeometry) {
        u = QuestMapUnrollMotion.clamp(u0 + dy / g.h); lastTouch = now
    }
    fun panEnd(velocity: Double, g: QuestMapUnrollGeometry) {
        dragging = false
        target = if (velocity > 500) 1.0 else if (velocity < -500) 0.0 else if (u > 0.5) 1.0 else 0.0
        v = velocity / g.h; lastTouch = now; moving = true
    }
    fun tap(x: Double, y: Double, g: QuestMapUnrollGeometry, selection: () -> Unit) {
        lastTouch = now; scripted = false; moving = true
        if (u < 0.9) { target = 1.0; return }
        g.nodes.forEachIndexed { i, n ->
            if (hypot(x - n.first, y - n.second) < 34) { wispTo = i.toDouble(); selection() }
        }
    }
}
