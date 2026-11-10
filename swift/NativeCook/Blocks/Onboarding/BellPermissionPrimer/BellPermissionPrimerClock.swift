import Foundation
import SwiftUI
import UIKit

final class BellPermissionPrimerClock: ObservableObject {
    typealias M=BellPermissionPrimerMotion
    @Published var running=false
    private var date:Date?
    private var now=0.0, autoStart=0.0, lastTouch = -100.0
    private var automatic=false, resumeAsked=false, dragging=false, lifting=false, beganPan=false
    private var f=M.Frame(), swingV=0.0, clapV=0.0, dragFrom=0.0
    private var ringStart=[Double](repeating:-100,count:3), ringNext=0
    private var animations:[String:BellPermissionPrimerTween]=[:]
    private var delayedPrevious:[String:BellPermissionPrimerTween]=[:]
    private var pending:[BellPermissionPrimerEvent]=[]
    private var touchReduced=false
    private let audio=BellPermissionPrimerAudio()
    private let light=UIImpactFeedbackGenerator(style:.light)
    private let success=UINotificationFeedbackGenerator()
    struct Sample { var frame:M.Frame; var t:Double }
    private struct BellPermissionPrimerEvent { var at:Double; var kind:Int }
    private struct BellPermissionPrimerTween {
        var from:Double, to:Double, at:Double, duration:Double, curve:Int
        var spring:M.Spring?
        func value(_ t:Double) -> Double {
            if let spring { return spring.sample(max(0,t-at)).value }
            let p=M.seg(t,at,at+max(duration,0.000001))
            let k:Double
            switch curve {
            case 1: k=M.bezier(p,0.77,0,0.175,1)
            case 2: k=M.bezier(p,0.23,1,0.32,1)
            case 3: k=p*p
            case 4: k=M.bezier(p,0.3,1.45,0.5,1)
            default: k=p<0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
            }
            return from+(to-from)*k
        }
        func finished(_ t:Double) -> Bool {
            guard t>=at else { return false }
            return spring?.sample(t-at).finished ?? (t>=at+duration)
        }
    }
    private func get(_ key:String) -> Double {
        switch key {
        case "swing":return f.swing; case "clap":return f.clap; case "liftX":return f.liftX
        case "liftY":return f.liftY; case "suck":return f.suck; case "expand":return f.expand
        case "finger":return f.finger; case "bob":return f.bob; default:return 0
        }
    }
    private func set(_ key:String,_ v:Double) {
        switch key {
        case "swing":f.swing=v; case "clap":f.clap=v; case "liftX":f.liftX=v
        case "liftY":f.liftY=v; case "suck":f.suck=v; case "expand":f.expand=v
        case "finger":f.finger=v; case "bob":f.bob=v; default:break
        }
    }
    private func animate(_ key:String,_ to:Double,_ duration:Double,_ curve:Int=0,_ delay:Double=0,velocity:Double=0) {
        // withDelay keeps the previous animation alive until the delayed animation starts.
        if delay>0,let previous=animations[key] { delayedPrevious[key]=previous }
        else { delayedPrevious.removeValue(forKey:key) }
        let from=get(key)
        let spring:M.Spring?
        switch curve {
        case 5,6: spring=M.Spring(from:from,to:to,velocity:velocity,zeta:curve==5 ? 0.45:0.6)
        case 7: spring=M.Spring(from:from,to:to,zeta:M.openZeta,omega:M.openOmega)
        default: spring=nil
        }
        animations[key] = .init(from:from,to:to,at:now+delay,duration:duration,curve:curve,spring:spring)
    }
    func reset(_ auto:Bool,_ reduced:Bool) {
        date=nil; now=0; autoStart=0; lastTouch = -100; automatic=auto && !reduced
        f=M.Frame(); swingV=0; clapV=0; dragging=false; lifting=false; beganPan=false
        resumeAsked=false; animations.removeAll(); delayedPrevious.removeAll(); pending.removeAll()
        ringStart=[Double](repeating:-100,count:3); ringNext=0; running=automatic
    }
    func active(_ active:Bool) { date=nil; if !active { audio.stop(); dragging=false; lifting=false } }
    func configure(_ auto:Bool,_ reduced:Bool) {
        if auto && !reduced { reset(auto,reduced); return }
        automatic=false; animations.removeAll(); delayedPrevious.removeAll(); pending.removeAll()
        let isOpen=f.expand>0.5
        for (key,to) in [("expand",isOpen ? 1.0:0.0),("suck",isOpen ? 1.0:0.0),("liftX",0),("liftY",0),("swing",0),("clap",0)] { animate(key,to,0.2) }
        swingV=0; clapV=0; f.bob=0; f.finger=0; f.fingerPress=0; f.rings = .zero
        ringStart=[Double](repeating:-100,count:3); date=nil; running=true
    }
    func sample(_ at:Date,_ auto:Bool,_ reduced:Bool,_ pinned:Double?) -> Sample {
        if let t=pinned { return .init(frame:M.frameAt(t),t:t) }
        // The recording clock follows wall time even on slow, GPU-less simulators.
        let dt=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
        date=at; let before=now; now += dt
        if automatic && auto && !reduced {
            let old=(before-autoStart).truncatingRemainder(dividingBy:M.loop)
            let t=(now-autoStart).truncatingRemainder(dividingBy:M.loop)
            f=M.frameAt(t)
            if DemoSound.inAutoPlay {
                for (beat,cue) in [(1.42,"ding"),(1.94,"ding"),(2.47,"ding"),(4.75,"allow"),(5.05,"banner")] {
                    if (old<beat && t>=beat) || (t<old && (beat>old || beat<=t)) { audio.play(cue) }
                }
            }
            return .init(frame:f,t:t)
        }
        for (key,scheduled) in animations {
            var a=scheduled
            if now<a.at {
                if let previous=delayedPrevious[key],now>=previous.at { set(key,previous.value(now)) }
                continue
            }
            if let previous=delayedPrevious.removeValue(forKey:key) {
                a.from=previous.value(a.at)
                if let spring=a.spring { a.spring=M.Spring(from:a.from,to:a.to,velocity:spring.configuredVelocity,zeta:spring.zeta,omega:spring.omega) }
                animations[key]=a
            }
            set(key,a.value(now))
            if a.finished(now) { animations.removeValue(forKey:key) }
        }
        let due=pending.filter { now >= $0.at }; pending.removeAll { now >= $0.at }
        for e in due {
            if e.kind==1 { audio.play("banner") }
            else if e.kind==2 { swingV += 1.2; clapV += 2 }
        }
        let h=min(0.05,dt)/4, w=2*Double.pi/M.period
        for _ in 0..<4 {
            if !dragging && !lifting && !reduced && animations["swing"] == nil {
                swingV += (-w*w*f.swing-2*0.12*w*swingV)*h
                f.swing += swingV*h
            }
            if animations["clap"] == nil {
                clapV += (-w*w*0.8*f.clap-2*0.1*w*clapV)*h
                f.clap += clapV*h
            }
            let rel=f.clap-f.swing
            if abs(rel)>M.gap {
                let side=rel>0 ? 1.0 : -1.0
                f.clap=f.swing+side*M.gap
                let relV=clapV-swingV
                if relV*side>0 {
                    if relV*side>0.6 { strike() }
                    clapV=swingV-relV*0.35
                }
            }
        }
        for i in 0..<3 where ringStart[i] > -99 {
            let p=(now-ringStart[i])/1.1
            f.rings[i]=p<1 ? 0.001+0.999*p : 0
        }
        if auto && !reduced && !dragging && now-lastTouch>=2 {
            if !resumeAsked {
                resumeAsked=true
                if f.suck>0.001 || f.expand>0.001 { close(false,reduced) }
            } else if f.suck<0.001 && f.expand<0.001 && abs(f.liftY)<0.5 && abs(f.swing)<0.004 && abs(swingV)<0.02 && abs(f.clap)<0.01 && abs(clapV)<0.05 {
                automatic=true; resumeAsked=false; autoStart=now
            }
        }
        let moving=automatic || dragging || !animations.isEmpty || !pending.isEmpty || (auto && !reduced) || abs(swingV)>0.00001 || abs(clapV)>0.00001 || (!reduced && (abs(f.swing)>0.00001 || abs(f.clap)>0.00001)) || ringStart.contains { now >= $0 && now<$0+1.1 }
        if running != moving { DispatchQueue.main.async { [weak self] in self?.running=moving } }
        return .init(frame:f,t:now.truncatingRemainder(dividingBy:M.loop))
    }
    private func strike() {
        ringStart[ringNext]=now; ringNext=(ringNext+1)%3
        light.impactOccurred(); audio.play("ding")
    }
    private func open(_ touch:Bool,_ reduced:Bool) {
        if f.suck>0.5 && f.expand>0.5 { return }
        pending.removeAll()
        if touch { audio.play("allow"); success.notificationOccurred(.success) }
        if reduced { animate("suck",1,0.001); animate("expand",1,0.2); return }
        let fly=max(0,1-abs(f.liftY)/M.lift)*0.4
        animate("liftY",-M.lift,fly,1); animate("liftX",0,fly,1)
        animate("suck",1,0.3,3,fly)
        animate("expand",1,0,7,fly+0.3)
        if touch { pending.append(.init(at:now+fly+0.3,kind:1)) }
    }
    private func close(_ touch:Bool,_ reduced:Bool) {
        pending.removeAll(); animations.removeValue(forKey:"liftX"); animations.removeValue(forKey:"liftY")
        delayedPrevious.removeValue(forKey:"liftX"); delayedPrevious.removeValue(forKey:"liftY")
        f.liftX=0; f.liftY=0
        if reduced { animate("expand",0,0.2); animate("suck",0,0.001,0,0.2); return }
        animate("expand",0,0.4,1); animate("suck",0,0.6,4,0.4)
        pending.append(.init(at:now+1,kind:2))
        if touch { light.impactOccurred() }
    }
    private func takeOver(_ reduced:Bool) {
        lastTouch=now; resumeAsked=false
        if automatic {
            automatic=false; animate("finger",0,0.15); f.fingerPress=0; animate("bob",0,0.5,2)
            swingV=0; clapV=0
            if f.suck>0.001 && f.expand<0.999 {
                if f.suck>0.5 { open(false,reduced) } else { close(false,reduced) }
            } else if f.expand>0.001 && f.expand<0.999 { open(false,reduced) }
        }
        date=Date(); running=true
    }
    func allow(_ reduced:Bool) { takeOver(reduced); open(true,reduced) }
    func dismiss(_ reduced:Bool) { takeOver(reduced); close(true,reduced) }
    func begin(_ reduced:Bool) { takeOver(reduced); beganPan=false }
    func drag(_ dx:Double,_ dy:Double,_ vx:Double,_ unit:Double,_ reduced:Bool) {
        lastTouch=now
        if !beganPan {
            beganPan=true
            guard !reduced && f.suck<=0.01 && f.expand<=0.01 else { return }
            dragging=true; dragFrom=f.swing
        }
        if !dragging { return }
        if !lifting && dy < -24 && abs(dy)>abs(dx) { lifting=true }
        if lifting {
            animations.removeValue(forKey:"liftX"); animations.removeValue(forKey:"liftY"); animations.removeValue(forKey:"swing")
            let up = -dy
            f.liftY = -(up<M.lift ? up : M.lift+(up-M.lift)*0.35)
            f.liftX=dx*0.35; f.swing=max(-0.5,min(0.5,vx/4000)); return
        }
        animations.removeValue(forKey:"swing")
        let raw=dragFrom+dx/(unit*1.6), prev=f.swing
        f.swing=(raw<0 ? -1:1)*(abs(raw)/(1+abs(raw)/0.8)); swingV=(f.swing-prev)*60
    }
    func end(_ vx:Double,_ vy:Double,_ unit:Double,_ reduced:Bool) {
        lastTouch=now
        guard dragging else { return }; dragging=false
        if lifting {
            lifting=false
            if -f.liftY>M.lift*0.75 || vy < -900 { open(true,reduced) }
            else {
                let band = -f.liftY<M.lift ? 1.0:0.35
                animate("liftY",0,0.975,5,velocity:vy*band)
                animate("liftX",0,0.975,6,velocity:vx*0.35)
                swingV += vx/1500; clapV += 1.6
            }
        } else { swingV=max(-6,min(6,vx/(unit*1.6))) }
    }
    func tap(_ reduced:Bool,accessibility:Bool=false) {
        takeOver(reduced)
        if !reduced && (accessibility ? f.suck<0.01 : f.suck<=0.01) { swingV += 2.6 }
    }
}
