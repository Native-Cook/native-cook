import SwiftUI

/*
 Crayon Box Picker — the autoPlay script. Every value is a pure function of the loop time t.

   0.0 – 1.2   rest: twelve crayons standing in their box, Sky is the colour, its swatch on the paper.
   1.2 – 2.4   Tangerine is pulled up against the box's friction (ease-out, 0.7 s), wobbles as it
               comes free, its neighbours lean into the gap.
   2.4 – 2.75  it swings down onto the paper (arc, turning to a writing angle, a little lift).
   2.75 – 3.75 it scribbles a swatch: back and forth in wax, rows working down; the Sky swatch fades.
   3.75        Tangerine becomes the colour (the name rolls).
   3.8 – 4.6   it goes home: back up along the arc, then slides down into its slot (spring settle).
   4.8 – 8.0   the same for Sky: pull 4.8 – 5.8, onto the paper 5.8 – 6.15, scribble 6.15 – 7.0,
               home 7.0 – 8.0.
   8.0 – 9.0   rest.
 */

struct CrayonBoxPickerCrayon {
    let name: String
    let rgb: (Double, Double, Double)
    var color: Color { Color(red: rgb.0, green: rgb.1, blue: rgb.2) }
}

/// One pick, as times: pull from, pull to, fly to paper end, scribble end, fly home end, settle end.
struct CrayonBoxPickerPick {
    var crayon: Int
    var times: [Double]
}

/// Where a pick is: phase 0 = in its slot, 1 = pulling (amount = pull 0..1), 2 = flying to the
/// paper, 3 = scribbling, 4 = flying home, 5 = settling into the slot (amount 1 → 0 pull).
struct CrayonBoxPickerPhase {
    var phase: Int
    var amount: Double
    var wobble: Double
}

enum CrayonBoxPickerMotion {
    static let crayons: [CrayonBoxPickerCrayon] = [
        .init(name: "Cherry", rgb: (0.82, 0.23, 0.26)),
        .init(name: "Tangerine", rgb: (0.94, 0.48, 0.16)),
        .init(name: "Lemon", rgb: (0.95, 0.77, 0.19)),
        .init(name: "Lime", rgb: (0.53, 0.72, 0.23)),
        .init(name: "Moss", rgb: (0.31, 0.48, 0.23)),
        .init(name: "Ocean", rgb: (0.12, 0.44, 0.62)),
        .init(name: "Sky", rgb: (0.36, 0.7, 0.9)),
        .init(name: "Grape", rgb: (0.48, 0.31, 0.69)),
        .init(name: "Bubblegum", rgb: (0.94, 0.55, 0.71)),
        .init(name: "Coral", rgb: (0.95, 0.53, 0.43)),
        .init(name: "Cocoa", rgb: (0.48, 0.29, 0.17)),
        .init(name: "Midnight", rgb: (0.17, 0.2, 0.31)),
    ]
    static let tangerine = 1
    static let sky = 6
    static let restColour = sky

    static let loopPicks: [CrayonBoxPickerPick] = [
        .init(crayon: tangerine, times: [1.2, 2.4, 2.75, 3.75, 4.25, 4.6]),
        .init(crayon: sky, times: [4.8, 5.8, 6.15, 7.0, 7.45, 8.0]),
    ]

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func easeInOut(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - u, 3) }

    /// A settle with one soft overshoot (spring-like), 0 → 1.
    static func settle(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        if u >= 1 { return 1 }
        return 1 - exp(-6 * u) * cos(u * 8)
    }

    static func pickPhase(_ p: CrayonBoxPickerPick, _ t: Double) -> CrayonBoxPickerPhase {
        let T = p.times
        if t < T[0] || t >= T[5] { return .init(phase: 0, amount: 0, wobble: 0) }
        if t < T[1] {
            // Friction: quick at first, then it drags; free after 60 % of the window, where it wobbles.
            let u = seg(t, T[0], T[0] + (T[1] - T[0]) * 0.6)
            let free = seg(t, T[0] + (T[1] - T[0]) * 0.6, T[1])
            let pull = easeOut(u) * 0.97 + 0.03 * sin(u * 12) * (1 - u)
            return .init(phase: 1, amount: pull, wobble: sin(free * .pi * 3) * (1 - free) * (u >= 1 ? 1 : 0))
        }
        if t < T[2] { return .init(phase: 2, amount: easeInOut(seg(t, T[1], T[2])), wobble: 0) }
        if t < T[3] { return .init(phase: 3, amount: seg(t, T[2], T[3]), wobble: 0) }
        if t < T[4] { return .init(phase: 4, amount: easeInOut(seg(t, T[3], T[4])), wobble: 0) }
        return .init(phase: 5, amount: 1 - settle(seg(t, T[4], T[5])), wobble: 0)
    }

    /// Swatches on the paper: (old colour, old alpha, new colour, new progress 0..1).
    static func loopSwatch(_ t: Double) -> (Int, Double, Int, Double) {
        let a = seg(t, 2.75, 3.75)
        let b = seg(t, 6.15, 7.0)
        if t < 2.75 { return (sky, 1, sky, 1) }
        if t < 3.75 { return (sky, 1 - a, tangerine, a) }
        if t < 6.15 { return (tangerine, 1, tangerine, 1) }
        if t < 7.0 { return (tangerine, 1 - b, sky, b) }
        return (sky, 1, sky, 1)
    }

    /// The chosen colour's name: (from, to, roll 0..1).
    static func loopColour(_ t: Double) -> (Int, Int, Double) {
        if t < 3.75 { return (tangerine, sky, 1) }
        if t < 7.0 { return (sky, tangerine, easeInOut(seg(t, 3.75, 4.1))) }
        return (tangerine, sky, easeInOut(seg(t, 7.0, 7.35)))
    }

    /// Which pick is under way at t and its phase.
    static func loopPick(_ t: Double) -> (Int, CrayonBoxPickerPhase) {
        for p in loopPicks {
            let ph = pickPhase(p, t)
            if ph.phase != 0 { return (p.crayon, ph) }
        }
        return (-1, .init(phase: 0, amount: 0, wobble: 0))
    }
}
