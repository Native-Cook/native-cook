import Foundation
import UIKit

// Paper Lantern Confirm — the loop's script and the hand-held lantern (a port of lanternMotion.ts).
//
// loop = 9 s:
//   0.0 – 1.2  rest: the lantern stands folded in the sheet with the wish written on it.
//   1.1 – 1.3  "Light and send" is pressed (scale 0.97); its label rolls to "Lighting the wick…".
//   1.2 – 2.0  the wick lights: a spark (to 50 % by 1.35), a dip, then full (ease-out); the paper
//              glows from inside.
//   2.0 – 3.0  it fills: the folds smooth out (ease-out), with a 3 % swell and settle.
//   3.0 – 6.4  it lifts off (a 3 pt dip first) and rises into the sky (ease-in-out), shrinking to a
//              third with distance, swaying 14 pt.
//   5.6 – 6.8  the "Wish sent" toast drops in from the top and leaves; the label reads "Wish sent".
//   6.4 – 8.6  it drifts on, smaller, and fades among the far lanterns (7.6 – 8.6).
//   6.8 – 8.2  a new lantern unfolds in the sheet (from flat, ease-out with a small overshoot);
//              8.2 the label rolls back to "Light and send".
//   8.2 – 9.0  rest: the frame at 9 s is the frame at 0 s.

enum PaperLanternConfirmMotion {
    /// How folded a lantern stands at rest (1 flat … 0 full).
    static let restFold = 0.72
    /// The sheet's height above the bottom inset.
    static let sheetH = 446.0
    /// The lantern's seat: its centre below the sheet's top, and its scale there.
    static let seatY = 190.0
    static let seatScale = 0.8

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

    /// The wick catching: a spark, a dip, then full.
    private static func ignite(_ t: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 1.35 { return 0.5 * easeOut(seg(t, 1.2, 1.35)) }
        if t < 1.45 { return 0.5 - 0.2 * sin(Double.pi * seg(t, 1.35, 1.45) * 0.5) }
        return 0.3 + 0.7 * easeOut(seg(t, 1.45, 2.0))
    }

    static func loopScene(_ t: Double, _ L: PaperLanternConfirmLayout) -> PaperLanternConfirmScene {
        var s = PaperLanternConfirmScene()
        var seat = PaperLanternConfirmLamp()
        var fly = PaperLanternConfirmLamp()
        seat.x = L.seatX
        seat.y = L.seatY
        seat.sx = seatScale
        seat.sy = seatScale
        if t < 3.0 {
            // A, in the sheet: lit, then filled with a small swell.
            let fill = seg(t, 2.0, 3.0)
            let swell = 1 + 0.03 * sin(Double.pi * fill)
            seat.o = 1
            seat.glow = ignite(t)
            seat.fold = restFold * (1 - easeOut(fill))
            seat.sx = seatScale * swell
            seat.sy = seatScale * swell
        } else if t < 6.8 {
            seat.o = 0
        } else {
            // B unfolds from flat.
            let u = seg(t, 6.8, 8.0)
            let grow = u < 0.75 ? 0.15 + 0.89 * easeOut(u / 0.75) : 1.04 - 0.04 * easeOut((u - 0.75) / 0.25)
            seat.o = easeOut(seg(t, 6.8, 7.1))
            seat.fold = restFold + (1 - restFold) * (1 - easeOut(seg(t, 6.9, 8.2)))
            seat.glow = 0
            seat.sy = seatScale * grow
        }
        // The flyer: A after lift-off.
        fly.glow = 1
        fly.fold = 0
        if t < 3.0 || t >= 8.6 {
            fly.o = 0
            fly.x = L.seatX
            fly.y = L.seatY
        } else {
            let dip = 3 * sin(Double.pi * seg(t, 3.0, 3.15))
            let e = easeInOut(seg(t, 3.0, 6.4))
            let drift = seg(t, 6.4, 8.6)
            fly.y = L.seatY + (L.skyY - L.seatY) * e + dip - 40 * sin(drift * Double.pi * 0.5)
            fly.x = L.seatX + 14 * sin(Double.pi * e * 1.5) - 10 * drift
            let k = seatScale * (1 - 0.66 * easeOut(seg(t, 3.0, 6.6))) * (1 - 0.35 * drift)
            fly.sx = k
            fly.sy = k
            fly.o = 1 - easeInOut(seg(t, 7.6, 8.6))
        }
        s.seat = seat
        s.fly = fly
        s.press = sin(Double.pi * seg(t, 1.1, 1.3))
        s.label = t < 1.25 ? 0 : t < 5.6 ? 1 : t < 8.2 ? 2 : 3
        s.toast = easeOut(seg(t, 5.6, 5.95)) * (1 - easeInOut(seg(t, 6.5, 6.8)))
        return s
    }

    /// The next label index at or after `from` whose label (index mod 3) is `state`: labels roll forward.
    static func nextLabel(_ from: Double, _ state: Int) -> Double {
        let base = Int(floor(from + 1e-3))
        for i in 0..<3 where (((base + i) % 3) + 3) % 3 == state { return Double(base + i) }
        return Double(base)
    }

    /// Past `limit`, a pull gives less and less.
    static func rubber(_ x: Double, _ limit: Double) -> Double {
        if x <= limit { return x }
        let over = x - limit
        return limit + over / (1 + over / 60)
    }
}

/// One lantern on screen: centre, scale (x, y), opacity, fold, glow.
struct PaperLanternConfirmLamp {
    var x = 0.0
    var y = 0.0
    var sx = PaperLanternConfirmMotion.seatScale
    var sy = PaperLanternConfirmMotion.seatScale
    var o = 0.0
    var fold = PaperLanternConfirmMotion.restFold
    var glow = 0.0

    static func blend(_ a: Self, _ b: Self, _ e: Double) -> Self {
        func m(_ x: Double, _ y: Double) -> Double { x + (y - x) * e }
        return Self(x: m(a.x, b.x), y: m(a.y, b.y), sx: m(a.sx, b.sx), sy: m(a.sy, b.sy), o: m(a.o, b.o), fold: m(a.fold, b.fold), glow: m(a.glow, b.glow))
    }
}

struct PaperLanternConfirmScene {
    var seat = PaperLanternConfirmLamp()
    var fly = PaperLanternConfirmLamp()
    /// Button press (0 … 1), the label's index (0 Light and send, 1 Lighting, 2 Wish sent), the toast.
    var press = 0.0
    var label = 0
    var toast = 0.0
}

struct PaperLanternConfirmLayout {
    let sheetTop: Double
    let seatX: Double
    let seatY: Double
    let skyY: Double

    init(width: Double, height: Double, insetBottom: Double) {
        sheetTop = height - insetBottom - PaperLanternConfirmMotion.sheetH
        seatX = width / 2
        seatY = sheetTop + PaperLanternConfirmMotion.seatY
        skyY = max(110, sheetTop * 0.42)
    }
}

/// What one frame draws.
struct PaperLanternConfirmFrame {
    var scene: PaperLanternConfirmScene
    /// The sky's and the flames' clock as a share of the loop (it never stops).
    var flicker: Double
    var labelPos: Double
}

/// The loop clock, the hand-held lantern and the hand-off between them, stepped once a frame.
@MainActor
final class PaperLanternConfirmEngine {
    private typealias M = PaperLanternConfirmMotion
    private enum Mode { case auto, user, back }

    private static let idle = 2.0
    private static let returnFor = 0.9
    private static let liftOff = 3.0

    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var skyClock = 0.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var scene = PaperLanternConfirmScene()
    private var from = PaperLanternConfirmLamp()
    private var labelPos = 0.0
    // The lantern under a finger.
    private var glow = 0.0
    private var fold = PaperLanternConfirmMotion.restFold
    private var dy = 0.0
    private var vy = 0.0
    private var targetY = 0.0
    private var holding = false
    // After a launch the flyer starts where the finger let go: dy, at.
    private var launch = (0.0, -10.0)
    private var last: Date?
    private var layout: PaperLanternConfirmLayout?
    /// Whether the drag on the lantern has begun and not ended.
    var fingerDown = false
    private let tapFeel = UIImpactFeedbackGenerator(style: .light)
    private let sentFeel = UINotificationFeedbackGenerator()

    func step(to date: Date, layout L: PaperLanternConfirmLayout, auto: Bool, reduced: Bool) -> PaperLanternConfirmFrame {
        layout = L
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        now += dt
        let at = now
        let loop = PaperLanternConfirm.loop
        skyClock = (skyClock + dt).truncatingRemainder(dividingBy: loop)
        var s: PaperLanternConfirmScene
        if reduced {
            s = M.loopScene(2.99, L)
        } else {
            switch mode {
            case .auto:
                let t = auto || clock > 0 ? (clock + dt).truncatingRemainder(dividingBy: loop) : 0
                // Without autoPlay a send plays once and stops back at rest.
                clock = !auto && clock + dt >= loop ? 0 : t
                s = M.loopScene(clock, L)
                let off = launch.0 * exp(-(at - launch.1) * 4)
                s.fly.y += off
            case .user:
                s = M.loopScene(0, L)
                if holding {
                    glow = min(1, glow + dt / 0.5)
                    if glow > 0.6 { fold = max(0, fold - dt / 1.0) }
                } else {
                    glow = max(0, glow - dt / 1.5)
                    fold = min(M.restFold, fold + dt / 2.5)
                }
                // The lantern follows the finger on a spring (response ~0.35 s, a little bounce).
                let k = 170.0
                let c = 2 * 0.7 * k.squareRoot()
                vy += (k * (targetY - dy) - c * vy) * dt
                dy += vy * dt
                s.seat.glow = glow
                s.seat.fold = fold
                s.seat.y = L.seatY + dy
                s.seat.o = 1
                if !holding && at - lastTouch > Self.idle {
                    from = s.seat
                    returnAt = at
                    mode = .back
                }
            case .back:
                let e = M.easeInOut((at - returnAt) / Self.returnFor)
                s = M.loopScene(0, L)
                s.seat = PaperLanternConfirmLamp.blend(from, s.seat, e)
                if e >= 1 {
                    mode = .auto
                    clock = 0
                }
            }
        }
        scene = s
        // The button's label rolls forward to the scene's label (index mod 3).
        var pos = labelPos
        var target = M.nextLabel(pos, s.label % 3)
        if pos >= 2 && target >= 3 {
            pos -= 3
            target -= 3
        }
        pos = reduced ? target : pos + (target - pos) * min(1, dt * 9)
        if abs(target - pos) < 0.002 { pos = target }
        labelPos = pos
        return PaperLanternConfirmFrame(scene: s, flicker: skyClock / loop, labelPos: pos)
    }

    /// Only a lantern standing in the sheet can be held.
    private func takeOver() -> Bool {
        if mode == .user { return true }
        let s = scene
        guard let L = layout, s.seat.o >= 0.95, s.fly.o <= 0.02 else { return false }
        glow = s.seat.glow
        fold = s.seat.fold
        dy = s.seat.y - L.seatY
        vy = 0
        targetY = 0
        holding = true
        mode = .user
        return true
    }

    func holdBegan() {
        guard takeOver() else { return }
        holding = true
        lastTouch = now
        tapFeel.impactOccurred()
    }

    func holdMoved(_ translationY: Double) {
        guard mode == .user else { return }
        // Up is free (a full lantern pulls a little), down is rubber-banded.
        targetY = -M.rubber(-translationY, 140) * (fold < 0.1 ? 1 : 0.6)
        if translationY > 0 { targetY = M.rubber(translationY, 0) * 0.5 }
        lastTouch = now
    }

    func holdEnded(velocityY: Double) {
        guard mode == .user else { return }
        if fold < 0.12 && glow > 0.9 && (dy < -30 || velocityY < -400) {
            // Away it goes: the loop's rise takes over from where the finger let go.
            launch = (dy, now)
            clock = Self.liftOff
            mode = .auto
            holding = false
            sentFeel.notificationOccurred(.success)
            return
        }
        targetY = 0
        holding = false
        lastTouch = now
    }

    /// "Light and send": plays the send from rest.
    func send() {
        let atRest = (mode == .auto && (clock < 1.1 || clock >= 8.2)) || (mode == .user && glow < 0.05)
        guard atRest else { return }
        launch = (0, -10)
        clock = 1.1
        mode = .auto
        holding = false
        tapFeel.impactOccurred()
    }
}
