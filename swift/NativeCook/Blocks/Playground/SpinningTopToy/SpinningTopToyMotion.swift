import Foundation

// Faithful port of stt-motion.ts. Times are seconds; touch velocity is points/second.
struct SpinningTopToyFrame {
    var x = 203.7, y = 603.5, angle = 0.0, spin = 0.0, speed = 0.0, glow = 0.35
    var alpha = 1.0, fall = 0.0, trail = [SIMD4<Float>](repeating: .zero, count: 24)
    var seconds = 0.0, down = false, pill = false, best = 5.4, bestPop = 1.0
}

final class SpinningTopToyMotion {
    static let loop = 7.6
    static let fallSpeed = 70 * exp(-0.32 * 5.2)
    var mode = 0, phase = 0, last = -1.0, start = -1.0, still = false
    var w0 = 70.0, duration = 5.2, tau = -1.0, nudge = 0.0, resetTime = 0.0, flickAfter = 0.0
    var pill = false, best = 5.4, bestAge = 1000.0, touches = 0, idle = 0.0
    var flicks = 0, lands = 0, landed = false
    var frame = SpinningTopToyFrame()
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func io(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func durationOf(_ w: Double) -> Double { log(max(w, fallSpeed)/fallSpeed)/0.32 }
    static func speed(_ t: Double, _ w: Double, _ d: Double) -> Double {
        if t < 0 { return 0 }; if t < d { return w*exp(-0.32*t) }
        return t < d+0.35 ? fallSpeed*(1-seg(t,d,d+0.35)) : 0
    }
    static func spin(_ t: Double, _ w: Double, _ d: Double) -> Double {
        if t < 0 { return 0 }
        var a = w/0.32*(1-exp(-0.32*min(t,d)))
        if t > d { let u = min(t,d+0.35)-d; a += fallSpeed*(u-u*u/0.7) }
        return a
    }
    static func wander(_ t: Double, _ d: Double) -> (Double, Double) {
        let tt = min(max(t,0),d), r = 14+58*clamp(t/d), a = 1.3*tt+0.9
        return (r*(cos(a)-cos(0.9)), 0.28*r*(sin(a)-sin(0.9)))
    }
    static func pose(_ t: Double, _ d: Double, _ still: Bool) -> (Double, Double, Double) {
        if t < 0 || still { return (203.7,603.5,0) }
        let (x,y) = wander(min(t,d),d)
        if t < d {
            let m = 0.035+0.3*pow(clamp(t/d),3), psi = 2*Double.pi*(1.1*t+0.55*t*t/d)
            return (203.7+x,603.5+y,m*sin(psi))
        }
        let a0 = (0.035+0.3*pow((d-0.001)/d,3))*sin(2*Double.pi*(1.1*d+0.55*d))
        let f = seg(t,d,d+0.42)
        var a = a0+(-0.745-a0)*f*f
        if t > d+0.42 { let u = t-d-0.42; a = -0.745+0.07*exp(-u*7)*cos(u*24) }
        return (203.7+x+46*io(f),603.5+y,a)
    }
    static func glow(_ t: Double, _ w: Double, _ d: Double) -> Double {
        if t < 0 { return 0.35 }
        if t < d { return min(1,0.35+0.65*min(1,t/0.15)*(0.55+0.45*speed(t,w,d)/70)) }
        if t < d+0.42 { return 0.7 }
        return 0.7-0.52*io(seg(t,d+0.42,d+1.02))
    }
    static func run(_ t: Double, _ w: Double, _ d: Double, _ still: Bool,
                    _ alpha: Double = 1, _ trailFade: Double = 1, _ nudge: Double = 0) -> SpinningTopToyFrame {
        var f = SpinningTopToyFrame()
        let (x,y,a) = pose(t,d,still)
        f.x=x; f.y=y; f.angle = still ? 0 : a+(t >= 0 && t < d ? nudge : 0)
        f.spin = still ? 0 : spin(t,w,d); f.speed = still ? 0 : speed(t,w,d)
        f.glow=glow(t,w,d); f.alpha=alpha; f.fall=t >= d && !still ? clamp(f.angle / -0.745)*alpha : 0
        f.seconds=t < 0 ? 0 : min(t,d); f.down=t >= d
        for i in 0..<24 {
            let ta=t-Double(i+1)*0.09
            if ta >= 0 && trailFade > 0 && !still {
                let (px,py,_) = pose(min(ta,d),d,false)
                f.trail[i]=SIMD4(Float(px),Float(py),Float((1-Double(i)/24)*min(1,speed(ta,w,d)/20)*trailFade),0)
            }
        }
        return f
    }
    static func loopFrame(_ t: Double) -> SpinningTopToyFrame {
        let tr=t >= 7.35 ? 0 : t
        let alpha=t < 7 ? 1 : t < 7.25 ? 1-seg(t,7,7.25) : seg(t,7.35,7.6)
        let fade=tr >= 7.25 ? 0 : tr < 7 ? 1 : 1-seg(tr,7,7.25)
        var f=run(tr-0.4,70,5.2,false,alpha,fade)
        f.down=tr >= 0.4+5.2 && tr < 7.25; f.pill=t > 0.25 && t < 0.45
        return f
    }
    func takeOver() {
        guard mode == 0 else { return }
        let t=start < 0 ? 0 : ((last-start)/1000).truncatingRemainder(dividingBy: Self.loop)
        mode=1; w0=70; duration=5.2; nudge=0; landed=false
        if t < 0.4 || t >= 7.35 { phase=0; tau = -1 }
        else if t < 7 { phase=1; tau=t-0.4; landed=tau >= duration+0.42 }
        else { phase=2; tau=min(t,7.25)-0.4; resetTime=t-7 }
    }
    func flick(_ w: Double) {
        phase=1; w0=min(90,max(40,w)); duration=Self.durationOf(w0); tau=0; nudge=0; landed=false; flicks += 1
    }
    func spin(_ speed: Double = 0, onTop: Bool = false) {
        takeOver(); idle=0
        let w=speed > 0 ? speed*0.05 : 70
        if phase == 0 { flick(w) }
        else if phase == 1 && tau < duration { if onTop && speed <= 0 { nudge += 0.05 } }
        else if phase == 1 { phase=2; resetTime=0; flickAfter=w }
        else { flickAfter=w }
    }
    func touchDown(pill: Bool = false) { takeOver(); touches += 1; idle=0; if pill { self.pill=true } }
    func touchUp() { touches=max(0,touches-1); pill=false; idle=0 }
    func liveFrame() -> SpinningTopToyFrame {
        var f: SpinningTopToyFrame
        if phase == 2 {
            let r=resetTime, standing=r >= 0.35
            let alpha=r < 0.25 ? 1-r/0.25 : standing ? Self.clamp((r-0.35)/0.25) : 0
            f=Self.run(standing ? -1 : tau,w0,duration,still,alpha,standing ? 0 : 1-Self.clamp(r/0.25))
            f.down = !standing
        } else { f=Self.run(tau,w0,duration,still,1,1,nudge) }
        f.pill=pill; f.best=best; f.bestPop=bestAge < 0.3 ? 1+0.12*sin(Double.pi*bestAge/0.3) : 1
        return f
    }
    func step(_ secondsNow: Double, auto: Bool) {
        let now=secondsNow*1000
        let dt=last < 0 ? 0 : min(max((now-last)/1000,0),0.05); last=now
        if mode == 0 {
            if auto && !still {
                if start < 0 { start=now }
                frame=Self.loopFrame(((now-start)/1000).truncatingRemainder(dividingBy: Self.loop)); return
            }; takeOver()
        }
        bestAge += dt; nudge *= exp(-3*dt)
        if phase == 1 {
            tau += dt
            if !landed && tau >= duration+0.42 {
                landed=true; lands += 1
                if duration > best { best=(duration*10).rounded()/10; bestAge=0 }
            }
        } else if phase == 2 {
            resetTime += dt
            if resetTime >= 0.6 {
                phase=0; tau = -1
                if flickAfter > 0 { let w=flickAfter; flickAfter=0; flick(w) }
                else if auto && !still { mode=0; start=now; frame=Self.loopFrame(0); return }
            }
        }
        frame=liveFrame()
        let settled=phase == 0 || (phase == 1 && tau >= duration+1.02)
        if touches > 0 || !settled { idle=0; return }
        idle += dt
        if !auto || idle < 2 { return }
        if phase == 1 { phase=2; resetTime=0 }
        else if !still { mode=0; start=now; frame=Self.loopFrame(0) }
    }
    func setStill(_ value: Bool) {
        if value == still { return }; still=value
        if value { takeOver() } else if mode == 1 { idle=2 }
    }
}
