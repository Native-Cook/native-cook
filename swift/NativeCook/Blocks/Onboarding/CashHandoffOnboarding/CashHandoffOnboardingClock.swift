import SwiftUI
import UIKit

final class CashHandoffOnboardingClock: ObservableObject {
    typealias M = CashHandoffOnboardingMotion
    @Published var running = false
    @Published var parked = false
    private var date: Date?
    private var now = 0.0
    private var time = 0.0
    private var last = -10.0
    private var phase = 0
    private var at = 0.0
    private var x = 0.0, y = 0.0, fx = 0.0, fy = 0.0, gx = 0.0, gy = 0.0
    private var scene = M.Scene()
    private var initialized = false
    private var audio: CashHandoffOnboardingAudio?
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()
    struct Sample { var scene: M.Scene; var time: Double; var now: Double }
    func active(_ active: Bool, _ reduced: Bool, _ pinned: Bool) {
        date = nil
        if active && !reduced && !pinned { if audio == nil { audio = CashHandoffOnboardingAudio() } }
        else { audio?.stop() }
    }
    func sample(_ date: Date, _ l: M.Layout, _ auto: Bool, _ pinned: Double?) -> Sample {
        if let pinned { return Sample(scene: M.at(pinned, l), time: pinned, now: pinned) }
        if !initialized { scene = M.at(0, l); initialized = true }
        // The loop clock follows wall time even on GPU-less recording simulators.
        let elapsed = min(0.5, max(0, self.date.map { date.timeIntervalSince($0) } ?? 0))
        self.date = date
        let dt = min(elapsed, 0.05) // RN touch clock and integrator; separate from loop time.
        now += dt
        if auto && phase == 0 && now - last >= 2 {
            let before = time
            time = (time + elapsed).truncatingRemainder(dividingBy: M.LOOP)
            scene = M.at(time, l)
            if DemoSound.inAutoPlay {
                if before < 1 && time >= 1 { audio?.play("flutter") }
                if before < 2.2 && time >= 2.2 { audio?.play("catch") }
            }
        } else {
            let since = now - at
            switch phase {
            case 0:
                scene.cx = l.hold.x; scene.cy = l.hold.y
                scene.gCurl += (0.75 - scene.gCurl) * (1 - exp(-dt * 12))
                scene.rCurl += (0.02 - scene.rCurl) * (1 - exp(-dt * 12))
                scene.rot *= exp(-dt * 10); scene.fan *= exp(-dt * 10)
                scene.gOff = 0; scene.rOff = 0; scene.caughtAt = -10
            case 1:
                let vx = (x - scene.cx) / max(dt, 0.001)
                scene.cx = x; scene.cy = y
                scene.rot += (max(-18, min(18, vx * 0.02)) - scene.rot) * (1 - exp(-dt * 12))
                scene.fan += (0.5 - scene.fan) * (1 - exp(-dt * 8))
                scene.gCurl += (0.15 - scene.gCurl) * (1 - exp(-dt * 14))
            case 2:
                let k = M.easeOut(since / 0.35)
                scene.cx = M.mix(fx, l.hold.x, k); scene.cy = M.mix(fy, l.hold.y, k)
                scene.rot *= exp(-dt * 10); scene.fan *= exp(-dt * 10)
                if since > 0.3 { scene.gCurl += (0.75 - scene.gCurl) * (1 - exp(-dt * 14)) }
                if since > 0.6 { phase = 0 }
            case 3:
                let k = M.easeOut(since / 0.25)
                scene.cx = M.mix(fx, l.palm.x, k); scene.cy = M.mix(fy, l.palm.y, k)
                scene.rot += (-6 - scene.rot) * (1 - exp(-dt * 10)); scene.fan *= exp(-dt * 10)
                scene.rCurl = M.mix(0.02, 0.85, M.closeSpring(since)); scene.caughtAt = now - since
            case 4:
                let out = M.easeIn(since / 0.9), back = M.easeOut((since - 1) / 1)
                if since < 1 {
                    scene.gOff = -out * 320; scene.rOff = out * 320; scene.cx = l.palm.x + out * 320
                } else {
                    scene.gOff = -(1 - back) * 320; scene.rOff = (1 - back) * 320
                    scene.cx = l.hold.x - (1 - back) * 320; scene.cy = l.hold.y
                    scene.rot = 0; scene.gCurl = 0.75; scene.rCurl = 0.02; scene.caughtAt = -10
                }
                if since > 2.05 { phase = 0 }
            default: break
            }
        }
        if phase == 3 && now - at > 2 && !parked {
            DispatchQueue.main.async { [weak self] in self?.parked = true }
        }
        if !auto && running && ((phase == 0 && now - last > 2) || (phase == 3 && now - at > 2)) {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return Sample(scene: scene, time: time, now: auto && phase == 0 && now - last >= 2 ? time : now)
    }
    func begin(_ p: CGPoint, _ reduced: Bool) {
        if !running { date = nil }
        running = true; parked = false; last = now
        if phase == 3 { phase = 4; at = now; return }
        if phase != 0 || hypot(p.x - scene.cx, p.y - scene.cy) > 70 { return }
        phase = 1; gx = p.x - scene.cx; gy = p.y - scene.cy; x = scene.cx; y = scene.cy
        if !reduced { audio?.play("flutter") }; selection.selectionChanged()
    }
    func move(_ p: CGPoint) { if phase == 1 { last = now; x = p.x - gx; y = p.y - gy } }
    func end(_ l: M.Layout, _ reduced: Bool) {
        if phase != 1 { return }
        last = now; fx = x; fy = y; at = now
        if hypot(x - l.palm.x, y - l.palm.y) < 90 {
            phase = 3; if !reduced { audio?.play("catch") }; success.notificationOccurred(.success)
        } else {
            phase = 2; if !reduced { audio?.play("return") }; light.impactOccurred()
        }
    }
    func cta() { light.impactOccurred() }
}
