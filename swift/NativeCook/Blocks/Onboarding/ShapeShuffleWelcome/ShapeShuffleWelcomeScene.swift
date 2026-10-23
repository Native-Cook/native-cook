import SwiftUI

/// The still life: eleven pieces that keep their identity while they are rebuilt into four
/// buildings. A composition gives every piece a place, a size, a turn, a shape and a colour; a
/// piece it has no use for shrinks to nothing behind a neighbour.
enum ShapeShuffleWelcomeScene {
    /// A composition rests for `rest` seconds and takes `change` to become the next; four of them make `loop`.
    static let rest = 1.4
    static let change = 0.8
    static let step = rest + change
    static let loop = step * 4
    /// After a touch autoPlay waits this long before it goes back to the first composition and starts over.
    static let userHold = 3.0

    // Each piece sets off a little after the one on its left, up to `delay` late, and lands on a
    // spring that carries it a few points past its place.
    static let delay = 0.12
    static let land = (stiffness: 150.0, damping: 17.0)
    // Shapes and colours do not overshoot: they blend over `blend` seconds.
    static let blend = 0.3
    // A piece that is thrown follows an arc that takes this long.
    static let arc = 0.55
    // A piece that was dragged away springs back.
    static let back = (stiffness: 220.0, damping: 16.0)
    static let backFor = 0.8
    // The scene is this wide; pieces are placed from its middle, and from the ground up.
    static let scene = 220.0

    static let lightest: UInt32 = 0xf4f7fc
    static let light: UInt32 = 0xd4d8db
    static let mid: UInt32 = 0xadb1b4
    static let dark: UInt32 = 0x858b93
    static let indigo: UInt32 = 0x4f46e5
    static let amber: UInt32 = 0xf59e0b
    static let teal: UInt32 = 0x14b8a6
    static let coral: UInt32 = 0xf9735b

    /// A piece in one composition: centre x from the scene's middle, centre y above the ground,
    /// width, height, turn in degrees, radius of the top corners and of the bottom ones, taper
    /// (1 brings the top edge to a point: a roof), cut (1 cuts a block into four steps), how high
    /// it is thrown on its way here, colour.
    struct Spot {
        let x, y, w, h, turn, top, bottom, taper, cut, lift: Double
        let color: UInt32

        init(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ turn: Double, _ top: Double, _ bottom: Double, _ taper: Double, _ cut: Double, _ lift: Double, _ color: UInt32) {
            (self.x, self.y, self.w, self.h, self.turn) = (x, y, w, h, turn)
            (self.top, self.bottom, self.taper, self.cut, self.lift, self.color) = (top, bottom, taper, cut, lift, color)
        }
    }

    struct Piece {
        let name: String
        /// A block that can be cut into steps; the others can be rounded and tapered.
        let steps: Bool
        /// What a whole loop adds to the piece's rotation.
        let turn: Double
        let spots: [Spot]
    }

    /// The pieces, back to front.
    static let pieces: [Piece] = [
        Piece(name: "base", steps: true, turn: 360, spots: [
            Spot(-80, 29, 58, 58, 0, 0, 0, 0, 0, 40, mid),
            Spot(-82, 29, 58, 58, 90, 0, 0, 0, 0, 0, mid),
            Spot(-61.5, 29, 87, 58, 180, 0, 0, 0, 0, 0, mid),
            Spot(-76, 29, 58, 58, 270, 0, 0, 0, 1, 0, mid),
        ]),
        Piece(name: "backing", steps: false, turn: 360, spots: [
            Spot(-22, 29, 58, 58, 0, 0, 0, 0, 0, 50, lightest),
            Spot(-24, 29, 0, 0, 90, 0, 0, 0, 0, 0, lightest),
            Spot(33, 58, 0, 0, 90, 0, 0, 0, 0, 0, lightest),
            Spot(-18, 58, 0, 0, 90, 0, 0, 0, 0, 0, lightest),
        ]),
        Piece(name: "stairs", steps: true, turn: 360, spots: [
            Spot(-22, 29, 58, 58, 0, 0, 0, 0, 1, 60, indigo),
            Spot(-24, 29, 58, 58, 90, 0, 0, 0, 0, 0, light),
            Spot(33, 58, 58, 116, 180, 0, 0, 0, 0, 0, light),
            Spot(-18, 58, 58, 116, 180, 0, 0, 0, 0, 0, light),
        ]),
        Piece(name: "upper left", steps: false, turn: 360, spots: [
            Spot(-22, 87, 58, 58, 0, 0, 0, 0, 0, 70, light),
            Spot(-24, 29, 0, 0, 90, 0, 0, 0, 0, 0, light),
            Spot(33, 58, 0, 0, 90, 0, 0, 0, 0, 0, light),
            Spot(-18, 58, 0, 0, 90, 0, 0, 0, 0, 0, light),
        ]),
        Piece(name: "upper right", steps: false, turn: 360, spots: [
            Spot(36, 87, 58, 58, 0, 0, 0, 0, 0, 80, lightest),
            Spot(86, 29, 0, 0, 90, 0, 0, 0, 0, 0, light),
            Spot(33, 58, 0, 0, 90, 0, 0, 0, 0, 0, light),
            Spot(40, 29, 0, 0, 90, 0, 0, 0, 0, 0, dark),
        ]),
        Piece(name: "front", steps: false, turn: 0, spots: [
            Spot(36, 29, 58, 58, 0, 0, 0, 0, 0, 50, light),
            Spot(86, 29, 58, 58, 90, 0, 0, 0, 0, 0, light),
            Spot(-11, 43.5, 58, 87, 0, 29, 0, 0, 0, 0, dark),
            Spot(40, 29, 58, 58, 0, 0, 0, 0, 0, 0, dark),
        ]),
        Piece(name: "window", steps: false, turn: 0, spots: [
            Spot(36, 87, 30, 30, 0, 15, 15, 0, 0, 60, mid),
            Spot(86, 22, 0, 0, 0, 15, 15, 0, 0, 0, mid),
            Spot(33, 92, 0, 0, 0, 15, 15, 0, 0, 0, lightest),
            Spot(-18, 92, 30, 30, 0, 15, 15, 0, 0, 0, lightest),
        ]),
        Piece(name: "roof", steps: false, turn: 360, spots: [
            Spot(7, 137, 116, 42, 0, 0, 0, 1, 0, 80, mid),
            Spot(-53, 79, 116, 42, 0, 0, 0, 1, 0, 0, dark),
            Spot(33, 130.5, 58, 29, 0, 29, 0, 0, 0, 0, mid),
            Spot(66, 65, 110, 14, 0, 0, 0, 0, 0, 0, mid),
        ]),
        Piece(name: "accent", steps: false, turn: 0, spots: [
            Spot(91, 11, 44, 22, 0, 22, 0, 0, 0, 40, amber),
            Spot(31, 59, 30, 30, 0, 15, 15, 0, 0, 0, teal),
            Spot(-40, 100, 30, 30, 0, 15, 15, 0, 0, 40, indigo),
            Spot(95, 11, 44, 22, 0, 22, 0, 0, 0, 0, coral),
        ]),
        Piece(name: "ball", steps: false, turn: 0, spots: [
            Spot(91, 44, 44, 44, 0, 22, 22, 0, 0, 60, amber),
            Spot(31, 22, 44, 44, 0, 22, 22, 0, 0, 0, teal),
            Spot(89, 22, 44, 44, 0, 22, 22, 0, 0, 0, mid),
            Spot(95, 11, 0, 0, 0, 22, 22, 0, 0, 0, coral),
        ]),
        Piece(name: "bowl", steps: false, turn: 360, spots: [
            Spot(-80, 69, 44, 22, 0, 0, 22, 0, 0, 50, light),
            Spot(86, 22, 29, 44, 180, 0, 14.5, 0, 0, 0, mid),
            Spot(-11, 30, 0, 0, 180, 0, 14.5, 0, 0, 0, dark),
            Spot(40, 29, 0, 0, 180, 0, 14.5, 0, 0, 0, dark),
        ]),
    ]

    static let count = 4

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    /// How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots.
    static func spring(_ t: Double, _ spring: (stiffness: Double, damping: Double)) -> Double {
        guard t > 0 else { return 0 }
        let w = spring.stiffness.squareRoot()
        let z = spring.damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }

    /// How much of a drag is left `t` seconds after the piece was let go.
    static func dragLeft(_ t: Double) -> Double {
        t < 0 ? 1 : t >= backFor ? 0 : 1 - spring(t, back)
    }

    struct Now {
        var x, y, w, h, rotation, top, bottom, taper, cut: Double
        var color: Color
    }

    /// A piece `e` seconds into the change from composition `from` to composition `to`.
    static func piece(_ index: Int, from: Int, to: Int, _ e: Double) -> Now {
        let piece = pieces[index]
        let a = piece.spots[from]
        let b = piece.spots[to]
        let since = e - delay * clamp01((b.x + scene / 2) / scene)
        // Places, sizes and turns land on the spring; shapes and colours blend.
        let p = from == to ? 1 : spring(since, land)
        let q = from == to ? 1 : smooth(since, 0, blend)
        func mix(_ from: Double, _ to: Double, _ by: Double) -> Double { from + (to - from) * by }
        func channel(_ shift: UInt32) -> Double { mix(Double((a.color >> shift) & 0xff), Double((b.color >> shift) & 0xff), q) / 255 }
        // A loop's last change brings the rotation round to where it started.
        let turned = b.turn + (to < from ? piece.turn : 0)
        return Now(
            x: mix(a.x, b.x, p),
            y: mix(a.y, b.y, p) + (from == to ? 0 : b.lift * sin(.pi * clamp01(since / arc))),
            w: max(0, mix(a.w, b.w, p)),
            h: max(0, mix(a.h, b.h, p)),
            rotation: mix(a.turn, turned, p),
            top: mix(a.top, b.top, q),
            bottom: mix(a.bottom, b.bottom, q),
            taper: mix(a.taper, b.taper, q),
            cut: mix(a.cut, b.cut, q),
            color: Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
        )
    }

    /// A quarter circle as one curve.
    static let k = 0.5523

    /// The outline of a piece round its own centre. A block is a four-cornered shape whose top
    /// edge narrows with `taper` and whose corners are rounded as far as its sides allow; a
    /// stepped block is a rectangle whose upper right is cut away in four steps, as deep as `cut`.
    static func outline(steps: Bool, _ n: Now) -> Path {
        let l = -n.w / 2
        let t = -n.h / 2
        var path = Path()
        if steps {
            path.move(to: CGPoint(x: l, y: -t))
            path.addLine(to: CGPoint(x: l, y: t))
            for i in 1...4 {
                let x = l + n.w * Double(i) / 4
                path.addLine(to: CGPoint(x: x, y: t + n.h * Double(i - 1) / 4 * n.cut))
                if i < 4 { path.addLine(to: CGPoint(x: x, y: t + n.h * Double(i) / 4 * n.cut)) }
            }
            path.addLine(to: CGPoint(x: -l, y: -t))
            path.closeSubpath()
            return path
        }
        let inset = n.w / 2 * n.taper
        let xs = [l + inset, -l - inset, -l, l]
        let ys = [t, t, -t, -t]
        let radii = [n.top, n.top, n.bottom, n.bottom]
        for i in 0..<4 {
            let before = (i + 3) % 4
            let after = (i + 1) % 4
            let toBefore = hypot(xs[before] - xs[i], ys[before] - ys[i])
            let toAfter = hypot(xs[after] - xs[i], ys[after] - ys[i])
            // Two corners share a side in proportion to their radii when it is too short for both.
            let shareBefore = radii[i] + radii[before] > toBefore ? toBefore / (radii[i] + radii[before]) : 1
            let shareAfter = radii[i] + radii[after] > toAfter ? toAfter / (radii[i] + radii[after]) : 1
            let r = radii[i] * min(shareBefore, shareAfter)
            let fb = toBefore > 0 ? r / toBefore : 0
            let fa = toAfter > 0 ? r / toAfter : 0
            let a = CGPoint(x: xs[i] + (xs[before] - xs[i]) * fb, y: ys[i] + (ys[before] - ys[i]) * fb)
            let b = CGPoint(x: xs[i] + (xs[after] - xs[i]) * fa, y: ys[i] + (ys[after] - ys[i]) * fa)
            if i == 0 { path.move(to: a) } else { path.addLine(to: a) }
            path.addCurve(
                to: b,
                control1: CGPoint(x: a.x + (xs[i] - a.x) * k, y: a.y + (ys[i] - a.y) * k),
                control2: CGPoint(x: b.x + (xs[i] - b.x) * k, y: b.y + (ys[i] - b.y) * k)
            )
        }
        path.closeSubpath()
        return path
    }
}
