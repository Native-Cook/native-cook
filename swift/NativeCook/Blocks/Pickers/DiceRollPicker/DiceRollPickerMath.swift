import Foundation

/*
 Dice Roll Picker — 3D math and the analytic tumble, a port of diceMath.ts. World: x right, y away
 from the player across the table, z up; the felt is the plane z = 0, the table's centre the
 origin, units in points. A die is a rounded cube (edge `edge`, corner radius `round`): an inner
 cube of half size half − round dilated by round. Its orientation is a row-major 3×3 matrix whose
 columns are its local axes.

 Faces: +Z 1, −Z 6, +X 3, −X 4, +Y 2, −Y 5 (opposite faces add up to 7).

 A throw is closed-form: the path runs through a first landing, a bounce off the far rim and on to
 the resting spot; the height is three parabolic hops; the orientation is
 R(s) = Rw(w, 2πk·(1 − e)) · R1 · Rl(a, φ0·(1 − e)), e = ease-out of the time. Whole turns are the
 identity, so s = 0 is R0 and s = end is R1 exactly.
 */

typealias DiceRollPickerV3 = SIMD3<Double>

/// A row-major 3×3 rotation.
struct DiceRollPickerMat {
    var e: [Double]

    static let identity = DiceRollPickerMat(e: [1, 0, 0, 0, 1, 0, 0, 0, 1])

    subscript(_ i: Int) -> Double { e[i] }

    static func * (a: DiceRollPickerMat, b: DiceRollPickerMat) -> DiceRollPickerMat {
        var r = [Double](repeating: 0, count: 9)
        for i in 0..<3 {
            for j in 0..<3 {
                r[i * 3 + j] = a.e[i * 3] * b.e[j] + a.e[i * 3 + 1] * b.e[3 + j] + a.e[i * 3 + 2] * b.e[6 + j]
            }
        }
        return DiceRollPickerMat(e: r)
    }

    var transposed: DiceRollPickerMat {
        DiceRollPickerMat(e: [e[0], e[3], e[6], e[1], e[4], e[7], e[2], e[5], e[8]])
    }

    func apply(_ v: DiceRollPickerV3) -> DiceRollPickerV3 {
        DiceRollPickerV3(
            e[0] * v.x + e[1] * v.y + e[2] * v.z,
            e[3] * v.x + e[4] * v.y + e[5] * v.z,
            e[6] * v.x + e[7] * v.y + e[8] * v.z
        )
    }

    /// Local axis k in world space (column k).
    func column(_ k: Int) -> DiceRollPickerV3 { DiceRollPickerV3(e[k], e[3 + k], e[6 + k]) }
}

/// A die's pose: orientation, table position, hop height (above its support).
struct DiceRollPickerPose {
    var m: DiceRollPickerMat
    var x: Double
    var y: Double
    var hop: Double
}

/// A throw from (R0, p0, starting hop h0) to (R1, p1), `k` whole turns, the far rim at rimY.
struct DiceRollPickerThrow {
    var r0: DiceRollPickerMat
    var r1: DiceRollPickerMat
    var p0: (Double, Double)
    var p1: (Double, Double)
    var h0: Double
    var k: Double
    var w: DiceRollPickerV3
    var a: DiceRollPickerV3
    var phi: Double
    var rimY: Double
}

enum DiceRollPickerMath {
    typealias Mat = DiceRollPickerMat
    typealias V3 = DiceRollPickerV3

    static let edge = 66.0
    static let half = edge / 2
    static let round = edge * 0.15
    static let inner = half - round

    /// Camera: elevation above the table and distance (pt, also the focal length).
    static let elev = 60 * Double.pi / 180
    static let sinE = sin(elev)
    static let cosE = cos(elev)
    static let camD = 1100.0
    /// Unit vector from a point to the light (up, left, far).
    static let toLight = V3(-0.33, 0.42, 0.845)

    static let tableX0 = -170.0, tableX1 = 170.0, tableY0 = -130.0, tableY1 = 175.0
    static let tableRim = 16.0, tableWall = 16.0

    static let throwEnd = 2.2

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOut(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) * (1 - x) }

    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let y = -2 * x + 2
        return 1 - y * y * y / 2
    }

    /// A seeded value in [0, 1).
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Rotation about a unit axis by an angle (radians).
    static func axisAngle(_ ax: V3, _ ang: Double) -> Mat {
        let c = cos(ang), s = sin(ang), t = 1 - c
        let x = ax.x, y = ax.y, z = ax.z
        return Mat(e: [
            t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c,
        ])
    }

    /// The axis and angle of a rotation matrix (robust near 0 and π).
    static func toAxisAngle(_ m: Mat) -> (V3, Double) {
        let cosA = clamp((m[0] + m[4] + m[8] - 1) / 2, -1, 1)
        let ang = acos(cosA)
        if ang < 1e-4 { return (V3(0, 0, 1), 0) }
        if Double.pi - ang < 1e-3 {
            let xx = sqrt(max(0, (m[0] + 1) / 2))
            let yy = sqrt(max(0, (m[4] + 1) / 2))
            let zz = sqrt(max(0, (m[8] + 1) / 2))
            if xx >= yy && xx >= zz { return (V3(xx, m[1] / (2 * xx), m[2] / (2 * xx)), ang) }
            if yy >= zz { return (V3(m[1] / (2 * yy), yy, m[5] / (2 * yy)), ang) }
            return (V3(m[2] / (2 * zz), m[5] / (2 * zz), zz), ang)
        }
        let s = 2 * sin(ang)
        return (V3((m[7] - m[5]) / s, (m[2] - m[6]) / s, (m[3] - m[1]) / s), ang)
    }

    static func rotZ(_ a: Double) -> Mat { axisAngle(V3(0, 0, 1), a) }

    /// Orientation showing `face` on top, turned by `yaw` (radians) about the vertical.
    static func faceUp(_ face: Int, _ yaw: Double) -> Mat {
        let h = Double.pi / 2
        let base: Mat
        switch face {
        case 1: base = .identity
        case 6: base = axisAngle(V3(1, 0, 0), .pi)
        case 3: base = axisAngle(V3(0, 1, 0), -h)
        case 4: base = axisAngle(V3(0, 1, 0), h)
        case 2: base = axisAngle(V3(1, 0, 0), h)
        default: base = axisAngle(V3(1, 0, 0), -h)
        }
        return rotZ(yaw) * base
    }

    /// The face on top of an orientation.
    static func topFace(_ m: Mat) -> Int {
        let faces = [3, 2, 1]
        let opposite = [4, 5, 6]
        var best = 0.0
        var face = 1
        for k in 0..<3 {
            let z = m[6 + k]
            if abs(z) > best {
                best = abs(z)
                face = z > 0 ? faces[k] : opposite[k]
            }
        }
        return face
    }

    /// The orientation nearest to `m` with a face flat on the table.
    static func snapFlat(_ m: Mat) -> Mat {
        var k = 0
        for i in 1..<3 where abs(m[6 + i]) > abs(m[6 + k]) { k = i }
        let s: Double = m[6 + k] > 0 ? 1 : -1
        let n = V3(m[k] * s, m[3 + k] * s, m[6 + k] * s)
        let ax = V3(n.y, -n.x, 0)
        let len = hypot(ax.x, ax.y)
        if len < 1e-5 { return m }
        let ang = acos(clamp(n.z, -1, 1))
        return axisAngle(V3(ax.x / len, ax.y / len, 0), ang) * m
    }

    /// Height of the centre above the felt when the die rests on its lowest point.
    static func support(_ m: Mat) -> Double {
        (abs(m[6]) + abs(m[7]) + abs(m[8])) * inner + round
    }

    /// Screen point of a world point; s = the perspective scale there.
    static func project(_ cx: Double, _ cy: Double, _ p: V3) -> (x: Double, y: Double, s: Double) {
        let yc = p.y * sinE + p.z * cosE
        let dc = camD + p.y * cosE - p.z * sinE
        let s = camD / dc
        return (cx + p.x * s, cy - yc * s, s)
    }

    /// Table-plane point under a screen point at height z (inverse of project).
    static func unproject(_ cx: Double, _ cy: Double, _ sx: Double, _ sy: Double, _ z: Double) -> (Double, Double) {
        let k = (cy - sy) / camD
        let y = (k * (camD - z * sinE) - z * cosE) / (sinE - k * cosE)
        let dc = camD + y * cosE - z * sinE
        return ((sx - cx) * dc / camD, y)
    }

    /// Direction from a world point toward the camera.
    static func toCamera(_ p: V3) -> V3 {
        let c = V3(0, -camD * cosE, camD * sinE)
        let d = c - p
        return d / (d * d).sum().squareRoot()
    }

    static func makeThrow(_ r0: Mat, _ r1: Mat, _ p0: (Double, Double), _ p1: (Double, Double), h0: Double, k: Double, rimY: Double) -> DiceRollPickerThrow {
        let (a, phi) = toAxisAngle(r1.transposed * r0)
        let dx = p1.0 - p0.0
        let dy = rimY - p0.1
        let l = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
        return DiceRollPickerThrow(r0: r0, r1: r1, p0: p0, p1: p1, h0: h0, k: k, w: V3(dy / l, -dx / l, 0), a: a, phi: phi, rimY: rimY)
    }

    private static func hopArc(_ u: Double, _ a: Double, _ b: Double, _ h0: Double, _ peak: Double) -> Double {
        let x = seg(u, a, b)
        return h0 * (1 - x) + 4 * peak * x * (1 - x)
    }

    static func throwPose(_ th: DiceRollPickerThrow, _ s: Double) -> DiceRollPickerPose {
        // Spread sideways in flight, away from the table's centre line, so two dice don't touch.
        let side: Double = th.p1.0 >= 0 ? 1 : -1
        let land = (mix(th.p0.0, th.p1.0, 0.55) + side * 10, mix(th.p0.1, th.rimY, 0.7))
        let rim = (mix(th.p0.0, th.p1.0, 0.8) + side * 20, th.rimY)
        var x: Double
        var y: Double
        if s < 0.55 {
            let u = s / 0.55
            x = mix(th.p0.0, land.0, u)
            y = mix(th.p0.1, land.1, u)
        } else if s < 0.85 {
            let u = (s - 0.55) / 0.3
            x = mix(land.0, rim.0, u)
            y = mix(land.1, rim.1, u)
        } else {
            let u = easeOut(seg(s, 0.85, 1.8))
            x = mix(rim.0, th.p1.0, u)
            y = mix(rim.1, th.p1.1, u)
        }
        var hop = 0.0
        if s < 0.55 {
            hop = hopArc(s, 0, 0.55, th.h0, 96)
        } else if s < 1.0 {
            hop = hopArc(s, 0.55, 1.0, 0, 34)
        } else if s < 1.28 {
            hop = hopArc(s, 1.0, 1.28, 0, 10)
        }
        let e = easeOut(seg(s, 0, 1.85))
        var m = axisAngle(th.w, 2 * .pi * th.k * (1 - e)) * th.r1 * axisAngle(th.a, th.phi * (1 - e))
        if s > 1.85 {
            // Rock on the face after the spin: two damped wobbles about the roll axis.
            let r = s - 1.85
            let d = 1 - seg(r, 0, throwEnd - 1.85)
            let rock = 0.11 * sin(2 * .pi * r / 0.2) * d * d
            m = axisAngle(th.w, rock) * m
        }
        return DiceRollPickerPose(m: m, x: x, y: y, hop: hop)
    }

    /// A slide home from (R1, p1) to (R0, p0): tips over as it turns, small hop.
    static func slidePose(_ r1: Mat, _ r0: Mat, _ p1: (Double, Double), _ p0: (Double, Double), _ u: Double) -> DiceRollPickerPose {
        let e = easeInOut(clamp(u, 0, 1))
        let (b, psi) = toAxisAngle(r0.transposed * r1)
        return DiceRollPickerPose(m: r0 * axisAngle(b, psi * (1 - e)), x: mix(p1.0, p0.0, e), y: mix(p1.1, p0.1, e), hop: 8 * sin(.pi * e))
    }
}
