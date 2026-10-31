import AVFoundation
import SwiftUI
import UIKit

final class StickerPeelProfileClock: ObservableObject {
    typealias M = StickerPeelProfileMotion
    @Published var awake = false
    @Published var holding = false
    @Published var pulse = 0
    private var holdTimer: DispatchWorkItem?
    var stickers: [M.Sticker] = []
    var order = [0,1,2,3]
    var time = 0.0
    private var now = 0.0, last = -10.0
    private var previous: Date?
    private var width = 0.0, top = 0.0
    private var drag = -1, peeling = false
    private var origin = CGPoint.zero, offset = CGPoint.zero, px = 0.0
    private var twist = -1, k0 = 1.0, r0 = 0.0
    private var players: [String:AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style:.medium)
    private let light = UIImpactFeedbackGenerator(style:.light)
    func layout(_ w: Double,_ t: Double) {
        guard width != w || top != t else { return }
        width = w; top = t; stickers = M.home(w,t)
    }
    func prepare(_ reduced: Bool) {
        previous = nil
        guard !reduced,players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for name in ["peel","slap","tap"] {
            if let u = Bundle.main.url(forResource:"StickerPeelProfile-spp-\(name)",withExtension:"wav"),let p = try? AVAudioPlayer(contentsOf:u) {
                p.volume = name == "tap" ? 0.5 : 0.75; p.prepareToPlay(); players[name] = p
            }
        }
    }
    func suspend() {
        previous = nil; holdTimer?.cancel(); holdTimer = nil; holding = false
        for p in players.values { p.stop() }
    }
    private func hold(_ until: Double?) {
        guard let until else {
            holdTimer?.cancel(); holdTimer = nil
            if holding { DispatchQueue.main.async { [weak self] in self?.holding = false } }
            return
        }
        guard holdTimer == nil else { return }
        if !holding { DispatchQueue.main.async { [weak self] in self?.holding = true } }
        // No display-rate work during a still. Wake at most 0.5 s apart so the wall-clock cap
        // never slows the script, and exactly at the next moving beat.
        let item = DispatchWorkItem { [weak self] in
            guard let self else { return }; self.holdTimer = nil; self.pulse += 1
        }
        holdTimer = item
        DispatchQueue.main.asyncAfter(deadline:.now()+max(0.001,min(0.5,until)),execute:item)
    }
    private func cue(_ name: String,_ reduced: Bool) {
        guard !reduced, let p = players[name] else { return }; p.currentTime = 0; p.play()
    }
    func sample(_ date: Date,_ w: Double,_ top: Double,_ auto: Bool,_ reduced: Bool,_ pinned: Double?) -> [M.Sticker] {
        layout(w,top)
        if let pinned {
            order = [0,1,3,2]
            var frame = M.frame(pinned,w,top)
            if reduced { for i in frame.indices { frame[i].f = 0; frame[i].lift = 0 } }
            return frame
        }
        let dt = min(max(0,previous.map { date.timeIntervalSince($0) } ?? 0.016),0.5)
        previous = date; now += dt
        let scripted = auto && !reduced && drag < 0 && now-last >= 2
        if scripted {
            let before = time
            time = (time+dt).truncatingRemainder(dividingBy:M.loop)
            let prior = stickers
            stickers = M.frame(time,w,top)
            for i in stickers.indices {
                stickers[i].vf = prior[i].vf; stickers[i].vsq = prior[i].vsq; stickers[i].vx = prior[i].vx
            }
            order = [0,1,3,2]
            if time < 0.8 { hold(0.8-time) }
            else if time >= 3.1 && time < 5.2 { hold(5.2-time) }
            else if time >= 7.5 { hold(M.loop-time+0.8) }
            else { hold(nil) }
            if DemoSound.inAutoPlay {
                for (beat,name) in [(1.65,"peel"),(2.6,"slap"),(6.05,"peel"),(7.0,"slap")] {
                    if (before < beat && time >= beat) || (time < before && time >= beat) { cue(name,reduced) }
                }
            }
        } else {
            hold(nil)
            // RN's Euler integrator and damping constants, with its 50 ms physics cap.
            let h = min(dt,0.05)
            for i in stickers.indices {
                if !(drag == i && peeling) {
                    stickers[i].vf += (-300*stickers[i].f-2*sqrt(300)*0.35*stickers[i].vf)*h
                    stickers[i].f = max(0,stickers[i].f+stickers[i].vf*h)
                }
                if !(drag == i && stickers[i].lift > 0.5) { stickers[i].lift = max(0,stickers[i].lift-h*10) }
                stickers[i].vsq += (-500*stickers[i].sq-2*sqrt(500)*0.3*stickers[i].vsq)*h
                stickers[i].sq += stickers[i].vsq*h; stickers[i].vx *= exp(-h*8)
            }
            let moving = stickers.contains { $0.f > 0.00001 || abs($0.vf) > 0.001 || $0.lift > 0 || abs($0.sq) > 0.00001 || abs($0.vsq) > 0.001 || abs($0.vx) > 0.01 }
            if awake && drag < 0 && !moving && (!auto || reduced) {
                DispatchQueue.main.async { [weak self] in self?.awake = false }
            }
        }
        return stickers
    }
    func pan(_ state: UIGestureRecognizer.State,_ p: CGPoint,_ reduced: Bool) {
        switch state {
        case .began:
            holdTimer?.cancel(); holdTimer = nil; holding = false
            previous = nil; awake = true; last = now
            for i in order.reversed() {
                let s = stickers[i], a = -s.rot * .pi/180, dx = p.x-s.x, dy = p.y-s.y
                let lx = dx*cos(a)-dy*sin(a), ly = dx*sin(a)+dy*cos(a)
                let h = M.half[i]
                if abs(lx) < h.0+8 && abs(ly) < h.1+8 {
                    stickers[i].corner = ly < 0 ? (lx < 0 ? 0 : 1) : (lx < 0 ? 3 : 2)
                    drag = i; origin = p; offset = CGPoint(x:s.x-p.x,y:s.y-p.y); peeling = !reduced; px = p.x
                    order.removeAll { $0 == i }; order.append(i); selection.selectionChanged(); break
                }
            }
        case .changed:
            guard drag >= 0 else { return }; last = now
            let i = drag, dist = hypot(p.x-origin.x,p.y-origin.y)
            if peeling {
                let h = M.half[i]; stickers[i].f = min(0.62,dist/(hypot(h.0,h.1)*1.6))
                if dist > 52 { peeling = false; stickers[i].lift = 1; stickers[i].f = 0; cue("peel",reduced); medium.impactOccurred() }
            } else {
                stickers[i].lift = 1; stickers[i].vx = (p.x-px)/0.016; px = p.x
                stickers[i].x = p.x+offset.x; stickers[i].y = p.y+offset.y
            }
        case .ended,.cancelled,.failed:
            guard drag >= 0 else { return }; last = now
            if !peeling && stickers[drag].lift > 0.5 {
                stickers[drag].lift = 0; stickers[drag].sq = 0.1; stickers[drag].vsq = 0
                cue("slap",reduced); medium.impactOccurred()
            }
            drag = -1; peeling = false
        default: break
        }
    }
    func transform(_ state: UIGestureRecognizer.State,_ p: CGPoint,_ scale: Double?,_ rotation: Double?) {
        if state == .began {
            let picked = order.reversed().first { i in
                let s = stickers[i], h = M.half[i]
                return abs(p.x-s.x) < h.0*s.k+30 && abs(p.y-s.y) < h.1*s.k+30
            }
            twist = drag >= 0 ? drag : (picked ?? -1)
            guard twist >= 0 else { return }
            if scale != nil { k0 = stickers[twist].k }; if rotation != nil { r0 = stickers[twist].rot }
            last = now; awake = true
        }
        if state == .changed && twist >= 0 {
            if let scale { stickers[twist].k = max(0.6,min(1.8,k0*scale)) }
            if let rotation { stickers[twist].rot = r0+rotation*180 / .pi }; last = now
        }
        if state == .ended || state == .cancelled {
            if scale != nil { twist = -1 }; if rotation != nil { selection.selectionChanged() }
        }
    }
    func close() { light.impactOccurred() }
}
