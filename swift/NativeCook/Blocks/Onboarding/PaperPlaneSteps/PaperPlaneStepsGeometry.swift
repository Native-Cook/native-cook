import Foundation
import CoreGraphics

/*
 Paper Plane Steps — the folds, worked out once per sheet size (a port of planeGeometry.ts).

 The sheet (0..sw, 0..sh) is folded four times, each a flat fold along a line:
   1. the top-left corner down to the centre line (line from (sw/2, 0) to (0, sw/2));
   2. the top-right corner, mirrored;
   3. the new left edge onto the centre line (line from the nose at 22.5° to the vertical);
   4. the same on the right.
 For each fold every face of the paper so far is clipped by the line (Sutherland–Hodgman, carrying
 its texture point along): the part on the fold's far side stays, the part on the near side is the
 flap that turns over. After the fold the flap is reflected across the line, shows its other side,
 and lands on top of everything with its layers in reverse order (a stack turned over).
 The result is a dart: a long nose and a short tail, the top view of a plane.
*/

struct PaperPlaneStepsFace {
    var pts: [CGPoint]
    var uv: [CGPoint]
    var back: Bool
    var layer: Int
}

struct PaperPlaneStepsFold {
    var a: CGPoint
    var b: CGPoint
    var statics: [PaperPlaneStepsFace]
    var movers: [PaperPlaneStepsFace]
}

struct PaperPlaneStepsPlan {
    var sw: Double
    var sh: Double
    var folds: [PaperPlaneStepsFold]
    var final: [PaperPlaneStepsFace]

    /// The flat sheet alone, for a sheet sliding in.
    static func flat(sw: Double, sh: Double) -> PaperPlaneStepsPlan {
        let r = [CGPoint(x: 0, y: 0), CGPoint(x: sw, y: 0), CGPoint(x: sw, y: sh), CGPoint(x: 0, y: sh)]
        return PaperPlaneStepsPlan(sw: sw, sh: sh, folds: [], final: [PaperPlaneStepsFace(pts: r, uv: r, back: false, layer: 0)])
    }

    private static func side(_ a: CGPoint, _ b: CGPoint, _ p: CGPoint) -> Double {
        (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
    }

    /// Keeps the part of a face where sign · side ≥ 0, or nil when nothing (or a sliver) is left.
    private static func clip(_ f: PaperPlaneStepsFace, _ a: CGPoint, _ b: CGPoint, _ sign: Double) -> PaperPlaneStepsFace? {
        var pts: [CGPoint] = []
        var uv: [CGPoint] = []
        let n = f.pts.count
        for i in 0..<n {
            let j = (i + 1) % n
            let p = f.pts[i]
            let q = f.pts[j]
            let sp = sign * side(a, b, p)
            let sq = sign * side(a, b, q)
            if sp >= 0 {
                pts.append(p)
                uv.append(f.uv[i])
            }
            if (sp >= 0) != (sq >= 0) {
                let t = sp / (sp - sq)
                pts.append(CGPoint(x: p.x + (q.x - p.x) * t, y: p.y + (q.y - p.y) * t))
                uv.append(CGPoint(x: f.uv[i].x + (f.uv[j].x - f.uv[i].x) * t, y: f.uv[i].y + (f.uv[j].y - f.uv[i].y) * t))
            }
        }
        if pts.count < 3 { return nil }
        // Drop slivers thinner than half a point.
        var area = 0.0
        for i in 0..<pts.count {
            let j = (i + 1) % pts.count
            area += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        if abs(area) < 1 { return nil }
        return PaperPlaneStepsFace(pts: pts, uv: uv, back: f.back, layer: f.layer)
    }

    private static func reflect(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGPoint {
        let dx = b.x - a.x
        let dy = b.y - a.y
        let l = hypot(dx, dy)
        let ux = dx / l
        let uy = dy / l
        let d = (p.x - a.x) * ux + (p.y - a.y) * uy
        let fx = a.x + ux * d
        let fy = a.y + uy * d
        return CGPoint(x: 2 * fx - p.x, y: 2 * fy - p.y)
    }

    /// A stable sort by layer (the React Native version relies on JavaScript's stable sort).
    private static func byLayer(_ faces: [PaperPlaneStepsFace]) -> [PaperPlaneStepsFace] {
        faces.enumerated().sorted { $0.element.layer != $1.element.layer ? $0.element.layer < $1.element.layer : $0.offset < $1.offset }.map(\.element)
    }

    static func make(sw: Double, sh: Double) -> PaperPlaneStepsPlan {
        let t = tan(22.5 * Double.pi / 180)
        // a, b, and a point on the flap's side.
        let lines: [(CGPoint, CGPoint, CGPoint)] = [
            (CGPoint(x: sw / 2, y: 0), CGPoint(x: 0, y: sw / 2), CGPoint(x: 1, y: 1)),
            (CGPoint(x: sw / 2, y: 0), CGPoint(x: sw, y: sw / 2), CGPoint(x: sw - 1, y: 1)),
            (CGPoint(x: sw / 2, y: 0), CGPoint(x: 0, y: sw / 2 / t), CGPoint(x: 2, y: sw * 0.6)),
            (CGPoint(x: sw / 2, y: 0), CGPoint(x: sw, y: sw / 2 / t), CGPoint(x: sw - 2, y: sw * 0.6)),
        ]
        var faces = flat(sw: sw, sh: sh).final
        var folds: [PaperPlaneStepsFold] = []
        for (a, b, r) in lines {
            let s = side(a, b, r)
            let flap: Double = s > 0 ? 1 : s < 0 ? -1 : 0
            var statics: [PaperPlaneStepsFace] = []
            var movers: [PaperPlaneStepsFace] = []
            for f in faces {
                if let st = clip(f, a, b, -flap) { statics.append(st) }
                if let m = clip(f, a, b, flap) { movers.append(m) }
            }
            folds.append(PaperPlaneStepsFold(a: a, b: b, statics: byLayer(statics), movers: byLayer(movers)))
            let top = statics.map(\.layer).max() ?? 0
            let moverTop = movers.map(\.layer).max() ?? 0
            let moved = movers.map { f in
                PaperPlaneStepsFace(pts: f.pts.map { reflect($0, a, b) }, uv: f.uv, back: !f.back, layer: top + 1 + (moverTop - f.layer))
            }
            faces = byLayer(statics + moved)
        }
        return PaperPlaneStepsPlan(sw: sw, sh: sh, folds: folds, final: faces)
    }
}
