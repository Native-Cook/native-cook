import CoreText
import SwiftUI
import UIKit

/// The loop as pure functions of time, the glyph pairing and the measured layout. Ported from the React Native
/// build's `motion.ts`, `match.ts` and `layout.ts`; every stack reads the same numbers.
enum GlyphReflowEditMotion {
    static let loop: Double = 9
    static let resumeAfter: Double = 2

    static let morphA = (0.95, 2.35) // original -> Shorter
    static let scrubBack = (3.55, 4.35) // the ghost drags the bar back to the original
    static let toneToWarmer = (4.55, 4.8)
    static let morphB = (4.85, 6.25) // original -> Warmer
    static let morphBack = (7.05, 7.85) // Warmer -> original
    static let toneToFirst = (7.95, 8.2)

    enum Glyph {
        static let spread = 0.34
        static let letterLag = 0.008
        static let arcMin = 5.0
        static let arcMax = 22.0
        static let arcPerPoint = 0.09
        static let outEnd = 0.34
        static let outStagger = 0.25
        static let drop = 10.0
        static let inStart = 0.52
        static let inStagger = 0.2
        static let inLength = 0.28
        static let rise = 7.0
    }

    /// Response ~0.48 s, damping ratio 0.85.
    static let stiffness = 170.0
    static let damping = 22.2

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func seg(_ t: Double, _ r: (Double, Double)) -> Double { seg(t, r.0, r.1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func easeInOutCubic(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }
    static func easeInOutSine(_ k: Double) -> Double { -(cos(Double.pi * k) - 1) / 2 }
    static func easeOutCubic(_ k: Double) -> Double { 1 - pow(1 - k, 3) }

    /// Morph amount 0 (original) to 1 (rewrite). Linear in the morphs: each glyph applies its own ease.
    static func progressAt(_ t: Double) -> Double {
        if t < scrubBack.0 { return seg(t, morphA) }
        if t < morphB.0 { return 1 - easeInOutSine(seg(t, scrubBack)) }
        if t < morphBack.0 { return seg(t, morphB) }
        return 1 - seg(t, morphBack)
    }

    /// Which rewrite the text is morphing to. It only changes while the morph is at 0.
    static func toneAt(_ t: Double) -> Int { t >= toneToWarmer.0 && t < toneToFirst.0 ? 1 : 0 }

    static func toneIndicatorAt(_ t: Double) -> Double {
        easeInOutCubic(seg(t, toneToWarmer)) * (1 - easeInOutCubic(seg(t, toneToFirst)))
    }

    static func ghostOpacityAt(_ t: Double) -> Double {
        let chipA = seg(t, 0.6, 0.78) * (1 - seg(t, 1.05, 1.25))
        let bar = seg(t, 3.15, 3.38) * (1 - seg(t, 4.3, 4.48))
        let chipB = seg(t, 4.5, 4.66) * (1 - seg(t, 4.95, 5.15))
        return max(chipA, bar, chipB)
    }

    /// 0 = hovering, 1 = pressed.
    static func ghostPressAt(_ t: Double) -> Double {
        let chipA = seg(t, 0.82, 0.92) * (1 - seg(t, 0.98, 1.1))
        let bar = seg(t, 3.4, 3.52) * (1 - seg(t, 4.3, 4.42))
        let chipB = seg(t, 4.7, 4.8) * (1 - seg(t, 4.86, 4.98))
        return max(chipA, bar, chipB)
    }

    /// The bar past its ends gives way like a rubber band.
    static func rubber(_ value: Double) -> Double {
        if value < 0 { return value / (1 - value * 3) * 0.4 }
        if value > 1 {
            let over = value - 1
            return 1 + (over / (1 + over * 3)) * 0.4
        }
        return value
    }

    /// JavaScript's `Math.round` (halves go up), so the stats read as in the other stacks.
    static func jsRound(_ v: Double) -> Double { (v + 0.5).rounded(.down) }
}

// MARK: - Which letters of the old text become which letters of the new one

enum GlyphReflowEditMatch {
    typealias Pair = (from: Int, to: Int)

    private static func isSpace(_ c: UInt16) -> Bool { c == 32 || c == 9 || c == 10 || c == 13 }

    private static func wordsOf(_ text: [UInt16]) -> [(start: Int, end: Int, key: String)] {
        var out: [(Int, Int, String)] = []
        var i = 0
        while i < text.count {
            if isSpace(text[i]) { i += 1; continue }
            var end = i
            while end < text.count && !isSpace(text[end]) { end += 1 }
            let word = String(decoding: text[i..<end], as: UTF16.self).lowercased()
            let key = String(word.filter { $0.isLetter || $0.isNumber })
            out.append((i, end, key))
            i = end
        }
        return out
    }

    /// Longest common subsequence, as index pairs in increasing order.
    private static func commonRun<T: Equatable>(_ a: [T], _ b: [T]) -> [Pair] {
        let row = b.count + 1
        var table = [Int](repeating: 0, count: (a.count + 1) * row)
        if !a.isEmpty && !b.isEmpty {
            for i in stride(from: a.count - 1, through: 0, by: -1) {
                for j in stride(from: b.count - 1, through: 0, by: -1) {
                    table[i * row + j] = a[i] == b[j] ? table[(i + 1) * row + j + 1] + 1 : max(table[(i + 1) * row + j], table[i * row + j + 1])
                }
            }
        }
        var out: [Pair] = []
        var i = 0, j = 0
        while i < a.count && j < b.count {
            if a[i] == b[j] {
                out.append((i, j)); i += 1; j += 1
            } else if table[(i + 1) * row + j] >= table[i * row + j + 1] {
                i += 1
            } else {
                j += 1
            }
        }
        return out
    }

    private static func letters(_ text: [UInt16], _ from: Int, _ to: Int) -> [Int] {
        guard from < to else { return [] }
        return (from..<to).filter { !isSpace(text[$0]) }
    }

    private static func pairLetters(_ a: [UInt16], _ b: [UInt16], _ aFrom: Int, _ aTo: Int, _ bFrom: Int, _ bTo: Int, _ out: inout [Pair]) {
        let aAt = letters(a, aFrom, aTo)
        let bAt = letters(b, bFrom, bTo)
        for (i, j) in commonRun(aAt.map { a[$0] }, bAt.map { b[$0] }) { out.append((aAt[i], bAt[j])) }
    }

    /// Whole words that survive the edit are anchors, so they travel together. In the gaps between anchors, the
    /// letters the two versions share are paired in reading order. Everything unpaired fades.
    static func glyphs(_ aText: String, _ bText: String) -> [Pair] {
        let a = Array(aText.utf16), b = Array(bText.utf16)
        let aWords = wordsOf(a), bWords = wordsOf(b)
        let anchors = commonRun(aWords.map(\.key), bWords.map(\.key))
        var out: [Pair] = []
        var aCursor = 0, bCursor = 0
        for (i, j) in anchors {
            pairLetters(a, b, aCursor, aWords[i].start, bCursor, bWords[j].start, &out)
            pairLetters(a, b, aWords[i].start, aWords[i].end, bWords[j].start, bWords[j].end, &out)
            aCursor = aWords[i].end
            bCursor = bWords[j].end
        }
        pairLetters(a, b, aCursor, a.count, bCursor, b.count, &out)
        return out
    }
}

// MARK: - Measured once: glyph positions and the morph tables

struct GlyphReflowEditLaid {
    var glyphs: [CGGlyph] = []
    var x: [Double] = []
    var y: [Double] = []
    var line: [Int] = []
    var lines = 1
    var height = 0.0
}

struct GlyphReflowEditMorph {
    // Glyphs both versions share.
    var ids: [CGGlyph] = []
    var ax: [Double] = [], ay: [Double] = [], bx: [Double] = [], by: [Double] = []
    var arc: [Double] = [], delay: [Double] = []
    // Glyphs only the original has.
    var outIds: [CGGlyph] = []
    var outX: [Double] = [], outY: [Double] = [], outDelay: [Double] = []
    // Glyphs only the rewrite has.
    var inIds: [CGGlyph] = []
    var inX: [Double] = [], inY: [Double] = [], inDelay: [Double] = []
}

enum GlyphReflowEditLayout {
    private typealias G = GlyphReflowEditMotion.Glyph

    /// The system font as the React Native build draws it in Skia: the 12 pt face scaled to `size`, its optical
    /// size held at 12, so advances and wraps match. `as CTFont` keeps the system face (a lookup by name would
    /// answer Times New Roman).
    static func font(_ size: Double) -> CTFont {
        let base = UIFont.systemFont(ofSize: 12, weight: .regular) as CTFont
        let optical = CTFontDescriptorCreateWithAttributes([kCTFontOpticalSizeAttribute: 12] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, optical)
    }

    /// Greedy word wrap on real glyph advances.
    static func layOut(_ font: CTFont, _ text: String, width: Double, lineHeight: Double) -> GlyphReflowEditLaid {
        let chars = Array(text.utf16)
        let n = chars.count
        var glyphs = [CGGlyph](repeating: 0, count: n)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, n)
        var adv = [CGSize](repeating: .zero, count: n)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, n)
        let advances = adv.map { Double($0.width) }
        let ascent = Double(CTFontGetAscent(font)), descent = Double(CTFontGetDescent(font))
        let baseline = (lineHeight - (descent + ascent)) / 2 + ascent
        var out = GlyphReflowEditLaid()
        out.glyphs = glyphs
        out.x = [Double](repeating: 0, count: n)
        out.y = [Double](repeating: 0, count: n)
        out.line = [Int](repeating: 0, count: n)
        var row = 0
        var pen = 0.0
        var i = 0
        while i < n {
            var end = i
            while end < n && chars[end] != 32 { end += 1 }
            var wordWidth = 0.0
            for k in i..<end { wordWidth += advances[k] }
            if pen > 0 && pen + wordWidth > width {
                row += 1
                pen = 0
            }
            for k in i..<end {
                out.x[k] = pen
                out.y[k] = Double(row) * lineHeight + baseline
                out.line[k] = row
                pen += advances[k]
            }
            if end < n {
                out.x[end] = pen
                out.y[end] = Double(row) * lineHeight + baseline
                out.line[end] = row
                pen += advances[end]
            }
            i = end + 1
        }
        out.lines = row + 1
        out.height = Double(row + 1) * lineHeight
        return out
    }

    private static func hash(_ i: Int) -> Double {
        let v = sin(Double(i) * 12.9898) * 43758.5453
        return v - v.rounded(.down)
    }

    private static func orderOf(_ laid: GlyphReflowEditLaid, _ i: Int, _ width: Double) -> Double {
        let rows = Double(max(laid.lines - 1, 1))
        return min(Double(laid.line[i]) / rows * 0.62 + laid.x[i] / width * 0.38, 1)
    }

    static func buildMorph(_ fromText: String, _ toText: String, _ from: GlyphReflowEditLaid, _ to: GlyphReflowEditLaid, width: Double) -> GlyphReflowEditMorph {
        let pairs = GlyphReflowEditMatch.glyphs(fromText, toText)
        let a = Array(fromText.utf16), b = Array(toText.utf16)
        var usedFrom = Set<Int>(), usedTo = Set<Int>()
        var m = GlyphReflowEditMorph()
        // Letters landing in one word share its arc and its start, so words stay readable in flight.
        var wordOf = [Int](repeating: 0, count: b.count)
        if b.count > 1 {
            for i in 1..<b.count { wordOf[i] = wordOf[i - 1] + (b[i] != 32 && b[i - 1] == 32 ? 1 : 0) }
        }
        var flights: [Int: (arc: Double, delay: Double, seen: Int)] = [:]
        for (ia, ib) in pairs {
            usedFrom.insert(ia)
            usedTo.insert(ib)
            let distance = hypot(to.x[ib] - from.x[ia], to.y[ib] - from.y[ia])
            let w = wordOf[ib]
            var flight = flights[w] ?? {
                let height = min(G.arcMin + distance * G.arcPerPoint, G.arcMax) * (0.8 + hash(w) * 0.2)
                return (-height, orderOf(to, ib, width), 0)
            }()
            m.ids.append(from.glyphs[ia])
            m.ax.append(from.x[ia]); m.ay.append(from.y[ia])
            m.bx.append(to.x[ib]); m.by.append(to.y[ib])
            m.arc.append(distance < 0.5 ? 0 : flight.arc)
            m.delay.append(min(flight.delay + Double(flight.seen) * G.letterLag, 1))
            flight.seen += 1
            flights[w] = flight
        }
        for i in 0..<a.count where !usedFrom.contains(i) && a[i] != 32 {
            m.outIds.append(from.glyphs[i])
            m.outX.append(from.x[i]); m.outY.append(from.y[i])
            m.outDelay.append(orderOf(from, i, width))
        }
        for i in 0..<b.count where !usedTo.contains(i) && b[i] != 32 {
            m.inIds.append(to.glyphs[i])
            m.inX.append(to.x[i]); m.inY.append(to.y[i])
            m.inDelay.append(orderOf(to, i, width))
        }
        return m
    }

    static func stats(_ original: String, _ text: String) -> (words: String, read: String, change: String) {
        let count = { (s: String) in s.split(whereSeparator: { $0 == " " || $0 == "\n" || $0 == "\t" }).count }
        let words = count(text), base = count(original)
        let change = Int(GlyphReflowEditMotion.jsRound(Double(words - base) / Double(base) * 100))
        let read = max(Int(GlyphReflowEditMotion.jsRound(Double(words) / 3.6)), 1)
        return ("\(words)", "\(read) s", change == 0 ? "0%" : "\(change > 0 ? "+" : "\u{2212}")\(abs(change))%")
    }
}

/// The page as drawn: the chosen font, one outline per glyph, a morph per rewrite.
struct GlyphReflowEditPage {
    static let sizes: [Double] = [31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16]
    static let leading = 1.38

    let size: Double
    let morphs: [GlyphReflowEditMorph]
    let paths: [CGGlyph: Path]

    /// The largest type at which every version fits the card, measured once per size.
    init(original: String, versions: [String], textWidth: Double, textRoom: Double) {
        var chosen: (Double, CTFont, [GlyphReflowEditMorph])?
        for size in Self.sizes {
            let font = GlyphReflowEditLayout.font(size)
            let lineHeight = GlyphReflowEditMotion.jsRound(size * Self.leading)
            let base = GlyphReflowEditLayout.layOut(font, original, width: textWidth, lineHeight: lineHeight)
            let laid = versions.map { GlyphReflowEditLayout.layOut(font, $0, width: textWidth, lineHeight: lineHeight) }
            let tallest = ([base.height] + laid.map(\.height)).max() ?? 0
            chosen = (size, font, versions.indices.map { GlyphReflowEditLayout.buildMorph(original, versions[$0], base, laid[$0], width: textWidth) })
            if tallest <= textRoom { break }
        }
        let (size, font, morphs) = chosen!
        self.size = size
        self.morphs = morphs
        var paths: [CGGlyph: Path] = [:]
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        for m in morphs {
            for g in m.ids + m.outIds + m.inIds where paths[g] == nil {
                paths[g] = CTFontCreatePathForGlyph(font, g, &flip).map { Path($0) } ?? Path()
            }
        }
        self.paths = paths
    }
}
