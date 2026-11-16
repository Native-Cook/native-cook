import SwiftUI
import UIKit

/// Interruptible RN gesture tracks. Autoplay reads only the analytic loop; no cues during autoplay.
@MainActor
final class BeamShapeTorchDriver {
    typealias M = BeamShapeTorchMotion
    private struct Track {
        var from: Double, to: Double, start: Double, duration = 0.0, stiffness = 0.0, damping = 0.0, velocity = 0.0
        var curve: [Double] = [0.25, 0.1, 0.25, 1]
        func sample(_ now: Double) -> Double {
            let t = max(now - start, 0)
            if now < start { return from }
            if stiffness == 0 {
                if duration <= 0 { return to }
                let x = M.clamp01(t / duration)
                let k = curve.isEmpty ? (x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2) : M.bezier(curve[0], curve[1], curve[2], curve[3], x)
                return M.mix(from, to, k)
            }
            let omega = sqrt(stiffness), decay = damping / 2, z = decay / omega, x0 = from - to
            let value: Double
            if z < 1 {
                let wd = sqrt(stiffness - decay * decay)
                value = exp(-decay * t) * (x0 * cos(wd * t) + (velocity + decay * x0) / wd * sin(wd * t))
            } else {
                value = exp(-omega * t) * (x0 + (velocity + omega * x0) * t)
            }
            // Reanimated's relative energy threshold; makes an at-rest track exact.
            let initialEnergy = max(stiffness * x0 * x0 + velocity * velocity, 1e-12)
            let v = speed(now)
            if t > 0 && (stiffness * value * value + v * v) / initialEnergy < 6e-9 { return to }
            return to + value
        }
        func speed(_ now: Double) -> Double {
            guard now >= start, stiffness > 0 else { return 0 }
            let t = now - start, omega = sqrt(stiffness), decay = damping / 2, x = from - to
            if decay < omega {
                let wd = sqrt(stiffness - decay * decay), b = (velocity + decay * x) / wd
                return exp(-decay * t) * ((b * wd - decay * x) * cos(wd * t) - (x * wd + decay * b) * sin(wd * t))
            }
            let b = velocity + omega * x
            return exp(-omega * t) * (b - omega * (x + b * t))
        }
    }
    private struct Grip {
        var region = 0, axis = 0
        var x0 = 0.0, y0 = 0.0, t0 = 0.0, b0 = 1.0, w0 = 1.0, d0 = 0.0, moved = 0.0, rawB = 1.0
        var pinch = false, wp0 = 1.0, handle = false, glide = false
        var tickB = 10, tickT = 8, tickAt = -1.0, wasOff = false
        var degPrev = 0.0, degAt = -1.0, degSpeed = 0.0
    }
    private var tracks: [String: Track] = [:]
    private var grip = Grip()
    private var values: [String: Double] = ["b": 1, "w": 1, "m": 1, "mw": 1, "press": 1, "sun": 0]
    private var hot = 1.0, after = 0.0, dwell = 0.0, afterAt: Double?
    private var previousTime: Double?, previousB = 1.0, previousW = 1.0, velocityB = 0.0, velocityW = 0.0
    private var clock = 0.0, loopTime = 0.0
    private var touchedAt = -1e9, focusAt: Double?, resuming = false, playing = false, open = true, lastOn = 1.0, ceilingOn = false
    private var fx = 0.0, fy = 0.0
    private var accessibilityB = 1.0, accessibilityW = 1.0
    private(set) var accessibilityExpanded = true
    private var reducedChangeAt: Double?, reducedOpen = true
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let selection = UISelectionFeedbackGenerator()
    private(set) var look = BeamShapeTorchMotion.Look()
    private(set) var sunGrab = 0.0, fade = 1.0, fingerDown = false
    private var started = false, reduce = false, auto = false

    func configure(auto: Bool, reduced: Bool, at date: Date) {
        let wasReduced = reduce
        if self.auto != auto { playing = auto && !reduced; loopTime = 0 }
        self.auto = auto; reduce = reduced
        if wasReduced && !reduced { playing = auto; loopTime = 0 }
        if !started { started = true; previousTime = date.timeIntervalSinceReferenceDate; playing = auto && !reduced }
        if reduced {
            // Like RN's own live values, the manual setting survives the preference changing.
            playing = false; after = 0; afterAt = nil
            look = M.Look(b: val("b"), w: val("w"), m: val("m"), mw: val("mw"),
                          press: val("press"), hot: val("b"))
            hot = val("b")
        }
    }
    func resumeClock(at date: Date) { previousTime = date.timeIntervalSinceReferenceDate }
    func frame(at date: Date, layout: BeamShapeTorchLayout, pin: Double?) -> BeamShapeTorchMotion.Look {
        let now = date.timeIntervalSinceReferenceDate
        let dt = min(max(now - (previousTime ?? now), 0), 0.05); previousTime = now
        clock += dt
        if let pin { look = M.lookAt(pin); return look }
        if playing {
            loopTime = (loopTime + dt).truncatingRemainder(dividingBy: 9)
            look = M.lookAt(loopTime); return look
        }
        for (key, track) in tracks { values[key] = track.sample(now) }
        if let start = reducedChangeAt {
            let t = now - start
            fade = t < 0.1 ? 1 - quad(t / 0.1) : quad((t - 0.1) / 0.1)
            if t >= 0.1 { values["m"] = reducedOpen ? 1 : 0; values["mw"] = reducedOpen ? 1 : 0 }
            if t >= 0.2 { fade = 1; reducedChangeAt = nil }
        }
        if fingerDown && grip.handle {
            let s = M.mix(0.55, 1, min(val("m"), val("mw"))) * val("press")
            let sol = M.solveSun(abs(fx - layout.cx) / (layout.scale * s), (layout.top + (layout.lensY - layout.top) * s - fy) / (layout.scale * s), grip.b0, grip.w0)
            let x = grip.glide ? min((now - grip.t0) / 0.12, 1) : 1
            let e = x >= 1 ? 1 : M.springAt(x * 0.12, 0.35, 0.85) / M.springAt(0.12, 0.35, 0.85)
            grip.rawB = M.mix(grip.b0, sol.b, e)
            values["b"] = M.rubber(grip.rawB); values["w"] = M.rubber(M.mix(grip.w0, sol.w, e))
            tick(at: now); touchedAt = now
        }
        let b = val("b"), w = val("w"), bc = M.clamp01(b), wc = M.clamp01(w)
        hot += (b - hot) * (1 - exp(-dt / (b > hot ? 0.06 : 0.14)))
        velocityB = velocityB * 0.6 + 0.4 * ((b - previousB) / max(dt, 0.001))
        velocityW = velocityW * 0.6 + 0.4 * ((w - previousW) / max(dt, 0.001)); previousB = b; previousW = w
        if let due = focusAt, now >= due { focusAt = nil; if !fingerDown && !reduce { rigid.impactOccurred() } }
        if !reduce {
            if wc < 0.12 && bc > 0.5 { dwell += dt }
            else { if dwell >= 1.2 && bc > 0.3 { afterAt = now }; dwell = 0 }
            if let start = afterAt {
                let t = now - start
                after = t < 0.08 ? quad(t / 0.08) : pow(1 - M.clamp01((t - 0.08) / 0.9), 2)
                if t >= 0.98 { afterAt = nil; after = 0 }
            }
            // RN reads its previous derived look here, before publishing the new one.
            let reach = M.opticsOf(look.b, look.w).reach
            if !ceilingOn && reach >= 205 && bc > 0.9 && wc < 0.1 { ceilingOn = true; rigid.impactOccurred() }
            else if ceilingOn && (reach < 190 || wc > 0.15) { ceilingOn = false }
        }
        if abs(hot - b) < 1e-4 { hot = b }
        let shimmer = !reduce && bc > 0.01 && 1 - wc > 0.02 && (fingerDown || now - touchedAt < 2)
        look = M.Look(b: b, w: w, m: val("m"), mw: val("mw"), press: val("press"), hot: reduce ? b : hot,
                      phase: shimmer ? (clock.truncatingRemainder(dividingBy: 9) / 9) : look.phase, after: reduce ? 0 : after)
        sunGrab = val("sun")
        if auto && !reduce && !fingerDown && now - touchedAt >= 2 {
            if !resuming {
                resuming = true; open = true
                for key in ["b", "w", "m", "mw", "press"] { timing(key, 1, 0.6, [0.65, 0, 0.35, 1], now) }
            } else if abs(b - 1) < 0.002 && abs(w - 1) < 0.002 && abs(val("m") - 1) < 0.002 && abs(val("mw") - 1) < 0.002 && abs(hot - 1) < 0.01 {
                resuming = false; loopTime = 0; playing = true
            }
        }
        return look
    }
    private func quad(_ x: Double) -> Double {
        let t = M.clamp01(x); return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
    private func val(_ key: String) -> Double { values[key] ?? 0 }
    private func takeOver(_ now: Double) {
        resuming = false; tracks.removeAll()
        if playing {
            playing = false
            values = ["b": look.b, "w": look.w, "m": look.m, "mw": look.mw, "press": look.press, "sun": sunGrab]
            hot = look.hot; open = look.m > 0.5; previousB = look.b; previousW = look.w
        }
        touchedAt = now
    }
    private func timing(_ key: String, _ to: Double, _ duration: Double, _ curve: [Double], _ now: Double, delay: Double = 0) {
        values[key] = tracks[key]?.sample(now) ?? val(key)
        tracks[key] = Track(from: val(key), to: to, start: now + delay, duration: duration, curve: curve)
    }
    private func spring(_ key: String, _ to: Double, _ stiffness: Double, _ damping: Double, _ now: Double, velocity: Double = 0, delay: Double = 0) {
        let previous = tracks[key]
        let inherited: Double
        if let previous, previous.stiffness > 0 {
            inherited = previous.sample(now) == previous.to ? 0 : previous.speed(now)
        } else { inherited = 0 }
        values[key] = previous?.sample(now) ?? val(key)
        tracks[key] = Track(from: val(key), to: to, start: now + delay, stiffness: stiffness, damping: damping, velocity: velocity + inherited)
    }
    private func response(_ key: String, _ to: Double, _ r: Double, _ z: Double, _ now: Double, delay: Double = 0) {
        let k = pow(2 * Double.pi / r, 2); spring(key, to, k, 2 * z * sqrt(k), now, delay: delay)
    }
    func begin(_ point: CGPoint, at date: Date, layout l: BeamShapeTorchLayout) {
        let now = date.timeIntervalSinceReferenceDate, rect = l.rect(look)
        focusAt = nil; fingerDown = true; touchedAt = now
        let inCap = rect.insetBy(dx: -6 * l.scale, dy: -6 * l.scale).contains(point)
        let inIsland = l.island.insetBy(dx: -14, dy: -14).contains(point)
        let region = look.m > 0.9 && inCap ? 1 : (look.m <= 0.9 && inIsland ? 2 : 3)
        grip = Grip(); grip.region = region; grip.x0 = point.x; grip.y0 = point.y; grip.t0 = now
        guard region == 1 else { return }
        takeOver(now)
        let b = val("b"), w = val("w"), deg = M.opticsOf(b, w).theta * 180 / .pi
        grip.b0 = b; grip.w0 = w; grip.rawB = b; grip.d0 = abs(point.x - l.cx)
        grip.tickB = Int(floor(M.clamp01(b) * 10 + 1e-4)); grip.tickT = Int(floor(deg / 5)); grip.wasOff = b < 0.01; grip.degPrev = deg
        let s = l.innerScale(look), so = M.sunOffset(M.clamp01(b), M.clamp01(w))
        let sx = l.cx + so.x * l.scale * s, sy = l.top + (l.lensY - l.top) * s - so.y * l.scale * s
        let away = hypot(point.x - sx, point.y - sy)
        if away <= 22 * l.scale {
            grip.handle = true; grip.glide = away > 12 * l.scale; fx = point.x; fy = point.y
            if !reduce { spring("sun", 1, 322.3, 30.5, now) }
        }
        if !reduce { spring("press", 1.012, 322.3, 30.5, now) }
        soft.impactOccurred()
    }
    func move(_ point: CGPoint, translation: CGSize, at date: Date, layout l: BeamShapeTorchLayout) {
        let now = date.timeIntervalSinceReferenceDate, dist = hypot(translation.width, translation.height)
        grip.moved = max(grip.moved, dist)
        guard grip.region == 1 else { return }
        if grip.handle { fx = point.x; fy = point.y; return }
        if grip.axis == 0 && dist > 10 * l.scale { grip.axis = abs(translation.width) > abs(translation.height) ? 1 : 2 }
        let gy = grip.axis == 1 ? 0.6 : 1, gx = grip.axis == 2 ? 0.6 : 1
        grip.rawB = grip.b0 - translation.height * gy / (220 * l.scale)
        values["b"] = M.rubber(grip.rawB); tracks["b"] = nil
        if !grip.pinch { values["w"] = M.rubber(grip.w0 + (abs(point.x - l.cx) - grip.d0) * gx / (120 * l.scale)); tracks["w"] = nil }
        touchedAt = now; tick(at: now)
    }
    private func tick(at now: Double) {
        let b = val("b"), deg = M.opticsOf(b, val("w")).theta * 180 / .pi
        let tb = Int(floor(M.clamp01(b) * 10 + 1e-4)), tt = Int(floor(deg / 5))
        let inst = grip.degAt >= 0 ? abs(deg - grip.degPrev) / max(now - grip.degAt, 0.001) : 0
        let speed = grip.degAt >= 0 ? grip.degSpeed * 0.7 + inst * 0.3 : 0
        if (tb != grip.tickB || tt != grip.tickT) && now - grip.tickAt > (speed > 60 ? 0.035 : 0.06) {
            grip.tickAt = now
            if grip.wasOff && b > 0.02 { grip.wasOff = false; medium.impactOccurred() } else { selection.selectionChanged() }
        }
        grip.tickB = tb; grip.tickT = tt; grip.degPrev = deg; grip.degAt = now; grip.degSpeed = speed
    }
    func end(_ point: CGPoint, velocity: CGSize, at date: Date, layout l: BeamShapeTorchLayout, cancelled: Bool = false) {
        let now = date.timeIntervalSinceReferenceDate, tap = grip.moved < 8 * l.scale && now - grip.t0 < 0.35
        if !cancelled {
            if grip.region == 1 {
                if !grip.handle && tap { toggle(now) }
                else if grip.rawB < -0.06 { lastOn = max(grip.b0, 0.35); timing("b", 0, 0.18, [0.23, 1, 0.32, 1], now); medium.impactOccurred() }
                else {
                    let gy = grip.axis == 1 ? 0.6 : 1, gx = grip.axis == 2 ? 0.6 : 1
                    let vb = grip.handle ? velocityB : -velocity.height * gy / (220 * l.scale)
                    let vw = grip.handle ? velocityW : (point.x >= l.cx ? 1.0 : -1.0) * velocity.width * gx / (120 * l.scale)
                    let bt = M.clamp01(M.clamp01(val("b")) + vb * 0.08)
                    timing("b", bt, 0.14, [0.23, 1, 0.32, 1], now)
                    if grip.handle || !grip.pinch {
                        let wt = M.clamp01(M.clamp01(val("w")) + vw * 0.12)
                        settleFocus(wt, vw, now)
                        if abs(wt - grip.w0) > 0.08 && !reduce { focusAt = now + 0.26 }
                    }
                }
            } else if tap && grip.region == 2 { takeOver(now); setOpen(true, now) }
            else if grip.region == 3 && grip.moved < 10 && now - grip.t0 < 0.4 && look.m >= 0.9 { takeOver(now); setOpen(false, now) }
        }
        if grip.handle { spring("sun", 0, 322.3, 30.5, now) }
        if grip.region == 1 && !reduce { spring("press", 1, 322.3, 30.5, now) }
        if grip.region != 0 {
            accessibilityB = M.clamp01(val("b")); accessibilityW = M.clamp01(val("w")); accessibilityExpanded = open
        }
        fingerDown = false; touchedAt = now; grip.region = 0; grip.handle = false
    }
    private func settleFocus(_ to: Double, _ velocity: Double, _ now: Double) {
        if reduce { values["w"] = to; tracks["w"] = nil }
        else { spring("w", to, 195, 19, now, velocity: velocity) }
    }
    func pinch(_ scale: Double, at date: Date, ended: Bool = false, velocity: Double = 0) {
        guard grip.region == 1 && !grip.handle else { return }
        if !grip.pinch { grip.pinch = true; grip.wp0 = val("w"); tracks["w"] = nil }
        if ended {
            let v = velocity * 1.1, wt = M.clamp01(M.clamp01(val("w")) + v * 0.12), now = date.timeIntervalSinceReferenceDate
            settleFocus(wt, v, now); if !reduce { focusAt = now + 0.26 }
        } else { values["w"] = M.rubber(grip.wp0 + (scale - 1) * 1.1) }
    }
    private func toggle(_ now: Double) {
        if val("b") > 0.01 { lastOn = max(val("b"), 0.35); timing("b", 0, reduce ? 0.2 : 0.22, [0.32, 0, 0.67, 0], now) }
        else if reduce { timing("b", lastOn, 0.2, [], now) }
        else { response("b", lastOn, 0.45, 1, now) }
        medium.impactOccurred()
    }
    private func setOpen(_ isOpen: Bool, _ now: Double) {
        open = isOpen
        if reduce { reducedOpen = isOpen; reducedChangeAt = now }
        else {
            response("m", isOpen ? 1 : 0, isOpen ? 0.75 : 0.8, isOpen ? 0.8 : 0.9, now)
            response("mw", isOpen ? 1 : 0, isOpen ? 0.75 : 0.8, isOpen ? 0.8 : 0.9, now, delay: isOpen ? 0 : 0.05)
        }
        if isOpen && val("b") < 0.01 { response("b", lastOn, 0.9, 1, now, delay: 0.25) }
        soft.impactOccurred()
    }
    func accessibility(_ action: String, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate; takeOver(now)
        let b = M.clamp01(val("b")), w = M.clamp01(val("w"))
        switch action {
        case "increment": timing("b", min(1, floor(b * 10 + 1.5) / 10), 0.2, [], now)
        case "decrement": timing("b", max(0, floor(b * 10 - 0.5) / 10), 0.2, [], now)
        case "narrow": timing("w", max(0, w - 0.25), 0.2, [], now)
        case "widen": timing("w", min(1, w + 0.25), 0.2, [], now)
        case "escape": setOpen(false, now)
        default:
            if !open { setOpen(true, now) }
            else { let target = b > 0.01 ? 0 : lastOn; if b > 0.01 { lastOn = max(b, 0.35) }; timing("b", target, 0.2, [], now) }
        }
        accessibilityB = tracks["b"]?.to ?? val("b")
        accessibilityW = tracks["w"]?.to ?? val("w")
        accessibilityExpanded = open
    }
    var accessibilityValue: String {
        if !accessibilityExpanded { return "Closed" }; let b = accessibilityB, w = accessibilityW
        if b < 0.01 { return "Off" }
        return "Brightness \(Int((b * 100).rounded())) percent, beam \(w < 0.15 ? "focused" : "width \(Int((w * 100).rounded())) percent")"
    }
}
