import Foundation
import Observation
import SwiftUI

@MainActor @Observable
final class GlassPourMenuDriver {
    typealias M = GlassPourMenuMotion
    private struct Channel {
        var value: Double
        var velocity = 0.0, from = 0.0, to = 0.0, at = 0.0, duration = 0.0, initialVelocity = 0.0
        var last = 0.0, initialEnergy = 0.0
        var spring: M.Spring?
        var running = false
        mutating func hold(_ x: Double) { value = x; velocity = 0; running = false }
        mutating func animate(_ x: Double, at time: Double, spring s: M.Spring, velocity v: Double? = nil) {
            // Reanimated continues a running spring aimed at the same endpoint.
            if running && spring != nil && to == x { return }
            let inherited = running && spring != nil ? velocity : 0
            from = value; to = x; at = time; spring = s
            initialVelocity = inherited + (v ?? 0)
            if (x > value && initialVelocity < 0) || (x < value && initialVelocity > 0) { initialVelocity = 0 }
            velocity = initialVelocity; last = time
            initialEnergy = 0.5 * s.mass * pow(v ?? 0, 2) + 0.5 * s.stiffness * pow(from - to, 2)
            running = true
        }
        mutating func timing(_ x: Double, at time: Double, duration d: Double) {
            from = value; to = x; at = time; duration = d; spring = nil; running = true; velocity = 0
        }
        mutating func tick(_ now: Double) {
            guard running else { return }
            if let s = spring {
                // Reanimated's spring clock caps a stalled frame at 64 ms, independently
                // of the source's 50 ms cap on its procedural life/loop clock.
                let t = min(max(0, now - last), 0.064)
                last = now
                let w = s.omega, z = s.zeta, x0 = value - to, v0 = velocity
                let decay = z * w, wd = w * sqrt(max(0, 1 - z * z))
                if z < 1 {
                    let b = (v0 + decay * x0) / wd
                    let wave = x0 * cos(wd * t) + b * sin(wd * t)
                    value = to + exp(-decay * t) * wave
                    velocity = exp(-decay * t) * (-decay * wave - x0 * wd * sin(wd * t) + b * wd * cos(wd * t))
                } else {
                    let b = v0 + w * x0
                    value = to + exp(-w * t) * (x0 + b * t)
                    velocity = exp(-w * t) * (b - w * (x0 + b * t))
                }
                let energy = 0.5 * s.mass * velocity * velocity + 0.5 * s.stiffness * pow(value - to, 2)
                if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { value = to; velocity = 0; running = false }
            } else {
                let q = M.clamp(max(0, now - at) / duration)
                let k = q < 0.5 ? 2 * q * q : 1 - pow(-2 * q + 2, 2) / 2
                value = M.mix(from, to, k)
                if q >= 1 { value = to; running = false }
            }
        }
    }
    private struct State {
        var p = Channel(value: 0), press = Channel(value: 0), lensY = Channel(value: -27.75)
        var lensA = Channel(value: 0), dwell = Channel(value: 0), flash = Channel(value: 0)
        var fade = Channel(value: 0), satellite = Channel(value: 0)
        var clock = 0.0, animationClock = 0.0, loopT = 0.0, touched = -1e9, pinchAt = -100.0, dipAt = -100.0, dipStrength = 0.0
        var satelliteAt = -100.0, rowAt = 0.0, twist = 0.0, previousP = 0.0
        var selectionAt: Double?, flashOutAt: Double?
        var playing = false, settling = false, auto = false, reduced = false
        var finger = false, mode = 0, start = CGPoint.zero, startP = 0.0, moved = false, latched = false, off = false, row = -1
        var lastDate: Date?
    }
    @ObservationIgnored private var s = State()
    @ObservationIgnored private var geometry = M.Geometry()
    @ObservationIgnored private var items = GlassPourMenuItem.defaults
    @ObservationIgnored private let feedback = GlassPourMenuFeedback()
    @ObservationIgnored private var onSelect: (String) -> Void = { _ in }
    @ObservationIgnored private var onOpenChange: (Bool) -> Void = { _ in }
    @ObservationIgnored private var fadeTask: Task<Void, Never>?
    var menuOpen = false
    var fading = false
    var pinCancelled = false
    var fingerDown: Bool { s.finger }
    var fade: Double { s.fade.value }
    var isAutomatic: Bool { s.playing }

    func configure(geometry: GlassPourMenuMotion.Geometry, items: [GlassPourMenuItem], auto: Bool, reduced: Bool,
                   haptics: Bool, sound: Bool, onSelect: @escaping (String) -> Void,
                   onOpenChange: @escaping (Bool) -> Void) {
        self.geometry = geometry; self.items = items; self.onSelect = onSelect; self.onOpenChange = onOpenChange
        feedback.haptics = haptics; feedback.sound = sound
        if s.auto != auto || s.reduced != reduced {
            s.auto = auto; s.reduced = reduced; s.playing = auto && !reduced; s.loopT = 0
            s.lastDate = nil
        }
    }
    func resetClock() { s.lastDate = nil }
    func frame(at date: Date, pin: Double?) -> GlassPourMenuMotion.Frame {
        let elapsed = max(0, date.timeIntervalSince(s.lastDate ?? date))
        let dt = min(elapsed, 0.05)
        s.lastDate = date; s.clock += dt; s.animationClock += elapsed
        let now = s.clock, animationNow = s.animationClock
        if let pin, !pinCancelled, !s.reduced {
            let f = M.timeline(pin)
            adoptAutomatic(f, dt: dt, pinned: true)
            return f
        }
        if s.playing && !s.reduced {
            s.loopT = (s.loopT + dt).truncatingRemainder(dividingBy: M.loop)
            let f = M.timeline(s.loopT)
            adoptAutomatic(f, dt: dt, pinned: false)
            return f
        }
        s.p.tick(animationNow); s.press.tick(animationNow); s.lensY.tick(animationNow); s.lensA.tick(animationNow)
        s.dwell.tick(animationNow); s.flash.tick(animationNow); s.fade.tick(animationNow); s.satellite.tick(animationNow)
        if let at = s.flashOutAt, animationNow >= at {
            s.flashOutAt = nil; s.flash.timing(0, at: animationNow, duration: 0.23)
        }
        if let at = s.selectionAt, animationNow >= at {
            s.selectionAt = nil
            if !s.reduced { s.p.animate(0, at: animationNow, spring: M.close) }
            else { s.fade.timing(0, at: animationNow, duration: 0.18) }
            s.lensA.timing(0, at: animationNow, duration: 0.16)
        }
        let p = s.p.value
        if (s.previousP < 0.7 && p >= 0.7) || (s.previousP >= 0.7 && p < 0.7) {
            s.pinchAt = now
            if !s.reduced && (s.finger || now - s.touched < 1.2) {
                feedback.cue(p >= 0.7 ? "pinch" : "merge", audio: p >= 0.7 ? "plip" : "merge")
            }
        }
        s.previousP = p
        if now - s.satelliteAt >= 0.22 && now - s.satelliteAt - dt < 0.22 { feedback.cue("grab") }
        if !s.reduced { s.twist = M.mix(s.twist, 8 * .pi / 180 * M.ease(M.clamp(p)), 1 - exp(-dt / 0.07)) }
        if !s.reduced && s.row >= 0 && now - s.rowAt >= 0.25 && s.dwell.value == 0 && !s.dwell.running {
            s.dwell.animate(1, at: animationNow, spring: M.lens)
        }
        if s.auto && !s.reduced && !s.finger && now - s.touched > 2 {
            if !s.settling {
                s.settling = true; s.p.animate(0, at: animationNow, spring: M.glide)
                s.lensA.timing(0, at: animationNow, duration: 0.16)
            } else if abs(p) < 0.001 {
                s.settling = false; s.playing = true; s.loopT = 0
            }
        }
        announce(s.reduced ? s.fade.value : p)
        let event = now - s.pinchAt
        return M.Frame(p: p, press: s.press.value, lensY: s.lensY.value, lensA: s.lensA.value,
                       flash: s.flash.value, dwell: s.dwell.value, time: now,
                       twist: s.reduced ? 0 : s.twist + 1.2 * .pi / 180 * M.impulse(event, M.twist),
                       event: event, dip: s.reduced ? 0 : 9 * s.dipStrength * max(0, M.impulse(now - s.dipAt, M.open)),
                       satelliteAge: now - s.satelliteAt, satelliteProgress: s.satellite.value)
    }
    private func adoptAutomatic(_ f: M.Frame, dt: Double, pinned: Bool) {
        // These live clocks also run under the source's analytic autoplay picture.
        // Keep them warm so a finger interrupt inherits the current twist and recoil.
        if (s.previousP < 0.7 && f.p >= 0.7) || (s.previousP >= 0.7 && f.p < 0.7) { s.pinchAt = s.clock }
        s.previousP = f.p
        let turn = 8 * Double.pi / 180 * M.ease(M.clamp(f.p))
        s.twist = pinned ? turn : M.mix(s.twist, turn, 1 - exp(-dt / 0.07))
        s.p.hold(f.p); s.press.hold(f.press); s.lensY.hold(f.lensY)
        s.lensA.hold(f.lensA); s.flash.hold(f.flash); s.dwell.hold(0)
        announce(f.p)
    }
    private func announce(_ p: Double) {
        let next = menuOpen ? p > 0.9 : p >= 0.999
        if menuOpen != next {
            // TimelineView samples while SwiftUI builds its tree; publish accessibility
            // endpoint changes on the next main turn, never during view evaluation.
            DispatchQueue.main.async { [weak self] in self?.menuOpen = next }
        }
    }
    private func takeOver() {
        s.playing = false; pinCancelled = true; s.settling = false
        s.p.hold(s.p.value); s.fade.hold(s.fade.value); s.lensY.hold(s.lensY.value)
        s.lensA.hold(s.lensA.value); s.flash.hold(0)
        s.selectionAt = nil; s.flashOutAt = nil; s.touched = s.clock
        if s.reduced { s.lastDate = Date() }
    }
    func begin(_ point: CGPoint) {
        takeOver(); s.finger = true; s.start = point; s.startP = s.p.value
        s.moved = false; s.latched = false; s.off = false
        let pv = s.reduced ? (s.fade.value > 0.5 ? 1.0 : 0.0) : s.p.value
        let g = M.glass(M.Frame(p: pv), geometry, reduced: true)
        s.mode = 4
        if hypot(point.x, point.y - g.beadY) < max(g.beadR + 10, 22) { s.mode = pv > 0.5 ? 3 : 1 }
        else if abs(point.x) < g.boxW && abs(point.y - g.boxY) < g.boxH { s.mode = pv > 0.985 ? 2 : 1 }
        s.row = s.mode == 2 ? rowAt(point.y) : -1
        if s.mode == 1 || s.mode == 3 { s.press.animate(1, at: s.animationClock, spring: M.snap); feedback.cue("grab", audio: "tap") }
        if s.row >= 0 { focus(s.row, onset: true) }
    }
    func move(_ point: CGPoint, dragToSelect: Bool) {
        guard s.finger else { return }
        s.touched = s.clock
        let dy = point.y - s.start.y
        s.moved = s.moved || hypot(point.x - s.start.x, dy) > 8
        s.off = abs(point.x) > geometry.half + 40 || point.y < geometry.top || point.y > geometry.bottom
        if !s.reduced && (s.mode == 1 || s.mode == 3) {
            let raw = s.startP + (s.mode == 3 ? -dy : dy) / 175
            let rubber = raw < 0 ? raw / (1 - raw * 6) : raw > 1 ? 1 + (raw - 1) / (1 + (raw - 1) * 6) : raw
            s.p.hold(rubber); s.latched = s.mode == 1 && raw >= 1 && dragToSelect
        }
        if s.mode == 2 || s.latched {
            let row = s.off ? -1 : rowAt(point.y)
            if row != s.row { s.row = row; focus(row) }
        }
    }
    func end(velocityY: Double, cancelled: Bool = false) {
        guard s.finger else { return }
        if cancelled { request(s.p.value > 0.5) }
        else if s.mode == 1 || s.mode == 3 {
            if !s.moved { request(s.mode == 1) }
            else if s.latched && s.row >= 0 { select(s.row) }
            else {
                let v = velocityY * (s.mode == 3 ? -1 : 1) / 175
                request(s.p.value + v * 0.12 > 0.5, velocity: v)
                s.dipAt = s.clock; s.dipStrength = M.clamp(v / 6)
                if !s.reduced && abs(velocityY) > 900 && s.p.value > 0.6 {
                    s.satelliteAt = s.clock; s.satellite.hold(0); s.satellite.animate(1, at: s.animationClock, spring: M.satellite)
                }
            }
        } else if s.mode == 2 && s.row >= 0 { select(s.row) }
        else if s.mode == 4 || s.off { request(false) }
        s.finger = false; s.row = -1; s.touched = s.clock
        s.press.animate(0, at: s.animationClock, spring: M.snap); s.dwell.animate(0, at: s.animationClock, spring: M.lens)
    }
    func activate(_ row: Int) {
        takeOver()
        if row < 0 { request(row == -1) } else { select(row) }
    }
    private func request(_ next: Bool, velocity: Double = 0) {
        onOpenChange(next)
        if s.reduced {
            if (s.fade.value > 0.5) != next { feedback.cue(next ? "pinch" : "merge", audio: next ? "plip" : "merge") }
            fadeTo(next)
        } else {
            var spring = next ? M.open : M.close
            if next { spring.damping = M.mix(22, 14, M.clamp(velocity / 6)) }
            s.p.animate(next ? 1 : 0, at: s.animationClock, spring: spring, velocity: velocity)
        }
        if !next { s.lensA.timing(0, at: s.animationClock, duration: 0.16) }
    }
    private func fadeTo(_ next: Bool) {
        s.fade.timing(next ? 1 : 0, at: s.animationClock, duration: 0.18)
        wakeReduced(for: 0.18)
    }
    /// Reduced Motion has no idle frame clock. Only its explicit small alpha
    /// animations schedule redraws; the fully formed glass endpoints never move.
    private func wakeReduced(for seconds: Double) {
        guard s.reduced else { return }
        if !fading { s.lastDate = Date() }
        fading = true; fadeTask?.cancel()
        fadeTask = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(Int(seconds * 1000))) } catch { return }
            guard let self else { return }
            let now = Date()
            let elapsed = max(0, now.timeIntervalSince(self.s.lastDate ?? now))
            self.s.clock += min(elapsed, 0.05); self.s.animationClock += elapsed
            self.s.lastDate = now
            self.s.fade.tick(self.s.animationClock); self.s.lensA.tick(self.s.animationClock); self.s.flash.tick(self.s.animationClock)
            self.menuOpen = self.menuOpen ? self.s.fade.value > 0.9 : self.s.fade.value >= 0.999
            self.fading = false
        }
    }
    private func select(_ row: Int) {
        guard items.indices.contains(row), !items[row].disabled, s.p.value >= 0.985 || s.reduced else { return }
        onSelect(items[row].key); feedback.cue(items[row].destructive ? "pickDanger" : "pick")
        s.flash.timing(1, at: s.animationClock, duration: 0.12); s.flashOutAt = s.animationClock + 0.12
        request(false)
        // withDelay preserves the already-running close for 300 ms, then replaces it.
        s.selectionAt = s.animationClock + 0.3
        wakeReduced(for: 0.48)
    }
    private func rowAt(_ y: Double) -> Int {
        let i = Int(floor((y - geometry.rows[0]) / geometry.pitch + 0.5))
        return items.indices.contains(i) && !items[i].disabled ? i : -1
    }
    private func focus(_ i: Int, onset: Bool = false) {
        if i >= 0 {
            if s.reduced { s.lensY.hold(geometry.rows[i]) } else { s.lensY.animate(geometry.rows[i], at: s.animationClock, spring: M.lens) }
            s.lensA.timing(1, at: s.animationClock, duration: 0.14); feedback.cue("row", audio: onset ? "tap" : nil)
        } else { s.lensA.timing(0, at: s.animationClock, duration: 0.14) }
        s.dwell.hold(0); s.rowAt = s.clock
        wakeReduced(for: 0.14)
    }
}
