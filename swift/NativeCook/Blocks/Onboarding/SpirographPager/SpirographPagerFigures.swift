import Foundation

/// One page of the pager.
struct SpirographPagerPage {
    var title: String
    /// What the page shows when it arrives.
    var figure: [[Double]]
    /// What that turns into a moment later, if anything.
    var settled: [[Double]]? = nil

    static let samples = [
        SpirographPagerPage(title: "Promotion strategy\nand development", figure: SpirographPagerFigures.dot, settled: SpirographPagerFigures.cone),
        SpirographPagerPage(title: "Rebate fulfillment", figure: SpirographPagerFigures.spheres),
        SpirographPagerPage(title: "Loyalty programs", figure: SpirographPagerFigures.treeRings, settled: SpirographPagerFigures.lens),
        SpirographPagerPage(title: "Sweepstakes\nand Contests", figure: SpirographPagerFigures.hourglass, settled: SpirographPagerFigures.bowls),
        SpirographPagerPage(title: "Customer service", figure: SpirographPagerFigures.lattice, settled: SpirographPagerFigures.trio),
        SpirographPagerPage(title: "Channel Incentives", figure: SpirographPagerFigures.halo),
    ]
}

/// Every figure is the same `rings` ellipses in a different arrangement, so one figure can turn
/// into any other: each ellipse travels from its place in the one to its place in the other.
///
/// One ellipse is six numbers: x and y of its centre from the centre of the artwork, width,
/// height, opacity, and how much it takes part in the turning (1 for a meridian of a sphere,
/// whose width follows the turn).
enum SpirographPagerFigures {
    /// Number of ellipses in a figure.
    static let rings = 24

    private static func figure(_ ring: (Int) -> [Double]) -> [[Double]] {
        (0..<rings).map(ring)
    }

    /// The opacity that makes `count` rings lying on each other read as one line of opacity `total`.
    private static func stacked(_ count: Int, _ total: Double) -> Double {
        1 - pow(1 - total, 1 / Double(count))
    }

    /// One small circle: every ring lies on it.
    static let dot = figure { _ in [0, 0, 54, 54, stacked(rings, 0.6), 0] }

    /// Fourteen circles from 24 to 190 points, each a little higher than the one inside it: a look into a cone.
    static let cone = figure { k in
        let j = Double(min(k, 13))
        let size = 24 + 166 * j / 13
        return [0, 13 - 2 * j, size, size, k < 14 ? 0.7 - 0.35 * j / 13 : 0, 0]
    }

    /// Two wire spheres, one on the other: eight meridians each, and their outlines.
    static let spheres = figure { k in
        let y: Double = k % 2 == 0 ? -95 : 95
        if k < 16 { return [0, y, 190, 190, 0.5, 1] }
        return [0, y, 190, 190, k < 18 ? 0.35 : 0, 0]
    }

    /// Three sets of eight circles, 40 to 200 points, 30 points apart.
    static let treeRings = figure { k in
        let j = Double(k / 3)
        let size = 40 + 160 * j / 7
        return [0, Double(k % 3 - 1) * 30, size, size, 0.6 - 0.3 * j / 7, 0]
    }

    /// The same rings squashed into a lens: the middle reads as a dark almond.
    static let lens = figure { k in
        let j = Double(k / 3)
        return [0, Double(k % 3 - 1) * 10, 260, 8 + 112 * j / 7, 0.6 - 0.25 * j / 7, 0]
    }

    /// Thirteen ellipses on the vertical axis, wide at the ends and narrow in the middle.
    static let hourglass = figure { k in
        let j = Double(k % 13)
        let waist = abs(2 * j / 12 - 1)
        return [0, -100 + 200 * j / 12, 60 + 200 * waist, 16 + 54 * waist, k < 13 ? 0.55 : 0, 0]
    }

    /// The hourglass without its middle: two bowls.
    static let bowls = figure { k in
        let j = k % 13
        let ring = hourglass[k]
        let y = ring[1] + (j < 5 ? -14 : j > 7 ? 14 : 0)
        return [0, y, ring[2], ring[3], k < 13 && (j < 5 || j > 7) ? 0.55 : 0, 0]
    }

    /// Four by six ellipses that touch their neighbours, like chain mail.
    static let lattice = figure { k in [(Double(k % 4) - 1.5) * 46, (Double(k / 4) - 2.5) * 30, 46, 30, 0.5, 0] }

    /// Three circles side by side, eight rings on each.
    static let trio = figure { k in [Double(k % 3 - 1) * 54, 0, 54, 54, stacked(rings / 3, 0.6), 0] }

    /// One large, faint circle.
    static let halo = figure { _ in [0, 0, 170, 170, stacked(rings, 0.2), 0] }
}

enum SpirographPagerMotion {
    /// A change of page: the rings travel for `move` seconds, the texts for `textMove`.
    static let move = 0.9
    static let textMove = 0.5
    static let textSlide = 60.0
    /// A page that has a settled figure waits `settleAfter`, then takes `settle` to turn into it.
    static let settleAfter = 0.6
    static let settle = 0.9
    /// Each ring starts this share of a change after the one before it.
    static let stagger = 0.016
    /// A drag of this many points is a whole change; let go past `commit` of it and the page changes.
    static let drag = 260.0
    static let commit = 0.4

    // At rest the artwork breathes, and the spheres turn about their vertical axis. Both come back
    // to where they started every `loop` seconds.
    static let breath = 0.02
    static let breathPeriod = 2.5
    /// Degrees a second.
    static let spin = 36.0
    /// The spheres' meridians are not caught edge-on at rest.
    static let spinStart = 0.12

    /// autoPlay: next page at each of `nextAt`, back to the first at `homeAt`, every `loop` seconds.
    static let loop = 10.0
    static let nextAt = [2.3, 4.5, 7.2]
    static let homeAt = loop - move
    /// After a touch autoPlay waits this long before it starts over from the first page.
    static let userHold = 3.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func ease(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// How far ring `k` is through a change that is `t` of the way: the rings leave one after another.
    static func share(_ t: Double, _ k: Int) -> Double {
        ease((t - Double(k) * stagger) / (1 - Double(SpirographPagerFigures.rings - 1) * stagger))
    }

    /// How long a run to a figure and on to its settled form takes.
    static func runLength(settles: Bool) -> Double {
        settles ? move + settleAfter + settle : move
    }

    /// Value `i` of ring `k`, `r` seconds into a run: first from where the rings were (`from`, a flat
    /// list of rings × 6 numbers) to `first`, then, after a wait, from `first` to `second`.
    static func ringValue(_ from: [Double], _ first: [[Double]], _ second: [[Double]], _ r: Double, _ k: Int, _ i: Int) -> Double {
        if r < move {
            let start = from[k * 6 + i]
            return start + (first[k][i] - start) * share(r / move, k)
        }
        let start = first[k][i]
        return start + (second[k][i] - start) * share((r - move - settleAfter) / settle, k)
    }

    /// The turn of ring `k` about the vertical axis: the meridians of a sphere are an eighth of a half turn apart.
    static func phase(_ k: Int) -> Double {
        Double(k / 2) * .pi / 8 + Double(k % 2) * (.pi / 16)
    }

    /// The width a turning ring shows, as a share of its own, `clock` seconds after the block appeared.
    static func turned(_ k: Int, spin amount: Double, clock: Double) -> Double {
        1 - amount * (1 - abs(cos(spinStart + spin * .pi * clock / 180 + phase(k))))
    }

    static func breathing(_ clock: Double) -> Double {
        1 + breath * sin(2 * .pi * clock / breathPeriod)
    }

    /// Texts of a page that is leaving (-1), staying (0) or arriving (1): opacity and offset.
    static func textShift(_ r: Double, role: Int, calm: Bool) -> (opacity: Double, y: Double) {
        let u = ease(r / textMove)
        if role == 0 { return (1, 0) }
        let slide = calm ? 0 : textSlide
        return role < 0 ? (1 - u, -slide * u) : (u, slide * (1 - u))
    }
}
