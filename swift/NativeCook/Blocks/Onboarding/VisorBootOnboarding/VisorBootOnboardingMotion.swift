import Foundation
import simd

// Visor Boot: the scene's fixed numbers and the film, ported from React Native's visorScene.ts and visorMotion.ts.
// glTF space: y up, the helmet faces +z. Every film value is a pure function of the loop time t (0 <= t < loop), so
// t = loop gives the same frame as t = 0. Under a finger the film is replaced by the onboarding's pages: a float
// page p (0 intro .. 4 Get started); every channel has a film form f(t) and a page form g(p), and the screen shows
// lerp(f(t), g(p), m), where m eases 0 -> 1 when a touch takes over.
//
//  0.0 –  1.5  poster: the helmet three-quarter on its plinth, "Meet Halo"
//  1.5 –  5.0  step 1: the camera swings to the front; the HUD boots on the visor
//  5.0 –  9.0  step 2: the camera orbits to the side; the panel lamps ramp and pulse; the radar sweeps the ear sensor
//  9.0 – 13.0  step 3: a high angle; voice rings at the mouthpiece, a waveform and a transcript
// 13.0 – 15.2  Get Started grows out of the pager's pill; the helmet rises under a light sweep
// 15.2 – 17.0  back to the poster
// 17.0 – 18.0  rest; last frame = first frame
enum VisorBootOnboardingMotion {
  typealias V3 = SIMD3<Double>
  struct Shot { var eye: V3; var target: V3 }

  static let loop: Double = 18

  // MARK: scene (visorScene.ts)

  static let plinthH = 0.22
  static let plinthR = 1.55
  static let helmetY = 1.6787
  static let rise = 0.14

  static let hfovDeg = 36.0
  static let near = 0.2
  static let far = 60.0

  static let poster = Shot(eye: V3(-1.95, 2.58, 5.0), target: V3(0, 0.44, 0.05))
  static let front = Shot(eye: V3(0, 2.03, 5.37), target: V3(0, 0.7, 0.71))
  static let frontDrift = Shot(eye: V3(0.42, 2.06, 5.36), target: V3(0.04, 0.7, 0.71))
  static let side = Shot(eye: V3(-5.38, 2.05, -0.06), target: V3(0, 0.48, -0.01))
  static let sideDrift = Shot(eye: V3(-5.35, 2.17, 0.55), target: V3(-0.02, 0.48, 0.02))
  static let top = Shot(eye: V3(1.29, 5.47, 3.02), target: V3(0.15, 0.24, 0.34))
  static let topDrift = Shot(eye: V3(0.98, 5.46, 3.04), target: V3(0.11, 0.26, 0.32))
  static let riseShot = Shot(eye: V3(-1.28, 1.86, 5.23), target: V3(0.01, 0.48, -0.02))
  static let riseDrift = Shot(eye: V3(-1.52, 2.0, 5.17), target: V3(0.02, 0.51, -0.02))

  static let pageShots: [Shot] = [poster, frontDrift, sideDrift, topDrift, riseDrift]
  static let pages = 5

  static let hudQuad: [V3] = [V3(-0.9, 0.886, 0.933), V3(0.9, 0.886, 0.933), V3(0.9, 0.274, 1.307), V3(-0.9, 0.274, 1.307)]
  static let hudNormal = V3(0, 0.52, 0.85)
  static let hudW = 260.0
  static let hudH = 108.0

  static let portCenter = V3(-0.98, 0, -0.68)
  static let portAxisA = V3(0, 1, 0)
  static let portAxisB = V3(0, 0, -1)
  static let radarR = 0.8

  static let mouth = V3(0, -0.836, 1.26)
  static let mouthAxisA = V3(1, 0, 0)
  static let mouthAxisB = V3(0, 0.92, -0.4)

  /// Light (Filament units). See VisorBootOnboardingScene3D for the SceneKit mapping.
  static let iblIntensity = 24_000.0
  static let keyDirection = V3(0.55, -0.6, -0.55)
  static let keyLux = 52_000.0
  static let rimDirection = V3(0.5, -0.25, 0.85)
  static let rimLux = 60_000.0
  static let sweepLux = 90_000.0
  static let sweepFromDeg = -70.0
  static let sweepToDeg = 70.0
  static let lampStrength = 1.5

  static let ink: UInt32 = 0xEEF0F2
  static let mute: UInt32 = 0x99A1AB
  static let amber: UInt32 = 0xFFC27A
  static let bgEdge: UInt32 = 0x090B0E

  // MARK: phases

  static let idle = 2.0
  static let returnDuration = 1.4
  static let takeover = 0.45

  static let copyWindows: [(Double, Double)] = [(15.75, 1.55), (1.95, 5.15), (5.55, 9.15), (9.55, 13.05), (13.5, 15.45)]

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeInCubic(_ x: Double) -> Double { x * x * x }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
  static func easeOutBack3(_ x: Double) -> Double {
    let c1 = 0.72, c3 = c1 + 1
    return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
  }
  static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double {
    let k = sin(Double.pi * seg(t, a, b))
    return k * k
  }
  static func pageWeight(_ p: Double, _ i: Int) -> Double { clamp01(1 - abs(p - Double(i))) }
  static func blend(_ film: Double, _ page: Double, _ m: Double) -> Double { lerp(film, page, clamp01(m)) }

  /// Reanimated's Easing.bezier(0.65, 0, 0.35, 1).
  static func easeInOutTouch(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
    func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
    var lo = 0.0, hi = 1.0
    for _ in 0..<30 {
      let mid = (lo + hi) / 2
      if bx(mid) < x { lo = mid } else { hi = mid }
    }
    return by((lo + hi) / 2)
  }

  // MARK: copy, pager, button

  /// Copy block i in the film: (opacity, dy).
  static func filmCopy(_ i: Int, _ t: Double) -> (Double, Double) {
    let w = copyWindows[i]
    let inK = easeOutCubic(seg(t, w.0, w.0 + 0.45))
    let outK = easeInCubic(seg(t, w.1, w.1 + 0.3))
    if i == 0 {
      return t < 8 ? (1 - outK, -6 * outK) : (inK, 12 * (1 - inK))
    }
    return (inK * (1 - outK), outK > 0 ? -6 * outK : 12 * (1 - inK))
  }

  static func pageCopy(_ i: Int, _ p: Double) -> (Double, Double) {
    let d = Double(i) - p
    return (clamp01(1 - abs(d) * 2), max(-6, min(12, d * 24)))
  }

  static func filmDot(_ i: Int, _ t: Double) -> Double {
    let o = filmCopy(i, t).0
    if i < 3 { return o }
    return max(o, filmCopy(4, t).0)
  }

  static func pageDot(_ i: Int, _ p: Double) -> Double {
    i < 3 ? pageWeight(p, i) : clamp01(1 - abs(min(p, 3) - 3))
  }

  static func filmCta(_ t: Double) -> Double {
    if t < 15 { return easeOutBack3(seg(t, 13.2, 14.1)) }
    return 1 - easeInOutCubic(seg(t, 15.3, 16.1))
  }

  static func pageCta(_ p: Double) -> Double { clamp01(p - 3) }
  static func filmCtaAnchor(_ t: Double) -> Double { t < 15 ? 3 : 0 }

  // MARK: helmet and light

  static func filmRise(_ t: Double) -> Double {
    rise * (easeInOutCubic(seg(t, 13.0, 14.2)) - easeInOutCubic(seg(t, 15.3, 16.9)))
  }
  static func pageRise(_ p: Double) -> Double { rise * clamp01(p - 3) }

  static func filmLamps(_ t: Double) -> Double {
    let up = easeInOutCubic(seg(t, 5.9, 6.8))
    let down = easeInOutCubic(seg(t, 8.6, 9.6))
    let pulse = 2.6 * bump(t, 6.9, 7.9)
    let cta = 0.9 * bump(t, 13.4, 15.4)
    return 0.15 + (1.35 * up + pulse) * (1 - down) + cta
  }
  static func pageLamps(_ p: Double) -> Double { 0.15 + 1.35 * pageWeight(p, 2) + 0.6 * clamp01(p - 3) }

  /// The Get Started light sweep: (level 0..1, azimuth 0..1).
  static func filmSweep(_ t: Double) -> (Double, Double) {
    (bump(t, 13.5, 15.1), easeInOutSine(seg(t, 13.5, 15.1)))
  }

  // MARK: 2D modules

  static func filmHud(_ t: Double) -> Double { easeOutCubic(seg(t, 2.45, 2.85)) * (1 - easeInCubic(seg(t, 5.0, 5.35))) }
  static func filmBoot(_ t: Double) -> Double { seg(t, 2.5, 4.4) }
  static func filmRadar(_ t: Double) -> Double { easeOutCubic(seg(t, 6.3, 6.9)) * (1 - easeInCubic(seg(t, 8.75, 9.15))) }
  static func filmVoice(_ t: Double) -> Double { easeOutCubic(seg(t, 10.1, 10.6)) * (1 - easeInCubic(seg(t, 12.7, 13.05))) }
  static func filmTranscript(_ t: Double) -> Double { seg(t, 10.9, 12.1) }
  static func radarAngle(_ s: Double) -> Double { 2 * Double.pi * s / 2.1 }
  static func heading(_ s: Double) -> Double { 24 + 7 * sin(2 * Double.pi * s / 9) + 2.5 * sin(2 * Double.pi * s / 3.6) }

  static func voiceBar(_ i: Int, _ n: Int, _ s: Double) -> Double {
    let x = Double(i) / Double(n - 1)
    let env = sin(Double.pi * x)
    let h = abs(sin(Double(i) * 12.9898) * 43758.5453).truncatingRemainder(dividingBy: 1)
    let v = 0.5
      + 0.22 * sin(2 * Double.pi * (s * 1.7 + x * 1.3))
      + 0.16 * sin(2 * Double.pi * (s * 3.1 - x * 2.2) + h * 6)
      + 0.12 * sin(2 * Double.pi * (s * 0.6 + h))
    let syll = 0.55 + 0.45 * max(0, sin(2 * Double.pi * s * 1.25))
    return clamp01(env * v * syll * (0.75 + 0.5 * h))
  }

  static func voiceWaveY(_ ay: Double, _ h: Double, _ limitY: Double) -> Double {
    min(max(ay + 64, h * 0.3), limitY - 64)
  }

  // MARK: camera

  static func blendShot(_ a: Shot, _ b: Shot, _ k: Double) -> Shot {
    let az0 = atan2(a.eye.x, a.eye.z)
    var az1 = atan2(b.eye.x, b.eye.z)
    if az1 - az0 > Double.pi { az1 -= 2 * Double.pi }
    if az0 - az1 > Double.pi { az1 += 2 * Double.pi }
    let r0 = hypot(a.eye.x, a.eye.z), r1 = hypot(b.eye.x, b.eye.z)
    let az = lerp(az0, az1, k), r = lerp(r0, r1, k)
    return Shot(eye: V3(sin(az) * r, lerp(a.eye.y, b.eye.y, k), cos(az) * r),
                target: a.target + (b.target - a.target) * k)
  }

  private struct Key { let t: Double; let shot: Shot; let hold: Bool }
  private static let keys: [Key] = [
    Key(t: 0, shot: poster, hold: true),
    Key(t: 1.5, shot: poster, hold: true),
    Key(t: 3.2, shot: front, hold: false),
    Key(t: 5.0, shot: frontDrift, hold: false),
    Key(t: 6.8, shot: side, hold: false),
    Key(t: 8.9, shot: sideDrift, hold: false),
    Key(t: 10.8, shot: top, hold: false),
    Key(t: 12.9, shot: topDrift, hold: false),
    Key(t: 14.4, shot: riseShot, hold: false),
    Key(t: 15.2, shot: riseDrift, hold: false),
    Key(t: 17.0, shot: poster, hold: true),
    Key(t: loop, shot: poster, hold: true),
  ]

  private static let cyl: [[Double]] = {
    var out: [[Double]] = []
    var prev = 0.0
    for (i, k) in keys.enumerated() {
      let e = k.shot.eye, g = k.shot.target
      var az = atan2(e.x, e.z)
      if i > 0 {
        while az - prev > Double.pi { az -= 2 * Double.pi }
        while prev - az > Double.pi { az += 2 * Double.pi }
      }
      prev = az
      out.append([az, hypot(e.x, e.z), e.y, g.x, g.y, g.z])
    }
    return out
  }()

  private static let tangents: [[Double]] = {
    var out: [[Double]] = []
    for i in 0..<keys.count {
      var row: [Double] = []
      for c in 0..<6 {
        if keys[i].hold || i == 0 || i == keys.count - 1 { row.append(0); continue }
        let h0 = keys[i].t - keys[i - 1].t, h1 = keys[i + 1].t - keys[i].t
        let d0 = (cyl[i][c] - cyl[i - 1][c]) / h0, d1 = (cyl[i + 1][c] - cyl[i][c]) / h1
        if d0 * d1 <= 0 { row.append(0); continue }
        let w1 = 2 * h1 + h0, w2 = h1 + 2 * h0
        row.append((w1 + w2) / (w1 / d0 + w2 / d1))
      }
      out.append(row)
    }
    return out
  }()

  static func filmCamera(_ t: Double) -> Shot {
    let n = keys.count
    var i = 0
    while i < n - 2 && t >= keys[i + 1].t { i += 1 }
    let t0 = keys[i].t, h = keys[i + 1].t - t0
    let u = clamp01((t - t0) / h)
    let h00 = 2 * u * u * u - 3 * u * u + 1, h10 = u * u * u - 2 * u * u + u
    let h01 = -2 * u * u * u + 3 * u * u, h11 = u * u * u - u * u
    var v = [Double](repeating: 0, count: 6)
    for c in 0..<6 {
      v[c] = h00 * cyl[i][c] + h10 * h * tangents[i][c] + h01 * cyl[i + 1][c] + h11 * h * tangents[i + 1][c]
    }
    return Shot(eye: V3(sin(v[0]) * v[1], v[2], cos(v[0]) * v[1]), target: V3(v[3], v[4], v[5]))
  }

  static func pageCamera(_ p: Double) -> Shot {
    let q = max(0, min(Double(pageShots.count - 1), p))
    let i = min(pageShots.count - 2, Int(floor(q)))
    return blendShot(pageShots[i], pageShots[i + 1], q - Double(i))
  }

  static func applyOrbit(_ c: Shot, yawDeg: Double, pitchDeg: Double) -> Shot {
    if yawDeg == 0 && pitchDeg == 0 { return c }
    let v = c.eye - c.target
    let r = simd_length(v), flat = hypot(v.x, v.z)
    let az = atan2(v.x, v.z) + yawDeg * Double.pi / 180
    let el = max(-0.08, min(1.35, atan2(v.y, flat) + pitchDeg * Double.pi / 180))
    return Shot(eye: c.target + V3(sin(az) * cos(el) * r, sin(el) * r, cos(az) * cos(el) * r), target: c.target)
  }

  static func cameraAt(_ s: VisorBootOnboardingState) -> Shot {
    var c = filmCamera(s.t)
    let k = clamp01(s.m)
    if k > 0 { c = blendShot(c, pageCamera(s.page), easeInOutSine(k)) }
    return applyOrbit(c, yawDeg: s.yaw, pitchDeg: s.pitch)
  }

  static func pageOfFilm(_ t: Double) -> Double {
    if t < 1.8 || t >= 15.6 { return 0 }
    if t < 5.2 { return 1 }
    if t < 9.2 { return 2 }
    if t < 13.1 { return 3 }
    return 4
  }

  // MARK: projection (the stage camera's own maths)

  struct Basis { var f: V3; var rx: Double; var rz: Double; var u: V3; var focal: Double }

  static func basis(_ c: Shot, width: Double) -> Basis {
    var f = c.target - c.eye
    let fl = simd_length(f)
    f = fl > 0 ? f / fl : V3(0, 0, -1)
    var rx = -f.z, rz = f.x
    let rl = hypot(rx, rz)
    if rl > 0 { rx /= rl; rz /= rl }
    let u = V3(-rz * f.y, rz * f.x - rx * f.z, rx * f.y)
    return Basis(f: f, rx: rx, rz: rz, u: u, focal: width / 2 / tan(hfovDeg * Double.pi / 360))
  }

  /// A world point on screen (pt, y down) and its depth, nil behind the camera.
  static func project(_ c: Shot, _ b: Basis, _ w: Double, _ h: Double, _ p: V3) -> (x: Double, y: Double, z: Double)? {
    let d = p - c.eye
    let z = simd_dot(d, b.f)
    if z < 0.05 { return nil }
    return (w / 2 + (d.x * b.rx + d.z * b.rz) / z * b.focal, h / 2 - simd_dot(d, b.u) / z * b.focal, z)
  }

  static func hudFacing(_ c: Shot, helmetY: Double) -> Double {
    let v = V3(c.eye.x, c.eye.y - (helmetY + 0.58), c.eye.z - 1.1)
    let l = simd_length(v)
    let d = simd_dot(v, hudNormal) / (l > 0 ? l : 1)
    return clamp01((d - 0.45) / 0.35)
  }

  /// Homography from the canonical rect (0,0)-(w,h) to the quad q (TL, TR, BR, BL): row-major 3x3.
  static func rectToQuad(_ q: [Double], _ w: Double, _ h: Double) -> [Double] {
    let x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], x2 = q[4], y2 = q[5], x3 = q[6], y3 = q[7]
    let dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3
    let dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3
    var g = 0.0, hh = 0.0
    let det = dx1 * dy2 - dx2 * dy1
    if abs(det) > 1e-9 {
      g = (dx3 * dy2 - dx2 * dy3) / det
      hh = (dx1 * dy3 - dx3 * dy1) / det
    }
    let a = x1 - x0 + g * x1, bb = x3 - x0 + hh * x3
    let d = y1 - y0 + g * y1, e = y3 - y0 + hh * y3
    return [a / w, bb / h, x0, d / w, e / h, y0, g / w, hh / h, 1]
  }

  static func helmetPoint(_ p: V3, lift: Double) -> V3 { V3(p.x, p.y + helmetY + lift, p.z) }
}

/// Everything a frame is drawn from: the film time, the pages, the blend between them, the finger's orbit and the
/// running time of the periodic modules (radar, compass, voice).
struct VisorBootOnboardingState {
  var t: Double = 0
  var page: Double = 0
  var m: Double = 0
  var yaw: Double = 0
  var pitch: Double = 0
  var run: Double = 0

  typealias M = VisorBootOnboardingMotion
  var lift: Double { M.blend(M.filmRise(t), M.pageRise(page), m) }
  var lamps: Double { M.blend(M.filmLamps(t), M.pageLamps(page), m) }
}
