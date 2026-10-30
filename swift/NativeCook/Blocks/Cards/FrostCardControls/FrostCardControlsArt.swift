import SwiftUI
import UIKit

struct FrostCardControlsArt {
  typealias T = FrostCardControlsTheme
  typealias M = FrostCardControlsMotion
  let resources = FrostCardControlsResources.shared
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
  }
  func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
  }
  func linear(_ colors: [(Int, Double, Double)], _ start: CGPoint, _ end: CGPoint)
    -> GraphicsContext.Shading
  {
    .linearGradient(
      Gradient(stops: colors.map { .init(color: T.color($0.0, $0.1), location: $0.2) }),
      startPoint: start, endPoint: end)
  }
  func line(
    _ c: GraphicsContext, _ points: [CGPoint], _ shading: GraphicsContext.Shading,
    _ width: Double = 2.4
  ) {
    var path = Path()
    if let first = points.first { path.move(to: first) }
    for point in points.dropFirst() { path.addLine(to: point) }
    c.stroke(
      path, with: shading, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  func drawIcon(_ context: GraphicsContext, _ index: Int, _ x: Double, _ y: Double, _ spin: Double)
  {
    var c = context
    c.translateBy(x: x, y: y)
    if index == 0 {
      c.rotate(by: .degrees(spin))
      let shade = linear(
        [(0x4B2BE0, 1, 0), (0x8B46FF, 1, 1)], CGPoint(x: -12, y: -12), CGPoint(x: 12, y: 12))
      // The gradient rotates with each branch, as in Skia's saved canvas transforms.
      for k in 0..<6 {
        var branch = c
        branch.rotate(by: .degrees(Double(k) * 60))
        line(branch, [.zero, CGPoint(x: 0, y: -12)], shade)
        line(branch, [CGPoint(x: 0, y: -7.5), CGPoint(x: -3.6, y: -10.6)], shade)
        line(branch, [CGPoint(x: 0, y: -7.5), CGPoint(x: 3.6, y: -10.6)], shade)
      }
    } else if index == 1 {
      for (from, color) in [(200.0, 0xFF6A1A), (20.0, 0x5B2BE6)] {
        var arc = Path()
        arc.addArc(
          center: .zero, radius: 11, startAngle: .degrees(from), endAngle: .degrees(from + 130),
          clockwise: false)
        c.stroke(
          arc, with: .color(T.color(color)),
          style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
      }
      line(
        c, [CGPoint(x: 6.5, y: -12.5), CGPoint(x: 10.5, y: -8), CGPoint(x: 4.5, y: -6.5)],
        .color(T.color(0xFF6A1A)))
      line(
        c, [CGPoint(x: -6.5, y: 12.5), CGPoint(x: -10.5, y: 8), CGPoint(x: -4.5, y: 6.5)],
        .color(T.color(0x5B2BE6)))
    } else {
      let shade = linear(
        [(0x5B2BE6, 1, 0), (0xFF6A1A, 1, 1)], CGPoint(x: -12, y: -12), CGPoint(x: 12, y: 12))
      c.stroke(circle(0, 0, 11), with: shade, lineWidth: 2.4)
      line(c, [CGPoint(x: -8, y: -8), CGPoint(x: 8, y: 8)], shade)
    }
  }
  func drawPage(_ context: GraphicsContext, _ frame: M.Frame, _ size: CGSize) {
    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(T.color(T.C.bg)))
    var c = context
    let s = size.width / T.REF_W
    c.scaleBy(x: s, y: s)
    c.draw(Image(uiImage: resources.page), in: CGRect(x: 0, y: 0, width: 402, height: 1100))
    for i in 0..<3 {
      let x = T.UI.tileXs[i]
      let w = T.UI.tileW
      let h = T.UI.tileH
      let y = T.UI.tileY
      let k = 1 - 0.04 * frame.press[i]
      var tile = c
      tile.translateBy(x: x + w / 2, y: y + h / 2)
      tile.scaleBy(x: k, y: k)
      tile.translateBy(x: -(x + w / 2), y: -(y + h / 2))
      let tint = i == 0 ? frame.frozenLabel : 0
      let color = Color(.sRGB, red: M.mix(1, 0.9, tint), green: M.mix(1, 0.95, tint), blue: 1)
      tile.fill(rect(x, y, w, h, T.UI.tileRadius), with: .color(color))
      drawIcon(tile, i, x + w / 2, T.UI.iconY, i == 0 ? frame.spin : 0)
      if i == 0 {
        let p = frame.frozenLabel
        resources.label(
          T.Copy.tiles[0], tile, x + w / 2 - resources.labelWidth(T.Copy.tiles[0]) / 2,
          T.UI.labelY + 5.4 - 6 * p, T.C.ink, 1 - p)
        resources.label(
          T.Copy.unfreeze, tile, x + w / 2 - resources.labelWidth(T.Copy.unfreeze) / 2,
          T.UI.labelY + 5.4 + 6 * (1 - p), T.C.ink, p)
      } else {
        let copy = T.Copy.tiles[i]
        resources.label(
          copy, tile, x + w / 2 - resources.labelWidth(copy) / 2, T.UI.labelY + 5.4, T.C.ink)
      }
    }
  }
  func ice(_ f: M.Frame, _ ambient: Double, _ mask: Double) -> GraphicsContext.Shading {
    .shader(
      ShaderLibrary.FrostCardControlsIce(
        .float2(T.UI.card.width, T.UI.card.height),
        .float2(f.cx, f.cy), .float(f.frost), .float(f.melt), .float(ambient), .float(mask)))
  }
  func drawCard(_ context: GraphicsContext, _ f: M.Frame, _ d: M.Driver, _ size: CGSize) {
    guard f.card > 0.5 else { return }
    var c = context
    let s = size.width / T.REF_W
    c.scaleBy(x: s, y: s)
    c.translateBy(x: T.UI.card.minX, y: T.UI.card.minY)
    let w = T.UI.card.width
    let h = T.UI.card.height
    let bounds = CGRect(x: 0, y: 0, width: w, height: h)
    if f.shred >= 0 {
      let n = 14
      let sw = w / 14
      for i in 0..<n {
        let order = (i * 7) % n
        let u = max(0, (f.shred - 0.18 - Double(order) * 0.025) / 0.6)
        let gap = M.smooth(f.shred / 0.18) * (Double(i) - Double(n - 1) / 2) * 0.9
        let fall = 520 * u * u
        let rotation = (i % 2 == 0 ? 1.0 : -1.0) * 18 * u
        let alpha = 1 - M.smooth((u - 0.6) / 0.4)
        if alpha <= 0.01 { continue }
        var strip = c
        strip.opacity *= alpha
        strip.translateBy(x: Double(i) * sw + sw / 2 + gap, y: h / 2 + fall)
        strip.rotate(by: .degrees(rotation))
        strip.clip(to: rect(-sw / 2, -h / 2, sw, h))
        strip.draw(
          Image(uiImage: resources.card),
          in: CGRect(x: -sw / 2 - Double(i) * sw, y: -h / 2, width: w, height: h))
      }
      return
    }
    let rr = rect(0, 0, w, h, T.UI.cardRadius)
    var shadow = c
    shadow.addFilter(.blur(radius: 14))
    shadow.fill(
      rect(6, 12, w - 12, h, 14),
      with: .color(Color(.sRGB, red: 0.1, green: 0.12, blue: 0.2, opacity: 0.16)))
    c.draw(Image(uiImage: resources.card), in: bounds)
    var clipped = c
    clipped.clip(to: rr)
    let sx = w * (0.5 + 0.45 * d.tiltX) + (f.shine >= 0 ? M.mix(-w, w, f.shine) : 0)
    var sheen = clipped
    sheen.blendMode = .plusLighter
    sheen.fill(
      Path(bounds),
      with: linear(
        [(0xFFFFFF, 0, 0), (0xFFFFFF, f.shine >= 0 ? 0.5 : 0.18, 0.5), (0xFFFFFF, 0, 1)],
        CGPoint(x: sx - 60, y: 0), CGPoint(x: sx + 60, y: h)))
    if f.frost > 0.001 {
      clipped.drawLayer { layer in
        layer.draw(Image(uiImage: resources.frosted), in: bounds)
        layer.blendMode = .destinationIn
        layer.fill(Path(bounds), with: ice(f, d.amb, 1))
      }
      clipped.fill(
        Path(bounds),
        with: .color(
          Color(
            .sRGB, red: 0.55, green: 0.75, blue: 1,
            opacity: 0.12 * min(1, f.frost) * (1 - f.melt))))
      clipped.fill(Path(bounds), with: ice(f, d.amb, 0))
      let iced = M.smooth((f.frost - 0.85) / 0.3) * (1 - f.melt)
      if iced > 0.01 {
        var sparkle = clipped
        sparkle.blendMode = .plusLighter
        for k in 0..<9 {
          let hx = sin(Double(k + 1) * 78.233) * 43758.5453
          let hy = sin(Double(k + 1) * 12.9898) * 43758.5453
          let px = (hx - floor(hx)) * (w - 30) + 15
          let py = (hy - floor(hy)) * (h - 30) + 15
          let tw = max(0, sin(2 * .pi * (d.amb / 3 + Double(k) * 0.37) + d.tiltX * 3))
          let a = iced * tw * tw * tw
          if a < 0.02 { continue }
          let arm = 3 + 5 * tw
          var cross = Path()
          cross.move(to: CGPoint(x: px - arm, y: py)); cross.addLine(to: CGPoint(x: px + arm, y: py))
          cross.move(to: CGPoint(x: px, y: py - arm)); cross.addLine(to: CGPoint(x: px, y: py + arm))
          sparkle.stroke(cross, with: .color(T.color(0xFFFFFF, 0.85 * a)), lineWidth: 1.3)
          sparkle.fill(circle(px, py, 1.6), with: .color(T.color(0xFFFFFF, 0.85 * a)))
        }
      }
    }
    if f.drips >= 0 {
      for k in 0..<7 {
        let x = 24 + (Double(k * 53).truncatingRemainder(dividingBy: w - 40))
        let st = 0.12 * Double((k * 3) % 5)
        let u = max(0, (f.drips - st) / (1 - st))
        if u <= 0 { continue }
        let length = 26 + 34 * u
        let yb = 20 + (h + 10) * u * u
        let alpha = 0.5 * (1 - M.smooth((u - 0.75) / 0.25))
        line(
          clipped, [CGPoint(x: x, y: yb - length), CGPoint(x: x, y: yb)],
          linear(
            [(0xCFE6FF, 0, 0), (0xBFDDFF, alpha, 1)], CGPoint(x: x, y: yb - length),
            CGPoint(x: x, y: yb)), 3)
        clipped.fill(circle(x, yb + 1, 2.6), with: .color(T.color(0xE8F4FF, alpha * 1.4)))
      }
    }
    let cold = M.smooth((f.frost - 0.85) / 0.3) * (1 - f.melt)
    if cold > 0.01 {
      var mist = c
      mist.addFilter(.blur(radius: 10))
      for k in 0..<5 {
        let u = (d.amb / 4.5 + Double(k) / 5).truncatingRemainder(dividingBy: 1)
        let x = w * (0.12 + 0.19 * Double(k)) + 10 * sin(2 * .pi * (u + Double(k) * 0.3))
        let y = h - 6 + 46 * u
        let a = cold * 0.32 * sin(.pi * u)
        mist.fill(Path(ellipseIn: CGRect(x: x - 26 - 14 * u, y: y - 7, width: 52 + 28 * u, height: 14 + 6 * u)),
          with: .color(Color(.sRGB, red: 0.86, green: 0.93, blue: 1, opacity: a)))
      }
    }
    let k = f.frozenLabel * (1 - f.melt)
    if k > 0.01 {
      var chip = c
      chip.translateBy(x: w - 58, y: 16)
      chip.scaleBy(x: 0.9 + 0.1 * k, y: 0.9 + 0.1 * k)
      chip.fill(rect(-4, -3, 52, 22, 11), with: .color(T.color(0xFFFFFF, 0.75 * k)))
      resources.label(T.Copy.frozen, chip, 5, 12.5, 0x2E5DD8, k)
    }
  }
  // Homography for P(700) · rotateY(tiltX*.28) · rotateX(-tiltY*.22), followed by
  // the card's 2D scale / rotation / translation. Keeps the complete card layer (including ice) in 3D.
  func projection(_ f: M.Frame, _ d: M.Driver, _ scale: Double) -> ProjectionTransform {
    let centerX = T.UI.card.midX * scale
    let centerY = T.UI.card.midY * scale
    let positionX = centerX + f.out * 460 * scale
    let positionY = centerY + (1 - f.drop) * -330 * scale
    let rotation = (f.out * 24 + (1 - f.drop) * -8) * .pi / 180
    let sc = 0.94 + 0.06 * f.drop
    let active = abs(d.tiltX) > 0.001 || abs(d.tiltY) > 0.001
    let ax = active ? -d.tiltY * 0.22 : 0
    let ay = active ? d.tiltX * 0.28 : 0
    let a = cos(ay)
    let b = sin(ay) * sin(ax)
    let e = cos(ax)
    let qx = sin(ay) / (700 * scale)
    let qy = -cos(ay) * sin(ax) / (700 * scale)
    let xx = sc * cos(rotation) * a + positionX * qx
    let xy = sc * (cos(rotation) * b - sin(rotation) * e) + positionX * qy
    let yx = sc * sin(rotation) * a + positionY * qx
    let yy = sc * (sin(rotation) * b + cos(rotation) * e) + positionY * qy
    var m = CATransform3DIdentity
    m.m11 = xx
    m.m21 = xy
    m.m41 = positionX - xx * centerX - xy * centerY
    m.m12 = yx
    m.m22 = yy
    m.m42 = positionY - yx * centerX - yy * centerY
    m.m14 = qx
    m.m24 = qy
    m.m44 = 1 - qx * centerX - qy * centerY
    return ProjectionTransform(m)
  }
}
