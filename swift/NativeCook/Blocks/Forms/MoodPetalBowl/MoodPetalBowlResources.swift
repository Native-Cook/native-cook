import SwiftUI
import UIKit
import CoreImage

/// Ordinary premultiplied sRGB art, prepared once before the scene epoch starts.
final class MoodPetalBowlResources {
    static let shared = MoodPetalBowlResources()
    static let canvasOrigin = CGPoint(x: 9, y: 111)
    static let canvasSize = CGSize(width: 375, height: 345)
    static let spriteSide = 104.0
    static let spriteRadius = 43.0
    let contours: [[CGPoint]]
    let tokenSprites: [CGImage]
    let tokenImages: [Image]
    let paper: CGImage
    let header: CGImage
    let sharpStrip: CGImage
    let softStrip: CGImage
    let paperImage: Image
    let headerImage: Image
    let sharpStripImage: Image
    let softStripImage: Image
    let bowlDisk: Path
    let sharpVisibility: Path

    private init() {
        contours = MoodPetalBowlGeometry.contours
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let filterContext = CIContext(options: [.workingColorSpace: space, .outputColorSpace: space,
                                                .cacheIntermediates: false])
        let sprites = MoodPetalBowlMood.allCases.map { mood in
            let raw = Self.raster(size: CGSize(width: 104, height: 104), scale: 3) { g in
                let samples = MoodPetalBowlGeometry.contours[mood.rawValue]
                let p = CGMutablePath()
                for (i, point) in samples.enumerated() {
                    let at = CGPoint(x: 52 + point.x * 43, y: 52 + point.y * 43)
                    if i == 0 { p.move(to: at) } else { p.addLine(to: at) }
                }
                p.closeSubpath(); g.addPath(p); g.setFillColor(Self.uiColor(mood.hex).cgColor); g.fillPath()
            }
            let source = CIImage(cgImage: raw)
            let softened = source.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 1.5])
            return filterContext.createCGImage(softened, from: source.extent, format: .RGBA8, colorSpace: space)!
        }
        tokenSprites = sprites
        tokenImages = sprites.map { Image(decorative: $0, scale: 3, orientation: .up) }
        let p = Self.makePaper(); paper = p; paperImage = Image(decorative: p, scale: 1, orientation: .up)
        let h = Self.makeHeader(); header = h; headerImage = Image(decorative: h, scale: 3, orientation: .up)
        let sharp = Self.makeStrip(soft: false), soft = Self.makeStrip(soft: true)
        sharpStrip = sharp; softStrip = soft
        sharpStripImage = Image(decorative: sharp, scale: 3, orientation: .up)
        softStripImage = Image(decorative: soft, scale: 3, orientation: .up)
        let disk = Path(ellipseIn: CGRect(x: 34.5, y: 9, width: 306, height: 306))
        bowlDisk = disk
        var visible = Path(CGRect(x: 0, y: 0, width: 375, height: 162))
        visible.addPath(disk); sharpVisibility = visible
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
        let format = UIGraphicsImageRendererFormat(); format.scale = scale
        format.opaque = false; format.preferredRange = .standard
        let original = UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }.cgImage!
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let g = CGContext(data: nil, width: original.width, height: original.height, bitsPerComponent: 8,
                          bytesPerRow: original.width * 4, space: space,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        g.draw(original, in: CGRect(x: 0, y: 0, width: original.width, height: original.height))
        return g.makeImage()!
    }
    private static func makeStrip(soft: Bool) -> CGImage {
        let height = 345 * 3
        var bytes = [UInt8](repeating: 0, count: height * 4)
        for row in 0..<height {
            let globalY = 111 + (Double(row) + 0.5) / 3
            let u = min(1, max(0, (globalY - 270) / 6)), w = u * u * (3 - 2 * u)
            let entry = min(1, max(0, (globalY - 137) / 16))
            let top = entry * entry * (3 - 2 * entry)
            let a = UInt8(((soft ? w : (1 - w) * top) * 255).rounded())
            for c in 0..<4 { bytes[row * 4 + c] = a }
        }
        return image(bytes: bytes, width: 1, height: height)
    }
    private static func makePaper() -> CGImage {
        let width = 393, height = 1024
        var bytes = [UInt8](repeating: 255, count: width * height * 4)
        for y in 0..<height { for x in 0..<width {
            var seed = UInt32(x + y * width) &* 1664525 &+ 1013904223
            seed ^= seed >> 13; seed = seed &* 2246822519; seed ^= seed >> 16
            let grain = (Double(seed & 65535) / 65535 - 0.5) * 5.0
            let offset = (y * width + x) * 4
            for (c, base) in [244.0, 241.0, 234.0].enumerated() {
                bytes[offset + c] = UInt8(min(255, max(0, (base + grain).rounded())))
            }
        }}
        return image(bytes: bytes, width: width, height: height)
    }
    private static func image(bytes: [UInt8], width: Int, height: Int) -> CGImage {
        let data = Data(bytes) as CFData
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                       bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: CGDataProvider(data: data)!, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }
    private static func makeHeader() -> CGImage {
        raster(size: CGSize(width: 393, height: 128), scale: 3) { g in
            g.setFillColor(uiColor(0xE7E4DD).cgColor)
            g.fillEllipse(in: CGRect(x: 28, y: 74, width: 36, height: 36))
            g.setStrokeColor(uiColor(0x8E8A81).cgColor); g.setLineWidth(1.5)
            let gear = CGMutablePath()
            for i in 0..<24 {
                let a = Double(i) * .pi / 12 - .pi / 2, r = i % 4 < 2 ? 7.5 : 6.0
                let p = CGPoint(x: 46 + cos(a) * r, y: 92 + sin(a) * r)
                if i == 0 { gear.move(to: p) } else { gear.addLine(to: p) }
            }
            gear.closeSubpath(); g.addPath(gear); g.strokePath()
            g.strokeEllipse(in: CGRect(x: 43, y: 89, width: 6, height: 6))
            for (i, x) in [229.0, 265.0, 301.0].enumerated() {
                let fills: [UInt32] = [0xD8B3AE, 0xADBBA5, 0xD5BA99]
                let inks: [UInt32] = [0x784E45, 0x4A6259, 0x806145]
                g.saveGState(); g.addEllipse(in: CGRect(x: x - 15, y: 76, width: 30, height: 30)); g.clip()
                g.setFillColor(uiColor(fills[i]).cgColor); g.fill(CGRect(x: x - 15, y: 76, width: 30, height: 30))
                g.setFillColor(uiColor(inks[i], alpha: 0.82).cgColor)
                g.fillEllipse(in: CGRect(x: x - 5, y: 81, width: 10, height: 11))
                g.fillEllipse(in: CGRect(x: x - 11, y: 94, width: 22, height: 21))
                g.setStrokeColor(uiColor(fills[i], alpha: 0.70).cgColor); g.setLineWidth(1)
                g.move(to: CGPoint(x: x - 3, y: 97)); g.addLine(to: CGPoint(x: x + 3, y: 97)); g.strokePath()
                g.restoreGState()
            }
            g.saveGState(); g.translateBy(x: 347, y: 92)
            let share = CGMutablePath(); share.move(to: CGPoint(x: -8, y: 5))
            share.addCurve(to: CGPoint(x: 3, y: -3), control1: CGPoint(x: -5, y: -1), control2: CGPoint(x: -2, y: -3))
            share.addLine(to: CGPoint(x: 3, y: -7)); share.addLine(to: CGPoint(x: 9, y: -1))
            share.addLine(to: CGPoint(x: 3, y: 4)); share.addLine(to: CGPoint(x: 3, y: 0))
            share.addCurve(to: CGPoint(x: -8, y: 5), control1: CGPoint(x: -2, y: 0), control2: CGPoint(x: -5, y: 1))
            share.closeSubpath(); g.addPath(share); g.setFillColor(uiColor(0x191A17).cgColor); g.fillPath()
            g.restoreGState()
        }
    }
}
