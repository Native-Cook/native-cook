import SwiftUI
import UIKit

/// What the dice table draws every frame, in one Canvas (a port of the per-frame half of
/// diceArt.ts): each die's shadow on the felt, its rounded silhouette, its lit faces and their
/// concave pips, all projected from 3D; the chosen player's pop and the result line.
enum DiceRollPickerFrame {
    private typealias D = DiceRollPickerMath
    private typealias P = DiceRollPickerPaint
    typealias V3 = DiceRollPickerV3

    static let pips: [[(Double, Double)]] = [
        [],
        [(0, 0)],
        [(-1, -1), (1, 1)],
        [(-1, -1), (0, 0), (1, 1)],
        [(-1, -1), (1, -1), (-1, 1), (1, 1)],
        [(-1, -1), (1, -1), (-1, 1), (1, 1), (0, 0)],
        [(-1, -1), (-1, 0), (-1, 1), (1, -1), (1, 0), (1, 1)],
    ]
    static let facePos = [3, 2, 1]
    static let faceNeg = [4, 5, 6]
    static let ivory = V3(0.965, 0.935, 0.86)

    static let nameUI = P.sans(13, .semibold)
    static let tagUI = P.sans(9, .heavy)
    static let resultUI = P.sans(20, .bold)
    static let name = Font.system(size: 13, weight: .semibold)
    static let tag = Font.system(size: 9, weight: .heavy)
    static let result = Font.system(size: 20, weight: .bold)

    private static func hull(_ input: [CGPoint]) -> Path {
        let p = input.sorted { $0.x == $1.x ? $0.y < $1.y : $0.x < $1.x }
        func cross(_ o: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        }
        var lower: [CGPoint] = []
        for q in p {
            while lower.count >= 2 && cross(lower[lower.count - 2], lower[lower.count - 1], q) <= 0 { lower.removeLast() }
            lower.append(q)
        }
        var upper: [CGPoint] = []
        for q in p.reversed() {
            while upper.count >= 2 && cross(upper[upper.count - 2], upper[upper.count - 1], q) <= 0 { upper.removeLast() }
            upper.append(q)
        }
        upper.removeLast()
        lower.removeLast()
        var path = Path()
        path.addLines(lower + upper)
        path.closeSubpath()
        return path
    }

    /// Lambert plus a Blinn highlight on the ivory, as RGB.
    private static func shade(_ n: V3, _ at: V3, _ k: Double) -> V3 {
        let l = D.toLight
        let diff = max(0, (n * l).sum())
        let v = D.toCamera(at)
        let hv = l + v
        let hl = (hv * hv).sum().squareRoot()
        let spec = pow(max(0, (n * hv).sum() / hl), 36) * 0.28
        let lit = (0.5 + 0.55 * diff) * k
        return V3(min(1, ivory.x * lit + spec), min(1, ivory.y * lit + spec), min(1, ivory.z * lit + spec * 0.95))
    }

    private static func color(_ c: V3, _ a: Double = 1) -> Color {
        Color(.sRGB, red: c.x, green: c.y, blue: c.z, opacity: a)
    }

    private static func corners(_ g: DiceRollPickerGeo, _ pose: DiceRollPickerPose, _ zc: Double, shadow: Bool) -> [CGPoint] {
        let l = D.toLight
        var pts: [CGPoint] = []
        pts.reserveCapacity(8)
        for i in 0..<8 {
            let r = pose.m.apply(V3(i & 1 != 0 ? D.inner : -D.inner, i & 2 != 0 ? D.inner : -D.inner, i & 4 != 0 ? D.inner : -D.inner))
            if shadow {
                let z = zc + r.z
                pts.append(g.project(V3(pose.x + r.x - l.x / l.z * z, pose.y + r.y - l.y / l.z * z, 0)))
            } else {
                pts.append(g.project(V3(pose.x + r.x, pose.y + r.y, zc + r.z)))
            }
        }
        return pts
    }

    /// The die's shadow on the felt (drawn for both dice before either die).
    static func shadow(_ ctx: GraphicsContext, _ g: DiceRollPickerGeo, _ pose: DiceRollPickerPose) {
        let zc = pose.hop + D.support(pose.m)
        let lift = D.clamp(pose.hop / 90, 0, 1)
        let path = hull(corners(g, pose, zc, shadow: true))
        let ink = P.color(0x02100A)
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 6 + lift * 10))
            l.stroke(path, with: .color(ink.opacity(0.5 - 0.3 * lift)), style: StrokeStyle(lineWidth: D.round * 2, lineCap: .round, lineJoin: .round))
            l.fill(path, with: .color(ink.opacity(0.55 - 0.35 * lift)))
        }
    }

    static func die(_ ctx: GraphicsContext, _ g: DiceRollPickerGeo, _ pose: DiceRollPickerPose) {
        let m = pose.m
        let zc = pose.hop + D.support(m)
        let c = V3(pose.x, pose.y, zc)
        let pc = D.project(Double(g.cx), Double(g.cy), c)
        // Rounded silhouette: the inner cube's outline, stroked round by the corner radius.
        let sil = hull(corners(g, pose, zc, shadow: false))
        let edge = color(shade(V3(0, -0.35, 0.94), c, 0.82))
        ctx.fill(sil, with: .color(edge))
        ctx.stroke(sil, with: .color(edge), style: StrokeStyle(lineWidth: D.round * 2 * pc.s, lineCap: .round, lineJoin: .round))
        let l = D.toLight
        for k in 0..<3 {
            for sgn in [-1.0, 1.0] {
                let col = m.column(k) * sgn
                let fcW = c + col * D.half
                let view = D.toCamera(fcW)
                let facing = (col * view).sum()
                if facing <= 0.01 { continue }
                let u = m.column((k + 1) % 3)
                let v = m.column((k + 2) % 3)
                func corner(_ a: Double, _ b: Double) -> CGPoint {
                    g.project(fcW + (u * a + v * b) * D.inner)
                }
                var face = Path()
                face.addLines([corner(-1, -1), corner(1, -1), corner(1, 1), corner(-1, 1)])
                face.closeSubpath()
                let lit = shade(col, fcW, 1)
                ctx.fill(face, with: .color(color(lit)))
                // The flat face blends into the rounded edge: a soft band in the face's own colour
                // (the React Native version blurs one stroke; here four strokes stand in for it).
                let w = D.round * 0.9 * pc.s
                let sigma = D.round * 0.35
                for o in [2.4, 0.8, -0.8, -2.4] {
                    ctx.stroke(face, with: .color(color(lit, 0.181)),
                               style: StrokeStyle(lineWidth: max(0.5, w + o * sigma), lineCap: .round, lineJoin: .round))
                }
                // Pips: a projected circle each, darker floor offset away from the light (concave).
                let value = sgn > 0 ? facePos[k] : faceNeg[k]
                let layout = pips[value]
                let spread = D.edge * 0.245
                let pr = D.edge * (value == 1 ? 0.13 : 0.082)
                let lu = (l * u).sum()
                let lv = (l * v).sum()
                let alpha = D.clamp((facing - 0.01) / 0.25, 0, 1)
                var base = Path(), floor = Path(), glint = Path()
                for p in layout {
                    let o = fcW + u * (p.0 * spread) + v * (p.1 * spread)
                    let p0 = g.project(o)
                    let pu = g.project(o + u * pr)
                    let pv = g.project(o + v * pr)
                    let t = CGAffineTransform(a: pu.x - p0.x, b: pu.y - p0.y, c: pv.x - p0.x, d: pv.y - p0.y, tx: p0.x, ty: p0.y)
                    base.addPath(Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)), transform: t)
                    floor.addPath(Path(ellipseIn: CGRect(x: -lu * 0.28 - 0.78, y: -lv * 0.28 - 0.78, width: 1.56, height: 1.56)), transform: t)
                    glint.addPath(Path(ellipseIn: CGRect(x: lu * 0.42 - 0.45, y: lv * 0.42 - 0.45, width: 0.9, height: 0.9)), transform: t)
                }
                let red = value == 1
                ctx.fill(base, with: .color(P.color(red ? 0x9E1F1A : 0x2B1D17, alpha)))
                ctx.fill(floor, with: .color(P.color(red ? 0x6E100C : 0x140C09, alpha)))
                ctx.fill(glint, with: .color(P.color(red ? 0xD65A4C : 0x6B5A4C, alpha * 0.35)))
            }
        }
        // A thin bright rim on the lit side of the silhouette.
        var rim = ctx
        rim.translateBy(x: -0.6, y: -0.8)
        rim.stroke(sil, with: .color(P.color(0xFFFAF0, 0.22)), style: StrokeStyle(lineWidth: 1, lineCap: .round, lineJoin: .round))
    }

    /// Avatars from their baked sprites; the chosen one pops (0 … 1.3), wears a ring and the tag.
    static func avatars(_ ctx: GraphicsContext, _ g: DiceRollPickerGeo, sprites: [UIImage], pop: Double, chosen: Int) {
        let r = g.avatarR
        let cy = g.avatarY
        let side = r * 2 + DiceRollPickerArt.spritePad * 2
        for i in 0..<sprites.count {
            let x = 16 + g.cell * (CGFloat(i) + 0.5)
            let k = i == chosen ? CGFloat(pop) : 0
            let s = 1 + 0.16 * k
            let lift = 6 * k
            var a = ctx
            a.translateBy(x: x, y: cy - lift)
            a.scaleBy(x: s, y: s)
            if k > 0.01 {
                // The lifted avatar's shadow grows and softens.
                a.drawLayer { l in
                    l.addFilter(.blur(radius: 4 + 6 * k))
                    l.fill(Path(ellipseIn: CGRect(x: -r, y: 3 + lift * 0.6 - r, width: r * 2, height: r * 2)),
                           with: .color(P.color(0x000000, Double(0.12 * min(k, 1.3)))))
                }
            }
            a.draw(Image(uiImage: sprites[i]), in: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
            if k > 0.01 {
                a.stroke(Path(ellipseIn: CGRect(x: -r - 3.5, y: -r - 3.5, width: r * 2 + 7, height: r * 2 + 7)),
                         with: .color(P.color(0xF2C46B, Double(min(k, 1)))), lineWidth: 2.2)
            }
            if i == chosen && pop > 0.02 {
                let p = DiceRollPickerArt.players[i]
                let al = D.clamp(pop, 0, 1)
                // The name brightens from 0.6 (baked) to 1.
                let nw = P.width(p.name, nameUI)
                text(ctx, p.name, name, P.color(0xF3EADB, al), x: x - nw / 2, baseline: cy + r + 20)
                let tw = P.width("STARTS", tagUI) + 14
                let ty = cy - r * s - lift - 26 + CGFloat(1 - al) * 8
                ctx.fill(Path(roundedRect: CGRect(x: x - tw / 2, y: ty, width: tw, height: 18), cornerRadius: 9),
                         with: .color(P.color(0xF2C46B, al)))
                text(ctx, "STARTS", tag, P.color(0x3A2610, al), x: x - tw / 2 + 7, baseline: ty + 12.5)
            }
        }
    }

    /// The result line: rolls from the prompt (k = 0) to "11 · Priya starts" (k = 1).
    static func result(_ ctx: GraphicsContext, _ g: DiceRollPickerGeo, _ line: String, k: Double) {
        let lines = ["Flick the dice to pick who starts", line]
        let travel: CGFloat = 22
        var clip = ctx
        clip.clip(to: Path(CGRect(x: 0, y: g.resultBase - 26, width: g.w, height: 34)))
        for j in 0..<2 {
            let a = 1 - min(1, abs(Double(j) - k))
            if a <= 0.01 { continue }
            let font = j == 0 ? name : result
            let w = P.width(lines[j], j == 0 ? nameUI : resultUI)
            text(clip, lines[j], font, P.color(j == 0 ? 0xF3EADB : 0xFFF4DC, j == 0 ? a * 0.6 : a),
                 x: g.cx - w / 2, baseline: g.resultBase + CGFloat(Double(j) - k) * travel)
        }
    }

    static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color, x: CGFloat, baseline: CGFloat) {
        let r = ctx.resolve(Text(s).font(font).foregroundColor(color))
        let b = r.firstBaseline(in: CGSize(width: 10_000, height: 10_000))
        ctx.draw(r, at: CGPoint(x: x, y: baseline - b), anchor: .topLeading)
    }
}
