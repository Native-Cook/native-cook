import CoreGraphics
import Foundation

// The bent glass of the sign: every tube as a smooth curve, defined by hand (a port of the React
// Native block's neonSignTubes.ts, number for number).
//
// The script is not set from a font. Each letter is one or two tubes, each tube a list of knots
// (in x-height units: baseline at y = 0, the top of the x-height at y = -1, y down) that a
// centripetal Catmull-Rom spline runs through, the way a glass bender heats and bends a tube
// through a few points. The letters are then slanted and placed on the sign. The cocktail glass
// is a polyline with rounded bends, since tubes cannot take a sharp corner.
//
// A tube becomes a list of cubic segments `[x0, y0, c1x, c1y, c2x, c2y, x1, y1]` in sign points.

/// One lit piece of the sign: a letter, a part of the glass, or a swash.
struct NeonSignFlickerPiece {
    /// The word (or icon) it belongs to: 0 = "open", 1 = "late", 2 = the glass.
    var word: Int
    /// Index into the colour table of the sign: 0 rose, 1 ice cyan, 2 amber, 3 lime.
    var tone: Int
    /// Cubic segments per tube, in sign points (origin at the sign's centre).
    var tubes: [[Double]]
    /// Its box, in sign points.
    var box: CGRect
    /// Order in which it lights within its word, from 0.
    var order: Int
    /// Whether the glass is blacked out at the start and at the end of its first tube, where the
    /// tube turns through the panel to its electrode.
    var ends: (Bool, Bool)
}

enum NeonSignFlickerTubes {
    /// The size of one x-height unit on the sign, in pt.
    static let unit = 41.0
    /// Script slant: a knot one x-height up moves this far right.
    private static let slant = 0.24

    private typealias Knot = (Double, Double)

    private struct Glyph {
        let tubes: [[Knot]]
        let advance: Double
    }

    // Lowercase script, monoline, each letter starting near the baseline on the left and leaving
    // on the right towards the next one.
    private static let glyphs: [Character: Glyph] = [
        "o": Glyph(tubes: [[
            (0.62, -0.98), (0.3, -0.98), (0.07, -0.72), (0.04, -0.3), (0.24, -0.02), (0.52, -0.05),
            (0.72, -0.36), (0.71, -0.76), (0.6, -0.96), (0.49, -0.93), (0.56, -0.83), (0.78, -0.84),
            (1.0, -0.92),
        ]], advance: 1.02),
        "p": Glyph(tubes: [
            [(0.0, -0.9), (0.13, -1.03), (0.22, -0.9), (0.18, -0.3), (0.1, 0.36), (0.04, 0.8)],
            [(0.19, -0.6), (0.38, -0.92), (0.64, -1.0), (0.84, -0.78), (0.8, -0.34), (0.54, -0.04), (0.3, -0.06), (0.2, -0.2)],
        ], advance: 0.98),
        "e": Glyph(tubes: [[
            (0.0, -0.22), (0.3, -0.42), (0.56, -0.66), (0.58, -0.92), (0.38, -1.0), (0.14, -0.82),
            (0.06, -0.42), (0.2, -0.06), (0.48, 0.0), (0.76, -0.2),
        ]], advance: 0.82),
        "n": Glyph(tubes: [[
            (0.0, -0.2), (0.1, -0.62), (0.2, -0.98), (0.28, -0.92), (0.26, -0.5), (0.22, -0.04),
            (0.29, -0.55), (0.48, -0.92), (0.7, -0.98), (0.84, -0.76), (0.84, -0.3), (0.9, -0.04),
            (1.06, 0.0), (1.2, -0.16),
        ]], advance: 1.22),
        "l": Glyph(tubes: [[
            (0.0, -0.18), (0.3, -0.62), (0.56, -1.2), (0.62, -1.66), (0.5, -1.9), (0.34, -1.76),
            (0.26, -1.2), (0.22, -0.5), (0.28, -0.06), (0.5, 0.0), (0.7, -0.2),
        ]], advance: 0.72),
        "a": Glyph(tubes: [[
            (0.0, -0.22), (0.3, -0.64), (0.64, -0.9), (0.46, -1.0), (0.2, -0.86), (0.06, -0.46),
            (0.14, -0.06), (0.38, -0.02), (0.6, -0.32), (0.72, -0.72), (0.8, -1.0), (0.74, -0.56),
            (0.72, -0.12), (0.86, 0.0), (1.06, -0.18),
        ]], advance: 1.1),
        "t": Glyph(tubes: [
            [(0.0, -0.2), (0.22, -0.7), (0.4, -1.42), (0.47, -1.5), (0.43, -1.0), (0.35, -0.4), (0.39, -0.04), (0.58, 0.0), (0.78, -0.2)],
            [(0.12, -0.98), (0.44, -1.04), (0.82, -1.0)],
        ], advance: 0.8),
    ]

    /// Centripetal Catmull-Rom (alpha 0.5) through the knots, as cubic segments.
    private static func spline(_ points: [Knot]) -> [Double] {
        var out: [Double] = []
        let n = points.count
        func at(_ i: Int) -> Knot { points[max(0, min(n - 1, i))] }
        for i in 0..<(n - 1) {
            let p0 = at(i - 1)
            let p1 = at(i)
            let p2 = at(i + 1)
            let p3 = at(i + 2)
            let d1 = max(1e-4, hypot(p1.0 - p0.0, p1.1 - p0.1).squareRoot())
            let d2 = max(1e-4, hypot(p2.0 - p1.0, p2.1 - p1.1).squareRoot())
            let d3 = max(1e-4, hypot(p3.0 - p2.0, p3.1 - p2.1).squareRoot())
            // At the ends the missing neighbour is the knot itself: the curve leaves along the chord.
            let c1x: Double
            let c1y: Double
            let c2x: Double
            let c2y: Double
            if i == 0 {
                c1x = p1.0 + (p2.0 - p1.0) / 3
                c1y = p1.1 + (p2.1 - p1.1) / 3
            } else {
                let a = d1 * d1
                let b = d2 * d2
                let k = 2 * a + 3 * d1 * d2 + b
                let m = 3 * d1 * (d1 + d2)
                c1x = (a * p2.0 - b * p0.0 + k * p1.0) / m
                c1y = (a * p2.1 - b * p0.1 + k * p1.1) / m
            }
            if i == n - 2 {
                c2x = p2.0 - (p2.0 - p1.0) / 3
                c2y = p2.1 - (p2.1 - p1.1) / 3
            } else {
                let a = d3 * d3
                let b = d2 * d2
                let k = 2 * a + 3 * d3 * d2 + b
                let m = 3 * d3 * (d3 + d2)
                c2x = (a * p1.0 - b * p3.0 + k * p2.0) / m
                c2y = (a * p1.1 - b * p3.1 + k * p2.1) / m
            }
            out += [p1.0, p1.1, c1x, c1y, c2x, c2y, p2.0, p2.1]
        }
        return out
    }

    /// A polyline whose corners are bent round with radius `r` (same units), as cubic segments.
    private static func bentPolyline(_ points: [Knot], _ r: Double) -> [Double] {
        var out: [Double] = []
        func line(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double) {
            out += [ax, ay, ax + (bx - ax) / 3, ay + (by - ay) / 3, ax + 2 * (bx - ax) / 3, ay + 2 * (by - ay) / 3, bx, by]
        }
        var x = points[0].0
        var y = points[0].1
        for i in 1..<points.count {
            let (cx, cy) = points[i]
            if i == points.count - 1 {
                line(x, y, cx, cy)
                break
            }
            let (nx, ny) = points[i + 1]
            let inLen = hypot(cx - x, cy - y)
            let outLen = hypot(nx - cx, ny - cy)
            let t = min(r, inLen / 2, outLen / 2)
            let ax = cx - (cx - x) / inLen * t
            let ay = cy - (cy - y) / inLen * t
            let bx = cx + (nx - cx) / outLen * t
            let by = cy + (ny - cy) / outLen * t
            line(x, y, ax, ay)
            // A quarter-ish bend: a cubic pulled towards the corner (0.55 of the way, close to a circle).
            out += [ax, ay, ax + (cx - ax) * 0.55, ay + (cy - ay) * 0.55, bx + (cx - bx) * 0.55, by + (cy - by) * 0.55, bx, by]
            x = bx
            y = by
        }
        return out
    }

    private static func transformed(_ segments: [Double], _ scale: Double, _ dx: Double, _ dy: Double, _ slant: Double) -> [Double] {
        var out = [Double](repeating: 0, count: segments.count)
        var i = 0
        while i < segments.count {
            let x = segments[i]
            let y = segments[i + 1]
            out[i] = (x - y * slant) * scale + dx
            out[i + 1] = y * scale + dy
            i += 2
        }
        return out
    }

    static func boxOf(_ tubes: [[Double]]) -> CGRect {
        var x0 = Double.infinity
        var y0 = Double.infinity
        var x1 = -Double.infinity
        var y1 = -Double.infinity
        for t in tubes {
            var i = 0
            while i < t.count {
                x0 = min(x0, t[i])
                x1 = max(x1, t[i])
                y0 = min(y0, t[i + 1])
                y1 = max(y1, t[i + 1])
                i += 2
            }
        }
        return CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)
    }

    /// Sets a word in script from `x`, `y` (baseline, in units); returns its pieces.
    private static func setWord(_ text: String, word: Int, tone: Int, x: Double, y: Double) -> [NeonSignFlickerPiece] {
        var pieces: [NeonSignFlickerPiece] = []
        var pen = x
        let letters = Array(text)
        for (i, letter) in letters.enumerated() {
            guard let g = glyphs[letter] else { continue }
            let tubes = g.tubes.map { transformed(spline($0), unit, pen * unit, y * unit, slant) }
            pieces.append(NeonSignFlickerPiece(word: word, tone: tone, tubes: tubes, box: boxOf(tubes), order: i, ends: (i == 0, i == letters.count - 1)))
            // Letters overlap a little, so each one's lead-in meets the last one's exit.
            pen += g.advance - 0.04
        }
        return pieces
    }

    /// The cocktail glass and its olive, centred on `x`, `y` (units).
    private static func setGlass(word: Int, x: Double, y: Double) -> [NeonSignFlickerPiece] {
        let s = unit
        func place(_ segments: [Double]) -> [Double] { transformed(segments, s, x * s, y * s, 0) }
        let bowl = place(bentPolyline([(-0.56, 1.02), (0, 0.95), (0, 0.06), (1.02, -1.08), (-1.02, -1.08), (-0.04, 0.02)], 0.16))
        let foot = place(bentPolyline([(0.06, 0.95), (0.58, 1.02)], 0.1))
        // The liquid's surface, a slow wave across the bowl.
        let drink = place(spline([(-0.7, -0.74), (-0.36, -0.68), (0, -0.76), (0.34, -0.7), (0.68, -0.76)]))
        let olive = place(spline([(-0.2, -0.82), (-0.42, -0.74), (-0.46, -0.52), (-0.28, -0.4), (-0.08, -0.48), (-0.06, -0.7), (-0.2, -0.82)]))
        let pick = place(bentPolyline([(0.42, -1.55), (-0.02, -0.86)], 0.1))
        return [
            NeonSignFlickerPiece(word: word, tone: 2, tubes: [bowl, foot], box: boxOf([bowl, foot]), order: 0, ends: (true, true)),
            NeonSignFlickerPiece(word: word, tone: 2, tubes: [drink], box: boxOf([drink]), order: 1, ends: (true, true)),
            NeonSignFlickerPiece(word: word, tone: 3, tubes: [olive, pick], box: boxOf([olive, pick]), order: 2, ends: (false, false)),
        ]
    }

    /// The whole sign, centred on its own origin: "open" over "late", the glass to the right.
    static func buildSign() -> (pieces: [NeonSignFlickerPiece], width: Double, height: Double) {
        let swash = transformed(spline([(1.3, 3.06), (2.2, 2.92), (3.3, 3.04), (4.4, 2.9), (5.3, 2.98)]), unit, 0, 0, 0)
        var pieces = setWord("open", word: 0, tone: 0, x: 0, y: 0)
            + setWord("late", word: 1, tone: 1, x: 1.5, y: 2.45)
            + setGlass(word: 2, x: 5.35, y: -0.5)
        pieces.append(NeonSignFlickerPiece(word: 1, tone: 1, tubes: [swash], box: boxOf([swash]), order: 4, ends: (true, true)))
        // Centre everything on the origin.
        let all = boxOf(pieces.flatMap { $0.tubes })
        let cx = all.minX + all.width / 2
        let cy = all.minY + all.height / 2
        for k in pieces.indices {
            pieces[k].tubes = pieces[k].tubes.map { t in t.enumerated().map { i, v in i % 2 == 0 ? v - cx : v - cy } }
            pieces[k].box = boxOf(pieces[k].tubes)
        }
        return (pieces, all.width, all.height)
    }
}
