package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val CPC_CUE_PRESS = 1
internal const val CPC_CUE_COIN = 2
internal const val CPC_CUE_CLICK = 3
internal const val CPC_CUE_EMPTY = 4
internal const val CPC_CUE_REFILL = 5
internal const val CPC_CUE_LATCH = 6
internal const val CPC_CUE_COLLECT = 7

/** the cfg of the teal tile: the baked heap for the default tile (a fresh settle for any other count or seed) */
internal fun cpcMakeCfg(coins: Int? = null, seed: Int? = null): CoinPileCollectCfg {
    val n = max(8, min(32, coins ?: CpcTheme.COIN_COUNT))
    val sd = seed ?: CpcTheme.SEED
    val cfg = CoinPileCollectCfg(
        n, CpcTheme.TEAL_W, CpcTheme.TEAL_H, CpcTheme.COIN_R, CpcTheme.COIN_H, CpcTheme.MOUTH_X, CpcTheme.MOUTH_Y,
        CpcTheme.KEEP_W, CpcTheme.KEEP_H, sd,
    )
    if (n == CpcTheme.COIN_COUNT && sd == CpcTheme.SEED) {
        cfg.heap = CpcHEAP.copyOf()
    } else {
        val s = DoubleArray(CpcNMAX * CpcSTRIDE)
        cpcSettleHeap(cfg, s, n * 14 + 300, 14)
    }
    cpcOrderOf(cfg)
    return cfg
}

/**
 * The engine hook of cpc-hook.ts without the UI toolkit: the shared state, the frame step (autoplay replay, live thumb,
 * restore after a touch, reduced motion) and the cues the host hears. Touches set the finger fields.
 */
internal class CoinPileCollectSim(
    val cfg: CoinPileCollectCfg,
    val totalCents: Int,
    val autoPlay: Boolean,
    val reduce: Boolean,
    val cue: (kind: Int, a: Double, b: Double) -> Unit,
) {
    val a = cpcMakeState()
    val inp = cpcMakeInputs()
    val ev = cpcMakeEvents()
    var theta = 0.0
    var uiMode = if (autoPlay && !reduce) 0 else 1 // 0 autoplay, 1 live, 2 restoring to rest
    private var acc = 0.0
    private var idleT = 0.0
    private var restoreT = 0.0
    private var th0 = 0.0
    private var th1 = 0.0
    var pinned = -1.0
    private var pinDone = -2.0

    // touch (UI thread)
    var tDown = 0 // 0 none, 1 on the pill, 2 in the pile
    var tX = 0.0
    var tY = 0.0
    var tSlide = 0.0
    private var command = 0.0
    private var cueClock = 0.0
    private var lastCue = 0.0
    private var coinSeq = 0.0
    private var collectedAcc = 0.0
    private var lastCb = -1.0

    init {
        reset()
    }

    fun reset() {
        cpcInitState(cfg, a, totalCents)
        uiMode = if (autoPlay && !reduce) 0 else 1
        acc = 0.0
        theta = 0.0
    }

    fun pour() { command = 1.0 }
    fun stop() { command = 2.0 }
    fun refill() { command = 3.0 }

    fun touchBegan(x: Double, y: Double) {
        val onPill = x >= CpcTheme.PILL_X - CpcGesture.hitSlopPill && x <= CpcTheme.PILL_X + CpcTheme.PILL_W + CpcGesture.hitSlopPill &&
            y >= CpcTheme.PILL_Y - CpcGesture.hitSlopPill && y <= CpcTheme.PILL_Y + CpcTheme.PILL_H + CpcGesture.hitSlopPill
        tDown = if (onPill) 1 else 2
        touchMoved(x, y)
        idleT = 0.0
        if (uiMode != 1) uiMode = 1
        if (onPill && a[CPC_CTL + C_MODE] == MODE_LIVE && a[CPC_CTL + C_EMPTYT] < 0) cue(CPC_CUE_PRESS, 0.0, 0.0)
    }

    fun touchMoved(x: Double, y: Double) {
        tX = x
        tY = y
        val cx = CpcTheme.PILL_X + CpcTheme.PILL_W / 2
        val cy = CpcTheme.PILL_Y + CpcTheme.PILL_H / 2
        tSlide = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
    }

    fun touchEnded() {
        tDown = 0
        tSlide = 0.0
        idleT = 0.0
    }

    /** seek the autoplay to loop time t and hold it (preview) */
    fun pin(t: Double) {
        pinned = t
        pinDone = -2.0
        uiMode = 0
    }

    private fun replayTo(t: Double) {
        cpcInitState(cfg, a, totalCents)
        for (q in 0 until CPC_EVENT_SIZE) ev[q] = 0.0
        val steps = floor(max(0.0, min(t, CpcLOOP)) / CpcDT + 1e-6).toInt()
        for (s in 0 until steps) cpcAutoStep(cfg, a, inp, ev)
    }

    /** one display frame; gx is the sideways gravity in pt/s^2 (0 when tilt is off) */
    fun frame(dtIn: Double, gxIn: Double) {
        val dt = min(dtIn, 0.05)
        if (pinned >= 0) {
            if (pinDone != pinned) {
                pinDone = pinned
                replayTo(pinned)
                theta = cpcXcoinAngle(cpcClockOf(a), a[CPC_CTL + C_KICKAT])
            }
            return
        }
        val mode = uiMode
        acc += dt
        val steps = min(floor(acc / CpcDT).toInt(), 8)
        acc -= floor(acc / CpcDT) * CpcDT
        if (steps <= 0) return

        val down = tDown
        val fx = tX
        val fy = tY
        val slide = tSlide
        val gx = if (mode == 1) gxIn else 0.0
        val cmd = command
        command = 0.0
        var collected = 0.0
        var cents = 0.0
        var impact = 0.0
        var flick = 0.0
        var emptied = 0.0
        var refilled = 0.0
        var latch = 0.0
        for (k in 0 until steps) {
            if (mode == 0) {
                if (a[CPC_CTL + C_STEP] >= (CpcLOOP / CpcDT).roundToInt()) {
                    cpcInitState(cfg, a, totalCents)
                    for (q in 0 until CPC_EVENT_SIZE) ev[q] = 0.0
                }
                cpcAutoStep(cfg, a, inp, ev)
            } else {
                inp[IN_HOLD] = if (down == 1) 1.0 else 0.0
                inp[IN_SLIDE] = slide
                inp[CpcIN_FINGER] = if (down == 2) 1.0 else 0.0
                inp[CpcIN_FX] = fx
                inp[CpcIN_FY] = fy
                inp[CpcIN_FR] = CpcGesture.fingerRadius
                inp[CpcIN_GX] = if (mode == 1) gx else 0.0
                inp[IN_REDUCED] = if (reduce) 1.0 else 0.0
                inp[IN_CMD] = if (k == 0) cmd else 0.0
                if (reduce) cpcReducedStep(cfg, a, inp, ev) else cpcEngineStep(cfg, a, inp, ev)
            }
            collected += ev[CpcEV_COLLECTED]
            cents += ev[CpcEV_CENTS]
            if (ev[CpcEV_IMPACT] > impact) impact = ev[CpcEV_IMPACT]
            flick += ev[CpcEV_FLICK]
            emptied += ev[EVX_EMPTY]
            refilled += ev[EVX_REFILLED]
            if (ev[EVX_LATCH] != 0.0) latch = ev[EVX_LATCH]
        }

        val clock = cpcClockOf(a)
        val kick = a[CPC_CTL + C_KICKAT]
        if (mode == 0) {
            theta = if (reduce) 0.0 else cpcXcoinAngle(clock, kick)
        } else if (mode == 1) {
            theta = if (reduce) 0.0 else cpcXcoinAngle(clock, kick)
            if (down == 0) idleT += dt else idleT = 0.0
            val quiet = a[CPC_CTL + C_MODE] == MODE_LIVE && a[CPC_CTL + C_EMPTYT] < 0 && a[CPC_CTL + C_VALVE] < 0.01 && a[CPC_CTL + C_LATCH] < 0.5
            if (autoPlay && !reduce && idleT >= CpcRESTORE_IDLE && quiet) {
                cpcBeginReturn(cfg, a, false)
                val w = 2 * PI / CpcXCoin.turn
                val start = cpcXcoinAngle(clock, kick)
                th0 = start
                th1 = cpcXcoinTarget(start, w)
                restoreT = 0.0
                uiMode = 2
            }
        } else if (mode == 2) {
            restoreT += dt
            theta = cpcXcoinEase(th0, th1, 2 * PI / CpcXCoin.turn, restoreT / CpcXCoin.restWindow)
            if (a[CPC_CTL + C_MODE] == MODE_LIVE && restoreT > 0.2) {
                cpcInitState(cfg, a, totalCents)
                theta = 0.0
                uiMode = 0
                acc = 0.0
                idleT = 0.0
            }
        }

        // what the host hears: only touches make cues, autoplay is silent
        if (mode != 0 && !reduce) {
            cueClock += dt
            val now = cueClock
            if (collected > 0) {
                coinSeq += collected
                collectedAcc += cents
                if (coinSeq % CpcGesture.coinTick < collected && now - lastCue > CpcGesture.hapticGap) {
                    lastCue = now
                    cue(CPC_CUE_COIN, 0.0, 0.0)
                }
            }
            if (flick > 0) cue(CPC_CUE_COIN, 1.0, 0.0)
            if (impact > CpcGesture.impactMin && now - lastCue > CpcGesture.clickGap) {
                lastCue = now
                cue(CPC_CUE_CLICK, impact, 0.0)
            }
            if (latch != 0.0) cue(CPC_CUE_LATCH, latch, 0.0)
            if (collectedAcc > 0 && (lastCb < 0 || now - lastCb > 0.1)) {
                cue(CPC_CUE_COLLECT, collectedAcc, a[CPC_CTL + C_TARGET])
                collectedAcc = 0.0
                lastCb = now
            }
            if (emptied > 0) cue(CPC_CUE_EMPTY, a[CPC_CTL + C_TOTAL], 0.0)
            if (refilled > 0) cue(CPC_CUE_REFILL, 0.0, 0.0)
        } else if (mode == 0) {
            coinSeq = 0.0
            collectedAcc = 0.0
        }
    }
}
