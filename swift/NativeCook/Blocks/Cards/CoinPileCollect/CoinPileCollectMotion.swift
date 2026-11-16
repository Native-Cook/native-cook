import Foundation

/// Direct fixed-step translation of cpc-motion, cpc-physics and cpc-engine. All arithmetic stays Double.
enum CoinPileCollectMotion {
    static let LOOP: Double = 8.0
    static let DT: Double = 1 / 120
    enum VALVE {
        static let rate: Double = 20
        static let attack: Double = 0.25
        static let release: Double = 0.12
        static let throttleMin: Double = 0.15
        static let throttleSlide: Double = 120
        static let throttleLag: Double = 0.08
    }
    enum SCRIPT {
        static let down1: Double = 0.5
        static let up1: Double = 1.55
        static let down2: Double = 1.75
        static let slideOn: CoinPileCollectBuffer = CoinPileCollectBuffer([1.75, 1.95])
        static let slideOff: CoinPileCollectBuffer = CoinPileCollectBuffer([2.45, 2.8])
        static let slideMax: Double = 100
        static let end: Double = 3.9
    }
    enum EMPTY {
        static let rainDelay: Double = 0.6
        static let pillAlpha: Double = 0.55
    }
    enum RAIN {
        static let stagger: Double = 0.062
        static let gravity: Double = 2400
        static let spawnY: Double = 46
        static let spawnSpread: Double = 80
        static let glide: Double = 0.7
        static let glideStagger: Double = 0.02
        static let bounce: Double = 0.34
        static let bounceZ: Double = 9
    }
    static let RESTORE_IDLE: Double = 2.0
    enum XCOIN {
        static let turn: Double = 4.0
        static let kickDelay: Double = 0.08
        static let kickTurns: Double = 2
        static let kickLength: Double = 0.6
        static let restWindow: Double = 0.7
        static let tilt: Double = 0.16
    }
    enum PILL {
        static let pressScale: Double = 0.97
        static let bulge: Double = 0.035
        enum bulgeSpring {
            static let response: Double = 0.2
            static let zeta: Double = 0.5
        }
        static let lineMax: Double = 0.6
        static let litTau: Double = 0.14
        static let pressTau: Double = 0.045
        static let alphaTau: Double = 0.12
    }
    enum ROLL {
        static let tau: Double = 0.04
    }
    enum GESTURE {
        static let tapMax: Double = 0.18
        static let fingerRadius: Double = 22
        static let hitSlopPill: Double = 8
        static let coinTick: Double = 3
        static let hapticGap: Double = 0.12
        static let impactMin: Double = 200
        static let clickGap: Double = 0.12
        static let tiltG: Double = 0.25
    }
    enum SPRING {
        enum snap {
            static let damping: Double = 30.5
            static let stiffness: Double = 322.3
            static let mass: Double = 1
        }
        enum glide {
            static let damping: Double = 22.1
            static let stiffness: Double = 157.9
            static let mass: Double = 1
        }
        enum press {
            static let damping: Double = 18
            static let stiffness: Double = 380
            static let mass: Double = 1
        }
    }
    static func clamp01(_ v: Double) -> Double {
        return v < 0 ? 0 : v > 1 ? 1 : v
    }

    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double {
        return a + (b - a) * k
    }

    static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
        let k: Double = clamp01((v - a) / (b - a))
        return k * k * (3 - 2 * k)
    }

    static func easeOut(_ k: Double) -> Double {
        let u: Double = 1 - clamp01(k)
        return 1 - u * u * u
    }

    static func easeInOut(_ k: Double) -> Double {
        let x: Double = clamp01(k)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 {
            return 0
        }
        let w: Double = (2 * Double.pi) / response
        if zeta >= 1 {
            return 1 - exp(-w * t) * (1 + w * t)
        }
        let wd: Double = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    static func scriptAt(_ t: Double, _ out: CoinPileCollectBuffer, _ k: Double) {
        let held: Bool = (t >= SCRIPT.down1 && t < SCRIPT.up1) || (t >= SCRIPT.down2 && t < SCRIPT.end)
        let on: Double = smooth(SCRIPT.slideOn[0], SCRIPT.slideOn[1], t)
        let off: Double = smooth(SCRIPT.slideOff[0], SCRIPT.slideOff[1], t)
        out[k] = held ? 1 : 0
        out[k + 1] = SCRIPT.slideMax * on * (1 - off)
    }

    static func xcoinAngle(_ t: Double, _ kickAt: Double) -> Double {
        let w: Double = (2 * Double.pi) / XCOIN.turn
        var a: Double = w * t
        if kickAt >= 0 {
            let k: Double = (t - kickAt - XCOIN.kickDelay) / XCOIN.kickLength
            if k > 0 {
                let e: Double = k >= 1 ? 1 : easeInOut(k) * 1.0
                let over: Double = k >= 1 ? 0.03 * exp(-(k - 1) * 9) * sin((k - 1) * 22) : 0
                a += 2 * Double.pi * XCOIN.kickTurns * (e + over)
            }
        }
        return a
    }

    static func xcoinEase(_ th0: Double, _ th1: Double, _ w: Double, _ s: Double) -> Double {
        let u: Double = clamp01(s)
        let T: Double = XCOIN.restWindow
        let h00: Double = 2 * u * u * u - 3 * u * u + 1
        let h10: Double = u * u * u - 2 * u * u + u
        let h01: Double = -2 * u * u * u + 3 * u * u
        return h00 * th0 + h10 * T * w + h01 * th1
    }

    static func xcoinTarget(_ th0: Double, _ w: Double) -> Double {
        let ahead: Double = th0 + 0.5 * w * XCOIN.restWindow
        return 2 * Double.pi * ceil(ahead / (2 * Double.pi))
    }

    static let STRIDE: Double = 32
    static let X: Double = 0
    static let Y: Double = 1
    static let PX: Double = 2
    static let PY: Double = 3
    static let Z: Double = 4
    static let AX: Double = 5
    static let AY: Double = 6
    static let PHI: Double = 7
    static let GLOW: Double = 8
    static let ALIVE: Double = 9
    static let DRAIN: Double = 10
    static let VAL: Double = 11
    static let FACE: Double = 12
    static let RC: Double = 13
    static let SLEEP: Double = 14
    static let AUX: Double = 15
    static let SX: Double = 16
    static let SY: Double = 17
    static let DUR: Double = 18
    static let Z0: Double = 19
    static let RANK: Double = 20
    static let ZT: Double = 21
    static let ZB: Double = 22
    static let CNT: Double = 23
    static let R0X: Double = 24
    static let R0Y: Double = 25
    static let R0Z: Double = 26
    static let R0AX: Double = 27
    static let R0AY: Double = 28
    static let R0PHI: Double = 29
    static let RT0: Double = 30
    static let RKIND: Double = 31
    static let NMAX: Double = 32
    static let FLICK_SPEED: Double = 300
    static let FLICK_REACH: Double = 30
    static let PUSH_MEMORY: Double = 0.5
    static let HS: Double = 9
    static let EV_COLLECTED: Double = 0
    static let EV_LAST: Double = 1
    static let EV_IMPACT: Double = 2
    static let EV_CREDIT: Double = 3
    static let EV_ALIVE: Double = 4
    static let EV_FLOW: Double = 5
    static let EV_LASTSPIN: Double = 6
    static let EV_CENTS: Double = 7
    static let EV_FLICK: Double = 8
    static let EV_SIZE: Double = 12
    static let IN_RATE: Double = 0
    static let IN_FINGER: Double = 1
    static let IN_FX: Double = 2
    static let IN_FY: Double = 3
    static let IN_GX: Double = 4
    static let IN_FR: Double = 5
    static let IN_FLICKON: Double = 6
    static let IN_SIZE: Double = 8
    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double {
        return v < a ? a : v > b ? b : v
    }

    static func coinValues(_ n: Double, _ totalCents: Double, _ seed: Double, _ out: CoinPileCollectBuffer) {
        var sum: Double = 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let wgt: Double = 0.55 + rnd(seed, i, 91) * 0.9
                out[i] = wgt
                sum += wgt
            }
        }
        var acc: Double = 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let c: Double = floor((out[i] / sum) * totalCents)
                out[i] = c
                acc += c
            }
        }
        var rest: Double = totalCents - acc
        var i: Double = 0
        while rest > 0 {
            out[i.truncatingRemainder(dividingBy: n)] += 1
            rest -= 1
            i += 1
        }
    }

    static func faceAz(_ ax: Double, _ ay: Double) -> Double {
        return sqrt(max(1 - ax * ax - ay * ay, 0.0004))
    }

    static func radiusOf(_ cfg: CoinPileCollectConfig, _ ax: Double, _ ay: Double) -> Double {
        let az: Double = sqrt(max(1 - ax * ax - ay * ay, 0.0004))
        return cfg.r * cfg.rcScale * (0.8 + 0.2 * az)
    }

    static func resolveZ(_ cfg: CoinPileCollectConfig, _ S: CoinPileCollectBuffer, _ dt: Double) {
        let n: Double = cfg.n
        let R: Double = cfg.r
        let Hh: Double = cfg.t
        do {
            var k: Double = 0
            while k < n {
                defer { k += 1 }
                let i: Double = cfg.order[k]
                let oi: Double = i * STRIDE
                if S[oi + ALIVE] < 0.5 || S[oi + DRAIN] > 0 {
                    continue
                }
                let ax: Double = S[oi + AX]
                let ay: Double = S[oi + AY]
                let azi: Double = faceAz(ax, ay)
                let sinT: Double = sqrt(ax * ax + ay * ay)
                var rx: Double = 0
                var ry: Double = 1
                let rz: Double = 0
                if abs(ay) > 0.9 {
                    rx = 1
                    ry = 0
                }
                var ux: Double = ry * azi - rz * ay
                var uy: Double = rz * ax - rx * azi
                var uz: Double = rx * ay - ry * ax
                let ul: Double = sqrt(ux * ux + uy * uy + uz * uz) + 1e-9
                ux /= ul
                uy /= ul
                uz /= ul
                let vx: Double = ay * uz - azi * uy
                let vy: Double = azi * ux - ax * uz
                let vz: Double = ax * uy - ay * ux
                var need: Double = Hh * azi + R * sinT + 0.05
                if S[oi + ZB] > need {
                    need = S[oi + ZB]
                }
                do {
                    var m: Double = 0
                    while m < k {
                        defer { m += 1 }
                        let j: Double = cfg.order[m]
                        let oj: Double = j * STRIDE
                        if S[oj + ALIVE] < 0.5 || S[oj + DRAIN] > 0 {
                            continue
                        }
                        let cdx: Double = S[oi + X] - S[oj + X]
                        let cdy: Double = S[oi + Y] - S[oj + Y]
                        if cdx * cdx + cdy * cdy > 4 * (R + Hh) * (R + Hh) {
                            continue
                        }
                        let jx: Double = S[oj + AX]
                        let jy: Double = S[oj + AY]
                        let azj: Double = faceAz(jx, jy)
                        let zj: Double = S[oj + ZT]
                        do {
                            var q: Double = 0
                            while q < 15 {
                                defer { q += 1 }
                                let r: Double = SAMPLE_R[q] * R
                                let ca: Double = SAMPLE_C[q]
                                let sa: Double = SAMPLE_S[q]
                                let ox: Double = r * (ca * ux + sa * vx)
                                let oy: Double = r * (ca * uy + sa * vy)
                                let oz: Double = r * (ca * uz + sa * vz)
                                let dx: Double = cdx + ox
                                let dy: Double = cdy + oy
                                let top: Double = (Hh - jx * dx - jy * dy) / azj
                                let rad2: Double = dx * dx + dy * dy + top * top - Hh * Hh
                                if rad2 > R * R {
                                    continue
                                }
                                let z: Double = zj + top - oz + azi * Hh + 0.12
                                if z > need {
                                    need = z
                                }
                            }
                        }
                    }
                }
                S[oi + ZT] = need
            }
        }
        let kz: Double = dt <= 0 ? 1 : min(dt / 0.05, 1)
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0 {
                    continue
                }
                S[o + Z] += (S[o + ZT] - S[o + Z]) * kz
            }
        }
    }

    static func stepSim(_ cfg: CoinPileCollectConfig, _ S: CoinPileCollectBuffer, _ dt: Double, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer) {
        let n: Double = cfg.n
        let w: Double = cfg.w
        let h: Double = cfg.h
        let g: Double = cfg.gravity
        let rate: Double = inp[IN_RATE]
        ev[EV_COLLECTED] = 0
        ev[EV_IMPACT] = 0
        ev[EV_CENTS] = 0
        ev[EV_FLICK] = 0
        let flowTarget: Double = rate > 0.01 ? 1 : 0
        let fk: Double = flowTarget > ev[EV_FLOW] ? dt / 0.25 : dt / 0.12
        ev[EV_FLOW] = clamp(ev[EV_FLOW] + (flowTarget - ev[EV_FLOW]) * min(fk, 1), 0, 1)
        if rate > 0.01 {
            ev[EV_CREDIT] += rate * dt
            while ev[EV_CREDIT] >= 1 {
                var best: Double = -1
                var bd: Double = 1e12
                do {
                    var i: Double = 0
                    while i < n {
                        defer { i += 1 }
                        let o: Double = i * STRIDE
                        if S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0 || S[o + CNT] < 0.5 {
                            continue
                        }
                        let dx: Double = cfg.mx - S[o + X]
                        let dy: Double = cfg.my - S[o + Y]
                        let d2: Double = dx * dx + dy * dy * 1.15
                        if d2 < bd {
                            bd = d2
                            best = i
                        }
                    }
                }
                if best < 0 {
                    ev[EV_CREDIT] = 1
                    break
                }
                let o: Double = best * STRIDE
                S[o + DRAIN] = 0.0001
                S[o + SX] = S[o + X]
                S[o + SY] = S[o + Y]
                S[o + Z0] = S[o + Z]
                S[o + R0AX] = S[o + AX]
                S[o + R0AY] = S[o + AY]
                S[o + DUR] = clamp(sqrt(bd) / 360, 0.13, 0.36)
                ev[EV_CREDIT] -= 1
            }
        } else {
            ev[EV_CREDIT] = min(ev[EV_CREDIT], 0.999)
        }
        var alive: Double = 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] > 0.5 && S[o + DRAIN] == 0 && S[o + CNT] > 0.5 {
                    alive += 1
                }
            }
        }
        ev[EV_ALIVE] = alive
        let damp: Double = 0.9985
        let funnel: Double = 900 * clamp(rate / 14, 0, 1.4)
        let awakeAll: Bool = rate > 0.01 || inp[IN_FINGER] > 0.5 || inp[IN_GX] != 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0 {
                    continue
                }
                if awakeAll {
                    S[o + SLEEP] = 0
                }
                if S[o + SLEEP] >= 24 {
                    S[o + PX] = S[o + X]
                    S[o + PY] = S[o + Y]
                    continue
                }
                let x: Double = S[o + X]
                let y: Double = S[o + Y]
                var vx: Double = x - S[o + PX]
                var vy: Double = y - S[o + PY]
                let spd: Double = sqrt(vx * vx + vy * vy) / dt
                let dk: Double = damp - 0.045 * (1 - smooth(25, 140, spd))
                vx *= dk
                vy *= dk
                if S[o + R0X] > 0 {
                    S[o + R0X] = max(S[o + R0X] - dt, 0)
                }
                var ax: Double = inp[IN_GX]
                var ay: Double = g
                if rate > 0.01 {
                    let dx: Double = cfg.mx - x
                    let dy: Double = cfg.my - y
                    let d: Double = sqrt(dx * dx + dy * dy) + 0.001
                    if d < cfg.reach {
                        let k: Double = (1 - d / cfg.reach) * funnel
                        ax += (dx / d) * k
                        ay += (dy / d) * k * 0.6 - g * 0.25 * (1 - d / cfg.reach)
                    }
                }
                vx += ax * dt * dt
                vy += ay * dt * dt
                S[o + PX] = x
                S[o + PY] = y
                S[o + X] = x + vx
                S[o + Y] = y + vy
                if rate <= 0.01 && inp[IN_FLICKON] > 0.5 && S[o + R0X] > 0 && S[o + CNT] > 0.5 {
                    let sp: Double = sqrt(vx * vx + vy * vy) / dt
                    let mdx: Double = cfg.mx - (x + vx)
                    let mdy: Double = cfg.my - (y + vy)
                    if sp > FLICK_SPEED && mdx * mdx + mdy * mdy < FLICK_REACH * FLICK_REACH && mdx * vx + mdy * vy > 0 {
                        S[o + DRAIN] = 0.0001
                        S[o + SX] = S[o + X]
                        S[o + SY] = S[o + Y]
                        S[o + Z0] = S[o + Z]
                        S[o + R0AX] = S[o + AX]
                        S[o + R0AY] = S[o + AY]
                        S[o + DUR] = 0.14
                        ev[EV_FLICK] += 1
                    }
                }
            }
        }
        var zTop: Double = 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] > 0.5 && S[o + DRAIN] == 0 && S[o + Z] > zTop {
                    zTop = S[o + Z]
                }
            }
        }
        zTop += cfg.t * 3
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] < 0.5 || S[o + DRAIN] == 0 {
                    continue
                }
                let p: Double = min(S[o + DRAIN] + dt / S[o + DUR], 1)
                S[o + DRAIN] = p
                let e: Double = p * p * (3 - 2 * p) * 0.35 + p * p * p * 0.65
                S[o + X] = S[o + SX] + (cfg.mx - S[o + SX]) * e
                S[o + Y] = S[o + SY] + (cfg.my - S[o + SY]) * e
                S[o + PX] = S[o + X]
                S[o + PY] = S[o + Y]
                S[o + Z] = S[o + Z0] + (zTop - S[o + Z0]) * smooth(0, 0.35, p)
                S[o + GLOW] = 1
                let ddx: Double = cfg.mx - S[o + SX]
                let ddy: Double = cfg.my - S[o + SY]
                let dl: Double = sqrt(ddx * ddx + ddy * ddy) + 0.001
                let tip: Double = smooth(0.2, 0.85, p)
                S[o + AX] = S[o + R0AX] + (-(ddy / dl) * 0.94 - S[o + R0AX]) * tip
                S[o + AY] = S[o + R0AY] + ((ddx / dl) * 0.94 - S[o + R0AY]) * tip
                if p >= 1 {
                    if alive == 0 && S[o + AUX] == 0 {
                        S[o + AUX] = 0.3
                        ev[EV_LASTSPIN] = 0.3
                    }
                    if S[o + AUX] > 0 {
                        S[o + AUX] = max(S[o + AUX] - dt, 0.0001)
                        ev[EV_LASTSPIN] = S[o + AUX]
                        if S[o + AUX] > 0.0002 {
                            continue
                        }
                    }
                    S[o + ALIVE] = 0
                    S[o + CNT] = 0
                    S[o + DRAIN] = 0
                    S[o + AUX] = 0
                    ev[EV_COLLECTED] += 1
                    ev[EV_CENTS] += S[o + VAL]
                    ev[EV_LAST] = i
                }
            }
        }
        let fOn: Double = inp[IN_FINGER]
        let fr: Double = inp[IN_FR]
        do {
            var it: Double = 0
            while it < 4 {
                defer { it += 1 }
                do {
                    var i: Double = 0
                    while i < n {
                        defer { i += 1 }
                        let oi: Double = i * STRIDE
                        if S[oi + ALIVE] < 0.5 {
                            continue
                        }
                        let drainI: Bool = S[oi + DRAIN] > 0
                        do {
                            var j: Double = i + 1
                            while j < n {
                                defer { j += 1 }
                                let oj: Double = j * STRIDE
                                if S[oj + ALIVE] < 0.5 {
                                    continue
                                }
                                let drainJ: Bool = S[oj + DRAIN] > 0
                                if drainI && drainJ {
                                    continue
                                }
                                let dz: Double = abs(S[oi + ZB] - S[oj + ZB])
                                let same: Double = 1 - smooth(cfg.layerGap * 0.4, cfg.layerGap * 1.7, dz)
                                let f: Double = cfg.overlap + (1 - cfg.overlap) * same
                                let minD: Double = (S[oi + RC] + S[oj + RC]) * f
                                let dx: Double = S[oj + X] - S[oi + X]
                                let dy: Double = S[oj + Y] - S[oi + Y]
                                let d2: Double = dx * dx + dy * dy
                                if d2 >= minD * minD || d2 < 1e-6 {
                                    continue
                                }
                                let d: Double = sqrt(d2)
                                let nx: Double = dx / d
                                let ny: Double = dy / d
                                let pen: Double = minD - d
                                var wi: Double = 0.5
                                var wj: Double = 0.5
                                if drainI {
                                    wi = 0
                                    wj = 1
                                } else {
                                    if drainJ {
                                        wi = 1
                                        wj = 0
                                    } else {
                                        let sI: Bool = S[oi + SLEEP] >= 24
                                        let sJ: Bool = S[oj + SLEEP] >= 24
                                        if sI && sJ {
                                            continue
                                        }
                                        if sI {
                                            wi = 0
                                            wj = 1
                                            if pen > 0.8 {
                                                S[oi + SLEEP] = 0
                                            }
                                        } else {
                                            if sJ {
                                                wi = 1
                                                wj = 0
                                                if pen > 0.8 {
                                                    S[oj + SLEEP] = 0
                                                }
                                            }
                                        }
                                    }
                                }
                                if it == 0 {
                                    let rv: Double = (S[oj + X] - S[oj + PX] - (S[oi + X] - S[oi + PX])) * nx + (S[oj + Y] - S[oj + PY] - (S[oi + Y] - S[oi + PY])) * ny
                                    let sp: Double = -rv / dt
                                    if sp > ev[EV_IMPACT] {
                                        ev[EV_IMPACT] = sp
                                    }
                                }
                                S[oi + X] -= nx * pen * wi
                                S[oi + Y] -= ny * pen * wi
                                S[oj + X] += nx * pen * wj
                                S[oj + Y] += ny * pen * wj
                                let tx: Double = -ny
                                let ty: Double = nx
                                let rt: Double = (S[oj + X] - S[oj + PX] - (S[oi + X] - S[oi + PX])) * tx + (S[oj + Y] - S[oj + PY] - (S[oi + Y] - S[oi + PY])) * ty
                                let mu: Double = min(0.5 * same, 1) * 0.55
                                S[oi + X] += tx * rt * mu * wi
                                S[oi + Y] += ty * rt * mu * wi
                                S[oj + X] -= tx * rt * mu * wj
                                S[oj + Y] -= ty * rt * mu * wj
                            }
                        }
                    }
                }
                do {
                    var i: Double = 0
                    while i < n {
                        defer { i += 1 }
                        let o: Double = i * STRIDE
                        if S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0 {
                            continue
                        }
                        let rc: Double = S[o + RC]
                        if S[o + SLEEP] >= 24 && fOn < 0.5 {
                            continue
                        }
                        if S[o + X] < rc {
                            S[o + X] = rc
                        }
                        if S[o + X] > w - rc {
                            S[o + X] = w - rc
                        }
                        if S[o + Y] > h - rc {
                            S[o + Y] = h - rc
                            S[o + PX] += (S[o + X] - S[o + PX]) * 0.35
                        }
                        if cfg.keepOn > 0 && S[o + X] < cfg.keepW + rc * 0.3 && S[o + Y] < cfg.keepH + rc * 0.6 && S[o + Y] > -rc * 2 {
                            let over: Double = cfg.keepH + rc * 0.6 - S[o + Y]
                            S[o + Y] += min(over * 0.35, 8)
                        }
                        if fOn > 0.5 {
                            let dx: Double = S[o + X] - inp[IN_FX]
                            let dy: Double = S[o + Y] - inp[IN_FY]
                            let d2: Double = dx * dx + dy * dy
                            let m: Double = rc * 0.9 + fr
                            if d2 < m * m && d2 > 1e-6 {
                                let d: Double = sqrt(d2)
                                S[o + X] += (dx / d) * (m - d)
                                S[o + Y] += (dy / d) * (m - d)
                                S[o + SLEEP] = 0
                                S[o + R0X] = PUSH_MEMORY
                                if S[o + GLOW] < 0.15 {
                                    S[o + GLOW] = 0.15
                                }
                            }
                        }
                    }
                }
            }
        }
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if S[o + ALIVE] < 0.5 {
                    continue
                }
                if S[o + DRAIN] == 0 {
                    var vx: Double = S[o + X] - S[o + PX]
                    var vy: Double = S[o + Y] - S[o + PY]
                    let maxv: Double = 1400 * dt
                    let sp: Double = sqrt(vx * vx + vy * vy)
                    if sp > maxv {
                        vx *= maxv / sp
                        vy *= maxv / sp
                        S[o + PX] = S[o + X] - vx
                        S[o + PY] = S[o + Y] - vy
                    }
                    if S[o + SLEEP] >= 24 {
                        if sp > 0.6 * dt * 120 {
                            S[o + SLEEP] = 0
                        } else {
                            S[o + PX] = S[o + X]
                            S[o + PY] = S[o + Y]
                        }
                    } else {
                        let rx: Double = S[o + X] - S[o + SX]
                        let ry: Double = S[o + Y] - S[o + SY]
                        if rx * rx + ry * ry < 0.12 {
                            S[o + SLEEP] += 1
                            if S[o + SLEEP] >= 24 {
                                S[o + PX] = S[o + X]
                                S[o + PY] = S[o + Y]
                            }
                        } else {
                            S[o + SX] = S[o + X]
                            S[o + SY] = S[o + Y]
                            S[o + SLEEP] = 0
                        }
                    }
                    let dx: Double = cfg.mx - S[o + X]
                    let dy: Double = cfg.my - S[o + Y]
                    let d: Double = sqrt(dx * dx + dy * dy)
                    let river: Double = 40 + 50 * clamp(rate / 14, 0, 1)
                    let target: Double = ev[EV_FLOW] * smooth(river, 28, d) * (rate > 0.01 || ev[EV_FLOW] > 0.05 ? 1 : 0)
                    let k: Double = target > S[o + GLOW] ? dt / 0.06 : dt / 0.28
                    S[o + GLOW] += (target - S[o + GLOW]) * min(k, 1)
                }
            }
        }
        resolveZ(cfg, S, dt)
    }

    static func loadHeap(_ cfg: CoinPileCollectConfig, _ S: CoinPileCollectBuffer) {
        let n: Double = cfg.n
        let Hp: CoinPileCollectBuffer = cfg.heap
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                let h: Double = i * HS
                S[o + X] = Hp[h]
                S[o + Y] = Hp[h + 1]
                S[o + PX] = Hp[h]
                S[o + PY] = Hp[h + 1]
                S[o + Z] = Hp[h + 2]
                S[o + ZT] = Hp[h + 2]
                S[o + AX] = Hp[h + 3]
                S[o + AY] = Hp[h + 4]
                S[o + PHI] = Hp[h + 5]
                S[o + FACE] = Hp[h + 6]
                S[o + RANK] = Hp[h + 7]
                S[o + RC] = radiusOf(cfg, Hp[h + 3], Hp[h + 4])
                S[o + ZB] = Hp[h + 8]
                S[o + ALIVE] = 1
                S[o + CNT] = 1
                S[o + DRAIN] = 0
                S[o + GLOW] = 0
                S[o + SLEEP] = 100
                S[o + AUX] = 0
                S[o + R0X] = 0
            }
        }
    }

    static let CTL: Double = NMAX * STRIDE
    static let C_STEP: Double = 0
    static let C_MODE: Double = 1
    static let C_MT: Double = 2
    static let C_VALVE: Double = 3
    static let C_THR: Double = 4
    static let C_LATCH: Double = 5
    static let C_EMPTYT: Double = 6
    static let C_PRESS: Double = 7
    static let C_BULGE: Double = 8
    static let C_BULGEV: Double = 9
    static let C_LIT: Double = 10
    static let C_PILLA: Double = 11
    static let C_CENTS: Double = 12
    static let C_TARGET: Double = 13
    static let C_KICKAT: Double = 14
    static let C_HOLDPREV: Double = 15
    static let C_RETEND: Double = 16
    static let C_LANDED: Double = 17
    static let C_RET_ALL: Double = 18
    static let C_XTH0: Double = 19
    static let C_XTH1: Double = 20
    static let C_XW: Double = 21
    static let C_FLOW: Double = 22
    static let C_TOTAL: Double = 23
    static let C_N: Double = 24
    static let C_TAP: Double = 25
    static let C_FRESH: Double = 26
    static let C_CREDIT: Double = 27
    static let C_SFLOW: Double = 28
    static let CTL_SIZE: Double = 32
    static let STATE_SIZE: Double = CTL + CTL_SIZE
    static let IN_HOLD: Double = 8
    static let IN_SLIDE: Double = 9
    static let IN_REDUCED: Double = 10
    static let IN_CMD: Double = 11
    static let IN_UP: Double = 12
    static let IN_FLICKT: Double = 13
    static let INPUT_SIZE: Double = 16
    static let MODE_LIVE: Double = 0
    static let MODE_RETURN: Double = 1
    static let MODE_FADE_OUT: Double = 2
    static let MODE_FADE_IN: Double = 3
    static let EVX_EMPTY: Double = 12
    static let EVX_LANDED: Double = 13
    static let EVX_REFILLED: Double = 14
    static let EVX_LATCH: Double = 15
    static let EVENT_SIZE: Double = 16
    static func makeInputs() -> CoinPileCollectBuffer {
        let inp: CoinPileCollectBuffer = CoinPileCollectBuffer(count: Int(INPUT_SIZE))
        inp[IN_FLICKT] = 99
        return inp
    }

    static func makeEvents() -> CoinPileCollectBuffer {
        return CoinPileCollectBuffer(count: Int(EVENT_SIZE))
    }

    static func setValues(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ totalCents: Double) {
        let vals: CoinPileCollectBuffer = CoinPileCollectBuffer([0])
        vals.length = cfg.n
        coinValues(cfg.n, totalCents, cfg.seed, vals)
        do {
            var i: Double = 0
            while i < cfg.n {
                defer { i += 1 }
                A[i * STRIDE + VAL] = vals[i]
            }
        }
        A[CTL + C_TOTAL] = totalCents
        A[CTL + C_N] = cfg.n
    }

    static func initState(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ totalCents: Double) {
        loadHeap(cfg, A)
        setValues(cfg, A, totalCents)
        A[CTL + C_STEP] = 0
        A[CTL + C_MODE] = MODE_LIVE
        A[CTL + C_MT] = 0
        A[CTL + C_VALVE] = 0
        A[CTL + C_THR] = 1
        A[CTL + C_LATCH] = 0
        A[CTL + C_EMPTYT] = -1
        A[CTL + C_PRESS] = 0
        A[CTL + C_BULGE] = 0
        A[CTL + C_BULGEV] = 0
        A[CTL + C_LIT] = 0
        A[CTL + C_PILLA] = 1
        A[CTL + C_CENTS] = totalCents
        A[CTL + C_TARGET] = totalCents
        A[CTL + C_KICKAT] = -1
        A[CTL + C_HOLDPREV] = 0
        A[CTL + C_RETEND] = 0
        A[CTL + C_LANDED] = 0
        A[CTL + C_RET_ALL] = 0
        A[CTL + C_FLOW] = 0
        A[CTL + C_TAP] = 0
        A[CTL + C_FRESH] = 0
        A[CTL + C_CREDIT] = 0
        A[CTL + C_SFLOW] = 0
    }

    static func clockOf(_ A: CoinPileCollectBuffer) -> Double {
        return A[CTL + C_STEP] * DT
    }

    static func countCents(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer) -> Double {
        var sum: Double = 0
        do {
            var i: Double = 0
            while i < cfg.n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if A[o + CNT] > 0.5 {
                    sum += A[o + VAL]
                }
            }
        }
        return sum
    }

    static func spawnPose(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ i: Double, _ order: Double) {
        let o: Double = i * STRIDE
        let h: Double = i * HS
        let rx: Double = cfg.heap[h]
        let ry: Double = cfg.heap[h + 1]
        let u: Double = rnd(cfg.seed, i, 41)
        let v: Double = rnd(cfg.seed, i, 42)
        let x: Double = min(max(rx + (u - 0.5) * 60, cfg.r * 0.8), cfg.w - cfg.r * 0.8)
        let y: Double = -cfg.r - RAIN.spawnY - v * RAIN.spawnSpread - (order.truncatingRemainder(dividingBy: 3)) * 14
        A[o + R0X] = x
        A[o + R0Y] = y
        A[o + R0Z] = cfg.heap[h + 2] + 70
        let a: Double = rnd(cfg.seed, i, 43) * 6.2831853
        let m: Double = 0.55 + 0.4 * rnd(cfg.seed, i, 44)
        A[o + R0AX] = cos(a) * m
        A[o + R0AY] = sin(a) * m
        A[o + R0PHI] = rnd(cfg.seed, i, 45) * 6.2831853
        let d: Double = max(ry - y, 40)
        A[o + DUR] = sqrt((2 * d) / RAIN.gravity)
    }

    static func beginReturn(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ rain: Bool) {
        let n: Double = cfg.n
        let stagger: Double = rain ? RAIN.stagger : RAIN.glideStagger * 2
        var k: Double = 0
        var end: Double = 0
        do {
            var q: Double = 0
            while q < n {
                defer { q += 1 }
                let i: Double = cfg.order[q]
                let o: Double = i * STRIDE
                let h: Double = i * HS
                let present: Bool = A[o + ALIVE] > 0.5 && A[o + CNT] > 0.5 && A[o + DRAIN] == 0 && !rain
                if present {
                    A[o + RKIND] = 0
                    A[o + R0X] = A[o + X]
                    A[o + R0Y] = A[o + Y]
                    A[o + R0Z] = A[o + Z]
                    A[o + R0AX] = A[o + AX]
                    A[o + R0AY] = A[o + AY]
                    A[o + R0PHI] = A[o + PHI]
                    A[o + RT0] = 0
                    A[o + DUR] = RAIN.glide
                    end = max(end, RAIN.glide)
                } else {
                    A[o + RKIND] = 1
                    A[o + CNT] = 0
                    A[o + ALIVE] = 0
                    A[o + DRAIN] = 0
                    A[o + GLOW] = 0
                    spawnPose(cfg, A, i, k)
                    A[o + RT0] = k * stagger
                    end = max(end, k * stagger + A[o + DUR] + RAIN.bounce)
                    k += 1
                }
                A[o + FACE] = cfg.heap[h + 6]
            }
        }
        A[CTL + C_MODE] = MODE_RETURN
        A[CTL + C_MT] = 0
        A[CTL + C_RETEND] = end + 0.05
        A[CTL + C_LANDED] = 0
        A[CTL + C_RET_ALL] = rain ? 1 : 0
        A[CTL + C_LATCH] = 0
        A[CTL + C_VALVE] = 0
        A[CTL + C_LIT] = 0
        A[CTL + C_CREDIT] = 0
        A[CTL + C_SFLOW] = 0
        A[CTL + C_TARGET] = countCents(cfg, A)
    }

    static func returnPose(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ i: Double, _ mt: Double, _ ev: CoinPileCollectBuffer) {
        let o: Double = i * STRIDE
        let h: Double = i * HS
        let s: Double = mt - A[o + RT0]
        let rx: Double = cfg.heap[h]
        let ry: Double = cfg.heap[h + 1]
        let rz: Double = cfg.heap[h + 2]
        let rax: Double = cfg.heap[h + 3]
        let ray: Double = cfg.heap[h + 4]
        let rphi: Double = cfg.heap[h + 5]
        if A[o + RKIND] < 0.5 {
            let k: Double = smooth(0, 1, s / A[o + DUR])
            A[o + X] = mix(A[o + R0X], rx, k)
            A[o + Y] = mix(A[o + R0Y], ry, k)
            A[o + Z] = mix(A[o + R0Z], rz, k)
            A[o + AX] = mix(A[o + R0AX], rax, k)
            A[o + AY] = mix(A[o + R0AY], ray, k)
            A[o + PHI] = rphi
            A[o + PX] = A[o + X]
            A[o + PY] = A[o + Y]
            return
        }
        if s < 0 {
            A[o + ALIVE] = 0
            return
        }
        let tau: Double = A[o + DUR]
        A[o + ALIVE] = 1
        if s < tau {
            let k: Double = s / tau
            A[o + Y] = A[o + R0Y] + (ry - A[o + R0Y]) * k * k
            let dx: Double = smooth(0, 1, k)
            A[o + X] = mix(A[o + R0X], rx, dx)
            let tk: Double = smooth(0.25, 1, k)
            A[o + AX] = mix(A[o + R0AX], rax, tk)
            A[o + AY] = mix(A[o + R0AY], ray, tk)
            A[o + PHI] = rphi + (A[o + R0PHI] - rphi) * (1 - k) + (1 - k) * 9.4
            A[o + Z] = rz + (A[o + R0Z] - rz) * (1 - k)
            A[o + CNT] = 0
        } else {
            let u: Double = s - tau
            if A[o + CNT] < 0.5 {
                A[o + CNT] = 1
                ev[EVX_LANDED] += 1
                A[CTL + C_LANDED] += 1
                A[CTL + C_TARGET] += A[o + VAL]
            }
            let k: Double = clamp01(u / RAIN.bounce)
            let decay: Double = exp(-u * 9)
            A[o + Y] = ry
            A[o + X] = rx
            A[o + Z] = rz + RAIN.bounceZ * decay * abs(sin(u * 17))
            let wob: Double = decay * sin(u * 22) * 0.12 * (1 - k)
            A[o + AX] = rax + wob
            A[o + AY] = ray - wob * 0.6
            A[o + PHI] = rphi
        }
        A[o + PX] = A[o + X]
        A[o + PY] = A[o + Y]
    }

    static func countAlive(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer) -> Double {
        var c: Double = 0
        do {
            var i: Double = 0
            while i < cfg.n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if A[o + ALIVE] > 0.5 && A[o + CNT] > 0.5 && A[o + DRAIN] == 0 {
                    c += 1
                }
            }
        }
        return c
    }

    static func countDraining(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer) -> Double {
        var c: Double = 0
        do {
            var i: Double = 0
            while i < cfg.n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                if A[o + ALIVE] > 0.5 && A[o + DRAIN] > 0 {
                    c += 1
                }
            }
        }
        return c
    }

    static func stepFade(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer) {
        A[CTL + C_MT] += DT
        let mt: Double = A[CTL + C_MT]
        let n: Double = cfg.n
        let out: Bool = A[CTL + C_MODE] == MODE_FADE_OUT
        let group: Double = 0.12
        var landed: Double = 0
        do {
            var i: Double = 0
            while i < n {
                defer { i += 1 }
                let o: Double = i * STRIDE
                let dx: Double = cfg.mx - A[o + X]
                let dy: Double = cfg.my - A[o + Y]
                let d: Double = sqrt(dx * dx + dy * dy)
                let g: Double = d < 90 ? 0 : d < 150 ? 1 : 2
                let gi: Double = out ? g : 2 - g
                let k: Double = clamp01((mt - gi * group) / group)
                let a: Double = out ? 1 - k : k
                A[o + ALIVE] = a
                A[o + CNT] = a > 0.5 ? 1 : 0
                A[o + GLOW] = 0
                if a > 0.5 {
                    landed += 1
                }
            }
        }
        A[CTL + C_TARGET] = countCents(cfg, A)
        A[CTL + C_CENTS] = A[CTL + C_TARGET]
        if mt >= group * 3 + 0.02 {
            if out {
                A[CTL + C_MODE] = MODE_LIVE
                A[CTL + C_EMPTYT] = 0
                A[CTL + C_KICKAT] = clockOf(A)
                A[CTL + C_PILLA] = EMPTY.pillAlpha
                A[CTL + C_MT] = 0
                ev[EVX_EMPTY] = 1
            } else {
                loadHeap(cfg, A)
                A[CTL + C_MODE] = MODE_LIVE
                A[CTL + C_EMPTYT] = -1
                A[CTL + C_TARGET] = A[CTL + C_TOTAL]
                A[CTL + C_CENTS] = A[CTL + C_TOTAL]
                A[CTL + C_PILLA] = 1
                ev[EVX_REFILLED] = 1
            }
        }
        if landed < 0 {
            ev[EVX_LANDED] = 0
        }
    }

    static func engineStep(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer) {
        let dt: Double = DT
        var mode: Double = A[CTL + C_MODE]
        let n: Double = cfg.n
        do {
            var q: Double = 0
            while q < EVENT_SIZE {
                defer { q += 1 }
                ev[q] = 0
            }
        }
        A[CTL + C_STEP] += 1
        let cmd: Double = inp[IN_CMD]
        inp[IN_CMD] = 0
        let hold: Double = inp[IN_HOLD] > 0.5 ? 1 : 0
        let prevHold: Double = A[CTL + C_HOLDPREV]
        let latched: Bool = A[CTL + C_LATCH] > 0.5
        var held: Double = hold
        if hold > 0.5 && prevHold < 0.5 {
            A[CTL + C_TAP] = 0
            A[CTL + C_FRESH] = latched ? 1 : 0
            if latched {
                A[CTL + C_LATCH] = 0
                ev[EVX_LATCH] = -1
            }
        }
        if hold > 0.5 {
            A[CTL + C_TAP] += dt
        }
        if hold < 0.5 && prevHold > 0.5 {
            if A[CTL + C_TAP] < 0.18 && A[CTL + C_FRESH] < 0.5 && mode == MODE_LIVE && A[CTL + C_EMPTYT] < 0 {
                A[CTL + C_LATCH] = 1
                ev[EVX_LATCH] = 1
            }
            A[CTL + C_FRESH] = 0
        }
        if A[CTL + C_FRESH] > 0.5 {
            held = 0
        }
        A[CTL + C_HOLDPREV] = hold
        let press: Double = A[CTL + C_PRESS]
        let pressTarget: Double = hold > 0.5 && mode == MODE_LIVE ? 1 : 0
        A[CTL + C_PRESS] = press + (pressTarget - press) * min(dt / PILL.pressTau, 1)
        if cmd == 1 {
            A[CTL + C_LATCH] = mode == MODE_LIVE && A[CTL + C_EMPTYT] < 0 ? 1 : A[CTL + C_LATCH]
        }
        if cmd == 2 {
            A[CTL + C_LATCH] = 0
        }
        if cmd == 3 && mode == MODE_LIVE {
            beginReturn(cfg, A, A[CTL + C_TARGET] == 0)
            mode = MODE_RETURN
        }
        if mode == MODE_LIVE {
            let alive: Double = countAlive(cfg, A)
            let lat: Bool = A[CTL + C_LATCH] > 0.5
            let want: Bool = (held > 0.5 || lat) && alive > 0 && A[CTL + C_EMPTYT] < 0 && inp[IN_REDUCED] < 0.5
            let v: Double = A[CTL + C_VALVE]
            if want && v < 0.001 && A[CTL + C_CREDIT] < 0.7 {
                A[CTL + C_CREDIT] = 0.7
            }
            let nv: Double = v + ((want ? 1 : 0) - v) * min(dt / (want ? VALVE.attack : VALVE.release), 1)
            A[CTL + C_VALVE] = nv < 0.0005 ? 0 : nv
            let slide: Double = held > 0.5 && !lat ? inp[IN_SLIDE] : 0
            let thrTarget: Double = 1 - (1 - VALVE.throttleMin) * smooth(0, VALVE.throttleSlide, slide)
            A[CTL + C_THR] += (thrTarget - A[CTL + C_THR]) * min(dt / VALVE.throttleLag, 1)
            let rate: Double = VALVE.rate * A[CTL + C_VALVE] * A[CTL + C_THR]
            inp[IN_RATE] = rate
            inp[IN_FLICKT] = inp[IN_FINGER] > 0.5 ? 0 : inp[IN_FLICKT] + dt
            inp[IN_FLICKON] = inp[IN_FLICKT] < 0.6 ? 1 : 0
            ev[EV_CREDIT] = A[CTL + C_CREDIT]
            ev[EV_FLOW] = A[CTL + C_SFLOW]
            stepSim(cfg, A, dt, inp, ev)
            A[CTL + C_CREDIT] = ev[EV_CREDIT]
            A[CTL + C_SFLOW] = ev[EV_FLOW]
            A[CTL + C_FLOW] = clamp01(rate / VALVE.rate)
            let coll: Double = ev[EV_COLLECTED]
            if coll > 0 {
                A[CTL + C_BULGEV] += coll * 1.8
            }
            A[CTL + C_TARGET] = countCents(cfg, A)
            let now: Double = countAlive(cfg, A)
            let draining: Double = countDraining(cfg, A)
            if now == 0 && draining == 0 && ev[EV_LASTSPIN] <= 0 && A[CTL + C_EMPTYT] < 0 && A[CTL + C_TARGET] == 0 {
                A[CTL + C_EMPTYT] = 0
                A[CTL + C_KICKAT] = clockOf(A)
                A[CTL + C_LATCH] = 0
                ev[EVX_EMPTY] = 1
            } else {
                if A[CTL + C_EMPTYT] >= 0 {
                    A[CTL + C_EMPTYT] += dt
                    if A[CTL + C_EMPTYT] >= EMPTY.rainDelay && inp[IN_REDUCED] < 0.5 {
                        beginReturn(cfg, A, true)
                    }
                }
            }
            let pillTarget: Double = A[CTL + C_EMPTYT] >= 0 ? EMPTY.pillAlpha : 1
            A[CTL + C_PILLA] += (pillTarget - A[CTL + C_PILLA]) * min(dt / PILL.alphaTau, 1)
            let litTarget: Double = A[CTL + C_FLOW] > 0.05 ? 1 : 0
            A[CTL + C_LIT] += (litTarget - A[CTL + C_LIT]) * min(dt / PILL.litTau, 1)
        } else {
            if mode == MODE_RETURN {
                A[CTL + C_MT] += dt
                let mt: Double = A[CTL + C_MT]
                do {
                    var i: Double = 0
                    while i < n {
                        defer { i += 1 }
                        returnPose(cfg, A, i, mt, ev)
                    }
                }
                let total: Double = A[CTL + C_TOTAL]
                let frac: Double = total > 0 ? A[CTL + C_TARGET] / total : 1
                let full: Bool = A[CTL + C_RET_ALL] > 0.5
                let pillTarget: Double = full ? EMPTY.pillAlpha + (1 - EMPTY.pillAlpha) * clamp01(frac) : 1
                A[CTL + C_PILLA] += (pillTarget - A[CTL + C_PILLA]) * min(dt / PILL.alphaTau, 1)
                A[CTL + C_LIT] += (0 - A[CTL + C_LIT]) * min(dt / PILL.litTau, 1)
                A[CTL + C_FLOW] = 0
                A[CTL + C_VALVE] = 0
                if mt >= A[CTL + C_RETEND] {
                    loadHeap(cfg, A)
                    A[CTL + C_MODE] = MODE_LIVE
                    A[CTL + C_EMPTYT] = -1
                    A[CTL + C_KICKAT] = full ? A[CTL + C_KICKAT] : -1
                    A[CTL + C_TARGET] = A[CTL + C_TOTAL]
                    A[CTL + C_PILLA] = 1
                    A[CTL + C_LATCH] = 0
                    ev[EVX_REFILLED] = 1
                }
            } else {
                stepFade(cfg, A, inp, ev)
            }
        }
        let bw: Double = (2 * Double.pi) / PILL.bulgeSpring.response
        let zeta: Double = PILL.bulgeSpring.zeta
        var b: Double = A[CTL + C_BULGE]
        var bv: Double = A[CTL + C_BULGEV]
        bv += (-bw * bw * b - 2 * zeta * bw * bv) * dt
        b += bv * dt
        A[CTL + C_BULGE] = b
        A[CTL + C_BULGEV] = bv
        let cur: Double = A[CTL + C_CENTS]
        let tgt: Double = A[CTL + C_TARGET]
        let nc: Double = cur + (tgt - cur) * min(dt / ROLL.tau, 1)
        A[CTL + C_CENTS] = abs(nc - tgt) < 0.004 ? tgt : nc
    }

    static func reducedStep(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer) {
        let mode: Double = A[CTL + C_MODE]
        A[CTL + C_STEP] += 1
        do {
            var q: Double = 0
            while q < EVENT_SIZE {
                defer { q += 1 }
                ev[q] = 0
            }
        }
        let hold: Bool = inp[IN_HOLD] > 0.5
        let prev: Bool = A[CTL + C_HOLDPREV] > 0.5
        A[CTL + C_HOLDPREV] = hold ? 1 : 0
        let cmd: Double = inp[IN_CMD]
        inp[IN_CMD] = 0
        if mode == MODE_LIVE {
            if A[CTL + C_EMPTYT] < 0 {
                if (hold && !prev) || cmd == 1 {
                    A[CTL + C_MODE] = MODE_FADE_OUT
                    A[CTL + C_MT] = 0
                    A[CTL + C_PRESS] = 1
                }
            } else {
                A[CTL + C_EMPTYT] += DT
                A[CTL + C_PRESS] = 0
                if A[CTL + C_EMPTYT] >= 1.2 {
                    A[CTL + C_MODE] = MODE_FADE_IN
                    A[CTL + C_MT] = 0
                }
            }
            if hold == false {
                A[CTL + C_PRESS] = 0
            }
        } else {
            stepFade(cfg, A, inp, ev)
        }
    }

    static func autoStep(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer) {
        let t: Double = clockOf(A)
        if t < 0.62 {
            do {
                var k: Double = 0
                while k < 2 {
                    defer { k += 1 }
                    let i: Double = cfg.order[cfg.n - 1 - k]
                    let h: Double = i * HS
                    let w: Double = t < 0.6 ? 0.035 * exp(-t * 5.5) * sin(t * 24 + k * 1.7) : 0
                    A[i * STRIDE + AX] = cfg.heap[h + 3] + w
                    A[i * STRIDE + AY] = cfg.heap[h + 4] - w * 0.6
                }
            }
        }
        scriptAt(t, inp, IN_HOLD)
        inp[IN_FINGER] = 0
        engineStep(cfg, A, inp, ev)
    }

    static func replayTo(_ cfg: CoinPileCollectConfig, _ A: CoinPileCollectBuffer, _ inp: CoinPileCollectBuffer, _ ev: CoinPileCollectBuffer, _ t: Double, _ total: Double) {
        initState(cfg, A, total)
        do {
            var q: Double = 0
            while q < EVENT_SIZE {
                defer { q += 1 }
                ev[q] = 0
            }
        }
        let steps: Double = floor(max(0, min(t, LOOP)) / DT + 1e-6)
        do {
            var s: Double = 0
            while s < steps {
                defer { s += 1 }
                autoStep(cfg, A, inp, ev)
            }
        }
    }

    static func resetInputs(_ inp: CoinPileCollectBuffer) {
        do {
            var q: Double = 0
            while q < INPUT_SIZE {
                defer { q += 1 }
                inp[q] = 0
            }
        }
        inp[IN_FLICKT] = 99
        inp[IN_FR] = 0
        inp[IN_GX] = 0
    }

    static let SAMPLE_R = CoinPileCollectBuffer((0..<15).map { (q: Int) -> Double in
        if q == 0 { return 0 }; return q < 7 ? 0.55 : 0.95
    })
    static let SAMPLE_C = CoinPileCollectBuffer((0..<15).map { (q: Int) -> Double in
        if q == 0 { return 1 }
        let angle: Double = q < 7 ? Double(q - 1) * 1.0471976 : Double(q - 7) * 0.7853982
        return cos(angle)
    })
    static let SAMPLE_S = CoinPileCollectBuffer((0..<15).map { (q: Int) -> Double in
        if q == 0 { return 0 }
        let angle: Double = q < 7 ? Double(q - 1) * 1.0471976 : Double(q - 7) * 0.7853982
        return sin(angle)
    })
    static func hash32(_ n: Double) -> Double {
        var h = UInt32(truncatingIfNeeded: Int64(n))
        h = (h ^ (h >> 16)) &* 0x45d9f3b
        h = (h ^ (h >> 16)) &* 0x45d9f3b
        h = h ^ (h >> 16)
        return Double(h) / 4294967296
    }
    static func rnd(_ seed: Double, _ i: Double, _ k: Double) -> Double {
        let a = Int32(bitPattern: UInt32(truncatingIfNeeded: Int64(seed)) &* 0x9e3779b1)
        let b = Int32(bitPattern: UInt32(truncatingIfNeeded: Int64(i + 1)) &* 0x85ebca6b)
        let c = Int32(bitPattern: UInt32(truncatingIfNeeded: Int64(k + 1)) &* 0xc2b2ae35)
        return hash32(Double(Int64(a) + Int64(b) + Int64(c)))
    }
    static func makeState() -> CoinPileCollectBuffer { CoinPileCollectBuffer(count: Int(STATE_SIZE)) }
}
