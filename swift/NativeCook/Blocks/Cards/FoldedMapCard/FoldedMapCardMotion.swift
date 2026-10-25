import Foundation
import CoreGraphics

/*
 Folded Map Card — the fold geometry and the autoPlay script (a port of foldedMapMotion.ts).

 The map is three panels side by side, folded as an accordion. Panel 0 lies flat; panel 1 turns
 about its left edge by φ1 (0 flat → foldMax folded back over panel 0, toward the viewer);
 panel 2 turns about panel 1's far edge by −φ2, so folded it lies flat on top, face up. Points are
 placed in 3D (x across, z toward the viewer is negative) and projected with a perspective of
 `perspective` pt about the card's centre. Each panel is a plane, so its projection is an exact
 homography of its rectangle (drawn with `projectionEffect`); its shading follows its normal
 against a light from the upper left, with darker creases near each hinge as it folds.

 loop = 8 s:
   0.0 – 1.4   rest: folded, the cover info beside it.
   1.4 – 3.2   unfold: panel 1 opens (ease-out back, 3 % overshoot), panel 2 follows 80 ms later;
               the info beside it fades out.
   3.2 – 4.4   the route draws on (ease-in-out), the summit pin drops (4.0 – 4.4, bounce) and the
               elevation profile rises (3.4 – 4.2, 40 ms per bar).
   4.4 – 6.0   hold.
   6.0 – 7.6   the route, pin and profile go (6.0 – 6.3), then refold (6.1 – 7.6, ease-in-out,
               panel 2 first, panel 1 60 ms later).
   7.6 – 8.0   rest.
*/

enum FoldedMapCardMotion {
    static let loop = 8.0
    static let perspective = 900.0
    /// Folded angle: a little short of flat so the stacked panels keep their paper thickness.
    static let foldMax = Double.pi - 0.05
    static let idleBeforeReturn = 2.0
    static let returnFor = 0.8

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x, 0, 1), 3) }

    /// Ease-out with a 3 % overshoot (back curve, s = 0.9).
    static func easeOutBack(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        let s = 0.9
        return 1 + (s + 1) * pow(c - 1, 3) + s * pow(c - 1, 2)
    }

    /// The loop: panel 1 unfold, panel 2 unfold, route drawn, pin, profile rise.
    static func script(_ t: Double) -> (u1: Double, u2: Double, route: Double, pin: Double, rise: Double) {
        let close1 = easeInOut(seg(t, 6.16, 7.6))
        let close2 = easeInOut(seg(t, 6.1, 7.54))
        let u1 = t < 6 ? (t < 1.4 ? 0 : easeOutBack(seg(t, 1.4, 3.1))) : 1 - close1
        let u2 = t < 6 ? (t < 1.48 ? 0 : easeOutBack(seg(t, 1.48, 3.2))) : 1 - close2
        let gone = 1 - easeOut(seg(t, 6.0, 6.3))
        let route = easeInOut(seg(t, 3.2, 4.2)) * gone
        let pin = seg(t, 4.0, 4.4) * gone
        let rise = seg(t, 3.4, 4.2) * gone
        return (u1, u2, route, pin, rise)
    }

    /// The pin's drop: from 26 pt above, landing with a small bounce. −1 before it starts.
    static func pinDrop(_ p: Double) -> Double {
        if p <= 0 { return -1 }
        let x = clamp(p, 0, 1)
        if x < 0.55 {
            let f = x / 0.55
            return 26 * (1 - f * f)
        }
        let b = (x - 0.55) / 0.45
        return 5 * sin(Double.pi * b) * (1 - b)
    }

    /// One panel's frame: its hinge (origin x, origin z) and the direction it runs in.
    struct Frame {
        var ox: Double
        var oz: Double
        var psi: Double
    }

    static func frames(pw: Double, u1: Double, u2: Double) -> [Frame] {
        let f1 = foldMax * (1 - u1)
        let f2 = foldMax * (1 - u2)
        let psi1 = f1
        let psi2 = f1 - f2
        let ox1 = pw
        let ox2 = ox1 + pw * cos(psi1)
        let oz2 = -pw * sin(psi1)
        return [Frame(ox: 0, oz: 0, psi: 0), Frame(ox: ox1, oz: 0, psi: psi1), Frame(ox: ox2, oz: oz2, psi: psi2)]
    }

    /// Brightness of a panel facing along psi (front or back), flat front = 1.
    static func shade(_ psi: Double, front: Bool) -> Double {
        var nx = -sin(psi)
        var nz = -cos(psi)
        if !front {
            nx = -nx
            nz = -nz
        }
        // Toward the light: upper left, in front.
        let d = max(0, nx * -0.5 + nz * -0.866)
        return clamp((0.55 + 0.45 * d) / (0.55 + 0.45 * 0.866), 0.35, 1.08)
    }

    /// The tint laid over a panel for a brightness b (1 = untouched): a warm shadow below 1, light above.
    static func tint(_ b: Double) -> (r: Double, g: Double, b: Double, a: Double) {
        if b >= 1 { return (255, 250, 235, clamp(b - 1, 0, 0.3)) }
        return (26, 18, 8, clamp(1 - b, 0, 0.75))
    }

    struct Panel {
        /// Corners on screen: top left, top right, bottom right, bottom left (in the panel's own
        /// left/right, so a back face comes out mirrored).
        var quad: [CGPoint]
        var front: Bool
        /// Shading stops across the panel at 0, 0.12, 0.88, 1 of its own width.
        var tints: [(r: Double, g: Double, b: Double, a: Double)]
    }

    struct Card {
        var x: Double
        var y: Double
        var w: Double
        var h: Double
        var pw: Double
    }

    static func project(_ card: Card, _ f: Frame, lx: Double, my: Double) -> CGPoint {
        let X = f.ox + lx * cos(f.psi)
        let Z = f.oz - lx * sin(f.psi)
        let s = perspective / (perspective + Z)
        let cx = card.w / 2
        let cy = card.h / 2
        return CGPoint(x: card.x + cx + (X - cx) * s, y: card.y + cy + (my - cy) * s)
    }

    /// A point on the flat map (x across, y down) through the folds, on screen.
    static func project(_ card: Card, _ frames: [Frame], mx: Double, my: Double) -> CGPoint {
        let k = Int(clamp(floor(mx / card.pw), 0, 2))
        return project(card, frames[k], lx: mx - Double(k) * card.pw, my: my)
    }

    /// The three panels this frame.
    static func mesh(_ card: Card, u1: Double, u2: Double) -> (panels: [Panel], frames: [Frame]) {
        let fr = frames(pw: card.pw, u1: u1, u2: u2)
        let f1 = foldMax * (1 - u1)
        let f2 = foldMax * (1 - u2)
        var out: [Panel] = []
        for k in 0..<3 {
            let f = fr[k]
            let front = cos(f.psi) > 0
            let base = shade(f.psi, front: front)
            let leftFold = k == 1 ? f1 : k == 2 ? f2 : 0
            let rightFold = k == 0 ? f1 : k == 1 ? f2 : 0
            let quad = [
                project(card, f, lx: 0, my: 0),
                project(card, f, lx: card.pw, my: 0),
                project(card, f, lx: card.pw, my: card.h),
                project(card, f, lx: 0, my: card.h),
            ]
            // Creases darken by up to 26 % as they close.
            let cl = 0.26 * abs(sin(leftFold / 2))
            let cr = 0.26 * abs(sin(rightFold / 2))
            let tints = [tint(base * (1 - cl)), tint(base * (1 - cl * 0.25)), tint(base * (1 - cr * 0.25)), tint(base * (1 - cr))]
            out.append(Panel(quad: quad, front: front, tints: tints))
        }
        return (out, fr)
    }
}
