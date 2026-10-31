import Foundation

enum PotionVialUploadMotion {
  static let LOOP = 12.0
  static let ANCHOR = PotionVialUploadPoint(x: 315.5, y: 14)
  static let CORK = PotionVialUploadPoint(x: 355, y: -1)
  static let MOUTH = PotionVialUploadPoint(x: 339, y: 0)
  static let HOME = PotionVialUploadPoint(x: 39.5, y: -15)
  static let NEAR_R = 88.0
  enum T {
    static let lift = 0.6, near = 1.0, drop = 1.75, recork1 = 2.0
    static let streak = [2.03, 2.73], splash = 2.73, fill = 8.4, done = 8.5
    static let climb = [9.3, 10.0], recork2 = 11.6
    static let fade = [11.15, 11.6], fadeIn = [0.0, 0.35], still = 10.4
  }
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
  static func smooth(_ k: Double) -> Double { let x = clamp01(k); return x*x*(3-2*x) }
  static func easeOut(_ k: Double) -> Double { let x = clamp01(k); return 1-pow(1-x,3) }
  static func easeIn(_ k: Double) -> Double { pow(clamp01(k),3) }
  static func easeInOut(_ k: Double) -> Double { let x = clamp01(k); return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOutBack(_ k: Double) -> Double { let x = clamp01(k); return 1+2.9*pow(x-1,3)+1.9*pow(x-1,2) }
  static func hash(_ i: Double) -> Double { let s = sin(i*12.9898+78.233)*43758.5453; return s-floor(s) }
  static func roundJS(_ x: Double) -> Double { floor(x + 0.5) }
  static let KEYS: [Double] = [2.73,1,2.8,3,2.87,5,2.93,7,3.0,8,3.07,10,3.13,12,3.2,14,3.27,15,3.33,17,3.4,18,3.47,20,3.53,21,4,27,4.4,34,4.8,40,5.2,48,5.6,58,6,60,6.4,67,6.8,78,7.2,82,7.6,86,8,94,8.1,96,8.2,97,8.3,99,8.4,100]
  static func progressAt(_ t: Double) -> Double {
    if t < KEYS[0] { return 0 }
    for i in stride(from: 0, to: KEYS.count-2, by: 2) {
      if t <= KEYS[i+2] { return mix(KEYS[i+1],KEYS[i+3],(t-KEYS[i])/(KEYS[i+2]-KEYS[i]))/100 }
    }
    return 1
  }
  static func mbText(_ p: Double) -> String {
    // JavaScript toFixed(1) rounds the exact binary value, with positive ties upward.
    // printf uses ties-to-even, which disagrees at values such as 2.25 MB.
    let value = max(0,p*48.2), bits = value.bitPattern
    let exponent = Int((bits >> 52) & 0x7ff)
    let mantissa = (bits & 0x000f_ffff_ffff_ffff) | (exponent == 0 ? 0 : 1 << 52)
    let shift = 1075-exponent
    let tenths: UInt64
    if shift >= 64 { tenths = 0 }
    else if shift > 0 {
      let denominator = UInt64(1) << shift
      tenths = (mantissa*10+denominator/2)/denominator
    } else { tenths = mantissa*10 << (-shift) }
    return "\(tenths/10).\(tenths%10) of 48.2 MB"
  }
}

struct PotionVialUploadGem {
  var x, y: Double
  var vx = 0.0, vy = 0.0, rot: Double, w = 0.0
  var corked: Int
  var homeAt = -1.0, fromX = 0.0, fromY = 0.0, fromRot = 0.0
  var holdX = Double.nan, holdY = Double.nan, lastHome = -10.0, homeDur = 0.34
  init(_ corked: Bool) {
    x = corked ? 39.5 : 0; y = corked ? -15 : 80
    rot = corked ? .pi+0.5 : 0; self.corked = corked ? 1 : 0
  }
  mutating func pop(_ strength: Double) {
    if corked == 0 && homeAt < 0 { return }
    corked = 0; homeAt = -1; vx = 70*strength; vy = -150*strength; w = 5.5*strength
  }
  mutating func recork(_ now: Double, _ snap: Bool = false) {
    if corked == 1 && homeAt < 0 { return }
    corked = 1; homeAt = now; lastHome = now; homeDur = snap ? 0.07 : 0.34
    fromX = x; fromY = y; fromRot = rot; vx = 0; vy = 0; w = 0; holdX = .nan
  }
  mutating func step(_ dt: Double, _ now: Double) {
    typealias M = PotionVialUploadMotion
    if corked == 1 {
      if homeAt < 0 { x = 39.5; y = -15; rot = .pi+0.5; return }
      let k = M.clamp01((now-homeAt)/homeDur), e = M.easeOutBack(k)
      let lift = homeDur < 0.1 ? 0 : sin(.pi*M.clamp01(k*1.15))*26
      x = M.mix(fromX,39.5,e); y = M.mix(fromY,-15,e)-lift
      var d = Double.pi+0.5-fromRot; d -= M.roundJS(d/(.pi*2)) * .pi*2
      rot = fromRot+d*M.easeOut(k)
      if k >= 1 { homeAt = -1 }; return
    }
    let n = max(1,Int(ceil(dt/(1.0/240)))), h = dt/Double(n)
    for _ in 0..<n {
      if !holdX.isNaN { vx += ((holdX-x)*260-vx*26)*h; vy += ((holdY-y)*260-vy*26)*h }
      else { vy += 980*h }
      let drag = exp(-0.55*h); vx *= drag; vy *= drag; x += vx*h; y += vy*h
      let d = hypot(x,y); var taut = false
      if d > 80 {
        let nx = x/d, ny = y/d; x = nx*80; y = ny*80
        let radial = vx*nx+vy*ny
        if radial > 0 { vx -= nx*radial; vy -= ny*radial }; taut = true
      }
      if taut {
        var diff = -atan2(x,y)-rot; diff -= M.roundJS(diff/(.pi*2)) * .pi*2
        w += (diff*70-w*9)*h
      } else { w *= exp(-0.8*h) }
      rot += w*h
    }
  }
}

struct PotionVialUploadDriver {
  var t: Double
  var mode = 0, sound = 0
  var last = -10.0
  var gem: PotionVialUploadGem
  var fileX = Double.nan, fileY = Double.nan, fileBack = 1.0, fileFromX = 0.0, fileFromY = 0.0
  var fileBackAt = -10.0, kickAt = -10.0, kickDir = 1.0, dropX = Double.nan, dropY = Double.nan
  init(_ t: Double = 0) { self.t = t; gem = .init(!(t >= 8.5 && t < 11.55)) }
  mutating func step(_ dt: Double, _ now: Double, _ auto: Bool, loopDT: Double? = nil) -> Int {
    var ev = 0
    if mode == 3 && now-last > 2 { mode = 0; sound = 0 }
    if mode == 0 && auto {
      let before = t; var next = before+(loopDT ?? dt)
      if next >= 12 { next -= 12; sound = 0; dropX = .nan }
      func crossed(_ mark: Double) -> Bool {
        before <= next ? before < mark && next >= mark : before < mark || next >= mark
      }
      if crossed(1) { gem.pop(0.8); ev = 1 }
      if crossed(1.75) { ev = 2 }
      if crossed(2) { gem.recork(now,true); ev = 3 }
      if crossed(8.5) { gem.pop(1); ev = 4 }
      if crossed(11.6) { gem.recork(now); ev = 3 }
      t = next
    }
    gem.step(dt,now); return ev
  }
  mutating func drop(_ now: Double) {
    t = 1.75; mode = 0; sound = 1; last = now; dropX = fileX; dropY = fileY; fileX = .nan
  }
  // Deterministic debug capture: run the same state machine from zero at 60 Hz.
  static func replay(_ time: Double) -> (PotionVialUploadDriver,Double) {
    var d = PotionVialUploadDriver(), now = 0.0
    let target = max(0,time.truncatingRemainder(dividingBy:12))
    while now < target { let dt = min(1.0/60,target-now); now += dt; _ = d.step(dt,now,true) }
    return (d,now)
  }
}
