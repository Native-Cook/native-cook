import SwiftUI

/// The marks as geometry: built once per layout (seeded, no randomness per frame) and drawn by amount.
enum ScribbleEmphasisNotesStrokes {
    private typealias M = ScribbleEmphasisNotesMotion
    typealias Band = ScribbleEmphasisNotesPage.Band

    /// One point of a mark: where, how wide, the share of the mark drawn when the pen is here, and whether the
    /// pen drew its way here (false: it moved here in the air).
    struct Node {
        var x: Double
        var y: Double
        var width: Double
        var share: Double
        var down: Bool
    }

    private struct Stroke {
        var points: [CGPoint]
        var widths: [Double]
        var length: Double
    }

    private static func smoothstep(_ a: Double, _ b: Double, _ v: Double) -> Double {
        let u = M.clamp((v - a) / (b - a), 0, 1)
        return u * u * (3 - 2 * u)
    }

    private static func dist(_ a: CGPoint, _ b: CGPoint) -> Double { hypot(b.x - a.x, b.y - a.y) }

    /// Catmull-Rom through the points.
    private static func curve(_ p: [CGPoint]) -> [CGPoint] {
        guard p.count >= 3 else { return p }
        var out: [CGPoint] = []
        for i in 0..<(p.count - 1) {
            let p0 = p[max(i - 1, 0)], p1 = p[i], p2 = p[i + 1], p3 = p[min(i + 2, p.count - 1)]
            let steps = max(2, Int(ceil(dist(p1, p2) / (M.step * 0.5))))
            for s in 0..<steps {
                let u = Double(s) / Double(steps), u2 = u * u, u3 = u2 * u
                func at(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
                    0.5 * (2 * b + (c - a) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (3 * b - a - 3 * c + d) * u3)
                }
                out.append(CGPoint(x: at(p0.x, p1.x, p2.x, p3.x), y: at(p0.y, p1.y, p2.y, p3.y)))
            }
        }
        out.append(p[p.count - 1])
        return out
    }

    /// The same line through points `step` apart.
    private static func resample(_ p: [CGPoint]) -> [CGPoint] {
        var out = [p[0]]
        var carried = 0.0
        for i in 1..<p.count {
            let a = p[i - 1], b = p[i]
            let d = dist(a, b)
            if d == 0 { continue }
            var at = M.step - carried
            while at <= d {
                out.append(CGPoint(x: M.mix(a.x, b.x, at / d), y: M.mix(a.y, b.y, at / d)))
                at += M.step
            }
            carried = d - (at - M.step)
        }
        if dist(p[p.count - 1], out[out.count - 1]) > 0.2 { out.append(p[p.count - 1]) }
        return out
    }

    /// A pen stroke: a hand's wobble across the line, pressure that swells in and flicks out.
    private static func ink(_ through: [CGPoint], _ width: Double, _ seed: Double, smooth: Bool = true) -> Stroke {
        let line = resample(smooth ? curve(through) : through)
        let phase = [M.hash(seed) * 6.28, M.hash(seed + 1) * 6.28, M.hash(seed + 2) * 6.28]
        var lengths = [0.0]
        for i in 1..<max(line.count, 1) { lengths.append(lengths[i - 1] + dist(line[i - 1], line[i])) }
        let length = max(lengths[lengths.count - 1], 0.001)
        // Short strokes (a dot, an arrow head) wobble less.
        let wobble = M.jitter * M.clamp(length / 40, 0.15, 1)
        var points: [CGPoint] = []
        var widths: [Double] = []
        for i in line.indices {
            let before = line[max(i - 1, 0)], after = line[min(i + 1, line.count - 1)]
            let d0 = dist(before, after)
            let d = d0 == 0 ? 1 : d0
            let s = lengths[i]
            let off = wobble * (0.6 * sin(s * 0.21 + phase[0]) + 0.4 * sin(s * 0.47 + phase[1]))
            points.append(CGPoint(x: line[i].x - ((after.y - before.y) / d) * off, y: line[i].y + ((after.x - before.x) / d) * off))
            let u = s / length
            // A dot is pressed, not drawn: no taper.
            let pressure = length < 4
                ? 1.25
                : M.mix(M.taperIn, 1, smoothstep(0, M.taperInSpan, u)) * M.mix(1, M.taperOut, smoothstep(1 - M.taperOutSpan, 1, u))
            widths.append(width * pressure * (1 + M.widthNoise * sin(s * 0.33 + phase[2])))
        }
        return Stroke(points: points, widths: widths, length: length)
    }

    /// Flattens strokes into one mark; the pen takes `penLift` points of "air" between two strokes.
    private static func pack(_ strokes: [Stroke]) -> [Node] {
        let total = strokes.reduce(0) { $0 + $1.length } + M.penLift * Double(strokes.count - 1)
        var out: [Node] = []
        var done = 0.0
        for stroke in strokes {
            var along = 0.0
            for i in stroke.points.indices {
                if i > 0 { along += dist(stroke.points[i - 1], stroke.points[i]) }
                out.append(Node(x: stroke.points[i].x, y: stroke.points[i].y, width: stroke.widths[i], share: min((done + along) / total, 1), down: i != 0))
            }
            done += stroke.length + M.penLift
        }
        if !out.isEmpty { out[out.count - 1].share = 1 }
        return out
    }

    /// An ellipse round one band: a little over one turn, spiralling out so its end crosses its start.
    static func circleMark(_ band: Band, fontSize: Double, seed: Double) -> [Node] {
        let cx = (band.x0 + band.x1) / 2, cy = band.base - fontSize * 0.3
        let rx = (band.x1 - band.x0) / 2 + M.circlePadX, ry = fontSize * M.circleRy
        let count = 72
        let points: [CGPoint] = (0...count).map { i in
            let u = Double(i) / Double(count)
            let angle = M.circleStart + M.circleTurns * 2 * .pi * u
            let r = 1 + M.circleSpiral * (u - 0.5)
            let px = rx * r * cos(angle), py = ry * r * sin(angle)
            return CGPoint(x: cx + px * cos(M.circleTilt) - py * sin(M.circleTilt), y: cy + px * sin(M.circleTilt) + py * cos(M.circleTilt))
        }
        return pack([ink(points, M.penWidth, seed, smooth: false)])
    }

    /// A wave under each band, one stroke per line.
    static func underlineMark(_ bands: [Band], seed: Double) -> [Node] {
        pack(bands.enumerated().map { n, band in
            let x0 = band.x0 - M.waveOvershoot, x1 = band.x1 + M.waveOvershoot
            let phase = M.hash(seed + Double(n) * 7) * 6.28
            var points: [CGPoint] = []
            var x = x0
            while x <= x1 {
                let u = (x - x0) / (x1 - x0)
                points.append(CGPoint(x: x, y: band.base + M.waveDrop - M.waveRise * u + M.waveAmp * sin((2 * .pi * (x - x0)) / M.waveLength + phase)))
                x += 1.5
            }
            return ink(points, M.penWidth, seed + Double(n) * 13, smooth: false)
        })
    }

    /// A brace in the margin, from `top` to `bottom`, opening to the left of `x`.
    static func bracketMark(x: Double, top: Double, bottom: Double, seed: Double) -> (data: [Node], nose: CGPoint) {
        let curl = min(M.braceCurl, (bottom - top) / 4)
        let mid = (top + bottom) / 2
        let edge = x + M.braceDepth
        let nose = CGPoint(x: edge + M.braceNose, y: mid)
        let points = [
            CGPoint(x: x, y: top),
            CGPoint(x: edge - 1, y: top + curl * 0.45),
            CGPoint(x: edge, y: top + curl),
            CGPoint(x: edge, y: mid - curl),
            CGPoint(x: edge + M.braceNose * 0.5, y: mid - curl * 0.3),
            nose,
            CGPoint(x: edge + M.braceNose * 0.5, y: mid + curl * 0.3),
            CGPoint(x: edge, y: mid + curl),
            CGPoint(x: edge, y: bottom - curl),
            CGPoint(x: edge - 1, y: bottom - curl * 0.45),
            CGPoint(x: x, y: bottom),
        ]
        return (pack([ink(points, M.penWidth, seed)]), nose)
    }

    /// Handwriting, as strokes: x to the right, y down, 0 the top of a small letter and 1 the baseline.
    /// `advance` is the letter's width in the same unit.
    private static let letters: [Character: (advance: Double, strokes: [[[Double]]])] = [
        "e": (0.95, [[[0.12, 0.54], [0.5, 0.5], [0.84, 0.4], [0.74, 0.1], [0.45, 0], [0.16, 0.2], [0.08, 0.55], [0.22, 0.88], [0.5, 1], [0.88, 0.8]]]),
        "x": (0.9, [
            [[0.06, 0.02], [0.42, 0.48], [0.86, 1]],
            [[0.84, 0], [0.48, 0.5], [0.04, 1]],
        ]),
        "a": (1.02, [
            [[0.82, 0.22], [0.6, 0.02], [0.3, 0.04], [0.1, 0.36], [0.12, 0.76], [0.36, 0.98], [0.66, 0.8], [0.83, 0.34]],
            [[0.84, 0.02], [0.85, 0.6], [0.9, 0.92], [1.04, 0.97]],
        ]),
        "m": (1.45, [
            [[0.06, 0.02], [0.08, 0.5], [0.06, 1]],
            [[0.08, 0.4], [0.2, 0.1], [0.4, 0.02], [0.58, 0.2], [0.62, 0.6], [0.62, 1]],
            [[0.63, 0.4], [0.78, 0.1], [0.98, 0.02], [1.16, 0.2], [1.2, 0.6], [1.22, 1]],
        ]),
        "!": (0.4, [
            [[0.22, -0.78], [0.19, -0.1], [0.16, 0.5]],
            [[0.14, 0.93], [0.17, 1]],
        ]),
    ]

    /// A word in that hand, its baseline starting at (x, baseline), tilted about that point.
    static func noteMark(_ word: String, x: Double, baseline: Double, seed: Double) -> (data: [Node], width: Double) {
        var strokes: [Stroke] = []
        let c = cos(M.noteTilt), s = sin(M.noteTilt)
        var pen = 0.0
        var n = 0.0
        for letter in word {
            guard let glyph = letters[letter] else { pen += 0.6; continue }
            // Each letter sits a touch high or low, as a hand writes.
            let bob = (M.hash(seed + n * 3) - 0.5) * 0.12
            for stroke in glyph.strokes {
                let points = stroke.map { g -> CGPoint in
                    let px = (pen + g[0]) * M.noteXHeight, py = (g[1] - 1 + bob) * M.noteXHeight
                    return CGPoint(x: x + px * c - py * s, y: baseline + px * s + py * c)
                }
                strokes.append(ink(points, M.noteWidth, seed + n * 17 + Double(strokes.count)))
            }
            pen += glyph.advance + M.noteGap
            n += 1
        }
        return (pack(strokes), (pen - M.noteGap) * M.noteXHeight)
    }

    /// A curved shaft through the points and a two-stroke head at its end.
    static func arrowMark(_ through: [CGPoint], seed: Double) -> [Node] {
        let shaft = ink(through, M.penWidth, seed)
        let tip = shaft.points[shaft.points.count - 1]
        let back = shaft.points[max(shaft.points.count - 4, 0)]
        let angle = atan2(back.y - tip.y, back.x - tip.x)
        let head = [-M.headSpread, M.headSpread].enumerated().map { i, turn in
            ink([
                CGPoint(x: tip.x + M.headLength * cos(angle + turn), y: tip.y + M.headLength * sin(angle + turn)),
                CGPoint(x: tip.x + M.headLength * 0.4 * cos(angle + turn * 0.8), y: tip.y + M.headLength * 0.4 * sin(angle + turn * 0.8)),
                tip,
            ], M.penWidth, seed + 5 + Double(i), smooth: false)
        }
        return pack([shaft] + head)
    }

    // MARK: Drawing

    /// Draws `amount` (0…1) of a mark, from its start. The width is set per segment: that is the taper.
    static func drawInk(_ ctx: GraphicsContext, _ color: Color, _ data: [Node], _ amount: Double) {
        guard amount > 0.0005, data.count > 1 else { return }
        let shading = GraphicsContext.Shading.color(color)
        for i in 1..<data.count {
            let a = data[i - 1], b = data[i]
            if a.share >= amount { break }
            if !b.down { continue }
            var x1 = b.x, y1 = b.y
            if b.share > amount {
                let k = (amount - a.share) / (b.share - a.share)
                x1 = M.mix(a.x, b.x, k)
                y1 = M.mix(a.y, b.y, k)
            }
            var path = Path()
            path.move(to: CGPoint(x: a.x, y: a.y))
            path.addLine(to: CGPoint(x: x1, y: y1))
            ctx.stroke(path, with: shading, style: StrokeStyle(lineWidth: (a.width + b.width) / 2, lineCap: .round))
        }
    }

    /// Where a highlight's leading edge is when `amount` of it is drawn: (x, baseline).
    static func bandHead(_ bands: [Band], _ amount: Double) -> (x: Double, base: Double) {
        let total = bands.reduce(0) { $0 + $1.x1 - $1.x0 }
        var left = M.clamp(amount, 0, 1) * total
        for (i, band) in bands.enumerated() {
            let w = band.x1 - band.x0
            if left <= w || i == bands.count - 1 { return (band.x0 + min(left, w), band.base) }
            left -= w
        }
        return (0, 0)
    }

    /// Draws `amount` (0…1) of a highlighter swipe over the bands, line after line. The edges wobble with a
    /// seeded hash of the position along the line, so the part already drawn never changes as the swipe grows.
    /// The context must already be set to multiply.
    static func drawHighlight(_ ctx: GraphicsContext, _ tint: Color, _ bands: [Band], fontSize: Double, seed: Double, amount: Double) {
        guard amount > 0.0005 else { return }
        let total = bands.reduce(0) { $0 + $1.x1 - $1.x0 }
        var left = M.clamp(amount, 0, 1) * total
        for (i, band) in bands.enumerated() where left > 0.01 {
            let x0 = band.x0 - M.bandPadX
            let full = band.x1 + M.bandPadX - x0
            let shown = min(left / (full - 2 * M.bandPadX), 1) * full
            left -= full - 2 * M.bandPadX
            let top = band.base - M.bandUp * fontSize, bottom = band.base + M.bandDown * fontSize
            let steps = max(1, Int(ceil(shown / M.bandStep)))
            var path = Path()
            // The nib is a chisel: both ends lean.
            for k in 0...steps {
                let x = x0 + min(Double(k) * M.bandStep, shown)
                let y = top + (M.hash(seed + Double(i) * 31 + Double(k)) - 0.5) * 2 * M.bandWobble
                if k == 0 { path.move(to: CGPoint(x: x + M.bandSlant, y: y)) }
                else { path.addLine(to: CGPoint(x: x + (k == steps ? M.bandSlant : 0), y: y)) }
            }
            for k in stride(from: steps, through: 0, by: -1) {
                let x = x0 + min(Double(k) * M.bandStep, shown)
                let y = bottom + (M.hash(seed + Double(i) * 31 + Double(k) + 400) - 0.5) * 2 * M.bandWobble
                path.addLine(to: CGPoint(x: x, y: y))
            }
            path.closeSubpath()
            ctx.fill(path, with: .color(tint.opacity(M.bandOpacity)))
            // Where the nib pressed harder, the ink is a little deeper.
            let y = band.base + M.bandDown * fontSize * 0.2
            var streak = Path()
            streak.move(to: CGPoint(x: x0 + M.bandSlant, y: y))
            streak.addLine(to: CGPoint(x: x0 + shown, y: y))
            ctx.stroke(streak, with: .color(tint.opacity(M.bandStreak)), style: StrokeStyle(lineWidth: fontSize * 0.24, lineCap: .butt))
        }
    }
}
