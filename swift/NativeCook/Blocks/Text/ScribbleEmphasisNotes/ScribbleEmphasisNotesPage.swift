import CoreText
import UIKit

/// The paragraph laid out once with CoreText, and every word measured, so the marks sit on real text.
struct ScribbleEmphasisNotesPage {
    struct Band {
        var x0: Double
        var x1: Double
        var base: Double
    }

    let x: Double
    let y: Double
    let width: Double
    let height: Double
    let fontSize: Double
    /// The lines and the UTF-16 range each one holds.
    let lines: [CTLine]
    let lineRanges: [Range<Int>]
    /// Per word: left edge, width, line index, sentence index. Screen coordinates.
    private(set) var wordX: [Double] = []
    private(set) var wordW: [Double] = []
    private(set) var wordLine: [Int] = []
    private(set) var wordSentence: [Int] = []
    /// Per sentence: first and last word.
    private(set) var sentenceFirst: [Int] = [0]
    private(set) var sentenceLast: [Int] = []
    /// Per line: baseline, left and right edge of its ink.
    let lineBase: [Double]
    private(set) var lineLeft: [Double]
    private(set) var lineRight: [Double]

    /// The system font as the React Native build's Skia paragraph draws it: the 12 pt face scaled, optical size
    /// held at 12, so the tracking UIKit bakes into each point size is left out and the lines wrap the same way.
    /// `as CTFont` keeps the system face; a lookup by name would answer Times New Roman.
    static func font(_ size: Double) -> CTFont {
        let base = UIFont.systemFont(ofSize: 12, weight: .regular) as CTFont
        let optical = CTFontDescriptorCreateWithAttributes([kCTFontOpticalSizeAttribute: 12] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, optical)
    }

    init(text: String, x: Double, y: Double, width: Double, fontSize: Double, lineHeight: Double, color: CGColor) {
        self.x = x
        self.y = y
        self.width = width
        self.fontSize = fontSize
        let font = Self.font(fontSize)
        let string = NSAttributedString(string: text, attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTKernAttributeName as String): -0.15,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
        ])
        let setter = CTTypesetterCreateWithAttributedString(string)
        let total = (text as NSString).length
        var lines: [CTLine] = []
        var ranges: [Range<Int>] = []
        var start = 0
        while start < total {
            let count = max(1, CTTypesetterSuggestLineBreak(setter, start, width))
            lines.append(CTTypesetterCreateLine(setter, CFRange(location: start, length: count)))
            ranges.append(start..<(start + count))
            start += count
        }
        self.lines = lines
        lineRanges = ranges
        height = Double(lines.count) * lineHeight
        // Half leading: the font's own ascent and descent centred in the line box.
        let ascent = Double(CTFontGetAscent(font)), descent = Double(CTFontGetDescent(font))
        let first = (lineHeight - (ascent + descent)) / 2 + ascent
        lineBase = lines.indices.map { y + Double($0) * lineHeight + first }
        lineLeft = lines.map { _ in .infinity }
        lineRight = lines.map { _ in -.infinity }

        // Words (runs without whitespace) and sentences (a word ending in . ! or ? closes one).
        let chars = Array(text.utf16)
        var sentence = 0
        var i = 0
        while i < chars.count {
            if chars[i] == 32 { i += 1; continue }
            var j = i
            while j < chars.count, chars[j] != 32 { j += 1 }
            if let line = ranges.firstIndex(where: { $0.contains(i) }) {
                let x0 = Double(CTLineGetOffsetForStringIndex(lines[line], i, nil))
                let x1 = Double(CTLineGetOffsetForStringIndex(lines[line], min(j, ranges[line].upperBound), nil))
                let index = wordX.count
                wordX.append(x + x0)
                wordW.append(x1 - x0)
                wordLine.append(line)
                wordSentence.append(sentence)
                lineLeft[line] = min(lineLeft[line], x + x0)
                lineRight[line] = max(lineRight[line], x + x1)
                let end = chars[j - 1]
                if end == 46 || end == 33 || end == 63 {
                    sentenceLast.append(index)
                    sentence += 1
                    sentenceFirst.append(index + 1)
                }
            }
            i = j
        }
        if sentenceLast.count < sentenceFirst.count { sentenceLast.append(wordX.count - 1) }
        if let last = sentenceFirst.last, last >= wordX.count {
            sentenceFirst.removeLast()
            sentenceLast = Array(sentenceLast.prefix(sentenceFirst.count))
        }
        for n in lines.indices where !lineLeft[n].isFinite {
            lineLeft[n] = x
            lineRight[n] = x + Double(CTLineGetTypographicBounds(lines[n], nil, nil, nil))
        }
    }

    /// The bands (one per line) a character range covers.
    func bands(_ start: Int, _ end: Int) -> [Band] {
        var out: [Band] = []
        for (n, range) in lineRanges.enumerated() {
            let a = max(start, range.lowerBound), b = min(end, range.upperBound)
            guard a < b else { continue }
            let x0 = Double(CTLineGetOffsetForStringIndex(lines[n], a, nil))
            let x1 = Double(CTLineGetOffsetForStringIndex(lines[n], b, nil))
            if x1 - x0 < 0.5 { continue }
            out.append(Band(x0: x + x0, x1: x + x1, base: lineBase[n]))
        }
        return out
    }

    func bands(of phrase: String, in text: String) -> [Band] {
        let range = (text as NSString).range(of: phrase)
        let start = range.location == NSNotFound ? 0 : range.location
        return bands(start, start + (phrase as NSString).length)
    }

    /// The bands words a…b cover, from the measured words and lines.
    func wordBands(_ a: Int, _ b: Int) -> [Band] {
        guard wordLine.indices.contains(a), wordLine.indices.contains(b) else { return [] }
        let first = wordLine[a], last = wordLine[b]
        guard first <= last else { return [] }
        return (first...last).map { line in
            Band(x0: line == first ? wordX[a] : lineLeft[line], x1: line == last ? wordX[b] + wordW[b] : lineRight[line], base: lineBase[line])
        }
    }

    /// The word under a point, or the nearest one on the nearest line.
    func wordAt(_ px: Double, _ py: Double) -> Int {
        var line = 0
        var near = Double.infinity
        for (i, base) in lineBase.enumerated() {
            let d = abs(base - fontSize * 0.32 - py)
            if d < near { near = d; line = i }
        }
        var word = -1
        near = .infinity
        for i in wordX.indices where wordLine[i] == line {
            let d = px < wordX[i] ? wordX[i] - px : (px > wordX[i] + wordW[i] ? px - wordX[i] - wordW[i] : 0)
            if d < near { near = d; word = i }
        }
        return word
    }

    /// Draws the paragraph into a flipped (y down) context.
    func draw(in cg: CGContext) {
        cg.saveGState()
        cg.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
        for (n, line) in lines.enumerated() {
            cg.textPosition = CGPoint(x: x, y: lineBase[n])
            CTLineDraw(line, cg)
        }
        cg.restoreGState()
    }
}
