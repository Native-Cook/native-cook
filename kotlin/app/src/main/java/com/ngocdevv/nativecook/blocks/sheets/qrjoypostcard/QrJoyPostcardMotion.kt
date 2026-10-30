package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Module
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.QR

/** Pure, analytic timeline. Values are Double to retain the reference's JS precision. */
internal object QrJoyPostcardMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val PEAK = 5.35
    const val LIFT = 34.0
    const val TURN_LIFTED = -4 * PI / 180
    object T {
        val press = doubleArrayOf(1.25, 1.42)
        const val commit = 1.42
        const val liftAt = 1.42
        const val swirlAt = 1.52
        val face = doubleArrayOf(1.85, 2.95)
        val finders = doubleArrayOf(2.0, 2.55)
        val cta = doubleArrayOf(2.15, 2.6)
        val check = doubleArrayOf(2.55, 2.95)
        const val heartAt = 3.0
        val burst = doubleArrayOf(3.0, 3.75)
        val sticker = doubleArrayOf(3.15, 3.42)
        val hop = doubleArrayOf(3.5, 3.95)
        const val bubbleAt = 3.62
        val write = doubleArrayOf(3.75, 4.6)
        val wave = doubleArrayOf(3.75, 5.15)
        val sheen = doubleArrayOf(4.7, 5.35)
        const val back = 6.5
        const val backSwirlAt = 6.65
        val lower = doubleArrayOf(7.55, 8.15)
    }
    const val SWIRL_DONE = T.swirlAt + 0.62 + 0.14 + 0.82
    const val BACK_DONE = T.backSwirlAt + 0.42 + 0.1 + 0.62

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))

    fun mix(a: Double, b: Double, p: Double) = a+(b-a)*p

    fun easeOut(x: Double): Double {
        val u = 1-clamp01(x)
        return 1-u*u*u*u*u
    }

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p<0.5) 16*p*p*p*p*p else 1-(-2*p+2).pow(5)/2
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
        if (tau <= 0) return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+(zw/wd)*sin(wd*tau))
    }

    fun wobble(tau: Double, period: Double, decay: Double): Double {
        if (tau <= 0) return 0.0
        return exp(-tau/decay)*cos(2*PI*tau/period)
    }
    data class Frame(
        val t: Double,
        val press: Double,
        val lift: Double,
        val face: Double,
        val finders: Double,
        val cta: Double,
        val check: Double,
        val heart: Double,
        val burst: Double,
        val sticker: Double,
        val stickerSquash: Double,
        val stickerPeel: Double,
        val bubble: Double,
        val write: Double,
        val hop: Double,
        val squash: Double,
        val wave: Double,
        val sheen: Double,
        val landSquash: Double
    )

    fun moduleU(m: Module, t: Double): Double {
        if (t<T.back) {
            val start = T.swirlAt+0.62*m.d+0.14*m.h
            return easeInOutCubic(seg(t, start, start+0.82))
        }
        val start = T.backSwirlAt+0.42*(1-m.d)+0.1*m.h
        return 1-easeInOutCubic(seg(t, start, start+0.62))
    }

    fun moduleRaw(m: Module, t: Double): Double {
        if (t<T.back) {
            val start = T.swirlAt+0.62*m.d+0.14*m.h
            return seg(t, start, start+0.82)
        }
        val start = T.backSwirlAt+0.42*(1-m.d)+0.1*m.h
        return 1-seg(t, start, start+0.62)
    }
    data class Pose(
        val x: Double,
        val y: Double,
        val z: Double,
        val spin: Double
    )

    fun modulePose(m: Module, u: Double): Pose {
        val mid = QR.x+QR.size/2
        val midY = QR.y+QR.size/2
        val x0 = QR.x+(m.c+0.5)*QR.m-mid
        val y0 = QR.y+(m.r+0.5)*QR.m-midY
        val r0 = hypot(x0, y0)
        val a0 = atan2(y0, x0)
        val z = sin(PI*u)
        val a = a0+2*PI*u
        val r = r0+(10+26*m.h)*z
        val turn = min(1.0, u/0.3)
        return Pose(mid+r*cos(a), midY+r*sin(a)-12*z, z, (a+PI/2)*turn*turn)
    }

    fun frameAt(t: Double): Frame {
        val fwd = t<T.back
        val pressIn = seg(t, T.press[0], T.press[0]+0.12)
        val pressOut = seg(t, T.commit, T.commit+0.16)
        val press = if (t >= T.press[0] && t<T.commit+0.16) easeOut(pressIn)*(1-easeOut(pressOut)) else 0.0
        val face = if (fwd) easeInOut(seg(t, T.face[0], T.face[1])) else 1-easeInOut(seg(t, 6.8, 7.6))
        val finders = if (fwd) easeInOut(seg(t, T.finders[0], T.finders[1])) else 1-easeInOut(seg(t, 6.75, 7.2))
        val cta = if (fwd) easeInOut(seg(t, T.cta[0], T.cta[1])) else 1-easeInOut(seg(t, 6.6, 6.95))
        val check = if (fwd) easeOut(seg(t, T.check[0], T.check[1])) else 1-easeOut(seg(t, 6.6, 6.85))
        val heart = if (fwd) spring(t-T.heartAt, 0.5, 0.55) else 1-easeInOut(seg(t, 6.55, 6.8))
        val burst = if (t >= T.burst[0] && t<T.burst[1]) seg(t, T.burst[0], T.burst[1]) else -1.0
        val sticker = if (fwd) seg(t, T.sticker[0], T.sticker[1]) else 1.0
        val stickerSquash = if (fwd) wobble(t-T.sticker[1], 0.24, 0.16) else 0.0
        val stickerPeel = if (fwd) 0.0 else easeInOut(seg(t, 6.5, 6.9))
        val bubble = if (fwd) spring(t-T.bubbleAt, 0.42, 0.6) else 1-easeInOut(seg(t, 6.5, 6.68))
        val write = if (fwd) seg(t, T.write[0], T.write[1]) else 1-seg(t, 6.5, 6.6)
        val hp = seg(t, T.hop[0], T.hop[1])
        val hopping = hp>0 && hp<1
        val hop = if (hopping) 13*sin(PI*hp) else 0.0
        val crouch = if (t>T.hop[0]-0.12 && t<T.hop[0]) sin(PI*seg(t, T.hop[0]-0.12, T.hop[0])) else 0.0
        val land = wobble(t-T.hop[1], 0.22, 0.14)
        val cardLand = if (fwd) wobble(t-(T.liftAt+0.48), 0.26, 0.16)*0.6 else wobble(t-T.lower[1], 0.26, 0.14)*0.6
        val squash = 0.07*crouch-0.05*sin(PI*hp)*(if (hopping) 1 else 0)+0.08*land+0.05*cardLand
        val sp = seg(t, T.sheen[0], T.sheen[1])
        val wp = seg(t, T.wave[0], T.wave[1])
        val wave = if (wp <= 0 || wp >= 1) 4.0 else 4+4*sin(2*PI*2*wp)*minOf(1.0, wp*5, (1-wp)*5)
        val lift = if (fwd) spring(t-T.liftAt, 0.62, 0.72) else 1-easeInOut(seg(t, T.lower[0], T.lower[1]))
        return Frame(t, press, lift, face, finders, cta, check, heart, burst, sticker, stickerSquash, stickerPeel, bubble, write, hop, squash, wave, if (sp>0 && sp<1) sp else -1.0, cardLand)
    }

    fun inRestA(t: Double) = t<T.press[0] || t >= T.lower[1]

    fun inPostcard(t: Double) = t >= T.heartAt && t<T.back
}
