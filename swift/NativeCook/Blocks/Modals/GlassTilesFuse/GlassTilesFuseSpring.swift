import Foundation

// Reanimated 4 duration-spring equations: mass 4, 6e-9 relative energy threshold,
// stiffness solved by the same bisection and 1.5 perceptual-duration coefficient.
struct GlassTilesFuseSpring {
    let from: Double, to: Double, zeta: Double, velocity: Double, configuredVelocity: Double, stiffness: Double
    init(from: Double,to: Double,duration: Double,zeta: Double,velocity: Double) {
        self.from=from; self.to=to; self.zeta=zeta; configuredVelocity=velocity
        let v=(to>from && velocity<0) || (to<from && velocity>0) ? 0:velocity
        self.velocity=v
        let x=from-to, settle=duration*1.5
        func fraction(_ k: Double) -> Double {
            let w=sqrt(k/4)*zeta, envelope=exp(-w*settle)
            let xt=(x+(v+x*w)*settle)*envelope
            let vt=xt * -w+(v+x*w)*envelope
            let e0=0.5*k*x*x+2*v*v
            return e0==0 ? 0:(0.5*k*xt*xt+2*vt*vt)/e0
        }
        var lo=Double.ulpOfOne,hi=8000.0,mid=4000.0
        var count=100
        while abs(fraction(mid)-6e-9)>6e-12 && count>0 {
            count -= 1
            if fraction(mid)>6e-9 { lo=mid } else { hi=mid }
            mid=(lo+hi)/2
        }
        stiffness=mid
    }
    func sample(_ elapsed: Double) -> (position: Double,velocity: Double) {
        if elapsed<=0 { return (from,velocity) }
        let w=sqrt(stiffness/4), wd=w*sqrt(1-zeta*zeta), x=from-to
        let env=exp(-zeta*w*elapsed), s=sin(wd*elapsed), c=cos(wd*elapsed)
        let displacement=env*(s*(velocity+zeta*w*x)/wd+x*c)
        let v = -zeta*w*displacement+env*(c*(velocity+zeta*w*x)-wd*x*s)
        let initialEnergy=0.5*stiffness*x*x+2*configuredVelocity*configuredVelocity
        if initialEnergy==0 || (0.5*stiffness*displacement*displacement+2*v*v)/initialEnergy<=6e-9 { return (to,0) }
        return (to+displacement,v)
    }
}
