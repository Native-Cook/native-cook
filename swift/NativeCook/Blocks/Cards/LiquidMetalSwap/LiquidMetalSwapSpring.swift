import Foundation

// Reanimated 4's duration spring: mass 4, relative energy 6e-9, duration × 1.5.
// This is separate from the scripted loop's deliberately softer omega=9 return.
struct LiquidMetalSwapSpring {
    let displacement: Double, velocity: Double, damping: Double, omega: Double, initialEnergy: Double
    init(displacement: Double, velocity requestedVelocity: Double = 0, duration: Double, damping: Double) {
        self.displacement=displacement; self.damping=damping
        // The bundled Reanimated spring clips velocity pointing away from its new target.
        let v=displacement*requestedVelocity > 0 ? 0 : requestedVelocity
        velocity=v
        let settling=duration*1.5, mass=4.0
        func energy(_ x: Double,_ speed: Double,_ stiffness: Double) -> Double { 0.5*stiffness*x*x+0.5*mass*speed*speed }
        func difference(_ stiffness: Double) -> Double {
            let w=sqrt(stiffness/mass)*damping
            let x=(displacement+(v+displacement*w)*settling)*exp(-w*settling)
            let speed=x * -w+(v+displacement*w)*exp(-w*settling)
            let initial=energy(displacement,v,stiffness)
            return initial == 0 ? -6e-9 : energy(x,speed,stiffness)/initial-6e-9
        }
        var lo=Double.ulpOfOne, hi=8000.0
        let direction=difference(hi) >= difference(lo) ? 1.0 : -1.0
        var stiffness=(lo+hi)/2
        for _ in 0..<100 {
            if abs(difference(stiffness)) <= 6e-12 { break }
            if difference(stiffness)*direction < 0 { lo=stiffness } else { hi=stiffness }
            stiffness=(lo+hi)/2
        }
        omega=sqrt(stiffness/mass)
        initialEnergy=energy(displacement,requestedVelocity,stiffness)
    }
    func value(_ tau: Double) -> Double {
        let t=max(0,tau), x: Double, v: Double
        if damping < 1 {
            let w=omega*sqrt(1-damping*damping), envelope=exp(-damping*omega*t)
            let phase=sin(w*t)*(velocity+damping*omega*displacement)/w+displacement*cos(w*t)
            x=envelope*phase
            v = -damping*omega*x+envelope*(cos(w*t)*(velocity+damping*omega*displacement)-w*displacement*sin(w*t))
        } else {
            x=exp(-omega*t)*(displacement+(velocity+omega*displacement)*t)
            v = -omega*x+exp(-omega*t)*(velocity+omega*displacement)
        }
        let energy=0.5*4*omega*omega*x*x+0.5*4*v*v
        return initialEnergy == 0 || energy/initialEnergy <= 6e-9 ? 0 : x
    }
}
