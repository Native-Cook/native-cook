import CoreImage
import ImageIO
import QuartzCore
import SwiftUI
import UIKit

// Matrices are post-multiplied in the same order as Skia processTransform3d.
struct GlassPlaylistFolderMatrix {
  var m: [Double] = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
  static func * (a: Self, b: Self) -> Self {
    var r = Self()
    r.m = Array(repeating: 0, count: 16)
    for i in 0..<4 {
      for j in 0..<4 { for k in 0..<4 { r.m[i * 4 + j] += a.m[i * 4 + k] * b.m[k * 4 + j] } }
    }
    return r
  }
  static func translate(_ x: Double = 0, _ y: Double = 0, _ z: Double = 0) -> Self {
    var r = Self()
    r.m[3] = x
    r.m[7] = y
    r.m[11] = z
    return r
  }
  static func scale(_ s: Double) -> Self {
    var r = Self()
    r.m[0] = s
    r.m[5] = s
    return r
  }
  static func rotate(_ axis: Int, _ degrees: Double) -> Self {
    let a = degrees * .pi / 180
    let c = cos(a)
    let s = sin(a)
    var r = Self()
    if axis == 0 {
      r.m[5] = c
      r.m[6] = -s
      r.m[9] = s
      r.m[10] = c
    }
    if axis == 1 {
      r.m[0] = c
      r.m[2] = s
      r.m[8] = -s
      r.m[10] = c
    }
    if axis == 2 {
      r.m[0] = c
      r.m[1] = -s
      r.m[4] = s
      r.m[5] = c
    }
    return r
  }
  static func base(_ cx: Double, _ cy: Double) -> Self {
    var p = Self()
    p.m[14] = -1 / 760.0
    return translate(cx, cy) * p * rotate(1, -20) * rotate(0, 12) * scale(1.14)
  }
  var projection: ProjectionTransform {
    var p = ProjectionTransform()
    p.m11 = m[0]
    p.m12 = m[4]
    p.m13 = m[12]
    p.m21 = m[1]
    p.m22 = m[5]
    p.m23 = m[13]
    p.m31 = m[3]
    p.m32 = m[7]
    p.m33 = m[15]
    return p
  }
}

final class GlassPlaylistFolderArt {
  static let shared = GlassPlaylistFolderArt()
  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: a)
  }
  static func ui(_ hex: UInt32, _ a: Double = 1) -> UIColor {
    UIColor(
      red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: a)
  }
  private static let p3 = CGColorSpace(name: CGColorSpace.displayP3)!
  private static let srgb = CGColorSpace(name: CGColorSpace.sRGB)!
  private static let rasterScale = 4.0
  // Skia's iOS surface interpolates and composites in encoded Display P3.
  // Keep those components in an sRGB-tagged intermediate until the final pass.
  private static func components(_ hex: UInt32, _ a: Double) -> [CGFloat] {
    ui(hex, a).cgColor.converted(to: p3, intent: .relativeColorimetric, options: nil)!.components!
  }
  static func paint(_ hex: UInt32, _ a: Double = 1) -> Color {
    let c = components(hex, a)
    return Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: a)
  }
  private static func stops(_ values: [(UInt32, Double)], _ locations: [Double]? = nil) -> Gradient {
    let rgba = values.map { components($0.0, $0.1) }
    let positions = locations ?? values.indices.map { Double($0) / Double(values.count - 1) }
    var result: [Gradient.Stop] = []
    // Dense stops preserve Skia's unpremultiplied interpolation in SwiftUI.
    for i in 0..<(values.count - 1) {
      for j in 0..<64 {
        let k = Double(j) / 64
        let c = (0..<4).map { Double(rgba[i][$0]) + (Double(rgba[i + 1][$0]) - Double(rgba[i][$0])) * k }
        result.append(.init(color: Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: c[3]),
                            location: positions[i] + (positions[i + 1] - positions[i]) * k))
      }
    }
    result.append(.init(color: paint(values.last!.0, values.last!.1), location: positions.last!))
    return Gradient(stops: result)
  }
  static let glowGradient = stops([(0x28aa46, 0.62), (0x145a28, 0.25), (0x05140a, 0)], [0, 0.45, 1])
  static let tintGradient = stops([(0x6eeb87, 0.42), (0x28aa50, 0.5), (0x146e32, 0.62)])
  static let bandGradient = stops([(0xffffff, 0), (0xe6ffeb, 0.28), (0xffffff, 0)])
  static let rimGradient = stops([(0xdcffe1, 0.75), (0x78e696, 0.25), (0xa0ffb4, 0.5)])
  let back: Image
  let covers: [Image]
  let photos: [Image]
  let sheen: Image
  let cursor: Image
  let mark: Image
  let flap = Path(roundedRect: CGRect(x: 66, y: 162, width: 268, height: 140), cornerRadius: 22)
  private static let ci = CIContext(options: [
    .workingColorSpace: p3, .outputColorSpace: p3
  ])
  private var boundsSize = CGSize.zero
  private var boundsPath = Path()
  func bounds(_ size: CGSize) -> Path {
    if boundsSize != size {
      boundsSize = size
      boundsPath = Path(CGRect(origin: .zero, size: size))
    }
    return boundsPath
  }
  private static func bitmap(_ draw: (CGContext) -> Void) -> UIImage {
    let scale = rasterScale
    let c = CGContext(data: nil, width: Int(400 * scale), height: Int(400 * scale),
                      bitsPerComponent: 8, bytesPerRow: 0, space: p3,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.translateBy(x: 0, y: 400 * scale)
    c.scaleBy(x: scale, y: -scale)
    c.translateBy(x: 200, y: 200)
    c.interpolationQuality = .low
    UIGraphicsPushContext(c)
    draw(c)
    UIGraphicsPopContext()
    return UIImage(cgImage: c.makeImage()!, scale: scale, orientation: .up)
  }
  private static func render(_ draw: (CGContext) -> Void) -> Image {
    let image = bitmap(draw)
    return Image(uiImage: UIImage(cgImage: image.cgImage!.copy(colorSpace: srgb)!,
                                 scale: rasterScale, orientation: .up))
  }
  private static func blurred(_ path: CGPath, _ color: UIColor, _ sigma: Double) -> UIImage {
    let input = CIImage(
      image: bitmap { c in
        c.addPath(path)
        c.setFillColor(color.cgColor)
        c.fillPath()
      })!
    let result = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * rasterScale])
    guard let cg = ci.createCGImage(result, from: input.extent) else { return UIImage() }
    return UIImage(cgImage: cg, scale: rasterScale, orientation: .up)
  }
  private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath
  {
    CGPath(
      roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r,
      transform: nil)
  }
  private static func gradient(_ c: CGContext, _ colors: [UIColor], _ a: CGPoint, _ b: CGPoint) {
    let g = CGGradient(
      colorsSpace: p3, colors: colors.map { $0.cgColor.converted(to: p3, intent: .relativeColorimetric, options: nil)! } as CFArray,
      locations: nil)!
    c.drawLinearGradient(
      g, start: a, end: b, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
  }
  init() {
    back = Self.render { c in
      let p = CGMutablePath()
      p.addPath(Self.rr(-128, -84, 256, 184, 20))
      p.addPath(Self.rr(-128, -102, 100, 40, 14))
      c.saveGState()
      c.addPath(p)
      c.clip()
      c.setAlpha(0.92)
      Self.gradient(
        c, [Self.ui(0x2f9a4a), Self.ui(0x16602b), Self.ui(0x0b3a19)], CGPoint(x: 0, y: -102),
        CGPoint(x: 0, y: 100))
      c.restoreGState()
      c.addPath(p)
      c.setStrokeColor(Self.ui(0xaaffbe, 0.35).cgColor)
      c.setLineWidth(1.4)
      c.strokePath()
    }
    let shadow = Self.blurred(Self.rr(-54, -64, 112, 140, 12), Self.ui(0x000000, 0.45), 8)
    let coverFrame = Self.render { c in
      shadow.draw(in: CGRect(x: -200, y: -200, width: 400, height: 400))
      c.addPath(Self.rr(-56, -70, 112, 140, 12))
      c.setFillColor(Self.ui(0x0d0f10).cgColor)
      c.fillPath()
    }
    covers = Array(repeating: coverFrame, count: 4)
    photos = (1...4).map { i in
      guard let url = Bundle.main.url(forResource: "GlassPlaylistFolder-cover-\(i)", withExtension: "webp"),
            let source = CGImageSourceCreateWithURL(url as CFURL, nil),
            let cg = CGImageSourceCreateImageAtIndex(source, 0,
                [kCGImageSourceShouldCacheImmediately: true] as CFDictionary),
            let c = CGContext(data: nil, width: cg.width, height: cg.height,
                bitsPerComponent: 8, bytesPerRow: 0, space: Self.p3,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
      else { return Image(uiImage: UIImage()) }
      c.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
      return Image(uiImage: UIImage(cgImage: c.makeImage()!.copy(colorSpace: Self.srgb)!))
    }
    sheen = Self.render { c in
      c.addPath(Self.rr(-52, -66, 104, 70, 9))
      c.clip()
      Self.gradient(c, [Self.ui(0xffffff, 0.22), Self.ui(0xffffff, 0)],
                    CGPoint(x: 0, y: -70), CGPoint(x: 0, y: 0))
    }
    mark = Self.render { c in
      let mx = -100.0
      let my = 66.0
      c.setFillColor(Self.ui(0x0f3b1d).cgColor)
      c.fillEllipse(in: CGRect(x: mx - 15, y: my - 15, width: 30, height: 30))
      c.setStrokeColor(Self.ui(0x7df59b).cgColor)
      c.setLineWidth(2.4)
      c.setLineCap(.round)
      for k in 0..<3 {
        let r = Double(4 + k * 4)
        c.addArc(
          center: CGPoint(x: mx - 4, y: my), radius: r, startAngle: -50 * .pi / 180,
          endAngle: 50 * .pi / 180, clockwise: false)
        c.strokePath()
      }
    }
    cursor = Self.render { c in
      let p = CGMutablePath()
      p.move(to: .zero)
      p.addLine(to: CGPoint(x: 56, y: 26))
      p.addQuadCurve(to: CGPoint(x: 56, y: 31), control: CGPoint(x: 60, y: 28))
      p.addLine(to: CGPoint(x: 31, y: 38))
      p.addLine(to: CGPoint(x: 22, y: 62))
      p.addQuadCurve(to: CGPoint(x: 17, y: 61), control: CGPoint(x: 19, y: 66))
      p.closeSubpath()
      Self.blurred(p, Self.ui(0x3dff7a, 0.4), 14).draw(
        in: CGRect(x: -200, y: -200, width: 400, height: 400))
      c.saveGState()
      c.addPath(p)
      c.clip()
      Self.gradient(
        c, [Self.ui(0xb6ffc8), Self.ui(0x43d96c), Self.ui(0x1b8d3c)], .zero, CGPoint(x: 50, y: 60))
      c.restoreGState()
      c.addPath(p)
      c.setLineWidth(1.4)
      c.setStrokeColor(Self.ui(0xe6ffec, 0.7).cgColor)
      c.strokePath()
    }
  }
}

struct GlassPlaylistFolderScene: View {
  typealias M = GlassPlaylistFolderMotion
  typealias X = GlassPlaylistFolderMatrix
  let frame: M.Frame
  let size: CGSize
  let top: Double
  private let art = GlassPlaylistFolderArt.shared
  private func image(_ image: Image, _ matrix: X) -> some View {
    image.resizable().interpolation(.low)
      .frame(width: 400, height: 400)
      .projectionEffect((matrix * .translate(-200, -200)).projection)
  }
  private func cover(_ i: Int, _ matrix: X) -> some View {
    ZStack(alignment: .topLeading) {
      art.covers[i].resizable().interpolation(.low).frame(width: 400, height: 400)
      // Keep the original 480 px texture instead of resampling it into the atlas.
      art.photos[i].resizable().interpolation(.low).frame(width: 132, height: 132)
        .frame(width: 104, height: 132)
        .clipShape(RoundedRectangle(cornerRadius: 9, style: .circular))
        .offset(x: 148, y: 134)
      art.sheen.resizable().interpolation(.low).frame(width: 400, height: 400)
    }.frame(width: 400, height: 400, alignment: .topLeading)
      .projectionEffect((matrix * .translate(-200, -200)).projection)
  }
  private var base: X { .base(size.width / 2, top + 318) }
  private var flap: ProjectionTransform {
    (base * .translate(0, 0, 8) * .translate(0, 102) * .rotate(0, -frame.s.open * 50)
      * .translate(0, -102) * .translate(-200, -200)).projection
  }
  private var backgroundScene: some View {
    ZStack(alignment: .topLeading) {
      Canvas { c, sz in
        c.fill(art.bounds(sz), with: .color(GlassPlaylistFolderArt.paint(0x050806)))
        var g = c
        g.opacity = 0.75 + 0.25 * M.clamp(frame.s.open)
        g.fill(
          art.bounds(sz),
          with: .radialGradient(
            GlassPlaylistFolderArt.glowGradient,
            center: CGPoint(x: size.width / 2, y: top + 198), startRadius: 0, endRadius: 320))
      }.frame(width: size.width, height: size.height)
      image(art.back, base * .translate(0, 0, -16))
      ForEach(0..<4, id: \.self) { i in
        let fan = [
          (-54.0, -70.0, -12.0, -18.0), (-18, -24, -4, -6), (18, 24, 4, 7), (52, 70, 11, 19),
        ][i]
        let k = frame.s.covers[i]
        let lift = frame.s.hovered == i ? frame.s.lift : 0
        let bob = sin(2 * .pi * 2 * frame.t / 8 + Double(i) * 1.3) * 2.5 * M.clamp(k)
        cover(
          i,
          base * .translate(0, 0, -9 + Double(i) * 2 + lift * 40)
            * .translate(M.mix(fan.0, fan.1, k), M.mix(-32, -116, k) + bob - lift * 16)
            * .rotate(2, M.mix(fan.2, fan.3, k)) * .scale(1 + lift * 0.06))
      }
    }.frame(width: size.width, height: size.height, alignment: .topLeading)
  }
  var body: some View {
    ZStack(alignment: .topLeading) {
      backgroundScene
      // Unproject the backdrop to the flap's plane, blur in that plane, clip,
      // then project it back. This preserves the 14 pt blur under perspective.
      backgroundScene.projectionEffect(flap.inverted())
        .drawingGroup(colorMode: .nonLinear)
        .layerEffect(ShaderLibrary.glassPlaylistFolderFrost(.float2(1, 0)),
                     maxSampleOffset: CGSize(width: 42, height: 0))
        .layerEffect(ShaderLibrary.glassPlaylistFolderFrost(.float2(0, 1)),
                     maxSampleOffset: CGSize(width: 0, height: 42))
        .mask { Canvas { c, _ in c.fill(art.flap, with: .color(.white)) } }
        .projectionEffect(flap)
      Canvas { c, _ in
        let k = M.clamp(frame.s.open)
        c.fill(
          art.flap,
          with: .linearGradient(
            GlassPlaylistFolderArt.tintGradient, startPoint: CGPoint(x: 66, y: 162), endPoint: CGPoint(x: 226.8, y: 302)))
        let y = M.mix(-78, 102, k) + 200
        c.fill(
          art.flap,
          with: .linearGradient(
            GlassPlaylistFolderArt.bandGradient, startPoint: CGPoint(x: 66, y: y), endPoint: CGPoint(x: 136, y: y + 120)))
        c.stroke(
          art.flap,
          with: .linearGradient(
            GlassPlaylistFolderArt.rimGradient, startPoint: CGPoint(x: 0, y: 162), endPoint: CGPoint(x: 0, y: 302)), lineWidth: 1.6)
        c.draw(art.mark, in: CGRect(x: 0, y: 0, width: 400, height: 400))
      }.frame(width: 400, height: 400).projectionEffect(flap)
      Canvas { c, _ in
        let t = frame.t
        let cx = size.width / 2
        let cy = top + 318
        let a = M.ease(M.seg(t, 1.1, 2))
        let hover = M.ease(M.seg(t, 2.9, 3.4))
        let leave = M.ease(M.seg(t, 5, 5.7))
        let home = M.ease(M.seg(t, 5.7, 6.8))
        var x = M.mix(cx + 112, cx + 40, a)
        var y = M.mix(cy + 196, cy + 50, a)
        x = M.mix(x, cx - 4, hover)
        y = M.mix(y, cy - 140, hover)
        x = M.mix(x, cx + 150, leave)
        y = M.mix(y, cy + 90, leave)
        x = M.mix(x, cx + 112, home)
        y = M.mix(y, cy + 196, home)
        let press = t >= 1.92 && t < 2.22 ? sin(M.seg(t, 1.92, 2.22) * .pi) : 0
        if !frame.dragging {
          var cursor = c
          cursor.translateBy(x: x, y: y)
          cursor.scaleBy(x: 1 - press * 0.16, y: 1 - press * 0.16)
          cursor.rotate(by: .degrees(-8))
          cursor.draw(art.cursor, in: CGRect(x: -200, y: -200, width: 400, height: 400))
        }
        let rp = M.seg(t, 2, 2.45)
        if rp > 0 && rp < 1 {
          let r = 8 + rp * 34
          c.stroke(
            Path(ellipseIn: CGRect(x: cx + 40 - r, y: cy + 50 - r, width: r * 2, height: r * 2)),
            with: .color(GlassPlaylistFolderArt.paint(0x8dffb0, 0.7 * (1 - rp))), lineWidth: 1.6)
        }
      }.frame(width: size.width, height: size.height)
    }.frame(width: size.width, height: size.height, alignment: .topLeading)
      .drawingGroup(opaque: true, colorMode: .nonLinear)
      .colorEffect(ShaderLibrary.glassPlaylistFolderDisplay())
      .allowsHitTesting(false).accessibilityHidden(true)
  }
}
