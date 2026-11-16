import Foundation
import UIKit

/// RN frame-callback ordering and live shared values, owned by the main thread.
@MainActor
final class PrintLineScrubDriver {
    typealias M = PrintLineScrubMotion
    typealias A = PrintLineScrubAnimation
    private var h = A(M.live), duck = A(0), yaw = A(0), heat = A(0), press = A(0), held = A(0)
    private var look = M.Look()
    private var clock = 0.0, loopTime = 0.0, touchedAt = -100.0
    private var previousDate: Date?
    private var previousH = M.live, previousPill: Double?
    private var speed = 0.0, tail = 0.0, gizDir = 1.0
    private var arrived = true, arrivedAt = -100.0
    private var gripH = M.live, tickAt = 0.0, tickLayer = M.layerAt(M.live), atEnd = false
    private var active = true
    private(set) var mode = 0 // loop/rest, finger, released
    private(set) var accessiblePercent = M.percentLive
    private(set) var accessibleLayer = M.layerAt(M.live)
    private let onset = UIImpactFeedbackGenerator(style: .soft)
    private let limit = UIImpactFeedbackGenerator(style: .rigid)
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    var fingerDown: Bool { mode == 1 }

    func reset() {
        look = M.Look(); mode = 0; loopTime = 0; speed = 0; tail = 0; gizDir = 1
        previousDate = nil; previousH = M.live; previousPill = nil
        accessiblePercent = M.percentLive; accessibleLayer = M.layerAt(M.live)
        stopAll()
    }
    func setActive(_ value: Bool) { active = value; previousDate = nil }

    func frame(at date: Date, geometry g: PrintLineScrubGeometry, auto: Bool, reduce: Bool) -> M.Look {
        guard active else { return look }
        let dt = min(max(date.timeIntervalSince(previousDate ?? date.addingTimeInterval(-0.016)), 0), 0.05)
        previousDate = date; clock += dt
        if mode == 0 && auto && !reduce {
            loopTime += dt
            look = M.lookAt(loopTime)
            stopAll()
        } else if mode == 0 {
            look.h = M.live; look.duck = 0; look.yaw = 0; look.heat = 0; look.press = 0; look.held = 0
            stopAll()
        } else {
            look.h = h.sample(clock); look.duck = duck.sample(clock); look.yaw = yaw.sample(clock)
            look.heat = heat.sample(clock); look.press = press.sample(clock); look.held = held.sample(clock)
            if !reduce { look.bead += dt * .pi * 2 / 1.4 }
            if mode == 2 {
                // This test intentionally uses the previous frame's filtered velocity, as RN does.
                if !arrived && abs(look.h - M.live) < 0.004 && abs(speed) < 60 {
                    arrived = true; arrivedAt = clock
                    success.notificationOccurred(.success)
                    accessiblePercent = M.percentLive; accessibleLayer = M.layerAt(M.live)
                }
                if arrived {
                    let tau = clock - arrivedAt
                    look.flash = tau < 0.6 ? tau / 0.6 : -1
                    look.over = tau < 0.7 && !reduce ? 5 * exp(-tau * 7) * cos(tau * 24) : 0
                }
                if arrived && auto && !reduce && clock - touchedAt > 2 && abs(look.h - M.live) < 0.003
                    && look.duck < 0.6 && abs(look.yaw) < 0.01 && look.heat < 0.04 && look.held < 0.02 {
                    mode = 0; loopTime = 0
                }
            }
        }
        let hv = (look.h - previousH) / max(dt, 0.001)
        previousH = look.h
        speed += (hv * g.perY - speed) * (1 - exp(-dt / 0.05))
        let pill = g.pillX(look.h), oldPill = previousPill ?? g.pillX(M.live)
        let pv = (pill - oldPill) / max(dt, 0.001)
        previousPill = pill
        let target = max(-22, min(22, pv / g.scale * 0.03))
        tail += (target - tail) * (1 - exp(-dt / 0.09))
        let wantDir = abs(speed) > 220 && speed < 0 ? -1.0 : 1.0
        gizDir += (wantDir - gizDir) * (1 - exp(-dt / 0.08))
        look.speed = speed; look.tail = tail; look.gizDir = gizDir
        look.band = reduce ? 4 : M.mix(14, 40, M.clamp01(max(look.heat, abs(speed) / 1400)))
        return look
    }

    private func stopAll() {
        h.set(look.h); duck.set(look.duck); yaw.set(look.yaw)
        heat.set(look.heat); press.set(look.press); held.set(look.held)
    }

    func begin(reduce: Bool) {
        stopAll(); mode = 1; touchedAt = clock; arrived = true
        look.flash = -1; look.over = 0
        gripH = look.h; tickAt = clock; tickLayer = M.layerAt(look.h); atEnd = false
        if reduce {
            // Reanimated withTiming/withSpring default to ReduceMotion.System and snap when it is enabled.
            duck.set(0); press.set(1); held.set(1)
        } else {
            duck.start(62, at: clock, curve: .spring(M.duck, 0))
            press.start(1, at: clock, curve: .spring(M.touch, 0))
            held.start(1, at: clock, curve: .timing(0.12, false))
        }
        onset.impactOccurred()
    }

    func move(_ translation: CGSize, geometry g: PrintLineScrubGeometry, reduce: Bool) {
        let raw = gripH - translation.height / g.perY
        var hv = raw, end = false
        if raw < 0 { hv = -M.rubber(-raw * g.perY, 6) / g.perY; end = true }
        else if raw > 1 { hv = 1 + M.rubber((raw - 1) * g.perY, 6) / g.perY; end = true }
        h.set(hv); look.h = hv
        if !reduce { let y = max(-4, min(4, translation.width * 0.03)); yaw.set(y * .pi / 180) }
        let layer = M.layerAt(M.clamp01(hv))
        if layer / 10 != tickLayer / 10 && clock - tickAt > 0.06 {
            selection.selectionChanged(); tickAt = clock; tickLayer = layer; atEnd = end
        } else if end && !atEnd {
            limit.impactOccurred(); atEnd = true
        } else if !end && atEnd { atEnd = false }
    }

    func end(velocityY: Double, geometry g: PrintLineScrubGeometry, reduce: Bool) {
        guard mode == 1 else { return }
        mode = 2; touchedAt = clock; arrived = false
        let currentH = h.sample(clock), dist = abs(currentH - M.live)
        let v = max(-1.5, min(1.5, -velocityY / g.perY))
        let heatNow = min(1, heat.sample(clock) + dist * 1.6)
        heat.set(heatNow)
        let tau = 0.5 * (1 + abs(v * 500) / 1200)
        if reduce {
            h.set(M.live); yaw.set(0); duck.set(0); press.set(0); held.set(0); heat.set(0)
        } else {
            h.start(M.live, at: clock, curve: .spring(M.catchUp, v))
            yaw.start(0, at: clock, curve: .spring(M.yaw, 0))
            duck.start(0, at: clock, curve: .spring(M.duck, 0), delay: 0.7)
            press.start(0, at: clock, curve: .spring(M.touch, 0))
            held.start(0, at: clock, curve: .timing(0.14, false), delay: 0.76)
            heat.start(0, at: clock, curve: .timing(tau * 3, true))
        }
    }

    func accessibilityStep(_ direction: Double, reduce: Bool) {
        stopAll(); mode = 2; touchedAt = clock; arrived = false
        let hh = M.clamp01(look.h + direction * 0.05)
        accessiblePercent = Int(floor(M.percentAt(hh) + 0.5)); accessibleLayer = M.layerAt(hh)
        if reduce {
            h.set(M.live); heat.set(0); duck.set(0); held.set(0)
            return
        }
        h.start(hh, at: clock, curve: .timing(0.2, false))
        h.then(M.live, after: 1.8, curve: .spring(M.catchUp, 0))
        heat.start(min(1, look.heat + 0.3), at: clock, curve: .timing(0.12, false))
        heat.then(0, after: 2.0, curve: .timing(0.9, false))
        duck.start(62, at: clock, curve: .spring(M.duck, 0))
        duck.then(0, after: 2.4, curve: .spring(M.duck, 0))
        held.start(1, at: clock, curve: .timing(0.12, false))
        held.then(0, after: 2.5, curve: .timing(0.14, false))
    }
}
