import CoreGraphics

struct DiceBoardRewardsGeometry: Equatable {
  let width, height, topInset, bottomInset: Double
  var tw: Double { ((width-20)/4).rounded() }
  var th: Double { tw/2 }
  var cx: Double { width/2 }
  var top: Double { (height*0.27).rounded() }
  var trayY: Double { height-bottomInset-175 }
  func iso(_ x: Double,_ y: Double) -> CGPoint {
    CGPoint(x:cx+(x-y)*tw*0.5,y:top+(x+y)*th*0.5)
  }
  func tileAt(_ index: Double) -> CGPoint {
    let i = (Int(floor(index)) % 12+12)%12
    let f = index-floor(index)
    let a = DiceBoardRewardsMotion.path[i], b = DiceBoardRewardsMotion.path[(i+1)%12]
    let p = iso(a.0+(b.0-a.0)*f,a.1+(b.1-a.1)*f)
    return CGPoint(x:p.x,y:p.y+th*0.5)
  }
}
