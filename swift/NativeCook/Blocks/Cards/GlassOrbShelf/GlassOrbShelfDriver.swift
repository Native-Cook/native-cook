import SwiftUI
import UIKit

final class GlassOrbShelfDriver: ObservableObject {
    @Published var revision = 0
    var autoPlay = false
    var reduced = false
    var epoch = Date.timeIntervalSinceReferenceDate
    private var pauseDate: Date?
    private var manual = false
    private var dragging = false
    private var lastTouch = -100.0
    private var held = 0.0
    private var startOffset = 0.0
    private var moveStart = 0.0
    private var moveFrom = 0.0
    private var moveTo = 0.0
    private var moveVelocity = 0.0
    private var moveDuration = 0.42
    private var moveDamping = 0.82
    private var spinBase = 0.0
    private var spinStart = 0.0
    private var tapTime = -100.0
    private var panActive = false
    private var panFailed = false
    var pin: Double? {
        #if DEBUG
        if UserDefaults.standard.object(forKey: "ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
        #endif
        return nil
    }
    func time(_ date: Date) -> Double { date.timeIntervalSinceReferenceDate-epoch }
    private func isAuto(_ t: Double) -> Bool { autoPlay && !reduced && (!manual || (!dragging && t-lastTouch>3)) }
    func loopTime(_ t: Double) -> Double {
        let start = manual ? lastTouch+3 : 0
        return pin ?? max(0,t-start).truncatingRemainder(dividingBy:GlassOrbShelfMotion.loop)
    }
    func offset(_ t: Double) -> Double {
        if isAuto(t) { return GlassOrbShelfMotion.offsetAt(loopTime(t)) }
        if dragging { return held }
        if autoPlay && !reduced && t-lastTouch>2 {
            let homeFrom = spring(lastTouch+2)
            return homeFrom*(1-GlassOrbShelfMotion.bezier(GlassOrbShelfMotion.seg(t,lastTouch+2,lastTouch+2.9),0.77,0,0.175,1))
        }
        return spring(t)
    }
    private func spring(_ t: Double) -> Double {
        if reduced { return moveTo }
        return GlassOrbShelfMotion.spring(t-moveStart,from:moveFrom,to:moveTo,velocity:moveVelocity,duration:moveDuration,damping:moveDamping)
    }
    func spin(_ t: Double) -> Double {
        if reduced { return 0 }
        if isAuto(t) { return loopTime(t)/GlassOrbShelfMotion.loop * .pi*2 }
        return spinBase+(t-spinStart)*0.35 + (tapTime > -99 ? .pi*0.6*GlassOrbShelfMotion.bezier(GlassOrbShelfMotion.seg(t,tapTime,tapTime+0.9),0.23,1,0.32,1) : 0)
    }
    func pop(_ t: Double) -> Double {
        if reduced { return 0 }
        let d=t-tapTime
        if d < 0.09 { let x=max(0,d/0.09); return x<0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2 }
        return GlassOrbShelfMotion.spring(d-0.09,from:1,to:0,duration:0.7,damping:0.3)
    }
    private var leanTime = -1.0
    private var previousOffset = 0.0
    private var filteredLean = 0.0
    func lean(_ t: Double) -> Double {
        if reduced { return 0 }
        if t == leanTime { return filteredLean }
        let dt=min(0.05,max(0.001,leanTime<0 ? 0.016 : t-leanTime))
        let value=offset(t)
        let aim=max(-0.5,min(0.5,-(value-previousOffset)/dt*0.16))
        filteredLean += (aim-filteredLean)*min(1,dt*9)
        previousOffset=value; leanTime=t
        return filteredLean
    }
    func setActive(_ active: Bool) {
        if active {
            if let paused = pauseDate { epoch += Date().timeIntervalSince(paused) }
            pauseDate=nil
        } else if pauseDate == nil { pauseDate=Date() }
    }
    func configure(auto: Bool, reduce: Bool) {
        autoPlay=auto; reduced=reduce; epoch=Date.timeIntervalSinceReferenceDate; pauseDate=nil
        leanTime = -1; previousOffset=0; filteredLean=0
        manual=false; held=0; moveFrom=0; moveTo=0; moveStart=0; tapTime = -100
        revision += 1
    }
    private func takeOver(_ t: Double) {
        let currentOffset=offset(t), currentSpin=spin(t)
        manual=true; lastTouch=t; held=currentOffset; moveFrom=currentOffset; moveTo=currentOffset; moveStart=t
        spinBase=currentSpin; spinStart=t; tapTime = -100
    }
    private func animate(to next: Double, at t: Double, velocity: Double = 0, duration: Double = 0.42, damping: Double = 0.82) {
        moveFrom=held; moveTo=next; moveStart=t; moveVelocity=velocity; moveDuration=duration; moveDamping=damping
        revision += 1
    }
    func adjust(_ direction: Int) {
        let t=time(Date()); takeOver(t)
        animate(to:min(3,max(0,held.rounded()+Double(direction))),at:t)
    }
    func touch(_ state: UIGestureRecognizer.State, point: CGPoint, translation: CGSize, velocity: CGVector, layout: GlassOrbShelfLayout, stripTop: Double) {
        let t=time(Date())
        switch state {
        case .began:
            takeOver(t); dragging=true; startOffset=held; panActive=false; panFailed=false
        case .changed:
            lastTouch=t
            if !panActive && abs(translation.height)>14 { panFailed=true }
            if !panFailed && abs(translation.width)>8 { panActive=true }
            if panActive { held=GlassOrbShelfMotion.rubber(startOffset-translation.width/layout.step) }
        case .ended, .cancelled, .failed:
            dragging=false; lastTouch=t
            if panActive {
                let v = -velocity.dx/layout.step
                animate(to:min(3,max(0,(held+v*0.18).rounded())),at:t,velocity:v)
            } else if state == .ended && !panFailed {
                let x=point.x, y=point.y+stripTop
                let dx=x-layout.orbX, dy=y-layout.orbY
                if dx*dx+dy*dy < layout.orbR*layout.orbR*1.2 {
                    tapTime=t; UIImpactFeedbackGenerator(style:.light).impactOccurred()
                } else if y>=layout.cardTop {
                    let cur=held.rounded()
                    if x>layout.cardLeft+layout.cardW && cur<3 { animate(to:cur+1,at:t,duration:0.48,damping:0.85) }
                    else if x<layout.cardLeft && cur>0 { animate(to:cur-1,at:t,duration:0.48,damping:0.85) }
                }
            }
        default: break
        }
        revision += 1
    }
}
