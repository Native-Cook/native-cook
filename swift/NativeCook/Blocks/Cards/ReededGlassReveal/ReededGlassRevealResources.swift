import CoreImage
import CoreText
import SwiftUI
import UIKit

/// Original image bytes and original Urbanist font instances. All defocus is cached once, like the RN offscreen images.
enum ReededGlassRevealResources {
    static let register: Void = {
        for weight in ["Light", "Regular"] {
            if let url = Bundle.main.url(forResource: "ReededGlassReveal-Urbanist-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static let sharp: UIImage = {
        guard let url = Bundle.main.url(forResource: "ReededGlassReveal-rgr-portrait", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image
    }()
    static let soft = blurred(2.6 * 3, 3.2 * 3)
    static let haze = blurred(7 * 3, 7 * 3)
    private static func blurred(_ sx: Double, _ sy: Double) -> UIImage {
        guard let cg = sharp.cgImage else { return sharp }
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = CIContext(options: [.workingColorSpace: space, .outputColorSpace: space])
        let image = CIImage(cgImage: cg, options: [.colorSpace: space]), extent = image.extent
        let stretch = sy / sx
        let blur = image.clampedToExtent()
            .transformed(by: CGAffineTransform(scaleX: stretch, y: 1))
            .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sy])
            .transformed(by: CGAffineTransform(scaleX: 1 / stretch, y: 1)).cropped(to: extent)
        guard let out = context.createCGImage(blur, from: extent, format: .RGBA8, colorSpace: space) else { return sharp }
        return UIImage(cgImage: out)
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func toP3(_ hex: UInt32) -> [Double] {
        let rgb = [16, 8, 0].map { Double(hex >> $0 & 255) / 255 }
        let l = rgb.map { $0 <= 0.04045 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        let p = [0.8225 * l[0] + 0.1774 * l[1], 0.0332 * l[0] + 0.9669 * l[1], 0.0171 * l[0] + 0.0724 * l[1] + 0.9108 * l[2]]
        return p.map { $0 <= 0.0031308 ? $0 * 12.92 : 1.055 * pow($0, 1 / 2.4) - 0.055 }
    }
    struct Line { let path: Path; let width: Double; let size: Double }
    static func line(_ text: String, _ size: Double, light: Bool = false, tracking: Double = 0) -> Line {
        _ = register
        guard let url = Bundle.main.url(forResource: "ReededGlassReveal-Urbanist-\(light ? "Light" : "Regular")", withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) else { return Line(path: Path(), width: 0, size: size) }
        let ct = CTFontCreateWithGraphicsFont(font, size, nil, nil)
        var attributes: [NSAttributedString.Key: Any] = [.font: ct]
        if tracking != 0 { attributes[.kern] = tracking }
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
        let runs = CTLineGetGlyphRuns(line) as! [CTRun]
        var all = Path()
        for run in runs {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count), positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            for i in 0..<count {
                if let raw = CTFontCreatePathForGlyph(ct, glyphs[i], nil) {
                    all.addPath(Path(raw), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: positions[i].x, ty: -positions[i].y))
                }
            }
        }
        return Line(path: all, width: CTLineGetTypographicBounds(line, nil, nil, nil), size: size)
    }
    static let title1 = line("Bank Account", 17, tracking: -0.17)
    static let title2 = line("Authenticated platform", 17, tracking: -0.17)
    static let digits = Array("48291730").map { line(String($0), 15, light: true) }
    static let star = line("*", 15, light: true)
    static let lastFour = line("2644", 15, light: true)
    static let names = ["Vidly", "Loop"].map { line($0, 14.5, light: true) }
    static let followers = ["122k  followers", "834k  followers"].map { line($0, 14.5, light: true) }
    static let values = ["+ 120", "+ 220"].map { line($0, 14.5) }
    static let thumbBrand = line("Sable", 4.2)
    static let thumbGroups = line("****  ****", 4)
    static let thumbLast = line("2644", 6.3)
    static let thumbExpiryLabel = line("Expiry date", 3.8)
    static let thumbExpiry = line("10/28", 3.8)
    static let advances: [Character: Double] = ["0": 0.593, "1": 0.245, "2": 0.5365, "3": 0.4855, "4": 0.5355,
        "5": 0.547, "6": 0.535, "7": 0.53, "8": 0.5205, "9": 0.52, "*": 0.3495, " ": 0.24]
    static let digitX: [Double] = {
        var x = 143.4, out: [Double] = []
        for group in ["4829", "1730", "2644"] {
            for ch in group { out.append(x); x += advances[ch]! * 15 }
            x += advances[" "]! * 15
        }
        return out
    }()
    static func draw(_ c: GraphicsContext, _ line: Line, _ x: Double, _ y: Double, _ hex: UInt32, _ alpha: Double = 1, fontScale: Double = 1) {
        if alpha <= 0 { return }
        let p = line.path.applying(CGAffineTransform(a: fontScale, b: 0, c: 0, d: fontScale, tx: x, ty: y + 0.95 * line.size * (fontScale - 1)))
        c.fill(p, with: .color(color(hex, min(1, alpha))))
    }
    static func polyline(_ lines: [[Double]]) -> Path {
        var p = Path()
        for line in lines {
            p.move(to: CGPoint(x: line[0], y: line[1]))
            for i in stride(from: 2, to: line.count, by: 2) { p.addLine(to: CGPoint(x: line[i], y: line[i + 1])) }
        }
        return p
    }
    static let swap = polyline([[1, 4.3, 17, 4.3], [1, 4.3, 5.2, 0.9], [17, 7.7, 1, 7.7], [17, 7.7, 12.8, 11.1]])
    static let plus = polyline([[6.15, 0.7, 6.15, 11.6], [0.7, 6.15, 11.6, 6.15]])
    static let vidly = polyline([[8, 5.2, 11.5, 11, 15, 5.2]])
    static let sable = polyline([[3.9, 0.6, 1.6, 2.3, 3.4, 2.3, 1.1, 4.4]])
    static let loopMark: Path = {
        var p = Path(); p.move(to: CGPoint(x: 7, y: 11))
        p.addCurve(to: CGPoint(x: 11, y: 11), control1: CGPoint(x: 7, y: 8.6), control2: CGPoint(x: 9.4, y: 8.6))
        p.addCurve(to: CGPoint(x: 15, y: 11), control1: CGPoint(x: 12.6, y: 13.4), control2: CGPoint(x: 15, y: 13.4))
        p.addCurve(to: CGPoint(x: 11, y: 11), control1: CGPoint(x: 15, y: 8.6), control2: CGPoint(x: 12.6, y: 8.6))
        p.addCurve(to: CGPoint(x: 7, y: 11), control1: CGPoint(x: 9.4, y: 13.4), control2: CGPoint(x: 7, y: 13.4))
        p.closeSubpath(); return p
    }()
    static let networkA: Path = {
        var p = Path(); p.move(to: CGPoint(x: 2.75, y: 0)); p.addLine(to: CGPoint(x: 5.4, y: 0))
        p.addLine(to: CGPoint(x: 3.6, y: 5.5)); p.addLine(to: CGPoint(x: 2.75, y: 5.5))
        p.addArc(center: CGPoint(x: 2.75, y: 2.75), radius: 2.75, startAngle: .degrees(90), endAngle: .degrees(270), clockwise: false)
        p.closeSubpath(); return p
    }()
    static let networkB: Path = {
        var p = Path(); p.move(to: CGPoint(x: 5.4, y: 0)); p.addLine(to: CGPoint(x: 6.25, y: 0))
        p.addArc(center: CGPoint(x: 6.25, y: 2.75), radius: 2.75, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: 3.6, y: 5.5)); p.closeSubpath(); return p
    }()
}
