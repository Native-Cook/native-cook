import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

// Decode artwork, fit unrounded glyph advances, and blur the page once, outside the frame loop.
final class GoldTicketRewardResources {
  typealias H = GoldTicketRewardTheme
  typealias W = H.WIDTH
  typealias Copy = H.COPY
  static let shared = GoldTicketRewardResources()
  struct Label {
    let bitmap: UIImage
    let width: Double
    let size: Double
    let baseline: Double
  }
  struct Dust {
    var x0, y0, c1x, c1y, c2x, c2y, tx, ty: Double
    var rel, arr, back0, size, warm: Double
  }
  var labels: [String: Label] = [:]
  let cars: UIImage
  let coinFrames: [Image]
  var page = UIImage()
  var dust: [Dust] = []
  static let carsBox: H.Box = {
    let box = H.A.cars
    let height = box.w * 280 / 900
    return H.Box(x: box.x, y: box.y + box.h - height, w: box.w, h: height)
  }()
  init() {
    for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
      if let url = Bundle.main.url(
        forResource: "GoldTicketReward-Poppins-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    func load(_ name: String) -> UIImage {
      guard
        let url = Bundle.main.url(forResource: "GoldTicketReward-\(name)", withExtension: "webp"),
        let src = CGImageSourceCreateWithURL(url as CFURL, nil),
        let cg = CGImageSourceCreateImageAtIndex(
          src, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
      else { return UIImage() }
      return UIImage(cgImage: cg)
    }
    cars = load("cars")
    let atlas = load("coin-atlas").cgImage
    coinFrames = (0..<24).map { index in
      let crop = atlas?.cropping(
        to: CGRect(x: (index % 6) * 256, y: (index / 6) * 256, width: 256, height: 256))
      return Image(uiImage: crop.map { UIImage(cgImage: $0) } ?? UIImage())
    }
    makeLabels()
    makeDust()
    page = makePage()
  }
  private func font(_ weight: String, _ size: Double) -> CTFont {
    (UIFont(name: "Poppins-\(weight)", size: size) ?? UIFont.systemFont(ofSize: size)) as CTFont
  }
  private func advances(_ font: CTFont, _ string: String) -> ([CGGlyph], [CGSize]) {
    let chars = Array(string.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
    var widths = [CGSize](repeating: .zero, count: chars.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &widths, glyphs.count)
    return (glyphs, widths)
  }
  private func fit(_ text: String, _ weight: String, _ target: Double, _ track: Double = 0) -> Label
  {
    let widths = advances(font(weight, 100), text).1
    let size = target / (widths.reduce(0) { $0 + $1.width } / 100 + track * Double(text.count - 1))
    return label(text, weight, size, track)
  }
  private func label(_ text: String, _ weight: String, _ size: Double, _ track: Double = 0) -> Label
  {
    let face = font(weight, size)
    let runs = advances(face, text)
    var x = 0.0
    let points = runs.1.enumerated().map { index, advance -> CGPoint in
      let point = CGPoint(x: x, y: 0)
      x += advance.width + (index < runs.1.count - 1 ? track * size : 0)
      return point
    }
    let pad = 12.0
    let baseline = pad + CTFontGetAscent(face)
    let dimensions = CGSize(
      width: ceil(x + pad * 2),
      height: ceil(CTFontGetAscent(face) + CTFontGetDescent(face) + pad * 2))
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    let bitmap = UIGraphicsImageRenderer(size: dimensions, format: format).image { renderer in
      let context = renderer.cgContext
      context.setFillColor(UIColor.white.cgColor)
      context.translateBy(x: pad, y: baseline)
      context.scaleBy(x: 1, y: -1)
      CTFontDrawGlyphs(face, runs.0, points, runs.0.count, context)
    }
    return Label(bitmap: bitmap, width: x, size: size, baseline: baseline)
  }
  private func makeLabels() {
    labels["title"] = fit(Copy.title, "SemiBold", W.title)
    labels["help"] = fit(Copy.help, "Medium", W.help)
    labels["status"] = fit(Copy.status, "SemiBold", W.status)
    labels["expert"] = label(
      Copy.expert, "SemiBold", fit(Copy.expertRef, "SemiBold", W.expert).size)
    labels["verified"] = fit(Copy.verified, "Regular", W.verified)
    labels["progress"] = fit(Copy.progress, "Medium", W.progress)
    labels["section"] = fit(Copy.section, "SemiBold", W.section, 0.06)
    labels["pros"] = fit(Copy.pros, "SemiBold", W.pros)
    labels["prosSub"] = fit(Copy.prosSub, "Regular", W.prosSub)
    labels["water"] = fit(Copy.water, "Medium", W.water)
    labels["thanks"] = fit(Copy.thanks, "Bold", W.thanks, 0.11)
    labels["callback"] = fit(Copy.callback, "Regular", W.callback)
    labels["winA"] = fit(Copy.win, "SemiBold", W.winA)
    labels["prizeA"] = fit(Copy.prize, "Bold", W.prizeA)
    labels["winB"] = fit(Copy.win, "Medium", W.winB)
    labels["prizeB"] = fit(Copy.prize, "Bold", W.prizeB)
    let benefitSize = fit(Copy.benefits[0], "SemiBold", W.benefitA).size
    for i in Copy.benefits.indices {
      labels["benefit\(i)"] = label(Copy.benefits[i], "SemiBold", benefitSize)
    }
    labels["cta"] = fit(Copy.cta, "Medium", W.ctaA)
  }
  private func hash(_ n: Double) -> Double {
    let s = sin(n * 12.9898) * 43758.5453
    return s - floor(s)
  }
  private func makeDust() {
    guard let image = cars.cgImage else { return }
    let w = image.width
    let h = image.height
    var bytes = [UInt8](repeating: 0, count: w * h * 4)
    bytes.withUnsafeMutableBytes { buffer in
      guard
        let context = CGContext(
          data: buffer.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
          space: CGColorSpaceCreateDeviceRGB(),
          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
      else { return }
      context.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
    }
    var spots: [(u: Double, v: Double)] = []
    for y in stride(from: 4, to: h - 4, by: 9) {
      for x in stride(from: 4, to: w - 4, by: 9) where bytes[(y * w + x) * 4 + 3] > 200 {
        spots.append((Double(x) / Double(w), Double(y) / Double(h)))
      }
    }
    if spots.isEmpty {
      for i in 0..<400 { spots.append((hash(Double(i) + 0.5), 0.3 + 0.6 * hash(Double(i) + 7.7))) }
    }
    let box = Self.carsBox
    let ticket = H.B.ticket
    let timeline = GoldTicketRewardMotion.T.self
    for index in 0..<150 {
      let i = Double(index)
      let s = spots[Int(floor(hash(i + 1.23) * Double(spots.count))) % spots.count]
      let x0 = box.x + s.u * box.w
      let y0 = box.y + s.v * box.h
      let front = ((1 - s.v) * 0.62 + 0.19 + 0.08) / 1.16
      let rel =
        timeline.carsOut[0] + min(1, max(0, front)) * (timeline.carsOut[1] - timeline.carsOut[0])
        + hash(i + 3.1) * 0.05
      let u = hash(i + 4.4)
      let v = hash(i + 5.5)
      let tx = ticket.x + 14 + u * (ticket.w - 28)
      let ty = ticket.y + 10 + v * (ticket.h - 20)
      let reach = (u + (v - 0.5) * 0.35 + 0.4) / 1.8
      let arr = max(
        rel + 0.34, timeline.gold[0] + reach * (timeline.gold[1] - timeline.gold[0]) + 0.02)
      dust.append(
        Dust(
          x0: x0, y0: y0, c1x: x0 + (hash(i + 6.6) - 0.5) * 60, c1y: y0 - 34 - hash(i + 7.7) * 56,
          c2x: tx + (hash(i + 8.8) - 0.5) * 90, c2y: ty - 80 - hash(i + 9.9) * 70, tx: tx, ty: ty,
          rel: rel, arr: arr,
          back0: timeline.goldOut[0] + (1 - u) * 0.3 + hash(i + 2.2) * 0.06,
          size: 1.1 + hash(i + 11.1) * 1.4, warm: hash(i + 12.2)))
    }
  }

}
