import Foundation

/*
 Typewriter Sign Up — the loop script, as pure functions of the loop time.

 Every frame of autoPlay is `autoPose(t)` with t = elapsed % loop, so the frame at t = loop is
 the frame at t = 0 (a blank sheet in the carriage, carriage at column 0, line 0).

 loop = 10 s
   0.0 – 1.2   rest: blank sheet, typebar fan still.
   1.2 – 2.6   types "Mara Quill" (10 strikes, humanised spacing).
   2.55        the margin bell rings; 2.6 – 3.1 carriage return (ease-in-out) + line feed.
   3.1 – 5.0   types "mara@inkwell.co".
   4.95        bell; 5.0 – 5.4 carriage return + line feed.
   5.4 – 6.6   stamps eight password dots.
   6.75 – 6.95 the CTA is pressed.
   7.0 – 8.2   the sheet is pulled: 4 pt anticipation down, then up and out with a flex.
   8.2 – 8.4   empty carriage (form clears).
   8.4 – 9.4   a blank sheet feeds back in from above (ease-out, 3 pt settle).
   9.4 – 10    rest.
*/
enum TypewriterSignupMotion {
    /// What the three fields type in the loop (the password is stamped as dots).
    static let script: [[Character]] = [Array("Mara Quill"), Array("mara@inkwell.co"), Array("••••••••")]
    static let scriptText = ["Mara Quill", "mara@inkwell.co", "••••••••"]
    private static let lineStart = [1.2, 3.1, 5.4]
    private static let lineDur = [1.4, 1.9, 1.2]
    private static let returnAt = [2.6, 5.0]
    private static let returnDur = [0.5, 0.4]
    private static let bellLead = 0.05
    static let pullStart = 7.0
    private static let anticipation = 0.14
    static let pullEnd = 8.2
    private static let backStart = 8.4
    private static let backEnd = 9.4
    private static let pressAt = 6.75
    private static let pressLen = 0.2
    /// How far the sheet travels to leave the screen (pt).
    static let sheetAway = 680.0

    /// Typebar timing: up in 70 ms (ease-out), back in 110 ms.
    static let strikeUp = 0.07
    static let strikeDown = 0.11
    /// Number of typebars drawn in the fan.
    static let bars = 9

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }

    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// A carriage step: a stiff, lightly underdamped spring from 0 to 1 (τ in seconds).
    static func carriageStep(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        return 1 - exp(-30 * tau) * cos(48 * tau)
    }

    /// The typebar's extension τ seconds after a key: up in 70 ms, back in 110 ms.
    static func strikeCurve(_ tau: Double) -> Double {
        if tau < 0 { return 0 }
        if tau < strikeUp { return easeOutCubic(tau / strikeUp) }
        if tau < strikeUp + strikeDown { return 1 - easeInOutCubic((tau - strikeUp) / strikeDown) }
        return 0
    }

    /// The bell τ seconds after it is hit: a damped wobble and a glow.
    static func bellCurve(_ tau: Double) -> (Double, Double) {
        if tau < 0 || tau > 1.2 { return (0, 0) }
        let env = exp(-5 * tau)
        return (sin(tau * 42) * env, exp(-3.2 * tau) * clamp01(tau / 0.04))
    }

    /// Which typebar strikes for glyph k of line l.
    static func barFor(_ l: Int, _ k: Int) -> Int {
        Int(floor(hash(Double(l * 97 + k * 13 + 5)) * Double(bars)))
    }

    /// When glyph k of line l is struck in the loop (humanised, still monotonic).
    private static func keyTime(_ l: Int, _ k: Int) -> Double {
        let n = Double(script[l].count)
        return lineStart[l] + (lineDur[l] * (Double(k) + 0.5 + 0.38 * (hash(Double(l * 31 + k)) - 0.5))) / n
    }

    private static func typedAt(_ l: Int, _ t: Double) -> Int {
        var c = 0
        for k in 0..<script[l].count where keyTime(l, k) <= t { c = k + 1 }
        return c
    }

    static func restPose() -> TypewriterSignupPose { TypewriterSignupPose() }

    /// The legible end state shown under reduced motion: all three lines typed.
    static func filledPose() -> TypewriterSignupPose {
        var p = restPose()
        p.n = [script[0].count, script[1].count, script[2].count]
        p.line = 2
        p.col = Double(script[2].count)
        return p
    }

    static func autoPose(_ t: Double) -> TypewriterSignupPose {
        var p = restPose()
        if t >= pullEnd && t < backStart {
            p.pull = sheetAway
            p.done = 1
            return p
        }
        if t >= backStart {
            // A fresh blank sheet feeds in from above; the label returns.
            let u = seg(t, backStart, backEnd)
            let e = easeOutCubic(u)
            let settle = u >= 1 ? 0 : -3 * sin(.pi * clamp01((u - 0.7) / 0.3))
            p.pull = sheetAway * (1 - e) + settle
            p.done = 1 - easeInOutCubic(seg(t, backStart, backStart + 0.6))
            return p
        }
        p.n = [typedAt(0, t), typedAt(1, t), typedAt(2, t)]

        // Which line the carriage is on and where it stands.
        var l = 0
        if t >= returnAt[1] { l = 2 } else if t >= returnAt[0] { l = 1 }
        let typedHere = p.n[l]
        var lastKey = -1.0
        if typedHere > 0 { lastKey = keyTime(l, typedHere - 1) }

        var col = 0.0
        var line = Double(l)
        for r in 0..<2 {
            let a = returnAt[r]
            let d = returnDur[r]
            if t >= a && t < a + d {
                let u = easeInOutCubic(seg(t, a, a + d))
                col = Double(script[r].count) * (1 - u)
                line = Double(r) + u
                lastKey = -1
            } else if t >= a + d && l == r + 1 {
                // A little knock as the carriage hits its stop, then the new line's keys.
                let tau = t - (a + d)
                col = -0.32 * exp(-12 * tau) * sin(30 * tau)
                line = Double(r + 1)
            }
        }
        if lastKey >= 0 {
            col = Double(typedHere - 1) + carriageStep(t - lastKey)
        } else if l == 0 {
            col = 0
        }
        p.col = col
        p.line = line

        if lastKey >= 0 {
            let tau = t - lastKey
            p.strike = strikeCurve(tau)
            p.inkAge = tau
            p.bar = barFor(l, typedHere - 1)
        }
        for r in 0..<2 {
            let b = bellCurve(t - (returnAt[r] - bellLead))
            if b.1 > p.bellGlow {
                p.bell = b.0
                p.bellGlow = b.1
            }
        }

        // CTA press and the pull.
        let pr = seg(t, pressAt, pressAt + pressLen)
        p.press = sin(.pi * pr)
        if t >= pullStart {
            let ua = seg(t, pullStart, pullStart + anticipation)
            let up = seg(t, pullStart + anticipation, pullEnd)
            p.pull = -4 * sin(.pi * ua) * (up > 0 ? 0 : 1) + sheetAway * easeInOutCubic(up)
            p.flex = sin(.pi * up)
            p.fields = 1 - easeOutCubic(seg(t, pullStart, pullStart + 0.5))
            p.done = easeOutCubic(seg(t, pullStart + 0.4, pullStart + 0.9))
        }
        return p
    }
}

/// Everything one frame draws.
struct TypewriterSignupPose {
    /// Glyphs typed on each of the three lines.
    var n = [0, 0, 0]
    /// Line feed (0…2, fractional while the paper advances) and carriage column (glyph units).
    var line = 0.0
    var col = 0.0
    /// Typebar extension 0…1 and which bar of the fan strikes.
    var strike = 0.0
    var bar = 4
    /// Seconds since the newest glyph landed (ink spread), large when none.
    var inkAge = 9.0
    /// Bell wobble −1…1 and glow 0…1.
    var bell = 0.0
    var bellGlow = 0.0
    /// Sheet offset upward in pt (negative = pushed down), and flex −1…1.
    var pull = 0.0
    var flex = 0.0
    /// CTA press 0…1, the "pulled" label 0…1, the field text opacity.
    var press = 0.0
    var done = 0.0
    var fields = 1.0
}

/// A cubic Bézier easing, as CSS and Reanimated's `Easing.bezier` define it.
struct TypewriterSignupBezier {
    let x1: Double, y1: Double, x2: Double, y2: Double

    func callAsFunction(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var u = x
        for _ in 0..<8 {
            let cx = curve(u, x1, x2) - x
            let d = slope(u, x1, x2)
            if abs(cx) < 1e-6 { break }
            if abs(d) < 1e-6 { break }
            u -= cx / d
        }
        // Bisection if Newton wandered off.
        if u < 0 || u > 1 || abs(curve(u, x1, x2) - x) > 1e-4 {
            var lo = 0.0, hi = 1.0
            u = x
            for _ in 0..<30 {
                let v = curve(u, x1, x2)
                if v < x { lo = u } else { hi = u }
                u = (lo + hi) / 2
            }
        }
        return curve(u, y1, y2)
    }

    private func curve(_ u: Double, _ a: Double, _ b: Double) -> Double {
        let v = 1 - u
        return 3 * v * v * u * a + 3 * v * u * u * b + u * u * u
    }

    private func slope(_ u: Double, _ a: Double, _ b: Double) -> Double {
        let v = 1 - u
        return 3 * v * v * a + 6 * v * u * (b - a) + 3 * u * u * (1 - b)
    }
}

/// One animated number of the user state: a timed tween or a spring, like a Reanimated shared value.
struct TypewriterSignupValue {
    enum Curve {
        case bezier(TypewriterSignupBezier)
        /// Reanimated's default `withTiming` easing, `Easing.inOut(Easing.quad)`.
        case inOutQuad
    }

    private enum Drive {
        case none
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: Curve)
        case spring(target: Double, damping: Double, stiffness: Double, mass: Double)
    }

    private(set) var value: Double
    private var velocity = 0.0
    private var drive = Drive.none
    /// Called once when a timing reaches its end (not when it is replaced).
    private var completion: (() -> Void)?

    init(_ value: Double) { self.value = value }

    mutating func set(_ v: Double) {
        value = v
        velocity = 0
        drive = .none
        completion = nil
    }

    mutating func timing(to: Double, duration: Double, curve: Curve = .inOutQuad, now: Double, completion: (() -> Void)? = nil) {
        if duration <= 0 {
            set(to)
            completion?()
            return
        }
        drive = .timing(from: value, to: to, start: now, duration: duration, curve: curve)
        velocity = 0
        self.completion = completion
    }

    mutating func spring(to: Double, velocity v: Double? = nil, damping: Double, stiffness: Double, mass: Double = 1) {
        if let v { velocity = v }
        drive = .spring(target: to, damping: damping, stiffness: stiffness, mass: mass)
        completion = nil
    }

    /// Advances to `now` (dt seconds after the previous frame). Returns a completion to run, if any.
    mutating func step(now: Double, dt: Double) -> (() -> Void)? {
        switch drive {
        case .none:
            return nil
        case let .timing(from, to, start, duration, curve):
            let u = TypewriterSignupMotion.clamp01((now - start) / duration)
            let e: Double
            switch curve {
            case let .bezier(b): e = b(u)
            case .inOutQuad: e = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
            }
            value = from + (to - from) * e
            if u >= 1 {
                drive = .none
                let c = completion
                completion = nil
                return c
            }
            return nil
        case let .spring(target, damping, stiffness, mass):
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 240)
                let force = -stiffness * (value - target) - damping * velocity
                velocity += force / mass * h
                value += velocity * h
                left -= h
            }
            if abs(value - target) < 0.01 && abs(velocity) < 0.05 {
                value = target
                velocity = 0
                drive = .none
            }
            return nil
        }
    }
}
