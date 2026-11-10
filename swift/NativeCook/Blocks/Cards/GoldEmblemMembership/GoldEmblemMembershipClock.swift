import AVFoundation
import SwiftUI
import UIKit

final class GoldEmblemMembershipClock: ObservableObject {
    typealias M = GoldEmblemMembershipMotion
    typealias Key = WritableKeyPath<M.Frame, Double>
    struct Lane {
        var from, to, at, duration: Double
        var spring = false
        var damping = 0.0, stiffness = 0.0, velocity = 0.0, energy = 0.0
        var position = 0.0, last = 0.0
    }
    @Published var running = false
    private(set) var now = 0.0
    private var date: Date?
    private var autoStart = 0.0, lastTouch = -100.0
    private var mode = 0, resumeAsked = false, dragging = false, stamped = false
    private var state = M.Frame()
    private var lanes: [Key: Lane] = [:]
    private var castAt: Double?, castDuration = M.CAST, castStart = 0.0
    private var auto = false, reduced = false
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let success = UINotificationFeedbackGenerator()
    private var players: [String: AVAudioPlayer] = [:]

    func reset(_ auto: Bool, _ reduced: Bool) {
        self.auto = auto && !reduced; self.reduced = reduced
        mode = self.auto ? 0 : 1; now = 0; autoStart = 0; date = nil
        state = M.Frame(); lanes.removeAll(); castAt = nil
        dragging = false; stamped = false; resumeAsked = false; lastTouch = -100
        preload(); running = self.auto
    }
    private func preload() {
        guard players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for name in ["melt", "stamp"] {
            if let url = Bundle.main.url(forResource: "GoldEmblemMembership-gem-\(name)", withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = name == "melt" ? 0.55 : 0.65
                player.prepareToPlay(); players[name] = player
            }
        }
    }
    private func cue(_ name: String) { if let p = players[name] { p.currentTime = 0; p.play() } }
    func active() { date = nil }
    private func set(_ key: Key, _ value: Double) { lanes[key] = nil; state[keyPath: key] = value }
    private func timing(_ key: Key, _ value: Double, _ duration: Double) {
        lanes[key] = Lane(from: state[keyPath: key], to: value, at: now, duration: duration)
    }
    // Reanimated 4 duration spring: mass 4, perceptual duration 1.5*d, energy threshold 6e-9.
    private func spring(_ key: Key, _ duration: Double, _ damping: Double, _ velocity: Double = 0) {
        let x = state[keyPath: key]
        var v = (lanes[key]?.velocity ?? 0)+velocity
        if x*v > 0 { v = 0 }
        if x == 0 && v == 0 { set(key, 0); return }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k/4)*damping, t = duration*1.5
            let xt = (x+(v+x*w)*t)*exp(-w*t)
            let vt = xt*(-w)+(v+x*w)*exp(-w*t)
            return (k*xt*xt+4*vt*vt)/(k*x*x+4*v*v)-6e-9
        }
        var lo = Double.ulpOfOne, hi = 8000.0, k = 4000.0
        let direction = difference(hi) >= difference(lo) ? 1.0 : -1.0
        for _ in 0..<100 {
            if abs(difference(k)) <= 6e-12 { break }
            if difference(k)*direction < 0 { lo = k } else { hi = k }; k = (lo+hi)/2
        }
        lanes[key] = Lane(from: x, to: 0, at: now, duration: duration, spring: true,
                          damping: damping, stiffness: k, velocity: v,
                          energy: 0.5*k*x*x+2*velocity*velocity, position: x, last: now)
    }
    private func animate() {
        for key in Array(lanes.keys) {
            guard var a = lanes[key] else { continue }
            if a.spring {
                let dt = min(0.064, max(0, now-a.last))
                let w = sqrt(a.stiffness/4), wd = w*sqrt(1-a.damping*a.damping)
                let envelope = exp(-a.damping*w*dt), x = a.position-a.to, v = a.velocity
                let frag = envelope*(sin(wd*dt)*((v+a.damping*w*x)/wd)+x*cos(wd*dt))
                a.position = a.to+frag
                a.velocity = -a.damping*w*frag+envelope*(cos(wd*dt)*(v+a.damping*w*x)-wd*x*sin(wd*dt))
                a.last = now
                if a.energy == 0 || (0.5*a.stiffness*pow(a.position-a.to, 2)+2*a.velocity*a.velocity)/a.energy <= 6e-9 {
                    set(key, a.to)
                } else { state[keyPath: key] = a.position; lanes[key] = a }
            } else {
                let p = M.clamp01((now-a.at)/a.duration)
                state[keyPath: key] = a.from+(a.to-a.from)*M.timing(p)
                if p >= 1 { lanes[key] = nil }
            }
        }
        if let at = castAt {
            state.cast = castStart+(1-castStart)*M.clamp01((now-at)/castDuration)
            if state.cast > 0.86 && !stamped {
                stamped = true; success.notificationOccurred(.success)
                if !reduced { cue("stamp") }
            }
            if state.cast >= 1 { state.from = state.to; state.cast = 0; castAt = nil }
        }
    }
    func sample(_ at: Date, _ pin: Double?) -> M.Frame {
        if let pin { return M.frameAt(pin) }
        // Wall-clock time: only the physical spring step is clamped to 64 ms.
        let dt = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0))
        date = at; let before = now; now += dt
        animate()
        if mode == 0 && auto {
            let old = (before-autoStart).truncatingRemainder(dividingBy: M.LOOP)
            let t = (now-autoStart).truncatingRemainder(dividingBy: M.LOOP)
            if DemoSound.inAutoPlay {
                for c in M.casts {
                    func crossed(_ beat: Double) -> Bool { old <= t ? old < beat && t >= beat : old < beat || t >= beat }
                    if crossed(c.at) { cue("melt") }
                    if crossed(c.at+M.CAST*0.86) { cue("stamp") }
                }
            }
            state = M.frameAt(t)
            return state
        }
        if auto && !dragging && state.cast == 0 && now-lastTouch >= 2 {
            if !resumeAsked {
                resumeAsked = true
                if state.to != 0 { castTo(0, -1) }
            } else if state.from == 0 && state.to == 0 && abs(state.drag) < 0.3 && now-lastTouch > 2.4 {
                resumeAsked = false; autoStart = now; mode = 0
            }
        }
        if !auto && !dragging && castAt == nil && lanes.isEmpty && running {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return state
    }
    private func castTo(_ next: Int, _ side: Double) {
        if next == state.to && state.cast == 0 && state.from == next { return }
        state.from = state.cast > 0 && state.cast < 0.5 ? state.from : state.to
        state.to = next; state.side = side; stamped = false; state.cast = 0
        castAt = now; castStart = 0; castDuration = reduced ? 0.4 : M.CAST
        heavy.impactOccurred(); if !reduced { cue("melt") }
    }
    private func takeOver() {
        lastTouch = now; resumeAsked = false
        guard mode == 0 else { return }
        mode = 1; timing(\M.Frame.finger, 0, 0.15); set(\M.Frame.press, 0)
        spring(\M.Frame.drag, 0.4, 0.7)
        if state.cast > 0 { castStart = state.cast; castAt = now; castDuration = (1-state.cast)*M.CAST }
    }
    func touch(_ event: GoldEmblemMembershipTouch.Event, _ dx: Double, _ dy: Double, _ vx: Double) {
        if !running { date = nil }; running = true
        switch event {
        case .begin: takeOver()
        case .start: dragging = true
        case .update: lastTouch = now; set(\M.Frame.drag, 34*tanh(dx/110))
        case .end:
            let cur = state.to, far = abs(vx) > 1800
            var next = cur
            if dx < -60 || vx < -600 { next = far ? 2 : min(2, cur+1) }
            else if dx > 60 || vx > 600 { next = far ? 0 : max(0, cur-1) }
            if next != cur && state.cast == 0 { castTo(next, next > cur ? 1 : -1) }
        case .finalize:
            let sech = 1/cosh(dx/110)
            lastTouch = now; dragging = false
            spring(\M.Frame.drag, 0.6, 0.55, 34*sech*sech/110*vx)
        }
    }
    func adjust(_ direction: Int) {
        if !running { date = nil }; running = true; takeOver()
        let next = max(0, min(2, state.to+direction))
        if next != state.to { castTo(next, next > state.to ? 1 : -1) }
    }
}
