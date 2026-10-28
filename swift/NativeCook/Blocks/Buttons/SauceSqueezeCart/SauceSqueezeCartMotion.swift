import Foundation

/*
 Sauce Squeeze Cart — the timeline, the springs and the touch driver.

 Every frame is a pure function of time. Before the release, the squeeze `s` (0…1) comes from the
 script (autoPlay) or from how long the finger has held (touch). After the release, everything is a
 function of τ, the time since the release, so the touch-driven shot replays the loop's own show.

 autoPlay: loop = 8 s
   0.0 – 1.2   rest: the bottle bobs gently on the pink page
   1.1 – 2.6   the "Hold to add" button is held: the bottle squashes (wider, shorter, the waist
               pinched), trembles, a bead of sauce swells at the nozzle
   2.6         release (τ = 0): the bottle snaps into a stretch and recoils up, a thick glossy stream
               whips out of the cap and down the screen
   2.9 – 3.8   a red pool rises from the bottom with a wavy surface until the screen is red
   3.95 – 5.7  stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an arrow, a sparkle)
   5.5 – 6.4   the red retracts up the screen, drips hanging from it, and reveals the page
   6.4 – 7.3   the cart in the header bumps and a "+1" floats up
   7.3 – 8.0   rest (the frame at 8 is the frame at 0)
*/

struct SauceSqueezeCartFrame {
    /// Hold pressure, 0…1: the button's fill and the bead at the nozzle.
    var s = 0.0
    /// Squash (+) or stretch (−) of the bottle.
    var q = 0.0
    /// The button pressed in, 0…1.
    var press = 0.0
    /// Recoil of the bottle, pt (negative is up).
    var lift = 0.0
    /// Extra rotation of the bottle, degrees.
    var spin = 0.0
    /// Idle bob, −1…1 (and its sway).
    var bob = 0.0
    var sway = 0.0
    /// Tremble of a hard squeeze, pt.
    var shake = 0.0
    /// Time since the release when a shot is running.
    var tau: Double?
    /// The squeeze the shot was released with (scales the recoil).
    var shotS = 1.0
    var reduced = false
}

enum SauceSqueezeCartMotion {
    static let loop = 8.0

    static let releaseAt = 2.6
    static let squeezeFrom = 1.2
    static let squeezeTo = 2.45
    /// When a shot has played out (rest again), in τ.
    static let shotEnd = 4.8
    /// The screen is fully red from here, in τ.
    static let coveredAt = 1.2
    static let threshold = 0.4
    static let idleBeforeBack = 2.0
    /// 60 cycles per loop = 7.5 Hz, zero at the release.
    static let trembleK = 60.0

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double { smooth((x - a) / (b - a)) }
    static func easeOut(_ u: Double) -> Double { let c = 1 - clamp01(u); return 1 - c * c * c }
    static func easeInOut(_ u: Double) -> Double {
        let c = clamp01(u)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }
    static func easeInOutQuad(_ u: Double) -> Double {
        let c = clamp01(u)
        return c < 0.5 ? 2 * c * c : 1 - pow(-2 * c + 2, 2) / 2
    }
    static func easeInBack(_ u: Double) -> Double {
        let c = clamp01(u), k = 1.70158
        return (k + 1) * c * c * c - k * c * c
    }
    static func hash(_ i: Double) -> Double {
        let v = sin(i * 12.9898 + 78.233) * 43758.5453
        return v - v.rounded(.down)
    }

    /// An analytic damped spring (damping < 1) at time τ after it starts from x0 with velocity v0.
    static func spring(_ tau: Double, x0: Double, v0: Double, to target: Double,
                       response: Double, damping: Double) -> Double {
        if tau <= 0 { return x0 }
        let z = min(damping, 0.999)
        let w0 = 2 * Double.pi / response
        let wd = w0 * (1 - z * z).squareRoot()
        let a = x0 - target
        let b = (v0 + z * w0 * a) / wd
        return target + exp(-z * w0 * tau) * (a * cos(wd * tau) + b * sin(wd * tau))
    }

    // MARK: autoPlay

    static func autoFrame(_ t: Double) -> SauceSqueezeCartFrame {
        var f = SauceSqueezeCartFrame()
        let phase = 2 * Double.pi * 2 * t / loop
        f.bob = sin(phase)
        f.sway = sin(phase + 1.1)
        if t < releaseAt {
            let s = easeInOut(seg(t, squeezeFrom, squeezeTo))
            let tr = sin(2 * Double.pi * trembleK * t / loop)
            f.s = s
            f.q = s + 0.03 * s * s * tr
            f.shake = 1.6 * s * s * tr
            f.press = easeOut(seg(t, 1.1, 1.25))
        } else {
            shot(&f, tau: t - releaseAt, s0: 1)
        }
        return f
    }

    /// The bottle after a release with squeeze s0, τ seconds ago.
    static func shot(_ f: inout SauceSqueezeCartFrame, tau: Double, s0: Double) {
        f.tau = tau
        f.shotS = s0
        f.s = s0 * (1 - easeOut(seg(tau, 0, 0.2)))
        f.q = max(-1, spring(tau, x0: s0, v0: -14 * s0, to: 0, response: 0.55, damping: 0.38))
        f.lift = spring(tau, x0: 0, v0: -560 * s0, to: 0, response: 0.6, damping: 0.5)
        f.spin = spring(tau, x0: 0, v0: 60 * s0, to: 0, response: 0.6, damping: 0.45)
        f.press = 1 - easeOut(seg(tau, 0, 0.15))
    }

    // MARK: The show after the release (functions of τ)

    /// How far the stream's head has travelled, 0…1.
    static func streamHead(_ tau: Double) -> Double { easeOut(seg(tau, 0, 0.35)) }
    static func streamWidth(_ tau: Double) -> Double {
        44 * easeOut(seg(tau, 0, 0.12)) + 106 * easeInOut(seg(tau, 0.35, 1.0))
    }
    static func streamSway(_ tau: Double) -> Double {
        26 * easeOut(seg(tau, 0, 0.35)) + 54 * easeInOut(seg(tau, 0.35, 1.0))
    }
    static func streamVisible(_ tau: Double) -> Bool { tau >= 0 && tau < 1.25 }

    /// The red's top edge before its waves: it floods up from below the screen.
    static func floodLevel(_ tau: Double, h: Double) -> Double {
        mix(h + 30, -80, easeInOut(seg(tau, 0.3, coveredAt)))
    }

    /// The red's bottom edge before its waves and drips: it retracts up towards the cart.
    static func drainLevel(_ tau: Double, h: Double) -> Double {
        mix(h + 60, -140, easeInOutQuad(drain(tau)))
    }

    /// How far the drain has gone, 0…1 (drives the drips).
    static func drain(_ tau: Double) -> Double { seg(tau, 2.9, 3.8) }

    static let dripX = [0.14, 0.36, 0.58, 0.8, 0.93]
    static let dripW = [16.0, 22, 13, 19, 14]
    static let dripL = [110.0, 170, 80, 140, 95]

    static func waves(_ x: Double, _ tau: Double) -> Double {
        10 * sin(2 * Double.pi * (x / 220 + 1.3 * tau)) + 6 * sin(2 * Double.pi * (x / 97 - 2.1 * tau) + 1.3)
    }

    /// The red's top edge at x (the flood's wavy surface).
    static func redTop(_ x: Double, tau: Double, h: Double) -> Double {
        floodLevel(tau, h: h) + waves(x, tau)
    }

    /// The red's bottom edge at x: a wavy line with drips hanging from it while it rises.
    static func redBottom(_ x: Double, tau: Double, w: Double, h: Double) -> Double {
        let u = drain(tau)
        var y = drainLevel(tau, h: h) + waves(x, tau + 0.4)
        guard u > 0 && u < 1 else { return y }
        let grow = pow(sin(Double.pi * u), 0.7)
        var hang = 0.0
        for i in 0..<dripX.count {
            let dx = abs(x - dripX[i] * w)
            let hw = dripW[i]
            let len = dripL[i] * grow
            var d = 0.0
            if dx < hw {
                let cap = (hw * hw - dx * dx).squareRoot()
                d = len > hw ? len - hw + cap : len * cap / hw
            }
            let fillet = min(len, 40) * 0.5 * exp(-pow(dx / (hw * 1.9), 2))
            hang = max(hang, max(d, fillet))
        }
        y += hang
        return y
    }

    /// Reduced motion: the red fades in and out instead of flooding.
    static func redFade(_ tau: Double) -> Double {
        smooth(seg(tau, 0, 0.3)) * (1 - smooth(seg(tau, 2.7, 3.1)))
    }

    /// A sticker's scale: pops in on a playful spring, pops out with a little anticipation.
    static func sticker(_ i: Int, tau: Double) -> Double {
        let start = 1.35 + 0.07 * Double(i)
        guard tau >= start else { return 0 }
        let pop = spring(tau - start, x0: 0, v0: 0, to: 1, response: 0.5, damping: 0.55)
        let out = seg(tau, 2.70 + 0.04 * Double(5 - i), 2.92 + 0.04 * Double(5 - i))
        return max(0, pop * (1 - easeInBack(out)))
    }

    /// The cart button's bump, around 0 (scale = 1 + 0.6 × bump).
    static func cartBump(_ tau: Double) -> Double {
        spring(tau - 3.8, x0: 0, v0: 7, to: 0, response: 0.42, damping: 0.4)
    }

    /// The "+1" over the cart: scale, rise (pt) and opacity.
    static func plusOne(_ tau: Double) -> (scale: Double, rise: Double, alpha: Double) {
        let c = tau - 3.85
        guard c > 0 else { return (0, 0, 0) }
        let scale = spring(c, x0: 0, v0: 0, to: 1, response: 0.45, damping: 0.55)
        return (max(0, scale), 10 * easeOut(seg(c, 0, 0.85)), 1 - smooth(seg(c, 0.5, 0.85)))
    }
}

/// The touch session and the loop clock. The screen asks it for a frame each tick.
final class SauceSqueezeCartDriver {
    typealias M = SauceSqueezeCartMotion

    private var autoStart: Double?
    private(set) var manual = false
    private var holdStart: Double?
    private var holdS0 = 0.0
    private var softAt: Double?
    private var softS = 0.0
    private(set) var shotAt: Double?
    private var shotS = 1.0
    private var lastTouch = -1e9
    private var bob0 = 0.0
    private var sway0 = 0.0
    private var manualAt = 0.0
    /// The finger is down but arrived while a shot was running; it only delays the restart.
    private var ignoring = false
    var touching: Bool { holdStart != nil || ignoring }

    func reset() {
        autoStart = nil
        manual = false
        holdStart = nil
        softAt = nil
        shotAt = nil
        ignoring = false
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> SauceSqueezeCartFrame {
        if auto && manual && !touching {
            var resume = lastTouch + M.idleBeforeBack
            if let sa = shotAt { resume = max(resume, sa + M.shotEnd) }
            if now >= resume { manual = false; autoStart = now; shotAt = nil; softAt = nil }
        }
        var f: SauceSqueezeCartFrame
        if auto && !manual {
            if autoStart == nil { autoStart = now }
            let t = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
            f = M.autoFrame(t)
        } else {
            f = manualFrame(at: now)
        }
        f.reduced = reduced
        if reduced {
            f.q = 0; f.lift = 0; f.spin = 0; f.bob = 0; f.sway = 0; f.shake = 0
        }
        return f
    }

    private func manualFrame(at now: Double) -> SauceSqueezeCartFrame {
        var f = SauceSqueezeCartFrame()
        let decay = exp(-(now - manualAt) / 0.35)
        f.bob = bob0 * decay
        f.sway = sway0 * decay
        if let sa = shotAt {
            M.shot(&f, tau: now - sa, s0: shotS)
        } else if let hs = holdStart {
            let s = holdLevel(now - hs)
            let tr = sin(2 * Double.pi * 7.5 * now)
            f.s = s
            f.q = s + 0.03 * s * s * tr
            f.shake = 1.6 * s * s * tr
            f.press = M.easeOut(M.seg(now - hs, 0, 0.15))
        } else if let sf = softAt {
            let u = now - sf
            f.s = softS * (1 - M.easeOut(M.seg(u, 0, 0.2)))
            f.q = M.spring(u, x0: softS, v0: 0, to: 0, response: 0.45, damping: 0.5)
            f.press = 1 - M.easeOut(M.seg(u, 0, 0.15))
        }
        return f
    }

    private func holdLevel(_ held: Double) -> Double {
        holdS0 + (1 - holdS0) * (1 - exp(-held / 0.6))
    }

    /// Seconds of holding until the squeeze is armed (0 if it already is).
    func armDelay() -> Double {
        guard holdStart != nil, holdS0 < M.threshold else { return 0 }
        return -0.6 * log((1 - M.threshold) / (1 - holdS0))
    }

    /// The finger goes down. Returns false when a shot is running and the touch is ignored.
    @discardableResult
    func begin(at now: Double, auto: Bool, reduced: Bool) -> Bool {
        let current = frame(at: now, auto: auto, reduced: reduced)
        lastTouch = now
        if !manual {
            manual = true
            manualAt = now
            bob0 = current.bob
            sway0 = current.sway
            if let tau = current.tau, tau < M.shotEnd {
                shotAt = now - tau
                shotS = current.shotS
                ignoring = true
                return false
            }
        }
        if let sa = shotAt, now - sa < M.shotEnd {
            ignoring = true
            return false
        }
        shotAt = nil
        softAt = nil
        holdS0 = M.clamp01(current.tau == nil ? current.s : 0)
        holdStart = now
        return true
    }

    /// Shoots at once (the accessibility action), unless a shot is already running.
    func shoot(at now: Double, auto: Bool, reduced: Bool) {
        guard begin(at: now, auto: auto, reduced: reduced) else { ignoring = false; return }
        holdStart = nil
        lastTouch = now
        shotAt = now
        shotS = 1
    }

    /// The finger lifts. Returns true when the squeeze was strong enough to shoot.
    @discardableResult
    func end(at now: Double) -> Bool {
        lastTouch = now
        if ignoring { ignoring = false; return false }
        guard let hs = holdStart else { return false }
        let s = holdLevel(now - hs)
        holdStart = nil
        if s >= M.threshold {
            shotAt = now
            shotS = s
            return true
        }
        softAt = now
        softS = s
        return false
    }
}
