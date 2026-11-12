import Foundation

/// Value-for-value port of psr-motion.ts. All clocks and animation arithmetic use Double.
enum PinataSmashRewardMotion {
    static let loop = 8.4
    static let HOP = 0, JOLT = 1, BURST = 2, POKE = 3
    static let CLEAR_BACK = 0, CLEAR_DRAIN = 1
    static let DEG = Double.pi / 180
    static let scriptBack = 6.0
    static let atlasNames = ["green-sweet", "magenta-sweet", "lollipop", "mint", "star", "bonbon", "gumdrop", "tile-violet", "tile-azure", "tile-aqua", "glow"]
    static let K_GREEN = 0, K_MAGENTA = 1, K_LOLLY = 2, K_MINT = 3, K_STAR = 4, K_BONBON = 5, K_GUMDROP = 6, K_TILE = 7, K_GLOW = 10
    static let kindW: [Double] = [40, 40, 29, 22, 26, 21, 22, 14, 14, 14, 34]

    enum M {
        static let restSway = 1.2, swayCycles = 3.0, tailLag = 0.08, joltDecay = 0.3, joltPeriod = 0.55
        static let ribbonPerDeg = 2.2, bodyLag = 0.05, bodyGain = 1.25, stop = 0.033, burstStop = 0.09
        static let squash = 0.06, dip = 3.0, yawGain = 1.6, yawMax = 16.0
        enum hop { static let rise = 0.43, hang = 0.35, fall = 0.4, height = 120.0, land = 8.0 }
        static let bloomDur = 0.42
        enum ripple { static let dur = 0.32, from = 15.0, to = 163.0 }
        enum ring { static let dur = 0.38, from = 20.0, to = 210.0 }
        enum token { static let pop = 0.02, ride = 0.25, fly = 0.52 }
        static let fillGrow = 0.12, gravity = 2100.0, restitution = 0.32, confGravity = 900.0, confTerm = 260.0
        enum slow { static let rate = 0.35, pop = 0.08, hold = 0.42, ramp = 0.25 }
        enum back { static let vacuum = 0.0, vacuumDur = 0.34, vacuumSpread = 0.14, dip = 0.06, hoist = 0.15, swap = 0.6, land = 0.95, pill = 1.05, chip = 1.05, end = 1.6 }
        enum drain { static let dur = 0.5, pill = 0.5, end = 0.7 }
        static let idleReturn = 2.0, hopGap = 1.5, merge = 0.09, liveInc = 1.0 / 12.0, releaseMax = 520.0
    }

    struct Hit: Codable {
        var t: Double; var x: Double; var y: Double; var kind: Int; var amp: Double; var inc: Double; var seed: Double
        var loot: [Int]; var conf: Int; var tiles: Int; var tokens: Int; var value: Double
    }
    struct Story: Codable { var hits: [Hit]; var clear: Double; var clearKind: Int }
    struct Pt: Codable { var x: Double; var y: Double }
    struct Geo: Codable { var chipX: Double; var chipY: Double; var bulbs: [Pt]; var flank: Pt; var body: Pt; var attach: Pt }
    struct Loot: Codable { var k: Int; var x: Double; var y: Double; var rot: Double; var s: Double; var a: Double; var sx: Double }
    struct Conf: Codable { var x: Double; var y: Double; var w: Double; var h: Double; var rot: Double; var c: Int; var a: Double; var diamond: Int }
    struct Token: Codable { var x: Double; var y: Double; var s: Double; var a: Double; var tx: [Double]; var ty: [Double] }
    struct Frame: Codable {
        var ax: Double; var ay: Double; var roll: Double; var yaw: Double; var sq: Double; var sqAng: Double; var press: Double; var tail: Double; var bow: Double
        var burst: Double; var crack1: Double; var crack2: Double; var bloomX: Double; var bloomY: Double; var bloom: Double; var hole: Double
        var bulbBoost: Double; var waveFrom: Int; var waveTau: Double; var flicker: Double
        var rippleX: Double; var rippleY: Double; var rippleR: Double; var rippleA: Double
        var ringX: Double; var ringY: Double; var ringR: Double; var ringA: Double
        var flash: Double; var stage: Double; var stageX: Double; var stageY: Double
        var pillW: Double; var wL: Double; var wB: Double; var wD: Double; var fill: Double; var fillGlow: Double; var shake: Double; var sparks: Double; var doneText: Double
        var count: Double; var chipBump: Double; var tokens: [Token]; var loot: [Loot]; var conf: [Conf]
        var streamers: Double; var fade: Double; var shadowLift: Double; var swayT: Double
    }
    struct Driver: Codable {
        var mode = 0; var loopT = 0.0; var t = 0.0
        var story = Story(hits: [], clear: -1, clearKind: 0)
        var last = 0.0; var drag = 0; var fx = 0.0; var fy = 0.0; var gx = 0.0; var gy = 0.0
        var ox = 0.0; var oy = 0.0; var vx = 0.0; var vy = 0.0; var th = 0.0; var om = 0.0; var press = 0.0; var pressAim = 0.0
        var blendT = -1.0; var blendRoll = 0.0; var blendX = 0.0; var blendY = 0.0; var blendPending = 0
        var lastAx = 0.0; var lastAy = 0.0; var lastRoll = 0.0
    }

    static func mk(_ t: Double, _ x: Double, _ y: Double, _ kind: Int, _ amp: Double, _ inc: Double, _ seed: Double, _ loot: [Int], _ conf: Int, _ tiles: Int, _ tokens: Int, _ value: Double) -> Hit {
        Hit(t: t, x: x, y: y, kind: kind, amp: amp, inc: inc, seed: seed, loot: loot, conf: conf, tiles: tiles, tokens: tokens, value: value)
    }
    static func burstLoot() -> [Int] {
        let order = [K_GREEN, K_STAR, K_MAGENTA, K_LOLLY, K_MINT, K_BONBON, K_GUMDROP, K_MAGENTA, K_GREEN]
        return (0..<36).map { order[($0 * 5 + ($0 >> 2)) % order.count] }
    }
    static let script: [Hit] = [
        mk(0.6, 201, 359 + 56, HOP, 0, 1 / 9, 1, [], 3, 0, 1, 10),
        mk(1.85, 170, 370 + 56, JOLT, 5, 1 / 9, 2, [K_GREEN, K_MAGENTA], 0, 0, 1, 10),
        mk(2.3, 230, 365 + 56, JOLT, -4, 1 / 9, 3, [K_MAGENTA, K_GUMDROP], 4, 0, 1, 10),
        mk(2.62, 165, 350 + 56, JOLT, 5, 1 / 9, 4, [K_LOLLY, K_MINT], 4, 0, 1, 10),
        mk(2.9, 240, 380 + 56, JOLT, -6, 1 / 9, 5, [K_STAR, K_GREEN], 5, 0, 1, 10),
        mk(3.14, 150, 370 + 56, JOLT, 6, 1 / 9, 6, [K_MAGENTA, K_BONBON, K_GREEN], 5, 0, 1, 10),
        mk(3.35, 225, 355 + 56, JOLT, -7, 1 / 9, 7, [K_STAR, K_STAR, K_MINT], 6, 0, 1, 10),
        mk(3.54, 175, 365 + 56, JOLT, 7, 1 / 9, 8, [K_GUMDROP, K_LOLLY, K_MAGENTA, K_BONBON], 6, 0, 1, 10),
        mk(3.74, 156, 365 + 56, BURST, -10, 1 / 9, 9, burstLoot(), 40, 18, 2, 20)
    ]
    static let scriptStory = Story(hits: script, clear: scriptBack, clearKind: CLEAR_BACK)

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ u: Double) -> Double { let v = 1 - u; return 1 - v * v * v }
    static func easeInCubic(_ u: Double) -> Double { u * u * u }
    static func easeInOutCubic(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOutQuad(_ u: Double) -> Double { 1 - (1 - u) * (1 - u) }
    static func smooth(_ u: Double) -> Double { let v = clamp01(u); return v * v * (3 - 2 * v) }
    static func hash(_ n: Double) -> Double { let s = sin(n * 12.9898 + 78.233) * 43758.5453; return s - floor(s) }
    static func spring(_ tau: Double, _ response: Double, _ z: Double) -> Double {
        if tau <= 0 { return 0 }
        let w0 = (2 * Double.pi) / response, a = z * w0, wd = w0 * sqrt(1 - z * z)
        return 1 - exp(-a * tau) * (cos(wd * tau) + (a / wd) * sin(wd * tau))
    }
    static func ring(_ tau: Double, _ decay: Double, _ period: Double) -> Double {
        if tau <= 0 { return 0 }
        return exp(-tau / decay) * sin((2 * Double.pi * tau) / period)
    }
    static let doneResponse = 0.24, doneZ = 0.8
    static let doneT98: Double = {
        for k in 1..<200 { if spring(Double(k) / 200, doneResponse, doneZ) >= 0.98 { return Double(k) / 200 } }
        return 0.3
    }()
    static func slowTau(_ tau: Double) -> Double {
        let r = M.slow.rate, p = M.slow.pop, h = M.slow.hold, g = M.slow.ramp
        if tau <= p { return tau }
        if tau <= h { return p + (tau - p) * r }
        let base = p + (h - p) * r
        if tau <= h + g { let u = tau - h; return base + r * u + ((1 - r) * u * u) / (2 * g) }
        return base + (r * g + (1 - r) * g * 0.5) + (tau - h - g)
    }
    static func bodyTime(_ t: Double, _ hits: [Hit]) -> Double {
        var te = t
        for h in hits {
            if h.t > t { break }
            let s = h.kind == BURST ? M.burstStop : h.kind == POKE ? 0.02 : M.stop
            te -= min(t - h.t, s)
        }
        return te
    }
    static func hopAt(_ tau: Double) -> (y: Double, roll: Double, yaw: Double) {
        let r = tau - 0.02
        var y = 0.0, roll = 0.0, yaw = 0.0
        if r <= 0 { return (y, roll, yaw) }
        if r < M.hop.rise {
            let u = easeOutCubic(r / M.hop.rise)
            y = -M.hop.height * u; roll = -6 * u; yaw = 14 * u
        } else if r < M.hop.rise + M.hop.hang {
            let u = (r - M.hop.rise) / M.hop.hang
            y = -M.hop.height - 4 * sin(Double.pi * u); roll = -6 + 2 * smooth(u); yaw = 14 - 24 * smooth(u)
        } else if r < M.hop.rise + M.hop.hang + M.hop.fall {
            let u = (r - M.hop.rise - M.hop.hang) / M.hop.fall, e = easeInOutCubic(u)
            y = -M.hop.height + (M.hop.height + M.hop.land) * e; roll = -4 + 7 * e; yaw = -10 * (1 - e)
        } else {
            let s = r - M.hop.rise - M.hop.hang - M.hop.fall
            y = M.hop.land * (1 - spring(s, 0.35, 0.5)); roll = 3 * (1 - spring(s, 0.45, 0.55))
        }
        return (y, roll, yaw)
    }
    static func roundState(_ story: Story, _ t: Double) -> (fill: Double, first: Double, burstT: Double, after: Double) {
        var fill = 0.0, first = -1.0, burstT = -1.0
        let after = story.clear >= 0 && t >= story.clear ? story.clear : -1
        for h in story.hits {
            if h.t > t { break }
            if after >= 0 && h.t < after { continue }
            if h.kind == POKE { continue }
            if first < 0 { first = h.t }
            if h.kind == BURST { burstT = h.t }
            let u = clamp01((t - h.t) / M.fillGrow), g = easeOutCubic(u) + 0.03 * sin(Double.pi * u)
            fill += h.inc * g
        }
        return (min(fill, 1.03), first, burstT, after)
    }
    static func fillAfter(_ story: Story, _ idx: Int) -> Double {
        let hits = story.hits, h = hits[idx]
        var f = 0.0
        let after = story.clear >= 0 && h.t >= story.clear ? story.clear : -1
        for i in 0...idx {
            let g = hits[i]
            if after >= 0 && g.t < after { continue }
            if g.kind == POKE { continue }
            f += g.inc
        }
        return f
    }
    static func roundInfo(_ hits: [Hit], _ from: Double, _ to: Double) -> (first: Double, burst: Double, inc: Double) {
        var first = -1.0, burst = -1.0, inc = 0.0
        for h in hits {
            if h.t > to { break }
            if h.t < from || h.kind == POKE { continue }
            if first < 0 { first = h.t }
            if h.kind == BURST { burst = h.t }
            inc += h.inc
        }
        return (first, burst, inc)
    }
    static func lootPiece(_ out: inout [Loot], _ k: Int, _ x0: Double, _ y0: Double, _ vx: Double, _ vy: Double, _ spin: Double, _ restRot: Double, _ floorY: Double, _ tau: Double, _ scale: Double) {
        let g = M.gravity, dy = floorY - y0
        let T = (-vy + sqrt(vy * vy + 2 * g * max(dy, 1))) / g
        let xl = min(max(x0 + vx * T * 0.55, 18), 384), vxe = (xl - x0) / T
        var x = 0.0, y = 0.0, rot = 0.0
        if tau < T {
            x = x0 + vxe * tau; y = y0 + vy * tau + 0.5 * g * tau * tau; rot = spin * tau
        } else {
            let vimp = vy + g * T, v1 = vimp * M.restitution, T1 = (2 * v1) / g
            let v2 = v1 * M.restitution, T2 = (2 * v2) / g, s = tau - T
            let drift = vxe * 0.18, aT = spin * T
            // JS Math.round ties toward positive infinity, including negative angles.
            let target = restRot + floor((aT - restRot) / Double.pi + 0.5) * Double.pi
            if s < T1 {
                x = xl + drift * s; y = floorY - (v1 * s - 0.5 * g * s * s)
                rot = aT + (target - aT) * easeOutQuad(s / (T1 + T2))
            } else if s < T1 + T2 {
                let q = s - T1
                x = xl + drift * (T1 + q * 0.5); y = floorY - (v2 * q - 0.5 * g * q * q)
                rot = aT + (target - aT) * easeOutQuad(s / (T1 + T2))
            } else {
                x = xl + drift * (T1 + T2 * 0.5); y = floorY; rot = target
            }
        }
        out.append(Loot(k: k, x: x, y: y, rot: rot, s: scale, a: 1, sx: 1))
    }
    static func confPiece(_ out: inout [Conf], _ x0: Double, _ y0: Double, _ vx: Double, _ vy: Double, _ tau: Double, _ seed: Double, _ c: Int, _ fade: Double) {
        let k = M.confTerm / M.confGravity, e = 1 - exp(-tau / k)
        let x = x0 + vx * k * e + 12 * sin(2 * Double.pi * 3 * tau + seed * 6.28) * clamp01(tau * 2)
        let y = y0 + M.confTerm * tau + (vy - M.confTerm) * k * e
        if y > 900 { return }
        let spin = (4 + hash(seed + 3) * 8) * (hash(seed + 4) > 0.5 ? 1.0 : -1.0), ang = spin * tau + seed * 3
        let diamond = hash(seed + 5) < 0.3 ? 1 : 0
        let w = diamond != 0 ? 8.0 : 4.0, h = diamond != 0 ? 8.0 : 9.0
        let tumble = cos(ang * 0.8), a = fade * (1 - seg(y, 820, 880))
        out.append(Conf(x: x, y: y, w: w * (0.35 + 0.65 * abs(tumble)), h: h, rot: ang, c: c, a: a, diamond: diamond))
    }
    static func nearestBulb(_ geo: Geo, _ x: Double, _ y: Double) -> Int {
        var best = 0, bd = 1e9
        for i in geo.bulbs.indices {
            let dx = geo.bulbs[i].x - x, dy = geo.bulbs[i].y - y, d = dx * dx + dy * dy
            if d < bd { bd = d; best = i }
        }
        return best
    }

    /// Pure frame evaluator: hit-stop, two-angle pendulum, analytic bounces, return and particles.
    static func frameAt(_ t: Double, _ story: Story, _ geo: Geo, _ swayT: Double) -> Frame {
        let hits = story.hits, te = bodyTime(t, hits), clear = story.clear
        let back = clear >= 0 && story.clearKind == CLEAR_BACK && t >= clear ? t - clear : -1
        let drain = clear >= 0 && story.clearKind == CLEAR_DRAIN && t >= clear ? t - clear : -1
        let swapped = back >= M.back.swap, rs = roundState(story, t)
        let sway = M.restSway * sin((2 * Double.pi * M.swayCycles * swayT) / loop)
        let swayLag = M.restSway * sin((2 * Double.pi * M.swayCycles * (swayT - M.tailLag)) / loop)
        var swing = 0.0, swingBody = 0.0, swingTail = 0.0, yaw = 0.0, dy = 0.0, sq = 0.0, sqAng = 0.0
        var hopY = 0.0, hopRoll = 0.0, hopYaw = 0.0, bloom = 0.0, bloomX = geo.body.x, bloomY = geo.body.y
        var flashBulbs = 0.0, waveFrom = -1, waveTau = 0.0
        var rippleA = 0.0, rippleR = 0.0, rippleX = geo.body.x, rippleY = geo.body.y
        var ringA = 0.0, ringR = 0.0, flash = 0.0, stage = 1.0, hole = 0.0, burstMix = 0.0
        var crack1 = 0.0, crack2 = 0.0, tremble = 0.0, shake = 0.0, sparks = -1.0, streamers = -1.0
        for i in hits.indices {
            let h = hits[i]
            if h.t > t { break }
            let tau = t - h.t, tb = te - bodyTime(h.t, hits), old = swapped && h.t < clear
            if h.kind == HOP {
                let hp = hopAt(tau)
                hopY += hp.y; hopRoll += hp.roll; hopYaw += hp.yaw
            } else if h.kind == JOLT || h.kind == POKE {
                let amp = h.amp * M.bodyGain
                swing += h.amp * ring(tb, M.joltDecay, M.joltPeriod)
                swingBody += amp * ring(tb - M.bodyLag, M.joltDecay, M.joltPeriod)
                swingTail += amp * 1.3 * ring(tb - M.bodyLag - M.tailLag, M.joltDecay * 1.2, M.joltPeriod)
                let ya = min(abs(h.amp) * M.yawGain, M.yawMax) * (h.amp > 0 ? -1.0 : 1.0)
                yaw += ya * ring(tb - 0.02, 0.4, 0.8)
            } else if h.kind == BURST && !old {
                swing += h.amp * ring(tb, M.joltDecay, M.joltPeriod) + -8 * ring(tb, 1.1, 1.9) * 0.6
                swingBody += -12 * ring(tb - M.bodyLag, 0.28, 0.6) + -8 * ring(tb - 0.1, 1.1, 1.9)
                swingTail += -16 * ring(tb - 0.13, 0.4, 0.6) + -9 * ring(tb - 0.2, 1.1, 1.9)
                yaw += -16 * ring(tb, 0.35, 0.7)
            }
            if old { continue }
            if tb < 0.12 { dy += M.dip * sin((Double.pi * tb) / 0.12) * (h.kind == POKE ? 0.5 : 1) }
            if tb < 0.5 {
                let sqv = (h.kind == BURST ? 1.6 : 1) * M.squash * exp(-tb * 10.1) * cos(20 * tb)
                if abs(sqv) > abs(sq) { sq = sqv; sqAng = atan2(h.y - geo.body.y, h.x - geo.body.x) }
            }
            if tau < M.bloomDur {
                let q = 1 - tau / M.bloomDur, b = q * sqrt(q) * 0.5
                if b > bloom { bloom = b; bloomX = h.x; bloomY = h.y }
            }
            if tau < 0.6 {
                waveFrom = i; waveTau = tau; flashBulbs = max(flashBulbs, h.kind == BURST ? 1 : 0.8)
            }
            if h.kind == HOP && tau < M.ripple.dur {
                let q = tau / M.ripple.dur, u = easeOutCubic(q)
                rippleR = M.ripple.from + (M.ripple.to - M.ripple.from) * u
                rippleA = 0.6 * (1 - q * q); rippleX = h.x; rippleY = h.y
            }
            if h.kind == POKE && tau < 0.4 { shake = 4 * ring(tau, 0.12, 0.12) }
            if h.kind == BURST {
                burstMix = clamp01(tau / 0.06)
                flash = tau < 0.09 ? 0.3 * (tau / 0.09) : 0.3 * (1 - clamp01((tau - 0.09) / 0.16))
                if tau < M.ring.dur {
                    let u = easeOutCubic(tau / M.ring.dur)
                    ringR = M.ring.from + (M.ring.to - M.ring.from) * u; ringA = 0.9 * (1 - u)
                }
                if tau < 0.26 {
                    let u = easeOutCubic(tau / 0.26)
                    rippleR = 15 + 135 * u; rippleA = 0.5 * (1 - u); rippleX = geo.flank.x; rippleY = geo.flank.y
                }
                stage = 1 + 0.06 * (spring(tau, 0.5, 0.6) - spring(tau - 0.12, 0.5, 0.6))
                hole = 0.35 * (1 - clamp01(tau / 0.6)); sparks = tau; streamers = tau - 0.06
            }
            let fa = fillAfter(story, i)
            if fa > 0.3 && crack1 == 0 { crack1 = clamp01(tau / 0.12) }
            if fa > 0.6 && crack2 == 0 { crack2 = clamp01(tau / 0.12) }
            if fa > 0.75 && h.kind != BURST { tremble = max(tremble, clamp01(tau / 0.2)) }
        }
        if rs.burstT >= 0 { tremble = 0 }
        if drain >= 0 {
            let k = 1 - seg(drain, 0, 0.3)
            crack1 *= k; crack2 *= k; tremble *= k
        }
        var hoistY = 0.0, backRoll = 0.0, tailWhip = 0.0, fade = 1.0
        if back >= 0 {
            fade = 1 - seg(back, 0, 0.3)
            if back < M.back.hoist { hoistY = 12 * sin(Double.pi * 0.5 * seg(back, M.back.dip, M.back.hoist)) }
            else if back < M.back.swap { hoistY = 12 - 572 * easeInCubic(seg(back, M.back.hoist, M.back.swap)) }
            else if back < M.back.land {
                let u = seg(back, M.back.swap, M.back.land)
                hoistY = -560 * (1 - u * u); backRoll = 6
            } else {
                let s = back - M.back.land, win = 1 - smooth(seg(back, M.back.end - 0.3, M.back.end))
                hoistY = 16 * ring(s, 0.16, 0.3) * win
                backRoll = 6 * (1 - spring(s, 0.5, 0.55)) * win
                tailWhip = 20 * ring(s - 0.04, 0.2, 0.36) * win
            }
            if swapped { burstMix = 0; crack1 = 0; crack2 = 0; hole = 0 }
        }
        let tr = tremble * 1.2 * sin(2 * Double.pi * 9 * t)
        let rollDeg = sway + swingBody + hopRoll + backRoll + tr, swingX = swing * M.ribbonPerDeg
        let ax = geo.attach.x + swingX, ay = geo.attach.y + hopY + hoistY + dy, bow = max(0, -hopY) * (16 / M.hop.height)
        let inReturn = (back >= 0 && back < M.back.end + 0.3) || (drain >= 0 && drain < M.drain.end + 0.4)
        let fresh = clear >= 0 && t >= clear ? roundInfo(hits, clear, t) : nil
        let useOld = inReturn && (fresh == nil || fresh!.first < 0)
        let info = useOld ? roundInfo(hits, -1, clear) : fresh ?? roundInfo(hits, -1, t)
        let s1 = info.first >= 0 ? spring(t - info.first, 0.22, 0.8) : 0
        let s2 = info.burst >= 0 ? spring(t - info.burst, doneResponse, doneZ) : 0
        let doneText = info.burst >= 0 ? clamp01((t - info.burst - doneT98) / 0.12) : 0
        let c2 = info.burst >= 0 ? easeOutCubic(clamp01((t - info.burst) / 0.08)) : 0
        let ret = useOld ? clear + (back >= 0 ? M.back.pill : M.drain.pill) : -1
        let s3 = ret >= 0 ? spring(t - ret, 0.3, 0.78) : 0
        let k1 = clamp01(s1), k2 = c2, k3 = clamp01(s3)
        var wL = 1 - k1, wB = k1 * (1 - k2), wD = k1 * k2
        wL = wL * (1 - k3) + k3; wB *= 1 - k3; wD *= 1 - k3
        let WL = 186.0, WB = 120.0, WD = 172.5
        var pillW = WL + (WB - WL) * s1 + (WD - WB) * s2
        pillW += (WL - (info.burst >= 0 ? WD : WB)) * s3
        var fill = rs.fill
        if useOld { fill = back >= 0 ? 1 : min(info.inc, 1) * (1 - easeOutCubic(seg(drain, 0, M.drain.dur))) }
        let fillGlow = smooth(seg(fill, 0.74, 0.8))

        var tokens: [Token] = [], count = 0.0, chipBump = 0.0
        let arrive = M.token.pop + M.token.ride + M.token.fly
        let chipReset = story.clearKind == CLEAR_BACK ? M.back.chip : M.drain.pill
        for h in hits {
            if h.t > t { break }
            for j in 0..<h.tokens {
                let tau = t - h.t - Double(j) * 0.12
                if tau < M.token.pop { continue }
                if tau >= arrive {
                    let k = clamp01((tau - arrive) / 0.2)
                    let reset = clear >= 0 && h.t < clear ? easeOutCubic(seg(t - clear, chipReset, chipReset + 0.3)) : 0
                    count += h.value * easeOutCubic(k) * (1 - reset)
                    if tau - arrive < 0.3 { chipBump = max(chipBump, sin(Double.pi * clamp01((tau - arrive) / 0.3))) }
                    continue
                }
                let bx = h.kind == BURST ? geo.flank.x + (j == 0 ? -14 : 18) : h.x - 12 * (hash(h.seed + Double(j)) - 0.5)
                let by = h.kind == BURST ? geo.flank.y - 20 : h.y + 6
                let pop = tau - M.token.pop, s = 0.6 + 0.4 * spring(pop, 0.25, 0.6)
                let rx = bx + swingX * 0.6
                let ry = by + (hopY + dy) * 0.9 - 26 * easeOutCubic(clamp01(pop / (M.token.ride + 0.1)))
                if pop < M.token.ride { tokens.append(Token(x: rx, y: ry, s: s, a: 1, tx: [], ty: [])); continue }
                var tx: [Double] = [], ty: [Double] = [], px = 0.0, py = 0.0, ps = 1.0
                for q in 0..<5 {
                    let u = clamp01((pop - M.token.ride - Double(q) * 0.012) / M.token.fly), e = u * u * (1.6 - 0.6 * u)
                    let cx = rx + (geo.chipX - rx) * 0.15, cy = min(ry, geo.chipY) - 150
                    let ix = (1 - e) * (1 - e) * rx + 2 * (1 - e) * e * cx + e * e * geo.chipX
                    let iy = (1 - e) * (1 - e) * ry + 2 * (1 - e) * e * cy + e * e * geo.chipY
                    if q == 0 { px = ix; py = iy; ps = 1 - 0.4 * e } else { tx.append(ix); ty.append(iy) }
                }
                tokens.append(Token(x: px, y: py, s: s * ps, a: 1, tx: tx, ty: ty))
            }
        }

        var loot: [Loot] = [], conf: [Conf] = [], pieceIdx = 0
        for i in hits.indices {
            let h = hits[i]
            if h.t > t { break }
            let tau0 = t - h.t, isBurst = h.kind == BURST, tau = isBurst ? slowTau(tau0) : tau0
            let vac = clear >= 0 && h.t < clear && t >= clear ? t - clear : -1
            let cfade = clear >= 0 && h.t < clear && t >= clear ? 1 - seg(t - clear, 0, 0.3) : 1
            let base = atan2(h.y - geo.body.y, h.x - geo.body.x), n = h.loot.count
            for j in 0..<n {
                let sd = h.seed * 31 + Double(j) * 7.13
                var ang = 0.0, sp = 0.0
                if isBurst {
                    ang = (150 + 240 * ((Double(j) + hash(sd) * 0.8) / Double(n))) * DEG
                    sp = 420 + 560 * hash(sd + 1)
                } else {
                    let dirx = cos(base), up = -Double.pi / 2
                    var a0 = base + (up - base) * 0.35
                    if abs(h.x - geo.body.x) < 6 && abs(h.y - geo.body.y) < 6 { a0 = up }
                    ang = a0 + (hash(sd) - 0.5) * 100 * DEG * (n > 1 ? 1 : 0.4)
                    sp = 420 + 340 * hash(sd + 1)
                    if dirx == 0 { ang = up }
                }
                let vx = cos(ang) * sp, vy = sin(ang) * sp
                let spin = (200 + 500 * hash(sd + 2)) * DEG * (hash(sd + 3) > 0.5 ? 1.0 : -1.0)
                let restRot = (hash(sd + 4) - 0.5) * 0.7, floorY = 836 - 18 * hash(sd + 5) - Double(pieceIdx % 3) * 6
                let k = h.loot[j], before = loot.count
                let ox0 = isBurst ? geo.flank.x + cos(ang) * 16 : h.x, oy0 = isBurst ? geo.flank.y + sin(ang) * 16 : h.y
                lootPiece(&loot, k, ox0, oy0, vx, vy, spin, restRot, floorY, tau, 1)
                pieceIdx += 1
                if before >= loot.count { continue }
                var p = loot[before]
                if !isBurst && rs.burstT >= 0 && !swapped {
                    let pj = t - rs.burstT - 0.5
                    if pj > 0 && pj < 0.3 { p.y -= 6 * sin((Double.pi * pj) / 0.3) }
                }
                if vac >= 0 {
                    let d = hypot(p.x - geo.chipX, p.y - geo.chipY), delay = clamp01((d - 480) / 420) * M.back.vacuumSpread
                    let u = clamp01((vac - delay) / M.back.vacuumDur)
                    if u >= 1 { loot.removeLast(); continue }
                    let e = u * u, cx = p.x + (geo.chipX - p.x) * 0.2, cy = p.y - 240
                    p.x = (1 - e) * (1 - e) * p.x + 2 * (1 - e) * e * cx + e * e * geo.chipX
                    p.y = (1 - e) * (1 - e) * p.y + 2 * (1 - e) * e * cy + e * e * geo.chipY
                    p.s = 1 - 0.7 * e; p.a = 1 - smooth(seg(u, 0.75, 1)); p.rot += e * 3
                }
                loot[before] = p
            }
            let fa = fillAfter(story, i)
            let nt = h.tiles + (fa > 0.3 && fa - h.inc <= 0.3 ? 4 : 0) + (fa > 0.6 && fa - h.inc <= 0.6 ? 4 : 0)
            for j in 0..<nt {
                let sd = h.seed * 17 + Double(j) * 3.31 + 100
                let ang = isBurst ? (160 + 220 * hash(sd)) * DEG : (200 + 140 * hash(sd)) * DEG
                let sp = isBurst ? 300 + 500 * hash(sd + 1) : 120 + 160 * hash(sd + 1)
                let ox = isBurst ? geo.flank.x : geo.flank.x + (hash(sd + 6) - 0.5) * 50
                let oy = isBurst ? geo.flank.y : geo.flank.y + (hash(sd + 7) - 0.5) * 40
                let k = K_TILE + Int(floor(hash(sd + 2) * 3)), tt = isBurst ? tau : tau0
                let x = ox + cos(ang) * sp * min(tt, 0.5) * (1 - min(tt, 0.5)) * 1.4 + 18 * sin(tt * 5 + sd)
                let y = oy + sin(ang) * sp * min(tt, 0.5) + 0.5 * 700 * tt * tt
                if y > 900 { continue }
                let s = (10 + 8 * hash(sd + 3)) / kindW[K_TILE]
                let spin = (3 + 6 * hash(sd + 4)) * (hash(sd + 5) > 0.5 ? 1.0 : -1.0)
                loot.append(Loot(k: k, x: x, y: y, rot: spin * tt, s: s, a: cfade * (1 - seg(y, 820, 880)), sx: cos(spin * tt * 0.7)))
            }
            for j in 0..<h.conf {
                let sd = h.seed * 13 + Double(j) * 1.77 + 50
                var ang = 0.0, sp = 0.0
                if isBurst { ang = (150 + 240 * hash(sd)) * DEG; sp = 420 + 560 * hash(sd + 1) }
                else { ang = (-90 + (hash(sd) - 0.5) * 140) * DEG; sp = 160 + 240 * hash(sd + 1) }
                let c = Int(floor(hash(sd + 2) * 6))
                confPiece(&conf, isBurst ? geo.flank.x : h.x, isBurst ? geo.flank.y : h.y, cos(ang) * sp, sin(ang) * sp, isBurst ? tau : tau0, sd, c, cfade)
            }
        }
        let lift = clamp01(-(hopY + hoistY) / 140)
        var flicker = 1.0, bulbBoost = 1.0
        if tremble > 0 { flicker = 1 - 0.18 * tremble * (0.5 + 0.5 * sin(2 * Double.pi * 6 * t)) }
        if rs.burstT >= 0 && !swapped {
            let bt = t - rs.burstT
            if bt < 0.36 { bulbBoost = Int(floor(bt / 0.06)) % 2 == 0 ? 1.8 : 0.9 }
            else { bulbBoost = 1 + 0.2 * (1 - seg(bt, 0.36, 1.5)) }
        }
        return Frame(ax: ax, ay: ay, roll: rollDeg * DEG, yaw: (yaw + hopYaw) * DEG, sq: sq, sqAng: sqAng, press: 0,
                     tail: (swingTail - swingBody + (sway - swayLag) * 2 + tailWhip) * DEG, bow: bow,
                     burst: burstMix, crack1: crack1, crack2: crack2, bloomX: bloomX, bloomY: bloomY,
                     bloom: bloom * (1 + 0.15 * ((crack1 > 0 ? 1 : 0) + (crack2 > 0 ? 1 : 0))), hole: hole,
                     bulbBoost: bulbBoost, waveFrom: waveFrom >= 0 ? nearestBulb(geo, hits[waveFrom].x, hits[waveFrom].y) : -1,
                     waveTau: waveFrom >= 0 ? waveTau * (flashBulbs > 0 ? 1 : 0) : 0, flicker: flicker,
                     rippleX: rippleX, rippleY: rippleY, rippleR: rippleR, rippleA: rippleA,
                     ringX: geo.flank.x, ringY: geo.flank.y, ringR: ringR, ringA: ringA, flash: flash,
                     stage: stage, stageX: geo.flank.x, stageY: geo.flank.y, pillW: pillW, wL: wL, wB: wB, wD: wD,
                     fill: max(0, fill), fillGlow: fillGlow, shake: shake, sparks: sparks, doneText: doneText,
                     count: count, chipBump: chipBump, tokens: tokens, loot: loot, conf: conf,
                     streamers: back >= M.back.swap ? -1 : streamers, fade: fade, shadowLift: lift, swayT: swayT)
    }

    static func bulbLevel(_ i: Int, _ f: Frame) -> Double {
        let k = 2 + floor(hash(Double(i) + 0.5) * 4)
        let tw = 0.85 + 0.15 * sin((2 * Double.pi * k * f.swayT) / loop + hash(Double(i) + 9) * 6.28)
        var wave = 0.0
        if f.waveFrom >= 0 {
            let d = Double(abs(i - f.waveFrom)) * 0.04, s = f.waveTau - d
            if s >= 0 { wave = 0.8 * exp(-s / 0.18) }
        }
        return (tw + wave) * f.bulbBoost * f.flicker
    }

    static func makeDriver() -> Driver { Driver() }
    /// Touch copies exactly the portion of the script that has already happened.
    static func takeOver(_ d: inout Driver) {
        if d.mode == 1 { return }
        let t0 = d.loopT
        d.mode = 1; d.t = t0
        var hits: [Hit] = []
        if t0 < scriptBack + M.back.end { for h in script where h.t <= t0 { hits.append(h) } }
        d.story = Story(hits: hits, clear: t0 >= scriptBack && t0 < scriptBack + M.back.end ? scriptBack : -1, clearKind: CLEAR_BACK)
        d.last = d.t
    }
    static func storyBusy(_ d: Driver) -> Bool {
        let s = d.story
        if s.clear < 0 { return false }
        let span = s.clearKind == CLEAR_BACK ? M.back.pill + 0.3 : M.drain.pill
        return d.t >= s.clear && d.t < s.clear + span
    }
    /// Returns the source haptic code: 0 none, 1 light, 2 medium, 3 burst, 4 poke.
    @discardableResult static func tapAt(_ d: inout Driver, _ x: Double, _ y: Double, _ geo: Geo) -> Int {
        takeOver(&d); d.last = d.t
        if storyBusy(d) { return 0 }
        let s = d.story, hits = s.hits
        if let lastHit = hits.last, d.t - lastHit.t < M.merge { return 0 }
        var fill = 0.0, burst = false, lastRound = -1.0
        for h in hits {
            if s.clear >= 0 && h.t < s.clear { continue }
            if h.kind == POKE { continue }
            fill += h.inc
            if h.kind == BURST { burst = true }
            lastRound = h.t
        }
        let seed = 20 + Double(hits.count) * 1.37 + floor(d.t * 10)
        let side = x < geo.body.x ? 1.0 : -1.0, amp = side * (4 + 3 * min(1, fill + 0.1))
        var newHits = hits
        if burst {
            newHits.append(mk(d.t, x, y, POKE, side * 4, 0, seed, [], 2, 0, 0, 0))
            d.story = Story(hits: newHits, clear: s.clear, clearKind: s.clearKind)
            return 4
        }
        let inc = M.liveInc, willBurst = fill + inc >= 0.999, rest = lastRound < 0 || d.t - lastRound > M.hopGap
        let lootPool = [K_GREEN, K_MAGENTA, K_LOLLY, K_MINT, K_STAR, K_BONBON, K_GUMDROP]
        if willBurst {
            newHits.append(mk(d.t, geo.flank.x, geo.flank.y, BURST, -10, inc, seed, burstLoot(), 40, 18, 2, 20))
        } else if rest && fill == 0 {
            newHits.append(mk(d.t, x, y, HOP, 0, inc, seed, [], 3, 0, 1, 10))
        } else {
            let n = 2 + Int(floor(hash(seed) * 2.2))
            var loot: [Int] = []
            for j in 0..<n { loot.append(lootPool[Int(floor(hash(seed + Double(j) * 3.1) * Double(lootPool.count)))]) }
            newHits.append(mk(d.t, x, y, JOLT, amp, inc, seed, loot, 4 + Int(floor(hash(seed + 1) * 3)), 0, 1, 10))
        }
        d.story = Story(hits: newHits, clear: s.clear, clearKind: s.clearKind)
        if willBurst { return 3 }
        return fill + inc >= 0.66 ? 2 : 1
    }
    static let pendW = (2 * Double.pi) / 1.7
    static let ribbon = 371.5 + 56
    /// Spring to the finger, then pendulum with radial spring back to ribbon length.
    static func stepDrag(_ d: inout Driver, _ dt: Double) {
        let steps = max(1, Int(ceil(dt / 0.004))), h = dt / Double(steps)
        for _ in 0..<steps {
            if d.drag == 1 {
                var tx = d.fx + d.gx, ty = d.fy + d.gy
                let dx = tx, dy = ty + ribbon, r = hypot(dx, dy)
                if r > ribbon {
                    let over = r - ribbon, rr = ribbon + (over * 0.12) / (1 + over / 120)
                    tx = dx * (rr / r); ty = dy * (rr / r) - ribbon
                }
                let ax = -320 * (d.ox - tx) - 26 * d.vx, ay = -320 * (d.oy - ty) - 26 * d.vy
                d.vx += ax * h; d.vy += ay * h; d.ox += d.vx * h; d.oy += d.vy * h
            } else if abs(d.ox) + abs(d.oy) + abs(d.vx) + abs(d.vy) + abs(d.th) + abs(d.om) > 0.001 {
                let px = d.ox, py = d.oy + ribbon, r = hypot(px, py)
                var th = atan2(px, py)
                let om = (d.vx * cos(th) - d.vy * sin(th)) / max(r, 1)
                var vr = d.vx * sin(th) + d.vy * cos(th)
                let acc = -pendW * pendW * sin(th) - 2 * 0.9 * om, om2 = om + acc * h
                th += om2 * h
                var rr = r + vr * h
                vr += (-300 * (rr - ribbon) - 30 * vr) * h
                rr = max(0, rr)
                d.ox = rr * sin(th); d.oy = rr * cos(th) - ribbon
                d.vx = om2 * rr * cos(th) + vr * sin(th); d.vy = -om2 * rr * sin(th) + vr * cos(th)
                d.th = th; d.om = om2
                if abs(d.ox) + abs(d.oy) < 0.02 && abs(d.vx) + abs(d.vy) < 0.05 {
                    d.ox = 0; d.oy = 0; d.vx = 0; d.vy = 0; d.th = 0; d.om = 0
                }
            }
        }
    }
    /// Advances live interactions; the view supplies its wall-clock autoplay time independently.
    @discardableResult static func stepDriver(_ d: inout Driver, _ dt: Double, _ auto: Bool) -> Double {
        d.press += (d.pressAim - d.press) * (1 - exp(-dt * 30))
        if d.mode == 0 {
            if auto { d.loopT = (d.loopT + dt).truncatingRemainder(dividingBy: loop) }
            if d.blendT >= 0 { d.blendT += dt; if d.blendT > 0.5 { d.blendT = -1 } }
            stepDrag(&d, dt)
            return d.loopT
        }
        d.t += dt; stepDrag(&d, dt)
        let s = d.story, idle = d.t - d.last
        if d.drag == 0 && idle > M.idleReturn {
            var burst = false, any = false
            for h in s.hits {
                if s.clear >= 0 && h.t < s.clear { continue }
                any = true
                if h.kind == BURST { burst = true }
            }
            let cleared = s.clear >= 0 && s.clear >= d.last
            if !cleared && (burst || any) {
                var kept: [Hit] = []
                for h in s.hits where s.clear < 0 || h.t >= s.clear { kept.append(h) }
                d.story = Story(hits: kept, clear: d.t, clearKind: burst ? CLEAR_BACK : CLEAR_DRAIN)
            } else {
                let end = s.clear >= 0 ? s.clear + (s.clearKind == CLEAR_BACK ? M.back.end : M.drain.end) : d.last + M.idleReturn
                let settled = abs(d.ox) + abs(d.oy) + abs(d.th) < 1.5
                if d.t >= end && settled && auto {
                    d.mode = 0; d.loopT = scriptBack + M.back.end; d.blendT = 0; d.blendPending = 1
                }
            }
        }
        return d.t
    }
    static func applyDriver(_ f: inout Frame, _ d: Driver) {
        f.ax += d.ox; f.ay += d.oy
        f.roll += atan2(d.ox, d.oy + ribbon) * 0.6 - max(-0.35, min(0.35, d.vx * 0.0006))
        f.tail += -max(-0.5, min(0.5, d.vx * 0.0012)); f.press = d.press
        if d.blendT >= 0 {
            let k = 1 - smooth(d.blendT / 0.5)
            f.roll += d.blendRoll * k; f.ax += d.blendX * k; f.ay += d.blendY * k
        }
    }
}
