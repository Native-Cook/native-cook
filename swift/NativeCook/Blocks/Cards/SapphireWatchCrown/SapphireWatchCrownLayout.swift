import CoreGraphics

struct SapphireWatchCrownLayout: Equatable {
  var w, h, top, bottom: Double
  init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) { self.w=w; self.h=h; self.top=max(top,44); self.bottom=max(bottom,20) }
  var sx: Double { w/402 }
  var k: Double { (h-top-bottom)/778 }
  func y(_ value: Double) -> Double { value < 62 ? value*top/62 : top+(value-62)*k }
  var cx: Double { 195*sx }
  var cy: Double { y(345) }
  var r: Double { 84*sx }
  var crownX: Double { cx+0.749*r }
  var crownY: Double { cy-0.655*r }
  static let swatches = [35.0,73,112,145]
  static let sizes = [236.0,273,311,354]
  func finger(_ f: SapphireWatchCrownMotion.Frame) -> CGPoint {
    if f.fingerAt == 0 { return CGPoint(x: crownX+14-30*f.fingerU, y: crownY+10+30*f.fingerU) }
    if f.fingerAt == 1 { return CGPoint(x: cx+70-140*f.fingerU,y: cy+40) }
    return CGPoint(x: (f.fingerAt == 2 ? 112 : 73)*sx, y: y(683))
  }
}
