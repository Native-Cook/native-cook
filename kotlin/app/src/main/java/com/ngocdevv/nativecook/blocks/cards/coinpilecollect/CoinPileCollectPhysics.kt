package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Port of cpc-physics.ts: a position-based solver for discs with a depth layer per coin, a seeded settle and the
// resolveZ stacking pass. Plain double arrays, no allocation per step.

internal const val CpcSTRIDE = 32
internal const val CpcX = 0
internal const val CpcY = 1
internal const val CpcPX = 2
internal const val CpcPY = 3
internal const val CpcZ = 4
internal const val CpcAX = 5
internal const val CpcAY = 6
internal const val CpcPHI = 7
internal const val CpcGLOW = 8
internal const val CpcALIVE = 9
internal const val CpcDRAIN = 10
internal const val CpcVAL = 11
internal const val CpcFACE = 12
internal const val CpcRC = 13
internal const val CpcSLEEP = 14
internal const val CpcAUX = 15
internal const val CpcSX = 16
internal const val CpcSY = 17
internal const val CpcDUR = 18
internal const val CpcZ0 = 19
internal const val CpcRANK = 20
internal const val CpcZT = 21
internal const val CpcZB = 22
internal const val CpcCNT = 23
internal const val CpcR0X = 24
internal const val CpcR0Y = 25
internal const val CpcR0Z = 26
internal const val CpcR0AX = 27
internal const val CpcR0AY = 28
internal const val CpcR0PHI = 29
internal const val CpcRT0 = 30
internal const val CpcRKIND = 31

internal const val CpcNMAX = 32
internal const val CpcFLICK_SPEED = 300.0
internal const val CpcFLICK_REACH = 30.0
internal const val CpcPUSH_MEMORY = 0.5
internal const val CpcHS = 9

internal const val CpcEV_COLLECTED = 0
internal const val CpcEV_LAST = 1
internal const val CpcEV_IMPACT = 2
internal const val CpcEV_CREDIT = 3
internal const val CpcEV_ALIVE = 4
internal const val CpcEV_FLOW = 5
internal const val CpcEV_LASTSPIN = 6
internal const val CpcEV_CENTS = 7
internal const val CpcEV_FLICK = 8
internal const val CpcEV_SIZE = 12

internal const val CpcIN_RATE = 0
internal const val CpcIN_FINGER = 1
internal const val CpcIN_FX = 2
internal const val CpcIN_FY = 3
internal const val CpcIN_GX = 4
internal const val CpcIN_FR = 5
internal const val CpcIN_FLICKON = 6
internal const val CpcIN_SIZE = 8

internal class CoinPileCollectCfg(
    var n: Int,
    var w: Double,
    var h: Double,
    var r: Double,
    var t: Double,
    var mx: Double,
    var my: Double,
    var keepW: Double,
    var keepH: Double,
    var seed: Int,
) {
    var reach = 150.0
    var gravity = 2600.0
    var layers = 3
    var layerGap = 2.2 * t
    var overlap = 0.45
    var rcScale = 0.7
    var order = IntArray(0)
    var keepOn = 0
    var heap = DoubleArray(0)
}

internal fun cpcHash32(n: Int): Double {
    var h = n
    h = (h xor (h ushr 16)) * 0x45d9f3b
    h = (h xor (h ushr 16)) * 0x45d9f3b
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
}

internal fun cpcRnd(seed: Int, i: Int, k: Int): Double =
    cpcHash32(seed * 0x9e3779b1.toInt() + (i + 1) * 0x85ebca6b.toInt() + (k + 1) * 0xc2b2ae35.toInt())

internal fun cpcClamp(v: Double, a: Double, b: Double): Double = if (v < a) a else if (v > b) b else v

internal fun cpcSmoothP(a: Double, b: Double, v: Double): Double {
    val k = cpcClamp((v - a) / (b - a), 0.0, 1.0)
    return k * k * (3 - 2 * k)
}

internal fun cpcCoinValues(n: Int, totalCents: Int, seed: Int, out: DoubleArray) {
    var sum = 0.0
    for (i in 0 until n) {
        val wgt = 0.55 + cpcRnd(seed, i, 91) * 0.9
        out[i] = wgt
        sum += wgt
    }
    var acc = 0.0
    for (i in 0 until n) {
        val c = floor(out[i] / sum * totalCents)
        out[i] = c
        acc += c
    }
    var rest = totalCents - acc.toInt()
    var i = 0
    while (rest > 0) {
        out[i % n] += 1.0
        rest--
        i++
    }
}

internal fun cpcFaceAz(ax: Double, ay: Double): Double = sqrt(max(1 - ax * ax - ay * ay, 0.0004))

internal fun cpcRadiusOf(cfg: CoinPileCollectCfg, ax: Double, ay: Double): Double {
    val az = sqrt(max(1 - ax * ax - ay * ay, 0.0004))
    return cfg.r * cfg.rcScale * (0.8 + 0.2 * az)
}

internal fun cpcStartPose(cfg: CoinPileCollectCfg, s: DoubleArray, i: Int) {
    val o = i * CpcSTRIDE
    val u = cpcRnd(cfg.seed, i, 1)
    val x = cfg.r + (cfg.w - 2 * cfg.r) * (1 - (1 - u).pow(1.5))
    s[o + CpcX] = x
    s[o + CpcY] = -cfg.r - i * 46
    s[o + CpcPX] = s[o + CpcX]
    s[o + CpcPY] = s[o + CpcY]
}

internal fun cpcTiltOf(seed: Int, i: Int, out: DoubleArray) {
    val a = cpcRnd(seed, i, 11) * 6.2831853
    val k = cpcRnd(seed, i, 12)
    val lean = cpcRnd(seed, i, 13) < 0.16
    val m = if (lean) 0.62 + 0.3 * cpcRnd(seed, i, 14) else 0.06 + 0.4 * k * k
    out[0] = cos(a) * m
    out[1] = sin(a) * m
}

private val SAMPLE_R = DoubleArray(15)
private val SAMPLE_C = DoubleArray(15)
private val SAMPLE_S = DoubleArray(15)

private val sampleInit = run {
    SAMPLE_R[0] = 0.0
    SAMPLE_C[0] = 1.0
    SAMPLE_S[0] = 0.0
    for (q in 1 until 15) {
        val ang = if (q < 7) (q - 1) * 1.0471976 else (q - 7) * 0.7853982
        SAMPLE_R[q] = if (q < 7) 0.55 else 0.95
        SAMPLE_C[q] = cos(ang)
        SAMPLE_S[q] = sin(ang)
    }
    true
}

internal fun cpcResolveZ(cfg: CoinPileCollectCfg, s: DoubleArray, dt: Double) {
    check(sampleInit)
    val n = cfg.n
    val rr = cfg.r
    val hh = cfg.t
    for (k in 0 until n) {
        val i = cfg.order[k]
        val oi = i * CpcSTRIDE
        if (s[oi + CpcALIVE] < 0.5 || s[oi + CpcDRAIN] > 0) continue
        val ax = s[oi + CpcAX]
        val ay = s[oi + CpcAY]
        val azi = cpcFaceAz(ax, ay)
        val sinT = sqrt(ax * ax + ay * ay)
        var rx = 0.0
        var ry = 1.0
        val rz = 0.0
        if (abs(ay) > 0.9) {
            rx = 1.0
            ry = 0.0
        }
        var ux = ry * azi - rz * ay
        var uy = rz * ax - rx * azi
        var uz = rx * ay - ry * ax
        val ul = sqrt(ux * ux + uy * uy + uz * uz) + 1e-9
        ux /= ul
        uy /= ul
        uz /= ul
        val vx = ay * uz - azi * uy
        val vy = azi * ux - ax * uz
        val vz = ax * uy - ay * ux
        var need = hh * azi + rr * sinT + 0.05
        if (s[oi + CpcZB] > need) need = s[oi + CpcZB]
        for (m in 0 until k) {
            val j = cfg.order[m]
            val oj = j * CpcSTRIDE
            if (s[oj + CpcALIVE] < 0.5 || s[oj + CpcDRAIN] > 0) continue
            val cdx = s[oi + CpcX] - s[oj + CpcX]
            val cdy = s[oi + CpcY] - s[oj + CpcY]
            if (cdx * cdx + cdy * cdy > 4 * (rr + hh) * (rr + hh)) continue
            val jx = s[oj + CpcAX]
            val jy = s[oj + CpcAY]
            val azj = cpcFaceAz(jx, jy)
            val zj = s[oj + CpcZT]
            for (q in 0 until 15) {
                val r = SAMPLE_R[q] * rr
                val ca = SAMPLE_C[q]
                val sa = SAMPLE_S[q]
                val ox = r * (ca * ux + sa * vx)
                val oy = r * (ca * uy + sa * vy)
                val oz = r * (ca * uz + sa * vz)
                val dx = cdx + ox
                val dy = cdy + oy
                val top = (hh - jx * dx - jy * dy) / azj
                val rad2 = dx * dx + dy * dy + top * top - hh * hh
                if (rad2 > rr * rr) continue
                val z = zj + top - oz + azi * hh + 0.12
                if (z > need) need = z
            }
        }
        s[oi + CpcZT] = need
    }
    val kz = if (dt <= 0) 1.0 else min(dt / 0.05, 1.0)
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] < 0.5 || s[o + CpcDRAIN] > 0) continue
        s[o + CpcZ] += (s[o + CpcZT] - s[o + CpcZ]) * kz
    }
}

internal fun cpcStepSim(cfg: CoinPileCollectCfg, s: DoubleArray, dt: Double, inp: DoubleArray, ev: DoubleArray) {
    val n = cfg.n
    val w = cfg.w
    val h = cfg.h
    val g = cfg.gravity
    val rate = inp[CpcIN_RATE]
    ev[CpcEV_COLLECTED] = 0.0
    ev[CpcEV_IMPACT] = 0.0
    ev[CpcEV_CENTS] = 0.0
    ev[CpcEV_FLICK] = 0.0
    val flowTarget = if (rate > 0.01) 1.0 else 0.0
    val fk = if (flowTarget > ev[CpcEV_FLOW]) dt / 0.25 else dt / 0.12
    ev[CpcEV_FLOW] = cpcClamp(ev[CpcEV_FLOW] + (flowTarget - ev[CpcEV_FLOW]) * min(fk, 1.0), 0.0, 1.0)

    if (rate > 0.01) {
        ev[CpcEV_CREDIT] += rate * dt
        while (ev[CpcEV_CREDIT] >= 1) {
            var best = -1
            var bd = 1e12
            for (i in 0 until n) {
                val o = i * CpcSTRIDE
                if (s[o + CpcALIVE] < 0.5 || s[o + CpcDRAIN] > 0 || s[o + CpcCNT] < 0.5) continue
                val dx = cfg.mx - s[o + CpcX]
                val dy = cfg.my - s[o + CpcY]
                val d2 = dx * dx + dy * dy * 1.15
                if (d2 < bd) {
                    bd = d2
                    best = i
                }
            }
            if (best < 0) {
                ev[CpcEV_CREDIT] = 1.0
                break
            }
            val o = best * CpcSTRIDE
            s[o + CpcDRAIN] = 0.0001
            s[o + CpcSX] = s[o + CpcX]
            s[o + CpcSY] = s[o + CpcY]
            s[o + CpcZ0] = s[o + CpcZ]
            s[o + CpcR0AX] = s[o + CpcAX]
            s[o + CpcR0AY] = s[o + CpcAY]
            s[o + CpcDUR] = cpcClamp(sqrt(bd) / 360, 0.13, 0.36)
            ev[CpcEV_CREDIT] -= 1.0
        }
    } else {
        ev[CpcEV_CREDIT] = min(ev[CpcEV_CREDIT], 0.999)
    }

    var alive = 0
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] > 0.5 && s[o + CpcDRAIN] == 0.0 && s[o + CpcCNT] > 0.5) alive++
    }
    ev[CpcEV_ALIVE] = alive.toDouble()

    val damp = 0.9985
    val funnel = 900 * cpcClamp(rate / 14, 0.0, 1.4)
    val awakeAll = rate > 0.01 || inp[CpcIN_FINGER] > 0.5 || inp[CpcIN_GX] != 0.0
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] < 0.5 || s[o + CpcDRAIN] > 0) continue
        if (awakeAll) s[o + CpcSLEEP] = 0.0
        if (s[o + CpcSLEEP] >= 24) {
            s[o + CpcPX] = s[o + CpcX]
            s[o + CpcPY] = s[o + CpcY]
            continue
        }
        val x = s[o + CpcX]
        val y = s[o + CpcY]
        var vx = x - s[o + CpcPX]
        var vy = y - s[o + CpcPY]
        val spd = sqrt(vx * vx + vy * vy) / dt
        val dk = damp - 0.045 * (1 - cpcSmoothP(25.0, 140.0, spd))
        vx *= dk
        vy *= dk
        if (s[o + CpcR0X] > 0) s[o + CpcR0X] = max(s[o + CpcR0X] - dt, 0.0)
        var ax = inp[CpcIN_GX]
        var ay = g
        if (rate > 0.01) {
            val dx = cfg.mx - x
            val dy = cfg.my - y
            val d = sqrt(dx * dx + dy * dy) + 0.001
            if (d < cfg.reach) {
                val k = (1 - d / cfg.reach) * funnel
                ax += (dx / d) * k
                ay += (dy / d) * k * 0.6 - g * 0.25 * (1 - d / cfg.reach)
            }
        }
        vx += ax * dt * dt
        vy += ay * dt * dt
        s[o + CpcPX] = x
        s[o + CpcPY] = y
        s[o + CpcX] = x + vx
        s[o + CpcY] = y + vy
        if (rate <= 0.01 && inp[CpcIN_FLICKON] > 0.5 && s[o + CpcR0X] > 0 && s[o + CpcCNT] > 0.5) {
            val sp = sqrt(vx * vx + vy * vy) / dt
            val mdx = cfg.mx - (x + vx)
            val mdy = cfg.my - (y + vy)
            if (sp > CpcFLICK_SPEED && mdx * mdx + mdy * mdy < CpcFLICK_REACH * CpcFLICK_REACH && mdx * vx + mdy * vy > 0) {
                s[o + CpcDRAIN] = 0.0001
                s[o + CpcSX] = s[o + CpcX]
                s[o + CpcSY] = s[o + CpcY]
                s[o + CpcZ0] = s[o + CpcZ]
                s[o + CpcR0AX] = s[o + CpcAX]
                s[o + CpcR0AY] = s[o + CpcAY]
                s[o + CpcDUR] = 0.14
                ev[CpcEV_FLICK] += 1.0
            }
        }
    }

    var zTop = 0.0
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] > 0.5 && s[o + CpcDRAIN] == 0.0 && s[o + CpcZ] > zTop) zTop = s[o + CpcZ]
    }
    zTop += cfg.t * 3
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] < 0.5 || s[o + CpcDRAIN] == 0.0) continue
        val p = min(s[o + CpcDRAIN] + dt / s[o + CpcDUR], 1.0)
        s[o + CpcDRAIN] = p
        val e = p * p * (3 - 2 * p) * 0.35 + p * p * p * 0.65
        s[o + CpcX] = s[o + CpcSX] + (cfg.mx - s[o + CpcSX]) * e
        s[o + CpcY] = s[o + CpcSY] + (cfg.my - s[o + CpcSY]) * e
        s[o + CpcPX] = s[o + CpcX]
        s[o + CpcPY] = s[o + CpcY]
        s[o + CpcZ] = s[o + CpcZ0] + (zTop - s[o + CpcZ0]) * cpcSmoothP(0.0, 0.35, p)
        s[o + CpcGLOW] = 1.0
        val ddx = cfg.mx - s[o + CpcSX]
        val ddy = cfg.my - s[o + CpcSY]
        val dl = sqrt(ddx * ddx + ddy * ddy) + 0.001
        val tip = cpcSmoothP(0.2, 0.85, p)
        s[o + CpcAX] = s[o + CpcR0AX] + (-(ddy / dl) * 0.94 - s[o + CpcR0AX]) * tip
        s[o + CpcAY] = s[o + CpcR0AY] + ((ddx / dl) * 0.94 - s[o + CpcR0AY]) * tip
        if (p >= 1) {
            if (alive == 0 && s[o + CpcAUX] == 0.0) {
                s[o + CpcAUX] = 0.3
                ev[CpcEV_LASTSPIN] = 0.3
            }
            if (s[o + CpcAUX] > 0) {
                s[o + CpcAUX] = max(s[o + CpcAUX] - dt, 0.0001)
                ev[CpcEV_LASTSPIN] = s[o + CpcAUX]
                if (s[o + CpcAUX] > 0.0002) continue
            }
            s[o + CpcALIVE] = 0.0
            s[o + CpcCNT] = 0.0
            s[o + CpcDRAIN] = 0.0
            s[o + CpcAUX] = 0.0
            ev[CpcEV_COLLECTED] += 1.0
            ev[CpcEV_CENTS] += s[o + CpcVAL]
            ev[CpcEV_LAST] = i.toDouble()
        }
    }

    val fOn = inp[CpcIN_FINGER]
    val fr = inp[CpcIN_FR]
    for (it in 0 until 4) {
        for (i in 0 until n) {
            val oi = i * CpcSTRIDE
            if (s[oi + CpcALIVE] < 0.5) continue
            val drainI = s[oi + CpcDRAIN] > 0
            for (j in i + 1 until n) {
                val oj = j * CpcSTRIDE
                if (s[oj + CpcALIVE] < 0.5) continue
                val drainJ = s[oj + CpcDRAIN] > 0
                if (drainI && drainJ) continue
                val dz = abs(s[oi + CpcZB] - s[oj + CpcZB])
                val same = 1 - cpcSmoothP(cfg.layerGap * 0.4, cfg.layerGap * 1.7, dz)
                val f = cfg.overlap + (1 - cfg.overlap) * same
                val minD = (s[oi + CpcRC] + s[oj + CpcRC]) * f
                val dx = s[oj + CpcX] - s[oi + CpcX]
                val dy = s[oj + CpcY] - s[oi + CpcY]
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD || d2 < 1e-6) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val pen = minD - d
                var wi = 0.5
                var wj = 0.5
                if (drainI) {
                    wi = 0.0
                    wj = 1.0
                } else if (drainJ) {
                    wi = 1.0
                    wj = 0.0
                } else {
                    val sI = s[oi + CpcSLEEP] >= 24
                    val sJ = s[oj + CpcSLEEP] >= 24
                    if (sI && sJ) continue
                    if (sI) {
                        wi = 0.0
                        wj = 1.0
                        if (pen > 0.8) s[oi + CpcSLEEP] = 0.0
                    } else if (sJ) {
                        wi = 1.0
                        wj = 0.0
                        if (pen > 0.8) s[oj + CpcSLEEP] = 0.0
                    }
                }
                if (it == 0) {
                    val rv = (s[oj + CpcX] - s[oj + CpcPX] - (s[oi + CpcX] - s[oi + CpcPX])) * nx +
                        (s[oj + CpcY] - s[oj + CpcPY] - (s[oi + CpcY] - s[oi + CpcPY])) * ny
                    val sp = -rv / dt
                    if (sp > ev[CpcEV_IMPACT]) ev[CpcEV_IMPACT] = sp
                }
                s[oi + CpcX] -= nx * pen * wi
                s[oi + CpcY] -= ny * pen * wi
                s[oj + CpcX] += nx * pen * wj
                s[oj + CpcY] += ny * pen * wj
                val tx = -ny
                val ty = nx
                val rt = (s[oj + CpcX] - s[oj + CpcPX] - (s[oi + CpcX] - s[oi + CpcPX])) * tx +
                    (s[oj + CpcY] - s[oj + CpcPY] - (s[oi + CpcY] - s[oi + CpcPY])) * ty
                val mu = min(0.5 * same, 1.0) * 0.55
                s[oi + CpcX] += tx * rt * mu * wi
                s[oi + CpcY] += ty * rt * mu * wi
                s[oj + CpcX] -= tx * rt * mu * wj
                s[oj + CpcY] -= ty * rt * mu * wj
            }
        }
        for (i in 0 until n) {
            val o = i * CpcSTRIDE
            if (s[o + CpcALIVE] < 0.5 || s[o + CpcDRAIN] > 0) continue
            val rc = s[o + CpcRC]
            if (s[o + CpcSLEEP] >= 24 && fOn < 0.5) continue
            if (s[o + CpcX] < rc) s[o + CpcX] = rc
            if (s[o + CpcX] > w - rc) s[o + CpcX] = w - rc
            if (s[o + CpcY] > h - rc) {
                s[o + CpcY] = h - rc
                s[o + CpcPX] += (s[o + CpcX] - s[o + CpcPX]) * 0.35
            }
            if (cfg.keepOn > 0 && s[o + CpcX] < cfg.keepW + rc * 0.3 && s[o + CpcY] < cfg.keepH + rc * 0.6 && s[o + CpcY] > -rc * 2) {
                val over = cfg.keepH + rc * 0.6 - s[o + CpcY]
                s[o + CpcY] += min(over * 0.35, 8.0)
            }
            if (fOn > 0.5) {
                val dx = s[o + CpcX] - inp[CpcIN_FX]
                val dy = s[o + CpcY] - inp[CpcIN_FY]
                val d2 = dx * dx + dy * dy
                val m = rc * 0.9 + fr
                if (d2 < m * m && d2 > 1e-6) {
                    val d = sqrt(d2)
                    s[o + CpcX] += (dx / d) * (m - d)
                    s[o + CpcY] += (dy / d) * (m - d)
                    s[o + CpcSLEEP] = 0.0
                    s[o + CpcR0X] = CpcPUSH_MEMORY
                    if (s[o + CpcGLOW] < 0.15) s[o + CpcGLOW] = 0.15
                }
            }
        }
    }

    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        if (s[o + CpcALIVE] < 0.5) continue
        if (s[o + CpcDRAIN] == 0.0) {
            var vx = s[o + CpcX] - s[o + CpcPX]
            var vy = s[o + CpcY] - s[o + CpcPY]
            val maxv = 1400 * dt
            val sp = sqrt(vx * vx + vy * vy)
            if (sp > maxv) {
                vx *= maxv / sp
                vy *= maxv / sp
                s[o + CpcPX] = s[o + CpcX] - vx
                s[o + CpcPY] = s[o + CpcY] - vy
            }
            if (s[o + CpcSLEEP] >= 24) {
                if (sp > 0.6 * dt * 120) s[o + CpcSLEEP] = 0.0
                else {
                    s[o + CpcPX] = s[o + CpcX]
                    s[o + CpcPY] = s[o + CpcY]
                }
            } else {
                val rx = s[o + CpcX] - s[o + CpcSX]
                val ry = s[o + CpcY] - s[o + CpcSY]
                if (rx * rx + ry * ry < 0.12) {
                    s[o + CpcSLEEP] += 1.0
                    if (s[o + CpcSLEEP] >= 24) {
                        s[o + CpcPX] = s[o + CpcX]
                        s[o + CpcPY] = s[o + CpcY]
                    }
                } else {
                    s[o + CpcSX] = s[o + CpcX]
                    s[o + CpcSY] = s[o + CpcY]
                    s[o + CpcSLEEP] = 0.0
                }
            }
            val dx = cfg.mx - s[o + CpcX]
            val dy = cfg.my - s[o + CpcY]
            val d = sqrt(dx * dx + dy * dy)
            val river = 40 + 50 * cpcClamp(rate / 14, 0.0, 1.0)
            val target = ev[CpcEV_FLOW] * cpcSmoothP(river, 28.0, d) * (if (rate > 0.01 || ev[CpcEV_FLOW] > 0.05) 1.0 else 0.0)
            val k = if (target > s[o + CpcGLOW]) dt / 0.06 else dt / 0.28
            s[o + CpcGLOW] += (target - s[o + CpcGLOW]) * min(k, 1.0)
        }
    }
    cpcResolveZ(cfg, s, dt)
}

internal fun cpcSettleHeap(cfg: CoinPileCollectCfg, s: DoubleArray, steps: Int, release: Int) {
    val n = cfg.n
    val inp = DoubleArray(CpcIN_SIZE)
    val ev = DoubleArray(CpcEV_SIZE)
    val tl = DoubleArray(2)
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        cpcTiltOf(cfg.seed, i, tl)
        s[o + CpcAX] = tl[0]
        s[o + CpcAY] = tl[1]
        s[o + CpcPHI] = cpcRnd(cfg.seed, i, 21) * 6.2831853
        s[o + CpcFACE] = if (cpcRnd(cfg.seed, i, 22) < 0.5) 0.0 else 1.0
        s[o + CpcRC] = cpcRadiusOf(cfg, tl[0], tl[1])
        val layer = (i * 3 + floor(cpcRnd(cfg.seed, i, 23) * 2).toInt()) % cfg.layers
        s[o + CpcZB] = cfg.t * 1.0 + layer * cfg.layerGap + cpcRnd(cfg.seed, i, 24) * cfg.t * 0.5
        s[o + CpcZ] = s[o + CpcZB]
        s[o + CpcZT] = s[o + CpcZB]
        s[o + CpcRANK] = (layer * 100 + i).toDouble()
        s[o + CpcALIVE] = 0.0
        s[o + CpcDRAIN] = 0.0
        s[o + CpcGLOW] = 0.0
        cpcStartPose(cfg, s, i)
    }
    cfg.keepOn = 1
    cfg.order = (0 until n).sortedBy { s[it * CpcSTRIDE + CpcRANK] }.toIntArray()
    val dt = 1.0 / 120
    for (st in 0 until steps) {
        val k = st / release
        if (st % release == 0 && k < n) {
            s[k * CpcSTRIDE + CpcALIVE] = 1.0
            s[k * CpcSTRIDE + CpcCNT] = 1.0
            s[k * CpcSTRIDE + CpcY] = -cfg.r
            s[k * CpcSTRIDE + CpcPY] = -cfg.r - 6
        }
        cpcStepSim(cfg, s, dt, inp, ev)
    }
    cfg.keepOn = 0
    cpcResolveZ(cfg, s, 0.0)
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        s[o + CpcPX] = s[o + CpcX]
        s[o + CpcPY] = s[o + CpcY]
        s[o + CpcSLEEP] = 100.0
    }
    val hp = DoubleArray(n * CpcHS)
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        val b = i * CpcHS
        hp[b] = s[o + CpcX]; hp[b + 1] = s[o + CpcY]; hp[b + 2] = s[o + CpcZ]
        hp[b + 3] = s[o + CpcAX]; hp[b + 4] = s[o + CpcAY]; hp[b + 5] = s[o + CpcPHI]
        hp[b + 6] = s[o + CpcFACE]; hp[b + 7] = s[o + CpcRANK]; hp[b + 8] = s[o + CpcZB]
    }
    cfg.heap = hp
}

internal fun cpcOrderOf(cfg: CoinPileCollectCfg) {
    cfg.order = (0 until cfg.n).sortedBy { cfg.heap[it * CpcHS + 7] }.toIntArray()
}

internal fun cpcLoadHeap(cfg: CoinPileCollectCfg, s: DoubleArray) {
    val n = cfg.n
    val hp = cfg.heap
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        val h = i * CpcHS
        s[o + CpcX] = hp[h]
        s[o + CpcY] = hp[h + 1]
        s[o + CpcPX] = hp[h]
        s[o + CpcPY] = hp[h + 1]
        s[o + CpcZ] = hp[h + 2]
        s[o + CpcZT] = hp[h + 2]
        s[o + CpcAX] = hp[h + 3]
        s[o + CpcAY] = hp[h + 4]
        s[o + CpcPHI] = hp[h + 5]
        s[o + CpcFACE] = hp[h + 6]
        s[o + CpcRANK] = hp[h + 7]
        s[o + CpcRC] = cpcRadiusOf(cfg, hp[h + 3], hp[h + 4])
        s[o + CpcZB] = hp[h + 8]
        s[o + CpcALIVE] = 1.0
        s[o + CpcCNT] = 1.0
        s[o + CpcDRAIN] = 0.0
        s[o + CpcGLOW] = 0.0
        s[o + CpcSLEEP] = 100.0
        s[o + CpcAUX] = 0.0
        s[o + CpcR0X] = 0.0
    }
}
