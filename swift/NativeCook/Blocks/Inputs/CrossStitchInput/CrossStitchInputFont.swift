import Foundation

/*
 Cross Stitch Input — a 5 × 7 sampler alphabet, the kind printed on cross-stitch charts, and the
 layout of a name on the aida grid. Capitals and digits use rows 0–6; lower case uses the same rows
 with ascenders in rows 0–1 and descenders in rows 7–8. 'X' is a stitch.
 */

/// A laid-out name: the cell of every stitch (grid units, the fabric's centre at 0), the glyph each
/// stitch belongs to, and per glyph its first stitch, count and top-left cell. Stitches are listed
/// in working order: glyph by glyph, rows top to bottom, each row worked back the other way.
struct CrossStitchInputLayout {
    var cx: [Double] = []
    var cy: [Double] = []
    var glyphOf: [Int] = []
    var first: [Int] = []
    var count: [Int] = []
    var col: [Double] = []
    var row: [Double] = []
    var total: Int { cx.count }
}

enum CrossStitchInputFont {
    static let maxLetters = 10
    /// Columns of grid a line may use before the name wraps to a second line.
    static let lineCols = 24

    private static let glyphs: [Character: String] = [
        "A": ".XXX.|X...X|X...X|XXXXX|X...X|X...X|X...X",
        "B": "XXXX.|X...X|X...X|XXXX.|X...X|X...X|XXXX.",
        "C": ".XXX.|X...X|X....|X....|X....|X...X|.XXX.",
        "D": "XXXX.|X...X|X...X|X...X|X...X|X...X|XXXX.",
        "E": "XXXXX|X....|X....|XXXX.|X....|X....|XXXXX",
        "F": "XXXXX|X....|X....|XXXX.|X....|X....|X....",
        "G": ".XXX.|X...X|X....|X.XXX|X...X|X...X|.XXXX",
        "H": "X...X|X...X|X...X|XXXXX|X...X|X...X|X...X",
        "I": "XXX|.X.|.X.|.X.|.X.|.X.|XXX",
        "J": "..XXX|...X.|...X.|...X.|...X.|X..X.|.XX..",
        "K": "X...X|X..X.|X.X..|XX...|X.X..|X..X.|X...X",
        "L": "X....|X....|X....|X....|X....|X....|XXXXX",
        "M": "X...X|XX.XX|X.X.X|X.X.X|X...X|X...X|X...X",
        "N": "X...X|XX..X|X.X.X|X..XX|X...X|X...X|X...X",
        "O": ".XXX.|X...X|X...X|X...X|X...X|X...X|.XXX.",
        "P": "XXXX.|X...X|X...X|XXXX.|X....|X....|X....",
        "Q": ".XXX.|X...X|X...X|X...X|X.X.X|X..X.|.XX.X",
        "R": "XXXX.|X...X|X...X|XXXX.|X.X..|X..X.|X...X",
        "S": ".XXXX|X....|X....|.XXX.|....X|....X|XXXX.",
        "T": "XXXXX|..X..|..X..|..X..|..X..|..X..|..X..",
        "U": "X...X|X...X|X...X|X...X|X...X|X...X|.XXX.",
        "V": "X...X|X...X|X...X|X...X|X...X|.X.X.|..X..",
        "W": "X...X|X...X|X...X|X.X.X|X.X.X|X.X.X|.X.X.",
        "X": "X...X|X...X|.X.X.|..X..|.X.X.|X...X|X...X",
        "Y": "X...X|X...X|.X.X.|..X..|..X..|..X..|..X..",
        "Z": "XXXXX|....X|...X.|..X..|.X...|X....|XXXXX",
        "a": ".....|.....|.XXX.|....X|.XXXX|X...X|.XXXX",
        "b": "X....|X....|XXXX.|X...X|X...X|X...X|XXXX.",
        "c": ".....|.....|.XXX.|X....|X....|X...X|.XXX.",
        "d": "....X|....X|.XXXX|X...X|X...X|X...X|.XXXX",
        "e": ".....|.....|.XXX.|X...X|XXXXX|X....|.XXX.",
        "f": "..XX.|.X..X|.X...|XXX..|.X...|.X...|.X...",
        "g": ".....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|.XXX.",
        "h": "X....|X....|XXXX.|X...X|X...X|X...X|X...X",
        "i": ".X.|...|XX.|.X.|.X.|.X.|XXX",
        "j": "...X|....|..XX|...X|...X|...X|...X|X..X|.XX.",
        "k": "X...|X...|X..X|X.X.|XX..|X.X.|X..X",
        "l": "XX.|.X.|.X.|.X.|.X.|.X.|XXX",
        "m": ".....|.....|XX.X.|X.X.X|X.X.X|X.X.X|X.X.X",
        "n": ".....|.....|XXXX.|X...X|X...X|X...X|X...X",
        "o": ".....|.....|.XXX.|X...X|X...X|X...X|.XXX.",
        "p": ".....|.....|XXXX.|X...X|X...X|X...X|XXXX.|X....|X....",
        "q": ".....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|....X",
        "r": ".....|.....|X.XX.|XX..X|X....|X....|X....",
        "s": ".....|.....|.XXXX|X....|.XXX.|....X|XXXX.",
        "t": ".X...|.X...|XXX..|.X...|.X...|.X..X|..XX.",
        "u": ".....|.....|X...X|X...X|X...X|X..XX|.XX.X",
        "v": ".....|.....|X...X|X...X|X...X|.X.X.|..X..",
        "w": ".....|.....|X...X|X...X|X.X.X|X.X.X|.X.X.",
        "x": ".....|.....|X...X|.X.X.|..X..|.X.X.|X...X",
        "y": ".....|.....|X...X|X...X|X...X|X...X|.XXXX|....X|.XXX.",
        "z": ".....|.....|XXXXX|...X.|..X..|.X...|XXXXX",
        "0": ".XXX.|X...X|X..XX|X.X.X|XX..X|X...X|.XXX.",
        "1": "..X..|.XX..|..X..|..X..|..X..|..X..|.XXX.",
        "2": ".XXX.|X...X|....X|...X.|..X..|.X...|XXXXX",
        "3": "XXXX.|....X|....X|.XXX.|....X|....X|XXXX.",
        "4": "...X.|..XX.|.X.X.|X..X.|XXXXX|...X.|...X.",
        "5": "XXXXX|X....|XXXX.|....X|....X|X...X|.XXX.",
        "6": ".XXX.|X....|X....|XXXX.|X...X|X...X|.XXX.",
        "7": "XXXXX|....X|...X.|..X..|.X...|.X...|.X...",
        "8": ".XXX.|X...X|X...X|.XXX.|X...X|X...X|.XXX.",
        "9": ".XXX.|X...X|X...X|.XXXX|....X|....X|.XXX.",
        ".": "X|X",
        "!": "X|X|X|X|X|.|X",
        "-": "...|...|...|XXX|...|...|...",
        "'": "X|X",
        "&": ".XX..|X..X.|.XX..|.X...|X.X.X|X..X.|.XX.X",
    ]

    private struct Glyph {
        var rows: [[Bool]]
        var width: Int
        var top: Int
    }

    private static func glyph(_ ch: Character) -> Glyph? {
        if ch == " " { return Glyph(rows: [], width: 3, top: 0) }
        guard let src = glyphs[ch] else { return nil }
        var rows = src.split(separator: "|", omittingEmptySubsequences: false).map { $0.map { $0 == "X" } }
        let top = ch == "." ? 5 : 0
        let width = rows.map(\.count).max() ?? 0
        rows = rows.map { $0 + Array(repeating: false, count: width - $0.count) }
        return Glyph(rows: rows, width: width, top: top)
    }

    static func layout(_ text: String) -> CrossStitchInputLayout {
        let chars = Array(Array(text).prefix(maxLetters))
        let gl: [Glyph?] = chars.map { glyph($0) ?? glyph(Character(String($0).uppercased())) }
        func advance(_ from: Int, _ to: Int) -> Int {
            var w = 0
            if from < to { for i in from..<to { w += (gl[i]?.width ?? 0) + (i < to - 1 ? 1 : 0) } }
            return w
        }
        var lines: [(Int, Int)] = [(0, chars.count)]
        if advance(0, chars.count) > lineCols && chars.count > 1 {
            var best = 1
            var bestScore = Int.max
            for s in 1..<chars.count {
                let a = advance(0, s)
                let b = advance(chars[s] == " " ? s + 1 : s, chars.count)
                let score = max(a, b) - (chars[s] == " " ? 3 : 0)
                if score < bestScore {
                    bestScore = score
                    best = s
                }
            }
            lines = [(0, best), (chars[best] == " " ? best + 1 : best, chars.count)]
        }
        var out = CrossStitchInputLayout()
        out.first = Array(repeating: 0, count: chars.count)
        out.count = Array(repeating: 0, count: chars.count)
        out.col = Array(repeating: 0, count: chars.count)
        out.row = Array(repeating: 0, count: chars.count)
        // One line: capitals on rows -3..3 (centred). Two lines: capitals on rows -8..-2 and 1..7.
        let tops = lines.count == 1 ? [-3] : [-8, 1]
        for (li, line) in lines.enumerated() {
            let w = advance(line.0, line.1)
            var col = -Int((Double(w) / 2).rounded(.down))
            let rowTop = tops[li]
            guard line.0 < line.1 else { continue }
            for i in line.0..<line.1 {
                out.first[i] = out.cx.count
                out.col[i] = Double(col)
                out.row[i] = Double(rowTop)
                if let g = gl[i] {
                    var leftToRight = true
                    for r in g.rows.indices {
                        var cols: [Int] = []
                        for c in 0..<g.width where g.rows[r][c] { cols.append(c) }
                        if cols.isEmpty { continue }
                        if !leftToRight { cols.reverse() }
                        for c in cols {
                            out.cx.append(Double(col + c))
                            out.cy.append(Double(rowTop + g.top + r))
                            out.glyphOf.append(i)
                        }
                        leftToRight.toggle()
                    }
                    col += g.width + 1
                }
                out.count[i] = out.cx.count - out.first[i]
            }
        }
        return out
    }

    /// The sampler's printed border, worked before the name: a row of little hearts above and a
    /// zig-zag below, in cell units.
    static func border() -> (cx: [Double], cy: [Double]) {
        var cx: [Double] = []
        var cy: [Double] = []
        let heart = [".X.X.", "XXXXX", ".XXX.", "..X.."]
        for k in -1...1 {
            let ox = k * 7 - 2
            for (r, row) in heart.enumerated() {
                for (c, ch) in row.enumerated() where ch == "X" {
                    cx.append(Double(ox + c))
                    cy.append(Double(-12 + r))
                }
            }
        }
        for c in -9...9 {
            cx.append(Double(c))
            cy.append(Double(10 + (abs(c) % 2 == 0 ? 0 : 1)))
        }
        return (cx, cy)
    }
}
