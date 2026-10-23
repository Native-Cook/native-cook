import SwiftUI
import UIKit

/// One card of the flow: its column, its place in the 393-wide design space and its size.
struct GlowSearchSplashCard {
    let column: Int
    let row: Int
    let x: Double
    let width: Double
    let height: Double
    let baseY: Double

    static let radius = 20.0
    /// Upward drift in points per second: the left column runs faster than the right (parallax).
    static let speeds = [44.0, 30.0]
    /// (x, y, width, height) per row, placed by hand: 100–150 pt wide (25–38 % of the screen), staggered,
    /// some bleeding past the screen edge, with 114–138 pt of black between cards of a column.
    private static let left: [(Double, Double, Double, Double)] = [(-26, 40, 150, 196), (30, 360, 118, 150), (-14, 640, 144, 188), (46, 960, 100, 132), (-30, 1210, 150, 196), (24, 1530, 124, 162)]
    private static let right: [(Double, Double, Double, Double)] = [(262, 110, 146, 186), (236, 420, 140, 182), (290, 740, 118, 150), (226, 1010, 150, 196), (280, 1330, 104, 136), (244, 1580, 146, 190)]

    static let all: [GlowSearchSplashCard] = [left, right].enumerated().flatMap { column, rows in
        rows.enumerated().map { row, item in
            GlowSearchSplashCard(column: column, row: row, x: item.0, width: item.2, height: item.3, baseY: item.1)
        }
    }

    /// Which of a category's ten artworks this card shows: ten cards in a row from top to bottom never repeat one.
    var artIndex: Int { (row * 2 + column) % 10 }
}

/// A code-drawn artwork: `kind` picks the drawing, `colors` its palette (0xRRGGBB).
struct GlowSearchSplashArtSpec {
    let kind: Kind
    let colors: [UInt32]
    var words: [String] = []
    var caption = ""
    var seed = 0.0

    enum Kind { case orb, blob, flower, poster, landscape, arches, checker, pills, ring }

    /// Four categories of ten artworks, in the order the placeholder names them:
    /// anything, illustration, typography, 3D shapes.
    static let categories: [[GlowSearchSplashArtSpec]] = [
        [
            .init(kind: .blob, colors: [0x1E3BFF, 0x6F8DFF, 0xFFD1EE, 0xFF5FC8, 0xB01682], seed: 0.7),
            .init(kind: .flower, colors: [0xF3EFEA, 0xFF8AD0, 0xFF2EA6, 0xB0005E, 0xFFF3A0, 0xFFD23F, 0xE09400], seed: 0.2),
            .init(kind: .landscape, colors: [0xFF4FD8, 0xFFC86B, 0xC8F56A, 0xFFF7D6, 0x7BD88F, 0x3FAE6A, 0x1F7A4D], seed: 0.30),
            .init(kind: .poster, colors: [0xFF4A1C, 0xFFE9D6], words: ["Yes."], caption: "Edition 01"),
            .init(kind: .orb, colors: [0x7CF0C8, 0x22A893, 0xFFE2B8, 0xFF8A3D, 0xB33F00], seed: 0.8),
            .init(kind: .arches, colors: [0xFFE3C2, 0xFF4A1C, 0xFF8A3D, 0xFFC23D, 0x3DB2FF, 0x2346FF]),
            .init(kind: .pills, colors: [0x8E7BFF, 0x4B2AE8, 0xFFF6B0, 0xFFD23F, 0xD69A00, 0xFFC4E6, 0xFF5FC8, 0xB01682, 0xFFFFFF, 0xE6E6F0, 0x9A9AB8]),
            .init(kind: .checker, colors: [0xFF8A3D, 0xFFB067, 0xFFFBD6, 0xFFE14D]),
            .init(kind: .ring, colors: [0x2346FF, 0x111A66, 0xFFF3C4, 0xFFC23D, 0xC27A00]),
            .init(kind: .landscape, colors: [0x1BC7FF, 0xFFB6E1, 0xFFE9A8, 0xFFFFFF, 0xFF8FB8, 0xE0457B, 0x8E1F4F], seed: 0.35),
        ],
        [
            .init(kind: .landscape, colors: [0x3A1C71, 0xD76D77, 0xFFAF7B, 0xFFE8C8, 0x9B4F96, 0x5E2D79, 0x2E1450], seed: 0.45),
            .init(kind: .flower, colors: [0x3B5BFF, 0xFFFFFF, 0xE8ECFF, 0xA9B6FF, 0xFFD9A0, 0xFF8A3D, 0xC2410C], seed: 0.5),
            .init(kind: .arches, colors: [0x2B2D6E, 0xFFB3C7, 0xFFD6A5, 0xFDFFB6, 0xCAFFBF, 0x9BF6FF]),
            .init(kind: .landscape, colors: [0x5CCBFF, 0xB7F0FF, 0xFFF4C2, 0xFFFFFF, 0x7EE08A, 0x2FB36B, 0x147A4A], seed: 0.25),
            .init(kind: .flower, colors: [0xFFD6E8, 0xFFC27A, 0xFF7A00, 0xC24A00, 0xC6A8FF, 0x6B2BFF, 0x3A0CA3], seed: 0.0),
            .init(kind: .checker, colors: [0x1BB55C, 0x139447, 0xFFC4F0, 0xFF4FD8]),
            .init(kind: .landscape, colors: [0x0B1340, 0x24308A, 0x5E4BC9, 0xFFF6C8, 0x2A2F7A, 0x1A1D52, 0x0D0F33], seed: 0.22),
            .init(kind: .arches, colors: [0xFF5B3A, 0xFFE14D, 0xFFFFFF, 0x2346FF, 0x7C3AED, 0xFFB3C7]),
            .init(kind: .flower, colors: [0x1E1B3A, 0xFFE38A, 0xFFC23D, 0xC27A00, 0xFFB3C7, 0xFF4F8B, 0xA3164A], seed: 0.3),
            .init(kind: .checker, colors: [0x2346FF, 0x1B37CC, 0xFFFBD6, 0xFFC23D]),
        ],
        [
            .init(kind: .poster, colors: [0xFF2D2D, 0xFFF1E0], words: ["BOLD"], caption: "Weight 900"),
            .init(kind: .poster, colors: [0x22C55E, 0x0B3D1E], words: ["Aa"], caption: "Specimen"),
            .init(kind: .poster, colors: [0x2346FF, 0xFFE14D], words: ["GO!"], caption: "Vol. 03"),
            .init(kind: .poster, colors: [0xFF8A00, 0x1A1A1A], words: ["47"], caption: "No. 47"),
            .init(kind: .poster, colors: [0xF5F0E6, 0xE63B2E], words: ["Big", "Idea"], caption: "Display"),
            .init(kind: .poster, colors: [0xFF5FC8, 0x2A0A3C], words: ["ZINE"], caption: "Issue 12"),
            .init(kind: .poster, colors: [0xFFE14D, 0x1A1A1A], words: ["Type", "Set"], caption: "Grotesk"),
            .init(kind: .poster, colors: [0x7C3AED, 0xF5F0E6], words: ["Néo"], caption: "Studio"),
            .init(kind: .poster, colors: [0x00C2A8, 0x062A26], words: ["Hello"], caption: "Sans Serif"),
            .init(kind: .poster, colors: [0xFFB3C7, 0xB0005E], words: ["Wow"], caption: "Headline"),
        ],
        [
            .init(kind: .orb, colors: [0xFF7AC6, 0xFFB8DD, 0xC9E4FF, 0x3D7BFF, 0x1238A8], seed: 0.2),
            .init(kind: .ring, colors: [0x1C1C3A, 0x3A2B8F, 0xFFF0C2, 0xFFB547, 0xB8620B]),
            .init(kind: .blob, colors: [0xF2F2F5, 0xD5D5E0, 0xF3C8FF, 0xB15CFF, 0x5B1AA8], seed: 2.1),
            .init(kind: .pills, colors: [0x22D3EE, 0x0284C7, 0xFFFFFF, 0xF0F0F5, 0xA0A0B8, 0xFFE1A8, 0xFF8A3D, 0xB33F00, 0xC4F5D0, 0x34D399, 0x047857]),
            .init(kind: .ring, colors: [0xFFD84D, 0xFF9F1C, 0xFFD1EE, 0xFF4FB3, 0xA3006B]),
            .init(kind: .orb, colors: [0x4B1FD1, 0x7B5CFF, 0xF4FFC2, 0xB8F02A, 0x5A8A00], seed: 0.9),
            .init(kind: .blob, colors: [0x0F172A, 0x1E293B, 0xC2FFF2, 0x2EE6C5, 0x0B7A6A], seed: 4.0),
            .init(kind: .pills, colors: [0xFF5B3A, 0xFF9575, 0xFFF6B0, 0xFFD23F, 0xD69A00, 0xC9E4FF, 0x3D7BFF, 0x1238A8, 0xFFFFFF, 0xEDEDF5, 0x9A9AB8]),
            .init(kind: .orb, colors: [0xFFE14D, 0xFFB800, 0xFFD1EE, 0xFF4FB3, 0xA3006B], seed: 0.7),
            .init(kind: .blob, colors: [0xFF6A3D, 0xFF9E7A, 0xFFF6B0, 0xFFD23F, 0xC28E00], seed: 5.3),
        ],
    ]
}

/// The card images, drawn once per app run off the main thread: a sharp image per card and category,
/// plus a tiny copy of the first category's, which drawn large reads as a blur while the cards bloom in
/// (square-cornered and a whole number of pixels, so the scene clips it to the card's rounded rectangle:
/// a tiny copy with rounded corners stretched a corner pixel into a dark square). They are 8-bit sRGB bitmaps, the format the renderer draws without converting them.
final class GlowSearchSplashArt: @unchecked Sendable {
    let sharp: [[Image]]
    let soft: [Image]

    private init(sharp: [[Image]], soft: [Image]) { self.sharp = sharp; self.soft = soft }

    private static let lock = NSLock()
    private static var made: [CGFloat: Task<GlowSearchSplashArt, Never>] = [:]

    /// The images for a pixel scale, made on the first call and kept for the rest of the run.
    static func shared(scale: CGFloat) async -> GlowSearchSplashArt {
        let task = lock.withLock {
            let task = made[scale] ?? Task.detached(priority: .userInitiated) { make(scale: scale) }
            made[scale] = task
            return task
        }
        return await task.value
    }

    private static func make(scale: CGFloat) -> GlowSearchSplashArt {
        do {
            let cards = GlowSearchSplashCard.all
            let sharp = GlowSearchSplashArtSpec.categories.map { specs in
                cards.map { Image(uiImage: render(specs[$0.artIndex], card: $0, scale: scale)) }
            }
            let soft = cards.map { Image(uiImage: renderSoft(GlowSearchSplashArtSpec.categories[0][$0.artIndex], card: $0)) }
            return GlowSearchSplashArt(sharp: sharp, soft: soft)
        }
    }

    /// The artwork at about 9 % of its size, filling every pixel.
    private static func renderSoft(_ spec: GlowSearchSplashArtSpec, card: GlowSearchSplashCard) -> UIImage {
        let pixels = CGSize(width: (card.width * 0.09).rounded(), height: (card.height * 0.09).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: pixels, format: format).image { context in
            let g = context.cgContext
            g.scaleBy(x: pixels.width / card.width, y: pixels.height / card.height)
            GlowSearchSplashPainter(g: g, w: card.width, h: card.height).paint(spec)
        }
    }

    private static func render(_ spec: GlowSearchSplashArtSpec, card: GlowSearchSplashCard, scale: CGFloat) -> UIImage {
        let size = CGSize(width: card.width, height: card.height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let g = context.cgContext
            let bounds = CGRect(origin: .zero, size: size)
            UIBezierPath(roundedRect: bounds, cornerRadius: GlowSearchSplashCard.radius).addClip()
            GlowSearchSplashPainter(g: g, w: size.width, h: size.height).paint(spec)
            g.setStrokeColor(UIColor.white.withAlphaComponent(0.08).cgColor)
            g.setLineWidth(1)
            g.addPath(UIBezierPath(roundedRect: bounds.insetBy(dx: 0.5, dy: 0.5), cornerRadius: GlowSearchSplashCard.radius - 0.5).cgPath)
            g.strokePath()
        }
    }
}

/// Core Graphics drawing of the artworks, in card coordinates (0…w, 0…h, y down).
struct GlowSearchSplashPainter {
    let g: CGContext
    let w: CGFloat
    let h: CGFloat
    private var m: CGFloat { min(w, h) }

    static func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    /// The colour with its channels multiplied by `factor` (a darker shade).
    static func shade(_ hex: UInt32, _ factor: Double) -> UInt32 {
        let r = UInt32(Double((hex >> 16) & 255) * factor), gr = UInt32(Double((hex >> 8) & 255) * factor), b = UInt32(Double(hex & 255) * factor)
        return (r << 16) | (gr << 8) | b
    }

    private func gradient(_ colors: [UIColor], _ stops: [CGFloat]?) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: stops)!
    }
    private func linear(_ path: CGPath, _ colors: [UIColor], _ stops: [CGFloat]?, _ start: CGPoint, _ end: CGPoint, evenOdd: Bool = false) {
        g.saveGState(); g.addPath(path); if evenOdd { g.clip(using: .evenOdd) } else { g.clip() }
        g.drawLinearGradient(gradient(colors, stops), start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        g.restoreGState()
    }
    private func radial(_ path: CGPath, _ colors: [UIColor], _ stops: [CGFloat]?, _ center: CGPoint, _ radius: CGFloat) {
        g.saveGState(); g.addPath(path); g.clip()
        g.drawRadialGradient(gradient(colors, stops), startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [.drawsAfterEndLocation])
        g.restoreGState()
    }
    private func fill(_ path: CGPath, _ color: UIColor) { g.addPath(path); g.setFillColor(color.cgColor); g.fillPath() }
    private func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGPath { CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r), transform: nil) }
    private func oval(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> CGPath { CGPath(ellipseIn: CGRect(x: x - rx, y: y - ry, width: 2 * rx, height: 2 * ry), transform: nil) }
    private var all: CGPath { CGPath(rect: CGRect(x: 0, y: 0, width: w, height: h), transform: nil) }
    private func c(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor { Self.color(hex, alpha) }
    private func shadow(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ alpha: CGFloat) {
        radial(oval(x, y, rx, ry), [UIColor.black.withAlphaComponent(alpha), UIColor.black.withAlphaComponent(0)], nil, CGPoint(x: x, y: y), rx)
    }
    /// A glossy sphere lit from the upper left.
    private func sphere(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ hi: UInt32, _ base: UInt32, _ deep: UInt32) {
        radial(circle(x, y, r), [c(hi), c(base), c(deep)], [0, 0.5, 1], CGPoint(x: x - 0.35 * r, y: y - 0.4 * r), 1.45 * r)
        radial(circle(x - 0.36 * r, y - 0.42 * r, 0.26 * r), [UIColor.white.withAlphaComponent(0.85), UIColor.white.withAlphaComponent(0)], nil, CGPoint(x: x - 0.36 * r, y: y - 0.42 * r), 0.26 * r)
    }

    func paint(_ spec: GlowSearchSplashArtSpec) {
        let k = spec.colors
        switch spec.kind {
        case .orb:
            linear(all, [c(k[0]), c(k[1])], nil, .zero, CGPoint(x: 0, y: h))
            let r = m * 0.32, x = w * 0.5, y = h * 0.5
            shadow(x, y + r * 1.04, r * 1.05, r * 0.18, 0.38)
            sphere(x, y, r, k[2], k[3], k[4])
            if spec.seed > 0.5 { sphere(w * 0.78, h * 0.2, m * 0.12, k[2], k[3], k[4]) }
        case .blob:
            linear(all, [c(k[0]), c(k[1])], nil, .zero, CGPoint(x: w, y: h))
            let r = m * 0.36, x = w * 0.5, y = h * 0.5
            let path = CGMutablePath()
            for i in 0..<120 {
                let a = Double(i) / 120 * 2 * .pi
                let rr = r * (1 + 0.13 * sin(3 * a + spec.seed) + 0.07 * sin(5 * a + 2 * spec.seed) + 0.04 * cos(2 * a))
                let p = CGPoint(x: x + rr * cos(a), y: y + rr * sin(a))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.closeSubpath()
            shadow(x, y + r * 1.12, r * 0.9, r * 0.14, 0.3)
            radial(path, [c(k[2]), c(k[3]), c(k[4])], [0, 0.48, 1], CGPoint(x: x - 0.3 * r, y: y - 0.38 * r), 1.5 * r)
            radial(circle(x - 0.34 * r, y - 0.4 * r, 0.3 * r), [UIColor.white.withAlphaComponent(0.7), UIColor.white.withAlphaComponent(0)], nil, CGPoint(x: x - 0.34 * r, y: y - 0.4 * r), 0.3 * r)
        case .flower:
            fill(all, c(k[0]))
            let x = w * 0.5, y = h * 0.5, l = m * 0.34
            radial(circle(x, y, m * 0.6), [UIColor.white.withAlphaComponent(0.35), UIColor.white.withAlphaComponent(0)], nil, CGPoint(x: x, y: y), m * 0.6)
            radial(circle(x + l * 0.12, y + l * 0.18, l * 1.15), [UIColor.black.withAlphaComponent(0.22), UIColor.black.withAlphaComponent(0)], nil, CGPoint(x: x + l * 0.12, y: y + l * 0.18), l * 1.15)
            for i in 0..<6 {
                g.saveGState()
                g.translateBy(x: x, y: y)
                g.rotate(by: (spec.seed * 60 + Double(i) * 60) * .pi / 180)
                let petal = CGPath(ellipseIn: CGRect(x: -0.31 * l, y: -1.02 * l, width: 0.62 * l, height: 0.84 * l), transform: nil)
                radial(petal, [c(k[3]), c(k[2]), c(k[1])], [0, 0.45, 1], CGPoint(x: 0, y: -0.12 * l), 1.0 * l)
                g.restoreGState()
            }
            let r = 0.3 * l
            radial(circle(x, y, r), [c(k[4]), c(k[5]), c(k[6])], [0, 0.5, 1], CGPoint(x: x - 0.1 * l, y: y - 0.12 * l), 0.42 * l)
            radial(circle(x - 0.32 * r, y - 0.36 * r, 0.3 * r), [UIColor.white.withAlphaComponent(0.7), UIColor.white.withAlphaComponent(0)], nil, CGPoint(x: x - 0.32 * r, y: y - 0.36 * r), 0.3 * r)
        case .poster:
            fill(all, c(k[0]))
            fill(circle(w * 0.86, h * 0.07 + 6, 5), c(k[1]))
            let caption = UIFont.systemFont(ofSize: 10, weight: .semibold)
            NSAttributedString(string: spec.caption, attributes: [.font: caption, .foregroundColor: c(k[1], 0.85)])
                .draw(at: CGPoint(x: w * 0.07, y: h * 0.07 + 10 - caption.ascender))
            fill(CGPath(rect: CGRect(x: w * 0.07, y: h * 0.07 + 17, width: w * 0.86, height: 1), transform: nil), c(k[1], 0.5))
            let probe = UIFont.systemFont(ofSize: 100, weight: .black)
            let widest = spec.words.map { NSAttributedString(string: $0, attributes: [.font: probe]).size().width }.max() ?? 1
            let n = CGFloat(spec.words.count)
            let size = min(100 * w * 0.86 / widest, h * 0.62 / (n * 0.82))
            let font = UIFont.systemFont(ofSize: size, weight: .black)
            let last = h - w * 0.08
            for (i, word) in spec.words.enumerated() {
                let baseline = last - (n - 1 - CGFloat(i)) * size * 0.84
                NSAttributedString(string: word, attributes: [.font: font, .foregroundColor: c(k[1])])
                    .draw(at: CGPoint(x: w * 0.07, y: baseline - font.ascender))
            }
        case .landscape:
            linear(all, [c(k[0]), c(k[1]), c(k[2])], nil, .zero, CGPoint(x: 0, y: h))
            let sx = w * 0.64, sy = h * spec.seed
            radial(circle(sx, sy, w * 0.42), [c(k[3], 0.55), c(k[3], 0)], nil, CGPoint(x: sx, y: sy), w * 0.42)
            fill(circle(sx, sy, w * 0.13), c(k[3]))
            for layer in 0..<3 {
                let top = h * (0.56 + 0.12 * Double(layer)), amp = h * 0.035 * (1 - 0.2 * Double(layer))
                let freq = 1.2 + 0.5 * Double(layer), phase = Double(layer) * 1.7 + spec.seed * 3
                let path = CGMutablePath()
                path.move(to: CGPoint(x: 0, y: h))
                for j in 0...40 {
                    let u = Double(j) / 40
                    path.addLine(to: CGPoint(x: w * u, y: top + amp * sin(u * 2 * .pi * freq + phase)))
                }
                path.addLine(to: CGPoint(x: w, y: h)); path.closeSubpath()
                let hill = k[4 + layer]
                linear(path, [c(hill), c(Self.shade(hill, 0.7))], nil, CGPoint(x: 0, y: top - amp), CGPoint(x: 0, y: h))
            }
        case .arches:
            fill(all, c(k[0]))
            fill(circle(w * 0.24, h * 0.2, m * 0.08), c(k[1]))
            let r0 = w * 0.62
            for i in 0..<5 { fill(circle(w * 0.5, h * 0.97, r0 * (1 - Double(i) * 0.17)), c(k[1 + i])) }
            fill(circle(w * 0.5, h * 0.97, r0 * 0.15), c(k[0]))
        case .checker:
            fill(all, c(k[0]))
            let s = w / 6
            for row in 0..<Int(ceil(h / s)) { for col in 0..<6 where (row + col) % 2 == 0 {
                fill(CGPath(rect: CGRect(x: Double(col) * s, y: Double(row) * s, width: s, height: s), transform: nil), c(k[1]))
            } }
            let x = w * 0.5, y = h * 0.46, r = w * 0.3
            radial(circle(x, y, r * 1.6), [c(k[3], 0.5), c(k[3], 0)], nil, CGPoint(x: x, y: y), r * 1.6)
            radial(circle(x, y, r), [c(k[2]), c(k[3])], nil, CGPoint(x: x - 0.3 * r, y: y - 0.35 * r), 1.4 * r)
        case .pills:
            linear(all, [c(k[0]), c(k[1])], nil, .zero, CGPoint(x: 0, y: h))
            let l = w * 0.64, t = w * 0.22
            for (i, spot) in [(0.40, 0.28), (0.60, 0.52), (0.40, 0.76)].enumerated() {
                g.saveGState()
                g.translateBy(x: w * spot.0, y: h * spot.1)
                g.rotate(by: -35 * .pi / 180)
                fill(CGPath(roundedRect: CGRect(x: -l / 2 + t * 0.12, y: -t / 2 + t * 0.28, width: l, height: t), cornerWidth: t / 2, cornerHeight: t / 2, transform: nil), UIColor.black.withAlphaComponent(0.2))
                let body = CGPath(roundedRect: CGRect(x: -l / 2, y: -t / 2, width: l, height: t), cornerWidth: t / 2, cornerHeight: t / 2, transform: nil)
                linear(body, [c(k[2 + i * 3]), c(k[3 + i * 3]), c(k[4 + i * 3])], [0, 0.45, 1], CGPoint(x: 0, y: -t / 2), CGPoint(x: 0, y: t / 2))
                fill(CGPath(roundedRect: CGRect(x: -l / 2 + t * 0.4, y: -t * 0.33, width: l - t * 0.8, height: t * 0.13), cornerWidth: t * 0.065, cornerHeight: t * 0.065, transform: nil), UIColor.white.withAlphaComponent(0.6))
                g.restoreGState()
            }
        case .ring:
            linear(all, [c(k[0]), c(k[1])], nil, .zero, CGPoint(x: 0, y: h))
            let x = w * 0.5, y = h * 0.5, ro = m * 0.36, ri = m * 0.17
            shadow(x, y + ro * 1.02, ro * 0.95, ro * 0.16, 0.3)
            let ring = CGMutablePath(); ring.addPath(circle(x, y, ro)); ring.addPath(circle(x, y, ri))
            linear(ring, [c(k[2]), c(k[3]), c(k[4])], [0, 0.5, 1], CGPoint(x: x - ro, y: y - ro), CGPoint(x: x + ro, y: y + ro), evenOdd: true)
            g.addPath(circle(x, y, ri + (ro - ri) * 0.12)); g.setStrokeColor(c(k[4], 0.35).cgColor); g.setLineWidth((ro - ri) * 0.25); g.strokePath()
            let arc = CGMutablePath(), mid = (ro + ri) / 2
            for j in 0...20 {
                let a = (200 + 55 * Double(j) / 20) * .pi / 180
                let p = CGPoint(x: x + mid * cos(a), y: y + mid * sin(a))
                if j == 0 { arc.move(to: p) } else { arc.addLine(to: p) }
            }
            g.addPath(arc); g.setStrokeColor(UIColor.white.withAlphaComponent(0.65).cgColor); g.setLineWidth((ro - ri) * 0.2); g.setLineCap(.round); g.strokePath()
        }
    }
}
