import CoreText
import SwiftUI

/*
 Karaoke Lyrics Highlight — the lyrics, their timing, the layout and the pure timeline.

 The chorus has four lines and repeats, so the lyric list is endless: absolute line i shows
 chorus line i mod 4, and the list scrolls down it forever. Every line gets a 2.5 s slot: its
 words fill between 0.1 and 2.05 s (times below), and at 1.95 s the list starts scrolling to
 the next line, reaching it exactly at 2.5 s along an ease-out-back curve (a small overshoot,
 like a soft spring). Four slots make the 10 s loop, ending one chorus further down, which looks
 exactly like its start.
 */

struct KaraokeLyricsHighlightWord: Equatable {
    var text: String
    /// Seconds into the line's slot when the fill starts and ends.
    var start: Double
    var end: Double
    /// A held note: its letters wave while it fills.
    var held = false
}

enum KaraokeLyricsHighlightSong {
    static let title = "Paper Satellites"
    static let artist = "Lumen Fair"
    /// The chorus starts 1:12 into a 3:43 song.
    static let chorusAt: Double = 72
    static let length: Double = 223

    typealias W = KaraokeLyricsHighlightWord
    static let chorus: [[W]] = [
        [W(text: "We", start: 0.15, end: 0.40), W(text: "fold", start: 0.40, end: 0.70), W(text: "the", start: 0.70, end: 0.88),
         W(text: "night", start: 0.88, end: 1.25), W(text: "into", start: 1.25, end: 1.52), W(text: "paper", start: 1.52, end: 2.0)],
        [W(text: "and", start: 0.15, end: 0.33), W(text: "throw", start: 0.33, end: 0.68), W(text: "it", start: 0.68, end: 0.82),
         W(text: "past", start: 0.82, end: 1.10), W(text: "the", start: 1.10, end: 1.25), W(text: "streetlights", start: 1.25, end: 2.0)],
        [W(text: "Ohhh,", start: 0.10, end: 1.05, held: true), W(text: "we're", start: 1.05, end: 1.30),
         W(text: "paper", start: 1.30, end: 1.62), W(text: "satellites", start: 1.62, end: 2.05)],
        [W(text: "still", start: 0.15, end: 0.42), W(text: "circling,", start: 0.42, end: 0.98), W(text: "still", start: 1.00, end: 1.30),
         W(text: "bright", start: 1.30, end: 2.0)],
    ]
}

enum KaraokeLyricsHighlightTimeline {
    static let slot: Double = 2.5
    static let lines = 4
    static let loop: Double = 10
    static let scrollStart: Double = 1.95
    /// The playhead glides from the end of the chorus back to its start over the last 0.5 s.
    static let rewind: Double = 0.5

    /// Where the list is, in lines from the loop's first line, `e` seconds into the loop (0 ≤ e < 10).
    static func scroll(_ e: Double) -> Double {
        let k = min(Double(lines - 1), floor(e / slot))
        let within = e - k * slot
        return k + easeOutBack(max(0, (within - scrollStart) / (slot - scrollStart)))
    }

    /// The line being sung and how far into its slot, `e` seconds into the loop.
    static func singing(_ e: Double) -> (line: Int, tau: Double) {
        let k = min(lines - 1, Int(e / slot))
        return (k, e - Double(k) * slot)
    }

    /// How much of a word is filled when its line is `tau` seconds into its slot.
    static func fill(_ w: KaraokeLyricsHighlightWord, tau: Double) -> Double {
        min(1, max(0, (tau - w.start) / (w.end - w.start)))
    }

    /// How much a word is lifted (0…1): in over 0.12 s as it starts, out over 0.2 s as it ends.
    static func lift(_ w: KaraokeLyricsHighlightWord, tau: Double) -> Double {
        smoothstep(w.start, w.start + 0.12, tau) * (1 - smoothstep(w.end - 0.05, w.end + 0.15, tau))
    }

    /// The song time in seconds shown by the scrubber.
    static func playhead(_ e: Double) -> Double {
        let end = KaraokeLyricsHighlightSong.chorusAt + loop - rewind
        if e < loop - rewind { return KaraokeLyricsHighlightSong.chorusAt + e }
        return end + (KaraokeLyricsHighlightSong.chorusAt - end) * easeInOut((e - (loop - rewind)) / rewind)
    }

    static func easeOutBack(_ t: Double) -> Double {
        let c1 = 1.2, c3 = c1 + 1
        let u = min(1, max(0, t)) - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }

    static func easeInOut(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /// Each line's look by its distance from the focus, d = line − scroll (negative: already sung).
    static func sungAlpha(_ d: Double) -> Double { d >= 0 ? 1 : 1 - 0.75 * min(1, -d) }
    static func unsungAlpha(_ d: Double) -> Double { d <= 0 ? 0.35 : 0.35 - 0.1 * min(d, 2) }
    static func blur(_ d: Double) -> Double { d < 0 ? 2.5 * min(1, -d) : 1.5 * min(d, 2) }
}

enum KaraokeLyricsHighlightFont {
    static let name = "Poppins-Bold"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "KaraokeLyricsHighlight-Poppins-Bold", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    /// Advances of a string's characters at 1 pt (no kerning, as in every stack).
    static func advances(_ text: String) -> [Double] {
        _ = registered
        let font = CTFontCreateWithName(name as CFString, 1, nil)
        var chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, glyphs.count)
        return adv.map { Double($0.width) }
    }
}

/// The chorus laid out for a width: lines wrapped into rows by measured advances.
struct KaraokeLyricsHighlightLayout {
    static let size: Double = 32
    static let rowHeight: Double = 38
    static let gap: Double = 22
    static let margin: Double = 24

    struct PlacedWord {
        var word: KaraokeLyricsHighlightWord
        var x: Double
        var row: Int
        var width: Double
        /// Each letter's offset from the word's left edge.
        var letters: [(Character, Double)]
    }

    struct Line {
        var words: [PlacedWord]
        var height: Double
    }

    let lines: [Line]
    /// Top of each chorus line within one chorus, and the height of one whole chorus.
    let prefix: [Double]
    let cycle: Double

    init(width: Double) {
        let maxWidth = width - 2 * Self.margin
        let space = KaraokeLyricsHighlightFont.advances(" ")[0] * Self.size
        var built: [Line] = []
        for words in KaraokeLyricsHighlightSong.chorus {
            var placed: [PlacedWord] = []
            var x = 0.0, row = 0
            for w in words {
                let adv = KaraokeLyricsHighlightFont.advances(w.text).map { $0 * Self.size }
                let ww = adv.reduce(0, +)
                if x > 0 && x + ww > maxWidth { row += 1; x = 0 }
                var offs: [(Character, Double)] = []
                var lx = 0.0
                for (ch, a) in zip(w.text, adv) { offs.append((ch, lx)); lx += a }
                placed.append(PlacedWord(word: w, x: Self.margin + x, row: row, width: ww, letters: offs))
                x += ww + space
            }
            built.append(Line(words: placed, height: Double(row + 1) * Self.rowHeight))
        }
        lines = built
        var p: [Double] = []
        var y = 0.0
        for l in built { p.append(y); y += l.height + Self.gap }
        prefix = p
        cycle = y
    }

    func line(_ i: Int) -> Line { lines[((i % lines.count) + lines.count) % lines.count] }

    /// Top of absolute line i, from the top of line 0.
    func top(_ i: Int) -> Double {
        let n = lines.count
        let c = Int(floor(Double(i) / Double(n)))
        return Double(c) * cycle + prefix[i - c * n]
    }

    /// The scroll offset in points for a scroll position in lines.
    func offset(_ s: Double) -> Double {
        let i = Int(floor(s))
        return top(i) + (s - Double(i)) * (line(i).height + Self.gap)
    }

    /// The scroll position in lines for an offset in points.
    func position(_ y: Double) -> Double {
        let n = lines.count
        let c = floor(y / cycle)
        var r = y - c * cycle
        for k in 0..<n {
            let step = lines[k].height + Self.gap
            if r < step || k == n - 1 { return c * Double(n) + Double(k) + min(1, r / step) }
            r -= step
        }
        return c * Double(n)
    }
}
