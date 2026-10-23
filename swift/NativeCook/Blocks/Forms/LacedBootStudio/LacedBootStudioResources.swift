import SwiftUI
import UIKit

final class LacedBootStudioResources: @unchecked Sendable {
    static let shared = LacedBootStudioResources()
    let paper: CGImage
    let bodies: [CGImage]
    let leftBoots: [CGImage]
    let eyelets: [CGImage]
    let soles: [CGImage]
    let shadow: CGImage
    let bodyPath: CGPath
    let decodedBytes: Int
    static let soleBounds = CGRect(x: -4, y: -4, width: 125, height: 332)
    static let shadowBounds = CGRect(x: -46, y: -40, width: 209, height: 422)

    private init() {
        bodyPath = Self.outline()
        paper = Self.raster(size: CGSize(width: 393, height: 852), scale: 1) { context in
            Self.ui(0xF2EDE8).setFill(); context.fill(CGRect(x: 0, y: 0, width: 393, height: 852))
            let colors = [Self.ui(0xF6F2ED).cgColor, Self.ui(0xF2EDE8).cgColor] as CFArray
            let gradient = CGGradient(colorsSpace: Self.space, colors: colors, locations: [0, 1])!
            context.drawRadialGradient(gradient, startCenter: CGPoint(x: 196.5, y: 293), startRadius: 0,
                                       endCenter: CGPoint(x: 196.5, y: 293), endRadius: 252,
                                       options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        let variants: [[UInt32]] = [[0xCB9A73, 0xA56D48, 0x70452F],
                                   [0x736556, 0x493F36, 0x302B26],
                                   [0xA27B59, 0x6F4F3A, 0x4F352A]]
        var right: [CGImage] = [], left: [CGImage] = [], rims: [CGImage] = []
        for variant in variants {
            let body = Self.raster(size: CGSize(width: 117, height: 320), scale: 2, grain: true) { context in
                Self.paintBody(context, colors: variant)
            }
            let overlay = Self.raster(size: CGSize(width: 117, height: 190), scale: 2) { context in
                Self.paintEyelets(context, leather: variant[1])
            }
            let complete = Self.raster(size: CGSize(width: 117, height: 320), scale: 2) { context in
                Self.draw(context, image: body, in: CGRect(x: 0, y: 0, width: 117, height: 320))
                Self.paintCompleteLace(context)
                Self.draw(context, image: overlay, in: CGRect(x: 0, y: 0, width: 117, height: 190))
            }
            right.append(body); left.append(complete); rims.append(overlay)
        }
        bodies = right; leftBoots = left; eyelets = rims
        soles = [0x45352D, 0x98734F].map { value in
            Self.raster(size: Self.soleBounds.size, scale: 2) { context in
                context.translateBy(x: -Self.soleBounds.minX, y: -Self.soleBounds.minY)
                let path = Self.outline()
                context.saveGState()
                context.translateBy(x: 58.5, y: 163.2)
                context.scaleBy(x: 1.051282, y: 1.01875)
                context.translateBy(x: -58.5, y: -160)
                context.addPath(path); Self.ui(UInt32(value)).setFill(); context.fillPath()
                context.addPath(path); Self.ui(0x2D2925, alpha: 0.55).setStroke(); context.setLineWidth(1.3); context.strokePath()
                context.restoreGState()
            }
        }
        shadow = Self.raster(size: Self.shadowBounds.size, scale: 1) { context in
            context.translateBy(x: -Self.shadowBounds.minX, y: -Self.shadowBounds.minY)
            let path = Self.outline()
            context.saveGState()
            context.setShadow(offset: CGSize(width: 0, height: 12), blur: 30, color: Self.ui(0x584130, alpha: 0.15).cgColor)
            Self.ui(0x584130).setFill(); context.addPath(path); context.fillPath(); context.restoreGState()
            context.saveGState()
            context.setShadow(offset: CGSize(width: 0, height: 2), blur: 5, color: Self.ui(0x000000, alpha: 0.22).cgColor)
            Self.ui(0x45352D).setFill(); context.addPath(path); context.fillPath(); context.restoreGState()
        }
        let images = [paper, shadow] + bodies + leftBoots + eyelets + soles
        decodedBytes = images.reduce(0) { $0 + $1.bytesPerRow * $1.height }
    }

    static func color(_ value: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255,
              blue: Double(value & 255) / 255, opacity: alpha)
    }
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!
    private static func ui(_ value: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255,
                blue: CGFloat(value & 255) / 255, alpha: alpha)
    }
    private static func raster(size: CGSize, scale: Int, grain: Bool = false, paint: (CGContext) -> Void) -> CGImage {
        let width = Int(size.width) * scale, height = Int(size.height) * scale
        let memory = calloc(width * height * 4, 1)!
        let context = CGContext(data: memory, width: width, height: height, bitsPerComponent: 8,
                                bytesPerRow: width * 4, space: space,
                                bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0, y: CGFloat(height)); context.scaleBy(x: CGFloat(scale), y: -CGFloat(scale))
        UIGraphicsPushContext(context); paint(context); UIGraphicsPopContext()
        if grain {
            var seed: UInt32 = 0x626F6F74
            let pixels = memory.assumingMemoryBound(to: UInt8.self)
            for index in 0..<(width * height) {
                seed = 1_664_525 &* seed &+ 1_013_904_223
                let delta = Int((seed >> 24) % 9) - 4
                let alpha = Int(pixels[index * 4 + 3])
                for channel in 0..<3 {
                    let offset = index * 4 + channel
                    pixels[offset] = UInt8(max(0, min(alpha, Int(pixels[offset]) + delta * alpha / 255)))
                }
            }
        }
        let bytes = NSData(bytesNoCopy: memory, length: width * height * 4, freeWhenDone: true)
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                       space: space, bitmapInfo: CGBitmapInfo(rawValue: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: CGDataProvider(data: bytes as CFData)!, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }
    private static func draw(_ context: CGContext, image: CGImage, in rect: CGRect) {
        context.saveGState(); context.translateBy(x: rect.minX, y: rect.maxY); context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(origin: .zero, size: rect.size)); context.restoreGState()
    }
    static func outline() -> CGPath {
        let points: [CGPoint] = [(31.0, 6.0), (83, 6), (92, 39), (90, 104), (94, 166), (109, 225),
                                 (106, 276), (88, 307), (56, 318), (24, 306), (7, 277), (8, 231),
                                 (23, 168), (27, 100), (23, 40)].map { CGPoint(x: $0.0, y: $0.1) }
        let path = CGMutablePath(); path.move(to: points[0])
        for i in points.indices {
            let a = points[(i + points.count - 1) % points.count], b = points[i]
            let c = points[(i + 1) % points.count], d = points[(i + 2) % points.count]
            path.addCurve(to: c, control1: CGPoint(x: b.x + (c.x - a.x) / 6, y: b.y + (c.y - a.y) / 6),
                          control2: CGPoint(x: c.x - (d.x - b.x) / 6, y: c.y - (d.y - b.y) / 6))
        }
        path.closeSubpath(); return path
    }
    private static func paintBody(_ context: CGContext, colors: [UInt32]) {
        context.saveGState(); context.addPath(outline()); context.clip()
        let gradient = CGGradient(colorsSpace: space, colors: colors.map { ui($0).cgColor } as CFArray,
                                  locations: [0, 0.48, 1])!
        context.drawLinearGradient(gradient, start: CGPoint(x: 6, y: 20), end: CGPoint(x: 112, y: 292),
                                   options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        let sideShade = UIBezierPath()
        sideShade.move(to: CGPoint(x: 90, y: 55)); sideShade.addCurve(to: CGPoint(x: 91, y: 290),
            controlPoint1: CGPoint(x: 81, y: 158), controlPoint2: CGPoint(x: 117, y: 235))
        sideShade.addLine(to: CGPoint(x: 117, y: 319)); sideShade.addLine(to: CGPoint(x: 117, y: 0)); sideShade.close()
        ui(colors[2], alpha: 0.26).setFill(); sideShade.fill()
        context.restoreGState()
        context.addPath(outline()); ui(0x4F352A, alpha: 0.86).setStroke(); context.setLineWidth(1.2); context.strokePath()

        let tongue = UIBezierPath(roundedRect: CGRect(x: 39, y: 28, width: 38, height: 154), cornerRadius: 13)
        ui(colors[1] == 0xA56D48 ? 0xB58058 : colors[0], alpha: 0.85).setFill(); tongue.fill()
        ui(0x4F352A, alpha: 0.42).setStroke(); tongue.lineWidth = 0.7; tongue.stroke()
        let opening = UIBezierPath(ovalIn: CGRect(x: 34, y: 8, width: 46, height: 32))
        ui(0x35291F).setFill(); opening.fill()
        ui(colors[2]).setStroke(); opening.lineWidth = 5; opening.stroke()
        ui(colors[0], alpha: 0.62).setStroke()
        let rim = UIBezierPath(); rim.addArc(withCenter: CGPoint(x: 57, y: 24), radius: 21.5,
                                            startAngle: .pi * 1.04, endAngle: .pi * 1.96, clockwise: true)
        rim.lineWidth = 1; rim.stroke()

        let toe = UIBezierPath(); toe.move(to: CGPoint(x: 11, y: 232))
        toe.addCurve(to: CGPoint(x: 108, y: 232), controlPoint1: CGPoint(x: 42, y: 211), controlPoint2: CGPoint(x: 79, y: 211))
        seam(toe, width: 0.75)
        for right in [false, true] {
            let seam = UIBezierPath(); seam.move(to: CGPoint(x: right ? 90 : 25, y: 75))
            seam.addCurve(to: CGPoint(x: right ? 102 : 14, y: 213), controlPoint1: CGPoint(x: right ? 83 : 31, y: 131),
                          controlPoint2: CGPoint(x: right ? 92 : 22, y: 182))
            Self.seam(seam, width: 0.65)
        }
        let highlight = UIBezierPath(); highlight.move(to: CGPoint(x: 23, y: 270))
        highlight.addCurve(to: CGPoint(x: 89, y: 281), controlPoint1: CGPoint(x: 29, y: 251), controlPoint2: CGPoint(x: 69, y: 252))
        highlight.lineCapStyle = .round
        for (width, alpha) in [(14.0, 0.008), (10, 0.015), (6, 0.025), (2.2, 0.04)] {
            ui(colors[0], alpha: alpha).setStroke(); highlight.lineWidth = width; highlight.stroke()
        }
        ui(0xE3BE95, alpha: 0.10).setStroke(); highlight.lineWidth = 0.65; highlight.stroke()
        for row in 0..<6 { for right in [false, true] {
            let p = LacedBootStudioGeometry.eyelet(row: row, right: right)
            ui(0x342A22).setFill(); UIBezierPath(ovalIn: CGRect(x: p.x - 2.4, y: p.y - 2.4, width: 4.8, height: 4.8)).fill()
        }}
    }
    private static func seam(_ path: UIBezierPath, width: Double) {
        ui(0x4F352A, alpha: 0.66).setStroke(); path.lineWidth = width; path.stroke()
        let stitches = path.copy() as! UIBezierPath
        stitches.apply(CGAffineTransform(translationX: 1.6, y: 1.3))
        stitches.setLineDash([0.6, 2.4], count: 2, phase: 0)
        ui(0xE5BB8B, alpha: 0.68).setStroke(); stitches.lineWidth = 0.6; stitches.stroke()
    }
    private static func paintEyelets(_ context: CGContext, leather: UInt32) {
        for row in 0..<6 { for right in [false, true] {
            let p = LacedBootStudioGeometry.eyelet(row: row, right: right)
            let ring = UIBezierPath(ovalIn: CGRect(x: p.x - 3.6, y: p.y - 3.6, width: 7.2, height: 7.2))
            ring.append(UIBezierPath(ovalIn: CGRect(x: p.x - 2.4, y: p.y - 2.4, width: 4.8, height: 4.8)))
            ring.usesEvenOddFillRule = true; ui(0xAD9065).setFill(); ring.fill()
            let arc = UIBezierPath(); arc.addArc(withCenter: p, radius: 3.1, startAngle: -.pi, endAngle: -.pi / 2, clockwise: true)
            ui(0xE4CCA0, alpha: 0.65).setStroke(); arc.lineWidth = 1; arc.stroke()
            let upper = (row.isMultiple(of: 2) != right)
            let lip = CGRect(x: p.x - 1.8, y: p.y + (upper ? -3.6 : 1.6), width: 3.6, height: 2)
            ui(0x39271E, alpha: 0.3).setFill(); UIBezierPath(roundedRect: lip.offsetBy(dx: 0, dy: 0.6), cornerRadius: 0.6).fill()
            ui(leather).setFill(); UIBezierPath(roundedRect: lip, cornerRadius: 0.6).fill()
        }}
    }
    private static func paintCompleteLace(_ context: CGContext) {
        for i in 0..<11 {
            let curve = LacedBootStudioGeometry.curve(i, slack: 0)
            let path = UIBezierPath(); path.move(to: curve.a)
            path.addCurve(to: curve.b, controlPoint1: curve.c1, controlPoint2: curve.c2)
            ui(0x281C16, alpha: 0.17).setStroke(); path.lineWidth = 4.8; path.lineCapStyle = .round
            context.saveGState(); context.translateBy(x: 0, y: 1); path.stroke(); context.restoreGState()
            ui(0x564135).setStroke(); path.lineWidth = 2.8; path.stroke()
            context.saveGState(); context.translateBy(x: -0.35, y: -0.45)
            ui(0xA78763).setStroke(); path.lineWidth = 0.65; path.stroke(); context.restoreGState()
        }
    }
}
