import Foundation

struct ClayReactionMorphFrame {
  var lift = 0.0, r = 1.0, hold = 0.0, press = 0.0
  var squash = 0.0, lean = 0.0, finger = 0.0, fingerDx = 0.0
}

enum ClayReactionMorphMotion {
  static let loop = 11.0
  static let phase = 1.6
  static let restTime = loop - phase
  static let liftSeconds = 0.6, flySeconds = 0.6
  static let slides: [[Double]] = [
    [1.75, 2.35, 1, 2], [2.75, 3.35, 2, 3], [3.75, 5, 3, 0],
    [5.4, 6.2, 0, 2], [8.75, 9.35, 2, 1]
  ]
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x, a, b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
  }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
  static func backOut(_ x: Double) -> Double {
    let u = clamp(x)-1; return 1+2.25*u*u*u+1.25*u*u
  }
  static func landSquash(_ u: Double) -> Double {
    if u <= 0 || u >= 0.9 { return 0 }
    return 0.85*exp(-u*8)*sin(u*24)
  }
  static func reactionAt(_ t: Double) -> Double {
    var r = 1.0
    for s in slides where t >= s[0] { r = s[2]+(s[3]-s[2])*easeInOut(seg(t,s[0],s[1])) }
    return r
  }
  static func liftOf(_ t: Double, _ lift: Double, _ release: Double) -> Double {
    if t < lift { return 0 }
    if t < release { return backOut(seg(t, lift, lift+liftSeconds)) }
    return 1-easeInOut(seg(t, release, release+flySeconds))
  }
  static func frameAt(_ time: Double) -> ClayReactionMorphFrame {
    let t = (time+phase).truncatingRemainder(dividingBy: loop)
    func up(_ a: Double, _ b: Double) -> Double {
      easeInOut(seg(t,a,a+0.2))*(1-easeInOut(seg(t,b,b+0.3)))
    }
    func press(_ a: Double, _ b: Double) -> Double {
      easeOut(seg(t,a,a+0.12))*(1-easeOut(seg(t,b,b+0.25)))
    }
    func pop(_ a: Double) -> Double { -0.35*sin(.pi*seg(t,a,a+0.35)) }
    func near(_ a: Double, _ b: Double) -> Double {
      easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
    }
    let r = reactionAt(t)
    let dr = (reactionAt(t+0.03)-reactionAt(t-0.03))/0.06
    return ClayReactionMorphFrame(
      lift: liftOf(t,1,6.55)+liftOf(t,8.05,9.6), r: r,
      hold: up(1,6.55)+up(8.05,9.6), press: press(0.7,1)+press(7.75,8.05),
      squash: pop(1)+pop(8.05)+landSquash(t-6.55-flySeconds)+landSquash(t-9.6-flySeconds),
      lean: max(-1,min(1,dr*0.32)), finger: near(0.7,6.55)+near(7.75,9.6),
      fingerDx: t < 7.45 ? r-1 : r-2)
  }
}
