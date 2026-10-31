import Foundation

// Reanimated 4 duration spring: mass 4, energy threshold 6e-9 and perceptual factor 1.5.
// Solve the same critical envelope for stiffness, then evaluate its damped response analytically.
struct ClayPromptSpring {
  var omega:Double, damping:Double, displacement:Double, velocity:Double
  init(duration:Double,damping:Double,displacement:Double = -1,velocity:Double=0) {
    self.damping=damping;self.displacement=displacement;self.velocity=velocity
    var lo=Double.ulpOfOne,hi=8000.0
    let time=duration*1.5,mass=4.0,threshold=6e-9
    for _ in 0..<100 {
      let k=(lo+hi)/2,w=sqrt(k/mass)*damping,e=exp(-w*time)
      let x=(displacement+(velocity+displacement*w)*time)*e
      let v = -w*x+(velocity+displacement*w)*e
      let initial=0.5*k*displacement*displacement+0.5*mass*velocity*velocity
      let fraction=(0.5*k*x*x+0.5*mass*v*v)/max(initial,Double.leastNonzeroMagnitude)
      if abs(fraction-threshold)<threshold*1e-3 { lo=k;hi=k;break }
      if fraction>threshold { lo=k } else { hi=k }
    }
    omega=sqrt((lo+hi)/2/mass)
  }
  func sample(_ t:Double)->(Double,Double) {
    if t<=0 { return (displacement,velocity) }
    let x:Double,v:Double
    if damping<1 {
      let wd=omega*sqrt(1-damping*damping),e=exp(-damping*omega*t),s=sin(wd*t),c=cos(wd*t)
      x=e*(s*((velocity+damping*omega*displacement)/wd)+displacement*c)
      v = -damping*omega*x+e*(c*(velocity+damping*omega*displacement)-wd*displacement*s)
    } else {
      let e=exp(-omega*t)
      x=e*(displacement+(velocity+omega*displacement)*t)
      v = -omega*x+e*(velocity+omega*displacement)
    }
    let energy=omega*omega*x*x+v*v,initial=omega*omega*displacement*displacement+velocity*velocity
    return energy<=initial*6e-9 ? (0,0) : (x,v)
  }
  static let pop=ClayPromptSpring(duration:0.52,damping:0.5)
  static let move=ClayPromptSpring(duration:0.42,damping:0.8)
}
