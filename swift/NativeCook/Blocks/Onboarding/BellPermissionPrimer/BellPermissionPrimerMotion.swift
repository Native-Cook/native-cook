import Foundation
import CoreGraphics

// Closed-form reference script; every pose is evaluated directly, including debug frames.
enum BellPermissionPrimerMotion {
    static let loop = 10.0, period = 1.05, gap = 0.13, lift = 150.0
    static let openZeta = 0.7, openOmega = 4 * Double.pi
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func smooth(_ x: Double) -> Double { x*x*(3-2*x) }
    static func openSpring(_ t: Double) -> Double {
        guard t>0 else { return 0 }
        let wd=openOmega*sqrt(1-openZeta*openZeta)
        return 1-exp(-openZeta*openOmega*t)*(cos(wd*t)+openZeta*openOmega/wd*sin(wd*t))
    }
    static func bounceIn(_ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        if x < 0.62 { return pow(x/0.62,2) }
        let u=(x-0.62)/0.38
        return 1-0.09*sin(.pi*u)*(1-u)
    }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        func at(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo=0.0, hi=1.0
        for _ in 0..<24 { let m=(lo+hi)/2; if at(m,x1,x2)<x { lo=m } else { hi=m } }
        return at((lo+hi)/2,y1,y2)
    }
    // Reanimated 4 duration springs: mass 4, relative energy 6e-9, perceived duration × 1.5.
    private static func springOmega(_ z:Double,_ x0:Double,_ v0:Double) -> Double {
        let duration=0.65*1.5, threshold=6e-9, mass=4.0
        func fraction(_ stiffness:Double) -> Double {
            let omega=sqrt(stiffness/mass)*z
            let x=(x0+(v0+x0*omega)*duration)*exp(-omega*duration)
            let v = -omega*x+(v0+x0*omega)*exp(-omega*duration)
            return (x*x+mass*v*v/stiffness)/(x0*x0+mass*v0*v0/stiffness)-threshold
        }
        var lo=Double.ulpOfOne, hi=8000.0, mid=(lo+hi)/2
        for _ in 0..<100 {
            if abs(fraction(mid)) <= threshold*1e-3 { break }
            if fraction(mid)>0 { lo=mid } else { hi=mid }
            mid=(lo+hi)/2
        }
        return sqrt(mid/mass)
    }
    struct Spring {
        let from:Double, to:Double, velocity:Double, configuredVelocity:Double, zeta:Double, omega:Double
        init(from:Double,to:Double,velocity:Double=0,zeta:Double,omega:Double?=nil) {
            self.from=from; self.to=to; self.configuredVelocity=velocity; self.zeta=zeta
            // Match withSpring's onStart: only momentum towards the target survives.
            let v=((to>from && velocity<0) || (to<from && velocity>0)) ? 0:velocity
            self.velocity=v
            self.omega=omega ?? (from==to && v==0 ? 1:springOmega(zeta,from-to,v))
        }
        func sample(_ t:Double) -> (value:Double,finished:Bool) {
            let x0=from-to, wd=omega*sqrt(1-zeta*zeta), e=exp(-zeta*omega*t)
            let x=e*(x0*cos(wd*t)+(velocity+zeta*omega*x0)/wd*sin(wd*t))
            let v = -zeta*omega*x+e*((velocity+zeta*omega*x0)*cos(wd*t)-wd*x0*sin(wd*t))
            let initial=x0*x0+configuredVelocity*configuredVelocity/(omega*omega)
            let finished=initial==0 || (x*x+v*v/(omega*omega))/initial<=6e-9
            return (finished ? to:to+x,finished)
        }
    }
    struct Frame {
        var swing=0.0, clap=0.0, lift=0.0, suck=0.0, expand=0.0
        var liftX=0.0, liftY=0.0
        var rings=SIMD3<Double>(repeating:0)
        var finger=0.0, fingerAt=0, fingerX=0.0, fingerPress=0.0, bob=0.0
    }
    static func frameAt(_ t: Double) -> Frame {
        let tau=Double.pi*2, a=0.42, w=tau/period
        let calm=1-easeInOut(seg(t,3.1,3.7))
        var f=Frame()
        if t>=0.9 && t<3.7 {
            f.swing = t<1.15 ? a*easeOut(seg(t,0.9,1.15)) : a*exp(-0.75*(t-1.15))*cos(w*(t-1.15))
            f.swing *= calm
            let dt=max(0,t-1.15)
            let raw = t<1.15 ? f.swing*0.7 : a*1.12*exp(-0.6*dt)*cos(w*dt-0.9)*calm
            f.clap=f.swing+max(-gap,min(gap,raw-f.swing))
        }
        if t>=8.8 {
            let dt=t-8.8, k=1-smooth(seg(t,9.4,9.95))
            f.swing += 0.12*exp(-1.6*dt)*sin(w*dt)*k
            f.clap += 0.16*exp(-1.4*dt)*sin(w*dt-0.7)*k
        }
        f.swing += 0.012*sin(2*tau*t/loop)
        f.clap += 0.012*sin(2*tau*t/loop-0.4)
        f.lift=easeInOut(seg(t,4,4.75))*(t<5.05 ? 1 : 0)
        f.liftY = -lift*f.lift
        f.suck = t<8.2 ? pow(seg(t,4.75,5.05),2) : 1-bounceIn(seg(t,8.2,8.8))
        if t<4.75 { f.suck=0 }
        f.expand=openSpring(t-5.05)*(1-easeInOut(seg(t,7.8,8.2)))
        for (i,strike) in [1.42,1.94,2.47].enumerated() {
            f.rings[i] = t>strike && t<strike+0.9 ? seg(t,strike,strike+0.9) : 0
        }
        if t<2 {
            f.finger=easeOut(seg(t,0.75,0.9))*(1-easeOut(seg(t,1.15,1.33)))
            f.fingerX = -0.1+0.55*easeOut(seg(t,0.9,1.15)); f.fingerPress=f.finger
        } else if t<6.5 {
            f.finger=easeOut(seg(t,3.82,3.98))*(1-easeOut(seg(t,4.75,4.91)))
            f.fingerPress=f.finger
        } else {
            let at=7.7
            f.finger=easeOut(seg(t,at-0.15,at-0.02))*(1-easeOut(seg(t,at+0.14,at+0.3)))
            f.fingerAt=2
            f.fingerPress=easeOut(seg(t,at-0.02,at+0.08))*(1-easeOut(seg(t,at+0.12,at+0.26)))
        }
        f.bob=0.015*sin(tau*t/loop)
        return f
    }
}

struct BellPermissionPrimerLayout: Equatable {
    let w: Double, h: Double, top: Double, bottom: Double
    let safeTop: Double
    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w=w; self.h=h; self.safeTop=top
        self.top=max(top,44); self.bottom=max(bottom,20)
    }
    var sx: Double { w/402 }
    var k: Double { (h-top-bottom)/778 }
    func y(_ y: Double) -> Double { y<62 ? y*top/62 : top+(y-62)*k }
    var px: Double { 200.5*sx }
    var py: Double { y(206) }
    var unit: Double { 116*sx }
    var islandY: Double { safeTop>=51 ? 14 : safeTop+6 }
    var island: SIMD4<Float> { SIMD4(Float(w/2-62.5),Float(islandY),125,36.3) }
    var slab: SIMD4<Float> { SIMD4(Float(10*sx),Float(islandY),Float(w-20*sx),92) }
    func uniforms(_ f: BellPermissionPrimerMotion.Frame, _ t: Double, _ ratio: Double) -> [SIMD4<Float>] {
        [SIMD4(Float(w),Float(h),Float(ratio),Float(t)),
         SIMD4(Float(px),Float(py),Float(unit),Float(f.bob*unit)),
         SIMD4(Float(f.swing),Float(f.clap),Float(f.suck),Float(f.expand)),
         SIMD4(Float(f.liftX),Float(f.liftY),0,0), island,slab,
         SIMD4(Float(f.rings.x),Float(f.rings.y),Float(f.rings.z),0),
         SIMD4(-0.22,-0.12,0,0)]
    }
}
