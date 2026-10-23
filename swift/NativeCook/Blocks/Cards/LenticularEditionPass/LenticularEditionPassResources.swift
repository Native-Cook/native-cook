import SwiftUI
import UIKit

/// Ordinary, tagged sRGB artwork. Originals and the guarded lens atlas are cached once.
final class LenticularEditionPassResources {
    static let shared = LenticularEditionPassResources()
    static let atlasSize = CGSize(width: 350, height: 426)
    static let atlasScale = 3.0
    static let artRects = [CGRect(x: 8.5, y: 1, width: 333, height: 211),
                           CGRect(x: 8.5, y: 214, width: 333, height: 211)]
    let artCGImages: [CGImage]
    let artImages: [Image]
    let atlas: CGImage
    let atlasImage: Image
    let paper: CGImage
    let paperImage: Image

    private init() {
        let originals = [Self.makeArt(viewB: false), Self.makeArt(viewB: true)]
        artCGImages = originals
        artImages = originals.map { Image(decorative: $0, scale: 2, orientation: .up) }
        let combined = Self.makeAtlas(originals)
        atlas = combined; atlasImage = Image(decorative: combined, scale: 3, orientation: .up)
        let backdrop = Self.makePaper()
        paper = backdrop; paperImage = Image(decorative: backdrop, scale: 1, orientation: .up)
    }

    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }

    private static func uiColor(_ hex: UInt32, alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: alpha)
    }

    private static func raster(size: CGSize, scale: Double, draw: (CGContext) -> Void) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale; format.opaque = false; format.preferredRange = .standard
        let raw = UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }.cgImage!
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let g = CGContext(data: nil, width: raw.width, height: raw.height, bitsPerComponent: 8,
                          bytesPerRow: raw.width * 4, space: space,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        g.draw(raw, in: CGRect(x: 0, y: 0, width: raw.width, height: raw.height))
        return g.makeImage()!
    }

    private static func gradient(_ g: CGContext, colors: [UInt32], from: CGPoint, to: CGPoint) {
        let values = colors.map { uiColor($0).cgColor } as CFArray
        let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: values,
                                  locations: nil)!
        g.drawLinearGradient(gradient, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    private static func makeArt(viewB: Bool) -> CGImage {
        let rendered = raster(size: CGSize(width: 512, height: 324), scale: 2) { g in
            gradient(g, colors: [0x10232F, 0x243D55, 0x314C68],
                     from: CGPoint(x: 70, y: 18), to: CGPoint(x: 465, y: 324))
            // Cached soft fog: Gaussian alpha with a zero-alpha, zero-slope outer tail.
            let locations = (0...32).map { CGFloat($0) / 32 }
            let fogColors = locations.map { radius -> CGColor in
                let r = Double(radius), u = min(1, max(0, (r - 0.75) / 0.25))
                let alpha = 0.12 * exp(-4.5 * r * r) * (1 - u * u * (3 - 2 * u))
                return uiColor(0xA9C6D4, alpha: alpha).cgColor
            } as CFArray
            let fog = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                                 colors: fogColors, locations: locations)!
            g.saveGState(); g.translateBy(x: 267, y: 162); g.scaleBy(x: 176, y: 114)
            g.drawRadialGradient(fog, startCenter: .zero, startRadius: 0,
                                 endCenter: .zero, endRadius: 1, options: [])
            g.restoreGState()

            if viewB {
                // Three continuous porcelain arches; their shared baseline belongs to the print.
                for (width, height) in [(176.0, 164.0), (130.0, 132.0), (84.0, 100.0)] {
                    let x = 270 - width / 2, top = 242 - height
                    let path = CGMutablePath()
                    path.move(to: CGPoint(x: x, y: 242))
                    path.addLine(to: CGPoint(x: x, y: top + width / 2))
                    path.addCurve(to: CGPoint(x: x + width, y: top + width / 2),
                                  control1: CGPoint(x: x, y: top - width / 6),
                                  control2: CGPoint(x: x + width, y: top - width / 6))
                    path.addLine(to: CGPoint(x: x + width, y: 242))
                    g.saveGState(); g.setLineWidth(24); g.setLineCap(.butt)
                    g.addPath(path); g.replacePathWithStrokedPath(); g.clip()
                    gradient(g, colors: [0xF0E9DA, 0xC7D5DF, 0x647B92],
                             from: CGPoint(x: x, y: top), to: CGPoint(x: x + width, y: 255))
                    g.restoreGState()
                }
                g.saveGState(); g.addEllipse(in: CGRect(x: 106, y: 188, width: 44, height: 44)); g.clip()
                let colors = [uiColor(0xF2D3A0).cgColor, uiColor(0xCFA568).cgColor, uiColor(0x765438).cgColor] as CFArray
                let sphere = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.58, 1])!
                g.drawRadialGradient(sphere, startCenter: CGPoint(x: 120, y: 201), startRadius: 0,
                                     endCenter: CGPoint(x: 128, y: 210), endRadius: 28, options: .drawsAfterEndLocation)
                g.restoreGState()
            } else {
                g.saveGState(); g.addEllipse(in: CGRect(x: 178, y: 75, width: 164, height: 164)); g.clip()
                gradient(g, colors: [0xF0E9DA, 0xC7D5DF, 0x647B92],
                         from: CGPoint(x: 215, y: 80), to: CGPoint(x: 322, y: 236))
                g.saveGState(); g.translateBy(x: 260, y: 157); g.rotate(by: -.pi / 5)
                g.setFillColor(uiColor(0x4774B7).cgColor)
                g.fill(CGRect(x: -11, y: -115, width: 22, height: 230)); g.restoreGState()
                g.restoreGState()
                g.saveGState(); g.translateBy(x: 260, y: 157); g.rotate(by: -22 * .pi / 180)
                g.setLineWidth(1.15); g.setStrokeColor(uiColor(0xB8CDD8, alpha: 0.72).cgColor)
                g.strokeEllipse(in: CGRect(x: -159, y: -48, width: 318, height: 96))
                g.setStrokeColor(uiColor(0x8BA7BD, alpha: 0.76).cgColor)
                g.strokeEllipse(in: CGRect(x: -125, y: -77, width: 250, height: 154))
                g.restoreGState()
                g.saveGState(); g.addRect(CGRect(x: 68, y: 83, width: 39, height: 161)); g.clip()
                gradient(g, colors: [0x708899, 0x334E66, 0x21394B],
                         from: CGPoint(x: 68, y: 83), to: CGPoint(x: 109, y: 247)); g.restoreGState()
                g.setStrokeColor(uiColor(0xB9CAD1, alpha: 0.26).cgColor); g.setLineWidth(0.65)
                g.move(to: CGPoint(x: 68.5, y: 244)); g.addLine(to: CGPoint(x: 68.5, y: 83.5))
                g.addLine(to: CGPoint(x: 106.5, y: 83.5)); g.strokePath()
            }
        }
        return grain(rendered, amplitude: 5.61)
    }

    private static func makeAtlas(_ originals: [CGImage]) -> CGImage {
        raster(size: atlasSize, scale: atlasScale) { _ in
            for (index, source) in originals.enumerated() {
                let r = artRects[index]
                UIImage(cgImage: source).draw(in: r)
                // Replicate one source texel through every gutter so linear sampling never sees the other print.
                let left = source.cropping(to: CGRect(x: 0, y: 0, width: 1, height: source.height))!
                let right = source.cropping(to: CGRect(x: source.width - 1, y: 0, width: 1, height: source.height))!
                let top = source.cropping(to: CGRect(x: 0, y: 0, width: source.width, height: 1))!
                let bottom = source.cropping(to: CGRect(x: 0, y: source.height - 1, width: source.width, height: 1))!
                UIImage(cgImage: left).draw(in: CGRect(x: 0, y: r.minY, width: r.minX, height: r.height))
                UIImage(cgImage: right).draw(in: CGRect(x: r.maxX, y: r.minY, width: 350 - r.maxX, height: r.height))
                UIImage(cgImage: top).draw(in: CGRect(x: r.minX, y: r.minY - 1, width: r.width, height: 1))
                UIImage(cgImage: bottom).draw(in: CGRect(x: r.minX, y: r.maxY, width: r.width, height: 1))
            }
        }
    }

    private static func grain(_ source: CGImage, amplitude: Double) -> CGImage {
        let width = source.width, height = source.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        bytes.withUnsafeMutableBytes { buffer in
            let g = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                              bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            g.draw(source, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        for y in 0..<height { for x in 0..<width {
            var seed = UInt32(x + y * width) &* 1664525 &+ 1013904223
            seed ^= seed >> 13; seed = seed &* 2246822519; seed ^= seed >> 16
            let noise = (Double(seed & 65535) / 65535 - 0.5) * amplitude
            let o = (y * width + x) * 4
            for channel in 0..<3 { bytes[o + channel] = UInt8(min(255, max(0, (Double(bytes[o + channel]) + noise).rounded()))) }
        }}
        return image(bytes: bytes, width: width, height: height)
    }

    private static func makePaper() -> CGImage {
        let width = 393, height = 1024
        var bytes = [UInt8](repeating: 255, count: width * height * 4)
        for y in 0..<height { for x in 0..<width {
            let radius = hypot((Double(x) - 196.5) / 330, (Double(y) - 345) / 650)
            let center = max(0, 1 - radius), weight = center * center
            var seed = UInt32(x + y * width) &* 1664525 &+ 1013904223
            seed ^= seed >> 13; seed = seed &* 2246822519; seed ^= seed >> 16
            let noise = (Double(seed & 65535) / 65535 - 0.5) * 5.1
            let offset = (y * width + x) * 4
            for (c, endpoints) in [(237.0, 247.0), (239.0, 248.0), (233.0, 242.0)].enumerated() {
                bytes[offset + c] = UInt8(min(255, max(0, (endpoints.0 + (endpoints.1 - endpoints.0) * weight + noise).rounded())))
            }
        }}
        return image(bytes: bytes, width: width, height: height)
    }

    private static func image(bytes: [UInt8], width: Int, height: Int) -> CGImage {
        CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: CGDataProvider(data: Data(bytes) as CFData)!, decode: nil,
                shouldInterpolate: true, intent: .defaultIntent)!
    }
}
