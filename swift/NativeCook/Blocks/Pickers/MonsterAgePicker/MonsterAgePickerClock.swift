import SwiftUI
import UIKit

final class MonsterAgePickerClock: ObservableObject {
    typealias M = MonsterAgePickerMotion
    @Published var running = true
    @Published var age = 5
    @Published var chosen = -1
    var frame = M.Frame()
    private var date: Date?
    private var now = 0.0
    private var off = 2.0, v = 0.0, target = 2.0, off0 = 2.0
    private var jawT = -10.0, lastTouch = -100.0, loopT = 0.0
    private var dragging = false, scripted = false
    private var lastTick = 2
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    private var wakeup: DispatchWorkItem?
    private var parkedAt: Date?
    deinit { wakeup?.cancel() }
    func active() { wake(); date = nil }
    private func wake() {
        wakeup?.cancel(); wakeup = nil
        if let parkedAt { now += max(0,Date().timeIntervalSince(parkedAt)); self.parkedAt = nil }
        date = nil
        running = true
    }
    private func park() {
        guard wakeup == nil, !dragging, abs(v)<0.00001, abs(target-off)<0.00001, now-jawT>=0.7 else { return }
        parkedAt = Date()
        let delay = max(0.001,4.2-now.truncatingRemainder(dividingBy:4.2))
        let item = DispatchWorkItem { [weak self] in self?.wake() }
        wakeup = item
        running = false
        DispatchQueue.main.asyncAfter(deadline:.now()+delay,execute:item)
    }
    func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
        if let pinned {
            let sc = M.script(pinned)
            return M.Frame(off: sc.off, jaw: sc.jaw)
        }
        if date == at { return frame }
        let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        let dt = min(wall, 0.05)
        now += wall
        var jaw = 0.0
        if auto && !reduced && !dragging && now-lastTouch > 3.5 && (scripted || abs(off-2)<0.002) {
            if !scripted { scripted = true; loopT = 0.2 }
            let previous = off
            loopT = (loopT+wall).truncatingRemainder(dividingBy: M.loop)
            let sc = M.script(loopT)
            v = (sc.off-previous)/max(dt,0.001)
            off = sc.off; target = off.rounded(); jaw = sc.jaw
        } else {
            scripted = false
            if !dragging {
                let k = reduced ? 600.0 : 220.0
                let c = 2*0.9*sqrt(k)
                v += (k*(target-off)-c*v)*dt
                off += v*dt
            }
            jaw = reduced ? (now-jawT < 0.25 ? 1 : 0) : M.chomp(now-jawT)
        }
        let tick = Int(off.rounded())
        if tick != lastTick {
            lastTick = tick
            if !scripted { selection.selectionChanged() }
            let value = min(11,max(0,tick))+3
            DispatchQueue.main.async { [weak self] in self?.age = value; self?.chosen = -1 }
        }
        if jaw > 0.5 && frame.jaw <= 0.5 {
            let value = Int(off.rounded())
            DispatchQueue.main.async { [weak self] in self?.chosen = value }
        }
        let bp = now.truncatingRemainder(dividingBy: 4.2)
        frame = M.Frame(off: off, v: v, jaw: jaw,
                        look: reduced ? 0 : max(-1,min(1,v/6)),
                        blink: bp < 0.14 ? sin(bp/0.14 * .pi) : 0)
        let moving = dragging || abs(v)>0.00001 || abs(target-off)>0.00001 || now-jawT<0.7 || bp<0.14
        if (!auto || reduced) && !moving && wakeup == nil {
            DispatchQueue.main.async { [weak self] in self?.park() }
        }
        return frame
    }
    func pan(_ state: UIGestureRecognizer.State, translation: Double, velocity: Double) {
        if state == .began { wake(); dragging = true; scripted = false; off0 = off; running = true }
        if state == .changed || state == .began {
            var value = off0-translation/60
            if value < 0 { value = -(1-exp(value))*0.5 }
            if value > 11 { value = 11+(1-exp(-(value-11)))*0.5 }
            v = -velocity/60; off = value; lastTouch = now
        } else if state == .ended || state == .cancelled || state == .failed {
            guard dragging else { return }
            dragging = false
            target = min(11,max(0,(off-velocity/60*0.18).rounded()))
            v = -velocity/60; lastTouch = now
        }
    }
    func tap(_ x: Double, width: Double) {
        if parkedAt != nil { wake() }
        target = min(11,max(0,(off+(x-width/2)/60).rounded()))
        scripted = false; lastTouch = now; running = true; date = nil
    }
    func step(_ delta: Double) {
        if parkedAt != nil { wake() }
        target = min(11,max(0,target+delta)); scripted = false; lastTouch = now
        running = true; date = nil
    }
    func chomp() {
        if parkedAt != nil { wake() }
        success.notificationOccurred(.success)
        jawT = now; scripted = false; lastTouch = now; running = true; date = nil
    }
}
