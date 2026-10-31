import SwiftUI
import UIKit

final class IconEraScrubberDriver: ObservableObject {
    typealias M = IconEraScrubberMotion
    typealias A = IconEraScrubberAnimation
    @Published private(set) var revision = 0
    private var origin = Date.timeIntervalSinceReferenceDate
    private var pausedAt: Double?
    private var pausedDuration = 0.0
    private var manual = false
    private var held = false
    private var dragging = false
    private var rulerTouch = false
    private var year = A.constant(M.first), lean = A.constant(0)
    private var grabbedYear = M.first
    private var lastTouch = -100.0
    private var touchStarted = 0.0
    private var pressedAt = 0.0
    private var caughtFinger = 0.0, caughtFingerX = 0.0
    private var autoStart = 0.0, phaseBase = 0.0, phaseStart = 0.0
    var autoPlay = false
    var reduced = false
    private var pin: Double? {
        #if DEBUG
        if UserDefaults.standard.object(forKey:"ncPinTime") != nil {
            return UserDefaults.standard.double(forKey:"ncPinTime")
        }
        #endif
        return nil
    }
    struct Sample {
        var frame: M.Frame
        var phase: Double
        var manual: Bool
    }
    func now(_ date: Date = Date()) -> Double {
        (pausedAt ?? date.timeIntervalSinceReferenceDate)-origin-pausedDuration
    }
    func configure(auto: Bool, reduced: Bool) {
        autoPlay = auto; self.reduced = reduced
        origin = Date.timeIntervalSinceReferenceDate
        pausedDuration = 0; pausedAt = nil
        manual = false; held = false; dragging = false
        autoStart = 0; phaseBase = 0; phaseStart = 0
        year = .constant(M.first); lean = .constant(0)
        lastTouch = -100; revision += 1
    }
    func preferences(auto: Bool, reduced: Bool) {
        let t = now(), current = sample(now())
        phaseBase = current.phase; phaseStart = t
        autoPlay = auto; self.reduced = reduced
        year = .constant(current.frame.year)
        lean = .constant(reduced ? 0 : current.frame.lean)
        caughtFinger = 0; held = false; dragging = false
        manual = !auto || reduced
        autoStart = t; lastTouch = t
        revision += 1
    }
    func active(_ active: Bool) {
        if active, let pausedAt {
            pausedDuration += Date.timeIntervalSinceReferenceDate-pausedAt
            self.pausedAt = nil
        } else if !active && pausedAt == nil {
            pausedAt = Date.timeIntervalSinceReferenceDate
        }
        revision += 1
    }
    // All frames, including the two-second idle return, are sampled without writing state.
    func sample(_ t: Double) -> Sample {
        let auto = autoPlay && !reduced
        let phase = phaseBase + (reduced ? 0 : 2*Double.pi*(t-phaseStart)/M.loop)
        if !manual {
            let loopTime = pin ?? (t-autoStart).truncatingRemainder(dividingBy:M.loop)
            return Sample(frame:auto ? M.frameAt(loopTime) : .init(year:year.value(t)),
                          phase:auto && pin != nil ? 2*Double.pi*loopTime/M.loop : phase,manual:!auto)
        }
        let resume = max(lastTouch,year.end)+2
        if auto && !held && t >= resume {
            let y = year.value(resume)
            let returnDuration = abs(y-M.first)>0.001 ? 1.2 : 0
            if t >= resume+returnDuration {
                let loopTime = (t-resume-returnDuration).truncatingRemainder(dividingBy:M.loop)
                return Sample(frame:M.frameAt(loopTime),phase:phase,manual:false)
            }
            let k = M.timingEase((t-resume)/returnDuration)
            return Sample(frame:.init(year:y+(M.first-y)*k,lean:lean.value(t)),phase:phase,manual:true)
        }
        let fade = M.clamp01((t-touchStarted)/0.15)
        let eased = fade<0.5 ? 2*fade*fade : 1-pow(-2*fade+2,2)/2
        return Sample(frame:.init(year:year.value(t),lean:reduced ? 0 : lean.value(t),
                                  finger:caughtFinger*(1-eased),fingerX:caughtFingerX),phase:phase,manual:true)
    }
    var paused: Bool {
        pausedAt != nil || (pin != nil && !manual && autoPlay && !reduced)
        || (reduced && !held && now() >= max(year.end,lean.end))
    }
    func moving(_ t: Double) -> Bool { held || t < max(year.end,lean.end) }
    func settled() { revision += 1 }
    private func takeOver(_ t: Double) {
        let current = sample(t)
        // A resumed loop can be running even while the earlier manual event is retained.
        if !current.manual {
            year = .constant(current.frame.year)
            lean = .init(from:current.frame.lean,to:0,start:t,duration:0.3,quadratic:true)
            caughtFinger = current.frame.finger; caughtFingerX = current.frame.fingerX
            touchStarted = t
        }
        manual = true; lastTouch = t
    }
    func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ delta: CGSize,
               _ velocity: CGVector, layout: IconEraScrubberTheme.Layout) {
        let t = now()
        switch state {
        case .began:
            let caught = sample(t).frame.year
            takeOver(t); held = true; dragging = false
            pressedAt = t
            rulerTouch = point.y >= layout.rulerTop-24 && point.y <= layout.rulerTop+72
            if rulerTouch { year = .constant(caught) }
            grabbedYear = year.value(t)
        case .changed:
            lastTouch = t
            if rulerTouch && (dragging || hypot(delta.width,delta.height)>=2) {
                dragging = true
                var y = grabbedYear-delta.width/IconEraScrubberTheme.step
                if y<M.first { y = M.first-0.6*(1-exp((y-M.first)/0.6)) }
                if y>M.last { y = M.last+0.6*(1-exp(-(y-M.last)/0.6)) }
                year = .constant(y)
                if !reduced {
                    let k = max(-1,min(1,-velocity.dx/IconEraScrubberTheme.step/30))
                    // Reanimated continues an already-running spring when its target is unchanged.
                    if lean.to != k || t >= lean.end {
                        lean = .glide(lean.value(t),k,at:t,duration:0.3,damping:0.8,velocity:lean.speed(t))
                    }
                }
            }
        case .ended, .cancelled, .failed:
            held = false; lastTouch = t
            if rulerTouch {
                lean = reduced ? .constant(0) : .glide(lean.value(t),0,at:t,duration:0.5,damping:0.7,velocity:lean.speed(t))
                if dragging {
                    let v = -velocity.dx/IconEraScrubberTheme.step
                    glide(to:min(M.last,max(M.first,floor(year.value(t)+0.3*v+0.5))),velocity:v,at:t)
                } else if state == .ended && t-pressedAt <= 0.5 {
                    let y = floor(year.value(t)+(point.x-layout.w/2)/IconEraScrubberTheme.step+0.5)
                    glide(to:min(M.last,max(M.first,y)),velocity:0,at:t)
                }
            }
            dragging = false
        default: break
        }
        revision += 1
    }
    private func glide(to: Double, velocity: Double, at t: Double) {
        let y = year.value(t)
        year = reduced ? .init(from:y,to:to,start:t,duration:0.3)
            : .glide(y,to,at:t,duration:0.7,damping:0.85,velocity:velocity+year.speed(t))
    }
    func step(_ direction: Int) {
        let t = now(), caught = sample(now()).frame.year
        takeOver(t); year = .constant(caught)
        glide(to:min(M.last,max(M.first,floor(year.value(t)+0.5)+Double(direction))),velocity:0,at:t)
        revision += 1
    }
}
