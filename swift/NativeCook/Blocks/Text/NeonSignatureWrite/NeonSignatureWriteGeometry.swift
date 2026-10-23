import CoreText
import SwiftUI

/// One word of the sign: its text and the colours its tube burns in once lit.
struct NeonSignatureWriteWord: Equatable {
    var text: String
    /// The fill runs from `start` (left) to `end` (right).
    var start: Color
    var end: Color
    /// The halo around the letters and the light it throws on the wall.
    var glow: Color

    static let samples = [
        NeonSignatureWriteWord(text: "Afterglow", start: Color(hex: 0xffc35c), end: Color(hex: 0xff4a2b), glow: Color(hex: 0xff6a2c)),
        NeonSignatureWriteWord(text: "Midnight", start: Color(hex: 0x3a6dff), end: Color(hex: 0x40d2ff), glow: Color(hex: 0x2f7bff)),
        NeonSignatureWriteWord(text: "Daydream", start: Color(hex: 0xff5ce0), end: Color(hex: 0xff3c6c), glow: Color(hex: 0xff3bb4)),
    ]
}

/// The outlines of the sign's words, laid out once for a screen size: every contour of every glyph
/// (the tubes trace these), the filled word, and the sparks thrown from the pen heads.
final class NeonSignatureWriteGeometry {
    struct Contour {
        let path: Path
        /// Where along the word the contour starts, 0 (left) to 1 (right): later contours start later.
        let delay: Double
        let sparks: [Spark]
    }

    struct Spark {
        /// The tube (0 mint, 1 rose) whose head throws it, and how far round the contour that head is then.
        let layer: Int
        let at: Double
        let origin: CGPoint
        let velocity: CGVector
        let life: Double
        let length: Double
        let width: Double
        let white: Bool
    }

    struct Word {
        let fill: Path
        let contours: [Contour]
        /// The word's box before it is tilted, and the tilt's centre.
        let box: CGRect
        let transform: CGAffineTransform
    }

    private(set) var words: [Word] = []
    private var key: (CGSize, [String]) = (.zero, [])

    static let fontName = "Hurricane-Regular"
    /// The words are laid out at this size, then all scaled by the same factor to fit the screen.
    private static let unit: CGFloat = 100
    /// The sign leans up to the right.
    static let tilt = -8.0 * .pi / 180

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "NeonSignatureWrite-Hurricane-Regular", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    func words(for texts: [String], in size: CGSize) -> [Word] {
        if key.0 == size, key.1 == texts { return words }
        _ = Self.registered
        let font = CTFontCreateWithName(Self.fontName as CFString, Self.unit, nil)
        let outlines = texts.map { Self.outline($0, font: font) }

        // One scale for every word, so the widest one (tilted) fits between 28 pt margins.
        let angle = abs(Self.tilt)
        let room = size.width - 56
        let widest = outlines.map { path -> CGFloat in
            let b = path.boundingBoxOfPath
            return b.width * cos(angle) + b.height * sin(angle)
        }.max() ?? 1
        let scale = min(room / widest, 1.9)
        let centre = CGPoint(x: size.width / 2, y: size.height * 0.45)

        words = outlines.enumerated().map { index, outline in
            let b = outline.boundingBoxOfPath
            let transform = CGAffineTransform(translationX: centre.x, y: centre.y)
                .rotated(by: Self.tilt)
                .scaledBy(x: scale, y: scale)
                .translatedBy(x: -b.midX, y: -b.midY)
            let fill = Path(outline).applying(transform)
            let contours = Self.contours(of: outline).enumerated().map { c, raw -> Contour in
                let path = Path(raw).applying(transform)
                let delay = Double((raw.boundingBoxOfPath.minX - b.minX) / max(b.width, 1))
                return Contour(path: path, delay: delay, sparks: Self.sparks(on: path, seed: index * 997 + c * 31))
            }
            let box = CGRect(x: centre.x - b.width * scale / 2, y: centre.y - b.height * scale / 2, width: b.width * scale, height: b.height * scale)
            let tiltOnly = CGAffineTransform(translationX: centre.x, y: centre.y).rotated(by: Self.tilt).translatedBy(x: -centre.x, y: -centre.y)
            return Word(fill: fill, contours: contours, box: box, transform: tiltOnly)
        }
        key = (size, texts)
        return words
    }

    /// The glyphs of `text` placed one after another by their advances (no shaping, so every stack
    /// lays the word out alike), baseline at y = 0, y pointing down.
    private static func outline(_ text: String, font: CTFont) -> CGPath {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        let path = CGMutablePath()
        var x: CGFloat = 0
        for (glyph, advance) in zip(glyphs, advances) {
            if let shape = CTFontCreatePathForGlyph(font, glyph, nil) {
                path.addPath(shape, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += advance.width
        }
        return path
    }

    /// The path split at every move: one closed outline per piece.
    private static func contours(of path: CGPath) -> [CGPath] {
        var out: [CGPath] = []
        var current: CGMutablePath?
        path.applyWithBlock { pointer in
            let e = pointer.pointee
            switch e.type {
            case .moveToPoint:
                if let c = current, !c.isEmpty { out.append(c) }
                current = CGMutablePath()
                current?.move(to: e.points[0])
            case .addLineToPoint: current?.addLine(to: e.points[0])
            case .addQuadCurveToPoint: current?.addQuadCurve(to: e.points[1], control: e.points[0])
            case .addCurveToPoint: current?.addCurve(to: e.points[2], control1: e.points[0], control2: e.points[1])
            case .closeSubpath: current?.closeSubpath()
            @unknown default: break
            }
        }
        if let c = current, !c.isEmpty { out.append(c) }
        return out.filter { $0.boundingBoxOfPath.width + $0.boundingBoxOfPath.height > 0.5 }
    }

    /// Three sparks per tube head on each contour, from a seeded hash so every loop throws the same ones.
    private static func sparks(on path: Path, seed: Int) -> [Spark] {
        (0..<6).map { k in
            let r = { (salt: Int) in NeonSignatureWriteGeometry.random(seed * 13 + k * 7 + salt) }
            let at = (Double(k / 2) + 0.15 + 0.7 * r(1)) / 3
            let origin = path.trimmedPath(from: 0, to: at).currentPoint ?? .zero
            let angle = r(2) * 2 * .pi
            let speed = 26 + 90 * r(3)
            return Spark(
                layer: k % 2, at: at, origin: origin,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed - 30),
                life: 0.32 + 0.3 * r(4), length: 2 + 6 * r(5), width: 1.4 + 1.2 * r(6), white: r(8) < 0.6
            )
        }
    }

    /// A 32-bit integer hash mapped to 0..<1; the same in every stack.
    static func random(_ n: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: n)
        x ^= x >> 16
        x = x &* 0x7feb_352d
        x ^= x >> 15
        x = x &* 0x846c_a68b
        x ^= x >> 16
        return Double(x) / 4_294_967_296
    }
}
