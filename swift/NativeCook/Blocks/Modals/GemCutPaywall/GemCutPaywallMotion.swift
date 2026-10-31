import Foundation

// A direct transcription of gcp-motion.ts. Time is seconds, angles are radians.
enum GemCutPaywallMotion {
    static let loop = 10.0
    static let idleSpin = 0.3
    static let pitch = 0.45
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func easeQuadratic(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
    struct Frame {
        var cut = 0.0, yaw = 0.0, pitch = 0.45, spark = 0.0
        var finger = 0.0, fingerOn = 0, press = 0.0
    }
    static func pulse(_ t: Double, _ at: Double, _ amp: Double) -> Double {
        if t < at-0.05 { return 0 }
        return amp * easeOut(seg(t,at-0.05,at+0.08)) * exp(-2.6*max(0,t-at-0.08))
    }
    static func frameAt(_ t: Double) -> Frame {
        let cut = easeInOut(seg(t,0.5,2.3)) - 0.5*easeInOut(seg(t,5.68,6.38)) - 0.5*easeInOut(seg(t,7.48,8.18))
        let k = seg(t,3.4,9.2), lead = 0.06
        let x = (k-lead)/(1-lead)
        let g = k < lead ? 0.5*k*k/lead : 0.5*lead+(1-lead)*(1-pow(1-x,4))/4
        let yaw = idleSpin*t + (2 * .pi-idleSpin*loop)*g/(0.5*lead+(1-lead)/4)
        let p = pitch+0.1*sin(.pi*seg(t,3.4,5.0))
        let spark = (pulse(t,2.3,1)+pulse(t,6.38,0.45)+pulse(t,8.18,0.18))*(1-seg(t,9.4,10))
        var finger = 0.0, on = 0, press = 0.0
        if t < 2.3+0.4 {
            finger = easeOut(seg(t,0.32,0.5))*(1-easeOut(seg(t,2.35,2.55)))
        } else if t < 3.72+0.5 {
            finger = easeOut(seg(t,3.25,3.4))*(1-easeOut(seg(t,3.72,3.90))); on = 1
        } else {
            let at = t < 7.4-0.3 ? 5.6 : 7.4
            on = at == 5.6 ? 2 : 3
            finger = easeOut(seg(t,at-0.15,at))*(1-easeOut(seg(t,at+0.12,at+0.3)))
            press = easeOut(seg(t,at,at+0.08))*(1-easeOut(seg(t,at+0.12,at+0.3)))
        }
        return Frame(cut:cut,yaw:yaw,pitch:p,spark:spark,finger:finger,fingerOn:on,press:press)
    }
    static func flickOffset(_ t: Double) -> Double { -0.35+0.9*easeInOut(seg(t,3.4,3.72)) }
    static func rotX(_ v: [Double], _ a: Double) -> [Double] {
        [v[0], cos(a)*v[1]-sin(a)*v[2], sin(a)*v[1]+cos(a)*v[2]]
    }
    static func rotY(_ v: [Double], _ a: Double) -> [Double] {
        [cos(a)*v[0]+sin(a)*v[2],v[1],-sin(a)*v[0]+cos(a)*v[2]]
    }
    static func glints(_ yaw: Double, _ pitch: Double, _ cut: Double, _ spark: Double) -> [Double] {
        var result: [Double] = []
        for i in 0..<9 {
            let a = Double(i) * .pi/4
            let point = i < 8 ? [0.8*cos(a),0.17,0.8*sin(a)] : [0.18,0.316,0.1]
            let normal = i < 8 ? [0.5664*cos(a),0.8241,0.5664*sin(a)] : [0,1,0]
            let p = rotX(rotY([point[0],point[1]+0.28,point[2]],yaw),pitch)
            let n = rotX(rotY(normal,yaw),pitch)
            let d = 2*n[2], rx = d*n[0], ry = d*n[1], rz = -1+d*n[2]
            let top = max(0,ry*0.949+rz*0.316), ring = max(0,rx*0.139+ry*0.324+rz*0.936)
            let strength = min(1,(pow(top,24)+pow(ring,60))*(1+2*spark))*cut*cut*(n[2]>0.05 ? 1 : 0)
            let k = 6/(6-p[2]); result += [p[0]*k,-p[1]*k,strength]
        }
        return result
    }
    // RN's interactive cubic-bezier, solved for x rather than applying y(t) directly.
    static func bezier(_ x: Double, _ x1: Double = 0.77, _ y1: Double = 0, _ x2: Double = 0.175, _ y2: Double = 1) -> Double {
        func value(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo=0.0, hi=1.0
        for _ in 0..<24 { let m=(lo+hi)/2; if value(m,x1,x2)<x { lo=m } else { hi=m } }
        return value((lo+hi)/2,y1,y2)
    }
    // Reanimated 4 duration spring: same mass, stiffness bisection, velocity clipping and
    // relative energy termination. `duration` is perceptual, not an abrupt time cutoff.
    static func spring(_ t: Double, _ duration: Double, _ damping: Double, _ from: Double, _ to: Double, _ velocity: Double) -> Double {
        let mass = 4.0, threshold = 6e-9, displacement = from-to
        let v = (to>from && velocity<0) || (to<from && velocity>0) ? 0 : velocity
        func energy(_ x: Double, _ speed: Double, _ stiffness: Double) -> Double { 0.5*stiffness*x*x+0.5*mass*speed*speed }
        func difference(_ stiffness: Double) -> Double {
            let omega = sqrt(stiffness/mass)*damping, settle = duration*1.5
            let envelope = exp(-omega*settle)
            let x = (displacement+(v+displacement*omega)*settle)*envelope
            let speed = -omega*x+(v+displacement*omega)*envelope
            let initial = energy(displacement,v,stiffness)
            return initial == 0 ? 0 : energy(x,speed,stiffness)/initial-threshold
        }
        var low = Double.ulpOfOne, high = 8000.0, stiffness = (low+high)/2
        let direction = difference(high)>=difference(low) ? 1.0 : -1.0
        var iterations = 100
        while abs(difference(stiffness))>threshold*1e-3 && iterations>0 {
            iterations -= 1
            if difference(stiffness)*direction<0 { low=stiffness } else { high=stiffness }
            stiffness=(low+high)/2
        }
        let omega=sqrt(stiffness/mass), wd=omega*sqrt(1-damping*damping), envelope=exp(-damping*omega*t)
        let fragment=envelope*(sin(wd*t)*(v+damping*omega*displacement)/wd+displacement*cos(wd*t))
        let speed = -damping*omega*fragment+envelope*(cos(wd*t)*(v+damping*omega*displacement)-wd*displacement*sin(wd*t))
        let initial=energy(displacement,velocity,stiffness)
        if initial == 0 || energy(fragment,speed,stiffness)/initial<=threshold { return to }
        return to+fragment
    }
}
