import CoreGraphics
import Foundation

/// The geometry of a spirograph set, in model units (pt at a scale of 1) around the ring's centre,
/// plus the pens and the autoPlay script.
///
/// A gear of `teeth` teeth rolls inside a ring of `ringTeeth` teeth. Its centre sits at angle `phi`
/// on a circle of radius R − r; rolling without slipping turns the gear by −(R − r)/r for every
/// radian of `phi`, which is kept as its own running angle `spin` so it stays continuous when the
/// gear is changed. A pen in a hole at distance d and angle ψ on the gear traces a hypotrochoid.
enum SpirographDrawModel {
    // The ring's pitch radius (where the teeth mesh) and its tooth count; the ring's body is `ringBody` wide.
    static let ringR = 150.0
    static let ringTeeth = 96
    static let ringBody = 26.0
    // Teeth stand `toothDepth` proud of the pitch circle on both ring and gears.
    static let toothDepth = 3.2
    // Everything the pen can reach, and the ring, fits in a square of this half-size.
    static let bounds = ringR + ringBody + 8

    struct Gear {
        let teeth: Int
        let tint: UInt32
    }

    /// The gears in the set, smallest first: tinted, translucent plastic.
    static let gears = [
        Gear(teeth: 30, tint: 0xE3A13B),
        Gear(teeth: 40, tint: 0x3F9C9E),
        Gear(teeth: 56, tint: 0xD4604F),
        Gear(teeth: 72, tint: 0x6A80C8),
    ]

    struct Ink {
        let color: UInt32
        let name: String
    }

    /// The pens: real ink colours, laid on the paper with a multiply blend.
    static let inks = [
        Ink(color: 0x2541A8, name: "Ultramarine"),
        Ink(color: 0xD3402A, name: "Vermilion"),
        Ink(color: 0x11806C, name: "Viridian"),
        Ink(color: 0x2A2830, name: "Graphite"),
    ]

    // Pen holes: distance from the gear's centre as a share of its radius, laid along a spiral.
    static let holes = [0.24, 0.4, 0.56, 0.71, 0.85]
    private static let holeTurn = 1.25

    /// Pitch radius of a gear.
    static func gearRadius(_ teeth: Int) -> Double { ringR * Double(teeth) / Double(ringTeeth) }

    private static func gcd(_ a: Int, _ b: Int) -> Int {
        var x = a
        var y = b
        while y != 0 {
            let t = y
            y = x % y
            x = t
        }
        return x
    }

    /// Turns of the gear round the ring before the pattern closes, and the loops it makes.
    static func pattern(_ teeth: Int) -> (turns: Int, petals: Int) {
        let g = gcd(ringTeeth, teeth)
        return (teeth / g, ringTeeth / g)
    }

    /// Distance and angle (on the gear) of a pen hole; `hole` may be fractional while it morphs.
    static func holeAt(_ hole: Double, _ radius: Double) -> (d: Double, psi: Double) {
        let i = max(0, min(Double(holes.count - 1), hole))
        let lo = Int(floor(i))
        let hi = min(holes.count - 1, lo + 1)
        let f = i - Double(lo)
        return (radius * (holes[lo] + (holes[hi] - holes[lo]) * f), holeTurn * i)
    }

    /// The pen's position (from the ring's centre) for a gear of `radius` at `phi`, turned by `spin`.
    static func penAt(_ phi: Double, _ spin: Double, _ radius: Double, _ d: Double, _ psi: Double) -> CGPoint {
        let reach = ringR - radius
        return CGPoint(x: reach * cos(phi) + d * cos(spin + psi), y: reach * sin(phi) + d * sin(spin + psi))
    }

    /// How far the gear turns on itself per radian it rolls round the ring.
    static func spinRate(_ radius: Double) -> Double { -(ringR - radius) / radius }

    /// The outline of a toothed wheel of `teeth` teeth on a pitch circle of `radius`, teeth pointing
    /// outwards (`outward` = 1, a gear) or inwards (−1, the ring's inner edge).
    static func toothOutline(_ teeth: Int, _ radius: Double, _ outward: Double, depth: Double = toothDepth) -> [CGPoint] {
        var pts: [CGPoint] = []
        let step = 2 * Double.pi / Double(teeth)
        // Root, flank up, flat tip, flank down: a trapezoid tooth on each pitch step.
        let profile: [(Double, Double)] = [(0, -1), (0.18, -1), (0.32, 1), (0.68, 1), (0.82, -1)]
        for k in 0..<teeth {
            let a = Double(k) * step
            for (t, h) in profile {
                let r = radius + outward * h * depth * 0.5
                pts.append(CGPoint(x: r * cos(a + t * step), y: r * sin(a + t * step)))
            }
        }
        return pts
    }

    struct Cue {
        var at: Double
        var gear: Int? = nil
        var hole: Int? = nil
        var ink: Int? = nil
        var roll: Double? = nil
        var time: Double? = nil
        var clear = false
    }

    // autoPlay (seconds). It starts and ends at rest on a clean sheet with the 40-tooth gear, hole 3
    // and ultramarine ink: rolls the gear five times round (a closed twelve-loop rosette), switches
    // to vermilion ink and the 72-tooth gear, moves the pen out to hole 5, rolls three times round
    // (four wide loops over the first pattern), tears the sheet off and sets the first gear, hole and
    // ink back. Every roll is a whole number of the pattern's turns, so the gear ends where it began.
    static let loop = 16.0
    static let startGear = 1
    static let startHole = 2
    static let startInk = 0
    static let script = [
        Cue(at: 0.9, roll: 5, time: 5.6),
        Cue(at: 7.0, ink: 1),
        Cue(at: 7.4, gear: 3),
        Cue(at: 7.9, hole: 4),
        Cue(at: 8.4, roll: 3, time: 3.6),
        Cue(at: 12.7, clear: true),
        Cue(at: 14.2, gear: 1, hole: 2, ink: 0),
    ]
}
