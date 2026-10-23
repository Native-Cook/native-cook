import Foundation

enum KeycapMoodInputGlyphs {
    private static let letters: [Character: [String]] = [
        "i": [".X.", "...", "XX.", ".X.", ".X.", "XXX"],
        "f": ["..XX", ".X..", "XXXX", ".X..", ".X..", ".X.."],
        "e": ["....", "....", ".XX.", "XXXX", "X...", ".XXX"],
        "l": ["XX.", ".X.", ".X.", ".X.", ".X.", "XXX"],
        "s": ["....", "....", ".XXX", "XX..", "..XX", "XXX."],
        "o": ["....", "....", ".XX.", "X..X", "X..X", ".XX."],
        "w": [".....", ".....", "X...X", "X.X.X", "X.X.X", ".X.X."],
        "t": ["....", ".X..", "XXXX", ".X..", ".X..", "..XX"],
        "v": [".....", ".....", "X...X", "X...X", ".X.X.", "..X.."],
        "r": ["....", "....", "X.XX", "XX..", "X...", "X..."],
        "y": ["....", "....", "X..X", "X..X", "X..X", ".XXX", "...X", ".XX."],
        "a": ["....", "....", ".XXX", "X..X", "X..X", ".XXX"],
        "b": ["X...", "X...", "XXX.", "X..X", "X..X", "XXX."],
        "u": ["....", "....", "X..X", "X..X", "X..X", ".XXX"]
    ]

    private static let faces = [
        [".XXX....XXX.", "X...X..X...X", "X...X..X...X", "............", "............", "X..........X", "X..........X", ".X........X.", "..X......X..", "...XXXXXX..."],
        [".X.X....X.X.", "XXXXX..XXXXX", "XXXXX..XXXXX", ".XXX....XXX.", "..X......X..", "............", "X..........X", ".X........X.", "..X......X..", "...XXXXXX..."],
        [".XXX....XXX.", ".X.X....X.X.", ".XXX....XXX.", "............", "............", "....XXXX....", "...X....X...", "...X....X...", "...X....X...", "....XXXX...."],
        ["............", "X...X..X...X", ".XXX....XXX.", "............", "............", "............", "............", "..X......X..", "...XXXXXX...", "............"],
        ["...X....X...", "..X......X..", "............", ".XX......XX.", ".XX......XX.", "............", "............", "...XXXXXX...", "..X......X..", ".X........X."],
        ["X..........X", ".XX......XX.", "...X....X...", ".XX......XX.", ".XX......XX.", "............", "............", "............", "..XXXXXXXX..", "............"]
    ]

    static func rows(key: Int, choice: Int) -> [Float] {
        if key == 3 {
            return faces[choice].map { row in
                Float(row.enumerated().reduce(0) { bits, pair in
                    bits | (pair.element == "X" ? 1 << pair.offset : 0)
                })
            }
        }
        let word = KeycapMoodInputPalette.words[key][choice]
        let characters = Array(word)
        var width = 0
        for i in characters.indices {
            let letter = characters[i]
            width += letter == " " ? 3 : letters[letter]![0].count
            if letter != " " && i < characters.count - 1 && characters[i + 1] != " " { width += 1 }
        }
        var rows = [Int](repeating: 0, count: 10)
        var cursor = 0
        for i in characters.indices {
            let letter = characters[i]
            if letter == " " { cursor += 3; continue }
            let image = letters[letter]!
            for y in image.indices {
                for (x, symbol) in image[y].enumerated() where symbol == "X" {
                    let column = cursor + x + Int(ceil(Double(24 - width) / 2))
                    if column >= 0 && column < 24 { rows[y + 1] |= 1 << column }
                }
            }
            cursor += image[0].count
            if i < characters.count - 1 && characters[i + 1] != " " { cursor += 1 }
        }
        return rows.map(Float.init)
    }
}
