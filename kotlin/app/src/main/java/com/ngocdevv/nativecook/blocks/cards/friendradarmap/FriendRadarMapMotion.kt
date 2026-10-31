package com.ngocdevv.nativecook.blocks.cards.friendradarmap

import kotlin.math.*

internal object FriendRadarMapMotion {
    const val LOOP = 9.0
    data class Friend(val name: String, val street: String, val sooner: String, val x: Double, val y: Double, val skew: Double)
    val friends = listOf(
        Friend("ANASTASIA", "IS ON BERSON STREET NOW", "MEET HER 3 MIN SOONER", 430.0, 560.0, 0.16),
        Friend("IGOR", "IS AT KILN AVENUE NOW", "MEET HIM 5 MIN SOONER", 760.0, 880.0, -0.12),
        Friend("DASHA", "IS BY PIER ROAD NOW", "MEET HER 2 MIN SOONER", 330.0, 1080.0, 0.08)
    )
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b-a)*k
    fun ease(p: Double): Double { val x = clamp(p); return if (x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun back(p: Double): Double { val x = clamp(p); return 1+2.5*(x-1).pow(3)+1.5*(x-1).pow(2) }
    fun hash(i: Double): Double { val s = sin(i*12.9898)*43758.5453; return s-floor(s) }
    fun random(i: Double): Double { val s = sin(i*91.3458)*47453.5453; return s-floor(s) }
    data class Frame(var time: Double = 0.0, var since: Double = 10.0, var cur: Int = 0, var prev: Int = 0, var fill: Double = 0.0, var burst: Double = -10.0) {
        val labelIndex get() = if (since<0.3) prev else cur
        fun decoded(reduced: Boolean): Pair<String, String> {
            val out = !reduced && prev!=cur && since<0.3
            val name = friends[if (out) prev else cur].name
            val glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=?"
            val text = name.mapIndexed { i, ch ->
                if (out) {
                    val start = 0.03+(name.length-1-i)*0.025
                    if (since<start) ch else if (since<start+0.1) glyphs[floor(hash(i*7+floor(since*30))*glyphs.length).toInt()] else ' '
                } else {
                    val start = 0.3+i*0.07
                    if (reduced || since>=start+0.32) ch else if (since>=start) glyphs[floor(hash(i*13+floor(since*22))*glyphs.length).toInt()] else ' '
                }
            }.joinToString("")
            return name to text
        }
    }
    fun at(t: Double): Frame { val time = t%LOOP; val i = floor(time/3).toInt()%3; return Frame(time,time-i*3,i,(i+2)%3) }
}

internal class FriendRadarMapDriver {
    var frame = FriendRadarMapMotion.Frame()
    var now = 0.0
    var last = -10.0
    var doneAt = -10.0
    var holding = false
    fun go(dir: Int) { frame.prev = frame.cur; frame.cur = (frame.cur+dir+3)%3; frame.since = 0.0; last = now }
    fun step(dt: Double, auto: Boolean, reduced: Boolean): Boolean {
        now += dt
        if (auto && !reduced && !holding && now-last>=2) {
            frame = FriendRadarMapMotion.at((frame.time+dt)%FriendRadarMapMotion.LOOP).also { it.fill = frame.fill }
        } else frame.since += dt
        var completed = false
        val step = min(dt,0.05)
        if (holding && frame.fill<1) {
            frame.fill = min(1.0,frame.fill+step/1.1)
            if (frame.fill>=1) { doneAt = now; completed = true }
        } else if (!holding && frame.fill<1) frame.fill = max(0.0,frame.fill-step*3)
        if (frame.fill>=1 && now-doneAt>2.6) frame.fill = 0.0
        frame.burst = now-doneAt
        return completed
    }
}
