package com.ngocdevv.nativecook.blocks.lists.mailboxinbox

import kotlin.math.*

internal object MailboxInboxMotion {
    const val LOOP = 9.0
    data class Frame(val open: Double = 0.0, val filed: List<Double> = listOf(0.0, 0.0, 0.0, 0.0))
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoother(k: Double): Double {
        val x = clamp(k)
        return x*x*x*(x*(x*6-15)+10)
    }
    fun at(time: Double): Frame {
        val t = time % LOOP
        val open = when {
            t >= 1 && t < 2.5 -> (t-1)/1.5
            t >= 2.5 && t < 6.2 -> 1.0
            t >= 6.2 && t < 7.6 -> 1-(t-6.2)/1.4
            else -> 0.0
        }
        val filed = when {
            t >= 4.2 && t < 6.2 -> smoother((t-4.2)/0.8)
            t >= 6.2 && t < 7.6 -> 1.0
            else -> 0.0
        }
        return Frame(open, listOf(0.0, 0.0, filed, 0.0))
    }
    fun letter(f: Frame, i: Int) = smoother(f.open*1.5-i*0.12)*(1-f.filed[i])
}

internal class MailboxInboxDriver {
    var frame = MailboxInboxMotion.Frame()
    var target = 0.0
    var now = 0.0
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    val fileT = BooleanArray(4)
    fun step(wall: Double, auto: Boolean, reduced: Boolean): MailboxInboxMotion.Frame {
        now += wall
        val dt = min(wall, 0.05)
        if (auto && !reduced && now-lastTouch > 3.5 && (scripted || (frame.open < 0.001 && target == 0.0))) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT+wall)%MailboxInboxMotion.LOOP
            frame = MailboxInboxMotion.at(loopT)
            target = if (frame.open > 0.5) 1.0 else 0.0
            return frame
        }
        scripted = false
        val speed = if (reduced) 4.0 else 1/1.4
        var open = frame.open
        if (open < target) open = min(target, open+dt*speed)
        if (open > target) open = max(target, open-dt*speed)
        val filed = frame.filed.toMutableList()
        for (i in 0..3) {
            if (fileT[i]) filed[i] = min(1.0, filed[i]+dt/0.8)
            if (open <= 0.001) { filed[i] = 0.0; fileT[i] = false }
        }
        frame = MailboxInboxMotion.Frame(open, filed)
        return frame
    }
    fun active(auto: Boolean, reduced: Boolean) = frame.open != target ||
        (0..3).any { fileT[it] && frame.filed[it] < 1 } ||
        (auto && !reduced && (scripted || (frame.open < 0.001 && target == 0.0)))
    fun toggle() { target = if (target > 0.5) 0.0 else 1.0; touch() }
    fun file(i: Int) { fileT[i] = true; touch() }
    private fun touch() { scripted = false; lastTouch = now }
}
