import CoreText
import Metal
import UIKit

/// Native paths use the source's glyph advances, baselines, tracking and three electrical weights.
final class SuperchargeCardOverlay {
    let inkTexture: MTLTexture
    let lightTexture: MTLTexture
    private let ink: CGContext
    private let light: CGContext
    private let width = 1206
    private let height = 2622
    private let scale = 3.0
    private static let inkColor = CGColor(srgbRed: 23 / 255, green: 24 / 255, blue: 26 / 255, alpha: 1)
    private static let gold = CGColor(srgbRed: 1, green: 196 / 255, blue: 0, alpha: 1)
    private static let lemon = CGColor(srgbRed: 1, green: 242 / 255, blue: 122 / 255, alpha: 1)
    private static let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)
    private var runs: [String: (CGPath, Double)] = [:]

    private static let fonts: [String: CTFont] = {
        var fonts: [String: CTFont] = [:]
        let specifications: [(String, String, CGFloat)] = [
            ("title", "Inter-Bold", 28),
            ("body", "Inter-Regular", 13),
            ("mono", "JetBrainsMono-Medium", 10),
            ("caption", "JetBrainsMono-Medium", 11)
        ]
        for (key, file, size) in specifications {
            let url = Bundle.main.url(forResource: "SuperchargeCard-\(file)", withExtension: "ttf")!
            let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as! [CTFontDescriptor]
            fonts[key] = CTFontCreateWithFontDescriptor(descriptors[0], size, nil)
        }
        return fonts
    }()

    init(device: MTLDevice) {
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let info = CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue
        ink = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: colorSpace, bitmapInfo: info)!
        light = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: colorSpace, bitmapInfo: info)!
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false)
        descriptor.usage = .shaderRead
        descriptor.storageMode = .shared
        inkTexture = device.makeTexture(descriptor: descriptor)!
        lightTexture = device.makeTexture(descriptor: descriptor)!
        for context in [ink, light] {
            context.translateBy(x: 0, y: Double(height))
            context.scaleBy(x: scale, y: -scale)
        }
    }

    private func run(_ text: String, font name: String, tracking: Double = 0) -> (CGPath, Double) {
        let key = "\(name)|\(tracking)|\(text)"
        if let value = runs[key] { return value }
        let font = Self.fonts[name]!
        let characters = Array(text.utf16)
        var glyphs = Array(repeating: CGGlyph(), count: characters.count)
        CTFontGetGlyphsForCharacters(font, characters, &glyphs, characters.count)
        var advances = Array(repeating: CGSize.zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        let path = CGMutablePath()
        var x = 0.0
        for i in glyphs.indices {
            if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                let transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0)
                path.addPath(glyph, transform: transform)
            }
            x += advances[i].width + (i + 1 < glyphs.count ? tracking : 0)
        }
        let value = (path as CGPath, x)
        runs[key] = value
        return value
    }

    private func text(_ string: String, font: String, x: Double, y: Double, tracking: Double = 0,
                      alpha: Double = 1, color: CGColor = SuperchargeCardOverlay.inkColor, size: Double = 1) {
        guard alpha > 0 else { return }
        ink.saveGState()
        ink.translateBy(x: x, y: y)
        ink.scaleBy(x: size, y: size)
        ink.setAlpha(alpha)
        ink.setFillColor(color)
        ink.addPath(run(string, font: font, tracking: tracking).0)
        ink.fillPath()
        ink.restoreGState()
    }

    private func cardTransform(_ context: CGContext, card: [Double]) {
        context.translateBy(x: 201 + card[0], y: 458 + card[1])
        context.rotate(by: card[2])
        context.scaleBy(x: card[3], y: card[3])
        context.translateBy(x: -201, y: -458)
    }

    func draw(_ frame: SuperchargeCardFrame, captionFrom: Int, captionTo: Int, captionAt: Double, dotAt: [Double]) {
        let rect = CGRect(x: 0, y: 0, width: 402, height: 874)
        ink.clear(rect)
        light.clear(rect)
        ink.saveGState()
        cardTransform(ink, card: frame.card)
        drawPrint(frame, dotAt: dotAt)
        ink.restoreGState()
        drawCaption(frame, from: captionFrom, to: captionTo, at: captionAt)
        light.saveGState()
        cardTransform(light, card: frame.card)
        drawLightning(frame)
        drawEmbers(frame.array("sparks"))
        light.restoreGState()
        let region = MTLRegionMake2D(0, 0, width, height)
        inkTexture.replace(region: region, mipmapLevel: 0, withBytes: ink.data!, bytesPerRow: width * 4)
        lightTexture.replace(region: region, mipmapLevel: 0, withBytes: light.data!, bytesPerRow: width * 4)
    }

    private func drawPrint(_ frame: SuperchargeCardFrame, dotAt: [Double]) {
        let charged = frame.number("charged")
        let resting = max(0, 1 - 2 * charged)
        let incoming = max(0, 2 * charged - 1)
        text("CHG ", font: "mono", x: 85, y: 492, tracking: 1.2, alpha: 0.56)
        let monoAdvance = run("0", font: "mono").1 + 1.2
        let meter = Int((frame.number("meter") * 100).rounded())
        let digits = [meter / 100, meter / 10 % 10, meter % 10]
        for i in 0..<3 {
            text(String(digits[i]), font: "mono", x: 85 + Double(4 + i) * monoAdvance,
                 y: 492 - 4 * charged, alpha: resting * 0.56)
        }
        text("%", font: "mono", x: 85 + 7 * monoAdvance, y: 492 - 4 * charged, alpha: resting * 0.56)
        text("MAX", font: "mono", x: 85 + 4 * monoAdvance, y: 492 + 4 * (1 - charged), tracking: 1.2, alpha: incoming * 0.56)
        for i in 0..<12 {
            let x = 150 + Double(i) * 6
            ink.setFillColor(Self.inkColor.copy(alpha: 0.26)!)
            ink.fillEllipse(in: CGRect(x: x - 1, y: 487.5, width: 2, height: 2))
            if dotAt[i] >= 0 {
                let pop = min(1, max(0, (frame.clock - dotAt[i]) / 0.12))
                let radius = 0.6 + 0.4 * pop
                ink.saveGState()
                let color = CGColor(srgbRed: 199 / 255, green: 154 / 255, blue: 0, alpha: 1)
                ink.setFillColor(color)
                ink.setShadow(offset: .zero, blur: 1.5 * scale, color: color)
                ink.fillEllipse(in: CGRect(x: x - radius, y: 488.5 - radius, width: radius * 2, height: radius * 2))
                ink.restoreGState()
            }
        }
        text("Supercharge", font: "title", x: 85, y: 544, tracking: -0.6)
        let advance = run("Supercharge", font: "title", tracking: -0.6).1 - 0.6
        let spring = charged >= 1 ? 0 : exp(-6 * charged) * cos(10 * charged)
        text("d", font: "title", x: 85 + advance, y: 544 - 6 * spring, alpha: charged, size: 1 + 0.15 * spring)
        for (i, line) in ["Press and hold the card", "to build up a charge"].enumerated() {
            text(line, font: "body", x: 85, y: 566 + Double(i) * 18 - 4 * charged, alpha: resting * 143 / 255)
        }
        for (i, line) in ["Full power, held in glass.", "Tap the card to let it go."].enumerated() {
            text(line, font: "body", x: 85, y: 566 + Double(i) * 18 + 4 * (1 - charged), alpha: incoming * 143 / 255)
        }
    }

    private func drawCaption(_ frame: SuperchargeCardFrame, from: Int, to: Int, at: Double) {
        let elapsed = frame.clock - at
        for (index, label) in ["HOLD TO CHARGE", "RELEASE", "TAP TO DRAIN"].enumerated() {
            let visibility: Double
            if from == to || elapsed < 0 {
                visibility = index == to ? 1 : 0
            } else if to == -1 {
                visibility = index == from ? max(0, 1 - elapsed / 0.09) : 0
            } else if from == -1 {
                visibility = index == to ? min(1, elapsed / 0.24) : 0
            } else {
                let t = min(1, elapsed / 0.16)
                visibility = index == from ? max(0, 1 - 2 * t) : index == to ? max(0, 2 * t - 1) : 0
            }
            let alpha = visibility * (index == 1 ? 0.85 + 0.15 * sin(frame.clock * 4 * .pi) : 0.46)
            let width = run(label, font: "caption", tracking: 2.2).1
            text(label, font: "caption", x: 201 - width / 2, y: 712 + (1 - visibility) * 4,
                 tracking: 2.2, alpha: alpha, color: index == 1 ? Self.lemon : Self.white)
        }
    }

    private func path(_ points: [Double]) -> CGPath {
        let path = CGMutablePath()
        for i in stride(from: 0, to: points.count - points.count % 4, by: 4) {
            path.move(to: CGPoint(x: points[i], y: points[i + 1]))
            path.addLine(to: CGPoint(x: points[i + 2], y: points[i + 3]))
        }
        return path
    }

    private func stroke(_ path: CGPath, width: Double, blur: Double, alpha: Double, color: CGColor) {
        guard alpha > 0 else { return }
        light.saveGState()
        light.setBlendMode(.plusLighter)
        light.setAlpha(min(1, alpha))
        light.setLineWidth(width)
        light.setLineCap(.round)
        light.setLineJoin(.round)
        light.setStrokeColor(color)
        if blur > 0 {
            // Draw only the shadow: the source stroke is outside the backing bitmap.
            let transform = light.ctm
            light.setShadow(offset: CGSize(width: 2000 * transform.a, height: 2000 * transform.b),
                            blur: blur * scale, color: color)
            light.translateBy(x: -2000, y: 0)
        }
        light.addPath(path)
        light.strokePath()
        light.restoreGState()
    }

    private func drawLightning(_ frame: SuperchargeCardFrame) {
        let values = frame.lightning
        let level = values.level
        let forkLevel = values.forkLevel ?? level
        let firstPulse = values.firstPulse
        for weight in ["main", "branch", "twig", "ground"] {
            let points: [Double]
            switch weight {
            case "main": points = values.main
            case "branch": points = values.branch
            case "twig": points = values.twig
            default: points = values.ground
            }
            if points.isEmpty { continue }
            let alpha = weight == "branch" || weight == "twig" ? forkLevel : level
            let widths: [Double]
            let blurs: [Double]
            let opacity: [Double]
            if weight == "main" {
                widths = firstPulse ? [22, 8, 2.8] : [16, 5.5, 2]
                blurs = [12, 3, 0]
                opacity = [0.42, 0.9, 1]
            } else if weight == "twig" {
                widths = [4.5, 1.4, 0.55]
                blurs = [6, 1.5, 0]
                opacity = [0.18, 0.6, 1]
            } else {
                widths = [7, 2.2, 0.9]
                blurs = [9, 2.2, 0]
                opacity = [0.24, 0.7, 1]
            }
            light.saveGState()
            if weight != "ground" { light.translateBy(x: 0, y: frame.number("sink")) }
            let shape = path(points)
            let colors = [Self.gold, Self.lemon, Self.white]
            for pass in 0..<3 {
                stroke(shape, width: widths[pass], blur: blurs[pass], alpha: alpha * opacity[pass], color: colors[pass])
            }
            light.restoreGState()
        }
    }

    private func drawEmbers(_ sparks: [Double]) {
        for bucket in -1..<3 {
            let thresholds = [0.55, 0.22, 0.0]
            let from = bucket < 0 ? 0.22 : thresholds[bucket]
            let below = bucket <= 0 ? 2 : thresholds[bucket - 1]
            let path = CGMutablePath()
            for i in stride(from: 0, to: sparks.count - sparks.count % 5, by: 5) {
                let life = sparks[i + 4]
                if life <= 0 || life < from || life >= below { continue }
                let speed = max(1, hypot(sparks[i + 2], sparks[i + 3]))
                let tail = max(0.6, speed * 0.022) * life / speed
                path.move(to: CGPoint(x: sparks[i], y: sparks[i + 1]))
                path.addLine(to: CGPoint(x: sparks[i] - sparks[i + 2] * tail, y: sparks[i + 1] - sparks[i + 3] * tail))
            }
            if bucket < 0 {
                stroke(path, width: 3, blur: 4, alpha: 0.5, color: Self.gold)
            } else {
                stroke(path, width: [1.4, 1, 0.7][bucket], blur: 0, alpha: [1, 0.75, 0.4][bucket],
                       color: [Self.white, Self.lemon, Self.gold][bucket])
            }
        }
    }
}
