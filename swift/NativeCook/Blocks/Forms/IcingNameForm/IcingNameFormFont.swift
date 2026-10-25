import Foundation

/*
 Icing Name Form — a monoline piping alphabet: every letter is the path a decorator's nozzle
 follows, written as strokes in font units (x-height 1, baseline 0, y down; capitals 1.45 tall).
 Commands: M x y · L x y · Q cx cy x y · C c1x c1y c2x c2y x y · A cx cy rx ry startDeg sweepDeg.
 Each M starts a new stroke (the nozzle lifts in between). The layout samples the strokes into
 polylines in pt, slants them like hand lettering and adds a tiny seeded wobble.
 */

/// A sampled stroke: points (x, y flat, pt), running length at each point, and its glyph.
struct IcingNameFormStroke {
    var pts: [Double]
    var len: [Double]
    var glyph: Int
    var full: Double { len.last ?? 0 }
}

struct IcingNameFormLettering {
    var strokes: [IcingNameFormStroke] = []
    /// Per glyph: its first stroke, stroke count and ink length.
    var firstStroke: [Int] = []
    var strokeCount: [Int] = []
    var glyphLength: [Double] = []
    /// Glyph origins (pt), used to glide letters when the layout changes.
    var originX: [Double] = []
    var originY: [Double] = []
    var total: Double = 0
    var size: Double = 0
}

enum IcingNameFormFont {
    static let maxLetters = 14
    private static let space = 0.55
    private static let track = 0.26
    private static let slant = 0.16

    private static let table: [Character: (Double, String)] = [
        "a": (1.1, "A 0.46 -0.5 0.44 0.5 -25 -335 M 0.93 -1 L 0.93 -0.12 Q 0.95 0.02 1.08 -0.04"),
        "b": (1.0, "M 0.08 -1.5 L 0.08 0 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359"),
        "c": (0.95, "A 0.5 -0.5 0.46 0.5 -40 -280"),
        "d": (1.05, "A 0.45 -0.5 0.44 0.5 0 359 M 0.92 -1.5 L 0.92 0"),
        "e": (1.0, "M 0.08 -0.5 L 0.93 -0.5 A 0.5 -0.5 0.44 0.5 0 -315"),
        "f": (0.8, "M 0.85 -1.38 C 0.72 -1.55 0.36 -1.52 0.36 -1.18 L 0.36 0 M 0.05 -0.98 L 0.74 -0.98"),
        "g": (1.05, "A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.22 C 0.9 0.62 0.3 0.66 0.08 0.36"),
        "h": (1.0, "M 0.08 -1.5 L 0.08 0 M 0.08 -0.62 C 0.18 -1.06 0.86 -1.1 0.86 -0.56 L 0.86 0"),
        "i": (0.36, "M 0.16 -1 L 0.16 0 M 0.16 -1.36 L 0.165 -1.35"),
        "j": (0.6, "M 0.46 -1 L 0.46 0.24 C 0.46 0.62 0.06 0.62 -0.04 0.4 M 0.46 -1.36 L 0.465 -1.35"),
        "k": (0.88, "M 0.08 -1.5 L 0.08 0 M 0.78 -1 L 0.1 -0.42 M 0.32 -0.6 L 0.82 0"),
        "l": (0.4, "M 0.16 -1.5 L 0.16 -0.14 Q 0.18 0.02 0.36 -0.03"),
        "m": (1.38, "M 0.07 -1 L 0.07 0 M 0.07 -0.64 C 0.14 -1.06 0.68 -1.1 0.68 -0.6 L 0.68 0 M 0.68 -0.6 C 0.74 -1.06 1.28 -1.1 1.28 -0.6 L 1.28 0"),
        "n": (0.96, "M 0.08 -1 L 0.08 0 M 0.08 -0.6 C 0.17 -1.06 0.86 -1.1 0.86 -0.55 L 0.86 0"),
        "o": (1.0, "A 0.5 -0.5 0.46 0.5 -90 359"),
        "p": (1.0, "M 0.08 -1 L 0.08 0.5 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359"),
        "q": (1.0, "A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.5"),
        "r": (0.75, "M 0.08 -1 L 0.08 0 M 0.08 -0.55 C 0.17 -0.96 0.46 -1.06 0.72 -0.9"),
        "s": (0.92, "M 0.8 -0.88 C 0.65 -1.06 0.1 -1.05 0.12 -0.75 C 0.14 -0.48 0.82 -0.55 0.84 -0.25 C 0.86 0.05 0.25 0.07 0.07 -0.12"),
        "t": (0.75, "M 0.36 -1.35 L 0.36 -0.15 Q 0.38 0.02 0.66 -0.05 M 0.05 -0.98 L 0.7 -0.98"),
        "u": (0.96, "M 0.08 -1 L 0.08 -0.4 C 0.08 0.08 0.86 0.08 0.86 -0.4 M 0.86 -1 L 0.86 0"),
        "v": (0.9, "M 0.02 -1 L 0.45 0 L 0.88 -1"),
        "w": (1.32, "M 0.02 -1 L 0.33 0 L 0.66 -0.74 L 0.98 0 L 1.3 -1"),
        "x": (0.86, "M 0.06 -1 L 0.8 0 M 0.8 -1 L 0.06 0"),
        "y": (0.9, "M 0.04 -1 L 0.47 0 M 0.88 -1 L 0.36 0.34 C 0.27 0.56 0.1 0.56 0.0 0.48"),
        "z": (0.92, "M 0.08 -1 L 0.82 -1 L 0.08 0 L 0.86 0"),
        "A": (1.12, "M 0 0 L 0.56 -1.45 L 1.12 0 M 0.22 -0.5 L 0.9 -0.5"),
        "B": (0.98, "M 0.08 0 L 0.08 -1.45 C 1.0 -1.5 1.0 -0.76 0.08 -0.76 C 1.12 -0.78 1.12 0.05 0.08 0"),
        "C": (1.25, "A 0.68 -0.725 0.62 0.725 -40 -280"),
        "D": (1.15, "M 0.08 0 L 0.08 -1.45 C 1.36 -1.45 1.36 0 0.08 0"),
        "E": (0.95, "M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 L 0.88 0 M 0.08 -0.75 L 0.7 -0.75"),
        "F": (0.9, "M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 M 0.08 -0.75 L 0.7 -0.75"),
        "G": (1.38, "A 0.7 -0.725 0.62 0.725 -40 -300 L 1.3 -0.72 L 0.86 -0.72"),
        "H": (1.1, "M 0.08 -1.45 L 0.08 0 M 1.02 -1.45 L 1.02 0 M 0.08 -0.75 L 1.02 -0.75"),
        "I": (0.36, "M 0.16 -1.45 L 0.16 0"),
        "J": (0.86, "M 0.72 -1.45 L 0.72 -0.36 C 0.72 0.1 0.06 0.12 0.03 -0.3"),
        "K": (1.02, "M 0.08 -1.45 L 0.08 0 M 0.96 -1.45 L 0.1 -0.6 M 0.38 -0.86 L 1.0 0"),
        "L": (0.9, "M 0.08 -1.45 L 0.08 0 L 0.86 0"),
        "M": (1.4, "M 0.05 0 L 0.12 -1.45 L 0.7 -0.4 L 1.28 -1.45 L 1.35 0"),
        "N": (1.14, "M 0.08 0 L 0.08 -1.45 L 1.04 0 L 1.04 -1.45"),
        "O": (1.45, "A 0.72 -0.725 0.66 0.725 -90 359"),
        "P": (0.96, "M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68"),
        "Q": (1.45, "A 0.72 -0.725 0.66 0.725 -90 359 M 0.86 -0.34 L 1.4 0.1"),
        "R": (1.02, "M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68 M 0.44 -0.68 L 0.98 0"),
        "S": (1.1, "M 1.0 -1.25 C 0.85 -1.5 0.12 -1.55 0.12 -1.08 C 0.12 -0.68 1.05 -0.8 1.05 -0.35 C 1.05 0.08 0.25 0.1 0.06 -0.18"),
        "T": (1.02, "M 0.01 -1.45 L 1.01 -1.45 M 0.51 -1.45 L 0.51 0"),
        "U": (1.12, "M 0.08 -1.45 L 0.08 -0.5 C 0.08 0.16 1.04 0.16 1.04 -0.5 L 1.04 -1.45"),
        "V": (1.1, "M 0 -1.45 L 0.55 0 L 1.1 -1.45"),
        "W": (1.5, "M 0 -1.45 L 0.38 0 L 0.75 -1.05 L 1.12 0 L 1.5 -1.45"),
        "X": (1.04, "M 0.03 -1.45 L 1.01 0 M 1.01 -1.45 L 0.03 0"),
        "Y": (1.04, "M 0 -1.45 L 0.52 -0.7 L 1.04 -1.45 M 0.52 -0.7 L 0.52 0"),
        "Z": (1.08, "M 0.05 -1.45 L 1.02 -1.45 L 0.05 0 L 1.03 0"),
        "0": (1.0, "A 0.5 -0.72 0.44 0.72 -90 359"),
        "1": (0.62, "M 0.08 -1.2 L 0.42 -1.45 L 0.42 0"),
        "2": (0.98, "M 0.1 -1.15 C 0.2 -1.55 0.92 -1.55 0.9 -1.08 C 0.88 -0.7 0.3 -0.42 0.06 0 L 0.94 0"),
        "3": (0.95, "M 0.12 -1.3 C 0.4 -1.6 0.94 -1.45 0.86 -1.1 C 0.8 -0.85 0.5 -0.78 0.36 -0.78 C 1.08 -0.74 1.0 0.1 0.36 0.02 C 0.2 0 0.1 -0.08 0.06 -0.16"),
        "4": (1.0, "M 0.72 0 L 0.72 -1.45 L 0.04 -0.42 L 0.98 -0.42"),
        "5": (0.95, "M 0.86 -1.45 L 0.2 -1.45 L 0.13 -0.82 C 0.6 -1.02 0.98 -0.78 0.92 -0.4 C 0.86 0.08 0.25 0.1 0.06 -0.16"),
        "6": (0.98, "M 0.82 -1.38 C 0.42 -1.62 0.06 -1.1 0.08 -0.52 A 0.5 -0.44 0.42 0.44 180 359"),
        "7": (0.95, "M 0.06 -1.45 L 0.92 -1.45 L 0.34 0"),
        "8": (0.98, "A 0.49 -1.1 0.34 0.35 90 359 M 0.49 -0.75 A 0.49 -0.375 0.42 0.375 -90 359"),
        "9": (0.98, "A 0.48 -1.0 0.42 0.44 0 359 M 0.9 -1.0 C 0.9 -0.3 0.6 0.08 0.14 -0.06"),
        ".": (0.32, "M 0.14 -0.06 L 0.145 -0.05"),
        ",": (0.32, "M 0.18 -0.06 L 0.08 0.22"),
        "!": (0.34, "M 0.16 -1.45 L 0.16 -0.42 M 0.16 -0.06 L 0.165 -0.05"),
        "?": (0.85, "M 0.08 -1.18 C 0.2 -1.6 0.86 -1.56 0.82 -1.1 C 0.8 -0.78 0.4 -0.76 0.4 -0.38 M 0.4 -0.06 L 0.405 -0.05"),
        "'": (0.3, "M 0.15 -1.45 L 0.13 -1.08"),
        "-": (0.62, "M 0.08 -0.5 L 0.54 -0.5"),
        "&": (1.1, "M 1.05 0 L 0.28 -0.9 C 0.1 -1.16 0.24 -1.45 0.48 -1.45 C 0.72 -1.45 0.82 -1.16 0.6 -0.96 L 0.2 -0.62 C -0.06 -0.36 0.1 0.04 0.44 0.02 C 0.7 0 0.86 -0.2 0.96 -0.5"),
        "♥": (1.1, "M 0.55 0 C 0.0 -0.4 0.0 -0.95 0.3 -1.0 C 0.45 -1.02 0.55 -0.9 0.55 -0.78 C 0.55 -0.9 0.65 -1.02 0.8 -1.0 C 1.1 -0.95 1.1 -0.4 0.55 0"),
    ]

    private static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898) * 43758.5453
        return s - floor(s)
    }

    /// Samples a glyph into strokes of points in font units.
    private static func sample(_ src: String) -> [[Double]] {
        let t = src.split(whereSeparator: { $0 == " " }).map(String.init)
        var strokes: [[Double]] = []
        var cur: [Double] = []
        var x = 0.0
        var y = 0.0
        var i = 0
        func push(_ px: Double, _ py: Double) {
            cur.append(px)
            cur.append(py)
            x = px
            y = py
        }
        func num() -> Double {
            defer { i += 1 }
            return Double(t[i]) ?? 0
        }
        while i < t.count {
            let cmd = t[i]
            i += 1
            switch cmd {
            case "M":
                if cur.count > 2 { strokes.append(cur) }
                cur = []
                let a = num(), b = num()
                push(a, b)
            case "L":
                let nx = num(), ny = num()
                let steps = max(1, Int(ceil(hypot(nx - x, ny - y) / 0.06)))
                let x0 = x, y0 = y
                for k in 1...steps {
                    let f = Double(k) / Double(steps)
                    push(x0 + (nx - x0) * f, y0 + (ny - y0) * f)
                }
            case "Q":
                let cx = num(), cy = num(), nx = num(), ny = num()
                let x0 = x, y0 = y
                for k in 1...16 {
                    let u = Double(k) / 16
                    push((1 - u) * (1 - u) * x0 + 2 * u * (1 - u) * cx + u * u * nx,
                         (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * cy + u * u * ny)
                }
            case "C":
                let c1x = num(), c1y = num(), c2x = num(), c2y = num(), nx = num(), ny = num()
                let x0 = x, y0 = y
                for k in 1...24 {
                    let u = Double(k) / 24
                    let v = 1 - u
                    push(v * v * v * x0 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * nx,
                         v * v * v * y0 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * ny)
                }
            case "A":
                let cx = num(), cy = num(), rx = num(), ry = num()
                let a0 = num() * .pi / 180
                let sw = num() * .pi / 180
                let steps = Int(ceil(abs(sw) / 0.12))
                push(cx + rx * cos(a0), cy + ry * sin(a0))
                if steps > 0 {
                    for k in 1...steps {
                        let a = a0 + sw * Double(k) / Double(steps)
                        push(cx + rx * cos(a), cy + ry * sin(a))
                    }
                }
            default:
                break
            }
        }
        if cur.count > 2 { strokes.append(cur) }
        return strokes
    }

    nonisolated(unsafe) private static var cache: [Character: (Double, [[Double]])] = [:]

    private static func glyph(_ ch: Character) -> (Double, [[Double]])? {
        if let g = cache[ch] { return g }
        guard let def = table[ch] ?? table[Character(String(ch).lowercased())] else { return nil }
        let out = (def.0, sample(def.1))
        cache[ch] = out
        return out
    }

    private static func lineWidth(_ chars: [Character]) -> Double {
        var w = 0.0
        for (i, ch) in chars.enumerated() {
            if ch == " " { w += space } else { w += (glyph(ch)?.0 ?? space) + (i < chars.count - 1 ? track : 0) }
        }
        return w
    }

    /// Lays the message out on the cake: one line, or two split at the space nearest the middle,
    /// fitted into a box `boxW` × `boxH` pt centred on the cake (cake-local coordinates).
    static func layout(_ text: String, boxW: Double, boxH: Double) -> IcingNameFormLettering {
        let chars = Array(Array(text).prefix(maxLetters))
        var lines: [[Character]] = [chars]
        let spaces = chars.indices.filter { chars[$0] == " " && $0 > 0 }
        if chars.count > 7, var best = spaces.first {
            let mid = Double(chars.count) / 2
            for s in spaces where abs(Double(s) - mid) < abs(Double(best) - mid) { best = s }
            lines = [Array(chars[..<best]), Array(chars[(best + 1)...])]
        }
        let widest = max(0.01, lines.map(lineWidth).max() ?? 0)
        let rows = Double(lines.count)
        let size = min(boxW / (widest + slant * 1.5), boxH / (rows * 1.5 + (rows - 1) * 0.85 + 0.4), 56)
        var out = IcingNameFormLettering()
        out.size = size
        let lineGap = 2.3 * size
        let blockH = (rows - 1) * lineGap
        var gi = 0
        var seedBase = 0.0
        for (li, line) in lines.enumerated() {
            let w = lineWidth(line) * size
            var x = -w / 2
            let base = -blockH / 2 + Double(li) * lineGap + size * 0.5
            for ch in line {
                let g = ch == " " ? nil : glyph(ch)
                out.firstStroke.append(out.strokes.count)
                out.originX.append(x)
                out.originY.append(base)
                var ink = 0.0
                if let g {
                    for src in g.1 {
                        var pts: [Double] = []
                        var len: [Double] = []
                        var run = 0.0
                        var k = 0
                        while k < src.count {
                            let fx = src[k], fy = src[k + 1]
                            let px = x + (fx - fy * slant) * size
                            let py = base + fy * size
                            let kd = Double(k)
                            let wob = (sin(kd * 0.21 + seedBase * 1.7) + 0.6 * sin(kd * 0.53 + hash(seedBase) * 6)) * size * 0.012
                            let qx = px + wob
                            let qy = py - wob * 0.6
                            if pts.count >= 2 { run += hypot(qx - pts[pts.count - 2], qy - pts[pts.count - 1]) }
                            pts.append(qx)
                            pts.append(qy)
                            len.append(run)
                            k += 2
                        }
                        seedBase += 1
                        out.strokes.append(IcingNameFormStroke(pts: pts, len: len, glyph: gi))
                        ink += max(run, 0.5)
                    }
                    x += (g.0 + track) * size
                } else {
                    x += (ch == " " ? space : space + track) * size
                }
                out.strokeCount.append(out.strokes.count - out.firstStroke[gi])
                out.glyphLength.append(ink)
                out.total += ink
                gi += 1
            }
        }
        return out
    }

    /// A piping schedule for scripted lettering: per stroke the fraction of the window at which it
    /// starts and ends, the nozzle travelling between strokes (lifted) in proportion to the distance.
    static func schedule(_ L: IcingNameFormLettering) -> (t0: [Double], t1: [Double]) {
        let ink = L.strokes.map(\.full)
        var travel: [Double] = []
        for i in L.strokes.indices {
            if i == 0 { travel.append(0) } else {
                let a = L.strokes[i - 1].pts
                let b = L.strokes[i].pts
                travel.append(0.35 * hypot(b[0] - a[a.count - 2], b[1] - a[a.count - 1]) + 6)
            }
        }
        var total = 0.0
        for i in ink.indices { total += ink[i] + travel[i] }
        total = max(total, 0.0001)
        var t0: [Double] = []
        var t1: [Double] = []
        var acc = 0.0
        for i in ink.indices {
            acc += travel[i]
            t0.append(acc / total)
            acc += ink[i]
            t1.append(acc / total)
        }
        return (t0, t1)
    }
}
