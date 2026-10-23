import SwiftUI

/// A small dot-matrix alphabet, seven pixels high, for the clock on the column screen, and the pixel
/// globe beside it. Each row is a string of "1" (lit) and "0" (dark); a glyph may be narrower than
/// five pixels.
enum MorphBubbleColumnGlyphs {
    static let glyphs: [Character: [String]] = [
        "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
        "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
        "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
        "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
        "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
        "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
        "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
        "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
        "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
        "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
        "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
        "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
        ":": ["0", "1", "1", "0", "1", "1", "0"],
        "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
        "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
        " ": ["000", "000", "000", "000", "000", "000", "000"],
    ]

    /// A pixel globe, seven by seven, like the one beside the clock in the reference.
    static let globe = ["0011100", "0101010", "1111111", "1010101", "1111111", "0101010", "0011100"]

    /// The glyph rows for each character of `text`.
    static func rows(_ text: String) -> [[String]] {
        text.map { glyphs[$0] ?? glyphs[" "]! }
    }

    /// The lit pixels of `rows` as filled squares, `px` wide with `gap` between them, its top left
    /// at (x, y).
    static func path(_ rows: [[String]], x: Double, y: Double, px: Double, gap: Double) -> Path {
        var path = Path()
        var left = x
        for glyph in rows {
            for (r, row) in glyph.enumerated() {
                for (c, bit) in row.enumerated() where bit == "1" {
                    path.addRect(CGRect(x: left + Double(c) * px + gap / 2, y: y + Double(r) * px + gap / 2, width: px - gap, height: px - gap))
                }
            }
            left += Double((glyph.first?.count ?? 0) + 1) * px
        }
        return path
    }
}
