import SwiftUI

final class SelfieVerifyBadgeDriver: ObservableObject {
    typealias M = SelfieVerifyBadgeMotion
    struct Track {
        var from=0.0, to=0.0, start=0.0, duration=0.0
        var curve=0, velocity=0.0, stiffness=0.0, suppliedVelocity=0.0
        func value(_ now: Double) -> Double {
            let t=now-start
            if t<=0 { return from }
            if curve==4 {
                let z=0.45, w=sqrt(stiffness/4), wd=w*sqrt(1-z*z), dx=from-to
                let env=exp(-z*w*t), a=velocity+z*w*dx
                let displacement=env*(dx*cos(wd*t)+a/wd*sin(wd*t))
                let speed = -z*w*displacement+env*(a*cos(wd*t)-wd*dx*sin(wd*t))
                let energy=0.5*stiffness*displacement*displacement+2*speed*speed
                let initialEnergy=0.5*stiffness*dx*dx+2*suppliedVelocity*suppliedVelocity
                return initialEnergy==0 || energy/initialEnergy<=6e-9 ? to : to+displacement
            }
            if duration<=0 || t>=duration { return to }
            let x=M.clamp01(t/duration)
            let u: Double
            switch curve {
            case 1: u=M.bezier(x)
            case 2: u=x<0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2
            case 3: u=M.bezier(x,0.23,1,0.32,1)
            default: u=x
            }
            return M.mix(from,to,u)
        }
    }
    @Published var revision=0
    let origin=Date.timeIntervalSinceReferenceDate
    var start=0.0, lastTouch = -100.0, manual=false
    var tracks=[String:Track]()
    var captureTime: Double? = nil
    var captureWait=0.0
    var now: Double { Date.timeIntervalSinceReferenceDate-origin }
    func track(_ name: String,_ time: Double) -> Double { tracks[name]?.value(time) ?? (name=="scan" ? -9 : 0) }
    func animate(_ name: String,_ to: Double,_ duration: Double,_ time: Double,_ curve: Int=0,_ velocity: Double=0) {
        let from=track(name,time)
        let effectiveVelocity=(from-to)*velocity>0 ? 0 : velocity
        tracks[name]=Track(from:from,to:to,start:time,duration:duration,curve:curve,
                           velocity:effectiveVelocity,stiffness:curve==4 ? M.releaseStiffness(from-to,effectiveVelocity) : 0,
                           suppliedVelocity:velocity)
    }
    func raw(_ time: Double) -> M.Frame {
        var f=M.Frame(link:track("link",time),detected:track("detected",time),scan:track("scan",time),
                      flash:track("flash",time),shutter:track("shutter",time),yaw:track("yaw",time),spin:track("spin",time),fade:track("fade",time))
        if let t=captureTime {
            let dt=time-t
            if captureWait>0 { f.scan=dt<0.52 ? M.mix(-0.8,0.95,M.bezier(M.clamp01(dt/0.52))) : -9 }
            let u=dt-captureWait
            if u>=0 { f.flash=u<0.06 ? u/0.06 : 1-M.clamp01((u-0.06)/0.26) }
        }
        return f
    }
    func frame(_ time: Double, auto: Bool, reduced: Bool) -> M.Frame {
        if auto && !reduced {
            if !manual {
                var t=(time-start).truncatingRemainder(dividingBy:M.loop)
                #if DEBUG
                if UserDefaults.standard.object(forKey:"ncPinTime") != nil { t=UserDefaults.standard.double(forKey:"ncPinTime") }
                #endif
                return M.frameAt(t)
            }
            let elapsed=time-lastTouch
            if elapsed>3.4 { var f=M.frameAt((elapsed-3.4).truncatingRemainder(dividingBy:M.loop));f.spin=track("spin",time);return f }
            if elapsed>2 {
                var f=raw(lastTouch+2)
                let u=elapsed-2
                f.link *= 1-M.bezier(M.clamp01(u/(f.link>0.01 ? 1.3 : 0.0001)))
                f.detected *= 1-(u<0.2 ? 2*pow(u/0.4,2) : 1-pow(-2*M.clamp01(u/0.4)+2,2)/2)
                f.yaw *= 1-M.bezier(M.clamp01(u/0.7));return f
            }
        }
        return raw(time)
    }
    func drawingTime(_ time: Double, auto: Bool, reduced: Bool) -> Double {
        #if DEBUG
        if auto && !reduced && !manual && UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
        #endif
        return time
    }
    func isManual(_ time: Double, auto: Bool, reduced: Bool) -> Bool { manual && (!auto || reduced || time-lastTouch<=3.4) }
    func takeOver(_ time: Double, auto: Bool, reduced: Bool) {
        let returnStart=lastTouch+2
        let returning=auto && !reduced && manual && time-lastTouch>2 && time-lastTouch<3.4
        let returnFrame=returning ? raw(returnStart) : nil
        let f=frame(time,auto:auto,reduced:reduced)
        let pairs:[String:Double] = ["link":f.link,"detected":f.detected,"scan":f.scan,"flash":f.flash,"shutter":f.shutter,"yaw":f.yaw,"spin":f.spin,"fade":f.fade]
        if !manual || time-lastTouch>2 { for (key,v) in pairs { tracks[key]=Track(from:v,to:v) };captureTime=nil }
        // Touching during the resume keeps its already-running return animations,
        // just as resetting resumeAsked in RN does not cancel withTiming.
        if let base=returnFrame {
            tracks["link"]=Track(from:base.link,to:0,start:returnStart,duration:base.link>0.01 ? 1.3 : 0,curve:1)
            tracks["detected"]=Track(from:base.detected,to:0,start:returnStart,duration:0.4,curve:2)
            tracks["yaw"]=Track(from:base.yaw,to:0,start:returnStart,duration:0.7,curve:1)
        }
        manual=true;lastTouch=time;revision+=1
    }
    func capture(auto: Bool,reduced: Bool) {
        let t=now;takeOver(t,auto:auto,reduced:reduced)
        guard track("link",t)<=0.02 else {return}
        UIImpactFeedbackGenerator(style:.medium).impactOccurred()
        if reduced { animate("detected",1,0,t);animate("fade",1,0.28,t,2);animate("link",1,0,t);return }
        captureWait=track("detected",t)>0.95 ? 0 : 0.7
        captureTime=t
        if captureWait>0 { animate("detected",1,0.22,t,2) }
        animate("link",1,3.2,t+captureWait)
    }
    func back(auto: Bool,reduced: Bool) {
        let t=now;takeOver(t,auto:auto,reduced:reduced);captureTime=nil
        animate("link",0,reduced ? 0 : 1.3,t,1);animate("detected",0,reduced ? 0 : 0.4,t,2)
        if reduced {animate("fade",0,0.28,t,2)}
    }
    func turn(_ state: UIGestureRecognizer.State,_ delta: CGSize,_ velocity: CGVector,auto: Bool,reduced: Bool) {
        let t=now
        if state == .began { takeOver(t,auto:auto,reduced:reduced) }
        if state == .changed { lastTouch=t;animate("yaw",min(1.1,max(-1.1,delta.width/120)),0,t) }
        if state == .ended || state == .cancelled {
            lastTouch=t
            if state == .ended && abs(delta.width)<8 && abs(delta.height)<8 && track("link",t)>=0.9 {
                tracks["spin"]=Track(from:0,to:Double.pi*2,start:t,duration:reduced ? 0 : 0.9,curve:3)
                UIImpactFeedbackGenerator(style:.light).impactOccurred()
            }
            animate("yaw",0,reduced ? 0 : 0.7,t,reduced ? 0 : 4,velocity.dx/120)
        }
        revision+=1
    }
}
