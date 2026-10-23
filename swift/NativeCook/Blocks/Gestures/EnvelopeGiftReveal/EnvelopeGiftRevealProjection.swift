import CoreGraphics
import Foundation

/// The 4x4 matrix a React Native transform list builds (`perspective`, `rotateX`, `translateY`), row-major,
/// applied to column vectors. A list's first item is the outermost: `[a, b, c]` is `a * b * c`.
struct EnvelopeGiftRevealMatrix4 {
    var m: [Double] = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]

    static func translation(y: Double) -> Self {
        var r = Self()
        r.m[7] = y
        return r
    }

    /// Looking at the plane from `distance` away; the vanishing point is the origin of the group it sits in.
    static func perspective(_ distance: Double) -> Self {
        var r = Self()
        r.m[14] = -1 / distance
        return r
    }

    static func rotationX(_ radians: Double) -> Self {
        var r = Self()
        r.m[5] = cos(radians)
        r.m[6] = -sin(radians)
        r.m[9] = sin(radians)
        r.m[10] = cos(radians)
        return r
    }

    static func * (a: Self, b: Self) -> Self {
        var r = Self()
        for row in 0..<4 {
            for col in 0..<4 {
                var sum = 0.0
                for k in 0..<4 { sum += a.m[row * 4 + k] * b.m[k * 4 + col] }
                r.m[row * 4 + col] = sum
            }
        }
        return r
    }

    /// The matrix as the 2D homography Skia keeps for a group: the plane z = 0, which is what `SkMatrix` takes from
    /// a 16-number transform (columns x, y and the translation, rows x, y and w).
    var homography: EnvelopeGiftRevealHomography {
        EnvelopeGiftRevealHomography(m: [m[0], m[1], m[3], m[4], m[5], m[7], m[12], m[13], m[15]])
    }
}

/// A 3x3 projective map of the plane, row-major.
struct EnvelopeGiftRevealHomography {
    var m: [Double] = [1, 0, 0, 0, 1, 0, 0, 0, 1]

    func apply(_ x: Double, _ y: Double) -> CGPoint {
        let px = m[0] * x + m[1] * y + m[2]
        let py = m[3] * x + m[4] * y + m[5]
        let pw = m[6] * x + m[7] * y + m[8]
        return CGPoint(x: px / pw, y: py / pw)
    }

    func apply(_ point: CGPoint) -> CGPoint { apply(point.x, point.y) }

    /// `a` then `b`: nested groups multiply as 3x3 maps (the inner group first).
    static func nested(outer a: Self, inner b: Self) -> Self {
        var r = Self()
        for row in 0..<3 {
            for col in 0..<3 {
                var sum = 0.0
                for k in 0..<3 { sum += a.m[row * 3 + k] * b.m[k * 3 + col] }
                r.m[row * 3 + col] = sum
            }
        }
        return r
    }

    /// The affine map that agrees with this one, and its slope, at `point`: good where the map is nearly affine
    /// (a seal on a flap that is still low).
    func linearisation(at point: CGPoint) -> CGAffineTransform {
        let origin = apply(point)
        let dx = apply(point.x + 1, point.y)
        let dy = apply(point.x, point.y + 1)
        return CGAffineTransform(
            a: dx.x - origin.x, b: dx.y - origin.y,
            c: dy.x - origin.x, d: dy.y - origin.y,
            tx: origin.x, ty: origin.y
        )
    }
}

enum EnvelopeGiftRevealPolygon {
    /// Keeps the part of `polygon` whose y lies in `minY...maxY` (Sutherland-Hodgman against two horizontal edges).
    static func clip(_ polygon: [CGPoint], minY: Double, maxY: Double) -> [CGPoint] {
        func against(_ input: [CGPoint], keep: (CGPoint) -> Bool, crossing: (CGPoint, CGPoint) -> CGPoint) -> [CGPoint] {
            var output: [CGPoint] = []
            for index in input.indices {
                let current = input[index]
                let previous = input[(index + input.count - 1) % input.count]
                if keep(current) {
                    if !keep(previous) { output.append(crossing(previous, current)) }
                    output.append(current)
                } else if keep(previous) {
                    output.append(crossing(previous, current))
                }
            }
            return output
        }
        func cross(_ a: CGPoint, _ b: CGPoint, at y: Double) -> CGPoint {
            let t = (y - a.y) / (b.y - a.y)
            return CGPoint(x: a.x + (b.x - a.x) * t, y: y)
        }
        let low = against(polygon, keep: { $0.y >= minY }, crossing: { cross($0, $1, at: minY) })
        if low.isEmpty { return [] }
        return against(low, keep: { $0.y <= maxY }, crossing: { cross($0, $1, at: maxY) })
    }

    static func shifted(_ polygon: [CGPoint], dy: Double) -> [CGPoint] {
        polygon.map { CGPoint(x: $0.x, y: $0.y + dy) }
    }
}
