import SwiftUI

/// ncm-theme.makeLayout, including the short-screen hero fit and accessible text wrap.
struct NeonContourMoodLayout {
    let width: Double
    let height: Double
    let scale: Double
    let fontScale: Double
    let sheetY: Double
    let titleY: Double
    let titleLines: [String]
    let questionLines: [String]
    let endpointLines: [[String]]
    let endpointStacked: Bool
    let heroY: Double
    let heroScale: Double
    let tagY: Double
    let questionY: Double
    let sliderY: Double
    let endpointY: Double
    let buttonY: Double
    let buttonHeight: Double
    let closeY: Double
    var heroTop: Double { titleY + 14 * scale }
    var heroBottom: Double { tagY - 18 * scale }
    var firstX: Double { 70 * scale }
    var lastX: Double { 340 * scale }
    var sliderX: Double { 37 * scale }
    var sliderWidth: Double { 336 * scale }
    var sliderHeight: Double { 60 * scale }
    var closeX: Double { 341 * scale }

    init(width: Double, height: Double, top: Double, bottom: Double, fontScale: Double) {
        let s = width / 402, fs = max(1, fontScale), expanded = fs > 1.2
        let extra = expanded ? (fs - 1) * 380 * s : 0
        let stageH = expanded ? max(height, 735 * s + top + bottom + extra) : height
        let bottomShift = stageH - 874 * s - (bottom - 34 * s)
        let title = top + 49 * s + (fs > 1.6 ? 48 * s : 0)
        let bh = 60 * s * max(1, fs * 0.7)
        let by = 768 * s + bottomShift - (bh - 60 * s)
        let questions = Self.split("What's your market mood?", fs > 1.6 ? 4 : fs > 1.2 ? 3 : 2)
        let stacked = fs > 2.4
        let ends = [Self.split("EXTREME FEAR", fs > 1.2 && !stacked ? 2 : 1),
                    Self.split("EXTREME GREED", fs > 1.2 && !stacked ? 2 : 1)]
        let ey = by - 35 * s - Double(stacked ? 1 : ends[0].count - 1) * 17 * s * fs
        let sy = expanded ? ey - 32 * s - 60 * s : 650 * s + bottomShift
        let qy = expanded ? sy - 38 * s - Double(questions.count - 1) * 34 * s * fs : 584 * s + bottomShift
        let ty = expanded ? qy - 28 * s * fs - 36 * s : 536 * s + bottomShift
        let hs = max(0.25, min(s, (ty - title + 28 * s) / 431))
        self.width = width; self.height = stageH; scale = s; self.fontScale = fs
        sheetY = top - 6 * s; titleY = title; titleLines = Self.split("Market Feelings", fs > 1.6 ? 2 : 1)
        questionLines = questions; endpointLines = ends; endpointStacked = stacked
        heroScale = hs; heroY = ty - 208 * hs; tagY = ty; questionY = qy
        sliderY = sy; endpointY = expanded ? ey : 733 * s + bottomShift
        buttonY = by; buttonHeight = bh; closeY = top + 20 * s
    }
    /// RCTAccessibilityManager's exact multipliers (not UIFont's body-size ratios).
    static func fontMultiplier(_ size: DynamicTypeSize) -> Double {
        switch size {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
    static func split(_ text: String, _ lines: Int) -> [String] {
        let words = text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        let count = max(1, min(lines, words.count))
        if count <= 1 { return [words.joined(separator: " ")] }
        var best: [String] = [], bestMax = Int.max, bestLengths: [Int] = []
        func walk(_ start: Int, _ left: Int, _ acc: [String]) {
            if left == 1 {
                let runs = acc + [words[start...].joined(separator: " ")], lengths = runs.map(\.count)
                let longest = lengths.max() ?? 0
                let longerFirst = zip(lengths, bestLengths).first(where: { $0 != $1 }).map { $0.0 > $0.1 } ?? false
                if longest < bestMax || (longest == bestMax && longerFirst) {
                    best = runs; bestMax = longest; bestLengths = lengths
                }
                return
            }
            for end in (start + 1)...(words.count - left + 1) {
                walk(end, left - 1, acc + [words[start..<end].joined(separator: " ")])
            }
        }
        walk(0, count, [])
        return best
    }
}
