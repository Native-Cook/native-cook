import Foundation

struct GhostFloatWelcomeLive {
  var amb = 0.0
  var ox = [Double](repeating: 0, count: 13), oy = [Double](repeating: 0, count: 13)
  var vx = [Double](repeating: 0, count: 13), vy = [Double](repeating: 0, count: 13)
  var or = [Double](repeating: 0, count: 13)
  var lookX = 0.0, lookY = 0.0, puff = 0.0, ring = -1.0, wide = 0.0, roll = 0.0, hop = 0.0
  var agree = -1.0, btnPress = 0.0, no = 0.0, noP = -1.0, tiltX = 0.0, tiltY = 0.0
}
