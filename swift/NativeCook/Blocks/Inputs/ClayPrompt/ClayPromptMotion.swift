import Foundation

enum ClayPromptMotion {
  static let loop = 11.0
  static let char = 0.085
  static let wordEnds = [5, 14, 30]
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func springStep(_ x: Double) -> Double {
    x <= 0 ? 0 : 1-exp(-6*x)*(cos(10*x)+0.6*sin(10*x))
  }
  static func backOut(_ x: Double) -> Double { let u=x-1; return 1+2.45*u*u*u+1.45*u*u }
  struct Look { var lift, spread, k, pull, blobR, reveal, night: Double }
  static func look(_ g: Double) -> Look {
    let swell=1+0.1*sin(.pi*seg(g,0.4,0.52))
    return Look(lift:easeInOut(seg(g,0,0.42)), spread:g <= 0.5 ? 0 : backOut(seg(g,0.5,0.86)),
      k:2+30*smooth(0.12,0.4,g)*(1-smooth(0.52,0.8,g)),
      pull:smooth(0.22,0.46,g)*(1-smooth(0.5,0.64,g)),
      blobR:62*smooth(0.25,0.46,g)*(1-smooth(0.5,0.6,g))*swell,
      reveal:easeOut(seg(g,0.5,0.8)), night:smooth(0.55,0.9,g))
  }
  struct Pose { var x,y,size,squash: Double }
  static func poseAt(_ g:Double,_ sx:Double,_ sy:Double,_ ss:Double,_ x:Double,_ y:Double,_ size:Double,_ cx:Double,_ cy:Double)->Pose {
    let l=look(g)
    if g <= 0.5 {
      let a=l.lift
      return Pose(x:sx+(cx-sx)*a,y:sy+(cy-sy)*a-70*sin(.pi*a),size:ss+(40-ss)*a,squash: -0.12*sin(.pi*a)*(1-a))
    }
    let b=l.spread
    return Pose(x:cx+(x-cx)*b,y:cy+(y-cy)*b,size:40+(size-40)*b,squash:0.08*sin(.pi*min(1,b))*(1-seg(g,0.75,1)))
  }
  static func composed(_ kind:Int,_ sofa:Bool,_ x:Double,_ y:Double,_ w:Double,_ h:Double)->(Double,Double,Double) {
    if kind == 1 { return (x+0.52*w,y+0.74*h,0.27*w) }
    if kind == 0 { return sofa ? (x+0.42*w,y+0.6*h,0.14*w) : (x+0.46*w,y+0.72*h,0.16*w) }
    if kind == 2 { return (x+0.72*w,y+0.25*h,0.125*w) }
    return (x+0.14*w,y+0.74*h,0.115*w)
  }
  static func slotX(_ i:Int,_ n:Double,_ w:Double,_ gap:Double=88)->Double { w/2+(Double(i)-(n-1)/2)*gap }
  struct Frame {
    var chars=0; var pops=[0.0,0,0,0]
    var g=0.0, clear=0.0, press=0.0, finger=0.0, fingerAt=0.0, dragY=0.0
  }
  static func popTime(_ i:Int)->Double { 0.4+Double(wordEnds[i])*char+0.12 }
  static func frameAt(_ t:Double)->Frame {
    var f=Frame()
    f.chars=max(0,min(30,Int(floor((t-0.4)/char))+1))
    for i in 0..<3 { f.pops[i]=springStep((t-popTime(i))/0.6) }
    let pull=0.42*easeInOut(seg(t,7.2,7.9))
    let release=t > 7.9 ? springStep((t-7.9)/0.7) : 0
    f.g=(seg(t,3.42,5.82)-pull*(1-release))*(1-smooth(10.5,10.95,t))
    f.clear=easeInOut(seg(t,9.8,10.5))
    f.press=easeOut(seg(t,3.3,3.38))*(1-easeOut(seg(t,3.42,3.6)))
    if t < 3.8 { f.finger=easeOut(seg(t,3.12,3.3))*(1-easeOut(seg(t,3.44,3.62))) }
    else { f.fingerAt=1; f.finger=easeOut(seg(t,7.02,7.2))*(1-easeOut(seg(t,7.9,8.08))) }
    f.dragY=130*easeInOut(seg(t,7.2,7.9))
    return f
  }
}
