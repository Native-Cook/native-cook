import Foundation
import SwiftUI

/// Breathe Mesh: the breath, the two lights it moves between, and the onboarding film, as pure functions of
/// time. A line-for-line port of the React Native `breatheMotion.ts`.
enum MeshGradientBreatheMotion {
    static let loop: Double = 16 // one box breath, and the onboarding's three steps

    struct Rhythm { let id: String; let label: String; let detail: String; let phases: [Double] }

    /// in, hold, out, hold (seconds). Box is the film's: 16 s, one loop.
    static let rhythms: [Rhythm] = [
        Rhythm(id: "box", label: "Box", detail: "Box breathing", phases: [4, 4, 4, 4]),
        Rhythm(id: "478", label: "4-7-8", detail: "4-7-8 breathing", phases: [4, 7, 8, 0]),
        Rhythm(id: "calm", label: "Even", detail: "Even breathing", phases: [5, 0, 5, 0]),
    ]
    static let lengths = [1, 3, 5, 10] // minutes

    // ---------------------------------------------------------------- colour

    static func hexToLab(_ hex: String) -> [Double] {
        let n = Int(hex.dropFirst(), radix: 16) ?? 0
        func lin(_ v: Int) -> Double {
            let c = Double(v) / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let r = lin((n >> 16) & 255), g = lin((n >> 8) & 255), b = lin(n & 255)
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return [
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
        ]
    }

    static func palette(_ rows: [String]) -> [Double] { rows.flatMap(hexToLab) }

    // Exhaled: cool and quiet (mist blue, lilac, sage). Inhaled: warm and open (peach, apricot, rose).
    // 5 x 5, rows from the top of the screen.
    static let exhaled = palette([
        "#C9D3EC", "#CFD6EE", "#D8D8EE", "#DED6EC", "#E2D5E9",
        "#C3D4E8", "#B9CBEA", "#C8C6EA", "#D9CBE8", "#E4D3E6",
        "#CFE0E2", "#BFD5E3", "#B6C3E6", "#C9C2E4", "#DCCFE3",
        "#DCE8DE", "#CBE0DA", "#C2D3E2", "#CCCBE3", "#DFD6E5",
        "#E8EFE3", "#DDEAE0", "#D5E2E6", "#DADAEA", "#E6E0EC",
    ])
    static let inhaled = palette([
        "#F6D9CF", "#F8D4C6", "#F7CFC4", "#F2CDCB", "#EDCED4",
        "#F7E0C8", "#F9CDB4", "#F6BFAE", "#EEBFBF", "#E8C6D2",
        "#F9EBCB", "#FAD9B2", "#F8C6A2", "#F0BCB0", "#E9C3CC",
        "#F8EFD6", "#F9E3C0", "#F7D3B0", "#F1CCBC", "#ECCFD4",
        "#F7F1E2", "#F8EAD2", "#F6DDC4", "#F2D6CC", "#EED8DB",
    ])

    // ---------------------------------------------------------------- easing

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * x) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInQuad(_ x: Double) -> Double { x * x }
    /// Easing.bezier(0.2, 0.8, 0.2, 1)
    static func ease(_ x: Double) -> Double { UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.2, y: 0.8), endControlPoint: UnitPoint(x: 0.2, y: 1)).value(at: clamp01(x)) }

    /// Blend two OKLab grids and keep the chroma (a straight Lab blend of a cool and a warm colour passes
    /// through grey, so each blended colour's a,b are rescaled to the blended chroma).
    static func mixLab(_ a: [Double], _ b: [Double], _ f: Double) -> [Double] {
        var out = [Double](repeating: 0, count: a.count)
        for i in 0..<(a.count / 3) {
            let k = i * 3
            let ca = (a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2]).squareRoot()
            let cb = (b[k + 1] * b[k + 1] + b[k + 2] * b[k + 2]).squareRoot()
            let L = a[k] + (b[k] - a[k]) * f
            let x = a[k + 1] + (b[k + 1] - a[k + 1]) * f
            let y = a[k + 2] + (b[k + 2] - a[k + 2]) * f
            let c = (x * x + y * y).squareRoot()
            let s = c > 0.0001 ? (ca + (cb - ca) * f) / c : 1
            out[k] = L; out[k + 1] = x * s; out[k + 2] = y * s
        }
        return out
    }

    struct Breath { var full: Double; var phase: Int; var left: Double; var length: Double }

    /// The breath at time s (seconds, any range) for rhythm r: fullness 0..1 (eased in and out of each hold),
    /// the phase index 0..3 and the seconds left in it.
    static func breath(at s: Double, rhythm r: Int) -> Breath {
        let p = rhythms[r].phases
        let per = p.reduce(0, +)
        var x = (s.truncatingRemainder(dividingBy: per) + per).truncatingRemainder(dividingBy: per)
        var k = 0
        while k < 3 && (p[k] == 0 || x >= p[k]) { x -= p[k]; k += 1 }
        let f = p[k] > 0 ? x / p[k] : 0
        let full = k == 0 ? easeInOutSine(f) : k == 1 ? 1 : k == 2 ? 1 - easeInOutSine(f) : 0
        return Breath(full: full, phase: k, left: p[k] - x, length: p[k])
    }

    /// The light's 75 OKLab numbers at fullness b.
    static func light(_ b: Double) -> [Double] { mixLab(exhaled, inhaled, b) }

    /// Control-point offsets (50 numbers) at fullness b and ambient time amb (seconds). (cx, cy) = the orb's
    /// centre in screen fractions.
    static func offsets(_ b: Double, _ amb: Double, _ cx: Double, _ cy: Double) -> [Double] {
        let tau = amb / loop * .pi * 2
        var out = [Double](repeating: 0, count: 50)
        for j in 0..<5 {
            for i in 0..<5 {
                let k = (j * 5 + i) * 2
                let gx = Double(i) / 4, gy = Double(j) / 4
                let s = Double(i * 5 + j * 11) * 0.41
                var dx = 0.035 * sin(tau * Double(1 + (i + j) % 2) + s)
                var dy = 0.025 * cos(tau * Double(1 + j % 2) + s * 1.3)
                let rx = gx - cx
                let ry = (gy - cy) * 2.1 // the screen is about twice as tall as wide
                let dist = (rx * rx + ry * ry).squareRoot() + 0.0001
                let near = exp(-dist * 2.2)
                dx += rx / dist * 0.09 * b * near
                dy += ry / dist * 0.09 * b * near / 2.1
                if i == 0 || i == 4 { dx = 0 }
                if j == 0 || j == 4 { dy = 0 }
                out[k] = dx; out[k + 1] = dy
            }
        }
        return out
    }

    // ---------------------------------------------------------------- the film

    static let toStep2 = (5.0, 5.7)  // Welcome -> Your rhythm
    static let pickLength = 7.4      // the length wheel turns from 3 to 5 min
    static let toStep3 = (9.6, 10.3) // -> Your first session
    static let resetLength = 12.4    // the hidden wheel goes back to 3 min
    static let toStep1 = (15.2, 16.0) // -> Welcome again

    /// Visibility of step k at loop time t: the old step fades out over the first 45 % of a transition
    /// (ease-in), the new one in over the last 55 % (ease-out cubic).
    static func stepVis(_ k: Int, _ t: Double) -> Double {
        let T = [toStep2, toStep3, toStep1]
        let from = [0, 1, 2], to = [1, 2, 0]
        var v: Double = k == 0 ? 1 : 0
        for j in 0..<3 {
            let (a, b) = T[j]
            let mid = a + (b - a) * 0.45
            let o = seg(t, a, mid)
            let inn = easeOutCubic(seg(t, mid, b))
            if from[j] == k && t >= a { v = 1 - o * o }
            if to[j] == k && t >= mid { v = inn }
        }
        return v
    }

    /// Which step the film is on (the one whose content is interactive).
    static func filmStep(_ t: Double) -> Int {
        if t < toStep2.0 + 0.35 { return 0 }
        if t < toStep3.0 + 0.35 { return 1 }
        if t < toStep1.0 + 0.4 { return 2 }
        return 0
    }

    static func filmLength(_ t: Double) -> Int { t >= pickLength && t < resetLength ? 5 : 3 }
}
