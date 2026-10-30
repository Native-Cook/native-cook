package com.ngocdevv.nativecook.blocks.sheets.bookfansignin

import kotlin.math.*
internal object BookFanSigninMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val PEAK = 3.4
    object T {
        val press = doubleArrayOf(1.3, 1.45)
        const val open = 1.45
        val blur = doubleArrayOf(1.45, 2.05)
        const val sheet = 1.55
        const val flight = 1.5
        const val icon = 1.95
        val logo = doubleArrayOf(2.05, 2.55)
        val tag = doubleArrayOf(2.1, 2.45)
        val title = doubleArrayOf(2.15, 2.55)
        val title2 = doubleArrayOf(2.25, 2.65)
        val sub = doubleArrayOf(2.35, 2.75)
        val cta = doubleArrayOf(2.4, 2.85)
        val shimmer = doubleArrayOf(3.0, 3.7)
        const val close = 6.6
        const val closeEnd = 7.5
    }
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun mix(a: Double, b: Double, p: Double) = a+(b-a)*p
    fun easeOut(x: Double): Double {
        val u = 1-clamp01(x)
        return 1-u*u*u*u*u
    }
    fun easeIn(x: Double): Double {
        val p = clamp01(x)
        return p*p*p
    }
    fun easeInOutCubic(x: Double): Double {
        val p = clamp01(x)
        return if (p<0.5) 4*p*p*p else 1-(-2*p+2).pow(3)/2
    }
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p*p*(3-2*p)
    }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau<=0) return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+(zw/wd)*sin(wd*tau))
    }
    val rad = -27.0*PI/180
    val ux = cos(rad)*69
    val uy = sin(rad)*69
    val vx = -sin(rad)*96
    val vy = cos(rad)*96
    val flying = arrayOf(1 to 3, 2 to 2, 3 to 3, 4 to 2, 5 to 3, 6 to 2)
    fun wallShift(amb: Double, extra: Double): Double {
        val s = -(amb/LOOP)*96+extra
        return ((s%96)+96)%96-96
    }
    fun cellAt(i: Int, j: Int, shift: Double): Pair<Double, Double> {
        val ci = i-3.0
        val cj = j-2.5
        val k = shift/96
        return (214+ci*ux+(cj+k)*vx) to (176+ci*uy+(cj+k)*vy)
    }
    fun coverOf(i: Int, j: Int) = (i*3+j*7)%10
    fun slot(k: Int) = Triple(52.0+k*76, 476.0-k*5, -17.0)
    fun flightAt(k: Int, t: Double): Double {
        if (t<T.close) return easeInOutCubic(seg(t, T.flight+k*0.05, T.flight+k*0.05+0.68))
        val start = T.close+(5-k)*0.04
        return 1-easeInOutCubic(seg(t, start, start+0.55))
    }
    data class Frame(
        val t: Double,
        val press: Double,
        val blur: Double,
        val dim: Double,
        val sheet: Double,
        val flights: DoubleArray,
        val icon: Double,
        val logo: Double,
        val tag: Double,
        val title: Double,
        val title2: Double,
        val sub: Double,
        val cta: Double,
        val shimmer: Double,
    )

    fun frameAt(t: Double): Frame {
        val forward = t < T.close
        val press = if (t >= T.press[0] && t < T.press[1] + 0.14) {
            easeOut(seg(t, T.press[0], T.press[0] + 0.1)) *
                (1 - easeOut(seg(t, T.press[1], T.press[1] + 0.14)))
        } else 0.0
        val back = seg(t, T.close, T.closeEnd)
        fun out(value: Double) = if (forward) value else value * (1 - easeOut(back * 2))
        val shimmer = seg(t, T.shimmer[0], T.shimmer[1])

        return Frame(
            t = t,
            press = press,
            blur = if (forward) smooth(seg(t, T.blur[0], T.blur[1]))
                else 1 - smooth(seg(t, T.close + 0.15, T.closeEnd)),
            dim = if (forward) smooth(seg(t, T.blur[0], T.blur[1]))
                else 1 - smooth(seg(t, T.close + 0.1, T.closeEnd - 0.1)),
            sheet = if (forward) spring(t - T.sheet, 0.55, 0.82)
                else 1 - easeIn(seg(t, T.close, T.close + 0.45)),
            flights = DoubleArray(6) { flightAt(it, t) },
            icon = out(spring(t - T.icon, 0.45, 0.6)),
            logo = out(easeInOutCubic(seg(t, T.logo[0], T.logo[1]))),
            tag = out(easeOut(seg(t, T.tag[0], T.tag[1]))),
            title = out(easeOut(seg(t, T.title[0], T.title[1]))),
            title2 = out(easeOut(seg(t, T.title2[0], T.title2[1]))),
            sub = out(easeOut(seg(t, T.sub[0], T.sub[1]))),
            cta = out(easeOut(seg(t, T.cta[0], T.cta[1]))),
            shimmer = if (shimmer > 0 && shimmer < 1) shimmer else -1.0,
        )
    }

    fun inHome(t: Double) = t < T.press[0] || t >= T.closeEnd
    fun inSheet(t: Double) = t >= PEAK - 0.6 && t < T.close
}
