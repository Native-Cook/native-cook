import CoreText
import SwiftUI
import UIKit

/// The scripted scroll and the reading front as pure functions (ported from the React Native `motion.ts`).
enum ScrollReadHighlightMotion {
    static let loop: Double = 10
    static let resumeAfter: Double = 2
    static let returnSeconds: Double = 1.1

    /// Three flicks, each to a share of the article's scroll range.
    static let flicks: [(start: Double, end: Double, to: Double)] = [(1.0, 2.6, 0.2), (2.7, 4.3, 0.42), (4.4, 6.2, 0.62)]
    static let scriptReturn = (7.0, 8.6)

    /// A word takes this many words of travel to ink in: the soft leading edge.
    static let fadeWords = 4.0
    static let wordsPerMinute = 140.0

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func easeOutCubic(_ k: Double) -> Double { 1 - pow(1 - k, 3) }
    static func easeInOutCubic(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }

    /// Share of the scroll range (0..1) at loop time t.
    static func scriptAt(_ t: Double) -> Double {
        var at = 0.0
        for f in flicks { at = mix(at, f.to, easeOutCubic(seg(t, f.start, f.end))) }
        return at * (1 - easeInOutCubic(seg(t, scriptReturn.0, scriptReturn.1)))
    }

    static func ghostOpacityAt(_ t: Double) -> Double {
        var opacity = 0.0
        for f in flicks {
            opacity = max(opacity, seg(t, f.start - 0.2, f.start - 0.04) * (1 - seg(t, f.start + 0.34, f.start + 0.5)))
        }
        return opacity
    }

    /// The ghost finger's travel, 0 (low on the screen) to 1 (high), during each flick.
    static func ghostTravelAt(_ t: Double) -> Double {
        var travel = 0.0
        for f in flicks where t >= f.start - 0.2 && t <= f.start + 0.5 { travel = easeOutCubic(seg(t, f.start, f.start + 0.4)) }
        return travel
    }

    /// Index of the last line whose top is at or above y, or -1.
    static func lineAt(_ y: Double, _ tops: [Double]) -> Int {
        var low = 0, high = tops.count - 1, found = -1
        while low <= high {
            let middle = (low + high) >> 1
            if tops[middle] <= y {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    /// The reading front in words (fractional), from the article's y of the reading line.
    static func frontAt(_ y: Double, _ page: ScrollReadHighlightPage) -> Double {
        let line = lineAt(y, page.lineTop)
        if line < 0 { return 0 }
        let along = min(max((y - page.lineTop[line]) / page.lineHeight[line], 0), 1)
        return Double(page.lineFirst[line]) + along * Double(page.lineCount[line])
    }

    static func wordInk(_ front: Double, _ word: Int) -> Double {
        let k = min(max((front - Double(word)) / fadeWords, 0), 1)
        return k * k * (3 - 2 * k)
    }
}

/// One paragraph laid out once: glyph outlines (y down, top of the first line at 0) and a rect per word.
struct ScrollReadHighlightText {
    var path = Path()
    var height = 0.0
    /// Per line: top and height, in paragraph coordinates.
    var lineTops: [Double] = []
    /// Per word (`\S+`): its first rect, the way Skia's `getRectsForRange(...)[0]` reports it, and its line.
    var wordRects: [CGRect] = []
    var wordLine: [Int] = []

    /// The system font as the React Native build draws it in Skia: the 12 pt face scaled to `size`, its optical size
    /// held at 12, so advances and wraps match. `as CTFont` keeps the system face.
    static func font(_ size: Double, _ weight: UIFont.Weight) -> CTFont {
        let base = UIFont.systemFont(ofSize: 12, weight: weight) as CTFont
        let optical = CTFontDescriptorCreateWithAttributes([kCTFontOpticalSizeAttribute: 12] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, optical)
    }

    /// Greedy wrap as Skia's paragraph does it: a line may break after spaces and after a hyphen; trailing spaces do
    /// not count against the width; letter spacing is added to every glyph. Lines are `leading` tall with the glyphs
    /// centred in them (half leading).
    init(_ text: String, width: Double, size: Double, weight: UIFont.Weight, leading: Double, spacing: Double) {
        let font = Self.font(size, weight)
        let chars = Array(text.utf16)
        let n = chars.count
        guard n > 0 else { return }
        var glyphs = [CGGlyph](repeating: 0, count: n)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, n)
        var adv = [CGSize](repeating: .zero, count: n)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, n)
        let advance = adv.map { Double($0.width) + spacing }
        let isSpace = { (c: UInt16) in c == 32 }

        // Segments end after a run of spaces, or after a hyphen followed by a letter.
        var x = [Double](repeating: 0, count: n)
        var line = [Int](repeating: 0, count: n)
        var row = 0
        var pen = 0.0
        var i = 0
        while i < n {
            var end = i
            while end < n && !isSpace(chars[end]) {
                end += 1
                if chars[end - 1] == 45 && end < n && !isSpace(chars[end]) { break }
            }
            var visible = 0.0
            for k in i..<end { visible += advance[k] }
            if pen > 0 && pen + visible > width + 0.001 {
                row += 1
                pen = 0
            }
            var stop = end
            while stop < n && isSpace(chars[stop]) { stop += 1 }
            for k in i..<stop {
                x[k] = pen
                line[k] = row
                pen += advance[k]
            }
            i = stop
        }

        let ascent = Double(CTFontGetAscent(font)), descent = Double(CTFontGetDescent(font))
        let baseline = (leading - (ascent + descent)) / 2 + ascent
        let cg = CGMutablePath()
        for k in 0..<n where !isSpace(chars[k]) {
            var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x[k], ty: Double(line[k]) * leading + baseline)
            if let g = CTFontCreatePathForGlyph(font, glyphs[k], &t) { cg.addPath(g) }
        }
        path = Path(cg)
        height = Double(row + 1) * leading
        lineTops = (0...row).map { Double($0) * leading }

        var k = 0
        while k < n {
            if isSpace(chars[k]) { k += 1; continue }
            var end = k
            while end < n && !isSpace(chars[end]) { end += 1 }
            var last = k
            while last + 1 < end && line[last + 1] == line[k] { last += 1 }
            wordRects.append(CGRect(x: x[k], y: Double(line[k]) * leading, width: x[last] + advance[last] - x[k], height: leading))
            wordLine.append(line[k])
            k = end
        }
    }
}

/// The article laid out once: the head (title, byline, drawn cover), each paragraph's outline and a table of
/// every word and line, in article coordinates.
struct ScrollReadHighlightPage {
    struct Block {
        let y: Double
        let height: Double
        let first: Int
        let last: Int
        let path: Path
    }

    let x: Double
    let width: Double
    let title: ScrollReadHighlightText
    let byline: ScrollReadHighlightText
    let titleY: Double
    let bylineY: Double
    let coverY: Double
    let coverHeight: Double
    let headHeight: Double
    let blocks: [Block]
    let height: Double
    let wordCount: Int
    let firstBodyTop: Double
    let lastBottom: Double
    var wordX: [Double] = [], wordY: [Double] = [], wordW: [Double] = [], wordH: [Double] = []
    var lineTop: [Double] = [], lineHeight: [Double] = []
    var lineFirst: [Int] = [], lineCount: [Int] = []

    init(title titleText: String, byline bylineText: String, paragraphs: [String], x: Double, width: Double, bodySize: Double, bodyLeading: Double) {
        self.x = x
        self.width = width
        let titleSize = bodySize >= 19 ? 34.0 : 30.0
        title = ScrollReadHighlightText(titleText, width: width, size: titleSize, weight: .bold, leading: titleSize * 1.12, spacing: -0.5)
        byline = ScrollReadHighlightText(bylineText, width: width, size: 15, weight: .medium, leading: 20, spacing: 0)
        titleY = 16
        bylineY = titleY + title.height + 8
        coverY = bylineY + 20 + 18
        coverHeight = (min(width * 0.5, 210) + 0.5).rounded(.down)
        headHeight = coverY + coverHeight + 24

        var blocks: [Block] = []
        var y = headHeight
        for text in paragraphs {
            let p = ScrollReadHighlightText(text, width: width, size: bodySize, weight: .regular, leading: bodyLeading, spacing: -0.1)
            let first = wordX.count
            var rowTop = -Double.infinity
            for (rect, _) in zip(p.wordRects, p.wordLine) {
                let top = y + rect.minY
                if abs(top - rowTop) > 2 {
                    rowTop = top
                    lineTop.append(top)
                    lineHeight.append(rect.height)
                    lineFirst.append(wordX.count)
                    lineCount.append(0)
                }
                lineCount[lineCount.count - 1] += 1
                wordX.append(x + rect.minX)
                wordY.append(top)
                wordW.append(rect.width)
                wordH.append(rect.height)
            }
            blocks.append(Block(y: y, height: p.height, first: first, last: wordX.count - 1, path: p.path))
            y += p.height + (bodyLeading * 0.62 + 0.5).rounded(.down)
        }
        self.blocks = blocks
        height = y + 40
        wordCount = wordX.count
        firstBodyTop = lineTop.first ?? headHeight
        lastBottom = lineTop.isEmpty ? y : lineTop[lineTop.count - 1] + lineHeight[lineHeight.count - 1]
    }

    // MARK: - The cover: a dusk over water, drawn

    private static func hill(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ a: Double, _ b: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x, y: y + h))
        p.addLine(to: CGPoint(x: x, y: y + h * a))
        p.addCurve(to: CGPoint(x: x + w, y: y + h * b),
                   control1: CGPoint(x: x + w * 0.28, y: y + h * (a - 0.2)),
                   control2: CGPoint(x: x + w * 0.52, y: y + h * (b + 0.16)))
        p.addLine(to: CGPoint(x: x + w, y: y + h))
        p.closeSubpath()
        return p
    }

    func drawCover(_ ctx: inout GraphicsContext) {
        let x = self.x, y = coverY, w = width, h = coverHeight
        let rect = CGRect(x: x, y: y, width: w, height: h)
        var c = ctx
        c.clip(to: Path(roundedRect: rect, cornerRadius: 18, style: .circular))
        c.fill(Path(rect), with: .linearGradient(Gradient(stops: [
            .init(color: Color(hex: 0x2B3346), location: 0),
            .init(color: Color(hex: 0xB0705A), location: 0.56),
            .init(color: Color(hex: 0xEDB27C), location: 1),
        ]), startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + h)))
        c.fill(Path(rect), with: .radialGradient(Gradient(stops: [
            .init(color: Color.rgba(255, 221, 170, 0.95), location: 0.08),
            .init(color: Color.rgba(244, 190, 130, 0), location: 1),
        ]), center: CGPoint(x: x + w * 0.66, y: y + h * 0.56), startRadius: 0, endRadius: h * 0.5))
        c.fill(Self.hill(x, y + h * 0.4, w, h * 0.3, 0.72, 0.38), with: .color(Color(hex: 0x3A3A48)))
        c.fill(Self.hill(x, y + h * 0.52, w * 0.62, h * 0.2, 0.3, 1), with: .color(Color(hex: 0x262A35)))
        c.fill(Path(CGRect(x: x, y: y + h * 0.7, width: w, height: h * 0.3)), with: .color(Color(hex: 0x20262E)))
        let glint = Color.rgba(247, 201, 150, 0.3)
        for i in 0..<6 {
            let seed = abs(sin(Double(i) * 12.9898) * 43758.5453).truncatingRemainder(dividingBy: 1)
            let spread = Double(i + 1) * 3.4
            let gw = 10 + Double(i) * 5 + seed * 10
            let r = CGRect(x: x + w * 0.66 - gw / 2 + (seed - 0.5) * spread * 2, y: y + h * 0.74 + Double(i) * h * 0.042, width: gw, height: 1.2)
            c.fill(Path(roundedRect: r, cornerRadius: 1, style: .circular), with: .color(glint))
        }
    }
}
