import Foundation
import SwiftUI

/// The five operations on the keys, in the order the keys are stacked on the screen (`KEYS` in the
/// React Native file). Each case holds the character the key shows.
enum TactileCalculatorWelcomeSymbol: Hashable, CaseIterable {
    case minus
    case divide
    case plus
    case multiply
    case equals
}

/// Isometric key geometry, a line-for-line port of `keyGeometry.ts`: flat faces, a hairline ink
/// outline, a lit (left-facing) wall and an ink-black (right-facing) wall. The rounded-square
/// outlines are sampled once; only the body wall's top edge moves with the cap, so a frame only
/// has to rebuild the walls from the flat point lists.
///
/// All numbers are in "plate units", the 216 x 150 viewBox the key is drawn in. The static
/// paths are rounded to two decimals like the strings of the React Native file (`fmt`).
enum TactileCalculatorWelcomeGeometry {
    struct Point {
        var x: Double
        var y: Double
    }

    /// One point of a sampled outline and its outward normal.
    struct Sample {
        var p: Point
        var n: Point
    }

    /// One bar or dot of a raised symbol: the top face and the walls beside it.
    struct GlyphPart {
        let top: Path
        let lit: [Path]
        let ink: [Path]
    }

    // MARK: Constants

    /// `CENTER`: the face is a rounded square centred on (108, 65).
    static let center = Point(x: 108, y: 65)
    /// `FACE_SIDE`.
    static let faceSide: Double = 114
    /// `FACE_RADIUS`.
    static let faceRadius: Double = 31
    /// `DEPTH`: wall height of the key body.
    static let depth: Double = 14
    /// `GLYPH_RISE`: raised symbols stand this far above the cap.
    static let glyphRise: Double = 6.5

    // MARK: Projection

    /// `iso`: local plane to screen, the projection the symbols always used.
    static func iso(_ point: Point) -> Point {
        let x = center.x + 0.94 * point.x - 0.94 * point.y
        let y = center.y + 0.53 * point.x + 0.53 * point.y
        return Point(x: x, y: y)
    }

    // MARK: Sampling

    /// `roundedRect`: a rounded rectangle sampled every 7.5 degrees (13 points a corner), turned
    /// by `turn` radians and moved to `at`, with its outward normals.
    static func roundedRect(
        _ w: Double, _ h: Double, _ r: Double, turn: Double = 0, at: Point = Point(x: 0, y: 0)
    ) -> [Sample] {
        var out: [Sample] = []
        let corners: [(cx: Double, cy: Double, start: Double)] = [
            (w / 2 - r, -h / 2 + r, -90),
            (w / 2 - r, h / 2 - r, 0),
            (-w / 2 + r, h / 2 - r, 90),
            (-w / 2 + r, -h / 2 + r, 180),
        ]
        let c = cos(turn)
        let s = sin(turn)
        for corner in corners {
            for i in 0...12 {
                let degrees: Double = corner.start + Double(i) * 7.5
                let a: Double = degrees * Double.pi / 180
                let nx = cos(a)
                let ny = sin(a)
                let x = corner.cx + r * nx
                let y = corner.cy + r * ny
                let px = at.x + x * c - y * s
                let py = at.y + x * s + y * c
                let qx = nx * c - ny * s
                let qy = nx * s + ny * c
                out.append(Sample(p: Point(x: px, y: py), n: Point(x: qx, y: qy)))
            }
        }
        return out
    }

    /// `label`: a wall is visible when its outward normal points down the screen. It is lit
    /// (1) when it faces left, since the key light sits upper-left, and ink (2) when it faces
    /// right. 0 is a wall that is not visible.
    private static func label(_ sample: Sample) -> Int {
        if sample.n.x + sample.n.y <= 1e-6 {
            return 0
        }
        return sample.n.y >= sample.n.x ? 1 : 2
    }

    /// `runs`: contiguous visible runs of one label, in boundary order, endpoints shared.
    static func runs(_ shape: [Sample], kind: Int) -> [[Point]] {
        let count = shape.count
        let labels = shape.map { label($0) }
        var start = -1
        for i in 0..<count where labels[i] != kind && labels[(i + 1) % count] == kind {
            start = i
            break
        }
        if start < 0 {
            return labels[0] == kind ? [shape.map { $0.p }] : []
        }
        var result: [[Point]] = []
        var current: [Point]?
        for k in 1...count {
            let i = (start + k) % count
            if labels[i] == kind {
                if current == nil {
                    current = [shape[(i - 1 + count) % count].p]
                }
                current?.append(shape[i].p)
            } else if let run = current {
                result.append(run + [shape[i].p])
                current = nil
            }
        }
        if let run = current {
            result.append(run)
        }
        return result
    }

    // MARK: Paths

    /// `fmt`: `Math.round(v * 100) / 100`.
    private static func fmt(_ value: Double) -> Double {
        (value * 100 + 0.5).rounded(.down) / 100
    }

    /// `polyline` / `closed`: the points as a path, rounded to two decimals and moved by `dy`.
    static func polyline(_ points: [Point], dy: Double = 0, dx: Double = 0, close: Bool) -> Path {
        var path = Path()
        for (index, point) in points.enumerated() {
            let target = CGPoint(x: fmt(point.x + dx), y: fmt(point.y + dy))
            if index == 0 {
                path.move(to: target)
            } else {
                path.addLine(to: target)
            }
        }
        if close {
            path.closeSubpath()
        }
        return path
    }

    /// `wall`: the wall between the same projected chain at two heights.
    static func wall(_ chain: [Point], top: Double, bottom: Double) -> Path {
        var path = polyline(chain, dy: top, close: false)
        for point in chain.reversed() {
            path.addLine(to: CGPoint(x: fmt(point.x), y: fmt(point.y + bottom)))
        }
        path.closeSubpath()
        return path
    }

    /// The `chainWall` worklet: a wall from the flat `[x, y, x, y, ...]` list at runtime, with
    /// the numbers as they are (no rounding), because only its top edge moves with the cap.
    static func chainWall(_ flat: [Double], top: Double, bottom: Double) -> Path {
        var path = Path()
        var i = 0
        while i < flat.count {
            let point = CGPoint(x: flat[i], y: flat[i + 1] + top)
            if i == 0 {
                path.move(to: point)
            } else {
                path.addLine(to: point)
            }
            i += 2
        }
        var j = flat.count - 2
        while j >= 0 {
            path.addLine(to: CGPoint(x: flat[j], y: flat[j + 1] + bottom))
            j -= 2
        }
        path.closeSubpath()
        return path
    }

    // MARK: Key body

    /// `face`: the key top, sampled once.
    private static let face: [Sample] = roundedRect(faceSide, faceSide, faceRadius)

    /// `FACE_PATH`.
    static let facePath: Path = polyline(face.map { iso($0.p) }, close: true)

    /// `RING_PATH`: the thin rim ring, the face grown by 9 and lowered to the foot of the body.
    static let ringPath: Path = polyline(
        roundedRect(faceSide + 9, faceSide + 9, faceRadius + 4.5).map { iso($0.p) },
        dy: depth + 1.2,
        close: true
    )

    /// `BODY_LIT`: the projected chain of the lit wall.
    static let bodyLit: [Point] = runs(face, kind: 1)[0].map { iso($0) }

    /// `BODY_INK`: the projected chain of the ink wall.
    static let bodyInk: [Point] = runs(face, kind: 2)[0].map { iso($0) }

    /// `BODY_LIT_FLAT` / `BODY_INK_FLAT`: the chains as `[x, y, x, y, ...]`.
    static let bodyLitFlat: [Double] = bodyLit.flatMap { [$0.x, $0.y] }
    static let bodyInkFlat: [Double] = bodyInk.flatMap { [$0.x, $0.y] }

    /// `BODY_FOOT`: the silhouette's lower edge at ground level, for the outline stroke (open).
    static let bodyFoot: Path = polyline(bodyInk + Array(bodyLit.dropFirst()), dy: depth, close: false)

    /// `FRONT`: front corner of the body, where the ink wall (right) meets the lit wall (left).
    static let front: Point = bodyLit[0]

    /// `STRIPES`: two pinstripes catch the light just left of the front corner, as on the
    /// reference keys. Each is the point on the lit chain that is 4.5 and 9 units left of it.
    static let stripes: [Point] = ([4.5, 9.0] as [Double]).map { offset -> Point in
        let x = front.x - offset
        var y = front.y
        for i in 1..<bodyLit.count {
            let a = bodyLit[i - 1]
            let b = bodyLit[i]
            if (a.x - x) * (b.x - x) <= 0 && a.x != b.x {
                y = a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
                break
            }
        }
        return Point(x: x, y: y)
    }

    /// `FACE_GLINT`: the highlight that catches the two upper rims of the face.
    static let faceGlint: Path = { () -> Path in
        let inner = roundedRect(faceSide - 9, faceSide - 9, faceRadius - 4.5)
        // Normals facing up the screen are the upper rims; walk them as one cyclic run.
        let up = inner.map { $0.n.x + $0.n.y < -0.35 }
        let count = inner.count
        var first = -1
        for i in 0..<count where up[i] && !up[(i - 1 + count) % count] {
            first = i
            break
        }
        guard first >= 0 else {
            return Path()
        }
        var top: [Point] = []
        var k = 0
        while k < count && up[(first + k) % count] {
            top.append(iso(inner[(first + k) % count].p))
            k += 1
        }
        return polyline(top, close: false)
    }()

    // MARK: Symbols

    /// `part`: one raised bar or dot.
    private static func part(_ shape: [Sample]) -> GlyphPart {
        let rise = -glyphRise
        return GlyphPart(
            top: polyline(shape.map { iso($0.p) }, dy: rise, close: true),
            lit: runs(shape, kind: 1).map { run in wall(run.map { iso($0) }, top: rise, bottom: 0) },
            ink: runs(shape, kind: 2).map { run in wall(run.map { iso($0) }, top: rise, bottom: 0) }
        )
    }

    /// `UPRIGHT`: the upright symbols are turned 45 degrees before the projection.
    private static let upright: Double = -Double.pi / 4
    /// `BAR_LONG` and `BAR`.
    private static let barLong: Double = 74
    private static let bar: Double = 21

    /// `GLYPHS`: the parts of each symbol, drawn together as one raised outline.
    static let glyphs: [TactileCalculatorWelcomeSymbol: [GlyphPart]] = { () -> [TactileCalculatorWelcomeSymbol: [GlyphPart]] in
        let longBar = roundedRect(barLong, bar, bar / 2)
        let shortBar = roundedRect(bar, barLong, bar / 2)
        let thinBar = roundedRect(barLong, bar - 2, (bar - 2) / 2, turn: upright)
        let dotAngleUp: Double = -3 * Double.pi / 4
        let dotAngleDown: Double = Double.pi / 4
        let dotUp = roundedRect(
            20, 20, 10, at: Point(x: cos(dotAngleUp) * 31, y: sin(dotAngleUp) * 31)
        )
        let dotDown = roundedRect(
            20, 20, 10, at: Point(x: cos(dotAngleDown) * 31, y: sin(dotAngleDown) * 31)
        )
        let upperBar = roundedRect(barLong, bar, bar / 2, at: Point(x: 0, y: -15))
        let lowerBar = roundedRect(barLong, bar, bar / 2, at: Point(x: 0, y: 15))
        return [
            .plus: [
                roundedRect(barLong, bar, bar / 2, turn: upright),
                roundedRect(bar, barLong, bar / 2, turn: upright),
            ].map { part($0) },
            .multiply: [longBar, shortBar].map { part($0) },
            .minus: [longBar].map { part($0) },
            .equals: [upperBar, lowerBar].map { part($0) },
            .divide: [thinBar, dotUp, dotDown].map { part($0) },
        ]
    }()
}
