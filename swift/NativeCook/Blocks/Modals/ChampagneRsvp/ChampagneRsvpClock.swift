import SwiftUI
import UIKit

final class ChampagneRsvpClock: ObservableObject {
    typealias M = ChampagneRsvpMotion
    @Published var done = false
    @Published var running = false
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
    private var date: Date?
    private var manual = false, dragging = false, began = false
    private var resumeAt: Double?
    private var resumeFrom = -M.restLean
    private var live = M.Frame()
    private var leanFrom = -M.restLean
    private var clinkedAt = -1.0, clinkAt = -1.0, burstAt = -1.0
    private var acceptAt = -1.0, acceptFrom = -M.restLean
    private var fingerAt = -1.0, fingerFrom = 0.0
    private let impact = UIImpactFeedbackGenerator(style: .rigid)
    private let notification = UINotificationFeedbackGenerator()

    func reset(_ auto: Bool, _ reduced: Bool) {
        now = 0; autoStart = 0; lastTouch = -100; date = nil
        manual = !auto || reduced; live = M.Frame()
        clinkedAt = -1; clinkAt = -1; burstAt = -1; acceptAt = -1
        fingerAt = -1; resumeAt = nil; dragging = false; began = false
        running = false; done = false
    }
    func active() { date = nil }
    private func setReply(_ value: Bool) {
        guard done != value else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.done != value else { return }
            self.done = value
        }
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pin: Double?) -> M.Frame {
        if let pin { return M.at(pin) }
        // Autoplay must follow wall time on the slow recording simulator.
        let dt = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0))
        date = at; now += dt
        if !manual && auto && !reduced {
            live = M.at((now - autoStart).truncatingRemainder(dividingBy: M.loop))
            setReply(live.done)
            return live
        }
        if !reduced { live.phase += 0.6 * dt }
        if fingerAt >= 0 {
            let p = M.seg(now, fingerAt, fingerAt + 0.15)
            let q = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
            live.finger = fingerFrom * (1 - q)
            if now >= fingerAt + 0.15 { fingerAt = -1 }
        }
        if acceptAt >= 0 {
            let k = M.seg(now, acceptAt, acceptAt + 0.48)
            live.lean = acceptFrom + (M.meet - acceptFrom) * k * k
            if k >= 1 { acceptAt = -1; toast(reduced) }
        }
        if clinkAt >= 0 {
            live.clink = 0.001 + 0.999 * M.seg(now, clinkAt, clinkAt + 0.9)
            if now >= clinkAt + 0.9 { live.clink = 0; clinkAt = -1 }
        }
        if burstAt >= 0 {
            live.burst = 0.001 + 0.999 * M.seg(now, burstAt, burstAt + 1.4)
            if now >= burstAt + 1.4 { live.burst = 0; burstAt = -1 }
        }
        if clinkedAt >= 0 && !dragging {
            let tau = now - clinkedAt
            live.lean = M.clinkLeanAt(tau)
            if tau > 1.2 { clinkedAt = -1 }
        }
        if auto && !reduced && !dragging && clinkedAt < 0 && now - lastTouch >= 2 {
            if resumeAt == nil {
                resumeAt = now; resumeFrom = live.lean; setReply(false)
            }
            if let start = resumeAt {
                let p = M.seg(now, start, start + 0.4)
                // withTiming's default Easing.inOut(Easing.quad).
                let q = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
                live.lean = resumeFrom + (-M.restLean - resumeFrom) * q
                if abs(live.lean + M.restLean) < 0.001 && live.clink == 0 && live.burst == 0 {
                    live.phase = 0; autoStart = now; manual = false; resumeAt = nil
                }
            }
        }
        live.done = done
        if reduced && clinkedAt < 0 && clinkAt < 0 && burstAt < 0 && acceptAt < 0 && running {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return live
    }
    private func takeOver() {
        lastTouch = now; resumeAt = nil
        if !manual {
            manual = true; fingerAt = now; fingerFrom = live.finger; live.fingerPress = 0
            if live.lean > -M.restLean + 0.002 { clinkedAt = now - 0.3 }
        }
        running = true
    }
    private func toast(_ reduced: Bool) {
        clinkedAt = now; clinkAt = now; live.clink = 0.001
        if !reduced { burstAt = now; live.burst = 0.001 }
        impact.impactOccurred()
        setReply(true)
    }
    func accept(_ reduced: Bool) {
        if done { notification.notificationOccurred(.success); return }
        date = nil; takeOver()
        if reduced { toast(true) }
        else { acceptAt = now; acceptFrom = live.lean }
    }
    func decline() { done = false }
    func drag(_ value: DragGesture.Value, _ l: ChampagneRsvpLayout, _ reduced: Bool) {
        if !began { began = true; takeOver() }
        lastTouch = now
        guard !reduced else { return }
        if !dragging && hypot(value.translation.width, value.translation.height) >= 2 {
            dragging = true; clinkedAt = -1; acceptAt = -1; leanFrom = live.lean
        }
        guard dragging else { return }
        let inward = value.location.x < l.w / 2 ? value.translation.width : -value.translation.width
        live.lean = max(-0.3, min(M.meet, leanFrom + inward / (l.heroS * 2.6)))
        if live.lean >= M.meet - 0.0005 && live.clink == 0 { toast(reduced) }
    }
    func endDrag() {
        lastTouch = now; began = false
        if dragging { dragging = false; clinkedAt = now - 0.12 }
    }
}
