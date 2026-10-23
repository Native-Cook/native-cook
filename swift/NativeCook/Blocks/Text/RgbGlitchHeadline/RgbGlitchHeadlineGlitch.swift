import CoreText
import SwiftUI

/// The headline as one path, laid out for a screen width: centred lines of glyphs placed by their
/// advances plus a fixed tracking, so every stack lays it out alike.
final class RgbGlitchHeadlineType {
    struct Block {
        let path: Path
        /// Top of the first line's capitals and the last baseline.
        let top: Double
        let bottom: Double
        let capHeight: Double
        let centre: CGPoint
    }

    static let fontName = "JostRoman-Light"
    private static let unit: CGFloat = 100
    /// Letter spacing, in ems.
    static let tracking: CGFloat = 0.06
    /// Baseline to baseline, in ems.
    static let leading: CGFloat = 1.02
    static let maxSize: CGFloat = 78

    private var cached: Block?
    private var key: (CGFloat, CGFloat, [String]) = (0, 0, [])

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "RgbGlitchHeadline-Jost-Light", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    /// - Parameters:
    ///   - width: the screen's width; the widest line fits between 24 pt margins.
    ///   - centreY: where the middle of the block goes.
    func block(_ lines: [String], width: CGFloat, centreY: CGFloat) -> Block {
        if let cached, key == (width, centreY, lines) { return cached }
        _ = Self.registered
        let font = CTFontCreateWithName(Self.fontName as CFString, Self.unit, nil)
        let shaped = lines.map { Self.line($0, font: font) }
        let widest = shaped.map(\.1).max() ?? 1
        let size = min((width - 48) / widest * Self.unit, Self.maxSize)
        let scale = size / Self.unit
        let cap = CTFontGetCapHeight(font) * scale
        let step = size * Self.leading
        let height = cap + step * CGFloat(lines.count - 1)
        let top = centreY - height / 2

        var path = Path()
        for (index, (glyphs, lineWidth)) in shaped.enumerated() {
            let baseline = top + cap + step * CGFloat(index)
            let x = (width - lineWidth * scale) / 2
            path.addPath(glyphs, transform: CGAffineTransform(a: scale, b: 0, c: 0, d: scale, tx: x, ty: baseline))
        }
        let block = Block(path: path, top: top, bottom: top + height, capHeight: cap, centre: CGPoint(x: width / 2, y: centreY))
        cached = block
        key = (width, centreY, lines)
        return block
    }

    /// One line at 100 pt, baseline at y = 0 (y down), and its width.
    private static func line(_ text: String, font: CTFont) -> (Path, CGFloat) {
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
            x += advance.width + tracking * unit
        }
        return (Path(path), max(x - tracking * unit, 1))
    }
}

/// One stepped frame of interference: horizontal bands of the headline knocked sideways, a red and a
/// cyan copy split apart, sometimes a shear, a band gone, and thin noise lines across.
struct RgbGlitchHeadlineFrame {
    struct Band {
        var y0: Double
        var y1: Double
        var dx: Double
        /// The white layer is missing here; only the colour copies show.
        var hidden = false
    }

    struct Strip {
        let y0: Double
        let y1: Double
        let dx: Double
        let hidden: Bool
    }

    var bands: [Band] = []
    /// How far the red copy sits right of the white one (the cyan one as far left).
    var split = 0.0
    /// The whole block's sideways jolt.
    var jolt = 0.0
    /// Shear of the whole block (tan of the angle; positive leans the tops right).
    var shear = 0.0
    /// Noise lines: y and opacity.
    var noise: [(Double, Double)] = []

    /// The frame for one step of a burst. `amount` 0…1 scales every offset.
    static func make(seed: Int, step: Int, amount: Double, block: RgbGlitchHeadlineType.Block) -> RgbGlitchHeadlineFrame {
        let r = { (k: Int) in RgbGlitchHeadlineFrame.random(seed &* 7919 &+ step &* 131 &+ k) }
        let cap = block.capHeight
        let height = block.bottom - block.top
        var frame = RgbGlitchHeadlineFrame()
        let count = 2 + Int(r(0) * 4)
        for i in 0..<count {
            let centre = block.top + height * r(10 + i * 4)
            let h = cap * (0.06 + 0.5 * r(11 + i * 4))
            let dx = (r(12 + i * 4) < 0.5 ? -1.0 : 1.0) * cap * (0.08 + 0.42 * r(13 + i * 4)) * amount
            frame.bands.append(Band(y0: centre - h / 2, y1: centre + h / 2, dx: dx))
        }
        if r(30) < 0.3 * amount { frame.bands[0].hidden = true }
        frame.split = (r(32) < 0.5 ? -1.0 : 1.0) * cap * (0.04 + 0.1 * r(31)) * amount
        frame.shear = r(33) < 0.22 * amount ? tan(10 * Double.pi / 180) : 0
        frame.jolt = cap * 0.06 * (r(35) * 2 - 1) * amount
        let lines = Int(r(36) * 3 * amount)
        for j in 0..<lines {
            frame.noise.append((block.top - cap * 0.4 + (height + cap * 0.8) * r(40 + j), 0.15 + 0.25 * r(44 + j)))
        }
        return frame
    }

    /// The block cut into horizontal strips at every band edge; a strip takes the offset of the last
    /// band that covers it (0 outside every band).
    func strips(top: Double, bottom: Double) -> [Strip] {
        var edges = [top, bottom]
        for band in bands {
            edges.append(min(max(band.y0, top), bottom))
            edges.append(min(max(band.y1, top), bottom))
        }
        edges.sort()
        var out: [Strip] = []
        for i in 0..<(edges.count - 1) where edges[i + 1] - edges[i] > 0.01 {
            let mid = (edges[i] + edges[i + 1]) / 2
            let band = bands.last { mid >= $0.y0 && mid < $0.y1 }
            out.append(Strip(y0: edges[i], y1: edges[i + 1], dx: band?.dx ?? 0, hidden: band?.hidden ?? false))
        }
        return out
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
