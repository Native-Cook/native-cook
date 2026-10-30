import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

/// The reference uses sRGB Canvas paint and precomputed silhouettes. UIKit provides the same
/// top-left drawing coordinates, while CoreText supplies unrounded glyph advances and outlines.
enum QrJoyPostcardArt {
  typealias M = QrJoyPostcardMotion
  typealias F = M.Frame
  typealias G = QrJoyPostcardTheme
  typealias C = G.C
  struct Live {
    var amb = 0.0, tiltX = 0.0, tiltY = 0.0, lean = 0.0, cta = 0.0
    var hop = 0.0, squash = 0.0, wave = 0.0, jiggle = 0.0
  }
  static let tau = Double.pi * 2
  static let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
  static func rgb(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(
      red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: alpha)
  }
  static func rgb(_ hex: String, _ alpha: Double = 1) -> UIColor {
    rgb(Int(hex.dropFirst(), radix: 16) ?? 0, alpha)
  }
  static func blend(_ a: String, _ b: String, _ p: Double, _ alpha: Double = 1) -> UIColor {
    blend(Int(a.dropFirst(), radix: 16) ?? 0, Int(b.dropFirst(), radix: 16) ?? 0, p, alpha)
  }
  static func blend(_ a: Int, _ b: Int, _ p: Double, _ alpha: Double = 1) -> UIColor {
    func ch(_ h: Int, _ s: Int) -> Double { Double((h >> s) & 255) / 255 }
    return UIColor(
      red: M.mix(ch(a, 16), ch(b, 16), p), green: M.mix(ch(a, 8), ch(b, 8), p),
      blue: M.mix(ch(a, 0), ch(b, 0), p), alpha: alpha)
  }
  static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
    CGRect(x: x, y: y, width: w, height: h)
  }
  static func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
    CGPath(roundedRect: rect(x, y, w, h), cornerWidth: r, cornerHeight: r, transform: nil)
  }
  static func fill(_ c: CGContext, _ p: CGPath, _ color: UIColor, evenOdd: Bool = false) {
    c.setFillColor(color.cgColor)
    c.addPath(p)
    c.drawPath(using: evenOdd ? .eoFill : .fill)
  }
  static func box(
    _ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double,
    _ color: UIColor
  ) {
    fill(c, rounded(x, y, w, h, r), color)
  }
  static func circle(_ c: CGContext, _ x: Double, _ y: Double, _ r: Double, _ color: UIColor) {
    c.setFillColor(color.cgColor)
    c.fillEllipse(in: rect(x - r, y - r, 2 * r, 2 * r))
  }
  static func stroke(_ c: CGContext, _ p: CGPath, _ color: UIColor, _ width: Double) {
    c.addPath(p)
    c.setStrokeColor(color.cgColor)
    c.setLineWidth(width)
    c.setLineCap(.round)
    c.setLineJoin(.round)
    c.strokePath()
  }
  static func lines(_ points: [(Double, Double)]) -> CGPath {
    let p = CGMutablePath()
    for (i, v) in points.enumerated() {
      if i == 0 {
        p.move(to: CGPoint(x: v.0, y: v.1))
      } else {
        p.addLine(to: CGPoint(x: v.0, y: v.1))
      }
    }
    return p
  }
  static func linear(
    _ c: CGContext, _ colors: [UIColor], _ locations: [CGFloat]? = nil,
    _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double
  ) {
    let gradient = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray,
      locations: locations)!
    c.drawLinearGradient(
      gradient, start: CGPoint(x: x0, y: y0), end: CGPoint(x: x1, y: y1),
      options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
  }
  static func radial(
    _ c: CGContext, _ colors: [UIColor], _ locations: [CGFloat], _ x: Double, _ y: Double,
    _ r: Double
  ) {
    let gradient = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray,
      locations: locations)!
    c.drawRadialGradient(
      gradient, startCenter: CGPoint(x: x, y: y), startRadius: 0,
      endCenter: CGPoint(x: x, y: y), endRadius: r, options: [.drawsAfterEndLocation])
  }
  static func bake(_ w: Double, _ h: Double, scale: Double = 3, _ body: (CGContext) -> Void)
    -> UIImage
  {
    let format = UIGraphicsImageRendererFormat()
    format.scale = scale
    format.opaque = false
    return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image {
      body($0.cgContext)
    }
  }
  static func blur(_ image: UIImage, _ radius: Double) -> UIImage {
    guard let cg = image.cgImage else { return image }
    let input = CIImage(cgImage: cg)
    let result = input.applyingFilter(
      "CIGaussianBlur", parameters: [kCIInputRadiusKey: radius * image.scale])
    guard let out = ci.createCGImage(result, from: input.extent) else { return image }
    return UIImage(cgImage: out, scale: image.scale, orientation: .up)
  }
  private static var softMasks: [String: UIImage] = [:]
  /// Gaussian mask paint, rather than a shadow plus an unwanted solid source shape.
  static func softFill(
    _ context: CGContext, _ path: CGPath, _ color: UIColor,
    _ sigma: Double, cacheKey: String? = nil
  ) {
    let bounds = path.boundingBoxOfPath.insetBy(dx: -sigma * 4, dy: -sigma * 4)
    let image: UIImage
    if let key = cacheKey, let cached = softMasks[key] {
      image = cached
    } else {
      let mask = bake(bounds.width, bounds.height) { target in
        target.translateBy(x: -bounds.minX, y: -bounds.minY)
        fill(target, path, .white)
      }
      image = blur(mask, sigma)
      if let key = cacheKey { softMasks[key] = image }
    }
    image.withTintColor(color, renderingMode: .alwaysOriginal).draw(in: bounds)
  }
  struct Label {
    let path: CGPath
    let width: Double
    let size: Double
  }
  final class Resources {
    static let shared = Resources()
    var labels: [String: Label] = [:]
    var images: [String: UIImage] = [:]
    var page = UIImage(), hero = UIImage(), gift = UIImage(), sticker = UIImage(),
      stickerShadow = UIImage()
    var bearFrames: [UIImage] = []
    var squares = CGMutablePath(), dots = CGMutablePath()
    init() {
      for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "CoveredByYourGrace"] {
        if let url = Bundle.main.url(forResource: "QrJoyPostcard-\(name)", withExtension: "ttf") {
          CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
      }
      for name in ["bear-atlas", "cushion", "gift", "tulips"] {
        if let url = Bundle.main.url(forResource: "QrJoyPostcard-\(name)", withExtension: "webp"),
          let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let cg = CGImageSourceCreateImageAtIndex(
            source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        {
          images[name] = UIImage(cgImage: cg)
        }
      }
      if let atlas = images["bear-atlas"]?.cgImage {
        for i in 0..<9 {
          if let frame = atlas.cropping(
            to: CGRect(x: (i % 3) * 360, y: (i / 3) * 345, width: 360, height: 345))
          {
            bearFrames.append(UIImage(cgImage: frame))
          }
        }
      }
      for md in G.MODULES {
        squares.addRect(
          rect(
            G.QR.x + md.c * G.QR.m - 0.02, G.QR.y + md.r * G.QR.m - 0.02, G.QR.m + 0.04,
            G.QR.m + 0.04))
        if !md.heart {
          dots.addEllipse(
            in: rect(
              G.QR.x + (md.c + 0.5) * G.QR.m - G.QR.m * 0.42,
              G.QR.y + (md.r + 0.5) * G.QR.m - G.QR.m * 0.42, G.QR.m * 0.84, G.QR.m * 0.84))
        }
      }
      page = bake(402, 874) { drawPage($0, self) }
      hero = bake(402, 874) { drawHero($0) }
      gift = blur(
        bake(G.GIFT.w + 40, G.GIFT.h + 40) { _ in
          self.image("gift").draw(in: rect(20, 20, G.GIFT.w, G.GIFT.h))
        }, G.GIFT.blur)
      let b = G.STICKER.border
      let sw = G.STICKER.w + b * 4
      let sh = G.STICKER.h + b * 4
      let base = bake(sw, sh) { _ in
        self.image("tulips").draw(in: rect(b * 2, b * 2, G.STICKER.w, G.STICKER.h))
      }
      if let cg = base.cgImage {
        let input = CIImage(cgImage: cg)
        let dilation = input.applyingFilter(
          "CIMorphologyRectangleMaximum", parameters: ["inputWidth": b * 6, "inputHeight": b * 6])
        func silhouette(_ color: UIColor) -> UIImage {
          var red: CGFloat = 0
          var green: CGFloat = 0
          var blue: CGFloat = 0
          var alpha: CGFloat = 0
          color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
          let tint = dilation.applyingFilter(
            "CIColorMatrix",
            parameters: [
              "inputRVector": CIVector(x: 0, y: 0, z: 0, w: red),
              "inputGVector": CIVector(x: 0, y: 0, z: 0, w: green),
              "inputBVector": CIVector(x: 0, y: 0, z: 0, w: blue),
              "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 1),
            ])
          guard let out = ci.createCGImage(tint, from: input.extent) else { return UIImage() }
          return UIImage(cgImage: out, scale: 3, orientation: .up)
        }
        let border = silhouette(.white)
        sticker = bake(sw, sh) { _ in
          border.draw(at: .zero)
          base.draw(at: .zero)
        }
        stickerShadow = blur(silhouette(rgb(0x5B0A55)), 3)
      }
    }
    func image(_ name: String) -> UIImage { images[name] ?? UIImage() }
    func label(_ text: String, _ size: Double, _ weight: String = "Regular") -> Label {
      let key = "\(text)|\(size)|\(weight)"
      if let found = labels[key] { return found }
      let names = [
        "Hand": "CoveredByYourGrace", "Regular": "Inter24pt-Regular", "Bold": "Inter24pt-Bold",
        "SemiBold": "Inter-SemiBold",
      ]
      let name = names[weight] ?? "Inter24pt-Regular"
      let font = CTFontCreateWithName(name as CFString, size, nil)
      let chars = Array(text.utf16)
      var glyphs = [CGGlyph](repeating: 0, count: chars.count)
      CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
      var advances = [CGSize](repeating: .zero, count: glyphs.count)
      CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
      let p = CGMutablePath()
      var x = 0.0
      for i in glyphs.indices {
        if let gp = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
          p.addPath(gp, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
        }
        x += advances[i].width
      }
      let l = Label(path: p, width: x, size: size)
      labels[key] = l
      return l
    }
  }
  static func label(
    _ c: CGContext, _ r: Resources, _ text: String, _ size: Double, _ x: Double, _ cy: Double,
    _ ax: Double = 0, _ color: UIColor = rgb(0x252527), _ weight: String = "Regular"
  ) {
    let l = r.label(text, size, weight)
    c.saveGState()
    c.translateBy(x: x - l.width * ax, y: cy + size * 0.36)
    fill(c, l.path, color)
    c.restoreGState()
  }
  static let heart: CGPath = {
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 0, y: 7.4))
    p.addCurve(
      to: CGPoint(x: -10, y: -3.6), control1: CGPoint(x: -1.1, y: 6.4),
      control2: CGPoint(x: -10, y: 0.6))
    p.addCurve(
      to: CGPoint(x: -4.9, y: -8.8), control1: CGPoint(x: -10, y: -7.2),
      control2: CGPoint(x: -6.9, y: -8.8))
    p.addCurve(
      to: CGPoint(x: 0, y: -5.6), control1: CGPoint(x: -2.8, y: -8.8),
      control2: CGPoint(x: -1.2, y: -7.6))
    p.addCurve(
      to: CGPoint(x: 4.9, y: -8.8), control1: CGPoint(x: 1.2, y: -7.6),
      control2: CGPoint(x: 2.8, y: -8.8))
    p.addCurve(
      to: CGPoint(x: 10, y: -3.6), control1: CGPoint(x: 6.9, y: -8.8),
      control2: CGPoint(x: 10, y: -7.2))
    p.addCurve(
      to: CGPoint(x: 0, y: 7.4), control1: CGPoint(x: 10, y: 0.6), control2: CGPoint(x: 1.1, y: 6.4)
    )
    p.closeSubpath()
    return p
  }()
  static let star: CGPath = {
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 0, y: -10))
    p.addQuadCurve(to: CGPoint(x: 10, y: 0), control: CGPoint(x: 1.5, y: -1.5))
    p.addQuadCurve(to: CGPoint(x: 0, y: 10), control: CGPoint(x: 1.5, y: 1.5))
    p.addQuadCurve(to: CGPoint(x: -10, y: 0), control: CGPoint(x: -1.5, y: 1.5))
    p.addQuadCurve(to: CGPoint(x: 0, y: -10), control: CGPoint(x: -1.5, y: -1.5))
    p.closeSubpath()
    return p
  }()
  static func drawPage(_ c: CGContext, _ r: Resources) {
    typealias p = G.PAGE
    c.setFillColor(rgb(C.page).cgColor)
    c.fill(rect(0, 0, 402, 874))
    box(c, 0, -40, 402, p.header.y1 + 40, p.header.r, .white)
    stroke(
      c,
      lines([
        (p.back.x + 4.5, p.back.y - 9), (p.back.x - 4.5, p.back.y), (p.back.x + 4.5, p.back.y + 9),
      ]), rgb(0x8F2BE0), 2.4)
    label(c, r, G.COPY.orders, G.SIZE.orders, 201, p.ordersY, 0.5, rgb(C.ink), "SemiBold")
    box(c, p.row.x, p.row.y, p.row.w, p.row.h, p.row.r, .white)
    let bag = CGMutablePath()
    bag.addPath(rounded(p.bag.x - 9.5, p.bag.y - 3.5, 19, 15, 3.2))
    bag.move(to: CGPoint(x: p.bag.x - 4.6, y: p.bag.y - 3.5))
    bag.addCurve(
      to: CGPoint(x: p.bag.x + 4.6, y: p.bag.y - 3.5),
      control1: CGPoint(x: p.bag.x - 4.6, y: p.bag.y - 12),
      control2: CGPoint(x: p.bag.x + 4.6, y: p.bag.y - 12))
    stroke(c, bag, rgb(C.ink), 2)
    label(c, r, G.COPY.purchases, G.SIZE.row, p.purchasesX, p.bag.y + 1, 0, rgb(C.ink), "SemiBold")
    stroke(
      c,
      lines([
        (p.chevron.x - 3, p.chevron.y - 6.5), (p.chevron.x + 3, p.chevron.y),
        (p.chevron.x - 3, p.chevron.y + 6.5),
      ]), rgb(C.ink), 2.2)
    box(c, p.promo.x, p.promo.y, p.promo.w, p.promo.h, p.promo.r, rgb(C.promo))
    r.image("tulips").draw(in: rect(p.tulips.x, p.tulips.y, p.tulips.w, p.tulips.h))
    label(
      c, r, G.COPY.promo, G.SIZE.promo, p.promoText.x, p.promoText.y, 0, rgb(C.violet), "SemiBold")
    stroke(
      c,
      lines([
        (p.promoChevron.x - 2.4, p.promoChevron.y - 5), (p.promoChevron.x + 2.4, p.promoChevron.y),
        (p.promoChevron.x - 2.4, p.promoChevron.y + 5),
      ]), rgb(C.violet), 2)
    box(c, p.order.x, p.order.y, p.order.w, 874, p.order.r, .white)
    label(c, r, G.COPY.pick1, G.SIZE.pick, 16.4, p.pick1Y, 0, rgb(C.ink), "Bold")
    label(c, r, G.COPY.pick2, G.SIZE.pick, 16.4, p.pick2Y, 0, rgb(C.ink), "Bold")
    typealias q = p.qrButton
    box(c, q.x, q.y, q.w, q.h, q.r, rgb(C.chip))
    let gx = q.x + q.w / 2
    let gy = q.y + q.h / 2
    for (dx, dy) in [(-1.0, -1.0), (1.0, -1.0), (-1.0, 1.0)] {
      stroke(
        c, rounded(gx + dx * 5.6 - 4.2, gy + dy * 5.6 - 4.2, 8.4, 8.4, 2.6), rgb(C.violet), 2.3)
    }
    for (dx, dy) in [(2.6, 2.6), (8.4, 2.6), (2.6, 8.4), (8.4, 8.4)] {
      box(c, gx + dx - 1.6, gy + dy - 1.6, 3.2, 3.2, 0.9, rgb(C.violet))
    }
    label(c, r, G.COPY.point, G.SIZE.point, 16.4, p.pointY, 0, rgb(C.grey))
  }
  static func drawHero(_ c: CGContext) {
    let top = G.SHEET.top
    let bottom = G.SHEET.heroBottom
    c.saveGState()
    c.addPath(rounded(0, top, 402, bottom - top + 40, G.SHEET.r))
    c.clip()
    linear(c, [rgb(C.heroTop), rgb(C.heroMid)], nil, 0, top, 402 * 0.3, bottom)
    for (x, y, r, a) in [(12.0, 455.0, 95.0, 0.9), (392, 528, 120, 0.9), (210, 525, 90, 0.45)] {
      c.saveGState()
      c.clip(to: rect(x - r, y - r, r * 2, r * 2))
      radial(c, [rgb(0xFFE4FC, a), rgb(0xFFE4FC, 0)], [0, 1], x, y, r)
      c.restoreGState()
    }
    // A translucent shaft, blurred once in this static backdrop.
    let shaft = bake(402, 874) { ctx in
      let p = CGMutablePath()
      p.move(to: CGPoint(x: 40, y: top - 10))
      p.addLine(to: CGPoint(x: 95, y: top - 10))
      p.addLine(to: CGPoint(x: 180, y: bottom))
      p.addLine(to: CGPoint(x: 110, y: bottom))
      p.closeSubpath()
      ctx.addPath(p)
      ctx.clip()
      linear(
        ctx, [rgb(0xFFFFFF, 0), rgb(0xFFFFFF, 0.32), rgb(0xFFFFFF, 0)], [0, 0.5, 1], 20, top, 120,
        top + 60)
    }
    blur(shaft, 10).draw(at: .zero)
    c.restoreGState()
  }
  static func drawStar(
    _ c: CGContext, _ x: Double, _ y: Double, _ half: Double, _ rotation: Double, _ alpha: Double
  ) {
    guard alpha > 0.01 && half > 0.2 else { return }
    c.saveGState()
    c.translateBy(x: x, y: y)
    c.rotate(by: rotation * Double.pi / 180)
    c.scaleBy(x: half / 10, y: half / 10)
    softFill(c, star, rgb(0xFFFFFF, alpha * 0.55), 2.2, cacheKey: "star")
    fill(c, star, rgb(0xFFFFFF, alpha))
    c.restoreGState()
  }
  static func drawBackdrop(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    c.saveGState()
    c.clip(to: rect(0, G.SHEET.top, 402, G.SHEET.heroBottom - G.SHEET.top))
    c.saveGState()
    c.translateBy(
      x: G.GIFT.x - live.tiltX * 6, y: G.GIFT.y + 2.2 * sin(tau * live.amb / 9) - live.tiltY * 3)
    c.rotate(by: (G.GIFT.rot + 1.2 * sin(tau * live.amb / 9 + 1.1)) * Double.pi / 180)
    r.gift.draw(in: rect(-(G.GIFT.w + 40) / 2, -(G.GIFT.h + 40) / 2, G.GIFT.w + 40, G.GIFT.h + 40))
    c.restoreGState()
    let boost = f.burst >= 0 ? sin(Double.pi * f.burst) : 0
    for (i, s) in G.SPARKLES.enumerated() {
      let cycles = i % 2 == 0 ? 2.0 : 3.0
      let w = 0.5 + 0.5 * sin(tau * (cycles * live.amb / 9 + s.ph))
      let k = (0.72 + 0.28 * w) * (1 + 0.25 * boost)
      drawStar(
        c, s.x - live.tiltX * 3, s.y - live.tiltY * 2, s.s * k,
        6 * sin(tau * (live.amb / 9 + s.ph)), 0.75 + 0.25 * w)
    }
    c.restoreGState()
  }
  static func drawCardFace(_ c: CGContext, _ f: F) {
    c.saveGState()
    c.translateBy(x: 0, y: 8 + 8 * f.lift)
    softFill(
      c, rounded(4, 0, G.CARD.w - 8, G.CARD.h, G.CARD.r),
      UIColor(red: 0.55, green: 0.05, blue: 0.5, alpha: 0.16 + 0.08 * min(1, f.lift)),
      9, cacheKey: "card")
    c.restoreGState()
    box(c, 0, 0, G.CARD.w, G.CARD.h, G.CARD.r, .white)
    guard f.face > 0.001 else { return }
    c.saveGState()
    c.addPath(rounded(0, 0, G.CARD.w, G.CARD.h, G.CARD.r))
    c.clip()
    c.beginTransparencyLayer(auxiliaryInfo: nil)
    linear(c, [rgb(C.postTop), rgb(C.postBottom)], nil, 0, 0, G.CARD.w * 0.5, G.CARD.h)
    let radius = 12 + f.face * 190
    let mx = G.QR.x + G.QR.size / 2
    let my = G.QR.y + G.QR.size / 2
    if f.face < 0.999 {
      c.setBlendMode(.destinationIn)
      radial(
        c, [.black, .black, .clear], [0, CGFloat(max(0, (radius - 16) / radius)), 1], mx, my, radius
      )
    }
    c.endTransparencyLayer()
    c.setBlendMode(.normal)
    if f.face < 0.999 {
      c.saveGState()
      let ring = CGPath(
        ellipseIn: rect(mx - radius + 8, my - radius + 8, (radius - 8) * 2, (radius - 8) * 2),
        transform: nil)
      let ringArea = ring.copy(
        strokingWithWidth: 5, lineCap: .round, lineJoin: .round, miterLimit: 10)
      softFill(
        c, ringArea,
        UIColor(
          red: 1, green: 0.43, blue: 0.85,
          alpha: 0.35 * sin(Double.pi * f.face)), 3)
      c.restoreGState()
    }
    c.setLineDash(phase: 0, lengths: [5, 4])
    stroke(
      c, rounded(7, 7, G.CARD.w - 14, G.CARD.h - 14, G.CARD.r - 6),
      rgb(0xFFFFFF, 0.95 * M.smooth((f.face - 0.6) / 0.4)), 1.3)
    c.restoreGState()
  }
  static func drawFinders(_ c: CGContext, _ f: F) {
    let m = G.QR.m
    let k = f.finders
    for (i, fd) in G.FINDERS.enumerated() {
      let x = G.QR.x + fd.c * m
      let y = G.QR.y + fd.r * m
      let cx = x + 3.5 * m
      let cy = y + 3.5 * m
      c.saveGState()
      c.translateBy(x: cx, y: cy)
      c.rotate(by: 10 * sin(Double.pi * k) * (i == 1 ? -1 : 1) * Double.pi / 180)
      c.translateBy(x: -cx, y: -cy)
      let p = CGMutablePath()
      p.addPath(rounded(x, y, 7 * m, 7 * m, 2.2 * m * k))
      p.addPath(rounded(x + m, y + m, 5 * m, 5 * m, 1.5 * m * k))
      fill(c, p, blend(C.qrInk, C.finder, k), evenOdd: true)
      box(c, x + 2 * m, y + 2 * m, 3 * m, 3 * m, 1.5 * m * k, blend(C.qrInk, C.finderCore, k))
      c.restoreGState()
    }
  }
  static func dotGradient(_ c: CGContext) {
    linear(
      c, [rgb(C.dotA), rgb(C.dotB)], nil, G.QR.x, G.QR.y, G.QR.x + G.QR.size, G.QR.y + G.QR.size)
  }
  static func drawModules(_ c: CGContext, _ r: Resources, _ t: Double, _ flying: Bool) {
    let m = G.QR.m
    let calm = t < M.T.swirlAt || (t >= M.SWIRL_DONE && t < M.T.backSwirlAt) || t >= M.BACK_DONE
    if calm {
      if flying { return }
      if t < M.T.swirlAt || t >= M.BACK_DONE {
        fill(c, r.squares, rgb(C.qrInk))
      } else {
        c.saveGState()
        c.addPath(r.dots)
        c.clip()
        dotGradient(c)
        c.restoreGState()
      }
      return
    }
    for md in G.MODULES {
      let u = M.moduleU(md, t)
      let settled = u <= 0.0005 || u >= 0.9995
      if settled == flying { continue }
      if u <= 0.0005 {
        box(
          c, G.QR.x + md.c * m - 0.02, G.QR.y + md.r * m - 0.02, m + 0.04, m + 0.04, 0, rgb(C.qrInk)
        )
        continue
      }
      if u >= 0.9995 {
        if !md.heart {
          c.saveGState()
          c.addEllipse(
            in: rect(
              G.QR.x + (md.c + 0.5) * m - m * 0.42, G.QR.y + (md.r + 0.5) * m - m * 0.42, m * 0.84,
              m * 0.84))
          c.clip()
          dotGradient(c)
          c.restoreGState()
        }
        continue
      }
      let pose = M.modulePose(md, u)
      let rounding = M.smooth((u - 0.12) / 0.5)
      let size = m * M.mix(1, 0.84, M.smooth(u)) * (1 + 0.55 * pose.z)
      let alpha = md.heart ? 1 - M.smooth((u - 0.55) / 0.4) : 1
      if pose.z > 0.3 {
        let back = M.modulePose(md, max(0, min(1, t < M.T.back ? u - 0.07 : u + 0.07)))
        circle(
          c, back.x, back.y, size * 0.42,
          blend(C.qrInk, C.petal, min(1, u * 2), 0.22 * pose.z * alpha))
      }
      let g = (md.r + md.c) / (2 * 29)
      let dot = blend(C.dotA, C.dotB, g)
      var red: CGFloat = 0
      var green: CGFloat = 0
      var blue: CGFloat = 0
      var a: CGFloat = 0
      dot.getRed(&red, green: &green, blue: &blue, alpha: &a)
      let ink: UIColor
      if u < 0.5 {
        ink = blend(C.qrInk, C.petal, u * 2, alpha)
      } else {
        let p = (u - 0.5) * 2
        ink = UIColor(
          red: M.mix(1, red, p), green: M.mix(111.0 / 255, green, p),
          blue: M.mix(216.0 / 255, blue, p), alpha: alpha)
      }
      c.saveGState()
      c.translateBy(x: pose.x, y: pose.y)
      c.rotate(by: pose.spin)
      let pw = size * (1 + 0.75 * pose.z)
      let ph = size * (1 - 0.32 * pose.z)
      box(c, -pw / 2, -ph / 2, pw, ph, min(pw, ph) / 2 * rounding, ink)
      c.restoreGState()
    }
  }
  static func drawBear(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    typealias b = G.BEAR
    let index = max(0, min(8, Int(floor((live.wave != 0 ? b.rest + live.wave : f.wave) + 0.5))))
    let breathe = 0.012 * sin(tau * 3 * live.amb / 9)
    let squash = f.squash + live.squash
    let hop = f.hop + live.hop
    let dst = rect(-b.w / 2, -b.h, b.w, b.h)
    c.saveGState()
    c.translateBy(x: b.x, y: b.y)
    c.scaleBy(x: 1 + max(0, squash) * 0.4, y: 1 - max(0, squash) * 0.3)
    r.image("cushion").draw(in: dst)
    c.restoreGState()
    c.saveGState()
    let shadow = UIColor(red: 0.12, green: 0, blue: 0.3, alpha: 0.28 * (1 - min(1, hop / 18)))
    let cushionShadow = CGPath(
      ellipseIn: rect(
        b.x - 34 + hop * 0.6, b.y - 26,
        68 - hop * 1.2, 12), transform: nil)
    softFill(c, cushionShadow, shadow, 3)
    c.restoreGState()
    c.saveGState()
    c.translateBy(x: b.x, y: b.y - 16 - hop)
    c.rotate(by: -live.lean * 7 * Double.pi / 180)
    c.scaleBy(x: 1 + squash, y: (1 - squash) * (1 + breathe))
    c.translateBy(x: 0, y: 16)
    if r.bearFrames.indices.contains(index) { r.bearFrames[index].draw(in: dst) }
    c.restoreGState()
  }
  static func drawHeart(_ c: CGContext, _ f: F) {
    guard f.heart > 0.01 else { return }
    let m = G.QR.m
    let k = max(0, f.heart)
    let a = min(1, max(0, f.heart) * 2.5)
    c.saveGState()
    c.translateBy(x: G.QR.x + G.QR.size / 2, y: G.QR.y + G.QR.size / 2)
    c.scaleBy(x: 0.55 + 0.45 * k, y: 0.55 + 0.45 * k)
    c.saveGState()
    softFill(
      c, rounded(-3.3 * m, -3.1 * m, 6.6 * m, 6.6 * m, 1.8 * m),
      UIColor(red: 0.6, green: 0.05, blue: 0.45, alpha: 0.18 * a),
      3, cacheKey: "heart")
    c.restoreGState()
    box(c, -3.3 * m, -3.3 * m, 6.6 * m, 6.6 * m, 1.8 * m, rgb(0xFFFFFF, a))
    c.scaleBy(x: 0.95, y: 0.95)
    c.translateBy(x: 0, y: 0.8)
    fill(c, heart, UIColor(red: 0.94, green: 0.13, blue: 0.62, alpha: a))
    c.restoreGState()
  }
  static func drawSheen(_ c: CGContext, _ f: F) {
    guard f.sheen >= 0 else { return }
    let x = M.mix(-90, G.CARD.w + 90, f.sheen)
    c.saveGState()
    c.addPath(rounded(0, 0, G.CARD.w, G.CARD.h, G.CARD.r))
    c.clip()
    c.setBlendMode(.plusLighter)
    linear(
      c, [rgb(0xFFFFFF, 0), rgb(0xFFFFFF, 0.42), rgb(0xFFFFFF, 0)], [0, 0.5, 1], x - 40, 0, x + 40,
      30)
    c.restoreGState()
  }
  static func drawSticker(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    guard f.t >= 3.15 && f.stickerPeel < 0.999 else { return }
    typealias s = G.STICKER
    let e = M.smooth(f.sticker)
    let drop = 1 - e * e
    let peel = f.stickerPeel
    let scale = 1 + 0.38 * drop + 0.12 * peel
    let rot = (s.rot + 22 * drop + 26 * peel) * Double.pi / 180
    let x = s.x + 18 * drop + 70 * peel + live.tiltX * 3
    let y = s.y - 46 * drop - 95 * peel + live.tiltY * 2
    let alpha = min(1, f.sticker * 5) * (1 - peel)
    let sw = s.w + s.border * 4
    let sh = s.h + s.border * 4
    c.saveGState()
    c.setAlpha(alpha * (0.32 - 0.16 * drop))
    c.translateBy(x: x + 2 + 10 * drop, y: y + 3 + 14 * drop)
    c.rotate(by: rot)
    c.scaleBy(x: scale, y: scale)
    r.stickerShadow.draw(in: rect(-sw / 2, -sh / 2, sw, sh))
    c.restoreGState()
    c.saveGState()
    c.setAlpha(alpha)
    c.translateBy(x: x, y: y)
    c.rotate(by: rot)
    c.scaleBy(x: scale * (1 + 0.07 * f.stickerSquash), y: scale * (1 - 0.07 * f.stickerSquash))
    r.sticker.draw(in: rect(-sw / 2, -sh / 2, sw, sh))
    c.restoreGState()
  }
  static func drawBurst(_ c: CGContext, _ f: F) {
    guard f.burst >= 0 else { return }
    for i in 0..<9 {
      let a = tau * Double(i) / 9 + 0.35
      let h = 0.5 + 0.5 * sin(Double(i) * 7.31)
      let d = (52 + 46 * h) * (1 - pow(1 - f.burst, 3))
      let life = sin(Double.pi * min(1, f.burst * 1.15))
      drawStar(
        c, G.QR.x + G.QR.size / 2 + cos(a) * d, G.QR.y + G.QR.size / 2 + sin(a) * d * 0.9,
        (4 + 5 * h) * life, 40 * f.burst + Double(i) * 20, life)
    }
  }
  static func drawBubble(_ c: CGContext, _ r: Resources, _ f: F) {
    guard f.bubble > 0.01 else { return }
    typealias b = G.BUBBLE
    let k = max(0, f.bubble)
    let tx = b.tail.x
    let ty = b.tail.y
    let bx = b.x
    let by = b.y
    let a = min(1, max(0, f.bubble) * 2)
    c.saveGState()
    c.translateBy(x: tx, y: ty)
    c.scaleBy(x: 0.5 + 0.5 * k, y: 0.5 + 0.5 * k)
    c.translateBy(x: -tx, y: -ty)
    let p = CGMutablePath()
    p.addPath(rounded(bx - b.w / 2, by - b.h / 2, b.w, b.h, b.r))
    p.move(to: CGPoint(x: bx - 22, y: by + 10))
    p.addQuadCurve(to: CGPoint(x: tx, y: ty), control: CGPoint(x: bx - 26, y: by + 22))
    p.addQuadCurve(to: CGPoint(x: bx - 8, y: by + 14), control: CGPoint(x: bx - 10, y: by + 20))
    p.closeSubpath()
    c.saveGState()
    c.translateBy(x: 0, y: 3)
    softFill(
      c, p, UIColor(red: 0.62, green: 0, blue: 0.5, alpha: 0.16 * a),
      3, cacheKey: "bubble")
    c.restoreGState()
    fill(c, p, rgb(0xFFFFFF, a))
    let text = r.label(G.COPY.bubble, G.SIZE.bubble, "Hand")
    let total = text.width + 19
    let x0 = bx - (text.width + 19) / 2
    let yb = by + G.SIZE.bubble * 0.3
    if f.write > 0 {
      c.saveGState()
      c.beginTransparencyLayer(auxiliaryInfo: nil)
      c.saveGState()
      c.translateBy(x: x0, y: yb)
      fill(c, text.path, UIColor(red: 0.48, green: 0.12, blue: 0.66, alpha: 1))
      c.restoreGState()
      c.setBlendMode(.destinationIn)
      c.clip(to: rect(x0 - 4, by - b.h / 2, total + 8, b.h))
      let reveal = min(1, f.write / 0.82) * (text.width + 10)
      linear(c, [.black, .clear], nil, x0 + reveal - 8, 0, x0 + reveal, 0)
      c.endTransparencyLayer()
      c.restoreGState()
    }
    let hp = M.smooth((f.write - 0.8) / 0.2)
    if hp > 0 {
      c.saveGState()
      c.translateBy(x: x0 + text.width + 4 + 7.5, y: by - 1)
      let hs = 0.62 * (0.6 + 0.4 * hp) * (1 + 0.15 * sin(Double.pi * hp))
      c.scaleBy(x: hs, y: hs)
      fill(c, heart, UIColor(red: 0.94, green: 0.13, blue: 0.62, alpha: a * hp))
      c.restoreGState()
    }
    c.restoreGState()
  }
  static func drawText(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    typealias s = G.SHEET
    let ink = UIColor(red: 0.145, green: 0.145, blue: 0.153, alpha: 1)
    label(c, r, G.COPY.title, G.SIZE.title, s.titleX, s.titleY, 0, ink, "Bold")
    label(c, r, G.COPY.sub, G.SIZE.body, s.titleX, s.subY, 0, ink)
    for step in 0..<2 {
      for i in 0..<3 {
        label(c, r, G.COPY.steps[step][i], G.SIZE.body, s.stepTextX, s.stepLineY[step][i], 0, ink)
      }
      let cy = s.stepChipY[step]
      let k = step == 0 ? f.check : 0
      let pop = 1 + 0.12 * sin(Double.pi * (step == 0 ? f.check : 0))
      circle(c, s.stepChip.x, cy, s.stepChip.r * pop, blend(C.stepChip, C.cta, k))
      if k < 0.999 {
        c.saveGState()
        c.translateBy(x: s.stepChip.x, y: cy)
        c.scaleBy(x: 1 - 0.4 * k, y: 1 - 0.4 * k)
        label(
          c, r, String(step + 1), G.SIZE.stepNo, 0, 0, 0.5,
          UIColor(red: 0.145, green: 0.145, blue: 0.153, alpha: 1 - k), "SemiBold")
        c.restoreGState()
      }
      if k > 0.001 {
        // Skia's trim follows path length, including the corner between the two strokes.
        let points = [CGPoint(x: -4.6, y: 0.4), CGPoint(x: -1.4, y: 3.6), CGPoint(x: 4.8, y: -3.4)]
        let first = hypot(3.2, 3.2)
        let second = hypot(6.2, 7)
        let length = (first + second) * min(1, k * 1.15)
        let tick = CGMutablePath()
        tick.move(to: points[0])
        if length < first {
          tick.addLine(
            to: CGPoint(
              x: points[0].x + 3.2 * length / first, y: points[0].y + 3.2 * length / first))
        } else {
          tick.addLine(to: points[1])
          let u = min(1, (length - first) / second)
          tick.addLine(to: CGPoint(x: points[1].x + 6.2 * u, y: points[1].y - 7 * u))
        }
        c.saveGState()
        c.translateBy(x: s.stepChip.x, y: cy)
        stroke(c, tick, .white, 2.3)
        c.restoreGState()
      }
    }
    typealias b = s.cta
    let press = max(f.press, live.cta)
    let bs = 1 - 0.04 * max(f.press, live.cta)
    let k = f.cta
    c.saveGState()
    c.translateBy(x: b.x + b.w / 2, y: b.y + b.h / 2)
    c.scaleBy(x: bs, y: bs)
    box(c, -b.w / 2, -b.h / 2, b.w, b.h, b.r, blend(C.cta, C.ctaPress, press))
    func buttonLabel(_ text: String, _ cy: Double, _ alpha: Double) {
      let l = r.label(text, G.SIZE.cta, "SemiBold")
      let radius = 2.2 * sin(Double.pi * k)
      if radius > 0.001 {
        // Keep the two label layers separate: the blur belongs to each fading glyph run.
        let image = bake(l.width + 20, 50) { ctx in
          label(ctx, r, text, G.SIZE.cta, 10, 25, 0, rgb(0xFFFFFF), "SemiBold")
        }
        c.saveGState()
        c.setAlpha(alpha)
        blur(image, radius).draw(in: rect(-l.width / 2 - 10, cy - 25, l.width + 20, 50))
        c.restoreGState()
      } else {
        label(c, r, text, G.SIZE.cta, 0, cy, 0.5, rgb(0xFFFFFF, alpha), "SemiBold")
      }
    }
    if k < 0.999 { buttonLabel(G.COPY.ctaA, -9 * k, 1 - k) }
    if k > 0.001 { buttonLabel(G.COPY.ctaB, 9 * (1 - k), k) }
    c.restoreGState()
  }
  static func cardContents(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    drawCardFace(c, f)
    drawFinders(c, f)
    drawModules(c, r, f.t, false)
    drawHeart(c, f)
    drawSheen(c, f)
    drawBear(c, r, f, live)
    drawModules(c, r, f.t, true)
    drawSticker(c, r, f, live)
    drawBurst(c, f)
  }
  static func drawCard(_ c: CGContext, _ r: Resources, _ f: F, _ live: Live) {
    let angle = M.mix(G.CARD.angle, M.TURN_LIFTED, min(1, max(0, f.lift))) + 0.045 * live.jiggle
    c.saveGState()
    c.translateBy(x: G.CARD.cx, y: G.CARD.cy - M.LIFT * f.lift)
    c.rotate(by: angle)
    let k = 1 + 0.012 * f.landSquash
    if abs(live.tiltX) > 0.001 || abs(live.tiltY) > 0.001 {
      // The 3D transform acts on the card, its airborne modules, and its bear together.
      let bounds = rect(-200, -230, 600, 650)
      let image = bake(bounds.width, bounds.height, scale: 2) { ctx in
        ctx.translateBy(x: -bounds.minX, y: -bounds.minY)
        ctx.scaleBy(x: 1 / k, y: k)
        ctx.translateBy(x: -G.CARD.w / 2, y: -G.CARD.h / 2)
        cardContents(ctx, r, f, live)
      }
      if let cg = image.cgImage {
        let ry = live.tiltX * 0.24
        let rx = -live.tiltY * 0.18
        func project(_ x: Double, _ y: Double) -> CIVector {
          // processTransform3d: perspective * rotateY * rotateX (column vectors).
          let yy = y * cos(rx)
          let zz = y * sin(rx)
          let xx = x * cos(ry) + zz * sin(ry)
          let z = -x * sin(ry) + zz * cos(ry)
          let d = 1 - z / 620
          return CIVector(x: (xx / d - bounds.minX) * 2, y: (bounds.maxY - yy / d) * 2)
        }
        let source = CIImage(cgImage: cg)
        let result = source.applyingFilter(
          "CIPerspectiveTransform",
          parameters: [
            "inputTopLeft": project(bounds.minX, bounds.minY),
            "inputTopRight": project(bounds.maxX, bounds.minY),
            "inputBottomLeft": project(bounds.minX, bounds.maxY),
            "inputBottomRight": project(bounds.maxX, bounds.maxY),
          ])
        let extent = result.extent.integral
        if let out = ci.createCGImage(result, from: extent) {
          UIImage(cgImage: out, scale: 2, orientation: .up).draw(
            in: rect(
              bounds.minX + extent.minX / 2, bounds.maxY - extent.maxY / 2, extent.width / 2,
              extent.height / 2))
        }
      }
    } else {
      c.scaleBy(x: 1 / k, y: k)
      c.translateBy(x: -G.CARD.w / 2, y: -G.CARD.h / 2)
      cardContents(c, r, f, live)
    }
    c.restoreGState()
  }
  static func draw(context: inout GraphicsContext, size: CGSize, frame f: F, live: Live) {
    let r = Resources.shared
    let s = size.width / 402
    let dy = size.height - 874 * s
    context.withCGContext { c in
      // The Canvas bridge already uses the scene's top-left coordinates. Push it for
      // UIImage.draw so the native scene stays at the device's actual render scale.
      UIGraphicsPushContext(c)
      defer { UIGraphicsPopContext() }
      c.setFillColor(UIColor(red: 0.965, green: 0.965, blue: 0.98, alpha: 1).cgColor)
      c.fill(CGRect(origin: .zero, size: size))
      c.saveGState()
      c.scaleBy(x: s, y: s)
      r.page.draw(at: .zero)
      c.restoreGState()
      c.setFillColor(UIColor.black.withAlphaComponent(0.5).cgColor)
      c.fill(CGRect(origin: .zero, size: size))
      c.saveGState()
      c.scaleBy(x: s, y: s)
      box(
        c, G.PAGE.grabber.x, G.PAGE.grabber.y, G.PAGE.grabber.w, G.PAGE.grabber.h,
        G.PAGE.grabber.h / 2, rgb(0xFFFFFF, 0.62))
      c.restoreGState()
      c.saveGState()
      c.translateBy(x: 0, y: dy)
      c.scaleBy(x: s, y: s)
      r.hero.draw(at: .zero)
      drawBackdrop(c, r, f, live)
      drawCard(c, r, f, live)
      box(c, 0, G.SHEET.heroBottom, 402, 874 - G.SHEET.heroBottom + 200, 0, .white)
      drawBubble(c, r, f)
      circle(c, G.SHEET.close.x, G.SHEET.close.y, G.SHEET.close.r, .white)
      let x = G.SHEET.close.x
      let y = G.SHEET.close.y
      let d = 4.4
      stroke(
        c, lines([(x - d, y - d), (x + d, y + d)]),
        UIColor(red: 1, green: 0.68, blue: 0.95, alpha: 1), 1.9)
      stroke(
        c, lines([(x + d, y - d), (x - d, y + d)]),
        UIColor(red: 1, green: 0.68, blue: 0.95, alpha: 1), 1.9)
      drawText(c, r, f, live)
      c.restoreGState()
    }
  }
}
