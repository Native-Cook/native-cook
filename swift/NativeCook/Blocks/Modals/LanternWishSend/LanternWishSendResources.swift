import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

final class LanternWishSendResources {
  static let shared = LanternWishSendResources()
  let sky: Image, lit: Image, packet: Image
  let distantGlow: Image, heroGlow: Image
  private var heroAtlases: [Double: [Int: Image]] = [:]
  private static let blurContext: CIContext = {
    let cs = CGColorSpace(name: CGColorSpace.sRGB)!
    return CIContext(options: [.workingColorSpace: cs, .outputColorSpace: cs,
                               .useSoftwareRenderer: true, .cacheIntermediates: false])
  }()
  static func color(_ hex: UInt32) -> UIColor {
    UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255, alpha: 1)
  }
  init() {
    for name in ["InstrumentSerif-Italic", "Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
      if let url = Bundle.main.url(forResource: "LanternWishSend-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    func load(_ name: String, _ ext: String) -> Image {
      guard let url = Bundle.main.url(forResource: "LanternWishSend-\(name)", withExtension: ext),
            let source = CGImageSourceCreateWithURL(url as CFURL, nil),
            let cg = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
      else { return Image(uiImage: UIImage()) }
      return Image(decorative: cg, scale: 1, orientation: .up)
    }
    sky = load("sky", "webp")
    lit = load("lantern", "png")
    packet = load("lantern-folded", "png")
    distantGlow = Self.makeGlow(0.55, 0.5)
    heroGlow = Self.makeGlow(0.6, 0.45)
  }
  private static func makeGlow(_ r: Double, _ sigma: Double, unit: Double = 100) -> Image {
    let side = unit * 6
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1; fmt.preferredRange = .standard
    let raw = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: fmt).image { ctx in
      ctx.cgContext.setFillColor(color(0xFFB45A).cgColor)
      ctx.cgContext.fillEllipse(in: CGRect(x: side / 2 - r * unit, y: side / 2 - r * unit,
                                           width: r * unit * 2, height: r * unit * 2))
    }
    let source = CIImage(cgImage: raw.cgImage!)
    let blurred = source.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * unit])
    let cg = blurContext.createCGImage(blurred, from: source.extent)!
    return Image(decorative: cg, scale: 1, orientation: .up)
  }
  func prepareGlows(scale: Double) -> [Int: Image] {
    if let cached = heroAtlases[scale] { return cached }
    // SkBlurMaskFilterImpl caps transformed sigma at 128 device pixels.
    // https://github.com/google/skia/blob/main/src/core/SkBlurMaskFilterImpl.cpp
    // Build once, outside TimelineView. Interpolate four-point bins in premultiplied space.
    var atlas: [Int: Image] = [:]
    let first = Int(floor(128 / (scale * 0.45) / 4)) * 4
    if first <= 170 {
      for size in stride(from: first, through: 172, by: 4) {
        let sigma = min(0.45, 128 / (Double(size) * scale))
        atlas[size] = sigma >= 0.45 ? heroGlow : Self.makeGlow(0.6, sigma, unit: 64)
      }
    }
    heroAtlases[scale] = atlas
    return atlas
  }
  func draw(_ context: GraphicsContext, _ size: CGSize, _ f: LanternWishSendMotion.Frame, _ reduced: Bool, _ glowAtlas: [Int: Image]) {
    let w = size.width, h = size.height, cardY = (h * 0.6).rounded()
    context.draw(sky, in: CGRect(origin: .zero, size: size))
    func image(_ image: Image, _ rect: CGRect, _ alpha: Double = 1) {
      var c = context; c.opacity = alpha; c.draw(image, in: rect)
    }
    func glow(_ image: Image, _ x: Double, _ y: Double, _ sz: Double, _ alpha: Double) {
      selfImage(image, CGRect(x: x - sz * 3, y: y - sz * 3, width: sz * 6, height: sz * 6), alpha)
    }
    func selfImage(_ i: Image, _ r: CGRect, _ a: Double) { image(i, r, a) }
    for i in 0..<22 {
      let depth = 0.25 + Double((i * 37) % 100) / 100 * 0.75
      let y0 = Double((i * 211) % 997) / 997
      let y = h * 1.05 - (y0 + (reduced ? 0 : f.t / 9)).truncatingRemainder(dividingBy: 1) * h * 1.2
      let hx = sin(Double(i) * 12.9898) * 43758.5453
      let x = (hx - floor(hx)) * w + (reduced ? 0 : sin(f.t * 2 * .pi / 9 + Double(i)) * 10 * depth)
      let sz = 20 + 44 * depth * depth
      glow(distantGlow, x, y, sz, 0.18 * depth)
      image(lit, CGRect(x: x - sz * 0.375, y: y - sz * 0.5, width: sz * 0.75, height: sz), 0.45 + 0.55 * depth)
    }
    if f.sending {
      let sz = LanternWishSendMotion.mix(170, 26, LanternWishSendMotion.smoother(f.rise))
      let y = LanternWishSendMotion.mix(cardY, h * 0.12, f.rise) - f.flick * 40
      let x = w / 2 + (reduced ? 0 : sin(f.rise * 7) * 22 * f.rise)
      let width = sz * 0.75
      let stretch = f.light > 0 && f.light < 1 ? 1 + 0.08 * sin(f.light * .pi) : 1
      if f.light > 0 {
        let lower = Int(floor(sz / 4)) * 4
        if let a = glowAtlas[lower], let b = glowAtlas[lower + 4] {
          let fraction = (sz - Double(lower)) / 4
          var c = context; c.opacity = 0.5 * f.light
          c.drawLayer { layer in
            let rect = CGRect(x: x - sz * 3, y: y - sz * 3, width: sz * 6, height: sz * 6)
            var blend = layer; blend.blendMode = .plusLighter
            blend.opacity = 1 - fraction; blend.draw(a, in: rect)
            blend.opacity = fraction; blend.draw(b, in: rect)
          }
        } else { glow(glowAtlas[lower] ?? heroGlow, x, y, sz, 0.5 * f.light) }
      }
      let ph = sz * (0.4 + 0.6 * f.fold)
      image(packet, CGRect(x: x - width / 2, y: y - ph / 2, width: width, height: ph), 1 - f.light)
      image(lit, CGRect(x: x - width / 2, y: y - sz * stretch / 2, width: width, height: sz * stretch), f.light)
    }
  }
}
