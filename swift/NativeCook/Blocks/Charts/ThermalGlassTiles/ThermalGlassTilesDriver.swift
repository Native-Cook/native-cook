import Observation
import SwiftUI
import UIKit

struct ThermalGlassTilesFrameState {
    var time = 0.0, progress = 1.0, fieldTime = 0.0, phase = 0.72
    var direction = 1.0, peak = 0.0, circlePeak = 0.0, smallPeak = 0.0, bpmScale = 1.0, ring = 4.0
    var touch = SIMD3<Double>(0, 0, -1)
    var sample = ThermalGlassTilesMotion.sample(1)
    var thermalEffort = ThermalGlassTilesMotion.sample(1).effort
    var numbers: [Double] = [2960, 270, 100]
}

@Observable @MainActor
final class ThermalGlassTilesDriver {
    typealias M = ThermalGlassTilesMotion
    var awake = false
    var revision = 0
    var pinDisabled = false
    @ObservationIgnored private var state = State()
    @ObservationIgnored private let onsetHaptic = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private let selectionHaptic = UISelectionFeedbackGenerator()
    @ObservationIgnored private let peakHaptic = UIImpactFeedbackGenerator(style: .rigid)

    private struct Roll {
        var current = -1.0, from = -1.0, target = -1.0, started = 0.0
        mutating func value(_ q: Double, at clock: Double, reduced: Bool, snap: Bool) -> Double {
            if reduced || snap || current < 0 { current = q; from = q; target = q; return q }
            let t = ThermalGlassTilesMotion.clamp((clock - started) / 0.18)
            current = ThermalGlassTilesMotion.mix(from, target, 1 - pow(1 - t, 3))
            if target != q { from = current; target = q; started = clock }
            return current
        }
    }
    private struct State {
        var epoch: Date?
        var lastFrame: Date?
        var clock = 0.0, time = 0.0, progress = 1.0, velocity = 0.0
        var playing = false, initialized = false, held = false, routeGrab = false, returning = false
        var coasting = false, coastOrigin = 1.0, coastVelocity = 0.0, releasedAt = 0.0
        var startX = 0.0, startP = 1.0, lastTouch = -1.0, peakStart = -100.0, peakArmed = true
        var lastTick = -100.0, lastDistance = 29.6, touchStart = -100.0, touchPosition = CGPoint.zero
        var spring: M.Spring?
        var ringSpring: M.Spring?
        var ringBase = 4.0, returnStart = 0.0, returnTime = 0.0
        var thermal = M.sample(1).effort, thermalFrom = M.sample(1).effort, thermalTarget = M.sample(1).effort, thermalAt = 0.0
        var rolls = [Roll(), Roll(), Roll()]
    }
    var fingerDown: Bool { state.held }

    func configure(auto: Bool, reduced: Bool) {
        state = State(); state.playing = auto && !reduced
        pinDisabled = false
        awake = auto && !reduced; revision += 1
    }
    func resume() { state.epoch = nil; state.lastFrame = nil }

    func frame(at date: Date, auto: Bool, reduced: Bool, pin: Double?, miles: Bool) -> ThermalGlassTilesFrameState {
        if !state.initialized { state.playing = auto && !reduced; state.initialized = true }
        let dt = awake ? max(0, state.lastFrame.map { date.timeIntervalSince($0) } ?? 1 / 60) : 0
        state.lastFrame = date; state.clock += dt
        let pinned = pinDisabled ? nil : pin
        if let pin = pinned {
            state.time = M.wrap(pin); state.progress = M.progress(pin)
        } else if state.playing && !reduced {
            if state.epoch == nil { state.epoch = date.addingTimeInterval(-state.time) }
            state.time = M.wrap(date.timeIntervalSince(state.epoch!)); state.progress = M.progress(state.time)
        } else if !reduced && awake {
            if state.coasting && !state.held && !state.returning {
                let c = M.coast(state.coastOrigin, state.coastVelocity, state.clock - state.releasedAt)
                state.progress = M.rubber(c.position); state.velocity = c.velocity
                if abs(c.velocity) < 0.001 || state.clock - state.releasedAt > 1.2 {
                    state.coasting = false
                    state.spring = M.Spring(state.progress, to: M.clamp(c.position), damping: 30.5, stiffness: 322.3, velocity: c.velocity)
                }
            }
            if !state.returning { state.time = M.wrap(state.time + dt) }
            if !state.held && state.lastTouch >= 0 && !state.returning && state.clock - state.lastTouch >= 3 {
                state.coasting = false; state.returning = true
                state.returnStart = state.clock; state.returnTime = state.time
                state.spring = M.Spring(state.progress, to: 1, damping: 24, stiffness: 70, mass: 1.2,
                                        inheritedVelocity: state.spring?.velocity ?? 0)
            }
            if state.returning {
                let k = M.clamp((state.clock - state.returnStart) / 0.6)
                let e = k < 0.5 ? 2 * k * k : 1 - pow(-2 * k + 2, 2) / 2
                state.time = M.mix(state.returnTime, 0, e)
            }
            if var spring = state.spring {
                let done = spring.step(dt); state.progress = spring.value; state.spring = done ? nil : spring
                if done && state.returning {
                    state.returning = false; state.lastTouch = -1; state.time = 0; state.epoch = nil
                    state.playing = auto; if !auto { awake = false }
                }
            }
        }
        if var spring = state.ringSpring {
            let done = spring.step(dt); state.ringBase = spring.value; state.ringSpring = done ? nil : spring
        }
        let sample = M.sample(state.progress), e = sample.effort
        if e < 0.6 { state.peakArmed = true }
        if e >= 0.8 && state.peakArmed {
            state.peakArmed = false; state.peakStart = state.clock
            if !state.playing && !state.returning && pinned == nil { peakHaptic.impactOccurred() }
        }
        if !state.playing && pinned == nil && (state.held || state.coasting) &&
            floor(sample.distance) != floor(state.lastDistance) && state.clock - state.lastTick >= 0.1 {
            state.lastTick = state.clock; selectionHaptic.selectionChanged()
        }
        state.lastDistance = sample.distance
        let automatic = state.playing || pinned != nil
        let age = state.clock - state.peakStart, t = state.time
        var f = ThermalGlassTilesFrameState()
        f.time = t; f.progress = state.progress; f.sample = sample
        f.fieldTime = reduced ? 0 : (automatic ? M.sceneTime(t) : t)
        f.phase = M.stride(state.progress, t, reduced: reduced)
        if automatic {
            let delta = M.progress(t + 1 / 120) - M.progress(t - 1 / 120)
            f.direction = delta == 0 ? 1 : (delta < 0 ? -1 : 1)
        } else { f.direction = state.velocity == 0 ? 1 : (state.velocity < 0 ? -1 : 1) }
        if !reduced {
            f.peak = automatic ? M.peak(t) : M.smooth(0, 0.08, age) * (1 - M.smooth(0.55, 0.7, age))
            f.circlePeak = automatic ? M.peak(t - 0.08) : M.smooth(0.08, 0.16, age) * (1 - M.smooth(0.63, 0.78, age))
            f.smallPeak = automatic ? M.peak(t - 0.16) : M.smooth(0.16, 0.24, age) * (1 - M.smooth(0.71, 0.86, age))
            f.bpmScale = M.bpmPulse(automatic ? t - M.peakStart : age)
            if state.touchStart > -100 {
                f.touch = SIMD3(state.touchPosition.x, state.touchPosition.y, state.held ? 0 : state.clock - state.touchStart)
            }
        }
        f.ring = state.ringBase + (reduced || state.held ? 0 : 2 * pow(max(0, sin(t * .pi / 2)), 8))
        // The source uses a 200 ms default inOut(quad) fade of effort only under Reduce Motion.
        if reduced {
            let k = M.clamp((state.clock - state.thermalAt) / 0.2)
            let eased = k < 0.5 ? 2 * k * k : 1 - pow(-2 * k + 2, 2) / 2
            state.thermal = M.mix(state.thermalFrom, state.thermalTarget, eased)
            if state.thermalTarget != e {
                state.thermalFrom = state.thermal; state.thermalTarget = e; state.thermalAt = state.clock
            }
        } else { state.thermal = e }
        f.thermalEffort = state.thermal
        let targets = [floor(sample.distance * (miles ? 0.621371 : 1) * 100 + 0.5), floor(sample.contact + 0.5), floor(sample.oscillation * 10 + 0.5)]
        for i in 0..<3 { f.numbers[i] = state.rolls[i].value(targets[i], at: state.clock, reduced: reduced, snap: pinned != nil) }
        if reduced && !state.held && abs(state.thermal - e) < 0.00001 { awake = false }
        return f
    }

    private func nearest(_ point: CGPoint, p: Double) -> (p: Double, distance: Double) {
        let points = ThermalGlassTilesRoute.points
        let lo = max(0, Int(floor((p - 0.12) * 255))), hi = min(255, Int(ceil((p + 0.12) * 255)))
        var best = p, d = 1e10
        if lo <= hi {
            for i in lo...hi {
                let v = pow(points[i].x - point.x, 2) + pow(points[i].y - point.y, 2)
                if v < d { best = Double(i) / 255; d = v }
            }
        }
        return (best, sqrt(d))
    }
    func begin(_ p: CGPoint, at date: Date, reduced: Bool) {
        pinDisabled = true; state.playing = false; state.returning = false; state.coasting = false
        state.spring = nil; state.held = true; state.velocity = 0
        state.startX = p.x; state.startP = state.progress; state.lastTouch = state.clock
        let near = nearest(p, p: state.progress); state.routeGrab = near.distance <= 28
        if state.routeGrab { state.progress = near.p }
        state.touchPosition = p; state.touchStart = state.clock
        setRing(6, reduced: reduced)
        state.lastFrame = date; awake = true; revision += 1; onsetHaptic.impactOccurred()
    }
    func move(_ p: CGPoint, velocity: CGSize, reduced: Bool) {
        let next = state.routeGrab ? nearest(p, p: state.progress).p : state.startP + (p.x - state.startX) / 300
        state.progress = M.rubber(next)
        if state.routeGrab {
            let points = ThermalGlassTilesRoute.points, i = Int(floor(M.clamp(state.progress) * 255 + 0.5))
            let a = points[max(0, i - 1)], b = points[min(255, i + 1)], len = max(0.001, hypot(b.x - a.x, b.y - a.y))
            state.velocity = (velocity.width * (b.x - a.x) + velocity.height * (b.y - a.y)) / (len * ThermalGlassTilesRoute.length)
        } else { state.velocity = velocity.width / 300 }
        state.touchPosition = p; state.lastTouch = state.clock
        revision += 1
    }
    func end(reduced: Bool, cancelled: Bool = false) {
        if !reduced && !cancelled {
            state.coastOrigin = state.progress; state.coastVelocity = state.velocity
            state.releasedAt = state.clock; state.coasting = true
        } else if reduced { state.progress = M.clamp(state.progress) }
        state.held = false; state.lastTouch = state.clock; state.touchStart = state.clock
        setRing(4, reduced: reduced); revision += 1
    }
    private func setRing(_ target: Double, reduced: Bool) {
        if reduced { state.ringBase = target; state.ringSpring = nil }
        else { state.ringSpring = M.Spring(state.ringBase, to: target, damping: 18, stiffness: 380, mass: 0.8,
                                         inheritedVelocity: state.ringSpring?.velocity ?? 0) }
    }
    func warm(_ p: CGPoint) {
        state.touchPosition = p; state.touchStart = state.clock
        awake = true; revision += 1
    }
    func shortcut(_ target: Double, reduced: Bool) {
        let inheritedVelocity = state.spring?.velocity ?? 0
        pinDisabled = true; state.playing = false; state.returning = false; state.coasting = false
        state.lastTouch = state.clock
        if reduced { state.progress = target; state.spring = nil }
        else { state.spring = M.Spring(state.progress, to: target, damping: 20, stiffness: 220, inheritedVelocity: inheritedVelocity) }
        awake = true; revision += 1; onsetHaptic.impactOccurred()
    }
    func adjust(_ delta: Double, reduced: Bool) { shortcut(M.clamp(state.progress + delta / 29.6), reduced: reduced) }
}
