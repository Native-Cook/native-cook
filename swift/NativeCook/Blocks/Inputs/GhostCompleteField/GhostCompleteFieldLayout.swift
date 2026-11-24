import CoreText
import SwiftUI
import UIKit

/*
 Ghost Complete Field: measuring and wrapping.

 The field draws its own text, so it needs to know where every glyph goes. The typed text and
 the suggestion after it are wrapped as ONE paragraph: accepting the suggestion then moves
 nothing, the grey glyphs just take ink where they already are.
*/
final class GhostCompleteFieldLayout {
    struct Word { let a: Int, b: Int, x0: Double, off: Double }
    struct Run { let start: Int, end: Int, length: Double, words: [Word], chipX: Double, chipY: Double }

    static let maxWords = 8
    static let keycapW = 70.0
    private static let keycapGap = 10.0

    let count: Int
    let cx: [Double]
    let cy: [Double]
    let runs: [Run]

    private let glyphs: [Path]
    private var cache: [Int: Path] = [:]

    static let empty = GhostCompleteFieldLayout(font: UIFont.systemFont(ofSize: 17), text: "", ghosts: [], width: 100, lineHeight: 22)

    /// Glyphs [a, b) as one path; cached, because the same ranges are drawn frame after frame.
    func path(_ a: Int, _ b: Int) -> Path {
        let lo = max(0, a), hi = min(count, b)
        if hi <= lo { return Path() }
        let key = lo << 16 | hi
        if let hit = cache[key] { return hit }
        var out = Path()
        for i in lo..<hi { out.addPath(glyphs[i]) }
        cache[key] = out
        return out
    }

    init(font: UIFont, text: String, ghosts: [(Int, Int)], width: Double, lineHeight: Double, keycap: Bool = true) {
        let ct = font as CTFont
        let units = Array(text.utf16)
        let n = units.count
        var ids = [CGGlyph](repeating: 0, count: n)
        var advances = [CGSize](repeating: .zero, count: n)
        if n > 0 {
            CTFontGetGlyphsForCharacters(ct, units, &ids, n)
            CTFontGetAdvancesForGlyphs(ct, .horizontal, ids, &advances, n)
        }
        var widths = advances.map { Double($0.width) }
        let ascent = Double(CTFontGetAscent(ct))
        let descent = Double(CTFontGetDescent(ct))
        let base = (lineHeight - (descent + ascent)) / 2 + ascent

        let space = UInt16(32), newline = UInt16(10)
        var xs = [Double](repeating: 0, count: n)
        var lineOf = [Int](repeating: 0, count: n)
        var x = 0.0
        var line = 0
        var i = 0
        while i < n {
            let ch = units[i]
            if ch == newline {
                xs[i] = x
                lineOf[i] = line
                widths[i] = 0
                line += 1
                x = 0
                i += 1
            } else if ch == space {
                xs[i] = x
                lineOf[i] = line
                x += widths[i]
                i += 1
            } else {
                var j = i
                var word = 0.0
                while j < n && units[j] != space && units[j] != newline {
                    word += widths[j]
                    j += 1
                }
                if x > 0 && x + word > width {
                    line += 1
                    x = 0
                }
                for k in i..<j {
                    if x > 0 && x + widths[k] > width {
                        line += 1
                        x = 0
                    }
                    xs[k] = x
                    lineOf[k] = line
                    x += widths[k]
                }
                i = j
            }
        }

        var cxs: [Double] = [0]
        var cys: [Double] = [0]
        if n > 0 {
            for k in 1...n {
                if units[k - 1] == newline {
                    cxs.append(0)
                    cys.append(Double(lineOf[k - 1] + 1) * lineHeight)
                } else {
                    cxs.append(min(width, xs[k - 1] + widths[k - 1]))
                    cys.append(Double(lineOf[k - 1]) * lineHeight)
                }
            }
        }

        var outRuns: [Run] = []
        for (g0, g1) in ghosts {
            let start = max(0, min(n, g0))
            let end = max(start, min(n, g1))
            var words: [Word] = []
            var flow = 0.0
            var lineStartX = 0.0
            var lineStartFlow = 0.0
            var current = -1
            var a = start
            var k = start
            while k < end {
                let newLine = lineOf[k] != current
                let newWord = k > start && units[k] != space && units[k - 1] == space
                if k > a && (newLine || newWord) && words.count < Self.maxWords - 1 {
                    words.append(Word(a: a, b: k, x0: lineStartX, off: lineStartFlow))
                    a = k
                }
                if newLine {
                    current = lineOf[k]
                    lineStartX = xs[k]
                    lineStartFlow = flow
                }
                flow += widths[k]
                k += 1
            }
            if end > a { words.append(Word(a: a, b: end, x0: lineStartX, off: lineStartFlow)) }

            var chipX = 0.0
            var chipY = 0.0
            if end > start {
                let last = end - 1
                let after = xs[last] + widths[last]
                if after + Self.keycapGap + Self.keycapW <= width {
                    chipX = after + Self.keycapGap
                    chipY = Double(lineOf[last]) * lineHeight
                } else {
                    chipY = Double(lineOf[last] + 1) * lineHeight
                }
            }
            outRuns.append(Run(start: start, end: end, length: flow, words: words, chipX: chipX, chipY: chipY))
        }

        var paths: [Path] = []
        paths.reserveCapacity(n)
        for g in 0..<n {
            let baseline = Double(lineOf[g]) * lineHeight + base
            if let outline = CTFontCreatePathForGlyph(ct, ids[g], nil) {
                let flip = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: xs[g], ty: baseline)
                paths.append(Path(outline).applying(flip))
            } else {
                paths.append(Path())
            }
        }

        count = n
        cx = cxs
        cy = cys
        runs = outRuns
        glyphs = paths
    }

    // MARK: Text helpers

    static func textWidth(_ font: UIFont, _ text: String) -> Double {
        let ct = font as CTFont
        let units = Array(text.utf16)
        guard !units.isEmpty else { return 0 }
        var ids = [CGGlyph](repeating: 0, count: units.count)
        var advances = [CGSize](repeating: .zero, count: units.count)
        CTFontGetGlyphsForCharacters(ct, units, &ids, units.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, ids, &advances, units.count)
        return advances.reduce(0) { $0 + Double($1.width) }
    }

    static func cleanText(_ text: String, multiline: Bool) -> String {
        let kept = text.utf16.filter { c in
            if c >= 0xD800 && c <= 0xDFFF { return false }
            if c == 10 && !multiline { return false }
            return c != 13 && c != 9
        }
        return String(decoding: kept, as: UTF16.self)
    }

    // MARK: Suggestions

    private static let phrases = [
        "Hi Mara, thanks for the notes \u{2014} ",
        "can we walk through them Thursday?",
        "thanks for getting back to me so quickly.",
        "let me know what works for you.",
        "sounds good to me.",
        "see you on Thursday.",
        "happy to take a look this week.",
        "I will send the updated draft tonight.",
        "looking forward to it.",
        "Best, Ines",
    ]
    private static let minMatch = 3

    static func suggestPhrase(_ text: String) -> String? {
        let units = Array(text.utf16)
        if units.count < minMatch || units.last == 10 { return nil }
        let from = max(0, units.count - 40)
        var s = from
        while s <= units.count - minMatch {
            defer { s += 1 }
            if s > 0 && units[s - 1] != 32 && units[s - 1] != 10 { continue }
            let tail = String(decoding: units[s...], as: UTF16.self).lowercased()
            if tail.trimmingCharacters(in: .whitespacesAndNewlines).utf16.count < minMatch { continue }
            let tailLength = tail.utf16.count
            for phrase in phrases where phrase.utf16.count > tailLength && phrase.lowercased().hasPrefix(tail) {
                return String(decoding: Array(phrase.utf16)[tailLength...], as: UTF16.self)
            }
        }
        return nil
    }

    static let domains = ["postbox.co", "northmail.com", "fernhill.org", "studio-okafor.com"]

    static func suggestDomains(_ address: String) -> [String] {
        let parts = address.components(separatedBy: "@")
        guard parts.count == 2, !parts[0].isEmpty, !address.contains(" ") else { return [] }
        let started = parts[1].lowercased()
        return Array(domains.filter { $0.hasPrefix(started) && $0 != started }.prefix(2))
    }
}
