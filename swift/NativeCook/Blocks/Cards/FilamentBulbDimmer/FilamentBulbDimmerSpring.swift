import Foundation

struct FilamentBulbDimmerSpring {
    var value: Double
    var velocity: Double
    let duration,ratio,stiffness,initialEnergy: Double
    var done=false
    init(value: Double,duration: Double,ratio: Double,velocity: Double = 0) {
        let velocity=value*velocity>0 ? 0:velocity
        self.value=value; self.velocity=velocity; self.duration=duration; self.ratio=ratio
        let settle=duration*1.5
        func residual(_ k: Double) -> Double {
            let z=sqrt(k/4)*ratio, envelope=exp(-z*settle)
            let p=(value+(velocity+value*z)*settle)*envelope
            let v = -(value+(velocity+value*z)*settle)*envelope*z+(velocity+value*z)*envelope
            let e0=0.5*k*value*value+2*velocity*velocity
            return e0 == 0 ? 0:(0.5*k*p*p+2*v*v)/e0-6e-9
        }
        var lo=Double.ulpOfOne,hi=8000.0,mid=4000.0
        for _ in 0..<100 {
            mid=(lo+hi)/2
            let r=residual(mid)
            if abs(r)<=6e-12 { break }
            if r>0 { lo=mid } else { hi=mid }
        }
        stiffness=mid; initialEnergy=0.5*mid*value*value
    }
    mutating func step(_ dt: Double) -> Double {
        let dt=min(0.064,dt),omega=sqrt(stiffness/4)
        let wd=omega*sqrt(max(0,1-ratio*ratio)),z=ratio*omega
        if ratio >= 1 {
            let a=value,b=velocity+z*value,e=exp(-z*dt)
            value=e*(a+b*dt); velocity=e*(b-z*(a+b*dt))
            if initialEnergy == 0 || (0.5*stiffness*value*value+2*velocity*velocity)/initialEnergy <= 6e-9 { value=0; velocity=0; done=true }; return value
        }
        let a=value,b=(velocity+z*value)/wd,e=exp(-z*dt)
        value=e*(a*cos(wd*dt)+b*sin(wd*dt))
        velocity=e*((-z*a+wd*b)*cos(wd*dt)+(-z*b-wd*a)*sin(wd*dt))
        if initialEnergy == 0 || (0.5*stiffness*value*value+2*velocity*velocity)/initialEnergy <= 6e-9 {
            value=0; velocity=0; done=true
        }
        return value
    }
}
