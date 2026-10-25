import Foundation

/*
 Chalkboard Search — the loop script and the menu.

 loop = 9 s
   0.0 – 1.2   rest: the board, a faint "search the menu…" in chalk on the search line.
   1.0 – 1.2   the placeholder rubs away.
   1.2 – 3.3   "lemon tart" is chalked letter by letter (0.21 s a glyph, the pen easing through
               each), dust falling from under every stroke (gravity 320 pt/s², 0.9 s).
   3.4 – 4.45  three menu lines are chalked in, 0.3 s apart, 0.55 s each.
   4.45 – 6.0  hold.
   6.0 – 7.2   a felt eraser sweeps the board in four passes (ease-in-out), wiping the chalk and
               leaving a haze where it went.
   7.2 – 8.6   the haze clears; 7.6 – 8.4 the placeholder comes back.
   8.6 – 9.0   rest.
*/
enum ChalkboardSearchMotion {
    static let query = "lemon tart"
    static let qAt = 1.2
    static let qStep = 0.21
    static let sugAt = 3.4
    static let sugStep = 0.3
    static let sugLen = 0.55
    static let eraseAt = 6.0
    static let eraseEnd = 7.2
    private static let hazeEnd = 8.6
    static let dustLife = 0.9
    static let dustPerGlyph = 8

    struct Item {
        let name: String
        let price: Int
    }

    static let menu: [Item] = [
        Item(name: "Lemon tart", price: 6),
        Item(name: "Lemon curd scone", price: 4),
        Item(name: "Tarte au citron", price: 7),
        Item(name: "Flat white", price: 4),
        Item(name: "Cardamom bun", price: 5),
        Item(name: "Oat milk latte", price: 5),
        Item(name: "Sourdough toast", price: 8),
        Item(name: "Almond croissant", price: 5),
        Item(name: "Rhubarb crumble", price: 6),
        Item(name: "Fresh mint tea", price: 3),
        Item(name: "Mushroom tartine", price: 11),
        Item(name: "Spiced chai", price: 4),
        Item(name: "Pear & ginger cake", price: 6),
        Item(name: "Iced lemonade", price: 4),
        Item(name: "Shakshuka", price: 12),
        Item(name: "Butter scone", price: 3),
    ]

    static func itemLine(_ i: Item) -> String { "\(i.name) · $\(i.price)" }

    static let autoSuggestions = [itemLine(menu[0]), itemLine(menu[1]), itemLine(menu[2])]

    /// Up to three menu lines for a query: items sharing a word stem, best matches first.
    static func suggest(_ query: String) -> [String] {
        let lower = query.lowercased()
        var words: [String] = []
        var cur = ""
        for ch in lower {
            let isWordChar = ("a"..."z").contains(ch) || ("à"..."ÿ").contains(ch)
            if isWordChar {
                cur.append(ch)
            } else {
                words.append(cur)
                cur = ""
            }
        }
        words.append(cur)
        words = words.filter { $0.count >= 2 }
        if words.isEmpty { return [] }
        var scored: [(Int, Int, Item)] = []
        for (idx, item) in menu.enumerated() {
            let name = item.name.lowercased()
            let parts = name.split(whereSeparator: { $0.isWhitespace }).map(String.init)
            var score = 0
            for w in words {
                if name.hasPrefix(w) {
                    score += 3
                } else if parts.contains(where: { $0.hasPrefix(String(w.prefix(max(3, w.count - 1)))) }) {
                    score += 2
                } else if name.contains(w) {
                    score += 1
                }
            }
            if score > 0 { scored.append((score, idx, item)) }
        }
        // A stable sort, best first, as Array.prototype.sort is in JavaScript.
        scored.sort { $0.0 != $1.0 ? $0.0 > $1.0 : $0.1 < $1.1 }
        return scored.prefix(3).map { itemLine($0.2) }
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * x) }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// When glyph i of the loop's query starts to be chalked.
    static func glyphAt(_ i: Int) -> Double { qAt + Double(i) * qStep }

    /// The pen's x on the query line at loop time t, from the glyph edges.
    static func autoPen(_ t: Double, lefts: [Double], rights: [Double]) -> Double {
        if lefts.isEmpty || t < qAt { return lefts.first ?? 0 }
        let i = min(lefts.count - 1, Int(floor((t - qAt) / qStep)))
        let u = easeInOutSine(clamp01((t - glyphAt(i)) / qStep))
        return lefts[i] + (rights[i] - lefts[i]) * u
    }

    static func autoBoard(_ t: Double, lefts: [Double], rights: [Double]) -> ChalkboardSearchBoard {
        let sug = (0..<3).map { j in easeInOutSine(seg(t, sugAt + Double(j) * sugStep, sugAt + Double(j) * sugStep + sugLen)) }
        let erasing = t >= eraseAt
        return ChalkboardSearchBoard(
            pen: t >= eraseEnd ? (lefts.first ?? 0) : autoPen(t, lefts: lefts, rights: rights),
            sug: t >= eraseEnd ? [0, 0, 0] : sug,
            trail: erasing && t < hazeEnd ? easeInOutCubic(seg(t, eraseAt, eraseEnd)) : 0,
            eraserOn: erasing ? clamp01((t - eraseAt) / 0.15) * (1 - seg(t, eraseEnd - 0.15, eraseEnd)) : 0,
            haze: erasing ? clamp01((t - eraseAt) / 0.2) * (1 - easeInOutSine(seg(t, eraseEnd, hazeEnd))) : 0,
            placeholder: max(1 - seg(t, 1.0, 1.2), easeInOutSine(seg(t, 7.6, 8.4)))
        )
    }
}

struct ChalkboardSearchBoard {
    /// Pen x on the query line, and each suggestion line's reveal 0…1.
    var pen: Double
    var sug: [Double]
    /// How far along its path the eraser has wiped 0…1, the eraser's opacity, the haze's opacity.
    var trail: Double
    var eraserOn: Double
    var haze: Double
    var placeholder: Double
}

/// A timed tween of one number (Reanimated's `withTiming` with its default in-out quad easing).
struct ChalkboardSearchTween {
    private(set) var from: Double
    private(set) var to: Double
    private var start = 0.0
    private var duration = 0.0

    init(_ v: Double) {
        from = v
        to = v
    }

    func value(_ now: Double) -> Double {
        if duration <= 0 { return to }
        let u = ChalkboardSearchMotion.clamp01((now - start) / duration)
        let e = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
        return from + (to - from) * e
    }

    mutating func set(_ v: Double) {
        from = v
        to = v
        duration = 0
    }

    mutating func animate(to v: Double, duration d: Double, now: Double) {
        from = value(now)
        to = v
        start = now
        duration = d
    }

    /// True once the value has reached its target.
    func settled(_ now: Double) -> Bool { duration <= 0 || now - start >= duration }
}
