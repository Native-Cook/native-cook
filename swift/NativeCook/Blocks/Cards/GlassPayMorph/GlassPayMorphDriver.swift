import Foundation
import UIKit

/// The approved RN touch driver. Only automatic playback uses an absolute wall-clock epoch;
/// finger-driven springs keep the source's four-step semi-implicit Euler integration.
@MainActor
final class GlassPayMorphDriver {
    private typealias M = GlassPayMorphMotion

    private enum Touch {
        static let scrubTravel = 150.0, rubber = 0.12, rubberFall = 0.55, commitSpeed = 600.0
        static let axisLock = 10.0, yawPerPt = 1.0 / 2.2, yawMax = 28.0, parallax = -0.12
        static let idle = 2.0, driftIn = 0.8, driftOut = 0.35, pressIn = 0.97, bandScale = 0.45
    }
    private struct Spring {
        let k: Double, c: Double
        init(k: Double, c: Double) { self.k = k; self.c = c }
        init(response: Double, ratio: Double) {
            k = pow(2 * .pi / response, 2)
            c = 2 * ratio * sqrt(k)
        }
    }
    private struct State {
        var mode = 0, t = 0.0, gain = 1.0, tau = 0.0, tauV = 0.0
        var play = 0, tauTarget = 0.0, playEnd = 0.0, holdAfter = 0, nu = 1.0
        var yaw = 0.0, yawV = 0.0, yawTarget = 0.0
        var press = 1.0, pressV = 0.0, pressTarget = 1.0, band = 0.0, bandV = 0.0
        var aOff = [0.0, 0, 0, 0], rOff = [0.0, 0, 0, 0]
        var held = 0, last = 0.0
    }
    private struct GestureState {
        var x = 0.0, y = 0.0, p0 = 0.0, onCard = false, lit = 0, p = 0.0
        var moved = 0, tx0 = 0.0, ty0 = 0.0
    }

    private let morphSpring = Spring(k: 170, c: 20)
    private let yawSpring = Spring(response: 0.5, ratio: 0.55)
    private let pressSpring = Spring(response: 0.22, ratio: 0.85)
    private let lightSpring = Spring(response: 0.55, ratio: 0.5)
    private let lightHaptic = UIImpactFeedbackGenerator(style: .light)
    private let mediumHaptic = UIImpactFeedbackGenerator(style: .medium)
    private let successHaptic = UINotificationFeedbackGenerator()
    private let selectionHaptic = UISelectionFeedbackGenerator()
    private var d = State()
    private var gesture = GestureState()
    private var previousFrame: TimeInterval?
    private var autoEpoch: TimeInterval?
    private var autoOffset = 0.0
    private var autoEnabled = false
    private var active = true
    private var inactiveAt: TimeInterval?
    private(set) var fingerDown = false

    init() {}

    func frame(at date: Date, auto: Bool) -> GlassPayMorphMotion.Scene {
        guard active else { return M.sceneAt(time, mods) }
        let now = date.timeIntervalSinceReferenceDate
        let dt = min(max(0, now - (previousFrame ?? now)), 0.05)
        previousFrame = now
        autoEnabled = auto
        synchronizeLoop(at: now, auto: auto)
        let wasTouch = d.mode == 1
        let event = step(dt: dt, now: now)
        if wasTouch && d.mode == 0 {
            autoOffset = 0
            autoEpoch = auto ? now : nil
        }
        if event != 0 { buzz(event) }
        return M.sceneAt(time, mods)
    }

    func begin(_ point: CGPoint, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        synchronizeLoop(at: now, auto: autoEnabled)
        takeOver(at: now)
        let x = Double(point.x), y = Double(point.y)
        let onCard = hitCard(x: x, y: y)
        let lit = onCard ? 0 : hitLight(x: x, y: y)
        let p = M.progressOf(d.tau)
        gesture = GestureState(x: x, y: y, p0: p, onCard: onCard, lit: lit, p: p)
        d.held = lit != 0 ? lit : 5
        fingerDown = true
        if onCard {
            d.pressTarget = Touch.pressIn
            buzz(0)
        }
    }

    /// Translation and velocity are already in the reference screen's point space.
    func move(_ translation: CGSize, velocity: CGSize, at date: Date) {
        guard fingerDown else { return }
        d.last = date.timeIntervalSinceReferenceDate
        let tx = Double(translation.width), ty = Double(translation.height)
        let vx = Double(velocity.width), vy = Double(velocity.height)
        if d.held == 3 || d.held == 4 {
            if d.held == 3 { d.aOff = [tx, ty, vx, vy] }
            else { d.rOff = [tx, ty, vx, vy] }
            return
        }
        if d.held == 5 && hypot(tx, ty) > Touch.axisLock {
            let t = d.tau
            let midExpand = t > M.expand0 && t < M.expand1
            if abs(ty) >= abs(tx) && !midExpand {
                d.held = 1
                let p0 = M.progressOf(t)
                gesture.p0 = p0
                gesture.p = p0
                gesture.moved = 1
                gesture.ty0 = ty
                d.pressTarget = 1
            } else if gesture.onCard && isRest(t) && abs(tx) > abs(ty) {
                d.held = 2
                gesture.moved = 1
                gesture.tx0 = tx
                d.pressTarget = 1
            } else {
                d.held = 6
                gesture.moved = 1
                d.pressTarget = 1
            }
            return
        }
        if d.held == 1 {
            gesture.p = scrubTo(p0: gesture.p0, dy: ty - gesture.ty0)
        } else if d.held == 2 {
            let over = ((tx - gesture.tx0) * Touch.yawPerPt) / Touch.yawMax
            let sign = over < 0 ? -1.0 : 1.0
            let a = abs(over)
            let y = a <= 1 ? a : 1 + (a - 1) / (1 + (a - 1) * 3)
            d.yaw = sign * y * Touch.yawMax
            d.yawV = 0
        }
    }

    func end(velocity: CGSize, at date: Date) {
        guard fingerDown else { return }
        fingerDown = false
        d.last = date.timeIntervalSinceReferenceDate
        d.pressTarget = 1
        let held = d.held
        d.held = 0
        if held == 5 {
            if gesture.onCard { toggleTimeline() }
        } else if held == 1 {
            scrubEnd(p: gesture.p, vy: Double(velocity.height))
        } else if held == 2 {
            d.yawV = Double(velocity.width) * Touch.yawPerPt
        }
    }

    /// Accessibility follows the same takeover and tap path as the RN action.
    func toggle(at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        synchronizeLoop(at: now, auto: autoEnabled)
        takeOver(at: now)
        toggleTimeline()
    }

    func reset(at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        d = State()
        gesture = GestureState()
        fingerDown = false
        previousFrame = now
        autoOffset = 0
        autoEpoch = nil
    }

    /// AppState pauses both the loop and the source's touch/idle clock in the background.
    func setActive(_ active: Bool, at date: Date) {
        guard self.active != active else { return }
        let now = date.timeIntervalSinceReferenceDate
        if active {
            let paused = max(0, now - (inactiveAt ?? now))
            if let epoch = autoEpoch { autoEpoch = epoch + paused }
            d.last += paused
            inactiveAt = nil
        } else {
            synchronizeLoop(at: now, auto: autoEnabled)
            inactiveAt = now
        }
        self.active = active
        previousFrame = now
    }

    private var time: Double { d.mode == 0 ? d.t : d.tau }
    private var mods: M.Mods {
        M.Mods(nu: d.mode == 0 ? 1 : d.nu, driftGain: d.gain, yaw: d.yaw,
               press: d.press * (1 - Touch.bandScale * d.band),
               parallax: Touch.parallax * (d.yaw / Touch.yawPerPt), aOff: d.aOff, rOff: d.rOff)
    }

    private func synchronizeLoop(at now: TimeInterval, auto: Bool) {
        guard d.mode == 0 else { return }
        if auto {
            if autoEpoch == nil {
                autoOffset = d.t
                autoEpoch = now
            }
            d.t = M.wrap(autoOffset + now - (autoEpoch ?? now))
        } else {
            if let epoch = autoEpoch { d.t = M.wrap(autoOffset + now - epoch) }
            autoOffset = d.t
            autoEpoch = nil
        }
    }

    private func takeOver(at now: TimeInterval) {
        if d.mode == 0 {
            d.mode = 1
            d.tau = d.t
            d.tauV = 0
            d.play = 0
            d.tauTarget = d.t
            d.holdAfter = 0
            let t = M.wrap(d.t)
            if t > M.collapse0 && t < M.paidHold {
                d.play = 1
                d.playEnd = M.paidHold
            } else if t > M.expand0 && t < M.expand1 {
                d.play = 1
                d.playEnd = M.expand1
            }
            autoEpoch = nil
        }
        d.last = now
    }

    private func toggleTimeline() {
        let t = M.wrap(d.tau)
        if isRest(t) {
            d.tau = M.collapse0
            d.play = 1
            d.playEnd = M.paidHold
        } else if t >= M.collapse1 && t < M.expand0 {
            d.tau = M.expand0
            d.play = 1
            d.playEnd = M.expand1
        } else if t > M.collapse0 && t < M.collapse1 {
            d.play = 0
            d.tauTarget = M.collapse0
            d.tauV = 0
        }
    }

    private func isRest(_ tau: Double) -> Bool {
        let t = M.wrap(tau)
        return t <= M.collapse0 + 1e-4 || t >= M.expand1 - 1e-4
    }
    private func rubber(_ over: Double, _ limit: Double, _ fall: Double) -> Double {
        over <= 0 ? 0 : (limit * over) / (over + limit / fall)
    }
    private func scrubTo(p0: Double, dy: Double) -> Double {
        var p = p0 - dy / Touch.scrubTravel
        if p < 0 { p = -rubber(-p, Touch.rubber, Touch.rubberFall) }
        else if p > 1 { p = 1 + rubber(p - 1, Touch.rubber, Touch.rubberFall) }
        d.play = 0
        d.tau = M.collapse0 + (M.collapse1 - M.collapse0) * max(0, min(1, p))
        d.tauTarget = d.tau
        d.band = p < 0 ? p : p > 1 ? p - 1 : 0
        d.bandV = 0
        return p
    }
    private func scrubEnd(p: Double, vy: Double) {
        let vp = -vy / Touch.scrubTravel
        let toPill = abs(vy) > Touch.commitSpeed ? vp > 0 : p > 0.5
        d.play = 0
        d.tauTarget = toPill ? M.collapse1 : M.collapse0
        d.tauV = vp * (M.collapse1 - M.collapse0)
        d.holdAfter = toPill ? 1 : 0
    }
    private func springStep(_ x: Double, _ v: Double, _ target: Double, _ s: Spring, _ dt: Double) -> (Double, Double) {
        var xx = x, vv = v
        let h = dt / 4
        for _ in 0..<4 {
            vv += (-s.k * (xx - target) - s.c * vv) * h
            xx += vv * h
        }
        return (xx, vv)
    }

    /// Event ordering, including the source's frame-local half-way check, is unchanged.
    private func step(dt: Double, now: TimeInterval) -> Int {
        if d.mode == 0 {
            d.gain = min(1, d.gain + dt / Touch.driftIn)
            d.nu = 1
            return 0
        }
        let prev = d.tau, held = d.held
        if held != 1 {
            if d.play == 1 {
                let k = min(1, max(0, (d.tau - 0.42) / 0.2))
                d.tau = min(d.playEnd, d.tau + dt * (1 + 1.2 * (1 - k * k * (3 - 2 * k))))
                if d.tau >= d.playEnd {
                    d.play = 0
                    d.tauTarget = d.playEnd
                    d.tauV = 0
                }
            } else {
                let length = M.collapse1 - M.collapse0
                let r = springStep(d.tau / length, d.tauV / length, d.tauTarget / length, morphSpring, dt)
                d.tau = r.0 * length
                d.tauV = r.1 * length
                if d.holdAfter == 1 && abs(d.tau - M.collapse1) < 0.004 && abs(d.tauV) < 0.05 {
                    d.holdAfter = 0
                    d.tau = M.collapse1
                    d.play = 1
                    d.playEnd = M.paidHold
                }
            }
        }
        let speed = dt > 0 ? (d.tau - prev) / dt : 0
        d.nu += (max(-2.5, min(2.5, speed)) - d.nu) * min(1, dt * 30)
        if abs(d.nu) < 0.02 { d.nu = 0 }

        if held != 2 {
            (d.yaw, d.yawV) = springStep(d.yaw, d.yawV, 0, yawSpring, dt)
        }
        if held != 1 {
            (d.band, d.bandV) = springStep(d.band, d.bandV, 0, pressSpring, dt)
        }
        (d.press, d.pressV) = springStep(d.press, d.pressV, d.pressTarget, pressSpring, dt)
        if held != 3 {
            for i in 0..<2 {
                let r = springStep(d.aOff[i], d.aOff[i + 2], 0, lightSpring, dt)
                d.aOff[i] = r.0
                d.aOff[i + 2] = r.1
            }
        }
        if held != 4 {
            for i in 0..<2 {
                let r = springStep(d.rOff[i], d.rOff[i + 2], 0, lightSpring, dt)
                d.rOff[i] = r.0
                d.rOff[i + 2] = r.1
            }
        }
        d.gain = max(0, d.gain - dt / Touch.driftOut)

        if held == 0 && now - d.last > Touch.idle {
            let t = M.wrap(d.tau)
            if d.play == 0 && t > M.collapse0 + 1e-4 && t < M.collapse1 && d.holdAfter == 0 {
                d.tauTarget = M.collapse0
            } else if d.play == 0 && t >= M.collapse1 && t < M.expand0 {
                d.tau = M.expand0
                d.play = 1
                d.playEnd = M.expand1
            }
            let offsets = sqrt(d.aOff[0] * d.aOff[0] + d.aOff[1] * d.aOff[1] + d.rOff[0] * d.rOff[0] + d.rOff[1] * d.rOff[1])
            let settled = isRest(d.tau) && d.play == 0 && abs(d.tauV) < 0.02 && abs(d.yaw) < 0.05
                && abs(d.press - 1) < 0.002 && abs(d.band) < 0.002 && offsets < 0.2
            if settled {
                d.mode = 0
                d.t = 0
                d.gain = 0
                d.yaw = 0
                d.aOff = [0, 0, 0, 0]
                d.rOff = [0, 0, 0, 0]
                d.press = 1
                d.nu = 1
            }
        }
        let a = M.wrap(prev), b = M.wrap(d.tau)
        if a < M.collapse1 && b >= M.collapse1 && b < M.expand0 { return 1 }
        if a < M.pulse && b >= M.pulse && b < M.expand0 { return 2 }
        if held == 1 && (M.progressOf(a) - 0.5) * (M.progressOf(b) - 0.5) < 0 { return 3 }
        return 0
    }

    private func hitCard(x: Double, y: Double) -> Bool {
        let s = M.sceneAt(time, mods), m = M.cardInverse(s)
        let z = m[6] * x + m[7] * y + m[8]
        let lx = (m[0] * x + m[1] * y + m[2]) / z, ly = (m[3] * x + m[4] * y + m[5]) / z
        return abs(lx) < s.w / 2 + 10 && abs(ly) < s.h / 2 + 10
    }
    private func hitLight(x: Double, y: Double) -> Int {
        let s = M.sceneAt(time, mods)
        if hypot(x - s.a[0], y - s.a[1]) < s.a[2] { return 3 }
        if hypot(x - s.rd[0], y - s.rd[1]) < s.rd[2] { return 4 }
        return 0
    }
    private func buzz(_ event: Int) {
        switch event {
        case 1: mediumHaptic.impactOccurred()
        case 2: successHaptic.notificationOccurred(.success)
        case 3: selectionHaptic.selectionChanged()
        default: lightHaptic.impactOccurred()
        }
    }
}
