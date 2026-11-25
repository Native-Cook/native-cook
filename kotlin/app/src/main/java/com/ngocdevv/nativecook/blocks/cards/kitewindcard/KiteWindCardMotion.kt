package com.ngocdevv.nativecook.blocks.cards.kitewindcard

import kotlin.math.*

/** The RN timeline and event-based touch model. All distances are screen dp. */
internal object KiteWindMotion {
    const val LOOP = 8.0
    val winds = doubleArrayOf(10.0, 12.0, 15.0, 18.0, 21.0, 24.0, 26.0, 22.0, 17.0, 12.0)
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    fun out(x: Double) = 1-(1-x).pow(3)
    fun quad(x: Double) = if (x < 0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
    fun phase(c: Double) = c-floor(c/LOOP)*LOOP
    fun wind(p: Double): Double {
        val q = p.coerceIn(0.0, 9.0); val i = min(8, q.toInt())
        return winds[i]+(winds[i+1]-winds[i])*(q-i)
    }
    fun rounded(p: Double) = floor(p+0.5).toInt()
    fun hour(p: Double): String {
        val h = rounded(p).coerceIn(0, 9)+9
        return if (h < 12) "$h am" else "${if (h == 12) 12 else h-12} pm"
    }
    fun loopP(t: Double): Double = when {
        t < 1.2 -> 3.0
        t < 3.2 -> 3+3*quad(seg(t, 1.2, 3.2))
        t < 4.6 -> 6.0
        t < 6.4 -> 6-3*quad(seg(t, 4.6, 6.4))
        else -> 3.0
    }
    data class Frame(val p: Double = 3.0, val t: Double = 0.0, val finger: Double = 0.0, val x: Double = 0.0, val y: Double = 0.0)
    class Touch {
        var manual = false; var dragging = false; var pulling = false
        var last = -100.0; var p0 = 3.0; var dx = 0.0
        var from = 3.0; var to = 3.0; var at = -100.0; var duration = 0.35
        var kx = 0.0; var ky = 0.0; var kFromX = 0.0; var kFromY = 0.0; var kAt = -100.0
        fun p(c: Double) = if (dragging) (p0+dx/31.4).coerceIn(0.0, 9.0) else from+(to-from)*out(seg(c, at, at+duration))
        fun pull(c: Double): Pair<Double, Double> {
            if (pulling) return kx to ky
            val u = c-kAt
            if (u < 0 || u > 1.2) return 0.0 to 0.0
            val k = exp(-7*u)*cos(12*u)
            return kFromX*k to kFromY*k
        }
        fun frame(c: Double, reduced: Boolean, auto: Boolean): Frame {
            val t = if (reduced) 0.0 else phase(c)
            if (!manual) {
                val f = seg(t, 1.0, 1.2)*(1-seg(t, 3.2, 3.4))+seg(t, 4.4, 4.6)*(1-seg(t, 6.4, 6.6))
                return Frame(if (reduced) 3.0 else loopP(t), t, if (reduced) 0.0 else f)
            }
            val k = if (reduced) 0.0 to 0.0 else pull(c)
            return Frame(p(c), t, 0.0, k.first, k.second)
        }
        fun takeOver(c: Double, reduced: Boolean, auto: Boolean) {
            last = c
            if (manual) return
            val p = frame(c, reduced, auto).p
            manual = true; from = p; to = p; at = c
        }
        fun go(target: Double, c: Double, reduced: Boolean) {
            from = p(c); to = target.coerceIn(0.0, 9.0); at = c; duration = 0.35
            if (reduced) from = to
            dragging = false; last = c
        }
        fun step(c: Double, auto: Boolean, reduced: Boolean) {
            if (!manual || !auto || reduced || dragging || pulling || c-last < 2 || c < at+duration || c < kAt+1.2) return
            if (abs(p(c)-3) > 0.001) {
                val previous = last; go(3.0, c, false); last = previous; duration = 0.6
            } else if (phase(c) >= 6.6 || phase(c) < 1) {
                manual = false; from = 3.0; to = 3.0; at = -100.0; kAt = -100.0
            }
        }
    }
}
