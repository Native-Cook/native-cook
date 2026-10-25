import Foundation

/*
 Jelly Cube Squish — the cube's geometry, soft-body deformation and camera (a port of
 jellyMath.ts), and the autoPlay timeline (jellyMotion.ts).

 World: x right, y away, z up; the plate is the plane z = 0 and the cube stands on it, half size
 `half`, edges rounded by `round` (each face is an n × n grid pushed onto a rounded box). The camera
 turns yaw about z and looks down elev, so the front (−y), left (−x) and top faces show.

 A deformation is a few numbers, applied to every rest point p (h = z / 2·half, 0 at the plate):
   squash sq   z shrinks by sq, the sides bulge to keep the volume (most at mid height);
   lean lx, ly the top shears sideways by h²·(lx, ly) — the cube's wobble modes;
   dent        a Gaussian push (radius dentR) at point (px, py, pz) along the face normal;
   stretch     (sx, sy, sz) a pull at the same point, falling off over grabR and with height.

 autoPlay (loop = 8 s). Every wobble is a damped sine windowed to reach exactly zero before the
 loop ends, so t = 8 is t = 0.
   0.0 – 1.2   rest (a faint breathing squash, whole-loop sine).
   1.1 – 1.2   a finger appears on the front face; 1.2 – 1.45 it pokes in (dent 30 pt, ease-out,
               squash 7 %); 1.45 it lets go: the dent springs back and the cube wobbles, gone by 2.6.
   2.6 – 3.6   flavour strawberry → lime: the new colour fills from the plate up with a wavy front;
               the name rolls (2.6 – 2.9).
   3.9 – 5.4   a finger takes the top front corner, pulls it up and right (4.0 – 5.0), holds; 5.4 it
               lets go: the stretch springs back with lean and squash wobbles, settled by 7.4.
   6.8 – 7.8   back to strawberry (same fill), the name rolls back (6.8 – 7.1).
   7.8 – 8.0   rest.
 */

typealias JellyCubeSquishV3 = SIMD3<Double>

struct JellyCubeSquishDeform {
    var sq = 0.0
    var lx = 0.0
    var ly = 0.0
    var dent = 0.0
    /// Dent / grab point and the dent's direction (unit, into the cube).
    var px = 0.0
    var py = -JellyCubeSquishMath.half
    var pz = JellyCubeSquishMath.half
    var nx = 0.0
    var ny = 1.0
    var nz = 0.0
    var sx = 0.0
    var sy = 0.0
    var sz = 0.0
}

/// Colour state: from, to (flavour indices), fill 0 … 1, name roll 0 … 1.
struct JellyCubeSquishColour {
    var from = 0
    var to = 0
    var fill = 0.0
    var roll = 0.0
}

enum JellyCubeSquishMath {
    typealias V3 = JellyCubeSquishV3

    static let half = 72.0
    static let round = 20.0
    static let n = 12
    static let dentR = 34.0
    static let grabR = 92.0
    static let yaw = 38 * Double.pi / 180
    static let elev = 24 * Double.pi / 180
    static let cy = cos(yaw), sy = sin(yaw), ce = cos(elev), se = sin(elev)
    static let camD = 900.0
    static let zoom = 1.45
    static let lookZ = half

    /// Unit vector from the cube toward the camera, in world space.
    static let toEye = V3(-sy * ce, -cy * ce, se)
    /// Camera right and up in world space (for mapping a drag to a pull).
    static let right = V3(cy, -sy, 0)
    static let up = V3(sy * se, cy * se, ce)
    /// Unit vector toward the light (up, left, front).
    static let toLight = V3(-0.45, -0.55, 0.7)

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOut(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) * (1 - x) }

    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let y = -2 * x + 2
        return 1 - y * y * y / 2
    }

    /// Screen point and scale of a world point, the cube's foot centred at (cx, cy).
    static func project(_ cx: Double, _ cyScreen: Double, _ p: V3) -> (x: Double, y: Double, s: Double) {
        let x1 = p.x * cy - p.y * sy
        let y1 = p.x * sy + p.y * cy
        let z = p.z - lookZ
        let yc = y1 * se + z * ce
        let s = zoom * camD / (camD + y1 * ce - z * se)
        return (cx + x1 * s, cyScreen - yc * s, s)
    }

    /// The six faces: origin corner, u and v axes (each 2·half long), outward normal.
    static let faces: [[Double]] = [
        [-1, -1, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0],
        [-1, 1, 0, 0, -1, 0, 0, 0, 1, -1, 0, 0],
        [-1, -1, 2, 1, 0, 0, 0, 1, 0, 0, 0, 1],
        [1, 1, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0],
        [1, -1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0],
        [-1, 1, 0, 1, 0, 0, 0, -1, 0, 0, 0, -1],
    ]
    static let faceCount = 6

    /// Rest points of every face grid (rounded box), face-major, row-major.
    static let restPoints: [V3] = {
        var out: [V3] = []
        let inner = half - round
        for f in 0..<faceCount {
            let F = faces[f]
            for j in 0...n {
                for i in 0...n {
                    let a = Double(i) / Double(n) * 2
                    let b = Double(j) / Double(n) * 2
                    let x = (F[0] + F[3] * a + F[6] * b) * half
                    let y = (F[1] + F[4] * a + F[7] * b) * half
                    let z = (F[2] + F[5] * a + F[8] * b) * half
                    // Push onto the rounded box: nearest point of the inner box plus round outward.
                    let cx = clamp(x, -inner, inner)
                    let cyy = clamp(y, -inner, inner)
                    let cz = clamp(z, round, 2 * half - round)
                    let dx = x - cx, dy = y - cyy, dz = z - cz
                    let l0 = (dx * dx + dy * dy + dz * dz).squareRoot()
                    let l = l0 == 0 ? 1 : l0
                    out.append(V3(cx + dx / l * round, cyy + dy / l * round, max(0, cz + dz / l * round)))
                }
            }
        }
        return out
    }()

    /// A deformed point.
    static func deform(_ d: JellyCubeSquishDeform, _ x: Double, _ y: Double, _ z: Double) -> V3 {
        let h = clamp(z / (2 * half), 0, 1)
        let sq = clamp(d.sq, -0.25, 0.4)
        let bulge = (1 / (1 - sq).squareRoot() - 1) * (0.55 + 0.45 * sin(.pi * h))
        var X = x * (1 + bulge)
        var Y = y * (1 + bulge)
        var Z = z * (1 - sq)
        X += d.lx * h * h * half * 0.01
        Y += d.ly * h * h * half * 0.01
        let ex = x - d.px, ey = y - d.py, ez = z - d.pz
        let r2 = ex * ex + ey * ey + ez * ez
        if d.dent != 0 {
            let w = exp(-r2 / (2 * dentR * dentR))
            // The dent, and a soft rim pushed out around it.
            let rr = dentR * 1.9
            let rim = exp(-r2 / (2 * rr * rr)) - w
            let k = d.dent * (w - 0.35 * rim)
            X += d.nx * k
            Y += d.ny * k
            Z += d.nz * k * (z > 2 ? 1 : 0)
        }
        if d.sx != 0 || d.sy != 0 || d.sz != 0 {
            let w = exp(-r2 / (2 * grabR * grabR)) * (0.25 + 0.75 * h)
            X += d.sx * w
            Y += d.sy * w
            Z += d.sz * w
        }
        return V3(X, Y, max(0, Z))
    }

    static func deform(_ d: JellyCubeSquishDeform, _ p: V3) -> V3 { deform(d, p.x, p.y, p.z) }
}

enum JellyCubeSquishMotion {
    private typealias J = JellyCubeSquishMath

    static let loop = 8.0

    static let flavours: [(name: String, rgb: JellyCubeSquishV3)] = [
        ("Strawberry", JellyCubeSquishV3(0.93, 0.2, 0.3)),
        ("Lime", JellyCubeSquishV3(0.55, 0.82, 0.22)),
        ("Mango", JellyCubeSquishV3(0.97, 0.62, 0.2)),
    ]

    /// The poke point (front face) and the grab point (top front corner), world.
    static let poke = JellyCubeSquishV3(18, -J.half, J.half * 0.95)
    static let grab = JellyCubeSquishV3(J.half * 0.45, -J.half * 0.55, J.half * 2)
    static let pullRight = 30.0
    static let pullUp = 26.0

    /// A damped sine after time s ≥ 0, windowed to zero at `span`.
    private static func ring(_ s: Double, _ decay: Double, _ w: Double, _ span: Double, cos useCos: Bool = false) -> Double {
        if s < 0 || s >= span { return 0 }
        let k = 1 - s / span
        return exp(-decay * s) * (useCos ? cos(w * s) : sin(w * s)) * k * k
    }

    /// The deformation at loop time t.
    static func deform(_ t: Double) -> JellyCubeSquishDeform {
        let tau = 2 * Double.pi
        var d = JellyCubeSquishDeform()
        d.sq = 0.008 * sin(tau * 2 * t / loop)
        // Poke.
        if t >= 1.2 && t < 2.8 {
            d.px = poke.x
            d.py = poke.y
            d.pz = poke.z
            d.nx = 0
            d.ny = 1
            d.nz = 0
            if t < 1.45 {
                let e = J.easeOut(J.seg(t, 1.2, 1.45))
                d.dent = 30 * e
                d.sq += 0.07 * e
                d.ly = 3 * e
            } else {
                let s = t - 1.45
                d.dent = 30 * ring(s, 7, 20, 1.0, cos: true)
                d.sq += 0.07 * ring(s, 4.5, 15, 1.15, cos: true)
                d.ly = 3 * ring(s, 3.5, 11, 1.15, cos: true) + 9 * ring(s, 3.5, 11, 1.15)
            }
        }
        // Jiggles when the colour changes.
        d.sq += 0.035 * ring(t - 2.6, 5, 16, 0.9) + 0.03 * ring(t - 6.8, 5, 16, 0.9)
        // Drag-stretch.
        if t >= 3.9 && t < 7.6 {
            d.px = grab.x
            d.py = grab.y
            d.pz = grab.z
            var k: Double
            var lean = 0.0
            if t < 5.4 {
                // A 4 % dip the other way first (anticipation), then the pull.
                let u = J.seg(t, 4.0, 5.0)
                k = J.easeInOut(u) - 0.04 * sin(.pi * J.seg(t, 4.0, 4.25))
                d.sq += -0.04 * J.easeInOut(u)
            } else {
                let s = t - 5.4
                k = ring(s, 3.8, 12, 2.0, cos: true)
                lean = ring(s, 3, 10, 2.0)
                d.sq += 0.06 * ring(s, 4, 13, 2.0)
            }
            d.sx = J.right.x * pullRight * k
            d.sy = J.right.y * pullRight * k
            d.sz = pullUp * k
            d.lx += -14 * lean * J.right.x
            d.ly += -14 * lean * J.right.y
        }
        return d
    }

    /// Colour state at loop time t.
    static func colour(_ t: Double) -> JellyCubeSquishColour {
        if t < 2.6 { return JellyCubeSquishColour() }
        if t < 6.8 { return JellyCubeSquishColour(from: 0, to: 1, fill: J.easeInOut(J.seg(t, 2.6, 3.6)), roll: J.easeInOut(J.seg(t, 2.6, 2.9))) }
        return JellyCubeSquishColour(from: 1, to: 0, fill: J.easeInOut(J.seg(t, 6.8, 7.8)), roll: J.easeInOut(J.seg(t, 6.8, 7.1)))
    }

    /// The demo finger: world point and alpha.
    static func finger(_ t: Double, _ d: JellyCubeSquishDeform) -> (JellyCubeSquishV3, Double) {
        if t >= 1.1 && t < 1.6 {
            let a = J.seg(t, 1.1, 1.2) * (1 - J.seg(t, 1.45, 1.6))
            return (JellyCubeSquishV3(poke.x, poke.y + d.dent, poke.z), a)
        }
        if t >= 3.9 && t < 5.55 {
            let a = J.seg(t, 3.9, 4.0) * (1 - J.seg(t, 5.4, 5.55))
            return (JellyCubeSquishV3(grab.x + d.sx, grab.y + d.sy, grab.z + d.sz), a)
        }
        return (JellyCubeSquishV3(0, 0, 0), 0)
    }
}
