import CoreGraphics
import SwiftUI
import UIKit

/*
 The supper table in 3D and the cloth as a mesh, a port of clothScene.ts. World: x right, y away
 from the viewer across the table (front edge y = 0, back edge y = depth), z up (table top z = 0).
 The camera looks down at 46° from the front.

 The cloth is a grid in its own material coordinates: u across (clothW wide), s along its length
 from the back edge (0) to the hem (clothLen). Pulled by d, a material point sits at p = s + d along
 its path: on the table while p ≤ depth, then over the front edge and down (streaming toward the
 viewer by `fling`). A travelling ripple lifts the part on the table. The linen is a texture baked
 once; each vertex also carries a shade (dark in the folds, light on the crests) that the Metal
 fragment lays over it.
 */

struct TableclothPullTrickGeo: Equatable {
    var w: CGFloat
    var h: CGFloat
    var cx: CGFloat
    var cy: CGFloat
    var panelTop: CGFloat
    var top: CGFloat
    var bottom: CGFloat

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        let panelTop = h - max(bottom, 12) - 150
        let frontY = panelTop - min(140, h * 0.17)
        let off = TableclothPullTrickScene.project(0, 0, 0, 0, 0).y
        self.w = w
        self.h = h
        self.cx = w / 2
        self.cy = frontY - off
        self.panelTop = panelTop
        self.top = top
        self.bottom = bottom
    }

    func project(_ x: Double, _ y: Double, _ z: Double) -> (x: CGFloat, y: CGFloat, s: CGFloat) {
        TableclothPullTrickScene.project(Double(cx), Double(cy), x, y, z)
    }

    func point(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
        let p = project(x, y, z)
        return CGPoint(x: p.x, y: p.y)
    }

    /// An affine transform drawing the table plane at height z around (x, y) in world units.
    func plane(_ x: Double, _ y: Double, _ z: Double) -> CGAffineTransform {
        let p = project(x, y, z)
        let px = project(x + 1, y, z)
        let py = project(x, y + 1, z)
        return CGAffineTransform(a: px.x - p.x, b: px.y - p.y, c: py.x - p.x, d: py.y - p.y, tx: p.x, ty: p.y)
    }
}

enum TableclothPullTrickScene {
    private typealias M = TableclothPullTrickMotion
    private typealias P = TableclothPullTrickPaint

    static let clothW = 460.0
    static let cols = 40
    static let rows = 34
    static let texScale: CGFloat = 3
    static let elev = 46 * Double.pi / 180
    static let sinE = sin(elev)
    static let cosE = cos(elev)
    static let camD = 1000.0
    static let targetY = M.depth * 0.42
    /// Overall zoom of the view (the table runs off both sides of the screen).
    static let zoom = 1.2
    /// Floats per mesh vertex: screen position (2), linen coordinate (2), shade (4).
    static let floatsPerVertex = 8
    static let vertexCount = (cols + 1) * (rows + 1)

    /// Screen point and perspective scale of a world point.
    static func project(_ cx: Double, _ cy: Double, _ x: Double, _ y: Double, _ z: Double) -> (x: CGFloat, y: CGFloat, s: CGFloat) {
        let ry = y - targetY
        let yc = ry * sinE + z * cosE
        let s = zoom * camD / (camD + ry * cosE - z * sinE)
        return (CGFloat(cx + x * s), CGFloat(cy - yc * s), CGFloat(s))
    }

    static let indices: [UInt16] = {
        var out: [UInt16] = []
        for j in 0..<rows {
            for i in 0..<cols {
                let a = UInt16(j * (cols + 1) + i)
                let b = a + 1
                let c = a + UInt16(cols + 1)
                let d = c + 1
                out += [a, b, c, b, d, c]
            }
        }
        return out
    }()

    // MARK: Baked

    private static func worldQuad(_ g: TableclothPullTrickGeo, _ pts: [(Double, Double, Double)]) -> CGPath {
        let path = CGMutablePath()
        for (i, q) in pts.enumerated() {
            let s = g.point(q.0, q.1, q.2)
            if i == 0 { path.move(to: s) } else { path.addLine(to: s) }
        }
        path.closeSubpath()
        return path
    }

    /// Wall, the walnut table (top and apron), the floor.
    static func room(_ g: TableclothPullTrickGeo, scale: CGFloat) -> UIImage {
        P.bake(CGSize(width: g.w, height: g.h), scale: scale) { c in
            let W = 900.0
            let all = P.rect(0, 0, g.w, g.h)
            P.linear(c, all, from: .zero, to: CGPoint(x: 0, y: g.h), [P.cg(0x2A1D1A), P.cg(0x22171A), P.cg(0x140D0C)], [0, 0.5, 1])
            // Damask-ish wall: soft vertical panels and a warm pool of candlelight.
            for i in 0..<6 { P.fill(c, P.rect(CGFloat(i) * g.w / 5 - 4, 0, 2, g.h), 0x000000, 0.12) }
            let back = g.point(0, M.depth, 0)
            P.radial(c, all, center: CGPoint(x: g.cx, y: back.y - 30), radius: g.w * 0.9,
                     [P.cg(0xB0703A, 0.32), P.cg(0x6A3A22, 0.12), P.cg(0x000000, 0)], [0, 0.5, 1])
            // Floor under the table.
            let front = g.point(0, 0, 0)
            P.linear(c, P.rect(0, front.y, g.w, g.h - front.y), from: CGPoint(x: 0, y: front.y), to: CGPoint(x: 0, y: g.h),
                     [P.cg(0x1A100C), P.cg(0x22150F), P.cg(0x120B08)], [0, 0.4, 1])
            // Apron: the table's front face, then the top.
            P.linear(c, worldQuad(g, [(-W, 0, 0), (W, 0, 0), (W, 0, -58), (-W, 0, -58)]),
                     from: CGPoint(x: 0, y: front.y), to: CGPoint(x: 0, y: front.y + 50),
                     [P.cg(0x4A2614), P.cg(0x341A0D), P.cg(0x1E0F07)], [0, 0.5, 1])
            let apronBottom = g.point(0, 0, -58)
            P.linear(c, P.rect(0, apronBottom.y, g.w, 40), from: CGPoint(x: 0, y: apronBottom.y), to: CGPoint(x: 0, y: apronBottom.y + 40),
                     [P.cg(0x000000, 0.6), P.cg(0x000000, 0)], nil)
            let topPath = worldQuad(g, [(-W, 0, 0), (W, 0, 0), (W, M.depth + 60, 0), (-W, M.depth + 60, 0)])
            P.linear(c, topPath, from: CGPoint(x: 0, y: back.y), to: CGPoint(x: 0, y: front.y),
                     [P.cg(0x5A2E17), P.cg(0x6E3A1E), P.cg(0x4F2812)], [0, 0.6, 1])
            c.saveGState()
            c.addPath(topPath)
            c.clip()
            P.brush(c, CGRect(x: 0, y: back.y - 60, width: g.w, height: front.y - back.y + 60), alpha: 0.2)
            // Plank seams running away from the viewer.
            var x = -W
            while x <= W {
                let a = g.point(x, 0, 0), b = g.point(x, M.depth + 60, 0)
                P.stroke(c, P.line(a.x, a.y, b.x, b.y), 0x1C0C05, width: 1, 0.45)
                x += 120
            }
            c.restoreGState()
            P.stroke(c, P.line(0, front.y + 0.5, g.w, front.y + 0.5), 0xD89A62, width: 1, 0.35)
            P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.06, seed: 2)
            // Vignette.
            P.radial(c, all, center: CGPoint(x: g.cx, y: front.y - 60), radius: g.h * 0.75,
                     [P.cg(0x000000, 0), P.cg(0x000000, 0.1), P.cg(0x000000, 0.6)], [0, 0.55, 1])
        }
    }

    /// The linen in material space (clothW × clothLen pt), at texScale pixels per pt.
    static func linen() -> CGImage? {
        let W = CGFloat(clothW), L = CGFloat(M.clothLen)
        let image = P.bake(CGSize(width: W, height: L), scale: texScale) { c in
            P.linear(c, P.rect(0, 0, W, L), from: .zero, to: CGPoint(x: 0, y: L), [P.cg(0xF7F2E8), P.cg(0xF1EBDF), P.cg(0xECE4D6)], [0, 0.6, 1])
            // Weave: fine, faint streaks one way and the other (multiplied), then the slubs of real
            // linen. Kept light so the cloth stays cream, as Skia's soft noise leaves it in RN.
            c.saveGState()
            c.setBlendMode(.multiply)
            P.brush(c, CGRect(x: 0, y: 0, width: W, height: L), alpha: 0.1)
            c.saveGState()
            c.translateBy(x: W, y: 0)
            c.rotate(by: .pi / 2)
            P.brush(c, CGRect(x: 0, y: 0, width: L, height: W), alpha: 0.1)
            c.restoreGState()
            c.saveGState()
            c.translateBy(x: W, y: 0)
            c.rotate(by: .pi / 2)
            c.scaleBy(x: 3, y: 1)
            P.brush(c, CGRect(x: 0, y: 0, width: L / 3, height: W), alpha: 0.06)
            c.restoreGState()
            c.restoreGState()
            // Windowpane check: a strong and a fine terracotta line each way.
            var x: CGFloat = 16
            while x < W {
                P.stroke(c, P.line(x, 0, x, L), 0xC0603A, width: 2.4, 0.42)
                P.stroke(c, P.line(x + 6, 0, x + 6, L), 0xC0603A, width: 0.9, 0.36)
                x += 64
            }
            var y: CGFloat = 20
            while y < L - 30 {
                P.stroke(c, P.line(0, y, W, y), 0xC0603A, width: 2.4, 0.42)
                P.stroke(c, P.line(0, y + 6, W, y + 6), 0xC0603A, width: 0.9, 0.36)
                y += 64
            }
            // Hem: a woven band and a stitched edge.
            P.fill(c, P.rect(0, L - 26, W, 9), 0xB4532F, 0.85)
            P.fill(c, P.rect(0, L - 13, W, 2.5), 0xB4532F, 0.7)
            P.stroke(c, P.line(0, L - 4, W, L - 4), 0x8A3A1E, width: 0.8, 0.5, dash: [3, 2])
            // Pressed creases from the linen cupboard.
            P.stroke(c, P.line(W / 2, 0, W / 2, L), 0xFFFFFF, width: 1.4, 0.35)
            P.stroke(c, P.line(W / 2 + 1.5, 0, W / 2 + 1.5, L), 0x8A7A60, width: 0.8, 0.15)
            let half = CGFloat(M.depth / 2)
            P.stroke(c, P.line(0, half, W, half), 0xFFFFFF, width: 1.4, 0.3)
            P.stroke(c, P.line(0, half + 1.5, W, half + 1.5), 0x8A7A60, width: 0.8, 0.12)
        }
        return image.cgImage
    }

    // MARK: The mesh

    /// Where a material point (u across, s along) lies for a pose, and how it is shaded.
    private static func clothPoint(_ pose: TableclothPullTrickPose, _ u: Double, _ s: Double, _ phase: Double, _ sway: Double)
        -> (x: Double, y: Double, z: Double, dark: Double, light: Double) {
        let p = s + pose.d
        var x = u
        var y: Double
        var z: Double
        var dark = 0.03 + 0.035 * (0.5 + 0.5 * sin(u / 83 + s / 61)) + 0.025 * (0.5 + 0.5 * sin(u / 37 - s / 29 + 2))
        var light = 0.0
        let depth = M.depth
        if p <= depth {
            // On the table (or above it, thrown): a travelling ripple plus air under the middle.
            let k = 2 * Double.pi / 120
            let across = 0.65 + 0.35 * sin(u / 70 + 1.3)
            let arg = k * s - phase + u / 160
            let bump = sin(Double.pi * M.clamp(s, 0, depth) / depth) * (0.75 + 0.25 * cos(u / 90))
            let rip = pose.wave * across * (0.5 + 0.5 * sin(arg))
            z = max(0, pose.lift + pose.air * bump + rip)
            let slope = pose.wave * across * 0.5 * cos(arg) * k + pose.air * cos(Double.pi * M.clamp(s, 0, depth) / depth) * Double.pi / depth
            dark += M.clamp(-slope * 1.3, 0, 0.34)
            light = M.clamp(slope * 1.1, 0, 0.24)
            y = depth - p + pose.back
            x += pose.wave * 0.1 * sin(arg * 0.7)
        } else {
            // Over the front edge: hinged at the edge, hanging at `hang` (0 flat forward, 1 down), in
            // soft vertical folds that deepen toward the hem; streaming toward the viewer on a yank.
            let q = p - depth
            let f = pose.fling
            let hem = pow(M.clamp(q / M.drop, 0, 1), 0.8)
            let beta = pose.hang * Double.pi / 2
            let ez = pose.lift + pose.air * 0.15
            let fa = u / 13 + 0.8 * sin(u / 37 + 1.1)
            let foldDepth = (5 + 6 * hem) * min(pose.hang, 1) * (1 - 0.5 * f)
            y = pose.back - q * cos(beta) - (0.5 + 0.5 * sin(fa)) * foldDepth - 1.5 - 3 * sway * hem * (0.6 + 0.4 * sin(u / 90))
            z = ez - q * sin(beta)
            // The yank: the drape streams out toward the viewer and a wave whips down it to the hem.
            let whip = q / 38 - pose.d / 26 + u / 150
            let reach = M.clamp(q / 50, 0, 1)
            y -= q * f * 0.5 + f * 34 * sin(whip) * reach
            x += f * 12 * sin(q / 50 - phase + u / 120)
            let fslope = cos(fa) * (1 + 0.8 * cos(u / 37 + 1.1) / 37 * 13)
            let wslope = f * cos(whip) * reach
            let hang1 = min(pose.hang, 1)
            dark += 0.1 + 0.12 * M.clamp(q / 140, 0, 1) + M.clamp(-fslope, 0, 1) * 0.16 * hang1 * (1 - f) + M.clamp(-wslope, 0, 1) * 0.4
            light = M.clamp(fslope, 0, 1) * 0.12 * hang1 * (1 - f) + M.clamp(wslope, 0, 1) * 0.3
        }
        return (x, y, z, dark, light)
    }

    /// The cloth's mesh for a pose, written into `out` (floatsPerVertex per vertex). Returns
    /// whether any of it is on screen. `alpha` scales the shade (the linen's alpha is a uniform).
    static func vertices(_ g: TableclothPullTrickGeo, _ pose: TableclothPullTrickPose, alpha: Double, into out: inout [Float]) -> Bool {
        let tau = 2 * Double.pi
        let phase = tau * 9 * pose.time / M.loop
        let sway = sin(tau * 2 * pose.time / M.loop)
        if out.count != vertexCount * floatsPerVertex { out = [Float](repeating: 0, count: vertexCount * floatsPerVertex) }
        var onScreen = false
        var n = 0
        for j in 0...rows {
            let s = Double(j) / Double(rows) * M.clothLen
            for i in 0...cols {
                let u = (Double(i) / Double(cols) - 0.5) * clothW
                let c = clothPoint(pose, u, s, phase, sway)
                let sp = g.project(c.x, c.y, c.z)
                if sp.y < g.panelTop + 40 && sp.y > -40 { onScreen = true }
                out[n] = Float(sp.x)
                out[n + 1] = Float(sp.y)
                out[n + 2] = Float(Double(i) / Double(cols))
                out[n + 3] = Float(Double(j) / Double(rows))
                if c.dark > c.light {
                    out[n + 4] = 0.2
                    out[n + 5] = 0.11
                    out[n + 6] = 0.06
                    out[n + 7] = Float(c.dark * alpha)
                } else {
                    out[n + 4] = 1
                    out[n + 5] = 0.96
                    out[n + 6] = 0.88
                    out[n + 7] = Float(c.light * alpha)
                }
                n += floatsPerVertex
            }
        }
        return onScreen
    }

    /// Speed streaks on the table behind the cloth's back edge while it is yanked.
    static func streaks(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ pose: TableclothPullTrickPose) {
        let k = M.clamp(pose.speed / 2200, 0, 1)
        if k <= 0.02 { return }
        let edge = M.depth - pose.d
        if edge < -M.drop { return }
        for i in 0..<16 {
            let h = sin(Double(i) * 12.9898 + 4.7) * 43758.5453
            let r = h - floor(h)
            let x = -190 + Double(i) / 15 * 380 + (r - 0.5) * 18
            let y0 = max(edge, 0) + 6
            let len = (60 + 120 * r) * k
            let a = g.point(x, y0, 1)
            let b = g.point(x, y0 + len, 1)
            var line = Path()
            line.move(to: a)
            line.addLine(to: b)
            let gradient = Gradient(colors: [
                Color(.sRGB, red: 1, green: 0.97, blue: 0.9, opacity: 0.75 * k),
                Color(.sRGB, red: 1, green: 0.97, blue: 0.9, opacity: 0),
            ])
            ctx.stroke(line, with: .linearGradient(gradient, startPoint: a, endPoint: b), lineWidth: 1.6 + 2.2 * r)
        }
    }
}
