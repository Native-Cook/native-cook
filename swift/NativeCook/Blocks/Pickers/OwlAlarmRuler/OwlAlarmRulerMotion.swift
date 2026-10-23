import Foundation
import SwiftUI

/// Owl Alarm Ruler — motion copied value for value from the React Native motion module.
///
/// The ruler position is in ticks (one tick is five minutes); zero is the pin's 1:05 PM. The
/// automatic script, pose transforms and live gesture engine are all deterministic functions of
/// time or of the supplied engine snapshot.
enum OwlAlarmRulerMotion {
    typealias Theme = OwlAlarmRulerTheme

    static let LOOP: Double = 10
    /// After the last touch, the screen waits this long, eases home, and the loop restarts.
    static let RESUME_AFTER: Double = 2

    /// Release: the glide lands on round(pos + v · PROJECTION) and takes 3·|distance|/|v| seconds.
    static let PROJECTION: Double = 0.385

    struct Glide {
        let min: Double
        let max: Double
        let still: Double
    }

    static let GLIDE = Glide(min: 0.25, max: 1.2, still: 0.28)

    /// Easing home after RESUME_AFTER: duration = base + perTick · |distance|, capped.
    struct Return {
        let base: Double
        let perTick: Double
        let max: Double
    }

    static let RETURN = Return(base: 0.55, perTick: 0.012, max: 1.3)

    /// The velocity spring behind the owl's lean, its eyes and the needle (ω rad/s, ζ).
    struct Filter {
        let omega: Double
        let zeta: Double
    }

    static let FILTER = Filter(omega: 9, zeta: 0.55)

    /// Lean of the owl (deg) = −max · tanh(vel / soft). Velocity in ticks/s.
    struct Lean {
        let max: Double
        let soft: Double
    }

    static let LEAN = Lean(max: 5.5, soft: 30)

    /// Pupils (pt): sideways with velocity, down towards the ruler with attention.
    struct Eyes {
        let x: Double
        let soft: Double
        let attention: Double
    }

    static let EYES = Eyes(x: 5, soft: 26, attention: 3)

    /// The needle bends with the ticks passing under it (deg).
    struct Needle {
        let max: Double
        let soft: Double
    }

    static let NEEDLE = Needle(max: 7, soft: 32)
    /// Attention eases in and out with this time constant under a finger (s).
    static let ATTENTION_TAU: Double = 0.16

    /// The light band turns with the time of day: amp · sin(2π · pos / ticksPerDay) degrees.
    struct Sundial {
        let amp: Double
    }

    static let SUNDIAL = Sundial(amp: 9)

    /// AM / PM card flip: a spring from one face to the other.
    struct Flip {
        let omega: Double
        let zeta: Double
    }

    static let FLIP = Flip(omega: 17, zeta: 0.68)
    /// Digit wheels roll while the ruler is within ±riser/2 of a half tick.
    static let RISER: Double = 0.36

    /// Button press: in fast, out slower (asymmetric).
    struct Press {
        let scale: Double
        let `in`: Double
        let out: Double
    }

    static let PRESS = Press(scale: 0.97, in: 0.1, out: 0.2)
    /// The button's label and the a11y value update at most this often while the ruler runs (s).
    static let LABEL_GAP: Double = 0.09
    /// Haptic ticks never closer than this (s).
    static let HAPTIC_GAP: Double = 0.045

    /// The startle after the button: times in s from the release, distances in pt.
    struct Startle {
        let crouch: Double
        let air: Double
        let height: Double
        let crouchSy: Double
        let crouchSx: Double
        let stretch: Double
        let rattleDeg: Double
        let rattleHz: Double
        let landSquash: Double
        let landDecay: Double
        let landFreq: Double
        let pupil: Double
        let blinkAt: Double
        let shadowScale: Double
        let shadowFade: Double
        let arrow: Double
        let end: Double
    }

    static let STARTLE = Startle(
        crouch: 0.1,
        air: 0.4,
        height: 30,
        crouchSy: 0.07,
        crouchSx: 0.05,
        stretch: 0.07,
        rattleDeg: 4,
        rattleHz: 14,
        landSquash: 0.08,
        landDecay: 5,
        landFreq: 18,
        pupil: 0.28,
        blinkAt: 0.98,
        shadowScale: 0.3,
        shadowFade: 0.45,
        arrow: 6,
        end: 1.15
    )

    struct Blink {
        let close: Double
        let hold: Double
        let open: Double
        let period: Double
        let jitter: Double
    }

    static let BLINK = Blink(close: 0.07, hold: 0.03, open: 0.1, period: 3.9, jitter: 1.4)

    // MARK: Helpers

    /// The one clock every frame, gesture and button reads (UI runtime), in seconds.
    static func clockNow() -> Double {
        ProcessInfo.processInfo.systemUptime
    }

    static func clamp01(_ v: Double) -> Double {
        v < 0 ? 0 : v > 1 ? 1 : v
    }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp01((t - a) / (b - a))
    }

    static func lerp(_ a: Double, _ b: Double, _ p: Double) -> Double {
        a + (b - a) * p
    }

    /// JavaScript's modulo result is non-negative for this positive modulus.
    static func mod(_ a: Double, _ n: Double) -> Double {
        let remainder = a.truncatingRemainder(dividingBy: n)
        return (remainder + n).truncatingRemainder(dividingBy: n)
    }

    /// JavaScript Math.round: ties go toward positive infinity, unlike Swift's round-to-even.
    private static func jsRound(_ value: Double) -> Double {
        floor(value + 0.5)
    }

    /// Solve the curve's x coordinate before sampling y; shared by all native ports.
    private static func bezier(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp01(p)
        if x == 0 || x == 1 { return x }
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<18 {
            let m = (lo + hi) / 2
            let u = 1 - m
            let at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m
            if at < x { lo = m } else { hi = m }
        }
        let m = (lo + hi) / 2
        let u = 1 - m
        return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m
    }

    /// Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1).
    static func easeOut(_ p: Double) -> Double {
        bezier(p, 0.23, 1, 0.32, 1)
    }

    /// On-screen movement: cubic-bezier(0.77, 0, 0.175, 1).
    static func easeInOut(_ p: Double) -> Double {
        bezier(p, 0.77, 0, 0.175, 1)
    }

    static func smoothstep(_ p: Double) -> Double {
        let x = clamp01(p)
        return x * x * (3 - 2 * x)
    }

    /// Cubic Hermite from p0 to p1 with end tangents m0, m1 (already multiplied by the duration).
    static func hermite(_ p0: Double, _ p1: Double, _ m0: Double, _ m1: Double, _ u: Double) -> Double {
        let u2 = u * u
        let u3 = u2 * u
        return (2 * u3 - 3 * u2 + 1) * p0
            + (u3 - 2 * u2 + u) * m0
            + (-2 * u3 + 3 * u2) * p1
            + (u3 - u2) * m1
    }

    /// d/du of `hermite`.
    static func hermiteSlope(_ p0: Double, _ p1: Double, _ m0: Double, _ m1: Double, _ u: Double) -> Double {
        let u2 = u * u
        return (6 * u2 - 6 * u) * p0
            + (3 * u2 - 4 * u + 1) * m0
            + (-6 * u2 + 6 * u) * p1
            + (3 * u2 - 2 * u) * m1
    }

    /// A damped spring from 0 to 1, `tau` seconds after it starts.
    static func spring(_ tau: Double, _ omega: Double, _ zeta: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = omega * sqrt(1 - zeta * zeta)
        let env = exp(-zeta * omega * tau)
        if env < 1e-4 { return 1 }
        return 1 - env * (cos(wd * tau) + ((zeta * omega) / wd) * sin(wd * tau))
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    // MARK: Release and glide

    struct GlidePlan {
        let target: Double
        let dur: Double
    }

    /// Where a release at `pos` with velocity `vel` (ticks/s) lands, and how long the glide takes.
    static func glidePlan(_ pos: Double, _ vel: Double) -> GlidePlan {
        let target = jsRound(pos + vel * PROJECTION)
        let dist = target - pos
        var dur = GLIDE.still
        if abs(vel) > 0.5 {
            dur = min(GLIDE.max, max(GLIDE.min, (3 * abs(dist)) / abs(vel)))
        }
        return GlidePlan(target: target, dur: dur)
    }

    // MARK: The autoPlay script

    struct Drag {
        let a: Double
        let b: Double
        let from: Double
        let to: Double
        let v: Double
    }

    private static let DRAGS = [
        Drag(a: 1.3, b: 1.85, from: 0, to: 6, v: 15.6),
        Drag(a: 3.45, b: 3.9, from: 12, to: 4, v: -52),
        Drag(a: 5.55, b: 6.45, from: -16, to: -3, v: 8)
    ]

    struct Piece {
        let a: Double
        let b: Double
        let p0: Double
        let p1: Double
        let m0: Double
        let m1: Double
    }

    private static func buildScript() -> [Piece] {
        var out: [Piece] = []
        for d in DRAGS {
            let duration = d.b - d.a
            out.append(Piece(a: d.a, b: d.b, p0: d.from, p1: d.to, m0: 0, m1: d.v * duration))
            let g = glidePlan(d.to, d.v)
            out.append(Piece(a: d.b, b: d.b + g.dur, p0: d.to, p1: g.target, m0: d.v * g.dur, m1: 0))
        }
        return out
    }

    static let SCRIPT: [Piece] = buildScript()

    /// The button: pressed at PRESS_AT, released at RELEASE_AT, which also starts the startle.
    static let PRESS_AT: Double = 7.95
    static let RELEASE_AT: Double = 8.05
    static let BLINKS_AT: [Double] = [0.75, 5.2]
    static let ATTENTION_IN: [Double] = [1.3, 1.6]
    static let ATTENTION_OUT: [Double] = [7.6, 7.95]

    struct ScriptPosition {
        let p: Double
        let v: Double
    }

    /// Ruler position and velocity (ticks, ticks/s) at loop time t.
    static func scriptPos(_ t: Double) -> ScriptPosition {
        let s = mod(t, LOOP)
        var p = 0.0
        for k in SCRIPT {
            if s < k.a { return ScriptPosition(p: p, v: 0) }
            if s < k.b {
                let duration = k.b - k.a
                let u = (s - k.a) / duration
                return ScriptPosition(
                    p: hermite(k.p0, k.p1, k.m0, k.m1, u),
                    v: hermiteSlope(k.p0, k.p1, k.m0, k.m1, u) / duration
                )
            }
            p = k.p1
        }
        return ScriptPosition(p: p, v: 0)
    }

    // The velocity spring as a convolution kernel (weights sum to 1).
    private static let KERNEL_DT: Double = 1 / 60
    private static let KERNEL_N = 61

    private static func buildKernel() -> [Double] {
        let omega = FILTER.omega
        let zeta = FILTER.zeta
        let wd = omega * sqrt(1 - zeta * zeta)
        var weights: [Double] = []
        var sum = 0.0
        for n in 0..<KERNEL_N {
            let tau = Double(n) * KERNEL_DT
            let h = ((omega * omega) / wd) * exp(-zeta * omega * tau) * sin(wd * tau)
            weights.append(h)
            sum += h
        }
        return weights.map { $0 / sum }
    }

    static let KERNEL: [Double] = buildKernel()

    /// The velocity spring's output at loop time t (ticks/s).
    static func scriptVel(_ t: Double) -> Double {
        var x = 0.0
        for n in 0..<KERNEL.count {
            x += KERNEL[n] * scriptPos(t - Double(n) * KERNEL_DT).v
        }
        return x
    }

    /// Meridiem of a ruler position: 0 = PM side of the pin's day, flips every 12 h.
    static func meridiem(_ pos: Double) -> Int {
        let m = mod(Theme.REST_MINUTES / Theme.STEP_MINUTES + jsRound(pos), Theme.TICKS_PER_DAY)
        return m < Theme.TICKS_PER_DAY / 2 ? 1 : 0
    }

    struct Crossing {
        let t: Double
        let dir: Double
    }

    /// Every AM/PM crossing of the script: loop time and direction (+1 forward in time).
    private static func buildCrossings() -> [Crossing] {
        var out: [Crossing] = []
        var previous = jsRound(scriptPos(0).p)
        for i in 1...Int(LOOP * 1000) {
            let t = Double(i) / 1000
            let rounded = jsRound(scriptPos(t).p)
            if rounded != previous {
                if meridiem(rounded) != meridiem(previous) {
                    out.append(Crossing(t: t, dir: rounded > previous ? 1 : -1))
                }
                previous = rounded
            }
        }
        return out
    }

    static let CROSSINGS: [Crossing] = buildCrossings()

    /// The AM/PM card's angle in degrees: 0 = PM face, ±180 = AM face.
    static func scriptFlip(_ t: Double) -> Double {
        var angle = 0.0
        for crossing in CROSSINGS where t >= crossing.t {
            angle += crossing.dir * 180 * spring(t - crossing.t, FLIP.omega, FLIP.zeta)
        }
        return angle
    }

    /// One blink, `tau` seconds after it starts: 0 open → 1 closed → 0.
    static func blinkAt(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        let close = BLINK.close
        let hold = BLINK.hold
        let open = BLINK.open
        if tau < close { return smoothstep(tau / close) }
        if tau < close + hold { return 1 }
        return 1 - easeOut(seg(tau, close + hold, close + hold + open))
    }

    /// Seeded blinks under a finger: one every BLINK.period ± jitter, from `tau` seconds.
    static func liveBlink(_ tau: Double) -> Double {
        let k = floor(tau / BLINK.period)
        let start = k * BLINK.period + 1 + hash(k) * BLINK.jitter
        return blinkAt(tau - start)
    }

    static func scriptPress(_ t: Double) -> Double {
        if t < RELEASE_AT { return easeOut(seg(t, PRESS_AT, PRESS_AT + PRESS.in)) }
        return 1 - easeOut(seg(t, RELEASE_AT, RELEASE_AT + PRESS.out))
    }

    static func scriptAttention(_ t: Double) -> Double {
        easeOut(seg(t, ATTENTION_IN[0], ATTENTION_IN[1]))
            * (1 - easeInOut(seg(t, ATTENTION_OUT[0], ATTENTION_OUT[1])))
    }

    // MARK: Frames

    /// Everything that moves, as numbers.
    struct Frame {
        var pos: Double
        var vel: Double
        var attention: Double
        var blink: Double
        var startle: Double
        var press: Double
        var flip: Double

        init(
            pos: Double = 0,
            vel: Double = 0,
            attention: Double = 0,
            blink: Double = 0,
            startle: Double = -1,
            press: Double = 0,
            flip: Double = 0
        ) {
            self.pos = pos
            self.vel = vel
            self.attention = attention
            self.blink = blink
            self.startle = startle
            self.press = press
            self.flip = flip
        }
    }

    static let REST_FRAME = Frame()

    static func frameAt(_ t: Double) -> Frame {
        let s = mod(t, LOOP)
        let startle = s >= RELEASE_AT && s < RELEASE_AT + STARTLE.end ? s - RELEASE_AT : -1
        var blink = 0.0
        for blinkStart in BLINKS_AT {
            blink = max(blink, blinkAt(s - blinkStart))
        }
        return Frame(
            pos: scriptPos(s).p,
            vel: scriptVel(s),
            attention: scriptAttention(s),
            blink: blink,
            startle: startle,
            press: scriptPress(s),
            flip: scriptFlip(s)
        )
    }

    // MARK: Poses derived from a frame

    struct Pose {
        var y: Double
        var sx: Double
        var sy: Double
        var rot: Double
        var pupil: Double
        var blink: Double
        var lift: Double
        var arrow: Double
        var lean: Double
        var jumpY: Double
        var pupilDX: Double
        var pupilDY: Double
        var pupilScale: Double
        var needle: Double
        var sundial: Double

        init(
            y: Double = 0,
            sx: Double = 1,
            sy: Double = 1,
            rot: Double = 0,
            pupil: Double = 1,
            blink: Double = 0,
            lift: Double = 0,
            arrow: Double = 0,
            lean: Double = 0,
            jumpY: Double = 0,
            pupilDX: Double = 0,
            pupilDY: Double = 0,
            pupilScale: Double = 1,
            needle: Double = 0,
            sundial: Double = 0
        ) {
            self.y = y
            self.sx = sx
            self.sy = sy
            self.rot = rot
            self.pupil = pupil
            self.blink = blink
            self.lift = lift
            self.arrow = arrow
            self.lean = lean
            self.jumpY = jumpY
            self.pupilDX = pupilDX
            self.pupilDY = pupilDY
            self.pupilScale = pupilScale
            self.needle = needle
            self.sundial = sundial
        }
    }

    /// The startle's pose, `tau` seconds after the button's release. Distances in pt.
    static func startlePose(_ tau: Double) -> Pose {
        let s = STARTLE
        if tau < 0 || tau >= s.end { return Pose() }
        var y = 0.0
        var sx = 1.0
        var sy = 1.0
        var rot = 0.0
        if tau < s.crouch {
            let c = easeOut(tau / s.crouch)
            sy = 1 - s.crouchSy * c
            sx = 1 + s.crouchSx * c
        } else if tau < s.crouch + s.air {
            let u = (tau - s.crouch) / s.air
            y = -s.height * 4 * u * (1 - u)
            // Release from the crouch into a stretch, normal at the apex, a little stretch falling.
            let out = easeOut(seg(u, 0, 0.18))
            let rise = u < 0.5 ? s.stretch * (1 - 2 * u) : 0.4 * s.stretch * (2 * u - 1)
            sy = lerp(1 - s.crouchSy, 1 + rise, out)
            sx = lerp(1 + s.crouchSx, 1 - rise * 0.6, out)
            rot = s.rattleDeg * sin(2 * Double.pi * s.rattleHz * (tau - s.crouch)) * sin(Double.pi * u)
        } else {
            let l = tau - s.crouch - s.air
            let sq = s.landSquash * exp(-s.landDecay * l) * cos(s.landFreq * l)
            sy = 1 - sq
            sx = 1 + sq * 0.7
        }
        let shock = easeOut(seg(tau, 0.04, 0.16)) * (1 - easeInOut(seg(tau, 0.6, 0.95)))
        let arrow = s.arrow * (easeOut(seg(tau, 0, 0.14)) - easeInOut(seg(tau, 0.14, 0.5)))
        return Pose(
            y: y,
            sx: sx,
            sy: sy,
            rot: rot,
            pupil: 1 - s.pupil * shock,
            blink: blinkAt(tau - s.blinkAt),
            lift: -y / s.height,
            arrow: arrow
        )
    }

    /// Everything the views need, from a frame. `still` = Reduce Motion: no lean, eyes, bend or jump.
    static func poseOf(_ f: Frame, _ still: Bool) -> Pose {
        let k = still ? 0.0 : 1.0
        let st = startlePose(still ? -1 : f.startle)
        return Pose(
            sx: st.sx,
            sy: st.sy,
            rot: st.rot,
            pupil: st.pupil,
            blink: still ? 0 : max(f.blink, st.blink),
            lift: st.lift,
            arrow: st.arrow,
            lean: -LEAN.max * tanh(f.vel / LEAN.soft) * k + st.rot,
            jumpY: st.y,
            pupilDX: -EYES.x * tanh(f.vel / EYES.soft) * k,
            pupilDY: EYES.attention * f.attention * k,
            pupilScale: st.pupil,
            needle: -NEEDLE.max * tanh(f.vel / NEEDLE.soft) * k,
            sundial: still ? 0 : SUNDIAL.amp * sin((2 * Double.pi * f.pos) / Theme.TICKS_PER_DAY)
        )
    }

    // MARK: The clock: digit wheels

    struct Wheels {
        let ones: Double
        let tens: Double
        let hour: Double
    }

    /// The digit wheels at a ruler position. Values are continuous while a half-tick carries.
    static func wheels(_ pos: Double) -> Wheels {
        let k = floor(pos)
        let fr = pos - k
        let g = k + smoothstep((fr - 0.5) / RISER + 0.5)
        let m = mod(Theme.REST_MINUTES / Theme.STEP_MINUTES + g, Theme.TICKS_PER_DAY)
        let mi = floor(m)
        let mf = m - mi
        let ones = m
        let tens = floor(mi / 2) + (mi.truncatingRemainder(dividingBy: 2) == 1 ? mf : 0)
        let hour = floor(mi / 12) + (mi.truncatingRemainder(dividingBy: 12) == 11 ? mf : 0)
        return Wheels(ones: mod(ones, 2), tens: mod(tens, 6), hour: mod(hour, 12))
    }

    /// The label at the nearest tick, e.g. "1:05 PM".
    static func timeLabel(_ pos: Double) -> String {
        let minutes = mod(Theme.REST_MINUTES / Theme.STEP_MINUTES + jsRound(pos), Theme.TICKS_PER_DAY) * Theme.STEP_MINUTES
        let hour24 = Int(floor(minutes / 60))
        let minute = Int(minutes.truncatingRemainder(dividingBy: 60))
        let hour = hour24 % 12 == 0 ? 12 : hour24 % 12
        return "\(hour):\(minute < 10 ? "0" : "")\(minute) \(hour24 < 12 ? "AM" : "PM")"
    }

    // MARK: Ruler ticks

    /// Tick height (pt) by distance from the needle, measured on the pin (px ÷ 3); monotone cubic between.
    private static let TICK_D: [Double] = [0, 1, 2, 3, 4, 5]
    private static let TICK_H: [Double] = [59, 50, 36, 24, 16, 15].map { $0 / 3 }

    private static func buildSlopes() -> [Double] {
        let n = TICK_H.count
        var d: [Double] = []
        for i in 0..<(n - 1) { d.append(TICK_H[i + 1] - TICK_H[i]) }
        var slopes: [Double] = [d[0]]
        if n > 2 {
            for i in 1..<(n - 1) {
                slopes.append(d[i - 1] * d[i] <= 0 ? 0 : (2 * d[i - 1] * d[i]) / (d[i - 1] + d[i]))
            }
        }
        slopes.append(0)
        return slopes
    }

    static let TICK_SLOPE: [Double] = buildSlopes()

    static func tickHeight(_ dist: Double) -> Double {
        let d = abs(dist)
        if d >= TICK_D[TICK_D.count - 1] { return TICK_H[TICK_H.count - 1] }
        let i = Int(floor(d))
        let u = d - Double(i)
        return hermite(TICK_H[i], TICK_H[i + 1], TICK_SLOPE[i], TICK_SLOPE[i + 1], u)
    }

    /// Tick ink: dark up to 2 ticks from the needle, linear to light grey at 9. Returns 0..1 (0 = dark).
    static func tickShade(_ dist: Double) -> Double {
        clamp01((abs(dist) - 2) / 7)
    }

    // MARK: The live engine (under a finger)

    static let AUTO = 0
    static let LIVE = 1
    static let HOME = 2

    struct Engine {
        var mode: Int
        var autoStart: Double
        var autoPlay: Bool
        var still: Bool
        var pos: Double
        var dragging: Bool
        var dragFrom: Double
        var dragVel: Double
        var glideAt: Double
        var glideFrom: Double
        var glideTo: Double
        var glideV: Double
        var glideDur: Double
        var fx: Double
        var fv: Double
        var attention: Double
        var lastTouch: Double
        var liveSince: Double
        var handoffT: Double
        var startleAt: Double
        var pressInAt: Double
        var releaseAt: Double
        var pressFrom: Double
        var flipFrom: Double
        var flipTo: Double
        var flipAt: Double
        var lastClock: Double

        init(
            mode: Int = 1,
            autoStart: Double = 0,
            autoPlay: Bool = false,
            still: Bool = false,
            pos: Double = 0,
            dragging: Bool = false,
            dragFrom: Double = 0,
            dragVel: Double = 0,
            glideAt: Double = -1,
            glideFrom: Double = 0,
            glideTo: Double = 0,
            glideV: Double = 0,
            glideDur: Double = 1,
            fx: Double = 0,
            fv: Double = 0,
            attention: Double = 0,
            lastTouch: Double = -1e9,
            liveSince: Double = 0,
            handoffT: Double = -1,
            startleAt: Double = -1e9,
            pressInAt: Double = -1e9,
            releaseAt: Double = -1e9,
            pressFrom: Double = 0,
            flipFrom: Double = 0,
            flipTo: Double = 0,
            flipAt: Double = -1e9,
            lastClock: Double = 0
        ) {
            self.mode = mode
            self.autoStart = autoStart
            self.autoPlay = autoPlay
            self.still = still
            self.pos = pos
            self.dragging = dragging
            self.dragFrom = dragFrom
            self.dragVel = dragVel
            self.glideAt = glideAt
            self.glideFrom = glideFrom
            self.glideTo = glideTo
            self.glideV = glideV
            self.glideDur = glideDur
            self.fx = fx
            self.fv = fv
            self.attention = attention
            self.lastTouch = lastTouch
            self.liveSince = liveSince
            self.handoffT = handoffT
            self.startleAt = startleAt
            self.pressInAt = pressInAt
            self.releaseAt = releaseAt
            self.pressFrom = pressFrom
            self.flipFrom = flipFrom
            self.flipTo = flipTo
            self.flipAt = flipAt
            self.lastClock = lastClock
        }
    }

    static func createEngine(_ now: Double, _ autoPlay: Bool, _ still: Bool) -> Engine {
        Engine(
            mode: autoPlay && !still ? AUTO : LIVE,
            autoStart: now,
            autoPlay: autoPlay,
            still: still,
            pos: 0,
            dragging: false,
            dragFrom: 0,
            dragVel: 0,
            glideAt: -1,
            glideFrom: 0,
            glideTo: 0,
            glideV: 0,
            glideDur: 1,
            fx: 0,
            fv: 0,
            attention: 0,
            lastTouch: -1e9,
            liveSince: now,
            handoffT: -1,
            startleAt: -1e9,
            pressInAt: -1e9,
            releaseAt: -1e9,
            pressFrom: 0,
            flipFrom: 0,
            flipTo: 0,
            flipAt: -1e9,
            lastClock: now
        )
    }

    private static func flipNow(_ e: Engine, _ now: Double) -> Double {
        lerp(e.flipFrom, e.flipTo, spring(now - e.flipAt, FLIP.omega, FLIP.zeta))
    }

    private static func pressNow(_ e: Engine, _ now: Double) -> Double {
        if e.releaseAt < e.pressInAt { return easeOut(seg(now - e.pressInAt, 0, PRESS.in)) }
        return e.pressFrom * (1 - easeOut(seg(now - e.releaseAt, 0, PRESS.out)))
    }

    struct GlideState {
        let p: Double
        let v: Double
        let done: Bool
    }

    /// Position and velocity of the ruler from a running glide.
    private static func glideNow(_ e: Engine, _ now: Double) -> GlideState {
        let u = clamp01((now - e.glideAt) / e.glideDur)
        let m0 = e.glideV * e.glideDur
        return GlideState(
            p: hermite(e.glideFrom, e.glideTo, m0, 0, u),
            v: u >= 1 ? 0 : hermiteSlope(e.glideFrom, e.glideTo, m0, 0, u) / e.glideDur,
            done: u >= 1
        )
    }

    /// The resting angle nearest `angle` that shows the given face (0 = PM; 1 = AM).
    private static func faceAngle(_ angle: Double, _ face: Int) -> Double {
        let k = jsRound((angle - Double(face) * 180) / 360)
        return k * 360 + Double(face) * 180
    }

    /// The finger takes over: freeze whatever autoPlay shows at this instant into live state.
    static func takeOver(_ e: Engine, _ now: Double) -> Engine {
        if e.mode != AUTO {
            var result = e
            result.mode = LIVE
            result.lastTouch = now
            return result
        }
        let t = mod(now - e.autoStart, LOOP)
        let f = frameAt(t)
        let previous = scriptVel(t - 1 / 240)
        let inStartle = f.startle >= 0
        let flip = mod(f.flip + 180, 360) - 180
        // A scripted drag or glide in progress carries on as a real glide to its tick.
        let s = scriptPos(t)
        let moving = abs(s.v) > 0.01 || abs(s.p - jsRound(s.p)) > 1e-3
        let plan = glidePlan(s.p, s.v)
        var result = e
        result.mode = LIVE
        result.pos = f.pos
        result.glideAt = moving ? now : -1
        result.glideFrom = s.p
        result.glideTo = plan.target
        result.glideV = s.v
        result.glideDur = plan.dur
        result.fx = f.vel
        result.fv = (f.vel - previous) * 240
        result.attention = f.attention
        result.lastTouch = now
        result.liveSince = now
        result.handoffT = t
        result.startleAt = inStartle ? now - f.startle : -1e9
        result.pressInAt = -1e9
        result.releaseAt = now
        result.pressFrom = f.press
        result.flipFrom = flip
        result.flipTo = faceAngle(flip, meridiem(f.pos))
        result.flipAt = now
        return result
    }

    /// Touch down on the ruler: catch a glide where it is.
    static func rulerDown(_ e0: Engine, _ now: Double) -> Engine {
        let e = takeOver(e0, now)
        let pos = e.glideAt >= 0 ? glideNow(e, now).p : e.pos
        var result = e
        result.mode = LIVE
        result.pos = pos
        result.glideAt = -1
        result.dragging = false
        result.dragFrom = pos
        result.dragVel = 0
        result.lastTouch = now
        return result
    }

    static func dragStart(_ e: Engine, _ now: Double) -> Engine {
        var result = e
        result.dragging = true
        result.dragFrom = e.pos
        result.dragVel = 0
        result.lastTouch = now
        return result
    }

    /// `dx` and `vx`: finger translation (pt) and velocity (pt/s); `spacing`: pt per tick. Left = later.
    static func dragMove(_ e: Engine, _ now: Double, _ dx: Double, _ vx: Double, _ spacing: Double) -> Engine {
        guard e.dragging else { return e }
        var result = e
        result.pos = e.dragFrom - dx / spacing
        result.dragVel = -vx / spacing
        result.lastTouch = now
        return result
    }

    /// Let go (or a touch that never dragged): glide to the projected tick.
    static func release(_ e: Engine, _ now: Double, _ vx: Double, _ spacing: Double) -> Engine {
        let velocity = e.dragging ? -vx / spacing : 0
        let plan = glidePlan(e.pos, e.still ? 0 : velocity)
        var result = e
        result.dragging = false
        result.dragVel = 0
        result.glideAt = now
        result.glideFrom = e.pos
        result.glideTo = plan.target
        result.glideV = e.still ? 0 : velocity
        result.glideDur = plan.dur
        result.lastTouch = now
        return result
    }

    /// Accessibility: one tick later (+1) or earlier (−1).
    static func stepBy(_ e0: Engine, _ now: Double, _ dir: Double) -> Engine {
        let e = takeOver(e0, now)
        let from = e.glideAt >= 0 ? glideNow(e, now).p : e.pos
        var result = e
        result.mode = LIVE
        result.pos = from
        result.glideAt = now
        result.glideFrom = from
        result.glideTo = jsRound(from) + dir
        result.glideV = 0
        result.glideDur = GLIDE.still
        result.lastTouch = now
        return result
    }

    static func pressIn(_ e0: Engine, _ now: Double) -> Engine {
        let e = takeOver(e0, now)
        var result = e
        result.pressInAt = now
        result.lastTouch = now
        return result
    }

    static func pressOut(_ e: Engine, _ now: Double, _ commit: Bool) -> Engine {
        let from = pressNow(e, now)
        var result = e
        result.releaseAt = now
        result.pressFrom = from
        if commit && !e.still { result.startleAt = now }
        result.lastTouch = now
        return result
    }

    static func setAutoPlay(_ e: Engine, _ autoPlay: Bool, _ still: Bool, _ now: Double) -> Engine {
        // First call after mount, or Reduce Motion turned on: start from the rest frame.
        if e.lastClock == 0 || (still && !e.still) { return createEngine(now, autoPlay, still) }
        if autoPlay && !still {
            if e.mode == AUTO && e.autoPlay && !e.still { return e }
            var result = e
            result.autoPlay = autoPlay
            result.still = still
            result.mode = LIVE
            result.lastTouch = now - RESUME_AFTER
            return result
        }
        if e.mode == AUTO {
            var taken = e
            taken.autoPlay = autoPlay
            taken.still = still
            var result = takeOver(taken, now)
            result.lastTouch = -1e9
            return result
        }
        var result = e
        result.autoPlay = autoPlay
        result.still = still
        return result
    }

    struct FilterState {
        let x: Double
        let v: Double
    }

    /// Exact step of the velocity spring over dt with input u held constant.
    private static func filterStep(_ x: Double, _ v: Double, _ u: Double, _ dt: Double) -> FilterState {
        let omega = FILTER.omega
        let zeta = FILTER.zeta
        let wd = omega * sqrt(1 - zeta * zeta)
        let a = zeta * omega
        let e0 = x - u
        let env = exp(-a * dt)
        let c = cos(wd * dt)
        let s = sin(wd * dt)
        let b = (v + a * e0) / wd
        let e1 = env * (e0 * c + b * s)
        let v1 = env * (-a * (e0 * c + b * s) + (-e0 * wd * s + b * wd * c))
        return FilterState(x: u + e1, v: v1)
    }

    struct StepResult {
        var e: Engine
        var f: Frame
        var haptic: Bool
    }

    /// Advance the engine to `now` and return the frame to draw.
    static func step(_ e0: Engine, _ now: Double) -> StepResult {
        if e0.mode == AUTO {
            var next = e0
            next.lastClock = now
            return StepResult(e: next, f: frameAt(now - e0.autoStart), haptic: false)
        }
        var e = e0
        let dt = min(0.05, max(0, now - e.lastClock))

        // Ruler position and the velocity that drives the spring.
        var pos = e.pos
        var u = 0.0
        var moving = e.dragging
        if e.dragging {
            u = e.dragVel
        } else if e.glideAt >= 0 {
            let g = glideNow(e, now)
            pos = g.p
            u = g.v
            moving = !g.done
            if g.done { e.glideAt = -1 }
        }

        // Idle long enough: ease home (the nearest copy of the rest tick), then hand back to autoPlay.
        let startleLeft = e.startleAt + STARTLE.end - now
        if e.mode == LIVE && e.autoPlay && !e.still && !e.dragging && !moving
            && now - e.lastTouch >= RESUME_AFTER && startleLeft <= 0
        {
            let from = mod(pos + Theme.TICKS_PER_DAY / 2, Theme.TICKS_PER_DAY) - Theme.TICKS_PER_DAY / 2
            let duration = min(RETURN.max, RETURN.base + RETURN.perTick * abs(from))
            e.mode = HOME
            e.glideAt = now
            e.glideFrom = from
            e.glideTo = 0
            e.glideV = 0
            e.glideDur = duration
            pos = from
            moving = true
        }

        // AM/PM: a crossing starts a flip from wherever the card is.
        let before = meridiem(e.pos)
        let after = meridiem(pos)
        if before != after {
            let dir: Double = jsRound(pos) > jsRound(e.pos) ? 1 : -1
            let current = flipNow(e, now)
            e.flipFrom = current
            e.flipTo += dir * 180
            e.flipAt = now
        }

        let fs = filterStep(e.fx, e.fv, e.still ? 0 : u, dt)
        let engaged = e.dragging || moving
        let target = engaged || now - e.lastTouch < 0.5 ? 1.0 : 0.0
        let attention = lerp(target, e.attention, exp(-dt / ATTENTION_TAU))

        // A blink in progress when the finger took over finishes; then seeded blinks.
        let since = now - e.liveSince
        var blink = liveBlink(since)
        if e.handoffT >= 0 && since < 0.3 {
            for blinkStart in BLINKS_AT {
                blink = max(blink, blinkAt(e.handoffT + since - blinkStart))
            }
        }

        e.pos = pos
        e.fx = fs.x
        e.fv = fs.v
        e.attention = attention
        e.lastClock = now

        let startle = now - e.startleAt
        let frame = Frame(
            pos: pos,
            vel: fs.x,
            attention: attention,
            blink: blink,
            startle: startle >= 0 && startle < STARTLE.end ? startle : -1,
            press: pressNow(e, now),
            flip: flipNow(e, now)
        )

        // Home and settled: the loop restarts at its rest frame.
        if e.mode == HOME && e.glideAt < 0 && abs(e.fx) < 0.05 && abs(e.fv) < 0.5
            && attention < 0.01
            && abs(mod(frame.flip + 180, 360) - 180) < 0.5
            && frame.press < 0.001
        {
            e.mode = AUTO
            e.autoStart = now
            e.pos = 0
            e.fx = 0
            e.fv = 0
            e.attention = 0
            return StepResult(e: e, f: REST_FRAME, haptic: false)
        }
        return StepResult(e: e, f: frame, haptic: e.mode == LIVE && engaged)
    }
}
