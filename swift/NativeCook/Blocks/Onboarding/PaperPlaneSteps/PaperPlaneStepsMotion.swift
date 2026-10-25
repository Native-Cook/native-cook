import Foundation
import CoreGraphics

/*
 Paper Plane Steps — the autoPlay script and the per-frame mesh (a port of planeMotion.ts).

 loop = 10 s:
   0.0 – 1.6   rest on sheet 1.
   1.6 – 3.4   sheet 1 folds into a plane: four folds of 0.45 s (ease-in-out), each flap turning
               about its crease in 3D with perspective, its shading following its angle.
   3.25 – 3.5  the plane tucks its wings (width × 0.62) and lifts (scale 1 → 0.86).
   3.4 – 4.6   it flies off along a curve (a small dip, then up and out at the top right), banking
               into the turn, shrinking with distance, a dashed trail behind it.
   4.4 – 5.2   sheet 2 slides up from below (ease-out back, 3 % overshoot, 4° → 0° tilt).
   5.2 – 6.4   hold.
   6.4 – 7.6   sheet 2 folds (four folds of 0.3 s); 7.45 – 7.7 tuck; 7.6 – 8.6 flight.
   8.6 – 9.4   sheet 1 slides back up, so the loop rests where it began; 9.4 – 10 rest.
*/

enum PaperPlaneStepsMotion {
    static let loop = 10.0
    static let perspective = 800.0
    static let idleBeforeReturn = 2.0
    static let steps = 3

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOutBack(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        let s = 0.9
        return 1 + (s + 1) * pow(c - 1, 3) + s * pow(c - 1, 2)
    }

    /// What is drawn: the sheet, fold 0..4, tuck 0..1, flight 0..1 (−1 none), the incoming sheet,
    /// its progress 0..1 (−1 none), and the dots' position.
    struct Pose {
        var sheet = 0
        var fold = 0.0
        var tuck = 0.0
        var fly = -1.0
        var inc = 1
        var incP = -1.0
        var dot = 0.0
    }

    private static func folds(_ t: Double, _ start: Double, _ each: Double) -> Double {
        clamp((t - start) / each, 0, 4)
    }

    static func script(_ t: Double) -> Pose {
        let dot = easeInOut(seg(t, 4.5, 4.9)) - easeInOut(seg(t, 8.6, 9.0))
        if t < 4.4 {
            return Pose(sheet: 0, fold: folds(t, 1.6, 0.45), tuck: easeInOut(seg(t, 3.25, 3.5)), fly: t < 3.4 ? -1 : seg(t, 3.4, 4.6), inc: 1, incP: -1, dot: dot)
        }
        if t < 5.2 { return Pose(sheet: 0, fold: 4, tuck: 1, fly: seg(t, 3.4, 4.6), inc: 1, incP: seg(t, 4.4, 5.2), dot: dot) }
        if t < 8.6 {
            return Pose(sheet: 1, fold: folds(t, 6.4, 0.3), tuck: easeInOut(seg(t, 7.45, 7.7)), fly: t < 7.6 ? -1 : seg(t, 7.6, 8.6), inc: 0, incP: -1, dot: dot)
        }
        if t < 9.4 { return Pose(sheet: 1, fold: 4, tuck: 1, fly: 1, inc: 0, incP: seg(t, 8.6, 9.4), dot: dot) }
        return Pose(sheet: 0, fold: 0, tuck: 0, fly: -1, inc: 1, incP: -1, dot: dot)
    }

    /// The flight path from the sheet's centre: position, heading (0 = nose up), scale, bank.
    static func flight(_ f: Double, sx: Double, sy: Double, w: Double) -> (x: Double, y: Double, heading: Double, scale: Double, bank: Double) {
        let cf = clamp(f, 0, 1)
        let u = easeInOut(cf * 0.92 + 0.08 * cf * cf)
        let p0x = sx, p0y = sy
        let p1x = sx - 70, p1y = sy + 80
        let p2x = sx + w * 0.6, p2y = sy - 120
        let p3x = w + 140, p3y = -220.0
        let a = 1 - u
        let x = a * a * a * p0x + 3 * a * a * u * p1x + 3 * a * u * u * p2x + u * u * u * p3x
        let y = a * a * a * p0y + 3 * a * a * u * p1y + 3 * a * u * u * p2y + u * u * u * p3y
        let dx = 3 * a * a * (p1x - p0x) + 6 * a * u * (p2x - p1x) + 3 * u * u * (p3x - p2x)
        let dy = 3 * a * a * (p1y - p0y) + 6 * a * u * (p2y - p1y) + 3 * u * u * (p3y - p2y)
        // Nose along the path once it moves; it starts pointing up the screen.
        let heading = u < 0.001 ? 0 : atan2(dy, dx) + Double.pi / 2
        let blend = clamp(u * 6, 0, 1)
        return (x, y, heading * blend, 0.86 - 0.62 * u, 0.55 * sin(Double.pi * u))
    }

    /// Where the sheet sits on screen: its centre, scale per axis, turn, overall scale and bank.
    struct Xf {
        var ox: Double
        var oy: Double
        var cx: Double
        var cy: Double
        var sx = 1.0
        var sy = 1.0
        var rot = 0.0
        var scale = 1.0
        var bank = 0.0
    }

    /// A sheet point (with height z toward the viewer, negative) on the screen.
    static func screenOf(_ xf: Xf, _ x: Double, _ y: Double, _ z: Double) -> CGPoint {
        let lx = (x - xf.cx) * xf.sx
        let ly = (y - xf.cy) * xf.sy
        let s = perspective / (perspective + z)
        let px = lx * s * xf.scale
        let py = ly * s * xf.scale
        let ca = cos(xf.rot)
        let sa = sin(xf.rot)
        return CGPoint(x: xf.ox + px * ca - py * sa, y: xf.oy + px * sa + py * ca)
    }

    /// A triangle on screen and the same triangle in the paper atlas (in points).
    struct Tri {
        var p: (CGPoint, CGPoint, CGPoint)
        var t: (CGPoint, CGPoint, CGPoint)
    }

    struct Out {
        var statics: [Tri] = []
        var movers: [Tri] = []
        /// Screen outlines: the flap's, and every face's.
        var moving: [[CGPoint]] = []
        var all: [[CGPoint]] = []
        /// The flap's brightness (1 = untouched).
        var flap = 1.0
        /// Outlines flattened onto the table, for the shadow.
        var shadow: [[CGPoint]] = []
    }

    private static func addFace(_ o: inout Out, _ xf: Xf, _ f: PaperPlaneStepsFace, sw: Double, step: Int, moving: Bool, line: (CGPoint, CGPoint)?, theta: Double, flipped: Bool) {
        var screen: [CGPoint] = []
        var flatOutline: [CGPoint] = []
        for q in f.pts {
            var x = q.x
            var y = q.y
            var z = 0.0
            if let line {
                // Turn the point about the crease by theta: its offset across the line shrinks by
                // cos θ and rises toward the viewer by sin θ.
                let ux = line.1.x - line.0.x
                let uy = line.1.y - line.0.y
                let l = hypot(ux, uy)
                let dx = ux / l
                let dy = uy / l
                let along = (x - line.0.x) * dx + (y - line.0.y) * dy
                let fx = line.0.x + dx * along
                let fy = line.0.y + dy * along
                let px = x - fx
                let py = y - fy
                let c = cos(theta)
                x = fx + px * c
                y = fy + py * c
                z = -hypot(px, py) * sin(theta)
            }
            screen.append(screenOf(xf, x, y, z))
            // The shadow falls on the table: the outline flattened, the lift kept as an offset.
            let sp = screenOf(xf, x, y, 0)
            flatOutline.append(CGPoint(x: sp.x - z * 0.05, y: sp.y - z * 0.09))
        }
        o.all.append(screen)
        if moving { o.moving.append(screen) }
        o.shadow.append(flatOutline)
        let back = f.back != flipped
        let ox = back ? 3 * sw : Double(step) * sw
        func tex(_ k: Int) -> CGPoint { CGPoint(x: ox + f.uv[k].x, y: f.uv[k].y) }
        for i in 1..<(f.pts.count - 1) {
            let tri = Tri(p: (screen[0], screen[i], screen[i + 1]), t: (tex(0), tex(i), tex(i + 1)))
            if moving { o.movers.append(tri) } else { o.statics.append(tri) }
        }
    }

    /// The paper this frame: static faces, then the turning flap (its layers reversed once it
    /// passes upright). `fold` is 0..4. The flap's brightness follows its angle, with a glint as it
    /// catches the light on the way over.
    static func paperMesh(_ plan: PaperPlaneStepsPlan, fold: Double, step: Int, xf: Xf) -> Out {
        var o = Out()
        if fold >= 4 || plan.folds.isEmpty {
            for f in plan.final { addFace(&o, xf, f, sw: plan.sw, step: step, moving: false, line: nil, theta: 0, flipped: false) }
            return o
        }
        let k = Int(floor(fold))
        let fd = plan.folds[k]
        let theta = Double.pi * easeInOut(fold - Double(k))
        for f in fd.statics { addFace(&o, xf, f, sw: plan.sw, step: step, moving: false, line: nil, theta: 0, flipped: false) }
        let flipped = theta > Double.pi / 2
        let glint = 0.12 * exp(-pow((theta - 1.05) / 0.28, 2))
        o.flap = (flipped ? 0.8 - 0.14 * cos(theta) : 0.8 + 0.2 * cos(theta)) + glint
        let n = fd.movers.count
        for i in 0..<n {
            let f = fd.movers[flipped ? n - 1 - i : i]
            addFace(&o, xf, f, sw: plan.sw, step: step, moving: true, line: (fd.a, fd.b), theta: theta, flipped: flipped)
        }
        return o
    }

    /// The tint laid over a face for a brightness b (1 = untouched): cool shadow below 1, light above.
    static func tint(_ b: Double) -> (r: Double, g: Double, b: Double, a: Double) {
        if b >= 1 { return (255, 250, 236, clamp(b - 1, 0, 0.3)) }
        return (22, 20, 38, clamp(1 - b, 0, 0.7))
    }
}
