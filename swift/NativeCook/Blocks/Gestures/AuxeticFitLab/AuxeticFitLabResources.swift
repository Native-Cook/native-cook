import SwiftUI
import UIKit

/// Exact structural identities, independent of bitmap scale or proximity.
struct AuxeticFitLabGeometry {
    let cosine: Double
    let sine: Double
    let spacing: Double
    let bounds: CGRect
    init(angle: Double) {
        let radians = angle * .pi / 180
        cosine = cos(radians); sine = sin(radians)
        spacing = AuxeticFitLabMath.edge * (cosine + sine)
        bounds = CGRect(x: 196.5 - 2 * spacing, y: 399 - 1.5 * spacing,
                        width: 4 * spacing, height: 3 * spacing)
    }
    func center(column: Int, row: Int) -> CGPoint {
        CGPoint(x: 196.5 + (Double(column) - 1.5) * spacing,
                y: 399 + (Double(row) - 1) * spacing)
    }
    func vertex(cell: Int, corner: Int) -> CGPoint {
        let column = cell % 4, row = cell / 4
        let sign = (column + row).isMultiple(of: 2) ? 1.0 : -1.0
        let half = AuxeticFitLabMath.edge / 2
        let x = (corner == 0 || corner == 3) ? -half : half
        let y = (corner == 0 || corner == 1) ? -half : half
        let c = center(column: column, row: row)
        return CGPoint(x: c.x + cosine * x - sign * sine * y,
                       y: c.y + sign * sine * x + cosine * y)
    }
    func pins() -> [CGPoint] {
        var pins: [CGPoint] = []
        pins.reserveCapacity(31)
        for row in 0..<3 { for column in 0..<3 {
            let positive = (column + row).isMultiple(of: 2)
            pins.append(vertex(cell: row * 4 + column, corner: positive ? 1 : 2))
        }}
        for row in 0..<2 { for column in 0..<4 {
            let positive = (column + row).isMultiple(of: 2)
            pins.append(vertex(cell: row * 4 + column, corner: positive ? 2 : 3))
        }}
        for pair in Self.boundary { pins.append(vertex(cell: pair.0, corner: pair.1)) }
        return pins
    }
    private static let boundary: [(Int, Int)] = [
        (0,0),(0,3),(1,1),(2,0),(3,1),(3,2),(4,0),(7,1),
        (8,2),(8,3),(9,3),(10,2),(11,2),(11,3)
    ]
}

/// Ordinary premultiplied sRGB images. All texture and blur work occurs once.
final class AuxeticFitLabResources {
    static let shared = AuxeticFitLabResources()
    let plate: Image
    let tightShadow: Image
    let ambientShadow: Image
    let paper: Image
    let plateRect = CGRect(x: -32, y: -32, width: 64, height: 64)
    let shadowRect = CGRect(x: -32, y: -32, width: 64, height: 64)
    let ambientRect = CGRect(x: -64, y: -64, width: 128, height: 128)
    let paperRect = CGRect(x: 0, y: 0, width: 393, height: 1024)
    private init() {
        plate = Self.image(Self.makePlate())
        tightShadow = Self.image(Self.shadow(width: 192, height: 192) { x, y in
            let px = x / 3 - 32, py = y / 3 - 32
            let xCoverage = Self.gaussianInterval(px, half: 24.75, sigma: 1.6)
            let yCoverage = Self.gaussianInterval(py, half: 24.75, sigma: 1.6)
            return 0.14 * xCoverage * yCoverage
        })
        // Full-alpha plate-shaped Gaussian coverage; the isolated union receives
        // its 6% opacity once in Art, rather than once for each overlapping cell.
        ambientShadow = Self.image(Self.shadow(width: 256, height: 256) { x, y in
            Self.gaussianInterval(x / 2 - 64, half: 24.75, sigma: 13)
                * Self.gaussianInterval(y / 2 - 64, half: 24.75, sigma: 13)
        })
        paper = Self.image(Self.makePaper())
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    private static func uiColor(_ hex: UInt32, alpha: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    private static func image(_ image: UIImage) -> Image {
        Image(decorative: image.cgImage!, scale: image.scale, orientation: .up)
    }
    private static func makePlate() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3; format.opaque = false; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64), format: format).image { renderer in
            let c = renderer.cgContext
            c.translateBy(x: 32, y: 32)
            let rect = CGRect(x: -24.75, y: -24.75, width: 49.5, height: 49.5)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 0.7).cgPath
            c.saveGState(); c.addPath(path); c.clip()
            c.setFillColor(uiColor(0xE1E6E2).cgColor); c.fill(rect)
            let space = CGColorSpace(name: CGColorSpace.sRGB)!
            // One broad matte diagonal shadow, attached to the rigid plate.
            // Its 80% multiply peak is approximately sRGB (163,175,167).
            let shadowColors = [0.18, 0.68, 0.80, 0.58, 0.08, 0.0]
                .map { uiColor(0xA7B3AC, alpha: $0).cgColor } as CFArray
            let shadow = CGGradient(colorsSpace: space, colors: shadowColors,
                                    locations: [0, 0.18, 0.34, 0.54, 0.74, 1])!
            c.setBlendMode(.multiply)
            c.drawLinearGradient(shadow, start: CGPoint(x: -24.75, y: -24.75),
                                 end: CGPoint(x: 24.75, y: 24.75), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            c.setBlendMode(.normal)
            let highlightColors = [0.0, 0.0, 0.0, 0.55, 0.12, 0.0]
                .map { uiColor(0xFFFFFF, alpha: $0).cgColor } as CFArray
            let highlight = CGGradient(colorsSpace: space, colors: highlightColors,
                                       locations: [0, 0.30, 0.48, 0.68, 0.84, 1])!
            c.drawLinearGradient(highlight, start: CGPoint(x: -24.75, y: -24.75),
                                 end: CGPoint(x: 24.75, y: 24.75), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            c.setStrokeColor(uiColor(0x3A5551, alpha: 0.24).cgColor); c.setLineWidth(0.30)
            for i in 1..<7 {
                let position = -24.75 + Double(i) * 49.5 / 7
                c.move(to: CGPoint(x: position, y: -24.75)); c.addLine(to: CGPoint(x: position, y: 24.75))
                c.move(to: CGPoint(x: -24.75, y: position)); c.addLine(to: CGPoint(x: 24.75, y: position))
            }
            c.strokePath(); c.restoreGState()
            c.addPath(path); c.setLineWidth(0.75); c.setStrokeColor(uiColor(0x3A5551).cgColor); c.strokePath()
        }
    }
    private static func makePaper() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1; format.opaque = true; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: 393, height: 1024), format: format).image { renderer in
            let c = renderer.cgContext
            c.setFillColor(uiColor(0xF6F8F4).cgColor); c.fill(CGRect(x: 0, y: 0, width: 393, height: 1024))
            c.setStrokeColor(uiColor(0x798885, alpha: 0.02).cgColor); c.setLineWidth(0.5)
            for i in 0..<2200 {
                let x = hash(i * 5 + 11) * 393, y = hash(i * 5 + 12) * 1024
                let length = 0.6 + hash(i * 5 + 13) * 1.8
                c.move(to: CGPoint(x: x, y: y))
                c.addLine(to: CGPoint(x: x + length, y: y + 0.35 * (hash(i * 5 + 14) - 0.5)))
            }
            c.strokePath()
        }
    }
    private static func gaussianInterval(_ position: Double, half: Double, sigma: Double) -> Double {
        let divisor = sigma * sqrt(2)
        return 0.5 * (erf((half - position) / divisor) - erf((-half - position) / divisor))
    }
    private static func shadow(width: Int, height: Int, alpha: (Double, Double) -> Double) -> UIImage {
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        for y in 1..<(height - 1) { for x in 1..<(width - 1) {
            // Exact zero outer border; the last two pixels feather to it.
            let distance = min(min(x, width - 1 - x), min(y, height - 1 - y))
            let feather = min(1, Double(distance) / 2)
            let value = min(1, max(0, alpha(Double(x) + 0.5, Double(y) + 0.5) * feather))
            bytes[(y * width + x) * 4 + 3] = UInt8((value * 255).rounded())
        }}
        let data = Data(bytes) as CFData
        let provider = CGDataProvider(data: data)!
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
        let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                            bytesPerRow: width * 4, space: colorSpace, bitmapInfo: bitmapInfo,
                            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return UIImage(cgImage: image, scale: 1, orientation: .up)
    }
    private static func hash(_ seed: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: seed) &+ 0x9E3779B9
        x = (x ^ (x >> 16)) &* 0x7FEB352D
        x = (x ^ (x >> 15)) &* 0x846CA68B
        return Double(x ^ (x >> 16)) / 4294967295
    }
}
