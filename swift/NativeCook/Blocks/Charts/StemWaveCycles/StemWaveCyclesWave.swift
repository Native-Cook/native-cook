import SwiftUI

/// The scene of the Stem Wave Cycles chart: its timeline and the drawing of one moment.
enum StemWaveCyclesWave {
    /// A block of colour along a row: its width in points and its colour, or nil for a gap.
    typealias Block = (width: Double, color: Color?)

    private static let blue = Color(hex: 0x2e86f5)
    private static let lime = Color(hex: 0xe5ff00)
    private static let lilac = Color(hex: 0xd08bff)
    private static let green = Color(hex: 0x1f4d22)
    private static let mint = Color(hex: 0xb8d4be)
    private static let white = Color(hex: 0xfafafa)

    // Each row repeats every 520 points, so it can scroll by one pattern and land where it started.
    static let pattern = 520.0
    static let strip: [Block] = [
        (60, blue), (90, lilac), (44, lime), (70, white), (56, mint), (38, green), (80, lilac), (42, blue), (40, lime),
    ]
    /// The thinner rows that overlap the strip's top and bottom edges while the stems stand.
    static let rowAbove: [Block] = [(40, nil), (78, green), (26, blue), (120, nil), (60, green), (30, blue), (166, nil)]
    static let rowBelow: [Block] = [
        (90, nil), (30, blue), (70, lime), (16, mint), (150, nil), (24, blue), (80, lime), (60, nil),
    ]

    /// autoPlay, in seconds from the start of a loop.
    static let loop = 9.0
    /// The loop rests until the wave starts to roll.
    static let rollStart = 0.8
    private static let roll = (rollStart, 3.0)
    /// Where a tap plays to: the zoomed curve, and back to the stems.
    static let retractStart = 3.0
    static let curveHeld = 6.1
    static let returnStart = 7.1
    static let stemsBack = 8.65
    private static let rowShiftBy = 40.0
    private static let rowShiftFor = 0.9
    private static let rowStagger = 0.05
    private static let rowsOut = (3.0, 3.4)
    private static let retractAt = retractStart
    private static let stemFor = 0.4
    private static let retractStagger = 0.025
    private static let curveDraw = (3.2, 3.9)
    private static let linesIn = (3.6, 4.3)
    private static let zoomIn = (4.6, curveHeld)
    private static let dotsOut = (4.6, 4.9)
    private static let marksIn = (5.8, curveHeld)
    private static let marksOut = (7.1, 7.3)
    private static let zoomOut = (7.1, 7.9)
    private static let dotsIn = (7.7, 7.9)
    private static let curveUndraw = (7.9, 8.4)
    private static let linesOut = (7.9, 8.3)
    private static let growAt = 7.9
    private static let growStagger = 0.015
    private static let rowsIn = (8.2, 8.6)
    // The strip drifts one pattern to the left over the loop, still at rest.
    private static let drift = (0.8, stemsBack)

    struct Look {
        var width: Double
        var height: Double
        /// The strip's centre line.
        var axis: Double
        var amplitude: Double
        var pitch: Double
        var periodStems: Double
        var strip: Double
        var row: Double
        var overlap: Double
        var dot: Double
        var ink: Color
        var curveInk: Color
        var lineInk: Color
    }

    struct Scene {
        var phase: Double
        var zoom: Double
        var dip: Double
        var rows: Double
        var rowShift: Double
        var curve: Double
        var curveAlpha: Double
        var lines: Double
        var dots: Double
        var marks: Double
        var drift: Double
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    private static func smooth(_ t: Double, _ range: (Double, Double)) -> Double {
        let u = clamp01((t - range.0) / (range.1 - range.0))
        return u * u * (3 - 2 * u)
    }

    private static func easeOut(_ value: Double) -> Double {
        let u = 1 - clamp01(value)
        return 1 - u * u * u
    }

    /// A short dip to 0 and back around `at`, for cuts under reduced motion.
    private static func dip(_ t: Double, at: Double) -> Double {
        sin(.pi * smooth(t, (at - 0.2, at + 0.2)))
    }

    /// The scene at a time of the loop. `phase` turns the wave (radians), `zoom` goes from a period
    /// of `periodStems` stems (0) to one period across the screen (1). `still` keeps the wave from
    /// rolling and the zoom from gliding, and fades instead.
    static func timeline(_ t: Double, still: Bool) -> Scene {
        let rolled = smooth(t, roll)
        let zoomMid = (zoomIn.0 + zoomIn.1) / 2
        let zoomBackMid = (zoomOut.0 + zoomOut.1) / 2
        let drawn = smooth(t, curveDraw) * (1 - smooth(t, curveUndraw))
        return Scene(
            phase: still ? 0 : -rolled * .pi * 2,
            zoom: still ? (t >= zoomMid && t < zoomBackMid ? 1 : 0) : smooth(t, zoomIn) * (1 - smooth(t, zoomOut)),
            // Under reduced motion the zoom is a cut, hidden in a dip of the wave's opacity.
            dip: still ? 1 - dip(t, at: zoomMid) - dip(t, at: zoomBackMid) : 1,
            rows: (1 - smooth(t, rowsOut)) + smooth(t, rowsIn),
            rowShift: t < rowsOut.1 ? t : -1,
            curve: still ? (t >= curveDraw.0 && t < curveUndraw.1 ? 1 : 0) : drawn,
            curveAlpha: still ? drawn : 1,
            lines: smooth(t, linesIn) * (1 - smooth(t, linesOut)),
            dots: 1 - smooth(t, dotsOut) + smooth(t, dotsIn),
            marks: smooth(t, marksIn) * (1 - smooth(t, marksOut)),
            drift: still ? 0 : smooth(t, drift) * pattern
        )
    }

    /// How much of stem `i` stands at a time: 1 standing, 0 gone. While the curve takes over, each
    /// stem retracts into its dot, left to right; on the way back each grows out of the strip again.
    /// `hanging` is true while the stem hangs from its dot (retracting), false while it grows.
    private static func stem(_ i: Int, _ t: Double, still: Bool) -> (stand: Double, hanging: Bool) {
        if still {
            let away = smooth(t, (retractAt, retractAt + stemFor)) * (1 - smooth(t, (growAt, growAt + stemFor)))
            return (1 - away, true)
        }
        let delay = Double(i)
        if t < growAt {
            let from = retractAt + delay * retractStagger
            return (1 - smooth(t, (from, from + stemFor)), true)
        }
        return (easeOut((t - growAt - delay * growStagger) / stemFor), false)
    }

    /// The sideways shift of block `k` of a row while the stems roll: a wipe with a stagger.
    private static func rowShift(_ k: Int, _ t: Double) -> Double {
        if t < 0 { return 0 }
        // Out and back within the roll, so the rows rest where they started.
        let from = roll.0 + Double(k) * rowStagger
        return rowShiftBy * sin(.pi * smooth(t, (from, from + rowShiftFor * 2)))
    }

    private static func drawRow(
        _ c: GraphicsContext, _ blocks: [Block], y: Double, h: Double, offset: Double, shiftAt: Double,
        direction: Double, width: Double
    ) {
        if h <= 0.05 { return }
        // Tile the pattern across the screen from the drifted start.
        var start = -((offset.truncatingRemainder(dividingBy: pattern) + pattern).truncatingRemainder(dividingBy: pattern))
        while start < width {
            var x = start
            for (k, block) in blocks.enumerated() {
                if let color = block.color {
                    let shift = direction * rowShift(k, shiftAt)
                    if x + shift < width && x + shift + block.width > 0 {
                        c.fill(Path(CGRect(x: x + shift, y: y, width: block.width, height: h)), with: .color(color))
                    }
                }
                x += block.width
            }
            start += pattern
        }
    }

    private static func line(_ a: CGPoint, _ b: CGPoint) -> Path {
        var path = Path()
        path.move(to: a)
        path.addLine(to: b)
        return path
    }

    private static func dot(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// The whole picture at one moment: hairlines, stems, the colour strip and its rows, the curve,
    /// the dots and the crest and trough markers. Stems, dots and curve sample the same sine.
    static func draw(_ c: GraphicsContext, t: Double, phase: Double, zoom: Double, still: Bool, look: Look) {
        let s = timeline(t, still: still)
        let width = look.width, axis = look.axis, amplitude = look.amplitude
        let cx = width / 2
        let p0 = look.pitch * look.periodStems
        // The period grows from p0 to the width; past either end the pinch keeps going a little.
        let period = p0 * pow(width / p0, zoom)
        let spread = period / p0
        let yAt = { (x: Double) in axis + amplitude * sin((x - cx) / period * .pi * 2 + phase) }
        let round = StrokeStyle(lineWidth: 1, lineCap: .round)

        // Hairlines at the full and the half amplitude, sliding in from the left.
        if s.lines > 0 {
            for k in [-2.0, -1, 1, 2] {
                let y = axis + k * amplitude / 2
                c.stroke(line(CGPoint(x: 0, y: y), CGPoint(x: width * s.lines, y: y)), with: .color(look.lineInk), style: StrokeStyle(lineWidth: 0.75, lineCap: .round))
            }
        }

        // The stems, from the strip to their dots.
        let count = Int(ceil(width / look.pitch)) + 2
        let dotsAlpha = clamp01(s.dots) * s.dip
        for i in 0..<count {
            let x = cx + (Double(i) - Double(count - 1) / 2) * look.pitch * spread
            if x < -10 || x > width + 10 { continue }
            let y = yAt(x)
            let (stand, hanging) = stem(i, t, still: still)
            if stand > 0.001 {
                let ink = look.ink.opacity((still ? stand : 1) * s.dip)
                let path = hanging
                    ? line(CGPoint(x: x, y: y + (axis - y) * (still ? 1 : stand)), CGPoint(x: x, y: y))
                    : line(CGPoint(x: x, y: axis), CGPoint(x: x, y: axis + (y - axis) * stand))
                c.stroke(path, with: .color(ink), style: round)
            }
            // While growing back, a dot rides the top of its stem.
            let dy = hanging ? y : axis + (y - axis) * stand
            if dotsAlpha > 0.001 { c.fill(dot(x, dy, look.dot), with: .color(look.ink.opacity(dotsAlpha))) }
        }

        // The strip, and the rows over its edges while the stems stand.
        let half = look.strip / 2
        let rowHeight = look.row * clamp01(s.rows)
        drawRow(c, rowAbove, y: axis - half - rowHeight + look.overlap, h: rowHeight, offset: s.drift, shiftAt: s.rowShift, direction: 1, width: width)
        drawRow(c, rowBelow, y: axis + half - look.overlap, h: rowHeight, offset: s.drift, shiftAt: s.rowShift, direction: -1, width: width)
        drawRow(c, strip, y: axis - half, h: look.strip, offset: s.drift, shiftAt: -1, direction: 0, width: width)

        // The curve, drawn from the left.
        if s.curve > 0.001 && s.curveAlpha > 0.001 {
            var path = Path()
            let end = width * s.curve
            let step = 3.0
            path.move(to: CGPoint(x: 0, y: yAt(0)))
            var x = step
            while x < end {
                path.addLine(to: CGPoint(x: x, y: yAt(x)))
                x += step
            }
            path.addLine(to: CGPoint(x: end, y: yAt(end)))
            c.stroke(path, with: .color(look.curveInk.opacity(s.curveAlpha * s.dip)), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
        }

        // Crest and trough of the period on screen, joined to the axis.
        if s.marks > 0.001 {
            let shiftX = -phase / (.pi * 2) * period
            var crest = cx - period / 4 + shiftX
            while crest < cx - period / 2 { crest += period }
            while crest > cx + period / 2 { crest -= period }
            var trough = crest + period / 2
            if trough > cx + period / 2 { trough -= period }
            let ink = look.ink.opacity(s.marks * s.dip)
            for x in [crest, trough] {
                let y = yAt(x)
                c.stroke(line(CGPoint(x: x, y: axis), CGPoint(x: x, y: axis + (y - axis) * s.marks)), with: .color(ink), style: round)
                c.fill(dot(x, y, look.dot), with: .color(ink))
                c.fill(dot(x, axis, look.dot * 0.8), with: .color(ink))
            }
        }
    }
}
