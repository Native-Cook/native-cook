import SwiftUI
import UIKit

final class LumaRunStudioResources: @unchecked Sendable {
    static let shared = LumaRunStudioResources()
    let source: LumaRunStudioSource
    let frame: CGImage
    let roiMask: CGImage
    let metrics: LumaRunStudioArtMetrics
    let bracket: Path

    private init() {
        let master = Self.raster(width: 384, height: 440, paint: Self.paintCanyon)
        let canonical = Self.canonical(master)
        source = LumaRunStudioSource(master: master.image, canonical: canonical.image,
                                     rgba: Data(referencing: canonical.bytes))
        metrics = Self.measure(canonical.bytes)
        frame = Self.raster(width: 393, height: 462, opaque: false, paint: Self.paintFrame).image
        roiMask = Self.makeROIMask()
        var corners = Path()
        for (x, y, sx, sy) in [(0.0, 0.0, 1.0, 1.0), (275, 0, -1, 1),
                                (0, 300, 1, -1), (275, 300, -1, -1)] {
            corners.move(to: CGPoint(x: x + sx * 6, y: y))
            corners.addLine(to: CGPoint(x: x + sx * 3, y: y))
            corners.addQuadCurve(to: CGPoint(x: x, y: y + sy * 3), control: CGPoint(x: x, y: y))
            corners.addLine(to: CGPoint(x: x, y: y + sy * 6))
        }
        bracket = corners
    }

    private struct Raster {
        let image: CGImage
        let bytes: NSData
    }

    private static func raster(width: Int, height: Int, opaque: Bool = true,
                               paint: (CGContext) -> Void) -> Raster {
        let count = width * height * 4
        let memory = calloc(count, 1)!
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = CGContext(data: memory, width: width, height: height, bitsPerComponent: 8,
                                bytesPerRow: width * 4, space: space,
                                bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue |
                                    CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0, y: CGFloat(height))
        context.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(context)
        paint(context)
        UIGraphicsPopContext()
        if opaque {
            // One deterministic grain sample per source pixel; the canonical copies whole tuples.
            let pixels = memory.assumingMemoryBound(to: UInt8.self)
            var seed: UInt32 = 0x6C756D61
            for index in 0..<(width * height) {
                seed = 1_664_525 &* seed &+ 1_013_904_223
                let delta = Int((seed >> 24) % 9) - 4
                for channel in 0..<3 {
                    let offset = index * 4 + channel
                    pixels[offset] = UInt8(max(0, min(255, Int(pixels[offset]) + delta)))
                }
                pixels[index * 4 + 3] = 255
            }
        }
        let bytes = NSData(bytesNoCopy: memory, length: count, freeWhenDone: true)
        return Raster(image: makeImage(bytes, width: width, height: height), bytes: bytes)
    }

    private static func makeImage(_ bytes: NSData, width: Int, height: Int) -> CGImage {
        CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGBitmapInfo(rawValue: CGBitmapInfo.byteOrder32Big.rawValue |
                    CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: CGDataProvider(data: bytes as CFData)!, decode: nil,
                shouldInterpolate: true, intent: .defaultIntent)!
    }

    private static func canonical(_ master: Raster) -> Raster {
        let memory = malloc(288 * 330 * 4)!
        let output = memory.assumingMemoryBound(to: UInt32.self)
        let input = master.bytes.bytes.assumingMemoryBound(to: UInt32.self)
        for y in 0..<330 {
            let sy = Int(floor((Double(y) + 0.5) * 4 / 3))
            for x in 0..<288 {
                let sx = Int(floor((Double(x) + 0.5) * 4 / 3))
                output[y * 288 + x] = input[sy * 384 + sx]
            }
        }
        let bytes = NSData(bytesNoCopy: memory, length: 288 * 330 * 4, freeWhenDone: true)
        return Raster(image: makeImage(bytes, width: 288, height: 330), bytes: bytes)
    }

    private static func color(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }

    private static func gradient(_ context: CGContext, colors: [UInt32],
                                 from: CGPoint, to: CGPoint) {
        let values = colors.map { color($0).cgColor } as CFArray
        let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!,
                                  colors: values, locations: nil)!
        context.drawLinearGradient(gradient, start: from, end: to,
                                   options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    /// Broad sunlit sediment variation gives eligible runs real color to rearrange.
    /// The dark cliff silhouettes are painted over this immutable field below.
    private static func paintSunlitValley(_ context: CGContext) {
        let width = 384, height = 440
        var pixels = [UInt8](repeating: 255, count: width * height * 4)
        let stops: [[Double]] = [[174, 146, 93], [209, 175, 117], [236, 215, 167]]
        for y in 0..<height {
            for x in 0..<width {
                let px = Double(x), py = Double(y)
                let bend = 0.5 + 0.24 * sin(px / 37 + py / 71)
                    + 0.19 * sin(px / 19 - py / 47)
                    + 0.12 * sin(px / 63 - py / 21)
                let t = min(1, max(0, bend * 0.86 + py / 440 * 0.14))
                let segment = t < 0.5 ? 0 : 1
                let u = (t - Double(segment) * 0.5) * 2
                for channel in 0..<3 {
                    pixels[(y * width + x) * 4 + channel] = UInt8((stops[segment][channel]
                        + (stops[segment + 1][channel] - stops[segment][channel]) * u).rounded())
                }
            }
        }
        let bytes = NSData(bytes: pixels, length: pixels.count)
        let image = makeImage(bytes, width: width, height: height)
        // The raster bytes are top-left; compensate the UIKit-oriented drawing context.
        context.saveGState(); context.translateBy(x: 0, y: 440); context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(x: 0, y: 0, width: 384, height: 440))
        context.restoreGState()
    }

    private static func paintCanyon(_ context: CGContext) {
        paintSunlitValley(context)

        let left = UIBezierPath()
        left.move(to: CGPoint(x: 0, y: 57))
        left.addCurve(to: CGPoint(x: 91, y: 105), controlPoint1: CGPoint(x: 44, y: 61), controlPoint2: CGPoint(x: 65, y: 73))
        left.addCurve(to: CGPoint(x: 126, y: 128), controlPoint1: CGPoint(x: 104, y: 120), controlPoint2: CGPoint(x: 119, y: 117))
        left.addCurve(to: CGPoint(x: 111, y: 174), controlPoint1: CGPoint(x: 136, y: 146), controlPoint2: CGPoint(x: 107, y: 148))
        left.addCurve(to: CGPoint(x: 159, y: 224), controlPoint1: CGPoint(x: 117, y: 200), controlPoint2: CGPoint(x: 149, y: 200))
        left.addCurve(to: CGPoint(x: 133, y: 271), controlPoint1: CGPoint(x: 167, y: 243), controlPoint2: CGPoint(x: 139, y: 253))
        left.addCurve(to: CGPoint(x: 155, y: 331), controlPoint1: CGPoint(x: 124, y: 294), controlPoint2: CGPoint(x: 159, y: 307))
        left.addCurve(to: CGPoint(x: 129, y: 391), controlPoint1: CGPoint(x: 149, y: 357), controlPoint2: CGPoint(x: 128, y: 367))
        left.addLine(to: CGPoint(x: 143, y: 440))
        left.addLine(to: CGPoint(x: 0, y: 440)); left.close()
        context.saveGState(); left.addClip()
        gradient(context, colors: [0xCF8350, 0x9B5237, 0xCF8350], from: CGPoint(x: 15, y: 70), to: CGPoint(x: 143, y: 440))
        for i in 0..<16 {
            let y = 25.0 + Double(i) * 26 + Double((i * 7) % 9)
            let line = UIBezierPath()
            line.move(to: CGPoint(x: -10, y: y))
            line.addCurve(to: CGPoint(x: 179, y: y + 21),
                          controlPoint1: CGPoint(x: 44, y: y - 13 + Double(i % 3) * 5),
                          controlPoint2: CGPoint(x: 122, y: y + 36))
            color(i.isMultiple(of: 2) ? 0xE1A269 : 0x763B2C, alpha: 0.66).setStroke()
            line.lineWidth = i.isMultiple(of: 3) ? 5 : 2.3; line.stroke()
            color(0xEBC395, alpha: 0.34).setStroke(); line.lineWidth = 0.8; line.stroke()
        }
        context.restoreGState()

        let right = UIBezierPath()
        right.move(to: CGPoint(x: 384, y: 0)); right.addLine(to: CGPoint(x: 332, y: 25))
        right.addCurve(to: CGPoint(x: 322, y: 84), controlPoint1: CGPoint(x: 316, y: 43), controlPoint2: CGPoint(x: 337, y: 65))
        right.addCurve(to: CGPoint(x: 277, y: 117), controlPoint1: CGPoint(x: 302, y: 94), controlPoint2: CGPoint(x: 275, y: 98))
        right.addCurve(to: CGPoint(x: 285, y: 169), controlPoint1: CGPoint(x: 269, y: 138), controlPoint2: CGPoint(x: 300, y: 151))
        right.addCurve(to: CGPoint(x: 244, y: 201), controlPoint1: CGPoint(x: 274, y: 187), controlPoint2: CGPoint(x: 253, y: 177))
        right.addCurve(to: CGPoint(x: 261, y: 247), controlPoint1: CGPoint(x: 232, y: 225), controlPoint2: CGPoint(x: 266, y: 225))
        right.addCurve(to: CGPoint(x: 238, y: 302), controlPoint1: CGPoint(x: 258, y: 268), controlPoint2: CGPoint(x: 232, y: 277))
        right.addCurve(to: CGPoint(x: 258, y: 350), controlPoint1: CGPoint(x: 235, y: 325), controlPoint2: CGPoint(x: 263, y: 330))
        right.addLine(to: CGPoint(x: 240, y: 440)); right.addLine(to: CGPoint(x: 384, y: 440)); right.close()
        context.saveGState(); right.addClip()
        gradient(context, colors: [0x846642, 0x4B3D2C, 0x382F25], from: CGPoint(x: 255, y: 80), to: CGPoint(x: 384, y: 430))
        for i in 0..<16 {
            let y = 18.0 + Double(i) * 27 + Double((i * 5) % 7)
            let line = UIBezierPath()
            line.move(to: CGPoint(x: 221, y: y + 28))
            line.addCurve(to: CGPoint(x: 397, y: y), controlPoint1: CGPoint(x: 286, y: y + 8), controlPoint2: CGPoint(x: 338, y: y + 15))
            color(i.isMultiple(of: 2) ? 0xB1915E : 0x2D2D23, alpha: 0.68).setStroke()
            line.lineWidth = i.isMultiple(of: 4) ? 7 : 2.0; line.stroke()
        }
        context.restoreGState()

        // A cut arch and a pale meandering river remain recognizable outside eligible runs.
        let river = UIBezierPath()
        river.move(to: CGPoint(x: 209, y: 164))
        river.addCurve(to: CGPoint(x: 193, y: 214), controlPoint1: CGPoint(x: 202, y: 179), controlPoint2: CGPoint(x: 188, y: 196))
        river.addCurve(to: CGPoint(x: 224, y: 266), controlPoint1: CGPoint(x: 192, y: 238), controlPoint2: CGPoint(x: 222, y: 242))
        river.addCurve(to: CGPoint(x: 170, y: 324), controlPoint1: CGPoint(x: 224, y: 289), controlPoint2: CGPoint(x: 164, y: 301))
        river.addCurve(to: CGPoint(x: 185.5, y: 440), controlPoint1: CGPoint(x: 173, y: 362), controlPoint2: CGPoint(x: 201, y: 389))
        river.addLine(to: CGPoint(x: 240.5, y: 440))
        river.addCurve(to: CGPoint(x: 210, y: 324), controlPoint1: CGPoint(x: 254, y: 387), controlPoint2: CGPoint(x: 206, y: 357))
        river.addCurve(to: CGPoint(x: 254, y: 266), controlPoint1: CGPoint(x: 214, y: 301), controlPoint2: CGPoint(x: 261, y: 293))
        river.addCurve(to: CGPoint(x: 211, y: 214), controlPoint1: CGPoint(x: 247, y: 238), controlPoint2: CGPoint(x: 211, y: 236))
        river.addCurve(to: CGPoint(x: 227, y: 164), controlPoint1: CGPoint(x: 210, y: 193), controlPoint2: CGPoint(x: 225, y: 182)); river.close()
        context.saveGState(); river.addClip()
        gradient(context, colors: [0xFFFBE9, 0xE8D7AB, 0xF4E8C4], from: CGPoint(x: 194, y: 165), to: CGPoint(x: 232, y: 440))
        context.restoreGState()

        let arch = UIBezierPath(ovalIn: CGRect(x: 159, y: 130, width: 96, height: 152))
        let opening = UIBezierPath(ovalIn: CGRect(x: 180, y: 154, width: 55, height: 132))
        arch.append(opening); arch.usesEvenOddFillRule = true
        color(0xF4E8C4).setFill(); arch.fill()
        let archLight = UIBezierPath()
        archLight.move(to: CGPoint(x: 165, y: 209))
        archLight.addCurve(to: CGPoint(x: 228, y: 140), controlPoint1: CGPoint(x: 157, y: 155), controlPoint2: CGPoint(x: 201, y: 121))
        color(0xFFFBE9).setStroke(); archLight.lineWidth = 8; archLight.lineCapStyle = .round; archLight.stroke()
        let darkArch = UIBezierPath(ovalIn: CGRect(x: 185, y: 166, width: 41, height: 93))
        color(0x524731).setFill(); darkArch.fill()

        let foreground = UIBezierPath()
        foreground.move(to: CGPoint(x: 0, y: 298))
        foreground.addCurve(to: CGPoint(x: 114, y: 367), controlPoint1: CGPoint(x: 49, y: 312), controlPoint2: CGPoint(x: 66, y: 354))
        foreground.addCurve(to: CGPoint(x: 160, y: 440), controlPoint1: CGPoint(x: 141, y: 389), controlPoint2: CGPoint(x: 126, y: 415))
        foreground.addLine(to: CGPoint(x: 0, y: 440)); foreground.close()
        color(0x292B22).setFill(); foreground.fill()
        for (x, y, w, h, tilt) in [(166.0, 310.0, 18.0, 12.0, -0.2), (240, 359, 26, 17, 0.3),
                                   (151, 276, 15, 12, 0.4), (278, 398, 30, 18, -0.3),
                                   (176, 386, 20, 13, 0.2), (225, 300, 12, 9, -0.5)] {
            context.saveGState(); context.translateBy(x: x, y: y); context.rotate(by: tilt)
            color(0x393B2B).setFill()
            UIBezierPath(roundedRect: CGRect(x: -w / 2, y: -h / 2, width: w, height: h), cornerRadius: h * 0.38).fill()
            context.restoreGState()
        }

        let label = NSAttributedString(string: "CANYON / 01", attributes: [
            .font: UIFont.systemFont(ofSize: 10 * 384 / 335, weight: .medium),
            .kern: 1.5 * 384 / 335,
            .foregroundColor: color(0xE8DEBA, alpha: 0.8)
        ])
        label.draw(at: CGPoint(x: 11 * 384 / 335, y: 13 * 440 / 384))
    }

    private static func paintFrame(_ context: CGContext) {
        let rect = CGRect(x: 24, y: 26, width: 345, height: 394)
        let path = UIBezierPath(roundedRect: rect, cornerRadius: 14)
        context.saveGState(); context.setShadow(offset: CGSize(width: 0, height: 12), blur: 24,
                                              color: color(0x000000, alpha: 0.18).cgColor)
        color(0x2C302C).setFill(); path.fill(); context.restoreGState()
        context.saveGState(); context.setShadow(offset: CGSize(width: 0, height: 2), blur: 3,
                                              color: color(0x000000, alpha: 0.22).cgColor)
        color(0x2C302C).setFill(); path.fill(); context.restoreGState()
        context.saveGState(); path.addClip()
        color(0xFFFFFF, alpha: 0.08).setStroke()
        let top = UIBezierPath(); top.move(to: CGPoint(x: 38, y: 26.5)); top.addLine(to: CGPoint(x: 355, y: 26.5))
        top.lineWidth = 1; top.stroke()
        color(0x000000, alpha: 0.35).setStroke()
        let bottom = UIBezierPath(); bottom.move(to: CGPoint(x: 38, y: 419.5)); bottom.addLine(to: CGPoint(x: 355, y: 419.5))
        bottom.lineWidth = 1; bottom.stroke(); context.restoreGState()
    }

    private static func makeROIMask() -> CGImage {
        let width = 275, height = 300
        let memory = malloc(width * height * 4)!
        let pixels = memory.assumingMemoryBound(to: UInt8.self)
        for y in 0..<height { for x in 0..<width {
            let qx = abs(Double(x) + 0.5 - 137.5) - 134.5
            let qy = abs(Double(y) + 0.5 - 150) - 147
            let distance = hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - 3
            let u = min(1, max(0, -distance / 2))
            let alpha = UInt8((255 * u * u * (3 - 2 * u)).rounded())
            let index = (y * width + x) * 4
            pixels[index] = alpha; pixels[index + 1] = alpha
            pixels[index + 2] = alpha; pixels[index + 3] = alpha
        }}
        let bytes = NSData(bytesNoCopy: memory, length: width * height * 4, freeWhenDone: true)
        return makeImage(bytes, width: width, height: height)
    }

    private static func measure(_ bytes: NSData) -> LumaRunStudioArtMetrics {
        let pixels = bytes.bytes.assumingMemoryBound(to: UInt8.self)
        let lut = (0...255).map { value -> Double in
            let c = Double(value) / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        var minimum = 1.0, maximum = 0.0, dark = 0, opaque = 0
        for index in 0..<(288 * 330) {
            let offset = index * 4
            let y = 0.2126 * lut[Int(pixels[offset])] + 0.7152 * lut[Int(pixels[offset + 1])] + 0.0722 * lut[Int(pixels[offset + 2])]
            minimum = min(minimum, y); maximum = max(maximum, y)
            if y < 0.25 { dark += 1 }; if pixels[offset + 3] == 255 { opaque += 1 }
        }
        return LumaRunStudioArtMetrics(minimumY: minimum, maximumY: maximum,
                                      darkFraction: Double(dark) / (288 * 330), opaquePixels: opaque)
    }
}

struct LumaRunStudioArtMetrics: Sendable {
    let minimumY: Double
    let maximumY: Double
    let darkFraction: Double
    let opaquePixels: Int
}
