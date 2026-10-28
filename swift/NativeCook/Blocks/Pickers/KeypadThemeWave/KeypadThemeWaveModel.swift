import Foundation

/*
 Keypad Theme Wave — the pure part: palettes, the keypad, the strip's layout, the timing of a theme change
 and the autoPlay timeline. Every value the screen draws is a function of these and of the time.
*/

/// One calculator theme: the card, three key roles (digit, function, operator), the display ink and the ring
/// colour a key shows while it is hollow.
struct KeypadThemeWaveTheme {
    let name: String
    let bg: UInt32
    let digit: UInt32
    let digitInk: UInt32
    let fn: UInt32
    let fnInk: UInt32
    let op: UInt32
    let opInk: UInt32
    let display: UInt32
    let ring: UInt32

    func fill(_ role: Int) -> UInt32 { role == 1 ? fn : role == 2 ? op : digit }
    func ink(_ role: Int) -> UInt32 { role == 1 ? fnInk : role == 2 ? opInk : digitInk }
}

/// A theme change: when, to which theme, and from which side (+1 the new swatch came from the right).
struct KeypadThemeWaveChange {
    var at: Double
    var to: Int
    var dir: Int
}

/// What one frame shows.
struct KeypadThemeWaveScene {
    var now = 0.0
    var base = KeypadThemeWaveModel.home
    var changes: [KeypadThemeWaveChange] = []
    /// The strip's position in swatches (wraps every `themes.count`).
    var scroll = Double(KeypadThemeWaveModel.home)
    var display = "0"
    /// The time each key was last pressed (index row * 5 + column).
    var presses = [Double](repeating: -1e9, count: 20)
    var reduced = false
}

enum KeypadThemeWaveModel {
    static let loop = 10.0

    static let themes: [KeypadThemeWaveTheme] = [
        .init(name: "Paper", bg: 0xE8E7EA, digit: 0xBEBDC3, digitInk: 0x2C2C31, fn: 0xBEBDC3, fnInk: 0x2C2C31,
              op: 0x8F8E95, opInk: 0xFFFFFF, display: 0x2C2C31, ring: 0x75747B),
        .init(name: "Classic", bg: 0xDCD8D0, digit: 0xA9A49B, digitInk: 0x26241F, fn: 0xF0532F, fnInk: 0xFFFFFF,
              op: 0x383532, opInk: 0xFFFFFF, display: 0x1F1D1A, ring: 0x5E5A53),
        .init(name: "Garden", bg: 0x23A05A, digit: 0xF5F2E6, digitInk: 0x1F5F38, fn: 0xF26A2A, fnInk: 0xFFFFFF,
              op: 0xE3392F, opInk: 0xFFFFFF, display: 0xFFFFFF, ring: 0xE6F6EA),
        .init(name: "Marigold", bg: 0xF8B33C, digit: 0xFFF8EA, digitInk: 0x3A2A12, fn: 0xE2432D, fnInk: 0xFFFFFF,
              op: 0x2B6FBE, opInk: 0xFFFFFF, display: 0xD93A22, ring: 0x7A4E0E),
        .init(name: "Lilac", bg: 0xD0C2F9, digit: 0x9B7CF1, digitInk: 0xFFFFFF, fn: 0x9B7CF1, fnInk: 0xFFFFFF,
              op: 0xCFF24B, opInk: 0x3D3470, display: 0x5B46C2, ring: 0x6A55C9),
        .init(name: "Sky", bg: 0x6CCAF4, digit: 0x2F9AD5, digitInk: 0xF2FAFF, fn: 0x2F9AD5, fnInk: 0xF2FAFF,
              op: 0xF26C22, opInk: 0xFFFFFF, display: 0x145C88, ring: 0x1F6F9C),
        .init(name: "Mint", bg: 0x7DE8D6, digit: 0x1DA388, digitInk: 0xF2FFFB, fn: 0x137E69, fnInk: 0xFFFFFF,
              op: 0x55D3B9, opInk: 0x0F5A4B, display: 0x0F5A4B, ring: 0x167D6A),
        .init(name: "Meadow", bg: 0x68D877, digit: 0x41BC55, digitInk: 0xF3FFF4, fn: 0x2E9C43, fnInk: 0xF3FFF4,
              op: 0xA6EC7A, opInk: 0x1F5A28, display: 0x1F5A28, ring: 0x2B8A3C),
        .init(name: "Lemon", bg: 0xFADA5E, digit: 0xF6A623, digitInk: 0xFFFFFF, fn: 0xF6A623, fnInk: 0xFFFFFF,
              op: 0xE8562A, opInk: 0xFFFFFF, display: 0x8A5A00, ring: 0xA8740E),
        .init(name: "Night", bg: 0x1D1C1B, digit: 0x3A3936, digitInk: 0xF3F1EC, fn: 0x5A5854, fnInk: 0xF3F1EC,
              op: 0x8E8B85, opInk: 0x1D1C1B, display: 0xF3F1EC, ring: 0x8A8782),
    ]
    static var count: Int { themes.count }
    /// The resting theme: Classic.
    static let home = 1

    // MARK: Keypad

    /// Labels, row by row; "" is the wordmark's cell.
    static let labels = [
        ["AC", "7", "8", "9", "÷"],
        ["±", "4", "5", "6", "×"],
        ["%", "1", "2", "3", "−"],
        ["", "0", ".", "=", "+"],
    ]

    /// 0 digit, 1 function (AC ± %), 2 operator (÷ × − + =).
    static func role(_ r: Int, _ c: Int) -> Int {
        if c == 0 { return 1 }
        if c == 4 || (r == 3 && c == 3) { return 2 }
        return 0
    }

    static func isKey(_ r: Int, _ c: Int) -> Bool { !(r == 3 && c == 0) }

    static let keyRadius = 25.0
    static let pitch = 59.0

    // MARK: Timing of a change

    /// How long one key takes to drain, stand hollow and fill again.
    static let keyDur = 0.64
    static let keyLead = 0.08
    static let keyStagger = 0.06
    static let revealDur = 0.62
    /// After this a change is complete everywhere and can become the base.
    static let settle = 1.3
    static let reducedDur = 0.25

    /// The diagonal a key sits on, counted from the corner the colour comes from (0 … 7).
    static func diagonal(_ r: Int, _ c: Int, _ dir: Int) -> Int {
        (3 - r) + (dir > 0 ? 4 - c : c)
    }

    static func keyDelay(_ r: Int, _ c: Int, _ dir: Int) -> Double {
        keyLead + keyStagger * Double(diagonal(r, c, dir))
    }

    /// One key's transition: the theme it leaves, the one it goes to, and its progress (1 = done).
    static func keyState(_ s: KeypadThemeWaveScene, _ r: Int, _ c: Int) -> (from: Int, to: Int, u: Double) {
        var from = s.base
        var to = s.base
        var u = 1.0
        var prev = s.base
        for ch in s.changes {
            let start = ch.at + (s.reduced ? 0 : keyDelay(r, c, ch.dir))
            if s.now >= start {
                from = prev
                to = ch.to
                u = min(1, (s.now - start) / (s.reduced ? reducedDur : keyDur))
            }
            prev = ch.to
        }
        return (from, to, u)
    }

    /// What a key draws at progress u of its change.
    struct Look {
        var oldR = 0.0       // the old fill, draining to the centre
        var oldLabel = 0.0   // alpha of the old label
        var ring = 0.0       // alpha of the hollow ring
        var ringLabel = 0.0  // alpha of the label in the ring colour
        var newR = 0.0       // the new fill, blooming from the centre
        var newLabel = 0.0   // alpha of the new label
        var scale = 1.0
        var mix = 1.0        // reduced motion: crossfade from old to new
    }

    static func look(_ u: Double, reduced: Bool) -> Look {
        var l = Look()
        let R = keyRadius
        if reduced {
            l.mix = easeInOutCubic(u)
            l.newR = R
            l.newLabel = 1
            return l
        }
        if u >= 1 {
            l.newR = R
            l.newLabel = 1
            return l
        }
        let drain = easeInCubic(seg(u, 0, 0.28))
        l.oldR = R * (1 - drain)
        l.oldLabel = 1 - seg(u, 0, 0.15)
        let v = seg(u, 0.46, 1)
        let grow = easeOutBack(v, 1.9)
        l.newR = R * grow
        l.ring = seg(u, 0.06, 0.2) * (1 - seg(grow, 0.85, 1))
        let swap = seg(v, 0.25, 0.6)
        l.ringLabel = seg(u, 0.12, 0.26) * (1 - swap)
        l.newLabel = swap
        let dip = easeOutCubic(seg(u, 0, 0.28))
        l.scale = 1 - 0.08 * dip * (1 - grow)
        return l
    }

    /// The press dip of a key tapped `tau` seconds ago: 0 at rest, 1 fully pressed.
    static func press(_ tau: Double) -> Double {
        if tau < 0 || tau > 0.5 { return 0 }
        if tau < 0.07 { return easeOutCubic(tau / 0.07) }
        return 1 - easeOutBack(seg(tau, 0.07, 0.4), 1.6)
    }

    /// The reveal radius of a change `tau` seconds old, as a fraction of the card's diagonal.
    static func reveal(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        let x = min(1, tau / revealDur)
        return (1 - cos(.pi * x)) / 2
    }

    // MARK: The strip

    static let swatchPitch = 34.0

    /// Distance of swatch i from the centre, in swatches, wrapped into [−count/2, count/2).
    static func wrap(_ d: Double) -> Double {
        let n = Double(count)
        var x = (d + n / 2).truncatingRemainder(dividingBy: n)
        if x < 0 { x += n }
        return x - n / 2
    }

    static func mod(_ i: Int) -> Int { ((i % count) + count) % count }

    /// Horizontal offset of a swatch d swatches from the centre (the centred one is wider and pushes the others).
    static func swatchX(_ d: Double) -> Double {
        let a = min(abs(d), 1)
        return d * swatchPitch + (d < 0 ? -1 : 1) * a * 8
    }

    // MARK: Springs

    /// An underdamped spring from x0 with speed v0 towards 0, after tau seconds.
    static func spring(_ x0: Double, _ v0: Double, _ tau: Double, response: Double = 0.5, damping: Double = 0.8) -> Double {
        if tau <= 0 { return x0 }
        let w = 2 * .pi / response
        let z = damping
        let wd = w * sqrt(1 - z * z)
        let e = exp(-z * w * tau)
        return e * (x0 * cos(wd * tau) + ((v0 + z * w * x0) / wd) * sin(wd * tau))
    }

    static func springVelocity(_ x0: Double, _ v0: Double, _ tau: Double) -> Double {
        let h = 0.001
        return (spring(x0, v0, tau + h) - spring(x0, v0, max(0, tau - h))) / (tau + h - max(0, tau - h))
    }

    // MARK: autoPlay

    /// The strip's moves: start time, from, to (unwrapped swatch positions).
    static let autoMoves: [(at: Double, from: Double, to: Double)] = [
        (1.0, 1, 3),   // Classic → Marigold
        (3.2, 3, 4),   // → Lilac
        (5.2, 4, 6),   // → Mint
        (7.2, 6, 1),   // back to Classic
    ]
    /// Each move sends its wave a moment after the strip starts to travel.
    static let waveAfterMove = 0.15

    static var autoChanges: [KeypadThemeWaveChange] {
        autoMoves.map { m in
            KeypadThemeWaveChange(at: m.at + waveAfterMove, to: mod(Int(m.to)), dir: m.to > m.from ? 1 : -1)
        }
    }

    /// Strip position and speed at loop time t.
    static func autoScroll(_ t: Double) -> (s: Double, v: Double, target: Double) {
        var s = autoMoves[0].from
        var v = 0.0
        var target = s
        for m in autoMoves where t >= m.at {
            let x0 = m.from - m.to
            s = m.to + spring(x0, 0, t - m.at)
            v = springVelocity(x0, 0, t - m.at)
            target = m.to
        }
        return (s, v, target)
    }

    static func autoScene(_ t: Double) -> KeypadThemeWaveScene {
        var s = KeypadThemeWaveScene()
        s.now = t
        s.base = home
        s.changes = autoChanges.filter { $0.at <= t }
        s.scroll = autoScroll(t).s
        return s
    }

    // MARK: Curves

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double, _ s: Double) -> Double {
        let y = x - 1
        return 1 + (s + 1) * y * y * y + s * y * y
    }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func mixHex(_ a: UInt32, _ b: UInt32, _ t: Double) -> (Double, Double, Double) {
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 0xFF) / 255 }
        return (lerp(ch(a, 16), ch(b, 16), t), lerp(ch(a, 8), ch(b, 8), t), lerp(ch(a, 0), ch(b, 0), t))
    }
}

// MARK: The tiny calculator behind the preview's keys

struct KeypadThemeWaveCalc {
    var display = "0"
    var acc: Double?
    var op: String?
    var fresh = true

    static let maxDigits = 9

    var value: Double { Double(display.replacingOccurrences(of: "−", with: "-")) ?? 0 }

    mutating func clear() {
        display = "0"
        acc = nil
        op = nil
        fresh = true
    }

    mutating func press(_ key: String) {
        if display == "Error" && key != "AC" { clear() }
        switch key {
        case "AC":
            clear()
        case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9":
            if fresh || display == "0" {
                display = key
                fresh = false
            } else if digits < Self.maxDigits {
                display += key
            }
        case ".":
            if fresh {
                display = "0."
                fresh = false
            } else if !display.contains(".") && digits < Self.maxDigits {
                display += "."
            }
        case "±":
            if display == "0" { return }
            display = display.hasPrefix("−") ? String(display.dropFirst()) : "−" + display
        case "%":
            display = Self.format(value / 100)
        case "÷", "×", "−", "+":
            if let o = op, let a = acc, !fresh {
                display = Self.format(Self.apply(o, a, value))
            }
            acc = value
            op = key
            fresh = true
        case "=":
            if let o = op, let a = acc {
                display = Self.format(Self.apply(o, a, value))
                acc = nil
                op = nil
            }
            fresh = true
        default:
            break
        }
    }

    /// The delete tag: drops the last character.
    mutating func backspace() {
        if fresh || display == "Error" { return }
        display.removeLast()
        if display.isEmpty || display == "−" { display = "0" }
    }

    private var digits: Int { display.filter { $0.isNumber }.count }

    static func apply(_ op: String, _ a: Double, _ b: Double) -> Double {
        switch op {
        case "÷": return b == 0 ? .nan : a / b
        case "×": return a * b
        case "−": return a - b
        default: return a + b
        }
    }

    /// At most nine characters, the minus sign written as "−".
    static func format(_ x: Double) -> String {
        if !x.isFinite { return "Error" }
        var s: String?
        if x == x.rounded() && abs(x) < 1e9 {
            s = String(Int64(x))
        } else {
            for d in stride(from: 8, through: 0, by: -1) {
                var t = String(format: "%.\(d)f", x)
                if t.contains(".") {
                    while t.hasSuffix("0") { t.removeLast() }
                    if t.hasSuffix(".") { t.removeLast() }
                }
                if t.count <= 9 { s = t; break }
            }
        }
        guard var out = s else { return "Error" }
        if out == "-0" { out = "0" }
        return out.replacingOccurrences(of: "-", with: "−")
    }
}
