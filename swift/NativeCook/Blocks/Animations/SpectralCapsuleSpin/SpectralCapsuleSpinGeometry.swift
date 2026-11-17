import CoreGraphics
import Foundation

/// The capsule's pose and its projected outlines, from one tilt angle `phi`.
///
/// The capsule is a cylinder of radius `r` between two seams `2h` apart, closed by two hemispheres.
/// It turns about the screen's anti-diagonal `v` through `center`; at `phi = 0` it lies flat along
/// the diagonal `u` with end A at the top right, at `phi = π/2` end A points at the viewer.
/// Every outline is a polygon sampled the same way in the three stacks.
struct SpectralCapsuleSpinGeometry {
    static let u = CGPoint(x: 0.70710678, y: -0.70710678)
    static let v = CGPoint(x: 0.70710678, y: 0.70710678)
    /// Camera distance in points: a weak perspective, ends ×1.06 / ×0.95 at end-on.
    static let focal = 2000.0

    let center: CGPoint
    let r: Double
    let h: Double
    let phi: Double

    let sinP: Double
    let cosP: Double
    /// How much the near cap faces the viewer: 0 flat, 1 end-on.
    let facing: Double
    let cA: CGPoint
    let cB: CGPoint
    let rA: Double
    let rB: Double
    let nearIsA: Bool
    /// The capsule's axis on screen (unit, towards end A) and the direction across it.
    let axis: CGPoint
    let across: CGPoint

    /// The camera looks down on the turn by this angle, so end-on the body's rings stack below the disc.
    static let tilt = 0.09

    init(center: CGPoint, r: Double, h: Double, phi: Double) {
        self.center = center
        self.r = r
        self.h = h
        self.phi = phi
        sinP = sin(phi)
        cosP = cos(phi)
        let u = Self.u
        let x0 = cosP * u.x
        let y0 = cosP * u.y
        let z0 = sinP
        let dx = x0
        let dy = y0 * cos(Self.tilt) - z0 * sin(Self.tilt)
        let dz = y0 * sin(Self.tilt) + z0 * cos(Self.tilt)
        facing = abs(dz)
        let zA = h * dz
        let sA = Self.focal / (Self.focal - zA)
        let sB = Self.focal / (Self.focal + zA)
        cA = CGPoint(x: center.x + dx * h * sA, y: center.y + dy * h * sA)
        cB = CGPoint(x: center.x - dx * h * sB, y: center.y - dy * h * sB)
        rA = r * sA
        rB = r * sB
        nearIsA = dz >= 0
        let length = (dx * dx + dy * dy).squareRoot()
        axis = length > 1e-6 ? CGPoint(x: dx / length, y: dy / length) : u
        across = CGPoint(x: -axis.y, y: axis.x)
    }

    var near: (c: CGPoint, r: Double) { nearIsA ? (cA, rA) : (cB, rB) }
    var far: (c: CGPoint, r: Double) { nearIsA ? (cB, rB) : (cA, rA) }

    /// The screen direction from the near cap's centre towards its pole.
    var outward: CGPoint {
        nearIsA ? axis : CGPoint(x: -axis.x, y: -axis.y)
    }

    /// Convex hull of the two end discs: the silhouette (exact for a capsule seen from afar).
    func silhouette(steps: Int = 40) -> [CGPoint] {
        let dx = cA.x - cB.x
        let dy = cA.y - cB.y
        let d = (dx * dx + dy * dy).squareRoot()
        if d + min(rA, rB) <= max(rA, rB) + 0.01 {
            let big = rA >= rB ? (cA, rA) : (cB, rB)
            return Self.ellipse(center: big.0, a: big.1, b: big.1, axisA: across, axisB: axis, from: 0, to: 2 * .pi, steps: steps * 2)
        }
        let a = atan2(dy, dx)
        let g = acos(min(max((rB - rA) / d, -1), 1))
        var points: [CGPoint] = []
        points.reserveCapacity(steps * 2 + 2)
        for i in 0...steps {
            let t = a - g + 2 * g * Double(i) / Double(steps)
            points.append(CGPoint(x: cA.x + rA * cos(t), y: cA.y + rA * sin(t)))
        }
        for i in 0...steps {
            let t = a + g + (2 * .pi - 2 * g) * Double(i) / Double(steps)
            points.append(CGPoint(x: cB.x + rB * cos(t), y: cB.y + rB * sin(t)))
        }
        return points
    }

    /// The near cap as seen: its outer half-disc joined to the front half of its seam ellipse.
    func nearCap(steps: Int = 32) -> [CGPoint] {
        let (c, rn) = near
        let o = outward
        let v = across
        var points: [CGPoint] = []
        for i in 0...steps {
            let t = Double.pi * Double(i) / Double(steps)
            points.append(CGPoint(x: c.x + rn * (cos(t) * v.x + sin(t) * o.x),
                                  y: c.y + rn * (cos(t) * v.y + sin(t) * o.y)))
        }
        for i in 1..<steps {
            let t = Double.pi + Double.pi * Double(i) / Double(steps)
            points.append(CGPoint(x: c.x + rn * (cos(t) * v.x + facing * sin(t) * o.x),
                                  y: c.y + rn * (cos(t) * v.y + facing * sin(t) * o.y)))
        }
        return points
    }

    /// The front half of the near seam: the edge between the lit cap and the body.
    func nearSeamFront(steps: Int = 32) -> [CGPoint] {
        let (c, rn) = near
        return Self.ellipse(center: c, a: rn, b: rn * facing, axisA: across, axisB: outward, from: .pi, to: 2 * .pi, steps: steps)
    }

    /// The outer arc of the near cap, inset by `inset` points, where the dispersion band sits.
    func nearRimArc(inset: Double, steps: Int = 32) -> [CGPoint] {
        let (c, rn) = near
        return Self.ellipse(center: c, a: rn - inset, b: rn - inset, axisA: across, axisB: outward, from: 0, to: .pi, steps: steps)
    }

    /// A cross-section ring of the body at `lambda` (0 far seam, 1 near seam).
    func ring(_ lambda: Double, steps: Int = 48) -> [CGPoint] {
        let n = near
        let f = far
        let c = CGPoint(x: f.c.x + (n.c.x - f.c.x) * lambda, y: f.c.y + (n.c.y - f.c.y) * lambda)
        let rr = f.r + (n.r - f.r) * lambda
        return Self.ellipse(center: c, a: rr, b: rr * facing, axisA: across, axisB: axis, from: 0, to: 2 * .pi, steps: steps)
    }

    /// The half of a body ring on the far side, the part that stacks below the disc end-on.
    func ringBack(_ lambda: Double, steps: Int = 32) -> [CGPoint] {
        let n = near
        let f = far
        let c = CGPoint(x: f.c.x + (n.c.x - f.c.x) * lambda, y: f.c.y + (n.c.y - f.c.y) * lambda)
        let rr = f.r + (n.r - f.r) * lambda
        return Self.ellipse(center: c, a: rr, b: rr * facing, axisA: across, axisB: outward, from: .pi, to: 2 * .pi, steps: steps)
    }

    /// Where the near cap's pole lands, pushed `k` radii along the outward direction.
    func towardsPole(_ k: Double) -> CGPoint {
        let (c, rn) = near
        let o = outward
        return CGPoint(x: c.x + o.x * rn * k, y: c.y + o.y * rn * k)
    }

    static func ellipse(center: CGPoint, a: Double, b: Double, axisA: CGPoint, axisB: CGPoint,
                        from: Double, to: Double, steps: Int) -> [CGPoint] {
        (0...steps).map { i in
            let t = from + (to - from) * Double(i) / Double(steps)
            return CGPoint(x: center.x + a * cos(t) * axisA.x + b * sin(t) * axisB.x,
                           y: center.y + a * cos(t) * axisA.y + b * sin(t) * axisB.y)
        }
    }
}

/// The light curves and the turn, as pure functions of the loop clock.
enum SpectralCapsuleSpinTimeline {
    /// One full turn: φ = 2πu − 0.10 sin 4πu, slower at the flat poses, faster end-on.
    static func phi(_ t: Double, loop: Double) -> Double {
        let u = t / loop
        return 2 * .pi * u - 0.10 * sin(4 * .pi * u)
    }

    static func bell(_ x: Double, _ c: Double, _ w: Double) -> Double {
        let k = (x - c) / w
        return exp(-k * k)
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(max((x - a) / (b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }

    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Rainbow band, warm caustic, frost, end-on rings, lit fibres for a facing of `c`.
    struct Light {
        let rainbow: Double
        let warm: Double
        let frost: Double
        let rings: Double
        let fibres: Double
        let rim: Double

        init(facing c: Double) {
            rainbow = bell(c, 0.40, 0.18) * smoothstep(0.10, 0.22, c)
            warm = bell(c, 0.72, 0.17) * (1 - smoothstep(0.80, 0.99, c))
            frost = bell(c, 0.68, 0.24)
            rings = smoothstep(0.80, 0.99, c)
            fibres = 0.6 * bell(c, 0.95, 0.05)
            rim = 1 - 0.3 * rings
        }
    }
}
