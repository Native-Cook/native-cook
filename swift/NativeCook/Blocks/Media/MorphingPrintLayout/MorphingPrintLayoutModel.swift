import SwiftUI

/*
 The layouts of Morphing Print Layout and how one becomes the next.

 Five film formats, in design points (scaled by the screen width / 393, at most 430 / 393). A frame is
 a white card; its wells are square photo windows placed from the frame's top-left corner:
   0 Single   270 × 322   one 242 well at (14, 14)
   1 Double   322 × 196   two 141 wells, 12 apart
   2 Triple   322 × 150   three 94 wells, 6 apart
   3 Four     290 × 316   2 × 2 wells of 125, 12 apart
   4 Six      322 × 242   3 × 2 wells of 94, 6 apart
 Every layout lists six wells: those it does not use sit on its last real well, under it, so a growing
 layout splits a photo out from under its neighbour and a shrinking one tucks it back.

 The layout at a fractional page p is the linear blend of its two neighbours (extrapolated past the
 ends). Between Triple and Four the prints come loose: with λ = sin(π (p − 2)), each of the four wells
 grows its own white border (8 pt, 22 pt at the bottom), tilts and spreads out (offsets below), and the
 big frame fades to 1 − 1.6 λ (gone at λ = 0.625).

 autoPlay timeline (loop = 7 s):
   0.0–0.8   rest on Single.
   0.8       → Double, spring (1 − e^(−9u)(cos 12u + 0.75 sin 12u), ~9 % overshoot, settled by 0.6 s).
   1.9       → Triple, same spring.
   3.0–4.1   → Four, ease in-out over 1.1 s, the prints scattering and gathering on the way.
   4.7       → Six, spring.
   5.8       back to Single in one sweep, critically damped (1 − (1 + 9u) e^(−9u)), through all the
             layouts in between.
   6.7–7.0   rest on Single, the same frame as t = 0.
 */

struct MorphingPrintLayoutWell {
    var x: Double, y: Double, s: Double
}

struct MorphingPrintLayoutFormat {
    var name: String
    var caption: String
    var w: Double, h: Double
    var wells: [MorphingPrintLayoutWell]
    var count: Int
}

enum MorphingPrintLayoutModel {
    static let loop = 7.0

    static let formats: [MorphingPrintLayoutFormat] = {
        func make(_ name: String, _ caption: String, _ w: Double, _ h: Double, _ real: [(Double, Double, Double)]) -> MorphingPrintLayoutFormat {
            var wells = real.map { MorphingPrintLayoutWell(x: $0.0, y: $0.1, s: $0.2) }
            while wells.count < 6 { wells.append(wells[real.count - 1]) }
            return MorphingPrintLayoutFormat(name: name, caption: caption, w: w, h: h, wells: wells, count: real.count)
        }
        return [
            make("Single", "One photo, a classic square", 270, 322, [(14, 14, 242)]),
            make("Double", "Two photos side by side", 322, 196, [(14, 14, 141), (167, 14, 141)]),
            make("Triple", "Three photos in a strip", 322, 150, [(14, 14, 94), (114, 14, 94), (214, 14, 94)]),
            make("Four", "Four photos in a grid", 290, 316, [(14, 14, 125), (151, 14, 125), (14, 151, 125), (151, 151, 125)]),
            make("Six", "Six photos, a contact sheet", 322, 242,
                 [(14, 14, 94), (114, 14, 94), (214, 14, 94), (14, 114, 94), (114, 114, 94), (214, 114, 94)]),
        ]
    }()

    /// Where the loose prints go between Triple and Four: offset (pt) and tilt (degrees) at λ = 1.
    static let scatter: [(dx: Double, dy: Double, r: Double)] = [(-24, -20, -10), (22, -14, 8), (-18, 18, 7), (26, 22, -9)]

    struct Shape {
        var w: Double, h: Double
        var wells: [MorphingPrintLayoutWell]
        /// How many wells to draw (the larger count of the two neighbours).
        var shown: Int
        var loose: Double
    }

    static func shape(_ p: Double, reduce: Bool = false) -> Shape {
        let n = formats.count
        let a = min(n - 2, max(0, Int(floor(p))))
        let f = p - Double(a)
        let A = formats[a], B = formats[a + 1]
        func mix(_ u: Double, _ v: Double) -> Double { u + (v - u) * f }
        let wells = (0..<6).map { i in
            MorphingPrintLayoutWell(x: mix(A.wells[i].x, B.wells[i].x), y: mix(A.wells[i].y, B.wells[i].y), s: mix(A.wells[i].s, B.wells[i].s))
        }
        let loose = (!reduce && p > 2 && p < 3) ? sin(.pi * (p - 2)) : 0
        let shown = f <= 0.001 ? A.count : (f >= 0.999 ? B.count : max(A.count, B.count))
        return Shape(w: mix(A.w, B.w), h: mix(A.h, B.h), wells: wells, shown: shown, loose: loose)
    }

    static func spring(_ u: Double) -> Double {
        guard u > 0 else { return 0 }
        return 1 - exp(-9 * u) * (cos(12 * u) + 0.75 * sin(12 * u))
    }
    static func critical(_ u: Double) -> Double {
        guard u > 0 else { return 0 }
        return 1 - (1 + 9 * u) * exp(-9 * u)
    }
    static func inOut(_ x: Double) -> Double {
        let t = min(1, max(0, x))
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// The page at loop time t.
    static func page(_ t: Double) -> Double {
        if t < 0.8 { return 0 }
        if t < 1.9 { return spring(t - 0.8) }
        if t < 3.0 { return 1 + spring(t - 1.9) }
        if t < 4.7 { return 2 + inOut((t - 3.0) / 1.1) }
        if t < 5.8 { return 3 + spring(t - 4.7) }
        return 4 - 4 * critical(t - 5.8)
    }
}
