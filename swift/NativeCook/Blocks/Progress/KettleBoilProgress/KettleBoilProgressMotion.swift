import Foundation
import UIKit

// Kettle Boil Progress — the loop's script and the hand-driven kettle (a port of kettleMotion.ts).
//
// loop = 9 s:
//   0.0 – 1.4  rest: still water at 21 °C, the ring dark, "Ready".
//   1.3 – 1.45 the switch is pressed down; the ring lights (0.2 s ease-out).
//   1.4 – 5.2  heat: 21 → 80 °C (ease-in-out), the ring fills with it; micro bubbles from ~30 °C,
//              rising streams from ~50 °C, the surface rolls near the end, steam starts at 4.6.
//   5.2        click: the switch springs back up, the ring flashes and dims (0.6 s).
//   5.2 – 6.8  steam pours from the spout and the lid; the boil settles (bubbles die by 6.8).
//   6.8 – 8.4  cool reset: the steam fades, the temperature rolls back to 21, the ring empties.
//   8.4 – 9.0  rest; the frame at 9 s is the frame at 0 s.
// The status line and the button roll through four states (Ready, Heating, Ready to steep,
// Cooling) in one direction: the index only grows, and 4 shows the same text as 0.

enum KettleBoilProgressMotion {
    static let cold = 21.0
    static let target = 80.0

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

    /// How hard the water works at a temperature while the element is on (0 still … 1 rolling boil).
    static func activityAt(_ temp: Double) -> Double {
        pow(clamp01((temp - 28) / 50), 1.15)
    }

    /// The kettle at loop time t.
    static func loopState(_ t: Double) -> KettleBoilProgressState {
        var s = KettleBoilProgressState()
        let heating = easeInOut(seg(t, 1.4, 5.2))
        let cooling = easeInOut(seg(t, 6.8, 8.4))
        s.temp = cold + (target - cold) * heating * (1 - cooling)
        s.ringFill = (s.temp - cold) / (target - cold)
        // On from the press, a flash at the click, then dim.
        let on = easeOut(seg(t, 1.3, 1.5))
        let click = seg(t, 5.2, 5.8)
        s.ringOn = t < 5.2 ? on : 1.35 * exp(-click * 4) * (1 - click)
        // The boil: follows the temperature while heating, then settles once the element is off.
        let settle = t < 5.2 ? 1 : 1 - easeOut(seg(t, 5.2, 6.8))
        s.heat = t < 5.2 ? activityAt(s.temp) : activityAt(target) * settle
        let steamIn = easeOut(seg(t, 4.6, 5.6))
        s.steam = steamIn * (0.4 + 0.6 * easeOut(seg(t, 5.2, 5.6))) * (1 - easeInOut(seg(t, 6.8, 8.0)))
        // The switch: pressed at 1.3 (0.15 s), back up at the click with a little overshoot.
        let up = seg(t, 5.2, 5.5)
        s.lever = t < 5.2 ? easeOut(seg(t, 1.3, 1.45)) : max(0, 1 - up) - 0.12 * sin(Double.pi * up) * (up < 1 ? 1 : 0)
        s.status = t < 1.4 ? 0 : t < 5.2 ? 1 : t < 6.8 ? 2 : t < 8.4 ? 3 : 4
        return s
    }

    /// The temperature shown: whole degrees, each clicking in over 0.35 of a degree's change.
    static func shownTemp(_ temp: Double) -> Double {
        let whole = floor(temp)
        return whole + easeOut((temp - whole) / 0.35)
    }

    /// The next status index at or after `from` whose state (index mod 4) is `state`: labels only roll forward.
    static func nextStatus(_ from: Double, _ state: Int) -> Double {
        let base = Int(floor(from + 1e-3))
        for i in 0..<4 where (((base + i) % 4) + 4) % 4 == state { return Double(base + i) }
        return Double(base)
    }
}

struct KettleBoilProgressState {
    var temp = KettleBoilProgressMotion.cold
    var heat = 0.0
    var ringFill = 0.0
    var ringOn = 0.0
    var steam = 0.0
    var lever = 0.0
    /// 0 Ready, 1 Heating, 2 Ready to steep, 3 Cooling.
    var status = 0

    /// One step of the kettle under a hand: heats while on and clicks off at 80, cools when off.
    mutating func stepUser(on: Bool, dt: Double) {
        typealias M = KettleBoilProgressMotion
        if on {
            temp = min(M.target, temp + (12 + 4 * (1 - (temp - M.cold) / (M.target - M.cold))) * dt)
        } else {
            temp += -(temp - M.cold) * 0.07 * dt
        }
        ringFill = M.clamp01((temp - M.cold) / (M.target - M.cold))
        let act = on ? M.activityAt(temp) : 0
        heat += (act - heat) * min(1, dt * (on ? 2.5 : 0.9))
        let steamTo = temp > 74 ? M.clamp01((temp - 74) / 6) * (on ? 0.4 : 1) : 0
        steam += (steamTo - steam) * min(1, dt * 1.6)
        ringOn += ((on ? 1 : 0) - ringOn) * min(1, dt * (on ? 8 : 3.5))
        lever += ((on ? 1 : 0) - lever) * min(1, dt * 18)
    }

    static func blend(_ a: Self, _ b: Self, _ e: Double) -> Self {
        func m(_ x: Double, _ y: Double) -> Double { x + (y - x) * e }
        var s = a
        s.temp = m(a.temp, b.temp)
        s.heat = m(a.heat, b.heat)
        s.ringFill = m(a.ringFill, b.ringFill)
        s.ringOn = m(a.ringOn, b.ringOn)
        s.steam = m(a.steam, b.steam)
        s.lever = m(a.lever, b.lever)
        return s
    }
}

struct KettleBoilProgressLayout {
    let ox: Double
    let oy: Double
    let k: Double
    let counter: Double

    init(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        counter = height - insetBottom - 196
        k = max(0.6, min(1.12, (width - 40) / 330, (counter - insetTop - 200) / 300))
        ox = width / 2 + 8 * k
        oy = counter - 52 * k
    }
}

/// What one frame draws.
struct KettleBoilProgressFrame {
    var state: KettleBoilProgressState
    var phase: Double
    var temp: Double
    var statusPos: Double
}

/// The loop clock, the hand-driven kettle and the hand-off between them, stepped once a frame.
@MainActor
final class KettleBoilProgressEngine {
    private typealias M = KettleBoilProgressMotion
    private enum Mode { case auto, user, back }

    private static let idle = 2.0
    private static let returnFor = 0.9

    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var lastTouch = -10.0
    private var offAt = -10.0
    private var returnAt = 0.0
    private var heater = false
    private var phase = 0.0
    private var state = KettleBoilProgressState()
    private var from = KettleBoilProgressState()
    private var statusPos = 0.0
    private var last: Date?
    private let tapFeel = UIImpactFeedbackGenerator(style: .light)
    private let doneFeel = UINotificationFeedbackGenerator()

    func step(to date: Date, auto: Bool, reduced: Bool) -> KettleBoilProgressFrame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        now += dt
        let at = now
        var s = state
        var st = 0
        switch mode {
        case .auto:
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: KettleBoilProgress.loop) : 0
            clock = t
            s = M.loopState(t)
            phase = t / KettleBoilProgress.loop
            st = s.status % 4
        case .user:
            phase = (phase + dt / KettleBoilProgress.loop).truncatingRemainder(dividingBy: 1)
            s.stepUser(on: heater, dt: dt)
            if heater && s.temp >= M.target - 0.01 {
                // The kettle clicks off by itself: a flash of the ring.
                heater = false
                offAt = at
                s.ringOn = 1.35
                doneFeel.notificationOccurred(.success)
            }
            st = heater ? 1 : s.temp > 74 ? 2 : s.temp > 23 ? 3 : 0
            if !heater && at - max(lastTouch, offAt) > Self.idle {
                from = s
                returnAt = at
                mode = .back
            }
        case .back:
            phase = (phase + dt / KettleBoilProgress.loop).truncatingRemainder(dividingBy: 1)
            let e = M.easeInOut((at - returnAt) / Self.returnFor)
            s = KettleBoilProgressState.blend(from, M.loopState(0), e)
            st = e < 1 ? 3 : 0
            if e >= 1 {
                mode = .auto
                clock = 0
            }
        }
        state = s
        let temp = reduced ? s.temp.rounded() : M.shownTemp(s.temp)
        // The labels roll forward to the state's next index; index 4 reads like 0, so wrap there.
        var pos = statusPos
        var target = M.nextStatus(pos, st)
        if pos >= 3 && target >= 4 {
            pos -= 4
            target -= 4
        }
        pos = reduced ? target : pos + (target - pos) * min(1, dt * 9)
        if abs(target - pos) < 0.002 { pos = target }
        statusPos = pos
        return KettleBoilProgressFrame(state: s, phase: phase, temp: temp, statusPos: pos)
    }

    /// A tap anywhere: heat, or cancel while it heats.
    func tap() {
        // Hand the loop's current frame to the hand-driven kettle.
        if mode == .auto {
            let t = clock
            state = M.loopState(t)
            heater = t >= 1.4 && t < 5.2
        } else if mode == .back {
            heater = false
        }
        mode = .user
        lastTouch = now
        let on = heater ? false : state.temp >= M.target - 0.5 ? false : true
        heater = on
        if !on { offAt = now }
        tapFeel.impactOccurred()
    }
}
