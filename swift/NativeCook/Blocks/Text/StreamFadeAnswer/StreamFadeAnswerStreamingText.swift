import CoreText
import SwiftUI
import UIKit

/// Text that arrives word by word, the way an assistant's answer streams in.
///
/// The paragraph is laid out once, in full: every word is measured (glyph advances of the system font) and wrapped
/// into lines before anything shows, so a word never moves sideways or pushes another when it appears. Only three
/// things animate per word: opacity 0 → 1, a blur clearing and a rise of a few points. One `Canvas` draws it as a
/// pure function of the stream time: settled lines are one outline each, and only the dozen or so words still
/// arriving are drawn one by one. The newest line also fades toward the front of the stream (a horizontal gradient
/// mask that runs ahead with the words), which softens the leading edge without a caret.
struct StreamFadeAnswerStreamingText: View {
    let layout: StreamFadeAnswerTextLayout
    /// Seconds since this text's first word started. `.infinity` shows the whole text.
    var time: Double = .infinity
    var color: Color = Color(red: 0x1D / 255, green: 0x1C / 255, blue: 0x1A / 255)

    private typealias M = StreamFadeAnswerMotion
    /// Room around the text for the blur and the rise, so neither is clipped by the canvas.
    static let pad: Double = 12

    var body: some View {
        let L = layout, s = time, pad = Self.pad
        Canvas { ctx, _ in
            let n = L.starts.count
            if s <= 0 || n == 0 { return }
            ctx.translateBy(x: pad, y: pad)
            let ink = GraphicsContext.Shading.color(color)
            // Past the last word the front still has to run off the line before the mask is gone.
            if s >= L.duration + 0.12 {
                for l in 0..<L.lineCount { ctx.fill(L.linePaths[l], with: ink) }
                return
            }
            // Words [0, a) have arrived, [a, b) are arriving, the rest have not started.
            var a = 0
            while a < n && s - L.starts[a] >= M.wordDur { a += 1 }
            var b = a
            while b < n && L.starts[b] < s { b += 1 }

            let openLine = a < n ? L.lineOf[a] : L.lineCount
            for l in 0..<openLine { ctx.fill(L.linePaths[l], with: ink) }
            if a < n {
                for w in L.lineFirst[openLine]..<a { ctx.fill(L.wordPaths[w], with: ink) }
            }
            guard b > a else { return }
            ctx.drawLayer { layer in
                for w in a..<b {
                    let q = (s - L.starts[w]) / M.wordDur
                    let e = M.easeOutCubic(q)
                    var c = layer
                    c.opacity = M.easeOutQuad(q)
                    let blur = M.wordBlur * (1 - e)
                    if blur > 0.05 { c.addFilter(.blur(radius: blur)) }
                    c.translateBy(x: 0, y: M.wordRise * (1 - e))
                    c.fill(L.wordPaths[w], with: ink)
                }
                // The newest line fades toward the front, which keeps running at the pace of the stream.
                let k = b - 1
                let front = L.xs[k] + (s - L.starts[k]) * L.frontSpeed
                // Only this line's box (and the rise under it): the line above keeps its descenders.
                let top = L.lineBase[L.lineOf[k]] - L.firstBase
                var mask = layer
                mask.blendMode = .destinationIn
                mask.fill(Path(CGRect(x: -pad, y: top, width: L.width + pad * 2, height: L.lineHeight + M.wordRise + 4)),
                          with: .linearGradient(Gradient(colors: [.black, .black.opacity(0)]),
                                                startPoint: CGPoint(x: front - M.feather, y: 0), endPoint: CGPoint(x: front + 8, y: 0)))
            }
        }
        .frame(width: L.width + pad * 2, height: L.height + pad * 2)
        .padding(-pad)
        .frame(width: L.width, height: L.height)
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel(L.text)
    }
}

/// A text wrapped and measured once: where every word sits and when it starts. A line break starts a new line.
struct StreamFadeAnswerTextLayout {
    let text: String
    let width: Double
    let height: Double
    let lineHeight: Double
    let lineCount: Int
    /// The first baseline below a line's top.
    let firstBase: Double
    /// Per word: pen x, the line it is on, when it starts (s), its outline in place.
    var xs: [Double] = []
    var lineOf: [Int] = []
    var starts: [Double] = []
    var wordPaths: [Path] = []
    /// Per line: its words as one outline, its baseline and its first word.
    var linePaths: [Path] = []
    var lineBase: [Double] = []
    var lineFirst: [Int] = []
    /// Width of the longest line, pt.
    let widest: Double
    /// How fast the front of the stream runs along a line, pt/s.
    let frontSpeed: Double
    /// From the first word starting to the last word having arrived, s.
    let duration: Double
    /// When the last word begins, which is where the next text on the same clock can follow.
    var lastStart: Double { duration - StreamFadeAnswerMotion.wordDur }

    init(_ text: String, width: Double, fontSize: Double = 16, weight: UIFont.Weight = .regular, lineHeight: Double? = nil,
         rate: Double = StreamFadeAnswerMotion.wordRate, seed: Double = 0) {
        typealias M = StreamFadeAnswerMotion
        let lineHeight = lineHeight ?? (fontSize * 1.42).rounded()
        let font = Self.font(fontSize, weight)
        let ascent = Double(CTFontGetAscent(font)), descent = Double(CTFontGetDescent(font))
        // The baseline sits where a native Text of this line height would put it.
        let firstBase = (lineHeight - (descent + ascent)) / 2 + ascent
        let space = Self.measure(font, " ").widths.reduce(0, +)

        var words: [(glyphs: [CGGlyph], widths: [Double], x: Double, line: Int)] = []
        var lineFirst: [Int] = []
        var line = 0
        var travelled = 0.0
        var widest = 0.0
        for paragraph in text.components(separatedBy: "\n") {
            let parts = paragraph.split(separator: " ", omittingEmptySubsequences: true)
            var x = 0.0
            for part in parts {
                let m = Self.measure(font, String(part))
                let wide = m.widths.reduce(0, +)
                if x > 0 && x + wide > width {
                    line += 1
                    x = 0
                }
                if lineFirst.count <= line { lineFirst.append(words.count) }
                words.append((m.glyphs, m.widths, x, line))
                widest = max(widest, x + wide)
                x += wide + space
                travelled += wide + space
            }
            if !parts.isEmpty { line += 1 }
        }
        let lineCount = max(1, line)
        while lineFirst.count <= lineCount { lineFirst.append(words.count) }

        var linePaths = [Path](repeating: Path(), count: lineCount)
        var lineBase: [Double] = []
        for l in 0..<lineCount { lineBase.append(firstBase + Double(l) * lineHeight) }
        for w in words {
            let cg = CGMutablePath()
            var pen = w.x
            for (g, adv) in zip(w.glyphs, w.widths) {
                var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pen, ty: lineBase[w.line])
                if let glyph = CTFontCreatePathForGlyph(font, g, &t) { cg.addPath(glyph) }
                pen += adv
            }
            let path = Path(cg)
            wordPaths.append(path)
            linePaths[w.line].addPath(path)
            xs.append(w.x)
            lineOf.append(w.line)
        }

        self.text = text
        self.width = width
        self.lineHeight = lineHeight
        self.lineCount = lineCount
        self.height = Double(lineCount) * lineHeight
        self.firstBase = firstBase
        self.linePaths = linePaths
        self.lineBase = lineBase
        self.lineFirst = lineFirst
        self.widest = widest
        self.starts = M.wordStarts(words.count, rate: rate, seed: seed)
        self.frontSpeed = words.isEmpty ? 0 : travelled / Double(words.count) * rate
        self.duration = (starts.last ?? 0) + M.wordDur
    }

    /// The system font as the React Native build draws it (Skia): the 12 pt face scaled to `size` with its optical
    /// size held at 12, so the tracking UIKit bakes into each point size is left out and every size has the same
    /// proportions. `as CTFont` keeps the system face; a lookup by name would answer Times New Roman.
    static func font(_ size: Double, _ weight: UIFont.Weight) -> CTFont {
        let base = UIFont.systemFont(ofSize: 12, weight: weight) as CTFont
        let optical = CTFontDescriptorCreateWithAttributes([kCTFontOpticalSizeAttribute: 12] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, optical)
    }

    private static func measure(_ font: CTFont, _ text: String) -> (glyphs: [CGGlyph], widths: [Double]) {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, chars.count)
        return (glyphs, adv.map { Double($0.width) })
    }
}
