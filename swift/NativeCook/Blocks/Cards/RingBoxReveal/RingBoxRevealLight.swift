import SwiftUI
import simd

/// RingLight.tsx: the floor behind the 3D view (paper, studio light, the box's shadows), the reflection's fade over it,
/// and the stone's glints, all projected with the stage's camera.
enum RingBoxRevealLight {
  typealias M = RingBoxRevealMotion

  static let fpArc = 9
  static let fpN = fpArc * 4
  static let floorNear = 0.6
  static let starMax = 26.0
  static let tints: [SIMD4<Double>] = [SIMD4(255, 255, 255, 1), SIMD4(170, 196, 255, 0.2), SIMD4(255, 214, 200, 0.35)]
  static let fire: [SIMD3<Double>] = [SIMD3(255, 120, 110), SIMD3(255, 214, 120), SIMD3(140, 255, 190), SIMD3(120, 190, 255), SIMD3(210, 150, 255)]

  struct Geom {
    var pts: [CGPoint]
    var cx: Double, cy: Double, a: Double, b: Double, boxH: Double
  }

  static func rgb(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
  static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
    Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
  }

  /// The box's footprint, turned with the box and projected, with its screen centre, half extents and height.
  static func floorGeom(_ c: M.Shot, _ b: M.Basis, _ w: Double, _ h: Double, _ yaw: Double) -> Geom? {
    let ins = M.baseR * 0.8
    let hw = M.boxW / 2 - ins, hd = M.boxD / 2 - ins, r = M.boxRV - ins
    let cy = cos(yaw), sy = sin(yaw)
    var pts: [CGPoint] = []
    var x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9
    for j in 0..<fpN {
      let corner = j / fpArc, k = j % fpArc
      let a = (Double(corner) + Double(k) / Double(fpArc - 1)) * Double.pi / 2
      let ccx = corner == 0 || corner == 3 ? hw - r : r - hw
      let ccz = corner == 0 || corner == 1 ? hd - r : r - hd
      let x = ccx + r * cos(a), z = ccz + r * sin(a)
      guard let pt = M.project(c, b, w, h, M.V3(x * cy + z * sy, 0, -x * sy + z * cy)) else { return nil }
      pts.append(CGPoint(x: pt.x, y: pt.y))
      x0 = min(x0, pt.x); x1 = max(x1, pt.x); y0 = min(y0, pt.y); y1 = max(y1, pt.y)
    }
    guard let o = M.project(c, b, w, h, M.V3(0, 0, 0)), let top = M.project(c, b, w, h, M.V3(0, M.baseH, 0)) else { return nil }
    return Geom(pts: pts, cx: (x0 + x1) / 2, cy: (y0 + y1) / 2, a: max(1, (x1 - x0) / 2), b: max(1, (y1 - y0) / 2),
                boxH: hypot(top.x - o.x, top.y - o.y))
  }

  /// The paper's gradient, a soft studio light and (with a footprint) the box's broad and contact shadows.
  static func drawFloor(_ ctx: inout GraphicsContext, _ w: Double, _ h: Double, _ paperTop: Double, _ paperH: Double, _ g: Geom?) {
    let rect = Path(CGRect(x: 0, y: 0, width: w, height: h))
    ctx.fill(rect, with: .linearGradient(Gradient(colors: [rgb(M.bgTop), rgb(M.bgBottom)]),
                                         startPoint: CGPoint(x: 0, y: paperTop), endPoint: CGPoint(x: 0, y: paperTop + paperH)))
    ctx.fill(rect, with: .radialGradient(Gradient(colors: [rgba(255, 253, 249, 0.8), rgba(255, 253, 249, 0)]),
                                         center: CGPoint(x: w * 0.42, y: h * 0.22), startRadius: 0, endRadius: w * 0.9))
    guard let g else { return }
    // broad shadow: a soft pool under and a little in front of the box
    let sx = g.cy + g.b * 0.12
    var s = ctx
    s.translateBy(x: g.cx, y: sx)
    let ky = g.b / g.a * 1.15
    s.scaleBy(x: 1, y: ky)
    s.fill(Path(CGRect(x: -w * 2, y: -h * 2 / ky, width: w * 4, height: h * 4 / ky)),
           with: .radialGradient(Gradient(stops: [.init(color: rgba(22, 24, 38, 0.2), location: 0), .init(color: rgba(22, 24, 38, 0.07), location: 0.6),
                                                  .init(color: rgba(22, 24, 38, 0), location: 1)]),
                                 center: .zero, startRadius: 0, endRadius: g.a * 1.4))
    // contact: the footprint itself, dark and blurred
    var path = Path()
    path.addLines(g.pts)
    path.closeSubpath()
    ctx.drawLayer { l in
      l.addFilter(.blur(radius: max(1.2, g.a * 0.03)))
      l.fill(path, with: .color(rgba(14, 16, 28, 0.55)))
    }
  }

  /// The floor below the box's front edge: the footprint's front chain, out to the sides and down to the bottom.
  static func frontRegion(_ g: Geom, _ w: Double, _ h: Double) -> Path {
    let p = g.pts
    var iL = 0, iR = 0, iB = 0
    for j in 1..<fpN {
      if p[j].x < p[iL].x { iL = j }
      if p[j].x > p[iR].x { iR = j }
      if p[j].y > p[iB].y { iB = j }
    }
    var passes = iB == iR
    var j = iL
    while j != iR {
      if j == iB { passes = true }
      j = (j + 1) % fpN
    }
    let dir = passes ? 1 : -1
    var path = Path()
    path.move(to: CGPoint(x: -2, y: p[iL].y))
    j = iL
    for _ in 0...fpN {
      path.addLine(to: p[j])
      if j == iR { break }
      j = (j + dir + fpN) % fpN
    }
    path.addLine(to: CGPoint(x: w + 2, y: p[iR].y))
    path.addLine(to: CGPoint(x: w + 2, y: h + 2))
    path.addLine(to: CGPoint(x: -2, y: h + 2))
    path.closeSubpath()
    return path
  }

  static func backdrop(_ ctx: inout GraphicsContext, size: CGSize, pose p: RingBoxRevealPose, paperTop: Double, paperH: Double) {
    let w = Double(size.width), h = Double(size.height)
    let c = M.cameraAt(p.open, p.shot, p.t)
    let b = M.basis(c, width: w)
    drawFloor(&ctx, w, h, paperTop, paperH, floorGeom(c, b, w, h, p.yaw))
  }

  /// Over the 3D view: the floor in front of the box drawn again over the baked reflection, see-through at the
  /// footprint and solid a box height away.
  static func floorFade(_ ctx: inout GraphicsContext, size: CGSize, pose p: RingBoxRevealPose, paperTop: Double, paperH: Double) {
    let w = Double(size.width), h = Double(size.height)
    let c = M.cameraAt(p.open, p.shot, p.t)
    let b = M.basis(c, width: w)
    guard let g = floorGeom(c, b, w, h, p.yaw), g.cy - g.b <= h else { return }
    let k = max(0.3, 0.9 * g.boxH / g.b)
    ctx.drawLayer { l in
      l.clip(to: frontRegion(g, w, h))
      drawFloor(&l, w, h, paperTop, paperH, g)
      l.blendMode = .destinationIn
      var m = l
      m.translateBy(x: g.cx, y: g.cy)
      let ky = g.b / g.a
      m.scaleBy(x: 1, y: ky)
      let R = g.a * (1 + k)
      m.fill(Path(CGRect(x: -w * 2, y: -h * 2 / ky, width: w * 4, height: h * 4 / ky)),
             with: .radialGradient(Gradient(stops: [
               .init(color: .black.opacity(0), location: 0),
               .init(color: .black.opacity(0), location: 0.98 / (1 + k)),
               .init(color: .black.opacity(floorNear), location: 1.06 / (1 + k)),
               .init(color: .black, location: 1),
             ]), center: .zero, startRadius: 0, endRadius: R))
    }
  }

  /// Over the 3D view: forty crown facets turned with the box, each flashing as a four-point star when it mirrors a
  /// studio light into the lens; a soft halo; spectral fire for a diamond; a burst as the lid snaps.
  static func sparkle(_ ctx: inout GraphicsContext, size: CGSize, pose p: RingBoxRevealPose) {
    let w = Double(size.width), h = Double(size.height)
    let vis = M.sparkleOf(p.open)
    if vis <= 0.002 { return }
    let t = p.t, yaw = p.yaw
    let burst = M.clamp01(p.burst)
    let sk = M.clamp01(p.sProg)
    let ta = tints[p.sFrom], tb = tints[p.sTo]
    let tint = ta + (tb - ta) * sk
    let glow = M.lerp(M.stones[p.sFrom].glow, M.stones[p.sTo].glow, sk) * 0.1
    let k = cbrt(p.carat / M.caratModel)
    let c = M.cameraAt(p.open, p.shot, t)
    let b = M.basis(c, width: w)
    guard let cen = M.project(c, b, w, h, M.stoneC) else { return }
    let rpx = M.stoneR * k * b.focal / cen.z
    let rs = min(rpx, starMax)
    var g = ctx
    g.blendMode = .plusLighter
    let halo = vis * (0.1 + glow + 0.45 * burst)
    let hr = rpx * (2.2 + 2.5 * burst)
    let hc = CGPoint(x: cen.x, y: cen.y - rpx * 0.3)
    g.fill(Path(ellipseIn: CGRect(x: hc.x - hr, y: hc.y - hr, width: 2 * hr, height: 2 * hr)),
           with: .radialGradient(Gradient(colors: [rgba(255, 255, 255, 0.5 * halo), rgba(255, 255, 255, 0)]),
                                 center: hc, startRadius: 0, endRadius: hr))
    let cy = cos(yaw), sy = sin(yaw)
    for i in 0..<40 {
      let f = M.facet(i, M.stoneR * k, M.stoneCrown * k)
      let n = M.V3(f.n.x * cy + f.n.z * sy, f.n.y, -f.n.x * sy + f.n.z * cy)
      let wp = M.V3(M.stoneC.x + f.p.x * cy + f.p.z * sy, M.stoneC.y + f.p.y, M.stoneC.z - f.p.x * sy + f.p.z * cy)
      let v = simd_normalize(c.eye - wp)
      let fi = Double(i)
      let cyc = 2 + floor(M.hash(fi + 29) * 5)
      let tw = 0.75 + 0.25 * sin(2 * Double.pi * cyc * t / M.loop + M.hash(fi + 31) * 6.28)
      let gl = M.glint(n, v) * tw * vis * (1 + 2.4 * burst) + burst * 0.5 * M.hash(fi + 41) * (i % 3 == 0 ? 1 : 0)
      if gl < 0.04 { continue }
      guard let pt = M.project(c, b, w, h, wp) else { continue }
      let s = min(1.6, gl)
      let len = rs * (0.5 + 1.5 * s)
      let fc = fire[i % fire.count]
      let fk = tint.w * 0.6 * Double(i % 2)
      let col = SIMD3(tint.x, tint.y, tint.z) + (fc - SIMD3(tint.x, tint.y, tint.z)) * fk
      let a = min(1, 0.35 + 0.65 * s)
      let color = rgba(col.x.rounded(), col.y.rounded(), col.z.rounded(), a)
      var cross = Path()
      cross.move(to: CGPoint(x: pt.x - len, y: pt.y)); cross.addLine(to: CGPoint(x: pt.x + len, y: pt.y))
      cross.move(to: CGPoint(x: pt.x, y: pt.y - len)); cross.addLine(to: CGPoint(x: pt.x, y: pt.y + len))
      g.stroke(cross, with: .color(color), style: StrokeStyle(lineWidth: max(0.6, rs * 0.06), lineCap: .round))
      let d = len * 0.42
      var diag = Path()
      diag.move(to: CGPoint(x: pt.x - d, y: pt.y - d)); diag.addLine(to: CGPoint(x: pt.x + d, y: pt.y + d))
      diag.move(to: CGPoint(x: pt.x - d, y: pt.y + d)); diag.addLine(to: CGPoint(x: pt.x + d, y: pt.y - d))
      g.stroke(diag, with: .color(color), style: StrokeStyle(lineWidth: max(0.5, rs * 0.04), lineCap: .round))
      let dr = max(0.8, rs * 0.09 * (0.6 + s))
      g.fill(Path(ellipseIn: CGRect(x: pt.x - dr, y: pt.y - dr, width: 2 * dr, height: 2 * dr)),
             with: .color(rgba(255, 255, 255, 0.9 * a)))
    }
  }
}
