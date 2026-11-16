package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Port of cpc-engine.ts: the state machine around the solver. One flat DoubleArray holds the coins and, after them,
// a control block.

internal const val CPC_CTL = CpcNMAX * CpcSTRIDE
internal const val C_STEP = 0
internal const val C_MODE = 1
internal const val C_MT = 2
internal const val C_VALVE = 3
internal const val C_THR = 4
internal const val C_LATCH = 5
internal const val C_EMPTYT = 6
internal const val C_PRESS = 7
internal const val C_BULGE = 8
internal const val C_BULGEV = 9
internal const val C_LIT = 10
internal const val C_PILLA = 11
internal const val C_CENTS = 12
internal const val C_TARGET = 13
internal const val C_KICKAT = 14
internal const val C_HOLDPREV = 15
internal const val C_RETEND = 16
internal const val C_LANDED = 17
internal const val C_RET_ALL = 18
internal const val C_FLOW = 22
internal const val C_TOTAL = 23
internal const val C_N = 24
internal const val C_TAP = 25
internal const val C_FRESH = 26
internal const val C_CREDIT = 27
internal const val C_SFLOW = 28
internal const val CPC_CTL_SIZE = 32
internal const val CPC_STATE_SIZE = CPC_CTL + CPC_CTL_SIZE

internal const val IN_HOLD = 8
internal const val IN_SLIDE = 9
internal const val IN_REDUCED = 10
internal const val IN_CMD = 11
internal const val IN_UP = 12
internal const val IN_FLICKT = 13
internal const val CPC_INPUT_SIZE = 16

internal const val MODE_LIVE = 0.0
internal const val MODE_RETURN = 1.0
internal const val MODE_FADE_OUT = 2.0
internal const val MODE_FADE_IN = 3.0

internal const val EVX_EMPTY = 12
internal const val EVX_LANDED = 13
internal const val EVX_REFILLED = 14
internal const val EVX_LATCH = 15
internal const val CPC_EVENT_SIZE = 16

internal fun cpcMakeState() = DoubleArray(CPC_STATE_SIZE)
internal fun cpcMakeInputs(): DoubleArray {
    val inp = DoubleArray(CPC_INPUT_SIZE)
    inp[IN_FLICKT] = 99.0
    return inp
}
internal fun cpcMakeEvents() = DoubleArray(CPC_EVENT_SIZE)

internal fun cpcSetValues(cfg: CoinPileCollectCfg, a: DoubleArray, totalCents: Int) {
    val vals = DoubleArray(cfg.n)
    cpcCoinValues(cfg.n, totalCents, cfg.seed, vals)
    for (i in 0 until cfg.n) a[i * CpcSTRIDE + CpcVAL] = vals[i]
    a[CPC_CTL + C_TOTAL] = totalCents.toDouble()
    a[CPC_CTL + C_N] = cfg.n.toDouble()
}

internal fun cpcInitState(cfg: CoinPileCollectCfg, a: DoubleArray, totalCents: Int) {
    cpcLoadHeap(cfg, a)
    cpcSetValues(cfg, a, totalCents)
    val c = CPC_CTL
    a[c + C_STEP] = 0.0
    a[c + C_MODE] = MODE_LIVE
    a[c + C_MT] = 0.0
    a[c + C_VALVE] = 0.0
    a[c + C_THR] = 1.0
    a[c + C_LATCH] = 0.0
    a[c + C_EMPTYT] = -1.0
    a[c + C_PRESS] = 0.0
    a[c + C_BULGE] = 0.0
    a[c + C_BULGEV] = 0.0
    a[c + C_LIT] = 0.0
    a[c + C_PILLA] = 1.0
    a[c + C_CENTS] = totalCents.toDouble()
    a[c + C_TARGET] = totalCents.toDouble()
    a[c + C_KICKAT] = -1.0
    a[c + C_HOLDPREV] = 0.0
    a[c + C_RETEND] = 0.0
    a[c + C_LANDED] = 0.0
    a[c + C_RET_ALL] = 0.0
    a[c + C_FLOW] = 0.0
    a[c + C_TAP] = 0.0
    a[c + C_FRESH] = 0.0
    a[c + C_CREDIT] = 0.0
    a[c + C_SFLOW] = 0.0
}

internal fun cpcClockOf(a: DoubleArray): Double = a[CPC_CTL + C_STEP] * CpcDT

internal fun cpcCountCents(cfg: CoinPileCollectCfg, a: DoubleArray): Double {
    var sum = 0.0
    for (i in 0 until cfg.n) {
        val o = i * CpcSTRIDE
        if (a[o + CpcCNT] > 0.5) sum += a[o + CpcVAL]
    }
    return sum
}

private fun spawnPose(cfg: CoinPileCollectCfg, a: DoubleArray, i: Int, order: Int) {
    val o = i * CpcSTRIDE
    val h = i * CpcHS
    val rx = cfg.heap[h]
    val ry = cfg.heap[h + 1]
    val u = cpcRnd(cfg.seed, i, 41)
    val v = cpcRnd(cfg.seed, i, 42)
    val x = min(max(rx + (u - 0.5) * 60, cfg.r * 0.8), cfg.w - cfg.r * 0.8)
    val y = -cfg.r - CpcRain.spawnY - v * CpcRain.spawnSpread - (order % 3) * 14
    a[o + CpcR0X] = x
    a[o + CpcR0Y] = y
    a[o + CpcR0Z] = cfg.heap[h + 2] + 70
    val ang = cpcRnd(cfg.seed, i, 43) * 6.2831853
    val m = 0.55 + 0.4 * cpcRnd(cfg.seed, i, 44)
    a[o + CpcR0AX] = cos(ang) * m
    a[o + CpcR0AY] = sin(ang) * m
    a[o + CpcR0PHI] = cpcRnd(cfg.seed, i, 45) * 6.2831853
    val d = max(ry - y, 40.0)
    a[o + CpcDUR] = sqrt(2 * d / CpcRain.gravity)
}

internal fun cpcBeginReturn(cfg: CoinPileCollectCfg, a: DoubleArray, rain: Boolean) {
    val n = cfg.n
    val stagger = if (rain) CpcRain.stagger else CpcRain.glideStagger * 2
    var k = 0
    var end = 0.0
    for (q in 0 until n) {
        val i = cfg.order[q]
        val o = i * CpcSTRIDE
        val h = i * CpcHS
        val present = a[o + CpcALIVE] > 0.5 && a[o + CpcCNT] > 0.5 && a[o + CpcDRAIN] == 0.0 && !rain
        if (present) {
            a[o + CpcRKIND] = 0.0
            a[o + CpcR0X] = a[o + CpcX]
            a[o + CpcR0Y] = a[o + CpcY]
            a[o + CpcR0Z] = a[o + CpcZ]
            a[o + CpcR0AX] = a[o + CpcAX]
            a[o + CpcR0AY] = a[o + CpcAY]
            a[o + CpcR0PHI] = a[o + CpcPHI]
            a[o + CpcRT0] = 0.0
            a[o + CpcDUR] = CpcRain.glide
            end = max(end, CpcRain.glide)
        } else {
            a[o + CpcRKIND] = 1.0
            a[o + CpcCNT] = 0.0
            a[o + CpcALIVE] = 0.0
            a[o + CpcDRAIN] = 0.0
            a[o + CpcGLOW] = 0.0
            spawnPose(cfg, a, i, k)
            a[o + CpcRT0] = k * stagger
            end = max(end, k * stagger + a[o + CpcDUR] + CpcRain.bounce)
            k++
        }
        a[o + CpcFACE] = cfg.heap[h + 6]
    }
    val c = CPC_CTL
    a[c + C_MODE] = MODE_RETURN
    a[c + C_MT] = 0.0
    a[c + C_RETEND] = end + 0.05
    a[c + C_LANDED] = 0.0
    a[c + C_RET_ALL] = if (rain) 1.0 else 0.0
    a[c + C_LATCH] = 0.0
    a[c + C_VALVE] = 0.0
    a[c + C_LIT] = 0.0
    a[c + C_CREDIT] = 0.0
    a[c + C_SFLOW] = 0.0
    a[c + C_TARGET] = cpcCountCents(cfg, a)
}

private fun returnPose(cfg: CoinPileCollectCfg, a: DoubleArray, i: Int, mt: Double, ev: DoubleArray) {
    val o = i * CpcSTRIDE
    val h = i * CpcHS
    val s = mt - a[o + CpcRT0]
    val rx = cfg.heap[h]
    val ry = cfg.heap[h + 1]
    val rz = cfg.heap[h + 2]
    val rax = cfg.heap[h + 3]
    val ray = cfg.heap[h + 4]
    val rphi = cfg.heap[h + 5]
    if (a[o + CpcRKIND] < 0.5) {
        val k = cpcSmooth(0.0, 1.0, s / a[o + CpcDUR])
        a[o + CpcX] = cpcMix(a[o + CpcR0X], rx, k)
        a[o + CpcY] = cpcMix(a[o + CpcR0Y], ry, k)
        a[o + CpcZ] = cpcMix(a[o + CpcR0Z], rz, k)
        a[o + CpcAX] = cpcMix(a[o + CpcR0AX], rax, k)
        a[o + CpcAY] = cpcMix(a[o + CpcR0AY], ray, k)
        a[o + CpcPHI] = rphi
        a[o + CpcPX] = a[o + CpcX]
        a[o + CpcPY] = a[o + CpcY]
        return
    }
    if (s < 0) {
        a[o + CpcALIVE] = 0.0
        return
    }
    val tau = a[o + CpcDUR]
    a[o + CpcALIVE] = 1.0
    if (s < tau) {
        val k = s / tau
        a[o + CpcY] = a[o + CpcR0Y] + (ry - a[o + CpcR0Y]) * k * k
        val dx = cpcSmooth(0.0, 1.0, k)
        a[o + CpcX] = cpcMix(a[o + CpcR0X], rx, dx)
        val tk = cpcSmooth(0.25, 1.0, k)
        a[o + CpcAX] = cpcMix(a[o + CpcR0AX], rax, tk)
        a[o + CpcAY] = cpcMix(a[o + CpcR0AY], ray, tk)
        a[o + CpcPHI] = rphi + (a[o + CpcR0PHI] - rphi) * (1 - k) + (1 - k) * 9.4
        a[o + CpcZ] = rz + (a[o + CpcR0Z] - rz) * (1 - k)
        a[o + CpcCNT] = 0.0
    } else {
        val u = s - tau
        if (a[o + CpcCNT] < 0.5) {
            a[o + CpcCNT] = 1.0
            ev[EVX_LANDED] += 1.0
            a[CPC_CTL + C_LANDED] += 1.0
            a[CPC_CTL + C_TARGET] += a[o + CpcVAL]
        }
        val k = cpcClamp01(u / CpcRain.bounce)
        val decay = exp(-u * 9)
        a[o + CpcY] = ry
        a[o + CpcX] = rx
        a[o + CpcZ] = rz + CpcRain.bounceZ * decay * abs(sin(u * 17))
        val wob = decay * sin(u * 22) * 0.12 * (1 - k)
        a[o + CpcAX] = rax + wob
        a[o + CpcAY] = ray - wob * 0.6
        a[o + CpcPHI] = rphi
    }
    a[o + CpcPX] = a[o + CpcX]
    a[o + CpcPY] = a[o + CpcY]
}

private fun countAlive(cfg: CoinPileCollectCfg, a: DoubleArray): Int {
    var c = 0
    for (i in 0 until cfg.n) {
        val o = i * CpcSTRIDE
        if (a[o + CpcALIVE] > 0.5 && a[o + CpcCNT] > 0.5 && a[o + CpcDRAIN] == 0.0) c++
    }
    return c
}

private fun countDraining(cfg: CoinPileCollectCfg, a: DoubleArray): Int {
    var c = 0
    for (i in 0 until cfg.n) {
        val o = i * CpcSTRIDE
        if (a[o + CpcALIVE] > 0.5 && a[o + CpcDRAIN] > 0) c++
    }
    return c
}

private fun stepFade(cfg: CoinPileCollectCfg, a: DoubleArray, ev: DoubleArray) {
    val c = CPC_CTL
    a[c + C_MT] += CpcDT
    val mt = a[c + C_MT]
    val n = cfg.n
    val out = a[c + C_MODE] == MODE_FADE_OUT
    val group = 0.12
    for (i in 0 until n) {
        val o = i * CpcSTRIDE
        val dx = cfg.mx - a[o + CpcX]
        val dy = cfg.my - a[o + CpcY]
        val d = sqrt(dx * dx + dy * dy)
        val g = if (d < 90) 0 else if (d < 150) 1 else 2
        val gi = if (out) g else 2 - g
        val k = cpcClamp01((mt - gi * group) / group)
        val al = if (out) 1 - k else k
        a[o + CpcALIVE] = al
        a[o + CpcCNT] = if (al > 0.5) 1.0 else 0.0
        a[o + CpcGLOW] = 0.0
    }
    a[c + C_TARGET] = cpcCountCents(cfg, a)
    a[c + C_CENTS] = a[c + C_TARGET]
    if (mt >= group * 3 + 0.02) {
        if (out) {
            a[c + C_MODE] = MODE_LIVE
            a[c + C_EMPTYT] = 0.0
            a[c + C_KICKAT] = cpcClockOf(a)
            a[c + C_PILLA] = CpcEmpty.pillAlpha
            a[c + C_MT] = 0.0
            ev[EVX_EMPTY] = 1.0
        } else {
            cpcLoadHeap(cfg, a)
            a[c + C_MODE] = MODE_LIVE
            a[c + C_EMPTYT] = -1.0
            a[c + C_TARGET] = a[c + C_TOTAL]
            a[c + C_CENTS] = a[c + C_TOTAL]
            a[c + C_PILLA] = 1.0
            ev[EVX_REFILLED] = 1.0
        }
    }
}

internal fun cpcEngineStep(cfg: CoinPileCollectCfg, a: DoubleArray, inp: DoubleArray, ev: DoubleArray) {
    val dt = CpcDT
    val c = CPC_CTL
    var mode = a[c + C_MODE]
    val n = cfg.n
    for (q in 0 until CPC_EVENT_SIZE) ev[q] = 0.0
    a[c + C_STEP] += 1.0
    val cmd = inp[IN_CMD]
    inp[IN_CMD] = 0.0

    val hold = if (inp[IN_HOLD] > 0.5) 1 else 0
    val prevHold = a[c + C_HOLDPREV]
    val latched = a[c + C_LATCH] > 0.5
    var held = hold
    if (hold > 0.5 && prevHold < 0.5) {
        a[c + C_TAP] = 0.0
        a[c + C_FRESH] = if (latched) 1.0 else 0.0
        if (latched) {
            a[c + C_LATCH] = 0.0
            ev[EVX_LATCH] = -1.0
        }
    }
    if (hold > 0.5) a[c + C_TAP] += dt
    if (hold < 0.5 && prevHold > 0.5) {
        if (a[c + C_TAP] < 0.18 && a[c + C_FRESH] < 0.5 && mode == MODE_LIVE && a[c + C_EMPTYT] < 0) {
            a[c + C_LATCH] = 1.0
            ev[EVX_LATCH] = 1.0
        }
        a[c + C_FRESH] = 0.0
    }
    if (a[c + C_FRESH] > 0.5) held = 0
    a[c + C_HOLDPREV] = hold.toDouble()
    val press = a[c + C_PRESS]
    val pressTarget = if (hold > 0.5 && mode == MODE_LIVE) 1.0 else 0.0
    a[c + C_PRESS] = press + (pressTarget - press) * min(dt / CpcPill.pressTau, 1.0)

    if (cmd == 1.0) a[c + C_LATCH] = if (mode == MODE_LIVE && a[c + C_EMPTYT] < 0) 1.0 else a[c + C_LATCH]
    if (cmd == 2.0) a[c + C_LATCH] = 0.0
    if (cmd == 3.0 && mode == MODE_LIVE) {
        cpcBeginReturn(cfg, a, a[c + C_TARGET] == 0.0)
        mode = MODE_RETURN
    }

    if (mode == MODE_LIVE) {
        val alive = countAlive(cfg, a)
        val lat = a[c + C_LATCH] > 0.5
        val want = (held > 0.5 || lat) && alive > 0 && a[c + C_EMPTYT] < 0 && inp[IN_REDUCED] < 0.5
        val v = a[c + C_VALVE]
        if (want && v < 0.001 && a[c + C_CREDIT] < 0.7) a[c + C_CREDIT] = 0.7
        val nv = v + ((if (want) 1 else 0) - v) * min(dt / (if (want) CpcValve.attack else CpcValve.release), 1.0)
        a[c + C_VALVE] = if (nv < 0.0005) 0.0 else nv
        val slide = if (held > 0.5 && !lat) inp[IN_SLIDE] else 0.0
        val thrTarget = 1 - (1 - CpcValve.throttleMin) * cpcSmooth(0.0, CpcValve.throttleSlide, slide)
        a[c + C_THR] += (thrTarget - a[c + C_THR]) * min(dt / CpcValve.throttleLag, 1.0)
        val rate = CpcValve.rate * a[c + C_VALVE] * a[c + C_THR]
        inp[CpcIN_RATE] = rate
        inp[IN_FLICKT] = if (inp[CpcIN_FINGER] > 0.5) 0.0 else inp[IN_FLICKT] + dt
        inp[CpcIN_FLICKON] = if (inp[IN_FLICKT] < 0.6) 1.0 else 0.0
        ev[CpcEV_CREDIT] = a[c + C_CREDIT]
        ev[CpcEV_FLOW] = a[c + C_SFLOW]
        cpcStepSim(cfg, a, dt, inp, ev)
        a[c + C_CREDIT] = ev[CpcEV_CREDIT]
        a[c + C_SFLOW] = ev[CpcEV_FLOW]
        a[c + C_FLOW] = cpcClamp01(rate / CpcValve.rate)

        val coll = ev[CpcEV_COLLECTED]
        if (coll > 0) a[c + C_BULGEV] += coll * 1.8
        a[c + C_TARGET] = cpcCountCents(cfg, a)

        val now = countAlive(cfg, a)
        val draining = countDraining(cfg, a)
        if (now == 0 && draining == 0 && ev[CpcEV_LASTSPIN] <= 0 && a[c + C_EMPTYT] < 0 && a[c + C_TARGET] == 0.0) {
            a[c + C_EMPTYT] = 0.0
            a[c + C_KICKAT] = cpcClockOf(a)
            a[c + C_LATCH] = 0.0
            ev[EVX_EMPTY] = 1.0
        } else if (a[c + C_EMPTYT] >= 0) {
            a[c + C_EMPTYT] += dt
            if (a[c + C_EMPTYT] >= CpcEmpty.rainDelay && inp[IN_REDUCED] < 0.5) cpcBeginReturn(cfg, a, true)
        }
        val pillTarget = if (a[c + C_EMPTYT] >= 0) CpcEmpty.pillAlpha else 1.0
        a[c + C_PILLA] += (pillTarget - a[c + C_PILLA]) * min(dt / CpcPill.alphaTau, 1.0)
        val litTarget = if (a[c + C_FLOW] > 0.05) 1.0 else 0.0
        a[c + C_LIT] += (litTarget - a[c + C_LIT]) * min(dt / CpcPill.litTau, 1.0)
    } else if (mode == MODE_RETURN) {
        a[c + C_MT] += dt
        val mt = a[c + C_MT]
        for (i in 0 until n) returnPose(cfg, a, i, mt, ev)
        val total = a[c + C_TOTAL]
        val frac = if (total > 0) a[c + C_TARGET] / total else 1.0
        val full = a[c + C_RET_ALL] > 0.5
        val pillTarget = if (full) CpcEmpty.pillAlpha + (1 - CpcEmpty.pillAlpha) * cpcClamp01(frac) else 1.0
        a[c + C_PILLA] += (pillTarget - a[c + C_PILLA]) * min(dt / CpcPill.alphaTau, 1.0)
        a[c + C_LIT] += (0 - a[c + C_LIT]) * min(dt / CpcPill.litTau, 1.0)
        a[c + C_FLOW] = 0.0
        a[c + C_VALVE] = 0.0
        if (mt >= a[c + C_RETEND]) {
            cpcLoadHeap(cfg, a)
            a[c + C_MODE] = MODE_LIVE
            a[c + C_EMPTYT] = -1.0
            a[c + C_KICKAT] = if (full) a[c + C_KICKAT] else -1.0
            a[c + C_TARGET] = a[c + C_TOTAL]
            a[c + C_PILLA] = 1.0
            a[c + C_LATCH] = 0.0
            ev[EVX_REFILLED] = 1.0
        }
    } else {
        stepFade(cfg, a, ev)
    }

    val bw = 2 * Math.PI / CpcPill.bulgeResponse
    val zeta = CpcPill.bulgeZeta
    var b = a[c + C_BULGE]
    var bv = a[c + C_BULGEV]
    bv += (-bw * bw * b - 2 * zeta * bw * bv) * dt
    b += bv * dt
    a[c + C_BULGE] = b
    a[c + C_BULGEV] = bv
    val cur = a[c + C_CENTS]
    val tgt = a[c + C_TARGET]
    val nc = cur + (tgt - cur) * min(dt / CpcROLL_TAU, 1.0)
    a[c + C_CENTS] = if (abs(nc - tgt) < 0.004) tgt else nc
}

internal fun cpcReducedStep(cfg: CoinPileCollectCfg, a: DoubleArray, inp: DoubleArray, ev: DoubleArray) {
    val c = CPC_CTL
    val mode = a[c + C_MODE]
    a[c + C_STEP] += 1.0
    for (q in 0 until CPC_EVENT_SIZE) ev[q] = 0.0
    val hold = inp[IN_HOLD] > 0.5
    val prev = a[c + C_HOLDPREV] > 0.5
    a[c + C_HOLDPREV] = if (hold) 1.0 else 0.0
    val cmd = inp[IN_CMD]
    inp[IN_CMD] = 0.0
    if (mode == MODE_LIVE) {
        if (a[c + C_EMPTYT] < 0) {
            if ((hold && !prev) || cmd == 1.0) {
                a[c + C_MODE] = MODE_FADE_OUT
                a[c + C_MT] = 0.0
                a[c + C_PRESS] = 1.0
            }
        } else {
            a[c + C_EMPTYT] += CpcDT
            a[c + C_PRESS] = 0.0
            if (a[c + C_EMPTYT] >= 1.2) {
                a[c + C_MODE] = MODE_FADE_IN
                a[c + C_MT] = 0.0
            }
        }
        if (!hold) a[c + C_PRESS] = 0.0
    } else {
        stepFade(cfg, a, ev)
    }
}

internal fun cpcAutoStep(cfg: CoinPileCollectCfg, a: DoubleArray, inp: DoubleArray, ev: DoubleArray) {
    val t = cpcClockOf(a)
    if (t < 0.62) {
        for (k in 0 until 2) {
            val i = cfg.order[cfg.n - 1 - k]
            val h = i * CpcHS
            val w = if (t < 0.6) 0.035 * exp(-t * 5.5) * sin(t * 24 + k * 1.7) else 0.0
            a[i * CpcSTRIDE + CpcAX] = cfg.heap[h + 3] + w
            a[i * CpcSTRIDE + CpcAY] = cfg.heap[h + 4] - w * 0.6
        }
    }
    cpcScriptAt(t, inp, IN_HOLD)
    inp[CpcIN_FINGER] = 0.0
    cpcEngineStep(cfg, a, inp, ev)
}

internal fun cpcResetInputs(inp: DoubleArray) {
    for (q in 0 until CPC_INPUT_SIZE) inp[q] = 0.0
    inp[IN_FLICKT] = 99.0
}
