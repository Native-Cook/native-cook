import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

// Static text and the blurred home are baked once. Only transforms and the sheet redraw per frame.
final class BookFanSigninArt {
  typealias M = BookFanSigninMotion
  enum C {
    static let page = 0xFFFFFF, ink = 0x0E0E10, grey = 0x8A8A8E, greyLight = 0x9A9A9F
    static let field = 0xF5F5F6, chip = 0xF3F3F4, chipInk = 0x6E6E73
    static let pink = 0xE0457B, pinkSoft = 0xFCE7EF, black = 0x121212
  }
  static let shared = BookFanSigninArt()
  let covers: CGImage?
  var over: CGImage?
  var blurred: CGImage?
  var fonts: [String: String] = [:]
  struct Label {
    let font: CTFont
    let glyphs: [CGGlyph]
    let positions: [CGPoint]
    let width: Double
    let outline: CGPath
  }
  private var labels: [String: Label] = [:]
  private let imageContext = CIContext(options: [
    .workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
    .outputColorSpace: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
  ])
  private var titleBitmaps: [CGImage] = []
  private var shadowMasks: [String: CGImage] = [:]
  private var titleBlurs: [String: CGImage] = [:]
  func blurredRoundedRect(
    _ canvas: CGContext, _ rect: CGRect, radius: Double, sigma: Double, color shadowColor: CGColor
  ) {
    // Every mask is cached, by size rounded to a point and blur to a quarter point: the wall's
    // light wave and the flights vary them continuously, and one Core Image blur per cover per
    // frame dropped the block to about 4 fps where Core Image has no GPU (Xcode Cloud's
    // simulators). A mask a fraction of a point off is stretched to the exact rect.
    let sigma = (sigma * 4).rounded() / 4
    let size = CGSize(width: rect.width.rounded(), height: rect.height.rounded())
    let padding = ceil(sigma * 3)
    let key = "\(size.width)|\(size.height)|\(radius)|\(sigma)"
    let mask: CGImage
    if let cached = shadowMasks[key] {
      mask = cached
    } else {
      let format = UIGraphicsImageRendererFormat()
      format.scale = 2
      format.preferredRange = .standard
      let bitmap = UIGraphicsImageRenderer(
        size: CGSize(width: size.width + padding * 2, height: size.height + padding * 2),
        format: format
      ).image { renderer in
        round(
          renderer.cgContext,
          CGRect(x: padding, y: padding, width: size.width, height: size.height), radius, C.page)
      }
      guard let cgImage = bitmap.cgImage else { return }
      let input = CIImage(cgImage: cgImage)
      let filtered = input.applyingFilter(
        "CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * 2])
      guard let result = imageContext.createCGImage(filtered, from: input.extent) else { return }
      mask = result
      if shadowMasks.count >= 512 { shadowMasks.removeAll(keepingCapacity: true) }
      shadowMasks[key] = mask
    }
    let bounds = CGSize(width: rect.width + padding * 2, height: rect.height + padding * 2)
    canvas.saveGState()
    canvas.translateBy(x: rect.minX - padding, y: rect.maxY + padding)
    canvas.scaleBy(x: 1, y: -1)
    canvas.clip(to: CGRect(origin: .zero, size: bounds), mask: mask)
    canvas.setFillColor(shadowColor)
    canvas.fill(CGRect(origin: .zero, size: bounds))
    canvas.restoreGState()
  }
  let logo: Path
  private init() {
    for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
      if let url = Bundle.main.url(
        forResource: "BookFanSignin-Inter-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let list = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
          as? [CTFontDescriptor], let first = list.first
        {
          fonts[weight] = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String
        }
      }
    }
    if let url = Bundle.main.url(forResource: "BookFanSignin-covers", withExtension: "webp"),
      let source = CGImageSourceCreateWithURL(url as CFURL, nil)
    {
      covers = CGImageSourceCreateImageAtIndex(
        source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
    } else {
      covers = nil
    }
    logo = Path { path in
      path.move(to: CGPoint(x: -15, y: 14))
      path.addLine(to: CGPoint(x: -15, y: -6))
      path.addCurve(
        to: CGPoint(x: -1, y: -10), control1: CGPoint(x: -15, y: -15),
        control2: CGPoint(x: -5, y: -17))
      path.addCurve(
        to: CGPoint(x: 3, y: 6), control1: CGPoint(x: 2, y: -4), control2: CGPoint(x: -4, y: 2))
      path.addCurve(
        to: CGPoint(x: 14, y: -4), control1: CGPoint(x: 9, y: 9.5), control2: CGPoint(x: 15, y: 4))
      path.addCurve(
        to: CGPoint(x: 4, y: -7), control1: CGPoint(x: 13, y: -11), control2: CGPoint(x: 6, y: -12))
    }
    let format = UIGraphicsImageRendererFormat()
    format.scale = 2
    format.preferredRange = .standard
    format.opaque = false
    over =
      UIGraphicsImageRenderer(size: CGSize(width: 402, height: 1074), format: format).image { ctx in
        homeOver(ctx.cgContext)
      }.cgImage
    format.opaque = true
    let home = UIGraphicsImageRenderer(size: CGSize(width: 402, height: 874), format: format).image
    { ctx in
      let canvas = ctx.cgContext
      canvas.setFillColor(color(C.page))
      canvas.fill(CGRect(x: 0, y: 0, width: 402, height: 874))
      wall(canvas, M.wallShift(0, 0), Array(repeating: 0, count: 6))
      image(canvas, over, CGRect(x: 0, y: 0, width: 402, height: 1074))
    }
    if let cg = home.cgImage {
      let input = CIImage(cgImage: cg)
      let filtered = input.clampedToExtent().applyingFilter(
        "CIGaussianBlur", parameters: [kCIInputRadiusKey: 26])
      blurred = imageContext.createCGImage(filtered, from: input.extent)
    }
    for (index, text) in ["Books Don't Change You,", "Practice Does."].enumerated() {
      let format = UIGraphicsImageRendererFormat()
      format.scale = 2
      format.preferredRange = .standard
      let bitmap = UIGraphicsImageRenderer(size: CGSize(width: 402, height: 70), format: format)
        .image { renderer in
          label(
            renderer.cgContext, text, 201, 28, 30, "Bold", index == 0 ? C.ink : C.pink, 1, 0.5,
            -0.02)
        }
      if let cgImage = bitmap.cgImage { titleBitmaps.append(cgImage) }
    }
  }
  func color(_ hex: Int, _ alpha: Double = 1) -> CGColor {
    CGColor(
      srgbRed: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: alpha)
  }
  func round(_ canvas: CGContext, _ rect: CGRect, _ radius: Double, _ hex: Int, _ alpha: Double = 1)
  {
    canvas.setFillColor(color(hex, alpha))
    canvas.addPath(
      CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
    canvas.fillPath()
  }
  func image(_ canvas: CGContext, _ img: CGImage?, _ rect: CGRect) {
    guard let img else { return }
    canvas.saveGState()
    canvas.translateBy(x: rect.minX, y: rect.maxY)
    canvas.scaleBy(x: 1, y: -1)
    canvas.interpolationQuality = .high
    canvas.draw(img, in: CGRect(origin: .zero, size: rect.size))
    canvas.restoreGState()
  }
  func line(
    _ canvas: CGContext, _ points: [CGPoint], _ hex: Int, _ width: Double, _ alpha: Double = 1, _ lift: Double = 0
  ) {
    guard let first = points.first else { return }
    canvas.setStrokeColor(color(hex, alpha))
    canvas.setLineWidth(width)
    canvas.setLineCap(.round)
    canvas.setLineJoin(.round)
    canvas.beginPath()
    canvas.move(to: first)
    for path in points.dropFirst() { canvas.addLine(to: path) }
    canvas.strokePath()
  }
  // Skia positions raw glyphs by their advances; CoreText shaping would change the widths.
  func labelLine(_ text: String, _ size: Double, _ weight: String, _ track: Double = 0) -> Label {
    let key = "\(text)|\(size)|\(weight)|\(track)"
    if let cached = labels[key] { return cached }
    let font =
      (UIFont(name: fonts[weight] ?? "", size: size) ?? UIFont.systemFont(ofSize: size)) as CTFont
    let characters = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: characters.count)
    CTFontGetGlyphsForCharacters(font, characters, &glyphs, characters.count)
    var advances = [CGSize](repeating: .zero, count: characters.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
    var positions: [CGPoint] = []
    var width = 0.0
    let outline = CGMutablePath()
    for index in glyphs.indices {
      positions.append(CGPoint(x: width, y: 0))
      if let glyphPath = CTFontCreatePathForGlyph(font, glyphs[index], nil) {
        outline.addPath(glyphPath, transform: CGAffineTransform(translationX: width, y: 0))
      }
      width += advances[index].width + (index < glyphs.count - 1 ? track * size : 0)
    }
    let result = Label(
      font: font, glyphs: glyphs, positions: positions, width: width, outline: outline)
    labels[key] = result
    return result
  }
  @discardableResult func label(
    _ canvas: CGContext, _ text: String, _ x: Double, _ cy: Double, _ size: Double,
    _ weight: String = "Regular", _ hex: Int = C.ink, _ alpha: Double = 1, _ anchor: Double = 0,
    _ track: Double = 0
  ) -> Double {
    let layout = labelLine(text, size, weight, track)
    canvas.saveGState()
    canvas.setFillColor(color(hex, alpha))
    canvas.textMatrix = .identity
    canvas.translateBy(x: x - layout.width * anchor, y: cy + size * 0.36)
    canvas.scaleBy(x: 1, y: -1)
    CTFontDrawGlyphs(layout.font, layout.glyphs, layout.positions, layout.glyphs.count, canvas)
    canvas.restoreGState()
    return layout.width
  }
  func gradient(
    _ canvas: CGContext, _ rect: CGRect, _ from: CGPoint, _ to: CGPoint, _ colors: [CGColor],
    _ stops: [CGFloat]? = nil
  ) {
    guard
      let gradient = CGGradient(
        colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray,
        locations: stops)
    else { return }
    canvas.saveGState()
    canvas.clip(to: rect)
    canvas.drawLinearGradient(
      gradient, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    canvas.restoreGState()
  }
  func cover(
    _ canvas: CGContext, _ n: Int, _ x: Double, _ y: Double, _ w: Double, _ angle: Double,
    _ sh: Double,
    _ alpha: Double = 1, _ lift: Double = 0
  ) {
    guard let covers else { return }
    let source = CGRect(x: (n % 5) * 300, y: (n / 5) * 450, width: 300, height: 450)
    guard let crop = covers.cropping(to: source) else { return }
    let h = w * 1.5
    let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
    canvas.saveGState()
    canvas.translateBy(x: x, y: y - 3 * lift)
    canvas.rotate(by: angle * .pi / 180)
    canvas.scaleBy(x: 1 + 0.05 * lift, y: 1 + 0.05 * lift)
    if sh > 0.01 {
      blurredRoundedRect(
        canvas, rect.offsetBy(dx: 2, dy: 4 + 8 * sh), radius: 3, sigma: 4 + 8 * sh,
        color: CGColor(srgbRed: 0.1, green: 0.08, blue: 0.15, alpha: 0.22 * sh * alpha))
    }
    canvas.addPath(CGPath(roundedRect: rect, cornerWidth: 2.5, cornerHeight: 2.5, transform: nil))
    canvas.clip()
    canvas.setAlpha(alpha)
    image(canvas, crop, rect)
    if lift > 0.02, let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [color(C.page, 0), color(C.page, 0.22 * lift * alpha), color(C.page, 0)] as CFArray, locations: [0.2, 0.5, 0.8]) {
      canvas.setAlpha(1)
      canvas.setBlendMode(.plusLighter)
      canvas.drawLinearGradient(gradient, start: CGPoint(x: -w / 2, y: -h / 2), end: CGPoint(x: w / 2, y: h / 2), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }
    canvas.restoreGState()
  }
  func wall(_ canvas: CGContext, _ shift: Double, _ taken: [Double], _ amb: Double = -1) {
    for i in 0..<7 {
      for j in -1...6 {
        if M.FLYING.indices.contains(where: {
          M.FLYING[$0].i == i && M.FLYING[$0].j == j && taken[$0] > 0.001
        }) {
          continue
        }
        let path = M.cellAt(i, j, shift)
        if path.y < -60 || path.y > 330 { continue }
        let front = -180 + (amb.truncatingRemainder(dividingBy: M.LOOP / 2) / (M.LOOP / 2)) * 900
        let d = path.x + path.y * 0.6 - front
        let lift = amb < 0 ? 0 : exp(-(d * d) / 2600)
        cover(canvas, M.coverOf(i, j), path.x, path.y, 60, -27, 0.25 + 0.6 * lift, 0.9, lift)
      }
    }
    let colors = [color(C.page, 0.08), color(C.page, 0.62), color(C.page)]
    if let gradient = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray,
      locations: [46 / 150, 0.74, 1])
    {
      canvas.saveGState()
      canvas.clip(to: CGRect(x: 0, y: -60, width: 402, height: 420))
      canvas.drawRadialGradient(
        gradient, startCenter: CGPoint(x: 214, y: 176), startRadius: 0,
        endCenter: CGPoint(x: 214, y: 176),
        endRadius: 150, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
      canvas.restoreGState()
    }
    gradient(
      canvas, CGRect(x: 0, y: 228, width: 402, height: 140), CGPoint(x: 0, y: 228),
      CGPoint(x: 0, y: 292), [color(C.page, 0), color(C.page)])
  }
  func homeOver(_ canvas: CGContext) {
    label(canvas, "retold", 22, 90, 30, "Bold", C.ink, 1, 0, -0.045)
    label(canvas, "Read less, remember more", 22, 309, 15.2, "Medium", C.pink)
    for (i, text) in ["Which book is", "on your mind?"].enumerated() {
      label(canvas, text, 22, Double([344, 383][i]), 35, "Bold", C.ink, 1, 0, -0.02)
    }
    for (i, text) in ["Turn the best ideas from great books into lessons", "you can actually use."]
      .enumerated()
    { label(canvas, text, 22, Double([420, 443][i]), 15, "Regular", C.grey) }
    round(canvas, CGRect(x: 22, y: 476, width: 356, height: 42), 21, C.field)
    canvas.setStrokeColor(color(C.greyLight))
    canvas.setLineWidth(1.6)
    canvas.strokeEllipse(in: CGRect(x: 41.5, y: 489.5, width: 13, height: 13))
    line(canvas, [CGPoint(x: 53, y: 501), CGPoint(x: 56.5, y: 504.5)], C.greyLight, 1.6)
    label(canvas, "Search books or authors", 64, 497, 16, "Regular", C.greyLight)
    let chipX = [22.0, 115, 240, 348]
    let chipW = [80.0, 113, 91, 104]
    for (i, text) in ["For you", "Most popular", "Top picks", "Best sellers"].enumerated() {
      round(
        canvas, CGRect(x: chipX[i], y: 538, width: chipW[i], height: 37), 18.5,
        i == 0 ? C.black : C.chip
      )
      let x = chipX[i] + 16
      let y = 556.5
      canvas.saveGState()
      canvas.translateBy(x: x, y: y)
      if i == 0 {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: 0, y: -6.5))
        path.addQuadCurve(to: CGPoint(x: 6.5, y: 0), control: CGPoint(x: 0.9, y: -0.9))
        path.addQuadCurve(to: CGPoint(x: 0, y: 6.5), control: CGPoint(x: 0.9, y: 0.9))
        path.addQuadCurve(to: CGPoint(x: -6.5, y: 0), control: CGPoint(x: -0.9, y: 0.9))
        path.addQuadCurve(to: CGPoint(x: 0, y: -6.5), control: CGPoint(x: -0.9, y: -0.9))
        canvas.addPath(path)
        canvas.setFillColor(color(C.page))
        canvas.fillPath()
      } else if i == 1 {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: 0, y: -6))
        path.addCurve(
          to: CGPoint(x: 4.2, y: 2.4), control1: CGPoint(x: 3, y: -2.5),
          control2: CGPoint(x: 4.6, y: 0))
        path.addCurve(
          to: CGPoint(x: 0, y: 6.3), control1: CGPoint(x: 3.8, y: 5),
          control2: CGPoint(x: 2, y: 6.3))
        path.addCurve(
          to: CGPoint(x: -4.2, y: 2.6), control1: CGPoint(x: -2.2, y: 6.3),
          control2: CGPoint(x: -4, y: 4.9))
        path.addCurve(
          to: CGPoint(x: -2.2, y: -2.8), control1: CGPoint(x: -4.4, y: 0.4),
          control2: CGPoint(x: -2.8, y: -1))
        path.addCurve(
          to: CGPoint(x: -0.2, y: -0.9), control1: CGPoint(x: -1.4, y: -1.3),
          control2: CGPoint(x: -0.6, y: -0.8))
        path.addCurve(
          to: CGPoint(x: 0, y: -6), control1: CGPoint(x: -0.6, y: -2.6),
          control2: CGPoint(x: -0.6, y: -4.4))
        canvas.addPath(path)
        canvas.setFillColor(color(C.chipInk))
        canvas.fillPath()
      } else if i == 2 {
        for k in 0..<3 {
          line(
            canvas,
            [
              CGPoint(x: -5, y: -4 + Double(k) * 4),
              CGPoint(x: 1 - Double(k) * 1.5, y: -4 + Double(k) * 4),
            ], C.chipInk, 1.5)
        }
        canvas.setFillColor(color(C.chipInk))
        canvas.fillEllipse(in: CGRect(x: 2.7, y: 2.2, width: 3.6, height: 3.6))
      } else {
        canvas.addPath(
          CGPath(
            roundedRect: CGRect(x: -5.5, y: -5, width: 11, height: 10), cornerWidth: 2,
            cornerHeight: 2, transform: nil))
        canvas.setLineWidth(1.5)
        canvas.setStrokeColor(color(C.chipInk))
        canvas.strokePath()
      }
      canvas.restoreGState()
      label(canvas, text, chipX[i] + 30, y, 13.6, "Medium", i == 0 ? C.page : C.chipInk)
    }
    label(canvas, "Recommended for you", 22, 615, 15, "Medium", C.grey)
    for y in [615.0, 842] {
      line(
        canvas, [CGPoint(x: 369, y: y - 5), CGPoint(x: 374, y: y), CGPoint(x: 369, y: y + 5)],
        C.greyLight, 1.8)
    }
    let titles = [
      ["Small Steps,", "Long Roads"], ["The Lighthouse", "Habit"], ["Before the Sun", "Gets Up"],
    ]
    for i in 0..<3 {
      let x = [31.0, 159, 285][i]
      let tx = x - 4 + (i == 2 ? -10 : 0)
      blurredRoundedRect(
        canvas, CGRect(x: x + 2, y: 641, width: 87, height: 116), radius: 0, sigma: 5,
        color: color(0, 0.16))
      if let crop = covers?.cropping(
        to: CGRect(
          x: ([3, 7, 0][i] % 5) * 300, y: ([3, 7, 0][i] / 5) * 450, width: 300, height: 450))
      {
        image(canvas, crop, CGRect(x: x, y: 636, width: 87, height: 116))
      }
      label(canvas, titles[i][0], tx, 770, 14.5, "SemiBold")
      label(canvas, titles[i][1], tx, 788, 14.5, "SemiBold")
      label(
        canvas, ["Mira Okafor", "Jonas Feld", "Ines Varga"][i], tx, 806, 14.5, "Regular", C.grey)
      let n = (i * 4 + 5) % 10
      image(
        canvas,
        covers?.cropping(to: CGRect(x: (n % 5) * 300, y: (n / 5) * 450, width: 300, height: 450)),
        CGRect(x: x, y: 871, width: 87, height: 116))
    }
    label(canvas, "Top picks", 22, 842, 15, "Medium", C.grey)
  }
  func sheetOffset(_ frame: M.Frame, _ driver: M.Driver) -> Double {
    (1 - frame.sheet) * 496 + max(0, driver.drag) * min(1, frame.sheet) + min(0, driver.drag) * 0.25
  }
  func sheet(_ canvas: CGContext, _ frame: M.Frame, _ driver: M.Driver, _ oy: Double) {
    canvas.saveGState()
    canvas.translateBy(x: 0, y: oy)
    let rect = CGRect(x: 12, y: 418, width: 378, height: 442)
    blurredRoundedRect(
      canvas, rect.offsetBy(dx: 0, dy: 6), radius: 42, sigma: 18,
      color: color(0, 0.18 * max(0, frame.sheet)))
    round(canvas, rect, 42, C.page)
    canvas.saveGState()
    canvas.addPath(CGPath(roundedRect: rect, cornerWidth: 42, cornerHeight: 42, transform: nil))
    canvas.clip()
    let drift = 4 * sin(2 * .pi * driver.amb / 9)
    for k in 0..<6 where frame.flights[k] >= 0.86 {
      let sl = M.slot(k)
      let fly = M.FLYING[k]
      cover(
        canvas, M.coverOf(fly.i, fly.j), sl.x + drift, sl.y, 62, sl.angle, 0.4,
        M.smooth((frame.flights[k] - 0.86) / 0.14))
    }
    gradient(
      canvas, CGRect(x: 12, y: 448, width: 378, height: 53), CGPoint(x: 0, y: 448),
      CGPoint(x: 0, y: 500), [color(C.page, 0), color(C.page)])
    canvas.restoreGState()
    let icon = max(0, frame.icon)
    if icon > 0.01 {
      canvas.saveGState()
      canvas.translateBy(x: 201, y: 483)
      canvas.scaleBy(x: 0.7 + 0.3 * icon, y: 0.7 + 0.3 * icon)
      round(canvas, CGRect(x: -31, y: -31, width: 62, height: 62), 15, 0x121212, min(1, icon * 2))
      if frame.logo > 0.01 {
        canvas.setStrokeColor(color(C.page))
        canvas.setLineWidth(5.2)
        canvas.setLineCap(.round)
        canvas.setLineJoin(.round)
        canvas.addPath(
          (frame.logo < 0.999 ? logo.trimmedPath(from: 0, to: frame.logo) : logo).cgPath)
        canvas.strokePath()
      }
      canvas.restoreGState()
    }
    if frame.tag > 0.01 {
      canvas.saveGState()
      canvas.translateBy(x: 0, y: 10 * (1 - frame.tag))
      round(canvas, CGRect(x: 113, y: 557, width: 176, height: 24), 12, C.pinkSoft, frame.tag)
      label(canvas, "Your Books, Put Into Practice", 201, 569, 14, "Medium", C.pink, frame.tag, 0.5)
      canvas.restoreGState()
    }
    for i in 0..<2 {
      let a = [frame.title, frame.title2][i]
      let y = [601.0, 636][i]
      if a <= 0.01 { continue }
      let text = ["Books Don't Change You,", "Practice Does."][i]
      // Focus is a mask blur on a pre-rasterized line, rather than a live page filter.
      if a < 0.98 {
        if titleBitmaps.indices.contains(i) {
          // Blur radii are cached in half-point steps, for the same reason as the masks.
          let blur = (24 * (1 - a)).rounded() / 2
          let key = "\(i)|\(blur)"
          var blurred = titleBlurs[key]
          if blurred == nil {
            let input = CIImage(cgImage: titleBitmaps[i])
            let filter = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: blur])
            blurred = imageContext.createCGImage(filter, from: input.extent)
            titleBlurs[key] = blurred
          }
          canvas.saveGState()
          canvas.setAlpha(a)
          image(canvas, blurred, CGRect(x: 0, y: y + 12 * (1 - a) - 28, width: 402, height: 70))
          canvas.restoreGState()
        }
      } else {
        label(
          canvas, text, 201, y + 12 * (1 - a), 30, "Bold", i == 0 ? C.ink : C.pink, a, 0.5, -0.02)
      }
      if i == 1 && frame.shimmer >= 0 {
        let layout = labelLine(text, 30, "Bold", -0.02)
        let lw = layout.width
        let sx = M.mix(201 - lw / 2 - 60, 201 + lw / 2 + 60, frame.shimmer)
        canvas.saveGState()
        canvas.clip(to: CGRect(x: sx - 40, y: 614, width: 80, height: 44))
        // Text clipping keeps the travelling highlight inside the glyphs.
        canvas.translateBy(x: 201 - lw / 2, y: 636 + 30 * 0.36)
        canvas.scaleBy(x: 1, y: -1)
        canvas.addPath(layout.outline)
        canvas.clip()
        canvas.scaleBy(x: 1, y: -1)
        canvas.translateBy(x: -(201 - lw / 2), y: -(636 + 30 * 0.36))
        gradient(
          canvas, CGRect(x: sx - 40, y: 614, width: 80, height: 44), CGPoint(x: sx - 40, y: 0),
          CGPoint(x: sx + 40, y: 0), [color(C.page, 0), color(0xFFD1E2, 0.95), color(C.page, 0)])
        canvas.restoreGState()
      }
    }
    if frame.sub > 0.01 {
      let texts = [
        "Turn any non-fiction book into interactive exercises",
        "you actually do. Stop reading. Start applying.", "Make knowledge stick forever.",
      ]
      for i in 0..<3 {
        label(
          canvas, texts[i], 201,
          [672.0, 691, 709][i] + 8 * (1 - frame.sub) * (1 + Double(i) * 0.4), 14.2,
          "Regular", C.grey, frame.sub, 0.5)
      }
    }
    if frame.cta > 0.01 {
      canvas.saveGState()
      canvas.translateBy(x: 201, y: 783.5 + 22 * (1 - frame.cta))
      let s = 1 - 0.03 * driver.ctaP
      canvas.scaleBy(x: s, y: s)
      round(canvas, CGRect(x: -165, y: -28.5, width: 330, height: 57), 28.5, C.black, frame.cta)
      let layout = labelLine("Continue with Email", 19, "SemiBold")
      let lw = layout.width + 32
      label(canvas, "Continue with Email", -lw / 2 + 32, 0, 19, "SemiBold", C.page, frame.cta)
      canvas.saveGState()
      canvas.translateBy(x: -lw / 2 + 10, y: 0)
      canvas.addPath(
        CGPath(
          roundedRect: CGRect(x: -10, y: -7.5, width: 20, height: 15), cornerWidth: 2.5,
          cornerHeight: 2.5, transform: nil))
      canvas.setStrokeColor(color(C.page, frame.cta))
      canvas.setLineWidth(1.8)
      canvas.setLineJoin(.round)
      canvas.strokePath()
      line(
        canvas, [CGPoint(x: -9, y: -6), CGPoint(x: 0, y: 1.2), CGPoint(x: 9, y: -6)], C.page, 1.8,
        frame.cta)
      canvas.restoreGState()
      canvas.restoreGState()
    }
    canvas.restoreGState()
  }
  func scene(_ canvas: CGContext, _ size: CGSize, _ frame: M.Frame, _ driver: M.Driver) {
    canvas.setFillColor(color(C.page))
    canvas.fill(CGRect(origin: .zero, size: size))
    let scale = size.width / 402
    let dy = size.height - 874 * scale
    canvas.saveGState()
    canvas.scaleBy(x: scale, y: scale)
    let shift = M.wallShift(driver.amb, driver.scroll)
    wall(canvas, shift, frame.flights, driver.amb)
    canvas.saveGState()
    canvas.translateBy(x: 333.5, y: 90)
    let press = 1 - 0.04 * max(frame.press, driver.pressP)
    canvas.scaleBy(x: press, y: press)
    blurredRoundedRect(
      canvas, CGRect(x: -43.5, y: -15, width: 87, height: 36), radius: 18, sigma: 8,
      color: color(0, 0.08))
    round(canvas, CGRect(x: -43.5, y: -18, width: 87, height: 36), 18, C.page)
    label(canvas, "Sign in", 0, 0, 16, "Medium", C.ink, 1, 0.5)
    canvas.restoreGState()
    image(canvas, over, CGRect(x: 0, y: 0, width: 402, height: 1074))
    if frame.blur > 0.001 {
      let edge = M.mix(-160, 914, frame.blur)
      canvas.saveGState()
      canvas.beginTransparencyLayer(auxiliaryInfo: nil)
      image(canvas, blurred, CGRect(x: 0, y: 0, width: 402, height: 874))
      canvas.setBlendMode(.destinationIn)
      gradient(
        canvas, CGRect(x: 0, y: 0, width: 402, height: 874), CGPoint(x: 0, y: edge),
        CGPoint(x: 0, y: edge + 160), [color(0), color(0, 0)])
      canvas.endTransparencyLayer()
      canvas.restoreGState()
    }
    if frame.dim > 0.001 {
      canvas.setFillColor(color(0, 0.42 * frame.dim))
      canvas.fill(CGRect(x: 0, y: -50, width: 402, height: 974))
    }
    let oy = sheetOffset(frame, driver) + dy / scale
    if frame.sheet > 0.001 { sheet(canvas, frame, driver, oy) }
    for k in 0..<6 {
      let u = frame.flights[k]
      if u <= 0.001 || u >= 0.999 { continue }
      let fly = M.FLYING[k]
      let src = M.cellAt(fly.i, fly.j, shift)
      let sl = M.slot(k)
      let ex = sl.x
      let ey = sl.y + oy
      let cx = M.mix(src.x, ex, 0.5) + (Double(k) - 2.5) * 22
      let cy = M.mix(src.y, ey, 0.45) - 36
      let q = 1 - u
      let lift = sin(.pi * u)
      let x = q * q * src.x + 2 * q * u * cx + u * u * ex
      let y = q * q * src.y + 2 * q * u * cy + u * u * ey
      let w = M.mix(60, 62, u) * (1 + 0.22 * lift)
      let deg = M.mix(-27, sl.angle, u) + 14 * lift * (k % 2 == 0 ? 1 : -1)
      cover(
        canvas, M.coverOf(fly.i, fly.j), x, y, w, deg, lift,
        u > 0.86 ? 1 - M.smooth((u - 0.86) / 0.14) : 1)
    }
    canvas.restoreGState()
  }
}
