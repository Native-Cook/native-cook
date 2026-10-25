import Foundation
import UIKit

// Darkroom Tray Develop — the loop's script and the hand-driven tray (a port of darkroomMotion.ts).
//
// loop = 9 s:
//   0.0 – 1.2  rest: a fresh sheet under the developer showing a faint latent image, the liquid
//              swaying gently (twice a loop), the safelight red.
//   1.2 – 5.0  rocking: the tray tips left and right (period 1.25 s, eased in and out over 0.4 s),
//              a wave runs across, the image comes up shadows first (development 0.6 – 4.9);
//              the timer runs 0:00 → 1:30.
//   5.0 – 5.8  stop bath: the liquid turns faintly amber (5.0 – 5.25) and clears into the fixer.
//   5.3        the FIXED stamp lands (scale 1.3 → 1 with a small overshoot, 0.25 s).
//   5.6 – 5.7  the room light flickers on (white), holds to 6.4, and goes back to red (0.15 s).
//   6.6 – 7.7  the print slides out to the right (ease-in-out); 6.9 – 8.1 a fresh sheet slides in
//              from the left (ease-out), stirring a small wave; the timer winds back 7.0 – 7.8;
//              7.1 – 7.9 the fresh sheet's latent image appears while it slides in.
//   8.1 – 9.0  rest. The frame at 9 s is the frame at 0 s.

enum DarkroomTrayDevelopMotion {
    /// Seconds of development the timer shows by the end of the rocking.
    static let devSeconds = 90.0
    static let rockPeriod = 1.25
    /// The faint latent image a sheet shows within 0.6 s of landing in the developer.
    static let latent = 0.14
    /// Whole number of wave crests a loop, so the wave phase repeats.
    static let waves = 7.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return 1 - pow(1 - c, 3)
    }

    /// The rocking envelope: eased in at 1.2 – 1.6 and out at 4.6 – 5.0.
    private static func rockEnvelope(_ t: Double) -> Double {
        easeInOut(seg(t, 1.2, 1.6)) * (1 - easeInOut(seg(t, 4.6, 5.0)))
    }

    /// The loop at time t, for a tray `width` pt wide; `leave` is the development of the print that leaves.
    static func loopFrame(_ t: Double, width: Double, leave: Double) -> DarkroomTrayDevelopFrame {
        let loop = DarkroomTrayDevelop.loop
        var f = DarkroomTrayDevelopFrame()
        let env = rockEnvelope(t)
        // The rocking, plus a gentle idle sway (twice a loop) so the liquid is never still.
        f.rock = 0.75 * env * sin(2 * Double.pi * (t - 1.2) / rockPeriod) + 0.06 * sin(2 * Double.pi * 2 * t / loop)
        f.wphase = 2 * Double.pi * waves * t / loop
        let stir = sin(Double.pi * seg(t, 6.6, 8.3))
        f.wave = 0.12 + 0.68 * env + 0.35 * stir
        let d = seg(t, 0.6, 4.9)
        f.dev = t < 6.4 ? latent + (1 - latent) * pow(d, 1.25) : leave
        // The fresh sheet starts to show its latent image as soon as it is under the developer.
        f.devB = latent * easeOut(seg(t, 7.1, 7.9))
        // The timer runs during the rocking and winds back to 0:00 while the sheets change.
        f.secs = devSeconds * seg(t, 1.2, 5.0) * (1 - easeInOut(seg(t, 7.0, 7.8)))
        f.stopBath = easeOut(seg(t, 5.0, 5.25)) * (1 - easeInOut(seg(t, 5.45, 5.8)))
        // The stamp lands with a small overshoot, then leaves when the light goes back to red.
        f.stamp = seg(t, 5.3, 5.55)
        f.stampOut = easeInOut(seg(t, 6.4, 6.65))
        // The room light: a fluorescent flicker on, then off in 0.15 s.
        var light = 0.0
        if t >= 5.6 && t < 5.64 {
            light = 0.75
        } else if t >= 5.64 && t < 5.67 {
            light = 0.2
        } else if t >= 5.67 && t < 6.4 {
            light = easeOut(seg(t, 5.67, 5.75))
        } else if t >= 6.4 {
            light = 1 - easeOut(seg(t, 6.4, 6.55))
        }
        f.light = light
        let out = easeInOut(seg(t, 6.6, 7.7))
        let inn = easeOut(seg(t, 6.9, 8.1))
        f.slideA = out * width * 1.05
        f.slideB = -(1 - inn) * width * 1.05
        if t < 6.6 { f.slideB = -width * 1.05 }
        f.stage = t < 5.0 ? 0 : t < 5.45 ? 1 : t < 6.6 ? 2 : 0
        return f
    }

    /// The stamp's scale while it lands: 1.3 down to 0.94 (ease-out), back up to 1.
    static func stampScale(_ s: Double) -> Double {
        s < 0.7 ? 1.3 - 0.36 * easeOut(s / 0.7) : 0.94 + 0.06 * easeOut((s - 0.7) / 0.3)
    }

    /// A finger's push past a full tip gives less and less (rubber band).
    static func rubber(_ x: Double) -> Double {
        let a = abs(x)
        if a <= 1 { return x }
        let over = a - 1
        return (x < 0 ? -1 : 1) * (1 + over / (1 + over * 2.2))
    }
}

struct DarkroomTrayDevelopFrame {
    var dev = DarkroomTrayDevelopMotion.latent
    /// The fresh sheet's development (it starts as soon as it lands).
    var devB = 0.0
    var slideA = 0.0
    var slideB = -1000.0
    var rock = 0.0
    var wave = 0.0
    var wphase = 0.0
    var light = 0.0
    var stopBath = 0.0
    /// The stamp's landing (0 … 1, see stampScale) and its exit (0 … 1).
    var stamp = 0.0
    var stampOut = 0.0
    /// Development seconds shown by the timer.
    var secs = 0.0
    /// 0 Develop, 1 Stop, 2 Fix (back to Develop for the fresh sheet).
    var stage = 0.0
}

struct DarkroomTrayDevelopLayout {
    /// x, y, width, height of the tray.
    let tray: (Double, Double, Double, Double)
    /// Centre x, centre y, width, height of a sheet.
    let sheet: (Double, Double, Double, Double)

    init(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        let top = insetTop + 118
        let bottom = height - insetBottom - 16 - 128 - 18
        let tw = width - 32
        let th = max(260, bottom - top)
        let sw = min(tw - 76, (th - 90) * 0.8)
        tray = (16, top, tw, th)
        sheet = (width / 2, top + th / 2, sw, sw * 1.25)
    }
}

/// What one frame shows: the loop's frame plus the smoothed stage position and the rolling timer.
struct DarkroomTrayDevelopShown {
    var frame: DarkroomTrayDevelopFrame
    var stagePos: Double
    var secs: Double
}

/// The loop clock, the hand-driven tray and the hand-off between them, stepped once a frame.
@MainActor
final class DarkroomTrayDevelopEngine {
    private typealias M = DarkroomTrayDevelopMotion
    private static let idle = 2.0
    /// Where the loop picks up after a touch: the stop bath for a finished print, the exchange otherwise.
    private static let resumeFixed = 5.0
    private static let resumeExchange = 6.6

    private var userMode = false
    private var clock = 0.0
    private var now = 0.0
    private var lastTouch = 0.0
    private var holding = false
    private var frame = DarkroomTrayDevelopFrame()
    private var leave = 1.0
    private var last: Date?
    // The hand-driven tray.
    private var target = 0.0
    private var rock = 0.0
    private var rockV = 0.0
    private var dev = 0.0
    private var wave = 0.0
    private var wphase = 0.0
    // What the loop adds back after a touch, fading out: rock, wave, secs, at.
    private var offset = (0.0, 0.0, 0.0, -10.0)
    private var stagePos = 0.0
    private var started = false
    private let select = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    func step(to date: Date, layout L: DarkroomTrayDevelopLayout, auto: Bool, reduced: Bool) -> DarkroomTrayDevelopShown {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        now += dt
        let at = now
        let width = L.tray.2
        var f: DarkroomTrayDevelopFrame
        if reduced {
            f = M.loopFrame(6.0, width: width, leave: 1)
            f.wave = 0
            f.rock = 0
        } else if !userMode {
            let before = clock
            let t = auto ? (before + dt).truncatingRemainder(dividingBy: DarkroomTrayDevelop.loop) : 0
            if t < before { leave = 1 }
            clock = t
            f = M.loopFrame(t, width: width, leave: leave)
            let fade = exp(-(at - offset.3) * 5)
            f.rock += offset.0 * fade
            f.wave += offset.1 * fade
            f.secs += offset.2 * exp(-(at - offset.3) * 3)
        } else {
            // Rocking by hand: a spring toward the finger's tilt, the wave from the speed.
            let k = 90.0
            let c = 2 * 0.62 * k.squareRoot()
            var v = rockV + (k * (target - rock) - c * rockV) * dt
            let r = rock + v * dt
            if abs(v) < 1e-4 { v = 0 }
            rockV = v
            rock = r
            let speed = min(1, abs(v) / 3)
            wave += (min(1, 0.12 + speed) - wave) * min(1, dt * 4)
            wphase = (wphase + dt * (2 * Double.pi * 7 / DarkroomTrayDevelop.loop) * (1 + speed * 1.5)).truncatingRemainder(dividingBy: Double.pi * 200)
            dev = min(1, dev + dt * (0.012 + 0.42 * speed))
            f = M.loopFrame(1.2, width: width, leave: 1)
            f.rock = r
            f.wave = wave
            f.wphase = wphase
            f.dev = dev
            f.secs = dev * M.devSeconds
            let done = dev >= 1
            if done || (!holding && at - lastTouch > Self.idle) {
                // Hand back to the loop where it fits, carrying the tilt, the wave and the timer.
                let t0 = done ? Self.resumeFixed : Self.resumeExchange
                let g = M.loopFrame(t0, width: width, leave: dev)
                offset = (r - g.rock, wave - g.wave, f.secs - g.secs, at)
                leave = dev
                clock = t0
                userMode = false
                if done { success.notificationOccurred(.success) }
            }
        }
        frame = f
        stagePos += (f.stage - stagePos) * min(1, dt * 10)
        let s = max(0, f.secs)
        let whole = floor(s)
        return DarkroomTrayDevelopShown(frame: f, stagePos: stagePos, secs: whole + M.easeOut((s - whole) / 0.3))
    }

    private func takeOver() {
        if userMode { return }
        let f = frame
        // During the exchange the fresh sheet takes the touch, with its latent image.
        rock = f.rock
        rockV = 0
        wave = f.wave
        wphase = f.wphase
        dev = clock >= Self.resumeExchange ? max(M.latent, f.devB) : min(0.99, f.dev)
        userMode = true
    }

    /// The drag gesture's events: down, moved by `translation` from where it landed, up.
    func drag(_ translation: CGSize) {
        let dx = translation.width
        if !holding {
            holding = true
            started = false
            lastTouch = now
        }
        if !started && hypot(translation.width, translation.height) >= 4 {
            started = true
            takeOver()
            select.selectionChanged()
        }
        guard started, userMode else { return }
        target = M.rubber(dx / 110)
        lastTouch = now
    }

    func release() {
        holding = false
        started = false
        target = 0
        lastTouch = now
    }
}
