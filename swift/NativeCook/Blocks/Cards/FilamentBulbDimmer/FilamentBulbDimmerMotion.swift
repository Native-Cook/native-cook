import Foundation

enum FilamentBulbDimmerMotion {
    static let LOOP=10.0, SWING_W=2*Double.pi/1.6, SWING_Z=0.14, TWIST_W=2*Double.pi/2.4, TWIST_Z=0.12
    static let PULL_W=22.0, PULL_Z=0.32, GRAV=3400.0, WARM=0.45, COOL=0.22
    static let off=8.12+0.3*(1-cbrt(1-16.0/24))
    static let colours=[(4.8,1.0),(5.8,4.0),(6.8,2.0)]
    struct Frame {
        var th=0.0,s=0.0,tw=0.0,on=1.0,heat=0.78,level=0.78,lead=2.0,trail=2.0
        var finger=0.0,fingerAt=0,fingerDot=0.0,fingerPress=0.0
        var time=0.0,tog=1.0,shownPct=78.0,knobPress=0.0
        var colour: Double { (lead+trail)/2 }
    }
    static func clamp01(_ x: Double)->Double { max(0,min(1,x)) }
    static func seg(_ t: Double,_ a: Double,_ b: Double)->Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double)->Double { x<0.5 ? 4*x*x*x:1-pow(-2*x+2,3)/2 }
    static func easeOut(_ x: Double)->Double { 1-pow(1-x,3) }
    static func smooth(_ x: Double)->Double { x*x*(3-2*x) }
    static func warmUp(_ x: Double)->Double { easeOut(x)*(1-0.14*sin(x*38)*(1-x)) }
    static func lum(_ h: Double)->Double { pow(max(0,h),2.1) }
    static func osc(_ t: Double,_ x: Double,_ v: Double,_ w: Double,_ z: Double)->Double {
        let wd=w*sqrt(1-z*z); return exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))
    }
    static func levelAt(_ t: Double)->Double { 0.78-0.6*easeInOut(seg(t,2.2,3.1))+0.82*easeInOut(seg(t,3.35,4.25))-0.22*easeInOut(seg(t,7.35,7.95)) }
    static func frameAt(_ t: Double)->Frame {
        var f=Frame(); f.time=t
        let fade=1-smooth(seg(t,8.8,9.8))
        if t>=0.6 && t<1.3 {
            let x=easeInOut(seg(t,0.6,1.3)); f.th=0.32*x; f.tw=0.55*x; f.s = -8*x
        } else if t>=1.3 {
            let tau=t-1.3,t0=sqrt(16/GRAV)
            f.th=osc(tau,0.32,0,SWING_W,SWING_Z); f.tw=osc(tau,0.55,0,TWIST_W,TWIST_Z)
            f.s=tau<t0 ? -8+0.5*GRAV*tau*tau:osc(tau-t0,0,GRAV*t0,PULL_W,PULL_Z)
        }
        if t>=8.12 && t<8.42 { f.s+=24*easeOut(seg(t,8.12,8.42)) }
        else if t>=8.42 { f.s+=osc(t-8.42,24,0,PULL_W,PULL_Z*1.1) }
        f.th=f.th*fade+0.004*sin(2*Double.pi*t/LOOP)+0.002*sin(4*Double.pi*t/LOOP+1)
        f.tw=f.tw*fade+0.16*sin(2*Double.pi*t/LOOP); f.s*=fade
        f.level=levelAt(t)
        var from=2.0
        for (at,to) in colours {
            f.lead+=(to-from)*easeOut(seg(t,at+0.03,at+0.36)); f.trail+=(to-from)*easeInOut(seg(t,at+0.08,at+0.62)); from=to
        }
        f.on=t>=off && t<9.15 ? 0:1
        f.heat=levelAt(t-0.06)
        if t>=off && t<9.15 { f.heat=levelAt(off)*exp(-(t-off)/COOL) }
        else if t>=9.15 { f.heat=levelAt(t)*warmUp(seg(t,9.15,9.6)) }
        let grab=easeOut(seg(t,0.42,0.58))*(1-easeOut(seg(t,1.3,1.46)))
        if grab>f.finger { f.finger=grab; f.fingerAt=0; f.fingerPress=grab }
        for (a,b) in [(2.2,3.1),(3.35,4.25),(7.35,7.95)] {
            let p=easeOut(seg(t,a-0.22,a-0.06))*(1-easeOut(seg(t,b+0.05,b+0.22)))
            if p>f.finger { f.finger=p; f.fingerAt=1; f.fingerPress=p }
        }
        for (at,to) in colours {
            let p=easeOut(seg(t,at-0.18,at-0.04))*(1-easeOut(seg(t,at+0.14,at+0.3)))
            if p>f.finger { f.finger=p; f.fingerAt=2; f.fingerDot=to; f.fingerPress=easeOut(seg(t,at-0.04,at+0.05))*(1-easeOut(seg(t,at+0.1,at+0.22))) }
        }
        let pull=easeOut(seg(t,7.94,8.09))*(1-easeOut(seg(t,8.42,8.58)))
        if pull>f.finger { f.finger=pull; f.fingerAt=4; f.fingerPress=pull }
        let tg=easeOut(seg(t,8.97,9.11))*(1-easeOut(seg(t,9.29,9.45)))
        if tg>f.finger { f.finger=tg; f.fingerAt=3; f.fingerPress=easeOut(seg(t,9.11,9.2))*(1-easeOut(seg(t,9.25,9.37))) }
        f.tog=f.on; f.shownPct=(f.level*100).rounded(); return f
    }
    static func bezier(_ x: Double,_ x1: Double,_ y1: Double,_ x2: Double,_ y2: Double)->Double {
        func b(_ t:Double,_ a:Double,_ c:Double)->Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t }
        var lo=0.0,hi=1.0
        for _ in 0..<30 { let t=(lo+hi)/2; if b(t,x1,x2)<clamp01(x) { lo=t } else { hi=t } }
        return b((lo+hi)/2,y1,y2)
    }
}
