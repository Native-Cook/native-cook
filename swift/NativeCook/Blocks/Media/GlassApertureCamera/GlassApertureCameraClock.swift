import SwiftUI
import UIKit

final class GlassApertureCameraClock: ObservableObject {
    typealias M = GlassApertureCameraMotion
    @Published var running = false
    private var date: Date?
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
    private var manual = true, resuming = false, pinching = false, buttonDown = false
    private var frame = M.Frame()
    private var fu = M.uOf(2.8), pinchU = 0.0, lastStop = 0
    private var ringT = -1.0
    private var shotStart = -1.0
    private var tweens: [String: [GlassApertureCameraTween]] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private var audio: GlassApertureCameraAudio?
    struct GlassApertureCameraTween {
        var start, from, to, duration: Double
        var curve = 0
        func value(_ now: Double) -> Double {
            let p = M.clamp01((now-start)/max(0.000001,duration))
            let e: Double
            switch curve {
            case 1: e = M.bezier(p,0.77,0,0.175,1)
            case 2: e = p*p*p
            case 3: e = M.out(p)
            case 4: e = M.bezier(p,0.23,1,0.32,1)
            default: e = p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
            }
            return from+(to-from)*e
        }
    }
    func reset(_ auto: Bool, _ reduced: Bool) {
        date = nil; now = 0; autoStart = 0; lastTouch = -100
        manual = !auto || reduced; frame = M.Frame(); fu = M.uOf(2.8)
        ringT = -1; shotStart = -1; resuming = false; pinching = false; buttonDown = false; tweens.removeAll()
        if audio == nil { audio = GlassApertureCameraAudio() }
        running = auto && !reduced
    }
    func active(_ active: Bool) { date = nil; if !active { audio?.stop() } }
    private func wake() { if !running { date = nil }; running = true }
    private func get(_ key: String) -> Double {
        switch key {
        case "fu": return fu
        case "iris": return frame.iris
        case "focus": return frame.focus
        case "snap": return frame.snap
        case "sent": return frame.sent
        case "finger": return frame.finger
        default: return frame.buttonPress
        }
    }
    private func put(_ key: String, _ v: Double) {
        switch key {
        case "fu": fu = v
        case "iris": frame.iris = v
        case "focus": frame.focus = v
        case "snap": frame.snap = v
        case "sent": frame.sent = v
        case "finger": frame.finger = v
        default: frame.buttonPress = v
        }
    }
    private func animate(_ key: String, _ to: Double, _ duration: Double, _ curve: Int = 0, _ delay: Double = 0) {
        sequence(key,[(delay,duration,to,curve)])
    }
    // Each tuple is delay, duration, target, curve; delays preserve the prior target.
    private func sequence(_ key: String, _ steps: [(Double,Double,Double,Int)]) {
        var start = now, from = get(key), result: [GlassApertureCameraTween] = []
        for (delay,duration,to,curve) in steps {
            start += delay
            result.append(.init(start: start,from: from,to: to,duration: duration,curve: curve))
            start += duration; from = to
        }
        tweens[key] = result; wake()
    }
    private func update() {
        for (key,sequence) in tweens {
            var value = sequence.first?.from ?? get(key)
            for tween in sequence {
                if now < tween.start { break }
                value = tween.value(now)
            }
            put(key,value)
        }
        tweens = tweens.filter { now < ($0.value.last.map { $0.start+$0.duration } ?? 0) }
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.frameAt(pinned) }
        // The loop retains wall-clock time on GPU-less recording simulators.
        let dt = min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
        date = at; now += dt
        update()
        if !manual && auto && !reduced {
            let t = (now-autoStart).truncatingRemainder(dividingBy: M.loop)
            let before = frame.t
            let button = frame.button, pressure = frame.buttonPress
            frame = M.frameAt(t); frame.button = button; frame.buttonPress = pressure
            fu = M.uOf(frame.fnum)
            if DemoSound.inAutoPlay {
                if before < 7.8 && t >= 7.8 { audio?.play("shutter") }
                if ((t >= 0.9 && t < 1.8) || (t >= 2.2 && t < 3.4) || (t >= 3.8 && t < 4.4)) && Int(floor(fu*21+0.5)) != lastStop { audio?.play("tick") }
                lastStop = Int(floor(fu*21+0.5))
            }
        } else {
            frame.fnum = M.fAt(fu)
            frame.ring = ringT >= 0 && now-ringT < 1.2 ? now-ringT : -1
            if shotStart >= 0 {
                let before = frame.shot, s = now-shotStart
                if s >= M.Shot.end { frame.shot = -1; shotStart = -1 }
                else {
                    frame.shot = s
                    if !reduced && before-M.Shot.go < M.land && s-M.Shot.go >= M.land { light.impactOccurred() }
                }
            }
            if auto && !reduced && !pinching && now-lastTouch >= 2 {
                if !resuming {
                    resuming = true; animate("fu",M.uOf(2.8),0.6,1); animate("focus",0.2,0.6,1)
                } else {
                    let still = abs(fu-M.uOf(2.8)) < 0.002 && abs(frame.focus-0.2) < 0.002 && frame.iris < 0.001 && frame.shot < 0 && frame.sent < 0.001 && frame.snap < 0.001
                    if still && now-lastTouch > 2.8 { resuming = false; autoStart = now; manual = false }
                }
            }
        }
        let awake = (!manual && auto && !reduced) || pinching || !tweens.isEmpty || frame.ring >= 0 || shotStart >= 0 || (auto && !reduced)
        if !awake && running { DispatchQueue.main.async { [weak self] in self?.running = false } }
        return frame
    }
    func takeOver() {
        lastTouch = now; resuming = false
        if !manual {
            manual = true
            animate("finger",0,0.15); frame.press = 0
            animate("iris",0,0.3)
            tweens.removeValue(forKey: "snap")
            frame.shot = -1; shotStart = -1; frame.snap = 0
            // The live ring is independent of the autoplay ring.
            frame.ring = -1
        }
        wake()
    }
    func pinchBegin(_ reduced: Bool) {
        takeOver(); pinching = true; pinchU = fu; lastStop = Int(floor(fu*21+0.5))
        animate("iris",1,reduced ? 0.001 : 0.22)
    }
    func pinchMove(_ scale: Double) {
        lastTouch = now; tweens.removeValue(forKey: "fu")
        fu = M.clamp01(pinchU-log(max(scale,0.000001))*0.55)
        let stop = Int(floor(fu*21+0.5))
        if stop != lastStop { lastStop = stop; selection.selectionChanged(); audio?.play("tick") }
    }
    func pinchEnd(_ reduced: Bool) {
        lastTouch = now; pinching = false
        animate("iris",0,reduced ? 0.001 : 0.36,0,0.5)
    }
    func focus(_ point: CGPoint, _ l: GlassApertureCameraTheme.Layout, _ reduced: Bool) {
        takeOver()
        animate("focus",M.depthAt(point.x/l.w,point.y/l.photoH),reduced ? 0.001 : 0.52,1)
        ringT = now; frame.ringPoint = point; soft.impactOccurred()
    }
    func press(_ target: Int, _ down: Bool) {
        if frame.button == target && buttonDown == down { return }
        frame.button = target; buttonDown = down
        animate("button",down ? 1 : 0,0.15,4)
    }
    func shoot(_ reduced: Bool) {
        takeOver(); rigid.impactOccurred(); audio?.play("shutter")
        if !reduced { sequence("snap",[(0,0.11,1,2),(0,0.23,0,3)]) }
        shotStart = now; frame.shot = 0
        let sentDelay = reduced ? 0.3 : ((M.Shot.go+M.land)*1000).rounded()/1000
        sequence("sent",[(sentDelay,0.25,1,0),(1.5,0.25,0,0)])
    }
}
