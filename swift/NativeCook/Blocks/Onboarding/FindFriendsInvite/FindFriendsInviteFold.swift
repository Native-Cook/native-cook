import CoreGraphics
import Foundation

/// The paper: the "invite them" button folded into a dart, flown, and a new sheet unfolded.
///
/// The button is a sheet in its own flat coordinates (x 0..W, y 0..H, origin at its top-left). Each
/// visible piece of paper (a "face") is a flat polygon carried into 3D by a chain of rigid folds, then
/// placed on screen and projected with a pinhole camera: one 3x3 homography per face.
///
/// Faces (draw order 1, 0, 4, 5, 2, 3):
///   0 left half   1 right half   2 top wing   3 bottom wing   4 top flap   5 bottom flap
enum FindFriendsInviteFold {
    typealias M = FindFriendsInviteMotion
    static let faces = 6
    static let drawOrder = [1, 0, 4, 5, 2, 3]

    /// 3D affine map, row-major: [m00 m01 m02 m10 m11 m12 m20 m21 m22 t0 t1 t2].
    typealias Aff = [Double]

    static func mul(_ a: Aff, _ b: Aff) -> Aff {
        var r = [Double](repeating: 0, count: 12)
        for i in 0..<3 {
            for j in 0..<3 {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
            }
        }
        for i in 0..<3 {
            r[9 + i] = a[i * 3] * b[9] + a[i * 3 + 1] * b[10] + a[i * 3 + 2] * b[11] + a[9 + i]
        }
        return r
    }

    /// Rotation by `th` (rad) about the axis through (px, py, pz) along unit (ux, uy, uz).
    static func rotAbout(_ px: Double, _ py: Double, _ pz: Double, _ ux: Double, _ uy: Double, _ uz: Double, _ th: Double) -> Aff {
        let c = cos(th), s = sin(th), C = 1 - c
        let m = [
            c + ux * ux * C, ux * uy * C - uz * s, ux * uz * C + uy * s,
            uy * ux * C + uz * s, c + uy * uy * C, uy * uz * C - ux * s,
            uz * ux * C - uy * s, uz * uy * C + ux * s, c + uz * uz * C
        ]
        return m + [
            px - (m[0] * px + m[1] * py + m[2] * pz),
            py - (m[3] * px + m[4] * py + m[5] * pz),
            pz - (m[6] * px + m[7] * py + m[8] * pz)
        ]
    }

    static func move(_ x: Double, _ y: Double, _ z: Double) -> Aff { [1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z] }

    /// Uniform scale `k` about (x, y) in the screen plane.
    static func scaleAbout(_ k: Double, _ x: Double, _ y: Double) -> Aff { [k, 0, 0, 0, k, 0, 0, 0, k, x - k * x, y - k * y, 0] }

    /// Perspective projection of the face plane (camera at distance d in front of (cx, cy)).
    static func homography(_ a: Aff, _ cx: Double, _ cy: Double, _ d: Double) -> [Double] {
        [a[0] - cx * a[6] / d, a[1] - cx * a[7] / d, a[9] - cx * a[11] / d,
         a[3] - cy * a[6] / d, a[4] - cy * a[7] / d, a[10] - cy * a[11] / d,
         -a[6] / d, -a[7] / d, 1 - a[11] / d]
    }

    /// The face's shadow on the card (z = 0), cast along the light.
    static func shadowMatrix(_ a: Aff) -> [Double] {
        let sx = M.Light.shadowDir.0, sy = M.Light.shadowDir.1
        return [a[0] + sx * a[6], a[1] + sx * a[7], a[9] + sx * a[11],
                a[3] + sy * a[6], a[4] + sy * a[7], a[10] + sy * a[11], 0, 0, 1]
    }

    /// Lighting of a face from its normal: (dark overlay, bright overlay).
    static func shade(_ a: Aff) -> (Double, Double) {
        var nx = a[2], ny = a[5], nz = a[8]
        let len = hypot(hypot(nx, ny), nz)
        let l = len == 0 ? 1 : len
        nx /= l; ny /= l; nz /= l
        if nz < 0 { nx = -nx; ny = -ny; nz = -nz }
        let d = M.Light.dir
        let ll = (d.0 * d.0 + d.1 * d.1 + d.2 * d.2).squareRoot()
        let v = (nx * d.0 + ny * d.1 + nz * d.2) / ll - d.2 / ll
        return (max(0, -v) * M.Light.dark, max(0, v) * M.Light.bright)
    }

    /// Scale of a pressed button at local time tau.
    static func pressScale(_ tau: Double, pressIn: Double, release: Double, to: Double) -> Double {
        if tau <= 0 { return 1 }
        if tau < pressIn { return 1 - (1 - to) * M.bezier(M.easeOut, tau / pressIn) }
        if tau < release { return to }
        return to + (1 - to) * M.springStep(tau - release, omega: M.Spring.omega, zeta: M.Spring.zeta)
    }

    /// Corner radius of the button's sheet.
    static func sheetRadius(_ tau: Double, pill: Double) -> Double {
        let t = tau - M.Invite.release
        if tau <= M.Invite.release { return pill }
        if t < M.Reform.radiusBack.0 {
            return pill + (M.Fold.paperRadius - pill) * M.bezier(M.easeOut, M.seg(t, 0, M.Fold.radiusDur))
        }
        return M.Fold.paperRadius + (pill - M.Fold.paperRadius) * M.bezier(M.easeOut, M.seg(t, M.Reform.radiusBack.0, M.Reform.radiusBack.1))
    }

    /// Label + icon of the button: (opacity, scale).
    static func labelState(_ tau: Double) -> (Double, Double) {
        let t = tau - M.Invite.release
        if tau <= M.Invite.release { return (1, 1) }
        let out = M.bezier(M.easeOut, M.seg(t, 0, M.Fold.labelOut))
        let back = M.bezier(M.easeOut, M.seg(t, M.Reform.labelIn.0, M.Reform.labelIn.1))
        let v = t < M.Reform.labelIn.0 ? 1 - out : back
        return (v, M.Fold.labelScale + (1 - M.Fold.labelScale) * v)
    }

    /// Whether the native button (pill + label) is shown.
    static func buttonShown(_ tau: Double) -> Bool {
        let t = tau - M.Invite.release
        return tau <= M.Invite.release || t < M.Fold.rnOff || t >= M.Reform.rnOn
    }

    /// Does the overlay draw any paper at tau?
    static func paperOn(_ tau: Double) -> Bool {
        let t = tau - M.Invite.release
        return tau > M.Invite.release && t >= M.Fold.skiaOn
            && t < max(M.Reform.skiaOff, M.Fold.launch + M.Flight.duration + M.Contrail.fadeOut)
    }

    struct Geo {
        var W: Double, H: Double, left: Double, top: Double
        var camX: Double, camY: Double, offX: Double, offY: Double
    }

    struct Frame {
        var mats = [[Double]](repeating: hidden, count: faces)
        var shadows = [[Double]](repeating: hidden, count: faces)
        var vis = [Double](repeating: 0, count: faces)
        var dark = [Double](repeating: 0, count: faces)
        var bright = [Double](repeating: 0, count: faces)
        var radius = 0.0
        /// Face 0 is the whole pill (no crease yet / any more).
        var flat = false
        var shadowOpacity = M.Light.shadowOpacity
        var shadowBlur = M.Light.shadowBlur
        var trailOpacity = 0.0
        /// Ribbon edge points, two per sample (tail -> head); empty while there is no contrail.
        var trail: [CGPoint] = []
    }

    static let hidden: [Double] = [0, 0, 0, 0, 0, 0, 0, 0, 1]

    /// The paper at `tau` seconds after the press of "invite them".
    static func frame(_ tau: Double, _ g: Geo, pill: Double) -> Frame {
        let t = tau - M.Invite.release
        let W = g.W, H = g.H, c = H / 2, d = M.Flight.camera
        var f = Frame()
        f.radius = sheetRadius(tau, pill: pill)
        if tau <= M.Invite.release || t < M.Fold.skiaOn { return f }

        func put(_ i: Int, _ a: Aff, _ o: Double) {
            f.mats[i] = homography(a, g.camX, g.camY, d)
            f.shadows[i] = shadowMatrix(a)
            f.vis[i] = o
            let s = shade(a)
            f.dark[i] = s.0
            f.bright[i] = s.1
        }

        let cx = g.left + W / 2
        let cy = g.top + c
        let flightEnd = M.Fold.launch + M.Flight.duration

        // The sheet before and during the book fold (faces 0, 1).
        if t < M.Fold.book.1 {
            let k = pressScale(tau, pressIn: M.Invite.pressIn, release: M.Invite.release, to: M.Invite.pressScale)
            let place = mul(scaleAbout(k, cx, cy), move(g.left, g.top, 0))
            let th = Double.pi * M.bezier(M.easeFold, M.seg(t, M.Fold.book.0, M.Fold.book.1))
            f.flat = th <= 0
            put(0, mul(place, rotAbout(W / 2, 0, 0, 0, 1, 0, th)), 1)
            if !f.flat { put(1, place, 1) }
        }

        // The dart: flaps, keel, launch and flight (faces 2...5).
        var z = 0.0
        if t >= M.Fold.book.1 && t < flightEnd {
            var beta = M.Fold.keelAngle * M.bezier(M.easeFold, M.seg(t, M.Fold.keelClose.0, M.Fold.keelClose.1))
            if t > M.Fold.keelClose.1 {
                beta = M.Fold.keelAngle + (M.Fold.wingAngle - M.Fold.keelAngle)
                    * M.springStep(t - M.Fold.keelClose.1, omega: M.Fold.wingOmega, zeta: M.Fold.wingZeta)
            }
            beta = beta * .pi / 180
            let fa = M.Fold.flapAngle * .pi / 180
            let ft = fa * M.bezier(M.easeFold, M.seg(t, M.Fold.flapTop.0, M.Fold.flapTop.1))
            let fb = fa * M.bezier(M.easeFold, M.seg(t, M.Fold.flapBot.0, M.Fold.flapBot.1))
            let fadeFrom = (M.Fold.flapAngle - M.Fold.flapFade) * .pi / 180
            let ot = 1 - M.seg(ft, fadeFrom, fa)
            let ob = 1 - M.seg(fb, fadeFrom, fa)

            var px = g.left + 3 * W / 4
            var py = g.top + c
            var yaw = 0.0, m = 1.0, roll = 0.0
            var k = pressScale(tau, pressIn: M.Invite.pressIn, release: M.Invite.release, to: M.Invite.pressScale)
            if t >= M.Fold.launch {
                let tf = t - M.Fold.launch
                let p = M.bezier(M.easeHang, tf / M.Flight.duration)
                let at = M.path.at(p * M.path.length)
                px = at.x + g.offX
                py = at.y + g.offY
                z = M.Flight.lift * sin(.pi * p)
                yaw = at.h * M.bezier(M.easeOut, M.seg(tf, 0, M.Flight.yawDur))
                m = 1 - (1 - M.Flight.scale) * M.bezier(M.easeOut, M.seg(tf, 0, M.Flight.scaleDur))
                roll = at.b * M.bezier(M.easeOut, M.seg(tf, 0, M.Flight.bankIn))
                k = 1
            }
            let cs = cos(yaw), sn = sin(yaw)
            let dcx = 3 * W / 4, dcy = c
            let fly: Aff = [cs * m, -sn * m, 0, sn * m, cs * m, 0, 0, 0, m,
                            px - m * (cs * dcx - sn * dcy), py - m * (sn * dcx + cs * dcy), z]
            let place = mul(scaleAbout(k, cx, cy), fly)
            let book = rotAbout(W / 2, 0, 0, 0, 1, 0, .pi)
            put(2, mul(mul(place, rotAbout(0, c, 0, 1, 0, 0, -beta + roll)), book), 1)
            put(3, mul(mul(place, rotAbout(0, c, 0, 1, 0, 0, beta + roll)), book), 1)
            let ln = hypot(W / 2, c)
            if ot > 0 { put(4, mul(mul(place, rotAbout(W / 2, 0, 0, W / 2 / ln, c / ln, 0, ft)), book), ot) }
            if ob > 0 { put(5, mul(mul(place, rotAbout(W / 2, H, 0, W / 2 / ln, -c / ln, 0, -fb)), book), ob) }
        }

        // The new sheet: appears folded, unfolds into the button (faces 0, 1).
        if t >= M.Reform.appear && t < M.Reform.skiaOff {
            let a = M.bezier(M.easeOut, M.seg(t, M.Reform.appear, M.Reform.appear + M.Reform.appearDur))
            let k = M.Reform.appearScale + (1 - M.Reform.appearScale) * a
            let place = mul(scaleAbout(k, g.left + 3 * W / 4, cy), move(g.left, g.top, 0))
            var th = Double.pi
            if t > M.Reform.unfold {
                th = Double.pi * max(0, 1 - M.springStep(t - M.Reform.unfold, omega: M.Reform.omega, zeta: M.Reform.zeta))
            }
            if t >= M.Reform.rnOn { th = 0 }
            f.flat = th <= 0
            put(0, mul(place, rotAbout(W / 2, 0, 0, 0, 1, 0, th)), a)
            if !f.flat { put(1, place, a) }
        }

        // Shadow: softer and lighter as the dart climbs.
        f.shadowOpacity = M.Light.shadowOpacity * (1 - 0.5 * z / M.Flight.lift)
        f.shadowBlur = M.Light.shadowBlur + M.Light.shadowBlurPerPt * z

        // Contrail: a ribbon along the path behind the dart.
        if t >= M.Fold.launch && t < flightEnd + M.Contrail.fadeOut {
            let tf = t - M.Fold.launch
            let p = M.bezier(M.easeHang, min(tf / M.Flight.duration, 1))
            let m = 1 - (1 - M.Flight.scale) * M.bezier(M.easeOut, M.seg(tf, 0, M.Flight.scaleDur))
            let head = p * M.path.length - (W / 4) * m * M.Contrail.headGap
            let tail = max(0, head - M.Contrail.length)
            if head > 1 {
                f.trailOpacity = M.seg(tf, 0, M.Contrail.fadeIn) * (1 - M.seg(tf, M.Flight.duration, M.Flight.duration + M.Contrail.fadeOut))
                let K = M.Contrail.samples
                f.trail.reserveCapacity(2 * K)
                for i in 0..<K {
                    let u = Double(i) / Double(K - 1)
                    let s = tail + (head - tail) * u
                    let at = M.path.at(s)
                    let zi = M.Flight.lift * sin(.pi * s / M.path.length)
                    let fz = d / (d - zi)
                    let age = 1 - u
                    let w = (M.Contrail.widthHead + (M.Contrail.widthTail - M.Contrail.widthHead) * age) * fz
                    let drift = M.Contrail.wobble * age * sin(s * 0.07 + tf * 2.2)
                    let nx = -sin(at.h), ny = cos(at.h)
                    let x = g.camX + (at.x + g.offX + nx * drift - g.camX) * fz
                    let y = g.camY + (at.y + g.offY + ny * drift - g.camY) * fz
                    f.trail.append(CGPoint(x: x + nx * w / 2, y: y + ny * w / 2))
                    f.trail.append(CGPoint(x: x - nx * w / 2, y: y - ny * w / 2))
                }
            }
        }
        return f
    }

    // MARK: flat outlines of the faces

    enum Side { case full, left, right }

    private static func arc(_ cx: Double, _ cy: Double, _ r: Double, _ a0: Double, _ a1: Double, into pts: inout [CGPoint]) {
        let n = 10
        for i in 0...n {
            let a = (a0 + (a1 - a0) * Double(i) / Double(n)) * .pi / 180
            pts.append(CGPoint(x: cx + r * cos(a), y: cy + r * sin(a)))
        }
    }

    /// The sheet's rounded rectangle (or one half of it) as a flat polygon.
    static func sheetOutline(_ W: Double, _ H: Double, radius: Double, side: Side) -> [CGPoint] {
        let r = max(0.01, min(radius, H / 2))
        var p: [CGPoint] = []
        switch side {
        case .full:
            arc(W - r, r, r, -90, 0, into: &p)
            arc(W - r, H - r, r, 0, 90, into: &p)
            arc(r, H - r, r, 90, 180, into: &p)
            arc(r, r, r, 180, 270, into: &p)
        case .left:
            p.append(CGPoint(x: W / 2, y: 0))
            arc(r, r, r, 270, 180, into: &p)
            arc(r, H - r, r, 180, 90, into: &p)
            p.append(CGPoint(x: W / 2, y: H))
        case .right:
            p.append(CGPoint(x: W / 2, y: H))
            arc(W - r, H - r, r, 90, 0, into: &p)
            arc(W - r, r, r, 0, -90, into: &p)
            p.append(CGPoint(x: W / 2, y: 0))
        }
        return p
    }

    /// The dart's four polygons in the left half's own coordinates (nose at x 0), faces 2...5.
    static func dartOutlines(_ W: Double, _ H: Double) -> [[CGPoint]] {
        let c = H / 2
        func poly(_ a: [(Double, Double)]) -> [CGPoint] { a.map { CGPoint(x: $0.0, y: $0.1) } }
        return [
            poly([(W / 2, 0), (0, c), (W / 2, c)]),
            poly([(W / 2, c), (0, c), (W / 2, H)]),
            poly([(W / 2, 0), (0, 0), (0, c)]),
            poly([(W / 2, H), (0, H), (0, c)])
        ]
    }
}
