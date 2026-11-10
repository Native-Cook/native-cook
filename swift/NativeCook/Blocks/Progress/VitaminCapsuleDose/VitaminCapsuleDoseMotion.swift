import Foundation

enum VitaminCapsuleDoseMotion {
  static let loop=10.0, morph=0.9, hold=1.2, take=1.25, reform=0.6
  static let tilt=0.785, pourTilt = -0.4, sepOff=1.5
  struct Frame {
    var a=0.0,b=0.0,k=0.0
    var med=0
    var hold=0.0,take=0.0,reform=1.0,taken=0.0
    var yaw=0.0,pitch=0.0,bob=0.0,sep=0.0,twist=0.0,tilt=0.785,hidden=0.0
    var mode=0.0,finger=0.0,fingerAt=0,fingerX=0.0,press=0.0,t=0.0
    var dt=0.0,fx = -999.0,fy = -999.0,fdx=0.0,fdy=0.0,down=0.0
    var targetRow=1
  }
  static func seg(_ t: Double,_ a: Double,_ b: Double)->Double { min(1,max(0,(t-a)/(b-a))) }
  static func easeIO(_ x: Double)->Double { x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double)->Double { 1-pow(1-x,3) }
  static func backOut(_ x: Double)->Double { let u=x-1; return 1+2.9*u*u*u+1.9*u*u }
  static func idle(_ t: Double)->(yaw:Double,pitch:Double,bob:Double) {
    let a=2*Double.pi*t/loop
    return (0.22*sin(a),0.1*sin(2*a+0.7),2.2*sin(2*a))
  }
  static func at(_ t: Double)->Frame {
    var f=Frame(); f.t=t
    f.hold=t>=5 && t<6.2 ? seg(t,5,6.2):0
    f.taken=easeOut(seg(t,7.15,7.35))*(1-easeIO(seg(t,8.65,9)))
    let closing=easeIO(seg(t,7.4,8.1))
    f.sep=0.55*easeIO(seg(t,0.45,1.15))
    if t>=1.15 { f.sep=0.55+0.95*backOut(seg(t,1.15,1.5)) }
    f.sep *= 1-closing
    f.twist=1.1*easeOut(seg(t,0.45,1.5))*(1-closing)
    f.tilt=tilt+(pourTilt-tilt)*easeIO(seg(t,1.2,1.7))*(1-easeIO(seg(t,7.2,7.8)))
    f.hidden=t>=1.3 && t<8.1 ? 1:0
    f.mode=t<1.3 || t>=7.4 ? 0:t<6.2 ? 2:4
    let id=idle(t)
    f.yaw=id.yaw*(1-f.hidden*0.6); f.pitch=id.pitch-f.hold*0.06; f.bob=id.bob
    let pull=easeOut(seg(t,0.27,0.43))*(1-easeOut(seg(t,1.15,1.31)))
    if pull>f.finger { f.finger=pull;f.fingerAt=0;f.fingerX=easeIO(seg(t,0.45,1.15));f.press=pull }
    let stir=easeOut(seg(t,3.72,3.88))*(1-easeOut(seg(t,4.6,4.76)))
    if stir>f.finger { f.finger=stir;f.fingerAt=3;f.fingerX=easeIO(seg(t,3.9,4.6));f.press=stir }
    let h=easeOut(seg(t,4.8,4.97))*(1-easeOut(seg(t,6.2,6.38)))
    if h>f.finger { f.finger=h;f.fingerAt=1;f.press=h }
    let u=easeOut(seg(t,8.4,8.56))*(1-easeOut(seg(t,8.74,8.9)))
    if u>f.finger {
      f.finger=u;f.fingerAt=2;f.fingerX=1
      f.press=easeOut(seg(t,8.56,8.65))*(1-easeOut(seg(t,8.7,8.82)))
    }
    return f
  }
  static func finger(_ f:Frame,_ l:VitaminCapsuleDoseLayout)->CGPoint {
    if f.fingerAt==0 {
      let k=0.45+0.55*f.fingerX
      return CGPoint(x:l.cx+l.unit*0.7*k,y:l.cy-l.unit*0.7*k)
    }
    if f.fingerAt==3 { return CGPoint(x:l.w*(0.12+0.72*f.fingerX),y:l.floor-6) }
    if f.fingerAt==1 { return CGPoint(x:l.w*0.5,y:l.logY) }
    return CGPoint(x:l.checkX,y:l.rowsY[1])
  }
  static func bezier(_ x:Double,_ x1:Double,_ y1:Double,_ x2:Double,_ y2:Double)->Double {
    var a=0.0,b=1.0
    for _ in 0..<24 { let t=(a+b)/2,u=1-t; if 3*u*u*t*x1+3*u*t*t*x2+t*t*t<x { a=t } else { b=t } }
    let t=(a+b)/2,u=1-t
    return 3*u*u*t*y1+3*u*t*t*y2+t*t*t
  }
}
