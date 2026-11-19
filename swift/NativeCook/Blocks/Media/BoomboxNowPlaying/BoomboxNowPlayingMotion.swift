import Foundation
import simd

// Vinyl Session: the set's fixed numbers and the film, ported from React Native's vinylScene.ts and vinylMotion.ts.
// glTF metres, y up, the record and sleeve standing upright with their faces toward +z. Every value is a pure
// function of the loop time t (0 <= t < loop), so t = loop gives the frame of t = 0.
//
//   0.00 –  0.80  poster: the sleeve three-quarter from the left, the record peeking out of its open side, 0:00
//   0.80 –  2.30  the record slides out of the sleeve to the right
//   2.20 –  3.80  the sleeve drifts back and to the left; the record comes forward to the middle
//   3.00          play; 3.0 – 4.0 the record spins up and the song starts
//   4.20 –  7.20  the record turns a full 360 deg about its upright axis: edge, B side, edge, A side
//   7.50 –  8.90  rotary scrub: 1.5 turns forward, the song jumps to about 1:40 with it
//   9.20 – 10.40  the volume goes up to full and back (the level bars follow)
//  10.60          forward +15 s
//  11.30          pause: the record runs down by 12.5; 11.8 – 12.3 back to 0:00
//  12.50 – 14.00  the sleeve comes back; the record moves beside it
//  13.90 – 15.10  the record slides back in; 15.1 – 15.8 rest = the poster
enum BoomboxNowPlayingMotion {
  typealias V3 = SIMD3<Double>

  static let loop: Double = 15.8
  static let trackSeconds: Double = 228
  static let idle: Double = 2
  static let volume0: Double = 0.7

  // MARK: scene (vinylScene.ts)

  static let keyDirection = V3(0.55, -0.6, -0.75)
  static let rimDirection = V3(-0.5, -0.4, 0.8)
  static let keyLux = 60_000.0
  static let rimLux = 28_000.0
  static let iblIntensity = 32_000.0
  /// Exposure of the colour grading (stops), before the ACES legacy curve.
  static let exposureStops = 0.25
  static let near = 0.02
  static let far = 20.0
  static let toKey: V3 = simd_normalize(-keyDirection)
  /// Brightness of the groove highlight (emissive factor of the sheen discs).
  static let sheenGlow = 0.9

  static let paperTop: UInt32 = 0xF4F2EE
  static let paperBottom: UInt32 = 0xE4DFD8
  static let ink: UInt32 = 0x1D1C1A
  static let secondary: UInt32 = 0x77716A
  static let barColor: UInt32 = 0xE4572E

  // MARK: phases

  static let slideOutA = 0.8, slideOutB = 2.3
  static let awayZA = 2.2, awayZB = 3.5, awayXA = 2.45, awayXB = 3.8
  static let centreA = 2.35, centreB = 3.8
  static let play = 3.0, spinA = 3.0, spinB = 4.0
  static let turnA = 4.2, turnB = 7.2
  static let scrubA = 7.5, scrubB = 8.9
  static let volUpA = 9.2, volUpB = 9.7, volDownA = 9.9, volDownB = 10.4
  static let forward = 10.6
  static let pause = 11.3, stopA = 11.3, stopB = 12.5
  static let backA = 11.8, backB = 12.3
  static let homeZA = 12.6, homeZB = 13.9, homeXA = 12.5, homeXB = 13.7
  static let uncentreA = 12.5, uncentreB = 14.0
  static let slideInA = 13.9, slideInB = 15.1

  static let centredFrom = 3.8
  static let centredTo = 12.5
  static let closeT = 12.5
  static let stillT = 3.9

  // MARK: the set

  static let rVinyl = 0.1505
  static let peekX = 0.085
  static let outX = 0.338
  static let away = (x: -0.205, y: 0.004, z: -0.37, yaw: 0.42)
  static let middle = V3(0, 0, 0.05)
  static let floorY = -0.205

  // MARK: speed and song

  static let seekPerRad = 10.0
  static let scrubTurns = 1.5
  static let scrubAngle = scrubTurns * 2 * Double.pi
  static let skipS = 15.0
  static let skipTime = 0.4
  static let turns = 6.0
  static let spinTime = (spinB - spinA) / 2 + (stopA - spinB) + (stopB - stopA) / 2
  /// Record speed while playing (rad/s): 32.5 rpm.
  static let omega = (turns * 2 * Double.pi - scrubAngle) / spinTime

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { let u = clamp01(x); return u * u * (3 - 2 * u) }
  static func smoothInt(_ u: Double) -> Double { let v = clamp01(u); return v * v * v - v * v * v * v / 2 }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * clamp01(x)) - 1) / 2 }
  static func easeInOutCubic(_ x: Double) -> Double {
    let u = clamp01(x)
    return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
  }
  static func fract(_ x: Double) -> Double { x - floor(x) }
  /// Reanimated's Easing.inOut(Easing.quad) (also withTiming's default) and Easing.out(Easing.quad).
  static func inOutQuad(_ x: Double) -> Double { let u = clamp01(x); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
  static func outQuad(_ x: Double) -> Double { let u = clamp01(x); return 1 - (1 - u) * (1 - u) }
  /// Easing.bezier(0.65, 0, 0.35, 1): the touch logic's ease.
  static func ease(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, clamp01(x)) }
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
    func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
    var lo = 0.0, hi = 1.0, s = x
    for _ in 0..<40 {
      s = (lo + hi) / 2
      if bx(s) < x { lo = s } else { hi = s }
    }
    return by(s)
  }

  // MARK: the record

  static func speedAt(_ t: Double) -> Double { smooth(seg(t, spinA, spinB)) * (1 - smooth(seg(t, stopA, stopB))) }

  static func motorAt(_ t: Double) -> Double {
    let up = (spinB - spinA) * smoothInt(seg(t, spinA, spinB))
    let hold = max(0, min(t, stopA) - spinB)
    let d = stopB - stopA
    let u = seg(t, stopA, stopB)
    return up + hold + d * (u - smoothInt(u))
  }

  static func scrubAt(_ t: Double) -> Double { easeInOutCubic(seg(t, scrubA, scrubB)) }

  /// Record angle (rad, clockwise as the viewer sees its A side): motor plus scrub; the loop gives six whole turns.
  static func platterAt(_ t: Double) -> Double { omega * motorAt(t) + scrubAngle * scrubAt(t) }

  static func songAt(_ t: Double) -> Double {
    let played = motorAt(t)
    let scrub = scrubAngle * seekPerRad * scrubAt(t)
    let skip = skipS * easeInOutSine(seg(t, forward, forward + skipTime))
    return (played + scrub + skip) * (1 - easeInOutSine(seg(t, backA, backB)))
  }

  static func playingAt(_ t: Double) -> Bool { t >= play && t < pause }

  static func volumeAt(_ t: Double) -> Double {
    let up = easeInOutSine(seg(t, volUpA, volUpB))
    let down = easeInOutSine(seg(t, volDownA, volDownB))
    return volume0 + (1 - volume0) * up * (1 - down)
  }

  static func turnAt(_ t: Double) -> Double { 2 * Double.pi * easeInOutCubic(seg(t, turnA, turnB)) }

  /// Record and sleeve: position and yaw (the record's whole-body turn; its spin is platterAt).
  struct Pose {
    var record = V3(0, 0, 0), recordYaw = 0.0
    var sleeve = V3(0, 0, 0), sleeveYaw = 0.0
  }

  static func poseAt(_ t: Double) -> Pose {
    let awayZ = easeInOutSine(seg(t, awayZA, awayZB)) * (1 - easeInOutSine(seg(t, homeZA, homeZB)))
    let awayX = easeInOutSine(seg(t, awayXA, awayXB)) * (1 - easeInOutSine(seg(t, homeXA, homeXB)))
    let sx = away.x * awayX, sy = away.y * awayX, sz = away.z * awayZ, syaw = away.yaw * awayX
    let rel = lerp(peekX, outX, easeInOutCubic(seg(t, slideOutA, slideOutB)) * (1 - easeInOutCubic(seg(t, slideInA, slideInB))))
    let ix = sx + cos(syaw) * rel
    let iz = sz - sin(syaw) * rel
    let c = easeInOutCubic(seg(t, centreA, centreB)) * (1 - easeInOutCubic(seg(t, uncentreA, uncentreB)))
    let bob = 0.005 * sin(4 * Double.pi * t / loop) * c
    var p = Pose()
    p.record = V3(lerp(ix, middle.x, c), lerp(sy, middle.y, c) + bob, lerp(iz, middle.z, c) + 0.03 * sin(Double.pi * c))
    p.recordYaw = lerp(syaw, 0, c) + turnAt(t)
    p.sleeve = V3(sx, sy, sz)
    p.sleeveYaw = syaw
    return p
  }

  // MARK: camera (orbit about a moving target)

  /// t, azimuth (deg, + = right), elevation (deg), distance, target x, y, z.
  static let keys: [[Double]] = [
    [0.0, -17, 7, 0.93, 0.06, -0.012, 0],
    [0.8, -17, 7, 0.93, 0.06, -0.012, 0],
    [2.3, -9, 6, 1.14, 0.165, -0.01, 0],
    [3.8, -4, 5, 0.82, -0.03, 0, 0.02],
    [5.8, 6, 4, 0.75, -0.02, 0, 0.03],
    [7.5, 11, 9, 0.68, 0.0, 0, 0.05],
    [8.9, 9, 8, 0.7, 0.0, 0, 0.05],
    [10.6, 1, 6, 0.78, -0.02, 0, 0.03],
    [12.5, -5, 6, 0.86, 0.0, 0, 0.01],
    [13.9, -11, 6, 1.14, 0.165, -0.01, 0],
    [15.1, -17, 7, 0.93, 0.06, -0.012, 0],
    [15.8, -17, 7, 0.93, 0.06, -0.012, 0],
  ]

  static func keyAt(_ t: Double, _ c: Int) -> Double {
    var i = 0
    while i < keys.count - 2 && t >= keys[i + 1][0] { i += 1 }
    let k0 = keys[max(0, i - 1)], k1 = keys[i], k2 = keys[i + 1], k3 = keys[min(keys.count - 1, i + 2)]
    let x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]))
    let p0 = k0[c], p1 = k1[c], p2 = k2[c], p3 = k3[c]
    var m1 = (p2 - p0) / 2, m2 = (p3 - p1) / 2
    if (p1 - p0) * (p2 - p1) <= 0 { m1 = 0 }
    if (p2 - p1) * (p3 - p2) <= 0 { m2 = 0 }
    let x2 = x * x, x3 = x2 * x
    return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2
  }

  struct Shot { var eye: V3; var target: V3 }

  static func cameraAt(_ t: Double) -> Shot {
    let az = keyAt(t, 1) * Double.pi / 180
    let el = keyAt(t, 2) * Double.pi / 180
    let dist = keyAt(t, 3)
    let target = V3(keyAt(t, 4), keyAt(t, 5), keyAt(t, 6))
    let ce = cos(el)
    return Shot(eye: target + V3(sin(az) * ce * dist, sin(el) * dist, cos(az) * ce * dist), target: target)
  }

  static func nearestTurn(_ a: Double) -> Double { (a / (2 * Double.pi)).rounded() * 2 * Double.pi }

  struct Scene { var pose: Pose; var shot: Shot }

  /// The scene for stage time s, eased by r (0..1) toward the close (closeT) after a touch in the centred window.
  static func sceneAt(_ s: Double, _ r: Double) -> Scene {
    var p = poseAt(s)
    var c = cameraAt(s)
    if r > 0 {
      var q = poseAt(closeT)
      let d = cameraAt(closeT)
      q.recordYaw = nearestTurn(p.recordYaw)
      p.record = p.record + (q.record - p.record) * r
      p.recordYaw = lerp(p.recordYaw, q.recordYaw, r)
      p.sleeve = p.sleeve + (q.sleeve - p.sleeve) * r
      p.sleeveYaw = lerp(p.sleeveYaw, q.sleeveYaw, r)
      c.eye = c.eye + (d.eye - c.eye) * r
      c.target = c.target + (d.target - c.target) * r
    }
    return Scene(pose: p, shot: c)
  }

  static let hfovPhone = 34.0
  /// Focal length (points) for a stage w x h: the horizontal field of view, unless the stage is short for it.
  static func focalFor(_ w: Double, _ h: Double) -> Double {
    let t = tan(hfovPhone * Double.pi / 360)
    return min(w / 2 / t, h * 0.73 / 2 / t)
  }
  static func hfovFor(_ w: Double, _ h: Double) -> Double { 2 * atan(w / 2 / focalFor(w, h)) * 180 / Double.pi }

  struct Basis { var f: V3; var rx: Double; var rz: Double; var u: V3 }

  static func basis(_ c: Shot) -> Basis {
    var f = c.target - c.eye
    let fl = simd_length(f)
    f = fl > 0 ? f / fl : V3(0, 0, -1)
    var rx = -f.z, rz = f.x
    let rl = hypot(rx, rz)
    if rl > 0 { rx /= rl; rz /= rl }
    return Basis(f: f, rx: rx, rz: rz, u: V3(-rz * f.y, rz * f.x - rx * f.z, rx * f.y))
  }

  /// World point onto a stage w x h: (x, y, points per metre), y down; nil behind the camera.
  static func project(_ c: Shot, _ b: Basis, _ w: Double, _ h: Double, _ p: V3) -> V3? {
    let q = p - c.eye
    let d = simd_dot(q, b.f)
    if d < 0.02 { return nil }
    let f = focalFor(w, h)
    return V3(w / 2 + (q.x * b.rx + q.z * b.rz) / d * f, h / 2 - simd_dot(q, b.u) / d * f, f / d)
  }

  /// The groove highlight's angle in the record's own plane (the half vector between the eye and the key light).
  static func sheenAngle(_ s: Scene) -> Double {
    var v = s.shot.eye - s.pose.record
    let vl = simd_length(v)
    v = (vl > 0 ? v / vl : v) + toKey
    let a = s.pose.recordYaw
    let lx = cos(a) * v.x - sin(a) * v.z
    return atan2(v.y, lx)
  }

  static let shadowHalfW = [0.135, 0.165]
  static let shadowHalfD = [0.05, 0.055]
  static let shadowAlpha = [0.3, 0.24]

  struct Shadow { var x: Double, y: Double, rw: Double, rd: Double, angle: Double, alpha: Double }

  /// Shadow k (0 record, 1 sleeve) on the floor, projected onto the stage.
  static func shadowAt(_ s: Scene, _ k: Int, _ w: Double, _ h: Double) -> Shadow? {
    let b = basis(s.shot)
    let pos = k == 0 ? s.pose.record : s.pose.sleeve
    let yaw = k == 0 ? s.pose.recordYaw : s.pose.sleeveYaw
    let hw = shadowHalfW[k], hd = shadowHalfD[k]
    guard let p0 = project(s.shot, b, w, h, V3(pos.x, floorY, pos.z)),
          let p1 = project(s.shot, b, w, h, V3(pos.x + cos(yaw) * hw, floorY, pos.z - sin(yaw) * hw)),
          let p2 = project(s.shot, b, w, h, V3(pos.x + sin(yaw) * hd, floorY, pos.z + cos(yaw) * hd)) else { return nil }
    let ax = p1.x - p0.x, ay = p1.y - p0.y
    let lift = pos.y - floorY
    return Shadow(x: p0.x, y: p0.y, rw: max(1, hypot(ax, ay)), rd: max(1, hypot(p2.x - p0.x, p2.y - p0.y)),
                  angle: atan2(ay, ax), alpha: shadowAlpha[k] * min(1.2, 0.205 / max(0.12, lift)))
  }

  static func angleDelta(_ a: Double, _ b: Double) -> Double {
    var d = a - b
    while d > Double.pi { d -= 2 * Double.pi }
    while d < -Double.pi { d += 2 * Double.pi }
    return d
  }

  // MARK: UI

  /// The small "now playing" bars: three wobbling levels from the song position.
  static func barAt(_ k: Int, _ song: Double, _ level: Double) -> Double {
    let f = fract(song * 2)
    let kick = exp(-f * 5)
    let w = 0.5 + 0.5 * sin(song * (5.3 + Double(k) * 2.1) + Double(k) * 1.7)
    return level * (0.25 + 0.45 * w + 0.3 * kick * (k == 1 ? 1 : 0.6))
  }

  static func clock(_ s: Double) -> String {
    let v = max(0, Int(floor(s)))
    return "\(v / 60):\(v % 60 < 10 ? "0" : "")\(v % 60)"
  }
}
