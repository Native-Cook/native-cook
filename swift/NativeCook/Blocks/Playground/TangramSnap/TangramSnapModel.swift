import Foundation

// The tangram: the seven classic pieces cut from one square, four silhouettes made of them, and the
// springs that move the pieces. A step-for-step port of the React Native `tangramModel.ts`.
//
// Lengths are in units of the small triangle's leg (u). Cut from a square of side 2√2: two large
// triangles (legs 2), one medium (legs √2), two small (legs 1), a square (side 1) and a
// parallelogram (sides 1 and √2, angles 45° and 135°). Areas 2+2+1+1+1+½+½ = 8.
//
// A pose is a centre (the mean of the corners), a turn in steps of 45° and a mirror flag (mirrored
// across the local y axis before turning). Screen y points down, so a positive turn is clockwise.
// Each silhouette is written as the corners of its seven pieces; its slots (one pose per piece)
// are solved from those corners, which also proves the corners are an exact tangram.

enum TangramSnapKind {
    case large, medium, small, square, parallelogram
}

/// `Math.round`: the nearest integer, halves towards +∞.
func tangramSnapRound(_ v: Double) -> Double {
    let r = floor(v)
    return v - r >= 0.5 ? r + 1 : r
}

struct TangramSnapSlot {
    /// Centre in units, relative to the silhouette's centre.
    let x: Double
    let y: Double
    /// Every (turn step 0…7, flip) that lays the piece exactly on this slot.
    let turns: [Int]
    let flips: [Int]
}

struct TangramSnapSilhouette {
    let name: String
    /// The solid colour it fills with when solved (rgb 0…255).
    let color: (Double, Double, Double)
    let slots: [TangramSnapSlot]
    /// Half its width and height (units), for layout.
    let halfW: Double
    let halfH: Double
}

enum TangramSnapModel {
    private static let s2 = 2.0.squareRoot()
    private static let h2 = s2 / 2

    /// The pieces in a fixed order; slots of every silhouette follow the same order.
    static let pieces: [TangramSnapKind] = [.large, .large, .medium, .square, .parallelogram, .small, .small]

    /// Each kind's corners, unit = small triangle's leg, y down.
    private static func raw(_ kind: TangramSnapKind) -> [Double] {
        switch kind {
        case .large: return [0, 0, 2, 0, 0, -2]
        case .medium: return [0, 0, 2, 0, 1, -1]
        case .small: return [0, 0, 1, 0, 0, -1]
        case .square: return [0, 0, 1, 0, 1, -1, 0, -1]
        case .parallelogram: return [0, 0, 1, 0, 2, -1, 1, -1]
        }
    }

    private static func centre(_ points: [Double]) -> (x: Double, y: Double) {
        var x = 0.0
        var y = 0.0
        let n = points.count / 2
        for i in 0..<n {
            x += points[i * 2]
            y += points[i * 2 + 1]
        }
        return (x / Double(n), y / Double(n))
    }

    private static func centred(_ points: [Double]) -> [Double] {
        let c = centre(points)
        return points.enumerated().map { i, v in i % 2 == 0 ? v - c.x : v - c.y }
    }

    /// Each piece's corners, centred on their mean.
    static let shapes: [[Double]] = pieces.map { centred(raw($0)) }

    /// A shape's corners at a pose (turn in radians, flip 0/1), around (x, y), scaled by `unit`.
    static func placed(_ shape: [Double], _ x: Double, _ y: Double, _ angle: Double, _ flip: Double, _ unit: Double) -> [Double] {
        let c = cos(angle)
        let s = sin(angle)
        var out: [Double] = []
        out.reserveCapacity(shape.count)
        for i in stride(from: 0, to: shape.count, by: 2) {
            let lx = (flip > 0.5 ? -shape[i] : shape[i]) * unit
            let ly = shape[i + 1] * unit
            out.append(x + lx * c - ly * s)
            out.append(y + lx * s + ly * c)
        }
        return out
    }

    /// Whether two corner lists are the same set of points, within `eps`.
    private static func sameCorners(_ a: [Double], _ b: [Double], _ eps: Double) -> Bool {
        if a.count != b.count { return false }
        for i in stride(from: 0, to: a.count, by: 2) {
            var found = false
            for j in stride(from: 0, to: b.count, by: 2) where abs(a[i] - b[j]) < eps && abs(a[i + 1] - b[j + 1]) < eps {
                found = true
                break
            }
            if !found { return false }
        }
        return true
    }

    /// Solves the slots of a silhouette given as corners (y up, as drawn on squared paper).
    private static func solve(_ name: String, _ color: (Double, Double, Double), _ corners: [[Double]]) -> TangramSnapSilhouette {
        // To screen space: y down.
        let down = corners.map { p in p.enumerated().map { i, v in i % 2 == 1 ? -v : v } }
        var minX = Double.infinity
        var maxX = -Double.infinity
        var minY = Double.infinity
        var maxY = -Double.infinity
        for p in down {
            for i in stride(from: 0, to: p.count, by: 2) {
                minX = min(minX, p[i])
                maxX = max(maxX, p[i])
                minY = min(minY, p[i + 1])
                maxY = max(maxY, p[i + 1])
            }
        }
        let mx = (minX + maxX) / 2
        let my = (minY + maxY) / 2
        let slots = down.enumerated().map { k, c -> TangramSnapSlot in
            let ctr = centre(c)
            var turns: [Int] = []
            var flips: [Int] = []
            for flip in 0..<2 {
                for turn in 0..<8 {
                    let at = placed(shapes[k], ctr.x, ctr.y, (Double(turn) * Double.pi) / 4, Double(flip), 1)
                    if sameCorners(at, c, 1e-6) {
                        turns.append(turn)
                        flips.append(flip)
                    }
                }
            }
            precondition(!turns.isEmpty, "Tangram \(name): piece \(k) is not a \(pieces[k])")
            return TangramSnapSlot(x: ctr.x - mx, y: ctr.y - my, turns: turns, flips: flips)
        }
        return TangramSnapSilhouette(name: name, color: color, slots: slots, halfW: (maxX - minX) / 2, halfH: (maxY - minY) / 2)
    }

    // The silhouettes, piece by piece in `pieces` order: large, large, medium, square,
    // parallelogram, small, small. Corners in units, y up.
    static let silhouettes: [TangramSnapSilhouette] = [
        solve("The Cat", (0xE2, 0x60, 0x3A), [
            // Body: two large triangles make one big right triangle, chest upright, back sloping to the tail.
            [0, 0, 0, 2 * s2, s2, s2],
            [0, 0, 2 * s2, 0, s2, s2],
            // Front paws, against the chest.
            [0, 0, -s2, 0, 0, s2],
            // The head: a diamond on the shoulder.
            [0.5, 2 * s2 - 0.5, 0.5 + h2, 2 * s2 - 0.5 + h2, 0.5, 2 * s2 - 0.5 + s2, 0.5 - h2, 2 * s2 - 0.5 + h2],
            // The tail, along the ground.
            [2 * s2, 0, 2 * s2 + 1, 0, 2 * s2 + 2, 1, 2 * s2 + 1, 1],
            // Ears.
            [0.5 - h2, 2 * s2 - 0.5 + h2, 0.5, 2 * s2 - 0.5 + s2, 0.5 - h2, 2 * s2 - 0.5 + h2 + s2],
            [0.5 + h2, 2 * s2 - 0.5 + h2, 0.5, 2 * s2 - 0.5 + s2, 0.5 + h2, 2 * s2 - 0.5 + h2 + s2],
        ]),
        solve("The Boat", (0x2F, 0x6D, 0xB5), [
            // Mainsail, then the jib before the mast.
            [1, 1, 1, 3, 3, 1],
            [1, 1, 1, 1 + 2 * s2, 1 - s2, 1 + s2],
            // A pennant at the masthead.
            [1, 1 + 2 * s2, 1 + s2, 1 + 2 * s2, 1, 1 + 3 * s2],
            // The hull: a square between two small triangles.
            [1, 0, 2, 0, 2, 1, 1, 1],
            // The keel, raked aft.
            [1, 0, 2, 0, 3, -1, 2, -1],
            [0, 1, 1, 1, 1, 0],
            [2, 0, 2, 1, 3, 1],
        ]),
        solve("The Runner", (0xE3, 0xA5, 0x2C), [
            // Torso, shoulders up.
            [0, 4, 2 * s2, 4, s2, 4 - s2],
            // The back leg, kicked out behind.
            [s2, 4 - s2, -s2, 4 - s2, 0, 4 - 2 * s2],
            // The front thigh.
            [s2, 4 - s2, 2 * s2, 4 - 2 * s2, 2 * s2, 4 - s2],
            // The head.
            [s2 + 0.5, 4, s2 + 0.5 + h2, 4 + h2, s2 + 0.5, 4 + s2, s2 + 0.5 - h2, 4 + h2],
            // The front arm, reaching up.
            [2 * s2, 4, 2 * s2 + 1, 4, 2 * s2 + 2, 5, 2 * s2 + 1, 5],
            // The front foot, and the back arm.
            [2 * s2, 4 - 2 * s2, 2 * s2, 3 - 2 * s2, 2 * s2 + 1, 3 - 2 * s2],
            [0, 4, -1, 4, 0, 3],
        ]),
        solve("The Swan", (0xEF, 0xE6, 0xD6), [
            // The body: a large triangle on the water, the wing raised over its back.
            [0, 0, 2 * s2, 0, s2, s2],
            [2 * s2, s2, 2 * s2, s2 + 2, 2 * s2 - 2, s2],
            // The tail end of the body.
            [2 * s2, 0, 2 * s2, s2, s2, s2],
            // The head, a diamond on the neck.
            [0, 2 * s2, h2, 2 * s2 + h2, 0, 3 * s2, -h2, 2 * s2 + h2],
            // The neck, upright.
            [0, s2, h2, s2 + h2, h2, 2 * s2 + h2, 0, 2 * s2],
            // The breast.
            [0, 0, 0, s2, h2, h2],
            [0, s2, s2, s2, h2, h2],
        ]),
    ]

    /// The widest and tallest any silhouette gets (units), for sizing the board.
    static let maxHalfW = silhouettes.map(\.halfW).max() ?? 0
    static let maxHalfH = silhouettes.map(\.halfH).max() ?? 0

    /// Where each piece rests off the board, around the centre of the loose area: centre (units),
    /// turn (steps of 45°) and flip. Spread so none overlap; together 7.3 × 4.3 units.
    static let looseHalfW = 3.7
    static let looseHalfH = 2.2
    static let loose: [(Double, Double, Double, Double)] = [
        (-2.3, -0.45, 3, 0),
        (2.2, 0.6, 7, 0),
        (0, -1.05, 4, 0),
        (-0.45, 1.35, 1, 0),
        (1.45, -1.2, 2, 1),
        (-2, 1.6, 6, 0),
        (2.85, -1.5, 5, 0),
    ]

    // Springs (1/s², 1/s): a free piece, a piece held by the finger, the snap into a slot (a little
    // overshoot: the magnet), the turn, the flip and the lift.
    static let springFree = (210.0, 26.0)
    static let springHeld = (900.0, 52.0)
    static let springSnap = (420.0, 24.0)
    static let springTurn = (260.0, 28.0)
    static let springFlip = (180.0, 26.8)
    static let springLift = (380.0, 36.0)

    // autoPlay (s): pieces are set one after another, the figure fills, turns into the swan with
    // its pieces, holds, and comes apart back to the cat's empty outline.
    static let loop = 13.0
    static let placeFrom = 0.8
    static let placeEvery = 0.74
    static let placeTime = 0.58
    static let morphAt = 7.9
    static let resetAt = 10.3
    static let morphTime = 1.2
    /// The silhouette the loop morphs into once the cat is done.
    static let autoSecond = 3

    /// `a` moved by whole turns to the nearest of `b`.
    static func nearestTurn(_ a: Double, _ b: Double) -> Double {
        let full = Double.pi * 2
        return a + full * tangramSnapRound((b - a) / full)
    }

    /// The pose that lays a piece on `slot` with the least turning from its angle `angle`, keeping
    /// its flip `flip` unless `anyFlip`. Nil when the slot needs the other face up.
    static func slotPose(_ slot: TangramSnapSlot, _ angle: Double, _ flip: Int, _ anyFlip: Bool) -> (angle: Double, flip: Int)? {
        var best = -1
        var bestTurn = 0.0
        var bestCost = Double.infinity
        for k in 0..<slot.turns.count {
            if !anyFlip && slot.flips[k] != flip { continue }
            let turn = nearestTurn((Double(slot.turns[k]) * Double.pi) / 4, angle)
            let cost = abs(turn - angle) + (slot.flips[k] != flip ? 1.2 : 0)
            if cost < bestCost {
                bestCost = cost
                best = k
                bestTurn = turn
            }
        }
        if best < 0 { return nil }
        return (bestTurn, slot.flips[best])
    }

    /// Whether a piece turned to `angle` (any real angle) matches the slot's turn within `slack` rad.
    static func turnMatches(_ slot: TangramSnapSlot, _ angle: Double, _ flip: Int, _ slack: Double) -> Bool {
        for k in 0..<slot.turns.count where slot.flips[k] == flip {
            let turn = nearestTurn((Double(slot.turns[k]) * Double.pi) / 4, angle)
            if abs(turn - angle) <= slack { return true }
        }
        return false
    }

    /// A point is inside a convex polygon given as corners (either winding).
    static func insideConvex(_ points: [Double], _ x: Double, _ y: Double) -> Bool {
        var sign = 0
        let n = points.count / 2
        for i in 0..<n {
            let j = (i + 1) % n
            let cross = (points[j * 2] - points[i * 2]) * (y - points[i * 2 + 1]) - (points[j * 2 + 1] - points[i * 2 + 1]) * (x - points[i * 2])
            if cross != 0 {
                let s = cross > 0 ? 1 : -1
                if sign == 0 { sign = s } else if s != sign { return false }
            }
        }
        return true
    }

    /// A convex polygon pulled in by `d` along every edge (corners where the moved edges meet).
    static func inset(_ points: [Double], _ d: Double) -> [Double] {
        let n = points.count / 2
        // Winding: positive area means the inside is to the left of each edge (y down).
        var area = 0.0
        for i in 0..<n {
            let j = (i + 1) % n
            area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
        }
        let side = area > 0 ? 1.0 : -1.0
        var out: [Double] = []
        out.reserveCapacity(points.count)
        for i in 0..<n {
            let h = (i + n - 1) % n
            let j = (i + 1) % n
            // Inward normals of the edges before and after corner i.
            var ax = points[i * 2] - points[h * 2]
            var ay = points[i * 2 + 1] - points[h * 2 + 1]
            var la = hypot(ax, ay)
            var bx = points[j * 2] - points[i * 2]
            var by = points[j * 2 + 1] - points[i * 2 + 1]
            let lb = hypot(bx, by)
            // A degenerate corner (a piece squeezed edge-on): keep the corner rather than divide by zero.
            if !(la > 1e-6) || !(lb > 1e-6) {
                out.append(points[i * 2])
                out.append(points[i * 2 + 1])
                continue
            }
            ax /= la
            ay /= la
            bx /= lb
            by /= lb
            let nax = -ay * side
            let nay = ax * side
            let nbx = -by * side
            let nby = bx * side
            // Corner of the two moved edges: along the bisector by d / cos(half the turn).
            let mx = nax + nbx
            let my = nay + nby
            la = mx * nax + my * nay
            if !(la > 1e-3) {
                out.append(points[i * 2])
                out.append(points[i * 2 + 1])
                continue
            }
            out.append(points[i * 2] + (mx * d) / la)
            out.append(points[i * 2 + 1] + (my * d) / la)
        }
        return out
    }
}

/// The seven pieces' poses, speeds, targets, spring modes, slots and drawing order.
final class TangramSnapPieces {
    private typealias M = TangramSnapModel

    var x = [Double](repeating: 0, count: 7)
    var y = [Double](repeating: 0, count: 7)
    var angle = [Double](repeating: 0, count: 7)
    /// 0…1: 0 face up, 1 mirrored; drawn as a horizontal squeeze through zero.
    var flip = [Double](repeating: 0, count: 7)
    var lift = [Double](repeating: 0, count: 7)
    var vx = [Double](repeating: 0, count: 7)
    var vy = [Double](repeating: 0, count: 7)
    var va = [Double](repeating: 0, count: 7)
    var vf = [Double](repeating: 0, count: 7)
    var vl = [Double](repeating: 0, count: 7)
    var tx = [Double](repeating: 0, count: 7)
    var ty = [Double](repeating: 0, count: 7)
    var ta = [Double](repeating: 0, count: 7)
    var tf = [Double](repeating: 0, count: 7)
    var tl = [Double](repeating: 0, count: 7)
    /// Which spring moves the piece: 0 free, 1 held, 2 snapping.
    var mode = [Int](repeating: 0, count: 7)
    /// The slot it sits in, or -1.
    var slot = [Int](repeating: -1, count: 7)
    /// Drawing order, bottom first.
    var order = Array(0..<7)

    func copy(from: TangramSnapPieces) {
        x = from.x
        y = from.y
        angle = from.angle
        flip = from.flip
        lift = from.lift
        vx = from.vx
        vy = from.vy
        va = from.va
        vf = from.vf
        vl = from.vl
        tx = from.tx
        ty = from.ty
        ta = from.ta
        tf = from.tf
        tl = from.tl
        mode = from.mode
        slot = from.slot
        order = from.order
    }

    /// Moves piece `i` to the top of the drawing order.
    func raise(_ i: Int) {
        var at = 0
        for k in 0..<order.count where order[k] == i { at = k }
        for k in at..<(order.count - 1) { order[k] = order[k + 1] }
        order[order.count - 1] = i
    }

    private static func spring(_ value: inout Double, _ velocity: inout Double, _ target: Double, _ k: Double, _ c: Double, _ dt: Double) {
        let v = velocity + (k * (target - value) - c * velocity) * dt
        value += v * dt
        velocity = v
    }

    /// One tick of every piece's springs.
    func step(_ dt: Double, calm: Bool) {
        for i in 0..<7 {
            let m = mode[i]
            let pos = m == 1 ? M.springHeld : m == 2 ? M.springSnap : M.springFree
            // Reduced motion: the snap settles without overshoot.
            let damp = calm && m == 2 ? 2 * pos.0.squareRoot() : pos.1
            TangramSnapPieces.spring(&x[i], &vx[i], tx[i], pos.0, damp, dt)
            TangramSnapPieces.spring(&y[i], &vy[i], ty[i], pos.0, damp, dt)
            TangramSnapPieces.spring(&angle[i], &va[i], ta[i], M.springTurn.0, M.springTurn.1, dt)
            TangramSnapPieces.spring(&flip[i], &vf[i], tf[i], M.springFlip.0, M.springFlip.1, dt)
            TangramSnapPieces.spring(&lift[i], &vl[i], tl[i], M.springLift.0, M.springLift.1, dt)
        }
    }
}
