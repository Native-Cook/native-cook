import SwiftUI

/// One view of the heat map: rows by columns of stress levels, with what the screen says about it.
struct StressDotGridDonutPeriod: Equatable {
    /// The tab's label.
    var label: String
    var range: String
    var rows: [String]
    var columns: [String]
    /// One string per row, one character per column: "." no data, "L" low, "N" normal, "H" high.
    var levels: [String]
    /// The cell of now, ringed: (row, column).
    var now: (row: Int, column: Int)
    /// What a counted cell is called, after its count.
    var unit: String
    var busiestTitle: String
    var busiest: String

    static func == (a: Self, b: Self) -> Bool {
        a.label == b.label && a.range == b.range && a.rows == b.rows && a.columns == b.columns && a.levels == b.levels
            && a.now == b.now && a.unit == b.unit && a.busiestTitle == b.busiestTitle && a.busiest == b.busiest
    }

    /// A day by two-hour periods: seven rows of twelve.
    static let days = StressDotGridDonutPeriod(
        label: "7 Days",
        range: "Oct 5, 2026 - Oct 11, 2026",
        rows: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
        columns: ["00", "02", "04", "06", "08", "10", "12", "14", "16", "18", "20", "22"],
        levels: [
            "HHL..NNN...N",
            "NHNH.HLLH.N.",
            "LLNHHLHNN...",
            "HNHLHLNL...N",
            "HHLNNLNLNH..",
            "LNL......HLL",
            "HNL.NH......",
        ],
        now: (6, 10),
        unit: "periods",
        busiestTitle: "Most stressful times",
        busiest: "0:00 and 2:00"
    )

    /// Four weeks by day: four rows of seven.
    static let weeks = StressDotGridDonutPeriod(
        label: "4 Weeks",
        range: "Sep 14, 2026 - Oct 11, 2026",
        rows: ["09/14 - 09/20", "09/21 - 09/27", "09/28 - 10/04", "10/05 - 10/11"],
        columns: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
        levels: ["NNNLNNN", "HNNNNLN", "NNHNHHN", "NNNNNLN"],
        now: (3, 6),
        unit: "days",
        busiestTitle: "Most stressful days",
        busiest: "Monday and Friday"
    )

    /// 0: no data, 1: low, 2: normal, 3: high.
    func level(_ row: Int, _ column: Int) -> Int {
        guard levels.indices.contains(row) else { return 0 }
        let marks = Array(levels[row])
        guard marks.indices.contains(column) else { return 0 }
        switch marks[column] {
        case "L": return 1
        case "N": return 2
        case "H": return 3
        default: return 0
        }
    }

    /// How many cells are low, normal and high.
    var counts: [Int] {
        var tally = [0, 0, 0]
        for r in rows.indices {
            for c in columns.indices {
                let level = level(r, c)
                if level > 0 { tally[level - 1] += 1 }
            }
        }
        return tally
    }
}

/// The colours of the levels, as sRGB components so a dot can mix straight from one to another.
enum StressDotGridDonutLevel {
    static let low: [Double] = [0xD4, 0xF5, 0x3C].map { Double($0) / 255 }
    static let normal: [Double] = [0x8E, 0x8C, 0xF7].map { Double($0) / 255 }
    static let high: [Double] = [0xFF, 0x5A, 0x3C].map { Double($0) / 255 }
    static let none: [Double] = [0x3A, 0x3A, 0x40].map { Double($0) / 255 }
    /// By level: no data, low, normal, high.
    static let rgb = [none, low, normal, high]

    static func color(_ rgb: [Double], opacity: Double = 1) -> Color {
        Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2], opacity: opacity)
    }
}
