import Foundation

enum TumbleCardRoutineMotion {
    static let loop = 9.0
    static let rest = [0.35, 2.1, 5.4, 7.45]
    static let sizes: [(Double, Double, Double, Double, Double, Double, Double)] = [
        (250, 286, 14, 163, 342, -5, 1.8), (228, 290, 20, 255, 367, 11, 2.2),
        (222, 270, 20, 205, 362, -6, 1.8), (300, 300, 16, 201, 359, 0, 2)
    ]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func out(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func wrap(_ x: Double) -> Double { (x.truncatingRemainder(dividingBy: loop)+loop).truncatingRemainder(dividingBy: loop) }
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        let cx=3*x1, bx=3*(x2-x1)-cx, ax=1-cx-bx
        let cy=3*y1, by=3*(y2-y1)-cy, ay=1-cy-by
        var lo=0.0, hi=1.0, u=x
        for _ in 0..<30 { let v=((ax*u+bx)*u+cx)*u; if abs(v-x)<0.000001 { break }; if v<x { lo=u } else { hi=u }; u=(lo+hi)/2 }
        return ((ay*u+by)*u+cy)*u
    }
    static func spring(_ t: Double, _ from: Double, _ to: Double, _ velocity: Double, _ damping: Double, _ stiffness: Double) -> Double {
        if t <= 0 { return from }; let w=sqrt(stiffness), z=damping/(2*w), x=from-to
        if z < 1 { let wd=w*sqrt(1-z*z); return to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t)) }
        let s=sqrt(max(0,z*z-1)), r1 = -w*(z-s), r2 = -w*(z+s)
        if abs(r2-r1)<0.000001 { return to+exp(-w*t)*(x+(velocity+w*x)*t) }
        let c2=(velocity-r1*x)/(r2-r1); return to+(x-c2)*exp(r1*t)+c2*exp(r2*t)
    }
    static let pulsePeak: Double = (0..<1000).map { spring(Double($0)/1000,0,0,1,15.1,93.4) }.max() ?? 1
    struct Deck { var pos=0.0, peek=0.0, drop=0.0, ant=0.0, bump=0.0; var bumpIndex = -1; var reveal=[Double](repeating: 0,count: 4); var rate=0.0; var live=false; var clock=0.0; var spin=0.0; var press=0.0; var nod=0.0 }
    static func position(_ t0: Double) -> (Double,Double) {
        let t=wrap(t0); var p=0.0, peek=0.0
        if t<0.8 { p=0 } else if t<1.35 { p=bezier(0.05,0.7,0.1,1,seg(t,0.8,1.35)) }
        else if t<3.45 { p=1 } else if t<4.15 { p=1+mix(0.22,0.55,0.5-0.5*cos(.pi*seg(t,3.45,4.15))) }
        else if t<4.3 { p=1.55 } else if t<4.58 { p=1+mix(0.55,1,bezier(0.55,0,0.25,1,seg(t,4.3,4.58))) }
        else if t<6.1 { p=2 } else if t<6.65 { p=2+bezier(0.05,0.7,0.1,1,seg(t,6.1,6.65)) }
        else if t<7.7 { p=3 } else if t<8.25 { p=3+bezier(0.05,0.7,0.1,1,seg(t,7.7,8.25)) } else { p=4 }
        if t>=2.9 && t<4.58 { peek=t<3.2 ? out(seg(t,2.9,3.2)) : 1 }
        return (p,peek)
    }
    static func frame(_ t0: Double) -> Deck {
        let t=wrap(t0), (p,peek)=position(t); var d=Deck(); d.clock=t; d.pos=p; d.peek=peek
        for (start,end) in [(1.35,2.9),(4.58,6.1),(6.65,7.7),(8.25,0.8)] {
            let tau=wrap(t-start); if tau<wrap(end-start) { d.drop=max(0,spring(tau,182,0,0,22.1,157.9)); break }
        }
        for (a,b,s) in [(0.7,0.8,0.8),(6.0,6.1,6.1),(7.6,7.7,7.7)] where t>=a && t<s+0.2 { d.ant=out(seg(t,a,b))*(1-out(seg(t,s,s+0.2))) }
        let lands=[1.15,4.45,6.45,8.05], reveals=[8.05,1.25,4.45,6.45], left=[1.35,4.58,6.65,8.25]
        for i in 0..<4 { let tau=wrap(t-lands[i]); if tau<1.4 { d.bumpIndex=(i+1)%4; d.bump=spring(tau,0,0,1,15.1,93.4)/pulsePeak }
            let age=wrap(t-reveals[i]); if age<=wrap(left[i]-reveals[i]) { d.reveal[i]=min(age,0.8) }
        }
        var delta=p-position(t-1/240).0; if delta>2 { delta-=4 }; if delta < -2 { delta+=4 }; d.rate=delta*240
        return d
    }
    struct Pose { var role: Int; var x: Double; var y: Double; var rotation: Double; var scale: Double; var alpha: Double; var blur: Double; var streak: Double; var reveal: Double; var rx=0.0; var blurAngle=0.0; var y0=0.0; var lift=0.0; var visible=true }
    static func pose(_ i: Int, _ d: Deck, _ top: Double, _ bottom: Double) -> Pose {
        let c=sizes[i], k=Int(floor(d.pos+0.000001)), h=max(0,d.pos-Double(k)), role=((i-k)%4+4)%4
        let ha=max(h,0.22*d.peek), hf=max(0,(h-0.22*d.peek)/(1-0.22*d.peek)), soft=d.peek*(1-seg(hf,0,0.25)), dir=c.5<0 ? -1.0 : 1.0
        let bump=d.bumpIndex==i ? d.bump : 0, slot=520+bottom+0.86*c.1/2, rest=c.4+top
        var p=Pose(role:role,x:c.3,y:rest,rotation:c.5,scale:1,alpha:1,blur:0,streak:0,reveal:d.reveal[i])
        if role==0 { p.x += dir*18*hf; p.y=rest-520*pow(hf,1.6)+6*d.ant-5*bump; p.rotation += dir*(38*hf+1.5*d.ant+c.6*bump); p.scale=(1-0.1*hf)*(1-0.03*soft); p.alpha=1-seg(hf,0.7,1); p.blur=3*soft; p.rx=55*pow(hf,0.9); p.lift=seg(hf,0,0.3)
            let idle=1-seg(hf,0,0.08), breath=sin(2*Double.pi*d.clock/(loop/2))
            p.rotation += d.spin*seg(hf,0.02,0.25)+d.nod+0.4*breath*idle
            p.y += 2*sin(2*Double.pi*d.clock/(loop/2)+1.1)*idle
            p.scale *= 1-0.03*d.press
            let rate=h>0.22*d.peek ? d.rate/(1-0.22*d.peek) : 0, speed=hypot(18*rate,520*1.6*pow(max(hf,0.0001),0.6)*rate); p.streak=min(max(0.043*(speed-240),0),64); p.blurAngle=atan2(-520*1.6*pow(max(hf,0.0001),0.6)*rate,dir*18*rate)
        } else if role==1 { let ky=pow(ha,1.15), ks=pow(ha,0.56); p.y=mix(slot+d.drop*(1-ha)-6*d.ant,rest,ky)-5*bump; p.scale=mix(0.86,1,ks); p.rotation=mix(c.5*0.6,c.5,ks)+dir*c.6*bump; p.blur=11*(1-seg(ha,0.2,0.85)); p.rx = -18*(1-ks); p.y0=c.1*0.7*min(1,ha/0.22); p.lift=1-seg(ha,0.55,1); p.visible=d.drop*(1-ha)<181 }
        else if role==2 { let drop=d.live ? 182*(1-seg(h,0.4,1)) : 182; p.y=slot+drop; p.scale=0.86; p.rotation=c.5*0.6; p.blur=11; p.rx = -18; p.lift=1; p.visible=drop<181 }
        else { p.visible=false }; return p
    }
}
