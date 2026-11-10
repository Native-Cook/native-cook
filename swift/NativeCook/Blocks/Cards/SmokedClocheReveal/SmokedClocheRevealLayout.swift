import CoreGraphics

struct SmokedClocheRevealLayout: Equatable {
  var w: Double, h: Double, top: Double, bottom: Double
  init(w: Double, h: Double, top: Double, bottom: Double) {
    self.w=w;self.h=h;self.top=max(top,44);self.bottom=max(bottom,20)
  }
  var sx: Double { w/402 }
  var k: Double { (h-top-bottom)/778 }
  var scale: Double { min(sx,k) }
  func y(_ v: Double) -> Double { v < 62 ? v*top/62 : top+(v-62)*k }
  var cx: Double { 200*sx }
  var cy: Double { y(304) }
  var half: Double { 206*scale/0.92 }
  var radius: Double { 188*scale }
  var ctaX: Double { 276*sx }
  var ctaY: Double { y(807) }
}
