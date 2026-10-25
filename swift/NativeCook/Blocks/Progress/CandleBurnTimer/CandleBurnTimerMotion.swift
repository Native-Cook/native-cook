import Foundation
import UIKit

// The candle's state, stepped once a frame from the view's TimelineView and read by the shader and
// the timer digits. A step-for-step port of candleMotion.ts.
//
// `remain` is the session left (0…1 of 25:00) and sets the wax height. autoPlay derives every value
// from the loop time (a compressed 25-minute burn); under a finger the timer runs in real time, the
// flame bends on a spring with the wind of a drag, a hard flick blows it out and a tap relights it.
// All times are seconds on the frame clock.

enum CandleBurnTimerTuning {
    static let session = 25.0 * 60
    /// The stub left at the end, as a share of the full height.
    static let stub = 0.24
    static let burn = (1.4, 6.0)
    static let out = (6.0, 6.35)
    static let smokeAt = 6.05
    static let done = (6.6, 7.4)
    static let rise = (7.4, 8.4)
    static let relight = 8.4
    static let dripFade = (7.4, 7.8)
    /// Two drips in the loop: x offset (share of the half width), birth (s), length (pt).
    static let scriptDrips: [(Double, Double, Double)] = [(-0.36, 2.4, 44), (0.44, 4.2, 32)]
    static let dripRun = 1.2
    static let smokeLife = 2.4
    // Wind: the flame's lean follows the drag's speed (pt/s per unit lean) on a wobbly spring; a
    // flick faster than blowAt blows it out.
    static let windPer = 900.0
    static let bendW = 10.0
    static let bendZ = 0.32
    static let blowAt = 1500.0
    static let idleBeforeBack = 2.0
    static let backFor = 0.9
    static let candleW = 108.0
}

enum CandleBurnTimerMode {
    case auto, live, back
}

enum CandleBurnTimerStatus {
    static let focus = 0
    static let paused = 1
    static let out = 2
    static let done = 3
}

struct CandleBurnTimerFrame {
    var remain = 1.0
    var lit = 1.0
    var bend = 0.0
    var flick = 0.0
    var phase = 0.0
    var smoke = (0.0, 0.0)
    /// x offset (pt from the centre), length (pt), alpha per drip.
    var drips: [(Double, Double, Double)] = [(0, 0, 0), (0, 0, 0)]
    var running = true
    var statusW = [1.0, 0, 0, 0]
}

@MainActor
final class CandleBurnTimerEngine {
    private typealias T = CandleBurnTimerTuning

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    /// A lit flame catching: 0 → 1 with a small overshoot, settled after ~0.5 s.
    private static func ignite(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        return 1 - exp(-9 * tau) * cos(10 * tau)
    }

    /// Flicker of whole-loop sines (−1…1).
    private static func flickerAt(_ t: Double) -> Double {
        let w = 2 * Double.pi / CandleBurnTimer.loop
        return 0.5 * sin(w * 7 * t) + 0.3 * sin(w * 13 * t + 1.3) + 0.2 * sin(w * 23 * t + 2.1)
    }

    private static func spring(_ x: Double, _ v: Double, _ x0: Double, _ w: Double, _ z: Double, _ dt: Double) -> (Double, Double) {
        let nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
        return (x + nv * dt, nv)
    }

    private var configured: (autoPlay: Bool, auto: Bool)?
    private var auto = false
    private(set) var mode: CandleBurnTimerMode = .auto
    private var autoStart: Date?
    private var last: Date?
    private var t = 0.0
    private var now = 0.0
    private var lastTouch = -100.0
    private var remain = 1.0
    private var lit = 1.0
    private var outAt = -100.0
    private var igniteAt = -100.0
    private var bend = 0.0
    private var bendV = 0.0
    private var wind = 0.0
    private var flick = 0.0
    private var phase = 0.0
    private var dx = [0.0, 0]
    private var db = [-100.0, -100]
    private var dl = [0.0, 0]
    private var dripAlpha = 1.0
    private var running = true
    private var statusW = [1.0, 0, 0, 0]
    private var riseAt = -100.0
    private var riseFrom = 1.0
    private var backAt = 0.0
    private var dragging = false
    private let light = UIImpactFeedbackGenerator(style: .light)

    private func configure(autoPlay: Bool, auto: Bool) {
        configured = (autoPlay, auto)
        self.auto = auto
        mode = autoPlay ? .auto : .live
        t = 0
        autoStart = nil
        lastTouch = -100
        remain = 1
        lit = 1
        outAt = -100
        igniteAt = -100
        bend = 0
        bendV = 0
        wind = 0
        flick = 0
        phase = 0
        dx = [0, 0]
        db = [-100, -100]
        dl = [0, 0]
        dripAlpha = 1
        running = true
        statusW = [1, 0, 0, 0]
        riseAt = -100
        riseFrom = 1
    }

    /// The still first frame under reduced motion: the timeline may stay paused.
    var isIdle: Bool {
        mode == .auto && !auto && !dragging
    }

    func step(to date: Date, height: Double, autoPlay: Bool, auto: Bool) -> CandleBurnTimerFrame {
        if configured?.autoPlay != autoPlay || configured?.auto != auto {
            configure(autoPlay: autoPlay, auto: auto)
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        if height > 0 {
            now += dt
            switch mode {
            case .auto:
                if auto {
                    let start = autoStart ?? date
                    autoStart = start
                    t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: CandleBurnTimer.loop)
                }
                scriptFrame(t)
                if !auto {
                    flick = 0
                    bend = 0
                }
            case .live:
                liveFrame(dt)
                if auto && now - lastTouch > T.idleBeforeBack { beginBack() }
            case .back:
                if backFrame(dt) {
                    mode = .auto
                    t = 0
                    autoStart = date
                }
            }
        }
        var f = CandleBurnTimerFrame()
        f.remain = remain
        f.lit = lit
        f.bend = bend
        f.flick = flick
        f.phase = phase
        f.smoke = smokeNow()
        for i in 0..<2 {
            f.drips[i] = (dx[i] * T.candleW * 0.5, dripLength(i), db[i] > -50 ? dripAlpha : 0)
        }
        f.running = running
        f.statusW = statusW
        return f
    }

    private func scriptFrame(_ t: Double) {
        let start = now - t
        let burn = Self.seg(t, T.burn.0, T.burn.1)
        let rise = Self.easeInOut(Self.seg(t, T.rise.0, T.rise.1))
        remain = t < T.rise.0 ? 1 - burn : rise
        if t < T.out.0 {
            lit = 1
        } else if t < T.relight {
            lit = 1 - Self.easeInOut(Self.seg(t, T.out.0, T.out.1))
        } else {
            lit = Self.ignite(t - T.relight)
        }
        outAt = t >= T.smokeAt ? start + T.smokeAt : -100
        igniteAt = start + T.relight
        flick = Self.flickerAt(t)
        let loop = CandleBurnTimer.loop
        phase = 2 * .pi * 6 / loop * t
        bend = 0.08 * sin(2 * .pi * 2 * t / loop) + 0.04 * sin(2 * .pi * 5 * t / loop)
        bendV = 0
        for i in 0..<2 {
            dx[i] = T.scriptDrips[i].0
            db[i] = t >= T.scriptDrips[i].1 ? start + T.scriptDrips[i].1 : -100
            dl[i] = T.scriptDrips[i].2
        }
        dripAlpha = 1 - Self.easeOut(Self.seg(t, T.dripFade.0, T.dripFade.1))
        // Status: focus, then "done" from the moment the flame is out until the new candle rises.
        let done = Self.seg(t, T.out.1, T.done.0) * (1 - Self.seg(t, T.done.1, T.rise.1))
        statusW = [1 - done, 0, 0, done]
        running = true
    }

    /// A drip's length now (pt): it runs down from the rim over dripRun s.
    private func dripLength(_ i: Int) -> Double {
        let age = now - db[i]
        if age < 0 { return 0 }
        return dl[i] * Self.easeOut(min(age / T.dripRun, 1))
    }

    /// Smoke: seconds since the flame went out, and its strength.
    private func smokeNow() -> (Double, Double) {
        let age = now - outAt
        if outAt < -50 || age < 0 { return (0, 0) }
        let fade = 1 - Self.seg(age, T.smokeLife * 0.55, T.smokeLife)
        let relit = lit > 0.05 ? 1 - Self.seg(now - igniteAt, 0, 0.3) : 1
        return (age, fade * max(relit, 0))
    }

    private func setStatus(_ status: Int, _ dt: Double) {
        let k = min(dt / 0.18, 1)
        for i in 0..<4 { statusW[i] += ((i == status ? 1 : 0) - statusW[i]) * k }
    }

    /// Real-time frame under a finger (or with autoPlay off).
    private func liveFrame(_ dt: Double) {
        let lean = max(-1, min(1, wind / T.windPer))
        (bend, bendV) = Self.spring(bend, bendV, lean, T.bendW, T.bendZ, dt)
        wind *= exp(-10 * dt)
        phase += 2 * .pi * 6 / CandleBurnTimer.loop * dt * (1 + abs(bend) * 2)
        flick = Self.flickerAt(now) * (1 + abs(bend) * 1.5)
        // The flame: going out over 0.25 s, catching again with a small overshoot.
        if outAt > igniteAt {
            lit = max(0, 1 - (now - outAt) / 0.25)
        } else {
            lit = igniteAt > -50 ? Self.ignite(now - igniteAt) : 1
        }
        if riseAt > -50 {
            let k = Self.easeInOut(min((now - riseAt) / 0.9, 1))
            remain = riseFrom + (1 - riseFrom) * k
            dripAlpha = min(dripAlpha, 1 - k)
            if k >= 1 {
                riseAt = -100
                db = [-100, -100]
                dripAlpha = 1
            }
        } else if running && lit > 0.5 && remain > 0 {
            remain = max(0, remain - dt / T.session)
        }
        let S = CandleBurnTimerStatus.self
        let status = lit < 0.5 ? S.out : remain <= 0 ? S.done : running ? S.focus : S.paused
        setStatus(status, dt)
    }

    private func blowOut() -> Bool {
        if outAt > igniteAt { return false }
        outAt = now
        return true
    }

    private func relight() -> Bool {
        if outAt <= igniteAt && lit > 0.5 { return false }
        igniteAt = now
        return true
    }

    private func beginBack() {
        mode = .back
        backAt = now
        riseAt = now
        riseFrom = remain
        running = true
        _ = relight()
    }

    /// Back to the loop's first frame: the wax rises, the flame catches; true when done.
    private func backFrame(_ dt: Double) -> Bool {
        wind = 0
        liveFrame(dt)
        // The loop's flicker and turbulence start from their t = 0 values.
        let k = Self.easeInOut(min((now - backAt) / T.backFor, 1))
        phase *= 1 - k
        flick *= 1 - k
        if k >= 1 && riseAt < -50 && now - igniteAt > 0.6 {
            bend = 0
            bendV = 0
            return true
        }
        return false
    }

    // MARK: Touch

    private func touched() {
        lastTouch = now
        if mode != .live { mode = .live }
    }

    func togglePause() {
        touched()
        running.toggle()
    }

    func reset() {
        touched()
        riseAt = now
        riseFrom = remain
        running = true
        _ = relight()
    }

    func dragChanged(location: CGPoint, velocity: CGSize, cx: Double) {
        if !dragging {
            dragging = true
            touched()
            light.prepare()
            return
        }
        lastTouch = now
        // Closer to the flame, stronger wind.
        let near = exp(-max(0, abs(location.x - cx) - 60) / 140)
        wind = velocity.width * near
        if abs(wind) > T.blowAt && lit > 0.5 && blowOut() { light.impactOccurred() }
    }

    func dragEnded(location: CGPoint, translation: CGSize, cx: Double, baseY: Double, full: Double) {
        dragging = false
        lastTouch = now
        let tap = hypot(translation.width, translation.height) < 10
        let topY = baseY - full * (T.stub + (1 - T.stub) * remain)
        let onCandle = abs(location.x - cx) < T.candleW * 0.9 && location.y > topY - 110 && location.y < baseY + 20
        if tap && onCandle && relight() { light.impactOccurred() }
    }
}
