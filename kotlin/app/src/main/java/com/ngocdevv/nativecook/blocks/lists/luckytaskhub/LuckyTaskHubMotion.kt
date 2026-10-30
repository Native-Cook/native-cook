package com.ngocdevv.nativecook.blocks.lists.luckytaskhub
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.BALANCE
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.BILL_VALUE
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.FLIPS
/** Pure RN timeline and touch driver; every clock and analytic easing uses Double. */
internal object LuckyTaskHubMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val BILLS = 5
    const val BILL_FLIGHT = 0.78
    const val BILL_GAP = 0.09
    const val FLIP_DUR = 0.9
    const val HOP_DUR = 1.0
    object T {
        val spin = doubleArrayOf(0.8, 2.6)
        const val bills = 2.62
        val prog = doubleArrayOf(3.0, 3.7)
        const val flip = 4.2
        const val hop = 5.4
        const val flipBack = 7.0
        val tierBack = doubleArrayOf(7.2, 7.7)
        val progBack = doubleArrayOf(7.4, 8.2)
        val balBack = doubleArrayOf(7.7, 8.2)
    }
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun mix(a: Double, b: Double, p: Double) = a+(b-a)*p
    fun easeOut(x: Double) = 1-(1-clamp01(x)).pow(3)
    fun easeOutQuint(x: Double) = 1-(1-clamp01(x)).pow(5)
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p<0.5)4*p*p*p else 1-(-2*p+2).pow(3)/2
    }
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p*p*(3-2*p)
    }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0)return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+zw/wd*sin(wd*tau))
    }
    fun bump(t: Double, at: Double, dur: Double): Double {
        val p = (t-at)/dur
        return if (p <= 0 || p >= 1)0.0 else sin(PI*p)
    }
    data class Frame(
        val amb: Double,
        val wheel: Double,
        val wheelSpeed: Double,
        val pop: Double,
        val billsT: Double,
        val balance: Double,
        val balFade: Double,
        val bump: Double,
        val prog: Double,
        val flipAng: Double,
        val flipFrom: Int,
        val flipTo: Int,
        val remaining: Double,
        val hop: Double,
        val tierFrom: Int,
        val tierTo: Int,
        val tierK: Double,
        val chest: Double,
        val press: Int,
        val pressK: Double,
    )
    fun landed(tau: Double): Double {
        var n = 0.0
        for (i in 0 until BILLS) {
            n += smooth((tau - BILL_FLIGHT - i * BILL_GAP) / 0.22)
        }
        return n
    }
    fun landBump(tau: Double): Double {
        var b = 0.0
        for (i in 0 until BILLS) {
            b = max(b, bump(tau, BILL_FLIGHT + i * BILL_GAP, 0.24))
        }
        return b
    }
    fun frameAt(t: Double): Frame {
        val sp = seg(t, T.spin[0], T.spin[1])
        val wheel = 810*easeOutQuint(sp)
        val wheelSpeed = if (sp>0 && sp<1)(1-sp).pow(4) else 0.0
        val billsT = if (t >= T.bills && t<T.balBack[0])t-T.bills else -1.0
        val balUp = if (billsT >= 0)landed(billsT) else if (t >= T.balBack[0])BILLS.toDouble() else 0.0
        val balFade = smooth(seg(t, T.balBack[0], T.balBack[1]))
        val prog = 3+easeInOut(seg(t, T.prog[0], T.prog[1]))-easeInOut(seg(t, T.progBack[0], T.progBack[1]))
        var flipAng = 0.0
        var flipFrom = 0
        var flipTo = 0
        if (t >= T.flip && t<T.flipBack) {
            flipAng = 360*easeInOut(seg(t, T.flip, T.flip+FLIP_DUR))
            flipTo = 1
        }
        else if (t >= T.flipBack) {
            flipAng = 360*easeInOut(seg(t, T.flipBack, T.flipBack+FLIP_DUR))
            flipFrom = 1
        }
        val remaining = FLIPS-4-smooth(seg(t, T.flip+0.35, T.flip+0.75))+smooth(seg(t, T.flipBack+0.35, T.flipBack+0.75))
        val hop = if (t >= T.hop && t<T.hop+HOP_DUR+0.6)t-T.hop else -1.0
        var tierFrom = 0
        var tierTo = 0
        var tierK = 0.0
        if (t >= T.hop && t<T.tierBack[0]) {
            tierTo = 1
            tierK = smooth(seg(t, T.hop+0.32, T.hop+0.44))
        }
        else if (t >= T.tierBack[0]) {
            tierFrom = 1
            tierK = smooth(seg(t, T.tierBack[0], T.tierBack[1]))
        }
        return Frame(
            amb = t,
            wheel = wheel,
            wheelSpeed = wheelSpeed,
            pop = bump(t, T.spin[1]-0.05, 0.4),
            billsT = billsT,
            balance = BALANCE+BILL_VALUE*balUp,
            balFade = balFade,
            bump = if (billsT >= 0)landBump(billsT)else 0.0,
            prog = prog,
            flipAng = flipAng,
            flipFrom = flipFrom,
            flipTo = flipTo,
            remaining = remaining,
            hop = hop,
            tierFrom = tierFrom,
            tierTo = tierTo,
            tierK = tierK,
            chest = if (t >= 6.7 && t<7.6)t-6.7 else -1.0,
            press = -1,
            pressK = 0.0,
        )
    }
    class Driver(var t: Double = 0.0) {
        var mode = 0
        var last = -10.0
        var amb = t
        var wheel = 0.0
        var wheelV = 0.0
        var dragging = 0
        var spun = 0.0
        var spinning = 0
        var billsAt = -10.0
        var balFrom = BALANCE
        var balance = BALANCE
        var progFrom = 3.0
        var progTo = 3.0
        var progAt = -10.0
        var flipAt = -10.0
        var flipFrom = 0
        var flipTo = 0
        var remFrom = (FLIPS-4).toDouble()
        var remTo = (FLIPS-4).toDouble()
        var hopAt = -10.0
        var tierFrom = 0
        var tierTo = 0
        var chestAt = -10.0
        var press = -1
        var pressLast = 0
        var pressAt = -10.0
        var releaseAt = -10.0
        var homeAt = -10.0
        var wheelFrom = 0.0
        var wheelTo = 0.0
    }
    // JavaScript Math.round is floor(x + .5), including negative wheel angles.
    fun jsRound(x: Double) = floor(x+0.5)
    fun takeOver(d: Driver, now: Double) {
        d.last = now
        if (d.mode == 2) {
            if (d.homeAt>0) {
                d.homeAt = -10.0
                d.balance = jsRound(d.balance)
                d.wheelV = 0.0
            }
            return
        }
        val f = frameAt(d.t)
        d.mode = 2
        d.wheel = f.wheel%360
        d.wheelV = 0.0
        d.spinning = 0
        d.billsAt = -10.0
        d.balance = if (f.balFade>0.5)BALANCE else jsRound(f.balance)
        d.balFrom = d.balance
        d.progTo = jsRound(f.prog)
        d.progFrom = d.progTo
        d.flipTo = if (f.flipAng>180)f.flipTo else f.flipFrom
        d.flipFrom = d.flipTo
        d.flipAt = -10.0
        d.remTo = jsRound(f.remaining)
        d.remFrom = d.remTo
        d.tierTo = if (f.tierK>0.5)f.tierTo else f.tierFrom
        d.tierFrom = d.tierTo
        d.hopAt = -10.0
        d.chestAt = -10.0
    }
    fun stepDriver(d: Driver, dt: Double, now: Double, auto: Boolean) {
        d.amb = (d.amb+dt)%LOOP
        if (d.mode == 0) {
            if (auto)d.t = (d.t+dt)%LOOP
            return
        }
        if (d.dragging == 0 && d.spinning == 1) {
            val sgn = if (d.wheelV >= 0)1 else -1
            val speed = abs(d.wheelV)
            val next = max(0.0, speed-(90+speed*0.9)*dt)
            d.wheelV = sgn*next
            d.wheel+=d.wheelV*dt
            d.spun+=abs(d.wheelV*dt)
            if (next<40) {
                val target = jsRound(d.wheel/45)*45
                d.wheel+=(target-d.wheel)*min(1.0, dt*10)
                if (abs(target-d.wheel)<0.3) {
                    d.wheel = target
                    d.wheelV = 0.0
                    d.spinning = 0
                    if (d.spun>300) {
                        d.billsAt = now
                        d.balFrom = d.balance
                        d.progFrom = d.progTo
                        d.progTo = min(14.0, d.progTo+1)
                        d.progAt = now+0.4
                    }
                }
            }
        }
        if (d.billsAt>0) {
            d.balance = d.balFrom+BILL_VALUE*landed(now-d.billsAt)
            if (now-d.billsAt>BILL_FLIGHT+BILLS*BILL_GAP+0.4) {
                d.balance = d.balFrom+BILL_VALUE*BILLS
                d.billsAt = -10.0
            }
        }
        if (d.homeAt>0) {
            val k = easeInOut((now-d.homeAt)/0.8)
            d.wheel = mix(d.wheelFrom, d.wheelTo, k)
            d.balance = mix(d.balFrom, BALANCE, k)
            if (now-d.homeAt>0.9 && now-d.hopAt>HOP_DUR+0.6 && now-d.flipAt>FLIP_DUR && now-d.progAt>0.8) {
                d.homeAt = -10.0
                d.mode = 0
                d.t = 0.0
            }
            return
        }
        if (auto && d.dragging == 0 && d.spinning == 0 && d.billsAt<0 && d.press<0 && now-d.last >= RESUME) {
            d.homeAt = now
            d.wheelFrom = d.wheel
            d.wheelTo = jsRound(d.wheel/90)*90
            d.balFrom = d.balance
            d.progFrom = mix(d.progFrom, d.progTo, easeInOut((now-d.progAt)/0.7))
            d.progTo = 3.0
            d.progAt = now
            if (d.flipTo != 0) {
                flipCard(d, now)
                d.remTo = (FLIPS-4).toDouble()
            }
            else if (d.remTo != (FLIPS-4).toDouble()) {
                d.remFrom = d.remTo
                d.remTo = (FLIPS-4).toDouble()
                d.flipAt = now-FLIP_DUR
            }
            if (d.tierTo != 0) {
                d.tierFrom = d.tierTo
                d.tierTo = 0
                d.hopAt = now
            }
        }
    }
    fun frameOf(d: Driver, now: Double): Frame {
        if (d.mode == 0)return frameAt(d.t)
        val billsT = if (d.billsAt>0)now-d.billsAt else -1.0
        val flipP = easeInOut((now-d.flipAt)/FLIP_DUR)
        val hopT = now-d.hopAt
        val pressK = if (d.press >= 0)smooth((now-d.pressAt)/0.1) else 1-smooth((now-d.releaseAt)/0.16)
        return Frame(
            amb = d.amb,
            wheel = d.wheel,
            wheelSpeed = min(1.0, abs(d.wheelV)/900),
            pop = if (billsT >= 0)bump(billsT, -0.05, 0.4)else 0.0,
            billsT = billsT,
            balance = d.balance,
            balFade = 0.0,
            bump = if (billsT >= 0)landBump(billsT)else 0.0,
            prog = mix(d.progFrom, d.progTo, easeInOut((now-d.progAt)/0.7)),
            flipAng = if (flipP >= 1)0.0 else 360*flipP,
            flipFrom = if (flipP >= 1)d.flipTo else d.flipFrom,
            flipTo = d.flipTo,
            remaining = mix(d.remFrom, d.remTo, smooth((now-d.flipAt-0.35)/0.4)),
            hop = if (hopT<HOP_DUR+0.6)hopT else -1.0,
            tierFrom = d.tierFrom,
            tierTo = d.tierTo,
            tierK = smooth((hopT-0.32)/0.12),
            chest = if (now-d.chestAt<0.9)now-d.chestAt else -1.0,
            press = if (d.press >= 0)d.press else d.pressLast,
            pressK = pressK,
        )
    }
    fun flipCard(d: Driver, now: Double): Boolean {
        if (now-d.flipAt<FLIP_DUR)return false
        d.flipFrom = d.flipTo
        d.flipTo = 1-d.flipTo
        d.flipAt = now
        d.remFrom = d.remTo
        d.remTo = if (d.remTo>0)d.remTo-1 else FLIPS.toDouble()
        return true
    }
    fun hopTrophy(d: Driver, now: Double): Boolean {
        if (now-d.hopAt<HOP_DUR)return false
        d.tierFrom = d.tierTo
        d.tierTo = (d.tierTo+1)%3
        d.hopAt = now
        return true
    }
    fun kickWheel(d: Driver): Boolean {
        if (d.spinning == 1 || d.billsAt>0)return false
        d.wheelV = 1500+kotlin.random.Random.nextDouble()*400
        d.spinning = 1
        d.spun = 0.0
        return true
    }
}
