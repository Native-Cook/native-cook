import SwiftUI
import UIKit

// Touch animations use an explicit wall-clock sampler so simultaneous values cannot drift.
final class GlassTilesFuseDriver: ObservableObject {
    typealias M = GlassTilesFuseMotion
    enum Key: CaseIterable { case link, press, dragX, dragY, lift, pull, pullY, recoil, stick, finger }
    enum Curve { case defaultTiming, out, quadOut, inOut, connect, spring(Double, Double) }
    struct Step {
        var from: Double, to: Double, start: Double, duration: Double, curve: Curve
        var spring: GlassTilesFuseSpring?
        init(from: Double,to: Double,start: Double,duration: Double,curve: Curve) {
            self.from=from; self.to=to; self.start=start; self.duration=duration; self.curve=curve
            if case let .spring(zeta,velocity)=curve { spring=GlassTilesFuseSpring(from:from,to:to,duration:duration,zeta:zeta,velocity:velocity) }
        }
        var end: Double { start+duration*(spring == nil ? 1:1.5) }
        func value(_ now: Double) -> Double {
            if let spring { return spring.sample(now-start).position }
            let t=M.clamp01((now-start)/duration)
            if t<=0 { return from }; if t>=1 { return to }
            let f: Double
            switch curve {
            case .defaultTiming: f=t<0.5 ? 2*t*t:1-pow(-2*t+2,2)/2
            case .out: f=M.easeOut(t)
            case .quadOut: f=1-pow(1-t,2)
            case .inOut: f=M.easeInOut(t)
            case .connect: f=GlassTilesFuseDriver.bezier(t,0.77,0,0.175,1)
            case .spring: f=t // Handled by the cached spring above.
            }
            return M.mix(from,to,f)
        }
    }
    struct Frame {
        var link=0.0, press=0.0, dragX=0.0, dragY=0.0, lift=0.0
        var pull=0.0, pullY=0.0, recoil=0.0, stick=1.0, finger=0.0, fingerOn=0
        var restTime=0.0
    }
    @Published var revision=0
    private let epoch=Date().timeIntervalSinceReferenceDate
    private var manual=false, lastTouch = -100.0, holding=false, pulling=false
    private var plans: [Key:[Step]]=[:]
    private var base: [Key:Double]=[.stick:1]
    private var lastBand=0
    private var fingerOn=0
    var now: Double { Date().timeIntervalSinceReferenceDate-epoch }
    static func bezier(_ x: Double,_ x1: Double,_ y1: Double,_ x2: Double,_ y2: Double) -> Double {
        func at(_ t: Double,_ a: Double,_ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo=0.0,hi=1.0
        for _ in 0..<24 { let mid=(lo+hi)/2; if at(mid,x1,x2)<x { lo=mid } else { hi=mid } }
        return at((lo+hi)/2,y1,y2)
    }
    func value(_ key: Key,_ t: Double) -> Double {
        guard let steps=plans[key],!steps.isEmpty else { return base[key] ?? 0 }
        var v=base[key] ?? 0
        for step in steps { if t<step.start { break }; v=step.value(t) }
        return v
    }
    func set(_ key: Key,_ v: Double) { base[key]=v; plans[key]=nil }
    func animate(_ key: Key,_ to: Double,_ duration: Double,_ curve: Curve,_ delay: Double=0) {
        let t=now
        var nextCurve=curve
        if case let .spring(zeta,velocity)=curve {
            let inherited=plans[key]?.last.flatMap { $0.spring?.sample(t-$0.start).velocity } ?? 0
            nextCurve = .spring(zeta,velocity+inherited)
        }
        let step=Step(from:value(key,t),to:to,start:t+delay,duration:duration,curve:nextCurve)
        plans[key]=[step]
        wake(after:step.end-t)
    }
    func frame(_ t: Double, auto: Bool, reduced: Bool, pin: Double?) -> Frame {
        var f=Frame(); f.restTime=t.truncatingRemainder(dividingBy:M.loop)
        let resumed=manual && value(.lift,t)<0.01 && t-lastTouch>3.2 && auto && !reduced
        if auto && !reduced && (!manual || resumed) {
            let elapsed=pin ?? (resumed ? t-lastTouch-3.2 : t)
            let lt=elapsed.truncatingRemainder(dividingBy:M.loop)
            f.link=M.linkAt(lt); f.press=max(M.pressAt(lt,M.T.press),M.pressAt(lt,M.T.done))
            f.pull=M.pullAt(lt); f.recoil=M.recoilAt(lt); f.stick=M.stickAt(lt)
            let fg=M.fingerAt(lt); f.finger=fg.finger; f.fingerOn=fg.at
            if let pin { f.restTime=pin.truncatingRemainder(dividingBy:M.loop) }
            return f
        }
        f.link=value(.link,t); f.press=value(.press,t); f.dragX=value(.dragX,t); f.dragY=value(.dragY,t)
        f.lift=value(.lift,t); f.pull=value(.pull,t); f.pullY=value(.pullY,t); f.recoil=value(.recoil,t)
        f.stick=value(.stick,t); f.finger=value(.finger,t); f.fingerOn=fingerOn
        if auto && !reduced && manual && value(.lift,t)<0.01 && t-lastTouch>2 {
            let a=M.clamp01((t-lastTouch-2)/1.1)
            f.link=M.mix(value(.link,lastTouch+2),0,Self.bezier(a,0.77,0,0.175,1)); f.stick=1
            f.dragX=GlassTilesFuseSpring(from:value(.dragX,lastTouch+2),to:0,duration:0.5,zeta:0.8,velocity:0).sample(t-lastTouch-2).position
            f.dragY=GlassTilesFuseSpring(from:value(.dragY,lastTouch+2),to:0,duration:0.5,zeta:0.8,velocity:0).sample(t-lastTouch-2).position
        }
        return f
    }
    private func wake(after delay: Double) {
        DispatchQueue.main.asyncAfter(deadline:.now()+delay+0.01) { [weak self] in self?.revision += 1 }
    }
    func needsFrames(auto: Bool,reduced: Bool) -> Bool {
        if auto && !reduced { return true }
        return holding || plans.values.contains { ($0.last.map { now < $0.end }) ?? false }
    }
    func takeOver(auto: Bool,reduced: Bool,pin: Double?) {
        let f=frame(now,auto:auto,reduced:reduced,pin:pin)
        if !manual || (auto && now-lastTouch>3.2 && value(.lift,now)<0.01) {
            for (k,v) in [(Key.link,f.link),(.press,f.press),(.dragX,f.dragX),(.dragY,f.dragY),(.lift,f.lift),(.pull,f.pull),(.pullY,f.pullY),(.recoil,f.recoil),(.stick,f.stick),(.finger,f.finger)] { set(k,v) }
            animate(.finger,0,0.15,.defaultTiming)
        }
        fingerOn=f.fingerOn
        manual=true; lastTouch=now; revision += 1
    }
    func press(_ down: Bool) { animate(.press,down ? 1:0,down ? 0.1:0.18,.defaultTiming); revision += 1 }
    func button(auto: Bool,reduced: Bool,pin: Double?) {
        takeOver(auto:auto,reduced:reduced,pin:pin)
        let t=now, k=value(.link,t)
        if reduced { set(.link,k>0.5 ? 0:1); revision += 1; return }
        set(.stick,1)
        if k>0.5 { animate(.link,0,1.15,.connect) }
        else {
            UIImpactFeedbackGenerator(style:.light).impactOccurred()
            plans[.link]=[Step(from:k,to:0.5,start:t,duration:1.6,curve:.connect),Step(from:0.5,to:1,start:t+2.5,duration:0.9,curve:.inOut)]
            wake(after:3.4)
        }
        revision += 1
    }
    func event(_ state: UIGestureRecognizer.State,_ translation: CGSize,_ velocity: CGVector,auto: Bool,reduced: Bool,pin: Double?) {
        if state == .began {
            takeOver(auto:auto,reduced:reduced,pin:pin); holding=true
            let k=value(.link,now)
            if !reduced && k>0.4 && k<0.6 && value(.stick,now)>0.99 { animate(.link,0.5,0.2,.defaultTiming); pulling=true }
            else if k<=0.02 { animate(.lift,1,0.26,.spring(0.8,0)) }
        } else if state == .changed {
            lastTouch=now
            if pulling {
                let px=translation.width>0 ? translation.width:0.25*translation.width
                set(.pull,px); set(.pullY,0.4*translation.height); set(.recoil,M.tension*max(0,px))
                if hypot(M.fusedW+M.fusedG+px-value(.recoil,now),value(.pullY,now))>M.snapDist {
                    pulling=false
                    animate(.stick,0,0.14,.quadOut); animate(.recoil,0,0.65,.spring(0.35,0))
                    animate(.link,1,0.8,.out); animate(.pull,0,0.8,.spring(0.9,velocity.dx)); animate(.pullY,0,0.6,.spring(0.8,0))
                    UIImpactFeedbackGenerator(style:.medium).impactOccurred()
                }
            } else if value(.link,now)<=0.02 { set(.dragX,translation.width); set(.dragY,translation.height) }
        } else if state == .ended || state == .cancelled || state == .failed {
            lastTouch=now; holding=false
            if pulling {
                pulling=false
                animate(.pull,0,0.6,.spring(0.55,velocity.dx)); animate(.pullY,0,0.6,.spring(0.55,0)); animate(.recoil,0,0.6,.spring(0.55,0))
                animate(.link,1,0.9,.inOut,0.9)
            } else {
                animate(.lift,0,0.42,.spring(0.8,0)); animate(.dragX,0,0.52,.spring(0.62,velocity.dx)); animate(.dragY,0,0.52,.spring(0.62,velocity.dy))
            }
        }
        revision += 1
    }
    func isAutomatic(auto: Bool,reduced: Bool) -> Bool { auto && !reduced && (!manual || (value(.lift,now)<0.01 && now-lastTouch>3.2)) }
    func haptics(_ k: Double, feedback: Bool) {
        let band=k>0.86 ? 2:k>0.4 ? 1:0
        if feedback && band>lastBand { if band==2 { UINotificationFeedbackGenerator().notificationOccurred(.success) } else { UIImpactFeedbackGenerator(style:.light).impactOccurred() } }
        lastBand=band
    }
}
