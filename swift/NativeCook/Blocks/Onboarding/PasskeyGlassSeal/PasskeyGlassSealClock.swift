import SwiftUI
import UIKit

final class PasskeyGlassSealClock: ObservableObject {
    typealias M = PasskeyGlassSealMotion
    typealias F = M.Frame
    typealias A = PasskeyGlassSealAnimation
    struct Sample { var frame: F; var scanTime: Double; var time: Double }
    @Published var moving = false
    private var f = F()
    private var animations: [WritableKeyPath<F,Double>: A] = [:]
    private var flipSpring: PasskeyGlassSealSpring?
    private var swinging = false, swingFrom = 0.0
    private var date: Date?
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
    private var manual = true, holding = false, read = false, resumeAsked = false
    private var ticks = 0
    private var touchAt = Date.distantPast
    private var clickAt: Double?
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()
    private let click = UIImpactFeedbackGenerator(style: .rigid)
    func reset(_ auto: Bool, _ reduced: Bool = false) {
        f = F(); animations.removeAll(); date = nil; now = 0; autoStart = 0; lastTouch = -100
        flipSpring = nil; swinging = false; swingFrom = 0
        manual = !auto; holding = false; read = false; resumeAsked = false; ticks = 0; clickAt = nil
        moving = true
    }
    func suspend() { date = nil }
    private func wake() { if !moving { date = nil }; moving = true }
    private func animate(_ k: WritableKeyPath<F,Double>, _ to: Double, _ duration: Double, _ curve: A.Curve = .standard, delay: Double = 0) {
        if k == \.flip { flipSpring = nil }
        animations[k] = A(from:f[keyPath:k],to:to,start:now+delay,duration:duration,curve:curve)
    }
    private func springFlip(_ to: Double, delay: Double = 0, velocity: Double? = nil, normalise: Bool = false) {
        let speed = velocity ?? flipSpring?.state(now).velocity ?? 0
        animations[\.flip] = nil
        flipSpring = PasskeyGlassSealSpring(from:f.flip,to:to,start:now+delay,velocity:speed,normalise:normalise)
    }
    private func open(_ reduced: Bool) {
        read = true; success.notificationOccurred(.success)
        animate(\.scan,0,0.2)
        if reduced { animate(\.flip,1,0.25); lastTouch = now+0.25; return }
        animate(\.unwind,1,1.2,.inOut)
        animate(\.key,1,0.6,.out,delay:1)
        animate(\.turn,1,0.6,.linear,delay:1.7); clickAt = now+2.3
        springFlip(1,delay:2.5)
        lastTouch = now+3.2
    }
    private func relock(_ reduced: Bool, lead: Double) {
        read = false
        func delay(_ s: Double) -> Double { reduced ? 0 : max(0,s-lead) }
        animate(\.turn,0,reduced ? 0.001 : 0.4,delay:delay(0.5)); clickAt = nil
        animate(\.key,0,reduced ? 0.001 : 0.4,delay:delay(0.75))
        animate(\.unwind,0,reduced ? 0.001 : 1.2,.inOut,delay:delay(0.7))
        animate(\.light,0,reduced ? 0.001 : 0.9,delay:delay(1.2))
        animate(\.scan,1,0.4,delay:delay(1.7))
    }
    private func close(_ reduced: Bool) {
        if reduced { animate(\.flip,0,0.25) } else { springFlip(0) }
        relock(reduced,lead:0)
    }
    func drag(_ tx: Double, width: Double, reduced: Bool) {
        guard read && !reduced else { return }
        if !swinging {
            guard abs(tx) >= 6 else { return }
            swinging = true; swingFrom = f.flip
        }
        animations[\.flip] = nil; flipSpring = nil
        f.flip = swingFrom+1.15*tanh((tx/(1.1*width))/1.15)
    }
    private func swingTo(_ v: Double, _ reduced: Bool) {
        let from = M.nearestFace(swingFrom)
        let goal = max(from-1,min(from+1,M.nearestFace(f.flip+v*0.18)))
        let front = M.faceOf(goal) == 0
        if front && read { relock(reduced,lead:0.15) }
        springFlip(goal,velocity:v,normalise:true)
        if !front { selection.selectionChanged() }
    }
    private func takeOver() {
        lastTouch = now; resumeAsked = false; wake()
        guard !manual else { return }
        manual = true; animate(\.finger,0,0.15); f.press = 0
        if f.flip > 0.5 {
            read = true
            for k: WritableKeyPath<F,Double> in [\.flip,\.unwind,\.key,\.turn,\.light] { animate(k,1,0.3) }
            animate(\.scan,0,0.2)
        } else {
            read = false; f.light = 0; animations[\.light] = nil
            for k: WritableKeyPath<F,Double> in [\.flip,\.unwind,\.key,\.turn] { animate(k,0,0.4) }
            animate(\.scan,1,0.3)
        }
    }
    func begin(_ reduced: Bool) {
        touchAt = Date()
        takeOver()
        if read { swingFrom = f.flip; return }
        ticks = 0; holding = true; animate(\.scan,0,0.25)
    }
    func end(_ reduced: Bool, tap: Bool, velocityX: Double = 0, width: Double = 327, cancelled: Bool = false) {
        let wasSwinging = swinging
        if swinging && !cancelled { swingTo(velocityX/(width*1.1),reduced) }
        swinging = false
        lastTouch = max(lastTouch,now)
        if holding { holding = false; if !read { animate(\.scan,1,0.3,delay:0.3) } }
        if !wasSwinging && tap && Date().timeIntervalSince(touchAt) <= 0.5 && read && abs(f.flip-1) < 0.01 { takeOver(); close(reduced) }
    }
    func activate(_ reduced: Bool) {
        takeOver()
        if read { close(reduced) } else { f.light = 1; animations[\.light] = nil; open(reduced) }
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Sample {
        if let pinned { return Sample(frame:M.at(pinned),scanTime:pinned,time:pinned) }
        // Wall-clock advancement is independent of the hold's capped integration step.
        let dt = min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
        date = at; now += dt
        let lt = now-autoStart
        if !manual && auto {
            f = M.at((lt.truncatingRemainder(dividingBy:M.loop)+M.loop).truncatingRemainder(dividingBy:M.loop))
        } else {
            for k in Array(animations.keys) {
                guard let a = animations[k], now >= a.start else { continue }
                f[keyPath:k] = a.value(now)
                if now >= a.start+a.duration { animations[k] = nil }
            }
            if let spring = flipSpring, now >= spring.start {
                let state = spring.state(now)
                f.flip = state.position
                if state.finished {
                    if spring.normalise { f.flip = M.faceOf(spring.to) == 0 ? 0 : 1 }
                    flipSpring = nil
                }
            }
            if let at = clickAt, now >= at { clickAt = nil; click.impactOccurred() }
            if holding && !read && f.unwind < 0.01 {
                f.light = min(1,f.light+(reduced ? 1 : min(dt,0.05)/M.hold))
                let q = Int(floor(f.light*4))
                if q > ticks && q < 4 { ticks = q; selection.selectionChanged() }
                if f.light >= 1 { open(reduced) }
            } else if !read && !holding && f.light > 0 && f.unwind < 0.001 {
                f.light = max(0,f.light-min(dt,0.05)*1.6)
            }
            if auto && !holding && !swinging && !(read && abs(f.flip-1) > 0.001) && now-lastTouch >= 2 {
                if !resumeAsked { resumeAsked = true; if read { close(reduced) } }
                else if f.flip < 0.001 && f.unwind < 0.001 && f.light < 0.001 && f.scan > 0.999 && now-lastTouch > 3.6 {
                    resumeAsked = false; autoStart += ceil((now-autoStart)/M.scan)*M.scan; manual = false
                }
            }
        }
        // The reference has haptics only, with no sound files or audio cues to preload.
        let needsFrames = auto || holding || swinging || flipSpring != nil || !animations.isEmpty || clickAt != nil || (f.scan > 0) || (!read && f.light > 0)
        if !needsFrames && moving {
            DispatchQueue.main.async { [weak self] in self?.moving = false; self?.date = nil }
        }
        return Sample(frame:f,scanTime:lt,time:now.truncatingRemainder(dividingBy:600))
    }
}
