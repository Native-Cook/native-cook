package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import kotlin.math.*

internal object CabinClimateCarMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val ROLL = 0.18
    val steps = listOf(
        1.0 to 69.0,1.3 to 68.0,1.6 to 67.0,1.9 to 66.0,3.0 to 67.0,3.25 to 68.0,
        3.5 to 69.0,3.75 to 70.0,4.0 to 71.0,4.25 to 72.0,4.5 to 73.0,4.75 to 74.0,
        7.2 to 73.0,7.45 to 72.0,7.7 to 71.0,7.95 to 70.0
    )
    data class Frame(
        var amb: Double, var temp: Double, val from: Double, var tempAt: Double,
        var heat: Double, val plusK: Double, val minusK: Double, var leg: Double,
        val legOn: Boolean, val legAt: Double, val fan: Int, val fanAt: Double,
        val cardK: Double, val card: Int
    )
    fun clamp01(x: Double) = x.coerceIn(0.0,1.0)
    fun mix(a: Double,b: Double,p: Double) = a+(b-a)*p
    fun smooth(x: Double): Double { val p = clamp01(x); return p*p*(3-2*p) }
    fun easeOut(x: Double) = 1-(1-clamp01(x)).pow(3)
    fun spring(tau: Double,response: Double = 0.55,damping: Double = 0.8): Double {
        if (tau <= 0) return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+(zw/wd)*sin(wd*tau))
    }
    fun bump(t: Double,at: Double,dur: Double): Double {
        val p = (t-at)/dur
        return if (p <= 0 || p >= 1) 0.0 else sin(PI*p)
    }
    fun frameAt(t: Double): Frame {
        var temp = 70.0; var from = 70.0; var at = -10.0
        var plus = 0.0; var minus = 0.0
        steps.forEachIndexed { index, (st,v) ->
            val k = bump(t,st-0.04,0.2)
            if (v > if (index > 0) steps[index-1].second else 70.0) plus = max(plus,k)
            else minus = max(minus,k)
            if (t >= st) { from = temp; temp = v; at = st }
        }
        val on = t >= 5.4 && t < 6.6
        val legAt = if (t >= 6.6) 6.6 else if (t >= 5.4) 5.4 else -10.0
        val lk = if (legAt < 0) 0.0 else spring(t-legAt)
        return Frame(t,temp,from,t-at,mix(from,temp,easeOut((t-at)/0.9)),plus,minus,
            if (on) lk else if (t >= 6.6) 1-lk else 0.0,on,t-legAt,0,10.0,
            max(bump(t,5.35,0.25),bump(t,6.55,0.25)),3)
    }
}
