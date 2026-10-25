import Foundation
import SwiftUI
import UIKit

// The blob's state and its timeline, stepped once a frame from the view's TimelineView and read by
// the shader's uniforms. In autoPlay every value is a pure function of the loop time t (so
// t = loop draws the same frame as t = 0); under a finger the shape weights and the held droplet
// run on springs. A step-for-step port of liquidMetalMotion.ts.
//
// Units: points and seconds. Positions are on screen; `cx`, `cy` is the blob's centre and `R` its
// size (the drop's round end is 0.9 R across, the ring's centre line 0.8 R from the middle).

enum LiquidMetalMorphTuning {
    /// drop → star, star → ring, ring → drop (s). Rest is 8.4 → 9 → 1.6 (a 2.2 s still drop).
    static let transitions: [(Double, Double)] = [(1.6, 3.2), (4.6, 6.2), (7.0, 8.4)]
    // After each morph the metal jiggles: a 5-lobe ripple of jiggleAmp pt, period jigglePeriod,
    // fading to nothing (value and slope) over jiggleFor.
    static let jiggleAmp = 3.2
    static let jigglePeriod = 0.32
    static let jiggleFor = 0.6
    /// The resting wobble (pt), two lobed sines of 2 and 3 cycles per loop.
    static let wobbleAmp = 2.2
    // Satellites: four droplets leave the surface during each morph, reach satOut × R from the
    // centre at mid-morph and fall back in, each staggered by satStagger of the morph.
    static let satOut = 0.62
    static let satStagger = 0.08
    static let satK = 22.0
    // Under a finger: the shape weights follow the chosen shape on a playful spring (rad/s,
    // ratio), the held droplet follows the finger closely and flies home on a looser one.
    static let shapeW = 9.0
    static let shapeZ = 0.62
    static let holdW = 26.0
    static let holdZ = 0.9
    static let homeW = 13.0
    static let homeZ = 0.5
    /// The droplet's tether: smooth-min reach (pt).
    static let tetherK = 26.0
    /// Light haptic when the droplet is pulled this far from where it was grabbed (pt).
    static let detachAt = 120.0
    /// Back to rest: wait this long after the last touch, then ease home over backFor (s).
    static let idleBeforeBack = 2.0
    static let backFor = 0.85
    /// The state label's roll (s).
    static let labelRoll = 0.26
}

enum LiquidMetalMorphMode {
    case auto, live, back
}

/// What the view reads each frame.
struct LiquidMetalMorphFrame {
    var w: (Double, Double, Double) = (1, 0, 0)
    var wob: (Double, Double, Double, Double) = (0, 0, 0, 0)
    var sats: [Float] = Array(repeating: 0, count: 16)
    var finger: (Double, Double, Double, Double) = (0, 0, 0, 0)
    /// The three state labels' roll: 0 = waiting below, 1 = shown, 2 = gone above.
    var labels: (Double, Double, Double) = (1, 0, 0)
}

@MainActor
final class LiquidMetalMorphEngine {
    private typealias T = LiquidMetalMorphTuning

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    /// Shortest signed angle from a to b.
    private static func angleTo(_ a: Double, _ b: Double) -> Double {
        let tau = 2 * Double.pi
        var d = (b - a).truncatingRemainder(dividingBy: tau)
        d = (d + 3 * .pi).truncatingRemainder(dividingBy: tau)
        if d < 0 { d += tau }
        return d - .pi
    }

    private static func spring(_ x: Double, _ v: Double, _ x0: Double, _ w: Double, _ z: Double, _ dt: Double) -> (Double, Double) {
        let nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
        return (x + nv * dt, nv)
    }

    private static let roll = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    // State (see liquidMetalMotion.ts → Metal).
    private(set) var mode: LiquidMetalMorphMode = .auto
    private var configured: (auto: Bool, reduced: Bool)?
    private var autoStart: Date?
    private var last: Date?
    private var t = 0.0
    private(set) var now = 0.0
    private var lastTouch = -100.0
    private(set) var w = [1.0, 0, 0]
    private var v = [0.0, 0, 0]
    private var target = 0
    private var p1 = 0.0
    private var p2 = 0.0
    private var amp = 0.0
    private var jiggle = 0.0
    private var sats = [Float](repeating: 0, count: 16)
    private var held = false
    private var shown = false
    private var fx = 0.0, fy = 0.0
    private var dx = 0.0, dy = 0.0
    private var dvx = 0.0, dvy = 0.0
    private var hx = 0.0, hy = 0.0
    private var detached = false
    private var backAt = 0.0
    private var backW = [1.0, 0, 0]
    private var backP1 = 0.0
    private var backP2 = 0.0
    private var dragging = false
    private var auto = false
    // The labels' roll: which shape is dominant, and per label a tween (from, to, start).
    private var active = 0
    private var rolls: [(from: Double, to: Double, at: Double)] = [(1, 1, -100), (0, 0, -100), (0, 0, -100)]
    private let light = UIImpactFeedbackGenerator(style: .light)

    private func configure(auto: Bool, reduced: Bool) {
        configured = (auto, reduced)
        self.auto = auto
        mode = auto ? .auto : .live
        t = 0
        autoStart = nil
        lastTouch = -100
        w = [1, 0, 0]
        v = [0, 0, 0]
        target = 0
        p1 = 0
        p2 = 0
        amp = reduced ? 0 : T.wobbleAmp
        jiggle = 0
        sats = [Float](repeating: 0, count: 16)
        held = false
        shown = false
        detached = false
        active = 0
        rolls = [(1, 1, -100), (0, 0, -100), (0, 0, -100)]
    }

    /// True when nothing on screen can change until the next touch (the timeline may pause).
    var isIdle: Bool {
        guard mode == .live, !auto, amp == 0, !held, !shown, !dragging else { return false }
        let still = zip(w, v).enumerated().allSatisfy { i, wv in
            abs(wv.0 - (i == target ? 1 : 0)) < 0.0005 && abs(wv.1) < 0.002
        }
        let rolling = rolls.contains { now - $0.at < T.labelRoll }
        return still && !rolling
    }

    func step(to date: Date, R: Double, auto: Bool, reduced: Bool) -> LiquidMetalMorphFrame {
        if configured?.auto != auto || configured?.reduced != reduced {
            configure(auto: auto, reduced: reduced)
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        if R > 0 {
            now += dt
            switch mode {
            case .auto:
                if auto {
                    // The loop time comes straight from the clock, so a slow frame never stretches it.
                    let start = autoStart ?? date
                    autoStart = start
                    t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: LiquidMetalMorph.loop)
                }
                scriptFrame(t, R)
            case .live:
                liveFrame(dt)
                if auto && !held && now - lastTouch > T.idleBeforeBack { beginBack() }
            case .back:
                if backFrame(dt) {
                    mode = .auto
                    t = 0
                    autoStart = date
                }
            }
            updateLabels()
        }
        var f = LiquidMetalMorphFrame()
        f.w = (w[0], w[1], w[2])
        f.wob = (p1, p2, amp, jiggle)
        f.sats = sats
        f.finger = (dx, dy, shown ? dropletRadius(R) : 0, T.tetherK)
        f.labels = (label(0), label(1), label(2))
        return f
    }

    // MARK: Labels

    private func label(_ i: Int) -> Double {
        let r = rolls[i]
        let k = min(max((now - r.at) / T.labelRoll, 0), 1)
        return r.from + (r.to - r.from) * Self.roll.value(at: k)
    }

    private func updateLabels() {
        var best = 0
        for i in 1..<3 where w[i] > w[best] { best = i }
        guard best != active else { return }
        let before = active
        active = best
        for i in 0..<3 {
            if i == best {
                rolls[i] = (0, 1, now)
            } else if i == before {
                rolls[i] = (label(i), 2, now)
            } else {
                rolls[i] = (0, 0, -100)
            }
        }
    }

    // MARK: Script

    private func scriptFrame(_ t: Double, _ R: Double) {
        let tr = T.transitions
        let a = Self.easeInOut(Self.seg(t, tr[0].0, tr[0].1))
        let b = Self.easeInOut(Self.seg(t, tr[1].0, tr[1].1))
        let c = Self.easeInOut(Self.seg(t, tr[2].0, tr[2].1))
        w[0] = 1 - a + c
        w[1] = a - b
        w[2] = b - c
        p1 = 2 * .pi * 2 * t / LiquidMetalMorph.loop
        p2 = 2 * .pi * 3 * t / LiquidMetalMorph.loop
        jiggle = 0
        for k in 0..<3 {
            let tau = t - tr[k].1
            if tau >= 0 && tau < T.jiggleFor {
                let fade = 1 - tau / T.jiggleFor
                jiggle = T.jiggleAmp * sin(2 * .pi * tau / T.jigglePeriod) * fade * fade
            }
        }
        for i in 0..<16 { sats[i] = 0 }
        for k in 0..<3 {
            let (s0, s1) = tr[k]
            if t <= s0 || t >= s1 { continue }
            let s = (t - s0) / (s1 - s0)
            for i in 0..<4 {
                let di = Double(i)
                let dk = Double(k)
                let si = Self.seg(s, T.satStagger * di, 1 - T.satStagger * (3 - di))
                if si <= 0 || si >= 1 { continue }
                let bump = sin(.pi * si)
                let h = Self.hash(di * 7 + dk * 31 + 1)
                let angle = dk * 0.9 + di * (.pi / 2) + 0.42 + (h - 0.5) * 0.7 + 0.6 * si
                let dist = R * (0.9 + T.satOut * bump * (0.75 + 0.5 * h))
                sats[i * 4] = Float(cos(angle) * dist)
                sats[i * 4 + 1] = Float(sin(angle) * dist)
                sats[i * 4 + 2] = Float(R * (0.1 + 0.08 * Self.hash(di * 3 + dk * 11 + 5)) * min(1, bump * 3))
                sats[i * 4 + 3] = Float(T.satK * min(1, bump * 4))
            }
        }
    }

    // MARK: Live

    private func liveFrame(_ dt: Double) {
        for i in 0..<3 {
            let (x, nv) = Self.spring(w[i], v[i], i == target ? 1 : 0, T.shapeW, T.shapeZ, dt)
            w[i] = x
            v[i] = nv
        }
        p1 += 2 * .pi * 2 / LiquidMetalMorph.loop * dt
        p2 += 2 * .pi * 3 / LiquidMetalMorph.loop * dt
        jiggle *= exp(-6 * dt)
        for i in 0..<16 { sats[i] = 0 }
        dropletStep(dt)
    }

    private func dropletStep(_ dt: Double) {
        guard shown else { return }
        let tx = held ? fx : hx
        let ty = held ? fy : hy
        let sw = held ? T.holdW : T.homeW
        let sz = held ? T.holdZ : T.homeZ
        // Substeps keep the stiff spring stable on a slow frame.
        let n = 4
        for _ in 0..<n {
            (dx, dvx) = Self.spring(dx, dvx, tx, sw, sz, dt / Double(n))
            (dy, dvy) = Self.spring(dy, dvy, ty, sw, sz, dt / Double(n))
        }
        if !held {
            let far = hypot(dx - hx, dy - hy)
            if far < 0.6 && hypot(dvx, dvy) < 8 { shown = false }
        }
    }

    /// The held droplet's radius: full at home, thinner as it is pulled away.
    private func dropletRadius(_ R: Double) -> Double {
        let dist = hypot(dx - hx, dy - hy)
        return min(max(0.34 * R - dist * 0.09, 0.13 * R), 0.34 * R)
    }

    private func beginBack() {
        mode = .back
        backAt = now
        backW = w
        backP1 = p1
        backP2 = p2
        held = false
    }

    /// Easing back to the loop's first frame; returns true when done.
    private func backFrame(_ dt: Double) -> Bool {
        let k = Self.easeInOut(min((now - backAt) / T.backFor, 1))
        w[0] = backW[0] + (1 - backW[0]) * k
        w[1] = backW[1] * (1 - k)
        w[2] = backW[2] * (1 - k)
        v = [0, 0, 0]
        p1 = backP1 + Self.angleTo(backP1, 0) * k
        p2 = backP2 + Self.angleTo(backP2, 0) * k
        jiggle *= exp(-6 * dt)
        for i in 0..<16 { sats[i] = 0 }
        dropletStep(dt)
        if k >= 1 {
            shown = false
            jiggle = 0
            target = 0
            return true
        }
        return false
    }

    // MARK: Touch

    private func touched() {
        lastTouch = now
        if mode != .live {
            mode = .live
            v = [0, 0, 0]
            // The chip the morph is closest to becomes the target, so the blob stays where it is.
            var best = 0
            for i in 1..<3 where w[i] > w[best] { best = i }
            target = best
        }
    }

    func choose(_ index: Int) {
        touched()
        target = index
    }

    func dragChanged(_ p: CGPoint, center: CGPoint, R: Double) {
        if !dragging {
            dragging = true
            touched()
            let inside = hypot(p.x - center.x, p.y - center.y) < R * 1.25
            if !inside { return }
            held = true
            shown = true
            detached = false
            hx = p.x
            hy = p.y
            fx = p.x
            fy = p.y
            dx = p.x
            dy = p.y
            dvx = 0
            dvy = 0
            light.prepare()
            return
        }
        lastTouch = now
        guard held else { return }
        fx = p.x
        fy = p.y
        let dist = hypot(p.x - hx, p.y - hy)
        if !detached && dist > T.detachAt {
            detached = true
            light.impactOccurred()
        } else if detached && dist < T.detachAt * 0.7 {
            detached = false
        }
    }

    func dragEnded(velocity: CGSize) {
        if held {
            dvx = velocity.width
            dvy = velocity.height
        }
        held = false
        dragging = false
        lastTouch = now
    }
}
