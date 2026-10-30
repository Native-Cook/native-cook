import CoreImage
import CoreText
import SwiftUI
import UIKit

enum IsometricScoreGaugeArt {
    typealias Theme = IsometricScoreGaugeTheme
    typealias M = IsometricScoreGaugeMotion
    static let space = CGColorSpace(name: CGColorSpace.sRGB)!
    static let registered: Void = {
        for name in ["DMSerifDisplay-Regular", "ArchivoBlack-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "IsometricScoreGauge-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func font(_ name: String, _ size: Double) -> CTFont {
        _ = registered
        return CTFontCreateWithName(name as CFString, size, nil)
    }
    static let logoFont = font("DMSerifDisplay-Regular", 27)
    static let numberFont = font("ArchivoBlack-Regular", 74)
    static let labelFont = font("Inter-Medium", 11)
    static let captionFont = font("Inter-SemiBold", 9.6)
    static let cardFont = font("DMSerifDisplay-Regular", 15.5)
    static let icons: [CGImage] = {
        guard let url = Bundle.main.url(forResource: "IsometricScoreGauge-icons", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path)?.cgImage else { return [] }
        let side = image.width / 3
        return (0..<6).compactMap { index in
            image.cropping(to: CGRect(x: index % 3 * side, y: index / 3 * side, width: side, height: side))
        }
    }()
    static func cgColor(_ hex: Int, _ alpha: Double = 1) -> CGColor {
        CGColor(colorSpace: space, components: [
            Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255,
            Double(hex & 255) / 255, alpha
        ])!
    }
    static func gradient(_ colors: [CGColor], _ locations: [CGFloat]? = nil) -> CGGradient {
        CGGradient(colorsSpace: space, colors: colors as CFArray, locations: locations)!
    }
    // Glyph paths let the native renderer clip the exact chrome ramp into the bundled font.
    static func glyphs(_ text: String, font: CTFont, tracking: Double = 0) -> (path: CGPath, width: Double) {
        let characters = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: characters.count)
        CTFontGetGlyphsForCharacters(font, characters, &glyphs, characters.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        let path = CGMutablePath()
        var x = 0.0
        for index in glyphs.indices {
            if let glyph = CTFontCreatePathForGlyph(font, glyphs[index], nil) {
                path.addPath(glyph, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += (tracking == 0 ? advances[index].width.rounded() : advances[index].width)
            if index < glyphs.count - 1 { x += tracking * CTFontGetSize(font) }
        }
        return (path, x)
    }
    static func text(_ context: CGContext, _ value: String, x: Double, cy: Double,
                     font: CTFont, color: CGColor, tracking: Double = 0) {
        let glyph = glyphs(value, font: font, tracking: tracking)
        context.saveGState()
        context.translateBy(x: x - glyph.width / 2, y: cy + CTFontGetSize(font) * 0.36)
        context.addPath(glyph.path)
        context.setFillColor(color)
        context.fillPath()
        context.restoreGState()
    }
    static func drawScene(_ context: GraphicsContext, size: CGSize, frame: M.Frame, ambient: Double) {
        var canvas = context
        canvas.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Theme.color(Theme.C.bg)))
        let scale = size.width / Theme.width
        canvas.translateBy(x: 0, y: (size.height - Theme.height * scale) / 2)
        canvas.scaleBy(x: scale, y: scale)
        var arrow = Path()
        arrow.move(to: CGPoint(x: 31.6, y: 93.8)); arrow.addLine(to: CGPoint(x: 59.6, y: 93.8))
        arrow.move(to: CGPoint(x: 38.6, y: 86.8)); arrow.addLine(to: CGPoint(x: 31.6, y: 93.8))
        arrow.addLine(to: CGPoint(x: 38.6, y: 100.8))
        canvas.stroke(arrow, with: .color(Theme.color(Theme.C.ink)),
                      style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
        canvas.withCGContext { cg in
            let logo = glyphs("outlook", font: logoFont)
            cg.saveGState()
            cg.translateBy(x: Theme.UI.logo.x - logo.width / 2, y: Theme.UI.logo.y + 27 * 0.36)
            cg.addPath(logo.path); cg.clip()
            cg.drawLinearGradient(gradient([cgColor(0xF2F2F2), cgColor(0xB8B8BE)]),
                                  start: CGPoint(x: 140 - Theme.UI.logo.x + logo.width / 2, y: 0),
                                  end: CGPoint(x: 262 - Theme.UI.logo.x + logo.width / 2, y: 0),
                                  options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            cg.restoreGState()
            text(cg, "MAKE A CHOICE. SEE WHERE IT TAKES YOU", x: 201, cy: Theme.UI.captionY,
                 font: captionFont, color: cgColor(Theme.C.caption), tracking: 0.12)
        }
        drawGauge(canvas, frame: frame, ambient: ambient)
    }
    static func drawGauge(_ context: GraphicsContext, frame: M.Frame, ambient: Double) {
        typealias G = Theme.Gauge
        let centre = CGPoint(x: G.cx, y: G.cy)
        let discCentre = CGPoint(x: G.cx, y: G.cy - 10)
        context.fill(Path(ellipseIn: CGRect(x: discCentre.x - 151, y: discCentre.y - 151, width: 302, height: 302)),
                     with: .radialGradient(Gradient(stops: [
                        .init(color: Theme.color(0x1A1A1E), location: 0),
                        .init(color: Theme.color(Theme.C.disc), location: 0.82),
                        .init(color: Theme.color(Theme.C.bg, 0), location: 1)
                     ]), center: discCentre, startRadius: 0, endRadius: 151))
        var track = Path()
        track.addArc(center: centre, radius: G.radius, startAngle: .degrees(G.start),
                     endAngle: .degrees(G.start + G.sweep), clockwise: false)
        context.stroke(track, with: .color(Theme.color(Theme.C.track)),
                       style: StrokeStyle(lineWidth: G.width, lineCap: .round))
        let end = G.start + (max(G.low, min(G.high, frame.score)) - G.low) / (G.high - G.low) * G.sweep
        var arc = Path()
        arc.addArc(center: centre, radius: G.radius, startAngle: .degrees(G.start), endAngle: .degrees(end), clockwise: false)
        let colors = [0xE8344B, 0xF2552F, 0xF59A2A, 0xE9D34A, 0x7FD957, 0x2FCB72, 0x2FCB72]
        let positions = [0.0, 0.12, 0.3, 0.48, 0.66, 0.78, 1]
        let stops = zip(colors, positions).map { Gradient.Stop(color: Theme.color($0.0), location: (12 + $0.1 * G.sweep) / 360) }
        let shading = GraphicsContext.Shading.conicGradient(Gradient(stops: stops), center: centre, angle: .degrees(G.start - 12))
        context.drawLayer { glow in
            glow.opacity = 0.35 + 0.08 * sin(2 * .pi * 2 * ambient / M.loop)
            glow.addFilter(.blur(radius: 9))
            glow.stroke(arc, with: shading, style: StrokeStyle(lineWidth: G.width * 3.4, lineCap: .round))
        }
        context.stroke(arc, with: shading, style: StrokeStyle(lineWidth: G.width, lineCap: .round))
        let angle = end * .pi / 180
        let tip = CGPoint(x: G.cx + cos(angle) * G.radius, y: G.cy + sin(angle) * G.radius)
        context.fill(Path(ellipseIn: CGRect(x: tip.x - 2.52, y: tip.y - 2.52, width: 5.04, height: 5.04)),
                     with: .color(.white.opacity(0.85)))
        context.withCGContext { cg in
            text(cg, "300", x: G.labelX[0], cy: G.labelY, font: labelFont, color: cgColor(Theme.C.grey))
            text(cg, "900", x: G.labelX[1], cy: G.labelY, font: labelFont, color: cgColor(Theme.C.grey))
            drawNumber(cg, frame: frame, ambient: ambient)
        }
    }
    static func drawNumber(_ context: CGContext, frame: M.Frame, ambient: Double) {
        typealias G = Theme.Gauge
        let digitWidth = glyphs("8", font: numberFont).width * 0.98
        let capHeight = G.numberSize * 0.72
        let delta = frame.score - frame.base
        let tint = delta >= 0 ? Theme.C.up : Theme.C.down
        let amount = 0.35 * min(1, abs(delta) / 20)
        let mid = CGColor(colorSpace: space, components: [
            M.mix(0.62, Double(tint >> 16 & 255) / 255, amount),
            M.mix(0.62, Double(tint >> 8 & 255) / 255, amount),
            M.mix(0.66, Double(tint & 255) / 255, amount), 1
        ])!
        let chrome = gradient([cgColor(0xFFFFFF), mid, cgColor(0xE6E6EA), cgColor(0x8C8C93)], [0, 0.45, 0.62, 1])
        for index in 0..<3 {
            let place = pow(10.0, Double(2 - index))
            let whole = frame.score / place
            let below = frame.score.truncatingRemainder(dividingBy: place) / place
            let fraction = index == 2 ? whole - floor(whole) : max(0, (below - 0.9) * 10)
            let digit = Int(floor(whole)) % 10
            let x = G.cx - digitWidth * 1.5 + Double(index) * digitWidth
            context.saveGState()
            context.clip(to: CGRect(x: x - 4, y: G.numberY - capHeight / 2 - 6, width: digitWidth + 8, height: capHeight + 12))
            for row in 0..<2 {
                let glyph = glyphs(String((digit + row) % 10), font: numberFont).path
                let y = G.numberY + capHeight / 2 - fraction * (capHeight + 14) + Double(row) * (capHeight + 14)
                context.saveGState()
                context.translateBy(x: x + 1.5, y: y + 2.5)
                context.addPath(glyph); context.setFillColor(cgColor(0x2A2A30)); context.fillPath()
                context.restoreGState()
                context.saveGState()
                context.translateBy(x: x, y: y)
                context.addPath(glyph); context.clip()
                context.drawLinearGradient(chrome, start: CGPoint(x: 0, y: -capHeight), end: .zero,
                                           options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                let q = ambient.truncatingRemainder(dividingBy: M.loop / 2) / (M.loop / 2) * 1.6 - 0.3
                let gx = G.cx - digitWidth * 2 + q * digitWidth * 4
                if q > -0.25 && q < 1.25 {
                    context.drawLinearGradient(gradient([cgColor(0xFFFFFF, 0), cgColor(0xFFFFFF, 0.85), cgColor(0xFFFFFF, 0)], [0.3, 0.5, 0.7]),
                        start: CGPoint(x: gx - 26 - x, y: -capHeight), end: CGPoint(x: gx + 26 - x, y: 0),
                        options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                }
                context.restoreGState()
            }
            context.restoreGState()
        }
    }
    private static let imageContext = CIContext(options: [.workingColorSpace: space, .outputColorSpace: space])
    private static var halos: [String: UIImage] = [:]
    private static func halo(tint: Int, scale: Double) -> UIImage? {
        let key = "\(tint):\(scale)"
        if let image = halos[key] { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.preferredRange = .standard
        let mask = UIGraphicsImageRenderer(size: CGSize(width: 219, height: 267), format: format).image { renderer in
            let cg = renderer.cgContext
            cg.addPath(CGPath(roundedRect: CGRect(x: 42, y: 42, width: 135, height: 183),
                              cornerWidth: 9, cornerHeight: 9, transform: nil))
            cg.setFillColor(cgColor(tint))
            cg.fillPath()
        }
        guard let bitmap = mask.cgImage else { return nil }
        let input = CIImage(cgImage: bitmap)
        let blurred = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 14 * scale])
        guard let output = imageContext.createCGImage(blurred, from: input.extent) else { return nil }
        let image = UIImage(cgImage: output, scale: scale, orientation: .up)
        halos[key] = image
        return image
    }
    static func cardImage(index: Int, lift: Double, angle: Double, scale: Double, ambient: Double) -> CGImage? {
        let scenario = Theme.columns[index / 2][index % 2]
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        // Three blur radii of padding preserve the halo before the perspective transform.
        return UIGraphicsImageRenderer(size: CGSize(width: 219, height: 267), format: format).image { renderer in
            let cg = renderer.cgContext
            cg.translateBy(x: 109.5, y: 133.5)
            let rect = CGRect(x: -67.5, y: -91.5, width: 135, height: 183)
            let path = CGPath(roundedRect: rect, cornerWidth: 9, cornerHeight: 9, transform: nil)
            if lift > 0.01 {
                halo(tint: scenario.delta >= 0 ? Theme.C.up : Theme.C.down, scale: scale)?
                    .draw(in: CGRect(x: -109.5, y: -133.5, width: 219, height: 267),
                          blendMode: .normal, alpha: 0.45 * lift)
            }
            cg.addPath(path); cg.setFillColor(cgColor(Theme.C.card)); cg.fillPath()
            cg.saveGState()
            cg.addPath(path); cg.clip()
            let bottom = scenario.icon == 4 ? 0x24493A : scenario.icon == 5 ? 0x4A3426 : Theme.C.glow
            cg.drawLinearGradient(gradient([cgColor(Theme.C.glow, 0), cgColor(bottom, 0.85)]),
                                  start: CGPoint(x: 0, y: 183 * 0.05), end: CGPoint(x: 0, y: 91.5),
                                  options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            if icons.indices.contains(scenario.icon) {
                let side = 135 * 0.82
                let destination = CGRect(x: -side / 2 + 8, y: 91.5 - side + 12 - 4 * lift - 5 * lift * (0.5 + 0.5 * sin(2 * .pi * 4 * ambient / M.loop)), width: side, height: side)
                cg.saveGState()
                cg.translateBy(x: destination.minX, y: destination.maxY)
                cg.scaleBy(x: 1, y: -1)
                cg.interpolationQuality = .high
                cg.draw(icons[scenario.icon], in: CGRect(x: 0, y: 0, width: side, height: side))
                cg.restoreGState()
            }
            cg.restoreGState()
            cg.addPath(path); cg.setLineWidth(1)
            cg.setStrokeColor(lift > 0.01 ? cgColor(scenario.delta >= 0 ? Theme.C.up : Theme.C.down, 0.4 + 0.5 * lift) : cgColor(Theme.C.cardEdge))
            cg.strokePath()
            for (line, value) in scenario.lines.enumerated() {
                text(cg, value, x: 0, cy: -91.5 + 34 + Double(line) * 22, font: cardFont,
                     color: cgColor(Theme.C.cardText, 1 - 0.35 * M.smooth(abs(angle) / 62)))
            }
        }.cgImage
    }
}
